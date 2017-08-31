/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.parser.ocr;

import static java.nio.charset.StandardCharsets.UTF_8;

import javax.imageio.ImageIO;
import javax.xml.parsers.SAXParser;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.tika.config.Initializable;
import org.apache.tika.config.InitializableProblemHandler;
import org.apache.tika.config.Param;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.CompositeParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.external.ExternalParser;
import org.apache.tika.parser.image.ImageParser;
import org.apache.tika.parser.image.TiffParser;
import org.apache.tika.parser.jpeg.JpegParser;
import org.apache.tika.sax.OfflineContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import com.google.common.util.concurrent.AtomicDouble;

import au.csiro.data61.dataFusion.common.Timer;

/**
 * TesseractOCRParser powered by tesseract-ocr engine. To enable this parser,
 * create a {@link TesseractOCRConfig} object and pass it through a
 * ParseContext. Tesseract-ocr must be installed and on system path or the path
 * to its root folder must be provided:
 * <p>
 * TesseractOCRConfig config = new TesseractOCRConfig();<br>
 * //Needed if tesseract is not on system path<br>
 * config.setTesseractPath(tesseractFolder);<br>
 * parseContext.set(TesseractOCRConfig.class, config);<br>
 * </p>
 *
 * Copied and modified from tika-parsers-1.16. One-jar will complain about the duplicate class.
 */
public class TesseractOCRParser extends AbstractParser implements Initializable {
    private static final Logger LOG = LoggerFactory.getLogger(TesseractOCRParser.class);

    private static volatile boolean HAS_WARNED = false;
    private static final Object[] LOCK = new Object[0];


    private static final long serialVersionUID = -8167538283213097265L;
    private static final TesseractOCRConfig DEFAULT_CONFIG = new TesseractOCRConfig();
    private static final Set<MediaType> SUPPORTED_TYPES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(new MediaType[]{
                    MediaType.image("png"), MediaType.image("jpeg"), MediaType.image("tiff"),
                    MediaType.image("bmp"), MediaType.image("gif"), MediaType.image("jp2"),
                    MediaType.image("jpx"), MediaType.image("x-portable-pixmap")
            })));
    private static Map<String,Boolean> TESSERACT_PRESENT = new HashMap<>();
    
    // these are set by dataFusion-tika{,-service} Main from command line options
    public static boolean ocrImagePreprocess = true;
    public static boolean ocrImageDeskew = true;
    public static int ocrTimeout = 300;
    public static int ocrResize = 200;
    public static boolean ocrPreserveInterwordSpacing = true;
    
    // timing
    private static AtomicLong count = new AtomicLong();
    private static AtomicDouble pythonTime = new AtomicDouble();
    private static AtomicDouble imageMagickTime  = new AtomicDouble();
    private static AtomicDouble tesseractTime = new AtomicDouble();

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        // If Tesseract is installed, offer our supported image types
        TesseractOCRConfig config = context.get(TesseractOCRConfig.class, DEFAULT_CONFIG);
        if (hasTesseract(config)) {
            return SUPPORTED_TYPES;
        }
        // Otherwise don't advertise anything, so the other image parsers
        //  can be selected instead
        return Collections.emptySet();
    }

    private void setEnv(TesseractOCRConfig config, ProcessBuilder pb) {
        String tessdataPrefix = "TESSDATA_PREFIX";
        Map<String, String> env = pb.environment();

        if (!config.getTessdataPath().isEmpty()) {
            env.put(tessdataPrefix, config.getTessdataPath());
        }
        else if(!config.getTesseractPath().isEmpty()) {
            env.put(tessdataPrefix, config.getTesseractPath());
        }
    }

    public boolean hasTesseract(TesseractOCRConfig config) {
        // Fetch where the config says to find Tesseract
        String tesseract = config.getTesseractPath() + getTesseractProg();

        // Have we already checked for a copy of Tesseract there?
        if (TESSERACT_PRESENT.containsKey(tesseract)) {
            return TESSERACT_PRESENT.get(tesseract);
        }
        // Try running Tesseract from there, and see if it exists + works
        String[] checkCmd = { tesseract };
        boolean hasTesseract = ExternalParser.check(checkCmd);
        TESSERACT_PRESENT.put(tesseract, hasTesseract);
        return hasTesseract;
     
    }
    
    private boolean hasImageMagick(TesseractOCRConfig config) {
        // Fetch where the config says to find ImageMagick Program
        String ImageMagick = config.getImageMagickPath() + getImageMagickProg();

        // Have we already checked for a copy of ImageMagick Program there?
        if (TESSERACT_PRESENT.containsKey(ImageMagick)) {
            return TESSERACT_PRESENT.get(ImageMagick);
        }

        // Try running ImageMagick program from there, and see if it exists + works
        String[] checkCmd = { ImageMagick };
        boolean hasImageMagick = ExternalParser.check(checkCmd);
        TESSERACT_PRESENT.put(ImageMagick, hasImageMagick);
        
        return hasImageMagick;
     
    }
    
//    private static boolean hasPython() {
//    	// check if python is installed and if the rotation program path has been specified correctly
//        
//    	boolean hasPython = false;
//    	
//		try {
//			Process proc = Runtime.getRuntime().exec("python -h");
//			BufferedReader stdInput = new BufferedReader(new InputStreamReader(proc.getInputStream(), "UTF-8"));
//			if(stdInput.read() != -1) {
//				hasPython = true;
//			}
//		} catch (IOException e) {
//
//		} 
//
//		return hasPython;	
//    }
    
    public void parse(Image image, ContentHandler handler, Metadata metadata, ParseContext context) throws IOException,
            SAXException, TikaException {
        TemporaryResources tmp = new TemporaryResources();
        FileOutputStream fos = null;
        TikaInputStream tis = null;
        try {
            int w = image.getWidth(null);
            int h = image.getHeight(null);
            BufferedImage bImage = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            File file = tmp.createTemporaryFile();
            fos = new FileOutputStream(file);
            ImageIO.write(bImage, "png", fos);
            tis = TikaInputStream.get(file);
            parse(tis, handler, metadata, context);
        } finally {
            tmp.dispose();
            if (tis != null)
                tis.close();
            if (fos != null)
                fos.close();
        }
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext parseContext)
            throws IOException, SAXException, TikaException {
        TesseractOCRConfig config = parseContext.get(TesseractOCRConfig.class, DEFAULT_CONFIG);
        config.setTimeout(ocrTimeout);
        config.setResize(ocrResize);
        config.setPreserveInterwordSpacing(ocrPreserveInterwordSpacing);
        // If Tesseract is not on the path with the current config, do not try to run OCR
        // getSupportedTypes shouldn't have listed us as handling it, so this should only
        //  occur if someone directly calls this parser, not via DefaultParser or similar
        if (! hasTesseract(config))
            return;

        TemporaryResources tmp = new TemporaryResources();
        try {
            TikaInputStream tikaStream = TikaInputStream.get(stream, tmp);

            //trigger the spooling to a tmp file if the stream wasn't
            //already a TikaInputStream that contained a file
            tikaStream.getPath();
            //this is the text output file name specified on the tesseract
            //commandline.  The actual output file name will have a suffix added.
            File tmpOCROutputFile = tmp.createTemporaryFile();

            // Temporary workaround for TIKA-1445 - until we can specify
            //  composite parsers with strategies (eg Composite, Try In Turn),
            //  always send the image onwards to the regular parser to have
            //  the metadata for them extracted as well
            _TMP_IMAGE_METADATA_PARSER.parse(tikaStream, new DefaultHandler(), metadata, parseContext);

            XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
            xhtml.startDocument();
            parse(tikaStream, tmpOCROutputFile, parseContext, xhtml, config, metadata);
            xhtml.endDocument();
        } finally {
            tmp.dispose();
        }
    }

    /**
     * Use this to parse content without starting a new document.
     * This appends SAX events to xhtml without re-adding the metadata, body start, etc.
     *
     * @param stream inputstream
     * @param xhtml handler
     * @param config TesseractOCRConfig to use for this parse
     * @throws IOException
     * @throws SAXException
     * @throws TikaException
     *
     * @deprecated use {@link #parseInline(InputStream, XHTMLContentHandler, ParseContext, TesseractOCRConfig)}
     */
    public void parseInline(InputStream stream, XHTMLContentHandler xhtml, TesseractOCRConfig config)
            throws IOException, SAXException, TikaException {
        parseInline(stream, xhtml, new ParseContext(), config);
    }

    /**
     * Use this to parse content without starting a new document.
     * This appends SAX events to xhtml without re-adding the metadata, body start, etc.
     *
     * @param stream inputstream
     * @param xhtml handler
     * @param config TesseractOCRConfig to use for this parse
     * @throws IOException
     * @throws SAXException
     * @throws TikaException
     *
     */
    public void parseInline(InputStream stream, XHTMLContentHandler xhtml, ParseContext parseContext,
                            TesseractOCRConfig config)
            throws IOException, SAXException, TikaException {
        // If Tesseract is not on the path with the current config, do not try to run OCR
        // getSupportedTypes shouldn't have listed us as handling it, so this should only
        //  occur if someone directly calls this parser, not via DefaultParser or similar
        if (! hasTesseract(config))
            return;

        TemporaryResources tmp = new TemporaryResources();
        try {
            TikaInputStream tikaStream = TikaInputStream.get(stream, tmp);
            File tmpImgFile = tmp.createTemporaryFile();
            parse(tikaStream, tmpImgFile, parseContext, xhtml, config, null);
        } finally {
            tmp.dispose();
        }
    }

    /**
     * This method is used to process the image to an OCR-friendly format.
     * @param streamingObject input image to be processed
     * @param config TesseractOCRconfig class to get ImageMagick properties
     * @throws IOException if an input error occurred
     * @throws TikaException if an exception timed out
     */
    private void processImage(File streamingObject, TesseractOCRConfig config, Metadata metadata) throws IOException, TikaException {
    	String angle = "0.00"; 
    	TemporaryResources tmp = new TemporaryResources();
    	if (ocrImageDeskew) {
	    	ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        	Timer t = new Timer();
            try {
		    	// fetch rotation script from resources
		    	InputStream in = getClass().getResourceAsStream("rotation.py");
		    	File rotationScript = tmp.createTemporaryFile();
		    	Files.copy(in, rotationScript.toPath(), StandardCopyOption.REPLACE_EXISTING);
		    	
		    	String cmd = "python " + rotationScript.getAbsolutePath() + " -f " + streamingObject.getAbsolutePath();
		    			
		    	DefaultExecutor executor = new DefaultExecutor();
		    	PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream);
		        executor.setStreamHandler(streamHandler);
		        
		        // determine the angle of rotation required to make the text horizontal
		        CommandLine cmdLine = CommandLine.parse(cmd);
                executor.execute(cmdLine);
                angle = outputStream.toString("UTF-8").trim();
                if (metadata != null) metadata.set("X-TIKA:Skew-Angle", angle);
            } catch(Exception e) {	
            	String err = outputStream.toString("UTF-8").trim();
            	if (err.length() > 0 && metadata != null) metadata.set("X-TIKA:Skew-Error", err);
            	LOG.warn("Can't run rotation.py to determine skew, so assume zero skew. Python said: " + err, e);
            }
            t.stop();
            pythonTime.addAndGet(t.elapsedSecs());
    	}
              
    	ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    	Timer t = new Timer();
		try {
	        // process the image - parameter values can be set in TesseractOCRConfig.properties
	    	String line = "convert -density " + config.getDensity() + " -depth " + config.getDepth() + 
	    			" -colorspace " + config.getColorspace() +  " -filter " + config.getFilter() + 
	    			" -resize " + config.getResize() + "% -rotate "+ angle + " " + streamingObject.getAbsolutePath() + 
	    			" " + streamingObject.getAbsolutePath();    	
	    	CommandLine cmdLine = CommandLine.parse(line);
	    	DefaultExecutor executor = new DefaultExecutor();
	    	PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream);
	        executor.setStreamHandler(streamHandler); // out & err
			executor.execute(cmdLine);
		} catch(Exception e) {
			String err = outputStream.toString("UTF-8").trim();
			if (metadata != null) metadata.set("X-TIKA:ImageMagick-Error", err);
			LOG.warn("ImageMagick failed so OCR orig image. ImageMagick said: " + err, e);
		} 
		t.stop();
		imageMagickTime.addAndGet(t.elapsedSecs());
       
        tmp.close();
    }
    
    private void parse(TikaInputStream tikaInputStream, File tmpOCROutputFile, ParseContext parseContext,
                       XHTMLContentHandler xhtml, TesseractOCRConfig config, Metadata metadata)
            throws IOException, SAXException, TikaException {
        File tmpTxtOutput = null;
        try {
            File input = tikaInputStream.getFile();
            long size = tikaInputStream.getLength();

            if (size >= config.getMinFileSizeToOcr() && size <= config.getMaxFileSizeToOcr()) {
            	if (ocrImagePreprocess) {
	            	// Process image if ImageMagick Tool is present
	            	if(hasImageMagick(config)) {
	                    // copy the contents of the original input file into a temporary file
	                    // which will be preprocessed for OCR
	                    TemporaryResources tmp = new TemporaryResources();
	                    try {
	                        File tmpFile = tmp.createTemporaryFile();
	                        FileUtils.copyFile(input, tmpFile);
	                        processImage(tmpFile, config, metadata);
	                        doOCR(tmpFile, tmpOCROutputFile, config);
	                    } finally {
	                        if (tmp != null) {
	                            tmp.dispose();
	                        }
	                    }
	            	} else {
	            		LOG.error("Image preprocessing requested but ImageMagick not found so it has been skipped");
	                    doOCR(input, tmpOCROutputFile, config);
	                }
            	} else {
            		doOCR(input, tmpOCROutputFile, config);
            	}

                // Tesseract appends the output type (.txt or .hocr) to output file name
                tmpTxtOutput = new File(tmpOCROutputFile.getAbsolutePath() + "." +
                        config.getOutputType().toString().toLowerCase(Locale.US));

                if (tmpTxtOutput.exists()) {
                    try (InputStream is = new FileInputStream(tmpTxtOutput)) {
                        if (config.getOutputType().equals(TesseractOCRConfig.OUTPUT_TYPE.HOCR)) {
                            extractHOCROutput(is, parseContext, xhtml);
                        } else {
                            extractOutput(is, xhtml);
                        }
                    }
                }
            } else {
            	LOG.warn("Image size {} bytes outside configured range {} - {} for OCR", new Object[]{size, config.getMinFileSizeToOcr(), config.getMaxFileSizeToOcr()});
            }
        } finally {
            if (tmpTxtOutput != null) {
                tmpTxtOutput.delete();
            }
        }
    }

    /**
     * no-op
     * @param params params to use for initialization
     * @throws TikaConfigException
     */
    @Override
    public void initialize(Map<String, Param> params) throws TikaConfigException {

    }

    @Override
    public void checkInitialization(InitializableProblemHandler problemHandler)
            throws TikaConfigException {
        //this will incorrectly trigger for people who turn off Tesseract
        //by sending in a bogus tesseract path via a custom TesseractOCRConfig.
        //TODO: figure out how to solve that.
//        if (! hasWarned()) {
//            if (hasTesseract(DEFAULT_CONFIG)) {
//                problemHandler.handleInitializableProblem(this.getClass().getName(),
//                        "Tesseract OCR is installed and will be automatically applied to image files unless\n" +
//                                "you've excluded the TesseractOCRParser from the default parser.\n"+
//                                "Tesseract may dramatically slow down content extraction (TIKA-2359).\n" +
//                                "As of Tika 1.15 (and prior versions), Tesseract is automatically called.\n" +
//                                "In future versions of Tika, users may need to turn the TesseractOCRParser on via TikaConfig.");
//                warn();
//            }
//        }
    }
    // TIKA-1445 workaround parser
    private static Parser _TMP_IMAGE_METADATA_PARSER = new CompositeImageParser();



    private static class CompositeImageParser extends CompositeParser {
        private static final long serialVersionUID = -2398203346206381382L;
        private static List<Parser> imageParsers = Arrays.asList(new Parser[]{
                new ImageParser(), new JpegParser(), new TiffParser()
        });
        CompositeImageParser() {
            super(new MediaTypeRegistry(), imageParsers);
        }
    }

    /**
     * Run external tesseract-ocr process.
     *
     * @param input
     *          File to be ocred
     * @param output
     *          File to collect ocr result
     * @param config
     *          Configuration of tesseract-ocr engine
     * @throws TikaException
     *           if the extraction timed out
     * @throws IOException
     *           if an input error occurred
     */
    private void doOCR(File input, File output, TesseractOCRConfig config) throws IOException, TikaException {
    	Timer t = new Timer();
        String[] cmd = { config.getTesseractPath() + getTesseractProg(), input.getPath(), output.getPath(), "-l",
                config.getLanguage(), "-psm", config.getPageSegMode(),
                config.getOutputType().name().toLowerCase(Locale.US),
                "-c",
                (config.getPreserveInterwordSpacing())? "preserve_interword_spaces=1" : "preserve_interword_spaces=0"};
        ProcessBuilder pb = new ProcessBuilder(cmd);
        setEnv(config, pb);
        final Process process = pb.start();

        process.getOutputStream().close();
        InputStream out = process.getInputStream();
        InputStream err = process.getErrorStream();

        logStream("OCR MSG", out, input);
        logStream("OCR ERROR", err, input);

        FutureTask<Integer> waitTask = new FutureTask<>(new Callable<Integer>() {
            public Integer call() throws Exception {
                return process.waitFor();
            }
        });

        Thread waitThread = new Thread(waitTask);
        waitThread.start();

        try {
            waitTask.get(config.getTimeout(), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            waitThread.interrupt();
            process.destroy();
            Thread.currentThread().interrupt();
            throw new TikaException("TesseractOCRParser interrupted", e);
        } catch (ExecutionException e) {
            // should not be thrown
        } catch (TimeoutException e) {
            waitThread.interrupt();
            process.destroy();
            throw new TikaException("TesseractOCRParser timeout", e);
        } finally {
        	t.stop();
        	tesseractTime.addAndGet(t.elapsedSecs());
        	Long n = count.incrementAndGet();
        	LOG.info("doOCR: OCR count = {}, Python {} secs, ImageMagick {} secs, Tesseract {} secs", new Object[]{n, pythonTime.get(), imageMagickTime.get(), tesseractTime.get()});
        }
    }

    /**
     * Reads the contents of the given stream and write it to the given XHTML
     * content handler. The stream is closed once fully processed.
     *
     * @param stream
     *          Stream where is the result of ocr
     * @param xhtml
     *          XHTML content handler
     * @throws SAXException
     *           if the XHTML SAX events could not be handled
     * @throws IOException
     *           if an input error occurred
     */
    private void extractOutput(InputStream stream, XHTMLContentHandler xhtml) throws SAXException, IOException {
        xhtml.startElement("div", "class", "ocr");
        try (Reader reader = new InputStreamReader(stream, UTF_8)) {
            char[] buffer = new char[1024];
            for (int n = reader.read(buffer); n != -1; n = reader.read(buffer)) {
                if (n > 0) {
                    xhtml.characters(buffer, 0, n);
                }
            }
        }
        xhtml.endElement("div");
    }

    private void extractHOCROutput(InputStream is, ParseContext parseContext,
                                   XHTMLContentHandler xhtml) throws TikaException, IOException, SAXException {
        if (parseContext == null) {
            parseContext = new ParseContext();
        }
        SAXParser parser = parseContext.getSAXParser();
        xhtml.startElement("div", "class", "ocr");
        parser.parse(is, new OfflineContentHandler(new HOCRPassThroughHandler(xhtml)));
        xhtml.endElement("div");
    }

    /**
     * Starts a thread that reads the contents of the standard output or error
     * stream of the given process to not block the process. The stream is closed
     * once fully processed.
     */
    private void logStream(final String logType, final InputStream stream, final File file) {
        new Thread() {
            public void run() {
                Reader reader = new InputStreamReader(stream, UTF_8);
                StringBuilder out = new StringBuilder();
                char[] buffer = new char[1024];
                try {
                    for (int n = reader.read(buffer); n != -1; n = reader.read(buffer))
                        out.append(buffer, 0, n);
                } catch (IOException e) {

                } finally {
                    IOUtils.closeQuietly(stream);
                }

                LOG.debug("{}", out);
            }
        }.start();
    }

    static String getTesseractProg() {
        return System.getProperty("os.name").startsWith("Windows") ? "tesseract.exe" : "tesseract";
    }

    static String getImageMagickProg() {
    	return System.getProperty("os.name").startsWith("Windows") ? "convert.exe" : "convert";
    }


    private static class HOCRPassThroughHandler extends DefaultHandler {
        private final ContentHandler xhtml;
        public static final Set<String> IGNORE = unmodifiableSet(
                "html", "head", "title", "meta", "body");

        public HOCRPassThroughHandler(ContentHandler xhtml) {
            this.xhtml = xhtml;
        }

        /**
         * Starts the given element. Table cells and list items are automatically
         * indented by emitting a tab character as ignorable whitespace.
         */
        @Override
        public void startElement(
                String uri, String local, String name, Attributes attributes)
                throws SAXException {
            if (!IGNORE.contains(name)) {
                xhtml.startElement(uri, local, name, attributes);
            }
        }

        /**
         * Ends the given element. Block elements are automatically followed
         * by a newline character.
         */
        @Override
        public void endElement(String uri, String local, String name) throws SAXException {
            if (!IGNORE.contains(name)) {
                xhtml.endElement(uri, local, name);
            }
        }

        /**
         * @see <a href="https://issues.apache.org/jira/browse/TIKA-210">TIKA-210</a>
         */
        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            xhtml.characters(ch, start, length);
        }

        private static Set<String> unmodifiableSet(String... elements) {
            return Collections.unmodifiableSet(
                    new HashSet<>(Arrays.asList(elements)));
        }
    }

    protected boolean hasWarned() {
        if (HAS_WARNED) {
            return true;
        }
        synchronized (LOCK) {
            if (HAS_WARNED) {
                return true;
            }
            return false;
        }
    }

    protected void warn() {
        HAS_WARNED = true;
    }
}


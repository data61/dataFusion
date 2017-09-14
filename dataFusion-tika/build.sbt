name := "dataFusion-tika"

// the one-jar classloader helpfully reports on conflicting classes (same package & name) from different jars
// (including whether the byte-code differs) and this has been used to set the following exclusions:
// jj2000 is older fork of jai-imageio-jpeg2000
//  
// tika-parsers and sentiment-analysis-parser both contain org/apache/tika/parser/sentiment/analysis/SentimentParser
// I guess the tika-parsers one is newer but still relies on other code in sentiment-analysis-parser?
// We don't use it so exclude sentiment-analysis-parser to avoid the conflict.
  

libraryDependencies ++= Seq(
  "org.apache.tika" % "tika-parsers" % "1.16" exclude("edu.ucar", "jj2000") exclude("edu.usc.ir", "sentiment-analysis-parser"),
  "com.github.jai-imageio" % "jai-imageio-core" % "1.3.1",        // add PDFBox support for TIFF
  "com.github.jai-imageio" % "jai-imageio-jpeg2000" % "1.3.0",    // add PDFBox support for jpeg2000
  "com.levigo.jbig2" % "levigo-jbig2-imageio" % "2.0",            // add PDFBox support for jbig2
  "org.xerial" % "sqlite-jdbc" % "3.19.3",                        // add to 'parse' sqlite files and embedded files
  "com.optimaize.languagedetector" % "language-detector" % "0.6", // tika-langdetect-1.15 dependency is 0.5, but we use language-detector directly, not via tika-langdetect
  // "org.apache.commons" % "commons-math3" % "3.6.1",            // distribution for sentence length statistics
  "com.typesafe" % "config" % "1.3.1",
  "com.github.scopt" %% "scopt" % "3.7.0",
  "com.jsuereth" %% "scala-arm" % "2.0",
  "org.scalatest" %% "scalatest" % "3.0.4" % "test"
)
  
com.github.retronym.SbtOneJar.oneJarSettings

mainClass in Compile := Some("au.csiro.data61.dataFusion.tika.Main")

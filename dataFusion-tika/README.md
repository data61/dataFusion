# dataFusion-tika

## Introduction

This project provides a library and multi-threaded CLI (command line interface) for bulk processing. It provides:

- access to [Apache Tika](https://tika.apache.org/) customized to OCR images embedded in PDFs (including TIFF, JPEG2000 and JBIG2, which are not handled by Tika out-of-the-box)
- some cleaning and filtering of Tika metadata
- augmentation of the metadata with the language of the text (`language-code` and `language-prob`)
- representation of results in dataFusion data structures

## Build, Configuration and Running

See the top level README.

Example:

    # CLI processing, with one file path per input line
    ls -1 src/test/resources/exampleData/PDF00{2,3}* | \
    java -jar target/scala-2.12/datafusion-tika_2.12-0.2-SNAPSHOT-one-jar.jar

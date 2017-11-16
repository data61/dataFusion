# dataFusion

## Introduction

The purpose is to extract useful information by fusing unstructured data with structured data.

This project:

1. extracts text and meta-data and performs language detection from a wide variety of unstructured document formats (PDF, Word, Excel etc.) using [Apache Tika](https://tika.apache.org/). Processing includes embedded documents, which in the case of images involves using [Tesseract](https://github.com/tesseract-ocr/tesseract/wiki) [OCR](https://en.wikipedia.org/wiki/Optical_character_recognition) to obtain the text.
2. performs [Named Entity Recognition](https://en.wikipedia.org/wiki/Named-entity_recognition) (NER);
4. provides batch search for known entities in documents, reporting locations of each match;
6. builds a network of entities that appear close together in documents and so are likely related in some way; 
7. provides web services to the above and multi-threaded command line interfaces;

## Project Structure
The top level directory provides the [sbt](http://www.scala-sbt.org/) build for the [Scala](http://scala-lang.org/) sub-projects in each of the child directories:
- `dataFusion-$name` for libraries; and
- `dataFusion-$name-service` for [RESTful](https://en.wikipedia.org/wiki/Representational_state_transfer) web services.

The library projects dataFusion-{tika,ner,search} all provide a multi-threaded command line interface (CLI) for efficient bulk processing without the need for web services and clients.

## Install Tools

To run the Scala code install:
- a JRE e.g. `sudo apt-get install openjdk-8-jre` (version 8 or higher is required by some dependencies);
- the build tool [sbt](http://www.scala-sbt.org/).

To develop [Scala](http://scala-lang.org/) code install:
- the above items (you may prefer to install the full JDK instead of just the JRE but I think the JRE is sufficient);
- the [Scala IDE](http://scala-ide.org/download/current.html).

## Build

The command:

    source sh/setenv
    sbt -J-Xmx2G clean test publish-local one-jar dumpLicenseReport

cleans out previous build products, runs unit tests, publishes libraries to your local Ivy repository (located at `~/.ivy2/`), builds [onejar](https://github.com/sbt/sbt-onejar) files and creates license reports on dependencies. Build products are found under each sub-project's `target` directory.

Notes:
- Without `-J-Xmx2G` tests may fail due to insufficient memory. 
- Without MITIE-models installed (see [dataFusion-ner](dataFusion-ner) for details) and `source sh/setenv` (with appropriate locations in that file) tests involving MITIE may fail.

## Run

The Scala programs are packaged by the build as a [onejar](https://github.com/sbt/sbt-onejar). This is a jar file containing all dependencies and run simply with: `java -jar {filename.jar}` (however dataFusion-ner and dataFusion-ner-service have additional dependencies as as noted in [dataFusion-ner](dataFusion-ner)). The `--help` command line option describes the available options.

Examples:

     source sh/setenv                       # set the environment for following commands

     # get help for the search CLI, running the one-jar from where the build creates it 
     java -jar dataFusion-search/target/scala-2.12/datafusion-search_2.12-0.2-SNAPSHOT-one-jar.jar --help


### Configuration
These projects have configuration in `src/main/resources/application.conf`, which uses `${?ENV_VAR}` syntax to define environment variables that may be set to override default values set in the file. For example dataFusion-ner's [application.conf](dataFusion-ner/src/main/resources/application.conf) sets the default location for MITIE's English NER model to `MITIE-models/english/ner_model.dat` (relative to whatever directory the program is run from) and allows this to be overridden by setting an environment variable `NER_MITIE_ENGLISH_MODEL`.

### Recommendations

- override all relative paths with environment variables specifying absolute paths; and
- set configuration environment variables for all dataFusion programs in `sh/setenv` and source this file prior to running any of the programs
- use the convenience script `sh/dfus` for shorter command lines as shown below.

Examples:

     source sh/setenv                       # set the environment for following commands

     dfus -h                                # get help for the sh/dfus script
     dfus tika --help                       # get help for the tika CLI
     dfus ner --help                        # get help for the ner CLI
     dfus search --help                     # get help for search CLI (same as Run example above)
     
     # run tika on all files under /collections; -m 7 to set 7GB Java heap; default output to tika.json
     find /collections -type f | dfus -m 7 tika
     
     # run tika only on the files listed in demoFiles.txt
     dfus -m 7 tika --output tika-demo.json < demoFiles.txt
     
     # run NER; default output to ner.json
     dfus -m 7 ner < tika.json
     
     # create bulk search index (location set in sh/setenv)
     dfus search --index < ner.json
     
     # bulk search for entities in entities.csv; default output to hits.json
     dfus search --searchCsv < entities.csv
     
     # merge search results (hits.json) into ner results (ner.json)
     # also create Ner entries from names in email headers; default output to gaz.json
     dfus util --hits hits.json --email < ner.json
     
     # proximity network building; default output to proximity-node.json and proximity-edge.json
     dfus util --proximity < gaz.json
     
     # start graph-service using data from proximity-node.json and proximity-edge.json; default port 8089
     dfus graph-service &
     
## Swagger Support

The `dataFusion-$name-service` web services use [Swagger](https://swagger.io/) to both
document their APIs and provide a user interface to call them (for use by developers rather than end users).
Each web service exposes an endpoint `http://host:port/api-docs/swagger.json` which provides the Swagger description of the service.

### Install swagger-ui

To install [swagger-ui](https://swagger.io/swagger-ui/) download it and copy the `dist` dir to `swagger-ui` on a web server.

### Use swagger-ui

- open the swagger-ui URL in a web browser, e.g. something like: `http://webServer/swagger-ui/`
- paste in the URL of web service's Swagger description, e.g. something like: `http://host:port/api-docs/swagger.json`

## Develop With Eclipse

The command:

    sbt eclipse

uses the [sbteclipse](https://github.com/typesafehub/sbteclipse/wiki/Using-sbteclipse) plugin to create the .project and .classpath files required by Eclipse (with source attachments for dependencies).

### Update Documentation of Third-party Dependencies

If libraries are changed, update the `3rd-party-licenses.md` for each sub-project.
After `sbt dumpLicenseReport` run:

    for i in */target/license-reports/*.md; do cp $i ${i%%/*}/3rd-party-licenses.md; done

## Software License

This software is released under the [GPLv3](LICENSE.txt). For alternative licensing arrangements please contact Warren.Bradey@data61.csiro.au. Each of the Scala sub-projects lists its dependencies and their licenses in 3rd-party-licenses.md.


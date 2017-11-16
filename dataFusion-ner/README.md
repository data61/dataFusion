# dataFusion-ner

## Introduction

This project provides a library and multi-threaded CLI (command line interface) for bulk processing.
It performs [Named Entity Recognition](https://en.wikipedia.org/wiki/Named-entity_recognition) using [CoreNLP](http://stanfordnlp.github.io/CoreNLP/), [OpenNLP](http://opennlp.apache.org/) and [MITIE](https://github.com/mit-nlp/MITIE).

## Build, Configuration and Running

This is mostly covered by the top level [README](../README.md), however MITIE has some particular requirements and
we use a local build of CoreNLP to get a bug fix made since the latest 3.8.0 release.

## MITIE Requirements

### Building MITIE libraries

Note: The MITIE java library and native shared library have been added to this source code repository,
so this section can be skipped. It serves to document how they were created and how they can be updated. 

See the [MITIE github repo](https://github.com/mit-nlp/MITIE) for full instructions.

	sudo apt-get install libopenblas-dev cmake swig
	
	DIR=~/sw      # somewhere to build MITIE
	mkdir -p $DIR
	cd $DIR
	git clone https://github.com/mit-nlp/MITIE
	cd MITIE
	
	mkdir mitielib/java/build
	cd mitielib/java/build
	cmake ..
	cmake --build . --config Release --target install
	
Install the libraries where this project expects them:

	cd dataFusion-ner
	mkdir -p lib MITIE-native
	cp $DIR/MITIE/mitielib/java/build/lib/javamitie.jar lib/
	cp $DIR/MITIE/mitielib/java/build/lib/libjavamitie.so MITIE-native/
	
### Installing MITIE models

The MITIE models are too large to include in the source code repository and these steps are required:

    cd dataFusion-ner
    # URL's from: https://github.com/mit-nlp/MITIE
    
    # English
    wget https://github.com/mit-nlp/MITIE/releases/download/v0.4/MITIE-models-v0.2.tar.bz2
    tar xvfj MITIE-models-v0.2.tar.bz2 MITIE-models/english/ner_model.dat # only extract what we need
    
    # Spanish
    wget https://github.com/mit-nlp/MITIE/releases/download/v0.4/MITIE-models-v0.2-Spanish.zip
    unzip MITIE-models-v0.2-Spanish.zip MITIE-models/spanish/ner_model.dat  # only extract what we need    


### MITIE Configuration

The following environment variables are used to locate the MITIE shared library and models:

    export LD_LIBRARY_PATH=${PWD}/MITIE-native # directory containing libjavamitie.so
    export NER_MITIE_ENGLISH_MODEL=${PWD}/MITIE-models/english/ner_model.dat
    export NER_MITIE_SPANISH_MODEL=${PWD}/MITIE-models/spanish/ner_model.dat

    java -jar target/scala-2.12/datafusion-ner_2.12-0.1-SNAPSHOT-one-jar.jar --help
    
Configuration to run in Eclipse:

Select `Build Path` > `Configure Build Path` > `Source` > `dataFusion-ner/src/main/scala` > `Native library location`
and add the MITIE-native directory.

## CoreNLP Local Build

    git clone https://github.com/stanfordnlp/CoreNLP.git
    cd CoreNLP
    vi pom.xml   # set version to 3.8.1-20170922, must match what is in build.sbt
    # download the latest stanford-corenlp-current-models.jar from the github page
    mv ~/Downloads/stanford-corenlp-current-models.jar stanford-corenlp-3.7.0-models.jar # yes 3.7.0 for mvn to find it
    mvn install  # install to ~/.m2/repository/



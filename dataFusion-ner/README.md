# dataFusion-ner

## Introduction

This project provides a library and multi-threaded CLI (command line interface) for bulk processing.
It performs [Named Entity Recognition](https://en.wikipedia.org/wiki/Named-entity_recognition) using [CoreNLP](http://stanfordnlp.github.io/CoreNLP/), [OpenNLP](http://opennlp.apache.org/) and [MITIE](https://github.com/mit-nlp/MITIE).

## Build, Configuration and Running

This is mostly covered by the top level [README](../README.md), however MITIE is C++ code and has some particular requirements satisfied by the script `build-MITIE.sh`:

1. a platform independent java library `lib/javamitie.jar`
2. a platform dependent shared library `MITIE-native/{platform}/libjavamitie.so`
3. language dependent models e.g. `MITIE-models/english/ner_model.dat`
4. environment variables to access the above

1 and 2 (for both ubuntu and centos) are checked into the code repository (so if you use one of these hopefully you won't need to build MITIE except to use a newer version), however 3 (language models) are large, not in the code repository, and you will need to run the script to get them and to create the script `sh/setenv.{platform}` (for 4).

Run `build-MITIE.sh` with no args to do as little as necessary, or `build-MITIE.sh --clean` to start from scratch and build the lastest MITIE.

Configuration to run in Eclipse:

Select `Build Path` > `Configure Build Path` > `Source` > `dataFusion-ner/src/main/scala` > `Native library location`
and add the `MITIE-native/{platform}` directory.


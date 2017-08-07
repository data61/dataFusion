#! /not/to/be/execed
 
# sbt tests using MITIE will fail without these vars being appropriately set
export LD_LIBRARY_PATH=${PWD}/dataFusion-ner/MITIE-native # directory containing libjavamitie.so
export NER_MITIE_ENGLISH_MODEL=${PWD}/dataFusion-ner/MITIE-models/english/ner_model.dat
export NER_MITIE_SPANISH_MODEL=${PWD}/dataFusion-ner/MITIE-models/spanish/ner_model.dat

export SEARCH_DOC_INDEX=${PWD}/dataFusion-search/docIndex
export SEARCH_META_INDEX=${PWD}/dataFusion-search/metaIndex
export SEARCH_NER_INDEX=${PWD}/dataFusion-search/nerIndex

export PATH=${PWD}/scripts/src/sh:/usr/sbin:/usr/bin:/sbin:/bin

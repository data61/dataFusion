#! /bin/sh

set -vex

# run in dataFusion-ner dir
DFUS_NER=$PWD

# ubuntu has: ID=ubuntu but centos has: ID="centos"
OS=`sed --regexp-extended --quiet 's/^ID="?([a-z]+)"?$/\1/p' /etc/os-release`

NER=ner_model.dat
EN=MITIE-models/english
ES=MITIE-models/spanish

# when these files are missing the script makes a fresh start, else it does as little as necessary
[ "$1" = "--clean" ] && rm -rf MITIE MITIE-native/$OS/libjavamitie.so "$EN/$NER" ../sh/setenv.$OS # "$ES/$NER"

# Build MITIE java jar and native shared library
[ -d MITIE ] || git clone https://github.com/mit-nlp/MITIE
[ -f MITIE-native/$OS/libjavamitie.so ] || {
  BUILD=MITIE/mitielib/java/build-$OS
  rm -rf $BUILD
  mkdir -p $BUILD
  cd $BUILD
  cmake ..
  cmake --build . --config Release --target install

  # Install MITIE libraries where this project expects them
  mkdir -p $DFUS_NER/lib $DFUS_NER/MITIE-native/$OS
  cp lib/javamitie.jar $DFUS_NER/lib
  cp lib/libjavamitie.so $DFUS_NER/MITIE-native/$OS

  cd $DFUS_NER
}

# Install English NER model
[ -r "$EN/$NER" ] || {
  echo "Downloading English models ..."
  EN_BZ2=MITIE-models-v0.2.tar.bz2
  curl --location https://github.com/mit-nlp/MITIE/releases/download/v0.4/$EN_BZ2 > $EN_BZ2
  tar xvfj $EN_BZ2 $EN/$NER  # only extract EN NER model
  rm $EN_BZ2
}

# Install Spanish NER model
if false; then
[ -r "$ES/$NER" ] || {
  echo "Downloading Spanish models ..."
  ES_ZIP=MITIE-models-v0.2-Spanish.zip
  curl --location https://github.com/mit-nlp/MITIE/releases/download/v0.4/$ES_ZIP > $ES_ZIP
  unzip $ES_ZIP $ES/$NER  # only extract ES NER model
  rm $ES_ZIP
}
fi

# create a file that can be sourced to set required environment variables
[ -r "../sh/setenv.$OS" ] || {
cat > ../sh/setenv.$OS <<EoF1
#! /not/to/be/execed
 
# used by sh/dfus
export DFUS_DIR=\${PWD}
export SCALA_VER=2.12
export DFUS_VER=`sed --regexp-extended --quiet 's/^.*"(.+)"$/\1/p' ../version.sbt`

# needed by dataFusion-ner (including sbt tests)
export LD_LIBRARY_PATH=\${DFUS_DIR}/dataFusion-ner/MITIE-native/$OS   # directory containing libjavamitie.so
export NER_MITIE_ENGLISH_MODEL=\${DFUS_DIR}/dataFusion-ner/$EN/$NER
# export NER_MITIE_SPANISH_MODEL=\${DFUS_DIR}/dataFusion-ner/$ES/$NER

SEARCH_DIR=\${DFUS_DIR}/dataFusion-search
export SEARCH_SYNONYMS=\${SEARCH_DIR}/synonyms.txt
export SEARCH_DOC_INDEX=\${SEARCH_DIR}/docIndex
export SEARCH_META_INDEX=\${SEARCH_DIR}/metaIndex
export SEARCH_NER_INDEX=\${SEARCH_DIR}/nerIndex

export PATH=\${DFUS_DIR}/sh:/usr/sbin:/usr/bin:/sbin:/bin

cat <<EoF2
Source this file with the dataFusion source tree top level dir as the current directory.
It sets env vars for the project and sets a restricted PATH with dataFusion/sh as the highest priority item.

Try "dfus -h" to get started.
EoF2

EoF1
}



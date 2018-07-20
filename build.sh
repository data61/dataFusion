#! /bin/sh

set -vex

# ubuntu has: ID=ubuntu but centos has: ID="centos"
OS=`sed --regexp-extended --quiet 's/^ID="?([a-z]+)"?$/\1/p' /etc/os-release`

# build MITIE (native code used by dataFusion-ner)
# do as little as necessary by default, add --clean option to do everything from scratch
cd dataFusion-ner
./build-MITIE.sh # --clean
cd ..

# set environment
. ./sh/setenv.$OS

# run Scala build
sbt one-jar                                                        # minimal, or
# sbt -J-Xmx3G clean test publish-local one-jar dumpLicenseReport  # the works
# move/rename the license reports
# for i in */target/license-reports/*.md; do cp $i ${i%%/*}/3rd-party-licenses.md; done


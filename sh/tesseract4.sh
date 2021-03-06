#! /bin/bash

# Tika calls tesseract 3 with args: imageInFile txtOutFile -l eng -psm 1 txt -c preserve_interword_spaces=0
# Tesseract 4 no longer accepts txt (which is the default anyway) so filter that out
declare -a args
for a in "$@"; do
  [[ "$a" != txt ]] && args+=("$a")
done

# if manually built and installed
# ROOT=/usr/local
ROOT=/usr
# put *.traindata files under $ROOT/share
# mv $ROOT/share/tessdata/pdf.ttf $ROOT/share
LD_LIBRARY_PATH=$ROOT/lib TESSDATA_PREFIX=$ROOT/share/tessdata $ROOT/bin/tesseract "${args[@]}"

# if installed from ppa:alex-p/tesseract-ocr
# /usr/bin/tesseract "${args[@]}"

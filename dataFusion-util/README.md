# dataFusion-util

## Introduction
This project provides command line utilities for:
- converting bulk entity search results into the NER data format and merging into the NER results (`--hits` CLI option)
- finding mentions of people in email headers and merging into the NER results (`--email` CLI option)
- network building from the NER results (including bulk search and email headers, `--proximity` CLI option)

Additionally the `--resetId` CLI option is provided to reallocate the id's in a NER format JSON file. This can be useful in the case of merging multiple partial tika runs where the ids would otherwise not be unique. 

## Network Building
Network building uses the follow named entities (see [dataFusion-common](../dataFusion/common) for the definitions of the NER structure and these fields):
- `impl=D62GAZ` and `typ=PERSON|PERSON2|ORGANIZATION`;
- `impl=D61EMAIL` and `typ=FROM|TO|CC|BCC`

Parameters are the decay value (set by the `--decay` CLI option with default value 500 characters) and a cutoff which is `5 * decay`.

    (weight, count) for edge representing co-occurrences of named entities n1 and n2 in collection c =
      sum over documents d in collection c
      sum over sub-documents e in d (main content and each embedded document)
      sum over pairs of instances of n1 & n2 in e, where dist = abs( n2.offStr - n2.offStr ) < cutoff
      weight = exp( - dist / decay ), count = 1
      

## Build, Configuration and Running

See the top level README.

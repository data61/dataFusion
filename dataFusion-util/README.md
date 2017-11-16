# dataFusion-util

## Introduction
This project provides command line utilities for:
- converting and merging [Search Result JSON format](../dataFusion-common#search-result-json-format) into the [Document JSON format](../dataFusion-common#document-json-format) (`--hits` CLI option);
- finding mentions of people in email headers and merging results into the [Document JSON format](../dataFusion-common#document-json-format) (`--email` CLI option);
- network building from the [Document JSON format](../dataFusion-common#document-json-format) (`--proximity` CLI option); and
- reallocating the id's in a [Document JSON format](../dataFusion-common#document-json-format) file, which can be useful in the case of merging multiple partial tika runs where the joint ids would otherwise not be unique (`--resetId` CLI option). 

## Network Building
Network building uses the follow named entities (see [NER Structure](../dataFusion-common#ner-structure) for details):
- `impl=D62GAZ` and `typ=PERSON|PERSON2|ORGANIZATION`;
- `impl=D61EMAIL` and `typ=FROM|TO|CC|BCC`

Parameters are the decay value (set by the `--decay` CLI option with default value 500 characters) and a cutoff which is `5 * decay`.

    (weight, count) for edge representing co-occurrences of named entities n1 and n2 in collection c =
      sum over documents d in collection c
      sum over sub-documents e in d (main content and each embedded document)
      sum over pairs of instances of n1 & n2 in e, where dist = abs( n2.offStr - n2.offStr ) < cutoff
      weight = exp( - dist / decay ), count = 1
      
The edges computed above (with weight, count > 0) are written to proximity-edge.json and the nodes referenced 

## Build, Configuration and Running

See the top level README.

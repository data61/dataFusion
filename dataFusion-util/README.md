# dataFusion-util

## Introduction
This project provides command line utilities for:
- Converting and merging [Search Result JSON format](../dataFusion-common#search-result-json-format) into the [Document JSON format](../dataFusion-common#document-json-format) (`--hits` CLI option).
- Parsing content for mentions of people in email headers and merging results into the [Document JSON format](../dataFusion-common#document-json-format) (`--email` CLI option). If the resulting `offStr` (see [NER Structure](../dataFusion-common#ner-structure)) matches that of a NER with `impl=D61GAZ` and `typ=PERSON|PERSON2` then the `score` and `extRef` are taken from that NER. Otherwise extRef is not set and score is computed using the Lucene's IDF formula if the `--emailIDF` option is true (default) else it's set to 1.0. 
- Parsing content for age soon after a person's name and merging results into the [Document JSON format](../dataFusion-common#document-json-format) (`--age` CLI option). Age is recognized as a number from 18 - 99 inclusive, either: parenenthesized immediately after a name (a NER with `impl=D61GAZ` and `typ=PERSON|PERSON2`) and not followed by further digits (to avoid telephone number area codes); or within 50 chars and following the word "age" or "aged" (only applied to the closest preceding person's name). The `extRef` is set from from the NER representing the name and `score` is set to 1.0.
- network building from the [Document JSON format](../dataFusion-common#document-json-format) (`--proximity` CLI option); and
- reallocating the id's in a [Document JSON format](../dataFusion-common#document-json-format) file, which can be useful in the case of merging multiple partial tika runs where the joint ids would otherwise not be unique (`--resetId` CLI option). 

The CLI options `--hits`, `--email` and `--age` can be used jointly.

## Network Building
Network building uses the follow named entities (see [NER Structure](../dataFusion-common#ner-structure) for details):
- `impl=D61GAZ` and `typ=PERSON|PERSON2|ORGANIZATION`;
- `impl=D61EMAIL` and `typ=FROM|TO|CC|BCC`

Documents are grouped into collections.
Documents in the filesystem are under (but not necessarily directly under) a directory that represents their collection.
The CLI option `--collectionRe` specifies a [regex](https://en.wikipedia.org/wiki/Regular_expression) to extract the collection from a document's path.
The default value for this option, `/collection/([^/]+)/`, is suitable if `collection` is the common parent directory for all collections.  

Parameters are the decay value (set by the `--decay` CLI option with default value 500 characters) and a cutoff which is `5 * decay`.

    (weight, count) for an edge representing co-occurrences of named entities n1 and n2 in collection c =
      sum over documents d in collection c
      sum over sub-documents e in d (main content and each embedded document)
      sum over pairs of instances of n1 & n2 in e, where dist = abs( n2.offStr - n1.offStr ) < cutoff
      weight = exp( - dist / decay ), count = 1
      
The edges computed above (with count > 0) are written in [Edge JSON format](../dataFusion-common#node-and-edge-json-formats) to proximity-edge.json and the nodes referenced in these edges are written in [Node JSON format](../dataFusion-common#node-and-edge-json-formats) to proximity-node.json.

## Build, Configuration and Running

See the top level [README](../README.md).
The score computation for the `--emailIDF` option requires term document frequencies from the Lucene index, which is located using the configuration from dataFusion-search.

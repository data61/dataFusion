# dataFusion-search

## Introduction

This project provides:

- a search library
- an indexer
- a multi-threaded CLI (command line interface) for high performance bulk searching for known entities
- other specialised command line tools (see --help)

This is different from the Solr index in that:
- results are at the level of embedded document (e.g. a main document with embIdx = -1 or a specific embedded document with embIdx >= 0) c.f. the Solr index which is at the level of whole document (main content with all embedded content)
- it very efficiently returns the word and character offsets to the location of each match

## Build, Configuration and Running

See the top level README.


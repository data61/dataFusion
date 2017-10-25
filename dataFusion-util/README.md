# dataFusion-util

## Introduction

This project provides command line utilities for:
- converting bulk entity search results into the NER data format and merging into the NER results (--hits option)
- finding mentions of people in email headers and merging into the NER results (--email option)
- network building from the NER results (including bulk search and email headers, --proximity option)

Additionally the --resetId option is provided to reallocate the id's in a NER format JSON file. This can be useful in the case of merging multiple partial tika runs where the ids would otherwise not be unique. 

## Build, Configuration and Running

See the top level README.

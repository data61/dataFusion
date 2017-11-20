# dataFusion-search-service

## Introduction

This project provides [RESTful](https://en.wikipedia.org/wiki/Representational_state_transfer) web services based on [dataFusion-search](../dataFusion-search).

## Build, Configuration, Running and Swagger Support

See the top level [README](../README.md). This will not run concurrently with the dataFusion-search CLI, unless they are configured to use different search indices, because Lucene takes an exclusive lock on its index.


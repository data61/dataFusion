# dataFusion-tika-service

## Introduction

This project provides a [RESTful](https://en.wikipedia.org/wiki/Representational_state_transfer) web service based on dataFusion-tika.

## Build, Configuration and Running

See the top level README.

Example:

    # run web service
    java -jar target/scala-2.12/datafusion-tika_2.12-0.2-SNAPSHOT-one-jar.jar
    # get swagger description (useful when loaded into Swagger UI)
    curl http://localhost:9998/api-docs/swagger.json
    # process a file
    curl --upload-file src/test/resources/exampleData/PDF002.pdf http://localhost:9998/tika?path=PDF002.pdf

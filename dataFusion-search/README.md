# dataFusion-search

## Introduction
This project provides:
- a search library
- an indexer
- a multi-threaded CLI (command line interface) for high performance bulk searching for known entities
- other specialised command line tools (see --help)

Search results are at the level of embedded document (e.g. a main document with embIdx = -1 or a specific embedded document with embIdx >= 0). Please see [Search Result JSON format](../dataFusion-common#search-result-json-format) for details of the output.

## Indexing
The `--index` CLI option creates the search index (at a location specified in [configuration](../README.md#configuration)). The input is in the [Document JSON format](../dataFusion-common#document-json-format) with the `content` and `embedded[].content` fields containing the text which is searched. The `meta` and `ner` data (again both main and embedded) is also separately indexed and can be searched using the [dataFusion-search-service](./dataFusion-search-service).

## Search Strategy
### Tokenization and Punctuation
Lucene's default `StandardTokenizer` removes punctuation, but as some organizations use punctuation as significant parts of their name this project uses Lucene's `WhitespaceTokenizer` and `LowerCaseFilter` with a custom `TrailingPunctuationFilter` to remove trailing commas, full stops etc. for a search which is case insensitive, but sensitive to non-trailing punctuation.
### Synonyms
Lucene's `SynonymGraphFilter` is used to map synonyms specified in a file `synonyms.txt` (the location is specified in [configuration](../README.md#configuration)), initially set to map "proprietary" to "pty" and "limited" to "ltd", but can be updated by the user. The synonym mapping should be consistent for indexing and searching.
### Organizations
A search hit must match all tokens in the query with tokens in the same order.
### People
A search hit must match all tokens in the query, but the tokens may appear in any order.
A phrase search for unordered terms (e.g. for PERSON|PERSON2) produces spurious matches where all terms are matched but not with the correct number of occurrences e.g. “Aaron H Aaron” matches “Aaron H H”. Fetching the text to check the number of occurrences would negatively impact the performance of the search, so this check is deferred to `dataFusion-util --hits` processing where the text is already available. Consequently the Search Result JSON (hits.json) contains the spurious matches, but they are filtered out from gaz.json.
### Scoring
A query is assigned an [IDF](https://en.wikipedia.org/wiki/Tf%E2%80%93idf#Inverse_document_frequency) score  calculated using [Lucene’s formula](https://lucene.apache.org/core/7_1_0/core/org/apache/lucene/search/similarities/TFIDFSimilarity.html). If this score is below the threshold set by the `--minScore` CLI option (default 3.5) then the query is deemed to be insufficiently distiguishing and is skipped.
### Query Generation from CSV
The `--searchCsv`  option generates queries from CSV data. If a type field is 'BUS' the record represents an organization, otherwise it represent a person.
- People's names are expected to be segmented into 3 fields for the person's family, first given and other names.
  - Where the 3 name fields for a person are non-blank a query is generated to search for all tokens in the name. The query and any resultant hits have `typ=PERSON`.
  - Where the first and family name fields are non-blank (whether or not the other names field is non-blank) a query is generated to search for all tokens in these two fields. The query and any resultant hits have `typ=PERSON2`.
- Organization names are in a single field. Where this contains at least 2 tokems a query is generated to search for all tokens in the name. The query and any resultant hits have `typ=ORGANIZATION`.
- A numeric id field is carried through from the CSV to the query and the results, to facilitate integration with other systems.
- Queries for the same name and `typ` are combined into a single query with multiple id values in `ExtRef.ids[]`.

## Build, Configuration and Running

See the top level [README](../README.md).


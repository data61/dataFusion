# dataFusion - ui/network

## Introduction
This project consists of static files providing a demonstration web user interface of a network graph of entities found in proximity in unstructured documents.

The visualization is based on [D3's force layout](https://github.com/d3/d3-force/blob/master/README.md).

[ECMAScript](https://en.wikipedia.org/wiki/ECMAScript) 6 is used so the web page only runs in some modern browsers (Chrome, Firefox, Edge, not yet Safari).

## Running

CORS access to servers isn't working from a `file:` URL. To use python's simple web server to serve the UI over HTTP, run `python3 -m http.server`. Access the UI at: http://localhost:8000/.

## Usage

This user interface provides a many input fields affecting the graph layout that would probably not be provided in a production user interface. This is intended to allow experimentation to determine appropriate parameters for a production user interface depending on the usage scenario.

### Description of input fields

- `include person nodes using only first and family names` : If not ticked then we use only Ner's with `impl=D61GAZ` and `typ=PERSON|ORGANIZATION`. If ticked we use the above plus: `impl=D61GAZ` and `typ=PERSON2`; and `impl=D61EMAIL` and `typ=FROM|TO|CC|BCC`, which includes many more people at the expense of some lower quality matches.
- `collections`: tick the collections you want included in the graph (none ticked includes all collections)
- `charge strength` : use a bigger negative number to increase the force pushing nodes apart (like electric charge). The layout balances a central attracting force, this charge pushing nodes apart and links/edges that try to maintain a fixed length. You may need to adjust this value by several orders of magnitude to have much effect.
- `distance range: log scale, from, to` : the graph-service provides a totalWeight over the specified collections for an edge. An edge length is calculated as the reciprocal of this weight (so closer represents a stronger relationship). However this varies too widely to be directly applied in the visualisation and these parameters map this length to the actual length provided to the force simulation. If `log scale` is ticked then the natural log is taken, which compresses the range so that changes in smaller values are more apparent. Then a linear transform is applied mapping the smallest value to `from` and the largest to `to`. 
- `node radius range: log scale, from, to` : the graph-service provides a node score implemented as per Lucene's IDF formula representing the rarity of the terms in the name across all collections. A high score corresponds loosely to a lower chance of spurious matches. The same transformations as described above for "distance range" are applied to this score to set a node's radius.
- `edge width range: log scale, from, to` :  the graph-service provides a totalCount of the number of times a pair of nodes contribute to the totalWeight (that is the number of co-occurrences within 5* the decay value in the specified collections). The same transformations as described above for "distance range" are applied to this totalCount to set an edge's width.
- `top` : is the maximum number of edges to display. Longer edges are filtered out to reduce the number to that specified.
- `Top Connected` : displays the `top` shortest edges with the specified collections.
- `nodeId` : is set by clicking on any node in a network graph.
- `extRefId` : can take a manually entered ExtRef id (e.g. a `client_intrnl_id`).
- `maxHops` : the number of edges to traverse out from the starting node in constructing a "local network".
- `Local Network` : displays the local network around a specified starting node. If `nodeId` is set it is the starting node, otherwise the node corresponding to `extRefId` is used. Use of `extRefId` with `include person nodes using only first and family names` could result in two starting nodes, one `typ=PERSON` and the other `typ=PERSON2`.




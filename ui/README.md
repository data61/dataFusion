# dataFusion - ui

## Introduction
Two user interfaces are provided here:

- network: an initial user interface to experiment with [D3's force layout](https://github.com/d3/d3-force/blob/master/README.md) for network visualization; and
- bubble: which refines the above and adds a new "bubble" representation, which is cleaner in apparence and less information dense.

Both user interfaces consist of static files providing a demonstration web user interface of a network graph of entities found in proximity in unstructured documents. The data for the visualizations is provided by [dataFusion-graph-service](../dataFusion-graph-service).

[ECMAScript](https://en.wikipedia.org/wiki/ECMAScript) 6 is used so the user interfaces only run in some modern browsers (Chrome, Firefox, Edge, not yet Safari).

## Running

[CORS](https://developer.mozilla.org/en-US/docs/Web/HTTP/CORS) access to servers isn't working from a `file:` URL. To use python's simple web server to serve the UI over HTTP, run `python3 -m http.server`. Access the UI at: http://localhost:8000/.

## Usage

This user interface provides many input fields affecting the graph layout that would probably not be provided in a production user interface. This is intended to allow experimentation to determine appropriate parameters for a production user interface, which will vary depending on the usage scenario.

![Network Graph](../images/network.png "Network Graph")

### Description of Input Fields

- `include person nodes using only first and family names`
If not ticked then we use only NER's with `impl=D62GAZ` and `typ=PERSON|ORGANIZATION`. If ticked we use the above plus: `impl=D61GAZ` and `typ=PERSON2`; and `impl=D61EMAIL` and `typ=FROM|TO|CC|BCC`, which includes many more people at the expense of some lower quality matches. Please see [NER Structure](../dataFusion-common#ner-structure) for details.

- `collections`
Tick the collections you want included in the graph (none ticked includes all collections).

- `charge strength`
Use a bigger negative number to increase the electric charge like force pushing nodes apart. The layout balances a central attracting force, this charge force pushing nodes apart and links/edges that try to maintain a fixed length. You may need to adjust this value by several orders of magnitude to have much effect.

- `distance range: log scale, from, to`
The [dataFusion-graph-service](../dataFusion-graph-service) provides a totalWeight over the specified collections for an edge. An edge length is calculated as the reciprocal of this weight (so closer represents a stronger relationship). However this varies too widely to be directly applied in the visualisation and these parameters map this length to the length provided to the force simulation. If `log scale` is ticked then the natural log is taken, which compresses the range so that changes in smaller values are more apparent. Then a linear transform is applied mapping the smallest value to `from` and the largest to `to`.

- `node radius range: log scale, from, to`
The [dataFusion-graph-service](../dataFusion-graph-service) provides a node score implemented as per Lucene's IDF formula representing the rarity of the terms in the name across all documents in all collections. A high score corresponds loosely to a lower chance of spurious matches. The same transformations as described above for "distance range" are applied to this score to set a node's radius.

- `edge width range: log scale, from, to`
The [dataFusion-graph-service](../dataFusion-graph-service) provides a totalCount of the number of times a pair of nodes contribute to the totalWeight (that is the number of co-occurrences within 5* the decay value in the specified collections). The same transformations as described above for "distance range" are applied to this totalCount to set an edge's width.

- `maxEdges`
The maximum number of edges to display.

- `minScore`
The minimum `score` of nodes to display. Low IDF scores reflect names without highly distinguishing terms and so bear a higher probability of spurious matches. Many (possibly spurious) connections to a low scoring node can obscure more important connections. Raising `minScore` above that of the low scoring node can address this. 

- `Top Connected`
Display the `maxEdges` shortest edges within the specified collections.

- `nodeId`
The central or starting node for the `Local Network` graph. Set by clicking a node in any network graph (or may be manually entered).

- `extRefId`
An alternative way to specify the central or starting node for a `Local Network` graph, using a manually entered ExtRef id (e.g. a `client_intrnl_id`).

- `maxHops`
The number of edges to traverse out from the starting node in constructing a `Local Network` graph.

- `Local Network`
Display the local network around the specified starting node. The graph includes the starting node and all edges and nodes on paths up to length `maxHops` from the starting node. If this results in more than `maxEdges` then nodes most distant from the starting node and corresponding edges are removed to obtain `maxEdges`. If `nodeId` is set it is the starting node, otherwise the node corresponding to `extRefId` is used. Use of `extRefId` with `include person nodes using only first and family names` could result in two starting nodes, one `typ=PERSON` and the other `typ=PERSON2`.

### Description of Network Graph

Nodes in the graph represent named entities selected as described in the description above for the input field `include person nodes using only first and family names`.  A tooltip provides details of the named entity, including its `typ`, IDF `score` and any known associated [ExtRef](../dataFusion-common#extref-structure) `ids`. The size of the node reflects the `score` and its colour reflects its `typ`.

Edges in the graph represent repeated proximity of the connected nodes within the specified `collections`. For each edge a `weight` and `count` is computed per collection by [dataFusion-util](../dataFusion-util). A tooltip provides details of these weights and counts as well as their sum over the specified `collections`. The edge length reflects the reciprocal of the sum of the weights over the specified `collections`, so close proximity and repeated proximity contribute to a short edge. The edge width reflects the sum of the counts over the specified `collections`, so short edges will usually also be thicker. A long thick edge represents high repeated co-occurences but with more distance between the entities on average. A short thin edge represents low repeated co-occurences but with less distance between the entities on average.


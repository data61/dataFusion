
var svg = d3.select("svg"),
    width = +svg.attr("width"),
    height = +svg.attr("height");

var color = d3.scaleOrdinal(d3.schemeCategory20);
var typs = [ 'PERSON', 'PERSON2', 'D61EMAIL', 'ORGANIZATION' ];
function nodeColour(n) {
  return color(1 + typs.indexOf(n.typ));
}

var simulation;

var nodeTitle = d => d.extRef.name + ' (typ: ' + d.typ + ', score: ' + d.score + ', ids: ' + d.extRef.ids + ')';

function edgeTitle(d) {
  var t = "weight, count in selected collections: " + [ d.totalWeight, d.totalCount ];
  var w = d.weights;
  for (c in w) t += "\n  weight, count in " + c + ": " + w[c];
  return t;
};

var edgeLength = d => 1/d.totalWeight;

function doGraph(graph, p) {
  console.log('graph =', graph);

  svg.selectAll("g").remove();
  
  var edgeScale = (
    p.edgeWidthLogScale
      ? d3.scaleLog().base(2.71828)
      : d3.scaleLinear()
    )
    .domain(d3.extent(graph.edges, e => e.totalCount)) // [ min, max ]
    .range([p.edgeWidthFrom, p.edgeWidthTo]);
    
  var link = svg.append("g")
    .attr("class", "links")
    .selectAll("line")
    .data(graph.edges)
    .enter().append("line")
      .attr("stroke-width", e => edgeScale(e.totalCount));

  link.append("title").text(edgeTitle);

  var nodeScale = (
    p.nodeRadiusLogScale
      ? d3.scaleLog().base(2.71828)
      : d3.scaleLinear()
    )
    .domain(d3.extent(graph.nodes, n => n.score)) // [ min, max ]
    .range([p.nodeRadiusFrom, p.nodeRadiusTo]);
    
  var node = svg.append("g")
      .attr("class", "nodes")
    .selectAll("circle")
    .data(graph.nodes)
    .enter().append("circle")
      .attr("r", n => nodeScale(n.score))
      .attr("fill", nodeColour)
      .call(d3.drag()
          .on("start", dragstarted)
          .on("drag", dragged)
          .on("end", dragended));

  node.append("title").text(nodeTitle);

  node.on('click', d => setNodeId(d.nodeId));

  var distScale = (
    p.distanceLogScale
      ? d3.scaleLog().base(2.71828) // log scale compresses the range, smaller base does more gentle compression
      : d3.scaleLinear()
    )
    .domain(d3.extent(graph.edges, edgeLength)) // [ min, max ]
    .range([p.distanceFrom, p.distanceTo]);
    
  simulation = d3.forceSimulation()
    .force("link", d3.forceLink()
      .id(d => d.nodeId)
    )
    .force("charge", d3.forceManyBody())
    .force("center", d3.forceCenter(width / 2, height / 2))
    .nodes(graph.nodes)
    .on("tick", ticked);

  simulation.force("link")
      .links(graph.edges)
      .distance(d => distScale(edgeLength(d)));
      
  simulation.force("charge").strength(p.chargeStrength);

  function ticked() {
    link
        .attr("x1", function(d) { return d.source.x; })
        .attr("y1", function(d) { return d.source.y; })
        .attr("x2", function(d) { return d.target.x; })
        .attr("y2", function(d) { return d.target.y; });

    node
        .attr("cx", function(d) { return d.x; })
        .attr("cy", function(d) { return d.y; });
  }
};

function dragstarted(d) {
  if (!d3.event.active) simulation.alphaTarget(0.3).restart();
  d.fx = d.x;
  d.fy = d.y;
}

function dragged(d) {
  d.fx = d3.event.x;
  d.fy = d3.event.y;
}

function dragended(d) {
  if (!d3.event.active) simulation.alphaTarget(0);
  d.fx = null;
  d.fy = null;
}

function getStr(selector, atr = 'value') {
  return d3.select(selector).property(atr);
};

function getInt(selector, atr = 'value') {
  var s = getStr(selector, atr);
  return s == "" ? 0 : parseInt(s);
};

function getFloat(selector, atr = 'value') {
  var s = getStr(selector, atr);
  return s == "" ? 0.0 : parseFloat(s);
};

function isChecked(selector) {
  return d3.select(selector + ':checked').node() != null;
};

function getCollections() {
  var ids = [ 'colCbc', 'colChanley', 'colMascot', 'colRtp', 'colRulings' ];
  var col = ids.map(id => isChecked('#' + id) ? d3.select('label[for="' + id + '"]').text() : null).filter(x => x != null);
  // console.log('getCollections: col =', col);
  return col;
};

function getFormVals() {
  return { 
    includePerson2: isChecked('#includePerson2'), chargeStrength: getInt('#chargeStrength'), 
    distanceLogScale: isChecked('#distanceLogScale'), distanceFrom: getInt('#distanceFrom'), distanceTo: getInt('#distanceTo'),
    nodeRadiusLogScale: isChecked('#nodeRadiusLogScale'), nodeRadiusFrom: getInt('#nodeRadiusFrom'), nodeRadiusTo: getInt('#nodeRadiusTo'),
    edgeWidthLogScale: isChecked('#edgeWidthLogScale'), edgeWidthFrom: getInt('#edgeWidthFrom'), edgeWidthTo: getInt('#edgeWidthTo'),
    maxEdges: getInt('#maxEdges'), minScore: getFloat('#minScore'), port: getInt('#port'),
    nodeId: getInt('#nodeId'), extRefId: getInt('#extRefId'), maxHops: getInt('#maxHops'),
    collections: getCollections()
  };
};

function setNodeId(nodeId) {
  d3.select('#nodeId').property('value', nodeId);
  d3.select('#extRefId').property('value', '');
};

function getProtoHost() {
  var l = window.location;
  return l.protocol + '//' + l.hostname;
};

var protoHost = getProtoHost();
console.log('protoHost =', protoHost);

function topConnected() {
  var p = getFormVals();
  var data = { includePerson2: p.includePerson2, maxEdges: p.maxEdges, minScore: p.minScore };
  if (p.collections.length) data.collections = p.collections;
  console.log('topConnected: request data =', data);
  d3.json(protoHost + ':' + p.port + '/topConnectedGraph')
    .mimeType("application/json")
    .header("Content-Type", "application/json")
    .response(xhr => JSON.parse(xhr.responseText))
    .post(JSON.stringify(data), function(error, graph) {
      if (error) throw error;
      doGraph(graph, p);
    });
};

function localNetwork() {
  var p = getFormVals();
  var data = { includePerson2: p.includePerson2, minScore: p.minScore, maxHops: p.maxHops, maxEdges: p.maxEdges };
  if (p.nodeId) data.nodeId = p.nodeId;
  else data.extRefId = p.extRefId;
  if (p.collections.length) data.collections = p.collections;
  console.log('localNetwork: request data =', data);
  d3.request(protoHost + ':' + p.port + '/graph')
    .mimeType("application/json")
    .header("Content-Type", "application/json")
    .response(xhr => JSON.parse(xhr.responseText))
    .post(JSON.stringify(data), function(error, graph) {
      if (error) throw error;
      doGraph(graph, p);
    });
};

topConnected();

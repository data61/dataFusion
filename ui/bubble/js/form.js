const apiRoutes = ['/topConnectedGraph', '/graph']

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
    graphType: getInt('#graphType'),
    chartType: document.forms.optsForm.visType.value,
    includePerson2: isChecked('#includePerson2'),
    chargeStrength: getInt('#chargeStrength'),
    distanceLogScale: isChecked('#distanceLogScale'),
    distanceFrom: getInt('#distanceFrom'),
    distanceTo: getInt('#distanceTo'),
    nodeRadiusLogScale: isChecked('#nodeRadiusLogScale'),
    nodeRadiusFrom: getInt('#nodeRadiusFrom'),
    nodeRadiusTo: getInt('#nodeRadiusTo'),
    edgeWidthLogScale: isChecked('#edgeWidthLogScale'),
    edgeWidthFrom: getInt('#edgeWidthFrom'),
    edgeWidthTo: getInt('#edgeWidthTo'),
    maxEdges: getInt('#maxEdges'),
    minScore: getFloat('#minScore'),
    port: getInt('#port'),
    nodeId: getInt('#nodeId'),
    extRefId: getInt('#extRefId'),
    maxHops: getInt('#maxHops'),
    collections: getCollections()
  };
};

function setNodeId(nodeId) {
  let fetchForm = document.getElementById("fetch-graph");
  let optsForm = document.getElementById("vis-opts");
  d3.select('#nodeId').property('value', nodeId);
  d3.select('#extRefId').property('value', '');
  document.forms.fetchForm.graphType.value = 1
  if (fetchForm.className.indexOf("closed") > -1) {
    toggleClosedClass(fetchForm.querySelector("h2"), false)
    toggleClosedClass(optsForm.querySelector("h2"), true)
  }
};

function getProtoHost() {
  var l = window.location;
  return l.protocol + '//' + l.hostname;
};

var protoHost = getProtoHost();
console.log('protoHost =', protoHost);

let currentGraph;

function getGraph() {
  var p = getFormVals();
  var data = { includePerson2: p.includePerson2, maxEdges: p.maxEdges, minScore: p.minScore, maxHops: p.maxHops };

  if (p.graphType === 1) {
    if (p.nodeId) data.nodeId = p.nodeId;
    else data.extRefId = p.extRefId;
  }

  if (p.collections.length) data.collections = p.collections;
  console.log(`${apiRoutes[p.graphType]}: request data`, data);
  d3.json(protoHost + ':' + p.port + apiRoutes[p.graphType])
    .mimeType("application/json")
    .header("Content-Type", "application/json")
    .response(xhr => JSON.parse(xhr.responseText))
    .post(JSON.stringify(data), function(error, graph) {
      if (error) throw error;
      currentGraph = graph;
      drawGraph();
    });
};

function drawGraph () {
  var p = getFormVals();

  switch (p.chartType) {
    case "network":
      document.querySelector("#network-chart").style.display = "block"
      document.querySelector("#network-chart").innerHTML = ""
      document.querySelector("#bubble-chart").style.display = "none"
      networkGraph(JSON.parse(JSON.stringify(currentGraph)), p)
      break;
    case "bubble":
      document.querySelector("#bubble-chart").style.display = "block"
      document.querySelector("#bubble-chart").innerHTML = ""
      document.querySelector("#network-chart").style.display = "none"
      bubbleGraph(JSON.parse(JSON.stringify(currentGraph)), p)
      break;
    default:
      return;
  }
}

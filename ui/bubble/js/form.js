const apiRoutes = [
  {
    method: 'POST',
    port: 8089,
    route: '/topConnectedGraph',
    name: 'Top Connected' 
  },
  {
    method: 'POST',
    port: 8089,
    route: '/graph',
    name: 'Local Network'
  },
  {
    method: 'GET',
    port: 80,
    route: '/ui/research/network.json',
    name: 'Research' 
  },
  {
    method: 'GET',
    port: 80,
    route: '/ui/research/network-nb.json',
    name: 'Research B' 
  },
  {
    method: 'GET',
    port: 80,
    route: '/ui/stephen/demo1/network.json',
    name: 'Demo 1' 
  },
  {
    method: 'GET',
    port: 80,
    route: '/ui/stephen/demo2/network.json',
    name: 'Demo 2' 
  },
  {
    method: 'GET',
    port: 80,
    route: '/ui/stephen/demo3/network.json',
    name: 'Demo 3' 
  }
]

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
    edgeLimit: getInt("#edgeLimit"),
    maxEdges: getInt('#maxEdges'),
    minScore: getFloat('#minScore'),
    //port: getInt('#port'),
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
const visSelect = document.getElementById("visType")
const edgeLmtInpt = document.getElementById("edgeLimit")
const distFields = document.getElementById("dist")
const radFields = document.getElementById("rad")
const widFields = document.getElementById("wid")
const chargeInpt = document.getElementById("chargeStrength")

function setFieldVals (type) {
  edgeLmtInpt.value = type === "network" ? 100 : 10
  chargeInpt.disabled = type === "bubble"
  distFields.disabled = type === "bubble"
  radFields.disabled = type === "bubble"
  widFields.disabled = type === "bubble"
}

visSelect.onchange = function (e) {
  setFieldVals(this.value)
}

setFieldVals(document.forms.optsForm.visType.value)

function getGraph() {
  var p = getFormVals();
  var data = { includePerson2: p.includePerson2, maxEdges: p.maxEdges, minScore: p.minScore, maxHops: p.maxHops };

  if (p.graphType === 1) {
    if (p.nodeId) data.nodeId = p.nodeId;
    else data.extRefId = p.extRefId;
  }

  p.port = apiRoutes[p.graphType].port;

  if (p.collections.length) data.collections = p.collections;
  console.log(`${apiRoutes[p.graphType]}: request data`, data);

  if (apiRoutes[p.graphType].method === "POST") {
    d3.json(protoHost + ':' + p.port + apiRoutes[p.graphType].route)
      .mimeType("application/json")
      .header("Content-Type", "application/json")
      .response(xhr => JSON.parse(xhr.responseText))
      .post(JSON.stringify(data), function(error, graph) {
        if (error) throw error;
        currentGraph = graph;
        drawGraph();
      });
  } else if (apiRoutes[p.graphType].method === "GET") {
    d3.json(protoHost + ':' + p.port + apiRoutes[p.graphType].route)
      .mimeType("application/json")
      .header("Content-Type", "application/json")
      .response(xhr => JSON.parse(xhr.responseText))
      .get(function(error, graph) {
        if (error) throw error;

        currentGraph = graph;
        drawGraph();
      });
  }
};

function drawGraph () {
  var p = getFormVals();
  var data = JSON.parse(JSON.stringify(currentGraph));

  // apply edges limit
  if (data.edges.length > p.edgeLimit) {
    data.edges.length = p.edgeLimit;
  };

  var ids = new Set();
  data.edges.forEach(e => {
    ids.add(e.source);
    ids.add(e.target);
  });

  data.nodes = data.nodes.filter(n => ids.has(n.nodeId));

  switch (p.chartType) {
    case "network":
      document.querySelector("#network-chart").style.display = "block"
      document.querySelector("#network-chart").innerHTML = ""
      document.querySelector("#bubble-chart").style.display = "none"
      networkGraph(data, p)
      break;
    case "bubble":
      document.querySelector("#bubble-chart").style.display = "block"
      document.querySelector("#bubble-chart").innerHTML = ""
      document.querySelector("#network-chart").style.display = "none"
      bubbleGraph(data, p)
      break;
    default:
      return;
  }
}

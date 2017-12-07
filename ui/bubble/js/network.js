function networkGraph (graph, p) {
  var svg = d3.select("#network-chart");

  var typs = [ 'PERSON', 'ORGANIZATION', 'PERSON2', 'FROM', 'TO', 'CC', 'BCC' ];
  function nodeColour(n) {
    return colourMap[n.typ];
  }

  var simulation;

  var nodeTitle = d => d.extRef.name + ' (typ: ' + d.typ + ', score: ' + d.score + ', ids: ' + d.extRef.ids + ')';

  function edgeTitle(d) {
    console.log(d)
    var t = "Weight, count in selected collections: " + [ d.totalWeight, d.totalCount ];
    var w = d.weights;
    for (c in w) t += "\n  Weight, count in " + c + ": " + w[c];
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
        .attr("stroke-width", e => edgeScale(e.totalCount))
        .style("cursor", "pointer")


    //link.append("title").text(edgeTitle);
    link.on("mouseover", d => {
      displayDataSidebar({
        title: `${d.source.extRef.name} <-> ${d.target.extRef.name}`,
        desc: edgeTitle(d)
      })    
    })

    link.on("mouseout", hideDataSidebar)

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
        .style("cursor", "pointer")
        .call(d3.drag()
            .on("start", dragstarted)
            .on("drag", dragged)
            .on("end", dragended));

    //node.append("title").text(nodeTitle);
    node.on("mouseover", d => {
      displayDataSidebar({
        title: `${d.extRef.name} (${d.typ})`,
        desc: `Score: ${d.score}, IDs: ${d.extRef.ids}`
      })    
    })

    node.on("mouseout", hideDataSidebar)

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

  doGraph(graph, p)
}

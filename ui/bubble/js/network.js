function networkGraph (graph, p) {
  var svg = d3.select("#network-chart");

  var typs = [ 'PERSON', 'ORGANIZATION', 'PERSON2', 'FROM', 'TO', 'CC', 'BCC' ];
  function nodeColour(n) {
    return colourMap[n.typ];
  }

  var simulation;

  var nodeTitle = d => {
    if (p.graphType > 1) {
      return d.name + ' (' + d.typ + ')';  
    } else {
      return (d.name || d.extRef.name) + ' (typ: ' + d.typ + ', score: ' + d.score + ', ids: ' + d.extRef.ids + ')';
    }
  }

  function edgeHeading(d) {
    if (p.graphType > 1) {
      return `${d.source.name || d.source.extRef.name} (${d.typ}) ${d.target.name || d.target.extRef.name}`
    } else {
      return `${d.source.name || d.source.extRef.name} <-> ${d.target.name || d.target.extRef.name}`
    }
  }

  function edgeTitle(d) {
    if (p.graphType > 1) {
      let retStr = `<p><strong>Context in document:</strong> ${d.context}</p>`;
      retStr += `<p><strong>Relationship type:</strong> ${d.typ}</p>`;
      retStr += `<p><strong>Source filename:</strong> ${d.file.split('/').pop()}`;
      return retStr;
    } else {
      let t = `<p><strong>Weight, count in selected collections:</strong> ${d.totalWeight}, ${d.totalCount}</p>`;
      let w = d.weights;
      for (c in w) t += `<p><strong>Weight, count in ${c}:</strong> ${w[c][0]}, ${w[c][1]}</p>`;
      return t;
    }

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
        .attr("stroke-width", p.graphType > 1 ? 2 : e => edgeScale(e.totalCount))
        .style("cursor", "pointer")


    //link.append("title").text(edgeTitle);
    link.on("mouseover", function (d) {
      link.style("stroke-opacity", 0.1)
      d3.select(this).style("stroke-opacity", 0.6)

      node.style("opacity", 0.1)
      d3.select(`#node-${d.source.nodeId}`).style("opacity", 1)
      d3.select(`#node-${d.target.nodeId}`).style("opacity", 1)
  
      displayDataSidebar({
        title: edgeHeading(d),
        desc: edgeTitle(d)
      })    
    })

    link.on("mouseout", d => {
      link.style("stroke-opacity", 0.6)
      node.style("opacity", 1)
      hideDataSidebar()
    })

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
        .attr("r", n => p.graphType > 1 ? 4 : nodeScale(n.score))
        .attr("fill", nodeColour)
        .style("cursor", "pointer")
        .attr("id", d => `node-${d.nodeId}`)
        .call(d3.drag()
            .on("start", dragstarted)
            .on("drag", dragged)
            .on("end", dragended));

    //node.append("title").text(nodeTitle);
    node.on("mouseover", d => {
      let idText = ""
      if (d.extRef && d.extRef.ids && Array.isArray(d.extRef.ids)) idText = `<strong>IDs:</strong> ${d.extRef.ids.reduce((acc, curr) => acc + ", " + curr)}`

      displayDataSidebar({
        title: `${d.name || (d.extRef && d.extRef.name)} (${d.typ})`,
        desc: `<p><strong>Score:</strong> ${d.score}</p> <p>${idText}</p>`
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
        .distance(d => p.graphType > 1 ? 20 : distScale(edgeLength(d)));

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

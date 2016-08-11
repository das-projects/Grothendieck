
function createBarChart(parentDivId, data, comparisonAttr, getDisplayText, config, nodeClickHandler) {
	var svg = d3.select(parentDivId)
				.append("svg")
			 	.attr("class", "barChart");

	var parentDiv = $(parentDivId);

	var width = parentDiv.width() - 5,
	    barHeight = 20;

	var x = d3.scale.linear()
	    .domain([-2, d3.max(data, function(d) {return parseFloat(d[comparisonAttr])})])
	    .range([0, width - 50]);

	var chart = d3.select(".barChart")
	    .attr("width", width)
	    .attr("height", barHeight * data.length);

	var bar = chart.selectAll("g")
	    .data(data)
	    .enter().append("g")
	    .attr("transform", function(d, i) { return "translate(0," + i * barHeight + ")"; });

    var handler = nodeClickHandler || genericNodeClickHandler;
	bar.append("rect")
	    .attr("class", "bar")
	    .attr("width", function(d) {return x(d[comparisonAttr])})
	    .attr("height", barHeight - 1)
	    .on("click", handler);
	
	bar.append("text")
		.attr("class", "barLabel")
	    .attr("x", function(d) { return x(d[comparisonAttr]) - 3; })
	    .attr("y", barHeight / 2)
	    .attr("dy", ".35em")
	    .text(getDisplayText)
	    .on("click", handler);

}

function genericNodeClickHandler(d) {
	//config.populateKernelInfoTable(d.node);
	//config.highlightLineInEditorByKernelId(d.node.id);

	config.populateKernelInfoTable( d );
	config.highlightLineInEditorForDNode( d );
}
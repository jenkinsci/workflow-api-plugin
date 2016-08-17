/**
 * Provides a library of methods to work with and analyze the graph of {@link org.jenkinsci.plugins.workflow.graph.FlowNode}s produced from a pipeline execution.
 *
 * <p></p>The core APIs are described in the javadocs for {@link org.jenkinsci.plugins.workflow.graphanalysis.AbstractFlowScanner}
 * But in general it provides for iteration through the Directed Acyclic Graph (DAG) of a flow, filtering, search for matches, and
 * visiting all nodes via internal iteration.
 *
 * <p></p> Static methods and a few implementations are also provided in {@link org.jenkinsci.plugins.workflow.graphanalysis.FlowScanningUtils}.
 */

package org.jenkinsci.plugins.workflow.graphanalysis;
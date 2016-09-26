/**
 * Provides a library of methods to work with and analyze the graph of {@link org.jenkinsci.plugins.workflow.graph.FlowNode}s produced from a pipeline execution.
 *
 * <p>The core APIs are described in the javadocs for {@link org.jenkinsci.plugins.workflow.graphanalysis.AbstractFlowScanner}
 * But in general it provides for iteration through the Directed Acyclic Graph (DAG) of a flow, filtering, search for matches, and
 * visiting all nodes via internal iteration.
 *
 * <p> Static methods and a few implementations are also provided in {@link org.jenkinsci.plugins.workflow.graphanalysis.FlowScanningUtils}.
 * <p><em>How To Pick A FlowScanner For Iteration</em></p>
 * <ol>
 *     <li><em>Want fast iteration, don't care about visiting every node or parallels?</em> {@link org.jenkinsci.plugins.workflow.graphanalysis.LinearScanner}</li>
 *     <li><em>Visit every node as fast as possible?</em> {@link org.jenkinsci.plugins.workflow.graphanalysis.DepthFirstScanner}</li>
 *     <li><em>Visit every block in a predictable order, from end to start?</em> {@link org.jenkinsci.plugins.workflow.graphanalysis.ForkScanner}</li>
 *     <li><em>Fastest way to find preceding sibling or enclosing nodes?</em> {@link org.jenkinsci.plugins.workflow.graphanalysis.LinearBlockHoppingScanner}</li>
 * </ol>
 */

package org.jenkinsci.plugins.workflow.graphanalysis;
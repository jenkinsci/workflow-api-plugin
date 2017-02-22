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
 *
 * <p> Methods to iterate through flow graph and break it into chunks (regions) of interest, with nesting possible and reporting of branches:
 * <p><em>Basic APIs</em>
 * <ol>
 *     <li>{@link org.jenkinsci.plugins.workflow.graphanalysis.ChunkFinder} - API to define conditions for starting/ending a chunk</li>
 *     <li>{@link org.jenkinsci.plugins.workflow.graphanalysis.SimpleChunkVisitor} - Visitor API that accepts callbacks for chunk boundaries/contenst + parallel branching</li>
 *     <li>{@link org.jenkinsci.plugins.workflow.graphanalysis.FlowChunk} - A region of interest, defined by its first and last nodes</li>
 *     <li>{@link org.jenkinsci.plugins.workflow.graphanalysis.FlowChunkWithContext} - A FlowChunk that knows about the nodes before/after it,
 *          to give context for determining success/failure and the time taken to execute</li>
 * </ol>
 *
 * <p><em>Data structures and implementations:</em>
 * <ul>
 *     <li>{@link org.jenkinsci.plugins.workflow.graphanalysis.MemoryFlowChunk} - A FlowChunkWithContext that just directly stores FlowNodes</li>
 *     <li>{@link org.jenkinsci.plugins.workflow.graphanalysis.ParallelFlowChunk}</li>
 *     <li>{@link org.jenkinsci.plugins.workflow.graphanalysis.ParallelMemoryFlowChunk}</li>
 *     <li>{@link org.jenkinsci.plugins.workflow.graphanalysis.StandardChunkVisitor} - a basic concrete implementation of SimpleChunkVisitor that
 *                tracks one chunk at a time (no nesting) and runs a callback when the chunk is done</li>
 *     <li>{@link org.jenkinsci.plugins.workflow.graphanalysis.LabelledChunkFinder} - ChunkFinder that matches against nodes with a label</li>
 *     <li>{@link org.jenkinsci.plugins.workflow.graphanalysis.BlockChunkFinder} - ChunkFinder that creates chunks from blocks</li>
 * </ul>
 */

package org.jenkinsci.plugins.workflow.graphanalysis;
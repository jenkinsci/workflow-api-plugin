/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jenkinsci.plugins.workflow.graphanalysis;

import org.jenkinsci.plugins.workflow.graph.BlockEndNode;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

/**
 * This visitor's callbacks are invoked as we walk through a pipeline flow graph, and it splits it into chunks.
 * <p> A {@link ForkScanner#visitSimpleChunks(SimpleChunkVisitor, ChunkFinder)} creates these FlowChunks using a {@link ChunkFinder} to define the chunk boundaries.
 *
 * <p>We walk through the {@link FlowNode}s in reverse order from end to start, so <em>end callbacks are invoked before
 *  their corresponding start callbacks.</em>
 *
 * <p><strong>Callback types</strong>
 * <p> There are two kinds of callbacks - chunk callbacks, and parallel structure callbacks
 * <p><strong>Chunk Callbacks:</strong>
 * <ul>
 *     <li>{@link #chunkStart(FlowNode, FlowNode, ForkScanner)} - detected the start of a chunk beginning with a node</li>
 *     <li>{@link #chunkEnd(FlowNode, FlowNode, ForkScanner)} - detected the end of a chunk, terminating with a node </li>
 *     <li>{@link #atomNode(FlowNode, FlowNode, FlowNode, ForkScanner)} - most nodes, which aren't boundaries of chunks</li>
 * </ul>
 *
 * <p><p><strong>Chunk callback rules:</strong>
 * <ol>
 *     <li>For a single node, it may have EITHER OR BOTH chunkStart and chunkEnd events</li>
 *     <li>Every node that doesn't get a startChunk/endChunk callback gets an atomNode callback.</li>
 *     <li>For {@link ChunkFinder} implementations that match the {@link BlockStartNode} and {@link BlockEndNode} should never have both for a single node.</li>
 *     <li>You cannot have multiple of any of the same specific type of callbacks for the same flownode</li>
 *     <li>You cannot have a atomNode callback AND a start/end for the same flownode (application of the above).</li>
 * </ol>
 *
 * <p>Parallel Structure Callbacks: Zero, One, or (in niche cases) several different ones may be invoked for any given FlowNode</p>
 * <p>These are used to provide awareness of parallel/branching structures if they need special handling.
 * <ul>
 *     <li>{@link #parallelStart(FlowNode, FlowNode, ForkScanner)}</li>
 *     <li>{@link #parallelEnd(FlowNode, FlowNode, ForkScanner)}</li>
 *     <li>{@link #parallelBranchStart(FlowNode, FlowNode, ForkScanner)}</li>
 *     <li>{@link #parallelBranchEnd(FlowNode, FlowNode, ForkScanner)}</li>
 * </ul>
 * <em>The cases where a node triggers multiple callbacks are where it is one of several forked branches of an incomplete parallel
 *   block.  In this case it can be a parallelBranchEnd, also potentially a parallelEnd, plus whatever role that node might normally
 *   have (such as the start of another parallel).</em>
 *
 * <p>Implementations get to decide how to use and handle chunks, and should be stateful.
 * <p><h3>At a minimum they should handle:</h3>
 * <ul>
 *     <li>Cases where there is no enclosing chunk (no start/end found, or outside a chunk)</li>
 *     <li>Cases where there is no chunk end to match the start, because we haven't finished running a block</li>
 *     <li>Nesting of chunks</li>
 * </ul>
 *
 * @author Sam Van Oort
 */
public interface SimpleChunkVisitor {

    /**
     * Called when hitting the start of a chunk.
     * @param startNode First node in chunk (marker), included in node
     * @param beforeBlock First node before chunk (null if none exist)
     * @param scanner Forkscanner used (for state tracking)
     */
    void chunkStart(@Nonnull FlowNode startNode, @CheckForNull FlowNode beforeBlock, @Nonnull ForkScanner scanner);

    /**
     * Called when hitting the end of a chunk.
     * @param endNode Last node in chunk
     * @param afterChunk Node after chunk (null if we are on the last node)
     * @param scanner Forkscanner used (for state tracking)
     */
    void chunkEnd(@Nonnull FlowNode endNode, @CheckForNull FlowNode afterChunk, @Nonnull ForkScanner scanner);

    /**
     * Notifies that we've hit the start of a parallel block (the point where it branches out).
     * @param parallelStartNode The {@link org.jenkinsci.plugins.workflow.graph.BlockStartNode} beginning it, next will be branches
     * @param branchNode {@link org.jenkinsci.plugins.workflow.graph.BlockStartNode} for one of the branches (it will be labelled)
     * @param scanner ForkScanner used
     */
    void parallelStart(@Nonnull FlowNode parallelStartNode, @Nonnull FlowNode branchNode, @Nonnull ForkScanner scanner);

    /**
     * Notifies that we've seen the end of a parallel block
     * @param parallelStartNode First node of parallel ({@link BlockStartNode} before the branches)
     * @param parallelEndNode Last node of parallel ({@link BlockEndNode})
     * @param scanner
     */
    void parallelEnd(@Nonnull FlowNode parallelStartNode, @Nonnull FlowNode parallelEndNode, @Nonnull ForkScanner scanner);

    /**
     * Hit the start of a parallel branch
     * @param parallelStartNode First node of parallel (BlockStartNode before the branches)
     * @param branchStartNode BlockStartNode beginning the branch (this will have the ThreadNameAction giving its name)
     * @param scanner
     */
    void parallelBranchStart(@Nonnull FlowNode parallelStartNode, @Nonnull FlowNode branchStartNode, @Nonnull ForkScanner scanner);

    /**
     * Hit the end start of a parallel branch
     * <p> May not be invoked if we're inside an in-progress parallel
     * @param parallelStartNode First node of parallel (BlockStartNode before the branches)
     * @param branchEndNode Final node of the branch (may be BlockEndNode if done, otherwise just the last one executed)
     * @param scanner
     */
    void parallelBranchEnd(@Nonnull FlowNode parallelStartNode, @Nonnull FlowNode branchEndNode, @Nonnull ForkScanner scanner);

    /**
     * Called for a flownode neither start nor end.
     * Ways you may want to use this: accumulate pause time, collect errors, etc.
     * Note: invocations don't guarantee whether or not you're within a marked chunk.
     * @param before Node before the current
     * @param atomNode The node itself
     * @param after Node after the current
     * @param scan Reference to our forkscanner, if we want to poke at the state within
     */
    void atomNode(@CheckForNull FlowNode before, @Nonnull FlowNode atomNode, @CheckForNull FlowNode after, @Nonnull ForkScanner scan);
}

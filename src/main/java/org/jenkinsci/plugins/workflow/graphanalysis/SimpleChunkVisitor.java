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

import org.jenkinsci.plugins.workflow.graph.FlowNode;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

/**
 * This visitor's callbacks are invoked as we walk through a pipeline flow graph, and it splits it into chunks.
 * <p/> A {@link ForkScanner#visitSimpleChunks(SimpleChunkVisitor, ChunkFinder)} creates these FlowChunks using a {@link ChunkFinder} to define the chunk boundaries.
 *
 * <p/> Implementations get to decide how to use & handle chunks.
 * <p/> <h3>At a minimum they should handle:</h3>
 * <ul>
 *     <li>Unbalanced numbers of chunk start/end calls (for incomplete flows)</li>
 *     <li>A chunk end with no beginning (runs to start of flow, or never began)</li>
 *     <li>A chunk start with no end (ex: a block that hasn't completed running)</li>
 *     <li>Other starts/ends before we hit the closing one (nesting)</li>
 *     <li>Atom nodes not within the current Chunk (visitor is responsible for handling state)</li>
 * </ul>
 *
 * <em>Important implementation note: multiple callbacks can be invoked for a single node depending on its type.</em
 * <p/>For example, we may capture parallels as chunks.
 *
 * <p/><h3>Callbacks Reporting on chunk/parallel information:</h3>
 * <ul>
 *     <li>{@link #chunkStart(FlowNode, FlowNode, ForkScanner)} is called on the current node when we hit start of a boundary (inclusive) </li>
 *     <li>{@link #chunkEnd(FlowNode, FlowNode, ForkScanner)} is called when we hit end of a boundary (inclusive)</li>
 *     <li>{@link #atomNode(FlowNode, FlowNode, FlowNode, ForkScanner)} called when a node is neither start nor end.</li>
 *     <li>All the parallel methods are used to report on parallel status - helpful when we need to deal with parallels internal to chunks.</li>
 * </ul>
 *
 * @author Sam Van Oort
 */
public interface SimpleChunkVisitor {

    /**
     * Called when hitting the start of a chunk
     * @param startNode First node in chunk (marker), included in node
     * @param beforeBlock First node before chunk
     * @param scanner Forkscanner used (for state tracking)
     */
    void chunkStart(@Nonnull FlowNode startNode, @CheckForNull FlowNode beforeBlock, @Nonnull ForkScanner scanner);

    /** Called when hitting the end of a block */
    void chunkEnd(@Nonnull FlowNode endNode, @CheckForNull FlowNode afterChunk, @Nonnull ForkScanner scanner);

    /**
     * Notifies that we've hit the start of a parallel block (the point where it branches out)
     * @param parallelStartNode The {@link org.jenkinsci.plugins.workflow.graph.BlockStartNode} beginning it, next will be branches
     * @param branchNode {@link org.jenkinsci.plugins.workflow.graph.BlockStartNode} for one of the branches (it will be labelled)
     * @param scanner ForkScanner used
     */
    void parallelStart(@Nonnull FlowNode parallelStartNode, @Nonnull FlowNode branchNode, @Nonnull ForkScanner scanner);

    /**
     * Notifies that we've seen the end of a parallel block
     * @param parallelStartNode First node of parallel (BlockStartNode before the branches)
     * @param parallelEndNode Last node of parallel (BlockEndNode)
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
     * <p/> May not be invoked if we're inside an in-progress parallel
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

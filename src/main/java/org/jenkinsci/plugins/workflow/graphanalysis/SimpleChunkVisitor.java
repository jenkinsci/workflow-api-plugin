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
 *     <li>Unbalanced numbers of chunk start/end calls</li>
 *     <li>A chunk end with no beginning (runs to start of flow, or never began)</li>
 *     <li>A chunk start with no end (ex: a block that hasn't completed running)</li>
 *     <li>Other starts/ends before we hit the closing one</li>
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
 * @author <samvanoort@gmail.com>Sam Van Oort</samvanoort@gmail.com>
 */
interface SimpleChunkVisitor {

    /**
     * Called when hitting the start of a chunk
     * @param startNode First node in chunk (marker), included in node
     * @param beforeBlock First node before chunk
     * @param scanner Forkscanner used (for state tracking)
     */
    void chunkStart(@Nonnull FlowNode startNode, @CheckForNull FlowNode beforeBlock, @Nonnull ForkScanner scanner);

    /** Called when hitting the end of a block (determined by the chunkEndPredicate) */
    void chunkEnd(@Nonnull FlowNode endNode, @CheckForNull FlowNode afterBlock, @Nonnull ForkScanner scanner);

    /** Notifies that we've seen a new parallel block */
    void parallelStart(@Nonnull FlowNode parallelStartNode, @Nonnull FlowNode branchNode, @Nonnull ForkScanner scanner);

    /** Notifies that we've seen the end of a parallel block*/
    void parallelEnd(@Nonnull FlowNode parallelStartNode, @Nonnull FlowNode parallelEndNode, @Nonnull ForkScanner scanner);

    void parallelBranchStart(@Nonnull FlowNode parallelStartNode, @Nonnull FlowNode branchStartNode, @Nonnull ForkScanner scanner);

    void parallelBranchEnd(@Nonnull FlowNode parallelStartNode, @Nonnull FlowNode branchEndNode, @Nonnull ForkScanner scanner);

    /**
     * Called for a flownode within the chunk that is neither start nor end.
     * Ways you may want to use this: accumulate pause time, collect errors, etc.
     * @param before Node before the current
     * @param atomNode The node itself
     * @param after Node after the current
     * @param scan Reference to our forkscanner, if we want to poke at the state within
     */
    void atomNode(@CheckForNull FlowNode before, @Nonnull FlowNode atomNode, @CheckForNull FlowNode after, @Nonnull ForkScanner scan);
}

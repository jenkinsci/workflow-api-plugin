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

import com.google.common.base.Predicate;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

/**
 * This visitor's callbacks are invoked as we walk through a pipeline flow graph, and it splits it into chunks.
 * The {@link FlowChunker} uses the split methods & holds state needed convert the {@link ForkScanner}'s API to invoke these right.
 *
 * <p/><h3>Determining how we split into chunk.</h3>
 * <ul>
 *     <li>{@link #getChunkStartPredicate()} Provides the condition marking the beginning of a chunk we care about</li>
 *     <li>{@link #getChunkEndPredicate()} Provides the condition to mark a node as ending a chunk we care about</li>
 * </ul>
 *
 * Think of it as a finite state machine: we're either in a chunk or not.
 *
 * <p/><h3>Callbacks Reporting on chunk/parallel information:</h3>
 * <ul>
 *     <li>{@link #chunkStart(FlowNode, FlowNode, ForkScanner)} is called when we hit start of a boundary</li>
 *     <li>{@link #chunkEnd(FlowNode, FlowNode, ForkScanner)} is called when we hit end of a boundary</li>
 *     <li>{@link #atomNode(FlowNode, FlowNode, FlowNode, ForkScanner)} is called, used to gather information within a chunk</li>
 *     <li>All the parallel methods are used to report on parallel status - helpful when we need to deal with parallels internal to chunks.</li>
 * </ul>
 *
 * <p/> Start/Stop predicates may both trigger on the same node (in which case end is invoked first).
 * For example with marker nodes like the legacy stage.
 *
 * @author <samvanoort@gmail.com>Sam Van Oort</samvanoort@gmail.com>
 */
public interface SimpleChunkVisitor {

    @Nonnull
    public Predicate<FlowNode> getChunkStartPredicate();

    @Nonnull
    public Predicate<FlowNode> getChunkEndPredicate();

    /** If true, we create an implicit chunk when starting out and don't wait for end condition */
    public boolean startInsideChunk();

    /** Called when hitting the start of a block */
    public void chunkStart(@Nonnull FlowNode startNode, @CheckForNull FlowNode beforeBlock, @Nonnull ForkScanner scanner);

    /** Called when hitting the end of a block */
    public void chunkEnd(@Nonnull FlowNode endNode, @CheckForNull FlowNode afterBlock, @Nonnull ForkScanner scanner);

    /** Notifies that we've seen a new parallel block */
    public void parallelStart(@Nonnull FlowNode parallelStartNode, @Nonnull FlowNode branchNode, @Nonnull ForkScanner scanner);

    /** Notifies that we've seen the end of a parallel block*/
    public void parallelEnd(@Nonnull FlowNode parallelStartNode, @Nonnull FlowNode parallelEndNode, @Nonnull ForkScanner scanner);

    public void parallelBranchStart(@Nonnull String branchName, @Nonnull FlowNode parallelStartNode, @Nonnull FlowNode branchStartNode, @Nonnull ForkScanner scanner);

    public void parallelBranchEnd(@Nonnull String branchName, @Nonnull FlowNode parallelStartNode, @Nonnull FlowNode branchEndNode, @Nonnull ForkScanner scanner);

    /**
     * Called for a flownode within the chunk that is neither start nor end.
     * Ways you may want to use this: accumulate pause time, collect errors, etc.
     * @param before Node before the current
     * @param atomNode The node itself
     * @param after Node after the current
     * @param scan Reference to our forkscanner, if we want to poke at the state within
     */
    public void atomNode(@CheckForNull FlowNode before, @Nonnull FlowNode atomNode, @CheckForNull FlowNode after, @Nonnull ForkScanner scan);
}

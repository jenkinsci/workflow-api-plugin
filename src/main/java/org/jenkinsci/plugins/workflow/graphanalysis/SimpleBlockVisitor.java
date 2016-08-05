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
 * Block handling mechanism
 * @author <samvanoort@gmail.com>Sam Van Oort</samvanoort@gmail.com>
 */
public interface SimpleBlockVisitor {


    @Nonnull
    public Predicate<FlowNode> getChunkStartPredicate();

    @Nonnull
    public Predicate<FlowNode> getChunkEndPredicate();

    /** Called when hitting the start of a block */
    public void chunkStart(@Nonnull FlowNode startNode, @CheckForNull FlowNode beforeBlock, @Nonnull ForkScanner scanner);

    /** Called when hitting the end of a block */
    public void chunkEnd(@Nonnull FlowNode endNode, @CheckForNull FlowNode afterBlock, @Nonnull ForkScanner scanner);

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

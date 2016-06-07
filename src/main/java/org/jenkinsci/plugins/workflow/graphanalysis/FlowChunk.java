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

import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

/**
 * Represents one or more FlowNodes in a linear sequence
 *
 * <p/> Common uses:
 * <ul>
 *     <li>A single FlowNode</li>
 *     <li>A block (with a {@link org.jenkinsci.plugins.workflow.graph.BlockStartNode} and {@link org.jenkinsci.plugins.workflow.graph.BlockEndNode})</li>
 *     <li>A Stage or other arbitrary run of nodes with a beginning and end, determined by a marker </li>
 * </ul>
 * @author <samvanoort@gmail.com>Sam Van Oort</samvanoort@gmail.com>
 */
public interface FlowChunk {

    public enum ChunkType {
        NODE, // single node
        BLOCK, // block with a BlockStartNode and BlockEndNode
        ARBITRARY // Random chunk of data
    }

    /**
     * Retrieve the starting node
     * @param execution Execution for the start and end nodes
     * @throws IllegalArgumentException If the start node is not part of the execution given
     * @return
     */
    @Nonnull
    public FlowNode getFirstNode(FlowExecution execution);

    /**
     * Retrieve the end node for the block
     * @param execution
     * @return Null if still in progress
     */
    @Nonnull
    public FlowNode getLastNode(FlowExecution execution);

    @Nonnull
    public String getFirstNodeId();

    @Nonnull
    public String getLastNodeId();

    /** True if block is finished */
    public boolean isComplete();

    @Nonnull
    public ChunkType getChunkType();
}

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
 * FlowChunk that holds direct references to the {@link FlowNode} instances and context info
 * This makes it easy to use in analysis and visualizations, but inappropriate to retain in caches, etc
 * @author Sam Van Oort
 */
public class MemoryFlowChunk implements FlowChunkWithContext {
    protected FlowNode firstNode;
    protected FlowNode lastNode;
    protected FlowNode nodeBefore;
    protected FlowNode nodeAfter;
    private long pauseTimeMillis = 0;

    public MemoryFlowChunk(@CheckForNull FlowNode before, @Nonnull FlowNode firstNode, @Nonnull FlowNode lastNode, @CheckForNull FlowNode nodeAfter) {
        this.setNodeBefore(before);
        this.setFirstNode(firstNode);
        this.setLastNode(lastNode);
        this.setNodeAfter(lastNode);
    }

    public MemoryFlowChunk() {

    }

    @Override
    public FlowNode getFirstNode() {
        return firstNode;
    }

    public void setFirstNode(FlowNode firstNode) {
        this.firstNode = firstNode;
    }


    @Override
    public FlowNode getLastNode() {
        return lastNode;
    }

    public void setLastNode(FlowNode lastNode) {
        this.lastNode = lastNode;
    }

    @Override
    public FlowNode getNodeBefore() {
        return nodeBefore;
    }

    public void setNodeBefore(FlowNode nodeBefore) {
        this.nodeBefore = nodeBefore;
    }

    @Override
    public FlowNode getNodeAfter() {
        return nodeAfter;
    }

    public void setNodeAfter(FlowNode nodeAfter) {
        this.nodeAfter = nodeAfter;
    }

    public long getPauseTimeMillis() {
        return pauseTimeMillis;
    }

    public void setPauseTimeMillis(long pauseTimeMillis) {
        this.pauseTimeMillis = pauseTimeMillis;
    }
}

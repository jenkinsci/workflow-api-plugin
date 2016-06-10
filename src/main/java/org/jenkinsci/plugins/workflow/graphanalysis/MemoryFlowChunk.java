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

import javax.annotation.Nonnull;

/**
 * FlowChunk that holds direct references to the {@link FlowNode} instances
 * This makes it easy to use in analysis and visualizations, but inappropriate to retain in caches, etc
 * @author <samvanoort@gmail.com>Sam Van Oort</samvanoort@gmail.com>
 */
public class MemoryFlowChunk implements FlowChunk, Timeable {

    private FlowNode firstNode;
    private FlowNode lastNode;
    private ChunkType chunkType;
    private boolean isComplete = false;

    private long startTimeMillis;
    private long endTimeMillis;
    private long durationMillis;
    private long pauseDurationMillis;

    public MemoryFlowChunk() {

    }

    @Nonnull
    @Override
    public FlowNode getFirstNode(FlowExecution execution) {
        return firstNode;
    }

    @Override
    public FlowNode getLastNode(FlowExecution execution) {
        return lastNode;
    }


    @Nonnull
    @Override
    public String getFirstNodeId() {
        return firstNode.getId();
    }

    @Override
    public String getLastNodeId() {
        return lastNode.getId();
    }

    @Override
    public boolean isComplete() {
        return isComplete;
    }

    @Override
    public ChunkType getChunkType() {
        return chunkType;
    }

    public void setChunkType(ChunkType type) {
        this.chunkType = type;
    }

    public FlowNode getFirstNode() {
        return firstNode;
    }

    public void setFirstNode(FlowNode firstNode) {
        this.firstNode = firstNode;
    }

    public FlowNode getLastNode() {
        return lastNode;
    }

    public void setLastNode(FlowNode lastNode) {
        this.lastNode = lastNode;
    }

    public void setIsComplete(boolean isComplete) {
        this.isComplete = isComplete;
    }

    @Override
    public long getStartTimeMillis() {
        return startTimeMillis;
    }

    public void setStartTimeMillis(long startTimeMillis) {
        this.startTimeMillis = startTimeMillis;
    }

    @Override
    public long getEndTimeMillis() {
        return endTimeMillis;
    }

    public void setEndTimeMillis(long endTimeMillis) {
        this.endTimeMillis = endTimeMillis;
    }

    @Override
    public long getDurationMillis() {
        return durationMillis;
    }

    public void setDurationMillis(long durationMillis) {
        this.durationMillis = durationMillis;
    }

    @Override
    public long getPauseDurationMillis() {
        return pauseDurationMillis;
    }

    public void setPauseDurationMillis(long pauseDurationMillis) {
        this.pauseDurationMillis = pauseDurationMillis;
    }
}

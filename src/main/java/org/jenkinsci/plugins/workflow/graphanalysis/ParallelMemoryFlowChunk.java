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
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Corresponds to a parallel block, acts as an in-memory container that can plug into status/timing APIs
 * @author <samvanoort@gmail.com>Sam Van Oort</samvanoort@gmail.com>
 */
public class ParallelMemoryFlowChunk extends MemoryFlowChunk implements ParallelFlowChunk<MemoryFlowChunk> {

    // LinkedHashMap to preserve insert order
    private LinkedHashMap<String, MemoryFlowChunk> branches = new LinkedHashMap<String, MemoryFlowChunk>();

    public ParallelMemoryFlowChunk(@Nonnull FlowNode firstNode, @Nonnull FlowNode lastNode) {
        super (null,firstNode, lastNode, null);
    }

    public ParallelMemoryFlowChunk(@CheckForNull FlowNode nodeBefore, @Nonnull FlowNode firstNode, @Nonnull FlowNode lastNode, @CheckForNull FlowNode nodeAfter) {
        super (nodeBefore,firstNode, lastNode, nodeAfter);
    }

    public void setBranch(@Nonnull String branchName, @Nonnull MemoryFlowChunk branchBlock) {
        branches.put(branchName, branchBlock);
    }

    @Override
    @Nonnull
    public Map<String,MemoryFlowChunk> getBranches() {
        return Collections.unmodifiableMap(branches);
    }

}

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

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Corresponds to a parallel block, does some customization to compute the timing with parallel branches
 * @author <samvanoort@gmail.com>Sam Van Oort</samvanoort@gmail.com>
 */
public class ParallelMemoryFlowChunk extends MemoryFlowChunk implements ParallelFlowChunk {
    private HashMap<String, MemoryFlowChunk> branches = new HashMap<String, MemoryFlowChunk>();

    @Override
    public void setPauseDurationMillis(long pauseDurationMillis) {
        throw new UnsupportedOperationException("Can't set pause duration for a parallel block, since it is determined by branches");
    }

    @Override
    public void setChunkType(ChunkType type) {
        throw new UnsupportedOperationException("Parallel chunk types are always block types, cannot override");
    }

    public ChunkType getChunkType() {
        return ChunkType.BLOCK;
    }

    public void setBranch(@Nonnull String branchName, @Nonnull MemoryFlowChunk branchBlock) {
        if (branchBlock.getChunkType() != ChunkType.BLOCK) {
            throw new IllegalArgumentException("All parallel branches must be blocks");
        }
        branches.put(branchName, branchBlock);
    }

    @Override
    @Nonnull
    public Map<String,MemoryFlowChunk> getBranches() {
        return Collections.unmodifiableMap(branches);
    }

    @Override
    public long getPauseDurationMillis() {
        if (branches.size() == 0) {
            return 0;
        }
        long longestPause = 0;
        for (Map.Entry<String, MemoryFlowChunk> branch : branches.entrySet()) {
            longestPause = Math.max(longestPause, branch.getValue().getPauseDurationMillis());
        }
        return longestPause;
    }
}

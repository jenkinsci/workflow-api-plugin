/*
 * The MIT License
 *
 * Copyright 2019 CloudBees, Inc.
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
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class MemoryFlowChunkTest {

    @Test
    public void constructor() {
        MockFlowNode start = new MockFlowNode("1");
        MockFlowNode blockStart = new MockFlowNode("2", start);
        MockFlowNode blockEnd = new MockFlowNode("3", blockStart);
        MockFlowNode end = new MockFlowNode("4", blockEnd);
        MemoryFlowChunk chunk = new MemoryFlowChunk(start, blockStart, blockEnd, end);
        assertThat(chunk.getNodeBefore(), equalTo(start));
        assertThat(chunk.getFirstNode(), equalTo(blockStart));
        assertThat(chunk.getLastNode(), equalTo(blockEnd));
        assertThat(chunk.getNodeAfter(), equalTo(end));
    }

    private static class MockFlowNode extends FlowNode {
        public MockFlowNode(String id, FlowNode... parents) {
            super(null, id, parents);
        }

        @Override
        protected String getTypeDisplayName() {
            return "Mock FlowNode";
        }
    }
}

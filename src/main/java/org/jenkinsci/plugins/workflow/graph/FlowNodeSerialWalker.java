/*
 * The MIT License
 *
 * Copyright 2016 CloudBees, Inc.
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

package org.jenkinsci.plugins.workflow.graph;

import java.util.Iterator;
import java.util.List;

/**
 * Unlike {@link FlowGraphWalker}, this iterator does not traverse the entire graph, but only walks backward and outward from one node.
 * If the start node is inside some nested blocks, it will step up through the {@link BlockStartNode}s.
 * But when moving backwards in time, when encountering blocks that the starting node was not inside,
 * it will visit the {@link BlockEndNode} and then the {@link BlockStartNode} without visiting any of the internal nodes.
 * The {@link FlowStartNode} will be last.
 */
public class FlowNodeSerialWalker implements Iterable<FlowNode> {

    private final FlowNode start;

    /**
     * Initializes the walker.
     * @param start the node which will be the first to be returned in the walk
     */
    public FlowNodeSerialWalker(FlowNode start) {
        this.start = start;
    }

    @Override public Iterator<FlowNode> iterator() {
        return new Iterator<FlowNode>() {
            FlowNode n = start;
            @Override public boolean hasNext() {
                return !n.getParents().isEmpty();
            }
            @Override public FlowNode next() {
                FlowNode prev = n;
                if (n instanceof BlockEndNode) {
                    n = ((BlockEndNode) n).getStartNode();
                } else {
                    List<FlowNode> parents = n.getParents();
                    if (parents.size() != 1) {
                        throw new IllegalStateException("unexpected " + n + " with parents " + parents);
                    }
                    n = parents.get(0);
                }
                return prev;
            }
        };
    }

}

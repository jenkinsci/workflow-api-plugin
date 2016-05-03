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

import org.jenkinsci.plugins.workflow.graph.BlockEndNode;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

import javax.annotation.Nonnull;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.List;

/**
 * Scanner that will scan down forks when we hit parallel blocks.
 * Think of it as the opposite of {@link DepthFirstScanner}:
 *   - We visit every node exactly once, but walk through all parallel forks before resuming the main flow
 *
 * This is near-optimal in many cases, since it keeps minimal state information and explores parallel blocks first
 * It is also very easy to make it branch/block-aware, since we have all the fork information at all times.
 * @author <samvanoort@gmail.com>Sam Van Oort</samvanoort@gmail.com>
 */
public class ForkScanner extends AbstractFlowScanner {

    /** These are the BlockStartNodes that begin parallel blocks
     *  There will be one entry for every executing parallel branch in current flow
     */
    ArrayDeque<FlowNode> forkStarts = new ArrayDeque<FlowNode>();

    /** FlowNode that will terminate the current parallel block */
    FlowNode currentParallelStart = null;

    /** How deep are we in parallel branches, if 0 we are linear */
    protected int parallelDepth = 0;

    @Override
    protected void reset() {
        if (_queue == null) {
            _queue = new ArrayDeque<FlowNode>();
        } else {
            _queue.clear();
        }
        forkStarts.clear();
        parallelDepth =0;
        currentParallelStart = null;
        _current = null;
        _next = null;
    }

    @Override
    protected void setHeads(@Nonnull Collection<FlowNode> heads) {
        if (heads.size() > 1) {
            throw new IllegalArgumentException("ForkedFlowScanner can't handle multiple head nodes yet");
            // TODO We need to implement this using filterableEnclosingBlocks
            // and add nodes to with the start of their parallel branches
        }
        _current = null;
        _queue.addAll(heads);
        _current = _queue.poll();
        _next = _current;
    }

    /**
     * Invoked when we start entering a parallel block (walking from head of the flow, so we see the block end first)
     * @param endNode Node where parents merge (final end node for the parallel block)
     * @param parents Parent nodes that end here
     * @return FlowNode next node to visit
     */
    protected FlowNode hitParallelEnd(BlockEndNode endNode, List<FlowNode> parents, Collection<FlowNode> blackList) {
        int branchesAdded = 0;
        BlockStartNode start = endNode.getStartNode();
        FlowNode output = null;
        for (FlowNode f : parents) {
            if (!blackList.contains(f)) {
                if (branchesAdded == 0) { // We use references because it is more efficient
                    currentParallelStart = start;
                    output = f;
                } else {
                    _queue.push(f);
                    forkStarts.push(start);
                }
                branchesAdded++;
            }
        }
        if (branchesAdded > 0) {
            parallelDepth++;
        }
        return output;
    }

    /**
     * Invoked when we complete parallel block, walking from the head (so encountered after the end)
     * @return FlowNode if we're the last node
     */
    protected FlowNode hitParallelStart() {
        FlowNode output = null;
        if (forkStarts.size() > 0) { // More forks (or nested parallel forks) remain
            FlowNode end = forkStarts.peek();
            // Nested parallel branches, finished nested level so we visit the head and enclosing parallel block
            if (end != currentParallelStart) {
                parallelDepth--;
                output = currentParallelStart;
            }

            // If the current end == currentParallelStart then we are finishing another branch of current flow
            currentParallelStart = end;
        } else {  // We're now at the top level of the flow, having finished our last (nested) parallel fork
            output = currentParallelStart;
            currentParallelStart = null;
            parallelDepth--;
        }
        // Handle cases where the BlockStartNode for the parallel block is blackListed
        return (output != null && !_blackList.contains(output)) ? output : null;
    }

    @Override
    protected FlowNode next(@Nonnull FlowNode current, @Nonnull Collection<FlowNode> blackList) {
        FlowNode output = null;

        // First we look at the parents of the current node if present
        if (current != null) {
            List<FlowNode> parents = current.getParents();
            if (parents == null || parents.size() == 0) {
                // welp done with this node, guess we consult the queue?
            } else if (parents.size() == 1) {
                FlowNode p = parents.get(0);
                if (p == currentParallelStart) {
                    // Terminating a parallel scan
                    FlowNode temp = hitParallelStart();
                    if (temp != null) { // Startnode for current parallel block now that it is done
                        return temp;
                    }
                } else if (!blackList.contains(p)) {
                    return p;
                }
            } else if (current instanceof BlockEndNode && parents.size() > 1) {
                // We must be a BlockEndNode that begins this
                BlockEndNode end = ((BlockEndNode) current);
                FlowNode possibleOutput = hitParallelEnd(end, parents, blackList); // What if output is block but other branches aren't?
                if (possibleOutput != null) {
                    return possibleOutput;
                }
            } else {
                throw new IllegalStateException("Found a FlowNode with multiple parents that isn't the end of a block! "+_current.toString());
            }
        }
        if (_queue.size() > 0) {
            output = _queue.pop();
            currentParallelStart = forkStarts.pop();
        }

        return output;
    }
}

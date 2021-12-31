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

import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

import edu.umd.cs.findbugs.annotations.NonNull;
import javax.annotation.concurrent.NotThreadSafe;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

/** Does a simple and somewhat efficient depth-first search of all FlowNodes in the DAG.
 *
 *  <p>Iteration order: depth-first search, revisiting parallel branches once done.
 *
 * <p> The behavior is analogous to {@link org.jenkinsci.plugins.workflow.graph.FlowGraphWalker} but faster.
 *  With parallel branches, the first branch is explored, then remaining branches are explored in order.
 *  This keeps ordering compatibility with {@link org.jenkinsci.plugins.workflow.graph.FlowGraphWalker} - it can be a drop-in replacement.
 *
 *  @author Sam Van Oort
 */
@NotThreadSafe
public class DepthFirstScanner extends AbstractFlowScanner {

    protected ArrayDeque<FlowNode> queue;

    protected HashSet<FlowNode> visited = new HashSet<>();

    @Override
    protected void reset() {
        if (this.queue == null) {
            this.queue = new ArrayDeque<>();
        } else {
            this.queue.clear();
        }
        this.visited.clear();
        this.myCurrent = null;
        this.myNext = null;
    }

    @Override
    protected void setHeads(@NonNull Collection<FlowNode> heads) {
        if (heads.isEmpty()) {
            return;
        }
        ArrayList<FlowNode>  nodes = new ArrayList<>(heads);
        for(int i=nodes.size()-1; i >= 0; i--) {
            queue.push(nodes.get(i));
        }
        myCurrent = queue.pop();
        myNext = myCurrent;
    }

    // Can be overridden with a more specific test
    protected boolean possibleParallelStart(FlowNode f) {
        return f instanceof BlockStartNode;
    }

    protected boolean testCandidate(FlowNode f, Collection<FlowNode> blackList) {
        return !blackList.contains(f) && !(possibleParallelStart(f) && visited.contains(f));
    }

    @Override
    protected FlowNode next(@NonNull FlowNode current, @NonNull final Collection<FlowNode> blackList) {
        FlowNode output = null;

        // Walk through parents of current node
        List<FlowNode> parents = current.getParents();  // Can't be null
        if (parents.size() == 1) {  // Common case, make it more efficient
            FlowNode f = parents.get(0);
            if (testCandidate(f, blackList)) {
                output = f;
            }
        } else if (parents.size() > 1) { // Add the branches in reverse order
            for(int i=parents.size()-1; i>=0; i--) {
                FlowNode f = parents.get(i);
                if (testCandidate(f, blackList)) {
                    queue.push(f);
                }
            }
        }

        if (output == null && queue.size() > 0) {
            output = queue.pop();
        }

        // Only BlockStartNodes, specifically ParallelStep can be the parent of multiple child nodes
        // Thus they're the only nodes we need to avoid visiting multiple times by recording the visit
        if (output instanceof BlockStartNode) {
            visited.add(output);
        }
        return output;
    }
}

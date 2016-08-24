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

import javax.annotation.Nonnull;
import javax.annotation.concurrent.NotThreadSafe;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

/** Does a simple and somewhat efficient depth-first search of all FlowNodes in the DAG.
 *
 *  <p/>Iteration order: depth-first search, revisiting parallel branches once done.
 *  With parallel branches, the first branch is explored, then remaining branches are explored in reverse order.
 *
 * <p/> The behavior is analogous to {@link org.jenkinsci.plugins.workflow.graph.FlowGraphWalker} but faster.
 *  @author <samvanoort@gmail.com>Sam Van Oort</samvanoort@gmail.com>
 */
@NotThreadSafe
public class DepthFirstScanner extends AbstractFlowScanner {

    protected ArrayDeque<FlowNode> queue;

    protected HashSet<FlowNode> visited = new HashSet<FlowNode>();

    protected void reset() {
        if (this.queue == null) {
            this.queue = new ArrayDeque<FlowNode>();
        } else {
            this.queue.clear();
        }
        this.visited.clear();
        this.myCurrent = null;
    }

    @Override
    protected void setHeads(@Nonnull Collection<FlowNode> heads) {
        Iterator<FlowNode> it = heads.iterator();
        if (it.hasNext()) {
            FlowNode f = it.next();
            myCurrent = f;
            myNext = f;
        }
        while (it.hasNext()) {
            queue.add(it.next());
        }
    }

    @Override
    protected FlowNode next(@Nonnull FlowNode current, @Nonnull Collection<FlowNode> blackList) {
        FlowNode output = null;

        // Walk through parents of current node
        List<FlowNode> parents = current.getParents();  // Can't be null
        for (FlowNode f : parents) {
            // Only ParallelStep nodes may be visited multiple times... but we can't just filter those
            // because that's in workflow-cps plugin which depends on this one.
            if (!blackList.contains(f) && !(f instanceof BlockStartNode && visited.contains(f))) {
                if (output == null ) {
                    output = f;  // Do direct assignment rather than needless push/pop
                } else {
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

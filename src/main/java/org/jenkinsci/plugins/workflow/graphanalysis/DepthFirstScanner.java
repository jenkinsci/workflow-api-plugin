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
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

/** Does a simple and efficient depth-first search:
 *   - This will visit each node exactly once, and walks through the first ancestry before revisiting parallel branches
 *  @author <samvanoort@gmail.com>Sam Van Oort</samvanoort@gmail.com>
 */
public class DepthFirstScanner extends AbstractFlowScanner {

    protected HashSet<FlowNode> _visited = new HashSet<FlowNode>();

    protected void reset() {
        if (this._queue == null) {
            this._queue = new ArrayDeque<FlowNode>();
        } else {
            this._queue.clear();
        }
        this._visited.clear();
        this._current = null;
    }

    @Override
    protected void setHeads(@Nonnull Collection<FlowNode> heads) {
        Iterator<FlowNode> it = heads.iterator();
        if (it.hasNext()) {
            FlowNode f = it.next();
            _current = f;
            _next = f;
        }
        while (it.hasNext()) {
            _queue.add(it.next());
        }
    }

    @Override
    protected FlowNode next(@Nonnull FlowNode current, @Nonnull Collection<FlowNode> blackList) {
        FlowNode output = null;
        // Walk through parents of current node
        if (current != null) {
            List<FlowNode> parents = current.getParents();
            if (parents != null) {
                for (FlowNode f : parents) {
                    // Only ParallelStep nodes may be visited multiple times... but we can't just filter those
                    // because that's in workflow-cps plugin which depends on this one
                    if (!blackList.contains(f) && !(f instanceof BlockStartNode && _visited.contains(f))) {
                        if (output == null ) {
                            output = f;
                        } else {
                            _queue.push(f);
                        }
                    }
                }
            }
        }

        if (output == null && _queue.size() > 0) {
            output = _queue.pop();
        }
        if (output instanceof BlockStartNode) {  // See above, best step towards just tracking parallel starts
            _visited.add(output);
        }
        return output;
    }
}

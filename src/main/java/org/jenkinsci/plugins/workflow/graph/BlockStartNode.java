/*
 * The MIT License
 *
 * Copyright (c) 2013-2014, CloudBees, Inc.
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

import org.jenkinsci.plugins.workflow.flow.FlowExecution;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import java.util.List;

/**
 * Together with {@link BlockEndNode}, designates some kind of nested structure that contains "children",
 * which are {@link FlowNode}s that are in between {@link BlockStartNode} and {@link BlockEndNode}
 *
 * @author Kohsuke Kawaguchi
 * @author Jesse Glick
 * @see BlockEndNode
 */
public abstract class BlockStartNode extends FlowNode {
    protected BlockStartNode(FlowExecution exec, String id, FlowNode... parents) {
        super(exec, id, parents);
    }

    protected BlockStartNode(FlowExecution exec, String id, List<FlowNode> parents) {
        super(exec, id, parents);
    }

    /** Return the {@link BlockEndNode} for this block, or null if the block hasn't completed yet. */
    @CheckForNull
    public BlockEndNode getEndNode() {
        return this.getExecution().getEndNode(this);
    }
}

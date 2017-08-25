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

import java.io.IOException;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;

/**
 * End of a block.
 * @see BlockStartNode
 */
public abstract class BlockEndNode<START extends BlockStartNode> extends FlowNode {
    private transient START start;
    private final String startId;

    public BlockEndNode(FlowExecution exec, String id, START start, FlowNode... parents) {
        super(exec, id, parents);
        this.start = start;
        startId = start.getId();
        this.enclosingId = findEnclosingId();
    }

    public BlockEndNode(FlowExecution exec, String id, START start, List<FlowNode> parents) {
        super(exec, id, parents);
        this.start = start;
        startId = start.getId();
        this.enclosingId = findEnclosingId();
    }

    /**
     * Returns the matching start node.
     * @return an earlier node matching this block
     * @throws IllegalStateException if the start node could not be reloaded after deserialization
     */
    public @Nonnull START getStartNode() {
        if (start == null) {
            try {
                start = (START) getExecution().getNode(startId);
                if (start == null) {
                    throw new IllegalStateException("Matching start node " + startId + " lost from deserialization");
                }
            } catch (IOException x) {
                throw new IllegalStateException("Could not load matching start node: " + x);
            }
        }
        return start;
    }

}

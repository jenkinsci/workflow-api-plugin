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

import edu.umd.cs.findbugs.annotations.NonNull;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;

import java.util.List;

/**
 * Start of the entire flow.
 *
 * @author Kohsuke Kawaguchi
 * @author Jesse Glick
 */
public class FlowStartNode extends BlockStartNode {
    public FlowStartNode(FlowExecution exec, String id) {
        // start node has no parents
        super(exec, id);
    }

    /**
     * @deprecated
     *      Why are you calling a method that always return empty list?
     */
    @NonNull
    @Override
    public List<FlowNode> getParents() {
        return super.getParents();
    }

    @Override
    protected String getTypeDisplayName() {
        return "Start of Pipeline";
    }
}

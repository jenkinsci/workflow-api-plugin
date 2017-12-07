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

import hudson.model.Result;
import org.jenkinsci.plugins.workflow.actions.ErrorAction;
import org.jenkinsci.plugins.workflow.actions.FlowNodeStatusAction;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException;
import org.jenkinsci.plugins.workflow.steps.Step;

import javax.annotation.CheckForNull;

/**
 * FlowNode that has no further FlowNodes inside.
 * Might be used to run a {@link Step}, for example.
 * @author Kohsuke Kawaguchi
 * @author Jesse Glick
 */
public abstract class AtomNode extends FlowNode {
    protected AtomNode(FlowExecution exec, String id, FlowNode... parents) {
        super(exec, id, parents);
    }

    @CheckForNull
    @Override
    public Result getStatus() {
        if (isActive()) {
            return null;
        } else {
            ErrorAction errorAction = getError();
            if (errorAction != null) {
                if (errorAction.getError() instanceof FlowInterruptedException) {
                    return Result.ABORTED;
                } else {
                    return Result.FAILURE;
                }
            }
            FlowNodeStatusAction statusAction = getPersistentAction(FlowNodeStatusAction.class);
            if (statusAction != null) {
                return statusAction.getResult();
            }

            return Result.SUCCESS;
        }
    }
}

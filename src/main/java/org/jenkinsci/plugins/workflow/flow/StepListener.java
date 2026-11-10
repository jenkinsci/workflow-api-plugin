/*
 * The MIT License
 *
 * Copyright (c) 2019, CloudBees, Inc.
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

package org.jenkinsci.plugins.workflow.flow;

import hudson.ExtensionPoint;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * {@link StepListener}s are fired before invoking a step but after the {@link FlowNode} has been created and the
 * {@link StepContext} has been populated. A {@link StepListener} can perform actions before the {@link Step} is
 * executed, such as calling {@link StepContext#onFailure} to make the step fail before it starts.
 */
public interface StepListener extends ExtensionPoint {
    /**
     * Called before a {@link Step} is invoked, but after its {@link FlowNode} and {@link StepContext} have been created.
     * Listeners can make the step fail by calling {@link StepContext#onFailure}.
     */
    void notifyOfNewStep(@NonNull Step step, @NonNull StepContext context);

    /**
     * Called on the thread that starts a {@link Step}, right before it calls {@link Step#start} and
     * {@link StepExecution#start}, after {@link #notifyOfNewStep} if no listener made the step fail. That thread closes
     * the returned object as soon as {@link StepExecution#start} returns or either method throws, whether or not the
     * step has completed. This lets a listener set up state for the code of the step's start only, such as a
     * thread-local tracing context: afterward the thread runs other steps, of this build or of others.
     *
     * @return what to close once the step has started, or {@code null}
     * @since TODO
     */
    @CheckForNull
    default AutoCloseable aroundStart(@NonNull Step step, @NonNull StepContext context) {
        return null;
    }
}

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

package org.jenkinsci.plugins.workflow.flow;

import com.google.common.collect.ImmutableList;
import hudson.Extension;
import hudson.ExtensionPoint;
import hudson.model.Action;
import hudson.model.ParametersAction;
import hudson.model.Queue;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.io.IOException;
import jenkins.scm.api.SCMRevisionAction;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * A way for plugins to copy metadata and associated files from one flow execution to another.
 * Useful when a new execution is not being created from scratch, but is a kind of clone of another.
 */
public abstract class FlowCopier implements ExtensionPoint {

    /**
     * Copies any required metadata or files from one to another.
     * @param original an initial build, typically complete
     * @param copy a new build, typically not yet started
     */
    public abstract void copy(FlowExecutionOwner original, FlowExecutionOwner copy) throws IOException, InterruptedException;

    /**
     * Convenience implementation that only operates on true builds.
     */
    public static abstract class ByRun extends FlowCopier {

        /**
         * Copies metadata between builds.
         * @param listener a way of logging messages to the copy
         */
        public abstract void copy(Run<?,?> original, Run<?,?> copy, TaskListener listener) throws IOException, InterruptedException;
        
        @Override public final void copy(FlowExecutionOwner original, FlowExecutionOwner copy) throws IOException, InterruptedException {
            Queue.Executable originalExec = original.getExecutable();
            Queue.Executable copyExec = copy.getExecutable();
            if (originalExec instanceof Run && copyExec instanceof Run) {
                copy((Run) originalExec, (Run) copyExec, copy.getListener());
            }
        }

    }

    @Restricted(NoExternalUse.class) // impl
    @Extension public static class StandardActions extends FlowCopier.ByRun {

        // TODO cloned from ReplayAction; consider whether it is appropriate to share these (related but not identical case)
        private static final Iterable<Class<? extends Action>> COPIED_ACTIONS = ImmutableList.of(
            ParametersAction.class,
            SCMRevisionAction.class
        );

        @Override public void copy(Run<?, ?> original, Run<?, ?> copy, TaskListener listener) throws IOException, InterruptedException {
            for (Class<? extends Action> type : COPIED_ACTIONS) {
                Action a = original.getAction(type);
                if (a != null) {
                    // Especially for ParametersAction we must replace any existing action.
                    // For example, scheduleBuild2 will typically create an instance from default parameter values.
                    copy.replaceAction(a);
                }
            }
        }

    }

}

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

package org.jenkinsci.plugins.workflow.flow;

import hudson.model.Queue;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.logging.Level;
import java.util.logging.Logger;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.model.TransientActionFactory;
import org.jenkinsci.plugins.workflow.log.LogStorage;
import org.jenkinsci.plugins.workflow.steps.StepContext;

/**
 * We need something that's serializable in small moniker that helps us find THE instance
 * of {@link FlowExecution}.
 */
public abstract class FlowExecutionOwner implements Serializable {

    private static final Logger LOGGER = Logger.getLogger(FlowExecutionOwner.class.getName());

    /**
     * @throws IOException
     *      if fails to find {@link FlowExecution}.
     */
    @NonNull
    public abstract FlowExecution get() throws IOException;

    /**
     * Same as {@link #get} but avoids throwing an exception or blocking.
     * @return a valid flow execution, or null if not ready or invalid
     */
    public @CheckForNull FlowExecution getOrNull() {
        try {
            return get();
        } catch (IOException x) {
            LOGGER.log(Level.WARNING, null, x);
            return null;
        }
    }

    /**
     * A directory (on the controller) where information may be persisted.
     * @see Run#getRootDir
     */
    public abstract File getRootDir() throws IOException;

    /**
     * The executor slot running this flow, such as a {@link Run}.
     * The conceptual "owner" of {@link FlowExecution}.
     *
     * (For anything that runs for a long enough time that demands flow, it better occupies an executor.
     * So this type restriction should still enable scriptler to use this.)
     * @return preferably an {@link Executable}
     */
    public abstract Queue.Executable getExecutable() throws IOException;

    /**
     * Returns the URL of the model object that owns {@link FlowExecution},
     * relative to the context root of Jenkins.
     *
     * This is usually not the same object as 'this'. This object
     * must have the {@code getExecution()} method to bind {@link FlowExecution} to the URL space
     * (or otherwise override {@link #getUrlOfExecution}).
     *
     * @return
     *      String like "job/foo/32/" with trailing slash but no leading slash.
     */
    public abstract String getUrl() throws IOException;

    public String getUrlOfExecution() throws IOException {
        return getUrl()+"execution/";
    }

    /**
     * The {@link Run#getExternalizableId}, if this owner is indeed a {@link Run}.
     * The default implementation uses {@link #getExecutable}
     * but an implementation may override this to avoid loading the actual {@link Run}.
     * @return an id, or null if unknown, unloadable, or unapplicable
     */
    @CheckForNull
    public String getExternalizableId() {
        try {
            var exec = getExecutable();
            if (exec instanceof Run) {
                return ((Run<?, ?>) exec).getExternalizableId();
            }
        } catch (IOException x) {
            LOGGER.log(Level.WARNING, "cannot look up externalizableId of " + this, x);
        }
        return null;
    }

    /**
     * {@link FlowExecutionOwner}s are equal to one another if and only if
     * they point to the same {@link FlowExecution} object.
     */
    @Override public abstract boolean equals(Object o);

    /**
     * Needs to be overridden as the {@link #equals(Object)} method is overridden.
     */
    @Override
    public abstract int hashCode();

    /**
     * Gets a listener to which we may print general messages.
     * Normally {@link StepContext#get} should be used, but in some cases there is no associated step.
     * <p>The listener should be remotable: if sent to an agent, messages printed to it should still appear in the log.
     * The same will then apply to calls to {@link StepContext#get} on {@link TaskListener}.
     */
    public @NonNull TaskListener getListener() throws IOException {
        try {
            return LogStorage.of(this).overallListener();
        } catch (InterruptedException x) {
            throw new IOException(x);
        }
    }

    /**
     * Marker interface for queue executables from {@link #getExecutable}.
     * A reasonable target type for {@link TransientActionFactory}.
     */
    public interface Executable extends Queue.Executable {
        
        /**
         * Gets the associated owner moniker.
         * @return the owner, or null if this instance is somehow inapplicable
         */
        @CheckForNull FlowExecutionOwner asFlowExecutionOwner();

    }

    /**
     * A placeholder implementation for use in compatibility stubs.
     */
    public static FlowExecutionOwner dummyOwner() {
        return new DummyOwner();
    }

    private static class DummyOwner extends FlowExecutionOwner {
        DummyOwner() {}
        @NonNull
        @Override public FlowExecution get() throws IOException {
            throw new IOException("not implemented");
        }
        @Override public File getRootDir() throws IOException {
            throw new IOException("not implemented");
        }
        @Override public Queue.Executable getExecutable() throws IOException {
            throw new IOException("not implemented");
        }
        @Override public String getUrl() throws IOException {
            throw new IOException("not implemented");
        }
        @Override public boolean equals(Object o) {
            return o instanceof DummyOwner;
        }
        @Override public int hashCode() {
            return 0;
        }
        @NonNull
        @Override public TaskListener getListener() {
            return TaskListener.NULL;
        }
    }

}

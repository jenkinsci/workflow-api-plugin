/*
 * The MIT License
 *
 * Copyright 2018 CloudBees, Inc.
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

package org.jenkinsci.plugins.workflow.log;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.console.ConsoleLogFilter;
import hudson.console.LineTransformationOutputStream;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import jenkins.util.BuildListenerAdapter;
import jenkins.util.JenkinsJVM;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.steps.BodyInvoker;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

/**
 * A way of decorating output from a {@link TaskListener}.
 * Similar to {@link ConsoleLogFilter} but better matched to Pipeline logging.
 * <p>May be passed to a {@link BodyInvoker} in lieu of {@link BodyInvoker#mergeConsoleLogFilters},
 * using {@link #merge} to pick up any earlier decorator in {@link StepContext#get}.
 * <p>Expected to be serializable either locally or over Remoting,
 * so an implementation of {@link #decorate} cannot assume that {@link JenkinsJVM#isJenkinsJVM}.
 * Any master-side configuration should thus be saved into instance fields when the decorator is constructed.
 * @see <a href="https://issues.jenkins-ci.org/browse/JENKINS-45693">JENKINS-45693</a>
 */
@Restricted(Beta.class)
public abstract class TaskListenerDecorator implements /* TODO Remotable */ Serializable {

    private static final long serialVersionUID = 1;

    private static final Logger LOGGER = Logger.getLogger(TaskListenerDecorator.class.getName());

    /**
     * Apply modifications to a build log.
     * Typical implementations use {@link LineTransformationOutputStream}.
     * @param logger a base logger
     * @return a possibly patched result
     */
    public abstract @Nonnull OutputStream decorate(@Nonnull OutputStream logger) throws IOException, InterruptedException;

    /**
     * Merges two decorators.
     * @param original the original decorator, if any
     * @param subsequent an overriding decorator, if any
     * @return null, or {@code original} or {@code subsequent}, or a merged result applying one then the other
     */
    public static @Nullable TaskListenerDecorator merge(@CheckForNull TaskListenerDecorator original, @CheckForNull TaskListenerDecorator subsequent) {
        if (original == null) {
            if (subsequent == null) {
                return null;
            } else {
                return subsequent;
            }
        } else {
            if (subsequent == null) {
                return original;
            } else {
                return new MergedTaskListenerDecorator(original, subsequent);
            }
        }
    }

    /**
     * Tries to translate a similar core interface into the new API.
     * <p>The filter may implement either {@link ConsoleLogFilter#decorateLogger(AbstractBuild, OutputStream)} and/or {@link ConsoleLogFilter#decorateLogger(Run, OutputStream)},
     * but only {@link ConsoleLogFilter#decorateLogger(AbstractBuild, OutputStream)} will be called, and with a null {@code build} parameter.
     * <p>The filter must be {@link Serializable}, and furthermore must not assume that {@link JenkinsJVM#isJenkinsJVM}:
     * the same constraints as for {@link TaskListenerDecorator} generally.
     * @param filter a filter, or null
     * @return an adapter, or null if it is null or (after issuing a warning) not {@link Serializable}
     * @see <a href="https://github.com/jenkinsci/jep/blob/master/jep/210/README.adoc#backwards-compatibility">JEP-210: Backwards Compatibility</a>
     */
    public static @CheckForNull TaskListenerDecorator fromConsoleLogFilter(@CheckForNull ConsoleLogFilter filter) {
        if (filter == null) {
            return null;
        } else if (filter instanceof Serializable) {
            return new ConsoleLogFilterAdapter(filter);
        } else {
            LOGGER.log(Level.WARNING, "{0} must implement Serializable to be used with Pipeline", filter.getClass());
            return null;
        }
    }

    /**
     * Allows a decorator to be applied to any build.
     * @see #apply
     */
    public interface Factory extends ExtensionPoint {

        /**
         * Supplies a decorator applicable to one build.
         * @param owner a build
         * @return a decorator, optionally
         */
        @CheckForNull TaskListenerDecorator of(@Nonnull FlowExecutionOwner owner);

    }

    /**
     * Wraps a logger in a supplied decorator as well as any available from {@link Factory}s.
     * <p>Does <em>not</em> apply {@link ConsoleLogFilter#all} even via {@link #fromConsoleLogFilter},
     * since there is no mechanical way to tell if implementations actually satisfy the constraints.
     * Anyway these singletons could not determine which build they are being applied to if remoted.
     * @param listener the main logger
     * @param owner a build
     * @param mainDecorator an additional contextual decorator to apply, if any
     * @return a possibly wrapped {@code listener}
     */
    public static BuildListener apply(@Nonnull TaskListener listener, @Nonnull FlowExecutionOwner owner, @CheckForNull TaskListenerDecorator mainDecorator) {
        JenkinsJVM.checkJenkinsJVM();
        List<TaskListenerDecorator> decorators = Stream.concat(
                ExtensionList.lookup(TaskListenerDecorator.Factory.class).stream().map(f -> f.of(owner)),
                Stream.of(mainDecorator)).
            filter(Objects::nonNull).
            collect(Collectors.toCollection(ArrayList::new));
        if (decorators.isEmpty()) {
            return CloseableTaskListener.of(BuildListenerAdapter.wrap(listener), listener);
        } else {
            Collections.reverse(decorators);
            return CloseableTaskListener.of(new DecoratedTaskListener(listener, decorators), listener);
        }
    }

    private static class MergedTaskListenerDecorator extends TaskListenerDecorator {

        private static final long serialVersionUID = 1;

        private final @Nonnull TaskListenerDecorator original;
        private final @Nonnull TaskListenerDecorator subsequent;

        MergedTaskListenerDecorator(TaskListenerDecorator original, TaskListenerDecorator subsequent) {
            this.original = original;
            this.subsequent = subsequent;
        }
        
        @Override public OutputStream decorate(OutputStream logger) throws IOException, InterruptedException {
            // TODO BodyInvoker.MergedFilter probably has these backwards
            return original.decorate(subsequent.decorate(logger));
        }

        @Override public String toString() {
            return "MergedTaskListenerDecorator[" + subsequent + ", " + original + "]";
        }

    }

    private static class ConsoleLogFilterAdapter extends TaskListenerDecorator {

        private static final long serialVersionUID = 1;

        @SuppressFBWarnings(value = "SE_BAD_FIELD", justification = "Explicitly checking for serializability.")
        private final @Nonnull ConsoleLogFilter filter;

        ConsoleLogFilterAdapter(ConsoleLogFilter filter) {
            assert filter instanceof Serializable;
            this.filter = filter;
        }

        @SuppressWarnings("deprecation") // the compatibility code in ConsoleLogFilter fails to delegate to the old overload when given a null argument
        @Override public OutputStream decorate(OutputStream logger) throws IOException, InterruptedException {
            return filter.decorateLogger((AbstractBuild) null, logger);
        }

        @Override public String toString() {
            return "ConsoleLogFilter[" + filter + "]";
        }

    }

    private static final class DecoratedTaskListener implements BuildListener {

        private static final long serialVersionUID = 1;

        /**
         * The listener we are delegating to, which was expected to be remotable.
         * Note that we ignore all of its methods other than {@link TaskListener#getLogger}.
         */
        private final @Nonnull TaskListener delegate;

        /**
         * A (nonempty) list of decorators we delegate to.
         * They are applied in reverse order, so the first one has the final say in what gets printed.
         */
        private final @Nonnull List<TaskListenerDecorator> decorators;

        private transient PrintStream logger;

        DecoratedTaskListener(TaskListener delegate, List<TaskListenerDecorator> decorators) {
            this.delegate = delegate;
            assert !decorators.isEmpty();
            assert !decorators.contains(null);
            this.decorators = decorators;
        }

        @Override public PrintStream getLogger() {
            if (logger == null) {
                OutputStream base = delegate.getLogger();
                for (TaskListenerDecorator decorator : decorators) {
                    try {
                        base = decorator.decorate(base);
                    } catch (Exception x) {
                        LOGGER.log(Level.WARNING, null, x);
                    }
                }
                try {
                    logger = new PrintStream(base, false, "UTF-8");
                } catch (UnsupportedEncodingException x) {
                    throw new AssertionError(x);
                }
            }
            return logger;
        }

        @Override public String toString() {
            return "DecoratedTaskListener[" + delegate + decorators + "]";
        }

    }

    private static final class CloseableTaskListener implements BuildListener, AutoCloseable {

        static BuildListener of(BuildListener mainDelegate, TaskListener closeDelegate) {
            if (closeDelegate instanceof AutoCloseable) {
                return new CloseableTaskListener(mainDelegate, closeDelegate);
            } else {
                return mainDelegate;
            }
        }

        private static final long serialVersionUID = 1;

        private final @Nonnull TaskListener mainDelegate;
        private final @Nonnull TaskListener closeDelegate;

        private CloseableTaskListener(TaskListener mainDelegate, TaskListener closeDelegate) {
            this.mainDelegate = mainDelegate;
            this.closeDelegate = closeDelegate;
            assert closeDelegate instanceof AutoCloseable;
        }

        @Override public PrintStream getLogger() {
            return mainDelegate.getLogger();
        }

        @Override public void close() throws Exception {
            ((AutoCloseable) closeDelegate).close();
        }

        @Override public String toString() {
            return "CloseableTaskListener[" + mainDelegate + " / " + closeDelegate + "]";
        }

    }

}

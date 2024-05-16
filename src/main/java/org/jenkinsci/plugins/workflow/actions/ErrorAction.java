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

package org.jenkinsci.plugins.workflow.actions;

import groovy.lang.GroovyClassLoader;
import groovy.lang.MissingMethodException;
import groovy.lang.MissingPropertyException;
import hudson.remoting.ProxyException;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import jenkins.model.Jenkins;
import org.apache.commons.io.output.NullOutputStream;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.AtomNode;
import org.jenkinsci.plugins.workflow.graph.BlockEndNode;
import org.jenkinsci.plugins.workflow.graphanalysis.DepthFirstScanner;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

/**
 * Attached to {@link FlowNode} that caused an error.
 *
 * This has to be Action because it's added after a node is created.
 */
public class ErrorAction implements PersistentAction {

    private final @NonNull Throwable error;

    public ErrorAction(@NonNull Throwable error) {
        Throwable errorForAction = error;
        if (isUnserializableException(error, new HashSet<>())) {
            errorForAction = new ProxyException(error);
        } else if (error != null) {
            try {
                Jenkins.XSTREAM2.toXMLUTF8(error, new NullOutputStream());
            } catch (Exception x) {
                // Typically SecurityException from ClassFilter.
                errorForAction = new ProxyException(error);
            }
        }
        this.error = errorForAction;
        String id = findId(error, new HashSet<>());
        if (id == null && error != null) {
            errorForAction.addSuppressed(new ErrorId());
            if (error != errorForAction) {
                // Make sure the original exception has the error ID, not just the copy here.
                error.addSuppressed(new ErrorId());
            }
        }
    }

    /**
     * Some exceptions don't serialize properly. If so, we need to replace that with
     * an equivalent that captures the same details but serializes nicely.
     */
    private boolean isUnserializableException(@CheckForNull Throwable error, Set<Throwable> visited) {
        if (error == null || !visited.add(error)) {
            return false;
        }
        // If the exception was defined in a Pipeline script, we don't want to serialize it
        // directly to avoid leaking a reference to the class loader for the Pipeline script.
        if (error.getClass().getClassLoader() instanceof GroovyClassLoader) {
            return true;
        }
        // Avoid MissingPropertyExceptions where the type points to the Pipeline script, it
        // contains references to the class loader for the Pipeline Script. Storing it leads
        // to memory leaks.
        if (error instanceof MissingPropertyException &&
                ((MissingPropertyException)error).getType() != null &&
                ((MissingPropertyException)error).getType().getClassLoader() instanceof GroovyClassLoader) {
            return true;
        }
        if (error instanceof MultipleCompilationErrorsException || error instanceof MissingMethodException) {
            return true;
        }
        if (isUnserializableException(error.getCause(), visited)) {
            return true;
        }
        for (Throwable t : error.getSuppressed()) {
            if (isUnserializableException(t, visited)) {
                return true;
            }
        }
        return false;
    }

    public @NonNull Throwable getError() {
        return error;
    }

    @Override
    public String getIconFileName() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return error.getMessage();
    }

    @Override
    public String getUrlName() {
        return null;
    }

    /**
     * Attempts to locate the first node of a build which threw an error.
     * Typically an error will be rethrown by enclosing blocks,
     * so this will look for the original node with an {@link ErrorAction}
     * matching the given stack trace.
     * @param error an error thrown at some point during a build
     * @param execution the build
     * @return the originating node, if one can be located;
     *         typically an {@link AtomNode} or {@link BlockEndNode}
     *         (in the latter case you may want to use {@link BlockEndNode#getStartNode} to look up actions)
     */
    public static @CheckForNull FlowNode findOrigin(@NonNull Throwable error, @NonNull FlowExecution execution) {
        FlowNode candidate = null;
        for (FlowNode n : new DepthFirstScanner().allNodes(execution)) {
            ErrorAction errorAction = n.getPersistentAction(ErrorAction.class);
            if (errorAction != null && equals(error, errorAction.getError())) {
                candidate = n; // continue search for earlier one
            }
        }
        return candidate;
    }

    private static @CheckForNull String findId(Throwable error, Set<Throwable> visited) {
        if (error == null || !visited.add(error)) {
            return null;
        }
        for (Throwable suppressed : error.getSuppressed()) {
            // We intentionally do not visit suppressed recursively so that we do not incorrectly select the ID from a
            // distinct error in a sibling branch of a parallel step.
            if (suppressed instanceof ErrorId) {
                return ((ErrorId) suppressed).uuid;
            }
        }
        return findId(error.getCause(), visited);
    }

    /**
     * {@link Throwable#equals} might not be reliable if the program has resumed
     * and stuff is deserialized.
     */
    @Restricted(Beta.class)
    public static boolean equals(Throwable t1, Throwable t2) {
        if (t1 == t2) {
            return true;
        }
        boolean noProxy = t1.getClass() != ProxyException.class && t2.getClass() != ProxyException.class;
        if (noProxy && t1.getClass() != t2.getClass()) {
            return false;
        } else if (noProxy && !Objects.equals(t1.getMessage(), t2.getMessage())) {
            return false;
        } else {
            String id1 = findId(t1, new HashSet<>());
            if (id1 != null) {
                return id1.equals(findId(t2, new HashSet<>()));
            }
            // No ErrorId, use a best-effort approach that doesn't work across restarts for exceptions thrown
            // synchronously from the CPS VM thread.
            // Check that stack traces match, but specifically avoid checking suppressed exceptions, which are often
            // modified after ErrorAction is written to disk when steps like parallel are involved.
            while (t1 != null && t2 != null) {
                if (!Arrays.equals(t1.getStackTrace(), t2.getStackTrace())) {
                    return false;
                }
                t1 = t1.getCause();
                t2 = t2.getCause();
            }
            return (t1 == null) == (t2 == null);
        }
    }

    private static class ErrorId extends Throwable {
        private final String uuid;

        public ErrorId() {
            this.uuid = UUID.randomUUID().toString();
        }

        @Override
        public String getMessage() {
            return uuid;
        }

        @Override
        public Throwable fillInStackTrace() {
            // We only care about the UUID, the stack trace is not relevant.
            return this;
        }
    }

}

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
import hudson.Functions;
import jenkins.model.Jenkins;
import org.apache.commons.io.output.NullOutputStream;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graphanalysis.ForkScanner;

/**
 * Attached to {@link FlowNode} that caused an error.
 *
 * This has to be Action because it's added after a node is created.
 */
public class ErrorAction implements PersistentAction {

    private final @NonNull Throwable error;

    public ErrorAction(@NonNull Throwable error) {
        if (isUnserializableException(error)) {
            error = new ProxyException(error);
        } else if (error != null) {
            try {
                Jenkins.XSTREAM2.toXMLUTF8(error, new NullOutputStream());
            } catch (Exception x) {
                // Typically SecurityException from ClassFilter.
                error = new ProxyException(error);
            }
        }
        this.error = error;
    }

    /**
     * Some exceptions don't serialize properly. If so, we need to replace that with
     * an equivalent that captures the same details but serializes nicely.
     */
    private boolean isUnserializableException(@CheckForNull Throwable error) {
        if (error == null) {
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
        if (isUnserializableException(error.getCause())) {
            return true;
        }
        for (Throwable t : error.getSuppressed()) {
            if (isUnserializableException(t)) {
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
     * @return the originating node, if one can be located
     */
    public static @CheckForNull FlowNode findOrigin(@NonNull Throwable error, @NonNull FlowExecution execution) {
        FlowNode candidate = null;
        for (FlowNode n : new ForkScanner().allNodes(execution)) {
            ErrorAction errorAction = n.getPersistentAction(ErrorAction.class);
            if (errorAction != null && equals(error, errorAction.getError())) {
                candidate = n; // continue search for earlier one
            }
        }
        return candidate;
    }

    /**
     * {@link Throwable#equals} might not be reliable if the program has resumed
     * and stuff is deserialized.
     */
    private static boolean equals(Throwable t1, Throwable t2) {
        if (t1 == t2) {
            return true;
        } else if (t1.getClass() != t2.getClass()) {
            return false;
        } else {
            return Functions.printThrowable(t1).equals(Functions.printThrowable(t2));
        }
    }

}

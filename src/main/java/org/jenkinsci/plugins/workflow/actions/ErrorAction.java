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

import groovy.lang.MissingMethodException;
import hudson.remoting.ClassFilter;
import hudson.remoting.ProxyException;

import java.util.HashSet;
import java.util.Set;

import javax.annotation.CheckForNull;
import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.jenkinsci.plugins.workflow.graph.AtomNode;
import javax.annotation.Nonnull;
import jenkins.model.Jenkins;
import org.apache.commons.io.output.NullOutputStream;

/**
 * Attached to {@link AtomNode} that caused an error.
 *
 * This has to be Action because it's added after a node is created.
 */
public class ErrorAction implements PersistentAction {

    private final @Nonnull Throwable error;

    public ErrorAction(@Nonnull Throwable error) {
        if (isUnserializableException(error, new HashSet<Throwable>())) {
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
     * an equivalent that captures the same details but serializes nicely. Tracking
     * visited nodes is required in some corner cases that might result in a cycle.
     */
    private boolean isUnserializableException(@CheckForNull Throwable error, Set<Throwable> visited) {
        if (error == null || visited.contains(error)) {
            return false;
        }
        if (error instanceof MultipleCompilationErrorsException || error instanceof MissingMethodException) {
            return true;
        }
        // Only need to add serializable visited nodes since any unserializable
        // encountered results in ending the recursion and returning true
        visited.add(error);
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

    public @Nonnull Throwable getError() {
        return error;
    }

    public String getIconFileName() {
        return null;
    }

    public String getDisplayName() {
        return error.getMessage();
    }

    public String getUrlName() {
        return null;
    }
}

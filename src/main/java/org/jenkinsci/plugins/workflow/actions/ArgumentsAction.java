/*
 * The MIT License
 *
 * Copyright (c) 2017, CloudBees, Inc.
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

import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graph.StepNode;
import org.jenkinsci.plugins.workflow.graph.StepArgumentsFormatter;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Stores some or all of the arguments used to create and configure the {@link Step} executed by a {@link FlowNode}.
 * This allows you to inspect information supplied in the pipeline script and otherwise discarded at runtime.
 * Supplied argument values can be hidden and replaced with a {@link NotStoredReason} for security or performance.
 */
public abstract class ArgumentsAction implements PersistentAction {

    /** Used as a placeholder for arguments not stored for various reasons */
    public enum NotStoredReason {
        /** Denotes an unsafe value that cannot be stored/displayed due to sensitive info */
        MASKED_VALUE,

        /** Denotes an object that is too big to retain, such as strings exceeding {@link #MAX_STRING_LENGTH} */
        OVERSIZE_VALUE
    }

    /** Largest string we'll persist in the Step, for performance reasons */
    public static final int MAX_STRING_LENGTH = 1024;

    @Override
    public String getIconFileName() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return "Step Arguments";
    }

    @Override
    public String getUrlName() {
        return "stepArguments";
    }

    /**
     * Get the map of arguments for the {@link Step}, with a {@link NotStoredReason} instead of the value
     *  if part of the arguments are not retained.
     * @return The arguments for the Step.
     */
    @Nonnull
    public Map<String,Object> getArguments() {
        return Collections.unmodifiableMap(getArgumentsInternal());
    }

    @Nonnull
    public static Map<String, Object> getArguments(@Nonnull  FlowNode n) {
        ArgumentsAction act = n.getPersistentAction(ArgumentsAction.class);
        return (act != null) ? act.getArguments() : (Map)(Collections.emptyMap());
    }

    /**
     * Get just the fully stored, non-null arguments
     * This means the arguments with all {@link NotStoredReason} or null values removed
     * @return Map of all completely stored arguments
     */
    @Nonnull
    public Map<String, Object> getFilteredArguments() {
        Map<String, Object> internalArgs = this.getArgumentsInternal();
        if (internalArgs.size() == 0) {
            return Collections.emptyMap();
        }
        HashMap<String, Object> filteredArguments = new HashMap<String, Object>();
        for (Map.Entry<String, Object> entry : internalArgs.entrySet()) {
            if (entry.getValue() != null && !(entry.getValue() instanceof NotStoredReason)) {
                filteredArguments.put(entry.getKey(), entry.getValue());
            }
        }
        return filteredArguments;
    }

    /**
     * Get just the fully stored, non-null arguments
     * This means the arguments with all {@link NotStoredReason} or null values removed
     * @param n FlowNode to get arguments for
     * @return Map of all completely stored arguments
     */
    @Nonnull
    public static Map<String, Object> getFilteredArguments(@Nonnull FlowNode n) {
        ArgumentsAction act = n.getPersistentAction(ArgumentsAction.class);
        return act != null ? act.getFilteredArguments() : Collections.EMPTY_MAP;
    }

    /** Return a tidy string description for the step arguments, or null if none is present or we can't make one */
    @CheckForNull
    public static String getArgumentDescriptionString(@Nonnull FlowNode n) {
        if (n instanceof StepNode) {
            StepDescriptor descriptor = ((StepNode) n).getDescriptor();
            Map<String, Object> filteredArgs = getFilteredArguments(n);
            if (descriptor instanceof StepArgumentsFormatter) {
                // If the StepDescriptor provides its own way to format descriptions, use it
                return ((StepArgumentsFormatter)descriptor).getDescriptionString(filteredArgs);
            } else {
                if (filteredArgs.size() == 0 || filteredArgs.size() > 1) {
                    return null;  // No description or can't generate a description on our own
                } else if (filteredArgs.size() == 1) {
                    Object val = filteredArgs.values().iterator().next();
                    return (val != null) ? val.toString() : null;
                }
                return null;
            }
        }
        return null;
    }

    /**
     * Return a fast view of internal arguments, without creating immutable wrappers
     * @return Internal arguments
     */
    @Nonnull
    protected abstract Map<String, Object> getArgumentsInternal();

    /**
     * Get the value of a argument, or null if not present/not stored.
     * Use {@link #getArgumentValueOrReason(String)} if you want to return the {@link NotStoredReason} rather than null.
     * @param argumentName Argument name of step to look up.
     * @return Argument value or null if not present/not stored.
     */
    @CheckForNull
    public Object getArgumentValue(@Nonnull String argumentName) {
        Object val = getArgumentValueOrReason(argumentName);
        return (val == null || val instanceof NotStoredReason) ? null : val;
    }

    /**
     * Get the argument value or its {@link NotStoredReason} if it has been intentionally omitted.
     * @param argumentName Name of step argument to find value for
     * @return Argument value, null if nonexistent/null, or NotStoredReason if it existed by was masked out.
     */
    @CheckForNull
    public Object getArgumentValueOrReason(@Nonnull String argumentName) {
        return getArgumentsInternal().get(argumentName);
    }

    /**
     * Test if {@link Step} arguments are persisted in an unaltered form.
     * @return True if full arguments are retained, false if some have been removed for security, size, or other reasons.
     */
    public abstract boolean isFullArguments();
}

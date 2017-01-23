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
import org.jenkinsci.plugins.workflow.steps.Step;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.Map;

/**
 * Stores some or all of the information used to create and configure the {@link Step} executed by a {@link FlowNode}.
 * This allows you to inspect information supplied in the pipeline script and otherwise discarded at runtime.
 * Supplied parameter values can be hidden and replaced with a {@link NotStoredReason} for security or performance.
 */
public abstract class StepInfoAction implements PersistentAction {

    /** Used as a placeholder for parameters not stored for various reasons */
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
        return "StepInfo";
    }

    @Override
    public String getUrlName() {
        return "stepInfo";
    }

    /**
     * Get the map of parameters for the {@link Step}, with a {@link NotStoredReason} instead of the value
     *  if part of the parameters are not retained.
     * @return The parameters for the Step.
     */
    @Nonnull
    public Map<String,Object> getParameters() {
        return Collections.unmodifiableMap(getParametersInternal());
    }

    @Nonnull
    public static Map<String, Object> getNodeParameters(@Nonnull  FlowNode m) {
        StepInfoAction act = m.getPersistentAction(StepInfoAction.class);
        return (act != null) ? act.getParameters() : (Map)(Collections.emptyMap());
    }

    /**
     * Return a fast view of internal parameters, without creating immutable wrappers
     * @return Internal parameters
     */
    @Nonnull
    protected abstract Map<String, Object> getParametersInternal();

    /**
     * Get the value of a parameter, or null if not present/not stored.
     * Use {@link #getParameterValueOrReason(String)} if you want to return the {@link NotStoredReason} rather than null.
     * @param parameterName Parameter name of step to look up.
     * @return Parameter value or null if not present/not stored.
     */
    @CheckForNull
    public Object getParameterValue(@Nonnull String parameterName) {
        Object val = getParameterValueOrReason(parameterName);
        return (val == null || val instanceof NotStoredReason) ? null : val;
    }

    /**
     * Get the parameter value or its {@link NotStoredReason} if it has been intentionally omitted.
     * @param parameterName Name of step parameter to find value for
     * @return Parameter value, null if nonexistent/null, or NotStoredReason if it existed by was masked out.
     */
    @CheckForNull
    public Object getParameterValueOrReason(@Nonnull String parameterName) {
        return getParametersInternal().get(parameterName);
    }

    /**
     * Test if {@link Step} parameters are persisted in an unaltered form.
     * @return True if full parameters are retained, false if some have been removed for security, size, or other reasons.
     */
    public abstract boolean isFullParameters();
}

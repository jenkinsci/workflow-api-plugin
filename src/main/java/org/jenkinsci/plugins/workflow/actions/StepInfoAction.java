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
import java.util.Map;

/**
 * Stores some or all of the information used to configure the {@link Step} run for a {@link FlowNode}.
 * This is used to allow inspecting information supplied in the pipeline script and otherwise not retained at runtime.
 *
 * There is flexibility in what information is retained, to allow for:
 * <ul>
 *     <li>Serializing and storing the original Step, for simplicity.</li>
 *     <li>Storing just the parameters, if this can be done more efficiently or the complete Step can't be kept.</li>
 *     <li>Storing some of the parameters, with others masked out</li>
 *     <li>Separating parameters excluded for security (masking sensitive info), or for size reasons</li>
 * </ul>
 */
public abstract class StepInfoAction implements PersistentAction {

    /** Used as a placeholder for parameters not stored for various reasons */
    public enum NotStoredReason {
        /** Denotes an unsafe value that cannot be stored/displayed due to sensitive info */
        MASKED_VALUE,

        /** Denotes an object that is oversized and thus not serialized */
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
     * Obtain the actual {@link Step} if we can have it, otherwise null.
     * @return Actual Step, or null if none.
     */
    @CheckForNull
    public abstract Step getStep();


    /**
     * Get the map of parameters for the {@link Step}Step, with a {@link NotStoredReason} instead of the value
     *  if part of the parameters are not retained.
     * @return The parameters for the Step.
     */
    @Nonnull
    public abstract Map<String,Object> getParameters();

    /**
     * Get the value of a parameter, or null if not present/not stored.
     * @param parameterName Parameter name of step to look up.
     * @return Parameter value.
     */
    public Object getParameterValue(@Nonnull String parameterName) {
        Map<String, Object> vals = getParameters();
        Object val = vals.get(parameterName);
        return (val == null || val instanceof NotStoredReason) ? null : val;
    }

    /**
     * Test if {@link Step} parameters are persisted in an unaltered form.
     * Generally we should be able to get a non-null result for {@link #getStep()} if this returns true.
     * @return True if full parameters are retained, false if some have been removed for security, size, or other reasons.
     */
    public abstract boolean isFullParameters();
}

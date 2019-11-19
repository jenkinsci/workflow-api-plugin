package org.jenkinsci.plugins.workflow.flow;

/**
 * Can be added to advertise the ability to mark pipeline components which prevent pipelines from being able to resume
 * after restart or after pause.
 *
 * Pipelines which cannot resume will simply fail.
 */
public interface BlockableResume {
    /** Return true if we prevent the abiity to resume. */
    boolean isResumeBlocked();

    /** Set resume on or off - may throw an {@link IllegalArgumentException} if trying to illegally toggle. */
    void setResumeBlocked(boolean isBlocked);
}

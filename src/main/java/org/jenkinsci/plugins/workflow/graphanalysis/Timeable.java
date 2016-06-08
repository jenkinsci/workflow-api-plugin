package org.jenkinsci.plugins.workflow.graphanalysis;

/**
 * Something with distinct start and end times
 */
public interface Timeable {
    public long getStartTimeMillis();
    public long getEndTimeMillis();
    public long getDurationMillis();
    public long getPauseDurationMillis();
}

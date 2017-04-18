package org.jenkinsci.plugins.workflow.graph;

import javax.annotation.CheckForNull;
import java.util.Map;

/**
 * Interface for StepDescriptors that can format (filtered) named {@link org.jenkinsci.plugins.workflow.steps.Step}
 * arguments into a formatted description string.
 */
public interface StepArgumentsFormatter {
    /** Return a string description from the step arguments */
    public String getDescriptionString(@CheckForNull Map<String, Object> namedArguments);
}

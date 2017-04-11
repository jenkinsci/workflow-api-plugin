package org.jenkinsci.plugins.workflow.graph;

import javax.annotation.CheckForNull;
import java.util.Map;

/**
 * Interface for StepDescriptors to provide a description string of the step parameters from their (filtered) values
 * TODO Give me a less ridiculous name
 */
public interface StepParametersDescriptorGenerator {
    /** Return a string description from the step parameters */
    public String getDescriptionString(@CheckForNull Map<String, Object> parameters);
}

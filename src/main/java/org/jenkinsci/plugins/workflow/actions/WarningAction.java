package org.jenkinsci.plugins.workflow.actions;

import hudson.model.Result;
import javax.annotation.Nonnull;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

/**
 * Action to be attached to a {@link FlowNode} to signify that some non-fatal warning occurred
 * during execution of a {@code Step}.
 *
 * Visualizations should treat FlowNodes with this action as if the FlowNode's result was
 * {@link Result#UNSTABLE}.
 */
public final class WarningAction implements PersistentAction {
    private final @Nonnull String message;

    public WarningAction(@Nonnull String message) {
        this.message = message;
    }

    public @Nonnull String getMessage() {
        return message;
    }

    @Override
    public String getDisplayName() {
        return "Warning: " + message;
    }

    @Override
    public String getIconFileName() {
        return null;
    }

    @Override
    public String getUrlName() {
        return null;
    }
}

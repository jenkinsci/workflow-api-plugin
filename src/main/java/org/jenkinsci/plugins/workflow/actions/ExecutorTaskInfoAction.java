package org.jenkinsci.plugins.workflow.actions;

import hudson.model.InvisibleAction;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

/**
 * Records information for a {@code node} block.
 */
public class ExecutorTaskInfoAction extends InvisibleAction implements PersistentAction {
    private static final long serialVersionUID = 1;

    private String whyBlocked;
    private boolean launched;
    // Initialized at -1 for "not started yet".
    private long whenStartedOrCancelled = -1L;

    public ExecutorTaskInfoAction() {
    }

    public ExecutorTaskInfoAction(@Nonnull String whyBlocked) {
        this.whyBlocked = whyBlocked;
    }

    public void setLaunched() {
        // Because we're not blocked any more at this point!
        this.whyBlocked = null;
        this.whenStartedOrCancelled = System.currentTimeMillis();
        this.launched = true;
    }

    public void setWhyBlocked(@Nonnull String whyBlocked) {
        this.whyBlocked = whyBlocked;
    }

    @CheckForNull
    public String getWhyBlocked() {
        return whyBlocked;
    }

    public long getWhenStartedOrCancelled() {
        return whenStartedOrCancelled;
    }

    public void cancelTask() {
        this.whyBlocked = null;
        this.whenStartedOrCancelled = System.currentTimeMillis();
    }

    public boolean isQueued() {
        return whyBlocked != null && whenStartedOrCancelled == -1;
    }

    public boolean isLaunched() {
        return launched;
    }

    public boolean isCancelled() {
        return !launched && whyBlocked == null && whenStartedOrCancelled > -1;
    }

    public static boolean isNodeQueued(@Nonnull FlowNode node) {
        ExecutorTaskInfoAction action = node.getAction(ExecutorTaskInfoAction.class);
        if (action != null) {
            return action.isQueued();
        } else {
            return false;
        }
    }

    public static boolean isNodeLaunched(@Nonnull FlowNode node) {
        ExecutorTaskInfoAction action = node.getAction(ExecutorTaskInfoAction.class);
        if (action != null) {
            return action.isLaunched();
        } else {
            return false;
        }
    }

    public static boolean isNodeCancelled(@Nonnull FlowNode node) {
        ExecutorTaskInfoAction action = node.getAction(ExecutorTaskInfoAction.class);
        if (action != null) {
            return action.isCancelled();
        } else {
            return false;
        }
    }
}

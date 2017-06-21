package org.jenkinsci.plugins.workflow.actions;

import hudson.model.InvisibleAction;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

/**
 * Records information for a {@code node} block.
 */
public class ExecutorTaskInfoAction extends InvisibleAction implements FlowNodeAction, PersistentAction {
    private static final long serialVersionUID = 1;

    private String whyBlocked;
    private boolean launched;
    // Initialized at -1 for "not started yet".
    private long whenStartedOrCanceled = -1L;

    private transient FlowNode parent;

    public ExecutorTaskInfoAction(FlowNode parent) {
        this.parent = parent;
    }

    public ExecutorTaskInfoAction(@Nonnull String whyBlocked, FlowNode parent) {
        this(parent);
        this.whyBlocked = whyBlocked;
    }

    public FlowNode getParent() {
        return parent;
    }

    @Override
    public void onLoad(FlowNode parent) {
        this.parent = parent;
    }

    public void setLaunched() {
        // Because we're not blocked any more at this point!
        this.whyBlocked = null;
        this.whenStartedOrCanceled = System.currentTimeMillis();
        this.launched = true;
    }

    public void setWhyBlocked(@Nonnull String whyBlocked) {
        this.whyBlocked = whyBlocked;
    }

    @CheckForNull
    public String getWhyBlocked() {
        return whyBlocked;
    }

    public long getWhenStartedOrCanceled() {
        return whenStartedOrCanceled;
    }

    public void cancelTask() {
        this.whyBlocked = null;
        this.whenStartedOrCanceled = System.currentTimeMillis();
    }

    public boolean isQueued() {
        return whyBlocked != null && whenStartedOrCanceled == -1;
    }

    public boolean isLaunched() {
        return launched;
    }

    public boolean isCanceled() {
        return !launched && whyBlocked == null && whenStartedOrCanceled > -1;
    }
}

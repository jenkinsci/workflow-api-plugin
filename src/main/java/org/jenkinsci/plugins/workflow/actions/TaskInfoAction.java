package org.jenkinsci.plugins.workflow.actions;

import hudson.model.InvisibleAction;
import hudson.model.Queue;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

/**
 * Records information for a {@code node} block.
 */
public abstract class TaskInfoAction extends InvisibleAction implements PersistentAction {
    private static final long serialVersionUID = 1;

    public enum QueueState {
        QUEUED,
        CANCELLED,
        LAUNCHED,
        UNKNOWN
    }
    
    /**
     * Gets the {@link Queue.Item} for this task, if it exists.
     *
     * @return The item, or null if it's not in the queue.
     */
    @CheckForNull
    protected abstract Queue.Item itemInQueue();

    @CheckForNull
    public String getWhyBlocked() {
        Queue.Item item = itemInQueue();

        return item != null ? item.getWhy() : null;
    }

    /**
     * Gets whether this task is currently queued.
     *
     * @return True if there's an item in the queue for this task, false otherwise.
     */
    private boolean isQueued() {
        return itemInQueue() != null;
    }

    /**
     * Get the current {@link QueueState} for a {@link FlowNode}. Will return {@link QueueState#UNKNOWN} for
     * any node without one of an {@link TaskInfoAction} or {@link WorkspaceAction}.
     *
     * @param node A non-null {@link FlowNode}
     * @return The current queue state of the flownode.
     */
    @Nonnull
    public static QueueState getNodeState(@Nonnull FlowNode node) {
        WorkspaceAction workspaceAction = node.getPersistentAction(WorkspaceAction.class);
        if (workspaceAction != null) {
            return QueueState.LAUNCHED;
        } else {
            TaskInfoAction action = node.getPersistentAction(TaskInfoAction.class);
            if (action != null) {
                if (action.isQueued()) {
                    return QueueState.QUEUED;
                } else {
                    // Getting here means we queued a task, but it's not in the queue any more and we
                    // never actually launched an executable, so implicitly it's cancelled.
                    return QueueState.CANCELLED;
                }
            } else {
                return QueueState.UNKNOWN;
            }
        }
    }

    @CheckForNull
    public static String getWhyBlockedForNode(@Nonnull FlowNode node) {
        TaskInfoAction action = node.getPersistentAction(TaskInfoAction.class);
        if (action != null) {
            return action.getWhyBlocked();
        } else {
            return null;
        }
    }
}

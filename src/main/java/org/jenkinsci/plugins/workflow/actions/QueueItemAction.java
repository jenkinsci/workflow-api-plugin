package org.jenkinsci.plugins.workflow.actions;

import hudson.model.InvisibleAction;
import hudson.model.Queue;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

/**
 * Records information for a {@code node} block.
 */
public abstract class QueueItemAction extends InvisibleAction implements PersistentAction {
    private static final long serialVersionUID = 1;

    /**
     * Possible queue states for the item associated with this {@link FlowNode}.
     */
    public enum QueueState {
        /**
         * The {@link Queue.Item} is currently in the queue.
         */
        QUEUED,

        /**
         * The {@link Queue.Item} has left the queue but no {@link WorkspaceAction} is present on the
         * {@link FlowNode}.
         */
        CANCELLED,

        /**
         * The {@link Queue.Item} has actually launched, meaning it has left the queue and there is a
         * {@link WorkspaceAction} present on the {@link FlowNode}.
         */
        LAUNCHED,

        /**
         * The {@link FlowNode} has neither a {@link QueueItemAction} nor a {@link WorkspaceAction}, so its
         * queue status cannot be determined. This may be the case if the {@link FlowNode} is not actually
         * for a node block.
         */
        UNKNOWN
    }
    
    /**
     * Gets the {@link Queue.Item} for this task, if it exists.
     *
     * @return The item, or null if it's not in the queue.
     */
    @CheckForNull
    public abstract Queue.Item itemInQueue();

    /**
     * Get the current {@link QueueState} for a {@link FlowNode}. Will return {@link QueueState#UNKNOWN} for
     * any node without one of an {@link QueueItemAction} or {@link WorkspaceAction}.
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
            QueueItemAction action = node.getPersistentAction(QueueItemAction.class);
            if (action != null) {
                Queue.Item item = action.itemInQueue();
                if (item == null) {
                    // The item has left the queue completely and there isn't a WorkspaceAction.
                    return QueueState.CANCELLED;
                } else if (item instanceof Queue.LeftItem) {
                    if (((Queue.LeftItem) item).isCancelled()) {
                        // The item has left the queue due to cancellation but is still in leftItems.
                        return QueueState.CANCELLED;
                    } else {
                        // The item has left the queue due to being launched but is still in leftItems.
                        return QueueState.LAUNCHED;
                    }
                } else {
                    // The item is still in the queue.
                    return QueueState.QUEUED;
                }
            } else {
                return QueueState.UNKNOWN;
            }
        }
    }

    @CheckForNull
    public static Queue.Item getQueueItem(@Nonnull FlowNode node) {
        QueueItemAction action = node.getPersistentAction(QueueItemAction.class);
        return action != null ? action.itemInQueue() : null;
    }
}

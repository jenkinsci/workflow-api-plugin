package org.jenkinsci.plugins.workflow.flow;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import org.jenkinsci.plugins.workflow.graph.FlowEndNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Listens for significant status updates for a {@link FlowExecution}, such as started running or completed.
 *
 * @since 2.14
 * @author Andrew Bayer
 */
public abstract class FlowExecutionListener implements ExtensionPoint {

    /**
     * Called when a {@link FlowExecution} has been created, but before it starts running.
     *
     * The {@link FlowExecution} will already have been added to the {@link FlowExecutionList} by this point.
     * Methods relating to in-progress execution state such as {@link FlowExecution#getCurrentHeads}
     * will not work as intended and should not be used.
     *
     * @param execution The {@link FlowExecution} that has been created.
     */
    public void onCreated(@NonNull FlowExecution execution) {
    }

    /**
     * Called when a {@link FlowExecution} has started running.
     *
     * The {@link FlowExecution} will already have been added to the {@link FlowExecutionList} by this point.
     *
     * @param execution The {@link FlowExecution} that has started running.
     */
    public void onRunning(@NonNull FlowExecution execution) {
    }

    /**
     * Called when a {@link FlowExecution} has resumed.
     *
     * @param execution The {@link FlowExecution} that has resumed.
     */
    public void onResumed(@NonNull FlowExecution execution) {
    }

    /**
     * Called when a {@link FlowExecution} has completed.
     *
     * The {@link FlowExecution} will already have been removed from the {@link FlowExecutionList} by this point,
     * {@link GraphListener.Synchronous#onNewHead(FlowNode)} will have already been called for the {@link FlowEndNode},
     * {@link FlowExecution#getCurrentHeads()} will have one element, a {@link FlowEndNode}, and if the Pipeline has
     * failed, {@link FlowExecution#getCauseOfFailure()} will return non-null.
     *
     * @param execution The {@link FlowExecution} that has completed.
     */
    public void onCompleted(@NonNull FlowExecution execution) {
    }

    /**
     * Fires the {@link #onCreated(FlowExecution)} event.
     */
    public static void fireCreated(@NonNull FlowExecution execution) {
        for (FlowExecutionListener listener : ExtensionList.lookup(FlowExecutionListener.class)) {
            listener.onCreated(execution);
        }
    }

    /**
     * Fires the {@link #onRunning(FlowExecution)} event.
     */
    public static void fireRunning(@NonNull FlowExecution execution) {
        for (FlowExecutionListener listener : ExtensionList.lookup(FlowExecutionListener.class)) {
            listener.onRunning(execution);
        }
    }

    /**
     * Fires the {@link #onResumed(FlowExecution)} event.
     */
    public static void fireResumed(@NonNull FlowExecution execution) {
        for (FlowExecutionListener listener : ExtensionList.lookup(FlowExecutionListener.class)) {
            listener.onResumed(execution);
        }
    }

    /**
     * Fires the {@link #onCompleted(FlowExecution)} event.
     */
    public static void fireCompleted(@NonNull FlowExecution execution) {
        for (FlowExecutionListener listener : ExtensionList.lookup(FlowExecutionListener.class)) {
            listener.onCompleted(execution);
        }
    }
}

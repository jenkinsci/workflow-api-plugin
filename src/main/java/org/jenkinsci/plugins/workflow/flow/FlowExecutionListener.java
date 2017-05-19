package org.jenkinsci.plugins.workflow.flow;

import hudson.ExtensionList;
import hudson.ExtensionPoint;

/**
 * Listens for significant status updates for a {@link FlowExecution}, such as started running or completed.
 *
 * @since 2.14
 * @author Andrew Bayer
 */
public abstract class FlowExecutionListener implements ExtensionPoint {

    /**
     * Called when a {@link FlowExecution} has started running or resumed.
     *
     * @param execution The {@link FlowExecution} that has started running or resumed.
     * @param resumed True the execution is resuming, false if it's starting for the first time.
     */
    public void onRunning(FlowExecution execution, boolean resumed) {
    }

    /**
     * Called when a {@link FlowExecution} has completed.
     *
     * @param execution The {@link FlowExecution} that has completed.
     */
    public void onCompleted(FlowExecution execution) {
    }

    /**
     * Fires the {@link #onRunning(FlowExecution, boolean)} event.
     */
    public static void fireRunning(FlowExecution execution, boolean resumed) {
        for (FlowExecutionListener listener : ExtensionList.lookup(FlowExecutionListener.class)) {
            listener.onRunning(execution, resumed);
        }
    }

    /**
     * Fires the {@link #onCompleted(FlowExecution)} event.
     */
    public static void fireCompleted(FlowExecution execution) {
        for (FlowExecutionListener listener : ExtensionList.lookup(FlowExecutionListener.class)) {
            listener.onCompleted(execution);
        }
    }
}

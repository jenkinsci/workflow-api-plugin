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
     */
    public void onRunning(FlowExecution execution) {
    }

    /**
     * Called when a {@link FlowExecution} has completed.
     *
     * @param execution The {@link FlowExecution} that has completed.
     */
    public void onCompleted(FlowExecution execution) {
    }

}

package org.jenkinsci.plugins.workflow.graphanalysis;

import org.jenkinsci.plugins.workflow.actions.LabelAction;
import org.jenkinsci.plugins.workflow.actions.ThreadNameAction;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

/**
 * Finds stages (legacy or block-scoped)
 * @author <samvanoort@gmail.com>Sam Van Oort</samvanoort@gmail.com>
 */
public class StageChunkFinder extends LabelledChunkFinder {
    @Override
    public boolean isChunkStart(@Nonnull FlowNode current, @CheckForNull FlowNode previous) {
        LabelAction la = current.getAction(LabelAction.class);
        return la != null && !(la instanceof ThreadNameAction);  // Filters out parallels
    }

}

package org.jenkinsci.plugins.workflow.graphanalysis;

import org.jenkinsci.plugins.workflow.actions.LabelAction;
import org.jenkinsci.plugins.workflow.graph.BlockEndNode;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

/**
 * Splits a flow execution into {@link FlowChunk}s whenever you have a label.
 * This works for labelled blocks or single-step labels.
 *
 * Useful for collecting stages and parallel branches.
 * @author Sam Van Oort
 */
public class LabelledChunkFinder implements ChunkFinder {

    public boolean isStartInsideChunk() {
        return true;
    }

    /** Start is anywhere with a {@link LabelAction} */
    @Override
    public boolean isChunkStart(@Nonnull FlowNode current, @CheckForNull FlowNode previous) {
        LabelAction la = current.getDirectAction(LabelAction.class);
        return la != null;
    }

    /** End is where the previous node is a chunk start
     * or this is a {@link BlockEndNode} whose {@link BlockStartNode} has a label action */
    @Override
    public boolean isChunkEnd(@Nonnull FlowNode current, @CheckForNull FlowNode previous) {
        if (previous == null) {
            return false;
        }
        if (current instanceof BlockEndNode) {
            BlockStartNode bsn = ((BlockEndNode) current).getStartNode();
            if (isChunkStart(bsn, null)) {
                return true;
            }
        }
        return isChunkStart(previous, null);
    }
}

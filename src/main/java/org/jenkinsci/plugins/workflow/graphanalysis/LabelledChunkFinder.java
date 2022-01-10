package org.jenkinsci.plugins.workflow.graphanalysis;

import org.jenkinsci.plugins.workflow.actions.LabelAction;
import org.jenkinsci.plugins.workflow.graph.BlockEndNode;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Splits a flow execution into {@link FlowChunk}s whenever you have a label.
 * This works for labelled blocks or single-step labels.
 *
 * Useful for collecting stages and parallel branches.
 * @author Sam Van Oort
 */
public class LabelledChunkFinder implements ChunkFinder {

    @Override
    public boolean isStartInsideChunk() {
        return true;
    }

    /** Start is anywhere with a {@link LabelAction} */
    @Override
    public boolean isChunkStart(@NonNull FlowNode current, @CheckForNull FlowNode previous) {
        LabelAction la = current.getPersistentAction(LabelAction.class);
        return la != null;
    }

    /** End is where the previous node is a chunk start
     * or this is a {@link BlockEndNode} whose {@link BlockStartNode} has a label action */
    @Override
    public boolean isChunkEnd(@NonNull FlowNode current, @CheckForNull FlowNode previous) {
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

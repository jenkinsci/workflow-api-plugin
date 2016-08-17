package org.jenkinsci.plugins.workflow.graphanalysis;

import org.jenkinsci.plugins.workflow.actions.LabelAction;
import org.jenkinsci.plugins.workflow.graph.BlockEndNode;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

/**
 * Basically finds stages. *Technically* it's any block of nodes.
 * Creates chunks whenever you have a labelled linear block (not a parallel branch).
 * @author Sam Van Oort
 */
public class LabelledChunkFinder implements ChunkFinder {

    public boolean isStartInsideChunk() {
        return true;
    }

    @Override
    public boolean isChunkStart(@Nonnull FlowNode current, @CheckForNull FlowNode previous) {
        LabelAction la = current.getAction(LabelAction.class);
        return la != null;
    }

    /** End is where you have a label marker before it... or  */
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

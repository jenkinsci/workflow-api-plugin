package org.jenkinsci.plugins.workflow.graphanalysis;

import org.jenkinsci.plugins.workflow.graph.BlockEndNode;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

/**
 * Matches start and end of a block.  Any block
 * @author Sam Van Oort
 */
public class BlockChunkFinder implements ChunkFinder {

    @Override
    public boolean isStartInsideChunk() {
        return false;
    }

    @Override
    public boolean isChunkStart(@Nonnull FlowNode current, @CheckForNull FlowNode previous) {
        return current instanceof BlockStartNode;
    }

    @Override
    public boolean isChunkEnd(@Nonnull FlowNode current, @CheckForNull FlowNode previous) {
        return current instanceof BlockEndNode;
    }
}

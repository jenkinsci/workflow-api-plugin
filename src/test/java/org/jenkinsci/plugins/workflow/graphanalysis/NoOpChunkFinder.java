package org.jenkinsci.plugins.workflow.graphanalysis;

import org.jenkinsci.plugins.workflow.graph.FlowNode;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * For test use: a ChunkFinder that never returns chunks, to use in testing parallel handling only.
 * All {@link FlowNode}s will this result in calling {@link SimpleChunkVisitor#atomNode(FlowNode, FlowNode, FlowNode, ForkScanner)}
 */
public class NoOpChunkFinder implements ChunkFinder {

    @Override
    public boolean isStartInsideChunk() {
        return false;
    }

    @Override
    public boolean isChunkStart(@NonNull FlowNode current, @CheckForNull FlowNode previous) {
        return false;
    }

    @Override
    public boolean isChunkEnd(@NonNull FlowNode current, @CheckForNull FlowNode previous) {
        return false;
    }
}

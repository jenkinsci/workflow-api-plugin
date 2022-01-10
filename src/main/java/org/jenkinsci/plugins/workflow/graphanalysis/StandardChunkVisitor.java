package org.jenkinsci.plugins.workflow.graphanalysis;

import org.jenkinsci.plugins.workflow.graph.FlowNode;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Simple handler for linear {@link FlowChunk}s (basic stages, etc), and designed to be extended.
 * Note: only tracks one chunk at a time, so it won't handle nesting or parallels.
 * Specifically, it will reset with each chunk start.
 * Extend {@link #handleChunkDone(MemoryFlowChunk)}  to gather up final chunks.
 * Extend {@link #atomNode(FlowNode, FlowNode, FlowNode, ForkScanner)} to gather data about nodes in a chunk.
 * @author Sam Van Oort
 */
public class StandardChunkVisitor implements SimpleChunkVisitor {

    protected MemoryFlowChunk chunk = new MemoryFlowChunk();


    /** Override me to do something once the chunk is finished (such as add it to a list).
     *  Note: the chunk will be mutated directly, so you need to copy it if you want to do something.
     */
    protected void handleChunkDone(@NonNull MemoryFlowChunk chunk) {
        // NO-OP initially
    }

    protected void resetChunk(@NonNull MemoryFlowChunk chunk) {
        chunk.setFirstNode(null);
        chunk.setLastNode(null);
        chunk.setNodeBefore(null);
        chunk.setNodeAfter(null);
        chunk.setPauseTimeMillis(0);
    }

    @Override
    public void chunkStart(@NonNull FlowNode startNode, @CheckForNull FlowNode beforeBlock, @NonNull ForkScanner scanner) {
        chunk.setNodeBefore(beforeBlock);
        chunk.setFirstNode(startNode);
        handleChunkDone(chunk);
        resetChunk(chunk);
    }

    @Override
    public void chunkEnd(@NonNull FlowNode endNode, @CheckForNull FlowNode afterChunk, @NonNull ForkScanner scanner) {
        chunk.setLastNode(endNode);
        chunk.setNodeAfter(afterChunk);
    }

    @Override
    public void parallelStart(@NonNull FlowNode parallelStartNode, @NonNull FlowNode branchNode, @NonNull ForkScanner scanner) {}

    @Override
    public void parallelEnd(@NonNull FlowNode parallelStartNode, @NonNull FlowNode parallelEndNode, @NonNull ForkScanner scanner) {}

    @Override
    public void parallelBranchStart(@NonNull FlowNode parallelStartNode, @NonNull FlowNode branchStartNode, @NonNull ForkScanner scanner) {}

    @Override
    public void parallelBranchEnd(@NonNull FlowNode parallelStartNode, @NonNull FlowNode branchEndNode, @NonNull ForkScanner scanner) {}

    /** Extend me to do something with nodes inside a chunk */
    @Override
    public void atomNode(@CheckForNull FlowNode before, @NonNull FlowNode atomNode, @CheckForNull FlowNode after, @NonNull ForkScanner scan) {}
}

package org.jenkinsci.plugins.workflow.graphanalysis;

import org.jenkinsci.plugins.workflow.graph.FlowNode;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

/**
 * Simple handler for linear chunks (basic stages, etc), designed to be extended
 * Note: does not handle parallels or nesting
 * Extend {@link #handleChunkDone(MemoryFlowChunk)}  to gather up final chunks
 * Extend {@link #atomNode(FlowNode, FlowNode, FlowNode, ForkScanner)} to gather data about nodes in a chunk
 * @author <samvanoort@gmail.com>Sam Van Oort</samvanoort@gmail.com>
 */
public class StandardChunkVisitor implements SimpleChunkVisitor {

    protected MemoryFlowChunk chunk = new MemoryFlowChunk();


    /** Override me to do something once the chunk is finished
     *  Note: the chunk will be mutated directly, so you need to copy it if you want to do something
     */
    protected void handleChunkDone(@Nonnull MemoryFlowChunk chunk) {
        // NO-OP initially
    }

    protected void resetChunk(@Nonnull MemoryFlowChunk chunk) {
        chunk.setFirstNode(null);
        chunk.setLastNode(null);
        chunk.setNodeBefore(null);
        chunk.setNodeAfter(null);
        chunk.setPauseTimeMillis(0);
    }

    @Override
    public void chunkStart(@Nonnull FlowNode startNode, @CheckForNull FlowNode beforeBlock, @Nonnull ForkScanner scanner) {
        chunk.setNodeBefore(beforeBlock);
        chunk.setFirstNode(startNode);
        handleChunkDone(chunk);
        resetChunk(chunk);
    }

    @Override
    public void chunkEnd(@Nonnull FlowNode endNode, @CheckForNull FlowNode afterChunk, @Nonnull ForkScanner scanner) {
        chunk.setLastNode(endNode);
        chunk.setNodeAfter(afterChunk);
    }

    @Override
    public void parallelStart(@Nonnull FlowNode parallelStartNode, @Nonnull FlowNode branchNode, @Nonnull ForkScanner scanner) {}

    @Override
    public void parallelEnd(@Nonnull FlowNode parallelStartNode, @Nonnull FlowNode parallelEndNode, @Nonnull ForkScanner scanner) {}

    @Override
    public void parallelBranchStart(@Nonnull FlowNode parallelStartNode, @Nonnull FlowNode branchStartNode, @Nonnull ForkScanner scanner) {}

    @Override
    public void parallelBranchEnd(@Nonnull FlowNode parallelStartNode, @Nonnull FlowNode branchEndNode, @Nonnull ForkScanner scanner) {}

    /** Extend me to do something with nodes inside a chunk */
    @Override
    public void atomNode(@CheckForNull FlowNode before, @Nonnull FlowNode atomNode, @CheckForNull FlowNode after, @Nonnull ForkScanner scan) {}
}

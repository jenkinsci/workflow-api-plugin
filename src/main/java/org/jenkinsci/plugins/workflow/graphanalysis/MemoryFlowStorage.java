package org.jenkinsci.plugins.workflow.graphanalysis;

import org.jenkinsci.plugins.workflow.graph.FlowNode;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.ArrayDeque;

/**
 * Memory-based flow chunk storage for constructing the tree
 */
public class MemoryFlowStorage implements FlowChunkStorage<MemoryFlowChunk> {

    ArrayDeque<MemoryFlowChunk> scopes = new ArrayDeque<MemoryFlowChunk>();

    @Nonnull
    @Override
    public MemoryFlowChunk createBase() {
        return new MemoryFlowChunk();
    }

    @Nonnull
    @Override
    public MemoryFlowChunk createBlockChunk(@Nonnull FlowNode blockStart, @CheckForNull FlowNode blockEnd) {
        MemoryFlowChunk output = new MemoryFlowChunk();
        output.setFirstNode(blockStart);
        output.setLastNode(blockEnd);
        return output;
    }

    @Nonnull
    @Override
    public MemoryFlowChunk setStatus(@Nonnull MemoryFlowChunk chunk, boolean isExecuted, boolean isErrored, boolean isComplete) {
        return chunk; // NOOP for now
    }

    @Nonnull
    @Override
    public MemoryFlowChunk createParallelChunk() {
        return new ParallelMemoryFlowChunk();
    }

    @Nonnull
    @Override
    public MemoryFlowChunk addBranchToParallel(@Nonnull MemoryFlowChunk parallelContainer, String branchName, MemoryFlowChunk branch) {
        if (! (parallelContainer instanceof ParallelMemoryFlowChunk)) {
            throw new IllegalArgumentException("Can't add a parallel branch to a container that is not a ParallelMemoryFlowStorage");
        }
        ((ParallelMemoryFlowChunk)parallelContainer).setBranch(branchName, branch);
        return parallelContainer;
    }

    @Nonnull
    @Override
    public MemoryFlowChunk finalizeChunk(@Nonnull MemoryFlowChunk chunk) {
        return chunk;
    }


    @Nonnull
    @Override
    public MemoryFlowChunk configureChunk(@Nonnull MemoryFlowChunk chunk, @Nonnull FlowNode firstNode, @Nonnull FlowNode lastNode) {
        chunk.setFirstNode(firstNode);
        chunk.setLastNode(lastNode);
        return chunk;
    }

    @Nonnull
    @Override
    public MemoryFlowChunk addAtomNode(@Nonnull MemoryFlowChunk container, @Nonnull FlowNode atomNode) {
        return container;
    }

    @Nonnull
    @Override
    public MemoryFlowChunk addBlockInside(@Nonnull MemoryFlowChunk container, @Nonnull MemoryFlowChunk content) {
        if (scopes.peek() == container) {
            scopes.push(content);
        }
        return container;
    }

    @Nonnull
    @Override
    public MemoryFlowChunk setTiming(MemoryFlowChunk base, long startTimeMillis, long endTimeMillis, long durationMillis, long pauseDurationMillis) {
        base.setStartTimeMillis(startTimeMillis);
        base.setEndTimeMillis(endTimeMillis);
        base.setDurationMillis(durationMillis);
        base.setPauseDurationMillis(pauseDurationMillis);
        return base;
    }
}

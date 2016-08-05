package org.jenkinsci.plugins.workflow.graphanalysis;

import javax.annotation.Nonnull;
import java.util.Map;

/**
 * FlowChunk that has parallel branches
 */
public interface ParallelFlowChunk <ChunkType extends FlowChunk> extends FlowChunk  {

    /** Returns the branches of a parallel flow chunk, mapped by branch name and parallel branch block */
    @Nonnull
    public Map<String, ChunkType> getBranches();

    @Nonnull
    public void setBranch(@Nonnull String branchName, @Nonnull ChunkType branchBlock);
}

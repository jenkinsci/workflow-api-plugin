package org.jenkinsci.plugins.workflow.graphanalysis;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Map;

/**
 * FlowChunk mapping to the block from a Parallel step (with parallel branches inside)
 */
public interface ParallelFlowChunk <ChunkType extends FlowChunk> extends FlowChunk  {

    /** Returns the branches of a parallel flow chunk, mapped by branch name and parallel branch block */
    @NonNull
    Map<String, ChunkType> getBranches();

    void setBranch(@NonNull String branchName, @NonNull ChunkType branchBlock);
}

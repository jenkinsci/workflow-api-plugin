package org.jenkinsci.plugins.workflow.graphanalysis;

import javax.annotation.Nonnull;
import java.util.Map;

/**
 * Flowchunk that has parallel branches
 */
public interface ParallelFlowChunk extends FlowChunk {

    /** Returns the branches of a parallel flow chunk, mapped by branch name and parallel branch block */
    @Nonnull
    public Map<String, ? extends FlowChunk> getBranches();

    @Nonnull
    public void setBranch(@Nonnull String branchName, @Nonnull MemoryFlowChunk branchBlock);
}

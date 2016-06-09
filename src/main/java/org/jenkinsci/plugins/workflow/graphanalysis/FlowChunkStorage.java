package org.jenkinsci.plugins.workflow.graphanalysis;

import org.jenkinsci.plugins.workflow.graph.FlowNode;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

/**
 * Storage API used to render/store results from a run analysis
 * Example: container classes to return from REST APIs
 * Think of it as a factory or state storage for results, but using a fluent API
 */
public interface FlowChunkStorage <CHUNKBASETYPE extends FlowChunk> {
    /** Returns the container */
    @Nonnull
    public CHUNKBASETYPE createBase();

    /** Creates a block, given a start node (and possibly end node) */
    @Nonnull
    public CHUNKBASETYPE createBlockChunk(@Nonnull FlowNode blockStart, @CheckForNull FlowNode blockEnd);

    // TODO parallel and arbitrary run blocks

    /** Complete analysis of chunk and return it (chunk may be contained in other things but never modified further) */
    @Nonnull
    public CHUNKBASETYPE finalizeChunk(@Nonnull CHUNKBASETYPE chunk);

    @Nonnull
    public CHUNKBASETYPE configureChunk(@Nonnull FlowNode firstNode, @Nonnull FlowNode lastNode);


    /** Returns the container */
    @Nonnull
    public CHUNKBASETYPE addAtomNode(@Nonnull CHUNKBASETYPE container, @Nonnull FlowNode atomNode);

    /** Returns the container */
    @Nonnull
    public CHUNKBASETYPE addBlockInside(@Nonnull CHUNKBASETYPE container, @Nonnull CHUNKBASETYPE content);

    /** Returns the container */
    @Nonnull
    public CHUNKBASETYPE setTiming(CHUNKBASETYPE base, long startTimeMillis, long endTimeMillis, long durationMillis, long pauseDurationMillis);

}

package org.jenkinsci.plugins.workflow.graphanalysis;

import org.jenkinsci.plugins.workflow.graph.FlowNode;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

/**
 * Storage API used to render/store results from a run analysis
 * Example: container classes to return from REST APIs
 * Think of it as a factory or state storage for results, but using a fluent API
 *
 * This is used to build whatever Directed Acyclic Graph output you use (an API response object)
 * This creates container types for your final output, and then callbacks are made to them
 *
 * Couples tightly to {@link AdvancedVisitor} which issues all the calls to this, to contribute information.
 */
public interface FlowChunkStorage <CHUNKBASETYPE extends FlowChunk> {
    /** Returns a basic container for arbitrary nodes */
    @Nonnull
    public CHUNKBASETYPE createBase();

    /** Creates a block, given a start node (and possibly end node) */
    @Nonnull
    public CHUNKBASETYPE createBlockChunk(@Nonnull FlowNode blockStart, @CheckForNull FlowNode blockEnd);

    /** Convert a series of status flags to a final output result */
    @Nonnull
    public CHUNKBASETYPE setStatus(@Nonnull CHUNKBASETYPE chunk, boolean isExecuted, boolean isErrored, boolean isComplete);

    /** Create a new parallel chunk container type */
    @Nonnull
    public CHUNKBASETYPE createParallelChunk();  // Return type must implement ParallelFlowChunk

    /** Yup, add in our parallel branch */
    @Nonnull
    public CHUNKBASETYPE addBranchToParallel(@Nonnull CHUNKBASETYPE parallelContainer, String branchName, CHUNKBASETYPE branch);

    /** Complete analysis of chunk and return it (chunk may be contained in other things but never modified further)
     *  May be a no-op in many cases
     */
    @Nonnull
    public CHUNKBASETYPE finalizeChunk(@Nonnull CHUNKBASETYPE chunk);

    @Nonnull
    public CHUNKBASETYPE configureChunk(@Nonnull CHUNKBASETYPE chunk, @Nonnull FlowNode firstNode, @Nonnull FlowNode lastNode);

    /** Using internal representation, *POSSIBLE* store a node to the given chunk.
     *  The  FlowChunkStorage may decide whether or not it cares about the FlowNode,
     *     and whether or not chunks should store nodes.
     *  For example, some chunks may store an internal list of nodes.
     */
    @Nonnull
    public CHUNKBASETYPE addAtomNode(@Nonnull CHUNKBASETYPE container, @Nonnull FlowNode atomNode);

    // Some sort of adder for flownode that shows the context for timing?

    /** Returns the container */
    @Nonnull
    public CHUNKBASETYPE addBlockInside(@Nonnull CHUNKBASETYPE container, @Nonnull CHUNKBASETYPE content);


    /** Configure timing for the given chunk */
    @Nonnull
    public CHUNKBASETYPE setTiming(CHUNKBASETYPE base, long startTimeMillis, long endTimeMillis, long durationMillis, long pauseDurationMillis);

}

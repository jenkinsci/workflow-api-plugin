package org.jenkinsci.plugins.workflow.graphanalysis;

import org.jenkinsci.plugins.workflow.graph.FlowNode;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

/**
 * Think of this as setting conditions to mark a region of interest in the graph of {@link FlowNode} from a {@link org.jenkinsci.plugins.workflow.flow.FlowExecution}.
 * <p>This is used to define a linear "chunk" from the graph of FlowNodes returned by a {@link ForkScanner}, after it applies ordering.
 * <p>This is done by invoking {@link ForkScanner#visitSimpleChunks(SimpleChunkVisitor, ChunkFinder)}.
 * <p>Your {@link SimpleChunkVisitor} will receive callbacks about chunk boundaries on the basis of the ChunkFinder.
 *   It isresponsible for tracking the state based on events fired
 *
 * <p><p><em>Common uses:</em>
 * <ul>
 *     <li>Find all nodes within a specific block type, such the block created by a timeout block, 'node' (executor) block, etc</li>
 *     <li>Find all nodes between specific markers, such as labels, milestones, or steps generating an error</li>
 * </ul>
 *
 * <p><em>Implementation Notes:</em>
 * <ul>
 *     <li>This can be used to detect both block-delimited regions of interest and marker-based regions</li>
 *     <li>Block-delimited regions should END when encountering the right kind of {@link org.jenkinsci.plugins.workflow.graph.BlockEndNode}
 *         and start when seeing the right kind of {@link org.jenkinsci.plugins.workflow.graph.BlockStartNode}</li>
 *     <li>Marker-based regions should start when you find the marker, and END when the previous node is a marker</li>
 *     <li>If you need to handle both for the same set of criteria... good grief. See the StageChunkFinder in the pipeline-graph-analysis plugin.</li>
 * </ul>
 *
 * @author Sam Van Oort
 */
public interface ChunkFinder {

    /** If true, a chunk is implicitly created whenever we begin.
     *  <p>If you are matching the start/end of a block, should always return false.
     *  <p>If you are trying to match markers (such as single-node labels or milestones), should always be true. */
    boolean isStartInsideChunk();

    /**
     * Test if the current node is the start of a new chunk (inclusive)
     * @param current Node to test for being a start, it will begin the chunk and be included
     * @param previous Previous node, to use in testing chunk
     * @return True if current node is the beginning of chunk
     */
    boolean isChunkStart(@Nonnull FlowNode current, @CheckForNull FlowNode previous);

    /**
     * Test if the current node is the end of a chunk (inclusive)
     * @param current Node to test for being end
     *    <p> For a block, the {@link org.jenkinsci.plugins.workflow.graph.BlockEndNode}
     *    <p> For a legacy stage or marker, this will be first node of new stage (previous is the marker)
     * @param previous Previous node, to use in testing chunk
     * @return True if current is the end of a chunk (inclusive)
     */
    boolean isChunkEnd(@Nonnull FlowNode current, @CheckForNull FlowNode previous);
}

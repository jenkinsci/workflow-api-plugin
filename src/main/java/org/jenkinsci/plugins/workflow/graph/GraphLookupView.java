package org.jenkinsci.plugins.workflow.graph;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Interface that can be exposed by objects providing means to easily look up information about the structure of a pipeline run
 * Usually this is scoped to a specific {@link org.jenkinsci.plugins.workflow.flow.FlowExecution}.
 *
 * Exists because we do not want to ourselves to only using the standard implementation in {@link StandardGraphLookupView}.
 *
 * <strong>Implementation note:</strong>
 * <p>Normally this should only be used internally to power APIs, but if exposed publicly remember that {@link FlowNode}s
 *  from different executions may be given as inputs.  There needs to be a way to handle that.
 *  Either throw {@link IllegalArgumentException}s if tied to a single {@link org.jenkinsci.plugins.workflow.flow.FlowExecution}
 *  or {@link org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner} or use the ID of the execution as a key to delegate to different cache objects.
 *
 */
public interface GraphLookupView {
    /** Tests if the node is a currently running head, or the start of a block that has not completed executing */
    boolean isActive(@Nonnull FlowNode node);

    /** Find the end node corresponding to a start node, and can be used to tell if the block is completed.
     *  @return {@link BlockEndNode} matching the given start node, or null if block hasn't completed
     */
    @CheckForNull
    BlockEndNode getEndNode(@Nonnull BlockStartNode startNode);

    /**
     * Find the immediately enclosing {@link BlockStartNode} around a {@link FlowNode}
     * @param node Node to find block enclosing it - note that it this is a BlockStartNode, you will return the start of the block enclosing this one.
     * @return Null if node is a {@link FlowStartNode} or {@link FlowEndNode}
     */
    @CheckForNull
    BlockStartNode findEnclosingBlockStart(@Nonnull FlowNode node);

    /**
     * Provide an {@link Iterable} over all enclosing blocks, which can be used similarly to {@link #findAllEnclosingBlockStarts(FlowNode)} but
     *  does lazy fetches rather than materializing a full result.  Handy for for-each loops.
     * <p><strong>Usage note:</strong>Prefer this to {@link #findAllEnclosingBlockStarts(FlowNode)} in most cases since it can evaluate lazily, unless you know
     *  you need all enclosing blocks.
     * @param node Node to find enclosing blocks for
     * @return Iterable over enclosing blocks, from the nearest-enclosing outward ("inside-out" order)
     */
    Iterable<BlockStartNode> iterateEnclosingBlocks(@Nonnull FlowNode node);

    /** Return all enclosing block start nodes, as with {@link #findEnclosingBlockStart(FlowNode)}.
     *  <p><strong>Usage note:</strong>Prefer using {@link #iterateEnclosingBlocks(FlowNode)} unless you know you need ALL blocks, since that can lazy-load.
     *  @param node Node to find enclosing blocks for
     *  @return All enclosing block starts from the nearest-enclosing outward ("inside-out" order), or EMPTY_LIST if this is a start or end node
     */
    @Nonnull
    List<BlockStartNode> findAllEnclosingBlockStarts(@Nonnull FlowNode node);

    /** Provides a trivial implementation to facilitate implementing {@link #iterateEnclosingBlocks(FlowNode)}*/
    class EnclosingBlocksIterable implements Iterable<BlockStartNode> {
        FlowNode node;
        GraphLookupView view;

        class EnclosingBlocksIterator implements Iterator<BlockStartNode> {

            EnclosingBlocksIterator(FlowNode start) {
                next = view.findEnclosingBlockStart(start);
            }

            BlockStartNode next;

            @Override
            public boolean hasNext() {
                return next != null;
            }

            @Override
            public BlockStartNode next() {
                if (hasNext()) {
                    BlockStartNode retVal = next;
                    next = view.findEnclosingBlockStart(next);
                    return retVal;
                } else {
                    throw new NoSuchElementException("No more block start nodes");
                }
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException("You cannot remove FlowNodes, once written they are immutable!");
            }
        }

        public EnclosingBlocksIterable(@Nonnull GraphLookupView view, @Nonnull FlowNode node) {
            this.view = view;
            this.node = node;
        }

        @Override
        public Iterator<BlockStartNode> iterator() {
            return new EnclosingBlocksIterator(node);
        }
    }
}

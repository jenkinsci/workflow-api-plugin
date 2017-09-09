package org.jenkinsci.plugins.workflow.graph;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.List;

/**
 * Interface that can be exposed by objects providing means to easily look up information about the structure of a pipeline run
 * Usually this is scoped to a specific {@link org.jenkinsci.plugins.workflow.flow.FlowExecution}.
 *
 * <strong>Implementation note:</strong>
 * <p>Normally this should only be used internally to power APIs, but if exposed publicly remember that {@link FlowNode}s
 *  from different executions may be given as inputs.  There needs to be a way to handle that.
 *  Either throw IllegalArgumentExceptions if tied to a single {@link org.jenkinsci.plugins.workflow.flow.FlowExecution},
 *  or use the ID of the execution as a key to delegate to different cache objects.
 *
 */
public interface GraphLookupView {
    /** Tests if the node is a currently running head, or the start of a block that has not completed executing */
    public boolean isActive(@Nonnull  FlowNode node);

    /** Find the end node corresponding to a start node
     *  @return {@link BlockEndNode} matching the given start node, or null if block hasn't completed
     */
    @CheckForNull
    public BlockEndNode getEndNode(@Nonnull BlockStartNode startNode);

    /**
     * Find the immediately enclosing {@link BlockStartNode} around a {@link FlowNode}
     * @param node Node to find block enclosing it - note that it this is a BlockStartNode, you will return the start of the block enclosing this one.
     * @return Null if node is a {@link FlowStartNode} or {@link FlowEndNode}
     */
    @CheckForNull
    public BlockStartNode findEnclosingBlockStart(@Nonnull FlowNode node);

    /** Return all enclosing block start nodes, as with {@link #findEnclosingBlockStart(FlowNode)}.
     *  @param node Node to find enclosing blocks for
     *  @return All enclosing block starts in no particular sort order, or EMPTY_LIST if this is a start or end node
     */
    @Nonnull
    public List<BlockStartNode> findAllEnclosingBlockStarts(@Nonnull FlowNode node);
}

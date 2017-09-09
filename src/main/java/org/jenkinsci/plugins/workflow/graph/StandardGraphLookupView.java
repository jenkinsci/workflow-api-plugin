package org.jenkinsci.plugins.workflow.graph;

import com.sun.tools.javac.comp.Flow;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.flow.GraphListener;
import org.jenkinsci.plugins.workflow.graphanalysis.DepthFirstScanner;
import org.jenkinsci.plugins.workflow.graphanalysis.Filterator;
import org.jenkinsci.plugins.workflow.graphanalysis.FlowNodeVisitor;
import org.jenkinsci.plugins.workflow.graphanalysis.FlowScanningUtils;
import org.jenkinsci.plugins.workflow.graphanalysis.ForkScanner;
import org.jenkinsci.plugins.workflow.graphanalysis.LinearBlockHoppingScanner;
import org.jenkinsci.plugins.workflow.graphanalysis.LinearScanner;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * Provides overall insight into the structure of a flow graph... but with limited visibility so we can change implementation.
 * Designed to work entirely on the basis of the {@link FlowNode#id} rather than the {@link FlowNode}s themselves.
 */
@SuppressFBWarnings(value = "ES_COMPARING_STRINGS_WITH_EQ", justification = "Can can use instance identity when comparing to a final constant")
final class StandardGraphLookupView implements GraphLookupView, GraphListener, GraphListener.Synchronous {

    static final String INCOMPLETE = "";

    /** Map the blockStartNode to its endNode, to accellerate a range of operations */
    HashMap<String, String> blockStartToEnd = new HashMap<String, String>();

    /** Map a node to its nearest enclosing block */
    HashMap<String, String> nearestEnclosingBlock = new HashMap<String, String>();

    /** Update with a new node added to the flowgraph */
    public void onNewHead(@Nonnull FlowNode newHead) {
        if (newHead instanceof BlockEndNode) {
            blockStartToEnd.put(((BlockEndNode)newHead).getStartNode().getId(), newHead.getId());
        } else if (newHead instanceof BlockStartNode) {
            blockStartToEnd.put(newHead.getId(), INCOMPLETE);
        } else { // AtomNode

        }
    }

    /** Create a lookup view for an execution */
    public StandardGraphLookupView(FlowExecution execution) {
        execution.addListener(this);
    }

    @Override
    public boolean isActive(@Nonnull FlowNode node) {
        if (node instanceof FlowEndNode) { // cf. JENKINS-26139
            return node.getExecution().isComplete();
        } else if (node instanceof BlockStartNode){  // BlockStartNode
            return this.getEndNode((BlockStartNode)node) == null;
        } else {
            return node.getExecution().isCurrentHead(node);
        }
    }

    // Do a brute-force scan for the block end matching the start, caching info along the way for future use
    BlockEndNode bruteForceScanForEnd(@Nonnull BlockStartNode start) {
        DepthFirstScanner scan = new DepthFirstScanner();
        scan.setup(start.getExecution().getCurrentHeads());
        for (FlowNode f : scan) {
            if (f instanceof BlockEndNode) {
                BlockEndNode end = (BlockEndNode)f;
                BlockStartNode maybeStart = end.getStartNode();
                // Cache start in case we need to scan again in the future
                blockStartToEnd.put(maybeStart.getId(), end.getId());
                if (start.equals(maybeStart)) {
                    return end;
                }
            } else if (f instanceof BlockStartNode) {
                BlockStartNode maybeThis = (BlockStartNode) f;

                // We're walking from the end to the start and see the start without finding the end first, block is incomplete
                String previousEnd = blockStartToEnd.get(maybeThis.getId());
                if (previousEnd == null) {
                    blockStartToEnd.put(maybeThis.getId(), INCOMPLETE);
                }
                if (start.equals(maybeThis)) {  // Early exit, the end can't be encountered before the start
                    return null;
                }
            }
        }
        return null;
    }


    /** Do a brute-force scan for the enclosing blocks **/
    BlockStartNode bruteForceScanForEnclosingBlock(@Nonnull FlowNode node) {
        Filterator<FlowNode> enclosing = FlowScanningUtils.fetchEnclosingBlocks(node);
        return (enclosing.hasNext()) ? (BlockStartNode) enclosing.next() : null;
    }

    @CheckForNull
    @Override
    public BlockEndNode getEndNode(@Nonnull final BlockStartNode startNode) {

        String id = blockStartToEnd.get(startNode.getId());
        if (id != null) {
            try {
                return id == INCOMPLETE ? null : (BlockEndNode)(startNode.getExecution().getNode(id));
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
        } else {
            return bruteForceScanForEnd(startNode);
        }
    }

    /** Get all enclosed nodes in a block, roughly in order with some quirks for parallel branches */
    public List<FlowNode> getEnclosedNodes(BlockStartNode bsn) {
        ForkScanner scan = new ForkScanner();
        BlockEndNode end = getEndNode(bsn);
        Collection<FlowNode> ends = (end != null) ? (Collection<FlowNode>) Collections.singleton((FlowNode)end) : bsn.getExecution().getCurrentHeads();
        scan.setup(ends, Collections.singleton((FlowNode)bsn));

        ArrayList<FlowNode> nodes = new ArrayList<FlowNode>();
        if (end != null && scan.hasNext()) { // Skip the BlockEndNode, since it's inside
            scan.next();
        }
        for (FlowNode f : scan) {
            nodes.add(f);
        }
        Collections.reverse(nodes);
        return nodes;
    }

    @CheckForNull
    @Override
    public BlockStartNode findEnclosingBlockStart(@Nonnull FlowNode node) {
        if (node instanceof FlowStartNode || node instanceof FlowEndNode) {
            return null;
        }

        String id = nearestEnclosingBlock.get(node.getId());
        if (id != null) {
            try {
                return (BlockStartNode) (node.getExecution().getNode(id));
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
        }

        FlowNode enclosing = bruteForceScanForEnclosingBlock(node);
        if (enclosing != null) {
            nearestEnclosingBlock.put(node.getId(), enclosing.getId());
        }
        return null;
    }

    @Nonnull
    @Override
    public List<BlockStartNode> findAllEnclosingBlockStarts(@Nonnull FlowNode node) {
        if (node instanceof FlowStartNode || node instanceof FlowEndNode) {
            return Collections.emptyList();
        }
        ArrayList<BlockStartNode> starts = new ArrayList<BlockStartNode>(2);
        BlockStartNode currentlyEnclosing = findEnclosingBlockStart(node);
        while (currentlyEnclosing != null) {
            starts.add(currentlyEnclosing);
            currentlyEnclosing = findEnclosingBlockStart(currentlyEnclosing);
        }
        return starts;
    }
}

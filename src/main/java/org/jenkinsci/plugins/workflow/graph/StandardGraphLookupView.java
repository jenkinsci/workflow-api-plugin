package org.jenkinsci.plugins.workflow.graph;

import com.google.common.base.Predicate;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.flow.GraphListener;
import org.jenkinsci.plugins.workflow.graphanalysis.DepthFirstScanner;
import org.jenkinsci.plugins.workflow.graphanalysis.Filterator;
import org.jenkinsci.plugins.workflow.graphanalysis.FlowScanningUtils;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Provides overall insight into the structure of a flow graph... but with limited visibility so we can change implementation.
 * Designed to work entirely on the basis of the {@link FlowNode#id} rather than the {@link FlowNode}s themselves.
 */
final class StandardGraphLookupView implements GraphLookupView, GraphListener, GraphListener.Synchronous {

    /** Update with a new node added to the flowgraph */
    public void onNewHead(FlowNode newHead) {

    }

    /** Create a lookup view for an execution */
    public StandardGraphLookupView(FlowExecution execution) {
        execution.addListener(this);
    }

    @Override
    public boolean isActive(@Nonnull FlowNode node) {
        if (node instanceof FlowEndNode) { // cf. JENKINS-26139
            return false;
        } else if (node instanceof BlockStartNode){  // BlockStartNode
            return this.getEndNode((BlockStartNode)node) != null;
        } else {
            return node.getExecution().isCurrentHead(node);
        }
    }

    @CheckForNull
    @Override
    public BlockEndNode getEndNode(@Nonnull final BlockStartNode startNode) {
        if (startNode instanceof FlowStartNode) {
            return null;
        }
        FlowNode node = new DepthFirstScanner().findFirstMatch(startNode.getExecution(),
                new Predicate<FlowNode>() {
                    @Override
                    public boolean apply(@Nullable FlowNode node) {
                        return node instanceof BlockEndNode && startNode.equals (((BlockEndNode)node).getStartNode());
                    }
                }
        );
        return node instanceof BlockEndNode ? (BlockEndNode)node : null;
    }

    @CheckForNull
    @Override
    public BlockStartNode findEnclosingBlockStart(@Nonnull FlowNode node) {
        if (node instanceof FlowStartNode || node instanceof FlowEndNode) {
            return null;
        }

        Filterator<FlowNode> enclosing = FlowScanningUtils.fetchEnclosingBlocks(node);
        return (enclosing.hasNext()) ? (BlockStartNode) enclosing.next() : null;
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

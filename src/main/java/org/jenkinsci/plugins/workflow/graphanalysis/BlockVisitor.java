package org.jenkinsci.plugins.workflow.graphanalysis;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.jenkinsci.plugins.workflow.graph.BlockEndNode;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowEndNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.ArrayDeque;

/**
 * Visitor that stores a list of block scopes
 * This MUST be coupled with the ForkScanner to work correctly because of its iteration order
 *   This is because it guarantees block-scoped traversal, where every end occurs before a start
 * Created by svanoort on 5/12/16.
 */
@SuppressFBWarnings
public class BlockVisitor implements FlowNodeVisitor {

    protected ArrayDeque<IdFlowBlock> scopes = new ArrayDeque<IdFlowBlock>();
    protected IdFlowBlock currentBlock = new IdFlowBlock();

    public static interface FlowBlock {
        @CheckForNull
        public String getBlockStartNodeId();

        @CheckForNull
        public String getBlockEndNodeId();

        @CheckForNull
        public String getFirstChildId();

        @CheckForNull
        public String getLastChildId();
    }

    public class IdFlowBlock implements FlowBlock {
        private String blockStartNodeId;
        private String blockEndNodeId;
        private String firstChildId;
        private String lastChildId;

        public String getBlockStartNodeId() {
            return blockStartNodeId;
        }

        public void setBlockStartNodeId(String blockStartNodeId) {
            this.blockStartNodeId = blockStartNodeId;
        }

        public String getBlockEndNodeId() {
            return blockEndNodeId;
        }

        public void setBlockEndNodeId(String blockEndNodeId) {
            this.blockEndNodeId = blockEndNodeId;
        }

        public String getFirstChildId() {
            return firstChildId;
        }

        public void setFirstChildId(String firstChildId) {
            this.firstChildId = firstChildId;
        }

        public String getLastChildId() {
            return lastChildId;
        }

        public void setLastChildId(String lastChildId) {
            this.lastChildId = lastChildId;
        }
    }

    // Block is closed, we pop it off the scope and do what we want with it
    protected void popBlock() {
        this.currentBlock = this.scopes.pop();
    }

    /**
     * Enter a new block scope
     * @param block Block that starts then new scope
     */
    protected void pushBlock(@Nonnull IdFlowBlock block) {
        this.scopes.push(this.currentBlock);
        this.currentBlock = block;
    }

    protected void addBlockChild(@Nonnull FlowNode f) {
        if (currentBlock.getLastChildId() != null) {
            currentBlock.setLastChildId(f.getId());
        }
        currentBlock.setFirstChildId(f.getId());
    }

    /**
     * Visit the flow node, and indicate if we should continue analysis
     *
     * @param f Node to visit
     * @return False if we should stop visiting nodes
     */
    public boolean visit(@Nonnull FlowNode f) {
        if (f instanceof BlockEndNode) {
            IdFlowBlock innerBlock = new IdFlowBlock();
            innerBlock.setBlockEndNodeId(f.getId());
            innerBlock.setBlockStartNodeId(((BlockEndNode) f).getId());
            pushBlock(innerBlock);
        } else if (f instanceof BlockStartNode) {
            String currentStartId = currentBlock.getBlockStartNodeId();
            if (currentStartId != null && currentBlock.getBlockStartNodeId() != null
                    && (currentStartId.equals(f.getId())) ) {
                // We're done with this block's scope, move up one level
                popBlock();
            } else {
                // We're inside an unterminated block, add an empty block scope above it to contain it and pop off the current block
                IdFlowBlock block = new IdFlowBlock();
                currentBlock.setBlockStartNodeId(f.getId());
                scopes.offer(new IdFlowBlock());
                popBlock();
            }
        } else { // We're inside the current block
            addBlockChild(f);
        }
        return true;
    }
}

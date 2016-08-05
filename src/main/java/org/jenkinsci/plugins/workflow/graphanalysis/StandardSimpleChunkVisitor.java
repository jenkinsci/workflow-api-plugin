package org.jenkinsci.plugins.workflow.graphanalysis;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import org.jenkinsci.plugins.workflow.graph.BlockEndNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

/**
 * Created by @author <samvanoort@gmail.com>Sam Van Oort</samvanoort@gmail.com>
 */
public class StandardSimpleChunkVisitor implements SimpleChunkVisitor {

    private Predicate<FlowNode> chunkStartPredicate;
    private Predicate<FlowNode> chunkEndPredicate;

    @Override
    public boolean startInsideChunk() {return false;}

    @Nonnull
    @Override
    public Predicate<FlowNode> getChunkStartPredicate() {
        return chunkStartPredicate;
    }

    @Nonnull
    @Override
    public Predicate<FlowNode> getChunkEndPredicate() {
        return chunkEndPredicate;
    }

    public StandardSimpleChunkVisitor(Predicate<FlowNode> chunkStartPredicate, Predicate<FlowNode> chunkEndPredicate) {
        this.chunkStartPredicate = chunkStartPredicate;
        this.chunkEndPredicate = chunkEndPredicate;
    }

    /** Creates visitor that breaks on blocks starts/ends */
    public StandardSimpleChunkVisitor() {
        this.chunkStartPredicate = FlowScanningUtils.MATCH_BLOCK_START;
        this.chunkEndPredicate = (Predicate)(Predicates.instanceOf(BlockEndNode.class));
    }

    @Override
    public void chunkStart(@Nonnull FlowNode startNode, @CheckForNull FlowNode beforeBlock, @Nonnull ForkScanner scanner) {

    }

    @Override
    public void chunkEnd(@Nonnull FlowNode endNode, @CheckForNull FlowNode afterBlock, @Nonnull ForkScanner scanner) {

    }

    @Override
    public void parallelStart(@Nonnull FlowNode parallelStartNode, @Nonnull FlowNode branchNode, @Nonnull ForkScanner scanner) {

    }

    @Override
    public void parallelEnd(@Nonnull FlowNode parallelStartNode, @Nonnull FlowNode parallelEndNode, @Nonnull ForkScanner scanner) {

    }

    @Override
    public void parallelBranchStart(String branchName, @Nonnull FlowNode parallelStartNode, @Nonnull FlowNode branchStartNode, @Nonnull ForkScanner scanner) {

    }

    @Override
    public void parallelBranchEnd(String branchName, @Nonnull FlowNode parallelStartNode, @Nonnull FlowNode branchEndNode, @Nonnull ForkScanner scanner) {

    }

    @Override
    public void atomNode(@CheckForNull FlowNode before, @Nonnull FlowNode atomNode, @CheckForNull FlowNode after, @Nonnull ForkScanner scan) {

    }
}

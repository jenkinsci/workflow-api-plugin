package org.jenkinsci.plugins.workflow.graphanalysis;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.ArrayDeque;

/**
 * Fairly straightforward implementation that will cover many cases.
 * To use it, extend it and invoke the parent methods while adding internal logic.
 * Created by @author <samvanoort@gmail.com>Sam Van Oort</samvanoort@gmail.com>
 */
@SuppressFBWarnings
public class StandardSimpleChunkVisitor implements SimpleChunkVisitor {

    // FIXME: nice-to-have: track current parallel state so we can do pause timing for parallel branches.

    protected ArrayDeque<MemoryFlowChunk> currentChunks = new ArrayDeque<MemoryFlowChunk>();
    protected MemoryFlowChunk currentChunk;

    // Tracks parallel state, last item in currentChunks
    protected ArrayDeque<ParallelMemoryFlowChunk> parallels = new ArrayDeque<ParallelMemoryFlowChunk>();

    @Override
    public void chunkStart(@Nonnull FlowNode startNode, @CheckForNull FlowNode beforeBlock, @Nonnull ForkScanner scanner) {

    }

    @Override
    public void chunkEnd(@Nonnull FlowNode endNode, @CheckForNull FlowNode afterBlock, @Nonnull ForkScanner scanner) {

    }

    @Override
    public void parallelStart(@Nonnull FlowNode parallelStartNode, @Nonnull FlowNode branchNode, @Nonnull ForkScanner scanner) {
        /*if (parallels.size() > 0) {
            Iterator<ParallelMemoryFlowChunk> it = parallels.iterator();
            while (it.hasNext()) {
                ParallelMemoryFlowChunk p = it.next();
                if (p.getFirstNode() == parallelStartNode) {
                    it.remove();
                    break;
                }
            }
        }*/
    }

    @Override
    public void parallelEnd(@Nonnull FlowNode parallelStartNode, @Nonnull FlowNode parallelEndNode, @Nonnull ForkScanner scanner) {
        /*ParallelMemoryFlowChunk chunk = new ParallelMemoryFlowChunk(parallelStartNode, parallelEndNode);
        parallels.push(chunk);*/
    }

    @Override
    public void parallelBranchStart(@Nonnull FlowNode parallelStartNode, @Nonnull FlowNode branchStartNode, @Nonnull ForkScanner scanner) {
        // TODO handle me
    }

    @Override
    public void parallelBranchEnd(@Nonnull FlowNode parallelStartNode, @Nonnull FlowNode branchEndNode, @Nonnull ForkScanner scanner) {
        // TOOD do stuff with me
    }

    @Override
    public void atomNode(@CheckForNull FlowNode before, @Nonnull FlowNode atomNode, @CheckForNull FlowNode after, @Nonnull ForkScanner scan) {

    }
}

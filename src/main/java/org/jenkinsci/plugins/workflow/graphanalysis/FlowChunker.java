package org.jenkinsci.plugins.workflow.graphanalysis;

import com.google.common.base.Predicate;
import org.jenkinsci.plugins.workflow.actions.ThreadNameAction;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.BlockEndNode;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

import javax.annotation.Nonnull;
import java.util.ArrayDeque;
import java.util.Enumeration;

/**
 * Splits a flow into chunks. How those chunks are handled is someone else's business...
 * @author <samvanoort@gmail.com>Sam Van Oort</samvanoort@gmail.com>
 */
public class FlowChunker {

    // Adapter to convert from raw ForkScanner iteration to chunks
    static class ChunkingIterator {
        FlowNode next;
        FlowNode previous;
        boolean isInsideChunk;
        SimpleChunkVisitor visitor;

        // Walk through visiting each node and firing callbacks as needed
        boolean next(ForkScanner f) {
            FlowNode currentParallelStart = f.getCurrentParallelStartNode();

            if (f.hasNext()) {
                FlowNode newNext = f.next(); // Next becomes current
                boolean isTipOfParallelBranch = false; //Start or end node for branch
                boolean isAtom = false;
                if (visitor.getChunkEndPredicate().apply(next)) {
                    visitor.chunkEnd(next, previous, f);
                } else if (visitor.getChunkStartPredicate().apply(next)) {
                    visitor.chunkStart(next, newNext, f);
                } else {
                    isAtom = true;
                    // FIXME what if we're in parallel start or end

                }
                if (next instanceof BlockEndNode) {
                    BlockStartNode start = ((BlockEndNode) next).getStartNode();
                    ThreadNameAction thread = start.getAction(ThreadNameAction.class);
                    if (thread != null) {
                        visitor.parallelBranchEnd(thread.getThreadName(), start, next, f);
                    } else if (next.getParentIds().size() > 0) {
                        visitor.parallelEnd(start, next, f);
                    }
                } else if (next instanceof BlockStartNode) {
                    ThreadNameAction thread = next.getAction(ThreadNameAction.class);
                    if (thread != null) {
                        visitor.parallelBranchStart(thread.getThreadName(), next.getParents().get(0), next, f);
                    } else {
                        // TODO use forkscanner state to see if we've hit a parallel start node
                    }
                } else {
                    // TODO use the state in ForkScanner to detect if we're beinning in an implicit parallel block
                }

                if(isAtom) {
                    if (!isTipOfParallelBranch) {
                        visitor.atomNode(newNext, next, previous, f);
                    } else { //We need to use parallel tips info?
                        // TODO case for start of branch
                        // TODO case for end of branch
                    }
                }

                previous = next;
                next = newNext;
                return true;
            } else {
                finish();
                return false;
            }

        }

        void finish() {
            // cap things off for final node & do postprocessing
        }

    }

    /** Walks through a flow, doing chunking */
    public static void walkme(FlowExecution exec, SimpleChunkVisitor visitor) {
        ForkScanner scan = new ForkScanner();
        scan.setup(exec.getCurrentHeads());

        ChunkingIterator context = new ChunkingIterator();
        context.isInsideChunk = visitor.startInsideChunk();
        context.visitor = visitor;
        // SETUP for first nodes?
        while (context.next(scan)) {
            // Do nothing, it'll run until done
        }
    }

    /**
     * Walks through splitting to chunks based on the condition and exposing them as something we can iterate over (yeah I know)
     * @param run
     * @param chunkStartCondition
     * @param chunkEndCondition
     * @return
     */
    public static Enumeration<FlowChunkWithContext> splitMe(@Nonnull FlowExecution run, @Nonnull Predicate<FlowChunk> chunkStartCondition, @Nonnull Predicate<FlowChunk> chunkEndCondition) {
        // TODO create enumerator that builds up an ArrayDeque of chunks & a tree of parallels if needed
        return null;
    }
}

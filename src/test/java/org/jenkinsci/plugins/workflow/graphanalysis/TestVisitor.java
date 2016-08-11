package org.jenkinsci.plugins.workflow.graphanalysis;

import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.junit.Assert;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Test visitor class, tracks invocations of methods
 */
public class TestVisitor implements SimpleChunkVisitor {
    public enum CallType {
        ATOM_NODE,
        CHUNK_START,
        CHUNK_END,
        PARALLEL_START,
        PARALLEL_END,
        PARALLEL_BRANCH_START,
        PARALLEL_BRANCH_END
    }

    public static class CallEntry {
        CallType type;
        int[] ids = {-1, -1, -1, -1};

        public void setIds(FlowNode... nodes) {
            for (int i=0; i<nodes.length; i++) {
                if (nodes[i] == null) {
                    ids[i] = -1;
                } else {
                    ids[i] = Integer.parseInt(nodes[i].getId());
                }
            }
        }

        public CallEntry(CallType type, FlowNode... nodes) {
            this.type = type;
            this.setIds(nodes);
        }

        public CallEntry(CallType type, int... vals) {
            this.type = type;
            for (int i=0; i<vals.length; i++){
                ids[i]=vals[i];
            }
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || !(o instanceof CallEntry)) {
                return false;
            }
            CallEntry entry = (CallEntry)o;
            return this.type == entry.type && Arrays.equals(this.ids, entry.ids);
        }

        public void assertEquals(CallEntry test) {
            Assert.assertNotNull(test);
            Assert.assertNotNull(test.type);
            Assert.assertArrayEquals(this.ids, test.ids);
        }
    }

    public List<CallEntry> calls = new ArrayList<CallEntry>();

    @Override
    public void chunkStart(@Nonnull FlowNode startNode, @CheckForNull FlowNode beforeBlock, @Nonnull ForkScanner scanner) {
        calls.add(new CallEntry(CallType.CHUNK_START, startNode, beforeBlock));
    }

    @Override
    public void chunkEnd(@Nonnull FlowNode endNode, @CheckForNull FlowNode afterChunk, @Nonnull ForkScanner scanner) {
        calls.add(new CallEntry(CallType.CHUNK_END, endNode, afterChunk));
    }

    @Override
    public void parallelStart(@Nonnull FlowNode parallelStartNode, @Nonnull FlowNode branchNode, @Nonnull ForkScanner scanner) {
        calls.add(new CallEntry(CallType.PARALLEL_START, parallelStartNode, branchNode));
    }

    @Override
    public void parallelEnd(@Nonnull FlowNode parallelStartNode, @Nonnull FlowNode parallelEndNode, @Nonnull ForkScanner scanner) {
        calls.add(new CallEntry(CallType.PARALLEL_END, parallelStartNode, parallelEndNode));
    }

    @Override
    public void parallelBranchStart(@Nonnull FlowNode parallelStartNode, @Nonnull FlowNode branchStartNode, @Nonnull ForkScanner scanner) {
        calls.add(new CallEntry(CallType.PARALLEL_BRANCH_START, parallelStartNode, branchStartNode));
    }

    @Override
    public void parallelBranchEnd(@Nonnull FlowNode parallelStartNode, @Nonnull FlowNode branchEndNode, @Nonnull ForkScanner scanner) {
        calls.add(new CallEntry(CallType.PARALLEL_BRANCH_END, parallelStartNode, branchEndNode));
    }

    @Override
    public void atomNode(@CheckForNull FlowNode before, @Nonnull FlowNode atomNode, @CheckForNull FlowNode after, @Nonnull ForkScanner scan) {
        calls.add(new CallEntry(CallType.ATOM_NODE, before, atomNode, after));
    }
}

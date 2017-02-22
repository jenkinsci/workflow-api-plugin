package org.jenkinsci.plugins.workflow.graphanalysis;

import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.junit.Assert;
import org.jvnet.hudson.test.Issue;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

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

    Boolean isFromCompleteRun = null;  // Unknown by default

    public static final EnumSet<CallType> CHUNK_EVENTS = EnumSet.of(CallType.ATOM_NODE, CallType.CHUNK_START, CallType.CHUNK_END);

    public void setIsFromCompleteRun(boolean isCompleteRun) {
        this.isFromCompleteRun = isCompleteRun;
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

        /** Return ID of the node pointed at by this event or null if none */
        @CheckForNull
        public Integer getNodeId() {
            int idOfInterest = -1;
            if (this.type == CallType.ATOM_NODE || this.type == CallType.PARALLEL_END ||
                    this.type == CallType.PARALLEL_BRANCH_START || this.type == CallType.PARALLEL_BRANCH_END) {
                idOfInterest = ids[1];
            } else {
                idOfInterest = ids[0];
            }
            return (idOfInterest == -1) ? null : idOfInterest;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder("CallEntry: ")
                    .append(type).append('-');
            switch (type) {
                case ATOM_NODE:
                    builder.append("Before/Current/After:")
                            .append(ids[0]).append('/')
                            .append(ids[1]).append('/')
                            .append(ids[2]);
                    break;
                case CHUNK_START:
                    builder.append("StartNode/BeforeNode:")
                            .append(ids[0]).append('/')
                            .append(ids[1]);
                    break;
                case CHUNK_END:
                    builder.append("EndNode/AfterNode:")
                            .append(ids[0]).append('/')
                            .append(ids[1]);
                    break;
                case PARALLEL_START:
                    builder.append("ParallelStartNode/OneBranchStartNode:")
                            .append(ids[0]).append('/')
                            .append(ids[1]);
                    break;
                case PARALLEL_END:
                    builder.append("ParallelStartNode/ParallelEndNode:")
                            .append(ids[0]).append('/')
                            .append(ids[1]);
                    break;
                case PARALLEL_BRANCH_START:
                    builder.append("ParallelStart/BranchStart:")
                            .append(ids[0]).append('/')
                            .append(ids[1]);
                    break;
                case PARALLEL_BRANCH_END:
                    builder.append("ParallelStart/BranchEnd:")
                            .append(ids[0]).append('/')
                            .append(ids[1]);
                    break;
            }
            return builder.toString();
        }

    }

    public ArrayList<CallEntry> calls = new ArrayList<CallEntry>();

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

    public void reset() {
        this.calls.clear();
        this.isFromCompleteRun = null;
    }

    /** Get all call entries of given type */
    public List<TestVisitor.CallEntry> filteredCallsByType(TestVisitor.CallType type) {
        ArrayList<TestVisitor.CallEntry> output = new ArrayList<TestVisitor.CallEntry>();
        for (TestVisitor.CallEntry ce : calls) {
            if (ce.type == type) {
                output.add(ce);
            }
        }
        return output;
    }

    /** Tests that the rules laid out in {@link SimpleChunkVisitor} javadocs are followed.
     *  Specifically: no atomNode dupes for the same node, no atomNode with a start/end for the same node*/
    public void assertNoDupes() throws Exception {
        // Full equality check
        List<CallEntry> entries = new ArrayList<CallEntry>();
        HashSet<Integer> visitedAtomNodes = new HashSet<Integer>();
        HashSet<Integer> visitedChunkStartNodes = new HashSet<Integer>();
        HashSet<Integer> visitedChunkEndNodes = new HashSet<Integer>();

        for (CallEntry ce : this.calls) {
            // Complete equality check
            if (entries.contains(ce)) {
                Assert.fail("Duplicate call: "+ce.toString());
            }
            // A node is either a start or end to a chunk, or an atom (a node within a chunk)
            if (CHUNK_EVENTS.contains(ce.type)) {
                int idToCheck = (ce.type == CallType.ATOM_NODE) ? ce.ids[1] : ce.ids[0];
                if (ce.type == CallType.ATOM_NODE) {
                    if (visitedAtomNodes.contains(idToCheck)) {
                        Assert.fail("Duplicate atomNode callback for node "+idToCheck+" with "+ce);
                    } else if (visitedChunkStartNodes.contains(idToCheck)) {
                        Assert.fail("Illegal atomNode callback where chunkStart callback existed for node "+idToCheck+" with "+ce);
                    } else if (visitedChunkEndNodes.contains(idToCheck)) {
                        Assert.fail("Illegal atomNode callback where chunkEnd callback existed for node "+idToCheck+" with "+ce);
                    }
                    visitedAtomNodes.add(idToCheck);
                } else { // Start/end
                    if (visitedAtomNodes.contains(idToCheck)) {
                        Assert.fail("Illegal chunk start/end callback where atomNode callback existed for node "+idToCheck+" with "+ce);
                    }
                    if (ce.type == CallType.CHUNK_START){
                        boolean added = visitedChunkStartNodes.add(idToCheck);
                        Assert.assertTrue("Duplicate chunkStart callback for node "+idToCheck+" with "+ce, added);
                    } else { // ChunkEnd
                        boolean added = visitedChunkEndNodes.add(idToCheck);
                        Assert.assertTrue("Duplicate chunkEnd callback for node "+idToCheck+" with "+ce, added);
                    }
                }
            }
        }
    }

    /** Parallel callback events CANNOT have nulls for the parallel start node */
    @Issue("JENKINS-39841")
    public void assertNoIllegalNullsInEvents() throws Exception {
        for (CallEntry ce : calls) {
            Integer id = ce.getNodeId();
            Assert.assertNotNull("Callback with illegally null node: "+ce, id);
            if (ce.type == CallType.PARALLEL_START || ce.type == CallType.PARALLEL_END
                    || ce.type == CallType.PARALLEL_BRANCH_START || ce.type == CallType.PARALLEL_BRANCH_END) {
                Assert.assertNotNull("Parallel event with illegally null parallel start node ID: "+ce, ce.ids[0]);
            }
        }
    }

    public void assertAllNodesGotChunkEvents(Iterable<FlowNode> nodes) {
        HashSet<String> ids = new HashSet<String>();
        for (CallEntry ce : this.calls) {
            // A node is either a start or end to a chunk, or an atom (a node within a chunk)
            if (CHUNK_EVENTS.contains(ce.type)) {
                int idToCheck = (ce.type == CallType.ATOM_NODE) ? ce.ids[1] : ce.ids[0];
                ids.add(Integer.toString(idToCheck));
            }
        }
        for (FlowNode f : nodes) {
            if(!ids.contains(f.getId())) {
                Assert.fail("No chunk callbacks for flownode: "+f);
            }
        }
    }

    public void assertMatchingParallelBranchStartEnd() throws Exception {
        // Map the parallel start node to the start/end nodes for all branches
        HashMap<Integer, List<Integer>> branchStartIds = new HashMap<Integer, List<Integer>>();
        HashMap<Integer, List<Integer>> branchEndIds = new HashMap<Integer, List<Integer>>();

        for (CallEntry ce : this.calls) {
            if (ce.type == CallType.PARALLEL_BRANCH_END) {
                List<Integer> ends = branchEndIds.get(ce.ids[0]);
                if (ends == null) {
                    ends = new ArrayList<Integer>();
                }
                ends.add(ce.ids[1]);
                branchEndIds.put(ce.ids[0], ends);
            } else if (ce.type == CallType.PARALLEL_BRANCH_START) {
                List<Integer> ends = branchStartIds.get(ce.ids[0]);
                if (ends == null) {
                    ends = new ArrayList<Integer>();
                }
                ends.add(ce.ids[1]);
                branchStartIds.put(ce.ids[0], ends);
            }
        }

        // First check every parallel with branch starts *also* has branch ends and the same number of them
        if (this.isFromCompleteRun != null && this.isFromCompleteRun) {
            for (Map.Entry<Integer, List<Integer>> startEntry : branchStartIds.entrySet()) {
                List<Integer> ends = branchEndIds.get(startEntry.getKey());
                // Branch start without branch end is legal due to incomplete flows
                if (ends != null) {  // Can have starts without ends due to single-branch parallels with incomplete branches that are unterminated
                    Assert.assertEquals("Parallels must have matching numbers of start and end events, but don't -- for parallel starting with: " +
                            startEntry.getKey(), startEntry.getValue().size(), ends.size());
                }
            }
        }

        // Verify the reverse is true: if we have a branch end, there are branch starts (count equality was checked above)
        for (Map.Entry<Integer, List<Integer>> endEntry : branchEndIds.entrySet()) {
            List<Integer> starts = branchStartIds.get(endEntry.getKey());
            Assert.assertNotNull("Parallels with a branch end event(s) but no matching branch start event(s), parallel start node id: "+endEntry.getKey(), starts);
        }
    }

    /** Verify that we have balanced start/end for parallels */
    public void assertMatchingParallelStartEnd() throws Exception {
        // It's like balancing parentheses, starts and ends must be equal
        ArrayDeque<Integer> openParallelStarts = new ArrayDeque<Integer>();

        for (CallEntry ce : this.calls) {
            if (ce.type == CallType.PARALLEL_END) {
                openParallelStarts.push(ce.ids[0]);
            } else if (ce.type == CallType.PARALLEL_START) {
                if (openParallelStarts.size() > 0) {
                    Assert.assertEquals("Parallel start and end events must point to the same parallel start node ID",
                            openParallelStarts.peekFirst(), new Integer(ce.ids[0])
                    );
                    openParallelStarts.pop();
                }

                // More parallel starts than ends is *legal* because we may have an in-progress parallel without an end created.
            }
        }

        if (openParallelStarts.size() > 0) {
            StringBuilder sb = new StringBuilder();
            for (Integer parallelStartId : openParallelStarts) {
                sb.append(parallelStartId).append(',');
            }
            Assert.fail("Parallel ends with no starts, for parallel(s) with start nodes IDs: "+sb.toString());
        }
    }
}

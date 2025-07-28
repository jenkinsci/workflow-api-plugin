package org.jenkinsci.plugins.workflow.graphanalysis;

import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jvnet.hudson.test.Issue;

import edu.umd.cs.findbugs.annotations.CheckForNull;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import edu.umd.cs.findbugs.annotations.NonNull;
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
            System.arraycopy(vals, 0, ids, 0, vals.length);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof CallEntry entry)) {
                return false;
            }
            return this.type == entry.type && Arrays.equals(this.ids, entry.ids);
        }

        public void assertEquals(CallEntry test) {
            assertNotNull(test);
            assertNotNull(test.type);
            assertArrayEquals(this.ids, test.ids);
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

    public ArrayList<CallEntry> calls = new ArrayList<>();

    @Override
    public void chunkStart(@NonNull FlowNode startNode, @CheckForNull FlowNode beforeBlock, @NonNull ForkScanner scanner) {
        calls.add(new CallEntry(CallType.CHUNK_START, startNode, beforeBlock));
    }

    @Override
    public void chunkEnd(@NonNull FlowNode endNode, @CheckForNull FlowNode afterChunk, @NonNull ForkScanner scanner) {
        calls.add(new CallEntry(CallType.CHUNK_END, endNode, afterChunk));
    }

    @Override
    public void parallelStart(@NonNull FlowNode parallelStartNode, @NonNull FlowNode branchNode, @NonNull ForkScanner scanner) {
        calls.add(new CallEntry(CallType.PARALLEL_START, parallelStartNode, branchNode));
    }

    @Override
    public void parallelEnd(@NonNull FlowNode parallelStartNode, @NonNull FlowNode parallelEndNode, @NonNull ForkScanner scanner) {
        calls.add(new CallEntry(CallType.PARALLEL_END, parallelStartNode, parallelEndNode));
    }

    @Override
    public void parallelBranchStart(@NonNull FlowNode parallelStartNode, @NonNull FlowNode branchStartNode, @NonNull ForkScanner scanner) {
        calls.add(new CallEntry(CallType.PARALLEL_BRANCH_START, parallelStartNode, branchStartNode));
    }

    @Override
    public void parallelBranchEnd(@NonNull FlowNode parallelStartNode, @NonNull FlowNode branchEndNode, @NonNull ForkScanner scanner) {
        calls.add(new CallEntry(CallType.PARALLEL_BRANCH_END, parallelStartNode, branchEndNode));
    }

    @Override
    public void atomNode(@CheckForNull FlowNode before, @NonNull FlowNode atomNode, @CheckForNull FlowNode after, @NonNull ForkScanner scan) {
        calls.add(new CallEntry(CallType.ATOM_NODE, before, atomNode, after));
    }

    public void reset() {
        this.calls.clear();
        this.isFromCompleteRun = null;
    }

    /** Get all call entries of given type */
    public List<TestVisitor.CallEntry> filteredCallsByType(TestVisitor.CallType type) {
        ArrayList<TestVisitor.CallEntry> output = new ArrayList<>();
        for (TestVisitor.CallEntry ce : calls) {
            if (ce.type == type) {
                output.add(ce);
            }
        }
        return output;
    }

    /** Tests that the rules laid out in {@link SimpleChunkVisitor} javadocs are followed.
     *  Specifically: no atomNode dupes for the same node, no atomNode with a start/end for the same node*/
    public void assertNoDupes() {
        // Full equality check
        List<CallEntry> entries = new ArrayList<>();
        HashSet<Integer> visitedAtomNodes = new HashSet<>();
        HashSet<Integer> visitedChunkStartNodes = new HashSet<>();
        HashSet<Integer> visitedChunkEndNodes = new HashSet<>();

        for (CallEntry ce : this.calls) {
            // Complete equality check
            if (entries.contains(ce)) {
                fail("Duplicate call: "+ce.toString());
            }
            // A node is either a start or end to a chunk, or an atom (a node within a chunk)
            if (CHUNK_EVENTS.contains(ce.type)) {
                int idToCheck = (ce.type == CallType.ATOM_NODE) ? ce.ids[1] : ce.ids[0];
                if (ce.type == CallType.ATOM_NODE) {
                    if (visitedAtomNodes.contains(idToCheck)) {
                        fail("Duplicate atomNode callback for node "+idToCheck+" with "+ce);
                    } else if (visitedChunkStartNodes.contains(idToCheck)) {
                        fail("Illegal atomNode callback where chunkStart callback existed for node "+idToCheck+" with "+ce);
                    } else if (visitedChunkEndNodes.contains(idToCheck)) {
                        fail("Illegal atomNode callback where chunkEnd callback existed for node "+idToCheck+" with "+ce);
                    }
                    visitedAtomNodes.add(idToCheck);
                } else { // Start/end
                    if (visitedAtomNodes.contains(idToCheck)) {
                        fail("Illegal chunk start/end callback where atomNode callback existed for node "+idToCheck+" with "+ce);
                    }
                    if (ce.type == CallType.CHUNK_START){
                        boolean added = visitedChunkStartNodes.add(idToCheck);
                        assertTrue(added, "Duplicate chunkStart callback for node "+idToCheck+" with "+ce);
                    } else { // ChunkEnd
                        boolean added = visitedChunkEndNodes.add(idToCheck);
                        assertTrue(added, "Duplicate chunkEnd callback for node "+idToCheck+" with "+ce);
                    }
                }
            }
        }
    }

    /** Parallel callback events CANNOT have nulls for the parallel start node */
    @Issue("JENKINS-39841")
    public void assertNoIllegalNullsInEvents() {
        for (CallEntry ce : calls) {
            Integer id = ce.getNodeId();
            assertNotNull(id, "Callback with illegally null node: "+ce);
            if (ce.type == CallType.PARALLEL_START || ce.type == CallType.PARALLEL_END
                    || ce.type == CallType.PARALLEL_BRANCH_START || ce.type == CallType.PARALLEL_BRANCH_END) {
                assertNotEquals(-1, ce.ids[0], "Parallel event with illegally null parallel start node ID: " + ce);
            }
        }
    }

    public void assertAllNodesGotChunkEvents(Iterable<FlowNode> nodes) {
        HashSet<String> ids = new HashSet<>();
        for (CallEntry ce : this.calls) {
            // A node is either a start or end to a chunk, or an atom (a node within a chunk)
            if (CHUNK_EVENTS.contains(ce.type)) {
                int idToCheck = (ce.type == CallType.ATOM_NODE) ? ce.ids[1] : ce.ids[0];
                ids.add(Integer.toString(idToCheck));
            }
        }
        for (FlowNode f : nodes) {
            if(!ids.contains(f.getId())) {
                fail("No chunk callbacks for flownode: "+f);
            }
        }
    }

    public void assertMatchingParallelBranchStartEnd() {
        // Map the parallel start node to the start/end nodes for all branches
        HashMap<Integer, List<Integer>> branchStartIds = new HashMap<>();
        HashMap<Integer, List<Integer>> branchEndIds = new HashMap<>();

        for (CallEntry ce : this.calls) {
            if (ce.type == CallType.PARALLEL_BRANCH_END) {
                List<Integer> ends = branchEndIds.get(ce.ids[0]);
                if (ends == null) {
                    ends = new ArrayList<>();
                }
                ends.add(ce.ids[1]);
                branchEndIds.put(ce.ids[0], ends);
            } else if (ce.type == CallType.PARALLEL_BRANCH_START) {
                List<Integer> ends = branchStartIds.get(ce.ids[0]);
                if (ends == null) {
                    ends = new ArrayList<>();
                }
                ends.add(ce.ids[1]);
                branchStartIds.put(ce.ids[0], ends);
            }
        }

        // First check every parallel with branch starts *also* has branch ends and the same number of them
        if (this.isFromCompleteRun != null && this.isFromCompleteRun) {
            for (Map.Entry<Integer, List<Integer>> startEntry : branchStartIds.entrySet()) {
                List<Integer> ends = branchEndIds.get(startEntry.getKey());
                // Branch start without branch end is legal due to incomplete flows, but not when complete!
                if (this.isFromCompleteRun != null  && this.isFromCompleteRun) {
                    assertNotNull(ends);
                } else if (ends != null) {  // Can have starts without ends due to single-branch parallels with incomplete branches that are unterminated
                    assertEquals(startEntry.getValue().size(), ends.size(), "Parallels must have matching numbers of start and end events, but don't -- for parallel starting with: " +
                            startEntry.getKey());
                }
            }
        }

        // Verify the reverse is true: if we have a branch end, there are branch starts (count equality was checked above)
        for (Map.Entry<Integer, List<Integer>> endEntry : branchEndIds.entrySet()) {
            List<Integer> starts = branchStartIds.get(endEntry.getKey());
            assertNotNull(starts, "Parallels with a branch end event(s) but no matching branch start event(s), parallel start node id: "+endEntry.getKey());
        }
    }

    /** Verify that we have balanced start/end for parallels */
    public void assertMatchingParallelStartEnd() {
        // It's like balancing parentheses, starts and ends must be equal
        ArrayDeque<Integer> openParallelStarts = new ArrayDeque<>();

        for (CallEntry ce : this.calls) {
            if (ce.type == CallType.PARALLEL_END) {
                openParallelStarts.push(ce.ids[0]);
            } else if (ce.type == CallType.PARALLEL_START) {
                if (!openParallelStarts.isEmpty()) {
                    assertEquals(openParallelStarts.peekFirst(), Integer.valueOf(ce.ids[0]), "Parallel start and end events must point to the same parallel start node ID"
                    );
                    openParallelStarts.pop();
                } else if (isFromCompleteRun != null && isFromCompleteRun) {
                    // For a complete flow, every start must have an end, for an incomplete one we may have
                    //  an incomplete block (still running)
                    fail("Found a parallel start without a matching end, with CallEntry: "+ce);
                }
            }
        }

        if (!openParallelStarts.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (Integer parallelStartId : openParallelStarts) {
                sb.append(parallelStartId).append(',');
            }
            fail("Parallel ends with no starts, for parallel(s) with start nodes IDs: " + sb);
        }
    }
}

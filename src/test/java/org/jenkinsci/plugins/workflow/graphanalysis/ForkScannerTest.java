/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jenkinsci.plugins.workflow.graphanalysis;

import org.jenkinsci.plugins.workflow.actions.ThreadNameAction;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.nodes.StepEndNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.cps.steps.ParallelStep;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.EchoStep;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.junit.Assert;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

// Slightly dirty but it removes a ton of FlowTestUtils.* class qualifiers
import static org.jenkinsci.plugins.workflow.graphanalysis.FlowTestUtils.*;

/**
 * Tests for internals of ForkScanner
 */
public class ForkScannerTest {
    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @Rule
    public JenkinsRule r = new JenkinsRule();

    public static Predicate<TestVisitor.CallEntry> predicateForCallEntryType(final TestVisitor.CallType type) {
        return new Predicate<TestVisitor.CallEntry>() {
            TestVisitor.CallType myType = type;

            @Override
            public boolean test(TestVisitor.CallEntry input) {
                return input.type != null && input.type == myType;
            }
        };
    }

    /** Flow structure (ID - type)
     2 - FlowStartNode (BlockStartNode)
     3 - Echostep
     4 - ParallelStep (StepStartNode) (start branches)
     6 - ParallelStep (StepStartNode) (start branch 1), ParallelLabelAction with branchname=1
     7 - ParallelStep (StepStartNode) (start branch 2), ParallelLabelAction with branchname=2
     8 - EchoStep, (branch 1) parent=6
     9 - StepEndNode, (end branch 1) startId=6, parentId=8
     10 - EchoStep, (branch 2) parentId=7
     11 - EchoStep, (branch 2) parentId = 10
     12 - StepEndNode (end branch 2)  startId=7  parentId=11,
     13 - StepEndNode (close branches), parentIds = 9,12, startId=4
     14 - EchoStep
     15 - FlowEndNode (BlockEndNode)
     */
    WorkflowRun SIMPLE_PARALLEL_RUN;

    /** Parallel nested in parallel (ID-type)
     * 2 - FlowStartNode (BlockStartNode)
     * 3 - Echostep
     * 4 - ParallelStep (stepstartnode)
     * 6 - ParallelStep (StepStartNode) (start branch 1), ParallelLabelAction with branchname=1
     * 7 - ParallelStep (StepStartNode) (start branch 2), ParallelLabelAction with branchname=2
     * 8 - EchoStep (branch #1) - parentId=6
     * 9 - StepEndNode (end branch #1) - startId=6
     * 10 - EchoStep - parentId=7
     * 11 - EchoStep
     * 12 - ParallelStep (StepStartNode) - start inner parallel
     * 14 - ParallelStep (StepStartNode) (start branch 2-1), parentId=12, ParallelLabellAction with branchName=2-1
     * 15 - ParallelStep (StepStartNode) (start branch 2-2), parentId=12, ParallelLabelAction with branchName=2-2
     * 16 - Echo (Branch2-1), parentId=14
     * 17 - StepEndNode (end branch 2-1), parentId=16, startId=14
     * 18 - SleepStep (branch 2-2) parentId=15
     * 19 - EchoStep (branch 2-2)
     * 20 - StepEndNode (end branch 2-2), startId=15
     * 21 - StepEndNode (end inner parallel ), parentIds=17,20, startId=12
     * 22 - StepEndNode (end parallel #2), parent=21, startId=7
     * 23 - StepEndNode (end outer parallel), parentIds=9,22, startId=4
     * 24 - Echo
     * 25 - FlowEndNode
     */
    WorkflowRun NESTED_PARALLEL_RUN;

    @Before
    public void setUp() throws Exception {
        r.jenkins.getInjector().injectMembers(this);

        WorkflowJob job = r.jenkins.createProject(WorkflowJob.class, "SimpleParallel");
        job.setDefinition(new CpsFlowDefinition(
                "echo 'first'\n" +
                        "def steps = [:]\n" +
                        "steps['1'] = {\n" +
                        "    echo 'do 1 stuff'\n" +
                        "}\n" +
                        "steps['2'] = {\n" +
                        "    echo '2a'\n" +
                        "    echo '2b'\n" +
                        "}\n" +
                        "parallel steps\n" +
                        "echo 'final'",
                true));
        WorkflowRun b = r.assertBuildStatusSuccess(job.scheduleBuild2(0));
        this.SIMPLE_PARALLEL_RUN = b;

        job = r.jenkins.createProject(WorkflowJob.class, "NestedParallel");
        job.setDefinition(new CpsFlowDefinition(
                "echo 'first'\n" +
                        "def steps = [:]\n" +
                        "steps['1'] = {\n" +
                        "    echo 'do 1 stuff'\n" +
                        "}\n" +
                        "steps['2'] = {\n" +
                        "    echo '2a'\n" +
                        "    echo '2b'\n" +
                        "    def nested = [:]\n" +
                        "    nested['2-1'] = {\n" +
                        "        echo 'do 2-1'\n" +
                        "    } \n" +
                        "    nested['2-2'] = {\n" +
                        "        sleep 1\n" +
                        "        echo '2 section 2'\n" +
                        "    }\n" +
                        "    parallel nested\n" +
                        "}\n" +
                        "parallel steps\n" +
                        "echo 'final'",
                true));
        b = r.assertBuildStatusSuccess(job.scheduleBuild2(0));
        this.NESTED_PARALLEL_RUN = b;
    }

    /**
     * Verify that we have a parallel end event when starting in an incomplete parallel
     * AND branch end events for each incomplete branch
     * @param heads Heads of the incomplete parallel
     * @param test TestVisitor that has already visited the flowgraph
     */
    private void assertIncompleteParallelsHaveEventsForEnd(List<FlowNode> heads, TestVisitor test) {
        // Verify we have at least one appropriate parallel end event, for the mandatory parallel
        List<String> parallelEnds =
                test.filteredCallsByType(TestVisitor.CallType.PARALLEL_END).stream()
                        .map(CALL_TO_NODE_ID::apply)
                        .collect(Collectors.toList());
        boolean hasMatchingEnd = false;
        for (FlowNode f : heads) {
            if (parallelEnds.contains(f.getId())) {
                hasMatchingEnd = true;
                break;
            }
        }
        Assert.assertTrue("If there are multiple heads, we MUST be in a parallel and have an event for the end", hasMatchingEnd);

        List<String> branchEnds =
                test.filteredCallsByType(TestVisitor.CallType.PARALLEL_BRANCH_END).stream()
                        .map(CALL_TO_NODE_ID::apply)
                        .collect(Collectors.toList());
        // Verify each branch has a branch end event
        for (FlowNode f : heads) {
            // Below can be used if we harden up the guarantees with incomplete parallels
            Assert.assertTrue("Must have a parallel branch end for each branch we know of, but didn't, for nodeId: "+f.getId(),
                    branchEnds.contains(f.getId()));
        }
    }

    /** Runs a fairly extensive suite of sanity tests of iteration and visitor use */
    private void sanityTestIterationAndVisiter(List<FlowNode> heads) throws Exception {
        ForkScanner scan = new ForkScanner();
        TestVisitor test = new TestVisitor();
        scan.setup(heads);

        // Test just parallels, not the chunk start/end detection
        scan.visitSimpleChunks(test, new NoOpChunkFinder());
        test.isFromCompleteRun = scan.isWalkingFromFinish();
        if (heads.size() > 1) {
            assertIncompleteParallelsHaveEventsForEnd(heads, test);
        }
        test.assertNoIllegalNullsInEvents();
        test.assertNoDupes();
        int nodeCount = new DepthFirstScanner().allNodes(heads).size();
        Assert.assertEquals(nodeCount,
                new ForkScanner().allNodes(heads).size());
        test.assertMatchingParallelStartEnd();
        test.assertAllNodesGotChunkEvents(new DepthFirstScanner().allNodes(heads));
        assertNoMissingParallelEvents(heads);
        if (heads.size() > 0) {
            test.assertMatchingParallelBranchStartEnd();
        }

        // Test parallels + chunk start/end
        test.reset();
        scan.setup(heads);
        test.isFromCompleteRun = scan.isWalkingFromFinish();
        scan.visitSimpleChunks(test, new LabelledChunkFinder());
        test.assertNoIllegalNullsInEvents();
        test.assertNoDupes();
        // LabelledChunkFinder is isStartInsideChunk, so first chunk callback must be ChunkEnd
        int lastId = -1;
        for (int i=0; i<test.calls.size(); i++) {
            TestVisitor.CallEntry entry = test.calls.get(i);
            if (lastId > 0) {
                lastId = entry.getNodeId();
            }
            if (TestVisitor.CHUNK_EVENTS.contains(entry.type)) {
                Assert.assertEquals(TestVisitor.CallType.CHUNK_END, entry.type);
                break;
            }
        }
        Assert.assertEquals(nodeCount,
                new ForkScanner().allNodes(heads).size());
        test.assertMatchingParallelStartEnd();
        test.assertMatchingParallelBranchStartEnd();
        test.assertAllNodesGotChunkEvents(new DepthFirstScanner().allNodes(heads));
        assertNoMissingParallelEvents(heads);
    }

    @Test
    public void testForkedScanner() throws Exception {
        FlowExecution exec = SIMPLE_PARALLEL_RUN.getExecution();
        List<FlowNode> heads =  SIMPLE_PARALLEL_RUN.getExecution().getCurrentHeads();

        // Initial case
        ForkScanner scanner = new ForkScanner();
        scanner.setup(heads, null);
        Assert.assertNull(scanner.currentParallelStart);
        Assert.assertNull(scanner.currentParallelStartNode);
        Assert.assertNotNull(scanner.parallelBlockStartStack);
        Assert.assertEquals(0, scanner.parallelBlockStartStack.size());
        Assert.assertTrue(scanner.isWalkingFromFinish());
        sanityTestIterationAndVisiter(heads);

        // Fork case
        scanner.setup(exec.getNode("13"));
        Assert.assertFalse(scanner.isWalkingFromFinish());
        Assert.assertNull(scanner.currentType);
        Assert.assertEquals(ForkScanner.NodeType.PARALLEL_END, scanner.nextType);
        Assert.assertEquals("13", scanner.next().getId());
        Assert.assertNotNull(scanner.parallelBlockStartStack);
        Assert.assertEquals(0, scanner.parallelBlockStartStack.size());
        Assert.assertEquals(exec.getNode("4"), scanner.currentParallelStartNode);
        sanityTestIterationAndVisiter(Collections.singletonList(exec.getNode("13")));

        ForkScanner.ParallelBlockStart start = scanner.currentParallelStart;
        Assert.assertEquals(1, start.unvisited.size());
        Assert.assertEquals(exec.getNode("4"), start.forkStart);

        /* Flow structure (ID - type)
         2 - FlowStartNode (BlockStartNode)
         3 - Echostep
         4 - ParallelStep (StepStartNode) (start branches)
         6 - ParallelStep (StepStartNode) (start branch 1), ParallelLabelAction with branchname=1
         7 - ParallelStep (StepStartNode) (start branch 2), ParallelLabelAction with branchname=2
         8 - EchoStep, (branch 1) parent=6
         9 - StepEndNode, (end branch 1) startId=6, parentId=8
         10 - EchoStep, (branch 2) parentId=7
         11 - EchoStep, (branch 2) parentId = 10
         12 - StepEndNode (end branch 2)  startId=7  parentId=11,
         13 - StepEndNode (close branches), parentIds = 9,12, startId=4
         14 - EchoStep
         15 - FlowEndNode (BlockEndNode)
         */

        Assert.assertEquals(exec.getNode("12"), scanner.next()); //12
        Assert.assertEquals(ForkScanner.NodeType.PARALLEL_BRANCH_END, scanner.getCurrentType());
        Assert.assertEquals(ForkScanner.NodeType.NORMAL, scanner.getNextType());
        Assert.assertEquals(exec.getNode("11"), scanner.next());
        Assert.assertEquals(ForkScanner.NodeType.NORMAL, scanner.getCurrentType());
        Assert.assertEquals(exec.getNode("10"), scanner.next());
        Assert.assertEquals(ForkScanner.NodeType.NORMAL, scanner.getCurrentType());
        Assert.assertEquals(ForkScanner.NodeType.PARALLEL_BRANCH_START, scanner.getNextType());
        Assert.assertEquals(exec.getNode("7"), scanner.next());
        Assert.assertEquals(ForkScanner.NodeType.PARALLEL_BRANCH_START, scanner.getCurrentType());

        // Next branch, branch 1 (since we visit in reverse)
        Assert.assertEquals(ForkScanner.NodeType.PARALLEL_BRANCH_END, scanner.getNextType());
        Assert.assertEquals(exec.getNode("9"), scanner.next());
        Assert.assertEquals(ForkScanner.NodeType.PARALLEL_BRANCH_END, scanner.getCurrentType());
        Assert.assertEquals(exec.getNode("8"), scanner.next());
        Assert.assertEquals(ForkScanner.NodeType.NORMAL, scanner.getCurrentType());
        Assert.assertEquals(exec.getNode("6"), scanner.next());
        Assert.assertEquals(ForkScanner.NodeType.PARALLEL_BRANCH_START, scanner.getCurrentType());
        Assert.assertEquals(ForkScanner.NodeType.PARALLEL_START, scanner.getNextType());
    }

    /** Reference the flow graphs in {@link #SIMPLE_PARALLEL_RUN} and {@link #NESTED_PARALLEL_RUN} */
    @Test
    public void testFlowSegmentSplit() throws Exception {
        FlowExecution exec = SIMPLE_PARALLEL_RUN.getExecution();

        /* Flow structure (ID - type)
         2 - FlowStartNode (BlockStartNode)
         3 - Echostep
         4 - ParallelStep (StepStartNode) (start branches)
         6 - ParallelStep (StepStartNode) (start branch 1), ParallelLabelAction with branchname=1
         7 - ParallelStep (StepStartNode) (start branch 2), ParallelLabelAction with branchname=2
         8 - EchoStep, (branch 1) parent=6
         9 - StepEndNode, (end branch 1) startId=6, parentId=8
         10 - EchoStep, (branch 2) parentId=7
         11 - EchoStep, (branch 2) parentId = 10
         12 - StepEndNode (end branch 2)  startId=7  parentId=11,
         13 - StepEndNode (close branches), parentIds = 9,12, startId=4
         14 - EchoStep
         15 - FlowEndNode (BlockEndNode)
         */

        HashMap<FlowNode, ForkScanner.FlowPiece> nodeMap = new HashMap<>();
        ForkScanner.FlowSegment mainBranch = new ForkScanner.FlowSegment();
        ForkScanner.FlowSegment sideBranch = new ForkScanner.FlowSegment();
        FlowNode BRANCH1_END = exec.getNode("9");
        FlowNode BRANCH2_END = exec.getNode("12");
        FlowNode START_PARALLEL = exec.getNode("4");

        // Branch 1, we're going to run one flownode beyond the start of the parallel branch and then split
        mainBranch.add(BRANCH1_END);
        mainBranch.add(exec.getNode("8"));
        mainBranch.add(exec.getNode("6"));
        mainBranch.add(exec.getNode("4"));
        mainBranch.add(exec.getNode("3"));  // FlowNode beyond the fork point
        for (FlowNode f : mainBranch.visited) {
            nodeMap.put(f, mainBranch);
        }
        assertNodeOrder("Visited nodes", mainBranch.visited, 9, 8, 6, 4, 3);

        // Branch 2
        sideBranch.add(BRANCH2_END);
        sideBranch.add(exec.getNode("11"));
        sideBranch.add(exec.getNode("10"));
        sideBranch.add(exec.getNode("7"));
        for (FlowNode f : sideBranch.visited) {
            nodeMap.put(f, sideBranch);
        }
        assertNodeOrder("Visited nodes", sideBranch.visited, 12, 11, 10, 7);

        ForkScanner.Fork forked = mainBranch.split(nodeMap, (BlockStartNode)exec.getNode("4"), sideBranch);
        ForkScanner.FlowSegment splitSegment = (ForkScanner.FlowSegment)nodeMap.get(BRANCH1_END); // New branch
        Assert.assertNull(splitSegment.after);
        assertNodeOrder("Branch 1 split after fork", splitSegment.visited, 9, 8, 6);

        // Just the single node before the fork
        Assert.assertEquals(forked, mainBranch.after);
        assertNodeOrder("Head of flow, pre-fork", mainBranch.visited, 3);

        // Fork point
        Assert.assertEquals(forked, nodeMap.get(START_PARALLEL));
        ForkScanner.FlowPiece[] follows = {splitSegment, sideBranch};
        Assert.assertArrayEquals(follows, forked.following.toArray());

        // Branch 2
        Assert.assertEquals(sideBranch, nodeMap.get(BRANCH2_END));
        assertNodeOrder("Branch 2", sideBranch.visited, 12, 11, 10, 7);

        // Test me where splitting right at a fork point, where we should have a fork with and main branch shoudl become following
        // Along with side branch (branch2)
        nodeMap.clear();
        mainBranch = new ForkScanner.FlowSegment();
        sideBranch = new ForkScanner.FlowSegment();
        mainBranch.visited.add(exec.getNode("6"));
        mainBranch.visited.add(START_PARALLEL);
        sideBranch.visited.add(exec.getNode("7"));
        for (FlowNode f : mainBranch.visited) {
            nodeMap.put(f, mainBranch);
        }
        nodeMap.put(exec.getNode("7"), sideBranch);

        forked = mainBranch.split(nodeMap, (BlockStartNode)exec.getNode("4"), sideBranch);
        follows = new ForkScanner.FlowSegment[2];
        follows[0] = mainBranch;
        follows[1] = sideBranch;
        Assert.assertArrayEquals(follows, forked.following.toArray());
        assertNodeOrder("Branch1", mainBranch.visited, 6);
        Assert.assertNull(mainBranch.after);
        assertNodeOrder("Branch2", sideBranch.visited, 7);
        Assert.assertNull(sideBranch.after);
        Assert.assertEquals(forked, nodeMap.get(START_PARALLEL));
        Assert.assertEquals(mainBranch, nodeMap.get(exec.getNode("6")));
        Assert.assertEquals(sideBranch, nodeMap.get(exec.getNode("7")));
    }

    @Test
    public void testEmptyParallel() throws Exception {
        WorkflowJob job = r.jenkins.createProject(WorkflowJob.class, "EmptyParallel");
        job.setDefinition(new CpsFlowDefinition(
                "parallel 'empty1': {}, 'empty2':{} \n" +
                        "echo 'done' ",
                true));
        WorkflowRun b = r.assertBuildStatusSuccess(job.scheduleBuild2(0));
        ForkScanner scan = new ForkScanner();

        List<FlowNode> outputs = scan.filteredNodes(b.getExecution().getCurrentHeads(), (Predicate<FlowNode>) x -> true);
        Assert.assertEquals(9, outputs.size());
    }

    private Function<FlowNode, String> NODE_TO_ID = input -> input != null ? input.getId() : null;

    private Function<TestVisitor.CallEntry, String> CALL_TO_NODE_ID =
            input ->
                    input != null && input.getNodeId() != null
                            ? input.getNodeId().toString()
                            : null;

    /** Verifies we're not doing anything wacky with parallels that loses appropriate parallel events. */
    private void assertNoMissingParallelEvents(List<FlowNode> heads) {
        DepthFirstScanner allScan = new DepthFirstScanner();
        TestVisitor visit = new TestVisitor();
        ForkScanner forkScan = new ForkScanner();

        // First look for parallel branch start events
        List<FlowNode> matches = allScan.filteredNodes(heads, FlowScanningUtils.hasActionPredicate(ThreadNameAction.class));
        forkScan.setup(heads);
        forkScan.visitSimpleChunks(visit, new LabelledChunkFinder());
        Set<String> callIds =
                visit.filteredCallsByType(TestVisitor.CallType.PARALLEL_BRANCH_START).stream()
                        .map(CALL_TO_NODE_ID)
                        .collect(Collectors.toSet());
        for (String id : matches.stream().map(NODE_TO_ID).collect(Collectors.toList())) {
            if (!callIds.contains(id)) {
                Assert.fail("Parallel Branch start node without an appropriate parallelBranchStart callback: "+id);
            }
        }

        // Look for parallel starts & ends all being matched
        matches = allScan.filteredNodes(heads, (Predicate<FlowNode>) input ->
                input instanceof StepStartNode
                        && ((StepStartNode) input).getDescriptor() instanceof ParallelStep.DescriptorImpl
                        && input.getPersistentAction(ThreadNameAction.class) == null);
        List<FlowNode> parallelEnds = allScan.filteredNodes(heads, (Predicate<FlowNode>) input ->
                input instanceof StepEndNode
                        && ((StepEndNode) input).getDescriptor() instanceof ParallelStep.DescriptorImpl
                        && ((StepEndNode) input).getStartNode().getPersistentAction(ThreadNameAction.class) == null);
        visit.reset();
        forkScan.setup(heads);
        forkScan.visitSimpleChunks(visit, new LabelledChunkFinder());

        // Parallel starts checked
        callIds =
                visit.filteredCallsByType(TestVisitor.CallType.PARALLEL_START).stream()
                        .map(CALL_TO_NODE_ID)
                        .collect(Collectors.toSet());
        for (String id : matches.stream().map(NODE_TO_ID).collect(Collectors.toList())) {
            if (!callIds.contains(id)) {
                Assert.fail("Parallel start node without an appropriate parallelStart callback: "+id);
            }
        }

        // Parallel ends checked
        callIds =
                visit.filteredCallsByType(TestVisitor.CallType.PARALLEL_END).stream()
                        .map(CALL_TO_NODE_ID)
                        .collect(Collectors.toSet());
        for (String id : parallelEnds.stream().map(NODE_TO_ID).collect(Collectors.toList())) {
            if (!callIds.contains(id)) {
                Assert.fail("Parallel END node without an appropriate parallelEnd callback: "+id);
            }
        }

        // Parallel Ends should be handled by the checks that blocks are balanced.
    }

    @Test
    @Issue("JENKINS-39839") // Implicitly covers JENKINS-39841 too though
    public void testSingleNestedParallelBranches() throws Exception {
        String script = "node {\n" +
                "   stage 'test'  \n" +
                "     echo ('Testing')\n" +
                "     parallel nestedBranch: {\n" +
                "       echo 'nested Branch'\n" +
                "       stage ('nestedBranchStage') { \n" +
                "           echo 'running nestedBranchStage'\n" +
                "           parallel secondLevelNestedBranch1: {\n" +
                "               echo 'secondLevelNestedBranch1'\n" + //
                "           }\n" +
                "       }\n"+
                "     }, failFast: false\n" +
                "}";
        WorkflowJob job = r.jenkins.createProject(WorkflowJob.class, "SingleNestedParallelBranch");
        job.setDefinition(new CpsFlowDefinition(script, true));
        WorkflowRun b = r.assertBuildStatusSuccess(job.scheduleBuild2(0));
        FlowNode echoNode = new DepthFirstScanner().findFirstMatch(b.getExecution(), new NodeStepTypePredicate(EchoStep.DescriptorImpl.byFunctionName("echo")));
        Assert.assertNotNull(echoNode);
        sanityTestIterationAndVisiter(b.getExecution().getCurrentHeads());
        sanityTestIterationAndVisiter(Arrays.asList(echoNode));

        TestVisitor visitor = new TestVisitor();
        ForkScanner scanner = new ForkScanner();
        scanner.setup(b.getExecution().getCurrentHeads());
        scanner.visitSimpleChunks(visitor, new NoOpChunkFinder());
        Assert.assertEquals(2, visitor.filteredCallsByType(TestVisitor.CallType.PARALLEL_START).size());
        Assert.assertEquals(2, visitor.filteredCallsByType(TestVisitor.CallType.PARALLEL_END).size());
        Assert.assertEquals(2, visitor.filteredCallsByType(TestVisitor.CallType.PARALLEL_BRANCH_START).size());
        Assert.assertEquals(2, visitor.filteredCallsByType(TestVisitor.CallType.PARALLEL_BRANCH_END).size());
    }

    /** Reference the flow graphs in {@link #SIMPLE_PARALLEL_RUN} and {@link #NESTED_PARALLEL_RUN} */
    @Test
    public void testLeastCommonAncestor() throws Exception {
        FlowExecution exec = SIMPLE_PARALLEL_RUN.getExecution();

        ForkScanner scan = new ForkScanner();
        // Starts at the ends of the parallel branches
        Set<FlowNode> heads = new LinkedHashSet<>(Arrays.asList(exec.getNode("12"), exec.getNode("9")));
        ArrayDeque<ForkScanner.ParallelBlockStart> starts = scan.leastCommonAncestor(heads);
        Assert.assertEquals(1, starts.size());

        ForkScanner.ParallelBlockStart start = starts.peek();
        Assert.assertEquals(2, start.unvisited.size());
        Assert.assertEquals(exec.getNode("4"), start.forkStart);
        Assert.assertArrayEquals(heads.toArray(), start.unvisited.toArray());

        // Ensure no issues with single start triggering least common ancestor
        heads = new LinkedHashSet<>(Arrays.asList(exec.getNode("4")));
        scan.setup(heads);
        Assert.assertNull(scan.currentParallelStart);
        Assert.assertTrue(scan.parallelBlockStartStack == null || scan.parallelBlockStartStack.isEmpty());

        // Empty fork
        heads = new LinkedHashSet<>(Arrays.asList(exec.getNode("6"), exec.getNode("7")));
        starts = scan.leastCommonAncestor(heads);
        Assert.assertEquals(1, starts.size());
        ForkScanner.ParallelBlockStart pbs = starts.pop();
        Assert.assertEquals(exec.getNode("4"), pbs.forkStart);
        Assert.assertEquals(2, pbs.unvisited.size());
        Assert.assertTrue(pbs.unvisited.contains(exec.getNode("6")));
        Assert.assertTrue(pbs.unvisited.contains(exec.getNode("7")));
        sanityTestIterationAndVisiter(new ArrayList<>(heads));

        /* Now we do the same with nested run */
        exec = NESTED_PARALLEL_RUN.getExecution();
        heads = new LinkedHashSet<>(Arrays.asList(exec.getNode("9"), exec.getNode("17"), exec.getNode("20")));

        // Problem: we get a parallel start with the same flowsegment in the following for more than one parallel start
        starts = scan.leastCommonAncestor(heads);
        Assert.assertEquals(2, starts.size());
        ForkScanner.ParallelBlockStart inner = starts.getFirst();
        ForkScanner.ParallelBlockStart outer = starts.getLast();

        Assert.assertEquals(2, inner.unvisited.size());
        Assert.assertEquals(exec.getNode("12"), inner.forkStart);

        Assert.assertEquals(1, outer.unvisited.size());
        Assert.assertEquals(exec.getNode("9"), outer.unvisited.peek());
        Assert.assertEquals(exec.getNode("4"), outer.forkStart);
        sanityTestIterationAndVisiter(new ArrayList<>(heads));

        heads = new LinkedHashSet<>(Arrays.asList(exec.getNode("9"), exec.getNode("17"), exec.getNode("20")));
        starts = scan.leastCommonAncestor(heads);
        Assert.assertEquals(2, starts.size());
        sanityTestIterationAndVisiter(new ArrayList<>(heads));
    }

    @Test
    @Issue("JENKINS-38089")
    public void testVariousParallelCombos() throws Exception {
        WorkflowJob job = r.jenkins.createProject(WorkflowJob.class, "ParallelTimingBug");
        job.setDefinition(new CpsFlowDefinition(
            // Seemingly gratuitous sleep steps are because original issue required specific timing to reproduce
            "stage 'test' \n" +
            "    parallel 'unit': {\n" +
            "          retry(1) {\n" +
            "            sleep 1;\n" +
            "            sleep 10; echo 'hello'; \n" +
            "          }\n" +
            "        }, 'otherunit': {\n" +
            "            retry(1) {\n" +
            "              sleep 1;\n" +
            "              sleep 5; \n" +
            "              echo 'goodbye'   \n" +
            "            }\n" +
            "        }",
            true));
        /*Node dump follows, format:
        [ID]{parent,ids}(millisSinceStartOfRun) flowNodeClassName stepDisplayName [st=startId if a block end node]
        Action format:
        - actionClassName actionDisplayName
        ------------------------------------------------------------------------------------------
        [2]{}FlowStartNode Start of Pipeline
        [3]{2}StepAtomNode test
        [4]{3}StepStartNode Execute in parallel : Start
        [6]{4}StepStartNode Branch: unit
        [7]{4}StepStartNode Branch: otherunit
            A [8]{6}StepStartNode Retry the body up to N times : Start
            A [9]{8}StepStartNode Retry the body up to N times : Body : Start
          B [10]{7}StepStartNode Retry the body up to N times : Start
          B [11]{10}StepStartNode Retry the body up to N times : Body : Start
            A [12]{9}StepAtomNode Sleep
          B [13]{11}StepAtomNode Sleep
            A [14]{12}StepAtomNode Sleep
          B [15]{13}StepAtomNode Sleep
          B [16]{15}StepAtomNode Print Message
          B [17]{16}StepEndNode Retry the body up to N times : Body : End  [st=11]
          B [18]{17}StepEndNode Retry the body up to N times : End  [st=10]
          B [19]{18}StepEndNode Execute in parallel : Body : End  [st=7]
            A [20]{14}StepAtomNode Print Message
            A [21]{20}StepEndNode Retry the body up to N times : Body : End  [st=9]
            A [22]{21}StepEndNode Retry the body up to N times : End  [st=8]
            A [23]{22}StepEndNode Execute in parallel : Body : End  [st=6]
        [24]{23,19}StepEndNode Execute in parallel : End  [st=4]
        [25]{24}FlowEndNode End of Pipeline  [st=2]*/
        WorkflowRun b = r.assertBuildStatusSuccess(job.scheduleBuild2(0));
        FlowExecution exec = b.getExecution();
        ForkScanner scan = new ForkScanner();

        // Test different start points in branch A & B, 20 and 19 were one error case.
        for (int i=0; i < 4; i++) {
            for (int j=0; j<5; j++) {
                int branchANodeId = i+20;
                int branchBNodeId = j+15;
                System.out.println("Starting test with nodes "+branchANodeId+","+branchBNodeId);
                ArrayList<FlowNode> starts = new ArrayList<>();
                FlowTestUtils.addNodesById(starts, exec, branchANodeId, branchBNodeId);
                List<FlowNode> all = scan.filteredNodes(starts, (Predicate<FlowNode>) x -> true);
                Assert.assertEquals(new HashSet<>(all).size(), all.size());
                scan.reset();
            }
        }
    }

    @Test
    @Issue("JENKINS-42895")
    public void testMissingHeadErrorWithZeroBranchParallel() throws Exception {
        WorkflowJob job = r.jenkins.createProject(WorkflowJob.class, "MissingHeadBug");
        job.setDefinition(new CpsFlowDefinition("" +
                "stage('Stage A') {\n" +
                "    echo \"A\"\n" +
                "}\n" +
                "// Works\n" +
                "stage('Stage B') {\n" +
                "    parallel a: {\n" +
                "        echo \"B.A\"\n" +
                "    }, b: {\n" +
                "        echo \"B.B\"\n" +
                "    }\n" +
                "}\n" +
                "// Breaks\n" +
                "stage('Stage C') {\n" +
                "    def steps = [:]\n" +
                "    // Empty map\n" +
                "    parallel steps\n" +
                "}\n", true));
        WorkflowRun run = r.buildAndAssertSuccess(job);
        FlowExecution exec = run.getExecution();
        sanityTestIterationAndVisiter(exec.getCurrentHeads());
    }

    @Test
    public void testParallelPredicate() throws Exception {
        FlowExecution exec = SIMPLE_PARALLEL_RUN.getExecution();
        Assert.assertTrue(new ForkScanner.IsParallelStartPredicate().test(exec.getNode("4")));
        Assert.assertFalse(new ForkScanner.IsParallelStartPredicate().test(exec.getNode("6")));
        Assert.assertFalse(new ForkScanner.IsParallelStartPredicate().test(exec.getNode("8")));
    }

    @Test
    public void testGetNodeType() throws Exception {
        FlowExecution exec = SIMPLE_PARALLEL_RUN.getExecution();
        Assert.assertEquals(ForkScanner.NodeType.NORMAL, ForkScanner.getNodeType(exec.getNode("2")));
        Assert.assertEquals(ForkScanner.NodeType.PARALLEL_START, ForkScanner.getNodeType(exec.getNode("4")));
        Assert.assertEquals(ForkScanner.NodeType.PARALLEL_BRANCH_START, ForkScanner.getNodeType(exec.getNode("6")));
        Assert.assertEquals(ForkScanner.NodeType.PARALLEL_BRANCH_END, ForkScanner.getNodeType(exec.getNode("9")));
        Assert.assertEquals(ForkScanner.NodeType.PARALLEL_END, ForkScanner.getNodeType(exec.getNode("13")));

        Assert.assertEquals(ForkScanner.NodeType.NORMAL, ForkScanner.getNodeType(exec.getNode("8")));
    }

    /** For nodes, see {@link #SIMPLE_PARALLEL_RUN} */
    @Test
    public void testSimpleVisitor() throws Exception {
        FlowExecution exec = this.SIMPLE_PARALLEL_RUN.getExecution();
        ForkScanner f = new ForkScanner();
        f.setup(exec.getCurrentHeads());
        Assert.assertArrayEquals(new HashSet(exec.getCurrentHeads()).toArray(), new HashSet(f.currentParallelHeads()).toArray());
        List<FlowNode> expectedHeads = f.currentParallelHeads();

        sanityTestIterationAndVisiter(exec.getCurrentHeads());

        TestVisitor visitor = new TestVisitor();
        f.visitSimpleChunks(visitor, new BlockChunkFinder());

        // 13 calls for chunk/atoms, 6 for parallels
        Assert.assertEquals(19, visitor.calls.size());

        // End has nothing after it, just last node (15)
        TestVisitor.CallEntry last = new TestVisitor.CallEntry(TestVisitor.CallType.CHUNK_END, 15, -1, -1, -1);
        last.assertEquals(visitor.calls.get(0));

        // Start has nothing before it, just the first node (2)
        TestVisitor.CallEntry first = new TestVisitor.CallEntry(TestVisitor.CallType.CHUNK_START, 2, -1, -1, -1);
        first.assertEquals(visitor.calls.get(18));

        long chunkStartCount =
                visitor.calls.stream()
                        .filter(predicateForCallEntryType(TestVisitor.CallType.CHUNK_START))
                        .count();
        long chunkEndCount =
                visitor.calls.stream()
                        .filter(predicateForCallEntryType(TestVisitor.CallType.CHUNK_END))
                        .count();
        Assert.assertEquals(4L, chunkStartCount);
        Assert.assertEquals(4L, chunkEndCount);

        // Verify the AtomNode calls are correct
        List<TestVisitor.CallEntry> atomNodeCalls =
                visitor.calls.stream()
                        .filter(predicateForCallEntryType(TestVisitor.CallType.ATOM_NODE))
                        .collect(Collectors.toList());
        Assert.assertEquals(5, atomNodeCalls.size());
        for (TestVisitor.CallEntry ce : atomNodeCalls) {
            int beforeId = ce.ids[0];
            int atomNodeId = ce.ids[1];
            int afterId = ce.ids[2];
            int alwaysEmpty = ce.ids[3];
            Assert.assertTrue(ce+" beforeNodeId <= 0: "+beforeId, beforeId > 0);
            Assert.assertTrue(ce + " atomNodeId <= 0: " + atomNodeId, atomNodeId > 0);
            Assert.assertTrue(ce+" afterNodeId <= 0: "+afterId, afterId > 0);
            Assert.assertEquals(-1, alwaysEmpty);
            Assert.assertTrue(ce + "AtomNodeId >= afterNodeId", atomNodeId < afterId);
            Assert.assertTrue(ce+ "beforeNodeId >= atomNodeId", beforeId < atomNodeId);
        }

        List<TestVisitor.CallEntry> parallelCalls = visitor.calls.stream()
                .filter(input ->
                        input.type != null
                                && input.type != TestVisitor.CallType.ATOM_NODE
                                && input.type != TestVisitor.CallType.CHUNK_START
                                && input.type != TestVisitor.CallType.CHUNK_END)
                .collect(Collectors.toList());
        Assert.assertEquals(6, parallelCalls.size());
        // Start to end
        new TestVisitor.CallEntry(TestVisitor.CallType.PARALLEL_END, 4, 13).assertEquals(parallelCalls.get(0));

        //Tests for parallel handling
        // Start to end, in reverse order

        new TestVisitor.CallEntry(TestVisitor.CallType.PARALLEL_BRANCH_END, 4, 12).assertEquals(parallelCalls.get(1));
        new TestVisitor.CallEntry(TestVisitor.CallType.PARALLEL_BRANCH_START, 4, 7).assertEquals(parallelCalls.get(2));
        new TestVisitor.CallEntry(TestVisitor.CallType.PARALLEL_BRANCH_END, 4, 9).assertEquals(parallelCalls.get(3));

        new TestVisitor.CallEntry(TestVisitor.CallType.PARALLEL_BRANCH_START, 4, 6).assertEquals(parallelCalls.get(4));
        new TestVisitor.CallEntry(TestVisitor.CallType.PARALLEL_START, 4, 6).assertEquals(parallelCalls.get(5));
    }

    /** Checks for off-by one cases with multiple parallel, and with the leastCommonAncestor */
    @Test
    public void testTripleParallel() throws Exception {
        WorkflowJob job = r.jenkins.createProject(WorkflowJob.class, "TripleParallel");
        job.setDefinition(new CpsFlowDefinition(
                "stage 'test'\n"+   // Id 3, Id 2 before that has the FlowStartNode
                "parallel 'unit':{\n" + // Id 4 starts parallel, Id 7 is the block start for the unit branch
                "  echo \"Unit testing...\"\n" + // Id 10
                "},'integration':{\n" + // Id 11 is unit branch end, Id 8 is the branch start for integration branch
                "    echo \"Integration testing...\"\n" + // Id 12
                "}, 'ui':{\n" +  // Id 13 in integration branch end, Id 9 is branch start for UI branch
                "    echo \"UI testing...\"\n" + // Id 14
                "}", // Node 15 is UI branch end node, Node 16 is Parallel End node, Node 17 is FlowWendNode
                true));
        WorkflowRun b = r.assertBuildStatusSuccess(job.scheduleBuild2(0));
        FlowExecution exec = b.getExecution();
        ForkScanner f = new ForkScanner();
        List<FlowNode> heads = exec.getCurrentHeads();
        f.setup(heads);
        TestVisitor visitor = new TestVisitor();
        f.visitSimpleChunks(visitor, new BlockChunkFinder());
        sanityTestIterationAndVisiter(heads);

        List<TestVisitor.CallEntry> parallels = visitor.calls.stream()
                .filter(
                        predicateForCallEntryType(TestVisitor.CallType.PARALLEL_BRANCH_START)
                                .or(
                                        predicateForCallEntryType(TestVisitor.CallType.PARALLEL_BRANCH_END)))
                .collect(Collectors.toList());
        Assert.assertEquals(6, parallels.size());

        // Visiting from partially completed branches
        // Verify we still get appropriate parallels callbacks for a branch end
        //   even if in-progress and no explicit end node
        ArrayList<FlowNode> ends = new ArrayList<>();
        ends.add(exec.getNode("11"));
        ends.add(exec.getNode("12"));
        ends.add(exec.getNode("14"));
        Assert.assertEquals(new DepthFirstScanner().allNodes(ends).size(),
                new ForkScanner().allNodes(ends).size());
        visitor = new TestVisitor();
        f.setup(ends);
        f.visitSimpleChunks(visitor, new BlockChunkFinder());
        sanityTestIterationAndVisiter(ends);

        // Specifically test parallel structures
        parallels = visitor.calls.stream()
                .filter(
                        predicateForCallEntryType(TestVisitor.CallType.PARALLEL_BRANCH_START)
                                .or(
                                        predicateForCallEntryType(TestVisitor.CallType.PARALLEL_BRANCH_END)))
                .collect(Collectors.toList());
        Assert.assertEquals(6, parallels.size());
        Assert.assertEquals(18, visitor.calls.size());

        // Test the least common ancestor implementation with triplicate
        FlowNode[] branchHeads = {exec.getNode("7"), exec.getNode("8"), exec.getNode("9")};
        ArrayDeque<ForkScanner.ParallelBlockStart> starts = f.leastCommonAncestor(new HashSet<>(Arrays.asList(branchHeads)));
        Assert.assertEquals(1, starts.size());
        ForkScanner.ParallelBlockStart pbs = starts.pop();
        Assert.assertEquals(exec.getNode("4"), pbs.forkStart);
        Assert.assertEquals(3, pbs.unvisited.size());
        Assert.assertTrue(pbs.unvisited.contains(exec.getNode("7")));
        Assert.assertTrue(pbs.unvisited.contains(exec.getNode("8")));
        Assert.assertTrue(pbs.unvisited.contains(exec.getNode("9")));
    }

    private void testParallelFindsLast(WorkflowJob job, String semaphoreName) throws Exception {
        ForkScanner scan = new ForkScanner();
        ChunkFinder labelFinder = new LabelledChunkFinder();

        System.out.println("Testing that semaphore step is always the last step for chunk with "+job.getName());
        WorkflowRun run  = job.scheduleBuild2(0).getStartCondition().get();
        SemaphoreStep.waitForStart(semaphoreName+"/1", run);

        FlowNode semaphoreNode = run.getExecution().getCurrentHeads().stream()
            .filter(new NodeStepTypePredicate("semaphore")).findFirst().orElse(null);

        TestVisitor visitor = new TestVisitor();
        List<FlowNode> heads = run.getExecution().getCurrentHeads();
        scan.setup(heads);

        // Check the right number of branches are set up
        Assert.assertEquals(run.getExecution().getCurrentHeads().size()-1, scan.currentParallelStart.unvisited.size());

        // Check visitor handling for parallel end
        scan.visitSimpleChunks(visitor, labelFinder);
        TestVisitor.CallEntry parallelEnd = visitor.calls.get(0);
        Assert.assertEquals(TestVisitor.CallType.PARALLEL_END, parallelEnd.type);
        Assert.assertEquals("Wrong End Node: ("+parallelEnd.getNodeId()+")", semaphoreNode.getId(), parallelEnd.getNodeId().toString());
        Assert.assertEquals(semaphoreNode.getId(), parallelEnd.getNodeId().toString());

        SemaphoreStep.success(semaphoreName+"/1", null);
        r.waitForCompletion(run);
        sanityTestIterationAndVisiter(heads);
    }

    /** Reproduce issues with in-progress parallels */
    @Test
    @Issue("JENKINS-41685")
    public void testParallelsWithDuplicateEvents() throws Exception {
        //https://gist.github.com/vivek/ccf3a4ef25fbff267c76c962d265041d
        WorkflowJob job = r.jenkins.createProject(WorkflowJob.class, "ParallelInsanity");
        job.setDefinition(new CpsFlowDefinition("" +
                "stage \"first\"\n" +
                "parallel left : {\n" +
                "  echo 'run a bit'\n" +
                "  echo 'run a bit more'\n" +
                "  semaphore 'wait1'\n" +
                "}, right : {\n" +
                "  echo 'wozzle'\n" +
                "  semaphore 'wait2'\n" +
                "}\n" +
                "stage \"last\"\n" +
                "echo \"last done\"\n",
                true));
        ForkScanner scan = new ForkScanner();
        ChunkFinder labelFinder = new NoOpChunkFinder();
        WorkflowRun run  = job.scheduleBuild2(0).getStartCondition().get();
        SemaphoreStep.waitForStart("wait1/1", run);
        SemaphoreStep.waitForStart("wait2/1", run);

        TestVisitor test = new TestVisitor();
        List<FlowNode> heads = run.getExecution().getCurrentHeads();
        scan.setup(heads);
        scan.visitSimpleChunks(test, labelFinder);

        SemaphoreStep.success("wait1"+"/1", null);
        SemaphoreStep.success("wait2"+"/1", null);
        r.waitForCompletion(run);

        int atomEventCount = 0;
        int parallelBranchEndCount = 0;
        int parallelStartCount = 0;
        for (TestVisitor.CallEntry ce : test.calls) {
            switch (ce.type) {
                case ATOM_NODE:
                    atomEventCount++;
                    break;
                case PARALLEL_BRANCH_END:
                    parallelBranchEndCount++;
                    break;
                case PARALLEL_START:
                    parallelStartCount++;
                    break;
                default:
                    break;
            }
        }

        sanityTestIterationAndVisiter(heads);
        Assert.assertEquals(10, atomEventCount);
        Assert.assertEquals(1, parallelStartCount);
        Assert.assertEquals(2, parallelBranchEndCount);
    }

    /** Covers finding the right parallel end node in cases where we have one long-running step on an incomplete branch.
     *  Previously we'd assume the BlockEndNode of the completed branch was the last-running branch because it had the
     *   most recent node addition, but the longer-running non-end node needs to take precedence.
     */
    @Issue("JENKINS-38536")
    @Test
    public void testPartlyCompletedParallels() throws Exception {
        String jobScript = ""+
                "stage 'first'\n" +
                "parallel 'long' : { sleep 60; }, \n" +  // Needs to be in-progress
                "         'short': { sleep 2; }";  // Needs to have completed, and SemaphoreStep alone doesn't cut it

        // This must be amateur science fiction because the exposition for the setting goes on FOREVER
        ForkScanner scan = new ForkScanner();
        TestVisitor tv = new TestVisitor();
        WorkflowJob job = r.jenkins.createProject(WorkflowJob.class, "parallelTimes");
        job.setDefinition(new CpsFlowDefinition(jobScript, true));
        WorkflowRun run = job.scheduleBuild2(0).getStartCondition().get();
        Thread.sleep(4000);  // Allows enough time for the shorter branch to finish and write its BlockEndNode
        FlowExecution exec = run.getExecution();
        List<FlowNode> heads = exec.getCurrentHeads();
        scan.setup(heads);
        scan.visitSimpleChunks(tv, new NoOpChunkFinder());
        FlowNode endNode = exec.getNode(tv.filteredCallsByType(TestVisitor.CallType.PARALLEL_END).get(0).getNodeId().toString());
        Assert.assertEquals("sleep", endNode.getDisplayFunctionName());
        sanityTestIterationAndVisiter(heads);
        run.doKill(); // Avoid waiting for long branch completion
    }

    /** Covers finding the right parallel end node in cases we have not written a TimingAction or are using SemaphoreStep */
    @Issue("JENKINS-38536")
    @Test
    public void testParallelCorrectEndNodeForVisitor() throws Exception {
        // Verify that SimpleBlockVisitor actually gets the *real* last node not just the last declared branch
        WorkflowJob jobPauseFirst = r.jenkins.createProject(WorkflowJob.class, "PauseFirst");
        jobPauseFirst.setDefinition(new CpsFlowDefinition("" +
                "stage 'primero'\n" +
                "parallel 'wait' : {sleep 1; semaphore 'wait1';}, \n" +
                " 'final': { echo 'succeed';} ",
                true));

        WorkflowJob jobPauseSecond = r.jenkins.createProject(WorkflowJob.class, "PauseSecond");
        jobPauseSecond.setDefinition(new CpsFlowDefinition("" +
                "stage 'primero'\n" +
                "parallel 'success' : {echo 'succeed'}, \n" +
                " 'pause':{ sleep 1; semaphore 'wait2'; }\n",
                true));

        WorkflowJob jobPauseMiddle = r.jenkins.createProject(WorkflowJob.class, "PauseMiddle");
        jobPauseMiddle.setDefinition(new CpsFlowDefinition("" +
                "stage 'primero'\n" +
                "parallel 'success' : {echo 'succeed'}, \n" +
                " 'pause':{ sleep 1; semaphore 'wait3'; }, \n" +
                " 'final': { echo 'succeed-final';} ",
                true));
        testParallelFindsLast(jobPauseFirst, "wait1");
        testParallelFindsLast(jobPauseSecond, "wait2");
        testParallelFindsLast(jobPauseMiddle, "wait3");
    }
}

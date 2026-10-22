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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterators;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.FlowGraphWalker;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.BuildWatcherExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Tests for all the core parts of graph analysis except the ForkScanner, internals which is complex enough to merit its own tests
 * @author Sam Van Oort
 */
@WithJenkins
class FlowScannerTest {

    @RegisterExtension
    private static final BuildWatcherExtension buildWatcher = new BuildWatcherExtension();

    private JenkinsRule r;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        r = rule;
    }

    /** Tests the core logic separately from each implementation's scanner */
    @Test
    void testAbstractScanner() throws Exception {
        WorkflowJob job = r.jenkins.createProject(WorkflowJob.class, "SimpleLinear");
        job.setDefinition(new CpsFlowDefinition(
                """
                        sleep 2
                        echo 'donothing'
                        echo 'doitagain'""",
                true));

        /* Flow structure (ID - type)
         2 - FlowStartNode
         3 - SleepStep
         4 - EchoStep
         5 - EchoStep
         6 - FlowEndNode
         */

        WorkflowRun b = r.assertBuildStatusSuccess(job.scheduleBuild2(0));
        FlowExecution exec = b.getExecution();
        List<FlowNode> heads = exec.getCurrentHeads();
        FlowNode intermediateNode = exec.getNode("4");
        AbstractFlowScanner linear = new LinearScanner();

        // ## Bunch of tests for convertToFastCheckable ##
        assertEquals(Collections.emptySet(), linear.convertToFastCheckable(null));
        assertEquals(Collections.emptySet(), linear.convertToFastCheckable(new ArrayList<>()));

        Collection<FlowNode> coll = linear.convertToFastCheckable(Collections.singletonList(intermediateNode));
        assertInstanceOf(AbstractSet.class, coll, "Singleton set used for one element");
        assertEquals(1, coll.size());

        Collection<FlowNode> multipleItems = Arrays.asList(exec.getNode("3"), exec.getNode("2"));
        coll = linear.convertToFastCheckable(multipleItems);
        assertInstanceOf(List.class, coll, "Original used for short list");
        assertEquals(2, coll.size());

        coll = linear.convertToFastCheckable(new LinkedHashSet<>(multipleItems));
        assertInstanceOf(LinkedHashSet.class, coll, "Original used where set");

        multipleItems = new ArrayList<>();
        for (int i=0; i < 3; i++) {
            multipleItems.add(intermediateNode);
        }
        coll = linear.convertToFastCheckable(multipleItems);
        assertInstanceOf(List.class, coll, "Original used for short list");
        assertEquals(3, coll.size());

        multipleItems = new ArrayList<>();
        for (int i=0; i < 10; i++) {
            multipleItems.add(intermediateNode);
        }
        coll = linear.convertToFastCheckable(multipleItems);
        assertInstanceOf(HashSet.class, coll, "Original used for short list");
        assertEquals(1, coll.size());


        // Setup, return false if no nodes to iterate, else true
        FlowNode lastNode = heads.get(0);
        FlowNode nullNode = null;
        Collection<FlowNode> nullColl = null;

        assertTrue(linear.setup(heads, null));
        assertTrue(linear.setup(heads, Collections.emptySet()));
        assertFalse(linear.setup(nullColl, heads));
        assertFalse(linear.setup(nullColl, null));
        assertFalse(linear.setup(heads, heads));
        assertTrue(linear.setup(heads));
        assertFalse(linear.setup(nullColl));
        assertFalse(linear.setup(Collections.emptySet()));
        assertTrue(linear.setup(lastNode));
        assertTrue(linear.setup(lastNode, nullColl));
        assertFalse(linear.setup(nullNode));
        assertFalse(linear.setup(nullNode, heads));
        assertFalse(linear.setup(nullNode, nullColl));
        assertTrue(linear.setup(Arrays.asList(intermediateNode, lastNode), Collections.singleton(intermediateNode)));
        assertEquals(lastNode, linear.myCurrent);

        // First match, with no blacklist
        int[] ids = {6, 5, 4, 3, 2};
        FlowNode firstEchoNode = exec.getNode("5");
        FlowExecution nullExecution = null;

        assertEquals(firstEchoNode, linear.findFirstMatch(heads, Collections.emptySet(), FlowTestUtils.MATCH_ECHO_STEP));
        assertEquals(firstEchoNode, linear.findFirstMatch(heads, FlowTestUtils.MATCH_ECHO_STEP));
        assertEquals(firstEchoNode, linear.findFirstMatch(lastNode, FlowTestUtils.MATCH_ECHO_STEP));
        assertEquals(firstEchoNode, linear.findFirstMatch(exec, FlowTestUtils.MATCH_ECHO_STEP));
        assertNull(linear.findFirstMatch(nullColl, FlowTestUtils.MATCH_ECHO_STEP));
        assertNull(linear.findFirstMatch(Collections.emptySet(), FlowTestUtils.MATCH_ECHO_STEP));
        assertNull(linear.findFirstMatch(nullNode, FlowTestUtils.MATCH_ECHO_STEP));
        assertNull(linear.findFirstMatch(nullExecution, FlowTestUtils.MATCH_ECHO_STEP));


        // Filtered nodes
        FlowTestUtils.assertNodeOrder("Filtered echo nodes", linear.filteredNodes(heads, FlowTestUtils.MATCH_ECHO_STEP), 5, 4);
        FlowTestUtils.assertNodeOrder("Filtered echo nodes", linear.filteredNodes(heads, Collections.singleton(intermediateNode), FlowTestUtils.MATCH_ECHO_STEP), 5);
        assertEquals(0, linear.filteredNodes(heads, null, Predicates.alwaysFalse()).size());
        assertEquals(0, linear.filteredNodes(nullNode, FlowTestUtils.MATCH_ECHO_STEP).size());
        assertEquals(0, linear.filteredNodes(Collections.emptySet(), FlowTestUtils.MATCH_ECHO_STEP).size());

        // Same filter using the filterator
        linear.setup(heads);
        ArrayList<FlowNode> collected = new ArrayList<>();
        Filterator<FlowNode> filt = linear.filter(FlowTestUtils.MATCH_ECHO_STEP);
        while (filt.hasNext()) {
            collected.add(filt.next());
        }
        FlowTestUtils.assertNodeOrder("Filterator filtered echo nodes", collected, 5, 4);


        // Visitor pattern tests
        FlowTestUtils.CollectingVisitor visitor = new FlowTestUtils.CollectingVisitor();
        linear.visitAll(Collections.emptySet(), visitor);
        assertEquals(0, visitor.getVisited().size());
        visitor.reset();

        linear.visitAll(heads, visitor);
        FlowTestUtils.assertNodeOrder("Visiting all nodes", visitor.getVisited(), 6, 5, 4, 3, 2);

        // And visiting with blacklist
        visitor.reset();
        linear.visitAll(heads, Collections.singleton(intermediateNode), visitor);
        FlowTestUtils.assertNodeOrder("Visiting all nodes with blacklist", visitor.getVisited(), 6, 5);

        // Tests for edge cases of the various basic APIs
        linear.myNext = null;
        assertFalse(linear.hasNext());
        try {
            linear.next();
            fail("Should throw NoSuchElement exception");
        } catch (NoSuchElementException nsee) {
            // Passing case
        }
        assertSame(linear.iterator(), linear);
        try {
            linear.remove();
            fail("Should throw UnsupportedOperation exception");
        } catch (UnsupportedOperationException usoe) {
            // Passing case
        }
    }

    /** Tests the basic scan algorithm, predicate use, start/stop nodes */
    @Test
    void testSimpleScan() throws Exception {
        WorkflowJob job = r.jenkins.createProject(WorkflowJob.class, "Convoluted");
        job.setDefinition(new CpsFlowDefinition(
                """
                        sleep 2
                        echo 'donothing'
                        echo 'doitagain'""",
                true));

        /* Flow structure (ID - type)
         2 - FlowStartNode
         3 - SleepStep
         4 - EchoStep
         5 - EchoStep
         6 - FlowEndNode
         */

        WorkflowRun b = r.assertBuildStatusSuccess(job.scheduleBuild2(0));
        FlowExecution exec = b.getExecution();
        AbstractFlowScanner[] scans = {new LinearScanner(),
                new DepthFirstScanner(),
                new ForkScanner()
        };

        List<FlowNode> heads = exec.getCurrentHeads();

        // Iteration tests
        for (AbstractFlowScanner scan : scans) {
            System.out.println("Iteration test with scanner: " + scan.getClass());
            scan.setup(heads, null);
            FlowTestUtils.assertNodeOrder("Testing full scan for scanner " + scan.getClass(), scan, 6, 5, 4, 3, 2);
            assertFalse(scan.hasNext());

            // Blacklist tests
            scan.setup(heads, Collections.singleton(exec.getNode("4")));
            FlowTestUtils.assertNodeOrder("Testing full scan for scanner " + scan.getClass(), scan, 6, 5);
            FlowNode f = scan.findFirstMatch(heads, Collections.singleton(exec.getNode("6")), Predicates.alwaysTrue());
            assertNull(f);
        }
    }

    /** Tests the basic scan algorithm where blocks are involved */
    @Test
    void testBasicScanWithBlock() throws Exception {
        WorkflowJob job = r.jenkins.createProject(WorkflowJob.class, "Convoluted");
        job.setDefinition(new CpsFlowDefinition(
                """
                        echo 'first'
                        timeout(time: 10, unit: 'SECONDS') {
                            echo 'second'
                            echo 'third'
                        }
                        sleep 1""",
            true));
        /* Flow structure (ID - type)
         2 - FlowStartNode
         3 - EchoStep
         4 - TimeoutStep
         5 - TimeoutStep with BodyInvocationAction
         6 - EchoStep
         7 - EchoStep
         8 - StepEndNode (BlockEndNode), startId=5
         9 - StepEndNode (BlockEndNode), startId = 4
         10 - SleepStep
         11 - FlowEndNode
         */

        WorkflowRun b = r.assertBuildStatusSuccess(job.scheduleBuild2(0));
        Predicate<FlowNode> matchEchoStep = FlowTestUtils.predicateMatchStepDescriptor("org.jenkinsci.plugins.workflow.steps.EchoStep");
        FlowExecution exec = b.getExecution();
        Collection<FlowNode> heads = exec.getCurrentHeads();

        // Linear analysis
        LinearScanner linearScanner = new LinearScanner();
        linearScanner.setup(heads);
        FlowTestUtils.assertNodeOrder("Linear scan with block", linearScanner, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2);
        linearScanner.setup(exec.getNode("7"));
        FlowTestUtils.assertNodeOrder("Linear scan with block from middle ", linearScanner, 7, 6, 5, 4, 3, 2);

        LinearBlockHoppingScanner linearBlockHoppingScanner = new LinearBlockHoppingScanner();

        // // Test block jump core
        FlowNode headCandidate = exec.getNode("8");
        assertEquals(exec.getNode("4"), linearBlockHoppingScanner.jumpBlockScan(headCandidate, Collections.emptySet()));
        assertTrue(linearBlockHoppingScanner.setup(headCandidate, null), "Setup should return true if we can iterate");

        // Test the actual iteration
        linearBlockHoppingScanner.setup(heads);
        assertFalse(linearBlockHoppingScanner.hasNext());
        linearBlockHoppingScanner.setup(exec.getNode("8"));
        FlowTestUtils.assertNodeOrder("Hopping over one block", linearBlockHoppingScanner, 4, 3, 2);
        linearBlockHoppingScanner.setup(exec.getNode("7"));
        FlowTestUtils.assertNodeOrder("Hopping over one block", linearBlockHoppingScanner, 7, 6, 5, 4, 3, 2);

        // Test the black list in combination with hopping
        linearBlockHoppingScanner.setup(exec.getNode("8"), Collections.singleton(exec.getNode("5")));
        assertFalse(linearBlockHoppingScanner.hasNext());
        linearBlockHoppingScanner.setup(exec.getNode("8"), Collections.singleton(exec.getNode("4")));
        assertFalse(linearBlockHoppingScanner.hasNext());
    }


    /** And the parallel case */
    @Test
    void testParallelScan() throws Exception {
        WorkflowJob job = r.jenkins.createProject(WorkflowJob.class, "Convoluted");
        job.setDefinition(new CpsFlowDefinition(
                """
                        echo 'first'
                        def steps = [:]
                        steps['1'] = {
                            echo 'do 1 stuff'
                        }
                        steps['2'] = {
                            echo '2a'
                            echo '2b'
                        }
                        parallel steps
                        echo 'final'""",
            true));

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

        WorkflowRun b = r.assertBuildStatusSuccess(job.scheduleBuild2(0));
        FlowExecution exec = b.getExecution();
        Collection<FlowNode> heads = b.getExecution().getCurrentHeads();

        AbstractFlowScanner scanner = new LinearScanner();
        scanner.setup(heads);
        FlowTestUtils.assertNodeOrder("Linear", scanner, 15, 14, 13, 9, 8, 6, 4, 3, 2);
        scanner.setup(heads, Collections.singleton(exec.getNode("9")));
        FlowTestUtils.assertNodeOrder("Linear", scanner, 15, 14, 13, 12, 11, 10, 7, 4, 3, 2);


        // Depth first scanner and with blacklist
        scanner = new DepthFirstScanner();
        scanner.setup(heads);

        // Compatibility test for ordering
        FlowTestUtils.assertNodeOrder("FlowGraphWalker", new FlowGraphWalker(exec), 15, 14, 13,
                9, 8, 6, // Branch 1
                4, 3, 2, // Before parallel
                12, 11, 10, 7); // Branch 2
        FlowTestUtils.assertNodeOrder("Depth first", new FlowGraphWalker(exec), 15, 14, 13,
                9, 8, 6, // Branch 1
                4, 3, 2, // Before parallel
                12, 11, 10, 7); // Branch 2
        scanner.setup(heads, Collections.singleton(exec.getNode("9")));
        FlowTestUtils.assertNodeOrder("Linear", scanner, 15, 14, 13, 12, 11, 10, 7, 4, 3, 2);

        scanner.setup(Arrays.asList(exec.getNode("9"), exec.getNode("12")));
        FlowTestUtils.assertNodeOrder("Depth-first scanner from inside parallels", scanner, 9, 8, 6, 4, 3, 2, 12, 11, 10, 7);

        // We're going to test the ForkScanner in more depth since this is its natural use
        scanner = new ForkScanner();
        scanner.setup(heads);
        FlowTestUtils.assertNodeOrder("ForkedScanner", scanner, 15, 14, 13,
                    12, 11, 10, 7,// One parallel
                    9, 8, 6, // other parallel
                4, 3, 2); // end bit
        scanner.setup(heads, Collections.singleton(exec.getNode("9")));
        FlowTestUtils.assertNodeOrder("ForkedScanner", scanner, 15, 14, 13, 12, 11, 10, 7, 4, 3, 2);

        // Test forkscanner midflow
        scanner.setup(exec.getNode("14"));
        FlowTestUtils.assertNodeOrder("ForkedScanner", scanner, 14, 13,
                    12, 11, 10, 7, // Last parallel
                    9, 8, 6, // First parallel
                4, 3, 2); // end bit

        // Test forkscanner inside a parallel
        List<FlowNode> startingPoints = Arrays.asList(exec.getNode("9"), exec.getNode("12"));
        scanner.setup(startingPoints);
        FlowTestUtils.assertNodeOrder("ForkedScanner", scanner, 9, 8, 6, 12, 11, 10, 7, 4, 3, 2);

        startingPoints = Arrays.asList(exec.getNode("9"), exec.getNode("11"));
        scanner.setup(startingPoints);
        FlowTestUtils.assertNodeOrder("ForkedScanner", scanner, 9, 8, 6, 11, 10, 7, 4, 3, 2);


        // Filtering at different points within branches
        List<FlowNode> blackList = Arrays.asList(exec.getNode("6"), exec.getNode("7"));
        assertEquals(4, scanner.filteredNodes(heads, blackList, FlowTestUtils.MATCH_ECHO_STEP).size());
        assertEquals(4, scanner.filteredNodes(heads, Collections.singletonList(exec.getNode("4")), FlowTestUtils.MATCH_ECHO_STEP).size());
        blackList = Arrays.asList(exec.getNode("6"), exec.getNode("10"));
        assertEquals(3, scanner.filteredNodes(heads, blackList, FlowTestUtils.MATCH_ECHO_STEP).size());
    }

    @Test
    void testNestedParallelScan() throws Exception {
        WorkflowJob job = r.jenkins.createProject(WorkflowJob.class, "Convoluted");
        job.setDefinition(new CpsFlowDefinition(
                """
                        echo 'first'
                        def steps = [:]
                        steps['1'] = {
                            echo 'do 1 stuff'
                        }
                        steps['2'] = {
                            echo '2a'
                            def nested = [:]
                            nested['2-1'] = {
                                echo 'do 2-1'
                            }
                            nested['2-2'] = {
                                sleep 1
                                echo '2 section 2'
                            }
                            echo '2b'
                            parallel nested
                        }
                        parallel steps
                        echo 'final'""",
                true));

        /* Parallel nested in parallel (ID-type)
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
         * 21 - StepEndNode (end inner parallel), parentIds=17,20, startId=12
         * 22 - StepEndNode (end parallel #2), parent=21, startId=7
         * 23 - StepEndNode (end outer parallel), parentIds=9,22, startId=4
         * 24 - Echo
         * 25 - FlowEndNode
         */

        WorkflowRun b = r.assertBuildStatusSuccess(job.scheduleBuild2(0));
        FlowExecution exec = b.getExecution();
        Collection<FlowNode> heads = b.getExecution().getCurrentHeads();

        // Basic test of DepthFirstScanner
        AbstractFlowScanner scanner = new DepthFirstScanner();
        Collection<FlowNode> matches = scanner.filteredNodes(heads, null, FlowTestUtils.MATCH_ECHO_STEP);
        assertEquals(7, matches.size());

        scanner.setup(heads);
        assertTrue(Iterators.elementsEqual(new FlowGraphWalker(exec).iterator(), scanner.iterator()), "FlowGraphWalker differs from DepthFirstScanner");


        // We're going to test the ForkScanner in more depth since this is its natural use
        scanner = new ForkScanner();
        matches = scanner.filteredNodes(heads, null, FlowTestUtils.MATCH_ECHO_STEP);
        assertEquals(7, matches.size());

        heads = Arrays.asList(exec.getNode("20"), exec.getNode("17"), exec.getNode("9"));
        matches = scanner.filteredNodes(heads, null, FlowTestUtils.MATCH_ECHO_STEP);
        assertEquals(6, matches.size()); // Commented out since temporarily failing
    }
}

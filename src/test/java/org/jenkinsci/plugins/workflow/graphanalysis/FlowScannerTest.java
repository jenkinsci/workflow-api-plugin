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

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.sun.tools.javac.comp.Flow;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.nodes.StepAtomNode;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graphanalysis.DepthFirstScanner;
import org.jenkinsci.plugins.workflow.graphanalysis.FlowNodeVisitor;
import org.jenkinsci.plugins.workflow.graphanalysis.ForkScanner;
import org.jenkinsci.plugins.workflow.graphanalysis.LinearBlockHoppingScanner;
import org.jenkinsci.plugins.workflow.graphanalysis.LinearScanner;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;
import org.jenkinsci.plugins.workflow.graphanalysis.AbstractFlowScanner;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class FlowScannerTest {

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @Rule public JenkinsRule r = new JenkinsRule();

    public static Predicate<FlowNode> predicateMatchStepDescriptor(@Nonnull final String descriptorId) {
        Predicate<FlowNode> outputPredicate = new Predicate<FlowNode>() {
            @Override
            public boolean apply(FlowNode input) {
                if (input instanceof StepAtomNode) {
                    StepAtomNode san = (StepAtomNode)input;
                    StepDescriptor sd = san.getDescriptor();
                    return sd != null && descriptorId.equals(sd.getId());
                }
                return false;
            }
        };
        return outputPredicate;
    }

    Predicate<FlowNode> MATCH_ECHO_STEP = predicateMatchStepDescriptor("org.jenkinsci.plugins.workflow.steps.EchoStep");

    static final class CollectingVisitor implements FlowNodeVisitor {
        ArrayList<FlowNode> visited = new ArrayList<FlowNode>();

        @Override
        public boolean visit(@Nonnull FlowNode f) {
            visited.add(f);
            return true;
        }

        public void reset() {
            this.visited.clear();
        }

        public ArrayList<FlowNode> getVisited() {
            return visited;
        }
    };

    /** Tests the basic scan algorithm, predicate use, start/stop nodes */
    @Test
    public void testSimpleScan() throws Exception {
        WorkflowJob job = r.jenkins.createProject(WorkflowJob.class, "Convoluted");
        job.setDefinition(new CpsFlowDefinition(
                "sleep 2 \n" +
                "echo 'donothing'\n" +
                "echo 'doitagain'"
        ));

        /** Flow structure (ID - type)
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
            System.out.println("Iteration test with scanner: "+scan.getClass());
            scan.setup(heads, null);

            for (int i=6; i>2; i--) {
                Assert.assertTrue(scan.hasNext());
                FlowNode f = scan.next();
                Assert.assertEquals(Integer.toString(i), f.getId());
            }

            FlowNode f2 = scan.next();
            Assert.assertFalse(scan.hasNext());
            Assert.assertEquals("2", f2.getId());
        }

        // Block Hopping tests
        LinearBlockHoppingScanner scanner = new LinearBlockHoppingScanner();
        Assert.assertFalse("BlockHopping scanner jumps over the flow when started at end", scanner.setup(heads, Collections.EMPTY_SET));
        List<FlowNode> collectedNodes = scanner.filteredNodes(Collections.singleton(exec.getNode("5")), null, (Predicate)Predicates.alwaysTrue());
        Assert.assertEquals(exec.getNode("5"), collectedNodes.get(0));
        Assert.assertEquals(exec.getNode("4"), collectedNodes.get(1));
        Assert.assertEquals(exec.getNode("3"), collectedNodes.get(2));
        Assert.assertEquals(exec.getNode("2"), collectedNodes.get(3));

        // Test expected scans with no stop nodes given (different ways of specifying none)
        for (AbstractFlowScanner sa : scans) {
            System.out.println("Testing class: "+sa.getClass());
            FlowNode node = sa.findFirstMatch(heads, null, MATCH_ECHO_STEP);
            Assert.assertEquals(exec.getNode("5"), node);
            node = sa.findFirstMatch(heads, Collections.EMPTY_LIST, MATCH_ECHO_STEP);
            Assert.assertEquals(exec.getNode("5"), node);
            node = sa.findFirstMatch(heads, Collections.EMPTY_SET, MATCH_ECHO_STEP);
            Assert.assertEquals(exec.getNode("5"), node);

            Collection<FlowNode> nodeList = sa.filteredNodes(heads, null, MATCH_ECHO_STEP);
            FlowNode[] expected = new FlowNode[]{exec.getNode("5"), exec.getNode("4")};
            Assert.assertArrayEquals(expected, nodeList.toArray());
            nodeList = sa.filteredNodes(heads, Collections.EMPTY_LIST, MATCH_ECHO_STEP);
            Assert.assertArrayEquals(expected, nodeList.toArray());
            nodeList = sa.filteredNodes(heads, Collections.EMPTY_SET, MATCH_ECHO_STEP);
            Assert.assertArrayEquals(expected, nodeList.toArray());
        }

        // Test with no matches
        for (AbstractFlowScanner sa : scans) {
            System.out.println("Testing class: "+sa.getClass());
            FlowNode node = sa.findFirstMatch(heads, null, (Predicate)Predicates.alwaysFalse());
            Assert.assertNull(node);

            Collection<FlowNode> nodeList = sa.filteredNodes(heads, null, (Predicate) Predicates.alwaysFalse());
            Assert.assertNotNull(nodeList);
            Assert.assertEquals(0, nodeList.size());
        }


        CollectingVisitor vis = new CollectingVisitor();
        // Verify we touch head and foot nodes too
        for (AbstractFlowScanner sa : scans) {
            System.out.println("Testing class: " + sa.getClass());
            Collection<FlowNode> nodeList = sa.filteredNodes(heads, null, (Predicate) Predicates.alwaysTrue());
            vis.reset();
            sa.visitAll(heads, vis);
            Assert.assertEquals(5, nodeList.size());
            Assert.assertEquals(5, vis.getVisited().size());
        }

        // Test with a stop node given, sometimes no matches
        Collection<FlowNode> noMatchEndNode = Collections.singleton(exec.getNode("5"));
        Collection<FlowNode> singleMatchEndNode = Collections.singleton(exec.getNode("4"));
        for (AbstractFlowScanner sa : scans) {
            FlowNode node = sa.findFirstMatch(heads, noMatchEndNode, MATCH_ECHO_STEP);
            Assert.assertNull(node);

            Collection<FlowNode> nodeList = sa.filteredNodes(heads, noMatchEndNode, MATCH_ECHO_STEP);
            Assert.assertNotNull(nodeList);
            Assert.assertEquals(0, nodeList.size());

            // Now we try with a stop list the reduces node set for multiple matches
            node = sa.findFirstMatch(heads, singleMatchEndNode, MATCH_ECHO_STEP);
            Assert.assertEquals(exec.getNode("5"), node);
            nodeList = sa.filteredNodes(heads, singleMatchEndNode, MATCH_ECHO_STEP);
            Assert.assertNotNull(nodeList);
            Assert.assertEquals(1, nodeList.size());
            Assert.assertEquals(exec.getNode("5"), nodeList.iterator().next());
        }
    }

    /** Tests the basic scan algorithm where blocks are involved */
    @Test
    public void testBasicScanWithBlock() throws Exception {
        WorkflowJob job = r.jenkins.createProject(WorkflowJob.class, "Convoluted");
        job.setDefinition(new CpsFlowDefinition(
            "echo 'first'\n" +
            "timeout(time: 10, unit: 'SECONDS') {\n" +
            "    echo 'second'\n" +
            "    echo 'third'\n" +
            "}\n" +
            "sleep 1"
        ));
        /** Flow structure (ID - type)
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
        Predicate<FlowNode> matchEchoStep = predicateMatchStepDescriptor("org.jenkinsci.plugins.workflow.steps.EchoStep");
        FlowExecution exec = b.getExecution();

        // Linear analysis
        LinearScanner linearScanner = new LinearScanner();
        Assert.assertEquals(3, linearScanner.filteredNodes(exec.getCurrentHeads(), null, matchEchoStep).size());
        Assert.assertEquals(3, linearScanner.filteredNodes(exec.getNode("7"), matchEchoStep).size());

        // Test blockhopping
        LinearBlockHoppingScanner linearBlockHoppingScanner = new LinearBlockHoppingScanner();
        Assert.assertEquals(0, linearBlockHoppingScanner.filteredNodes(exec.getCurrentHeads(), null, matchEchoStep).size()); //Hopped
        Assert.assertEquals(1, linearBlockHoppingScanner.filteredNodes(exec.getNode("8"), matchEchoStep).size());
        Assert.assertEquals(3, linearBlockHoppingScanner.filteredNodes(exec.getNode("7"), matchEchoStep).size());

        // Prove we covered all
        DepthFirstScanner depthFirstScanner = new DepthFirstScanner();
        Assert.assertEquals(3, depthFirstScanner.filteredNodes(exec.getCurrentHeads(), null, matchEchoStep).size());
        Assert.assertEquals(3, depthFirstScanner.filteredNodes(exec.getNode("7"), matchEchoStep).size());

        // Prove we covered all
        ForkScanner forkScanner = new ForkScanner();
        Assert.assertEquals(3, forkScanner.filteredNodes(exec.getCurrentHeads(), null, matchEchoStep).size());
        Assert.assertEquals(3, forkScanner.filteredNodes(exec.getNode("7"), matchEchoStep).size());
    }

    @Test
    public void blockJumpTest() throws Exception {
        WorkflowJob job = r.jenkins.createProject(WorkflowJob.class, "BlockUsing");
        job.setDefinition(new CpsFlowDefinition(
                "echo 'sample'\n" +
                "node {\n" +
                "    echo 'inside node'    \n" +
                "}"
        ));

        /** Flow structure (ID - type)
         2 - FlowStartNode (BlockStartNode)
         3 - Echostep
         4 - ExecutorStep (StepStartNode) - WorkspaceAction
         5 - ExecutorStep (StepStartNode) - BodyInvocationAction
         6 - Echostep
         7 - StepEndNode - startId (5)
         8 - StepEndNode - startId (4)
         9 - FlowEndNode
         */

        WorkflowRun b = r.assertBuildStatusSuccess(job.scheduleBuild2(0));
        Collection<FlowNode> heads = b.getExecution().getCurrentHeads();
        FlowExecution exec = b.getExecution();

        LinearBlockHoppingScanner hopper = new LinearBlockHoppingScanner();
        FlowNode headCandidate = exec.getNode("7");
        Assert.assertEquals(exec.getNode("4"), hopper.jumpBlockScan(headCandidate, Collections.EMPTY_SET));
        Assert.assertTrue("Setup should return true if we can iterate", hopper.setup(headCandidate, null));

        headCandidate = exec.getNode("6");
        List<FlowNode> filtered = hopper.filteredNodes(headCandidate, MATCH_ECHO_STEP);
        Assert.assertEquals(2, filtered.size());

        headCandidate = exec.getNode("7");
        filtered = hopper.filteredNodes(Collections.singleton(headCandidate), null, MATCH_ECHO_STEP);
        Assert.assertEquals(1, filtered.size());

        filtered = hopper.filteredNodes(Collections.singleton(exec.getNode("8")), null, MATCH_ECHO_STEP);
        Assert.assertEquals(1, filtered.size());

        filtered = hopper.filteredNodes(Collections.singleton(exec.getNode("9")), null, MATCH_ECHO_STEP);
        Assert.assertEquals(0, filtered.size());
    }


    /** And the parallel case */
    @Test
    public void testParallelScan() throws Exception {
        WorkflowJob job = r.jenkins.createProject(WorkflowJob.class, "Convoluted");
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
            "echo 'final'"
        ));

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

        WorkflowRun b = r.assertBuildStatusSuccess(job.scheduleBuild2(0));
        FlowExecution exec = b.getExecution();
        Collection<FlowNode> heads = b.getExecution().getCurrentHeads();

        AbstractFlowScanner scanner = new LinearScanner();
        Collection<FlowNode> matches = scanner.filteredNodes(heads, null, MATCH_ECHO_STEP);
        Assert.assertTrue(matches.size() == 3 || matches.size() == 4);  // Depending on ordering

        scanner = new DepthFirstScanner();
        matches = scanner.filteredNodes(heads, null, MATCH_ECHO_STEP);
        Assert.assertEquals(5, matches.size());

        // Block hopping scanner
        scanner = new LinearBlockHoppingScanner();
        matches = scanner.filteredNodes(heads, null, MATCH_ECHO_STEP);
        Assert.assertEquals(0, matches.size());

        matches = scanner.filteredNodes(Collections.singleton(b.getExecution().getNode("14")), MATCH_ECHO_STEP);
        Assert.assertEquals(2, matches.size());

        // We're going to test the ForkScanner in more depth since this is its natural use
        scanner = new ForkScanner();
        matches = scanner.filteredNodes(heads, null, MATCH_ECHO_STEP);
        Assert.assertEquals(5, matches.size());

        /*ArrayList<FlowNode> forkedHeads = new ArrayList<FlowNode>();
        forkedHeads.add(exec.getNode("9"));
        forkedHeads.add(exec.getNode("11"));
        matches = scanner.filteredNodes(forkedHeads, null, MATCH_ECHO_STEP);
        Assert.assertEquals(5, matches.size());*/

        // Start in one branch, test the forkscanning
        Assert.assertEquals(3, scanner.filteredNodes(exec.getNode("12"), MATCH_ECHO_STEP).size());
        Assert.assertEquals(2, scanner.filteredNodes(exec.getNode("9"), MATCH_ECHO_STEP).size());

        // Filtering at different points within branches
        List<FlowNode> blackList = Arrays.asList(exec.getNode("6"), exec.getNode("7"));
        Assert.assertEquals(4, scanner.filteredNodes(heads, blackList, MATCH_ECHO_STEP).size());
        Assert.assertEquals(4, scanner.filteredNodes(heads, Collections.singletonList(exec.getNode("4")), MATCH_ECHO_STEP).size());
        blackList = Arrays.asList(exec.getNode("6"), exec.getNode("10"));
        Assert.assertEquals(3, scanner.filteredNodes(heads, blackList, MATCH_ECHO_STEP).size());
    }

    @Test
    public void testNestedParallelScan() throws Exception {
        WorkflowJob job = r.jenkins.createProject(WorkflowJob.class, "Convoluted");
        job.setDefinition(new CpsFlowDefinition(
                "echo 'first'\n" +
                "def steps = [:]\n" +
                "steps['1'] = {\n" +
                "    echo 'do 1 stuff'\n" +
                "}\n" +
                "steps['2'] = {\n" +
                "    echo '2a'\n" +
                "    def nested = [:]\n" +
                "    nested['2-1'] = {\n" +
                "        echo 'do 2-1'\n" +
                "    } \n" +
                "    nested['2-2'] = {\n" +
                "        sleep 1\n" +
                "        echo '2 section 2'\n" +
                "    }\n" +
                "    echo '2b'\n" +
                "    parallel nested\n" +
                "}\n" +
                "parallel steps\n" +
                "echo 'final'"
        ));

        WorkflowRun b = r.assertBuildStatusSuccess(job.scheduleBuild2(0));
        FlowExecution exec = b.getExecution();
        Collection<FlowNode> heads = b.getExecution().getCurrentHeads();

        // Basic test of DepthFirstScanner
        AbstractFlowScanner scanner = new DepthFirstScanner();
        Collection<FlowNode> matches = scanner.filteredNodes(heads, null, MATCH_ECHO_STEP);
        Assert.assertEquals(7, matches.size());


        // We're going to test the ForkScanner in more depth since this is its natural use
        scanner = new ForkScanner();
        matches = scanner.filteredNodes(heads, null, MATCH_ECHO_STEP);
        Assert.assertEquals(7, matches.size());
    }

    /** Unit tests for the innards of the ForkScanner */
    @Test
    public void testForkedScanner() throws Exception {

        WorkflowJob job = r.jenkins.createProject(WorkflowJob.class, "Convoluted");
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
                        "echo 'final'"
        ));

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

        WorkflowRun b = r.assertBuildStatusSuccess(job.scheduleBuild2(0));
        FlowExecution exec = b.getExecution();
        Collection<FlowNode> heads = b.getExecution().getCurrentHeads();

        // Initial case
        ForkScanner scanner = new ForkScanner();
        scanner.setup(heads, null);
        Assert.assertNull(scanner.currentParallelStart);
        Assert.assertNull(scanner.currentParallelStartNode);
        Assert.assertNotNull(scanner.parallelBlockStartStack);
        Assert.assertEquals(0, scanner.parallelBlockStartStack.size());
        Assert.assertTrue(scanner.isWalkingFromFinish());

        // Fork case
        scanner.setup(exec.getNode("13"));
        Assert.assertFalse(scanner.isWalkingFromFinish());
        Assert.assertEquals("13", scanner.next().getId());
        Assert.assertNotNull(scanner.parallelBlockStartStack);
        Assert.assertEquals(0, scanner.parallelBlockStartStack.size());
        Assert.assertEquals(exec.getNode("4"), scanner.currentParallelStartNode);

        ForkScanner.ParallelBlockStart start = scanner.currentParallelStart;
        Assert.assertEquals(2, start.totalBranches);
        Assert.assertEquals(1, start.remainingBranches);
        Assert.assertEquals(1, start.unvisited.size());
        Assert.assertEquals(exec.getNode("4"), start.forkStart);

        Assert.assertEquals(exec.getNode("9"), scanner.next());
        Assert.assertEquals(exec.getNode("8"), scanner.next());
        Assert.assertEquals(exec.getNode("6"), scanner.next());
        FlowNode f = scanner.next();
        Assert.assertEquals(exec.getNode("12"), f);

    }
}
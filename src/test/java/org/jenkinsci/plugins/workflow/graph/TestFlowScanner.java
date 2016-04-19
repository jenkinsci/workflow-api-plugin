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

package org.jenkinsci.plugins.workflow.graph;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class TestFlowScanner {

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @Rule public JenkinsRule r = new JenkinsRule();

    static final class CollectingVisitor implements FlowScanner.FlowNodeVisitor {
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
        WorkflowRun b = r.assertBuildStatusSuccess(job.scheduleBuild2(0));
        FlowExecution exec = b.getExecution();
        FlowScanner.ScanAlgorithm[] scans = {new FlowScanner.LinearScanner(),
                new FlowScanner.DepthFirstScanner(),
                new FlowScanner.BlockHoppingScanner()
        };

        Predicate<FlowNode> echoPredicate = FlowScanner.predicateMatchStepDescriptor("org.jenkinsci.plugins.workflow.steps.EchoStep");
        List<FlowNode> heads = exec.getCurrentHeads();

        // Test expected scans with no stop nodes given (different ways of specifying none)
        for (FlowScanner.ScanAlgorithm sa : scans) {
            System.out.println("Testing class: "+sa.getClass());
            FlowNode node = sa.findFirstMatch(heads, null, echoPredicate);
            Assert.assertEquals(exec.getNode("5"), node);
            node = sa.findFirstMatch(heads, Collections.EMPTY_LIST, echoPredicate);
            Assert.assertEquals(exec.getNode("5"), node);
            node = sa.findFirstMatch(heads, Collections.EMPTY_SET, echoPredicate);
            Assert.assertEquals(exec.getNode("5"), node);

            Collection<FlowNode> nodeList = sa.findAllMatches(heads, null, echoPredicate);
            FlowNode[] expected = new FlowNode[]{exec.getNode("5"), exec.getNode("4")};
            Assert.assertArrayEquals(expected, nodeList.toArray());
            nodeList = sa.findAllMatches(heads, Collections.EMPTY_LIST, echoPredicate);
            Assert.assertArrayEquals(expected, nodeList.toArray());
            nodeList = sa.findAllMatches(heads, Collections.EMPTY_SET, echoPredicate);
            Assert.assertArrayEquals(expected, nodeList.toArray());
        }

        // Test with no matches
        for (FlowScanner.ScanAlgorithm sa : scans) {
            System.out.println("Testing class: "+sa.getClass());
            FlowNode node = sa.findFirstMatch(heads, null, (Predicate)Predicates.alwaysFalse());
            Assert.assertNull(node);

            Collection<FlowNode> nodeList = sa.findAllMatches(heads, null, (Predicate)Predicates.alwaysFalse());
            Assert.assertNotNull(nodeList);
            Assert.assertEquals(0, nodeList.size());
        }


        CollectingVisitor vis = new CollectingVisitor();
        // Verify we touch head and foot nodes too
        for (FlowScanner.ScanAlgorithm sa : scans) {
            System.out.println("Testing class: " + sa.getClass());
            Collection<FlowNode> nodeList = sa.findAllMatches(heads, null, (Predicate)Predicates.alwaysTrue());
            vis.reset();
            sa.visitAll(heads, vis);
            Assert.assertEquals(5, nodeList.size());
            Assert.assertEquals(5, vis.getVisited().size());
        }

        // Test with a stop node given, sometimes no matches
        Collection<FlowNode> noMatchEndNode = Collections.singleton(exec.getNode("5"));
        Collection<FlowNode> singleMatchEndNode = Collections.singleton(exec.getNode("4"));
        for (FlowScanner.ScanAlgorithm sa : scans) {
            FlowNode node = sa.findFirstMatch(heads, noMatchEndNode, echoPredicate);
            Assert.assertNull(node);

            Collection<FlowNode> nodeList = sa.findAllMatches(heads, noMatchEndNode, echoPredicate);
            Assert.assertNotNull(nodeList);
            Assert.assertEquals(0, nodeList.size());

            // Now we try with a stop list the reduces node set for multiple matches
            node = sa.findFirstMatch(heads, singleMatchEndNode, echoPredicate);
            Assert.assertEquals(exec.getNode("5"), node);
            nodeList = sa.findAllMatches(heads, singleMatchEndNode, echoPredicate);
            Assert.assertNotNull(nodeList);
            Assert.assertEquals(1, nodeList.size());
            Assert.assertEquals(exec.getNode("5"), nodeList.iterator().next());
        }
    }

    /** Tests the basic scan algorithm where blocks are involved */
    @Test
    public void testBlockScan() throws Exception {
        WorkflowJob job = r.jenkins.createProject(WorkflowJob.class, "Convoluted");
        job.setDefinition(new CpsFlowDefinition(
            "echo 'first'\n" +
            "timeout(time: 10, unit: 'SECONDS') {\n" +
            "    echo 'second'\n" +
            "    echo 'third'\n" +
            "}\n" +
            "sleep 1"
        ));
        WorkflowRun b = r.assertBuildStatusSuccess(job.scheduleBuild2(0));
        Predicate<FlowNode> matchEchoStep = FlowScanner.predicateMatchStepDescriptor("org.jenkinsci.plugins.workflow.steps.EchoStep");

        // Test blockhopping
        FlowScanner.BlockHoppingScanner blockHoppingScanner = new FlowScanner.BlockHoppingScanner();
        Collection<FlowNode> matches = blockHoppingScanner.findAllMatches(b.getExecution().getCurrentHeads(), null, matchEchoStep);

        // This means we jumped the blocks
        Assert.assertEquals(1, matches.size());

        FlowScanner.DepthFirstScanner depthFirstScanner = new FlowScanner.DepthFirstScanner();
        matches = depthFirstScanner.findAllMatches(b.getExecution().getCurrentHeads(), null, matchEchoStep);

        // Nodes all covered
        Assert.assertEquals(3, matches.size());
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
        WorkflowRun b = r.assertBuildStatusSuccess(job.scheduleBuild2(0));
        Collection<FlowNode> heads = b.getExecution().getCurrentHeads();
        Predicate<FlowNode> matchEchoStep = FlowScanner.predicateMatchStepDescriptor("org.jenkinsci.plugins.workflow.steps.EchoStep");

        FlowScanner.ScanAlgorithm scanner = new FlowScanner.LinearScanner();
        Collection<FlowNode> matches = scanner.findAllMatches(heads, null, matchEchoStep);
        Assert.assertTrue(matches.size() >= 3 && matches.size() <= 4);

        scanner = new FlowScanner.DepthFirstScanner();
        matches = scanner.findAllMatches(heads, null, matchEchoStep);
        Assert.assertTrue(matches.size() == 5);

        scanner = new FlowScanner.BlockHoppingScanner();
        matches = scanner.findAllMatches(heads, null, matchEchoStep);
        Assert.assertTrue(matches.size() == 2);
    }

}
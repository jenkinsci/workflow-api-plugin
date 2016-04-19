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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class TestFlowScanner {

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @Rule public JenkinsRule r = new JenkinsRule();

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
            FlowNode node = sa.findFirstMatch(heads, null, (Predicate)Predicates.alwaysFalse());
            Assert.assertNull(node);

            Collection<FlowNode> nodeList = sa.findAllMatches(heads, null, (Predicate)Predicates.alwaysFalse());
            Assert.assertNotNull(nodeList);
            Assert.assertEquals(0, nodeList.size());
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

        // Test blockhopping
        FlowScanner.BlockHoppingScanner blockHoppingScanner = new FlowScanner.BlockHoppingScanner();
        Collection<FlowNode> matches = blockHoppingScanner.findAllMatches(b.getExecution().getCurrentHeads(), null,
                FlowScanner.predicateMatchStepDescriptor("org.jenkinsci.plugins.workflow.steps.EchoStep"));

        // This means we jumped the blocks
        Assert.assertEquals(1, matches.size());
    }


    @Test
    public void testme() throws Exception {
        WorkflowJob job = r.jenkins.createProject(WorkflowJob.class, "Convoluted");
        job.setDefinition(new CpsFlowDefinition(
                "echo 'pre-stage command'\n" +
                        "sleep 1\n" +
                        "stage 'first'\n" +
                        "echo 'I ran'\n" +
                        "stage 'second'\n" +
                        "node {\n" +
                        "    def steps = [:]\n" +
                        "    steps['2a-dir'] = {\n" +
                        "        echo 'do 2a stuff'\n" +
                        "        echo 'do more 2a stuff'\n" +
                        "        timeout(time: 10, unit: 'SECONDS') {\n" +
                        "            stage 'invalid'\n" +
                        "            echo 'time seconds'\n" +
                        "        }\n" +
                        "        sleep 15\n" +
                        "    }\n" +
                        "    steps['2b'] = {\n" +
                        "        echo 'do 2b stuff'\n" +
                        "        sleep 10\n" +
                        "        echo 'echo_label_me'\n" +
                        "    }\n" +
                        "    parallel steps\n" +
                        "}\n" +
                        "\n" +
                        "stage 'final'\n" +
                        "echo 'ran final 1'\n" +
                        "echo 'ran final 2'"
        ));
        WorkflowRun b = r.assertBuildStatusSuccess(job.scheduleBuild2(0));
        FlowGraphWalker walker = new FlowGraphWalker();
        walker.addHeads(b.getExecution().getCurrentHeads());
    }
}
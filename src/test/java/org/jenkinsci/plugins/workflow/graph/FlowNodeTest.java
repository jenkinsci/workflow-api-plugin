/*
 * The MIT License
 *
 * Copyright 2017 CloudBees, Inc.
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

import java.lang.reflect.Method;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

import com.google.common.collect.Lists;
import org.jenkinsci.plugins.workflow.actions.ArgumentsAction;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graphanalysis.DepthFirstScanner;
import org.jenkinsci.plugins.workflow.graphanalysis.FlowScanningUtils;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.RestartableJenkinsRule;

public class FlowNodeTest {

    @Rule public RestartableJenkinsRule rr = new RestartableJenkinsRule();
    @Rule public LoggerRule logging = new LoggerRule().record(FlowNode.class, Level.FINER);

    @Issue("JENKINS-38223")
    @Test public void isActive() {
        rr.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = rr.j.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(
                    "semaphore 'pre-outer'\n" +
                    "stage('outer') {\n" +
                    "  semaphore 'pre-inner'\n" +
                    "  stage('inner') {\n" +
                    "    semaphore 'inner'\n" +
                    "  }\n" +
                    "  semaphore 'post-inner'\n" +
                    "}\n" +
                    "semaphore 'post-outer'\n" +
                    "parallel a: {\n" +
                    "  semaphore 'branch-a'\n" +
                    "}, b: {\n" +
                    "  semaphore 'branch-b'\n" +
                    "}\n" +
                    "semaphore 'last'", true));
                WorkflowRun b = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("pre-outer/1", b);
                assertActiveSteps(b, "Start of Pipeline", "semaphore: pre-outer");
                SemaphoreStep.success("pre-outer/1", null);
                SemaphoreStep.waitForStart("pre-inner/1", b);
            }
        });
        rr.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowRun b = rr.j.jenkins.getItemByFullName("p", WorkflowJob.class).getLastBuild();
                assertActiveSteps(b, "Start of Pipeline", "stage: outer", "{ (outer)", "semaphore: pre-inner");
                SemaphoreStep.success("pre-inner/1", null);
                SemaphoreStep.waitForStart("inner/1", b);
                assertActiveSteps(b, "Start of Pipeline", "stage: outer", "{ (outer)", "stage: inner", "{ (inner)", "semaphore: inner");
                SemaphoreStep.success("inner/1", null);
                SemaphoreStep.waitForStart("post-inner/1", b);
                assertActiveSteps(b, "Start of Pipeline", "stage: outer", "{ (outer)", "semaphore: post-inner");
                SemaphoreStep.success("post-inner/1", null);
                SemaphoreStep.waitForStart("post-outer/1", b);
                assertActiveSteps(b, "Start of Pipeline", "semaphore: post-outer");
                SemaphoreStep.success("post-outer/1", null);
                SemaphoreStep.waitForStart("branch-a/1", b);
                SemaphoreStep.waitForStart("branch-b/1", b);
            }
        });
        rr.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowRun b = rr.j.jenkins.getItemByFullName("p", WorkflowJob.class).getLastBuild();
                // weird order caused by FlowGraphWalker DFS
                assertActiveSteps(b, "{ (Branch: a)", "semaphore: branch-a", "Start of Pipeline", "parallel", "{ (Branch: b)", "semaphore: branch-b");
                SemaphoreStep.success("branch-a/1", null);
                SemaphoreStep.success("branch-b/1", null);
                SemaphoreStep.waitForStart("last/1", b);
                assertActiveSteps(b, "Start of Pipeline", "semaphore: last");
                SemaphoreStep.success("last/1", null);
                rr.j.waitForCompletion(b);
                assertActiveSteps(b);
            }
        });
    }

    private static void assertActiveSteps(WorkflowRun b, String... expected) throws Exception{
        List<String> expectedList = Arrays.asList(expected);
        Collections.sort(expectedList);
        List<String> actual = new ArrayList<>();
        DepthFirstScanner scan = new DepthFirstScanner();
        for (FlowNode n : scan.allNodes(b.getExecution())) {
            if (n.isActive()) {
                String args = ArgumentsAction.getStepArgumentsAsString(n);
                String name = n.getDisplayFunctionName();
                actual.add(args != null ? name + ": " + args : name);
            }
        }
        Collections.sort(actual);
        assertEquals(expectedList, actual);

        // Now we clear the cache and try this again
        Method m = FlowExecution.class.getDeclaredMethod("getInternalGraphLookup", null);
        m.setAccessible(true);
        Object ob = m.invoke((FlowExecution)(b.getExecution()), null);
        StandardGraphLookupView view = (StandardGraphLookupView)ob;
        ((StandardGraphLookupView) ob).clearCache();

        actual.clear();
        for (FlowNode n : scan.allNodes(b.getExecution())) {
            if (n.isActive()) {
                String args = ArgumentsAction.getStepArgumentsAsString(n);
                String name = n.getDisplayFunctionName();
                actual.add(args != null ? name + ": " + args : name);
            }
        }
        Collections.sort(actual);
        assertEquals(expectedList, actual);
    }

    @Issue("JENKINS-27395")
    @Test
    public void enclosingBlocksSingleBlock() throws Exception {
        rr.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowJob job = rr.j.createProject(WorkflowJob.class, "enclosingBlocks");
                job.setDefinition(new CpsFlowDefinition(
                        "catchError {echo 'bob';}", true));

                WorkflowRun r = rr.j.buildAndAssertSuccess(job);

                FlowExecution execution = r.getExecution();

                // FlowStartNode
                assertExpectedEnclosing(execution, "2", null);

                // CatchError block, outer block
                assertExpectedEnclosing(execution, "3", "2");

                // CatchError block, inner block (body)
                assertExpectedEnclosing(execution, "4", "3");

                // Echo step, enclosed in body block for catchError step
                assertExpectedEnclosing(execution, "5", "4");

                // End of inner catchError block (end of body), enclosed by outer block
                assertExpectedEnclosing(execution, "6", "3");

                // End of outer catchError block, enclosed by FlowStartNode
                assertExpectedEnclosing(execution, "7", "2");

                // FlowEndNode, not inside anything at all
                assertExpectedEnclosing(execution, "8", null);
            }
        });
    }


    @Issue("JENKINS-27395")
    @Test
    public void enclosingBlocks() throws Exception {
        rr.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowJob job = rr.j.createProject(WorkflowJob.class, "enclosingBlocks");
                /*
                Node dump follows, format:
[ID]{parent,ids}(millisSinceStartOfRun) flowNodeClassName stepDisplayName [st=startId if a block end node]
Action format:
	- actionClassName actionDisplayName
------------------------------------------------------------------------------------------
[2]{}FlowStartNode Start of Pipeline
[3]{2}StepStartNode Stage : Start
  -LogActionImpl Console Output
  -ArgumentsActionImpl null
[4]{3}StepStartNode outermost
  -BodyInvocationAction null
  -LabelAction outermost
[5]{4}StepAtomNode Print Message
  -LogActionImpl Console Output
  -ArgumentsActionImpl null
[6]{5}StepStartNode Execute in parallel : Start
  -LogActionImpl Console Output
[8]{6}StepStartNode Branch: a
  -BodyInvocationAction null
  -ParallelLabelAction Branch: a
[9]{6}StepStartNode Branch: b
  -BodyInvocationAction null
  -ParallelLabelAction Branch: b
[10]{8}StepStartNode Stage : Start
  -LogActionImpl Console Output
  -ArgumentsActionImpl null
[11]{10}StepStartNode inner-a
  -BodyInvocationAction null
  -LabelAction inner-a
[12]{9}StepStartNode Stage : Start
  -LogActionImpl Console Output
  -ArgumentsActionImpl null
[13]{12}StepStartNode inner-b
  -BodyInvocationAction null
  -LabelAction inner-b
[14]{11}StepAtomNode Print Message
  -LogActionImpl Console Output
  -ArgumentsActionImpl null
[15]{14}StepStartNode Stage : Start
  -LogActionImpl Console Output
  -ArgumentsActionImpl null
[16]{15}StepStartNode innermost-a
  -BodyInvocationAction null
  -LabelAction innermost-a
[17]{13}StepAtomNode Print Message
  -LogActionImpl Console Output
  -ArgumentsActionImpl null
[18]{17}StepStartNode Stage : Start
  -LogActionImpl Console Output
  -ArgumentsActionImpl null
[19]{18}StepStartNode innermost-b
  -BodyInvocationAction null
  -LabelAction innermost-b
[20]{16}StepAtomNode Print Message
  -LogActionImpl Console Output
  -ArgumentsActionImpl null
[21]{20}StepEndNode Stage : Body : End  [st=16]
  -BodyInvocationAction null
[22]{19}StepAtomNode Print Message
  -LogActionImpl Console Output
  -ArgumentsActionImpl null
[23]{22}StepEndNode Stage : Body : End  [st=19]
  -BodyInvocationAction null
[24]{21}StepEndNode Stage : End  [st=15]
[25]{23}StepEndNode Stage : End  [st=18]
[26]{24}StepEndNode Stage : Body : End  [st=11]
  -BodyInvocationAction null
[27]{25}StepEndNode Stage : Body : End  [st=13]
  -BodyInvocationAction null
[28]{26}StepEndNode Stage : End  [st=10]
[29]{27}StepEndNode Stage : End  [st=12]
[30]{28}StepEndNode Execute in parallel : Body : End  [st=8]
  -BodyInvocationAction null
[31]{29}StepEndNode Execute in parallel : Body : End  [st=9]
  -BodyInvocationAction null
[32]{30,31}StepEndNode Execute in parallel : End  [st=6]
[33]{32}StepEndNode Stage : Body : End  [st=4]
  -BodyInvocationAction null
[34]{33}StepEndNode Stage : End  [st=3]
[35]{34}FlowEndNode End of Pipeline  [st=2]
------------------------------------------------------------------------------------------
                 */
                job.setDefinition(new CpsFlowDefinition(
                        "stage('outermost') {\n" +
                                "  echo 'in outermost'\n" +
                                "  parallel(a: {\n" +
                                "    stage('inner-a') {\n" +
                                "      echo 'in inner-a'\n" +
                                "      stage('innermost-a') {\n" +
                                "        echo 'in innermost-a'\n" +
                                "      }\n" +
                                "    }\n" +
                                "  },\n" +
                                "  b: {\n" +
                                "    stage('inner-b') {\n" +
                                "      echo 'in inner-b'\n" +
                                "      stage('innermost-b') {\n" +
                                "        echo 'in innermost-b'\n" +
                                "      }\n" +
                                "    }\n" +
                                "  })\n" +
                                "}\n", true));

                WorkflowRun r = rr.j.buildAndAssertSuccess(job);

                FlowExecution execution = r.getExecution();

                // FlowStartNode
                assertExpectedEnclosing(execution, "2", null);

                // outermost stage start
                assertExpectedEnclosing(execution, "3", "2");

                // outermost stage body
                assertExpectedEnclosing(execution, "4", "3");

                // outermost echo
                assertExpectedEnclosing(execution, "5", "4");

                // parallel start
                assertExpectedEnclosing(execution, "6", "4");

                // Branch a start
                assertExpectedEnclosing(execution, "8", "6");

                // Branch b start
                assertExpectedEnclosing(execution, "9", "6");

                // Stage inner-a start
                assertExpectedEnclosing(execution, "10", "8");

                // Stage inner-a body
                assertExpectedEnclosing(execution, "11", "10");

                // Stage inner-b start
                assertExpectedEnclosing(execution, "12", "9");

                // Stage inner-b body
                assertExpectedEnclosing(execution, "13", "12");

                // echo inner-a
                assertExpectedEnclosing(execution, "14", "11");

                // Stage innermost-a start
                assertExpectedEnclosing(execution, "15", "11");

                // Stage innermost-a body
                assertExpectedEnclosing(execution, "16", "15");

                // echo inner-b
                assertExpectedEnclosing(execution, "17", "13");

                // Stage innermost-b start
                assertExpectedEnclosing(execution, "18", "13");

                // Stage innermost-b body
                assertExpectedEnclosing(execution, "19", "18");

                // echo innermost-a
                assertExpectedEnclosing(execution, "20", "16");

                // Stage innermost-a body end
                assertExpectedEnclosing(execution, "21", "15");

                // echo innermost-b
                assertExpectedEnclosing(execution, "22", "19");

                // Stage innermost-b body end
                assertExpectedEnclosing(execution, "23", "18");

                // Stage innermost-a end
                assertExpectedEnclosing(execution, "24", "11");

                // Stage innermost-b end
                assertExpectedEnclosing(execution, "25", "13");

                // Stage inner-a body end
                assertExpectedEnclosing(execution, "26", "10");

                // Stage inner-b body end
                assertExpectedEnclosing(execution, "27", "12");

                // Stage inner-a end
                assertExpectedEnclosing(execution, "28", "8");

                // Stage inner-b end
                assertExpectedEnclosing(execution, "29", "9");

                // Branch a end
                assertExpectedEnclosing(execution, "30", "6");

                // Branch b end
                assertExpectedEnclosing(execution, "31", "6");

                // parallel end
                assertExpectedEnclosing(execution, "32", "4");

                // outermost stage body end
                assertExpectedEnclosing(execution, "33", "3");

                // outermost stage end
                assertExpectedEnclosing(execution, "34", "2");

                // FlowEndNode
                assertExpectedEnclosing(execution, "35", null);
            }
        });
    }

    private void assertExpectedEnclosing(FlowExecution execution, String nodeId, String enclosingId) throws Exception {
        FlowNode node = execution.getNode(nodeId);
        assertNotNull(node);
        String secondEnclosingId = node.getEnclosingId();

        assertEquals(MessageFormat.format("Node {0}: enclosingID doesn't match between first and second fetches", node),
                enclosingId, secondEnclosingId);
        if (enclosingId == null) {
            assertTrue(MessageFormat.format("Node {0} and enclosingId {1}: null enclosing ID, but non-empty list of enclosing IDs", node, enclosingId),
                    node.getAllEnclosingIds().isEmpty());
            assertTrue(MessageFormat.format("Node {0} and enclosingId {1}: null enclosing ID, but non-empty list of enclosing blocks", node, enclosingId),
                    node.getEnclosingBlocks().isEmpty());
        } else {
            FlowNode enclosingNode = execution.getNode(enclosingId);
            assertNotNull(MessageFormat.format("Node {0} and enclosingId {1}: no node with enclosing ID exists", node, enclosingId),
                    enclosingNode);
            List<String> enclosingIds = node.getAllEnclosingIds();
            List<String> enclosingIdsIncludingNode = enclosingIdsIncludingNode(enclosingNode);
            List<String> iteratedEnclosingBlockIds = new ArrayList<String>();
            for (BlockStartNode bsn : node.iterateEnclosingBlocks()) {
                iteratedEnclosingBlockIds.add(bsn.getId());
            }
            assertArrayEquals(enclosingIds.toArray(), enclosingIdsIncludingNode.toArray());
            assertArrayEquals(enclosingIdsIncludingNode(enclosingNode).toArray(), node.getAllEnclosingIds().toArray());
            assertArrayEquals(enclosingBlocksIncludingNode(enclosingNode).toArray(), node.getEnclosingBlocks().toArray());
            assertArrayEquals(enclosingIdsIncludingNode(enclosingNode).toArray(), iteratedEnclosingBlockIds.toArray());
        }
    }

    private List<String> enclosingIdsIncludingNode(FlowNode node) {
        List<String> encl = new ArrayList<>();
        encl.add(node.getId());
        encl.addAll(node.getAllEnclosingIds());
        return encl;
    }

    private List<FlowNode> enclosingBlocksIncludingNode(FlowNode node) {
        List<FlowNode> encl = new ArrayList<>();
        encl.add(node);
        encl.addAll(node.getEnclosingBlocks());
        return encl;
    }
}


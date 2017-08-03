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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.jenkinsci.plugins.workflow.actions.ArgumentsAction;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.RestartableJenkinsRule;

public class FlowNodeTest {

    @Rule public RestartableJenkinsRule rr = new RestartableJenkinsRule();

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
    private static void assertActiveSteps(WorkflowRun b, String... expected) {
        List<String> actual = new ArrayList<>();
        for (FlowNode n : new FlowGraphWalker(b.getExecution())) {
            if (n.isActive()) {
                String args = ArgumentsAction.getStepArgumentsAsString(n);
                String name = n.getDisplayFunctionName();
                actual.add(args != null ? name + ": " + args : name);
            }
        }
        Collections.reverse(actual); // more readable
        assertEquals(Arrays.asList(expected), actual);
    }

}

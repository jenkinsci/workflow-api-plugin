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

package org.jenkinsci.plugins.workflow.flow;

import static org.junit.Assert.assertNotNull;

import hudson.AbortException;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Result;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import hudson.model.TaskListener;
import hudson.model.queue.QueueTaskFuture;
import java.io.Serializable;
import java.util.Collections;
import java.util.Set;
import java.util.logging.Level;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.Rule;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.JenkinsSessionRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;

public class FlowExecutionListTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public JenkinsSessionRule sessions = new JenkinsSessionRule();
    @Rule public LoggerRule logging = new LoggerRule().record(FlowExecutionList.class, Level.FINE);

    @Issue("JENKINS-40771")
    @Test public void simultaneousRegister() throws Throwable {
        sessions.then(j -> {
                WorkflowJob p = j.createProject(WorkflowJob.class, "p");
                { // make sure there is an initial FlowExecutionList.xml
                    p.setDefinition(new CpsFlowDefinition("", true));
                    j.buildAndAssertSuccess(p);
                }
                p.setDefinition(new CpsFlowDefinition("echo params.key; sleep 5", true));
                p.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("key", null)));
                QueueTaskFuture<WorkflowRun> f1 = p.scheduleBuild2(0, new ParametersAction(new StringParameterValue("key", "one")));
                QueueTaskFuture<WorkflowRun> f2 = p.scheduleBuild2(0, new ParametersAction(new StringParameterValue("key", "two")));
                f1.waitForStart();
                f2.waitForStart();
                WorkflowRun b2 = p.getBuildByNumber(2);
                assertNotNull(b2);
                WorkflowRun b3 = p.getBuildByNumber(3);
                assertNotNull(b3);
                j.waitForMessage("Sleeping for ", b2);
                j.waitForMessage("Sleeping for ", b3);
        });
        sessions.then(j -> {
                WorkflowJob p = j.jenkins.getItemByFullName("p", WorkflowJob.class);
                WorkflowRun b2 = p.getBuildByNumber(2);
                WorkflowRun b3 = p.getBuildByNumber(3);
                j.assertBuildStatusSuccess(j.waitForCompletion(b2));
                j.assertBuildStatusSuccess(j.waitForCompletion(b3));
        });
    }

    @Issue("TODO")
    @Test public void resumeStepExecutions() throws Throwable {
        sessions.then(r -> {
            WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
            p.setDefinition(new CpsFlowDefinition("noResume()", true));
            WorkflowRun b = p.scheduleBuild2(0).waitForStart();
            r.waitForMessage("Starting non-resumable step", b);
            // TODO: Unclear how this might happen in practice.
            FlowExecutionList.get().unregister(b.asFlowExecutionOwner());
        });
        sessions.then(r -> {
            WorkflowJob p = r.jenkins.getItemByFullName("p", WorkflowJob.class);
            WorkflowRun b = p.getBuildByNumber(1);
            r.waitForCompletion(b);
            r.assertBuildStatus(Result.FAILURE, b);
            r.assertLogContains("Unable to resume NonResumableStep", b);
        });
    }

    public static class NonResumableStep extends Step implements Serializable {
        public static final long serialVersionUID = 1L;
        @DataBoundConstructor
        public NonResumableStep() { }
        @Override
        public StepExecution start(StepContext sc) throws Exception {
            return new ExecutionImpl(sc);
        }

        private static class ExecutionImpl extends StepExecution implements Serializable {
            public static final long serialVersionUID = 1L;
            private ExecutionImpl(StepContext sc) {
                super(sc);
            }
            @Override
            public boolean start() throws Exception {
                getContext().get(TaskListener.class).getLogger().println("Starting non-resumable step");
                return false;
            }
            @Override
            public void onResume() {
                getContext().onFailure(new AbortException("Unable to resume NonResumableStep"));
            }
        }

        @TestExtension public static class DescriptorImpl extends StepDescriptor {
            @Override
            public Set<? extends Class<?>> getRequiredContext() {
                return Collections.singleton(TaskListener.class);
            }
            @Override
            public String getFunctionName() {
                return "noResume";
            }
        }
    }

}

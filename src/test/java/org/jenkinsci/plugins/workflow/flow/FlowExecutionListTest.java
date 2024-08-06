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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
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
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Set;
import java.util.function.Supplier;
import java.util.logging.Level;
import org.hamcrest.Matcher;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.Rule;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.JenkinsSessionRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.recipes.LocalData;
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

    @Test public void forceLoadRunningExecutionsAfterRestart() throws Throwable {
        logging.capture(50);
        sessions.then(r -> {
            WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
            p.setDefinition(new CpsFlowDefinition("semaphore('wait')", true));
            WorkflowRun b = p.scheduleBuild2(0).waitForStart();
            SemaphoreStep.waitForStart("wait/1", b);
        });
        sessions.then(r -> {
            /*
            Make sure that the build gets loaded automatically by FlowExecutionList$ItemListenerImpl before we load it explictly.
            Expected call stack for resuming a Pipelines and its steps:
                at org.jenkinsci.plugins.workflow.flow.FlowExecutionList$ResumeStepExecutionListener$1.onSuccess(FlowExecutionList.java:250)
                at org.jenkinsci.plugins.workflow.flow.FlowExecutionList$ResumeStepExecutionListener$1.onSuccess(FlowExecutionList.java:247)
                at com.google.common.util.concurrent.Futures$6.run(Futures.java:975)
                at org.jenkinsci.plugins.workflow.flow.DirectExecutor.execute(DirectExecutor.java:33)
                ... Guava Futures API internals ...
                at com.google.common.util.concurrent.Futures.addCallback(Futures.java:985)
                at org.jenkinsci.plugins.workflow.flow.FlowExecutionList$ResumeStepExecutionListener.onResumed(FlowExecutionList.java:247)
                at org.jenkinsci.plugins.workflow.flow.FlowExecutionListener.fireResumed(FlowExecutionListener.java:84)
                at org.jenkinsci.plugins.workflow.job.WorkflowRun.onLoad(WorkflowRun.java:528)
                at hudson.model.RunMap.retrieve(RunMap.java:225)
                ... RunMap internals ...
                at hudson.model.RunMap.getById(RunMap.java:205)
                at org.jenkinsci.plugins.workflow.job.WorkflowRun$Owner.run(WorkflowRun.java:937)
                at org.jenkinsci.plugins.workflow.job.WorkflowRun$Owner.get(WorkflowRun.java:948)
                at org.jenkinsci.plugins.workflow.flow.FlowExecutionList$1.computeNext(FlowExecutionList.java:65)
                at org.jenkinsci.plugins.workflow.flow.FlowExecutionList$1.computeNext(FlowExecutionList.java:57)
                at com.google.common.collect.AbstractIterator.tryToComputeNext(AbstractIterator.java:143)
                at com.google.common.collect.AbstractIterator.hasNext(AbstractIterator.java:138)
                at org.jenkinsci.plugins.workflow.flow.FlowExecutionList$ItemListenerImpl.onLoaded(FlowExecutionList.java:175)
                at jenkins.model.Jenkins.<init>(Jenkins.java:1019)
            */
            waitFor(logging::getMessages, hasItem(containsString("Will resume [org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep")));
            WorkflowJob p = r.jenkins.getItemByFullName("p", WorkflowJob.class);
            SemaphoreStep.success("wait/1", null);
            WorkflowRun b = p.getBuildByNumber(1);
            r.waitForCompletion(b);
            r.assertBuildStatus(Result.SUCCESS, b);
        });
    }

    @Issue("JENKINS-67164")
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

    @Ignore("Build never completes due to infinite loop")
    @LocalData
    @Test public void stepExecutionIteratorDoesNotLeakBuildsWhenOneIsStuck() throws Throwable {
        // LocalData created using the following snippet while the build was waiting in the _second_ sleep, except
        // for build.xml, which was captured during the sleep step. The StepEndNode for the stage was then adjusted to
        // have its startId point to the timeout step's StepStartNode, creating a loop.
        /*
        sessions.then(r -> {
            var stuck = r.createProject(WorkflowJob.class);
            stuck.setDefinition(new CpsFlowDefinition("stage('stage') { sleep 30 }; timeout(time: 10) { sleep 30 }", true));
            var b = stuck.scheduleBuild2(0).waitForStart();
            System.out.println(b.getRootDir());
            r.waitForCompletion(b);
        });
        */
        sessions.then(r -> {
            var p = r.jenkins.getItemByFullName("test0", WorkflowJob.class);
            var b = p.getBuildByNumber(1);
            // TODO: Create and run a new build, call StepExecutionIterator.applyAll while it is running, and assert
            // that we do not leak a hard reference to it through the task queue for the CpsVmExecutorService for the
            // stuck build and the futures inside of StepExecutionIteratorImpl.applyAll.
            // TODO: Ideally, we would detect the issue with the stuck build and find some way to kill it.
            r.waitForCompletion(b);
        });
    }

    public static class NonResumableStep extends Step implements Serializable {
        public static final long serialVersionUID = 1L;
        @DataBoundConstructor
        public NonResumableStep() { }
        @Override
        public StepExecution start(StepContext sc) {
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

    /**
     * Wait up to 5 seconds for the given supplier to return a matching value.
     */
    private static <T> void waitFor(Supplier<T> valueSupplier, Matcher<T> matcher) throws InterruptedException {
        Instant end = Instant.now().plus(Duration.ofSeconds(5));
        while (!matcher.matches(valueSupplier.get()) && Instant.now().isBefore(end)) {
            Thread.sleep(100L);
        }
        assertThat("Matcher should have matched after 5s", valueSupplier.get(), matcher);
    }

}

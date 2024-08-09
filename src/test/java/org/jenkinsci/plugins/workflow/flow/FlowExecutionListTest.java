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

import static org.awaitility.Awaitility.await;
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
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.StepExecutions;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.Rule;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.JenkinsSessionRule;
import org.jvnet.hudson.test.MemoryAssert;
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
            await().atMost(5, TimeUnit.SECONDS).until(logging::getMessages, hasItem(containsString("Will resume [org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep")));
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

    @Test public void stepExecutionIteratorDoesNotLeakBuildsWhenOneIsStuck() throws Throwable {
        sessions.then(r -> {
            var notStuck = r.createProject(WorkflowJob.class, "not-stuck");
            notStuck.setDefinition(new CpsFlowDefinition("semaphore 'wait'", true));
            var notStuckBuild = notStuck.scheduleBuild2(0).waitForStart();
            SemaphoreStep.waitForStart("wait/1", notStuckBuild);
            WeakReference<Object> notStuckBuildRef = new WeakReference<>(notStuckBuild);
            // Create a Pipeline that runs a long-lived task on its CpsVmExecutorService, causing it to get stuck.
            var stuck = r.createProject(WorkflowJob.class, "stuck");
            stuck.setDefinition(new CpsFlowDefinition("blockSynchronously 'stuck'", false));
            var stuckBuild = stuck.scheduleBuild2(0).waitForStart();
            await().atMost(5, TimeUnit.SECONDS).until(() -> SynchronousBlockingStep.isStarted("stuck"));
            // Make FlowExecutionList$StepExecutionIteratorImpl.applyAll submit a task to the CpsVmExecutorService
            // for stuck #1 that will never complete, so the resulting future will never complete.
            StepExecution.applyAll(e -> null);
            // Let notStuckBuild complete and clean up all references.
            SemaphoreStep.success("wait/1", null);
            r.waitForCompletion(notStuckBuild);
            notStuckBuild = null; // Clear out the local variable in this thread.
            Jenkins.get().getQueue().clearLeftItems(); // Otherwise we'd have to wait 5 minutes for the cache to be cleared.
            // Make sure that the reference can be GC'd.
            MemoryAssert.assertGC(notStuckBuildRef, true);
            // Allow stuck #1 to complete so the test can be cleaned up promptly.
            SynchronousBlockingStep.unblock("stuck");
            r.waitForCompletion(stuckBuild);
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
     * Blocks the CPS VM thread synchronously (bad!) to test related problems.
     */
    public static class SynchronousBlockingStep extends Step implements Serializable {
        private static final long serialVersionUID = 1L;
        private static final Map<String, State> blocked = new HashMap<>();
        private final String id;

        @DataBoundConstructor
        public SynchronousBlockingStep(String id) {
            this.id = id;
            if (blocked.put(id, State.NOT_STARTED) != null) {
                throw new IllegalArgumentException("Attempting to reuse ID: " + id);
            }
        }

        @Override
        public StepExecution start(StepContext context) throws Exception {
            return StepExecutions.synchronous(context, c -> {
                blocked.put(id, State.BLOCKED);
                c.get(TaskListener.class).getLogger().println(id + " blocked");
                while (blocked.get(id) == State.BLOCKED) {
                    Thread.sleep(100L);
                }
                c.get(TaskListener.class).getLogger().println(id + " unblocked ");
                return null;
            });
        }

        public static boolean isStarted(String id) {
            var state = blocked.get(id);
            return state != null && state != State.NOT_STARTED;
        }

        public static void unblock(String id) {
            blocked.put(id, State.UNBLOCKED);
        }

        private enum State {
            NOT_STARTED,
            BLOCKED,
            UNBLOCKED,
        }

        @TestExtension("stepExecutionIteratorDoesNotLeakBuildsWhenOneIsStuck") public static class DescriptorImpl extends StepDescriptor {
            @Override
            public Set<? extends Class<?>> getRequiredContext() {
                return Collections.singleton(TaskListener.class);
            }
            @Override
            public String getFunctionName() {
                return "blockSynchronously";
            }
        }
    }

}

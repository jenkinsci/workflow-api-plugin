/*
 * The MIT License
 *
 * Copyright (c) 2016 IKEDA Yasuyuki
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

package org.jenkinsci.plugins.workflow.actions;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.hamcrest.Matchers;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.FlowGraphWalker;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.Issue;

import groovy.lang.MissingMethodException;
import hudson.FilePath;
import hudson.Functions;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.remoting.ProxyException;
import hudson.remoting.VirtualChannel;
import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.logging.Level;
import jenkins.MasterToSlaveFileCallable;
import org.codehaus.groovy.runtime.NullObject;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.StepExecutions;
import org.jvnet.hudson.test.LogRecorder;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.BuildWatcherExtension;
import org.jvnet.hudson.test.junit.jupiter.InboundAgentExtension;
import org.jvnet.hudson.test.junit.jupiter.JenkinsSessionExtension;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Tests for {@link ErrorAction}
 */
class ErrorActionTest {

    @RegisterExtension
    private static final BuildWatcherExtension buildWatcher = new BuildWatcherExtension();

    @RegisterExtension
    private final JenkinsSessionExtension rr = new JenkinsSessionExtension();

    @RegisterExtension
    private final InboundAgentExtension agents = new InboundAgentExtension();

    private final LogRecorder logging = new LogRecorder().record(ErrorAction.class, Level.FINE);

    private List<ErrorAction> extractErrorActions(FlowExecution exec) {
        List<ErrorAction> ret = new ArrayList<>();

        FlowGraphWalker walker = new FlowGraphWalker(exec);
        for (FlowNode n : walker) {
            ErrorAction e = n.getAction(ErrorAction.class);
            if (e != null) {
                ret.add(e);
            }
        }
        return ret;
    }

    @Test
    void simpleException() throws Throwable {
        rr.then(r -> {
            final String EXPECTED = "For testing purpose";
            WorkflowJob job = r.jenkins.createProject(WorkflowJob.class, "p");
            job.setDefinition(new CpsFlowDefinition(String.format(
                    """
                            node {
                              throw new Exception('%s');
                            }"""
                    , EXPECTED
            ), true));
            WorkflowRun b = r.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get());
            List<ErrorAction> errorActionList = extractErrorActions(b.asFlowExecutionOwner().get());
            assertThat(errorActionList, Matchers.not(Matchers.empty()));
            for (ErrorAction e : errorActionList) {
                assertEquals(Exception.class, e.getError().getClass());
                assertEquals(EXPECTED, e.getError().getMessage());
            }
        });
    }

    @Issue("JENKINS-34488")
    @Test
    void unserializableForSecurityReason() throws Throwable {
        rr.then(r -> {
            final String FAILING_EXPRESSION = "(2 + 2) == 5";
            WorkflowJob job = r.jenkins.createProject(WorkflowJob.class, "p");
            // "assert false" throws org.codehaus.groovy.runtime.powerassert.PowerAssertionError,
            // which is rejected by remoting.
            job.setDefinition(new CpsFlowDefinition(String.format(
                    """
                            node {
                              assert %s;
                            }""",
                    FAILING_EXPRESSION
            ), true));
            WorkflowRun b = r.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get());
            r.assertLogContains(FAILING_EXPRESSION, b); // ensure that failed with the assertion.
            List<ErrorAction> errorActionList = extractErrorActions(b.asFlowExecutionOwner().get());
            assertThat(errorActionList, Matchers.not(Matchers.empty()));
            for (ErrorAction e : errorActionList) {
                assertEquals(ProxyException.class, e.getError().getClass());
            }
        });
    }

    @Issue("JENKINS-39346")
    @Test
    void wrappedUnserializableException() throws Throwable {
        rr.then(r -> {
            WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
            p.setDefinition(new CpsFlowDefinition(
                "catchError {\n" +
                "  try {\n" +
                "    try {\n" +
                "      throw new NullPointerException('oops')\n" +
                "    } catch (e) {\n" +
                "      throw new org.codehaus.groovy.runtime.InvokerInvocationException(e)\n" + // TODO is there a way to convince Groovy to throw this on its own?
                "    }\n" +
                "  } catch (e) {\n" +
                "    throw new IllegalArgumentException(e)\n" +
                "  }\n" +
                "}\n" +
                "echo 'got to the end'", false /* for the three types of exceptions thrown in the pipeline */));
            WorkflowRun b = r.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0).get());
            r.assertLogContains("got to the end", b);
            r.assertLogContains("java.lang.NullPointerException: oops", b);
        });
    }

    @Issue("JENKINS-49025")
    @Test
    void nestedFieldUnserializable() throws Throwable {
        rr.then(r -> {
            WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
            p.setDefinition(new CpsFlowDefinition(
                "catchError {\n" +
                "  throw new " + X.class.getCanonicalName() + "()\n" +
                "}\n" +
                "echo 'got to the end'", false /* for "new org.jenkinsci.plugins.workflow.actions.ErrorActionTest$X" */));
            WorkflowRun b = r.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0).get());
            r.assertLogContains("got to the end", b);
            r.assertLogContains(X.class.getName(), b);
            List<ErrorAction> errorActionList = extractErrorActions(b.asFlowExecutionOwner().get());
            assertThat(errorActionList, Matchers.not(Matchers.empty()));
            for (ErrorAction e : errorActionList) {
                assertEquals(ProxyException.class, e.getError().getClass());
            }
        });
    }
    public static class X extends Exception {
        final NullObject nil = NullObject.getNullObject();
    }

    @Test
    void userDefinedError() throws Throwable {
        rr.then(r -> {
            WorkflowJob p = r.createProject(WorkflowJob.class);
            p.setDefinition(new CpsFlowDefinition(
                    """
                            class MyException extends Exception {
                              MyException(String message) { super(message) }
                            }
                            throw new MyException('test')
                            """,
                    true));
            WorkflowRun b = r.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0));
            assertThat(b.getExecution().getCauseOfFailure(), Matchers.instanceOf(ProxyException.class));
        });
    }

    @Test
    void missingPropertyExceptionMemoryLeak() throws Throwable {
        rr.then(r -> {
            WorkflowJob p = r.createProject(WorkflowJob.class);
            p.setDefinition(new CpsFlowDefinition("FOO", false));
            WorkflowRun b = r.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0));
            assertThat(b.getExecution().getCauseOfFailure(), Matchers.instanceOf(ProxyException.class));
        });
    }

    @Test
    void findOriginOfAsyncErrorAcrossRestart() throws Throwable {
        String name = "restart";
        AtomicReference<String> origin = new AtomicReference<>();
        rr.then(r -> {
            String script = Functions.isWindows() ? "bat 'exit 1'" : "sh 'exit 1'";
            WorkflowJob p = r.createProject(WorkflowJob.class, name);
            p.setDefinition(new CpsFlowDefinition(
                    "parallel(one: { node {" + script + "} }, two: { node {" + script + "} })", true));
            WorkflowRun b = r.buildAndAssertStatus(Result.FAILURE, p);
            FlowNode originNode = ErrorAction.findOrigin(b.getExecution().getCauseOfFailure(), b.getExecution());
            origin.set(originNode.getId());
        });
        rr.then(r -> {
            WorkflowJob p = r.jenkins.getItemByFullName(name, WorkflowJob.class);
            WorkflowRun b = p.getLastBuild();
            FlowNode originNode = ErrorAction.findOrigin(b.getExecution().getCauseOfFailure(), b.getExecution());
            assertEquals(origin.get(), originNode.getId());
        });
    }

    @Test
    void findOriginOfSyncErrorAcrossRestart() throws Throwable {
        String name = "restart";
        AtomicReference<String> origin = new AtomicReference<>();
        rr.then(r -> {
            WorkflowJob p = r.createProject(WorkflowJob.class, name);
            p.setDefinition(new CpsFlowDefinition(
                    "parallel(one: { error 'one' }, two: { error 'two' })", true));
            WorkflowRun b = r.buildAndAssertStatus(Result.FAILURE, p);
            FlowNode originNode = ErrorAction.findOrigin(b.getExecution().getCauseOfFailure(), b.getExecution());
            origin.set(originNode.getId());
        });
        rr.then(r -> {
            WorkflowJob p = r.jenkins.getItemByFullName(name, WorkflowJob.class);
            WorkflowRun b = p.getLastBuild();
            FlowNode originNode = ErrorAction.findOrigin(b.getExecution().getCauseOfFailure(), b.getExecution());
            assertEquals(origin.get(), originNode.getId());
        });
    }

    @Test
    void findOriginFromBodyExecutionCallback() throws Throwable {
        rr.then(r -> {
            agents.createAgent(r, "remote");
            var p = r.createProject(WorkflowJob.class);
            p.setDefinition(new CpsFlowDefinition("callsFindOrigin {node('remote') {fails()}}", true));
            var b = p.scheduleBuild2(0).waitForStart();
            r.waitForMessage("Acting slowly in ", b);
            agents.stop("remote");
            r.assertBuildStatus(Result.FAILURE, r.waitForCompletion(b));
            r.assertLogContains("Found in: fails", b);
        });
    }

    public static final class WrapperStep extends Step {
        @DataBoundConstructor public WrapperStep() {}
        @Override public StepExecution start(StepContext context) throws Exception {
            return new ExecutionImpl(context);
        }
        private static final class ExecutionImpl extends StepExecution {
            ExecutionImpl(StepContext context) {
                super(context);
            }
            @Override public boolean start() throws Exception {
                getContext().newBodyInvoker().withCallback(new Callback()).start();
                return false;
            }
        }
        private static class Callback extends BodyExecutionCallback {
            @Override public void onSuccess(StepContext context, Object result) {
                context.onSuccess(result);
            }
            @Override
            public void onFailure(StepContext context, Throwable t) {
                try {
                    var l = context.get(TaskListener.class);
                    Functions.printStackTrace(t, l.error("Original failure:"));
                    l.getLogger().println("Found in: " + ErrorAction.findOrigin(t, context.get(FlowExecution.class)).getDisplayFunctionName());
                } catch (Exception x) {
                    assert false : x;
                }
                context.onFailure(t);
            }
        }
        @TestExtension("findOriginFromBodyExecutionCallback") public static final class DescriptorImpl extends StepDescriptor {
            @Override public String getFunctionName() {
                return "callsFindOrigin";
            }
            @Override public Set<? extends Class<?>> getRequiredContext() {
                return Set.of();
            }
            @Override public boolean takesImplicitBlockArgument() {
                return true;
            }
        }
    }

    public static final class FailingStep extends Step {
        @DataBoundConstructor public FailingStep() {}
        @Override public StepExecution start(StepContext context) throws Exception {
            return StepExecutions.synchronousNonBlockingVoid(context, c -> c.get(FilePath.class).act(new Sleep(c.get(TaskListener.class))));
        }
        private static final class Sleep extends MasterToSlaveFileCallable<Void> {
            private final TaskListener l;
            Sleep(TaskListener l) {
                this.l = l;
            }
            @Override public Void invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
                l.getLogger().println("Acting slowly in " + f);
                l.getLogger().flush();
                Thread.sleep(Long.MAX_VALUE);
                return null;
            }
        }
        @TestExtension("findOriginFromBodyExecutionCallback") public static final class DescriptorImpl extends StepDescriptor {
            @Override public String getFunctionName() {
                return "fails";
            }
            @Override public Set<? extends Class<?>> getRequiredContext() {
                return Set.of(FilePath.class);
            }
        }
    }

    @Test
    void cyclicErrorsAreSupported() throws Throwable {
        Exception cyclic1 = new Exception();
        Exception cyclic2 = new Exception(cyclic1);
        cyclic1.initCause(cyclic2);
        assertNotNull(new ErrorAction(cyclic1));
        assertNotNull(new ErrorAction(cyclic2));
    }

    @Test
    void unserializableCyclicErrorsAreSupported() throws Throwable {
        Exception unserializable = new MissingMethodException("thisMethodDoesNotExist", String.class, new Object[0]);
        Exception cyclic = new Exception(unserializable);
        unserializable.initCause(cyclic);
        assertNotNull(new ErrorAction(unserializable));
        assertNotNull(new ErrorAction(cyclic));
    }
}

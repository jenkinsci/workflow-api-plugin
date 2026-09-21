/*
 * The MIT License
 *
 * Copyright 2023 CloudBees, Inc.
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

package org.jenkinsci.plugins.workflow.log;

import hudson.console.LineTransformationOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serial;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.BuildWatcherExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.DataBoundConstructor;

@WithJenkins
class TaskListenerDecoratorTest {

    @RegisterExtension
    private static final BuildWatcherExtension buildWatcher = new BuildWatcherExtension();

    private JenkinsRule r;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        r = rule;
    }

    @Test
    void brokenMergedTaskListenerDecorator() throws Exception {
        var p = r.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("mask {broken {echo 'please mask s3cr3t'}}; broken {mask {echo 'please also mask s3cr3t'}}", true));
        var b = r.buildAndAssertSuccess(p);
        r.assertLogNotContains("s3cr3t", b);
        r.assertLogContains("please mask ****", b);
        r.assertLogContains("please also mask ****", b);
        r.assertLogContains("IllegalStateException: oops", b);
        r.assertLogContains(BrokenStep.BrokenDecorator.class.getName(), b);
    }

    public static final class MaskStep extends Step {
        @DataBoundConstructor public MaskStep() {}
        @Override public StepExecution start(StepContext context) throws Exception {
            return new Execution(context);
        }
        private static final class Execution extends StepExecution {
            @Serial
            private static final long serialVersionUID = 1L;
            Execution(StepContext context) {
                super(context);
            }
            @Override public boolean start() throws Exception {
                getContext().newBodyInvoker().withContext(TaskListenerDecorator.merge(getContext().get(TaskListenerDecorator.class), new MaskingDecorator())).withCallback(BodyExecutionCallback.wrap(getContext())).start();
                return false;
            }
        }
        private static final class MaskingDecorator extends TaskListenerDecorator {
            @Override public OutputStream decorate(OutputStream logger) throws IOException, InterruptedException {
                return new LineTransformationOutputStream.Delegating(logger) {
                    @Override protected void eol(byte[] b, int len) throws IOException {
                        out.write(new String(b, 0, len, StandardCharsets.UTF_8).replace("s3cr3t", "****").getBytes(StandardCharsets.UTF_8));
                    }
                };
            }
        }
        @TestExtension("brokenMergedTaskListenerDecorator") public static final class DescriptorImpl extends StepDescriptor {
            @Override public Set<? extends Class<?>> getRequiredContext() {
                return Set.of();
            }
            @Override public String getFunctionName() {
                return "mask";
            }
            @Override public boolean takesImplicitBlockArgument() {
                return true;
            }
        }
    }

    public static final class BrokenStep extends Step {
        @DataBoundConstructor public BrokenStep() {}
        @Override public StepExecution start(StepContext context) throws Exception {
            return new Execution(context);
        }
        private static final class Execution extends StepExecution {
            @Serial
            private static final long serialVersionUID = 1L;
            Execution(StepContext context) {
                super(context);
            }
            @Override public boolean start() throws Exception {
                getContext().newBodyInvoker().withContext(TaskListenerDecorator.merge(getContext().get(TaskListenerDecorator.class), new BrokenDecorator())).withCallback(BodyExecutionCallback.wrap(getContext())).start();
                return false;
            }
        }
        private static final class BrokenDecorator extends TaskListenerDecorator {
            @Override public OutputStream decorate(OutputStream logger) throws IOException, InterruptedException {
                throw new IllegalStateException("oops");
            }
        }
        @TestExtension("brokenMergedTaskListenerDecorator") public static final class DescriptorImpl extends StepDescriptor {
            @Override public Set<? extends Class<?>> getRequiredContext() {
                return Set.of();
            }
            @Override public String getFunctionName() {
                return "broken";
            }
            @Override public boolean takesImplicitBlockArgument() {
                return true;
            }
        }
    }

}

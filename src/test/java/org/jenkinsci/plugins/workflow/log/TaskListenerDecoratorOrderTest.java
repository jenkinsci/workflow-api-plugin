package org.jenkinsci.plugins.workflow.log;

import com.google.common.collect.ImmutableSet;
import hudson.console.ConsoleLogFilter;
import hudson.console.LineTransformationOutputStream;
import hudson.model.AbstractBuild;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.remoting.Channel;
import jenkins.security.MasterToSlaveCallable;
import jenkins.util.JenkinsJVM;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.*;
import org.jvnet.hudson.test.junit.jupiter.BuildWatcherExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.Set;

@For(TaskListenerDecorator.class)
@WithJenkins
class TaskListenerDecoratorOrderTest {

    @RegisterExtension
    private static final BuildWatcherExtension buildWatcher = new BuildWatcherExtension();

    private JenkinsRule r;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        r = rule;
    }

    @Test
    void verifyTaskListenerDecoratorOrder() throws Exception {
        r.createSlave("remote", null, null);
        WorkflowJob p = r.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("filter {node('remote') {remotePrint()}}", true));
        WorkflowRun b = r.buildAndAssertSuccess(p);
        r.assertLogContains("[ApplyOrderDecoratorFactory: job/p/1/] Started", b);
        // TaskListenerDecorator applied before step decorators, modify stream last. pseudo: new StepDecorator(new TaskListenerDecorator(stream))
        r.assertLogContains("[ApplyOrderDecoratorFactory: job/p/1/] [StepLevelDecorator] Running on remote in", b);
        r.assertLogContains("[ApplyOrderDecoratorFactory: job/p/1/ via remote] [StepLevelDecorator via remote] printed a message on master=false", b);
        // now reverse the order
        ApplyOrderDecoratorFactory muteOrderDecoratorFactory=r.jenkins.getExtensionList(TaskListenerDecorator.Factory.class).get(ApplyOrderDecoratorFactory.class);
        muteOrderDecoratorFactory.applyFirst=false;
        b = r.buildAndAssertSuccess(p);
        // TaskListenerDecorator applied after step decorators, modify stream first. pseudo: new TaskListenerDecorator(new StepDecorator(stream))
        r.assertLogContains("[StepLevelDecorator] [ApplyOrderDecoratorFactory: job/p/2/] Running", b);
        r.assertLogContains("[StepLevelDecorator via remote] [ApplyOrderDecoratorFactory: job/p/2/ via remote] printed a message on master=false", b);
    }

    private static final class DecoratorImpl extends TaskListenerDecorator {
        @Serial
        private static final long serialVersionUID = 1L;

        private final String tagName;
        DecoratorImpl(String tagName) {
            this.tagName = tagName;
        }
        @Serial
        private Object writeReplace() {
            Channel ch = Channel.current();
            return ch != null ? new DecoratorImpl(tagName + " via " + ch.getName()) : this;
        }
        @Override public OutputStream decorate(OutputStream logger) throws IOException, InterruptedException {
            return new LineTransformationOutputStream() {
                @Override
                protected void eol(byte[] b, int len) throws IOException {
                    logger.write(("[" + tagName + "] ").getBytes());
                    logger.write(b, 0, len );
                }
                @Override public void close() throws IOException {
                    super.close();
                    logger.close();
                }
                @Override public void flush() throws IOException {
                    logger.flush();
                }
            };
        }
        @Override public String toString() {
            return "DecoratorImpl[" + tagName + "]";
        }
    }

    @TestExtension public static final class ApplyOrderDecoratorFactory implements TaskListenerDecorator.Factory {
        private boolean applyFirst=true;
        @Override public TaskListenerDecorator of(FlowExecutionOwner owner) {
            try {
                return new DecoratorImpl("ApplyOrderDecoratorFactory: "+owner.getUrl());
            } catch (IOException x) {
                throw new AssertionError(x);
            }
        }
        protected void setApplyFirst(boolean applyFirst){
            this.applyFirst=applyFirst;
        }
        @Override
        public boolean isAppliedBeforeMainDecorator() {
            return applyFirst;
        }
    }


    public static final class FilterStep extends Step {
        @DataBoundConstructor public FilterStep() {}
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
                getContext().newBodyInvoker().withContext(new Filter("StepLevelDecorator")).withCallback(BodyExecutionCallback.wrap(getContext())).start();
                return false;
            }
        }
        private static final class Filter extends ConsoleLogFilter implements Serializable {
            @Serial
            private static final long serialVersionUID = 1L;

            private final String message;
            Filter(String message) {
                this.message = message;
            }
            @Serial
            private Object writeReplace() {
                Channel ch = Channel.current();
                return ch != null ? new Filter(message + " via " + ch.getName()) : this;
            }
            @Override public OutputStream decorateLogger(AbstractBuild _ignore, OutputStream logger) throws IOException, InterruptedException {
                return new DecoratorImpl(message).decorate(logger);
            }
            @Override public String toString() {
                return "Filter[" + message + "]";
            }
        }
        @TestExtension public static final class DescriptorImpl extends StepDescriptor {
            @Override public Set<? extends Class<?>> getRequiredContext() {
                return Collections.emptySet();
            }
            @Override public String getFunctionName() {
                return "filter";
            }
            @Override public boolean takesImplicitBlockArgument() {
                return true;
            }
        }
    }

    public static final class RemotePrintStep extends Step {
        @DataBoundConstructor public RemotePrintStep() {}
        @Override public StepExecution start(StepContext context) throws Exception {
            return new Execution(context);
        }
        private static final class Execution extends SynchronousNonBlockingStepExecution<Void> {
            @Serial
            private static final long serialVersionUID = 1L;

            Execution(StepContext context) {
                super(context);
            }
            @Override protected Void run() throws Exception {
                return getContext().get(Node.class).getChannel().call(new PrintCallable(getContext().get(TaskListener.class)));
            }
        }
        private static final class PrintCallable extends MasterToSlaveCallable<Void, RuntimeException> {
            @Serial
            private static final long serialVersionUID = 1L;

            private final TaskListener listener;
            PrintCallable(TaskListener listener) {
                this.listener = listener;
            }
            @Override public Void call() throws RuntimeException {
                listener.getLogger().println("printed a message on master=" + JenkinsJVM.isJenkinsJVM());
                listener.getLogger().flush();
                return null;
            }
        }
        @TestExtension public static final class DescriptorImpl extends StepDescriptor {
            @Override public Set<? extends Class<?>> getRequiredContext() {
                return ImmutableSet.of(Node.class, TaskListener.class);
            }
            @Override public String getFunctionName() {
                return "remotePrint";
            }
        }
    }

}

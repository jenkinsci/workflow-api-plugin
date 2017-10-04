package org.jenkinsci.plugins.workflow.flow;

import com.google.common.collect.ImmutableSet;
import hudson.Extension;
import hudson.FilePath;
import hudson.Util;
import hudson.model.Run;
import hudson.model.TaskListener;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FakeStashStep extends Step {
    private final @Nonnull
    String name;
    private @CheckForNull
    String includes;
    private @CheckForNull
    String excludes;
    private boolean useDefaultExcludes = true;
    private boolean allowEmpty = false;

    @DataBoundConstructor
    public FakeStashStep(@Nonnull String name) {
        Jenkins.checkGoodName(name);
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public String getIncludes() {
        return includes;
    }

    @DataBoundSetter
    public void setIncludes(String includes) {
        this.includes = Util.fixEmpty(includes);
    }

    public String getExcludes() {
        return excludes;
    }

    @DataBoundSetter
    public void setExcludes(String excludes) {
        this.excludes = Util.fixEmpty(excludes);
    }

    public boolean isUseDefaultExcludes() {
        return useDefaultExcludes;
    }

    @DataBoundSetter
    public void setUseDefaultExcludes(boolean useDefaultExcludes) {
        this.useDefaultExcludes = useDefaultExcludes;
    }

    public boolean isAllowEmpty() {
        return allowEmpty;
    }

    @DataBoundSetter
    public void setAllowEmpty(boolean allowEmpty) {
        this.allowEmpty = allowEmpty;
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new Execution(this, context);
    }

    public static class Execution extends SynchronousNonBlockingStepExecution<List<String>> {

        private static final long serialVersionUID = 1L;

        private transient final FakeStashStep step;

        Execution(FakeStashStep step, StepContext context) {
            super(context);
            this.step = step;
        }

        @Override
        protected List<String> run() throws Exception {
            return StashManager.stash(getContext().get(Run.class), step.name, getContext().get(FilePath.class), getContext().get(TaskListener.class), step.includes, step.excludes,
                    step.useDefaultExcludes, step.allowEmpty);
        }

    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {

        @Override
        public String getFunctionName() {
            return "fakeStash";
        }

        @Override
        public String getDisplayName() {
            return "Stash some files to be used later in the build";
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return ImmutableSet.of(Run.class, FilePath.class, TaskListener.class);
        }

        @Override
        public String argumentsToString(Map<String, Object> namedArgs) {
            Object name = namedArgs.get("name");
            return name instanceof String ? (String) name : null;


        }
    }
}

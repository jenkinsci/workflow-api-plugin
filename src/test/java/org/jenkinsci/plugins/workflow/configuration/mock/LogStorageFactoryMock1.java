package org.jenkinsci.plugins.workflow.configuration.mock;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Descriptor;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.log.LogStorage;
import org.jenkinsci.plugins.workflow.log.LogStorageFactory;
import org.kohsuke.stapler.DataBoundConstructor;

public class LogStorageFactoryMock1 implements LogStorageFactory {

    @DataBoundConstructor
    public LogStorageFactoryMock1() {}

    @Override
    public LogStorage forBuild(@NonNull FlowExecutionOwner b) {
        return null;
    }

    @Extension
    @Symbol("logStorageFactoryMock1")
    public static final class DescriptorImpl extends Descriptor<LogStorageFactory> {
        @NonNull
        @Override
        public String getDisplayName() {
            return "Log Storage Factory Mock 1";
        }
    }
}

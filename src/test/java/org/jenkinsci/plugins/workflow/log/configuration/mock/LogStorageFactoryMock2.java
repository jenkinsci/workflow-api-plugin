package org.jenkinsci.plugins.workflow.log.configuration.mock;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.log.LogStorage;
import org.jenkinsci.plugins.workflow.log.LogStorageFactory;
import org.jenkinsci.plugins.workflow.log.LogStorageFactoryDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

public class LogStorageFactoryMock2 implements LogStorageFactory {

    @DataBoundConstructor
    public LogStorageFactoryMock2() {}

    @Override
    public LogStorage forBuild(@NonNull FlowExecutionOwner b) {
        return null;
    }

    @Extension
    @Symbol("logMock2")
    public static final class DescriptorImpl extends LogStorageFactoryDescriptor<LogStorageFactoryMock2> {
        @NonNull
        @Override
        public String getDisplayName() {
            return "Log Storage Factory Mock 2";
        }
    }
}

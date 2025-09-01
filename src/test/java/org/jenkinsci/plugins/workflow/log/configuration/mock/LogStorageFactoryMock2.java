package org.jenkinsci.plugins.workflow.log.configuration.mock;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import java.io.File;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.log.BrokenLogStorage;
import org.jenkinsci.plugins.workflow.log.FileLogStorage;
import org.jenkinsci.plugins.workflow.log.LogStorage;
import org.jenkinsci.plugins.workflow.log.LogStorageFactory;
import org.jenkinsci.plugins.workflow.log.LogStorageFactoryDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

public class LogStorageFactoryMock2 implements LogStorageFactory {

    @DataBoundConstructor
    public LogStorageFactoryMock2() {}

    @Override
    public LogStorage forBuild(@NonNull FlowExecutionOwner b) {
        try {
            File file = new File(b.getRootDir(), "log-mock2");
            return FileLogStorage.forFile(file);
        } catch (Exception x) {
            return new BrokenLogStorage(x);
        }
    }

    @Extension(ordinal = -1)
    @Symbol("logMock2")
    public static final class DescriptorImpl extends LogStorageFactoryDescriptor<LogStorageFactoryMock2> {
        @NonNull
        @Override
        public String getDisplayName() {
            return "Another mock Pipeline logger";
        }
    }
}

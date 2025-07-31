package org.jenkinsci.plugins.workflow.log.tee;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import java.io.File;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.log.BrokenLogStorage;
import org.jenkinsci.plugins.workflow.log.LogStorage;
import org.jenkinsci.plugins.workflow.log.LogStorageFactory;
import org.jenkinsci.plugins.workflow.log.LogStorageFactoryDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

public class RemoteCustomFileLogStorageFactory implements LogStorageFactory {

    @DataBoundConstructor
    public RemoteCustomFileLogStorageFactory() {}

    @Override
    public LogStorage forBuild(@NonNull FlowExecutionOwner b) {
        try {
            File file = new File(b.getRootDir(), "custom-log");
            return RemoteCustomFileLogStorage.forFile(file);
        } catch (Exception x) {
            return new BrokenLogStorage(x);
        }
    }

    @Extension
    @Symbol("customLog")
    public static final class DescriptorImpl extends LogStorageFactoryDescriptor<RemoteCustomFileLogStorageFactory> {
        @NonNull
        @Override
        public String getDisplayName() {
            return "Remote custom file log storage factory";
        }
    }
}

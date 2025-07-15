package org.jenkinsci.plugins.workflow.log;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Descriptor;
import java.io.File;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;
import org.kohsuke.stapler.DataBoundConstructor;

@Restricted(Beta.class)
public class FileLogStorageFactory implements LogStorageFactory {

    @DataBoundConstructor
    public FileLogStorageFactory() {}

    @Override
    public LogStorage forBuild(@NonNull FlowExecutionOwner b) {
        try {
            return FileLogStorage.forFile(new File(b.getRootDir(), "log"));
        } catch (Exception x) {
            return new BrokenLogStorage(x);
        }
    }

    @Extension
    @Symbol("fileLogStorageFactory")
    public static final class DescriptorImpl extends Descriptor<LogStorageFactory> {
        @NonNull
        @Override
        public String getDisplayName() {
            return "File Log Storage Factory";
        }
    }
}

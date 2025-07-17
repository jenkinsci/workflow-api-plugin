package org.jenkinsci.plugins.workflow.log.tee;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Descriptor;
import java.io.File;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.log.LogStorage;
import org.jenkinsci.plugins.workflow.log.LogStorageFactory;

public class RemoteCustomFileLogStorageFactory implements LogStorageFactory {

    private final File file;

    public RemoteCustomFileLogStorageFactory(File file) {
        this.file = file;
    }

    @Override
    public LogStorage forBuild(@NonNull FlowExecutionOwner b) {
        return new RemoteCustomFileLogStorage(file);
    }

    @Override
    public Descriptor<LogStorageFactory> getDescriptor() {
        return null;
    }
}

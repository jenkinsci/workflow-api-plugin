package org.jenkinsci.plugins.workflow.configuration.mock;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Descriptor;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.log.LogStorage;
import org.jenkinsci.plugins.workflow.log.tee.TeeLogStorageFactory;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;

public class TeeLogStorageFactoryMock1 implements TeeLogStorageFactory {

    @DataBoundConstructor
    public TeeLogStorageFactoryMock1() {
    }

    @Override
    public LogStorage forBuild(@NonNull FlowExecutionOwner b) {
        return null;
    }

    @Extension
    @Symbol("teeLogStorageFactoryMock1")
    public static final class DescriptorImpl extends Descriptor<TeeLogStorageFactory> {
        @NonNull
        @Override
        public String getDisplayName() {
            return "Tee Log Storage Factory Mock 1";
        }
    }
}

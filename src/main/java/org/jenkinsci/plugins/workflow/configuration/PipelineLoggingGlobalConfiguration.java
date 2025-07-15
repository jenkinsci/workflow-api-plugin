package org.jenkinsci.plugins.workflow.configuration;

import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.Descriptor;
import java.util.logging.Logger;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.workflow.log.LogStorageFactory;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;
import org.kohsuke.stapler.DataBoundSetter;

@Extension
@Symbol("pipelineLoggingGlobalConfiguration")
@Restricted(Beta.class)
public class PipelineLoggingGlobalConfiguration extends GlobalConfiguration {
    private static final Logger LOGGER = Logger.getLogger(PipelineLoggingGlobalConfiguration.class.getName());
    private LogStorageFactory factory;

    public PipelineLoggingGlobalConfiguration() {
        load();
    }

    public LogStorageFactory getFactory() {
        return factory;
    }

    @DataBoundSetter
    public void setFactory(LogStorageFactory factory) {
        this.factory = factory;
        save();
    }

    public DescriptorExtensionList<LogStorageFactory, Descriptor<LogStorageFactory>> getLogStorageFactoryDescriptors() {
        return LogStorageFactory.all();
    }

    public Descriptor<LogStorageFactory> getDefaultLogStorageFactoryDescriptor() {
        return Jenkins.get().getDescriptor("fileLogStorageFactory");
    }

    public static PipelineLoggingGlobalConfiguration get() {
        return ExtensionList.lookupSingleton(PipelineLoggingGlobalConfiguration.class);
    }
}

package org.jenkinsci.plugins.workflow.log.configuration;

import hudson.Extension;
import hudson.ExtensionList;
import java.util.List;
import java.util.logging.Logger;
import jenkins.model.GlobalConfiguration;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.workflow.log.LogStorageFactory;
import org.jenkinsci.plugins.workflow.log.LogStorageFactoryDescriptor;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;
import org.kohsuke.stapler.DataBoundSetter;

@Extension
@Symbol("pipelineLogging")
@Restricted(Beta.class)
public class PipelineLoggingGlobalConfiguration extends GlobalConfiguration {
    private static final Logger LOGGER = Logger.getLogger(PipelineLoggingGlobalConfiguration.class.getName());
    private LogStorageFactory factory;

    public PipelineLoggingGlobalConfiguration() {
        load();
    }

    public LogStorageFactory getFactory() {
        if (factory == null) {
            factory = LogStorageFactory.getDefaultFactory();
        }
        return factory;
    }

    @DataBoundSetter
    public void setFactory(LogStorageFactory factory) {
        this.factory = factory;
        save();
    }

    public List<LogStorageFactoryDescriptor<?>> getLogStorageFactoryDescriptors() {
        return LogStorageFactory.all();
    }

    public static PipelineLoggingGlobalConfiguration get() {
        return ExtensionList.lookupSingleton(PipelineLoggingGlobalConfiguration.class);
    }
}

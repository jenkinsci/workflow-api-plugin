package org.jenkinsci.plugins.workflow.log.configuration;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.workflow.log.LogStorageFactory;
import org.jenkinsci.plugins.workflow.log.LogStorageFactoryDescriptor;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest2;

@Extension
@Symbol("pipelineLogging")
@Restricted(Beta.class)
public class PipelineLoggingGlobalConfiguration extends GlobalConfiguration {
    private static final Logger LOGGER = Logger.getLogger(PipelineLoggingGlobalConfiguration.class.getName());
    private LogStorageFactory factory;

    public PipelineLoggingGlobalConfiguration() {
        load();
    }

    /**
     * For configuration only. Use {@link #getFactoryOrDefault()} instead.
     */
    @Restricted(NoExternalUse.class)
    public LogStorageFactory getFactory() {
        return factory;
    }

    @DataBoundSetter
    public void setFactory(LogStorageFactory factory) {
        this.factory = factory;
        save();
    }

    @Override
    public boolean configure(StaplerRequest2 req, JSONObject json) throws FormException {
        this.factory = null;
        return super.configure(req, json);
    }

    public LogStorageFactory getFactoryOrDefault() {
        if (factory == null) {
            return LogStorageFactory.getDefaultFactory();
        }
        return factory;
    }

    public List<LogStorageFactoryDescriptor<?>> getLogStorageFactoryDescriptors() {
        List<LogStorageFactoryDescriptor<?>> result = new ArrayList<>();
        result.add(null); // offer the option to use the default factory without any explicit configuration
        result.addAll(LogStorageFactory.all());
        return result;
    }

    public LogStorageFactoryDescriptor<?> getDefaultFactoryDescriptor() {
        return LogStorageFactory.getDefaultFactory().getDescriptor();
    }
    
    public String getDefaultFactoryPlugin() {
        var pluginWrapper = Jenkins.get().getPluginManager().whichPlugin(LogStorageFactory.getDefaultFactory().getClass());
        return pluginWrapper != null ? pluginWrapper.getShortName() : "unknown";
    }

    public static PipelineLoggingGlobalConfiguration get() {
        return ExtensionList.lookupSingleton(PipelineLoggingGlobalConfiguration.class);
    }
}

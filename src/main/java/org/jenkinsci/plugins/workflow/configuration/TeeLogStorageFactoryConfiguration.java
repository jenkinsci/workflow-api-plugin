package org.jenkinsci.plugins.workflow.configuration;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.BulkChange;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.Descriptor;
import hudson.model.DescriptorVisibilityFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.workflow.log.tee.TeeLogStorageFactory;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest2;

@Extension
@Symbol("teeLogStorageFactoryConfiguration")
@Restricted(Beta.class)
public class TeeLogStorageFactoryConfiguration extends GlobalConfiguration {
    private static final Logger LOGGER = Logger.getLogger(TeeLogStorageFactoryConfiguration.class.getName());
    private boolean enabled = false;
    private List<TeeLogStorageFactory> factories = new ArrayList<>();

    public TeeLogStorageFactoryConfiguration() {
        load();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public List<TeeLogStorageFactory> getFactories() {
        return factories;
    }

    public <T> List<T> lookup(Class<T> type) {
        return getFactories().stream().filter(type::isInstance).map(type::cast).toList();
    }

    @DataBoundSetter
    public void setFactories(List<TeeLogStorageFactory> factories) {
        this.factories = factories;
    }

    @DataBoundSetter
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean configure(StaplerRequest2 req, JSONObject json) throws FormException {
        // We have to null out providers before data binding to allow all providers to be deleted in the config UI.
        // We use a BulkChange to avoid double saves in other cases.
        try (BulkChange bc = new BulkChange(this)) {
            factories = new ArrayList<>();
            req.bindJSON(this, json);
            bc.commit();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to save " + getConfigFile(), e);
        }
        return true;
    }

    public static TeeLogStorageFactoryConfiguration get() {
        return ExtensionList.lookupSingleton(TeeLogStorageFactoryConfiguration.class);
    }

    @Extension
    public static class TeeLogStorgeFactoryConfigurationFilter extends DescriptorVisibilityFilter {

        @Override
        public boolean filter(@CheckForNull Object context, @NonNull Descriptor descriptor) {
            if (descriptor instanceof TeeLogStorageFactoryConfiguration) {
                return !Jenkins.get()
                        .getDescriptorList(TeeLogStorageFactory.class)
                        .isEmpty();
            }
            return true;
        }
    }
}

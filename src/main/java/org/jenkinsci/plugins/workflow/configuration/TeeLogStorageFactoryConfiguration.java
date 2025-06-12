package org.jenkinsci.plugins.workflow.configuration;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.BulkChange;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.Descriptor;
import hudson.model.DescriptorVisibilityFilter;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.GlobalConfiguration;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.workflow.log.tee.TeeLogStorageFactory;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest2;

@Extension
@Symbol("teeLogStorageFactoryConfiguration")
@Restricted(NoExternalUse.class)
public class TeeLogStorageFactoryConfiguration extends GlobalConfiguration {
    private static final Logger LOGGER = Logger.getLogger(TeeLogStorageFactoryConfiguration.class.getName());
    private boolean enabled = false;
    private List<TeeLogStorageFactory> factories;

    public TeeLogStorageFactoryConfiguration() {
        load();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public List<TeeLogStorageFactory> getFactories() {
        return factories;
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
            factories = null;
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

    public List<TeeLogStorageFactory> getAll() {
        return ExtensionList.lookup(TeeLogStorageFactory.class);
    }

    //    public List<TeeLogStorageFactory> getFactoryInstances() {
    //        List<TeeLogStorageFactory> result = new ArrayList<>();
    //        List<TeeLogStorageFactory> all = ExtensionList.lookup(TeeLogStorageFactory.class);
    //        for(String factory : factories) {
    //            all.stream().filter(f -> f.getId().equals(factory)).findFirst().ifPresent(result::add);
    //        }
    //        return result;
    //    }

    @Extension
    public static class TeeLogStorgeFactoryConfigurationFilter extends DescriptorVisibilityFilter {

        @Override
        public boolean filter(@CheckForNull Object context, @NonNull Descriptor descriptor) {
            if (descriptor instanceof TeeLogStorageFactoryConfiguration) {
                return !ExtensionList.lookup(TeeLogStorageFactory.class).isEmpty();
            }
            return true;
        }
    }
}

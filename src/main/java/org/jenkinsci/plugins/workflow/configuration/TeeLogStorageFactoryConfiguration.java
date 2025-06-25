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
import java.util.stream.Collectors;
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
    private List<TeeLogStorageFactoryWrapper> wrappers;

    public TeeLogStorageFactoryConfiguration() {
        load();
    }

    public boolean isEnabled() {
        return enabled;
    }

    private Object readResolve() {
        // remove the unknown wrappers
        if (this.wrappers != null) {
            this.wrappers = this.wrappers.stream()
                    .filter(w -> {
                        try {
                            return w.resolve() != null;
                        } catch (AssertionError e) {
                            LOGGER.log(Level.WARNING, e.getMessage(), e);
                            return false;
                        }
                    })
                    .collect(Collectors.toList());
        }
        return this;
    }

    public List<TeeLogStorageFactoryWrapper> getWrappers() {
        return wrappers;
    }

    @DataBoundSetter
    public void setWrappers(List<TeeLogStorageFactoryWrapper> wrappers) {
        this.wrappers = wrappers;
    }

    @DataBoundSetter
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean configure(StaplerRequest2 req, JSONObject json) throws FormException {
        // We have to null out wrappers before data binding to allow all wrappers to be deleted in the config UI.
        // We use a BulkChange to avoid double saves in other cases.
        try (BulkChange bc = new BulkChange(this)) {
            wrappers = null;
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

    public List<Descriptor<TeeLogStorageFactoryWrapper>> getDescriptors() {
        return new TeeLogStorageFactoryWrapper.DescriptorImpl().getDescriptors();
    }

    public List<TeeLogStorageFactory> getAll() {
        return ExtensionList.lookup(TeeLogStorageFactory.class);
    }

    public List<TeeLogStorageFactory> getFactories() {
        if (wrappers == null) {
            return List.of();
        }
        return wrappers.stream().map(TeeLogStorageFactoryWrapper::resolve).collect(Collectors.toList());
    }

    @Extension
    public static class TeeLogStorageFactoryConfigurationFilter extends DescriptorVisibilityFilter {
        @Override
        public boolean filter(@CheckForNull Object context, @NonNull Descriptor descriptor) {
            if (descriptor instanceof TeeLogStorageFactoryConfiguration) {
                return !ExtensionList.lookup(TeeLogStorageFactory.class).isEmpty();
            }
            return true;
        }
    }
}

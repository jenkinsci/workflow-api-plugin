package org.jenkinsci.plugins.workflow.configuration;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.Util;
import hudson.model.Descriptor;
import hudson.model.DescriptorVisibilityFilter;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.workflow.log.tee.TeeLogStorageFactory;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.interceptor.RequirePOST;

@Extension
@Symbol("teeLogStorageFactoryConfiguration")
@Restricted(NoExternalUse.class)
public class TeeLogStorageFactoryConfiguration extends GlobalConfiguration {
    private static final Logger LOGGER = Logger.getLogger(TeeLogStorageFactoryConfiguration.class.getName());
    private boolean enabled = false;
    private String primaryId;
    private String secondaryId;

    public TeeLogStorageFactoryConfiguration() {
        load();
    }

    public boolean isEnabled() {
        return enabled;
    }

    @DataBoundSetter
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        save();
    }

    public String getPrimaryId() {
        return primaryId;
    }

    @DataBoundSetter
    public void setPrimaryId(@CheckForNull String primaryId) {
        this.primaryId = Util.fixEmptyAndTrim(primaryId);
        save();
    }

    public String getSecondaryId() {
        return secondaryId;
    }

    @DataBoundSetter
    public void setSecondaryId(@CheckForNull String secondaryId) {
        this.secondaryId = Util.fixEmptyAndTrim(secondaryId);
        save();
    }

    @Restricted(NoExternalUse.class)
    @RequirePOST
    public ListBoxModel doFillPrimaryIdItems() {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        return fillItems(primaryId);
    }

    @Restricted(NoExternalUse.class)
    @RequirePOST
    public ListBoxModel doFillSecondaryIdItems() {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        return fillItems(secondaryId);
    }

    @Restricted(NoExternalUse.class)
    @RequirePOST
    public FormValidation doCheckPrimaryId(@QueryParameter String primaryId) {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        if (Util.fixEmptyAndTrim(primaryId) == null) {
            return FormValidation.error("Primary Factory must be set");
        }
        return FormValidation.ok();
    }

    @Restricted(NoExternalUse.class)
    @RequirePOST
    public FormValidation doCheckSecondaryId(@QueryParameter String primaryId, @QueryParameter String secondaryId) {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        if (Util.fixEmptyAndTrim(primaryId) != null && Objects.equals(primaryId, secondaryId)) {
            return FormValidation.error("Secondary Factory can't be the same as Primary Factory");
        }
        return FormValidation.ok();
    }

    private ListBoxModel fillItems(String selectedId) {
        ListBoxModel items = new ListBoxModel();
        items.add(new ListBoxModel.Option("", "", selectedId == null));
        for (var factory : TeeLogStorageFactory.all()) {
            items.add(new ListBoxModel.Option(
                    factory.getDisplayName(),
                    factory.getClass().getName(),
                    Objects.equals(selectedId, factory.getClass().getName())));
        }
        return items;
    }

    public static TeeLogStorageFactoryConfiguration get() {
        return ExtensionList.lookupSingleton(TeeLogStorageFactoryConfiguration.class);
    }

    public List<TeeLogStorageFactory> getFactories() {
        if (primaryId == null) {
            return List.of();
        }
        List<TeeLogStorageFactory> factories = new ArrayList<>(2);
        for (var factory : TeeLogStorageFactory.all()) {
            if (factory.getClass().getName().equals(primaryId)) {
                factories.add(0, factory);
                continue;
            }
            if (factory.getClass().getName().equals(secondaryId)) {
                factories.add(1, factory);
                continue;
            }
        }
        return factories;
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

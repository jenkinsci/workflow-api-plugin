package org.jenkinsci.plugins.workflow.configuration;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.Describable;
import hudson.model.Descriptor;
import java.util.List;
import java.util.stream.Collectors;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.workflow.log.tee.TeeLogStorageFactory;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest2;

/**
 * Wrapper of {@link org.jenkinsci.plugins.workflow.log.tee.TeeLogStorageFactory} for UI selection.
 * It avoids having to declare a {@code @DataBoundConstructor} and {@code Describable} on implementations of TeeLogStorageFactory.
 */
public class TeeLogStorageFactoryWrapper implements Describable<TeeLogStorageFactoryWrapper> {
    private final String typeId;

    @DataBoundConstructor
    public TeeLogStorageFactoryWrapper(String typeId) {
        this.typeId = typeId;
    }

    public String getTypeId() {
        return typeId;
    }

    protected TeeLogStorageFactory resolve() {
        return ExtensionList.lookup(TeeLogStorageFactory.class).stream()
                .filter(ext -> ext.getId().equals(typeId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No TeeLogStorageFactory found for type " + typeId));
    }

    @Override
    public Descriptor<TeeLogStorageFactoryWrapper> getDescriptor() {
        TeeLogStorageFactory factory = resolve();
        return new DescriptorImpl.TeeLogStorageFactoryVirtualDescriptor(factory);
    }

    /**
     * This singleton descriptor is a wrapper for generating dynamic {@link }VirtualDescriptor}s.
     * Exists only for a proper Extension initialization.
     */
    @Extension
    public static final class DescriptorImpl extends Descriptor<TeeLogStorageFactoryWrapper> {
        /**
         * Unused, overriden by {@link TeeLogStorageFactoryWrapper#getDescriptor()}
         */
        @Override
        @NonNull
        public String getDisplayName() {
            return "";
        }

        // Used by f:hetero-list
        public List<Descriptor<TeeLogStorageFactoryWrapper>> getDescriptors() {
            return ExtensionList.lookup(TeeLogStorageFactory.class).stream()
                    .map(TeeLogStorageFactoryVirtualDescriptor::new)
                    .collect(Collectors.toList());
        }

        /**
         * Custom dynamic descriptor for each selectable {@link TeeLogStorageFactory}
         */
        private static class TeeLogStorageFactoryVirtualDescriptor extends Descriptor<TeeLogStorageFactoryWrapper> {
            private final TeeLogStorageFactory target;

            private TeeLogStorageFactoryVirtualDescriptor(@NonNull TeeLogStorageFactory target) {
                super(TeeLogStorageFactoryWrapper.class);
                this.target = target;
            }

            @Override
            public TeeLogStorageFactoryWrapper newInstance(@Nullable StaplerRequest2 req, @NonNull JSONObject formData)
                    throws FormException {
                return new TeeLogStorageFactoryWrapper(target.getId());
            }

            @Override
            public String getId() {
                return target.getId();
            }

            @Override
            @NonNull
            public String getDisplayName() {
                return target.getDisplayName();
            }
        }
    }
}

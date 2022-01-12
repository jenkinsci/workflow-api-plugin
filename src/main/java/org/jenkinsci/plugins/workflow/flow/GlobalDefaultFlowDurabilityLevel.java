package org.jenkinsci.plugins.workflow.flow;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.security.Permission;
import hudson.util.ReflectionUtils;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.lang.reflect.InvocationTargetException;

/**
 * Supports a global default durability level for users
 * @author Sam Van Oort
 */
public class GlobalDefaultFlowDurabilityLevel extends AbstractDescribableImpl<GlobalDefaultFlowDurabilityLevel>  {
    /** Currently suggested durability level for pipelines.  */
    public static final FlowDurabilityHint SUGGESTED_DURABILITY_HINT = FlowDurabilityHint.MAX_SURVIVABILITY;

    @Extension
    public static class DescriptorImpl extends Descriptor<GlobalDefaultFlowDurabilityLevel> {

        public DescriptorImpl() {
            super();
            load();
        }

        /** Null to use the platform default, which may change over time as enhanced options are available. */
        private FlowDurabilityHint durabilityHint = null;

        @NonNull
        @Override
        public String getDisplayName() {
            return "Global Default Pipeline Durability Level";
        }

        @CheckForNull
        public FlowDurabilityHint getDurabilityHint() {
            return durabilityHint;
        }

        public void setDurabilityHint(FlowDurabilityHint hint){
            this.durabilityHint = hint;
            save();
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) {
            // TODO verify if this is covered by permissions checks or we need an explicit check here.
            Object ob = json.opt("durabilityHint");
            FlowDurabilityHint hint = null;
            if (ob instanceof String) {
                String str = (String)ob;
                for (FlowDurabilityHint maybeHint : FlowDurabilityHint.values()) {
                    if (maybeHint.name().equals(str)) {
                        hint = maybeHint;
                        break;
                    }
                }
            }
            setDurabilityHint(hint);
            return true;
        }

        public static FlowDurabilityHint getSuggestedDurabilityHint() {
            return GlobalDefaultFlowDurabilityLevel.SUGGESTED_DURABILITY_HINT;
        }

        public static FlowDurabilityHint[] getDurabilityHintValues() {
            return FlowDurabilityHint.values();
        }

        @NonNull
        // TODO: Add @Override when Jenkins core baseline is 2.222+
        public Permission getRequiredGlobalConfigPagePermission() {
            return getJenkinsManageOrAdmin();
        }

        // TODO: remove when Jenkins core baseline is 2.222+
        Permission getJenkinsManageOrAdmin() {
            Permission manage;
            try { // Manage is available starting from Jenkins 2.222 (https://jenkins.io/changelog/#v2.222). See JEP-223 for more info
                manage = (Permission) ReflectionUtils.getPublicProperty(Jenkins.get(), "MANAGE");
            } catch (IllegalArgumentException | InvocationTargetException | NoSuchMethodException | IllegalAccessException e) {
                manage = Jenkins.ADMINISTER;
            }
            return manage;
        }
    }

    public static FlowDurabilityHint getDefaultDurabilityHint() {
        Jenkins j = Jenkins.getInstanceOrNull();
        if (j != null) {
            FlowDurabilityHint hint = j.getExtensionList(DescriptorImpl.class).get(0).durabilityHint;
            if (hint != null) {
                return hint;
            }
        }
        return SUGGESTED_DURABILITY_HINT;
    }
}

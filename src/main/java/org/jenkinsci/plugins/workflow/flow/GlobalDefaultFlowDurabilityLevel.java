package org.jenkinsci.plugins.workflow.flow;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.CheckForNull;

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
            if (this.durabilityHint == null) {
                System.out.println("Null durability hint");
            }
        }

        /** Null to use the platform default, which may change over time as enhanced options are available. */
        private FlowDurabilityHint durabilityHint = null;

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
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
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

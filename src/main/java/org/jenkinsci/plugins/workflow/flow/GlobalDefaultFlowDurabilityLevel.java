package org.jenkinsci.plugins.workflow.flow;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.security.Permission;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;

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

        public FormValidation doCheckDurabilityHint(@QueryParameter("durabilityHint") String durabilityHint) {
            FlowDurabilityHint flowDurabilityHint = Arrays.stream(FlowDurabilityHint.values())
                    .filter(f -> f.name().equals(durabilityHint))
                    .findFirst()
                    .orElse(GlobalDefaultFlowDurabilityLevel.SUGGESTED_DURABILITY_HINT);

            return FormValidation.ok(flowDurabilityHint.getTooltip());
        }

        @Override
        public boolean configure(StaplerRequest2 req, JSONObject json) {
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

        public ListBoxModel doFillDurabilityHintItems() {
            ListBoxModel options = new ListBoxModel();

            options.add("None: use pipeline default (" + GlobalDefaultFlowDurabilityLevel.SUGGESTED_DURABILITY_HINT.getDescription()+ ")", "null");

            List<ListBoxModel.Option> mappedOptions = Arrays.stream(FlowDurabilityHint.values())
                    .map(hint -> new ListBoxModel.Option(hint.getDescription(), hint.name()))
                    .collect(Collectors.toList());

            options.addAll(mappedOptions);

            return options;
        }

        @NonNull
        @Override
        public Permission getRequiredGlobalConfigPagePermission() {
            return Jenkins.MANAGE;
        }
    }

    public static FlowDurabilityHint getDefaultDurabilityHint() {
        Jenkins j = Jenkins.getInstanceOrNull();
        if (j != null) {
            List<DescriptorImpl> descriptor = j.getExtensionList(DescriptorImpl.class);
            if (descriptor.isEmpty()) {
                return SUGGESTED_DURABILITY_HINT;
            }
            FlowDurabilityHint hint = descriptor.get(0).durabilityHint;
            if (hint != null) {
                return hint;
            }
        }
        return SUGGESTED_DURABILITY_HINT;
    }
}

package org.jenkinsci.plugins.workflow.flow;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Item;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Provides a way to indirectly register durability settings to apply to pipelines.
 */
public interface DurabilityHintProvider extends ExtensionPoint {


    int ordinal();

    @CheckForNull
    FlowDurabilityHint suggestFor(@NonNull Item x);

    static @NonNull FlowDurabilityHint suggestedFor(@NonNull Item x) {
        int ordinal = Integer.MAX_VALUE;
        FlowDurabilityHint hint = GlobalDefaultFlowDurabilityLevel.getDefaultDurabilityHint();

        for (DurabilityHintProvider p : ExtensionList.lookup(DurabilityHintProvider.class)) {
            FlowDurabilityHint h = p.suggestFor(x);
            if (h != null) {
                if (p.ordinal() < ordinal) {
                    hint = h;
                    ordinal = p.ordinal();
                }
            }
        }
        return hint;
    }
}

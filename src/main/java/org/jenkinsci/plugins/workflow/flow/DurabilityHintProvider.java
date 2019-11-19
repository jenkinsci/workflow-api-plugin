package org.jenkinsci.plugins.workflow.flow;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Item;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

/**
 * Provides a way to indirectly register durability settings to apply to pipelines.
 */
public interface DurabilityHintProvider extends ExtensionPoint {


    int ordinal();

    @CheckForNull
    FlowDurabilityHint suggestFor(@Nonnull Item x);

    static @Nonnull FlowDurabilityHint suggestedFor(@Nonnull Item x) {
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

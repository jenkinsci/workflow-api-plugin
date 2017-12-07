package org.jenkinsci.plugins.workflow.actions;

import hudson.model.Result;

import javax.annotation.Nonnull;

public final class FlowNodeStatusAction implements PersistentAction {
    private final Result result;

    public FlowNodeStatusAction(@Nonnull Result result) {
        this.result = result;
    }

    @Nonnull
    public Result getResult() {
        return result;
    }

    @Override
    public String getIconFileName() {
        return null;  // If we add one then we can easily use this with UI renderings
    }

    @Override
    public String getDisplayName() {
        return "Status";
    }

    @Override
    public String getUrlName() {
        return null;
    }

}

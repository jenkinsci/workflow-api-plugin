package org.jenkinsci.plugins.workflow.actions;

import hudson.model.Action;

/**
 * This is a marker interface for an action that can't be contributed by a {@link jenkins.model.TransientActionFactory}.
 * Actions implementing this can use more efficient {@link hudson.model.Actionable#getAction(Class)} variant
 */
public interface PersistentAction extends Action {
}

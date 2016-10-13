package org.jenkinsci.plugins.workflow.actions;

import hudson.model.Action;

/**
 * Marker interface for an action that can't be contributed by a {@link jenkins.model.TransientActionFactory}
 * Actions implementing this can use more efficient getAction methods
 */
public interface PersistentAction extends Action {
}

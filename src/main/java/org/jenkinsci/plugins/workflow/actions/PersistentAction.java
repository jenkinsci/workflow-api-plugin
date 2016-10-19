package org.jenkinsci.plugins.workflow.actions;

import hudson.model.Action;

/**
 * This is a marker interface for an action that can't be contributed by a {@link jenkins.model.TransientActionFactory}.
 * Actions implementing this can use more efficient {@link org.jenkinsci.plugins.workflow.graph.FlowNode#getPersistentAction(Class)}
 *   and {@link org.jenkinsci.plugins.workflow.graph.FlowNode#getAction(Class)} internally delegates to that
 */
public interface PersistentAction extends Action {
}

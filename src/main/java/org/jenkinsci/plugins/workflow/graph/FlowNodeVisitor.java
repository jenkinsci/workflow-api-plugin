package org.jenkinsci.plugins.workflow.graph;

import javax.annotation.Nonnull;

/**
 * Interface used when examining a pipeline FlowNode graph
 */
public interface FlowNodeVisitor {
    /**
     * Visit the flow node, and indicate if we should continue analysis
     *
     * @param f Node to visit
     * @return False if we should stop visiting nodes
     */
    public boolean visit(@Nonnull FlowNode f);
}

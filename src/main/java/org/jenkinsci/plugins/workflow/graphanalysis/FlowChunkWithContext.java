package org.jenkinsci.plugins.workflow.graphanalysis;

import org.jenkinsci.plugins.workflow.graph.FlowNode;

import javax.annotation.CheckForNull;

/** FlowChunk with information about what comes before/after */
public interface FlowChunkWithContext extends FlowChunk {

    /** Return the node before this chunk, or null if it is the beginning */
    @CheckForNull
    FlowNode getNodeBefore();

    /** Return the node after this chunk, or null if it is the end */
    @CheckForNull
    FlowNode getNodeAfter();
}

package org.jenkinsci.plugins.workflow.graphanalysis;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

import javax.annotation.Nonnull;
import java.util.ArrayDeque;

/**
 * Fancier visitor class supporting markers, FlowStorage, etc
 */
public abstract class AdvancedVisitor {
    protected Predicate<FlowNode> markerTest = (Predicate)Predicates.alwaysFalse();
    protected FlowChunkStorage chunkStorage;

    protected ArrayDeque<FlowChunk> scopes = new ArrayDeque<FlowChunk>();

    public AdvancedVisitor(FlowChunkStorage storageEngine) {
        this.chunkStorage = storageEngine;
    }

    public Predicate<FlowNode> getMarkerTest() {
        return markerTest;
    }

    public void setMarkerTest(Predicate<FlowNode> markerTest) {
        this.markerTest = markerTest;
    }

    /** Visitor core that uses internal info from the scanner */
    public abstract boolean visitSpecial(@Nonnull FlowNode node, @Nonnull ForkScanner scanner);
}

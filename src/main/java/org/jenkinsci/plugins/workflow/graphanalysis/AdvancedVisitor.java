package org.jenkinsci.plugins.workflow.graphanalysis;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

import javax.annotation.Nonnull;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;

/**
 * Fancier visitor class supporting markers, FlowStorage, etc
 */
@edu.umd.cs.findbugs.annotations.SuppressWarnings(
        value={"URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD",
                "UUF_UNREAD_PUBLIC_OR_PROTECTED_FIELD",
                "UUF_UNUSED_PUBLIC_OR_PROTECTED_FIELD"},
        justification="Still implementing")
public abstract class AdvancedVisitor {
    protected Predicate<FlowNode> markerTest = (Predicate)Predicates.alwaysFalse();

    @edu.umd.cs.findbugs.annotations.SuppressWarnings( value="UUF_UNUSED_FIELD", justification="Implementation use in progress")
    protected FlowChunkStorage chunkStorage;

    protected ArrayDeque<FlowChunk> scopes = new ArrayDeque<FlowChunk>();

    /** Stores ancilliary information on chunks so individual classes don't get cluttered */
    protected IdentityHashMap<FlowChunk, ChunkMetaData> chunkMetaData = new IdentityHashMap<FlowChunk, ChunkMetaData>();

    /** Outputs */
    protected Collection<FlowChunk> markedChunks = new ArrayDeque<FlowChunk>();

    /** Implicit chunk that holds the entire outer scope, may be replaced with a new outer one */
    protected FlowChunk outerScope;

    @edu.umd.cs.findbugs.annotations.SuppressWarnings( value="UUF_UNUSED_FIELD", justification="Implementation use in progress")
    protected static class ChunkMetaData {
        boolean isComplete;
        boolean isExecuted;
        boolean isErrored;
    }

    /** Override me: if true, we care about a given block type based on its start node type  */
    public boolean careAboutBlock(FlowNode blockStartNode) {
        return true;
    }

    public AdvancedVisitor(FlowChunkStorage storageEngine) {
        this.chunkStorage = storageEngine;
    }

    public Predicate<FlowNode> getMarkerTest() {
        return markerTest;
    }

    public void setMarkerTest(Predicate<FlowNode> markerTest) {
        this.markerTest = markerTest;
    }

    public Collection<FlowChunk> getMarkedChunks() {
        return Collections.unmodifiableCollection(markedChunks);
    }

    public ArrayDeque<FlowChunk> getScopes() {
        return scopes;
    }

    /** Visitor core that uses internal info from the scanner */
    public boolean visitSpecial(@Nonnull FlowNode node, @Nonnull ForkScanner scanner) {
        // TODO Push/pop to arrayDeques of scopes with blocks, filtering on careAboutBlock and not adding if failed
        // Marker check, add to markers as needed
        // TODO invocations of storage for parallels
        // TODO save the timing information using very simple calculations
        // TODO recursive add/calc of timing -- add up the tree of block scopes


        // TODO when we add a new (incomplete) enclosing block, add the pause times of children

        return true;
    }
}

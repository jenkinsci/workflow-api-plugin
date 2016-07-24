package org.jenkinsci.plugins.workflow.graphanalysis;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import org.jenkinsci.plugins.workflow.graph.BlockEndNode;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
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
public class AdvancedVisitor {

    // FIXME for purposes of tracking nodes *after* a block you might use a HashMap<FlowNode, FlowChunk>

    protected Predicate<FlowNode> markerTest = (Predicate)Predicates.alwaysFalse();

    @edu.umd.cs.findbugs.annotations.SuppressWarnings( value="UUF_UNUSED_FIELD", justification="Implementation use in progress")
    protected FlowChunkStorage chunkStorage;

    protected ArrayDeque<FlowChunk> scopes = new ArrayDeque<FlowChunk>();

    /** Stores ancilliary information on chunks so individual classes don't get cluttered */
    protected IdentityHashMap<FlowChunk, ChunkMetaData> chunkMetaData = new IdentityHashMap<FlowChunk, ChunkMetaData>();

    /** Outputs -- think stages */
    protected Collection<FlowChunk> markedChunks = new ArrayDeque<FlowChunk>();

    /** Implicit chunk that holds the entire outer scope, may be replaced with a new outer one */
    protected FlowChunk outerScope;

    @edu.umd.cs.findbugs.annotations.SuppressWarnings( value="UUF_UNUSED_FIELD", justification="Implementation use in progress")
    protected static class ChunkMetaData {
        boolean isComplete;
        boolean isExecuted;
        boolean isErrored;
    }

    public AdvancedVisitor(FlowChunkStorage storageEngine) {
        this.chunkStorage = storageEngine;
        outerScope = storageEngine.createBase();
    }

    public Predicate<FlowNode> getMarkerTest() {
        return markerTest;
    }

    /** Marks chunks of interest, for example stages, if true we start a new chunk of interest
     *  Every time the predicate evaluates true, a new marked chunk is begun.
     *  A common one would be a predicate that tells you when you've hit the start of a stage
     */
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

        if (getMarkerTest().apply(node)) {
            //
        }
        if (node instanceof BlockEndNode) {

        } else if (node instanceof BlockStartNode) {
            // check if end for current start, otherwise create a new block above current
        } else {

        }

        // TODO Push/pop to arrayDeques of scopes with blocks, filtering on careAboutBlock and not adding if failed
        // Marker check, add to markers as needed
        // TODO invocations of storage for parallels
        // TODO save the timing information using very simple calculations from the pipeline-graph-analysis-plugin
        // TODO recursive add/calc of timing -- add up the tree of block scopes
        // TODO when we add a new (incomplete) enclosing block, add the pause times of children

        // Timing note, if we can track linear pause times and branchwise pause times life will be easier

        return true;
    }
}

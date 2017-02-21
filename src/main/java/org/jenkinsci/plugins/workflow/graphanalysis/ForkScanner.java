/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jenkinsci.plugins.workflow.graphanalysis;

import com.google.common.base.Predicate;
import org.jenkinsci.plugins.workflow.actions.ThreadNameAction;
import org.jenkinsci.plugins.workflow.actions.TimingAction;
import org.jenkinsci.plugins.workflow.graph.BlockEndNode;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowEndNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graph.StepNode;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Scanner that will scan down all forks when we hit parallel blocks before continuing, but generally runs in linear order
 * <p>Think of it as the opposite of {@link DepthFirstScanner}.
 *
 * <p>This is a fairly efficient way to visit all FlowNodes, and provides four useful guarantees:
 * <ul>
 *   <li>Every FlowNode is visited, and visited EXACTLY ONCE (not true for LinearScanner, which misses some)</li>
 *   <li>All parallel branches are visited before we move past the parallel block (not true for DepthFirstScanner)</li>
 *   <li>For parallels, we visit branches in reverse order (in fitting with end to start general flow)</li>
 *   <li>For EVERY block, the BlockEndNode is visited before the BlockStartNode (not true for DepthFirstScanner, with parallels)</li>
 * </ul>
 *
 * <p>The big advantages of this approach:
 * <ul>
 *     <li>Blocks are visited in the order they end (no backtracking) - helps with working a block at a time</li>
 *     <li>Points are visited in linear order within a block (easy to use for analysis)</li>
 *     <li>Minimal state information needed</li>
 *     <li>Branch information is available for use here</li>
 * </ul>
 *
 * @author Sam Van Oort
 */
@NotThreadSafe
public class ForkScanner extends AbstractFlowScanner {

    @CheckForNull
    public NodeType getCurrentType() {
        return currentType;
    }

    @CheckForNull
    public NodeType getNextType() {
        return nextType;
    }

    /** Used to recognize special nodes */
    public enum NodeType {
        /** Not any of the parallel types */
        NORMAL,
        /**{@link BlockStartNode} starting a parallel block */
        PARALLEL_START,
        /**{@link BlockEndNode} ending a parallel block */
        PARALLEL_END,
        /**{@link BlockStartNode} starting a branch of a parallel */
        PARALLEL_BRANCH_START,
        /**{@link BlockEndNode} ending a parallel block... or last executed nodes */
        PARALLEL_BRANCH_END,
    }

    /** Current node in chunk */
    ChunkTreeNode currentChunkNode = null;

    /** Container class that provides DOM-like iteration methods.
     *  This will be grossly inefficient vs. direct iteration because of heavy object use, but gets the job done.
     *  Revisit: determine if better to make this an interface that is implemented by a {@link FlowChunk} descendant.
     *
     *  Note: in a parallel, the children are parallel branches, in a normal block they are sequential.
     */
    static class ChunkTreeNode {
        FlowChunk chunk;
        ChunkTreeNode parent;
        List<ChunkTreeNode> children = null;
        boolean childrenParallel = false;

        /** Prev sibling if it has one, otherwise parent */

        /** If true, child nodes are parallel branches, otherwise they are sequential */
        public boolean isChildrenParallel() {
            return childrenParallel;
        }

        public void setChildrenParallel(boolean isParallel) {
            this.childrenParallel = isParallel;
        }

        @CheckForNull
        public ChunkTreeNode getPrevChunk() {
            ChunkTreeNode sibling = getPrevSibling();
            return (sibling != null) ? sibling : parent;
        }

        @CheckForNull
        public ChunkTreeNode getPrevSibling() {
            if (parent != null && parent.hasChildren()) {
                List<ChunkTreeNode> sibs = parent.getChildren();
                if (sibs != null) {
                    int idx = sibs.indexOf(this);
                    if (idx > 0) {
                        return sibs.get(idx-1);
                    }
                }
            }
            return null;
        }

        @CheckForNull
        public ChunkTreeNode getNextSibling() {
            if (parent != null && parent.hasChildren()) {
                List<ChunkTreeNode> sibs = parent.getChildren();
                if (sibs != null) {
                    int idx = sibs.indexOf(this);
                    if (idx < sibs.size()) {
                        return sibs.get(idx++);
                    }
                }
            }
            return null;
        }

        // TODO add getNextChunk method

        @CheckForNull
        public ChunkTreeNode getParent() {
            return parent;
        }

        public void setParent(ChunkTreeNode node) {
            this.parent = node;
        }

        public FlowChunk getChunk(){
            return chunk;
        }

        public boolean hasChildren() {
            return children != null && children.size() > 0;
        }

        public void setChildren(Collection<ChunkTreeNode> nodes) {
            for (ChunkTreeNode c : children) {
                c.setParent(null);  // Avoid memory leaks
            }
            children.clear();
            children.addAll(nodes);
            for (ChunkTreeNode c : children) {
                c.setParent(this);
            }
        }

        @CheckForNull
        public ChunkTreeNode getFirstChild() {
            return (children != null && !children.isEmpty()) ? children.get(0) : null;
        }

        @CheckForNull
        public ChunkTreeNode getLastChild() {
            return (children != null && !children.isEmpty()) ? children.get(children.size()-1) : null;
        }

        /** Find the enclosing node that must be a parent of this one.
         *  While technically this is O(n) where n is depth, in practice depth is generally small.
         *  If that assumption is violated, we can always use a HashMap of (first FlowNode, ChunkTreeNode).
         */
        public ChunkTreeNode findEnclosingNode(@Nonnull FlowNode startOfBlock) {
            ChunkTreeNode candidate = this;
            ChunkTreeNode found = null;
            // Search until we hit the root for an enclosing block with that node as the first
            while (found == null && candidate != null) {
                if (candidate.getChunk() != null && startOfBlock.equals(candidate.getChunk().getFirstNode())) {
                    found = candidate;
                } else {
                    candidate = candidate.getParent();
                }
            }
            return found;
        }

        ChunkTreeNode getEnclosingParallel() {
            ChunkTreeNode temp = this;
            while (temp != null) {
                if (temp.isChildrenParallel()) {
                    return temp;
                }
                temp = temp.parent;
            }
            return null;
        }

        int getParallelDepth() {
            ChunkTreeNode temp = this;
            int count = 0;
            while (temp != null) {
                if (temp.isChildrenParallel()) {
                    count++;
                }
                temp = temp.parent;
            }
            return count;
        }

        public List<ChunkTreeNode> getChildren() {
            return (children == null) ? Collections.EMPTY_LIST : Collections.unmodifiableList(children);
        }

        /** Adds a child as needed */
        public ChunkTreeNode prependChild(ChunkTreeNode child) {
            if (children == null) {
                children = new ArrayList<ChunkTreeNode>();
            }
            children.add(0, child);
            child.setParent(this);
            return this;
        }

        /** Adds a child as needed */
        public ChunkTreeNode appendChild(ChunkTreeNode child) {
            if (children == null) {
                children = new ArrayList<ChunkTreeNode>();
            }
            children.add(child);
            child.setParent(this);
            return this;
        }

        ChunkTreeNode(@Nonnull FlowChunk myChunk) {
            this.chunk = myChunk;
        }

        /** Tries to create a parallel structure given the end node */
        ChunkTreeNode(@Nonnull BlockEndNode parallelEndNode) {
            FlowNode parallelStart = parallelEndNode.getStartNode();
            for (FlowNode f : parallelEndNode.getParents()) {
                if (!(f instanceof BlockEndNode)) {
                    throw new IllegalArgumentException("Expected BlockEndNode for the end of a branch, but  "+f+"was not!");
                }
                BlockStartNode bsn = ((BlockEndNode)f).getStartNode();
                this.appendChild(new ChunkTreeNode(new MemoryFlowChunk(parallelStart, bsn, f, parallelEndNode)));
            }
            this.chunk = new MemoryFlowChunk(null, parallelStart, parallelEndNode, null);
            this.childrenParallel = true;
        }

        ChunkTreeNode(FlowChunk chunk, ChunkTreeNode parent, List<ChunkTreeNode> children) {
            this.chunk = chunk;
            this.parent = parent;
            this.children = children;
        }
    }

    private boolean walkingFromFinish = false;

    NodeType currentType = null;
    NodeType nextType = null;

    public ForkScanner() {

    }

    public ForkScanner(@Nonnull Collection<FlowNode> heads) {
        this.setup(heads);
    }

    public ForkScanner(@Nonnull Collection<FlowNode> heads, @Nonnull Collection<FlowNode> blackList) {
        this.setup(heads, blackList);
    }

    @Override
    protected void reset() {
        currentChunkNode = null;
        myCurrent = null;
        myNext = null;
        currentType = null;
        nextType = null;
    }

    /** Works with workflow-cps 2.26 and up, otherwise you'll need to provide your own predicate
     *   However this is better than the previous (always false predicate).
     */
    public static class IsParallelStartPredicate implements Predicate<FlowNode> {
        static final String PARALLEL_DESCRIPTOR_CLASSNAME = "org.jenkinsci.plugins.workflow.cps.steps.ParallelStep";

        @Override
        public boolean apply(@Nullable FlowNode input) {
            if (input == null || !(input instanceof StepNode && input instanceof BlockStartNode)) {
                return false;
            } else {
                StepDescriptor desc = ((StepNode)input).getDescriptor();
                // FIXME way to use reflection the first time to obtain a reference to getDescriptor if workflow-cps <= 2.26?
                return desc != null && PARALLEL_DESCRIPTOR_CLASSNAME.equals(desc.getId()) && input.getPersistentAction(ThreadNameAction.class) == null;
            }
        }

        @Override
        public boolean equals(@Nullable Object object) {
            return object != null && object instanceof IsParallelStartPredicate;
        }
    }

    /** Originally a workaround to deal with needing the {@link StepDescriptor} to determine if a node is a parallel start
     *  Now tidily solved by {@link IsParallelStartPredicate}*/
    private static Predicate<FlowNode> parallelStartPredicate = new IsParallelStartPredicate();

    // Invoke this passing a test against the ParallelStep conditions
    public static void setParallelStartPredicate(@Nonnull Predicate<FlowNode> pred) {
        parallelStartPredicate = pred;
    }

    // Needed because the *next* node might be a parallel start if we start in middle and we don't know it
    public static boolean isParallelStart(@CheckForNull FlowNode f) {
        return parallelStartPredicate.apply(f);
    }

    // Needed because the *next* node might be a parallel end and we don't know it from a normal one
    public static boolean isParallelEnd(@CheckForNull FlowNode f) {
        return f != null && f instanceof BlockEndNode && (f.getParents().size()>1 || isParallelStart(((BlockEndNode) f).getStartNode()));
    }

    /**
     * Check the type of a given {@link FlowNode} for purposes of parallels, or return null if node is null.
     */
    @CheckForNull
    public static NodeType getNodeType(@CheckForNull FlowNode f) {
        if (f == null) {
            return null;
        }
        if (f instanceof BlockStartNode) {
            if (f.getPersistentAction(ThreadNameAction.class) != null) {
                return NodeType.PARALLEL_BRANCH_START;
            } else if (isParallelStart(f)) {
                return NodeType.PARALLEL_START;
            } else {
                return NodeType.NORMAL;
            }
        } else if (f instanceof BlockEndNode) {
            BlockStartNode start = ((BlockEndNode)f).getStartNode();
            NodeType type = getNodeType(start);
            switch (type) {
                case PARALLEL_BRANCH_START:
                    return NodeType.PARALLEL_BRANCH_END;
                case PARALLEL_START:
                    return NodeType.PARALLEL_END;
                default:
                    return NodeType.NORMAL;
            }
        } else {
            return NodeType.NORMAL;
        }
    }

    /** If true, we are walking from the flow end node and have a complete view of the flow
     *  Needed because there are implications when not walking from a finished flow (blocks without a {@link BlockEndNode})*/
    public boolean isWalkingFromFinish() {
        return walkingFromFinish;
    }

    /** Return the label of this node if it is a branch start node, otherwise null */
    @CheckForNull
    static String getBranchStartLabel(@Nonnull FlowNode f) {
        ThreadNameAction tna = f.getPersistentAction(ThreadNameAction.class);
        return (tna != null) ? tna.getThreadName() : null;
    }

    private static final Predicate<FlowNode> IS_BRANCH_HEAD = new Predicate<FlowNode>() {
        @Override
        public boolean apply(@Nullable FlowNode input) {
            return (input == null) ? false : getBranchStartLabel(input) != null;
        }
    };

    /**
     * Create the necessary information about parallel blocks in order to provide flowscanning from inside incomplete parallel branches
     * This works by walking back to construct the tree of parallel blocks covering all heads back to the Least Common Ancestor of all heads
     *  (the top parallel block).  One by one, as branches join, we remove them from the list of live pieces and replace with their common ancestor.
     *
     * <p> The core algorithm is simple in theory but the many cases render the implementation quite complex. In gist:
     * //FIXME Update this for the revised algorithm
     * <ul>
     *     <li>We track FlowPieces, which are Forks (where branches merge) and FlowSegments (where there's a unforked sequence of nodes)</li>
     *     <li>A map of FlowNode to its containing FlowPiece is created </li>
     *     <li>For each head we start a new FlowSegment and create an iterator of all enclosing blocks (all we need for this)</li>
     *     <li>We do a series of passes through all iterators looking to see if the parent of any given piece maps to an existing FlowPiece</li>
     *     <ol>
     *         <li>Where there are no mappings, we add another node to the FlowSegment</li>
     *         <li>Where an existing piece exists, <strong>if it's a Fork</strong>, we add the current piece on as a new branch</li>
     *         <li>Where an existing piece exists <strong>if it's a FlowSegment</strong>, we create a fork:
     *              <ul><li>If we're joining at the most recent point, create a Fork with both branches following it, and replace that item's ForkSegment in the piece list with a Fork</li>
     *              <li>If joining midway through, split the segment and create a fork as needed</li></ul>
     *         </li>
     *         <li>When two pieces join together, we remove one from the list</li>
     *         <li>When we're down to a single piece, we have the full ancestry & we're done</li>
     *         <li>When we're down to a single piece, all heads have merged and we're done</li>
     *     </ol>
     *     <li>Each time we merge a branch in, we need to remove an entry from enclosing blocks & live pieces</li>
     * </ul>
     *
     * <p>  There are some assumptions you need to know about to understand why this works:
     * <ul>
     *     <li>None of the pieces have multiple parents, since we only look at enclosing blocks (only be a BlockEndNodes for a parallel block have multipel parents)</li>
     *     <li>No cycles exist in the graph</li>
     *     <li>Flow graphs are correctly constructed</li>
     *     <li>Heads are all separate branches</li>
     * </ul>
     *
     * @param nodes heads
     */
    @CheckForNull
    ChunkTreeNode buildParallelStructure(Collection<FlowNode> nodes) {
        // FIXME finish implementation
        if (nodes == null || nodes.size() <= 1) {
            return null;
        }

        ChunkTreeNode root = null;  // Will be declared when found
        HashMap<FlowNode, ChunkTreeNode> parallelStartMap = new HashMap<FlowNode, ChunkTreeNode>();
        HashMap<FlowNode, ChunkTreeNode> unmergedChunks = new HashMap<FlowNode, ChunkTreeNode>();

        // Need pointers to parallel starts for purposes of connecting up branches
        for (FlowNode f : nodes) {
            MemoryFlowChunk mfc = new MemoryFlowChunk(null, f, f, null);  // Singleton node, the first part will be added to it soon
            unmergedChunks.put(f, new ChunkTreeNode(mfc, null, null));
        }

        // 3 Cases:
        // 1 - BlockEndNode for parallel -> create parallel chunk, and connect up as needed
        // 2 - BlockStartNode with ThreadNameAction - this is the start of a parallel branch, create a parallel or connect to existing
        // 3 - ParallelStartNode with no preceding end node
        // 4 - default


        // For each of the unmergedNodes, walk back until we find a node with ThreadNameAction or a ParallelEndNode?
        LinearScanner scan = new LinearScanner();  // We need the block end node
        while(unmergedChunks.size() > 1) {
            for (Map.Entry<FlowNode, ChunkTreeNode> unmergedBit : unmergedChunks.entrySet()) {
                // FIXME I can't directly mutate the map like this!
                FlowNode f = unmergedBit.getKey();
                ChunkTreeNode node = unmergedBit.getValue();

                // Look for branch starts from parallels that we haven't seen an end node for
                // TODO do same with branchTip = scan.findFirstMatch(f, IS_BRANCH_HEAD);
                if (f instanceof BlockEndNode && IS_BRANCH_HEAD.apply(((BlockEndNode)f).getStartNode())) {
                    // We've hit a branch start, let's create a parallel containing it and put a pointer to catch additional pointersback
                    node.chunk = new MemoryFlowChunk(null, f, node.getChunk().getLastNode(), null);
                    FlowNode parallelStart = f.getParents().get(0); // NEEDS A CHECK!
                    ChunkTreeNode possibleStart = parallelStartMap.get(parallelStart);
                    if (possibleStart != null) { // Merged me!
                        possibleStart.appendChild(node); // Huzzah we merged
                        unmergedChunks.remove(f);  // FIXME, can't remove this directly!
                    } else {
                        // Create a new parallel and attach the branch onto it
                        ChunkTreeNode parallel = new ChunkTreeNode(null, null, null);
                        parallel.appendChild(node);
                        parallelStartMap.put(parallelStart, parallel);  // Fixme non-null chunk for
                        unmergedChunks.put(parallelStart, parallel);
                        unmergedChunks.remove(f);
                    }
                }

                // TODO add in parallels that we see an end node for!
                // TODO clear out if we hit the start
                // Merge into parallel
            }
        }
        return null;
    }

    @Override
    protected void setHeads(@Nonnull Collection<FlowNode> heads) {
        // FIXME I need to do something with the tips, similar to what we do in nextTree if they're normal-type
        // Otherwise NPEs
        if (heads.size() > 1) {
            this.currentChunkNode = buildParallelStructure(new LinkedHashSet<FlowNode>(heads));
            assert this.currentChunkNode != null;
            myCurrent = this.currentChunkNode.getChunk().getLastNode();  // Verifyme
            myNext = myCurrent;
            nextType = NodeType.PARALLEL_BRANCH_END;
            walkingFromFinish = false;
        } else {
            FlowNode f = heads.iterator().next();
            walkingFromFinish = f instanceof FlowEndNode;
            myCurrent = f;
            myNext = f;
            nextType = getNodeType(f);
            // Checkme: we may need to do housekeeping with parallel tips
        }
        currentType = null;
    }

    /**
     * Return the node that begins the current parallel head
     * @return The FlowNode that marks current parallel start
     */
    @CheckForNull
    public FlowNode getCurrentParallelStartNode() {
        if (currentChunkNode != null) {
            ChunkTreeNode parallel = currentChunkNode.getEnclosingParallel();
            if (parallel != null && parallel.getChunk() != null) {
                return parallel.getChunk().getFirstNode();
            }
        }
        return null;
    }


    /** Return number of levels deep we are in parallel blocks */
    public int getParallelDepth() {
        return (currentChunkNode != null) ? currentChunkNode.getParallelDepth() : 0;
    }

    @Override
    public FlowNode next() {
        currentType = nextType;
        FlowNode output = super.next();
        return output;
    }

    /** Find next node using the current tree structure if needed */
    protected FlowNode nextTree(@Nonnull FlowNode current, @Nonnull Collection<FlowNode> blackList) {
        FlowNode output = null;
        List<FlowNode> parents = current.getParents();
        ChunkTreeNode currentTreeNode = this.currentChunkNode;
        FlowChunk nextChunk = (currentTreeNode != null) ? currentTreeNode.getChunk() : null;

        if (parents.size() == 0) {  // End of the line, bub!
            nextType = null;
            return null;
        }

        // Potential candidates for next value
        // Potential candidates for next value
        ChunkTreeNode plannedTreeNode = null;
        NodeType plannedNextType = null;
        FlowNode plannedNext = null;

        // Special cases for how we generate the next branch depending on if inside parallels
        switch (currentType) {
            case PARALLEL_BRANCH_START:
                // Try to jump to previous branch (if not blacklisted), or the enclosing parent parallel block
                plannedTreeNode = currentTreeNode.getPrevChunk();
                if (plannedTreeNode != null) {
                    if (plannedTreeNode == currentTreeNode.getParent()) { // Previous block is the parent, I.E. enclosing block
                        plannedNextType = NodeType.PARALLEL_START;
                        plannedNext = plannedTreeNode.getChunk().getFirstNode();
                    } else {
                        plannedNextType = NodeType.PARALLEL_BRANCH_END;
                        plannedNext = plannedTreeNode.getChunk().getLastNode();
                    }
                }
                break;
            case PARALLEL_START:
                // We're jumping up a level
                plannedTreeNode = currentTreeNode.getParent();
                plannedNextType = NodeType.NORMAL;
                break;
            case PARALLEL_END:
                // Start with the last child branch
                plannedNextType = NodeType.PARALLEL_BRANCH_END;
                plannedTreeNode = currentTreeNode.getLastChild();
                plannedNext = plannedTreeNode.getChunk().getLastNode();
                break;
            case PARALLEL_BRANCH_END:
                // Check if I point to current branch start?  Maybe?  Probably not needed, but that lets us check if new parallel
            case NORMAL:
            default:
                plannedNextType = NodeType.NORMAL; // We will need to do some inspections here, below.
                plannedTreeNode = currentTreeNode;
        }

        // We need to inspect this FlowNode more closely because we may be in an incomplete branch, or have an end/head/etc etc
        if (plannedNextType == NodeType.NORMAL) {
            plannedNext = parents.get(0);  // Must be 1 parent, because only parallel ends have > 1, and 0 is covered by precheck
            plannedNextType = getNodeType(plannedNext);
            switch (plannedNextType) {
                case PARALLEL_END:
                    BlockStartNode parallelStart = ((BlockEndNode)plannedNext).getStartNode();
                    ChunkTreeNode parallelBlock = null;
                    if (currentTreeNode == null || currentTreeNode.findEnclosingNode(parallelStart) == null) {
                        // FIXME this is wrong.
                        // Found an untracked parallel block
                        parallelBlock = new ChunkTreeNode((BlockEndNode)plannedNext);
                    }
                    ChunkTreeNode parallelChunk = new ChunkTreeNode((BlockEndNode)plannedNext);
                    currentTreeNode.appendChild(parallelChunk); // Effectively splits current chunk?  May need to actually split it?
                    plannedTreeNode = parallelChunk;
                    break;
                case PARALLEL_BRANCH_END:
                    // Check if we already have a parallel set up for this branch, and if not, create one!
                    BlockStartNode start = ((BlockEndNode)plannedNext).getStartNode();
                    if (currentTreeNode == null || currentTreeNode.findEnclosingNode(start) == null) { // New branch-end for a parallel we haven't seen yet!
                        FlowNode myParallelStart = start.getParents().get(0);
                        ChunkTreeNode  branchNode = new ChunkTreeNode(new MemoryFlowChunk(null, start, plannedNext, null));
                        MemoryFlowChunk parallelChunkTemp = new MemoryFlowChunk(null, myParallelStart, null, null);
                        ChunkTreeNode parallelNode = new ChunkTreeNode(parallelChunkTemp, currentTreeNode, Arrays.asList(branchNode));
                        if (currentTreeNode != null) {
                            currentTreeNode.prependChild(parallelNode);
                        }

                        plannedTreeNode = branchNode;
                    }
                    break;
                case PARALLEL_BRANCH_START:
                    if (currentTreeNode == null || !current.equals(currentTreeNode.getChunk().getFirstNode()) ) { // Check if we already have a parallel set up
                        // Create ourselves a tidy parallel and insert this branch, so we have the structure in place
                        ChunkTreeNode newParallel = new ChunkTreeNode(new MemoryFlowChunk(null, plannedNext.getParents().get(0), null, null));
                        ChunkTreeNode newBranch = new ChunkTreeNode(new MemoryFlowChunk(null, plannedNext, null, null), newParallel, null);
                        if (currentTreeNode != null) {
                            currentTreeNode.appendChild(newParallel);
                        }
                    }
                    break;
                case PARALLEL_START:
                    // No worries here, mate
                    break;
                default:
                    break;
            }
        }

        if (blackList.contains(plannedNext)) {
            // TODO figure out a straightforward way to track and route around blacklisted branches... including nested parallels
            // YAGNI for now because blacklisting hasn't seen wide use and use with parallel branches may not be needed
            // Note that the next legal branch may be:
            //   1. A preceding sibling branch, whose endNode is not blacklisted (any of them)
            //   2. The BlockStartNode of the enclosing parallel... IFF at least one of the siblings is not blacklisted
            //      (Which would require us to track for each node when branches are blocked for iteration by blacklisting)
            //   3. The same for *any* enclosing parallel, recursively until we hit the root, yuck.
            return null;
        }
        nextType = plannedNextType;
        currentChunkNode = plannedTreeNode;
        return plannedNext;
    }


    @Override
    protected FlowNode next(@Nonnull FlowNode current, @Nonnull Collection<FlowNode> blackList) {
        return nextTree(current, blackList);
    }

    public static void visitSimpleChunks(@Nonnull Collection<FlowNode> heads, @Nonnull Collection<FlowNode> blacklist, @Nonnull SimpleChunkVisitor visitor, @Nonnull ChunkFinder finder) {
        ForkScanner scanner = new ForkScanner();
        scanner.setup(heads, blacklist);
        if (scanner.getParallelDepth() > 0) {
//            visitor.parallelEnd(scanner.getCurrentParallelStartNode(), scanner.myCurrent, scanner);
        }
        scanner.visitSimpleChunks(visitor, finder);
    }

    public static void visitSimpleChunks(@Nonnull Collection<FlowNode> heads, @Nonnull SimpleChunkVisitor visitor, @Nonnull ChunkFinder finder) {
        ForkScanner scanner = new ForkScanner();
        scanner.setup(heads);
        if (scanner.getParallelDepth() > 0) {
//            visitor.parallelEnd(scanner.getCurrentParallelStartNode(), scanner.myCurrent, scanner);
        }
        scanner.visitSimpleChunks(visitor, finder);
    }

    /** Ensures we find the last *begun* node when there are multiple heads (parallel branches)
     *  This means that the simpleBlockVisitor gets the *actual* last node, not just the end of the last declared branch
     *  ()
     */
    @CheckForNull
    private static FlowNode findLastStartedNode(@Nonnull List<FlowNode> candidates) {
        if (candidates.size() == 0) {
            return null;
        } else if (candidates.size() == 1) {
            return candidates.get(0);
        } else {
            FlowNode returnOut = candidates.get(0);
            long startTime = Long.MIN_VALUE;
            for(FlowNode f : candidates) {
                TimingAction ta = f.getAction(TimingAction.class);
                // Null timing with multiple heads is probably a node where the GraphListener hasn't fired to add TimingAction yet
                long myStart = (ta == null) ? System.currentTimeMillis() : ta.getStartTime();
                if (myStart > startTime) {
                    returnOut = f;
                    startTime = myStart;
                }
            }
            return returnOut;
        }
    }

    static class ChunkFinalNodeComparator implements Comparator<ChunkTreeNode> {

        @Override
        public int compare(ChunkTreeNode o1, ChunkTreeNode o2) {
            if (o1 == null || o2 == null || o1.getChunk() == null || o2.chunk == null) {
                return 0;
            } else {
                return FlowScanningUtils.TIME_ORDER_COMPARATOR.compare(o1.getChunk().getLastNode(), o2.getChunk().getLastNode());
            }
        }
    }

    static final Comparator<ChunkTreeNode> FINAL_NODE_TIME_COMPARATOR = new ChunkFinalNodeComparator();

    /** Trivial sorting of the current in-progress branches (See issue JENKINS-38536)... does not handle nesting correctly though */
    void sortChildrenByTime(ChunkTreeNode node) {
        if (node.hasChildren()) {
            List<ChunkTreeNode> children = new ArrayList<>(node.getChildren());
            Collections.sort(children, FINAL_NODE_TIME_COMPARATOR);
            node.setChildren(children);
        }
    }

    /** Walk through flows */
    public void visitSimpleChunks(@Nonnull SimpleChunkVisitor visitor, @Nonnull ChunkFinder finder) {
        FlowNode prev = null;
        // We can't just  fire the extra chunkEnd event
        // We need to look at the parallels structure, and for each parallel re-sort the nodes by their timing info...
        // IFF they are open when beginning (if complete, it is irrelevant)
//        sortParallelByTime();
        boolean needsEnd = false;
        if (finder.isStartInsideChunk()) {
            needsEnd = true;
        }
        // FIXME: fire a parallelEnd if nextType is a parallelBranchEnd, using the best end from the set of open parallel tips
        while(hasNext()) {
            prev = (myCurrent != myNext) ? myCurrent : null;
            FlowNode f = next();

            boolean boundary = false;
            if (finder.isChunkStart(myCurrent, prev)) {
                visitor.chunkStart(myCurrent, myNext, this);
                boundary = true;
            }
            if (needsEnd || finder.isChunkEnd(myCurrent, prev)) {
                visitor.chunkEnd(myCurrent, prev, this);
                boundary = true;
                needsEnd = false;
            }
            if (!boundary) {
                visitor.atomNode(myNext, f, prev, this);
            }

            // Trigger on parallels
            switch (currentType) {
                case NORMAL:
                    break;
                case PARALLEL_END:
                    visitor.parallelEnd(this.getCurrentParallelStartNode(), myCurrent, this);
                    break;
                case PARALLEL_START:
                    visitor.parallelStart(myCurrent, prev, this);
                    break;
                case PARALLEL_BRANCH_END:
                    visitor.parallelBranchEnd(this.getCurrentParallelStartNode(), myCurrent, this);
                    break;
                case PARALLEL_BRANCH_START:
                    // Needed because once we hit the start of the last branch, the next node is our currentParallelStart
                    FlowNode parallelStart = (nextType == NodeType.PARALLEL_START) ? myNext : this.getCurrentParallelStartNode();
                    visitor.parallelBranchStart(parallelStart, myCurrent, this);
                    break;
                default:
                    throw new IllegalStateException("Unhandled type for current node");
            }

            /*
            // We need to cover single-branch case by looking for branch & parallel start/end if it's a block
            NodeType tempType = currentType;

            if (currentType == NodeType.NORMAL) { // Normal node but we have to check if it's a single-branch parallel
                NodeType myType = getNodeType(myCurrent);
                if (myType == NodeType.NORMAL) {
                    continue;
                } else {
                    tempType = myType;
                }
            }
            switch (tempType) {  // "Fixed"
                case NORMAL:
                    break;
                    // Below is covering cases where you're an unterminated end
//                    if (currentParallelStart != null) {
//                        visitor.parallelBranchEnd(((BlockEndNode)myCurrent).getStartNode().getParents().get(0), myCurrent, this);
//                    }
                    break;
                case PARALLEL_END:
                    visitor.parallelEnd(((BlockEndNode)myCurrent).getStartNode(), myCurrent, this);
                    break;
                case PARALLEL_START:
                    visitor.parallelStart(myCurrent, prev, this);
                    break;
                case PARALLEL_BRANCH_END:
                    visitor.parallelBranchEnd(((BlockEndNode)myCurrent).getStartNode().getParents().get(0), myCurrent, this);
                    break;
                case PARALLEL_BRANCH_START:
                    // Needed because once we hit the start of the last branch, the next node is our currentParallelStart
                    FlowNode parallelStart = (nextType == NodeType.PARALLEL_START) ? myNext : this.currentParallelStartNode;
                    if (parallelStart == null) {
                        parallelStart = myCurrent.getParents().get(0);
                    }
                    visitor.parallelBranchStart(parallelStart, myCurrent, this);
                    break;
                default:
                    throw new IllegalStateException("Unhandled type for current node");
            }*/
        }
    }

}

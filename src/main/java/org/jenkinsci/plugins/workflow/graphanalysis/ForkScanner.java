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
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.jenkinsci.plugins.workflow.actions.ThreadNameAction;
import org.jenkinsci.plugins.workflow.actions.TimingAction;
import org.jenkinsci.plugins.workflow.graph.BlockEndNode;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowEndNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

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

    /** Used to recognize special nodes
     *  TODO Rethink this approach, since a single node may fit into more than one if it is part of an incomplete parallel.
     *   Ex: you may have a {@link BlockStartNode} normally representing the beginning of a branch... which is also the END of a branch
     *   because it represents the last node created in an in-progress parallel block.
     */
    enum NodeType {
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

    // Last element in stack is end of myCurrent parallel start, first is myCurrent start
    ArrayDeque<ParallelBlockStart> parallelBlockStartStack = new ArrayDeque<ParallelBlockStart>();

    /** List of nodes identified as heads, for purposes of marking parallel branch ends */
    HashSet<String> headIds = new HashSet<String>();

    /** FlowNode that will terminate the myCurrent parallel block */
    FlowNode currentParallelStartNode = null;

    ParallelBlockStart currentParallelStart = null;

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
        parallelBlockStartStack.clear();
        currentParallelStart = null;
        currentParallelStartNode = null;
        myCurrent = null;
        myNext = null;
        this.headIds.clear();
    }

    /** Test if a {@link FlowNode} is the start of a parallel block (and not also not just a branch start) */
    static class IsParallelStartPredicate implements Predicate<FlowNode> {
        static final NodeStepNamePredicate PARALLEL_STEP = new NodeStepNamePredicate("org.jenkinsci.plugins.workflow.cps.steps.ParallelStep");

        @Override
        public boolean apply(@Nullable FlowNode input) {
            return (input instanceof BlockStartNode && PARALLEL_STEP.apply(input) && input.getPersistentAction(ThreadNameAction.class) == null);
        }
    }

    /** Originally a workaround to deal with needing the {@link StepDescriptor} to determine if a node is a parallel start
     *  Now tidily solved by {@link IsParallelStartPredicate}*/
    private static final Predicate<FlowNode> parallelStartPredicate = new IsParallelStartPredicate();

    /** Now a complete no-op -- originally this was a workaround for dependency issues with workflow-cps.
     *  Specifically, requiring classes from workflow-cps to detect if something is a parallel step.
     */
    @Deprecated
    public static void setParallelStartPredicate(@Nonnull Predicate<FlowNode> pred) {
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
    static NodeType getNodeType(@CheckForNull FlowNode f) {
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
            if (type == null) {
                return null;
            }
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

    /** Tracks state for parallel blocks, so we can ensure all are visited and know the branch starting point */
    static class ParallelBlockStart {
        BlockStartNode forkStart; // This is the node with child branches
        ArrayDeque<FlowNode> unvisited = new ArrayDeque<FlowNode>();  // Remaining branches of this that we have have not visited yet

        ParallelBlockStart(@Nonnull BlockStartNode forkStart) {
            this.forkStart = forkStart;
        }

        /** Strictly for internal use in the least common ancestor problem */
        ParallelBlockStart() {}
    }

    interface FlowPiece {  // Mostly a marker
        /** If true, this is not a fork and has no following forks */
        boolean isLeaf();
    }

    /** Linear (no parallels) run of FLowNodes */
    // TODO see if this can be replaced with a FlowChunk acting as a container class for a list of FlowNodes
    static class FlowSegment implements FlowPiece {
        ArrayList<FlowNode> visited = new ArrayList<FlowNode>();
        FlowPiece after;
        boolean isLeaf = true;

        @Override
        public boolean isLeaf() {
            return isLeaf;
        }

        /**
         * We have discovered a forking node intersecting our FlowSegment in the middle or meeting at the end
         * Now we need to split the flow, or pull out the fork point and make both branches follow it
         * @param nodeMapping Mapping of BlockStartNodes to flowpieces (forks or segments)
         * @param joinPoint Node where the branches intersect/meet (fork point)
         * @param joiningBranch Flow piece that is joining this
         * @throws IllegalStateException When you try to split a segment on a node that it doesn't contain, or invalid graph structure
         * @return Recreated fork
         */
        Fork split(@Nonnull HashMap<FlowNode, FlowPiece> nodeMapping, @Nonnull BlockStartNode joinPoint, @Nonnull FlowPiece joiningBranch) {
            int index = visited.lastIndexOf(joinPoint);  // Fork will be closer to end, so this is better than indexOf
            Fork newFork = new Fork(joinPoint);

            if (index < 0) {
                throw new IllegalStateException("Tried to split a segment where the node doesn't exist in this segment");
            } else if (index == this.visited.size()-1) { // We forked just off the most recent node
                newFork.following.add(this);
                newFork.following.add(joiningBranch);
                this.visited.remove(index);
            } else if (index == 0) {
                throw new IllegalStateException("We have a cyclic graph or heads that are not separate branches!");
            } else { // Splitting at some midpoint within the segment, everything before becomes part of the following
                // Execute the split: create a new fork at the fork point, and shuffle the part of the flow after it
                //   to a new segment and add that to the fork.

                FlowSegment newSegment = new FlowSegment();
                newSegment.after = this.after;
                newSegment.visited.addAll(this.visited.subList(0, index));
                newFork.following.add(newSegment);
                newFork.following.add(joiningBranch);
                this.after = newFork;
                this.isLeaf = false;

                // Remove the part before the fork point
                this.visited.subList(0, index+1).clear();
                for (FlowNode n : newSegment.visited) {
                    nodeMapping.put(n, newSegment);
                }
            }
            nodeMapping.put(joinPoint, newFork);
            return newFork;
        }

        public void add(FlowNode f) {
            this.visited.add(f);
        }
    }

    /** Internal class used for constructing the LeastCommonAncestor structure */
    // TODO see if this can be replaced with a FlowChunk acting as a container class for parallels
    // I.E. ParallelMemoryFlowChunk or similar
    static class Fork extends ParallelBlockStart implements FlowPiece {
        List<FlowPiece> following = new ArrayList<FlowPiece>();

        @Override
        public boolean isLeaf() {
            return false;
        }

        public Fork(BlockStartNode forkNode) {
            this.forkStart = forkNode;
        }
    }

    /** Does a conversion of the fork container class to a set of block starts */
    ArrayDeque<ParallelBlockStart> convertForksToBlockStarts(ArrayDeque<Fork> parallelForks) {
        // Walk through and convert forks to parallel block starts, and find heads that point to them
        ArrayDeque<ParallelBlockStart> output = new ArrayDeque<ParallelBlockStart>();
        for (Fork f : parallelForks) {
            // Do processing to assign heads to flowsegments
            ParallelBlockStart start = new ParallelBlockStart();
            start.forkStart = f.forkStart;
            start.unvisited = new ArrayDeque<FlowNode>();

            // Add the nodes to the parallel starts here
            for (FlowPiece fp : f.following) {
                if (fp.isLeaf()) { // Forks are never leaves
                    start.unvisited.add(((FlowSegment)fp).visited.get(0));
                }
            }
            output.add(start);
        }
        return output;
    }

    /**
     * Create the necessary information about parallel blocks in order to provide flowscanning from inside incomplete parallel branches
     * This works by walking back to construct the tree of parallel blocks covering all heads back to the Least Common Ancestor of all heads
     *  (the top parallel block).  One by one, as branches join, we remove them from the list of live pieces and replace with their common ancestor.
     *
     * <p> The core algorithm is simple in theory but the many cases render the implementation quite complex. In gist:
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
     * @param heads
     */
    ArrayDeque<ParallelBlockStart> leastCommonAncestor(@Nonnull final Set<FlowNode> heads) {
        HashMap<FlowNode, FlowPiece> branches = new HashMap<FlowNode, FlowPiece>();
        ArrayList<Filterator<FlowNode>> iterators = new ArrayList<Filterator<FlowNode>>();
        ArrayList<FlowPiece> livePieces = new ArrayList<FlowPiece>();

        ArrayDeque<Fork> parallelForks = new ArrayDeque<Fork>();  // Tracks the discovered forks in order of encounter

        Predicate<FlowNode> notAHead = new Predicate<FlowNode>() {  // Filter out pre-existing heads
            Collection<FlowNode> checkHeads = convertToFastCheckable(heads);

            @Override
            public boolean apply(FlowNode input) { return !(checkHeads.contains(input)); }
        };

        for (FlowNode f : heads) {
            iterators.add(FlowScanningUtils.fetchEnclosingBlocks(f).filter(notAHead));  // We can do this because Parallels always meet at a BlockStartNode
            FlowSegment b = new FlowSegment();
            b.add(f);
            livePieces.add(b);
            branches.put(f, b);
        }

        // Walk through, merging flownodes one-by-one until everything has merged to one ancestor
        boolean mergedAll = false;
        // Ends when we merged all branches together, or hit the start of the flow without it
        while (!mergedAll && iterators.size() > 0) {
            ListIterator<Filterator<FlowNode>> itIterator = iterators.listIterator();
            ListIterator<FlowPiece> pieceIterator = livePieces.listIterator();

            while (itIterator.hasNext()) {
                Filterator<FlowNode> blockStartIterator = itIterator.next();
                FlowPiece myPiece = pieceIterator.next(); //Safe because we always remove/add with both iterators at once

                // Welp we hit the end of a branch
                if (!blockStartIterator.hasNext()) {
                    pieceIterator.remove();
                    itIterator.remove();
                    continue;
                }

                FlowNode nextBlockStart = blockStartIterator.next();

                // Look for cases where two branches merge together
                FlowPiece existingPiece = branches.get(nextBlockStart);
                if (existingPiece == null && myPiece instanceof FlowSegment) { // No merge, just add to segment
                    ((FlowSegment) myPiece).add(nextBlockStart);
                    branches.put(nextBlockStart, myPiece);
                } else if (existingPiece == null && myPiece instanceof Fork) {  // No merge, we had a fork. Start a segment preceding the fork
                    FlowSegment newSegment = new FlowSegment();
                    newSegment.isLeaf = false;
                    newSegment.add(nextBlockStart);
                    newSegment.after = myPiece;
                    pieceIterator.remove();
                    pieceIterator.add(newSegment);
                    branches.put(nextBlockStart, newSegment);
                } else if (existingPiece != null) {  // Always not null. We're merging into another thing, we're going to eliminate a branch
                    if (existingPiece instanceof Fork) {
                        ((Fork) existingPiece).following.add(myPiece);
                    } else { // Split a flow segment so it forks against this one
                        Fork f = ((FlowSegment) existingPiece).split(branches, (BlockStartNode)nextBlockStart, myPiece);
                        // If we split the existing segment at its end, we created a fork replacing its latest node
                        // Thus we must replace the piece with the fork ahead of it
                        if (f.following.contains(existingPiece) ) {
                            int headIndex = livePieces.indexOf(existingPiece);
                            livePieces.set(headIndex, f);
                        }
                        parallelForks.add(f);
                    }

                    // Merging removes the piece & its iterator from heads
                    itIterator.remove();
                    pieceIterator.remove();
                    if (iterators.size() == 1) { // Merged in the final branch
                        mergedAll = true;
                    }
                }
            }
        }

        // If we hit issues with the ordering of blocks by depth, apply a sorting to the parallels by depth
        return convertForksToBlockStarts(parallelForks);
    }

    @Override
    protected void setHeads(@Nonnull Collection<FlowNode> heads) {
        if (heads.size() > 1) {
            for (FlowNode f : heads) {
                headIds.add(f.getId());
            }
            parallelBlockStartStack = leastCommonAncestor(new LinkedHashSet<FlowNode>(heads));
            assert parallelBlockStartStack.size() > 0;
            currentParallelStart = parallelBlockStartStack.pop();
            currentParallelStartNode = currentParallelStart.forkStart;
            myCurrent = currentParallelStart.unvisited.pop();
            myNext = myCurrent;

            // We may have a start type, so we need to override the beginning type
            NodeType tempType = getNodeType(myCurrent);
            if (tempType == NodeType.NORMAL) {
                nextType = NodeType.PARALLEL_BRANCH_END;
                currentType = NodeType.PARALLEL_BRANCH_END;
            } else {
                nextType = tempType;
            }
            walkingFromFinish = false;
        } else {
            FlowNode f = heads.iterator().next();
            walkingFromFinish = f instanceof FlowEndNode;
            myCurrent = f;
            myNext = f;
            if (isParallelEnd(f)) {
                nextType = NodeType.PARALLEL_END;
            } else if (isParallelStart(f)) {
                nextType = NodeType.PARALLEL_START;
            } else {
                nextType = NodeType.NORMAL;
            }
        }
        currentType = null;
    }

    /**
     * Return the node that begins the current parallel head, if we are known to be in a parallel block
     * @return The FlowNode that marks current parallel start
     */
    @CheckForNull
    public FlowNode getCurrentParallelStartNode() {
        return currentParallelStartNode;
    }


    /** Return number of levels deep we are in parallel blocks */
    public int getParallelDepth() {
        return (currentParallelStart == null) ? 0 : 1 + parallelBlockStartStack.size();
    }

    /**
     * Invoked when we start entering a parallel block (walking from head of the flow, so we see the block end first)
     * @param endNode Node where parents merge (final end node for the parallel block)
     * @param parents Parent nodes that end here
     * @return FlowNode myNext node to visit
     */
    FlowNode hitParallelEnd(BlockEndNode endNode, List<FlowNode> parents, Collection<FlowNode> blackList) {
        BlockStartNode start = endNode.getStartNode();

        ArrayDeque<FlowNode> branches = new ArrayDeque<FlowNode>();
        for (FlowNode f : parents) {
            if (!blackList.contains(f)) {
                branches.addFirst(f);
            }
        }

        FlowNode output = null;
        if (branches.size() > 0) { // Push another branch start
            ParallelBlockStart parallelBlockStart = new ParallelBlockStart(start);
            output = branches.pop();
            parallelBlockStart.unvisited = branches;

            if (currentParallelStart != null) {
                parallelBlockStartStack.push(currentParallelStart);
            }
            currentParallelStart = parallelBlockStart;
            currentParallelStartNode = start;
        }
        return output;
    }

    /**
     * Invoked when we complete parallel block, walking from the head (so encountered after the end)
     * @return FlowNode if we're the last node
     */
    FlowNode hitParallelStart() {
        FlowNode output = null;

        if (currentParallelStart != null) {
            if (currentParallelStart.unvisited.isEmpty()) {  // Strip off a completed branch
                // We finished a nested set of parallel branches, visit the head and move up a level
                output = currentParallelStartNode;

                if (parallelBlockStartStack.size() > 0) {
                    // Finished a nested parallel block, move up a level
                    currentParallelStart = parallelBlockStartStack.pop();
                    currentParallelStartNode = currentParallelStart.forkStart;
                } else { // At the top level, not inside any parallel block
                    currentParallelStart = null;
                    currentParallelStartNode = null;
                }
            }
        } else {
            if (isWalkingFromFinish()) {  //Incomplete single-branch parallels can do this
                throw new IllegalStateException("Hit a BlockStartNode with no record of the start!");
            }
            return myCurrent.getParents().get(0); // No branches to explore
        }

        // Handle cases where the BlockStartNode for the parallel block is blackListed
        return (output != null && !myBlackList.contains(output)) ? output : null;
    }

    @Override
    public FlowNode next() {
        currentType = nextType;
        FlowNode output = super.next();
        return output;
    }

    @Override
    @SuppressFBWarnings(value = "DLS_DEAD_LOCAL_STORE",
            justification = "Function call to modify state, special case where we don't need the returnVal")
    protected FlowNode next(@Nonnull FlowNode current, @Nonnull Collection<FlowNode> blackList) {
        FlowNode output = null;

        // First we look at the parents of the current node if present
        List<FlowNode> parents = current.getParents();
        if (parents.isEmpty()) {
            // welp, we're done with this node, guess we consult the queue?
        } else if (parents.size() == 1) {
            FlowNode p = parents.get(0);
            if (p == currentParallelStartNode || isParallelStart(p)) {
                // Terminating a parallel scan
                FlowNode temp = hitParallelStart();
                if (temp != null) { // Start node for current parallel block now that it is done
                    nextType = NodeType.PARALLEL_START;
                    return temp;
                }
            } else {
                if (isParallelEnd(current)) {
                    BlockEndNode end = ((BlockEndNode) current);
                    FlowNode possibleOutput = hitParallelEnd(end, parents, blackList);  // possibleOutput can only be "p"
                }
                nextType = getNodeType(p);
                if (!blackList.contains(p)) {
                    return p;
                }
            }
        } else if (isParallelEnd(current)) {
            // We must be a BlockEndNode that begins this
            BlockEndNode end = ((BlockEndNode) current);
            FlowNode possibleOutput = hitParallelEnd(end, parents, blackList); // What if output is block but other branches aren't?
            if (possibleOutput != null) {
                nextType = NodeType.PARALLEL_BRANCH_END;
                return possibleOutput;
            }
        } else {
            throw new IllegalStateException("Found a FlowNode with multiple parents that isn't the end of a block! "+ this.myCurrent);
        }

        if (currentParallelStart != null && currentParallelStart.unvisited.size() > 0) {
            output = currentParallelStart.unvisited.pop();
            nextType = NodeType.PARALLEL_BRANCH_END;
            // Below is because your two branches *might* be just the branch start nodes, and should be treated as such
            // Even if they're ends as well.
            if (output instanceof BlockStartNode && output.getPersistentAction(ThreadNameAction.class) != null) {
                nextType = NodeType.PARALLEL_BRANCH_START;
            }
        }
        if (output == null) {
            nextType = null;
        }
        return output;
    }

    public static void visitSimpleChunks(@Nonnull Collection<FlowNode> heads, @Nonnull Collection<FlowNode> blacklist, @Nonnull SimpleChunkVisitor visitor, @Nonnull ChunkFinder finder) {
        ForkScanner scanner = new ForkScanner();
        scanner.setup(heads, blacklist);
        scanner.visitSimpleChunks(visitor, finder);
    }

    public static void visitSimpleChunks(@Nonnull Collection<FlowNode> heads, @Nonnull SimpleChunkVisitor visitor, @Nonnull ChunkFinder finder) {
        ForkScanner scanner = new ForkScanner();
        scanner.setup(heads);
        scanner.visitSimpleChunks(visitor, finder);
    }

    /**
     * Allows you to find the last begun node when there are multiple heads (parallel branches) running.
     * This is useful for computing timing/status of incomplete parallel blocks, and is also used in
     *  {@link SimpleChunkVisitor#parallelEnd(FlowNode, FlowNode, ForkScanner)}, so we get the REAL end of the block -
     *    not just the last declared branch. (See issue JENKINS-38536)
     */
    @CheckForNull
    static FlowNode findLastRunningNode(@Nonnull List<FlowNode> candidates) {
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

    /** Find the current head nodes but only for the current parallel */
    List<FlowNode> currentParallelHeads() {
        ArrayList<FlowNode> ends = new ArrayList<FlowNode>();
        if (this.currentParallelStart != null) {
            ends.addAll(this.currentParallelStart.unvisited);
        }
        if (this.myCurrent != null) {
            ends.add(this.myCurrent);
        }
        return ends;
    }

    /** Pulls out firing the callbacks for parallels */
    static void fireVisitParallelCallbacks(@CheckForNull FlowNode next, @CheckForNull FlowNode current, @CheckForNull FlowNode prev,
                                           @Nonnull SimpleChunkVisitor visitor, @Nonnull ChunkFinder finder, @Nonnull ForkScanner scanner) {
        // Trigger on parallels
        switch (scanner.currentType) {
            case NORMAL:
                break;
            case PARALLEL_END:
                FlowNode n = scanner.getCurrentParallelStartNode();
                if (n != null) {
                    visitor.parallelEnd(n, current, scanner);
                } else if (current instanceof BlockEndNode){
                    visitor.parallelEnd(((BlockEndNode) current).getStartNode(), current, scanner);
                }
                break;
            case PARALLEL_START:
                visitor.parallelStart(current, prev, scanner);
                break;
            case PARALLEL_BRANCH_END:
                FlowNode f = scanner.getCurrentParallelStartNode();
                if (f != null) {
                    visitor.parallelBranchEnd(f, current, scanner);
                } else if (current instanceof BlockEndNode) {
                    // Branch end for single-branch parallel, fire zee events!
                    visitor.parallelBranchEnd(((BlockEndNode)current).getStartNode().getParents().get(0), current, scanner);
                } // Anything else can only be the work of a bug
                break;
            case PARALLEL_BRANCH_START:
                // Needed because once we hit the start of the last branch, the next node is our currentParallelStart
                FlowNode parallelStart = (scanner.nextType == NodeType.PARALLEL_START) ? next : scanner.getCurrentParallelStartNode();
                if (scanner.headIds.contains(current.getId())) {
                    // Covers an obscure case where the heads are also BlockStartNodes for a branch
                    visitor.parallelBranchEnd(parallelStart, current, scanner);
                }
                if (parallelStart != null) {
                    visitor.parallelBranchStart(parallelStart, current, scanner);
                } else {
                    // Assume we're the start of a single-branch parallel, which ALWAYS has a parent
                    visitor.parallelBranchStart(current.getParents().get(0), current, scanner);
                }
                break;
            default:
                throw new IllegalStateException("Unhandled type for current node");
        }
    }

    /** Abstracts out the simpleChunkVisitor callback-triggering logic.
     *  Note that a null value of "prev" is assumed to mean we're the last node. */
    @SuppressFBWarnings(value="NP_LOAD_OF_KNOWN_NULL_VALUE", justification = "FindBugs doesn't like passing nulls to a method that can take null")
    static void fireVisitChunkCallbacks(@CheckForNull FlowNode next, @Nonnull FlowNode current, @CheckForNull FlowNode prev,
                                        @Nonnull SimpleChunkVisitor visitor, @Nonnull ChunkFinder finder, @Nonnull ForkScanner scanner) {
        boolean boundary = false;
        if (prev == null && finder.isStartInsideChunk()) { // Last node, need to fire end event to start inside chunk
            visitor.chunkEnd(current, prev, scanner);
            boundary = true;
            if (finder.isChunkStart(current, prev)) {
                visitor.chunkStart(current, next, scanner);
            }
        } else { // Not starting inside chunk, OR not at end
            if (finder.isChunkStart(current, prev)) {
                visitor.chunkStart(current, next, scanner);
                boundary = true;
            }
            if (finder.isChunkEnd(current, prev)) { // Null for previous means we're the last node.
                visitor.chunkEnd(current, prev, scanner);
                boundary = true;
            }
        }
        if (!boundary) {
            visitor.atomNode(next, current, prev, scanner);
        }
    }

    /** Walk through flows */
    public void visitSimpleChunks(@Nonnull SimpleChunkVisitor visitor, @Nonnull ChunkFinder finder) {
        FlowNode prev;

        if (this.currentParallelStart != null) {
            FlowNode last = findLastRunningNode(currentParallelHeads());
            if (last != null) {
                visitor.parallelEnd(this.currentParallelStartNode, last, this);
            }
        }

        while(hasNext()) {
            prev = (myCurrent != myNext) ? myCurrent : null;
            FlowNode f = next();
            fireVisitChunkCallbacks(myNext, myCurrent, prev, visitor, finder, this);
            fireVisitParallelCallbacks(myNext, myCurrent, prev, visitor, finder, this);
        }
    }

}

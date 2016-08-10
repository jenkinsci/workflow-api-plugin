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
import com.google.common.base.Predicates;
import org.jenkinsci.plugins.workflow.actions.ThreadNameAction;
import org.jenkinsci.plugins.workflow.graph.BlockEndNode;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowEndNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

/**
 * Scanner that will scan down all forks when we hit parallel blocks before continuing, but generally runs in linear order
 * <p/>Think of it as the opposite of {@link DepthFirstScanner}.
 *
 * <p/>This is a fairly efficient way to visit all FlowNodes, and provides three useful guarantees:
 * <ul>
 *   <li>Every FlowNode is visited, and visited EXACTLY ONCE (not true for LinearScanner)</li>
 *   <li>All parallel branches are visited before we move past the parallel block (not true for DepthFirstScanner)</li>
 *   <li>For EVERY block, the BlockEndNode is visited before the BlockStartNode (not true for DepthFirstScanner, with parallels)</li>
 * </ul>
 *
 * <p/>The big advantages of this approach:
 * <ul>
 *     <li>Blocks are visited in the order they end (no backtracking) - helps with working a block at a time</li>
 *     <li>Points are visited in linear order within a block (easy to use for analysis)</li>
 *     <li>Minimal state information needed</li>
 *     <li>Branch information is available for use here</li>
 * </ul>
 *
 * @author <samvanoort@gmail.com>Sam Van Oort</samvanoort@gmail.com>
 */
public class ForkScanner extends AbstractFlowScanner {

    public NodeType getCurrentType() {
        return currentType;
    }

    public NodeType getNextType() {
        return nextType;
    }

    /** Used to recognize special nodes */
    public enum NodeType {
        NORMAL,
        PARALLEL_START,
        PARALLEL_END,
        PARALLEL_BRANCH_START,
        PARALLEL_BRANCH_END,
    }

    // Last element in stack is end of myCurrent parallel start, first is myCurrent start
    ArrayDeque<ParallelBlockStart> parallelBlockStartStack = new ArrayDeque<ParallelBlockStart>();

    /** FlowNode that will terminate the myCurrent parallel block */
    FlowNode currentParallelStartNode = null;

    ParallelBlockStart currentParallelStart = null;

    private boolean walkingFromFinish = false;

    protected NodeType currentType;
    protected NodeType nextType;

    @Override
    protected void reset() {
        parallelBlockStartStack.clear();
        currentParallelStart = null;
        currentParallelStartNode = null;
        myCurrent = null;
        myNext = null;
    }

    // A bit of a dirty hack, but it works around the fact that we need trivial access to classes from workflow-cps
    // For this and only this test. So, we load them from a context that is aware of them.
    // Ex: workflow-cps can automatically set this correctly. Not perfectly graceful but it works.
    private static Predicate<FlowNode> parallelStartPredicate = Predicates.alwaysFalse();

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
        return f != null && f instanceof BlockEndNode && isParallelStart(((BlockEndNode) f).getStartNode());
    }

    /** If true, we are walking from the flow end node and have a complete view of the flow */
    public boolean isWalkingFromFinish() {
        return walkingFromFinish;
    }

    /** Tracks state for parallel blocks, so we can ensure all are visited and know the branch starting point */
    protected static class ParallelBlockStart {
        protected BlockStartNode forkStart; // This is the node with child branches
        protected int remainingBranches;
        protected int totalBranches;
        protected ArrayDeque<FlowNode> unvisited = new ArrayDeque<FlowNode>();  // Remaining branches of this that we have have not visited yet

        protected ParallelBlockStart(BlockStartNode forkStart, int branchCount) {
            this.forkStart = forkStart;
            this.remainingBranches = branchCount;
        }

        /** Strictly for internal use in the least common ancestor problem */
        ParallelBlockStart() {}
    }

    interface FlowPiece {  // Mostly a marker
        /** If true, this is not a fork and has no following forks */
        boolean isLeaf();
    }

    /** Linear (no parallels) run of FLowNodes */
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
            start.totalBranches = f.following.size();
            start.forkStart = f.forkStart;
            start.remainingBranches = start.totalBranches;
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
     * <p/> The core algorithm is simple in theory but the many cases render the implementation quite complex. In gist:
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
     * <p/>  There are some assumptions you need to know about to understand why this works:
     * <ul>
     *     <li>None of the pieces have multiple parents, since we only look at enclosing blocks (only be a BlockEndNodes for a parallel block have multipel parents)</li>
     *     <li>No cycles exist in the graph</li>
     *     <li>Flow graphs are correctly constructed</li>
     *     <li>Heads are all separate branches</li>
     * </ul>
     *
     * @param heads
     */
    ArrayDeque<ParallelBlockStart> leastCommonAncestor(@Nonnull Set<FlowNode> heads) {
        HashMap<FlowNode, FlowPiece> branches = new HashMap<FlowNode, FlowPiece>();
        ArrayList<Filterator<FlowNode>> iterators = new ArrayList<Filterator<FlowNode>>();
        ArrayList<FlowPiece> livePieces = new ArrayList<FlowPiece>();

        ArrayDeque<Fork> parallelForks = new ArrayDeque<Fork>();  // Tracks the discovered forks in order of encounter

        for (FlowNode f : heads) {
            iterators.add(FlowScanningUtils.filterableEnclosingBlocks(f));
            FlowSegment b = new FlowSegment();
            b.add(f);
            livePieces.add(b);
            branches.put(f, b);
        }

        // Walk through, merging flownodes one-by-one until everything has merged to one ancestor
        while (iterators.size() > 1) {
            ListIterator<Filterator<FlowNode>> itIterator = iterators.listIterator();
            ListIterator<FlowPiece> pieceIterator = livePieces.listIterator();

            while (itIterator.hasNext()) {
                Filterator<FlowNode> blockStartIterator = itIterator.next();
                FlowPiece myPiece = pieceIterator.next();

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
                } else if (existingPiece != null) {  // Always not null. We're merging into another thing, we're going to elliminate a branch
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
                }
            }
        }

        // If we hit issues with the ordering of blocks by depth, apply a sorting to the parallels by depth
        return convertForksToBlockStarts(parallelForks);
    }

    @Override
    protected void setHeads(@Nonnull Collection<FlowNode> heads) {
        if (heads.size() > 1) {
            //throw new IllegalArgumentException("ForkedFlowScanner can't handle multiple head nodes yet");
            parallelBlockStartStack = leastCommonAncestor(new LinkedHashSet<FlowNode>(heads));
            currentParallelStart = parallelBlockStartStack.pop();
            currentParallelStartNode = currentParallelStart.forkStart;
            myCurrent = currentParallelStart.unvisited.pop();
            myNext = myCurrent;
            nextType = NodeType.PARALLEL_BRANCH_END;
            currentParallelStart.remainingBranches--;
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
     * Return the node that begins the current parallel head
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
    protected FlowNode hitParallelEnd(BlockEndNode endNode, List<FlowNode> parents, Collection<FlowNode> blackList) {
        BlockStartNode start = endNode.getStartNode();

        ArrayDeque<FlowNode> branches = new ArrayDeque<FlowNode>();
        for (FlowNode f : parents) {
            if (!blackList.contains(f)) {
                branches.add(f);
            }
        }

        FlowNode output = null;
        if (branches.size() > 0) { // Push another branch start
            ParallelBlockStart parallelBlockStart = new ParallelBlockStart(start, branches.size());
            output = branches.pop();
            parallelBlockStart.totalBranches = parents.size();
            parallelBlockStart.remainingBranches--;
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
    protected FlowNode hitParallelStart() {
        FlowNode output = null;

        if (currentParallelStart != null) {
            if ((currentParallelStart.remainingBranches--) <= 0) {  // Strip off a completed branch
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
            throw new IllegalStateException("Hit a BlockStartNode with multiple children, and no record of the start!");
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
    protected FlowNode next(@Nonnull FlowNode current, @Nonnull Collection<FlowNode> blackList) {
        FlowNode output = null;

        // First we look at the parents of the current node if present
        List<FlowNode> parents = current.getParents();
        if (parents == null || parents.size() == 0) {
            // welp done with this node, guess we consult the queue?
        } else if (parents.size() == 1) {
            FlowNode p = parents.get(0);
            if (p == currentParallelStartNode) {
                // Terminating a parallel scan
                FlowNode temp = hitParallelStart();
                if (temp != null) { // Start node for current parallel block now that it is done
                    nextType = NodeType.PARALLEL_START;
                    return temp;
                }
            } else if (!blackList.contains(p)) {
                if (p instanceof BlockStartNode && p.getAction(ThreadNameAction.class) != null) {
                    nextType = NodeType.PARALLEL_BRANCH_START;
                } else if (ForkScanner.isParallelEnd(p)) {
                    nextType = NodeType.PARALLEL_END;
                } else {
                    nextType = NodeType.NORMAL;
                }
                return p;
            }
        } else if (current instanceof BlockEndNode && parents.size() > 1) {
            // We must be a BlockEndNode that begins this
            BlockEndNode end = ((BlockEndNode) current);
            FlowNode possibleOutput = hitParallelEnd(end, parents, blackList); // What if output is block but other branches aren't?
            if (possibleOutput != null) {
                nextType = NodeType.PARALLEL_BRANCH_END;
                return possibleOutput;
            }
        } else {
            throw new IllegalStateException("Found a FlowNode with multiple parents that isn't the end of a block! "+ this.myCurrent.toString());
        }

        if (currentParallelStart != null && currentParallelStart.unvisited.size() > 0) {
            output = currentParallelStart.unvisited.pop();
            nextType = NodeType.PARALLEL_BRANCH_END;
            currentParallelStart.remainingBranches--;
        }
        if (output == null) {
            nextType = null;
        }
        return output;
    }

    /** Walk through flows  */
    public void visitSimpleChunks(SimpleChunkVisitor visitor, ChunkFinder finder) {
        FlowNode prev = null;
        while(hasNext()) {
            prev = myCurrent;
            FlowNode f = next();

            boolean boundary = false;
            if (finder.isChunkStart(myCurrent, prev)) {
                visitor.chunkStart(myCurrent, myNext, this);
                boundary = true;
            }
            if (finder.isChunkEnd(myCurrent, prev)) {
                visitor.chunkEnd(myCurrent, prev, this);
                boundary = true;
            }
            if (!boundary) {
                visitor.atomNode(prev, f, myNext, this);
            }

            // Trigger on parallels
            switch (currentType) {
                case PARALLEL_END:
                    visitor.parallelEnd(this.currentParallelStartNode, prev, this);
                    break;
                case PARALLEL_START:
                    visitor.parallelStart(myCurrent, prev, this);
                    break;
                case PARALLEL_BRANCH_END:
                    visitor.parallelBranchEnd(myCurrent, this.currentParallelStartNode, this);
                    break;
                case PARALLEL_BRANCH_START:
                    visitor.parallelBranchStart(myCurrent, this.currentParallelStartNode, this);
                    break;
                default:
                    break;
            }
        }
    }

}

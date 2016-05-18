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

import org.jenkinsci.plugins.workflow.graph.BlockEndNode;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowEndNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

/**
 * Scanner that will scan down all forks when we hit parallel blocks before continuing, but generally runs in linear order
 * Think of it as the opposite of {@link DepthFirstScanner}.
 *
 * This is a fairly efficient way to visit all FlowNodes, and provides three useful guarantees:
 *   - Every FlowNode is visited, and visited EXACTLY ONCE (not true for LinearScanner)
 *   - All parallel branches are visited before we move past the parallel block (not true for DepthFirstScanner)
 *   - For EVERY block, the BlockEndNode is visited before the BlockStartNode (not true for DepthFirstScanner)
 *
 * The big advantages of this approach:
 *   - Blocks are visited in the order they end (no backtracking) - helps with working a block at a time
 *   - Points are visited in linear order within a block (easy to use for analysis)
 *   - Minimal state information needed
 *   - Branch information is available for use here
 *
 * @author <samvanoort@gmail.com>Sam Van Oort</samvanoort@gmail.com>
 */
public class ForkScanner extends AbstractFlowScanner {

    // Last element in stack is end of myCurrent parallel start, first is myCurrent start
    ArrayDeque<ParallelBlockStart> parallelBlockStartStack = new ArrayDeque<ParallelBlockStart>();

    /** FlowNode that will terminate the myCurrent parallel block */
    FlowNode currentParallelStartNode = null;

    ParallelBlockStart currentParallelStart = null;

    private boolean walkingFromFinish = false;

    @Override
    protected void reset() {
        parallelBlockStartStack.clear();
        currentParallelStart = null;
        currentParallelStartNode = null;
        myCurrent = null;
        myNext = null;
    }

    /** If true, we are walking from the flow end node and have a complete view of the flow */
    public boolean isWalkingFromFinish() {
        return walkingFromFinish;
    }

    /** Tracks state for parallel blocks */
    protected static class ParallelBlockStart {
        protected BlockStartNode forkStart; // This is the node with child branches
        protected int remainingBranches;
        protected int totalBranches;
        protected ArrayDeque<FlowNode> unvisited;  // Remaining branches of this that we have have not visited yet

        protected ParallelBlockStart(BlockStartNode forkStart, int branchCount) {
            this.forkStart = forkStart;
            this.remainingBranches = branchCount;
        }
    }

    protected static abstract class FlowPiece {
        long startTime;
        long endTime;
        long pauseDuration;
        String statusCode;

        // Bounds for a block
        String startId;
        String endId;
    }

    protected static class FlowSegment extends FlowPiece {
        ArrayList<FlowNode> visited = new ArrayList<FlowNode>();
        FlowPiece before;
        FlowPiece after;

        /**
         * We have discovered a forking node intersecting our FlowSegment in the middle
         * Now we need to split the flow
         * @param nodeMapping Mapping of BlockStartNodes to flowpieces (forks or segments)
         * @param forkPoint Node where the flows intersec
         * @param forkBranch Flow piece that is joining this
         */
        public void split(@Nonnull HashMap<FlowNode, FlowPiece> nodeMapping, @Nonnull BlockStartNode forkPoint, @Nonnull FlowPiece forkBranch) {
            int index = visited.indexOf(forkPoint);
            if (index < 0) {
                throw new IllegalStateException("Tried to split a segment where the node doesn't exist in this segment");
            }

            // Execute the split: create a new fork at the fork point, and shuffle the part of the flow after it
            //   to a new segment and add that to the fork
            Fork newFork = new Fork(forkPoint);
            FlowSegment newSegment = new FlowSegment();
            newSegment.after = this.after;
            newSegment.before = newFork;
            if (visited.size() > index+1) {
                newSegment.visited.addAll(index+1, visited);
            }
            newFork.before = this;
            newFork.following.add(forkBranch);
            newFork.following.add(newSegment);
            this.after = newFork;

            // Remove the nodes after the split, and remap the fork points
            this.visited.subList(index,visited.size()-1).clear();
            for (FlowNode n : newSegment.visited) {
                nodeMapping.put(n, newSegment);
            }
            nodeMapping.put(forkPoint, newFork);
        }

        public void add(FlowNode f) {
            this.visited.add(f);
        }
    }

    protected static class Fork extends FlowPiece {
        FlowPiece before;
        BlockStartNode forkNode;
        List<FlowPiece> following = new ArrayList<FlowPiece>();

        public Fork(BlockStartNode forkNode) {
            this.forkNode = forkNode;
        }
    }

    /** References from a branch to parent, used for creating a sorted hierarchy */
    protected static class ForkRef implements  Comparable<ForkRef> {
        int depth;
        FlowNode self;
        FlowNode parent;

        /** Sort by depth then by parents, other than that irrelevent */
        @Override
        public int compareTo(ForkRef o) {
            if (o == null) {
                return -1;
            }
            if (this.depth != o.depth) {
                return (this.depth - o.depth);  // Deepest first, sorting in reverse order
            }
            return (this.parent.getId().compareTo(o.parent.getId()));
        }

        public boolean equals(Object o) {
            if (o == this) {
                return true;
            } else if (o == null || !(o instanceof ForkRef)) {
                return false;
            }
            return o != null && o instanceof ForkRef && ((ForkRef)o).depth == this.depth &&
                    ((ForkRef)o).self == this.self && ((ForkRef)o).parent == this.parent;
        }

        protected ForkRef(int depth, FlowNode self, FlowNode parent) {
            this.depth = depth;
            this.self = self;
            this.parent = parent;
        }
    }

    /** Accumulate all the branch references here, recursively */
    private void addForkRefs(List<ForkRef> refs, Fork myFork, int currentDepth) {
        List<FlowPiece> pieces = myFork.following;
        for (FlowPiece f : pieces) {
            FlowSegment fs = (FlowSegment)f;
            refs.add(new ForkRef(currentDepth+1, fs.visited.get(fs.visited.size()-1), myFork.forkNode));
            if (fs.after != null && fs.after instanceof Fork) {
                addForkRefs(refs, (Fork)fs.after, currentDepth+1);
            }
        }
    }

    /*private void addToRefs(List<ForkRef> refList) {
        Collections.sort(refList);
        for (ForkRef fr : refList) {
            // Add appropriate entries to queue, etc
        }
    }*/

    /**
     * Constructs the tree mapping each flowNode to its nearest
     * @param heads
     */
     void leastCommonAncestor(@Nonnull Set<FlowNode> heads) {
         // FIX ME: nest in the parallel blockstart nodes, as we see further back ones, add them on hte opposite side of pushing

        HashMap<FlowNode, FlowPiece> branches = new HashMap<FlowNode, FlowPiece>();
        ArrayList<Filterator<FlowNode>> iterators = new ArrayList<Filterator<FlowNode>>();
        ArrayList<FlowSegment> liveHeads = new ArrayList<FlowSegment>();

        for (FlowNode f : heads) {
            iterators.add(FlowScanningUtils.filterableEnclosingBlocks(f));
            FlowSegment b = new FlowSegment();
            b.add(f);
            liveHeads.add(b);
            branches.put(f, b);
        }

        // Walk through until everything has merged to one ancestor
        while (iterators.size() > 1) {
            ListIterator<Filterator<FlowNode>> itIterator = iterators.listIterator();
            ListIterator<FlowSegment> pieceIterator = liveHeads.listIterator();

            while(itIterator.hasNext()) {
                Filterator<FlowNode> blockStarts = itIterator.next();
                FlowSegment myPiece = pieceIterator.next();

                // Welp we hit the end of a branch
                if (!blockStarts.hasNext()) {
                    pieceIterator.remove();
                    itIterator.remove();
                    continue;
                }

                FlowNode nextHead = blockStarts.next();
                FlowPiece existingBranch = branches.get(nextHead);
                if (existingBranch != null) { //
                    // Found a case where they convert, replace with a convergent branch
                    if (existingBranch instanceof Fork) {
                        Fork f = (Fork)existingBranch;
                        f.following.add(myPiece);
                    } else {
                        ((FlowSegment)existingBranch).split(branches, (BlockStartNode)nextHead, myPiece);
                    }
                    itIterator.remove();
                    pieceIterator.remove();
                } else {
                    myPiece.add(nextHead);
                    branches.put(nextHead, myPiece);
                }
            }
        }

        // Add the ancestry to the forks, note that we alternate fork-flowsegment-fork
        ArrayList<ForkRef> refs = new ArrayList<ForkRef>();
        ArrayDeque<Fork> children = new ArrayDeque<Fork>();
        children.add((Fork)liveHeads.get(0).after);
        while (children.size() > 0) {
            Fork child = children.pop();
            if (child.following  != null && child.following.size() > 0) {
                // ad dthe fork child and its forks
            }

        }
        Collections.sort(refs);
        // Now we add start points

         // TODO : don't use sorted ForkRef, just applying the ParallelBlockStarts as we go, pushing in the tree levels


        // FIRST: we visit all nodes on the same level, with the same parent
        // Add refs to an
        // Then we visit their parents


    }

    @Override
    protected void setHeads(@Nonnull Collection<FlowNode> heads) {
        if (heads.size() > 1) {
            //throw new IllegalArgumentException("ForkedFlowScanner can't handle multiple head nodes yet");
            leastCommonAncestor(new HashSet<FlowNode>(heads));
            walkingFromFinish = false;
        } else {
            FlowNode f = heads.iterator().next();
            walkingFromFinish = f instanceof FlowEndNode;
            myCurrent = f;
            myNext = f;
        }
    }

    /**
     * Return the node that begins the current parallel head
     * @return
     */
    @CheckForNull
    public FlowNode getCurrentParallelStartNode() {
        return currentParallelStartNode;
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
            if (currentParallelStart.remainingBranches-- <= 1) {  // Strip off a completed branch
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
            else { // We're at the top
                currentParallelStart = null;
                currentParallelStartNode = null;
                parallelBlockStartStack.pop();
            }
        } else {
            throw new IllegalStateException("Hit a BlockStartNode with multiple children, and no record of the start!");
        }

        // Handle cases where the BlockStartNode for the parallel block is blackListed
        return (output != null && !myBlackList.contains(output)) ? output : null;
    }

    @Override
    protected FlowNode next(@Nonnull FlowNode current, @Nonnull Collection<FlowNode> blackList) {
        FlowNode output = null;

        // First we look at the parents of the current node if present
        if (current != null) {
            List<FlowNode> parents = current.getParents();
            if (parents == null || parents.size() == 0) {
                // welp done with this node, guess we consult the queue?
            } else if (parents.size() == 1) {
                FlowNode p = parents.get(0);
                if (p == currentParallelStartNode) {
                    // Terminating a parallel scan
                    FlowNode temp = hitParallelStart();
                    if (temp != null) { // Startnode for current parallel block now that it is done
                        return temp;
                    }
                } else if (!blackList.contains(p)) {
                    return p;
                }
            } else if (current instanceof BlockEndNode && parents.size() > 1) {
                // We must be a BlockEndNode that begins this
                BlockEndNode end = ((BlockEndNode) current);
                FlowNode possibleOutput = hitParallelEnd(end, parents, blackList); // What if output is block but other branches aren't?
                if (possibleOutput != null) {
                    return possibleOutput;
                }
            } else {
                throw new IllegalStateException("Found a FlowNode with multiple parents that isn't the end of a block! "+ this.myCurrent.toString());
            }
        }
        if (currentParallelStart != null && currentParallelStart.unvisited.size() > 0) {
            output = currentParallelStart.unvisited.pop();
        }

        return output;
    }
}

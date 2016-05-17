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
import java.util.Objects;
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

    /** These are the BlockStartNodes that begin parallel blocks
     *  There will be one entry for every executing parallel branch in current flow
     */
    ArrayDeque<FlowNode> forkStarts = new ArrayDeque<FlowNode>();

    /** FlowNode that will terminate the current parallel block */
    FlowNode currentParallelStart = null;

    /** How deep are we in parallel branches, if 0 we are linear */
    protected int parallelDepth = 0;

    @Override
    protected void reset() {
        if (_queue == null) {
            _queue = new ArrayDeque<FlowNode>();
        } else {
            _queue.clear();
        }
        forkStarts.clear();
        parallelDepth =0;
        currentParallelStart = null;
        _current = null;
        _next = null;
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

    protected static class AtomicStep extends FlowPiece {

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

    /** Endpoint for a fork */
    protected static class ForkHead {
        protected FlowNode head;
        protected int branchCount = 0;
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


        // FIRST: we visit all nodes on the same level, with the same parent
        // Add refs to an
        // Then we visit their parents


    }

    @Override
    protected void setHeads(@Nonnull Collection<FlowNode> heads) {
        if (heads.size() > 1) {
            //throw new IllegalArgumentException("ForkedFlowScanner can't handle multiple head nodes yet");
            leastCommonAncestor(new HashSet<FlowNode>(heads));
        }
        _current = null;
        _queue.addAll(heads);
        _current = _queue.poll();
        _next = _current;
    }

    public int getParallelDepth() {
        return parallelDepth;
    }

    /**
     * Return the node that begins the current parallel head
     * @return
     */
    @CheckForNull
    public FlowNode getCurrentParallelStart() {
        return currentParallelStart;
    }

    /**
     * Invoked when we start entering a parallel block (walking from head of the flow, so we see the block end first)
     * @param endNode Node where parents merge (final end node for the parallel block)
     * @param parents Parent nodes that end here
     * @return FlowNode next node to visit
     */
    protected FlowNode hitParallelEnd(BlockEndNode endNode, List<FlowNode> parents, Collection<FlowNode> blackList) {
        int branchesAdded = 0;
        BlockStartNode start = endNode.getStartNode();
        FlowNode output = null;
        for (FlowNode f : parents) {
            if (!blackList.contains(f)) {
                // If this is the first fork, we'll walk up it, and then queue up the others
                if (branchesAdded == 0) {
                    currentParallelStart = start;
                    output = f;
                } else {
                    _queue.push(f);
                    forkStarts.push(start);
                }
                branchesAdded++;
            }
        }
        if (branchesAdded > 0) {
            parallelDepth++;
        }
        return output;
    }

    /**
     * Invoked when we complete parallel block, walking from the head (so encountered after the end)
     * @return FlowNode if we're the last node
     */
    protected FlowNode hitParallelStart() {
        FlowNode output = null;
        if (forkStarts.size() > 0) { // More forks (or nested parallel forks) remain
            FlowNode end = forkStarts.peek();
            // Nested parallel branches, finished nested level so we visit the head and enclosing parallel block
            if (end != currentParallelStart) {
                parallelDepth--;
                output = currentParallelStart;
            }

            // If the current end == currentParallelStart then we are finishing another branch of current flow
            currentParallelStart = end;
        } else {  // We're now at the top level of the flow, having finished our last (nested) parallel fork
            output = currentParallelStart;
            currentParallelStart = null;
            parallelDepth--;
        }
        // Handle cases where the BlockStartNode for the parallel block is blackListed
        return (output != null && !_blackList.contains(output)) ? output : null;
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
                if (p == currentParallelStart) {
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
                throw new IllegalStateException("Found a FlowNode with multiple parents that isn't the end of a block! "+_current.toString());
            }
        }
        if (_queue.size() > 0) {
            output = _queue.pop();
            currentParallelStart = forkStarts.pop();
        }

        return output;
    }
}

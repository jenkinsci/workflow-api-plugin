package org.jenkinsci.plugins.workflow.graph;

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

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import hudson.model.Action;
import org.jenkinsci.plugins.workflow.actions.ErrorAction;
import org.jenkinsci.plugins.workflow.actions.LabelAction;
import org.jenkinsci.plugins.workflow.actions.LogAction;
import org.jenkinsci.plugins.workflow.actions.StageAction;
import org.jenkinsci.plugins.workflow.actions.WorkspaceAction;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Generified algorithms for scanning pipeline flow graphs for information
 * Supports a variety of algorithms for searching, and pluggable conditions
 * Worth noting: predicates may be stateful here, and may see some or all of the nodes, depending on the scan method used.
 *
 * @author <samvanoort@gmail.com>Sam Van Oort</samvanoort@gmail.com>
 */
public class FlowScanner {

    /**
     * Create a predicate that will match on all FlowNodes having a specific action present
     * @param actionClass Action class to look for
     * @param <T> Action type
     * @return Predicate that will match when FlowNode has the action given
     */
    @Nonnull
    public static <T extends Action>  Predicate<FlowNode> nodeHasActionPredicate(@Nonnull final Class<T> actionClass) {
        return new Predicate<FlowNode>() {
            @Override
            public boolean apply(FlowNode input) {
                return (input != null && input.getAction(actionClass) != null);
            }
        };
    }

    // Default predicates
    public static final Predicate<FlowNode> MATCH_HAS_LABEL = nodeHasActionPredicate(LabelAction.class);
    public static final Predicate<FlowNode> MATCH_IS_STAGE = nodeHasActionPredicate(StageAction.class);
    public static final Predicate<FlowNode> MATCH_HAS_WORKSPACE = nodeHasActionPredicate(WorkspaceAction.class);
    public static final Predicate<FlowNode> MATCH_HAS_ERROR = nodeHasActionPredicate(ErrorAction.class);
    public static final Predicate<FlowNode> MATCH_HAS_LOG = nodeHasActionPredicate(LogAction.class);
    public static final Predicate<FlowNode> MATCH_BLOCK_START = (Predicate)Predicates.instanceOf(BlockStartNode.class);

    public interface FlowNodeVisitor {
        /**
         * Visit the flow node, and indicate if we should continue analysis
         * @param f Node to visit
         * @return False if node is done
         */
        public boolean visit(@Nonnull FlowNode f);
    }

    /** Interface to be used for scanning/analyzing FlowGraphs with support for different visit orders
     */
    public interface ScanAlgorithm {

        /**
         * Search for first node (walking from the heads through parents) that matches the condition
         * @param heads Nodes to start searching from, which may be filtered against blackList
         * @param stopNodes Search doesn't go beyond any of these nodes, null or empty will run to end of flow
         * @param matchPredicate Matching condition for search
         * @return First node matching condition, or null if none found
         */
        @CheckForNull
        public FlowNode findFirstMatch(@CheckForNull Collection<FlowNode> heads, @CheckForNull Collection<FlowNode> stopNodes, @Nonnull Predicate<FlowNode> matchPredicate);

        /**
         * Search for first node (walking from the heads through parents) that matches the condition
         * @param heads Nodes to start searching from, which may be filtered against a blackList
         * @param stopNodes Search doesn't go beyond any of these nodes, null or empty will run to end of flow
         * @param matchPredicate Matching condition for search
         * @return All nodes matching condition
         */
        @Nonnull
        public Collection<FlowNode> filteredNodes(@CheckForNull Collection<FlowNode> heads, @CheckForNull Collection<FlowNode> stopNodes, @Nonnull Predicate<FlowNode> matchPredicate);

        /** Used for extracting metrics from the flow graph */
        public void visitAll(@CheckForNull Collection<FlowNode> heads, FlowNodeVisitor visitor);
    }

    public static Filterator<FlowNode> filterableEnclosingBlocks(FlowNode f) {
        LinearBlockHoppingScanner scanner = new LinearBlockHoppingScanner();
        scanner.setup(f);
        return scanner.filter(MATCH_BLOCK_START);
    }

    /** Iterator that exposes filtering */
    public interface Filterator<T> extends Iterator<T> {
        /** Returns a filtered view of an iterable */
        @Nonnull
        public Filterator<T> filter(@Nonnull Predicate<T> matchCondition);
    }

    /** Filters an iterator against a match predicate */
    public static class FilteratorImpl<T> implements Filterator<T> {
        boolean hasNext = false;
        T nextVal;
        Iterator<T> wrapped;
        Predicate<T> matchCondition;

        public FilteratorImpl<T> filter(Predicate<T> matchCondition) {
            return new FilteratorImpl<T>(this, matchCondition);
        }

        public FilteratorImpl(@Nonnull Iterator<T> it, @Nonnull Predicate<T> matchCondition) {
            this.wrapped = it;
            this.matchCondition = matchCondition;

            while(it.hasNext()) {
                T val = it.next();
                if (matchCondition.apply(val)) {
                    this.nextVal = val;
                    hasNext = true;
                    break;
                }
            }
        }

        @Override
        public boolean hasNext() {
            return hasNext;
        }

        @Override
        public T next() {
            T returnVal = nextVal;
            T nextMatch = null;

            boolean foundMatch = false;
            while(wrapped.hasNext()) {
                nextMatch = wrapped.next();
                if (matchCondition.apply(nextMatch)) {
                    foundMatch = true;
                    break;
                }
            }
            if (foundMatch) {
                this.nextVal = nextMatch;
                this.hasNext = true;
            } else {
                this.nextVal = null;
                this.hasNext = false;
            }
            return returnVal;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * Base class for flow scanners, which offers basic methods and stubs for algorithms
     * Scanners store state internally, and are not thread-safe but are reusable
     * Scans/analysis of graphs is implemented via internal iteration to allow reusing algorithm bodies
     * However internal iteration has access to additional information
     */
    public static abstract class AbstractFlowScanner implements ScanAlgorithm, Iterable <FlowNode>, Filterator<FlowNode> {

        // State variables, not all need be used
        protected ArrayDeque<FlowNode> _queue;

        protected FlowNode _current;

        protected FlowNode _next;

        protected Collection<FlowNode> _blackList = Collections.EMPTY_SET;

        @Override
        public boolean hasNext() {
            return _next != null;
        }

        @Override
        public FlowNode next() {
            if (_next == null) {
                throw new NoSuchElementException();
            }

            // For computing timings and changes, it may be helpful to keep the previous result
            // by creating a variable _last and storing _current to it.

//            System.out.println("Current iterator val: " + ((_current == null) ? "null" : _current.getId()));
//            System.out.println("Next iterator val: " + ((_next == null) ? "null" : _next.getId()));
            _current = _next;
            _next = next(_blackList);
//            System.out.println("New next val: " + ((_next == null) ? "null" : _next.getId()));
            return _current;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("FlowGraphs are immutable, so FlowScanners can't remove nodes");
        }

        @Override
        public Iterator<FlowNode> iterator() {
            return this;
        }

        /**
         * Set up for iteration/analysis on a graph of nodes, initializing the internal state
         * @param heads The head nodes we start walking from (the most recently executed nodes,
         *              i.e. FlowExecution.getCurrentHeads()
         * @param blackList Nodes that we cannot visit or walk past (useful to limit scanning to only nodes after a specific point)
         * @return True if we can have nodes to work with, otherwise false
         */
        public boolean setup(@CheckForNull Collection<FlowNode> heads, @CheckForNull Collection<FlowNode> blackList) {
            if (heads == null || heads.size() == 0) {
                return false;
            }
            Collection<FlowNode> fastEndNodes = convertToFastCheckable(blackList);
            HashSet<FlowNode> filteredHeads = new HashSet<FlowNode>(heads);
            filteredHeads.removeAll(fastEndNodes);

            if (filteredHeads.size() == 0) {
                return false;
            }

            reset();
            _blackList = fastEndNodes;
            setHeads(filteredHeads);
            return true;
        }

        /**
         * Set up for iteration/analysis on a graph of nodes, initializing the internal state
         * @param head The head FlowNode to start walking back from
         * @param blackList Nodes that we cannot visit or walk past (useful to limit scanning to only nodes after a specific point)
         *                  null or empty collection means none
         * @return True if we can have nodes to work with, otherwise false
         */
        public boolean setup(@CheckForNull FlowNode head, @CheckForNull Collection<FlowNode> blackList) {
            if (head == null) {
                return false;
            }
            return setup(Collections.singleton(head), blackList);
        }

        public boolean setup(@CheckForNull FlowNode head) {
            if (head == null) {
                return false;
            }
            return setup(Collections.singleton(head), Collections.EMPTY_SET);
        }

        /** Public APIs need to invoke this before searches */
        protected abstract void reset();

        /** Add current head nodes to current processing set, after filtering by blackList */
        protected abstract void setHeads(@Nonnull Collection<FlowNode> filteredHeads);

        /**
         * Actual meat of the iteration, get the next node to visit, using & updating state as needed
         * @param blackList Nodes that are not eligible for visiting
         * @return Next node to visit, or null if we've exhausted the node list
         */
        @CheckForNull
        protected abstract FlowNode next(@Nonnull Collection<FlowNode> blackList);

        /** Convert stop nodes to a collection that can efficiently be checked for membership, handling nulls if needed */
        @Nonnull
        protected Collection<FlowNode> convertToFastCheckable(@CheckForNull Collection<FlowNode> nodeCollection) {
            if (nodeCollection == null || nodeCollection.size()==0) {
                return Collections.EMPTY_SET;
            } else  if (nodeCollection.size() == 1) {
                return Collections.singleton(nodeCollection.iterator().next());
            } else if (nodeCollection instanceof Set) {
                return nodeCollection;
            }
            return nodeCollection.size() > 5 ? new HashSet<FlowNode>(nodeCollection) : nodeCollection;
        }

        // Polymorphic methods for syntactic sugar

        @CheckForNull
        public FlowNode findFirstMatch(@CheckForNull Collection<FlowNode> heads, @Nonnull Predicate<FlowNode> matchPredicate) {
            return this.findFirstMatch(heads, null, matchPredicate);
        }

        @CheckForNull
        public FlowNode findFirstMatch(@CheckForNull FlowNode head, @Nonnull Predicate<FlowNode> matchPredicate) {
            return this.findFirstMatch(Collections.singleton(head), null, matchPredicate);
        }

        @CheckForNull
        public FlowNode findFirstMatch(@CheckForNull FlowExecution exec, @Nonnull Predicate<FlowNode> matchPredicate) {
            if (exec != null && exec.getCurrentHeads() != null) {
                return this.findFirstMatch(exec.getCurrentHeads(), null, matchPredicate);
            }
            return null;
        }

        @Nonnull
        public List<FlowNode> filteredNodes(@CheckForNull Collection<FlowNode> heads, @Nonnull Predicate<FlowNode> matchPredicate) {
            return this.filteredNodes(heads, null, matchPredicate);
        }

        @Nonnull
        public List<FlowNode> filteredNodes(@CheckForNull FlowNode head, @Nonnull Predicate<FlowNode> matchPredicate) {
            return this.filteredNodes(Collections.singleton(head), null, matchPredicate);
        }

        // Basic algo impl
        public FlowNode findFirstMatch(@CheckForNull Collection<FlowNode> heads,
                                               @CheckForNull Collection<FlowNode> endNodes,
                                               Predicate<FlowNode> matchCondition) {
            if (!setup(heads, endNodes)) {
                return null;
            }

            for (FlowNode f : this) {
                if (matchCondition.apply(f)) {
                    return f;
                }
            }
            return null;
        }

        // Basic algo impl
        @Nonnull
        public List<FlowNode> filteredNodes(@CheckForNull Collection<FlowNode> heads,
                                            @CheckForNull Collection<FlowNode> endNodes,
                                            Predicate<FlowNode> matchCondition) {
            if (!setup(heads, endNodes)) {
                return Collections.EMPTY_LIST;
            }

            ArrayList<FlowNode> nodes = new ArrayList<FlowNode>();
            for (FlowNode f : this) {
                if (matchCondition.apply(f)) {
                    nodes.add(f);
                }
            }
            return nodes;
        }

        public Filterator<FlowNode> filter(Predicate<FlowNode> filterCondition) {
            return new FilteratorImpl<FlowNode>(this, filterCondition);
        }

        /** Used for extracting metrics from the flow graph */
        @Nonnull
        public void visitAll(@CheckForNull Collection<FlowNode> heads, FlowNodeVisitor visitor) {
            if (!setup(heads, Collections.EMPTY_SET)) {
                return;
            }
            for (FlowNode f : this) {
                boolean canContinue = visitor.visit(f);
                if (!canContinue) {
                    break;
                }
            }
        }
    }

    /** Does a simple and efficient depth-first search:
     *   - This will visit each node exactly once, and walks through the first ancestry before revisiting parallel branches
     */
    public static class DepthFirstScanner extends AbstractFlowScanner {

        protected HashSet<FlowNode> _visited = new HashSet<FlowNode>();

        protected void reset() {
            if (this._queue == null) {
                this._queue = new ArrayDeque<FlowNode>();
            } else {
                this._queue.clear();
            }
            this._visited.clear();
            this._current = null;
        }

        @Override
        protected void setHeads(@Nonnull Collection<FlowNode> heads) {
            Iterator<FlowNode> it = heads.iterator();
            if (it.hasNext()) {
                FlowNode f = it.next();
                _current = f;
                _next = f;
            }
            while (it.hasNext()) {
                _queue.add(it.next());
            }
        }

        @Override
        protected FlowNode next(@Nonnull Collection<FlowNode> blackList) {
            FlowNode output = null;
            // Walk through parents of current node
            if (_current != null) {
                List<FlowNode> parents = _current.getParents();
                if (parents != null) {
                    for (FlowNode f : parents) {
                        if (!blackList.contains(f) && !_visited.contains(f)) {
                            if (output == null ) {
                                output = f;
                            } else {
                                _queue.push(f);
                            }
                        }
                    }
                }
            }

            if (output == null && _queue.size() > 0) {
                output = _queue.pop();
            }
            _visited.add(output); // No-op if null
            return output;
        }
    }

    /**
     * Scans through a single ancestry, does not cover parallel branches
     * Use case: we don't care about parallel branches
     */
    public static class LinearScanner extends AbstractFlowScanner {

        @Override
        protected void reset() {
            this._current = null;
            this._next = null;
            this._blackList = Collections.EMPTY_SET;
        }

        @Override
        protected void setHeads(@Nonnull Collection<FlowNode> heads) {
            if (heads.size() > 0) {
                this._current = heads.iterator().next();
                this._next = this._current;
            }
        }

        @Override
        protected FlowNode next(@Nonnull Collection<FlowNode> blackList) {
            if (_current == null) {
                return null;
            }
            List<FlowNode> parents = _current.getParents();
            if (parents != null && parents.size() > 0) {
                for (FlowNode f : parents) {
                    if (!blackList.contains(f)) {
                        return f;
                    }
                }
            }
            return null;
        }
    }

    /**
     * LinearScanner that jumps over nested blocks
     * Use case: finding information about enclosing blocks or preceding nodes
     *   - Ex: finding out the executor workspace used to run a flownode
     * Caveats:
     *   - If you start on the last node of a completed flow, it will jump straight to start (by design)
     *   - Will only consider the first branch in a parallel case
     */
    public static class LinearBlockHoppingScanner extends LinearScanner {

        @Override
        public boolean setup(@CheckForNull Collection<FlowNode> heads, @CheckForNull Collection<FlowNode> blackList) {
            boolean possiblyStartable = super.setup(heads, blackList);
            return possiblyStartable && _current != null;  // In case we start at an end block
        }

        @Override
        protected void setHeads(@Nonnull Collection<FlowNode> heads) {
            if (heads.size() > 0) {
                this._current = jumpBlockScan(heads.iterator().next(), _blackList);
                this._next = this._current;
            }
        }

        /** Keeps jumping over blocks until we hit the first node preceding a block */
        @CheckForNull
        protected FlowNode jumpBlockScan(@CheckForNull FlowNode node, @Nonnull Collection<FlowNode> blacklistNodes) {
            FlowNode candidate = node;

            // Find the first candidate node preceding a block... and filtering by blacklist
            while (candidate != null && candidate instanceof BlockEndNode) {
                candidate = ((BlockEndNode) candidate).getStartNode();
                if (blacklistNodes.contains(candidate)) {
                    return null;
                }
                List<FlowNode> parents = candidate.getParents();
                if (parents == null || parents.size() == 0) {
                    return null;
                }
                boolean foundNode = false;
                for (FlowNode f : parents) {
                    if (!blacklistNodes.contains(f)) {
                        candidate = f;  // Loop again b/c could be BlockEndNode
                        foundNode = true;
                        break;
                    }
                }
                if (!foundNode) {
                    return null;
                }
            }

            return candidate;
        }

        @Override
        protected FlowNode next(@Nonnull Collection<FlowNode> blackList) {
            if (_current == null) {
                return null;
            }
            List<FlowNode> parents = _current.getParents();
            if (parents != null && parents.size() > 0) {
                for (FlowNode f : parents) {
                    if (!blackList.contains(f)) {
                        return (f instanceof BlockEndNode) ? jumpBlockScan(f, blackList) : f;
                    }
                }
            }
            return null;
        }
    }

    /**
     * Scanner that will scan down forks when we hit parallel blocks.
     * Think of it as the opposite of {@link org.jenkinsci.plugins.workflow.graph.FlowScanner.DepthFirstScanner}:
     *   - We visit every node exactly once, but walk through all parallel forks before resuming the main flow
     *
     * This is near-optimal in many cases, since it keeps minimal state information and explores parallel blocks first
     * It is also very easy to make it branch/block-aware, since we have all the fork information at all times.
     */
    public static class ForkScanner extends AbstractFlowScanner {

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

        @Override
        protected void setHeads(@Nonnull Collection<FlowNode> heads) {
            if (heads.size() > 1) {
                throw new IllegalArgumentException("ForkedFlowScanner can't handle multiple head nodes yet");
                // TODO We need to implement this using filterableEnclosingBlocks
                // and add nodes to with the start of their parallel branches
            }
            _current = null; // Somehow set head like linearhoppoingflowscanner
            _queue.addAll(heads);
            _current = _queue.poll();
            _next = _current;
        }

        /**
         * Invoked when we start entering a parallel block (walking from head of the flow, so we see the block end first)
         * @param endNode
         * @param heads
         */
        protected void hitParallelEnd(BlockEndNode endNode, List<FlowNode> heads, Collection<FlowNode> blackList) {
            int branchesAdded = 0;
            BlockStartNode start = endNode.getStartNode();
            for (FlowNode f : heads) {
                if (!blackList.contains(f)) {
                    if (branchesAdded == 0) { // We use references because it is more efficient
                        currentParallelStart = start;
                    } else {
                        forkStarts.push(start);
                    }
                    branchesAdded++;
                }
            }
            if (branchesAdded > 0) {
                parallelDepth++;
            }
        }

        /**
         * Invoked when we complete parallel block, walking from the head (so encountered after the end)
         * @param startNode StartNode for the block,
         * @param parallelChild Parallel child node that is ending this
         * @return FlowNode if we're the last node
         */
        protected FlowNode hitParallelStart(FlowNode startNode, FlowNode parallelChild) {
            FlowNode output = null;
            if (forkStarts.size() > 0) { // More forks (or nested parallel forks) remain
                FlowNode end = forkStarts.pop();
                if (end != currentParallelStart) { // Nested parallel branches, and we finished this fork
                    parallelDepth--;
                    output = currentParallelStart;
                }
                // TODO handle case where we do early exit because we encountered stop node

                // If the current end == currentParallelStart then we are finishing another branch of current flow
                currentParallelStart = end;
            } else {  // We're now at the top level of the flow, having finished our last (nested) parallel fork
                output = currentParallelStart;
                currentParallelStart = null;
                parallelDepth--;
            }
            return output;
        }

        @Override
        protected FlowNode next(@Nonnull Collection<FlowNode> blackList) {
            FlowNode output = null;

            // First we look at the parents of the current node if present
            if (_current != null) {
                List<FlowNode> parents = _current.getParents();
                if (parents == null || parents.size() == 0) {
                    // welp do  ne with this node, guess we consult the queue?
                } else if (parents.size() == 1) {
                    FlowNode p = parents.get(0);
                    if (p == currentParallelStart) {
                        // Terminating a parallel scan
                        FlowNode temp = hitParallelStart(currentParallelStart, p);
                        if (temp != null) { // Startnode for current parallel block now that it is done
                            return temp;
                        }
                    } else  if (!blackList.contains(p)) {
                        return p;
                    }
                } else if (_current instanceof BlockEndNode && parents.size() > 1) {
                    // We must be a BlockEndNode that begins this
                    BlockEndNode end = ((BlockEndNode) _current);
                    hitParallelEnd(end, parents, blackList);
                    // Return a node?
                } else {
                    throw new IllegalStateException("Found a FlowNode with multiple parents that isn't the end of a block! "+_current.toString());
                }
            }
            if (_queue.size() > 0) {
                output = _queue.pop();
                currentParallelStart = forkStarts.pop();
            }
            // Welp, now we consult the queue since we've not hit a likely candidate among parents

            return output;
        }
    }
}

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
import java.util.HashSet;
import java.util.List;
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
        public Collection<FlowNode> filter(@CheckForNull Collection<FlowNode> heads, @CheckForNull Collection<FlowNode> stopNodes, @Nonnull Predicate<FlowNode> matchPredicate);

        /** Used for extracting metrics from the flow graph */
        public void visitAll(@CheckForNull Collection<FlowNode> heads, FlowNodeVisitor visitor);
    }

    /**
     * Base class for flow scanners, which offers basic methods and stubs for algorithms
     * Scanners store state internally, and are not thread-safe but are reusable
     * Scans/analysis of graphs is implemented via internal iteration to allow reusing algorithm bodies
     * However internal iteration has access to additional information
     */
    public static abstract class AbstractFlowScanner implements ScanAlgorithm {

        // State variables, not all need be used
        protected ArrayDeque<FlowNode> _queue;
        protected FlowNode _current;

        /** Public APIs need to invoke this before searches */
        protected abstract void initialize();

        /** Add current head nodes to current processing set */
        protected abstract void setHeads(@Nonnull Collection<FlowNode> heads);

        /**
         * Actual meat of the iteration, get the next node to visit, using & updating state as needed
         * @param blackList Nodes that are not eligible for visiting
         * @return Next node to visit, or null if we've exhausted the node list
         */
        @CheckForNull
        protected abstract FlowNode next(@Nonnull Collection<FlowNode> blackList);


        /** Fast internal scan from start through single-parent (unbranched) nodes until we hit a node with one of the following:
         *      - Multiple parents
         *      - No parents
         *      - Satisfies the endCondition predicate
         *
         * @param endCondition Predicate that ends search
         * @return Node satisfying condition
         */
        @CheckForNull
        protected static FlowNode linearScanUntil(@Nonnull FlowNode start, @Nonnull Predicate<FlowNode> endCondition) {
            while(true) {
                if (endCondition.apply(start)){
                    break;
                }
                List<FlowNode> parents = start.getParents();
                if (parents == null || parents.size() == 0 || parents.size() > 1) {
                    break;
                }
                start = parents.get(0);
            }
            return start;
        }

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
        public Collection<FlowNode> findAllMatches(@CheckForNull Collection<FlowNode> heads, @Nonnull Predicate<FlowNode> matchPredicate) {
            return this.filter(heads, null, matchPredicate);
        }



        // Basic algo impl
        public FlowNode findFirstMatch(@CheckForNull Collection<FlowNode> heads,
                                               @CheckForNull Collection<FlowNode> endNodes,
                                               Predicate<FlowNode> matchCondition) {
            if (heads == null || heads.size() == 0) {
                return null;
            }

            initialize();
            Collection<FlowNode> fastEndNodes = convertToFastCheckable(endNodes);
            Collection<FlowNode> filteredHeads = new HashSet<FlowNode>(heads);
            filteredHeads.removeAll(fastEndNodes);
            if (filteredHeads.size() == 0) {
                return null;
            }
            this.setHeads(filteredHeads);

            while ((_current = next(fastEndNodes)) != null) {
                if (matchCondition.apply(_current)) {
                    return _current;
                }
            }
            return null;
        }

        // Basic algo impl
        public List<FlowNode> filter(@CheckForNull Collection<FlowNode> heads,
                                     @CheckForNull Collection<FlowNode> endNodes,
                                     Predicate<FlowNode> matchCondition) {
            if (heads == null || heads.size() == 0) {
                return Collections.EMPTY_LIST;
            }
            initialize();
            Collection<FlowNode> fastEndNodes = convertToFastCheckable(endNodes);
            Collection<FlowNode> filteredHeads = new HashSet<FlowNode>(heads);
            if (filteredHeads.size() == 0) {
                return Collections.EMPTY_LIST;
            }
            filteredHeads.removeAll(fastEndNodes);
            this.setHeads(filteredHeads);
            ArrayList<FlowNode> nodes = new ArrayList<FlowNode>();

            while ((_current = next(fastEndNodes)) != null) {
                if (matchCondition.apply(_current)) {
                    nodes.add(_current);
                }
            }
            return nodes;
        }

        /** Used for extracting metrics from the flow graph */
        public void visitAll(@CheckForNull Collection<FlowNode> heads, FlowNodeVisitor visitor) {
            if (heads == null || heads.size() == 0) {
                return;
            }
            initialize();
            this.setHeads(heads);
            Collection<FlowNode> endNodes = Collections.EMPTY_SET;

            boolean continueAnalysis = true;
            while (continueAnalysis && (_current = next(endNodes)) != null) {
                continueAnalysis = visitor.visit(_current);
            }
        }
    }

    /** Does a simple and efficient depth-first search:
     *   - This will visit each node exactly once, and walks through the first ancestry before revisiting parallel branches
     */
    public static class DepthFirstScanner extends AbstractFlowScanner {

        protected HashSet<FlowNode> _visited = new HashSet<FlowNode>();

        protected void initialize() {
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
            _queue.addAll(heads);
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
        protected boolean isFirst = true;

        @Override
        protected void initialize() {
            isFirst = true;
        }

        @Override
        protected void setHeads(@Nonnull Collection<FlowNode> heads) {
            if (heads.size() > 0) {
                this._current = heads.iterator().next();
            }
        }

        @Override
        protected FlowNode next(@Nonnull Collection<FlowNode> blackList) {
            if (_current == null) {
                return null;
            }
            if (isFirst) { // Kind of cheating, but works
                isFirst = false;
                return _current;
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
     */
    public static class LinearBlockHoppingScanner extends LinearScanner {

        protected FlowNode jumpBlock(FlowNode current) {
            return (current instanceof BlockEndNode) ?
                ((BlockEndNode)current).getStartNode() : current;
        }

        @Override
        protected FlowNode next(@Nonnull Collection<FlowNode> blackList) {
            if (_current == null) {
                return null;
            }
            if (isFirst) { // Hax, but solves the problem
                isFirst = false;
                return _current;
            }
            List<FlowNode> parents = _current.getParents();
            if (parents != null && parents.size() > 0) {
                for (FlowNode f : parents) {
                    if (!blackList.contains(f)) {
                        FlowNode jumped = jumpBlock(f);
                        if (jumped != f) {
                            _current = jumped;
                            return next(blackList);
                        } else {
                            return f;
                        }
                    }
                }
            }
            return null;
        }
    }

    /**
     * Scanner that will scan down forks when we hit parallel blocks.
     * Think of it as the opposite reverse of {@link org.jenkinsci.plugins.workflow.graph.FlowScanner.DepthFirstScanner}:
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
        protected void initialize() {
            if (_queue == null) {
                _queue = new ArrayDeque<FlowNode>();
            } else {
                _queue.clear();
            }
        }

        @Override
        protected void setHeads(@Nonnull Collection<FlowNode> heads) {
            // FIXME handle case where we have multiple heads - we need to do something special to handle the parallel branches
            // Until they rejoin the head!
            _current = null;
            _queue.addAll(heads);
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
                    // welp done with this node, guess we consult the queue?
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
            // Welp, now we consult the queue since we've not hit a likely candidate among parents

            return output;
        }
    }
}

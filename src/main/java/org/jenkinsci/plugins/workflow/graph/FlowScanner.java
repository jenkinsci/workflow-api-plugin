package org.jenkinsci.plugins.workflow.graph;

/*
 * The MIT License
 *
 * Copyright (c) 2013-2014, CloudBees, Inc.
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

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import hudson.model.Action;
import org.jenkinsci.plugins.workflow.actions.ErrorAction;
import org.jenkinsci.plugins.workflow.actions.LabelAction;
import org.jenkinsci.plugins.workflow.actions.LogAction;
import org.jenkinsci.plugins.workflow.actions.StageAction;
import org.jenkinsci.plugins.workflow.actions.WorkspaceAction;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Generified algorithms for scanning flows for information
 * Supports a variety of algorithms for searching, and pluggable conditions
 * Worth noting: predicates may be stateful here
 *
 * ANALYSIS method will
 * @author <samvanoort@gmail.com>Sam Van Oort</samvanoort@gmail.com>
 */
public class FlowScanner {
    /** Different ways of scannning the flow graph starting from one or more head nodes
     *  DEPTH_FIRST_ALL_PARENTS is the same as FlowWalker
     *    - scan through the first parents (depth first search), then come back to visit parallel branches
     *  BLOCK_SCOPES just skims through the blocks from the inside out, in reverse order
     *  SINGLE_PARENT only walks through the hierarchy of the first parent in the head (or heads)
     */
    public enum ScanType {
        DEPTH_FIRST_ALL_PARENTS,
        BLOCK_SCOPES,
        SINGLE_PARENT
    }

    /**
     * Create a predicate that will match on all FlowNodes having a specific action present
     * @param actionClass Action class to look for
     * @param <T> Action type
     * @return Predicate that will match when FlowNode has the action given
     */
    public static <T extends Action>  Predicate<FlowNode> createPredicateWhereActionExists(@Nonnull final Class<T> actionClass) {
        return new Predicate<FlowNode>() {
            @Override
            public boolean apply(FlowNode input) {
                return (input != null && input.getAction(actionClass) != null);
            }
        };
    }

    // Default predicates
    static final Predicate<FlowNode> MATCH_HAS_LABEL = createPredicateWhereActionExists(LabelAction.class);
    static final Predicate<FlowNode> MATCH_IS_STAGE = createPredicateWhereActionExists(StageAction.class);
    static final Predicate<FlowNode> MATCH_HAS_WORKSPACE = createPredicateWhereActionExists(WorkspaceAction.class);
    static final Predicate<FlowNode> MATCH_HAS_ERROR = createPredicateWhereActionExists(ErrorAction.class);
    static final Predicate<FlowNode> MATCH_HAS_LOG = createPredicateWhereActionExists(LogAction.class);

    /** One of many ways to scan the flowgraph */
    public interface ScanAlgorithm {

        /**
         * Search for first node (walking from the heads through parents) that matches the condition
         * @param heads Nodes to start searching from
         * @param stopNodes Search doesn't go beyond any of these nodes, null or empty will run to end of flow
         * @param matchPredicate Matching condition for search
         * @return First node matching condition, or null if none found
         */
        @CheckForNull
        public FlowNode findFirstMatch(@CheckForNull Collection<FlowNode> heads, @CheckForNull Collection<FlowNode> stopNodes, @Nonnull Predicate<FlowNode> matchPredicate);

        /**
         * Search for first node (walking from the heads through parents) that matches the condition
         * @param heads Nodes to start searching from
         * @param stopNodes Search doesn't go beyond any of these nodes, null or empty will run to end of flow
         * @param matchPredicate Matching condition for search
         * @return All nodes matching condition
         */
        @Nonnull
        public Collection<FlowNode> findAllMatches(@CheckForNull Collection<FlowNode> heads, @CheckForNull Collection<FlowNode> stopNodes, @Nonnull Predicate<FlowNode> matchPredicate);
    }

    /** Does a simple and efficient depth-first search */
    public static class DepthFirstScanner implements ScanAlgorithm {

        @Override
        public FlowNode findFirstMatch(@CheckForNull Collection<FlowNode> heads, @CheckForNull Collection<FlowNode> stopNodes, @Nonnull Predicate<FlowNode> matchPredicate) {
            if (heads == null || heads.size() == 0) {
                return null;
            }

            HashSet<FlowNode> visited = new HashSet<FlowNode>();
            ArrayDeque<FlowNode> queue = new ArrayDeque<FlowNode>(heads); // Only used for parallel branches

            // Do what we need to for fast tests
            Collection<FlowNode> fastStopNodes = (stopNodes == null || stopNodes.size() == 0) ? Collections.EMPTY_SET : stopNodes;
            if (fastStopNodes.size() > 10 && !(fastStopNodes instanceof Set)) {
                fastStopNodes = new HashSet<FlowNode>(fastStopNodes);
            }

            // TODO this will probably be more efficient if we work with the first node
            // or use a recursive solution for parallel forks
            while (!queue.isEmpty()) {
                FlowNode f = queue.pop();
                if (matchPredicate.apply(f)) {
                    return f;
                }
                visited.add(f);
                List<FlowNode> parents = f.getParents(); // Parents never null
                for (FlowNode p : parents) {
                    if (!visited.contains(p) && !fastStopNodes.contains(p)) {
                        queue.push(p);
                    }
                }
            }
            return null;
        }

        @Override
        public Collection<FlowNode> findAllMatches(@CheckForNull Collection<FlowNode> heads, @CheckForNull Collection<FlowNode> stopNodes, @Nonnull Predicate<FlowNode> matchPredicate) {
            if (heads == null || heads.size() == 0) {
                return Collections.EMPTY_LIST;
            }

            HashSet<FlowNode> visited = new HashSet<FlowNode>();
            ArrayDeque<FlowNode> queue = new ArrayDeque<FlowNode>(heads); // Only used for parallel branches
            ArrayList<FlowNode> matches = new ArrayList<FlowNode>();

            // Do what we need to for fast tests
            Collection<FlowNode> fastStopNodes = (stopNodes == null || stopNodes.size() == 0) ? Collections.EMPTY_SET : stopNodes;
            if (fastStopNodes.size() > 10 && !(fastStopNodes instanceof Set)) {
                fastStopNodes = new HashSet<FlowNode>(fastStopNodes);
            }

            // TODO this will probably be more efficient if use a variable for non-parallel flows and don't constantly push/pop array
            while (!queue.isEmpty()) {
                FlowNode f = queue.pop();
                if (matchPredicate.apply(f)) {
                    matches.add(f);
                }
                visited.add(f);
                List<FlowNode> parents = f.getParents(); // Parents never null
                for (FlowNode p : parents) {
                    if (!visited.contains(p) && !fastStopNodes.contains(p)) {
                        queue.push(p);
                    }
                }
            }
            return matches;
        }
    }

    /**
     * Scans through a single ancestry, does not cover parallel branches
     */
    public static class LinearScanner implements ScanAlgorithm {

        @Override
        public FlowNode findFirstMatch(@CheckForNull Collection<FlowNode> heads, @CheckForNull Collection<FlowNode> stopNodes, @Nonnull Predicate<FlowNode> matchPredicate) {
            if (heads == null || heads.size() == 0) {
                return null;
            }

            // Do what we need to for fast tests
            Collection<FlowNode> fastStopNodes = (stopNodes == null || stopNodes.size() == 0) ? Collections.EMPTY_SET : stopNodes;
            if (fastStopNodes.size() > 10 && !(fastStopNodes instanceof Set)) {
                fastStopNodes = new HashSet<FlowNode>(fastStopNodes);
            }

            FlowNode current = heads.iterator().next();
            while (current != null) {
                if (matchPredicate.apply(current)) {
                    return current;
                }
                List<FlowNode> parents = current.getParents(); // Parents never null
                current = null;
                for (FlowNode p : parents) {
                    if (!fastStopNodes.contains(p)) {
                        current = p;
                        break;
                    }
                }
            }
            return current;
        }

        @Override
        public Collection<FlowNode> findAllMatches(@CheckForNull Collection<FlowNode> heads, @CheckForNull Collection<FlowNode> stopNodes, @Nonnull Predicate<FlowNode> matchPredicate) {
            if (heads == null || heads.size() == 0) {
                return Collections.EMPTY_LIST;
            }

            // Do what we need to for fast tests
            Collection<FlowNode> fastStopNodes = (stopNodes == null || stopNodes.size() == 0) ? Collections.EMPTY_SET : stopNodes;
            if (fastStopNodes.size() > 10 && !(fastStopNodes instanceof Set)) {
                fastStopNodes = new HashSet<FlowNode>(fastStopNodes);
            }
            ArrayList<FlowNode> matches = new ArrayList<FlowNode>();

            FlowNode current = heads.iterator().next();
            while (current != null) {
                if (matchPredicate.apply(current)) {
                    matches.add(current);
                }
                List<FlowNode> parents = current.getParents(); // Parents never null
                current = null;
                for (FlowNode p : parents) {
                    if (!fastStopNodes.contains(p)) {
                        current = p;
                        break;
                    }
                }
            }
            return matches;
        }
    }

    /**
     * Scanner that jumps over nested blocks
     */
    public static class BlockHoppingScanner implements ScanAlgorithm {

        @Override
        public FlowNode findFirstMatch(@CheckForNull Collection<FlowNode> heads, @CheckForNull Collection<FlowNode> stopNodes, @Nonnull Predicate<FlowNode> matchPredicate) {
            if (heads == null || heads.size() == 0) {
                return null;
            }

            // Do what we need to for fast tests
            Collection<FlowNode> fastStopNodes = (stopNodes == null || stopNodes.size() == 0) ? Collections.EMPTY_SET : stopNodes;
            if (fastStopNodes.size() > 10 && !(fastStopNodes instanceof Set)) {
                fastStopNodes = new HashSet<FlowNode>(fastStopNodes);
            }

            FlowNode current = heads.iterator().next();
            while (current != null) {
                if (!(current instanceof BlockEndNode) && matchPredicate.apply(current)) {
                    return current;
                } else { // Hop the block
                    current = ((BlockEndNode) current).getStartNode();
                }
                List<FlowNode> parents = current.getParents(); // Parents never null
                current = null;
                for (FlowNode p : parents) {
                    if (!fastStopNodes.contains(p)) {
                        current = p;
                        break;
                    }
                }
            }
            return current;
        }

        @Override
        public Collection<FlowNode> findAllMatches(@CheckForNull Collection<FlowNode> heads, @CheckForNull Collection<FlowNode> stopNodes, @Nonnull Predicate<FlowNode> matchPredicate) {
            if (heads == null || heads.size() == 0) {
                return Collections.EMPTY_LIST;
            }

            // Do what we need to for fast tests
            Collection<FlowNode> fastStopNodes = (stopNodes == null || stopNodes.size() == 0) ? Collections.EMPTY_SET : stopNodes;
            if (fastStopNodes.size() > 10 && !(fastStopNodes instanceof Set)) {
                fastStopNodes = new HashSet<FlowNode>(fastStopNodes);
            }
            ArrayList<FlowNode> matches = new ArrayList<FlowNode>();

            FlowNode current = heads.iterator().next();
            while (current != null) {
                if (!(current instanceof BlockEndNode) && matchPredicate.apply(current)) {
                    matches.add(current);
                } else { // Hop the block
                    current = ((BlockEndNode) current).getStartNode();
                }
                List<FlowNode> parents = current.getParents(); // Parents never null
                current = null;
                for (FlowNode p : parents) {
                    if (!fastStopNodes.contains(p)) {
                        current = p;
                        break;
                    }
                }
            }
            return matches;
        }
    }
}

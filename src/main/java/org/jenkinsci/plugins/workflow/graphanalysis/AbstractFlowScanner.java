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

import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.concurrent.NotThreadSafe;
import java.util.function.Predicate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Core APIs and base logic for FlowScanners that extract information from a pipeline execution.
 *
 * <p>These iterate through the directed acyclic graph (DAG) or "flow graph" of {@link FlowNode}s produced when a pipeline runs.
 *
 * <p>This provides 6 base APIs to use, in decreasing expressiveness and increasing genericity:
 * <ul>
 *   <li>{@link #findFirstMatch(Collection, Collection, Predicate)}: find the first FlowNode matching predicate condition.</li>
 *   <li>{@link #filteredNodes(Collection, Collection, Predicate)}: return the collection of FlowNodes matching the predicate.</li>
 *   <li>{@link #visitAll(Collection, FlowNodeVisitor)}: given a {@link FlowNodeVisitor}, invoke {@link FlowNodeVisitor#visit(FlowNode)} on each node and halt when it returns false.</li>
 *   <li>Iterator: Each FlowScanner can be used as an Iterator for FlowNode-by-FlowNode walking,
 *               after you invoke {@link #setup(Collection, Collection)} to initialize it for iteration.</li>
 *   <li>{@link Filterator}: If initialized as an Iterator, each FlowScanner can provide a filtered view from the current point in time.</li>
 *   <li>Iterable: for syntactic sugar, FlowScanners implement Iterable to allow use in for-each loops once initialized.</li>
 * </ul>
 *
 * <p>All APIs visit the parent nodes, walking backward from heads(inclusive) until they they hit {@link #myBlackList} nodes (exclusive) or reach the end of the DAG.
 * If denyList nodes are an empty collection or null, APIs will walk to the beginning of the FlowGraph.
 * Multiple denyList nodes are helpful for putting separate bounds on walking different parallel branches.
 *
 * <p><strong>Key Points:</strong>
 * <ul><li>There are many helper methods offering syntactic sugar for the above APIs in common use cases (simpler method signatures).</li>
 *   <li>Each implementation provides its own iteration order (described in its javadoc comments),
 *     but it is generally unsafe to rely on parallel branches being visited in a specific order.</li>
 *   <li>Implementations may visit some or all points in the DAG, this should be called out in the class's javadoc comments</li>
 *   <li>FlowScanners are NOT thread safe, for performance reasons and because it is too hard to guarantee.</li>
 *   <li>Many fields and methods are protected: this is intentional to allow building upon the implementations for more complex analyses.</li>
 *   <li>Each FlowScanner stores state internally for several reasons:</li>
 *   <li><ul>
 *      <li>This state can be used to construct more advanced analyses.</li>
 *      <li>FlowScanners can be reinitialized and reused repeatedly: avoids the overheads of creating scanners repeatedly.</li>
 *      <li>Allows for caching to be added inside a FlowScanner if desired, but caching is only useful when reused.</li>
 *   </ul></li>
 *   </ul>
 *
 * <p><strong>Suggested uses:</strong>
 *   <ul>
 *   <li>Implement a {@link FlowNodeVisitor} that collects metrics from each FlowNode visited, and call visitAll to extract the data.</li>
 *   <li>Find all flownodes of a given type (ex: stages), using {@link #filteredNodes(Collection, Collection, Predicate)}</li>
 *   <li>Find the first node with an {@link org.jenkinsci.plugins.workflow.actions.ErrorAction} before a specific node</li>
 *   <li>Scan through all nodes *just* within a block
 *      <ul>
 *        <li>Use the {@link org.jenkinsci.plugins.workflow.graph.BlockEndNode} as the head</li>
 *        <li>Use the {@link org.jenkinsci.plugins.workflow.graph.BlockStartNode} as its denylist with {@link Collections#singleton(Object)}</li>
 *     </ul></li>
 *   </ul>
 *
 * <em>Implementations are generally NOT threadsafe and should be so annotated</em>
 * @author Sam Van Oort
 */
@NotThreadSafe
public abstract class AbstractFlowScanner implements Iterable<FlowNode>, Filterator<FlowNode> {

    protected FlowNode myCurrent;

    protected FlowNode myNext;

    protected Collection<FlowNode> myBlackList = Collections.emptySet();

    /** When checking for denylist membership, we convert to a hashset when checking more than this many elements */
    protected static final int MAX_LIST_CHECK_SIZE = 5;

    /** Helper: convert stop nodes to a collection that can efficiently be checked for membership, handling null if needed */
    @Nonnull
    protected Collection<FlowNode> convertToFastCheckable(@CheckForNull Collection<FlowNode> nodeCollection) {
        if (nodeCollection == null || nodeCollection.size()==0) {
            return Collections.emptySet();
        } else  if (nodeCollection.size() == 1) {
            return Collections.singleton(nodeCollection.iterator().next());
        } else if (nodeCollection instanceof HashSet) {
            return nodeCollection;
        }
        return nodeCollection.size() > MAX_LIST_CHECK_SIZE ? new HashSet<>(nodeCollection) : nodeCollection;
    }

    /**
     * Set up for iteration/analysis on a graph of nodes, initializing the internal state
     * Includes null-checking on arguments to allow directly calling with unchecked inputs (simplifies use).
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
        LinkedHashSet<FlowNode> filteredHeads = new LinkedHashSet<>(heads);
        filteredHeads.removeAll(fastEndNodes);

        if (filteredHeads.size() == 0) {
            return false;
        }

        reset();
        myBlackList = fastEndNodes;
        setHeads(filteredHeads);
        return true;
    }

    /**
     * Helper: version of {@link #setup(Collection, Collection)} where we don't have any nodes to denylist
     */
    public boolean setup(@CheckForNull Collection<FlowNode> heads) {
        if (heads == null) {
            return false;
        }
        return setup(heads, Collections.emptySet());
    }

    /**
     *  Helper: version of {@link #setup(Collection, Collection)} where we don't have any nodes to denylist, and have just a single head
     */
    public boolean setup(@CheckForNull FlowNode head, @CheckForNull Collection<FlowNode> blackList) {
        if (head == null) {
            return false;
        }
        return setup(Collections.singleton(head), blackList);
    }

    /**
     * Helper: version of {@link #setup(Collection, Collection)} where we don't have any nodes to denylist and have just a single head
     */
    public boolean setup(@CheckForNull FlowNode head) {
        if (head == null) {
            return false;
        }
        return setup(Collections.singleton(head), Collections.emptySet());
    }

    /** Reset internal state so that we can begin walking a new flow graph
     *  Public APIs need to invoke this before searches */
    protected abstract void reset();

    /**
     * Set up to begin flow scanning using the filteredHeads as starting points
     *
     * This method makes several assumptions:
     *
     *   - {@link #reset()} has already been invoked to reset state
     *   - filteredHeads has already had any points in {@link #myBlackList} removed
     *   - none of the filteredHeads are null
     * @param filteredHeads Head nodes that have been filtered against denyList
     */
    protected abstract void setHeads(@Nonnull Collection<FlowNode> filteredHeads);

    /**
     * Actual meat of the iteration, get the next node to visit, using and updating state as needed
     * @param current Current node to use in generating next value
     * @param blackList Nodes that are not eligible for visiting
     * @return Next node to visit, or null if we've exhausted the node list
     */
    @CheckForNull
    protected abstract FlowNode next(@Nonnull FlowNode current, @Nonnull Collection<FlowNode> blackList);

    @Override
    public boolean hasNext() {
        return myNext != null;
    }

    @Override
    public FlowNode next() {
        if (myNext == null) {
            throw new NoSuchElementException();
        }

        myCurrent = myNext;
        myNext = next(myCurrent, myBlackList);
        return myCurrent;
    }

    @Override
    public final void remove() {
        throw new UnsupportedOperationException("FlowGraphs are immutable, so FlowScanners can't remove nodes");
    }

    @Override
    @Nonnull
    public Iterator<FlowNode> iterator() {
        return this;
    }


    /**
     * Expose a filtered view of this FlowScanner's output.
     * @param filterCondition Filterator only returns {@link FlowNode}s matching this predicate.
     * @return A {@link Filterator} against this FlowScanner, which can be filtered in additional ways.
     */
    @Override
    @Nonnull
    @Deprecated
    public Filterator<FlowNode> filter(@Nonnull Predicate<FlowNode> filterCondition) {
        return new FilteratorImpl<>(this, filterCondition);
    }

    /**
     * Expose a filtered view of this FlowScanner's output.
     * @param filterCondition Filterator only returns {@link FlowNode}s matching this predicate.
     * @return A {@link Filterator} against this FlowScanner, which can be filtered in additional ways.
     */
    @Override
    @Nonnull
    @Deprecated
    public Filterator<FlowNode> filter(@Nonnull com.google.common.base.Predicate<FlowNode> filterCondition) {
        return new FilteratorImpl<>(this, filterCondition::apply);
    }


    /**
     * Find the first FlowNode within the iteration order matching a given condition
     * Includes null-checking on arguments to allow directly calling with unchecked inputs (simplifies use).
     * @param heads Head nodes to start walking from
     * @param blackListNodes Nodes that are never visited, search stops here (bound is exclusive).
     *                       If you want to create an inclusive bound, just use a node's parents.
     * @param matchCondition Predicate to match when we've successfully found a given node type
     * @return First matching node, or null if no matches found
     */
    @CheckForNull
    public FlowNode findFirstMatch(@CheckForNull Collection<FlowNode> heads,
                                   @CheckForNull Collection<FlowNode> blackListNodes,
                                   Predicate<FlowNode> matchCondition) {
        if (!setup(heads, blackListNodes)) {
            return null;
        }

        for (FlowNode f : this) {
            if (matchCondition.test(f)) {
                return f;
            }
        }
        return null;
    }

    @Deprecated
    public FlowNode findFirstMatch(@CheckForNull Collection<FlowNode> heads,
                                   @CheckForNull Collection<FlowNode> blackListNodes,
                                   com.google.common.base.Predicate<FlowNode> matchCondition) {
        return findFirstMatch(heads, blackListNodes, (Predicate<FlowNode>) matchCondition::apply);
    }

    // Polymorphic methods for syntactic sugar

    /** Syntactic sugar for {@link #findFirstMatch(Collection, Collection, Predicate)} where there is no denyList */
    @CheckForNull
    public FlowNode findFirstMatch(@CheckForNull Collection<FlowNode> heads, @Nonnull Predicate<FlowNode> matchPredicate) {
        return this.findFirstMatch(heads, null, matchPredicate);
    }

    @CheckForNull
    @Deprecated
    public FlowNode findFirstMatch(@CheckForNull Collection<FlowNode> heads, @Nonnull com.google.common.base.Predicate<FlowNode> matchPredicate) {
        return this.findFirstMatch(heads, (Predicate<FlowNode>) matchPredicate::apply);
    }

    /** Syntactic sugar for {@link #findFirstMatch(Collection, Collection, Predicate)} where there is a single head and no denyList */
    @CheckForNull
    public FlowNode findFirstMatch(@CheckForNull FlowNode head, @Nonnull Predicate<FlowNode> matchPredicate) {
        return this.findFirstMatch(Collections.singleton(head), null, matchPredicate);
    }

    @Deprecated
    public FlowNode findFirstMatch(@CheckForNull FlowNode head, @Nonnull com.google.common.base.Predicate<FlowNode> matchPredicate) {
        return findFirstMatch(head, (Predicate<FlowNode>) matchPredicate::apply);
    }

    /** Syntactic sugar for {@link #findFirstMatch(Collection, Collection, Predicate)} using {@link FlowExecution#getCurrentHeads()}  to get heads and no denyList */
    @CheckForNull
    public FlowNode findFirstMatch(@CheckForNull FlowExecution exec, @Nonnull Predicate<FlowNode> matchPredicate) {
        if (exec != null && exec.getCurrentHeads() != null && !exec.getCurrentHeads().isEmpty()) {
            return this.findFirstMatch(exec.getCurrentHeads(), null, matchPredicate);
        }
        return null;
    }

    @Deprecated
    public FlowNode findFirstMatch(@CheckForNull FlowExecution exec, @Nonnull com.google.common.base.Predicate<FlowNode> matchPredicate) {
        return findFirstMatch(exec, (Predicate<FlowNode>) matchPredicate::apply);
    }

    /**
     * Return a filtered list of {@link FlowNode}s matching a condition, in the order encountered.
     * Includes null-checking on arguments to allow directly calling with unchecked inputs (simplifies use).
     * @param heads Nodes to start iterating backward from by visiting their parents.
     * @param blackList Nodes we may not visit or walk beyond.
     * @param matchCondition Predicate that must be met for nodes to be included in output.  Input is always non-null.
     * @return List of flownodes matching the predicate.
     */
    @Nonnull
    public List<FlowNode> filteredNodes(@CheckForNull Collection<FlowNode> heads,
                                        @CheckForNull Collection<FlowNode> blackList,
                                        Predicate<FlowNode> matchCondition) {
        if (!setup(heads, blackList)) {
            return Collections.emptyList();
        }

        ArrayList<FlowNode> nodes = new ArrayList<>();
        for (FlowNode f : this) {
            if (matchCondition.test(f)) {
                nodes.add(f);
            }
        }
        return nodes;
    }

    @Deprecated
    public List<FlowNode> filteredNodes(@CheckForNull Collection<FlowNode> heads,
                                        @CheckForNull Collection<FlowNode> blackList,
                                        com.google.common.base.Predicate<FlowNode> matchCondition) {
        return filteredNodes(heads, blackList, (Predicate<FlowNode>) matchCondition::apply);
    }

    /** Convenience method to get the list all flownodes in the iterator order. */
    @Nonnull
    public List<FlowNode> allNodes(@CheckForNull Collection<FlowNode> heads) {
        if (!setup(heads)) {
            return Collections.emptyList();
        }
        List<FlowNode> nodes = new ArrayList<>();
        for (FlowNode f : this) {
            nodes.add(f);
        }
        return nodes;
    }

    /** Convenience method to get the list of all {@link FlowNode}s for the execution, in iterator order. */
    @Nonnull
    public List<FlowNode> allNodes(@CheckForNull FlowExecution exec) {
        return (exec == null) ? Collections.emptyList() : allNodes(exec.getCurrentHeads());
    }

    /** Syntactic sugar for {@link #filteredNodes(Collection, Collection, Predicate)} with no denyList nodes */
    @Nonnull
    public List<FlowNode> filteredNodes(@CheckForNull Collection<FlowNode> heads, @Nonnull Predicate<FlowNode> matchPredicate) {
        return this.filteredNodes(heads, null, matchPredicate);
    }

    @Deprecated
    public List<FlowNode> filteredNodes(@CheckForNull Collection<FlowNode> heads, @Nonnull com.google.common.base.Predicate<FlowNode> matchPredicate) {
        return this.filteredNodes(heads, (Predicate<FlowNode>) matchPredicate::apply);
    }

    /** Syntactic sugar for {@link #filteredNodes(Collection, Collection, Predicate)} with a single head and no denyList nodes */
    @Nonnull
    public List<FlowNode> filteredNodes(@CheckForNull FlowNode head, @Nonnull Predicate<FlowNode> matchPredicate) {
        return this.filteredNodes(Collections.singleton(head), null, matchPredicate);
    }

    @Deprecated
    public List<FlowNode> filteredNodes(@CheckForNull FlowNode head, @Nonnull com.google.common.base.Predicate<FlowNode> matchPredicate) {
        return this.filteredNodes(head, (Predicate<FlowNode>) matchPredicate::apply);
    }

    @Nonnull
    public List<FlowNode> filteredNodes(@CheckForNull FlowExecution exec, @Nonnull Predicate<FlowNode> matchPredicate) {
        if (exec == null) {
            return Collections.emptyList();
        }
        return this.filteredNodes(exec.getCurrentHeads(), null, matchPredicate);
    }

    @Deprecated
    public List<FlowNode> filteredNodes(@CheckForNull FlowExecution exec, @Nonnull com.google.common.base.Predicate<FlowNode> matchPredicate) {
        if (exec == null) {
            return Collections.emptyList();
        }
        return this.filteredNodes(exec, (Predicate<FlowNode>) matchPredicate::apply);
    }

    /**
     * Given a {@link FlowNodeVisitor}, invoke {@link FlowNodeVisitor#visit(FlowNode)} on each node and halt early if it returns false.
     * Includes null-checking on all but the visitor, to allow directly calling with unchecked inputs (simplifies use).
     *
     * Useful if you wish to collect some information from every node in the FlowGraph.
     * To do that, accumulate internal state in the visitor, and invoke a getter when complete.
     * @param heads Nodes to start walking the DAG backwards from.
     * @param blackList Nodes we can't visit or pass beyond.
     * @param visitor Visitor that will see each FlowNode encountered.
     */
    public void visitAll(@CheckForNull Collection<FlowNode> heads, @CheckForNull Collection<FlowNode> blackList, @Nonnull FlowNodeVisitor visitor) {
        if (!setup(heads, blackList)) {
            return;
        }
        for (FlowNode f : this) {
            boolean canContinue = visitor.visit(f);
            if (!canContinue) {
                break;
            }
        }
    }

    /** Syntactic sugar for {@link #visitAll(Collection, Collection, FlowNodeVisitor)} where we don't denylist any nodes */
    public void visitAll(@CheckForNull Collection<FlowNode> heads, @Nonnull FlowNodeVisitor visitor) {
        visitAll(heads, null, visitor);
    }
}

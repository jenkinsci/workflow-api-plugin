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
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Base class for flow scanners, which offers basic methods and stubs for algorithms
 * Scanners store state internally, and are not thread-safe but are reusable
 * Scans/analysis of graphs is implemented via internal iteration to allow reusing algorithm bodies
 * However internal iteration has access to additional information
 *
 * @author <samvanoort@gmail.com>Sam Van Oort</samvanoort@gmail.com>
 */
public abstract class AbstractFlowScanner implements Iterable <FlowNode>, Filterator<FlowNode> {

    // State variables, not all need be used
    protected ArrayDeque<FlowNode> _queue;

    protected FlowNode _current;

    protected FlowNode _next;

    protected Collection<FlowNode> _blackList = Collections.EMPTY_SET;

    /** Convert stop nodes to a collection that can efficiently be checked for membership, handling null if needed */
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
     * @param current Current node to use in generating next value
     * @param blackList Nodes that are not eligible for visiting
     * @return Next node to visit, or null if we've exhausted the node list
     */
    @CheckForNull
    protected abstract FlowNode next(@Nonnull FlowNode current, @Nonnull Collection<FlowNode> blackList);

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
        _next = next(_current, _blackList);
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

    public Filterator<FlowNode> filter(Predicate<FlowNode> filterCondition) {
        return new FilteratorImpl<FlowNode>(this, filterCondition);
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

    @Nonnull
    public List<FlowNode> filteredNodes(@CheckForNull Collection<FlowNode> heads, @Nonnull Predicate<FlowNode> matchPredicate) {
        return this.filteredNodes(heads, null, matchPredicate);
    }

    @Nonnull
    public List<FlowNode> filteredNodes(@CheckForNull FlowNode head, @Nonnull Predicate<FlowNode> matchPredicate) {
        return this.filteredNodes(Collections.singleton(head), null, matchPredicate);
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

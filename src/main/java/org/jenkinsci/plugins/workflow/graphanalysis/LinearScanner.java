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

import org.jenkinsci.plugins.workflow.graph.FlowNode;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.NotThreadSafe;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;

/**
 * Scans through the flow graph in strictly linear fashion, visiting only the first branch in parallel blocks.
 *
 * <p>Iteration order: depth-ONLY, meaning we walk through parents and only follow the first parent of each {@link FlowNode}
 * This means that where are parallel branches, we will only visit a partial set of {@link FlowNode}s in the directed acyclic graph.
 *
 * <p>Use case: we don't care about parallel branches or know they don't exist, we just want to walk through the top-level blocks.
 *
 * <p>This is the fastest and simplest way to walk a flow, because you only care about a single node at a time.
 * Nuance: where there are multiple parent nodes (in a parallel block), and one is denylisted, we'll find the first non-denylisted one.
 * @author Sam Van Oort
 */
@NotThreadSafe
public class LinearScanner extends AbstractFlowScanner {

    private static final Logger LOGGER = Logger.getLogger(LinearScanner.class.getName());

    @Override
    protected void reset() {
        this.myCurrent = null;
        this.myNext = null;
        this.myBlackList = Collections.emptySet();
    }

    /**
     * {@inheritDoc}
     * @param heads Head nodes that have been filtered against denyList. <strong>Do not pass multiple heads.</strong>
     */
    @Override
    protected void setHeads(@Nonnull Collection<FlowNode> heads) {
        Iterator<FlowNode> it = heads.iterator();
        if (it.hasNext()) {
            this.myCurrent = it.next();
            this.myNext = this.myCurrent;
            if (it.hasNext()) {
                LOGGER.log(Level.WARNING, null, new IllegalArgumentException("Multiple heads not supported for linear scanners"));
            }
        }
    }

    @Override
    protected FlowNode next(FlowNode current, @Nonnull Collection<FlowNode> blackList) {
        if (current == null) {
            return null;
        }
        List<FlowNode> parents = current.getParents();
        if (parents != null && parents.size() > 0) {
            for (FlowNode f : parents) {
                if (!blackList.contains(f)) {
                    return f;
                }
            }
        }
        return null;
    }

    /**
     * {@inheritDoc}
     * @deprecated prefer {@link #filteredNodes(FlowNode, Predicate)}
     */
    @Deprecated
    @Override
    public List<FlowNode> filteredNodes(Collection<FlowNode> heads, Predicate<FlowNode> matchPredicate) {
        return super.filteredNodes(heads, matchPredicate);
    }

    // TODO not deprecated since there is no apparent direct replacement yet

    /**
     * {@inheritDoc}
     * @param heads Nodes to start iterating backward from by visiting their parents. <strong>Do not pass multiple heads.</strong>
     */
    @Override
    public List<FlowNode> filteredNodes(Collection<FlowNode> heads, Collection<FlowNode> blackList, Predicate<FlowNode> matchCondition) {
        return super.filteredNodes(heads, blackList, matchCondition);
    }

    /**
     * {@inheritDoc}
     * @deprecated prefer {@link #findFirstMatch(FlowNode, Predicate)}
     */
    @Deprecated
    @Override
    public FlowNode findFirstMatch(Collection<FlowNode> heads, Predicate<FlowNode> matchPredicate) {
        return super.findFirstMatch(heads, matchPredicate);
    }

    // TODO not deprecated since there is no apparent direct replacement yet

    /**
     * {@inheritDoc}
     * @param heads Head nodes to start walking from. <strong>Do not pass multiple heads.</strong>
     */
    @Override
    public FlowNode findFirstMatch(Collection<FlowNode> heads, Collection<FlowNode> blackListNodes, Predicate<FlowNode> matchCondition) {
        return super.findFirstMatch(heads, blackListNodes, matchCondition);
    }

    // TODO not deprecated since there is no apparent direct replacement yet

    /**
     * {@inheritDoc}
     * @param heads <strong>Do not pass multiple heads.</strong>
     */
    @Override
    public void visitAll(Collection<FlowNode> heads, FlowNodeVisitor visitor) {
        super.visitAll(heads, visitor);
    }

    // TODO not deprecated since there is no apparent direct replacement yet

    /**
     * {@inheritDoc}
     * @param heads Nodes to start walking the DAG backwards from. <strong>Do not pass multiple heads.</strong>
     */
    @Override
    public void visitAll(Collection<FlowNode> heads, Collection<FlowNode> blackList, FlowNodeVisitor visitor) {
        super.visitAll(heads, blackList, visitor);
    }

    /**
     * {@inheritDoc}
     * @deprecated unsafe to call
     */
    @Deprecated
    @Override
    public FlowNode findFirstMatch(FlowExecution exec, Predicate<FlowNode> matchPredicate) {
        return super.findFirstMatch(exec, matchPredicate);
    }

    /**
     * {@inheritDoc}
     * @deprecated prefer {@link #setup(FlowNode)}
     */
    @Deprecated
    @Override
    public boolean setup(Collection<FlowNode> heads) {
        return super.setup(heads);
    }

    /**
     * {@inheritDoc}
     * @deprecated prefer {@link #setup(FlowNode, Collection)}
     */
    @Deprecated
    @Override
    public boolean setup(Collection<FlowNode> heads, Collection<FlowNode> blackList) {
        return super.setup(heads, blackList);
    }

}

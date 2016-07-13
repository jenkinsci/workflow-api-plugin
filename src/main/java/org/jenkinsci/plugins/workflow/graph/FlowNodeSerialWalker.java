/*
 * The MIT License
 *
 * Copyright 2016 CloudBees, Inc.
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

package org.jenkinsci.plugins.workflow.graph;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import javax.annotation.CheckForNull;
import org.jenkinsci.plugins.workflow.actions.LabelAction;
import org.jenkinsci.plugins.workflow.actions.StageAction;

/**
 * Unlike {@link FlowGraphWalker}, this iterator does not traverse the entire graph, but only walks backward and outward from one node.
 * If the start node is inside some nested blocks, it will step up through the {@link BlockStartNode}s.
 * But when moving backwards in time, when encountering blocks that the starting node was not inside,
 * it will visit the {@link BlockStartNode} without visiting any of the internal nodes or {@link BlockEndNode}.
 * The {@link FlowStartNode} will not be visited.
 */
public final class FlowNodeSerialWalker implements Iterable<FlowNode> {

    private final FlowNode start;

    /**
     * Initializes the walker.
     * @param start the node which will be the first to be returned in the walk (unless it was a {@link BlockEndNode}, in which case the corresponding {@link BlockStartNode} will be first)
     */
    public FlowNodeSerialWalker(FlowNode start) {
        this.start = start instanceof BlockEndNode ? ((BlockEndNode) start).getStartNode() : start;
    }

    @Override public EnhancedIterator iterator() {
        return new EnhancedIterator();
    }
    
    /**
     * Special iterator offering additional functionality.
     */
    public final class EnhancedIterator implements Iterator<FlowNode> {

        private FlowNode next;
        private FlowNode curr;
        boolean ancestor;

        EnhancedIterator() {
            next = start;
        }

        @Override public boolean hasNext() {
            return !next.getParents().isEmpty();
        }

        @Override public FlowNode next() {
            if (next instanceof BlockEndNode) {
                next = ((BlockEndNode) next).getStartNode();
                ancestor = false;
            } else {
                ancestor = next == start || next instanceof BlockStartNode;
            }
            curr = next;
            List<FlowNode> parents = next.getParents();
            if (parents.size() != 1) {
                throw new NoSuchElementException("unexpected " + next + " with parents " + parents);
            }
            next = parents.get(0);
            return curr;
        }
        
        @Override public void remove() {
            throw new UnsupportedOperationException();
        }

        /**
         * Checks whether the current node is an ancestor of the start node.
         * May only be called after {@link #next}.
         * @return true if the current node is the start node, or is a {@link BlockStartNode} which enclosed the start node
         */
        public boolean isAncestor() {
            return ancestor;
        }

        /**
         * Checks if there is a display label shown on the current node which should apply to the start node.
         * Applicable labels include {@link LabelAction#getDisplayName} if {@link #isAncestor}, or {@link StageAction#getStageName} if encountered anywhere.
         * May only be called after {@link #next}.
         * @return a label, or null if inapplicable
         */
        public @CheckForNull String currentLabel() {
            if (ancestor) {
                LabelAction a = curr.getAction(LabelAction.class);
                if (a != null) {
                    return a.getDisplayName();
                }
            }
            StageAction a = curr.getAction(StageAction.class);
            if (a != null) {
                return a.getStageName();
            }
            return null;
        }

    }

}

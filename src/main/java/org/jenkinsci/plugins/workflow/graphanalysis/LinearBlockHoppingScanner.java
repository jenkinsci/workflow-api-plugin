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
import javax.annotation.concurrent.NotThreadSafe;
import java.util.Collection;
import java.util.List;

/**
 * Extension of {@link LinearScanner} that skips nested blocks at the current level, useful for finding enclosing blocks.
 *  <strong>ONLY use this with nodes inside the flow graph</strong>, never the last node of a completed flow (it will jump over the whole flow).
 *
 * <p/>This is useful where you only care about {@link FlowNode}s that precede this one or are part of an enclosing scope (within a Block).
 *
 * <p></p>Specifically:
 *  <ul>
 *    <li>Where a {@link BlockEndNode} is encountered, the scanner will jump to the {@link BlockStartNode} and go to its first parent.</li>
 *    <li>The only case where you visit branches of a parallel block is if you begin inside it.</li>
 *  </ul>
 *
 * <p/>Specific use cases:
 * <ul>
 *   <li>Finding out the executor workspace used to run a FlowNode</li>
 *   <li>Finding the start of the parallel block enclosing the current node</li>
 *   <li>Locating the label applying to a given FlowNode (if any) if using labelled blocks</li>
 * </ul>
 *
 * @author <samvanoort@gmail.com>Sam Van Oort</samvanoort@gmail.com>
 */
@NotThreadSafe
public class LinearBlockHoppingScanner extends LinearScanner {

    @Override
    public boolean setup(@CheckForNull Collection<FlowNode> heads, @CheckForNull Collection<FlowNode> blackList) {
        boolean possiblyStartable = super.setup(heads, blackList);
        return possiblyStartable && myCurrent != null;  // In case we start at an end block
    }

    @Override
    protected void setHeads(@Nonnull Collection<FlowNode> heads) {
        if (heads.size() > 0) {
            this.myCurrent = jumpBlockScan(heads.iterator().next(), myBlackList);
            this.myNext = this.myCurrent;
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
    protected FlowNode next(@Nonnull FlowNode current, @Nonnull Collection<FlowNode> blackList) {
        if (current == null) {
            return null;
        }
        List<FlowNode> parents = current.getParents();
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

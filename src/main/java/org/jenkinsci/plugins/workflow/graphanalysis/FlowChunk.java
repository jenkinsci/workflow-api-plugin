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

/**
 * <p>Common container interface for a series of {@link FlowNode}s with a logical start and end.</p>
 * <p>We use this because every plugin has a different way of storing info about the nodes.</p>
 *
 * <p> Common uses:</p>
 * <ul>
 *     <li>A single FlowNode (when coupling with timing/status APIs)</li>
 *     <li>A block (with a {@link org.jenkinsci.plugins.workflow.graph.BlockStartNode} and {@link org.jenkinsci.plugins.workflow.graph.BlockEndNode})</li>
 *     <li>A linear run of marked nodes (such as a legacy stage)</li>
 *     <li>A parallel block (special case of block)</li>
 *     <li>A parallel branch within a parallel block</li>
 *     <li>A mix of types in sequence, such as nested structures</li>
 * </ul>
 *
 * @author Sam Van Oort
 */
public interface FlowChunk {
    @Nonnull
    FlowNode getFirstNode();

    @Nonnull
    FlowNode getLastNode();
}

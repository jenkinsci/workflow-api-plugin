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
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Scans through a single ancestry, does not cover parallel branches
 * Use case: we don't care about parallel branches
 * @author <samvanoort@gmail.com>Sam Van Oort</samvanoort@gmail.com>
 */
public class LinearScanner extends AbstractFlowScanner {

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
}

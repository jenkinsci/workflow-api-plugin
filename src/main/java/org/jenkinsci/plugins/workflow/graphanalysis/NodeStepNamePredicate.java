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
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graph.StepNode;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Predicate that matches {@link FlowNode}s (specifically {@link StepNode}s) with a specific {@link StepDescriptor} name.
 *  May be used in preference to {@link NodeStepTypePredicate} in cases whern dependencie structures prevent import
 *   of the actual descriptor class (I.E. when writing code in the workflow-api plugin that looks for step types in workflow-cps).
 */
public final class NodeStepNamePredicate implements Predicate<FlowNode> {
    String descriptorId;

    public NodeStepNamePredicate(@Nonnull String descriptorId) {
        this.descriptorId = descriptorId;
    }

    public String getDescriptorId() {
        return descriptorId;
    }

    @Override
    public boolean apply(@Nullable FlowNode input) {
        if (input != null && input instanceof StepNode) {
            StepDescriptor sd = ((StepNode) input).getDescriptor();
            return (sd != null) ? descriptorId.equals(sd.getId()) : false;
        }
        return false;
    }
}

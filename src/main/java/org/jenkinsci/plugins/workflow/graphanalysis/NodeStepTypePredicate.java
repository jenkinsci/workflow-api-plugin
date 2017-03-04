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
import org.jenkinsci.plugins.workflow.graph.FlowEndNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graph.FlowStartNode;
import org.jenkinsci.plugins.workflow.graph.StepNode;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Predicate that matches {@link FlowNode}s (specifically {@link StepNode}s) with a specific {@link StepDescriptor} type. */
public final class NodeStepTypePredicate implements Predicate<FlowNode> {
    StepDescriptor stepDescriptor;

    public NodeStepTypePredicate(@Nonnull StepDescriptor descriptorType) {
        stepDescriptor = descriptorType;
    }

    /** Create a filter predicate based on the step name */
    public NodeStepTypePredicate(@Nonnull String functionName) {
        stepDescriptor = StepDescriptor.byFunctionName(functionName);
    }

    public StepDescriptor getStepDescriptor(){
        return stepDescriptor;
    }

    @Override
    public boolean apply(@Nullable FlowNode input) {
        if (input instanceof StepNode) {
            return ((StepNode) input).getDescriptor() == stepDescriptor;
        } else if (input != null && !(input instanceof FlowStartNode || input instanceof FlowEndNode)) {
            try {
                // Workaround for cases where someone is using the latest workflow-api which has StepNode
                //  but manages to skip the post-2.26 versions of workflow-cps where it a parent of workflow-cps StepNode.
                // Technically consumers *were* supposed to call ForkScanner#setParallelStartPredicate, but better to be foolproof.
                Method getDescriptorMethod = input.getClass().getDeclaredMethod("getDescriptor", null);
                StepDescriptor sd = (StepDescriptor)(getDescriptorMethod.invoke(input, null));
                return  (sd == stepDescriptor);
            } catch (NoSuchMethodException | SecurityException | IllegalAccessException | InvocationTargetException e) {
                return false; // Not a step node
            }
        }
        return false;
    }
}

package org.jenkinsci.plugins.workflow.graphanalysis;
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

import org.jenkinsci.plugins.workflow.cps.nodes.StepAtomNode;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.junit.Assert;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Predicate;

/**
 * Utilities for testing flow scanning
 * @author Sam Van Oort
 */
public class FlowTestUtils {
    public static Predicate<FlowNode> predicateMatchStepDescriptor( @Nonnull final String descriptorId) {
        return input -> {
            if (input instanceof StepAtomNode) {
                StepAtomNode san = (StepAtomNode)input;
                StepDescriptor sd = san.getDescriptor();
                return sd != null && descriptorId.equals(sd.getId());
            }
            return false;
        };
    }

    public static final class CollectingVisitor implements FlowNodeVisitor {
        ArrayList<FlowNode> visited = new ArrayList<>();

        @Override
        public boolean visit(@Nonnull FlowNode f) {
            visited.add(f);
            return true;
        }

        public void reset() {
            this.visited.clear();
        }

        public ArrayList<FlowNode> getVisited() {
            return visited;
        }
    }

    public static Predicate<FlowNode> MATCH_ECHO_STEP = FlowTestUtils.predicateMatchStepDescriptor("org.jenkinsci.plugins.workflow.steps.EchoStep");

    /** Assert node ordering using their ids */
    public static void assertNodeOrder(String description, Iterable<FlowNode> nodes, String... nodeIds) {
        ArrayList<String> realIds = new ArrayList<>();
        for (FlowNode f: nodes) {
            Assert.assertNotNull(f);
            realIds.add(f.getId());
        }
        Assert.assertArrayEquals(description, nodeIds, realIds.toArray());
    }

    /** Assert node ordering using iotas for their ids */
    public static void assertNodeOrder(String description, Iterable<FlowNode> nodes, int... nodeIds) {
        String[] nodeIdStrings = new String[nodeIds.length];
        for (int i=0; i<nodeIdStrings.length; i++) {
            nodeIdStrings[i] = Integer.toString(nodeIds[i]);
        }
        assertNodeOrder(description, nodes, nodeIdStrings);
    }


    /** Syntactic sugar to add a large batch of nodes */
    public static void addNodesById(Collection<FlowNode> coll, FlowExecution exec, int... iotas) {
        try {
            for (int nodeId : iotas) {
                coll.add(exec.getNode(Integer.toString(nodeId)));
            }
        } catch (IOException ioe) {
            throw new IllegalStateException("Failed to load node by id", ioe);
        }

    }
}

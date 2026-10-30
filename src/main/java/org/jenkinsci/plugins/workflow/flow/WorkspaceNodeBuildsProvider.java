/*
 * The MIT License
 *
 * Copyright (c) 2026, Tim Jacomb
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

package org.jenkinsci.plugins.workflow.flow;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Job;
import hudson.model.Node;
import hudson.model.Run;
import hudson.util.RunList;
import jenkins.model.Jenkins;
import jenkins.model.NodeBuildsProvider;
import org.jenkinsci.plugins.workflow.actions.WorkspaceAction;
import org.jenkinsci.plugins.workflow.graphanalysis.DepthFirstScanner;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Lists the flow runs that used a node, as recorded by a {@link WorkspaceAction} on one of their flow nodes.
 */
@Restricted(NoExternalUse.class)
@Extension
public class WorkspaceNodeBuildsProvider extends NodeBuildsProvider {

    @NonNull
    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Iterable<? extends Run<?, ?>> getCandidates(@NonNull Node node) {
        // core lists the runs of AbstractProjects
        return RunList.fromJobs((Iterable) Jenkins.get().allItems(Job.class, job -> !(job instanceof AbstractProject)));
    }

    @Override
    public boolean usedNode(@NonNull Run<?, ?> run, @NonNull Node node) {
        if (!(run instanceof FlowExecutionOwner.Executable)) {
            return false;
        }
        FlowExecutionOwner owner = ((FlowExecutionOwner.Executable) run).asFlowExecutionOwner();
        if (owner == null) {
            return false;
        }
        FlowExecution execution = owner.getOrNull();
        if (execution == null) {
            return false;
        }
        String nodeName = node.getNodeName();
        return new DepthFirstScanner().findFirstMatch(execution, flowNode -> {
            WorkspaceAction action = flowNode.getPersistentAction(WorkspaceAction.class);
            return action != null && action.getNode().equals(nodeName);
        }) != null;
    }
}

/*
 * The MIT License
 *
 * Copyright (c) 2013-2014, CloudBees, Inc.
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

import com.google.common.util.concurrent.ListenableFuture;
import hudson.Util;
import hudson.model.Executor;
import jenkins.model.CauseOfInterruption;
import org.jenkinsci.plugins.workflow.actions.ErrorAction;
import org.jenkinsci.plugins.workflow.graph.BlockEndNode;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowActionStorage;
import org.jenkinsci.plugins.workflow.graph.FlowEndNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graph.GraphLookupView;
import org.jenkinsci.plugins.workflow.graph.StandardGraphLookupView;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import hudson.model.Result;
import hudson.security.ACL;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

import java.io.IOException;
import java.util.List;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.model.Jenkins;
import jenkins.model.queue.AsynchronousExecution;
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.springframework.security.core.Authentication;

/**
 * State of a currently executing workflow.
 *
 * <p>
 * This "interface" abstracts away workflow definition language, syntax, or its
 * execution model, but it allows other code to listen on what's going on.
 *
 * <h2>Persistence</h2>
 * <p>
 * {@link FlowExecution} must support persistence by XStream, which should
 * capture the state of execution at one point. The expectation is that when
 * the object gets deserialized, it'll start re-executing from that point.
 *
 *
 * @author Kohsuke Kawaguchi
 * @author Jesse Glick
 */
public abstract class FlowExecution implements FlowActionStorage, GraphLookupView {  // Implements GraphLookupView because FlowNode lives in another package

    protected transient GraphLookupView internalGraphLookup = null;

    /** CheckForNull due to loading pre-durability runs. */
    @CheckForNull
    protected FlowDurabilityHint durabilityHint = null;

    /** Eventually this may be overridden if the FlowExecution has a better source of structural information, such as the {@link FlowNode} storage. */
    protected synchronized GraphLookupView getInternalGraphLookup() {
        if (internalGraphLookup == null) {
            StandardGraphLookupView lookupView = new StandardGraphLookupView();
            this.internalGraphLookup = lookupView;
            this.addListener(lookupView);
        }
        return internalGraphLookup;
    }

    /**
     * Get the durability level we're aiming for, or a default value if none is set (defaults may change as implementation evolve).
     * @return Durability level we are aiming for with this execution.
     */
    @NonNull
    public FlowDurabilityHint getDurabilityHint() {
        // MAX_SURVIVABILITY is the behavior of builds before the change was introduced.
        return (durabilityHint != null) ? durabilityHint : FlowDurabilityHint.MAX_SURVIVABILITY;
    }

    /**
     * Called after {@link FlowDefinition#create(FlowExecutionOwner, List)} to
     * initiate the execution. This method is not invoked on rehydrated execution.
     *
     * This separation ensures that {@link FlowExecutionOwner} is functional when this method
     * is called.
     */
    public abstract void start() throws IOException;

    /**
     * Should be called by the flow owner after it is deserialized.
     */
    public /*abstract*/ void onLoad(FlowExecutionOwner owner) throws IOException {
        if (Util.isOverridden(FlowExecution.class, getClass(), "onLoad")) {
            onLoad();
        }
    }

    @Deprecated
    public void onLoad() {
        throw new AbstractMethodError("you must implement the new overload of onLoad");
    }

    public abstract FlowExecutionOwner getOwner();

    /**
     * In the current flow graph, return all the "head" nodes where the graph is still growing.
     *
     * If you think of a flow graph as a git repository, these heads correspond to branches.
     */
    // TODO: values are snapshot in time
    public abstract List<FlowNode> getCurrentHeads();

    /**
     * Yields the {@link StepExecution}s that are currently executing.
     *
     * <p>{@link StepExecution}s are persisted as a part of the program state, so its lifecycle
     * is independent of {@link FlowExecution}, hence the asynchrony.
     *
     * <p>Think of this as program counters of all the virtual threads.
     *
     * <p>The implementation should return results in the order that steps were started, insofar as that makes sense.
     * @param innerMostOnly if true, only return the innermost steps; if false, include any block-scoped steps running around them
     */
    public /*abstract*/ ListenableFuture<List<StepExecution>> getCurrentExecutions(boolean innerMostOnly) {
        if (Util.isOverridden(FlowExecution.class, getClass(), "getCurrentExecutions") && innerMostOnly) {
            return getCurrentExecutions();
        } else {
            throw new AbstractMethodError("you must implement the new overload of getCurrentExecutions");
        }
    }

    @Deprecated
    public ListenableFuture<List<StepExecution>> getCurrentExecutions() {
        throw new AbstractMethodError("you must implement the new overload of getCurrentExecutions");
    }

    // TODO: there should be navigation between FlowNode <-> StepExecution

    /**
     * Short for {@code getCurrentHeads().contains(n)} but more efficient.
     */
    public abstract boolean isCurrentHead(FlowNode n);

    /**
     * Returns the URL of this {@link FlowExecution}, relative to the context root of Jenkins.
     *
     * @return
     *      String like "job/foo/32/execution/" with trailing slash but no leading slash.
     */
    public String getUrl() throws IOException {
        return getOwner().getUrlOfExecution();
    }

    /**
     * Interrupts the execution of a flow.
     *
     * If any computation is going on synchronously, it will be interrupted/killed/etc.
     * If it's in a suspended state waiting to be resurrected (such as waiting for
     * {@link StepContext#onSuccess(Object)}), then it just marks the workflow as done
     * with the specified status.
     *
     * <p>
     * If it's evaluating bodies (see {@link StepContext#newBodyInvoker()},
     * then it's callback needs to be invoked.
     * <p>
     * Do not use this from a step. Throw {@link FlowInterruptedException} or some other exception instead.
     *
     * @see StepExecution#stop(Throwable)
     * @see Executor#interrupt(Result)
     */
    public abstract void interrupt(Result r, CauseOfInterruption... causes) throws IOException, InterruptedException;

    /**
     * Add a listener to changes in the flow graph structure.
     * @param listener a listener to add
     */
    public abstract void addListener(GraphListener listener);

    /**
     * Reverse of {@link #addListener}.
     */
    public /*abstract*/ void removeListener(GraphListener listener) {}

    /**
     * Checks whether this flow execution has finished executing completely.
     */
    public boolean isComplete() {
        List<FlowNode> heads = getCurrentHeads();
        return heads.size()==1 && heads.get(0) instanceof FlowEndNode;
    }

    /**
     * Determines whether the activity currently being run should block a Jenkins restart.
     * @return by default, true
     * @see AsynchronousExecution#blocksRestart
     */
    public boolean blocksRestart() {
        return true;
    }

    /**
     * If this execution {@linkplain #isComplete() has completed} with an error,
     * report that.
     *
     * This is a convenience method to look up the error result from {@link FlowEndNode}.
     */
    public final @CheckForNull Throwable getCauseOfFailure() {
        List<FlowNode> heads = getCurrentHeads();
        if (heads.size()!=1 || !(heads.get(0) instanceof FlowEndNode))
            return null;

        FlowNode e = heads.get(0);
        ErrorAction error = e.getPersistentAction(ErrorAction.class);
        if (error==null)    return null;        // successful completion

        return error.getError();
    }

    /**
     * Loads a node by its ID.
     * Also gives each {@link FlowNode} a portion of the URL space.
     *
     * @see FlowNode#getId()
     */
    public abstract @CheckForNull FlowNode getNode(String id) throws IOException;

    /**
     * Looks up authentication associated with this flow execution.
     * For example, if a flow is configured to be a trusted agent of a user, that would be set here.
     * A flow run triggered by a user manually might be associated with the runtime, or it might not.
     * @return an authentication; {@link ACL#SYSTEM2} as a fallback, or {@link Jenkins#ANONYMOUS2} if the flow is supposed to be limited to a specific user but that user cannot now be looked up
     */
    public /* abstract */ @NonNull Authentication getAuthentication2() {
        return Util.ifOverridden(
                () -> getAuthentication().toSpring(),
                FlowExecution.class,
                getClass(),
                "getAuthentication");
    }

    /**
     * @deprecated use {@link #getAuthentication2()}
     */
    @Deprecated
    public /* abstract */ @NonNull org.acegisecurity.Authentication getAuthentication() {
        return Util.ifOverridden(
                () -> org.acegisecurity.Authentication.fromSpring(getAuthentication2()),
                FlowExecution.class,
                getClass(),
                "getAuthentication2");
    }

    /** @see GraphLookupView#isActive(FlowNode)
     * @throws IllegalArgumentException If the input {@link FlowNode} does not belong to this execution
     */
    @Override
    @Restricted(NoExternalUse.class)  // Only public because graph, flow, and graphanalysis are separate packages
    public boolean isActive(@NonNull  FlowNode node) {
        if (!this.equals(node.getExecution())) {
            throw new IllegalArgumentException("Can't look up info for a FlowNode that doesn't belong to this execution!");
        }
        return getInternalGraphLookup().isActive(node);
    }

    /** @see GraphLookupView#getEndNode(BlockStartNode)
     *  @throws IllegalArgumentException If the input {@link FlowNode} does not belong to this execution
     */
    @CheckForNull
    @Override
    @Restricted(NoExternalUse.class)  // Only public because graph, flow, and graphanalysis are separate packages
    public BlockEndNode getEndNode(@NonNull BlockStartNode startNode) {
        if (!this.equals(startNode.getExecution())) {
            throw new IllegalArgumentException("Can't look up info for a FlowNode that doesn't belong to this execution!");
        }
        return getInternalGraphLookup().getEndNode(startNode);
    }

    /** @see GraphLookupView#findEnclosingBlockStart(FlowNode)
     * @throws IllegalArgumentException If the input {@link FlowNode} does not belong to this execution
     */
    @CheckForNull
    @Override
    @Restricted(NoExternalUse.class)  // Only public because graph, flow, and graphanalysis are separate packages
    public BlockStartNode findEnclosingBlockStart(@NonNull FlowNode node) {
        if (!this.equals(node.getExecution())) {
            throw new IllegalArgumentException("Can't look up info for a FlowNode that doesn't belong to this execution!");
        }
        return getInternalGraphLookup().findEnclosingBlockStart(node);
    }

    /** @see GraphLookupView#findAllEnclosingBlockStarts(FlowNode)
     * @throws IllegalArgumentException If the input {@link FlowNode} does not belong to this execution
     */
    @NonNull
    @Override
    @Restricted(NoExternalUse.class)  // Only public because graph, flow, and graphanalysis are separate packages
    public List<BlockStartNode> findAllEnclosingBlockStarts(@NonNull FlowNode node) {
        if (!this.equals(node.getExecution())) {
            throw new IllegalArgumentException("Can't look up info for a FlowNode that doesn't belong to this execution!");
        }
        return getInternalGraphLookup().findAllEnclosingBlockStarts(node);
    }

    /** @see GraphLookupView#iterateEnclosingBlocks(FlowNode)
     * @throws IllegalArgumentException If the input {@link FlowNode} does not belong to this execution
     */
    @NonNull
    @Override
    @Restricted(NoExternalUse.class)
    public Iterable<BlockStartNode> iterateEnclosingBlocks(@NonNull FlowNode node) {
        if (!this.equals(node.getExecution())) {
            throw new IllegalArgumentException("Can't look up info for a FlowNode that doesn't belong to this execution!");
        }
        return getInternalGraphLookup().iterateEnclosingBlocks(node);
    }

    /**
     * Called after a restart and any attempts at {@link StepExecution#onResume} have completed.
     * This is a signal that it is safe to resume program execution.
     * By default, does nothing.
     */
    protected void afterStepExecutionsResumed() {}

}

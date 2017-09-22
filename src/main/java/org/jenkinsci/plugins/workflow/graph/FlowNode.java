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

package org.jenkinsci.plugins.workflow.graph;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.Action;
import hudson.model.Actionable;
import hudson.model.BallColor;
import hudson.model.Saveable;
import hudson.search.SearchItem;
import java.io.IOException;
import java.io.ObjectStreamException;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import org.jenkinsci.plugins.workflow.actions.ErrorAction;
import org.jenkinsci.plugins.workflow.actions.LabelAction;
import org.jenkinsci.plugins.workflow.actions.PersistentAction;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionListener;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.flow.GraphListener;
import org.jenkinsci.plugins.workflow.graphanalysis.DepthFirstScanner;
import org.jenkinsci.plugins.workflow.graphanalysis.LinearBlockHoppingScanner;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * One node in a flow graph.
 */
@ExportedBean
public abstract class FlowNode extends Actionable implements Saveable {
    private transient List<FlowNode> parents;
    private final List<String> parentIds;

    private String id;

    @SuppressFBWarnings(value="IS2_INCONSISTENT_SYNC", justification="this is a copy-on-write array so synchronization isn't needed between reader & writer")
    private transient CopyOnWriteArrayList<Action> actions = new CopyOnWriteArrayList<Action>();

    private transient final FlowExecution exec;

    protected FlowNode(FlowExecution exec, String id, List<FlowNode> parents) {
        this.id = id;
        this.exec = exec;
        this.parents = ImmutableList.copyOf(parents);
        parentIds = ids();
    }

    protected FlowNode(FlowExecution exec, String id, FlowNode... parents) {
        this.id = id;
        this.exec = exec;
        this.parents = ImmutableList.copyOf(parents);
        parentIds = ids();
    }

    private List<String> ids() {
        List<String> ids = new ArrayList<>(parents.size());
        for (FlowNode n : parents) {
            ids.add(n.id);
        }
        return ids;
    }

    protected Object readResolve() throws ObjectStreamException {
        // Ensure we deduplicate strings upon deserialization
        if (this.id != null) {
            this.id = this.id.intern();
        }
        if (parentIds != null) {
            for (int i=0; i<parentIds.size(); i++) {
                parentIds.set(i, parentIds.get(i).intern());
            }
        }
        return this;
    }

    /**
     * Transient flag that indicates if this node is currently actively executing something.
     * <p>It will be false for a node which still has active children, like a step with a running body.
     * It will also be false for something that has finished but is pending child node creation,
     * such as a completed fork branch which is waiting for the join node to be created.
     * <p>This can only go from true to false.
     * @deprecated Usually {@link #isActive} is what you want. If you really wanted the original behavior, use {@link FlowExecution#isCurrentHead}.
     */
    @Deprecated
    public final boolean isRunning() {
        return getExecution().isCurrentHead(this);
    }

    /**
     * Checks whether a node is still part of the active part of the graph.
     * Unlike {@link #isRunning}, this behaves intuitively for a {@link BlockStartNode}:
     * it will be considered active until the {@link BlockEndNode} is added.
     */
    @Exported(name="running")
    public final boolean isActive() {
        if (this instanceof FlowEndNode) { // cf. JENKINS-26139
            LOGGER.finer("shortcut: FlowEndNode is never active");
            return false;
        }
        if (this instanceof BlockStartNode) {
            Map<FlowExecutionOwner, Map<String, Boolean>> startNodesAreClosedByFlow = FlowL.startNodesAreClosedByFlow();
            LOGGER.log(Level.FINER, "for {0}, startNodesAreClosedByFlow={1}", new Object[] {this, startNodesAreClosedByFlow});
            Map<String, Boolean> startNodesAreClosed = startNodesAreClosedByFlow.get(exec.getOwner());
            if (startNodesAreClosed != null) {
                Boolean closed = startNodesAreClosed.get(id);
                if (closed != null) { // quick version
                    LOGGER.log(Level.FINER, "quick closed={0}", closed);
                    return !closed;
                } else {
                    LOGGER.log(Level.FINER, "no record of {0} in {1}, presumably GraphListener not working", new Object[] {this, exec});
                }
            } else {
                LOGGER.log(Level.FINER, "no record of {0}, either FlowExecutionListener not working or it is already complete", exec);
            }
        } // atom or end node, or fall through to slow mode for start node
        List<FlowNode> currentHeads = exec.getCurrentHeads();
        if (currentHeads.contains(this)) {
            LOGGER.log(Level.FINER, "{0} is a current head", this);
            return true;
        }
        if (currentHeads.size() == 1 && currentHeads.get(0) instanceof FlowEndNode) { // i.e., exec.isComplete()
            LOGGER.log(Level.FINER, "{0} is complete", exec);
            return false;
        }
        if (this instanceof BlockStartNode) {
            // Fallback (old workflow-job, old workflow-cps):
            LOGGER.log(Level.FINER, "slow currentHeads={0}", currentHeads);
            for (FlowNode headNode : currentHeads) {
                if (new LinearBlockHoppingScanner().findFirstMatch(headNode, Predicates.equalTo(this)) != null) {
                    LOGGER.finer("slow match");
                    return true;
                }
            }
            LOGGER.finer("slow no match");
            return false;
        }
        LOGGER.log(Level.FINER, "{0} is not a current head nor a start node", this);
        return false;
    }
    /**
     * Cache of known block start node statuses.
     * Keys are running executions ~ builds.
     * Values are maps from {@link BlockStartNode#getId} to whether the corresponding {@link BlockEndNode} has been encountered.
     * For old {@code workflow-job}, the top-level entries will be missing;
     * for old {@code workflow-cps}, the second-level entries will be missing.
     */
    @Restricted(DoNotUse.class)
    @Extension public static final class GraphL implements GraphListener.Synchronous {
        @Override public void onNewHead(FlowNode node) {
            LOGGER.finer("GraphListener working");
            if (node instanceof BlockStartNode || node instanceof BlockEndNode) {
                Map<String, Boolean> startNodesAreClosed = FlowL.startNodesAreClosedByFlow().get(node.getExecution().getOwner());
                if (startNodesAreClosed != null) {
                    if (node instanceof BlockStartNode) {
                        assert !startNodesAreClosed.containsKey(node.getId());
                        // Starting a block; record that it is open.
                        startNodesAreClosed.put(node.getId(), false);
                    } else {
                        // Closed a block; find the matching start node and record that it is now closed.
                        startNodesAreClosed.put(((BlockEndNode) node).getStartNode().getId(), true);
                    }
                } // else we must have an old workflow-job
            } // do not need to pay attention to atom nodes: either they are current heads, thus active, or they are not, thus inactive
        }
    }
    @Restricted(DoNotUse.class)
    @Extension public static final class FlowL extends FlowExecutionListener {
        final Map<FlowExecutionOwner, Map<String, Boolean>> startNodesAreClosedByFlow = new HashMap<>();
        static Map<FlowExecutionOwner, Map<String, Boolean>> startNodesAreClosedByFlow() {
            FlowL flowL = ExtensionList.lookup(FlowExecutionListener.class).get(FlowL.class);
            if (flowL == null) { // should not happen unless Jenkins is busted
                throw new IllegalStateException("missing FlowNode.FlowL extension");
            }
            return flowL.startNodesAreClosedByFlow;
        }
        @Override public void onRunning(FlowExecution execution) {
            LOGGER.finer("FlowExecutionListener working");
            assert !startNodesAreClosedByFlow.containsKey(execution.getOwner());
            startNodesAreClosedByFlow.put(execution.getOwner(), new HashMap<String, Boolean>());
        }
        @Override public void onResumed(FlowExecution execution) {
            assert !startNodesAreClosedByFlow.containsKey(execution.getOwner());
            Map<String, Boolean> startNodesAreClosed = new HashMap<String, Boolean>();
            startNodesAreClosedByFlow.put(execution.getOwner(), startNodesAreClosed);
            // To handle start nodes encountered in a prior Jenkins session, try to recreate the cache to date:
            DepthFirstScanner dfs = new DepthFirstScanner();
            dfs.setup(execution.getCurrentHeads());
            for (FlowNode n : dfs) { // end nodes first, later the start nodes
                if (n instanceof BlockEndNode) {
                    startNodesAreClosed.put(((BlockEndNode) n).getStartNode().getId(), true);
                } else if (n instanceof BlockStartNode) {
                    if (!startNodesAreClosed.containsKey(n.getId())) {
                        // If we have not encountered the BlockEndNode, it remains open.
                        startNodesAreClosed.put(n.getId(), false);
                    }
                }
            }
        }
        @Override public void onCompleted(FlowExecution execution) {
            assert startNodesAreClosedByFlow.containsKey(execution.getOwner());
            // After a build finishes, we do not need the cache any more, since we do the equivalent of FlowExecution.isComplete relatively quickly:
            startNodesAreClosedByFlow.remove(execution.getOwner());
        }
    }

    /**
     * If this node has terminated with an error, return an object that indicates that.
     * This is just a convenience method.
     */
    public final @CheckForNull ErrorAction getError() {
        return getPersistentAction(ErrorAction.class);
    }

    public @Nonnull FlowExecution getExecution() {
        return exec;
    }

    /**
     * Returns a read-only view of parents.
     */
    @Nonnull
    public List<FlowNode> getParents() {
        if (parents == null) {
            parents = loadParents(parentIds);
        }
        return parents;
    }

    @Nonnull
    private List<FlowNode> loadParents(List<String> parentIds) {
        List<FlowNode> _parents = new ArrayList<>(parentIds.size());
        for (String parentId : parentIds) {
            try {
                _parents.add(exec.getNode(parentId));
            } catch (IOException x) {
                LOGGER.log(Level.WARNING, "failed to load parents of " + id, x);
            }
        }
        return _parents;
    }

    @Restricted(DoNotUse.class)
    @Exported(name="parents")
    @Nonnull
    public List<String> getParentIds() {
        List<String> ids = new ArrayList<>(2);
        for (FlowNode parent : getParents()) {
            ids.add(parent.getId());
        }
        return ids;
    }

    /**
     * Has to be unique within a {@link FlowExecution}.
     *
     * Needs to remain stable across serialization and JVM restarts.
     *
     * @see FlowExecution#getNode(String)
     */
    @Exported
    public String getId() {
        return id;
    }

    /**
     * Reference from the parent {@link SearchItem} is through {@link FlowExecution#getNode(String)}
     */
    @Override
    public final String getSearchUrl() {
        return getId();
    }

    @Exported
    @Override
    public String getDisplayName() {
        LabelAction a = getPersistentAction(LabelAction.class);
        if (a!=null)    return a.getDisplayName();
        else            return getTypeDisplayName();
    }

    public String getDisplayFunctionName() {
        String functionName = getTypeFunctionName();
        if (functionName == null) {
            return getDisplayName();
        } else {
            LabelAction a = getPersistentAction(LabelAction.class);
            if (a != null) {
                return functionName + " (" + a.getDisplayName() + ")";
            } else {
                return functionName;
            }
        }
    }

    /**
     * Returns colored orb that represents the current state of this node.
     *
     * TODO: this makes me wonder if we should support other colored states,
     * like unstable and aborted --- seems useful.
     */
    @Exported
    public BallColor getIconColor() {
        BallColor c = getError()!=null ? BallColor.RED : BallColor.BLUE;
        if (isActive()) {
            c = c.anime();
        }
        return c;
    }

    /**
     * Gets a human readable name for this type of the node.
     *
     * This is used to implement {@link #getDisplayName()} as a fallback in case {@link LabelAction} doesnt exist.
     */
    protected abstract String getTypeDisplayName();

    /**
     * Gets a human readable text that may include a {@link StepDescriptor#getFunctionName()}.
     * It would return "echo" for a flow node linked to an EchoStep or "ws {" for WorkspaceStep.
     *
     * For StepEndNode it would return "} // step.getFunctionName()".
     *
     * Note that this method should be abstract (supposed to be implemented in all subclasses), but keeping
     * it non-abstract to avoid binary incompatibilities.
     *
     * @return the text human-readable representation of the step function name
     *      or {@link FlowNode#getDisplayName()} by default (if not overriden in subclasses)
     */
    protected /* abstract */ String getTypeFunctionName() {
        return getDisplayName();
    }

    /**
     * Returns the URL of this {@link FlowNode}, relative to the context root of Jenkins.
     *
     * @return
     *      String like "job/foo/32/execution/node/abcde/" with no leading slash but trailing slash.
     */
    @Exported
    public String getUrl() throws IOException {
        return getExecution().getUrl()+"node/"+getId()+'/';
    }


    /**
     * SPI for subtypes to directly manipulate the actions field.
     *
     * When a brand new {@link FlowNode} is created, or when {@link FlowNode} and actions are
     * stored in close proximity, it is convenient to be able to set the {@link #actions}
     * so as to eliminate the separate call to {@link FlowActionStorage#loadActions(FlowNode)}.
     *
     * This method provides such an opportunity for subtypes.
     */
    protected synchronized void setActions(List<Action> actions) {
            this.actions = new CopyOnWriteArrayList<>(actions);
    }

    /**
     * Return the first nontransient {@link Action} on the FlowNode, without consulting {@link jenkins.model.TransientActionFactory}s
     * <p> This is not restricted to just Actions implementing {@link PersistentAction} but usually they should.
     * Used here because it is much faster than base {@link #getAction(Class)} method.
     * @param type Class of action
     * @param <T>  Action type
     * @return First nontransient action or null if not found.
     */
    @CheckForNull
    public final <T extends Action> T getPersistentAction(@Nonnull Class<T> type) {
        loadActions();
        for (Action a : actions) {
            if (type.isInstance(a)) {
                return type.cast(a);
            }
        }
        return null;
    }

    /** Split out so it can be tightly JIT compiled since the callsite cannot be overridden, benchmarked as a win */
    private <T extends Action> T getMaybeTransientAction(Class<T> type) {
        for (Action a : getAllActions()) {
            if (type.isInstance(a)) {
                return type.cast(a);
            }
        }
        return null;
    }

    @Override
    @CheckForNull
    public <T extends Action> T getAction(Class<T> type) {
        // Delegates internally to methods that are not overloads, which are more subject to inlining and optimization
        // Normally a micro-optimization, but these methods are invoked *heavily* and improves performance 5%
        // In flow analysis
        if (PersistentAction.class.isAssignableFrom(type)) {
            return getPersistentAction(type);
        } else {
            return getMaybeTransientAction(type);
        }
    }

    private synchronized void loadActions() {
        if (actions != null) {
            return;
        }
        try {
            actions = new CopyOnWriteArrayList<>(exec.loadActions(this));
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to load actions for FlowNode id=" + id, e);
            actions = new CopyOnWriteArrayList<>();
        }
    }

    @Exported
    @SuppressWarnings("deprecation") // of override
    @Override
    @SuppressFBWarnings(value = "UG_SYNC_SET_UNSYNC_GET", justification = "CopyOnWrite ArrayList, and field load & modification is synchronized")
    public List<Action> getActions() {
        loadActions();

        /*
        We can't use Actionable#actions to store actions because they aren't transient,
        and we need to store actions elsewhere because this is the only mutable part of FlowNode.

        So we create a separate transient field and store List of them there, and intercept every mutation.
        */
        return new AbstractList<Action>() {

                @Override
                public Action get(int index) {
                    return actions.get(index);
                }

                @Override
                public void add(int index, Action element) {
                    actions.add(index, element);
                    persistSafe();
                }

                @Override
                public Iterator<Action> iterator() {
                    return actions.iterator();
                }

                @Override
                public Action remove(int index) {
                    Action old = actions.remove(index);
                    persistSafe();
                    return old;
                }

                @Override
                public Action set(int index, Action element) {
                    Action old = actions.set(index, element);
                    persistSafe();
                    return old;
                }

                @Override
                public int size() {
                    return actions.size();
                }
        };
    }

    /**
     * Explicitly save all the actions in this {@link FlowNode}.
     * Useful when an existing {@link Action} gets updated.
     */
    @Override
    public void save() throws IOException {
        exec.saveActions(this, actions);
    }

    // Persist, handling possible IOException
    private void persistSafe() {
        try {
            save();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "failed to save actions for FlowNode id=" + this.id, e);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof FlowNode) {
            FlowNode that = (FlowNode) obj;
            return this.id.equals(that.id);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return this.id.hashCode();
    }

    @Override
    public String toString() {
        return getClass().getName() + "[id=" + id + "]";
    }

    private static final Logger LOGGER = Logger.getLogger(FlowNode.class.getName());
}

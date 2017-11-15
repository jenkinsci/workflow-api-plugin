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

import com.google.common.collect.ImmutableList;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.model.Action;
import hudson.model.Actionable;
import hudson.model.BallColor;
import hudson.model.Saveable;
import hudson.search.SearchItem;
import java.io.IOException;
import java.io.ObjectStreamException;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import org.jenkinsci.plugins.workflow.actions.ErrorAction;
import org.jenkinsci.plugins.workflow.actions.LabelAction;
import org.jenkinsci.plugins.workflow.actions.PersistentAction;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
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
        // Note that enclosingId is set in the constructors of AtomNode, BlockEndNode, and BlockStartNode, since we need
        // BlockEndNode in particular to have its start node beforehand.
    }

    protected FlowNode(FlowExecution exec, String id, FlowNode... parents) {
        this.id = id;
        this.exec = exec;
        this.parents = ImmutableList.copyOf(parents);
        parentIds = ids();
        // Note that enclosingId is set in the constructors of AtomNode, BlockEndNode, and BlockStartNode, since we need
        // BlockEndNode in particular to have its start node beforehand.
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
        return this.getExecution().isActive(this);
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
        try {
            if (parentIds.size() == 1) {
                return Collections.singletonList(exec.getNode(parentIds.get(0)));
            } else {
                List<FlowNode> _parents = new ArrayList<>(parentIds.size());
                for (String parentId : parentIds) {
                    _parents.add(exec.getNode(parentId));
                }
                return _parents;
            }
        } catch (IOException x) {
            LOGGER.log(Level.WARNING, "failed to load parents of " + id, x);
            return Collections.emptyList();
        }
    }

    /**
     * Get the {@link #id} of the enclosing {@link BlockStartNode}for this node, or null if none.
     * Only {@link FlowStartNode} and {@link FlowEndNode} should generally return null.
     */
    @CheckForNull
    public String getEnclosingId() {
        FlowNode enclosing = this.exec.findEnclosingBlockStart(this);
        return enclosing != null ? enclosing.getId() : null;
    }

    /**
     * Get the list of enclosing {@link BlockStartNode}s, starting from innermost, for this node.
     * May be empty if we are the {@link FlowStartNode} or {@link FlowEndNode}
     */
    @Nonnull
    public List<? extends BlockStartNode> getEnclosingBlocks() {
        return this.exec.findAllEnclosingBlockStarts(this);
    }

    /** Return an iterator over all enclosing blocks.
     *  Prefer this to {@link #getEnclosingBlocks()} unless you need ALL nodes, because it can evaluate lazily. */
    @Nonnull
    public Iterable<BlockStartNode> iterateEnclosingBlocks() {
        return this.exec.iterateEnclosingBlocks(this);
    }

    /**
     * Returns a read-only view of the IDs for enclosing blocks of this flow node, innermost first. May be empty.
     */
    @Nonnull
    public List<String> getAllEnclosingIds() {
        List<? extends BlockStartNode> nodes = getEnclosingBlocks();
        ArrayList<String> output = new ArrayList<String>(nodes.size());
        for (FlowNode f : nodes) {
            output.add(f.getId());
        }
        return output;
    }

    @Restricted(DoNotUse.class)
    @Exported(name="parents")
    @Nonnull
    public List<String> getParentIds() {
        if (parentIds != null) {
            return Collections.unmodifiableList(parentIds);
        } else {
            List<String> ids = new ArrayList<>(parents.size());
            for (FlowNode parent : getParents()) {
                ids.add(parent.getId());
            }
            return ids;
        }
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
     * This is used to implement {@link #getDisplayName()} as a fallback in case {@link LabelAction} does not exist.
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

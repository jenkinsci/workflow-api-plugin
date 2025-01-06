package org.jenkinsci.plugins.workflow.flow;

import com.google.common.base.Function;
import com.google.common.collect.AbstractIterator;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.XmlFile;
import hudson.init.InitMilestone;
import hudson.init.TermMilestone;
import hudson.init.Terminator;
import hudson.model.Computer;
import hudson.model.listeners.ItemListener;
import hudson.remoting.SingleLaneExecutorService;
import hudson.util.CopyOnWriteList;
import jenkins.model.Jenkins;
import jenkins.util.Timer;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.StepExecutionIterator;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graphanalysis.LinearBlockHoppingScanner;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Enumerates running builds and ensures they resume after Jenkins is restarted.
 */
@Extension
public class FlowExecutionList implements Iterable<FlowExecution> {

    /**
     * Milestone for {@link Terminator} between {@link TermMilestone#STARTED} and {@link #LIST_SAVED}.
     * All running builds have been suspended and their {@link FlowExecutionOwner#getListener}s closed.
     */
    public static final String EXECUTIONS_SUSPENDED = "FlowExecutionList.EXECUTIONS_SUSPENDED";

    /**
     * Milestone for {@link Terminator} between {@link #EXECUTIONS_SUSPENDED} and {@link TermMilestone#COMPLETED}.
     * {@link FlowExecutionList} itself has been saved.
     */
    public static final String LIST_SAVED = "FlowExecutionList.LIST_SAVED";

    private transient volatile boolean resumptionComplete;

    public FlowExecutionList() {
        ExtensionList.lookupFirst(Storage.class).load();
    }

    /**
     * Lists all the current {@link FlowExecution}s.
     */
    @Override
    public Iterator<FlowExecution> iterator() {
        return new AbstractIterator<>() {
            final Iterator<FlowExecutionOwner> base = ExtensionList.lookupFirst(Storage.class).owners();

            @Override
            protected FlowExecution computeNext() {
                while (base.hasNext()) {
                    FlowExecutionOwner o = base.next();
                    try {
                        FlowExecution e = o.get();
                        if (!e.isComplete()) {
                            return e;
                        }
                    } catch (Throwable e) {
                        LOGGER.log(Level.FINE, "Failed to load " + o + ". Unregistering", e);
                        unregister(o);
                    }
                }
                return endOfData();
            }
        };
    }

    /**
     * It is the responsibility of the {@link FlowExecutionOwner} to register itself before it starts executing.
     * And likewise, unregister itself after it is completed, even though this class does clean up entries that
     * are no longer running.
     */
    public synchronized void register(final FlowExecutionOwner self) {
        ExtensionList.lookupFirst(Storage.class).register(self);
    }

    public synchronized void unregister(final FlowExecutionOwner self) {
        ExtensionList.lookupFirst(Storage.class).unregister(self);
    }

    private static final Logger LOGGER = Logger.getLogger(FlowExecutionList.class.getName());

    public static FlowExecutionList get() {
        return ExtensionList.lookupSingleton(FlowExecutionList.class);
    }

    /**
     * Returns true if all executions that were present in this {@link FlowExecutionList} have been loaded.
     *
     * <p>This takes place slightly after {@link InitMilestone#COMPLETED} is reached during Jenkins startup.
     *
     * <p>Useful to avoid resuming Pipelines in contexts that may lead to deadlock.
     *
     * <p>It is <em>not</em> guaranteed that {@link FlowExecution#afterStepExecutionsResumed} has been called at this point.
     */
    @Restricted(Beta.class)
    public boolean isResumptionComplete() {
        return resumptionComplete;
    }

    /**
     * When Jenkins starts up and everything is loaded, be sure to proactively resurrect
     * all the ongoing {@link FlowExecution}s so that they start running again.
     */
    @Extension
    public static class ItemListenerImpl extends ItemListener {
        @Override
        public void onLoaded() {
            Timer.get().submit(FlowExecutionList.get()::resume);
        }
    }

    private void resume() {
        ExtensionList.lookupFirst(Storage.class).resume();
        resumptionComplete = true;
    }

    /**
     * Alternate mechanism for implementing the storage of the set of builds.
     */
    @Restricted(Beta.class)
    public interface Storage extends ExtensionPoint {

        /**
         * Enumerate the build handles.
         * Order is unspecified.
         * The set may be mutated while the iterator is active.
         */
        Iterator<FlowExecutionOwner> owners();

        /**
         * Add an entry, if not already present.
         */
        void register(FlowExecutionOwner owner);

        /**
         * Remove an entry, if present.
         */
        void unregister(FlowExecutionOwner owner);

        /**
         * Check if an entry is present.
         */
        boolean contains(FlowExecutionOwner o);

        /**
         * Load data during startup.
         */
        void load();

        /**
         * Resume builds.
         * {@link FlowExecutionOwner#get} should be called on each entry.
         * If {@link FlowExecution#isComplete} already, or an exception is thrown,
         * the entry should be removed as if {@link #unregister} had been called.
         */
        void resume();

        /**
         * Flush any unsaved data before Jenkins exits.
         */
        void shutDown() throws InterruptedException;
    }

    @Restricted(NoExternalUse.class)
    @Extension(ordinal = -1000)
    public static final class DefaultStorage implements Storage {

        private final CopyOnWriteList<FlowExecutionOwner> runningTasks = new CopyOnWriteList<>();
        private final SingleLaneExecutorService executor = new SingleLaneExecutorService(Timer.get());
        private XmlFile configFile;

        @Override public Iterator<FlowExecutionOwner> owners() {
            return runningTasks.iterator();
        }

        @Override public void register(FlowExecutionOwner o) {
            if (runningTasks.contains(o)) {
                LOGGER.log(Level.WARNING, "{0} was already in the list: {1}", new Object[] {o, runningTasks.getView()});
            } else {
                runningTasks.add(o);
                saveLater();
            }
        }

        @Override public void unregister(FlowExecutionOwner o) {
            if (runningTasks.remove(o)) {
                LOGGER.log(Level.FINE, "unregistered {0}", new Object[] {o});
                saveLater();
            } else {
                LOGGER.log(Level.WARNING, "{0} was not in the list to begin with: {1}", new Object[] {o, runningTasks.getView()});
            }
        }

        @Override public boolean contains(FlowExecutionOwner o) {
            return runningTasks.contains(o);
        }

        @SuppressWarnings("unchecked")
        @Override public void load() {
            XmlFile cf = configFile();
            if (cf == null) {
                return; // oh well
            }
            if (cf.exists()) {
                try {
                    runningTasks.replaceBy((List<FlowExecutionOwner>) cf.read());
                    LOGGER.log(Level.FINE, "loaded: {0}", runningTasks);
                } catch (Exception x) {
                    LOGGER.log(Level.WARNING, "ignoring broken " + cf, x);
                }
            }
        }

        @Override public void resume() {
            boolean needSave = false;
            for (var it = runningTasks.iterator(); it.hasNext();) {
                var o = it.next();
                try {
                    FlowExecution e = o.get();
                    LOGGER.log(Level.FINE, "Eagerly loaded {0}", e);
                    if (e.isComplete()) {
                        LOGGER.log(Level.FINE, "Unregistering completed " + o, e);
                        it.remove();
                        needSave = true;
                    }
                } catch (IOException ex) {
                    LOGGER.log(Level.FINE, "Failed to load " + o + ". Unregistering", ex);
                    it.remove();
                    needSave = true;
                }
            }
            if (needSave) {
                saveLater();
            }
        }

        @Override public void shutDown() throws InterruptedException {
            executor.shutdown();
            executor.awaitTermination(1, TimeUnit.MINUTES);
        }

        private synchronized void saveLater() {
            final List<FlowExecutionOwner> copy = new ArrayList<>(runningTasks.getView());
            LOGGER.log(Level.FINE, "scheduling save of {0}", copy);
            try {
                executor.submit(() -> save(copy));
            } catch (RejectedExecutionException x) {
                LOGGER.log(Level.FINE, "could not schedule save, perhaps because Jenkins is shutting down; saving immediately", x);
                save(copy);
            }
        }

        private void save(List<FlowExecutionOwner> copy) {
            XmlFile cf = configFile();
            LOGGER.log(Level.FINE, "saving {0} to {1}", new Object[] {copy, cf});
            if (cf == null) {
                return; // oh well
            }
            try {
                cf.write(copy);
            } catch (IOException x) {
                LOGGER.log(Level.WARNING, null, x);
            }
        }

        private synchronized @CheckForNull XmlFile configFile() {
            if (configFile == null) {
                Jenkins j = Jenkins.getInstanceOrNull();
                if (j != null) {
                    configFile = new XmlFile(new File(j.getRootDir(), FlowExecutionList.class.getName() + ".xml"));
                }
            }
            return configFile;
        }
    }

    /**
     * Enumerates {@link StepExecution}s running inside {@link FlowExecution}.
     */
    @Extension
    public static class StepExecutionIteratorImpl extends StepExecutionIterator {
        @Override
        public ListenableFuture<?> apply(final Function<StepExecution, Void> f) {
            List<ListenableFuture<?>> all = new ArrayList<>();

            for (FlowExecution e : FlowExecutionList.get()) {
                ListenableFuture<List<StepExecution>> execs = e.getCurrentExecutions(false);
                // It is important that the combined future's return values do not reference the individual step
                // executions, so we use transform instead of addCallback. Otherwise, it is possible to leak references
                // to the WorkflowRun for each processed StepExecution in the case where a single live FlowExecution
                // has a stuck CpsVmExecutorService that prevents the getCurrentExecutions future from completing.
                ListenableFuture<Void> results = Futures.transform(execs, (List<StepExecution> result) -> {
                    for (StepExecution se : result) {
                        try {
                            f.apply(se);
                        } catch (RuntimeException x) {
                            LOGGER.log(Level.WARNING, null, x);
                        }
                    }
                    return null;
                }, MoreExecutors.directExecutor());
                ListenableFuture<Void> resultsWithWarningsLogged = Futures.catching(results, Throwable.class, t -> {
                    LOGGER.log(Level.WARNING, null, t);
                    return null;
                }, MoreExecutors.directExecutor());
                all.add(resultsWithWarningsLogged);
            }

            return Futures.allAsList(all);
        }
    }

    @Restricted(DoNotUse.class)
    @Terminator(requires = EXECUTIONS_SUSPENDED, attains = LIST_SAVED)
    public static void saveAll() throws InterruptedException {
        LOGGER.fine("ensuring all executions are saved");
        ExtensionList.lookupFirst(Storage.class).shutDown();
    }

    /**
     * Whenever a Pipeline resumes, resume all incomplete steps in its {@link FlowExecution}.
     *
     * <p>Called by {@code WorkflowRun.onLoad}, so guaranteed to run if a Pipeline resumes
     * regardless of its presence in {@link FlowExecutionList}.
     */
    @Extension
    public static class ResumeStepExecutionListener extends FlowExecutionListener {
        @Override
        public void onResumed(@NonNull FlowExecution e) {
            Futures.addCallback(e.getCurrentExecutions(false), new FutureCallback<List<StepExecution>>() {
                @Override
                public void onSuccess(@NonNull List<StepExecution> result) {
                    if (e.isComplete()) {
                        // WorkflowRun.onLoad will not fireResumed if the execution was already complete when loaded,
                        // and CpsFlowExecution should not then complete until afterStepExecutionsResumed, so this is defensive.
                        return;
                    }
                    var storage = ExtensionList.lookupFirst(Storage.class);
                    FlowExecutionOwner owner = e.getOwner();
                    if (!storage.contains(owner)) {
                        LOGGER.warning(() -> "Resuming " + owner + ", which is missing from FlowExecutionList, so registering it now");
                        storage.register(owner);
                    }
                    LOGGER.log(Level.FINE, "Will resume {0}", result);
                    new ParallelResumer(result, e::afterStepExecutionsResumed).run();
                }

                @Override
                public void onFailure(@NonNull Throwable t) {
                    if (t instanceof CancellationException) {
                        LOGGER.log(Level.FINE, "Cancelled load of " + e, t);
                    } else {
                        LOGGER.log(Level.WARNING, "Failed to load " + e, t);
                    }
                    e.afterStepExecutionsResumed();
                }

            }, MoreExecutors.directExecutor());
        }
    }

    /** Calls {@link StepExecution#onResume} for each step in a running build.
     * Does so in parallel, but always completing enclosing blocks before the enclosed step.
     * A simplified version of https://stackoverflow.com/a/67449067/12916, since this should be a tree not a general DAG.
     */
    private static final class ParallelResumer {

        private final Runnable onCompletion;
        /** Step nodes mapped to the step execution. Entries removed when they are ready to be resumed. */
        private final Map<FlowNode, StepExecution> nodes = new HashMap<>();
        /** Step nodes currently being resumed. Removed after resumption completes. */
        private final Set<FlowNode> processing = new HashSet<>();
        /** Step nodes mapped to the nearest enclosing step node (no entry if at root). */
        private final Map<FlowNode, FlowNode> enclosing = new HashMap<>();

        ParallelResumer(Collection<StepExecution> executions, Runnable onCompletion) {
            this.onCompletion = onCompletion;
            // First look up positions in the flow graph, so that we can compute dependencies:
            for (StepExecution se : executions) {
                try {
                    FlowNode n = se.getContext().get(FlowNode.class);
                    if (n != null) {
                        nodes.put(n, se);
                    } else {
                        LOGGER.warning(() -> "Could not find FlowNode for " + se + " so it will not be resumed");
                    }
                } catch (IOException | InterruptedException x) {
                    LOGGER.log(Level.WARNING, "Could not look up FlowNode for " + se + " so it will not be resumed", x);
                }
            }
            for (Map.Entry<FlowNode, StepExecution> entry : nodes.entrySet()) {
                FlowNode n = entry.getKey();
                try {
                    LinearBlockHoppingScanner scanner = new LinearBlockHoppingScanner();
                    scanner.setup(n);
                    for (FlowNode parent : scanner) {
                        if (parent != n && nodes.containsKey(parent)) {
                            enclosing.put(n, parent);
                            break;
                        }
                    }
                } catch (Exception x) {
                    LOGGER.log(Level.WARNING, x, () -> "Unable to compute enclosing blocks for " + n + ", so " + entry.getValue() + " might not resume successfully");
                }
            }
        }

        synchronized void run() {
            if (Jenkins.get().isTerminating()) {
                LOGGER.fine("Skipping step resumption during shutdown");
                return;
            }
            if (Jenkins.get().getInitLevel() != InitMilestone.COMPLETED || Jenkins.get().isQuietingDown()) {
                LOGGER.fine("Waiting to resume step until Jenkins completes startup and is not in quiet mode");
                Timer.get().schedule(this::run, 100, TimeUnit.MILLISECONDS);
                return;
            }
            LOGGER.fine(() -> "Checking status with nodes=" + nodes + " enclosing=" + enclosing + " processing=" + processing);
            if (nodes.isEmpty()) {
                if (processing.isEmpty()) {
                    LOGGER.fine("Done");
                    onCompletion.run();
                }
                return;
            }
            Map<FlowNode, StepExecution> ready = new HashMap<>();
            for (Map.Entry<FlowNode, StepExecution> entry : nodes.entrySet()) {
                FlowNode n = entry.getKey();
                FlowNode parent = enclosing.get(n);
                if (parent == null || !nodes.containsKey(parent)) {
                    ready.put(n, entry.getValue());
                }
            }
            LOGGER.fine(() -> "Ready to resume: " + ready);
            nodes.keySet().removeAll(ready.keySet());
            for (Map.Entry<FlowNode, StepExecution> entry : ready.entrySet()) {
                FlowNode n = entry.getKey();
                StepExecution exec = entry.getValue();
                processing.add(n);
                // Strictly speaking threadPoolForRemoting should be used for agent communications.
                // In practice the only onResume impl known to block is in ExecutorStepExecution.
                // Avoid jenkins.util.Timer since it is capped at 10 threads and should not be used for long tasks.
                Computer.threadPoolForRemoting.submit(() -> {
                    LOGGER.fine(() -> "About to resume " + n + " ~ " + exec);
                    try {
                        exec.onResume();
                    } catch (Throwable x) {
                        exec.getContext().onFailure(x);
                    }
                    LOGGER.fine(() -> "Finished resuming " + n + " ~ " + exec);
                    synchronized (ParallelResumer.this) {
                        processing.remove(n);
                        run();
                    }
                });
            }
        }

    }

}

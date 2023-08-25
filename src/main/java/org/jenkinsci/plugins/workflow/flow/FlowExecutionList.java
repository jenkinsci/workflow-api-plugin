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
import org.jvnet.hudson.reactor.Milestone;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;
import org.kohsuke.accmod.restrictions.DoNotUse;

/**
 * Tracks the running {@link FlowExecution}s so that it can be enumerated.
 *
 * @author Kohsuke Kawaguchi
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

    private final CopyOnWriteList<FlowExecutionOwner> runningTasks = new CopyOnWriteList<>();
    private final SingleLaneExecutorService executor = new SingleLaneExecutorService(Timer.get());
    private XmlFile configFile;

    private transient volatile boolean resumptionComplete;

    public FlowExecutionList() {
        load();
    }

    /**
     * Lists all the current {@link FlowExecutionOwner}s.
     */
    @Override
    public Iterator<FlowExecution> iterator() {
        return new AbstractIterator<>() {
            final Iterator<FlowExecutionOwner> base = runningTasks.iterator();

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

    private synchronized @CheckForNull XmlFile configFile() {
        if (configFile == null) {
            Jenkins j = Jenkins.getInstanceOrNull();
            if (j != null) {
                configFile = new XmlFile(new File(j.getRootDir(), FlowExecutionList.class.getName() + ".xml"));
            }
        }
        return configFile;
    }

    @SuppressWarnings("unchecked")
    private synchronized void load() {
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

    /**
     * It is the responsibility of the {@link FlowExecutionOwner} to register itself before it starts executing.
     * And likewise, unregister itself after it is completed, even though this class does clean up entries that
     * are no longer running.
     */
    public synchronized void register(final FlowExecutionOwner self) {
        if (runningTasks.contains(self)) {
            LOGGER.log(Level.WARNING, "{0} was already in the list: {1}", new Object[] {self, runningTasks.getView()});
        } else {
            runningTasks.add(self);
            saveLater();
        }
    }

    public synchronized void unregister(final FlowExecutionOwner self) {
        if (runningTasks.remove(self)) {
            LOGGER.log(Level.FINE, "unregistered {0}", new Object[] {self});
            saveLater();
        } else {
            LOGGER.log(Level.WARNING, "{0} was not in the list to begin with: {1}", new Object[] {self, runningTasks.getView()});
        }
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

    private static final Logger LOGGER = Logger.getLogger(FlowExecutionList.class.getName());

    public static FlowExecutionList get() {
        FlowExecutionList l = ExtensionList.lookup(FlowExecutionList.class).get(FlowExecutionList.class);
        if (l == null) { // might be called during shutdown
            l = new FlowExecutionList();
        }
        return l;
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
            FlowExecutionList list = FlowExecutionList.get();
            for (final FlowExecution e : list) {
                // The call to FlowExecutionOwner.get in the implementation of iterator() is sufficent to load the Pipeline.
                LOGGER.log(Level.FINE, "Eagerly loaded {0}", e);
            }
            list.resumptionComplete = true;
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
                all.add(execs);
                Futures.addCallback(execs,new FutureCallback<List<StepExecution>>() {
                    @Override
                    public void onSuccess(@NonNull List<StepExecution> result) {
                        for (StepExecution e : result) {
                            try {
                                f.apply(e);
                            } catch (RuntimeException x) {
                                LOGGER.log(Level.WARNING, null, x);
                            }
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Throwable t) {
                        LOGGER.log(Level.WARNING, null, t);
                    }
                }, MoreExecutors.directExecutor());
            }

            return Futures.allAsList(all);
        }
    }

    @Restricted(DoNotUse.class)
    @SuppressWarnings("deprecation")
    @Terminator(requires = EXECUTIONS_SUSPENDED, attains = LIST_SAVED)
    public static void saveAll() throws InterruptedException {
        LOGGER.fine("ensuring all executions are saved");

        for (FlowExecutionOwner owner : get().runningTasks.getView()) {
            try {
                owner.notifyShutdown();
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Error shutting down task", ex);
            }
        }

        SingleLaneExecutorService executor = get().executor;
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.MINUTES);
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
                    FlowExecutionList list = FlowExecutionList.get();
                    FlowExecutionOwner owner = e.getOwner();
                    if (!list.runningTasks.contains(owner)) {
                        LOGGER.log(Level.WARNING, "Resuming {0}, which is missing from FlowExecutionList ({1}), so registering it now.", new Object[] {owner, list.runningTasks.getView()});
                        list.register(owner);
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
            for (FlowNode n : nodes.keySet()) {
                LinearBlockHoppingScanner scanner = new LinearBlockHoppingScanner();
                scanner.setup(n);
                for (FlowNode parent : scanner) {
                    if (parent != n && nodes.containsKey(parent)) {
                        enclosing.put(n, parent);
                        break;
                    }
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

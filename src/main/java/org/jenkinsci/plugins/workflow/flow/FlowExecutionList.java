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
import hudson.init.Terminator;
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
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;

/**
 * Tracks the running {@link FlowExecution}s so that it can be enumerated.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class FlowExecutionList implements Iterable<FlowExecution> {
    private final CopyOnWriteList<FlowExecutionOwner> runningTasks = new CopyOnWriteList<>();
    private final SingleLaneExecutorService executor = new SingleLaneExecutorService(Timer.get());
    private XmlFile configFile;

    public FlowExecutionList() {
        load();
    }

    /**
     * Lists all the current {@link FlowExecutionOwner}s.
     */
    @Override
    public Iterator<FlowExecution> iterator() {
        return new AbstractIterator<FlowExecution>() {
            final Iterator<FlowExecutionOwner> base = runningTasks.iterator();

            @Override
            protected FlowExecution computeNext() {
                while (base.hasNext()) {
                    FlowExecutionOwner o = base.next();
                    try {
                        FlowExecution e = o.get();
                        if (e.isComplete()) {
                            unregister(o);
                        } else {
                            return e;
                        }
                    } catch (Throwable e) {
                        LOGGER.log(Level.WARNING, "Failed to load " + o + ". Unregistering", e);
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
            executor.submit(new Runnable() {
                @Override public void run() {
                    save(copy);
                }
            });
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
     * When Jenkins starts up and everything is loaded, be sure to proactively resurrect
     * all the ongoing {@link FlowExecution}s so that they start running again.
     */
    @Extension
    public static class ItemListenerImpl extends ItemListener {
        @Override
        public void onLoaded() {
            for (final FlowExecution e : FlowExecutionList.get()) {
                // The call to FlowExecutionOwner.get in the implementation of iterator() is sufficent to load the Pipeline.
                LOGGER.log(Level.FINE, "Eagerly loaded {0}", e);
            }
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
                    public void onSuccess(List<StepExecution> result) {
                        for (StepExecution e : result) {
                            try {
                                f.apply(e);
                            } catch (RuntimeException x) {
                                LOGGER.log(Level.WARNING, null, x);
                            }
                        }
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        LOGGER.log(Level.WARNING, null, t);
                    }
                }, MoreExecutors.directExecutor());
            }

            return Futures.allAsList(all);
        }
    }

    @Restricted(DoNotUse.class)
    @Terminator public static void saveAll() throws InterruptedException {
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
     * Called by {@code WorkflowRun.onLoad}, so guaranteed to run if a Pipeline resumes regardless of its presence in
     * {@link FlowExecutionList}.
     */
    @Extension
    public static class ResumeStepExecutionListener extends FlowExecutionListener {
        @Override
        public void onResumed(@NonNull FlowExecution e) {
            Futures.addCallback(e.getCurrentExecutions(false), new FutureCallback<List<StepExecution>>() {
                @Override
                public void onSuccess(List<StepExecution> result) {
                    if (e.isComplete()) {
                        // WorkflowRun.onLoad will not fire onResumed if the serialized execution was already
                        // complete when loaded, but right now (at least for workflow-cps), the execution resumes
                        // asynchronously before WorkflowRun.onLoad completes, so it is possible that the execution
                        // finishes before onResumed gets called.
                        // That said, there is nothing to prevent the execution from completing right after we check
                        // isComplete. If we want to fully prevent that, we would need to delay actual execution
                        // resumption until WorkflowRun.onLoad completes or add some form of synchronization.
                        return;
                    }
                    FlowExecutionList list = FlowExecutionList.get();
                    FlowExecutionOwner owner = e.getOwner();
                    if (!list.runningTasks.contains(owner)) {
                        LOGGER.log(Level.WARNING, "Resuming {0}, which is missing from FlowExecutionList ({1}), so registering it now.",
                                new Object[] {owner, list.runningTasks.getView()});
                        list.register(owner);
                    }
                    LOGGER.log(Level.FINE, "Will resume {0}", result);
                    for (StepExecution se : result) {
                        se.onResume();
                    }
                }

                @Override
                public void onFailure(Throwable t) {
                    if (t instanceof CancellationException) {
                        LOGGER.log(Level.FINE, "Cancelled load of " + e, t);
                    } else {
                        LOGGER.log(Level.WARNING, "Failed to load " + e, t);
                    }
                }

            }, Timer.get()); // We always hold RunMap and WorkflowRun locks here, so we resume steps on a different thread to avoid potential deadlocks. See JENKINS-67351.
        }
    }
}

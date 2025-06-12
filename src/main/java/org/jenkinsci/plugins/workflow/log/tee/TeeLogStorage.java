package org.jenkinsci.plugins.workflow.log.tee;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.console.AnnotatedLargeText;
import hudson.model.BuildListener;
import hudson.model.TaskListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.log.LogStorage;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

/**
 * Advancaed implementation of log storage allowing a primary log storage for read and write; and multiple secondary log storages for writes.
 * This behaves as a tee execution.
 */
@Restricted(Beta.class)
public class TeeLogStorage implements LogStorage {

    LogStorage primary;
    List<LogStorage> secondaries = List.of();

    /**
     * Log storage allowing a primary for read/write and multiple secondaries for write only
     * @param primary primary log storage used for read and write
     * @param secondaries secondary log storages used for write
     */
    public TeeLogStorage(@NonNull LogStorage primary, LogStorage... secondaries) {
        this.primary = primary;
        if (secondaries != null) {
            this.secondaries =
                    Arrays.stream(secondaries).filter(Objects::nonNull).toList();
        }
    }

    @Override
    public LogStorage getPrimary() {
        return primary;
    }

    @Override
    public List<LogStorage> getSecondaries() {
        return secondaries;
    }

    @NonNull
    @Override
    public BuildListener overallListener() throws IOException, InterruptedException {
        List<BuildListener> secondaryListeners = new ArrayList<>();
        for (LogStorage secondary : secondaries) {
            secondaryListeners.add(secondary.overallListener());
        }

        return new TeeBuildListener(primary.overallListener(), secondaryListeners.toArray(BuildListener[]::new));
    }

    @NonNull
    @Override
    public TaskListener nodeListener(@NonNull FlowNode node) throws IOException, InterruptedException {
        List<TaskListener> secondaryListeners = new ArrayList<>();
        for (LogStorage secondary : secondaries) {
            secondaryListeners.add(secondary.nodeListener(node));
        }
        return new TeeTaskListener(primary.nodeListener(node), secondaryListeners.toArray(TaskListener[]::new));
    }

    @NonNull
    @Override
    public AnnotatedLargeText<FlowExecutionOwner.Executable> overallLog(
            @NonNull FlowExecutionOwner.Executable build, boolean complete) {
        return primary.overallLog(build, complete);
    }

    @NonNull
    @Override
    public AnnotatedLargeText<FlowNode> stepLog(@NonNull FlowNode node, boolean complete) {
        return primary.stepLog(node, complete);
    }
}

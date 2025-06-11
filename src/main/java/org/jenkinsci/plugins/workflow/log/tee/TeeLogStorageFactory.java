package org.jenkinsci.plugins.workflow.log.tee;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.log.LogStorage;
import org.jenkinsci.plugins.workflow.log.LogStorageFactory;

public class TeeLogStorageFactory {

    public static Optional<TeeLogStorage> handleFactories(List<LogStorageFactory> factories, FlowExecutionOwner b) {
        if (factories.size() <= 1) {
            return Optional.empty();
        }
        List<LogStorageFactory> copy = new ArrayList<>(factories);
        LogStorage primary = copy.remove(0).forBuild(b);
        List<LogStorage> secondaries = new ArrayList<>();
        for (LogStorageFactory factory : copy) {
            secondaries.add(factory.forBuild(b));
        }
        if (primary == null) {
            return Optional.empty();
        }
        return Optional.of(new TeeLogStorage(primary, secondaries.toArray(LogStorage[]::new)));
    }
}

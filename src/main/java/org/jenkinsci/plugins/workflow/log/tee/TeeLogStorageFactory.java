package org.jenkinsci.plugins.workflow.log.tee;

import hudson.ExtensionList;
import hudson.model.Describable;
import hudson.model.Descriptor;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.configuration.TeeLogStorageFactoryConfiguration;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.log.LogStorage;
import org.jenkinsci.plugins.workflow.log.LogStorageFactory;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

/***
 * Allows a {@link LogStorage} to be teeable, meaning it can be configured as a primary or secondary log storage.
 * See {@link TeeLogStorage}.
 */
@Restricted(Beta.class)
public interface TeeLogStorageFactory extends LogStorageFactory, Describable<TeeLogStorageFactory> {

    public static List<TeeLogStorageFactory> all() {
        return ExtensionList.lookup(TeeLogStorageFactory.class);
    }

    /**
     * Handle the factories configured in {@link TeeLogStorageFactoryConfiguration}
     */
    public static Optional<TeeLogStorage> handleFactories(FlowExecutionOwner b) {
        TeeLogStorageFactoryConfiguration configuration = TeeLogStorageFactoryConfiguration.get();

        if (!configuration.isEnabled()) {
            return Optional.empty();
        }
        if (configuration.getFactories().isEmpty()) {
            return Optional.empty();
        }

        List<LogStorageFactory> copy = new ArrayList<>(configuration.getFactories());
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

    default Descriptor<TeeLogStorageFactory> getDescriptor() {
        return Jenkins.get().getDescriptorOrDie(this.getClass());
    }
}

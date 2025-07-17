package org.jenkinsci.plugins.workflow.log.tee;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.DescriptorVisibilityFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.log.BrokenLogStorage;
import org.jenkinsci.plugins.workflow.log.LogStorage;
import org.jenkinsci.plugins.workflow.log.LogStorageFactory;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/***
 * Allows a {@link LogStorage} to be teeable, meaning it can be configured as a primary or secondary log storage.
 * See {@link TeeLogStorage}.
 */
public class TeeLogStorageFactory implements LogStorageFactory {

    private static final Logger LOGGER = Logger.getLogger(TeeLogStorageFactory.class.getName());

    private LogStorageFactory primary;
    private List<LogStorageFactory> secondaries = List.of();

    @DataBoundConstructor
    public TeeLogStorageFactory() {}

    private List<LogStorageFactory> factories = new ArrayList<>();

    public @CheckForNull List<LogStorageFactory> getFactories() {
        return factories;
    }

    @DataBoundSetter
    public void setFactories(List<LogStorageFactory> factories) {
        this.factories = factories;

        if (this.factories == null || this.factories.isEmpty()) {
            this.primary = null;
            this.secondaries = null;
            return;
        }

        List<LogStorageFactory> copy = new ArrayList<>(factories);
        this.primary = copy.remove(0);
        this.secondaries = copy;
    }

    @Override
    public LogStorage forBuild(@NonNull FlowExecutionOwner b) {
        if (this.primary == null) {
            return new BrokenLogStorage(new IllegalArgumentException("The primary TeeLogStorageFactory is not set."));
        }
        var primaryLogStorage = this.primary.forBuild(b);
        if (primaryLogStorage == null) {
            return new BrokenLogStorage(new IllegalArgumentException(String.format(
                    "The primary TeeLogStorageFactory of type %s returned null",
                    primary.getClass().getName())));
        }
        return new TeeLogStorage(
                primaryLogStorage, secondaries.stream().map(s -> s.forBuild(b)).toArray(LogStorage[]::new));
    }

    @Extension
    @Symbol("tee")
    public static final class DescriptorImpl extends Descriptor<LogStorageFactory> {
        @NonNull
        @Override
        public String getDisplayName() {
            return "Tee Log Storage Factory";
        }
    }

    @Extension
    public static class TeeLogStorageFactoryFilter extends DescriptorVisibilityFilter {

        @Override
        public boolean filter(@CheckForNull Object context, @NonNull Descriptor descriptor) {
            if (descriptor instanceof TeeLogStorageFactory.DescriptorImpl) {
                // avoids nesting in configuration page when using `f:repeatableHeteroProperty` in
                // TeeLogStorageFactory/config.jelly
                return false;
            }
            return true;
        }
    }
}

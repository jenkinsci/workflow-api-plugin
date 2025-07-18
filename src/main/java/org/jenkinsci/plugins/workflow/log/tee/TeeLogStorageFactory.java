package org.jenkinsci.plugins.workflow.log.tee;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import java.util.List;
import java.util.logging.Logger;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.log.BrokenLogStorage;
import org.jenkinsci.plugins.workflow.log.LogStorage;
import org.jenkinsci.plugins.workflow.log.LogStorageFactory;
import org.jenkinsci.plugins.workflow.log.LogStorageFactoryDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

/***
 * Allows a {@link LogStorage} to be teeable, meaning it can be configured as a primary or secondary log storage.
 * See {@link TeeLogStorage}.
 */
public class TeeLogStorageFactory implements LogStorageFactory {

    private static final Logger LOGGER = Logger.getLogger(TeeLogStorageFactory.class.getName());

    private final LogStorageFactory primary;

    private final LogStorageFactory secondary;
    
    @DataBoundConstructor
    public TeeLogStorageFactory(LogStorageFactory primary, LogStorageFactory secondary) {
        if (primary == null) {
            throw new IllegalArgumentException("Primary LogStorageFactory cannot be null");
        }
        if (secondary == null) {
            throw new IllegalArgumentException("Secondary LogStorageFactory cannot be null");
        }
        this.primary = primary;
        this.secondary = secondary;
    }

    @NonNull
    public LogStorageFactory getPrimary() {
        return primary;
    }

    @NonNull
    public LogStorageFactory getSecondary() {
        return secondary;
    }

    @Override
    public LogStorage forBuild(@NonNull FlowExecutionOwner b) {
        var primaryLogStorage = this.primary.forBuild(b);
        if (primaryLogStorage == null) {
            return new BrokenLogStorage(new IllegalArgumentException(String.format(
                    "The primary TeeLogStorageFactory of type %s returned null",
                    primary.getClass().getName())));
        }
        var secondaryLogStorage = this.secondary.forBuild(b);
        if (secondaryLogStorage == null) {
            return new BrokenLogStorage(new IllegalArgumentException(String.format(
                    "The secondary TeeLogStorageFactory of type %s returned null",
                    primary.getClass().getName())));
        }
        return new TeeLogStorage(primaryLogStorage, secondaryLogStorage);
    }

    @Extension
    @Symbol("tee")
    public static final class DescriptorImpl extends LogStorageFactoryDescriptor<TeeLogStorageFactory> {
        @NonNull
        @Override
        public String getDisplayName() {
            return "Tee Log Storage Factory";
        }

        public List<LogStorageFactoryDescriptor<?>> getFilteredDescriptors() {
            return LogStorageFactory.all().stream()
                    .filter(descriptor -> {
                        return !TeeLogStorageFactory.class.getName().equals(descriptor.getId());
                    })
                    .toList();
        }
    }
}

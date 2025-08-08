package org.jenkinsci.plugins.workflow.log;

import hudson.model.Descriptor;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

@Restricted(Beta.class)
public abstract class LogStorageFactoryDescriptor<T extends LogStorageFactory> extends Descriptor<LogStorageFactory> {

    /**
     * Indicates whether the factory supports being used in read/write mode (e.g. as a top-level logger, or as a primary for {@link org.jenkinsci.plugins.workflow.log.tee.TeeLogStorageFactory})
     */
    public boolean isReadWrite() {
        return true;
    }
    /**
     * Indicates whether the factory supports being used in write-only mode (as a secondary for {@link org.jenkinsci.plugins.workflow.log.tee.TeeLogStorageFactory}).
     */
    public boolean isWriteOnly() {
        return true;
    }

    /**
     * Allow to define the default factory instance to use if no configuration exists
     */
    public LogStorageFactory getDefaultInstance() {
        return null;
    }
}

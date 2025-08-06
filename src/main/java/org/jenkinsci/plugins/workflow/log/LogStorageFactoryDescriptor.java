package org.jenkinsci.plugins.workflow.log;

import hudson.model.Descriptor;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

@Restricted(Beta.class)
public abstract class LogStorageFactoryDescriptor<T extends LogStorageFactory> extends Descriptor<LogStorageFactory> {

    /**
     * States if the factory descriptor is used to read and write logs.
     */
    public boolean isReadWrite() {
        return true;
    }
    /**
     * States if the factory descriptor is used to write only logs.
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

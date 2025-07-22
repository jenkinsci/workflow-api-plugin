package org.jenkinsci.plugins.workflow.log;

import hudson.model.Descriptor;

public abstract class LogStorageFactoryDescriptor<T extends LogStorageFactory> extends Descriptor<LogStorageFactory> {

    /**
     * States if the factory descriptor is configurable as primary TeeLogStorage.
     */
    public boolean isConfigurableAsPrimaryTeeLogStorageFactory() {
        return true;
    }
    /**
     * States if the factory descriptor is configurable as secondary TeeLogStorage.
     */
    public boolean isConfigurableAsSecondaryTeeLogStorageFactory() {
        return true;
    }

    /**
     * Allow to define the default factory instance to use if no configuration exists
     */
    public LogStorageFactory getDefaultInstance() {
        return null;
    }
}

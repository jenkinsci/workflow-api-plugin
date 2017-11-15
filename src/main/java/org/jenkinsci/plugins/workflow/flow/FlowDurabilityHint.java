/*
 * The MIT License
 *
 * Copyright (c) 2017, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jenkinsci.plugins.workflow.flow;

import javax.annotation.Nonnull;
import java.io.Serializable;

/**
 * Provides hints about just how hard we should try to protect our workflow from failures of the master.
 * There is a trade-off between durability and performance, with higher levels carrying much higher overheads to execution.
 * Storage and persistence of data should try to provide at least the specified level (may offer more).
 * @author Sam Van Oort
 */
public enum FlowDurabilityHint {

    /** Make no promises, we pull out all the stops for speed. */
    NO_PROMISES(false, false, false, "Fastest. Makes no guarantees about resuming pipelines if Jenkins fails."),

    /** Should be able to recover and resume as long as master shut down cleanly without errors. */
    SURVIVE_CLEAN_RESTART(false, false, true, "Fast. Able to resume pipelines if Jenkins shuts down cleanly."),

    /** Sometimes able to recover from an unplanned failure of the master, depending on when and how it happens. */
    PARTIALLY_DURABLE(true, false, true, "Slower.  Able to recover from some unplanned Jenkins failures."),

    /** Do our best to handle even catastrophic failures of the master and resume pipelines. Default level. */
    FULLY_DURABLE(true, true, true, "Slowest but safest. Previously the only option.  Able to recover and resume pipelines in many cases even after catastrophic failures.");

    private final boolean atomicWrite;

    private final boolean synchronousWrite;

    private final boolean allowPersistPartially;

    private final String description;

    FlowDurabilityHint(boolean useAtomicWrite, boolean synchronousWrite, boolean allowPersistPartially, @Nonnull String description) {
        this.atomicWrite = useAtomicWrite;
        this.synchronousWrite = synchronousWrite;
        this.allowPersistPartially = allowPersistPartially;
        this.description = description;
    }

    /** Should we try to use an atomic write to protect from corrupting data with failures and errors during writes? */
    public boolean isAtomicWrite() {
        return atomicWrite;
    }

    /** Do we block execution while writing out state? */
    public boolean isSynchronousWrite() {
        return synchronousWrite;
    }

    /** If false, the flow has to complete one way or the other in order to be persisted. */
    public boolean isAllowPersistPartially() {
        return allowPersistPartially;
    }

    public String getDescription() {return  description;}
}

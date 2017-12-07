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

/**
 * Provides hints about just how hard we should try to protect our workflow from failures of the master.
 * There is a trade-off between durability and performance, with higher levels carrying much higher overheads to execution.
 * Storage and persistence of data should try to provide at least the specified level (may offer more).
 *
 * <p> Implementation note: all implementations should be immutable - this is only an extension point rather than enum because
 *     we may add additional durability flags.
 * @author Sam Van Oort
 */
public enum FlowDurabilityHint {
    PERFORMANCE_OPTIMIZED(false, false, "Performance-optimized. Pipelines resume if Jenkins shuts down cleanly, but running pipelines lose information and can't resume if Jenkins unexpectedly fails."),

    SURVIVABLE_NONATOMIC(false,  true, "Less survivability, a bit faster. Survives most failures but does not rely on atomic writes to XML files, so data may be lost if writes fail or are interrupted."),

    MAX_SURVIVABILITY (true,  true, "Maximum survivability but slowest. " +
            "Previously the only option.  Able to recover and resume pipelines in many cases even after catastrophic failures.");

    private final boolean atomicWrite;

    private final boolean persistWithEveryStep;

    private final String description;

    FlowDurabilityHint (boolean useAtomicWrite, boolean persistWithEveryStep, @Nonnull String description) {
        this.atomicWrite = useAtomicWrite;
        this.persistWithEveryStep = persistWithEveryStep;
        this.description = description;
    }

    /** Should we try to use an atomic write to protect from corrupting data with failures and errors during writes? */
    public boolean isAtomicWrite() {
        return atomicWrite;
    }

    /** If false, the flow has to complete one way or the other in order to be persisted. */
    public boolean isPersistWithEveryStep() {
        return persistWithEveryStep;
    }

    /** For compatibility with Jelly, etc. */
    public String getName() {
        return name();
    }

    public String getDescription() {return  description;}
}

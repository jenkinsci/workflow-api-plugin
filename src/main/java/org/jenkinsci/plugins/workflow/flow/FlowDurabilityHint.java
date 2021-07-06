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

import hudson.util.AtomicFileWriter;

import java.nio.channels.FileChannel;

import javax.annotation.Nonnull;

/**
 * Provides hints about just how hard we should try to protect our workflow from failures of the controller.
 * There is a trade-off between durability and performance, with higher levels carrying much higher overheads to execution.
 * Storage and persistence of data should try to provide at least the specified level (may offer more).
 *
 * @author Sam Van Oort
 */
public enum FlowDurabilityHint {
    PERFORMANCE_OPTIMIZED(false, false, false, Messages.FlowDurabilityHint_PERFORMANCE_OPTIMIZED_description(), Messages.FlowDurabilityHint_PERFORMANCE_OPTIMIZED_tooltip()),

    SURVIVABLE_NONATOMIC(true, false, true, Messages.FlowDurabilityHint_SURVIVABLE_NONATOMIC_description(), Messages.FlowDurabilityHint_SURVIVABLE_NONATOMIC_tooltip()),

    MAX_SURVIVABILITY(true, true, true, Messages.FlowDurabilityHint_MAX_SURVIVABILITY_description(), Messages.FlowDurabilityHint_MAX_SURVIVABILITY_tooltip());

    private final boolean atomicWrite;

    private final boolean force;

    private final boolean persistWithEveryStep;

    private final String description;

    private final String tooltip;

    FlowDurabilityHint(
            boolean useAtomicWrite,
            boolean force,
            boolean persistWithEveryStep,
            @Nonnull String description,
            String tooltip) {
        if (!useAtomicWrite && force) {
            throw new IllegalArgumentException(
                    "Cannot specify force without also specifying atomic writes");
        }
        if (!persistWithEveryStep && (useAtomicWrite || force)) {
            throw new IllegalArgumentException(
                    "Atomic writes or force require persisting with every step");
        }
        this.atomicWrite = useAtomicWrite;
        this.force = force;
        this.persistWithEveryStep = persistWithEveryStep;
        this.description = description;
        this.tooltip = tooltip;
    }

    /** Should we use {@link AtomicFileWriter} to write the file? */
    public boolean isAtomicWrite() {
        return atomicWrite;
    }

    /**
     * Should we call {@link FileChannel#force} (i.e., {@code fsync()}} or {@code
     * FlushFileBuffers()}) after writing the file?
     */
    public boolean isForce() {
        return force;
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

    public String getTooltip() {
        return tooltip;
    }
}

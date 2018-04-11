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
 * @author Sam Van Oort
 */
public enum FlowDurabilityHint {
    PERFORMANCE_OPTIMIZED(false, false, Messages.FlowDurabilityHint_PERFORMANCE_OPTIMIZED_description(), Messages.FlowDurabilityHint_PERFORMANCE_OPTIMIZED_tooltip()),

    SURVIVABLE_NONATOMIC(false,  true, Messages.FlowDurabilityHint_SURVIVABLE_NONATOMIC_description(), Messages.FlowDurabilityHint_SURVIVABLE_NONATOMIC_tooltip()),

    MAX_SURVIVABILITY (true,  true, Messages.FlowDurabilityHint_MAX_SURVIVABILITY_description(), Messages.FlowDurabilityHint_MAX_SURVIVABILITY_tooltip());

    private final boolean atomicWrite;

    private final boolean persistWithEveryStep;

    private final String description;

    private final String tooltip;

    FlowDurabilityHint (boolean useAtomicWrite, boolean persistWithEveryStep, @Nonnull String description, String tooltip) {
        this.atomicWrite = useAtomicWrite;
        this.persistWithEveryStep = persistWithEveryStep;
        this.description = description;
        this.tooltip = tooltip;
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

    public String getTooltip() {
        return tooltip;
    };
}

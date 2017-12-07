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

import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import jenkins.model.Jenkins;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Provides hints about just how hard we should try to protect our workflow from failures of the master.
 * There is a trade-off between durability and performance, with higher levels carrying much higher overheads to execution.
 * Storage and persistence of data should try to provide at least the specified level (may offer more).
 *
 * <p> Implementation note: all implementations should be immutable - this is only an extension point rather than enum because
 *     we may add additional durability flags.
 * @author Sam Van Oort
 */
public abstract class FlowDurabilityHint implements ExtensionPoint, Serializable, Comparable<FlowDurabilityHint> {

    public static ExtensionList<FlowDurabilityHint> all() {
        return Jenkins.getInstance().getExtensionList(FlowDurabilityHint.class);
    }

    public static List<FlowDurabilityHint> allSorted() {
        ArrayList<FlowDurabilityHint> list = new ArrayList<FlowDurabilityHint>(all());
        Collections.sort(list);
        return list;
    }

    @Extension
    public static final class FullyDurable extends FlowDurabilityHint {
        public FullyDurable() {
            super("FULLY_DURABLE", true,  true, "Maximum survivability but slowest " +
                    "Previously the only option.  Able to recover and resume pipelines in many cases even after catastrophic failures.");
        }
    }

    @Extension
    public static final class DurableButNonAtomic extends FlowDurabilityHint {
        public DurableButNonAtomic() {
            super("DURABLE_NONATOMIC", false,  true, "Less survivability, a bit faster. Survives most failures but does not rely on atomic writes to XML files, so data may be lost if writes fail or are interrupted.");
        }
    }

    @Extension
    public static final class SurviveCleanRestart extends FlowDurabilityHint {
        public SurviveCleanRestart() {
            super("SURVIVE_CLEAN_RESTART", false, false, "Performance-optimized. Pipelines resume if Jenkins shuts down cleanly, but running pipelines lose information and can't resume if Jenkins unexpectedly fails.");
        }
    }

    private final String name;

    private final boolean atomicWrite;

    private final boolean persistWithEveryStep;

    private final String description;

    private FlowDurabilityHint(@Nonnull String name, boolean useAtomicWrite, boolean persistWithEveryStep, @Nonnull String description) {
        this.name = name;
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

    public String getDescription() {return  description;}

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object ob) {
        return ob instanceof FlowDurabilityHint && this.getClass().equals(ob.getClass());
    }

    @Override
    public int hashCode() {
        return getClass().toString().hashCode();
    }

    public int compareTo(FlowDurabilityHint hint) {
        if (hint == null || hint == this || hint.getClass() == this.getClass()) {
            return 0;
        }
        if (this.isPersistWithEveryStep() != hint.isPersistWithEveryStep()) {
            return hint.isPersistWithEveryStep() ? -1 : 1;
        } else if (this.isAtomicWrite() != hint.isAtomicWrite()) {
            return (hint.isAtomicWrite()) ? -1 : 1;
        }
        return 0;
    }
}

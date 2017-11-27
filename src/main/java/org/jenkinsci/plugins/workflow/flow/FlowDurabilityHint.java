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

import javax.annotation.Nonnull;
import java.io.Serializable;

/**
 * Provides hints about just how hard we should try to protect our workflow from failures of the master.
 * There is a trade-off between durability and performance, with higher levels carrying much higher overheads to execution.
 * Storage and persistence of data should try to provide at least the specified level (may offer more).
 *
 * <p> Implementation note: all implementations should be static.
 * @author Sam Van Oort
 */
public abstract class FlowDurabilityHint implements ExtensionPoint, Serializable {

    public static ExtensionList<FlowDurabilityHint> all() {
        return Jenkins.getInstance().getExtensionList(FlowDurabilityHint.class);
    }

    @Extension
    public static class FullyDurable extends FlowDurabilityHint {
        private FullyDurable() {
            super("Fully durable", true,  true, "Slowest but safest. Previously the only option.  Able to recover and resume pipelines in many cases even after catastrophic failures.");
        }
    }

    @Extension
    public static class SurviveCleanRestart extends FlowDurabilityHint {
        private SurviveCleanRestart() {
            super("Survive clean restart", false, false, "Fast. Able to resume pipelines if Jenkins shuts down cleanly.");
        }
    }

    private final String name;

    private final boolean atomicWrite;

    private final boolean persistWithEveryStep;

    private final String description;

    protected FlowDurabilityHint(@Nonnull String name, boolean useAtomicWrite, boolean persistWithEveryStep, @Nonnull String description) {
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
}

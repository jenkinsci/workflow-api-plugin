/*
 * The MIT License
 *
 * Copyright 2018 CloudBees, Inc.
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

package org.jenkinsci.plugins.workflow.log;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Describable;
import java.util.List;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

/**
 * Factory interface for {@link LogStorage}.
 */
@Restricted(Beta.class)
public interface LogStorageFactory extends Describable<LogStorageFactory> {

    /**
     * When the current factory has been configured or is considered a default factory {@link #getDefaultFactory()}, returns the expected log storage instance to handle the build.
     * @param b a build about to start
     * @return a mechanism for handling this build, see {@link LogStorage#of(FlowExecutionOwner)}
     */
    @CheckForNull LogStorage forBuild(@NonNull FlowExecutionOwner b);

    default LogStorageFactoryDescriptor<?> getDescriptor() {
        return (LogStorageFactoryDescriptor<?>) Jenkins.get().getDescriptorOrDie(this.getClass());
    }

    static List<LogStorageFactoryDescriptor<?>> all() {
        return Jenkins.get().getDescriptorList(LogStorageFactory.class);
    }

    /**
     * Returns the default {@link LogStorageFactory} based on the descriptor {@code @Extension#ordinal} order and the {@link LogStorageFactoryDescriptor#getDefaultInstance()} implmentations.
     */
    static LogStorageFactory getDefaultFactory() {
        for (LogStorageFactoryDescriptor<?> descriptor : all()) {
            var instance = descriptor.getDefaultInstance();
            if (instance != null) {
                return instance;
            }
        }
        return new FileLogStorageFactory();
    }
}

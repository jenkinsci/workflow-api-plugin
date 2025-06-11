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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.ExtensionList;
import hudson.console.AnnotatedLargeText;
import hudson.console.ConsoleAnnotationOutputStream;
import hudson.model.BuildListener;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.jenkinsci.plugins.workflow.actions.LogAction;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.log.tee.TeeLogStorage;
import org.jenkinsci.plugins.workflow.log.tee.TeeLogStorageFactory;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

/**
 * Means of replacing how logs are stored for a Pipeline build as a whole or for one step.
 * UTF-8 encoding is assumed throughout.
 * @see <a href="https://github.com/jenkinsci/jep/blob/master/jep/210/README.adoc#pluggable-log-storage">JEP-210: Pluggable log storage</a>
 */
@Restricted(Beta.class)
public interface LogStorage {


    /**
     * Provides an alternate way of emitting output from a build.
     * <p>May implement {@link AutoCloseable} to clean up at the end of a build;
     * it may or may not be closed during Jenkins shutdown while a build is running.
     * <p>The caller may wrap the result using {@link TaskListenerDecorator#apply}.
     * @return a (remotable) build listener; do not bother overriding anything except {@link TaskListener#getLogger}
     * @see FlowExecutionOwner#getListener
     */
    @NonNull BuildListener overallListener() throws IOException, InterruptedException;

    /**
     * Provides an alternate way of emitting output from a node (such as a step).
     * <p>May implement {@link AutoCloseable} to clean up at the end of a node ({@link FlowNode#isActive});
     * it may or may not be closed during Jenkins shutdown while a build is running.
     * <p>The caller may wrap the result using {@link TaskListenerDecorator#apply}.
     * @param node a running node
     * @return a (remotable) task listener; do not bother overriding anything except {@link TaskListener#getLogger}
     * @see StepContext#get
     */
    @NonNull TaskListener nodeListener(@NonNull FlowNode node) throws IOException, InterruptedException;

    /**
     * Provides an alternate way of retrieving output from a build.
     * <p>In an {@link AnnotatedLargeText#writeHtmlTo} override, {@link ConsoleAnnotationOutputStream#eol}
     * should apply {@link #startStep} and {@link #endStep} to delineate blocks contributed by steps.
     * (Also see {@link ConsoleAnnotators}.)
     * @param complete if true, we claim to be serving the complete log for a build,
     *                  so implementations should be sure to retrieve final log lines
     * @return a log
     */
    @NonNull AnnotatedLargeText<FlowExecutionOwner.Executable> overallLog(@NonNull FlowExecutionOwner.Executable build, boolean complete);

    /**
     * Introduces an HTML block with a {@code pipeline-node-<ID>} CSS class based on {@link FlowNode#getId}.
     * @see #endStep
     * @see #overallLog
     */
    static @NonNull String startStep(@NonNull String id) {
        return "<span class=\"pipeline-node-" + id + "\">";
    }

    /**
     * Closes an HTML step block.
     * @see #startStep
     * @see #overallLog
     */
    static @NonNull String endStep() {
        return "</span>";
    }

    /**
     * Provides an alternate way of retrieving output from a build.
     * @param node a running node
     * @param complete if true, we claim to be serving the complete log for a node,
     *                  so implementations should be sure to retrieve final log lines
     * @return a log for this just this node
     * @see LogAction
     */
     @NonNull AnnotatedLargeText<FlowNode> stepLog(@NonNull FlowNode node, boolean complete);

     /**
      * Provide a file containing the log text.
      * The default implementation creates a temporary file based on the current contents of {@link #overallLog}.
      * @param build as in {@link #overallLog}
      * @param complete as in {@link #overallLog}
      * @return a possibly temporary file
      * @deprecated Only used for compatibility with {@link Run#getLogFile}.
      */
     @SuppressFBWarnings(value = "REC_CATCH_EXCEPTION", justification = "silly rule")
     @Deprecated
     default @NonNull File getLogFile(@NonNull FlowExecutionOwner.Executable build, boolean complete) {
         try {
             AnnotatedLargeText<FlowExecutionOwner.Executable> logText = overallLog(build, complete);
             FlowExecutionOwner owner = build.asFlowExecutionOwner();
             File f = File.createTempFile("deprecated", ".log", owner != null ? owner.getRootDir() : null);
             f.deleteOnExit();
             try (OutputStream os = new FileOutputStream(f)) {
                 // Similar to Run#writeWholeLogTo but terminates even if !complete:
                 long pos = 0;
                 while (true) {
                     long pos2 = logText.writeRawLogTo(pos, os);
                     if (pos2 <= pos) {
                         break;
                     }
                     pos = pos2;
                 }
             }
             return f;
         } catch (Exception x) {
             Logger.getLogger(LogStorage.class.getName()).log(Level.WARNING, null, x);
             if (build instanceof Run) {
                 return new File(((Run<?, ?>) build).getRootDir(), "log");
             } else {
                 return new File("broken.log"); // not much we can do
             }
         }
     }

    /**
     * Gets the available log storage method for a given build.
     * @param b a build about to start
     * @return the mechanism for handling this build, including any necessary fallback
     * @see LogStorageFactory
     */
    static @NonNull LogStorage of(@NonNull FlowExecutionOwner b) {
        try {
            List<LogStorageFactory> factories = ExtensionList.lookup(LogStorageFactory.class);
            Optional<TeeLogStorage> teeLogStorage = TeeLogStorageFactory.handleFactories(factories, b);
            if (teeLogStorage.isPresent()) {
                return teeLogStorage.get();
            }
            
            for (LogStorageFactory factory : factories) {
                LogStorage storage = factory.forBuild(b);
                if (storage != null) {
                    // Pending integration with JEP-207 / JEP-212, this choice is not persisted.
                    return storage;
                }
            }
            // Similar to Run.getLogFile, but not supporting gzip:
            return FileLogStorage.forFile(new File(b.getRootDir(), "log"));
        } catch (Exception x) {
            return new BrokenLogStorage(x);
        }
    }

    /**
     * Return the primary Log Storage. By default, it's the current implementation.
     * See {@link TeeLogStorage} for overriden implementation.
     */
    default LogStorage getPrimary() {
        return this;
    }

    /**
     * Return a list of secondary Log Storages. Buy default it's an empty list.
     * See {@link TeeLogStorage} for overriden implementation.
     */
    default List<LogStorage> getSecondaries() {
        return List.of();
    }

    /**
     * Wraps the specified {@link OutputStream} with a {@link BuildListener} that automatically buffers and flushes
     * remote writes.
     */
    static @NonNull BuildListener wrapWithRemoteAutoFlushingListener(@NonNull OutputStream os) throws IOException {
        return new BufferedBuildListener(os);
    }

    /**
     * Wraps the specified {@link OutputStream} with a buffer that flushes automatically as needed.
     */
    static @NonNull OutputStream wrapWithAutoFlushingBuffer(@NonNull OutputStream os) throws IOException {
        return new GCFlushedOutputStream(new DelayBufferedOutputStream(os));
    }

}

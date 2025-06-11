/*
 * The MIT License
 *
 * Copyright 2023 CloudBees, Inc.
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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.StreamBuildListener;
import hudson.model.TaskListener;
import hudson.util.LogTaskListener;
import hudson.util.StreamTaskListener;
import java.io.FilterOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.util.BuildListenerAdapter;
import org.apache.commons.io.output.ClosedOutputStream;
import org.jenkinsci.plugins.workflow.log.tee.TeePrintStream;
import org.jenkinsci.plugins.workflow.log.tee.TeeTaskListener;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

/**
 * {@link TaskListener} which can directly return an {@link OutputStream} not wrapped in a {@link PrintStream}.
 * This is important for logging since the error-swallowing behavior of {@link PrintStream} is unwanted,
 * and {@link PrintStream#checkError} is useless.
 */
@Restricted(Beta.class)
public interface OutputStreamTaskListener extends TaskListener {

    /**
     * Returns the {@link OutputStream} from which {@link #getLogger} was constructed.
     */
    @NonNull OutputStream getOutputStream();

    /**
     * Tries to call {@link #getOutputStream} and otherwise falls back to reflective access to {@link PrintStream#out} when possible, at worst returning the {@link PrintStream} itself.
     */
    static @NonNull OutputStream getOutputStream(@NonNull TaskListener listener) {
        if (listener instanceof OutputStreamTaskListener) {
            return ((OutputStreamTaskListener) listener).getOutputStream();
        }
        PrintStream ps = listener.getLogger();
        if (ps.getClass() != PrintStream.class && ps.getClass() != TeePrintStream.class) {
            Logger.getLogger(OutputStreamTaskListener.class.getName()).warning(() -> "Unexpected PrintStream subclass " + ps.getClass().getName() + " which might override write(…); error handling is degraded unless OutputStreamTaskListener is used: " + listener.getClass().getName());
            return ps;
        }
        if (Runtime.version().compareToIgnoreOptional(Runtime.Version.parse("17")) >= 0) {
            boolean core = // nothing to be done about these, though they should not be used in Pipeline build logs anyway
                listener == TaskListener.NULL ||
                listener.getClass() == StreamTaskListener.class ||
                listener.getClass() == LogTaskListener.class ||
                listener.getClass() == StreamBuildListener.class ||
                listener.getClass() == BuildListenerAdapter.class ||
                listener.getClass() == TeeTaskListener.class;
                ;
            Logger.getLogger(OutputStreamTaskListener.class.getName()).log(core ? Level.FINE : Level.WARNING, () -> "On Java 17+ error handling is degraded unless OutputStreamTaskListener is used: " + listener.getClass().getName());
            return ps;
        }
        Field printStreamDelegate;
        try {
            printStreamDelegate = FilterOutputStream.class.getDeclaredField("out");
        } catch (NoSuchFieldException x) {
            Logger.getLogger(OutputStreamTaskListener.class.getName()).log(Level.WARNING, "PrintStream.out defined in Java Platform and protected, so should not happen.", x);
            return ps;
        }
        try {
            printStreamDelegate.setAccessible(true);
        } catch (InaccessibleObjectException x) {
            Logger.getLogger(OutputStreamTaskListener.class.getName()).warning(() -> "Using --illegal-access=deny? Error handling is degraded unless OutputStreamTaskListener is used: " + listener.getClass().getName());
            return ps;
        }
        OutputStream os;
        try {
            os = (OutputStream) printStreamDelegate.get(ps);
        } catch (IllegalAccessException x) {
            Logger.getLogger(OutputStreamTaskListener.class.getName()).log(Level.WARNING, "Unexpected failure to access PrintStream.out", x);
            return ps;
        }
        if (os == null) {
            // like PrintStream.ensureOpen
            return ClosedOutputStream.CLOSED_OUTPUT_STREAM;
        }
        return os;
    }

    /**
     * Convenience implementation handling {@link #getLogger}.
     */
    abstract class Default implements OutputStreamTaskListener {

        private transient PrintStream ps;

        @Override public synchronized PrintStream getLogger() {
            if (ps == null) {
                ps = new PrintStream(getOutputStream(), false, StandardCharsets.UTF_8);
            }
            return ps;
        }

    }

}

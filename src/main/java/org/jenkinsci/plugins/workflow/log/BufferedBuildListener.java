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
import hudson.model.BuildListener;
import hudson.remoting.RemoteOutputStream;
import hudson.util.StreamTaskListener;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.util.Timer;
import org.jenkinsci.remoting.SerializableOnlyOverRemoting;

/**
 * Unlike {@link StreamTaskListener} this does not set {@code autoflush} on the reconstructed {@link PrintStream}.
 * It also implements buffering on output, flushed (without ProxyOutputStream.Flush) at intervals.
 */
final class BufferedBuildListener implements BuildListener, Closeable, SerializableOnlyOverRemoting {

    private static final Logger LOGGER = Logger.getLogger(BufferedBuildListener.class.getName());

    private final OutputStream out;
    @SuppressFBWarnings(value = "SE_BAD_FIELD", justification = "using Replacement anyway, fields here are irrelevant")
    private final PrintStream ps;

    BufferedBuildListener(OutputStream out) throws IOException {
        this.out = out;
        ps = new PrintStream(out, false, "UTF-8");
    }
    
    @Override public PrintStream getLogger() {
        return ps;
    }
    
    @Override public void close() throws IOException {
        ps.close();
    }

    private Object writeReplace() {
        return new Replacement(this);
    }

    private static final class Replacement implements SerializableOnlyOverRemoting {

        private static final long serialVersionUID = 1;

        private final RemoteOutputStream ros;

        Replacement(BufferedBuildListener cbl) {
            this.ros = new RemoteOutputStream(cbl.out);
        }

        private Object readResolve() throws IOException {
            return new BufferedBuildListener(new DelayBufferedOutputStream(ros));
        }

    }

    private static final class DelayBufferedOutputStream extends BufferedOutputStream {

        // TODO make these customizable (not trivial since this system properties would need to be loaded on the master side and then remoted)
        private static final long MIN_RECURRENCE_PERIOD = 250; // ¼s
        private static final long MAX_RECURRENCE_PERIOD = 10_000; // 10s
        private static final float RECURRENCE_PERIOD_BACKOFF = 1.05f;

        private long recurrencePeriod = MIN_RECURRENCE_PERIOD;

        DelayBufferedOutputStream(OutputStream out) {
            super(new FlushControlledOutputStream(out)); // default buffer size: 8Kib
            reschedule();
        }

        private void reschedule() {
            Timer.get().schedule(new Flush(this), recurrencePeriod, TimeUnit.MILLISECONDS);
            recurrencePeriod = Math.min((long) (recurrencePeriod * RECURRENCE_PERIOD_BACKOFF), MAX_RECURRENCE_PERIOD);
        }

        /** We can only call {@link BufferedOutputStream#flushBuffer} via {@link #flush}, but we do not wish to flush the underlying stream, only write out the buffer. */
        private void flushBuffer() throws IOException {
            ThreadLocal<Boolean> enableFlush = ((FlushControlledOutputStream) out).enableFlush;
            boolean orig = enableFlush.get();
            enableFlush.set(false);
            try {
                flush();
            } finally {
                enableFlush.set(orig);
            }
        }
        
        @Override public void close() throws IOException {
            // Ignored. We do not allow the stream to be closed from the remote side.
        }
        
        void run() {
            try {
                flushBuffer();
            } catch (IOException x) {
                LOGGER.log(Level.FINE, null, x);
            }
            reschedule();
        }
        
    }
    
    private static final class Flush implements Runnable {

        /** Since there is no explicit close event, just keep flushing periodically until the stream is collected. */
        private final Reference<DelayBufferedOutputStream> osr;
        
        Flush(DelayBufferedOutputStream os) {
            osr = new WeakReference<>(os);
        }
        
        @Override public void run() {
            DelayBufferedOutputStream os = osr.get();
            if (os != null) {
                os.run();
            }
        }
        
    }

    /** @see DelayBufferedOutputStream#flushBuffer */
    private static final class FlushControlledOutputStream extends FilterOutputStream {

        private final ThreadLocal<Boolean> enableFlush = new ThreadLocal<Boolean>() {
            @Override protected Boolean initialValue() {
                return true;
            }
        };

        FlushControlledOutputStream(OutputStream out) {
            super(out);
        }

        @Override public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len); // super method writes one byte at a time!
        }
        
        @Override public void flush() throws IOException {
            if (enableFlush.get()) {
                super.flush();
            }
        }

    }

}

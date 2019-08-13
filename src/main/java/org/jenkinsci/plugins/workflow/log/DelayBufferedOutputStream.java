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

import java.io.BufferedOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.util.Timer;
import org.jenkinsci.remoting.SerializableOnlyOverRemoting;

/**
 * Buffered output stream which is guaranteed to deliver content after some time even if idle and the buffer does not fill up.
 * The automatic “flushing” does <em>not</em> flush the underlying stream, for example via {@code ProxyOutputStream.Flush}.
 */
final class DelayBufferedOutputStream extends BufferedOutputStream {

    private static final Logger LOGGER = Logger.getLogger(DelayBufferedOutputStream.class.getName());

    static final class Tuning implements SerializableOnlyOverRemoting {
        private Tuning() {}
        // nonfinal for Groovy scripting:
        long minRecurrencePeriod = Long.getLong(DelayBufferedOutputStream.class.getName() + ".minRecurrencePeriod", 1_000); // 1s
        long maxRecurrencePeriod = Long.getLong(DelayBufferedOutputStream.class.getName() + ".maxRecurrencePeriod", 10_000); // 10s
        float recurrencePeriodBackoff = Float.parseFloat(System.getProperty(DelayBufferedOutputStream.class.getName() + ".recurrencePeriodBackoff", "1.05"));
        int bufferSize = Integer.getInteger(DelayBufferedOutputStream.class.getName() + ".bufferSize", 1 << 16); // 64Kib
        static final Tuning DEFAULT = new Tuning();
    }

    private final Tuning tuning;
    private long recurrencePeriod;

    DelayBufferedOutputStream(OutputStream out) {
        this(out, Tuning.DEFAULT);
    }

    DelayBufferedOutputStream(OutputStream out, Tuning tuning) {
        super(new FlushControlledOutputStream(out), tuning.bufferSize);
        this.tuning = tuning;
        recurrencePeriod = tuning.minRecurrencePeriod;
        reschedule();
    }

    private void reschedule() {
        Timer.get().schedule(new Flush(this), recurrencePeriod, TimeUnit.MILLISECONDS);
        recurrencePeriod = Math.min((long) (recurrencePeriod * tuning.recurrencePeriodBackoff), tuning.maxRecurrencePeriod);
    }

    /** We can only call {@link BufferedOutputStream#flushBuffer} via {@link #flush}, but we do not wish to flush the underlying stream, only write out the buffer. */
    private void flushBuffer() throws IOException {
        ThreadLocal<Boolean> enableFlush = ((FlushControlledOutputStream) out).enableFlush;
        enableFlush.set(false);
        try {
            flush();
        } finally {
            // This method is always called from a thread in the jenkins.util.Timer thread pool. We want to avoid
            // leaking ThreadLocals on these long-lived threads (see JENKINS-58899), and we do not care about
            // maintaining the value from call to call, since we only set it to false here for the duration of
            // flush, and leave it as true in all other cases and on all other threads.
            enableFlush.remove();
        }
    }

    void flushAndReschedule() {
        // TODO as an optimization, avoid flushing the buffer if it was recently flushed anyway due to filling up
        try {
            flushBuffer();
        } catch (IOException x) {
            LOGGER.log(Level.FINE, null, x);
        }
        reschedule();
    }

    @Override public String toString() {
        return "DelayBufferedOutputStream[" + out + "]";
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
                os.flushAndReschedule();
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

        @Override public String toString() {
            return "FlushControlledOutputStream[" + out + "]";
        }

    }

}

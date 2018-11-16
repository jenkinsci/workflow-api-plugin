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
import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.util.Timer;
import org.jenkinsci.remoting.SerializableOnlyOverRemoting;

/**
 * Buffered output stream which is guaranteed to deliver content after some time even if idle and the buffer does not fill up.
 * The automatic “flushing” does <em>not</em> flush the underlying stream, for example via {@code ProxyOutputStream.Flush}.
 * Also the stream will be flushed before garbage collection.
 * Otherwise it is similar to {@link BufferedOutputStream}.
 */
final class DelayBufferedOutputStream extends FilterOutputStream {

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

    /**
     * The interesting state of the buffered stream, kept as a separate object so that {@link FlushRef} can hold on to it.
     */
    private static final class Buffer {

        final OutputStream out;
        private final byte[] dat;
        private int pos;

        Buffer(OutputStream out, int size) {
            this.out = out;
            dat = new byte[size];
        }

        synchronized void drain() throws IOException {
            if (pos == 0) {
                return;
            }
            out.write(dat, 0, pos);
            pos = 0;
        }

        void write(int b) throws IOException {
            assert Thread.holdsLock(this);
            if (pos == dat.length) {
                drain();
            }
            dat[pos++] = (byte) b;
        }

        synchronized void write(byte[] b, int off, int len) throws IOException {
            for (int i = 0; i < len; i++) {
                write(b[off + i]);
            }
        }

    }

    private final Buffer buf;
    private final Tuning tuning;
    private long recurrencePeriod;

    DelayBufferedOutputStream(OutputStream out) {
        this(out, Tuning.DEFAULT);
    }

    DelayBufferedOutputStream(OutputStream out, Tuning tuning) {
        super(out);
        buf = new Buffer(out, tuning.bufferSize);
        this.tuning = tuning;
        recurrencePeriod = tuning.minRecurrencePeriod;
        FlushRef.register(this);
        reschedule();
    }

    @Override public void write(int b) throws IOException {
        synchronized (buf) {
            buf.write(b);
        }
    }

    @Override public void write(byte[] b, int off, int len) throws IOException {
        buf.write(b, off, len);
    }

    @Override public void flush() throws IOException {
        buf.drain();
        super.flush();
    }

    private void reschedule() {
        Timer.get().schedule(new Drain(this), recurrencePeriod, TimeUnit.MILLISECONDS);
        recurrencePeriod = Math.min((long) (recurrencePeriod * tuning.recurrencePeriodBackoff), tuning.maxRecurrencePeriod);
    }

    void drainAndReschedule() {
        // TODO as an optimization, avoid flushing the buffer if it was recently flushed anyway due to filling up
        try {
            buf.drain();
        } catch (IOException x) {
            LOGGER.log(Level.FINE, null, x);
        }
        reschedule();
    }

    private static final class Drain implements Runnable {

        /** Since there is no explicit close event, just keep flushing periodically until the stream is collected. */
        private final Reference<DelayBufferedOutputStream> osr;

        Drain(DelayBufferedOutputStream os) {
            osr = new WeakReference<>(os);
        }

        @Override public void run() {
            DelayBufferedOutputStream os = osr.get();
            if (os != null) {
                os.drainAndReschedule();
            }
        }

    }

    /**
     * Flushes streams prior to garbage collection.
     * In Java 9+ could use {@code java.util.Cleaner} instead.
     */
    private static final class FlushRef extends PhantomReference<DelayBufferedOutputStream> {

        private static final ReferenceQueue<DelayBufferedOutputStream> rq = new ReferenceQueue<>();

        static {
            Timer.get().scheduleWithFixedDelay(() -> {
                while (true) {
                    FlushRef ref = (FlushRef) rq.poll();
                    if (ref == null) {
                        break;
                    }
                    LOGGER.log(Level.FINE, "flushing {0} from a DelayBufferedOutputStream", ref.buf.out);
                    try {
                        ref.buf.drain();
                        ref.buf.out.flush();
                    } catch (IOException x) {
                        LOGGER.log(Level.WARNING, null, x);
                    }
                }
            }, 0, 10, TimeUnit.SECONDS);
        }

        static void register(DelayBufferedOutputStream dbos) {
            new FlushRef(dbos, rq).enqueue();
        }

        private final Buffer buf;

        private FlushRef(DelayBufferedOutputStream dbos, ReferenceQueue<DelayBufferedOutputStream> rq) {
            super(dbos, rq);
            this.buf = dbos.buf;
        }

    }

}

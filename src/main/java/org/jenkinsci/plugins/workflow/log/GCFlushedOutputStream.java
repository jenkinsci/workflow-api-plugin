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
import java.lang.ref.ReferenceQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.util.Timer;

/**
 * A stream which will be flushed before garbage collection.
 * {@link BufferedOutputStream} does not do this automatically.
 */
final class GCFlushedOutputStream extends FilterOutputStream {
    
    private static final Logger LOGGER = Logger.getLogger(GCFlushedOutputStream.class.getName());

    GCFlushedOutputStream(OutputStream out) {
        super(out);
        FlushRef.register(this, out);
    }

    @Override public void write(byte[] b, int off, int len) throws IOException {
        out.write(b, off, len); // super method is surprising
    }

    @Override public String toString() {
        return "GCFlushedOutputStream[" + out + "]";
    }

    /**
     * Flushes streams prior to garbage collection.
     * ({@link BufferedOutputStream} does not do this automatically.)
     * TODO Java 9+ could use java.util.Cleaner
     */
    private static final class FlushRef extends PhantomReference<GCFlushedOutputStream> {

        private static final ReferenceQueue<GCFlushedOutputStream> rq = new ReferenceQueue<>();

        static {
            Timer.get().scheduleWithFixedDelay(() -> {
                while (true) {
                    FlushRef ref = (FlushRef) rq.poll();
                    if (ref == null) {
                        break;
                    }
                    LOGGER.log(Level.FINE, "flushing {0}", ref.out);
                    try {
                        ref.out.flush();
                    } catch (IOException x) {
                        LOGGER.log(Level.WARNING, null, x);
                    }
                }
            }, 0, 10, TimeUnit.SECONDS);
        }

        static void register(GCFlushedOutputStream fos, OutputStream out) {
            new FlushRef(fos, out, rq).enqueue();
        }

        private final OutputStream out;

        private FlushRef(GCFlushedOutputStream fos, OutputStream out, ReferenceQueue<GCFlushedOutputStream> rq) {
            super(fos, rq);
            this.out = out;
        }

    }

}

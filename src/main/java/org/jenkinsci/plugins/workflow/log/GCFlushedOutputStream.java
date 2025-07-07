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
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.remoting.ChannelClosedException;
import hudson.remoting.DaemonThreadFactory;
import hudson.remoting.NamingThreadFactory;
import java.io.EOFException;
import java.lang.ref.Cleaner;
import java.nio.channels.ClosedChannelException;
import java.util.stream.Stream;

/**
 * A stream which will be flushed before garbage collection.
 * {@link BufferedOutputStream} does not do this automatically.
 */
final class GCFlushedOutputStream extends FilterOutputStream {
    
    private static final Logger LOGGER = Logger.getLogger(GCFlushedOutputStream.class.getName());
    private static final Cleaner CLEANER = Cleaner.create(
            new NamingThreadFactory(new DaemonThreadFactory(), GCFlushedOutputStream.class.getName() + ".CLEANER"));

    static boolean DISABLED = Boolean.getBoolean(GCFlushedOutputStream.class.getName() + ".DISABLED");

    GCFlushedOutputStream(OutputStream out) {
        this(out, DISABLED);
    }

    GCFlushedOutputStream(OutputStream out, boolean disabled) {
        super(out);
        if (!disabled) {
            CLEANER.register(this, new CleanerTask(out));
        }
    }

    @Override public void write(@NonNull byte[] b, int off, int len) throws IOException {
        out.write(b, off, len); // super method is surprising
    }

    @Override public String toString() {
        return "GCFlushedOutputStream[" + out + "]";
    }

    // TODO https://github.com/jenkinsci/remoting/pull/657
    private static boolean isClosedChannelException(Throwable t) {
        if (t instanceof ClosedChannelException) {
            return true;
        } else if (t instanceof ChannelClosedException) {
            return true;
        } else if (t instanceof EOFException) {
            return true;
        } else if (t == null) {
            return false;
        } else {
            return isClosedChannelException(t.getCause()) || Stream.of(t.getSuppressed()).anyMatch(GCFlushedOutputStream::isClosedChannelException);
        }
    }

    /**
     * Flushes streams prior to garbage collection.
     * ({@link BufferedOutputStream} does not do this automatically.)
     */
    private static final class CleanerTask implements Runnable {
        private final OutputStream out;

        public CleanerTask(OutputStream out) {
            this.out = out;
        }

        @Override
        public void run() {
            LOGGER.log(Level.FINE, "flushing {0}", out);
            try {
                out.flush();
            } catch (IOException x) {
                LOGGER.log(isClosedChannelException(x) ? Level.FINE : Level.WARNING, null, x);
            }
        }
    }

}

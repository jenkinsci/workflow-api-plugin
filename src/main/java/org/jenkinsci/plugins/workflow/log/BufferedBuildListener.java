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

import hudson.CloseProofOutputStream;
import hudson.model.BuildListener;
import hudson.remoting.Channel;
import hudson.remoting.ChannelClosedException;
import hudson.remoting.RemoteOutputStream;
import hudson.util.StreamTaskListener;
import java.io.Closeable;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.logging.Logger;
import org.jenkinsci.remoting.SerializableOnlyOverRemoting;

/**
 * Unlike {@link StreamTaskListener} this does not set {@code autoflush} on the reconstructed {@link PrintStream}.
 * It also wraps on the remote side in {@link DelayBufferedOutputStream}.
 */
final class BufferedBuildListener extends OutputStreamTaskListener.Default implements BuildListener, Closeable, SerializableOnlyOverRemoting {

    private static final Logger LOGGER = Logger.getLogger(BufferedBuildListener.class.getName());

    private final OutputStream out;

    BufferedBuildListener(OutputStream out) {
        this.out = out;
    }

    @Override public OutputStream getOutputStream() {
        return out;
    }

    @Override public void close() throws IOException {
        getLogger().close();
    }

    private Object writeReplace() {
        return new Replacement(this);
    }

    private static final class Replacement implements SerializableOnlyOverRemoting {

        private static final long serialVersionUID = 1;

        private final RemoteOutputStream ros;
        private final DelayBufferedOutputStream.Tuning tuning = DelayBufferedOutputStream.Tuning.DEFAULT; // load defaults on controller

        Replacement(BufferedBuildListener cbl) {
            this.ros = new RemoteOutputStream(new CloseProofOutputStream(cbl.out));
        }

        private Object readResolve() {
            var cos = new CloseableOutputStream(new GCFlushedOutputStream(new DelayBufferedOutputStream(ros, tuning)));
            Channel.currentOrFail().addListener(new Channel.Listener() {
                @Override public void onClosed(Channel channel, IOException cause) {
                    LOGGER.fine(() -> "closing " + channel.getName());
                    cos.close(channel, cause);
                }
            });
            return new BufferedBuildListener(cos);
        }

    }

    /**
     * Output stream which throws {@link ChannelClosedException} when appropriate.
     * Otherwise callers could continue trying to write to {@link DelayBufferedOutputStream}
     * long after {@link Channel#isClosingOrClosed} without errors.
     * In the case of {@code org.jenkinsci.plugins.durabletask.Handler.output},
     * this is actively harmful since it would mean that writes apparently succeed
     * and {@code last-location.txt} would move forward even though output was lost.
     */
    private static final class CloseableOutputStream extends FilterOutputStream {

        /** non-null if closed */
        private Channel channel;
        /** optional close cause */
        private IOException cause;

        CloseableOutputStream(OutputStream delegate) {
            super(delegate);
        }

        void close(Channel channel, IOException cause) {
            this.channel = channel;
            this.cause = cause;
            // Do not call close(): ProxyOutputStream.doClose would just throw ChannelClosedException: …: channel is already closed
        }

        private void checkClosed() throws IOException {
            if (channel != null) {
                throw new ChannelClosedException(channel, cause);
            }
            LOGGER.finer("not closed yet");
        }

        @Override public void write(int b) throws IOException {
            checkClosed();
            out.write(b);
        }

        @Override public void write(byte[] b, int off, int len) throws IOException {
            checkClosed();
            out.write(b, off, len);
        }

        @Override public String toString() {
            return "CloseableOutputStream[" + out + "]";
        }

    }

}

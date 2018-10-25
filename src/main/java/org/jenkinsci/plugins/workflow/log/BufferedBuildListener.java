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
import hudson.CloseProofOutputStream;
import hudson.model.BuildListener;
import hudson.remoting.RemoteOutputStream;
import hudson.util.StreamTaskListener;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import org.jenkinsci.remoting.SerializableOnlyOverRemoting;

/**
 * Unlike {@link StreamTaskListener} this does not set {@code autoflush} on the reconstructed {@link PrintStream}.
 * It also wraps on the remote side in {@link DelayBufferedOutputStream}.
 */
final class BufferedBuildListener implements BuildListener, Closeable, SerializableOnlyOverRemoting {

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
            this.ros = new RemoteOutputStream(new CloseProofOutputStream(cbl.out));
        }

        private Object readResolve() throws IOException {
            return new BufferedBuildListener(new DelayBufferedOutputStream(ros));
        }

    }

}

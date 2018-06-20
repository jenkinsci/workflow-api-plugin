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

import com.google.common.base.Charsets;
import com.google.common.primitives.Bytes;
import hudson.console.AnnotatedLargeText;
import hudson.console.ConsoleAnnotationOutputStream;
import hudson.console.ConsoleAnnotator;
import hudson.console.LineTransformationOutputStream;
import hudson.model.BuildListener;
import hudson.model.StreamBuildListener;
import hudson.model.TaskListener;
import hudson.util.ByteArrayOutputStream2;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.CountingInputStream;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.framework.io.ByteBuffer;

/**
 * Simple implementation of log storage that intermixes node and general messages in a single log bytestream.
 */
@Restricted(Beta.class)
public abstract class StreamLogStorage implements LogStorage {

    private static final Logger LOGGER = Logger.getLogger(StreamLogStorage.class.getName());

    /**
     * Division between a {@link FlowNode#getId} and a regular log line.
     * Could use anything, but nicer to use something visually distinct from typical output,
     * and unlikely to be produced by non-step output such as SCM loading.
     */
    @Restricted(NoExternalUse.class)
    public static final String NODE_ID_SEP = "¦";

    private static final byte[] INFIX = NODE_ID_SEP.getBytes(StandardCharsets.UTF_8);

    private static byte[] prefix(FlowNode node) {
        return prefix(node.getId());
    }

    private static byte[] prefix(String id) {
        return (id + NODE_ID_SEP).getBytes(StandardCharsets.UTF_8);
    }

    public static LogStorage forFile(File file, FlowExecutionOwner b) {
        return new StreamLogStorage(b) {
            @Override protected OutputStream write() throws IOException, InterruptedException {
                return new FileOutputStream(file, true);
            }
            @Override
            protected InputStream read() throws IOException {
                return new FileInputStream(file);
            }
            // useless to specify the file size since we anyway need to process the text to strip node ID annotations
        };
    }

    private final FlowExecutionOwner b;

    private BuildListener listener;

    protected StreamLogStorage(FlowExecutionOwner b) {
        this.b = b;
    }

    protected abstract OutputStream write() throws IOException, InterruptedException;

    protected abstract InputStream read() throws IOException;

    @Override public synchronized BuildListener overallListener() throws IOException, InterruptedException {
        if (listener == null) {
            listener = new StreamBuildListener(write(), StandardCharsets.UTF_8);
        }
        return listener;
    }

    @Override public TaskListener nodeListener(FlowNode node) throws IOException, InterruptedException {
        return new DecoratedTaskListener(overallListener(), prefix(node));
    }

    /** For testing. */
    static @Nonnull TaskListener decorate(@Nonnull TaskListener raw, @Nonnull String id) {
        return new DecoratedTaskListener(raw, prefix(id));
    }

    private static class DecoratedTaskListener implements TaskListener {
        private static final long serialVersionUID = 1;
        /**
         * The listener we are delegating to, which was expected to be remotable.
         * Note that we ignore all of its methods other than {@link TaskListener#getLogger}.
         */
        private final @Nonnull TaskListener delegate;
        private final @Nonnull byte[] prefix;
        private transient PrintStream logger;
        DecoratedTaskListener(TaskListener delegate, byte[] prefix) {
            this.delegate = delegate;
            this.prefix = prefix;
        }
        @SuppressWarnings("deprecation")
        @Override public PrintStream getLogger() {
            if (logger == null) {
                final OutputStream initial = new BufferedOutputStream(delegate.getLogger());
                OutputStream decorated = new LineTransformationOutputStream() {
                    @Override protected void eol(byte[] b, int len) throws IOException {
                        synchronized (initial) { // to match .println etc.
                            initial.write(prefix);
                            initial.write(b, 0, len);
                            initial.flush();
                        }
                    }
                };
                try {
                    logger = new PrintStream(decorated, false, "UTF-8");
                } catch (UnsupportedEncodingException x) {
                    throw new AssertionError(x);
                }
            }
            return logger;
        }
    }

    @Override public AnnotatedLargeText<FlowExecutionOwner.Executable> overallLog(FlowExecutionOwner.Executable build, boolean complete) {
        return new StreamLargeText(build, complete);
    }

    private final class StreamLargeText extends AnnotatedLargeText<FlowExecutionOwner.Executable> {

        private final FlowExecutionOwner.Executable context;

        StreamLargeText(FlowExecutionOwner.Executable context, boolean completed) {
            this(context, new HackedByteBuffer(), completed);
        }

        private StreamLargeText(FlowExecutionOwner.Executable context, HackedByteBuffer buf, boolean completed) {
            super(buf, Charsets.UTF_8, completed, context);
            // TODO for simplicitly, currently just making a copy of the log into a memory buffer.
            // Overriding writeLogTo would work to strip annotations from plain-text console output more efficiently,
            // though it would be cumbersome to also override all the other LargeText methods, esp. doProgressText.
            // (We could also override ByteBuffer to stream output after stripping, but the length would be wrong, if anyone cares.)
            try (InputStream log = read(); CountingInputStream cis = new CountingInputStream(log)) {
                strip(cis, buf);
                buf.length = cis.getByteCount();
            } catch (IOException x) {
                Logger.getLogger(StreamLargeText.class.getName()).log(Level.SEVERE, null, x);
                // TODO rather return BrokenAnnotatedLargeText
            }
            this.context = context;
        }

        // It does *not* work to override writeHtmlTo to strip node annotations after ConsoleNote’s are processed:
        // AbstractMarkupText.wrapBy and similar routinely put the close tag on the next line,
        // since the marked-up text includes the newline.
        // Thus we would be trying to parse, e.g., "123¦<span class='red'>Some headline\n</span>123¦Regular line\n"
        // and it is not necessarily obvious where the boundaries of the ID are.
        // Anyway annotateHtml is an easier way of handling node annotations.
        @Override public long writeHtmlTo(long start, Writer w) throws IOException {
            ConsoleAnnotationOutputStream<FlowExecutionOwner.Executable> caw = annotateHtml(
                    w, ConsoleAnnotators.createAnnotator(context), context);
            FlowExecutionOwner owner = context.asFlowExecutionOwner();
            assert owner != null;
            long r;
            // Yes this calls read() twice; for the current implementation we assume that this is less expensive than caching in heap.
            try (InputStream log = read()) {
                IOUtils.skipFully(log, start);
                CountingInputStream cis = new CountingInputStream(log);
                IOUtils.copy(cis, caw);
                r = start + cis.getByteCount();
            }
            ConsoleAnnotators.setAnnotator(caw.getConsoleAnnotator());
            return r;
        }

    }

    /** Records length of the raw log file, so that {@link AnnotatedLargeText#doProgressText} does not think we have blown past the end. */
    private static class HackedByteBuffer extends ByteBuffer {
        long length;
        @Override public long length() {
            return Math.max(length, super.length());
        }
    }

    /**
     * Copies a “raw” log decorated with node annotations to a sink with no such annotations.
     */
    private static void strip(InputStream decorated, OutputStream stripped) throws IOException {
        InputStream buffered = new BufferedInputStream(decorated);
        ByteArrayOutputStream2 baos = new ByteArrayOutputStream2();
        READ:
        while (true) {
            int c = buffered.read();
            if (c == -1) {
                break;
            }
            baos.write(c);
            if (c == '\n') {
                byte[] buf = baos.getBuffer();
                int len = baos.size();
                int idx = Bytes.indexOf(buf, INFIX);
                int restLen = len - idx - INFIX.length;
                if (idx == -1 || /* would be nice if indexOf took a bound */ restLen < 0) {
                    stripped.write(buf, 0, len);
                } else {
                    stripped.write(buf, idx + INFIX.length, restLen);
                }
                baos.reset();
            }
        }
    }

    /**
     * Decorates an HTML stream with output coming from nodes wrapped in start/end step HTML.
     */
    static <T> ConsoleAnnotationOutputStream<T> annotateHtml(Writer out, ConsoleAnnotator<? super T> ann, T context) {
        return new NodeConsoleAnnotationOutputStream<>(out, ann, context);
    }
    private static class NodeConsoleAnnotationOutputStream<T> extends ConsoleAnnotationOutputStream<T> {
        private final Writer out;
        private String currentId;
        NodeConsoleAnnotationOutputStream(Writer out, ConsoleAnnotator<? super T> ann, T context) {
            super(out, ann, context, StandardCharsets.UTF_8);
            this.out = out;
        }
        @Override protected void eol(byte[] in, int sz) throws IOException {
            if (sz < 0 || sz > in.length) {
                throw new IllegalArgumentException(sz + " vs. " + in.length);
            }
            String id = null;
            int idx = Bytes.indexOf(in, INFIX);
            int skip = idx + INFIX.length;
            if (idx != -1 && skip <= sz) {
                id = new String(in, 0, idx, StandardCharsets.UTF_8);
                if (!id.equals(currentId)) {
                    if (currentId != null) {
                        out.write(LogStorage.endStep());
                    }
                    out.write(LogStorage.startStep(id));
                }
                in = Arrays.copyOfRange(in, skip, sz);
                sz -= skip;
                assert sz >= 0 && sz <= in.length;
            } else if (currentId != null) {
                out.write(LogStorage.endStep());
            }
            super.eol(in, sz);
            currentId = id;
        }
    }

    @Override public AnnotatedLargeText<FlowNode> stepLog(FlowNode node, boolean complete) {
        try (ByteBuffer buf = new ByteBuffer();
             InputStream whole = read();
             InputStream wholeBuffered = new BufferedInputStream(whole);
             ByteArrayOutputStream2 baos = new ByteArrayOutputStream2();) {
            byte[] prefix = prefix(node);
            READ: while (true) {
                int c = wholeBuffered.read();
                if (c == -1) {
                    break;
                }
                baos.write(c);
                if (c == '\n') {
                    byte[] linebuf = baos.getBuffer();
                    int len = baos.size();
                    if (len >= prefix.length) {
                        boolean matches = true;
                        for (int i = 0; i < prefix.length; i++) {
                            if (linebuf[i] != prefix[i]) {
                                matches = false;
                                break;
                            }
                        }
                        if (matches) {
                            // This line in fact belongs to our node, so copy it out.
                            buf.write(linebuf, prefix.length, len - prefix.length);
                        }
                    }
                    baos.reset();
                }
            }
            return new AnnotatedLargeText<>(buf, StandardCharsets.UTF_8, complete, node);
        } catch (IOException x) {
            LOGGER.log(Level.WARNING, null, x);
            return new BrokenLogStorage(x).stepLog(node, complete);
        }
    }

}

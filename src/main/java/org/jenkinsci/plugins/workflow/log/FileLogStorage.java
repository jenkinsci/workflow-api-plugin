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

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.console.AnnotatedLargeText;
import hudson.console.ConsoleAnnotationOutputStream;
import hudson.model.BuildListener;
import hudson.model.TaskListener;
import java.io.BufferedReader;
import java.io.EOFException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.io.input.NullReader;
import org.apache.commons.io.output.CountingOutputStream;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;
import org.kohsuke.stapler.framework.io.ByteBuffer;
import org.kohsuke.stapler.framework.io.LargeText;

/**
 * Simple implementation of log storage in a single file that maintains a side file with an index indicating where node transitions occur.
 * Each line in the index file is a byte offset, optionally followed by a space and then a node ID.
 */
/* Note: Avoid FileChannel methods in this class, as they close the channel and its parent stream if the thread is
   interrupted, which is problematic given that we do not control the threads which write to the log file.
*/
@Restricted(Beta.class)
public final class FileLogStorage implements LogStorage {

    private static final Logger LOGGER = Logger.getLogger(FileLogStorage.class.getName());

    private static final Map<File, FileLogStorage> openStorages = Collections.synchronizedMap(new HashMap<>());

    public static synchronized LogStorage forFile(File log) {
        return openStorages.computeIfAbsent(log, FileLogStorage::new);
    }

    private final File log;
    private final File index;
    private FileOutputStream os;
    @SuppressFBWarnings(value = "IS2_INCONSISTENT_SYNC", justification = "actually it is always accessed within the monitor")
    private long osStartPosition;
    @SuppressFBWarnings(value = "IS2_INCONSISTENT_SYNC", justification = "actually it is always accessed within the monitor")
    private CountingOutputStream cos;
    @SuppressFBWarnings(value = "IS2_INCONSISTENT_SYNC", justification = "we only care about synchronizing writes")
    private OutputStream bos;
    private Writer indexOs;
    private String lastId;

    private FileLogStorage(File log) {
        this.log = log;
        this.index = new File(log + "-index");
    }

    private synchronized void open() throws IOException {
        if (os == null) {
            os = new FileOutputStream(log, true);
            osStartPosition = log.length();
            cos = new CountingOutputStream(os);
            bos = LogStorage.wrapWithAutoFlushingBuffer(cos);
            if (index.isFile()) {
                try (BufferedReader r = Files.newBufferedReader(index.toPath(), StandardCharsets.UTF_8)) {
                    // TODO would be faster to scan the file backwards for the penultimate \n, then convert the byte sequence from there to EOF to UTF-8 and set lastId accordingly
                    String lastLine = null;
                    while (true) {
                        // Note that BufferedReader tolerates final lines without a line separator, so if for some reason the last write has been truncated this result could be incorrect.
                        // In practice this seems unlikely since we explicitly flush after the newline, so we should be sending a single small block to the filesystem to persist.
                        // Anyway at worst the result would be a (perhaps temporarily) incorrect line → step mapping, which is tolerable for one step of one build, and barely affects the overall build log.
                        String line = r.readLine();
                        if (line == null) {
                            break;
                        } else {
                            lastLine = line;
                        }
                    }
                    if (lastLine != null) {
                        int space = lastLine.indexOf(' ');
                        lastId = space == -1 ? null : lastLine.substring(space + 1);
                    }
                }
            }
            indexOs = new OutputStreamWriter(new FileOutputStream(index, true), StandardCharsets.UTF_8);
        }
    }

    @NonNull
    @Override public BuildListener overallListener() throws IOException {
        return LogStorage.wrapWithRemoteAutoFlushingListener(new IndexOutputStream(null));
    }

    @NonNull
    @Override public TaskListener nodeListener(@NonNull FlowNode node) throws IOException {
        return LogStorage.wrapWithRemoteAutoFlushingListener(new IndexOutputStream(node.getId()));
    }

    private void checkId(String id) throws IOException {
        assert Thread.holdsLock(this);
        if (!Objects.equals(id, lastId)) {
            bos.flush();
            long pos = osStartPosition + cos.getByteCount();
            if (id == null) {
                indexOs.write(pos + "\n");
            } else {
                indexOs.write(pos + " " + id + "\n");
            }
            // Could call FileChannel.force(true) like hudson.util.FileChannelWriter does for AtomicFileWriter,
            // though making index-log writes slower is likely a poor tradeoff for slightly more reliable log display,
            // since logs are often never read and this is transient data rather than configuration or valuable state.
            indexOs.flush();
            lastId = id;
        }
    }

    private final class IndexOutputStream extends OutputStream {

        private final String id;

        IndexOutputStream(String id) throws IOException {
            this.id = id;
            open();
        }

        @Override public void write(int b) throws IOException {
            synchronized (FileLogStorage.this) {
                checkId(id);
                bos.write(b);
            }
        }

        @Override public void write(@NonNull byte[] b) throws IOException {
            synchronized (FileLogStorage.this) {
                checkId(id);
                bos.write(b);
            }
        }

        @Override public void write(@NonNull byte[] b, int off, int len) throws IOException {
            synchronized (FileLogStorage.this) {
                checkId(id);
                bos.write(b, off, len);
            }
        }

        @Override public void flush() throws IOException {
            bos.flush();
        }

        @Override public void close() throws IOException {
            if (id == null) {
                openStorages.remove(log);
                try {
                    bos.close();
                } finally {
                    indexOs.close();
                }
            }
        }

    }

    private void maybeFlush() {
        if (bos != null) {
            try {
                bos.flush();
            } catch (IOException x) {
                LOGGER.log(Level.WARNING, "failed to flush " + log, x);
            }
        }
    }

    @NonNull
    @Override public AnnotatedLargeText<FlowExecutionOwner.Executable> overallLog(@NonNull FlowExecutionOwner.Executable build, boolean complete) {
        maybeFlush();
        return new AnnotatedLargeText<FlowExecutionOwner.Executable>(log, StandardCharsets.UTF_8, complete, build) {
            @Override public long writeHtmlTo(long start, Writer w) throws IOException {
                try (BufferedReader indexBR = index.isFile() ? Files.newBufferedReader(index.toPath(), StandardCharsets.UTF_8) : new BufferedReader(new NullReader(0))) {
                    ConsoleAnnotationOutputStream<FlowExecutionOwner.Executable> caos = new ConsoleAnnotationOutputStream<>(w, ConsoleAnnotators.createAnnotator(build), build, StandardCharsets.UTF_8);
                    long r = this.writeRawLogTo(start, new FilterOutputStream(caos) {
                        // To insert startStep/endStep annotations into the overall log, we need to simultaneously read index-log.
                        // We use the standard LargeText.FileSession to get the raw log text (we need not think about ConsoleNote here), having seeked to the start position.
                        // Then we read index-log in order, looking for transitions from one step to the next (or to or from non-step overall output).
                        // Whenever we are about to write a byte which is at a boundary, or if there is a boundary at EOF, the HTML annotations are injected into the output;
                        // the read of index-log is advanced lazily (it is not necessary to have the whole mapping in memory).
                        long lastTransition = -1;
                        boolean eof; // NullReader is strict and throws IOException (not EOFException) if you read() again after having already gotten -1
                        String lastId;
                        long pos = start;
                        boolean hadLastId;
                        @Override public void write(int b) throws IOException {
                            while (lastTransition < pos && !eof) {
                                String line = indexBR.readLine();
                                if (line == null) {
                                    eof = true;
                                    break;
                                }
                                int space = line.indexOf(' ');
                                try {
                                    lastTransition = Long.parseLong(space == -1 ? line : line.substring(0, space));
                                } catch (NumberFormatException x) {
                                    LOGGER.warning("Ignoring corrupt index file " + index);
                                }
                                lastId = space == -1 ? null : line.substring(space + 1);
                            }
                            if (pos == lastTransition) {
                                if (hadLastId) {
                                    w.write(LogStorage.endStep());
                                }
                                hadLastId = lastId != null;
                                if (lastId != null) {
                                    w.write(LogStorage.startStep(lastId));
                                }
                            }
                            super.write(b);
                            pos++;
                        }
                        @Override public void flush() throws IOException {
                            if (lastId != null) {
                                w.write(LogStorage.endStep());
                            }
                            super.flush();
                        }
                    });
                    ConsoleAnnotators.setAnnotator(caos.getConsoleAnnotator());
                    return r;
                }
            }
        };
    }

    @NonNull
    @Override public AnnotatedLargeText<FlowNode> stepLog(@NonNull FlowNode node, boolean complete) {
        maybeFlush();
        long rawLogSize;
        long stepLogSize = 0;
        String nodeId = node.getId();
        try (RandomAccessFile raf = new RandomAccessFile(log, "r")) {
            // Check this _before_ reading index-log to reduce the chance of a race condition resulting in recent content being associated with the wrong step.
            rawLogSize = raf.length();
            if (index.isFile()) {
                try (IndexReader idr = new IndexReader(rawLogSize, nodeId)) {
                    stepLogSize = idr.getStepLogSize();
                }
            }
        } catch (IOException x) {
            return new BrokenLogStorage(x).stepLog(node, complete);
        }
        if (stepLogSize == 0) {
            return new AnnotatedLargeText<>(new ByteBuffer(), StandardCharsets.UTF_8, complete, node);
        }
        return new AnnotatedLargeText<>(new StreamingStepLog(rawLogSize, stepLogSize, nodeId), StandardCharsets.UTF_8, complete, node);
    }

    private class IndexReader implements AutoCloseable {
        static class Next {
            public long start = -1;
            public long end = -1;
        }
        private final String nodeId;
        private final long rawLogSize;
        private boolean done;
        private BufferedReader indexBR = null;
        private long pos = -1; // -1 if not currently in this node, start position if we are

        public IndexReader(long rawLogSize, String nodeId) {
            this.rawLogSize = rawLogSize;
            this.nodeId = nodeId;
        }

        public void close() throws IOException {
            if (indexBR != null) {
                indexBR.close();
                indexBR = null;
            }
        }

        private void ensureOpen() throws IOException {
            if (indexBR == null) {
                indexBR = Files.newBufferedReader(index.toPath(), StandardCharsets.UTF_8);
            }
        }

        public long getStepLogSize() throws IOException {
            long stepLogSize = 0;
            Next next = new Next();
            while (readNext(next)) {
                stepLogSize += (next.end - next.start);
            }
            return stepLogSize;
        }

        public boolean readNext(Next next) throws IOException {
            if (done) return false;
            ensureOpen();
            while (!done) {
                String line = indexBR.readLine();
                if (line == null) {
                    done = true;
                    break;
                }
                int space = line.indexOf(' ');
                long nextTransition;
                try {
                    nextTransition = Long.parseLong(space == -1 ? line : line.substring(0, space));
                } catch (NumberFormatException x) {
                    LOGGER.warning("Ignoring corrupt index file " + index);
                    // If index-log is corrupt for whatever reason, we given up on this step in this build;
                    // there is no way we would be able to produce accurate output anyway.
                    // Note that NumberFormatException is nonfatal in the case of the overall build log:
                    // the whole-build HTML output always includes exactly what is in the main log file,
                    // at worst with some missing or inaccurate startStep/endStep annotations.
                    pos = -1;
                    continue;
                }
                if (nextTransition >= rawLogSize) {
                    // Do not emit positions past the previously determined logSize.
                    nextTransition = rawLogSize;
                    done = true;
                }
                if (pos == -1) {
                    if (space != -1 && line.substring(space + 1).equals(nodeId)) {
                        pos = nextTransition;
                    }
                } else if (nextTransition > pos) {
                    next.start = pos;
                    next.end = nextTransition;
                    pos = -1;
                    return true;
                } else {
                    // Some sort of mismatch. Do not emit this section.
                    pos = -1;
                }
            }
            if (pos != -1 && rawLogSize > pos) {
                // In case the build is ongoing and we are still actively writing content for this step,
                // we will hit EOF before any other transition. Otherwise identical to normal case above.
                next.start = pos;
                next.end = rawLogSize;
                return true;
            }
            return false;
        }
    }

    private class StreamingStepLog implements LargeText.Source {
        private final String nodeId;
        private final long rawLogSize;
        private final long stepLogSize;

        StreamingStepLog(long rawLogSize, long stepLogSize, String nodeId ) {
            super();
            this.rawLogSize = rawLogSize;
            this.stepLogSize = stepLogSize;
            this.nodeId = nodeId;
        }

        public boolean exists() {
            return true;
        }

        public long length() {
            return stepLogSize;
        }

        public LargeText.Session open() {
            return new StreamingStepLogSession();
        }

        class StreamingStepLogSession extends InputStream implements LargeText.Session {
            private RandomAccessFile rawLog;
            private final IndexReader.Next next = new IndexReader.Next();
            private IndexReader indexReader;
            private long rawLogPos = next.end;
            private long stepLogPos = 0;

            @Override
            public void close() throws IOException {
                try {
                    if (rawLog != null) {
                        rawLog.close();
                        rawLog = null;
                    }
                } finally {
                    if (indexReader != null) {
                        indexReader.close();
                        indexReader = null;
                    }
                }
            }

            @Override
            public long skip(long n) throws IOException {
                if (stepLogPos + n > stepLogSize) {
                    return 0;
                }
                if (n == 0) return 0;

                ensureOpen();
                long skipped = 0;
                while (skipped < n) {
                    advanceNextIfNeeded(false);
                    long remainingInNext = next.end - rawLogPos;
                    long remainingToSkip = n - skipped;
                    long skip = Long.min(remainingInNext, remainingToSkip);
                    rawLogPos += skip;
                    stepLogPos += skip;
                    skipped += skip;
                }
                rawLog.seek(rawLogPos);
                return skipped;
            }

            @Override
            public int read() throws IOException {
                byte[] b = new byte[1];
                int n = read(b, 0, 1);
                if (n != 1) return -1;
                return (int) b[0];
            }

            @Override
            public int read(@NonNull byte[] b) throws IOException {
                return read(b, 0, b.length);
            }

            @Override
            public int read(@NonNull byte[] b, int off, int len) throws IOException {
                if (stepLogPos == stepLogSize) {
                    return -1;
                }
                ensureOpen();
                advanceNextIfNeeded(true);
                long remaining = next.end - rawLogPos;
                if (len > remaining) {
                    // len is an int and remaining is smaller, so no overflow is possible.
                    len = (int) remaining;
                }
                int n = rawLog.read(b, off, len);
                rawLogPos += n;
                stepLogPos += n;
                return n;
            }

            private void advanceNextIfNeeded(boolean seek) throws IOException {
                if (rawLogPos < next.end) return;
                if (!indexReader.readNext(next)) {
                    throw new EOFException("index truncated; did not reach previously discovered end of step log");
                }
                if (seek) rawLog.seek(next.start);
                rawLogPos = next.start;
            }

            private void ensureOpen() throws IOException {
                if (rawLog == null) {
                    rawLog = new RandomAccessFile(log, "r");
                }
                if (indexReader == null) {
                    indexReader = new IndexReader(rawLogSize, nodeId);
                }
            }
        }
    }

    @Deprecated
    @NonNull
    @Override public File getLogFile(@NonNull FlowExecutionOwner.Executable build, boolean complete) {
        return log;
    }

}

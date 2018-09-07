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
import hudson.console.AnnotatedLargeText;
import hudson.console.ConsoleAnnotationOutputStream;
import hudson.model.BuildListener;
import hudson.model.StreamBuildListener;
import hudson.model.TaskListener;
import hudson.util.StreamTaskListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
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
import java.util.logging.Logger;
import org.apache.commons.io.input.NullReader;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;
import org.kohsuke.stapler.framework.io.ByteBuffer;

/**
 * Simple implementation of log storage in a single file that maintains a side file with an index indicating where node transitions occur.
 * Each line in the index file is a byte offset, optionally followed by a space and then a node ID.
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
    @SuppressFBWarnings(value = "IS2_INCONSISTENT_SYNC", justification = "FB apparently gets confused by what the lock is, and anyway we only care about synchronizing writes")
    private FileOutputStream os;
    private Writer indexOs;
    private String lastId;

    private FileLogStorage(File log) {
        this.log = log;
        this.index = new File(log + "-index");
    }

    private synchronized void open() throws IOException {
        if (os == null) {
            os = new FileOutputStream(log, true);
            if (index.isFile()) {
                try (BufferedReader r = Files.newBufferedReader(index.toPath(), StandardCharsets.UTF_8)) {
                    String lastLine = null;
                    while (true) {
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

    @Override public BuildListener overallListener() throws IOException, InterruptedException {
        return new StreamBuildListener(new IndexOutputStream(null), StandardCharsets.UTF_8);
    }

    @Override public TaskListener nodeListener(FlowNode node) throws IOException, InterruptedException {
        return new StreamTaskListener(new IndexOutputStream(node.getId()), StandardCharsets.UTF_8);
    }

    private void checkId(String id) throws IOException {
        assert Thread.holdsLock(this);
        if (!Objects.equals(id, lastId)) {
            long pos = os.getChannel().position();
            if (id == null) {
                indexOs.write(pos + "\n");
            } else {
                indexOs.write(pos + " " + id + "\n");
            }
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
                os.write(b);
            }
        }

        @Override public void write(byte[] b) throws IOException {
            synchronized (FileLogStorage.this) {
                checkId(id);
                os.write(b);
            }
        }

        @Override public void write(byte[] b, int off, int len) throws IOException {
            synchronized (FileLogStorage.this) {
                checkId(id);
                os.write(b, off, len);
            }
        }

        @Override public void flush() throws IOException {
            os.flush();
        }

        @Override public void close() throws IOException {
            if (id == null) {
                openStorages.remove(log);
                try {
                    os.close();
                } finally {
                    indexOs.close();
                }
            }
        }

    }

    @Override public AnnotatedLargeText<FlowExecutionOwner.Executable> overallLog(FlowExecutionOwner.Executable build, boolean complete) {
        return new AnnotatedLargeText<FlowExecutionOwner.Executable>(log, StandardCharsets.UTF_8, complete, build) {
            @Override public long writeHtmlTo(long start, Writer w) throws IOException {
                try (BufferedReader indexBR = index.isFile() ? Files.newBufferedReader(index.toPath(), StandardCharsets.UTF_8) : new BufferedReader(new NullReader(0))) {
                    ConsoleAnnotationOutputStream<FlowExecutionOwner.Executable> caos = new ConsoleAnnotationOutputStream<>(w, ConsoleAnnotators.createAnnotator(build), build, StandardCharsets.UTF_8);
                    long r = this.writeRawLogTo(start, new FilterOutputStream(caos) {
                        long lastTransition = -1;
                        boolean eof;
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

    @Override public AnnotatedLargeText<FlowNode> stepLog(FlowNode node, boolean complete) {
        String id = node.getId();
        try (ByteBuffer buf = new ByteBuffer();
             RandomAccessFile raf = new RandomAccessFile(log, "r");
             BufferedReader indexBR = index.isFile() ? Files.newBufferedReader(index.toPath(), StandardCharsets.UTF_8) : new BufferedReader(new NullReader(0))) {
            String line;
            long pos = -1; // -1 if not currently in this node, start position if we are
            while ((line = indexBR.readLine()) != null) {
                int space = line.indexOf(' ');
                long lastTransition = -1;
                try {
                    lastTransition = Long.parseLong(space == -1 ? line : line.substring(0, space));
                } catch (NumberFormatException x) {
                    break; // corrupt index file; forget it
                }
                if (pos == -1) {
                    if (space != -1 && line.substring(space + 1).equals(id)) {
                        pos = lastTransition;
                    }
                } else {
                    raf.seek(pos);
                    if (lastTransition > pos + Integer.MAX_VALUE) {
                        throw new IOException("Cannot read more than 2Gib at a time"); // ByteBuffer does not support it anyway
                    }
                    // TODO can probably be done a bit more efficiently with FileChannel methods
                    byte[] data = new byte[(int) (lastTransition - pos)];
                    raf.readFully(data);
                    buf.write(data);
                    pos = -1;
                }
            }
            if (pos != -1) {
                raf.seek(pos);
                long end = raf.length();
                if (end > pos + Integer.MAX_VALUE) {
                    throw new IOException("Cannot read more than 2Gib at a time");
                }
                byte[] data = new byte[(int) (end - pos)];
                raf.readFully(data);
                buf.write(data);
            }
            return new AnnotatedLargeText<>(buf, StandardCharsets.UTF_8, complete, node);
        } catch (IOException x) {
            return new BrokenLogStorage(x).stepLog(node, complete);
        }
    }

}

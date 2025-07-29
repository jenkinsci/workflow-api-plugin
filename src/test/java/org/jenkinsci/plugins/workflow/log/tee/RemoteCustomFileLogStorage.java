package org.jenkinsci.plugins.workflow.log.tee;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.console.AnnotatedLargeText;
import hudson.console.LineTransformationOutputStream;
import hudson.model.BuildListener;
import hudson.model.TaskListener;
import hudson.remoting.RemoteOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.log.LogStorage;
import org.jenkinsci.plugins.workflow.log.OutputStreamTaskListener;
import org.jenkinsci.remoting.SerializableOnlyOverRemoting;

/**
 * LogStorage acting as a FileLogStorage without the index.
 * When serialized over remoting, transorms all the characters to uppercase.
 */
public class RemoteCustomFileLogStorage implements LogStorage {
    private static final Logger LOGGER = Logger.getLogger(RemoteCustomFileLogStorage.class.getName());

    private final File log;
    private OutputStream out;
    private OutputStreamSupplier supplier;

    private static final Map<File, RemoteCustomFileLogStorage> openStorages =
            Collections.synchronizedMap(new HashMap<>());

    public static synchronized LogStorage forFile(File log) {
        return forFile(log, null);
    }

    public static synchronized LogStorage forFile(File log, OutputStreamSupplier supplier) {
        return openStorages.computeIfAbsent(log, key -> new RemoteCustomFileLogStorage(key, supplier));
    }

    private RemoteCustomFileLogStorage(File log) {
        this(log, null);
    }
    
    @FunctionalInterface
    public interface OutputStreamSupplier{
        OutputStream apply() throws IOException;
    }
    
    private RemoteCustomFileLogStorage(File log, OutputStreamSupplier supplier) {
        this.log = log;
        this.supplier = supplier;
    }

    private synchronized void open() throws IOException {
        if (this.supplier == null) {
            this.out = new FileOutputStream(log, true);
            return;
        }
        this.out = supplier.apply();
    }

    @Override
    @NonNull
    public BuildListener overallListener() throws IOException, InterruptedException {
        return new MyListener(new Writer(null));
    }

    @Override
    @NonNull
    public TaskListener nodeListener(FlowNode node) throws IOException, InterruptedException {
        // TODO: Does not actually handle step logs differently.
        return new MyListener(new Writer(node.getId()));
    }

    private static final class MyListener extends OutputStreamTaskListener.Default implements BuildListener, Closeable {
        private static final long serialVersionUID = 1;
        private final OutputStream listenerOut;

        public MyListener(OutputStream listenerOut) {
            this.listenerOut = listenerOut;
        }

        @Override
        @NonNull
        public OutputStream getOutputStream() {
            return listenerOut;
        }

        @Override
        public void close() throws IOException {
            getLogger().close();
        }

        private Object writeReplace() throws IOException {
            return new MyListener(new UppercaseWriter(listenerOut));
        }
    }

    private final class Writer extends OutputStream implements SerializableOnlyOverRemoting {
        private final String node;

        public Writer(String node) throws IOException {
            this.node = node;
            open();
        }

        @Override
        public void write(int b) throws IOException {
            synchronized (RemoteCustomFileLogStorage.this) {
                out.write(b);
            }
        }

        @Override
        public void write(@NonNull byte[] b, int off, int len) throws IOException {
            synchronized (RemoteCustomFileLogStorage.this) {
                out.write(b, off, len);
            }
        }

        @Override
        public void flush() throws IOException {
            out.flush();
        }

        @Override
        public void close() throws IOException {
            if (node == null) {
                openStorages.remove(log);
                out.close();
            }
        }
    }

    private static final class UppercaseWriter extends LineTransformationOutputStream
            implements SerializableOnlyOverRemoting {
        private static final long serialVersionUID = 1L;
        private final RemoteOutputStream out;

        public UppercaseWriter(OutputStream out) {
            this.out = new RemoteOutputStream(out);
        }

        @Override
        protected void eol(byte[] b, int len) throws IOException {
            String line = new String(b, 0, len, StandardCharsets.UTF_8);
            var uppercaseLine = line.toUpperCase(Locale.ENGLISH);
            var uppercaseBytes = uppercaseLine.getBytes(StandardCharsets.UTF_8);
            out.write(uppercaseBytes);
        }

        @Override
        public void flush() throws IOException {
            out.flush();
        }

        @Override
        public void close() throws IOException {
            out.close();
        }
    }

    @NonNull
    @Override
    public AnnotatedLargeText<FlowExecutionOwner.Executable> overallLog(
            @NonNull FlowExecutionOwner.Executable build, boolean complete) {
        return new AnnotatedLargeText<>(log, StandardCharsets.UTF_8, complete, build);
    }

    @NonNull
    @Override
    public AnnotatedLargeText<FlowNode> stepLog(@NonNull FlowNode node, boolean complete) {
        // TODO: Does not actually handle step logs differently.
        return new AnnotatedLargeText<>(log, StandardCharsets.UTF_8, complete, node);
    }
}

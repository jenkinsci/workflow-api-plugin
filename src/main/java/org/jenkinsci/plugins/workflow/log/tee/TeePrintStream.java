package org.jenkinsci.plugins.workflow.log.tee;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class TeePrintStream extends PrintStream {

    final transient TeeLogStorage teeLogStorage;
    final List<PrintStream> secondaries;

    TeePrintStream(@NonNull TeeLogStorage teeLogStorage, @NonNull PrintStream primary, PrintStream... secondaries) {
        super(primary, false, StandardCharsets.UTF_8);
        this.secondaries = List.of(secondaries);
        this.teeLogStorage = teeLogStorage;
    }

    @Override
    public void flush() {
        synchronized (synced()) {
            super.flush();
            for (PrintStream secondary : secondaries) {
                secondary.flush();
            }
        }
    }

    @Override
    public void close() {
        synchronized (synced()) {
            RuntimeException e1 = null;
            try {
                super.close();
            } catch (RuntimeException e) {
                e1 = e;
            }
            RuntimeException e2 = null;
            for (PrintStream secondary : secondaries) {
                try {
                    secondary.close();
                } catch (RuntimeException e) {
                    e2 = e;
                    break;
                }
            }
            if (e1 != null && e2 != null) {
                throw new RuntimeException(
                        "All print streams failed to close: primary=" + e1 + ", secondary=" + e2, e1);
            } else if (e1 != null) {
                throw e1;
            } else if (e2 != null) {
                throw e2;
            }
        }
    }

    @Override
    public boolean checkError() {
        return super.checkError()
                && secondaries.stream().map(PrintStream::checkError).reduce(true, (a, b) -> a && b);
    }

    @Override
    public void write(int b) {
        synchronized (synced()) {
            super.write(b);
            for (PrintStream secondary : secondaries) {
                secondary.write(b);
            }
        }
    }

    @Override
    public void write(@NonNull byte[] buf, int off, int len) {
        synchronized (synced()) {
            super.write(buf, off, len);
            for (PrintStream secondary : secondaries) {
                secondary.write(buf, off, len);
            }
        }
    }

    private Object synced() {
        return teeLogStorage == null ? this : teeLogStorage;
    }
}

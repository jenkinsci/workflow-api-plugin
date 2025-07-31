package org.jenkinsci.plugins.workflow.log.tee;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

class TeeOutputStream extends OutputStream {

    final OutputStream primary;
    final List<OutputStream> secondaries;

    TeeOutputStream(OutputStream primary, OutputStream[] secondaries) {
        this.primary = primary;
        this.secondaries = List.of(secondaries);
    }

    @Override
    public void write(int b) throws IOException {
        handleAction(outputStream -> outputStream.write(b));
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        handleAction(outputStream -> outputStream.write(b, off, len));
    }

    @Override
    public void flush() throws IOException {
        handleAction(OutputStream::flush);
    }

    @Override
    public void close() throws IOException {
        handleAction(OutputStream::close);
    }

    @FunctionalInterface
    private interface ActionFunction<T> {
        void apply(T t) throws IOException;
    }

    private void handleAction(ActionFunction<OutputStream> function) throws IOException {
        IOException exception = null;
        try {
            function.apply(primary);
        } catch (IOException e) {
            exception = e;
        }
        for (OutputStream secondary : secondaries) {
            try {
                function.apply(secondary);
            } catch (IOException e) {
                if (exception == null) {
                    exception = e;
                } else {
                    exception.addSuppressed(e);
                }
            }
        }
        if (exception != null) {
            throw exception;
        }
    }
}

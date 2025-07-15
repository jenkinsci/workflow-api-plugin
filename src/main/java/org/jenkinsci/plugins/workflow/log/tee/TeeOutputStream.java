package org.jenkinsci.plugins.workflow.log.tee;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

class TeeOutputStream extends OutputStream {

    final transient TeeLogStorage teeLogStorage;
    final OutputStream primary;
    final List<OutputStream> secondaries;

    TeeOutputStream(TeeLogStorage teeLogStorage, OutputStream primary, OutputStream[] secondaries) {
        this.teeLogStorage = teeLogStorage;
        this.primary = primary;
        this.secondaries = List.of(secondaries);
    }

    @Override
    public void write(int b) throws IOException {
        synchronized (teeLogStorage) {
            IOException exception = null;
            primary.write(b);
            for (OutputStream secondary : secondaries) {
                try {
                    secondary.write(b);
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

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        synchronized (teeLogStorage) {
            IOException exception = null;
            primary.write(b, off, len);
            for (OutputStream secondary : secondaries) {
                try {
                    secondary.write(b, off, len);
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

    @Override
    public void flush() throws IOException {
        synchronized (teeLogStorage) {
            IOException exception = null;
            primary.flush();
            for (OutputStream secondary : secondaries) {
                try {
                    secondary.flush();
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

    @Override
    public void close() throws IOException {
        synchronized (teeLogStorage) {
            IOException exception = null;
            primary.close();
            for (OutputStream secondary : secondaries) {
                try {
                    secondary.close();
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
}

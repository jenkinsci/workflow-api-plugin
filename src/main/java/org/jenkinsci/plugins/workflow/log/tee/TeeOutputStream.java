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
        primary.write(b);
        for (OutputStream secondary : secondaries) {
            secondary.write(b);
        }
    }

    @Override
    public void write(byte[] b) throws IOException {
        primary.write(b);
        for (OutputStream secondary : secondaries) {
            secondary.write(b);
        }
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        primary.write(b, off, len);
        for (OutputStream secondary : secondaries) {
            secondary.write(b, off, len);
        }
    }

    @Override
    public void flush() throws IOException {
        primary.flush();
        for (OutputStream secondary : secondaries) {
            secondary.flush();
        }
    }

    @Override
    public void close() throws IOException {
        primary.close();
        for (OutputStream secondary : secondaries) {
            secondary.close();
        }
    }
}

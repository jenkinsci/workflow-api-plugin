package org.jenkinsci.plugins.workflow.log.tee;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.BuildListener;
import hudson.model.TaskListener;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Serial;
import java.util.List;
import org.jenkinsci.plugins.workflow.log.OutputStreamTaskListener;

class TeeBuildListener implements BuildListener, OutputStreamTaskListener, AutoCloseable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final BuildListener primary;

    private final List<BuildListener> secondaries;

    private transient OutputStream outputStream;

    private transient PrintStream printStream;

    TeeBuildListener(BuildListener primary, BuildListener... secondaries) {
        if (!(primary instanceof OutputStreamTaskListener)) {
            throw new ClassCastException("Primary is not an instance of OutputStreamTaskListener: " + primary);
        }
        List.of(secondaries).forEach(secondary -> {
            if (!(secondary instanceof OutputStreamTaskListener)) {
                throw new ClassCastException("Secondary is not an instance of OutputStreamTaskListener: " + secondary);
            }
        });

        this.primary = primary;
        this.secondaries = List.of(secondaries);
    }

    @NonNull
    @Override
    public synchronized OutputStream getOutputStream() {
        if (outputStream == null) {
            outputStream = new TeeOutputStream(
                    ((OutputStreamTaskListener) primary).getOutputStream(),
                    secondaries.stream()
                            .map(secondary -> ((OutputStreamTaskListener) secondary).getOutputStream())
                            .toArray(OutputStream[]::new));
        }
        return outputStream;
    }

    @NonNull
    @Override
    public synchronized PrintStream getLogger() {
        if (printStream == null) {
            printStream = new TeePrintStream(
                    primary.getLogger(),
                    secondaries.stream().map(TaskListener::getLogger).toArray(PrintStream[]::new));
        }
        return printStream;
    }

    @Override
    public void close() throws Exception {
        if (primary instanceof AutoCloseable) {
            ((AutoCloseable) primary).close();
        }
        for (BuildListener secondary : secondaries) {
            if (secondary instanceof AutoCloseable) {
                ((AutoCloseable) secondary).close();
            }
        }
    }
}

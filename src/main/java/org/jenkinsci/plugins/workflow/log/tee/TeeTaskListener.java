/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jenkinsci.plugins.workflow.log.tee;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.BuildListener;
import hudson.model.TaskListener;
import java.io.Closeable;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class TeeTaskListener implements BuildListener, AutoCloseable {

    private static final Logger logger = Logger.getLogger(TeeTaskListener.class.getName());

    final TaskListener primary;
    final TaskListener[] secondaries;

    public TeeTaskListener(TaskListener primary, TaskListener... secondaries) {
        this.primary = primary;
        this.secondaries = secondaries;
    }

    @NonNull
    @Override
    public PrintStream getLogger() {
        return new TeePrintStream(
                primary.getLogger(),
                Arrays.stream(secondaries).map(TaskListener::getLogger).toArray(PrintStream[]::new));
    }

    @Override
    public void close() throws IOException {
        logger.log(Level.FINEST, "close()");
        if (primary instanceof Closeable) {
            ((Closeable) primary).close();
        }
        for (TaskListener secondary : secondaries) {
            if (secondary instanceof Closeable) {
                ((Closeable) secondary).close();
            }
        }
    }

    @Override
    public String toString() {
        return "TeeBuildListener[" + primary + "," + secondaries + "]";
    }
}

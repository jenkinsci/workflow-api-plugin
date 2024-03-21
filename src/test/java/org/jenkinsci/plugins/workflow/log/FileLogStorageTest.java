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

import static org.junit.Assert.assertTrue;

import hudson.model.TaskListener;
import java.io.File;
import java.util.logging.Level;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.LoggerRule;

public class FileLogStorageTest extends LogStorageTestBase {

    @ClassRule public static LoggerRule fineLogging = new LoggerRule();
    @Rule public TemporaryFolder tmp = new TemporaryFolder();
    private File log;

    @Before public void log() throws Exception {
        log = tmp.newFile();
    }

    @Override protected LogStorage createStorage() {
        return FileLogStorage.forFile(log);
    }

    @Test public void oldFormat() throws Exception {
        LogStorage ls = createStorage();
        TaskListener overall = ls.overallListener();
        overall.getLogger().println("stuff");
        close(overall);
        assertTrue(new File(log + "-index").delete());
        assertOverallLog(0, lines("stuff"), true);
    }

    @Test public void interruptionDoesNotCloseStream() throws Exception {
        fineLogging.record(FileLogStorage.class, Level.FINE);
        LogStorage ls = createStorage();
        TaskListener overall = ls.overallListener();
        overall.getLogger().println("overall 1");
        Thread.currentThread().interrupt();
        TaskListener stepLog = ls.nodeListener(new MockNode("1"));
        stepLog.getLogger().println("step 1");
        assertTrue(Thread.interrupted());
        close(stepLog);
        overall.getLogger().println("overall 2");
        close(overall);
        assertOverallLog(0, lines("overall 1", "<span class=\"pipeline-node-1\">step 1", "</span>overall 2"), true);
    }

}

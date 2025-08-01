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

import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.TaskListener;
import java.io.File;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class FileLogStorageTest extends LogStorageTestBase {

    @TempDir(cleanup = CleanupMode.NEVER)
    private File tmp;
    private File log;

    @BeforeEach
    @Override
    void setUp(JenkinsRule rule) throws Exception {
        super.setUp(rule);
        log = File.createTempFile("junit", null, tmp);
    }

    @Override
    protected LogStorage createStorage() {
        return FileLogStorage.forFile(log);
    }

    @Test
    void oldFormat() throws Exception {
        LogStorage ls = createStorage();
        TaskListener overall = ls.overallListener();
        overall.getLogger().println("stuff");
        close(overall);
        assertTrue(new File(log + "-index").delete());
        assertOverallLog(0, lines("stuff"), true);
    }

    @Test
    void interruptionDoesNotCloseStream() throws Exception {
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

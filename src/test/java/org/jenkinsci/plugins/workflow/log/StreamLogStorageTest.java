/*
 * The MIT License
 *
 * Copyright 2017 CloudBees, Inc.
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

import hudson.console.ConsoleAnnotator;
import hudson.model.TaskListener;
import hudson.util.StreamTaskListener;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.IOUtils;
import static org.junit.Assert.*;
import org.junit.Test;

public class StreamLogStorageTest {

    @Test public void coalescing() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        TaskListener raw = new StreamTaskListener(baos);
        raw.getLogger().println("General output.");
        TaskListener output1 = StreamLogStorage.decorate(raw, "1");
        output1.getLogger().println("Step one, first line.");
        output1.getLogger().println("Step one, second line.");
        TaskListener output2 = StreamLogStorage.decorate(raw, "2");
        output2.getLogger().println("Step two, first line.");
        output2.getLogger().println("Step two, second line.");
        output2.getLogger().println("Step two, third line.");
        output1.getLogger().println("More from step one.");
        raw.getLogger().println("End of build.");
        StringWriter sw = new StringWriter();
        IOUtils.copy(new ByteArrayInputStream(baos.toByteArray()), StreamLogStorage.annotateHtml(sw, ConsoleAnnotator.initial(null), null));
        assertEquals(
            "General output.\n" +
            "<span class=\"pipeline-node-1\">Step one, first line.\n" +
            "Step one, second line.\n" +
            "</span><span class=\"pipeline-node-2\">Step two, first line.\n" +
            "Step two, second line.\n" +
            "Step two, third line.\n" +
            "</span><span class=\"pipeline-node-1\">More from step one.\n" +
            "</span>End of build.\n",
            sw.toString().replace("\r\n", "\n"));
    }

    /**
     * Checks what happens when code using {@link TaskListener#getLogger} prints a line with inadequate synchronization.
     * Normally you use something like {@link PrintWriter#println(String)} which synchronizes and so delivers a complete line.
     * Failures to do this can cause output from different steps (or general build output) to be interleaved at a sub-line level.
     * This will not render well, but we need to ensure that the entire build log is not broken as a result.
     */
    @Test public void mangledLines() throws Exception {
        StringWriter sw = new StringWriter();
        IOUtils.copy(new ByteArrayInputStream((
            "General output.\n" +
            "1¦Step one, 2¦Step two, some line.\n" +
            "another line.\n" +
            "End of build.\n").getBytes(StandardCharsets.UTF_8)), StreamLogStorage.annotateHtml(sw, ConsoleAnnotator.initial(null), null));
        assertEquals(
            "General output.\n" +
            "<span class=\"pipeline-node-1\">Step one, 2¦Step two, some line.\n" +
            "</span>another line.\n" +
            "End of build.\n",
            sw.toString().replace("\r\n", "\n"));
    }

    // TODO test missing final newline

}

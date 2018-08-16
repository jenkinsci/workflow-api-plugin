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

import hudson.console.AnnotatedLargeText;
import hudson.console.HyperlinkNote;
import hudson.model.TaskListener;
import java.io.EOFException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.function.BiFunction;
import jenkins.security.ConfidentialStore;
import org.apache.commons.io.output.NullOutputStream;
import org.apache.commons.io.output.NullWriter;
import org.apache.commons.io.output.WriterOutputStream;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Foundation for compliance tests of {@link LogStorage} implementations.
 */
public abstract class LogStorageTestBase {

    static {
        System.setProperty("line.separator", "\n");
    }

    /** Needed since {@link ConsoleAnnotators} will not work without encryption, and currently {@link ConfidentialStore#get} has no fallback mode for unit tests accessible except via package-local. */
    @ClassRule public static JenkinsRule r = new JenkinsRule();

    /** Create a new storage implementation, but potentially reusing any data initialized in the last {@link Before} setup. */
    protected abstract LogStorage createStorage() throws Exception;

    @Test public void smokes() throws Exception {
        LogStorage ls = createStorage();
        TaskListener overall = ls.overallListener();
        overall.getLogger().println("starting");
        TaskListener step1 = ls.nodeListener(new MockNode("1"));
        step1.getLogger().println("one #1");
        TaskListener step2 = ls.nodeListener(new MockNode("2"));
        step2.getLogger().println("two #1");
        long betweenStep2Lines = text().writeHtmlTo(0, new NullWriter());
        step2.getLogger().println("two #2");
        overall.getLogger().println("interrupting");
        /* We do not really care much whether nodes are annotated when we start display in the middle; the UI will not do anything with it anyway:
        assertOverallLog(betweenStep2Lines, "<span class=\"pipeline-node-2\">two #2\n</span>interrupting\n", true);
        */
        long overallHtmlPos = assertOverallLog(0, "starting\n<span class=\"pipeline-node-1\">one #1\n</span><span class=\"pipeline-node-2\">two #1\ntwo #2\n</span>interrupting\n", true);
        assertEquals(overallHtmlPos, assertOverallLog(overallHtmlPos, "", true));
        assertLength(overallHtmlPos);
        try { // either tolerate OOB, or not
            assertOverallLog(999, "", true);
            assertOverallLog(999, "", false);
        } catch (EOFException x) {}
        long step1Pos = assertStepLog("1", 0, "one #1\n", true);
        long step2Pos = assertStepLog("2", 0, "two #1\ntwo #2\n", true);
        step1.getLogger().println("one #2");
        step1.getLogger().println("one #3");
        overall.getLogger().println("pausing");
        overallHtmlPos = assertOverallLog(overallHtmlPos, "<span class=\"pipeline-node-1\">one #2\none #3\n</span>pausing\n", true);
        step1Pos = assertStepLog("1", step1Pos, "one #2\none #3\n", true);
        assertLength("1", step1Pos);
        try { // as above
            assertStepLog("1", 999, "", true);
            assertStepLog("1", 999, "", false);
        } catch (EOFException x) {}
        step2Pos = assertStepLog("2", step2Pos, "", true);
        ((AutoCloseable) overall).close();
        ls = createStorage();
        overall = ls.overallListener();
        overall.getLogger().println("resuming");
        step1 = ls.nodeListener(new MockNode("1"));
        step1.getLogger().println("one #4");
        TaskListener step3 = ls.nodeListener(new MockNode("3"));
        step3.getLogger().println("three #1");
        overall.getLogger().println("ending");
        ((AutoCloseable) overall).close();
        overallHtmlPos = assertOverallLog(overallHtmlPos, "resuming\n<span class=\"pipeline-node-1\">one #4\n</span><span class=\"pipeline-node-3\">three #1\n</span>ending\n", true);
        assertEquals(overallHtmlPos, assertOverallLog(overallHtmlPos, "", true));
        assertLength(overallHtmlPos);
        step1Pos = assertStepLog("1", step1Pos, "one #4\n", true);
        assertLength("1", step1Pos);
        assertStepLog("1", 0, "one #1\none #2\none #3\none #4\n", false);
        step2Pos = assertStepLog("2", step2Pos, "", true);
        assertStepLog("3", 0, "three #1\n", true);
        ls = createStorage();
        TaskListener step4 = ls.nodeListener(new MockNode("4"));
        step4.getLogger().println(HyperlinkNote.encodeTo("http://nowhere.net/", "nikde"));
        ((AutoCloseable) overall).close();
        long step4Pos = assertStepLog("4", 0, "<a href='http://nowhere.net/'>nikde</a>\n", true);
        assertLength("4", step4Pos);
        overall = ls.overallListener();
        overall.getLogger().println("really ending");
        ((AutoCloseable) overall).close();
        overallHtmlPos = assertOverallLog(overallHtmlPos, "<span class=\"pipeline-node-4\"><a href='http://nowhere.net/'>nikde</a>\n</span>really ending\n", true);
        assertEquals(overallHtmlPos, assertOverallLog(overallHtmlPos, "", true));
        assertLength(overallHtmlPos);
    }

    /**
     * Checks what happens when code using {@link TaskListener#getLogger} prints a line with inadequate synchronization.
     * Normally you use something like {@link PrintWriter#println(String)} which synchronizes and so delivers a complete line.
     * Failures to do this can cause output from different steps (or general build output) to be interleaved at a sub-line level.
     * This might not render well (depending on the implementation), but we need to ensure that the entire build log is not broken as a result.
     */
    @Test public void mangledLines() throws Exception {
        Random r = new Random();
        BiFunction<Character, TaskListener, Thread> thread = (c, l) -> new Thread(() -> {
            for (int i = 0; i < 1000; i++) {
                l.getLogger().print(c);
                if (r.nextDouble() < 0.1) {
                    l.getLogger().println();
                }
                if (r.nextDouble() < 0.1) {
                    try {
                        Thread.sleep(r.nextInt(10));
                    } catch (InterruptedException x) {
                        x.printStackTrace();
                    }
                }
            }
        });
        List<Thread> threads = new ArrayList<>();
        LogStorage ls = createStorage();
        threads.add(thread.apply('.', ls.overallListener()));
        threads.add(thread.apply('1', ls.nodeListener(new MockNode("1"))));
        threads.add(thread.apply('2', ls.nodeListener(new MockNode("2"))));
        threads.forEach(Thread::start);
        threads.forEach(t -> {
            try {
                t.join();
            } catch (InterruptedException x) {
                x.printStackTrace();
            }
        });
        long pos = text().writeHtmlTo(0, new NullWriter());
        // TODO detailed assertions would need to take into account completion flag:
        // assertLength(pos);
        // assertOverallLog(pos, "", true);
        text().writeRawLogTo(0, new NullOutputStream());
        pos = text("1").writeHtmlTo(0, new NullWriter());
        // assertLength("1", pos);
        // assertStepLog("1", pos, "", true);
        text("1").writeRawLogTo(0, new NullOutputStream());
        pos = text("2").writeHtmlTo(0, new NullWriter());
        // assertLength("2", pos);
        // assertStepLog("2", pos, "", true);
        text("2").writeRawLogTo(0, new NullOutputStream());
    }

    // TODO test missing final newline

    private long assertOverallLog(long start, String expected, boolean html) throws Exception {
        return assertLog(() -> text(), start, expected, html, html);
    }

    private long assertStepLog(String id, long start, String expected, boolean html) throws Exception {
        return assertLog(() -> text(id), start, expected, html, false);
    }

    private long assertLog(Callable<AnnotatedLargeText<?>> text, long start, String expected, boolean html, boolean coalesceSpans) throws Exception {
        long pos = start;
        StringWriter sw = new StringWriter();
        AnnotatedLargeText<?> oneText;
        do {
            oneText = text.call();
            if (html) {
                pos = oneText.writeHtmlTo(pos, sw);
            } else {
                pos = oneText.writeRawLogTo(pos, new WriterOutputStream(sw, StandardCharsets.UTF_8));
            }
        } while (!oneText.isComplete());
        String result = sw.toString();
        if (coalesceSpans) {
            result = SpanCoalescerTest.coalesceSpans(result);
        }
        assertEquals(expected, result);
        return pos;
    }

    private void assertLength(long length) throws Exception {
        assertLength(text(), length);
    }

    private void assertLength(String id, long length) throws Exception {
        assertLength(text(id), length);
    }

    private void assertLength(AnnotatedLargeText<?> text, long length) throws Exception {
        assertEquals(length, text.length());
    }

    private AnnotatedLargeText<?> text() throws Exception {
        return createStorage().overallLog(null, true);
    }

    private AnnotatedLargeText<?> text(String id) throws Exception {
        return createStorage().stepLog(new MockNode(id), true);
    }

    private static class MockNode extends FlowNode {
        MockNode(String id) {super(null, id);}
        @Override protected String getTypeDisplayName() {return null;}
    }

}

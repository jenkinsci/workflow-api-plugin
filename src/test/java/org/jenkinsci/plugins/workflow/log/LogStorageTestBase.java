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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.junit.Assert.assertEquals;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.console.AnnotatedLargeText;
import hudson.console.HyperlinkNote;
import hudson.model.Action;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.remoting.Channel;
import hudson.remoting.VirtualChannel;
import hudson.slaves.DumbSlave;
import java.io.EOFException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.function.BiFunction;
import java.util.logging.Level;
import jenkins.model.CauseOfInterruption;
import jenkins.security.MasterToSlaveCallable;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.output.NullOutputStream;
import org.apache.commons.io.output.NullWriter;
import org.apache.commons.io.output.WriterOutputStream;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.flow.GraphListener;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.springframework.security.core.Authentication;

/**
 * Foundation for compliance tests of {@link LogStorage} implementations.
 */
public abstract class LogStorageTestBase {

    @ClassRule public static JenkinsRule r = new JenkinsRule();

    @ClassRule public static LoggerRule logging = new LoggerRule();

    /** Create a new storage implementation, but potentially reusing any data initialized in the last {@link Before} setup. */
    protected abstract LogStorage createStorage();

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
        assertOverallLog(betweenStep2Lines, lines("<span class=\"pipeline-node-2\">two #2", "</span>interrupting"), true);
        */
        long overallHtmlPos = assertOverallLog(0, lines(
                "starting",
                "<span class=\"pipeline-node-1\">one #1",
                "</span><span class=\"pipeline-node-2\">two #1",
                "two #2",
                "</span>interrupting"), true);
        assertEquals(overallHtmlPos, assertOverallLog(overallHtmlPos, "", true));
        assertLength(overallHtmlPos);
        try { // either tolerate OOB, or not
            assertOverallLog(999, "", true);
            assertOverallLog(999, "", false);
        } catch (EOFException x) {}
        long step1Pos = assertStepLog("1", 0, lines("one #1"), true);
        long step2Pos = assertStepLog("2", 0, lines("two #1", "two #2"), true);
        step1.getLogger().println("one #2");
        step1.getLogger().println("one #3");
        overall.getLogger().println("pausing");
        overallHtmlPos = assertOverallLog(overallHtmlPos, lines(
                "<span class=\"pipeline-node-1\">one #2",
                "one #3",
                "</span>pausing"), true);
        // TODO if we produce output from the middle of a step, we need new span blocks
        step1Pos = assertStepLog("1", step1Pos, lines("one #2", "one #3"), true);
        assertLength("1", step1Pos);
        try { // as above
            assertStepLog("1", 999, "", true);
            assertStepLog("1", 999, "", false);
        } catch (EOFException x) {}
        step2Pos = assertStepLog("2", step2Pos, "", true);
        close(overall);
        ls = createStorage();
        overall = ls.overallListener();
        overall.getLogger().println("resuming");
        step1 = ls.nodeListener(new MockNode("1"));
        step1.getLogger().println("one #4");
        close(step1);
        TaskListener step3 = ls.nodeListener(new MockNode("3"));
        step3.getLogger().println("three #1");
        close(step3);
        overall.getLogger().println("ending");
        close(overall);
        overallHtmlPos = assertOverallLog(overallHtmlPos, lines(
                "resuming",
                "<span class=\"pipeline-node-1\">one #4",
                "</span><span class=\"pipeline-node-3\">three #1",
                "</span>ending"), true);
        assertEquals(overallHtmlPos, assertOverallLog(overallHtmlPos, "", true));
        assertLength(overallHtmlPos);
        step1Pos = assertStepLog("1", step1Pos, lines("one #4"), true);
        assertLength("1", step1Pos);
        assertStepLog("1", 0, lines("one #1", "one #2", "one #3", "one #4"), false);
        step2Pos = assertStepLog("2", step2Pos, "", true);
        assertStepLog("3", 0, lines("three #1"), true);
        ls = createStorage();
        TaskListener step4 = ls.nodeListener(new MockNode("4"));
        step4.getLogger().println(HyperlinkNote.encodeTo("http://nowhere.net/", "nikde"));
        close(overall);
        long step4Pos = assertStepLog("4", 0, lines("<a href='http://nowhere.net/'>nikde</a>"), true);
        assertLength("4", step4Pos);
        overall = ls.overallListener();
        overall.getLogger().println("really ending");
        close(overall);
        overallHtmlPos = assertOverallLog(overallHtmlPos, lines(
                "<span class=\"pipeline-node-4\"><a href='http://nowhere.net/'>nikde</a>",
                "</span>really ending"), true);
        assertEquals(overallHtmlPos, assertOverallLog(overallHtmlPos, "", true));
        assertLength(overallHtmlPos);
    }

    protected static void close(TaskListener listener) throws Exception {
        if (listener instanceof AutoCloseable) {
            ((AutoCloseable) listener).close();
        }
    }

    @Test public void remoting() throws Exception {
        logging.capture(100).record(Channel.class, Level.WARNING);
        LogStorage ls = createStorage();
        TaskListener overall = ls.overallListener();
        overall.getLogger().println("overall from controller");
        TaskListener step = ls.nodeListener(new MockNode("1"));
        step.getLogger().println("step from controller");
        long overallPos = assertOverallLog(0, lines(
                "overall from controller",
                "<span class=\"pipeline-node-1\">step from controller",
                "</span>").stripTrailing(), true);
        long stepPos = assertStepLog("1", 0, lines("step from controller"), true);
        DumbSlave s = r.createOnlineSlave();
        r.showAgentLogs(s, agentLoggers());
        VirtualChannel channel = s.getChannel();
        channel.call(new RemotePrint("overall from agent", overall));
        channel.call(new RemotePrint("step from agent", step));
        channel.call(new GC());
        overallPos = assertOverallLog(overallPos, lines(
                "overall from agent",
                "<span class=\"pipeline-node-1\">step from agent",
                "</span>").stripTrailing(), true);
        stepPos = assertStepLog("1", stepPos, lines("step from agent"), true);
        assertEquals(overallPos, assertOverallLog(overallPos, "", true));
        assertEquals(stepPos, assertStepLog("1", stepPos, "", true));
        assertThat(logging.getMessages(), empty());
    }
    protected Map<String, Level> agentLoggers() {
        return Collections.singletonMap(LogStorageTestBase.class.getPackage().getName(), Level.FINER);
    }
    private static final class RemotePrint extends MasterToSlaveCallable<Void, Exception> {
        private final String message;
        private final TaskListener listener;
        RemotePrint(String message, TaskListener listener) {
            this.message = message;
            this.listener = listener;
        }
        @Override public Void call() {
            listener.getLogger().println(message);
            listener.getLogger().flush();
            return null;
        }
    }
    /** Checking behavior of {@link DelayBufferedOutputStream} garbage collection. */
    private static final class GC extends MasterToSlaveCallable<Void, Exception> {
        @Override public Void call() {
            System.gc();
            System.runFinalization();
            return null;
        }
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
        close(ls.overallListener());
    }

    @SuppressWarnings("deprecation")
    @Test public void getLogFile() throws Exception {
        LogStorage ls = createStorage();
        TaskListener overall = ls.overallListener();
        overall.getLogger().println("starting");
        TaskListener step1 = ls.nodeListener(new MockNode("1"));
        step1.getLogger().println("from step");
        step1.getLogger().flush();
        overall.getLogger().println("finishing");
        overall.getLogger().flush();
        WorkflowJob fakeProject = r.createProject(WorkflowJob.class, "fake");
        fakeProject.setDefinition(new CpsFlowDefinition("", true));
        WorkflowRun fakeBuild = r.buildAndAssertSuccess(fakeProject);
        assertOverallLog(0, FileUtils.readFileToString(ls.getLogFile(fakeBuild, false)), false);
        close(overall);
        ls = createStorage();
        assertOverallLog(0, FileUtils.readFileToString(ls.getLogFile(fakeBuild, true)), false);
        close(overall);
    }

    // TODO test missing final newline

    protected final long assertOverallLog(long start, String expected, boolean html) throws Exception {
        return assertLog(this::text, start, expected, html, html);
    }

    protected final long assertStepLog(String id, long start, String expected, boolean html) throws Exception {
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

    protected final void assertLength(long length) {
        assertLength(text(), length);
    }

    protected final void assertLength(String id, long length) {
        assertLength(text(id), length);
    }

    private void assertLength(AnnotatedLargeText<?> text, long length) {
        assertEquals(length, text.length());
    }

    private AnnotatedLargeText<?> text() {
        return createStorage().overallLog(null, true);
    }

    private AnnotatedLargeText<?> text(String id) {
        return createStorage().stepLog(new MockNode(id), true);
    }

    /**
     * Concatenate the given lines after interspersing system-dependent line separators between them and adding a final line separator.
     */
    protected String lines(CharSequence... lines) {
        return String.join(System.lineSeparator(), lines) + System.lineSeparator();
    }

    protected static class MockNode extends FlowNode {
        MockNode(String id) {super(new MockFlowExecution(), id);}
        @Override protected String getTypeDisplayName() {return null;}
    }

    private static class MockFlowExecution extends FlowExecution {
        @Override
        public void start() {
            throw new UnsupportedOperationException();
        }

        @Override
        public FlowExecutionOwner getOwner() {
            return null;
        }

        @Override
        public List<FlowNode> getCurrentHeads() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isCurrentHead(FlowNode n) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void interrupt(Result r, CauseOfInterruption... causes) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addListener(GraphListener listener) {
            throw new UnsupportedOperationException();
        }

        @Override
        public FlowNode getNode(String id) {
            throw new UnsupportedOperationException();
        }

        @NonNull
        @Override
        public Authentication getAuthentication2() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Action> loadActions(FlowNode node) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void saveActions(FlowNode node, List<Action> actions) {
            throw new UnsupportedOperationException();
        }
    }
}

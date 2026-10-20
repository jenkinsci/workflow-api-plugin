/*
 * The MIT License
 *
 * Copyright 2026 the Jenkins project contributors
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

package org.jenkinsci.plugins.workflow.flow;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import hudson.ExtensionList;
import hudson.model.Action;
import hudson.model.Result;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import jenkins.model.CauseOfInterruption;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.For;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.TestExtension;

/**
 * A listener that throws must not prevent later listeners from running, and must not fail the caller.
 *
 * @see <a href="https://github.com/jenkinsci/workflow-job-plugin/issues/760">workflow-job-plugin#760</a>
 */
@For(FlowExecutionListener.class)
public class FlowExecutionListenerTest {

    private static final List<String> events = new ArrayList<>();
    private static final List<FlowExecution> seen = new ArrayList<>();

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Rule
    public LoggerRule logging =
            new LoggerRule().record(FlowExecutionListener.class, Level.WARNING).capture(50);

    @Test
    public void throwingListenerDoesNotPreventSubsequentListeners() {
        events.clear();
        seen.clear();
        // TestExtension has no ordinal, so equal-ordinal listeners are ordered by class name.
        assertEquals(
                List.of("ListenerA", "ListenerB", "ListenerC"),
                ExtensionList.lookup(FlowExecutionListener.class).stream()
                        .filter(l -> l.getClass().getEnclosingClass() == FlowExecutionListenerTest.class)
                        .map(l -> l.getClass().getSimpleName())
                        .toList());
        FlowExecution execution = new StubFlowExecution();

        // None of these may throw, even though a listener fails inside each notification.
        // fireResumed also notifies FlowExecutionList.ResumeStepExecutionListener, which throws on this stub.
        FlowExecutionListener.fireCompleted(execution);
        FlowExecutionListener.fireRunning(execution);
        FlowExecutionListener.fireCreated(execution);
        FlowExecutionListener.fireResumed(execution);

        assertEquals(
                List.of(
                        "completed:before",
                        "completed:middle",
                        "completed:after",
                        "running:before",
                        "running:middle",
                        "running:after",
                        "created:before",
                        "created:middle",
                        "created:after",
                        "resumed:before",
                        "resumed:middle",
                        "resumed:after"),
                List.copyOf(events));
        for (FlowExecution observed : seen) {
            assertSame(execution, observed);
        }
        assertThat(
                thrownMessages(), hasItems("completed failed", "running failed", "created failed", "resumed failed"));
    }

    private List<String> thrownMessages() {
        return logging.getRecords().stream()
                .map(LogRecord::getThrown)
                .filter(Objects::nonNull)
                .map(Throwable::getMessage)
                .toList();
    }

    private static void record(String event, FlowExecution execution) {
        events.add(event);
        seen.add(execution);
    }

    /** Notified first. Fails completion and running after recording the call. */
    @TestExtension
    public static class ListenerA extends FlowExecutionListener {
        @Override
        public void onCreated(FlowExecution execution) {
            record("created:before", execution);
        }

        @Override
        public void onRunning(FlowExecution execution) {
            record("running:before", execution);
            throw new IllegalStateException("running failed");
        }

        @Override
        public void onResumed(FlowExecution execution) {
            record("resumed:before", execution);
        }

        @Override
        public void onCompleted(FlowExecution execution) {
            record("completed:before", execution);
            throw new IllegalStateException("completed failed");
        }
    }

    /** Notified second. Fails creation and resumption after recording the call. */
    @TestExtension
    public static class ListenerB extends FlowExecutionListener {
        @Override
        public void onCreated(FlowExecution execution) {
            record("created:middle", execution);
            throw new IllegalStateException("created failed");
        }

        @Override
        public void onRunning(FlowExecution execution) {
            record("running:middle", execution);
        }

        @Override
        public void onResumed(FlowExecution execution) {
            record("resumed:middle", execution);
            throw new IllegalStateException("resumed failed");
        }

        @Override
        public void onCompleted(FlowExecution execution) {
            record("completed:middle", execution);
        }
    }

    /** Notified last. Always records, so a prior failure is visible. */
    @TestExtension
    public static class ListenerC extends FlowExecutionListener {
        @Override
        public void onCreated(FlowExecution execution) {
            record("created:after", execution);
        }

        @Override
        public void onRunning(FlowExecution execution) {
            record("running:after", execution);
        }

        @Override
        public void onResumed(FlowExecution execution) {
            record("resumed:after", execution);
        }

        @Override
        public void onCompleted(FlowExecution execution) {
            record("completed:after", execution);
        }
    }

    /** Only passed through to listeners; none of its methods need to succeed. */
    private static final class StubFlowExecution extends FlowExecution {
        @Override
        public void start() {}

        @Override
        public FlowExecutionOwner getOwner() {
            throw new IllegalStateException("stub");
        }

        @Override
        public List<FlowNode> getCurrentHeads() {
            return List.of();
        }

        @Override
        public boolean isCurrentHead(FlowNode n) {
            return false;
        }

        @Override
        public void interrupt(Result r, CauseOfInterruption... causes) {}

        @Override
        public void addListener(GraphListener listener) {}

        @Override
        public FlowNode getNode(String id) {
            return null;
        }

        @Override
        public List<Action> loadActions(FlowNode node) {
            return List.of();
        }

        @Override
        public void saveActions(FlowNode node, List<Action> actions) {}
    }
}

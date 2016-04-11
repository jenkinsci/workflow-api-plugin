/*
 * The MIT License
 *
 * Copyright 2016 CloudBees, Inc.
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

package org.jenkinsci.plugins.workflow.graph;

import com.google.inject.Inject;
import hudson.model.TaskListener;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.junit.Test;
import org.junit.ClassRule;
import org.junit.Rule;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;

public class FlowNodeSerialWalkerTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public JenkinsRule r = new JenkinsRule();

    @Test public void labelEnclosing() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
            "lE 'start'; parallel one: {lE 'in-one'}, two: {lE 'in-two'}; lE 'middle'\n" +
            "stage 'dev'; lE 'in-dev'; stage 'test'; lE 'in-test'\n" +
            "parallel three: {lE 'in-three'}\n" +
            "node {writeFile file: 'f1', text: ''; load 'f1'; lE 'after-load'}\n" +
            "node {writeFile file: 'f2', text: 'lE \"in-load\"'; load 'f2'}\n" +
            "node {writeFile file: 'f3', text: '{-> lE \"closure-load\"}'; load('f3')()}\n" +
            "lE 'end'", true));
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        r.assertLogContains("start: null", b);
        r.assertLogContains("in-one: Branch: one", b);
        r.assertLogContains("in-two: Branch: two", b);
        r.assertLogContains("middle: null", b);
        r.assertLogContains("in-dev: dev", b);
        r.assertLogContains("in-test: test", b);
        r.assertLogContains("in-three: Branch: three", b);
        r.assertLogContains("after-load: test", b);
        r.assertLogContains("in-load: f2", b);
        r.assertLogContains("closure-load: test", b);
        r.assertLogContains("end: test", b);
        p.setDefinition(new CpsFlowDefinition("lE 'start'; stage 'dev'; lE 'in-dev'; stage 'test'; lE 'in-test'", true));
        b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        r.assertLogContains("start: null", b);
        r.assertLogContains("in-dev: dev", b);
        r.assertLogContains("in-test: test", b);
    }
    public static class PrintEnclosingLabelStep extends AbstractStepImpl {
        final String which;
        @DataBoundConstructor public PrintEnclosingLabelStep(String which) {
            this.which = which;
        }
        public static class Execution extends AbstractSynchronousStepExecution<Void> {
            @Inject private PrintEnclosingLabelStep step;
            @StepContextParameter private TaskListener listener;
            @StepContextParameter private FlowNode node;
            @Override protected Void run() throws Exception {
                listener.getLogger().println(step.which + ": " + labelEnclosing(node));
                return null;
            }
            private static @CheckForNull String labelEnclosing(@Nonnull FlowNode node) {
                FlowNodeSerialWalker.EnhancedIterator it = new FlowNodeSerialWalker(node).iterator();
                while (it.hasNext()) {
                    it.next();
                    String label = it.currentLabel();
                    if (label != null) {
                        return label;
                    }
                }
                return null;
            }
        }
        @TestExtension("labelEnclosing") public static class DescriptorImpl extends AbstractStepDescriptorImpl {
            public DescriptorImpl() {
                super(Execution.class);
            }
            @Override public String getFunctionName() {
                return "lE";
            }
        }
    }

}

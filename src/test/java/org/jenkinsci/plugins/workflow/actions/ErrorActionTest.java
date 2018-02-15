/*
 * The MIT License
 *
 * Copyright (c) 2016 IKEDA Yasuyuki
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

package org.jenkinsci.plugins.workflow.actions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.hamcrest.Matchers;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.FlowGraphWalker;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import hudson.model.Result;
import hudson.remoting.ProxyException;
import org.codehaus.groovy.runtime.NullObject;

/**
 * Tests for {@link ErrorAction}
 */
public class ErrorActionTest {
    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @Rule
    public JenkinsRule r = new JenkinsRule();

    private List<ErrorAction> extractErrorActions(FlowExecution exec) {
        List<ErrorAction> ret = new ArrayList<ErrorAction>();

        FlowGraphWalker walker = new FlowGraphWalker(exec);
        for (FlowNode n : walker) {
            ErrorAction e = n.getAction(ErrorAction.class);
            if (e != null) {
                ret.add(e);
            }
        }
        return ret;
    }

    @Test
    public void simpleException() throws Exception {
        final String EXPECTED = "For testing purpose";
        WorkflowJob job = r.jenkins.createProject(WorkflowJob.class, "p");
        job.setDefinition(new CpsFlowDefinition(String.format(
                "node {\n"
                        + "throw new Exception('%s');\n"
                + "}"
                , EXPECTED
        )));
        WorkflowRun b = r.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get());
        List<ErrorAction> errorActionList = extractErrorActions(b.asFlowExecutionOwner().get());
        assertThat(errorActionList, Matchers.not(Matchers.empty()));
        for (ErrorAction e : errorActionList) {
            assertEquals(Exception.class, e.getError().getClass());
            assertEquals(EXPECTED, e.getError().getMessage());
        }
    }

    @Issue("JENKINS-34488")
    @Test
    public void unserializableForSecurityReason() throws Exception {
        final String FAILING_EXPRESSION = "(2 + 2) == 5";
        WorkflowJob job = r.jenkins.createProject(WorkflowJob.class, "p");
        // "assert false" throws org.codehaus.groovy.runtime.powerassert.PowerAssertionError,
        // which is rejected by remoting.
        job.setDefinition(new CpsFlowDefinition(String.format(
                "node {\n"
                        + "assert %s;\n"
                + "}",
                FAILING_EXPRESSION
        )));
        WorkflowRun b = r.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get());
        r.assertLogContains(FAILING_EXPRESSION, b); // ensure that failed with the assertion.
        List<ErrorAction> errorActionList = extractErrorActions(b.asFlowExecutionOwner().get());
        assertThat(errorActionList, Matchers.not(Matchers.empty()));
        for (ErrorAction e : errorActionList) {
            assertEquals(ProxyException.class, e.getError().getClass());
        }
    }

    @Issue("JENKINS-39346")
    @Test public void wrappedUnserializableException() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
            "catchError {\n" +
            "  try {\n" +
            "    try {\n" +
            "      throw new NullPointerException('oops')\n" +
            "    } catch (e) {\n" +
            "      throw new org.codehaus.groovy.runtime.InvokerInvocationException(e)\n" + // TODO is there a way to convince Groovy to throw this on its own?
            "    }\n" +
            "  } catch (e) {\n" +
            "    throw new IllegalArgumentException(e)\n" +
            "  }\n" +
            "}\n" +
            "echo 'got to the end'", false));
        WorkflowRun b = r.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0).get());
        r.assertLogContains("got to the end", b);
        r.assertLogContains("java.lang.NullPointerException: oops", b);
    }

    @Issue("JENKINS-49025")
    @Test public void nestedFieldUnserializable() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
            "catchError {\n" +
            "  throw new " + X.class.getCanonicalName() + "()\n" +
            "}\n" +
            "echo 'got to the end'", false));
        WorkflowRun b = r.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0).get());
        r.assertLogContains("got to the end", b);
        r.assertLogContains(X.class.getName(), b);
        List<ErrorAction> errorActionList = extractErrorActions(b.asFlowExecutionOwner().get());
        assertThat(errorActionList, Matchers.not(Matchers.empty()));
        for (ErrorAction e : errorActionList) {
            assertEquals(ProxyException.class, e.getError().getClass());
        }
    }
    public static class X extends Exception {
        final NullObject nil = NullObject.getNullObject();
    }

}

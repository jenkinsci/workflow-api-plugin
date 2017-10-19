package org.jenkinsci.plugins.workflow.flow;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.RestartableJenkinsRule;

import java.io.File;
import java.util.List;
import java.util.logging.Level;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;


public class StashManagerTest {
    @Rule public RestartableJenkinsRule rr = new RestartableJenkinsRule();
    @Rule public LoggerRule logging = new LoggerRule().record(FlowNode.class, Level.FINER);

    @Issue("JENKINS-40912")
    @Test public void fileList() throws Exception {
        rr.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowJob p = rr.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(
                        "node {\n" +
                                "  writeFile file: 'subdir/fname', text: 'whatever'\n" +
                                "  writeFile file: 'subdir/other', text: 'more'\n" +
                                "  dir('subdir') {\n" +
                                "    fakeStash 'whatever'\n" +
                                "  }\n" +
                                "}\n" +
                                "node {\n" +
                                "  dir('elsewhere') {\n" +
                                "    fakeUnstash 'whatever'\n" +
                                "    echo \"got fname: ${readFile 'fname'} other: ${readFile 'other'}\"\n" +
                                "  }\n" +
                                "  writeFile file: 'at-top', text: 'ignored'\n" +
                                "  fakeStash name: 'from-top', includes: 'elsewhere/', excludes: '**/other'\n" +
                                "  semaphore 'ending'\n" +
                                "}", true));
                WorkflowRun b = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("ending/1", b);
                assertEquals("{from-top={elsewhere/fname=whatever}, whatever={fname=whatever, other=more}}", StashManager.stashesOf(b).toString());
                List<String> whateverFiles = StashManager.getStashFiles(b, "whatever");
                assertNotNull(whateverFiles);
                assertEquals(2, whateverFiles.size());
                assertTrue(whateverFiles.contains("fname"));
                assertTrue(whateverFiles.contains("other"));

                List<String> fromTopFiles = StashManager.getStashFiles(b, "from-top");
                assertNotNull(fromTopFiles);
                assertEquals(1, fromTopFiles.size());
                assertTrue(fromTopFiles.contains("elsewhere" + File.separator + "fname"));

                SemaphoreStep.success("ending/1", null);
                rr.j.assertBuildStatusSuccess(rr.j.waitForCompletion(b));
                rr.j.assertLogContains("got fname: whatever other: more", b);
                assertEquals("{}", StashManager.stashesOf(b).toString()); // TODO flake expected:<{[]}> but was:<{[from-top={elsewhere/fname=whatever}, whatever={fname=whatever, other=more}]}>
            }
        });
    }

}
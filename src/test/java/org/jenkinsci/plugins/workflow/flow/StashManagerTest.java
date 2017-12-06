package org.jenkinsci.plugins.workflow.flow;

import com.google.common.base.Predicate;
import hudson.FilePath;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.actions.WorkspaceAction;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graphanalysis.DepthFirstScanner;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.RestartableJenkinsRule;

import javax.annotation.Nullable;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class StashManagerTest {
    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule
    public RestartableJenkinsRule rr = new RestartableJenkinsRule();

    @Issue("JENKINS-40912")
    @Test
    public void stashFileList() throws Exception {
        rr.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowJob p = rr.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(
                        "node {\n" +
                                "  dir('first') {\n" +
                                "    writeFile file: 'fname', text: 'whatever'\n" +
                                "    writeFile file: 'other', text: 'more'\n" +
                                "  }\n" +
                                "  dir('second') {\n" +
                                "    writeFile file: 'at-top', text: 'ignored'\n" +
                                "    dir('elsewhere') {\n" +
                                "      writeFile file: 'nested', text: 'present'\n" +
                                "      writeFile file: 'other', text: 'ignored'\n" +
                                "    }\n" +
                                "  }\n" +
                                "}", true));
                WorkflowRun b = rr.j.buildAndAssertSuccess(p);

                // Not using stash step so that we're not dependent on a proper match between workflow-api and workflow-basic-steps versions.
                FlowNode node = new DepthFirstScanner().findFirstMatch(b.getExecution(), new HasWorkspacePredicate());
                assertNotNull(node);
                WorkspaceAction action = node.getPersistentAction(WorkspaceAction.class);
                assertNotNull(action);
                FilePath ws = action.getWorkspace();
                assertNotNull(ws);

                TaskListener tl = rr.j.createTaskListener();

                // Add stashes
                StashManager.stash(b, "first", ws.child("first"), tl, null, null, true, false);
                StashManager.stash(b, "second", ws.child("second"), tl, "elsewhere/", "**/other", true, false);

                List<String> firstList = StashManager.stashFileList(b, "first");
                assertFalse(firstList.isEmpty());
                assertEquals(2, firstList.size());
                assertTrue(firstList.contains("fname"));
                assertTrue(firstList.contains("other"));

                List<String> secondList = StashManager.stashFileList(b, "second");
                assertFalse(secondList.isEmpty());
                assertEquals(1, secondList.size());
                assertTrue(secondList.contains("elsewhere" + File.separator + "nested"));
            }
        });
    }

    private static final class HasWorkspacePredicate implements Predicate<FlowNode> {
        @Override
        public boolean apply(@Nullable FlowNode input) {
            return input != null &&
                    input.getPersistentAction(WorkspaceAction.class) != null;
        }
    }

}

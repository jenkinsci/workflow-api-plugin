package org.jenkinsci.plugins.workflow.log.tee;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.log.FileLogStorageFactory;
import org.jenkinsci.plugins.workflow.log.LogStorage;
import org.jenkinsci.plugins.workflow.log.configuration.PipelineLoggingGlobalConfiguration;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;

public class TeeLogStoragePipelineTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void smokes() throws Exception {
        var storageFactory = new TeeLogStorageFactory(
                new FileLogStorageFactory(), new RemoteCustomFileLogStorageFactory(tmp.newFile()));
        var config = PipelineLoggingGlobalConfiguration.get();
        config.setFactory(storageFactory);

        WorkflowJob workflowJob = j.createProject(WorkflowJob.class);
        workflowJob.setDefinition(new CpsFlowDefinition("echo 'Hello World'", true));

        j.buildAndAssertSuccess(workflowJob);
        assertThat(LogStorage.of(workflowJob.getLastBuild().asFlowExecutionOwner()), instanceOf(TeeLogStorage.class));
    }
}

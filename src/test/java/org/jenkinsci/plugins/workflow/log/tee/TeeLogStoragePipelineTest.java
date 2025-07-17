package org.jenkinsci.plugins.workflow.log.tee;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;

import hudson.model.Result;
import java.util.List;
import org.jenkinsci.plugins.workflow.log.configuration.PipelineLoggingGlobalConfiguration;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.log.BrokenLogStorage;
import org.jenkinsci.plugins.workflow.log.FileLogStorageFactory;
import org.jenkinsci.plugins.workflow.log.LogStorage;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class TeeLogStoragePipelineTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void smokes() throws Exception {
        var storageFactory = new TeeLogStorageFactory();
        storageFactory.setFactories(List.of(new FileLogStorageFactory()));
        var config = PipelineLoggingGlobalConfiguration.get();
        config.setFactory(storageFactory);

        WorkflowJob workflowJob = j.createProject(WorkflowJob.class);
        workflowJob.setDefinition(new CpsFlowDefinition("echo 'Hello World'", true));

        j.buildAndAssertSuccess(workflowJob);
        assertThat(LogStorage.of(workflowJob.getLastBuild().asFlowExecutionOwner()), instanceOf(TeeLogStorage.class));
    }

    @Test
    public void empty_factories() throws Exception {
        var storageFactory = new TeeLogStorageFactory();
        storageFactory.setFactories(List.of());
        var config = PipelineLoggingGlobalConfiguration.get();
        config.setFactory(storageFactory);

        WorkflowJob workflowJob = j.createProject(WorkflowJob.class);
        workflowJob.setDefinition(new CpsFlowDefinition("echo 'Hello World'", true));

        j.buildAndAssertStatus(Result.FAILURE, workflowJob);
        assertThat(
                LogStorage.of(workflowJob.getLastBuild().asFlowExecutionOwner()), instanceOf(BrokenLogStorage.class));
    }
}

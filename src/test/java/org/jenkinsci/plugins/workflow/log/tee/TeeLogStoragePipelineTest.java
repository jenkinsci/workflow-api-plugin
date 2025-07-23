package org.jenkinsci.plugins.workflow.log.tee;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.io.FileMatchers.anExistingFile;

import java.io.File;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.log.FileLogStorageFactory;
import org.jenkinsci.plugins.workflow.log.LogStorage;
import org.jenkinsci.plugins.workflow.log.configuration.PipelineLoggingGlobalConfiguration;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
public class TeeLogStoragePipelineTest {

    @Test
    public void smokes(JenkinsRule j) throws Exception {
        var storageFactory =
                new TeeLogStorageFactory(new FileLogStorageFactory(), new RemoteCustomFileLogStorageFactory());
        var config = PipelineLoggingGlobalConfiguration.get();
        config.setFactory(storageFactory);

        WorkflowJob workflowJob = j.createProject(WorkflowJob.class);
        workflowJob.setDefinition(new CpsFlowDefinition("echo 'Hello World'", true));

        WorkflowRun b = j.buildAndAssertSuccess(workflowJob);
        assertThat(LogStorage.of(workflowJob.getLastBuild().asFlowExecutionOwner()), instanceOf(TeeLogStorage.class));

        File logIndex = new File(b.getRootDir(), "log-index");
        File log = new File(b.getRootDir(), "log");
        File customLog = new File(b.getRootDir(), "custom-log");

        assertThat(logIndex, anExistingFile());
        assertThat(log, anExistingFile());
        assertThat(customLog, anExistingFile());

        //        assertThat(Files.readString(log.toPath()), containsString("Hello World"));
        //        assertThat(Files.readString(customLog.toPath()), containsString("Hello World"));
    }
}

package org.jenkinsci.plugins.workflow.log.configuration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;

import io.jenkins.plugins.casc.ConfiguratorException;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.log.FileLogStorage;
import org.jenkinsci.plugins.workflow.log.FileLogStorageFactory;
import org.jenkinsci.plugins.workflow.log.LogStorage;
import org.jenkinsci.plugins.workflow.log.configuration.mock.LogStorageFactoryMock1;
import org.jenkinsci.plugins.workflow.log.configuration.mock.LogStorageFactoryMock2;
import org.jenkinsci.plugins.workflow.log.tee.TeeLogStorageFactory;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class PipelineLoggingGlobalConfigurationJCasCTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Rule
    public JenkinsConfiguredWithCodeRule r = new JenkinsConfiguredWithCodeRule();

    @Test
    public void no_configuration() throws Throwable {
        PipelineLoggingGlobalConfiguration config = PipelineLoggingGlobalConfiguration.get();
        assertThat(config.getFactory(), instanceOf(FileLogStorageFactory.class));

        WorkflowJob workflowJob = r.createProject(WorkflowJob.class);
        workflowJob.setDefinition(new CpsFlowDefinition("echo 'Hello World'", true));

        r.buildAndAssertSuccess(workflowJob);
        assertThat(LogStorage.of(workflowJob.getLastBuild().asFlowExecutionOwner()), instanceOf(FileLogStorage.class));
    }

    @Test
    @ConfiguredWithCode("jcasc_smokes.yaml")
    public void smokes() throws Throwable {
        PipelineLoggingGlobalConfiguration config = PipelineLoggingGlobalConfiguration.get();
        assertThat(config.getFactory(), instanceOf(TeeLogStorageFactory.class));
        var factory = (TeeLogStorageFactory) config.getFactory();
        assertThat(factory.getPrimary(), instanceOf(LogStorageFactoryMock1.class));
        assertThat(factory.getSecondary(), instanceOf(LogStorageFactoryMock2.class));
    }

    @Test
    @ConfiguredWithCode(
            value = "jcasc_primary-only.yaml",
            expected = ConfiguratorException.class,
            message =
                    "Arguments: [org.jenkinsci.plugins.workflow.log.configuration.mock.LogStorageFactoryMock1, null].\n Expected Parameters: primary org.jenkinsci.plugins.workflow.log.LogStorageFactory, secondary org.jenkinsci.plugins.workflow.log.LogStorageFactory")
    public void primary_only() throws Throwable {}

    @Test
    @ConfiguredWithCode(
            value = "jcasc_secondary-only.yaml",
            expected = ConfiguratorException.class,
            message =
                    "Arguments: [null, org.jenkinsci.plugins.workflow.log.configuration.mock.LogStorageFactoryMock2].\n Expected Parameters: primary org.jenkinsci.plugins.workflow.log.LogStorageFactory, secondary org.jenkinsci.plugins.workflow.log.LogStorageFactory")
    public void secondary_only() throws Throwable {}

    @Test
    @ConfiguredWithCode(
            value = "jcasc_empty.yaml",
            expected = ConfiguratorException.class,
            message =
                    "Arguments: [null, null].\n Expected Parameters: primary org.jenkinsci.plugins.workflow.log.LogStorageFactory, secondary org.jenkinsci.plugins.workflow.log.LogStorageFactory")
    public void empty() throws Throwable {}

    @Test
    @ConfiguredWithCode(
            value = "jcasc_duplicate.yaml",
            expected = ConfiguratorException.class,
            message =
                    "Arguments: [org.jenkinsci.plugins.workflow.log.configuration.mock.LogStorageFactoryMock1, org.jenkinsci.plugins.workflow.log.configuration.mock.LogStorageFactoryMock1].\n Expected Parameters: primary org.jenkinsci.plugins.workflow.log.LogStorageFactory, secondary org.jenkinsci.plugins.workflow.log.LogStorageFactory")
    public void duplicate() throws Throwable {}
}

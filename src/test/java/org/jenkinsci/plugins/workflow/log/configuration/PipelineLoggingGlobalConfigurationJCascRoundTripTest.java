package org.jenkinsci.plugins.workflow.log.configuration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.instanceOf;

import io.jenkins.plugins.casc.misc.junit.jupiter.AbstractRoundTripTest;
import org.jenkinsci.plugins.workflow.log.configuration.mock.LogStorageFactoryMock1;
import org.jenkinsci.plugins.workflow.log.configuration.mock.LogStorageFactoryMock2;
import org.jenkinsci.plugins.workflow.log.tee.TeeLogStorageFactory;
import org.jvnet.hudson.test.JenkinsRule;

public class PipelineLoggingGlobalConfigurationJCascRoundTripTest extends AbstractRoundTripTest {

    @Override
    protected String configResource() {
        return "jcasc_smokes.yaml";
    }

    @Override
    protected void assertConfiguredAsExpected(JenkinsRule j, String configContent) {
        PipelineLoggingGlobalConfiguration config = PipelineLoggingGlobalConfiguration.get();
        assertThat(config.getFactory(), instanceOf(TeeLogStorageFactory.class));
        var factory = (TeeLogStorageFactory) config.getFactory();
        assertThat(
                factory.getFactories(),
                contains(instanceOf(LogStorageFactoryMock1.class), instanceOf(LogStorageFactoryMock2.class)));
    }

    @Override
    protected String stringInLogExpected() {
        return "pipelineLoggingGlobalConfiguration";
    }
}

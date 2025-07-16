package org.jenkinsci.plugins.workflow.configuration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.emptyIterable;
import static org.hamcrest.Matchers.instanceOf;

import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import io.jenkins.plugins.casc.misc.junit.jupiter.AbstractRoundTripTest;
import org.jenkinsci.plugins.workflow.configuration.mock.LogStorageFactoryMock1;
import org.jenkinsci.plugins.workflow.configuration.mock.LogStorageFactoryMock2;
import org.jenkinsci.plugins.workflow.log.tee.TeeLogStorageFactory;
import org.junit.Rule;
import org.junit.Test;

public class PipelineLoggingGlobalConfigurationJCasCTest {

    @Rule
    public JenkinsConfiguredWithCodeRule r = new JenkinsConfiguredWithCodeRule();

    @Test
    @ConfiguredWithCode("jcasc_smokes.yaml")
    public void smokes() throws Throwable {
        PipelineLoggingGlobalConfiguration config = PipelineLoggingGlobalConfiguration.get();
        assertThat(config.getFactory(), instanceOf(TeeLogStorageFactory.class));
        var factory = (TeeLogStorageFactory) config.getFactory();
        assertThat(
                factory.getFactories(),
                contains(instanceOf(LogStorageFactoryMock1.class), instanceOf(LogStorageFactoryMock2.class)));
    }

    @Test
    @ConfiguredWithCode("jcasc_primary-only.yaml")
    public void primary_only() throws Throwable {
        PipelineLoggingGlobalConfiguration config = PipelineLoggingGlobalConfiguration.get();
        assertThat(config.getFactory(), instanceOf(TeeLogStorageFactory.class));
        var factory = (TeeLogStorageFactory) config.getFactory();
        assertThat(factory.getFactories(), contains(instanceOf(LogStorageFactoryMock1.class)));
    }

    @Test
    @ConfiguredWithCode("jcasc_empty.yaml")
    public void empty() throws Throwable {
        PipelineLoggingGlobalConfiguration config = PipelineLoggingGlobalConfiguration.get();
        assertThat(config.getFactory(), instanceOf(TeeLogStorageFactory.class));
        var factory = (TeeLogStorageFactory) config.getFactory();
        assertThat(factory.getFactories(), emptyIterable());
    }
}

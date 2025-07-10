package org.jenkinsci.plugins.workflow.configuration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import org.jenkinsci.plugins.workflow.configuration.mock.TeeLogStorageFactoryMock1;
import org.jenkinsci.plugins.workflow.configuration.mock.TeeLogStorageFactoryMock2;
import org.junit.Rule;
import org.junit.Test;

public class TeeLogStorageFactoryConfigurationJCasCTest {

    @Rule
    public JenkinsConfiguredWithCodeRule r = new JenkinsConfiguredWithCodeRule();

    @Test
    @ConfiguredWithCode("jcasc_smokes.yaml")
    public void smokes() throws Throwable {
        TeeLogStorageFactoryConfiguration config = TeeLogStorageFactoryConfiguration.get();
        assertThat(config.isEnabled(), is(true));
        assertThat(config.getFactories(), contains(
            instanceOf(TeeLogStorageFactoryMock1.class),
            instanceOf(TeeLogStorageFactoryMock2.class))
        );
    }
    
    @Test
    @ConfiguredWithCode("jcasc_primary-only.yaml")
    public void primary_only() throws Throwable {
        TeeLogStorageFactoryConfiguration config = TeeLogStorageFactoryConfiguration.get();
        assertThat(config.isEnabled(), is(true));
        assertThat(config.getFactories(), contains(
            instanceOf(TeeLogStorageFactoryMock1.class))
        );
    }
}

package org.jenkinsci.plugins.workflow.configuration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

import java.util.List;
import org.jenkinsci.plugins.workflow.configuration.mock.TeeLogStorageFactoryMock1;
import org.jenkinsci.plugins.workflow.configuration.mock.TeeLogStorageFactoryMock2;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsSessionRule;
import org.jvnet.hudson.test.recipes.LocalData;

public class TeeLogStorageFactoryConfigurationTest {

    @Rule
    public JenkinsSessionRule sessions = new JenkinsSessionRule();

    @Test
    public void smokes() throws Throwable {
        sessions.then(r -> {
            var config = TeeLogStorageFactoryConfiguration.get();
            assertThat(config.isEnabled(), is(false));
            config.setEnabled(true);
            assertThat(config.isEnabled(), is(true));

            assertThat(config.getFactories(), empty());
            config.setFactories(List.of(new TeeLogStorageFactoryMock1(), new TeeLogStorageFactoryMock2()));
            assertThat(
                    config.getFactories(),
                    contains(instanceOf(TeeLogStorageFactoryMock1.class), instanceOf(TeeLogStorageFactoryMock2.class)));
            r.configRoundtrip();
        });
        sessions.then(r -> {
            assertThat(
                    TeeLogStorageFactoryConfiguration.get().getFactories(),
                    contains(instanceOf(TeeLogStorageFactoryMock1.class), instanceOf(TeeLogStorageFactoryMock2.class)));
            assertThat(TeeLogStorageFactoryConfiguration.get().lookup(TeeLogStorageFactoryMock1.class),
                       contains(instanceOf(TeeLogStorageFactoryMock1.class)));
        });
    }

    @Test
    @LocalData
    public void primary_only() throws Throwable {
        sessions.then(r -> {
            assertThat(TeeLogStorageFactoryConfiguration.get().isEnabled(), is(true));
            assertThat(
                    TeeLogStorageFactoryConfiguration.get().getFactories(),
                    contains(instanceOf(TeeLogStorageFactoryMock1.class)));
        });
    }

    @Test
    @LocalData
    public void unknown_factory() throws Throwable {
        sessions.then(r -> {
            assertThat(TeeLogStorageFactoryConfiguration.get().isEnabled(), is(true));
            assertThat(
                    TeeLogStorageFactoryConfiguration.get().getFactories(),
                    contains(
                        instanceOf(TeeLogStorageFactoryMock1.class),
                        instanceOf(TeeLogStorageFactoryMock2.class)));
        });
    }
}

package org.jenkinsci.plugins.workflow.configuration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

import edu.umd.cs.findbugs.annotations.NonNull;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.log.LogStorage;
import org.jenkinsci.plugins.workflow.log.tee.TeeLogStorageFactory;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsSessionRule;
import org.jvnet.hudson.test.TestExtension;
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
            config.setPrimaryId(TeeLogStorageFactoryMock1.class.getName());
            config.setSecondaryId(TeeLogStorageFactoryMock2.class.getName());

            assertThat(
                    config.getFactories(),
                    contains(instanceOf(TeeLogStorageFactoryMock1.class), instanceOf(TeeLogStorageFactoryMock2.class)));
            r.configRoundtrip();
        });
        sessions.then(r -> {
            assertThat(
                    TeeLogStorageFactoryConfiguration.get().getFactories(),
                    contains(instanceOf(TeeLogStorageFactoryMock1.class), instanceOf(TeeLogStorageFactoryMock2.class)));
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
                    contains(instanceOf(TeeLogStorageFactoryMock2.class)));
        });
    }

    @TestExtension
    public static class TeeLogStorageFactoryMock1 implements TeeLogStorageFactory {

        @Override
        public String getDisplayName() {
            return "Tee Log Storage Factory Mock 1";
        }

        @Override
        public LogStorage forBuild(@NonNull FlowExecutionOwner b) {
            return null;
        }
    }

    @TestExtension
    public static class TeeLogStorageFactoryMock2 implements TeeLogStorageFactory {

        @Override
        public String getDisplayName() {
            return "Tee Log Storage Factory Mock 2";
        }

        @Override
        public LogStorage forBuild(@NonNull FlowExecutionOwner b) {
            return null;
        }
    }
}

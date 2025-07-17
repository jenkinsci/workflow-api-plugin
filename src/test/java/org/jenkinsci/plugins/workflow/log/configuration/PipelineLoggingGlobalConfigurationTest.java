package org.jenkinsci.plugins.workflow.log.configuration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.emptyIterable;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.nullValue;

import java.util.List;
import org.jenkinsci.plugins.workflow.log.configuration.mock.LogStorageFactoryMock1;
import org.jenkinsci.plugins.workflow.log.configuration.mock.LogStorageFactoryMock2;
import org.jenkinsci.plugins.workflow.log.FileLogStorageFactory;
import org.jenkinsci.plugins.workflow.log.tee.TeeLogStorageFactory;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsSessionRule;

public class PipelineLoggingGlobalConfigurationTest {

    @Rule
    public JenkinsSessionRule sessions = new JenkinsSessionRule();

    @Test
    public void default_factory() throws Throwable {
        sessions.then(r -> {
            assertThat(PipelineLoggingGlobalConfiguration.get().getFactory(), nullValue());
            r.configRoundtrip();
        });
        sessions.then(r -> {
            assertThat(PipelineLoggingGlobalConfiguration.get().getFactory(), instanceOf(FileLogStorageFactory.class));
        });
    }

    @Test
    public void teeLogStorageFactory() throws Throwable {
        sessions.then(r -> {
            TeeLogStorageFactory factory = new TeeLogStorageFactory();
            factory.setFactories(List.of(new LogStorageFactoryMock1(), new LogStorageFactoryMock2()));
            PipelineLoggingGlobalConfiguration.get().setFactory(factory);
            r.configRoundtrip();
        });
        sessions.then(r -> {
            var configuration = PipelineLoggingGlobalConfiguration.get();
            assertThat(configuration.getFactory(), instanceOf(TeeLogStorageFactory.class));
            var factory = (TeeLogStorageFactory) configuration.getFactory();
            assertThat(
                    factory.getFactories(),
                    contains(instanceOf(LogStorageFactoryMock1.class), instanceOf(LogStorageFactoryMock2.class)));
        });
    }

    @Test
    public void teeLogStorageFactory_empty() throws Throwable {
        sessions.then(r -> {
            TeeLogStorageFactory factory = new TeeLogStorageFactory();
            PipelineLoggingGlobalConfiguration.get().setFactory(factory);
            r.configRoundtrip();
        });
        sessions.then(r -> {
            var configuration = PipelineLoggingGlobalConfiguration.get();
            assertThat(configuration.getFactory(), instanceOf(TeeLogStorageFactory.class));
            var factory = (TeeLogStorageFactory) configuration.getFactory();
            assertThat(factory.getFactories(), emptyIterable());
        });
    }
}

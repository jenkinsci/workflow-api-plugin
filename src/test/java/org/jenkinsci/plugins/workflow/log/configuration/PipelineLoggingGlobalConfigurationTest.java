package org.jenkinsci.plugins.workflow.log.configuration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThrows;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.File;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.log.BrokenLogStorage;
import org.jenkinsci.plugins.workflow.log.FileLogStorageFactory;
import org.jenkinsci.plugins.workflow.log.LogStorage;
import org.jenkinsci.plugins.workflow.log.LogStorageFactory;
import org.jenkinsci.plugins.workflow.log.LogStorageFactoryDescriptor;
import org.jenkinsci.plugins.workflow.log.configuration.mock.LogStorageFactoryMock1;
import org.jenkinsci.plugins.workflow.log.configuration.mock.LogStorageFactoryMock2;
import org.jenkinsci.plugins.workflow.log.tee.RemoteCustomFileLogStorage;
import org.jenkinsci.plugins.workflow.log.tee.TeeLogStorageFactory;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsSessionRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;

public class PipelineLoggingGlobalConfigurationTest {

    @Rule
    public JenkinsSessionRule sessions = new JenkinsSessionRule();

    @Test
    public void default_factory() throws Throwable {
        sessions.then(r -> {
            assertThat(PipelineLoggingGlobalConfiguration.get().getFactory(), nullValue());
            assertThat(
                    PipelineLoggingGlobalConfiguration.get().getFactoryOrDefault(),
                    instanceOf(FileLogStorageFactory.class));
            r.configRoundtrip();
        });
        sessions.then(r -> {
            assertThat(PipelineLoggingGlobalConfiguration.get().getFactory(), nullValue());
            assertThat(
                    PipelineLoggingGlobalConfiguration.get().getFactoryOrDefault(),
                    instanceOf(FileLogStorageFactory.class));
        });
    }

    @Test
    public void custom_default_factory() throws Throwable {
        sessions.then(r -> {
            assertThat(PipelineLoggingGlobalConfiguration.get().getFactory(), nullValue());
            assertThat(
                    PipelineLoggingGlobalConfiguration.get().getFactoryOrDefault(),
                    instanceOf(LogStorageFactoryCustom.class));
            r.configRoundtrip();
        });
        sessions.then(r -> {
            assertThat(PipelineLoggingGlobalConfiguration.get().getFactory(), nullValue());
            assertThat(
                    PipelineLoggingGlobalConfiguration.get().getFactoryOrDefault(),
                    instanceOf(LogStorageFactoryCustom.class));
        });
    }

    @Test
    public void teeLogStorageFactory() throws Throwable {
        sessions.then(r -> {
            TeeLogStorageFactory factory =
                    new TeeLogStorageFactory(new LogStorageFactoryMock1(), new LogStorageFactoryMock2());
            PipelineLoggingGlobalConfiguration.get().setFactory(factory);
            r.configRoundtrip();
        });
        sessions.then(r -> {
            var configuration = PipelineLoggingGlobalConfiguration.get();
            assertThat(configuration.getFactory(), instanceOf(TeeLogStorageFactory.class));
            var factory = (TeeLogStorageFactory) configuration.getFactory();
            assertThat(factory.getPrimary(), instanceOf(LogStorageFactoryMock1.class));
            assertThat(factory.getSecondary(), instanceOf(LogStorageFactoryMock2.class));
        });
    }

    @Test
    public void teeLogStorageFactory_primary_null() throws Throwable {
        sessions.then(r -> {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                new TeeLogStorageFactory(null, new LogStorageFactoryMock2());
            });
            assertThat(exception.getMessage(), is("Primary Pipeline logger cannot be null"));
            r.configRoundtrip();
        });
        sessions.then(r -> {
            var configuration = PipelineLoggingGlobalConfiguration.get();
            assertThat(configuration.getFactory(), nullValue());
            assertThat(configuration.getFactoryOrDefault(), instanceOf(FileLogStorageFactory.class));
        });
    }

    @Test
    public void teeLogStorageFactory_secondary_null() throws Throwable {
        sessions.then(r -> {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                new TeeLogStorageFactory(new LogStorageFactoryMock1(), null);
            });
            assertThat(exception.getMessage(), is("Secondary Pipeline logger cannot be null"));
            r.configRoundtrip();
        });
        sessions.then(r -> {
            var configuration = PipelineLoggingGlobalConfiguration.get();
            assertThat(configuration.getFactory(), nullValue());
            assertThat(configuration.getFactoryOrDefault(), instanceOf(FileLogStorageFactory.class));
        });
    }

    public static class LogStorageFactoryCustom implements LogStorageFactory {
        @DataBoundConstructor
        public LogStorageFactoryCustom() {}

        @Override
        public LogStorage forBuild(@NonNull FlowExecutionOwner b) {
            try {
                File file = new File(b.getRootDir(), "custom-log");
                return RemoteCustomFileLogStorage.forFile(file);
            } catch (Exception x) {
                return new BrokenLogStorage(x);
            }
        }

        @TestExtension("custom_default_factory")
        @Symbol("logCustom")
        public static final class DescriptorImpl extends LogStorageFactoryDescriptor<LogStorageFactoryCustom> {
            @NonNull
            @Override
            public String getDisplayName() {
                return "My custom log";
            }

            @Override
            public LogStorageFactory getDefaultInstance() {
                return new LogStorageFactoryCustom();
            }
        }
    }
}

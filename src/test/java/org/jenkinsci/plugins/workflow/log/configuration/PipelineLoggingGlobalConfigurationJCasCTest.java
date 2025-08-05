package org.jenkinsci.plugins.workflow.log.configuration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.jenkins.plugins.casc.ConfiguratorException;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import java.io.File;
import org.htmlunit.html.HtmlForm;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.log.BrokenLogStorage;
import org.jenkinsci.plugins.workflow.log.FileLogStorage;
import org.jenkinsci.plugins.workflow.log.LogStorage;
import org.jenkinsci.plugins.workflow.log.LogStorageFactory;
import org.jenkinsci.plugins.workflow.log.LogStorageFactoryDescriptor;
import org.jenkinsci.plugins.workflow.log.configuration.mock.LogStorageFactoryMock1;
import org.jenkinsci.plugins.workflow.log.configuration.mock.LogStorageFactoryMock2;
import org.jenkinsci.plugins.workflow.log.tee.RemoteCustomFileLogStorage;
import org.jenkinsci.plugins.workflow.log.tee.TeeLogStorage;
import org.jenkinsci.plugins.workflow.log.tee.TeeLogStorageFactory;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;

public class PipelineLoggingGlobalConfigurationJCasCTest {

    @Rule
    public JenkinsConfiguredWithCodeRule r = new JenkinsConfiguredWithCodeRule();

    @Test
    public void default_factory() throws Throwable {
        PipelineLoggingGlobalConfiguration config = PipelineLoggingGlobalConfiguration.get();
        assertThat(config.getFactory(), nullValue());

        // check build happens with the default file log storage
        WorkflowJob workflowJob = r.createProject(WorkflowJob.class);
        workflowJob.setDefinition(new CpsFlowDefinition("echo 'Hello World'", true));

        r.buildAndAssertSuccess(workflowJob);
        assertThat(LogStorage.of(workflowJob.getLastBuild().asFlowExecutionOwner()), instanceOf(FileLogStorage.class));

        checkNoPipelineLoggingCasCConfiguration();
    }

    @Test
    public void custom_default_factory() throws Throwable {
        PipelineLoggingGlobalConfiguration config = PipelineLoggingGlobalConfiguration.get();
        assertThat(config.getFactory(), nullValue());

        // check build happens with the default file log storage
        WorkflowJob workflowJob = r.createProject(WorkflowJob.class);
        workflowJob.setDefinition(new CpsFlowDefinition("echo 'Hello World'", true));

        r.buildAndAssertSuccess(workflowJob);
        assertThat(
                LogStorage.of(workflowJob.getLastBuild().asFlowExecutionOwner()),
                instanceOf(RemoteCustomFileLogStorage.class));

        checkNoPipelineLoggingCasCConfiguration();
    }

    @Test
    public void custom_default_factory_ui() throws Throwable {
        HtmlForm form = form = r.createWebClient().goTo("configure").getFormByName("config");
        // not sure if there's a simpler way to get the select, as no `name` or `id` attribute is available
        var selectedText =
                form.getFirstByXPath("//*[@id='pipeline-logging']/../descendant::select/option[@selected]/text()");
        assertThat(selectedText, nullValue());
        var description = form.getFirstByXPath("//*[@id='pipeline-logging']/../descendant::div[@colspan]/text()");
        assertThat(description.toString(), containsString("My custom log"));
        checkNoPipelineLoggingCasCConfiguration();
    }

    @Test
    @ConfiguredWithCode("jcasc_smokes.yaml")
    public void smokes() throws Throwable {
        PipelineLoggingGlobalConfiguration config = PipelineLoggingGlobalConfiguration.get();
        assertThat(config.getFactory(), instanceOf(TeeLogStorageFactory.class));
        var factory = (TeeLogStorageFactory) config.getFactory();
        assertThat(factory.getPrimary(), instanceOf(LogStorageFactoryMock1.class));
        assertThat(factory.getSecondary(), instanceOf(LogStorageFactoryMock2.class));

        WorkflowJob workflowJob = r.createProject(WorkflowJob.class);
        workflowJob.setDefinition(new CpsFlowDefinition("echo 'Hello World'", true));

        r.buildAndAssertSuccess(workflowJob);
        assertThat(LogStorage.of(workflowJob.getLastBuild().asFlowExecutionOwner()), instanceOf(TeeLogStorage.class));

        String content = r.exportToString(true);
        assertThat(content, containsString("pipelineLogging"));
        assertThat(content, containsString("tee"));
        assertThat(content, containsString("logMock1"));
        assertThat(content, containsString("logMock2"));
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

    private void checkNoPipelineLoggingCasCConfiguration() throws Exception {
        r.configRoundtrip();
        // check exported CasC
        String content = r.exportToString(true);
        assertThat(content, not(containsString("pipelineLogging")));
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

        @TestExtension({"custom_default_factory", "custom_default_factory_ui"})
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

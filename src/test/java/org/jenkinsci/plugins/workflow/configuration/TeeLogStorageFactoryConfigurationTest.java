package org.jenkinsci.plugins.workflow.configuration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import org.htmlunit.html.HtmlCheckBoxInput;
import org.htmlunit.html.HtmlDivision;
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
            assertThat(TeeLogStorageFactoryConfiguration.get().isEnabled(), is(false));
            TeeLogStorageFactoryConfiguration.get().setEnabled(true);
            assertThat(TeeLogStorageFactoryConfiguration.get().isEnabled(), is(true));

            assertThat(TeeLogStorageFactoryConfiguration.get().getWrappers(), nullValue());
            var wrapper1 = new TeeLogStorageFactoryWrapper("TeeLogStorageFactoryMock1");
            var wrapper2 = new TeeLogStorageFactoryWrapper("TeeLogStorageFactoryMock2");
            TeeLogStorageFactoryConfiguration.get().setWrappers(List.of(wrapper1, wrapper2));
            assertThat(TeeLogStorageFactoryConfiguration.get().getWrappers(), hasItems(wrapper1, wrapper2));
            r.configRoundtrip();
        });
        sessions.then(r -> {
            assertThat(
                    TeeLogStorageFactoryConfiguration.get().getWrappers(),
                    contains(
                            hasProperty("typeId", is("TeeLogStorageFactoryMock1")),
                            hasProperty("typeId", is("TeeLogStorageFactoryMock2"))));
            // check the items have their labels properly displayed
            var form = r.createWebClient().goTo("configure").getFormByName("config");
            HtmlCheckBoxInput checkbox = form.getInputByName("teeLogStorageEnabled");
            assertThat(checkbox.isChecked(), is(true));

            List<HtmlDivision> divs = form.getByXPath("//div[starts-with(@descriptorid, 'TeeLogStorageFactoryMock')]");
            assertThat(divs.size(), is(2));
            assertThat(divs.get(0).getAttribute("descriptorid"), is("TeeLogStorageFactoryMock1"));
            assertThat(divs.get(1).getAttribute("descriptorid"), is("TeeLogStorageFactoryMock2"));
            assertThat(divs.get(0).getTextContent(), containsString("Tee Log Storage Factory Mock 1"));
            assertThat(divs.get(1).getTextContent(), containsString("Tee Log Storage Factory Mock 2"));
        });
    }

    @Test
    @LocalData
    public void unknown_factory() throws Throwable {
        sessions.then(r -> {
            assertThat(TeeLogStorageFactoryConfiguration.get().isEnabled(), is(true));
            assertThat(
                    TeeLogStorageFactoryConfiguration.get().getWrappers(),
                    contains(
                            hasProperty("typeId", is("TeeLogStorageFactoryMock2")),
                            hasProperty("typeId", is("TeeLogStorageFactoryMock1"))));
        });
    }

    @TestExtension
    public static class TeeLogStorageFactoryMock1 implements TeeLogStorageFactory {
        @Override
        public String getId() {
            return "TeeLogStorageFactoryMock1";
        }

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
        public String getId() {
            return "TeeLogStorageFactoryMock2";
        }

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

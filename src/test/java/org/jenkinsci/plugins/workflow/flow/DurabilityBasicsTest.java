package org.jenkinsci.plugins.workflow.flow;

import jenkins.model.Jenkins;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.RestartableJenkinsRule;

/**
 * @author Sam Van Oort
 */
public class DurabilityBasicsTest {

    @Rule
    public RestartableJenkinsRule r = new RestartableJenkinsRule();

    @Test
    public void configRoundTrip() throws Exception {
        r.then(r -> {
            GlobalDefaultFlowDurabilityLevel.DescriptorImpl level = r.jenkins.getExtensionList(GlobalDefaultFlowDurabilityLevel.DescriptorImpl.class).get(0);
            level.setDurabilityHint(FlowDurabilityHint.PERFORMANCE_OPTIMIZED);
            r.configRoundtrip();
            Assert.assertEquals(FlowDurabilityHint.PERFORMANCE_OPTIMIZED, level.getDurabilityHint());
            level.setDurabilityHint(null);
            r.configRoundtrip();
            Assert.assertNull(level.getDurabilityHint());

            // Customize again so we can check for persistence
            level.setDurabilityHint(FlowDurabilityHint.PERFORMANCE_OPTIMIZED);
            Assert.assertEquals(FlowDurabilityHint.PERFORMANCE_OPTIMIZED, level.getDurabilityHint());
        });
        r.then(r -> {
            GlobalDefaultFlowDurabilityLevel.DescriptorImpl level = r.jenkins.getExtensionList(GlobalDefaultFlowDurabilityLevel.DescriptorImpl.class).get(0);
            Assert.assertEquals(FlowDurabilityHint.PERFORMANCE_OPTIMIZED, level.getDurabilityHint());
        });
    }

    @Test
    public void defaultHandling() throws Exception {
        r.then(r -> {
            Assert.assertEquals(GlobalDefaultFlowDurabilityLevel.SUGGESTED_DURABILITY_HINT, GlobalDefaultFlowDurabilityLevel.getDefaultDurabilityHint());
            GlobalDefaultFlowDurabilityLevel.DescriptorImpl level = r.jenkins.getExtensionList(GlobalDefaultFlowDurabilityLevel.DescriptorImpl.class).get(0);
            level.setDurabilityHint(FlowDurabilityHint.PERFORMANCE_OPTIMIZED);
            Assert.assertEquals(FlowDurabilityHint.PERFORMANCE_OPTIMIZED, GlobalDefaultFlowDurabilityLevel.getDefaultDurabilityHint());
        });
    }
}

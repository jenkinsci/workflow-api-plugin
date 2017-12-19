package org.jenkinsci.plugins.workflow.flow;

import jenkins.model.Jenkins;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * @author Sam Van Oort
 */
public class DurabilityBasicsTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Test
    public void configRoundTrip() throws Exception {
        GlobalDefaultFlowDurabilityLevel.DescriptorImpl level = Jenkins.getInstance().getExtensionList(GlobalDefaultFlowDurabilityLevel.DescriptorImpl.class).get(0);
        level.setDurabilityHint(FlowDurabilityHint.PERFORMANCE_OPTIMIZED);
        r.configRoundtrip();
        Assert.assertEquals(FlowDurabilityHint.PERFORMANCE_OPTIMIZED, level.getDurabilityHint());
        level.setDurabilityHint(null);
        r.configRoundtrip();
        Assert.assertEquals(null, level.getDurabilityHint());
    }

    @Test
    public void defaultHandling() throws Exception {
        Assert.assertEquals(GlobalDefaultFlowDurabilityLevel.SUGGESTED_DURABILITY_HINT, GlobalDefaultFlowDurabilityLevel.getDefaultDurabilityHint());
        GlobalDefaultFlowDurabilityLevel.DescriptorImpl level = Jenkins.getInstance().getExtensionList(GlobalDefaultFlowDurabilityLevel.DescriptorImpl.class).get(0);
        level.setDurabilityHint(FlowDurabilityHint.PERFORMANCE_OPTIMIZED);
        Assert.assertEquals(FlowDurabilityHint.PERFORMANCE_OPTIMIZED, GlobalDefaultFlowDurabilityLevel.getDefaultDurabilityHint());
    }
}

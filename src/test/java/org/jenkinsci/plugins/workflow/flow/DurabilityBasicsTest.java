package org.jenkinsci.plugins.workflow.flow;

import hudson.Functions;
import hudson.model.Descriptor;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.ACLContext;
import jenkins.model.Jenkins;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.JenkinsSessionRule;

import java.util.Collection;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.junit.Assert.assertTrue;

/**
 * @author Sam Van Oort
 */
public class DurabilityBasicsTest {

    @Rule
    public JenkinsSessionRule sessions = new JenkinsSessionRule();

    @Test
    public void configRoundTrip() throws Throwable {
        sessions.then(j -> {
            GlobalDefaultFlowDurabilityLevel.DescriptorImpl level = j.jenkins.getExtensionList(GlobalDefaultFlowDurabilityLevel.DescriptorImpl.class).get(0);
            level.setDurabilityHint(FlowDurabilityHint.PERFORMANCE_OPTIMIZED);
            j.configRoundtrip();
            Assert.assertEquals(FlowDurabilityHint.PERFORMANCE_OPTIMIZED, level.getDurabilityHint());
            level.setDurabilityHint(null);
            j.configRoundtrip();
            Assert.assertNull(level.getDurabilityHint());

            // Customize again so we can check for persistence
            level.setDurabilityHint(FlowDurabilityHint.PERFORMANCE_OPTIMIZED);
            Assert.assertEquals(FlowDurabilityHint.PERFORMANCE_OPTIMIZED, level.getDurabilityHint());
        });
        sessions.then(j -> {
            GlobalDefaultFlowDurabilityLevel.DescriptorImpl level = j.jenkins.getExtensionList(GlobalDefaultFlowDurabilityLevel.DescriptorImpl.class).get(0);
            Assert.assertEquals(FlowDurabilityHint.PERFORMANCE_OPTIMIZED, level.getDurabilityHint());
        });
    }

    @Test
    public void defaultHandling() throws Throwable {
        sessions.then(j -> {
            Assert.assertEquals(GlobalDefaultFlowDurabilityLevel.SUGGESTED_DURABILITY_HINT, GlobalDefaultFlowDurabilityLevel.getDefaultDurabilityHint());
            GlobalDefaultFlowDurabilityLevel.DescriptorImpl level = j.jenkins.getExtensionList(GlobalDefaultFlowDurabilityLevel.DescriptorImpl.class).get(0);
            level.setDurabilityHint(FlowDurabilityHint.PERFORMANCE_OPTIMIZED);
            Assert.assertEquals(FlowDurabilityHint.PERFORMANCE_OPTIMIZED, GlobalDefaultFlowDurabilityLevel.getDefaultDurabilityHint());
        });
    }

    @Test
    public void managePermissionShouldAccessGlobalConfig() throws Throwable {
        sessions.then(j -> {
            final String USER = "user";
            final String MANAGER = "manager";
            j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
            j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                    // Read access
                    .grant(Jenkins.READ).everywhere().to(USER)

                    // Read and Manage
                    .grant(Jenkins.READ).everywhere().to(MANAGER)
                    .grant(Jenkins.MANAGE).everywhere().to(MANAGER)
            );

            try (ACLContext c = ACL.as(User.getById(USER, true))) {
                Collection<Descriptor> descriptors = Functions.getSortedDescriptorsForGlobalConfigUnclassified();
                assertThat("Global configuration should not be accessible to READ users", descriptors, empty());
            }
            try (ACLContext c = ACL.as(User.getById(MANAGER, true))) {
                Collection<Descriptor> descriptors = Functions.getSortedDescriptorsForGlobalConfigUnclassified();
                Optional<Descriptor> found = descriptors.stream().filter(descriptor -> descriptor instanceof GlobalDefaultFlowDurabilityLevel.DescriptorImpl).findFirst();
                assertTrue("Global configuration should be accessible to MANAGE users", found.isPresent());
            }
        });
    }
}

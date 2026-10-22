package org.jenkinsci.plugins.workflow.flow;

import hudson.Functions;
import hudson.model.Descriptor;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.ACLContext;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.JenkinsSessionExtension;

import java.util.Collection;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Sam Van Oort
 */
class DurabilityBasicsTest {

    @RegisterExtension
    private final JenkinsSessionExtension sessions = new JenkinsSessionExtension();

    @Test
    void configRoundTrip() throws Throwable {
        sessions.then(j -> {
            GlobalDefaultFlowDurabilityLevel.DescriptorImpl level = j.jenkins.getExtensionList(GlobalDefaultFlowDurabilityLevel.DescriptorImpl.class).get(0);
            level.setDurabilityHint(FlowDurabilityHint.PERFORMANCE_OPTIMIZED);
            j.configRoundtrip();
            assertEquals(FlowDurabilityHint.PERFORMANCE_OPTIMIZED, level.getDurabilityHint());
            level.setDurabilityHint(null);
            j.configRoundtrip();
            assertNull(level.getDurabilityHint());

            // Customize again so we can check for persistence
            level.setDurabilityHint(FlowDurabilityHint.PERFORMANCE_OPTIMIZED);
            assertEquals(FlowDurabilityHint.PERFORMANCE_OPTIMIZED, level.getDurabilityHint());
        });
        sessions.then(j -> {
            GlobalDefaultFlowDurabilityLevel.DescriptorImpl level = j.jenkins.getExtensionList(GlobalDefaultFlowDurabilityLevel.DescriptorImpl.class).get(0);
            assertEquals(FlowDurabilityHint.PERFORMANCE_OPTIMIZED, level.getDurabilityHint());
        });
    }

    @Test
    void defaultHandling() throws Throwable {
        sessions.then(j -> {
            assertEquals(GlobalDefaultFlowDurabilityLevel.SUGGESTED_DURABILITY_HINT, GlobalDefaultFlowDurabilityLevel.getDefaultDurabilityHint());
            GlobalDefaultFlowDurabilityLevel.DescriptorImpl level = j.jenkins.getExtensionList(GlobalDefaultFlowDurabilityLevel.DescriptorImpl.class).get(0);
            level.setDurabilityHint(FlowDurabilityHint.PERFORMANCE_OPTIMIZED);
            assertEquals(FlowDurabilityHint.PERFORMANCE_OPTIMIZED, GlobalDefaultFlowDurabilityLevel.getDefaultDurabilityHint());
        });
    }

    @Test
    void managePermissionShouldAccessGlobalConfig() throws Throwable {
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
                assertTrue(found.isPresent(), "Global configuration should be accessible to MANAGE users");
            }
        });
    }
}

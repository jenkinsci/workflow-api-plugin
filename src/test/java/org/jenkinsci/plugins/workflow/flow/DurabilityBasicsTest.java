package org.jenkinsci.plugins.workflow.flow;

import hudson.ExtensionList;
import hudson.Functions;
import hudson.model.Descriptor;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.security.Permission;
import hudson.util.ReflectionUtils;
import jenkins.model.Jenkins;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.RestartableJenkinsRule;

import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.Optional;

import static org.junit.Assert.assertTrue;

/**
 * @author Sam Van Oort
 */
public class DurabilityBasicsTest {

    @Rule
    public RestartableJenkinsRule r = new RestartableJenkinsRule();

    @Test
    public void configRoundTrip() {
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
    public void defaultHandling() {
        r.then(r -> {
            Assert.assertEquals(GlobalDefaultFlowDurabilityLevel.SUGGESTED_DURABILITY_HINT, GlobalDefaultFlowDurabilityLevel.getDefaultDurabilityHint());
            GlobalDefaultFlowDurabilityLevel.DescriptorImpl level = r.jenkins.getExtensionList(GlobalDefaultFlowDurabilityLevel.DescriptorImpl.class).get(0);
            level.setDurabilityHint(FlowDurabilityHint.PERFORMANCE_OPTIMIZED);
            Assert.assertEquals(FlowDurabilityHint.PERFORMANCE_OPTIMIZED, GlobalDefaultFlowDurabilityLevel.getDefaultDurabilityHint());
        });
    }

    @Test
    public void managePermissionShouldAccessGlobalConfig() {
        r.then(r -> {
            Permission jenkinsManage;
            try {
                jenkinsManage = getJenkinsManage();
            } catch (Exception e) {
                Assume.assumeTrue("Jenkins baseline is too old for this test (requires Jenkins.MANAGE)", false);
                return;
            }
            final String USER = "user";
            final String MANAGER = "manager";
            r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
            r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                    // Read access
                    .grant(Jenkins.READ).everywhere().to(USER)

                    // Read and Manage
                    .grant(Jenkins.READ).everywhere().to(MANAGER)
                    .grant(jenkinsManage).everywhere().to(MANAGER)
            );

            try (ACLContext c = ACL.as(User.getById(USER, true))) {
                Collection<Descriptor> descriptors = Functions.getSortedDescriptorsForGlobalConfigUnclassified();
                assertTrue("Global configuration should not be accessible to READ users", descriptors.size() == 0);
            }
            try (ACLContext c = ACL.as(User.getById(MANAGER, true))) {
                Collection<Descriptor> descriptors = Functions.getSortedDescriptorsForGlobalConfigUnclassified();
                Optional<Descriptor> found = descriptors.stream().filter(descriptor -> descriptor instanceof GlobalDefaultFlowDurabilityLevel.DescriptorImpl).findFirst();
                assertTrue("Global configuration should be accessible to MANAGE users", found.isPresent());
            }
        });
    }

    // TODO: remove when Jenkins core baseline is 2.222+
    private Permission getJenkinsManage() throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        // Jenkins.MANAGE is available starting from Jenkins 2.222 (https://jenkins.io/changelog/#v2.222). See JEP-223 for more info
        return (Permission) ReflectionUtils.getPublicProperty(Jenkins.get(), "MANAGE");
    }
}

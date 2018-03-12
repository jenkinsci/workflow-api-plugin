/*
 * The MIT License
 *
 * Copyright 2018 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jenkinsci.plugins.workflow;

import hudson.FilePath;
import hudson.Functions;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.slaves.DumbSlave;
import hudson.tasks.ArtifactArchiver;
import java.io.InputStream;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import jenkins.model.ArtifactManager;
import jenkins.model.ArtifactManagerConfiguration;
import jenkins.model.ArtifactManagerFactory;
import jenkins.security.MasterToSlaveCallable;
import jenkins.util.VirtualFile;
import org.apache.commons.io.IOUtils;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * {@link #run} allows an implementation of {@link ArtifactManager} plus {@link VirtualFile} to be run through a standard gantlet of tests.
 */
public class ArtifactManagerTest {

    @Rule public JenkinsRule r = new JenkinsRule();

    public static void run(@Nonnull JenkinsRule r, @CheckForNull ArtifactManagerFactory factory) throws Exception {
        // Certainly file mode tests will not work on Windows; symlink tests may or may not; and who knows about weird characters on NTFS:
        boolean platformSpecifics = !Functions.isWindows();
        if (factory != null) {
            ArtifactManagerConfiguration.get().getArtifactManagerFactories().add(factory);
        }
        DumbSlave upstreamNode = r.createOnlineSlave();
        FreeStyleProject p = r.createFreeStyleProject();
        p.setAssignedNode(upstreamNode);
        setUpWorkspace(upstreamNode.getWorkspaceFor(p), platformSpecifics);
        p.getPublishersList().add(new ArtifactArchiver("**"));
        FreeStyleBuild b = r.buildAndAssertSuccess(p);
        VirtualFile root = b.getArtifactManager().root();
        VirtualFile remotable = root.asRemotable();
        if (remotable != null) {
            r.createOnlineSlave().getChannel().call(new Verify(remotable, platformSpecifics));
        } else {
            new Verify(root, platformSpecifics).call();
        }
    }

    private static void setUpWorkspace(FilePath workspace, boolean platformSpecifics) throws Exception {
        workspace.child("file").write("content", null);
        // TODO deeply nested subdirectories
        // TODO files matching default excludes
        if (platformSpecifics) {
            // TODO symlinks
            // TODO file modes
            // TODO unusual filename characters
        }
        // best to avoid scalability tests (large number of files, single large file) here—too fragile
    }

    private static class Verify extends MasterToSlaveCallable<Void, Exception> {

        private final VirtualFile root;
        private final boolean platformSpecifics;

        Verify(VirtualFile root, boolean platformSpecifics) {
            this.root = root;
            this.platformSpecifics = platformSpecifics;
        }

        @Override public Void call() throws Exception {
            VirtualFile file = root.child("file");
            try (InputStream is = file.open()) {
                assertEquals("content", IOUtils.toString(is));
            }
            assertArrayEquals(new VirtualFile[] {file}, root.list());
            assertThat(root.list("file", null, false), containsInAnyOrder("file"));
            // TODO everything interesting in VirtualFileTest and then some
            return null;
        }

    }

    /** Run the standard one, as a control. */
    @Test public void standard() throws Exception {
        run(r, null);
    }

    /** Check that {@link #run} complies with the expectations of {@link DirectArtifactManagerFactory}. */
    @Test public void direct() throws Exception {
        run(r, new DirectArtifactManagerFactory());
    }

}

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

import hudson.AbortException;
import hudson.EnvVars;
import hudson.ExtensionList;
import hudson.FilePath;
import hudson.Functions;
import hudson.Launcher;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.TaskListener;
import hudson.slaves.DumbSlave;
import hudson.tasks.ArtifactArchiver;
import hudson.util.StreamTaskListener;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.logging.Level;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import jenkins.model.ArtifactManager;
import jenkins.model.ArtifactManagerConfiguration;
import jenkins.model.ArtifactManagerFactory;
import jenkins.model.StandardArtifactManager;
import jenkins.security.MasterToSlaveCallable;
import jenkins.util.VirtualFile;
import org.apache.commons.io.IOUtils;
import static org.hamcrest.Matchers.*;
import org.jenkinsci.plugins.workflow.flow.StashManager;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;

/**
 * {@link #run} allows an implementation of {@link ArtifactManager} plus {@link VirtualFile} to be run through a standard gantlet of tests.
 */
public class ArtifactManagerTest {

    @Rule public JenkinsRule r = new JenkinsRule();
    @Rule public LoggerRule logging = new LoggerRule();

    public static void run(@Nonnull JenkinsRule r, @CheckForNull ArtifactManagerFactory factory, boolean weirdCharacters) throws Exception {
        if (factory != null) {
            ArtifactManagerConfiguration.get().getArtifactManagerFactories().add(factory);
        }
        DumbSlave upstreamNode = r.createOnlineSlave();
        FreeStyleProject p = r.createFreeStyleProject();
        p.setAssignedNode(upstreamNode);
        FilePath upstreamWS = upstreamNode.getWorkspaceFor(p);
        setUpWorkspace(upstreamWS, weirdCharacters);
        ArtifactArchiver aa = new ArtifactArchiver("**");
        aa.setDefaultExcludes(false);
        p.getPublishersList().add(aa);
        FreeStyleBuild b = r.buildAndAssertSuccess(p);
        VirtualFile root = b.getArtifactManager().root();
        VirtualFile remotable = root.asRemotable();
        TaskListener listener = StreamTaskListener.fromStderr();
        DumbSlave downstreamNode;
        if (remotable != null) {
            downstreamNode = r.createOnlineSlave();
            downstreamNode.getChannel().call(new Verify(listener, remotable, weirdCharacters));
        } else {
            downstreamNode = null;
            new Verify(listener, root, weirdCharacters).call();
        }
        if (b.getArtifactManager() instanceof StashManager.StashAwareArtifactManager) {
            Launcher launcher = upstreamNode.createLauncher(listener);
            EnvVars env = upstreamNode.toComputer().getEnvironment();
            env.putAll(upstreamNode.toComputer().buildEnvironment(listener));
            // Make sure we can stash and then unstash within a build:
            StashManager.stash(b, "stuff", upstreamWS, launcher, env, listener, "file", null, false, false);
            upstreamWS.child("file").delete();
            StashManager.unstash(b, "stuff", upstreamWS, launcher, env, listener);
            assertEquals("content", upstreamWS.child("file").readToString());
            upstreamWS.child("file").delete();
            // Copy stashes and artifacts from one build to a second one:
            p.getPublishersList().clear();
            FreeStyleBuild b2 = r.buildAndAssertSuccess(p);
            ExtensionList.lookupSingleton(StashManager.CopyStashesAndArtifacts.class).copy(b, b2, listener);
            // Verify the copied stashes:
            StashManager.unstash(b2, "stuff", upstreamWS, launcher, env, listener);
            assertEquals("content", upstreamWS.child("file").readToString());
            // And the copied artifacts:
            root = b2.getArtifactManager().root();
            remotable = root.asRemotable();
            if (remotable != null) {
                downstreamNode.getChannel().call(new Verify(listener, remotable, weirdCharacters));
            } else {
                new Verify(listener, root, weirdCharacters).call();
            }
            // Also delete the original:
            StashManager.clearAll(b, listener);
            // Stashes should have been deleted, but not artifacts:
            assertTrue(b.getArtifactManager().root().child("file").isFile());
            upstreamWS.deleteContents();
            assertFalse(upstreamWS.child("file").exists());
            try {
                StashManager.unstash(b, "stuff", upstreamWS, launcher, env, listener);
                fail("should not have succeeded in unstashing");
            } catch (AbortException x) {
                System.err.println("caught as expected: " + x);
            }
            assertFalse(upstreamWS.child("file").exists());
        }
        // Also check deletion:
        assertTrue(b.getArtifactManager().delete());
        assertFalse(b.getArtifactManager().root().child("file").isFile());
        assertFalse(b.getArtifactManager().delete());
    }

    private static void setUpWorkspace(FilePath workspace, boolean weirdCharacters) throws Exception {
        workspace.child("file").write("content", null);
        workspace.child("some/deeply/nested/dir/subfile").write("content", null);
        workspace.child(".git/config").write("whatever", null);
        workspace.child("otherdir/somefile~").write("whatever", null);
        if (weirdCharacters) {
            workspace.child("otherdir/xxx#?:$&'\"<>čॐ").write("whatever", null);
        }
        // best to avoid scalability tests (large number of files, single large file) here—too fragile
        // also avoiding tests of file mode and symlinks: will not work on Windows, and may or may not work in various providers
    }

    private static class Verify extends MasterToSlaveCallable<Void, Exception> {

        private final TaskListener listener;
        private final VirtualFile root;
        private final boolean weirdCharacters;

        Verify(TaskListener listener, VirtualFile root, boolean weirdCharacters) {
            this.listener = listener;
            this.root = root;
            this.weirdCharacters = weirdCharacters;
        }

        @Override public Void call() throws Exception {
            assertThat("root name is unspecified generally", root.getName(), not(endsWith("/")));
            VirtualFile file = root.child("file");
            assertEquals("file", file.getName());
            assertFile(file, "content", listener);
            assertEquals(root, file.getParent());
            VirtualFile some = root.child("some");
            assertEquals("some", some.getName());
            assertDir(some);
            assertEquals(root, some.getParent());
            assertThat(root.list(), arrayContainingInAnyOrder(file, some, root.child("otherdir"), root.child(".git")));
            assertThat(root.list("file", null, false), containsInAnyOrder("file"));
            VirtualFile subfile = root.child("some/deeply/nested/dir/subfile");
            assertEquals("subfile", subfile.getName());
            assertFile(subfile, "content", listener);
            VirtualFile someDeeplyNestedDir = some.child("deeply/nested/dir");
            assertEquals("dir", someDeeplyNestedDir.getName());
            assertDir(someDeeplyNestedDir);
            assertEquals(some, someDeeplyNestedDir.getParent().getParent().getParent());
            assertEquals(Collections.singletonList(subfile), Arrays.asList(someDeeplyNestedDir.list()));
            assertThat(someDeeplyNestedDir.list("subfile", null, false), containsInAnyOrder("subfile"));
            assertThat(root.list("**/*file", null, false), containsInAnyOrder("file", "some/deeply/nested/dir/subfile"));
            assertThat(some.list("**/*file", null, false), containsInAnyOrder("deeply/nested/dir/subfile"));
            assertThat(root.list("**", "**/xxx*", true), containsInAnyOrder("file", "some/deeply/nested/dir/subfile"));
            if (weirdCharacters) {
                assertFile(root.child("otherdir/xxx#?:$&'\"<>čॐ"), "whatever", listener);
            }
            assertNonexistent(root.child("does-not-exist"));
            assertNonexistent(root.child("some/deeply/nested/dir/does-not-exist"));
            return null;
        }

    }

    private static void assertFile(VirtualFile f, String contents, TaskListener listener) throws Exception {
        assertTrue(f.isFile());
        assertFalse(f.isDirectory());
        assertTrue(f.exists());
        assertEquals(contents.length(), f.length());
        assertThat(f.lastModified(), not(is(0)));
        try (InputStream is = f.open()) {
            assertEquals(contents, IOUtils.toString(is));
        }
        URL url = f.toExternalURL();
        if (url != null) { // TODO try to do this in a docker slave, so we can be sure the environment is not affecting anything
            listener.getLogger().println("opening " + url);
            try (InputStream is = url.openStream()) {
                assertEquals(contents, IOUtils.toString(is));
            }
        }
    }

    private static void assertDir(VirtualFile f) throws Exception {
        assertFalse(f.isFile());
        assertTrue(f.isDirectory());
        assertTrue(f.exists());
        // length & lastModified may or may not be defined
    }

    private static void assertNonexistent(VirtualFile f) throws Exception {
        assertFalse(f.isFile());
        assertFalse(f.isDirectory());
        assertFalse(f.exists());
        try {
            assertEquals(0, f.length());
        } catch (IOException x) {
            // also OK
        }
        try {
            assertEquals(0, f.lastModified());
        } catch (IOException x) {
            // also OK
        }
    }

    /** Run the standard one, as a control. */
    @Test public void standard() throws Exception {
        logging.record(StandardArtifactManager.class, Level.FINE);
        // Who knows about weird characters on NTFS; also case-sensitivity could confuse things
        run(r, null, !Functions.isWindows());
    }

    /** Check that {@link #run} complies with the expectations of {@link DirectArtifactManagerFactory}. */
    @Test public void direct() throws Exception {
        run(r, new DirectArtifactManagerFactory(), !Functions.isWindows());
    }

}

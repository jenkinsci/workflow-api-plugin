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
import hudson.Util;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.TaskListener;
import hudson.plugins.sshslaves.SSHLauncher;
import hudson.remoting.Callable;
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
import jenkins.model.Jenkins;
import jenkins.model.StandardArtifactManager;
import jenkins.security.MasterToSlaveCallable;
import jenkins.util.VirtualFile;
import org.apache.commons.io.IOUtils;
import static org.hamcrest.Matchers.*;
import org.jenkinsci.plugins.workflow.flow.StashManager;
import org.jenkinsci.test.acceptance.docker.Docker;
import org.jenkinsci.test.acceptance.docker.DockerImage;
import org.jenkinsci.test.acceptance.docker.fixtures.JavaContainer;
import static org.junit.Assert.*;
import org.junit.BeforeClass;
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
    
    private static DockerImage image;
    
    @BeforeClass public static void doPrepareImage() throws Exception {
        image = prepareImage();
    }

    public static @CheckForNull DockerImage prepareImage() throws Exception {
        Docker docker = new Docker();
        if (docker.isAvailable()) {
            return docker.build(JavaContainer.class);
        } else {
            System.err.println("No Docker support; falling back to running tests against an agent in a process on the same machine.");
            return null;
        }
    }
    
    private static void wrapInContainer(@Nonnull JenkinsRule r, @CheckForNull ArtifactManagerFactory factory,
            boolean weirdCharacters, TestFunction f) throws Exception {
        if (factory != null) {
            ArtifactManagerConfiguration.get().getArtifactManagerFactories().add(factory);
        }
        JavaContainer runningContainer = null;
        try {
            DumbSlave agent;
            if (image != null) {
                runningContainer = image.start(JavaContainer.class).start();
                agent = new DumbSlave("test-agent", "/home/test/slave", new SSHLauncher(runningContainer.ipBound(22), runningContainer.port(22), "test", "test", "", ""));
                Jenkins.get().addNode(agent);
                r.waitOnline(agent);
            } else {
                agent = r.createOnlineSlave();
            }
            FreeStyleProject p = r.createFreeStyleProject();
            p.setAssignedNode(agent);
            FilePath ws = agent.getWorkspaceFor(p);
            setUpWorkspace(ws, weirdCharacters);
            ArtifactArchiver aa = new ArtifactArchiver("**");
            aa.setDefaultExcludes(false);
            p.getPublishersList().add(aa);
            FreeStyleBuild b = r.buildAndAssertSuccess(p);
            f.apply(agent, p, b, ws);
        } finally {
            if (runningContainer != null) {
                runningContainer.close();
            }
        }
    }

    /**
     * @param image use {@link #prepareImage} in a {@link BeforeClass} block
     */
    public static void artifactArchive(@Nonnull JenkinsRule r, @CheckForNull ArtifactManagerFactory factory, boolean weirdCharacters, @CheckForNull DockerImage image) throws Exception {
        wrapInContainer(r, factory, weirdCharacters, new TestFunction() {
            @Override
            public void apply(DumbSlave agent, FreeStyleProject p, FreeStyleBuild b, FilePath ws) throws Exception {
                VirtualFile root = b.getArtifactManager().root();
                new Verify(agent, root, weirdCharacters).run();
                // should not delete
                assertFalse(b.getArtifactManager().delete());
                assertTrue(b.getArtifactManager().root().child("file").isFile());
            }
        });
    }

    /**
     * @param image use {@link #prepareImage} in a {@link BeforeClass} block
     */
    public static void artifactArchiveAndDelete(@Nonnull JenkinsRule r, @CheckForNull ArtifactManagerFactory factory, boolean weirdCharacters, @CheckForNull DockerImage image) throws Exception {
        wrapInContainer(r, factory, weirdCharacters, new TestFunction() {
            @Override
            public void apply(DumbSlave agent, FreeStyleProject p, FreeStyleBuild b, FilePath ws) throws Exception {
                VirtualFile root = b.getArtifactManager().root();
                new Verify(agent, root, weirdCharacters).run();
                // Also check deletion:
                assertTrue(b.getArtifactManager().delete());
                assertFalse(b.getArtifactManager().root().child("file").isFile());
                assertFalse(b.getArtifactManager().delete());
            }
        });
    }

    /**
     * @param image use {@link #prepareImage} in a {@link BeforeClass} block
     */
    public static void artifactStash(@Nonnull JenkinsRule r, @CheckForNull ArtifactManagerFactory factory, boolean weirdCharacters, @CheckForNull DockerImage image) throws Exception {
        wrapInContainer(r, factory, weirdCharacters, new StashFunction(r, weirdCharacters, new TestStashFunction() {
            @Override
            public void apply(FreeStyleProject p, FreeStyleBuild b, FilePath ws, Launcher launcher, EnvVars env,
                    TaskListener listener) throws Exception {
                // should not have deleted
                StashManager.unstash(b, "stuff", ws, launcher, env, listener);
                assertTrue(ws.child("file").exists());
            }}));
    }

    /**
     * @param image use {@link #prepareImage} in a {@link BeforeClass} block
     */
    public static void artifactStashAndDelete(@Nonnull JenkinsRule r, @CheckForNull ArtifactManagerFactory factory, boolean weirdCharacters, @CheckForNull DockerImage image) throws Exception {
        wrapInContainer(r, factory, weirdCharacters, new StashFunction(r, weirdCharacters, new TestStashFunction() {
            @Override
            public void apply(FreeStyleProject p, FreeStyleBuild b, FilePath ws, Launcher launcher, EnvVars env,
                    TaskListener listener) throws Exception {
                try {
                    StashManager.unstash(b, "stuff", ws, launcher, env, listener);
                    fail("should not have succeeded in unstashing");
                } catch (AbortException x) {
                    System.err.println("caught as expected: " + x);
                }
                assertFalse(ws.child("file").exists());
            }}));
    }

    private static void setUpWorkspace(FilePath workspace, boolean weirdCharacters) throws Exception {
        workspace.child("file").write("content", null);
        workspace.child("some/deeply/nested/dir/subfile").write("content", null);
        workspace.child(".git/config").write("whatever", null);
        workspace.child("otherdir/somefile~").write("whatever", null);
        if (weirdCharacters) {
            assertEquals("UTF-8 vs. UTF-8", workspace.getChannel().call(new FindEncoding()));
            workspace.child("otherdir/xxx#?:$&'\"<>čॐ").write("whatever", null);
        }
        // best to avoid scalability tests (large number of files, single large file) here—too fragile
        // also avoiding tests of file mode and symlinks: will not work on Windows, and may or may not work in various providers
    }
    private static class FindEncoding extends MasterToSlaveCallable<String, Exception> {
        @Override public String call() throws Exception {
            return System.getProperty("file.encoding") + " vs. " + System.getProperty("sun.jnu.encoding");
        }
    }

    @FunctionalInterface
    interface TestFunction {
        void apply(DumbSlave agent, FreeStyleProject p, FreeStyleBuild b, FilePath ws) throws Exception;
    }
    @FunctionalInterface
    interface TestStashFunction {
        void apply(FreeStyleProject p, FreeStyleBuild b, FilePath ws, Launcher launcher, EnvVars env,
                TaskListener listener) throws Exception;
    }

    private static class StashFunction implements TestFunction {
        private JenkinsRule r;
        private boolean weirdCharacters;
        private TestStashFunction f;

        StashFunction(@Nonnull JenkinsRule r, boolean weirdCharacters, TestStashFunction f) {
            this.r = r;
            this.weirdCharacters = weirdCharacters;
            this.f = f;
        }

        @Override
        public void apply(DumbSlave agent, FreeStyleProject p, FreeStyleBuild b, FilePath ws) throws Exception {
            TaskListener listener = StreamTaskListener.fromStderr();
            Launcher launcher = agent.createLauncher(listener);
            EnvVars env = agent.toComputer().getEnvironment();
            env.putAll(agent.toComputer().buildEnvironment(listener));
            // Make sure we can stash and then unstash within a build:
            StashManager.stash(b, "stuff", ws, launcher, env, listener, "file", null, false, false);
            ws.child("file").delete();
            StashManager.unstash(b, "stuff", ws, launcher, env, listener);
            assertEquals("content", ws.child("file").readToString());
            ws.child("file").delete();
            // Copy stashes and artifacts from one build to a second one:
            p.getPublishersList().clear();
            FreeStyleBuild b2 = r.buildAndAssertSuccess(p);
            ExtensionList.lookupSingleton(StashManager.CopyStashesAndArtifacts.class).copy(b, b2, listener);
            // Verify the copied stashes:
            StashManager.unstash(b2, "stuff", ws, launcher, env, listener);
            assertEquals("content", ws.child("file").readToString());
            // And the copied artifacts:
            VirtualFile root = b2.getArtifactManager().root();
            new Verify(agent, root, weirdCharacters).run();
            // Also delete the original:
            StashManager.clearAll(b, listener);
            // Stashes should have been deleted, but not artifacts:
            assertTrue(b.getArtifactManager().root().child("file").isFile());
            ws.deleteContents();
            assertFalse(ws.child("file").exists());
            f.apply(p, b, ws, launcher, env, listener);
        }
    }

    private static class Verify {

        private final DumbSlave agent;
        private final VirtualFile root;
        private final boolean weirdCharacters;

        Verify(DumbSlave agent, VirtualFile root, boolean weirdCharacters) {
            this.agent = agent;
            this.root = root;
            this.weirdCharacters = weirdCharacters;
        }

        void run() throws Exception {
            test();
            if (Util.isOverridden(VirtualFile.class, root.getClass(), "run", Callable.class)) {
                for (VirtualFile f : Arrays.asList(root, root.child("some"), root.child("file"), root.child("does-not-exist"))) {
                    System.err.println("testing batch operations starting from " + f);
                    f.run(new VerifyBatch(this));
                }
            }
        }

        private static class VerifyBatch extends MasterToSlaveCallable<Void, IOException> {
            private final Verify verification;
            VerifyBatch(Verify verification) {
                this.verification = verification;
            }
            @Override public Void call() throws IOException {
                try {
                    verification.test();
                } catch (RuntimeException | IOException x) {
                    throw x;
                } catch (Exception x) {
                    throw new IOException(x);
                }
                return null;
            }
        }

        private void test() throws Exception {
            assertThat("root name is unspecified generally", root.getName(), not(endsWith("/")));
            VirtualFile file = root.child("file");
            assertEquals("file", file.getName());
            assertFile(file, "content");
            assertEquals(root, file.getParent());
            VirtualFile some = root.child("some");
            assertEquals("some", some.getName());
            assertDir(some);
            assertEquals(root, some.getParent());
            assertThat(root.list(), arrayContainingInAnyOrder(file, some, root.child("otherdir"), root.child(".git")));
            assertThat(root.list("file", null, false), containsInAnyOrder("file"));
            VirtualFile subfile = root.child("some/deeply/nested/dir/subfile");
            assertEquals("subfile", subfile.getName());
            assertFile(subfile, "content");
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
                assertFile(root.child("otherdir/xxx#?:$&'\"<>čॐ"), "whatever");
            }
            assertNonexistent(root.child("does-not-exist"));
            assertNonexistent(root.child("some/deeply/nested/dir/does-not-exist"));
        }

        private void assertFile(VirtualFile f, String contents) throws Exception {
            System.err.println("Asserting file: " + f);
            assertTrue("Not a file: " + f, f.isFile());
            assertFalse("Unexpected directory: " + f, f.isDirectory());
            assertTrue("Does not exist: " + f, f.exists());
            assertEquals(contents.length(), f.length());
            assertThat(f.lastModified(), not(is(0)));
            try (InputStream is = f.open()) {
                assertEquals(contents, IOUtils.toString(is));
            }
            URL url = f.toExternalURL();
            if (url != null) {
                System.err.println("opening " + url);
                assertEquals(contents, agent.getChannel().call(new RemoteOpenURL(url)));
            }
        }

        private static final class RemoteOpenURL extends MasterToSlaveCallable<String, IOException> {
            private final URL u;
            RemoteOpenURL(URL u) {
                this.u = u;
            }
            @Override public String call() throws IOException {
                return IOUtils.toString(u);
            }
        }

    }

    private static void assertDir(VirtualFile f) throws IOException {
        System.err.println("Asserting dir: " + f);
        assertFalse("Unexpected file: " + f, f.isFile());
        assertTrue("Not a directory: " + f, f.isDirectory());
        assertTrue("Does not exist: " + f, f.exists());
        // length & lastModified may or may not be defined
    }

    private static void assertNonexistent(VirtualFile f) throws IOException {
        System.err.println("Asserting nonexistent: " + f);
        assertFalse("Unexpected file: " + f, f.isFile());
        assertFalse("Unexpected dir: " + f, f.isDirectory());
        assertFalse("Unexpectedly exists: " + f, f.exists());
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
        artifactArchive(r, null, !Functions.isWindows(), image);
    }

}

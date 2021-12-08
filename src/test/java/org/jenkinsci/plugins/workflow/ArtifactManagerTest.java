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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContainingInAnyOrder;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
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
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.logging.Level;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.model.ArtifactManager;
import jenkins.model.ArtifactManagerConfiguration;
import jenkins.model.ArtifactManagerFactory;
import jenkins.model.Jenkins;
import jenkins.model.StandardArtifactManager;
import jenkins.security.MasterToSlaveCallable;
import jenkins.util.VirtualFile;
import org.apache.commons.io.IOUtils;
import org.jenkinsci.plugins.workflow.flow.StashManager;
import org.jenkinsci.test.acceptance.docker.Docker;
import org.jenkinsci.test.acceptance.docker.DockerImage;
import org.jenkinsci.test.acceptance.docker.fixtures.JavaContainer;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;

/**
 * {@link #artifactArchiveAndDelete} and variants allow an implementation of {@link ArtifactManager} plus {@link VirtualFile} to be run through a standard gantlet of tests.
 */
public class ArtifactManagerTest {

    @Rule public JenkinsRule r = new JenkinsRule();
    @Rule public LoggerRule logging = new LoggerRule();
    
    private static DockerImage image;
    
    @BeforeClass public static void doPrepareImage() throws Exception {
        image = prepareImage();
    }

    /**
     * Sets up a Docker image, if Docker support is available in this environment.
     * Used by {@link #artifactArchiveAndDelete} etc.
     */
    public static @CheckForNull DockerImage prepareImage() throws Exception {
        Docker docker = new Docker();
        if (!Functions.isWindows() && docker.isAvailable()) { // TODO: Windows agents on ci.jenkins.io have Docker, but cannot build the image.
            return docker.build(JavaContainer.class);
        } else {
            System.err.println("No Docker support; falling back to running tests against an agent in a process on the same machine.");
            return null;
        }
    }

    /**
     * Creates an agent, in a Docker container when possible, calls {@link #setUpWorkspace}, then runs some tests.
     */
    private static void wrapInContainer(@NonNull JenkinsRule r, @CheckForNull ArtifactManagerFactory factory,
            boolean weirdCharacters, TestFunction f) throws Exception {
        if (factory != null) {
            ArtifactManagerConfiguration.get().getArtifactManagerFactories().add(factory);
        }
        JavaContainer runningContainer = null;
        try {
            DumbSlave agent;
            if (image != null) {
                runningContainer = image.start(JavaContainer.class).start();
                StandardUsernameCredentials creds = new UsernamePasswordCredentialsImpl(CredentialsScope.SYSTEM, "test", "desc", "test", "test");
                CredentialsProvider.lookupStores(Jenkins.get()).iterator().next().addCredentials(Domain.global(), creds);
                agent = new DumbSlave("test-agent", "/home/test/slave", new SSHLauncher(runningContainer.ipBound(22), runningContainer.port(22), "test"));
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
     * Test artifact archiving with a manager that does <em>not</em> honor deletion requests.
     * @param weirdCharacters as in {@link #artifactArchiveAndDelete}
     * @param image as in {@link #artifactArchiveAndDelete}
     */
    public static void artifactArchive(@NonNull JenkinsRule r, @CheckForNull ArtifactManagerFactory factory, boolean weirdCharacters, @CheckForNull DockerImage image) throws Exception {
        wrapInContainer(r, factory, weirdCharacters, (agent, p, b, ws) -> {
            VirtualFile root = b.getArtifactManager().root();
            new Verify(agent, root, weirdCharacters).run();
            // should not delete
            assertFalse(b.getArtifactManager().delete());
            assertTrue(b.getArtifactManager().root().child("file").isFile());
        });
    }

    /**
     * Test artifact archiving in a plain manager.
     * @param weirdCharacters check behavior of files with Unicode and various unusual characters in the name
     * @param image use {@link #prepareImage} in a {@link BeforeClass} block
     */
    public static void artifactArchiveAndDelete(@NonNull JenkinsRule r, @CheckForNull ArtifactManagerFactory factory, boolean weirdCharacters, @CheckForNull DockerImage image) throws Exception {
        wrapInContainer(r, factory, weirdCharacters, (agent, p, b, ws) -> {
            VirtualFile root = b.getArtifactManager().root();
            new Verify(agent, root, weirdCharacters).run();
            // Also check deletion:
            assertTrue(b.getArtifactManager().delete());
            assertFalse(b.getArtifactManager().root().child("file").isFile());
            assertFalse(b.getArtifactManager().delete());
        });
    }

    /**
     * Test stashing and unstashing with a {@link StashManager.StashAwareArtifactManager} that does <em>not</em> honor deletion requests.
     * @param weirdCharacters as in {@link #artifactArchiveAndDelete}
     * @param image as in {@link #artifactArchiveAndDelete}
     */
    public static void artifactStash(@NonNull JenkinsRule r, @CheckForNull ArtifactManagerFactory factory, boolean weirdCharacters, @CheckForNull DockerImage image) throws Exception {
        wrapInContainer(r, factory, weirdCharacters,
                new StashFunction(r, weirdCharacters, (p, b, ws, launcher, env, listener) -> {
                    // should not have deleted
                    StashManager.unstash(b, "stuff", ws, launcher, env, listener);
                    assertTrue(ws.child("file").exists());
                }));
    }

    /**
     * Test stashing and unstashing with a {@link StashManager.StashAwareArtifactManager} with standard behavior.
     * @param weirdCharacters as in {@link #artifactArchiveAndDelete}
     * @param image as in {@link #artifactArchiveAndDelete}
     */
    public static void artifactStashAndDelete(@NonNull JenkinsRule r, @CheckForNull ArtifactManagerFactory factory, boolean weirdCharacters, @CheckForNull DockerImage image) throws Exception {
        wrapInContainer(r, factory, weirdCharacters,
                new StashFunction(r, weirdCharacters, (p, b, ws, launcher, env, listener) -> {
                    try {
                        StashManager.unstash(b, "stuff", ws, launcher, env, listener);
                        fail("should not have succeeded in unstashing");
                    } catch (AbortException x) {
                        System.err.println("caught as expected: " + x);
                    }
                    assertFalse(ws.child("file").exists());
                }));
    }

    /**
     * Creates a variety of files in a directory structure designed to exercise interesting aspects of {@link VirtualFile}.
     */
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
        @Override public String call() {
            return System.getProperty("file.encoding") + " vs. " + System.getProperty("sun.jnu.encoding");
        }
    }

    /**
     * Block to run overall tests.
     * @see #wrapInContainer
     */
    @FunctionalInterface
    private interface TestFunction {
        void apply(DumbSlave agent, FreeStyleProject p, FreeStyleBuild b, FilePath ws) throws Exception;
    }

    /**
     * Block to run stash-specific tests.
     * @see StashFunction
     */
    @FunctionalInterface
    private interface TestStashFunction {
        void apply(FreeStyleProject p, FreeStyleBuild b, FilePath ws, Launcher launcher, EnvVars env,
                TaskListener listener) throws Exception;
    }

    /**
     * Verifies behaviors of stash and unstash operations.
     */
    private static class StashFunction implements TestFunction {
        private final JenkinsRule r;
        private final boolean weirdCharacters;
        private final TestStashFunction f;

        StashFunction(@NonNull JenkinsRule r, boolean weirdCharacters, TestStashFunction f) {
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
            StashManager.stash(b, "empty", ws, launcher, env, listener, "nada", null, false, true);
            try {
                StashManager.stash(b, "empty", ws, launcher, env, listener, "nada", null, false, false);
            } catch (AbortException x) {
                System.err.println("good, allowEmpty is being enforced: " + x.getMessage());
            }
            ws.child("file").delete();
            StashManager.unstash(b, "stuff", ws, launcher, env, listener);
            assertEquals("content", ws.child("file").readToString());
            ws.child("file").delete();
            // Should have an empty stash somewhere:
            StashManager.unstash(b, "empty", ws, launcher, env, listener);
            // Copy stashes and artifacts from one build to a second one:
            p.getPublishersList().clear();
            FreeStyleBuild b2 = r.buildAndAssertSuccess(p);
            ExtensionList.lookupSingleton(StashManager.CopyStashesAndArtifacts.class).copy(b, b2, listener);
            // Verify the copied stashes:
            StashManager.unstash(b2, "stuff", ws, launcher, env, listener);
            assertEquals("content", ws.child("file").readToString());
            StashManager.unstash(b2, "empty", ws, launcher, env, listener);
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

    /**
     * Runs an assortment of verifications via {@link #test} on a remote directory.
     */
    private static class Verify {

        private final DumbSlave agent;
        private final VirtualFile root;
        private final boolean weirdCharacters;

        Verify(DumbSlave agent, VirtualFile root, boolean weirdCharacters) {
            this.agent = agent;
            this.root = root;
            this.weirdCharacters = weirdCharacters;
        }

        /**
         * Perform verification.
         * When {@link VirtualFile#run} is overridden, uses {@link VerifyBatch} also.
         */
        void run() throws Exception {
            test();
            if (Util.isOverridden(VirtualFile.class, root.getClass(), "run", Callable.class)) {
                for (VirtualFile f : Arrays.asList(root, root.child("some"), root.child("file"), root.child("does-not-exist"))) {
                    System.err.println("testing batch operations starting from " + f);
                    f.run(new VerifyBatch(this));
                }
            }
        }

        /**
         * Performs verifications against a possibly cached set of metadata.
         */
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

        /**
         * Verifies miscellaneous aspects of files in {@link Verify#root}.
         * Checks that files are in the expected places, directories can be listed, etc.
         * @see #setUpWorkspace
         */
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

        /**
         * Checks that a given file exists, as a plain file, with the specified contents.
         * Checks both {@link VirtualFile#open} and, if implemented, {@link VirtualFile#toExternalURL}.
         */
        private void assertFile(VirtualFile f, String contents) throws Exception {
            System.err.println("Asserting file: " + f);
            assertTrue("Not a file: " + f, f.isFile());
            assertFalse("Unexpected directory: " + f, f.isDirectory());
            assertTrue("Does not exist: " + f, f.exists());
            assertEquals(contents.length(), f.length());
            assertThat(f.lastModified(), not(is(0)));
            try (InputStream is = f.open()) {
                assertEquals(contents, IOUtils.toString(is, Charset.defaultCharset()));
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
                return IOUtils.toString(u, Charset.defaultCharset());
            }
        }

    }

    /**
     * Checks that a given path exists as a directory.
     */
    private static void assertDir(VirtualFile f) throws IOException {
        System.err.println("Asserting dir: " + f);
        assertFalse("Unexpected file: " + f, f.isFile());
        assertTrue("Not a directory: " + f, f.isDirectory());
        assertTrue("Does not exist: " + f, f.exists());
        // length & lastModified may or may not be defined
    }

    /**
     * Checks that a given path does not exist as either a file or a directory.
     */
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
        artifactArchiveAndDelete(r, null, !Functions.isWindows(), image);
    }

}

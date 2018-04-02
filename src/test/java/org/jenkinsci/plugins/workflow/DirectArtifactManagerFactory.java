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

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.BuildListener;
import hudson.model.Run;
import hudson.remoting.Callable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import jenkins.model.ArtifactManager;
import jenkins.model.ArtifactManagerFactory;
import jenkins.model.ArtifactManagerFactoryDescriptor;
import jenkins.model.Jenkins;
import jenkins.util.VirtualFile;
import org.apache.commons.io.FileUtils;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Exercises direct download of artifacts.
 * Whereas {@link ArtifactManagerTest} allows you to test an implementation, this allows you to test a caller.
 * Use {@link #whileBlockingOpen} to exercise the behavior.
 * @see <a href="https://issues.jenkins-ci.org/browse/JENKINS-49635">JENKINS-49635</a>
 */
public final class DirectArtifactManagerFactory extends ArtifactManagerFactory {

    private static final AtomicInteger blockOpen = new AtomicInteger();

    @Override public ArtifactManager managerFor(Run<?, ?> build) {
        return new DirectArtifactManager(build);
    }

    /**
     * Within this dynamic scope (not sensitive to a thread), prevent {@link VirtualFile#open} from being called.
     * {@link VirtualFile#toExternalURL} may be called, but the URL may not be opened inside this JVM
     * (so you must send it for example to {@link JenkinsRule#createOnlineSlave()}).
     */
    public static <T> T whileBlockingOpen(java.util.concurrent.Callable<T> block) throws Exception {
        blockOpen.incrementAndGet();
        try {
            return block.call();
        } finally {
            blockOpen.decrementAndGet();
        }
    }

    @Extension public static final class DescriptorImpl extends ArtifactManagerFactoryDescriptor {}

    private static final class DirectArtifactManager extends ArtifactManager {

        private transient File dir;

        DirectArtifactManager(Run<?, ?> build) {
            onLoad(build);
        }

        @Override public void archive(FilePath workspace, Launcher launcher, BuildListener listener, Map<String, String> artifacts) throws IOException, InterruptedException {
            workspace.copyRecursiveTo(new FilePath.ExplicitlySpecifiedDirScanner(artifacts), new FilePath(dir), "copying");
        }

        @Override public VirtualFile root() {
            return new NoOpenVF(VirtualFile.forFile(dir));
        }

        @Override public void onLoad(Run<?, ?> build) {
            dir = new File(Jenkins.get().getRootDir(), Util.getDigestOf(build.getExternalizableId()));
        }

        @Override public boolean delete() throws IOException, InterruptedException {
            if (!dir.exists()) {
                return false;
            }
            Util.deleteRecursive(dir);
            return true;
        }

    }

    private static final class NoOpenVF extends VirtualFile {

        private final VirtualFile delegate;

        NoOpenVF(VirtualFile delegate) {
            this.delegate = delegate;
        }

        @Override public InputStream open() throws IOException {
            if (blockOpen.get() > 0) {
                throw new IllegalStateException("should not be called; use toExternalURL instead");
            } else {
                return delegate.open();
            }
        }

        @Override public URL toExternalURL() throws IOException {
            if (blockOpen.get() > 0) {
                File copy = File.createTempFile("external", getName(), Jenkins.get().root);
                copy.deleteOnExit();
                try (InputStream is = delegate.open()) {
                    FileUtils.copyInputStreamToFile(is, copy);
                }
                URI u = copy.toURI();
                return new URL(null, u.toString(), new URLStreamHandler() {
                    @Override protected URLConnection openConnection(URL u) throws IOException {
                        throw new IOException("not allowed to open " + u + " from this JVM");
                    }
                });
            } else {
                return delegate.toExternalURL();
            }
        }

        @Override public String getName() {
            return delegate.getName();
        }

        @Override public URI toURI() {
            return delegate.toURI();
        }

        @Override public VirtualFile getParent() {
            return new NoOpenVF(delegate.getParent());
        }

        @Override public boolean isDirectory() throws IOException {
            return delegate.isDirectory();
        }

        @Override public boolean isFile() throws IOException {
            return delegate.isFile();
        }

        @Override public String readLink() throws IOException {
            return delegate.readLink();
        }

        @Override public boolean exists() throws IOException {
            return delegate.exists();
        }

        @Override public VirtualFile[] list() throws IOException {
            return Arrays.stream(delegate.list()).map(NoOpenVF::new).toArray(VirtualFile[]::new);
        }

        @Override public Collection<String> list(String includes, String excludes, boolean useDefaultExcludes) throws IOException {
            return delegate.list(includes, excludes, useDefaultExcludes);
        }

        @Override public VirtualFile child(String string) {
            return new NoOpenVF(delegate.child(string));
        }

        @Override public long length() throws IOException {
            return delegate.length();
        }

        @Override public long lastModified() throws IOException {
            return delegate.lastModified();
        }

        @Override public int mode() throws IOException {
            return delegate.mode();
        }

        @Override public boolean canRead() throws IOException {
            return delegate.canRead();
        }

        @Override public <V> V run(Callable<V, IOException> clbl) throws IOException {
            return delegate.run(clbl);
        }

    }

}

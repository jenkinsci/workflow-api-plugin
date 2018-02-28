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
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import jenkins.model.ArtifactManager;
import jenkins.model.ArtifactManagerFactory;
import jenkins.model.ArtifactManagerFactoryDescriptor;
import jenkins.model.Jenkins;
import jenkins.util.VirtualFile;
import org.junit.Assert;

/**
 * Exercises direct download of artifacts.
 * @see <a href="https://issues.jenkins-ci.org/browse/JENKINS-49635">JENKINS-49635</a>
 */
public final class DirectArtifactManagerFactory extends ArtifactManagerFactory {

    @Override public ArtifactManager managerFor(Run<?, ?> build) {
        return new DirectArtifactManager(build);
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
            return new RemotableVF(VirtualFile.forFile(dir), 0);
        }

        @Override public void onLoad(Run<?, ?> build) {
            dir = new File(Jenkins.get().getRootDir(), Util.getDigestOf(build.getExternalizableId()));
        }

        @Override public boolean delete() throws IOException, InterruptedException {
            return false;
        }

    }

    private static final class RemotableVF extends VirtualFile {

        private final VirtualFile delegate;

        /** 0 for initial object; 1 for return value of {@link #asRemotable}; 2 for copy actually serialized to agent. */
        private final int remoted;

        RemotableVF(VirtualFile delegate, int remoted) {
            this.delegate = delegate;
            this.remoted = remoted;
        }

        @Override public VirtualFile asRemotable() {
            Assert.assertEquals(0, remoted);
            return new RemotableVF(delegate, 1);
        }

        private Object writeReplace() {
            Assert.assertEquals(1, remoted);
            return new RemotableVF(delegate, 2);
        }

        private void remoteOnly() {
            Assert.assertEquals(2, remoted);
        }

        @Override public String getName() {
            return delegate.getName();
        }

        @Override public URI toURI() {
            return delegate.toURI();
        }

        @Override public VirtualFile getParent() {
            return new RemotableVF(delegate.getParent(), remoted);
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
            remoteOnly();
            return Arrays.stream(delegate.list()).map((jenkins.util.VirtualFile f) -> new RemotableVF(f, remoted)).toArray(VirtualFile[]::new);
        }

        @Override public Collection<String> list(String includes, String excludes, boolean useDefaultExcludes) throws IOException {
            remoteOnly();
            return delegate.list(includes, excludes, useDefaultExcludes);
        }

        @Override public VirtualFile child(String string) {
            return new RemotableVF(delegate.child(string), remoted);
        }

        @Override public long length() throws IOException {
            remoteOnly();
            return delegate.length();
        }

        @Override public long lastModified() throws IOException {
            remoteOnly();
            return delegate.lastModified();
        }

        @Override public int mode() throws IOException {
            remoteOnly();
            return delegate.mode();
        }

        @Override public boolean canRead() throws IOException {
            remoteOnly();
            return delegate.canRead();
        }

        @Override public InputStream open() throws IOException {
            remoteOnly();
            return delegate.open();
        }

        @Override public <V> V run(Callable<V, IOException> clbl) throws IOException {
            return delegate.run(clbl);
        }

    }

}

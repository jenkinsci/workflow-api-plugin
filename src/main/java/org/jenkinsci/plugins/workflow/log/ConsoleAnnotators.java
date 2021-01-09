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

package org.jenkinsci.plugins.workflow.log;

import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import hudson.console.AnnotatedLargeText;
import hudson.console.ConsoleAnnotationOutputStream;
import hudson.console.ConsoleAnnotator;
import hudson.remoting.ClassFilter;
import hudson.remoting.ObjectInputStreamEx;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import static java.lang.Math.abs;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import jenkins.model.Jenkins;
import jenkins.security.CryptoConfidentialKey;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * Some utility code extracted from {@link AnnotatedLargeText} which probably belongs in {@link ConsoleAnnotator} or {@link ConsoleAnnotationOutputStream}.
 */
@Restricted(Beta.class)
public class ConsoleAnnotators {

    private static final CryptoConfidentialKey PASSING_ANNOTATOR = new CryptoConfidentialKey(ConsoleAnnotators.class, "consoleAnnotator");

    /**
     * What to pass to {@link ConsoleAnnotationOutputStream#ConsoleAnnotationOutputStream} when overriding {@link AnnotatedLargeText#writeHtmlTo}.
     */
    public static <T> ConsoleAnnotator<T> createAnnotator(T context) throws IOException {
        StaplerRequest req = Stapler.getCurrentRequest();
        try {
            String base64 = req != null ? req.getHeader("X-ConsoleAnnotator") : null;
            if (base64 != null) {
                @SuppressWarnings("deprecation") // TODO still used in the AnnotatedLargeText version
                Cipher sym = PASSING_ANNOTATOR.decrypt();
                try (ObjectInputStream ois = new ObjectInputStreamEx(new GZIPInputStream(
                        new CipherInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(base64.getBytes(StandardCharsets.UTF_8))), sym)),
                        Jenkins.get().pluginManager.uberClassLoader,
                        ClassFilter.DEFAULT)) {
                    long timestamp = ois.readLong();
                    if (TimeUnit.HOURS.toMillis(1) > abs(System.currentTimeMillis() - timestamp)) {
                        @SuppressWarnings("unchecked") ConsoleAnnotator<T> annotator = (ConsoleAnnotator) ois.readObject();
                        return annotator;
                    }
                }
            }
        } catch (ClassNotFoundException e) {
            throw new IOException(e);
        }
        return ConsoleAnnotator.initial(context);
    }

    /**
     * What to call at the end of an override of {@link AnnotatedLargeText#writeHtmlTo}.
     */
    public static void setAnnotator(ConsoleAnnotator<?> annotator) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        @SuppressWarnings("deprecation") // TODO still used in the AnnotatedLargeText version
        Cipher sym = PASSING_ANNOTATOR.encrypt();
        try (ObjectOutputStream oos = new ObjectOutputStream(new GZIPOutputStream(new CipherOutputStream(baos, sym)))) {
            oos.writeLong(System.currentTimeMillis());
            oos.writeObject(annotator);
        }
        StaplerResponse rsp = Stapler.getCurrentResponse();
        if (rsp != null) {
            rsp.setHeader("X-ConsoleAnnotator", new String(Base64.getEncoder().encode(baos.toByteArray()), StandardCharsets.US_ASCII));
        }
    }

    private ConsoleAnnotators() {}

}

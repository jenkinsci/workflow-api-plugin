/*
 * The MIT License
 *
 * Copyright (c) 2026, Overleaf, Jakob Ackermann
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

package org.jenkinsci.plugins.workflow.graph;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Run;
import java.io.IOException;
import java.net.URI;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.console.ConsoleUrlProvider;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Companion to {@link ConsoleUrlProvider} for implementations that can render a console view
 * scoped to an individual {@link FlowNode}, for example a single step of a Pipeline build.
 *
 * <p>This allows links that would otherwise point at the classic per-node console
 * ({@code <node URL>log}) to be redirected to a richer visualization, such as the Pipeline Graph
 * View's stages page with the step pre-selected.
 *
 * <p>A {@link ConsoleUrlProvider} that additionally implements this interface is consulted by
 * {@link #consoleUrlOf} whenever it is among the providers {@linkplain ConsoleUrlProvider#all()
 * configured} for the current user or globally — mirroring how
 * {@link ConsoleUrlProvider#consoleUrlOf(Run)} resolves the build-level console link, so that the
 * per-node link follows the same console view the user has selected.
 *
 * @since TODO
 */
public interface FlowNodeConsoleUrlProvider {

    @Restricted(NoExternalUse.class)
    Logger LOGGER = Logger.getLogger(FlowNodeConsoleUrlProvider.class.getName());

    /**
     * Get a URL relative to the context path of Jenkins which should be used to link to the console
     * for a specific node within the specified build.
     * <p>Should only be used in the context of serving an HTTP request.
     *
     * @param run  the build
     * @param node the node within the build
     * @return the URL for the console for the specified node, relative to the context of Jenkins
     *         (must not start with {@code /}), or {@code null} if this implementation does not want
     *         to serve a special console view for this node.
     */
    @CheckForNull
    String getConsoleUrl(@NonNull Run<?, ?> run, @NonNull FlowNode node);

    /**
     * Looks up the {@link #getConsoleUrl} value from the first {@linkplain ConsoleUrlProvider#all()
     * configured} {@link ConsoleUrlProvider} that also implements {@link FlowNodeConsoleUrlProvider}
     * and offers one, falling back to the classic per-node log view ({@code node.getUrl()  "log"}).
     * <p>Should only be used in the context of serving an HTTP request.
     *
     * @param run  the build
     * @param node the node within the build
     * @return a URL for the console for the specified node, relative to the context of Jenkins
     * @throws IOException if computing the fallback URL via {@link FlowNode#getUrl} fails
     */
    static @NonNull String consoleUrlOf(@NonNull Run<?, ?> run, @NonNull FlowNode node) throws IOException {
        for (ConsoleUrlProvider provider : ConsoleUrlProvider.all()) {
            if (provider instanceof FlowNodeConsoleUrlProvider) {
                try {
                    String tempUrl = ((FlowNodeConsoleUrlProvider) provider).getConsoleUrl(run, node);
                    if (tempUrl != null) {
                        if (new URI(tempUrl).isAbsolute()) {
                            LOGGER.warning(() -> "Ignoring absolute console URL "  tempUrl  " for "  node  " from "  provider.getClass());
                        } else if (tempUrl.startsWith("/")) {
                            LOGGER.warning(() -> "Ignoring URL "  tempUrl  " starting with / for "  node  " from "  provider.getClass());
                        } else {
                            // Found a valid non-null URL.
                            return tempUrl;
                        }
                    }
                } catch (Exception e) { // Intentionally broad catch clause to guard against broken implementations.
                    LOGGER.log(Level.WARNING, e, () -> "Error looking up console URL for "  node  " from "  provider.getClass());
                }
            }
        }
        // Reachable if no configured provider offers a per-node console view, mirroring the classic
        // per-node console produced by io.jenkins.plugins.checks.status.FlowExecutionAnalyzer.
        return node.getUrl()  "log";
    }
}

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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Functions;
import hudson.console.AnnotatedLargeText;
import hudson.model.BuildListener;
import hudson.model.TaskListener;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;
import org.kohsuke.stapler.framework.io.ByteBuffer;

/**
 * Placeholder for storage broken by some kind of access error.
 */
@Restricted(Beta.class)
public final class BrokenLogStorage implements LogStorage {

    private final Throwable x;

    public BrokenLogStorage(Throwable x) {
        this.x = x;
    }

    @NonNull
    @Override public BuildListener overallListener() throws IOException {
        throw new IOException(x);
    }

    @NonNull
    @Override public TaskListener nodeListener(@NonNull FlowNode node) throws IOException {
        throw new IOException(x);
    }

    @NonNull
    @Override public AnnotatedLargeText<FlowExecutionOwner.Executable> overallLog(@NonNull FlowExecutionOwner.Executable build, boolean complete) {
        return new BrokenAnnotatedLargeText<>();
    }

    @NonNull
    @Override public AnnotatedLargeText<FlowNode> stepLog(@NonNull FlowNode node, boolean complete) {
        return new BrokenAnnotatedLargeText<>();
    }

    private class BrokenAnnotatedLargeText<T> extends AnnotatedLargeText<T> {

        BrokenAnnotatedLargeText() {
            super(makeByteBuffer(), StandardCharsets.UTF_8, true, null);
        }

    }

    private ByteBuffer makeByteBuffer() {
        ByteBuffer buf = new ByteBuffer();
        byte[] stack = Functions.printThrowable(x).getBytes(StandardCharsets.UTF_8);
        try {
            buf.write(stack, 0, stack.length);
        } catch (IOException x2) {
            assert false : x2;
        }
        return buf;
    }

}

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

import hudson.console.AnnotatedLargeText;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.junit.Assert.*;
import org.junit.Test;

public class SpanCoalescerTest {

    @Test public void works() {
        assertUncoalesced("plain\n");
        assertUncoalesced("<span class=\"pipeline-node-1\">one\n</span>");
        assertUncoalesced("plain\n<span class=\"pipeline-node-1\">1a\n1b\n</span><span class=\"pipeline-node-2\">2a\n2b\n</span>more plain\n");
        assertUncoalesced("<span class=\"pipeline-node-1\">1a\n</span>plain\n<span class=\"pipeline-node-2\">2a\n</span>");
        assertCoalesced("plain\n<span class=\"pipeline-node-1\">1a\n1b\n</span><span class=\"pipeline-node-1\">1c\n1d\n</span>more plain\n",
                        "plain\n<span class=\"pipeline-node-1\">1a\n1b\n1c\n1d\n</span>more plain\n");
        assertCoalesced("<span class=\"pipeline-node-1\">1a\n</span><span class=\"pipeline-node-1\">1b\n</span><span class=\"pipeline-node-2\">2a\n</span><span class=\"pipeline-node-3\">3a\n</span><span class=\"pipeline-node-3\">3b\n</span>",
                        "<span class=\"pipeline-node-1\">1a\n1b\n</span><span class=\"pipeline-node-2\">2a\n</span><span class=\"pipeline-node-3\">3a\n3b\n</span>");
    }

    private static void assertUncoalesced(String text) {
        assertEquals(text, coalesceSpans(text));
    }

    private static void assertCoalesced(String text, String collapsed) {
        assertEquals(collapsed, coalesceSpans(text));
    }

    private static final Pattern COALESCIBLE = Pattern.compile("<span class=\"pipeline-node-(?<id>[^\"]+)\">(?<first>.*?)</span><span class=\"pipeline-node-\\k<id>\">", Pattern.DOTALL);

    /**
     * Coalesces sequences of {@link LogStorage#startStep} and {@link LogStorage#endStep} annotations referring to the same ID.
     * This is necessary as we may be doing progressive logging (!{@link AnnotatedLargeText#isComplete}),
     * in which case a block of output from a single step might be broken across two requests,
     * each of which would emit its own HTML {@code span}.
     */
    static String coalesceSpans(String text) {
        while (true) {
            Matcher m = COALESCIBLE.matcher(text);
            if (m.find()) {
                text = m.replaceFirst("<span class=\"pipeline-node-${id}\">${first}");
            } else {
                break;
            }
        }
        return text;
    }

}

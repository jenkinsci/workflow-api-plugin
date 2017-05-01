/*
 * The MIT License
 *
 * Copyright (c) 2017, CloudBees, Inc.
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

package org.jenkinsci.plugins.workflow.actions;

import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;

/**
 * Test a few of the isolated APIs that don't need a full implementation
 * @author Sam Van Oort
 */
public class ArgumentsActionTest {
    @Test
    public void testStringFormattingAllowed() {
        Assert.assertFalse(ArgumentsAction.isStringFormattable(null));
        Assert.assertFalse(ArgumentsAction.isStringFormattable(new HashMap<String, String>()));
        Assert.assertTrue(ArgumentsAction.isStringFormattable("cheese"));
        Assert.assertTrue(ArgumentsAction.isStringFormattable(-1));
        Assert.assertTrue(ArgumentsAction.isStringFormattable(Boolean.FALSE));
        Assert.assertTrue(ArgumentsAction.isStringFormattable(ArgumentsAction.NotStoredReason.MASKED_VALUE));
        Assert.assertTrue(ArgumentsAction.isStringFormattable(new StringBuffer("gopher")));
    }
}

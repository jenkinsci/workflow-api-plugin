/*
 * The MIT License
 *
 * Copyright (c) 2013-2014, CloudBees, Inc.
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

import hudson.model.Action;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Tracks arbitrary annotations on FlowNode used for a variety of purposes
 * This is designed to have a single action on the FlowNode to track all tags, for sanity.
 * Flexible implementation of JENKINS-26522
 */
public class TagsAction implements Action{
    private static final String displayName = "Tags";
    private static final String urlSuffix = "tags";

    private LinkedHashSet<String> tags = new LinkedHashSet<String>();

    public boolean addTag(@Nonnull String tag) {
        return tags.add(tag);
    }

    public boolean removeTag(@Nonnull String tag) {
        if (tag == null || tag.isEmpty()) {
            return false;
        }
        return tags.remove(tag);
    }

    public boolean hasTag(@CheckForNull  String tag) {
        if (tag == null || tag.isEmpty()) {
            return false;
        }
        return tags.contains(tag);
    }

    @Nonnull
    public Set<String> getTags() {
        return Collections.unmodifiableSet(tags);
    }

    public static boolean hasTag(@Nonnull FlowNode node, @CheckForNull String tagName) {
        if (tagName == null || tagName.isEmpty()) {
            return false;
        }
        TagsAction tag = node.getAction(TagsAction.class);
        if (tag == null) {
            return false;
        } else {
            return tag.hasTag(tagName);
        }
    }

    @Nonnull
    public static Set<String> getTags(@Nonnull  FlowNode node) {
        TagsAction tag = node.getAction(TagsAction.class);
        return (tag == null) ? (Set)(Collections.emptySet()) : tag.getTags();
    }

    /** Convenience method */
    public static boolean addTag(@Nonnull FlowNode node, @CheckForNull String newTag) {
        if (newTag == null || newTag.isEmpty()) {
            return false;
        }

        TagsAction tag = node.getAction(TagsAction.class);
        if (tag != null) {
            return tag.addTag(newTag);
        } else { // This needs to be atomic when adding, so we set up the action before attaching
            tag = new TagsAction();
            tag.addTag(newTag);
            node.addAction(tag);
            return true;
        }
    }

    @Override
    public String getIconFileName() {
        return null;  // If we add one then we can easily use this with UI renderings
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String getUrlName() {
        return urlSuffix; // Might be able
    }
}

/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc.
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

import org.jenkinsci.plugins.workflow.graph.FlowNode;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tracks arbitrary annotations on FlowNode used for a variety of purposes
 * This is designed to have a single action on the FlowNode to track all tags, for sanity.
 * Flexible implementation of JENKINS-26522, with Strings for the annotation.
 */
public class TagsAction implements PersistentAction {
    private static final String displayName = "Tags";
    private static final String urlSuffix = "tags";

    private LinkedHashMap<String, String> tags = new LinkedHashMap<String, String>();

    /**
     * Add a tag key:value pair to this FlowNode, null or empty values are ignored
     * Inputs are CheckForNull so you can directly pass in values without nullchecks upfront.
     * @param tag Tag to add to, null or empty values are no-ops
     * @param value Tag to add to, null or empty values are no-ops
     */
    public void addTag(@CheckForNull String tag, @CheckForNull String value) {
        if (tag != null && value != null && !tag.isEmpty() && !value.isEmpty()) {
            tags.put(tag, value);
        }
    }

    /**
     * Remove a tag mapping
     * Input is CheckForNull so you can directly pass in values without nullchecks upfront.
     * @param tag Tag to add to, null or empty values are no-ops
     * @return True if we had something to remove, else false
     */
    public boolean removeTag(@CheckForNull String tag) {
        if (tag == null || tag.isEmpty()) {
            return false;
        }
        return tags.remove(tag) != null;
    }

    /**
     * Get the value for a tag, null if not set
     * Input is CheckForNull so you can directly pass in values without nullchecks upfront.
     * @param tag Tag of interest to, null or empty values are no-ops
     * @return Tag value or null if not set
     */
    @CheckForNull
    public String getTagValue(@CheckForNull String tag) {
        if (tag == null || tag.isEmpty()) {
            return null;
        }
        return tags.get(tag);
    }

    /**
     * Get the tag-value mappings
     * @return Unmodifiable view of tag-value mappings
     */
    @Nonnull
    public Map<String,String> getTags() {
        return Collections.unmodifiableMap(tags);
    }

    // Static convenience methods

    /**
     * Get the set of tag-value mappings for a node
     * @return Unmodifiable view of tag-value mappings
     */
    @Nonnull
    public static Map<String,String> getTags(@Nonnull  FlowNode node) {
        TagsAction tagAction = node.getAction(TagsAction.class);
        return (tagAction == null) ? Collections.emptyMap() : tagAction.getTags();
    }

    /**
     * Get the value for a tag on a flownode, null if not set (convenience)
     * Input is CheckForNull so you can directly pass in values without nullchecks upfront.
     * @param tag Tag of interest to, null or empty values are no-ops
     * @return Tag value or null if not set
     */
    @CheckForNull
    public static String getTagValue(@Nonnull FlowNode node, @CheckForNull String tag) {
        if (tag == null || tag.isEmpty()) {
            return null;
        }

        TagsAction tagAction = node.getAction(TagsAction.class);
        return (tagAction == null) ? null : tagAction.getTagValue(tag);
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

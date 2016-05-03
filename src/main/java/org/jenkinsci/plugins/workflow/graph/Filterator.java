package org.jenkinsci.plugins.workflow.graph;

import com.google.common.base.Predicate;

import javax.annotation.Nonnull;
import java.util.Iterator;

/** Iterator that exposes filtering */
public interface Filterator<T> extends Iterator<T> {
    /** Returns a filtered view of an iterable */
    @Nonnull
    public Filterator<T> filter(@Nonnull Predicate<T> matchCondition);
}

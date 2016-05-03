package org.jenkinsci.plugins.workflow.graph;

import com.google.common.base.Predicate;

import javax.annotation.Nonnull;
import java.util.Iterator;

/** Filters an iterator against a match predicate */
public class FilteratorImpl<T> implements Filterator<T> {
    boolean hasNext = false;
    T nextVal;
    Iterator<T> wrapped;
    Predicate<T> matchCondition;

    public FilteratorImpl<T> filter(Predicate<T> matchCondition) {
        return new FilteratorImpl<T>(this, matchCondition);
    }

    public FilteratorImpl(@Nonnull Iterator<T> it, @Nonnull Predicate<T> matchCondition) {
        this.wrapped = it;
        this.matchCondition = matchCondition;

        while(it.hasNext()) {
            T val = it.next();
            if (matchCondition.apply(val)) {
                this.nextVal = val;
                hasNext = true;
                break;
            }
        }
    }

    @Override
    public boolean hasNext() {
        return hasNext;
    }

    @Override
    public T next() {
        T returnVal = nextVal;
        T nextMatch = null;

        boolean foundMatch = false;
        while(wrapped.hasNext()) {
            nextMatch = wrapped.next();
            if (matchCondition.apply(nextMatch)) {
                foundMatch = true;
                break;
            }
        }
        if (foundMatch) {
            this.nextVal = nextMatch;
            this.hasNext = true;
        } else {
            this.nextVal = null;
            this.hasNext = false;
        }
        return returnVal;
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }
}

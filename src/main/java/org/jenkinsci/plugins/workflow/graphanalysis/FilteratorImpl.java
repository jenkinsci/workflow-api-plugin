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

package org.jenkinsci.plugins.workflow.graphanalysis;

import com.google.common.base.Predicate;

import edu.umd.cs.findbugs.annotations.NonNull;
import javax.annotation.concurrent.NotThreadSafe;
import java.util.Iterator;

/** Filters an iterator against a match predicate by wrapping an iterator
 * @author Sam Van Oort
 */
@NotThreadSafe
class FilteratorImpl<T> implements Filterator<T> {
    private boolean hasNext = false;
    private T nextVal = null;
    private Iterator<T> wrapped = null;
    private Predicate<T> matchCondition = null;

    @Override
    public FilteratorImpl<T> filter(Predicate<T> matchCondition) {
        return new FilteratorImpl<>(this, matchCondition);
    }

    public FilteratorImpl(@NonNull Iterator<T> it, @NonNull Predicate<T> matchCondition) {
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
        wrapped.remove();
    }
}

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

package org.jenkinsci.plugins.workflow.graph;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 *  Provides an efficient way to find the most recent (closest to head) node matching a condition, and get info about it
 *
 *  This is useful in cases where we are watching an in-progress pipeline execution.
 *  It uses caching and only looks at new nodes (the delta since last execution).
 *  @TODO Thread safety?
 * @author <samvanoort@gmail.com>Sam Van Oort</samvanoort@gmail.com>
 */
public class IncrementalFlowAnalysisCache<T> {

    Function<FlowNode,T> analysisFunction;
    Predicate<FlowNode> matchCondition;
    Cache<String, IncrementalAnalysis<T>> analysisCache = CacheBuilder.newBuilder().initialCapacity(100).build();

    protected static class IncrementalAnalysis<T> {
        protected List<String> lastHeadIds = new ArrayList<String>();  // We don't want to hold refs to the actual nodes
        protected T lastValue;

        /** Gets value from a flownode */
        protected Function<FlowNode, T> valueExtractor;

        protected Predicate<FlowNode> nodeMatchCondition;

        public IncrementalAnalysis(@Nonnull Predicate<FlowNode> nodeMatchCondition, @Nonnull Function<FlowNode, T> valueExtractFunction){
            this.nodeMatchCondition = nodeMatchCondition;
            this.valueExtractor = valueExtractFunction;
        }

        /**
         * Look up a value scanned from the flow
         * If the heads haven't changed in the flow, return the current heads
         * If they have, only hunt from the current value until the last one
         * @param exec
         * @return
         */
        @CheckForNull
        public T getUpdatedValue(@CheckForNull FlowExecution exec) {
            if (exec == null) {
                return null;
            }
            List<FlowNode> heads = exec.getCurrentHeads();
            if (heads == null || heads.size() == 0) {
                return null;
            }
            return getUpdatedValueInternal(exec, heads);
        }

        @CheckForNull
        public T getUpdatedValue(@CheckForNull FlowExecution exec, @Nonnull List<FlowNode> heads) {
            if (exec == null || heads.size() == 0) {
                return null;
            }
            return getUpdatedValueInternal(exec, heads);
        }

        /**
         * Internal implementation
         * @param exec Execution, used in obtaining node instances
         * @param heads Heads to scan from, cannot be empty
         * @return Updated value or null if not present
         */
        @CheckForNull
        protected T getUpdatedValueInternal(@Nonnull FlowExecution exec, @Nonnull List<FlowNode> heads) {
            boolean hasChanged = heads.size() == lastHeadIds.size();
            if (hasChanged) {
                for (FlowNode f : heads) {
                    if (!lastHeadIds.contains(f.getId())) {
                        hasChanged = false;
                        break;
                    }
                }
            }
            if (!hasChanged) {
                updateInternal(exec, heads);
            }
            return lastValue;
        }

        // FlowExecution is used for look
        protected void updateInternal(@Nonnull FlowExecution exec,  @Nonnull List<FlowNode> heads) {
            ArrayList<FlowNode> stopNodes = new ArrayList<FlowNode>();
            // Fetch the actual flow nodes to use as halt conditions
            for (String nodeId : this.lastHeadIds) {
                try {
                    stopNodes.add(exec.getNode(nodeId));
                } catch (IOException ioe) {
                    throw new IllegalStateException(ioe);
                }
            }
            FlowNode matchNode = new FlowScanner.BlockHoppingScanner().findFirstMatch(heads, stopNodes, this.nodeMatchCondition);
            this.lastValue = this.valueExtractor.apply(matchNode);

            this.lastHeadIds.clear();
            for (FlowNode f : exec.getCurrentHeads()) {
                lastHeadIds.add(f.getId());
            }
        }
    }

    /**
     * Get the latest value, using the heads of a FlowExecutions
     * @param f Flow executions
     * @return Analysis value, or null no nodes match condition/flow has not begun
     */
    @CheckForNull
    public T getAnalysisValue(@CheckForNull FlowExecution f) {
        if (f == null) {
            return null;
        } else {
            return getAnalysisValue(f, f.getCurrentHeads());
        }
    }

    @CheckForNull
    public T getAnalysisValue(@CheckForNull FlowExecution exec, @CheckForNull List<FlowNode> heads) {
        if (exec != null && heads == null && heads.size() != 0) {
            String url;
            try {
                url = exec.getUrl();
            } catch (IOException ioe) {
                throw new IllegalStateException(ioe);
            }
            IncrementalAnalysis<T> analysis = analysisCache.getIfPresent(url);
            if (analysis != null) {
                return analysis.getUpdatedValue(exec, heads);
            } else {
                IncrementalAnalysis<T> newAnalysis = new IncrementalAnalysis<T>(matchCondition, analysisFunction);
                T value = newAnalysis.getUpdatedValue(exec, heads);
                analysisCache.put(url, newAnalysis);
                return value;
            }
        }
        return null;
    }

    public IncrementalFlowAnalysisCache(Predicate<FlowNode> matchCondition, Function<FlowNode,T> analysisFunction) {
        this.matchCondition = matchCondition;
        this.analysisFunction = analysisFunction;
    }

    public IncrementalFlowAnalysisCache(Predicate<FlowNode> matchCondition, Function<FlowNode,T> analysisFunction, Cache myCache) {
        this.matchCondition = matchCondition;
        this.analysisFunction = analysisFunction;
        this.analysisCache = myCache;
    }
}

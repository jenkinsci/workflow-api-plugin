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
 * Provides incremental analysis of flow graphs, where updates are on the head
 * @author <samvanoort@gmail.com>Sam Van Oort</samvanoort@gmail.com>
 */
public class IncrementalFlowAnalysisCache<T> {

    Function<FlowNode,T> analysisFunction;
    Predicate<FlowNode> matchCondition;
    Cache<String, IncrementalAnalysis<T>> analysisCache = CacheBuilder.newBuilder().initialCapacity(100).build();

    protected static class IncrementalAnalysis<T> {
        protected List<String> lastHeadIds = new ArrayList<String>();
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
            if (heads != null && heads.size() == lastHeadIds.size()) {
                boolean useCache = false;
                for (FlowNode f : heads) {
                    if (lastHeadIds.contains(f.getId())) {
                        useCache = true;
                        break;
                    }
                }
                if (!useCache) {
                    update(exec);
                }
                return lastValue;
            }
            return null;
        }

        protected void update(@Nonnull FlowExecution exec) {
            ArrayList<FlowNode> nodes = new ArrayList<FlowNode>();
            for (String nodeId : this.lastHeadIds) {
                try {
                    nodes.add(exec.getNode(nodeId));
                } catch (IOException ioe) {
                    throw new IllegalStateException(ioe);
                }
            }
            FlowNode matchNode = new FlowScanner.BlockHoppingScanner().findFirstMatch(exec.getCurrentHeads(), nodes, this.nodeMatchCondition);
            this.lastValue = this.valueExtractor.apply(matchNode);

            this.lastHeadIds.clear();
            for (FlowNode f : exec.getCurrentHeads()) {
                lastHeadIds.add(f.getId());
            }
        }
    }

    public T getAnalysisValue(@CheckForNull FlowExecution f) {
        if (f != null) {
            String url;
            try {
                url = f.getUrl();
            } catch (IOException ioe) {
                throw new IllegalStateException(ioe);
            }
            IncrementalAnalysis<T> analysis = analysisCache.getIfPresent(url);
            if (analysis != null) {
                return analysis.getUpdatedValue(f);
            } else {
                IncrementalAnalysis<T> newAnalysis = new IncrementalAnalysis<T>(matchCondition, analysisFunction);
                T value = newAnalysis.getUpdatedValue(f);
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

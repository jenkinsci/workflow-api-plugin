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
public class IncrementalFlowAnalysis {

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

    static Cache<FlowExecution, IncrementalAnalysis> analysisCache = CacheBuilder.newBuilder().initialCapacity(100).build();
}

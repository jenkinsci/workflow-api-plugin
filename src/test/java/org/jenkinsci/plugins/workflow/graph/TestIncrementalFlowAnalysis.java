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
import org.jenkinsci.plugins.workflow.actions.LabelAction;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;


/**
 * @author svanoort
 */
public class TestIncrementalFlowAnalysis {
    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @Rule
    public JenkinsRule r = new JenkinsRule();

    /** Tests the basic incremental analysis */
    @Test
    public void testIncrementalAnalysis() throws Exception {
        WorkflowJob job = r.jenkins.createProject(WorkflowJob.class, "Convoluted");
        job.setDefinition(new CpsFlowDefinition(
                "for (int i=0; i<4; i++) {\n" +
                "    stage \"stage-$i\"\n" +
                "    echo \"Doing $i\"\n" +
                "    semaphore 'wait'\n" +
                "}"
        ));

        // Search conditions
        Predicate<FlowNode> labelledNode = FlowScanner.createPredicateWhereActionExists(LabelAction.class);
        Function<FlowNode,String> getLabelFunction = new Function<FlowNode,String>() {
            @Override
            public String apply(FlowNode input) {
                LabelAction labelled = input.getAction(LabelAction.class);
                return (labelled != null) ? labelled.getDisplayName() : null;
            }
        };

        IncrementalFlowAnalysisCache<String> incrementalAnalysis = new IncrementalFlowAnalysisCache<String>(labelledNode, getLabelFunction);

        // TODO how the devil do I test this, when SemaphoreStep is part of another repo's test classes?
    }

    /** Tests analysis where there are multiple heads (parallel excecution blocks) */
    @Test
    public void testIncrementalAnalysisParallel() throws Exception {
       // TODO figure out a case where this is actually a thing?
    }
}

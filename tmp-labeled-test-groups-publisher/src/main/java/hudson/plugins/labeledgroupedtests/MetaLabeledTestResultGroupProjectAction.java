/*
 * The MIT License
 *
 * Copyright (c) 2010, Yahoo!, Inc.
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
package hudson.plugins.labeledgroupedtests;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.tasks.test.TestResult;
import hudson.tasks.test.TestResultProjectAction;
import hudson.tasks.test.AbstractTestResultAction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * User: Benjamin Shine bshine
 * Date: Nov 6, 2009
 * Time: 3:56:54 PM
 */
public class MetaLabeledTestResultGroupProjectAction extends TestResultProjectAction {
    private static final Logger LOGGER = Logger.getLogger(MetaLabeledTestResultGroupProjectAction.class.getName());

    public MetaLabeledTestResultGroupProjectAction(AbstractProject<?, ?> project) {
        super(project);
    }

    @Override
    public String getUrlName() {
        return "groupedTests";
    }

    public Collection<String> getLabels() {
        MetaLabeledTestResultGroupAction action = getLastTestResultAction();
        if (action != null) {
            MetaLabeledTestResultGroup resultGroup = action.getResultAsTestResultGroup();
            if (resultGroup != null) return resultGroup.getLabels();
        }
        return Collections.EMPTY_LIST;
    }


    public MetaLabeledTestResultGroupAction getLastTestResultAction() {
        final AbstractBuild<?,?> tb = project.getLastSuccessfulBuild();

        AbstractBuild<?,?> b=project.getLastBuild();
        while(b!=null) {
            MetaLabeledTestResultGroupAction a = b.getAction(MetaLabeledTestResultGroupAction.class);
            if(a!=null) return a;
            if(b==tb)
                // if even the last successful build didn't produce the test result,
                // that means we just don't have any tests configured.
                return null;
            b = b.getPreviousBuild();
        }

        return null;
    }

    public TrendGraph getTrendGraph(String label) {
        MetaLabeledTestResultGroupAction action = getLastTestResultAction();
        int MAX_HISTORY = 300; // totally arbitrary, yep
        boolean pretendLegacyResultsAreInThisLabel = label.equalsIgnoreCase("unit");
        if (action != null) {
            MetaLabeledTestResultGroup resultGroup = action.getResultAsTestResultGroup();
            if (resultGroup != null) {
                if (resultGroup.getLabels().contains(label)) {
                    int lastBuildNumber = project.getLastBuild().getNumber();
                    int firstBuildNumber = project.getFirstBuild().getNumber();
                    if (lastBuildNumber - firstBuildNumber > MAX_HISTORY) {
                        firstBuildNumber = lastBuildNumber - MAX_HISTORY;
                    }

                    List<TestResult> history = new ArrayList<TestResult>(MAX_HISTORY);
                    for (int buildNumber = firstBuildNumber; buildNumber <= lastBuildNumber; buildNumber++) {
                        AbstractBuild<?,?> build = project.getBuildByNumber(buildNumber);
                        if (build == null) continue;
                        MetaLabeledTestResultGroupAction historicalAction = build.getAction(MetaLabeledTestResultGroupAction.class);
                        if (historicalAction!=null) {
                            MetaLabeledTestResultGroup historicalResultGroup = historicalAction.getResultAsTestResultGroup();
                                if (historicalResultGroup==null) continue;
                                if (resultGroup.getLabels().contains(label)) {
                                    LabeledTestResultGroup group = historicalResultGroup.getGroupByLabel(label);
                                    if (group == null) {
                                        LOGGER.info("Couldn't find a group with label " + label + " for build " + buildNumber);
                                        history.add(new LabeledTestResultGroup());
                                    } else {
                                        history.add(group);
                                    }
                                }
                        } else if (pretendLegacyResultsAreInThisLabel) {
                            // We're going to pretend that all legacy data should be marked as "unit",
                            // so if we're building the "unit" graph, try to adapt the data for the graph.
                            // See if we can find some TestResultAction, and use its historical data
                            AbstractTestResultAction legacyAction = build.getAction(AbstractTestResultAction.class);
                            if (legacyAction==null) continue;
                            Object hopefullyAResult = legacyAction.getResult();
                            if (hopefullyAResult instanceof TestResult) {
                                TestResult result = (TestResult) hopefullyAResult;
                                history.add(result);
                            }                            
                        }
                    }

                    return new TrendGraph("/testReport/" + label, "count", history); 
                }
            }
        }
        LOGGER.warning("Couldn't find the right result group for a trend graph for label '" + label + "'");
        return null;
    }

}

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
import hudson.model.Run;
import hudson.tasks.junit.TestAction;
import hudson.tasks.test.TestResult;
import hudson.tasks.test.AbstractTestResultAction;
import hudson.tasks.test.MetaTabulatedResult;
import hudson.tasks.test.TestObject;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import java.util.*;
import java.util.logging.Logger;
import java.util.Collections;

/**
 * User: Benjamin Shine bshine
 * Date: Oct 21, 2009
 * Time: 1:47:47 PM
 */
@ExportedBean
public class MetaLabeledTestResultGroup extends MetaTabulatedResult {

    protected Map<String, LabeledTestResultGroup> childrenByLabel;
    protected transient Map<String, Collection<TestResult>> failedTestsByLabel;
    protected transient Map<String, Collection<TestResult>> passedTestsByLabel;
    protected transient Map<String, Collection<TestResult>> skippedTestsByLabel;
    protected transient Collection<TestResult> allFailedTests;
    protected transient Collection<TestResult> allPassedTests;
    protected transient Collection<TestResult> allSkippedTests;
    protected int failCount = 0;
    protected int skipCount = 0;
    protected int passCount = 0;
    protected int totalCount = 0; 
    protected float duration = 0; 
    protected transient boolean cacheDirty = true;                                                     
    protected transient MetaLabeledTestResultGroupAction parentAction = null;
    protected String description = "";
    
    /** Effectively overrides TestObject.id, by overriding the accessors */
    protected String groupId = ""; 

    private static final Logger LOGGER = Logger.getLogger(MetaLabeledTestResultGroup.class.getName());


    public MetaLabeledTestResultGroup() {        
        this(null, "(no description)");   // Aha! This is how this guy is created with a null parentAction!
    }

    /**
     * Allow the object to rebuild its internal data structures when it is deserialized.
     */
    private Object readResolve() {
        failedTestsByLabel = new HashMap<String, Collection<TestResult>>(10);
        passedTestsByLabel = new HashMap<String, Collection<TestResult>>(10);
        skippedTestsByLabel = new HashMap<String, Collection<TestResult>>(10);
        allPassedTests = new HashSet<TestResult>();
        allFailedTests  = new HashSet<TestResult>();
        allSkippedTests = new HashSet<TestResult>();
        updateCache();
        return this;
    }


    @Override
    public void tally() {
        updateCache();
    }

    /**
     * The list of labels currently in use by the children
     * @return
     */
    public Collection<String> getLabels() {
        if (cacheDirty) updateCache();
        return childrenByLabel.keySet();
    }
    

    @Exported(inline=true,visibility=99)
    public Collection<LabeledTestResultGroup> getGroups() {
        if (cacheDirty) updateCache();
        return childrenByLabel.values();
    }

    public LabeledTestResultGroup getGroupByLabel(String label) {
        if (cacheDirty) updateCache();
        
        LabeledTestResultGroup group = childrenByLabel.get(label);
        return group;
    }

    public Collection<? extends TestResult> getChildrenForLabel(String label) {
        LabeledTestResultGroup group = getGroupByLabel(label);
        if (group==null) {
            return Collections.EMPTY_LIST;
        }
        return group.getChildren();
    }

    @Override
    public MetaLabeledTestResultGroupAction getTestResultAction() {
        if (parentAction ==null) {
            LOGGER.finest("null parentAction");
        }
        return parentAction;         
    }

    /** I wish the superclass didn't call this.
     * FIXME. TODO.
     * @return
     */
    @Override
    public List<TestAction> getTestActions() {
        return EMPTY_TEST_ACTIONS_LIST;
    }

    private static final List<TestAction> EMPTY_TEST_ACTIONS_LIST = new ArrayList<TestAction>();

    public MetaLabeledTestResultGroup(MetaLabeledTestResultGroupAction parentAction, String description ) {
        childrenByLabel = new HashMap<String, LabeledTestResultGroup>(10);
        failedTestsByLabel = new HashMap<String, Collection<TestResult>>(10);
        passedTestsByLabel = new HashMap<String, Collection<TestResult>>(10);
        skippedTestsByLabel = new HashMap<String, Collection<TestResult>>(10);
        allPassedTests = new HashSet<TestResult>();
        allFailedTests  = new HashSet<TestResult>();
        allSkippedTests = new HashSet<TestResult>();
        this.parentAction  = parentAction;
        this.description = description;
        cacheDirty = true; 
    }

    public void setParentAction(MetaLabeledTestResultGroupAction parentAction) {
        if (this.parentAction == parentAction) {
            return;
        }
        
        this.parentAction = parentAction;
        // Tell all of our children about the parent action, too.
        for (LabeledTestResultGroup group : childrenByLabel.values()) {
            group.setParentAction(parentAction);            
        }
    }

    public void addTestResult(String label, TestResult result) {
        if (! childrenByLabel.keySet().contains(label)) {
            childrenByLabel.put(label, new LabeledTestResultGroup(this, label, Arrays.asList(result)));
        }  else {
            childrenByLabel.get(label).addResult(result);
        }
        cacheDirty = true;
    }

    public void addTestResultGroup(String label, LabeledTestResultGroup group) {
        if (! childrenByLabel.keySet().contains(label)) {
            childrenByLabel.put(label, group);
        }  else {
            childrenByLabel.get(label).addAll(group);
        }
        cacheDirty = true;
    }

    @Override
    public String getTitle() {
        return "Test Reports";
    }

    @Override
    public String getName() {
        return ""; 
    }

    @Override
    public boolean isPassed() {
        if (cacheDirty) updateCache();
        return (failCount == 0) && (skipCount == 0);
    }

    @Override
    public String getChildTitle() {                                          
        return "Group"; 
    }

    @Override
    public MetaLabeledTestResultGroup getPreviousResult() {
        // TODO: consider caching
        if (parentAction == null) return null;
        AbstractBuild<?,?> b = parentAction.owner;
        while(true) {
            b = b.getPreviousBuild();
            if(b==null)
                return null;
            MetaLabeledTestResultGroupAction r = b.getAction(MetaLabeledTestResultGroupAction.class);
            if(r!=null)
                return r.getResultAsTestResultGroup();
        }
    }

    public int getPassDiff() {
        MetaLabeledTestResultGroup prev = getPreviousResult();
        if (prev==null) return getPassCount();
        return getPassCount() - prev.getPassCount();  
    }

    public int getSkipDiff() {
        MetaLabeledTestResultGroup prev = getPreviousResult();
        if (prev==null) return getSkipCount();
        return getSkipCount() - prev.getSkipCount();
    }

    public int getFailDiff() {
        MetaLabeledTestResultGroup prev = getPreviousResult();
        if (prev==null) return getFailCount();
        return getFailCount() - prev.getFailCount();
    }

    public int getTotalDiff() {
        MetaLabeledTestResultGroup prev = getPreviousResult();
        if (prev==null) return getTotalCount();
        return getTotalCount() - prev.getTotalCount();         
    }

    @Override
    public TestResult getResultInBuild(AbstractBuild<?,?> build) {
        AbstractTestResultAction action = build.getAction(AbstractTestResultAction.class);
        if (action == null) {
            return null;
        }
        if (action instanceof MetaLabeledTestResultGroupAction) {
            return ((MetaLabeledTestResultGroupAction)action).getResultAsTestResultGroup();
        }

        return (TestResult)action.getResult();
    }

    @Override
    public TestResult findCorrespondingResult(String id) {
        String groupName;
        String remainingId = null;
        int groupNameEnd = id.indexOf('/');
        if (groupNameEnd < 0) {
            groupName = id;
            remainingId = null;
        } else {
            groupName = id.substring(0, groupNameEnd);
            if (groupNameEnd != id.length()) {
                remainingId = id.substring(groupNameEnd + 1);
                if (remainingId.length() == 0) {
                    remainingId = null;
                }
            }
        }
        LabeledTestResultGroup group = getGroupByLabel(groupName);
        if (group != null) {
            if (remainingId != null) {
                return group.findCorrespondingResult(remainingId);
            } else {
                return group;
            }
        }

        return null;
    }
    
    @Override
    public int getFailedSince() {   // TODO: implement this.
        throw new UnsupportedOperationException("hudson.plugins.labeledgroupedtests.MetaLabeledTestResultGroup#getFailedSince: Not yet implemented."); // TODO: implement
    }

    @Override
    public Run<?,?> getFailedSinceRun() { // TODO: implement this.
        throw new UnsupportedOperationException("hudson.plugins.labeledgroupedtests.MetaLabeledTestResultGroup#getFailedSinceRun: Not yet implemented."); // TODO: implement
    }



    /**
     * Gets the number of failed tests.
     */
    @Exported(visibility=99)
    @Override
    public int getFailCount() {
        if (cacheDirty) updateCache();
        return failCount;
    }

    /**
     * Gets the total number of skipped tests
     * @return
     */
    @Exported(visibility=99)
    public int getSkipCount() {
        if (cacheDirty) updateCache();
        return skipCount;
    }

    /**
     * Gets the number of passed tests.
     */
    @Exported(visibility=99)
    @Override
    public int getPassCount() {
        if (cacheDirty) updateCache();
        return passCount;
    }

    @Override
    public Collection<? extends TestResult> getFailedTests() {
        // BAD result to force problems -- this method is now effectively UNIMPLEMENTED
        LOGGER.severe("getFailedTests unimplemented. Expect garbage.");
        if (cacheDirty) updateCache();
        return allFailedTests;
    }

    @Override
    public Collection<? extends TestResult> getSkippedTests() {
        LOGGER.severe("getSkippedTests unimplemented. Expect garbage.");
        if (cacheDirty) updateCache();
        return allSkippedTests;
    }

    @Override
    public Collection<? extends TestResult> getPassedTests() {
        LOGGER.severe("getSkippedTests unimplemented. Expect garbage.");
        if (cacheDirty) updateCache();
        return allPassedTests;        
    }

    @Override
    public Collection<? extends TestResult> getChildren() {
        if (cacheDirty) updateCache();
        return flattenTopTier(childrenByLabel.values());
    }

    @Override
    public boolean hasChildren() {
        if (cacheDirty) updateCache();
        return (totalCount != 0);
    }

    public AbstractBuild<?, ?> getOwner() {
        if (parentAction != null)
            return parentAction.owner;
        else
            return null;
    }

    /**
     * Strange API. A LabeledTestResultGroup is always the direct child of an
     * action, so we'll just return null here, for the parent. This is the
     * same behavior as TestResult. 
     * @return
     */
    @Override
    public TestObject getParent() {
        return null; 
    }

    @Exported(visibility=99)
    @Override
    public float getDuration() {
        if (cacheDirty) updateCache();
        return duration; 
    }

    @Exported(visibility=99)
    /* @Override */
    public String getDisplayName() {
        return "Test Result Groups";
    }

    @Exported(visibility=99)
    @Override
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }



    @Override
    public Object getDynamic(String token, StaplerRequest req, StaplerResponse rsp) {
        if (cacheDirty) updateCache();

        // If there's a test with that label, serve up that test.
        TestResult thatOne = childrenByLabel.get(token);
        if (thatOne != null) {
        	return thatOne;
        } else {
        	return super.getDynamic(token, req, rsp);
        }
    }


    @Override
    public String toPrettyString() {
        if (cacheDirty) updateCache();
        StringBuilder sb = new StringBuilder();
        Set<String> labels = childrenByLabel.keySet();
        for (String label: labels) {
            LabeledTestResultGroup listForThisLabel = childrenByLabel.get(label);
            sb.append(label); sb.append(" results:\n");
            sb.append(listForThisLabel.toPrettyString());
        }
        return sb.toString();
    }


    protected Collection<TestResult> flattenTopTier(Collection<LabeledTestResultGroup> twoTieredCollection) {
        // TODO: Consider caching
        if (twoTieredCollection == null || twoTieredCollection.isEmpty()) return Collections.emptyList(); 
        List<TestResult> flattenedList = new ArrayList<TestResult>();
        for (LabeledTestResultGroup topTierElement : twoTieredCollection) {
            flattenedList.addAll(topTierElement.getChildren());
        }
        return flattenedList;
    }

    private void storeInCache(String label, Map<String, Collection<TestResult>> sameStatusCollection, TestResult r) {
        if (sameStatusCollection.keySet().contains(label)) {
            sameStatusCollection.get(label).add(r);
        } else {
            List<TestResult> newCollection = new ArrayList<TestResult>(Arrays.asList(r));
            sameStatusCollection.put(label, newCollection);
        }
    }

    private void updateCache() {
        failedTestsByLabel.clear();
        skippedTestsByLabel.clear();
        passedTestsByLabel.clear();
        allFailedTests.clear();;
        allPassedTests.clear();
        allSkippedTests.clear();
        passCount = 0;
        failCount = 0;
        skipCount = 0;         
        float durationAccum = 0.0f;

        Collection<String> theLabels = childrenByLabel.keySet();
        for (String l : theLabels) {
            LabeledTestResultGroup groupForThisLabel = childrenByLabel.get(l);
            groupForThisLabel.setParentAction(parentAction);
            groupForThisLabel.tally();
            passCount += groupForThisLabel.getPassCount();
            failCount += groupForThisLabel.getFailCount();
            skipCount += groupForThisLabel.getSkipCount();            
            for (TestResult aResult : groupForThisLabel.getChildren()) {
                durationAccum += aResult.getDuration();
                if (aResult.isPassed()) {
                    storeInCache(l, passedTestsByLabel, aResult);
                    allPassedTests.add(aResult);
                } else if (aResult.getFailCount() > 0) {
                    storeInCache(l, failedTestsByLabel, aResult);
                    allFailedTests.add(aResult);
                } else {
                    storeInCache(l, skippedTestsByLabel, aResult);
                    allSkippedTests.add(aResult);
                }                
            }
        }

        duration = durationAccum;
        totalCount = passCount + failCount + skipCount; 

        cacheDirty=false; 
    }





}

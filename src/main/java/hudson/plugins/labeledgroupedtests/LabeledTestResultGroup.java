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

/**
 * Represents a list of several test results that share a common label.
 * It is designed to be used for children of MetaLabeledTestResultGroup. 
 */
@ExportedBean
public class LabeledTestResultGroup extends MetaTabulatedResult {

    /**
     * We expect a maximum of one AbstractTestResult per parser class. 
     */
    protected List<TestResult> children = null;
    protected transient List<TestResult> childrenWithFailures = null;
    protected transient List<TestResult> passedChildren = null;
    protected String label;
    protected int failCount = 0;
    protected int skipCount = 0;
    protected int passCount = 0;
    protected int totalCount = 0;
    protected float duration = 0;
    protected TestResult parent; // TODO: This should be transient.
    protected String description = "";
    protected transient boolean cacheDirty = true;
    protected Map<String, TestResult> childrenByName;
    protected Map<TestResult, String> nameToChildMap;
    protected boolean namesHaveBeenSet = false; 

    private static final Logger LOGGER = Logger.getLogger(LabeledTestResultGroup.class.getName());

    public LabeledTestResultGroup() {
        this(null, "unlabeled", new ArrayList<TestResult>());
    }

    public LabeledTestResultGroup(TestResult parent, String label, List<TestResult> children) {
        this.parent = parent;
        this.label = label;
        this.children = children;
        childrenWithFailures = new ArrayList<TestResult>();
        passedChildren = new ArrayList<TestResult>();
        namesHaveBeenSet = false;
        childrenByName = null; // we'll instantiate this just-in-time
        nameToChildMap = null; // we'll instantiate this just-in-time
        cacheDirty = true;  
    }

    @Override
    public void setParentAction(AbstractTestResultAction action) {
        for (TestResult result : children) {
            result.setParentAction(action);
        }
    }


    @Exported(visibility=99)
    @Override
    public int getPassCount() {
        if (cacheDirty) updateCache();
        return passCount;
    }

    @Exported(visibility=99)
    @Override
    public int getSkipCount() {
        if (cacheDirty) updateCache();
        return skipCount;
    }

    @Exported(visibility=99)
    @Override
    public int getFailCount() {
        if (cacheDirty) updateCache();
        return failCount; 
    }

    @Exported(visibility=99)
    public String getLabel() {
        return label;
    }

    @Override
    public String getName() {
        return label;
    }

    public String getDisplayNameForChild(TestResult c) {
        if (!namesHaveBeenSet) lockInNames();

        String niceName = nameToChildMap.get(c);
        if (niceName == null) {
            String msg = "LabeledTestResultGroup can't find a name for test child: " + c.toPrettyString(); 
            LOGGER.severe(msg);
            System.err.println(msg);
            return "no_such_child";
        }
        return niceName;
    }

    public TestResult getChildByIndex(int i) {
        if (i < 0 || i >= children.size()) {
            String msg = "Requested child with index " + i + " but only " + children.size() + "children exist";
            LOGGER.severe(msg);
            throw new NoSuchElementException(msg); 
        }
        return children.get(i);
    }

    @Override
    public Object getDynamic(String token, StaplerRequest req, StaplerResponse rsp) {
        if (cacheDirty) updateCache();
        if (!namesHaveBeenSet) {
            String msg = "trouble: we're in LabeledTestResultGroup.getDynamic, but we haven't created a name map yet.";
            LOGGER.severe(msg);
            throw new RuntimeException(msg); 
        }
        
        // If there's a test with that name, serve up that test.
        TestResult thatOne = childrenByName.get(token);
        if (thatOne != null) {
            return thatOne;
        } else {
            Object result = super.getDynamic(token, req, rsp);
            if (result != null) {
                return result;
            } else {
                return new Run.RedirectUp();
            }
        }
    }

    /**
     * Allow the object to rebuild its internal data structures when it is deserialized. 
     */                     
    public Object readResolve() {
        childrenWithFailures =  new ArrayList<TestResult>();
        passedChildren = new ArrayList<TestResult>();
        // TODO: should I lockInNames here? Probably. 
        updateCache();
        return this;
    }


    @Override
    public void tally() {
        // Always update, even if the cache isn't marked dirty.
        updateCache();
    }

    protected void updateCache() {
        // clean out all resutls
        failCount = 0;
        skipCount = 0;
        passCount = 0;
        totalCount = 0;
        childrenWithFailures.clear();
        passedChildren.clear();
        AbstractTestResultAction parentAction =
                (parent == null ? null : parent.getTestResultAction()); // not cool, but when we're in readResolve, we don't have much choice.
        float durationAccum = 0.0f;
        for (TestResult r : children) {
            r.setParentAction(parentAction);
            r.setParent(this);
            r.tally();
            durationAccum += r.getDuration();
            passCount += r.getPassCount();
            failCount += r.getFailCount();
            skipCount += r.getSkipCount(); 
            if (r.isPassed()) {
                passedChildren.add(r);
            } else if (r.getFailCount() > 0) {
                childrenWithFailures.add(r);
            } 
        }

        duration = durationAccum;
        totalCount = passCount + failCount + skipCount; 
        cacheDirty = false;
    }

    @Override
    public Collection<? extends TestResult> getFailedTests() {
        if (cacheDirty) updateCache();
        return childrenWithFailures;
    }

    @Exported(visibility=99)
    @Override
    public Collection<? extends TestResult> getChildren() {
        if (cacheDirty) updateCache();
        return children; 
    }

    @Override
    public boolean hasChildren() {
        if (cacheDirty) updateCache();
        return children.size() > 0;
    }
    
    @Override
    public AbstractBuild<?, ?> getOwner() {
        if (parent == null) return null;
        return parent.getOwner();
    }

    @Override
    public TestObject getParent() {
        return parent; 
    }

    @Override
    public float getDuration() {
        if (cacheDirty) updateCache();
        return duration; 
    }

    @Exported(visibility=99)
    public String getDisplayName() {
        return label;   
    }

    /**
     * Add the result, unless we've already got it
     * @param result
     */
    public void addResult(TestResult result) {
        if (!children.contains(result)) {
            children.add(result);
            cacheDirty = true;
        }
    }

    /**
     * Add the children from this group, unless we've already got them
     * @param group
     */
    public void addAll(LabeledTestResultGroup group) {
        for (TestResult r : group.getChildren()) {
            if (!children.contains(r)) {
                children.add(r);
                cacheDirty = true;
            }
        }
    }


    @Override
    public TestResult getPreviousResult() {
        if (parent==null) {
            LOGGER.warning("Can't getPreviousResult; parent was null."); 
            return null;
        }
        AbstractBuild<?,?> b = parent.getOwner();
        if (b==null) {
            LOGGER.warning("Can't getPreviousResult; parent.getOwner() was null");
            return null;
        }
        while(true) {
            AbstractBuild<?,?> n = b;
            b = b.getPreviousBuild();
            if(b==null) {
                if (n.getNumber()!=1) { 
                    LOGGER.warning("Can't getPreviousResult; no previousBuild can be found on build " + n.getNumber() + ".");
                }
                return null;
            }
            MetaLabeledTestResultGroupAction r = b.getAction(MetaLabeledTestResultGroupAction.class);
            if(r!=null) {
                return r.getLabeledTestResultGroup(label);
            }
            // If we get to here, then there was no LabeledGroupsTestResultAction on the previous build.
            // Should we give up and return null, or walk to a previous build? The core code look
            // farther back, so let's do that, too. 
        }
    }



    @Override
    public TestResult getResultInBuild(AbstractBuild<?,?> build) {
        MetaLabeledTestResultGroupAction action = build.getAction(MetaLabeledTestResultGroupAction.class);
        if (action == null) {
            // Fall back to any AbstractTestResultAction if we are showing the
            // unit group, for historical purposes.
            if (label.equals("unit")) {
                AbstractTestResultAction tra = build.getAction(AbstractTestResultAction.class);
                if (tra == null) {
                    return null;
                }

                return (TestResult)tra.getResult();
            } else {
                return null;
            }
        }
        return action.getLabeledTestResultGroup(label);
    }

    @Override
    public TestResult findCorrespondingResult(String id) {
        String childName;
        String remainingId = null;
        int childNameEnd = id.indexOf('/');
        if (childNameEnd < 0) {
            childName = id;
            remainingId = null;
        } else {
            childName = id.substring(0, childNameEnd);
            if (childNameEnd != id.length()) {
                remainingId = id.substring(childNameEnd + 1);
            }

        }
        TestResult child = childrenByName.get(childName);
        if (child != null) {
            if (remainingId != null) {
                return child.findCorrespondingResult(remainingId);
            } else {
                return child;
            }
        }

        return null;
    }

    @Override
    public String toPrettyString() {
        if (cacheDirty) updateCache();
        StringBuilder sb = new StringBuilder();
        for (TestResult r: children) {
            sb.append("\t").append(label); sb.append(": ").append(r.toPrettyString());
        }
        return sb.toString();
    }

    public int getPassDiff() {
        TestResult prev = getPreviousResult();
        if (prev==null) return getPassCount();
        return getPassCount() - prev.getPassCount();
    }

    public int getSkipDiff() {
        TestResult prev = getPreviousResult();
        if (prev==null) return getSkipCount();
        return getSkipCount() - prev.getSkipCount();
    }

    public int getFailDiff() {
        TestResult prev = getPreviousResult();
        if (prev==null) return getFailCount();
        return getFailCount() - prev.getFailCount();
    }

    public int getTotalDiff() {
        TestResult prev = getPreviousResult();
        if (prev==null) return getTotalCount();
        return getTotalCount() - prev.getTotalCount();
    }

      /**
     * This method records a unique name for each child result.
     * Either this *or* lockInNames should be called. setNameMap is much better,
     * This method allows the caller to provide meaningful names based on information
     * that will not be available later. See ${@link LabeledTestResultGroupPublisher :peform}
     */

    public void setNameMap(HashMap<TestResult, String> resultToNameMap) {
        if (namesHaveBeenSet || (childrenByName != null) || (nameToChildMap != null)) {
            String msg = "LabeledTestResultGroup is in a bad state. setNameMap called, but we already have a name map.";
            LOGGER.severe(msg);
            System.out.println(msg);
            throw new RuntimeException(msg);
        }

        childrenByName = new HashMap<String, TestResult>( totalCount );
        nameToChildMap  = new HashMap<TestResult, String>( totalCount );

        // Go through each of the results that we already have.
        for (TestResult r : children) {
            // Look up the name for that result group, as specified in the map passed in
            String name = resultToNameMap.get(r);
            if (name==null) {
                String msg = "LabeledTestResultGroup.setNameMap: can't find a name for that test result.";
                LOGGER.severe(msg);
                System.err.println(msg);
            }

            // Store the mapping from name to result, and from result to name, in our two maps.
            childrenByName.put(name, r);
            nameToChildMap.put(r, name);
        }

        namesHaveBeenSet = true;
    }




    /**
     * Either this *or* setNameMap should be called. Both record a unique name for
     * each child result. This method generates names based only on the information it
     * has available to it, while setNameMap can use additional information available
     * outside this class.
     * I don't expect that this will be called in the normal course of using the
     * LabeledResultGroupArchiver, but I've implemented it so that we can still
     * drill down to chilldren of the result group even if the name map was somehow
     * lost or corrupted.
     */
    protected void lockInNames() {
        if (namesHaveBeenSet || (childrenByName != null) || (nameToChildMap != null)) {
            String msg = "LabeledTestResultGroup is in a bad state. lockInNames is being called, but names have already been set.";
            LOGGER.severe(msg);
            System.out.println(msg);
            throw new RuntimeException(msg);
        }
        LOGGER.warning("Using lockInNames to build a name map. setNameMap is preferred.");

        childrenByName = new HashMap<String, TestResult>( totalCount );
        nameToChildMap  = new HashMap<TestResult, String>( totalCount );
        int i = 0;

        for (TestResult aResult : children) {
            StringBuilder sb = new StringBuilder();
            sb.append("result-").append(i); 
            String niceChildName = sb.toString();
            childrenByName.put(niceChildName, aResult);
            nameToChildMap.put(aResult, niceChildName);
        }

        namesHaveBeenSet = true; 
    }
}

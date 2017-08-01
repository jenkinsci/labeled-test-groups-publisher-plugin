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

import com.thoughtworks.xstream.XStream;
import hudson.XmlFile;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.test.AbstractTestResultAction;
import hudson.tasks.test.TestObject;
import hudson.util.XStream2;
import org.kohsuke.stapler.StaplerProxy;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * User: Benjamin Shine bshine
 * Date: Oct 21, 2009
 * Time: 2:39:57 PM
 */
public class MetaLabeledTestResultGroupAction extends AbstractTestResultAction<MetaLabeledTestResultGroupAction> implements StaplerProxy {

    static public final String RESULT_DATA_FILENAME = "testResultGroups.xml";
    private int failCount;
    private int skipCount;
    private Integer totalCount; // TODO: can we make this just a normal int, and find another way to check
    // whether we're populated yet? (This technique is borrowed from hudson core TestResultAction.) 


    /**
     * @deprecated use resultGroupReference instead.
     */
    protected MetaLabeledTestResultGroup resultGroup;

    /**
     * Store the result group itself in a separate file so we don't eat up
     * too much memory.
     */
    private transient WeakReference<MetaLabeledTestResultGroup> resultGroupReference;

    public MetaLabeledTestResultGroupAction(Run<?, ?> owner, MetaLabeledTestResultGroup r, TaskListener listener) {
        super();
        this.onAttached(owner);
        setResult(r, listener);
    }


    /**
     * Store the data to a separate file, and update our cached values.
     */
    public synchronized void setResult(MetaLabeledTestResultGroup r, TaskListener listener) {

        r.setParentAction(this);

        totalCount = r.getTotalCount();
        failCount = r.getFailCount();
        skipCount = r.getSkipCount();

        // persist the data
        try {
            getDataFile().write(r);
        } catch (IOException e) {
            e.printStackTrace(listener.fatalError("Failed to save the labeled test groups publisher's test result"));
        }

        this.resultGroupReference = new WeakReference<MetaLabeledTestResultGroup>(r);
    }

    private XmlFile getDataFile() {
        return new XmlFile(XSTREAM, new File(run.getRootDir(), RESULT_DATA_FILENAME));
    }

    public Object getTarget() {
        return getResult();
    }

    /**
     * Gets the number of failed tests.
     */
    public int getFailCount() {
        if (totalCount == null)
            getResult();    // this will load the result from disk if necessary
        return failCount;
    }

    /**
     * Gets the total number of skipped tests
     *
     * @return
     */
    public int getSkipCount() {
        if (totalCount == null)
            getResult();   // this will load the result from disk if necessary
        return skipCount;
    }

    /**
     * Gets the total number of tests.
     */
    public int getTotalCount() {
        if (totalCount == null)
            getResult();    // this will load the result from disk if necessary
        return totalCount;
    }


    /**
     * Get the result that this action represents. If necessary, the result will be
     * loaded from disk.
     *
     * @return
     */
    public synchronized MetaLabeledTestResultGroup getResult() {
        // If we've got a legacy data structure, inline, then just return it,
        // no fancy loading-on-demand. 
        if (this.resultGroup != null)
            return this.resultGroup;
        
        MetaLabeledTestResultGroup r;
        if (resultGroupReference == null) {
            r = load();
            resultGroupReference = new WeakReference<MetaLabeledTestResultGroup>(r);
        } else {
            r = resultGroupReference.get();
        }

        if (r == null) {
            r = load();
            resultGroupReference = new WeakReference<MetaLabeledTestResultGroup>(r);
        }
        if (r == null) {
            logger.severe("Couldn't get result for MetaLabeledTestResultGroup " + this);
            return null;
        }

        if (totalCount == null) {
            totalCount = r.getTotalCount();
            failCount = r.getFailCount();
            skipCount = r.getSkipCount();
        }
        return r;
    }

    /**
     * Loads a {@link MetaLabeledTestResultGroup} from disk.
     */
    private MetaLabeledTestResultGroup load() {
        MetaLabeledTestResultGroup r;
        try {
            r = (MetaLabeledTestResultGroup) getDataFile().read();
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to load " + getDataFile(), e);
            r = new MetaLabeledTestResultGroup();   // return a dummy
        }
        r.setParentAction(this);
        return r;
    }

    /**
     * This convenience method is what getResult() should have been,
     * but with a specified return type.
     *
     * @return
     */
    public MetaLabeledTestResultGroup getResultAsTestResultGroup() {
        return getResult();
    }

    public LabeledTestResultGroup getLabeledTestResultGroup(String label) {
        return getResult().getGroupByLabel(label);
    }

    public String getDescription(TestObject testObject) {
        return getResult().getDescription();
    }

    public void setDescription(TestObject testObject, String s) {
        getResult().setDescription(s);
    }

    public String getDisplayName() {
        return "Test Results";
    }

    /**
     * Bring this object into an internally-consistent state after deserializing it.
     * For a MetaLabeledTestResultGroupAction , we don't have to do anything,
     * because the WeakReference handles loading the actual test result data
     * from disk when it is requested.
     * The only case where we have something to do here is if this object was
     * serialized with the test result data inline, rather than in a separate file.
     * If the data was inline, we do a little dance to move the data into a separate
     * file.
     *
     * @return
     */
    public Object readResolve() {
        // This method is called when an instance of this object is loaded from
        // persistent storage into memory. We use this opportunity to detect
        // and convert from storing the test results in the same file as the
        // build.xml to storing the test results in a separate file. 
        if (this.resultGroup != null) {

            // If there was a non-null resultGroup stored in the same file as this action,
            // tell this result group to get into a valid state, with this object
            // as the parent action.
            this.resultGroup.setParentAction(this);
            this.resultGroup.tally();
            totalCount = this.resultGroup.getTotalCount();
            failCount = this.resultGroup.getFailCount();
            skipCount = this.resultGroup.getSkipCount();

            // ******************************************************************
            // Slightly riskier: in-memory modifications of legacy data
            // 
            // Free up memory at startup when loading builds with inline test results.
            // Save the the results out to a separate file that can be re-loaded
            // on demand, and release the memory used by the test results,
            // but do not modify the original build.xml
            //
            // MetaLabeledTestResultGroup tmp = this.resultGroup;
            //      Save the result with the new separate-file/weak reference system
            // setResult(tmp, null);
            // // Null out this field so that its memory can be reclaimed.
            // this.resultGroup = null;
            // ******************************************************************


            // ******************************************************************
            // Seriously risky: on-disk modification of legacy data.
            //
            // This code will re-save the build file without the inline test result data,
            // but I'm leaving it commented out because we risk data loss or corruption
            // if we get synchronization wrong.
            //
            // MetaLabeledTestResultGroup tmp = this.resultGroup;
            //      Save the result with the new separate-file/weak reference system
            // setResult(tmp, null);
            //
            // // Null out this field so that its memory can be reclaimed.
            // this.resultGroup = null;
            //
            // // Save the build, so that it no longer stores the whole test data inside of itself.
            //    try {
            //        this.owner.save();
            //    } catch (IOException e) {
            //        logger.warning("Couldn't rewrite build.xml from MetaLabeledTestResultGroupAction: "
            //                      + Functions.printThrowable(e));
            //    }
            // ******************************************************************
        }

        return this;
    }

    private static final Logger logger = Logger.getLogger(MetaLabeledTestResultGroupAction.class.getName());

    private static final XStream XSTREAM = new XStream2();
}

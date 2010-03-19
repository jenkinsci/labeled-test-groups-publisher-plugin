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
package hudson.plugins.labeledandgroupedtests;


import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlTable;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.Page;
import hudson.tasks.test.*;
import hudson.tasks.junit.*;
import hudson.tasks.junit.PackageResult;
import hudson.model.*;
import hudson.plugins.labeledgroupedtests.MetaLabeledTestResultGroupAction;
import hudson.plugins.labeledgroupedtests.MetaLabeledTestResultGroup;
import hudson.plugins.labeledgroupedtests.LabeledTestResultGroup;
import hudson.slaves.DumbSlave;
import hudson.tasks.test.TestResult;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.recipes.LocalData;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * Exercise a project with a known configuration using multiple test
 * formats and multiple test phases
 */
public class CombinationOfParsersAndLabelsTest extends EnhancedHudsonTestCase {

    private static final String COMBO_PROJECT_NAME = "combo";
    private static final String JUST_JAVA_GROUPS = "just_java_groups";
    private static final String CAT_PROJECT_NAME = "cat"; 


    private void SKIP_buildComboProjectAndValidate(FreeStyleProject freestyleProj) throws Exception {
        FreeStyleBuild build = freestyleProj.scheduleBuild2(0).get(10, TimeUnit.SECONDS);
        // Hey! we should *not* have build success! Dude! There's a test failure!   
        assertBuildStatus(Result.UNSTABLE, build);

        // I need an action
        MetaLabeledTestResultGroupAction action = build.getAction(MetaLabeledTestResultGroupAction.class);
        assertNotNull("we should have an action", action);

        // Look for a MetaLabeledTestResultGroup at the top level
        MetaLabeledTestResultGroup result = action.getResultAsTestResultGroup();
        assertNotNull("we should have a non-null result group", result);

        assertTrue( "should have at least one test", result.getTotalCount() > 0);
        assertTrue( "should have at least one passing test", result.getPassCount() > 0);
        assertTrue( "should have at least one failing test", result.getFailCount() > 0);
        assertEquals("should have zero skipped tests", 0, result.getSkipCount() );

        HudsonTestCase.WebClient wc = new HudsonTestCase.WebClient();

        // On the project page:
        HtmlPage projectPage = wc.getPage(freestyleProj);
        assertGoodStatus(projectPage);
        HtmlPage buildPage = wc.goTo(freestyleProj.getLastBuild().getUrl() );
        assertGoodStatus(buildPage);
        HtmlPage testReportPage = wc.goTo(freestyleProj.getLastBuild().getUrl() + "/testReport/");
        assertGoodStatus(testReportPage);

        //      we should have a link that reads "Latest Test Result"
        //      that link should go to http://localhost:8080/job/breakable/lastBuild/testReport/
//        assertXPath(projectPage, "//a[@href='lastBuild/testReport/']");
//        assertXPathValue(projectPage, "//a[@href='lastBuild/testReport/']", "Latest Test Result");
//        assertXPathValueContains(projectPage, "//a[@href='lastBuild/testReport/']", "Latest Test Result");
//        //      after "Latest Test Result" it should say "no failures"
//        assertXPathResultsContainText(projectPage, "//td", "(no failures)");
//        //      there should be a test result trend graph
//        assertXPath(projectPage, "//img[@src='test/trend']");
//        // the trend graph should be served up with a good http status
//        Page trendGraphPage = wc.goTo(project.getUrl() + "/test/trend", "image/png");
//        assertGoodStatus(trendGraphPage);
    }
    
    @LocalData
    public void SKIP_testPublishingTests() throws Exception {
        SKIP_buildComboProjectAndValidate(setupProject(COMBO_PROJECT_NAME));
    }

    @LocalData
    public void SKIP_testRemotePublishingResults() throws Exception {
        FreeStyleProject freestyleProj = setupProject(COMBO_PROJECT_NAME);
        DumbSlave s = createOnlineSlave();
        
        freestyleProj.setAssignedLabel(s.getSelfLabel());

        setupRemoteData(COMBO_PROJECT_NAME, s, freestyleProj, "*.xml");
        
        SKIP_buildComboProjectAndValidate(freestyleProj);
    }

    private void buildJavaGroupingProjectAndValidate(FreeStyleProject freestyleProj) throws Exception {
        FreeStyleBuild build = freestyleProj.scheduleBuild2(0).get(10, TimeUnit.SECONDS);
        // Hey! we should *not* have build success! Dude! There's a test failure!
        assertBuildStatus(Result.UNSTABLE, build);

        assertJavaProjectTestResults(build);
    }

    private void assertJavaProjectTestResults(FreeStyleBuild build) {
        // Make sure we've got some test results
        MetaLabeledTestResultGroupAction action = build.getAction(MetaLabeledTestResultGroupAction.class);
        assertNotNull("we should have an action", action);

        // Look for a MetaLabeledTestResultGroup at the top level
        MetaLabeledTestResultGroup result = action.getResultAsTestResultGroup();
        assertNotNull("we should have a non-null result group", result);
        AbstractBuild<?,?> owner = result.getOwner();
        assertNotNull("the result should have a non-null owner", owner);

        
        // Display correct *total* number of tests, not just the number of groups
        assertEquals( "should have 132 total groups of tests", 132, result.getTotalCount());
        assertEquals( "should have zero skipped tests", 0, result.getSkipCount());
        assertEquals( "should have exactly one failing test", 1, result.getFailCount());

        // We want a non-zero duration
        assertTrue( "the tests should have duration of at least several seconds", result.getDuration() > 1.0f);

        // Drill down to the LabeledTestResultGroup
        // We know that there are labels "unit" and "smoke"
        LabeledTestResultGroup g = result.getGroupByLabel("unit");
        assertNotNull("we have a 'unit' label, with children", g);
        assertEquals("expecting exactly 1 child of unit group", 1, g.getChildren().size());
        assertEquals("unit test total children count", 39, g.getTotalCount());
        assertEquals("unit test pass count", 39, g.getPassCount());
        assertEquals("unit test fail count", 0, g.getFailCount());
        TestResult firstChild = g.getChildByIndex(0);
        assertNotNull("we should have exactly one child", firstChild);

        LabeledTestResultGroup smoke = result.getGroupByLabel("smoke");
        assertNotNull("we have a 'smoke' label, with children", smoke);
        assertEquals("expecting exactly 1 child of smoke group", 1, smoke.getChildren().size());
        assertEquals("smoke test total children count", 81, smoke.getTotalCount());
        assertEquals("smoke test pass count", 80, smoke.getPassCount());
        assertEquals("smoke test fail count", 1, smoke.getFailCount());

        TestResult firstSmokeChild = smoke.getChildByIndex(0);

        assertEquals("expect 1 failure", 1, firstSmokeChild.getFailCount());
        // Can't test this method; it's unimplemented: assertEquals("expect 1 failure", 1, firstSmokeChild.getFailedTests().size());
        assertEquals("exepct pass", 80, firstSmokeChild.getPassCount());
        // Can't test this method; it's unimplemented: assertEquals("expect  pass", 80, firstSmokeChild.getPassedTests().size());

        // Drill down all the way into the TestResult (junit)        
        assertTrue( "it should be a TestResult", firstSmokeChild instanceof TestResult );
        hudson.tasks.junit.TestResult tr = (hudson.tasks.junit.TestResult) firstSmokeChild;

        PackageResult aPackage = tr.byPackage("hudson.matrix");
        assertNotNull("should have a hudson.matrix package", aPackage);
        assertEquals("number of passed tests in hudson.matrix", 3, aPackage.getPassCount());

        aPackage = tr.byPackage("hudson.security");
        assertNotNull("should have a hudson.security package", aPackage);
        assertEquals("number of failed tests in hudson.security", 1, aPackage.getFailCount());
        assertEquals("number of passed tests in hudson.security", 1, aPackage.getPassCount());

        // TODO: re-check all the above data with a webclient. 
        // TODO: check the xml api
    }

    @LocalData
    public void testJustJavaGrouping() throws Exception {
        buildJavaGroupingProjectAndValidate(setupProject(JUST_JAVA_GROUPS));
    }

    /**
     * Tests whether we can get result in previous builds. That behavior is not yet
     * supported in labeled test groups, so I'm deactivating this test.
     * @throws Exception
     */
    @LocalData
    public void SKIP_testGetResultInBuild() throws Exception {
        int numberOfBuildsToRun = 5;
        assertTrue("gotta have enough builds", numberOfBuildsToRun > 3);
        FreeStyleProject proj = setupHistoryTest(numberOfBuildsToRun);
        FreeStyleBuild lastBuild = proj.getLastBuild();
        FreeStyleBuild prevBuild = lastBuild.getPreviousBuild();
        FreeStyleBuild firstBuild = proj.getFirstBuild();
        MetaLabeledTestResultGroupAction action = lastBuild.getAction(MetaLabeledTestResultGroupAction.class);
        MetaLabeledTestResultGroup metaResult = action.getResultAsTestResultGroup();
        assertMatchingResults("metaResult in lastBuild", metaResult, metaResult.getResultInBuild(lastBuild));
        assertMatchingResults("metaResult in prevBuild", metaResult, metaResult.getResultInBuild(prevBuild));
        assertMatchingResults("metaResult in firstBuild", metaResult, metaResult.getResultInBuild(firstBuild));

        for (String l : metaResult.getLabels()) {
            LabeledTestResultGroup group = metaResult.getGroupByLabel(l);
            TestResult groupInFirstBuild = group.getResultInBuild(firstBuild);
            TestResult groupInLastBuild = group.getResultInBuild(lastBuild);
            TestResult groupinPrevBuild = group.getResultInBuild(prevBuild);

            assertMatchingResults("group in firstBuild", group, groupInFirstBuild); 
            assertMatchingResults("group in lastBuild", group, groupInLastBuild);
            assertMatchingResults("group in prevBuild", group, groupinPrevBuild);

            // TODO: Drill even farther down
        }
    }

    @LocalData
    public void testRemoteJavaGrouping() throws Exception {
        FreeStyleProject freestyleProj = setupProject(JUST_JAVA_GROUPS);
        DumbSlave s = createOnlineSlave();

        freestyleProj.setAssignedLabel(s.getSelfLabel());

        setupRemoteData(JUST_JAVA_GROUPS, s, freestyleProj, "*.xml");

        buildJavaGroupingProjectAndValidate(freestyleProj);
    }

    public FreeStyleProject setupHistoryTest(int numberOfBuildsToRun) throws Exception {
        FreeStyleProject proj = setupProject(JUST_JAVA_GROUPS);

        // build several times
       
        List<FreeStyleBuild> builds = new ArrayList<FreeStyleBuild>(numberOfBuildsToRun);
        for (int i = 0; i < numberOfBuildsToRun; i++) {
            FreeStyleBuild build = proj.scheduleBuild2(0).get(10, TimeUnit.SECONDS); // leave time for interactive debugging
            builds.add(build);
        }
        return proj;
    }

    @LocalData
    public void testHistory() throws Exception {
        FreeStyleProject proj = setupHistoryTest(3);

        String[] relativePaths = {
                "testReport/history/",
                "testReport/smoke/history/",
                /* We know this will fail  "testReport/integration/integration-hudson.tasks.junit.JUnitParser/history/", */
        };
        // Go to the history page
        HudsonTestCase.WebClient wc = new HudsonTestCase.WebClient();

        for (String relativeUrl : relativePaths) {
            HtmlPage page = wc.goTo(proj.getLastBuild().getUrl() + relativeUrl );
            assertGoodHistoryPage(page);
        }

        // then visit http://localhost:8080/job/just_java_groups/3/testReport/integration/history/
        // and http://localhost:8080/job/just_java_groups/3/testReport/integration/integration-hudson.tasks.junit.JUnitParser/history/

        // it should have some useful data, and it should not say "More than 1 builds are needed for the chart."

        // Go here:
        // http://localhost:8080/job/just_java_groups/3/testReport/regression/
        // we should have a good previous result


    }

    private void assertGoodHistoryPage(HtmlPage page) {
        String uri = page.getDocumentURI();
        assertTrue("good http status for " + uri, isGoodHttpStatus(page.getWebResponse().getStatusCode()));

        // The page should not say "More than 1 builds are needed for the chart."
        final String NO_HISTORY_CHART_MSG = "More than 1 builds are needed for the chart.";
        final String PROGRAMMING_ERROR_MSG = "programming error";
        String pageText = page.asText();
        assertFalse("should not say 'more than 1 builds are needed for the chart on page '" + uri, pageText.contains(NO_HISTORY_CHART_MSG));
        assertFalse("should not say 'programming error' on page"  + uri, pageText.contains(PROGRAMMING_ERROR_MSG));

        HtmlElement wholeTable = page.getElementById("testresult");
        assertNotNull("table with id 'testresult' exists on page " + uri, wholeTable);
        assertTrue("wholeTable is a table on page "  + uri, wholeTable instanceof HtmlTable);
        HtmlTable table = (HtmlTable) wholeTable;

        // We really want to call table.getRowCount(), but
        // it returns 1, not the real answer,
        // because this table has *two* tbody elements,
        // and getRowCount() only seems to count the *first* tbody.
        // Maybe HtmlUnit can't handle the two tbody's. In any case,
        // the tableText.contains tests do a (ahem) passable job
        // of detecting whether the history results are present.

        String tableText = table.getTextContent();
        assertTrue("table text should have header that says Fail on page "  + uri, tableText.contains("Fail"));
        assertTrue("table text should have header that says Skip on page "  + uri, tableText.contains("Skip"));

        // assert that there is a table with some interesting history in it
        assertTrue("table text content should have the project name in it on page "  + uri,
                tableText.contains(JUST_JAVA_GROUPS));
        assertTrue("table text content should have some build numbers in it on page "  + uri,
                tableText.contains("#1"));

    }

    @LocalData
    public void testPromotedFailureLinks() throws Exception, InterruptedException {
        // We should have a list of links to failures.
        // Those links should be traversible.
         FreeStyleProject freestyleProj = setupProject(CAT_PROJECT_NAME);
         FreeStyleBuild build = freestyleProj.scheduleBuild2(0).get(10, TimeUnit.SECONDS);

        HudsonTestCase.WebClient wc = new HudsonTestCase.WebClient();
        HtmlPage failingTestPage = wc.goTo(freestyleProj.getLastBuild().getUrl() + "testReport/special/junit/tacoshack.meals/NachosTest/testBeanDip/");
        assertXPath(failingTestPage, "//h1[@class='result-failed']" );

        String testReportPageUrl =  freestyleProj.getLastBuild().getUrl() + "/testReport/special/junit/";
        HtmlPage testReportPage = wc.goTo( testReportPageUrl );
                
        Page packagePage = testReportPage.getFirstAnchorByText("tacoshack.meals").click();
        assertGoodStatus(packagePage); // I expect this to work; just checking that my use of the APIs is correct.

        // Now we're on that page. We should be able to find a link to the failed test in there.
        HtmlAnchor anchor = testReportPage.getFirstAnchorByText("tacoshack.meals.NachosTest.testBeanDip");
        String href = anchor.getHrefAttribute();
        System.out.println("link is : " + href);
        Page failureFromLink = anchor.click();
        assertGoodStatus(failureFromLink);
       
    }


    /**
     * This test is all about tally(). 
     * 
     * @throws Exception
     */
     @LocalData
     public void testPersistence() throws Exception {
         FreeStyleProject project = setupProject(JUST_JAVA_GROUPS);
         FreeStyleBuild buildBeforeShutdown = project.scheduleBuild2(0).get(60, TimeUnit.SECONDS);
         assertJavaProjectTestResults(buildBeforeShutdown);
         reloadHudson();
         FreeStyleProject projectAfterReload =  (FreeStyleProject) hudson.getItem(JUST_JAVA_GROUPS);
         FreeStyleBuild build = projectAfterReload.getBuildByNumber(1);
         assertJavaProjectTestResults(build);
     }

    /**
     * This started failing when we merged in 1.343 from upstream, because Hudson.load()
     * doesn't seem to be an API anymore.
     * @throws NoSuchMethodException
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     */
     private void reloadHudson() throws NoSuchMethodException,
             IllegalAccessException, InvocationTargetException {
         Method m = Hudson.class.getDeclaredMethod("loadTasks");
         m.setAccessible(true);
         m.invoke(hudson);
     }

    /**
     * Test that we can switch the graph on the project summary page to
     * just show failures. 
     * @throws Exception
     */
    @LocalData
    public void testJustShowFailures() throws Exception {
        FreeStyleProject proj = setupHistoryTest(4);
        assertNotNull("our project should exist", proj);
        HudsonTestCase.WebClient wc = new HudsonTestCase.WebClient();
        HtmlPage projectPage = wc.goTo(proj.getUrl());
        String pageContent = projectPage.asXml();
        String XPATH_TO_LINK = "//a[@id='change-mode-link']";
        Object o = projectPage.getDocumentElement().selectSingleNode(XPATH_TO_LINK);
        assertNotNull("found the link", o);
        assertTrue("the link is a node", o instanceof org.w3c.dom.Node);
        HtmlAnchor anchor = projectPage.getAnchorByName("change-mode-link");
        assertNotNull("found the anchor", anchor);
        Page afterClick = anchor.click();
        assertGoodStatus(afterClick);

        // TODO: verify that the image we get back has the expected data. (I've verified this interactively.)
        // TODO: verify that we set a cookie correctly. I'm not sure if the WebClient handles cookies correctly.

    }
}

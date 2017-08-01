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

import org.jvnet.hudson.test.HudsonTestCase;

import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.DomNode;
import com.gargoylesoftware.htmlunit.xml.XmlPage;
import hudson.model.*;
import org.jvnet.hudson.test.recipes.LocalData;

import java.util.concurrent.TimeUnit;


/**
 * Test to check our pass/fail/skip counts and grouping
 */
public class CountingTest extends EnhancedHudsonTestCase {

    final String TEST_REPORT = "testReport/";
    final String API_XML = "api/xml";
    final String UNIT_GROUP = "unit/";
    final String REGRESSION_GROUP = "regression/";
    final String REGGRESION_JUNIT_GROUP = "junit/";
    final String UNIT_CPP_GROUP = "cppunit/";


    @LocalData
    public void SKIP_testOverallCount() throws Exception {
        String log = "(no log collected)";
        try {
            // Load in a project with known counts
            FreeStyleProject project = setupProject("knowncount");
            assertNotNull(project);

            // Run and validate a traditional build
            FreeStyleBuild build = project.scheduleBuild2(0).get(5, TimeUnit.MINUTES);
            log = getLog(build);
            assertBuildStatus(Result.UNSTABLE, build);

            HudsonTestCase.WebClient wc = new HudsonTestCase.WebClient();

            // Test overall results via xml api
            // http://localhost:8080/job/knowncount/5/testReport/api/xml is correct: 69/1/0

            XmlPage testReportPage = wc.goToXml(project.getLastBuild().getUrl() + TEST_REPORT + API_XML);
            assertXPathValue(testReportPage, "/metaLabeledTestResultGroup/failCount/text()", "1" );
            assertXPathValue(testReportPage, "/metaLabeledTestResultGroup/passCount/text()", "69" );
            assertXPathValue(testReportPage, "/metaLabeledTestResultGroup/skipCount/text()", "0" );

            // Test single-group junit parser results via xml api: 
            // http://localhost:8080/job/knowncount/5/testReport/regression/regression-hudson.tasks.junit.JUnitParser/api/xml should have 0/1/0
            XmlPage regressionJunitPage =  wc.goToXml(project.getLastBuild().getUrl() + TEST_REPORT + REGRESSION_GROUP + REGGRESION_JUNIT_GROUP + API_XML);
            assertXPathValue(regressionJunitPage, "/testResult/failCount/text()", "1" );
            assertXPathValue(regressionJunitPage, "/testResult/passCount/text()", "0" );
            assertXPathValue(regressionJunitPage, "/testResult/skipCount/text()", "0" );

            // To demonstrate labeled test groups publisher: via xml api, pass count is wrong for a group (but html is ok)
            // http://localhost:8080/job/knowncount/5/testReport/unit/api/xml should have 25/0/0, actually has 2/0/0
            XmlPage unitReportPage = wc.goToXml(project.getLastBuild().getUrl() + TEST_REPORT + UNIT_GROUP + API_XML);
            assertXPathValue(unitReportPage, "/labeledTestResultGroup/failCount/text()", "0" );
            assertXPathValue(unitReportPage, "/labeledTestResultGroup/passCount/text()", "25" );
            assertXPathValue(unitReportPage, "/labeledTestResultGroup/skipCount/text()", "0" );
            // The same problem is visible at these urls, too:
            // http://localhost:8080/job/knowncount/5/testReport/regression/api/xml should have 18/1/0, actually has 0/1/0
            // http://localhost:8080/job/knowncount/5/testReport/smoke/api/xml should have 26/0/0, actually has 4/0/0


            // http://localhost:8080/job/knowncount/5/testReport/unit/unit-hudson.plugins.cppunitparser.CPPUnitTestResultParser/api/xml serves up
            XmlPage cppUnitPage = wc.goToXml(project.getLastBuild().getUrl() + TEST_REPORT + UNIT_GROUP + UNIT_CPP_GROUP + API_XML);
            assertXPathValue(cppUnitPage, "/CPPUnitTestResultGroup/failCount/text()", "0" );
            assertXPathValue(cppUnitPage, "/CPPUnitTestResultGroup/passCount/text()", "23" );
            assertXPathValue(cppUnitPage, "/CPPUnitTestResultGroup/skipCount/text()", "0" );



        } finally {
            System.out.println("Here's the log: " + log);
        }
    }


    /**
     * Working on a test for a bug where labeled test groups publisher: inter-build diffs for groups are wrong 
     * @throws Exception
     */
    @LocalData
    public void SKIP_testDiffs() throws Exception {
        String log = "(no log collected)";
        try {
            // Load in a project with known counts
            FreeStyleProject project = setupProject("knowncount");
            assertNotNull(project);
            int numBuildsToRun = 4;
            // Run and validate a traditional build
            FreeStyleBuild build = null;

            for (int i = 0; i < numBuildsToRun; i++) {
                build = project.scheduleBuild2(0).get(20, TimeUnit.SECONDS);
                log = getLog(build);
                assertBuildStatus(Result.UNSTABLE, build);
            }
            HudsonTestCase.WebClient wc = new HudsonTestCase.WebClient();

            // First check that for the ones we just ran, it always says (1 failure / ±0) on the build summary page

            HtmlPage projectPage = wc.goTo(project.getUrl());
            String pageAsText = projectPage.asXml();
            // System.out.println(pageAsText);

            assertXPathResultsContainText( projectPage, "//td[@id='main-panel']", "Test Result");
            assertXPathValueContains( projectPage, "//td[@id='main-panel']", "(1 failure ") ;
            assertXPathValueContains( projectPage, "//td[@id='main-panel']", "0)");
            // Ugh. The +/- character behaves differently on the mac than on unix.
            // This test passes on the mac, fails on unix, so, sadly, I'm going to
            // do some obnoxious workaround.
            String plusOrMinusSymbol = " ±";


            String xPathToMainPanel = "//td[@id='main-panel']";
            new CountingTest.ExpectedValues(projectPage, "Test Result", xPathToMainPanel).runCheck();
            new CountingTest.ExpectedValues(projectPage, "(1 failure", xPathToMainPanel).runCheck();
            new CountingTest.ExpectedValues(projectPage,  "0)", xPathToMainPanel).runCheck();

            // TODO: write test: Diffs are wrong for labeled test groups:
            // on  http://localhost:8080/job/knowncount/6/testReport/ each of unit, regression, smoke
            // list +/- as n (+/- n) when in fact it should be n +/- 0

        } finally {
            System.out.println("Here's the log: " + log);
        }
    }

    class ExpectedValues {
        private final String xpath;

        private DomNode page;
        private final String needle;
        private final String msg;

        public ExpectedValues( String msg, DomNode page, String needle, String xpath) {
            this.needle = needle;
            this.page = page;
            this.xpath = xpath;
            this.msg = msg;
        }

        public ExpectedValues(DomNode page, String needle, String xpath) {
            this("(no message)", page, needle, xpath);
        }

        public void runCheck() {
            assertXPathValueContains(page, xpath, needle);
        }

    }

}

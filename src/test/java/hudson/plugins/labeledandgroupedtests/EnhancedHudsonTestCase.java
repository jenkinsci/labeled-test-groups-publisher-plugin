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

import hudson.tasks.test.TestResult;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.TouchBuilder;
import org.xml.sax.SAXException;
import hudson.model.FreeStyleProject;
import hudson.model.Project;
import hudson.model.Hudson;
import hudson.slaves.DumbSlave;
import hudson.FilePath;
import hudson.tasks.test.TabulatedResult;

import java.util.List;
import java.io.IOException;

import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.xml.XmlPage;

/**
 * User: Benjamin Shine bshine
 * Date: Nov 9, 2009
 * Time: 3:58:22 PM
 */
public class EnhancedHudsonTestCase extends HudsonTestCase {
    protected FreeStyleProject setupProject(String recipeProjectName) throws Exception {
        List<Project> projects = Hudson.getInstance().getProjects();
        Project project = null;
        for (Project p : projects) {
            if (p.getName().equalsIgnoreCase(recipeProjectName)) {
                project = p;
                break;
            }
        }
        assertNotNull("We should be able to load the " + recipeProjectName + " project", project);
        assertTrue("it's a freestyle project", project instanceof FreeStyleProject);
        FreeStyleProject freestyleProj = (FreeStyleProject) project;
        freestyleProj.getBuildersList().add(new TouchBuilder());

        return freestyleProj;
    }

    protected void setupRemoteData(String projectName, DumbSlave s, FreeStyleProject project, String fileMask) throws Exception {
        FilePath src = new FilePath(hudson.getRootPath(), "jobs/" + projectName + "/workspace/");
        assertNotNull(src);
        FilePath dest = s.getWorkspaceFor(project);
        assertNotNull(dest);
        src.copyRecursiveTo(fileMask, dest);

        // Force deletion of the local data to ensure we're not parsing that
        // accidentally
        src.deleteContents();
    }

    public void assertMatchingResults(String msg, TestResult r, TestResult s) {
        assertNotNull(msg + ": r not null", r);
        assertNotNull(msg + ": s not null", s);
        assertTrue( msg + ": same class", r.getClass().equals(s.getClass()));
        assertEquals(msg + ": pass count", r.getPassCount(), s.getPassCount());
        assertEquals(msg + ": fail count", r.getFailCount(), s.getFailCount());

        assertTrue("Should be TabulatedResult instance", r instanceof TabulatedResult);
        assertTrue("Should be TabulatedResult instance", s instanceof TabulatedResult);
        
        assertEquals(msg + ": number of children", ((TabulatedResult)r).getChildren().size(), ((TabulatedResult)s).getChildren().size());
    }

    public void assertGoodHttpStatus(String msg, String url, WebClient wc) throws IOException, SAXException {
        try {
            Page page = wc.goTo(url);
            assertTrue(msg + ": good http status for " + url, isGoodHttpStatus(page.getWebResponse().getStatusCode()));
        } catch (FailingHttpStatusCodeException e) {
            fail(msg + ": failing http status exception: " + url);
        }
    }

    public void assertGoodHttpStatusForXmlContent(String msg, String url, WebClient wc) throws IOException, SAXException {
        try {
            XmlPage page = wc.goToXml(url);
            assertTrue(msg + ": good http status for xml " + url, isGoodHttpStatus(page.getWebResponse().getStatusCode()));
         } catch (FailingHttpStatusCodeException e) {
            fail(msg + ": failing http status exception " + url);
        }
    }

    public void assertGoodHttpStatusForPngContent(String msg, String url, WebClient wc) {
        try {
            Page page = wc.goTo(url, "image/png");
            assertTrue(msg, isGoodHttpStatus(page.getWebResponse().getStatusCode()));
        } catch (IOException e) {
            fail(msg + " io exception: " + url);
        } catch (SAXException e) {
            fail(msg + " SAXException: " + url);            
        } catch (FailingHttpStatusCodeException e) {
            fail(msg + ": failing http status exception " + url);                        
        }
    }

    public void assertGoodHttpStatusForHtmlAndXmlApi(String msg, String url, WebClient wc) throws IOException, SAXException {
        assertGoodHttpStatus(msg, url, wc);        
        assertGoodHttpStatusForXmlContent(msg, url + "/api/xml", wc);
    }

    public void testTrivial() {
        assertTrue(true); // so surefire won't complain about not having any tests in this class. 
    }

    public void checkUrls(String msg, WebClient wc, List<String> urlsToCheck, List<String> pngUrlsToCheck) throws IOException, SAXException {
        for (String s : urlsToCheck) {
            assertGoodHttpStatusForHtmlAndXmlApi(msg, s, wc);
        }
        for (String s: pngUrlsToCheck) {
            assertGoodHttpStatusForPngContent(msg, s, wc);
        }
    }
}

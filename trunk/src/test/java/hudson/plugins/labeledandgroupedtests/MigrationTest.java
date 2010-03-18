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

import hudson.tasks.test.TestResult;
import hudson.model.*;
import hudson.plugins.labeledgroupedtests.*;
import hudson.plugins.labeledgroupedtests.converters.JUnitResultArchiverConverter;
import org.jvnet.hudson.test.recipes.LocalData;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.io.IOException;

/**
 * User: Benjamin Shine bshine
 * Date: Nov 9, 2009
 * Time: 3:34:40 PM
 */
public class MigrationTest extends EnhancedHudsonTestCase {



    /**
     * Test case where we load in a JUnitResultArchiver config, it automatically gets
     * convereted to a new-style config.
     */
    @LocalData
    public void testAutomatedJUnitMigration() throws Exception {

        assertTrue(JUnitResultArchiverConverter.ENABLE_CONVERSIONS);
        // Load in a project with legacy data
        FreeStyleProject project = setupProject("corepublisher");
        assertNotNull(project);

        // If this is all working, we should have a LabeledTestResultPublisher on here, not an old-school publisher.
        LabeledTestResultGroupPublisher publisher = project.getPublishersList().get(LabeledTestResultGroupPublisher.class);
        assertNotNull("we found our new cool publisher", publisher);
        checkConfigsForJunit(publisher.getConfigs(), "TESTING_JUNIT_FILE_MASK");

    }

    private void checkConfigsForJunit(List<LabeledTestGroupConfiguration> configs,  String expectedJUnitFileMask) {
        // One should be Junit
        boolean foundJunit = false;
        String junitFilemask = "";
        for (LabeledTestGroupConfiguration c : configs) {
            if (c.getParserClassName().equals("hudson.tasks.junit.JUnitParser")) {
                foundJunit = true;
                junitFilemask = c.getTestResultFileMask();
            }
        }

        assertTrue("found junit config" , foundJunit);
        assertEquals("junit file mask matches", expectedJUnitFileMask, junitFilemask );
    }
}

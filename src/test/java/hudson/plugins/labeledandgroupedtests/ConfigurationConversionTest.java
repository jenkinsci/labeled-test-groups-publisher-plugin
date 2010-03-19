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

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.ConversionException;
import hudson.XmlFile;
import hudson.model.AbstractProject;
import hudson.model.FreeStyleProject;
import hudson.plugins.labeledgroupedtests.LabeledTestResultGroupPublisher;
import hudson.plugins.labeledgroupedtests.LabeledTestGroupConfiguration;
import hudson.plugins.labeledgroupedtests.LabeledTestGroupsPublisherPlugin;
import hudson.tasks.Publisher;
import hudson.util.StringConverter2;
import hudson.util.XStream2;
import hudson.util.IOException2;
import junit.framework.TestCase;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;



/**
 * This class experiments with using various Xstream strategies
 * for migratingpublisher configurations.
 * @author bshine
 * Date: Nov 13, 2009
 * Time: 1:37:03 PM
 */
public class ConfigurationConversionTest extends TestCase {

    private XStream XSTREAM = new XStream2();
    
    private void registerConverters() {
        XSTREAM.alias("project",FreeStyleProject.class);
        LabeledTestGroupsPublisherPlugin.registerWithXStream(XSTREAM);
    }

    private File getDataFile(String name) throws URISyntaxException {
        String containingDirectory = "ConfigurationConversionTest/";
        return new File(ConfigurationConversionTest.class.getResource(containingDirectory + name).toURI());
    }



    /** This test verifies that we can load in a JUnitResultArchiver configuration
     * and create a matching LabeledTestGroups publisher config.
     * @throws Exception
     */
    public void testConvertFromJUnitResultArchiver() throws Exception {
        registerConverters();
        XmlFile xmlFile = new XmlFile(XSTREAM, getDataFile("junit-archiver-config.xml"));
        LabeledTestResultGroupPublisher publisher = (LabeledTestResultGroupPublisher) xmlFile.read();
        assertNotNull("we found our new cool publisher", publisher);
        checkConfigsForCppAndJunit(publisher.getConfigs(), null, "TESTING_JUNIT_FILE_MASK");
    }


    /** Make sure that we throw an error if we try to convert a JUnitResultArchiver
     * that has any testDataPublishers
     * and create a matching LabeledTestGroups publisher config.
     * @throws Exception
     */
    public void testConvertWithTestDataPublishers() throws Exception {
        registerConverters();
        XmlFile xmlFile = new XmlFile(XSTREAM, getDataFile("junit-archiver-with-testdatapublisher.xml"));
        try {
            LabeledTestResultGroupPublisher publisher = (LabeledTestResultGroupPublisher) xmlFile.read();
            fail("exception not encountered!");
        } catch (IOException2 e) {
            if (!ConversionException.class.equals(e.getCause().getClass())) {
                fail("wrong cause"); 
            }
        }
    }



 
    private void checkConfigsForCppAndJunit(List<LabeledTestGroupConfiguration> configs, String expectedCppFileMask, String expectedJUnitFileMask) {
        // One of the parsers should be cpp
        // One should be Junit
        boolean foundCpp = false;
        boolean foundJunit = false;
        String cppFilemask = "";
        String junitFilemask = "";
        for (LabeledTestGroupConfiguration c : configs) {
            if (c.getParserClassName().equals("hudson.plugins.cppunitparser.CPPUnitTestResultParser")) {
                foundCpp = true;
                cppFilemask = c.getTestResultFileMask();
            } else if (c.getParserClassName().equals("hudson.tasks.junit.JUnitParser")) {
                foundJunit = true;
                junitFilemask = c.getTestResultFileMask();
            }
        }

        if (expectedJUnitFileMask != null) {
            assertTrue("found junit config" , foundJunit);
            assertEquals("junit file mask matches", expectedJUnitFileMask, junitFilemask );
        }
        if (expectedCppFileMask != null) {
            assertTrue("found cpp config", foundCpp);
            assertEquals("cpp file mask matches", expectedCppFileMask, cppFilemask );
        }
    }
}

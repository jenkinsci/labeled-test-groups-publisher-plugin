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
import hudson.model.Items;
import hudson.tasks.junit.JUnitResultArchiver;
import hudson.tasks.junit.TestDataPublisher;
import org.jvnet.hudson.test.HudsonTestCase;

import java.util.List;


/**
 * Exercise to make sure that we can save
 * User: Benjamin Shine bshine
 * Date: Nov 16, 2009
 * Time: 11:03:17 AM
 */
public class AntiDestructionTest extends HudsonTestCase {
    private static final String JUNIT_MASK = "**/*.xml";
 
   public void testSavingJUnitConfiguration() throws Exception {

        // This demonstrates that we can save an old-style converter
        JUnitResultArchiver archiver = new JUnitResultArchiver(JUNIT_MASK);
        String archiverAsString = Items.XSTREAM.toXML(archiver);
        System.out.println("We converted it: " + archiverAsString );

        assertTrue( archiverAsString.contains( "hudson.tasks.junit.JUnitResultArchiver") );
        assertTrue( archiverAsString.contains( "<testResults>" + JUNIT_MASK + "</testResults>" ));
        assertTrue( archiverAsString.contains( "<testDataPublishers/>") );

        // Now, reconstitute the old archiver from the XML we just saved,
        // and verify the structure is exactly what we expect
        Object reloaded = new XStream().fromXML(archiverAsString);
        assertTrue("got a JUnitResultArchiver back", reloaded instanceof JUnitResultArchiver);

        JUnitResultArchiver reloadedArchiver = (JUnitResultArchiver) reloaded;
        assertEquals(JUNIT_MASK, reloadedArchiver.getTestResults());


        boolean emptyTDPs = false;
        List<? extends TestDataPublisher> testDataPublishers = reloadedArchiver.getTestDataPublishers();
        try {
            if (testDataPublishers==null) {
                emptyTDPs = true;
            } else if (testDataPublishers.size() == 0) {
                emptyTDPs = true;
            }
        } catch (NullPointerException e) {
            // that's okay
            emptyTDPs = true;
        }

        assertTrue("we should not have any TestDataPublishers in the reloaded thing",
                emptyTDPs);
    }

}

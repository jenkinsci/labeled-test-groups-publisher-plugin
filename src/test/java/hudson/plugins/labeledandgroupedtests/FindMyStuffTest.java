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

import hudson.tasks.test.TestResultParser;
import hudson.ExtensionList;
import hudson.model.Hudson;


/**
 * User: Benjamin Shine bshine
 * Date: Oct 20, 2009
 * Time: 7:09:26 PM
 */
public class FindMyStuffTest extends HudsonTestCase {

    public void testFindingParsers() {
        ExtensionList<TestResultParser> parserPlugins = Hudson.getInstance().getExtensionList(TestResultParser.class);
        printList(TestResultParser.class.getName(), parserPlugins);
        assertTrue("We should have at least one extension of TestResultParser == the junit test result parser.", parserPlugins.size() > 0);
    }

     public <T> void printList(String label, ExtensionList<T> list) {

        System.out.println(label + " extensions: ");
        for (T t : list) {
            System.out.println(" --> " + t.getClass().getName());
        }

        System.out.println();

    }
}

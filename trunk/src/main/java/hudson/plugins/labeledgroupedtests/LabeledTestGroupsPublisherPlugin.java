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

import hudson.Plugin;
import hudson.plugins.labeledgroupedtests.converters.*;
import hudson.model.Items;
import hudson.model.Run;
import com.thoughtworks.xstream.XStream;

/**
 * User: Benjamin Shine bshine
 * Date: Nov 14, 2009
 * Time: 3:51:00 PM
 */
public class LabeledTestGroupsPublisherPlugin extends Plugin {

    public static void registerWithXStream(XStream xs) {

        // Register a converter for converting old archivers into the labeled test groups publisher
        JUnitResultArchiverConverter.registerWithXStream(xs);
        OpenCppunitPublisherConverter.registerWithXStream(xs);

        // Set up some aliases for legacy class names
        xs.alias("hudson.plugins.labeledgroupedtests.LabeledGroupedTestArchiver", LabeledTestResultGroupPublisher.class);
        xs.alias("hudson.plugins.labeledgroupedtests.LabeledGroupedTestResultProjectAction", MetaLabeledTestResultGroupProjectAction.class);
        xs.alias("hudson.plugins.labeledgroupedtests.LabeledGroupedTestResultAction", MetaLabeledTestResultGroupAction.class); 
    }

    public void start() throws Exception {
        // The Run xstream instance loads build results
        registerWithXStream(Run.XSTREAM);

        // The Items xstream instance loads job configurations
        registerWithXStream(Items.XSTREAM);

    }
}

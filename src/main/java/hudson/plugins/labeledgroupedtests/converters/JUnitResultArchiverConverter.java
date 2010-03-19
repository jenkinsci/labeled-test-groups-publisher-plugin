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
package hudson.plugins.labeledgroupedtests.converters;

import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.ConversionException;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.XStream;
import hudson.tasks.junit.JUnitResultArchiver;
import hudson.plugins.labeledgroupedtests.LabeledTestGroupConfiguration;
import hudson.plugins.labeledgroupedtests.LabeledTestResultGroupPublisher;

import java.util.List;
import java.util.ArrayList;

/**
 * User: Benjamin Shine bshine
 * Date: Nov 16, 2009
 * Time: 1:49:19 PM
 */
public class JUnitResultArchiverConverter extends ConvertToLabeledGroupsConverter {

    /**
     * Write a JUnitResultArchiver out as a JUnitResultArchiver archiver --
     * not really the behavior you'd expect from a converter, but this particular
     * converter only wants to run the conversion when a JUnitResultArchiver
     * is unmarshaled.
     * This lets users continue to edit configurations that include
     * JUnitResultArchivers when this plugin is installed.
     *
     * @param source  The object to be marshalled.
     * @param writer  A stream to write to.
     * @param context A context that allows nested objects to be processed by XStream.
     */
    public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
        if (!ENABLE_CONVERSIONS) {
            LOGGER.severe("This converter should not be called to convert anything when conversion is disabled.");
            throw new IllegalStateException("conversion is disabled");
        }
        if (!(source instanceof JUnitResultArchiver)) {
            throw new ConversionException("JUnitResultArchiverConverter can't marshal objects " +
                    "that aren't JUnitResultArchiver instances");
        }

        JUnitResultArchiver archiver = (JUnitResultArchiver) source;

        writer.startNode("testResults");
        writer.setValue(archiver.getTestResults());
        writer.endNode();
        writer.startNode("testDataPublishers");
        writer.endNode();     
    }

    /**
     * Convert textual data back into an object.
     *
     * @param reader  The stream to read the text from.
     * @param context
     * @return The resulting object.
     */
    public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
        if (!ENABLE_CONVERSIONS) {
            LOGGER.info("Not converting to LabeledTestResultGroupPublisher because this conversion is disabled");
            throw new IllegalStateException("We shouldn't be able to get into this method when conversions are disabled");
        }

        List<LabeledTestGroupConfiguration> configs = new ArrayList<LabeledTestGroupConfiguration>();

        while (reader.hasMoreChildren()) {
            reader.moveDown();
            if ("testResults".equals(reader.getNodeName())) {
                configs.add( new LabeledTestGroupConfiguration(
                        JUNIT_PARSER_TO_USE, reader.getValue(), "unit"));
            } else if ("testDataPublishers".equals(reader.getNodeName())) {
                // We can only do this conversion if there aren't any test data publishers,
                // because the LabeledTestResultGroupPublisher doesn't know what to do with
                // TestDataPublishers. 
                if (reader.hasMoreChildren()) {
                    String msg =  "Encountered a configuration of JUnitResultArchiver that this converter does not know how to deal with. Aborting conversion.";
                    msg = msg + ". Node name is '" + reader.getNodeName() + "'";
                    LOGGER.severe( msg );
                    throw new ConversionException("This converter cannot convert testDataPublishers");
                }
            }
            reader.moveUp();
        }
        return new LabeledTestResultGroupPublisher(configs);
    }

    /**
     * Determines whether the converter can marshall a particular type.
     *
     * @param type the Class representing the object type to be converted
     */
    public boolean canConvert(Class type) {
        return ENABLE_CONVERSIONS && JUnitResultArchiver.class == type;
    }


    public static void registerWithXStream(XStream xstream) {
        xstream.registerConverter(new JUnitResultArchiverConverter());
    }
}

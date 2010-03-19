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

import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.XStream;

import java.util.Map;
import java.util.HashMap;
import java.util.logging.Logger;

import hudson.tasks.junit.JUnitParser;

/**
 * User: Benjamin Shine bshine
 * Date: Nov 16, 2009
 * Time: 1:50:23 PM
 */
public abstract class ConvertToLabeledGroupsConverter implements Converter {
    /**
     * When this is false, the converter will refuse to convert anything.
     * It is marked final because its state really only matters while Hudson is loading.
     * It cannot effectively be changed except at compile time. Does that make it useless?
     * Well, at least it's here.
     */
    public static final boolean ENABLE_CONVERSIONS = true;
    static Map<String, String> PARSER_MAP = new HashMap<String, String>(5);
    static Map<String, String> HISTORICAL_DATA_MAP = new HashMap<String, String>(3);
    public static final String JUNIT_PARSER_TO_USE = JUnitParser.class.getName();

    public static final String OPEN_CPPUNIT_PLUGIN_CLASS_NAME = "hudson.plugins.cppunit.CppUnitPublisher";

    // We're using reflection because we're not sure whether the old, open plugin will be present.
    public static final String CPPUNIT_PARSER_TO_USE = "hudson.plugins.cppunitparser.CPPUnitTestResultParser";
    

    protected static final Logger LOGGER = Logger.getLogger(ConvertToLabeledGroupsConverter.class.getName());

    static {        
        PARSER_MAP.put(OPEN_CPPUNIT_PLUGIN_CLASS_NAME, CPPUNIT_PARSER_TO_USE);

     
    }


    public static void registerWithXStream(XStream xstream) {
       // subclasses should implement 
    }

}

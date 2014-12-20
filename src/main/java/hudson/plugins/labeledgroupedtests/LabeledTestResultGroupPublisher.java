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


import hudson.Extension;
import hudson.Launcher;
import hudson.matrix.MatrixAggregatable;
import hudson.matrix.MatrixAggregator;
import hudson.matrix.MatrixBuild;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.tasks.test.TestResult;
import hudson.tasks.test.TestResultParser;
import hudson.tasks.test.TestResultAggregator;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.logging.Logger;

public class LabeledTestResultGroupPublisher extends Recorder implements Serializable, MatrixAggregatable {
    private static final Logger LOGGER = Logger.getLogger(LabeledTestResultGroupPublisher.class.getName());
    protected List<LabeledTestGroupConfiguration> configs;
    private static List<TestResultParser> testResultParsers = null;
    
    @DataBoundConstructor
    public LabeledTestResultGroupPublisher(List<LabeledTestGroupConfiguration> configs) {
        if (configs == null || configs.size() == 0) {
            throw new IllegalArgumentException("Null or empty list of configs passed in to LabeledTestResultGroupPublisher. Please file a bug.");
        }
        this.configs = new ArrayList<LabeledTestGroupConfiguration>(configs);
        discoverParsers();
    }

    /**
     * Declares the scope of the synchronization monitor this {@link hudson.tasks.BuildStep} expects from outside.
     */
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.BUILD;
    }

    public MatrixAggregator createAggregator(MatrixBuild build, Launcher launcher, BuildListener listener) {
        return new TestResultAggregator(build, launcher, listener);
    }

    public static void discoverParsers() {
        if (testResultParsers == null) {
            if (Hudson.getInstance()==null) {
                testResultParsers = new ArrayList<TestResultParser>();
                return; // If we're not in a Hudson world, we won't have parsers.
            }
            testResultParsers = Hudson.getInstance().getExtensionList(TestResultParser.class);
        }
    }

    public static List<TestResultParser> getTestResultParsers() {
        discoverParsers();
        return testResultParsers;
    }

    public void debugPrint() {
        for (LabeledTestGroupConfiguration config: configs) {
            LOGGER.info("got config: " + config.toString());
        }

        for (TestResultParser parser: testResultParsers) {
            LOGGER.info("we have test result parser: " + parser.getClass().getName());
        }
    }

    public List<LabeledTestGroupConfiguration> getConfigs() {
        return configs;
    }

    public void setConfigs(List<LabeledTestGroupConfiguration> configs) {
        this.configs = configs;
    }

    @Override
    public boolean perform(final AbstractBuild build, Launcher launcher, final BuildListener listener)
            throws InterruptedException, IOException {
        String startMsg = "Analyzing test results with LabeledTestResultGroupPublisher...";
        listener.getLogger().println(startMsg);
        LOGGER.fine(startMsg);

        // Prepare to process results
        final long buildTime = build.getTimestamp().getTimeInMillis();
        final long nowMaster = System.currentTimeMillis();

        HashSet<String> labels = new HashSet<String>(10);
        HashMap<String, List<TestResult>> resultGroupsByLabel = new HashMap(3);
        HashMap<TestResult, String> resultsWithName = new  HashMap<TestResult, String>(configs.size());

        // Roll up configs so that there is zero or one config for each label/parser pair
        rollupConfigs(); 

        // For each TestResults configuration, attempt to parse its results
        //      Invoke the parser on the specified results
        //      Label those results as the specified type (unit/smoke/regression)
        //      Include those results in an aggregrated result
        for (LabeledTestGroupConfiguration config:configs) {
            try {
                String parserClassName = config.getParserClassName();
                String label = config.getLabel();
                labels.add(label); // adds only if not already there
                if (!resultGroupsByLabel.containsKey(label)) {
                    // Make a new, empty list for that label 
                    resultGroupsByLabel.put(label, new ArrayList<TestResult>(5));
                }
                Collection<TestResult> listForThisLabel = resultGroupsByLabel.get(label);

                ClassLoader uberLoader = Hudson.getInstance().getPluginManager().uberClassLoader;
                Class parserClass = Class.forName(parserClassName, true, uberLoader);
                Object parserObject = parserClass.newInstance();
                String nameForThisResult = config.toNameString();

                TestResult someResult = null;

                // Actually parse the file!
                // NB: we're calling a static method via an instance, because I can't figure out
                // how to go from the Class object to calling a static method without an instance involved.
                if (parserObject instanceof TestResultParser) {
                    TestResultParser parser = (TestResultParser) parserObject;
                    someResult = parser.parse(config.getTestResultFileMask(), build, launcher, listener);
                } else {
                    LOGGER.warning("Couldn't find a parser for class: " + parserClassName);
                    listener.getLogger().println("Couldn't find a parser for class: " + parserClassName);
                    continue;
                }

                if (someResult != null) {
                    listForThisLabel.add(someResult);
                    resultsWithName.put(someResult, nameForThisResult);
                    String msg = "Here's your result: " + someResult.toPrettyString();
                    listener.getLogger().println(msg);
                    LOGGER.fine(msg);

                } else {
                    String msg =  "Trouble while parsing results for " + config.getTestResultFileMask() + "-- couldn't parse results.";
                    LOGGER.warning(msg);
                    listener.getLogger().println(msg);
                }

            } catch (IOException e) {
                LOGGER.warning("While processing config " + config.toString() + ":" + e.getMessage());
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                LOGGER.warning("Couldn't find parser while processing config " + config.toString() + ":" + e.getMessage());
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                LOGGER.warning("Couldn't get an instance of parser while processing config " + config.toString() + ":" + e.getMessage());
                e.printStackTrace();
            } catch (InstantiationException e) {
                LOGGER.warning("Couldn't get an instance of parser while processing config " + config.toString() + ":" + e.getMessage());
                e.printStackTrace();
            }
        }


        // Create and populate the result that will contain all the children we parsed
        MetaLabeledTestResultGroup resultGroup = new MetaLabeledTestResultGroup();
        for (String label: labels) {            
            LabeledTestResultGroup group = new LabeledTestResultGroup(resultGroup, label, resultGroupsByLabel.get(label));
            resultGroup.addTestResultGroup(label, group);
            group.setNameMap(resultsWithName);
        }

        MetaLabeledTestResultGroupAction action = new MetaLabeledTestResultGroupAction(build, resultGroup, listener);
        build.addAction(action);
        resultGroup.setParentAction(action);
        
        resultGroup.tally();

        Result healthResult = determineBuildHealth(build, resultGroup);
        // Parsers can only decide to make the build worse than it currently is, never better.
        if (healthResult != null && healthResult.isWorseThan(build.getResult())) {
            build.setResult(healthResult);
        }

        String debugString = resultGroup.toString(); // resultGroup.toPrettyString();
        LOGGER.info("Test results parsed: " + debugString);
        listener.getLogger().println("Test results parsed: " + debugString); 


        return true;
    }

    /**
     * Roll up configs so that there is zero or one config for each label/parser pair
     */
    private void rollupConfigs() {
        // Build a unique list of labels and a unique list of parsers
        HashSet<String> parserNames = new HashSet<String>();
        HashSet<String> labelsInUse = new HashSet<String>();
        for (LabeledTestGroupConfiguration config:configs) {
            parserNames.add(config.getParserClassName());
            labelsInUse.add(config.getLabel());
        }
        
        // Make one empty list of string for each label/parser pair,
        // and simultaneously flatten into a new, smaller list of configs
        List<LabeledTestGroupConfiguration> newConfigs = new ArrayList<LabeledTestGroupConfiguration>();
        for (String label : labelsInUse) {
            for (String parserName : parserNames) {
                StringBuilder filemaskBuilder = new StringBuilder();

                // Now, go through all of the configs searching for this label/parser combination
                for (LabeledTestGroupConfiguration config:configs) {
                    if (config.getParserClassName().equals(parserName) &&
                            config.getLabel().equals(label)) {
                        if (filemaskBuilder.length() > 0) {
                            filemaskBuilder.append(",");
                        }
                        filemaskBuilder.append(config.getTestResultFileMask());
                    }
                }
                // At this point we have a complete list of the file masks for this label/parser combination.
                // Make a new config representing that unified filemask!
                String combinedFilemask = filemaskBuilder.toString();
                if (combinedFilemask.length() > 0) { // only build a new config if there is some content in the filemask string
                    LabeledTestGroupConfiguration newConfig =
                            new LabeledTestGroupConfiguration(parserName, filemaskBuilder.toString(), label);
                    newConfigs.add(newConfig);
                }
            }
        }

        // Now replace the old set of configs with the new, smaller one we just built. 
        this.configs = newConfigs; 
    }


    private Result determineBuildHealth(AbstractBuild build,  MetaLabeledTestResultGroup resultGroup) {
        // Set build health on the basis of all configured test report groups
        Result worstSoFar = build.getResult();

         for (TestResult result : resultGroup.getChildren()) {
             Result thisResult = result.getBuildResult();
             if (thisResult != null && thisResult.isWorseThan(worstSoFar)) {
                 worstSoFar = result.getBuildResult();
             }
         }

         return worstSoFar;
    }

	@Override
	public Action getProjectAction(AbstractProject<?, ?> project) {
		return new MetaLabeledTestResultGroupProjectAction(project);
	}

    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        public String getDisplayName() {
            return "Publish Test Results in Labeled Groups";
        }

        @Override
        public Publisher newInstance(StaplerRequest req, JSONObject formData)
                throws hudson.model.Descriptor.FormException {
            LOGGER.info(formData.toString());
            return req.bindJSON(LabeledTestResultGroupPublisher.class, formData);
        }

        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            // for Maven we have SurefireArchiver that automatically kicks in.
            return !"AbstractMavenProject".equals(jobType.getClass().getSimpleName());
        }
    }
}

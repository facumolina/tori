package org.tori;

import org.apache.commons.cli.*;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;

import org.tori.utils.ReportStyle;
import org.tori.metrics.Metric;

import org.tori.metrics.StateFieldCoverage;
import java.lang.ClassNotFoundException;

public class Main {
    
    private static final String VERSION = "1.0.0";

    /**
     * Builds the command-line options for the application.
     *
     * @return the configured Options object
     */
    private static Options buildOptions() {
        Options options = new Options();
        
        options.addOption(Option.builder("t")
                .longOpt("test-file")
                .hasArg()
                .required()
                .desc("Path to the test file (required)")
                .argName("FILE")
                .build());
        
        options.addOption(Option.builder("m")
                .longOpt("test-method")
                .hasArg()
                .required(false)
                .desc("Name of the specific test method to analyze (optional)")
                .argName("METHOD")
                .build());
        
        options.addOption(Option.builder("metric")
                .longOpt("metric")
                .hasArg()
                .required(false)
                .desc("Class name of the metric to use for oracle assessment (optional)")
                .argName("METRIC_CLASS")
                .build());
        
        options.addOption(Option.builder("mc")
                .longOpt("metric-config")
                .hasArg()
                .required(false)
                .desc("Path to the properties file with metric configuration (optional)")
                .argName("CONFIG_FILE")
                .build());
        
        return options;
    }

    public static void main(String[] args) {
        // Print banner
        printBanner();
        
        // Define command-line options
        Options options = buildOptions();

        // Parse command-line arguments
        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd = null;

        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            System.err.println("Error: " + e.getMessage());
            System.err.println();
            formatter.printHelp("tori", options);
            System.exit(1);
        }

        String testFilePath = cmd.getOptionValue("t");
        String testMethodName = cmd.getOptionValue("m");
        String metricClassName = cmd.getOptionValue("metric");
        String metricConfigPath = cmd.getOptionValue("metric-config");

        // Print execution parameters
        printExecutionParameters(testFilePath, testMethodName, metricClassName, metricConfigPath);

        try {
            // Read the test file
            Path path = Paths.get(testFilePath);
            if (!Files.exists(path)) {
                System.err.println("Error: File not found: " + testFilePath);
                System.exit(1);
            }

            String sourceCode = Files.readString(path);

            // Parse and find target oracles
            TestOracleInspector inspector = new TestOracleInspector();
            List<MethodOracles> targetClassOracles = inspector.findOracles(sourceCode, testMethodName);

            // Load metric if specified
            Metric metric = null;
            if (metricClassName != null) {
                metric = loadMetric(metricClassName, metricConfigPath, testFilePath, testMethodName);
            } else {
                System.out.println(ReportStyle.boldYellowWarning("No metric specified. Priting oracles without assessment."));
                System.out.println();
                System.out.println(ReportStyle.boldWhite("Test methods: " + targetClassOracles.size()));
                System.out.println(ReportStyle.boldWhite("Total assertions: " + targetClassOracles.stream().mapToInt(m -> m.oracles().size()).sum()));
                System.out.println();
            }

            // Print results
            if (targetClassOracles.isEmpty()) {
                if (testMethodName != null) {
                    System.out.println("No test method named '" + testMethodName + "' found.");
                } else {
                    System.out.println("No test methods with assertions found.");
                }
            } else {
                // Check execution level and print accordingly
                if (metric != null && metric.getExecutionLevel() == org.tori.metrics.ExecutionLevel.TEST_CLASS) {
                    // TEST_CLASS level: compute metric for all assertions in all methods
                    java.util.List<String> allOracles = new java.util.ArrayList<>();
                    StringBuilder allTestCases = new StringBuilder();
                    
                    for (MethodOracles methodOracles : targetClassOracles) {
                        allOracles.addAll(methodOracles.oracles());
                        allTestCases.append(methodOracles.testCaseSource()).append("\n");
                    }
                    
                    if (!allOracles.isEmpty()) {
                        double score = metric.assessMultiple(allTestCases.toString(), allOracles);
                        System.out.println("Test Class Assessment:");
                        metric.reportTestClassLevel(score, allOracles);
                    }
                    System.out.println();
                } else if (metric != null && metric.getExecutionLevel() == org.tori.metrics.ExecutionLevel.TEST_METHOD) {
                    // TEST_METHOD level: compute metric per method for all assertions in that method
                    for (MethodOracles methodOracles : targetClassOracles) {
                        System.out.println(ReportStyle.boldWhite("Test Method: " + methodOracles.methodName()));
                        if (methodOracles.oracles().isEmpty()) {
                            System.out.println("  No assertions found");
                        } else {
                            double score = metric.assessMultiple(methodOracles.testCaseSource(), methodOracles.oracles());
                            metric.reportTestMethodLevel(score, methodOracles);
                        }
                        System.out.println();
                    }
                } else {
                    // ASSERT level (default): compute metric per assertion
                    for (MethodOracles methodOracles : targetClassOracles) {
                        System.out.println(ReportStyle.boldWhite("Test Method: " + methodOracles.methodName()));
                        if (methodOracles.oracles().isEmpty()) {
                            System.out.println("  No assertions found");
                        } else {
                            for (String oracle : methodOracles.oracles()) {
                                System.out.println();
                                if (metric != null) {
                                    double score = metric.assess(methodOracles.testCaseSource(), oracle);
                                    metric.reportAssertLevel(score, oracle);
                                } else {
                                    System.out.println("  - " + oracle);
                                }
                            }
                        }
                        System.out.println();
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading file: " + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Error parsing file: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Helper method to map metric class names to actual Class objects, 
     * with support for language-specific variants.
     */
    private static Class<? extends StateFieldCoverage> getMetricClass(String metricName, String testFilePath) throws ClassNotFoundException {
        // For simplicity, we only support StateFieldCoverage for now
        if ("org.tori.metrics.StateFieldCoverage".equals(metricName)) {
            if (testFilePath != null && testFilePath.endsWith(".java")) {
                return org.tori.metrics.sfc.StateFieldCoverageJava.class;
            }
            throw new IllegalArgumentException("Unsupported language for StateFieldCoverage metric. Currently only Java (.java) files are supported.");
        }
        throw new ClassNotFoundException("Metric class not found: " + metricName);
    }

    /**
     * Load a metric instance based on its class name and configuration.
     * This method handles class loading, instantiation, and configuration of the metric.
     * 
     * @param metricClassName the fully qualified class name of the metric to load
     * @param metricConfigPath the path to the metric configuration file (optional)
     * @param testFilePath the path to the test file being analyzed (used for language
     * @param testMethodName the name of the specific test method to analyze (optional)
     */
    private static Metric loadMetric(String metricClassName, String metricConfigPath, String testFilePath, String testMethodName) throws Exception {
        try {
            Class<?> metricClass = getMetricClass(metricClassName, testFilePath);
            Metric metric = (Metric) metricClass.getDeclaredConstructor().newInstance();
            
            // Load metric configuration if provided
            if (metricConfigPath != null) {
                Path configPath = Paths.get(metricConfigPath);
                if (!Files.exists(configPath)) {
                    System.err.println("Error: Metric config file not found: " + metricConfigPath);
                    System.exit(1);
                }
                
                Properties config = new Properties();
                try (FileInputStream fis = new FileInputStream(configPath.toFile())) {
                    config.load(fis);
                }
                metric.configure(config);
                
                // Print Metric Configuration section
                metric.printConfigurationParams();
                System.out.println();
                        
                // Validate execution level constraints
                if (metric.getExecutionLevel() == org.tori.metrics.ExecutionLevel.TEST_CLASS && testMethodName != null) {
                    System.err.println("Error: exec_level 'test_class' is not allowed when a specific test method is specified");
                    System.exit(1);
                }
            }

            return metric;
        } catch (ClassNotFoundException e) {
            System.err.println("Error: Metric class not found: " + metricClassName);
            System.exit(1);
        } catch (ClassCastException e) {
            System.err.println("Error: Class does not implement org.tori.metrics.Metric: " + metricClassName);
            System.exit(1);
        } catch (NoSuchMethodException e) {
            System.err.println("Error: Metric class must have a public no-argument constructor: " + metricClassName);
            System.exit(1);
        } catch (InstantiationException | IllegalAccessException e) {
            System.err.println("Error: Cannot instantiate metric class: " + metricClassName);
            System.err.println("  Ensure the class is concrete and has a public no-argument constructor");
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Error: Failed to instantiate metric: " + e.getMessage());
            System.exit(1);
        }
        return null; // Unreachable, but required for compilation
    }
    
    /**
     * Print the application banner with version information.
     */
    private static void printBanner() {
        System.out.println(ReportStyle.boldWhite("> Tori " + VERSION + " - Test Oracle Inspector"));
        System.out.println();
    }

    /**
     * Print the execution parameters to the console.
     */
    private static void printExecutionParameters(String testFilePath, String testMethodName, String metricClassName, String metricConfigPath) {
        System.out.println(ReportStyle.boldWhite("Execution Parameters:"));
        System.out.println("  test-file: " + testFilePath);
        if (testMethodName != null) {
            System.out.println("  test-method: " + testMethodName);
        } else {
            System.out.println("  test-method: all");
        }
        if (metricClassName != null) {
            System.out.println("  metric: " + metricClassName);
            if (metricConfigPath != null) {
                System.out.println("  metric-config: " + metricConfigPath);
            }
        } else {
            System.out.println("  metric: none (default: print oracles without assessment)");
        }
        System.out.println();
    }

}

package org.tori;

import org.apache.commons.cli.*;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;

import org.tori.metrics.StateFieldCoverage;
import java.lang.ClassNotFoundException;

public class Main {
    
    private static final String VERSION = "1.0.0";
    
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
        System.out.println("Tori " + VERSION + " - Test Oracle Inspector");
        System.out.println();
        
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
        System.out.println("Execution Parameters:");
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
            System.out.println("  metric: none");
        }
        System.out.println();

        try {
            // Read the test file
            Path path = Paths.get(testFilePath);
            if (!Files.exists(path)) {
                System.err.println("Error: File not found: " + testFilePath);
                System.exit(1);
            }

            String sourceCode = Files.readString(path);

            // Parse and find assertions
            TestOracleInspector inspector = new TestOracleInspector();
            List<MethodOracles> results = inspector.findOracles(sourceCode, testMethodName);

            // Load metric if specified
            org.tori.metrics.Metric metric = null;
            if (metricClassName != null) {
                try {
                    Class<?> metricClass = getMetricClass(metricClassName, testFilePath);
                    //Class<?> metricClass = Class.forName(metricClassName);
                    metric = (org.tori.metrics.Metric) metricClass.getDeclaredConstructor().newInstance();
                    
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
                        System.out.println("Metric Configuration:");
                        if (metric instanceof StateFieldCoverage) {
                            StateFieldCoverage sfcMetric = (StateFieldCoverage) metric;
                            java.util.List<String> paths = sfcMetric.getTargetClassPaths();
                            if (paths.size() == 1) {
                                System.out.println("  target_class: " + paths.get(0));
                            } else {
                                System.out.println("  target_classes: " + String.join(", ", paths));
                            }
                            
                            // Report identified target fields
                            java.util.Set<String> targetFields = sfcMetric.getLastTargetFields();
                            System.out.println("  target_fields: " + targetFields.size() + " fields identified");
                            if (!targetFields.isEmpty()) {
                                java.util.List<String> sortedFields = new java.util.ArrayList<>(targetFields);
                                java.util.Collections.sort(sortedFields);
                                for (String field : sortedFields) {
                                    System.out.println("    - " + field);
                                }
                            }
                            
                            // Report dependency classes
                            java.util.Set<String> loadedDeps = sfcMetric.getLastLoadedDependencyClasses();
                            java.util.Set<String> failedDeps = sfcMetric.getLastFailedDependencyClasses();
                            
                            if (!loadedDeps.isEmpty() || !failedDeps.isEmpty()) {
                                System.out.println("  dependency_classes:");
                                
                                if (!loadedDeps.isEmpty()) {
                                    java.util.List<String> sortedLoadedDeps = new java.util.ArrayList<>(loadedDeps);
                                    java.util.Collections.sort(sortedLoadedDeps);
                                    for (String depClass : sortedLoadedDeps) {
                                        System.out.println("    - " + depClass + " (loaded)");
                                    }
                                }
                                
                                if (!failedDeps.isEmpty()) {
                                    java.util.List<String> sortedFailedDeps = new java.util.ArrayList<>(failedDeps);
                                    java.util.Collections.sort(sortedFailedDeps);
                                    for (String depClass : sortedFailedDeps) {
                                        System.out.println("    - " + depClass + " (WARNING: source file not found)");
                                    }
                                }
                            }
                            
                            System.out.println("  exec_level: " + sfcMetric.getExecutionLevel().getConfigValue());
                            System.out.println("  iterable_field_tracking: " + (sfcMetric.isIterableFieldTrackingEnabled() ? "enabled" : "disabled"));
                        } else {
                            // For other metrics, print all config properties
                            for (String key : config.stringPropertyNames()) {
                                System.out.println("  " + key + ": " + config.getProperty(key));
                            }
                        }
                        System.out.println();
                        
                        // Validate execution level constraints
                        if (metric.getExecutionLevel() == org.tori.metrics.ExecutionLevel.TEST_CLASS && testMethodName != null) {
                            System.err.println("Error: exec_level 'test_class' is not allowed when a specific test method is specified");
                            System.exit(1);
                        }
                    }
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
                    e.printStackTrace();
                    System.err.println("Error: Cannot instantiate metric class: " + metricClassName);
                    System.err.println("  Ensure the class is concrete and has a public no-argument constructor");
                    System.exit(1);
                } catch (Exception e) {
                    System.err.println("Error: Failed to instantiate metric: " + e.getMessage());
                    System.exit(1);
                }
            }

            // Print results
            if (results.isEmpty()) {
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
                    
                    for (MethodOracles methodOracles : results) {
                        allOracles.addAll(methodOracles.oracles());
                        allTestCases.append(methodOracles.testCaseSource()).append("\n");
                    }
                    
                    if (!allOracles.isEmpty()) {
                        double score = metric.assessMultiple(allTestCases.toString(), allOracles);
                        System.out.println("Test Class Assessment:");
                        
                        if (metric instanceof StateFieldCoverage) {
                            StateFieldCoverage sfcMetric = (StateFieldCoverage) metric;
                            java.util.Set<String> accessedFields = sfcMetric.getLastAccessedFields();
                            java.util.Set<String> missingFields = sfcMetric.getLastMissingFields();
                            System.out.println("  state_field_coverage_score: " + String.format("%.2f", score));
                            System.out.println("  total_assertions: " + allOracles.size());
                            System.out.println("  accessed_fields: " + accessedFields.size() + " " + accessedFields);
                            System.out.println("  missing_fields: " + missingFields.size() + " " + missingFields);
                        } else {
                            System.out.print("  All assertions [score: " + String.format("%.2f", score));
                            System.out.println("]");
                            System.out.println("  Total assertions: " + allOracles.size());
                        }
                    }
                    System.out.println();
                } else if (metric != null && metric.getExecutionLevel() == org.tori.metrics.ExecutionLevel.TEST_METHOD) {
                    // TEST_METHOD level: compute metric per method for all assertions in that method
                    for (MethodOracles methodOracles : results) {
                        System.out.println("Test Method: " + methodOracles.methodName());
                        if (methodOracles.oracles().isEmpty()) {
                            System.out.println("  No assertions found");
                        } else {
                            double score = metric.assessMultiple(methodOracles.testCaseSource(), methodOracles.oracles());
                            
                            if (metric instanceof StateFieldCoverage) {
                                StateFieldCoverage sfcMetric = (StateFieldCoverage) metric;
                                java.util.Set<String> accessedFields = sfcMetric.getLastAccessedFields();
                                java.util.Set<String> missingFields = sfcMetric.getLastMissingFields();
                                System.out.println("  state_field_coverage_score: " + String.format("%.2f", score));
                                System.out.println("  total_assertions: " + methodOracles.oracles().size());
                                System.out.println("  accessed_fields: " + accessedFields.size() + " " + accessedFields);
                                System.out.println("  missing_fields: " + missingFields.size() + " " + missingFields);
                            } else {
                                System.out.print("  All assertions [score: " + String.format("%.2f", score));
                                System.out.println("]");
                                System.out.println("  Total assertions: " + methodOracles.oracles().size());
                            }
                        }
                        System.out.println();
                    }
                } else {
                    // ASSERT level (default): compute metric per assertion
                    for (MethodOracles methodOracles : results) {
                        System.out.println("Test Method: " + methodOracles.methodName());
                        if (methodOracles.oracles().isEmpty()) {
                            System.out.println("  No assertions found");
                        } else {
                            for (String oracle : methodOracles.oracles()) {
                                if (metric != null) {
                                    double score = metric.assess(methodOracles.testCaseSource(), oracle);
                                    
                                    // If the metric is StateFieldCoverage, print detailed field information
                                    if (metric instanceof org.tori.metrics.StateFieldCoverage) {
                                        org.tori.metrics.StateFieldCoverage sfcMetric = (org.tori.metrics.StateFieldCoverage) metric;
                                        java.util.Set<String> accessedFields = sfcMetric.getLastAccessedFields();
                                        java.util.Set<String> missingFields = sfcMetric.getLastMissingFields();
                                        System.out.println("  - " + oracle);
                                        System.out.println("    state_field_coverage_score: " + String.format("%.2f", score));
                                        System.out.println("    total_assertions: 1");
                                        System.out.println("    accessed_fields: " + accessedFields.size() + " " + accessedFields);
                                        System.out.println("    missing_fields: " + missingFields.size() + " " + missingFields);
                                    } else {
                                        System.out.print("  - " + oracle + " [score: " + String.format("%.2f", score));
                                        System.out.println("]");
                                    }
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
}

package org.tori;

import org.apache.commons.cli.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        // Define command-line options
        Options options = new Options();
        
        Option testFileOption = Option.builder("t")
                .longOpt("test-file")
                .hasArg()
                .required()
                .desc("Path to the test file (required)")
                .argName("FILE")
                .build();
        
        Option testMethodOption = Option.builder("m")
                .longOpt("test-method")
                .hasArg()
                .required(false)
                .desc("Name of the specific test method to analyze (optional)")
                .argName("METHOD")
                .build();
        
        options.addOption(testFileOption);
        options.addOption(testMethodOption);

        // Parse command-line arguments
        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd;

        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            System.err.println("Error: " + e.getMessage());
            System.err.println();
            formatter.printHelp("tori", options);
            System.exit(1);
            return;
        }

        String testFilePath = cmd.getOptionValue("t");
        String testMethodName = cmd.getOptionValue("m");

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

            // Print results
            if (results.isEmpty()) {
                if (testMethodName != null) {
                    System.out.println("No test method named '" + testMethodName + "' found.");
                } else {
                    System.out.println("No test methods with assertions found.");
                }
            } else {
                for (MethodOracles methodOracles : results) {
                    System.out.println("Test Method: " + methodOracles.methodName());
                    if (methodOracles.oracles().isEmpty()) {
                        System.out.println("  No assertions found");
                    } else {
                        for (String oracle : methodOracles.oracles()) {
                            System.out.println("  - " + oracle);
                        }
                    }
                    System.out.println();
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

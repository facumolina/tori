package org.tori;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar tori.jar <test-file.java> [test-method-name]");
            System.exit(1);
        }

        String testFilePath = args[0];
        String testMethodName = args.length > 1 ? args[1] : null;

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

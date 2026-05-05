package org.tori.metrics.sfc;

import org.tori.metrics.StateFieldCoverage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for StateFieldCoverage with absolute file paths.
 * Tests that referenced classes in different packages are properly found
 * when using absolute paths for the target class.
 */
class StateFieldCoverageAbsolutePathTest {

    @TempDir
    Path tempDir;

    @Test
    void testPackageAwareClassResolution_AbsolutePath() {
        StateFieldCoverage metric = new StateFieldCoverageJava();
        Properties config = new Properties();
        
        // Use absolute path instead of relative
        String absolutePath = System.getProperty("user.dir") + "/src/test/resources/com/example/package1/MainClass.java";
        config.setProperty("target_class", absolutePath);
        config.setProperty("iterable_field_tracking", "false");
        metric.configure(config);

        // Get the identified target fields
        Set<String> targetFields = metric.getLastTargetFields();

        // MainClass: id, name, dependency (3 fields)
        // ReferencedClass: value, active (2 fields)
        // Total: 5 fields
        assertEquals(5, targetFields.size(),
            "Should have 5 fields from MainClass and ReferencedClass");

        // Verify fields from both classes are present
        assertTrue(targetFields.stream().anyMatch(f -> f.contains("MainClass.id")),
            "Should identify MainClass.id field");
        assertTrue(targetFields.stream().anyMatch(f -> f.contains("MainClass.name")),
            "Should identify MainClass.name field");
        assertTrue(targetFields.stream().anyMatch(f -> f.contains("MainClass.dependency")),
            "Should identify MainClass.dependency field");
        assertTrue(targetFields.stream().anyMatch(f -> f.contains("ReferencedClass.value")),
            "Should identify ReferencedClass.value field");
        assertTrue(targetFields.stream().anyMatch(f -> f.contains("ReferencedClass.active")),
            "Should identify ReferencedClass.active field");
    }

    @Test
    void testPackageAwareClassResolution_AbsolutePath_DependencyTracking() {
        StateFieldCoverage metric = new StateFieldCoverageJava();
        Properties config = new Properties();
        
        // Use absolute path instead of relative
        String absolutePath = System.getProperty("user.dir") + "/src/test/resources/com/example/package1/MainClass.java";
        config.setProperty("target_class", absolutePath);
        config.setProperty("iterable_field_tracking", "false");
        metric.configure(config);

        // Get the dependency tracking information
        Set<String> loadedDeps = metric.getLastLoadedDependencyClasses();
        Set<String> failedDeps = metric.getLastFailedDependencyClasses();

        // ReferencedClass should be found and loaded
        assertTrue(loadedDeps.contains("ReferencedClass"),
            "Should have loaded ReferencedClass as a dependency");
        
        // There should be no failed dependencies
        assertTrue(failedDeps.isEmpty(),
            "Should have no failed dependencies for MainClass - failed: " + failedDeps);
    }

    @Test
    void testPackageAwareClassResolution_NonStandardDirectoryStructure() throws IOException {
        // This test verifies the fix for resolveClassPath when the file path does not
        // contain standard source root markers ("java", "resources", "main", "test", "src").
        // The old heuristic-based findSourceRoot would fall back to the package directory
        // itself, leading to an incorrect resolution path. The fix uses the package name
        // to compute the source root by navigating up by the number of package components.

        // Create a non-standard directory structure: tempDir/com/example/package1/ and
        // tempDir/com/example/package2/ where tempDir has no standard markers.
        Path pkg1Dir = tempDir.resolve("com/example/package1");
        Path pkg2Dir = tempDir.resolve("com/example/package2");
        Files.createDirectories(pkg1Dir);
        Files.createDirectories(pkg2Dir);

        // Create MainClass.java in package1 (references ReferencedClass from package2)
        String mainClassSource =
            "package com.example.package1;\n" +
            "import com.example.package2.ReferencedClass;\n" +
            "public class MainClass {\n" +
            "    private int id;\n" +
            "    private String name;\n" +
            "    private ReferencedClass dependency;\n" +
            "}\n";
        Files.writeString(pkg1Dir.resolve("MainClass.java"), mainClassSource);

        // Create ReferencedClass.java in package2
        String referencedClassSource =
            "package com.example.package2;\n" +
            "public class ReferencedClass {\n" +
            "    private double value;\n" +
            "    private boolean active;\n" +
            "}\n";
        Files.writeString(pkg2Dir.resolve("ReferencedClass.java"), referencedClassSource);

        StateFieldCoverage metric = new StateFieldCoverageJava();
        Properties config = new Properties();
        config.setProperty("target_class", pkg1Dir.resolve("MainClass.java").toString());
        config.setProperty("iterable_field_tracking", "false");
        metric.configure(config);

        Set<String> targetFields = metric.getLastTargetFields();
        // MainClass: id, name, dependency (3 fields)
        // ReferencedClass: value, active (2 fields)
        // Total: 5 fields
        assertEquals(5, targetFields.size(),
            "Should have 5 fields from MainClass and ReferencedClass even without standard source root markers");

        Set<String> loadedDeps = metric.getLastLoadedDependencyClasses();
        Set<String> failedDeps = metric.getLastFailedDependencyClasses();
        assertTrue(loadedDeps.contains("ReferencedClass"),
            "ReferencedClass should be found even without standard source root markers in path");
        assertTrue(failedDeps.isEmpty(),
            "Should have no failed dependencies - failed: " + failedDeps);
    }
}

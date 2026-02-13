package org.tori.metrics;

import org.junit.jupiter.api.Test;

import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for StateFieldCoverage with absolute file paths.
 * Tests that referenced classes in different packages are properly found
 * when using absolute paths for the target class.
 */
class StateFieldCoverageAbsolutePathTest {

    @Test
    void testPackageAwareClassResolution_AbsolutePath() {
        StateFieldCoverage metric = new StateFieldCoverage();
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
        StateFieldCoverage metric = new StateFieldCoverage();
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
}

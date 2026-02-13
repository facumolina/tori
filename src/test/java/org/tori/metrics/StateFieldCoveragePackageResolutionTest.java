package org.tori.metrics;

import org.junit.jupiter.api.Test;

import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for StateFieldCoverage package-aware class resolution.
 * Tests that referenced classes in different packages are properly found.
 */
class StateFieldCoveragePackageResolutionTest {

    @Test
    void testPackageAwareClassResolution() {
        StateFieldCoverage metric = new StateFieldCoverage();
        Properties config = new Properties();
        config.setProperty("target_class", "src/test/resources/com/example/package1/MainClass.java");
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
    void testPackageAwareClassResolution_DependencyTracking() {
        StateFieldCoverage metric = new StateFieldCoverage();
        Properties config = new Properties();
        config.setProperty("target_class", "src/test/resources/com/example/package1/MainClass.java");
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
            "Should have no failed dependencies for MainClass");
    }
    
    @Test
    void testSamePackageResolution() {
        StateFieldCoverage metric = new StateFieldCoverage();
        Properties config = new Properties();
        config.setProperty("target_class", "src/test/resources/com/example/package1/MixedReferenceClass.java");
        config.setProperty("iterable_field_tracking", "false");
        metric.configure(config);

        // Get the identified target fields
        Set<String> targetFields = metric.getLastTargetFields();

        // MixedReferenceClass: name, localDep, importedDep (3 fields)
        // LocalReferenceClass: value (1 field)
        // ReferencedClass: value, active (2 fields)
        // Total: 6 fields
        assertEquals(6, targetFields.size(),
            "Should have 6 fields from all three classes");

        // Verify fields from all three classes are present
        assertTrue(targetFields.stream().anyMatch(f -> f.contains("MixedReferenceClass.name")),
            "Should identify MixedReferenceClass.name field");
        assertTrue(targetFields.stream().anyMatch(f -> f.contains("MixedReferenceClass.localDep")),
            "Should identify MixedReferenceClass.localDep field");
        assertTrue(targetFields.stream().anyMatch(f -> f.contains("MixedReferenceClass.importedDep")),
            "Should identify MixedReferenceClass.importedDep field");
        assertTrue(targetFields.stream().anyMatch(f -> f.contains("LocalReferenceClass.value")),
            "Should identify LocalReferenceClass.value field");
        assertTrue(targetFields.stream().anyMatch(f -> f.contains("ReferencedClass.value")),
            "Should identify ReferencedClass.value field");
        assertTrue(targetFields.stream().anyMatch(f -> f.contains("ReferencedClass.active")),
            "Should identify ReferencedClass.active field");
    }
    
    @Test
    void testSamePackageResolution_DependencyTracking() {
        StateFieldCoverage metric = new StateFieldCoverage();
        Properties config = new Properties();
        config.setProperty("target_class", "src/test/resources/com/example/package1/MixedReferenceClass.java");
        config.setProperty("iterable_field_tracking", "false");
        metric.configure(config);

        // Get the dependency tracking information
        Set<String> loadedDeps = metric.getLastLoadedDependencyClasses();
        Set<String> failedDeps = metric.getLastFailedDependencyClasses();

        // Both dependencies should be found and loaded
        assertTrue(loadedDeps.contains("LocalReferenceClass"),
            "Should have loaded LocalReferenceClass (same package) as a dependency");
        assertTrue(loadedDeps.contains("ReferencedClass"),
            "Should have loaded ReferencedClass (imported) as a dependency");
        
        // There should be no failed dependencies
        assertTrue(failedDeps.isEmpty(),
            "Should have no failed dependencies for MixedReferenceClass");
    }
}

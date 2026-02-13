package org.tori.metrics;

import org.junit.jupiter.api.Test;

import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for StateFieldCoverage transitive dependency tracking.
 * Tests that dependencies of dependency classes are properly tracked.
 */
class StateFieldCoverageTransitiveDependencyTest {

    @Test
    void testTransitiveDependencyTracking_ThreeLevels() {
        StateFieldCoverage metric = new StateFieldCoverage();
        Properties config = new Properties();
        config.setProperty("target_class", "src/test/resources/ClassA.java");
        config.setProperty("iterable_field_tracking", "false");
        metric.configure(config);

        // Get the dependency tracking information
        Set<String> loadedDeps = metric.getLastLoadedDependencyClasses();
        Set<String> failedDeps = metric.getLastFailedDependencyClasses();

        // ClassA directly depends on ClassB, which depends on ClassC
        // All of these should be tracked as loaded dependencies
        assertTrue(loadedDeps.contains("ClassB"),
            "Should have loaded ClassB as a direct dependency of ClassA");
        assertTrue(loadedDeps.contains("ClassC"),
            "Should have loaded ClassC as a transitive dependency (ClassA -> ClassB -> ClassC)");
        
        // There should be no failed dependencies
        assertTrue(failedDeps.isEmpty(),
            "Should have no failed dependencies for ClassA");
    }

    @Test
    void testTransitiveDependencyTracking_AllFieldsIncluded() {
        StateFieldCoverage metric = new StateFieldCoverage();
        Properties config = new Properties();
        config.setProperty("target_class", "src/test/resources/ClassA.java");
        config.setProperty("iterable_field_tracking", "false");
        metric.configure(config);

        // Get the identified target fields
        Set<String> targetFields = metric.getLastTargetFields();

        // ClassA: id, dependency (2 fields)
        // ClassB: name, nestedDependency (2 fields)
        // ClassC: value, active (2 fields)
        // Total: 6 fields
        assertEquals(6, targetFields.size(),
            "Should have 6 fields from ClassA, ClassB, and ClassC");

        // Verify fields from all three classes are present
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith(".ClassA.id")),
            "Should identify ClassA.id field");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith(".ClassA.dependency")),
            "Should identify ClassA.dependency field");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith(".ClassB.name")),
            "Should identify ClassB.name field");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith(".ClassB.nestedDependency")),
            "Should identify ClassB.nestedDependency field");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith(".ClassC.value")),
            "Should identify ClassC.value field");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith(".ClassC.active")),
            "Should identify ClassC.active field");
    }

    @Test
    void testTransitiveDependencyTracking_MissingTransitiveDependency() {
        StateFieldCoverage metric = new StateFieldCoverage();
        Properties config = new Properties();
        config.setProperty("target_class", "src/test/resources/ClassWithTransitiveMissingDep.java");
        config.setProperty("iterable_field_tracking", "false");
        metric.configure(config);

        // Get the dependency tracking information
        Set<String> loadedDeps = metric.getLastLoadedDependencyClasses();
        Set<String> failedDeps = metric.getLastFailedDependencyClasses();

        // ClassWithTransitiveMissingDep depends on ClassWithMissingDep
        // ClassWithMissingDep depends on MissingTransitiveClass (which doesn't exist)
        assertTrue(loadedDeps.contains("ClassWithMissingDep"),
            "Should have loaded ClassWithMissingDep as a direct dependency");
        assertTrue(failedDeps.contains("MissingTransitiveClass"),
            "Should report MissingTransitiveClass as a failed transitive dependency");
    }

    @Test
    void testTransitiveDependencyTracking_FieldsFromLoadedTransitiveDeps() {
        StateFieldCoverage metric = new StateFieldCoverage();
        Properties config = new Properties();
        config.setProperty("target_class", "src/test/resources/ClassWithTransitiveMissingDep.java");
        config.setProperty("iterable_field_tracking", "false");
        metric.configure(config);

        // Get the identified target fields
        Set<String> targetFields = metric.getLastTargetFields();

        // ClassWithTransitiveMissingDep: count, dependency (2 fields)
        // ClassWithMissingDep: label, missing (2 fields)
        // Total: 4 fields (MissingTransitiveClass not loaded)
        assertEquals(4, targetFields.size(),
            "Should have 4 fields from ClassWithTransitiveMissingDep and ClassWithMissingDep");

        // Verify fields from both loaded classes are present
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith(".ClassWithTransitiveMissingDep.count")),
            "Should identify ClassWithTransitiveMissingDep.count field");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith(".ClassWithMissingDep.label")),
            "Should identify ClassWithMissingDep.label field");
    }
}

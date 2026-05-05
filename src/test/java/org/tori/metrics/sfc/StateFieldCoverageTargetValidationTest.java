package org.tori.metrics.sfc;

import org.tori.metrics.StateFieldCoverage;
import org.junit.jupiter.api.Test;

import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for StateFieldCoverage target class validation and field identification.
 * Tests validation of target class paths and proper identification of fields.
 */
class StateFieldCoverageTargetValidationTest {

    @Test
    void testConfigure_InvalidFilePath_ThrowsException() {
        StateFieldCoverage metric = new StateFieldCoverageJava();
        Properties config = new Properties();
        config.setProperty("target_class", "src/test/resources/NonExistentFile.java");
        
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> metric.configure(config),
            "Should throw exception for non-existent file"
        );
        
        assertTrue(exception.getMessage().contains("does not exist"),
            "Exception message should mention file does not exist");
        assertTrue(exception.getMessage().contains("NonExistentFile.java"),
            "Exception message should include the file name");
    }

    @Test
    void testConfigure_NonJavaFile_ThrowsException() {
        // Test with an existing non-Java file (README.md)
        StateFieldCoverage metric = new StateFieldCoverageJava();
        Properties config = new Properties();
        config.setProperty("target_class", "README.md");
        
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> metric.configure(config),
            "Should throw exception for non-Java file"
        );
        
        assertTrue(exception.getMessage().contains(".java extension"),
            "Exception message should mention .java extension requirement");
        assertTrue(exception.getMessage().contains("README.md"),
            "Exception message should include the file name");
    }

    @Test
    void testConfigure_ValidFilePath_Succeeds() {
        StateFieldCoverage metric = new StateFieldCoverageJava();
        Properties config = new Properties();
        config.setProperty("target_class", "src/test/resources/IntsList.java");
        config.setProperty("iterable_field_tracking", "false");
        
        // Should not throw any exception
        assertDoesNotThrow(() -> metric.configure(config),
            "Should not throw exception for valid Java file");
        
        // Verify the target class path was set
        assertEquals("src/test/resources/IntsList.java", metric.getTargetClassPath());
    }

    @Test
    void testConfigure_MultipleClasses_OneInvalid_ThrowsException() {
        StateFieldCoverage metric = new StateFieldCoverageJava();
        Properties config = new Properties();
        config.setProperty("target_class", "src/test/resources/IntsList.java,src/test/resources/Invalid.java");
        
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> metric.configure(config),
            "Should throw exception when one of multiple files is invalid"
        );
        
        assertTrue(exception.getMessage().contains("does not exist"),
            "Exception message should mention file does not exist");
    }

    @Test
    void testFieldIdentification_IntsList() {
        StateFieldCoverage metric = new StateFieldCoverageJava();
        Properties config = new Properties();
        config.setProperty("target_class", "src/test/resources/IntsList.java");
        config.setProperty("iterable_field_tracking", "false");
        metric.configure(config);
        
        // Get the identified target fields
        Set<String> targetFields = metric.getLastTargetFields();
        
        // IntsList has 4 fields: IntsList.header, IntsList.size, Node.next, Node.item
        assertEquals(4, targetFields.size(), 
            "IntsList should have 4 fields (including inner class fields)");
        
        // Verify specific fields are present
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith(".IntsList.header")),
            "Should identify header field");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith(".IntsList.size")),
            "Should identify size field");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith(".Node.next")),
            "Should identify Node.next field");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith(".Node.item")),
            "Should identify Node.item field");
    }

    @Test
    void testFieldIdentification_Person() {
        StateFieldCoverage metric = new StateFieldCoverageJava();
        Properties config = new Properties();
        config.setProperty("target_class", "src/test/resources/Person.java");
        config.setProperty("iterable_field_tracking", "false");
        metric.configure(config);
        
        // Get the identified target fields
        Set<String> targetFields = metric.getLastTargetFields();
        
        // Person has 6 fields: Person.name, Person.age, Person.address, Address.street, Address.city, Address.name
        assertEquals(6, targetFields.size(), 
            "Person should have 6 fields (including inner class fields)");
        
        // Verify specific fields are present
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith(".Person.name")),
            "Should identify Person.name field");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith(".Person.age")),
            "Should identify Person.age field");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith(".Person.address")),
            "Should identify Person.address field");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith(".Address.street")),
            "Should identify Address.street field");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith(".Address.city")),
            "Should identify Address.city field");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith(".Address.name")),
            "Should identify Address.name field");
    }

    @Test
    void testFieldIdentification_MultipleClasses() {
        StateFieldCoverage metric = new StateFieldCoverageJava();
        Properties config = new Properties();
        config.setProperty("target_class", "src/test/resources/IntsList.java,src/test/resources/Person.java");
        config.setProperty("iterable_field_tracking", "false");
        metric.configure(config);
        
        // Get the identified target fields
        Set<String> targetFields = metric.getLastTargetFields();
        
        // IntsList has 4 fields + Person has 6 fields = 10 total fields
        assertEquals(10, targetFields.size(), 
            "Should have 10 fields from both IntsList and Person");
        
        // Verify fields from both classes are present
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith(".IntsList.header")),
            "Should identify IntsList.header field");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith(".Person.name")),
            "Should identify Person.name field");
    }

    @Test
    void testFieldIdentification_WithIterableTracking() {
        StateFieldCoverage metric = new StateFieldCoverageJava();
        Properties config = new Properties();
        config.setProperty("target_class", "src/test/resources/IntsList.java");
        config.setProperty("iterable_field_tracking", "true");
        metric.configure(config);
        
        // Get the identified target fields
        Set<String> targetFields = metric.getLastTargetFields();
        
        // With iterable tracking, there should be additional "+" suffixed fields for iterable fields
        assertTrue(targetFields.size() >= 4, 
            "Should have at least 4 base fields from IntsList");
        
        // The base fields should still be present
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith(".IntsList.header") && !f.contains("+")),
            "Should identify header field without + suffix");
    }

    @Test
    void testFieldIdentification_usedClassesInOtherFiles_noIterables() { 
        StateFieldCoverage metric = new StateFieldCoverageJava(); 
        Properties config = new Properties(); config.setProperty("target_class", "src/test/resources/RedBlackTree.java");
        config.setProperty("iterable_field_tracking", "false");
        metric.configure(config);

        // Get the identified target fields
        Set<String> targetFields = metric.getLastTargetFields();

        // RedBlackTree has 2 non-static fields (root, size) + RedBlackTreeNode has 6 fields = 8 total fields
        // Static fields (RED, BLACK) are excluded by default (include_static_fields=false)
        assertEquals(8, targetFields.size(), "Should have 8 fields from both RedBlackTree and RedBlackATreeNode"); 

        // Verify fields from both classes are present
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith(".RedBlackTree.root")),"Should identify RedBlackTree.root field");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith(".RedBlackTreeNode.key")),"Should identify RedBlackTreeNode.key field");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith(".RedBlackTreeNode.left")),"Should identify RedBlackTreeNode.left field");
    }

    @Test
    void testFieldIdentification_RedBlackTree_AllFields() {
        StateFieldCoverage metric = new StateFieldCoverageJava();
        Properties config = new Properties();
        config.setProperty("target_class", "src/test/resources/RedBlackTree.java");
        config.setProperty("iterable_field_tracking", "false");
        config.setProperty("include_static_fields", "true");
        metric.configure(config);

        // Get the identified target fields
        Set<String> targetFields = metric.getLastTargetFields();

        // RedBlackTree has 4 fields: root, size, RED, BLACK (static fields included explicitly)
        // RedBlackTreeNode has 6 fields: key, value, left, right, parent, color
        // Total: 10 fields
        assertEquals(10, targetFields.size(), "Should have 10 fields total");

        // Verify all RedBlackTree fields are present
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith(".RedBlackTree.root")),
            "Should identify RedBlackTree.root field");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith(".RedBlackTree.size")),
            "Should identify RedBlackTree.size field");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith(".RedBlackTree.RED")),
            "Should identify RedBlackTree.RED field");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith(".RedBlackTree.BLACK")),
            "Should identify RedBlackTree.BLACK field");

        // Verify all RedBlackTreeNode fields are present
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith(".RedBlackTreeNode.key")),
            "Should identify RedBlackTreeNode.key field");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith(".RedBlackTreeNode.value")),
            "Should identify RedBlackTreeNode.value field");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith(".RedBlackTreeNode.left")),
            "Should identify RedBlackTreeNode.left field");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith(".RedBlackTreeNode.right")),
            "Should identify RedBlackTreeNode.right field");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith(".RedBlackTreeNode.parent")),
            "Should identify RedBlackTreeNode.parent field");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith(".RedBlackTreeNode.color")),
            "Should identify RedBlackTreeNode.color field");
    }

    @Test
    void testAssess_RedBlackTree_SizeField() {
        StateFieldCoverage metric = new StateFieldCoverageJava();
        Properties config = new Properties();
        config.setProperty("target_class", "src/test/resources/RedBlackTree.java");
        config.setProperty("iterable_field_tracking", "false");
        metric.configure(config);

        // Test case that uses getSize method which accesses the size field
        String testCase = """
            @Test
            public void testSize() {
                RedBlackTree t = new RedBlackTree();
                t.insert(10);
                assertEquals(1, t.getSize());
            }
            """;
        
        String oracle = "assertEquals(1, t.getSize());";
        
        double coverage = metric.assess(testCase, oracle);
        
        // getSize method accesses the size field (1 out of 8 non-static fields = 0.125)
        // Static fields (RED, BLACK) are excluded by default (include_static_fields=false)
        assertTrue(coverage > 0.0, "Should have non-zero coverage");
        assertEquals(0.125, coverage, 0.01, "Should cover size field (1 out of 8)");
        
        // Verify that size field was identified as accessed
        Set<String> accessedFields = metric.getLastAccessedFields();
        assertTrue(accessedFields.stream().anyMatch(f -> f.endsWith(".RedBlackTree.size")),
            "Should have accessed RedBlackTree.size field");
    }

    @Test
    void testAssess_RedBlackTree_VerifyMissingFields() {
        StateFieldCoverage metric = new StateFieldCoverageJava();
        Properties config = new Properties();
        config.setProperty("target_class", "src/test/resources/RedBlackTree.java");
        config.setProperty("iterable_field_tracking", "false");
        metric.configure(config);

        // Simple test that only accesses size
        String testCase = """
            @Test
            public void testSize() {
                RedBlackTree t = new RedBlackTree();
                t.insert(10);
                assertEquals(1, t.getSize());
            }
            """;
        
        String oracle = "assertEquals(1, t.getSize());";
        
        double coverage = metric.assess(testCase, oracle);
        
        // Verify missing fields include all RedBlackTreeNode fields
        Set<String> missingFields = metric.getLastMissingFields();
        assertTrue(missingFields.stream().anyMatch(f -> f.endsWith(".RedBlackTreeNode.key")),
            "RedBlackTreeNode.key should be in missing fields");
        assertTrue(missingFields.stream().anyMatch(f -> f.endsWith(".RedBlackTreeNode.value")),
            "RedBlackTreeNode.value should be in missing fields");
        assertTrue(missingFields.stream().anyMatch(f -> f.endsWith(".RedBlackTreeNode.left")),
            "RedBlackTreeNode.left should be in missing fields");
        assertTrue(missingFields.stream().anyMatch(f -> f.endsWith(".RedBlackTreeNode.right")),
            "RedBlackTreeNode.right should be in missing fields");
        assertTrue(missingFields.stream().anyMatch(f -> f.endsWith(".RedBlackTreeNode.parent")),
            "RedBlackTreeNode.parent should be in missing fields");
        assertTrue(missingFields.stream().anyMatch(f -> f.endsWith(".RedBlackTreeNode.color")),
            "RedBlackTreeNode.color should be in missing fields");
        
        // Should have 7 missing fields (all except size; static fields RED and BLACK excluded by default)
        assertEquals(7, missingFields.size(), "Should have 7 missing fields");
    }

    @Test
    void testFieldIdentification_RedBlackTree_WithIterableTracking() {
        StateFieldCoverage metric = new StateFieldCoverageJava();
        Properties config = new Properties();
        config.setProperty("target_class", "src/test/resources/RedBlackTree.java");
        config.setProperty("iterable_field_tracking", "true");
        metric.configure(config);

        // Get the identified target fields
        Set<String> targetFields = metric.getLastTargetFields();

        // With iterable tracking, there may be additional fields with "+" suffix
        // Base fields should still be 10
        assertTrue(targetFields.size() >= 10, "Should have at least 10 base fields");
        
        // Verify key fields are still present (without + suffix)
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith(".RedBlackTree.root") && !f.contains("+")),
            "Should identify RedBlackTree.root field without + suffix");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith(".RedBlackTreeNode.key") && !f.contains("+")),
            "Should identify RedBlackTreeNode.key field without + suffix");
    }

    @Test
    void testDependencyTracking_RedBlackTree_LoadedDependency() {
        StateFieldCoverage metric = new StateFieldCoverageJava();
        Properties config = new Properties();
        config.setProperty("target_class", "src/test/resources/RedBlackTree.java");
        config.setProperty("iterable_field_tracking", "false");
        metric.configure(config);

        // Get the dependency tracking information
        Set<String> loadedDeps = metric.getLastLoadedDependencyClasses();
        Set<String> failedDeps = metric.getLastFailedDependencyClasses();

        // RedBlackTreeNode should be loaded as a dependency
        assertTrue(loadedDeps.contains("RedBlackTreeNode"),
            "Should have loaded RedBlackTreeNode as a dependency");
        
        // There should be no failed dependencies
        assertTrue(failedDeps.isEmpty(),
            "Should have no failed dependencies for RedBlackTree");
    }

    @Test
    void testDependencyTracking_IntsList_NoExternalDependencies() {
        StateFieldCoverage metric = new StateFieldCoverageJava();
        Properties config = new Properties();
        config.setProperty("target_class", "src/test/resources/IntsList.java");
        config.setProperty("iterable_field_tracking", "false");
        metric.configure(config);

        // Get the dependency tracking information
        Set<String> loadedDeps = metric.getLastLoadedDependencyClasses();
        Set<String> failedDeps = metric.getLastFailedDependencyClasses();

        // IntsList uses Node which is an inner class, so no external dependencies
        assertTrue(loadedDeps.isEmpty(),
            "Should have no loaded external dependencies (Node is inner class)");
        assertTrue(failedDeps.isEmpty(),
            "Should have no failed dependencies");
    }

    @Test
    void testDependencyTracking_MissingDependency() {
        StateFieldCoverage metric = new StateFieldCoverageJava();
        Properties config = new Properties();
        config.setProperty("target_class", "src/test/resources/TestClassWithMissingDep.java");
        config.setProperty("iterable_field_tracking", "false");
        metric.configure(config);

        // Get the dependency tracking information
        Set<String> loadedDeps = metric.getLastLoadedDependencyClasses();
        Set<String> failedDeps = metric.getLastFailedDependencyClasses();

        // NonExistentClass should be reported as failed
        assertTrue(failedDeps.contains("NonExistentClass"),
            "Should report NonExistentClass as a failed dependency");
        
        // There should be no successfully loaded dependencies
        assertTrue(loadedDeps.isEmpty(),
            "Should have no successfully loaded dependencies");
    }

    @Test
    void testDependencyTracking_ClearedBetweenAssessments() {
        StateFieldCoverage metric = new StateFieldCoverageJava();
        
        // First assessment with RedBlackTree
        Properties config1 = new Properties();
        config1.setProperty("target_class", "src/test/resources/RedBlackTree.java");
        config1.setProperty("iterable_field_tracking", "false");
        metric.configure(config1);

        Set<String> loadedDeps1 = metric.getLastLoadedDependencyClasses();
        assertTrue(loadedDeps1.contains("RedBlackTreeNode"),
            "First assessment should have RedBlackTreeNode");

        // Second assessment with IntsList (which has no external dependencies)
        Properties config2 = new Properties();
        config2.setProperty("target_class", "src/test/resources/IntsList.java");
        config2.setProperty("iterable_field_tracking", "false");
        metric.configure(config2);

        Set<String> loadedDeps2 = metric.getLastLoadedDependencyClasses();
        assertTrue(loadedDeps2.isEmpty(),
            "Second assessment should have no external dependencies");
        assertFalse(loadedDeps2.contains("RedBlackTreeNode"),
            "Second assessment should not have RedBlackTreeNode from first assessment");
    }
}

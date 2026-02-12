package org.tori.metrics;

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
        StateFieldCoverage metric = new StateFieldCoverage();
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
        StateFieldCoverage metric = new StateFieldCoverage();
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
        StateFieldCoverage metric = new StateFieldCoverage();
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
        StateFieldCoverage metric = new StateFieldCoverage();
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
        StateFieldCoverage metric = new StateFieldCoverage();
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
        StateFieldCoverage metric = new StateFieldCoverage();
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
        StateFieldCoverage metric = new StateFieldCoverage();
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
        StateFieldCoverage metric = new StateFieldCoverage();
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
        StateFieldCoverage metric = new StateFieldCoverage(); 
        Properties config = new Properties(); config.setProperty("target_class", "src/test/resources/RedBlackTree.java");
        config.setProperty("iterable_field_tracking", "false");
        metric.configure(config);

        // Get the identified target fields
        Set<String> targetFields = metric.getLastTargetFields();

        // RedBlackTree has 4 fields + RedBlackATreeNode has 6 fields = 10 total fields
        assertEquals(10, targetFields.size(), "Should have 10 fields from both RedBlackTree and RedBlackATreeNode"); 

        // Verify fields from both classes are present
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith(".RedBlackTree.root")),"Should identify RedBlackTree.root field");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith(".RedBlackTreeNode.key")),"Should identify RedBlackTreeNode.key field");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith(".RedBlackTreeNode.left")),"Should identify RedBlackTreeNode.left field");
    }
}

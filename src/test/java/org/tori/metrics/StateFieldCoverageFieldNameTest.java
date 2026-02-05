package org.tori.metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for StateFieldCoverage metric with focus on field name handling.
 * Tests are based on the Person class which has duplicate field names:
 * - Person.name (String)
 * - Person.age (int)
 * - Person.address (Address)
 * - Person.Address.name (String) - duplicate of Person.name
 * - Person.Address.street (String)
 * - Person.Address.city (String)
 * Total: 6 fields (3 in Person, 3 in Person.Address)
 */
class StateFieldCoverageFieldNameTest {

    private StateFieldCoverage metric;

    @BeforeEach
    void setUp() {
        metric = new StateFieldCoverage();
        
        // Disable detailed reporting for tests
        metric.setDetailedReportingEnabled(false);
        
        // Configure the metric with the target class path
        Properties config = new Properties();
        config.setProperty("target_class", "src/test/resources/Person.java");
        metric.configure(config);
    }

    @Test
    void testFullyQualifiedFieldNames() {
        // Test that fields are stored with fully qualified names
        String testCase = """
            @Test
            public void testPerson() {
                Person p = new Person("John", 30);
                assertEquals("John", p.getName());
            }
            """;
        String oracle = "assertEquals(\"John\", p.getName());";
        
        // Assess to populate lastTargetFields
        metric.assess(testCase, oracle);
        
        Set<String> targetFields = metric.getLastTargetFields();
        
        // Verify we have the expected number of fields (3 in Person + 3 in Person.Address)
        assertEquals(6, targetFields.size(), "Should have 6 fields total");
        
        // Verify fields contain fully qualified names
        assertTrue(targetFields.stream().anyMatch(f -> f.contains("com.example.Person.name")),
                "Should contain fully qualified Person.name field");
        assertTrue(targetFields.stream().anyMatch(f -> f.contains("com.example.Person.age")),
                "Should contain fully qualified Person.age field");
        assertTrue(targetFields.stream().anyMatch(f -> f.contains("com.example.Person.address")),
                "Should contain fully qualified Person.address field");
        assertTrue(targetFields.stream().anyMatch(f -> f.contains("com.example.Person.Address.name")),
                "Should contain fully qualified Person.Address.name field");
        assertTrue(targetFields.stream().anyMatch(f -> f.contains("com.example.Person.Address.street")),
                "Should contain fully qualified Person.Address.street field");
        assertTrue(targetFields.stream().anyMatch(f -> f.contains("com.example.Person.Address.city")),
                "Should contain fully qualified Person.Address.city field");
    }

    @Test
    void testAccessPersonNameField() {
        // Test accessing Person's name field via getName()
        // Note: Since both Person and Address have getName() methods, and we don't have
        // type inference, we conservatively include fields from both methods.
        String testCase = """
            @Test
            public void testPersonName() {
                Person p = new Person("Alice", 25);
                assertEquals("Alice", p.getName());
            }
            """;
        String oracle = "assertEquals(\"Alice\", p.getName());";
        
        double score = metric.assess(testCase, oracle);
        
        Set<String> accessedFields = metric.getLastAccessedFields();
        
        // Conservative analysis: includes fields from all getName() methods (2 out of 6 fields)
        assertEquals(2.0/6.0, score, 0.01, "Should be 2/6 when getName() is called (both Person.name and Address.name)");
        
        // Verify both name fields are accessed (conservative)
        assertEquals(2, accessedFields.size(), "Should access 2 fields conservatively");
        assertTrue(accessedFields.stream().anyMatch(f -> f.contains("com.example.Person.name")),
                "Should access Person.name field");
        assertTrue(accessedFields.stream().anyMatch(f -> f.contains("com.example.Person.Address.name")),
                "Should also include Address.name field (conservative analysis)");
    }

    @Test
    void testAccessAddressNameField() {
        // Test accessing Address's name field
        // Note: When calling getAddress().getName(), we access Person.address via getAddress(),
        // and then both Person.name and Address.name via getName() (conservative analysis)
        String testCase = """
            @Test
            public void testAddressName() {
                Person p = new Person("Bob", 35);
                Person.Address addr = new Person.Address("Home", "123 Main St", "NYC");
                p.setAddress(addr);
                assertEquals("Home", p.getAddress().getName());
            }
            """;
        String oracle = "assertEquals(\"Home\", p.getAddress().getName());";
        
        double score = metric.assess(testCase, oracle);
        
        Set<String> accessedFields = metric.getLastAccessedFields();
        
        // Conservative: Person.address (from getAddress) + both name fields (from getName)
        assertEquals(3.0/6.0, score, 0.01, "Should be 3/6 with conservative analysis");
        
        // Verify expected fields
        assertTrue(accessedFields.stream().anyMatch(f -> f.contains("com.example.Person.address")),
                "Should access Person.address field");
        assertTrue(accessedFields.stream().anyMatch(f -> f.contains("com.example.Person.name") || 
                                                          f.contains("com.example.Person.Address.name")),
                "Should access at least one name field");
    }

    @Test
    void testDistinguishBetweenDuplicateFieldNames() {
        // Test that we can distinguish between Person.name and Address.name using FQN
        // Conservative analysis will include fields from all getName() methods
        String testCase = """
            @Test
            public void testBothNames() {
                Person p = new Person("Charlie", 40);
                Person.Address addr = new Person.Address("Work", "456 Oak Ave", "LA");
                p.setAddress(addr);
                // Access both name fields
                assertTrue(p.getName().equals("Charlie") && p.getAddress().getName().equals("Work"));
            }
            """;
        String oracle = "assertTrue(p.getName().equals(\"Charlie\") && p.getAddress().getName().equals(\"Work\"));";
        
        double score = metric.assess(testCase, oracle);
        
        Set<String> accessedFields = metric.getLastAccessedFields();
        
        // Conservative: Person.name, Address.name (from 2 getName calls), and Person.address (from getAddress)
        // But getName appears twice, so we get: Person.name, Address.name, Person.address
        // That's 3 unique fields out of 6 total
        assertEquals(3.0/6.0, score, 0.01, "Should be 3/6 with conservative analysis");
        
        // Verify we track fields with FQN and can distinguish between duplicate names
        assertTrue(accessedFields.stream().anyMatch(f -> f.contains("com.example.Person.name")),
                "Should access Person.name field");
        assertTrue(accessedFields.stream().anyMatch(f -> f.contains("com.example.Person.address")),
                "Should access Person.address field");
        assertTrue(accessedFields.stream().anyMatch(f -> f.contains("com.example.Person.Address.name")),
                "Should access Person.Address.name field");
        
        // Verify we distinguish between the two 'name' fields using FQN
        long nameFieldCount = accessedFields.stream()
                .filter(f -> f.endsWith(".name"))
                .count();
        assertEquals(2, nameFieldCount, "Should access 2 different 'name' fields (distinguished by FQN)");
    }

    @Test
    void testAccessAllFieldsInPerson() {
        // Test accessing multiple fields in Person class
        String testCase = """
            @Test
            public void testAllPersonFields() {
                Person p = new Person("Dave", 50);
                Person.Address addr = new Person.Address("Office", "789 Pine Rd", "SF");
                p.setAddress(addr);
                assertTrue(p.getName().equals("Dave") && 
                           p.getAge() == 50 && 
                           p.getAddress().getName().equals("Office"));
            }
            """;
        String oracle = "assertTrue(p.getName().equals(\"Dave\") && p.getAge() == 50 && p.getAddress().getName().equals(\"Office\"));";
        
        double score = metric.assess(testCase, oracle);
        
        Set<String> accessedFields = metric.getLastAccessedFields();
        
        // Conservative: Person.name, Address.name, Person.age, Person.address (4 out of 6)
        assertEquals(4.0/6.0, score, 0.01, "Should be 4/6 with conservative analysis");
        assertTrue(accessedFields.size() >= 4, "Should access at least 4 fields");
    }

    @Test
    void testFieldNameMappingIsCorrect() {
        // Test that field name mapping correctly maps short names to FQN
        String testCase = """
            @Test
            public void testAge() {
                Person p = new Person("Eve", 28);
                assertEquals(28, p.getAge());
            }
            """;
        String oracle = "assertEquals(28, p.getAge());";
        
        double score = metric.assess(testCase, oracle);
        
        Set<String> accessedFields = metric.getLastAccessedFields();
        
        // Should only access age field (1 out of 6)
        assertEquals(1.0/6.0, score, 0.01, "Should be 1/6 when only age is accessed");
        assertEquals(1, accessedFields.size(), "Should access exactly 1 field");
        assertTrue(accessedFields.stream().anyMatch(f -> f.contains("com.example.Person.age")),
                "Should access Person.age field with FQN");
    }
}

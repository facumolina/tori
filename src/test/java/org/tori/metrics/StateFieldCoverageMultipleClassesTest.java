package org.tori.metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for StateFieldCoverage metric with multiple target classes.
 * Tests are based on two unrelated classes: IntsList and Person.
 * 
 * IntsList has 4 fields:
 * - IntsList.header (Node)
 * - IntsList.size (int)
 * - Node.next (Node)
 * - Node.item (int)
 * 
 * Person has 6 fields:
 * - Person.name (String)
 * - Person.age (int)
 * - Person.address (Address)
 * - Address.street (String)
 * - Address.city (String)
 * - Address.name (String) - distinct from Person.name as it belongs to Address class
 * 
 * Total: 10 fields (4 from IntsList + 6 from Person)
 */
class StateFieldCoverageMultipleClassesTest {

    private StateFieldCoverage metric;

    @BeforeEach
    void setUp() {
        metric = new StateFieldCoverageJava();
        
        // Disable detailed reporting for tests
        metric.setDetailedReportingEnabled(false);
        
        // Configure the metric with multiple target classes
        Properties config = new Properties();
        config.setProperty("target_class", "src/test/resources/IntsList.java,src/test/resources/Person.java");
        config.setProperty("iterable_field_tracking", "false"); // Disable for simpler tests
        metric.configure(config);
    }

    @Test
    void testAssess_OnlyIntsListFields() {
        // This test only accesses IntsList fields
        String testCase = """
            @Test
            public void test1() {
                IntsList l = new IntsList();
                l.add(10);
                l.add(20);
                assertEquals(2, l.getSize());
            }
            """;
        String oracle = "assertEquals(2, l.getSize());";
        
        double score = metric.assess(testCase, oracle);
        // Only size field from IntsList is accessed (1 out of 10 total fields)
        // IntsList: header, size, next, item = 4 fields
        // Person: name, age, address, Address.street, Address.city, Address.name = 6 fields
        // Total = 10 fields
        assertEquals(0.1, score, 0.01, "Should be 0.1 when only 1 out of 10 fields is accessed");
    }

    @Test
    void testAssess_OnlyPersonFields() {
        // This test only accesses Person fields
        String testCase = """
            @Test
            public void test2() {
                Person p = new Person("John", 30);
                assertEquals("John", p.getName());
            }
            """;
        String oracle = "assertEquals(\"John\", p.getName());";
        
        double score = metric.assess(testCase, oracle);
        
        // getName() matches both Person.getName() and Address.getName()
        // This is because the metric conservatively considers all methods with the same name
        // when it cannot determine which specific method is being called (no type inference).
        // So both Person.name and Address.name are marked as accessed.
        // Total: 2 out of 10 fields
        assertEquals(0.2, score, 0.01, "Should be 0.2 when 2 name fields are accessed (conservative matching)");
    }

    @Test
    void testAssess_BothClassesFields() {
        // This test accesses fields from both classes
        String testCase = """
            @Test
            public void test3() {
                IntsList l = new IntsList();
                l.add(5);
                Person p = new Person("Alice", 25);
                assertTrue(l.getSize() == 1 && p.getName().equals("Alice"));
            }
            """;
        String oracle = "assertTrue(l.getSize() == 1 && p.getName().equals(\"Alice\"));";
        
        double score = metric.assess(testCase, oracle);
        // size field from IntsList and both name fields from Person (2 name fields due to conservative matching)
        // Total accessed: 3 out of 10 total fields
        assertEquals(0.3, score, 0.01, "Should be 0.3 when 3 out of 10 fields are accessed");
    }

    @Test
    void testAssess_MultipleFieldsFromBothClasses() {
        // This test accesses multiple fields from both classes
        String testCase = """
            @Test
            public void test4() {
                IntsList l = new IntsList();
                l.add(1);
                l.add(2);
                Person p = new Person("Bob", 40);
                p.setAddress(new Person.Address("Home", "123 Main St", "Springfield"));
                assertTrue(l.getHeader().item == 1 && l.getSize() == 2 && 
                           p.getName().equals("Bob") && p.getAge() == 40 &&
                           p.getAddress().getCity().equals("Springfield"));
            }
            """;
        String oracle = "assertTrue(l.getHeader().item == 1 && l.getSize() == 2 && p.getName().equals(\"Bob\") && p.getAge() == 40 && p.getAddress().getCity().equals(\"Springfield\"));";
        
        double score = metric.assess(testCase, oracle);
        // From IntsList: header, size, item (3 fields)
        // From Person: name (2 fields due to conservative matching), age, address, Address.city (4 more fields)
        // Total accessed: 8 out of 10 fields (conservative getName matches both name fields)
        assertEquals(0.8, score, 0.01, "Should be 0.8 when 8 out of 10 fields are accessed");
    }

    @Test
    void testAssess_AllFieldsFromBothClasses() {
        // This test accesses all fields from both classes
        String testCase = """
            @Test
            public void test5() {
                IntsList l = new IntsList();
                l.add(1);
                l.add(2);
                Person p = new Person("Charlie", 35);
                p.setAddress(new Person.Address("Work", "456 Oak Ave", "Boston"));
                assertTrue(l.getHeader().item == 1 && l.getHeader().next.item == 2 && l.getSize() == 2 && 
                           p.getName().equals("Charlie") && p.getAge() == 35 &&
                           p.getAddress().getName().equals("Work") &&
                           p.getAddress().getStreet().equals("456 Oak Ave") &&
                           p.getAddress().getCity().equals("Boston"));
            }
            """;
        String oracle = "assertTrue(l.getHeader().item == 1 && l.getHeader().next.item == 2 && l.getSize() == 2 && p.getName().equals(\"Charlie\") && p.getAge() == 35 && p.getAddress().getName().equals(\"Work\") && p.getAddress().getStreet().equals(\"456 Oak Ave\") && p.getAddress().getCity().equals(\"Boston\"));";
        
        double score = metric.assess(testCase, oracle);
        // All fields from both classes are accessed
        // IntsList: header, size, next, item (4 fields)
        // Person: name, age, address, Address.name, Address.street, Address.city (6 fields)
        // Total: 10 fields
        assertEquals(1.0, score, 0.01, "Should be 1.0 when all fields are accessed");
    }

    @Test
    void testGetTargetClassPaths() {
        // Verify that getTargetClassPaths returns both classes
        var paths = metric.getTargetClassPaths();
        assertEquals(2, paths.size(), "Should have 2 target class paths");
        assertTrue(paths.contains("src/test/resources/IntsList.java"), "Should contain IntsList.java");
        assertTrue(paths.contains("src/test/resources/Person.java"), "Should contain Person.java");
    }

    @Test
    void testGetTargetClassPath_BackwardCompatibility() {
        // Verify backward compatibility method returns the first class
        String path = metric.getTargetClassPath();
        assertEquals("src/test/resources/IntsList.java", path, 
                     "Backward compatibility method should return the first target class");
    }

    @Test
    void testDebug_CheckFieldCount() {
        // Debug test to see actual field counts
        
        // Run a simple assessment to populate the fields
        String testCase = """
            @Test
            public void test() {
                assertTrue(true);
            }
            """;
        String oracle = "assertTrue(true);";
        
        metric.assess(testCase, oracle);
        var targetFields = metric.getLastTargetFields();
        
        // Verify we have the expected 10 fields
        assertEquals(10, targetFields.size(), "Should have 10 total target fields from both classes");
        
        // Verify the fields are from both IntsList and Person
        assertTrue(targetFields.stream().anyMatch(f -> f.contains("IntsList")), "Should have IntsList fields");
        assertTrue(targetFields.stream().anyMatch(f -> f.contains("Person")), "Should have Person fields");
    }

    @Test
    void testSingleClassStillWorks() {
        // Test that single class configuration still works
        StateFieldCoverage singleMetric = new StateFieldCoverageJava();
        singleMetric.setDetailedReportingEnabled(false);
        
        Properties config = new Properties();
        config.setProperty("target_class", "src/test/resources/IntsList.java");
        config.setProperty("iterable_field_tracking", "false");
        singleMetric.configure(config);
        
        String testCase = """
            @Test
            public void test1() {
                IntsList l = new IntsList();
                l.add(10);
                l.add(20);
                assertEquals(2, l.getSize());
            }
            """;
        String oracle = "assertEquals(2, l.getSize());";
        
        double score = singleMetric.assess(testCase, oracle);
        // Only size field is accessed out of 4 total IntsList fields
        assertEquals(0.25, score, 0.01, "Should be 0.25 when only size field is accessed (single class)");
    }
}

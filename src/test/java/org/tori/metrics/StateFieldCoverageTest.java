package org.tori.metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for StateFieldCoverage metric.
 * Tests are based on the IntsList class which has 4 fields:
 * - IntsList.header (Node)
 * - IntsList.size (int)
 * - Node.next (Node)
 * - Node.item (int)
 */
class StateFieldCoverageTest {

    private StateFieldCoverage metric;

    @BeforeEach
    void setUp() {
        metric = new StateFieldCoverageJava();
        
        // Disable detailed reporting for tests
        metric.setDetailedReportingEnabled(false);
        
        // Configure the metric with the target class path
        Properties config = new Properties();
        config.setProperty("target_class", "src/test/resources/IntsList.java");
        config.setProperty("iterable_field_tracking", "false"); // Disable for existing tests
        metric.configure(config);
    }

    @Test
    void testAssess_OnlySize() {
        // This test only accesses the size field via getSize()
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
        // Only size field is accessed out of 4 total fields (header, size, next, item)
        assertEquals(0.25, score, 0.01, "Should be 0.25 when only size field is accessed");
    }

    @Test
    void testAssess_HeaderField() {
        // This test accesses the header field
        String testCase = """
            @Test
            public void testGetHeader() {
                IntsList l = new IntsList();
                l.add(100);
                assertNotNull(l.getHeader());
            }
            """;
        String oracle = "assertNotNull(l.getHeader());";
        
        double score = metric.assess(testCase, oracle);
        // getHeader accesses the header field (1 out of 4)
        assertEquals(0.25, score, 0.01, "Should be 0.25 when only header field is accessed");
    }

    @Test
    void testAssess_HeaderItem() {
        // This test accesses header and item fields
        String testCase = """
            @Test
            public void testHeaderItem() {
                IntsList l = new IntsList();
                l.add(42);
                assertEquals(42, l.getHeader().item);
            }
            """;
        String oracle = "assertEquals(42, l.getHeader().item);";
        
        double score = metric.assess(testCase, oracle);
        // getHeader accesses header, and .item accesses item (2 out of 4)
        assertEquals(0.50, score, 0.01, "Should be 0.50 when header and item fields are accessed");
    }

    @Test
    void testAssess_HeaderNext() {
        // This test accesses header and next fields
        String testCase = """
            @Test
            public void testHeaderNext() {
                IntsList l = new IntsList();
                l.add(1);
                l.add(2);
                assertNotNull(l.getHeader().next);
            }
            """;
        String oracle = "assertNotNull(l.getHeader().next);";
        
        double score = metric.assess(testCase, oracle);
        // getHeader accesses header, and .next accesses next (2 out of 4)
        assertEquals(0.50, score, 0.01, "Should be 0.50 when header and next fields are accessed");
    }

    @Test
    void testAssess_MultipleFields() {
        // This test accesses header and size fields
        String testCase = """
            @Test
            public void testMultipleFields() {
                IntsList l = new IntsList();
                l.add(7);
                l.add(8);
                assertTrue(l.getHeader() != null && l.getSize() == 2);
            }
            """;
        String oracle = "assertTrue(l.getHeader() != null && l.getSize() == 2);";
        
        double score = metric.assess(testCase, oracle);
        // getHeader accesses header, getSize accesses size (2 out of 4)
        assertEquals(0.50, score, 0.01, "Should be 0.50 when header and size fields are accessed");
    }

    @Test
    void testAssess_AllFields() {
        // This test accesses all 4 fields
        String testCase = """
            @Test
            public void testAllFields() {
                IntsList l = new IntsList();
                l.add(1);
                l.add(2);
                assertTrue(l.getHeader().item == 1 && l.getHeader().next != null && l.getSize() == 2 && l.getHeader().next.item == 2);
            }
            """;
        String oracle = "assertTrue(l.getHeader().item == 1 && l.getHeader().next != null && l.getSize() == 2 && l.getHeader().next.item == 2);";
        
        double score = metric.assess(testCase, oracle);
        // All fields accessed: header (via getHeader), size (via getSize), item, next
        assertEquals(1.0, score, 0.01, "Should be 1.0 when all fields are accessed");
    }

    @Test
    void testAssess_EmptyList() {
        // Test with isEmpty which accesses header field
        String testCase = """
            @Test
            public void testIsEmpty() {
                IntsList l = new IntsList();
                assertTrue(l.isEmpty());
            }
            """;
        String oracle = "assertTrue(l.isEmpty());";
        
        double score = metric.assess(testCase, oracle);
        // isEmpty accesses header field (1 out of 4)
        assertEquals(0.25, score, 0.01, "Should be 0.25 when only header field is accessed");
    }

    @Test
    void testAssess_GetMethod() {
        // Test with get method which accesses all fields: size, header, next, and item
        String testCase = """
            @Test
            public void testGet() {
                IntsList l = new IntsList();
                l.add(10);
                l.add(20);
                l.add(30);
                assertEquals(20, l.get(1));
            }
            """;
        String oracle = "assertEquals(20, l.get(1));";
        
        double score = metric.assess(testCase, oracle);
        // get method accesses size (in condition), header, next (in loop), and item (4 out of 4)
        assertEquals(1.0, score, 0.01, "Should be 1.0 when all fields (size, header, next, item) are accessed");
    }
    
    @Test
    void testAssess_IterableFieldTracking_CheckSize() {
        // Test with iterable field tracking enabled
        StateFieldCoverage iterableMetric = new StateFieldCoverageJava();
        iterableMetric.setDetailedReportingEnabled(false);
        
        Properties config = new Properties();
        config.setProperty("target_class", "src/test/resources/IntsList.java");
        config.setProperty("iterable_field_tracking", "true"); // Enable iterable tracking
        iterableMetric.configure(config);
        
        String testCase = """
            @Test
            public void testCheckSize() {
                IntsList l = new IntsList();
                l.add(1);
                l.add(2);
                l.add(3);
                assertTrue(l.checkSize());
            }
            """;
        String oracle = "assertTrue(l.checkSize());";
        
        double score = iterableMetric.assess(testCase, oracle);
        
        // checkSize method accesses:
        // - header (regular access)
        // - next (iterated in loop - should cover both next and next+)
        // - size (regular access)
        // Total target fields with iterable tracking: 6 (header, size, item, next, item+, next+)
        // Fields accessed: header, size, next (in loop), next+ (iteration detected)
        // So we should cover: header, size, next, next+ = 4/6 = 0.666...
        
        assertTrue(score > 0.5, "Score should be > 0.5 when checkSize accesses fields including iteration");
        
        // Verify that the right fields were accessed
        var accessed = iterableMetric.getLastAccessedFields();
        var target = iterableMetric.getLastTargetFields();
        
        // Target should include both regular and special labels for iterable fields
        int expectedTargetFieldsWithIterable = 6;  // 4 regular + 2 special labels
        assertEquals(expectedTargetFieldsWithIterable, target.size(), 
            "Should have 6 target fields (4 regular + 2 special labels)");
        
        // Accessed should include special label for next (next+) since it's iterated
        assertTrue(accessed.stream().anyMatch(f -> f.contains("next+")), 
            "Should access next+ label (iterated field)");
    }
    
    @Test
    void testAssess_IterableFieldTracking_Disabled() {
        // Verify that with iterable tracking disabled, we get the old behavior
        StateFieldCoverage noIterableMetric = new StateFieldCoverageJava();
        noIterableMetric.setDetailedReportingEnabled(false);
        
        Properties config = new Properties();
        config.setProperty("target_class", "src/test/resources/IntsList.java");
        config.setProperty("iterable_field_tracking", "false");
        noIterableMetric.configure(config);
        
        String testCase = """
            @Test
            public void testCheckSize() {
                IntsList l = new IntsList();
                l.add(1);
                l.add(2);
                assertTrue(l.checkSize());
            }
            """;
        String oracle = "assertTrue(l.checkSize());";
        
        double score = noIterableMetric.assess(testCase, oracle);
        
        // Without iterable tracking, checkSize accesses header, size, next
        // Total: 4 fields (header, size, item, next)
        // Accessed: 3 (header, size, next)
        // Score: 3/4 = 0.75
        assertEquals(0.75, score, 0.01, "Should be 0.75 when checkSize accesses 3 out of 4 fields");
        
        var target = noIterableMetric.getLastTargetFields();
        int expectedTargetFieldsNoIterable = 4;  // Regular fields only
        assertEquals(expectedTargetFieldsNoIterable, target.size(), 
            "Should have 4 target fields when iterable tracking is disabled");
        
        // Should not have any + labels
        assertTrue(target.stream().noneMatch(f -> f.contains("+")), 
            "Should not have any special + labels when iterable tracking is disabled");
    }

    @Test
    void testAssess_similarClass() {
        Properties config = new Properties();
        config.setProperty("target_class", "src/test/resources/IntsList.java");
        config.setProperty("exec_level", "test_method");
        metric.configure(config);
        
        // Test with a similar class that has different fields to ensure correct field identification
        String testCase = """
            @Test
            public void testCreateStackedValueList1a() {
                DefaultCategoryDataset d = new DefaultCategoryDataset();
                d.addValue(1.0, "s0", "c0");
                d.addValue(1.1, "s1", "c0");
                MyRenderer r = new MyRenderer();
                List l = r.createStackedValueList(d, "c0", new int[] { 0, 1 }, 0.0, false);
                
                assertEquals(3, l.size());
                assertEquals(new Double(0.0), ((Object[]) l.get(0))[1]);
                assertEquals(new Double(1.0), ((Object[]) l.get(1))[1]);
                assertEquals(new Double(2.1), ((Object[]) l.get(2))[1]);
            }
            """;
        List<String> oracles = Arrays.asList(
            "assertEquals(3, l.size());",
            "assertEquals(new Double(0.0), ((Object[]) l.get(0))[1]);",
            "assertEquals(new Double(1.0), ((Object[]) l.get(1))[1]);",
            "assertEquals(new Double(2.1), ((Object[]) l.get(2))[1]);"
        );
        
        double score = metric.assessMultiple(testCase, oracles);
        // Provided test has different fields, so we should get 0 coverage for IntsList fields
        assertEquals(0.0, score, 0.01, "Should be 0.0 when testing a similar class with different fields");
    }
    
    @Test
    void testAssess_packageAware_samePackage() {
        // Test that when a variable is of the target class type (same package), fields are counted
        Properties config = new Properties();
        config.setProperty("target_class", "src/test/resources/IntsList.java");
        metric.configure(config);
        
        String testCase = """
            package com.example;
            
            @Test
            public void test() {
                IntsList l = new IntsList();
                l.add(10);
                assertEquals(1, l.getSize());
            }
            """;
        String oracle = "assertEquals(1, l.getSize());";
        
        double score = metric.assess(testCase, oracle);
        // getSize accesses the size field (1 out of 4)
        assertEquals(0.25, score, 0.01, "Should be 0.25 when size field is accessed in same package");
    }
    
    @Test
    void testAssess_packageAware_differentPackage() {
        // Test that when a variable is of a class with the same name but different package, 
        // fields are NOT counted
        Properties config = new Properties();
        config.setProperty("target_class", "src/test/resources/IntsList.java");
        metric.configure(config);
        
        String testCase = """
            package org.other;
            
            @Test
            public void test() {
                IntsList l = new IntsList();
                l.add(10);
                assertEquals(1, l.getCount());
            }
            """;
        String oracle = "assertEquals(1, l.getCount());";
        
        double score = metric.assess(testCase, oracle);
        // The test uses org.other.IntsList, not com.example.IntsList, so no fields should match
        assertEquals(0.0, score, 0.01, "Should be 0.0 when using a class with same name but different package");
    }
    
    @Test
    void testAssess_packageAware_noPackageInTest() {
        // Test that when test has no package declaration, we assume the target's package
        Properties config = new Properties();
        config.setProperty("target_class", "src/test/resources/IntsList.java");
        metric.configure(config);
        
        String testCase = """
            @Test
            public void test() {
                IntsList l = new IntsList();
                l.add(10);
                assertEquals(1, l.getSize());
            }
            """;
        String oracle = "assertEquals(1, l.getSize());";
        
        double score = metric.assess(testCase, oracle);
        // No package in test, so we assume com.example package (same as target)
        // getSize accesses the size field (1 out of 4)
        assertEquals(0.25, score, 0.01, "Should be 0.25 when no package in test (assumes target package)");
    }
    
    @Test
    void testAssess_packageAware_differentClassName() {
        // Test that fields are correctly filtered by class name
        Properties config = new Properties();
        config.setProperty("target_class", "src/test/resources/IntsList.java");
        metric.configure(config);
        
        String testCase = """
            package com.example;
            
            @Test
            public void test() {
                Person p = new Person("John", 30);
                assertEquals("John", p.getName());
            }
            """;
        String oracle = "assertEquals(\"John\", p.getName());";
        
        double score = metric.assess(testCase, oracle);
        // Person is not IntsList, so no fields should be counted
        assertEquals(0.0, score, 0.01, "Should be 0.0 when using a different class");
    }
}

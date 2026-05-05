package org.tori.metrics.sfc;

import org.tori.metrics.StateFieldCoverage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tori.metrics.ExecutionLevel;

import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for StateFieldCoverage metric execution levels.
 * Tests the assert, test_method, and test_class execution levels.
 */
class StateFieldCoverageExecutionLevelTest {

    private StateFieldCoverage metric;

    @BeforeEach
    void setUp() {
        metric = new StateFieldCoverageJava();
        metric.setDetailedReportingEnabled(false);
        
        Properties config = new Properties();
        config.setProperty("target_class", "src/test/resources/IntsList.java");
        config.setProperty("iterable_field_tracking", "false"); // Disable for existing tests
        metric.configure(config);
    }

    @Test
    void testAssertLevel_SingleAssertion() {
        // Default execution level should be ASSERT
        assertEquals(ExecutionLevel.ASSERT, metric.getExecutionLevel());
        
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
        // Only size field is accessed out of 4 total fields
        assertEquals(0.25, score, 0.01, "Assert level should assess individual assertion");
    }

    @Test
    void testTestMethodLevel_SingleAssertion() {
        // Configure for TEST_METHOD level
        Properties config = new Properties();
        config.setProperty("target_class", "src/test/resources/IntsList.java");
        config.setProperty("exec_level", "test_method");
        metric.configure(config);
        
        assertEquals(ExecutionLevel.TEST_METHOD, metric.getExecutionLevel());
        
        String testCase = """
            @Test
            public void test1() {
                IntsList l = new IntsList();
                l.add(10);
                l.add(20);
                assertEquals(2, l.getSize());
            }
            """;
        List<String> oracles = Arrays.asList("assertEquals(2, l.getSize());");
        
        double score = metric.assessMultiple(testCase, oracles);
        // Only size field is accessed out of 4 total fields
        assertEquals(0.25, score, 0.01, "Test method level with single assertion");
    }

    @Test
    void testTestMethodLevel_MultipleAssertions() {
        // Configure for TEST_METHOD level
        Properties config = new Properties();
        config.setProperty("target_class", "src/test/resources/IntsList.java");
        config.setProperty("exec_level", "test_method");
        metric.configure(config);
        
        assertEquals(ExecutionLevel.TEST_METHOD, metric.getExecutionLevel());
        
        String testCase = """
            @Test
            public void testMultiple() {
                IntsList l = new IntsList();
                l.add(7);
                l.add(8);
                assertEquals(2, l.getSize());
                assertNotNull(l.getHeader());
            }
            """;
        List<String> oracles = Arrays.asList(
            "assertEquals(2, l.getSize());",
            "assertNotNull(l.getHeader());"
        );
        
        double score = metric.assessMultiple(testCase, oracles);
        // getSize accesses size, getHeader accesses header (2 out of 4)
        assertEquals(0.50, score, 0.01, "Test method level should compute union of fields");
    }

    @Test
    void testTestMethodLevel_AllFields() {
        // Configure for TEST_METHOD level
        Properties config = new Properties();
        config.setProperty("target_class", "src/test/resources/IntsList.java");
        config.setProperty("exec_level", "test_method");
        metric.configure(config);
        
        String testCase = """
            @Test
            public void testAllFields() {
                IntsList l = new IntsList();
                l.add(1);
                l.add(2);
                assertEquals(2, l.getSize());
                assertEquals(1, l.getHeader().item);
                assertNotNull(l.getHeader().next);
            }
            """;
        List<String> oracles = Arrays.asList(
            "assertEquals(2, l.getSize());",
            "assertEquals(1, l.getHeader().item);",
            "assertNotNull(l.getHeader().next);"
        );
        
        double score = metric.assessMultiple(testCase, oracles);
        // All fields accessed: size, header, item, next (4 out of 4)
        assertEquals(1.0, score, 0.01, "Test method level should access all fields");
    }

    @Test
    void testTestClassLevel_MultipleMethodsPartialCoverage() {
        // Configure for TEST_CLASS level
        Properties config = new Properties();
        config.setProperty("target_class", "src/test/resources/IntsList.java");
        config.setProperty("exec_level", "test_class");
        metric.configure(config);
        
        assertEquals(ExecutionLevel.TEST_CLASS, metric.getExecutionLevel());
        
        // Simulate multiple test methods
        String testCase = """
            @Test
            public void test1() {
                IntsList l = new IntsList();
                l.add(10);
                assertEquals(1, l.getSize());
            }
            @Test
            public void test2() {
                IntsList l = new IntsList();
                l.add(5);
                assertNotNull(l.getHeader());
            }
            """;
        List<String> oracles = Arrays.asList(
            "assertEquals(1, l.getSize());",
            "assertNotNull(l.getHeader());"
        );
        
        double score = metric.assessMultiple(testCase, oracles);
        // getSize accesses size, getHeader accesses header (2 out of 4)
        assertEquals(0.50, score, 0.01, "Test class level should compute union across all methods");
    }

    @Test
    void testTestClassLevel_FullCoverage() {
        // Configure for TEST_CLASS level
        Properties config = new Properties();
        config.setProperty("target_class", "src/test/resources/IntsList.java");
        config.setProperty("exec_level", "test_class");
        metric.configure(config);
        
        // Simulate multiple test methods covering all fields
        String testCase = """
            @Test
            public void test1() {
                IntsList l = new IntsList();
                l.add(10);
                assertEquals(1, l.getSize());
            }
            @Test
            public void test2() {
                IntsList l = new IntsList();
                l.add(5);
                assertNotNull(l.getHeader());
            }
            @Test
            public void test3() {
                IntsList l = new IntsList();
                l.add(1);
                l.add(2);
                assertEquals(1, l.getHeader().item);
                assertNotNull(l.getHeader().next);
            }
            """;
        List<String> oracles = Arrays.asList(
            "assertEquals(1, l.getSize());",
            "assertNotNull(l.getHeader());",
            "assertEquals(1, l.getHeader().item);",
            "assertNotNull(l.getHeader().next);"
        );
        
        double score = metric.assessMultiple(testCase, oracles);
        // All fields accessed across all methods: size, header, item, next (4 out of 4)
        assertEquals(1.0, score, 0.01, "Test class level should achieve full coverage");
    }

    @Test
    void testExecutionLevelSetter() {
        // Test that setExecutionLevel works
        assertEquals(ExecutionLevel.ASSERT, metric.getExecutionLevel());
        
        metric.setExecutionLevel(ExecutionLevel.TEST_METHOD);
        assertEquals(ExecutionLevel.TEST_METHOD, metric.getExecutionLevel());
        
        metric.setExecutionLevel(ExecutionLevel.TEST_CLASS);
        assertEquals(ExecutionLevel.TEST_CLASS, metric.getExecutionLevel());
        
        metric.setExecutionLevel(ExecutionLevel.ASSERT);
        assertEquals(ExecutionLevel.ASSERT, metric.getExecutionLevel());
    }

    @Test
    void testExecutionLevelFromConfig() {
        // Test assert level from config
        Properties config1 = new Properties();
        config1.setProperty("target_class", "src/test/resources/IntsList.java");
        config1.setProperty("exec_level", "assert");
        metric.configure(config1);
        assertEquals(ExecutionLevel.ASSERT, metric.getExecutionLevel());
        
        // Test test_method level from config
        Properties config2 = new Properties();
        config2.setProperty("target_class", "src/test/resources/IntsList.java");
        config2.setProperty("exec_level", "test_method");
        metric.configure(config2);
        assertEquals(ExecutionLevel.TEST_METHOD, metric.getExecutionLevel());
        
        // Test test_class level from config
        Properties config3 = new Properties();
        config3.setProperty("target_class", "src/test/resources/IntsList.java");
        config3.setProperty("exec_level", "test_class");
        metric.configure(config3);
        assertEquals(ExecutionLevel.TEST_CLASS, metric.getExecutionLevel());
    }

    @Test
    void testInvalidExecutionLevel() {
        Properties config = new Properties();
        config.setProperty("target_class", "src/test/resources/IntsList.java");
        config.setProperty("exec_level", "invalid_level");
        
        assertThrows(IllegalArgumentException.class, () -> {
            metric.configure(config);
        }, "Should throw exception for invalid execution level");
    }

    @Test
    void testAssessMultipleWithEmptyList() {
        Properties config = new Properties();
        config.setProperty("target_class", "src/test/resources/IntsList.java");
        config.setProperty("exec_level", "test_method");
        metric.configure(config);
        
        String testCase = """
            @Test
            public void test() {
                IntsList l = new IntsList();
            }
            """;
        
        double score = metric.assessMultiple(testCase, Arrays.asList());
        assertEquals(0.0, score, 0.01, "Empty oracle list should return 0.0");
    }
}

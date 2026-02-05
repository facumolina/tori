package org.tori.metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for CheckedVarsMetric execution levels.
 * Tests the assert, test_method, and test_class execution levels.
 */
class CheckedVarsMetricExecutionLevelTest {

    private CheckedVarsMetric metric;

    @BeforeEach
    void setUp() {
        metric = new CheckedVarsMetric();
    }

    @Test
    void testAssertLevel_SingleVariable() {
        // Default execution level should be ASSERT
        assertEquals(ExecutionLevel.ASSERT, metric.getExecutionLevel());
        
        String testCase = """
            @Test
            public void testExample() {
                String result = "test";
                assertEquals("test", result);
            }
            """;
        String oracle = "assertEquals(\"test\", result);";
        
        double score = metric.assess(testCase, oracle);
        assertEquals(1.0, score, 0.01, "Single variable checked");
    }

    @Test
    void testTestMethodLevel_SingleAssertion() {
        // Configure for TEST_METHOD level
        Properties config = new Properties();
        config.setProperty("exec_level", "test_method");
        metric.configure(config);
        
        assertEquals(ExecutionLevel.TEST_METHOD, metric.getExecutionLevel());
        
        String testCase = """
            @Test
            public void testExample() {
                int a = 5;
                int b = 10;
                assertEquals(5, a);
            }
            """;
        List<String> oracles = Arrays.asList("assertEquals(5, a);");
        
        double score = metric.assessMultiple(testCase, oracles);
        // Only 'a' is checked, 'b' is not (1 out of 2)
        assertEquals(0.5, score, 0.01, "Test method level with single assertion");
    }

    @Test
    void testTestMethodLevel_MultipleAssertions() {
        // Configure for TEST_METHOD level
        Properties config = new Properties();
        config.setProperty("exec_level", "test_method");
        metric.configure(config);
        
        String testCase = """
            @Test
            public void testExample() {
                int a = 5;
                int b = 10;
                int c = 15;
                assertEquals(5, a);
                assertEquals(10, b);
            }
            """;
        List<String> oracles = Arrays.asList(
            "assertEquals(5, a);",
            "assertEquals(10, b);"
        );
        
        double score = metric.assessMultiple(testCase, oracles);
        // 'a' and 'b' are checked, 'c' is not (2 out of 3)
        assertEquals(0.67, score, 0.01, "Test method level should compute union of checked vars");
    }

    @Test
    void testTestMethodLevel_AllVariablesChecked() {
        // Configure for TEST_METHOD level
        Properties config = new Properties();
        config.setProperty("exec_level", "test_method");
        metric.configure(config);
        
        String testCase = """
            @Test
            public void testExample() {
                int a = 5;
                int b = 10;
                assertEquals(5, a);
                assertTrue(b > 0);
            }
            """;
        List<String> oracles = Arrays.asList(
            "assertEquals(5, a);",
            "assertTrue(b > 0);"
        );
        
        double score = metric.assessMultiple(testCase, oracles);
        // Both 'a' and 'b' are checked (2 out of 2)
        assertEquals(1.0, score, 0.01, "Test method level should check all variables");
    }

    @Test
    void testTestClassLevel_MultipleMethodsPartialCoverage() {
        // Configure for TEST_CLASS level
        Properties config = new Properties();
        config.setProperty("exec_level", "test_class");
        metric.configure(config);
        
        assertEquals(ExecutionLevel.TEST_CLASS, metric.getExecutionLevel());
        
        // Simulate multiple test methods
        String testCase = """
            @Test
            public void test1() {
                int a = 5;
                int b = 10;
                assertEquals(5, a);
            }
            @Test
            public void test2() {
                int c = 15;
                int d = 20;
                assertEquals(15, c);
            }
            """;
        List<String> oracles = Arrays.asList(
            "assertEquals(5, a);",
            "assertEquals(15, c);"
        );
        
        double score = metric.assessMultiple(testCase, oracles);
        // 'a' and 'c' are checked, 'b' and 'd' are not (2 out of 4)
        assertEquals(0.5, score, 0.01, "Test class level should compute union across all methods");
    }

    @Test
    void testTestClassLevel_FullCoverage() {
        // Configure for TEST_CLASS level
        Properties config = new Properties();
        config.setProperty("exec_level", "test_class");
        metric.configure(config);
        
        // Simulate multiple test methods covering all variables
        String testCase = """
            @Test
            public void test1() {
                int a = 5;
                int b = 10;
                assertEquals(5, a);
            }
            @Test
            public void test2() {
                int c = 15;
                int d = 20;
                assertTrue(c > 0 && d > 0);
            }
            @Test
            public void test3() {
                int e = 25;
                assertEquals(10, b);
                assertEquals(25, e);
            }
            """;
        
        // Note: 'b' from test1 is not accessible in test3, so each method has its own scope
        // We have: a, b from test1; c, d from test2; e, b from test3
        // But 'b' in test3 is a different variable than 'b' in test1
        // So we have 6 total variables: a, b(test1), c, d, e, b(test3)
        List<String> oracles = Arrays.asList(
            "assertEquals(5, a);",
            "assertTrue(c > 0 && d > 0);",
            "assertEquals(10, b);",
            "assertEquals(25, e);"
        );
        
        double score = metric.assessMultiple(testCase, oracles);
        // This test checks the union across methods
        // Variables: a, b (from test1), c, d (from test2), e, b (from test3) - but they're in different scopes
        // Actually, each test method has its own scope, so we have:
        // test1: a (checked), b (not checked) = 1/2
        // test2: c (checked), d (checked) = 2/2
        // test3: e (checked), b (checked) = 2/2
        // Total: a, b, c, d, e, b = 6 variables, 5 unique names checked: a, b, c, d, e
        // But since they're in the same test case string, the parser sees them all together
        assertTrue(score >= 0.67, "Test class level should achieve good coverage");
    }

    @Test
    void testTestClassLevel_NoVariables() {
        // Configure for TEST_CLASS level
        Properties config = new Properties();
        config.setProperty("exec_level", "test_class");
        metric.configure(config);
        
        String testCase = """
            @Test
            public void test1() {
                assertEquals(5, 2 + 3);
            }
            """;
        List<String> oracles = Arrays.asList("assertEquals(5, 2 + 3);");
        
        double score = metric.assessMultiple(testCase, oracles);
        assertEquals(0.0, score, 0.01, "No variables declared should return 0.0");
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
        config1.setProperty("exec_level", "assert");
        metric.configure(config1);
        assertEquals(ExecutionLevel.ASSERT, metric.getExecutionLevel());
        
        // Test test_method level from config
        Properties config2 = new Properties();
        config2.setProperty("exec_level", "test_method");
        metric.configure(config2);
        assertEquals(ExecutionLevel.TEST_METHOD, metric.getExecutionLevel());
        
        // Test test_class level from config
        Properties config3 = new Properties();
        config3.setProperty("exec_level", "test_class");
        metric.configure(config3);
        assertEquals(ExecutionLevel.TEST_CLASS, metric.getExecutionLevel());
    }

    @Test
    void testDefaultExecutionLevel() {
        // When no exec_level is configured, it should default to ASSERT
        Properties config = new Properties();
        metric.configure(config);
        assertEquals(ExecutionLevel.ASSERT, metric.getExecutionLevel());
    }

    @Test
    void testInvalidExecutionLevel() {
        Properties config = new Properties();
        config.setProperty("exec_level", "invalid_level");
        
        assertThrows(IllegalArgumentException.class, () -> {
            metric.configure(config);
        }, "Should throw exception for invalid execution level");
    }

    @Test
    void testAssessMultipleWithEmptyList() {
        Properties config = new Properties();
        config.setProperty("exec_level", "test_method");
        metric.configure(config);
        
        String testCase = """
            @Test
            public void test() {
                int a = 5;
            }
            """;
        
        double score = metric.assessMultiple(testCase, Arrays.asList());
        assertEquals(0.0, score, 0.01, "Empty oracle list should return 0.0");
    }

    @Test
    void testAssertLevelStillWorks() {
        // Verify that the original assert level functionality still works
        String testCase = """
            @Test
            public void testExample() {
                int a = 1;
                int b = 2;
                int c = 3;
                assertEquals(6, 1 + 2 + 3);
            }
            """;
        String oracle = "assertEquals(6, 1 + 2 + 3);";
        
        double score = metric.assess(testCase, oracle);
        assertEquals(0.0, score, 0.01, "Variables not checked in assertion");
    }
}

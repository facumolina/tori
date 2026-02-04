package org.tori.metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for CheckedVarsMetric.
 */
class CheckedVarsMetricTest {

    private CheckedVarsMetric metric;

    @BeforeEach
    void setUp() {
        metric = new CheckedVarsMetric();
    }

    @Test
    void testAssess_SingleVariableChecked() {
        String testCase = """
            @Test
            public void testExample() {
                String result = "test";
                assertEquals("test", result);
            }
            """;
        String oracle = "assertEquals(\"test\", result);";
        
        double score = metric.assess(testCase, oracle);
        assertEquals(1.0, score, 0.01, "Should be 1.0 when the only declared variable is checked");
    }

    @Test
    void testAssess_TwoVariablesOneChecked() {
        String testCase = """
            @Test
            public void testExample() {
                Calculator calc = new Calculator();
                int result = calc.add(2, 3);
                assertEquals(5, result);
            }
            """;
        String oracle = "assertEquals(5, result);";
        
        double score = metric.assess(testCase, oracle);
        assertEquals(0.5, score, 0.01, "Should be 0.5 when 1 out of 2 variables is checked");
    }

    @Test
    void testAssess_TwoVariablesBothChecked() {
        String testCase = """
            @Test
            public void testExample() {
                Calculator calc = new Calculator();
                int result = calc.add(2, 3);
                assertNotNull(calc);
            }
            """;
        String oracle = "assertNotNull(calc);";
        
        double score = metric.assess(testCase, oracle);
        assertEquals(0.5, score, 0.01, "Should be 0.5 when 1 out of 2 variables is checked");
    }

    @Test
    void testAssess_NoVariablesDeclared() {
        String testCase = """
            @Test
            public void testExample() {
                assertEquals(5, 2 + 3);
            }
            """;
        String oracle = "assertEquals(5, 2 + 3);";
        
        double score = metric.assess(testCase, oracle);
        assertEquals(0.0, score, 0.01, "Should be 0.0 when no variables are declared");
    }

    @Test
    void testAssess_MultipleVariablesNoneChecked() {
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
        assertEquals(0.0, score, 0.01, "Should be 0.0 when declared variables are not checked");
    }

    @Test
    void testAssess_ComplexAssertion() {
        String testCase = """
            @Test
            public void testStringComparison() {
                String str1 = "test";
                String str2 = "test";
                String str3 = new String("test");
                
                assertEquals(str1, str2);
            }
            """;
        String oracle = "assertEquals(str1, str2);";
        
        double score = metric.assess(testCase, oracle);
        assertEquals(0.67, score, 0.01, "Should be 2/3 when 2 out of 3 variables are checked");
    }
}

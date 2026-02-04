package org.tori;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for TestOracleInspector using example files from resources.
 */
class TestOracleInspectorTest {
    
    private TestOracleInspector inspector;
    private String calculatorTestContent;
    private String stringUtilsTestContent;
    
    @BeforeEach
    void setUp() throws IOException {
        inspector = new TestOracleInspector();
        
        // Load test files from classpath resources
        calculatorTestContent = loadResourceAsString("/CalculatorTest.java");
        stringUtilsTestContent = loadResourceAsString("/StringUtilsTest.java");
    }
    
    /**
     * Load a resource file from classpath as a string.
     */
    private String loadResourceAsString(String resourcePath) throws IOException {
        try (InputStream inputStream = getClass().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
    
    @Test
    void testCalculatorTest_AllMethods() {
        List<MethodOracles> results = inspector.findOracles(calculatorTestContent, null);
        
        // Should find 5 test methods
        assertEquals(5, results.size(), "Should find 5 test methods in CalculatorTest");
        
        // Verify each method and its assertion count
        MethodOracles testAddition = findMethod(results, "testAddition");
        assertNotNull(testAddition, "testAddition method should be found");
        assertEquals(2, testAddition.oracles().size(), "testAddition should have 2 assertions");
        
        MethodOracles testSubtraction = findMethod(results, "testSubtraction");
        assertNotNull(testSubtraction, "testSubtraction method should be found");
        assertEquals(2, testSubtraction.oracles().size(), "testSubtraction should have 2 assertions");
        
        MethodOracles testMultiplication = findMethod(results, "testMultiplication");
        assertNotNull(testMultiplication, "testMultiplication method should be found");
        assertEquals(2, testMultiplication.oracles().size(), "testMultiplication should have 2 assertions");
        
        MethodOracles testDivision = findMethod(results, "testDivision");
        assertNotNull(testDivision, "testDivision method should be found");
        assertEquals(1, testDivision.oracles().size(), "testDivision should have 1 assertion");
        
        MethodOracles testWithoutAssertions = findMethod(results, "testWithoutAssertions");
        assertNotNull(testWithoutAssertions, "testWithoutAssertions method should be found");
        assertEquals(0, testWithoutAssertions.oracles().size(), "testWithoutAssertions should have 0 assertions");
    }
    
    @Test
    void testCalculatorTest_SpecificMethod() {
        List<MethodOracles> results = inspector.findOracles(calculatorTestContent, "testAddition");
        
        // Should find only 1 method
        assertEquals(1, results.size(), "Should find only testAddition method");
        
        MethodOracles testAddition = results.get(0);
        assertEquals("testAddition", testAddition.methodName());
        assertEquals(2, testAddition.oracles().size(), "testAddition should have 2 assertions");
        
        // Verify the assertions contain expected keywords
        List<String> oracles = testAddition.oracles();
        assertTrue(oracles.stream().anyMatch(o -> o.contains("assertEquals")), 
                   "Should contain assertEquals");
        assertTrue(oracles.stream().anyMatch(o -> o.contains("assertTrue")), 
                   "Should contain assertTrue");
    }
    
    @Test
    void testStringUtilsTest_AllMethods() {
        List<MethodOracles> results = inspector.findOracles(stringUtilsTestContent, null);
        
        // Should find 7 test methods
        assertEquals(7, results.size(), "Should find 7 test methods in StringUtilsTest");
        
        // Verify each method and its assertion count
        MethodOracles testConcatenation = findMethod(results, "testConcatenation");
        assertNotNull(testConcatenation, "testConcatenation method should be found");
        assertEquals(3, testConcatenation.oracles().size(), "testConcatenation should have 3 assertions");
        
        MethodOracles testStringLength = findMethod(results, "testStringLength");
        assertNotNull(testStringLength, "testStringLength method should be found");
        assertEquals(2, testStringLength.oracles().size(), "testStringLength should have 2 assertions");
        
        MethodOracles testNullString = findMethod(results, "testNullString");
        assertNotNull(testNullString, "testNullString method should be found");
        assertEquals(1, testNullString.oracles().size(), "testNullString should have 1 assertion");
        
        MethodOracles testStringComparison = findMethod(results, "testStringComparison");
        assertNotNull(testStringComparison, "testStringComparison method should be found");
        assertEquals(4, testStringComparison.oracles().size(), "testStringComparison should have 4 assertions");
        
        MethodOracles testThrowsException = findMethod(results, "testThrowsException");
        assertNotNull(testThrowsException, "testThrowsException method should be found");
        assertEquals(1, testThrowsException.oracles().size(), "testThrowsException should have 1 assertion");
        
        MethodOracles testMultipleAssertions = findMethod(results, "testMultipleAssertions");
        assertNotNull(testMultipleAssertions, "testMultipleAssertions method should be found");
        assertEquals(5, testMultipleAssertions.oracles().size(), "testMultipleAssertions should have 5 assertions (1 assertAll + 4 nested)");
        
        MethodOracles testArrayEquals = findMethod(results, "testArrayEquals");
        assertNotNull(testArrayEquals, "testArrayEquals method should be found");
        assertEquals(1, testArrayEquals.oracles().size(), "testArrayEquals should have 1 assertion");
    }
    
    @Test
    void testStringUtilsTest_SpecificMethod() {
        List<MethodOracles> results = inspector.findOracles(stringUtilsTestContent, "testConcatenation");
        
        // Should find only 1 method
        assertEquals(1, results.size(), "Should find only testConcatenation method");
        
        MethodOracles testConcatenation = results.get(0);
        assertEquals("testConcatenation", testConcatenation.methodName());
        assertEquals(3, testConcatenation.oracles().size(), "testConcatenation should have 3 assertions");
        
        // Verify the assertions contain expected keywords
        List<String> oracles = testConcatenation.oracles();
        assertTrue(oracles.stream().anyMatch(o -> o.contains("assertEquals")), 
                   "Should contain assertEquals");
        assertTrue(oracles.stream().anyMatch(o -> o.contains("assertNotNull")), 
                   "Should contain assertNotNull");
        assertTrue(oracles.stream().anyMatch(o -> o.contains("assertTrue")), 
                   "Should contain assertTrue");
    }
    
    @Test
    void testNonExistentMethod() {
        List<MethodOracles> results = inspector.findOracles(calculatorTestContent, "nonExistentMethod");
        
        // Should find no methods
        assertEquals(0, results.size(), "Should find no methods with name 'nonExistentMethod'");
    }
    
    /**
     * Helper method to find a MethodOracles by method name.
     */
    private MethodOracles findMethod(List<MethodOracles> results, String methodName) {
        return results.stream()
                .filter(m -> m.methodName().equals(methodName))
                .findFirst()
                .orElse(null);
    }
}

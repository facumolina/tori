package org.tori;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tori.metrics.StateFieldCoverage;
import org.tori.metrics.StateFieldCoverageJava;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end test class for StateFieldCoverage metric using TestOracleInspector.
 * Tests the integration of TestOracleInspector with StateFieldCoverage metric
 * using the IntsList class as the target class.
 */
class TestOracleInspectorTest_StateFieldCoverageTest {
    
    private TestOracleInspector inspector;
    private StateFieldCoverage metric;
    private String intsListTestContent;
    
    @BeforeEach
    void setUp() throws IOException {
        inspector = new TestOracleInspector();
        metric = new StateFieldCoverageJava();
        
        // Disable detailed reporting for tests
        metric.setDetailedReportingEnabled(false);
        
        // Configure the metric with the target class
        Properties config = new Properties();
        try (InputStream configStream = getClass().getResourceAsStream("/state_field_coverage.properties")) {
            if (configStream == null) {
                throw new IOException("Configuration file not found: /state_field_coverage.properties");
            }
            config.load(configStream);
        }
        metric.configure(config);
        
        // Load test file from classpath resources
        intsListTestContent = loadResourceAsString("/IntsListTest.java");
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
    void testIntsListTest_AllMethods_WithMetric() {
        List<MethodOracles> results = inspector.findOracles(intsListTestContent, null);
        
        // Should find test methods
        assertTrue(results.size() > 0, "Should find at least one test method in IntsListTest");
        
        // Verify that we can assess each oracle with the metric
        for (MethodOracles methodOracles : results) {
            String testCaseSource = methodOracles.testCaseSource();
            List<String> oracles = methodOracles.oracles();
            
            for (String oracle : oracles) {
                double score = metric.assess(testCaseSource, oracle);
                // Score should be between 0.0 and 1.0
                assertTrue(score >= 0.0 && score <= 1.0, 
                    "Score should be between 0.0 and 1.0, got: " + score);
            }
        }
    }
    
    @Test
    void testIntsListTest_SpecificMethod_AddTest() {
        List<MethodOracles> results = inspector.findOracles(intsListTestContent, "testAdd");
        
        if (results.isEmpty()) {
            // Skip test if method doesn't exist
            return;
        }
        
        // Should find the testAdd method
        assertEquals(1, results.size(), "Should find only the testAdd method");
        
        MethodOracles methodOracles = results.get(0);
        assertEquals("testAdd", methodOracles.methodName());
        
        String testCaseSource = methodOracles.testCaseSource();
        List<String> oracles = methodOracles.oracles();
        
        // Assess each oracle
        assertTrue(oracles.size() > 0, "testAdd should have at least one assertion");
        
        for (String oracle : oracles) {
            double score = metric.assess(testCaseSource, oracle);
            assertTrue(score >= 0.0 && score <= 1.0, 
                "Score should be between 0.0 and 1.0, got: " + score);
        }
    }
    
    @Test
    void testIntsListTest_AssessmentScores() {
        List<MethodOracles> results = inspector.findOracles(intsListTestContent, null);
        
        // Count how many oracles we successfully assess
        int assessedOracles = 0;
        int totalOracles = 0;
        
        for (MethodOracles methodOracles : results) {
            String testCaseSource = methodOracles.testCaseSource();
            List<String> oracles = methodOracles.oracles();
            
            for (String oracle : oracles) {
                totalOracles++;
                double score = metric.assess(testCaseSource, oracle);
                if (score >= 0.0) {
                    assessedOracles++;
                }
            }
        }
        
        // We should be able to assess all oracles (even if score is 0.0)
        assertEquals(totalOracles, assessedOracles, 
            "Should be able to assess all oracles");
    }
    
    @Test
    void testMetricConfiguration() {
        // Test that the metric is properly configured
        Properties config = new Properties();
        config.setProperty("target_class", "src/test/resources/IntsList.java");
        
        StateFieldCoverage testMetric = new StateFieldCoverageJava();
        testMetric.setDetailedReportingEnabled(false);
        assertDoesNotThrow(() -> testMetric.configure(config), 
            "Should be able to configure metric with valid target_class");
    }
    
    @Test
    void testMetricConfiguration_MissingProperty() {
        // Test that configuration fails without target_class
        Properties config = new Properties();
        
        StateFieldCoverage testMetric = new StateFieldCoverageJava();
        testMetric.setDetailedReportingEnabled(false);
        assertThrows(IllegalArgumentException.class, () -> testMetric.configure(config), 
            "Should throw exception when target_class is missing");
    }
    
    @Test
    void testMetricConfiguration_EmptyProperty() {
        // Test that configuration fails with empty target_class
        Properties config = new Properties();
        config.setProperty("target_class", "");
        
        StateFieldCoverage testMetric = new StateFieldCoverageJava();
        testMetric.setDetailedReportingEnabled(false);
        assertThrows(IllegalArgumentException.class, () -> testMetric.configure(config), 
            "Should throw exception when target_class is empty");
    }
}

package org.tori.metrics;

import java.util.List;
import java.util.Properties;

/**
 * Interface for metrics that assess the quality of test oracles (assertions).
 * All metrics should implement the assess method to evaluate a test case and its oracle.
 */
public interface Metric {
    /**
     * Assess the quality of an oracle (assertion) in the context of a test case.
     *
     * @param testCase The full source code of the test case (method body)
     * @param oracle The oracle (assertion statement) to assess
     * @return A double value representing the quality metric (e.g., proportion, score)
     */
    double assess(String testCase, String oracle);
    
    /**
     * Assess the quality of multiple oracles at a given execution level.
     * This method is used when the execution level is TEST_METHOD or TEST_CLASS.
     *
     * @param testCase The full source code of the test case (method body or class)
     * @param oracles List of oracle (assertion statements) to assess
     * @return A double value representing the quality metric (e.g., proportion, score)
     */
    default double assessMultiple(String testCase, List<String> oracles) {
        // Default implementation: assess each oracle individually and return the average
        if (oracles == null || oracles.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        for (String oracle : oracles) {
            sum += assess(testCase, oracle);
        }
        return sum / oracles.size();
    }
    
    /**
     * Configure the metric with properties from a configuration file.
     * This method is optional and should only be implemented by metrics that require configuration.
     * 
     * @param config Properties object containing metric configuration
     */
    default void configure(Properties config) {
        // Default implementation does nothing
    }
    
    /**
     * Get the execution level for this metric.
     * 
     * @return The execution level (default is ASSERT)
     */
    default ExecutionLevel getExecutionLevel() {
        return ExecutionLevel.ASSERT;
    }
    
    /**
     * Set the execution level for this metric.
     * 
     * @param level The execution level to set
     */
    default void setExecutionLevel(ExecutionLevel level) {
        // Default implementation does nothing - metrics that support different levels should override this
    }
}

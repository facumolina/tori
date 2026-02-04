package org.tori.metrics;

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
     * Configure the metric with properties from a configuration file.
     * This method is optional and should only be implemented by metrics that require configuration.
     * 
     * @param config Properties object containing metric configuration
     */
    default void configure(Properties config) {
        // Default implementation does nothing
    }
}

package org.tori.metrics;

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
}

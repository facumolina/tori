package org.tori;

import java.util.List;

/**
 * Record to hold the method name, its oracles (assertions), and the test case source code.
 */
public record MethodOracles(String methodName, List<String> oracles, String testCaseSource) {
}

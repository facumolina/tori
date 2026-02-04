package org.tori;

import java.util.List;

/**
 * Record to hold the method name and its oracles (assertions).
 */
public record MethodOracles(String methodName, List<String> oracles) {
}

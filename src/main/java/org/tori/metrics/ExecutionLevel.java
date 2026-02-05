package org.tori.metrics;

/**
 * Enum representing the level at which a metric should be executed.
 */
public enum ExecutionLevel {
    /**
     * Metric computed individually for each assert statement.
     */
    ASSERT("assert"),
    
    /**
     * Metric computed treating all assertions in a test method as a single one.
     */
    TEST_METHOD("test_method"),
    
    /**
     * Metric computed treating all assertions in a test class as a single one.
     * Note: This option should not be allowed if a method parameter is specified.
     */
    TEST_CLASS("test_class");
    
    private final String configValue;
    
    ExecutionLevel(String configValue) {
        this.configValue = configValue;
    }
    
    public String getConfigValue() {
        return configValue;
    }
    
    /**
     * Parse an ExecutionLevel from a configuration string value.
     * 
     * @param value The configuration string value
     * @return The corresponding ExecutionLevel
     * @throws IllegalArgumentException if the value is not recognized
     */
    public static ExecutionLevel fromConfigValue(String value) {
        if (value == null || value.isEmpty()) {
            return ASSERT; // Default level
        }
        
        for (ExecutionLevel level : values()) {
            if (level.configValue.equalsIgnoreCase(value)) {
                return level;
            }
        }
        
        throw new IllegalArgumentException("Invalid execution level: " + value + 
            ". Valid values are: assert, test_method, test_class");
    }
}

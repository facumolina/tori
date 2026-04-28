package org.tori.metrics.sfc;

import org.junit.jupiter.api.Test;

import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for StateFieldCoverage with the include_static_fields configuration option.
 * Tests are based on ClassWithStaticFields which has:
 * - Static fields: staticCount (int), DEFAULT_NAME (String)
 * - Instance fields: instanceValue (int), name (String)
 */
class StateFieldCoverageStaticFieldsTest {

    private static final String TARGET_CLASS = "src/test/resources/ClassWithStaticFields.java";
    private static final String TARGET_CLASS_ITERABLE = "src/test/resources/ClassWithStaticIterableField.java";

    private StateFieldCoverage createMetric(boolean includeStaticFields) {
        StateFieldCoverage metric = new StateFieldCoverageJava();
        metric.setDetailedReportingEnabled(false);
        Properties config = new Properties();
        config.setProperty("target_class", TARGET_CLASS);
        config.setProperty("iterable_field_tracking", "false");
        config.setProperty("include_static_fields", String.valueOf(includeStaticFields));
        metric.configure(config);
        return metric;
    }

    private StateFieldCoverage createIterableMetric(boolean includeStaticFields) {
        StateFieldCoverage metric = new StateFieldCoverageJava();
        metric.setDetailedReportingEnabled(false);
        Properties config = new Properties();
        config.setProperty("target_class", TARGET_CLASS_ITERABLE);
        config.setProperty("iterable_field_tracking", "true");
        config.setProperty("include_static_fields", String.valueOf(includeStaticFields));
        metric.configure(config);
        return metric;
    }

    @Test
    void testDefaultBehavior_staticFieldsNotIncluded() {
        // Default configuration: include_static_fields=false
        StateFieldCoverage metric = new StateFieldCoverageJava();
        metric.setDetailedReportingEnabled(false);
        Properties config = new Properties();
        config.setProperty("target_class", TARGET_CLASS);
        config.setProperty("iterable_field_tracking", "false");
        metric.configure(config);

        assertFalse(metric.isIncludeStaticFieldsEnabled(),
                "Static fields should be excluded by default");

        Set<String> targetFields = metric.getLastTargetFields();

        // Only instance fields should be targets (instanceValue, name)
        assertEquals(2, targetFields.size(),
                "Should have 2 target fields (instance fields only) by default");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith("instanceValue")),
                "instanceValue should be in target fields");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith("name")),
                "name should be in target fields");
        assertFalse(targetFields.stream().anyMatch(f -> f.endsWith("staticCount")),
                "staticCount should NOT be in target fields by default");
        assertFalse(targetFields.stream().anyMatch(f -> f.endsWith("DEFAULT_NAME")),
                "DEFAULT_NAME should NOT be in target fields by default");
    }

    @Test
    void testIncludeStaticFieldsTrue_staticFieldsIncluded() {
        StateFieldCoverage metric = createMetric(true);

        assertTrue(metric.isIncludeStaticFieldsEnabled(),
                "Static fields should be included when configured");

        Set<String> targetFields = metric.getLastTargetFields();

        // All 4 fields should be targets (2 static + 2 instance)
        assertEquals(4, targetFields.size(),
                "Should have 4 target fields (static + instance) when include_static_fields=true");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith("instanceValue")),
                "instanceValue should be in target fields");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith("name")),
                "name should be in target fields");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith("staticCount")),
                "staticCount should be in target fields when include_static_fields=true");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith("DEFAULT_NAME")),
                "DEFAULT_NAME should be in target fields when include_static_fields=true");
    }

    @Test
    void testDefaultBehavior_staticFieldAccessNotCounted() {
        StateFieldCoverage metric = createMetric(false);

        // Oracle calls getStaticCount() which accesses the static field staticCount
        String testCase = """
                @Test
                public void test() {
                    ClassWithStaticFields obj = new ClassWithStaticFields(42, "hello");
                    assertEquals(1, ClassWithStaticFields.getStaticCount());
                }
                """;
        String oracle = "assertEquals(1, ClassWithStaticFields.getStaticCount());";

        double score = metric.assess(testCase, oracle);

        // staticCount is not a target, so accessing it doesn't improve coverage
        // No instance fields are accessed, so score should be 0
        assertEquals(0.0, score, 0.01,
                "Should be 0.0 when only static field is accessed and static fields are excluded");

        Set<String> accessedFields = metric.getLastAccessedFields();
        assertFalse(accessedFields.stream().anyMatch(f -> f.endsWith("staticCount")),
                "staticCount should NOT appear in accessed fields when static fields are excluded");
    }

    @Test
    void testIncludeStaticFieldsTrue_staticFieldAccessCounted() {
        StateFieldCoverage metric = createMetric(true);

        // Oracle calls getStaticCount() which accesses the static field staticCount
        String testCase = """
                @Test
                public void test() {
                    ClassWithStaticFields obj = new ClassWithStaticFields(42, "hello");
                    assertEquals(1, ClassWithStaticFields.getStaticCount());
                }
                """;
        String oracle = "assertEquals(1, ClassWithStaticFields.getStaticCount());";

        double score = metric.assess(testCase, oracle);

        // staticCount is a target and accessed via getStaticCount()
        // 4 total fields, 1 accessed (staticCount) => score = 0.25
        assertEquals(0.25, score, 0.01,
                "Should be 0.25 when staticCount is accessed and static fields are included");

        Set<String> accessedFields = metric.getLastAccessedFields();
        assertTrue(accessedFields.stream().anyMatch(f -> f.endsWith("staticCount")),
                "staticCount should appear in accessed fields when static fields are included");
    }

    @Test
    void testDefaultBehavior_instanceFieldsCoveredAsExpected() {
        StateFieldCoverage metric = createMetric(false);

        // Oracle calls getInstanceValue() which accesses the instance field
        String testCase = """
                @Test
                public void test() {
                    ClassWithStaticFields obj = new ClassWithStaticFields(42, "hello");
                    assertEquals(42, obj.getInstanceValue());
                }
                """;
        String oracle = "assertEquals(42, obj.getInstanceValue());";

        double score = metric.assess(testCase, oracle);

        // 2 total instance fields, 1 accessed (instanceValue) => score = 0.5
        assertEquals(0.5, score, 0.01,
                "Should be 0.5 when one of two instance fields is accessed");
    }

    @Test
    void testIncludeStaticFieldsTrue_instanceFieldCoveredWithStaticTargets() {
        StateFieldCoverage metric = createMetric(true);

        // Oracle calls getInstanceValue() which accesses the instance field
        String testCase = """
                @Test
                public void test() {
                    ClassWithStaticFields obj = new ClassWithStaticFields(42, "hello");
                    assertEquals(42, obj.getInstanceValue());
                }
                """;
        String oracle = "assertEquals(42, obj.getInstanceValue());";

        double score = metric.assess(testCase, oracle);

        // 4 total fields (2 static + 2 instance), 1 accessed (instanceValue) => score = 0.25
        assertEquals(0.25, score, 0.01,
                "Should be 0.25 when one instance field is accessed out of 4 total fields");
    }

    @Test
    void testIterableTracking_staticIterableFieldExcludedByDefault() {
        // ClassWithStaticIterableField has:
        // - Static field: staticItems (List<String>) => iterable
        // - Instance fields: instanceItems (List<String>) => iterable, size (int)
        // With include_static_fields=false and iterable_field_tracking=true:
        // Targets should be: instanceItems, instanceItems+, size (NOT staticItems or staticItems+)
        StateFieldCoverage metric = createIterableMetric(false);

        Set<String> targetFields = metric.getLastTargetFields();

        assertFalse(targetFields.stream().anyMatch(f -> f.contains("staticItems")),
                "staticItems should NOT be in target fields when include_static_fields=false");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith("instanceItems")),
                "instanceItems should be in target fields");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith("instanceItems+")),
                "instanceItems+ should be in target fields (iterable label)");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith("size")),
                "size should be in target fields");
    }

    @Test
    void testIterableTracking_staticIterableFieldIncludedWhenEnabled() {
        // With include_static_fields=true and iterable_field_tracking=true:
        // Targets should include: staticItems, staticItems+, instanceItems, instanceItems+, size
        StateFieldCoverage metric = createIterableMetric(true);

        Set<String> targetFields = metric.getLastTargetFields();

        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith("staticItems")),
                "staticItems should be in target fields when include_static_fields=true");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith("staticItems+")),
                "staticItems+ should be in target fields when include_static_fields=true");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith("instanceItems")),
                "instanceItems should be in target fields");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith("instanceItems+")),
                "instanceItems+ should be in target fields (iterable label)");
    }
}

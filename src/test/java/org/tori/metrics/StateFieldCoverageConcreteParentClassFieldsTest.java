package org.tori.metrics;

import org.junit.jupiter.api.Test;

import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the {@code include_concrete_parent_class_fields} configuration property in
 * {@link StateFieldCoverage}.
 *
 * <p>The class hierarchy used in these tests:
 *
 * <pre>
 *   ConcreteBase          (baseField : int)
 *       ↑
 *   ConcreteChild extends ConcreteBase    (childField : String)
 * </pre>
 *
 * <p>When the target class is {@code ConcreteChild}:
 * <ul>
 *   <li>Default / {@code include_concrete_parent_class_fields=false}: only
 *       {@code ConcreteChild.baseField} and {@code ConcreteChild.childField} are target fields
 *       (inherited field attributed to the concrete child class).</li>
 *   <li>{@code include_concrete_parent_class_fields=true}: additionally includes
 *       {@code ConcreteBase.baseField}, so there are three target fields in total.</li>
 * </ul>
 */
class StateFieldCoverageConcreteParentClassFieldsTest {

    private StateFieldCoverage buildMetric(boolean includeConcreteParentFields) {
        StateFieldCoverage metric = new StateFieldCoverage();
        metric.setDetailedReportingEnabled(false);
        Properties config = new Properties();
        config.setProperty("target_class", "src/test/resources/ConcreteChild.java");
        config.setProperty("iterable_field_tracking", "false");
        if (includeConcreteParentFields) {
            config.setProperty("include_concrete_parent_class_fields", "true");
        }
        metric.configure(config);
        return metric;
    }

    // -------------------------------------------------------------------------
    // Default behaviour (include_concrete_parent_class_fields not set / false)
    // -------------------------------------------------------------------------

    @Test
    void testDefaultBehaviour_targetFieldsOnlyUseConcreteChildName() {
        StateFieldCoverage metric = buildMetric(false);

        String testCase = """
                @Test
                public void test() {
                    assertTrue(true);
                }
                """;
        metric.assess(testCase, "assertTrue(true);");

        Set<String> targetFields = metric.getLastTargetFields();

        // Two fields: ConcreteChild.childField and ConcreteChild.baseField (inherited)
        assertEquals(2, targetFields.size(),
                "Default: target fields should be ConcreteChild.childField and ConcreteChild.baseField only");

        // All field FQNs must use the concrete child class name
        assertTrue(targetFields.stream().allMatch(f -> f.contains("ConcreteChild")),
                "Default: all target field FQNs should use the concrete child class name");

        // ConcreteBase's own name must not appear
        assertFalse(targetFields.stream().anyMatch(f -> f.contains("ConcreteBase")),
                "Default: target fields must not reference the concrete parent class name");

        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith("ConcreteChild.childField")),
                "Should contain ConcreteChild.childField");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith("ConcreteChild.baseField")),
                "Should contain ConcreteChild.baseField (inherited, not ConcreteBase.baseField)");
    }

    @Test
    void testPropertyFalse_sameAsDefault() {
        StateFieldCoverage metric = new StateFieldCoverage();
        metric.setDetailedReportingEnabled(false);
        Properties config = new Properties();
        config.setProperty("target_class", "src/test/resources/ConcreteChild.java");
        config.setProperty("iterable_field_tracking", "false");
        config.setProperty("include_concrete_parent_class_fields", "false");
        metric.configure(config);

        metric.assess("@Test public void test() { assertTrue(true); }", "assertTrue(true);");
        Set<String> targetFields = metric.getLastTargetFields();

        assertEquals(2, targetFields.size(),
                "With property=false: same as default, only ConcreteChild-prefixed fields");
        assertFalse(targetFields.stream().anyMatch(f -> f.contains("ConcreteBase")),
                "With property=false: ConcreteBase fields must not appear");
    }

    // -------------------------------------------------------------------------
    // include_concrete_parent_class_fields=true
    // -------------------------------------------------------------------------

    @Test
    void testPropertyTrue_includesBothChildAndParentFields() {
        StateFieldCoverage metric = buildMetric(true);

        String testCase = """
                @Test
                public void test() {
                    assertTrue(true);
                }
                """;
        metric.assess(testCase, "assertTrue(true);");

        Set<String> targetFields = metric.getLastTargetFields();

        // Three fields expected: ConcreteChild.childField, ConcreteChild.baseField, ConcreteBase.baseField
        assertEquals(3, targetFields.size(),
                "With property=true: should have ConcreteChild.childField, ConcreteChild.baseField, and ConcreteBase.baseField");

        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith("ConcreteChild.childField")),
                "Should contain ConcreteChild.childField");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith("ConcreteChild.baseField")),
                "Should contain ConcreteChild.baseField (inherited field under child name)");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith("ConcreteBase.baseField")),
                "Should contain ConcreteBase.baseField (field under parent's own name)");
    }

    @Test
    void testPropertyTrue_abstractParentFieldsNotDuplicated() {
        // AbstractShape (abstract) -> Circle (concrete)
        // Even with include_concrete_parent_class_fields=true, abstract parents should NOT
        // contribute their own-named fields (the option only applies to concrete parents).
        StateFieldCoverage metric = new StateFieldCoverage();
        metric.setDetailedReportingEnabled(false);
        Properties config = new Properties();
        config.setProperty("target_class", "src/test/resources/Circle.java");
        config.setProperty("iterable_field_tracking", "false");
        config.setProperty("include_concrete_parent_class_fields", "true");
        metric.configure(config);

        metric.assess("@Test public void test() { assertTrue(true); }", "assertTrue(true);");
        Set<String> targetFields = metric.getLastTargetFields();

        // AbstractShape is abstract, so its own-named fields should NOT be added.
        // Expect: Circle.radius, Circle.area, Circle.color (3 total, same as without the option)
        assertEquals(3, targetFields.size(),
                "Abstract parent fields must not be duplicated under their own name");
        assertFalse(targetFields.stream().anyMatch(f -> f.contains("AbstractShape")),
                "Abstract parent name must not appear even with include_concrete_parent_class_fields=true");
    }
}

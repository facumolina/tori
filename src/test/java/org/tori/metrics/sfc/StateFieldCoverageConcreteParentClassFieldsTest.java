package org.tori.metrics.sfc;

import org.junit.jupiter.api.Test;

import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the {@code include_concrete_parent_class_fields} configuration property in
 * {@link StateFieldCoverage}.
 *
 * <p>Two class hierarchies are exercised:
 *
 * <p><b>Hierarchy 1 – target class with direct concrete parent:</b>
 * <pre>
 *   ConcreteBase          (baseField : int)
 *       ↑
 *   ConcreteChild extends ConcreteBase    (childField : String)
 * </pre>
 * When the target class is {@code ConcreteChild}:
 * <ul>
 *   <li>Default / {@code include_concrete_parent_class_fields=false}: only
 *       {@code ConcreteChild.baseField} and {@code ConcreteChild.childField} are target fields
 *       (inherited field attributed to the concrete child class).</li>
 *   <li>{@code include_concrete_parent_class_fields=true}: additionally includes
 *       {@code ConcreteBase.baseField}, so there are three target fields in total.</li>
 * </ul>
 *
 * <p><b>Hierarchy 2 – target class that holds a field whose type inherits from a concrete parent:</b>
 * <pre>
 *   DependencyBase        (baseData : int)
 *       ↑
 *   DependencyChild extends DependencyBase    (childData : String)
 *
 *   ClassHoldingDependency    (dep : DependencyChild)
 * </pre>
 * When the target class is {@code ClassHoldingDependency}:
 * <ul>
 *   <li>Default / {@code include_concrete_parent_class_fields=false}: the inherited fields of
 *       {@code DependencyChild} must be attributed to {@code DependencyChild}, not to
 *       {@code DependencyBase}.</li>
 *   <li>{@code include_concrete_parent_class_fields=true}: additionally includes
 *       {@code DependencyBase.baseData}.</li>
 * </ul>
 */
class StateFieldCoverageConcreteParentClassFieldsTest {

    private StateFieldCoverage buildMetric(boolean includeConcreteParentFields) {
        StateFieldCoverage metric = new StateFieldCoverageJava();
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
        StateFieldCoverage metric = new StateFieldCoverageJava();
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
        StateFieldCoverage metric = new StateFieldCoverageJava();
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

    // -------------------------------------------------------------------------
    // Hierarchy 2: dependency class (field type) with concrete parent
    // -------------------------------------------------------------------------

    private StateFieldCoverage buildMetricForDependency(boolean includeConcreteParentFields) {
        StateFieldCoverage metric = new StateFieldCoverageJava();
        metric.setDetailedReportingEnabled(false);
        Properties config = new Properties();
        config.setProperty("target_class", "src/test/resources/ClassHoldingDependency.java");
        config.setProperty("iterable_field_tracking", "false");
        if (includeConcreteParentFields) {
            config.setProperty("include_concrete_parent_class_fields", "true");
        }
        metric.configure(config);
        return metric;
    }

    @Test
    void testDependencyInheritance_defaultBehaviour_attributedToDependencyChild() {
        StateFieldCoverage metric = buildMetricForDependency(false);

        metric.assess("@Test public void test() { assertTrue(true); }", "assertTrue(true);");
        Set<String> targetFields = metric.getLastTargetFields();

        // Expected fields:
        //   ClassHoldingDependency.dep  (the reference field itself)
        //   DependencyChild.childData   (own field of DependencyChild)
        //   DependencyChild.baseData    (inherited from DependencyBase, attributed to DependencyChild)
        //
        // DependencyBase's own name must NOT appear by default.
        assertEquals(3, targetFields.size(),
                "Default: should have ClassHoldingDependency.dep, DependencyChild.childData, and DependencyChild.baseData only");

        assertFalse(targetFields.stream().anyMatch(f -> f.contains("DependencyBase")),
                "Default: DependencyBase must not appear in target field FQNs");

        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith("ClassHoldingDependency.dep")),
                "Should contain ClassHoldingDependency.dep");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith("DependencyChild.childData")),
                "Should contain DependencyChild.childData");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith("DependencyChild.baseData")),
                "Should contain DependencyChild.baseData (inherited, attributed to DependencyChild)");
    }

    @Test
    void testDependencyInheritance_propertyTrue_includesParentOwnNamedFields() {
        StateFieldCoverage metric = buildMetricForDependency(true);

        metric.assess("@Test public void test() { assertTrue(true); }", "assertTrue(true);");
        Set<String> targetFields = metric.getLastTargetFields();

        // Expected:
        //   ClassHoldingDependency.dep  (the reference field itself)
        //   DependencyChild.childData   (own field)
        //   DependencyChild.baseData    (inherited under child name)
        //   DependencyBase.baseData     (parent's own-named field, added by the property)
        assertEquals(4, targetFields.size(),
                "With property=true: should include ClassHoldingDependency.dep, DependencyChild.childData, DependencyChild.baseData, and DependencyBase.baseData");

        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith("ClassHoldingDependency.dep")),
                "Should contain ClassHoldingDependency.dep");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith("DependencyChild.childData")),
                "Should contain DependencyChild.childData");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith("DependencyChild.baseData")),
                "Should contain DependencyChild.baseData (inherited under child name)");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith("DependencyBase.baseData")),
                "Should contain DependencyBase.baseData (field under parent's own name)");
    }
}

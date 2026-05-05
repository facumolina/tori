package org.tori.metrics.sfc;

import org.tori.metrics.StateFieldCoverage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for StateFieldCoverage with inherited (parent class) fields.
 *
 * The hierarchy used in these tests:
 *
 *   SecondParentClass
 *       secondParentField (int)
 *
 *   ParentClass extends SecondParentClass
 *       parentField (String)
 *
 *   ChildClass extends ParentClass
 *       childField (int)
 *
 * When the target class is ChildClass, the target fields should include
 * childField, parentField, and secondParentField (3 total).
 */
class StateFieldCoverageInheritedFieldsTest {

    private StateFieldCoverage metric;

    @BeforeEach
    void setUp() {
        metric = new StateFieldCoverageJava();
        metric.setDetailedReportingEnabled(false);

        Properties config = new Properties();
        config.setProperty("target_class", "src/test/resources/ChildClass.java");
        config.setProperty("iterable_field_tracking", "false");
        metric.configure(config);
    }

    @Test
    void testTargetFields_includesParentClassFields() {
        // Run an empty assessment to populate lastTargetFields
        String testCase = """
                @Test
                public void test() {
                    assertTrue(true);
                }
                """;
        String oracle = "assertTrue(true);";
        metric.assess(testCase, oracle);

        Set<String> targetFields = metric.getLastTargetFields();

        // Three fields total across the hierarchy
        assertEquals(3, targetFields.size(),
                "Target fields should include childField, parentField, and secondParentField");

        // All field FQNs must use the concrete class name "ChildClass", never a parent class name
        assertTrue(targetFields.stream().allMatch(f -> f.contains("ChildClass")),
                "All target field FQNs should use the concrete class name 'ChildClass'");
        assertFalse(targetFields.stream().anyMatch(f -> f.contains("ParentClass") || f.contains("SecondParentClass")),
                "Target fields must not reference abstract/parent class names");

        // Individual field checks using the concrete class prefix
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith("ChildClass.childField")),
                "Should contain ChildClass.childField (own field)");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith("ChildClass.parentField")),
                "Should contain ChildClass.parentField (inherited – not ParentClass.parentField)");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith("ChildClass.secondParentField")),
                "Should contain ChildClass.secondParentField (inherited – not SecondParentClass.secondParentField)");
    }

    @Test
    void testAssess_onlyChildField_covered() {
        // Oracle accesses childField via getChildField()
        String testCase = """
                @Test
                public void test() {
                    ChildClass c = new ChildClass(5, "hello", 10);
                    assertEquals(5, c.getChildField());
                }
                """;
        String oracle = "assertEquals(5, c.getChildField());";

        double score = metric.assess(testCase, oracle);
        // getChildField() accesses childField (1 out of 3 total fields)
        assertEquals(1.0 / 3.0, score, 0.01,
                "Should cover 1/3 fields when only childField is accessed");
    }

    @Test
    void testAssess_parentField_coveredByAssertion() {
        // Oracle calls getParentField() which is defined in ParentClass and accesses parentField
        String testCase = """
                @Test
                public void test() {
                    ChildClass c = new ChildClass(5, "hello", 10);
                    assertEquals("hello", c.getParentField());
                }
                """;
        String oracle = "assertEquals(\"hello\", c.getParentField());";

        double score = metric.assess(testCase, oracle);
        // getParentField() accesses parentField from ParentClass (1 out of 3)
        assertEquals(1.0 / 3.0, score, 0.01,
                "Should cover 1/3 fields when a parent class field is accessed via its getter");

        Set<String> accessed = metric.getLastAccessedFields();
        assertTrue(accessed.stream().anyMatch(f -> f.endsWith("ChildClass.parentField")),
                "parentField should be in accessed fields as ChildClass.parentField (not ParentClass.parentField)");
    }

    @Test
    void testAssess_secondParentField_coveredByAssertion() {
        // Oracle calls getSecondParentField() defined in SecondParentClass
        String testCase = """
                @Test
                public void test() {
                    ChildClass c = new ChildClass(5, "hello", 10);
                    assertEquals(10, c.getSecondParentField());
                }
                """;
        String oracle = "assertEquals(10, c.getSecondParentField());";

        double score = metric.assess(testCase, oracle);
        // getSecondParentField() accesses secondParentField from SecondParentClass (1 out of 3)
        assertEquals(1.0 / 3.0, score, 0.01,
                "Should cover 1/3 fields when a second-level parent class field is accessed");

        Set<String> accessed = metric.getLastAccessedFields();
        assertTrue(accessed.stream().anyMatch(f -> f.endsWith("ChildClass.secondParentField")),
                "secondParentField should be in accessed fields as ChildClass.secondParentField");
    }

    @Test
    void testAssess_allFields_covered() {
        // Oracle accesses all three fields across the hierarchy
        String testCase = """
                @Test
                public void test() {
                    ChildClass c = new ChildClass(5, "hello", 10);
                    assertTrue(c.getChildField() == 5
                            && c.getParentField().equals("hello")
                            && c.getSecondParentField() == 10);
                }
                """;
        String oracle = "assertTrue(c.getChildField() == 5 && c.getParentField().equals(\"hello\") && c.getSecondParentField() == 10);";

        double score = metric.assess(testCase, oracle);
        // All 3 fields covered
        assertEquals(1.0, score, 0.01,
                "Should be 1.0 when all fields across the inheritance chain are accessed");
    }
}

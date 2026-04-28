package org.tori.metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests verifying that fields inherited from abstract parent classes are attributed to
 * the concrete (non-abstract, instantiatable) child class.
 *
 * The hierarchy used in these tests:
 *
 *   abstract AbstractShape
 *       area   (double)   - abstract parent field
 *       color  (String)   - abstract parent field
 *
 *   Circle extends AbstractShape
 *       radius (double)   - own field
 *
 * When the target class is Circle, ALL target fields should use the prefix "Circle",
 * not "AbstractShape".  That is:
 *   Circle.radius   (own field)
 *   Circle.area     (inherited from AbstractShape – NOT AbstractShape.area)
 *   Circle.color    (inherited from AbstractShape – NOT AbstractShape.color)
 */
class StateFieldCoverageAbstractClassInheritanceTest {

    private StateFieldCoverage metric;

    @BeforeEach
    void setUp() {
        metric = new StateFieldCoverageJava();
        metric.setDetailedReportingEnabled(false);

        Properties config = new Properties();
        config.setProperty("target_class", "src/test/resources/Circle.java");
        config.setProperty("iterable_field_tracking", "false");
        metric.configure(config);
    }

    @Test
    void testTargetFields_inheritedFieldsUseConcreteClassName() {
        // Run an empty assessment to populate lastTargetFields
        String testCase = """
                @Test
                public void test() {
                    assertTrue(true);
                }
                """;
        metric.assess(testCase, "assertTrue(true);");

        Set<String> targetFields = metric.getLastTargetFields();

        // All three fields (own + inherited) should be present
        assertEquals(3, targetFields.size(),
                "Target fields should include radius, area, and color (3 total)");

        // All field FQNs should use the concrete class name "Circle"
        assertTrue(targetFields.stream().allMatch(f -> f.contains("Circle")),
                "All target field FQNs should use the concrete class name 'Circle', not 'AbstractShape'");

        // Individual field checks using the concrete class prefix
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith("Circle.radius")),
                "Should contain Circle.radius (own field)");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith("Circle.area")),
                "Should contain Circle.area (inherited – not AbstractShape.area)");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith("Circle.color")),
                "Should contain Circle.color (inherited – not AbstractShape.color)");

        // Fields should NOT be attributed to the abstract parent class
        assertFalse(targetFields.stream().anyMatch(f -> f.contains("AbstractShape")),
                "Target fields must not use the abstract class name 'AbstractShape'");
    }

    @Test
    void testAssess_ownFieldCovered() {
        String testCase = """
                @Test
                public void test() {
                    Circle c = new Circle(5.0, "red");
                    assertEquals(5.0, c.getRadius(), 0.001);
                }
                """;
        String oracle = "assertEquals(5.0, c.getRadius(), 0.001);";

        double score = metric.assess(testCase, oracle);

        // getRadius() accesses radius (1 out of 3 total fields)
        assertEquals(1.0 / 3.0, score, 0.01,
                "Should cover 1/3 fields when only radius is accessed");

        Set<String> accessed = metric.getLastAccessedFields();
        assertTrue(accessed.stream().anyMatch(f -> f.endsWith("Circle.radius")),
                "Accessed fields should include Circle.radius");
    }

    @Test
    void testAssess_inheritedFieldCoveredViaGetter() {
        String testCase = """
                @Test
                public void test() {
                    Circle c = new Circle(5.0, "blue");
                    assertEquals("blue", c.getColor());
                }
                """;
        String oracle = "assertEquals(\"blue\", c.getColor());";

        double score = metric.assess(testCase, oracle);

        // getColor() (defined in AbstractShape) accesses color (1 out of 3)
        assertEquals(1.0 / 3.0, score, 0.01,
                "Should cover 1/3 fields when only color (inherited) is accessed");

        Set<String> accessed = metric.getLastAccessedFields();
        // The accessed field should be attributed to Circle, not AbstractShape
        assertTrue(accessed.stream().anyMatch(f -> f.endsWith("Circle.color")),
                "Accessed field should be Circle.color, not AbstractShape.color");
        assertFalse(accessed.stream().anyMatch(f -> f.contains("AbstractShape")),
                "Accessed fields must not reference AbstractShape");
    }

    @Test
    void testAssess_areaFieldCovered() {
        String testCase = """
                @Test
                public void test() {
                    Circle c = new Circle(5.0, "green");
                    c.computeArea();
                    assertEquals(Math.PI * 25.0, c.getArea(), 0.001);
                }
                """;
        String oracle = "assertEquals(Math.PI * 25.0, c.getArea(), 0.001);";

        double score = metric.assess(testCase, oracle);

        // getArea() (defined in AbstractShape) accesses area (1 out of 3)
        assertEquals(1.0 / 3.0, score, 0.01,
                "Should cover 1/3 fields when only area (inherited) is accessed");

        Set<String> accessed = metric.getLastAccessedFields();
        assertTrue(accessed.stream().anyMatch(f -> f.endsWith("Circle.area")),
                "Accessed field should be Circle.area, not AbstractShape.area");
    }

    @Test
    void testAssess_allFieldsCovered() {
        String testCase = """
                @Test
                public void test() {
                    Circle c = new Circle(3.0, "red");
                    c.computeArea();
                    assertTrue(c.getRadius() == 3.0
                            && c.getColor().equals("red")
                            && c.getArea() > 0);
                }
                """;
        String oracle = "assertTrue(c.getRadius() == 3.0 && c.getColor().equals(\"red\") && c.getArea() > 0);";

        double score = metric.assess(testCase, oracle);

        // All 3 fields accessed
        assertEquals(1.0, score, 0.01,
                "Should be 1.0 when all three fields are accessed");
    }
}

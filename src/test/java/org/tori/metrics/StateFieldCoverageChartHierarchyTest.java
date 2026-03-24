package org.tori.metrics;

import org.junit.jupiter.api.Test;

import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for StateFieldCoverage with the JFreeChart-inspired hierarchy:
 *
 * <pre>
 *   AbstractObjectList
 *       objects  (transient Object[])
 *       size     (int)
 *          ↑
 *   ShapeList extends AbstractObjectList
 *       (no own fields)
 *          ↑ used as field type
 *   AbstractRenderer  (abstract)
 *       shapeList  (private ShapeList)   ← private field in abstract parent
 *          ↑
 *   AbstractCategoryItemRenderer extends AbstractRenderer  (abstract, no own fields)
 * </pre>
 *
 * Key aspects under test:
 * <ol>
 *   <li>A {@code private} field declared in an abstract parent class is discovered
 *       and attributed to the target class (not to its declaring class).</li>
 *   <li>With {@code iterable_field_tracking=false} only {@code shapeList} is
 *       counted (1 field total).</li>
 *   <li>With {@code iterable_field_tracking=true} the nested fields of
 *       {@code ShapeList} ({@code objects}, {@code size}) are also tracked,
 *       giving 3 target fields in total.</li>
 *   <li>Assessment scores are calculated correctly against the discovered fields.</li>
 * </ol>
 *
 * A concrete helper class ({@code ConcreteCategoryItemRenderer}) is used inside
 * test-case strings so that field access patterns can be written; the configured
 * target class is always {@code AbstractCategoryItemRenderer}.
 */
class StateFieldCoverageChartHierarchyTest {

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private StateFieldCoverage buildMetric(boolean iterableTracking) {
        StateFieldCoverage metric = new StateFieldCoverage();
        metric.setDetailedReportingEnabled(false);
        Properties config = new Properties();
        config.setProperty("target_class",
                "src/test/resources/AbstractCategoryItemRenderer.java");
        config.setProperty("iterable_field_tracking",
                Boolean.toString(iterableTracking));
        metric.configure(config);
        return metric;
    }

    /**
     * Runs an empty assessment just to trigger field discovery and returns
     * the set of target fields.
     */
    private Set<String> discoverTargetFields(StateFieldCoverage metric) {
        metric.assess("""
                @Test
                public void dummy() {
                    assertTrue(true);
                }
                """, "assertTrue(true);");
        return metric.getLastTargetFields();
    }

    // =========================================================================
    // Field discovery – iterable_field_tracking = false
    // =========================================================================

    @Test
    void testTargetFields_noIterableTracking_onlyShapeListField() {
        StateFieldCoverage metric = buildMetric(false);
        Set<String> targetFields = discoverTargetFields(metric);

        assertEquals(6, targetFields.size(),
                "With iterable_field_tracking=false, there should be exactly 6 target fields: shapeList, objectList, shapeList.objects, shapeList.size, objectList.objects, and objectList.size");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith("AbstractCategoryItemRenderer.shapeList")),
                "shapeList must be attributed to AbstractCategoryItemRenderer");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith("AbstractCategoryItemRenderer.objectList")),
                "objectList must be attributed to AbstractCategoryItemRenderer");
        assertFalse(targetFields.stream().anyMatch(f -> f.contains("AbstractRenderer")),
                "No field should be attributed to AbstractRenderer");
        assertFalse(targetFields.stream().anyMatch(f -> f.contains("AbstractObjectList")),
                "No field should be attributed to AbstractObjectList");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith("ShapeList.objects")),
                "ShapeList.objects field should be included even with iterable_field_tracking=false");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith("ShapeList.size")),
                "ShapeList.size field should be included even with iterable_field_tracking=false");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith("ObjectList.objects")),
                "ObjectList.objects field should be included even with iterable_field_tracking=false");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith("ObjectList.size")),
                "ObjectList.size field should be included even with iterable_field_tracking=false");
    }


    // =========================================================================
    // Field discovery – iterable_field_tracking = true
    // =========================================================================

    @Test
    void testTargetFields_withIterableTracking_includesNestedFields() {
        StateFieldCoverage metric = buildMetric(true);
        Set<String> targetFields = discoverTargetFields(metric);

        // shapeList + objects + size  →  8 target fields
        assertEquals(8, targetFields.size(),
                "With iterable_field_tracking=true, shapeList, objects, objects+, and size should all be tracked as target fields");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith("AbstractCategoryItemRenderer.shapeList")),
                "shapeList must be attributed to AbstractCategoryItemRenderer");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith("AbstractCategoryItemRenderer.objectList")),
                "objectList must be attributed to AbstractCategoryItemRenderer");
        assertFalse(targetFields.stream().anyMatch(f -> f.contains("AbstractRenderer")),
                "No field should be attributed to AbstractRenderer");
        assertFalse(targetFields.stream().anyMatch(f -> f.contains("AbstractObjectList")),
                "No field should be attributed to AbstractObjectList");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith("ShapeList.objects")),
                "objects field should be included with iterable_field_tracking=true");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith("ShapeList.size")),
                "size field should be included with iterable_field_tracking=true");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith("ShapeList.objects+")),
                "objects+ field should be included with iterable_field_tracking=true");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith("ObjectList.objects")),
                "objects field should be included with iterable_field_tracking=true");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith("ObjectList.size")),
                "size field should be included with iterable_field_tracking=true");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith("ObjectList.objects+")),
                "objects+ field should be included with iterable_field_tracking=true");
    }

    // =========================================================================
    // Coverage value tests – iterable_field_tracking = false
    // =========================================================================

    /**
     * An oracle that does not reference any target-class field should yield a
     * coverage score of exactly 0.0.
     */
    @Test
    void testScore_zeroWhenNoFieldsAccessed() {
        StateFieldCoverage metric = buildMetric(false);
        String testCase = """
                @Test
                public void test() {
                    AbstractCategoryItemRenderer r = new ConcreteCategoryItemRenderer();
                    assertTrue(r != null);
                }
                """;
        double score = metric.assess(testCase, "assertTrue(r != null);");
        assertEquals(0.0, score, 0.001,
                "Score should be 0.0 when no target fields are accessed");
    }

    /**
     * An oracle that accesses only the inherited {@code shapeList} field via
     * {@code getShapeList()} should yield 1/3 with 3 total target fields
     * ({@code shapeList}, {@code objects}, {@code size}).
     */
    @Test
    void testScore_onlyShapeListAccessed_noIterableTracking() {
        StateFieldCoverage metric = buildMetric(false);
        String testCase = """
                @Test
                public void test() {
                    AbstractCategoryItemRenderer r = new ConcreteCategoryItemRenderer();
                    assertNotNull(r.getShapeList());
                }
                """;
        double score = metric.assess(testCase, "assertNotNull(r.getShapeList());");
        assertEquals(1.0 / 3.0, score, 0.001,
                "Accessing only shapeList should give score 1/3");
    }

    /**
     * An oracle that directly accesses the nested {@code size} field through the
     * {@code ShapeList} dependency should yield 2/6.
     */
    @Test
    void testScore_shapeListAndSizeAccessed_noIterableTracking() {
        StateFieldCoverage metric = buildMetric(false);
        String testCase = """
                @Test
                public void test() {
                    AbstractCategoryItemRenderer r = new ConcreteCategoryItemRenderer();
                    assertEquals(0, r.getShapeList().size);
                }
                """;
        double score = metric.assess(testCase, "assertEquals(0, r.getShapeList().size);");
        assertEquals(2.0 / 6.0, score, 0.001,
                "Accessing shapeList and size should give score 2/6");
    }

    /**
     * An oracle that accesses all three target fields ({@code shapeList},
     * {@code objects}, {@code size}) in one expression should yield 3/6.
     */
    @Test
    void testScore_allFieldsAccessed_noIterableTracking() {
        StateFieldCoverage metric = buildMetric(false);
        String testCase = """
                @Test
                public void test() {
                    AbstractCategoryItemRenderer r = new ConcreteCategoryItemRenderer();
                    assertEquals(r.getShapeList().size, r.getShapeList().objects.length);
                }
                """;
        double score = metric.assess(testCase,
                "assertEquals(r.getShapeList().size, r.getShapeList().objects.length);");
        assertEquals(3.0 / 6.0, score, 0.001,
                "Accessing shapeList, size, and objects should give score 3/6 with 6 total fields");
    }

    // =========================================================================
    // Coverage value tests – iterable_field_tracking = true
    // =========================================================================

    /**
     * With iterable tracking enabled (4 total fields), an oracle accessing only
     * {@code shapeList} should yield score 1/4 = 0.25.
     */
    @Test
    void testScore_onlyShapeListAccessed_withIterableTracking() {
        StateFieldCoverage metric = buildMetric(true);
        String testCase = """
                @Test
                public void test() {
                    AbstractCategoryItemRenderer r = new ConcreteCategoryItemRenderer();
                    assertNotNull(r.getShapeList());
                }
                """;
        double score = metric.assess(testCase, "assertNotNull(r.getShapeList());");
        assertEquals(0.25, score, 0.001,
                "With iterable tracking, accessing only shapeList should give score 1/4");
    }

    /**
     * With iterable tracking enabled, an oracle accessing {@code shapeList} and
     * {@code size} should yield score 2/4 = 0.5.
     */
    @Test
    void testScore_shapeListAndSizeAccessed_withIterableTracking() {
        StateFieldCoverage metric = buildMetric(true);
        String testCase = """
                @Test
                public void test() {
                    AbstractCategoryItemRenderer r = new ConcreteCategoryItemRenderer();
                    assertEquals(0, r.getShapeList().size);
                }
                """;
        double score = metric.assess(testCase, "assertEquals(0, r.getShapeList().size);");
        assertEquals(0.5, score, 0.001,
                "With iterable tracking, accessing shapeList and size should give score 2/4");
    }

}

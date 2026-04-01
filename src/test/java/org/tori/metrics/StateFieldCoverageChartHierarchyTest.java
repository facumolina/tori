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
 *   ObjectList extends AbstractObjectList
 *       (no own fields)
 *          ↑ used as field type
 *   CategoryPlot
 *       orientation  (Object)
 *       axisOffset   (int)
 *       domainAxes   (ObjectList)
 *   AbstractRenderer  (abstract)
 *       shapeList   (private ShapeList)   ← private field in abstract parent
 *       objectList  (private ObjectList)  ← private field in abstract parent
 *          ↑
 *   AbstractCategoryItemRenderer extends AbstractRenderer
 *       plot  (private CategoryPlot)      ← own field
 * </pre>
 *
 * Key aspects under test:
 * <ol>
 *   <li>A {@code private} field declared in an abstract parent class is discovered
 *       and attributed to the target class (not to its declaring class).</li>
 *   <li>With {@code iterable_field_tracking=false}, the target fields are:
 *       {@code shapeList}, {@code objectList}, {@code plot},
 *       {@code ShapeList.objects}, {@code ShapeList.size},
 *       {@code ObjectList.objects}, {@code ObjectList.size},
 *       {@code CategoryPlot.orientation}, {@code CategoryPlot.axisOffset},
 *       {@code CategoryPlot.domainAxes} — 10 fields total.</li>
 *   <li>With {@code iterable_field_tracking=true} the iterable nested fields
 *       ({@code ShapeList.objects+} and {@code ObjectList.objects+}) are also
 *       tracked, giving 12 target fields in total.</li>
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

        assertEquals(10, targetFields.size(),
                "With iterable_field_tracking=false, there should be exactly 10 target fields: " +
                "shapeList, objectList, plot, ShapeList.objects, ShapeList.size, " +
                "ObjectList.objects, ObjectList.size, CategoryPlot.orientation, " +
                "CategoryPlot.axisOffset, and CategoryPlot.domainAxes");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith("AbstractCategoryItemRenderer.shapeList")),
                "shapeList must be attributed to AbstractCategoryItemRenderer");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith("AbstractCategoryItemRenderer.objectList")),
                "objectList must be attributed to AbstractCategoryItemRenderer");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith("AbstractCategoryItemRenderer.plot")),
                "plot must be attributed to AbstractCategoryItemRenderer");
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
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith("CategoryPlot.orientation")),
                "CategoryPlot.orientation field should be included");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith("CategoryPlot.axisOffset")),
                "CategoryPlot.axisOffset field should be included");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith("CategoryPlot.domainAxes")),
                "CategoryPlot.domainAxes field should be included");
    }


    // =========================================================================
    // Field discovery – iterable_field_tracking = true
    // =========================================================================

    @Test
    void testTargetFields_withIterableTracking_includesNestedFields() {
        StateFieldCoverage metric = buildMetric(true);
        Set<String> targetFields = discoverTargetFields(metric);

        // shapeList + objectList + plot + CategoryPlot fields + nested ShapeList/ObjectList fields → 12 target fields
        assertEquals(12, targetFields.size(),
                "With iterable_field_tracking=true, shapeList, objectList, plot, " +
                "ShapeList.objects, ShapeList.size, ShapeList.objects+, " +
                "ObjectList.objects, ObjectList.size, ObjectList.objects+, " +
                "CategoryPlot.orientation, CategoryPlot.axisOffset, CategoryPlot.domainAxes " +
                "should all be tracked as target fields");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith("AbstractCategoryItemRenderer.shapeList")),
                "shapeList must be attributed to AbstractCategoryItemRenderer");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith("AbstractCategoryItemRenderer.objectList")),
                "objectList must be attributed to AbstractCategoryItemRenderer");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith("AbstractCategoryItemRenderer.plot")),
                "plot must be attributed to AbstractCategoryItemRenderer");
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
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith("CategoryPlot.orientation")),
                "CategoryPlot.orientation field should be included");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith("CategoryPlot.axisOffset")),
                "CategoryPlot.axisOffset field should be included");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith("CategoryPlot.domainAxes")),
                "CategoryPlot.domainAxes field should be included");
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
     * {@code getShapeList()} should yield 1/10 with 10 total target fields.
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
        assertEquals(1.0 / 10.0, score, 0.001,
                "Accessing only shapeList should give score 1/10");
    }

    /**
     * An oracle that directly accesses the nested {@code size} field through the
     * {@code ShapeList} dependency should yield 2/10.
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
        assertEquals(2.0 / 10.0, score, 0.001,
                "Accessing shapeList and size should give score 2/10");
    }

    /**
     * An oracle that accesses all three target fields ({@code shapeList},
     * {@code objects}, {@code size}) in one expression should yield 3/10.
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
        assertEquals(3.0 / 10.0, score, 0.001,
                "Accessing shapeList, size, and objects should give score 3/10 with 10 total fields");
    }

    // =========================================================================
    // Coverage value tests – iterable_field_tracking = true
    // =========================================================================

    /**
     * With iterable tracking enabled (12 total fields), an oracle accessing only
     * {@code shapeList} should yield score 1/12.
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
        assertEquals(1.0 / 12.0, score, 0.001,
                "With iterable tracking, accessing only shapeList should give score 1/12");
    }

    /**
     * With iterable tracking enabled, an oracle accessing {@code shapeList} and
     * {@code size} should yield score 2/12.
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
        assertEquals(2.0 / 12.0, score, 0.001,
                "With iterable tracking, accessing shapeList and size should give score 2/12");
    }

}

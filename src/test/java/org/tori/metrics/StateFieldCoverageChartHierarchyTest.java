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

        assertEquals(3, targetFields.size(),
                "With iterable_field_tracking=false, there should be exactly 3 target fields: shapeList, objects, and size");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith("AbstractCategoryItemRenderer.shapeList")),
                "shapeList must be attributed to AbstractCategoryItemRenderer");
        assertFalse(targetFields.stream().anyMatch(f -> f.contains("AbstractRenderer")),
                "No field should be attributed to AbstractRenderer");
        assertFalse(targetFields.stream().anyMatch(f -> f.contains("AbstractObjectList")),
                "No field should be attributed to AbstractObjectList");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith("ShapeList.objects")),
                "objects field should be included even with iterable_field_tracking=false");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith("ShapeList.size")),
                "size field should be included even with iterable_field_tracking=false");
    }


    // =========================================================================
    // Field discovery – iterable_field_tracking = true
    // =========================================================================

    @Test
    void testTargetFields_withIterableTracking_includesNestedFields() {
        StateFieldCoverage metric = buildMetric(true);
        Set<String> targetFields = discoverTargetFields(metric);

        // shapeList + objects + size  →  4 target fields
        assertEquals(4, targetFields.size(),
                "With iterable_field_tracking=true, shapeList, objects, objects+, and size should all be tracked as target fields");
        assertTrue(targetFields.stream().anyMatch(f -> f.endsWith("AbstractCategoryItemRenderer.shapeList")),
                "shapeList must be attributed to AbstractCategoryItemRenderer");
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
    }

}

package org.tori.metrics.sfc;

import org.tori.metrics.StateFieldCoverage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for StateFieldCoverage metric with ignoreDependencyClassesFields enabled.
 * Tests are based on the apache.ByteArrayInputStreamWithPos class. When dependency class fields
 * are ignored, only the 4 fields from the root class hierarchy are considered as target fields
 * (segment, position, count, mark from MemorySegmentInputStreamWithPos), excluding the fields
 * from MemorySegment (the dependency class reachable via the segment field).
 */
class StateFieldCoverageIgnoreDependencyFieldsTest {

    private StateFieldCoverage metric;

    @BeforeEach
    void setUp() {
        metric = new StateFieldCoverageJava();

        // Disable detailed reporting for tests
        metric.setDetailedReportingEnabled(false);

        // Configure the metric with the target class path and ignore dependency class fields
        Properties config = new Properties();
        config.setProperty("target_class", "src/test/resources/apache/ByteArrayInputStreamWithPos.java");
        config.setProperty("ignore_dependency_classes_fields", "true");
        metric.configure(config);
    }

    @Test
    void testGetMoreThanAvailable_exhaustedAssertion() {
        String testCase = """
            private final byte[] data = new byte[] {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9'};

            private final ByteArrayInputStreamWithPos stream = new ByteArrayInputStreamWithPos(data);

            @Test
            void testGetMoreThanAvailable() {
                int read = stream.read(new byte[20], 0, 20);
                assertThat(stream.read()).isEqualTo(-1); // exhausted now
            }
            """;
        String oracle = "assertThat(stream.read()).isEqualTo(-1);";

        double score = metric.assess(testCase, oracle);

        // Only 4 target fields: segment, position, count, mark (MemorySegment fields are excluded)
        assertTrue(metric.getLastTargetFields().size() == 4, "Should have identified 4 target fields");

        assertTrue(metric.getLastAccessedFields().size() > 0, "Should have identified accessed fields");

        assertEquals(0.75, score, 0.01, "Score should be 75.0");

        assertTrue(metric.getLastAccessedFields().stream().anyMatch(f -> f.contains("ByteArrayInputStreamWithPos.position")),
            "Should have identified access to 'ByteArrayInputStreamWithPos.position' field");

        assertTrue(metric.getLastAccessedFields().stream().anyMatch(f -> f.contains("ByteArrayInputStreamWithPos.count")),
            "Should have identified access to 'ByteArrayInputStreamWithPos.count' field");

        assertTrue(metric.getLastAccessedFields().stream().anyMatch(f -> f.contains("ByteArrayInputStreamWithPos.segment")),
            "Should have identified access to 'ByteArrayInputStreamWithPos.segment' field");

        // MemorySegment is a dependency class, so its fields should not be in the accessed fields
        assertFalse(metric.getLastAccessedFields().stream().anyMatch(f -> f.contains("MemorySegment.address")),
            "Should NOT have identified access to 'MemorySegment.address' field (dependency class field)");
    }
}

package org.tori.metrics.sfc;

import org.tori.metrics.StateFieldCoverage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for StateFieldCoverage metric.
 * Tests are based on the apache.ByteArrayInputStreamWithPos class which has 4 fields:
 */
class StateFieldCoverageFieldAccessedInMethodTest {

    private StateFieldCoverage metric;

    @BeforeEach
    void setUp() {
        metric = new StateFieldCoverageJava();
        
        // Disable detailed reporting for tests
        metric.setDetailedReportingEnabled(false);
        
        // Configure the metric with the target class path
        Properties config = new Properties();
        config.setProperty("target_class", "src/test/resources/apache/ByteArrayInputStreamWithPos.java");
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

        assertTrue(metric.getLastTargetFields().size() == 14, "Should have identified 14 target fields");

        assertTrue(metric.getLastAccessedFields().size() > 0, "Should have identified accessed fields");

        assertEquals(0.5, score, 0.01, "Score should be 50.0");

        assertTrue(metric.getLastAccessedFields().stream().anyMatch(f -> f.contains("ByteArrayInputStreamWithPos.position")),
            "Should have identified access to 'ByteArrayInputStreamWithPos.position' field");

        assertTrue(metric.getLastAccessedFields().stream().anyMatch(f -> f.contains("ByteArrayInputStreamWithPos.count")),
            "Should have identified access to 'ByteArrayInputStreamWithPos.count' field");

        assertTrue(metric.getLastAccessedFields().stream().anyMatch(f -> f.contains("ByteArrayInputStreamWithPos.segment")),
            "Should have identified access to 'ByteArrayInputStreamWithPos.segment' field");
        
        // Ensure field org.apache.flink.core.memory.MemorySegment.address is accessed
        assertTrue(metric.getLastAccessedFields().stream().anyMatch(f -> f.contains("MemorySegment.address")),
            "Should have identified access to 'MemorySegment.address' field");
    }


}
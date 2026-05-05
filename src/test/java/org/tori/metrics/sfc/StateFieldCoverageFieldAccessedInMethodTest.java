package org.tori.metrics.sfc;

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

        assertTrue(metric.getLastAccessedFields().stream().anyMatch(f -> f.contains("position")),
            "Should have identified access to 'position' field");

        assertTrue(metric.getLastAccessedFields().stream().anyMatch(f -> f.contains("count")),
            "Should have identified access to 'count' field");

        assertTrue(metric.getLastAccessedFields().stream().anyMatch(f -> f.contains("segment")),
            "Should have identified access to 'segment' field");
        
    }


}
package org.tori.metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for StateFieldCoverage metric.
 * Tests are based on the IntsList class which has 4 fields:
 * - IntsList.header (Node)
 * - IntsList.size (int)
 * - Node.next (Node)
 * - Node.item (int)
 */
class StateFieldCoverageTest {

    private StateFieldCoverage metric;

    @BeforeEach
    void setUp() {
        metric = new StateFieldCoverage();
    }

    @Test
    void testAssess_OnlySize() {
        // This test only accesses the size field via getSize()
        String testCase = """
            @Test
            public void test1() {
                IntsList l = new IntsList();
                l.add(10);
                l.add(20);
                assertEquals(2, l.getSize());
            }
            """;
        String oracle = "assertEquals(2, l.getSize());";
        
        double score = metric.assess(testCase, oracle);
        // Only size field is accessed out of 4 total fields (header, size, next, item)
        assertEquals(0.25, score, 0.01, "Should be 0.25 when only size field is accessed");
    }

    @Test
    void testAssess_HeaderField() {
        // This test accesses the header field
        String testCase = """
            @Test
            public void testGetHeader() {
                IntsList l = new IntsList();
                l.add(100);
                assertNotNull(l.getHeader());
            }
            """;
        String oracle = "assertNotNull(l.getHeader());";
        
        double score = metric.assess(testCase, oracle);
        // getHeader accesses the header field (1 out of 4)
        assertEquals(0.25, score, 0.01, "Should be 0.25 when only header field is accessed");
    }

    @Test
    void testAssess_HeaderItem() {
        // This test accesses header and item fields
        String testCase = """
            @Test
            public void testHeaderItem() {
                IntsList l = new IntsList();
                l.add(42);
                assertEquals(42, l.getHeader().item);
            }
            """;
        String oracle = "assertEquals(42, l.getHeader().item);";
        
        double score = metric.assess(testCase, oracle);
        // getHeader accesses header, and .item accesses item (2 out of 4)
        assertEquals(0.50, score, 0.01, "Should be 0.50 when header and item fields are accessed");
    }

    @Test
    void testAssess_HeaderNext() {
        // This test accesses header and next fields
        String testCase = """
            @Test
            public void testHeaderNext() {
                IntsList l = new IntsList();
                l.add(1);
                l.add(2);
                assertNotNull(l.getHeader().next);
            }
            """;
        String oracle = "assertNotNull(l.getHeader().next);";
        
        double score = metric.assess(testCase, oracle);
        // getHeader accesses header, and .next accesses next (2 out of 4)
        assertEquals(0.50, score, 0.01, "Should be 0.50 when header and next fields are accessed");
    }

    @Test
    void testAssess_MultipleFields() {
        // This test accesses header and size fields
        String testCase = """
            @Test
            public void testMultipleFields() {
                IntsList l = new IntsList();
                l.add(7);
                l.add(8);
                assertTrue(l.getHeader() != null && l.getSize() == 2);
            }
            """;
        String oracle = "assertTrue(l.getHeader() != null && l.getSize() == 2);";
        
        double score = metric.assess(testCase, oracle);
        // getHeader accesses header, getSize accesses size (2 out of 4)
        assertEquals(0.50, score, 0.01, "Should be 0.50 when header and size fields are accessed");
    }

    @Test
    void testAssess_AllFields() {
        // This test accesses all 4 fields
        String testCase = """
            @Test
            public void testAllFields() {
                IntsList l = new IntsList();
                l.add(1);
                l.add(2);
                assertTrue(l.getHeader().item == 1 && l.getHeader().next != null && l.getSize() == 2 && l.getHeader().next.item == 2);
            }
            """;
        String oracle = "assertTrue(l.getHeader().item == 1 && l.getHeader().next != null && l.getSize() == 2 && l.getHeader().next.item == 2);";
        
        double score = metric.assess(testCase, oracle);
        // All fields accessed: header (via getHeader), size (via getSize), item, next
        assertEquals(1.0, score, 0.01, "Should be 1.0 when all fields are accessed");
    }

    @Test
    void testAssess_EmptyList() {
        // Test with isEmpty which accesses header field
        String testCase = """
            @Test
            public void testIsEmpty() {
                IntsList l = new IntsList();
                assertTrue(l.isEmpty());
            }
            """;
        String oracle = "assertTrue(l.isEmpty());";
        
        double score = metric.assess(testCase, oracle);
        // isEmpty accesses header field (1 out of 4)
        assertEquals(0.25, score, 0.01, "Should be 0.25 when only header field is accessed");
    }

    @Test
    void testAssess_GetMethod() {
        // Test with get method which accesses all fields: size, header, next, and item
        String testCase = """
            @Test
            public void testGet() {
                IntsList l = new IntsList();
                l.add(10);
                l.add(20);
                l.add(30);
                assertEquals(20, l.get(1));
            }
            """;
        String oracle = "assertEquals(20, l.get(1));";
        
        double score = metric.assess(testCase, oracle);
        // get method accesses size (in condition), header, next (in loop), and item (4 out of 4)
        assertEquals(1.0, score, 0.01, "Should be 1.0 when all fields (size, header, next, item) are accessed");
    }
}

package com.example;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * More comprehensive example demonstrating various assertion types
 */
public class StringUtilsTest {

    @Test
    @DisplayName("Test string concatenation")
    public void testConcatenation() {
        String result = "Hello" + " " + "World";
        assertEquals("Hello World", result);
        assertNotNull(result);
        assertTrue(result.contains("World"));
    }

    @Test
    public void testStringLength() {
        String str = "Testing";
        assertEquals(7, str.length());
        assertFalse(str.isEmpty());
    }

    @Test
    public void testNullString() {
        String str = null;
        assertNull(str);
    }

    @Test
    public void testStringComparison() {
        String str1 = "test";
        String str2 = "test";
        String str3 = new String("test");
        
        assertEquals(str1, str2);
        assertEquals(str1, str3);
        assertSame(str1, str2);
        assertNotSame(str1, str3);
    }

    @Test
    public void testThrowsException() {
        assertThrows(NullPointerException.class, () -> {
            String str = null;
            str.length();
        });
    }

    @Test
    public void testMultipleAssertions() {
        String input = "JUnit Testing";
        
        assertAll("String properties",
            () -> assertEquals(13, input.length()),
            () -> assertTrue(input.startsWith("JUnit")),
            () -> assertTrue(input.endsWith("Testing")),
            () -> assertFalse(input.isEmpty())
        );
    }

    @Test
    public void testArrayEquals() {
        String[] expected = {"A", "B", "C"};
        String[] actual = {"A", "B", "C"};
        assertArrayEquals(expected, actual);
    }
}

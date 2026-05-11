package com.example;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for IntsList class.
 */
public class IntsListTest {

    @Test
    public void test1() {
        IntsList l = new IntsList();
        l.add(10);
        l.add(20);
        assertEquals(2, l.getSize());
    }

    @Test
    public void testAddAndGetFirst() {
        IntsList l = new IntsList();
        l.add(5);
        assertEquals(5, l.getFirst());
    }

    @Test
    public void testAddAndSize() {
        IntsList l = new IntsList();
        l.add(10);
        l.add(20);
        l.add(30);
        assertEquals(3, l.getSize());
    }

    @Test
    public void testIsEmpty() {
        IntsList l = new IntsList();
        assertTrue(l.isEmpty());
    }

    @Test
    public void testNotEmpty() {
        IntsList l = new IntsList();
        l.add(1);
        assertFalse(l.isEmpty());
    }

    @Test
    public void testGetHeader() {
        IntsList l = new IntsList();
        l.add(100);
        assertNotNull(l.getHeader());
    }

    @Test
    public void testHeaderItem() {
        IntsList l = new IntsList();
        l.add(42);
        assertEquals(42, l.getHeader().item);
    }

    @Test
    public void testHeaderNext() {
        IntsList l = new IntsList();
        l.add(1);
        l.add(2);
        assertNotNull(l.getHeader().next);
    }

    @Test
    public void testGet() {
        IntsList l = new IntsList();
        l.add(10);
        l.add(20);
        l.add(30);
        assertEquals(20, l.get(1));
    }

    @Test
    public void testMultipleFields() {
        IntsList l = new IntsList();
        l.add(7);
        l.add(8);
        assertTrue(l.getHeader() != null && l.getSize() == 2);
    }

    @Test
    public void testAllFields() {
        IntsList l = new IntsList();
        l.add(1);
        l.add(2);
        assertTrue(l.getHeader().item == 1 && l.getHeader().next != null && l.getSize() == 2 && l.getHeader().next.item == 2);
    }

    @Test
    public void testCheckSize() {
        IntsList l = new IntsList();
        l.add(1);
        l.add(2);
        l.add(3);
        assertTrue(l.checkSize());
    }
}

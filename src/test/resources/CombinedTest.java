package com.example;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class that tests both IntsList and Person classes.
 * This demonstrates the StateFieldCoverage metric with multiple target classes.
 */
public class CombinedTest {

    @Test
    public void testIntsListOnly() {
        IntsList list = new IntsList();
        list.add(10);
        list.add(20);
        assertEquals(2, list.getSize());
    }

    @Test
    public void testPersonOnly() {
        Person person = new Person("Alice", 30);
        assertEquals("Alice", person.getName());
        assertEquals(30, person.getAge());
    }

    @Test
    public void testBothClasses() {
        IntsList list = new IntsList();
        list.add(1);
        list.add(2);
        
        Person person = new Person("Bob", 25);
        person.setAddress(new Person.Address("Home", "123 Main St", "Boston"));
        
        // Assert on both classes
        assertTrue(list.getSize() == 2 && person.getAge() == 25);
        assertEquals("Home", person.getAddress().getName());
        assertEquals(1, list.getHeader().item);
    }

    @Test
    public void testComprehensiveCoverage() {
        IntsList list = new IntsList();
        list.add(5);
        list.add(10);
        list.add(15);
        
        Person person = new Person("Charlie", 35);
        person.setAddress(new Person.Address("Work", "456 Oak Ave", "NYC"));
        
        // Comprehensive assertions covering many fields from both classes
        assertTrue(list.getHeader().item == 5 &&
                   list.getHeader().next.item == 10 &&
                   list.getSize() == 3 &&
                   person.getName().equals("Charlie") &&
                   person.getAge() == 35 &&
                   person.getAddress().getStreet().equals("456 Oak Ave") &&
                   person.getAddress().getCity().equals("NYC"));
    }
}

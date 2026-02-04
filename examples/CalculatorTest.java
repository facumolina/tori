package com.example;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class CalculatorTest {

    @Test
    public void testAddition() {
        Calculator calc = new Calculator();
        int result = calc.add(2, 3);
        assertEquals(5, result);
        assertTrue(result > 0);
    }

    @Test
    public void testSubtraction() {
        Calculator calc = new Calculator();
        int result = calc.subtract(5, 3);
        assertEquals(2, result);
        assertNotNull(calc);
    }

    @Test
    public void testMultiplication() {
        Calculator calc = new Calculator();
        int result = calc.multiply(4, 5);
        assertEquals(20, result);
        assertFalse(result < 0);
    }

    @Test
    public void testDivision() {
        Calculator calc = new Calculator();
        assertThrows(ArithmeticException.class, () -> {
            calc.divide(10, 0);
        });
    }

    @Test
    public void testWithoutAssertions() {
        Calculator calc = new Calculator();
        int result = calc.add(1, 1);
        // No assertions here
    }
}

package com.example.test;

import com.example.package1.MainClass;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test file to manually verify package-aware class resolution.
 */
public class MainClassTest {
    
    @Test
    public void testMainClassFields() {
        MainClass obj = new MainClass();
        
        // This assertion accesses fields from MainClass
        assertTrue(obj.getId() >= 0 && obj.getName() != null);
        
        // This assertion accesses field from ReferencedClass (via imported dependency)
        assertTrue(obj.getDependency() != null || obj.getId() > 0);
    }
}

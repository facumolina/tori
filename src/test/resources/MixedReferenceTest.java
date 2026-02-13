package com.example.test;

import com.example.package1.MixedReferenceClass;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test file to manually verify mixed reference resolution.
 */
public class MixedReferenceTest {
    
    @Test
    public void testMixedReferences() {
        MixedReferenceClass obj = new MixedReferenceClass();
        
        // This assertion accesses name from MixedReferenceClass
        assertTrue(obj.getName() != null);
        
        // This assertion accesses both localDep (same package) and importedDep (imported)
        assertTrue(obj.getLocalDep() != null || obj.getImportedDep() != null);
    }
}

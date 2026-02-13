package com.example.package1;

import com.example.package2.ReferencedClass;

/**
 * Test class with mixed references: same package and imported.
 */
public class MixedReferenceClass {
    private String name;
    private LocalReferenceClass localDep;  // Same package, no import
    private ReferencedClass importedDep;   // Different package, imported
    
    public String getName() {
        return name;
    }
    
    public LocalReferenceClass getLocalDep() {
        return localDep;
    }
    
    public ReferencedClass getImportedDep() {
        return importedDep;
    }
}

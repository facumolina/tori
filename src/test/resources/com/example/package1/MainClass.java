package com.example.package1;

import com.example.package2.ReferencedClass;

/**
 * Test class with referenced class in different package.
 * Used to test package-aware class path resolution.
 */
public class MainClass {
    private int id;
    private String name;
    private ReferencedClass dependency;
    
    public int getId() {
        return id;
    }
    
    public String getName() {
        return name;
    }
    
    public ReferencedClass getDependency() {
        return dependency;
    }
}

package com.example.package2;

/**
 * Referenced class in a different package.
 * Used to test package-aware class path resolution.
 */
public class ReferencedClass {
    private double value;
    private boolean active;
    
    public double getValue() {
        return value;
    }
    
    public boolean isActive() {
        return active;
    }
}

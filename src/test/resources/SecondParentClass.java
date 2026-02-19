package com.example;

/**
 * A base class used to test coverage of inherited fields through multiple levels.
 */
public class SecondParentClass {
    protected int secondParentField;

    public SecondParentClass(int secondParentField) {
        this.secondParentField = secondParentField;
    }

    public int getSecondParentField() {
        return secondParentField;
    }
}

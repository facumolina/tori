package com.example;

/**
 * A child class used to test coverage of inherited fields.
 */
public class ChildClass extends ParentClass {
    private int childField;

    public ChildClass(int childField, String parentField, int secondParentField) {
        super(parentField, secondParentField);
        this.childField = childField;
    }

    public int getChildField() {
        return childField;
    }
}

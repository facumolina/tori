package com.example;

/**
 * A parent class used to test coverage of inherited fields.
 */
public class ParentClass extends SecondParentClass {
    private String parentField;

    public ParentClass(String parentField, int secondParentField) {
        super(secondParentField);
        this.parentField = parentField;
    }

    public String getParentField() {
        return parentField;
    }
}

package com.example;

import java.util.List;
import java.util.ArrayList;

/**
 * A class with both static and instance iterable (collection) fields,
 * used for testing the interaction between include_static_fields and
 * iterable_field_tracking configuration options in StateFieldCoverage.
 */
public class ClassWithStaticIterableField {

    public static List<String> staticItems = new ArrayList<>();
    private List<String> instanceItems;
    private int size;

    public ClassWithStaticIterableField() {
        this.instanceItems = new ArrayList<>();
        this.size = 0;
    }

    public void addItem(String item) {
        instanceItems.add(item);
        staticItems.add(item);
        size++;
    }

    public List<String> getInstanceItems() {
        return instanceItems;
    }

    public static List<String> getStaticItems() {
        return staticItems;
    }

    public int getSize() {
        return size;
    }
}

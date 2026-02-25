package com.example;

/**
 * A simple class with both static and instance fields, used for testing
 * the include_static_fields configuration option in StateFieldCoverage.
 */
public class ClassWithStaticFields {

    public static int staticCount = 0;
    public static final String DEFAULT_NAME = "default";
    private int instanceValue;
    private String name;

    public ClassWithStaticFields(int value, String name) {
        this.instanceValue = value;
        this.name = name;
        staticCount++;
    }

    public int getInstanceValue() {
        return instanceValue;
    }

    public String getName() {
        return name;
    }

    public static int getStaticCount() {
        return staticCount;
    }

    public static String getDefaultName() {
        return DEFAULT_NAME;
    }
}

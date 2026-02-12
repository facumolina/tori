package test;

public class TestClassWithMissingDep {
    private int value;
    private NonExistentClass dependency;
    
    public int getValue() {
        return value;
    }
}

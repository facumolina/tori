package multilevel;

/**
 * Intermediate class that depends on ClassC.
 * This is a transitive dependency from ClassA.
 */
public class ClassB {
    private String name;
    private ClassC nestedDependency;
    
    public String getName() {
        return name;
    }
    
    public ClassC getNestedDependency() {
        return nestedDependency;
    }
}

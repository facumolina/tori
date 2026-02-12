package multilevel;

/**
 * Root class that depends on ClassB.
 * Used to test transitive dependency tracking.
 */
public class ClassA {
    private int id;
    private ClassB dependency;
    
    public int getId() {
        return id;
    }
    
    public ClassB getDependency() {
        return dependency;
    }
}

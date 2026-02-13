package multilevel;

/**
 * Leaf class with no external dependencies.
 * This is a transitive dependency from ClassA through ClassB.
 */
public class ClassC {
    private double value;
    private boolean active;
    
    public double getValue() {
        return value;
    }
    
    public boolean isActive() {
        return active;
    }
}

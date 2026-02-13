package multilevel;

/**
 * Class that depends on an existing class which has a missing dependency.
 */
public class ClassWithTransitiveMissingDep {
    private int count;
    private ClassWithMissingDep dependency;
    
    public int getCount() {
        return count;
    }
    
    public ClassWithMissingDep getDependency() {
        return dependency;
    }
}

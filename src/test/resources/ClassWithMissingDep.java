package multilevel;

/**
 * Class that has a missing dependency.
 */
public class ClassWithMissingDep {
    private String label;
    private MissingTransitiveClass missing;
    
    public String getLabel() {
        return label;
    }
}

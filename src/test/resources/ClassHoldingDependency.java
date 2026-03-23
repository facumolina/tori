/**
 * A target class that holds a field whose type ({@link DependencyChild}) extends another
 * concrete class ({@link DependencyBase}).
 *
 * <p>Used to test that when resolving target state fields the inherited fields of a
 * dependency class are attributed to the dependency class (DependencyChild), not to its
 * concrete parent (DependencyBase), unless include_concrete_parent_class_fields is enabled.
 */
public class ClassHoldingDependency {
    private DependencyChild dep;

    public ClassHoldingDependency(DependencyChild dep) {
        this.dep = dep;
    }

    public DependencyChild getDep() {
        return dep;
    }
}

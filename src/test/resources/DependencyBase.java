/**
 * A concrete (non-abstract) base class used as the parent of a field-type dependency class.
 * Used to test that inherited fields of a dependency are attributed to the dependency class,
 * not to this parent, when include_concrete_parent_class_fields is false (the default).
 */
public class DependencyBase {
    private int baseData;

    public DependencyBase(int baseData) {
        this.baseData = baseData;
    }

    public int getBaseData() {
        return baseData;
    }
}

/**
 * A concrete child class extending {@link DependencyBase}.
 * Used as a field type in target classes to test that inherited fields are attributed to
 * this class (DependencyChild), not to its parent (DependencyBase).
 */
public class DependencyChild extends DependencyBase {
    private String childData;

    public DependencyChild(String childData, int baseData) {
        super(baseData);
        this.childData = childData;
    }

    public String getChildData() {
        return childData;
    }
}

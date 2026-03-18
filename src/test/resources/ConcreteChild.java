/**
 * A concrete child class that extends a concrete (non-abstract) parent class.
 * Used to test the include_concrete_parent_class_fields configuration property.
 */
public class ConcreteChild extends ConcreteBase {
    private String childField;

    public ConcreteChild(String childField, int baseField) {
        super(baseField);
        this.childField = childField;
    }

    public String getChildField() {
        return childField;
    }
}

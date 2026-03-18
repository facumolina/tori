/**
 * A concrete (non-abstract) base class used to test the
 * include_concrete_parent_class_fields configuration property.
 */
public class ConcreteBase {
    private int baseField;

    public ConcreteBase(int baseField) {
        this.baseField = baseField;
    }

    public int getBaseField() {
        return baseField;
    }
}

/**
 * A list of Shape objects backed by AbstractObjectList.
 * It has no fields of its own – all state is inherited from AbstractObjectList
 * (objects : transient Object[], size : int).
 * Mimics the JFreeChart ShapeList class.
 */
public class ShapeList extends AbstractObjectList {

    public ShapeList() {
        super();
    }

    /**
     * Returns the shape stored at the given index, or null if none is set.
     */
    public Object getShape(int index) {
        return get(index);
    }

    /**
     * Stores a shape at the given index.
     */
    public void setShape(int index, Object shape) {
        set(index, shape);
    }
}

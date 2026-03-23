/**
 * Base class holding a transient array and a size counter.
 * Mimics the JFreeChart AbstractObjectList pattern.
 * Used in tests that verify inherited fields (including transient ones) are
 * attributed to the concrete / target class.
 */
public class AbstractObjectList {

    private transient Object[] objects;

    private int size = 0;

    public AbstractObjectList() {
        this.objects = new Object[0];
        this.size = 0;
    }

    public Object get(int index) {
        if (index < 0 || index >= size) {
            return null;
        }
        return objects[index];
    }

    public void set(int index, Object object) {
        if (index >= size) {
            Object[] enlarged = new Object[index + 1];
            System.arraycopy(objects, 0, enlarged, 0, size);
            objects = enlarged;
            size = index + 1;
        }
        objects[index] = object;
    }

    public int size() {
        return size;
    }

    public Object[] getObjects() {
        return objects;
    }

    public int getSize() {
        return size;
    }
}

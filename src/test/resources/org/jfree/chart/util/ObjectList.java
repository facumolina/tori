/**
 * A list of objects backed by AbstractObjectList.
 * It has no fields of its own – all state is inherited from AbstractObjectList
 * (objects : transient Object[], size : int).
 * Mimics the JFreeChart ObjectList class.
 */
package org.jfree.chart.util;

public class ObjectList extends AbstractObjectList {

    public ObjectList() {
        super();
    }

    /**
     * Returns the object stored at the given index, or null if none is set.
     */
    public Object getObject(int index) {
        return get(index);
    }

    /**
     * Stores an object at the given index.
     */
    public void setObject(int index, Object object) {
        set(index, object);
    }
}

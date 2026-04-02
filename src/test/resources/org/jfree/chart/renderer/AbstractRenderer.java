/**
 * Abstract base renderer that owns a ShapeList.
 * The shapeList field is private, so it is only accessible via getters.
 * Mimics the JFreeChart AbstractRenderer class.
 * Used in tests where AbstractCategoryItemRenderer is the target class and
 * shapeList must be attributed to it even though the field is declared here.
 */
package org.jfree.chart.renderer;

import org.jfree.chart.util.ObjectList;
import org.jfree.chart.util.ShapeList;

public abstract class AbstractRenderer {

    private ShapeList shapeList;

    private ObjectList objectList;

    public AbstractRenderer() {
        this.shapeList = new ShapeList();
    }

    public ShapeList getShapeList() {
        return shapeList;
    }

    protected void setShapeList(ShapeList shapeList) {
        this.shapeList = shapeList;
    }

    public ObjectList getObjectList() {
        return objectList;
    }

    protected void setObjectList(ObjectList objectList) {
        this.objectList = objectList;
    }
}

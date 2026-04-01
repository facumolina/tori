/**
 * Abstract renderer for category items. Has no fields of its own.
 * All state is inherited from AbstractRenderer (shapeList : ShapeList).
 * Mimics the JFreeChart AbstractCategoryItemRenderer class.
 *
 * Used as the target class in StateFieldCoverage tests that verify:
 *  - inherited private fields are discovered (AbstractRenderer.shapeList)
 *  - those fields are attributed to the target class (AbstractCategoryItemRenderer.shapeList)
 *  - nested field tracking follows the ShapeList -> AbstractObjectList hierarchy
 */
public abstract class AbstractCategoryItemRenderer extends AbstractRenderer {

    /** The plot that the renderer is assigned to. */
    private CategoryPlot plot;

    public AbstractCategoryItemRenderer() {
        super();
    }
}

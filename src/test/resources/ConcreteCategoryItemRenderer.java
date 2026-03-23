/**
 * Minimal concrete subclass of AbstractCategoryItemRenderer.
 * Used only to instantiate the hierarchy in test-case strings so that
 * assess() can analyse field accesses; the target class remains
 * AbstractCategoryItemRenderer.
 */
public class ConcreteCategoryItemRenderer extends AbstractCategoryItemRenderer {

    public ConcreteCategoryItemRenderer() {
        super();
    }
}

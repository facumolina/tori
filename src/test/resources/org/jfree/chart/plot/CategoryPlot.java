package org.jfree.chart.plot;

import org.jfree.chart.util.ObjectList;

public class CategoryPlot {

    private Object orientation;

    private int axisOffset;

    private ObjectList domainAxes;

    /**
     * Tests the plot for equality with an arbitrary object.
     *
     * @param obj  the object to test against (<code>null</code> permitted).
     *
     * @return A boolean.
     */
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof CategoryPlot)) {
            return false;
        }
        CategoryPlot that = (CategoryPlot) obj;
        if (this.orientation != that.orientation) {
            return false;
        }
        if (!(this.axisOffset == that.axisOffset)) {
            return false;
        }
        if (!this.domainAxes.equals(that.domainAxes)) {
            return false;
        }
        return true;
    }
}
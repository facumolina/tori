/**
 * Abstract class with fields to be inherited by concrete subclasses.
 * Used in tests that verify inherited fields are attributed to the concrete class.
 */
public abstract class AbstractShape {
    protected double area;
    protected String color;
    
    public AbstractShape(String color) {
        this.color = color;
        this.area = 0.0;
    }
    
    public double getArea() {
        return area;
    }
    
    public String getColor() {
        return color;
    }
    
    public abstract void computeArea();
}

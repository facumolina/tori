/**
 * Concrete class extending an abstract parent class.
 * Used in tests that verify inherited fields are attributed to the concrete class (Circle),
 * not the abstract parent (AbstractShape).
 */
public class Circle extends AbstractShape {
    private double radius;
    
    public Circle(double radius, String color) {
        super(color);
        this.radius = radius;
    }
    
    @Override
    public void computeArea() {
        this.area = Math.PI * radius * radius;
    }
    
    public double getRadius() {
        return radius;
    }
}

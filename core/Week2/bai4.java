import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class bai4 {
    public static void main(String[] args) {
        Shape c = new Circle(2);
        Shape r = new Rectangle(3,4);
        Shape t = new Triangle(5);

        List<Shape> shapes = new ArrayList<>(List.of(c, r, t));
        shapes.sort(Comparator.comparingDouble(Shape::area));
        System.out.println(shapes);
    }

}

abstract class Shape {
    abstract double area();

    abstract double perimeter();

    void describe() {
        System.out.println(getClass().getSimpleName()
            + " has area: " + area() + "; perimeter: " + perimeter());
    }
}

class Circle extends Shape {
    double radius;

    public Circle(double radius) {
        this.radius = radius;
    }

    @Override
    double area() {
        return Math.PI * radius * radius;
    }

    @Override
    double perimeter() {
        return Math.PI * radius;
    }

}

class Rectangle extends Shape {
    double length;
    double width;

    public Rectangle(double length, double width) {
        this.length = length;
        this.width = width;
    }

    @Override
    double area() {
        return length * width;
    }

    @Override
    double perimeter() {
        return (length + width) * 2;
    }


}

class Triangle extends Shape {
    double n;

    public Triangle(double n) {
        this.n = n;
    }

    @Override
    double area() {
        return n * n;
    }

    @Override
    double perimeter() {
        return n * 4;
    }

}
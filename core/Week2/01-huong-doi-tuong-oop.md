# Tuần 2: Hướng đối tượng (OOP)

> Tài liệu tổng hợp kiến thức mục 2 trong lộ trình `java-core-roadmap.md`.

---

## 1. Bốn tính chất của OOP

### 1.1. Encapsulation (Tính đóng gói)
Giấu chi tiết dữ liệu bên trong class, chỉ cho phép truy cập/thay đổi thông qua method công khai (getter/setter). Giúp bảo vệ tính toàn vẹn dữ liệu (invariant).

```java
public class Account {
    private double balance; // private -> không cho truy cập trực tiếp từ ngoài

    public double getBalance() {
        return balance;
    }

    public void deposit(double amount) {
        if (amount <= 0) throw new IllegalArgumentException("Amount must be positive");
        balance += amount;
    }
}
```

### 1.2. Inheritance (Tính kế thừa)
Class con (`subclass`) kế thừa field/method từ class cha (`superclass`) bằng `extends`, tái sử dụng code và mở rộng hành vi.

```java
class Animal {
    void eat() { System.out.println("Animal eats"); }
}

class Dog extends Animal {
    void bark() { System.out.println("Dog barks"); }
}
```
- Java chỉ hỗ trợ **đơn kế thừa** (single inheritance) với class — 1 class chỉ extends được 1 class cha. Đa kế thừa hành vi được giải quyết qua `interface`.

### 1.3. Polymorphism (Tính đa hình)
Một hành động có thể biểu hiện khác nhau tùy đối tượng thực thi. Hai loại:
- **Compile-time (overloading)**: cùng tên method, khác tham số → quyết định lúc biên dịch.
- **Runtime (overriding)**: subclass ghi đè method của superclass → quyết định lúc chạy dựa trên object thực tế (dynamic binding).

```java
Animal a = new Dog(); // reference kiểu Animal, object thực tế là Dog
a.eat(); // gọi method nào phụ thuộc vào object thực tế (runtime polymorphism)
```

### 1.4. Abstraction (Tính trừu tượng)
Chỉ expose những gì cần thiết, ẩn chi tiết triển khai phức tạp. Thực hiện qua `abstract class` và `interface`.

```java
abstract class Shape {
    abstract double area(); // chưa biết cách tính cụ thể, để subclass tự định nghĩa
}

class Circle extends Shape {
    double radius;
    Circle(double radius) { this.radius = radius; }
    double area() { return Math.PI * radius * radius; }
}
```

---

## 2. Class, Object, Constructor

### Class & Object
- **Class**: bản thiết kế (blueprint) định nghĩa field + method.
- **Object**: thực thể cụ thể được tạo ra từ class bằng `new`, tồn tại trên Heap.

### Constructor
- Constructor cùng tên với class, không có kiểu trả về, chạy khi `new`.
- **Default constructor**: nếu không khai báo constructor nào, compiler tự sinh 1 constructor không tham số.
- **Overloaded constructor**: nhiều constructor khác tham số.
- **Constructor chaining**:
  - `this(...)`: gọi constructor khác trong cùng class.
  - `super(...)`: gọi constructor của class cha (luôn là dòng đầu tiên, ngầm định nếu không viết).

```java
class Person {
    String name;
    int age;

    Person() {
        this("Unknown", 0); // gọi constructor khác trong cùng class
    }

    Person(String name, int age) {
        this.name = name;
        this.age = age;
    }
}

class Employee extends Person {
    double salary;

    Employee(String name, int age, double salary) {
        super(name, age); // gọi constructor của Person, phải là dòng đầu tiên
        this.salary = salary;
    }
}
```

---

## 3. Method Overloading vs Overriding

| Tiêu chí | Overloading | Overriding |
|---|---|---|
| Vị trí | Cùng 1 class (hoặc class con định nghĩa thêm) | Class con ghi đè method của class cha |
| Chữ ký (signature) | Khác tham số (số lượng/kiểu) | Giống hệt chữ ký |
| Kiểu trả về | Có thể khác | Phải giống hoặc là subtype (covariant return type) |
| Binding | Compile-time (static binding) | Runtime (dynamic binding) |
| Từ khóa | Không cần | Nên dùng `@Override` để compiler kiểm tra |
| Phạm vi truy cập | Không ràng buộc | Không được thu hẹp hơn method cha |

```java
class MathUtil {
    int add(int a, int b) { return a + b; }
    double add(double a, double b) { return a + b; } // overloading
}

class Parent {
    void greet() { System.out.println("Hello from Parent"); }
}

class Child extends Parent {
    @Override
    void greet() { System.out.println("Hello from Child"); } // overriding
}
```

---

## 4. `static` vs Instance

| | `static` | Instance |
|---|---|---|
| Thuộc về | Class (dùng chung cho mọi object) | Từng object riêng biệt |
| Truy cập | `ClassName.member` | `object.member` |
| Bộ nhớ | Method Area (1 bản duy nhất) | Heap (mỗi object 1 bản) |
| Có thể dùng `this`? | Không | Có |

```java
class Counter {
    static int totalCount = 0; // dùng chung cho mọi object
    int id;                     // riêng của từng object

    Counter() {
        totalCount++;
        id = totalCount;
    }

    static void printTotal() { // static method: không truy cập được instance field
        System.out.println("Total: " + totalCount);
    }

    static { // static block: chạy 1 lần khi class được load
        System.out.println("Counter class loaded");
    }

    static class Helper { // static nested class
        void help() { System.out.println("Helping..."); }
    }
}
```

---

## 5. `final` với biến, method, class

| Áp dụng cho | Ý nghĩa |
|---|---|
| Biến (`final int x = 5`) | Không thể gán lại giá trị sau khi khởi tạo (hằng số) |
| Method (`final void m()`) | Không cho subclass override |
| Class (`final class C`) | Không cho class khác extends (VD: `String`, `Integer` đều là final class) |

```java
final class ImmutablePoint {
    final int x, y; // final field: chỉ gán 1 lần (trong constructor)

    ImmutablePoint(int x, int y) {
        this.x = x;
        this.y = y;
    }

    final void print() { // không cần thiết vì class đã final, chỉ minh họa
        System.out.println(x + ", " + y);
    }
}
```
> Lưu ý: `final` trên reference chỉ khóa việc gán lại reference, **không** khiến object đó immutable (field bên trong object vẫn có thể đổi nếu không phải final).

---

## 6. Abstract Class vs Interface

| Tiêu chí | Abstract Class | Interface |
|---|---|---|
| Từ khóa | `abstract class` | `interface` |
| Kế thừa | 1 class chỉ extends 1 abstract class | 1 class implements được nhiều interface |
| Constructor | Có | Không |
| Field | Có thể có field thường (instance state) | Mặc định `public static final` (hằng số) |
| Method | Có thể có method cụ thể + abstract method | Trước Java 8: chỉ abstract method. Từ Java 8+: có `default`, `static` method. Từ Java 9+: có `private` method |
| Khi nào dùng | Khi các class con có quan hệ "is-a" chặt và cần chia sẻ state/code chung | Khi định nghĩa "hợp đồng" hành vi (capability), cho phép nhiều class không liên quan cùng implement |

```java
interface Flyable {
    void fly(); // abstract method (implicit public abstract)

    default void land() { // default method (Java 8+): có sẵn implementation
        System.out.println("Landing...");
    }

    static Flyable createDefault() { // static method (Java 8+)
        return () -> System.out.println("Default flying");
    }

    private void log(String msg) { // private method (Java 9+): chỉ dùng nội bộ interface
        System.out.println("[LOG] " + msg);
    }
}

abstract class Vehicle {
    protected String name;
    Vehicle(String name) { this.name = name; } // abstract class CÓ constructor

    abstract void move(); // abstract method: bắt buộc subclass triển khai

    void honk() { System.out.println(name + " honks"); } // method cụ thể, dùng chung
}

class Airplane extends Vehicle implements Flyable {
    Airplane(String name) { super(name); }
    void move() { System.out.println(name + " moves on runway"); }
    public void fly() { System.out.println(name + " flies"); }
}
```

---

## 7. Nested Class

| Loại | Đặc điểm |
|---|---|
| **Static nested class** | Khai báo `static` bên trong class ngoài, không giữ reference tới instance của outer class, truy cập như `Outer.Nested` |
| **Inner class (non-static)** | Gắn với 1 instance cụ thể của outer class, giữ reference ngầm tới outer (`Outer.this`), tạo bằng `outer.new Inner()` |
| **Local class** | Khai báo bên trong 1 method/block, chỉ dùng được trong phạm vi đó |
| **Anonymous class** | Class không tên, khai báo và khởi tạo cùng lúc, thường dùng để triển khai nhanh 1 interface/abstract class |

```java
class Outer {
    int outerField = 10;

    static class StaticNested { // không cần instance của Outer
        void show() { System.out.println("Static nested class"); }
    }

    class Inner { // cần instance của Outer
        void show() { System.out.println("Access outer field: " + outerField); }
    }

    void method() {
        class LocalClass { // local class trong method
            void show() { System.out.println("Local class"); }
        }
        new LocalClass().show();

        Runnable r = new Runnable() { // anonymous class
            @Override
            public void run() { System.out.println("Anonymous class running"); }
        };
        r.run();
    }
}

// Sử dụng:
Outer.StaticNested sn = new Outer.StaticNested();
Outer outer = new Outer();
Outer.Inner inner = outer.new Inner();
```

---

## 8. Enum nâng cao

Enum trong Java không chỉ là danh sách hằng số — có thể có field, constructor, method, và implement interface.

```java
interface Describable {
    String describe();
}

enum Planet implements Describable {
    MERCURY(3.303e+23, 2.4397e6),
    VENUS(4.869e+24, 6.0518e6),
    EARTH(5.976e+24, 6.37814e6);

    private final double mass;   // field
    private final double radius;

    Planet(double mass, double radius) { // constructor (luôn private/package-private)
        this.mass = mass;
        this.radius = radius;
    }

    double surfaceGravity() { // method riêng
        final double G = 6.67300E-11;
        return G * mass / (radius * radius);
    }

    @Override
    public String describe() { // implement interface
        return name() + " has gravity " + surfaceGravity();
    }
}

// Enum với method riêng cho từng hằng số (constant-specific method)
enum Operation {
    PLUS {
        public int apply(int a, int b) { return a + b; }
    },
    MINUS {
        public int apply(int a, int b) { return a - b; }
    };

    public abstract int apply(int a, int b);
}
```
- `values()`, `valueOf(String)`, `ordinal()`, `name()` là các method có sẵn.
- Enum thường dùng để triển khai **Singleton pattern** an toàn (`enum Singleton { INSTANCE; }`).

---

## 9. Object class: `equals()`, `hashCode()`, `toString()`, `clone()`

Mọi class trong Java đều ngầm định `extends Object`. Các method quan trọng cần override:

### `toString()`
Trả về biểu diễn string của object, mặc định là `ClassName@hashCodeHex`.

```java
@Override
public String toString() {
    return "Person{name=" + name + ", age=" + age + "}";
}
```

### `equals()` và `hashCode()` — Contract bắt buộc đi cùng nhau
- **Reflexive**: `x.equals(x)` phải `true`.
- **Symmetric**: `x.equals(y) == y.equals(x)`.
- **Transitive**: `x.equals(y) && y.equals(z)` → `x.equals(z)`.
- **Consistent**: gọi nhiều lần cho kết quả giống nhau (nếu object không đổi).
- **Quy tắc vàng**: nếu `a.equals(b) == true` thì bắt buộc `a.hashCode() == b.hashCode()`. Ngược lại **không** bắt buộc (2 object có thể cùng hashCode nhưng không equals — gọi là *collision*).

```java
class Point {
    int x, y;

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Point)) return false;
        Point p = (Point) obj;
        return x == p.x && y == p.y;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y); // dùng java.util.Objects tiện lợi
    }
}
```
> Nếu chỉ override `equals()` mà quên `hashCode()` → object không hoạt động đúng khi làm key trong `HashMap`/`HashSet` (2 object equals nhau nhưng rơi vào bucket khác nhau).

### `clone()`
- Tạo bản sao object. Class phải implement `Cloneable` (marker interface), nếu không sẽ ném `CloneNotSupportedException`.
- Mặc định là **shallow copy** (copy field nguyên thủy trực tiếp, field object chỉ copy reference — 2 object dùng chung 1 object con bên trong).
- Muốn **deep copy** phải tự override và clone cả các field object bên trong.

```java
class Address implements Cloneable {
    String city;
    @Override
    protected Object clone() throws CloneNotSupportedException {
        return super.clone();
    }
}

class Person implements Cloneable {
    String name;
    Address address;

    @Override
    protected Object clone() throws CloneNotSupportedException {
        Person copy = (Person) super.clone(); // shallow copy
        copy.address = (Address) address.clone(); // deep copy thủ công cho field object
        return copy;
    }
}
```

---

## 10. `instanceof` và Pattern Matching

```java
Object obj = "Hello";

// Cách cũ
if (obj instanceof String) {
    String s = (String) obj; // phải cast thủ công
    System.out.println(s.length());
}

// Pattern matching (Java 16+)
if (obj instanceof String s) { // tự động cast vào biến s
    System.out.println(s.length());
}

// Kết hợp điều kiện
if (obj instanceof String s && s.length() > 3) {
    System.out.println("Long string: " + s);
}
```

---

## Câu hỏi tự kiểm tra
1. Giải thích 4 tính chất OOP bằng ví dụ thực tế khác với ví dụ trong tài liệu.
2. Constructor chaining bằng `this()` và `super()` khác nhau ở điểm nào? Vì sao `super()` luôn phải là dòng đầu tiên?
3. Overloading và overriding khác nhau ra sao về binding (compile-time vs runtime)?
4. Khi nào nên dùng abstract class, khi nào nên dùng interface? Cho 1 ví dụ thiết kế cụ thể.
5. Phân biệt static nested class và inner class — tại sao inner class cần 1 instance của outer class để khởi tạo?
6. Vì sao phải override `hashCode()` khi đã override `equals()`? Điều gì xảy ra nếu chỉ override 1 trong 2 khi dùng object làm key của `HashMap`?
7. Shallow copy và deep copy khác nhau như thế nào khi dùng `clone()`? Cho ví dụ minh họa lỗi thường gặp.

## Bài tập luyện tập
- Thiết kế hệ thống `Shape` (abstract class) với các subclass `Circle`, `Rectangle`, `Triangle` — mỗi class override `area()` và `toString()`.
- Viết 1 enum `Day` có method `isWeekend()` sử dụng constant-specific method hoặc field.
- Viết class `Employee` override đúng `equals()`/`hashCode()` theo `id`, sau đó thử đưa nhiều `Employee` vào `HashSet` để kiểm chứng không bị trùng.
- Viết ví dụ minh họa lỗi shallow copy: tạo 2 object bằng `clone()`, sửa field object con ở 1 bản, kiểm tra bản còn lại có bị ảnh hưởng không.

# Review: Bài 4 — Hệ thống `Shape` — Abstract class + Polymorphism

File được đánh giá: [bai4.java](bai4.java)
Đối chiếu với: [02-oop-bai-tap.md](02-oop-bai-tap.md) — Bài 4

## Kết luận nhanh

Code biên dịch và chạy được, nhưng **vi phạm đúng nguyên tắc cốt lõi mà bài học muốn dạy**: `describe()` được khai báo `abstract` và **lặp lại y hệt ở cả 3 subclass** thay vì là 1 method cụ thể dùng chung — điều này phá vỡ hoàn toàn tiêu chí "Hoàn thành khi" của đề bài (thêm shape mới mà không cần sửa `describe()`). Ngoài ra, `Triangle` dùng nhầm công thức của **hình vuông** thay vì công thức Heron cho tam giác 3 cạnh, `Circle.perimeter()` tính sai (thiếu hệ số 2), thiếu `toString()`, thiếu `List<Shape>`, và thiếu hoàn toàn phần sắp xếp theo `area()`.

---

## Lỗi nghiêm trọng theo yêu cầu đề bài

### 1. `describe()` bị khai báo `abstract` và lặp lại ở từng subclass — vi phạm chính nguyên tắc bài học muốn dạy
Đề bài mục 1 yêu cầu rõ: *"method cụ thể `describe()` in ra tên class + area + perimeter (**dùng chung cho mọi subclass, không override**)."* Code hiện tại làm ngược lại hoàn toàn:
```java
abstract class Shape {
    abstract double area();
    abstract double perimeter();
    abstract void description(); // <- BẮT BUỘC phải override, ngược lại với yêu cầu "không override"
}
```
Và mỗi subclass phải tự viết lại gần như y hệt:
```java
// Circle
void description() {
    System.out.println("Circle has area: "+ area() +"; perimeter: " +perimeter());
}
// Rectangle
void description() {
    System.out.println("Rectangle has area: "+ area() +"; perimeter: " +perimeter());
}
// Triangle — y hệt, chỉ đổi chữ "Triangle"
```
Đây chính là kiểu code mà bài học về **abstraction/Open-Closed principle** muốn tránh: 3 method gần như giống hệt nhau, chỉ khác đúng 1 chữ (tên shape). Hệ quả trực tiếp: tiêu chí "Hoàn thành khi" của đề bài — *"thêm được 1 shape mới (VD `Square`) mà **không cần sửa** code của `describe()`"* — **không thể đạt được**, vì mỗi shape mới đều phải tự viết thêm 1 bản sao của `describe()`.

**Sửa đúng** — biến `describe()` thành method **cụ thể** (không `abstract`) ngay trong `Shape`, dùng `getClass().getSimpleName()` để tự lấy tên class mà không cần hardcode:
```java
abstract class Shape {
    abstract double area();
    abstract double perimeter();

    // Method CỤ THỂ, dùng chung cho MỌI subclass — không override ở đâu cả
    void describe() {
        System.out.println(getClass().getSimpleName()
            + " has area: " + area() + "; perimeter: " + perimeter());
    }
}
```
Với cách này, `describe()` gọi `area()`/`perimeter()` — 2 method abstract — và tại runtime, JVM tự dispatch đúng phiên bản của object thực tế (đây mới đúng là **runtime polymorphism** đề bài muốn minh họa qua **1 method dùng chung duy nhất**, không phải 3 bản sao).

> Lưu ý thêm: đề bài đặt tên method là `describe()`, code hiện tại đặt tên `description()` — không phải lỗi nghiêm trọng, nhưng nên đổi lại cho khớp.

### 2. `Triangle` dùng nhầm công thức của hình vuông, không dùng công thức Heron như đề bài yêu cầu
Đề bài mục 2 yêu cầu tường minh: *"Triangle (**dùng công thức Heron cho tam giác 3 cạnh**)."* Code hiện tại:
```java
class Triangle extends Shape {
    double n;                                    // chỉ có 1 cạnh, không phải 3 cạnh
    double area() { return n * n; }              // đây là công thức diện tích HÌNH VUÔNG
    double perimeter() { return n * 4; }          // đây là công thức chu vi HÌNH VUÔNG
}
```
`Triangle` ở đây không hề đại diện cho 1 tam giác — nó tính toán y hệt logic của 1 hình vuông cạnh `n` (diện tích = cạnh², chu vi = 4×cạnh). Đây là lỗi khái niệm nghiêm trọng, không chỉ là thiếu sót cú pháp.

**Sửa đúng theo công thức Heron** (3 cạnh `a, b, c`):
```java
class Triangle extends Shape {
    double a, b, c;

    public Triangle(double a, double b, double c) {
        this.a = a; this.b = b; this.c = c;
    }

    @Override
    double perimeter() {
        return a + b + c;
    }

    @Override
    double area() {
        double s = perimeter() / 2; // nửa chu vi
        return Math.sqrt(s * (s - a) * (s - b) * (s - c)); // công thức Heron
    }
}
```

### 3. `Circle.perimeter()` tính sai — thiếu hệ số 2
```java
double perimeter() {
    return Math.PI * radius; // SAI — chu vi hình tròn = 2 * π * r
}
```
Chu vi (chu vi đường tròn) đúng phải là `2 * Math.PI * radius`, code hiện tại thiếu nhân 2 — kết quả tính ra chỉ bằng **một nửa** giá trị đúng. Với `Circle(2)` trong `main()`, code hiện in ra chu vi ≈ `6.28`, trong khi giá trị đúng phải là ≈ `12.57`.

**Sửa**: `return 2 * Math.PI * radius;`

### 4. Thiếu `toString()` ở cả 3 subclass
Đề bài mục 2 yêu cầu rõ: *"mỗi class override `area()`, `perimeter()`, `toString()`."* Code hiện tại hoàn toàn không có `toString()` ở `Circle`, `Rectangle`, hay `Triangle`.

### 5. Không dùng `List<Shape>` và vòng lặp như đề bài yêu cầu
Đề bài mục 3 yêu cầu: *"Tạo `List<Shape>` chứa cả 3 loại, dùng vòng lặp gọi `describe()` trên từng phần tử."* Code hiện tại dùng 3 biến riêng lẻ (`c`, `r`, `t`) và gọi `.description()` từng cái theo kiểu liệt kê thủ công, không hề tạo `List<Shape>`, không có vòng lặp:
```java
Shape c = new Circle(2);
Shape r = new Rectangle(3,4);
Shape t = new Triangle(5);
c.description();
r.description();
t.description();
```
Cách viết này vẫn thể hiện được polymorphism ở mức cơ bản (biến khai báo kiểu `Shape` nhưng gọi đúng method của object thực tế), nhưng chưa đúng cấu trúc dữ liệu đề bài yêu cầu — và thiếu cấu trúc này khiến yêu cầu tiếp theo (sắp xếp bằng `Comparator`) cũng không có chỗ để thực hiện.

### 6. Thiếu hoàn toàn phần sắp xếp `List<Shape>` theo `area()` tăng dần
Đề bài mục 4 yêu cầu: *"Sắp xếp `List<Shape>` theo `area()` tăng dần bằng `Comparator`."* Không có bất kỳ đoạn code nào liên quan đến sắp xếp trong bài nộp hiện tại.

**Cần bổ sung** (sau khi đã có `List<Shape>`):
```java
List<Shape> shapes = new ArrayList<>(List.of(c, r, t));
shapes.sort(Comparator.comparingDouble(Shape::area));
```

---

## Điểm đã làm đúng

- `Shape` là `abstract class` với `area()`/`perimeter()` khai báo `abstract` — đúng cấu trúc cơ bản.
- `Circle`, `Rectangle` override đúng `area()`/`perimeter()` với công thức chính xác (trừ `Circle.perimeter()` như đã nêu ở mục 3).
- Constructor của cả 3 class gán field đúng, không lỗi cú pháp.
- Dùng `@Override` đầy đủ cho các method override — thói quen tốt.
- Biến khai báo kiểu `Shape c = new Circle(2)` rồi gọi method qua biến đó — thể hiện đúng ý tưởng runtime polymorphism ở mức cơ bản.

---

## Bảng đối chiếu tiêu chí đề bài

| Yêu cầu đề bài | Trạng thái |
|---|---|
| 1. `describe()` cụ thể, dùng chung, không override | **Không đạt** — khai báo `abstract`, lặp lại ở cả 3 subclass |
| 2a. `Circle`/`Rectangle` override `area()`, `perimeter()` đúng công thức | Đạt phần lớn — riêng `Circle.perimeter()` sai (thiếu hệ số 2) |
| 2b. `Triangle` dùng công thức Heron 3 cạnh | **Không đạt** — dùng nhầm công thức hình vuông, chỉ nhận 1 tham số |
| 2c. Cả 3 override `toString()` | **Không đạt** — hoàn toàn chưa có |
| 3. `List<Shape>` + vòng lặp gọi `describe()` | **Không đạt** — dùng biến rời rạc, không có `List`/vòng lặp |
| 4. Sắp xếp theo `area()` bằng `Comparator` | **Không đạt** — hoàn toàn chưa có |
| Tiêu chí hoàn thành: thêm `Square` không cần sửa `describe()`/logic sort | Không thể đạt được với cấu trúc hiện tại (vì `describe()` chưa dùng chung, và chưa có logic sort nào để giữ nguyên) |

---

## Tổng kết
Bài nộp hiện tại mới dừng ở mức có 3 class con override đúng phần lớn `area()`/`perimeter()` (trừ `Circle.perimeter()` và toàn bộ `Triangle`), nhưng **chưa thể hiện đúng bản chất abstraction** mà bài học nhắm tới: `describe()` phải là 1 method **duy nhất, cụ thể, dùng chung** cho mọi shape — hiện tại nó bị biến thành `abstract` và lặp lại 3 lần. Đây là vấn đề quan trọng nhất cần sửa trước tiên, vì nó quyết định việc bài tập có đạt được tiêu chí Open/Closed hay không. Sau đó cần: sửa `Triangle` dùng đúng công thức Heron với 3 cạnh, sửa `Circle.perimeter()` (nhân thêm 2), bổ sung `toString()` cho cả 3 class, dùng `List<Shape>` thay vì biến rời rạc, và thêm logic sắp xếp bằng `Comparator.comparingDouble(Shape::area)`.

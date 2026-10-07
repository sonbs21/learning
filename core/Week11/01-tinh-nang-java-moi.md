# Tuần 11: Tính năng Java mới hơn (9-21)

> Tài liệu tổng hợp kiến thức mục 11 trong lộ trình `java-core-roadmap.md`.
> Một số nội dung đã được đề cập ở tuần trước sẽ chỉ nhắc lại ngắn gọn kèm liên kết, để tập trung vào phần còn thiếu.

---

## 1. `var` — Local Variable Type Inference (Java 10+)

Cho phép compiler **tự suy luận kiểu dữ liệu** của biến local dựa trên giá trị khởi tạo, giảm sự lặp lại không cần thiết khi kiểu đã rõ ràng từ vế phải.

```java
var name = "An";                          // suy luận: String
var count = 10;                            // suy luận: int
var list = new ArrayList<String>();        // suy luận: ArrayList<String>
var map = new HashMap<String, Integer>();  // suy luận: HashMap<String, Integer>

for (var i = 0; i < 10; i++) { }            // dùng được trong for-loop
for (var item : list) { }                    // dùng được trong enhanced for
```

### `var` KHÔNG phải là kiểu động (dynamic typing)
- `var` chỉ là **cú pháp rút gọn tại thời điểm biên dịch** — kiểu thực sự vẫn được compiler xác định và cố định **ngay khi biên dịch**, hoàn toàn khác với biến kiểu động trong JavaScript/Python (nơi kiểu có thể đổi lúc runtime).
- Sau khi suy luận, biến `var` vẫn là **static typed** bình thường — không thể gán giá trị khác kiểu sau đó.

```java
var x = 10;
// x = "hello"; // LỖI biên dịch! x đã được suy luận là int, không đổi kiểu được
```

### Giới hạn sử dụng `var`
```java
var x;              // LỖI - PHẢI có giá trị khởi tạo ngay để compiler suy luận được kiểu
var y = null;       // LỖI - không suy luận được kiểu cụ thể từ null
private var field;  // LỖI - CHỈ dùng được cho biến LOCAL, không dùng cho field, tham số method, kiểu trả về
public var method() { } // LỖI - không dùng cho kiểu trả về của method
```

### Khi nào nên/không nên dùng `var`?
- **Nên dùng**: khi kiểu đã rõ ràng từ vế phải (`var list = new ArrayList<String>()`), giúp code gọn hơn mà không mất thông tin.
- **Không nên dùng**: khi kiểu trả về của method không rõ ràng từ tên method (`var result = process(data);` — người đọc không biết `result` là kiểu gì nếu không xem lại khai báo `process()`) — làm giảm khả năng đọc code.

---

## 2. Record (Java 16+) — Immutable Data Carrier

### Vấn đề Record giải quyết
Trước Java 16, để tạo 1 class chỉ đơn giản **mang dữ liệu (data carrier)** như DTO, phải viết rất nhiều "boilerplate" (code lặp lại máy móc):

```java
// TRƯỚC Java 16 - phải viết đầy đủ constructor, getter, equals, hashCode, toString
final class Point {
    private final int x;
    private final int y;

    Point(int x, int y) { this.x = x; this.y = y; }
    public int getX() { return x; }
    public int getY() { return y; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Point)) return false;
        Point p = (Point) o;
        return x == p.x && y == p.y;
    }

    @Override
    public int hashCode() { return Objects.hash(x, y); }

    @Override
    public String toString() { return "Point[x=" + x + ", y=" + y + "]"; }
}
```

### Record — cùng nội dung chỉ trong 1 dòng
```java
record Point(int x, int y) { } // TỰ ĐỘNG sinh: constructor, getter (x(), y()), equals(), hashCode(), toString()

Point p1 = new Point(1, 2);
Point p2 = new Point(1, 2);

p1.x();                     // 1 - getter tự sinh, KHÔNG có tiền tố "get" (khác JavaBean convention)
p1.equals(p2);               // true - so sánh dựa trên GIÁ TRỊ mọi field, tự sinh đúng contract
p1.hashCode();                // tự sinh dựa trên tất cả field
p1.toString();                 // "Point[x=1, y=2]" - tự sinh định dạng rõ ràng
```

### Đặc điểm quan trọng của Record
- **Immutable hoàn toàn** — mọi field tự động là `private final`, không có setter, không thể sửa sau khi tạo.
- **Không thể `extends`** class khác (record ngầm định `extends java.lang.Record`), nhưng **CÓ THỂ implements interface**.
- **Không thể có thêm instance field** ngoài các field khai báo trong "header" của record (chỉ có thể thêm static field).

```java
record Point(int x, int y) implements Comparable<Point> {
    // Có thể thêm method, static field, static method, compact constructor
    static final Point ORIGIN = new Point(0, 0);

    @Override
    public int compareTo(Point other) {
        return Integer.compare(x + y, other.x + other.y);
    }

    // Compact constructor - validate dữ liệu đầu vào mà KHÔNG cần lặp lại tham số
    Point {
        if (x < 0 || y < 0) throw new IllegalArgumentException("Tọa độ phải không âm");
    }

    // Method tính toán thêm (không phải getter tự sinh)
    double distanceFromOrigin() {
        return Math.sqrt(x * x + y * y);
    }
}
```

### Khi nào nên dùng Record?
Phù hợp cho các "data carrier" thuần túy — DTO, value object, kết quả trả về của method (VD: `record Pair<A, B>(A first, B second) {}`) — nơi bản chất chỉ là **gói nhiều giá trị lại với nhau, không có hành vi nghiệp vụ phức tạp**. **Không** phù hợp cho entity có state thay đổi liên tục hoặc cần kế thừa.

---

## 3. Sealed Class (Java 17+)

### Vấn đề Sealed Class giải quyết
Trước Java 17, khi thiết kế 1 hệ thống phân cấp kiểu (type hierarchy) đóng — VD: "1 hình dạng chỉ có thể là Circle, Square, hoặc Triangle, không được phép có loại khác" — Java **không có cách nào ràng buộc** điều này ở compile-time (bất kỳ class nào ở bất kỳ đâu cũng có thể `extends`/`implements`).

### Cú pháp
```java
sealed interface Shape permits Circle, Square, Triangle { }
// hoặc: sealed abstract class Shape permits Circle, Square, Triangle { }

final class Circle implements Shape {       // final - không cho phép kế thừa tiếp
    double radius;
}

final class Square implements Shape {
    double side;
}

non-sealed class Triangle implements Shape { // non-sealed - MỞ LẠI, cho phép bất kỳ ai extends tiếp
    double base, height;
}
```

**Mỗi subclass được `permits` phải khai báo tường minh là 1 trong 3 loại:**
| Modifier | Ý nghĩa |
|---|---|
| `final` | Không cho phép kế thừa thêm nữa (đóng hoàn toàn ở đây) |
| `sealed` | Vẫn đóng, nhưng cho phép 1 tập con `permits` cụ thể tiếp theo (đóng thêm 1 tầng) |
| `non-sealed` | Mở lại hoàn toàn — bất kỳ class nào cũng được `extends`/`implements` tiếp từ đây |

### Lợi ích lớn nhất: kết hợp với Pattern Matching Switch — Exhaustiveness Checking
```java
double area(Shape shape) {
    return switch (shape) {
        case Circle c -> Math.PI * c.radius * c.radius;
        case Square s -> s.side * s.side;
        case Triangle t -> 0.5 * t.base * t.height;
        // KHÔNG cần "default"! Vì Shape là sealed, compiler BIẾT CHẮC đây là TOÀN BỘ khả năng
        // Nếu sau này thêm 1 subclass mới vào "permits" mà quên xử lý ở đây -> LỖI BIÊN DỊCH ngay
    };
}
```
> Đây là lợi ích cốt lõi: sealed class biến switch/pattern matching thành **"closed" và được compiler kiểm tra đầy đủ (exhaustive)** — giống khái niệm "algebraic data type" trong các ngôn ngữ lập trình hàm (Haskell, Kotlin `sealed class`, Rust `enum`).

---

## 4. Pattern Matching cho `instanceof` và `switch`

### Pattern Matching cho `instanceof` (Java 16+)
Đã giới thiệu ở [Week1/01-nen-tang-ngon-ngu.md](../Week1/01-nen-tang-ngon-ngu.md#5-toán-tử-operators) — nhắc lại nhanh:
```java
// Trước Java 16
if (obj instanceof String) {
    String s = (String) obj; // phải cast thủ công
}

// Từ Java 16 - tự động cast vào biến khai báo sẵn
if (obj instanceof String s) {
    System.out.println(s.length()); // dùng "s" trực tiếp, không cần cast
}
```

### Pattern Matching cho `switch` (Java 21+, chính thức hóa)
Mở rộng mạnh mẽ hơn nhiều so với `switch` truyền thống — cho phép match theo **kiểu dữ liệu**, kết hợp **record deconstruction** (giải cấu trúc record) và **guard condition** (`when`).

```java
sealed interface Shape permits Circle, Rectangle { }
record Circle(double radius) implements Shape { }
record Rectangle(double width, double height) implements Shape { }

double area(Shape shape) {
    return switch (shape) {
        case Circle c -> Math.PI * c.radius() * c.radius();
        case Rectangle r -> r.width() * r.height();
    };
}

// Record deconstruction (Java 21+) - "giải nén" trực tiếp field của record ngay trong case
double areaV2(Shape shape) {
    return switch (shape) {
        case Circle(double radius) -> Math.PI * radius * radius;               // lấy radius trực tiếp
        case Rectangle(double width, double height) -> width * height;          // lấy cả 2 field trực tiếp
    };
}

// Guard condition với "when" - thêm điều kiện lọc sau khi đã match kiểu
String describe(Object obj) {
    return switch (obj) {
        case Integer i when i > 0 -> "Positive integer: " + i;
        case Integer i when i < 0 -> "Negative integer: " + i;
        case Integer i -> "Zero";
        case String s when s.isEmpty() -> "Empty string";
        case String s -> "Non-empty string: " + s;
        case null -> "It's null";           // Java 21+ cho phép match cả null tường minh trong switch
        default -> "Unknown type";
    };
}
```

> Kết hợp **sealed class + record + pattern matching switch** là bộ 3 tính năng hiện đại nhất của Java để mô hình hóa dữ liệu theo phong cách "algebraic data type" — rất phổ biến trong các ngôn ngữ hàm, nay đã có mặt đầy đủ trong Java hiện đại.

---

## 5. Text Block (Java 15+)

Đã trình bày chi tiết ở [Week6/01-string-text-processing.md](../Week6/01-string-text-processing.md#6-text-block-java-15) — dùng `"""` để viết chuỗi nhiều dòng không cần escape `\n` thủ công, tự động xử lý thụt lề hợp lý.

```java
String json = """
        {
          "name": "An",
          "age": 25
        }
        """;
```

---

## 6. Module System (Java 9+) — Mức Khái Niệm

### Vấn đề Module System giải quyết
Trước Java 9, đơn vị đóng gói lớn nhất chỉ là **package** — nhưng package **không có khái niệm "public/private" ở cấp package**: bất kỳ package nào cũng truy cập được `public class` của package khác nếu nó nằm trên classpath, kể cả những class chỉ nên dùng **nội bộ (internal)** trong 1 thư viện. Điều này gây khó khăn khi:
- Xây dựng thư viện lớn (JDK, framework) muốn **ẩn hoàn toàn** các package nội bộ (internal implementation detail) khỏi người dùng bên ngoài.
- **JDK 8 trở về trước**: bản thân JDK cũng chỉ là 1 khối `rt.jar` khổng lồ chứa mọi thứ — không thể chỉ deploy phần cần dùng, làm runtime image nặng hơn cần thiết.

### Cấu trúc cơ bản: `module-info.java`
```java
// module-info.java đặt ở thư mục gốc của module
module com.myapp.core {
    requires java.base;          // mọi module ĐỀU tự động requires java.base (không cần khai báo tường minh)
    requires java.sql;            // module này cần dùng các class trong module java.sql

    exports com.myapp.core.api;   // CHỈ package này được các module khác truy cập
    // package com.myapp.core.internal (không exports) -> HOÀN TOÀN ẨN với module khác, dù là public class
}
```

### Các khái niệm chính
| Từ khóa | Ý nghĩa |
|---|---|
| `module` | Khai báo 1 module với tên (thường theo convention giống package: `com.company.module`) |
| `requires` | Module này **phụ thuộc** vào module khác (giống `import` nhưng ở cấp module) |
| `exports` | Package nào được **công khai** cho module khác sử dụng — package không `exports` bị ẩn hoàn toàn dù class bên trong là `public` |
| `requires transitive` | Module phụ thuộc CŨNG được "kế thừa" phụ thuộc này (module dùng module này không cần tự khai báo lại `requires`) |
| `opens` | Cho phép **reflection** truy cập vào package (kể cả private) — cần thiết cho framework dùng reflection nhiều (VD: Spring, Hibernate) |

### `java.base` — module nền tảng
Chứa các package cốt lõi nhất (`java.lang`, `java.util`, `java.io`...) — **mọi module khác đều tự động `requires java.base`** dù không khai báo tường minh (tương tự cách `java.lang` tự động được import trong mọi file `.java`).

### `jlink` — tạo Runtime Image tùy chỉnh
Nhờ có module system, có thể dùng `jlink` để tạo ra **1 JRE tối giản** chỉ chứa đúng những module ứng dụng thực sự cần — giảm đáng kể kích thước runtime khi đóng gói ứng dụng (đặc biệt hữu ích cho container/Docker image).

> **Ở mức khái niệm**: Module System là 1 tính năng lớn nhưng ít được dùng tường minh trong ứng dụng thông thường (đa số dự án Spring Boot/Maven/Gradle vẫn chạy trên **classpath truyền thống**, không bật module system) — quan trọng nhất là hiểu **được vấn đề nó giải quyết** (đóng gói/ẩn thông tin ở cấp module, giảm kích thước runtime) hơn là thực hành viết `module-info.java` chi tiết.

---

## Câu hỏi tự kiểm tra
1. `var` có phải là kiểu dữ liệu động (dynamic typing) không? Giải thích vì sao `var x = 10; x = "hello";` vẫn là lỗi biên dịch.
2. Kể ra 4 thứ được Record tự động sinh ra chỉ với 1 dòng khai báo. Vì sao Record không dùng tiền tố "get" cho getter?
3. Compact constructor trong Record dùng để làm gì? Cho ví dụ minh họa validate dữ liệu.
4. Phân biệt 3 modifier `final`/`sealed`/`non-sealed` khi khai báo subclass của 1 sealed class.
5. Vì sao kết hợp sealed class với pattern matching switch cho phép compiler kiểm tra "exhaustiveness" (đầy đủ mọi trường hợp) mà không cần `default`?
6. Record deconstruction trong switch pattern matching (Java 21+) khác gì so với chỉ match theo kiểu (`case Circle c ->`)?
7. Module system giải quyết vấn đề gì mà package thông thường (trước Java 9) không giải quyết được? `exports` khác `requires` ở điểm nào?

## Bài tập luyện tập
- Chuyển đổi 1 class DTO truyền thống (có đầy đủ constructor/getter/equals/hashCode/toString viết tay) sang Record, so sánh độ dài code.
- Thiết kế 1 hệ thống `sealed interface PaymentMethod permits CreditCard, BankTransfer, EWallet` với mỗi loại là 1 Record, viết hàm xử lý bằng switch pattern matching không cần `default`.
- Viết ví dụ dùng record deconstruction trong switch để trích xuất trực tiếp field của 1 record lồng nhau (VD: `record Order(Customer customer, List<Item> items)`).
- Thử thêm 1 subclass mới vào hệt thống sealed ở bài tập 2 mà quên cập nhật switch — quan sát lỗi biên dịch compiler báo ra.
- Tạo 1 project Java nhỏ có `module-info.java`, thử `exports` 1 package và giữ 1 package khác ẩn, kiểm chứng module khác không truy cập được package ẩn.

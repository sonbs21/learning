# Tuần 12: Kiến thức bổ trợ khác

> Tài liệu tổng hợp kiến thức mục 12 trong lộ trình `java-core-roadmap.md`.

---

## 1. Reflection API (Class, Method, Field — dùng để hiểu cách Spring hoạt động)

### Reflection là gì?
Cho phép chương trình **kiểm tra và thao tác với chính cấu trúc của class tại runtime** (tên class, method, field, constructor, annotation...) — kể cả khi không biết trước kiểu cụ thể lúc biên dịch. Đây là nền tảng kỹ thuật đứng sau hầu hết framework hiện đại (Spring, Hibernate, Jackson...).

```java
class Person {
    private String name = "An";
    private int age = 25;

    private Person() { } // constructor private
    public Person(String name, int age) { this.name = name; this.age = age; }

    private void greet() { System.out.println("Hello, " + name); }
}
```

### Lấy thông tin `Class`
```java
Class<?> clazz = Person.class;                          // cách 1: từ .class literal
Class<?> clazz2 = Class.forName("com.example.Person");  // cách 2: từ tên đầy đủ (String) - hay dùng khi load class động
Class<?> clazz3 = new Person("An", 25).getClass();       // cách 3: từ 1 object có sẵn

clazz.getName();          // "com.example.Person"
clazz.getSimpleName();     // "Person"
clazz.getSuperclass();      // Object
clazz.getInterfaces();       // các interface class này implements
```

### Thao tác với Field (kể cả private)
```java
Field nameField = clazz.getDeclaredField("name"); // getDeclaredField lấy được CẢ private (khác getField chỉ lấy public)
nameField.setAccessible(true);                      // BẮT BUỘC để bypass kiểm tra access modifier (private/protected)

Person p = new Person("An", 25);
String value = (String) nameField.get(p);            // đọc giá trị field private
nameField.set(p, "Binh");                              // GHI ĐÈ giá trị field private - phá vỡ encapsulation!
```

### Thao tác với Method (kể cả private)
```java
Method greetMethod = clazz.getDeclaredMethod("greet"); // lấy method theo tên + kiểu tham số
greetMethod.setAccessible(true);
greetMethod.invoke(p); // gọi method private "greet()" từ bên ngoài class - "Hello, Binh"
```

### Thao tác với Constructor (tạo object không qua constructor thông thường)
```java
Constructor<?> privateConstructor = clazz.getDeclaredConstructor(); // lấy constructor private không tham số
privateConstructor.setAccessible(true);
Person p2 = (Person) privateConstructor.newInstance(); // tạo object bằng constructor private!
```

### Reflection và Spring Framework — ứng dụng thực tế
Spring dùng Reflection rộng rãi để thực hiện **Dependency Injection (DI)**:
```
1. Spring quét (scan) các class có annotation @Component/@Service/@Repository
2. Dùng Reflection đọc CONSTRUCTOR hoặc FIELD có annotation @Autowired
3. Dùng Reflection TẠO object (Constructor.newInstance()) và GÁN giá trị dependency
   vào field (Field.set()) mà KHÔNG cần code của bạn tự viết "new SomeService()"
   hay tự gọi setter - toàn bộ thực hiện tự động qua Reflection lúc khởi động ứng dụng
```
> Đây là lý do vì sao Spring có thể inject dependency vào `private` field mà không cần setter public — nhờ `Field.setAccessible(true)`.

### Đánh đổi khi dùng Reflection
- **Vi phạm encapsulation** — có thể truy cập/sửa field `private` từ bên ngoài, phá vỡ tính đóng gói.
- **Chậm hơn** gọi method/field trực tiếp (phải tra cứu metadata tại runtime, mất tối ưu của JIT compiler).
- **Mất kiểm tra kiểu tại compile-time** — lỗi (VD: sai tên field/method) chỉ phát hiện được lúc runtime, không phải lúc biên dịch.
- Vì vậy Reflection nên dùng cho **framework/tooling** (nơi cần tính linh hoạt cao), **không nên** dùng tùy tiện trong business logic thông thường.

---

## 2. Annotation: Built-in và Custom Annotation

### Built-in Annotation thường gặp
```java
class Animal {
    @Override // báo cho compiler kiểm tra: method này PHẢI đang override 1 method của superclass/interface
    public String toString() { return "Animal"; }

    @Deprecated // đánh dấu method/class không nên dùng nữa, IDE sẽ cảnh báo gạch ngang khi gọi
    void oldMethod() { }

    @SuppressWarnings("unchecked") // báo compiler bỏ qua 1 loại warning cụ thể (VD: unchecked cast)
    void castExample() {
        List list = new ArrayList();
        List<String> strings = list; // warning bị suppress
    }
}

@FunctionalInterface // báo compiler: interface này CHỈ được có đúng 1 abstract method (xem Week10)
interface Calculator {
    int calculate(int a, int b);
}
```

### Custom Annotation — Tự định nghĩa
```java
import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME) // annotation này TỒN TẠI đến lúc RUNTIME (đọc được bằng Reflection)
@Target(ElementType.METHOD)          // annotation này CHỈ áp dụng được cho METHOD (không phải class/field)
public @interface LogExecutionTime {
    String description() default ""; // element có giá trị mặc định, không bắt buộc phải truyền
}

class Service {
    @LogExecutionTime(description = "Xử lý đơn hàng")
    void processOrder() {
        // logic xử lý...
    }
}
```

### `@Retention` — Vòng đời của Annotation
| Policy | Ý nghĩa |
|---|---|
| `SOURCE` | Chỉ tồn tại trong source code, bị loại bỏ ngay khi biên dịch (VD: `@Override`, `@SuppressWarnings`) |
| `CLASS` | Được ghi vào file `.class` (bytecode), nhưng KHÔNG load vào JVM lúc runtime (mặc định, ít dùng trực tiếp) |
| `RUNTIME` | Tồn tại đến tận lúc chạy chương trình, **đọc được bằng Reflection** — dùng cho annotation của framework (VD: `@Autowired`, `@Component`, `@Test`) |

### `@Target` — Annotation áp dụng được cho đâu
`TYPE` (class/interface), `METHOD`, `FIELD`, `PARAMETER`, `CONSTRUCTOR`, `ANNOTATION_TYPE` (annotation áp dụng lên annotation khác)...

### Đọc Custom Annotation bằng Reflection — ví dụ hoàn chỉnh
```java
Method method = Service.class.getDeclaredMethod("processOrder");
if (method.isAnnotationPresent(LogExecutionTime.class)) {
    LogExecutionTime annotation = method.getAnnotation(LogExecutionTime.class);
    System.out.println("Description: " + annotation.description()); // "Xử lý đơn hàng"

    // Có thể dùng thông tin này để "wrap" method gốc bằng logic đo thời gian thực thi,
    // đây chính là ý tưởng nền tảng của AOP (Aspect-Oriented Programming) trong Spring
}
```
> **Kết hợp Annotation + Reflection** chính là cơ chế lõi đứng sau hầu hết "magic" của Spring: `@Component` để đánh dấu class cần quản lý, `@Autowired` để đánh dấu nơi cần inject, `@Transactional` để tự động bọc method trong transaction — tất cả đều được framework **đọc bằng Reflection lúc runtime** rồi hành động tương ứng.

---

## 3. Java Memory Model (JMM) — Nguyên Lý Cơ Bản cho Concurrency

### JMM là gì?
Là **đặc tả (specification)** định nghĩa cách các thread tương tác với bộ nhớ (Heap, biến shared) — cụ thể là quy định **khi nào** 1 thread có thể **nhìn thấy** thay đổi mà thread khác thực hiện lên biến chia sẻ. JMM giải quyết vấn đề: các CPU hiện đại có nhiều tầng cache riêng (L1, L2 mỗi core) và có thể **tái sắp xếp lệnh (instruction reordering)** để tối ưu hiệu năng — nếu không có quy tắc rõ ràng, hành vi đa luồng sẽ không thể dự đoán được và khác nhau giữa các CPU/JVM implementation.

### 3 vấn đề cốt lõi JMM giải quyết

**1. Visibility (Khả năng nhìn thấy)** — đã phân tích chi tiết ở [Week9/01-multithreading-concurrency.md](../Week9/01-multithreading-concurrency.md#4-volatile-keyword-happens-before-relationship): thread A ghi biến, thread B có thể không thấy ngay do cache riêng của CPU.

**2. Atomicity (Tính nguyên tử)** — 1 thao tác tưởng như đơn giản (`count++`) thực chất là nhiều bước (đọc-cộng-ghi), có thể bị xen ngang bởi thread khác giữa các bước.

**3. Ordering (Thứ tự thực thi)** — Compiler và CPU được phép **tái sắp xếp lệnh** miễn là không ảnh hưởng kết quả trong **1 thread đơn lẻ** — nhưng thứ tự bị đảo này **có thể quan sát được** bởi thread khác, gây ra hành vi bất ngờ.

```java
class Example {
    int a = 0;
    boolean flag = false;

    void writer() {
        a = 1;        // (1)
        flag = true;   // (2)
        // Compiler/CPU CÓ THỂ đảo thứ tự (1) và (2) nếu không có rào cản (barrier) nào ngăn -
        // vì trong 1 thread đơn lẻ, thứ tự này không ảnh hưởng kết quả cuối
    }

    void reader() {
        if (flag) {          // Thread khác có thể thấy flag=true TRƯỚC KHI thấy a=1 (do reordering)
            System.out.println(a); // có thể in ra 0 thay vì 1 - nếu không có volatile/synchronized!
        }
    }
}
```

### Happens-Before — Cơ chế JMM dùng để đảm bảo trật tự
Đã trình bày chi tiết ở [Week9/01-multithreading-concurrency.md](../Week9/01-multithreading-concurrency.md#4-volatile-keyword-happens-before-relationship) — các quy tắc (`program order`, `monitor lock`, `volatile variable`, `thread start/join`) chính là **công cụ chính thức** mà JMM cung cấp để lập trình viên **kiểm soát được** visibility và ordering, thay vì phụ thuộc vào hành vi không xác định của từng CPU/JVM cụ thể.

> **Tóm lại**: JMM là nền tảng lý thuyết giải thích *"vì sao"* `synchronized`, `volatile`, và các lớp trong `java.util.concurrent` hoạt động đúng đắn trong môi trường đa luồng — nắm được JMM giúp hiểu sâu hơn thay vì chỉ ghi nhớ "quy tắc thực hành" một cách máy móc.

---

## 4. `equals`/`hashCode` Contract khi dùng Object làm Key trong HashMap

Đã trình bày kỹ ở [Week2/01-huong-doi-tuong-oop.md](../Week2/01-huong-doi-tuong-oop.md#9-object-class-equals-hashcode-tostring-clone) — nhắc lại điểm mấu chốt liên quan trực tiếp tới `HashMap`/`HashSet`:

### Quy tắc vàng
> Nếu `a.equals(b) == true` thì BẮT BUỘC `a.hashCode() == b.hashCode()`.

### Hệ quả nếu vi phạm khi dùng làm key HashMap
```java
class BadKey {
    int id;
    BadKey(int id) { this.id = id; }

    @Override
    public boolean equals(Object o) { // override equals()...
        if (!(o instanceof BadKey)) return false;
        return id == ((BadKey) o).id;
    }
    // ... nhưng QUÊN override hashCode() -> vẫn dùng identity hashCode mặc định của Object
}

Map<BadKey, String> map = new HashMap<>();
map.put(new BadKey(1), "value");

BadKey lookupKey = new BadKey(1); // equals() với key đã put ở trên -> true
System.out.println(map.get(lookupKey)); // in ra NULL! Vì hashCode() khác nhau -> rơi vào bucket khác
                                          // -> HashMap không bao giờ tìm đến đúng bucket chứa entry đó
```

### Vì sao lỗi này xảy ra? (Liên hệ cơ chế HashMap ở Week3)
Theo cơ chế `HashMap` đã phân tích ở [Week3/04-map-chi-tiet.md](../Week3/04-map-chi-tiet.md#21-hashmap--cấu-trúc-bucket--linked-listtree): `get(key)` trước tiên tính `hash(key)` để xác định **bucket**, sau đó mới dùng `equals()` để so sánh trong bucket đó. Nếu 2 object equals nhau nhưng có `hashCode()` khác nhau, chúng rơi vào **2 bucket khác nhau** — `equals()` không bao giờ có cơ hội được gọi để so sánh, nên `get()` luôn trả về `null` dù entry thực sự tồn tại (chỉ ở bucket khác).

### Best Practice
- Luôn override **cả 2** method cùng lúc (IDE hiện đại đều có tool tự sinh cặp `equals`/`hashCode` nhất quán).
- Dùng `Objects.equals()` và `Objects.hash()` (từ `java.util.Objects`) để viết đúng và ngắn gọn:
```java
@Override
public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof BadKey)) return false;
    BadKey other = (BadKey) o;
    return id == other.id;
}

@Override
public int hashCode() {
    return Objects.hash(id); // đảm bảo hashCode dựa trên CÙNG field dùng trong equals()
}
```
- **Không nên** dùng field **mutable** (có thể thay đổi sau khi object đã được thêm vào HashMap/HashSet) làm cơ sở tính `hashCode()` — nếu field đó đổi sau khi đã `put()`, object sẽ "thất lạc" (không tìm lại được ở đúng bucket cũ, dù vẫn còn trong map).

---

## 5. Design Pattern liên quan tới JDK

### Singleton qua Enum — cách an toàn nhất để viết Singleton
```java
enum Singleton {
    INSTANCE; // chỉ có DUY NHẤT 1 instance, JVM đảm bảo

    void doSomething() {
        System.out.println("Singleton method");
    }
}

Singleton.INSTANCE.doSomething();
```
**Vì sao Enum Singleton được coi là cách tốt nhất** (so với Singleton viết tay bằng `private constructor` + `static getInstance()`)?
- **Thread-safe tự động** — JVM đảm bảo việc khởi tạo enum constant chỉ xảy ra **đúng 1 lần**, không cần tự viết `synchronized` hay double-checked locking phức tạp.
- **Chống lại Reflection attack** — Singleton viết tay bằng `private constructor` vẫn có thể bị phá vỡ bằng Reflection (`Constructor.setAccessible(true)` rồi `newInstance()` — xem mục 1), nhưng JVM **cấm tuyệt đối** việc tạo thêm instance enum bằng Reflection (ném `IllegalArgumentException`).
- **Chống lại Serialization attack** — Singleton thường có thể bị "nhân bản" qua việc deserialize nhiều lần tạo ra nhiều instance khác nhau, nhưng Enum tự động đảm bảo deserialize luôn trả về đúng 1 instance có sẵn.

### Builder Pattern — ví dụ trong JDK: `StringBuilder`, `StringBuffer`
```java
// StringBuilder chính là 1 dạng Builder Pattern - "build" dần chuỗi kết quả qua nhiều bước
String result = new StringBuilder()
    .append("Hello")
    .append(" ")
    .append("World")
    .toString();
```

**Builder Pattern tự viết** — hữu ích khi object có nhiều tham số tùy chọn (tránh constructor với quá nhiều tham số — "telescoping constructor" anti-pattern):
```java
class Pizza {
    private final String size;      // bắt buộc
    private final boolean cheese;    // tùy chọn
    private final boolean pepperoni; // tùy chọn

    private Pizza(Builder builder) {
        this.size = builder.size;
        this.cheese = builder.cheese;
        this.pepperoni = builder.pepperoni;
    }

    static class Builder {
        private final String size; // bắt buộc, truyền qua constructor của Builder
        private boolean cheese = false;
        private boolean pepperoni = false;

        Builder(String size) { this.size = size; }

        Builder cheese(boolean value) { this.cheese = value; return this; } // trả về "this" để chain
        Builder pepperoni(boolean value) { this.pepperoni = value; return this; }

        Pizza build() { return new Pizza(this); }
    }
}

Pizza pizza = new Pizza.Builder("Large")
    .cheese(true)
    .pepperoni(true)
    .build();
```

### Factory Pattern — ví dụ trong JDK: `Collections`, `Executors`, `List.of()`
```java
// Executors là 1 Factory class - ẩn chi tiết tạo ThreadPoolExecutor phức tạp, expose API đơn giản
ExecutorService executor = Executors.newFixedThreadPool(4); // Factory Method

// Collections.unmodifiableList(), List.of(), Collectors.toList() cũng đều là Factory Method
List<Integer> immutableList = List.of(1, 2, 3); // "factory method" tạo ra 1 implementation cụ thể ẩn danh
```
**Ý tưởng cốt lõi của Factory Pattern**: ẩn đi **chi tiết cách tạo object** (loại object cụ thể nào được trả về) đằng sau 1 method tĩnh hoặc interface, giúp code gọi không phụ thuộc trực tiếp vào class cụ thể (chỉ cần biết interface/kiểu trả về).

---

## Câu hỏi tự kiểm tra
1. Reflection cho phép làm gì mà code thông thường không làm được? Nêu 1 rủi ro cụ thể khi dùng Reflection để sửa field `private`.
2. Giải thích cách Spring dùng Reflection + Annotation để thực hiện Dependency Injection mà không cần code của bạn tự gọi `new`.
3. Phân biệt 3 `RetentionPolicy` (`SOURCE`, `CLASS`, `RUNTIME`). Annotation dùng cho framework (như `@Autowired`) cần policy nào? Vì sao?
4. JMM giải quyết những vấn đề gì trong lập trình đa luồng? Cho ví dụ minh họa "instruction reordering" gây ra kết quả bất ngờ.
5. Giải thích chi tiết vì sao 1 object override `equals()` nhưng quên `hashCode()` sẽ khiến `HashMap.get()` trả về `null` dù `equals()` đúng.
6. Vì sao Singleton qua Enum được coi là an toàn hơn Singleton viết tay (`private constructor` + `getInstance()`) trước tấn công bằng Reflection và Serialization?
7. Nêu 2 ví dụ Factory Pattern có sẵn trong JDK (không phải tự viết) và giải thích chúng "ẩn" chi tiết tạo object như thế nào.

## Bài tập luyện tập
- Viết chương trình dùng Reflection để đọc và ghi giá trị vào 1 field `private` của class tự định nghĩa, sau đó gọi 1 method `private` từ bên ngoài.
- Viết 1 custom annotation `@LogExecutionTime` (như ví dụ mục 2), dùng Reflection để đọc annotation này trên method và tự động in ra thời gian thực thi (dùng `System.nanoTime()` trước/sau khi `Method.invoke()`).
- Viết ví dụ minh họa lỗi HashMap khi object key override `equals()` mà quên `hashCode()`, kiểm chứng `get()` trả về `null` dù `equals()` đúng, sau đó sửa lại đúng cả 2.
- Thử phá vỡ 1 Singleton viết tay bằng Reflection (`setAccessible(true)` trên constructor private), sau đó thử làm tương tự với Enum Singleton để kiểm chứng nó ném exception.
- Viết 1 class `Pizza` dùng Builder Pattern với ít nhất 2 tham số bắt buộc và 3 tham số tùy chọn.

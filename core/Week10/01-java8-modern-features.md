# Tuần 10: Java 8+ Modern Features

> Tài liệu tổng hợp kiến thức mục 10 trong lộ trình `java-core-roadmap.md`.

---

## 1. Lambda Expression, Functional Interface (`@FunctionalInterface`)

### Functional Interface là gì?
Là 1 interface chỉ có **đúng 1 abstract method** (có thể có nhiều `default`/`static` method). Đây là điều kiện để có thể dùng **lambda expression** thay thế cho việc viết anonymous class dài dòng.

```java
@FunctionalInterface // annotation KHÔNG bắt buộc nhưng nên dùng - compiler sẽ báo lỗi nếu có >1 abstract method
interface Calculator {
    int calculate(int a, int b);
}
```

### Lambda Expression — thay thế Anonymous Class
```java
// Trước Java 8 - Anonymous class dài dòng
Calculator oldWay = new Calculator() {
    @Override
    public int calculate(int a, int b) {
        return a + b;
    }
};

// Từ Java 8 - Lambda expression ngắn gọn
Calculator sum = (a, b) -> a + b;                    // 1 dòng, tự suy luận kiểu trả về
Calculator multiply = (int a, int b) -> { return a * b; }; // dạng đầy đủ với block

System.out.println(sum.calculate(3, 4)); // 7
```

### Cú pháp Lambda
```java
() -> 42                          // không tham số
x -> x * 2                        // 1 tham số, không cần ngoặc đơn (không bắt buộc)
(x, y) -> x + y                   // nhiều tham số
(int x, int y) -> x + y           // khai báo kiểu tường minh (thường không cần, compiler tự suy luận)
(x, y) -> { return x + y; }       // dùng block khi cần nhiều câu lệnh
```

### Biến capture trong Lambda — phải là "effectively final"
```java
int base = 10; // effectively final (không bị gán lại sau khi khởi tạo)
Calculator addBase = (a, b) -> a + b + base; // OK, dùng được biến "effectively final" từ scope ngoài

// base = 20; // nếu thêm dòng này -> LỖI biên dịch, vì base không còn "effectively final"
```
> Lambda **capture theo giá trị** (không phải theo reference) đối với biến local — đây là lý do biến phải bất biến (effectively final), tránh nhập nhằng khi lambda được thực thi ở thời điểm khác với lúc khai báo (VD: chạy trong thread khác).

---

## 2. Built-in Functional Interfaces

Nằm trong package `java.util.function`, JDK cung cấp sẵn các functional interface phổ biến để không phải tự định nghĩa lặp lại:

| Interface | Method | Ý nghĩa | Ví dụ |
|---|---|---|---|
| `Function<T, R>` | `R apply(T t)` | Nhận vào T, trả về R (biến đổi kiểu) | `Function<String, Integer> len = String::length;` |
| `BiFunction<T, U, R>` | `R apply(T t, U u)` | Nhận vào 2 tham số khác kiểu, trả về R | `BiFunction<Integer, Integer, Integer> add = (a, b) -> a + b;` |
| `Predicate<T>` | `boolean test(T t)` | Kiểm tra điều kiện, trả về boolean | `Predicate<Integer> isEven = n -> n % 2 == 0;` |
| `Supplier<T>` | `T get()` | Không nhận tham số, chỉ CUNG CẤP giá trị | `Supplier<String> greeting = () -> "Hello";` |
| `Consumer<T>` | `void accept(T t)` | Nhận vào T, KHÔNG trả về gì (side-effect) | `Consumer<String> printer = System.out::println;` |

```java
Function<Integer, Integer> square = x -> x * x;
System.out.println(square.apply(5)); // 25

Predicate<String> isEmpty = String::isEmpty;
System.out.println(isEmpty.test("")); // true

Supplier<Double> randomValue = Math::random;
System.out.println(randomValue.get());

Consumer<String> logger = msg -> System.out.println("[LOG] " + msg);
logger.accept("Application started");

// Kết hợp (compose/chain) functional interface
Predicate<Integer> isPositive = n -> n > 0;
Predicate<Integer> isEven = n -> n % 2 == 0;
Predicate<Integer> isPositiveAndEven = isPositive.and(isEven); // and(), or(), negate()

Function<Integer, Integer> addOne = x -> x + 1;
Function<Integer, Integer> combined = square.andThen(addOne); // square TRƯỚC, rồi addOne -> f(x) = x² + 1
Function<Integer, Integer> combined2 = square.compose(addOne); // addOne TRƯỚC, rồi square -> f(x) = (x+1)²
```

---

## 3. Method Reference: Static, Instance, Constructor Reference

Là cú pháp rút gọn hơn nữa của lambda, khi lambda chỉ đơn giản **gọi lại 1 method có sẵn**.

| Loại | Cú pháp | Ví dụ lambda tương đương |
|---|---|---|
| **Static method reference** | `ClassName::staticMethod` | `Integer::parseInt` ≡ `s -> Integer.parseInt(s)` |
| **Instance method của object cụ thể** | `object::instanceMethod` | `System.out::println` ≡ `x -> System.out.println(x)` |
| **Instance method của "1 tham số bất kỳ"** | `ClassName::instanceMethod` | `String::toUpperCase` ≡ `s -> s.toUpperCase()` |
| **Constructor reference** | `ClassName::new` | `ArrayList::new` ≡ `() -> new ArrayList<>()` |

```java
List<String> names = List.of("banana", "apple", "cherry");

names.forEach(System.out::println);              // instance method của object cụ thể (System.out)

List<String> upper = names.stream()
    .map(String::toUpperCase)                     // instance method trên tham số truyền vào
    .collect(Collectors.toList());

List<Integer> nums = names.stream()
    .map(String::length)                          // String::length ≡ s -> s.length()
    .collect(Collectors.toList());

Function<String, Integer> parser = Integer::parseInt; // static method reference

Supplier<List<String>> listFactory = ArrayList::new;   // constructor reference
List<String> newList = listFactory.get();
```

---

## 4. Stream API: map, filter, reduce, collect, flatMap, sorted, distinct

Stream là 1 chuỗi thao tác xử lý dữ liệu theo phong cách **khai báo (declarative)** thay vì mệnh lệnh (imperative) — mô tả "làm gì" thay vì "làm như thế nào từng bước".

```java
List<String> names = List.of("Anna", "Bob", "Charlie", "Anna", "David");

// Ví dụ tổng hợp: lọc, biến đổi, sắp xếp, loại trùng, giới hạn, thu thập kết quả
List<String> result = names.stream()
    .filter(name -> name.length() > 3)   // giữ lại phần tử thỏa điều kiện
    .map(String::toUpperCase)             // biến đổi từng phần tử
    .distinct()                            // loại bỏ trùng lặp (dựa trên equals())
    .sorted()                              // sắp xếp theo thứ tự tự nhiên
    .limit(3)                               // giới hạn số lượng kết quả
    .collect(Collectors.toList());          // thu thập thành List

System.out.println(result); // [ANNA, CHARLIE, DAVID]
```

### `map()` vs `flatMap()`
```java
// map() - biến đổi 1-1: mỗi phần tử đầu vào -> 1 phần tử đầu ra
List<Integer> lengths = names.stream().map(String::length).collect(Collectors.toList());

// flatMap() - "làm phẳng" (flatten): dùng khi mỗi phần tử tạo ra 1 Stream con, cần gộp lại thành 1 Stream duy nhất
List<List<Integer>> nestedLists = List.of(List.of(1, 2), List.of(3, 4), List.of(5));
List<Integer> flatList = nestedLists.stream()
    .flatMap(List::stream) // mỗi List<Integer> con -> Stream<Integer>, flatMap gộp tất cả thành 1 Stream duy nhất
    .collect(Collectors.toList());
System.out.println(flatList); // [1, 2, 3, 4, 5]
```

### `reduce()` — tổng hợp toàn bộ Stream thành 1 giá trị duy nhất
```java
List<Integer> numbers = List.of(1, 2, 3, 4, 5);

int sum = numbers.stream().reduce(0, (a, b) -> a + b); // identity=0, accumulator -> 15
Optional<Integer> max = numbers.stream().reduce(Integer::max); // không có identity -> trả về Optional

int product = numbers.stream().reduce(1, (a, b) -> a * b); // 120
```

---

## 5. Stream Đặc Điểm: Lazy Evaluation, Intermediate vs Terminal Operation

### Phân loại Operation
- **Intermediate Operation** (trung gian): `filter`, `map`, `sorted`, `distinct`, `limit`, `flatMap`... — trả về 1 Stream mới, **KHÔNG thực thi ngay**, chỉ "ghi nhận" thao tác cần làm.
- **Terminal Operation** (kết thúc): `collect`, `forEach`, `reduce`, `count`, `anyMatch`, `findFirst`... — **kích hoạt** toàn bộ pipeline thực sự chạy, trả về kết quả (không phải Stream nữa).

```java
Stream<String> stream = names.stream()
    .filter(name -> {
        System.out.println("Filtering: " + name); // sẽ KHÔNG in ra gì cho đến khi có terminal operation
        return name.length() > 3;
    });
// Tại đây CHƯA có gì thực sự chạy! Đây chính là "Lazy Evaluation"

List<String> result = stream.collect(Collectors.toList()); // CHỈ KHI GỌI collect() (terminal), pipeline mới chạy thật
```

### Lazy Evaluation — Vì sao quan trọng?
- Cho phép JVM **tối ưu hóa toàn bộ pipeline** thay vì chạy từng bước riêng lẻ — VD: nếu có `filter().limit(3)`, Stream có thể dừng sớm ngay khi đủ 3 phần tử thỏa điều kiện, không cần duyệt hết toàn bộ dữ liệu.
- Xử lý được **Stream vô hạn** (infinite stream) — VD: `Stream.iterate(1, n -> n + 1).limit(5)` chỉ tính đến khi đủ 5 phần tử nhờ `limit()`, không "treo" cố tính vô hạn.

```java
Stream.iterate(1, n -> n * 2)
    .limit(5)
    .forEach(System.out::println); // 1, 2, 4, 8, 16 - dừng đúng lúc nhờ lazy evaluation
```

> **Lưu ý**: 1 Stream chỉ dùng được **1 lần duy nhất** — sau khi đã gọi terminal operation, Stream đó "đóng" lại, gọi lại sẽ ném `IllegalStateException`.

---

## 6. Collectors: toList, groupingBy, partitioningBy, joining, summarizing

```java
List<String> words = List.of("apple", "banana", "avocado", "blueberry", "cherry");

// toList / toSet / toMap
List<String> list = words.stream().collect(Collectors.toList());
Set<String> set = words.stream().collect(Collectors.toSet());
Map<String, Integer> map = words.stream().collect(Collectors.toMap(w -> w, String::length));

// groupingBy - nhóm theo 1 tiêu chí, trả về Map<K, List<T>>
Map<Character, List<String>> byFirstLetter = words.stream()
    .collect(Collectors.groupingBy(w -> w.charAt(0)));
// {a=[apple, avocado], b=[banana, blueberry], c=[cherry]}

// groupingBy + downstream collector (đếm số lượng thay vì List đầy đủ)
Map<Character, Long> countByFirstLetter = words.stream()
    .collect(Collectors.groupingBy(w -> w.charAt(0), Collectors.counting()));
// {a=2, b=2, c=1}

// partitioningBy - chia làm 2 nhóm true/false (đặc biệt của groupingBy với điều kiện boolean)
Map<Boolean, List<String>> partitioned = words.stream()
    .collect(Collectors.partitioningBy(w -> w.length() > 5));
// {false=[apple, cherry], true=[banana, avocado, blueberry]}

// joining - nối các String lại, có thể có prefix/suffix/delimiter
String joined = words.stream().collect(Collectors.joining(", ", "[", "]"));
// "[apple, banana, avocado, blueberry, cherry]"

// summarizing - tính toán thống kê (min, max, sum, average, count) trong 1 lần duyệt
IntSummaryStatistics stats = words.stream().collect(Collectors.summarizingInt(String::length));
stats.getMax(); stats.getMin(); stats.getAverage(); stats.getSum(); stats.getCount();
```

---

## 7. Optional: of, ofNullable, orElse, orElseGet, map, ifPresent — Tránh Lạm Dụng

### Vấn đề Optional giải quyết
`Optional<T>` là 1 "wrapper" biểu diễn tường minh khả năng **"có thể không có giá trị"** — buộc caller phải xử lý trường hợp rỗng một cách **tường minh trong code**, thay vì để `null` âm thầm gây `NullPointerException` bất ngờ ở đâu đó xa nguồn gốc lỗi.

```java
Optional<String> opt1 = Optional.of("Hello");        // giá trị KHÔNG được null (ném NPE ngay nếu null)
Optional<String> opt2 = Optional.ofNullable(null);    // cho phép null - trả về Optional.empty()
Optional<String> opt3 = Optional.empty();              // rỗng tường minh

// Lấy giá trị an toàn
String value1 = opt2.orElse("default");                // trả về "default" nếu rỗng - LUÔN tính "default" dù có giá trị hay không
String value2 = opt2.orElseGet(() -> computeDefault()); // chỉ gọi computeDefault() KHI THỰC SỰ rỗng - lazy, hiệu quả hơn orElse()
String value3 = opt2.orElseThrow(() -> new NoSuchElementException()); // ném exception tùy chỉnh nếu rỗng

// Kiểm tra và xử lý
opt1.ifPresent(v -> System.out.println("Has value: " + v)); // chỉ chạy nếu CÓ giá trị
opt1.ifPresentOrElse(
    v -> System.out.println("Value: " + v),
    () -> System.out.println("Empty!")
); // Java 9+

// map() - biến đổi giá trị bên trong Optional (nếu có), giữ nguyên "rỗng" nếu Optional rỗng
Optional<Integer> length = opt1.map(String::length); // Optional[5]

// Chaining nhiều thao tác an toàn, không cần if-else lồng nhau kiểm tra null
Optional<String> result = Optional.ofNullable(getUser())
    .map(User::getAddress)
    .map(Address::getCity);
String city = result.orElse("Unknown");
```

### `orElse()` vs `orElseGet()` — bẫy hiệu năng thường gặp
```java
// orElse() - LUÔN gọi expensiveDefault(), NGAY CẢ KHI opt đã có giá trị (lãng phí tính toán)
String v1 = opt.orElse(expensiveDefault());

// orElseGet() - CHỈ gọi supplier khi opt THỰC SỰ rỗng - lazy, hiệu quả hơn
String v2 = opt.orElseGet(() -> expensiveDefault());
```

### Best Practice — Tránh lạm dụng Optional
- **KHÔNG** dùng `Optional` làm kiểu cho field của class hay tham số method (`Optional` không implement `Serializable`, gây overhead không cần thiết) — chỉ nên dùng làm **kiểu trả về của method**, báo hiệu rõ ràng "kết quả này có thể không tồn tại".
- **KHÔNG** gọi `optional.get()` trực tiếp mà không kiểm tra `isPresent()` trước — mất hết ý nghĩa của Optional (vẫn có thể ném `NoSuchElementException` y hệt NPE).
- **KHÔNG** dùng `Optional` cho collection (`List`, `Map`...) — nên trả về collection **rỗng** (`Collections.emptyList()`) thay vì `Optional<List<T>>`.

```java
// XẤU - lạm dụng, phá vỡ mục đích thiết kế ban đầu của Optional
class User {
    Optional<String> name; // KHÔNG nên dùng Optional làm field
}

// TỐT - chỉ dùng Optional làm kiểu trả về của method để báo hiệu "có thể không có kết quả"
Optional<User> findUserById(int id) { ... }
```

---

## 8. Date/Time API mới: LocalDate, LocalDateTime, ZonedDateTime, Duration, Period

Trước Java 8, `java.util.Date`/`Calendar` có nhiều vấn đề: **mutable** (không thread-safe), API khó dùng (tháng bắt đầu từ 0), lẫn lộn giữa "thời điểm" và "thời gian có múi giờ". Java 8 giới thiệu `java.time` — **immutable, thread-safe, API rõ ràng**.

```java
LocalDate date = LocalDate.of(2024, 3, 15);          // chỉ NGÀY, không giờ, không múi giờ
LocalDate today = LocalDate.now();

LocalTime time = LocalTime.of(14, 30);                 // chỉ GIỜ, không ngày
LocalDateTime dateTime = LocalDateTime.of(date, time);  // cả NGÀY và GIỜ, không múi giờ
LocalDateTime now = LocalDateTime.now();

ZonedDateTime zoned = ZonedDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")); // có MÚI GIỜ tường minh

// Thao tác - LUÔN trả về object MỚI (immutable, giống String)
LocalDate tomorrow = today.plusDays(1);
LocalDate lastMonth = today.minusMonths(1);
boolean isBefore = date.isBefore(today);

// Duration - khoảng thời gian tính bằng giây/nano (dùng cho LocalTime/LocalDateTime/Instant)
Duration duration = Duration.between(LocalTime.of(9, 0), LocalTime.of(17, 30));
duration.toHours(); // 8
duration.toMinutes(); // 510

// Period - khoảng thời gian tính bằng năm/tháng/ngày (dùng cho LocalDate)
Period period = Period.between(LocalDate.of(2020, 1, 1), LocalDate.of(2024, 3, 15));
period.getYears();  // 4
period.getMonths(); // 2
period.getDays();   // 14

// Format
DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
String formatted = today.format(formatter);
LocalDate parsed = LocalDate.parse("15/03/2024", formatter);
```

| Class | Dùng cho |
|---|---|
| `LocalDate` | Chỉ ngày (VD: ngày sinh) |
| `LocalTime` | Chỉ giờ (VD: giờ mở cửa) |
| `LocalDateTime` | Ngày + giờ, KHÔNG múi giờ |
| `ZonedDateTime` | Ngày + giờ + múi giờ cụ thể (VD: lịch họp quốc tế) |
| `Instant` | 1 điểm thời gian tuyệt đối trên trục thời gian (epoch), dùng cho timestamp hệ thống |
| `Duration` | Khoảng cách thời gian theo giây/nano |
| `Period` | Khoảng cách thời gian theo năm/tháng/ngày |

---

## 9. Interface Default/Static Method

Đã giới thiệu ở [Week2/01-huong-doi-tuong-oop.md](../Week2/01-huong-doi-tuong-oop.md#6-abstract-class-vs-interface) — nhắc lại điểm mấu chốt liên quan tới Java 8:

### Vì sao Java 8 thêm `default method` vào interface?
Trước Java 8, thêm 1 method mới vào interface sẽ **phá vỡ (break)** mọi class đã implement interface đó (bắt buộc phải cài đặt method mới). `default method` cho phép **thêm method mới vào interface có sẵn mà không phá vỡ code cũ** — đây chính là cách JDK 8 thêm hàng loạt method mới (`forEach`, `stream`,...) vào các interface `Collection` đã tồn tại từ lâu mà không làm hỏng toàn bộ code Java cũ đang implement chúng.

```java
interface Vehicle {
    void move(); // abstract - bắt buộc implement

    default void honk() { // default - có sẵn implementation, KHÔNG bắt buộc override
        System.out.println("Beep beep!");
    }

    static Vehicle createDefault() { // static - thuộc về interface, gọi qua Vehicle.createDefault()
        return () -> System.out.println("Default vehicle moving");
    }
}
```

### Xung đột khi 1 class implements nhiều interface có cùng default method
```java
interface A { default void hello() { System.out.println("Hello from A"); } }
interface B { default void hello() { System.out.println("Hello from B"); } }

class C implements A, B {
    @Override
    public void hello() { // BẮT BUỘC override để giải quyết xung đột (compiler không tự chọn)
        A.super.hello(); // có thể gọi tường minh 1 trong 2 bằng cú pháp Interface.super.method()
    }
}
```

---

## Câu hỏi tự kiểm tra
1. Functional Interface là gì? Vì sao lambda expression chỉ dùng được với interface có đúng 1 abstract method?
2. Giải thích khái niệm "effectively final" khi lambda capture biến local — vì sao lambda không cho phép capture biến có thể bị gán lại?
3. Phân biệt `map()` và `flatMap()` — cho ví dụ 1 tình huống bắt buộc phải dùng `flatMap()` thay vì `map()`.
4. Giải thích "Lazy Evaluation" của Stream — vì sao intermediate operation không chạy ngay mà phải đợi terminal operation?
5. `groupingBy` và `partitioningBy` khác nhau ở điểm nào? Khi nào nên dùng `partitioningBy` thay vì `groupingBy`?
6. Vì sao `orElseGet()` thường được khuyến nghị hơn `orElse()` khi giá trị mặc định tốn kém để tính toán?
7. Nêu 2 lý do vì sao KHÔNG nên dùng `Optional` làm field của class.
8. Vì sao Java 8 thêm `default method` vào interface? Điều gì xảy ra nếu 1 class implements 2 interface có default method trùng tên?

## Bài tập luyện tập
- Viết chương trình dùng Stream API để: lọc danh sách nhân viên có lương > 10 triệu, nhóm theo phòng ban (`groupingBy`), tính lương trung bình mỗi phòng ban.
- Viết hàm `findUserById(int id)` trả về `Optional<User>`, sử dụng `map()`/`orElseThrow()` để lấy ra email của user (ném exception rõ ràng nếu không tìm thấy).
- Dùng `flatMap()` để gộp danh sách các đơn hàng từ nhiều khách hàng (`List<Customer>` với mỗi `Customer` có `List<Order>`) thành 1 `List<Order>` duy nhất.
- Viết chương trình tính số ngày còn lại đến sinh nhật tiếp theo bằng `LocalDate`/`Period`.
- Viết 2 interface có cùng tên default method, cho 1 class implements cả 2 và giải quyết xung đột bằng `Interface.super.method()`.

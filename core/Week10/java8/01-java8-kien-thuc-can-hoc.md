# Java 8 — Toàn bộ kiến thức cần học

> Checklist này mở rộng [Java 8+ Modern Features](../01-java8-modern-features.md).  
> Mục tiêu: nắm cú pháp, hợp đồng API, cơ chế thực thi, performance/concurrency trade-off và phân biệt Java 8 thuần với API được bổ sung ở các phiên bản sau.

---

## 1. Mục tiêu đầu ra

Sau khi học xong, cần có khả năng:

- [ ] Viết và đọc lambda, method reference và functional interface.
- [ ] Hiểu target typing, effectively final, lexical scope và `invokedynamic`.
- [ ] Sử dụng đúng các interface trong `java.util.function`.
- [ ] Thiết kế Stream pipeline đúng, lazy, không side effect nguy hiểm.
- [ ] Hiểu reduction, collector, primitive stream và parallel stream.
- [ ] Dùng `Optional` đúng vai trò và không lạm dụng.
- [ ] Mô hình hóa thời gian đúng bằng `java.time`.
- [ ] Dùng default/static interface methods và giải quyết xung đột.
- [ ] Xây async pipeline với `CompletableFuture`.
- [ ] Nắm các API Java 8 mới trong Collection, Map, Comparator, Base64 và Files.
- [ ] Biết các thay đổi annotation, concurrency và JVM quan trọng.
- [ ] Nhận ra API Java 9+ thường bị nhầm là Java 8.

---

## 2. Kỷ luật phiên bản: Java 8 khác “Java 8+”

Nếu project compile với `--release 8`, các API sau **chưa tồn tại**:

| API thường bị nhầm | Có từ |
|---|---:|
| `List.of`, `Set.of`, `Map.of` | Java 9 |
| `Optional.ifPresentOrElse`, `Optional.or`, `Optional.stream` | Java 9 |
| `Stream.takeWhile`, `dropWhile`, `ofNullable` | Java 9 |
| `List.copyOf`, `Set.copyOf`, `Map.copyOf` | Java 10 |
| `Collectors.toUnmodifiableList/Set/Map` | Java 10 |
| `Stream.toList()` | Java 16 |
| `CompletableFuture.orTimeout`, `completeOnTimeout` | Java 9 |

Ví dụ Java 8 thuần nên dùng:

```java
List<String> names = Arrays.asList("An", "Binh");
List<String> result = stream.collect(Collectors.toList());
```

Phân biệt “ngôn ngữ Java 8” với “chạy trên JDK mới nhưng dùng phong cách Java 8”.

---

## 3. Lambda expression

### 3.1. Cú pháp

```java
() -> 42
x -> x * 2
(x, y) -> x + y
(int x, int y) -> x + y
(x, y) -> {
    int result = x + y;
    return result;
}
```

- Expression body tự trả về kết quả.
- Block body cần `return` nếu SAM trả về giá trị.
- Không được trộn parameter có kiểu tường minh và suy luận kiểu trong cùng lambda.
- Lambda không có kiểu độc lập; nó nhận kiểu từ target context.

### 3.2. Target typing

Cùng một lambda có thể thích nghi với các functional interface tương thích:

```java
Predicate<String> nonEmpty = value -> !value.isEmpty();
Function<String, Boolean> boxed = value -> !value.isEmpty();
```

Target type đến từ:

- biến được gán;
- tham số method;
- return type;
- cast tường minh;
- overload context.

Overload có nhiều functional interface tương tự có thể gây ambiguous call; đôi khi cần cast hoặc đổi tên overload.

### 3.3. Effectively final và capture

Lambda chỉ capture local variable là `final` hoặc effectively final:

```java
int base = 10;
IntUnaryOperator addBase = value -> value + base;
// base = 20; // làm capture không còn hợp lệ
```

- Giá trị local được capture; với object, giá trị được copy là reference.
- Object được tham chiếu vẫn có thể mutable; effectively final không tạo deep immutability.
- Instance/static field không chịu quy tắc effectively final như local variable.

### 3.4. Lexical scope và `this`

- Lambda không tạo scope `this` mới; `this` vẫn là object bao quanh.
- Không thể shadow local variable cùng tên trong enclosing scope.
- Anonymous class có `this` riêng và quy tắc scope khác.

### 3.5. Checked exception

Lambda phải tuân theo `throws` của SAM. Functional interface chuẩn như `Function` không khai báo checked exception, nên phải:

- xử lý bên trong lambda;
- wrap thành unchecked exception có ngữ cảnh;
- hoặc tạo functional interface riêng khai báo `throws`.

Không nuốt exception hoặc wrap mất nguyên nhân.

### 3.6. Cơ chế thực thi

Lambda không được đặc tả là “anonymous class viết ngắn”. Compiler thường phát sinh `invokedynamic` và bootstrap qua `LambdaMetafactory`, cho JVM quyền chọn chiến lược runtime.

Hệ quả:

- Không phụ thuộc tên/class shape nội bộ của lambda.
- Không giả định identity của lambda instance.
- Chỉ serialize lambda khi target interface `Serializable` và đã chấp nhận rủi ro tương thích; thông thường nên tránh.

---

## 4. Functional interface (SAM)

Functional interface có đúng một **abstract method contract**.

```java
@FunctionalInterface
interface Calculator {
    int calculate(int left, int right);

    default Calculator andThen(IntUnaryOperator after) {
        return (left, right) -> after.applyAsInt(calculate(left, right));
    }
    static Calculator add() { return (a, b) -> a + b; }
}
```

### 4.1. Quy tắc đếm abstract method

- `default` và `static` method không được tính.
- Public method tương ứng với method của `Object` không làm mất tính functional.
- Abstract method kế thừa có cùng signature có thể hợp thành một contract.
- `@FunctionalInterface` không bắt buộc nhưng giúp compiler bảo vệ thiết kế.

### 4.2. Thiết kế SAM tốt

- Một trách nhiệm rõ ràng.
- Tên method diễn tả hành vi.
- Tránh tạo interface mới nếu `java.util.function` đã có loại phù hợp.
- Cân nhắc primitive specialization để tránh boxing ở hot path.

---

## 5. `java.util.function`

### 5.1. Nhóm chính

| Interface | SAM | Ý nghĩa |
|---|---|---|
| `Function<T,R>` | `R apply(T)` | Biến đổi T → R |
| `BiFunction<T,U,R>` | `R apply(T,U)` | Hai input → R |
| `Predicate<T>` | `boolean test(T)` | Điều kiện |
| `BiPredicate<T,U>` | `boolean test(T,U)` | Điều kiện hai input |
| `Consumer<T>` | `void accept(T)` | Side effect |
| `BiConsumer<T,U>` | `void accept(T,U)` | Side effect hai input |
| `Supplier<T>` | `T get()` | Tạo/cung cấp giá trị |
| `UnaryOperator<T>` | `T apply(T)` | T → T |
| `BinaryOperator<T>` | `T apply(T,T)` | Hai T → T |

### 5.2. Primitive specialization

- `IntFunction<R>`, `ToIntFunction<T>`
- `IntPredicate`, `IntConsumer`, `IntSupplier`
- `IntUnaryOperator`, `IntBinaryOperator`
- Tương tự cho `long` và `double`

Dùng khi tránh boxing/unboxing có ý nghĩa, đặc biệt trong primitive streams hoặc vòng lặp lớn.

### 5.3. Composition

```java
Predicate<Integer> valid = positive.and(even).negate();
Function<A, C> pipeline = first.andThen(second);
Function<A, C> composed = second.compose(first);
Consumer<T> both = firstConsumer.andThen(secondConsumer);
```

Cần hiểu thứ tự thực thi, exception propagation và side effect.

---

## 6. Method reference

| Dạng | Ví dụ | Lambda tương đương |
|---|---|---|
| Static method | `Integer::parseInt` | `s -> Integer.parseInt(s)` |
| Bound instance | `printer::print` | `x -> printer.print(x)` |
| Unbound instance | `String::length` | `s -> s.length()` |
| Constructor | `ArrayList::new` | `() -> new ArrayList<>()` |
| Array constructor | `String[]::new` | `n -> new String[n]` |

Method reference cũng phụ thuộc target type và overload resolution. Chỉ dùng khi làm code rõ hơn; lambda đôi khi dễ đọc hơn nếu cần đổi thứ tự tham số hoặc thêm logic.

---

## 7. Default và static method trong interface

### 7.1. Mục đích

Default method cho phép tiến hóa interface mà không buộc mọi implementation cũ phải thêm method ngay. Đây là nền tảng để bổ sung `forEach`, `stream`, `removeIf`, `sort` vào API cũ.

### 7.2. Quy tắc giải quyết method

1. Method của class/superclass thắng default method của interface.
2. Default method từ interface cụ thể hơn thắng interface cha.
3. Nếu hai interface không liên quan cung cấp cùng default method, class phải override.

```java
class C implements A, B {
    @Override
    public void hello() {
        A.super.hello();
    }
}
```

### 7.3. Static interface method

- Gọi qua tên interface: `Comparator.comparing(...)`.
- Không được kế thừa như instance method.
- Không gọi bằng instance.

Interface vẫn không có mutable instance state/constructor như class.

---

## 8. Stream API: mô hình tinh thần

Stream không phải collection:

- không sở hữu/lưu trữ dữ liệu;
- mô tả pipeline xử lý;
- thường lazy;
- chỉ dùng một lần;
- có thể hữu hạn hoặc vô hạn;
- có thể tuần tự hoặc song song.

```text
Source → Intermediate operations → Terminal operation
```

### 8.1. Nguồn Stream

- `collection.stream()` / `parallelStream()`
- `Arrays.stream(array)`
- `Stream.of(...)`, `Stream.empty()`
- `Stream.iterate(...)`, `Stream.generate(...)`
- `IntStream.range/rangeClosed`
- `Files.lines(path)` và các nguồn I/O khác

### 8.2. Intermediate operations

- Stateless: `map`, `filter`, `peek`, `flatMap`.
- Stateful: `distinct`, `sorted`, một số slice operation.
- Short-circuiting: `limit`.

Intermediate operation trả stream mới và không chạy cho đến terminal operation.

### 8.3. Terminal operations

- `collect`, `reduce`, `count`, `forEach`, `forEachOrdered`
- `min`, `max`
- `findFirst`, `findAny`
- `anyMatch`, `allMatch`, `noneMatch`
- `toArray`

Sau terminal operation, stream đã consumed; tái sử dụng có thể ném `IllegalStateException`.

---

## 9. Lazy evaluation, fusion và short-circuit

```java
Optional<String> first = names.stream()
    .filter(name -> name.length() > 3)
    .map(String::toUpperCase)
    .findFirst();
```

- Các stage thường được fused để từng phần tử đi qua pipeline.
- `findFirst`, `anyMatch`, `limit` có thể dừng sớm.
- Stateful operation như `sorted` có thể cần buffer toàn bộ input trước khi phát kết quả.
- Thứ tự operation ảnh hưởng performance: filter sớm thường giảm dữ liệu cho stage sau.
- Không dựa vào `peek` để chứa business logic; nó chủ yếu hỗ trợ quan sát/debug pipeline.

Infinite stream phải có short-circuit terminal/pipeline phù hợp; `sorted` hoặc `collect` toàn bộ infinite stream sẽ không kết thúc.

---

## 10. `map`, `flatMap` và primitive streams

### 10.1. `map` vs `flatMap`

- `map`: một phần tử thành một kết quả.
- `flatMap`: một phần tử thành một stream rồi làm phẳng.

```java
List<Order> orders = customers.stream()
    .flatMap(customer -> customer.getOrders().stream())
    .collect(Collectors.toList());
```

### 10.2. Primitive streams

`IntStream`, `LongStream`, `DoubleStream` tránh boxing và có API số học:

```java
int total = people.stream()
    .mapToInt(Person::getAge)
    .sum();
```

- `mapToInt/mapToLong/mapToDouble` chuyển sang primitive stream.
- `boxed()` trở lại object stream.
- Có `sum`, `average`, `summaryStatistics`, `range`.

---

## 11. Reduction đúng cách

### 11.1. `reduce`

```java
int sum = numbers.stream().reduce(0, Integer::sum);
Optional<Integer> max = numbers.stream().reduce(Integer::max);
```

Để parallel-safe:

- identity phải là phần tử trung hòa;
- accumulator/combiner phải associative;
- combiner phải tương thích accumulator;
- tránh mutable shared state.

Phép toán không associative như trừ thường cho kết quả khó hiểu khi parallel.

### 11.2. Mutable reduction

Dùng `collect` cho container mutable thay vì `reduce` bằng cách mutate cùng một object:

```java
List<String> result = stream.collect(
    ArrayList::new,
    List::add,
    List::addAll
);
```

Collector cần supplier, accumulator, combiner, finisher và characteristics phù hợp.

---

## 12. Collectors phải biết

### 12.1. Thu thập cơ bản

- `toList`, `toSet`, `toCollection`
- `joining`
- `counting`, `summingInt`, `averagingInt`, `summarizingInt`
- `minBy`, `maxBy`, `reducing`

Không giả định implementation/mutability cụ thể ngoài collector contract.

### 12.2. `toMap`

```java
Map<Long, User> byId = users.stream().collect(
    Collectors.toMap(User::getId, Function.identity())
);
```

Duplicate key làm overload hai tham số ném `IllegalStateException`. Chọn merge policy và map supplier khi cần:

```java
Collectors.toMap(
    User::getEmail,
    Function.identity(),
    (first, second) -> first,
    LinkedHashMap::new
)
```

### 12.3. Grouping và partitioning

- `groupingBy(classifier)` → `Map<K,List<T>>`.
- `groupingBy(classifier, downstream)` → aggregation theo nhóm.
- `partitioningBy(predicate)` → đúng hai bucket boolean.
- `mapping`, `collectingAndThen`, `reducing` làm downstream collector.

---

## 13. Non-interference, statelessness và side effect

Behavior parameter của stream nên:

- không sửa source trong lúc pipeline chạy;
- stateless hoặc quản lý state an toàn;
- deterministic khi nghiệp vụ yêu cầu;
- không phụ thuộc thứ tự thread trong parallel stream.

Sai:

```java
List<String> output = new ArrayList<>();
source.parallelStream().forEach(output::add); // data race
```

Đúng: dùng collector phù hợp.

Encounter order đến từ source/operation. `forEach` trên parallel stream không giữ thứ tự; `forEachOrdered` giữ order nhưng có thể giảm parallel benefit.

---

## 14. Parallel stream

### 14.1. Cơ chế

- Source được chia bằng `Spliterator`.
- Task thường chạy trên `ForkJoinPool.commonPool()`.
- Mỗi nhánh xử lý rồi combine kết quả.
- Spliterator characteristics như `ORDERED`, `SIZED`, `SUBSIZED`, `SORTED`, `DISTINCT` giúp tối ưu.

### 14.2. Khi có thể hiệu quả

- Dữ liệu đủ lớn.
- Công việc CPU-bound trên mỗi phần tử đủ đáng kể.
- Source dễ chia như array/`ArrayList`/primitive range.
- Reduction associative, ít coordination.
- Đã benchmark trên workload/hardware thật.

### 14.3. Khi nên tránh

- I/O blocking, gọi network/database.
- Collection nhỏ hoặc operation rất rẻ.
- Shared mutable state/lock contention.
- Yêu cầu encounter order chặt.
- Chạy trong server/request path mà common pool contention khó kiểm soát.
- Nested parallelism.

Parallel không tự động nhanh hơn và không biến code không thread-safe thành an toàn.

---

## 15. Stream từ I/O và đóng resource

Stream dựa trên in-memory collection thường không cần đóng. Stream giữ resource I/O phải dùng try-with-resources:

```java
try (Stream<String> lines = Files.lines(path, StandardCharsets.UTF_8)) {
    long count = lines.filter(line -> !line.isEmpty()).count();
}
```

`onClose` đăng close handler nhưng vẫn cần caller kích hoạt `close`, thường qua try-with-resources.

---

## 16. `Optional`

### 16.1. Tạo và biến đổi

```java
Optional<String> present = Optional.of("value");
Optional<String> nullable = Optional.ofNullable(maybeNull);
Optional<String> empty = Optional.empty();

Optional<Integer> length = present.map(String::length);
Optional<String> city = findUser(id)
    .flatMap(User::findAddress)
    .map(Address::getCity)
    .filter(value -> !value.isEmpty());
```

- `of(null)` ném `NullPointerException`.
- `map` wrap kết quả non-null; kết quả mapper null thành empty.
- `flatMap` dùng khi mapper đã trả `Optional`.
- `filter` giữ hoặc loại giá trị.

### 16.2. Lấy giá trị

- `orElse(value)`: argument được tính eager.
- `orElseGet(supplier)`: tính lazy khi empty.
- `orElseThrow(supplier)`: ném exception có ý nghĩa.
- `ifPresent(consumer)`: thực hiện khi có giá trị.
- `get()`: ném `NoSuchElementException` nếu empty; thường nên tránh.

Trong Java 8, `orElseThrow` cần exception supplier; overload không tham số có từ Java 10.

### 16.3. Primitive Optional

`OptionalInt`, `OptionalLong`, `OptionalDouble` tránh boxing và thường xuất hiện từ primitive stream reductions.

### 16.4. Khi không nên dùng

- Không dùng chỉ để thay mọi `null` trong field/DTO/entity.
- Không dùng làm method parameter nếu overload/domain type rõ hơn.
- Không bọc collection; thường trả collection rỗng.
- Không tạo `Optional<Optional<T>>`; dùng `flatMap`.
- `Optional` Java 8 không implements `Serializable`; cân nhắc framework boundary.
- Mục tiêu chính là biểu diễn kết quả trả về có thể vắng mặt.

---

## 17. Date-Time API (`java.time`)

### 17.1. Chọn đúng type

| Type | Ý nghĩa |
|---|---|
| `Instant` | Điểm tuyệt đối trên timeline UTC |
| `LocalDate` | Ngày không time/zone |
| `LocalTime` | Giờ không date/zone |
| `LocalDateTime` | Date-time không zone/offset |
| `OffsetDateTime` | Date-time với UTC offset |
| `ZonedDateTime` | Date-time với `ZoneId` và timezone rules |
| `Year`, `YearMonth`, `MonthDay` | Mô hình domain chuyên biệt |
| `Duration` | Lượng thời gian theo giây/nano |
| `Period` | Lượng thời gian theo năm/tháng/ngày |

Không dùng `LocalDateTime` làm timestamp toàn cục; nó không xác định duy nhất một instant nếu thiếu zone/offset.

### 17.2. Immutability

Hầu hết type `java.time` là immutable và thread-safe:

```java
LocalDate tomorrow = today.plusDays(1); // today không đổi
```

### 17.3. Zone, DST gap và overlap

- `ZoneId` đại diện vùng như `Asia/Ho_Chi_Minh`.
- `ZoneOffset` chỉ là offset cố định như `+07:00`.
- DST có thể tạo local time không tồn tại (gap) hoặc xuất hiện hai lần (overlap).
- Lịch quốc tế phải giữ đúng `ZoneId`/`Instant`, không chỉ offset hiện tại.

### 17.4. Parse/format

`DateTimeFormatter` immutable và thread-safe, khác `SimpleDateFormat`.

```java
DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/uuuu");
LocalDate date = LocalDate.parse("25/09/2026", formatter);
```

Phân biệt `yyyy` (year-of-era) và `uuuu` (proleptic year) khi strict date handling.

### 17.5. Amount, unit và adjuster

- `Duration`, `Period`
- `ChronoUnit.DAYS.between(...)`
- `TemporalAdjusters.firstDayOfMonth()`, `next(DayOfWeek.MONDAY)`
- `Clock` giúp test code phụ thuộc “now” một cách deterministic.

### 17.6. Interop legacy

- `Date.from(instant)` và `date.toInstant()`.
- `GregorianCalendar.from(zonedDateTime)` và `calendar.toZonedDateTime()`.
- Chuyển `LocalDateTime` thành instant phải cung cấp zone/offset.

---

## 18. `CompletableFuture`

### 18.1. Tạo task

```java
CompletableFuture<Void> task = CompletableFuture.runAsync(() -> work());
CompletableFuture<Result> result = CompletableFuture.supplyAsync(() -> load());
```

Không truyền executor thì async stage thường dùng `ForkJoinPool.commonPool()`. Với server/blocking I/O, nên truyền executor được quản lý và sizing phù hợp.

### 18.2. Chaining

| Method | Ý nghĩa |
|---|---|
| `thenApply` | T → U đồng bộ sau completion |
| `thenAccept` | Consume kết quả |
| `thenRun` | Chạy action không cần kết quả |
| `thenCompose` | Flat-map T → `CompletionStage<U>` |
| `thenCombine` | Kết hợp hai future độc lập |
| `allOf` | Chờ tất cả |
| `anyOf` | Hoàn thành khi một future hoàn thành |

`thenApply` tạo nested future nếu function trả future; dùng `thenCompose` để flatten.

### 18.3. Sync vs Async variants

- Method không có `Async` có thể chạy trên thread hoàn thành stage trước.
- `...Async` dùng common pool hoặc executor truyền vào.
- Async không có nghĩa tự động non-blocking; supplier vẫn có thể block thread executor.

### 18.4. Exception

- `exceptionally`: fallback khi lỗi.
- `handle`: biến đổi cả success/failure.
- `whenComplete`: quan sát completion, thường không biến đổi result.
- Exception thường được wrap trong `CompletionException` khi `join`.

### 18.5. `get`, `join`, cancellation và timeout

- `get()` checked exceptions và có overload timeout.
- `join()` ném unchecked `CompletionException`.
- Cancellation không bảo đảm dừng computation nếu task không hợp tác.
- Java 8 chưa có `orTimeout/completeOnTimeout`; có thể phối hợp scheduled executor hoặc `get(timeout, unit)`.
- Tránh block `join/get` quá sớm làm mất lợi ích composition.

---

## 19. Collection, Map và Comparator API mới

### 19.1. Collection/Iterable/List

- `Iterable.forEach`
- `Collection.removeIf`
- `Collection.stream`, `parallelStream`
- `List.replaceAll`
- `List.sort`
- `Spliterator`

### 19.2. Map

- `getOrDefault`, `forEach`, `replaceAll`
- `putIfAbsent`, conditional `remove/replace`
- `computeIfAbsent`, `computeIfPresent`, `compute`
- `merge`

Hiểu null semantics, remapping function trả null và atomicity phụ thuộc implementation; `ConcurrentHashMap` cung cấp contract concurrent riêng.

### 19.3. Comparator

```java
Comparator<Person> order = Comparator
    .comparing(Person::getLastName)
    .thenComparing(Person::getFirstName)
    .thenComparingInt(Person::getAge)
    .reversed();
```

- `comparing`, `comparingInt/Long/Double`
- `thenComparing`
- `naturalOrder`, `reverseOrder`
- `nullsFirst`, `nullsLast`
- `reversed`

Tránh comparator bằng phép trừ vì overflow.

---

## 20. Các API Java 8 khác nên biết

### 20.1. Base64

```java
String encoded = Base64.getEncoder()
    .encodeToString(data.getBytes(StandardCharsets.UTF_8));
byte[] decoded = Base64.getDecoder().decode(encoded);
```

Có basic, URL-safe và MIME encoder/decoder. Base64 là encoding, không phải encryption hay password hashing.

### 20.2. `StringJoiner`

```java
StringJoiner joiner = new StringJoiner(", ", "[", "]");
joiner.add("A").add("B");
```

Là cơ chế đứng sau nhiều use case của `Collectors.joining`.

### 20.3. Files/IO

- `Files.lines`, `Files.list`, `Files.walk`, `Files.find`
- Các stream này giữ resource và cần try-with-resources.
- `UncheckedIOException` giúp biểu diễn lỗi I/O trong functional pipeline nhưng vẫn phải xử lý ở boundary phù hợp.

### 20.4. Arrays và numbers

- `Arrays.parallelSort`
- `Arrays.setAll`, `parallelSetAll`, `parallelPrefix`
- `Integer/Long` unsigned operations
- `Math.addExact`, `subtractExact`, `multiplyExact`, `toIntExact`

Exact arithmetic ném `ArithmeticException` khi overflow thay vì âm thầm wrap.

---

## 21. Annotation và reflection

### 21.1. Repeating annotations

`@Repeatable(Container.class)` cho phép lặp cùng annotation trên một declaration. Reflection cần dùng `getAnnotationsByType` để đọc cả direct/container forms.

### 21.2. Type annotations

Java 8 cho phép annotation xuất hiện tại vị trí sử dụng type, phục vụ static analysis:

```java
List<@NonNull String> names;
```

Annotation tự nó không thực thi null checking nếu không có compiler/tool/framework xử lý.

### 21.3. Parameter names

Compiler option `-parameters` lưu tên parameter để reflection đọc được. Nếu không bật, không giả định tên source còn tồn tại trong bytecode.

---

## 22. Concurrency additions quan trọng

- `CompletableFuture`/`CompletionStage`
- `StampedLock` với optimistic read; phải validate stamp và luôn release lock đúng cách.
- `LongAdder`/`DoubleAdder` cho counter contention cao; `sum()` không phải atomic snapshot trong lúc update.
- `LongAccumulator`/`DoubleAccumulator`
- `ConcurrentHashMap` bulk operations và atomic mapping methods.
- Common ForkJoinPool được dùng bởi parallel stream và nhiều async stage mặc định.

Không chọn primitive concurrency chỉ vì mới; bắt đầu từ yêu cầu atomicity/consistency và benchmark contention thực tế.

---

## 23. JVM và công cụ Java 8

### 23.1. Metaspace thay PermGen

- Class metadata chuyển từ fixed PermGen sang native-memory Metaspace.
- Loại bỏ `-XX:PermSize`/`MaxPermSize` không có nghĩa class metadata vô hạn an toàn.
- Có thể giới hạn bằng `-XX:MaxMetaspaceSize`.
- ClassLoader leak vẫn gây Metaspace leak.

### 23.2. Nashorn

Java 8 từng cung cấp Nashorn JavaScript engine. Đây là kiến thức lịch sử: Nashorn bị deprecated ở Java 11 và bị loại khỏi JDK ở Java 15. Không chọn nó cho thiết kế mới.

### 23.3. `javap` và quan sát bytecode

Dùng `javap -c -p` để quan sát `invokedynamic` của lambda và hiểu compiler output, nhưng không phụ thuộc bytecode shape nội bộ như API ổn định.

---

## 24. Lỗi và bẫy phải nhận ra

- [ ] Gọi API Java 9+ nhưng tưởng là Java 8.
- [ ] Nghĩ lambda luôn được biên dịch thành anonymous class.
- [ ] Hiểu “effectively final” là object cũng immutable.
- [ ] Nhầm `this` trong lambda với `this` trong anonymous class.
- [ ] Tạo overload functional interface gây ambiguous lambda.
- [ ] Dùng generic functional interface làm hot path và boxing quá nhiều.
- [ ] Tái sử dụng stream sau terminal operation.
- [ ] Sửa source/shared state trong stream pipeline.
- [ ] Dùng `peek` cho business side effect.
- [ ] Dùng reduction không associative trên parallel stream.
- [ ] Cho rằng parallel stream luôn nhanh hơn.
- [ ] Dùng parallel stream cho blocking I/O/common-pool workload.
- [ ] Quên đóng `Files.lines/list/walk` stream.
- [ ] `Collectors.toMap` gặp duplicate key.
- [ ] Gọi `Optional.get()` không kiểm tra.
- [ ] Dùng `orElse(expensive())` rồi tưởng lazy.
- [ ] Lạm dụng `Optional` cho field, parameter hoặc collection.
- [ ] Dùng `LocalDateTime` như timestamp toàn cục.
- [ ] Bỏ qua DST gap/overlap.
- [ ] Dùng `yyyy` khi cần proleptic year `uuuu`.
- [ ] Dùng common pool cho blocking `CompletableFuture` task.
- [ ] Nhầm `thenApply` với `thenCompose`.
- [ ] Nuốt exception trong async pipeline.
- [ ] Nghĩ cancellation chắc chắn interrupt/dừng computation.
- [ ] Nghĩ Base64 là bảo mật.
- [ ] Nghĩ type annotation tự động được enforce.
- [ ] Cho rằng Metaspace loại bỏ classloader leak.

---

## 25. Lộ trình học đề xuất

### Mức 1 — Ngôn ngữ

- [ ] Lambda syntax, SAM, method reference.
- [ ] `java.util.function` và composition.
- [ ] Effectively final, target typing, lexical scope.
- [ ] Default/static interface method.

### Mức 2 — Stream và Optional

- [ ] Pipeline, lazy, intermediate/terminal.
- [ ] map/flatMap/filter/reduce/collect.
- [ ] Collector, grouping, duplicate key.
- [ ] Primitive stream và Optional.
- [ ] Non-interference và side effect.

### Mức 3 — Date/time và async

- [ ] `java.time`, zone/DST, formatter, Clock.
- [ ] `CompletableFuture` chaining/composition/error handling.
- [ ] Executor/common-pool trade-off.

### Mức 4 — Chuyên sâu

- [ ] Spliterator và parallel stream.
- [ ] Collector contract/characteristics.
- [ ] `invokedynamic`/LambdaMetafactory.
- [ ] Concurrency additions, annotations, Metaspace.
- [ ] Version compatibility với `--release 8`.

---

## 26. Bài thực hành

1. Chuyển anonymous class sang lambda và so sánh `this` semantics.
2. Viết các ví dụ cho bốn dạng method reference.
3. Tạo functional interface có checked exception và wrapper an toàn.
4. Xử lý danh sách nhân viên bằng filter/map/grouping/averaging.
5. Dùng `flatMap` gộp order của nhiều customer.
6. Viết collector `toMap` với ba duplicate-key policy.
7. So sánh object stream và `IntStream` bằng JMH.
8. Viết reduction đúng và một reduction sai khi chạy parallel.
9. Đo sequential vs parallel stream trên nhiều kích thước/workload.
10. Dùng `Optional` xây pipeline tìm user/address/city.
11. Viết test chứng minh `orElse` eager, `orElseGet` lazy.
12. Tính lịch qua nhiều zone và test một DST gap/overlap.
13. Inject `Clock` để test code phụ thuộc thời gian.
14. Ghép hai API độc lập bằng `thenCombine`, chuỗi phụ thuộc bằng `thenCompose`.
15. Xử lý success/failure của `CompletableFuture` bằng `handle`/`whenComplete`.
16. Dùng `Files.lines` đúng với try-with-resources.
17. Dùng `LongAdder` cho counter contention và so sánh `AtomicLong`.
18. Dùng `javap` quan sát `invokedynamic` của lambda.
19. Compile ví dụ với `--release 8` để tìm API dùng nhầm version.

---

## 27. Câu hỏi tự kiểm tra

1. SAM được xác định như thế nào?
2. Target typing ảnh hưởng lambda/method reference ra sao?
3. Effectively final bảo đảm điều gì và không bảo đảm điều gì?
4. `this` trong lambda khác anonymous class thế nào?
5. Vì sao Java dùng `invokedynamic` cho lambda?
6. Intermediate và terminal operation khác nhau thế nào?
7. Stateful operation ảnh hưởng lazy/parallel pipeline ra sao?
8. `map` khác `flatMap` thế nào?
9. Reduction cần identity/associativity gì để parallel-safe?
10. Khi nào dùng primitive stream?
11. Vì sao parallel stream có thể chậm hoặc nguy hiểm?
12. `Optional.map` khác `flatMap` thế nào?
13. `orElse` khác `orElseGet` thế nào?
14. Khi nào dùng `Instant`, `LocalDateTime`, `ZonedDateTime`?
15. `Duration` khác `Period` thế nào?
16. `thenApply` khác `thenCompose` và `thenCombine` thế nào?
17. Sync/async CompletableFuture stage chạy ở đâu?
18. Default-method conflict được giải quyết theo quy tắc nào?
19. API nào trong file nguồn thực ra là Java 9+?
20. Metaspace thay đổi điều gì và không giải quyết vấn đề gì?

---

## 28. Tài liệu liên quan trong repo

- [Bài Java 8+ Modern Features](../01-java8-modern-features.md)
- [Bài tập Java 8](../02-java8-bai-tap.md)
- [Lộ trình Java Core](../../java-core-roadmap.md)

## 29. Tài liệu Java 8 chính thức

- [Java SE 8 — Lambda Expressions](https://docs.oracle.com/javase/tutorial/java/javaOO/lambdaexpressions.html)
- [Java SE 8 — `java.util.function`](https://docs.oracle.com/javase/8/docs/api/java/util/function/package-summary.html)
- [Java SE 8 — Stream package](https://docs.oracle.com/javase/8/docs/api/java/util/stream/package-summary.html)
- [Java SE 8 — `Collectors`](https://docs.oracle.com/javase/8/docs/api/java/util/stream/Collectors.html)
- [Java SE 8 — `Optional`](https://docs.oracle.com/javase/8/docs/api/java/util/Optional.html)
- [Java SE 8 — Date-Time API](https://docs.oracle.com/javase/8/docs/api/java/time/package-summary.html)
- [Java SE 8 — `CompletableFuture`](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletableFuture.html)
- [Java SE 8 — `Collection`](https://docs.oracle.com/javase/8/docs/api/java/util/Collection.html)
- [Java SE 8 — `Map`](https://docs.oracle.com/javase/8/docs/api/java/util/Map.html)
- [Java SE 8 — `Comparator`](https://docs.oracle.com/javase/8/docs/api/java/util/Comparator.html)
- [Java SE 8 — `Base64`](https://docs.oracle.com/javase/8/docs/api/java/util/Base64.html)
- [Java SE 8 — `Spliterator`](https://docs.oracle.com/javase/8/docs/api/java/util/Spliterator.html)

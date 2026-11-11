# Bài tập luyện tập: Java 8+ Modern Features

> Bộ bài tập bám sát nội dung [01-java8-modern-features.md](01-java8-modern-features.md). Chia làm 3 mức độ: Cơ bản → Trung bình → Nâng cao. Mỗi bài đều ghi rõ mục tiêu, yêu cầu, gợi ý, và tiêu chí hoàn thành để bạn tự chấm.

---

## Mức 1 — Cơ bản (Củng cố kiến thức nền)

### Bài 1: Chứng minh lỗi "effectively final" khi capture biến trong Lambda
**Mục tiêu**: hiểu rõ tại sao lambda bắt buộc biến local phải "effectively final", đúng như cảnh báo ở mục 1 bài học.

**Yêu cầu**:
1. Viết 1 vòng lặp `for` thông thường, bên trong tạo 1 `Runnable` bằng lambda capture biến đếm `i` của vòng lặp — cố tình để compiler báo lỗi (không cần sửa ngay, chụp lại thông báo lỗi).
2. Sửa lại bằng cách tạo 1 biến `final int captured = i;` bên trong thân vòng lặp trước khi tạo lambda — biên dịch thành công.
3. Chạy thử: tạo 1 `List<Runnable>` chứa 5 lambda tương tự (mỗi lambda in ra giá trị đã capture), gọi từng cái để chứng minh mỗi lambda giữ đúng giá trị riêng của nó tại thời điểm tạo (không bị "dính" giá trị cuối cùng của biến vòng lặp).

**Hoàn thành khi**: bạn giải thích được bằng lời tại sao lambda "capture theo giá trị" (snapshot tại thời điểm tạo) lại là lý do biến phải bất biến — liên hệ tới trường hợp lambda được thực thi ở 1 thread khác, muộn hơn nhiều so với lúc khai báo.

---

### Bài 2: Kết hợp (compose) Predicate và Function
**Mục tiêu**: thành thạo các method `and()`, `or()`, `negate()`, `andThen()`, `compose()` ở mục 2 bài học.

**Yêu cầu**:
1. Viết 3 `Predicate<Integer>` riêng lẻ: `isPositive`, `isEven`, `isGreaterThan10`. Kết hợp cả 3 bằng `and()` thành 1 predicate duy nhất, test với ít nhất 5 giá trị khác nhau (bao gồm giá trị đúng và sai từng điều kiện).
2. Viết `Function<Integer, Integer> square` và `Function<Integer, Integer> addOne`. So sánh kết quả của `square.andThen(addOne)` và `square.compose(addOne)` với cùng đầu vào — chứng minh bằng số liệu cụ thể sự khác biệt thứ tự thực thi.
3. Viết 1 `Predicate` phức tạp hơn kết hợp cả `and()`, `or()`, `negate()` trong cùng 1 biểu thức, dự đoán kết quả trước khi chạy để tự kiểm chứng độ hiểu.

**Hoàn thành khi**: bạn giải thích chính xác bằng công thức toán học sự khác biệt giữa `f.andThen(g)` (= g(f(x))) và `f.compose(g)` (= f(g(x))).

---

### Bài 3: 4 loại Method Reference
**Mục tiêu**: nhận diện và tự viết đúng cả 4 dạng method reference ở mục 3 bài học.

**Yêu cầu**: với 1 `List<String> words`, viết lại **cùng 1 đoạn logic** bằng cả lambda và method reference tương ứng cho từng loại sau, in kết quả ra để đối chiếu 2 cách cho cùng kết quả:
1. Static method reference: chuyển `List<String>` số dạng chuỗi thành `List<Integer>` bằng `Integer::parseInt`.
2. Instance method của object cụ thể: in từng phần tử ra `System.out` bằng `System.out::println`.
3. Instance method của "tham số bất kỳ": chuyển toàn bộ `words` thành chữ hoa bằng `String::toUpperCase`.
4. Constructor reference: dùng `Supplier<List<String>>` với `ArrayList::new` để tạo 1 list rỗng mới, sau đó `addAll()` toàn bộ `words` vào đó.

**Hoàn thành khi**: cả 4 cặp (lambda vs method reference) đều cho kết quả giống hệt nhau, và bạn phân biệt được rõ ràng loại 3 (`ClassName::instanceMethod`, tham số truyền vào chính là object gọi method) khác loại 2 (`object::instanceMethod`, object đã cố định từ trước) ở điểm nào.

---

## Mức 2 — Trung bình (Áp dụng Stream & Collectors)

### Bài 4: Pipeline Stream tổng hợp — quản lý nhân viên theo phòng ban
**Mục tiêu**: áp dụng đúng chuỗi `filter → map → groupingBy` như gợi ý bài tập cuối bài học.

**Đề bài**: Cho `List<Employee>` với các field `name`, `department`, `salary`. Yêu cầu:
1. Lọc ra các nhân viên có `salary > 10_000_000`.
2. Nhóm các nhân viên đã lọc theo `department` (`Collectors.groupingBy`).
3. Với mỗi phòng ban, tính **lương trung bình** (`Collectors.averagingDouble` làm downstream collector của `groupingBy`) — kết quả cuối cùng là `Map<String, Double>`.
4. Viết thêm 1 biến thể: thay vì lương trung bình, đếm **số lượng** nhân viên đủ điều kiện mỗi phòng ban (`Collectors.counting()`).

**Test bắt buộc**: tạo dữ liệu mẫu với ít nhất 3 phòng ban, mỗi phòng ban có nhân viên cả trên và dưới ngưỡng lương, kiểm chứng kết quả nhóm và tính trung bình đúng bằng tay.

**Hoàn thành khi**: pipeline chạy đúng với dữ liệu mẫu tự thiết kế, và bạn giải thích được vai trò của "downstream collector" trong `groupingBy` (tham số thứ 2).

---

### Bài 5: `flatMap()` — gộp đơn hàng từ nhiều khách hàng
**Mục tiêu**: phân biệt rõ khi nào bắt buộc dùng `flatMap()` thay vì `map()`, đúng bài tập gợi ý cuối bài học.

**Đề bài**: Cho `List<Customer>`, mỗi `Customer` có field `List<Order> orders`. Yêu cầu:
1. Dùng `flatMap()` để gộp toàn bộ đơn hàng của mọi khách hàng thành 1 `List<Order>` duy nhất.
2. Thử dùng `map()` thay vì `flatMap()` cho cùng logic — quan sát kết quả trả về là `List<List<Order>>` thay vì `List<Order>`, chứng minh bằng code tại sao đây là lỗi cần `flatMap()` để sửa.
3. Mở rộng: từ `List<Order>` đã gộp, dùng thêm `flatMap()` lần 2 để lấy ra toàn bộ `OrderItem` từ field `List<OrderItem> items` của mỗi `Order` — tổng cộng 2 lần `flatMap()` liên tiếp.

**Hoàn thành khi**: chứng minh được bằng code sự khác biệt giữa dùng `map()` (list lồng list) và `flatMap()` (list phẳng), và pipeline 2 lần `flatMap()` liên tiếp chạy đúng cho ra `List<OrderItem>`.

---

### Bài 6: Collectors nâng cao — `partitioningBy`, `joining`, `summarizingInt`
**Mục tiêu**: thành thạo các Collector ít dùng hơn `toList()` nhưng rất hữu ích, đúng mục 6 bài học.

**Yêu cầu**:
1. Với `List<String> words`, dùng `partitioningBy` chia thành 2 nhóm: từ có độ dài > 5 và <= 5. So sánh với cách làm tương đương bằng `groupingBy` (dùng cùng điều kiện boolean) — chứng minh 2 cách cho cùng kết quả nhưng `partitioningBy` **luôn** có đủ cả 2 key `true`/`false` trong map kết quả (kể cả khi 1 nhóm rỗng), còn `groupingBy` thì không.
2. Dùng `Collectors.joining(", ", "[", "]")` nối danh sách tên nhân viên thành 1 chuỗi có định dạng.
3. Dùng `Collectors.summarizingInt()` trên độ dài các từ trong `words`, in ra đầy đủ `min`, `max`, `average`, `sum`, `count` — so sánh với việc tự viết 5 dòng code tính riêng lẻ từng giá trị bằng vòng lặp thường, để thấy `summarizingInt` gọn hơn nhiều.

**Hoàn thành khi**: chứng minh bằng code cụ thể sự khác biệt giữa `partitioningBy` và `groupingBy` khi 1 trong 2 nhóm không có phần tử nào.

---

## Mức 3 — Nâng cao (Lazy Evaluation, Optional, Date/Time)

### Bài 7: Chứng minh Lazy Evaluation bằng log thực tế
**Mục tiêu**: hiểu sâu cơ chế Stream chỉ thực thi khi có terminal operation, đúng mục 5 bài học.

**Yêu cầu**:
1. Viết 1 pipeline Stream với `filter()` và `map()` đều có `System.out.println()` bên trong để log lại mỗi lần phần tử được xử lý — **không gọi terminal operation ngay**, chứng minh chưa có log nào được in ra.
2. Gọi `collect(Collectors.toList())` — quan sát log in ra theo đúng thứ tự nào (gợi ý: Stream xử lý **từng phần tử** qua toàn bộ pipeline trước khi chuyển sang phần tử tiếp theo, không phải chạy hết `filter` cho mọi phần tử rồi mới chạy `map`).
3. Viết 1 Stream vô hạn bằng `Stream.iterate(1, n -> n * 2)`, kết hợp `filter()` (log lại) và `limit(5)` — chứng minh chương trình vẫn kết thúc bình thường (không treo) và log chỉ in ra đúng số lần cần thiết để đủ 5 phần tử thỏa `limit`.
4. Thử gọi terminal operation **2 lần** trên cùng 1 biến `Stream` đã tạo — quan sát và giải thích `IllegalStateException` ném ra ở lần gọi thứ 2.

**Hoàn thành khi**: bạn trình bày được (bằng log thực tế, không chỉ suy đoán) đúng thứ tự xử lý "theo từng phần tử xuyên suốt pipeline" của Stream, khác với cách nghĩ sai lầm phổ biến "chạy hết filter cho mọi phần tử rồi mới chạy map".

---

### Bài 8: Optional đúng chuẩn — tránh các bẫy phổ biến
**Mục tiêu**: áp dụng đúng best practice ở mục 7 bài học, tránh lạm dụng Optional.

**Yêu cầu**:
1. Viết `Optional<User> findUserById(int id)` trả về `Optional.empty()` nếu không tìm thấy. Dùng `map()` + `orElseThrow()` để lấy email của user, ném `NoSuchElementException` có message rõ ràng (kèm `id`) nếu không tìm thấy — đúng bài tập gợi ý cuối bài học.
2. Viết 1 method `String expensiveDefault()` có `Thread.sleep(1000)` bên trong để mô phỏng tính toán tốn kém, kèm `System.out.println` để biết khi nào nó được gọi. So sánh `optional.orElse(expensiveDefault())` và `optional.orElseGet(() -> expensiveDefault())` khi `optional` **đã có giá trị** — đo thời gian chạy để chứng minh `orElse()` vẫn lãng phí gọi `expensiveDefault()` dù không dùng tới kết quả.
3. Viết 1 class `User` **cố tình sai** dùng `Optional<String> email` làm field, sau đó refactor lại đúng chuẩn (field kiểu `String` bình thường, có thể `null`, chỉ dùng `Optional` ở method trả về) — giải thích bằng lời tại sao cách ban đầu là anti-pattern.

**Hoàn thành khi**: đo được chênh lệch thời gian thực tế ở bài 2 (chứng minh `orElse()` luôn tốn ~1 giây bất kể optional có giá trị hay không, còn `orElseGet()` thì không), và refactor đúng ở bài 3.

---

### Bài 9: Date/Time API — đếm ngày đến sinh nhật & giải quyết xung đột Default Method
**Mục tiêu**: áp dụng `LocalDate`/`Period` vào bài toán thực tế, đồng thời ôn lại cách giải quyết xung đột default method ở mục 9 bài học.

**Yêu cầu phần Date/Time**:
1. Viết hàm `int daysUntilNextBirthday(LocalDate birthDate)` — tính số ngày còn lại đến sinh nhật **tiếp theo** kể từ hôm nay, đúng bài tập gợi ý cuối bài học. Xử lý đúng edge case: nếu sinh nhật năm nay đã qua, phải tính tới sinh nhật năm **sau**; nếu sinh nhật đúng là hôm nay, trả về `0`.
2. Xử lý riêng trường hợp đặc biệt: người sinh ngày 29/2 (năm nhuận) — quyết định rõ ràng sinh nhật của họ ở năm không nhuận sẽ tính vào ngày nào (28/2 hay 1/3) và cài đặt đúng theo quyết định đó.
3. Dùng `Period.between()` để tính tuổi chính xác (năm, tháng, ngày) tính đến hôm nay từ `birthDate`.

**Yêu cầu phần Default Method**:
4. Viết 2 interface `Flyable` và `Swimmable`, cả 2 đều có default method `move()` với nội dung khác nhau. Viết class `Duck implements Flyable, Swimmable` — chứng minh compiler bắt buộc phải override `move()` để giải quyết xung đột.
5. Trong `move()` đã override của `Duck`, gọi **cả 2** phiên bản gốc bằng `Flyable.super.move()` và `Swimmable.super.move()`, rồi thêm log riêng của `Duck` — chứng minh có thể tận dụng cả 2 default method gốc thay vì viết lại từ đầu.

**Hoàn thành khi**: `daysUntilNextBirthday()` đúng cho cả trường hợp thường và trường hợp 29/2, và `Duck.move()` gọi đúng cả 2 default method gốc kèm log riêng.

---

## Cách tự đánh giá

| Mức độ | Tiêu chí đạt |
|---|---|
| Cơ bản (1-3) | Chạy được, in ra đúng kết quả, giải thích được bằng lời (không chỉ copy code) |
| Trung bình (4-6) | Pipeline Stream/Collectors chạy đúng với dữ liệu mẫu tự thiết kế, không chỉ copy ví dụ có sẵn |
| Nâng cao (7-9) | Chứng minh được cơ chế bên trong (lazy evaluation, chi phí Optional, edge case ngày tháng) bằng số liệu/log thực tế, không chỉ dựa vào lý thuyết |

> **Gợi ý học tập**: sau khi hoàn thành mỗi bài, quay lại đọc phần tương ứng trong [01-java8-modern-features.md](01-java8-modern-features.md) và tự hỏi "code mình viết có khớp với cơ chế đã mô tả không?" — đây là cách tốt nhất để biến lý thuyết thành hiểu biết thực sự.

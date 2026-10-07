# Bài tập luyện tập: Hướng đối tượng (OOP)

> Bộ bài tập bám sát nội dung [01-huong-doi-tuong-oop.md](01-huong-doi-tuong-oop.md). Chia làm 3 mức độ: Cơ bản → Trung bình → Nâng cao. Mỗi bài đều ghi rõ mục tiêu, yêu cầu, gợi ý, và tiêu chí hoàn thành để bạn tự chấm.

---

## Mức 1 — Cơ bản (Củng cố kiến thức nền)

### Bài 1: Encapsulation đúng nghĩa với `BankAccount`
**Mục tiêu**: hiểu vì sao encapsulation không chỉ là "thêm `private` + getter/setter", mà là bảo vệ invariant.

**Yêu cầu**:
1. Viết class `BankAccount` với field `private double balance` và `private final String owner`.
2. Method `deposit(double amount)`: ném `IllegalArgumentException` nếu `amount <= 0`.
3. Method `withdraw(double amount)`: ném `IllegalArgumentException` nếu `amount <= 0`, ném `IllegalStateException` nếu `amount > balance` (không đủ tiền).
4. **Không** viết `setBalance()` — balance chỉ được thay đổi thông qua `deposit()`/`withdraw()`.
5. Viết `main()` thử: nạp tiền âm, rút quá số dư, rút đúng số dư còn lại — bắt exception và in thông báo rõ ràng cho từng trường hợp.

**Hoàn thành khi**: không có cách nào từ bên ngoài class có thể làm `balance` âm hoặc set trực tiếp một giá trị tuỳ ý.

---

### Bài 2: Constructor chaining với `this()` và `super()`
**Mục tiêu**: hiểu rõ khác biệt giữa gọi constructor cùng class và constructor của class cha.

**Yêu cầu**:
1. Viết class `Vehicle` với field `String brand`, `int wheels`, có 1 constructor đầy đủ tham số.
2. Viết class `Car extends Vehicle` thêm field `int doors`, có:
   - 1 constructor đầy đủ tham số gọi `super(brand, 4)` (xe hơi luôn 4 bánh).
   - 1 constructor chỉ nhận `brand`, gọi `this(brand, 4)` (mặc định 4 cửa).
3. Viết `toString()` cho cả 2 class, override ở `Car` để in thêm `doors`.
4. Thử compile khi cố tình đặt `super(...)` **không phải** dòng đầu tiên trong constructor — quan sát lỗi compiler.

**Hoàn thành khi**: bạn giải thích được bằng lời tại sao Java bắt buộc `super()` phải là dòng đầu tiên (gợi ý: liên quan đến thứ tự khởi tạo object cha trước con).

---

### Bài 3: `equals()`/`hashCode()` đúng chuẩn với `Employee`
**Mục tiêu**: tránh lỗi kinh điển "chỉ override 1 trong 2 method".

**Yêu cầu**:
1. Viết class `Employee` với field `int id`, `String name`.
2. Override `equals()` để 2 `Employee` bằng nhau khi cùng `id`.
3. Override `hashCode()` dựa trên `id` (dùng `Objects.hash()`).
4. Tạo `HashSet<Employee>`, thêm 3 `Employee` trong đó có 2 object khác instance nhưng cùng `id` — in `set.size()` để kiểm chứng không bị trùng.
5. **Thử nghiệm phản chứng**: comment lại `hashCode()` (chỉ giữ `equals()`), lặp lại bước 4 — quan sát `set.size()` thay đổi thế nào và giải thích tại sao.

**Hoàn thành khi**: bạn chỉ ra được chính xác vì sao thiếu `hashCode()` khiến `HashSet` không nhận diện được 2 object "bằng nhau" theo `equals()`.

---

## Mức 2 — Trung bình (Áp dụng thiết kế)

### Bài 4: Hệ thống `Shape` — Abstract class + Polymorphism
**Mục tiêu**: áp dụng abstraction, inheritance, và runtime polymorphism cùng lúc (đúng như bài tập gợi ý cuối bài học, làm chi tiết hơn).

**Yêu cầu**:
1. `abstract class Shape` có `abstract double area()`, `abstract double perimeter()`, và method cụ thể `describe()` in ra tên class + area + perimeter (dùng chung cho mọi subclass, không override).
2. Cài `Circle`, `Rectangle`, `Triangle` (dùng công thức Heron cho tam giác 3 cạnh) — mỗi class override `area()`, `perimeter()`, `toString()`.
3. Tạo `List<Shape>` chứa cả 3 loại, dùng vòng lặp gọi `describe()` trên từng phần tử — quan sát runtime polymorphism (cùng 1 lời gọi `area()` nhưng chạy code khác nhau tùy object thực tế).
4. Sắp xếp `List<Shape>` theo `area()` tăng dần bằng `Comparator` (dùng `Comparable` hoặc `List.sort(Comparator.comparingDouble(...))`).

**Hoàn thành khi**: thêm được 1 shape mới (VD `Square`) mà **không cần sửa** code của `describe()` hay logic sắp xếp — minh chứng cho nguyên tắc Open/Closed nhờ abstraction.

---

### Bài 5: Overloading vs Overriding — bẫy static binding
**Mục tiêu**: phân biệt rõ compile-time binding (overloading) và runtime binding (overriding) bằng ví dụ gây nhầm lẫn thực tế.

**Yêu cầu**:
1. Viết class `Animal` với method `void makeSound()`. Viết `Dog extends Animal` override `makeSound()`.
2. Viết thêm 1 method overload gây nhiễu: `Animal` có `void feed(Animal a)`, `Dog` (không override, mà **overload**) thêm `void feed(Dog d)`.
3. Viết đoạn code:
   ```java
   Animal a = new Dog();
   a.makeSound();      // (1)
   Dog d = new Dog();
   Animal ref = d;
   feedSomething(ref); // gọi hàm feed nào?
   ```
4. Dự đoán trước bằng giấy method nào được gọi ở mỗi dòng, sau đó chạy thử để kiểm chứng.

**Hoàn thành khi**: bạn giải thích được bằng lời tại sao `makeSound()` phụ thuộc vào **object thực tế** (runtime) nhưng việc chọn overload nào của `feed()` lại phụ thuộc vào **kiểu khai báo của biến** (compile-time) — đây là điểm gây bug phổ biến nhất khi mới học OOP.

---

### Bài 6: Enum nâng cao — máy tính `Operation` với constant-specific method
**Mục tiêu**: áp dụng enum như 1 dạng polymorphism đặc biệt (đã có khung ví dụ ở mục 8 bài học, làm đầy đủ thành ứng dụng nhỏ).

**Yêu cầu**:
1. Mở rộng enum `Operation` (PLUS, MINUS) trong bài học thêm `TIMES`, `DIVIDE` — mỗi constant tự cài `apply(int a, int b)` riêng.
2. `DIVIDE` phải ném `ArithmeticException` rõ ràng khi chia cho 0 (không để lỗi mặc định của Java ném ra).
3. Thêm field `String symbol` (VD `"+"`, `"-"`, `"*"`, `"/"`) và constructor cho enum, override `toString()` trả về symbol.
4. Viết vòng lặp `for (Operation op : Operation.values())` in ra bảng: `3 + 4 = 7`, `3 - 4 = -1`, ... cho từng phép toán.

**Hoàn thành khi**: thêm 1 phép toán mới (VD `MOD`) chỉ cần thêm 1 constant + cài `apply()`, không cần sửa `switch/if` nào ở nơi khác — minh chứng enum tránh được switch-statement cồng kềnh.

---

## Mức 3 — Nâng cao (Thiết kế hệ thống nhỏ)

### Bài 7: Nested class — mô phỏng `LinkedList` đơn giản bằng Inner Class
**Mục tiêu**: hiểu vì sao `Node` thường được thiết kế là **private static nested class**, không phải inner class hay class riêng.

**Yêu cầu**:
1. Viết class `SimpleList<T>` chứa `private static class Node<T>` (field `T data`, `Node<T> next`).
2. Cài `void add(T item)` (thêm cuối), `T get(int index)`, `int size()`.
3. Cài thêm 1 **inner class (non-static)** `Iterator` bên trong `SimpleList` implements `java.util.Iterator<T>`, giữ con trỏ duyệt và có quyền truy cập trực tiếp `head` của `SimpleList` (nhờ đặc tính inner class giữ reference tới outer instance).
4. Cho `SimpleList` implements `Iterable<T>`, trả về `new Iterator()` ở `iterator()` — để dùng được enhanced-for.

**Câu hỏi bắt buộc trả lời**: tại sao `Node` nên là `static` nested class (không cần reference tới `SimpleList` instance) trong khi `Iterator` **bắt buộc** phải là non-static inner class (cần truy cập `head` của instance cụ thể)? Nếu đổi `Node` thành non-static, có lỗi gì xảy ra về mặt bộ nhớ (mỗi `Node` sẽ giữ dư 1 reference không cần thiết tới `SimpleList`)?

---

### Bài 8: Bẫy shallow copy — chứng minh bug và tự sửa bằng deep copy
**Mục tiêu**: tái hiện đúng lỗi thực tế mà `clone()` mặc định gây ra, đã cảnh báo ở mục 9 bài học.

**Yêu cầu**:
1. Dựng lại ví dụ `Person`/`Address` implements `Cloneable` giống bài học, nhưng **cố tình chỉ gọi `super.clone()`** ở `Person` (không tự clone `Address`) để tạo ra bug shallow copy.
2. Viết test: tạo `Person p1`, `clone()` ra `p2`, sửa `p2.address.city`, kiểm tra `p1.address.city` — chứng minh cả 2 bị ảnh hưởng (bug).
3. Sửa lại `Person.clone()` để tự clone `Address` (deep copy đúng như bài học), chạy lại test — kiểm tra `p1.address.city` **không** bị ảnh hưởng khi sửa `p2`.
4. Viết thêm 1 dòng comment giải thích: nếu `Address` có thêm 1 field kiểu `List<String>` (VD danh sách số điện thoại), thì `clone()` của `Address` cần làm gì thêm để thực sự là deep copy hoàn chỉnh (gợi ý: `List` cũng là reference type, cần `new ArrayList<>(original)`).

**Hoàn thành khi**: cả 2 kịch bản (lỗi và đã sửa) đều chạy được và cho kết quả khác nhau rõ ràng, chứng minh bằng số liệu thực tế thay vì chỉ đọc lý thuyết.

---

### Bài 9: Thiết kế hệ thống `Library` — tổng hợp toàn bộ kiến thức OOP
**Mục tiêu**: kết hợp interface, abstract class, enum, static/instance, equals/hashCode, và instanceof pattern matching vào 1 bài toán thực tế.

**Đề bài**: Thiết kế hệ thống quản lý thư viện đơn giản gồm:
- `interface Borrowable { void borrow(String borrowerName); void returnItem(); boolean isAvailable(); }`
- `abstract class LibraryItem implements Borrowable` — field `String id`, `String title`, chung logic `borrow()`/`returnItem()`/`isAvailable()` (dùng chung cho mọi loại item), abstract method `String getCategory()`.
- 2 subclass: `Book extends LibraryItem` (thêm `String author`), `DVD extends LibraryItem` (thêm `int durationMinutes`).
- `enum ItemStatus { AVAILABLE, BORROWED, LOST }` — dùng thay cho `boolean` để mô tả trạng thái chi tiết hơn.
- `class Library` quản lý `List<LibraryItem>`, có:
  - `static int totalItemsRegistered` — đếm tổng số item từng được tạo ra qua mọi instance `Library` (minh hoạ đúng ý nghĩa `static` — dùng chung, không thuộc về 1 `Library` cụ thể).
  - `void addItem(LibraryItem item)`, `LibraryItem findById(String id)`.
  - `void printCatalog()` — duyệt qua từng item, dùng **pattern matching `instanceof`** (`if (item instanceof Book b)`) để in thêm thông tin riêng (`author` cho Book, `durationMinutes` cho DVD) mà không cần cast thủ công.
- Override `equals()`/`hashCode()` ở `LibraryItem` dựa trên `id` — đảm bảo `findById()` hoặc dùng trong `HashSet<LibraryItem>` hoạt động đúng.

**Test bắt buộc**:
1. Đăng ký 2 `Book` + 1 `DVD` vào 1 `Library`, kiểm tra `totalItemsRegistered == 3`.
2. `borrow()` 1 item, kiểm tra `isAvailable() == false`, thử `borrow()` lần nữa phải ném exception hoặc trả về thông báo item đang được mượn (tự thiết kế cách xử lý, miễn nhất quán).
3. `printCatalog()` in đúng thông tin riêng biệt cho `Book` và `DVD` bằng pattern matching, không dùng ép kiểu `(Book) item` kiểu cũ.

**Hoàn thành khi**: hệ thống chạy đúng toàn bộ test trên, và bạn có thể chỉ ra chính xác trong thiết kế của mình đâu là chỗ dùng encapsulation, đâu là inheritance, đâu là polymorphism, đâu là abstraction — đủ cả 4 tính chất OOP trong 1 hệ thống thống nhất.

---

## Cách tự đánh giá

| Mức độ | Tiêu chí đạt |
|---|---|
| Cơ bản (1-3) | Chạy được, in ra đúng kết quả, giải thích được bằng lời (không chỉ copy code) |
| Trung bình (4-6) | Thêm được tính năng mới mà không phải sửa code cũ (chứng minh hiểu đúng bản chất polymorphism/abstraction) |
| Nâng cao (7-9) | Hệ thống hoạt động đúng với các edge case, và chỉ ra được rõ ràng từng tính chất OOP đang được áp dụng ở đâu trong code |

> **Gợi ý học tập**: sau khi hoàn thành mỗi bài, quay lại đọc phần tương ứng trong [01-huong-doi-tuong-oop.md](01-huong-doi-tuong-oop.md) và tự hỏi "code mình viết có khớp với nguyên lý đã mô tả không?" — đây là cách tốt nhất để biến lý thuyết thành hiểu biết thực sự.

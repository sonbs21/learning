# Tuần 5: Exception Handling

> Tài liệu tổng hợp kiến thức mục 5 trong lộ trình `java-core-roadmap.md`.

---

## 1. Phân cấp Exception: Throwable → Error / Exception → Checked / Unchecked

```
                    Throwable
                   /          \
              Error            Exception
           (nghiêm trọng,      /          \
            không nên catch) Checked     RuntimeException
                              Exception   (Unchecked)
                                  │             │
                        IOException,    NullPointerException,
                        SQLException,   ArrayIndexOutOfBounds,
                        FileNotFound-   ArithmeticException,
                        Exception...    ClassCastException,
                                        IllegalArgumentException...
```

### Error
- Đại diện cho lỗi **nghiêm trọng ở tầng hệ thống/JVM** mà chương trình thường **không thể** (và không nên cố) phục hồi: `OutOfMemoryError`, `StackOverflowError`, `NoClassDefFoundError`.
- Không bắt buộc (và cũng không nên) `catch` — nếu Error xảy ra, cách xử lý đúng thường là để chương trình dừng lại.

### Exception
Đại diện cho các tình huống **bất thường nhưng có thể xử lý được** trong logic chương trình. Chia làm 2 loại:

| | Checked Exception | Unchecked Exception (RuntimeException) |
|---|---|---|
| Kiểm tra lúc biên dịch | **Có** — compiler bắt buộc phải `catch` hoặc khai báo `throws` | **Không** — compiler không bắt buộc xử lý |
| Đại diện cho | Lỗi có thể lường trước, thường đến từ **môi trường bên ngoài** (I/O, network, database) mà logic đúng vẫn có thể gặp phải | Lỗi thường do **lập trình sai** (bug), lẽ ra có thể tránh được bằng cách kiểm tra trước |
| Ví dụ | `IOException`, `SQLException`, `FileNotFoundException`, `InterruptedException` | `NullPointerException`, `ArrayIndexOutOfBoundsException`, `ArithmeticException`, `ClassCastException`, `IllegalArgumentException` |
| Vị trí trong cây phân cấp | Extends `Exception` trực tiếp (không qua `RuntimeException`) | Extends `RuntimeException` (mà `RuntimeException` cũng extends `Exception`) |

```java
// Checked - bắt buộc xử lý (catch hoặc khai báo throws), nếu không sẽ LỖI BIÊN DỊCH
void readFile(String path) throws IOException {
    FileReader reader = new FileReader(path); // có thể ném FileNotFoundException (checked)
}

// Unchecked - không bắt buộc, compiler không kiểm tra
void divide(int a, int b) {
    int result = a / b; // có thể ném ArithmeticException lúc runtime nếu b == 0, không cần khai báo
}
```

> **Ghi nhớ**: `RuntimeException` **cũng là** `Exception` (về mặt kế thừa), nhưng được compiler đối xử đặc biệt — không bắt buộc catch/throws. Đây là điểm hay gây nhầm lẫn: "Unchecked" không có nghĩa là "không phải Exception", mà nghĩa là "không bị compiler kiểm tra bắt buộc".

---

## 2. try-catch-finally, thứ tự thực thi, finally với return

### Cú pháp cơ bản
```java
try {
    // code có thể ném exception
} catch (SpecificException e) {
    // xử lý exception cụ thể
} catch (Exception e) {
    // xử lý các exception còn lại (bắt tổng quát hơn PHẢI đặt SAU)
} finally {
    // luôn luôn chạy - dù có exception hay không, dù có return hay không
}
```

### Thứ tự thực thi
```java
static String test() {
    try {
        System.out.println("1. try");
        throw new RuntimeException("error");
    } catch (RuntimeException e) {
        System.out.println("2. catch");
        return "from catch";
    } finally {
        System.out.println("3. finally"); // LUÔN chạy, kể cả sau khi catch đã return
    }
}
// Output: 1. try -> 2. catch -> 3. finally -> (rồi mới thực sự return "from catch")
```

### `finally` với `return` — điểm cực kỳ hay bị hỏi trong phỏng vấn

**Trường hợp 1: `finally` có `return` sẽ GHI ĐÈ giá trị return của `try`/`catch`:**
```java
static int test1() {
    try {
        return 1;
    } finally {
        return 2; // GHI ĐÈ - method này LUÔN trả về 2, giá trị "1" bị vứt bỏ hoàn toàn
    }
}
```
> **Best practice**: không bao giờ nên viết `return` bên trong `finally` — đây được coi là code smell nghiêm trọng, dễ gây bug khó hiểu.

**Trường hợp 2: `finally` KHÔNG có `return`, nhưng sửa biến — giá trị trả về vẫn là giá trị đã được "chốt" tại thời điểm `return` chạy trong `try`, KHÔNG bị ảnh hưởng bởi thay đổi sau đó trong `finally` (với kiểu nguyên thủy):**
```java
static int test2() {
    int x = 1;
    try {
        return x; // giá trị "1" được TÍNH và LƯU TẠM ngay tại đây (trước khi vào finally)
    } finally {
        x = 2; // đổi x thành 2, nhưng KHÔNG ảnh hưởng tới giá trị đã lưu tạm ở trên
    }
}
// test2() trả về 1, KHÔNG PHẢI 2!
```

**Trường hợp 3: nếu return 1 object (reference type) và finally sửa field bên trong object đó → THAY ĐỔI ĐƯỢC phản ánh (vì return chỉ lưu tạm địa chỉ reference, không copy toàn bộ object):**
```java
static StringBuilder test3() {
    StringBuilder sb = new StringBuilder("A");
    try {
        return sb;
    } finally {
        sb.append("B"); // sửa NỘI DUNG của object mà reference đang trỏ tới
    }
}
// test3() trả về StringBuilder chứa "AB" - vì chỉ địa chỉ được "chốt", không phải nội dung object
```

**Trường hợp 4: exception bị ném trong `try`, nhưng `finally` có `return` (không ném) → exception bị "nuốt mất" hoàn toàn, không bao giờ được ném ra ngoài:**
```java
static int test4() {
    try {
        throw new RuntimeException("Original error");
    } finally {
        return 99; // exception "Original error" bị NUỐT hoàn toàn, không bao giờ đến được nơi gọi
    }
}
```

---

## 3. try-with-resources, AutoCloseable

Trước Java 7, phải tự đóng resource (file, connection...) thủ công trong `finally` — dễ quên hoặc viết sai:

```java
// Cách cũ (trước Java 7) - dài dòng, dễ quên đóng, dễ che khuất exception gốc
FileReader reader = null;
try {
    reader = new FileReader("file.txt");
    // ...
} finally {
    if (reader != null) {
        try { reader.close(); } catch (IOException e) { /* ... */ }
    }
}
```

**Từ Java 7**: `try-with-resources` tự động gọi `close()` khi kết thúc block `try` (dù thành công hay có exception), miễn là resource implements `AutoCloseable` (hoặc `Closeable`).

```java
try (FileReader reader = new FileReader("file.txt");
     BufferedReader br = new BufferedReader(reader)) { // có thể khai báo NHIỀU resource, ngăn cách bằng ;
    String line = br.readLine();
} catch (IOException e) {
    e.printStackTrace();
}
// KHÔNG cần finally để đóng - reader và br tự động được close() theo thứ tự NGƯỢC với lúc khai báo
```

### Tự định nghĩa resource với AutoCloseable
```java
class MyResource implements AutoCloseable {
    MyResource() { System.out.println("Mở resource"); }

    void use() { System.out.println("Đang dùng resource"); }

    @Override
    public void close() { // không cần khai báo throws nếu không có checked exception
        System.out.println("Đóng resource");
    }
}

try (MyResource res = new MyResource()) {
    res.use();
}
// Output: Mở resource -> Đang dùng resource -> Đóng resource (tự động)
```

### Thứ tự đóng resource và "suppressed exception"
- Các resource được đóng theo thứ tự **ngược lại** với thứ tự khai báo (resource khai báo sau cùng được đóng trước tiên).
- Nếu cả code trong `try` VÀ `close()` đều ném exception, exception từ `try` được coi là **exception chính**, còn exception từ `close()` bị "đàn áp" (**suppressed**) — vẫn được lưu lại và có thể truy xuất qua `getSuppressed()`, không bị mất hoàn toàn như cách cũ dùng `finally`.

---

## 4. Multi-catch (`catch (A | B e)`)

Từ Java 7, có thể gộp nhiều loại exception không liên quan (không cùng nhánh kế thừa) vào 1 block `catch` nếu cách xử lý giống hệt nhau — tránh lặp code.

```java
// Trước Java 7 - phải viết lặp lại logic xử lý
try {
    // ...
} catch (IOException e) {
    log.error("Error occurred", e);
    throw new ServiceException(e);
} catch (SQLException e) {
    log.error("Error occurred", e); // LẶP LẠI code giống hệt
    throw new ServiceException(e);
}

// Từ Java 7 - multi-catch, gộp lại
try {
    // ...
} catch (IOException | SQLException e) {
    log.error("Error occurred", e);
    throw new ServiceException(e);
}
```

**Ràng buộc quan trọng**: các exception trong multi-catch **không được có quan hệ cha-con** với nhau (VD: không thể viết `catch (IOException | FileNotFoundException e)` vì `FileNotFoundException` đã là subclass của `IOException` — dư thừa, compiler sẽ báo lỗi).

Biến `e` trong multi-catch mặc định có kiểu là **"lowest common ancestor"** (superclass chung gần nhất) của các exception, và **implicitly `final`** — không thể gán lại giá trị cho `e` bên trong block catch.

---

## 5. Custom Exception (Checked vs Unchecked)

### Tự định nghĩa Checked Exception
```java
class InsufficientFundsException extends Exception { // extends Exception -> checked
    private final double shortfall;

    public InsufficientFundsException(String message, double shortfall) {
        super(message);
        this.shortfall = shortfall;
    }

    public double getShortfall() { return shortfall; }
}

class Account {
    double balance;
    void withdraw(double amount) throws InsufficientFundsException { // bắt buộc khai báo throws
        if (amount > balance) {
            throw new InsufficientFundsException("Not enough balance", amount - balance);
        }
        balance -= amount;
    }
}
```

### Tự định nghĩa Unchecked Exception
```java
class InvalidOrderException extends RuntimeException { // extends RuntimeException -> unchecked
    public InvalidOrderException(String message) {
        super(message);
    }
}

class OrderService {
    void placeOrder(int quantity) {
        if (quantity <= 0) {
            throw new InvalidOrderException("Quantity must be positive"); // không cần khai báo throws
        }
    }
}
```

### Khi nào nên tạo Checked, khi nào nên tạo Unchecked?
- **Checked**: khi caller **có khả năng thực sự phục hồi** được từ lỗi này (VD: thử kết nối lại, yêu cầu người dùng nhập lại), và muốn **ép buộc** caller phải xử lý (compiler sẽ nhắc).
- **Unchecked**: khi lỗi thường là **bug lập trình** (tham số sai, vi phạm precondition) mà caller không thể "phục hồi" một cách có ý nghĩa — chỉ có thể sửa code. Xu hướng hiện đại (VD: Spring Framework) nghiêng về dùng unchecked exception nhiều hơn để tránh "checked exception hell" (phải viết `throws` dây chuyền qua nhiều tầng method).

---

## 6. Exception Chaining (`getCause()`)

Khi bắt 1 exception ở tầng thấp và muốn ném lại 1 exception khác ở tầng cao hơn (phù hợp ngữ cảnh nghiệp vụ hơn), nên **giữ lại exception gốc** làm "cause" thay vì vứt bỏ thông tin gốc — giúp debug dễ dàng hơn nhiều.

```java
class DataAccessException extends RuntimeException {
    public DataAccessException(String message, Throwable cause) {
        super(message, cause); // truyền cause vào constructor của Throwable
    }
}

void queryDatabase() {
    try {
        // giả sử ném SQLException ở đây
        throw new SQLException("Connection timeout");
    } catch (SQLException e) {
        // Ném lại 1 exception khác phù hợp tầng nghiệp vụ hơn, NHƯNG giữ lại nguyên nhân gốc
        throw new DataAccessException("Failed to fetch user data", e);
    }
}

// Ở tầng gọi, có thể truy vết ngược lại nguyên nhân gốc:
try {
    queryDatabase();
} catch (DataAccessException e) {
    System.out.println(e.getMessage());        // "Failed to fetch user data"
    System.out.println(e.getCause());           // "java.sql.SQLException: Connection timeout"
    e.printStackTrace();                        // in ra CẢ 2 stack trace, nối bằng "Caused by:"
}
```

> **Bẫy thường gặp**: nếu chỉ ném `throw new DataAccessException("Failed to fetch user data")` mà **không truyền `e` vào** — thông tin exception gốc (dòng code gây lỗi thực sự, loại lỗi ban đầu) sẽ **bị mất vĩnh viễn**, khiến việc debug production cực kỳ khó khăn.

---

## 7. Best Practice: Không nuốt Exception, Không dùng Exception cho Control Flow

### Không "nuốt" (swallow) exception
```java
// XẤU - "nuốt" exception, không log, không xử lý gì cả -> lỗi biến mất trong im lặng
try {
    riskyOperation();
} catch (Exception e) {
    // để trống hoàn toàn - CỰC KỲ NGUY HIỂM, không ai biết lỗi đã xảy ra
}

// XẤU - chỉ in ra console, không có ngữ cảnh, dễ bị bỏ qua trong production (log tập trung không thấy)
try {
    riskyOperation();
} catch (Exception e) {
    e.printStackTrace(); // chỉ in ra stderr, không được ghi vào hệ thống log tập trung
}

// TỐT - log đầy đủ ngữ cảnh bằng logger thực sự, và quyết định rõ ràng: rethrow, xử lý, hay fallback có ý nghĩa
try {
    riskyOperation();
} catch (SpecificException e) {
    log.error("riskyOperation failed for user {}", userId, e);
    throw new ServiceException("Unable to complete operation", e); // hoặc xử lý fallback có chủ đích
}
```

### Không dùng Exception để điều khiển luồng chương trình (control flow)
Exception có chi phí hiệu năng đáng kể (tạo stack trace tốn kém) và làm code khó đọc nếu dùng cho logic bình thường thay vì if-else.

```java
// XẤU - dùng exception để kiểm tra "có tồn tại hay không" thay vì if-else đơn giản
int findIndex(int[] arr, int target) {
    try {
        for (int i = 0; ; i++) { // cố tình để vòng lặp chạy đến khi lỗi
            if (arr[i] == target) return i;
        }
    } catch (ArrayIndexOutOfBoundsException e) {
        return -1; // dùng exception thay cho điều kiện dừng vòng lặp bình thường
    }
}

// TỐT - kiểm tra điều kiện tường minh bằng if/vòng lặp thông thường
int findIndex(int[] arr, int target) {
    for (int i = 0; i < arr.length; i++) {
        if (arr[i] == target) return i;
    }
    return -1;
}
```

### Các nguyên tắc khác
- **Chỉ catch những exception mà bạn thực sự biết cách xử lý** — đừng `catch (Exception e)` chung chung rồi bỏ qua mọi loại lỗi khác nhau bằng cùng 1 cách.
- **Ném exception càng sớm càng tốt, xử lý càng muộn càng tốt** ("throw early, catch late") — validate input ngay khi phát hiện sai, nhưng chỉ xử lý (catch) ở tầng có đủ ngữ cảnh để quyết định phải làm gì tiếp theo.
- **Không dùng exception cho luồng nghiệp vụ bình thường** (VD: "không tìm thấy" nên trả về `Optional.empty()` hoặc `null` có kiểm soát, không nên là 1 exception nếu đó là tình huống thường xuyên xảy ra trong logic bình thường).

---

## Câu hỏi tự kiểm tra
1. Phân biệt `Error` và `Exception` — vì sao không nên `catch (Error e)` trong đa số trường hợp?
2. Checked và Unchecked Exception khác nhau ở điểm nào về mặt compiler? Cho ví dụ mỗi loại.
3. Giải thích chi tiết vì sao `finally` có `return` sẽ ghi đè giá trị return của `try`/`catch`. Đây có phải là best practice không?
4. Nếu `return` 1 biến kiểu nguyên thủy (`int`) trong `try`, sau đó `finally` thay đổi giá trị biến đó — giá trị trả về cuối cùng là gì? Vì sao?
5. `try-with-resources` giải quyết vấn đề gì so với cách đóng resource thủ công trong `finally` trước Java 7?
6. Multi-catch (`catch (A | B e)`) có ràng buộc gì về mối quan hệ giữa các exception được gộp?
7. Exception chaining (`getCause()`) giải quyết vấn đề gì? Điều gì xảy ra nếu quên truyền exception gốc vào khi ném lại 1 exception mới?

## Bài tập luyện tập
- Viết 4 method minh họa 4 trường hợp `finally` với `return` đã nêu ở mục 2, chạy thử và kiểm chứng kết quả in ra.
- Viết 1 class tự định nghĩa `AutoCloseable`, dùng trong `try-with-resources` với 2-3 resource lồng nhau, quan sát thứ tự đóng.
- Viết 1 custom checked exception (`InsufficientFundsException`) và 1 custom unchecked exception (`InvalidOrderException`), áp dụng vào 1 class `BankAccount` đơn giản.
- Viết ví dụ minh họa exception chaining: bắt `SQLException` giả lập, ném lại `DataAccessException` có giữ `cause`, in ra `printStackTrace()` để thấy "Caused by:".
- Refactor 1 đoạn code dùng exception để control flow (giống ví dụ xấu ở mục 7) thành code dùng if/else thông thường, so sánh hiệu năng bằng 1 vòng lặp benchmark lớn.

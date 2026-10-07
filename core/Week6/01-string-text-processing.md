# Tuần 6: String & Text Processing

> Tài liệu tổng hợp kiến thức mục 6 trong lộ trình `java-core-roadmap.md`.

---

## 1. String Immutability, String Pool (Constant Pool)

### String là bất biến (immutable)
Một khi `String` được tạo ra, **nội dung của nó không bao giờ thay đổi**. Mọi thao tác "tưởng như sửa" (`concat`, `replace`, `toUpperCase`...) thực chất đều **tạo ra 1 object `String` mới**, object cũ không đổi.

```java
String s = "Hello";
s.concat(" World"); // tạo ra object MỚI "Hello World", nhưng KHÔNG gán lại cho s
System.out.println(s); // vẫn in ra "Hello" - s không đổi!

s = s.concat(" World"); // phải gán lại thì s mới trỏ tới object mới
System.out.println(s); // "Hello World"
```

**Vì sao Java thiết kế String bất biến?**
- **An toàn khi dùng làm key trong `HashMap`/`HashSet`**: nếu String có thể đổi nội dung sau khi đã tính `hashCode()` và đưa vào bucket, nó sẽ "thất lạc" trong bảng hash (không tìm lại được).
- **Bảo mật**: String thường dùng cho username, password, URL, tên file, tham số kết nối database — nếu String có thể bị 1 phần code khác âm thầm sửa đổi sau khi đã được validate, sẽ mở ra lỗ hổng bảo mật nghiêm trọng.
- **Thread-safe tự nhiên**: object bất biến chia sẻ được giữa nhiều thread mà không cần đồng bộ hóa (synchronization) — không có race condition vì không ai sửa được nó.
- **Cho phép String Pool hoạt động an toàn** (xem mục dưới) — vì nhiều biến có thể cùng trỏ tới 1 object String dùng chung mà không sợ 1 nơi sửa làm ảnh hưởng tới nơi khác.

### String Pool (String Constant Pool)
Là 1 vùng nhớ đặc biệt nằm trong **Heap** (từ Java 7 trở đi; trước đó nằm trong PermGen — xem lại [Week1/02-kien-truc-jdk-jre-jvm.md](../Week1/02-kien-truc-jdk-jre-jvm.md)), dùng để **lưu trữ và tái sử dụng** các String literal, tránh tạo object trùng lặp lãng phí bộ nhớ.

```java
String s1 = "Hello"; // literal -> JVM kiểm tra String Pool, chưa có -> tạo mới và đưa vào pool
String s2 = "Hello"; // literal -> JVM kiểm tra String Pool, ĐÃ có -> tái sử dụng luôn, không tạo mới
System.out.println(s1 == s2); // true - CÙNG 1 object trong pool

String s3 = new String("Hello"); // dùng "new" -> LUÔN LUÔN tạo object MỚI trên Heap (ngoài pool)
System.out.println(s1 == s3); // false - khác object (dù cùng nội dung)
System.out.println(s1.equals(s3)); // true - nội dung giống nhau

String s4 = s3.intern(); // intern() -> trả về reference của bản trong pool (tạo mới nếu chưa có)
System.out.println(s1 == s4); // true - giờ cùng trỏ vào bản trong pool
```

```
Minh họa bộ nhớ:
Heap
 ├── String Pool (vùng đặc biệt trong Heap từ Java 7+)
 │      └── "Hello"  <───────┐
 │                            │
 └── Object thường            │
        └── new String("Hello") ──> nội dung "Hello" (object riêng, KHÔNG trong pool)

s1 ──────────────────────────┘
s2 ──────────────────────────┘  (cùng trỏ vào pool)
s3 ───> object riêng ngoài pool
```

> **Quy tắc ghi nhớ**: `==` so sánh **địa chỉ reference**, `.equals()` so sánh **nội dung**. Với String, luôn dùng `.equals()` để so sánh giá trị — dùng `==` là lỗi rất phổ biến của người mới học Java.

---

## 2. String vs StringBuilder vs StringBuffer (Thread-safety)

| | String | StringBuilder | StringBuffer |
|---|---|---|---|
| Mutable? | Không (immutable) | Có (mutable) | Có (mutable) |
| Thread-safe | Có (nhờ immutable, không ai sửa được nên an toàn tự nhiên) | **Không** | **Có** (mọi method quan trọng đều `synchronized`) |
| Hiệu năng | Chậm khi nối chuỗi nhiều lần (mỗi lần `+` tạo object mới) | Nhanh nhất (đơn luồng) | Chậm hơn StringBuilder (overhead của lock) |
| Ra đời | Từ đầu | Java 5 (thêm vào để thay StringBuffer cho trường hợp đơn luồng) | Java 1.0 (cũ hơn) |
| Khi nào dùng | Chuỗi cố định, ít thao tác nối | Nối chuỗi nhiều trong 1 thread (vòng lặp, xử lý dữ liệu) | Nối chuỗi nhiều, được chia sẻ giữa nhiều thread |

### Vì sao nối String bằng `+` trong vòng lặp lại chậm?
```java
// XẤU - mỗi lần += tạo ra 1 object String MỚI hoàn toàn (vì String immutable)
String result = "";
for (int i = 0; i < 10000; i++) {
    result += i; // tạo ra object mới, copy toàn bộ nội dung cũ + thêm "i" -> O(n) mỗi lần -> O(n^2) tổng
}

// TỐT - StringBuilder dùng 1 buffer duy nhất, chỉ append vào cuối -> O(1) amortized mỗi lần
StringBuilder sb = new StringBuilder();
for (int i = 0; i < 10000; i++) {
    sb.append(i);
}
String result = sb.toString();
```
> Trình biên dịch Java **tự động** chuyển `String + String` đơn giản (không trong vòng lặp) thành `StringBuilder` ngầm định, nhưng **KHÔNG** tối ưu được nếu nối chuỗi lặp lại nhiều lần trong vòng lặp (mỗi lần lặp lại tạo `StringBuilder` mới) — vì vậy vẫn nên tự dùng `StringBuilder` tường minh khi nối chuỗi trong loop.

```java
StringBuffer sb = new StringBuffer(); // thread-safe, dùng khi nhiều thread cùng ghi vào 1 buffer
sb.append("Hello").append(" ").append("World"); // method chaining (mỗi method trả về chính đối tượng)
```

---

## 3. Các Method Quan Trọng: `substring`, `split`, `replace`, `format`, `intern()`

### `substring(beginIndex, endIndex)`
```java
String s = "Hello World";
s.substring(6);       // "World" (từ index 6 đến hết)
s.substring(0, 5);    // "Hello" (từ index 0 đến TRƯỚC index 5, endIndex exclusive)
```
> **Từ Java 7 trở đi**: `substring()` tạo ra 1 mảng `char[]`/`byte[]` MỚI (copy dữ liệu), **không** còn chia sẻ mảng gốc với String cha như trước Java 7 (thay đổi này để tránh memory leak: trước đó, giữ 1 substring rất ngắn vẫn khiến toàn bộ mảng ký tự gốc — có thể rất lớn — không được GC thu hồi).

### `split(regex)`
```java
"a,b,,c".split(",");         // ["a", "b", "", "c"]
"a,b,,c".split(",", -1);      // ["a", "b", "", "c"] (limit=-1: giữ cả chuỗi rỗng ở cuối nếu có)
"  Hello   World  ".trim().split("\\s+"); // ["Hello", "World"] - tách theo 1 hoặc nhiều khoảng trắng
```
> Tham số của `split()` là **regex** — các ký tự đặc biệt của regex (`.`, `|`, `*`,...) cần escape đúng cách (VD: tách theo dấu chấm phải viết `split("\\.")`, không phải `split(".")`).

### `replace()` vs `replaceAll()` vs `replaceFirst()`
```java
"a.b.c".replace(".", "-");        // "a-b-c" - CharSequence thường (không phải regex), thay TẤT CẢ
"a.b.c".replaceAll("\\.", "-");   // "a-b-c" - regex, thay TẤT CẢ
"a.b.c".replaceFirst("\\.", "-"); // "a-b.c" - regex, chỉ thay lần đầu tiên
```
> `replace()` nhận `CharSequence` (không phải regex), còn `replaceAll()`/`replaceFirst()` nhận **regex pattern**.

### `String.format()` — tạo chuỗi có định dạng
```java
String.format("Name: %s, Age: %d", "An", 25);      // "Name: An, Age: 25"
String.format("Price: %.2f", 19.999);                // "Price: 20.00" (làm tròn 2 chữ số thập phân)
String.format("%-10s|", "abc");                       // "abc       |" (căn trái, độ rộng 10)
String.format("%05d", 42);                            // "00042" (đệm số 0, độ rộng 5)
```

### `intern()`
Trả về reference tới bản trong **String Pool** — nếu nội dung đã có trong pool thì trả về bản đó, nếu chưa có thì thêm vào pool rồi trả về (xem lại ví dụ chi tiết ở mục 1).

---

## 4. StringBuilder Internal — Mảng char/byte, Capacity Growth

### Kiến trúc bên trong
```java
public abstract class AbstractStringBuilder {
    byte[] value;       // Java 9+: dùng byte[] với "Compact Strings" (xem bên dưới)
                          // Java 8 trở về trước: dùng char[]
    int count;            // số ký tự thực tế đang dùng (count <= value.length)
}
```

- Tương tự cơ chế **mảng động** của `ArrayList` (xem lại [Week3/02-list-chi-tiet.md](../Week3/02-list-chi-tiet.md)): dữ liệu lưu trong 1 mảng liên tục, `count` là độ dài thực tế, `value.length` là capacity.
- Capacity mặc định ban đầu: `16` ký tự (hoặc `length + 16` nếu khởi tạo từ 1 String có sẵn).

```java
StringBuilder sb = new StringBuilder();       // capacity mặc định = 16
StringBuilder sb2 = new StringBuilder("Hi");  // capacity = "Hi".length() + 16 = 18
StringBuilder sb3 = new StringBuilder(100);   // capacity tùy chỉnh = 100 (nên dùng nếu biết trước kích thước để tránh resize)
```

### Cơ chế grow() khi vượt capacity
```java
// Công thức grow của StringBuilder/StringBuffer: capacity mới = (capacity cũ * 2) + 2
int newCapacity = (oldCapacity << 1) + 2;
value = Arrays.copyOf(value, newCapacity); // copy toàn bộ dữ liệu cũ sang mảng mới lớn hơn
```
- Giống `ArrayList`, đây là **amortized O(1)** cho mỗi lần `append()`, dù bản thân thao tác resize là O(n).
- Nếu biết trước độ dài chuỗi cần xây dựng, nên dùng `new StringBuilder(expectedCapacity)` để tránh resize nhiều lần không cần thiết.

### Compact Strings (từ Java 9+)
- Trước Java 9: `String`/`StringBuilder` luôn dùng `char[]` (mỗi ký tự chiếm **2 byte**, kể cả ký tự ASCII chỉ cần 1 byte).
- Từ Java 9: dùng `byte[]` kèm 1 cờ (flag) đánh dấu encoding là **Latin-1** (1 byte/ký tự, dùng khi toàn bộ chuỗi chỉ chứa ký tự Latin-1) hoặc **UTF-16** (2 byte/ký tự, dùng khi có ký tự ngoài Latin-1, VD: tiếng Việt có dấu, emoji, chữ Hán...) → tiết kiệm đáng kể bộ nhớ cho phần lớn chuỗi thông thường (chủ yếu là ASCII/Latin).

---

## 5. Regular Expression (Pattern, Matcher)

```java
import java.util.regex.Pattern;
import java.util.regex.Matcher;

Pattern pattern = Pattern.compile("\\d+"); // biên dịch regex 1 lần, có thể tái sử dụng nhiều lần
Matcher matcher = pattern.matcher("abc123def456");

while (matcher.find()) { // tìm TẤT CẢ các đoạn khớp trong chuỗi
    System.out.println(matcher.group()); // in ra: "123", rồi "456"
}
```

### Các method thường dùng
```java
"abc123".matches("[a-z]+\\d+");        // true - toàn bộ chuỗi phải khớp hoàn toàn với pattern
Pattern.matches("\\d+", "123");         // true - cách gọi tĩnh tương đương

Matcher m = Pattern.compile("(\\w+)@(\\w+)\\.com").matcher("user@example.com");
if (m.matches()) {
    m.group(0); // "user@example.com" (toàn bộ match)
    m.group(1); // "user" (group thứ 1, trong ngoặc đơn đầu tiên)
    m.group(2); // "example" (group thứ 2)
}
```

### Vì sao nên dùng `Pattern.compile()` thay vì `String.matches()` khi dùng nhiều lần?
```java
// XẤU nếu gọi trong vòng lặp - "\\d+" bị biên dịch lại (compile regex) MỖI LẦN gọi -> tốn hiệu năng
for (String s : hugeList) {
    if (s.matches("\\d+")) { ... }
}

// TỐT - biên dịch pattern 1 lần duy nhất, tái sử dụng nhiều lần
Pattern digitPattern = Pattern.compile("\\d+");
for (String s : hugeList) {
    if (digitPattern.matcher(s).matches()) { ... }
}
```

### Một số ký tự regex thông dụng
| Ký hiệu | Ý nghĩa |
|---|---|
| `\d` | 1 chữ số (0-9) |
| `\w` | 1 ký tự chữ/số/gạch dưới |
| `\s` | 1 khoảng trắng |
| `+` | 1 hoặc nhiều |
| `*` | 0 hoặc nhiều |
| `?` | 0 hoặc 1 |
| `{n,m}` | từ n đến m lần |
| `^`, `$` | đầu chuỗi, cuối chuỗi |
| `[abc]` | 1 trong các ký tự a, b, c |
| `(...)` | nhóm (group), có thể lấy ra bằng `group(n)` |

---

## 6. Text Block (Java 15+)

Cho phép viết chuỗi nhiều dòng mà không cần nối `+` hoặc escape `\n` thủ công — dễ đọc hơn nhiều khi viết JSON, SQL, HTML mẫu trong code.

```java
// Trước Java 15 - dài dòng, khó đọc
String json = "{\n" +
              "  \"name\": \"An\",\n" +
              "  \"age\": 25\n" +
              "}";

// Từ Java 15 - Text Block, dùng 3 dấu ngoặc kép """
String json = """
        {
          "name": "An",
          "age": 25
        }
        """;
```

### Quy tắc quan trọng của Text Block
- Bắt đầu bằng `"""` theo sau là xuống dòng ngay (nội dung dòng đầu tiên không được viết cùng dòng với `"""` mở).
- **Indentation (thụt lề) tự động bị loại bỏ** dựa trên dòng có thụt lề ít nhất (thường là dòng chứa `"""` đóng) — giúp code trong IDE vẫn thụt lề đẹp mà không ảnh hưởng tới nội dung chuỗi thực tế.
- Không cần escape dấu `"` đơn lẻ, nhưng vẫn cần escape nếu có `"""` liên tiếp xuất hiện trong nội dung.
- Hỗ trợ các escape thông thường (`\n`, `\t`...) và thêm `\` cuối dòng để nối dòng mà không xuống dòng thực sự trong chuỗi kết quả.

```java
String sql = """
        SELECT id, name
        FROM users
        WHERE age > 18
        """;

String noNewLineAtEnd = """
        Dòng này nối với \
        dòng này thành 1 dòng duy nhất""";
```

---

## Câu hỏi tự kiểm tra
1. Vì sao Java thiết kế `String` là immutable? Nêu ít nhất 2 lý do.
2. String Pool nằm ở đâu trong bộ nhớ JVM (trước và sau Java 7)? Vì sao `new String("abc") == "abc"` luôn trả về `false`?
3. Trong 3 lớp String/StringBuilder/StringBuffer, lớp nào thread-safe và vì sao? Khi nào nên chọn `StringBuffer` thay vì `StringBuilder`?
4. Vì sao nối chuỗi bằng `+=` trong vòng lặp lại có độ phức tạp O(n²) tổng thể, trong khi dùng `StringBuilder.append()` chỉ là O(n)?
5. `replace()` và `replaceAll()` khác nhau ở điểm nào? Cho ví dụ minh họa sự khác biệt khi dùng ký tự đặc biệt của regex.
6. Compact Strings (Java 9+) tối ưu bộ nhớ String bằng cách nào?
7. Text Block xử lý thụt lề (indentation) như thế nào để không làm hỏng nội dung chuỗi thực tế?

## Bài tập luyện tập
- Viết chương trình chứng minh String Pool: so sánh `==` giữa string literal, `new String()`, và `.intern()` trong nhiều tình huống khác nhau.
- Viết benchmark so sánh thời gian nối 50,000 chuỗi bằng `+=` so với `StringBuilder.append()`.
- Viết chương trình dùng `Pattern`/`Matcher` để trích xuất tất cả email hợp lệ từ 1 đoạn văn bản lớn.
- Viết hàm kiểm tra 1 chuỗi có phải là palindrome hay không (bỏ qua khoảng trắng, không phân biệt hoa/thường), dùng `StringBuilder.reverse()`.
- Dùng Text Block viết 1 chuỗi JSON mẫu và 1 câu lệnh SQL multi-line, so sánh độ dễ đọc với cách viết nối chuỗi truyền thống.

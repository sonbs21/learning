# Tuần 7: I/O & NIO

> Tài liệu tổng hợp kiến thức mục 7 trong lộ trình `java-core-roadmap.md`.

---

## 1. InputStream/OutputStream (Byte Stream), Reader/Writer (Character Stream)

Java I/O truyền thống (`java.io`, từ JDK 1.0) chia làm 2 họ song song, tùy loại dữ liệu:

```
Xử lý BYTE (nhị phân - ảnh, video, file bất kỳ)     Xử lý KÝ TỰ (text - có encoding)
        │                                                   │
   InputStream (đọc)          OutputStream (ghi)      Reader (đọc)         Writer (ghi)
        │                          │                       │                    │
  FileInputStream          FileOutputStream         FileReader           FileWriter
  ByteArrayInputStream     ByteArrayOutputStream     StringReader         StringWriter
  BufferedInputStream      BufferedOutputStream      BufferedReader       BufferedWriter
  ObjectInputStream        ObjectOutputStream         InputStreamReader   OutputStreamWriter
                                                       (cầu nối byte<->char)
```

### Byte Stream — `InputStream`/`OutputStream`
Làm việc trực tiếp với **byte** (8-bit) — dùng cho dữ liệu nhị phân bất kỳ (ảnh, video, file thực thi, file nén...), không quan tâm encoding.

```java
try (InputStream in = new FileInputStream("image.png");
     OutputStream out = new FileOutputStream("copy.png")) {
    int byteData;
    while ((byteData = in.read()) != -1) { // đọc từng byte, trả về -1 khi hết file
        out.write(byteData);
    }
}
```

### Character Stream — `Reader`/`Writer`
Làm việc với **ký tự** (16-bit `char`, dựa trên Unicode) — dùng cho dữ liệu văn bản (text), tự động xử lý encoding (chuyển đổi giữa byte và ký tự theo charset, mặc định thường là UTF-8 từ Java 18+).

```java
try (Reader reader = new FileReader("data.txt");
     Writer writer = new FileWriter("output.txt")) {
    int charData;
    while ((charData = reader.read()) != -1) { // đọc từng ký tự
        writer.write(charData);
    }
}
```

### Cầu nối giữa 2 họ: `InputStreamReader` / `OutputStreamWriter`
Cho phép chuyển đổi từ byte stream sang character stream với 1 charset (encoding) cụ thể — hữu ích khi cần đọc file text với encoding không phải mặc định.

```java
try (Reader reader = new InputStreamReader(new FileInputStream("data.txt"), StandardCharsets.UTF_8)) {
    // đọc file text với encoding UTF-8 tường minh, tránh phụ thuộc vào default charset của hệ điều hành
}
```

> **Nguyên tắc chọn**: dữ liệu là **văn bản con người đọc được** → dùng `Reader`/`Writer`. Dữ liệu **nhị phân** (ảnh, file bất kỳ, network protocol) → dùng `InputStream`/`OutputStream`.

---

## 2. BufferedReader/BufferedWriter, FileReader/FileWriter

### Vì sao cần Buffered?
Đọc/ghi trực tiếp từng byte/ký tự một (`FileReader.read()`) rất chậm vì mỗi lần gọi là **1 lời gọi hệ thống (system call)** xuống tầng OS/disk I/O — chi phí rất cao nếu lặp lại hàng nghìn lần.

`BufferedReader`/`BufferedWriter` bọc (wrap) quanh 1 Reader/Writer khác, thêm **1 vùng đệm (buffer) trong bộ nhớ** — đọc/ghi 1 lượng lớn dữ liệu (VD: 8KB) trong 1 lần system call, sau đó phục vụ các lời gọi `read()`/`write()` tiếp theo từ buffer trong RAM (rất nhanh) cho đến khi buffer cạn mới thực hiện I/O thật tiếp theo.

```java
// KHÔNG buffer - chậm, đọc từng ký tự 1 = rất nhiều system call
try (FileReader fr = new FileReader("large.txt")) {
    int c;
    while ((c = fr.read()) != -1) { /* ... */ }
}

// CÓ buffer - nhanh hơn nhiều lần, và có thêm method tiện lợi readLine()
try (BufferedReader br = new BufferedReader(new FileReader("large.txt"))) {
    String line;
    while ((line = br.readLine()) != null) { // đọc theo TỪNG DÒNG, rất tiện cho xử lý text
        System.out.println(line);
    }
}

try (BufferedWriter bw = new BufferedWriter(new FileWriter("output.txt"))) {
    bw.write("Hello");
    bw.newLine(); // xuống dòng (tương thích đa nền tảng, khác \n cứng)
    bw.write("World");
}
```

> Đây là ví dụ kinh điển của **Decorator Pattern** trong JDK — `BufferedReader` "bọc" quanh 1 `Reader` bất kỳ để thêm tính năng (buffering) mà không cần sửa class gốc.

---

## 3. Serialization: Serializable, transient, serialVersionUID

### Serialization là gì?
Quá trình chuyển đổi 1 object trong bộ nhớ (Heap) thành 1 **chuỗi byte** để có thể lưu xuống file, gửi qua network, hoặc lưu vào database — và **Deserialization** là quá trình ngược lại (khôi phục object từ chuỗi byte).

```java
class Person implements Serializable { // phải implement Serializable (marker interface, không có method)
    private static final long serialVersionUID = 1L; // xem giải thích bên dưới

    String name;
    transient String password; // transient -> KHÔNG được serialize, bị bỏ qua khi ghi/đọc

    Person(String name, String password) {
        this.name = name;
        this.password = password;
    }
}

// Serialize - ghi object xuống file
try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream("person.dat"))) {
    oos.writeObject(new Person("An", "secret123"));
}

// Deserialize - đọc object từ file
try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream("person.dat"))) {
    Person p = (Person) ois.readObject();
    System.out.println(p.name);       // "An"
    System.out.println(p.password);   // null - vì "transient" nên KHÔNG được lưu, đọc lại là giá trị mặc định
}
```

### `transient` — loại trừ field khỏi serialization
Dùng cho:
- **Dữ liệu nhạy cảm** (password, token) — không nên lưu ra file/network dưới dạng có thể đọc được.
- **Field không có ý nghĩa khi khôi phục** (VD: cache tạm thời, connection object, `Thread`) — hoặc field thuộc kiểu **không implement `Serializable`** (nếu không đánh dấu `transient`, sẽ ném `NotSerializableException` khi serialize).

### `serialVersionUID` — vì sao quan trọng?
- Là 1 con số (thường khai báo tường minh) dùng để **xác định phiên bản (version)** của class khi deserialize.
- Khi deserialize, JVM so sánh `serialVersionUID` của class hiện tại với giá trị được lưu trong file/byte đã serialize trước đó:
  - **Khớp nhau** → deserialize thành công bình thường.
  - **Không khớp** → ném `InvalidClassException` — thường xảy ra khi class đã bị sửa đổi cấu trúc (thêm/xóa field) sau khi dữ liệu đã được serialize từ phiên bản cũ.
- Nếu **không khai báo tường minh**, JVM tự tính `serialVersionUID` dựa trên chi tiết cấu trúc class (tên field, method, modifier...) — rất dễ thay đổi chỉ với 1 sửa đổi nhỏ trong code (thậm chí đổi compiler), khiến các object đã serialize trước đó **không đọc lại được nữa**.
> **Best practice**: luôn khai báo `serialVersionUID` tường minh để kiểm soát tính tương thích ngược giữa các phiên bản class.

---

## 4. NIO: Path, Files, Paths (Java 7+)

`java.nio.file` (NIO.2, từ Java 7) là API hiện đại hơn để thao tác với **hệ thống file** — thay thế `java.io.File` cũ (vốn có nhiều hạn chế: thông báo lỗi không rõ ràng, thiếu nhiều thao tác hữu ích, không hỗ trợ symbolic link tốt).

### `Path` — đại diện cho đường dẫn file/thư mục
```java
Path path = Paths.get("data", "file.txt");     // tạo Path từ nhiều phần, tự nối bằng separator của OS
Path path2 = Path.of("data/file.txt");          // cách viết mới hơn (Java 11+), tương đương Paths.get()

path.getFileName();     // "file.txt"
path.getParent();       // "data"
path.toAbsolutePath();  // đường dẫn tuyệt đối
path.normalize();       // chuẩn hóa (loại bỏ "..", "." dư thừa)
```

### `Files` — utility class chứa hầu hết thao tác cần thiết trên file
```java
// Đọc toàn bộ nội dung file (phù hợp file nhỏ/vừa, không nên dùng cho file rất lớn)
String content = Files.readString(path);              // Java 11+
List<String> lines = Files.readAllLines(path);          // đọc thành List<String>, mỗi phần tử 1 dòng
byte[] bytes = Files.readAllBytes(path);                 // đọc dạng byte[]

// Ghi file
Files.writeString(path, "Hello World");                  // Java 11+
Files.write(path, List.of("line1", "line2"));

// Các thao tác file/thư mục khác
Files.exists(path);
Files.createDirectory(path);
Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
Files.move(source, target);
Files.delete(path);
Files.isDirectory(path);
Files.size(path); // kích thước file tính bằng byte

// Duyệt qua toàn bộ file trong 1 thư mục (trả về Stream - xử lý lazy, hiệu quả cho thư mục lớn)
try (Stream<Path> stream = Files.walk(path)) {
    stream.filter(Files::isRegularFile)
          .forEach(System.out::println);
}
```

### Ưu điểm của NIO.2 (`Path`/`Files`) so với `java.io.File` cũ
- Method trả về `boolean` mơ hồ (VD: `file.delete()` trả về `false` không rõ lý do) → NIO.2 ném **exception cụ thể** (`NoSuchFileException`, `FileAlreadyExistsException`,...) giúp debug dễ hơn nhiều.
- Hỗ trợ tốt symbolic link, file attributes chi tiết (owner, permissions...).
- Tích hợp với `Stream API` (VD: `Files.lines()`, `Files.walk()`) — xử lý file lớn hiệu quả hơn.

---

## 5. NIO.2 Channel & Buffer (Khái niệm cơ bản)

NIO (New I/O, từ Java 4, khác với NIO.2 file API ở mục 4) giới thiệu mô hình I/O khác biệt căn bản so với I/O truyền thống (`java.io`):

| | I/O truyền thống (`java.io`) | NIO (`java.nio`) |
|---|---|---|
| Hướng dữ liệu | **Stream-oriented** — đọc tuần tự từng byte/ký tự 1 chiều | **Buffer-oriented** — đọc cả khối dữ liệu vào buffer, thao tác qua lại (đọc/ghi/di chuyển con trỏ) trên buffer đó |
| Blocking | **Blocking** — thread gọi `read()` bị chặn (đứng chờ) cho đến khi có dữ liệu | Có thể **Non-blocking** — thread có thể hỏi "có dữ liệu chưa" mà không bị chặn, làm việc khác trong lúc chờ |
| Đối tượng trung tâm | Stream (`InputStream`, `Reader`...) | `Channel` + `Buffer` |

### Channel — kênh giao tiếp 2 chiều
Đại diện cho 1 kết nối tới thực thể I/O (file, socket...), có thể **vừa đọc vừa ghi** trên cùng 1 channel (khác với Stream truyền thống — 1 chiều, phải tách riêng InputStream và OutputStream).

```java
try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
    ByteBuffer buffer = ByteBuffer.allocate(1024); // tạo buffer 1024 byte
    int bytesRead = channel.read(buffer); // đọc dữ liệu từ channel VÀO buffer

    buffer.flip(); // chuyển buffer từ chế độ "ghi" sang "đọc" (đảo vị trí limit/position)
    while (buffer.hasRemaining()) {
        System.out.print((char) buffer.get()); // đọc từng byte ra khỏi buffer
    }
}
```

### Buffer — vùng đệm dữ liệu có trạng thái (position, limit, capacity)
```
Buffer có 3 chỉ số quan trọng:
- capacity: kích thước tối đa của buffer (cố định sau khi tạo)
- position: vị trí đọc/ghi hiện tại
- limit: giới hạn có thể đọc/ghi đến đâu

flip():  chuyển từ chế độ WRITE sang READ (limit = position hiện tại, position = 0)
clear(): reset về chế độ WRITE (position = 0, limit = capacity) - dữ liệu cũ không bị xóa nhưng sẽ bị ghi đè
rewind(): đọc lại từ đầu (position = 0, limit giữ nguyên)
```

> Ở mức khái niệm cơ bản: NIO Channel/Buffer phù hợp cho các ứng dụng cần **hiệu năng I/O cao, xử lý nhiều kết nối đồng thời** (VD: server mạng) nhờ khả năng non-blocking kết hợp với `Selector` (cho phép 1 thread giám sát nhiều channel cùng lúc) — trong khi I/O truyền thống đơn giản, dễ dùng hơn, phù hợp cho các tác vụ file thông thường.

---

## Câu hỏi tự kiểm tra
1. Khi nào nên dùng `InputStream`/`OutputStream`, khi nào nên dùng `Reader`/`Writer`? Cho ví dụ mỗi loại.
2. `BufferedReader` giải quyết vấn đề gì so với `FileReader` thuần túy? Giải thích bằng khái niệm "system call".
3. `transient` dùng để làm gì trong Serialization? Cho 2 lý do nên đánh dấu 1 field là `transient`.
4. `serialVersionUID` là gì và vì sao nên khai báo tường minh thay vì để JVM tự sinh?
5. Nêu 2 ưu điểm của `java.nio.file.Path`/`Files` (NIO.2) so với `java.io.File` truyền thống.
6. Phân biệt mô hình "Stream-oriented" của I/O truyền thống với "Buffer-oriented" của NIO Channel/Buffer.
7. Giải thích ý nghĩa của việc gọi `buffer.flip()` sau khi đọc dữ liệu vào buffer trước khi đọc dữ liệu ra khỏi buffer.

## Bài tập luyện tập
- Viết chương trình copy 1 file ảnh (byte stream) và 1 file text (character stream), so sánh code khác nhau ở đâu.
- Viết chương trình đọc 1 file text lớn bằng `BufferedReader.readLine()` và đếm số dòng, số từ.
- Viết class `User` implements `Serializable` có field `password` là `transient`, serialize/deserialize và kiểm chứng `password` bị mất sau khi đọc lại.
- Dùng `Files.walk()` để liệt kê tất cả file `.java` trong 1 thư mục dự án (đệ quy qua các thư mục con).
- Viết chương trình đọc 1 file bằng `FileChannel`/`ByteBuffer`, so sánh tốc độ với cách đọc bằng `FileInputStream` thông thường cho 1 file lớn (VD: vài trăm MB).

# Kiến trúc JDK / JRE / JVM — Giải thích chi tiết

> Đào sâu phần "JDK/JRE/JVM là gì, biên dịch và chạy" trong [01-nen-tang-ngon-ngu.md](01-nen-tang-ngon-ngu.md).

---

## 1. Tổng quan mối quan hệ

```
┌─────────────────────────────────────────────────────────┐
│                          JDK                             │
│   (Java Development Kit — dùng để PHÁT TRIỂN)            │
│                                                           │
│   javac | javadoc | jar | jdb | jconsole | jshell ...    │
│                                                           │
│   ┌───────────────────────────────────────────────────┐ │
│   │                       JRE                          │ │
│   │   (Java Runtime Environment — dùng để CHẠY)        │ │
│   │                                                     │ │
│   │   Thư viện chuẩn: java.lang, java.util, java.io,   │ │
│   │   java.net, collections, java.nio ...              │ │
│   │                                                     │ │
│   │   ┌─────────────────────────────────────────────┐ │ │
│   │   │                    JVM                       │ │ │
│   │   │   (Java Virtual Machine — máy ảo thực thi)    │ │ │
│   │   │                                                │ │ │
│   │   │   Class Loader | Runtime Data Area |          │ │ │
│   │   │   Execution Engine | JNI | Native Libraries    │ │ │
│   │   └─────────────────────────────────────────────┘ │ │
│   └───────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────┘
```

- **JVM** ⊂ **JRE** ⊂ **JDK**.
- Từ Java 11 trở đi, Oracle **không phân phối JRE riêng lẻ** nữa — chỉ còn JDK (nhưng khái niệm JRE vẫn tồn tại như một *phần* của JDK về mặt logic, hoặc dùng `jlink` để tự build runtime image tối giản).

---

## 2. JDK (Java Development Kit)

JDK là bộ công cụ đầy đủ nhất, dùng để **viết, biên dịch, debug, đóng gói** ứng dụng Java. Gồm:

| Thành phần | Vai trò |
|---|---|
| `javac` | Compiler: biên dịch `.java` → `.class` (bytecode) |
| `java` | Launcher: khởi động JVM và chạy chương trình |
| `javadoc` | Sinh tài liệu API từ comment `/** ... */` |
| `jar` | Đóng gói nhiều `.class` + resource thành 1 file `.jar` |
| `jdb` | Debugger dòng lệnh |
| `jconsole` / `jvisualvm` | Giám sát JVM (heap, thread, CPU) |
| `jshell` | REPL — chạy thử code Java tương tác (từ Java 9) |
| `jlink` | Tạo custom runtime image chỉ chứa module cần thiết (Java 9+) |
| JRE (bên trong) | Để chạy thử chương trình vừa biên dịch |

**Tóm lại:** JDK = JRE + compiler + dev tools. Nếu bạn là lập trình viên Java, bạn luôn cần cài JDK.

---

## 3. JRE (Java Runtime Environment)

JRE là môi trường **tối thiểu để chạy** một chương trình Java đã biên dịện (đã có sẵn file `.class`/`.jar`). Gồm:

1. **JVM** — máy ảo thực thi bytecode.
2. **Java Class Library (thư viện chuẩn)** — hàng ngàn class có sẵn: `java.lang.*`, `java.util.*`, `java.io.*`, `java.net.*`,...
3. Các file cấu hình, thuộc tính hỗ trợ runtime (không có compiler `javac`).

> Người dùng cuối chỉ cần JRE để **chạy** ứng dụng Java (VD: chạy 1 file `.jar` desktop app) — không cần biết code, không cần biên dịch.

---

## 4. JVM (Java Virtual Machine) — Trái tim của nền tảng Java

JVM là một **đặc tả (specification)** — có nhiều implementation khác nhau (HotSpot của Oracle/OpenJDK, OpenJ9 của Eclipse, GraalVM,...). JVM là lý do Java có khẩu hiệu **"Write Once, Run Anywhere" (WORA)**: bytecode sinh ra bởi `javac` không phụ thuộc hệ điều hành, chỉ cần có JVM tương ứng cho từng OS/CPU để thực thi.

JVM gồm 3 subsystem chính:

```
┌──────────────────────────────────────────────────────────────┐
│                            JVM                                │
│                                                                │
│  1. Class Loader Subsystem                                    │
│     Loading → Linking (Verify, Prepare, Resolve) → Initializing│
│                                                                │
│  2. Runtime Data Area (bộ nhớ)                                 │
│     Method Area | Heap | Stack (per thread) |                 │
│     PC Register (per thread) | Native Method Stack             │
│                                                                │
│  3. Execution Engine                                           │
│     Interpreter | JIT Compiler | Garbage Collector             │
│                                                                │
│  + Java Native Interface (JNI) — cầu nối gọi code native (C/C++)│
│  + Native Method Libraries                                      │
└──────────────────────────────────────────────────────────────┘
```

### 4.1. Class Loader Subsystem

Chịu trách nhiệm **nạp file `.class` vào bộ nhớ** khi chương trình cần dùng đến class đó (lazy loading — chỉ nạp khi cần, không nạp hết ngay từ đầu).

Gồm 3 giai đoạn:

1. **Loading**: đọc file `.class` (từ classpath, jar, network...) và tạo đối tượng `Class` tương ứng trong Method Area.
2. **Linking**:
   - **Verify**: kiểm tra bytecode hợp lệ, an toàn (không vi phạm bộ nhớ, không giả mạo).
   - **Prepare**: cấp phát bộ nhớ cho static fields, gán giá trị mặc định (0, null, false...).
   - **Resolve**: chuyển các symbolic reference (tên) thành direct reference (địa chỉ bộ nhớ thực).
3. **Initializing**: chạy static initializer và gán giá trị thực cho static field (`static { }` block, `static int x = 5;`).

#### Phân cấp Class Loader (Delegation Model)

```
Bootstrap ClassLoader   (nạp core Java: java.lang, java.util... từ <JAVA_HOME>/lib)
        ↓ (parent của)
Platform/Extension ClassLoader   (nạp thư viện mở rộng)
        ↓ (parent của)
Application ClassLoader   (nạp class trong classpath của chương trình bạn viết)
```

**Delegation model**: khi cần load 1 class, ClassLoader con **luôn hỏi ClassLoader cha trước** — cha không tìm thấy mới tự tìm. Điều này đảm bảo:
- Class lõi (`java.lang.String`) không bị ghi đè bởi code người dùng.
- Tránh nạp trùng 1 class nhiều lần.

### 4.2. Runtime Data Area (Vùng nhớ JVM)

| Vùng nhớ | Phạm vi | Mô tả |
|---|---|---|
| **Method Area** (Metaspace từ Java 8+) | Chia sẻ (shared) toàn bộ threads | Lưu metadata của class: tên, method, field, bytecode, static variables, runtime constant pool. Trước Java 8 gọi là **PermGen** (nằm trong Heap, dễ bị `OutOfMemoryError: PermGen space`); từ Java 8 chuyển thành **Metaspace** nằm trong **native memory** (bộ nhớ hệ điều hành, không phải Heap) → linh hoạt hơn, ít lỗi OOM hơn. |
| **Heap** | Chia sẻ toàn bộ threads | Nơi lưu **tất cả object và array** (`new`). Chia thành **Young Generation** (Eden + Survivor S0/S1) và **Old Generation (Tenured)**. Đây là vùng nhớ chính bị Garbage Collector quét dọn. |
| **Stack** | Riêng mỗi thread | Mỗi lần gọi method tạo 1 **Stack Frame** chứa: local variables, operand stack, reference đến runtime constant pool. Method kết thúc → frame bị pop. Lưu biến primitive và **reference** (con trỏ) tới object trên Heap. |
| **PC Register** | Riêng mỗi thread | Lưu địa chỉ (offset) của bytecode instruction đang thực thi của thread đó. |
| **Native Method Stack** | Riêng mỗi thread | Dùng khi gọi code native (C/C++) thông qua JNI. |

**Điểm mấu chốt hay bị hỏi:**
> Object luôn nằm trên **Heap**. Biến local (kể cả reference variable) nằm trên **Stack** của thread đang chạy method đó — biến chỉ giữ **địa chỉ** trỏ tới object trên Heap.

```java
void method() {
    Person p = new Person("An"); // "p" (reference) -> Stack
                                  // object Person("An") -> Heap
}
```

### 4.3. Execution Engine

Chịu trách nhiệm **thực thi bytecode**. Gồm:

1. **Interpreter**: đọc và thực thi bytecode từng dòng một → khởi động nhanh nhưng chạy chậm nếu lặp lại nhiều lần (interpret lại mỗi lần).
2. **JIT Compiler (Just-In-Time)**: phát hiện đoạn code "nóng" (hot spot — chạy nhiều lần, VD trong vòng lặp) và **biên dịch trực tiếp thành native machine code** của CPU, cache lại để lần sau chạy thẳng không cần interpret nữa → tăng tốc đáng kể. Đây là lý do JVM implementation phổ biến nhất tên là **HotSpot**.
   - C1 (Client compiler): biên dịch nhanh, tối ưu ít — dùng cho khởi động nhanh.
   - C2 (Server compiler): biên dịch chậm hơn nhưng tối ưu sâu — dùng cho code chạy lâu dài.
   - Tiered Compilation (mặc định từ Java 8+): kết hợp cả Interpreter → C1 → C2 tùy mức độ "nóng" của code.
3. **Garbage Collector (GC)**: tự động dọn dẹp object không còn được tham chiếu (unreachable) trên Heap, giải phóng bộ nhớ mà lập trình viên không cần `free()` thủ công như C/C++.

### 4.4. JNI (Java Native Interface) & Native Method Libraries

- **JNI**: cầu nối cho phép code Java gọi (và bị gọi bởi) code viết bằng ngôn ngữ khác (C, C++) — dùng khi cần thao tác phần cứng, thư viện hệ thống, hoặc tối ưu hiệu năng cực cao.
- **Native Method Libraries**: các thư viện native (`.dll`, `.so`) được JNI liên kết tới.

---

## 5. Toàn bộ vòng đời: Từ source code đến khi chạy

```
HelloWorld.java
      │  javac (compiler kiểm tra cú pháp, semantic)
      ▼
HelloWorld.class  (bytecode — dạng nhị phân độc lập platform)
      │  lệnh: java HelloWorld
      ▼
┌─────────────────────────── JVM khởi động ───────────────────────────┐
│                                                                      │
│  1. Class Loader nạp HelloWorld.class (Loading → Linking → Init)    │
│  2. Cấp phát Runtime Data Area (Method Area, Heap, Stack cho thread  │
│     main, PC Register...)                                           │
│  3. Execution Engine bắt đầu thực thi từ method main():             │
│       - Interpreter chạy bytecode từng dòng                         │
│       - Nếu phát hiện method/loop "nóng" → JIT compiler biên dịch    │
│         sang native code để chạy nhanh hơn                          │
│  4. Trong lúc chạy, object được tạo trên Heap; khi không còn         │
│     reference nào trỏ tới → Garbage Collector thu hồi                │
│  5. Chương trình kết thúc (hết main() hoặc System.exit()) → JVM tắt  │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 6. Vì sao kiến trúc này giúp Java "Write Once, Run Anywhere"?

- `javac` sinh ra **bytecode** — định dạng trung gian, không gắn với bất kỳ hệ điều hành/CPU nào.
- Mỗi nền tảng (Windows/Linux/macOS, x86/ARM) có **JVM implementation riêng** hiểu và thực thi đúng loại bytecode đó.
- Lập trình viên chỉ biên dịch 1 lần → file `.class`/`.jar` chạy được trên bất kỳ máy nào có cài JVM phù hợp, không cần biên dịch lại.

---

## 7. Câu hỏi tự kiểm tra
1. Vẽ lại sơ đồ quan hệ JDK–JRE–JVM và giải thích vì sao từ Java 11 Oracle không phát hành JRE độc lập nữa.
2. Class Loader có mấy giai đoạn? Giai đoạn nào kiểm tra tính an toàn của bytecode?
3. Vì sao Method Area chuyển từ PermGen (trong Heap) sang Metaspace (native memory) kể từ Java 8? Lợi ích là gì?
4. Sự khác nhau giữa Heap và Stack: cái gì được lưu ở đâu? Vì sao Stack là "per-thread" còn Heap thì chia sẻ?
5. JIT compiler hoạt động như thế nào để tăng tốc chương trình so với Interpreter thuần túy?
6. Giải thích cơ chế "delegation model" của Class Loader và lý do nó tồn tại (bảo mật, tránh trùng lặp).
7. Vì sao nói JVM là một "specification" chứ không phải 1 sản phẩm cụ thể? Kể tên 2 implementation phổ biến.

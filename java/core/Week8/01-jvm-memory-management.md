# Tuần 8: JVM & Memory Management

> Tài liệu tổng hợp kiến thức mục 8 trong lộ trình `java-core-roadmap.md`.
> Kiến trúc JVM tổng quan (Class Loader, Runtime Data Area, Execution Engine) đã được trình bày chi tiết ở [Week1/02-kien-truc-jdk-jre-jvm.md](../Week1/02-kien-truc-jdk-jre-jvm.md) — tài liệu này đào sâu thêm về **Garbage Collection** và **Memory Management**.

---

## 1. Kiến trúc JVM (Ôn tập nhanh)

3 subsystem chính của JVM:
```
Class Loader Subsystem  ->  Runtime Data Area (bộ nhớ)  ->  Execution Engine
```
Xem chi tiết đầy đủ (Loading/Linking/Initializing, delegation model, Method Area/Heap/Stack/PC Register, Interpreter/JIT) tại [Week1/02-kien-truc-jdk-jre-jvm.md](../Week1/02-kien-truc-jdk-jre-jvm.md).

---

## 2. Vùng nhớ chi tiết: Stack, Heap, Metaspace, PC Register, Native Method Stack

### Heap — chia thành các "thế hệ" (Generational Hypothesis)
Giả thuyết nền tảng của GC hiện đại: **"đa số object chết trẻ"** (most objects die young) — object tạm thời (biến local, object trung gian trong tính toán) chiếm phần lớn và bị loại bỏ rất nhanh, trong khi số ít object sống lâu dài (cache, singleton, connection pool). Dựa trên giả thuyết này, Heap được chia thành các vùng để tối ưu GC:

```
Heap
├── Young Generation (thế hệ trẻ - object mới tạo)
│     ├── Eden Space       (object mới `new` luôn được cấp phát ở đây đầu tiên)
│     ├── Survivor Space 0 (S0)
│     └── Survivor Space 1 (S1)
│           (S0, S1 dùng luân phiên - tại 1 thời điểm chỉ 1 trong 2 đang "active")
│
└── Old Generation / Tenured Generation (thế hệ già - object sống lâu)
```

**Vòng đời của 1 object qua các vùng Heap:**
```
1. new Object() -> cấp phát vào Eden Space
2. Khi Eden đầy -> chạy Minor GC (GC nhỏ, chỉ quét Young Gen):
   - Object còn sống (còn được reference) -> chuyển sang 1 Survivor Space (VD: S0)
   - Object chết (không còn reference) -> bị dọn dẹp ngay, giải phóng bộ nhớ
3. Minor GC lần tiếp theo: object sống ở S0 được chuyển sang S1 (Eden cũng dồn sang S1),
   mỗi lần "sống sót" qua 1 lần GC, object được tăng "age" (tuổi) thêm 1
4. Khi object đạt "age" đủ lớn (ngưỡng mặc định thường là 15, cấu hình qua -XX:MaxTenuringThreshold)
   -> được "promote" (thăng cấp) sang Old Generation
5. Khi Old Generation đầy -> chạy Major GC / Full GC (GC toàn bộ Heap, tốn kém hơn Minor GC nhiều)
```

> **Vì sao thiết kế theo generation?** Minor GC (chỉ quét Young Gen, vùng nhỏ) chạy rất nhanh và thường xuyên; Full GC (quét toàn bộ Heap) chậm và gây "stop-the-world" (dừng toàn bộ ứng dụng) lâu hơn nhiều — nên thiết kế để đa số object "chết trẻ" bị dọn ở Minor GC nhanh, hạn chế tần suất Full GC tốn kém.

### Metaspace (thay PermGen từ Java 8)
- Lưu **metadata của class**: tên class, method, field, bytecode, runtime constant pool, static variables.
- Trước Java 8: gọi là **PermGen** (Permanent Generation), nằm **trong Heap**, có kích thước cố định (`-XX:MaxPermSize`) → dễ gặp `OutOfMemoryError: PermGen space` khi ứng dụng load quá nhiều class (VD: dùng nhiều framework, hoặc bị memory leak về class/classloader).
- Từ Java 8: chuyển thành **Metaspace**, nằm trong **native memory** (bộ nhớ trực tiếp của OS, ngoài Heap), **mặc định không giới hạn** (chỉ giới hạn bởi RAM vật lý của máy, có thể set `-XX:MaxMetaspaceSize` nếu muốn giới hạn) → linh hoạt hơn, ít gặp lỗi OOM do PermGen như trước.

### Stack, PC Register, Native Method Stack
Đã trình bày chi tiết ở [Week1/02-kien-truc-jdk-jre-jvm.md](../Week1/02-kien-truc-jdk-jre-jvm.md#42-runtime-data-area-vùng-nhớ-jvm) — mỗi thread có **Stack** riêng (lưu Stack Frame cho mỗi lần gọi method: local variable, operand stack), **PC Register** riêng (lưu địa chỉ bytecode đang chạy), và **Native Method Stack** riêng (dùng khi gọi code native qua JNI).

---

## 3. Garbage Collection: Thuật toán và các loại GC

### Thuật toán cơ bản: Mark - Sweep - Compact

```
Bước 1 - MARK (đánh dấu):
  Bắt đầu từ các "GC Roots" (xem mục 4), duyệt (traverse) toàn bộ đồ thị object
  đang được tham chiếu -> đánh dấu mọi object CÒN SỐNG (reachable)

Bước 2 - SWEEP (quét dọn):
  Duyệt qua toàn bộ Heap, giải phóng bộ nhớ của mọi object KHÔNG được đánh dấu
  (tức là "chết" - không còn reachable từ GC Roots)
  => Vấn đề: để lại các "lỗ hổng" (fragmentation) rải rác trong bộ nhớ

Bước 3 - COMPACT (dồn nén, tùy chọn):
  Di chuyển các object còn sống lại gần nhau (liền kề), loại bỏ khoảng trống (fragmentation)
  => Giúp cấp phát object mới nhanh hơn (chỉ cần dịch 1 con trỏ "bump the pointer"
     thay vì tìm kiếm lỗ hổng đủ lớn trong danh sách free-list)
```

### Các loại GC (Garbage Collector) trong JVM

| GC | Đặc điểm | Phù hợp cho |
|---|---|---|
| **Serial GC** | Đơn luồng (single-threaded), dừng toàn bộ ứng dụng (stop-the-world) khi GC chạy | Ứng dụng nhỏ, máy đơn CPU, heap nhỏ (VD: môi trường container giới hạn tài nguyên) |
| **Parallel GC** (Throughput Collector) | Đa luồng cho cả Minor và Major GC, vẫn stop-the-world nhưng nhanh hơn Serial nhờ chạy song song | Ứng dụng batch-processing, ưu tiên **throughput** tổng thể hơn là độ trễ (latency) từng lần GC |
| **CMS (Concurrent Mark Sweep)** | Chạy phần lớn công việc GC **đồng thời (concurrent)** với ứng dụng (giảm thời gian stop-the-world), nhưng **không nén (compact)** -> gây phân mảnh bộ nhớ theo thời gian. **Đã bị deprecated từ Java 9, loại bỏ từ Java 14** | Từng phù hợp cho ứng dụng cần độ trễ thấp (trước khi có G1) |
| **G1 (Garbage First)** | Chia Heap thành nhiều **vùng nhỏ (region)** thay vì 2 vùng liền khối lớn (Young/Old truyền thống), ưu tiên dọn dẹp vùng có nhiều rác nhất trước ("Garbage First"), có nén (compact), cân bằng giữa throughput và latency. **Là GC mặc định từ Java 9 trở đi** | Ứng dụng đa số hiện đại, heap lớn (vài GB đến hàng trăm GB), cần độ trễ dự đoán được |
| **ZGC** (Java 11+) / **Shenandoah** (Java 12+) | GC có độ trễ cực thấp (**thời gian dừng dưới 10ms**, gần như không phụ thuộc vào kích thước Heap) nhờ thực hiện hầu hết công việc concurrent (kể cả compact) | Ứng dụng yêu cầu độ trễ (latency) cực thấp, heap rất lớn (hàng trăm GB đến TB), hệ thống tài chính/giao dịch real-time |

> **Cấu hình chọn GC**: `-XX:+UseG1GC`, `-XX:+UseZGC`, `-XX:+UseParallelGC`,... (từ Java 9, G1 là mặc định nên thường không cần chỉ định).

---

## 4. GC Roots — Khi nào Object "Eligible for GC"

### GC Roots là gì?
Là tập hợp các điểm khởi đầu (entry point) mà GC dùng để xác định object nào **"còn sống" (reachable)**. Nếu 1 object không thể truy vết được (unreachable) từ bất kỳ GC Root nào (trực tiếp hoặc gián tiếp qua chuỗi tham chiếu), nó được coi là **rác (garbage)** và **eligible for GC** (đủ điều kiện bị thu hồi).

**Các loại GC Roots phổ biến:**
1. **Local variable** trên Stack của các thread đang chạy (biến trong method đang thực thi).
2. **Active thread** (`Thread` object đang chạy).
3. **Static field** của các class đã được load (tồn tại suốt vòng đời class, trong Method Area/Metaspace).
4. **JNI reference** (object được tham chiếu từ code native qua JNI).

```java
class Node {
    Node next;
}

void method() {
    Node a = new Node(); // "a" là local variable -> GC Root
    Node b = new Node();
    a.next = b; // b được tham chiếu (reachable) TỪ a (mà a lại reachable từ GC Root)
    a = null;    // giờ "a" không còn trỏ tới object A nữa
    // -> Object A KHÔNG còn reachable từ bất kỳ GC Root nào -> eligible for GC
    // -> Object B cũng KHÔNG còn reachable (vì chỉ được trỏ tới từ A, mà A đã "chết")
    //    -> Object B cũng eligible for GC, dù bản thân biến "b" chưa null!
}
```

### Trường hợp hay gây nhầm lẫn: circular reference (tham chiếu vòng) vẫn bị GC
```java
class Node {
    Node other;
}

void method() {
    Node x = new Node();
    Node y = new Node();
    x.other = y;
    y.other = x; // x và y tham chiếu VÒNG lẫn nhau

    x = null;
    y = null;
    // Dù x và y tham chiếu lẫn nhau, KHÔNG local variable nào (GC Root) còn trỏ tới chúng
    // -> Cả 2 vẫn eligible for GC (khác với reference counting - kỹ thuật đếm tham chiếu ngây thơ
    //    sẽ KHÔNG dọn được circular reference, nhưng thuật toán Mark-Sweep của Java thì có thể)
}
```

> Đây chính là lý do JVM dùng **thuật toán Reachability (Mark-Sweep)** thay vì **Reference Counting** đơn thuần (như 1 số ngôn ngữ khác) — reachability xử lý đúng được cả trường hợp tham chiếu vòng.

---

## 5. ClassLoader (Ôn tập nhanh)

Đã trình bày chi tiết ở [Week1/02-kien-truc-jdk-jre-jvm.md](../Week1/02-kien-truc-jdk-jre-jvm.md#41-class-loader-subsystem):
- **Bootstrap ClassLoader** → **Extension/Platform ClassLoader** → **Application ClassLoader**.
- **Delegation model**: ClassLoader con luôn hỏi ClassLoader cha trước khi tự tìm — đảm bảo class lõi (`java.lang.*`) không bị ghi đè, tránh nạp trùng lặp.

---

## 6. Memory Leak Thường Gặp trong Java

Dù Java có Garbage Collector, **memory leak vẫn có thể xảy ra** — khi object không còn cần dùng nữa nhưng vẫn còn ít nhất 1 reference "vô tình" trỏ tới nó từ 1 GC Root, khiến GC **không thể** coi nó là rác dù về mặt logic nó đã "vô dụng".

### 1. Static Collection (nguyên nhân phổ biến nhất)
```java
class Cache {
    // static -> tồn tại SUỐT VÒNG ĐỜI của class (đến khi ứng dụng tắt hoặc ClassLoader bị unload)
    private static final List<Object> cache = new ArrayList<>();

    void addToCache(Object obj) {
        cache.add(obj); // liên tục thêm nhưng KHÔNG BAO GIỜ remove
        // -> object bị "static collection" giữ tham chiếu mãi mãi, GC không bao giờ thu hồi được
        // -> heap dần đầy lên theo thời gian -> cuối cùng OutOfMemoryError
    }
}
```
**Cách khắc phục**: dùng cache có giới hạn kích thước và cơ chế tự loại bỏ (VD: `LinkedHashMap` LRU như ở [Week3/04-map-chi-tiet.md](../Week3/04-map-chi-tiet.md)), hoặc dùng `WeakHashMap`/`SoftReference` cho cache (xem mục 7).

### 2. Listener/Callback chưa Unregister
```java
class EventPublisher {
    private List<EventListener> listeners = new ArrayList<>();

    void addListener(EventListener listener) {
        listeners.add(listener);
    }
    // Nếu KHÔNG có removeListener() được gọi khi component/UI bị hủy,
    // listener đó (và toàn bộ object mà nó tham chiếu tới, VD: 1 màn hình UI) vẫn bị giữ mãi
    // trong danh sách listeners của EventPublisher (thường là 1 singleton sống lâu dài)
}
```
**Cách khắc phục**: luôn đảm bảo có cơ chế `removeListener()`/`unsubscribe()` tương ứng khi đối tượng đăng ký không còn cần lắng nghe sự kiện nữa (VD: khi component bị destroy).

### 3. ThreadLocal không remove
```java
class RequestContext {
    private static final ThreadLocal<Map<String, Object>> context = new ThreadLocal<>();

    void setData(String key, Object value) {
        context.get().put(key, value);
    }
    // Nếu quên gọi context.remove() sau khi xử lý request xong,
    // trong môi trường dùng THREAD POOL (VD: Tomcat, Spring server), các thread được TÁI SỬ DỤNG
    // liên tục cho nhiều request khác nhau -> dữ liệu của request TRƯỚC vẫn còn tồn tại trong
    // ThreadLocal của thread đó -> vừa leak bộ nhớ, vừa có nguy cơ LỘ DỮ LIỆU giữa các request khác nhau!
}

// SỬA: luôn remove trong finally
try {
    context.setData("userId", "123");
    // xử lý request...
} finally {
    context.remove(); // BẮT BUỘC khi dùng ThreadLocal trong môi trường thread pool
}
```
> Đây là 1 trong những lỗi memory leak **nguy hiểm nhất** trong ứng dụng web Java vì thread trong pool tồn tại rất lâu (không như thread tạo mới cho từng request), khiến `ThreadLocal` bị leak "cộng dồn" theo thời gian.

### Tổng kết nguyên nhân chung
Cả 3 trường hợp trên đều có chung 1 gốc rễ: **object bị 1 thứ "sống lâu" (static field, singleton, thread trong pool) giữ tham chiếu (trực tiếp hoặc gián tiếp) lâu hơn cần thiết**, khiến GC Root vẫn còn dẫn tới object đó dù logic nghiệp vụ đã không cần nó nữa.

---

## 7. `System.gc()`, `finalize()` (deprecated), Phantom Reference

### `System.gc()`
```java
System.gc(); // chỉ là 1 "GỢI Ý" (hint) cho JVM nên chạy GC, KHÔNG đảm bảo GC chạy ngay lập tức
```
- JVM **có quyền bỏ qua** lời gọi này hoàn toàn.
- Gọi `System.gc()` thủ công trong code production **không được khuyến khích** — GC hiện đại (G1, ZGC) đã được tối ưu rất tốt để tự quyết định thời điểm phù hợp; gọi thủ công có thể gây ra 1 lần Full GC không cần thiết, ảnh hưởng hiệu năng.

### `finalize()` — Deprecated (từ Java 9), sẽ bị loại bỏ
```java
class Resource {
    @Override
    protected void finalize() throws Throwable {
        // "Dọn dẹp" trước khi object bị GC - Ý TƯỞNG BAN ĐẦU nhưng có nhiều vấn đề
        super.finalize();
    }
}
```
**Vì sao `finalize()` bị coi là thiết kế tồi và deprecated:**
- **Không đảm bảo được gọi** — nếu chương trình thoát đột ngột, hoặc GC quyết định không bao giờ cần thu hồi vùng nhớ đó, `finalize()` có thể **không bao giờ chạy**.
- **Không đảm bảo thời điểm chạy** — object có thể "chết" từ lâu nhưng `finalize()` chạy muộn (chỉ chạy khi GC thực sự quét tới), khiến resource (file handle, connection) bị giữ lâu hơn cần thiết.
- **Có thể "hồi sinh" object** (resurrection) — trong `finalize()`, nếu vô tình gán `this` cho 1 static field, object có thể trở nên reachable trở lại, làm phức tạp hóa vòng đời object và gây bug khó debug.
- **Ảnh hưởng hiệu năng GC** — object có `finalize()` cần thêm 1 bước xử lý đặc biệt trong GC, chậm hơn object thường.

> **Thay thế hiện đại**: dùng `try-with-resources` + `AutoCloseable` (xem [Week5](../Week5/01-exception-handling.md)) để giải phóng resource **tường minh, đúng lúc, đảm bảo chắc chắn chạy** — thay vì phụ thuộc vào GC không đáng tin cậy.

### Reference Types — 4 mức độ "mạnh yếu" của tham chiếu

| Loại | Khi nào bị GC thu hồi | Use case |
|---|---|---|
| **Strong Reference** (mặc định) | Chỉ bị GC khi **không còn** Strong Reference nào trỏ tới (reference thông thường ta hay dùng) | Dùng hàng ngày, mặc định |
| **SoftReference** | Bị GC thu hồi khi JVM **sắp hết bộ nhớ** (trước khi ném `OutOfMemoryError`) | Cache — giữ dữ liệu càng lâu càng tốt, nhưng sẵn sàng "hy sinh" khi bộ nhớ thiếu |
| **WeakReference** | Bị GC thu hồi ngay ở **lần GC tiếp theo** (dù bộ nhớ còn dư dả), miễn là không còn Strong Reference nào khác | `WeakHashMap`, tránh memory leak khi cần giữ tham chiếu tạm thời mà không "khóa" object mãi mãi |
| **PhantomReference** | Object đã bị GC thu hồi vùng nhớ thực sự, `get()` **luôn trả về `null`** (không thể lấy lại object) | Theo dõi chính xác **thời điểm object đã được GC dọn hoàn toàn** — dùng để dọn dẹp tài nguyên native (off-heap) 1 cách đáng tin cậy hơn `finalize()`, thường kết hợp với `ReferenceQueue` |

```java
// SoftReference - cache tự "nhường chỗ" khi thiếu bộ nhớ
SoftReference<byte[]> softCache = new SoftReference<>(new byte[1024 * 1024]);
byte[] data = softCache.get(); // có thể null nếu đã bị GC thu hồi do thiếu bộ nhớ

// WeakReference - nền tảng của WeakHashMap
WeakReference<Object> weakRef = new WeakReference<>(new Object());
// object gốc sẽ bị GC ngay khi không còn Strong Reference nào khác, dù weakRef vẫn "biết" về nó

Map<Object, String> weakMap = new WeakHashMap<>(); // key bị GC tự động khi không còn ai giữ Strong Reference tới key đó

// PhantomReference - biết CHÍNH XÁC khi nào object bị dọn hoàn toàn
ReferenceQueue<Object> queue = new ReferenceQueue<>();
PhantomReference<Object> phantomRef = new PhantomReference<>(new Object(), queue);
// Khi object bị GC dọn xong, phantomRef sẽ xuất hiện trong "queue" -> có thể dùng để dọn tài nguyên native tương ứng
```

---

## Câu hỏi tự kiểm tra
1. Giải thích "Generational Hypothesis" — vì sao Heap được chia thành Young Gen và Old Gen thay vì 1 vùng duy nhất?
2. Trình bày vòng đời của 1 object qua Eden -> Survivor -> Old Generation, kèm khái niệm "age" và "promotion".
3. Vì sao Metaspace (Java 8+) ít gặp lỗi `OutOfMemoryError` hơn PermGen (Java 7 trở về trước)?
4. Trình bày 3 bước Mark-Sweep-Compact. Vì sao bước Compact lại cần thiết (ngoài việc chỉ Sweep)?
5. Kể tên 4 loại GC Root. Cho ví dụ 1 object bị coi là "eligible for GC" dù 2 object vẫn tham chiếu vòng lẫn nhau.
6. Giải thích chi tiết vì sao quên gọi `ThreadLocal.remove()` trong môi trường thread pool lại vừa gây memory leak vừa có nguy cơ lộ dữ liệu giữa các request.
7. Vì sao `finalize()` bị deprecated? Nên thay thế bằng cơ chế nào?
8. Phân biệt 4 loại reference (Strong/Soft/Weak/Phantom) — mỗi loại bị GC thu hồi trong điều kiện nào?

## Bài tập luyện tập
- Chạy chương trình Java với flag `-Xlog:gc` (hoặc `-verbose:gc`) để quan sát Minor GC/Full GC xảy ra khi tạo hàng triệu object nhỏ.
- Viết chương trình minh họa memory leak bằng static collection (thêm object liên tục vào 1 `static List` mà không bao giờ xóa), theo dõi heap tăng dần bằng `jconsole`/`jvisualvm`.
- Viết ví dụ minh họa lỗi `ThreadLocal` không remove trong 1 mô phỏng thread pool đơn giản (dùng `ExecutorService` tái sử dụng thread), chứng minh dữ liệu "rò rỉ" giữa các task khác nhau.
- Viết chương trình dùng `WeakHashMap` làm cache, chứng minh entry tự động biến mất sau khi key không còn Strong Reference và GC chạy.
- So sánh hành vi `SoftReference` và `WeakReference` bằng cách tạo áp lực bộ nhớ (heap gần đầy) và quan sát thời điểm mỗi loại bị thu hồi.

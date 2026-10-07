# Map trong Java — Đào sâu chi tiết

> Đào sâu phần "Map: HashMap, LinkedHashMap, TreeMap, Hashtable, ConcurrentHashMap" trong [01-collections-framework.md](01-collections-framework.md).

---

## 1. Map là gì? Vị trí trong kiến trúc

`Map<K, V>` là interface **độc lập, không extends `Collection`** (xem lại lý do trong [01-collections-framework.md](01-collections-framework.md#1-kiến-trúc-tổng-thể)), biểu diễn **ánh xạ key → value**, mỗi key là duy nhất.

```
Map<K, V>
    │
    ├── HashMap
    │      └── LinkedHashMap (extends HashMap)
    ├── SortedMap (interface con)
    │      └── NavigableMap (interface con)
    │             └── TreeMap
    ├── Hashtable (legacy, thread-safe)
    │      └── Properties (extends Hashtable)
    └── ConcurrentMap (interface, java.util.concurrent)
           └── ConcurrentHashMap
           └── ConcurrentSkipListMap (sorted + thread-safe)
```

Đặc trưng chung: `put(K, V)`, `get(K)`, `remove(K)`, `containsKey(K)`, `containsValue(V)`, `keySet()`, `values()`, `entrySet()`.

---

## 2. Các loại Map và kiến trúc bên trong

### 2.1. HashMap — Cấu trúc Bucket + Linked List/Tree

**Kiến trúc bên trong:**
```java
public class HashMap<K, V> {
    transient Node<K,V>[] table; // mảng các "bucket"
    static final float DEFAULT_LOAD_FACTOR = 0.75f;
    static final int TREEIFY_THRESHOLD = 8;  // ngưỡng chuyển bucket sang cây
    static final int UNTREEIFY_THRESHOLD = 6; // ngưỡng chuyển ngược cây về linked list

    static class Node<K,V> {
        final int hash;
        final K key;
        V value;
        Node<K,V> next; // linked list nếu có collision
    }
}
```

**Từng bước hoạt động của `put(key, value)`:**
```
1. Tính hash(key):  h = key.hashCode(); hash = h ^ (h >>> 16)
   (XOR với 16 bit cao để trộn đều bit, giảm collision khi capacity nhỏ)
2. Xác định bucket index = hash & (table.length - 1)
   (capacity luôn là lũy thừa của 2 -> phép AND thay thế phép % nhanh hơn)
3. Nếu bucket trống -> tạo Node mới, đặt vào bucket
4. Nếu bucket có phần tử (collision):
     a. Nếu bucket là linked list -> duyệt qua từng node, so sánh key bằng equals()
        - Nếu tìm thấy key trùng -> ghi đè value, trả về value cũ
        - Nếu không -> thêm Node mới vào CUỐI linked list
     b. Nếu bucket đã là cây đỏ-đen (đã treeify) -> tìm/thêm theo cơ chế BST O(log n)
5. Nếu độ dài linked list của 1 bucket >= 8 (TREEIFY_THRESHOLD)
   VÀ table.length >= 64 (MIN_TREEIFY_CAPACITY)
     -> chuyển bucket đó thành Red-Black Tree (treeify)
   (Nếu table.length < 64, ưu tiên resize bảng trước thay vì treeify)
6. Sau khi thêm, nếu size > table.length * loadFactor (mặc định 0.75)
     -> resize(): tạo bảng mới gấp đôi capacity, rehash và phân phối lại TOÀN BỘ entry
```

**Vì sao capacity luôn là lũy thừa của 2?**
- Cho phép dùng `hash & (capacity - 1)` thay vì `hash % capacity` — phép AND nhanh hơn phép chia/modulo rất nhiều ở cấp độ CPU.

**Resize (rehashing) — điểm hay bị hỏi:**
- Khi resize, JDK 8+ có tối ưu: mỗi entry chỉ có thể rơi vào 1 trong 2 vị trí ở bảng mới (vị trí cũ, hoặc vị trí cũ + capacity cũ) — nhờ tính chất của phép AND với lũy thừa 2 — nên **không cần tính lại hash từ đầu**, chỉ cần kiểm tra 1 bit để biết entry thuộc nhóm nào.

```java
Map<String, Integer> map = new HashMap<>();
map.put("apple", 1);
map.put("apple", 2); // trùng key -> ghi đè, put() trả về giá trị CŨ (1)
map.get("banana");    // null nếu không tồn tại key
```

- **Độ phức tạp trung bình**: O(1) cho `put`/`get`/`remove`.
- **Trường hợp xấu nhất** (nhiều collision, chưa treeify): O(n). Sau khi treeify: O(log n).
- Cho phép **1 key null** và nhiều value null.
- **Không thread-safe**: nhiều thread `put()` đồng thời có thể gây race condition, thậm chí vòng lặp vô hạn khi resize (bug kinh điển ở Java 7 trở về trước với linked list vòng — đã được khắc phục 1 phần ở Java 8 nhưng vẫn **không an toàn** để dùng đa luồng).

### 2.2. LinkedHashMap — HashMap + Thứ tự (Insertion hoặc Access order)

**Kiến trúc bên trong:**
```java
public class LinkedHashMap<K,V> extends HashMap<K,V> {
    // Mỗi Entry kế thừa thêm 2 con trỏ before/after
    static class Entry<K,V> extends HashMap.Node<K,V> {
        Entry<K,V> before, after; // tạo thành 1 danh sách liên kết đôi xuyên suốt mọi entry
    }
    final boolean accessOrder; // false = insertion order (mặc định), true = access order
}
```

- Về bản chất vẫn dùng **bucket array giống HashMap** để tra cứu O(1), nhưng có thêm **1 danh sách liên kết đôi phụ (double linked list)** nối tất cả các Entry theo thứ tự — giúp `keySet()`/`entrySet()`/`values()` duyệt theo đúng thứ tự mong muốn.
- **`accessOrder = false` (mặc định)**: giữ thứ tự chèn (insertion order).
- **`accessOrder = true`**: mỗi lần `get()` 1 entry, entry đó được **di chuyển ra cuối danh sách liên kết** → thứ tự duyệt trở thành "least recently used → most recently used". Đây chính là cơ chế nền tảng để cài đặt **LRU Cache**.

```java
// Insertion order (mặc định)
Map<String, Integer> insertionOrderMap = new LinkedHashMap<>();

// Access order -> nền tảng cho LRU Cache
Map<String, Integer> accessOrderMap = new LinkedHashMap<>(16, 0.75f, true);
accessOrderMap.put("a", 1);
accessOrderMap.put("b", 2);
accessOrderMap.get("a"); // "a" được đẩy ra cuối vì vừa được truy cập
// Thứ tự hiện tại: b, a
```

### 2.3. TreeMap — Cây đỏ-đen (Red-Black Tree)

**Kiến trúc bên trong:**
```java
public class TreeMap<K,V> {
    private transient Entry<K,V> root; // gốc của cây đỏ-đen
    private final Comparator<? super K> comparator; // null nếu dùng Comparable tự nhiên

    static final class Entry<K,V> {
        K key; V value;
        Entry<K,V> left, right, parent;
        boolean color; // RED hoặc BLACK
    }
}
```

- Toàn bộ entry được lưu trong **1 cây tìm kiếm nhị phân tự cân bằng** theo key (không dùng bucket/hash).
- Mọi thao tác `put`/`get`/`remove` đều là **O(log n)** vì mỗi lần chỉ cần đi từ gốc xuống lá theo 1 nhánh (`key < node.key` đi trái, ngược lại đi phải).
- Sau mỗi lần thêm/xóa, cây tự cân bằng lại (xoay trái/xoay phải, đổi màu node) để đảm bảo chiều cao luôn ~ O(log n), không suy biến thành danh sách.
- Duyệt `entrySet()` luôn cho ra thứ tự **sắp xếp theo key** (in-order traversal của cây).

```java
TreeMap<String, Integer> treeMap = new TreeMap<>();
treeMap.put("banana", 2); treeMap.put("apple", 1); treeMap.put("cherry", 3);
System.out.println(treeMap); // {apple=1, banana=2, cherry=3} - luôn sắp xếp theo key

treeMap.firstKey();          // "apple"
treeMap.lastKey();           // "cherry"
treeMap.higherKey("apple");  // "banana" (key nhỏ nhất > "apple")
treeMap.ceilingKey("b");     // "banana" (key nhỏ nhất >= "b")
treeMap.headMap("cherry");   // {apple=1, banana=2} (các entry có key < "cherry")
```

> Key trong `TreeMap` phải implement `Comparable` hoặc truyền `Comparator` vào constructor; không cho phép key `null` (ném NPE khi so sánh).

### 2.4. Hashtable — Legacy thread-safe Map

- Cấu trúc bên trong tương tự `HashMap` cũ (bucket + linked list) nhưng **mọi method đều `synchronized`** ở cấp độ toàn bộ object (`this`) → khóa nguyên cả bảng khi 1 thread đang thao tác, các thread khác phải chờ.
- **Không cho phép** key hoặc value là `null` (ném `NullPointerException` ngay lập tức — khác với `HashMap` cho phép 1 null key).
- Là lớp **legacy** (JDK 1.0, trước cả Collections Framework) — hiệu năng kém do lock quá thô (coarse-grained locking), hiện nay hầu như không dùng.

### 2.5. ConcurrentHashMap — Thread-safe hiệu năng cao

**Kiến trúc (Java 8+):**
- Không còn dùng cơ chế "phân đoạn" (Segment locking) như Java 7, mà dùng **CAS (Compare-And-Swap)** kết hợp **khóa ở cấp độ từng bucket** (synchronized trên node đầu của bucket) khi cần.
- Đọc (`get()`) hầu như **không cần lock** — nhờ các field quan trọng được khai báo `volatile`, đảm bảo visibility giữa các thread (xem thêm Java Memory Model ở Tuần 4).
- Ghi (`put()`) chỉ khóa **bucket cụ thể** đang bị ghi, không khóa toàn bộ bảng → nhiều thread có thể ghi đồng thời vào các bucket khác nhau mà không tranh chấp.
- Cũng áp dụng cơ chế treeify giống HashMap khi bucket có nhiều collision.

```java
ConcurrentHashMap<String, Integer> map = new ConcurrentHashMap<>();
map.put("count", 0);

// Thao tác atomic (an toàn tuyệt đối với đa luồng, không cần lock thủ công)
map.compute("count", (k, v) -> v + 1);
map.computeIfAbsent("newKey", k -> 0);
map.merge("count", 1, Integer::sum); // cộng dồn an toàn giữa nhiều thread
```

> Không cho phép key/value `null` (giống Hashtable) — vì trong môi trường đa luồng, `get(key) == null` không phân biệt được "key không tồn tại" hay "key tồn tại với value null", gây nhập nhằng khi nhiều thread thao tác đồng thời.

---

## 3. Bảng so sánh tổng hợp

| Tiêu chí | HashMap | LinkedHashMap | TreeMap | Hashtable | ConcurrentHashMap |
|---|---|---|---|---|---|
| Cấu trúc bên trong | Bucket array + linked list/tree | HashMap + linked list phụ (thứ tự) | Red-Black Tree | Bucket array + linked list | Bucket array + CAS + lock cấp bucket |
| Thứ tự | Không đảm bảo | Insertion hoặc access order | Sắp xếp theo key | Không đảm bảo | Không đảm bảo |
| `put`/`get` | O(1) trung bình | O(1) trung bình | O(log n) | O(1) trung bình (có lock) | O(1) trung bình (gần như không lock khi đọc) |
| Cho phép null | 1 null key, nhiều null value | Giống HashMap | Không cho null key | Không cho null key/value | Không cho null key/value |
| Thread-safe | Không | Không | Không | Có (khóa toàn bộ - chậm) | Có (khóa cấp bucket - nhanh) |
| Legacy? | Không | Không | Không | Có (JDK 1.0) | Không |

---

## 4. Ưu / Nhược điểm

### HashMap
- **Ưu điểm**: nhanh nhất trong các Map thông thường (O(1)), linh hoạt, cho phép null.
- **Nhược điểm**: không đảm bảo thứ tự, không thread-safe.

### LinkedHashMap
- **Ưu điểm**: giữ được thứ tự (chèn hoặc truy cập), vẫn nhanh gần bằng HashMap, nền tảng lý tưởng để cài LRU Cache.
- **Nhược điểm**: tốn thêm bộ nhớ (2 con trỏ mỗi entry), không thread-safe.

### TreeMap
- **Ưu điểm**: luôn sắp xếp theo key, hỗ trợ truy vấn range mạnh (`firstKey`, `ceilingKey`, `headMap`...).
- **Nhược điểm**: chậm hơn (O(log n)), không cho null key, không thread-safe.

### Hashtable
- **Ưu điểm**: thread-safe sẵn có, không cần wrapper.
- **Nhược điểm**: hiệu năng kém do lock thô toàn bộ bảng, là API legacy, không nên dùng cho code mới.

### ConcurrentHashMap
- **Ưu điểm**: thread-safe với hiệu năng cao (lock phân đoạn/bucket-level), có sẵn các method atomic tiện lợi (`compute`, `merge`, `computeIfAbsent`).
- **Nhược điểm**: không cho null key/value, phức tạp hơn 1 chút khi cần thao tác "toàn cục nhất quán" (VD: `size()` chỉ là ước lượng gần đúng tại 1 thời điểm khi có nhiều thread đang ghi).

---

## 5. Khi nào dùng loại nào? (Use case)

| Tình huống | Nên dùng |
|---|---|
| Lưu trữ key-value đơn giản, không cần thứ tự, đơn luồng | **HashMap** (mặc định) |
| Cần giữ thứ tự chèn để hiển thị lại đúng thứ tự (VD: cache cấu hình, JSON ordered) | **LinkedHashMap** (insertion order) |
| Cần cài đặt **LRU Cache** (loại bỏ phần tử ít dùng nhất khi đầy) | **LinkedHashMap** (access order = true) + override `removeEldestEntry()` |
| Cần dữ liệu luôn sắp xếp theo key, hoặc cần truy vấn khoảng (range, ceiling, floor) | **TreeMap** |
| Ứng dụng đa luồng, nhiều thread đọc/ghi đồng thời | **ConcurrentHashMap** (ưu tiên hơn hẳn Hashtable) |
| Code legacy bắt buộc tương thích API cũ | `Hashtable` (không khuyến khích cho code mới) |
| Cần đếm tần suất xuất hiện (word count, histogram) | **HashMap** (hoặc `ConcurrentHashMap` nếu đa luồng) |
| Map bất biến, không đổi sau khi tạo | **`Map.of(...)`** (immutable) |

---

## 6. Các bài toán / pattern thường gặp theo từng loại Map

### 6.1. HashMap — Đếm tần suất (Frequency Count)
```java
// Đếm số lần xuất hiện của mỗi ký tự trong chuỗi
Map<Character, Integer> countChars(String s) {
    Map<Character, Integer> freq = new HashMap<>();
    for (char c : s.toCharArray()) {
        freq.merge(c, 1, Integer::sum); // gọn hơn getOrDefault() + put()
    }
    return freq;
}
```

### 6.2. HashMap — Two Sum (bài toán kinh điển)
```java
int[] twoSum(int[] nums, int target) {
    Map<Integer, Integer> valueToIndex = new HashMap<>();
    for (int i = 0; i < nums.length; i++) {
        int complement = target - nums[i];
        if (valueToIndex.containsKey(complement)) {
            return new int[]{valueToIndex.get(complement), i};
        }
        valueToIndex.put(nums[i], i);
    }
    throw new IllegalArgumentException("No solution");
}
```

### 6.3. HashMap — Group Anagrams (nhóm các từ đồng dạng)
```java
Map<String, List<String>> groupAnagrams(String[] words) {
    Map<String, List<String>> groups = new HashMap<>();
    for (String word : words) {
        char[] chars = word.toCharArray();
        Arrays.sort(chars);
        String key = new String(chars); // dùng chuỗi đã sắp xếp làm key
        groups.computeIfAbsent(key, k -> new ArrayList<>()).add(word);
    }
    return groups;
}
```

### 6.4. LinkedHashMap — Cài đặt LRU Cache hoàn chỉnh
```java
class LRUCache<K, V> extends LinkedHashMap<K, V> {
    private final int capacity;

    LRUCache(int capacity) {
        super(capacity, 0.75f, true); // accessOrder = true -> LRU behavior
        this.capacity = capacity;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > capacity; // tự động xóa entry cũ nhất (ít dùng nhất) khi vượt capacity
    }
}

// Sử dụng
LRUCache<Integer, String> cache = new LRUCache<>(3);
cache.put(1, "A"); cache.put(2, "B"); cache.put(3, "C");
cache.get(1);          // "1" trở thành "mới nhất"
cache.put(4, "D");     // vượt capacity -> tự động xóa key ít dùng nhất (key 2, vì 1 vừa được get)
```

### 6.5. TreeMap — Range Query & "Closest Match"
```java
TreeMap<Integer, String> schedule = new TreeMap<>();
schedule.put(900, "Meeting A"); schedule.put(1300, "Meeting B"); schedule.put(1500, "Meeting C");

// Tìm sự kiện gần nhất sắp diễn ra sau thời điểm hiện tại
Map.Entry<Integer, String> nextEvent = schedule.ceilingEntry(1000); // -> (1300, "Meeting B")

// Lấy tất cả sự kiện trong khoảng thời gian
SortedMap<Integer, String> morningEvents = schedule.headMap(1200); // {900=Meeting A}
```

### 6.6. TreeMap — Bài toán "Đếm số khoảng bao phủ 1 điểm" (interval/sweep line)
```java
// Dùng TreeMap để cộng dồn sự kiện +1/-1 tại các mốc thời gian, sau đó duyệt tuần tự để tính số khoảng đang "active"
TreeMap<Integer, Integer> delta = new TreeMap<>();
for (int[] interval : intervals) {
    delta.merge(interval[0], 1, Integer::sum);   // bắt đầu khoảng -> +1
    delta.merge(interval[1], -1, Integer::sum);  // kết thúc khoảng -> -1
}
int active = 0, maxOverlap = 0;
for (int change : delta.values()) {
    active += change;
    maxOverlap = Math.max(maxOverlap, active);
}
```

### 6.7. ConcurrentHashMap — Đếm đa luồng an toàn (word count song song)
```java
ConcurrentHashMap<String, Integer> wordCount = new ConcurrentHashMap<>();

// Nhiều thread cùng gọi hàm này đồng thời vẫn an toàn, không cần lock thủ công
void countWord(String word) {
    wordCount.merge(word, 1, Integer::sum);
}
```

### 6.8. ConcurrentHashMap — Cache đơn giản với `computeIfAbsent` (tránh tính toán trùng lặp giữa các thread)
```java
ConcurrentHashMap<Integer, Long> fibCache = new ConcurrentHashMap<>();

long fib(int n) {
    if (n <= 1) return n;
    return fibCache.computeIfAbsent(n, k -> fib(k - 1) + fib(k - 2));
}
```

### 6.9. Bẫy thường gặp: sửa đổi Map trong lúc duyệt `entrySet()`
```java
Map<String, Integer> map = new HashMap<>(Map.of("a", 1, "b", 2, "c", 3));

// SAI - ConcurrentModificationException
for (String key : map.keySet()) {
    if (map.get(key) == 2) map.remove(key);
}

// ĐÚNG - dùng Iterator hoặc entrySet().removeIf()
map.entrySet().removeIf(entry -> entry.getValue() == 2);
```

---

## Câu hỏi tự kiểm tra
1. Trình bày từng bước cơ chế `put()` của `HashMap`: từ tính hash, xác định bucket, xử lý collision, đến khi nào treeify/resize.
2. Vì sao capacity của `HashMap` luôn là lũy thừa của 2? Điều này giúp tối ưu phép toán nào?
3. `LinkedHashMap` với `accessOrder = true` khác gì so với mặc định (`accessOrder = false`)? Cơ chế này giúp cài đặt thuật toán nào?
4. `TreeMap` dùng cấu trúc dữ liệu gì bên trong, và tại sao mọi thao tác đều là O(log n) thay vì O(1)?
5. So sánh cơ chế thread-safe giữa `Hashtable` và `ConcurrentHashMap` — vì sao `ConcurrentHashMap` nhanh hơn đáng kể?
6. Vì sao `ConcurrentHashMap` và `Hashtable` không cho phép key/value là `null`, trong khi `HashMap` cho phép?
7. Viết lại LRU Cache bằng `LinkedHashMap`, giải thích vai trò của việc override `removeEldestEntry()`.

## Bài tập luyện tập
- Cài đặt lại 1 phiên bản `MyHashMap<K,V>` đơn giản (mảng bucket + linked list, chưa cần treeify) để hiểu cơ chế bên trong.
- Giải bài toán "Group Anagrams" và "Two Sum" bằng `HashMap` (mục 6.2, 6.3).
- Cài đặt LRU Cache hoàn chỉnh bằng `LinkedHashMap`, viết test kiểm chứng đúng hành vi loại bỏ phần tử ít dùng nhất.
- Dùng `TreeMap` giải bài toán "Meeting Rooms II" (tìm số phòng họp tối thiểu cần thiết để không bị trùng lịch) bằng kỹ thuật sweep line ở mục 6.6.
- Viết chương trình đếm số từ trong 1 văn bản lớn bằng nhiều thread, so sánh kết quả và thời gian chạy giữa `HashMap` (không an toàn, có thể sai) và `ConcurrentHashMap` (an toàn).

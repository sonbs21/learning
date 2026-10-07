# List trong Java — Đào sâu chi tiết

> Đào sâu phần "List: ArrayList, LinkedList, Vector" trong [01-collections-framework.md](01-collections-framework.md).

---

## 1. List là gì? Vị trí trong kiến trúc

`List<E>` là interface con của `Collection<E>`, biểu diễn **danh sách có thứ tự (ordered), cho phép trùng lặp**, truy cập phần tử qua **chỉ số (index)**.

```
Collection
    │
   List  (ordered, indexed, cho phép duplicate)
    │
    ├── ArrayList
    ├── LinkedList
    ├── Vector
    │       └── Stack (extends Vector, legacy)
    └── CopyOnWriteArrayList (thread-safe, java.util.concurrent)
```

Đặc trưng của `List` so với `Set`:
- Có thứ tự, truy cập bằng index: `get(int index)`, `set(int index, E e)`, `indexOf(Object o)`.
- Cho phép phần tử trùng lặp (`Set` thì không).
- Cho phép nhiều `null` (tùy implementation).

---

## 2. Các loại List và kiến trúc bên trong

### 2.1. ArrayList — Mảng động (Dynamic Array)

**Kiến trúc bên trong:**
```java
// Rút gọn từ source code thật của ArrayList
public class ArrayList<E> {
    transient Object[] elementData; // mảng thực sự chứa dữ liệu
    private int size;               // số phần tử hiện có (size <= elementData.length)
    private static final int DEFAULT_CAPACITY = 10;
}
```

- Dữ liệu được lưu trong **1 mảng liên tục** (`Object[]`).
- `size` là số phần tử thực tế; `elementData.length` là **capacity** (sức chứa), luôn `>= size`.
- Khi mảng đầy và cần `add()` thêm → **resize (grow)**:

```java
// Công thức grow (JDK 8+): capacity mới = capacity cũ * 1.5
int newCapacity = oldCapacity + (oldCapacity >> 1); // oldCapacity + oldCapacity/2
elementData = Arrays.copyOf(elementData, newCapacity); // copy toàn bộ sang mảng mới
```

**Cơ chế `grow()` từng bước:**
1. Mảng đầy (size == capacity), gọi `add(e)`.
2. JVM cấp phát 1 mảng mới lớn hơn (x1.5 lần).
3. Copy toàn bộ phần tử cũ sang mảng mới (`System.arraycopy` — thao tác O(n) nhưng **amortized O(1)** vì không xảy ra thường xuyên).
4. Mảng cũ được Garbage Collector thu hồi.

```java
List<Integer> list = new ArrayList<>(); // capacity mặc định = 10 (thực ra rỗng, cấp phát lười lúc add() đầu tiên)
for (int i = 0; i < 15; i++) list.add(i); // sau lần add thứ 11, capacity tự tăng: 10 -> 15
```

**Insert/remove ở giữa hoặc đầu danh sách** đòi hỏi dịch chuyển (`System.arraycopy`) toàn bộ phần tử phía sau:
```java
list.add(0, "X"); // O(n): mọi phần tử phải dịch phải 1 vị trí
list.remove(0);    // O(n): mọi phần tử phải dịch trái 1 vị trí
```

### 2.2. LinkedList — Danh sách liên kết đôi (Doubly Linked List)

**Kiến trúc bên trong:**
```java
public class LinkedList<E> {
    transient Node<E> first; // con trỏ tới node đầu
    transient Node<E> last;  // con trỏ tới node cuối

    private static class Node<E> {
        E item;
        Node<E> next; // trỏ tới node kế tiếp
        Node<E> prev; // trỏ tới node trước đó
    }
}
```

- Mỗi phần tử là 1 **Node** riêng biệt (không liền kề trong bộ nhớ), chứa data + 2 con trỏ `prev`/`next`.
- `add()`/`remove()` ở **đầu hoặc cuối** là O(1) thực sự (chỉ cần đổi vài con trỏ, không dịch chuyển dữ liệu).
- Truy cập theo index (`get(i)`) phải **duyệt tuần tự** từ đầu hoặc cuối (tùy vị trí gần đầu/cuối hơn) → O(n).
- `LinkedList` implements cả `List` và `Deque` → dùng được như Queue/Stack 2 đầu.

```java
LinkedList<String> ll = new LinkedList<>();
ll.addFirst("A"); // O(1) - chỉ đổi con trỏ first
ll.addLast("B");  // O(1) - chỉ đổi con trỏ last
ll.get(0);         // O(1) vì gần đầu, nhưng get(size/2) sẽ là O(n/2)
```

### 2.3. Vector — ArrayList "phiên bản cũ" có khóa đồng bộ

- Cấu trúc bên trong **giống hệt ArrayList** (mảng động).
- Khác biệt: mọi method (`add`, `get`, `remove`,...) đều khai báo `synchronized` → an toàn với đa luồng nhưng **chậm hơn ArrayList** trong môi trường đơn luồng (tốn chi phí lock ngay cả khi không cần).
- Công thức grow mặc định: **x2** (gấp đôi capacity), khác với ArrayList (x1.5).
- Là lớp **legacy** (JDK 1.0, trước khi có Collections Framework) — hiện nay hầu như không dùng, thay bằng `Collections.synchronizedList(new ArrayList<>())` hoặc `CopyOnWriteArrayList`.

### 2.4. CopyOnWriteArrayList — thread-safe cho môi trường đọc nhiều, ghi ít

- Mỗi lần `add`/`remove`/`set` → **copy toàn bộ mảng ra 1 bản mới**, thao tác trên bản mới rồi thay thế con trỏ mảng cũ.
- Đọc (`get`, iterate) **không cần lock**, luôn đọc trên snapshot ổn định → fail-safe (không ném `ConcurrentModificationException`).
- Rất tốn kém nếu ghi nhiều (mỗi lần ghi là O(n) copy toàn bộ mảng).

```java
List<String> cowList = new CopyOnWriteArrayList<>();
// Phù hợp: danh sách listener/observer - thêm/xóa hiếm, duyệt (notify) thường xuyên
```

---

## 3. Bảng so sánh tổng hợp

| Tiêu chí | ArrayList | LinkedList | Vector | CopyOnWriteArrayList |
|---|---|---|---|---|
| Cấu trúc | Mảng động | Danh sách liên kết đôi | Mảng động | Mảng động (copy khi ghi) |
| `get(index)` | O(1) | O(n) | O(1) | O(1) |
| `add`/`remove` cuối | O(1) amortized | O(1) | O(1) amortized | O(n) |
| `add`/`remove` đầu/giữa | O(n) | O(1) nếu có node reference, O(n) nếu phải tìm | O(n) | O(n) |
| Bộ nhớ / phần tử | Thấp (chỉ data trong mảng) | Cao hơn (data + 2 con trỏ prev/next mỗi node) | Thấp | Thấp nhưng nhân đôi tạm thời khi ghi |
| Cache locality | Tốt (liền kề bộ nhớ) | Kém (node rải rác) | Tốt | Tốt |
| Thread-safe | Không | Không | Có (khóa toàn bộ, chậm) | Có (copy-on-write, đọc nhanh) |
| Iterator | Fail-fast | Fail-fast | Fail-fast | Fail-safe |
| Grow factor | x1.5 | Không cần (node động) | x2 | Copy toàn bộ khi ghi |

---

## 4. Ưu / Nhược điểm

### ArrayList
- **Ưu điểm**: truy cập ngẫu nhiên nhanh O(1), tiết kiệm bộ nhớ, cache-friendly (duyệt tuần tự rất nhanh nhờ tính liền kề bộ nhớ — CPU cache prefetching hiệu quả).
- **Nhược điểm**: chèn/xóa ở đầu hoặc giữa chậm O(n), resize tốn chi phí (dù amortized O(1)).

### LinkedList
- **Ưu điểm**: chèn/xóa ở đầu/cuối O(1) thực sự, không cần resize, phù hợp làm Queue/Deque.
- **Nhược điểm**: truy cập ngẫu nhiên chậm O(n), tốn bộ nhớ hơn (mỗi node có overhead 2 con trỏ + object header), cache locality kém (node rải rác trong Heap → nhiều cache miss khi duyệt).

### Vector
- **Ưu điểm**: thread-safe sẵn có (không cần wrap thêm).
- **Nhược điểm**: chậm hơn ArrayList do lock không cần thiết trong đơn luồng; là API legacy, không còn được khuyến khích dùng.

### CopyOnWriteArrayList
- **Ưu điểm**: đọc cực nhanh, không lock, an toàn tuyệt đối khi duyệt trong lúc có thread khác ghi.
- **Nhược điểm**: ghi rất tốn kém (copy toàn bộ mảng mỗi lần), không phù hợp nếu ghi thường xuyên hoặc list lớn.

---

## 5. Khi nào dùng loại nào? (Use case)

| Tình huống | Nên dùng |
|---|---|
| Cần truy cập ngẫu nhiên nhiều (`get(i)`, duyệt for thông thường), ít chèn/xóa ở giữa | **ArrayList** (mặc định nên chọn đầu tiên trừ khi có lý do khác) |
| Cần thêm/xóa liên tục ở đầu hoặc cuối danh sách (queue, stack, undo/redo history) | **LinkedList** hoặc tốt hơn là **ArrayDeque** (nếu không cần tính năng của `List`) |
| Cần danh sách thread-safe, ghi nhiều, đọc ít, không quan tâm hiệu năng cao | `Collections.synchronizedList(new ArrayList<>())` |
| Cần danh sách thread-safe, đọc rất nhiều (duyệt liên tục), ghi hiếm khi (VD: danh sách observer/listener, cấu hình ít thay đổi) | **CopyOnWriteArrayList** |
| Danh sách cố định, không bao giờ thay đổi sau khi tạo | **`List.of(...)`** (immutable, an toàn nhất) |
| Cần độ ưu tiên / sắp xếp tự động khi lấy ra | Không dùng List — dùng `PriorityQueue` hoặc `TreeSet` |
| Legacy code cần tương thích Java cũ, đa luồng | `Vector` (nhưng khuyến nghị thay bằng các lựa chọn hiện đại hơn ở trên) |

**Quy tắc thực dụng (rule of thumb)**: *"Mặc định dùng `ArrayList`. Chỉ chuyển sang `LinkedList`/`ArrayDeque` nếu bạn đã đo (benchmark) và xác nhận thao tác thêm/xóa ở đầu là bottleneck thực sự."* Trong thực tế, `ArrayList` gần như luôn nhanh hơn `LinkedList` kể cả trong nhiều trường hợp lý thuyết tưởng chừng `LinkedList` thắng, vì overhead object + cache miss của `LinkedList` thường lớn hơn lợi ích O(1) trên giấy.

---

## 6. Các bài toán / pattern thường gặp liên quan đến List

### 6.1. Duyệt và xóa an toàn (tránh `ConcurrentModificationException`)
```java
List<Integer> list = new ArrayList<>(List.of(1, 2, 3, 4, 5));

// SAI - ném ConcurrentModificationException
for (Integer n : list) {
    if (n % 2 == 0) list.remove(n);
}

// ĐÚNG - dùng Iterator.remove()
Iterator<Integer> it = list.iterator();
while (it.hasNext()) {
    if (it.next() % 2 == 0) it.remove();
}

// ĐÚNG - dùng removeIf (Java 8+, ngắn gọn nhất)
list.removeIf(n -> n % 2 == 0);
```

### 6.2. Two Pointers trên ArrayList (do random access O(1))
```java
// Đảo ngược list tại chỗ (in-place reverse)
void reverse(List<Integer> list) {
    int left = 0, right = list.size() - 1;
    while (left < right) {
        Collections.swap(list, left, right);
        left++; right--;
    }
}
```

### 6.3. Sliding Window trên List
```java
// Tìm tổng lớn nhất của k phần tử liên tiếp
int maxSumOfWindow(List<Integer> list, int k) {
    int windowSum = 0;
    for (int i = 0; i < k; i++) windowSum += list.get(i);
    int maxSum = windowSum;
    for (int i = k; i < list.size(); i++) {
        windowSum += list.get(i) - list.get(i - k); // trượt cửa sổ
        maxSum = Math.max(maxSum, windowSum);
    }
    return maxSum;
}
```

### 6.4. Dùng LinkedList/ArrayDeque để cài đặt Stack/Queue (bài toán kinh điển)
```java
// LRU Cache đơn giản hóa bằng LinkedHashMap (dùng danh sách liên kết theo access-order)
class LRUCache<K, V> extends LinkedHashMap<K, V> {
    private final int capacity;
    LRUCache(int capacity) {
        super(capacity, 0.75f, true); // accessOrder = true
        this.capacity = capacity;
    }
    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > capacity; // tự động xóa phần tử cũ nhất khi vượt capacity
    }
}
```

### 6.5. Sao chép danh sách (shallow copy) và các cách khởi tạo
```java
List<Integer> original = List.of(1, 2, 3);

List<Integer> copy1 = new ArrayList<>(original);       // copy constructor
List<Integer> copy2 = List.copyOf(original);            // immutable copy (Java 10+)
List<Integer> copy3 = original.stream().collect(Collectors.toList()); // qua Stream
```
> Lưu ý: đây đều là **shallow copy** — nếu phần tử là object (không phải primitive/String), copy chỉ sao chép reference, object bên trong vẫn dùng chung.

### 6.6. Chuyển đổi giữa List và Array
```java
// List -> Array
List<String> list = List.of("a", "b", "c");
String[] arr = list.toArray(new String[0]);

// Array -> List
String[] arr2 = {"x", "y", "z"};
List<String> fixedSizeList = Arrays.asList(arr2); // CẢNH BÁO: fixed-size, không add/remove được (chỉ set được)
List<String> mutableList = new ArrayList<>(Arrays.asList(arr2)); // list có thể sửa đổi tự do
```
> **Bẫy thường gặp**: `Arrays.asList()` trả về 1 `List` có kích thước cố định (bọc trực tiếp quanh mảng gốc) — gọi `add()`/`remove()` sẽ ném `UnsupportedOperationException`.

### 6.7. Sắp xếp và tìm kiếm nhị phân
```java
List<Integer> list = new ArrayList<>(List.of(5, 2, 8, 1));
Collections.sort(list); // [1, 2, 5, 8]
int index = Collections.binarySearch(list, 5); // O(log n), list PHẢI đã sort trước
```

### 6.8. Loại bỏ phần tử trùng lặp nhưng giữ thứ tự
```java
List<Integer> withDuplicates = List.of(3, 1, 2, 3, 1, 4);
List<Integer> distinctKeepOrder = new ArrayList<>(new LinkedHashSet<>(withDuplicates));
// -> [3, 1, 2, 4] (giữ đúng thứ tự xuất hiện đầu tiên, dùng LinkedHashSet trung gian)
```

---

## Câu hỏi tự kiểm tra
1. Vì sao `ArrayList.add()` ở cuối được coi là "amortized O(1)" thay vì O(1) tuyệt đối?
2. Trình bày công thức grow của `ArrayList` và `Vector` — vì sao 2 class này chọn hệ số grow khác nhau (1.5x vs 2x)?
3. Vì sao `LinkedList` có `get(i)` là O(1) khi `i` gần đầu/cuối nhưng O(n) khi `i` ở giữa danh sách?
4. Trong đa số benchmark thực tế, `ArrayList` thường nhanh hơn `LinkedList` kể cả khi thêm/xóa ở đầu danh sách với số lượng phần tử vừa phải. Giải thích vì sao (gợi ý: cache locality, object overhead).
5. `CopyOnWriteArrayList` phù hợp với tình huống nào? Vì sao không nên dùng nó cho danh sách bị ghi liên tục?
6. Bẫy của `Arrays.asList()` là gì? Làm sao để có 1 `List` thực sự có thể `add`/`remove` từ 1 mảng có sẵn?
7. Viết lại đoạn code loại bỏ phần tử chẵn khỏi `List<Integer>` bằng 3 cách: for-each thường (lỗi), `Iterator.remove()`, và `removeIf()`.

## Bài tập luyện tập
- Cài đặt lại 1 phiên bản `MyArrayList<T>` đơn giản (mảng động tự resize) để hiểu cơ chế grow.
- Cài đặt 1 `MyLinkedList<T>` đơn giản (doubly linked list) với `addFirst`, `addLast`, `get(index)`.
- Viết benchmark so sánh thời gian chạy giữa `ArrayList` và `LinkedList` cho 3 thao tác: `add` cuối, `add` đầu, `get(i)` ngẫu nhiên — với 100,000 phần tử.
- Giải bài toán "Rotate Array" (xoay mảng k vị trí) chỉ dùng `List` và `Collections`.
- Cài đặt LRU Cache hoàn chỉnh dùng `LinkedHashMap` (như ví dụ 6.4), viết test kiểm chứng phần tử cũ nhất bị loại bỏ đúng khi vượt capacity.

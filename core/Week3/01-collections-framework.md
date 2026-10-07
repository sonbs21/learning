# Tuần 3: Collections Framework

> Tài liệu tổng hợp kiến thức mục 3 trong lộ trình `java-core-roadmap.md`.

---

## 1. Kiến trúc tổng thể

```
                     Iterable
                        │
                   Collection
        ┌───────────────┼───────────────┐
        │               │               │
       List            Set            Queue
        │               │               │
 ArrayList        HashSet          PriorityQueue
 LinkedList       LinkedHashSet    ArrayDeque
 Vector           TreeSet          LinkedList (cũng implements Queue)
                     (SortedSet)      │
                                     Deque
                                   ArrayDeque, LinkedList

                        Map   (KHÔNG extends Collection — độc lập)
        ┌───────────────┼───────────────┐
     HashMap       LinkedHashMap     TreeMap (SortedMap)
        │
    Hashtable, ConcurrentHashMap
```

### Điểm mấu chốt: Vì sao `Map` không extends `Collection`?
- `Collection` biểu diễn **1 nhóm phần tử đơn** (single elements): `add(E e)`.
- `Map` biểu diễn **ánh xạ key-value** (pairs): `put(K key, V value)` — ngữ nghĩa hoàn toàn khác, không thể thống nhất API `add()` cho cả 2. Vì vậy `Map` là interface độc lập, chỉ nằm chung "họ" Collections Framework chứ không kế thừa `Collection`.

---

## 2. List: ArrayList, LinkedList, Vector

| | Cấu trúc bên trong | `get(i)` | `add`/`remove` đầu/giữa | `add` cuối | Thread-safe? |
|---|---|---|---|---|---|
| **ArrayList** | Mảng động (dynamic array), tự động `grow()` (thường x1.5) khi đầy | O(1) | O(n) (phải dịch chuyển phần tử) | O(1) amortized | Không |
| **LinkedList** | Danh sách liên kết đôi (doubly linked list), mỗi node có `prev`/`next` | O(n) (phải duyệt từ đầu/cuối) | O(1) nếu đã có reference tới node, O(n) nếu phải tìm vị trí trước | O(1) | Không |
| **Vector** | Giống ArrayList (mảng động) | O(1) | O(n) | O(1) amortized | **Có** (mọi method đều `synchronized`, nên chậm hơn ArrayList) |

```java
List<String> arrayList = new ArrayList<>();
List<String> linkedList = new LinkedList<>();

arrayList.add("A"); arrayList.add("B");
arrayList.add(1, "X"); // chèn giữa -> ArrayList phải dịch chuyển phần tử phía sau

LinkedList<String> ll = new LinkedList<>();
ll.addFirst("Head"); ll.addLast("Tail"); // LinkedList tận dụng thế mạnh 2 đầu, dùng làm Deque tốt
```

> **Vector vs ArrayList**: Vector là "phiên bản cũ" (legacy, JDK 1.0) đã bị `synchronized` toàn bộ → hiệu năng kém hơn ArrayList trong môi trường đơn luồng. Ngày nay hầu như không dùng Vector, thay bằng `Collections.synchronizedList()` hoặc `CopyOnWriteArrayList` khi cần thread-safe.

---

## 3. Set: HashSet, LinkedHashSet, TreeSet

| | Cấu trúc bên trong | Thứ tự phần tử | Độ phức tạp add/contains |
|---|---|---|---|
| **HashSet** | Bọc quanh `HashMap` (mỗi phần tử là 1 key, value là hằng số `PRESENT`) | Không đảm bảo thứ tự | O(1) trung bình |
| **LinkedHashSet** | `HashSet` + danh sách liên kết đôi để giữ thứ tự chèn | Giữ đúng thứ tự **insertion order** | O(1) trung bình |
| **TreeSet** | Cây đỏ-đen (Red-Black Tree) — cài đặt của `NavigableSet`/`SortedSet` | Luôn sắp xếp theo thứ tự tự nhiên hoặc `Comparator` | O(log n) |

```java
Set<Integer> hashSet = new HashSet<>(List.of(3, 1, 2)); // thứ tự không đảm bảo khi in ra
Set<Integer> linkedSet = new LinkedHashSet<>(List.of(3, 1, 2)); // in ra đúng: 3, 1, 2
Set<Integer> treeSet = new TreeSet<>(List.of(3, 1, 2)); // in ra: 1, 2, 3 (đã sort)
```

### Cơ chế hash trong HashSet/HashMap
- Mỗi object được tính `hashCode()` → xác định **bucket** (ngăn chứa) trong mảng nội bộ.
- Nhiều object có cùng bucket (hash collision) được lưu dưới dạng **linked list** trong bucket đó.
- **Từ Java 8**: nếu 1 bucket có ≥ 8 phần tử (và bảng có ≥ 64 bucket) → linked list được chuyển thành **cây đỏ-đen (treeify)** để tra cứu từ O(n) xuống O(log n) trong trường hợp collision nhiều.

---

## 4. Map: HashMap, LinkedHashMap, TreeMap, Hashtable, ConcurrentHashMap

### HashMap — cơ chế bucket & treeify
```
put(key, value):
  1. Tính hash(key) -> xác định index bucket = hash & (capacity - 1)
  2. Nếu bucket trống -> tạo Node mới
  3. Nếu bucket đã có phần tử (collision) -> so sánh key bằng equals(),
     nếu trùng key -> ghi đè value; nếu không trùng -> thêm vào linked list/tree của bucket đó
  4. Nếu số phần tử trong bucket >= 8 (TREEIFY_THRESHOLD) và capacity bảng >= 64
     -> chuyển linked list thành Red-Black Tree (treeify) để tăng tốc tra cứu
  5. Nếu tổng số phần tử vượt (capacity * loadFactor) (mặc định 0.75)
     -> resize (thường x2) và rehash toàn bộ (gọi là "rehashing")
```

| | Thứ tự | Cho phép null | Thread-safe |
|---|---|---|---|
| **HashMap** | Không đảm bảo | 1 null key, nhiều null value | Không |
| **LinkedHashMap** | Giữ insertion order (hoặc access order nếu cấu hình) | Giống HashMap | Không |
| **TreeMap** | Sắp xếp theo key (`Comparable`/`Comparator`) | Không cho null key (NPE) | Không |
| **Hashtable** | Không đảm bảo | Không cho null key/value | Có (`synchronized` toàn bộ, legacy) |
| **ConcurrentHashMap** | Không đảm bảo | Không cho null key/value | Có (lock phân đoạn — segment/bucket-level, hiệu năng tốt hơn Hashtable nhiều) |

```java
Map<String, Integer> map = new HashMap<>();
map.put("a", 1);
map.put("a", 2); // ghi đè giá trị cũ vì trùng key

Map<String, Integer> linkedMap = new LinkedHashMap<>(); // giữ thứ tự insert
Map<String, Integer> treeMap = new TreeMap<>();          // tự sắp xếp theo key
```

> **Lưu ý quan trọng**: key trong `HashMap`/`HashSet` phải override đúng `equals()`/`hashCode()` (xem [Week2](../Week2/01-huong-doi-tuong-oop.md)) — nếu không, object trùng logic vẫn bị coi là khác nhau.

---

## 5. Queue/Deque: PriorityQueue, ArrayDeque, LinkedList làm Queue

### Queue (FIFO — First In First Out)
```java
Queue<Integer> queue = new LinkedList<>();
queue.offer(1); queue.offer(2); queue.offer(3);
queue.poll(); // lấy ra và xóa phần tử đầu -> 1
queue.peek(); // xem phần tử đầu mà không xóa -> 2
```

### PriorityQueue — hàng đợi ưu tiên
- Cài đặt bằng **binary heap** (mặc định min-heap: phần tử nhỏ nhất luôn ở đầu).
- Không đảm bảo thứ tự FIFO thông thường, phần tử được lấy ra theo độ ưu tiên (`Comparable`/`Comparator`).

```java
PriorityQueue<Integer> pq = new PriorityQueue<>(); // min-heap mặc định
pq.offer(5); pq.offer(1); pq.offer(3);
pq.poll(); // -> 1 (nhỏ nhất)

PriorityQueue<Integer> maxHeap = new PriorityQueue<>(Comparator.reverseOrder());
```

### Deque (Double-Ended Queue) — thao tác được cả 2 đầu
```java
Deque<Integer> deque = new ArrayDeque<>();
deque.addFirst(1); deque.addLast(2);
deque.removeFirst(); deque.removeLast();

// ArrayDeque có thể dùng làm Stack (thay cho class Stack cũ, hiệu năng tốt hơn)
Deque<Integer> stack = new ArrayDeque<>();
stack.push(1); stack.push(2);
stack.pop(); // -> 2 (LIFO)
```
- **ArrayDeque** thường được ưu tiên hơn `LinkedList` khi dùng làm Queue/Stack thuần túy vì không có overhead của node objects, cache-friendly hơn (mảng liền kề trong bộ nhớ).

---

## 6. Comparable vs Comparator

| | Comparable | Comparator |
|---|---|---|
| Vị trí định nghĩa | Ngay trong class đối tượng (`implements Comparable<T>`) | Class riêng biệt hoặc lambda, tách khỏi đối tượng |
| Method | `compareTo(T o)` | `compare(T o1, T o2)` |
| Số lượng cách sắp xếp | Chỉ 1 (thứ tự "tự nhiên" mặc định) | Nhiều (tùy ý định nghĩa nhiều Comparator khác nhau) |
| Khi nào dùng | Khi đối tượng có 1 thứ tự tự nhiên rõ ràng (VD: số, ngày tháng) | Khi cần nhiều tiêu chí sắp xếp khác nhau, hoặc không sửa được class gốc |

```java
class Person implements Comparable<Person> {
    String name;
    int age;

    @Override
    public int compareTo(Person other) { // thứ tự tự nhiên: theo tuổi tăng dần
        return Integer.compare(this.age, other.age);
    }
}

List<Person> people = new ArrayList<>();
Collections.sort(people); // dùng Comparable mặc định

// Comparator: sắp xếp theo tiêu chí khác, không cần sửa class Person
people.sort(Comparator.comparing((Person p) -> p.name));
people.sort(Comparator.comparingInt((Person p) -> p.age).reversed());

// Kết hợp nhiều tiêu chí (sort theo age trước, name sau nếu age bằng nhau)
people.sort(Comparator.comparingInt((Person p) -> p.age)
                       .thenComparing(p -> p.name));
```

---

## 7. Iterator, ListIterator, Fail-fast vs Fail-safe

### Iterator — duyệt và xóa an toàn
```java
Iterator<String> it = list.iterator();
while (it.hasNext()) {
    String item = it.next();
    if (item.equals("X")) {
        it.remove(); // xóa an toàn qua Iterator, không gây lỗi
    }
}
```

### ListIterator — mở rộng của Iterator, chỉ dùng cho List
Cho phép duyệt **2 chiều** (`hasNext`/`hasPrevious`) và **sửa/thêm phần tử** khi đang duyệt (`set()`, `add()`).

```java
ListIterator<String> lit = list.listIterator();
while (lit.hasNext()) {
    String item = lit.next();
    lit.set(item.toUpperCase()); // sửa phần tử tại vị trí hiện tại
}
```

### Fail-fast vs Fail-safe

| | Fail-fast | Fail-safe |
|---|---|---|
| Đại diện | `ArrayList`, `HashMap`, `HashSet` (collections thường) | `CopyOnWriteArrayList`, `ConcurrentHashMap` (concurrent collections) |
| Cơ chế | Dùng biến đếm `modCount`; nếu phát hiện collection bị sửa đổi cấu trúc (thêm/xóa) trong lúc đang duyệt bằng Iterator (không qua chính Iterator đó) → ném `ConcurrentModificationException` ngay lập tức | Duyệt trên **bản sao** (snapshot) của dữ liệu tại thời điểm tạo iterator, không bị ảnh hưởng bởi thay đổi đang diễn ra, không ném exception |
| Đánh đổi | An toàn phát hiện lỗi sớm, nhưng không dùng được trong môi trường đa luồng sửa đổi đồng thời | An toàn đa luồng, nhưng dữ liệu đọc được có thể "cũ" (không phản ánh thay đổi mới nhất), tốn thêm bộ nhớ để copy |

```java
List<String> list = new ArrayList<>(List.of("A", "B", "C"));
for (String s : list) {
    if (s.equals("B")) {
        list.remove(s); // ConcurrentModificationException! (sửa trực tiếp trong for-each)
    }
}
```

---

## 8. Collections Utility Class

`java.util.Collections` chứa các static method thao tác trên collection:

```java
List<Integer> list = new ArrayList<>(List.of(3, 1, 2));

Collections.sort(list);                    // [1, 2, 3]
Collections.reverse(list);                 // [3, 2, 1]
Collections.max(list); Collections.min(list);
Collections.shuffle(list);                 // xáo trộn ngẫu nhiên
Collections.frequency(list, 2);            // đếm số lần xuất hiện

List<Integer> unmodifiable = Collections.unmodifiableList(list); // view chỉ đọc (vẫn đổi được nếu sửa list gốc)
List<Integer> syncList = Collections.synchronizedList(new ArrayList<>()); // thread-safe wrapper (phải tự synchronized khi duyệt)
```

---

## 9. Immutable Collections

### `List.of()`, `Set.of()`, `Map.of()` (Java 9+)
Tạo collection **thực sự bất biến** — mọi thao tác sửa đổi (`add`, `remove`, `set`) đều ném `UnsupportedOperationException`.

```java
List<Integer> immutableList = List.of(1, 2, 3);
immutableList.add(4); // UnsupportedOperationException

Map<String, Integer> immutableMap = Map.of("a", 1, "b", 2);
```

### `Collections.unmodifiableList()` — chỉ là 1 "view" chỉ đọc
```java
List<Integer> original = new ArrayList<>(List.of(1, 2, 3));
List<Integer> readOnlyView = Collections.unmodifiableList(original);

readOnlyView.add(4);   // UnsupportedOperationException
original.add(4);        // OK! và readOnlyView cũng "thấy" thay đổi này (vì chỉ là view, không phải copy)
```

> **Khác biệt cốt lõi**: `List.of()` tạo ra collection bất biến độc lập hoàn toàn. `Collections.unmodifiableList()` chỉ bọc (wrap) 1 view chỉ đọc quanh list gốc — nếu list gốc còn bị sửa ở nơi khác, view vẫn phản ánh thay đổi đó (không thực sự immutable).

---

## Câu hỏi tự kiểm tra
1. Vì sao `Map` không extends `Collection` trong Java Collections Framework?
2. So sánh độ phức tạp `get(i)` và `add` ở giữa danh sách giữa `ArrayList` và `LinkedList`. Khi nào nên chọn cái nào?
3. Giải thích cơ chế "treeify" trong `HashMap` từ Java 8: khi nào xảy ra và mang lại lợi ích gì?
4. `TreeSet`/`TreeMap` dùng cấu trúc dữ liệu gì bên trong? Độ phức tạp truy vấn là bao nhiêu?
5. Khi nào nên dùng `Comparable`, khi nào nên dùng `Comparator`? Có thể dùng cả 2 cùng lúc trên 1 class không?
6. Fail-fast và fail-safe khác nhau như thế nào? Cho ví dụ code minh họa `ConcurrentModificationException`.
7. `List.of()` và `Collections.unmodifiableList()` khác nhau ở điểm cốt lõi nào?

## Bài tập luyện tập
- Viết chương trình benchmark so sánh thời gian `add` ở đầu danh sách giữa `ArrayList` và `LinkedList` với 100,000 phần tử.
- Cài đặt 1 `HashMap` đơn giản bằng tay (mảng bucket + linked list) để hiểu cơ chế hash/collision.
- Viết chương trình dùng `PriorityQueue` để tìm k phần tử lớn nhất trong 1 mảng.
- Viết ví dụ minh họa lỗi `ConcurrentModificationException` khi xóa phần tử trong for-each, sau đó sửa lại bằng `Iterator.remove()`.
- So sánh hiệu năng `Collections.synchronizedList()` và `CopyOnWriteArrayList` khi có nhiều thread đọc/ghi đồng thời.

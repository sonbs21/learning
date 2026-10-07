# Set trong Java — Đào sâu chi tiết

> Đào sâu phần "Set: HashSet, LinkedHashSet, TreeSet" trong [01-collections-framework.md](01-collections-framework.md).

---

## 1. Set là gì? Vị trí trong kiến trúc

`Set<E>` là interface con của `Collection<E>`, biểu diễn **tập hợp không cho phép phần tử trùng lặp** (mỗi phần tử chỉ xuất hiện tối đa 1 lần, xác định "trùng" dựa trên `equals()`/`hashCode()`).

```
Collection
    │
   Set  (không duplicate)
    │
    ├── HashSet
    │      └── LinkedHashSet (extends HashSet)
    ├── SortedSet (interface con)
    │      └── NavigableSet (interface con)
    │             └── TreeSet
    └── CopyOnWriteArraySet (thread-safe, java.util.concurrent)
    └── ConcurrentSkipListSet (thread-safe, sorted, java.util.concurrent)
```

Khác biệt cốt lõi với `List`:
- Không có index, không có `get(int index)`.
- Không cho phần tử trùng: `add(e)` trả về `false` nếu `e` đã tồn tại (theo `equals()`).
- Một số implementation có thứ tự (LinkedHashSet, TreeSet), một số thì không (HashSet).

---

## 2. Các loại Set và kiến trúc bên trong

### 2.1. HashSet — bọc quanh HashMap

**Kiến trúc bên trong:** `HashSet` **không** có cấu trúc riêng — nó chỉ là 1 lớp "vỏ" (wrapper) bọc quanh `HashMap<E, Object>`.

```java
// Rút gọn từ source code thật của HashSet
public class HashSet<E> {
    private transient HashMap<E, Object> map;
    private static final Object PRESENT = new Object(); // giá trị "dummy" dùng chung cho mọi key

    public boolean add(E e) {
        return map.put(e, PRESENT) == null; // e trở thành KEY của map, value luôn là PRESENT
    }
}
```

Vì bản chất là `HashMap`, `HashSet` thừa hưởng toàn bộ cơ chế của `HashMap`:

**Cơ chế hash — bucket — collision — treeify (giống hệt HashMap):**
```
add(element):
  1. Tính hash(element) = element.hashCode() ^ (h >>> 16)  // trộn thêm bit cao để giảm collision
  2. Xác định index bucket = hash & (capacity - 1)          // capacity luôn là lũy thừa của 2
  3. Nếu bucket trống -> đặt element vào bucket đó
  4. Nếu bucket đã có phần tử (collision):
       - So sánh bằng equals() với từng phần tử trong bucket
       - Nếu trùng -> add() trả về false (không thêm)
       - Nếu không trùng -> thêm vào cuối linked list của bucket
  5. Nếu 1 bucket có >= 8 phần tử VÀ capacity bảng >= 64
       -> chuyển linked list trong bucket đó thành Red-Black Tree (treeify)
       -> tra cứu trong bucket đó từ O(n) giảm còn O(log n)
  6. Nếu tổng phần tử > capacity * loadFactor (mặc định 0.75)
       -> resize (x2) toàn bộ bảng và rehash lại mọi phần tử
```

- **Độ phức tạp trung bình**: `add`/`contains`/`remove` là **O(1)** (giả sử hash phân bố đều, ít collision).
- **Trường hợp xấu nhất**: nếu mọi phần tử hash trùng bucket (VD: `hashCode()` bị lỗi trả về hằng số) → suy biến thành linked list O(n), hoặc sau treeify thì O(log n).
- **Không đảm bảo thứ tự** khi duyệt — thứ tự phụ thuộc vào giá trị hash và trạng thái bảng nội bộ, có thể thay đổi giữa các lần chạy chương trình.

```java
Set<String> set = new HashSet<>();
set.add("banana"); set.add("apple"); set.add("cherry");
System.out.println(set); // thứ tự in ra KHÔNG đảm bảo, VD: [banana, cherry, apple]
```

### 2.2. LinkedHashSet — HashSet + thứ tự chèn

**Kiến trúc bên trong:** `LinkedHashSet extends HashSet`, nhưng bên trong dùng `LinkedHashMap` thay vì `HashMap`.

```java
public class LinkedHashSet<E> extends HashSet<E> {
    // constructor gọi super(...) với 1 flag đặc biệt để HashSet dùng LinkedHashMap thay vì HashMap
}
```

- `LinkedHashMap` = `HashMap` + **1 danh sách liên kết đôi (doubly linked list) phụ** xuyên suốt các Entry, dùng để ghi nhớ **thứ tự chèn (insertion order)**.
- Mỗi khi `add()` 1 phần tử mới, nó được thêm vào bucket (như HashMap bình thường) **đồng thời** được nối vào cuối danh sách liên kết phụ này.
- Khi duyệt (`iterator()`), `LinkedHashSet` duyệt theo **danh sách liên kết phụ** (theo đúng thứ tự chèn) chứ không duyệt trực tiếp mảng bucket.
- Độ phức tạp `add`/`contains` vẫn là O(1) (giống HashSet), chỉ tốn thêm bộ nhớ cho 2 con trỏ `before`/`after` mỗi Entry.

```java
Set<String> linkedSet = new LinkedHashSet<>();
linkedSet.add("banana"); linkedSet.add("apple"); linkedSet.add("cherry");
System.out.println(linkedSet); // luôn in đúng: [banana, apple, cherry] (thứ tự chèn)
```

### 2.3. TreeSet — Cây đỏ-đen (Red-Black Tree)

**Kiến trúc bên trong:** `TreeSet` bọc quanh `TreeMap<E, Object>` (tương tự cách HashSet bọc HashMap), và `TreeMap` cài đặt bằng **cây tìm kiếm nhị phân tự cân bằng — Red-Black Tree**.

```java
public class TreeSet<E> {
    private transient NavigableMap<E, Object> m; // thực chất là TreeMap
}
```

**Đặc điểm Red-Black Tree (tóm tắt):**
- Là cây nhị phân tìm kiếm (BST) có thêm quy tắc tô màu (đỏ/đen) để đảm bảo cây luôn **cân bằng** (chiều cao ~ O(log n)) — tránh suy biến thành linked list như BST thường.
- Mọi thao tác `add`, `remove`, `contains` đều là **O(log n)** — chậm hơn HashSet/O(1) nhưng đổi lại có được:
  - Thứ tự **luôn được sắp xếp** (theo `Comparable` tự nhiên hoặc `Comparator` truyền vào constructor).
  - Các thao tác "định vị" mạnh mẽ: `first()`, `last()`, `higher(e)`, `lower(e)`, `ceiling(e)`, `floor(e)`, `headSet(e)`, `tailSet(e)`, `subSet(from, to)`.

```java
TreeSet<Integer> treeSet = new TreeSet<>(List.of(5, 1, 8, 3));
System.out.println(treeSet);          // [1, 3, 5, 8] - luôn sắp xếp
treeSet.first();                       // 1 (nhỏ nhất)
treeSet.last();                        // 8 (lớn nhất)
treeSet.higher(3);                     // 5 (phần tử nhỏ nhất lớn hơn 3)
treeSet.lower(5);                      // 3 (phần tử lớn nhất nhỏ hơn 5)
treeSet.ceiling(4);                    // 5 (phần tử nhỏ nhất >= 4)
treeSet.floor(4);                      // 3 (phần tử lớn nhất <= 4)
treeSet.subSet(3, 8);                   // [3, 5] (từ 3 đến trước 8)

// Dùng Comparator tùy chỉnh
TreeSet<String> byLength = new TreeSet<>(Comparator.comparingInt(String::length));
```

> **Lưu ý**: `TreeSet` dùng `compareTo()`/`Comparator.compare()` để xác định "trùng lặp", **không dùng `equals()`**. Nếu `compareTo()` trả về 0 cho 2 object khác nhau, `TreeSet` sẽ coi chúng là trùng và chỉ giữ 1 phần tử — đây là điểm rất dễ gây bug nếu `compareTo()` không nhất quán với `equals()`.

### 2.4. CopyOnWriteArraySet & ConcurrentSkipListSet (thread-safe)

| | Cấu trúc | Đặc điểm |
|---|---|---|
| **CopyOnWriteArraySet** | Bọc quanh `CopyOnWriteArrayList`, dùng `contains()` (O(n)) để kiểm tra trùng trước khi add | Đọc nhanh không lock, ghi tốn kém (copy toàn mảng) — hợp danh sách nhỏ, đọc nhiều ghi ít |
| **ConcurrentSkipListSet** | Cấu trúc **Skip List** (danh sách liên kết nhiều tầng), tương tự vai trò của TreeSet nhưng thread-safe | O(log n), có thứ tự sắp xếp, an toàn đa luồng không cần lock toàn cục |

---

## 3. Bảng so sánh tổng hợp

| Tiêu chí | HashSet | LinkedHashSet | TreeSet |
|---|---|---|---|
| Cấu trúc bên trong | HashMap (bucket + linked list/tree khi collision) | LinkedHashMap (HashMap + linked list phụ giữ thứ tự) | TreeMap (Red-Black Tree) |
| Thứ tự phần tử | Không đảm bảo | Giữ đúng thứ tự chèn (insertion order) | Luôn sắp xếp (tự nhiên hoặc Comparator) |
| `add`/`contains`/`remove` | O(1) trung bình | O(1) trung bình | O(log n) |
| Cho phép `null` | 1 phần tử null | 1 phần tử null | Không (ném NPE khi so sánh) |
| Bộ nhớ overhead | Thấp nhất | Cao hơn HashSet (thêm 2 con trỏ/phần tử) | Cao hơn HashSet (node cây + màu) |
| Tiêu chí "trùng lặp" | `equals()`/`hashCode()` | `equals()`/`hashCode()` | `compareTo()`/`Comparator.compare()` |
| API đặc biệt | Không | Không | `first/last/higher/lower/ceiling/floor/subSet` |

---

## 4. Ưu / Nhược điểm

### HashSet
- **Ưu điểm**: nhanh nhất (O(1) trung bình), tiết kiệm bộ nhớ nhất trong 3 loại.
- **Nhược điểm**: không đảm bảo thứ tự — không phù hợp nếu cần thứ tự dự đoán được (kể cả để test/debug cũng khó vì output có thể khác nhau giữa các lần chạy).

### LinkedHashSet
- **Ưu điểm**: giữ được thứ tự chèn, vẫn nhanh gần bằng HashSet (O(1)), rất hữu ích khi cần loại bỏ trùng lặp **nhưng vẫn giữ thứ tự xuất hiện ban đầu**.
- **Nhược điểm**: tốn thêm bộ nhớ so với HashSet (2 con trỏ mỗi phần tử), chậm hơn 1 chút do phải duy trì thêm linked list.

### TreeSet
- **Ưu điểm**: luôn sắp xếp, hỗ trợ truy vấn khoảng/range mạnh mẽ (`subSet`, `ceiling`, `floor`...), rất hữu ích cho bài toán liên quan tới thứ tự/khoảng giá trị.
- **Nhược điểm**: chậm hơn (O(log n) thay vì O(1)), không cho phép `null`, yêu cầu phần tử phải `Comparable` hoặc truyền `Comparator`, dễ bug nếu `compareTo()` không nhất quán với `equals()`.

---

## 5. Khi nào dùng loại nào? (Use case)

| Tình huống | Nên dùng |
|---|---|
| Chỉ cần loại bỏ trùng lặp, không quan tâm thứ tự, ưu tiên tốc độ | **HashSet** (mặc định nên chọn đầu tiên) |
| Cần loại bỏ trùng lặp nhưng vẫn phải giữ đúng thứ tự xuất hiện (VD: xử lý log, giữ thứ tự người dùng nhập) | **LinkedHashSet** |
| Cần dữ liệu luôn ở trạng thái sắp xếp, hoặc cần truy vấn "phần tử gần nhất", "khoảng giá trị" (range query) | **TreeSet** |
| Cần tập hợp thread-safe, đọc nhiều/ghi ít (VD: danh sách listener) | **CopyOnWriteArraySet** |
| Cần tập hợp thread-safe **và** có thứ tự sắp xếp (VD: leaderboard cập nhật liên tục bởi nhiều thread) | **ConcurrentSkipListSet** |
| Cần kiểm tra nhanh "phần tử X đã tồn tại chưa" trong 1 dataset lớn, không cần duyệt theo thứ tự | **HashSet** |
| Cần tập hợp bất biến, không đổi sau khi tạo | **`Set.of(...)`** (immutable) |

---

## 6. Các bài toán / pattern thường gặp liên quan đến Set

### 6.1. Loại bỏ phần tử trùng lặp khỏi List
```java
List<Integer> withDuplicates = List.of(3, 1, 2, 3, 1, 4);

// Không giữ thứ tự (nhanh nhất)
Set<Integer> distinct = new HashSet<>(withDuplicates);

// Giữ đúng thứ tự xuất hiện đầu tiên
List<Integer> distinctKeepOrder = new ArrayList<>(new LinkedHashSet<>(withDuplicates));
```

### 6.2. Kiểm tra phần tử đã gặp chưa (Two Sum, tìm cặp trùng)
```java
// Bài toán: kiểm tra mảng có phần tử trùng lặp không - O(n) thay vì O(n^2)
boolean containsDuplicate(int[] nums) {
    Set<Integer> seen = new HashSet<>();
    for (int n : nums) {
        if (!seen.add(n)) return true; // add() trả về false nếu đã tồn tại
    }
    return false;
}

// Two Sum bằng HashSet: tìm 2 số có tổng = target
boolean hasPairWithSum(int[] nums, int target) {
    Set<Integer> seen = new HashSet<>();
    for (int n : nums) {
        if (seen.contains(target - n)) return true;
        seen.add(n);
    }
    return false;
}
```

### 6.3. Phép toán tập hợp: Union, Intersection, Difference
```java
Set<Integer> a = new HashSet<>(Set.of(1, 2, 3, 4));
Set<Integer> b = new HashSet<>(Set.of(3, 4, 5, 6));

// Union (hợp): {1,2,3,4,5,6}
Set<Integer> union = new HashSet<>(a);
union.addAll(b);

// Intersection (giao): {3,4}
Set<Integer> intersection = new HashSet<>(a);
intersection.retainAll(b);

// Difference (hiệu, a - b): {1,2}
Set<Integer> difference = new HashSet<>(a);
difference.removeAll(b);
```

### 6.4. Range Query với TreeSet (bài toán khoảng giá trị)
```java
// Tìm số nhỏ nhất trong tập hợp mà >= x (bài toán "ceiling")
TreeSet<Integer> events = new TreeSet<>(Set.of(10, 20, 30, 40));
Integer nextEvent = events.ceiling(25); // -> 30 (sự kiện gần nhất sau thời điểm 25)

// Đếm số phần tử trong khoảng [low, high]
int countInRange(TreeSet<Integer> set, int low, int high) {
    return set.subSet(low, true, high, true).size(); // inclusive cả 2 đầu
}
```

### 6.5. Set dùng để đánh dấu "đã thăm" (visited) trong DFS/BFS trên đồ thị
```java
Set<Integer> visited = new HashSet<>();
void dfs(int node, Map<Integer, List<Integer>> graph) {
    if (!visited.add(node)) return; // nếu đã thăm rồi thì dừng
    for (int neighbor : graph.getOrDefault(node, List.of())) {
        dfs(neighbor, graph);
    }
}
```

### 6.6. Bẫy: dùng object tự định nghĩa làm phần tử của HashSet mà quên override `equals()`/`hashCode()`
```java
class Point {
    int x, y;
    Point(int x, int y) { this.x = x; this.y = y; }
    // QUÊN override equals()/hashCode() -> dùng identity mặc định của Object
}

Set<Point> points = new HashSet<>();
points.add(new Point(1, 2));
points.add(new Point(1, 2)); // Set nghĩ đây là 2 object khác nhau (dù giá trị giống hệt)!
System.out.println(points.size()); // in ra 2, không phải 1 như mong đợi

// SỬA: phải override đúng equals() và hashCode() (xem Week2/01-huong-doi-tuong-oop.md)
```

### 6.7. Bẫy: dùng object có `compareTo()` không nhất quán với `equals()` trong TreeSet
```java
class Employee implements Comparable<Employee> {
    String name;
    double salary;

    @Override
    public int compareTo(Employee other) {
        return Double.compare(this.salary, other.salary); // chỉ so sánh theo salary
    }
    // equals() mặc định (identity) không liên quan gì tới salary
}

TreeSet<Employee> set = new TreeSet<>();
set.add(new Employee("An", 5000));
set.add(new Employee("Binh", 5000)); // cùng salary -> compareTo() trả về 0
System.out.println(set.size()); // in ra 1! "Binh" bị coi là trùng và không được thêm vào, dù equals() thực ra khác nhau
```
> Đây là lý do JDK khuyến nghị: `compareTo()` nên **nhất quán với `equals()`** (nếu `a.compareTo(b) == 0` thì nên có `a.equals(b) == true`), nếu không sẽ vi phạm hợp đồng của `Set`.

---

## Câu hỏi tự kiểm tra
1. Vì sao `HashSet` được nói là "chỉ là 1 HashMap với value giả (dummy)"? Giải thích qua cấu trúc `add()` bên trong.
2. `LinkedHashSet` giữ được thứ tự chèn bằng cơ chế nào? Có làm chậm đáng kể so với `HashSet` không?
3. `TreeSet` xác định 2 phần tử là "trùng nhau" dựa trên tiêu chí gì — `equals()` hay `compareTo()`? Hệ quả của việc này là gì nếu 2 tiêu chí không nhất quán?
4. Viết lại đoạn code kiểm tra mảng có phần tử trùng lặp hay không bằng cả 2 cách: dùng vòng lặp lồng nhau O(n²) và dùng `HashSet` O(n). So sánh.
5. Khi nào nên chọn `TreeSet` thay vì `HashSet` dù chậm hơn? Cho 1 ví dụ bài toán thực tế cần `ceiling()`/`floor()`.
6. Thực hiện phép `union`, `intersection`, `difference` giữa 2 `Set` bằng các method nào của `java.util.Set`?
7. Điều gì xảy ra nếu thêm 1 object tự định nghĩa vào `HashSet` mà quên override `equals()`/`hashCode()`? Vì sao `size()` lại lớn hơn mong đợi?

## Bài tập luyện tập
- Viết chương trình tìm các phần tử xuất hiện nhiều hơn 1 lần trong 1 mảng, dùng `HashSet`.
- Cài đặt lại 1 phiên bản `MyHashSet<T>` đơn giản (bọc quanh `HashMap` tự viết) để hiểu cơ chế bên trong.
- Viết chương trình dùng `TreeSet` để quản lý danh sách sự kiện theo thời gian, hỗ trợ truy vấn "sự kiện gần nhất sau thời điểm X".
- Viết class `Point` override đúng `equals()`/`hashCode()`, kiểm chứng `HashSet` loại bỏ đúng phần tử trùng lặp theo giá trị (không phải theo reference).
- Giải bài toán "Longest Consecutive Sequence" (tìm dãy số liên tiếp dài nhất trong mảng không sắp xếp) bằng `HashSet` với độ phức tạp O(n).

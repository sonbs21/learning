# Java `Set` — Toàn bộ kiến thức cần học

> Checklist này mở rộng phần `Set` trong [Collections Framework](../01-collections-framework.md).  
> Mục tiêu: hiểu tính chất, hợp đồng API, cơ chế bên trong, độ phức tạp, cách chọn implementation và các lỗi thường gặp.

---

## 1. Mục tiêu đầu ra

Sau khi học xong, cần có khả năng:

- [ ] Giải thích `Set` khác `List`, `Queue` và `Map` như thế nào.
- [ ] Hiểu tiêu chí xác định phần tử trùng của từng loại set.
- [ ] Dùng đúng API cơ bản và các phép toán tập hợp.
- [ ] Giải thích cơ chế `HashSet`, `LinkedHashSet`, `TreeSet` và `EnumSet`.
- [ ] Hiểu hashing, bucket, collision, load factor, resize và treeification.
- [ ] Sử dụng `SortedSet`/`NavigableSet` và range view.
- [ ] Phân biệt fail-fast, snapshot và weakly consistent iterator.
- [ ] Chọn đúng mutable, unmodifiable, immutable hoặc concurrent set.
- [ ] Tránh lỗi do mutable element, `equals/hashCode` hoặc comparator sai.
- [ ] Chọn implementation dựa trên thứ tự, hiệu năng và thread-safety.

---

## 2. Nền tảng: `Set` là gì?

```text
Iterable<E>
    └── Collection<E>
            └── Set<E>
                    ├── HashSet<E>
                    │       └── LinkedHashSet<E>
                    ├── SortedSet<E>
                    │       └── NavigableSet<E>
                    │               ├── TreeSet<E>
                    │               └── ConcurrentSkipListSet<E>
                    ├── EnumSet<E extends Enum<E>>
                    └── CopyOnWriteArraySet<E>
```

### 2.1. Các tính chất cốt lõi

- `Set<E>` là `Collection<E>` không chứa hai phần tử được coi là tương đương.
- Không có index, do đó không có `get(int index)`.
- `add(e)` trả về `true` nếu set thay đổi, `false` nếu phần tử đã tồn tại.
- Thứ tự duyệt và chính sách `null` phụ thuộc implementation.
- Các thao tác sửa là optional; set không sửa được có thể ném `UnsupportedOperationException`.
- Generic `E` giúp kiểm tra kiểu ở compile time.

```java
Set<String> tags = new HashSet<>();
tags.add("java");   // true
tags.add("spring"); // true
tags.add("java");   // false
```

### 2.2. Thứ tự của set

- `HashSet`: không cam kết thứ tự duyệt.
- `LinkedHashSet`: mặc định giữ thứ tự chèn.
- `TreeSet`: luôn sắp xếp theo natural order hoặc comparator.
- Unordered không có nghĩa là thứ tự bắt buộc thay đổi ở mỗi lần chạy; chỉ có nghĩa là không được phụ thuộc vào nó.

### 2.3. Tiêu chí xác định “trùng”

| Loại | Tiêu chí tương đương |
|---|---|
| `HashSet`, `LinkedHashSet` | `hashCode()` để tìm bucket, `equals()` để xác nhận |
| `TreeSet`, `ConcurrentSkipListSet` | `compareTo()` hoặc `Comparator.compare()` trả về `0` |
| `EnumSet` | Cùng hằng enum |
| `CopyOnWriteArraySet` | Equality khi tìm tuyến tính trong cấu trúc bên dưới |

---

## 3. Hợp đồng `equals()` và `hashCode()` của `Set`

Hai set bằng nhau khi đối tượng kia cũng là `Set`, có cùng kích thước và chứa cùng tập phần tử. Thứ tự và implementation không ảnh hưởng:

```java
Set<Integer> a = new HashSet<>(List.of(1, 2, 3));
Set<Integer> b = new LinkedHashSet<>(List.of(3, 2, 1));
Set<Integer> c = new TreeSet<>(List.of(2, 1, 3));

a.equals(b); // true
b.equals(c); // true
```

`hashCode()` của set là tổng hash code của các phần tử nên không phụ thuộc thứ tự.

### 3.1. Quy tắc với hash-based set

- Nếu `a.equals(b)` là `true`, hai object phải có cùng `hashCode()`.
- Cùng hash code chưa chắc bằng nhau; đó là collision hợp lệ.
- Khi override một method, gần như luôn phải override method còn lại.
- `record` tự sinh `equals/hashCode` từ các component và thường phù hợp cho value object.

### 3.2. Mutable element là một rủi ro lớn

Không thay đổi field tham gia `equals/hashCode` khi object đang nằm trong `HashSet`/`LinkedHashSet`:

```java
Set<User> users = new HashSet<>();
User user = new User(1, "An"); // id tham gia equals/hashCode
users.add(user);

user.setId(2);
users.contains(user); // có thể false dù reference vẫn nằm trong set
users.remove(user);   // có thể không tìm được bucket cũ để xóa
```

Ưu tiên immutable element hoặc giữ ổn định các field định danh.

---

## 4. API phải thành thạo

### 4.1. Thao tác cơ bản

| API | Ý nghĩa |
|---|---|
| `add(E e)` | Thêm nếu chưa tồn tại |
| `addAll(Collection)` | Thêm toàn bộ phần tử chưa có |
| `contains(Object o)` | Kiểm tra membership |
| `containsAll(Collection)` | Kiểm tra chứa toàn bộ collection khác |
| `remove(Object o)` | Xóa phần tử tương đương |
| `removeIf(Predicate)` | Xóa các phần tử thỏa điều kiện |
| `size()`, `isEmpty()` | Kích thước/trạng thái rỗng |
| `clear()` | Xóa toàn bộ |
| `iterator()`, `forEach()` | Duyệt set |

Giá trị trả về của `add` giúp phát hiện duplicate mà không cần gọi `contains` trước:

```java
if (!seen.add(value)) {
    System.out.println("Đã gặp: " + value);
}
```

### 4.2. Các phép toán tập hợp

| Toán học | Java |
|---|---|
| Hợp `A ∪ B` | `result.addAll(b)` |
| Giao `A ∩ B` | `result.retainAll(b)` |
| Hiệu `A \ B` | `result.removeAll(b)` |
| B là tập con của A | `a.containsAll(b)` |
| Hiệu đối xứng | `(A \ B) ∪ (B \ A)` |

Các method này thay đổi receiver. Tạo bản sao nếu cần giữ input:

```java
Set<Integer> intersection = new HashSet<>(a);
intersection.retainAll(b);
```

---

## 5. `HashSet` — hash table bọc quanh `HashMap`

### 5.1. Mô hình bên trong

Trong OpenJDK, `HashSet<E>` lưu phần tử như key của `HashMap<E, Object>`; các key dùng chung một dummy value nội bộ.

```text
HashSet.add(element)
        └── backingMap.put(element, PRESENT)
```

Đây là chi tiết implementation để học cơ chế, không phải API nên truy cập bằng reflection trong code thật.

### 5.2. Hash, bucket và collision

```text
add(element)
    ├── lấy hash từ element.hashCode()
    ├── phân phối hash để chọn bucket
    ├── bucket trống → thêm entry
    └── bucket đã có entry
            ├── hash/equality tương đương → không thêm, trả false
            └── khác → collision, lưu thêm trong bucket
```

- Collision là bình thường và khác duplicate.
- Hash tốt giúp phần tử phân bố đều.
- Hash tệ làm nhiều phần tử dồn vào một bucket và tăng số phép so sánh.

### 5.3. Treeification

Trong OpenJDK hiện đại, bucket có nhiều collision có thể chuyển từ linked structure sang balanced tree. Ngưỡng treeify/untreeify và điều kiện capacity là **chi tiết cài đặt**, không phải API contract.

Cần hiểu mục đích: tree bin hạn chế tác động của collision nghiêm trọng. Cách phòng ngừa chính vẫn là viết `hashCode()` tốt và nhất quán với `equals()`.

### 5.4. Capacity, load factor và resize

- `capacity`: số bucket của bảng nội bộ.
- `load factor`: mức đầy trước khi resize; mặc định thường là `0.75`.
- Vượt threshold làm bảng tăng capacity và phân phối lại entry.
- Capacity quá nhỏ gây nhiều resize; quá lớn lãng phí bộ nhớ và làm duyệt chậm.
- Chỉ tinh chỉnh `initialCapacity/loadFactor` khi biết trước dữ liệu hoặc đã đo.

### 5.5. Độ phức tạp

| Thao tác | Độ phức tạp trung bình |
|---|---|
| `add`, `contains`, `remove` | `O(1)` |
| Duyệt toàn bộ | Gần `O(size + capacity)` |

`O(1)` là trung bình khi hash phân bố tốt, không phải bảo đảm tuyệt đối cho mọi input.

### 5.6. Tính chất khác

- Cho phép tối đa một phần tử `null`.
- Không thread-safe.
- Iterator thường fail-fast trên best-effort basis.
- Không cam kết thứ tự duyệt.

---

## 6. `LinkedHashSet` — hashing cộng encounter order

- Kết hợp hash table với cấu trúc liên kết duy trì thứ tự.
- Mặc định duyệt theo thứ tự chèn.
- Thêm lại phần tử đã có bằng `add` không tự đổi vị trí của nó.
- `add`, `contains`, `remove` thường `O(1)` trung bình.
- Cho phép một `null`.
- Tốn thêm bộ nhớ để lưu liên kết thứ tự.
- Duyệt tỷ lệ với `size`, không phụ thuộc trực tiếp vào capacity như `HashSet`.

Use case chính: loại duplicate nhưng giữ thứ tự xuất hiện đầu tiên.

```java
List<Integer> distinct =
    new ArrayList<>(new LinkedHashSet<>(input));
```

Từ Java 21, `LinkedHashSet` thuộc `SequencedSet`, có view đảo chiều và API thao tác hai đầu. Kiểm tra JDK của dự án trước khi dùng.

---

## 7. `TreeSet`, `SortedSet` và `NavigableSet`

`TreeSet` thường được backing bởi `TreeMap` sử dụng Red-Black Tree.

### 7.1. Tính chất và độ phức tạp

- Luôn sắp xếp theo natural order hoặc `Comparator`.
- `add`, `contains`, `remove`: `O(log n)`.
- `first`, `last`, `floor`, `ceiling`, `lower`, `higher`: `O(log n)`.
- Không có random access theo index.
- Không thread-safe.

### 7.2. Comparator quyết định cả thứ tự lẫn tính duy nhất

Nếu comparator trả về `0`, `TreeSet` coi hai phần tử là trùng dù `equals()` trả về `false`:

```java
TreeSet<String> byLength = new TreeSet<>(
    Comparator.comparingInt(String::length)
);

byLength.add("An");
byLength.add("Vy"); // false vì cùng length
```

Thêm tie-breaker để tránh mất phần tử ngoài ý muốn:

```java
Comparator<String> byLengthThenText =
    Comparator.comparingInt(String::length)
              .thenComparing(Comparator.naturalOrder());
```

Comparator phải ổn định, phản đối xứng, bắc cầu và nên nhất quán với `equals()`.

### 7.3. Navigable API

| API | Kết quả |
|---|---|
| `lower(e)` | Phần tử lớn nhất `< e` |
| `floor(e)` | Phần tử lớn nhất `<= e` |
| `ceiling(e)` | Phần tử nhỏ nhất `>= e` |
| `higher(e)` | Phần tử nhỏ nhất `> e` |
| `first()`, `last()` | Phần tử đầu/cuối |
| `pollFirst()`, `pollLast()` | Lấy và xóa đầu/cuối |
| `descendingSet()` | View theo thứ tự đảo |

### 7.4. Range view

```java
NavigableSet<Integer> scores = new TreeSet<>(List.of(10, 20, 30, 40));

SortedSet<Integer> below30 = scores.headSet(30);       // < 30
SortedSet<Integer> from20 = scores.tailSet(20);        // >= 20
SortedSet<Integer> middle = scores.subSet(20, 40);     // [20, 40)
NavigableSet<Integer> closed = scores.subSet(20, true, 40, true);
```

Range set và `descendingSet()` là live view:

- thay đổi hợp lệ qua view phản ánh vào set gốc;
- thêm phần tử ngoài range qua view ném `IllegalArgumentException`;
- muốn bản độc lập, dùng `new TreeSet<>(view)`.

### 7.5. `null`

- Natural-order `TreeSet` không chấp nhận `null`.
- Comparator tùy chỉnh chỉ có thể xử lý `null` nếu chính comparator định nghĩa thứ tự cho nó.
- Thực tế nên tránh `null` trong sorted set.

---

## 8. `EnumSet` — set tối ưu cho enum

`EnumSet<E extends Enum<E>>` chỉ chứa constant của cùng một enum type và thường dùng bit vector.

- Rất gọn bộ nhớ và nhanh.
- Duyệt theo thứ tự khai báo enum.
- Không cho phép `null`.
- Không thread-safe.
- Iterator weakly consistent, không fail-fast.

```java
EnumSet<Day> workDays = EnumSet.range(Day.MONDAY, Day.FRIDAY);
EnumSet<Day> weekend = EnumSet.of(Day.SATURDAY, Day.SUNDAY);
EnumSet<Day> everyDay = EnumSet.allOf(Day.class);
EnumSet<Day> noDays = EnumSet.noneOf(Day.class);
EnumSet<Day> notWorkDays = EnumSet.complementOf(workDays);
```

Khi phần tử là enum, thường ưu tiên `EnumSet` hơn `HashSet<Enum>`.

---

## 9. Thread safety và concurrent set

### 9.1. `Collections.synchronizedSet`

```java
Set<String> synchronizedSet =
    Collections.synchronizedSet(new HashSet<>());

synchronized (synchronizedSet) {
    for (String value : synchronizedSet) {
        // Duyệt dưới monitor của wrapper
    }
}
```

- Đồng bộ các method riêng lẻ.
- Phải khóa ngoài khi duyệt theo hướng dẫn API.
- Chuỗi thao tác “kiểm tra rồi hành động” vẫn cần khóa ngoài nếu phải atomic.

### 9.2. `CopyOnWriteArraySet`

- Dựa trên `CopyOnWriteArrayList`.
- Search/kiểm tra duplicate tuyến tính.
- Mỗi lần ghi tạo bản sao cấu trúc bên dưới.
- Iterator là snapshot và không hỗ trợ mutation.
- Hợp với set nhỏ, đọc rất nhiều, ghi rất ít.

### 9.3. `ConcurrentHashMap.newKeySet()`

```java
Set<String> concurrentIds = ConcurrentHashMap.newKeySet();
```

- Concurrent, unordered, không cho `null`.
- Thích hợp cho membership concurrent tổng quát.
- Iterator weakly consistent.

### 9.4. `ConcurrentSkipListSet`

- Concurrent và luôn sắp xếp.
- Dựa trên skip list.
- `add`, `remove`, `contains` có expected average `O(log n)`.
- Có API `NavigableSet` và range view.
- Không cho `null`; iterator weakly consistent.

### 9.5. Ba cơ chế iterator

| Cơ chế | Ví dụ | Hành vi |
|---|---|---|
| Fail-fast | `HashSet`, `LinkedHashSet`, `TreeSet` | Có thể ném `ConcurrentModificationException`; best effort |
| Snapshot | `CopyOnWriteArraySet` | Duyệt bản chụp lúc tạo iterator |
| Weakly consistent | Concurrent key set, skip-list set, `EnumSet` | Không fail-fast; có thể thấy một phần thay đổi |

“Fail-safe” không phải thuật ngữ chính thức của Collections API. Fail-fast cũng không phải cơ chế thread safety.

---

## 10. Mutable, unmodifiable và immutable set

| Cách tạo | Sửa cấu trúc | Quan hệ với nguồn | `null` | Duplicate đầu vào |
|---|---|---|---|---|
| `new HashSet<>(source)` | Được | Shallow copy độc lập về cấu trúc | Cho phép | Tự loại trùng |
| `Collections.unmodifiableSet(source)` | Không qua view | Live read-only view | Theo source | Theo source |
| `Set.of(...)` | Không | Unmodifiable value-based set | Không | Ném `IllegalArgumentException` |
| `Set.copyOf(source)` | Không | Unmodifiable shallow copy | Không | Collection đầu vào được thu gọn |
| `Collections.singleton(e)` | Không | Set một phần tử | Có thể `null` | Không áp dụng |

### 10.1. Các điểm phải nhớ

- Không dựa vào iteration order của `Set.of`/`Set.copyOf`.
- `Collections.unmodifiableSet` không phải copy; nguồn đổi thì view đổi.
- Unmodifiable set không làm object phần tử trở thành immutable.
- `Set.copyOf` không phản ánh thay đổi cấu trúc về sau của source.
- Các cách trên đều chỉ là shallow copy/immutability ở mức container.

---

## 11. Bảng so sánh implementation

| Loại | Thứ tự | Thao tác chính | `null` | Thread-safe | Cơ chế |
|---|---|---:|---|---|---|
| `HashSet` | Không cam kết | `O(1)` trung bình | Một | Không | Hash table |
| `LinkedHashSet` | Thứ tự chèn | `O(1)` trung bình | Một | Không | Hash + linked order |
| `TreeSet` | Sorted | `O(log n)` | Tùy comparator; nên tránh | Không | Red-Black Tree |
| `EnumSet` | Thứ tự khai báo enum | Rất nhanh | Không | Không | Bit vector |
| `CopyOnWriteArraySet` | Encounter order | Search `O(n)`, write đắt | Có | Có | Copy-on-write array |
| `ConcurrentHashMap.newKeySet()` | Không cam kết | Gần `O(1)` trung bình | Không | Có | Concurrent hash table |
| `ConcurrentSkipListSet` | Sorted | Expected `O(log n)` | Không | Có | Skip list |

---

## 12. Cách chọn implementation

| Nhu cầu | Lựa chọn thường phù hợp |
|---|---|
| Membership/deduplication, không cần thứ tự | `HashSet` |
| Loại trùng nhưng giữ thứ tự đầu vào | `LinkedHashSet` |
| Luôn sắp xếp, cần nearest/range query | `TreeSet` |
| Phần tử là enum | `EnumSet` |
| Dữ liệu cố định, không `null` | `Set.of`/`Set.copyOf` |
| Concurrent, unordered | `ConcurrentHashMap.newKeySet()` |
| Concurrent, sorted | `ConcurrentSkipListSet` |
| Set nhỏ, concurrent, đọc rất nhiều/ghi rất ít | `CopyOnWriteArraySet` |

Cần xét cả thứ tự, số phần tử, tần suất đọc/ghi, chi phí hash/comparison, bộ nhớ, thread-safety và kết quả profiling — không chỉ Big-O.

---

## 13. Stream, sorting và chuyển đổi

### 13.1. Sorting

`Set` chung không có API sort tại chỗ. Dùng `TreeSet` nếu cần luôn sorted, hoặc copy sang `List` rồi sort:

```java
List<String> sorted = new ArrayList<>(set);
sorted.sort(Comparator.naturalOrder());
```

### 13.2. Stream collector

- Không giả định `Collectors.toSet()` trả về implementation, mutability hoặc order cụ thể.
- Cần giữ encounter order: `Collectors.toCollection(LinkedHashSet::new)`.
- `Collectors.toUnmodifiableSet()` không chấp nhận `null`.
- Không mutate source set trong lúc stream pipeline đang chạy.

### 13.3. Chuyển đổi

```java
Set<String> unique = new HashSet<>(list);
Set<String> orderedUnique = new LinkedHashSet<>(list);
List<String> values = new ArrayList<>(set);
String[] array = set.toArray(String[]::new); // Java 11+
```

---

## 14. Pattern thường gặp

- Loại duplicate khỏi `List`; dùng `LinkedHashSet` nếu cần giữ thứ tự.
- Membership lookup thay cho vòng lặp tuyến tính lặp lại.
- `visited` set trong DFS/BFS.
- Phát hiện duplicate bằng giá trị trả về của `add`.
- Two Sum và Longest Consecutive Sequence bằng `HashSet`.
- Permission/feature flags bằng `EnumSet`.
- Nearest value và range query bằng `TreeSet`.
- Union, intersection, difference và symmetric difference.

---

## 15. Lỗi và bẫy phải nhận ra

- [ ] Quên override đồng thời `equals()` và `hashCode()`.
- [ ] Mutate field tham gia equality/hashing sau khi thêm vào hash-based set.
- [ ] `hashCode()` trả hằng số, gây collision nghiêm trọng.
- [ ] Dựa vào thứ tự quan sát được của `HashSet`.
- [ ] Nghĩ `LinkedHashSet` tự sắp xếp thay vì giữ thứ tự chèn.
- [ ] Comparator trả `0` cho hai object cần giữ riêng trong `TreeSet`.
- [ ] Comparator không bắc cầu hoặc không ổn định.
- [ ] Dùng `null` với implementation không hỗ trợ.
- [ ] Nghĩ range set/descending set là copy độc lập.
- [ ] Thêm phần tử ngoài range qua range view.
- [ ] Gọi `retainAll/removeAll` trực tiếp làm thay đổi input.
- [ ] Sửa set trực tiếp trong enhanced `for`.
- [ ] Nghĩ fail-fast đồng nghĩa với thread-safe.
- [ ] Quên khóa khi duyệt `Collections.synchronizedSet`.
- [ ] Dùng `CopyOnWriteArraySet` cho set lớn hoặc ghi nhiều.
- [ ] Dùng `HashSet<Enum>` khi `EnumSet` phù hợp hơn.
- [ ] Phụ thuộc implementation/order của `Collectors.toSet()`.
- [ ] Nghĩ unmodifiable set làm phần tử bên trong immutable.
- [ ] Đặt initial capacity quá lớn làm chậm iteration.
- [ ] Phụ thuộc ngưỡng treeify/grow như một API contract.

---

## 16. Lộ trình học đề xuất

### Mức 1 — Cơ bản

- [ ] Unique, không index, thứ tự và `null` policy.
- [ ] `add`, `contains`, `remove`, `size`, iteration.
- [ ] Giá trị boolean trả về từ `add`.
- [ ] Union, intersection, difference, subset.
- [ ] `HashSet` vs `LinkedHashSet` vs `TreeSet`.

### Mức 2 — Làm việc thực tế

- [ ] Hợp đồng `equals/hashCode`.
- [ ] Bucket, collision, load factor và resize.
- [ ] `Comparable`, `Comparator`, `NavigableSet`.
- [ ] Range view và descending view.
- [ ] `Set.of`, `Set.copyOf`, unmodifiable wrapper.

### Mức 3 — Chuyên sâu

- [ ] Treeification là implementation detail.
- [ ] Mutable element problem.
- [ ] `EnumSet` và bit vector.
- [ ] Concurrent hash set, copy-on-write set, skip-list set.
- [ ] Fail-fast, snapshot, weakly consistent iterator.
- [ ] JMH benchmark và memory/performance trade-off.

### Mức 4 — Theo phiên bản Java

- [ ] Java 9+: `Set.of`.
- [ ] Java 10+: `Set.copyOf`.
- [ ] Java 21+: `SequencedSet` và API encounter order của `LinkedHashSet`.
- [ ] Kiểm tra JDK dự án trước khi dùng API mới.

---

## 17. Bài thực hành

1. Viết `Point` override đúng `equals/hashCode`, kiểm tra duplicate trong `HashSet`.
2. Mutate `Point` sau khi thêm và giải thích lỗi `contains/remove`.
3. Cài union, intersection, difference, symmetric difference mà không đổi input.
4. Loại trùng bằng `HashSet` và `LinkedHashSet`; so sánh thứ tự.
5. Tạo comparator sai cho `TreeSet`, tái hiện mất phần tử rồi sửa bằng tie-breaker.
6. Dùng `floor`, `ceiling`, `lower`, `higher` cho lịch sự kiện.
7. Chứng minh range set của `TreeSet` là live view.
8. Dùng `EnumSet` biểu diễn quyền và thực hiện phép hợp/giao.
9. Minh họa fail-fast, snapshot và weakly consistent iterator.
10. Dùng JMH so sánh membership của `HashSet`, `TreeSet` và `ArrayList`.
11. Cài `MyHashSet<E>` đơn giản để hiểu bucket, collision và resize.
12. Giải Longest Consecutive Sequence bằng `HashSet` với thời gian kỳ vọng `O(n)`.

---

## 18. Câu hỏi tự kiểm tra

1. Vì sao `Set` không có `get(index)`?
2. `HashSet.add()` phát hiện duplicate như thế nào?
3. Collision khác duplicate ra sao?
4. Vì sao `equals()` và `hashCode()` phải nhất quán?
5. Điều gì xảy ra khi mutate object trong `HashSet`?
6. Vì sao `HashSet` là `O(1)` trung bình chứ không tuyệt đối?
7. Treeification giải quyết vấn đề gì?
8. `LinkedHashSet` khác `HashSet` và `TreeSet` về thứ tự thế nào?
9. Vì sao comparator trả `0` có thể làm `TreeSet` mất phần tử?
10. `floor`, `ceiling`, `lower`, `higher` khác nhau thế nào?
11. Range view ảnh hưởng set gốc ra sao?
12. Khi nào `EnumSet` tốt hơn `HashSet`?
13. `Set.of`, `Set.copyOf`, `unmodifiableSet` khác nhau thế nào?
14. Snapshot iterator khác weakly consistent iterator thế nào?
15. Khi nào chọn concurrent hash set thay vì copy-on-write set?

---

## 19. Tài liệu liên quan trong repo

- [Tổng quan Collections Framework](../01-collections-framework.md)
- [Bài giải thích chi tiết về Set](../03-set-chi-tiet.md)
- [Bài tập Set](../07-set-bai-tap.md)

## 20. Tài liệu Java chính thức

- [Java SE 21 — `Set`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Set.html)
- [Java SE 21 — `HashSet`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/HashSet.html)
- [Java SE 21 — `LinkedHashSet`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/LinkedHashSet.html)
- [Java SE 21 — `TreeSet`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/TreeSet.html)
- [Java SE 21 — `EnumSet`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/EnumSet.html)
- [Java SE 21 — `CopyOnWriteArraySet`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/CopyOnWriteArraySet.html)
- [Java SE 21 — `ConcurrentSkipListSet`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ConcurrentSkipListSet.html)
- [Java SE 21 — `ConcurrentHashMap`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html)
- [Java SE 21 — `Collections`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Collections.html)


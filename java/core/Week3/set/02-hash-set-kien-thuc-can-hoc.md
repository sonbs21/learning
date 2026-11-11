# Java `HashSet` — Học chi tiết từ API đến cơ chế bên trong

> `HashSet` là implementation phổ biến của `Set` khi cần kiểm tra phần tử tồn tại nhanh và không quan tâm thứ tự duyệt.
>
> Tài liệu này bổ sung cho [kiến thức tổng quan về `Set`](./01-set-kien-thuc-can-hoc.md).

---

## 1. Mục tiêu đầu ra

Sau khi học xong, cần có khả năng:

- [ ] Giải thích `HashSet` lưu phần tử như thế nào.
- [ ] Phân biệt vai trò của `hashCode()` và `equals()`.
- [ ] Mô tả bucket, collision, load factor, threshold và resize.
- [ ] Ước lượng đúng độ phức tạp của các thao tác.
- [ ] Viết class có `equals/hashCode` phù hợp để dùng trong set.
- [ ] Nhận ra lỗi do mutable element.
- [ ] Duyệt, xóa và chia sẻ `HashSet` trong phạm vi an toàn.
- [ ] Chọn đúng giữa `HashSet`, `LinkedHashSet`, `TreeSet` và concurrent set.

---

## 2. `HashSet` là gì?

`HashSet<E>` là một `Set` dựa trên hash table:

- Không cho phép phần tử trùng theo equality của phần tử.
- Không đảm bảo thứ tự duyệt.
- Cho phép tối đa một phần tử `null`.
- `add`, `contains` và `remove` có độ phức tạp trung bình `O(1)` khi hash phân bố tốt.
- Không thread-safe.

```java
Set<String> languages = new HashSet<>();

languages.add("Java");    // true
languages.add("Kotlin");  // true
languages.add("Java");    // false: phần tử tương đương đã tồn tại

languages.contains("Java"); // true
```

Các bài toán phù hợp:

- kiểm tra “đã xuất hiện chưa?”;
- loại duplicate;
- lưu các mã đã xử lý;
- lưu node đã thăm trong DFS/BFS;
- membership lookup lặp lại nhiều lần.

Không dùng `HashSet` khi cần index, insertion order hoặc sorted/range query. Khi đó lần lượt cân nhắc `List`, `LinkedHashSet` hoặc `TreeSet`.

---

## 3. Quan hệ với `Set` và `HashMap`

Khai báo biến bằng abstraction:

```java
Set<Long> ids = new HashSet<>();
```

Trong OpenJDK, `HashSet` dùng một `HashMap` làm cấu trúc lưu trữ bên dưới. Mỗi phần tử của set là key của map; tất cả key dùng chung một value nội bộ gọi là `PRESENT`.

```text
HashSet.add(element)
        └── backingMap.put(element, PRESENT)

HashSet.contains(element)
        └── backingMap.containsKey(element)

HashSet.remove(element)
        └── backingMap.remove(element)
```

Đây là mô hình để hiểu cơ chế, không phải API public. Không nên dùng reflection hoặc phụ thuộc vào tên field nội bộ của `HashMap`.

Set chỉ cần biết phần tử có tồn tại hay không nên không cần value riêng. Nếu cần ánh xạ key–value, dùng `HashMap`; nếu cần đếm số lần xuất hiện, dùng `Map<E, Integer>`.

---

## 4. Một thao tác hoạt động như thế nào?

### 4.1. `add(element)`

```text
add(element)
    ├── tính hash từ element.hashCode()
    ├── phân phối hash để chọn bucket
    ├── bucket trống → thêm entry và trả true
    └── bucket đã có entry
            ├── hash/equality tương đương → duplicate, trả false
            └── khác → collision, tìm entry tiếp theo
```

Tên method và chi tiết trộn hash có thể thay đổi giữa các JDK. Ở mức API, điều quan trọng là hash code phải ổn định và nhất quán với `equals()`.

### 4.2. `contains(element)`

`contains` không so sánh mọi phần tử. Nó dùng hash để đi đến bucket có khả năng chứa phần tử, sau đó dùng equality để xác nhận:

```text
contains(target)
    ├── tính hash của target
    ├── chọn bucket
    ├── tìm entry phù hợp
    └── gọi equals() để xác nhận
```

Hash code không phải định danh duy nhất. Hai object cùng hash code vẫn có thể khác nhau; đó là collision.

### 4.3. `remove(element)`

`remove` tìm phần tử bằng cùng quy trình hash + equality rồi gỡ entry khỏi bucket. Method trả `true` nếu set thay đổi:

```java
Set<String> tags = new HashSet<>(Set.of("java", "backend"));

boolean removed = tags.remove("java");   // true
boolean absent = tags.remove("python");  // false
```

---

## 5. Hợp đồng `equals()` và `hashCode()`

Với hai object `a` và `b`:

1. Nếu `a.equals(b)` là `true` thì `a.hashCode() == b.hashCode()` phải là `true`.
2. Hash code giống nhau chưa thể kết luận hai object bằng nhau.
3. Kết quả equality và hash phải ổn định trong thời gian object nằm trong set.
4. Khi override một method, gần như luôn phải override method còn lại.

Nếu vi phạm quy tắc đầu tiên, set có thể chứa hai object mà logic ứng dụng coi là một. Nếu hash thay đổi sau khi `add`, set có thể không tìm lại được object.

### 5.1. Ví dụ value object đúng

```java
public final class UserId {
    private final long value;

    public UserId(long value) {
        this.value = value;
    }

    public long value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof UserId that)) return false;
        return value == that.value;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(value);
    }
}
```

```java
Set<UserId> ids = new HashSet<>();
ids.add(new UserId(42));
ids.contains(new UserId(42)); // true
```

`record` thường là lựa chọn gọn cho value object bất biến:

```java
public record UserId(long value) {}
```

### 5.2. Collision không phải duplicate

```java
final class BadHash {
    private final int id;

    BadHash(int id) {
        this.id = id;
    }

    @Override
    public int hashCode() {
        return 1; // mọi object dồn vào cùng vùng hash
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof BadHash that && id == that.id;
    }
}
```

`BadHash(1)` và `BadHash(2)` vẫn có thể cùng nằm trong set. Hash code giống nhau chỉ làm tăng chi phí tìm kiếm; `equals()` mới quyết định chúng có trùng không.

Không cố tạo hash code “duy nhất tuyệt đối”: `int` có hữu hạn giá trị, trong khi số object có thể lớn hơn nhiều.

---

## 6. Mutable element — lỗi nguy hiểm nhất

```java
final class User {
    private int id;

    User(int id) {
        this.id = id;
    }

    void setId(int id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof User that && id == that.id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }
}

User user = new User(1);
Set<User> users = new HashSet<>();
users.add(user);

user.setId(2); // hash code thay đổi sau khi add

users.contains(user); // có thể false
users.remove(user);   // có thể không xóa được
```

Object vẫn còn trong cấu trúc nội bộ, nhưng ở vị trí được tính từ hash cũ. Truy vấn bằng hash mới không đến đúng vị trí đó.

Ưu tiên:

1. dùng immutable element;
2. xóa trước, thay đổi rồi thêm lại;
3. tách identity bất biến khỏi dữ liệu mutable;
4. dùng key bất biến riêng như `Set<Long>`.

```java
users.remove(user);
user.setId(2);
users.add(user);
```

> Cách remove–modify–add chỉ an toàn khi toàn bộ code dùng đúng quy trình và không có thread khác truy cập giữa các bước.

---

## 7. Capacity, load factor, threshold và resize

- **Capacity**: số bucket của bảng hash.
- **Size**: số phần tử thực tế.
- **Load factor**: mức đầy mục tiêu trước khi mở rộng.
- **Threshold**: ngưỡng kích hoạt resize, thường gần `capacity * loadFactor`.
- **Resize**: mở rộng bảng và phân phối lại entry vào bucket mới.

Trong nhiều JDK, load factor mặc định là `0.75`. Đây là giá trị cân bằng bộ nhớ và collision, không phải contract của interface `Set`.

Khi vượt threshold, resize có thể làm một lần `add` đắt hơn bình thường. Vì vậy `add` là `O(1)` trung bình hoặc amortized, không phải mọi lần gọi đều có chi phí cố định.

Nếu biết trước set lớn, có thể truyền capacity:

```java
Set<String> ids = new HashSet<>(1_000);
```

Nhưng `initialCapacity` không nhất thiết bằng đúng số phần tử chứa được; threshold còn phụ thuộc load factor và implementation. Không chọn capacity cực lớn nếu chưa có số liệu đo.

Khi copy collection, constructor thường là lựa chọn tự nhiên:

```java
Set<String> unique = new HashSet<>(input);
```

Trong OpenJDK hiện đại, bucket có quá nhiều collision có thể chuyển thành cấu trúc cây cân bằng khi đủ điều kiện. Ngưỡng treeification, số bucket và cách resize là chi tiết implementation, không được dùng làm API contract.

---

## 8. Độ phức tạp và bộ nhớ

| Thao tác | Trung bình | Ghi chú |
|---|---:|---|
| `add` | `O(1)` | Có thể tăng do collision hoặc resize |
| `contains` | `O(1)` | Phụ thuộc chất lượng hash |
| `remove` | `O(1)` | Tìm phần tử trong bucket |
| `size`, `isEmpty` | `O(1)` | Theo dõi trực tiếp kích thước |
| `clear` | Phụ thuộc capacity | Phải dọn cấu trúc nội bộ |
| duyệt toàn bộ | Gần `O(size + capacity)` | Capacity quá lớn làm duyệt kém hiệu quả |

Mỗi entry cần bộ nhớ cho node, key và liên kết bucket. Con số byte thực tế phụ thuộc JVM, kiến trúc và JDK; không suy ra chính xác chỉ từ Big-O.

---

## 9. API phải thành thạo

### 9.1. Thêm và kiểm tra

```java
Set<String> seen = new HashSet<>();

boolean firstTime = seen.add("A"); // true
boolean duplicate = seen.add("A"); // false

if (seen.add("B")) {
    System.out.println("B là giá trị mới");
}

boolean exists = seen.contains("A");
```

Dùng kết quả của `add` thường tránh được một lần lookup so với `contains` rồi `add`. Tuy nhiên, với set không concurrent, chuỗi nhiều bước vẫn không atomic.

### 9.2. Xóa và chuyển đổi

```java
seen.remove("A");
seen.removeIf(value -> value == null || value.isBlank());

List<String> list = new ArrayList<>(seen);
String[] array = seen.toArray(String[]::new); // Java 11+
```

`removeIf` phù hợp để xóa theo điều kiện. Khi dùng iterator thủ công, có thể gọi `iterator.remove()`.

### 9.3. Phép toán tập hợp

Các method dưới đây thay đổi set gọi method, nên tạo copy nếu cần giữ input:

```java
Set<Integer> a = new HashSet<>(Set.of(1, 2, 3));
Set<Integer> b = new HashSet<>(Set.of(3, 4, 5));

Set<Integer> union = new HashSet<>(a);
union.addAll(b); // {1, 2, 3, 4, 5}

Set<Integer> intersection = new HashSet<>(a);
intersection.retainAll(b); // {3}

Set<Integer> difference = new HashSet<>(a);
difference.removeAll(b); // {1, 2}

boolean subset = a.containsAll(Set.of(1, 2)); // true
```

Hiệu đối xứng:

```java
Set<Integer> symmetric = new HashSet<>(a);
symmetric.addAll(b);

Set<Integer> common = new HashSet<>(a);
common.retainAll(b);
symmetric.removeAll(common);
```

---

## 10. `null`, equality và thứ tự

### 10.1. `null`

`HashSet` cho phép tối đa một `null`:

```java
Set<String> values = new HashSet<>();
values.add(null); // true
values.add(null); // false
```

Điều này không có nghĩa mọi implementation của `Set` đều cho phép `null`. `TreeSet`, `EnumSet` và concurrent set có policy khác.

### 10.2. Không phụ thuộc thứ tự

`HashSet` không đảm bảo insertion order, sorted order hoặc một thứ tự cố định giữa các JDK:

```java
Set<Integer> values = new HashSet<>();
values.add(3);
values.add(1);
values.add(2);

// Không được suy luận output luôn là 1, 2, 3 hay 3, 1, 2.
System.out.println(values);
```

Nếu cần output deterministic:

```java
List<Integer> sorted = values.stream()
        .sorted()
        .toList();
```

Hai set bằng nhau khi có cùng size và cùng phần tử, không phụ thuộc implementation hoặc thứ tự:

```java
Set<Integer> first = new HashSet<>(Set.of(1, 2, 3));
Set<Integer> second = new LinkedHashSet<>(List.of(3, 2, 1));
first.equals(second); // true
```

---

## 11. Duyệt và iterator

```java
for (String value : values) {
    System.out.println(value);
}

values.forEach(System.out::println);

Iterator<String> iterator = values.iterator();
while (iterator.hasNext()) {
    if (iterator.next() == null) {
        iterator.remove();
    }
}
```

Không sửa trực tiếp set trong enhanced `for`:

```java
for (String value : values) {
    if (value != null && value.isBlank()) {
        values.remove(value); // lỗi điển hình
    }
}
```

Dùng một trong hai cách:

```java
values.removeIf(value -> value == null || value.isBlank());
```

hoặc gọi `iterator.remove()` như ví dụ trên.

Iterator của `HashSet` thường fail-fast khi có structural modification ngoài iterator và có thể ném `ConcurrentModificationException`. Đây là cơ chế phát hiện lỗi best effort, không phải thread-safety và không phải bảo đảm tuyệt đối.

---

## 12. Thread safety

`HashSet` không thread-safe. Khi nhiều thread cùng đọc/ghi, có thể xảy ra race condition, iterator lỗi hoặc kết quả logic không đúng.

### 12.1. Wrapper đồng bộ

```java
Set<String> synchronizedSet =
        Collections.synchronizedSet(new HashSet<>());

synchronized (synchronizedSet) {
    for (String value : synchronizedSet) {
        // Phải khóa khi duyệt theo contract của wrapper.
    }
}
```

Wrapper đồng bộ hóa từng method, nhưng chuỗi “kiểm tra rồi hành động” cần lock ngoài:

```java
synchronized (synchronizedSet) {
    if (!synchronizedSet.contains(value)) {
        synchronizedSet.add(value);
    }
}
```

### 12.2. Concurrent set

```java
Set<String> concurrentSet = ConcurrentHashMap.newKeySet();
```

Set này hỗ trợ truy cập đồng thời, không cho `null`, không đảm bảo thứ tự và có weakly consistent iterator.

Nếu cần concurrent set được sắp xếp, cân nhắc `ConcurrentSkipListSet`. Nếu set nhỏ, đọc rất nhiều và ghi rất ít, `CopyOnWriteArraySet` có thể phù hợp; mỗi lần ghi tạo bản sao nên không hợp với set lớn hoặc write-heavy.

---

## 13. Stream và collector

```java
Set<String> unique = names.stream()
        .map(String::trim)
        .collect(Collectors.toSet());
```

Không phụ thuộc `Collectors.toSet()` trả về implementation, order hoặc mutability cụ thể nếu tài liệu API không cam kết điều đó.

Khi cần chỉ rõ implementation:

```java
Set<String> ordered = names.stream()
        .collect(Collectors.toCollection(LinkedHashSet::new));

Set<String> hashSet = names.stream()
        .collect(Collectors.toCollection(HashSet::new));
```

Khi cần kết quả không sửa được:

```java
Set<String> immutable = names.stream()
        .collect(Collectors.toUnmodifiableSet());
```

Collector không làm cho object bên trong immutable. Duplicate được xác định theo equality của phần tử sau các bước `map`.

---

## 14. Pattern sử dụng thực tế

### 14.1. Deduplication

```java
List<String> input = List.of("java", "sql", "java", "http");
Set<String> unique = new HashSet<>(input);
```

Nếu cần giữ thứ tự xuất hiện đầu tiên, đổi sang `LinkedHashSet`.

### 14.2. Phát hiện duplicate trong một lượt duyệt

```java
Set<String> seen = new HashSet<>();
for (String value : input) {
    if (!seen.add(value)) {
        System.out.println("Duplicate: " + value);
    }
}
```

### 14.3. `visited` trong DFS/BFS

```java
void visit(Node node, Set<Node> visited) {
    if (!visited.add(node)) {
        return;
    }

    for (Node next : node.neighbors()) {
        visit(next, visited);
    }
}
```

`Node` phải có equality phù hợp với khái niệm “cùng node” của bài toán.

### 14.4. Membership nhiều lần

```java
Set<String> allowedRoles = new HashSet<>(List.of("ADMIN", "EDITOR"));
if (allowedRoles.contains(role)) {
    // role hợp lệ
}
```

Với collection rất nhỏ và chỉ lookup một lần, chi phí tạo set có thể không đáng. Hãy xét tổng số lookup và kích thước dữ liệu.

---

## 15. So sánh và cách chọn

| Nhu cầu | Lựa chọn | Lý do |
|---|---|---|
| Membership, deduplication, không cần order | `HashSet` | Lookup trung bình `O(1)` |
| Deduplication và giữ insertion order | `LinkedHashSet` | Duy trì encounter order |
| Sorted order, nearest/range query | `TreeSet` | `O(log n)` và có `NavigableSet` |
| Tập các enum | `EnumSet` | Bit vector, gọn và nhanh |
| Concurrent, unordered membership | `ConcurrentHashMap.newKeySet()` | Hỗ trợ truy cập đồng thời |
| Concurrent, sorted membership | `ConcurrentSkipListSet` | Sorted và concurrent |

Ngoài Big-O, cần xét thứ tự, kích thước, tỷ lệ đọc/ghi, thread-safety, `null`, bộ nhớ và chất lượng `hashCode()`.

---

## 16. Checklist lỗi thường gặp

- [ ] Chỉ override `equals()` mà quên `hashCode()`.
- [ ] Thay đổi field tham gia equality/hashing sau khi `add`.
- [ ] Nghĩ cùng hash code nghĩa là hai object trùng nhau.
- [ ] Dùng hash code hằng số cho class có nhiều phần tử.
- [ ] Phụ thuộc thứ tự của `HashSet`.
- [ ] Dùng `HashSet` khi yêu cầu là giữ insertion order hoặc sorted order.
- [ ] Xóa trực tiếp trong enhanced `for`.
- [ ] Nghĩ fail-fast đồng nghĩa với thread-safe.
- [ ] Quên khóa khi duyệt `Collections.synchronizedSet`.
- [ ] Dùng `CopyOnWriteArraySet` cho workload ghi nhiều.
- [ ] Phụ thuộc implementation cụ thể của `Collectors.toSet()`.
- [ ] Tối ưu capacity trước khi có số liệu đo.
- [ ] Phụ thuộc vào ngưỡng treeify hoặc field nội bộ của JDK.

---

## 17. Bài tập tự kiểm tra

1. Tạo class `Email` để hai email không phân biệt hoa/thường là một phần tử. Kiểm tra `add` và `contains` với các cách viết khác nhau.
2. Viết test chứng minh mutable element có thể “mất” khỏi `HashSet`, rồi sửa bằng immutable class hoặc remove–modify–add.
3. Viết union, intersection, difference và symmetric difference mà không làm thay đổi input set.
4. So sánh deduplication bằng `HashSet`, `LinkedHashSet` và stream collector.
5. Đo lookup trên `ArrayList` và `HashSet` ở nhiều kích thước; tách thời gian tạo cấu trúc khỏi thời gian lookup.

---

## 18. Tóm tắt cần nhớ

1. `HashSet` lưu phần tử như key của một `HashMap` nội bộ.
2. `hashCode()` giúp tìm vùng; `equals()` xác nhận phần tử.
3. `equals()` là `true` thì `hashCode()` phải giống nhau.
4. Không thay đổi field tham gia hashing khi object đang ở trong set.
5. `add`, `contains`, `remove` là `O(1)` trung bình, không phải bảo đảm tuyệt đối.
6. Collision là bình thường; hash giống nhau chưa có nghĩa là duplicate.
7. `HashSet` không đảm bảo thứ tự và không thread-safe.
8. Dùng implementation khác khi yêu cầu về order, sorting hoặc concurrency thay đổi.

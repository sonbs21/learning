# Java `LinkedHashSet` — Hashing kết hợp thứ tự chèn

> `LinkedHashSet` phù hợp khi cần loại phần tử trùng như `HashSet` nhưng vẫn giữ được thứ tự encounter order.
>
> Tài liệu này bổ sung cho [kiến thức tổng quan về `Set`](./01-set-kien-thuc-can-hoc.md) và [chi tiết về `HashSet`](./02-hash-set-kien-thuc-can-hoc.md).

---

## 1. Mục tiêu đầu ra

Sau khi học xong, cần có khả năng:

- [ ] Giải thích vì sao `LinkedHashSet` vừa lookup nhanh vừa giữ thứ tự duyệt.
- [ ] Phân biệt insertion order, encounter order và sorted order.
- [ ] Hiểu quan hệ giữa `LinkedHashSet`, `HashSet` và `LinkedHashMap`.
- [ ] Dùng đúng constructor, API thêm/xóa và các phép toán tập hợp.
- [ ] Tính được trade-off về thời gian, bộ nhớ và capacity.
- [ ] Nhận ra ảnh hưởng của duplicate, `equals/hashCode` và mutable element.
- [ ] Biết khi nào dùng `HashSet`, `LinkedHashSet`, `TreeSet` hoặc collection khác.

---

## 2. `LinkedHashSet` là gì?

`LinkedHashSet<E>` là implementation của `Set` có hai đặc điểm chính:

1. Dùng hashing để xác định phần tử trùng và hỗ trợ `add`, `contains`, `remove` nhanh.
2. Duy trì thứ tự phần tử theo thứ tự encounter, thông thường là thứ tự chèn thành công.

```java
Set<String> languages = new LinkedHashSet<>();
languages.add("Java");
languages.add("Kotlin");
languages.add("Java"); // duplicate, không đổi vị trí

System.out.println(languages); // [Java, Kotlin]
```

`LinkedHashSet` không phải là set được sắp xếp. Nó nhớ thứ tự encounter; nó không tự xếp `Java`, `Kotlin` theo alphabet.

### 2.1. Khi nào nên dùng?

- Loại duplicate khỏi `List` nhưng giữ lần xuất hiện đầu tiên.
- Trả kết quả API/log theo thứ tự input ổn định.
- Xây dựng danh sách unique có thứ tự deterministic.
- Giữ thứ tự cấu hình, tag, permission hoặc bước xử lý.
- Cần set có lookup trung bình `O(1)` và không muốn output thay đổi ngẫu nhiên.

### 2.2. Khi nào không nên dùng?

- Không cần order và muốn giảm overhead: cân nhắc `HashSet`.
- Cần natural order, comparator, nearest hoặc range query: dùng `TreeSet`.
- Cần truy cập theo index: dùng `List`.
- Cần thread-safety: dùng collection concurrent hoặc wrapper đồng bộ phù hợp.
- Cần LRU thật sự theo thứ tự truy cập: `LinkedHashSet` không đủ; cân nhắc `LinkedHashMap` với access order.

---

## 3. Mô hình bên trong

### 3.1. Hash table cộng linked list

Có thể hình dung `LinkedHashSet` gồm hai cấu trúc:

```text
LinkedHashSet
    ├── hash table
    │     └── tìm bucket, contains nhanh, loại duplicate
    └── doubly-linked encounter list
          └── nối các entry theo thứ tự duyệt
```

Trong OpenJDK, `LinkedHashSet` kế thừa từ `HashSet` và dùng cơ chế liên kết entry của `LinkedHashMap` để duy trì thứ tự. Đây là chi tiết implementation quan trọng để hiểu nhưng không nên truy cập bằng reflection hoặc coi tên field là API.

Mô hình khái niệm:

```text
add(element)
    ├── hash/equality xác định element đã có chưa
    ├── chưa có → thêm vào bucket
    └── đồng thời nối entry vào cuối encounter list
```

### 3.2. Vì sao duplicate không đổi vị trí?

`add` chỉ nối entry khi set thực sự thay đổi. Nếu phần tử tương đương đã tồn tại, method trả `false` và không tạo entry mới:

```java
Set<Integer> values = new LinkedHashSet<>();
values.add(1); // [1]
values.add(2); // [1, 2]
values.add(1); // [1, 2], không chuyển 1 xuống cuối
```

Đây là hành vi phù hợp với pattern “giữ lần xuất hiện đầu tiên”. Nếu muốn mỗi lần gặp lại phần tử làm nó xuất hiện ở cuối, phải chủ động `remove` rồi `add` lại.

```java
values.remove(1);
values.add(1); // [2, 1]
```

### 3.3. Thứ tự nào được duy trì?

Trong các phiên bản Java truyền thống, `LinkedHashSet` duy trì insertion order, chính xác hơn là thứ tự các phần tử được thêm thành công vào set.

- `add` phần tử mới: thêm vào cuối.
- `add` duplicate: không đổi thứ tự.
- `remove` rồi `add` lại: phần tử xuất hiện ở cuối.
- `clear`: xóa toàn bộ encounter list.
- `addAll`: duyệt collection đầu vào và thêm từng phần tử theo thứ tự đó.
- `retainAll/removeAll`: các phần tử còn lại giữ thứ tự tương đối của chúng.

Không được hiểu thứ tự này là thứ tự được sắp xếp, thứ tự truy cập, hay thứ tự do `hashCode()` quyết định.

---

## 4. `equals()` và `hashCode()` vẫn là nền tảng

`LinkedHashSet` xác định duplicate theo nguyên tắc hash-based set:

1. `hashCode()` giúp tìm bucket.
2. `equals()` xác nhận hai object có tương đương không.

Nếu `a.equals(b)` là `true`, `a.hashCode()` và `b.hashCode()` phải giống nhau. Hash code giống nhau một mình chưa đủ để kết luận duplicate.

```java
record UserId(long value) {}

Set<UserId> ids = new LinkedHashSet<>();
ids.add(new UserId(10));
ids.add(new UserId(10));

System.out.println(ids.size()); // 1
```

### 4.1. Mutable element

Giống `HashSet`, không thay đổi field tham gia `equals/hashCode` khi object đang nằm trong `LinkedHashSet`:

```java
Set<User> users = new LinkedHashSet<>();
User user = new User(1);
users.add(user);

user.setId(2); // hash code thay đổi
users.contains(user); // có thể false
users.remove(user);   // có thể không tìm thấy
```

Thứ tự liên kết vẫn có thể còn reference đến object, nhưng hash table đang đặt object dựa trên hash cũ. Cách an toàn là dùng immutable element hoặc remove–modify–add.

---

## 5. API và constructor

### 5.1. Constructor

```java
Set<String> empty = new LinkedHashSet<>();

Set<String> withCapacity = new LinkedHashSet<>(128);

Set<String> copy = new LinkedHashSet<>(List.of("Java", "SQL", "Java"));
// [Java, SQL]
```

Constructor nhận `Collection` duyệt input theo encounter order của input, rồi thêm từng phần tử. Duplicate bị loại theo equality.

Không có constructor public nhận `loadFactor` như `HashSet` trong API thông dụng. Đừng cố can thiệp cấu trúc bên trong bằng reflection để tinh chỉnh implementation.

### 5.2. Thêm phần tử

```java
Set<String> tags = new LinkedHashSet<>();

boolean added = tags.add("java"); // true
boolean duplicate = tags.add("java"); // false
tags.addAll(List.of("sql", "http", "sql"));
// [java, sql, http]
```

Giá trị `boolean` cho biết set có thay đổi không. Đây là cách phát hiện duplicate trong một lượt duyệt:

```java
Set<String> seen = new LinkedHashSet<>();
for (String value : input) {
    if (!seen.add(value)) {
        System.out.println("Duplicate: " + value);
    }
}
```

### 5.3. Kiểm tra, xóa, duyệt

```java
tags.contains("java");
tags.size();
tags.isEmpty();
tags.remove("sql");
tags.removeIf(value -> value != null && value.isBlank());
tags.clear();
```

Các API `Set` quen thuộc như `containsAll`, `retainAll`, `removeAll`, `iterator` và `forEach` đều dùng được.

### 5.4. Các phép toán tập hợp

Các method mutation thay đổi object gọi method, nên tạo copy nếu cần bảo toàn input:

```java
Set<Integer> a = new LinkedHashSet<>(List.of(1, 2, 3));
Set<Integer> b = new LinkedHashSet<>(List.of(3, 4, 5));

Set<Integer> union = new LinkedHashSet<>(a);
union.addAll(b); // [1, 2, 3, 4, 5]

Set<Integer> intersection = new LinkedHashSet<>(a);
intersection.retainAll(b); // [3]

Set<Integer> difference = new LinkedHashSet<>(a);
difference.removeAll(b); // [1, 2]
```

Khi trộn hai set, thứ tự kết quả phụ thuộc vào set nhận mutation:

```java
Set<Integer> first = new LinkedHashSet<>(List.of(2, 1));
Set<Integer> second = new LinkedHashSet<>(List.of(1, 3));

first.addAll(second); // [2, 1, 3]
```

Phần tử cũ giữ vị trí, phần tử mới được thêm theo encounter order của `second`.

---

## 6. Độ phức tạp và chi phí bộ nhớ

| Thao tác | Độ phức tạp trung bình | Ghi chú |
|---|---:|---|
| `add` | `O(1)` | Hash lookup và nối cuối list |
| `contains` | `O(1)` | Không cần duyệt toàn bộ encounter list |
| `remove` | `O(1)` | Gỡ khỏi bucket và linked list |
| `size`, `isEmpty` | `O(1)` | Theo dõi trực tiếp kích thước |
| duyệt | Gần `O(size)` | Thường không phụ thuộc trực tiếp vào capacity |
| `clear` | Phụ thuộc cấu trúc | Xóa bucket và liên kết |

So với `HashSet`:

- lookup trung bình tương tự;
- thêm chi phí reference `before/after` để lưu thứ tự;
- dùng nhiều bộ nhớ hơn;
- duyệt thường ổn định và hiệu quả hơn khi capacity lớn nhưng size nhỏ;
- resize vẫn có thể làm một lần `add` đắt hơn bình thường.

`O(1)` là độ phức tạp trung bình khi hash phân bố tốt, không phải bảo đảm tuyệt đối cho mọi `hashCode()`.

### 6.1. Capacity

Nếu biết trước số phần tử lớn, có thể truyền initial capacity để giảm resize:

```java
Set<String> ids = new LinkedHashSet<>(1_000);
```

Không nên chọn capacity cực lớn một cách tùy ý. Capacity quá lớn làm tốn bộ nhớ; implementation và load factor quyết định thời điểm bảng thực sự mở rộng.

---

## 7. `LinkedHashSet` so với `HashSet`

| Tiêu chí | `HashSet` | `LinkedHashSet` |
|---|---|---|
| Duplicate | Không cho | Không cho |
| `contains/add/remove` | `O(1)` trung bình | `O(1)` trung bình |
| Thứ tự duyệt | Không cam kết | Thứ tự encounter/insertion |
| `null` | Tối đa một | Tối đa một |
| Bộ nhớ | Thấp hơn | Cao hơn do linked order |
| Duyệt | Có thể phụ thuộc capacity | Theo các entry thực tế |
| Thread-safe | Không | Không |
| Use case | Membership thuần túy | Membership + output/input order ổn định |

Ví dụ cùng input:

```java
List<String> input = List.of("b", "a", "b", "c");

Set<String> hash = new HashSet<>(input);
Set<String> linked = new LinkedHashSet<>(input);

// hash: không được dựa vào thứ tự
// linked: [b, a, c]
```

Chỉ đổi sang `LinkedHashSet` nếu thứ tự thực sự là yêu cầu. Nếu không, `HashSet` đơn giản hơn và thường ít overhead hơn.

---

## 8. `LinkedHashSet` so với `TreeSet`

| Tiêu chí | `LinkedHashSet` | `TreeSet` |
|---|---|---|
| Tiêu chí unique | `hashCode/equals` | comparator hoặc natural order |
| Thứ tự | Thứ tự encounter | Sorted order |
| Lookup trung bình | `O(1)` | `O(log n)` |
| Range query | Không | Có |
| Nearest query | Không | Có `floor/ceiling/lower/higher` |
| `null` | Cho một `null` | Natural order thường không cho `null` |

Một comparator trả `0` trong `TreeSet` sẽ làm phần tử mới bị coi là duplicate dù `equals()` có thể khác. `LinkedHashSet` không có vấn đề comparator này vì nó không dùng comparator để xác định uniqueness.

---

## 9. Deduplication và thứ tự

### 9.1. Giữ lần xuất hiện đầu tiên

```java
List<String> input = List.of("Java", "SQL", "Java", "HTTP", "SQL");
List<String> unique = new ArrayList<>(new LinkedHashSet<>(input));

System.out.println(unique); // [Java, SQL, HTTP]
```

Đây là pattern nên dùng khi yêu cầu là “unique nhưng giữ nguyên thứ tự input”.

### 9.2. Khác với `distinct()` của stream

```java
List<String> unique = input.stream()
        .distinct()
        .toList();
```

Với stream tuần tự, `distinct()` giữ encounter order của stream. `LinkedHashSet` hữu ích khi cần một collection set có order sau khi pipeline kết thúc, hoặc khi muốn thao tác set tiếp tục.

### 9.3. Collector chỉ rõ implementation

```java
Set<String> ordered = input.stream()
        .collect(Collectors.toCollection(LinkedHashSet::new));
```

Không nên dùng `Collectors.toSet()` nếu code phụ thuộc insertion order. Contract của `toSet()` không nên được dùng để suy ra `LinkedHashSet`.

---

## 10. Iterator, sửa phần tử và fail-fast

Iterator của `LinkedHashSet` duyệt theo encounter order:

```java
for (String tag : tags) {
    System.out.println(tag);
}
```

Không thêm hoặc xóa trực tiếp trong enhanced `for`:

```java
for (String tag : tags) {
    if (tag.isBlank()) {
        tags.remove(tag); // có thể ném ConcurrentModificationException
    }
}
```

Dùng `removeIf`:

```java
tags.removeIf(tag -> tag != null && tag.isBlank());
```

Hoặc dùng iterator:

```java
Iterator<String> iterator = tags.iterator();
while (iterator.hasNext()) {
    String tag = iterator.next();
    if (tag != null && tag.isBlank()) {
        iterator.remove();
    }
}
```

Iterator thường fail-fast trên best-effort basis khi có structural modification ngoài iterator. Fail-fast không phải thread-safety, không phải snapshot và không phải một bảo đảm tuyệt đối.

---

## 11. `null` và element mutable

### 11.1. `null`

`LinkedHashSet` cho phép tối đa một `null`, và `null` cũng có vị trí trong encounter order:

```java
Set<String> values = new LinkedHashSet<>();
values.add("A");
values.add(null);
values.add("B");

System.out.println(values); // [A, null, B]
```

Các method xử lý phần tử phải tự quyết định cách ứng xử với `null`. Ví dụ `String::isBlank` sẽ ném `NullPointerException` nếu set chứa `null`; dùng predicate có kiểm tra `null` khi cần.

### 11.2. Element mutable

Thứ tự liên kết không bảo vệ set khỏi lỗi mutable element. Nếu field identity thay đổi:

```java
users.remove(user);
user.setId(newId);
users.add(user);
```

Tốt nhất là dùng record hoặc class immutable làm element. Nếu element chứa collection mutable, cũng cần xem xét sâu hơn: equality có thể thay đổi khi collection bên trong bị sửa.

---

## 12. Thread safety và immutable view

`LinkedHashSet` không thread-safe. Nếu nhiều thread cùng đọc/ghi, cần chọn chiến lược rõ ràng.

### 12.1. Wrapper đồng bộ

```java
Set<String> synchronizedSet =
        Collections.synchronizedSet(new LinkedHashSet<>());

synchronized (synchronizedSet) {
    for (String value : synchronizedSet) {
        // Phải khóa khi duyệt.
    }
}
```

Lock ngoài vẫn cần cho chuỗi nhiều bước như “contains rồi add”. Wrapper cũng không biến phần tử bên trong thành immutable.

### 12.2. Snapshot không sửa được

```java
Set<String> source = new LinkedHashSet<>(List.of("A", "B"));
Set<String> copy = Set.copyOf(source);
```

`Set.copyOf` trả set không sửa được nhưng không nên dùng nếu cần bảo đảm thứ tự encounter như một phần contract. Khi order là yêu cầu, tạo bản copy có order rồi bọc read-only:

```java
Set<String> orderedCopy = Collections.unmodifiableSet(
        new LinkedHashSet<>(source)
);
```

Đây là shallow copy: object bên trong vẫn có thể mutable.

### 12.3. Concurrent set

`ConcurrentHashMap.newKeySet()` phù hợp cho concurrent membership nhưng không duy trì insertion order. Nếu vừa cần concurrent vừa cần order, phải thiết kế đồng bộ hóa/serialization thao tác hoặc chọn cấu trúc khác theo workload; không thay thế máy móc bằng `ConcurrentHashMap.newKeySet()`.

---

## 13. Java version và `SequencedSet`

Từ Java 21, `LinkedHashSet` liên quan đến abstraction `SequencedSet`, cho phép làm việc với phần tử đầu/cuối và view đảo chiều theo API của JDK tương ứng.

Ví dụ theo Java 21+:

```java
LinkedHashSet<String> values = new LinkedHashSet<>();
values.add("A");
values.add("B");

// API SequencedSet trong JDK 21+
values.addFirst("first");
values.addLast("last");
values.getFirst();
values.getLast();
values.reversed();
```

Kiểm tra version JDK của project trước khi dùng các method này. Code cần chạy trên Java 8/11/17 không được giả định có API Java 21.

---

## 14. Pattern thực tế

### 14.1. Giữ thứ tự ưu tiên

```java
List<String> configured = List.of("cache", "database", "queue", "cache");
Set<String> executionOrder = new LinkedHashSet<>(configured);
```

Kết quả giữ đúng bước đầu tiên của mỗi thành phần.

### 14.2. Xây danh sách unique từ nhiều nguồn

```java
Set<String> allTags = new LinkedHashSet<>();
allTags.addAll(tagsFromUser);
allTags.addAll(tagsFromDatabase);
allTags.addAll(tagsFromDefaults);
```

Tag xuất hiện trước giữ vị trí, tag mới được nối ở cuối. Cần xác định rõ đây có phải thứ tự ưu tiên mong muốn không.

### 14.3. Duy trì thứ tự khi xử lý graph

```java
Set<Node> visited = new LinkedHashSet<>();
for (Node node : traversal) {
    if (visited.add(node)) {
        process(node);
    }
}
```

Khác với `HashSet`, set này còn giúp quan sát thứ tự node được phát hiện, hữu ích cho log hoặc kết quả tái lập. Equality của `Node` phải phản ánh đúng identity graph.

### 14.4. Reset vị trí về cuối

```java
static <T> void moveToEnd(Set<T> set, T value) {
    if (set.remove(value)) {
        set.add(value);
    }
}
```

Pattern này chỉ nên dùng nếu API nghiệp vụ thực sự muốn “remove rồi re-add”. Nó không biến `LinkedHashSet` thành access-order set hoàn chỉnh.

---

## 15. Những bẫy thường gặp

- [ ] Nghĩ `LinkedHashSet` tự động sắp xếp phần tử.
- [ ] Nghĩ thêm duplicate sẽ chuyển phần tử đó xuống cuối.
- [ ] Dùng `HashSet` trong code cần output order ổn định.
- [ ] Dùng `LinkedHashSet` khi thực ra cần range query hoặc comparator.
- [ ] Quên rằng linked order làm tăng chi phí bộ nhớ.
- [ ] Thay đổi field tham gia `equals/hashCode` sau khi thêm element.
- [ ] Chỉ override `equals()` mà quên `hashCode()`.
- [ ] Dùng `String::isBlank` khi set có thể chứa `null`.
- [ ] Xóa trực tiếp trong enhanced `for`.
- [ ] Nghĩ fail-fast đồng nghĩa với thread-safe.
- [ ] Duyệt synchronized wrapper mà không giữ monitor.
- [ ] Dùng `Collectors.toSet()` rồi kỳ vọng insertion order.
- [ ] Dùng `Set.copyOf` nhưng vẫn kỳ vọng order là contract cần giữ.
- [ ] Dùng `LinkedHashSet` để mô phỏng LRU/access order.
- [ ] Dùng API `SequencedSet` trên JDK cũ hơn Java 21.
- [ ] Phụ thuộc vào field hoặc linked node nội bộ của JDK.

---

## 16. Bài tập tự kiểm tra

1. Cho input `A, B, A, C, B`, tạo output unique vẫn giữ thứ tự lần xuất hiện đầu tiên.
2. So sánh output của `HashSet`, `LinkedHashSet` và `TreeSet` trên cùng input.
3. Viết method `moveToEnd` và giải thích vì sao nó không phải access-order tự động.
4. Viết union/intersection/difference bằng `LinkedHashSet` và xác định thứ tự kết quả.
5. Tạo class mutable có field dùng trong `hashCode`, viết test chứng minh lỗi rồi sửa bằng immutable object.
6. Viết benchmark nhỏ so sánh lookup và iteration của `HashSet` với `LinkedHashSet` khi capacity lớn hơn nhiều so với size.

---

## 17. Tóm tắt cần nhớ

1. `LinkedHashSet` là hash-based set có thêm encounter order.
2. `add`, `contains`, `remove` có `O(1)` trung bình khi hash tốt.
3. Duplicate được xác định bằng `hashCode()` và `equals()`, không bằng thứ tự.
4. Thêm duplicate không đổi vị trí; remove rồi add lại sẽ đưa phần tử về cuối.
5. So với `HashSet`, `LinkedHashSet` tốn thêm bộ nhớ nhưng duyệt có thứ tự ổn định.
6. Nó không sorted, không access-order và không thread-safe.
7. Dùng `LinkedHashSet` cho “unique + giữ thứ tự input”, không dùng cho range query.
8. Luôn giữ field tham gia equality/hashing ổn định trong suốt vòng đời phần tử ở set.


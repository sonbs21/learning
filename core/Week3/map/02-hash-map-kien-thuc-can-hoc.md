# Java `HashMap` — Học chi tiết từ API đến cơ chế bên trong

> `HashMap` là implementation phổ biến khi cần ánh xạ `key -> value` với thao tác trung bình `O(1)` và không yêu cầu thứ tự duyệt.
>
> Tài liệu này bổ sung cho [kiến thức tổng quan về `Map`](./01-map-kien-thuc-can-hoc.md).

---

## 1. Mục tiêu đầu ra

Sau khi học xong, cần có khả năng:

- [ ] Giải thích cách `HashMap` lưu key-value.
- [ ] Phân biệt vai trò của `hashCode()` và `equals()`.
- [ ] Mô tả bucket, collision, capacity, load factor, threshold và resize.
- [ ] Dùng đúng `put`, `get`, `putIfAbsent`, `compute`, `merge`.
- [ ] Phân biệt `get == null` với `containsKey`.
- [ ] Dùng `keySet`, `values`, `entrySet` như live view.
- [ ] Nhận ra lỗi do mutable key và hash code kém.
- [ ] Hiểu giới hạn của fail-fast và thread safety.
- [ ] Chọn đúng giữa `HashMap`, `LinkedHashMap`, `TreeMap` và concurrent map.

---

## 2. `HashMap` là gì?

`HashMap<K, V>` là map dựa trên hash table:

- Mỗi key có tối đa một mapping.
- Nhiều key có thể cùng value.
- `put`, `get`, `containsKey`, `remove` có độ phức tạp trung bình `O(1)` khi hash phân bố tốt.
- Không đảm bảo thứ tự duyệt.
- Cho phép một `null` key và nhiều `null` value.
- Không thread-safe.

```java
Map<String, Integer> scores = new HashMap<>();

scores.put("An", 8);  // null: chưa có value cũ
scores.put("Binh", 9);
scores.put("An", 10); // thay value của key An

System.out.println(scores.get("An")); // 10
```

### 2.1. Khi nào nên dùng?

- Tra cứu dữ liệu theo key nhiều lần.
- Đếm tần suất hoặc gom nhóm dữ liệu.
- Cache đơn giản trong phạm vi một thread.
- Chuyển một collection thành index theo một field.
- Lưu metadata không yêu cầu order.

### 2.2. Khi nào không nên dùng?

- Cần insertion/access order: dùng `LinkedHashMap`.
- Cần sorted key, nearest key hoặc range query: dùng `TreeMap`.
- Cần truy cập đồng thời: dùng `ConcurrentHashMap` hoặc map phù hợp.
- Cần key là identity bằng `==`: cân nhắc `IdentityHashMap`.
- Cần key là enum và muốn tối ưu: cân nhắc `EnumMap`.

---

## 3. Mô hình bên trong

### 3.1. Bucket và node

Có thể hình dung bảng bên trong như một mảng bucket:

```text
table
  [0] -> null
  [1] -> Node(key=A, value=1) -> Node(key=Q, value=9)
  [2] -> Node(key=B, value=2)
  [3] -> null
```

Mỗi mapping gồm key, value, hash và liên kết tới entry tiếp theo trong bucket. Khi `get(key)`:

1. Hash của key được tính.
2. Hash được phân phối để xác định bucket.
3. Các node trong bucket được kiểm tra.
4. `equals()` được dùng để xác nhận đúng key.

Trong OpenJDK hiện đại, bucket có nhiều collision có thể chuyển sang cấu trúc cây cân bằng khi đạt một số điều kiện. Đây là chi tiết implementation, không phải API contract.

### 3.2. `put(key, value)` từng bước

```text
put(key, value)
    ├── key == null? → xử lý bucket dành cho null
    ├── tính hash từ key.hashCode()
    ├── chọn bucket
    ├── bucket trống → tạo mapping mới
    └── bucket có node
            ├── cùng hash và equals() → thay value cũ
            └── khác key → collision, tìm node tiếp theo
```

Nếu key đã tồn tại, `put` thay value nhưng không tăng `size`:

```java
Map<String, Integer> map = new HashMap<>();
map.put("x", 1);
map.put("x", 2);

map.size(); // 1
map.get("x"); // 2
```

### 3.3. Collision không phải duplicate

Hai key có cùng hash code vẫn có thể khác nhau:

```java
final class Key {
    private final int id;

    Key(int id) {
        this.id = id;
    }

    @Override
    public int hashCode() {
        return 1; // hash kém, nhưng hợp lệ
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Key key && id == key.id;
    }
}
```

Hash code giống nhau chỉ khiến nhiều key vào cùng bucket. `equals()` mới quyết định mapping có cùng key hay không.

---

## 4. Hợp đồng `equals()` và `hashCode()` của key

Với hai key `a` và `b`:

1. Nếu `a.equals(b)` là `true`, `a.hashCode()` phải bằng `b.hashCode()`.
2. Hash code giống nhau chưa thể kết luận hai key bằng nhau.
3. Kết quả equality và hash phải ổn định khi key đang nằm trong map.
4. Khi override một method, gần như luôn phải override method còn lại.

Ví dụ value object phù hợp làm key:

```java
public record UserId(long value) {}

Map<UserId, String> users = new HashMap<>();
users.put(new UserId(42), "An");

users.get(new UserId(42)); // "An"
```

Các kiểu thường phù hợp làm key:

- `String`;
- primitive wrapper như `Integer`, `Long`;
- `UUID`;
- enum;
- record/value object immutable.

Không nên dùng object mutable làm key nếu field identity có thể thay đổi.

---

## 5. Mutable key — lỗi nghiêm trọng nhất

```java
final class UserKey {
    private int id;

    UserKey(int id) {
        this.id = id;
    }

    void setId(int id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof UserKey key && id == key.id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }
}

Map<UserKey, String> map = new HashMap<>();
UserKey key = new UserKey(1);
map.put(key, "An");

key.setId(2); // hash code thay đổi sau khi put

map.get(key);    // có thể null
map.containsKey(key); // có thể false
map.remove(key); // có thể không xóa được
```

Entry vẫn có thể nằm trong bảng nhưng ở bucket được tính từ hash cũ. Query bằng hash mới không tìm thấy nó.

### 5.1. Cách xử lý

- Dùng immutable key.
- Nếu phải thay đổi identity, remove trước, thay đổi rồi put lại.
- Tách key immutable khỏi object value mutable.
- Không dùng field tạm thời, timestamp hoặc trạng thái runtime trong identity của key.

```java
map.remove(key);
key.setId(2);
map.put(key, "An");
```

Trong code đa luồng, remove–modify–put phải nằm trong cơ chế đồng bộ phù hợp.

---

## 6. Capacity, load factor, threshold và resize

- **Capacity**: số bucket của bảng hash.
- **Size**: số mapping thực tế.
- **Load factor**: mức đầy mục tiêu trước khi mở rộng bảng.
- **Threshold**: ngưỡng kích hoạt resize, thường gần `capacity * loadFactor`.
- **Resize**: mở rộng bảng và phân phối lại node vào bucket mới.

Trong nhiều JDK, load factor mặc định của `HashMap` là `0.75`. Đây là cân bằng giữa memory và collision, không phải contract của `Map`.

Khi `size` vượt threshold:

1. bảng được mở rộng;
2. các mapping được phân bố lại;
3. lần `put` hiện tại có thể đắt hơn bình thường;
4. các lookup sau thường có phân bố bucket tốt hơn.

Do đó, `put` là `O(1)` trung bình hoặc amortized; không phải mọi lần gọi đều có chi phí cố định.

### 6.1. Initial capacity

Nếu biết trước map sẽ lớn, có thể truyền capacity để giảm số lần resize:

```java
Map<String, User> users = new HashMap<>(1_000);
```

Không nên chọn capacity cực lớn chỉ để “cho chắc”: sẽ tốn memory, có thể làm việc duyệt kém hiệu quả và không loại bỏ nhu cầu hiểu load factor.

Khi tạo map từ collection, copy constructor thường đủ tốt:

```java
Map<String, Integer> copy = new HashMap<>(source);
```

### 6.2. Treeification

Trong OpenJDK hiện đại, bucket có quá nhiều collision có thể chuyển từ linked structure sang balanced tree nếu đạt điều kiện về độ dài bucket và capacity. Mục đích là hạn chế ảnh hưởng của collision nghiêm trọng.

Không phụ thuộc vào:

- ngưỡng treeification cụ thể;
- số bucket cụ thể;
- thứ tự node trong bucket;
- tên field nội bộ của `HashMap`.

---

## 7. Độ phức tạp và bộ nhớ

| Thao tác | Trung bình | Ghi chú |
|---|---:|---|
| `put` | `O(1)` | Có thể tăng do collision hoặc resize |
| `get` | `O(1)` | Phụ thuộc chất lượng hash |
| `containsKey` | `O(1)` | Dùng hash của key |
| `remove` | `O(1)` | Tìm rồi gỡ mapping |
| `containsValue` | `O(n)` | Phải duyệt các value |
| `size`, `isEmpty` | `O(1)` | Theo dõi trực tiếp |
| duyệt `entrySet` | Gần `O(size + capacity)` | Tùy cấu trúc và implementation |
| `clear` | Phụ thuộc capacity | Dọn bảng và node |

Mỗi mapping cần memory cho node, key, value và liên kết bucket. Chi phí byte thực tế phụ thuộc JVM, kiến trúc và JDK; không suy ra chính xác từ Big-O.

---

## 8. API cơ bản phải thành thạo

### 8.1. `put`, `get`, `containsKey`

```java
Map<String, Integer> scores = new HashMap<>();

Integer previous = scores.put("An", 8); // null
scores.put("An", 9);

Integer score = scores.get("An");
boolean exists = scores.containsKey("An");
```

`put` trả value cũ, hoặc `null` nếu chưa có mapping hoặc value cũ là `null`.

### 8.2. Phân biệt missing key và null value

```java
Map<String, String> map = new HashMap<>();
map.put("known", null);

map.get("known");       // null
map.get("missing");     // cũng null
map.containsKey("known");   // true
map.containsKey("missing"); // false
```

Nếu value `null` có ý nghĩa, luôn dùng `containsKey` khi cần phân biệt hai trạng thái.

### 8.3. `getOrDefault`

```java
int score = scores.getOrDefault("Unknown", 0);
```

`getOrDefault` trả default khi key không có mapping; nếu key có mapping tới `null`, kết quả vẫn có thể là `null` tùy value type và cách gọi.

### 8.4. Xóa và thay thế có điều kiện

```java
scores.remove("An");
scores.remove("Binh", 9); // chỉ xóa nếu value hiện tại là 9

scores.replace("An", 10); // chỉ thay nếu key tồn tại
scores.replace("An", 9, 10); // cần khớp value cũ
```

Các method conditional giúp tránh mẫu “đọc rồi ghi” không cần thiết. Trong `HashMap` đơn luồng, chúng vẫn không tạo thread safety.

---

## 9. `putIfAbsent`, `compute` và `merge`

### 9.1. `putIfAbsent`

```java
Map<String, String> aliases = new HashMap<>();
aliases.putIfAbsent("java", "Java");
aliases.putIfAbsent("java", "JAVA"); // không ghi đè
```

Với map cho phép null, “absent” trong ngữ cảnh method này có thể bao gồm mapping đang có value `null`. Nếu cần semantics khác, đọc kỹ contract của API.

### 9.2. `computeIfAbsent`

Pattern gom nhóm:

```java
Map<String, List<String>> groups = new HashMap<>();
groups.computeIfAbsent(category, key -> new ArrayList<>())
      .add(item);
```

Quy tắc chính:

- mapping function chạy khi key chưa có value non-null;
- function trả `null` thì không tạo mapping;
- không nên sửa chính map bên trong function;
- trên `HashMap` thường, method không tự nhiên trở thành atomic/thread-safe.

### 9.3. `computeIfPresent` và `compute`

```java
scores.computeIfPresent("An", (key, oldValue) -> oldValue + 1);

scores.compute("Binh", (key, oldValue) ->
        oldValue == null ? 1 : oldValue + 1
);
```

Nếu remapping function trả `null`, mapping hiện tại bị xóa. Với key chưa có, `computeIfPresent` không chạy; `compute` có thể tạo mapping nếu function trả non-null.

### 9.4. `merge`

Phù hợp để đếm hoặc cộng dồn:

```java
Map<String, Integer> frequency = new HashMap<>();
for (String word : words) {
    frequency.merge(word, 1, Integer::sum);
}
```

Ý nghĩa:

- chưa có key: ghi value mặc định `1`;
- đã có key: gọi remapping function;
- remapping function trả `null`: xóa mapping.

Không sửa map đó bên trong remapping function và không giả định các method này atomic trên `HashMap` thường.

---

## 10. Các view: `keySet`, `values`, `entrySet`

| View | Kiểu | Đặc điểm |
|---|---|---|
| `keySet()` | `Set<K>` | Key duy nhất |
| `values()` | `Collection<V>` | Value có thể trùng |
| `entrySet()` | `Set<Map.Entry<K,V>>` | Duyệt key và value cùng lúc |

Các view là live view, không phải bản copy:

- xóa khỏi view sẽ xóa mapping tương ứng;
- thay đổi map phản ánh vào view;
- `add` thường không được hỗ trợ vì không đủ thông tin tạo mapping;
- `entrySet` cho phép cập nhật value qua `Map.Entry.setValue()` trong phạm vi contract của iterator/view.

### 10.1. Duyệt `entrySet`

Khi cần cả key và value, ưu tiên `entrySet`:

```java
for (Map.Entry<String, Integer> entry : scores.entrySet()) {
    System.out.println(entry.getKey() + " = " + entry.getValue());
}
```

Tránh mẫu sau nếu cần cả value:

```java
for (String key : scores.keySet()) {
    Integer value = scores.get(key); // thêm một lookup
}
```

### 10.2. Xóa an toàn khi duyệt

```java
Iterator<Map.Entry<String, Integer>> iterator = scores.entrySet().iterator();
while (iterator.hasNext()) {
    Map.Entry<String, Integer> entry = iterator.next();
    if (entry.getValue() < 0) {
        iterator.remove();
    }
}
```

Hoặc dùng:

```java
scores.entrySet().removeIf(entry -> entry.getValue() < 0);
```

---

## 11. Thứ tự, iterator và fail-fast

`HashMap` không đảm bảo insertion order, sorted order hoặc một thứ tự ổn định giữa các phiên bản JDK.

```java
Map<Integer, String> map = new HashMap<>();
map.put(3, "C");
map.put(1, "A");
map.put(2, "B");

// Không được phụ thuộc vào thứ tự in ra.
System.out.println(map);
```

Nếu cần order:

- insertion order: `LinkedHashMap`;
- sorted key: `TreeMap`;
- sorted và concurrent: `ConcurrentSkipListMap`.

Iterator của `HashMap` thường fail-fast khi map bị structural modification ngoài iterator và có thể ném `ConcurrentModificationException`. Đây là best-effort bug detection, không phải thread safety và không phải bảo đảm tuyệt đối.

---

## 12. Thread safety

`HashMap` không thread-safe. Nhiều thread cùng đọc/ghi có thể gây race condition, kết quả mất update, iterator lỗi hoặc trạng thái logic sai.

### 12.1. Synchronized wrapper

```java
Map<String, Integer> synchronizedMap =
        Collections.synchronizedMap(new HashMap<>());

synchronized (synchronizedMap) {
    for (Map.Entry<String, Integer> entry : synchronizedMap.entrySet()) {
        // Phải khóa khi duyệt theo contract của wrapper.
    }
}
```

Wrapper đồng bộ từng method riêng lẻ. Chuỗi “contains rồi put” vẫn cần lock ngoài nếu phải atomic:

```java
synchronized (synchronizedMap) {
    if (!synchronizedMap.containsKey(key)) {
        synchronizedMap.put(key, value);
    }
}
```

### 12.2. Concurrent map

```java
ConcurrentMap<String, Integer> concurrent = new ConcurrentHashMap<>();
concurrent.merge("java", 1, Integer::sum);
```

`ConcurrentHashMap` phù hợp khi cần truy cập đồng thời và hỗ trợ các thao tác atomic như `putIfAbsent`, `compute`, `merge` theo contract concurrent map. Nó không cho `null` key hoặc `null` value và không đảm bảo thứ tự.

Không chỉ thay `HashMap` bằng `ConcurrentHashMap` nếu còn logic compound nhiều bước; phải kiểm tra toàn bộ invariant và atomicity cần thiết.

---

## 13. Stream và collector

### 13.1. Tạo map với key duy nhất

```java
Map<Long, User> byId = users.stream()
        .collect(Collectors.toMap(
                User::id,
                Function.identity()
        ));
```

Nếu có duplicate key, `toMap` mặc định ném `IllegalStateException`. Cần chỉ rõ merge function:

```java
Map<Long, User> lastById = users.stream()
        .collect(Collectors.toMap(
                User::id,
                Function.identity(),
                (oldUser, newUser) -> newUser
        ));
```

### 13.2. Chỉ rõ implementation

`Collectors.toMap` mặc định không nên được dùng để suy ra `HashMap`, order hoặc mutability cụ thể nếu code phụ thuộc contract đó. Khi cần chỉ rõ:

```java
Map<Long, User> result = users.stream()
        .collect(Collectors.toMap(
                User::id,
                Function.identity(),
                (left, right) -> left,
                HashMap::new
        ));
```

Muốn giữ insertion order, dùng `LinkedHashMap::new`; muốn sorted key, dùng `TreeMap::new`.

### 13.3. Grouping

```java
Map<String, List<User>> byDepartment = users.stream()
        .collect(Collectors.groupingBy(User::department));
```

Nếu cần map cụ thể:

```java
Map<String, Set<User>> uniqueByDepartment = users.stream()
        .collect(Collectors.groupingBy(
                User::department,
                HashMap::new,
                Collectors.toSet()
        ));
```

---

## 14. Mutable, unmodifiable và immutable map

```java
Map<String, Integer> mutable = new HashMap<>();

Map<String, Integer> liveReadOnly =
        Collections.unmodifiableMap(mutable);

Map<String, Integer> fixed = Map.of("A", 1, "B", 2);
Map<String, Integer> snapshot = Map.copyOf(mutable);
```

Khác biệt quan trọng:

- `new HashMap<>(source)`: copy cấu trúc độc lập, mutable.
- `Collections.unmodifiableMap(source)`: live read-only view; source đổi thì view đổi.
- `Map.of(...)`: unmodifiable map; không cho null; duplicate key trong factory không hợp lệ.
- `Map.copyOf(source)`: unmodifiable shallow copy; không phản ánh thay đổi cấu trúc của source.

Không cách nào ở trên làm object key/value bên trong tự immutable. Nếu value là `List` mutable, map read-only vẫn có thể chứa list bị thay đổi.

---

## 15. `null` và các edge case

`HashMap` cho phép:

- một `null` key;
- nhiều key có value `null`;
- nhiều mapping có cùng value non-null.

```java
Map<String, String> map = new HashMap<>();
map.put(null, "unknown key");
map.put("A", null);
map.put("B", null);
```

Đừng dùng `get(key) == null` để suy ra key chắc chắn không tồn tại. Hãy dùng `containsKey` nếu hai trạng thái cần phân biệt.

Các map concurrent như `ConcurrentHashMap` không cho null vì null sẽ làm nhập nhằng giữa “không có mapping” và “mapping có null”.

---

## 16. `HashMap` so với implementation khác

| Nhu cầu | Lựa chọn | Đặc điểm |
|---|---|---|
| Lookup key nhanh, không cần order | `HashMap` | `O(1)` trung bình |
| Giữ insertion/access order | `LinkedHashMap` | Hashing + linked order |
| Sorted key, range/nearest query | `TreeMap` | `O(log n)` |
| Enum key | `EnumMap` | Gọn và chuyên biệt cho enum |
| Concurrent lookup/update | `ConcurrentHashMap` | Không null, không đảm bảo order |
| Concurrent sorted key | `ConcurrentSkipListMap` | Expected `O(log n)` |
| Key identity bằng `==` | `IdentityHashMap` | Semantics khác `HashMap` |

Chọn map theo invariant cần bảo vệ, không chỉ theo tên implementation.

---

## 17. Pattern sử dụng thực tế

### 17.1. Đếm tần suất

```java
Map<String, Integer> frequency = new HashMap<>();
for (String word : words) {
    frequency.merge(word, 1, Integer::sum);
}
```

### 17.2. Index theo ID

```java
Map<Long, User> byId = new HashMap<>();
for (User user : users) {
    byId.put(user.id(), user);
}

User user = byId.get(requestedId);
```

Nếu duplicate ID là lỗi dữ liệu, kiểm tra kết quả `put` hoặc dùng collector có merge function ném lỗi rõ ràng.

### 17.3. Cache đơn giản

```java
Map<String, Result> cache = new HashMap<>();
Result result = cache.computeIfAbsent(input, this::calculate);
```

Đây chỉ là cache đơn giản: không có eviction, TTL hay giới hạn kích thước. Không biến `HashMap` thành cache production bằng cách thêm `computeIfAbsent` đơn thuần.

### 17.4. Gom nhóm

```java
Map<String, List<Order>> ordersByCustomer = new HashMap<>();
for (Order order : orders) {
    ordersByCustomer
            .computeIfAbsent(order.customerId(), id -> new ArrayList<>())
            .add(order);
}
```

---

## 18. Các bẫy thường gặp

- [ ] Phụ thuộc vào thứ tự duyệt của `HashMap`.
- [ ] Dùng key mutable làm thay đổi `hashCode` sau khi `put`.
- [ ] Chỉ override `equals()` mà quên `hashCode()`.
- [ ] Nghĩ cùng hash code nghĩa là cùng key.
- [ ] Dùng `get(key) == null` thay cho `containsKey` khi map cho phép null.
- [ ] Nhầm `put` luôn tạo mapping mới; cùng key sẽ thay value cũ.
- [ ] Duyệt `keySet` rồi gọi `get` lặp lại khi có thể dùng `entrySet`.
- [ ] Sửa map trực tiếp trong enhanced `for`.
- [ ] Nghĩ fail-fast là thread-safe.
- [ ] Duyệt synchronized wrapper mà không giữ monitor.
- [ ] Nghĩ `computeIfAbsent` tự làm mọi compound operation thread-safe trên `HashMap`.
- [ ] Sử dụng mapping function có side effect vào chính map.
- [ ] Quên xử lý duplicate key khi dùng `Collectors.toMap`.
- [ ] Phụ thuộc implementation của `Collectors.toMap`.
- [ ] Nghĩ unmodifiable map làm key/value bên trong immutable.
- [ ] Chọn `HashMap` khi yêu cầu thật sự là sorted hoặc insertion order.
- [ ] Dùng capacity quá nhỏ làm resize nhiều hoặc quá lớn làm lãng phí memory.
- [ ] Dùng reflection để phụ thuộc bucket/treeification nội bộ của JDK.

---

## 19. Bài tập tự kiểm tra

1. Viết class `UserId` immutable và dùng nó làm key của `HashMap`.
2. Viết test chứng minh mutable key có thể làm entry không tìm thấy.
3. Tạo frequency map bằng `getOrDefault`, sau đó viết lại bằng `merge`.
4. Gom nhóm object bằng `computeIfAbsent` và bằng `groupingBy`.
5. So sánh `keySet`, `values`, `entrySet` khi map bị thay đổi sau khi lấy view.
6. Dùng `Collectors.toMap` với duplicate key và viết merge policy rõ ràng.
7. So sánh workload lookup của `HashMap`, `TreeMap` và `LinkedHashMap` với dữ liệu thực tế.

---

## 20. Tóm tắt cần nhớ

1. `HashMap` ánh xạ key tới value bằng hash table.
2. `hashCode()` giúp tìm bucket; `equals()` xác nhận đúng key.
3. Cùng key sẽ thay value cũ và không tăng `size`.
4. `put`, `get`, `containsKey`, `remove` là `O(1)` trung bình khi hash tốt.
5. Collision là bình thường; hash giống nhau chưa có nghĩa key giống nhau.
6. Không thay đổi field tham gia equality/hashing khi key đang nằm trong map.
7. `keySet`, `values`, `entrySet` là live view.
8. `HashMap` không đảm bảo thứ tự, không thread-safe và cho phép null.
9. Dùng `LinkedHashMap` cho order, `TreeMap` cho sorted key và `ConcurrentHashMap` cho concurrency.



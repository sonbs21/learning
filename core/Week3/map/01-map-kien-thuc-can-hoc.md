# Java `Map` — Toàn bộ kiến thức cần học

> Checklist này mở rộng phần `Map` trong [Collections Framework](../01-collections-framework.md).  
> Mục tiêu: hiểu hợp đồng key-value, cơ chế hashing/cây, các view, API biến đổi, tính bất biến, concurrency và cách chọn implementation.

---

## 1. Mục tiêu đầu ra

Sau khi học xong, cần có khả năng:

- [ ] Giải thích vì sao `Map` không extends `Collection`.
- [ ] Hiểu key duy nhất, value có thể trùng và quy tắc `null`.
- [ ] Dùng đúng `put`, `get`, `remove`, `replace`, `compute` và `merge`.
- [ ] Hiểu `keySet`, `values`, `entrySet` là live view.
- [ ] Giải thích cơ chế `HashMap`, `LinkedHashMap` và `TreeMap`.
- [ ] Tránh lỗi do mutable key hoặc sai `equals/hashCode`/comparator.
- [ ] Biết `EnumMap`, `WeakHashMap`, `IdentityHashMap` dùng khi nào.
- [ ] Phân biệt mutable, unmodifiable và immutable map.
- [ ] Chọn đúng map cho đơn luồng, concurrent, ordered hoặc sorted data.
- [ ] Dùng Map với Stream mà không gặp lỗi duplicate key.

---

## 2. Nền tảng: `Map` là gì?

```text
Map<K, V>                         (không extends Collection)
    ├── HashMap<K, V>
    │       └── LinkedHashMap<K, V>
    ├── SortedMap<K, V>
    │       └── NavigableMap<K, V>
    │               ├── TreeMap<K, V>
    │               └── ConcurrentSkipListMap<K, V>
    ├── EnumMap<K extends Enum<K>, V>
    ├── WeakHashMap<K, V>
    ├── IdentityHashMap<K, V>
    ├── Hashtable<K, V>           (legacy)
    │       └── Properties
    └── ConcurrentMap<K, V>
            └── ConcurrentHashMap<K, V>
```

### 2.1. Các tính chất cốt lõi

- Map biểu diễn ánh xạ `key -> value`.
- Mỗi key chỉ có tối đa một mapping tại một thời điểm.
- Nhiều key có thể trỏ đến các value bằng nhau.
- Thêm lại cùng key sẽ thay thế value cũ, không tăng `size`.
- Thứ tự duyệt và chính sách `null` phụ thuộc implementation.
- `Map` không extends `Collection` vì phần tử logic là cặp key-value và API trung tâm là `put(K,V)`, không phải `add(E)`.
- Map cung cấp các collection view: `keySet()`, `values()`, `entrySet()`.

```java
Map<String, Integer> scores = new HashMap<>();
scores.put("An", 8);
scores.put("Binh", 8); // value trùng hợp lệ
scores.put("An", 9);   // thay value của key An
```

### 2.2. Key duy nhất được xác định bằng gì?

| Nhóm map | Tiêu chí key tương đương |
|---|---|
| `HashMap`, `LinkedHashMap`, `ConcurrentHashMap` | `hashCode()` để định vị, `equals()` để xác nhận |
| `TreeMap`, `ConcurrentSkipListMap` | `compareTo()` hoặc `Comparator.compare()` trả về `0` |
| `EnumMap` | Hằng enum cùng identity |
| `IdentityHashMap` | `==`, không phải `equals()` |

---

## 3. Hợp đồng `equals()` và `hashCode()` của `Map`

Hai map bằng nhau khi chúng biểu diễn cùng tập mapping key-value, không phụ thuộc implementation hay thứ tự duyệt.

```java
Map<String, Integer> a = new HashMap<>();
a.put("x", 1);
a.put("y", 2);

Map<String, Integer> b = new LinkedHashMap<>();
b.put("y", 2);
b.put("x", 1);

a.equals(b); // true
```

- Hai entry bằng nhau khi cả key và value tương ứng bằng nhau.
- Hash code của map là tổng hash code của các entry.
- Các map bằng nhau phải có cùng hash code.

### 3.1. Hợp đồng của key trong hash-based map

- Nếu `a.equals(b)` là `true`, `a.hashCode()` phải bằng `b.hashCode()`.
- Cùng hash code chưa chắc cùng key; đó là collision hợp lệ.
- Phải override `equals()` và `hashCode()` nhất quán.
- Key nên immutable hoặc ít nhất các field tham gia equality/hashing phải ổn định.

### 3.2. Mutable key làm “mất” entry

```java
Map<UserKey, String> map = new HashMap<>();
UserKey key = new UserKey(1); // id tham gia equals/hashCode
map.put(key, "An");

key.setId(2);
map.get(key);    // có thể null
map.remove(key); // có thể không tìm được bucket cũ
```

Entry vẫn có thể nằm trong bảng nhưng không còn được định vị đúng. Đây là lý do `String`, primitive wrapper, enum, record/value object immutable thường là key tốt.

---

## 4. API cơ bản phải thành thạo

### 4.1. Thêm, đọc, kiểm tra và xóa

| API | Ý nghĩa |
|---|---|
| `put(K key, V value)` | Gắn value; trả về value cũ hoặc `null` |
| `putAll(Map)` | Thêm/thay toàn bộ mapping |
| `get(Object key)` | Lấy value hoặc `null` |
| `getOrDefault(key, defaultValue)` | Lấy value hoặc default khi không có mapping |
| `containsKey(key)` | Kiểm tra key tồn tại |
| `containsValue(value)` | Kiểm tra value tồn tại, thường tuyến tính |
| `remove(key)` | Xóa theo key và trả về value cũ |
| `remove(key, value)` | Chỉ xóa nếu đang map đúng cặp đó |
| `size()`, `isEmpty()`, `clear()` | Kích thước/trạng thái/xóa toàn bộ |

### 4.2. `null` gây nhập nhằng

Với map cho phép null value, `get(key) == null` có thể nghĩa là:

1. Key không tồn tại.
2. Key tồn tại nhưng value là `null`.

Dùng `containsKey(key)` nếu phải phân biệt hai trường hợp.

```java
map.put("known", null);
map.get("known");       // null
map.get("missing");     // cũng null
map.containsKey("known");   // true
map.containsKey("missing"); // false
```

### 4.3. Conditional update

| API | Hành vi chính |
|---|---|
| `putIfAbsent(k, v)` | Ghi khi chưa có mapping hoặc đang map tới `null` |
| `replace(k, v)` | Chỉ thay nếu key có mapping |
| `replace(k, old, new)` | Chỉ thay khi cả key và old value khớp |
| `replaceAll(function)` | Thay value cho mọi entry |

Không suy luận “key có tồn tại” chỉ từ value trả về nếu implementation cho phép null.

---

## 5. `keySet()`, `values()` và `entrySet()` là live view

| View | Kiểu | Đặc điểm |
|---|---|---|
| `keySet()` | `Set<K>` | Key là duy nhất |
| `values()` | `Collection<V>` | Value có thể trùng, nên không phải `Set` |
| `entrySet()` | `Set<Map.Entry<K,V>>` | Duyệt key và value cùng lúc |

Các view được backing bởi map:

- Xóa khỏi view sẽ xóa mapping tương ứng khỏi map.
- Thay đổi map sẽ phản ánh trong view.
- Thao tác `add` thường không được hỗ trợ vì không đủ thông tin tạo mapping.
- `Map.Entry.setValue()` có thể cập nhật value nếu iterator/view hỗ trợ.

```java
Iterator<Map.Entry<String, Integer>> iterator = map.entrySet().iterator();
while (iterator.hasNext()) {
    Map.Entry<String, Integer> entry = iterator.next();
    if (entry.getValue() < 0) {
        iterator.remove(); // xóa an toàn mapping hiện tại
    }
}
```

Ưu tiên `entrySet()` khi cần cả key lẫn value; tránh `keySet()` rồi gọi `get()` lặp lại không cần thiết.

### 5.1. Fail-fast

Iterator của map thông thường thường fail-fast theo best effort nếu map bị sửa cấu trúc ngoài iterator. Đây là cơ chế phát hiện bug, không phải thread-safety và không nên dùng exception để điều khiển logic.

---

## 6. `compute`, `merge` và các default method

### 6.1. `computeIfAbsent`

Tính value khi key chưa gắn với value khác `null`:

```java
groups.computeIfAbsent(category, key -> new ArrayList<>())
      .add(item);
```

- Mapping function trả `null` thì không thêm mapping.
- Không sửa chính map đó bên trong mapping function.
- Trên map thường, method không tự nhiên trở thành thread-safe.

### 6.2. `computeIfPresent` và `compute`

- `computeIfPresent`: chạy khi key đang gắn với non-null value.
- `compute`: tính lại mapping bất kể trạng thái cũ.
- Nếu remapping function trả `null`, mapping đang có sẽ bị xóa; nếu key chưa có thì không tạo mapping mới.

### 6.3. `merge`

Phù hợp với frequency count và aggregation:

```java
frequency.merge(word, 1, Integer::sum);
```

- Nếu key chưa có hoặc đang map tới `null`, dùng value mới.
- Nếu đã có non-null value, gọi remapping function với old/new value.
- Remapping function trả `null` thì xóa mapping.
- Đối số `value` truyền vào `merge(key, value, function)` phải khác `null`.

### 6.4. Atomicity

Default implementation của `Map` không cam kết đồng bộ. `ConcurrentMap`/`ConcurrentHashMap` override các thao tác phù hợp để cung cấp tính atomic theo contract riêng. Luôn đọc tài liệu của implementation cụ thể.

---

## 7. `HashMap` — bucket, collision và treeification

### 7.1. Mô hình `put`

```text
put(key, value)
    ├── lấy hash của key (null key có xử lý riêng)
    ├── phân phối hash và chọn bucket
    ├── bucket trống → thêm node
    └── bucket có entry
            ├── key tương đương → thay value, trả value cũ
            └── collision → thêm node hoặc thao tác trên tree bin
```

Trong OpenJDK hiện đại, bucket collision lớn có thể treeify thành balanced tree. Ngưỡng treeify/untreeify và capacity cụ thể là implementation detail, không phải API contract.

### 7.2. Capacity, load factor và resize

- `capacity`: số bucket của bảng.
- `load factor`: mức đầy trước resize, mặc định thường `0.75`.
- `threshold ≈ capacity × loadFactor`.
- Resize tăng capacity và phân phối entry sang bảng mới.
- Capacity thấp gây resize/collision; quá cao lãng phí bộ nhớ và làm iteration chậm.
- Chỉ tinh chỉnh initial capacity/load factor khi biết dữ liệu hoặc đã profiling.

### 7.3. Độ phức tạp

| Thao tác | Trung bình |
|---|---|
| `put`, `get`, `containsKey`, `remove` | `O(1)` |
| `containsValue` | `O(n)` |
| Duyệt | Gần `O(size + capacity)` |

Kết quả trung bình giả định hash phân bố tốt. Collision nghiêm trọng làm tăng chi phí; tree bin giúp giảm tác động trong một số trường hợp.

### 7.4. Tính chất

- Cho phép một null key và nhiều null value.
- Không thread-safe.
- Không cam kết thứ tự duyệt.
- Key tương đương ghi đè value cũ.

### 7.5. Cơ chế định vị entry từ `hash` đến bucket

Có thể hình dung `HashMap` là một mảng bucket. Mỗi bucket chứa một hoặc nhiều node; mỗi node lưu `hash`, `key`, `value` và liên kết tới node tiếp theo (hoặc liên kết trong tree bin sau khi treeify):

```text
table
  [0] → null
  [1] → Node(hash, keyA, valueA) → Node(hash, keyB, valueB)
  [2] → TreeBin
  [3] → null
```

#### 7.5.1. `put(key, value)` diễn ra thế nào?

Giả sử gọi:

```java
map.put("java", 17);
```

Luồng xử lý khái quát:

```text
1. key.hashCode()
       ↓
2. trộn hash để tận dụng cả bit cao và bit thấp
       ↓
3. tính bucket index từ hash và độ dài table
       ↓
4. bucket rỗng? ── Có  → tạo node mới
       │
       └─ Không → so sánh các node trong bucket
                       ├─ cùng key → thay value cũ
                       └─ key khác → thêm node vào collision chain/tree
       ↓
5. tăng size; nếu vượt threshold thì resize
```

Với key không `null`, `HashMap` không dùng trực tiếp toàn bộ số trả về từ `hashCode()` làm vị trí. Nó trộn các bit của hash rồi dùng kết quả để chọn bucket:

```text
key.hashCode()
    → hash spreading (trộn bit cao/thấp)
    → index trong bảng bucket
    → so sánh hash
    → so sánh equals()
```

Trong implementation hiện đại, capacity thường là lũy thừa của 2 nên việc tính index có thể tối ưu bằng phép toán bit. Đây là chi tiết của JDK, không nên tự viết code phụ thuộc vào công thức nội bộ. Hai key có cùng bucket vẫn có thể là key khác nhau: map tiếp tục kiểm tra `hash` và `equals()` để phân biệt.

Ở mức khái niệm, index thường tương đương:

```java
index = (table.length - 1) & spreadHash;
```

Phép toán này chỉ hoạt động theo cách tối ưu khi capacity là lũy thừa của 2. Đây là lý do capacity ban đầu và capacity sau resize thường được chọn theo dạng `1, 2, 4, 8, ...`.

#### 7.5.2. Collision được xử lý ra sao?

Hai key khác nhau có thể rơi vào cùng bucket. HashMap không coi đó là cùng key; nó tiếp tục kiểm tra:

1. hash của node có khớp không;
2. key có cùng reference không (`key == storedKey`);
3. nếu chưa, `equals()` có trả `true` không.

```text
bucket 5
  Node("Aa", 1) → Node("BB", 2)
       ↑ hai key khác nhau nhưng cùng bucket
```

Collision ít: các node được nối thành linked list và HashMap duyệt lần lượt. Collision nhiều: bucket có thể chuyển sang tree bin để việc tìm kiếm trong bucket không tiếp tục tuyến tính. Ngưỡng treeify/untreeify là chi tiết implementation của JDK; không được coi là API contract.

#### 7.5.3. `get(key)` tìm value thế nào?

```text
get(key)
  → tính lại hash từ key
  → tính đúng bucket index
  → kiểm tra node đầu tiên
  → nếu chưa khớp, đi qua linked list hoặc cây
  → trả value khi hash + equals khớp
  → không có thì trả null
```

`get()` không duyệt toàn bộ map. Nó tính lại bucket từ key nên key phải giữ nguyên kết quả `hashCode()` và `equals()` trong suốt thời gian được dùng làm key. Nếu sửa field tham gia hai method sau `put`, entry có thể vẫn tồn tại trong table nhưng `get()` không còn tìm được:

```java
UserKey key = new UserKey(1); // id tham gia equals/hashCode
map.put(key, "An");

key.setId(2);                  // làm thay đổi hash/identity logic
map.get(key);                 // có thể null
map.remove(key);              // có thể không xóa được entry cũ
```

#### 7.5.4. Resize và rehash

HashMap có `threshold ≈ capacity × loadFactor` (thường load factor mặc định là `0.75`). Khi số entry vượt threshold:

```text
capacity 16, threshold 12
size tăng từ 12 → 13
        ↓
tạo table mới capacity 32
        ↓
phân phối lại các node vào table mới
```

Resize tốn `O(n)` tại thời điểm xảy ra và có thể tạo pause đáng kể nếu map lớn. Khi capacity tăng gấp đôi, mỗi node thường ở bucket cũ hoặc bucket cũ cộng `oldCapacity`, dựa trên bit hash mới; đây là tối ưu nội bộ để không phải tính lại mọi thứ từ đầu theo cách tổng quát.

- Capacity quá nhỏ: nhiều resize và collision hơn.
- Capacity quá lớn: tốn bộ nhớ và iteration phải quét nhiều bucket rỗng.
- Nếu biết gần đúng số phần tử, có thể đặt initial capacity phù hợp; không nên tùy tiện chỉnh load factor khi chưa profiling.

#### 7.5.5. Null key và thứ tự duyệt

`HashMap` cho phép một `null` key. Key này được xử lý như trường hợp đặc biệt vì không thể gọi `hashCode()` trên `null`; implementation đặt nó vào bucket tương ứng với hash đặc biệt. Nhiều `null` value được phép vì value không tham gia định vị bucket.

Thứ tự duyệt phụ thuộc vào bucket, collision chain/tree và thời điểm resize. Vì vậy việc thấy một thứ tự “có vẻ ổn định” không có nghĩa `HashMap` bảo đảm thứ tự đó. Nếu cần thứ tự, hãy chọn `LinkedHashMap` hoặc `TreeMap`.

---

## 8. `LinkedHashMap` — encounter order và LRU

- Kế thừa cơ chế hash lookup của `HashMap`.
- Duy trì thêm liên kết đôi giữa các entry.
- Mặc định giữ insertion order.
- Có thể cấu hình access order qua constructor.
- Thường `O(1)` trung bình cho thao tác cơ bản.
- Tốn thêm bộ nhớ nhưng duyệt tỷ lệ với `size`, không phụ thuộc capacity.

```java
Map<String, Integer> accessOrder =
    new LinkedHashMap<>(16, 0.75f, true);
```

Trong access-order map, các thao tác truy cập được quy định có thể đưa entry về cuối thứ tự. Việc đổi access order có thể được xem là structural modification và ảnh hưởng iterator fail-fast.

### 8.1. LRU cache

```java
class LruCache<K, V> extends LinkedHashMap<K, V> {
    private final int capacity;

    LruCache(int capacity) {
        super(capacity, 0.75f, true);
        this.capacity = capacity;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > capacity;
    }
}
```

Ví dụ này không tự thread-safe. Java 21 bổ sung `SequencedMap` và API encounter-order; kiểm tra JDK dự án trước khi dùng.

### 8.2. Cấu trúc bên trong

`LinkedHashMap` ghép hai cấu trúc:

```text
HashMap bucket table:  lookup nhanh theo key
        ↓
Entry ⇄ Entry ⇄ Entry: doubly linked list giữ encounter order
```

Mỗi entry vừa nằm trong bucket của hash table, vừa có liên kết `before/after` với entry trước/sau. Vì vậy:

- `get` vẫn tìm entry qua hash table, trung bình `O(1)`.
- Duyệt theo insertion/access order chỉ đi qua linked list, nên chi phí gần `O(size)`.
- Xóa một entry phải gỡ nó khỏi cả bucket lẫn linked list.
- Với `accessOrder = true`, một lần truy cập đủ điều kiện có thể tháo entry khỏi vị trí cũ và nối nó vào cuối danh sách; đó là lý do thao tác đọc cũng có thể làm thay đổi thứ tự.

`removeEldestEntry` được kiểm tra sau khi thêm mapping mới. Vì vậy nó là chính sách loại entry đơn giản theo kích thước, không thay thế đầy đủ cho cache có TTL, tải lại dữ liệu, thống kê hit/miss hoặc đồng bộ đa luồng.

---

## 9. `TreeMap`, `SortedMap` và `NavigableMap`

`TreeMap` thường dùng Red-Black Tree và sắp xếp theo key.

### 9.1. Độ phức tạp và comparator

- `put`, `get`, `containsKey`, `remove`: `O(log n)`.
- Natural order yêu cầu key tương thích `Comparable`.
- Có thể truyền `Comparator<? super K>`.
- Comparator trả `0` nghĩa là cùng key đối với `TreeMap`, dù `equals()` là `false`.
- Comparator phải ổn định, bắc cầu và nên nhất quán với `equals()`.

### 9.2. Navigable API

| API | Ý nghĩa |
|---|---|
| `firstKey()`, `lastKey()` | Key đầu/cuối |
| `lowerKey(k)` | Key lớn nhất `< k` |
| `floorKey(k)` | Key lớn nhất `<= k` |
| `ceilingKey(k)` | Key nhỏ nhất `>= k` |
| `higherKey(k)` | Key nhỏ nhất `> k` |
| `lowerEntry`, `floorEntry`, ... | Trả cả key-value entry |
| `pollFirstEntry`, `pollLastEntry` | Lấy và xóa entry biên |
| `descendingMap()` | View thứ tự đảo |

### 9.3. Range view

```java
NavigableMap<Integer, String> schedule = new TreeMap<>();
NavigableMap<Integer, String> morning =
    schedule.subMap(800, true, 1200, false);
```

`subMap`, `headMap`, `tailMap`, `descendingMap` là live view:

- thay đổi hợp lệ phản ánh vào map gốc;
- thêm key ngoài range qua view ném `IllegalArgumentException`;
- cần bản độc lập thì dùng `new TreeMap<>(view)`.

### 9.4. `null`

- Natural-order `TreeMap` không chấp nhận null key.
- Comparator tùy chỉnh chỉ xử lý được null nếu chính comparator định nghĩa thứ tự cho nó.
- Value có thể là `null`.

### 9.5. Red-Black Tree hoạt động như thế nào?

Mỗi key là một node trong cây tìm kiếm nhị phân; `Comparator` hoặc natural ordering quyết định đi trái/phải. Sau `put` hoặc `remove`, `TreeMap` thực hiện recolor và rotation để giữ các bất biến chính của Red-Black Tree:

- Node có màu đỏ hoặc đen.
- Root thường là node đen.
- Node đỏ không có node đỏ làm con trực tiếp.
- Mọi đường từ một node tới lá rỗng có cùng black-height.

Nhờ chiều cao được giữ ở mức `O(log n)`, `get`, `put`, `remove` và các truy vấn biên có chi phí `O(log n)`. Rotation chỉ thay đổi liên kết nội bộ, không thay đổi thứ tự logic của key.

```text
             20(B)
            /     \
        10(R)     30(R)
        /  \       /  \
      5(B) 15(B) 25(B) 40(B)
```

Điều kiện quan trọng nhất ở tầng API là comparator phải nhất quán. Nếu comparator coi hai object là bằng (`compare(a, b) == 0`) thì `TreeMap` chỉ giữ một mapping, ngay cả khi `a.equals(b)` trả `false`. Vì vậy comparator chính là một phần của identity semantics của map.

---

## 10. Các implementation chuyên dụng

### 10.1. `EnumMap`

- Chỉ nhận key thuộc cùng một enum type.
- Thường dùng cấu trúc giống array nội bộ, rất gọn và nhanh.
- Duyệt theo thứ tự khai báo enum.
- Không cho null key nhưng cho null value.
- Không thread-safe; iterator weakly consistent.

```java
EnumMap<Day, String> schedule = new EnumMap<>(Day.class);
schedule.put(Day.MONDAY, "Java");
```

Khi key là enum, thường ưu tiên `EnumMap` hơn `HashMap<Enum, V>`.

### 10.2. `WeakHashMap`

- Key được giữ bằng weak reference.
- Entry có thể tự biến mất sau khi key không còn strong reference và GC xử lý.
- Kích thước có thể thay đổi dù chương trình không gọi mutator.
- Không dùng khi cần lifetime/determinism chính xác.
- Phù hợp cho metadata phụ gắn với object có vòng đời bên ngoài map; không mặc định là một cache hoàn chỉnh.

### 10.3. `IdentityHashMap`

- So sánh key bằng `==`, không bằng `equals()`.
- Cố ý vi phạm contract chung của `Map` về object equality.
- Chỉ dùng cho topology-preserving transform, graph traversal, serialization hoặc thuật toán cần identity semantics.
- Không dùng thay `HashMap` trong business logic thông thường.

### 10.4. `Hashtable` và `Properties`

- `Hashtable` là legacy, synchronized toàn bộ method, không cho null key/value.
- Code mới thường dùng `HashMap`, synchronized wrapper hoặc concurrent map phù hợp.
- `Properties` kế thừa `Hashtable<Object,Object>` vì lý do lịch sử, nhưng API cấu hình nên ưu tiên key/value dạng chuỗi và `getProperty/setProperty`.

### 10.5. Cơ chế hoạt động chi tiết của các map chuyên dụng

#### `EnumMap`: dùng ordinal để truy cập như mảng

```java
enum Status { TODO, IN_PROGRESS, DONE }

EnumMap<Status, String> labels = new EnumMap<>(Status.class);
labels.put(Status.TODO, "Chưa làm");
```

`EnumMap` biết trước toàn bộ tập key vì key là một enum type. Về ý tưởng, nó có thể ánh xạ:

```text
Status.TODO        → ordinal 0 → values[0]
Status.IN_PROGRESS → ordinal 1 → values[1]
Status.DONE        → ordinal 2 → values[2]
```

Vì vậy nó không cần tính hash, không có collision bucket và thường nhanh/gọn hơn `HashMap<Status, V>`. Nó duyệt theo thứ tự khai báo enum, không theo thứ tự `put`. Key `null` không hợp lệ vì không xác định được enum type/ordinal; value `null` vẫn được phép.

Không lưu `ordinal()` ra database hoặc message để làm ID bền vững. Nếu thứ tự enum thay đổi, ordinal thay đổi; đây là lý do ordinal chỉ nên là chi tiết nội bộ của `EnumMap`.

#### `WeakHashMap`: key là weak reference

`WeakHashMap` không giữ key bằng strong reference thông thường. Khi bên ngoài map không còn strong reference tới key, garbage collector có thể thu hồi key. Sau đó entry tương ứng có thể bị xóa khỏi map:

```java
Map<Object, String> metadata = new WeakHashMap<>();

Object key = new Object();
metadata.put(key, "metadata");
// key còn strong reference nên entry vẫn còn.

key = null;
// Sau GC, entry có thể biến mất; thời điểm GC không xác định.
```

Mental model:

```text
WeakHashMap entry
    ├── WeakReference(key) ──> object key
    └── value

key không còn strong reference
    → GC thu hồi key
    → ReferenceQueue báo key đã mất
    → map expunge entry tương ứng
```

Không được dựa vào kích thước hoặc thời điểm xóa entry để thực hiện business logic. Nếu value giữ strong reference ngược về key, value có thể giữ key sống và ngăn việc thu hồi; hãy kiểm tra object graph.

`WeakHashMap` phù hợp metadata phụ thuộc vào vòng đời object bên ngoài, không phải cache TTL/LRU đầy đủ. Cache production cần eviction policy, giới hạn kích thước, metric và behavior khi miss rõ ràng.

#### `IdentityHashMap`: bảng băm theo identity

`IdentityHashMap` dùng `==` cho key, không dùng `equals()`:

```java
String first = new String("task");
String second = new String("task");

Map<String, Integer> normal = new HashMap<>();
normal.put(first, 1);
normal.put(second, 2);
// size = 1 vì first.equals(second)

Map<String, Integer> identity = new IdentityHashMap<>();
identity.put(first, 1);
identity.put(second, 2);
// size = 2 vì first != second
```

Về ý tưởng, map dùng identity hash của object reference:

```text
key reference → identity hash → slot
slot           → kiểm tra key == storedKey
```

Nó hữu ích khi duyệt graph object và cần biết “đã gặp đúng instance này chưa”, kể cả hai object có dữ liệu bằng nhau. Không dùng cho user ID, String key hoặc business equality thông thường. `IdentityHashMap` cũng cố ý khác contract equality thông thường của `Map`, nên cần đọc kỹ Javadoc trước khi truyền nó cho code generic.

#### `ConcurrentHashMap`: hash table với cập nhật đồng thời

`ConcurrentHashMap` chia sẻ cấu trúc hash table giữa nhiều thread nhưng không khóa toàn bộ map cho mỗi thao tác. Đọc thường không cần khóa toàn cục; cập nhật tranh chấp được xử lý bằng cơ chế CAS và đồng bộ cục bộ trên vùng cần thiết trong implementation hiện đại.

```java
ConcurrentHashMap<String, Long> counts = new ConcurrentHashMap<>();
counts.merge("java", 1L, Long::sum); // atomic cho key này theo contract
```

Mental model:

```text
get(key)
    → tính hash
    → đọc bucket/node an toàn giữa nhiều thread

put/compute/merge
    → thử cập nhật không khóa bằng CAS
    → nếu collision/tranh chấp, đồng bộ cục bộ
    → publish kết quả cho thread khác
```

Các điểm quan trọng:

- Không cho `null` key/value để tránh nhập nhằng giữa “chưa có mapping” và “mapping tới null” trong concurrent computation.
- `putIfAbsent`, `computeIfAbsent`, `compute`, `merge` có semantics atomic theo từng thao tác.
- Atomic từng thao tác không làm chuỗi nhiều thao tác thành transaction.
- Mapping function chạy trong lúc cập nhật; phải ngắn, không blocking lâu và không gọi đệ quy sửa chính map.
- Iterator là weakly consistent: có thể thấy update xảy ra trong lúc duyệt, không phải snapshot và không ném `ConcurrentModificationException` như map thường.

Ví dụ không atomic:

```java
if (!counts.containsKey("java")) {
    counts.put("java", 1L); // hai thread có thể cùng thấy chưa có
}
```

Viết thành:

```java
counts.merge("java", 1L, Long::sum);
```

#### `ConcurrentSkipListMap`: skip list có thứ tự

`ConcurrentSkipListMap` giữ key theo thứ tự bằng nhiều tầng linked list:

```text
level 2:  ─────── 10 ───────────── 40 ─────
level 1:  ── 3 ── 10 ───── 25 ──── 40 ─────
level 0:  1 ─ 3 ─ 7 ─ 10 ─ 18 ─ 25 ─ 40 ─ 50
```

Tầng trên cho phép bỏ qua nhiều node khi tìm kiếm; tầng 0 chứa toàn bộ key theo thứ tự. Đây là cấu trúc concurrent sorted map, không phải hash table. Lookup/update có expected `O(log n)`, cung cấp `firstKey`, range view và navigation API.

Nó phù hợp khi cần vừa concurrent vừa sorted/range query. Nếu chỉ cần concurrent lookup không ordering, `ConcurrentHashMap` thường phù hợp hơn. Nếu chỉ cần sorted single-thread, `TreeMap` đơn giản và thường ít overhead hơn.

---

## 11. Thread safety và concurrent map

### 11.1. `Collections.synchronizedMap`

```java
Map<String, Integer> synchronizedMap =
    Collections.synchronizedMap(new HashMap<>());

synchronized (synchronizedMap) {
    for (Map.Entry<String, Integer> entry : synchronizedMap.entrySet()) {
        // duyệt dưới monitor của wrapper
    }
}
```

- Đồng bộ method riêng lẻ.
- Phải khóa ngoài khi duyệt view.
- Compound action vẫn cần khóa ngoài nếu phải atomic.

### 11.2. `ConcurrentHashMap`

- Không cho null key/value.
- `get` có concurrency cao; update dùng CAS và đồng bộ cục bộ khi cần trong OpenJDK hiện đại.
- Không còn mô tả chính xác bằng “Segment locking” như thiết kế Java 7.
- Iterator/view weakly consistent, không ném `ConcurrentModificationException`.
- `putIfAbsent`, conditional `remove/replace`, `compute`, `merge` cung cấp thao tác atomic theo contract.
- Mapping/remapping function nên ngắn, không blocking lâu và không tự sửa map theo cách đệ quy nguy hiểm.
- Các thao tác aggregate trong khi có update đồng thời không đại diện cho một snapshot toàn cục ổn định.

### 11.3. `ConcurrentSkipListMap`

- Concurrent và sorted theo key.
- Dựa trên skip list.
- Thao tác chính có expected average `O(log n)`.
- Có `ConcurrentNavigableMap` và live range views.
- Không cho null key/value; iterator weakly consistent.

---

## 12. Mutable, unmodifiable và immutable map

| Cách tạo | Sửa cấu trúc | Quan hệ với nguồn | `null` | Duplicate key |
|---|---|---|---|---|
| `new HashMap<>(source)` | Được | Shallow copy độc lập về cấu trúc | Cho phép | Key sau ghi đè key trước khi xây source |
| `Collections.unmodifiableMap(source)` | Không qua view | Live read-only view | Theo source | Theo source |
| `Map.of(...)` | Không | Unmodifiable value-based map | Không | Ném `IllegalArgumentException` |
| `Map.ofEntries(...)` | Không | Như `Map.of`, tiện cho nhiều entry | Không | Ném `IllegalArgumentException` |
| `Map.copyOf(source)` | Không | Unmodifiable shallow copy | Không | Source đã phải có key duy nhất |
| `Collections.singletonMap(k,v)` | Không | Map một entry | Cho phép null | Không áp dụng |

Các điểm phải nhớ:

- Không dựa vào iteration order của `Map.of`/`Map.copyOf`.
- `Collections.unmodifiableMap` là view, không phải snapshot.
- Unmodifiable map không làm key/value object immutable.
- Tất cả đều shallow ở mức object key/value.
- `Map.entry(k,v)` tạo unmodifiable entry và không chấp nhận null.

---

## 13. Bảng so sánh implementation chính

| Loại | Thứ tự | Thao tác chính | Null key/value | Thread-safe | Cơ chế |
|---|---|---:|---|---|---|
| `HashMap` | Không cam kết | `O(1)` trung bình | Có/Có | Không | Hash table |
| `LinkedHashMap` | Insertion/access order | `O(1)` trung bình | Có/Có | Không | Hash + linked order |
| `TreeMap` | Sorted by key | `O(log n)` | Tùy comparator/Có | Không | Red-Black Tree |
| `EnumMap` | Thứ tự enum | Rất nhanh | Không/Có | Không | Enum-indexed storage |
| `Hashtable` | Không cam kết | `O(1)` trung bình | Không/Không | Có, legacy | Synchronized hash table |
| `ConcurrentHashMap` | Không cam kết | Gần `O(1)` trung bình | Không/Không | Có | Concurrent hash table |
| `ConcurrentSkipListMap` | Sorted | Expected `O(log n)` | Không/Không | Có | Skip list |

---

## 14. Cách chọn implementation

| Nhu cầu | Lựa chọn thường phù hợp |
|---|---|
| Map mặc định, không cần thứ tự | `HashMap` |
| Giữ thứ tự chèn | `LinkedHashMap` |
| LRU/access order | `LinkedHashMap` cấu hình access order |
| Sorted/range/nearest-key query | `TreeMap` |
| Key là enum | `EnumMap` |
| Metadata có lifetime theo key | `WeakHashMap`, sau khi hiểu rõ GC semantics |
| Identity semantics thật sự | `IdentityHashMap` |
| Concurrent unordered | `ConcurrentHashMap` |
| Concurrent sorted | `ConcurrentSkipListMap` |
| Dữ liệu cố định, không null | `Map.of`/`Map.copyOf` |

Không chọn chỉ dựa vào Big-O; cần xét order, size, read/write ratio, range query, memory, null policy, atomicity và profiling.

---

## 15. Stream và collector

```java
Map<Long, User> byId = users.stream().collect(
    Collectors.toMap(User::id, Function.identity())
);
```

### 15.1. Duplicate key

`Collectors.toMap(keyMapper, valueMapper)` ném `IllegalStateException` nếu hai phần tử tạo cùng key. Phải chọn merge policy:

```java
Map<String, User> byEmail = users.stream().collect(
    Collectors.toMap(
        User::email,
        Function.identity(),
        (first, second) -> first
    )
);
```

### 15.2. Cần implementation/order cụ thể

```java
Map<Long, User> ordered = users.stream().collect(
    Collectors.toMap(
        User::id,
        Function.identity(),
        (a, b) -> a,
        LinkedHashMap::new
    )
);
```

- Không giả định `toMap` trả implementation cụ thể.
- `toUnmodifiableMap` không cho null key/value.
- `groupingBy` phù hợp khi một key có nhiều value.
- Concurrent collector chỉ hữu ích khi toàn pipeline và downstream collector phù hợp.

---

## 16. Pattern thường gặp

- Frequency count/histogram bằng `merge`.
- Grouping one-to-many bằng `computeIfAbsent`.
- Index/cache tra cứu object theo ID.
- Two Sum bằng value-to-index map.
- Group Anagrams bằng canonical key.
- Memoization/cache có chính sách rõ ràng.
- LRU bằng access-order `LinkedHashMap`.
- Sweep line/range query bằng `TreeMap`.
- Dispatch table: map command/type sang handler.
- Adjacency list: `Map<Node, List<Node>>`.

---

## 17. Lỗi và bẫy phải nhận ra

- [ ] Dùng mutable key rồi thay field tham gia `equals/hashCode`.
- [ ] Override `equals` nhưng quên `hashCode`.
- [ ] Nghĩ `get(key) == null` luôn có nghĩa key không tồn tại.
- [ ] Gọi `containsValue` như thể là `O(1)`.
- [ ] Dựa vào thứ tự quan sát được của `HashMap`.
- [ ] Sửa map trực tiếp trong enhanced `for`.
- [ ] Nghĩ `keySet/values/entrySet` là copy độc lập.
- [ ] Gọi `add` trên collection view.
- [ ] Comparator trả `0` cho hai key cần giữ riêng trong `TreeMap`.
- [ ] Nghĩ range map/descending map là copy.
- [ ] Dùng mapping function có side effect hoặc sửa lại chính map.
- [ ] Quên rằng remapping function trả `null` có thể xóa/không tạo mapping.
- [ ] Dùng `HashMap` khi nhiều thread cùng ghi.
- [ ] Mô tả `ConcurrentHashMap` Java 8+ bằng Segment locking.
- [ ] Nghĩ synchronized method làm cả chuỗi thao tác atomic.
- [ ] Dùng `Hashtable` cho code mới không có lý do legacy.
- [ ] Dùng `WeakHashMap` và kỳ vọng thời điểm entry biến mất xác định.
- [ ] Dùng `IdentityHashMap` cho business equality thông thường.
- [ ] Dùng `Collectors.toMap` mà không xử lý duplicate key.
- [ ] Nghĩ unmodifiable map làm key/value bên trong immutable.
- [ ] Phụ thuộc ngưỡng treeify/grow như API contract.

---

## 18. Lộ trình học đề xuất

### Mức 1 — Cơ bản

- [ ] Key-value, unique key, duplicate value.
- [ ] `put`, `get`, `remove`, `containsKey`, `getOrDefault`.
- [ ] Duyệt `entrySet`.
- [ ] `HashMap` vs `LinkedHashMap` vs `TreeMap`.

### Mức 2 — Làm việc thực tế

- [ ] `equals/hashCode` và mutable key.
- [ ] `keySet/values/entrySet` live views.
- [ ] `putIfAbsent`, conditional replace/remove.
- [ ] `computeIfAbsent`, `compute`, `merge`.
- [ ] Bucket, collision, resize và treeification.
- [ ] `NavigableMap` và range view.

### Mức 3 — Chuyên sâu

- [ ] `EnumMap`, `WeakHashMap`, `IdentityHashMap`.
- [ ] `ConcurrentHashMap` atomic operations.
- [ ] `ConcurrentSkipListMap`.
- [ ] Fail-fast và weakly consistent iterator.
- [ ] Stream collector, duplicate key và merge policy.
- [ ] JMH benchmark/memory trade-off.

### Mức 4 — Theo phiên bản Java

- [ ] Java 9+: `Map.of`, `Map.ofEntries`, `Map.entry`.
- [ ] Java 10+: `Map.copyOf`, `Collectors.toUnmodifiableMap`.
- [ ] Java 21+: `SequencedMap` và encounter-order API.
- [ ] Kiểm tra JDK dự án trước khi dùng API mới.

---

## 19. Bài thực hành

1. Viết `UserKey` đúng `equals/hashCode`, sau đó tái hiện lỗi mutable key.
2. Minh họa `get == null` và `containsKey` với null value.
3. Duyệt/xóa qua `entrySet` iterator và `removeIf`.
4. Viết word frequency bằng `merge`.
5. Group object bằng `computeIfAbsent`.
6. Cài `MyHashMap<K,V>` có bucket, collision và resize.
7. Cài LRU cache bằng `LinkedHashMap`, viết test access order.
8. Giải range query/sweep line bằng `TreeMap`.
9. Dùng `EnumMap` cho lịch hoặc state transition table.
10. So sánh fail-fast `HashMap` và weakly consistent `ConcurrentHashMap` iterator.
11. Viết collector xử lý duplicate key theo ba policy: giữ đầu, giữ cuối, gộp.
12. Dùng JMH so sánh lookup của `HashMap`, `TreeMap` và linear search.

---

## 20. Câu hỏi tự kiểm tra

1. Vì sao `Map` không extends `Collection`?
2. `put` trả về gì khi key mới và khi ghi đè?
3. Khi nào phải dùng `containsKey` thay vì chỉ `get`?
4. Vì sao mutable key có thể làm entry không tìm thấy?
5. Collision khác duplicate key thế nào?
6. HashMap resize/treeification giải quyết vấn đề gì?
7. `keySet`, `values`, `entrySet` thay đổi map gốc ra sao?
8. `computeIfAbsent`, `compute`, `merge` khác nhau thế nào?
9. Access-order `LinkedHashMap` giúp xây LRU ra sao?
10. Vì sao comparator trả `0` có thể làm `TreeMap` ghi đè entry?
11. Range view của `TreeMap` ảnh hưởng map gốc thế nào?
12. Khi nào dùng `EnumMap`, `WeakHashMap`, `IdentityHashMap`?
13. `Map.of`, `Map.copyOf`, `unmodifiableMap` khác nhau thế nào?
14. `ConcurrentHashMap` khác synchronized map thế nào?
15. Vì sao `Collectors.toMap` có thể ném duplicate-key exception?

---

## 21. Tài liệu liên quan trong repo

- [Tổng quan Collections Framework](../01-collections-framework.md)
- [Bài giải thích chi tiết về Map](../04-map-chi-tiet.md)
- [Bài tập Map](../08-map-bai-tap.md)

## 22. Tài liệu Java chính thức

- [Java SE 21 — `Map`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Map.html)
- [Java SE 21 — `HashMap`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/HashMap.html)
- [Java SE 21 — `LinkedHashMap`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/LinkedHashMap.html)
- [Java SE 21 — `TreeMap`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/TreeMap.html)
- [Java SE 21 — `EnumMap`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/EnumMap.html)
- [Java SE 21 — `WeakHashMap`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/WeakHashMap.html)
- [Java SE 21 — `IdentityHashMap`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/IdentityHashMap.html)
- [Java SE 21 — `ConcurrentHashMap`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html)
- [Java SE 21 — `ConcurrentSkipListMap`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ConcurrentSkipListMap.html)
- [Java SE 21 — `Collectors`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/Collectors.html)

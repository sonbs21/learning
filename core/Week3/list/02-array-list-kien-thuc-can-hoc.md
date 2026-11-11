# Java `ArrayList` — Mảng động và cách sử dụng hiệu quả

> `ArrayList` là implementation `List` mặc định trong rất nhiều trường hợp: truy cập theo index nhanh, duyệt tuần tự hiệu quả và thêm phần tử ở cuối có chi phí amortized thấp.
>
> Tài liệu này bổ sung cho [kiến thức tổng quan về `List`](./01-list-kien-thuc-can-hoc.md).

---

## 1. Mục tiêu đầu ra

Sau khi học xong, cần có khả năng:

- [ ] Giải thích `ArrayList` dùng mảng động như thế nào.
- [ ] Phân biệt `size` và `capacity`.
- [ ] Tính đúng độ phức tạp của get/add/remove/search.
- [ ] Hiểu resize, `ensureCapacity` và `trimToSize`.
- [ ] Dùng constructor và các API mutation đúng cách.
- [ ] Tránh lỗi do `Integer` boxing, `subList`, mutable element và iterator.
- [ ] Phân biệt shallow copy, view, unmodifiable list và immutable list.
- [ ] Biết khi nào `ArrayList` phù hợp hơn `LinkedList` hoặc implementation khác.

---

## 2. `ArrayList` là gì?

`ArrayList<E>` là một `List` triển khai bằng mảng có thể tự mở rộng:

- Cho phép phần tử trùng lặp.
- Giữ thứ tự chèn.
- Cho phép `null`.
- Truy cập bằng index có độ phức tạp `O(1)`.
- `add` ở cuối có chi phí amortized `O(1)`.
- Chèn/xóa ở giữa cần dịch phần tử, thường là `O(n)`.
- Không thread-safe.

```java
List<String> languages = new ArrayList<>();
languages.add("Java");
languages.add("Kotlin");
languages.add("Java"); // duplicate hợp lệ

System.out.println(languages.get(0)); // Java
```

### 2.1. Use case phù hợp

- Đọc phần tử theo index nhiều lần.
- Duyệt tuần tự hoặc random access.
- Thêm phần tử chủ yếu ở cuối.
- Cần một collection tổng quát, dễ dùng và có locality of reference tốt.
- Chuyển đổi từ collection khác sang list độc lập về cấu trúc.

### 2.2. Use case không phù hợp

- Thêm/xóa liên tục ở đầu: cân nhắc `ArrayDeque` hoặc cấu trúc chuyên dụng.
- Chèn/xóa giữa list rất thường xuyên và đã có iterator/node phù hợp: cân nhắc `LinkedList`, nhưng phải đo thực tế.
- Cần thread-safe: dùng wrapper hoặc collection concurrent phù hợp.
- Cần primitive list hiệu quả memory: Java Collections không lưu primitive trực tiếp; cân nhắc thư viện primitive collection nếu cần.

---

## 3. Mảng động bên trong

### 3.1. `size` và `capacity`

`ArrayList` có hai khái niệm cần tách biệt:

- **size**: số phần tử đang có trong list;
- **capacity**: số phần tử mà mảng nội bộ có thể chứa trước khi cần cấp phát lại.

```text
capacity = 8
size     = 3

[A] [B] [C] [ ] [ ] [ ] [ ] [ ]
```

`size()` trả `3`, không trả `8`. Vùng trống là capacity dự phòng, không phải phần tử `null` thực sự trong list.

### 3.2. Thêm phần tử

```text
add(element)
    ├── nếu còn ô trống → ghi vào mảng tại index size
    ├── tăng size
    └── nếu hết capacity
            ├── cấp mảng lớn hơn
            ├── copy reference từ mảng cũ
            └── ghi phần tử mới
```

Resize không xảy ra ở mọi lần `add`. Các lần thêm bình thường rất nhanh; thỉnh thoảng một lần thêm phải copy cả mảng.

### 3.3. Tỷ lệ tăng trưởng là implementation detail

JDK có thể dùng chiến lược tăng capacity khác nhau giữa các phiên bản. Không viết code phụ thuộc vào việc capacity tăng chính xác bao nhiêu phần trăm.

Điều cần nhớ là tổng chi phí của nhiều lần append vẫn amortized `O(1)`, còn một lần resize riêng lẻ có thể là `O(n)`.

### 3.4. Locality of reference

Các reference nằm liên tiếp trong mảng giúp CPU cache và duyệt tuần tự thường hiệu quả. Đây là một lý do `ArrayList` thường nhanh trong thực tế dù một số thao tác có cùng Big-O với collection khác.

---

## 4. Constructor và khởi tạo

```java
List<String> empty = new ArrayList<>();

List<String> sized = new ArrayList<>(100);

List<String> copy = new ArrayList<>(Set.of("A", "B"));
```

### 4.1. Constructor nhận capacity

```java
ArrayList<String> values = new ArrayList<>(1_000);
```

Tham số này là initial capacity, không phải size:

```java
ArrayList<String> values = new ArrayList<>(100);
values.size(); // 0
```

Không thể gọi `set(0, value)` ngay sau constructor capacity vì chưa có phần tử:

```java
values.set(0, "A"); // IndexOutOfBoundsException
```

Phải dùng `add` để tạo phần tử:

```java
values.add("A");
```

### 4.2. Copy constructor là shallow copy

```java
List<User> copy = new ArrayList<>(source);
```

Mảng chứa reference mới nhưng các object `User` vẫn là cùng object:

```text
source list ─┐
             ├──> User object
copy list  ──┘
```

Thay đổi cấu trúc một list không đổi list kia, nhưng thay đổi một `User` mutable có thể quan sát được từ cả hai list.

---

## 5. Độ phức tạp

| Thao tác | Độ phức tạp | Ghi chú |
|---|---:|---|
| `get(index)` | `O(1)` | Tính địa chỉ trực tiếp |
| `set(index, value)` | `O(1)` | Không đổi size |
| `add(value)` cuối | Amortized `O(1)` | Resize riêng lẻ có thể `O(n)` |
| `add(index, value)` | `O(n)` | Dịch phần tử phía sau |
| `remove(index)` | `O(n)` | Dịch phần tử phía sau |
| `remove(object)` | `O(n)` | Tìm tuyến tính rồi dịch |
| `contains`/`indexOf` | `O(n)` | Duyệt tuyến tính |
| `size`/`isEmpty` | `O(1)` | Theo dõi trực tiếp |
| duyệt tuần tự | `O(n)` | Cache-friendly |
| `clear` | `O(n)` | Xóa reference để tránh giữ object |

Big-O không thể hiện toàn bộ hiệu năng. `ArrayList` thường thắng trong duyệt và random access; linked structure có thể có trade-off khác về cache và allocation.

---

## 6. API đọc và cập nhật

### 6.1. `get`, `set`, `size`

```java
ArrayList<String> values = new ArrayList<>(List.of("A", "B", "C"));

String first = values.get(0); // A
values.set(1, "B2");         // thay phần tử, không đổi size

int count = values.size();
boolean empty = values.isEmpty();
```

Index đọc/sửa hợp lệ từ `0` đến `size() - 1`. Index bằng `size()` không hợp lệ cho `get` và `set`.

### 6.2. `add`

```java
values.add("D");          // thêm cuối
values.add(1, "inserted"); // chèn tại index 1
```

Index hợp lệ khi chèn là từ `0` đến `size()`, vì chèn ở cuối tương đương `add(value)`.

### 6.3. `remove` và overload với `Integer`

Đây là bẫy kinh điển:

```java
List<Integer> numbers = new ArrayList<>(List.of(10, 20, 30));

numbers.remove(1);              // xóa theo index, kết quả [10, 30]
numbers.remove(Integer.valueOf(30)); // xóa theo object value
```

Vì `remove(int)` và `remove(Object)` cùng tồn tại, literal `1` được chọn là index. Nếu muốn xóa value integer, dùng `Integer.valueOf(value)` hoặc `removeIf`.

### 6.4. Batch operations

```java
values.addAll(List.of("E", "F"));
values.addAll(1, List.of("X", "Y"));
values.removeAll(Set.of("A", "B"));
values.retainAll(Set.of("C", "D"));
```

Các method mutation thay đổi list gọi method. Nếu cần giữ input, tạo copy trước.

---

## 7. Tìm kiếm, equality và `null`

### 7.1. Tìm kiếm tuyến tính

```java
values.contains("A");
values.indexOf("A");
values.lastIndexOf("A");
```

Các method này dùng equality của phần tử và duyệt từ đầu hoặc cuối. Chúng không có lookup `O(1)` như `HashSet`.

Nếu cần kiểm tra membership nhiều lần, cân nhắc tạo `HashSet` riêng:

```java
Set<String> membership = new HashSet<>(values);
```

Đừng tạo set chỉ cho một lookup trên list nhỏ nếu chi phí tạo set lớn hơn lợi ích.

### 7.2. `contains` dùng equality

```java
List<UserId> ids = new ArrayList<>();
ids.add(new UserId(42));

ids.contains(new UserId(42)); // true nếu UserId override equality đúng
```

Nếu class không override `equals`, hai object có cùng dữ liệu nhưng khác reference thường không được coi là bằng nhau.

### 7.3. `null`

`ArrayList` cho phép nhiều `null`:

```java
List<String> values = new ArrayList<>();
values.add(null);
values.add(null);

values.size(); // 2
```

Các method như `contains(null)` và `remove(null)` hoạt động theo equality null-safe của collection. Nhưng lambda/method reference của ứng dụng phải tự xử lý null:

```java
values.removeIf(value -> value == null || value.isBlank());
```

---

## 8. `ensureCapacity` và `trimToSize`

Hai method này thuộc API của `ArrayList`, không phải mọi `List`:

```java
ArrayList<String> values = new ArrayList<>();
values.ensureCapacity(10_000);
```

`ensureCapacity` có thể giảm số lần resize khi biết trước workload append lớn. Chỉ dùng khi đã hiểu profile dữ liệu; không cần gọi cho list nhỏ thông thường.

```java
values.trimToSize();
```

`trimToSize` yêu cầu giảm capacity về gần size hiện tại. Nó có thể tiết kiệm memory nhưng làm mất vùng dự phòng; nếu tiếp tục append nhiều, list có thể phải resize lại.

Không dùng hai method này qua biến khai báo kiểu `List`:

```java
List<String> values = new ArrayList<>();
// values.ensureCapacity(100); // không compile vì List không có API này
```

Hãy chỉ expose chúng khi capacity là một phần của quyết định implementation.

---

## 9. Iterator, `ListIterator` và fail-fast

### 9.1. Duyệt cơ bản

```java
for (String value : values) {
    System.out.println(value);
}

values.forEach(System.out::println);
```

### 9.2. Xóa trong lúc duyệt

Không xóa trực tiếp khỏi list trong enhanced `for`:

```java
for (String value : values) {
    if (value != null && value.isBlank()) {
        values.remove(value); // có thể ném ConcurrentModificationException
    }
}
```

Dùng `removeIf`:

```java
values.removeIf(value -> value != null && value.isBlank());
```

Hoặc dùng iterator:

```java
Iterator<String> iterator = values.iterator();
while (iterator.hasNext()) {
    if (iterator.next() == null) {
        iterator.remove();
    }
}
```

### 9.3. `ListIterator`

`ListIterator` hỗ trợ đi hai chiều và cập nhật/chèn trong vị trí hiện tại:

```java
ListIterator<String> iterator = values.listIterator();
while (iterator.hasNext()) {
    String value = iterator.next();
    if ("old".equals(value)) {
        iterator.set("new");
    }
}
```

Các thao tác chính:

- `next`, `previous`: di chuyển;
- `set`: thay phần tử vừa trả về;
- `add`: chèn trước phần tử tiếp theo;
- `remove`: xóa phần tử vừa trả về.

### 9.4. Fail-fast là best effort

Iterator của `ArrayList` thường phát hiện structural modification ngoài iterator và có thể ném `ConcurrentModificationException`. Đây là cơ chế phát hiện lỗi best effort, không phải thread safety và không phải bảo đảm tuyệt đối.

---

## 10. `subList` là live view

```java
List<String> source = new ArrayList<>(List.of("A", "B", "C", "D"));
List<String> middle = source.subList(1, 3); // [B, C], range [1, 3)

middle.set(0, "B2");
System.out.println(source); // [A, B2, C, D]
```

`subList(from, to)` không tạo bản copy độc lập:

- thay đổi phần tử qua view phản ánh vào list gốc;
- thay đổi cấu trúc qua view phản ánh vào list gốc;
- thay đổi cấu trúc list gốc trong khi dùng view có thể làm view mất tính hợp lệ và gây `ConcurrentModificationException`;
- range dùng lower-inclusive, upper-exclusive.

Nếu cần snapshot:

```java
List<String> snapshot = new ArrayList<>(source.subList(1, 3));
```

### 10.1. Xóa một range

```java
source.subList(from, to).clear();
```

Đây là pattern hữu ích để xóa một đoạn liên tiếp, nhưng phải kiểm tra index và hiểu rằng list gốc bị thay đổi.

---

## 11. Copy, view và immutable list

| Cách tạo | Cấu trúc | Quan hệ với nguồn |
|---|---|---|
| `new ArrayList<>(source)` | Mutable copy | Độc lập về cấu trúc |
| `source.subList(a, b)` | Live view | Gắn với source |
| `Collections.unmodifiableList(source)` | Read-only view | Source đổi thì view đổi |
| `List.copyOf(source)` | Unmodifiable copy | Không phản ánh cấu trúc source |
| `List.of(...)` | Unmodifiable value-based list | Không cho null |

```java
List<String> source = new ArrayList<>(List.of("A", "B"));
List<String> view = Collections.unmodifiableList(source);
List<String> copy = List.copyOf(source);

source.add("C");
// view thấy C; copy không thấy C
```

Read-only container không làm object bên trong immutable. Đây đều là shallow copy/view.

---

## 12. `sort`, `replaceAll` và thao tác hàng loạt

```java
ArrayList<String> names = new ArrayList<>(List.of("Bob", "An", "Chi"));

names.sort(Comparator.naturalOrder());
names.replaceAll(String::toUpperCase);
```

`sort` thay đổi thứ tự trong list. `replaceAll` thay từng reference bằng kết quả của operator.

Không nên sửa cấu trúc list trong lambda đang chạy trên chính list nếu điều đó làm invalid iterator hoặc pipeline:

```java
// Không nên thêm/xóa names bên trong operator của chính names
names.replaceAll(value -> value.trim());
```

Nếu cần tạo kết quả mới thay vì mutation:

```java
List<String> normalized = names.stream()
        .map(String::trim)
        .toList();
```

---

## 13. Stream và collector

### 13.1. Tạo `ArrayList` rõ ràng

```java
ArrayList<String> result = values.stream()
        .filter(Objects::nonNull)
        .collect(Collectors.toCollection(ArrayList::new));
```

Không suy ra implementation từ `Collectors.toList()` nếu code cần chính xác `ArrayList`, mutability hoặc capacity behavior.

### 13.2. `toList()` và khả năng sửa

```java
List<String> result = values.stream().toList();
```

Kết quả `Stream.toList()` không nên được coi là mutable `ArrayList`. Nếu cần list mutable rõ ràng:

```java
List<String> mutable = new ArrayList<>(values.stream().toList());
```

### 13.3. Duyệt song song

`ArrayList` hỗ trợ chia vùng dữ liệu tốt cho một số parallel stream workload, nhưng parallel không tự động nhanh hơn. Chi phí task, kích thước dữ liệu và công việc trong lambda quyết định hiệu quả; hãy benchmark trước khi dùng.

---

## 14. Thread safety

`ArrayList` không thread-safe. Nhiều thread cùng sửa có thể gây mất update, trạng thái không nhất quán hoặc lỗi khi duyệt.

### 14.1. Synchronized wrapper

```java
List<String> synchronizedList =
        Collections.synchronizedList(new ArrayList<>());

synchronized (synchronizedList) {
    for (String value : synchronizedList) {
        // Phải khóa khi duyệt theo contract của wrapper.
    }
}
```

Wrapper đồng bộ method riêng lẻ. Chuỗi nhiều bước như “contains rồi add” vẫn cần lock ngoài nếu cần atomicity.

### 14.2. `CopyOnWriteArrayList`

```java
List<String> listeners = new CopyOnWriteArrayList<>();
```

Phù hợp khi:

- đọc và duyệt rất nhiều;
- ghi rất ít;
- cần iterator snapshot ổn định.

Mỗi lần ghi tạo/copy mảng mới, vì vậy không phù hợp cho list lớn hoặc workload ghi nhiều. Nó không phải bản nâng cấp tổng quát của `ArrayList`.

---

## 15. Java version và `SequencedCollection`

Từ Java 21, `ArrayList` thuộc nhóm collection có thứ tự và hỗ trợ các API của `SequencedCollection` như thao tác đầu/cuối theo JDK tương ứng.

Kiểm tra version JDK của project trước khi dùng API Java 21. Code cần chạy trên Java 8/11/17 không được giả định có method mới.

Với code portable giữa nhiều Java version, các API quen thuộc như `get(0)`, `get(size - 1)`, `add(0, value)` vẫn cần được dùng với kiểm tra list rỗng và chi phí phù hợp.

---

## 16. `ArrayList` so với `LinkedList`

| Nhu cầu | `ArrayList` | `LinkedList` |
|---|---:|---:|
| `get(index)` | `O(1)` | `O(n)` |
| `set(index)` | `O(1)` | `O(n)` |
| add cuối | Amortized `O(1)` | `O(1)` nếu có node cuối |
| add đầu | `O(n)` | `O(1)` |
| remove đầu | `O(n)` | `O(1)` |
| remove giữa theo index | `O(n)` dịch mảng | Tìm node vẫn có thể `O(n)` |
| duyệt tuần tự | Thường nhanh, cache-friendly | Nhiều pointer traversal |
| memory | Thường thấp hơn | Node/reference overhead cao hơn |
| random access | Phù hợp | Không phù hợp |

Nhiều trường hợp từng được gán cho `LinkedList` thực ra phù hợp hơn với `ArrayDeque`, đặc biệt nếu cần queue/deque ở đầu và cuối. Hãy benchmark workload thật thay vì chọn theo Big-O đơn lẻ.

---

## 17. Pattern sử dụng thực tế

### 17.1. Thu thập kết quả theo thứ tự

```java
List<String> result = new ArrayList<>();
for (String input : inputs) {
    if (isValid(input)) {
        result.add(normalize(input));
    }
}
```

### 17.2. Pre-size cho batch lớn

```java
ArrayList<Record> records = new ArrayList<>(expectedCount);
for (Record record : source) {
    records.add(transform(record));
}
```

Pre-size có thể giảm resize nhưng không thay thế việc đo memory; `expectedCount` quá lớn cũng gây lãng phí.

### 17.3. Snapshot trước khi chia sẻ

```java
List<String> snapshot = List.copyOf(values);
```

Nếu caller cần sửa bản copy:

```java
List<String> mutableCopy = new ArrayList<>(values);
```

### 17.4. Loại phần tử theo điều kiện

```java
values.removeIf(value -> value == null || value.isBlank());
```

Pattern này tránh lỗi sửa list trực tiếp trong enhanced `for`.

---

## 18. Các bẫy thường gặp

- [ ] Nhầm initial capacity với size.
- [ ] Gọi `set` trên index chưa tồn tại thay vì dùng `add`.
- [ ] Nhầm `remove(int)` với `remove(Object)` trong `List<Integer>`.
- [ ] Chèn/xóa đầu list lớn nhiều lần rồi vẫn dùng `ArrayList`.
- [ ] Nghĩ `contains` hoặc `indexOf` là `O(1)`.
- [ ] Nghĩ `add` cuối luôn là `O(1)` ở từng lần gọi.
- [ ] Phụ thuộc chính xác vào tỷ lệ tăng capacity của JDK.
- [ ] Dùng `subList` như bản copy độc lập.
- [ ] Thay đổi cấu trúc list gốc khi đang dùng `subList`.
- [ ] Xóa trực tiếp trong enhanced `for`.
- [ ] Nghĩ fail-fast là thread-safe.
- [ ] Duyệt synchronized wrapper mà không giữ monitor.
- [ ] Nghĩ copy constructor copy sâu object bên trong.
- [ ] Nghĩ `Collections.unmodifiableList` là snapshot độc lập.
- [ ] Nghĩ `Stream.toList()` chắc chắn trả mutable `ArrayList`.
- [ ] Gọi `String::isBlank` khi list có thể chứa `null`.
- [ ] Pre-size quá lớn mà không có dữ liệu đo lường.
- [ ] Dùng `CopyOnWriteArrayList` cho workload ghi nhiều.
- [ ] Dùng `ArrayList` để biểu diễn queue/deque cần xóa ở đầu.

---

## 19. Bài tập tự kiểm tra

1. Viết benchmark `get(index)` trên `ArrayList` và `LinkedList` với nhiều kích thước.
2. Minh họa khác biệt giữa `size` và initial capacity bằng code.
3. Viết ví dụ chứng minh `remove(1)` và `remove(Integer.valueOf(1))` khác nhau.
4. Dùng `subList` để xóa một range, sau đó viết lại bằng snapshot để tránh live view.
5. Tạo list với `ensureCapacity`, thêm nhiều phần tử và đo số lần resize nếu có thể quan sát qua profiling.
6. Viết code duyệt và xóa an toàn bằng `removeIf`, `Iterator.remove` và `ListIterator`.
7. So sánh `ArrayList`, `ArrayDeque` và `LinkedList` cho queue workload.

---

## 20. Tóm tắt cần nhớ

1. `ArrayList` là mảng động, giữ thứ tự và cho phép duplicate/null.
2. `size` là số phần tử; capacity là sức chứa dự phòng của mảng nội bộ.
3. `get` và `set` là `O(1)`; add cuối là amortized `O(1)`.
4. Chèn/xóa giữa hoặc đầu thường là `O(n)` vì phải dịch reference.
5. `remove(int)` và `remove(Object)` là hai overload dễ gây lỗi với `List<Integer>`.
6. `subList` là live view; copy constructor mới tạo bản độc lập về cấu trúc.
7. Iterator fail-fast không đồng nghĩa với thread-safe.
8. Dùng `ArrayList` làm default cho list cần random access và append; chọn cấu trúc khác khi workload yêu cầu đặc biệt.



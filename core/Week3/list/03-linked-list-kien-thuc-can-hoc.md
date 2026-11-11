# Java `LinkedList` — Danh sách liên kết và hàng đợi hai đầu

> `LinkedList` là một doubly-linked list đồng thời triển khai `List` và `Deque`. Nó hỗ trợ chèn/xóa ở đầu/cuối nhanh, nhưng truy cập theo index thường chậm.
>
> Tài liệu này bổ sung cho [kiến thức tổng quan về `List`](./01-list-kien-thuc-can-hoc.md).

---

## 1. Mục tiêu đầu ra

Sau khi học xong, cần có khả năng:

- [ ] Giải thích node, liên kết trước/sau và sentinel của linked list.
- [ ] Tính đúng độ phức tạp của `get`, `add`, `remove` và thao tác ở đầu/cuối.
- [ ] Hiểu vì sao `ListIterator` thay đổi trade-off của chèn/xóa.
- [ ] Dùng `LinkedList` như `List`, `Deque` hoặc `Queue` đúng cách.
- [ ] Phân biệt `LinkedList` với `ArrayList` và `ArrayDeque`.
- [ ] Nhận ra chi phí memory, cache locality và object allocation.
- [ ] Tránh lỗi do index traversal, mutable element, iterator và thread safety.

---

## 2. `LinkedList` là gì?

`LinkedList<E>` là một doubly-linked list:

- mỗi node giữ element, liên kết tới node trước và node sau;
- giữ thứ tự phần tử;
- cho phép duplicate và `null`;
- triển khai `List`, `Deque` và `Queue`;
- thêm/xóa ở đầu/cuối thường `O(1)`;
- truy cập theo index thường `O(n)`;
- không thread-safe.

```java
List<String> values = new LinkedList<>();
values.add("A");
values.add("B");
values.add("A"); // duplicate hợp lệ
```

### 2.1. Khi nào có thể dùng?

- Cần thao tác rõ ràng ở đầu và cuối list.
- Cần một implementation `Deque` có thể chứa `null`.
- Đã có `ListIterator` tại vị trí cần chèn/xóa.
- Cần một cấu trúc list hai chiều cho workload đặc thù.

### 2.2. Khi nào thường không nên dùng?

- Cần `get(index)` nhiều lần: thường chọn `ArrayList`.
- Cần queue/deque hiệu năng cao: thường chọn `ArrayDeque`.
- Cần random access và append: `ArrayList` thường đơn giản, ít memory overhead hơn.
- Cần thread-safe: dùng wrapper hoặc collection concurrent phù hợp.

---

## 3. Cấu trúc bên trong

### 3.1. Node hai chiều

```text
null <- [A] <-> [B] <-> [C] -> null
        first              last
```

Một node khái niệm có dạng:

```java
static final class Node<E> {
    E item;
    Node<E> next;
    Node<E> previous;
}
```

OpenJDK có thể dùng sentinel/first-last representation và tên field khác nhau giữa phiên bản. Code ứng dụng không nên truy cập node nội bộ bằng reflection.

### 3.2. Thêm ở đầu/cuối

```text
addFirst(X)
    ├── tạo node X
    ├── nối X trước first hiện tại
    └── cập nhật first

addLast(X)
    ├── tạo node X
    ├── nối X sau last hiện tại
    └── cập nhật last
```

Vì list giữ reference tới hai đầu, các thao tác này không cần dịch toàn bộ phần tử.

### 3.3. Truy cập theo index

Khi gọi `get(index)`, `LinkedList` chọn đi từ đầu hoặc cuối tùy index gần đầu nào hơn, rồi lần lượt đi qua các node:

```text
get(1)   → first → next
get(n-2) → last → previous
```

Điều này giảm số bước trung bình nhưng vẫn là `O(n)`, không biến random access thành `O(1)`.

---

## 4. Độ phức tạp

| Thao tác | Độ phức tạp | Ghi chú |
|---|---:|---|
| `get(index)` | `O(n)` | Đi qua node từ đầu gần hơn |
| `set(index, value)` | `O(n)` | Tìm node trước rồi thay item |
| `addFirst`, `addLast` | `O(1)` | Có reference tới hai đầu |
| `removeFirst`, `removeLast` | `O(1)` | Gỡ node ở đầu/cuối |
| `add(index, value)` | `O(n)` | Tìm vị trí; nối node sau đó thường `O(1)` |
| `remove(index)` | `O(n)` | Tìm vị trí; gỡ node sau đó thường `O(1)` |
| `add/remove` qua iterator tại vị trí hiện tại | Tìm vị trí `O(n)`, mutation `O(1)` | Lợi ích khi đã có iterator |
| `contains`, `indexOf` | `O(n)` | Duyệt tuyến tính |
| `size`, `isEmpty` | `O(1)` | Theo dõi trực tiếp |
| duyệt tuần tự | `O(n)` | Nhưng kém locality hơn mảng |

Điểm quan trọng: nói “xóa giữa linked list là `O(1)`” chỉ đúng khi đã có reference tới node hoặc iterator tại vị trí đó. Nếu phải tìm node bằng index trước, toàn bộ thao tác vẫn `O(n)`.

---

## 5. Constructor và copy

```java
LinkedList<String> empty = new LinkedList<>();

LinkedList<String> copy = new LinkedList<>(List.of("A", "B", "C"));
```

Constructor nhận `Collection` duyệt source và tạo node theo encounter order của source.

Copy constructor là shallow copy:

```java
List<User> copy = new LinkedList<>(source);
```

Hai list có node và cấu trúc riêng, nhưng reference tới cùng `User` object. Sửa cấu trúc một list không sửa list kia; sửa một `User` mutable có thể thấy từ cả hai.

---

## 6. API `List`

### 6.1. Thêm phần tử

```java
LinkedList<String> values = new LinkedList<>();

values.add("B");       // add cuối
values.addFirst("A");  // add đầu
values.addLast("C");   // add cuối
values.add(1, "B2");   // chèn tại index
```

`add(E)` của `List` thêm vào cuối. `addFirst/addLast` đến từ `Deque` và biểu đạt ý định rõ hơn.

### 6.2. Đọc và cập nhật

```java
String first = values.getFirst();
String last = values.getLast();
String item = values.get(1);

values.set(1, "updated");
```

`getFirst/getLast` ném `NoSuchElementException` nếu list rỗng. Nếu muốn nhận `null` khi rỗng, dùng `peekFirst/peekLast` trong vai trò deque.

### 6.3. Xóa

```java
values.removeFirst();
values.removeLast();
values.remove(0); // remove theo index
values.remove("C"); // remove theo object
```

Với `LinkedList<Integer>`, vẫn có bẫy overload giống `ArrayList`:

```java
List<Integer> numbers = new LinkedList<>(List.of(10, 20, 30));
numbers.remove(1); // xóa index 1, tức 20
numbers.remove(Integer.valueOf(30)); // xóa value 30
```

### 6.4. Tìm kiếm

```java
values.contains("A");
values.indexOf("A");
values.lastIndexOf("A");
```

Các method này là tuyến tính. `LinkedList` không có lookup theo hash.

---

## 7. API `Deque` và `Queue`

`LinkedList` có thể dùng qua interface phù hợp thay vì expose toàn bộ implementation:

```java
Deque<String> deque = new LinkedList<>();
Queue<String> queue = new LinkedList<>();
```

### 7.1. Nhóm API exception và không exception

| Ý định | Ném exception khi lỗi | Trả giá trị đặc biệt |
|---|---|---|
| Thêm đầu | `addFirst(e)` | `offerFirst(e)` |
| Thêm cuối | `addLast(e)` | `offerLast(e)` |
| Xóa đầu | `removeFirst()` | `pollFirst()` |
| Xóa cuối | `removeLast()` | `pollLast()` |
| Đọc đầu | `getFirst()` | `peekFirst()` |
| Đọc cuối | `getLast()` | `peekLast()` |

Với queue:

| Queue | Deque tương đương |
|---|---|
| `add(e)` | thêm cuối |
| `offer(e)` | thêm cuối, trả boolean |
| `remove()` | xóa đầu |
| `poll()` | xóa đầu, trả `null` nếu rỗng |
| `element()` | đọc đầu, exception nếu rỗng |
| `peek()` | đọc đầu, `null` nếu rỗng |

### 7.2. Ví dụ queue

```java
Queue<String> queue = new LinkedList<>();
queue.offer("A");
queue.offer("B");

String next = queue.poll(); // A
String nextAgain = queue.peek(); // B, không xóa
```

### 7.3. Ví dụ deque

```java
Deque<String> deque = new LinkedList<>();
deque.addFirst("middle");
deque.addFirst("first");
deque.addLast("last");

deque.pollFirst(); // first
deque.pollLast();  // last
```

Nếu không cần `List` API và không cần cho phép null, `ArrayDeque` thường là lựa chọn tốt hơn cho queue/deque.

---

## 8. `ListIterator` — trường hợp `LinkedList` có lợi

### 8.1. Chèn/xóa tại vị trí iterator

```java
LinkedList<String> values = new LinkedList<>(List.of("A", "C"));
ListIterator<String> iterator = values.listIterator(1);

iterator.add("B"); // [A, B, C]
```

Khi iterator đã đứng tại vị trí cần thao tác, việc nối hoặc gỡ node không cần dịch cả dãy phần tử. Đây là lý do `ListIterator` quan trọng trong workload linked list.

Nếu mỗi lần đều gọi `list.get(i)` để tìm vị trí, lợi thế này biến mất:

```java
for (int i = 0; i < values.size(); i++) {
    values.get(i); // lặp traversal, có thể thành O(n^2)
}
```

### 8.2. Duyệt hai chiều

```java
ListIterator<String> iterator = values.listIterator(values.size());
while (iterator.hasPrevious()) {
    System.out.println(iterator.previous());
}
```

### 8.3. Xóa an toàn

```java
Iterator<String> iterator = values.iterator();
while (iterator.hasNext()) {
    if (iterator.next().isBlank()) {
        iterator.remove();
    }
}
```

Không xóa trực tiếp khỏi list trong enhanced `for`; dùng `removeIf` hoặc iterator.

---

## 9. `subList`, view và sao chép

`LinkedList.subList(from, to)` vẫn là live view:

```java
LinkedList<String> source = new LinkedList<>(List.of("A", "B", "C", "D"));
List<String> middle = source.subList(1, 3); // [B, C]

middle.set(0, "B2");
System.out.println(source); // [A, B2, C, D]
```

Đặc điểm:

- upper bound exclusive;
- sửa phần tử qua view phản ánh vào source;
- structural modification source khi view đang được dùng có thể gây `ConcurrentModificationException`;
- muốn snapshot thì copy:

```java
List<String> snapshot = new LinkedList<>(source.subList(1, 3));
```

Xóa range:

```java
source.subList(from, to).clear();
```

---

## 10. `null`, equality và mutable element

### 10.1. `null`

`LinkedList` cho phép nhiều `null`:

```java
LinkedList<String> values = new LinkedList<>();
values.add(null);
values.add(null);
```

Đây là khác biệt quan trọng khi so sánh với `ArrayDeque`, vốn không cho `null`.

Các API `peek` dùng `null` để biểu thị deque rỗng, nên nếu dữ liệu thật cũng cho phép null thì caller không thể phân biệt chỉ bằng return value:

```java
Deque<String> deque = new LinkedList<>();
String first = deque.peekFirst();
// first == null có thể là deque rỗng hoặc phần tử đầu là null
```

Vì vậy không nên dùng null element khi code queue cần phân biệt “không có phần tử”.

### 10.2. Equality

`List.equals` phụ thuộc cả phần tử và thứ tự:

```java
List<Integer> a = new LinkedList<>(List.of(1, 2));
List<Integer> b = new ArrayList<>(List.of(1, 2));

a.equals(b); // true
```

Implementation khác nhau không ảnh hưởng nếu nội dung và thứ tự giống nhau.

### 10.3. Mutable element

Thay đổi element bên trong không thay đổi node/link, nhưng có thể làm kết quả equality hoặc logic ứng dụng thay đổi. Nếu list được dùng làm key của map/set khác, đây là rủi ro lớn.

Ưu tiên element immutable hoặc không dùng list mutable làm key.

---

## 11. Iterator và fail-fast

Iterator của `LinkedList` thường fail-fast trên best-effort basis khi có structural modification ngoài iterator:

```java
for (String value : values) {
    if ("remove".equals(value)) {
        values.remove(value); // có thể ném ConcurrentModificationException
    }
}
```

Cách an toàn:

```java
values.removeIf("remove"::equals);
```

Hoặc dùng `Iterator.remove()`/`ListIterator`. Fail-fast chỉ giúp phát hiện một số lỗi; nó không phải thread safety và không phải bảo đảm tuyệt đối.

---

## 12. Thread safety

`LinkedList` không thread-safe. Nhiều thread cùng đọc/ghi có thể tạo race condition và iterator lỗi.

### 12.1. Synchronized wrapper

```java
List<String> synchronizedList =
        Collections.synchronizedList(new LinkedList<>());

synchronized (synchronizedList) {
    for (String value : synchronizedList) {
        // Phải khóa khi duyệt.
    }
}
```

Nếu dùng như deque, có thể đồng bộ object `Deque` bằng lock ngoài hoặc thiết kế abstraction thread-safe riêng. Wrapper không biến element bên trong thành immutable.

### 12.2. Chọn collection concurrent

- queue non-blocking nhiều thread: cân nhắc `ConcurrentLinkedQueue`;
- deque non-blocking: cân nhắc `ConcurrentLinkedDeque`;
- queue có blocking semantics: cân nhắc `LinkedBlockingQueue` hoặc implementation `BlockingDeque`.

Không đổi sang collection concurrent chỉ vì thấy tên `LinkedList`; hãy xác định semantics queue, blocking và backpressure cần thiết.

---

## 13. `LinkedList` so với `ArrayList`

| Nhu cầu | `LinkedList` | `ArrayList` |
|---|---:|---:|
| `get(index)` | `O(n)` | `O(1)` |
| `set(index)` | `O(n)` | `O(1)` |
| add cuối | `O(1)` | Amortized `O(1)` |
| add đầu | `O(1)` | `O(n)` |
| remove đầu | `O(1)` | `O(n)` |
| chèn/xóa khi đã có iterator | Mutation `O(1)` | Thường phải dịch phần tử |
| duyệt | Pointer traversal | Cache-friendly array |
| memory | Cao hơn do node/link | Thấp hơn, có capacity dư |
| random access | Không phù hợp | Phù hợp |

Trong thực tế, `ArrayList` thường là default cho `List`. `LinkedList` chỉ thắng khi workload thật sự tận dụng thao tác đầu/cuối hoặc iterator tại vị trí đã biết.

---

## 14. `LinkedList` so với `ArrayDeque`

| Tiêu chí | `LinkedList` | `ArrayDeque` |
|---|---|---|
| Interface | `List`, `Deque`, `Queue` | `Deque`, `Queue` |
| Random access | Có nhưng `O(n)` | Không có |
| Null element | Cho phép | Không cho phép |
| Add/remove đầu/cuối | `O(1)` | Amortized `O(1)` |
| Memory locality | Thấp hơn | Tốt hơn |
| Node allocation | Có | Không tạo node cho từng phần tử |
| Use case thường | List hai chiều đặc thù | Queue/deque tổng quát |

Nếu chỉ cần queue/deque và có thể loại bỏ `null`, ưu tiên thử `ArrayDeque` trước. Nó thường có locality và overhead memory tốt hơn.

---

## 15. Stream và chuyển đổi

### 15.1. Tạo `LinkedList` rõ ràng

```java
LinkedList<String> result = values.stream()
        .filter(Objects::nonNull)
        .collect(Collectors.toCollection(LinkedList::new));
```

Không dùng `Collectors.toList()` nếu code thực sự yêu cầu implementation là `LinkedList`.

### 15.2. Chuyển thành deque hoặc list khác

```java
Deque<String> deque = new LinkedList<>(values);
List<String> arrayList = new ArrayList<>(values);
```

Chuyển sang `ArrayList` nếu sau đó cần random access nhiều lần; chuyển sang `ArrayDeque` nếu chỉ cần queue/deque và không có null.

---

## 16. Các bẫy thường gặp

- [ ] Nghĩ `LinkedList.get(index)` là `O(1)`.
- [ ] Gọi `get(i)` trong vòng lặp lớn và vô tình tạo workload `O(n^2)`.
- [ ] Nói chèn/xóa giữa luôn `O(1)` mà quên chi phí tìm node.
- [ ] Dùng `LinkedList` làm queue dù `ArrayDeque` phù hợp hơn.
- [ ] Quên `LinkedList` cho phép null nhưng `ArrayDeque` không cho.
- [ ] Nhầm `remove(int)` với `remove(Object)` trong `List<Integer>`.
- [ ] Xóa trực tiếp trong enhanced `for`.
- [ ] Nghĩ fail-fast là thread-safe.
- [ ] Duyệt synchronized wrapper mà không giữ monitor.
- [ ] Dùng `subList` như bản copy độc lập.
- [ ] Nghĩ copy constructor copy sâu object bên trong.
- [ ] Chọn `LinkedList` chỉ vì “xóa node là O(1)” mà chưa có iterator/node.
- [ ] Dùng `peek` với null element rồi không phân biệt được deque rỗng.
- [ ] Phụ thuộc node hoặc sentinel nội bộ của JDK.
- [ ] Bỏ qua chi phí allocation và cache locality khi benchmark.

---

## 17. Bài tập tự kiểm tra

1. Viết queue bằng `LinkedList`, sau đó thay bằng `ArrayDeque` và so sánh API.
2. Minh họa sự khác nhau giữa `remove(1)` và `remove(Integer.valueOf(1))`.
3. Viết code chèn 10.000 phần tử bằng `get(i)` và bằng `ListIterator`, rồi phân tích độ phức tạp.
4. Dùng `subList` để xóa một range và viết lại bằng snapshot độc lập.
5. Tạo workload add/remove đầu-cuối, random access và iteration; benchmark `LinkedList`, `ArrayList`, `ArrayDeque`.
6. Viết test cho `peekFirst()` khi deque rỗng, khi phần tử đầu là `null`, và giải thích sự nhập nhằng.

---

## 18. Tóm tắt cần nhớ

1. `LinkedList` là doubly-linked list và đồng thời là `Deque`/`Queue`.
2. Add/remove đầu/cuối là `O(1)`; `get(index)` thường là `O(n)`.
3. Chèn/xóa qua iterator tại vị trí đã biết có mutation `O(1)`.
4. Chèn/xóa theo index vẫn cần tìm node, nên thường `O(n)`.
5. `LinkedList` tốn memory và allocation hơn `ArrayList`, locality kém hơn.
6. Nếu chỉ cần queue/deque, `ArrayDeque` thường là lựa chọn tốt hơn và không cho null.
7. `subList` là live view; copy constructor là shallow copy độc lập về cấu trúc.
8. `LinkedList` không thread-safe và fail-fast không thay thế đồng bộ hóa.




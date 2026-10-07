# Java `Deque` — Toàn bộ kiến thức cần học

> Checklist này mở rộng phần `Deque` trong [Collections Framework](../01-collections-framework.md).  
> Mục tiêu: hiểu hàng đợi hai đầu, cách mô hình hóa FIFO/LIFO, cơ chế circular array/linked nodes, blocking/concurrent deque và các thuật toán quan trọng.

---

## 1. Mục tiêu đầu ra

Sau khi học xong, cần có khả năng:

- [ ] Giải thích `Deque` khác `Queue`, `List` và class `Stack` thế nào.
- [ ] Dùng đúng toàn bộ API ở đầu (`First`) và cuối (`Last`).
- [ ] Ánh xạ chính xác `Deque` thành FIFO queue hoặc LIFO stack.
- [ ] Phân biệt API ném exception và API trả giá trị đặc biệt.
- [ ] Giải thích cơ chế `ArrayDeque` và `LinkedList`.
- [ ] Biết `ConcurrentLinkedDeque` và `LinkedBlockingDeque` dùng khi nào.
- [ ] Hiểu fail-fast, weakly consistent và descending iterator.
- [ ] Tính được độ phức tạp của các thao tác chính.
- [ ] Dùng deque cho monotonic queue, sliding window và 0–1 BFS.
- [ ] Tránh lỗi do `null`, sai đầu/cuối, concurrency hoặc phụ thuộc implementation detail.

---

## 2. `Deque` là gì?

`Deque` viết tắt của **double-ended queue**, thường đọc là “deck”. Đây là queue cho phép thêm, xóa và xem phần tử ở cả hai đầu.

```text
Iterable<E>
    └── Collection<E>
            └── Queue<E>
                    └── Deque<E>
                            ├── ArrayDeque<E>
                            ├── LinkedList<E>
                            ├── ConcurrentLinkedDeque<E>
                            └── BlockingDeque<E>
                                    └── LinkedBlockingDeque<E>
```

### 2.1. Các tính chất cốt lõi

- Không có random access theo index.
- Có thể thêm/xóa/xem ở cả đầu và cuối.
- Có thể biểu diễn cả FIFO queue lẫn LIFO stack.
- Thường cho phép phần tử trùng lặp.
- Thứ tự encounter được xác định từ first đến last.
- Chính sách `null`, capacity, thread-safety và blocking phụ thuộc implementation.
- Từ Java 21, `Deque` thuộc `SequencedCollection`.

### 2.2. First và Last

```text
First / Head                         Last / Tail
     ↓                                    ↓
   [ A ] <-> [ B ] <-> [ C ] <-> [ D ]
```

Một deque không tự quyết định “đây là stack hay queue”; chính cách gọi API quyết định semantics.

---

## 3. Toàn bộ API hai đầu

| Thao tác | Đầu (`First`) | Cuối (`Last`) |
|---|---|---|
| Thêm, ném exception | `addFirst(e)` | `addLast(e)` |
| Thêm, trả special value | `offerFirst(e)` | `offerLast(e)` |
| Xóa, ném exception | `removeFirst()` | `removeLast()` |
| Xóa, trả special value | `pollFirst()` | `pollLast()` |
| Xem, ném exception | `getFirst()` | `getLast()` |
| Xem, trả special value | `peekFirst()` | `peekLast()` |

### 3.1. Exception API và special-value API

- `addFirst/addLast`: có thể ném `IllegalStateException` nếu deque bị giới hạn và đầy.
- `offerFirst/offerLast`: trả `false` khi không thêm được.
- `removeFirst/removeLast`: ném `NoSuchElementException` khi rỗng.
- `pollFirst/pollLast`: trả `null` khi rỗng.
- `getFirst/getLast`: ném `NoSuchElementException` khi rỗng.
- `peekFirst/peekLast`: trả `null` khi rỗng.

Trong luồng điều khiển bình thường, `offer/poll/peek` thường dễ sử dụng hơn.

### 3.2. API kế thừa từ `Queue`

| `Queue` API | Tương đương trên `Deque` |
|---|---|
| `add(e)` | `addLast(e)` |
| `offer(e)` | `offerLast(e)` |
| `remove()` | `removeFirst()` |
| `poll()` | `pollFirst()` |
| `element()` | `getFirst()` |
| `peek()` | `peekFirst()` |

### 3.3. API stack

| Stack API | Tương đương |
|---|---|
| `push(e)` | `addFirst(e)` |
| `pop()` | `removeFirst()` |
| `peek()` | `peekFirst()` |

### 3.4. Xóa theo lần xuất hiện

- `removeFirstOccurrence(o)`: xóa lần xuất hiện đầu tiên khi duyệt từ first đến last.
- `removeLastOccurrence(o)`: xóa lần xuất hiện cuối cùng trong thứ tự first-to-last; tương đương lần xuất hiện đầu tiên nếu duyệt ngược từ last về first.
- `remove(o)`: tương đương `removeFirstOccurrence(o)`.
- Các method xóa theo occurrence thường phải tìm tuyến tính nên có chi phí `O(n)`.

---

## 4. Dùng `Deque` làm FIFO queue

Quy ước phổ biến:

```java
Deque<String> queue = new ArrayDeque<>();

queue.offerLast("A"); // enqueue
queue.offerLast("B");
queue.peekFirst();     // A
queue.pollFirst();     // A
```

```text
dequeue ← First [ A, B, C ] Last ← enqueue
```

Có thể chọn chiều ngược lại, nhưng không nên trộn hai quy ước trong cùng codebase.

---

## 5. Dùng `Deque` làm LIFO stack

```java
Deque<String> stack = new ArrayDeque<>();

stack.push("A");
stack.push("B");
stack.peek(); // B
stack.pop();  // B
```

Ưu tiên `Deque` hơn class `Stack` vì:

- `Stack` là API legacy kế thừa `Vector`.
- `Deque` diễn tả thao tác stack rõ ràng hơn.
- `ArrayDeque` thường có hiệu năng và memory locality tốt hơn.

Để code dễ đọc, nên dùng nhất quán một vocabulary: hoặc `push/pop/peek`, hoặc `addFirst/removeFirst/peekFirst`.

---

## 6. `ArrayDeque` — resizable circular array

### 6.1. Cơ chế circular array

`ArrayDeque` lưu phần tử trong một mảng và duy trì vị trí logic của hai đầu. Khi đi qua cuối mảng, vị trí quấn về đầu để tận dụng ô trống.

```text
Mảng vật lý: [ D, E, _, _, A, B, C ]
Thứ tự logic: A -> B -> C -> D -> E
```

- Thêm/xóa hai đầu không cần dịch chuyển toàn bộ dữ liệu.
- Khi không còn chỗ, mảng được tăng capacity và dữ liệu được copy/sắp lại.
- Thao tác hai đầu là `O(1)` amortized.
- Duyệt, `contains`, `removeFirstOccurrence`, `removeLastOccurrence` là `O(n)`.

### 6.2. Tính chất

- Không cho phép `null`.
- Không thread-safe.
- Iterator thường fail-fast theo best effort.
- `iterator()` đi từ first đến last.
- `descendingIterator()` đi từ last về first.
- Cache locality tốt và overhead thấp hơn linked nodes.
- Thường là lựa chọn mặc định cho queue/stack/deque đơn luồng.

> Growth factor, capacity, layout mảng và công thức wrap index là implementation detail có thể đổi giữa các JDK. Không phụ thuộc giả định capacity là lũy thừa của 2 hoặc một phép bitmask cụ thể.

### 6.3. Resize và amortized complexity

Một lần resize có thể tốn `O(n)`, nhưng không xảy ra ở mọi lần thêm. Vì vậy chuỗi thao tác thêm được mô tả là amortized `O(1)` cho mỗi thao tác.

Nếu ước lượng trước số phần tử, có thể dùng constructor có initial capacity để giảm số lần tăng mảng; đây vẫn chỉ là tối ưu sau khi hiểu workload.

---

## 7. `LinkedList` làm `Deque`

`LinkedList` dùng doubly linked nodes:

```text
null <- [prev | item | next] <-> [prev | item | next] -> null
```

### Tính chất

- `addFirst/addLast/removeFirst/removeLast`: `O(1)`.
- `contains` và xóa theo occurrence: `O(n)`.
- Cho phép `null`, nhưng nên tránh khi dùng như queue/deque vì `poll/peek` dùng null báo rỗng.
- Mỗi phần tử cần node object và reference `prev/next`.
- Cache locality kém hơn array-backed deque.
- Implements cả `List` và `Deque`, tạo API surface lớn hơn nhu cầu deque thuần túy.
- Không thread-safe; iterator thường fail-fast.

Chỉ chọn `LinkedList` khi thực sự cần đặc điểm của nó; không chọn chỉ vì thao tác hai đầu có Big-O giống `ArrayDeque`.

---

## 8. Bảng so sánh implementation

| Loại | Capacity | Hai đầu | `null` | Thread-safe | Blocking | Cơ chế |
|---|---|---:|---|---|---|---|
| `ArrayDeque` | Tự tăng | `O(1)` amortized | Không | Không | Không | Circular array |
| `LinkedList` | Theo memory | `O(1)` | Có, nhưng tránh | Không | Không | Doubly linked list |
| `ConcurrentLinkedDeque` | Unbounded | Concurrent | Không | Có | Không | Concurrent linked nodes |
| `LinkedBlockingDeque` | Optional bounded | Concurrent | Không | Có | Có | Linked nodes + blocking control |

---

## 9. `ConcurrentLinkedDeque`

- Thread-safe, unbounded, non-blocking deque.
- Hỗ trợ thao tác concurrent ở cả hai đầu.
- Không cho `null`.
- Iterator weakly consistent: không ném `ConcurrentModificationException`, có thể thấy một phần thay đổi đồng thời.
- `size()` không constant time và kết quả có thể không ổn định khi có concurrent update.
- Các thao tác đơn lẻ có thread-safety theo contract; chuỗi nhiều thao tác không tự trở thành một transaction atomic.

Phù hợp khi caller không muốn thread chờ vì deque đầy/rỗng và tự quản lý retry/backoff/drop policy.

---

## 10. `BlockingDeque` và `LinkedBlockingDeque`

`BlockingDeque` mở rộng cả `BlockingQueue` và `Deque`, thêm API chờ ở hai đầu.

### 10.1. Nhóm API blocking

| Thao tác | Đầu | Cuối |
|---|---|---|
| Chờ khi đầy | `putFirst(e)` | `putLast(e)` |
| Chờ có timeout khi đầy | `offerFirst(e,time,unit)` | `offerLast(e,time,unit)` |
| Chờ khi rỗng | `takeFirst()` | `takeLast()` |
| Chờ có timeout khi rỗng | `pollFirst(time,unit)` | `pollLast(time,unit)` |

### 10.2. `LinkedBlockingDeque`

- Có thể bounded; constructor mặc định dùng capacity rất lớn.
- Không cho `null`.
- Thao tác `put/take` có thể block và bị interrupt.
- Tạo backpressure nếu đặt capacity hữu hạn.
- Hỗ trợ producer-consumer ở cả hai đầu.
- Iterator weakly consistent.

Trong production, nên đặt capacity rõ ràng để tránh backlog tăng gần như không giới hạn.

### 10.3. Xử lý interruption

```java
try {
    deque.putLast(task);
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    return;
}
```

Không nuốt `InterruptedException`. Khôi phục interrupt status nếu method hiện tại không xử lý hoàn toàn việc hủy.

### 10.4. Shutdown

`BlockingDeque` không có `close()` chung. Có thể dùng:

- poison pill riêng, không dùng `null`;
- interruption;
- lifecycle của executor;
- trạng thái shutdown có synchronization/visibility đúng.

---

## 11. Iterator và thứ tự duyệt

```java
Iterator<E> forward = deque.iterator();
Iterator<E> backward = deque.descendingIterator();
```

- `iterator`: first → last.
- `descendingIterator`: last → first.
- `ArrayDeque`/`LinkedList`: iterator thường fail-fast, best effort.
- Concurrent/blocking deque: iterator thường weakly consistent.
- Fail-fast không phải thread-safety.
- Weakly consistent không đồng nghĩa snapshot; iterator có thể phản ánh một phần thay đổi concurrent.

### 11.1. Java 21+: `reversed()`

Từ Java 21, `Deque` có `reversed()` do thuộc `SequencedCollection`. Method này trả về view đảo thứ tự encounter:

- first của deque gốc trở thành last của view;
- thao tác trên view phản ánh lên deque gốc;
- chỉ dùng khi project thật sự chạy JDK 21+.

---

## 12. Copy, view và tính bất biến

- JDK không có `Deque.of(...)`.
- `new ArrayDeque<>(source)` tạo shallow copy mutable, độc lập về cấu trúc.
- Copy container không deep-copy các object phần tử.
- `Collections.asLifoQueue(deque)` tạo `Queue` view dùng deque theo LIFO; thay đổi phản ánh hai chiều.
- `Collections.unmodifiableCollection(deque)` làm mất interface `Deque` và chỉ là live view.
- Nếu cần public read-only API, nên trả snapshot dạng `List`/`Collection` hoặc thiết kế interface chỉ cho phép quan sát.

### 12.1. `equals()` và `hashCode()`

`Deque` không có contract so sánh nội dung chặt như `List`. Ví dụ `ArrayDeque` không nên được dùng với kỳ vọng hai deque khác instance nhưng cùng phần tử sẽ luôn `equals()` nhau; `LinkedList` lại chịu ảnh hưởng contract của `List`. Khi cần so sánh theo thứ tự phần tử, chuyển sang `List`/array hoặc tự duyệt và so sánh rõ ràng.

---

## 13. Các pattern và thuật toán quan trọng

### 13.1. Valid Parentheses / expression stack

Dùng `push/pop/peek` để xử lý ngoặc, toán tử và biểu thức.

### 13.2. BFS và level-order traversal

Dùng `offerLast/pollFirst` để bảo toàn FIFO.

### 13.3. DFS iterative

Dùng `push/pop` để mô hình hóa call stack.

### 13.4. Monotonic deque

Deque lưu candidate theo thứ tự đơn điệu:

- Loại index đã ra khỏi cửa sổ ở đầu.
- Loại candidate kém hơn phần tử mới ở cuối.
- Đầu deque luôn là đáp án cho cửa sổ hiện tại.

Mỗi index vào/ra tối đa một lần nên Sliding Window Maximum đạt `O(n)`.

### 13.5. 0–1 BFS

Với edge weight chỉ là `0` hoặc `1`:

- weight `0`: thêm node mới vào đầu bằng `offerFirst`;
- weight `1`: thêm vào cuối bằng `offerLast`.

Kỹ thuật này đạt `O(V + E)` thay vì cần heap như Dijkstra tổng quát.

### 13.6. Palindrome/two-ended processing

So sánh/xử lý `pollFirst` và `pollLast`, nhưng nhớ rằng tạo deque từ dữ liệu vẫn tốn bộ nhớ bổ sung.

### 13.7. Work queue hai đầu

Worker có thể lấy task một đầu và task khẩn cấp được đưa vào đầu còn lại. Với đa luồng, phải dùng concurrent/blocking deque và thiết kế ownership/fairness rõ ràng.

---

## 14. Cách chọn implementation

| Nhu cầu | Lựa chọn thường phù hợp |
|---|---|
| Queue/stack/deque đơn luồng | `ArrayDeque` |
| Cần cả `List` và `Deque` | `LinkedList`, sau khi cân nhắc overhead |
| Concurrent, thao tác trả ngay | `ConcurrentLinkedDeque` |
| Producer-consumer hai đầu, cần block/backpressure | Bounded `LinkedBlockingDeque` |
| Chỉ cần FIFO blocking queue | Cân nhắc `ArrayBlockingQueue`/`LinkedBlockingQueue` thay vì deque |
| Priority ordering | Không dùng deque; dùng `PriorityQueue`/`PriorityBlockingQueue` |

Cần xét capacity, concurrency, backpressure, memory, fairness, latency và shutdown — không chỉ Big-O.

---

## 15. Lỗi và bẫy phải nhận ra

- [ ] Trộn chiều thêm/lấy làm FIFO vô tình thành LIFO.
- [ ] Trộn vocabulary stack và queue khiến code khó đọc.
- [ ] Dùng `remove/get` khi rỗng nhưng không muốn exception.
- [ ] Dùng `null` làm dữ liệu hoặc poison pill.
- [ ] Chọn `LinkedList` mặc định dù `ArrayDeque` phù hợp hơn.
- [ ] Dùng class `Stack` legacy thay cho `Deque`.
- [ ] Nghĩ `contains/removeOccurrence` là `O(1)`.
- [ ] Phụ thuộc capacity, growth factor hoặc bitmask nội bộ của `ArrayDeque`.
- [ ] Sửa deque thường từ nhiều thread.
- [ ] Nghĩ fail-fast đảm bảo thread-safety.
- [ ] Dùng `size()` để ra quyết định atomic trong concurrent code.
- [ ] Dùng `ConcurrentLinkedDeque` rồi chờ backpressure tự xuất hiện.
- [ ] Dùng `LinkedBlockingDeque` mặc định mà không giới hạn capacity.
- [ ] Nuốt `InterruptedException`.
- [ ] Quên chiến lược shutdown cho consumer đang block.
- [ ] Nghĩ unmodifiable view làm phần tử bên trong immutable.

---

## 16. Lộ trình học đề xuất

### Mức 1 — Cơ bản

- [ ] First/last và API hai đầu.
- [ ] Exception API vs special-value API.
- [ ] FIFO aliases và stack aliases.
- [ ] `ArrayDeque` vs `LinkedList`.

### Mức 2 — Cơ chế và thuật toán

- [ ] Circular array, wrap-around, resize, amortized complexity.
- [ ] Forward/descending iterator.
- [ ] BFS, DFS, valid parentheses.
- [ ] Monotonic deque và sliding window.
- [ ] 0–1 BFS.

### Mức 3 — Concurrency

- [ ] `ConcurrentLinkedDeque`.
- [ ] `BlockingDeque`/`LinkedBlockingDeque`.
- [ ] Bounded capacity và backpressure.
- [ ] Interruption, shutdown, weak consistency.

### Mức 4 — Theo phiên bản Java

- [ ] Java 21+: `SequencedCollection` và reversed encounter-order API.
- [ ] Kiểm tra JDK dự án trước khi dùng API mới.

---

## 17. Bài thực hành

1. Cài `MyArrayDeque<E>` bằng circular array, có resize.
2. Viết FIFO queue chỉ dùng API rõ chiều `offerLast/pollFirst`.
3. Viết stack bằng `push/pop/peek` và giải Valid Parentheses.
4. BFS level-order traversal bằng `ArrayDeque`.
5. Giải Sliding Window Maximum bằng monotonic deque `O(n)`.
6. Giải shortest path trên graph có weight 0/1 bằng 0–1 BFS.
7. Minh họa `iterator` và `descendingIterator`.
8. So sánh memory/performance của `ArrayDeque` và `LinkedList` bằng JMH.
9. Viết producer-consumer hai đầu bằng bounded `LinkedBlockingDeque`.
10. Thêm timeout, interruption và shutdown bằng poison pill.

---

## 18. Câu hỏi tự kiểm tra

1. `Deque` mở rộng khả năng của `Queue` như thế nào?
2. Sáu cặp method ở hai đầu khác nhau ra sao?
3. Quy ước nào tạo FIFO và quy ước nào tạo LIFO?
4. Vì sao `ArrayDeque` thao tác hai đầu `O(1)` amortized?
5. Vì sao không nên phụ thuộc capacity/công thức wrap nội bộ?
6. `ArrayDeque` khác `LinkedList` về memory/cache thế nào?
7. Khi nào dùng `removeFirstOccurrence` và `removeLastOccurrence`?
8. `iterator` khác `descendingIterator` thế nào?
9. `ConcurrentLinkedDeque` khác `LinkedBlockingDeque` thế nào?
10. Bounded deque tạo backpressure ra sao?
11. Vì sao không được nuốt `InterruptedException`?
12. Monotonic deque đạt `O(n)` như thế nào?
13. 0–1 BFS dùng hai đầu deque ra sao?
14. Vì sao không dùng deque cho priority ordering?
15. JDK có immutable deque factory không?

---

## 19. Tài liệu liên quan trong repo

- [Tổng quan Collections Framework](../01-collections-framework.md)
- [Bài giải thích chi tiết Queue/Deque](../05-queue-deque-chi-tiet.md)
- [Checklist Queue/Deque](../queue/01-queue-deque-kien-thuc-can-hoc.md)
- [Bài chi tiết về List/LinkedList](../02-list-chi-tiet.md)

## 20. Tài liệu Java chính thức

- [Java SE 21 — `Deque`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Deque.html)
- [Java SE 21 — `ArrayDeque`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/ArrayDeque.html)
- [Java SE 21 — `LinkedList`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/LinkedList.html)
- [Java SE 21 — `BlockingDeque`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/BlockingDeque.html)
- [Java SE 21 — `LinkedBlockingDeque`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/LinkedBlockingDeque.html)
- [Java SE 21 — `ConcurrentLinkedDeque`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ConcurrentLinkedDeque.html)
- [Java SE 21 — `Collections.asLifoQueue`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Collections.html#asLifoQueue(java.util.Deque))

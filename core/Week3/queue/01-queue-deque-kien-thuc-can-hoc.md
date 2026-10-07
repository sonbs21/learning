# Java `Queue` và `Deque` — Toàn bộ kiến thức cần học

> Checklist này mở rộng phần `Queue/Deque` trong [Collections Framework](../01-collections-framework.md).  
> Mục tiêu: hiểu FIFO/LIFO/priority ordering, API hai đầu, heap, circular array, blocking/concurrent queue và cách chọn implementation.

---

## 1. Mục tiêu đầu ra

Sau khi học xong, cần có khả năng:

- [ ] Phân biệt `Queue`, `Deque`, stack và priority queue.
- [ ] Dùng đúng hai nhóm API: ném exception và trả giá trị đặc biệt.
- [ ] Giải thích cơ chế `ArrayDeque`, `LinkedList` và `PriorityQueue`.
- [ ] Hiểu FIFO không phải là quy tắc của mọi implementation `Queue`.
- [ ] Dùng `Deque` đúng chiều khi mô hình hóa queue hoặc stack.
- [ ] Hiểu heap invariant, sift-up và sift-down.
- [ ] Phân biệt queue thường, concurrent queue và blocking queue.
- [ ] Chọn đúng bounded/unbounded queue cho producer-consumer.
- [ ] Xử lý interruption, backpressure và shutdown đúng cách.
- [ ] Nhận ra các bẫy về `null`, comparator, iteration order và capacity.

---

## 2. Nền tảng và hệ phân cấp

```text
Iterable<E>
    └── Collection<E>
            └── Queue<E>
                    ├── PriorityQueue<E>
                    ├── Deque<E>
                    │       ├── ArrayDeque<E>
                    │       ├── LinkedList<E>
                    │       ├── ConcurrentLinkedDeque<E>
                    │       └── BlockingDeque<E>
                    │               └── LinkedBlockingDeque<E>
                    ├── ConcurrentLinkedQueue<E>
                    ├── BlockingQueue<E>
                    │       ├── ArrayBlockingQueue<E>
                    │       ├── LinkedBlockingQueue<E>
                    │       ├── PriorityBlockingQueue<E>
                    │       ├── DelayQueue<E>
                    │       └── SynchronousQueue<E>
                    └── TransferQueue<E>
                            └── LinkedTransferQueue<E>
```

### 2.1. Các khái niệm

- **Queue**: collection được thiết kế để giữ phần tử trước khi xử lý.
- **FIFO**: vào trước ra trước; là quy tắc phổ biến nhưng không bắt buộc cho mọi queue.
- **Priority queue**: phần tử được lấy theo độ ưu tiên, không theo thời gian chèn thuần túy.
- **Deque**: double-ended queue, thêm/xóa/xem ở cả hai đầu.
- **Stack**: LIFO; trong Java hiện đại thường mô hình hóa bằng `Deque`.
- **Blocking queue**: có thao tác chờ khi queue rỗng hoặc đầy.
- Queue nói chung có thể chứa phần tử trùng lặp; ordering quyết định phần tử nào trở thành head.

### 2.2. Head và tail

- Head: phần tử tiếp theo được lấy ra.
- Tail: vị trí logic nơi phần tử mới thường được thêm vào trong FIFO queue.
- Với priority queue, head là phần tử có độ ưu tiên cao nhất theo ordering của queue.

### 2.3. Queue không có equality contract như List

`Queue` không định nghĩa equality dựa trên thứ tự phần tử như `List`. Không giả định hai queue khác instance nhưng cùng nội dung sẽ `equals()` nhau; hành vi phụ thuộc implementation.

---

## 3. API `Queue`: exception và giá trị đặc biệt

| Thao tác | Ném exception khi thất bại | Trả giá trị đặc biệt |
|---|---|---|
| Thêm | `add(e)` | `offer(e)` trả `false` |
| Lấy và xóa head | `remove()` | `poll()` trả `null` |
| Xem head | `element()` | `peek()` trả `null` |

### 3.1. Khi nào dùng nhóm nào?

- Dùng `offer/poll/peek` khi đầy/rỗng là trạng thái bình thường.
- Dùng `add/remove/element` khi thất bại thể hiện lỗi logic và muốn exception.
- Với queue không giới hạn thực tế, `offer` thường thành công nhưng vẫn nên code theo interface contract.

```java
Queue<String> queue = new ArrayDeque<>();
queue.offer("A");
queue.offer("B");

queue.peek(); // A, không xóa
queue.poll(); // A, có xóa
queue.poll(); // B
queue.poll(); // null
```

### 3.2. Vì sao nên tránh `null`?

`poll()` và `peek()` dùng `null` để báo queue rỗng. Dù một số implementation như `LinkedList` có thể chứa `null`, dùng `null` trong queue gây nhập nhằng và bị nhiều queue cấm.

---

## 4. API `Deque`: thao tác ở hai đầu

| Thao tác | Đầu (`First`) | Cuối (`Last`) |
|---|---|---|
| Thêm, exception | `addFirst(e)` | `addLast(e)` |
| Thêm, special value | `offerFirst(e)` | `offerLast(e)` |
| Xóa, exception | `removeFirst()` | `removeLast()` |
| Xóa, special value | `pollFirst()` | `pollLast()` |
| Xem, exception | `getFirst()` | `getLast()` |
| Xem, special value | `peekFirst()` | `peekLast()` |

### 4.1. Dùng `Deque` làm FIFO queue

Chọn một quy ước và giữ nhất quán:

```java
deque.offerLast(item); // enqueue ở cuối
deque.pollFirst();     // dequeue ở đầu
deque.peekFirst();     // xem head
```

### 4.2. Dùng `Deque` làm LIFO stack

```java
stack.push(item); // tương đương addFirst
stack.pop();      // tương đương removeFirst
stack.peek();     // tương đương peekFirst
```

Ưu tiên `Deque`/`ArrayDeque` hơn class `Stack` legacy. Từ Java 21, `Deque` thuộc `SequencedCollection`; kiểm tra JDK dự án trước khi dùng API mới.

---

## 5. `ArrayDeque` — resizable circular array

### 5.1. Cơ chế

`ArrayDeque` lưu phần tử trong mảng và theo dõi vị trí logic ở hai đầu. Khi một đầu đi qua cuối mảng, vị trí được quấn về đầu để tái sử dụng ô trống.

```text
Mảng vật lý: [ D, E, _, _, A, B, C ]
Thứ tự logic: A -> B -> C -> D -> E
```

- Thêm/xóa ở hai đầu không cần dịch chuyển toàn bộ phần tử.
- Khi hết chỗ, cấu trúc tăng capacity và sắp xếp/copy dữ liệu sang mảng mới.
- Các thao tác hai đầu thường `O(1)` amortized.
- Duyệt toàn bộ `O(n)`.
- Không cho phép `null`.
- Không thread-safe; iterator thường fail-fast theo best effort.
- Cache locality và memory overhead thường tốt hơn linked nodes.

> Growth factor, kích thước mảng và công thức wrap index là chi tiết OpenJDK có thể thay đổi. Không viết code phụ thuộc vào giả định “capacity luôn là lũy thừa của 2” hay một phép bitmask cụ thể.

### 5.2. Khi nên dùng

- FIFO queue hoặc LIFO stack trong code đơn luồng.
- BFS/DFS iterative.
- Monotonic deque, sliding window.
- Undo/redo đơn giản.
- Thao tác thường xuyên ở cả hai đầu.

### 5.3. Hạn chế

- Không có random access theo index.
- Không hỗ trợ `null`.
- Không tự thread-safe.
- Resize định kỳ có chi phí `O(n)`, dù chi phí trung bình mỗi lần thêm vẫn amortized `O(1)`.

---

## 6. `LinkedList` làm Queue/Deque

`LinkedList` dùng doubly linked nodes và implements cả `List` lẫn `Deque`.

- Thêm/xóa ở hai đầu `O(1)`.
- Cho phép `null`, nhưng nên tránh khi dùng như queue.
- Tốn object/node overhead và hai reference mỗi node.
- Cache locality kém hơn array-backed deque.
- Không thread-safe.
- Có thêm API `List` không cần thiết nếu bài toán chỉ cần queue/deque.

Trong đa số queue/stack đơn luồng, bắt đầu với `ArrayDeque`; chỉ chọn `LinkedList` khi có lý do cụ thể và đã cân nhắc trade-off.

---

## 7. `PriorityQueue` — binary heap

### 7.1. Heap invariant

`PriorityQueue` thường được cài bằng binary heap trong mảng:

- Mặc định là min-heap theo natural order.
- Có thể truyền comparator để định nghĩa priority khác.
- Head là một phần tử nhỏ nhất theo ordering.
- Nếu nhiều phần tử bằng priority, queue không cam kết thứ tự lấy giữa chúng.
- Heap chỉ bảo đảm quan hệ cha-con, không phải toàn bộ mảng đã sort.

Với index `i`, mô hình heap nhị phân thường suy ra:

- con trái: `2i + 1`;
- con phải: `2i + 2`;
- cha: `(i - 1) / 2`.

### 7.2. `offer`: sift-up

1. Đặt phần tử mới ở cuối heap.
2. So sánh với cha.
3. Đổi chỗ/đẩy lên cho đến khi heap invariant được khôi phục.

Chi phí: `O(log n)`.

### 7.3. `poll`: sift-down

1. Lưu head để trả về.
2. Đưa phần tử cuối lên gốc.
3. Đẩy xuống theo nhánh có priority phù hợp đến khi heap hợp lệ.

Chi phí: `O(log n)`; `peek()` là `O(1)`.

### 7.4. Độ phức tạp

| Thao tác | Độ phức tạp |
|---|---|
| `offer` | `O(log n)` |
| `peek` | `O(1)` |
| `poll` | `O(log n)` |
| `contains`, `remove(Object)` | `O(n)` |
| Duyệt | `O(n)`, nhưng không theo sorted order |

Khởi tạo từ collection có thể heapify hiệu quả hơn việc `offer` từng phần tử; không viết logic phụ thuộc growth policy nội bộ.

### 7.5. Comparator và bẫy overflow

```java
// Tránh: có thể overflow
(a, b) -> a.priority - b.priority

// Nên dùng
Comparator.comparingInt(Task::priority)
```

Comparator phải ổn định, bắc cầu và xử lý tie rõ ràng nếu nghiệp vụ cần deterministic order:

```java
Comparator<Task> order =
    Comparator.comparingInt(Task::priority)
              .thenComparingLong(Task::sequence);
```

### 7.6. Tính chất khác

- Không cho `null`.
- Không thread-safe.
- Iterator không trả phần tử theo priority order.
- Muốn lấy toàn bộ theo thứ tự, copy queue rồi `poll` liên tục hoặc copy sang list và sort.

---

## 8. Bảng so sánh queue đơn luồng

| Loại | Ordering | Thao tác chính | `null` | Cơ chế | Use case |
|---|---|---:|---|---|---|
| `ArrayDeque` | FIFO/LIFO tùy API | Hai đầu `O(1)` amortized | Không | Circular array | Queue/stack/deque mặc định |
| `LinkedList` | FIFO/LIFO tùy API | Hai đầu `O(1)` | Có, nhưng tránh | Doubly linked list | Khi thực sự cần cả List/Deque |
| `PriorityQueue` | Priority order | offer/poll `O(log n)` | Không | Binary heap | Top-K, scheduler, Dijkstra |

---

## 9. `BlockingQueue`: bốn nhóm thao tác

| Hành vi | Thêm | Lấy và xóa | Xem head |
|---|---|---|---|
| Ném exception | `add(e)` | `remove()` | `element()` |
| Special value | `offer(e)` | `poll()` | `peek()` |
| Chờ vô hạn | `put(e)` | `take()` | Không có |
| Chờ có timeout | `offer(e,time,unit)` | `poll(time,unit)` | Không có |

### 9.1. Producer-consumer và backpressure

- `put` chờ khi bounded queue đầy.
- `take` chờ khi queue rỗng.
- Bounded queue tạo backpressure, ngăn producer tăng bộ nhớ không giới hạn.
- Unbounded queue không làm `put` block vì capacity trong điều kiện bình thường, nên producer nhanh hơn consumer có thể gây memory pressure.
- Blocking queue không chấp nhận `null`.

### 9.2. Interruption

`put/take` có thể ném `InterruptedException`. Không được nuốt interruption:

```java
try {
    queue.put(task);
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    return;
}
```

### 9.3. Shutdown

`BlockingQueue` không có thao tác `close()` chung. Các chiến lược thường dùng:

- poison pill/sentinel riêng không phải `null`;
- interruption;
- cờ trạng thái phối hợp đúng visibility;
- lifecycle của `ExecutorService`.

### 9.4. Memory consistency

Hành động trong một thread trước khi đưa object vào blocking queue happens-before hành động sau khi thread khác lấy object đó thành công. Đây là nền tảng truyền dữ liệu an toàn giữa producer và consumer.

---

## 10. Các `BlockingQueue` quan trọng

### 10.1. `ArrayBlockingQueue`

- Bounded, capacity cố định.
- Array-backed FIFO queue.
- Có tùy chọn fairness; fairness giảm starvation nhưng thường giảm throughput.
- Phù hợp khi cần giới hạn tài nguyên rõ ràng.

### 10.2. `LinkedBlockingQueue`

- Có thể bounded; constructor không truyền capacity dùng giới hạn rất lớn.
- Linked nodes, FIFO.
- Cần đặt capacity rõ ràng trong production để tránh backlog tăng gần như không giới hạn.
- Dùng trong producer-consumer và thread pool tùy chính sách tải.

### 10.3. `PriorityBlockingQueue`

- Thread-safe priority queue.
- Logically unbounded; `put` không chờ vì đầy trong điều kiện bình thường.
- `take` chờ khi rỗng.
- Iterator không đảm bảo priority order.
- Phần tử cùng priority không có FIFO guarantee nếu không thêm tie-breaker.

### 10.4. `DelayQueue`

- Unbounded queue chứa phần tử implements `Delayed`.
- Chỉ lấy được head khi delay đã hết.
- Dùng cho expiration, retry scheduling hoặc delayed tasks.
- Ordering dựa trên thời gian delay còn lại.

### 10.5. `SynchronousQueue`

- Không có capacity lưu trữ; mỗi insert phải handoff trực tiếp cho một consumer.
- `put` chờ một `take` tương ứng và ngược lại.
- Có tùy chọn fairness.
- Dùng cho direct handoff, không phải backlog queue.

### 10.6. `LinkedBlockingDeque`

- Blocking deque, có thể bounded.
- Hỗ trợ chờ ở cả hai đầu.
- Hữu ích cho work queue hai đầu hoặc work-stealing design đơn giản, nhưng phải xác định rõ ownership/concurrency policy.

---

## 11. Concurrent non-blocking queue/deque

### 11.1. `ConcurrentLinkedQueue`

- Thread-safe, unbounded, FIFO, non-blocking.
- Dựa trên linked nodes và atomic operations.
- `offer/poll/peek` không chờ producer/consumer khác.
- Iterator weakly consistent.
- `size()` không phải constant time và kết quả có thể không ổn định khi concurrent update.
- Không cho `null`.

### 11.2. `ConcurrentLinkedDeque`

- Thread-safe, unbounded deque, non-blocking.
- Thao tác được cả hai đầu.
- Iterator weakly consistent; `size()` không constant time.
- Không cho `null`.

### 11.3. `LinkedTransferQueue`

- Unbounded `TransferQueue` có thể enqueue thông thường hoặc chờ consumer nhận trực tiếp qua `transfer`.
- `tryTransfer` hỗ trợ kiểm tra/handoff có hoặc không timeout.
- Phù hợp khi producer đôi lúc cần bảo đảm consumer đã nhận phần tử.

### 11.4. Blocking khác non-blocking

- Blocking queue: cung cấp wait/timeout/backpressure API.
- Concurrent non-blocking queue: thao tác trả ngay, caller tự quyết định retry, park, drop hoặc backoff.
- “Non-blocking” không có nghĩa mọi thao tác tổng hợp đều atomic hay không tốn chi phí.

---

## 12. Iterator, bulk operation và tính nhất quán

| Nhóm | Iterator |
|---|---|
| `ArrayDeque`, `LinkedList`, `PriorityQueue` | Thường fail-fast, best effort |
| Concurrent/blocking queues | Thường weakly consistent |

- Weakly consistent iterator có thể thấy một phần thay đổi concurrent và không ném `ConcurrentModificationException`.
- Iterator `PriorityQueue`/`PriorityBlockingQueue` không theo priority order.
- `drainTo(collection)` của `BlockingQueue` chuyển nhiều phần tử hiệu quả hơn `poll` lặp lại trong một số implementation.
- Bulk operations trên concurrent collection không nhất thiết là một snapshot nguyên tử toàn cục.
- Không dựa vào `size()` rồi thực hiện hành động trong concurrent code; trạng thái có thể đổi ngay sau kiểm tra.

---

## 13. Cách chọn implementation

| Nhu cầu | Lựa chọn thường phù hợp |
|---|---|
| FIFO/LIFO/deque đơn luồng | `ArrayDeque` |
| Cần cả API `List` và `Deque` | `LinkedList`, sau khi cân nhắc overhead |
| Lấy phần tử theo priority | `PriorityQueue` |
| Bounded producer-consumer | `ArrayBlockingQueue` hoặc bounded `LinkedBlockingQueue` |
| Concurrent priority | `PriorityBlockingQueue` |
| Delayed item | `DelayQueue` |
| Direct producer-consumer handoff | `SynchronousQueue` |
| Concurrent FIFO, không muốn block | `ConcurrentLinkedQueue` |
| Concurrent double-ended, không block | `ConcurrentLinkedDeque` |
| Handoff tùy chọn | `LinkedTransferQueue` |

Cần xét ordering, capacity, backpressure, fairness, read/write ratio, memory, latency, shutdown và thread-pool policy — không chỉ Big-O.

---

## 14. Copy, read-only view và adapter

- JDK không có factory `Queue.of(...)` tương đương `List.of(...)`.
- `new ArrayDeque<>(source)` tạo shallow copy mutable, độc lập về cấu trúc.
- `Collections.unmodifiableCollection(queue)` chỉ trả `Collection`, không bảo toàn interface `Queue` và vẫn là live view.
- Nếu API cần expose queue chỉ đọc, thường nên trả snapshot dưới dạng `List`/`Collection` hoặc thiết kế một abstraction chỉ có thao tác quan sát.
- `Collections.asLifoQueue(deque)` tạo một `Queue` view sử dụng `Deque` theo thứ tự LIFO; thay đổi qua một phía phản ánh sang phía kia.
- Copy container không deep-copy phần tử; object mutable bên trong vẫn được dùng chung.

---

## 15. Pattern và thuật toán thường gặp

- BFS bằng FIFO `ArrayDeque`.
- DFS iterative/valid parentheses bằng stack `ArrayDeque`.
- Monotonic deque cho Sliding Window Maximum.
- Top-K bằng min-heap kích thước `k`.
- Merge K Sorted Lists bằng heap.
- Dijkstra/Prim/A* bằng priority queue và chiến lược xử lý stale entry.
- Median stream bằng hai heap.
- Producer-consumer bằng bounded blocking queue.
- Task scheduling bằng delay/priority queue.
- Thread pool work queue với capacity/rejection policy rõ ràng.

---

## 16. Lỗi và bẫy phải nhận ra

- [ ] Cho rằng mọi `Queue` đều FIFO.
- [ ] Dùng sai cặp đầu/cuối của `Deque`, làm đảo FIFO thành LIFO.
- [ ] Trộn `push/pop` với `offerLast/pollFirst` mà không có quy ước.
- [ ] Cho `null` vào queue hoặc dùng `null` làm poison pill.
- [ ] Gọi `remove/element` khi queue rỗng nhưng không muốn exception.
- [ ] Nghĩ iterator của `PriorityQueue` đã sorted.
- [ ] Comparator dùng phép trừ gây integer overflow.
- [ ] Comparator không có tie-breaker khi nghiệp vụ cần ổn định.
- [ ] Gọi `remove(Object)` trên heap và nghĩ là `O(log n)`.
- [ ] Mutate field priority khi object đang nằm trong priority queue.
- [ ] Dùng `LinkedList` mặc định dù chỉ cần queue/stack.
- [ ] Phụ thuộc growth factor, capacity hoặc công thức wrap nội bộ của `ArrayDeque`.
- [ ] Dùng queue không thread-safe cho nhiều producer/consumer.
- [ ] Dùng unbounded queue mà không kiểm soát tốc độ producer.
- [ ] Nghĩ `PriorityBlockingQueue.put()` tạo backpressure khi queue đầy.
- [ ] Nuốt `InterruptedException` mà không restore interrupt status.
- [ ] Dùng `size()` để ra quyết định atomic trong concurrent code.
- [ ] Quên thiết kế shutdown cho consumer đang block.
- [ ] Benchmark bằng một lần `System.nanoTime()` thay vì JMH.

---

## 17. Lộ trình học đề xuất

### Mức 1 — Cơ bản

- [ ] FIFO, LIFO, priority ordering.
- [ ] `offer/poll/peek` vs `add/remove/element`.
- [ ] API hai đầu của `Deque`.
- [ ] `ArrayDeque` vs `LinkedList` vs `PriorityQueue`.

### Mức 2 — Làm việc thực tế

- [ ] Circular array và amortized complexity.
- [ ] Binary heap, sift-up, sift-down.
- [ ] Comparator và tie-breaker.
- [ ] BFS, stack, top-K, monotonic deque.
- [ ] Fail-fast và iteration order.

### Mức 3 — Concurrency

- [ ] `BlockingQueue` bốn nhóm thao tác.
- [ ] Bounded queue, backpressure và fairness.
- [ ] Interruption, poison pill và shutdown.
- [ ] Các blocking queue chuyên dụng.
- [ ] Concurrent non-blocking queue/deque.
- [ ] Weakly consistent iterator và memory consistency.

### Mức 4 — Chuyên sâu

- [ ] ThreadPoolExecutor work-queue trade-off.
- [ ] Priority mutation/stale-entry pattern.
- [ ] Latency, throughput và memory benchmarking bằng JMH.
- [ ] Java 21 `SequencedCollection`/`Deque` APIs.

---

## 18. Bài thực hành

1. Cài `MyArrayDeque<E>` bằng circular array và tự resize.
2. Cài `MyPriorityQueue<E>` bằng binary heap.
3. Dùng `ArrayDeque` giải Valid Parentheses và BFS shortest path.
4. Dùng monotonic deque giải Sliding Window Maximum `O(n)`.
5. Dùng heap giải Top K Frequent Elements và Merge K Sorted Lists.
6. Viết comparator có tie-breaker cho task scheduler.
7. Minh họa iterator của `PriorityQueue` không sorted.
8. Viết producer-consumer bằng bounded `ArrayBlockingQueue`.
9. Thêm shutdown bằng poison pill và xử lý interruption đúng.
10. So sánh bounded queue với unbounded queue khi producer nhanh hơn consumer.
11. Dùng `DelayQueue` xây retry scheduler đơn giản.
12. So sánh `ConcurrentLinkedQueue` và `BlockingQueue` cho hai workload khác nhau.

---

## 19. Câu hỏi tự kiểm tra

1. Vì sao không phải mọi queue đều FIFO?
2. `offer/poll/peek` khác `add/remove/element` thế nào?
3. Quy ước nào biến `Deque` thành FIFO hoặc LIFO?
4. Circular array giúp thao tác hai đầu `O(1)` amortized ra sao?
5. Vì sao không nên phụ thuộc capacity/growth policy của `ArrayDeque`?
6. Heap invariant là gì? Sift-up và sift-down hoạt động thế nào?
7. Vì sao iterator của `PriorityQueue` không sorted?
8. Vì sao `contains/remove(Object)` của heap là `O(n)`?
9. Bounded queue tạo backpressure như thế nào?
10. `ArrayBlockingQueue`, `LinkedBlockingQueue`, `PriorityBlockingQueue` khác nhau ra sao?
11. `SynchronousQueue` có capacity bằng 0 nghĩa là gì?
12. Blocking queue khác concurrent non-blocking queue thế nào?
13. Vì sao phải restore interrupt status?
14. Weakly consistent iterator có ý nghĩa gì?
15. Vì sao concurrent code không nên dựa vào `size()` rồi hành động?

---

## 20. Tài liệu liên quan trong repo

- [Tổng quan Collections Framework](../01-collections-framework.md)
- [Bài giải thích chi tiết Queue/Deque](../05-queue-deque-chi-tiet.md)
- [Bài chi tiết về List/LinkedList](../02-list-chi-tiet.md)

## 21. Tài liệu Java chính thức

- [Java SE 21 — `Queue`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Queue.html)
- [Java SE 21 — `Deque`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Deque.html)
- [Java SE 21 — `ArrayDeque`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/ArrayDeque.html)
- [Java SE 21 — `PriorityQueue`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/PriorityQueue.html)
- [Java SE 21 — `BlockingQueue`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/BlockingQueue.html)
- [Java SE 21 — `ArrayBlockingQueue`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ArrayBlockingQueue.html)
- [Java SE 21 — `LinkedBlockingQueue`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/LinkedBlockingQueue.html)
- [Java SE 21 — `PriorityBlockingQueue`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/PriorityBlockingQueue.html)
- [Java SE 21 — `DelayQueue`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/DelayQueue.html)
- [Java SE 21 — `SynchronousQueue`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/SynchronousQueue.html)
- [Java SE 21 — `ConcurrentLinkedQueue`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ConcurrentLinkedQueue.html)
- [Java SE 21 — `LinkedTransferQueue`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/LinkedTransferQueue.html)

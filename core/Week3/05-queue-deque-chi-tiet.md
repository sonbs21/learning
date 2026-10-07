# Queue / Deque trong Java — Đào sâu chi tiết

> Đào sâu phần "Queue/Deque: PriorityQueue, ArrayDeque, LinkedList làm Queue" trong [01-collections-framework.md](01-collections-framework.md).

---

## 1. Queue/Deque là gì? Vị trí trong kiến trúc

```
Collection
    │
   Queue  (FIFO - First In First Out, có thể có ưu tiên)
    │
    ├── LinkedList (implements cả List lẫn Deque)
    ├── PriorityQueue
    ├── Deque (interface con của Queue - hỗ trợ cả 2 đầu)
    │      ├── ArrayDeque
    │      └── LinkedList (cũng implements Deque)
    └── BlockingQueue (interface, java.util.concurrent — cho đa luồng)
           ├── ArrayBlockingQueue
           ├── LinkedBlockingQueue
           └── PriorityBlockingQueue
```

- **Queue**: hàng đợi, nguyên tắc **FIFO** (vào trước ra trước) — trừ `PriorityQueue` (ra theo độ ưu tiên).
- **Deque** (Double-Ended Queue, đọc là "deck"): hàng đợi 2 đầu, thao tác được cả đầu lẫn cuối → dùng làm được cả **Queue** lẫn **Stack**.

### API cặp đôi đáng chú ý: có ném exception vs trả null/false

| Thao tác | Ném Exception khi thất bại | Trả giá trị đặc biệt khi thất bại |
|---|---|---|
| Thêm | `add(e)` | `offer(e)` → `false` |
| Lấy & xóa | `remove()` | `poll()` → `null` |
| Xem (không xóa) | `element()` | `peek()` → `null` |

> **Khuyến nghị**: dùng `offer()`/`poll()`/`peek()` trong đa số trường hợp — an toàn hơn, tránh phải bọc try-catch cho tình huống hàng đợi rỗng/đầy (vốn là chuyện bình thường, không phải lỗi).

---

## 2. Các loại Queue/Deque và kiến trúc bên trong

### 2.1. LinkedList làm Queue/Deque

Đã phân tích kiến trúc chi tiết ở [02-list-chi-tiet.md](02-list-chi-tiet.md#22-linkedlist--danh-sách-liên-kết-đôi-doubly-linked-list) — `LinkedList` dùng **doubly linked list**, mỗi node có `prev`/`next`.

- `addFirst()`/`addLast()`/`removeFirst()`/`removeLast()` đều là **O(1) thực sự** (chỉ đổi vài con trỏ).
- Implements cả `List`, `Queue`, và `Deque` → linh hoạt nhưng "ôm đồm" nhiều trách nhiệm, tốn bộ nhớ (mỗi phần tử là 1 Node object riêng, có overhead con trỏ + object header).

```java
Queue<Integer> queue = new LinkedList<>();
queue.offer(1); queue.offer(2); // enqueue
queue.poll();                    // dequeue -> 1 (FIFO)

Deque<Integer> deque = new LinkedList<>();
deque.addFirst(1); deque.addLast(2);
```

### 2.2. ArrayDeque — Mảng vòng (Circular Array / Ring Buffer)

**Kiến trúc bên trong — đây là điểm quan trọng nhất cần hiểu:**
```java
public class ArrayDeque<E> {
    transient Object[] elements; // mảng có kích thước LUÔN là lũy thừa của 2
    transient int head;           // chỉ số phần tử đầu
    transient int tail;           // chỉ số vị trí TIẾP THEO sẽ chèn ở cuối
}
```

- Dữ liệu lưu trong **1 mảng duy nhất**, nhưng được coi là **"mảng vòng" (circular buffer)**: khi `tail` chạm cuối mảng, nó "quấn vòng" về đầu mảng (`0`) thay vì cần dịch chuyển dữ liệu.
- `addFirst(e)`: giảm `head` đi 1 (vòng về cuối mảng nếu `head == 0`), đặt `e` vào đó.
- `addLast(e)`: đặt `e` vào vị trí `tail`, tăng `tail` lên 1 (vòng về đầu nếu chạm cuối mảng).
- Công thức "vòng" thực hiện bằng phép AND (vì capacity là lũy thừa của 2): `index = (index + 1) & (elements.length - 1)`.
- Khi mảng đầy (`head == tail` sau khi thêm) → **resize** (nhân đôi capacity), copy lại toàn bộ phần tử theo đúng thứ tự logic vào mảng mới lớn hơn.

```
Ví dụ trực quan (capacity = 8):
Ban đầu:      [_, _, _, A, B, C, _, _]   head=3, tail=6
addFirst(Z):  [_, _, Z, A, B, C, _, _]   head=2, tail=6
addLast(D):   [_, _, Z, A, B, C, D, _]   head=2, tail=7
addLast(E) khi mảng gần đầy và tail vòng về đầu:
              [E, _, Z, A, B, C, D, _]   head=2, tail=1 (đã "quấn vòng")
```

- **Mọi thao tác ở cả 2 đầu đều O(1) amortized** (giống ArrayList về bản chất grow, nhưng không cần dịch chuyển phần tử giữa mảng như ArrayList vì dùng 2 con trỏ head/tail thay vì luôn bắt đầu từ index 0).
- **Không cho phép `null`** (khác `LinkedList`) — vì `ArrayDeque` dùng giá trị `null` nội bộ để đánh dấu "vị trí trống", nếu cho phép `null` làm phần tử thật sẽ gây nhập nhằng khi kiểm tra `poll()` trả về `null` là "rỗng" hay "phần tử thực sự là null".
- **Cache-friendly**: dữ liệu liền kề trong bộ nhớ (giống mảng), duyệt nhanh hơn `LinkedList` nhờ tận dụng CPU cache tốt hơn.

```java
Deque<Integer> deque = new ArrayDeque<>();
deque.addFirst(1); deque.addLast(2);   // O(1) cả 2 đầu
deque.offerFirst(0); deque.offerLast(3);

// Dùng làm Stack (thay thế cho class Stack cũ - khuyến nghị chính thức của JDK)
Deque<Integer> stack = new ArrayDeque<>();
stack.push(1); stack.push(2); // push = addFirst
stack.pop();                    // pop = removeFirst -> 2 (LIFO)

// Dùng làm Queue
Deque<Integer> asQueue = new ArrayDeque<>();
asQueue.offer(1); asQueue.offer(2); // offer = addLast
asQueue.poll();                      // poll = removeFirst -> 1 (FIFO)
```

### 2.3. PriorityQueue — Binary Heap

**Kiến trúc bên trong:**
```java
public class PriorityQueue<E> {
    transient Object[] queue; // mảng biểu diễn CÂY NHỊ PHÂN dạng heap (không phải node/con trỏ)
    private final Comparator<? super E> comparator; // null = dùng thứ tự tự nhiên (Comparable)
}
```

- Cài đặt bằng **binary heap** (mặc định là **min-heap**: phần tử nhỏ nhất luôn ở gốc/đầu), biểu diễn bằng **1 mảng** theo quy tắc:
  - Phần tử tại index `i` có con trái ở `2i + 1`, con phải ở `2i + 2`, cha ở `(i - 1) / 2`.
  - Không cần con trỏ tường minh — quan hệ cha/con suy ra hoàn toàn từ chỉ số mảng (rất tiết kiệm bộ nhớ và cache-friendly).

**Cơ chế `offer(e)` (thêm phần tử) — "sift up" / "bubble up":**
```
1. Đặt phần tử mới vào cuối mảng (vị trí cuối cùng của heap)
2. So sánh với phần tử cha:
   - Nếu phần tử mới nhỏ hơn cha (vi phạm tính chất min-heap) -> hoán đổi với cha
   - Lặp lại bước này (đi lên dần) cho đến khi đúng vị trí hoặc chạm gốc
=> Độ phức tạp: O(log n) vì chiều cao cây heap là log n
```

**Cơ chế `poll()` (lấy phần tử nhỏ nhất) — "sift down" / "bubble down":**
```
1. Lưu lại phần tử gốc (nhỏ nhất) để trả về
2. Đưa phần tử CUỐI CÙNG của mảng lên vị trí gốc
3. So sánh phần tử này với 2 con:
   - Nếu lớn hơn con nhỏ nhất -> hoán đổi với con đó
   - Lặp lại (đi xuống dần) cho đến khi đúng vị trí hoặc thành lá
=> Độ phức tạp: O(log n)
```

- `peek()` (xem phần tử nhỏ nhất mà không xóa) là **O(1)** — vì luôn nằm ở gốc (index 0).
- **Không đảm bảo thứ tự khi duyệt bằng iterator thông thường** — chỉ đảm bảo `poll()` luôn trả về phần tử nhỏ nhất hiện có, còn cấu trúc mảng bên trong không phải là mảng đã sắp xếp hoàn toàn.

```java
PriorityQueue<Integer> minHeap = new PriorityQueue<>(); // mặc định min-heap
minHeap.offer(5); minHeap.offer(1); minHeap.offer(3);
minHeap.poll(); // -> 1 (nhỏ nhất)

PriorityQueue<Integer> maxHeap = new PriorityQueue<>(Comparator.reverseOrder());

// Custom object với Comparator
PriorityQueue<int[]> pq = new PriorityQueue<>((a, b) -> a[0] - b[0]); // sắp theo phần tử đầu của mảng
```

### 2.4. BlockingQueue — Queue cho đa luồng (Producer-Consumer)

| | Cấu trúc | Đặc điểm |
|---|---|---|
| **ArrayBlockingQueue** | Mảng có kích thước cố định (bounded) | `put()` chặn (block) thread khi hàng đầy, `take()` chặn khi hàng rỗng — dùng 1 lock + 2 Condition (notFull/notEmpty) |
| **LinkedBlockingQueue** | Linked list, có thể bounded hoặc unbounded | Dùng 2 lock riêng biệt cho đầu/cuối (putLock/takeLock) → throughput cao hơn ArrayBlockingQueue khi nhiều thread đọc/ghi đồng thời |
| **PriorityBlockingQueue** | Binary heap (không giới hạn kích thước) | Giống PriorityQueue nhưng thread-safe, `take()` chặn khi rỗng |

```java
BlockingQueue<Integer> queue = new ArrayBlockingQueue<>(10);

// Producer thread
queue.put(1); // tự động block nếu hàng đầy, chờ đến khi có chỗ trống

// Consumer thread
int value = queue.take(); // tự động block nếu hàng rỗng, chờ đến khi có dữ liệu
```
> `BlockingQueue` là nền tảng cài đặt mô hình **Producer-Consumer** kinh điển, cũng chính là cấu trúc lõi bên trong `ThreadPoolExecutor` (hàng đợi task chờ xử lý) — xem thêm ở Tuần 5 - Multithreading khi học tới `java.util.concurrent`.

---

## 3. Bảng so sánh tổng hợp

| Tiêu chí | LinkedList | ArrayDeque | PriorityQueue | ArrayBlockingQueue |
|---|---|---|---|---|
| Cấu trúc bên trong | Doubly linked list | Mảng vòng (circular array) | Binary heap (mảng) | Mảng vòng + lock |
| `addFirst`/`addLast` | O(1) | O(1) amortized | Không hỗ trợ (không phải Deque) | Không hỗ trợ |
| `poll()`/`peek()` (phần tử ưu tiên) | Không áp dụng | Không áp dụng | O(log n) / O(1) | Không áp dụng |
| Thứ tự | FIFO (hoặc LIFO nếu dùng như stack) | FIFO/LIFO tùy cách gọi | Theo độ ưu tiên (Comparable/Comparator) | FIFO |
| Cho phép `null` | Có | **Không** | **Không** | Không |
| Thread-safe | Không | Không | Không | Có (blocking khi đầy/rỗng) |
| Cache locality | Kém (node rải rác) | Tốt (mảng liền kề) | Tốt (mảng liền kề) | Tốt |
| Bộ nhớ overhead | Cao (2 con trỏ/node) | Thấp | Thấp | Thấp |

---

## 4. Ưu / Nhược điểm

### LinkedList (làm Queue/Deque)
- **Ưu điểm**: O(1) thực sự ở cả 2 đầu, không cần resize, cho phép `null`.
- **Nhược điểm**: tốn bộ nhớ hơn (mỗi phần tử là 1 object Node riêng biệt với 2 con trỏ), cache locality kém → trong benchmark thực tế thường **chậm hơn ArrayDeque**.

### ArrayDeque
- **Ưu điểm**: nhanh hơn `LinkedList` trong hầu hết trường hợp thực tế (cache-friendly), tiết kiệm bộ nhớ hơn, được JDK khuyến nghị chính thức thay thế cả `Stack` (class cũ) và `LinkedList` khi dùng làm Queue/Stack thuần túy.
- **Nhược điểm**: không cho phép `null`, kích thước phải resize (dù amortized O(1), vẫn có chi phí định kỳ), không implements `List` (không có `get(index)`).

### PriorityQueue
- **Ưu điểm**: luôn lấy ra được phần tử ưu tiên cao nhất/thấp nhất nhanh (O(log n) thêm/xóa, O(1) xem), tiết kiệm bộ nhớ (biểu diễn bằng mảng, không cần con trỏ).
- **Nhược điểm**: không đảm bảo thứ tự toàn phần khi duyệt (chỉ đảm bảo gốc là nhỏ nhất/lớn nhất), không cho `null`, không thread-safe.

### BlockingQueue (các loại)
- **Ưu điểm**: giải quyết bài toán đồng bộ hóa giữa nhiều thread producer/consumer một cách an toàn, có sẵn cơ chế block tự động (không cần tự viết `wait()`/`notify()`).
- **Nhược điểm**: có overhead do lock, `ArrayBlockingQueue` cần xác định trước capacity (bounded), `LinkedBlockingQueue` unbounded có thể gây tràn bộ nhớ nếu producer nhanh hơn consumer quá nhiều.

---

## 5. Khi nào dùng loại nào? (Use case)

| Tình huống | Nên dùng |
|---|---|
| Cần cấu trúc Stack (LIFO) hoặc Queue (FIFO) thuần túy, đơn luồng | **ArrayDeque** (khuyến nghị mặc định, JDK doc ghi rõ nên ưu tiên hơn `Stack`/`LinkedList`) |
| Cần lấy ra phần tử có độ ưu tiên cao nhất/thấp nhất liên tục (top-K, Dijkstra, Huffman coding, task scheduling theo priority) | **PriorityQueue** |
| Cần Queue/Deque cho phép phần tử `null` | `LinkedList` (nhưng cân nhắc: có thực sự cần null hay chỉ là thiết kế chưa tốt?) |
| Bài toán BFS trên đồ thị/cây (cần FIFO đơn giản) | **ArrayDeque** (nhanh hơn `LinkedList`) |
| Bài toán Undo/Redo, kiểm tra dấu ngoặc hợp lệ, đánh giá biểu thức hậu tố (cần Stack) | **ArrayDeque** dùng như Stack (`push`/`pop`) |
| Mô hình Producer-Consumer đa luồng, hàng đợi task cố định kích thước | **ArrayBlockingQueue** |
| Mô hình Producer-Consumer, throughput cao, không cần giới hạn cứng kích thước | **LinkedBlockingQueue** |
| Hàng đợi task đa luồng cần xử lý theo độ ưu tiên | **PriorityBlockingQueue** |
| Sliding window tối ưu (Maximum in sliding window) — cần Deque lưu index | **ArrayDeque** (Monotonic Deque) |

---

## 6. Các bài toán / pattern thường gặp theo từng loại

### 6.1. ArrayDeque làm Stack — Kiểm tra ngoặc hợp lệ (Valid Parentheses)
```java
boolean isValid(String s) {
    Deque<Character> stack = new ArrayDeque<>();
    Map<Character, Character> pairs = Map.of(')', '(', ']', '[', '}', '{');
    for (char c : s.toCharArray()) {
        if (pairs.containsValue(c)) {
            stack.push(c);
        } else if (pairs.containsKey(c)) {
            if (stack.isEmpty() || stack.pop() != pairs.get(c)) return false;
        }
    }
    return stack.isEmpty();
}
```

### 6.2. ArrayDeque làm Queue — BFS trên đồ thị/ma trận
```java
int bfsShortestPath(int[][] grid, int[] start, int[] end) {
    Deque<int[]> queue = new ArrayDeque<>();
    Set<String> visited = new HashSet<>();
    queue.offer(start);
    visited.add(start[0] + "," + start[1]);
    int steps = 0;

    while (!queue.isEmpty()) {
        int size = queue.size();
        for (int i = 0; i < size; i++) {
            int[] cur = queue.poll();
            if (cur[0] == end[0] && cur[1] == end[1]) return steps;
            for (int[] dir : new int[][]{{0,1},{0,-1},{1,0},{-1,0}}) {
                int nx = cur[0] + dir[0], ny = cur[1] + dir[1];
                String key = nx + "," + ny;
                if (isValid(grid, nx, ny) && visited.add(key)) {
                    queue.offer(new int[]{nx, ny});
                }
            }
        }
        steps++;
    }
    return -1;
}
```

### 6.3. PriorityQueue — Top K Frequent Elements
```java
List<Integer> topKFrequent(int[] nums, int k) {
    Map<Integer, Integer> freq = new HashMap<>();
    for (int n : nums) freq.merge(n, 1, Integer::sum);

    // Min-heap kích thước k: giữ lại k phần tử có tần suất cao nhất
    PriorityQueue<Map.Entry<Integer, Integer>> pq =
        new PriorityQueue<>(Comparator.comparingInt(Map.Entry::getValue));

    for (Map.Entry<Integer, Integer> entry : freq.entrySet()) {
        pq.offer(entry);
        if (pq.size() > k) pq.poll(); // loại bỏ phần tử có tần suất thấp nhất
    }
    return pq.stream().map(Map.Entry::getKey).collect(Collectors.toList());
}
```

### 6.4. PriorityQueue — Merge K Sorted Lists (gộp k danh sách đã sắp xếp)
```java
List<Integer> mergeKSortedLists(List<List<Integer>> lists) {
    // PriorityQueue lưu [giá trị, index danh sách, index phần tử trong danh sách đó]
    PriorityQueue<int[]> pq = new PriorityQueue<>(Comparator.comparingInt(a -> a[0]));
    for (int i = 0; i < lists.size(); i++) {
        if (!lists.get(i).isEmpty()) pq.offer(new int[]{lists.get(i).get(0), i, 0});
    }

    List<Integer> result = new ArrayList<>();
    while (!pq.isEmpty()) {
        int[] cur = pq.poll();
        result.add(cur[0]);
        int listIdx = cur[1], elemIdx = cur[2] + 1;
        if (elemIdx < lists.get(listIdx).size()) {
            pq.offer(new int[]{lists.get(listIdx).get(elemIdx), listIdx, elemIdx});
        }
    }
    return result;
}
```

### 6.5. Monotonic Deque — Sliding Window Maximum (bài toán kinh điển, độ khó cao)
```java
// Tìm giá trị lớn nhất trong mỗi cửa sổ trượt kích thước k, độ phức tạp O(n) nhờ Deque đơn điệu
int[] maxSlidingWindow(int[] nums, int k) {
    Deque<Integer> deque = new ArrayDeque<>(); // lưu INDEX, giá trị tại các index giảm dần
    int[] result = new int[nums.length - k + 1];

    for (int i = 0; i < nums.length; i++) {
        // Loại bỏ các index đã ra khỏi cửa sổ
        if (!deque.isEmpty() && deque.peekFirst() <= i - k) deque.pollFirst();
        // Loại bỏ các phần tử nhỏ hơn phần tử hiện tại (chúng không bao giờ là max nữa)
        while (!deque.isEmpty() && nums[deque.peekLast()] < nums[i]) deque.pollLast();
        deque.offerLast(i);
        if (i >= k - 1) result[i - k + 1] = nums[deque.peekFirst()]; // đầu deque luôn là max hiện tại
    }
    return result;
}
```

### 6.6. BlockingQueue — Mô hình Producer-Consumer
```java
BlockingQueue<Integer> queue = new ArrayBlockingQueue<>(5);

Runnable producer = () -> {
    for (int i = 0; i < 20; i++) {
        try {
            queue.put(i); // tự block nếu hàng đầy, không cần tự viết wait/notify
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
};

Runnable consumer = () -> {
    while (true) {
        try {
            int value = queue.take(); // tự block nếu hàng rỗng
            System.out.println("Consumed: " + value);
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
    }
};
```

---

## Câu hỏi tự kiểm tra
1. Giải thích cơ chế "mảng vòng" (circular array) của `ArrayDeque` — vì sao thao tác ở cả 2 đầu đều O(1) mà không cần dịch chuyển phần tử như `ArrayList`?
2. Vì sao `ArrayDeque` không cho phép phần tử `null`?
3. `PriorityQueue` biểu diễn bằng cấu trúc dữ liệu gì bên trong? Giải thích cơ chế "sift up" khi `offer()` và "sift down" khi `poll()`.
4. Vì sao `peek()` của `PriorityQueue` là O(1) nhưng `poll()` lại là O(log n)?
5. Vì sao JDK khuyến nghị dùng `ArrayDeque` thay vì `LinkedList` hoặc class `Stack` cũ khi cần cấu trúc Stack/Queue thuần túy?
6. `ArrayBlockingQueue` và `LinkedBlockingQueue` khác nhau ở điểm nào về cơ chế lock? Vì sao `LinkedBlockingQueue` có throughput cao hơn khi nhiều thread đọc/ghi đồng thời?
7. Giải thích ý tưởng "Monotonic Deque" trong bài toán Sliding Window Maximum — vì sao có thể loại bỏ các phần tử nhỏ hơn phần tử mới khỏi cuối deque mà không làm mất đáp án đúng?

## Bài tập luyện tập
- Cài đặt lại 1 phiên bản `MyArrayDeque<T>` đơn giản (mảng vòng tự resize) với `addFirst`, `addLast`, `pollFirst`, `pollLast`.
- Cài đặt lại 1 phiên bản `MyPriorityQueue<T>` đơn giản (binary heap bằng mảng) với `offer`, `poll`, `peek`.
- Giải bài toán "Valid Parentheses" (6.1) và "BFS shortest path" (6.2) bằng `ArrayDeque`.
- Giải bài toán "Top K Frequent Elements" (6.3) và "Merge K Sorted Lists" (6.4) bằng `PriorityQueue`.
- Giải bài toán "Sliding Window Maximum" (6.5) bằng Monotonic Deque — đây là bài toán rất hay gặp trong phỏng vấn.
- Viết chương trình Producer-Consumer hoàn chỉnh với `ArrayBlockingQueue`, có 2 producer và 3 consumer chạy song song.

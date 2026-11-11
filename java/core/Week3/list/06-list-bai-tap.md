# Bài tập luyện tập: List trong Java

> Bộ bài tập bám sát nội dung [02-list-chi-tiet.md](02-list-chi-tiet.md). Chia làm 3 mức độ: Cơ bản → Trung bình → Nâng cao. Mỗi bài đều ghi rõ mục tiêu, yêu cầu, gợi ý, và tiêu chí hoàn thành để bạn tự chấm.

---

## Mức 1 — Cơ bản (Củng cố kiến thức nền)

### Bài 1: Chứng minh cơ chế grow() của ArrayList
**Mục tiêu**: hiểu công thức resize `x1.5` của `ArrayList`.

**Yêu cầu**:
1. Dùng Reflection lấy field `elementData` (private) của 1 `ArrayList<Integer>` để in ra **capacity thực tế** (không phải `size()`).
2. Thêm phần tử từng cái một, in ra capacity sau mỗi lần `add()` để quan sát khi nào nó nhảy từ 10 → 15 → 22 → ...
3. Tự tính bằng tay capacity sau 5 lần grow liên tiếp bắt đầu từ 10, so sánh với kết quả in ra.

**Gợi ý**: dùng `Field field = ArrayList.class.getDeclaredField("elementData"); field.setAccessible(true);` (xem lại Reflection ở [Week12/01-kien-thuc-bo-tro.md](../Week12/01-kien-thuc-bo-tro.md)).

**Hoàn thành khi**: in ra được đúng dãy capacity và giải thích bằng lời tại sao capacity KHÔNG tăng đúng 1.5 lần tuyệt đối ở vài bước đầu (gợi ý: làm tròn số nguyên).

---

### Bài 2: So sánh Vector và Collections.synchronizedList()
**Mục tiêu**: hiểu vì sao `Vector` bị coi là lỗi thời.

**Yêu cầu**:
1. Viết 1 đoạn code tạo `Vector<Integer>` và `Collections.synchronizedList(new ArrayList<>())`, mỗi cái cho 4 thread cùng `add()` 10,000 phần tử.
2. Đo thời gian chạy của cả 2 bằng `System.nanoTime()`.
3. Giải thích tại sao 2 cách này thường cho thời gian gần tương đương (gợi ý: cả 2 đều `synchronized` toàn bộ method).

**Hoàn thành khi**: có bảng so sánh thời gian thực đo, không chỉ dựa vào lý thuyết.

---

### Bài 3: Bẫy `Arrays.asList()`
**Mục tiêu**: tránh bẫy kinh điển khi chuyển mảng sang List.

**Yêu cầu**:
1. Viết code tạo `List<String> list = Arrays.asList(new String[]{"a", "b", "c"});`
2. Thử gọi `list.add("d")` — quan sát exception ném ra, in `printStackTrace()` để đọc rõ nguyên nhân.
3. Thử `list.set(0, "z")` — có lỗi không? Vì sao `set()` khác `add()`/`remove()`?
4. Sửa lại để có 1 `List` thực sự mutable từ cùng mảng gốc.

**Hoàn thành khi**: giải thích được bằng lời (không chỉ code) sự khác biệt giữa "fixed-size list" và "immutable list".

---

## Mức 2 — Trung bình (Áp dụng thuật toán)

### Bài 4: Rotate Array (xoay mảng k vị trí)
**Mục tiêu**: áp dụng thao tác List + tư duy thuật toán.

**Đề bài**: Cho `List<Integer> list = [1, 2, 3, 4, 5, 6, 7]` và `k = 3`, xoay phải k vị trí để được `[5, 6, 7, 1, 2, 3, 4]`.

**Yêu cầu**:
1. Cài đặt 3 cách khác nhau:
   - Cách 1: dùng `Collections.rotate(list, k)` có sẵn.
   - Cách 2: tự viết bằng cách tạo `sublist` mới rồi ghép lại (`subList(0, n-k)` + `subList(n-k, n)`).
   - Cách 3: xoay tại chỗ (in-place) bằng 3 lần đảo ngược (reverse toàn bộ, rồi reverse từng phần) — kỹ thuật "3 reversal".
2. Viết test kiểm chứng cả 3 cách cho cùng 1 kết quả với `k > list.size()` (k = 10 cho list 7 phần tử).

**Gợi ý kỹ thuật "3 reversal"**:
```
reverse(0, n-1)       // đảo ngược toàn bộ: [7,6,5,4,3,2,1]
reverse(0, k-1)        // đảo ngược k phần tử đầu: [5,6,7,4,3,2,1]
reverse(k, n-1)        // đảo ngược phần còn lại: [5,6,7,1,2,3,4]
```

**Hoàn thành khi**: cả 3 cách cho cùng kết quả, và bạn giải thích được độ phức tạp thời gian/bộ nhớ của từng cách (cách nào O(1) extra space, cách nào không).

---

### Bài 5: Benchmark ArrayList vs LinkedList (đo thực tế, không đoán)
**Mục tiêu**: kiểm chứng bằng số liệu thay vì chỉ tin vào lý thuyết ở bảng so sánh mục 3 của bài học.

**Yêu cầu**: Viết chương trình benchmark với `N = 100,000` phần tử, đo thời gian (nano giây) cho 4 thao tác trên cả `ArrayList` và `LinkedList`:
1. `add()` liên tục ở **cuối** danh sách.
2. `add(0, x)` liên tục ở **đầu** danh sách.
3. `get(i)` với `i` **ngẫu nhiên** N lần.
4. Duyệt tuần tự toàn bộ bằng **enhanced for**.

**Yêu cầu trình bày**: in kết quả ra dạng bảng (loại thao tác | ArrayList (ms) | LinkedList (ms) | Ai nhanh hơn).

**Câu hỏi bắt buộc trả lời sau khi chạy xong**: Kết quả `add(0, x)` có đúng như lý thuyết dự đoán (`LinkedList` nhanh hơn) không? Nếu không đúng, hãy giải thích bằng khái niệm cache locality và object overhead đã học ở mục 4 bài học.

---

### Bài 6: Cài đặt LRU Cache hoàn chỉnh + Unit Test
**Mục tiêu**: áp dụng `LinkedHashMap` với access-order (đã có ví dụ khung ở mục 6.4 bài học).

**Yêu cầu**:
1. Hoàn thiện class `LRUCache<K, V>` với capacity truyền vào constructor.
2. Viết ít nhất 4 test case (JUnit hoặc `assert` thủ công):
   - Thêm đúng `capacity` phần tử → không phần tử nào bị xóa.
   - Thêm vượt `capacity` → phần tử **cũ nhất** (theo thứ tự truy cập) bị xóa.
   - `get()` 1 phần tử → phần tử đó trở thành "mới nhất", không bị xóa ở lần thêm tiếp theo dù nó được thêm sớm nhất.
   - `put()` lại 1 key đã tồn tại → giá trị được cập nhật, và key đó cũng được coi là "vừa truy cập".

**Hoàn thành khi**: cả 4 test case đều pass, và bạn giải thích được vì sao cần `accessOrder = true` trong constructor của `LinkedHashMap`.

---

## Mức 3 — Nâng cao (Tự cài đặt cấu trúc dữ liệu)

### Bài 7: Tự cài đặt `MyArrayList<T>`
**Mục tiêu**: hiểu sâu cơ chế mảng động bằng cách tự viết lại.

**Yêu cầu tối thiểu** — class `MyArrayList<T>` với:
- `void add(T item)` — thêm cuối, tự động `grow()` khi đầy (theo công thức x1.5 giống JDK thật).
- `T get(int index)` — ném `IndexOutOfBoundsException` nếu index không hợp lệ.
- `void add(int index, T item)` — chèn vào giữa, dịch chuyển phần tử phía sau.
- `T remove(int index)` — xóa và trả về phần tử bị xóa, dịch chuyển phần tử phía sau về trước.
- `int size()`, `boolean isEmpty()`.
- Method `int capacity()` để lộ ra capacity hiện tại (phục vụ việc test/quan sát grow).

**Yêu cầu nâng cao (tùy chọn)**: implements `Iterable<T>` để dùng được enhanced-for (`for (T item : myList)`).

**Test bắt buộc**:
1. Thêm 20 phần tử, kiểm tra `size()` đúng và capacity tăng đúng công thức.
2. Chèn vào giữa, kiểm tra thứ tự phần tử đúng sau khi chèn.
3. Xóa phần tử đầu, kiểm tra mọi phần tử dịch chuyển đúng.

---

### Bài 8: Tự cài đặt `MyLinkedList<T>` (Doubly Linked List)
**Mục tiêu**: hiểu sâu cấu trúc Node + con trỏ 2 chiều.

**Yêu cầu tối thiểu**:
- Class `Node<T>` nội bộ với `T data`, `Node<T> prev`, `Node<T> next`.
- `void addFirst(T item)`, `void addLast(T item)` — O(1).
- `T removeFirst()`, `T removeLast()` — O(1), ném exception nếu rỗng.
- `T get(int index)` — tự chọn duyệt từ đầu hay từ cuối tùy vị trí `index` gần đầu hay gần cuối hơn (tối ưu giống `LinkedList` thật của JDK).
- `int size()`.

**Test bắt buộc**:
1. `addFirst`/`addLast` xen kẽ, kiểm tra thứ tự đúng.
2. `get(index)` với index ở đầu, giữa, cuối — kiểm tra kết quả đúng.
3. `removeFirst()`/`removeLast()` liên tục cho đến rỗng, kiểm tra ném đúng exception khi gọi trên danh sách rỗng.

---

### Bài 9: Thiết kế hệ thống Undo/Redo bằng 2 Stack (ArrayDeque)
**Mục tiêu**: áp dụng `ArrayDeque` vào bài toán thực tế (không phải bài tập thuần lý thuyết).

**Đề bài**: Thiết kế class `TextEditor` mô phỏng 1 trình soạn thảo văn bản đơn giản, hỗ trợ:
- `void type(String text)` — thêm text vào cuối nội dung hiện tại, lưu lại trạng thái trước đó để undo được.
- `void undo()` — quay lại trạng thái trước lần `type()` gần nhất.
- `void redo()` — làm lại thao tác vừa undo (nếu có).
- `String getContent()` — trả về nội dung hiện tại.

**Yêu cầu thiết kế**: dùng 2 `Deque<String>` (undo stack và redo stack) để lưu lịch sử trạng thái — mỗi lần `type()` mới phải **xóa sạch redo stack** (vì lịch sử "tương lai" không còn hợp lệ sau khi có thao tác mới).

**Test bắt buộc**: `type("Hello")` → `type(" World")` → `undo()` (còn "Hello") → `undo()` (còn "") → `redo()` (còn "Hello") → `type("!")` (redo stack phải bị xóa, gọi lại `redo()` không còn tác dụng).

---

## Cách tự đánh giá

| Mức độ | Tiêu chí đạt |
|---|---|
| Cơ bản (1-3) | Chạy được, in ra đúng kết quả, giải thích được bằng lời (không chỉ copy code) |
| Trung bình (4-6) | Có số liệu benchmark thực tế hoặc test case rõ ràng, không chỉ dựa vào "chắc là đúng" |
| Nâng cao (7-9) | Cấu trúc dữ liệu tự viết hoạt động đúng với các edge case (rỗng, 1 phần tử, index biên) |

> **Gợi ý học tập**: sau khi hoàn thành mỗi bài, quay lại đọc phần tương ứng trong [02-list-chi-tiet.md](02-list-chi-tiet.md) và tự hỏi "code mình viết có khớp với cơ chế đã mô tả không?" — đây là cách tốt nhất để biến lý thuyết thành hiểu biết thực sự.

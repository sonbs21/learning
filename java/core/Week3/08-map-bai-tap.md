# Bài tập luyện tập: Map trong Java

> Bộ bài tập bám sát nội dung [04-map-chi-tiet.md](04-map-chi-tiet.md). Chia làm 3 mức độ: Cơ bản → Trung bình → Nâng cao. Mỗi bài đều ghi rõ mục tiêu, yêu cầu, gợi ý, và tiêu chí hoàn thành để bạn tự chấm.

---

## Mức 1 — Cơ bản (Củng cố kiến thức nền)

### Bài 1: Quan sát trực tiếp cơ chế collision và resize của HashMap
**Mục tiêu**: hiểu rõ từng bước của `put()` đã mô tả ở mục 2.1 bài học, không chỉ đọc lý thuyết.

**Yêu cầu**:
1. Tạo 1 class `BadKey` cố tình có `hashCode()` luôn trả về hằng số (VD `return 1;`) nhưng `equals()` vẫn so sánh đúng theo giá trị field — mục đích ép **mọi key đều rơi vào cùng 1 bucket**.
2. Thêm 20 object `BadKey` khác nhau vào `HashMap<BadKey, Integer>`, đo thời gian `get()` trung bình so với dùng `GoodKey` (có `hashCode()` phân bố tốt, VD dùng `Objects.hash()`).
3. Dùng Reflection lấy field `table` (mảng bucket) để đếm số bucket đang **thực sự chứa phần tử** với `BadKey` — chứng minh gần như toàn bộ 20 phần tử dồn vào 1 bucket.

**Gợi ý**: dùng `Field field = HashMap.class.getDeclaredField("table"); field.setAccessible(true);` (xem lại Reflection ở [Week12/01-kien-thuc-bo-tro.md](../Week12/01-kien-thuc-bo-tro.md)).

**Hoàn thành khi**: đo được chênh lệch thời gian thực tế giữa `BadKey` và `GoodKey`, và giải thích bằng lời tại sao `hashCode()` phân bố kém lại làm suy biến hiệu năng `HashMap` từ O(1) về gần O(n).

---

### Bài 2: `put()` trả về giá trị cũ — bẫy dễ bỏ sót
**Mục tiêu**: hiểu đúng hành vi trả về của `put()` khi key đã tồn tại.

**Yêu cầu**:
1. Viết đoạn code `put()` cùng 1 key 3 lần với 3 giá trị khác nhau, mỗi lần in ra **giá trị `put()` trả về** (không phải giá trị hiện tại trong map).
2. So sánh với `putIfAbsent()` — cũng cùng key, quan sát: giá trị trong map có bị ghi đè không, và `putIfAbsent()` trả về gì khi key đã tồn tại.
3. So sánh tiếp với `merge()` — dùng `merge(key, newValue, (old, new) -> old + new)`, quan sát kết quả cộng dồn.

**Hoàn thành khi**: bạn viết được bảng so sánh (bằng comment) cho biết chính xác giá trị trả về và trạng thái map sau mỗi loại thao tác: `put`, `putIfAbsent`, `merge`.

---

### Bài 3: Bẫy `ConcurrentModificationException` khi sửa Map lúc đang duyệt
**Mục tiêu**: tái hiện đúng bug đã cảnh báo ở mục 6.9 bài học và học cách sửa đúng.

**Yêu cầu**:
1. Viết code duyệt `map.keySet()` bằng enhanced-for và gọi `map.remove(key)` ngay trong vòng lặp khi thỏa điều kiện — chạy để tận mắt thấy `ConcurrentModificationException`.
2. Sửa lại bằng `Iterator` + `iterator.remove()` — kiểm chứng chạy đúng không lỗi.
3. Sửa lại lần 2 bằng `entrySet().removeIf(...)` — kiểm chứng cho cùng kết quả, ngắn gọn hơn.

**Hoàn thành khi**: cả 2 cách sửa đều chạy đúng và cho cùng 1 kết quả cuối cùng, bạn giải thích được vì sao cách 1 (enhanced-for + remove trực tiếp) lại lỗi trong khi 2 cách sau thì không.

---

## Mức 2 — Trung bình (Áp dụng thuật toán)

### Bài 4: Two Sum và Group Anagrams
**Mục tiêu**: thành thạo pattern dùng `HashMap` để giảm độ phức tạp từ O(n²) xuống O(n).

**Yêu cầu**:
1. Cài đặt `twoSum(int[] nums, int target)` đúng như mục 6.2 bài học, viết thêm 1 phiên bản brute-force O(n²) để so sánh.
2. Benchmark cả 2 cách với mảng 50,000 phần tử ngẫu nhiên (đo bằng `System.nanoTime()`), trình bày kết quả dạng bảng.
3. Cài đặt `groupAnagrams(String[] words)` đúng như mục 6.3 bài học, viết test với ít nhất 1 bộ dữ liệu có 3 nhóm anagram khác nhau xen kẽ nhau trong mảng đầu vào.

**Hoàn thành khi**: có số liệu benchmark thực tế cho Two Sum, và `groupAnagrams` nhóm đúng dù input không sắp xếp theo nhóm.

---

### Bài 5: Cài đặt LRU Cache hoàn chỉnh bằng LinkedHashMap
**Mục tiêu**: hiểu sâu cơ chế `accessOrder` + `removeEldestEntry()` đã mô tả ở mục 6.4 bài học.

**Yêu cầu**:
1. Hoàn thiện class `LRUCache<K, V>` đúng như mẫu ở mục 6.4, với `capacity` truyền vào constructor.
2. Viết ít nhất 5 test case:
   - Thêm đúng `capacity` phần tử → không phần tử nào bị xóa.
   - Thêm vượt `capacity` → phần tử **cũ nhất theo thứ tự truy cập** (không phải cũ nhất theo thứ tự chèn) bị xóa.
   - `get()` 1 phần tử → phần tử đó trở thành "mới nhất", không bị xóa ở lần thêm tiếp theo dù nó được thêm sớm nhất.
   - `put()` lại 1 key đã tồn tại → giá trị được cập nhật, và key đó cũng được coi là "vừa truy cập".
   - **So sánh trực tiếp** với 1 `LinkedHashMap` thường (`accessOrder = false`) trong cùng kịch bản — chứng minh nếu quên set `accessOrder = true`, hành vi LRU sẽ sai (xóa nhầm phần tử vừa mới `get()`).

**Hoàn thành khi**: cả 5 test case đều pass, và bạn giải thích được chính xác tại sao thiếu `accessOrder = true` sẽ phá vỡ toàn bộ logic LRU dù `removeEldestEntry()` vẫn hoạt động.

---

### Bài 6: Sweep Line với TreeMap — bài toán "Meeting Rooms II"
**Mục tiêu**: áp dụng kỹ thuật sweep line ở mục 6.6 bài học vào 1 bài toán thực tế.

**Đề bài**: Cho danh sách các khoảng thời gian họp `intervals = [[9,10],[9,12],[11,13],[15,16]]` (giờ bắt đầu, giờ kết thúc), tìm **số phòng họp tối thiểu** cần thiết để không có 2 cuộc họp nào trùng phòng cùng lúc.

**Yêu cầu**:
1. Cài đặt đúng kỹ thuật sweep line bằng `TreeMap<Integer, Integer>` như mục 6.6: `merge(start, 1, Integer::sum)`, `merge(end, -1, Integer::sum)`, duyệt `values()` theo thứ tự key để tìm `maxOverlap`.
2. Viết thêm 1 cách giải khác dùng `PriorityQueue` (sort theo giờ bắt đầu, dùng min-heap lưu giờ kết thúc) để so sánh 2 cách tiếp cận.
3. Test với ví dụ trên — kết quả đúng phải là `2` phòng.

**Hoàn thành khi**: cả 2 cách giải cho cùng kết quả đúng, và bạn giải thích được tại sao `TreeMap` (duyệt theo thứ tự key tăng dần) lại phù hợp tự nhiên cho kỹ thuật sweep line.

---

## Mức 3 — Nâng cao (Tự cài đặt & Đa luồng)

### Bài 7: Tự cài đặt `MyHashMap<K,V>` đơn giản
**Mục tiêu**: hiểu sâu cơ chế bucket + linked list bằng cách tự viết lại, đúng gợi ý bài học.

**Yêu cầu tối thiểu** — class `MyHashMap<K,V>` với mảng bucket tự quản lý (chưa cần treeify):
- `V put(K key, V value)` — trả về giá trị **cũ** nếu key đã tồn tại (ghi đè), `null` nếu là key mới.
- `V get(K key)` — trả về `null` nếu không tồn tại.
- `V remove(K key)`.
- `boolean containsKey(K key)`.
- `int size()`.
- Tự cài đặt cơ chế **resize** khi `size > capacity * 0.75` (nhân đôi capacity, rehash lại toàn bộ entry).

**Test bắt buộc**:
1. Thêm 20 entry, kiểm tra `size()` đúng và toàn bộ `get()` trả về đúng giá trị đã `put()`.
2. Ghi đè 1 key đã tồn tại, kiểm tra `put()` trả về đúng giá trị cũ.
3. Thêm đủ nhiều entry để kích hoạt `resize()` ít nhất 1 lần, kiểm tra sau resize mọi `get()` vẫn trả về đúng giá trị (không bị mất dữ liệu do rehash sai).

**Hoàn thành khi**: `MyHashMap` hoạt động đúng qua resize, và bạn giải thích được (bằng lời) vì sao sau khi resize phải **rehash lại toàn bộ entry** thay vì chỉ copy nguyên mảng cũ sang mảng mới.

---

### Bài 8: So sánh HashMap vs ConcurrentHashMap trong môi trường đa luồng
**Mục tiêu**: chứng minh bằng số liệu thực tế lý do không dùng `HashMap` trong ứng dụng đa luồng, đúng bài tập gợi ý ở mục cuối bài học.

**Yêu cầu**:
1. Viết chương trình đếm số từ (`word count`) trong 1 văn bản lớn (VD 1 triệu từ ngẫu nhiên từ 100 từ vựng khác nhau), chia công việc cho 8 thread cùng chạy song song.
2. Chạy với `HashMap` (không đồng bộ hóa thủ công) — so sánh tổng số đếm được với kết quả đúng (chạy đơn luồng) để chứng minh có sai lệch (do race condition) hoặc chương trình bị treo/lỗi.
3. Chạy lại với `ConcurrentHashMap` dùng `merge()` — kiểm chứng kết quả luôn khớp chính xác với kết quả đơn luồng, chạy lại nhiều lần để xác nhận tính ổn định.

**Hoàn thành khi**: bạn có bằng chứng cụ thể (log hoặc số liệu) cho thấy `HashMap` cho kết quả sai/không ổn định khi đa luồng, trong khi `ConcurrentHashMap` luôn đúng.

---

### Bài 9: Cài đặt Cache có giới hạn thời gian sống (TTL) kết hợp TreeMap + HashMap
**Mục tiêu**: kết hợp nhiều loại Map đã học vào 1 bài toán thiết kế tổng hợp.

**Đề bài**: Thiết kế class `TtlCache<K, V>` — 1 cache mà mỗi entry có thời gian sống giới hạn (Time To Live), tự động "hết hạn" sau `ttlMillis` mili-giây kể từ lúc `put()`.

**Yêu cầu thiết kế**:
- Dùng `HashMap<K, V>` để lưu giá trị thực tế, tra cứu O(1).
- Dùng `TreeMap<Long, List<K>>` (key là timestamp hết hạn) để biết **thứ tự** các key sẽ hết hạn — hỗ trợ phương thức `evictExpired()` duyệt từ đầu `TreeMap` (`firstEntry()`) và xóa các key đã hết hạn (`timestamp <= System.currentTimeMillis()`), dừng lại ngay khi gặp entry chưa hết hạn (không cần duyệt hết toàn bộ).
- `V get(K key)` — nếu key đã hết hạn (dù chưa được `evictExpired()` dọn), phải trả về `null` như thể không tồn tại.

**Test bắt buộc**:
1. `put("a", 1, ttlMillis=100)`, `get("a")` ngay lập tức phải trả về `1`.
2. Sau khi `Thread.sleep(150)`, `get("a")` phải trả về `null` dù chưa gọi `evictExpired()`.
3. Thêm 5 key với TTL khác nhau, gọi `evictExpired()` sau khi 1 phần đã hết hạn — kiểm chứng chỉ đúng các key hết hạn bị xóa khỏi cả `HashMap` lẫn `TreeMap`, các key còn hạn vẫn nguyên vẹn.

**Hoàn thành khi**: cả 3 test case pass, và bạn giải thích được vì sao dùng `TreeMap<Long, List<K>>` (thay vì duyệt toàn bộ `HashMap` mỗi lần) giúp `evictExpired()` hiệu quả hơn khi cache có nhiều entry.

---

## Cách tự đánh giá

| Mức độ | Tiêu chí đạt |
|---|---|
| Cơ bản (1-3) | Chạy được, in ra đúng kết quả, giải thích được bằng lời (không chỉ copy code) |
| Trung bình (4-6) | Có số liệu benchmark thực tế hoặc test case rõ ràng, không chỉ dựa vào "chắc là đúng" |
| Nâng cao (7-9) | Cấu trúc dữ liệu/hệ thống tự viết hoạt động đúng với các edge case (resize, đa luồng, hết hạn), giải thích được nguyên lý thiết kế đằng sau |

> **Gợi ý học tập**: sau khi hoàn thành mỗi bài, quay lại đọc phần tương ứng trong [04-map-chi-tiet.md](04-map-chi-tiet.md) và tự hỏi "code mình viết có khớp với cơ chế đã mô tả không?" — đây là cách tốt nhất để biến lý thuyết thành hiểu biết thực sự.

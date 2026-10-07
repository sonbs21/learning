# Bài tập luyện tập: Set trong Java

> Bộ bài tập bám sát nội dung [03-set-chi-tiet.md](03-set-chi-tiet.md). Chia làm 3 mức độ: Cơ bản → Trung bình → Nâng cao. Mỗi bài đều ghi rõ mục tiêu, yêu cầu, gợi ý, và tiêu chí hoàn thành để bạn tự chấm.

---

## Mức 1 — Cơ bản (Củng cố kiến thức nền)

### Bài 1: Chứng minh HashSet chỉ là "vỏ bọc" của HashMap
**Mục tiêu**: hiểu rõ cơ chế `add()` của `HashSet` thực chất gọi `map.put(e, PRESENT)` bên trong.

**Yêu cầu**:
1. Dùng Reflection lấy field `map` (private) bên trong 1 `HashSet<String>` để lấy ra `HashMap` thật đứng sau nó.
2. In ra `keySet()` của `HashMap` đó, so sánh với nội dung của `HashSet` ban đầu — chứng minh 2 tập giá trị giống hệt nhau.
3. Lấy tiếp field `PRESENT` (static, dummy value dùng chung cho mọi key) và in ra giá trị của nó cho **mọi** entry trong map — chứng minh mọi value đều trỏ tới đúng **1 object duy nhất**.

**Gợi ý**: dùng `Field field = HashSet.class.getDeclaredField("map"); field.setAccessible(true);` (xem lại Reflection ở [Week12/01-kien-thuc-bo-tro.md](../Week12/01-kien-thuc-bo-tro.md)).

**Hoàn thành khi**: in ra được đúng `HashMap` bên trong và chứng minh bằng code (không chỉ đọc lý thuyết) rằng mọi value trong map đều `==` nhau (cùng 1 object `PRESENT`).

---

### Bài 2: So sánh thứ tự duyệt giữa HashSet, LinkedHashSet, TreeSet
**Mục tiêu**: quan sát trực tiếp sự khác biệt về thứ tự để hiểu rõ khi nào nên chọn loại nào.

**Yêu cầu**:
1. Tạo cùng 1 danh sách 10 chuỗi ngẫu nhiên (không trùng), thêm lần lượt vào `HashSet`, `LinkedHashSet`, `TreeSet`.
2. In ra cả 3 tập, quan sát: `HashSet` thứ tự không dự đoán được, `LinkedHashSet` giữ đúng thứ tự chèn, `TreeSet` luôn sắp xếp.
3. Chạy chương trình **nhiều lần** (restart JVM), quan sát xem thứ tự in ra của `HashSet` có ổn định giữa các lần chạy không.

**Hoàn thành khi**: bạn viết được 1 đoạn nhận xét (dạng comment) mô tả chính xác sự khác biệt quan sát được, không chỉ dựa vào lý thuyết đã đọc.

---

### Bài 3: Bẫy quên override `equals()`/`hashCode()` trong HashSet
**Mục tiêu**: tái hiện đúng bug kinh điển đã cảnh báo ở mục 6.6 bài học.

**Yêu cầu**:
1. Viết class `Point` với field `int x, y`, **cố tình không override** `equals()`/`hashCode()`.
2. Thêm 2 object `Point` có cùng giá trị `x, y` vào `HashSet<Point>`, in `size()` — quan sát kết quả sai (in ra 2 thay vì 1).
3. Sửa lại `Point` override đúng `equals()`/`hashCode()` (dùng `Objects.equals()`/`Objects.hash()`), lặp lại bước 2 — kiểm chứng `size()` giờ in ra đúng 1.

**Hoàn thành khi**: bạn giải thích được bằng lời (không chỉ chạy code) tại sao `HashSet` "nghĩ" 2 object là khác nhau khi thiếu `equals()`/`hashCode()` — liên hệ lại cơ chế `map.put()` ở Bài 1.

---

## Mức 2 — Trung bình (Áp dụng thuật toán)

### Bài 4: Kiểm tra phần tử trùng lặp — O(n²) vs O(n)
**Mục tiêu**: đo lường thực tế lợi ích của `HashSet` so với thuật toán brute-force.

**Yêu cầu**:
1. Cài đặt 2 phiên bản hàm `containsDuplicate(int[] nums)`:
   - Cách 1: vòng lặp lồng nhau so sánh từng cặp phần tử — O(n²).
   - Cách 2: dùng `HashSet<Integer>`, gọi `add()` và kiểm tra giá trị trả về — O(n).
2. Benchmark cả 2 cách với mảng ngẫu nhiên kích thước `N = 1,000`, `10,000`, `100,000` phần tử (đo bằng `System.nanoTime()`).
3. Trình bày kết quả dạng bảng (N | Cách 1 (ms) | Cách 2 (ms)).

**Hoàn thành khi**: có số liệu benchmark thực tế chứng minh độ chênh lệch tăng mạnh khi N tăng, đúng như dự đoán độ phức tạp O(n²) vs O(n).

---

### Bài 5: Phép toán tập hợp — Union, Intersection, Difference, Symmetric Difference
**Mục tiêu**: thành thạo các method `addAll()`/`retainAll()`/`removeAll()` của `Set`.

**Yêu cầu**:
1. Cho 2 `Set<Integer>` bất kỳ, cài đặt đủ 4 phép toán:
   - Union (hợp).
   - Intersection (giao).
   - Difference (hiệu, `a - b`).
   - **Symmetric Difference** (phần tử chỉ thuộc `a` hoặc chỉ thuộc `b`, không thuộc cả 2) — tự nghĩ cách cài đặt từ các method có sẵn của `Set`, không dùng method dựng sẵn nào khác ngoài `addAll`/`retainAll`/`removeAll`.
2. Viết test kiểm chứng cả 4 phép toán cho kết quả đúng với ít nhất 2 bộ dữ liệu khác nhau (kể cả trường hợp 2 tập rời nhau hoàn toàn, và trường hợp 1 tập là con của tập kia).

**Gợi ý**: Symmetric Difference = `(union) - (intersection)`, hoặc `(a - b) ∪ (b - a)` — thử cả 2 cách và kiểm chứng cho cùng kết quả.

**Hoàn thành khi**: cả 4 phép toán chạy đúng, và bạn giải thích được vì sao cần `new HashSet<>(a)` (tạo bản sao) trước khi gọi `retainAll()`/`removeAll()` thay vì gọi trực tiếp trên `a` (tránh làm thay đổi tập gốc ngoài ý muốn).

---

### Bài 6: Range Query với TreeSet — quản lý lịch sự kiện
**Mục tiêu**: áp dụng `ceiling()`, `floor()`, `subSet()` vào bài toán thực tế.

**Đề bài**: Quản lý danh sách các mốc thời gian sự kiện (dùng `int` biểu diễn phút trong ngày), hỗ trợ:
- `Integer nextEventAfter(int time)` — trả về sự kiện gần nhất diễn ra **sau hoặc đúng** thời điểm `time` (dùng `ceiling()`).
- `Integer previousEventBefore(int time)` — trả về sự kiện gần nhất diễn ra **trước hoặc đúng** thời điểm `time` (dùng `floor()`).
- `int countEventsInRange(int from, int to)` — đếm số sự kiện trong khoảng `[from, to]` bao gồm cả 2 đầu (dùng `subSet(from, true, to, true)`).

**Test bắt buộc**:
1. Với tập sự kiện `{540, 600, 720, 900}` (9h, 10h, 12h, 15h tính theo phút), gọi `nextEventAfter(650)` phải trả về `720`.
2. `previousEventBefore(650)` phải trả về `600`.
3. `countEventsInRange(600, 900)` phải trả về `3`.
4. Test edge case: gọi `nextEventAfter()` với thời điểm lớn hơn mọi sự kiện — phải trả về `null` (không ném exception).

**Hoàn thành khi**: cả 4 test case đều pass, kể cả edge case trả về `null` khi không tìm thấy.

---

## Mức 3 — Nâng cao (Thuật toán & tự cài đặt)

### Bài 7: Longest Consecutive Sequence bằng HashSet — O(n)
**Mục tiêu**: giải đúng bài toán kinh điển với độ phức tạp tối ưu, tránh bẫy O(n log n) do sort.

**Đề bài**: Cho 1 mảng số nguyên không sắp xếp, tìm độ dài của dãy số **liên tiếp** dài nhất (không cần liền kề trong mảng gốc). VD: `[100, 4, 200, 1, 3, 2]` → dãy liên tiếp dài nhất là `[1, 2, 3, 4]`, độ dài `4`.

**Yêu cầu**:
1. Đưa toàn bộ phần tử vào `HashSet<Integer>` để tra cứu `contains()` trong O(1).
2. Với mỗi số `n`, chỉ bắt đầu đếm dãy nếu `n - 1` **không** có trong set (đảm bảo `n` là điểm bắt đầu của 1 dãy, tránh đếm lại từ giữa dãy nhiều lần).
3. Từ điểm bắt đầu, đếm liên tục `n+1, n+2, ...` cho tới khi không còn trong set.

**Test bắt buộc**: kiểm chứng độ phức tạp tổng thể vẫn là O(n) dù có vòng lặp lồng nhau (giải thích bằng lời tại sao — gợi ý: mỗi phần tử chỉ được duyệt tối đa 2 lần trong toàn bộ quá trình).

**Hoàn thành khi**: giải đúng bài toán và giải thích chính xác tại sao thuật toán là O(n) chứ không phải O(n²) dù nhìn có vẻ như vòng lặp lồng nhau.

---

### Bài 8: Tự cài đặt `MyHashSet<T>` đơn giản
**Mục tiêu**: hiểu sâu cơ chế bên trong bằng cách tự viết lại, đúng như gợi ý ở bài học.

**Yêu cầu tối thiểu** — class `MyHashSet<T>` bọc quanh 1 `HashMap<T, Object>` tự quản lý (hoặc mảng bucket tự cài đặt nếu muốn thử thách cao hơn):
- `boolean add(T item)` — trả về `false` nếu phần tử đã tồn tại (theo `equals()`), ngược lại thêm và trả về `true`.
- `boolean contains(T item)`.
- `boolean remove(T item)` — trả về `true` nếu xóa thành công.
- `int size()`.

**Yêu cầu nâng cao (tùy chọn)**: implement `Iterable<T>` để dùng được enhanced-for.

**Test bắt buộc**:
1. Thêm 5 phần tử, thêm lại 1 phần tử trùng — `size()` vẫn là 5, `add()` lần thứ 2 trả về `false`.
2. `remove()` 1 phần tử không tồn tại — trả về `false`, `size()` không đổi.
3. Dùng 1 class tự định nghĩa (có override `equals()`/`hashCode()` đúng) làm phần tử, kiểm chứng `MyHashSet` nhận diện trùng lặp theo giá trị, không theo reference.

---

### Bài 9: Bẫy `compareTo()` không nhất quán với `equals()` trong TreeSet — tái hiện và tự thiết kế quy tắc đúng
**Mục tiêu**: hiểu sâu rủi ro khi thiết kế `Comparable` cho object dùng trong `TreeSet`, đúng như cảnh báo ở mục 6.7 bài học.

**Yêu cầu**:
1. Tái hiện đúng bug ở mục 6.7: class `Employee implements Comparable<Employee>` chỉ so sánh theo `salary`, thêm 2 `Employee` khác `name` nhưng cùng `salary` vào `TreeSet` — chứng minh `size()` chỉ còn 1.
2. Sửa lại `compareTo()` để **nhất quán với `equals()`** — khi 2 `Employee` cùng `salary` nhưng khác `name`, `compareTo()` phải trả về khác 0 (gợi ý: so sánh thêm theo `name` khi `salary` bằng nhau, dùng `Comparator.comparingDouble(Employee::getSalary).thenComparing(Employee::getName)`).
3. Lặp lại test ở bước 1 với `Employee` đã sửa — kiểm chứng `size()` giờ đúng là 2, và cả 2 vẫn được sắp xếp đúng theo `salary` trước, `name` sau khi `salary` bằng nhau.

**Hoàn thành khi**: bạn trình bày được nguyên tắc chung (không chỉ riêng cho `Employee`) để tránh bug này: khi thiết kế `compareTo()` cho 1 class sẽ dùng trong `TreeSet`/`TreeMap`, nếu tiêu chí so sánh chính có thể trùng giữa 2 object khác nhau, phải thêm tiêu chí phụ (tie-breaker) cho tới khi đảm bảo `compareTo() == 0` chỉ xảy ra khi 2 object thực sự "bằng nhau" theo nghĩa nghiệp vụ.

---

## Cách tự đánh giá

| Mức độ | Tiêu chí đạt |
|---|---|
| Cơ bản (1-3) | Chạy được, in ra đúng kết quả, giải thích được bằng lời (không chỉ copy code) |
| Trung bình (4-6) | Có số liệu benchmark thực tế hoặc test case rõ ràng, không chỉ dựa vào "chắc là đúng" |
| Nâng cao (7-9) | Thuật toán/cấu trúc dữ liệu tự viết hoạt động đúng với các edge case, giải thích được độ phức tạp và nguyên tắc thiết kế tổng quát, không chỉ riêng cho ví dụ cụ thể |

> **Gợi ý học tập**: sau khi hoàn thành mỗi bài, quay lại đọc phần tương ứng trong [03-set-chi-tiet.md](03-set-chi-tiet.md) và tự hỏi "code mình viết có khớp với cơ chế đã mô tả không?" — đây là cách tốt nhất để biến lý thuyết thành hiểu biết thực sự.

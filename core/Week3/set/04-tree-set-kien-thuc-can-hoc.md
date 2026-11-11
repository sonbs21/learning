# Java `TreeSet` — Set có thứ tự và truy vấn theo khoảng

> `TreeSet` phù hợp khi cần một tập phần tử luôn được sắp xếp và cần tìm phần tử gần nhất, lấy đầu/cuối hoặc truy vấn theo range.
>
> Tài liệu này bổ sung cho [kiến thức tổng quan về `Set`](./01-set-kien-thuc-can-hoc.md), [chi tiết về `HashSet`](./02-hash-set-kien-thuc-can-hoc.md) và [chi tiết về `LinkedHashSet`](./03-linked-hash-set-kien-thuc-can-hoc.md).

---

## 1. Mục tiêu đầu ra

Sau khi học xong, cần có khả năng:

- [ ] Giải thích cách `TreeSet` dùng thứ tự để lưu phần tử.
- [ ] Phân biệt natural ordering và custom `Comparator`.
- [ ] Hiểu vì sao comparator trả `0` quyết định tính duy nhất.
- [ ] Dùng thành thạo API của `NavigableSet`.
- [ ] Dùng range view đúng cách và hiểu nó là live view.
- [ ] Tính được độ phức tạp của các thao tác chính.
- [ ] Nhận ra lỗi do comparator sai, mutable element và `null`.
- [ ] Chọn đúng giữa `TreeSet`, `HashSet`, `LinkedHashSet` và concurrent sorted set.

---

## 2. `TreeSet` là gì?

`TreeSet<E>` là một `SortedSet` và `NavigableSet`:

- Luôn duy trì phần tử theo natural order hoặc `Comparator` được cung cấp.
- Không chứa hai phần tử mà thứ tự coi là tương đương.
- `add`, `contains`, `remove` có độ phức tạp `O(log n)`.
- Có API tìm phần tử nhỏ/lớn gần một giá trị.
- Có range view như `subSet`, `headSet`, `tailSet`.
- Không thread-safe.
- Natural-order `TreeSet` thường không cho `null`.

```java
NavigableSet<Integer> scores = new TreeSet<>();
scores.add(70);
scores.add(90);
scores.add(80);
scores.add(80); // duplicate

System.out.println(scores); // [70, 80, 90]
```

`TreeSet` không phải set giữ thứ tự chèn. Nếu thêm `90` trước `70`, kết quả vẫn được duyệt theo thứ tự tăng dần.

### 2.1. Khi nào nên dùng?

- Cần invariant “dữ liệu luôn sorted”.
- Cần tìm phần tử gần nhất hoặc theo khoảng.
- Cần lấy phần tử nhỏ nhất/lớn nhất nhiều lần.
- Cần duy trì một leaderboard hoặc danh sách ưu tiên có comparator.

### 2.2. Khi nào không nên dùng?

- Chỉ cần membership nhanh: cân nhắc `HashSet`.
- Cần giữ thứ tự input: dùng `LinkedHashSet`.
- Cần index/random access: dùng `List`.
- Chỉ sort một lần: dùng `List` rồi `sort` có thể đơn giản hơn.
- Cần thread-safe sorted set: cân nhắc `ConcurrentSkipListSet`.

---

## 3. Cấu trúc bên trong

### 3.1. Backing bởi `TreeMap`

Trong OpenJDK, `TreeSet` thường được triển khai bằng một `TreeMap`. Phần tử của set là key của map; value chỉ là marker nội bộ.

```text
TreeSet.add(element)
        └── backingMap.put(element, PRESENT)

TreeSet.contains(element)
        └── backingMap.containsKey(element)
```

`TreeMap` dùng cây tìm kiếm cân bằng, thường là Red-Black Tree. Đây là chi tiết implementation giúp giải thích độ phức tạp, không phải lý do để dùng reflection truy cập backing map.

### 3.2. Vì sao là `O(log n)`?

```text
                 50
              /      \
            30        80
          /   \      /  \
        10    40    70   90
```

Comparator quyết định đi sang trái hoặc phải. Cây được cân bằng để chiều cao logarithmic, nhờ đó `contains`, `add` và `remove` không phải duyệt tuần tự toàn bộ phần tử.

Red-Black Tree có thể thực hiện rotation và recoloring sau insert/delete. Khi dùng `TreeSet`, chỉ cần hiểu mục tiêu là giữ cây cân bằng; không cần tự cài đặt hay phụ thuộc màu node, rotation hoặc field nội bộ.

---

## 4. Natural ordering và `Comparator`

### 4.1. Natural ordering

Nếu không truyền comparator, phần tử cần có thứ tự tự nhiên phù hợp:

```java
Set<String> names = new TreeSet<>();
names.add("Charlie");
names.add("Alice");
names.add("Bob");

System.out.println(names); // [Alice, Bob, Charlie]
```

Không trộn các kiểu không có quan hệ ordering rõ ràng:

```java
Set<Object> values = new TreeSet<>();
values.add("text");
values.add(10); // ClassCastException khi so sánh khác kiểu
```

### 4.2. Custom comparator

```java
Comparator<String> byLengthThenText =
        Comparator.comparingInt(String::length)
                  .thenComparing(Comparator.naturalOrder());

NavigableSet<String> names = new TreeSet<>(byLengthThenText);
names.add("Bob");
names.add("An");
names.add("Alice");

System.out.println(names); // [An, Bob, Alice]
```

Comparator nên có thứ tự nhất quán, bắc cầu và nhất quán với `equals()`.

### 4.3. Comparator quyết định cả uniqueness

```java
TreeSet<String> byLength = new TreeSet<>(
        Comparator.comparingInt(String::length)
);

byLength.add("An");
byLength.add("Vy"); // false: comparator trả 0

System.out.println(byLength); // [An]
```

Hai chuỗi khác nhau theo `equals()` nhưng comparator trả `0` sẽ bị `TreeSet` coi là cùng một phần tử. Nếu cần giữ cả hai, thêm tie-breaker:

```java
Comparator<String> byLengthThenValue =
        Comparator.comparingInt(String::length)
                  .thenComparing(String::compareTo);
```

Với object:

```java
Comparator<User> byAgeThenId =
        Comparator.comparingInt(User::age)
                  .thenComparingLong(User::id);
```

### 4.4. Lỗi comparator thường gặp

- Trả `0` cho các object cần được giữ riêng.
- Không bắc cầu hoặc phụ thuộc trạng thái mutable bên ngoài.
- Xử lý `null` không nhất quán.
- Dùng phép trừ có thể overflow:

```java
// Không nên dùng nếu có nguy cơ overflow
(a, b) -> a - b

// Nên dùng
Integer.compare(a, b)
Long.compare(a, b)
```

---

## 5. Constructor và `null`

```java
TreeSet<Integer> natural = new TreeSet<>();
TreeSet<String> reverse = new TreeSet<>(Comparator.reverseOrder());
TreeSet<Integer> copy = new TreeSet<>(List.of(3, 1, 2));
// [1, 2, 3]
```

Constructor nhận `Collection` sẽ thêm từng phần tử theo comparator của set mới. Thứ tự input không quyết định thứ tự cuối cùng.

### 5.1. Natural-order set không nhận `null`

```java
TreeSet<String> values = new TreeSet<>();
values.add(null); // NullPointerException
```

### 5.2. Comparator xử lý `null`

```java
Comparator<String> nullsFirst =
        Comparator.nullsFirst(Comparator.naturalOrder());

TreeSet<String> values = new TreeSet<>(nullsFirst);
values.add(null);
values.add("B");
values.add("A");

System.out.println(values); // [null, A, B]
```

Chỉ dùng `null` khi nó có ý nghĩa nghiệp vụ và comparator xử lý nó nhất quán.

---

## 6. API `NavigableSet` phải thành thạo

### 6.1. Phần tử đầu/cuối

```java
NavigableSet<Integer> values = new TreeSet<>(List.of(10, 20, 30));

values.first(); // 10, ném NoSuchElementException nếu rỗng
values.last();  // 30, ném NoSuchElementException nếu rỗng

values.pollFirst(); // trả 10 và xóa
values.pollLast();  // trả 30 và xóa
```

Nếu set có thể rỗng và không muốn exception, dùng `pollFirst/pollLast` hoặc kiểm tra `isEmpty` trước.

### 6.2. Tìm phần tử gần nhất

| API | Ý nghĩa |
|---|---|
| `lower(e)` | Phần tử lớn nhất nhưng `< e` |
| `floor(e)` | Phần tử lớn nhất nhưng `<= e` |
| `ceiling(e)` | Phần tử nhỏ nhất nhưng `>= e` |
| `higher(e)` | Phần tử nhỏ nhất nhưng `> e` |

```java
NavigableSet<Integer> scores = new TreeSet<>(List.of(10, 20, 30));

scores.lower(20);   // 10
scores.floor(20);   // 20
scores.ceiling(25); // 30
scores.higher(20);  // 30
```

Các method trên trả `null` nếu không có phần tử phù hợp.

### 6.3. `descendingSet`

```java
NavigableSet<Integer> ascending = new TreeSet<>(List.of(1, 2, 3));
NavigableSet<Integer> descending = ascending.descendingSet();

System.out.println(descending); // [3, 2, 1]
```

`descendingSet()` là live view, không phải bản copy. Nếu cần collection độc lập:

```java
NavigableSet<Integer> copy = new TreeSet<>(descending);
```

Thay đổi hợp lệ qua view phản ánh vào set gốc:

```java
descending.remove(3);
System.out.println(ascending); // [1, 2]
```

---

## 7. Range view: `subSet`, `headSet`, `tailSet`

### 7.1. Các range cơ bản

```java
NavigableSet<Integer> scores =
        new TreeSet<>(List.of(10, 20, 30, 40, 50));

SortedSet<Integer> below30 = scores.headSet(30);   // [10, 20]
SortedSet<Integer> from30 = scores.tailSet(30);    // [30, 40, 50]
SortedSet<Integer> middle = scores.subSet(20, 50); // [20, 30, 40]
```

Mặc định:

- `headSet(to)` là `[... , to)`, không gồm upper bound;
- `tailSet(from)` là `[from, ...]`, gồm lower bound;
- `subSet(from, to)` là `[from, to)`, gồm lower và loại upper.

### 7.2. Chỉ rõ inclusive/exclusive

```java
NavigableSet<Integer> closed =
        scores.subSet(20, true, 50, true); // [20, 50]

NavigableSet<Integer> open =
        scores.subSet(20, false, 50, false); // (20, 50)
```

Với comparator đảo chiều, ý nghĩa trước/sau được diễn giải theo comparator của set, không nhất thiết theo số học tự nhiên.

### 7.3. Range view là live view

```java
NavigableSet<Integer> scores = new TreeSet<>(List.of(10, 20, 30, 40));
NavigableSet<Integer> middle = scores.subSet(20, true, 40, false);

middle.remove(20);
System.out.println(scores); // [10, 30, 40]

scores.add(25);
System.out.println(middle); // [25, 30]
```

Nếu cần bản độc lập:

```java
Set<Integer> snapshot = new TreeSet<>(middle);
```

### 7.4. Thêm phần tử ngoài range

```java
NavigableSet<Integer> middle = scores.subSet(20, true, 40, false);
middle.add(50); // IllegalArgumentException
```

View phải giữ invariant rằng mọi phần tử của nó nằm trong giới hạn đã định. Bound không hợp lệ cũng gây lỗi:

```java
scores.subSet(50, 20); // IllegalArgumentException
```

---

## 8. Độ phức tạp

| Thao tác | Độ phức tạp |
|---|---:|
| `add` | `O(log n)` |
| `contains` | `O(log n)` |
| `remove` | `O(log n)` |
| `first`, `last` | `O(log n)` hoặc gần `O(1)` tùy implementation |
| `floor`, `ceiling`, `lower`, `higher` | `O(log n)` |
| `pollFirst`, `pollLast` | `O(log n)` |
| duyệt toàn bộ | `O(n)` |
| tạo range view | Thường `O(log n)` hoặc phụ thuộc implementation |

`TreeSet` đánh đổi lookup trung bình `O(1)` của `HashSet` để có sorted order và neighbor/range query `O(log n)`.

Không dùng `TreeSet` chỉ vì muốn sort một lần nếu collection nhỏ:

```java
List<Integer> sorted = new ArrayList<>(values);
sorted.sort(Comparator.naturalOrder());
```

Dùng `TreeSet` khi invariant “luôn sorted” hoặc các truy vấn theo thứ tự được sử dụng nhiều lần.

---

## 9. Equality, comparator và mutable element

### 9.1. `TreeSet` không dùng `equals()` như `HashSet`

Với `TreeSet`, hai element được xem là trùng khi comparator hoặc natural ordering trả `0`:

```java
Comparator<String> byLength = Comparator.comparingInt(String::length);
TreeSet<String> values = new TreeSet<>(byLength);

values.add("ab");
values.add("cd");

values.size(); // 1
```

Comparator nên nhất quán với `equals()` khi có thể. Nếu business cố ý dùng equality khác, phải ghi rõ trong thiết kế để tránh “mất” phần tử.

### 9.2. Mutable element làm hỏng thứ tự

Nếu comparator đọc field mutable rồi field thay đổi sau khi thêm, object có thể nằm ở vị trí không còn đúng:

```java
TreeSet<User> users = new TreeSet<>(
        Comparator.comparingInt(User::age)
);

User user = new User("An", 20);
users.add(user);

user.setAge(80); // thứ tự bên trong không tự xây lại
```

Không kỳ vọng `TreeSet` tự di chuyển node sau khi object thay đổi. Cách an toàn:

1. dùng immutable element;
2. remove trước khi thay đổi field dùng trong comparator;
3. thay đổi rồi add lại;
4. dùng key immutable và dữ liệu mutable ở cấu trúc khác.

### 9.3. Comparator không được phụ thuộc trạng thái bên ngoài

Comparator nên giữ cùng quy tắc trong suốt vòng đời set. Nếu comparator đọc biến bên ngoài rồi biến đó thay đổi, cấu trúc cây có thể không còn nhất quán với thứ tự hiện tại.

---

## 10. Thread safety

`TreeSet` không thread-safe. Các thao tác đọc/ghi đồng thời cần cơ chế đồng bộ.

### 10.1. Synchronized wrapper

```java
NavigableSet<Integer> synchronizedSet =
        Collections.synchronizedNavigableSet(new TreeSet<>());

synchronized (synchronizedSet) {
    for (Integer value : synchronizedSet) {
        // Phải khóa khi duyệt.
    }
}
```

Wrapper đồng bộ các method riêng lẻ. Chuỗi nhiều thao tác như “tìm rồi thêm” vẫn cần lock ngoài nếu cần atomicity.

### 10.2. Concurrent sorted set

```java
NavigableSet<Integer> concurrent = new ConcurrentSkipListSet<>();
```

`ConcurrentSkipListSet` phù hợp khi cần truy cập đồng thời, sorted order, các thao tác `NavigableSet` và iterator weakly consistent. Nó thường có expected `O(log n)`, không cho `null` và không phải drop-in replacement cho mọi workload.

---

## 11. Pattern sử dụng thực tế

### 11.1. Tìm điểm gần nhất

```java
NavigableSet<Integer> supported =
        new TreeSet<>(List.of(10, 20, 30, 40));

int requested = 26;
Integer lower = supported.floor(requested);   // 20
Integer upper = supported.ceiling(requested); // 30
```

Nếu cần điểm gần nhất tuyệt đối, so sánh khoảng cách giữa `lower` và `upper`, đồng thời xử lý trường hợp một bên là `null`.

### 11.2. Range filter

```java
NavigableSet<Integer> prices =
        new TreeSet<>(List.of(50, 100, 150, 200, 250));

NavigableSet<Integer> budget =
        prices.subSet(100, true, 200, true);
```

View giúp tránh tạo collection mới, nhưng phải nhớ nó liên kết với `prices`.

### 11.3. Lấy top/bottom

```java
TreeSet<Integer> values = new TreeSet<>(List.of(8, 2, 5, 1, 9));

Integer smallest = values.pollFirst();
Integer largest = values.pollLast();
```

Nếu cần giữ set gốc, dùng `first/last` thay vì `pollFirst/pollLast`.

### 11.4. Xếp hạng object

```java
record Score(String name, int value) {}

Comparator<Score> ranking =
        Comparator.comparingInt(Score::value)
                  .reversed()
                  .thenComparing(Score::name);

NavigableSet<Score> leaderboard = new TreeSet<>(ranking);
```

Tie-breaker theo `name` giúp hai người có cùng điểm vẫn được giữ riêng. Nếu chỉ compare theo `value`, người thứ hai có cùng điểm sẽ bị coi là duplicate.

---

## 12. Stream và chuyển đổi

### 12.1. Tạo sorted set từ stream

```java
NavigableSet<String> sorted = names.stream()
        .collect(Collectors.toCollection(TreeSet::new));
```

Với comparator:

```java
Comparator<String> reverse = Comparator.reverseOrder();
NavigableSet<String> reverseSorted = names.stream()
        .collect(Collectors.toCollection(() -> new TreeSet<>(reverse)));
```

`Collectors.toSet()` không đảm bảo trả về `TreeSet`. Khi cần sorted invariant, chỉ rõ `TreeSet` như trên.

### 12.2. Chuyển đổi sang list

```java
List<String> sortedList = new ArrayList<>(sorted);
```

List nhận thứ tự duyệt sorted của set. Nếu cần index-based access nhiều lần, dùng list sau khi sort có thể phù hợp hơn.

---

## 13. So sánh cách chọn implementation

| Nhu cầu | Lựa chọn |
|---|---|
| Membership nhanh, không cần order | `HashSet` |
| Membership nhanh và giữ thứ tự input | `LinkedHashSet` |
| Luôn sorted, nearest/range query | `TreeSet` |
| Sorted và concurrent | `ConcurrentSkipListSet` |
| Enum set nhỏ/gọn | `EnumSet` |
| Sort một lần rồi truy cập theo index | `ArrayList` + `sort` |

Chọn `TreeSet` khi thứ tự là invariant của dữ liệu, không chỉ là yêu cầu trình bày một lần.

---

## 14. Các bẫy thường gặp

- [ ] Nghĩ `TreeSet` giữ thứ tự chèn.
- [ ] Nhầm sorted order với encounter order.
- [ ] Comparator trả `0` cho các object cần được giữ riêng.
- [ ] Comparator không bắc cầu hoặc thay đổi trong lúc set đang tồn tại.
- [ ] Dùng phép trừ để so sánh số và gây overflow.
- [ ] Dùng natural-order set với element không có natural ordering.
- [ ] Trộn các kiểu không tương thích trong natural-order set.
- [ ] Thêm `null` vào `TreeSet` không có comparator xử lý null.
- [ ] Thay đổi field dùng trong comparator sau khi thêm element.
- [ ] Nghĩ range view là bản copy độc lập.
- [ ] Thêm phần tử ngoài range qua view và gặp `IllegalArgumentException`.
- [ ] Quên bound của `subSet` mặc định là upper-exclusive.
- [ ] Dùng `first/last` trên set rỗng và gặp `NoSuchElementException`.
- [ ] Gọi `pollFirst/pollLast` khi không muốn xóa dữ liệu.
- [ ] Phụ thuộc `Collectors.toSet()` trả về `TreeSet`.
- [ ] Nghĩ `TreeSet` thread-safe vì cấu trúc bên trong đã cân bằng.
- [ ] Dùng `TreeSet` khi chỉ cần sort một lần và cần random access.

---

## 15. Bài tập tự kiểm tra

1. Tạo `TreeSet<Integer>` và minh họa khác biệt giữa `lower`, `floor`, `ceiling`, `higher`.
2. Tạo comparator sắp xếp `User` theo tuổi tăng dần, sau đó theo tên; kiểm tra hai user cùng tuổi.
3. Viết ví dụ chứng minh comparator chỉ so sánh theo độ dài sẽ làm mất chuỗi có cùng độ dài.
4. Tạo `subSet` với các bound inclusive/exclusive và thử thêm phần tử ngoài range.
5. Viết method tìm giá trị gần nhất với một target bằng `floor` và `ceiling`.
6. So sánh `TreeSet` với `HashSet` + `stream.sorted()` cho workload insert nhiều và query sorted nhiều lần.

---

## 16. Tóm tắt cần nhớ

1. `TreeSet` giữ phần tử theo natural order hoặc comparator.
2. Cấu trúc sorted tree cho `add`, `contains`, `remove` độ phức tạp `O(log n)`.
3. Comparator/natural ordering quyết định cả thứ tự lẫn uniqueness.
4. Comparator trả `0` có nghĩa `TreeSet` coi hai phần tử là một.
5. `NavigableSet` cung cấp `floor`, `ceiling`, `lower`, `higher`, `pollFirst`, `pollLast`.
6. `subSet`, `headSet`, `tailSet` và `descendingSet` là live view, không phải copy.
7. Không thay đổi field dùng trong comparator khi element đang ở trong set.
8. Dùng `TreeSet` cho sorted invariant và range/nearest query; dùng implementation khác nếu chỉ cần membership hoặc insertion order.



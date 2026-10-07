# Java `List` — Toàn bộ kiến thức cần học

> Checklist này mở rộng phần `List` trong [Collections Framework](../01-collections-framework.md).  
> Mục tiêu: hiểu **tính chất**, **hợp đồng API**, **cơ chế bên trong**, **độ phức tạp**, cách chọn implementation và các lỗi thường gặp.

---

## 1. Mục tiêu đầu ra

Sau khi học xong, cần có khả năng:

- [ ] Giải thích `List` khác `Set`, `Queue` và array như thế nào.
- [ ] Sử dụng đúng các API thêm, đọc, sửa, xóa, tìm kiếm và duyệt.
- [ ] Phân biệt mutable, fixed-size, unmodifiable và immutable list.
- [ ] Giải thích cơ chế của `ArrayList`, `LinkedList`, `Vector` và `CopyOnWriteArrayList`.
- [ ] Tính được độ phức tạp thời gian và bộ nhớ của thao tác chính.
- [ ] Chọn đúng implementation theo dữ liệu và kiểu truy cập thực tế.
- [ ] Duyệt và xóa phần tử mà không gây `ConcurrentModificationException`.
- [ ] Hiểu view, shallow copy, defensive copy và ảnh hưởng của aliasing.
- [ ] Dùng `List` an toàn trong code đa luồng.
- [ ] Nhận ra các bẫy phổ biến và giải thích nguyên nhân thay vì chỉ nhớ cách sửa.

---

## 2. Nền tảng: `List` là gì?

```text
Iterable<E>
    └── Collection<E>
            └── List<E>
                    ├── ArrayList<E>
                    ├── LinkedList<E>
                    ├── Vector<E>
                    │       └── Stack<E>       (legacy)
                    └── CopyOnWriteArrayList<E>
```

### 2.1. Các tính chất bắt buộc phải biết

- `List<E>` là một `Collection<E>` có **thứ tự**.
- Mỗi phần tử có một **vị trí/index**, bắt đầu từ `0`.
- Cho phép **phần tử trùng lặp**.
- Thứ tự phần tử có ý nghĩa và được dùng khi so sánh hai list.
- Có thể cho phép `null`, nhưng điều này phụ thuộc implementation/cách tạo list.
- List có thể mutable hoặc không hỗ trợ một số/toàn bộ thao tác thay đổi.
- Generic `E` giúp kiểm tra kiểu tại compile time; collection chỉ chứa reference type, không chứa primitive trực tiếp.

```java
List<String> names = new ArrayList<>();
names.add("An");
names.add("Binh");
names.add("An");       // duplicate hợp lệ
names.add(null);       // ArrayList cho phép null

String first = names.get(0); // index bắt đầu từ 0
```

### 2.2. “Có thứ tự” không đồng nghĩa với “đã sắp xếp”

- **Ordered**: phần tử có thứ tự xác định trong list.
- **Sorted**: phần tử đã được xếp theo một `Comparator` hoặc natural order.

`ArrayList` giữ thứ tự chèn, nhưng không tự động sắp xếp.

### 2.3. Hợp đồng `equals()` và `hashCode()`

Hai list bằng nhau khi:

1. Có cùng số phần tử.
2. Các phần tử ở cùng index bằng nhau theo `Objects.equals()`.

Vì vậy thứ tự ảnh hưởng đến kết quả:

```java
List<Integer> a = List.of(1, 2, 3);
List<Integer> b = new LinkedList<>(List.of(1, 2, 3));
List<Integer> c = List.of(3, 2, 1);

a.equals(b); // true: implementation khác nhau nhưng nội dung và thứ tự giống nhau
a.equals(c); // false: thứ tự khác nhau
```

Các list bằng nhau phải có cùng `hashCode()`. Không nên dùng một list còn mutable làm key của `HashMap` hoặc phần tử của `HashSet`, vì thay đổi nội dung sẽ làm thay đổi hash code.

---

## 3. API của `List` phải thành thạo

### 3.1. Tạo và thêm phần tử

| API | Ý nghĩa |
|---|---|
| `add(E e)` | Thêm vào cuối |
| `add(int index, E e)` | Chèn vào vị trí, dịch các phần tử phía sau |
| `addAll(Collection)` | Thêm cả collection vào cuối |
| `addAll(int, Collection)` | Chèn cả collection tại một index |

Index hợp lệ khi chèn là từ `0` đến `size()`; index hợp lệ khi đọc/sửa/xóa là từ `0` đến `size() - 1`.

### 3.2. Đọc và sửa

| API | Ý nghĩa |
|---|---|
| `get(int index)` | Đọc phần tử tại index |
| `set(int index, E e)` | Thay phần tử và trả về giá trị cũ |
| `size()` | Số phần tử logic |
| `isEmpty()` | Kiểm tra list rỗng |

### 3.3. Tìm kiếm

| API | Ý nghĩa |
|---|---|
| `contains(Object o)` | Có phần tử bằng `o` hay không |
| `indexOf(Object o)` | Index xuất hiện đầu tiên, hoặc `-1` |
| `lastIndexOf(Object o)` | Index xuất hiện cuối cùng, hoặc `-1` |

Các API trên dùng `equals()`, không dùng `==`. Với object tự định nghĩa, phải cài đặt `equals()` phù hợp với nghiệp vụ.

### 3.4. Xóa và overload dễ nhầm

| API | Ý nghĩa |
|---|---|
| `remove(int index)` | Xóa theo index |
| `remove(Object o)` | Xóa lần xuất hiện đầu tiên bằng `o` |
| `removeIf(Predicate)` | Xóa mọi phần tử thỏa điều kiện |
| `clear()` | Xóa toàn bộ |

```java
List<Integer> numbers = new ArrayList<>(List.of(10, 20, 30));

numbers.remove(1);                  // xóa phần tử index 1: số 20
numbers.remove(Integer.valueOf(10)); // xóa object có giá trị 10
```

### 3.5. Biến đổi và sắp xếp

- `replaceAll(UnaryOperator)` — thay từng phần tử.
- `sort(Comparator)` — sắp xếp list tại chỗ.
- `Collections.reverse(list)` — đảo thứ tự.
- `Collections.shuffle(list)` — xáo trộn.
- `Collections.rotate(list, distance)` — xoay list.
- `Collections.binarySearch(list, key)` — tìm nhị phân; list phải được sắp xếp bằng cùng quy tắc so sánh.

---

## 4. Cơ chế của từng implementation

### 4.1. `ArrayList` — mảng động

#### Tính chất

- Lưu phần tử trong một mảng nội bộ.
- `size` là số phần tử đang có; `capacity` là sức chứa của mảng nội bộ.
- Truy cập index nhanh vì địa chỉ phần tử được tính trực tiếp.
- Chèn/xóa đầu hoặc giữa phải dịch chuyển phần tử.
- Dữ liệu nằm gần nhau nên tận dụng CPU cache tốt.

#### Cơ chế thêm cuối

```text
add(element)
    ├── còn capacity → ghi vào elementData[size]
    └── hết capacity
            ├── cấp phát mảng lớn hơn
            ├── copy phần tử cũ
            └── ghi phần tử mới
```

Một lần grow có chi phí `O(n)`, nhưng nhiều lần `add` cuối liên tiếp có chi phí trung bình **amortized `O(1)`**.

> Hệ số tăng capacity là **chi tiết cài đặt**, không phải cam kết trong API `List`/`ArrayList`; không nên viết logic ứng dụng phụ thuộc vào con số như `1.5x`.

#### API quản lý capacity

- `new ArrayList<>(initialCapacity)` — dùng khi ước lượng trước số phần tử.
- `ensureCapacity(n)` — bảo đảm sức chứa tối thiểu để giảm số lần copy.
- `trimToSize()` — giảm capacity về gần `size`; chỉ dùng khi có lý do đo được vì lần tăng sau lại phải cấp phát/copy.

#### Độ phức tạp điển hình

| Thao tác | Độ phức tạp |
|---|---|
| `get`, `set` | `O(1)` |
| `add` cuối | amortized `O(1)` |
| `add(index, e)` | `O(n)` |
| `remove(index)` | `O(n)` |
| `contains`, `indexOf` | `O(n)` |
| Duyệt toàn bộ | `O(n)` |

### 4.2. `LinkedList` — danh sách liên kết đôi

Mỗi node chứa:

```text
[previous reference | item | next reference]
```

List giữ reference đến node đầu và cuối.

#### Tính chất và cơ chế

- Thêm/xóa ở hai đầu là `O(1)`.
- Tìm node theo index phải đi từ đầu hoặc cuối, tùy phía nào gần hơn.
- Sau khi đã có vị trí iterator/node, thao tác liên kết hoặc tháo node là `O(1)`.
- Nếu gọi `add(index, e)` hoặc `remove(index)`, vẫn phải tìm node trước nên tổng thể là `O(n)`.
- Tốn thêm bộ nhớ cho node và hai reference.
- Cache locality kém hơn mảng động.
- Implements cả `List` và `Deque`.

| Thao tác | Độ phức tạp |
|---|---|
| `get`, `set` theo index | `O(n)` |
| `addFirst`, `addLast` | `O(1)` |
| `removeFirst`, `removeLast` | `O(1)` |
| `add/remove` theo index | `O(n)` do phải tìm node |
| `contains` | `O(n)` |

> Không nên duyệt `LinkedList` bằng `for (int i = 0; i < list.size(); i++) list.get(i)` vì có thể thành `O(n²)`. Dùng enhanced `for`, `Iterator` hoặc `ListIterator` để duyệt `O(n)`.

### 4.3. `Vector` và `Stack` — legacy

- `Vector` dùng mảng động và đồng bộ hóa các method riêng lẻ.
- `Stack` kế thừa `Vector`, là thiết kế cũ.
- Việc từng method là `synchronized` không làm một chuỗi nhiều thao tác tự động trở thành atomic.
- Code mới thường dùng `ArrayList`, synchronized wrapper hoặc concurrent collection phù hợp.
- Khi cần stack/queue một đầu hay hai đầu, thường ưu tiên `ArrayDeque`.

### 4.4. `CopyOnWriteArrayList` — snapshot khi đọc

- Mỗi thao tác ghi tạo/công bố một bản sao mảng mới.
- Đọc nhanh và không phải khóa toàn bộ đường đọc.
- Iterator nhìn thấy snapshot tại lúc iterator được tạo.
- Iterator không phản ánh thay đổi xảy ra sau đó.
- Các thao tác thay đổi qua iterator như `remove`, `set`, `add` không được hỗ trợ.
- Phù hợp khi **đọc rất nhiều, ghi rất ít**, ví dụ danh sách listener.
- Không phù hợp với list lớn hoặc ghi thường xuyên vì mỗi lần ghi tốn `O(n)` copy và tạo áp lực GC.

---

## 5. Cách chọn implementation

| Nhu cầu | Lựa chọn thường phù hợp |
|---|---|
| Mặc định, đọc theo index, duyệt nhiều | `ArrayList` |
| Thêm/xóa ở hai đầu và thực sự cần API `List` | `LinkedList` |
| Chỉ cần queue/stack/deque | `ArrayDeque`, không cần `List` |
| Dữ liệu cố định sau khi tạo | `List.of(...)` hoặc `List.copyOf(...)` |
| Nhiều thread, đọc nhiều và ghi rất hiếm | `CopyOnWriteArrayList` |
| Nhiều thread với mô hình truy cập khác | Xem lại thiết kế đồng bộ; không mặc định chọn `Vector` |

Quy tắc thực dụng: bắt đầu bằng `ArrayList`, chỉ đổi implementation khi yêu cầu dữ liệu hoặc kết quả profiling/benchmark chứng minh cần đổi.

### Marker interface `RandomAccess`

`ArrayList` implements `RandomAccess`, còn `LinkedList` không. Thuật toán generic có thể dựa vào marker này để chọn:

- vòng lặp theo index cho random-access list;
- iterator cho sequential-access list.

---

## 6. Duyệt list đúng cách

### 6.1. Các kiểu duyệt

```java
for (String item : list) { }                    // enhanced for
for (int i = 0; i < list.size(); i++) { }      // cần index
list.forEach(item -> System.out.println(item)); // lambda
Iterator<String> iterator = list.iterator();
ListIterator<String> cursor = list.listIterator();
```

### 6.2. `Iterator`

- Duyệt một chiều bằng `hasNext()` và `next()`.
- Có thể xóa phần tử vừa đọc bằng `iterator.remove()` nếu implementation hỗ trợ.
- Không gọi `remove()` trước `next()` hoặc hai lần liên tiếp cho cùng một phần tử.

### 6.3. `ListIterator`

- Chỉ dành cho `List`.
- Duyệt hai chiều: `next()` và `previous()`.
- Biết vị trí: `nextIndex()` và `previousIndex()`.
- Có thể `remove()`, `set(e)`, `add(e)` khi đang duyệt nếu list hỗ trợ thay đổi.
- Có thể bắt đầu từ một vị trí bằng `list.listIterator(index)`.

### 6.4. Fail-fast không phải cơ chế an toàn đa luồng

Iterator của `ArrayList`/`LinkedList` thường phát hiện thay đổi cấu trúc ngoài iterator thông qua trạng thái sửa đổi và có thể ném `ConcurrentModificationException`.

```java
Iterator<String> iterator = list.iterator();
while (iterator.hasNext()) {
    if (iterator.next().isBlank()) {
        iterator.remove(); // đúng: thay đổi qua chính iterator
    }
}
```

- Đây là cơ chế phát hiện lỗi theo kiểu **best effort**, không phải bảo đảm tuyệt đối.
- Không dùng exception này để điều khiển logic chương trình.
- Nó không biến collection thành thread-safe.
- “Fail-safe” không phải thuật ngữ chính thức của Java Collections API; nên mô tả chính xác là **snapshot iterator** (`CopyOnWriteArrayList`) hoặc **weakly consistent iterator** ở một số concurrent collections.

---

## 7. View, copy và tính bất biến

Đây là nhóm kiến thức rất dễ nhầm.

| Cách tạo | Thay đổi kích thước | `set` phần tử | Quan hệ với nguồn | `null` |
|---|---|---|---|---|
| `new ArrayList<>(source)` | Thay đổi được | Được | Shallow copy độc lập về cấu trúc | Cho phép |
| `Arrays.asList(array)` | Cố định | `set` được | View được backing bởi array | Cho phép |
| `Collections.unmodifiableList(source)` | Không sửa qua view | Không | Read-only view; nguồn đổi thì view đổi | Theo nguồn |
| `List.of(...)` | Không đổi | Không | Unmodifiable value-based list | Không cho phép |
| `List.copyOf(source)` | Không đổi | Không | Unmodifiable shallow copy | Không cho phép |
| `source.subList(from, to)` | Phụ thuộc list gốc | Thường sửa được | View `[from, to)` của list gốc | Theo nguồn |

### 7.1. Shallow copy

Copy list chỉ copy các reference; object phần tử vẫn dùng chung:

```java
List<Person> copy = new ArrayList<>(original);
copy.get(0).setName("New name"); // original cũng nhìn thấy Person đã đổi
```

Muốn tránh điều này phải dùng immutable element hoặc tự tạo deep copy theo domain.

### 7.2. `Arrays.asList()`

```java
String[] array = {"A", "B"};
List<String> view = Arrays.asList(array);

view.set(0, "X"); // array[0] cũng thành "X"
view.add("C");    // UnsupportedOperationException
```

Với primitive array, `Arrays.asList(intArray)` tạo list có **một phần tử là cả `int[]`**, không tạo `List<Integer>`.

### 7.3. `subList()`

`subList(from, to)` dùng khoảng nửa kín `[from, to)` và thường là một view:

```java
List<String> source = new ArrayList<>(List.of("A", "B", "C", "D"));
List<String> middle = source.subList(1, 3); // [B, C]
middle.clear();                              // source còn [A, D]
```

Nếu thay đổi cấu trúc list gốc bằng con đường khác trong khi view còn được sử dụng, hành vi của view không còn được bảo đảm. Nếu cần dữ liệu độc lập, dùng `new ArrayList<>(source.subList(...))`.

### 7.4. Unmodifiable không đồng nghĩa với phần tử bất biến

`List.of(person)` không cho thêm/xóa/thay reference trong list, nhưng vẫn có thể thay đổi `person` nếu `Person` là mutable. Bất biến sâu đòi hỏi cả cấu trúc list và các phần tử đều bất biến.

---

## 8. Generics cần biết khi dùng `List`

### 8.1. Invariance

`List<Integer>` không phải subtype của `List<Number>`.

```java
List<Integer> integers = List.of(1, 2);
// List<Number> numbers = integers; // compile error
```

### 8.2. PECS

- `? extends T`: producer — chủ yếu đọc ra kiểu `T`.
- `? super T`: consumer — có thể thêm giá trị kiểu `T`.

```java
double sum(List<? extends Number> values) { /* đọc Number */ }
void addDefaults(List<? super Integer> target) { target.add(0); }
```

Ghi nhớ: **Producer Extends, Consumer Super**.

### 8.3. Primitive và boxing

`List<int>` không hợp lệ; dùng `List<Integer>`. Autoboxing tiện lợi nhưng có chi phí object/bộ nhớ và có thể gây `NullPointerException` khi unbox `null`.

---

## 9. Sorting, searching và Stream

### 9.1. Sắp xếp

- Natural order: phần tử implements `Comparable`.
- Custom order: truyền `Comparator`.
- Kết hợp comparator bằng `comparing`, `thenComparing`, `reversed`, `nullsFirst`, `nullsLast`.

```java
people.sort(
    Comparator.comparingInt(Person::age)
              .thenComparing(Person::name)
);
```

Comparator phải nhất quán và có tính bắc cầu; comparator sai có thể làm kết quả sort không hợp lệ hoặc gây exception.

### 9.2. Binary search

- Chỉ đúng khi list đã được sort bằng cùng natural order/comparator.
- Kết quả âm biểu diễn insertion point nếu không tìm thấy.
- Trên list không hỗ trợ random access, thuật toán vẫn có thể tốn chi phí duyệt đáng kể.

### 9.3. Stream

- `stream()` không sửa list trừ khi callback tự tạo side effect.
- Tránh sửa source trong khi pipeline đang chạy.
- `stream().toList()` trả về list unmodifiable; không giả định implementation cụ thể.
- Không mặc định giả định list từ `Collectors.toList()` là một implementation hay có tính mutable cụ thể theo contract.
- Cẩn thận với parallel stream, shared mutable state và thứ tự encounter order.

---

## 10. Thread safety

### 10.1. List thông thường

`ArrayList` và `LinkedList` không thread-safe. Nếu nhiều thread cùng truy cập và ít nhất một thread thay đổi list, phải có chiến lược đồng bộ phù hợp.

### 10.2. Synchronized wrapper

```java
List<String> synchronizedList =
    Collections.synchronizedList(new ArrayList<>());

synchronized (synchronizedList) {
    for (String item : synchronizedList) {
        // duyệt dưới cùng monitor của wrapper
    }
}
```

Wrapper đồng bộ từng method. Chuỗi thao tác kiểu “kiểm tra rồi hành động” vẫn cần khóa ngoài để atomic:

```java
synchronized (synchronizedList) {
    if (!synchronizedList.contains("A")) {
        synchronizedList.add("A");
    }
}
```

### 10.3. Chọn theo workload

- Snapshot bất biến và thay cả reference: đơn giản, phù hợp dữ liệu cấu hình.
- `CopyOnWriteArrayList`: đọc nhiều, ghi rất ít.
- Synchronized wrapper/lock: cần bảo vệ thao tác ghép.
- Nếu bài toán thực chất là hàng đợi công việc, dùng concurrent queue/deque thay vì ép `List` làm queue.

---

## 11. Lỗi và bẫy phải nhận ra

- [ ] Nhầm `remove(index)` với `remove(object)` trên `List<Integer>`.
- [ ] Dùng `==` để so sánh object/String thay vì `equals()`.
- [ ] Gọi `add/remove` trên `List.of`, `List.copyOf`, unmodifiable view hoặc `Arrays.asList`.
- [ ] Nghĩ `Collections.unmodifiableList` là bản copy độc lập.
- [ ] Nghĩ unmodifiable list làm cho object phần tử cũng immutable.
- [ ] Sửa trực tiếp list trong enhanced `for`.
- [ ] Duyệt `LinkedList` bằng `get(i)` gây `O(n²)`.
- [ ] Cho rằng `LinkedList` luôn nhanh hơn khi chèn/xóa mà không tính chi phí tìm node và cache locality.
- [ ] Dùng list chưa sort với `binarySearch`.
- [ ] Dùng mutable list làm key trong hash-based collection.
- [ ] Quên `subList` là view liên kết với list gốc.
- [ ] Nghĩ `Vector` hoặc synchronized wrapper làm cả chuỗi thao tác trở thành atomic.
- [ ] Dùng `CopyOnWriteArrayList` cho workload ghi nhiều.
- [ ] Sửa trạng thái dùng chung bên trong parallel stream.
- [ ] Phụ thuộc vào capacity/grow factor nội bộ như một API contract.
- [ ] Benchmark thiếu warm-up rồi kết luận sai; benchmark nghiêm túc nên dùng JMH.

---

## 12. Lộ trình học đề xuất

### Mức 1 — Cơ bản

- [ ] Tính chất ordered, indexed, duplicate, `null`.
- [ ] CRUD bằng `add`, `get`, `set`, `remove`.
- [ ] `contains`, `indexOf`, `lastIndexOf`.
- [ ] Enhanced `for`, index loop, `Iterator`.
- [ ] Phân biệt `ArrayList` và `LinkedList`.

### Mức 2 — Làm việc thực tế

- [ ] Complexity và amortized complexity.
- [ ] `equals/hashCode` contract.
- [ ] `Comparator`, sort, binary search.
- [ ] `Arrays.asList`, `List.of`, `List.copyOf`, unmodifiable wrapper.
- [ ] `subList`, shallow copy, defensive copy.
- [ ] `Iterator` và `ListIterator`.
- [ ] Generics, wildcard và PECS.

### Mức 3 — Chuyên sâu

- [ ] Cơ chế grow/copy/shift của `ArrayList`.
- [ ] Node linking/unlinking của `LinkedList`.
- [ ] Cache locality, object overhead và GC pressure.
- [ ] `RandomAccess` marker.
- [ ] Fail-fast, snapshot iterator và weak consistency.
- [ ] Java Memory Model ở mức cần thiết cho concurrent collections.
- [ ] Synchronized wrapper, compound action và `CopyOnWriteArrayList`.
- [ ] Benchmark bằng JMH và lựa chọn cấu trúc dựa trên số đo.

### Mức 4 — Bổ sung theo phiên bản Java

- [ ] Từ Java 9: factory method `List.of`.
- [ ] Từ Java 10: `List.copyOf`.
- [ ] Từ Java 16: `Stream.toList`.
- [ ] Từ Java 21: `List` thuộc họ `SequencedCollection`, có API thao tác hai đầu/reversed view; cần kiểm tra implementation có hỗ trợ thao tác thay đổi hay không.

---

## 13. Bài thực hành để xác nhận đã hiểu

1. Viết `MyArrayList<E>` có `add`, `get`, `set`, `remove` và cơ chế grow.
2. Viết `MyLinkedList<E>` hai chiều, có thao tác ở hai đầu và iterator đơn giản.
3. Minh họa bốn loại: mutable, fixed-size, unmodifiable view và immutable list.
4. Viết ví dụ chứng minh `subList` và `Arrays.asList` là view được backing bởi nguồn.
5. Xóa số chẵn bằng `Iterator.remove()` và `removeIf()`.
6. So sánh vòng lặp index trên `ArrayList` và `LinkedList`.
7. Viết hàm generic dùng đúng `? extends` và `? super`.
8. Tạo test cho `equals/hashCode` giữa `ArrayList` và `LinkedList`.
9. Mô phỏng nhiều reader/ít writer với `CopyOnWriteArrayList`.
10. Dùng JMH đo `get`, thêm cuối, thêm đầu và duyệt tuần tự; giải thích kết quả bằng cơ chế bên trong.

---

## 14. Câu hỏi tự kiểm tra

1. Vì sao `ArrayList.add()` cuối là amortized `O(1)`?
2. Vì sao `LinkedList.add(index, value)` vẫn thường là `O(n)`?
3. Khi nào `ArrayDeque` phù hợp hơn `LinkedList`?
4. Hai list khác implementation có thể `equals()` nhau không?
5. Vì sao list mutable là một key nguy hiểm trong `HashMap`?
6. `Arrays.asList`, `List.of` và `Collections.unmodifiableList` khác nhau thế nào?
7. Thay đổi `subList` ảnh hưởng list gốc ra sao?
8. Vì sao `ConcurrentModificationException` không phải cơ chế bảo đảm thread safety?
9. Vì sao `CopyOnWriteArrayList` tốt cho đọc nhiều nhưng tệ cho ghi nhiều?
10. Vì sao vòng lặp `get(i)` có thể làm `LinkedList` thành `O(n²)`?
11. `? extends T` và `? super T` dùng khi nào?
12. Unmodifiable list có đồng nghĩa với deep immutability không?

---

## 15. Tài liệu liên quan trong repo

- [Tổng quan Collections Framework](../01-collections-framework.md)
- [Bài giải thích chi tiết về List](../02-list-chi-tiet.md)
- [Bài tập List](06-list-bai-tap.md)

## 16. Tài liệu Java chính thức

- [Java SE 21 — `List`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/List.html)
- [Java SE 21 — `ArrayList`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/ArrayList.html)
- [Java SE 21 — `LinkedList`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/LinkedList.html)
- [Java SE 21 — `Vector`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Vector.html)
- [Java SE 21 — `CopyOnWriteArrayList`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/CopyOnWriteArrayList.html)
- [Java SE 21 — `Arrays`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Arrays.html)
- [Java SE 21 — `Collections`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Collections.html)

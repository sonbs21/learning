# Tuần 4: Generics

> Tài liệu tổng hợp kiến thức mục 4 trong lộ trình `java-core-roadmap.md`.

---

## 1. Generic Class, Generic Method

### Vì sao cần Generics?
Trước Java 5, collection lưu `Object`, phải ép kiểu thủ công khi lấy ra → dễ lỗi runtime (`ClassCastException`) nếu bỏ nhầm kiểu.

```java
// Trước Generics (Java 1.4 trở về trước)
List list = new ArrayList();
list.add("Hello");
list.add(123); // vẫn compile được! không kiểm tra kiểu
String s = (String) list.get(1); // ClassCastException lúc CHẠY, không phải lúc biên dịch
```

Generics cho phép **kiểm tra kiểu tại thời điểm biên dịch (compile-time type safety)**, loại bỏ nhu cầu ép kiểu thủ công.

```java
List<String> list = new ArrayList<>();
list.add("Hello");
// list.add(123); // LỖI BIÊN DỊCH ngay lập tức, không đợi đến runtime
String s = list.get(0); // không cần ép kiểu
```

### Generic Class
```java
class Box<T> { // T là type parameter (tên tùy ý, quy ước: T, E, K, V, R...)
    private T value;

    public void set(T value) { this.value = value; }
    public T get() { return value; }
}

Box<String> stringBox = new Box<>();
stringBox.set("Hello");
String s = stringBox.get(); // không cần cast

Box<Integer> intBox = new Box<>();
intBox.set(123);
```

### Nhiều type parameter
```java
class Pair<K, V> {
    private K key;
    private V value;

    Pair(K key, V value) { this.key = key; this.value = value; }
    public K getKey() { return key; }
    public V getValue() { return value; }
}

Pair<String, Integer> pair = new Pair<>("age", 25);
```

### Generic Method
Method có thể tự khai báo type parameter riêng, **độc lập** với type parameter của class chứa nó (thậm chí trong 1 class không phải generic).

```java
class Utils {
    // <T> khai báo ngay trước kiểu trả về -> đây là generic method
    public static <T> void printArray(T[] array) {
        for (T element : array) {
            System.out.println(element);
        }
    }

    // Generic method với nhiều type parameter, có thể suy luận kiểu trả về khác
    public static <K, V> Pair<K, V> makePair(K key, V value) {
        return new Pair<>(key, value);
    }
}

Integer[] intArray = {1, 2, 3};
Utils.printArray(intArray); // T được suy luận tự động là Integer

String[] strArray = {"a", "b"};
Utils.printArray(strArray); // T được suy luận tự động là String
```

---

## 2. Bounded Type Parameter (`<T extends Number>`)

Giới hạn type parameter chỉ được là 1 kiểu cụ thể hoặc kiểu con của nó — cho phép gọi được các method của kiểu giới hạn đó (mà `Object` không có).

```java
// Không bound: chỉ dùng được các method của Object (toString, equals, hashCode...)
class Box<T> {
    T value;
}

// Bounded: T phải là Number hoặc subclass (Integer, Double, Long...)
class NumberBox<T extends Number> {
    T value;

    double doubleValue() {
        return value.doubleValue(); // gọi được method của Number - không thể nếu không bound
    }
}

NumberBox<Integer> box = new NumberBox<>(); // OK, Integer extends Number
// NumberBox<String> box2 = new NumberBox<>(); // LỖI biên dịch, String không phải Number
```

### Multiple bounds (nhiều giới hạn)
```java
// T phải vừa extends Number, vừa implements Comparable<T>
// Lưu ý: nếu có 1 class và nhiều interface, class PHẢI đứng trước
static <T extends Number & Comparable<T>> T max(T a, T b) {
    return a.compareTo(b) > 0 ? a : b;
}
```

### Bounded generic method — tìm phần tử lớn nhất
```java
static <T extends Comparable<T>> T findMax(List<T> list) {
    T max = list.get(0);
    for (T item : list) {
        if (item.compareTo(max) > 0) max = item;
    }
    return max;
}
```

---

## 3. Wildcard: `<?>`, `<? extends T>`, `<? super T>`

Wildcard dùng khi **không cần biết chính xác kiểu**, chỉ cần biết nó thuộc 1 họ kiểu nào đó — thường dùng ở **tham số method**, hiếm khi dùng để khai báo biến hay tạo object.

### `<?>` — Unbounded wildcard
Chấp nhận `List` của **bất kỳ kiểu nào**, nhưng chỉ đọc an toàn (không biết kiểu cụ thể nên không thể `add()` — trừ `null`).

```java
void printList(List<?> list) {
    for (Object o : list) { // chỉ đọc được ra kiểu Object
        System.out.println(o);
    }
    // list.add("x"); // LỖI biên dịch - không biết ? là kiểu gì nên không an toàn để thêm
}

printList(List.of(1, 2, 3));
printList(List.of("a", "b"));
```

### `<? extends T>` — Upper bounded wildcard (Producer — chỉ ĐỌC)
Chấp nhận `T` hoặc **bất kỳ subclass nào** của `T`. Dùng khi method chỉ **đọc** dữ liệu ra từ collection (collection đóng vai trò "producer" — sản xuất ra dữ liệu cho ta dùng).

```java
double sumOfList(List<? extends Number> list) {
    double sum = 0;
    for (Number n : list) { // an toàn để ĐỌC ra kiểu Number (vì mọi phần tử chắc chắn là Number hoặc con của nó)
        sum += n.doubleValue();
    }
    return sum;
    // list.add(10); // LỖI biên dịch! Không biết list là List<Integer> hay List<Double>,
    //                  nếu cho add(10) vào 1 List<Double> thực tế thì sẽ hỏng kiểu dữ liệu
}

sumOfList(List.of(1, 2, 3));       // List<Integer> - OK
sumOfList(List.of(1.5, 2.5));      // List<Double> - OK
```

### `<? super T>` — Lower bounded wildcard (Consumer — chỉ GHI)
Chấp nhận `T` hoặc **bất kỳ superclass nào** của `T`. Dùng khi method chỉ **ghi** dữ liệu vào collection (collection đóng vai trò "consumer" — tiêu thụ dữ liệu ta đưa vào).

```java
void addNumbers(List<? super Integer> list) {
    list.add(1);    // an toàn để GHI vào - vì list chắc chắn là Integer hoặc 1 kiểu cha của Integer
    list.add(2);
    // Integer x = list.get(0); // LỖI biên dịch! Không biết chắc kiểu trả về, chỉ có thể lấy ra dưới dạng Object
}

List<Integer> intList = new ArrayList<>();
addNumbers(intList); // OK, List<Integer>

List<Number> numList = new ArrayList<>();
addNumbers(numList); // OK, List<Number> (Number là cha của Integer)

List<Object> objList = new ArrayList<>();
addNumbers(objList); // OK, List<Object> (Object là cha của mọi thứ)
```

### Nguyên tắc ghi nhớ: **PECS — Producer Extends, Consumer Super**
> - Nếu collection chỉ dùng để **lấy dữ liệu ra** (produce) → dùng `? extends T`.
> - Nếu collection chỉ dùng để **đưa dữ liệu vào** (consume) → dùng `? super T`.
> - Nếu vừa đọc vừa ghi với kiểu cụ thể đã biết → không cần wildcard, dùng kiểu cụ thể trực tiếp.

```java
// Ví dụ kinh điển: Collections.copy(dest, src)
// src chỉ ĐỌC (producer) -> extends
// dest chỉ GHI (consumer) -> super
static <T> void copy(List<? super T> dest, List<? extends T> src) {
    for (int i = 0; i < src.size(); i++) {
        dest.set(i, src.get(i));
    }
}
```

---

## 4. Type Erasure và giới hạn của Generics tại Runtime

### Type Erasure là gì?
Generics trong Java là **compile-time only** — sau khi biên dịch, thông tin về type parameter bị **"xóa" (erase)** khỏi bytecode, thay thế bằng kiểu giới hạn (`Object` nếu không bound, hoặc kiểu bound nếu có).

```java
class Box<T> {
    T value;
    void set(T value) { this.value = value; }
}

// Sau type erasure, bytecode thực chất tương đương với:
class Box {
    Object value;
    void set(Object value) { this.value = value; }
}
```

```java
class NumberBox<T extends Number> {
    T value;
}
// Sau erasure: T bị thay bằng Number (kiểu bound)
class NumberBox {
    Number value;
}
```

**Bằng chứng type erasure — 2 generic instance khác kiểu vẫn cùng 1 `Class` object tại runtime:**
```java
List<String> stringList = new ArrayList<>();
List<Integer> intList = new ArrayList<>();
System.out.println(stringList.getClass() == intList.getClass()); // true! Cả 2 đều là ArrayList.class
```

### Các giới hạn do type erasure gây ra

**1. Không thể dùng `instanceof` với generic type cụ thể:**
```java
List<String> list = new ArrayList<>();
// if (list instanceof List<String>) { } // LỖI biên dịch
if (list instanceof List<?>) { }          // OK - chỉ kiểm tra được raw type
```

**2. Không thể tạo instance của type parameter (`new T()`):**
```java
class Factory<T> {
    T create() {
        // return new T(); // LỖI biên dịch - JVM không biết T là gì lúc runtime để new
        return null;
    }
}
```

**3. Không thể tạo mảng generic trực tiếp — chi tiết ở mục 5.**

**4. Không thể có static field kiểu T:**
```java
class Box<T> {
    // static T value; // LỖI biên dịch - static field thuộc về class, dùng chung cho MỌI T,
                        // nhưng T lại khác nhau tùy instance -> mâu thuẫn
}
```

**5. Không thể overload 2 method có tham số generic bị erase về cùng 1 kiểu:**
```java
class Processor {
    void process(List<String> list) { }
    // void process(List<Integer> list) { } // LỖI biên dịch! Sau erasure cả 2 đều là process(List)
}
```

### Vì sao Java thiết kế theo hướng type erasure?
- **Tương thích ngược (backward compatibility)**: Generics được thêm vào Java 5 (2004), nhưng phải đảm bảo code cũ (dùng raw type `List`) vẫn chạy được với bytecode mới mà không cần biên dịch lại toàn bộ thư viện đã có từ trước.

---

## 5. Generic Array — Vì sao không tạo được `new T[]`

```java
class Container<T> {
    // T[] array = new T[10]; // LỖI biên dịch: "Cannot create a generic array of T"
}
```

### Nguyên nhân sâu xa: mâu thuẫn giữa Generics (erasure) và Array (reified — giữ kiểu lúc runtime)

- **Mảng (Array)** trong Java là **"reified"** — tại runtime, mảng **biết và kiểm tra** kiểu phần tử của chính nó (`ArrayStoreException` sẽ được ném nếu cố gắng lưu sai kiểu vào mảng).
- **Generics** lại là **"erased"** — tại runtime, JVM **không biết** `T` thực sự là kiểu gì (đã bị xóa còn `Object`).

→ Nếu Java cho phép `new T[10]`, tại runtime JVM sẽ phải tạo ra 1 mảng kiểu gì đó — nhưng vì `T` đã bị erase, nó chỉ có thể tạo `Object[10]`. Điều này phá vỡ tính an toàn kiểu (type safety) mà mảng vốn đảm bảo, gây ra tình huống nguy hiểm:

```java
// Giả sử Java CHO PHÉP (thực tế thì không):
class Container<T> {
    T[] createArray(int size) {
        return new T[size]; // giả định được phép
    }
}

Container<String> container = new Container<>();
Object[] objArray = container.createArray(5); // thực chất tại runtime là Object[]
objArray[0] = 123; // Integer bị lưu vào mảng "tưởng là String[]" -> phá vỡ type safety hoàn toàn!
```

### Cách khắc phục thực tế
```java
class Container<T> {
    @SuppressWarnings("unchecked")
    T[] createArray(int size, Class<T> clazz) {
        // Dùng Reflection Array.newInstance() với Class token truyền vào tường minh
        return (T[]) java.lang.reflect.Array.newInstance(clazz, size);
    }
}

Container<String> container = new Container<>();
String[] arr = container.createArray(5, String.class); // phải truyền Class<T> làm "bằng chứng" về kiểu
```

Hoặc đơn giản hơn — **dùng `List<T>` thay vì mảng generic** (đây là cách được khuyến khích trong thực tế, cũng là lý do JDK Collections Framework hầu như không dùng mảng generic nội bộ theo kiểu này):
```java
class Container<T> {
    List<T> items = new ArrayList<>(); // linh hoạt, không gặp vấn đề generic array
}
```

> Ngược lại, **varargs generic** (`T... args`) thì Java **cho phép** (dù có cảnh báo `unchecked` ngầm) vì đây là 1 trường hợp đặc biệt được compiler hỗ trợ riêng, khác với khai báo mảng generic tường minh.

---

## Câu hỏi tự kiểm tra
1. Vì sao Generics giúp phát hiện lỗi kiểu ngay lúc biên dịch thay vì phải đợi đến `ClassCastException` lúc runtime như trước Java 5?
2. Bounded type parameter (`<T extends Number>`) mang lại lợi ích gì so với không bound? Cho ví dụ 1 method chỉ hoạt động được nhờ có bound.
3. Giải thích nguyên tắc PECS (Producer Extends, Consumer Super) bằng ví dụ `Collections.copy()`.
4. Type erasure là gì? Vì sao `List<String>.getClass() == List<Integer>.getClass()` lại trả về `true`?
5. Kể ra 3 giới hạn cụ thể của Generics tại runtime do type erasure gây ra.
6. Vì sao `new T[10]` không được phép trong khi `new T()` cũng không được phép — điểm chung giữa 2 hạn chế này là gì?
7. Nếu thực sự cần tạo mảng generic bên trong 1 class generic, có những cách nào để giải quyết?

## Bài tập luyện tập
- Viết 1 generic class `Stack<T>` (dùng `ArrayList<T>` bên trong) với các method `push`, `pop`, `peek`, `isEmpty`.
- Viết generic method `<T extends Comparable<T>> T findMin(List<T> list)` tìm phần tử nhỏ nhất trong danh sách.
- Viết method `copyElements(List<? super T> dest, List<? extends T> src)` tương tự `Collections.copy()`, thử nghiệm với các kiểu `Integer`/`Number`/`Object` để hiểu rõ PECS.
- Viết chương trình chứng minh type erasure: tạo `List<String>` và `List<Integer>`, in ra `getClass()` của cả 2 để thấy chúng giống nhau.
- Viết 1 generic class có method tạo mảng bằng `Class<T>` token (kỹ thuật ở mục 5), thử tạo mảng `String[]` và `Integer[]` từ cùng 1 class generic.

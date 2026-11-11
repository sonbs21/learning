# Review: Bài 5 — Overloading vs Overriding — bẫy static binding

File được đánh giá: [bai5.java](bai5.java)
Đối chiếu với: [02-oop-bai-tap.md](02-oop-bai-tap.md) — Bài 5

---

## Đánh giá lần 2 (sau khi sửa)

### Đã sửa đúng — chính xác hoàn toàn
```java
class Dog extends Animal {
    void feed(Dog d) { System.out.println("Dog:"+d); } // khác chữ ký feed(Animal) -> OVERLOAD thật sự
}
```
```java
d.feed(d);      // d khai báo kiểu Dog
ref.feed(d);    // ref khai báo kiểu Animal, CÙNG object Dog với d
```
Đây chính xác là cấu trúc đề bài yêu cầu. Truy vết lại toàn bộ `main()`:

| Dòng | Biến gọi | Kiểu khai báo | Overload được compiler chọn | Method thực chạy | In ra |
|---|---|---|---|---|---|
| `a.makeSound()` | `a` | `Animal` (object thật: `Dog`) | (không áp dụng — chỉ 1 method) | `Dog.makeSound()` (override, dynamic dispatch) | `Sound Dog` |
| `d.feed(d)` | `d` | `Dog` | `feed(Dog)` (khớp chính xác, ưu tiên hơn `feed(Animal)`) | `Dog.feed(Dog)` | `Dog:...` |
| `ref.feed(d)` | `ref` | `Animal` (object thật cũng là `Dog`) | `feed(Animal)` (compiler chỉ thấy được method của kiểu `Animal`) | `Animal.feed(Animal)` (Dog không override cái này) | `Animal:...` |

Đây **chính xác** là "bẫy" mà đề bài muốn minh họa: `ref` và `d` trỏ tới **cùng 1 object Dog**, nhưng `ref.feed(d)` lại chạy `Animal.feed()` chỉ vì `ref` được khai báo kiểu `Animal` — hoàn toàn trái ngược với `makeSound()` (luôn chạy đúng theo object thực tế `Dog`, bất kể biến khai báo kiểu gì). Đây là bước tiến đúng và đầy đủ nhất qua 2 lần sửa.

### Vấn đề còn lại: phần giải thích (comment) đã lỗi thời, chưa được cập nhật theo code mới
```
/*
Vì makeSound() là override lớp con dùng lại của lớp cha
Trong khi feed() chỉ giống tên thôi
*/
```
Đây **vẫn là đoạn comment cũ y nguyên từ lần 1**, viết cho code cũ (khi `feed()` chưa phải overload thật). Với code hiện tại, câu "feed() chỉ giống tên thôi" **càng sai hơn nữa** — giờ đây `feed(Dog)` và `feed(Animal)` không chỉ "giống tên", chúng là 2 overload **thực sự tồn tại song song**, và chính bài học cần giải thích là **cơ chế nào quyết định overload nào được chọn** (kiểu khai báo biến, không phải object thực tế) — điều mà comment hiện tại hoàn toàn chưa đề cập tới. Đây là điểm duy nhất còn thiếu để đạt tiêu chí "Hoàn thành khi" của đề bài.

### Bảng đối chiếu (lần 2)

| Yêu cầu đề bài | Trạng thái |
|---|---|
| 2. `Dog` overload `feed(Dog d)` (không override) | **Đạt đầy đủ** |
| 3. Kịch bản gọi qua biến khai báo kiểu khác nhau trên cùng 1 object | **Đạt đầy đủ** |
| 4. Dự đoán trước, chạy thử kiểm chứng | Đã chạy đúng, nhưng chưa thấy ghi lại dự đoán trước khi chạy |
| Tiêu chí hoàn thành: giải thích đúng cơ chế runtime (override) vs compile-time (overload) | **Chưa đạt** — comment giải thích vẫn là bản cũ, chưa phản ánh đúng code mới |

### Tổng kết lần 2
Phần code đã hoàn toàn chính xác — đây là bản demo đúng chuẩn về "bẫy static binding" mà đề bài muốn dạy. Việc còn lại chỉ là viết lại phần giải thích bằng lời cho khớp với hành vi thực tế của code mới, thay vì giữ nguyên đoạn comment cũ đã lỗi thời. Xem đáp án tham khảo bên dưới để có bản giải thích đầy đủ, chính xác theo đúng những gì code hiện tại thể hiện.

---

## Đáp án tham khảo (bản đúng đầy đủ theo yêu cầu đề bài)

```java
public class bai5 {
    public static void main(String[] args) {
        Animal a = new Dog();
        a.makeSound(); // (1) -> "Sound Dog" -- OVERRIDE: chọn theo OBJECT THỰC TẾ lúc runtime (Dog)

        Dog d = new Dog();
        Animal ref = d;       // ref và d CÙNG trỏ tới 1 object Dog, chỉ khác KIỂU KHAI BÁO

        d.feed(d);            // d khai báo kiểu Dog    -> compiler thấy được feed(Dog) -> chọn Dog.feed(Dog)
        ref.feed(d);          // ref khai báo kiểu Animal -> compiler CHỈ thấy feed(Animal) -> chọn Animal.feed(Animal)
                               // (dù ref và d là CÙNG 1 OBJECT Dog!)
    }
}

class Animal {
    void makeSound() {
        System.out.println("Sound Animal");
    }

    void feed(Animal a) {
        System.out.println("Animal: " + a);
    }
}

class Dog extends Animal {

    @Override
    void makeSound() {
        System.out.println("Sound Dog"); // đổi err -> out cho nhất quán với Animal
    }

    // OVERLOAD (khác chữ ký so với feed(Animal)) -- KHÔNG phải override
    void feed(Dog d) {
        System.out.println("Dog: " + d);
    }
}

/*
 * KẾT QUẢ CHẠY THỰC TẾ:
 *   Sound Dog
 *   Dog: Dog@<hash>
 *   Animal: Dog@<hash>
 *
 * GIẢI THÍCH:
 *
 * (1) a.makeSound() -> luôn chạy Dog.makeSound(), BẤT KỂ biến `a` được khai báo
 *     kiểu Animal. Đây là OVERRIDING: Java chọn method theo OBJECT THỰC TẾ nằm
 *     sau reference (dynamic/runtime binding) -- được quyết định LÚC CHẠY.
 *
 * (2) d.feed(d) và ref.feed(d) chạy trên CÙNG 1 OBJECT DOG nhưng in ra 2 kết
 *     quả khác nhau:
 *       - d.feed(d): biến `d` khai báo kiểu Dog -> trình biên dịch nhìn thấy cả
 *         feed(Dog) lẫn feed(Animal) (kế thừa), và chọn feed(Dog) vì đây là
 *         overload khớp CHÍNH XÁC hơn (không cần ép kiểu rộng lên Animal).
 *       - ref.feed(d): biến `ref` khai báo kiểu Animal -> trình biên dịch CHỈ
 *         nhìn thấy các method thuộc kiểu Animal, tức chỉ có feed(Animal) --
 *         nó KHÔNG HỀ BIẾT tới feed(Dog) của Dog, dù object thực tế của `ref`
 *         chính là 1 Dog.
 *     Đây là OVERLOADING: Java chọn overload nào dựa vào KIỂU KHAI BÁO của
 *     biến (static/compile-time binding) -- được quyết định NGAY LÚC BIÊN DỊCH,
 *     không quan tâm object thực tế lúc chạy là gì.
 *
 * TÓM LẠI: override phụ thuộc "vật thực tế đang cầm" (runtime), còn overload
 * phụ thuộc "tấm nhãn khai báo trên biến" (compile-time). Đây là bẫy phổ biến
 * nhất khi mới học OOP: tưởng rằng vì object thực tế là Dog nên Java sẽ luôn
 * "thông minh" chọn phiên bản feed() dành riêng cho Dog -- nhưng KHÔNG, nếu
 * biến được khai báo kiểu Animal, Java chỉ thấy được method của Animal mà thôi.
 */
```

---

## Kết luận nhanh (đánh giá lần 1)

Code biên dịch và chạy được, nhưng **thiếu đúng thành phần cốt lõi mà cả bài tập xoay quanh**: `Dog` không hề thêm **overload** `feed(Dog d)` như đề bài yêu cầu — nó chỉ viết lại y hệt `feed(Animal a)` đã có ở `Animal`, tức là **override**, không phải **overload**. Vì vậy toàn bộ tình huống "gây nhiễu" giữa overloading (compile-time) và overriding (runtime) mà đề bài muốn minh họa **không hề xảy ra** — `feed()` trong code hiện tại hoạt động y hệt cơ chế của `makeSound()` (đơn thuần override + dynamic dispatch), không có gì để so sánh đối lập cả.

---

## Lỗi nghiêm trọng theo yêu cầu đề bài

### 1. Thiếu overload `feed(Dog d)` trong `Dog` — đây là trọng tâm của cả bài tập
Đề bài mục 2 yêu cầu rõ: *"Viết thêm 1 method overload gây nhiễu: `Animal` có `void feed(Animal a)`, `Dog` (**không override, mà overload**) thêm `void feed(Dog d)`."*

Code hiện tại:
```java
class Animal {
    void feed(Animal a) { System.out.println(a); }
}

class Dog extends Animal {
    void feed(Animal a) { System.out.println(a); } // <- CÙNG chữ ký với Animal -> đây là OVERRIDE, không phải overload!
}
```
`Dog.feed(Animal a)` có **chữ ký giống hệt 100%** với `Animal.feed(Animal a)` (cùng tên, cùng kiểu tham số `Animal`) — về mặt kỹ thuật Java, đây chính xác là 1 **override**, dù không có annotation `@Override`. Đề bài muốn `Dog` thêm 1 method **khác chữ ký** (`feed(Dog d)`, tham số kiểu `Dog` thay vì `Animal`) để tạo ra 2 method **overload** cùng tồn tại — đó mới là tình huống "gây nhiễu" thật sự cần có.

**Sửa đúng**:
```java
class Dog extends Animal {
    // KHÔNG lặp lại feed(Animal a) — chỉ thêm 1 overload MỚI với tham số kiểu Dog
    void feed(Dog d) {
        System.out.println("Feeding a Dog with special food: " + d);
    }
}
```

### 2. Vì thiếu overload thật, kịch bản `main()` không thể minh họa đúng "bẫy static binding"
Đề bài đưa ra kịch bản mẫu:
```java
Animal a = new Dog();
a.makeSound();      // (1)
Dog d = new Dog();
Animal ref = d;
feedSomething(ref); // gọi hàm feed nào?
```
Ý đồ của kịch bản này là so sánh **cùng 1 object Dog**, nhưng gọi `feed()` qua 2 **biến khai báo kiểu khác nhau** (`Dog d` vs `Animal ref`) để cho thấy: overload nào được chọn phụ thuộc vào **kiểu khai báo của biến tại compile-time**, không phụ thuộc object thực tế lúc runtime — trái ngược hẳn với `makeSound()` (luôn theo object thực tế nhờ override).

Code hiện tại chỉ viết:
```java
a.feed(ref); // a và ref đều khai báo kiểu Animal
```
Vì chỉ tồn tại đúng 1 method `feed(Animal a)` (do lỗi ở mục 1), lời gọi này **chắc chắn** chạy `feed(Animal a)`, và do `Dog` "override" (nhầm) method đó nên tại runtime nó nhảy vào `Dog.feed(Animal a)` — **hành vi giống hệt `makeSound()`**, không có gì đối lập để so sánh. Không có bước nào gọi `feed()` qua 1 biến khai báo kiểu `Dog` để cho thấy sự khác biệt.

**Kịch bản đúng cần có** (sau khi đã sửa mục 1):
```java
Dog d = new Dog();
Animal ref = d;         // CÙNG 1 object Dog, nhưng khai báo kiểu khác nhau

d.feed(d);              // d khai báo kiểu Dog -> compiler thấy được feed(Dog d) -> gọi Dog.feed(Dog)
ref.feed(d);            // ref khai báo kiểu Animal -> compiler CHỈ thấy feed(Animal a) (kế thừa từ Animal)
                         // -> gọi Animal.feed(Animal), DÙ ref và d trỏ tới CÙNG 1 object Dog!
```
Đây mới là "bẫy" thật sự: 2 dòng gọi `feed()` trên cùng 1 object nhưng cho ra 2 method khác nhau, chỉ vì kiểu khai báo biến khác nhau — trong khi `makeSound()` luôn nhất quán theo object thực tế bất kể khai báo kiểu gì.

### 3. Lời giải thích ở comment cuối file hiểu sai bản chất
```
/*
Vì makeSound() là override lớp con dùng lại của lớp cha
Trong khi feed() chỉ giống tên thôi
*/
```
Câu "feed() chỉ giống tên thôi" (ý nói `Dog.feed()` và `Animal.feed()` là 2 method tách biệt, chỉ tình cờ trùng tên) là **sai** — như đã phân tích ở mục 1, `Dog.feed(Animal a)` có **chữ ký giống hệt** `Animal.feed(Animal a)`, nên về bản chất kỹ thuật nó **là override**, không phải "chỉ giống tên". Nếu thêm `@Override` vào trước `Dog.feed(Animal a)` như hiện tại, trình biên dịch sẽ **chấp nhận bình thường** (vì đây đúng là 1 override hợp lệ) — đây chính là cách nhanh nhất để tự phát hiện ra nhầm lẫn: nếu nghĩ 1 method là overload nhưng thêm `@Override` vẫn compile được, nghĩa là nó thực chất là override.

---

## Điểm đã làm đúng

- `makeSound()` cài đặt và override đúng chuẩn (`@Override` + đúng chữ ký) — đúng yêu cầu mục 1 của đề bài.
- `main()` đúng cấu trúc khai báo biến `Animal a = new Dog()`, `Dog d = new Dog()`, `Animal ref = d` — đúng với mẫu đề bài đưa ra (chỉ thiếu bước gọi `feed()` qua biến `d` để so sánh).

---

## Vấn đề nhỏ

- `Dog.makeSound()` dùng `System.err.println` trong khi `Animal.makeSound()` dùng `System.out.println` — không sai về mặt kết quả (cả 2 đều lên console) nhưng không nhất quán, không có lý do rõ ràng để đổi stream ở đây.
- `Dog.feed(Animal a)` thiếu `@Override` — như đã phân tích ở mục 3, nếu thêm vào sẽ compile được và giúp phát hiện sớm rằng đây là override chứ không phải overload.

---

## Bảng đối chiếu tiêu chí đề bài

| Yêu cầu đề bài | Trạng thái |
|---|---|
| 1. `Animal.makeSound()`, `Dog` override `makeSound()` | Đạt |
| 2. `Animal.feed(Animal a)`, `Dog` **overload** thêm `feed(Dog d)` | **Không đạt** — `Dog` chỉ override lại `feed(Animal a)`, không hề có overload `feed(Dog d)` |
| 3. Kịch bản gọi `makeSound()` và `feed()` qua các biến khai báo kiểu khác nhau | Một phần — có đúng cấu trúc biến, nhưng thiếu bước gọi `feed()` qua biến `Dog` để tạo đối lập |
| 4. Dự đoán trước rồi chạy thử kiểm chứng | Có ghi lại 1 nhận định bằng comment, nhưng **nội dung giải thích sai bản chất** |
| Tiêu chí hoàn thành: giải thích đúng vì sao `makeSound()` theo runtime còn `feed()` theo compile-time | **Chưa đạt** — vì `feed()` hiện tại không hề có overload để so sánh, và giải thích đưa ra không chính xác |

---

## Tổng kết
Vấn đề duy nhất nhưng mang tính quyết định của bài này: `Dog` chưa thêm đúng overload `feed(Dog d)` mà đề bài yêu cầu, khiến toàn bộ tình huống "bẫy static binding" giữa overloading và overriding không có cơ hội xảy ra — `feed()` hiện tại chỉ hoạt động y hệt `makeSound()` (override + dynamic dispatch thông thường). Cần: (1) đổi `Dog.feed(Animal a)` thành `Dog.feed(Dog d)` (khác kiểu tham số), (2) trong `main()`, gọi `feed()` cả qua biến khai báo kiểu `Dog` lẫn kiểu `Animal` trên cùng 1 object để thấy 2 kết quả khác nhau, và (3) viết lại phần giải thích cho đúng: `makeSound()` luôn theo **object thực tế** (runtime, nhờ override + dynamic dispatch), còn overload nào của `feed()` được chọn lại phụ thuộc vào **kiểu khai báo của biến** tại **compile-time** (static binding) — đây chính là điểm khác biệt cốt lõi đề bài muốn minh họa.

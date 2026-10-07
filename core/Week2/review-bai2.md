# Review: Bài 2 — Constructor chaining với `this()` và `super()`

File được đánh giá: [bai2.java](bai2.java)
Đối chiếu với: [02-oop-bai-tap.md](02-oop-bai-tap.md) — Bài 2

---

## Đánh giá lần 3 (mới nhất, sau khi sửa)

### Đã sửa đúng — bước tiến lớn nhất qua 3 lần
1. **Cấu trúc `this()` chaining giờ đã đúng hoàn toàn**: `Car(String brand) { this(brand, 4); }` — đúng chính xác yêu cầu 2c mà 2 lần review trước đều chưa đạt.
2. **Constructor đầy đủ đã hardcode `wheels = 4`**: `Car(String brand, int doors) { super(brand, 4); ... }` — đúng yêu cầu 2b, đúng cấu trúc tham số (`brand`, `doors`) như gợi ý ở review lần 2.
3. **`toString()`**: đúng tên chuẩn, có `@Override` thật (không còn bị comment) ở cả `Vehicle` lẫn `Car`.
4. **Lỗi thiếu khoảng trắng ở `Vehicle.toString()`** đã sửa xong (`" wheels"` có dấu cách).

Đây là lần đầu tiên cấu trúc 2 constructor khớp đúng tinh thần "constructor chaining" mà đề bài muốn: 1 constructor "đầy đủ" chứa logic thật (gọi `super`), 1 constructor rút gọn **ủy quyền lại** cho constructor kia qua `this()`.

### Lỗi mới phát sinh — nghiêm trọng

#### 1. [BUG MỚI] Quên gán `this.doors = doors` trong constructor đầy đủ
```java
Car(String brand, int doors) {
    super(brand, 4);       // constructor "đầy đủ" — gọi super, hardcode 4 bánh
}
```
Tham số `doors` được nhận vào nhưng **không bao giờ được gán vào field `this.doors`**. Vì tham số `doors` **shadow (che khuất)** field cùng tên, bên trong constructor chỉ có tham số cục bộ tồn tại rồi biến mất khi constructor kết thúc — field `this.doors` giữ nguyên giá trị mặc định `0` suốt vòng đời object, bất kể truyền gì vào.

**Hậu quả khi chạy thực tế**:
```java
Car c = new Car("Lexus", 6);   // ý định: xe 6 cửa
System.out.println(c);
// In ra: "This car is Lexus brand has 0 doors,4 wheels"  <- SAI, phải là 6 doors!
```
Đây là lỗi **cùng một mẫu** với lỗi đã gặp ở Bài 1 (`deposit()`/`withdraw()` quên gán lại `this.balance`): nhận tham số, dùng nó ở chỗ khác (hoặc hardcode giá trị khác), nhưng quên bước cuối cùng — gán lại vào field.

**Sửa**: thêm đúng 1 dòng `this.doors = doors;` ngay sau `super(brand, 4);`.

### Vẫn chưa hoàn thành sau 3 lần sửa

#### 2. Chưa thực hiện thí nghiệm "đặt `super()` sai vị trí"
Comment cuối file vẫn là câu kết luận có sẵn, nội dung giống hệt lần 1 và lần 2 — qua 3 lần sửa vẫn chưa có bằng chứng đã thực sự di chuyển `super(...)` xuống dưới để tự mắt thấy lỗi compiler, và lời giải thích vẫn chưa nêu đúng bản chất "thứ tự khởi tạo cha trước con".

### Vấn đề rất nhỏ
Dòng `new Car( "Lexus", 6)` có 1 dấu cách thừa sau dấu `(` — không ảnh hưởng chức năng, chỉ là style nit.

### Bảng đối chiếu (lần 3)

| Yêu cầu | Trạng thái |
|---|---|
| 2b. Constructor đầy đủ gọi `super(brand, 4)` | Đúng cấu trúc, nhưng **quên gán `this.doors`** nên field luôn = 0 |
| 2c. Constructor rút gọn gọi `this(brand, 4)` | **Đạt đầy đủ** |
| 3. `toString()` + `@Override` | **Đạt đầy đủ** |
| 4. Thử nghiệm `super()` sai vị trí | Vẫn chưa đạt (như 2 lần trước) |

### Tổng kết lần 3
Đây là bước tiến lớn nhất qua 3 lần sửa: cấu trúc `this()`/`super()` chaining cuối cùng đã đúng chính xác yêu cầu đề bài. Tuy nhiên phát sinh 1 bug mới — quên `this.doors = doors` — khiến field `doors` luôn là `0` bất kể truyền gì vào, đúng dạng lỗi "quên gán lại field" đã từng gặp ở Bài 1. Chỉ cần thêm đúng 1 dòng là xong. Phần thí nghiệm `super()` sai vị trí vẫn là điểm duy nhất còn thiếu sau 3 lần sửa.

---

## Đáp án tham khảo (bản đúng đầy đủ theo yêu cầu đề bài)

```java
public class bai2 {
    public static void main(String[] args) {
        Vehicle c1 = new Car("Lexus", 6);  // constructor đầy đủ: 6 cửa, wheels hardcode = 4
        System.out.println(c1);

        Car c2 = new Car("Toyota");        // constructor rút gọn: mặc định 4 cửa, chain qua this()
        System.out.println(c2);
    }
}

class Vehicle {
    String brand;
    int wheels;

    Vehicle(String brand, int wheels) {
        this.brand = brand;
        this.wheels = wheels;
    }

    @Override
    public String toString() {
        return "The vehicle is " + brand + " brand, has " + wheels + " wheels";
    }
}

class Car extends Vehicle {
    int doors;

    // Constructor "đầy đủ" — logic thật nằm ở đây, hardcode wheels = 4 vì "xe hơi luôn 4 bánh"
    Car(String brand, int doors) {
        super(brand, 4);
        this.doors = doors; // BẮT BUỘC — nếu thiếu dòng này, doors sẽ luôn = 0
    }

    // Constructor "rút gọn" — KHÔNG lặp lại logic, chỉ ủy quyền qua this(), mặc định 4 cửa
    Car(String brand) {
        this(brand, 4);
    }

    @Override
    public String toString() {
        return "This car is " + brand + " brand, has " + doors + " doors, " + wheels + " wheels";
    }
}

/*
 * Thí nghiệm bắt buộc của đề bài (mục 4): thử đặt super(...) KHÔNG PHẢI dòng đầu tiên.
 *
 * Car(String brand, int doors) {
 *     this.doors = doors;   // đặt TRƯỚC super() -> LỖI BIÊN DỊCH
 *     super(brand, 4);
 * }
 *
 * Trình biên dịch báo lỗi:
 *   error: call to super must be first statement in constructor
 *
 * Giải thích: Java bắt buộc object cha (Vehicle) phải được khởi tạo XONG HOÀN TOÀN
 * trước khi phần khởi tạo riêng của object con (Car) được phép chạy. Nếu cho phép
 * đặt code khác trước super(), đoạn code đó có thể vô tình đọc/ghi lên các field kế
 * thừa từ Vehicle (brand, wheels) khi chúng CHƯA TỪNG được khởi tạo -> object rơi vào
 * trạng thái không nhất quán ngay từ lúc "chưa sinh ra". Bắt super() đứng đầu đảm bảo:
 * phần của cha luôn được xây xong trước, con mới được phép xây tiếp phần của mình.
 */
```

**Điểm mấu chốt cần nhớ trong đáp án này**:
- Chỉ 1 constructor (`Car(String, int)`) chứa logic thật (`super(...)` + gán field) — constructor kia (`Car(String)`) không lặp lại logic, chỉ **ủy quyền** qua `this(...)`. Đây chính là lợi ích thực sự của constructor chaining: tránh trùng lặp code giữa các constructor.
- **Luôn gán lại field ngay sau khi gọi `super(...)`** nếu tham số đó cần lưu trữ — đây là lỗi dễ quên nhất (đã xảy ra ở cả Bài 1 và Bài 2 trong quá trình bạn tự sửa).
- `super(...)`/`this(...)` luôn phải là dòng đầu tiên của constructor — không thể vừa gọi `super()` vừa gọi `this()` trong cùng 1 constructor (chỉ được chọn 1 trong 2).

---

## Đánh giá lần 2 (sau khi sửa)

### Đã sửa đúng so với lần 1
1. **`toString()`**: đã đổi đúng tên method chuẩn (`public String toString()`) cho cả `Vehicle` và `Car`, thay vì `toText()` tự đặt như lần 1 — yêu cầu mục 3 của đề bài giờ đã đạt.
2. **Đã thêm 1 constructor hardcode `wheels = 4`**: `Car(String brand, int doors) { super(brand, 4); ... }` — có nơi thể hiện đúng ý "xe hơi luôn 4 bánh" mà lần 1 hoàn toàn chưa có.

Tuy nhiên, khi đối chiếu kỹ với **đúng cấu trúc 2 constructor mà đề bài yêu cầu**, vẫn còn lệch đáng kể — đặc biệt là lỗi cốt lõi xuyên suốt cả 2 lần review vẫn chưa được sửa.

### Lỗi nghiêm trọng còn tồn tại

#### 1. [CHƯA SỬA — vẫn là lỗi cốt lõi] Không có constructor nào gọi `this(...)`
Code hiện có 2 constructor trong `Car`:
```java
Car(int doors, String brand, int wheels) {
    super(brand, wheels);
    this.doors = doors;
}

Car(String brand, int doors) {
    super(brand, 4); // hardcode 4 bánh, không nhận tham số wheels nữa
    this.doors = doors;
}
```
Cả 2 đều gọi **`super(...)` trực tiếp** — không có constructor nào chain qua `this(...)`. Đây là y nguyên lỗi đã nêu ở lần review 1, sau 1 lượt sửa vẫn chưa được đụng tới, dù đây chính là trọng tâm của cả bài tập ("Constructor chaining với `this()` **và** `super()`" — vế `this()` vẫn hoàn toàn vắng mặt).

Theo đúng đề bài, cấu trúc đúng phải là 2 constructor **phụ thuộc lẫn nhau**:
```java
Car(String brand, int doors) {
    super(brand, 4);       // constructor "đầy đủ" — gọi super, hardcode 4 bánh
}

Car(String brand) {
    this(brand, 4);        // constructor rút gọn — CHAIN qua constructor trên, mặc định 4 cửa
}
```
Ở code hiện tại, 2 constructor chỉ là 2 overload **độc lập, song song** (khác nhau ở thứ tự/số tham số), không có quan hệ "gọi lẫn nhau" nào — nên "chaining" chưa từng xảy ra.

#### 2. [CHƯA SỬA] Constructor 3-tham-số vẫn không hardcode `wheels`, vẫn cho tạo "Car" bao nhiêu bánh cũng được
```java
Car(int doors, String brand, int wheels) {
    super(brand, wheels); // wheels vẫn tự do, y nguyên lỗi từ lần 1
    ...
}
```
Bằng chứng ngay trong `main()` hiện tại:
```java
Vehicle c = new Car(4, "Lexus", 6); // tạo ra "Car" 6 bánh — đúng cái lỗi đã cảnh báo ở lần review 1!
```
Lỗi này không những chưa sửa mà còn được chính `main()` mới phơi bày rõ ràng hơn — 1 chiếc xe hơi 6 bánh chạy được bình thường, không có gì ngăn cản.

#### 3. Constructor mới (`Car(String brand, int doors)`) sai signature so với yêu cầu đề bài
Đề bài yêu cầu constructor rút gọn phải **"chỉ nhận `brand`"** (1 tham số duy nhất). Constructor hiện tại vẫn nhận **2 tham số** (`brand` và `doors`), nên về bản chất nó chỉ là 1 overload khác của constructor đầy đủ (đổi thứ tự tham số + hardcode `wheels`), chứ không phải "phiên bản rút gọn mặc định 4 cửa" như đề bài mô tả. `doors = 4` phải là giá trị **mặc định do `this(brand, 4)` cung cấp**, không phải do caller tự truyền vào.

#### 4. [CHƯA SỬA] Vẫn chưa thực hiện thí nghiệm "đặt `super()` sai vị trí"
Comment cuối file vẫn giữ nguyên nội dung như lần 1 (chỉ xuống dòng khác do dài hơn):
```java
// super phải nằm đầu để khởi tạo và truyền value của Car và class cha là
// Vehicle
```
Vẫn là phát biểu **kết luận có sẵn**, chưa có bằng chứng đã thực sự di chuyển `super(...)` xuống dưới để tự quan sát lỗi compiler thật, và vẫn chưa giải thích đúng bản chất "thứ tự khởi tạo cha trước con" như "Hoàn thành khi" của đề bài yêu cầu.

### Vấn đề nhỏ còn tồn tại / mới phát sinh

#### 5. `@Override` vẫn bị comment out (chưa sửa)
```java
// @Override
public String toString() {
```
Vẫn y nguyên từ lần 1.

#### 6. Lỗi thiếu khoảng trắng trong chuỗi in ra — vẫn chưa sửa
`"...has 4wheels"` và `"...4 doors,4 wheels"` vẫn dính liền/thiếu dấu cách như lần 1.

#### 7. [MỚI] Gọi `.toString()` tường minh trong `println` là dư thừa
```java
System.out.println(c.toString());
```
`System.out.println(Object)` đã **tự động gọi `toString()`** của object được truyền vào — viết `System.out.println(c);` là đủ, không cần gọi `.toString()` tường minh. Không sai, chỉ hơi thừa.

---

## Bảng đối chiếu tiêu chí đề bài (lần 2)

| Yêu cầu đề bài | Trạng thái |
|---|---|
| 1. `Vehicle` có field `brand`, `wheels` + 1 constructor đầy đủ tham số | Đạt |
| 2a. `Car` thêm field `doors` | Đạt |
| 2b. Constructor đầy đủ tham số của `Car` gọi `super(brand, 4)` (hardcode 4) | **Vẫn không đạt** — constructor 3-tham-số vẫn nhận `wheels` tự do (`main()` tạo được Car 6 bánh) |
| 2c. Constructor chỉ nhận `brand`, gọi `this(brand, 4)` | **Vẫn không đạt** — constructor mới nhận 2 tham số (`brand`, `doors`), gọi `super()` chứ không phải `this()` |
| 3. `toString()` cho cả 2 class, override ở `Car` | **Đạt** (đã sửa đúng) |
| 4. Thử nghiệm đặt `super()` sai vị trí, quan sát lỗi compiler | Vẫn không đạt — chưa có bằng chứng thực nghiệm |
| Tiêu chí hoàn thành: giải thích đúng lý do `super()` phải đứng đầu | Vẫn chỉ 1 phần, chưa nêu rõ "thứ tự khởi tạo cha trước con" |

## Tổng kết lần 2
So với lần 1, bài đã sửa đúng phần `toString()` và có thêm 1 constructor biết hardcode `wheels = 4`. Nhưng lỗi quan trọng nhất — **hoàn toàn chưa có constructor nào dùng `this(...)`** — vẫn y nguyên sau 2 lượt sửa, và constructor "đầy đủ tham số" vẫn cho phép tạo ra "Car" với số bánh tùy ý (kể cả `main()` mới còn minh chứng rõ điều này bằng ví dụ Car 6 bánh). Vì đây là bài học trọng tâm về **constructor chaining bằng `this()`**, cần sửa lại đúng cấu trúc: constructor 2-tham-số (`brand`, `doors`) là bản "đầy đủ" gọi `super(brand, 4)`; còn constructor 1-tham-số (`brand` mà thôi) gọi `this(brand, 4)` để tận dụng lại constructor kia — đó mới là "chaining" thực sự mà đề bài muốn luyện.

---

## Đánh giá lần 1 (trước khi sửa — lưu lại để đối chiếu)

### Kết luận nhanh

Code **biên dịch và chạy được**, đúng cú pháp `super(...)` là dòng đầu tiên trong constructor của `Car`. Tuy nhiên code mới chỉ làm đúng **1/4 yêu cầu** của đề bài: thiếu hẳn constructor thứ 2 dùng `this()` (chính là trọng tâm "constructor chaining" mà đề bài muốn luyện), constructor còn lại không hardcode đúng như đề bài yêu cầu, dùng sai tên method (`toText()` thay vì `toString()`), và chưa thực hiện thí nghiệm "cố tình đặt `super()` sai vị trí" mà đề bài yêu cầu.

---

## Lỗi nghiêm trọng theo yêu cầu đề bài

### 1. Thiếu hoàn toàn constructor dùng `this()` — đây là trọng tâm của cả bài tập
Đề bài yêu cầu `Car` phải có **2 constructor**:
- 1 constructor đầy đủ tham số gọi `super(brand, 4)`.
- 1 constructor **chỉ nhận `brand`**, gọi `this(brand, 4)`.

Code hiện tại chỉ có **đúng 1 constructor**:
```java
Car(int doors, String brand, int wheels) {
    super(brand, wheels);
    this.doors = doors;
}
```
Không có bất kỳ constructor nào gọi `this(...)`. Vì tên bài là "Constructor chaining với `this()` và `super()`", việc thiếu hẳn nhánh `this()` khiến bài tập mới chỉ luyện được 1 nửa kỹ thuật cần học.

**Cần bổ sung**:
```java
Car(String brand) {
    this(brand, 4); // gọi lại constructor kia, mặc định 4 cửa
}
```
(Lưu ý: để gọi được `this(brand, 4)` theo đúng tinh thần đề bài, constructor 3-tham-số nên đổi thứ tự thành `Car(String brand, int doors)` — xem mục 2 bên dưới.)

### 2. Constructor hiện có không hardcode `wheels = 4` như đề bài yêu cầu
Đề bài yêu cầu tường minh: *"1 constructor đầy đủ tham số gọi `super(brand, 4)` (xe hơi luôn 4 bánh)"* — tức là `wheels` phải là hằng số `4` viết chết trong lệnh gọi `super`, không phải tham số truyền vào.

Code hiện tại:
```java
Car(int doors, String brand, int wheels) {
    super(brand, wheels); // wheels vẫn là tham số tự do, không hardcode 4
    this.doors = doors;
}
```
Ở đây `wheels` vẫn nhận giá trị tùy ý từ caller (`new Car(4, "Lexus", 4)` truyền `wheels=4` thủ công) — không có gì ép buộc "xe hơi luôn 4 bánh" cả. Nếu gọi `new Car(4, "Lexus", 6)`, code vẫn chạy bình thường và tạo ra 1 chiếc "Car" 6 bánh — đi ngược lại chính giả định mà đề bài muốn minh họa.

**Sửa đúng theo đề bài**:
```java
Car(String brand, int doors) {
    super(brand, 4); // hardcode 4 bánh, không nhận tham số wheels nữa
    this.doors = doors;
}
```

### 3. Dùng `toText()` thay vì `toString()` như đề bài yêu cầu
Đề bài mục 3 yêu cầu rõ: *"Viết `toString()` cho cả 2 class, override ở `Car` để in thêm `doors`."* Code hiện tại dùng tên method tự đặt `toText()`:
```java
void toText() { ... }
```
Về mặt kỹ thuật, đây **vẫn là 1 override hợp lệ** của `Vehicle.toText()` (cùng chữ ký), nên tính đa hình vẫn hoạt động đúng khi gọi `c.toText()` — nhưng nó không phải là `toString()` như đề bài chỉ định. Dùng `toString()` chuẩn của `Object` còn có lợi thế lớn hơn: tự động được gọi khi `System.out.println(car)` hoặc nối chuỗi `"" + car`, trong khi `toText()` tự đặt thì không có đặc quyền này.

**Sửa**: đổi `void toText()` thành `@Override public String toString()`, trả về chuỗi thay vì tự `println` bên trong.

### 4. Chưa thực hiện thí nghiệm "đặt `super()` sai vị trí" theo yêu cầu đề bài
Đề bài mục 4 yêu cầu: *"Thử compile khi cố tình đặt `super(...)` **không phải** dòng đầu tiên trong constructor — quan sát lỗi compiler."* Code hiện tại chỉ có 1 dòng comment khẳng định luật, không có bằng chứng đã thực sự thử nghiệm:
```java
//super phải nằm đầu để khởi tạo và truyền value của Car và class cha là Vehicle
```
Đây là comment nêu **kết luận**, chưa phải là quá trình thực nghiệm (thử di chuyển `super(...)` xuống dưới `this.doors = doors;`, quan sát thông báo lỗi thật của compiler, rồi mới rút ra kết luận). Ngoài ra, giải thích hiện tại ("để khởi tạo và truyền value") còn khá chung chung, chưa chạm đúng vào bản chất mà "Hoàn thành khi" của đề bài yêu cầu: **thứ tự khởi tạo object cha phải hoàn tất trước khi object con bắt đầu khởi tạo phần của riêng nó** — nếu cho phép chạy code khác trước `super()`, đoạn code đó có thể vô tình dùng tới field kế thừa từ cha khi field đó **chưa tồn tại**, dẫn tới trạng thái không nhất quán.

---

## Vấn đề nhỏ / góp ý style

### 5. `@Override` bị comment out
```java
// @Override
void toText() {
```
Dòng `@Override` đang bị comment, mất tác dụng kiểm tra của compiler. Bài học gốc ([01-huong-doi-tuong-oop.md](01-huong-doi-tuong-oop.md) mục 3) khuyến nghị **luôn** dùng `@Override` khi override — nếu lỡ gõ sai tên/chữ ký method, có `@Override` sẽ báo lỗi compile ngay, còn không có thì Java âm thầm coi đó là 1 method overload mới, rất khó phát hiện bug.

### 6. Thiếu khoảng trắng trong chuỗi in ra
```java
System.out.println("The vehicle is " + this.brand + " brand has " + this.wheels + "wheels");
// in ra: "... has 4wheels" — dính liền, thiếu dấu cách trước "wheels"

System.out.println("This car is " + this.brand + " brand has " + this.doors + " doors," + this.wheels + " wheels");
// in ra: "... 4 doors,4 wheels" — thiếu dấu cách sau dấu phẩy
```
Không ảnh hưởng tới logic OOP đang học, nhưng nên sửa cho output dễ đọc.

### 7. Điểm làm tốt: `main()` thể hiện đúng runtime polymorphism (dù ngoài phạm vi đề bài)
```java
Vehicle c = new Car(4, "Lexus", 4);
c.toText();
```
Khai báo biến kiểu `Vehicle` nhưng object thực tế là `Car`, và lời gọi `c.toText()` đúng là dispatch tới `Car.toText()` lúc runtime — đây là minh chứng tốt cho tính đa hình (dù đây là chủ đề của Bài 5, không bị đề bài Bài 2 yêu cầu, nhưng là 1 điểm cộng cho thấy đã hiểu cơ chế).

---

## Bảng đối chiếu tiêu chí đề bài

| Yêu cầu đề bài | Trạng thái |
|---|---|
| 1. `Vehicle` có field `brand`, `wheels` + 1 constructor đầy đủ tham số | Đạt |
| 2a. `Car` thêm field `doors` | Đạt |
| 2b. Constructor đầy đủ tham số của `Car` gọi `super(brand, 4)` (hardcode 4) | Không đạt — vẫn nhận `wheels` làm tham số tự do, không hardcode |
| 2c. Constructor chỉ nhận `brand`, gọi `this(brand, 4)` | Không đạt — hoàn toàn chưa có constructor này |
| 3. `toString()` cho cả 2 class, override ở `Car` | Không đạt — dùng tên tự đặt `toText()` thay vì `toString()` |
| 4. Thử nghiệm đặt `super()` sai vị trí, quan sát lỗi compiler | Không đạt — chỉ có 1 dòng comment khẳng định luật, chưa có bằng chứng đã thực nghiệm |
| Tiêu chí hoàn thành: giải thích đúng lý do `super()` phải đứng đầu | Một phần — có giải thích nhưng còn sơ sài, chưa nêu rõ "thứ tự khởi tạo cha trước con" |

## Tổng kết
Code hiện tại chạy được nhưng mới thỏa **1/4 yêu cầu chính** của Bài 2 (field + constructor cơ bản của `Vehicle`). Phần quan trọng nhất của bài — constructor chaining bằng `this()` — hoàn toàn chưa được viết, khiến bài tập chưa thực sự luyện đúng kỹ năng mà đề bài nhắm tới. Cần bổ sung: (1) constructor `Car(String brand)` gọi `this(brand, 4)`, (2) sửa constructor còn lại để hardcode `wheels = 4` qua `super(brand, 4)`, (3) đổi `toText()` thành `toString()` chuẩn, và (4) thực sự thử di chuyển `super()` xuống dưới để quan sát lỗi compiler thật, từ đó viết lại phần giải thích cho đầy đủ.

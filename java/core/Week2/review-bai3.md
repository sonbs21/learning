# Review: Bài 3 — `equals()`/`hashCode()` đúng chuẩn với `Employee`

File được đánh giá: [bai3.java](bai3.java)
Đối chiếu với: [02-oop-bai-tap.md](02-oop-bai-tap.md) — Bài 3

---

## Đánh giá lần 2 (sau khi sửa)

### Đã sửa đúng — lỗi lõi đã được giải quyết
```java
@Override
public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof Employee)) return false;
    Employee other = (Employee) obj;
    return this.id == other.id;
}
```
`equals()` giờ đã so sánh đúng theo `id`, không còn ủy quyền cho `Object.equals()` nữa. Với `hashCode()` đã đúng sẵn từ lần 1, `main()` hiện tại sẽ in ra đúng **`2`** (thay vì `3` như lần 1) — `e2` và `e3` (cùng `id=2`) được `HashSet` nhận diện là trùng lặp, đúng mục tiêu cốt lõi của bài học.

### Thí nghiệm phản chứng (mục 5) — đã thử, nhưng giải thích còn sai bản chất
Đã thêm comment mô tả thí nghiệm:
```
Khi comment hashCode(), Employee hoàn toàn không check id nên
kết quả ban đầu đang là 2 thành 3
```
**Kết luận về con số là ĐÚNG** — nếu comment `hashCode()` đi, `size()` thực sự sẽ trở lại `3`. Tuy nhiên **cách giải thích lý do lại sai bản chất**, và đây chính xác là điều "Hoàn thành khi" của đề bài đòi hỏi phải nêu **chính xác**.

**Điểm sai trong giải thích**: câu "Employee hoàn toàn không check id" là **không đúng** — `equals()` vẫn giữ nguyên logic so sánh `id`, không hề bị ảnh hưởng bởi việc comment `hashCode()`. Vấn đề **không nằm ở `equals()`**.

**Lý do thật sự**:
1. `HashSet` dùng `hashCode()` để xác định **bucket** (ngăn chứa) cho mỗi phần tử trước.
2. `equals()` **chỉ được gọi để so sánh giữa các phần tử nằm chung 1 bucket** — đây là cơ chế tối ưu, không phải so sánh với toàn bộ tập hợp.
3. Khi không override `hashCode()`, `Employee` dùng lại `Object.hashCode()` mặc định — dựa trên **identity** (gần như luôn khác nhau giữa 2 object, bất kể `id` có giống nhau hay không).
4. Vì vậy `e2` và `e3` (cùng `id=2`) rơi vào **2 bucket khác nhau**.
5. `HashSet` không bao giờ có lý do để đem `e2` ra so sánh với `e3` bằng `equals()` (vì "chúng ở 2 ngăn khác nhau") — nên dù `equals()` vẫn đúng, nó **không bao giờ được gọi** giữa 2 object này.

Nói ngắn gọn: `equals()` quyết định "2 object có bằng nhau không", còn `hashCode()` quyết định "2 object có bao giờ được **đem ra so sánh** bằng `equals()` hay không". Thiếu `hashCode()` đúng, `equals()` dù viết đúng đến đâu cũng không có cơ hội chạy giữa các object liên quan.

### Điểm trừ nhỏ về mặt phương pháp
Thí nghiệm hiện chỉ tồn tại dưới dạng **comment mô tả dự đoán**, chưa có bằng chứng đã thực sự comment `hashCode()` và chạy lại để tự mắt xác nhận `size()` = 3 (dù con số đưa ra là đúng). Nên có 1 đoạn code thực sự chạy được cho cả 2 kịch bản (hoặc ít nhất ghi rõ "đã chạy thử và xác nhận") để chứng minh bằng thực nghiệm, không chỉ suy đoán.

### Bảng đối chiếu (lần 2)

| Yêu cầu đề bài | Trạng thái |
|---|---|
| 2. `equals()` coi 2 `Employee` bằng nhau khi cùng `id` | **Đạt** (đã sửa đúng) |
| 4. `HashSet` in đúng `size() = 2` | **Đạt** |
| 5. Thử nghiệm phản chứng, quan sát `size()` đổi | Có thử, con số đúng, nhưng **giải thích sai bản chất** ("equals() không check id" là sai) |
| Tiêu chí hoàn thành: giải thích chính xác vì sao thiếu `hashCode()` khiến `HashSet` không nhận diện đúng | **Chưa đạt đầy đủ** — cần sửa lại đúng thành "do khác bucket nên `equals()` không được gọi", không phải "equals() không check id" |

### Tổng kết lần 2
Lỗi lõi của lần 1 (`equals()` không thực sự so sánh `id`) đã được sửa hoàn toàn đúng — `main()` giờ in ra đúng kết quả mong đợi. Điểm còn lại cần chỉnh là **cách diễn giải** thí nghiệm phản chứng: con số dự đoán (`2` → `3`) đúng, nhưng lý do đưa ra ("không check id") sai bản chất cơ chế — nguyên nhân thực sự là `hashCode()` mặc định khiến 2 object rơi vào khác bucket nên `equals()` không bao giờ được gọi tới, chứ không phải `equals()` "hỏng". Xem đáp án tham khảo bên dưới để có bản giải thích đầy đủ, chính xác.

---

## Đáp án tham khảo (bản đúng đầy đủ theo yêu cầu đề bài)

```java
import java.util.HashSet;
import java.util.Objects;

public class bai3 {
    public static void main(String[] args) {
        HashSet<Employee> setEmp = new HashSet<>();
        Employee e1 = new Employee(1, "Son");
        Employee e2 = new Employee(2, "Nam");
        Employee e3 = new Employee(2, "Trung"); // cùng id=2, khác instance, khác name

        setEmp.add(e1);
        setEmp.add(e2);
        setEmp.add(e3);

        System.out.println("Có equals() + hashCode() đúng chuẩn: size = " + setEmp.size());
        // In ra: 2  (e2 và e3 được coi là trùng vì cùng id)
    }
}

class Employee {
    int id;
    String name;

    public Employee(int id, String name) {
        this.id = id;
        this.name = name;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Employee)) return false;
        Employee other = (Employee) obj;
        return this.id == other.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

/*
 * ============================================================
 * THÍ NGHIỆM PHẢN CHỨNG (mục 5 của đề bài)
 * ============================================================
 * Comment lại hashCode() ở trên (GIỮ NGUYÊN equals()), rồi chạy lại main():
 *
 *     // @Override
 *     // public int hashCode() {
 *     //     return Objects.hash(id);
 *     // }
 *
 * Kết quả thực tế khi chạy: size = 3 (không còn là 2 nữa).
 *
 * GIẢI THÍCH CHÍNH XÁC:
 * KHÔNG PHẢI vì "equals() không còn check id" -- equals() vẫn y nguyên,
 * vẫn so sánh đúng theo id như cũ, không hề bị đụng tới.
 *
 * Lý do thật sự nằm ở CÁCH HashSet TÌM phần tử để so sánh:
 *   1. HashSet dùng hashCode() để xác định BUCKET (ngăn chứa) của mỗi phần tử.
 *   2. equals() CHỈ được gọi giữa các phần tử NẰM CHUNG 1 BUCKET -- đây là
 *      bước tối ưu để tránh phải so sánh equals() với TOÀN BỘ tập hợp.
 *   3. Khi KHÔNG override hashCode(), Employee dùng lại Object.hashCode()
 *      mặc định -- dựa trên identity (địa chỉ bộ nhớ), gần như luôn KHÁC
 *      NHAU giữa 2 object riêng biệt, bất kể id có giống hệt nhau hay không.
 *   4. Vì vậy e2 và e3 (cùng id=2) rơi vào 2 bucket KHÁC NHAU.
 *   5. HashSet không bao giờ có lý do đem e2 ra so sánh với e3 bằng equals()
 *      (vì chúng "ở 2 ngăn khác nhau") -- nên dù equals() vẫn đúng, nó
 *      KHÔNG BAO GIỜ ĐƯỢC GỌI giữa e2 và e3. Cả 2 bị coi là 2 phần tử
 *      độc lập, không trùng lặp.
 *
 * KẾT LUẬN: equals() và hashCode() PHẢI đi cùng nhau, mỗi cái giữ 1 vai trò
 * khác nhau:
 *   - equals()   quyết định "2 object CÓ bằng nhau hay không".
 *   - hashCode() quyết định "2 object CÓ BAO GIỜ được đem ra so sánh bằng
 *                 equals() hay không" (qua việc có rơi vào cùng bucket không).
 * Thiếu 1 trong 2, HashSet đều hoạt động sai -- nhưng sai theo 2 CƠ CHẾ
 * hoàn toàn khác nhau, không phải cùng 1 lý do.
 * ============================================================
 */
```

---

## Kết luận nhanh (đánh giá lần 1)

Code **biên dịch và chạy được**, nhưng cho ra **kết quả sai** so với mục tiêu bài học vì `equals()` chỉ "giả vờ" được override — bên trong nó gọi thẳng `super.equals(obj)` nên hành vi thực tế **không hề thay đổi** so với `Object.equals()` mặc định (so sánh theo địa chỉ tham chiếu). Đây là lỗi nghiêm trọng nhất, khiến `HashSet` không loại bỏ được 2 `Employee` trùng `id` như đề bài kỳ vọng. Ngoài ra, thí nghiệm phản chứng ở mục 5 (comment `hashCode()`) hoàn toàn chưa được thực hiện.

---

## Lỗi nghiêm trọng

### 1. `equals()` không thực sự so sánh theo `id` — chỉ ủy quyền cho `Object.equals()`
```java
@Override
public boolean equals(Object obj) {
    return super.equals(obj);
}
```
`Employee` không override `equals()`, mà kế thừa toàn bộ hành vi từ `Object`, vốn chỉ so sánh **địa chỉ tham chiếu** (`this == obj`). Dù có annotation `@Override` và tên method đúng, đây **không phải** là override có ý nghĩa — nó tương đương hệt như không viết `equals()` gì cả. Yêu cầu mục 2 của đề bài ("2 `Employee` bằng nhau khi cùng `id`") hoàn toàn chưa được cài đặt.

**Hậu quả cụ thể khi chạy `main()` hiện tại**:
```java
Employee e2 = new Employee(2, "Nam");
Employee e3 = new Employee(2, "Trung");   // cùng id = 2, khác instance
setEmp.add(e2);
setEmp.add(e3);
```
Vì `hashCode()` đã đúng (dựa trên `id`), `e2` và `e3` rơi vào **cùng 1 bucket** trong `HashSet`. Nhưng khi `HashSet` gọi `equals()` để kiểm tra trùng lặp trong bucket đó, `e2.equals(e3)` trả về `false` (2 object khác nhau về địa chỉ) — nên `e3` vẫn được thêm vào bình thường.

**Kết quả in ra thực tế**: `setEmp.size()` in ra **`3`**, trong khi mục tiêu của đề bài (2 `Employee` cùng `id` phải được coi là trùng) đòi hỏi kết quả phải là **`2`**. Đây chính là lỗi lõi khiến bài tập chưa đạt được mục đích chính: minh chứng `HashSet` nhận diện đúng object "bằng nhau" theo `equals()`.

**Sửa đúng**:
```java
@Override
public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof Employee)) return false;
    Employee other = (Employee) obj;
    return this.id == other.id;
}
```

### 2. Thiếu hoàn toàn thí nghiệm phản chứng (mục 5 của đề bài)
Đề bài yêu cầu: *"comment lại `hashCode()` (chỉ giữ `equals()`), lặp lại bước 4 — quan sát `set.size()` thay đổi thế nào và giải thích tại sao."* Code hiện tại không có bất kỳ đoạn nào thực hiện thí nghiệm này — không có 2 kịch bản so sánh (có `hashCode()` vs không có `hashCode()`), cũng không có giải thích kèm theo. Đây là phần quan trọng để hiểu **tại sao cả `equals()` lẫn `hashCode()` đều bắt buộc phải override cùng nhau**, và tiêu chí "Hoàn thành khi" của đề bài yêu cầu chính xác điều này.

---

## Điểm đã làm đúng

- `hashCode()` cài đặt đúng, dựa trên `id` qua `Objects.hash(id)` — đúng yêu cầu mục 3.
- Cấu trúc `main()` đúng ý đồ: tạo 3 `Employee`, trong đó 2 object khác instance nhưng cùng `id` (`e2` và `e3` cùng `id=2`) — đúng yêu cầu mục 4 về mặt dữ liệu test, chỉ là kết quả in ra sai do lỗi `equals()` ở trên.
- Constructor gán đúng field, không có lỗi cú pháp nào khác.

---

## Vấn đề nhỏ / góp ý style

### 3. Field `id`, `name` không có access modifier (mặc định package-private)
Không bắt buộc với đề bài này (Bài 3 không yêu cầu encapsulation như Bài 1), nhưng theo thói quen tốt nên khai báo `private` cho field và cung cấp getter nếu cần truy cập từ ngoài — nhất quán với nguyên tắc encapsulation đã học ở mục 1 bài học lý thuyết.

### 4. Có 2 dòng trống thừa trong class `Employee` (dòng 22, 39)
Không ảnh hưởng chức năng, chỉ là style nhỏ.

### 5. Chưa có `toString()`
Không bắt buộc, nhưng nếu có sẽ giúp debug dễ hơn khi in trực tiếp nội dung `HashSet<Employee>` để kiểm tra bằng mắt phần tử nào còn lại sau khi loại trùng.

---

## Bảng đối chiếu tiêu chí đề bài

| Yêu cầu đề bài | Trạng thái |
|---|---|
| 1. Field `int id`, `String name` | Đạt |
| 2. `equals()` coi 2 `Employee` bằng nhau khi cùng `id` | **Không đạt** — chỉ ủy quyền `super.equals()`, vẫn so sánh theo địa chỉ tham chiếu |
| 3. `hashCode()` dựa trên `id` qua `Objects.hash()` | Đạt |
| 4. Tạo `HashSet`, thêm 3 `Employee` (2 object cùng `id`), in `size()` | Đạt về cấu trúc test, nhưng **kết quả in ra sai** (`3` thay vì `2`) do lỗi ở mục 2 |
| 5. Thử nghiệm phản chứng: comment `hashCode()`, quan sát lại `size()` | **Không đạt** — hoàn toàn chưa thực hiện |
| Tiêu chí hoàn thành: chỉ ra chính xác vì sao thiếu `hashCode()` khiến `HashSet` không nhận diện đúng | Chưa đạt — chưa có thí nghiệm lẫn giải thích |

---

## Tổng kết
Lỗi nghiêm trọng nhất là `equals()` chỉ "làm cảnh" bằng cách gọi `super.equals(obj)` — về bản chất không khác gì không override, khiến `HashSet` không thể nhận diện 2 `Employee` cùng `id` là "trùng nhau", dẫn đến `setEmp.size()` in ra `3` thay vì `2` như mục tiêu đề bài. Cần viết lại `equals()` để thực sự so sánh theo `id` (dùng `instanceof` + so sánh field, hoặc `Objects.equals()`). Sau khi sửa xong phần này, cần bổ sung thêm phần thí nghiệm phản chứng ở mục 5 — thử comment `hashCode()` đi để tự mắt quan sát `size()` quay lại `3` dù `equals()` đã đúng, từ đó rút ra kết luận: `HashSet` cần **cả 2** method `equals()` và `hashCode()` phối hợp đúng thì mới hoạt động chính xác — thiếu 1 trong 2 đều gây lỗi nhận diện trùng lặp, dù theo 2 cơ chế khác nhau.

# Review: Bài 6 — Enum nâng cao — máy tính `Operation` với constant-specific method

File được đánh giá: [bai6.java](bai6.java)
Đối chiếu với: [02-oop-bai-tap.md](02-oop-bai-tap.md) — Bài 6

## Kết luận nhanh

Đây là bài làm **tốt nhất trong số các bài đã review** — cả 4 yêu cầu chính của đề bài đều được cài đặt đúng và đầy đủ: mở rộng `TIMES`/`DIVIDE`, `DIVIDE` tự kiểm tra chia 0 và ném `ArithmeticException`, thêm field `symbol` + constructor + `toString()`, và vòng lặp in bảng đúng định dạng. Chỉ còn vài điểm nhỏ có thể hoàn thiện thêm, không có lỗi nghiêm trọng nào.

---

## Đối chiếu từng yêu cầu

### 1. Mở rộng enum thêm `TIMES`, `DIVIDE` — Đạt
```java
TIMES("*") { public double apply(double a, double b) { return a * b; } },
DIVIDE("/") { ... }
```
Cả 4 hằng số (`PLUS`, `MINUS`, `TIMES`, `DIVIDE`) đều tự cài `apply()` riêng theo đúng kiểu "constant-specific method" — không có `switch/if` nào phân biệt loại phép toán ở đâu cả. Đúng tinh thần bài học.

> Lưu ý nhỏ: đề bài ghi rõ chữ ký `apply(int a, int b)`, code hiện dùng `apply(double a, double b)`. Đây không phải lỗi — thực ra là lựa chọn hợp lý hơn vì tránh phép chia nguyên bị làm tròn (`3/4` với `int` sẽ ra `0`, còn với `double` ra đúng `0.75`). Đánh đổi là output có thêm đuôi `.0` (VD `7.0` thay vì `7` như ví dụ mẫu trong đề bài) — nếu muốn khớp chính xác định dạng ví dụ, cần đổi lại `int`.

### 2. `DIVIDE` tự ném `ArithmeticException` khi chia 0 — Đạt
```java
if (b == 0)
    throw new ArithmeticException("Not divided by 0");
```
Đúng yêu cầu, và đặc biệt quan trọng vì code dùng `double`: khác với `int` (Java tự ném lỗi khi chia nguyên cho 0), phép `double / 0.0` sẽ **không** tự ném exception (chỉ trả về `Infinity`/`NaN` âm thầm) — nên việc tự kiểm tra thủ công ở đây là bắt buộc, không phải tùy chọn, và bạn đã làm đúng.

**Điểm có thể cải thiện**: `main()` hiện tại chỉ gọi `op.apply(3, 4)` cho mọi phép toán — chưa từng gọi `DIVIDE.apply(x, 0)` để tự mắt nhìn thấy exception thực sự được ném ra. Nên thêm 1 đoạn test riêng, ví dụ:
```java
try {
    Operation.DIVIDE.apply(5, 0);
} catch (ArithmeticException e) {
    System.out.println("Bắt được lỗi: " + e.getMessage());
}
```

### 3. Field `symbol` + constructor + `toString()` — Đạt
```java
private final String symbol;

Operation(String symbol) {
    this.symbol = symbol;
}

@Override
public String toString() {
    return symbol;
}
```
Cài đặt đúng chuẩn: mỗi hằng số gọi constructor riêng (`PLUS("+")`, `MINUS("-")`,...), `toString()` override đúng để khi in `op` ra sẽ hiện ký hiệu thay vì tên hằng số. Comment giải thích "constructor của enum luôn private, không cần viết tường minh" cũng chính xác — thể hiện hiểu đúng bản chất.

### 4. Vòng lặp in bảng — Đạt
```java
for (Operation op : Operation.values()) {
    System.out.println(3 + " " + op + " " + 4 + " = " + op.apply(3, 4));
}
```
Chạy đúng, in ra:
```
3 + 4 = 7.0
3 - 4 = -1.0
3 * 4 = 12.0
3 / 4 = 0.75
```
Định dạng đúng ý đồ đề bài (chỉ khác đuôi `.0` do dùng `double`, như đã nêu ở mục 1).

### Tiêu chí hoàn thành: thêm `MOD` không cần sửa code khác — Chưa tự kiểm chứng
Đề bài muốn bạn **thực sự thử thêm** 1 phép toán mới (VD `MOD`) để tự mắt xác nhận không cần sửa gì ở vòng lặp hay `main()`. Code hiện tại chưa có bước này — dù dựa vào cấu trúc hiện tại, việc thêm `MOD("%") { public double apply(double a, double b) { return a % b; } }` chắc chắn sẽ hoạt động ngay mà không cần sửa gì khác (đã kiểm chứng bằng lý thuyết cấu trúc code). Nên tự tay thêm vào để có bằng chứng thực nghiệm, không chỉ suy luận lý thuyết.

---

## Điểm đã làm rất tốt (so với các bài trước đó)

- Đã sửa hết lỗi chính tả từ bản nháp trước (`Operatin` → `Operation`, `DIVIED` → `DIVIDE`).
- Đã xóa `class Operation {}` rỗng, thừa từ bản nháp trước.
- `MOD` ở bản nháp trước tính nhầm thành phép nhân — đã được loại bỏ hoàn toàn khỏi bản này (không còn nhầm lẫn).
- Toàn bộ 4 yêu cầu chính của đề bài đều đã được cài đặt đúng, không thiếu phần nào lớn.

---

## Bảng đối chiếu tiêu chí đề bài

| Yêu cầu đề bài | Trạng thái |
|---|---|
| 1. Mở rộng `TIMES`, `DIVIDE`, mỗi constant tự cài `apply()` | Đạt (khác kiểu tham số `double` thay vì `int` — không sai, chỉ là lựa chọn khác) |
| 2. `DIVIDE` tự ném `ArithmeticException` khi chia 0 | Đạt (chưa có test thực nghiệm gọi thử trong `main()`) |
| 3. Field `symbol` + constructor + `toString()` | Đạt đầy đủ |
| 4. Vòng lặp in bảng | Đạt đầy đủ |
| Tiêu chí hoàn thành: tự thêm `MOD` để kiểm chứng | Chưa thực hiện — nhưng cấu trúc code hiện tại đủ điều kiện để làm ngay mà không cần sửa gì |

---

## Tổng kết
Đây là bài làm hoàn thiện nhất trong loạt bài đã review — không có lỗi nghiêm trọng, hiểu đúng và cài đặt đúng bản chất "constant-specific method" của enum. Các điểm còn lại chỉ mang tính hoàn thiện thêm: (1) cân nhắc đổi lại `int` nếu muốn khớp chính xác định dạng ví dụ đề bài, (2) thêm đoạn test thực sự gọi `DIVIDE.apply(x, 0)` để tận mắt thấy exception được ném, và (3) tự tay thêm 1 phép toán mới (`MOD`) vào enum để tự kiểm chứng tiêu chí "Hoàn thành khi" bằng thực nghiệm thay vì chỉ suy luận.

# Review: Bài 1 — Encapsulation với `BankAccount`

File được đánh giá: [bai1.java](bai1.java)
Đối chiếu với: [02-oop-bai-tap.md](02-oop-bai-tap.md) — Bài 1

---

## Đánh giá lần 2 (sau khi sửa)

### Đã sửa đúng so với lần 1
1. **Exception type**: đã đổi đúng thành `IllegalArgumentException` — không còn lỗi compile do checked exception.
2. **Field `owner`**: đã được khởi tạo (`= "Pionero"`) — không còn lỗi "definite assignment".
3. **Thứ tự class trong file**: `bai1` (chứa `main()`) giờ đứng trước `Bank` — chạy đúng bằng single-file launch (`java bai1.java`), đúng như đã giải thích ở lượt trao đổi trước.
4. **Đã thêm `try/catch`** quanh các lời gọi trong `main()` — chương trình không còn crash với stack trace khi gặp exception.

Nhờ các sửa trên, code giờ **biên dịch và chạy được**. Tuy nhiên khi chạy thử thực tế (truy vết từng dòng), vẫn còn 1 lỗi cốt lõi **chưa được đụng tới** từ lần 1, và phát sinh 1 vấn đề mới do cách đặt `try/catch`.

### Lỗi nghiêm trọng còn tồn tại

#### 1. [CHƯA SỬA] `balance` vẫn không bao giờ được cập nhật thật
```java
public double deposit(double amount) {
    if (amount <= 0) throw new IllegalArgumentException(...);
    return balance + amount;   // vẫn chỉ TRẢ VỀ, không gán this.balance
}
```
Đây là lỗi quan trọng nhất từ lần review đầu và **vẫn y nguyên** — chưa được sửa. `balance` mãi mãi là `0` bất kể gọi `deposit()` bao nhiêu lần. Đây vẫn là gốc rễ khiến bài tập chưa đạt mục tiêu "bảo vệ invariant qua encapsulation".

**Sửa**: `this.balance += amount;` trong `deposit()`, `this.balance -= amount;` trong `withdraw()`, trước dòng `return`.

#### 2. [VẤN ĐỀ MỚI] `try/catch` bọc chung cả 5 lời gọi → dừng ngay sau exception đầu tiên
Truy vết thực tế khi chạy `main()` hiện tại:
```
b.deposit(10)   -> return 0 + 10 = 10.0   -> in "1:10.0"
b.deposit(-2)   -> ném IllegalArgumentException -> nhảy thẳng vào catch, in exception, main() KẾT THÚC
b.withdraw(3)   -> KHÔNG BAO GIỜ CHẠY
b.withdraw(-3)  -> KHÔNG BAO GIỜ CHẠY
b.withdraw(20)  -> KHÔNG BAO GIỜ CHẠY
```
Vì cả 5 lời gọi nằm chung 1 khối `try`, ngay khi dòng thứ 2 ném exception, toàn bộ phần còn lại của `try` bị bỏ qua và nhảy thẳng vào `catch` — chương trình chỉ thực sự chạy **2/5 dòng test**, dù về mặt code cả 5 dòng đều "có mặt". Đây là điểm dễ gây nhầm lẫn nguy hiểm hơn cả lần 1: trước đây chương trình crash rõ ràng (dễ nhận ra sai), còn giờ nó chạy "êm" và chỉ in ra 2 dòng — dễ khiến người viết tưởng nhầm là đã test đủ.

**Sửa**: bọc **từng lời gọi** trong `try/catch` riêng (hoặc viết 1 helper method `runCase(String label, Runnable action)` để không lặp code), để 1 exception ở dòng này không chặn các dòng test khác chạy tiếp.

#### 3. [CHƯA SỬA] Vẫn thiếu kịch bản "rút đúng số dư còn lại"
Do lỗi #2, ngay cả 2 dòng `withdraw` hiện có cũng chưa từng chạy tới. Đề bài yêu cầu rõ 3 kịch bản (nạp âm, rút quá số dư, **rút đúng số dư còn lại**) — hiện chưa kịch bản `withdraw` nào được thực thi thật sự, nói gì đến đủ 3 loại.

### Vấn đề nhỏ / góp ý style

#### 4. `owner` bị hardcode cứng trong field initializer
`private final String owner = "Pionero";` khiến **mọi** object `Bank` đều có cùng 1 chủ tài khoản — không có cách nào tạo tài khoản cho người khác. Nên truyền `owner` qua constructor: `Bank(String owner) { this.owner = owner; }` để field này thực sự có ý nghĩa (đề bài không bắt buộc cách này, nhưng đây là cách tự nhiên hơn cho 1 field đại diện "chủ sở hữu").

#### 5. Message exception thiếu khoảng trắng và sai ngữ pháp
`owner + "don't enough money"` → in ra `"Pionerodon't enough money"` (dính liền, không dấu cách) và câu tiếng Anh không đúng ngữ pháp ("don't" nên là "doesn't", và cấu trúc câu nên là "doesn't have enough money"). Nên sửa: `owner + " doesn't have enough money"`.

#### 6. Tên class/naming chưa đổi (như lần 1)
- Class `Bank` vẫn chưa đổi thành `BankAccount` theo đúng tên đề bài yêu cầu.
- Public class `bai1` vẫn viết thường, không theo convention `PascalCase`.

---

## Bảng đối chiếu tiêu chí đề bài (lần 2)

| Yêu cầu đề bài | Trạng thái |
|---|---|
| 1. Field `private double balance`, `private final String owner` | Biên dịch OK, nhưng `owner` hardcode cứng "Pionero" thay vì qua constructor |
| 2. `deposit()` ném `IllegalArgumentException` khi `amount <= 0` | Đúng loại exception, nhưng **không cập nhật `balance`** |
| 3. `withdraw()` ném đúng 2 loại exception | Đúng loại + đúng điều kiện, nhưng **không cập nhật `balance`** |
| 4. Không có `setBalance()` | Đúng, không có |
| 5. `main()` test đủ 3 kịch bản + bắt exception + in thông báo | Có `try/catch`, nhưng bọc chung 1 khối → chỉ chạy được 2/5 dòng, chưa test được kịch bản nào của `withdraw` |
| Tiêu chí hoàn thành: không thể làm `balance` âm/set tuỳ ý | Vẫn "đạt" chỉ vì `balance` chưa từng đổi — chưa phải vì logic đúng |

## Tổng kết lần 2
Code giờ **biên dịch và chạy được** (khác biệt lớn so với lần 1), nhưng lỗi lõi quan trọng nhất — `deposit()`/`withdraw()` không gán lại `this.balance` — **vẫn y nguyên chưa sửa**. Thêm vào đó, cách đặt `try/catch` bọc chung cả 5 lời gọi khiến chương trình dừng ngay sau exception đầu tiên, nên trên thực tế chỉ 2/5 dòng test từng được thực thi — vấn đề này còn khó nhận ra hơn lần 1 vì chương trình không còn crash ồn ào. Cần sửa 2 điểm: (1) gán lại `balance` sau mỗi giao dịch, (2) tách `try/catch` riêng cho từng lời gọi test, trước khi bài tập có thể coi là đạt yêu cầu.

---

## Đánh giá lần 1 (trước khi sửa — lưu lại để đối chiếu)

### Kết luận nhanh

Code hiện tại **không biên dịch được** (2 lỗi compile) và có 1 lỗi logic nghiêm trọng khiến mục tiêu chính của bài tập — bảo vệ invariant `balance` bằng encapsulation — không thực sự được kiểm chứng, kể cả sau khi sửa lỗi biên dịch.

---

## Lỗi nghiêm trọng (chặn biên dịch / phá vỡ mục tiêu bài học)

### 1. Dùng sai loại exception — gây lỗi biên dịch
`IllegalAccessException` (dòng 12, 19) là **checked exception** (`extends ReflectiveOperationException extends Exception`), khác hẳn `IllegalArgumentException` (unchecked, `extends RuntimeException`) mà đề bài yêu cầu ở mục 2 và 3.

Vì `deposit()`/`withdraw()` không khai báo `throws IllegalAccessException` và cũng không bắt nó, trình biên dịch sẽ báo lỗi:
```
error: unreported exception IllegalAccessException; must be caught or declared to be thrown
```
**Sửa**: đổi cả 2 chỗ thành `throw new IllegalArgumentException("Amount must be positive");`.

### 2. Field `final String owner` không bao giờ được gán giá trị — gây lỗi biên dịch
`Bank` không có constructor nào. Trình biên dịch tự sinh constructor mặc định, nhưng constructor đó **không gán** `owner` — vi phạm quy tắc "definite assignment" bắt buộc với field `final`:
```
error: variable owner not initialized in the default constructor
```
**Sửa**: thêm constructor nhận `owner`, ví dụ `Bank(String owner) { this.owner = owner; }`.

### 3. `deposit()`/`withdraw()` không thực sự cập nhật `balance` — lỗi logic nghiêm trọng nhất
```java
public double deposit(double amount) {
    if (amount <= 0) throw ...;
    return balance + amount;   // chỉ TRẢ VỀ giá trị mới, KHÔNG gán lại balance
}
```
`balance` không bao giờ thay đổi — nó mãi mãi là `0`. Đây là lỗi quan trọng nhất vì dù logic validate (`amount <= 0`, `amount > balance`) đúng cú pháp, trạng thái nội bộ mà nó bảo vệ lại không bao giờ tồn tại thật. Cùng lỗi tương tự ở `withdraw()`.

**Sửa**: `this.balance += amount;` trong `deposit()`, `this.balance -= amount;` trong `withdraw()`, trước khi `return`.

> Vì lỗi này, tiêu chí hoàn thành của đề bài ("không có cách nào từ bên ngoài làm `balance` âm") chỉ **đúng một cách tình cờ** — vì `balance` chưa từng thay đổi, không phải vì logic bảo vệ thực sự hoạt động.

---

## Lỗi vừa (không chặn compile nhưng sai yêu cầu đề bài)

### 4. `main()` không bắt exception, không in thông báo
Đề bài yêu cầu (mục 5): "bắt exception và in thông báo rõ ràng cho từng trường hợp". Code hiện tại gọi thẳng các method có thể ném exception mà không có `try/catch`:
```java
b.deposit(10);
b.deposit(-2);   // sẽ ném exception ngay tại đây (nếu sửa đúng thành IllegalArgumentException)
b.withdraw(3);   // KHÔNG BAO GIỜ chạy tới vì chương trình đã dừng ở dòng trên
b.withdraw(-3);
b.withdraw(20);
```
Do thiếu `try/catch`, ngay khi `deposit(-2)` ném exception, `main()` dừng đột ngột — 3 dòng test còn lại không bao giờ thực thi, dù có mặt trong code nguồn.

### 5. Thiếu kịch bản test "rút đúng số dư còn lại"
Đề bài yêu cầu đủ 3 kịch bản: nạp tiền âm, rút quá số dư, **và rút đúng số dư còn lại**. Code hiện tại chỉ có 2/3 kịch bản (thiếu kịch bản rút thành công).

---

## Vấn đề nhỏ / góp ý style

### 6. Tên class không khớp đề bài
Đề bài yêu cầu class tên `BankAccount`, code đặt tên `Bank`. Không sai kỹ thuật nhưng nên đổi cho khớp yêu cầu.

### 7. Tên public class `bai1` không theo convention
Tên class trong Java theo convention nên viết `PascalCase` (VD `Bai1`). Tên viết thường vẫn hợp lệ về cú pháp nhưng không đúng coding convention chuẩn.

### 8. Field `owner` không được dùng ở đâu
`owner` được khai báo nhưng không có getter, không xuất hiện trong bất kỳ logic nào — hiện là dead field, chỉ tồn tại để thỏa yêu cầu khai báo của đề bài mà chưa phục vụ mục đích gì.

---

## Bảng đối chiếu tiêu chí đề bài

| Yêu cầu đề bài | Trạng thái |
|---|---|
| 1. Field `private double balance`, `private final String owner` | Có khai báo, nhưng `owner` không bao giờ gán được (lỗi compile) |
| 2. `deposit()` ném `IllegalArgumentException` khi `amount <= 0` | Sai loại exception (`IllegalAccessException`), gây lỗi compile |
| 3. `withdraw()` ném đúng 2 loại exception | Đúng logic điều kiện, nhưng sai loại exception ở nhánh đầu |
| 4. Không có `setBalance()` | Đúng, không có |
| 5. `main()` test đủ 3 kịch bản + bắt exception + in thông báo | Thiếu `try/catch`, thiếu 1 kịch bản, không in gì |
| Tiêu chí hoàn thành: không thể làm `balance` âm/set tuỳ ý | "Đạt" nhưng chỉ vì `balance` chưa từng đổi, không phải vì logic đúng |

---

## Tổng kết
Code hiện chưa chạy được (2 lỗi compile) và có 1 lỗi logic gốc (`balance` không được cập nhật) khiến mục đích chính của bài — bảo vệ invariant qua encapsulation — chưa thực sự được kiểm chứng. Cần sửa đủ 3 lỗi nghiêm trọng (mục 1-3) trước khi phần `main()`/test case có thể được đánh giá đúng theo tinh thần "Hoàn thành khi" của đề bài.

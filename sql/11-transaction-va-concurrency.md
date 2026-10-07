# Tuần 11: Transaction và concurrency trong PostgreSQL

Mục tiêu của bài học:

- hiểu transaction và bốn tính chất ACID;
- dùng BEGIN, COMMIT, ROLLBACK và SAVEPOINT;
- thiết kế transaction nhiều bước cho nghiệp vụ đặt hàng;
- hiểu MVCC, Read Committed và Repeatable Read ở mức thực hành;
- khóa row bằng SELECT FOR UPDATE;
- nhận biết lost update, deadlock và race condition;
- thiết kế retry và idempotency cho request tạo order hoặc payment.

Một nghiệp vụ như đặt hàng không chỉ là một câu INSERT:

~~~text
Kiểm tra tồn kho
Giữ hoặc trừ tồn kho
Tạo order
Tạo order item
Tạo payment attempt
Commit toàn bộ hoặc rollback toàn bộ
~~~

Nếu một bước lỗi mà các bước trước vẫn được lưu, database có thể chứa order không có item, inventory đã trừ nhưng order thất bại, hoặc payment thành công nhưng ứng dụng trả lỗi. Transaction giải quyết tính nguyên tử của nhóm thao tác; concurrency control giải quyết việc nhiều request cùng làm việc trên dữ liệu.

---

## 1. Transaction là gì?

Transaction là một đơn vị công việc logic gồm một hoặc nhiều câu SQL. Với người dùng bên ngoài, transaction được xem như một thao tác hoàn chỉnh:

- thành công thì mọi thay đổi được commit;
- thất bại thì mọi thay đổi được rollback.

Ví dụ tối giản:

~~~sql
BEGIN;

UPDATE inventory
SET quantity = quantity - 1
WHERE book_id = 10
  AND quantity > 0;

INSERT INTO orders (customer_id, status)
VALUES (7, 'pending');

COMMIT;
~~~

Nếu câu lệnh thứ hai lỗi và application không rollback, transaction có thể bị abort. Vì vậy application phải xử lý lỗi và kết thúc transaction rõ ràng.

---

## 2. ACID

### Atomicity

Toàn bộ transaction thành công hoặc toàn bộ bị hủy.

Trong đặt hàng, không được chỉ trừ inventory mà không tạo order item.

### Consistency

Transaction đưa database từ một trạng thái hợp lệ sang một trạng thái hợp lệ khác, tôn trọng constraint, foreign key và quy tắc nghiệp vụ mà database có thể kiểm tra.

Ví dụ quantity không thể âm nếu có CHECK phù hợp.

### Isolation

Các transaction đồng thời không nhìn thấy thay đổi chưa commit của nhau theo cách phá vỡ quy tắc đã chọn. Mức cô lập càng cao thường có thêm chi phí hoặc retry.

### Durability

Sau COMMIT, thay đổi được database bảo đảm theo cơ chế WAL và recovery của PostgreSQL, kể cả khi process hoặc server gặp sự cố trong giới hạn mà hệ thống có thể phục hồi.

ACID không có nghĩa query luôn đúng nghiệp vụ. Transaction vẫn cần điều kiện WHERE đúng, constraint đúng và authorization ở application.

---

## 3. Vòng đời transaction

### BEGIN

Bắt đầu transaction:

~~~sql
BEGIN;
~~~

Có thể viết:

~~~sql
START TRANSACTION;
~~~

### COMMIT

Xác nhận mọi thay đổi:

~~~sql
COMMIT;
~~~

### ROLLBACK

Hủy các thay đổi chưa commit:

~~~sql
ROLLBACK;
~~~

Một session ở trạng thái transaction sẽ giữ transaction cho tới COMMIT hoặc ROLLBACK. Không để transaction mở lâu hơn cần thiết.

### Autocommit

Trong nhiều client, nếu không viết BEGIN, mỗi statement có thể là một transaction riêng.

~~~sql
UPDATE books SET price = price * 1.1 WHERE id = 10;
~~~

Statement này thường được commit tự động. Hai statement liên tiếp không tự động trở thành một transaction chung.

Khi nhiều bước phải thành công cùng nhau, hãy BEGIN rõ ràng.

---

## 4. SAVEPOINT

SAVEPOINT tạo điểm quay lui trong một transaction:

~~~sql
BEGIN;

INSERT INTO orders (customer_id, status)
VALUES (7, 'pending')
RETURNING id;

SAVEPOINT after_order;

INSERT INTO order_items (order_id, book_id, quantity, unit_price)
VALUES (100, 10, 1, 120000);

ROLLBACK TO SAVEPOINT after_order;

-- Transaction vẫn còn hoạt động
COMMIT;
~~~

ROLLBACK TO SAVEPOINT hủy các thao tác sau savepoint nhưng không hủy toàn bộ transaction.

Có thể giải phóng savepoint:

~~~sql
RELEASE SAVEPOINT after_order;
~~~

Dùng savepoint cho lỗi có thể xử lý cục bộ. Không dùng nó để che lỗi nghiêm trọng hoặc để giữ transaction quá dài.

---

## 5. Transaction đặt hàng an toàn

Giả sử có inventory:

~~~sql
CREATE TABLE inventory (
    book_id bigint PRIMARY KEY REFERENCES books(id),
    quantity integer NOT NULL CHECK (quantity >= 0)
);
~~~

Một transaction cơ bản:

~~~sql
BEGIN;

SELECT quantity
FROM inventory
WHERE book_id = 10
FOR UPDATE;

UPDATE inventory
SET quantity = quantity - 1
WHERE book_id = 10
  AND quantity > 0;

-- Application phải kiểm tra row count của UPDATE.
-- Nếu row count = 0, rollback vì hết hàng.

INSERT INTO orders (customer_id, status)
VALUES (7, 'pending')
RETURNING id;

-- Dùng order id vừa nhận được để tạo item.
INSERT INTO order_items (order_id, book_id, quantity, unit_price)
VALUES (100, 10, 1, 120000);

COMMIT;
~~~

Nhưng query trên chưa hoàn chỉnh nếu inventory có thể hết hàng sau SELECT. Cách tốt hơn là khóa row rồi kiểm tra giá trị, hoặc thực hiện UPDATE có điều kiện và kiểm tra row count.

Mẫu atomic update:

~~~sql
BEGIN;

UPDATE inventory
SET quantity = quantity - 1
WHERE book_id = 10
  AND quantity >= 1
RETURNING book_id, quantity;

-- Nếu không có row trả về: hết hàng, ROLLBACK.
-- Nếu có row: tiếp tục tạo order và item.

COMMIT;
~~~

UPDATE có điều kiện giúp kiểm tra và trừ trong cùng một statement. SELECT FOR UPDATE vẫn hữu ích khi cần đọc nhiều thông tin từ row trước khi quyết định.

---

## 6. Row-level lock

SELECT FOR UPDATE khóa các row được chọn để transaction khác không thể cập nhật hoặc khóa tương thích cho tới khi transaction hiện tại kết thúc.

~~~sql
BEGIN;

SELECT book_id, quantity
FROM inventory
WHERE book_id = 10
FOR UPDATE;

-- Transaction khác muốn UPDATE cùng row sẽ phải chờ.
UPDATE inventory
SET quantity = quantity - 1
WHERE book_id = 10;

COMMIT;
~~~

Các row không được chọn không bị khóa bởi câu lệnh này. Row-level lock không đồng nghĩa khóa toàn bộ table.

Các biến thể thường gặp:

- FOR UPDATE: khóa để cập nhật hoặc xóa.
- FOR NO KEY UPDATE: khóa yếu hơn cho cập nhật không đổi key.
- FOR SHARE: khóa chia sẻ.
- FOR KEY SHARE: bảo vệ key được foreign key tham chiếu.

Ở mức cơ bản, tập trung vào FOR UPDATE và hiểu rằng lock được giữ tới COMMIT hoặc ROLLBACK.

---

## 7. Hai session cùng mua cuốn sách cuối cùng

Giả sử inventory của book 10 là 1. Mở hai psql session: A và B.

### Session A

~~~sql
BEGIN;

SELECT quantity
FROM inventory
WHERE book_id = 10
FOR UPDATE;
~~~

A nhận quantity bằng 1 và giữ lock.

### Session B

~~~sql
BEGIN;

SELECT quantity
FROM inventory
WHERE book_id = 10
FOR UPDATE;
~~~

B sẽ chờ vì A đang giữ lock.

### A tiếp tục

~~~sql
UPDATE inventory
SET quantity = quantity - 1
WHERE book_id = 10
  AND quantity > 0;

COMMIT;
~~~

Sau khi A commit, B tiếp tục và đọc trạng thái mới của row. B thấy quantity bằng 0, không được tạo order thành công.

Đây là mục đích của lock: serialize việc thay đổi cùng một resource thay vì để hai request cùng tin rằng còn hàng.

---

## 8. Atomic update và SELECT FOR UPDATE

Có hai pattern chính.

### Pattern A: UPDATE có điều kiện

~~~sql
UPDATE inventory
SET quantity = quantity - 1
WHERE book_id = 10
  AND quantity >= 1
RETURNING book_id, quantity;
~~~

Ưu điểm:

- ngắn;
- kiểm tra và cập nhật trong một statement;
- application chỉ cần kiểm tra có row trả về hay không.

### Pattern B: khóa rồi đọc

~~~sql
SELECT quantity
FROM inventory
WHERE book_id = 10
FOR UPDATE;
~~~

Sau đó application hoặc SQL kiểm tra quantity rồi mới UPDATE.

Ưu điểm:

- phù hợp khi cần nhiều quyết định dựa trên row;
- có thể lấy thêm thông tin đã khóa.

Nhược điểm:

- transaction dài hơn;
- phải xử lý rõ trường hợp row không tồn tại;
- dễ giữ lock lâu nếu code làm việc khác giữa SELECT và UPDATE.

Chọn pattern đơn giản nhất đáp ứng nghiệp vụ.

---

## 9. Lost update

Lost update xảy ra khi hai transaction cùng đọc một giá trị, cùng tính giá trị mới, và một kết quả ghi đè kết quả còn lại.

Ví dụ quantity ban đầu là 10:

~~~text
A đọc 10
B đọc 10
A ghi 9
B ghi 9
~~~

Kết quả là 9, dù có hai lần mua. Một lần cập nhật đã bị mất.

Không an toàn:

~~~sql
SELECT quantity FROM inventory WHERE book_id = 10;
-- application tính quantity - 1
UPDATE inventory SET quantity = 9 WHERE book_id = 10;
~~~

Cách an toàn bằng conditional update:

~~~sql
UPDATE inventory
SET quantity = quantity - 1
WHERE book_id = 10
  AND quantity > 0
RETURNING quantity;
~~~

Hoặc khóa row:

~~~sql
BEGIN;

SELECT quantity
FROM inventory
WHERE book_id = 10
FOR UPDATE;

UPDATE inventory
SET quantity = quantity - 1
WHERE book_id = 10;

COMMIT;
~~~

Một cách khác là optimistic locking với version:

~~~sql
UPDATE inventory
SET quantity = 9,
    version = version + 1
WHERE book_id = 10
  AND version = 3;
~~~

Nếu row count bằng 0, một transaction khác đã thay đổi row; application cần đọc lại hoặc báo conflict.

---

## 10. MVCC trong PostgreSQL

PostgreSQL dùng MVCC, tức Multi-Version Concurrency Control. Thay vì mọi reader chặn mọi writer, database giữ các phiên bản row và dùng snapshot để quyết định transaction nào nhìn thấy dữ liệu nào.

Hệ quả thực hành:

- đọc thường không chặn ghi theo cách của khóa độc quyền toàn bảng;
- transaction nhìn thấy snapshot theo isolation level;
- UPDATE tạo phiên bản mới của row;
- row cũ cần vacuum dọn dẹp sau khi không còn transaction cần nhìn thấy.

MVCC không loại bỏ mọi lock. UPDATE vẫn cần lock trên row và các thao tác DDL có thể cần lock mạnh. MVCC cũng không tự bảo vệ mọi invariant nghiệp vụ; query cần dùng điều kiện, lock hoặc isolation phù hợp.

---

## 11. Read Committed

Read Committed là mức cô lập mặc định của PostgreSQL.

Đặc điểm thực hành:

- mỗi statement có snapshot riêng;
- statement không thấy dữ liệu chưa commit;
- hai statement trong cùng transaction có thể thấy kết quả khác nhau nếu transaction khác commit ở giữa;
- UPDATE hoặc SELECT FOR UPDATE có thể chờ row lock.

Ví dụ:

~~~sql
BEGIN;

SELECT quantity
FROM inventory
WHERE book_id = 10;

-- Transaction khác commit thay đổi ở đây.

SELECT quantity
FROM inventory
WHERE book_id = 10;

COMMIT;
~~~

Hai SELECT có thể thấy hai giá trị khác nhau vì mỗi statement lấy snapshot riêng.

Read Committed phù hợp với nhiều CRUD và transaction ngắn. Nhưng nếu logic yêu cầu toàn bộ transaction nhìn một snapshot ổn định, cần xem xét Repeatable Read hoặc khóa rõ ràng.

---

## 12. Repeatable Read

Repeatable Read cho transaction một snapshot ổn định hơn:

~~~sql
BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ;

SELECT quantity
FROM inventory
WHERE book_id = 10;

-- Các transaction khác commit thay đổi.
-- Các SELECT tiếp theo vẫn theo snapshot của transaction này.

COMMIT;
~~~

Nếu transaction cố cập nhật một row đã bị transaction khác thay đổi sau khi snapshot được tạo, PostgreSQL có thể báo lỗi serialization failure. Application phải rollback và retry toàn bộ transaction.

Repeatable Read không phải là lý do để giữ transaction lâu. Transaction dài làm tăng thời gian giữ snapshot, giữ lock và gây áp lực vacuum.

---

## 13. Serializable ở mức nhận biết

Serializable cố làm các transaction đồng thời có kết quả tương đương một thứ tự chạy tuần tự.

~~~sql
BEGIN TRANSACTION ISOLATION LEVEL SERIALIZABLE;
-- đọc, kiểm tra, cập nhật
COMMIT;
~~~

Giá phải trả là transaction có thể bị lỗi serialization failure. Application phải:

1. ROLLBACK;
2. chờ ngắn với backoff;
3. chạy lại toàn bộ transaction;
4. giới hạn số lần retry;
5. ghi log đủ context.

Không retry một statement đơn lẻ nếu transaction có nhiều bước. Retry toàn bộ đơn vị nghiệp vụ.

---

## 14. Dirty read, non-repeatable read và phantom

### Dirty read

Một transaction đọc dữ liệu chưa commit của transaction khác. PostgreSQL không cho dirty read ở các isolation level thông thường.

### Non-repeatable read

Một transaction đọc cùng row hai lần và thấy giá trị khác nhau vì transaction khác commit cập nhật ở giữa. Read Committed có thể gặp hiện tượng này giữa hai statement.

### Phantom

Một query lặp lại và thấy thêm hoặc mất row thỏa điều kiện vì transaction khác insert/delete. Mức cô lập cao hơn hoặc lock/predicate strategy phù hợp giúp kiểm soát tùy bài toán.

Không cần ghi nhớ tên anomaly một cách máy móc. Hãy mô tả được:

- transaction nào đọc;
- transaction nào ghi;
- thời điểm commit;
- statement nhìn thấy snapshot nào;
- invariant nào cần bảo vệ.

---

## 15. Deadlock

Deadlock xảy ra khi hai transaction chờ lock của nhau:

~~~text
Transaction A giữ row 1 và chờ row 2
Transaction B giữ row 2 và chờ row 1
~~~

PostgreSQL phát hiện deadlock và hủy một transaction bằng lỗi deadlock detected. Application phải rollback và retry nếu nghiệp vụ cho phép.

### Tạo deadlock về mặt khái niệm

Session A:

~~~sql
BEGIN;

SELECT id
FROM inventory
WHERE book_id = 10
FOR UPDATE;

-- Chưa commit
~~~

Session B:

~~~sql
BEGIN;

SELECT id
FROM inventory
WHERE book_id = 20
FOR UPDATE;

-- Chưa commit
~~~

A cố khóa book 20; B cố khóa book 10. Hai session có thể chờ nhau.

### Phòng tránh

- khóa nhiều row theo cùng một thứ tự, thường tăng dần theo ID;
- giữ transaction ngắn;
- không gọi HTTP hoặc chờ người dùng trong transaction;
- tránh giữ lock trong khi làm việc không liên quan;
- retry lỗi deadlock với giới hạn;
- log transaction type và resource IDs.

Khóa đúng thứ tự là cách quan trọng nhất:

~~~sql
SELECT book_id, quantity
FROM inventory
WHERE book_id IN (10, 20)
ORDER BY book_id
FOR UPDATE;
~~~

---

## 16. Transaction boundary trong application

Transaction nên bắt đầu gần nơi thực hiện nghiệp vụ nhiều bước và kết thúc ngay sau khi dữ liệu nhất quán.

Nên làm:

~~~text
BEGIN
  validate dữ liệu cần thiết
  lock hoặc update các row cần thiết
  tạo order và item
  ghi outbox/event nếu có
COMMIT
gửi response
~~~

Không nên:

~~~text
BEGIN
  gọi payment provider qua network
  chờ người dùng
  xử lý file lớn
  UPDATE database
COMMIT
~~~

Gọi external service trong transaction có thể giữ lock lâu và làm tăng deadlock. Nếu cần phối hợp database với external payment, dùng trạng thái, idempotency key, outbox hoặc saga tùy độ phức tạp.

---

## 17. Payment thất bại sau khi tạo order

Một workflow an toàn không nhất thiết phải giữ database transaction mở trong suốt cuộc gọi payment.

Mẫu đơn giản:

### Transaction 1: tạo order

~~~sql
BEGIN;

INSERT INTO orders (customer_id, status)
VALUES (7, 'pending')
RETURNING id;

INSERT INTO order_items (order_id, book_id, quantity, unit_price)
VALUES (100, 10, 1, 120000);

COMMIT;
~~~

### Gọi payment provider bên ngoài

Application dùng idempotency key và gửi payment request.

### Transaction 2: cập nhật payment result

~~~sql
BEGIN;

INSERT INTO payments (
    order_id,
    provider,
    provider_transaction_id,
    amount,
    status
)
VALUES (100, 'example-pay', 'txn-abc', 120000, 'succeeded');

UPDATE orders
SET status = 'paid'
WHERE id = 100
  AND status = 'pending';

COMMIT;
~~~

Nếu payment thất bại, ghi payment failed và chuyển order sang cancelled hoặc trạng thái retry tùy nghiệp vụ.

Nếu inventory phải được giữ trước payment, cần thêm reservation với expiry thay vì giữ row lock trong lúc gọi provider.

---

## 18. Idempotency

Idempotency bảo đảm retry cùng một request không tạo hiệu ứng nghiệp vụ nhiều lần.

Client gửi idempotency key:

~~~text
create-order-request-8f3...
~~~

Database có thể lưu key:

~~~sql
CREATE TABLE idempotency_keys (
    key text PRIMARY KEY,
    request_hash text NOT NULL,
    response_status integer,
    response_body jsonb,
    created_at timestamptz NOT NULL DEFAULT now()
);
~~~

Khi nhận request:

1. bắt đầu transaction;
2. INSERT key;
3. nếu key đã tồn tại, kiểm tra request_hash;
4. nếu hash khác, từ chối vì cùng key nhưng payload khác;
5. nếu đã có response, trả lại response cũ;
6. nếu chưa xử lý, tạo nghiệp vụ;
7. lưu response và commit.

INSERT chống trùng bằng primary key:

~~~sql
INSERT INTO idempotency_keys (key, request_hash)
VALUES ('request-8f3', 'hash-abc')
ON CONFLICT (key) DO NOTHING
RETURNING key;
~~~

Nếu không có row trả về, key đã tồn tại. Không được mặc định tạo lại order.

---

## 19. Optimistic và pessimistic concurrency

### Pessimistic locking

Khóa trước khi đọc hoặc cập nhật:

~~~sql
SELECT quantity
FROM inventory
WHERE book_id = 10
FOR UPDATE;
~~~

Phù hợp khi:

- xung đột thường xuyên;
- resource khan hiếm như inventory cuối;
- cần bảo vệ nhiều bước trong một transaction.

### Optimistic locking

Không khóa lâu; kiểm tra version khi update:

~~~sql
UPDATE documents
SET content = 'new content',
    version = version + 1
WHERE id = 5
  AND version = 12;
~~~

Nếu row count là 0, báo conflict hoặc retry.

Phù hợp khi:

- xung đột hiếm;
- transaction không cần giữ lock;
- muốn giảm thời gian chờ.

Không có lựa chọn luôn tốt hơn. Chọn theo tỷ lệ conflict, độ dài transaction và hậu quả khi retry.

---

## 20. Advisory lock ở mức nhận biết

PostgreSQL có advisory lock cho resource do application định nghĩa:

~~~sql
SELECT pg_advisory_xact_lock(12345);
~~~

Lock này gắn với transaction và tự giải phóng khi commit hoặc rollback. Cả các code path liên quan phải dùng cùng quy ước lock key; database không tự biết key đại diện cho resource nào.

Advisory lock hữu ích cho một số job hoặc resource logic không tương ứng trực tiếp với một row, nhưng không nên dùng thay foreign key hoặc row lock nếu row lock đã diễn đạt đúng invariant.

---

## 21. Kiểm tra row count và RETURNING

Sau UPDATE hoặc DELETE có điều kiện, phải biết có bao nhiêu row bị tác động.

~~~sql
UPDATE inventory
SET quantity = quantity - 1
WHERE book_id = 10
  AND quantity >= 1
RETURNING book_id, quantity;
~~~

Không có row trả về có thể nghĩa là:

- book không tồn tại;
- inventory đã hết;
- điều kiện trạng thái không còn đúng;
- transaction khác đã xử lý trước.

RETURNING giúp lấy dữ liệu đã ghi mà không cần query thêm:

~~~sql
INSERT INTO orders (customer_id, status)
VALUES (7, 'pending')
RETURNING id, ordered_at;
~~~

---

## 22. Bài tập thực hành

### Bài 1 — Atomic inventory

Viết transaction trừ một đơn vị inventory chỉ khi quantity còn ít nhất 1. Nếu không có row được cập nhật, coi là hết hàng.

### Bài 2 — Đặt hàng nhiều item

Thiết kế các bước transaction cho order có nhiều item. Các row inventory phải được khóa theo thứ tự book_id tăng dần.

### Bài 3 — Hai session

Mô phỏng hai session cùng mua cuốn sách cuối cùng. Ghi lại session nào chờ, khi nào tiếp tục và vì sao chỉ một transaction thành công.

### Bài 4 — Lost update

Tạo ví dụ lost update với quantity ban đầu là 10. Sửa lại bằng conditional UPDATE và bằng SELECT FOR UPDATE.

### Bài 5 — SAVEPOINT

Tạo order trước, tạo item sau. Nếu một item không hợp lệ, rollback về savepoint và chuyển order thành cancelled trong cùng transaction.

### Bài 6 — Deadlock

Mô tả hai transaction khóa book 10 và 20 theo thứ tự ngược nhau. Sửa bằng cách chuẩn hóa thứ tự khóa.

### Bài 7 — Idempotency

Thiết kế bảng và flow để request tạo order được retry an toàn bằng idempotency key.

### Bài 8 — Isolation

Giải thích sự khác nhau giữa Read Committed và Repeatable Read trong kịch bản một transaction đọc inventory hai lần trong khi transaction khác commit update ở giữa.

### Đáp án tham khảo

~~~sql
-- Bài 1
BEGIN;

UPDATE inventory
SET quantity = quantity - 1
WHERE book_id = 10
  AND quantity >= 1
RETURNING book_id, quantity;

-- Nếu không có row: ROLLBACK.
-- Nếu có row: tiếp tục nghiệp vụ.
COMMIT;

-- Bài 2: khóa nhiều row theo thứ tự ổn định
BEGIN;

SELECT book_id, quantity
FROM inventory
WHERE book_id IN (10, 20)
ORDER BY book_id
FOR UPDATE;

UPDATE inventory
SET quantity = quantity - 1
WHERE book_id = 10 AND quantity >= 1;

UPDATE inventory
SET quantity = quantity - 2
WHERE book_id = 20 AND quantity >= 2;

-- Kiểm tra row count của cả hai UPDATE.
COMMIT;

-- Bài 5: savepoint
BEGIN;

INSERT INTO orders (customer_id, status)
VALUES (7, 'pending')
RETURNING id;

SAVEPOINT before_items;

-- Nếu item lỗi:
ROLLBACK TO SAVEPOINT before_items;

UPDATE orders
SET status = 'cancelled'
WHERE id = 100;

COMMIT;

-- Bài 7: claim idempotency key
INSERT INTO idempotency_keys (key, request_hash)
VALUES ('request-8f3', 'hash-abc')
ON CONFLICT (key) DO NOTHING
RETURNING key;
~~~

---

## 23. Tiêu chí hoàn thành Tuần 11

Bạn hoàn thành bài học khi có thể:

- Giải thích Atomicity, Consistency, Isolation và Durability.
- Dùng BEGIN, COMMIT, ROLLBACK và SAVEPOINT.
- Thiết kế transaction đặt hàng nhiều bước.
- Dùng conditional UPDATE và kiểm tra row count.
- Dùng SELECT FOR UPDATE để bảo vệ resource cạnh tranh.
- Giải thích MVCC ở mức thực hành.
- Phân biệt Read Committed và Repeatable Read.
- Nhận biết lost update.
- Giải thích deadlock và cách khóa theo thứ tự ổn định.
- Giữ transaction ngắn và không gọi external service bên trong transaction dài.
- Thiết kế idempotency cho request retry.
- Phân biệt pessimistic locking và optimistic locking.
- Biết khi nào cần retry toàn bộ transaction sau lỗi deadlock hoặc serialization.

## Tóm tắt cần nhớ

~~~text
Transaction là một đơn vị công việc: thành công hết hoặc rollback hết.
BEGIN bắt đầu; COMMIT xác nhận; ROLLBACK hủy; SAVEPOINT quay lui một phần.
Read Committed là isolation mặc định và mỗi statement có snapshot riêng.
Repeatable Read giữ snapshot ổn định hơn nhưng có thể cần retry.
SELECT FOR UPDATE khóa row tới khi transaction kết thúc.
Conditional UPDATE giúp kiểm tra và cập nhật resource trong một bước.
Lost update cần lock, điều kiện update hoặc version check.
Khóa nhiều resource theo cùng một thứ tự để giảm deadlock.
Không gọi network hoặc chờ người dùng trong transaction dài.
Retry phải chạy lại toàn bộ transaction, không chỉ statement cuối.
Idempotency key giúp retry request không tạo hiệu ứng nghiệp vụ trùng.
~~~

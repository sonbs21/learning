# Tuần 8 nâng cao: MVCC, isolation và locking trong PostgreSQL

> Mục tiêu: hiểu visibility của MVCC, chọn isolation level, dùng row/table lock, SELECT FOR UPDATE, FOR SHARE, NOWAIT, SKIP LOCKED, phát hiện lost update/write skew/deadlock bằng pg_locks và thiết kế retry có giới hạn khi gặp serialization failure.

Concurrency không phải chỉ là “hai query chạy cùng lúc”. Câu hỏi quan trọng là:

~~~text
Transaction nào nhìn thấy phiên bản row nào?
Transaction nào được phép cập nhật row?
Lock được giữ tới khi nào?
Nếu hai giao dịch cùng thỏa điều kiện, invariant có còn đúng không?
Khi có deadlock hoặc serialization failure, application làm gì?
~~~

Lab chính dùng hai psql session. Không chạy các câu lệnh phá dữ liệu trên database người dùng.

---

## 1. MVCC là gì?

PostgreSQL dùng MVCC, tức Multi-Version Concurrency Control. Khi row được UPDATE, PostgreSQL tạo phiên bản mới thay vì ghi đè theo cách khiến mọi reader phải chờ writer.

MVCC giúp:

- reader và writer ít chặn nhau hơn;
- mỗi transaction có snapshot để quyết định row visible;
- transaction không đọc dữ liệu chưa commit;
- UPDATE/DELETE tạo dead tuple cần VACUUM dọn sau này.

MVCC không loại bỏ lock. UPDATE vẫn cần lock row. DDL và một số thao tác table vẫn có thể chờ lock mạnh. MVCC cũng không tự bảo vệ mọi business invariant như “chỉ một worker nhận một job” hoặc “inventory không âm”.

---

## 2. Row version và visibility

Mỗi phiên bản row có metadata transaction nội bộ như transaction tạo và transaction xóa phiên bản. Người dùng thường không cần đọc trực tiếp các field này, nhưng cần hiểu nguyên tắc:

- row chưa commit của transaction khác không visible;
- row đã bị xóa bởi transaction chưa commit vẫn có thể visible với snapshot cũ;
- snapshot quyết định transaction nào đã commit tại thời điểm đọc;
- VACUUM dọn phiên bản cũ khi không còn transaction cần chúng.

Ví dụ:

~~~text
T1 insert row nhưng chưa commit
T2 SELECT không thấy row của T1
T1 COMMIT
T2 có thể thấy row trong statement/snapshot tiếp theo tùy isolation
~~~

Một transaction không có nghĩa mọi statement luôn thấy cùng snapshot. Điều này phụ thuộc isolation level.

---

## 3. Transaction và snapshot

Bắt đầu transaction:

~~~sql
BEGIN;
~~~

Kết thúc:

~~~sql
COMMIT;
~~~

hoặc:

~~~sql
ROLLBACK;
~~~

Read Committed mặc định tạo snapshot cho từng statement. Repeatable Read tạo snapshot ổn định hơn cho cả transaction.

Không nên giữ transaction lâu vì:

- giữ snapshot cũ;
- cản VACUUM dọn dead tuple;
- giữ row/table lock;
- tăng lock wait;
- tăng khả năng deadlock;
- làm connection pool bị chiếm.

Đặc biệt tránh idle in transaction trong application.

---

## 4. Read Committed

Read Committed là isolation level mặc định PostgreSQL.

Đặc điểm:

- mỗi statement có snapshot riêng;
- không thấy uncommitted data;
- hai SELECT trong một transaction có thể thấy kết quả khác nếu transaction khác commit ở giữa;
- UPDATE có thể chờ row lock;
- sau khi chờ lock, PostgreSQL đánh giá lại điều kiện trên phiên bản mới trong một số tình huống.

Demo hai session.

Session A:

~~~sql
BEGIN;

UPDATE inventory
SET quantity = quantity - 1
WHERE book_id = 10;

-- chưa COMMIT
~~~

Session B:

~~~sql
BEGIN;

SELECT quantity
FROM inventory
WHERE book_id = 10;
~~~

B không thấy giá trị chưa commit của A và có thể đọc giá trị cũ. Nếu B UPDATE cùng row, B sẽ chờ A.

A:

~~~sql
COMMIT;
~~~

B tiếp tục sau khi lock được giải phóng. Application phải hiểu transaction đang thao tác trên phiên bản mới, không tiếp tục giả định dữ liệu đã đọc trước khi chờ lock vẫn còn đúng.

---

## 5. Repeatable Read

Bắt đầu Repeatable Read:

~~~sql
BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ;
~~~

Các statement trong transaction nhìn một snapshot ổn định hơn. Nếu transaction khác commit thay đổi sau snapshot, SELECT lặp lại có thể vẫn thấy phiên bản cũ.

Khi Repeatable Read cố cập nhật row đã thay đổi sau snapshot, PostgreSQL có thể báo serialization failure. Application phải rollback và retry toàn bộ transaction.

Repeatable Read phù hợp khi:

- cần đọc nhất quán nhiều bước;
- báo cáo hoặc nghiệp vụ cần snapshot ổn định;
- chấp nhận retry;
- transaction ngắn.

Không dùng Repeatable Read để giữ transaction mở lâu. Snapshot ổn định không làm transaction dài trở nên rẻ hơn.

---

## 6. Serializable ở mức thực hành

Serializable cố bảo đảm kết quả tương đương một thứ tự chạy tuần tự:

~~~sql
BEGIN TRANSACTION ISOLATION LEVEL SERIALIZABLE;

-- đọc, kiểm tra, cập nhật

COMMIT;
~~~

PostgreSQL có thể hủy transaction với lỗi serialization failure khi phát hiện các giao dịch không thể đồng thời theo thứ tự serializable.

Application phải:

1. rollback transaction lỗi;
2. chờ backoff ngắn;
3. chạy lại toàn bộ transaction;
4. giới hạn số lần retry;
5. ghi log số lần retry và context.

Retry toàn bộ transaction, không chỉ statement cuối. Các bước trước đó có thể đã dựa trên snapshot hoặc kết quả không còn hợp lệ.

Serializable phù hợp với invariant khó bảo vệ bằng một row lock đơn giản, nhưng có chi phí retry và contention.

---

## 7. Dirty read, non-repeatable read và phantom

### Dirty read

Đọc dữ liệu chưa commit của transaction khác. PostgreSQL không cho dirty read ở các isolation level thông thường.

### Non-repeatable read

Cùng một SELECT chạy hai lần thấy giá trị khác vì transaction khác commit UPDATE ở giữa. Read Committed có thể gặp hiện tượng này giữa hai statement.

### Phantom

Một query lặp lại thấy thêm hoặc mất row thỏa điều kiện vì transaction khác INSERT hoặc DELETE row phù hợp.

Repeatable Read và Serializable giảm hoặc phát hiện nhiều anomaly, nhưng isolation cao hơn không thay thế việc thiết kế lock và constraint.

Điều cần nhớ không phải chỉ là tên anomaly. Hãy vẽ timeline:

~~~text
T1 BEGIN
T1 SELECT
T2 UPDATE
T2 COMMIT
T1 SELECT lại
T1 COMMIT
~~~

Sau đó ghi rõ isolation level và kết quả mong đợi.

---

## 8. Row-level lock

SELECT FOR UPDATE khóa các row được chọn để transaction khác không thể cập nhật hoặc khóa tương thích cho tới khi transaction kết thúc.

~~~sql
BEGIN;

SELECT book_id, quantity
FROM inventory
WHERE book_id = 10
FOR UPDATE;

UPDATE inventory
SET quantity = quantity - 1
WHERE book_id = 10;

COMMIT;
~~~

Các lock phổ biến:

- FOR UPDATE: khóa mạnh cho update/delete;
- FOR NO KEY UPDATE: khóa update không đổi key;
- FOR SHARE: khóa chia sẻ;
- FOR KEY SHARE: bảo vệ key bị foreign key tham chiếu.

Ở mức thực hành, FOR UPDATE và FOR SHARE là hai mẫu cần nắm chắc.

Lock được giữ tới COMMIT hoặc ROLLBACK, không chỉ hết statement.

---

## 9. NOWAIT

NOWAIT không chờ lock. Nếu row đang bị khóa, statement lỗi ngay:

~~~sql
SELECT *
FROM inventory
WHERE book_id = 10
FOR UPDATE NOWAIT;
~~~

Phù hợp khi:

- UI cần báo resource đang được xử lý;
- worker không muốn chờ;
- cần thử lock và chuyển sang việc khác;
- timeout do application tự điều khiển.

Application phải bắt lỗi lock_not_available và quyết định retry, bỏ qua hoặc trả lỗi thân thiện.

NOWAIT không tự rollback transaction. Sau lỗi statement, transaction có thể ở trạng thái aborted; thường cần ROLLBACK hoặc xử lý savepoint.

---

## 10. SKIP LOCKED

SKIP LOCKED bỏ qua row đang bị lock:

~~~sql
SELECT id, payload
FROM jobs
WHERE status = 'ready'
ORDER BY id
FOR UPDATE SKIP LOCKED
LIMIT 10;
~~~

Đây là pattern cho nhiều worker lấy job. Worker A khóa một nhóm row; worker B lấy nhóm chưa bị khóa thay vì chờ.

SKIP LOCKED có thể trả kết quả không đầy đủ hoặc không theo thứ tự toàn cục tuyệt đối trong môi trường concurrency. Đây là trade-off có chủ đích cho queue.

---

## 11. Worker queue bằng FOR UPDATE SKIP LOCKED

Schema:

~~~sql
CREATE TABLE jobs (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    status text NOT NULL CHECK (
        status IN ('ready', 'processing', 'done', 'failed')
    ),
    available_at timestamptz NOT NULL DEFAULT now(),
    attempts integer NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    payload jsonb NOT NULL,
    locked_at timestamptz
);
~~~

Claim job:

~~~sql
BEGIN;

WITH picked AS (
    SELECT id
    FROM jobs
    WHERE status = 'ready'
      AND available_at <= now()
    ORDER BY id
    FOR UPDATE SKIP LOCKED
    LIMIT 10
)
UPDATE jobs AS j
SET
    status = 'processing',
    attempts = j.attempts + 1,
    locked_at = now()
FROM picked
WHERE j.id = picked.id
RETURNING j.id, j.payload, j.attempts;

COMMIT;
~~~

Sau COMMIT, worker xử lý job bên ngoài transaction. Không giữ row lock trong khi gọi API lâu.

Nếu job xử lý thất bại:

~~~sql
UPDATE jobs
SET status = 'failed'
WHERE id = 100;
~~~

Nếu retry:

~~~sql
UPDATE jobs
SET
    status = 'ready',
    available_at = now() + interval '5 minutes',
    locked_at = NULL
WHERE id = 100
  AND attempts < 5;
~~~

Cần có cơ chế lease timeout nếu worker chết sau khi claim.

---

## 12. FOR SHARE

FOR SHARE khóa row để bảo vệ việc row bị thay đổi hoặc xóa theo cách xung đột với việc đọc có ý nghĩa nghiệp vụ:

~~~sql
BEGIN;

SELECT id, status
FROM orders
WHERE id = 100
FOR SHARE;

-- đọc nhất quán trong workflow
COMMIT;
~~~

FOR SHARE không phải cách thay thế authorization. Nó chỉ điều khiển concurrency trong database.

Chọn khóa theo invariant:

- cần cập nhật row: FOR UPDATE;
- cần ngăn thay đổi/xóa trong một đoạn logic đọc: FOR SHARE;
- chỉ cần đọc snapshot: không nhất thiết cần explicit lock.

---

## 13. Table lock

Table lock khóa ở cấp table:

~~~sql
LOCK TABLE orders IN SHARE MODE;
~~~

Các mode có xung đột khác nhau. Không cần nhớ toàn bộ ma trận ngay, nhưng phải biết:

- table lock có thể chặn nhiều loại thao tác;
- lock mode mạnh có blast radius lớn;
- lock được giữ đến transaction end;
- DDL thường cần lock mạnh;
- LOCK TABLE phải có lý do rõ ràng và test trên workload.

Phần lớn nghiệp vụ row-level nên dùng row lock hoặc atomic UPDATE thay vì table lock.

Xem lock mode và blocker bằng pg_locks thay vì đoán.

---

## 14. Inventory và lost update

Giả sử quantity bằng 1. Hai request cùng đọc quantity rồi trừ trong application:

~~~text
T1 đọc 1
T2 đọc 1
T1 tính 0
T2 tính 0
T1 ghi 0
T2 ghi 0
~~~

Kết quả ghi nhận một lần trừ dù có hai lần mua. Đây là lost update.

Mẫu atomic update:

~~~sql
UPDATE inventory
SET quantity = quantity - 1,
    updated_at = now()
WHERE book_id = 10
  AND quantity >= 1
RETURNING book_id, quantity;
~~~

Nếu không có row trả về, hết hàng hoặc resource không tồn tại. Nếu có row, bước trừ thành công.

Mẫu lock rõ ràng:

~~~sql
BEGIN;

SELECT quantity
FROM inventory
WHERE book_id = 10
FOR UPDATE;

-- kiểm tra quantity trong transaction

UPDATE inventory
SET quantity = quantity - 1
WHERE book_id = 10;

COMMIT;
~~~

Atomic UPDATE thường đủ cho một điều kiện đơn giản. FOR UPDATE phù hợp khi nhiều quyết định phụ thuộc row đã khóa.

---

## 15. Write skew

Write skew xảy ra khi hai transaction đọc các row khác nhau, mỗi transaction thấy invariant còn đúng, rồi cập nhật row riêng khiến invariant tổng thể bị sai.

Ví dụ có hai bác sĩ trực:

~~~text
Ít nhất một bác sĩ phải on call.
T1 thấy bác sĩ B on call và tắt A.
T2 thấy bác sĩ A on call và tắt B.
Cả hai commit.
Không còn ai on call.
~~~

Row lock trên row riêng không đủ vì mỗi transaction không khóa toàn bộ tập dữ liệu liên quan.

Cách xử lý:

- khóa một row đại diện cho resource;
- SELECT FOR UPDATE toàn bộ rows liên quan theo thứ tự;
- dùng Serializable và retry;
- thiết kế constraint hoặc bảng trạng thái phù hợp.

Không sửa write skew bằng cách đoán rằng hai SELECT riêng sẽ tự bảo vệ invariant.

---

## 16. Deadlock

Deadlock xảy ra khi transaction giữ lock mà transaction kia cần, đồng thời chờ lock ngược lại.

~~~text
T1 giữ row 10, chờ row 20
T2 giữ row 20, chờ row 10
~~~

Tạo tình huống trong lab.

Session A:

~~~sql
BEGIN;

SELECT *
FROM inventory
WHERE book_id = 10
FOR UPDATE;
~~~

Session B:

~~~sql
BEGIN;

SELECT *
FROM inventory
WHERE book_id = 20
FOR UPDATE;
~~~

A:

~~~sql
SELECT *
FROM inventory
WHERE book_id = 20
FOR UPDATE;
~~~

B:

~~~sql
SELECT *
FROM inventory
WHERE book_id = 10
FOR UPDATE;
~~~

PostgreSQL phát hiện deadlock và hủy một transaction.

Phòng tránh:

- lock resource theo thứ tự tăng dần;
- giữ transaction ngắn;
- không gọi network trong transaction;
- giảm số row khóa;
- dùng timeout;
- retry transaction bị deadlock với giới hạn.

Khóa theo thứ tự:

~~~sql
SELECT book_id, quantity
FROM inventory
WHERE book_id IN (10, 20)
ORDER BY book_id
FOR UPDATE;
~~~

---

## 17. Xem lock bằng pg_locks

pg_locks cho biết lock mà session đang giữ hoặc đang yêu cầu:

~~~sql
SELECT
    pid,
    locktype,
    mode,
    granted,
    relation::regclass AS relation_name,
    page,
    tuple,
    virtualxid,
    transactionid
FROM pg_locks
WHERE pid <> pg_backend_pid()
ORDER BY pid, granted;
~~~

Tìm session đang chờ:

~~~sql
SELECT
    pid,
    usename,
    state,
    wait_event_type,
    wait_event,
    query,
    query_start
FROM pg_stat_activity
WHERE wait_event_type = 'Lock';
~~~

Tìm blocker qua wait_event hoặc pg_blocking_pids:

~~~sql
SELECT
    pid,
    pg_blocking_pids(pid) AS blocking_pids,
    query,
    state,
    wait_event_type,
    wait_event
FROM pg_stat_activity
WHERE cardinality(pg_blocking_pids(pid)) > 0;
~~~

Không kill session ngay khi thấy lock. Xác định:

- blocker đang chạy query gì;
- transaction mở bao lâu;
- có idle in transaction không;
- terminate có làm mất công việc hoặc rollback lớn không;
- timeout và retry có phải giải pháp tốt hơn không.

---

## 18. Timeout

statement_timeout giới hạn thời gian một statement:

~~~sql
SET LOCAL statement_timeout = '5s';
~~~

lock_timeout giới hạn thời gian chờ lock:

~~~sql
SET LOCAL lock_timeout = '1s';
~~~

idle_in_transaction_session_timeout đóng session idle trong transaction:

~~~sql
SET LOCAL idle_in_transaction_session_timeout = '30s';
~~~

Dùng SET LOCAL trong transaction hoặc cấu hình theo role/workload khi phù hợp. Timeout không sửa deadlock hoặc query sai; nó giới hạn blast radius.

Application phải phân biệt:

- statement timeout;
- lock timeout;
- deadlock detected;
- serialization failure;
- connection failure.

Mỗi lỗi có retry policy khác nhau.

---

## 19. Retry có giới hạn

Retry phù hợp với:

- deadlock detected;
- serialization failure;
- một số lock timeout ngắn;
- lỗi transient đã xác định.

Retry không phù hợp với:

- constraint violation do dữ liệu sai;
- authorization failure;
- syntax error;
- bug deterministic;
- transaction quá lớn luôn timeout.

Mẫu policy:

~~~text
max_attempts = 3
backoff = exponential với jitter
retry toàn bộ transaction
log attempt, error, resource và duration
sau cùng trả lỗi rõ ràng
~~~

Pseudo-code:

~~~text
for attempt in 1..3:
    BEGIN
    try:
        chạy toàn bộ business transaction
        COMMIT
        return success
    catch retryable_error:
        ROLLBACK
        sleep(backoff + jitter)
continue
return failure
~~~

Không retry chỉ statement cuối. Transaction có thể đã đọc snapshot và lock khác nhau.

---

## 20. Isolation level và nghiệp vụ

| Nhu cầu | Gợi ý |
|---|---|
| CRUD ngắn, không invariant phức tạp | Read Committed |
| Nhiều đọc cần snapshot ổn định | Repeatable Read |
| Invariant liên quan nhiều row khó khóa rõ | Serializable + retry |
| Inventory một row | conditional UPDATE hoặc FOR UPDATE |
| Queue nhiều worker | FOR UPDATE SKIP LOCKED |
| Không muốn chờ lock | NOWAIT hoặc lock_timeout |
| Nhiều resource | thứ tự lock nhất quán |

Đây là điểm bắt đầu, không phải luật cứng. Isolation cao hơn không thể sửa transaction boundary sai hoặc query không có điều kiện.

---

## 21. Hai worker lấy job bằng SKIP LOCKED

Chuẩn bị:

~~~sql
INSERT INTO jobs (status, payload)
SELECT
    'ready',
    jsonb_build_object('sequence', g)
FROM generate_series(1, 20) AS g;
~~~

Mở session A và B.

Session A:

~~~sql
BEGIN;

SELECT id
FROM jobs
WHERE status = 'ready'
ORDER BY id
FOR UPDATE SKIP LOCKED
LIMIT 5;
~~~

Chưa commit. Session B chạy cùng query:

~~~sql
BEGIN;

SELECT id
FROM jobs
WHERE status = 'ready'
ORDER BY id
FOR UPDATE SKIP LOCKED
LIMIT 5;
~~~

B lấy các row khác, không chờ A. Để biến claim thành trạng thái bền vững, dùng UPDATE trong cùng transaction rồi commit.

Kiểm tra không có duplicate claim:

~~~sql
SELECT id, status, count(*)
FROM jobs
GROUP BY id, status
HAVING count(*) > 1;
~~~

Bảng jobs cần lưu claim/lease nếu worker có thể chết sau commit claim.

---

## 22. Hai giao dịch cùng trừ inventory

Inventory:

~~~sql
INSERT INTO inventory (book_id, quantity)
VALUES (10, 1)
ON CONFLICT (book_id)
DO UPDATE SET quantity = EXCLUDED.quantity;
~~~

Session A:

~~~sql
BEGIN;

UPDATE inventory
SET quantity = quantity - 1
WHERE book_id = 10
  AND quantity >= 1
RETURNING book_id, quantity;
~~~

Session B chạy cùng lúc. Một session thành công; session còn lại chờ và sau đó không có row vì quantity không còn >= 1.

Điểm cần kiểm tra:

- application có kiểm tra row count không;
- transaction có rollback khi không có row không;
- order item chỉ được tạo sau khi reserve thành công;
- retry có làm trừ inventory lần hai không;
- payment failure có hoàn inventory theo policy không.

---

## 23. Serialization failure

Lab:

~~~sql
BEGIN TRANSACTION ISOLATION LEVEL SERIALIZABLE;

SELECT quantity
FROM inventory
WHERE book_id = 10;

-- transaction khác thay đổi cùng dữ liệu

UPDATE inventory
SET quantity = quantity - 1
WHERE book_id = 10;

COMMIT;
~~~

Một transaction có thể nhận lỗi serialization failure. Application phải rollback và retry transaction từ đầu.

Không retry trong cùng transaction đã lỗi. Sau error, transaction thường ở trạng thái aborted cho tới ROLLBACK.

Ghi log:

~~~text
transaction name
isolation level
attempt
SQL operation
resource identifiers
error code
duration
~~~

---

## 24. Visibility và VACUUM

MVCC tạo các row version cũ. VACUUM dọn dead tuple khi không còn transaction cần nhìn thấy chúng.

Transaction mở lâu có thể:

- giữ snapshot cũ;
- ngăn VACUUM dọn một số version;
- làm n_dead_tup tăng;
- gây table/index bloat;
- làm autovacuum khó theo kịp.

Theo dõi transaction lâu:

~~~sql
SELECT
    pid,
    usename,
    state,
    xact_start,
    query_start,
    now() - xact_start AS transaction_age,
    wait_event_type,
    query
FROM pg_stat_activity
WHERE xact_start IS NOT NULL
ORDER BY xact_start;
~~~

Nếu state là idle in transaction trong thời gian dài, cần sửa application hoặc timeout. Đừng chỉ tăng autovacuum để che transaction boundary sai.

---

## 25. Lab lock report

Mẫu báo cáo:

~~~text
Scenario
Session A steps
Session B steps
Isolation level
Locks held
Locks waited
Observed result
Expected invariant
Error code nếu có
Retry policy
~~~

Ví dụ:

~~~text
Scenario: two workers claim jobs
Isolation: Read Committed
Lock: FOR UPDATE SKIP LOCKED
Result: workers nhận các row khác nhau
Invariant: một job chỉ được claim một lần
Operational note: cần lease khi worker chết
~~~

---

## 26. Lỗi thường gặp

### Nghĩ MVCC loại bỏ mọi lock

UPDATE, DELETE và explicit SELECT FOR UPDATE vẫn khóa row.

### Giữ transaction khi gọi API

Network chậm làm lock giữ lâu và connection pool bị cạn.

### Dùng SELECT rồi UPDATE không khóa

Có thể tạo lost update hoặc kiểm tra stale data.

### Dùng table lock cho inventory

Table lock làm giảm concurrency không cần thiết. Dùng conditional UPDATE hoặc row lock.

### SKIP LOCKED cho dữ liệu cần hoàn toàn công bằng

SKIP LOCKED có thể bỏ qua row đang bận. Nó phù hợp queue throughput, không phải mọi fairness policy.

### Retry vô hạn

Retry vô hạn che deadlock, tăng tải và làm latency xấu hơn. Retry có giới hạn và backoff.

### Retry statement thay vì transaction

Serialization failure yêu cầu retry toàn bộ transaction.

### Khóa theo thứ tự khác nhau

Lock resource theo thứ tự ổn định để giảm deadlock.

### Không đặt timeout

Một blocker lâu có thể làm request chờ vô hạn. Dùng statement_timeout, lock_timeout và idle_in_transaction_session_timeout phù hợp.

### Không kiểm tra pg_locks

Khi query chậm do lock, EXPLAIN có thể không phải nơi đầu tiên cần nhìn. Kiểm tra wait_event và blocker.

---

## 27. Bài tập thực hành

### Bài 1 — Visibility

Mở hai session, INSERT nhưng chưa COMMIT ở session A. Kiểm tra session B thấy gì ở Read Committed.

### Bài 2 — Non-repeatable read

Dùng hai SELECT trong Read Committed, commit UPDATE ở session khác giữa hai SELECT. Ghi kết quả.

### Bài 3 — Repeatable Read

Lặp lại bài 2 ở Repeatable Read. Giải thích snapshot khác nhau thế nào.

### Bài 4 — Worker queue

Dùng FOR UPDATE SKIP LOCKED để cho hai worker claim 10 jobs mà không trùng.

### Bài 5 — NOWAIT

Khóa row ở session A; dùng NOWAIT ở session B và ghi error behavior.

### Bài 6 — Inventory

Hai session cùng trừ inventory cuối cùng bằng conditional UPDATE. Chứng minh chỉ một session thành công.

### Bài 7 — Deadlock

Tạo deadlock bằng cách khóa row 10 rồi 20 ở A và 20 rồi 10 ở B. Quan sát lỗi và sửa bằng thứ tự tăng dần.

### Bài 8 — pg_locks

Trong lúc B chờ A, query pg_stat_activity, pg_locks và pg_blocking_pids để tìm blocker.

### Bài 9 — Serialization retry

Chạy hai transaction Serializable có conflict. Viết pseudo-code retry tối đa ba lần với exponential backoff.

### Bài 10 — Write skew

Thiết kế ví dụ hai nhân viên on-call và chứng minh row lock riêng không đủ. Đề xuất khóa chung hoặc Serializable.

---

## 28. Tiêu chí hoàn thành Tuần 8

Bạn hoàn thành bài học khi có thể:

- Giải thích MVCC và visibility ở mức thực hành.
- Phân biệt Read Committed, Repeatable Read và Serializable.
- Dùng row lock và hiểu table lock.
- Dùng SELECT FOR UPDATE và FOR SHARE.
- Dùng NOWAIT và SKIP LOCKED đúng tình huống.
- Thiết kế worker queue nhiều session.
- Bảo vệ inventory khỏi lost update.
- Nhận biết write skew.
- Tạo và phòng tránh deadlock bằng thứ tự lock.
- Dùng pg_locks, pg_stat_activity và pg_blocking_pids để tìm blocker.
- Đặt statement timeout, lock timeout và idle transaction timeout.
- Retry deadlock hoặc serialization failure có giới hạn.
- Hiểu transaction dài ảnh hưởng MVCC và VACUUM.
- Chọn isolation level theo invariant nghiệp vụ.

## Tóm tắt cần nhớ

~~~text
MVCC cho mỗi transaction một cách nhìn về các phiên bản row.
Read Committed có snapshot theo statement.
Repeatable Read giữ snapshot ổn định hơn và có thể cần retry.
Serializable có thể báo serialization failure; retry toàn bộ transaction.
FOR UPDATE khóa row tới COMMIT hoặc ROLLBACK.
NOWAIT không chờ; SKIP LOCKED bỏ qua row đang bị khóa.
Conditional UPDATE thường là cách đơn giản để trừ inventory an toàn.
Khóa nhiều resource theo cùng một thứ tự để giảm deadlock.
pg_locks và pg_blocking_pids giúp tìm blocker.
Transaction dài giữ snapshot và lock lâu, cản VACUUM.
Timeout giới hạn blast radius; retry phải có backoff và giới hạn.
~~~

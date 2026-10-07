# Tuần 9 nâng cao: VACUUM, autovacuum và bloat trong PostgreSQL

> Mục tiêu: hiểu dead tuple do MVCC, phân biệt VACUUM thường, VACUUM FULL và ANALYZE, theo dõi autovacuum, xử lý transaction wraparound, nhận biết table/index bloat, visibility map, HOT update và fillfactor.

PostgreSQL không xóa vật lý row ngay khi UPDATE hoặc DELETE. MVCC cần giữ phiên bản cũ cho transaction còn có thể nhìn thấy. VACUUM là cơ chế dọn dẹp những phiên bản không còn cần thiết.

Mối quan hệ chính:

~~~text
UPDATE/DELETE
  → dead tuple
  → VACUUM có thể dọn tuple
  → visibility map và free space được cập nhật
  → autovacuum tự động chạy theo ngưỡng
~~~

Nếu maintenance không theo kịp:

- table phình;
- index phình;
- query đọc nhiều page hơn;
- cache hiệu quả kém;
- autovacuum bị trễ;
- transaction wraparound có thể đe dọa khả năng ghi.

---

## 1. Dead tuple do MVCC

Khi UPDATE một row, PostgreSQL thường tạo phiên bản row mới và đánh dấu phiên bản cũ không còn là phiên bản hiện tại. DELETE cũng đánh dấu row đã xóa thay vì lập tức thu hồi file page.

Ví dụ:

~~~sql
UPDATE accounts
SET last_seen_at = now()
WHERE id = 10;
~~~

Phiên bản cũ có thể cần giữ lại nếu một transaction cũ vẫn có snapshot nhìn thấy nó. Sau khi không còn transaction nào cần phiên bản cũ, VACUUM có thể đánh dấu không gian để tái sử dụng.

Dead tuple không nhất thiết biến mất khỏi file table ngay. VACUUM thường tái sử dụng space bên trong table; nó không thường trả file về OS.

Theo dõi:

~~~sql
SELECT
    relname,
    n_live_tup,
    n_dead_tup,
    n_mod_since_analyze,
    last_vacuum,
    last_autovacuum,
    last_analyze,
    last_autoanalyze
FROM pg_stat_user_tables
ORDER BY n_dead_tup DESC;
~~~

---

## 2. VACUUM thường

VACUUM thường:

- dọn dead tuple không còn cần;
- đánh dấu free space để tái sử dụng;
- cập nhật visibility map;
- hỗ trợ index-only scan;
- giúp ngăn transaction ID wraparound;
- không tạo table copy mới;
- thường cho phép đọc/ghi đồng thời hơn VACUUM FULL.

Chạy:

~~~sql
VACUUM events;
~~~

Có thể verbose:

~~~sql
VACUUM (VERBOSE) events;
~~~

Có thể freeze trong maintenance:

~~~sql
VACUUM (FREEZE, VERBOSE) events;
~~~

VACUUM không tự cập nhật column statistics như ANALYZE. Nếu cần cả hai:

~~~sql
VACUUM (ANALYZE) events;
~~~

---

## 3. VACUUM FULL

VACUUM FULL viết lại table vào file mới để thu hồi space về OS:

~~~sql
VACUUM FULL events;
~~~

Trade-off:

- cần lock mạnh;
- chặn nhiều hoạt động đọc/ghi;
- cần disk tạm đủ lớn;
- chạy lâu trên table lớn;
- có thể làm downtime;
- index cũng được rebuild theo table rewrite.

Không dùng VACUUM FULL như cron job định kỳ. Nó là công cụ sửa chữa hoặc reclaim space trong maintenance window.

Trước khi dùng:

1. xác định bloat thật sự;
2. kiểm tra disk còn đủ;
3. lên kế hoạch lock;
4. có backup/restore plan;
5. thông báo downtime nếu cần;
6. đo trước/sau.

VACUUM thường và VACUUM FULL không phải hai mức nhanh/chậm của cùng một lệnh. Chúng có mục đích vận hành khác nhau.

---

## 4. ANALYZE

ANALYZE cập nhật statistics để planner ước lượng cardinality:

~~~sql
ANALYZE events;
~~~

VACUUM dọn dead tuple; ANALYZE lấy sample dữ liệu. Một table có thể cần cả hai sau workload lớn:

~~~sql
VACUUM (ANALYZE) events;
~~~

Nhưng chúng không thay thế nhau:

| Lệnh | Mục tiêu |
|---|---|
| VACUUM | dọn dead tuple, free space, visibility |
| ANALYZE | cập nhật planner statistics |
| VACUUM FULL | rewrite và trả space về OS |

---

## 5. Autovacuum

Autovacuum tự động chạy VACUUM và ANALYZE dựa trên số row thay đổi.

Các thành phần chính:

- autovacuum launcher theo dõi database;
- autovacuum worker thực hiện vacuum/analyze;
- worker có giới hạn theo autovacuum_max_workers;
- ngưỡng được tính dựa trên threshold và scale factor.

Kiểm tra setting:

~~~sql
SELECT
    name,
    setting,
    unit,
    source
FROM pg_settings
WHERE name LIKE 'autovacuum%';
~~~

Một số setting quan trọng:

- autovacuum;
- autovacuum_max_workers;
- autovacuum_naptime;
- autovacuum_vacuum_threshold;
- autovacuum_vacuum_scale_factor;
- autovacuum_analyze_threshold;
- autovacuum_analyze_scale_factor;
- autovacuum_vacuum_cost_limit;
- autovacuum_vacuum_cost_delay;
- autovacuum_freeze_max_age.

Autovacuum là safety system quan trọng. Không tắt tùy tiện để giảm CPU; nếu tắt, dead tuple và wraparound risk tăng.

---

## 6. Ngưỡng autovacuum

Mô hình đơn giản:

~~~text
vacuum khi dead tuples >
autovacuum_vacuum_threshold
+ autovacuum_vacuum_scale_factor × số row
~~~

Analyze tương tự dựa trên số row thay đổi.

Với table nhỏ, threshold cố định có thể khiến autovacuum chạy trước khi có nhiều row thay đổi. Với table rất lớn, scale factor mặc định có thể chờ quá nhiều dead tuple.

Ví dụ table một tỷ row với scale factor 0.2 có thể cần hàng trăm triệu thay đổi trước khi threshold tự nhiên kích hoạt. Vì vậy bảng lớn hoặc nóng thường cần per-table tuning.

---

## 7. Cấu hình autovacuum riêng cho table nóng

Xem table nóng:

~~~sql
SELECT
    relname,
    n_live_tup,
    n_dead_tup,
    n_mod_since_analyze,
    last_autovacuum,
    last_autoanalyze
FROM pg_stat_user_tables
WHERE relname IN ('events', 'orders', 'inventory');
~~~

Đặt scale factor nhỏ hơn:

~~~sql
ALTER TABLE events SET (
    autovacuum_vacuum_scale_factor = 0.02,
    autovacuum_analyze_scale_factor = 0.01,
    autovacuum_vacuum_threshold = 1000,
    autovacuum_analyze_threshold = 1000
);
~~~

Tuning per-table cần dựa trên:

- tốc độ INSERT/UPDATE/DELETE;
- kích thước table;
- dead tuple growth;
- latency;
- CPU/I/O;
- thời gian vacuum;
- workload ban ngày và ban đêm.

Không copy cùng một setting cho mọi table. Table append-only và table update-heavy có nhu cầu khác nhau.

---

## 8. Theo dõi autovacuum

pg_stat_user_tables:

~~~sql
SELECT
    schemaname,
    relname,
    n_live_tup,
    n_dead_tup,
    n_mod_since_analyze,
    last_autovacuum,
    autovacuum_count,
    last_autoanalyze,
    autoanalyze_count
FROM pg_stat_user_tables
ORDER BY n_dead_tup DESC;
~~~

Xem worker đang chạy:

~~~sql
SELECT
    pid,
    datname,
    relid::regclass AS relation_name,
    phase,
    heap_blks_total,
    heap_blks_scanned,
    heap_blks_vacuumed,
    index_vacuum_count,
    num_dead_tuples,
    max_dead_tuples
FROM pg_stat_progress_vacuum;
~~~

Khi autovacuum chạy lâu, xem:

- phase;
- table nào;
- heap block scanned/vacuumed;
- index vacuum count;
- blocker;
- I/O và cost delay;
- transaction lâu có giữ snapshot không.

---

## 9. Transaction lâu và VACUUM

Một transaction lâu có thể giữ snapshot cần các phiên bản row cũ:

~~~sql
SELECT
    pid,
    usename,
    state,
    xact_start,
    now() - xact_start AS transaction_age,
    wait_event_type,
    query
FROM pg_stat_activity
WHERE xact_start IS NOT NULL
ORDER BY xact_start;
~~~

Transaction idle in transaction đặc biệt nguy hiểm:

- application BEGIN;
- đọc một row;
- không COMMIT/ROLLBACK;
- connection nằm idle;
- VACUUM không thể dọn một số dead tuple cũ.

Giải pháp:

- transaction boundary ngắn;
- commit/rollback trong mọi code path;
- idle_in_transaction_session_timeout;
- connection pool trả connection sau transaction;
- alert transaction age.

Đừng cố giải quyết transaction lâu bằng VACUUM FULL. Nguyên nhân cần sửa ở application hoặc session.

---

## 10. Transaction ID wraparound

PostgreSQL dùng transaction ID hữu hạn và cần freeze các row cũ trước khi ID quay vòng. Nếu không freeze kịp, database có thể ngăn thao tác để bảo vệ dữ liệu.

Theo dõi age:

~~~sql
SELECT
    datname,
    age(datfrozenxid) AS xid_age,
    datfrozenxid
FROM pg_database
ORDER BY xid_age DESC;
~~~

Theo table:

~~~sql
SELECT
    relname,
    age(relfrozenxid) AS xid_age,
    n_live_tup,
    n_dead_tup,
    last_autovacuum
FROM pg_class AS c
JOIN pg_stat_user_tables AS s
    ON s.relid = c.oid
ORDER BY age(relfrozenxid) DESC;
~~~

Autovacuum freeze là cơ chế bình thường. Đừng tắt autovacuum trên table lâu dài.

Các yếu tố gây risk:

- table ít được vacuum;
- transaction lâu;
- autovacuum bị disable;
- lock ngăn vacuum;
- workload hoặc cấu hình không phù hợp;
- database/partition bị bỏ quên.

Wraparound là vấn đề availability và correctness, không chỉ là bloat.

---

## 11. Visibility map

Visibility map lưu thông tin page:

- tất cả tuple trên page đã visible với mọi transaction;
- page đã được freeze.

Visibility map giúp:

- index-only scan biết có thể không cần đọc heap;
- VACUUM tránh đọc page không cần thiết;
- giảm I/O maintenance.

UPDATE hoặc DELETE có thể làm page mất trạng thái all-visible. VACUUM cập nhật lại khi phù hợp.

Kiểm tra index-only scan bằng:

~~~sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT customer_id, created_at
FROM orders
WHERE customer_id = 10
ORDER BY created_at DESC
LIMIT 20;
~~~

Nếu thấy heap fetches cao, index-only scan chưa tránh được heap hoàn toàn. Nguyên nhân có thể là page chưa all-visible hoặc index không chứa đủ dữ liệu.

---

## 12. HOT update

HOT là Heap-Only Tuple update. PostgreSQL có thể cập nhật row mà không tạo entry mới trong index nếu:

- các column được index không thay đổi;
- page còn đủ free space;
- điều kiện khác phù hợp.

Ví dụ table:

~~~sql
CREATE TABLE sessions (
    id bigint PRIMARY KEY,
    user_id bigint NOT NULL,
    last_seen_at timestamptz NOT NULL,
    payload jsonb NOT NULL
);

CREATE INDEX sessions_user_id_idx
ON sessions (user_id);
~~~

Update last_seen_at có thể có cơ hội HOT vì last_seen_at không nằm trong index:

~~~sql
UPDATE sessions
SET last_seen_at = now()
WHERE id = 10;
~~~

Nếu index last_seen_at:

~~~sql
CREATE INDEX sessions_last_seen_idx
ON sessions (last_seen_at);
~~~

mỗi update last_seen_at phải maintain index, làm HOT khó xảy ra.

HOT giảm index write nhưng không loại bỏ dead tuple. VACUUM vẫn cần dọn các version cũ.

---

## 13. Fillfactor

Fillfactor để lại khoảng trống trong page cho update, tăng cơ hội HOT:

~~~sql
CREATE TABLE hot_sessions (
    id bigint PRIMARY KEY,
    status text NOT NULL,
    updated_at timestamptz NOT NULL
) WITH (fillfactor = 70);
~~~

Hoặc:

~~~sql
ALTER TABLE sessions SET (fillfactor = 80);
~~~

Fillfactor thấp hơn:

- để nhiều free space;
- có thể tăng HOT update;
- table lớn hơn ngay từ đầu;
- giảm density đọc;
- cần benchmark.

Fillfactor của table và index là hai khái niệm khác nhau. Không giảm tùy tiện; chỉ tune cho table update-heavy có evidence.

Sau thay đổi fillfactor, row hiện có không tự sắp xếp lại. Có thể cần rewrite table để áp dụng đầy đủ, nên phải có kế hoạch maintenance.

---

## 14. Table bloat

Table bloat là space không còn chứa row hiện tại nhưng vẫn nằm trong relation file hoặc page layout.

Nguyên nhân:

- UPDATE/DELETE nhiều;
- dead tuple;
- page không tái sử dụng hiệu quả;
- transaction lâu;
- autovacuum chậm;
- fillfactor hoặc workload;
- bulk delete phân tán.

Hậu quả:

- table scan đọc nhiều page;
- cache hit ratio kém;
- backup lớn;
- VACUUM tốn hơn;
- planner và I/O xử lý nhiều dữ liệu phụ.

n_dead_tup chỉ là một tín hiệu, không phải con số bloat chính xác. Cần dùng extension hoặc phương pháp đo phù hợp để ước lượng thật.

Theo dõi kích thước:

~~~sql
SELECT
    relname,
    pg_size_pretty(pg_table_size(relid)) AS table_size,
    pg_size_pretty(pg_indexes_size(relid)) AS indexes_size,
    pg_size_pretty(pg_total_relation_size(relid)) AS total_size,
    n_live_tup,
    n_dead_tup
FROM pg_stat_user_tables
ORDER BY pg_total_relation_size(relid) DESC;
~~~

---

## 15. Index bloat

Index cũng có thể phình do delete, update indexed column và page split.

Theo dõi kích thước và usage:

~~~sql
SELECT
    relname,
    indexrelname,
    pg_size_pretty(pg_relation_size(indexrelid)) AS index_size,
    idx_scan,
    idx_tup_read,
    idx_tup_fetch
FROM pg_stat_user_indexes
ORDER BY pg_relation_size(indexrelid) DESC;
~~~

Kích thước lớn không chứng minh bloat. Cần xem:

- số row thật;
- index key distribution;
- write pattern;
- page density;
- index usage;
- công cụ đo bloat;
- khả năng rebuild online.

REINDEX CONCURRENTLY:

~~~sql
REINDEX INDEX CONCURRENTLY events_created_idx;
~~~

Rebuild cần thêm disk và thời gian. Không chạy hàng loạt không có maintenance plan.

---

## 16. VACUUM và index

VACUUM thường có thể dọn index entries liên quan dead tuple. Với nhiều index:

- VACUUM phải xử lý thêm index;
- index bloat làm vacuum nặng;
- write-heavy table tạo nhiều dead index entries;
- index thừa làm maintenance tốn hơn.

Khi table update-heavy, review cả:

- số lượng index;
- index có sử dụng không;
- indexed columns có thay đổi thường xuyên không;
- có HOT update không;
- autovacuum có theo kịp không.

Đừng thêm index để chữa một query mà bỏ qua write amplification và vacuum cost.

---

## 17. VACUUM options

Xem help:

~~~sql
VACUUM (VERBOSE, ANALYZE) events;
~~~

Một số option:

- VERBOSE: log chi tiết;
- ANALYZE: cập nhật statistics;
- FULL: rewrite table;
- FREEZE: freeze row đủ điều kiện;
- DISABLE_PAGE_SKIPPING: bỏ qua visibility optimization, chỉ dùng tình huống đặc biệt;
- INDEX_CLEANUP: điều khiển xử lý index trong trường hợp phù hợp.

Ví dụ maintenance thường:

~~~sql
VACUUM (ANALYZE, VERBOSE) events;
~~~

Không dùng DISABLE_PAGE_SKIPPING hoặc FULL tùy tiện. Đọc lock, I/O và mục đích trước khi chạy.

---

## 18. Lab UPDATE và DELETE

Tạo table lab:

~~~sql
CREATE TABLE vacuum_lab (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id bigint NOT NULL,
    status text NOT NULL,
    payload text NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX vacuum_lab_account_idx
ON vacuum_lab (account_id);

INSERT INTO vacuum_lab (account_id, status, payload)
SELECT
    1 + (g % 1000),
    'active',
    repeat('x', 200)
FROM generate_series(1, 500000) AS g;

ANALYZE vacuum_lab;
~~~

Baseline:

~~~sql
SELECT
    relname,
    pg_size_pretty(pg_table_size(relid)) AS table_size,
    n_live_tup,
    n_dead_tup
FROM pg_stat_user_tables
WHERE relname = 'vacuum_lab';
~~~

UPDATE nhiều row:

~~~sql
UPDATE vacuum_lab
SET status = 'processed',
    updated_at = now()
WHERE id % 3 = 0;
~~~

DELETE nhiều row:

~~~sql
DELETE FROM vacuum_lab
WHERE id % 10 = 0;
~~~

Theo dõi:

~~~sql
SELECT
    relname,
    n_live_tup,
    n_dead_tup,
    last_autovacuum,
    autovacuum_count
FROM pg_stat_user_tables
WHERE relname = 'vacuum_lab';
~~~

Chạy vacuum:

~~~sql
VACUUM (VERBOSE, ANALYZE) vacuum_lab;
~~~

So sánh size, n_dead_tup và plan query trước/sau. Table file có thể không giảm vì VACUUM thường tái sử dụng space bên trong.

---

## 19. Lab VACUUM FULL trong database riêng

Chỉ chạy trên lab database:

~~~sql
SELECT pg_size_pretty(pg_table_size('vacuum_lab'::regclass));

VACUUM FULL vacuum_lab;

SELECT pg_size_pretty(pg_table_size('vacuum_lab'::regclass));
~~~

Quan sát:

- thời gian chạy;
- lock;
- kích thước trước/sau;
- disk tạm;
- query khác có bị block không.

Không chạy VACUUM FULL trên table production lớn nếu chưa có lock và disk plan.

---

## 20. Lab HOT update

Tạo hai table tương tự:

~~~sql
CREATE TABLE hot_candidate (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    payload text NOT NULL,
    updated_at timestamptz NOT NULL
) WITH (fillfactor = 70);

CREATE TABLE non_hot_candidate (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    payload text NOT NULL,
    updated_at timestamptz NOT NULL
);

CREATE INDEX non_hot_updated_idx
ON non_hot_candidate (updated_at);
~~~

Seed và update updated_at nhiều lần. Theo dõi pg_stat_user_tables hoặc pg_stat_all_tables và so sánh kích thước/index maintenance. HOT behavior phụ thuộc page space, tuple size và pattern; không kết luận chỉ vì column không có index.

---

## 21. Tuning autovacuum theo table

Table nóng:

~~~sql
ALTER TABLE vacuum_lab SET (
    autovacuum_vacuum_scale_factor = 0.02,
    autovacuum_analyze_scale_factor = 0.01,
    autovacuum_vacuum_threshold = 1000,
    autovacuum_analyze_threshold = 1000,
    autovacuum_vacuum_cost_limit = 2000
);
~~~

Sau đó theo dõi:

~~~sql
SELECT
    relname,
    n_dead_tup,
    n_mod_since_analyze,
    last_autovacuum,
    autovacuum_count,
    last_autoanalyze,
    autoanalyze_count
FROM pg_stat_user_tables
WHERE relname = 'vacuum_lab';
~~~

Không tăng cost limit hoặc giảm scale factor mà chưa xem CPU/I/O. Maintenance quá aggressive cũng có thể cạnh tranh với workload chính.

---

## 22. Lock và VACUUM

VACUUM thường cần lock phù hợp nhưng thường cho phép đọc/ghi đồng thời hơn VACUUM FULL. DDL hoặc transaction khác có thể làm maintenance chờ.

Trong lúc vacuum chờ, xem:

~~~sql
SELECT
    pid,
    relation::regclass AS relation_name,
    mode,
    granted
FROM pg_locks
WHERE relation IS NOT NULL
ORDER BY relation, granted;
~~~

Kết hợp với pg_stat_activity:

~~~sql
SELECT
    pid,
    state,
    wait_event_type,
    wait_event,
    xact_start,
    query
FROM pg_stat_activity
WHERE wait_event_type IS NOT NULL
ORDER BY xact_start NULLS LAST;
~~~

Maintenance cũng cần timeout và change window. Đừng chạy VACUUM FULL hoặc REINDEX lớn mà không biết blocker.

---

## 23. Monitoring checklist

Theo dõi hàng ngày hoặc theo workload:

- n_dead_tup;
- n_live_tup;
- n_mod_since_analyze;
- last_autovacuum;
- last_autoanalyze;
- autovacuum_count;
- autoanalyze_count;
- transaction age;
- age(relfrozenxid);
- table size;
- index size;
- query latency;
- lock wait;
- disk usage;
- vacuum progress.

Alert nên dựa trên trend, không chỉ một snapshot. Table 100 triệu row có 1 triệu dead tuple khác table 1 triệu row có 1 triệu dead tuple.

---

## 24. Lỗi thường gặp

### Tắt autovacuum để giảm CPU

Có thể làm dead tuple, bloat và wraparound risk tăng. Tuning per-table trước khi disable.

### Dùng VACUUM FULL định kỳ

Lock mạnh và rewrite table. Dùng khi có maintenance plan, không phải cron mặc định.

### Nghĩ VACUUM thường trả space cho OS

VACUUM thường chủ yếu làm space tái sử dụng nội bộ. Muốn shrink file thường cần rewrite như VACUUM FULL hoặc công cụ online phù hợp.

### Bỏ qua transaction lâu

Transaction lâu ngăn cleanup snapshot cũ. Kiểm tra xact_start và idle in transaction.

### Nâng fillfactor mọi bảng

Fillfactor thấp tốn space và giảm density. Chỉ dùng cho update-heavy table có evidence HOT.

### Nghĩ n_dead_tup là bloat chính xác

Đó là estimate/statistic. Cần đo bloat thực tế và xem workload.

### Thêm index rồi quên vacuum cost

Mỗi index làm maintenance và write nặng hơn.

### Không chạy ANALYZE

VACUUM không tự có nghĩa statistics mới nếu không dùng VACUUM ANALYZE hoặc autoanalyze phù hợp.

### Chạy maintenance trên production mà không kiểm tra lock

Luôn xem lock, disk, thời gian và rollback/abort plan.

---

## 25. Runbook xử lý table bloat

1. Xác định table có vấn đề bằng metric và query latency.
2. Kiểm tra n_dead_tup và dead tuple trend.
3. Kiểm tra transaction lâu và blocker.
4. Kiểm tra autovacuum có chạy không.
5. Chạy VACUUM thường nếu chỉ cần cleanup/reuse.
6. Chạy ANALYZE nếu estimate cũ.
7. Đánh giá index bloat riêng.
8. Cân nhắc REINDEX CONCURRENTLY cho index cụ thể.
9. Cân nhắc VACUUM FULL hoặc rewrite online trong maintenance window.
10. Đo lại table size, query, lock và disk.
11. Tuning autovacuum per-table nếu workload lặp lại.
12. Ghi nguyên nhân và cách phòng tái diễn.

Không bắt đầu bằng VACUUM FULL chỉ vì table lớn. Tìm nguyên nhân trước.

---

## 26. Bài tập thực hành

### Bài 1 — Dead tuple

Tạo table lab, UPDATE/DELETE nhiều row, theo dõi n_dead_tup trước và sau VACUUM.

### Bài 2 — VACUUM và VACUUM FULL

So sánh kích thước table, lock và thời gian giữa hai lệnh trên database lab.

### Bài 3 — ANALYZE

Seed dữ liệu lệch, đo EXPLAIN trước/sau ANALYZE và ghi estimated rows.

### Bài 4 — Autovacuum

Đặt per-table scale factor thấp, tạo workload update và theo dõi autovacuum_count.

### Bài 5 — Transaction lâu

Mở transaction giữ snapshot, UPDATE/DELETE ở session khác, chạy VACUUM và quan sát cleanup bị ảnh hưởng thế nào.

### Bài 6 — XID age

Theo dõi age(datfrozenxid) ở database và table. Viết alert threshold và runbook.

### Bài 7 — HOT update

So sánh table có indexed updated_at và không có index. Ghi tác động lên size và update workload.

### Bài 8 — Fillfactor

Tạo hai table với fillfactor khác nhau, chạy update cùng pattern và so sánh bloat/HOT.

### Bài 9 — Index maintenance

Thêm nhiều index, chạy write benchmark, so sánh thời gian và kích thước index.

### Bài 10 — Runbook

Viết runbook xử lý một table bloat trong production có giới hạn lock và downtime.

---

## 27. Tiêu chí hoàn thành Tuần 9

Bạn hoàn thành bài học khi có thể:

- Giải thích dead tuple do MVCC.
- Phân biệt VACUUM, VACUUM FULL và ANALYZE.
- Hiểu autovacuum launcher, worker và ngưỡng trigger.
- Theo dõi pg_stat_user_tables và pg_stat_progress_vacuum.
- Nhận biết transaction lâu cản VACUUM.
- Theo dõi transaction ID age và wraparound risk.
- Hiểu visibility map và liên hệ với index-only scan.
- Giải thích HOT update.
- Chọn fillfactor có cơ sở.
- Nhận biết table bloat và index bloat.
- Cấu hình autovacuum riêng cho bảng nóng.
- Không dùng VACUUM FULL như giải pháp mặc định.
- Viết runbook maintenance có lock, disk và rollback plan.
- Đo tác động trước/sau bằng statistics, size và query latency.

## Tóm tắt cần nhớ

~~~text
UPDATE và DELETE tạo dead tuple do MVCC.
VACUUM thường dọn tuple và tái sử dụng space trong table.
VACUUM FULL rewrite table, cần lock mạnh và disk tạm.
ANALYZE cập nhật statistics; VACUUM không đồng nghĩa ANALYZE.
Autovacuum là hệ thống bảo vệ quan trọng, không tắt tùy tiện.
Transaction lâu giữ snapshot và ngăn cleanup.
Freeze giúp tránh transaction ID wraparound.
Visibility map hỗ trợ VACUUM và index-only scan.
HOT update giảm index write khi column indexed không thay đổi.
Fillfactor để lại space cho update nhưng làm table lớn hơn.
Bloat phải được đo, không suy ra chỉ từ một metric.
Maintenance cần theo dõi lock, disk, thời gian và tác dụng phụ.
~~~

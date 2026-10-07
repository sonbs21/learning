# Tuần 10 nâng cao: Server configuration và resource control trong PostgreSQL

> Mục tiêu: hiểu các setting ảnh hưởng tới memory, connection, WAL, checkpoint, parallel query và timeout; biết thay đổi setting an toàn; kiểm soát query/resource runaway; và benchmark bằng pgbench mà không tối ưu theo cảm giác.

Cấu hình PostgreSQL không có một bộ số đúng cho mọi server. Một setting tốt phụ thuộc vào:

~~~text
RAM
CPU
disk và I/O
số connection thật
workload đọc/ghi
query shape
độ dài transaction
SLA và giới hạn chi phí
~~~

Nguyên tắc:

1. Đo baseline trước.
2. Đổi một setting mỗi lần.
3. Ghi cấu hình, workload và kết quả.
4. Kiểm tra tác dụng phụ.
5. Có rollback plan.
6. Không copy cấu hình trên Internet mà chưa hiểu máy và workload.

---

## 1. Xem cấu hình hiện tại

Xem một setting:

~~~sql
SHOW shared_buffers;
SHOW work_mem;
SHOW max_connections;
~~~

Xem nguồn và mô tả:

~~~sql
SELECT
    name,
    setting,
    unit,
    context,
    vartype,
    boot_val,
    reset_val,
    source,
    pending_restart,
    short_desc
FROM pg_settings
WHERE name IN (
    'shared_buffers',
    'work_mem',
    'maintenance_work_mem',
    'effective_cache_size',
    'max_connections',
    'statement_timeout',
    'lock_timeout',
    'idle_in_transaction_session_timeout',
    'temp_file_limit'
);
~~~

Các context quan trọng:

- user: đổi ở session/user/database;
- superuser: cần quyền cao;
- sighup: đổi config rồi reload;
- postmaster: cần restart;
- backend: được áp dụng khi session mới tạo;
- internal: không đổi trực tiếp.

Kiểm tra pending restart:

~~~sql
SELECT
    name,
    setting,
    pending_restart
FROM pg_settings
WHERE pending_restart;
~~~

Đừng chỉ nhìn setting value. Context và pending_restart quyết định thay đổi có hiệu lực khi nào.

---

## 2. SET, ALTER DATABASE, ALTER ROLE và file config

Thay đổi trong session:

~~~sql
SET LOCAL work_mem = '32MB';
~~~

SET LOCAL chỉ có hiệu lực trong transaction:

~~~sql
BEGIN;

SET LOCAL work_mem = '32MB';

SELECT ...;

COMMIT;
~~~

SET trong session:

~~~sql
SET statement_timeout = '5s';
~~~

Thiết lập theo database:

~~~sql
ALTER DATABASE app_db
SET statement_timeout = '30s';
~~~

Theo role:

~~~sql
ALTER ROLE app_runtime
SET statement_timeout = '5s';
~~~

Các tầng cấu hình có precedence. Cần kiểm tra SHOW trong đúng role/database/application connection.

ALTER SYSTEM ghi vào postgresql.auto.conf:

~~~sql
ALTER SYSTEM SET shared_buffers = '4GB';
~~~

ALTER SYSTEM là thay đổi server-wide và cần quyền cao. Roadmap lab không yêu cầu chạy trên dữ liệu người dùng. Dùng môi trường test, review thay đổi, biết setting cần reload/restart và có rollback:

~~~sql
ALTER SYSTEM RESET shared_buffers;
~~~

Trong production, configuration change cần change record, backup config, kế hoạch apply và rollback.

---

## 3. shared_buffers

shared_buffers là vùng cache chính PostgreSQL dùng để giữ page table và index.

Nó không phải toàn bộ cache của hệ thống. OS page cache vẫn tồn tại.

shared_buffers quá thấp:

- PostgreSQL phải đọc storage nhiều hơn;
- cache hit có thể kém;
- workload đọc lặp lại tốn I/O.

shared_buffers quá cao:

- giảm RAM cho OS cache;
- giảm RAM cho connection và work_mem;
- tăng áp lực memory;
- không tự động làm mọi query nhanh.

Xem cache hit ở mức database:

~~~sql
SELECT
    datname,
    blks_hit,
    blks_read,
    round(
        100.0 * blks_hit / NULLIF(blks_hit + blks_read, 0),
        2
    ) AS cache_hit_percent
FROM pg_stat_database
WHERE datname = current_database();
~~~

Cache hit cao không tự chứng minh latency tốt; query vẫn có thể chậm do CPU, lock, sort, bloat hoặc plan sai.

Thay đổi shared_buffers thường cần restart. Không đổi trong production mà chưa có maintenance window.

---

## 4. OS cache và effective_cache_size

effective_cache_size không cấp phát memory. Nó là gợi ý cho planner về tổng cache PostgreSQL và OS có thể dùng cho query.

~~~sql
SHOW effective_cache_size;
~~~

Nếu đặt quá thấp, planner có thể đánh giá index scan kém hấp dẫn. Nếu đặt quá cao, planner có thể quá lạc quan về cache.

effective_cache_size nên dựa trên:

- RAM;
- shared_buffers;
- memory của hệ điều hành;
- workload khác;
- container limit;
- connection và process memory.

Không hiểu effective_cache_size là cache PostgreSQL rồi cộng thêm vào RAM. Đây là estimate cho planner, không phải allocation.

---

## 5. work_mem

work_mem là memory tối đa cho một operation như:

- sort;
- hash join;
- hash aggregate;
- materialize trong một số trường hợp.

Quan trọng: work_mem áp dụng cho mỗi operation, có thể cho mỗi worker và mỗi connection.

Một query có nhiều sort/hash có thể dùng nhiều lần work_mem:

~~~text
memory potential ≈ connections × operations × workers × work_mem
~~~

Đây là mô hình cảnh báo, không phải công thức chính xác trong mọi plan. Đừng đặt work_mem rất lớn chỉ vì một query cần sort.

Kiểm tra query spill bằng EXPLAIN:

~~~sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT
    customer_id,
    COUNT(*)
FROM orders
GROUP BY customer_id
ORDER BY COUNT(*) DESC;
~~~

Nếu sort/hash vượt work_mem, PostgreSQL có thể dùng temporary file. Tăng work_mem session-local cho một job/report:

~~~sql
BEGIN;

SET LOCAL work_mem = '128MB';

SELECT ...;

COMMIT;
~~~

Đây thường an toàn hơn tăng global work_mem cho mọi connection.

---

## 6. maintenance_work_mem

maintenance_work_mem dùng cho maintenance như:

- VACUUM;
- CREATE INDEX;
- ALTER TABLE thêm index/constraint;
- một số thao tác maintenance khác.

~~~sql
SHOW maintenance_work_mem;
~~~

Có thể đặt cao hơn work_mem vì maintenance thường ít operation song song hơn, nhưng autovacuum có setting riêng:

~~~sql
SHOW autovacuum_work_mem;
~~~

Nếu autovacuum_work_mem là -1, nó có thể dùng maintenance_work_mem tùy cấu hình. Cần xem tổng worker và memory potential trước khi tăng.

CREATE INDEX lớn có thể hưởng lợi từ maintenance_work_mem, nhưng cần:

- disk tạm;
- lock plan;
- concurrency;
- thời gian build;
- rollback hoặc cleanup nếu lỗi.

---

## 7. max_connections và connection pool

max_connections là số connection backend tối đa, không phải throughput.

Tăng max_connections quá cao có thể:

- tăng memory potential;
- tăng context switching;
- tăng contention;
- làm cache locality kém;
- khiến database chậm hơn;
- làm lỗi lan rộng khi application connection storm.

Xem connection:

~~~sql
SELECT
    state,
    count(*)
FROM pg_stat_activity
GROUP BY state
ORDER BY state;
~~~

Xem tổng và giới hạn:

~~~sql
SELECT
    count(*) AS current_connections,
    current_setting('max_connections')::integer AS max_connections
FROM pg_stat_activity;
~~~

Dùng application pool hoặc PgBouncer để giới hạn connection thật vào database. Pool size cần dựa trên:

- CPU;
- query latency;
- số database;
- workload;
- số instance application;
- background jobs;
- reserved admin connections.

Cẩn thận tổng pool size:

~~~text
app instance count × pool size
+ workers
+ admin
+ monitoring
< max_connections
~~~

max_connections cao không sửa query chậm. Nó chỉ cho phép nhiều session cùng chờ hoặc tranh tài nguyên hơn.

---

## 8. statement_timeout

statement_timeout giới hạn thời gian một statement được phép chạy:

~~~sql
SET LOCAL statement_timeout = '5s';
~~~

Dùng để bảo vệ API khỏi query runaway:

~~~sql
ALTER ROLE app_runtime
SET statement_timeout = '5s';
~~~

Không đặt một timeout chung cho mọi workload nếu reporting cần chạy lâu hơn. Có thể tách role:

~~~text
app_runtime → timeout ngắn
reporting    → timeout dài hơn, quyền riêng
migration    → timeout theo change window
~~~

Timeout có thể hủy statement nhưng transaction vẫn cần được application xử lý. Sau lỗi statement trong transaction, có thể cần ROLLBACK.

---

## 9. lock_timeout

lock_timeout giới hạn thời gian chờ lock:

~~~sql
SET LOCAL lock_timeout = '1s';
~~~

Phù hợp với:

- migration online cần fail nhanh;
- API không muốn chờ lock lâu;
- job có thể retry;
- thao tác maintenance có window rõ.

lock_timeout khác statement_timeout:

~~~text
lock_timeout: thời gian chờ lock
statement_timeout: tổng thời gian statement
~~~

Nếu lock_timeout lớn hơn statement_timeout, statement_timeout có thể kết thúc trước. Chọn giá trị theo SLA và retry policy.

---

## 10. idle_in_transaction_session_timeout

Session idle trong transaction giữ snapshot và có thể giữ lock:

~~~text
BEGIN
SELECT ...
application mất kết nối logic
session không COMMIT/ROLLBACK
~~~

Đặt timeout:

~~~sql
ALTER ROLE app_runtime
SET idle_in_transaction_session_timeout = '30s';
~~~

Theo dõi:

~~~sql
SELECT
    pid,
    usename,
    state,
    xact_start,
    query_start,
    now() - xact_start AS transaction_age,
    now() - state_change AS state_age,
    query
FROM pg_stat_activity
WHERE state = 'idle in transaction'
ORDER BY xact_start;
~~~

Timeout không thay thế việc sửa code. Nó là safety net để giải phóng session bị bỏ quên.

---

## 11. temp_file_limit

Query có thể tạo temporary file khi sort, hash hoặc materialize không vừa memory.

Giới hạn theo session:

~~~sql
SET LOCAL temp_file_limit = '1GB';
~~~

Theo role:

~~~sql
ALTER ROLE app_runtime
SET temp_file_limit = '512MB';
~~~

Theo dõi temp file qua log hoặc EXPLAIN BUFFERS, tùy version và monitoring setup.

temp_file_limit bảo vệ disk nhưng có thể làm query lỗi nếu query thật sự cần nhiều temporary space. Cần:

- log lỗi;
- tối ưu query hoặc index;
- kiểm tra work_mem;
- giới hạn role phù hợp;
- không đặt quá thấp khiến các query hợp lệ luôn fail.

---

## 12. WAL

WAL ghi các thay đổi trước khi data page được ghi ổn định. WAL phục vụ:

- crash recovery;
- replication;
- point-in-time recovery;
- durability.

Theo dõi database:

~~~sql
SELECT
    datname,
    tup_inserted,
    tup_updated,
    tup_deleted,
    temp_files,
    temp_bytes
FROM pg_stat_database
WHERE datname = current_database();
~~~

Một số setting liên quan WAL:

- wal_level;
- wal_buffers;
- max_wal_size;
- min_wal_size;
- checkpoint_timeout;
- checkpoint_completion_target;
- synchronous_commit;
- wal_compression;
- max_wal_senders;
- max_replication_slots.

Không đổi wal_level hoặc replication-related setting mà chưa hiểu backup/replication topology.

---

## 13. Checkpoint

Checkpoint flush dirty pages và tạo mốc recovery. Checkpoint quá thường xuyên có thể tạo I/O spike; checkpoint quá thưa có thể làm recovery lâu và WAL tăng.

Theo dõi checkpoint:

~~~sql
SELECT
    checkpoints_timed,
    checkpoints_req,
    checkpoint_write_time,
    checkpoint_sync_time,
    buffers_checkpoint,
    buffers_clean,
    maxwritten_clean
FROM pg_stat_bgwriter;
~~~

Ý nghĩa:

- checkpoints_timed: checkpoint theo thời gian;
- checkpoints_req: checkpoint bị yêu cầu do WAL;
- checkpoint_write_time: thời gian ghi;
- checkpoint_sync_time: thời gian sync;
- buffers_checkpoint: buffer ghi trong checkpoint.

Nếu checkpoints_req cao, max_wal_size có thể quá thấp hoặc workload tạo WAL lớn. Không chỉ tăng setting mà không đo disk, recovery time và replication.

checkpoint_completion_target phân bổ ghi checkpoint trong khoảng checkpoint để giảm spike, nhưng không giải quyết storage chậm vô hạn.

---

## 14. max_wal_size và WAL disk budget

max_wal_size là target mềm để kích hoạt checkpoint theo WAL. WAL thực tế còn phụ thuộc replication slot, archiving, checkpoint và workload.

Cần theo dõi:

- disk trống;
- WAL generation;
- replication slot giữ WAL;
- archive lag;
- checkpoint frequency;
- recovery time.

Replication slot bị bỏ quên có thể giữ WAL lâu dù max_wal_size đã cấu hình. Resource control phải xem toàn bộ pipeline, không chỉ một setting.

---

## 15. synchronous_commit

synchronous_commit điều khiển mức chờ WAL flush/replication trước khi COMMIT báo thành công.

Các lựa chọn có trade-off durability và latency. Không tắt tùy tiện cho transaction nghiệp vụ quan trọng.

Có thể dùng session-local cho workload chấp nhận rủi ro mất vài transaction khi crash:

~~~sql
SET LOCAL synchronous_commit = off;
~~~

Chỉ dùng khi:

- dữ liệu có thể tạo lại;
- chấp nhận durability yếu hơn;
- đã được owner nghiệp vụ phê duyệt;
- có tài liệu rõ.

Không dùng để chữa latency do lock, query chậm hoặc storage bottleneck mà chưa điều tra.

---

## 16. Parallel query

PostgreSQL có thể dùng parallel worker cho scan, join hoặc aggregate lớn.

Setting liên quan:

- max_worker_processes;
- max_parallel_workers;
- max_parallel_workers_per_gather;
- min_parallel_table_scan_size;
- min_parallel_index_scan_size;
- parallel_setup_cost;
- parallel_tuple_cost.

Xem plan:

~~~sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT
    date_trunc('hour', created_at) AS hour,
    count(*)
FROM events
GROUP BY date_trunc('hour', created_at);
~~~

Plan có thể chứa:

~~~text
Gather
  Workers Planned
  Workers Launched
  Parallel Seq Scan
  Partial Aggregate
  Finalize Aggregate
~~~

Parallel có overhead:

- khởi tạo worker;
- phối hợp;
- chia sẻ CPU;
- memory mỗi worker;
- cạnh tranh với workload khác.

Query nhỏ không nên ép parallel. Theo dõi workers planned so với workers launched; nếu không đủ worker, hệ thống có thể thiếu tài nguyên hoặc bị giới hạn.

---

## 17. work_mem và parallel memory

Một query parallel có thể có operation riêng ở nhiều worker. Hash hoặc sort memory potential có thể tăng đáng kể.

Không suy ra rằng tăng work_mem global sẽ luôn tốt. Thử session-local:

~~~sql
BEGIN;

SET LOCAL work_mem = '64MB';

EXPLAIN (ANALYZE, BUFFERS)
SELECT
    category,
    count(*)
FROM books
GROUP BY category
ORDER BY count(*) DESC;

ROLLBACK;
~~~

So sánh:

- execution time;
- temp read/write;
- memory;
- concurrency khi nhiều query chạy cùng lúc.

Một query nhanh hơn đơn lẻ có thể làm hệ thống tệ hơn khi chạy 100 query đồng thời.

---

## 18. OS cache và storage

PostgreSQL sử dụng shared_buffers, nhưng OS cũng cache file. Effective_cache_size chỉ là planner hint.

Bottleneck có thể nằm ở:

- storage latency;
- IOPS;
- throughput;
- fsync;
- checkpoint write;
- page cache;
- noisy neighbor;
- container cgroup limit.

Khi benchmark, ghi:

~~~text
CPU
RAM
container limit
disk type
filesystem
cache warm/cold
concurrency
dataset size
PostgreSQL version
~~~

Không kết luận setting memory tốt/xấu khi server test khác production về disk và RAM.

---

## 19. pgbench lab

Khởi tạo database lab:

~~~bash
createdb pg_lab

pgbench -i -s 20 pg_lab
~~~

Baseline read/write:

~~~bash
pgbench \
  -c 10 \
  -j 2 \
  -T 60 \
  --protocol=prepared \
  pg_lab
~~~

Ghi:

- transactions per second;
- latency average;
- latency stddev;
- failures;
- CPU;
- disk I/O;
- connections.

Chạy custom script đọc:

~~~sql
\set aid random(1, 100000)
SELECT abalance
FROM pgbench_accounts
WHERE aid = :aid;
~~~

Lưu thành pgbench-read.sql:

~~~bash
pgbench \
  -c 20 \
  -j 4 \
  -T 60 \
  -f pgbench-read.sql \
  pg_lab
~~~

Không chạy benchmark trên database người dùng. Tạo database lab riêng.

---

## 20. Quy tắc đổi từng setting

Không đổi shared_buffers, work_mem, max_connections, WAL và parallel settings cùng lúc. Quy trình:

1. ghi baseline;
2. chọn một hypothesis;
3. đổi một setting;
4. restart/reload đúng cách;
5. chạy cùng workload;
6. lặp đủ lần;
7. so p50/p95/p99, TPS, CPU, I/O, temp và errors;
8. kiểm tra tác dụng phụ;
9. rollback nếu không tốt;
10. ghi kết luận.

Ví dụ hypothesis:

~~~text
Sort lớn đang spill temp file.
Tăng work_mem session-local từ 4MB lên 64MB sẽ giảm temp write
nhưng không vượt memory budget khi 20 query chạy đồng thời.
~~~

Đây là hypothesis có thể đo, không phải “tăng memory chắc chắn nhanh”.

---

## 21. Resource group theo workload

Tách workload bằng role/database/pool:

~~~text
app_runtime
- connection pool nhỏ
- statement_timeout ngắn
- lock_timeout ngắn
- temp_file_limit vừa phải
- quyền tối thiểu

reporting
- pool riêng
- timeout dài hơn
- có thể work_mem session cao hơn
- chạy ngoài peak nếu có thể

maintenance
- connection riêng
- maintenance_work_mem phù hợp
- lock timeout theo change window
~~~

Không để reporting query cạnh tranh trực tiếp với API transaction nếu có thể tách pool hoặc lịch chạy.

---

## 22. Đo timeout và error

Theo dõi:

- số statement timeout;
- số lock timeout;
- số deadlock;
- temp file;
- connection refused;
- out of memory;
- checkpoint spikes;
- replication lag.

Timeout là một phần của API contract. Nếu database timeout sau 5 giây, application phải biết trả lỗi, retry hay fallback. Không retry mọi timeout vì có thể tạo thundering herd.

---

## 23. Cấu hình an toàn và rollback

Trước thay đổi:

~~~sql
SELECT
    name,
    setting,
    source,
    pending_restart
FROM pg_settings
WHERE name IN (
    'shared_buffers',
    'work_mem',
    'max_connections',
    'statement_timeout'
);
~~~

Ghi snapshot cấu hình vào change record. Sau thay đổi:

~~~sql
SELECT
    name,
    setting,
    source,
    pending_restart
FROM pg_settings
WHERE name IN (
    'shared_buffers',
    'work_mem',
    'max_connections',
    'statement_timeout'
);
~~~

Rollback session:

~~~sql
RESET work_mem;
RESET statement_timeout;
~~~

Rollback role:

~~~sql
ALTER ROLE app_runtime
RESET statement_timeout;
~~~

Rollback ALTER SYSTEM:

~~~sql
ALTER SYSTEM RESET work_mem;
~~~

Sau ALTER SYSTEM cần reload/restart tùy context. Kiểm tra pending_restart và test bằng session mới.

---

## 24. Lỗi thường gặp

### Tăng max_connections để chữa connection wait

Có thể làm memory và contention tệ hơn. Dùng pool và tìm connection leak.

### Tăng work_mem global quá lớn

Nhiều operation và worker có thể nhân memory.

### Nhầm effective_cache_size là memory allocation

Đây là planner hint, không cấp phát RAM.

### Tăng shared_buffers mà bỏ qua OS cache

Database cần cả shared buffers và OS/application memory.

### Tắt statement_timeout

Một query runaway có thể chiếm connection và CPU rất lâu. Dùng timeout theo workload.

### Dùng VACUUM FULL hoặc CREATE INDEX lớn trong peak

Maintenance có lock, I/O và disk cost. Lập change window.

### Đổi nhiều setting cùng lúc

Không biết thay đổi nào tạo tác dụng hoặc regression.

### Benchmark chỉ một lần

Cache, background activity và concurrency làm kết quả nhiễu.

### Ép parallel cho query nhỏ

Parallel setup cost có thể lớn hơn lợi ích.

### Không giới hạn temp file

Một query sort/hash có thể làm đầy disk.

### SET trong một session rồi tưởng toàn server đổi

SET thường chỉ áp dụng session; SET LOCAL chỉ trong transaction.

---

## 25. Lab resource control hoàn chỉnh

### Bước 1: baseline

~~~sql
SHOW shared_buffers;
SHOW work_mem;
SHOW maintenance_work_mem;
SHOW effective_cache_size;
SHOW max_connections;
SHOW statement_timeout;
SHOW lock_timeout;
SHOW temp_file_limit;
~~~

Chạy pgbench 3 lần và ghi p50/p95, TPS, CPU và I/O.

### Bước 2: query sort

~~~sql
BEGIN;

SET LOCAL work_mem = '4MB';

EXPLAIN (ANALYZE, BUFFERS)
SELECT
    aid,
    sum(abalance)
FROM pgbench_accounts
GROUP BY aid
ORDER BY sum(abalance) DESC;

ROLLBACK;
~~~

Lặp với 64MB. Kiểm tra temp file và thời gian.

### Bước 3: connection pressure

Tăng concurrency pgbench trong lab, không tăng max_connections ngay. Theo dõi latency và connection count.

### Bước 4: timeout

~~~sql
BEGIN;

SET LOCAL statement_timeout = '1s';

SELECT pg_sleep(2);

ROLLBACK;
~~~

Lock timeout cần hai session giữ và chờ lock.

### Bước 5: parallel

Chạy query aggregate lớn, ghi workers planned/launched và execution time. Đổi một setting parallel ở lab, đo lại rồi reset.

### Bước 6: rollback

Reset tất cả SET/role setting và xác nhận giá trị trở về baseline.

---

## 26. Bài tập thực hành

### Bài 1 — Configuration inventory

Lấy snapshot các setting quan trọng và ghi context, source, pending_restart.

### Bài 2 — work_mem

Chạy query sort/hash với 4MB và 64MB. So sánh temp file, buffers và latency.

### Bài 3 — max_connections

Mô phỏng connection storm bằng pgbench nhưng không đổi max_connections. Đề xuất pool size và backpressure.

### Bài 4 — Timeout

Thiết lập statement_timeout, lock_timeout và idle_in_transaction_session_timeout theo từng role.

### Bài 5 — Checkpoint

Theo dõi checkpoints_timed, checkpoints_req và checkpoint_write_time trước/sau workload ghi.

### Bài 6 — Parallel query

Chạy aggregate lớn, đọc Gather và Workers Planned/Launched. Đo concurrency impact.

### Bài 7 — temp_file_limit

Tạo query sort lớn, đặt temp_file_limit thấp trong session, ghi lỗi và cách xử lý.

### Bài 8 — pgbench report

Chạy baseline và ít nhất hai thay đổi, mỗi lần một setting. Báo cáo TPS, latency, CPU, I/O và rollback.

### Bài 9 — Resource profiles

Thiết kế setting cho app_runtime, reporting và maintenance. Giải thích memory budget.

### Bài 10 — Runbook

Viết runbook xử lý query runaway, lock wait dài và connection pool cạn.

---

## 27. Tiêu chí hoàn thành Tuần 10

Bạn hoàn thành bài học khi có thể:

- Đọc setting, context, source và pending_restart.
- Phân biệt SET, SET LOCAL, ALTER ROLE, ALTER DATABASE và ALTER SYSTEM.
- Giải thích shared_buffers và OS cache.
- Dùng effective_cache_size đúng vai trò planner hint.
- Tính rủi ro memory của work_mem theo operation, connection và worker.
- Phân biệt maintenance_work_mem với autovacuum_work_mem.
- Thiết kế max_connections cùng connection pool.
- Dùng statement_timeout, lock_timeout và idle transaction timeout.
- Hiểu WAL và checkpoint ở mức vận hành.
- Đọc parallel query và workers.
- Giới hạn temp file.
- Benchmark bằng pgbench.
- Đổi một setting mỗi lần và đo tác dụng phụ.
- Viết rollback plan và resource profile theo workload.

## Tóm tắt cần nhớ

~~~text
Không có cấu hình PostgreSQL đúng cho mọi server.
shared_buffers là PostgreSQL cache; OS cache vẫn quan trọng.
effective_cache_size là planner hint, không cấp phát memory.
work_mem áp dụng theo operation và có thể nhân bởi connection/worker.
maintenance_work_mem dành cho maintenance; autovacuum có budget riêng.
max_connections cao không đồng nghĩa throughput cao.
Timeout là safety boundary cho statement, lock và idle transaction.
WAL và checkpoint cần đo cùng disk, replication và recovery.
Parallel query có overhead và tranh CPU/memory.
temp_file_limit bảo vệ disk nhưng có thể làm query lỗi.
Đổi một setting mỗi lần, benchmark cùng workload và có rollback.
Resource control tốt tách API, reporting và maintenance.
~~~

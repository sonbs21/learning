# Tuần 15 nâng cao: Monitoring và chẩn đoán PostgreSQL production

> Mục tiêu: đọc pg_stat_activity, pg_locks, pg_stat_database, pg_stat_user_tables, pg_stat_user_indexes và pg_stat_statements; cấu hình slow-query log; theo dõi checkpoint, WAL, connection, replication lag, disk và autovacuum; xây dựng dashboard cùng alert threshold có ngữ cảnh.

Monitoring không phải là tập hợp dashboard đẹp. Mục tiêu của monitoring là trả lời nhanh:

~~~text
Điều gì đang chậm?
Đang chờ tài nguyên nào?
Query, lock hay storage là nguyên nhân?
Dữ liệu đang tăng ở đâu?
Sự cố ảnh hưởng request nào?
Ngưỡng nào cần cảnh báo?
Hành động an toàn tiếp theo là gì?
~~~

Một số metric mô tả triệu chứng, không phải nguyên nhân. Connection count cao có thể là ứng dụng mở quá nhiều connection, nhưng cũng có thể do query giữ connection lâu. Cache hit ratio cao không chứng minh query nhanh nếu bảng nóng đã bị đọc nhiều lần hoặc storage vẫn là bottleneck. Luôn nối metric với query, thời gian và workload.

---

## 1. Mô hình monitoring tối thiểu

Chia observability thành bốn lớp:

| Lớp | Câu hỏi | Nguồn |
|---|---|---|
| Request | API nào chậm/lỗi? | application metrics, traces, logs |
| Database activity | session đang chạy/chờ gì? | pg_stat_activity, pg_locks |
| Database workload | query/table/index nào tốn? | pg_stat_statements, pg_stat_database |
| Host/storage | CPU, RAM, I/O, disk có nghẽn? | OS metrics, exporter |

Dashboard production tối thiểu theo roadmap:

- CPU, RAM, disk và I/O;
- active/idle connections;
- latency p50, p95, p99;
- top query theo total time;
- lock wait và deadlock count;
- cache hit ratio;
- WAL generation;
- replication lag;
- autovacuum activity.

Không phải metric nào cũng lấy trực tiếp từ PostgreSQL. p50/p95/p99 thường cần application histogram hoặc log aggregation; pg_stat_statements chủ yếu cung cấp tổng thời gian, số lần gọi và mean/min/max/stddev.

---

## 2. Nguyên tắc baseline trước khi alert

Trước khi đặt threshold:

1. ghi nhận workload bình thường;
2. đo theo giờ cao điểm và thấp điểm;
3. phân biệt primary/read replica;
4. lưu query rate cùng latency;
5. biết maintenance window;
6. biết giới hạn CPU/RAM/disk;
7. ghi version PostgreSQL và extension;
8. đặt ngưỡng theo SLO và xu hướng, không chỉ theo một snapshot.

Ví dụ cùng 70 phần trăm CPU:

- nếu latency vẫn trong SLO, có thể là trạng thái khỏe;
- nếu p95 tăng gấp ba, có thể đã thiếu CPU hoặc query plan thay đổi;
- nếu CPU thấp nhưng lock wait cao, tăng CPU không giải quyết được;
- nếu disk 90 phần trăm và WAL tăng, vấn đề là storage/WAL retention.

Alert tốt gồm:

~~~text
condition + duration + severity + owner + runbook + suppression
~~~

Ví dụ:

~~~text
replication replay lag > 60s trong 10 phút
severity: warning
owner: database on-call
runbook: kiểm tra pg_stat_replication, disk, slot, receiver
~~~

---

## 3. Chuẩn bị extension và quyền monitoring

Kết nối bằng role monitoring riêng, không dùng superuser cho dashboard:

~~~sql
CREATE ROLE monitoring NOLOGIN;
CREATE ROLE monitoring_login LOGIN
    PASSWORD 'secret-tu-secret-manager';

GRANT pg_monitor TO monitoring;
GRANT monitoring TO monitoring_login;
~~~

pg_monitor là predefined role cho các quyền quan sát thường cần. Một số thông tin nhạy cảm vẫn được che hoặc phụ thuộc privilege.

Cài pg_stat_statements:

~~~sql
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;
~~~

Trong một số triển khai, cần thêm vào shared_preload_libraries và restart:

~~~sql
SHOW shared_preload_libraries;
ALTER SYSTEM SET shared_preload_libraries = 'pg_stat_statements';
~~~

Kiểm tra setting:

~~~sql
SHOW pg_stat_statements.track;
SHOW pg_stat_statements.max;
SHOW pg_stat_statements.track_utility;
~~~

Không thay shared_preload_libraries tùy tiện nếu đã có extension khác. Ghi lại giá trị cũ, thay bằng configuration management và có restart plan.

---

## 4. pg_stat_activity: session đang làm gì?

Query cơ bản:

~~~sql
SELECT
    pid,
    usename,
    datname,
    client_addr,
    application_name,
    backend_type,
    state,
    wait_event_type,
    wait_event,
    query_start,
    xact_start,
    state_change,
    now() - query_start AS query_age,
    now() - xact_start AS xact_age,
    left(query, 500) AS query
FROM pg_stat_activity
WHERE datname = current_database()
ORDER BY query_start NULLS LAST;
~~~

Các field quan trọng:

- state: active, idle, idle in transaction, idle in transaction (aborted);
- wait_event_type: Lock, IO, Client, LWLock, BufferPin, Activity;
- wait_event: nguyên nhân đang chờ;
- query_start: query hiện tại bắt đầu khi nào;
- xact_start: transaction bắt đầu khi nào;
- backend_type: client backend, autovacuum worker, walsender, walreceiver;
- application_name: phân biệt service/job/replica.

Chỉ nhìn state=active chưa đủ. Active nhưng wait_event_type=Lock nghĩa là session đang active về mặt process nhưng thực tế chờ lock.

### 4.1 Query đang chạy lâu

~~~sql
SELECT
    pid,
    usename,
    application_name,
    now() - query_start AS duration,
    wait_event_type,
    wait_event,
    left(query, 1000) AS query
FROM pg_stat_activity
WHERE state = 'active'
  AND query_start IS NOT NULL
  AND now() - query_start > interval '30 seconds'
ORDER BY duration DESC;
~~~

Đừng terminate mọi query lâu. Query analytics có thể được phép chạy lâu; cần kiểm tra lock, plan, resource và owner trước.

### 4.2 Idle in transaction

~~~sql
SELECT
    pid,
    usename,
    application_name,
    client_addr,
    now() - xact_start AS transaction_age,
    state_change,
    left(query, 1000) AS last_query
FROM pg_stat_activity
WHERE state IN ('idle in transaction', 'idle in transaction (aborted)')
ORDER BY xact_start;
~~~

Transaction idle lâu có thể:

- giữ snapshot và dead tuple;
- chặn VACUUM;
- giữ lock;
- làm bloat;
- giữ connection pool;
- khiến failover/maintenance khó hơn.

Không kill theo một con số cứng. Xác định owner, application và tác động rồi dùng timeout ở tầng phù hợp.

### 4.3 Transaction và xmin lâu nhất

~~~sql
SELECT
    pid,
    usename,
    datname,
    xact_start,
    now() - xact_start AS age,
    backend_xmin,
    state,
    left(query, 500) AS query
FROM pg_stat_activity
WHERE xact_start IS NOT NULL
ORDER BY xact_start;
~~~

Transaction mở lâu là một trong các nguyên nhân khiến VACUUM không dọn được tuple cũ.

---

## 5. Blocking và blocked session

Tìm session đang bị block:

~~~sql
SELECT
    blocked.pid AS blocked_pid,
    blocked.usename AS blocked_user,
    now() - blocked.query_start AS blocked_for,
    blocked.wait_event_type,
    blocked.wait_event,
    left(blocked.query, 500) AS blocked_query,
    blocker.pid AS blocker_pid,
    blocker.usename AS blocker_user,
    now() - blocker.query_start AS blocker_query_age,
    blocker.state AS blocker_state,
    left(blocker.query, 500) AS blocker_query
FROM pg_stat_activity AS blocked
JOIN LATERAL unnest(pg_blocking_pids(blocked.pid)) AS blocker_pid
    ON true
JOIN pg_stat_activity AS blocker
    ON blocker.pid = blocker_pid;
~~~

Câu hỏi cần trả lời:

1. blocker đang chạy query hay idle in transaction?
2. blocker giữ lock từ khi nào?
3. blocked session ảnh hưởng bao nhiêu request?
4. lock là bình thường trong migration hay bất ngờ?
5. có deadlock hay chỉ lock wait?

Terminate backend là hành động có tác động:

~~~sql
SELECT pg_cancel_backend(12345);
SELECT pg_terminate_backend(12345);
~~~

pg_cancel_backend hủy query hiện tại nhưng giữ session. pg_terminate_backend đóng session và rollback transaction chưa commit. Chỉ dùng sau khi xác định PID đúng, owner, tác động và runbook.

---

## 6. pg_locks

Xem lock hiện tại:

~~~sql
SELECT
    l.pid,
    a.usename,
    a.application_name,
    a.state,
    l.locktype,
    l.mode,
    l.granted,
    l.relation::regclass AS relation_name,
    l.transactionid,
    l.virtualxid,
    a.query_start,
    left(a.query, 500) AS query
FROM pg_locks AS l
LEFT JOIN pg_stat_activity AS a
    ON a.pid = l.pid
WHERE l.granted = false
ORDER BY a.query_start NULLS LAST;
~~~

Lock mode cần đọc cùng với object và granted:

- granted=false: đang chờ;
- granted=true: đang giữ;
- relation lock: table/index;
- transactionid/virtualxid: thường liên quan transaction visibility hoặc concurrent update;
- AccessExclusiveLock: có thể chặn đọc/ghi nhiều thao tác.

Tìm AccessExclusiveLock:

~~~sql
SELECT
    l.pid,
    l.granted,
    l.mode,
    l.relation::regclass,
    a.state,
    a.xact_start,
    left(a.query, 800) AS query
FROM pg_locks AS l
JOIN pg_stat_activity AS a ON a.pid = l.pid
WHERE l.mode = 'AccessExclusiveLock';
~~~

Đừng kết luận mọi AccessExclusiveLock là lỗi. DDL có thể cần lock mạnh; vấn đề là thời điểm, thời lượng và ảnh hưởng.

---

## 7. Deadlock

Deadlock là vòng chờ lock. PostgreSQL phát hiện và abort một transaction để phá vòng:

~~~sql
SHOW deadlock_timeout;
SHOW log_lock_waits;
~~~

Thống kê database:

~~~sql
SELECT
    datname,
    deadlocks,
    conflicts,
    temp_files,
    temp_bytes,
    blk_read_time,
    blk_write_time
FROM pg_stat_database
ORDER BY deadlocks DESC;
~~~

Deadlock count là counter từ lúc stats reset. Cần lấy delta theo thời gian:

~~~text
delta_deadlocks = current_counter - previous_counter
~~~

Khi deadlock xảy ra:

1. đọc log để lấy transaction/statement;
2. xác định lock order;
3. làm cho mọi workflow lock object theo cùng thứ tự;
4. giảm transaction duration;
5. xử lý retry ở application cho lỗi serialization/deadlock phù hợp;
6. không chỉ tăng deadlock_timeout để che triệu chứng.

---

## 8. pg_stat_database

Tổng quan theo database:

~~~sql
SELECT
    datname,
    numbackends,
    xact_commit,
    xact_rollback,
    blks_read,
    blks_hit,
    tup_returned,
    tup_fetched,
    tup_inserted,
    tup_updated,
    tup_deleted,
    temp_files,
    temp_bytes,
    deadlocks,
    blk_read_time,
    blk_write_time,
    stats_reset
FROM pg_stat_database
WHERE datname IS NOT NULL
ORDER BY datname;
~~~

Cache hit ratio thường tính:

~~~sql
SELECT
    datname,
    round(
        100.0 * blks_hit / nullif(blks_hit + blks_read, 0),
        2
    ) AS cache_hit_ratio
FROM pg_stat_database
WHERE datname IS NOT NULL;
~~~

Diễn giải cẩn thận:

- ratio thấp có thể chỉ ra thiếu cache hoặc workload scan lớn;
- ratio cao không chứng minh latency tốt;
- một query có thể đọc nhiều page nhưng tất cả đều từ cache;
- index/table bloat và query plan vẫn có thể gây chậm;
- xem delta theo thời gian, không chỉ tổng counter.

temp_files/temp_bytes tăng có thể chỉ ra sort/hash spill, query report lớn hoặc work_mem thấp. Tăng work_mem global không phải phản ứng mặc định; tìm query trước.

blk_read_time và blk_write_time chỉ có ý nghĩa khi track_io_timing hoặc cơ chế tương ứng được bật. Kiểm tra setting trước khi diễn giải:

~~~sql
SHOW track_io_timing;
~~~

---

## 9. pg_stat_user_tables

Theo dõi table và autovacuum:

~~~sql
SELECT
    schemaname,
    relname,
    seq_scan,
    seq_tup_read,
    idx_scan,
    idx_tup_fetch,
    n_live_tup,
    n_dead_tup,
    n_mod_since_analyze,
    last_vacuum,
    last_autovacuum,
    last_analyze,
    last_autoanalyze,
    vacuum_count,
    autovacuum_count,
    analyze_count,
    autoanalyze_count
FROM pg_stat_user_tables
ORDER BY n_dead_tup DESC;
~~~

Tín hiệu cần xem:

- n_dead_tup tăng liên tục;
- last_autovacuum quá cũ;
- n_mod_since_analyze cao;
- seq_scan/tup_read lớn trên table đáng lẽ selective;
- autovacuum count tăng nhưng dead tuple vẫn không giảm;
- table nóng có tỷ lệ update/delete cao.

Dead tuple không tự chứng minh bloat nghiêm trọng. Cần xem kích thước, free space, transaction lâu và pattern update.

Tìm table thay đổi nhiều:

~~~sql
SELECT
    schemaname,
    relname,
    n_mod_since_analyze,
    n_live_tup,
    n_dead_tup
FROM pg_stat_user_tables
ORDER BY n_mod_since_analyze DESC;
~~~

Tìm autovacuum đang chạy:

~~~sql
SELECT
    pid,
    datname,
    usename,
    wait_event_type,
    wait_event,
    xact_start,
    query_start,
    left(query, 800) AS query
FROM pg_stat_activity
WHERE backend_type = 'autovacuum worker';
~~~

Nếu có transaction lâu, tăng autovacuum worker chưa chắc giải quyết được. Bước đầu là tìm snapshot/lock giữ xmin.

---

## 10. pg_stat_user_indexes

Theo dõi index:

~~~sql
SELECT
    schemaname,
    relname AS table_name,
    indexrelname AS index_name,
    idx_scan,
    idx_tup_read,
    idx_tup_fetch,
    pg_size_pretty(pg_relation_size(indexrelid)) AS index_size
FROM pg_stat_user_indexes
ORDER BY idx_scan ASC, pg_relation_size(indexrelid) DESC;
~~~

idx_scan bằng 0 không tự động nghĩa index thừa:

- stats có thể mới reset;
- query hiếm nhưng quan trọng;
- index hỗ trợ constraint;
- planner chọn sequential scan đúng;
- index mới tạo chưa được dùng;
- query workload chưa đại diện.

Trước khi drop index:

1. kiểm tra pg_stat_user_indexes trong thời gian đủ dài;
2. kiểm tra constraint/unique/foreign key;
3. kiểm tra plan của query quan trọng;
4. kiểm tra index phục vụ ORDER BY hoặc partial condition;
5. tạo rollback plan;
6. cân nhắc drop/rebuild trong maintenance window.

Index size lớn và idx_scan thấp là tín hiệu điều tra, không phải lệnh DROP ngay.

---

## 11. pg_stat_statements

pg_stat_statements gom các query tương tự theo normalized form:

~~~sql
SELECT
    queryid,
    calls,
    total_exec_time,
    mean_exec_time,
    min_exec_time,
    max_exec_time,
    stddev_exec_time,
    rows,
    shared_blks_hit,
    shared_blks_read,
    temp_blks_read,
    temp_blks_written,
    wal_bytes,
    left(query, 1000) AS query
FROM pg_stat_statements
ORDER BY total_exec_time DESC
LIMIT 20;
~~~

Top theo tổng thời gian:

~~~sql
SELECT
    calls,
    round(total_exec_time::numeric, 2) AS total_ms,
    round(mean_exec_time::numeric, 2) AS mean_ms,
    rows,
    shared_blks_read,
    temp_blks_written,
    left(query, 500) AS query
FROM pg_stat_statements
ORDER BY total_exec_time DESC
LIMIT 20;
~~~

Top theo latency trung bình với đủ số call:

~~~sql
SELECT
    calls,
    round(mean_exec_time::numeric, 2) AS mean_ms,
    round(max_exec_time::numeric, 2) AS max_ms,
    left(query, 500) AS query
FROM pg_stat_statements
WHERE calls >= 20
ORDER BY mean_exec_time DESC
LIMIT 20;
~~~

Top query đọc disk:

~~~sql
SELECT
    calls,
    shared_blks_read,
    shared_blks_hit,
    temp_blks_read,
    temp_blks_written,
    left(query, 500) AS query
FROM pg_stat_statements
ORDER BY shared_blks_read DESC
LIMIT 20;
~~~

Top query tạo WAL:

~~~sql
SELECT
    calls,
    wal_records,
    wal_fpi,
    wal_bytes,
    left(query, 500) AS query
FROM pg_stat_statements
ORDER BY wal_bytes DESC
LIMIT 20;
~~~

### 11.1 Tổng thời gian và latency

total_exec_time cho biết query nào tiêu thụ nhiều thời gian database nhất. mean_exec_time cho biết trung bình mỗi call. Cần đọc cùng calls:

~~~text
query A: 1 call × 30 giây
query B: 1.000.000 call × 20 ms
~~~

Query A có mean cao; query B có thể chiếm tổng tài nguyên lớn hơn. Tối ưu theo tổng time, latency và tác động SLO tùy vấn đề.

pg_stat_statements không cung cấp histogram p95/p99 chuẩn. Percentile nên lấy từ:

- application metrics histogram;
- structured slow-query log;
- tracing;
- extension/collector hỗ trợ histogram nếu được đánh giá.

### 11.2 Reset stats có chủ ý

~~~sql
SELECT pg_stat_statements_reset();
~~~

Reset làm mất baseline đang có. Chỉ reset khi:

- bắt đầu một benchmark có ghi timestamp;
- sau deploy muốn đo giai đoạn mới;
- có runbook và ghi lại lý do.

Không reset để làm dashboard “đẹp hơn”.

---

## 12. Slow query log

Cấu hình thường dùng:

~~~sql
SHOW log_min_duration_statement;
SHOW log_lock_waits;
SHOW deadlock_timeout;
SHOW log_line_prefix;
~~~

Bật log query vượt ngưỡng:

~~~sql
ALTER SYSTEM SET log_min_duration_statement = '500ms';
ALTER SYSTEM SET log_lock_waits = 'on';
ALTER SYSTEM SET deadlock_timeout = '1s';
SELECT pg_reload_conf();
~~~

Các setting khác cần cân nhắc:

~~~text
log_min_duration_sample
log_statement_sample_rate
log_transaction_sample_rate
log_temp_files
track_io_timing
~~~

log_min_duration_statement thấp làm log volume tăng mạnh. Chọn threshold theo traffic và khả năng ingest. Có thể dùng sampling cho traffic lớn.

log_temp_files:

~~~sql
ALTER SYSTEM SET log_temp_files = '64MB';
SELECT pg_reload_conf();
~~~

Log temp files giúp tìm query spill disk. Kết hợp query ID/application_name để gắn log với pg_stat_statements.

log_line_prefix nên có thông tin đủ để correlate:

~~~text
%m [%p] %q%u@%d/%a %r
~~~

Thường bao gồm timestamp, PID, user, database, application name và remote address. Không ghi query parameter nhạy cảm bừa bãi.

---

## 13. EXPLAIN trong chẩn đoán production

Xem plan không thực thi:

~~~sql
EXPLAIN
SELECT *
FROM sales.orders
WHERE customer_id = 10
ORDER BY created_at DESC
LIMIT 50;
~~~

Đo thực tế:

~~~sql
EXPLAIN (ANALYZE, BUFFERS, WAL, SETTINGS)
SELECT *
FROM sales.orders
WHERE customer_id = 10
ORDER BY created_at DESC
LIMIT 50;
~~~

ANALYZE thực thi query. Không chạy trên INSERT/UPDATE/DELETE production nếu chưa hiểu side effect. Với SELECT nặng, vẫn có thể gây I/O và CPU thật.

Đọc:

- estimated rows vs actual rows;
- loops;
- shared hit/read;
- temp read/write;
- WAL;
- planning/execution time;
- settings thay đổi plan.

Query chẩn đoán phải được chạy với parameter/role/workload gần thực tế. Plan trong staging với dữ liệu nhỏ không đại diện production.

---

## 14. Checkpoint và WAL metrics

WAL statistics:

~~~sql
SELECT
    wal_records,
    wal_fpi,
    wal_bytes,
    wal_buffers_full,
    stats_reset
FROM pg_stat_wal;
~~~

Tính WAL generation theo khoảng:

~~~text
wal_bytes_per_second
  = (current_wal_bytes - previous_wal_bytes) / elapsed_seconds
~~~

Hoặc lấy LSN:

~~~sql
SELECT
    pg_current_wal_lsn(),
    pg_wal_lsn_diff(
        pg_current_wal_lsn(),
        '0/0'
    );
~~~

Checkpoint statistics phụ thuộc version. PostgreSQL hiện đại có pg_stat_checkpointer:

~~~sql
SELECT *
FROM pg_stat_checkpointer;
~~~

Ở version cũ hơn, một phần metric nằm trong pg_stat_bgwriter:

~~~sql
SELECT *
FROM pg_stat_bgwriter;
~~~

Đọc các tín hiệu:

- checkpoint count tăng;
- checkpoint_write_time/checkpoint_sync_time cao;
- buffers written trong checkpoint lớn;
- WAL tăng đột biến;
- checkpoint warning trong log;
- checkpoint làm latency tăng.

Checkpoints quá dày có thể do max_wal_size nhỏ hoặc workload ghi lớn. Checkpoint quá thưa không tự động tốt nếu recovery time và WAL burst tăng. Điều chỉnh cùng với storage, RTO và workload.

---

## 15. WAL, full-page image và write amplification

wal_bytes tăng có thể do:

- INSERT/UPDATE/DELETE nhiều;
- full-page image sau checkpoint;
- index nhiều;
- update làm thay đổi nhiều index;
- bulk operation;
- logical replication;
- maintenance hoặc rewrite table.

pg_stat_statements có wal_bytes theo query khi version/configuration hỗ trợ. Kết hợp:

~~~sql
SELECT
    queryid,
    calls,
    wal_bytes,
    wal_fpi,
    left(query, 500)
FROM pg_stat_statements
ORDER BY wal_bytes DESC
LIMIT 20;
~~~

Nếu WAL tăng bất thường:

1. tìm deploy/job mới;
2. so sánh query WAL top;
3. kiểm tra checkpoint;
4. kiểm tra replication slot/archive;
5. kiểm tra bulk update/index;
6. xem disk và replica lag;
7. không xóa WAL thủ công trong pg_wal.

---

## 16. Connection count

Theo dõi connection:

~~~sql
SELECT
    datname,
    usename,
    application_name,
    client_addr,
    state,
    count(*) AS connections
FROM pg_stat_activity
GROUP BY datname, usename, application_name, client_addr, state
ORDER BY connections DESC;
~~~

Tổng quan:

~~~sql
SELECT
    count(*) AS total,
    count(*) FILTER (WHERE state = 'active') AS active,
    count(*) FILTER (WHERE state = 'idle') AS idle,
    count(*) FILTER (
        WHERE state LIKE 'idle in transaction%'
    ) AS idle_in_transaction,
    current_setting('max_connections')::integer AS max_connections
FROM pg_stat_activity;
~~~

Connection cao không đồng nghĩa throughput cao. Mỗi backend có memory, context switch và contention. Cần phân biệt:

- active query;
- idle connection bình thường từ pool;
- idle in transaction nguy hiểm;
- reserved superuser connections;
- app leak/connection storm.

Alert nên dựa trên available headroom, active/idle split và error rate, không chỉ percent of max_connections.

Không dùng pg_terminate_backend để chữa connection spike mà không tìm nguồn. Kiểm tra pool, deployment, health check, retry storm và network.

---

## 17. Replication lag

Physical primary:

~~~sql
SELECT
    application_name,
    client_addr,
    state,
    sync_state,
    sent_lsn,
    write_lsn,
    flush_lsn,
    replay_lsn,
    write_lag,
    flush_lag,
    replay_lag
FROM pg_stat_replication;
~~~

Byte lag:

~~~sql
SELECT
    application_name,
    pg_size_pretty(
        pg_wal_lsn_diff(sent_lsn, replay_lsn)
    ) AS replay_lag_bytes
FROM pg_stat_replication;
~~~

Standby:

~~~sql
SELECT
    pg_last_wal_receive_lsn(),
    pg_last_wal_replay_lsn(),
    pg_last_xact_replay_timestamp(),
    clock_timestamp() - pg_last_xact_replay_timestamp()
        AS replay_time_lag;
~~~

Logical:

~~~sql
SELECT
    subname,
    received_lsn,
    latest_end_lsn,
    latest_end_time,
    now() - latest_end_time AS message_age
FROM pg_stat_subscription;
~~~

Lag có thể do network, receiver, disk flush, replay conflict, long query trên standby, apply conflict hoặc subscriber bị dừng. Không chỉ nhìn một cột replay_lag.

---

## 18. Disk usage và I/O

Database size:

~~~sql
SELECT
    datname,
    pg_size_pretty(pg_database_size(datname)) AS database_size
FROM pg_database
WHERE datallowconn
ORDER BY pg_database_size(datname) DESC;
~~~

Table/index size:

~~~sql
SELECT
    schemaname,
    relname,
    pg_size_pretty(
        pg_total_relation_size(relid)
    ) AS total_size,
    pg_size_pretty(
        pg_relation_size(relid)
    ) AS table_size,
    pg_size_pretty(
        pg_indexes_size(relid)
    ) AS indexes_size
FROM pg_catalog.pg_statio_user_tables
ORDER BY pg_total_relation_size(relid) DESC
LIMIT 30;
~~~

WAL directory:

~~~bash
du -sh "$PGDATA/pg_wal"
df -h "$PGDATA"
df -ih "$PGDATA"
~~~

Theo dõi cả:

- bytes còn lại;
- inode;
- IOPS/throughput;
- fsync latency;
- WAL growth;
- temp file growth;
- archive destination;
- replication slot retained WAL.

Disk đầy có thể làm database read-only hoặc không ghi được WAL. Alert phải đủ sớm để có thời gian xử lý, không chờ 99 phần trăm.

pg_stat_io trên version hỗ trợ:

~~~sql
SELECT *
FROM pg_stat_io;
~~~

Dùng để tách I/O theo backend type/context/object. Xác nhận schema cột theo phiên bản đang chạy.

---

## 19. Autovacuum activity

Xem cấu hình:

~~~sql
SELECT
    name,
    setting,
    unit,
    context,
    source
FROM pg_settings
WHERE name LIKE 'autovacuum%'
ORDER BY name;
~~~

Table stats:

~~~sql
SELECT
    schemaname,
    relname,
    n_live_tup,
    n_dead_tup,
    n_mod_since_analyze,
    last_autovacuum,
    last_autoanalyze,
    autovacuum_count,
    autoanalyze_count
FROM pg_stat_user_tables
ORDER BY n_dead_tup DESC;
~~~

Autovacuum không chỉ là maintenance background. Nó ảnh hưởng:

- table/index bloat;
- planner statistics;
- transaction wraparound;
- cache/I/O;
- query latency.

Đừng xử lý dead tuple bằng VACUUM FULL theo phản xạ. Điều tra long transaction, threshold/scale factor, table churn, lock và disk trước. VACUUM FULL cần lock mạnh và rewrite table.

---

## 20. Query latency p50, p95, p99

Database statistics thường có aggregate mean/max, không phải percentile histogram. p50/p95/p99 nên lấy từ:

- application timer theo route/query name;
- OpenTelemetry span;
- structured log với duration;
- metrics histogram;
- collector chuyên dụng.

Ví dụ log dạng:

~~~text
timestamp, service, db, query_name, duration_ms, rows, success
~~~

Từ histogram có thể tính:

- p50: median;
- p95: 95 phần trăm request nhanh hơn hoặc bằng ngưỡng;
- p99: tail latency.

Mean có thể che tail:

~~~text
99 request: 10 ms
1 request: 10 s
mean gần 110 ms
p99 gần 10 s
~~~

Dashboard cần hiển thị cùng:

- request rate;
- error rate;
- p50/p95/p99;
- query database time;
- lock wait;
- CPU/I/O;
- deployment marker.

---

## 21. Dashboard panels đề xuất

### Panel workload

- transactions per second;
- SELECT/INSERT/UPDATE/DELETE rate;
- active sessions;
- connection utilization;
- temp files/temp bytes;
- WAL bytes per second.

### Panel latency

- application p50/p95/p99;
- top query total time;
- top query mean time;
- top query calls;
- lock wait duration;
- checkpoint duration.

### Panel storage

- disk used/free;
- pg_wal size;
- table/index growth;
- read/write throughput;
- I/O latency;
- temp directory usage.

### Panel maintenance

- dead tuples;
- last autovacuum/autoanalyze;
- autovacuum workers;
- long transactions;
- wraparound risk;
- analyze freshness.

### Panel replication

- sender/receiver state;
- replay lag bytes/time;
- sync state;
- logical apply status;
- slot retained WAL;
- archive failure.

Một panel nên có owner, unit, time window và link runbook. Không hiển thị counter tổng mà không ghi stats_reset hoặc rate.

---

## 22. Alert threshold mẫu

Các ngưỡng dưới đây là điểm bắt đầu cho lab/staging, không phải giá trị universal:

| Metric | Warning mẫu | Critical mẫu | Ghi chú |
|---|---:|---:|---|
| disk usage | > 75% | > 90% | xem growth và tốc độ đầy |
| connection usage | > 70% | > 85% | xem active/idle/idle transaction |
| replication lag | > 30s | > 120s | tùy RPO/SLO |
| blocked query | > 30s | > 5m | cần PID/blocker |
| deadlocks delta | > 0 | lặp lại | xem log và lock order |
| slot retained WAL | tăng liên tục | gần giới hạn disk | xác định consumer |
| autovacuum stale | table nóng quá ngưỡng | dead tuple tăng nhanh | theo workload |
| p95 latency | vượt SLO | vượt error budget | lấy từ app |

Alert cần duration:

~~~text
disk > 90% trong 5 phút
~~~

Tránh alert theo một điểm đo vì checkpoint, backup, deploy hoặc batch có thể tạo spike ngắn. Dùng rate, delta, moving window và suppression cho maintenance window.

---

## 23. Chẩn đoán query chậm

Quy trình:

1. xác nhận latency từ application;
2. tìm query normalized trong pg_stat_statements/log;
3. kiểm tra calls, total/mean time và rows;
4. xem pg_stat_activity đang chạy gì;
5. kiểm tra lock wait;
6. lấy EXPLAIN đúng parameter;
7. so sánh estimated vs actual rows;
8. kiểm tra index/statistics/cache/I/O;
9. thay đổi một thứ;
10. đo lại và ghi tác dụng phụ.

Query:

~~~sql
SELECT
    queryid,
    calls,
    round(total_exec_time::numeric, 2) AS total_ms,
    round(mean_exec_time::numeric, 2) AS mean_ms,
    rows,
    left(query, 800) AS query
FROM pg_stat_statements
ORDER BY total_exec_time DESC
LIMIT 20;
~~~

Không tối ưu chỉ vì query có mean cao. Query ít gọi nhưng một lần chạy report 30 giây có thể chấp nhận; query 5 ms gọi hàng triệu lần có thể là bottleneck lớn hơn.

---

## 24. Chẩn đoán lock wait

Quy trình:

1. xác định blocked PID;
2. dùng pg_blocking_pids;
3. xem blocker query, state và xact_age;
4. xem lock relation/mode;
5. xác định deploy/migration/job liên quan;
6. ưu tiên sửa transaction/lock order;
7. cancel/terminate chỉ theo runbook;
8. ghi lại deadlock/lock wait để ngăn lặp lại.

Query tổng hợp:

~~~sql
SELECT
    blocked.pid AS blocked_pid,
    now() - blocked.query_start AS blocked_age,
    blocked.wait_event_type,
    blocked.wait_event,
    blocker.pid AS blocker_pid,
    blocker.state AS blocker_state,
    now() - blocker.xact_start AS blocker_xact_age,
    left(blocked.query, 400) AS blocked_query,
    left(blocker.query, 400) AS blocker_query
FROM pg_stat_activity AS blocked
JOIN LATERAL unnest(pg_blocking_pids(blocked.pid)) AS b(pid)
    ON true
JOIN pg_stat_activity AS blocker
    ON blocker.pid = b.pid;
~~~

---

## 25. Chẩn đoán connection storm

Dấu hiệu:

- connection count tăng nhanh;
- active query không tăng tương ứng;
- login/authentication rate tăng;
- CPU tăng do backend/context switch;
- application retry liên tục;
- connection refused hoặc remaining connection slots lỗi.

Kiểm tra:

~~~sql
SELECT
    application_name,
    client_addr,
    state,
    count(*)
FROM pg_stat_activity
GROUP BY application_name, client_addr, state
ORDER BY count(*) DESC;
~~~

Tìm connection idle in transaction:

~~~sql
SELECT
    pid,
    application_name,
    client_addr,
    now() - xact_start AS xact_age,
    left(query, 500)
FROM pg_stat_activity
WHERE state LIKE 'idle in transaction%';
~~~

Hành động ưu tiên:

- giảm retry storm ở application;
- kiểm tra pool size và leak;
- bật backpressure;
- áp timeout;
- giữ connection pool tổng dưới năng lực database;
- xử lý connection cũ theo runbook.

Tăng max_connections thường chỉ làm memory/CPU contention nặng hơn. Connection pooling là chủ đề tiếp theo, nhưng monitoring phải phát hiện vấn đề ngay từ tuần này.

---

## 26. Chẩn đoán WAL/checkpoint tăng

Dấu hiệu:

- wal_bytes tăng đột biến;
- pg_wal lớn;
- checkpoint write/sync time cao;
- replica lag tăng;
- archive/slot retained WAL tăng;
- disk gần đầy.

Điều tra:

~~~sql
SELECT
    queryid,
    calls,
    wal_bytes,
    wal_fpi,
    left(query, 700)
FROM pg_stat_statements
ORDER BY wal_bytes DESC
LIMIT 20;

SELECT *
FROM pg_stat_wal;

SELECT *
FROM pg_stat_checkpointer;
~~~

Kiểm tra thêm config:

~~~sql
SHOW max_wal_size;
SHOW checkpoint_timeout;
SHOW checkpoint_completion_target;
SHOW full_page_writes;
~~~

Không tắt full_page_writes để giảm WAL trên production như một phản xạ. Đây là setting bảo vệ page sau checkpoint và liên quan durability/corruption safety.

---

## 27. Chẩn đoán replication lag

Phân loại:

1. sender không gửi;
2. network chậm;
3. receiver không ghi;
4. standby disk flush chậm;
5. replay bị conflict;
6. apply worker lỗi;
7. slot giữ WAL;
8. standby quá tải read query.

Physical:

~~~sql
SELECT
    application_name,
    state,
    sync_state,
    pg_size_pretty(pg_wal_lsn_diff(sent_lsn, replay_lsn))
        AS replay_lag_bytes,
    replay_lag
FROM pg_stat_replication;
~~~

Logical:

~~~sql
SELECT
    subname,
    status,
    latest_end_lsn,
    latest_end_time
FROM pg_stat_subscription;
~~~

Nếu lag tăng cùng disk I/O standby, xem storage. Nếu receiver disconnected, xem network/credential. Nếu apply error, đọc log và sửa dữ liệu/schema. Nếu slot retained WAL tăng nhưng consumer inactive, xử lý slot theo phê duyệt.

---

## 28. Chẩn đoán disk gần đầy

Trình tự an toàn:

1. kiểm tra filesystem và inode;
2. phân loại data, pg_wal, temp, archive, log;
3. tìm replication slot retained WAL;
4. tìm archive failure;
5. tìm query temp file;
6. tìm table/index growth;
7. không xóa file trong PGDATA thủ công;
8. giải phóng theo runbook và backup trước khi destructive action.

SQL:

~~~sql
SELECT
    slot_name,
    active,
    pg_size_pretty(
        pg_wal_lsn_diff(pg_current_wal_lsn(), restart_lsn)
    ) AS retained_wal
FROM pg_replication_slots
WHERE restart_lsn IS NOT NULL;

SELECT
    datname,
    temp_files,
    pg_size_pretty(temp_bytes) AS temp_bytes
FROM pg_stat_database
ORDER BY temp_bytes DESC;
~~~

Nếu cần xóa slot inactive, xác nhận consumer đã bỏ và có phương án re-seed. Nếu cần rotate log, dùng cơ chế logging chuẩn, không xóa nhầm WAL/data.

---

## 29. Chẩn đoán autovacuum và bloat

Dấu hiệu:

- n_dead_tup tăng;
- table/index lớn nhanh hơn dữ liệu;
- last_autovacuum cũ;
- query scan nhiều page;
- long transaction;
- autovacuum worker bị lock;
- wraparound age tăng.

Kiểm tra transaction lâu:

~~~sql
SELECT
    pid,
    usename,
    datname,
    xact_start,
    now() - xact_start AS age,
    backend_xmin,
    state,
    left(query, 500)
FROM pg_stat_activity
WHERE xact_start IS NOT NULL
ORDER BY xact_start;
~~~

Kiểm tra table:

~~~sql
SELECT
    schemaname,
    relname,
    n_live_tup,
    n_dead_tup,
    last_autovacuum,
    last_autoanalyze,
    n_mod_since_analyze
FROM pg_stat_user_tables
ORDER BY n_dead_tup DESC;
~~~

Hành động có thể gồm:

- kết thúc transaction idle lâu sau phê duyệt;
- điều chỉnh autovacuum riêng cho table nóng;
- chạy VACUUM/ANALYZE trong window;
- xử lý lock;
- giảm update không cần thiết;
- thiết kế partition/retention;
- không dùng VACUUM FULL tự động.

---

## 30. Reset và snapshot statistics

Các view statistic chứa counter tích lũy từ stats_reset. Để dashboard có rate:

1. lấy snapshot định kỳ;
2. lưu timestamp;
3. tính delta;
4. chia thời gian elapsed;
5. xử lý counter reset/rollover;
6. gắn version và instance identity.

Kiểm tra stats_reset:

~~~sql
SELECT datname, stats_reset
FROM pg_stat_database;

SELECT stats_reset
FROM pg_stat_wal;
~~~

Không so sánh counter giữa hai instance hoặc sau restart mà không biết mốc reset. Exporter thường xử lý một phần, nhưng cần hiểu semantics để tránh dashboard sai.

---

## 31. Log và bảo mật

Monitoring query có thể chứa:

- email;
- token;
- payload JSON;
- dữ liệu cá nhân;
- parameter nhạy cảm.

Quy tắc:

- hạn chế quyền đọc log;
- mask/parameterize ở application;
- không gửi raw query vào nơi quá rộng;
- retention log theo policy;
- encrypt log transport/storage;
- audit ai đọc dashboard/log;
- không expose pg_stat_activity cho user không cần.

query trong pg_stat_activity và pg_stat_statements có thể lộ SQL text. Role monitoring phải được cấp theo least privilege.

---

## 32. Chẩn đoán theo triệu chứng

### API tăng p95

1. kiểm tra rate/error;
2. xem top query theo mean và total;
3. kiểm tra lock wait;
4. kiểm tra CPU/I/O;
5. so sánh deployment marker;
6. chạy EXPLAIN query đại diện;
7. kiểm tra replica routing nếu read path.

### CPU thấp nhưng query chậm

Có thể đang chờ:

- lock;
- disk I/O;
- network/client;
- connection pool;
- buffer pin;
- synchronous replication.

Xem wait_event_type trước khi tăng CPU.

### Disk tăng nhanh

Kiểm tra:

- pg_wal;
- replication slot;
- archive failure;
- temp_bytes;
- table/index growth;
- logs;
- long transaction/bloat.

### Deadlock tăng

Đọc log, xác định lock order, query pair và transaction boundary. Không chỉ tăng timeout.

### Cache hit ratio giảm

Kiểm tra data growth, plan, sequential scan, cache pressure, index bloat và workload mới. Không tăng shared_buffers ngay nếu chưa có bằng chứng.

---

## 33. Incident workflow

### Phase 1: Stabilize

- xác nhận incident và phạm vi;
- tránh chạy query nặng chẩn đoán;
- freeze migration/deploy nếu liên quan;
- ghi mốc thời gian;
- bảo vệ dữ liệu/log.

### Phase 2: Observe

- pg_stat_activity;
- blocking/locks;
- pg_stat_database;
- pg_stat_statements;
- CPU/RAM/I/O/disk;
- WAL/checkpoint;
- replication/autovacuum.

### Phase 3: Hypothesize

Viết giả thuyết cụ thể:

~~~text
p95 tăng vì UPDATE batch giữ AccessExclusiveLock trên orders
~~~

Sau đó tìm bằng chứng phản bác hoặc xác nhận. Không kết luận từ một metric.

### Phase 4: Mitigate

Chọn hành động ít rủi ro trước:

- cancel query không cần;
- pause batch;
- rollback deploy;
- chuyển read traffic;
- tăng storage theo quy trình;
- xử lý blocker;
- giới hạn workload.

### Phase 5: Verify

- latency trở lại;
- lock wait giảm;
- WAL/disk ổn định;
- replication catch up;
- không tạo lỗi dữ liệu mới;
- application health pass.

### Phase 6: Learn

- root cause;
- contributing factors;
- detection gap;
- alert/runbook update;
- regression test;
- owner và deadline.

---

## 34. Dashboard SQL snapshot

Snapshot nhanh:

~~~sql
SELECT 'activity' AS section, count(*)::text AS value
FROM pg_stat_activity
UNION ALL
SELECT 'active',
       count(*) FILTER (WHERE state = 'active')::text
FROM pg_stat_activity
UNION ALL
SELECT 'idle_in_transaction',
       count(*) FILTER (
           WHERE state LIKE 'idle in transaction%'
       )::text
FROM pg_stat_activity
UNION ALL
SELECT 'blocked',
       count(*) FILTER (WHERE wait_event_type = 'Lock')::text
FROM pg_stat_activity;
~~~

Database snapshot:

~~~sql
SELECT
    datname,
    numbackends,
    xact_commit,
    xact_rollback,
    blks_read,
    blks_hit,
    temp_files,
    temp_bytes,
    deadlocks,
    stats_reset
FROM pg_stat_database
WHERE datname = current_database();
~~~

Top queries:

~~~sql
SELECT
    queryid,
    calls,
    round(total_exec_time::numeric, 2) AS total_ms,
    round(mean_exec_time::numeric, 2) AS mean_ms,
    shared_blks_read,
    temp_blks_written,
    wal_bytes,
    left(query, 300) AS query
FROM pg_stat_statements
ORDER BY total_exec_time DESC
LIMIT 10;
~~~

Đây là snapshot phục vụ chẩn đoán, không phải metrics time series. Production dashboard cần exporter/collector lưu dữ liệu theo thời gian.

---

## 35. Alert runbook mẫu

### Alert: blocked query

~~~text
Điều kiện: wait_event_type=Lock > 60 giây

Kiểm tra:
1. pg_blocking_pids
2. blocker state/xact age
3. lock mode/relation
4. migration/batch đang chạy

Hành động:
1. liên hệ owner
2. cancel blocker nếu được phê duyệt
3. terminate chỉ khi cần
4. ghi PID/query/rollback
~~~

### Alert: replication lag

~~~text
Điều kiện: replay lag > SLA trong 10 phút

Kiểm tra:
1. sender/receiver state
2. LSN sent/write/flush/replay
3. standby I/O
4. recovery conflict
5. slot/WAL/disk

Hành động:
1. giảm read workload nếu cần
2. xử lý network/disk
3. không promote chỉ vì lag ngắn
4. re-seed nếu mất WAL
~~~

### Alert: disk

~~~text
Điều kiện: filesystem > 90%

Kiểm tra:
1. pg_wal
2. slot retained WAL
3. archive failure
4. temp files
5. logs

Hành động:
1. ngăn workload tạo thêm dữ liệu nếu cần
2. xử lý consumer/slot theo runbook
3. mở rộng storage
4. không xóa file PGDATA thủ công
~~~

---

## 36. Bài tập thực hành

### Bài 1 — Activity inventory

Tạo query hiển thị active, idle, idle in transaction, wait event, query age và transaction age. Chạy bằng role monitoring và ghi các field không được thấy.

### Bài 2 — Blocking

Mở hai session, tạo lock wait, dùng pg_blocking_pids và pg_locks để xác định blocker. Viết hành động cancel an toàn.

### Bài 3 — pg_stat_statements

Chạy workload gồm query nhanh nhiều lần, query chậm ít lần và query tạo temp spill. Xếp hạng theo total time, mean time, shared read và temp bytes.

### Bài 4 — Slow log

Bật log_min_duration_statement và log_temp_files trong lab. Correlate log với query ID/application_name và thử threshold khác nhau.

### Bài 5 — Checkpoint/WAL

Tạo workload ghi, chụp pg_stat_wal trước/sau, tính WAL bytes per second và tìm query tạo WAL nhiều.

### Bài 6 — Replication lag

Dùng primary/standby từ Tuần 14, tạo lag có chủ ý, dashboard sent/write/flush/replay LSN và retained WAL.

### Bài 7 — Autovacuum

Tạo update/delete workload, theo dõi n_dead_tup, last_autovacuum và long transaction. Đề xuất hành động không dùng VACUUM FULL ngay.

### Bài 8 — Incident report

Mô phỏng p95 tăng do lock wait hoặc connection storm. Viết timeline, evidence, mitigation, verification, root cause và alert/runbook update.

---

## 37. Tiêu chí hoàn thành Tuần 15

Bạn hoàn thành bài khi có thể:

- đọc pg_stat_activity và phân biệt active, idle, idle in transaction, wait event;
- tìm blocked session và blocker bằng pg_blocking_pids;
- đọc pg_locks và deadlock counter;
- hiểu pg_stat_database, cache hit ratio, temp bytes và I/O time;
- theo dõi table/autovacuum bằng pg_stat_user_tables;
- đánh giá index usage bằng pg_stat_user_indexes;
- cài và dùng pg_stat_statements;
- xếp hạng query theo total time, mean time, disk read, temp và WAL;
- cấu hình slow-query/lock-wait log;
- phân biệt mean với p50/p95/p99;
- theo dõi checkpoint và WAL generation;
- phát hiện connection storm;
- đo physical/logical replication lag;
- phát hiện slot giữ WAL;
- theo dõi disk, inode và I/O;
- chẩn đoán autovacuum/bloat theo evidence;
- đặt alert threshold có duration, severity và runbook;
- xử lý incident từ stabilize đến verify;
- xây dashboard tối thiểu có metric database và application.

---

## Tóm tắt cần nhớ

1. Monitoring phải trả lời câu hỏi vận hành, không chỉ hiển thị số.
2. pg_stat_activity cho biết session, query và wait event hiện tại.
3. pg_blocking_pids và pg_locks là điểm bắt đầu khi có lock wait.
4. pg_stat_database là counter; phải tính delta/rate theo thời gian.
5. pg_stat_statements cho total/mean, không tự cung cấp p95/p99 chuẩn.
6. Slow-query log cần threshold, sampling và retention phù hợp.
7. Checkpoint/WAL tăng phải nối với write workload, FPI, slot và replication.
8. Connection cao không đồng nghĩa throughput cao; idle in transaction đặc biệt nguy hiểm.
9. Replication lag cần xem sent/write/flush/replay và nguyên nhân phía sau.
10. Disk gần đầy cần phân loại pg_wal, slot, temp, archive, log và data.
11. Autovacuum phải được theo dõi cùng long transaction và dead tuple.
12. Alert tốt có duration, severity, owner và runbook.

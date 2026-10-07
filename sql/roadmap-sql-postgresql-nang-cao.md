# Roadmap học SQL và PostgreSQL nâng cao trong 16 tuần

Lộ trình này dành cho người đã hoàn thành SQL/PostgreSQL cơ bản và muốn đi sâu vào hiệu năng, concurrency, bảo mật, vận hành và khả năng mở rộng.

Thời lượng đề xuất: **10–15 giờ/tuần** trong 16 tuần.

Nên thực hành trên PostgreSQL 18.x stable và đọc song song [PostgreSQL 18 Documentation](https://www.postgresql.org/docs/18/).

## Điều kiện đầu vào

- Viết được query nhiều JOIN, GROUP BY, subquery và CTE.
- Thiết kế schema có primary key, foreign key và constraint.
- Dùng transaction cơ bản.
- Tạo B-tree index và đọc EXPLAIN ở mức nhập môn.
- Dùng psql hoặc một SQL client.
- Biết Linux command line, Git và Docker cơ bản.

## Mục tiêu cuối lộ trình

Sau 16 tuần, bạn có thể:

- Phân tích query chậm bằng EXPLAIN ANALYZE và pg_stat_statements.
- Thiết kế B-tree, GIN, GiST, BRIN, partial và covering index.
- Hiểu query planner, statistics và cardinality estimate.
- Dùng JSONB, full-text search, materialized view và partitioning.
- Xử lý lock, deadlock, isolation level và concurrent update.
- Hiểu MVCC, VACUUM, autovacuum và table/index bloat.
- Viết PL/pgSQL function, procedure và trigger có kiểm soát.
- Thiết kế roles, GRANT, default privileges và Row-Level Security.
- Thực hiện backup, restore, PITR và kiểm tra khả năng khôi phục.
- Hiểu physical/logical replication và mô hình HA cơ bản.
- Thiết lập monitoring, timeout, connection pooling và resource limit.
- Hoàn thành một project PostgreSQL production-like có runbook.

## Môi trường lab

~~~bash
docker network create pg-lab

docker run --name pg-advanced \
  --network pg-lab \
  -e POSTGRES_PASSWORD=postgres \
  -e POSTGRES_DB=lab \
  -p 5432:5432 \
  -d postgres:18
~~~

Nên cài thêm psql, pgAdmin hoặc DBeaver, pgbench, pg_dump, pg_restore, pg_basebackup và jq.

Không chạy ALTER SYSTEM, DROP DATABASE, TRUNCATE hoặc test failover trên dữ liệu người dùng.

---

# Giai đoạn 1 — Query planner và hiệu năng

## Tuần 1: EXPLAIN, planner và cardinality estimate

### Học

- Parse, rewrite, plan và execute.
- Sequential scan, index scan, index-only scan.
- Bitmap index scan và bitmap heap scan.
- Nested Loop, Hash Join, Merge Join.
- Estimated cost, actual time, startup cost.
- Planning time, execution time.
- Rows estimate và rows actual.
- Statistics target.

### Thực hành

Tạo bảng lớn bằng generate_series và đọc plan:

~~~sql
EXPLAIN (ANALYZE, BUFFERS, VERBOSE)
SELECT *
FROM events
WHERE tenant_id = 10
  AND created_at >= now() - interval '30 days';
~~~

Ghi lại node tốn thời gian nhất, chênh lệch rows estimate/actual và số buffer được đọc.

### Tiêu chí hoàn thành

- Đọc được execution plan từ dưới lên.
- Phân biệt estimated rows với actual rows.
- Không kết luận query nhanh/chậm chỉ bằng cost.

## Tuần 2: Statistics và tối ưu query có hệ thống

### Học

- ANALYZE và pg_stats.
- Histogram và most-common values.
- Correlated columns.
- Extended statistics.
- Generic plan và custom plan.
- Parameterized query.
- SARGability.
- Tác động của function lên column trong WHERE.

### Lab

- Tạo dữ liệu lệch phân phối.
- So sánh plan trước và sau ANALYZE.
- Tăng statistics target cho column đặc biệt.
- Tạo extended statistics cho hai column có tương quan.
- Viết lại query để planner dùng index.

### Deliverable

Một báo cáo gồm query gốc, plan, nguyên nhân, thay đổi, plan sau tối ưu và tác dụng phụ.

## Tuần 3: Index nâng cao

### Học

- Composite index và thứ tự column.
- Equality trước range.
- Covering index với INCLUDE.
- Partial index.
- Expression index.
- GIN cho JSONB và full-text.
- GiST cho range và dữ liệu đặc biệt.
- BRIN cho bảng lớn có correlation theo thời gian.
- CREATE INDEX CONCURRENTLY.
- Index bloat.
- Tác động của index lên write.

### Ví dụ

~~~sql
CREATE INDEX CONCURRENTLY idx_events_tenant_created
ON events (tenant_id, created_at DESC);

CREATE INDEX CONCURRENTLY idx_users_active_email
ON users (email)
WHERE deleted_at IS NULL;

CREATE INDEX idx_profiles_metadata
ON profiles USING gin (metadata jsonb_path_ops);

CREATE INDEX idx_events_created_brin
ON events USING brin (created_at);
~~~

### Lab

- Benchmark không index, B-tree và BRIN.
- So sánh kích thước index bằng pg_relation_size.
- Thử REINDEX CONCURRENTLY.
- Tìm index không được sử dụng.
- Tìm foreign key chưa có index.

---

# Giai đoạn 2 — SQL nâng cao và dữ liệu đặc biệt

## Tuần 4: Window function và query nhiều tầng

### Học

- Window frame, ROWS và RANGE.
- ROW_NUMBER, RANK, DENSE_RANK.
- LAG, LEAD, FIRST_VALUE, LAST_VALUE.
- Running total.
- Gaps and islands.
- Top-N-per-group.
- Recursive CTE.
- LATERAL JOIN.

### Bài tập

- Top 3 sản phẩm theo từng category.
- Khoảng thời gian giữa hai event của cùng một user.
- Gom các ngày hoạt động liên tiếp.
- Tìm bản ghi gần nhất cho mỗi customer bằng LATERAL.
- Duyệt cây category bằng recursive CTE.

## Tuần 5: JSONB, array và dữ liệu bán cấu trúc

### Học

- JSONB khác JSON.
- Toán tử mũi tên, containment và key existence.
- JSON path.
- jsonb_set và jsonb_array_elements.
- GIN index cho JSONB.
- Generated column.
- Array và unnest.
- Khi nào dùng column quan hệ thay cho JSONB.

### Thực hành

~~~sql
CREATE INDEX idx_orders_metadata
ON orders USING gin (metadata jsonb_path_ops);

SELECT id, metadata->>'source' AS source
FROM orders
WHERE metadata @> '{"priority": "high"}';
~~~

Field dùng để JOIN, filter hoặc constraint thường xuyên nên là column riêng. JSONB phù hợp với metadata thay đổi hoặc payload phụ.

## Tuần 6: Full-text search và generated data

### Học

- tsvector và tsquery.
- to_tsvector, websearch_to_tsquery.
- Ranking với ts_rank.
- GIN full-text index.
- Dictionary và text search configuration.
- Generated column.
- Expression index.
- Collation và accent ở mức khái niệm.

### Thực hành

- Tạo search vector từ title và description.
- Search nhiều từ và phrase.
- Xếp hạng kết quả.
- So sánh ILIKE với full-text search.
- Dùng generated column hoặc expression index.

---

# Giai đoạn 3 — Schema lớn và concurrency

## Tuần 7: Partitioning

### Học

- Range, list và hash partition.
- Partition key.
- Partition pruning.
- Default partition.
- Local index và partitioned index.
- Attach/detach partition.
- Retention bằng drop partition.
- Partition constraint.

### Lab

~~~sql
CREATE TABLE events (
    id bigint GENERATED ALWAYS AS IDENTITY,
    tenant_id bigint NOT NULL,
    created_at timestamptz NOT NULL,
    payload jsonb NOT NULL
) PARTITION BY RANGE (created_at);

CREATE TABLE events_2026_01
PARTITION OF events
FOR VALUES FROM ('2026-01-01') TO ('2026-02-01');
~~~

Tạo 12 partition, kiểm tra pruning bằng EXPLAIN, detach dữ liệu cũ và xử lý row nằm ngoài range.

## Tuần 8: MVCC, isolation và locking

### Học

- MVCC và visibility.
- Read Committed, Repeatable Read, Serializable.
- Row lock và table lock.
- SELECT FOR UPDATE, FOR SHARE.
- NOWAIT và SKIP LOCKED.
- Lost update, write skew và deadlock.
- Serialization failure.

### Lab

Mở hai session psql để mô phỏng:

- Hai worker lấy job bằng FOR UPDATE SKIP LOCKED.
- Hai giao dịch cùng trừ inventory.
- Deadlock do lock theo thứ tự ngược.
- Retry khi gặp serialization failure.

### Tiêu chí hoàn thành

- Dùng pg_locks để tìm session đang chờ.
- Chọn isolation level theo yêu cầu nghiệp vụ.
- Thiết kế thứ tự lock nhất quán.
- Có timeout và retry có giới hạn.

---

# Giai đoạn 4 — Maintenance và server internals

## Tuần 9: VACUUM, autovacuum và bloat

### Học

- Dead tuple và MVCC cleanup.
- VACUUM thường, VACUUM FULL và ANALYZE.
- Autovacuum launcher/worker.
- Freeze và transaction wraparound.
- Table bloat và index bloat.
- Visibility map.
- HOT update.
- Fillfactor.

### Lab

- Tạo workload UPDATE/DELETE lớn.
- Theo dõi pg_stat_user_tables.
- So sánh kích thước trước/sau VACUUM.
- Kiểm tra n_dead_tup và last_autovacuum.
- Điều chỉnh autovacuum riêng cho một bảng nóng.

### Cần nhớ

- VACUUM FULL cần lock mạnh và tạo lại file.
- Không tắt autovacuum một cách tùy tiện.
- Transaction mở quá lâu có thể ngăn VACUUM dọn dead tuple.

## Tuần 10: Server configuration và resource control

### Học

- shared_buffers.
- work_mem.
- maintenance_work_mem.
- effective_cache_size.
- max_connections.
- statement_timeout.
- lock_timeout.
- idle_in_transaction_session_timeout.
- WAL và checkpoint.
- Parallel query.
- temp_file_limit.
- Vai trò của OS cache.

### Lab

- Tạo workload bằng pgbench.
- Thay đổi từng setting một.
- Đo throughput, latency và temporary file.
- Ghi cấu hình trước/sau.
- Viết cách rollback.

Không copy cấu hình trên Internet nếu chưa hiểu RAM, CPU và workload của hệ thống.

---

# Giai đoạn 5 — Programmability và security

## Tuần 11: PL/pgSQL, function, procedure và trigger

### Học

- Function và procedure.
- Parameter mode.
- RETURNS TABLE.
- Biến và control flow.
- Exception handling.
- Dynamic SQL với format.
- SECURITY INVOKER và SECURITY DEFINER.
- Trigger BEFORE, AFTER và INSTEAD OF.
- Row-level và statement-level trigger.

### Thực hành

- Function tính order total.
- Function tìm sản phẩm tồn kho thấp.
- Trigger audit thay đổi giá.
- Procedure xử lý batch job.
- Dynamic SQL an toàn với identifier được quote đúng.

### Cần tránh

- Đưa toàn bộ business logic vào trigger.
- Nối chuỗi input vào dynamic SQL.
- SECURITY DEFINER không khóa search_path.
- Function có side effect nhưng đặt tên như query thuần.

## Tuần 12: Roles, privileges và Row-Level Security

### Học

- Role login và role group.
- CREATE ROLE, GRANT, REVOKE.
- Schema, table, sequence và function privilege.
- Default privileges.
- Ownership và search_path.
- SSL/TLS ở mức triển khai.
- Row-Level Security.
- Policy USING và WITH CHECK.
- Bypass RLS và owner behavior.

### Lab

Tạo các role:

~~~text
app_owner       migration và schema owner
app_runtime     chỉ quyền cần thiết cho ứng dụng
app_readonly    chỉ đọc báo cáo
app_reporting   đọc view/materialized view
~~~

Tạo policy để tenant chỉ đọc và sửa dữ liệu của chính tenant đó. Test SELECT, INSERT, UPDATE và tình huống lộ dữ liệu qua view.

### Checklist bảo mật

- Ứng dụng không kết nối bằng superuser.
- Không cấp quyền trên toàn database nếu không cần.
- Không để schema public writable tùy tiện.
- Secret không nằm trong Git.
- Có audit log cho thao tác nhạy cảm.
- Kiểm tra quyền bằng role thật, không chỉ bằng owner.

---

# Giai đoạn 6 — Backup, replication và observability

## Tuần 13: Backup, restore và Point-in-Time Recovery

### Học

- Logical backup.
- pg_dump, pg_dumpall và pg_restore.
- Custom format và directory format.
- Physical base backup.
- WAL archive.
- Restore database mới.
- PITR.
- RPO và RTO.
- Backup encryption và retention.

### Thực hành

- Dump database bằng custom format.
- Restore vào database khác.
- Tạo base backup.
- Bật WAL archive trong lab.
- Xóa nhầm bảng rồi phục hồi tới thời điểm trước khi xóa.
- Viết runbook restore từ đầu.

Backup chỉ được coi là tồn tại khi đã restore thử thành công. Ghi thời gian restore, dữ liệu mất tối đa và bước xác minh sau restore.

## Tuần 14: Physical và logical replication

### Học

- Primary và standby.
- Streaming replication.
- WAL sender và WAL receiver.
- Replication slot.
- Synchronous và asynchronous replication.
- Read replica.
- Replication lag.
- Logical publication/subscription.
- Failover và promotion.
- Split brain.

### Lab

- Chạy primary và standby trong Docker.
- Theo dõi replay lag.
- Tạo replication slot và quan sát WAL retention.
- Promote standby trong môi trường test.
- Tạo logical publication cho một nhóm bảng.

Replication không thay thế backup. Replication slot bị bỏ quên có thể làm WAL phình lớn.

## Tuần 15: Monitoring và chẩn đoán production

### Học

- pg_stat_activity.
- pg_locks.
- pg_stat_database.
- pg_stat_user_tables.
- pg_stat_user_indexes.
- pg_stat_statements.
- Slow query log.
- Checkpoint và WAL metrics.
- Connection count.
- Replication lag.
- Disk usage.
- Alert threshold.

### Dashboard tối thiểu

- CPU, RAM, disk và I/O.
- Active/idle connections.
- Query latency p50, p95, p99.
- Top query theo total time.
- Lock wait và deadlock count.
- Cache hit ratio.
- WAL generation.
- Replication lag.
- Autovacuum activity.

## Tuần 16: Connection pooling, HA và capstone

### Học

- Vì sao max_connections cao không đồng nghĩa nhiều throughput.
- Application connection pool.
- PgBouncer session pooling và transaction pooling.
- Pool sizing.
- Timeout và backpressure.
- Health check.
- Read/write split.
- HA topology.
- SLO cho database.

---

# Capstone: Multi-tenant Event và Order Platform

## Schema gợi ý

~~~text
tenants
users
products
inventory
orders
order_items
payments
events
audit_logs
~~~

## Yêu cầu

- Bảng events partition theo thời gian.
- Tenant isolation bằng RLS.
- JSONB metadata với GIN index.
- Full-text search.
- Workflow dùng transaction và SKIP LOCKED.
- Một query dashboard tối ưu bằng EXPLAIN.
- Autovacuum setting riêng cho bảng nóng.
- Backup/restore đã kiểm thử.
- Read replica hoặc logical replication trong lab.
- Dashboard monitoring.
- Runbook xử lý lock, disk đầy, lag và restore.

## Dữ liệu tối thiểu

- 100 tenants.
- 100.000 users.
- 1.000.000 events.
- 200.000 orders.
- 1.000.000 order_items.
- Có tenant lớn bất thường để kiểm tra skew.

## Scenario phải test

- Query tenant nhỏ và tenant lớn.
- Deadlock do hai transaction lock khác thứ tự.
- Serialization failure và retry.
- Autovacuum bị ảnh hưởng bởi transaction mở lâu.
- Replication slot giữ WAL.
- Restore database từ backup.
- Tenant A cố đọc dữ liệu tenant B.
- Partition kế tiếp chưa được tạo.
- Connection pool cạn.

## Deliverables

- ERD và quyết định thiết kế.
- DDL và migration versioned.
- Seed script.
- Query benchmark.
- Execution plan trước/sau tối ưu.
- Index inventory.
- Partition maintenance script.
- RLS policy test.
- Backup/restore runbook.
- Replication runbook.
- Monitoring dashboard.
- Incident response checklist.

---

# Lịch học mỗi tuần

| Buổi | Nội dung |
|---|---|
| Buổi 1 — 2 giờ | Đọc documentation và ghi chú |
| Buổi 2 — 2 giờ | Tạo workload hoặc schema |
| Buổi 3 — 3 giờ | Benchmark, EXPLAIN và đo metric |
| Buổi 4 — 2 giờ | Viết migration, runbook hoặc test |
| Cuối tuần — 2–6 giờ | Capstone và review kết quả |

Mọi tối ưu phải theo quy trình:

1. Đo baseline.
2. Đưa ra giả thuyết.
3. Thay đổi một biến.
4. Đo lại cùng workload.
5. Kiểm tra tác dụng phụ lên write, lock, memory và disk.
6. Ghi cách rollback.

---

# Những lỗi cần tránh

- Tạo index theo cảm tính.
- Dùng EXPLAIN nhưng không dùng ANALYZE.
- Tăng work_mem toàn hệ thống để chữa một query.
- Tắt autovacuum vì thấy nó tốn CPU.
- Dùng VACUUM FULL như bảo trì hằng ngày.
- Dùng superuser cho application.
- Coi read replica là backup.
- Cấu hình replication nhưng không theo dõi lag.
- Dùng trigger cho toàn bộ nghiệp vụ.
- Dùng JSONB thay schema quan hệ mà không có lý do.
- Dùng OFFSET lớn cho bảng lớn.
- Để transaction mở trong thời gian dài.
- Chạy migration phá hủy dữ liệu không có rollback plan.

---

# Checklist sau 16 tuần

## Query và hiệu năng

- Đọc được execution plan phức tạp.
- Phân biệt planner estimate và actual result.
- Chọn được loại index có lý do.
- Tìm được query chậm bằng pg_stat_statements.
- Phát hiện được lock wait và deadlock.

## Internals và maintenance

- Giải thích MVCC và dead tuple.
- Theo dõi autovacuum.
- Nhận biết table/index bloat.
- Điều chỉnh timeout và resource limit an toàn.

## Reliability

- Tạo và restore logical backup.
- Có runbook PITR.
- Hiểu replication lag và replication slot.
- Có health check và alert quan trọng.

## Security

- Không dùng superuser cho ứng dụng.
- Dùng role tách biệt.
- Kiểm tra GRANT/REVOKE.
- Viết và test RLS policy.
- Bảo vệ SECURITY DEFINER.

## Portfolio

- Capstone chạy được từ README.
- Có benchmark reproducible.
- Có before/after execution plan.
- Có dashboard.
- Có runbook incident và restore.

---

# Nguồn học chính thức

- [PostgreSQL 18 Documentation](https://www.postgresql.org/docs/18/)
- [Using EXPLAIN](https://www.postgresql.org/docs/18/using-explain.html)
- [Indexes](https://www.postgresql.org/docs/18/indexes.html)
- [Table Partitioning](https://www.postgresql.org/docs/18/ddl-partitioning.html)
- [Concurrency Control](https://www.postgresql.org/docs/18/mvcc.html)
- [Explicit Locking](https://www.postgresql.org/docs/18/explicit-locking.html)
- [Routine Database Maintenance Tasks](https://www.postgresql.org/docs/18/routine-vacuuming.html)
- [Server Configuration](https://www.postgresql.org/docs/18/runtime-config.html)
- [PL/pgSQL](https://www.postgresql.org/docs/18/plpgsql.html)
- [Database Roles](https://www.postgresql.org/docs/18/user-manag.html)
- [Row Security Policies](https://www.postgresql.org/docs/18/ddl-rowsecurity.html)
- [Backup and Restore](https://www.postgresql.org/docs/18/backup.html)
- [High Availability and Replication](https://www.postgresql.org/docs/18/high-availability.html)
- [Monitoring Database Activity](https://www.postgresql.org/docs/18/monitoring.html)
- [pg_stat_statements](https://www.postgresql.org/docs/18/pgstatstatements.html)

PostgreSQL nâng cao không chỉ là nhớ nhiều câu lệnh hơn. Đó là khả năng đo lường, giải thích hành vi database, thay đổi có kiểm soát và khôi phục được khi sự cố xảy ra.

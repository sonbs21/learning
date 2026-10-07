# Tuần 7 nâng cao: Partitioning trong PostgreSQL

> Mục tiêu: hiểu khi nào partitioning đáng dùng, chọn range/list/hash partition, thiết kế partition key, kiểm tra partition pruning, quản lý default partition, index trên partition, attach/detach và retention bằng drop partition.

Partitioning chia một table logic lớn thành nhiều partition vật lý nhỏ hơn. Application vẫn query table cha, nhưng PostgreSQL có thể chỉ đọc partition liên quan.

Ví dụ events theo tháng:

~~~text
events
├── events_2026_01
├── events_2026_02
├── events_2026_03
└── ...
~~~

Nếu query chỉ lấy tháng 2026-01 và planner prune đúng, database không cần đọc các partition khác.

Partitioning không phải index lớn hơn. Nó là cách tổ chức table, dữ liệu, constraint, maintenance và retention.

---

## 1. Khi nào cần partitioning?

Partitioning có thể phù hợp khi:

- table rất lớn;
- dữ liệu có chiều tự nhiên như thời gian hoặc tenant;
- query thường lọc theo partition key;
- cần xóa dữ liệu cũ nhanh;
- cần maintenance từng phần;
- muốn tách workload hoặc archive;
- một partition có thể được detach/backup riêng.

Partitioning không tự động giúp mọi query. Nếu query không lọc partition key, PostgreSQL có thể phải scan nhiều hoặc toàn bộ partition.

Không partition chỉ vì table có vài triệu row. Trước tiên đo:

- table size;
- query latency;
- retention;
- vacuum/autovacuum;
- index size;
- write pattern;
- khả năng vận hành.

Partitioning thêm complexity:

- nhiều table con;
- migration khó hơn;
- index và constraint cần hiểu;
- tạo partition mới đúng hạn;
- query không sargable có thể mất pruning;
- quá nhiều partition làm planning chậm.

---

## 2. Partitioned table và partition

Tạo table cha:

~~~sql
CREATE TABLE events (
    id bigint GENERATED ALWAYS AS IDENTITY,
    tenant_id bigint NOT NULL,
    event_type text NOT NULL,
    created_at timestamptz NOT NULL,
    payload jsonb NOT NULL
) PARTITION BY RANGE (created_at);
~~~

Table này là partitioned table. Nó định nghĩa schema và partition key nhưng dữ liệu nằm ở partition con.

Tạo partition:

~~~sql
CREATE TABLE events_2026_01
PARTITION OF events
FOR VALUES FROM ('2026-01-01 00:00:00+00')
           TO   ('2026-02-01 00:00:00+00');
~~~

Range partition dùng lower bound inclusive và upper bound exclusive:

~~~text
FROM  inclusive
TO    exclusive
~~~

Timestamp đúng ngày 2026-02-01 sẽ thuộc events_2026_02, không thuộc events_2026_01.

Tạo partition kế tiếp:

~~~sql
CREATE TABLE events_2026_02
PARTITION OF events
FOR VALUES FROM ('2026-02-01 00:00:00+00')
           TO   ('2026-03-01 00:00:00+00');
~~~

Insert vào table cha:

~~~sql
INSERT INTO events (
    tenant_id,
    event_type,
    created_at,
    payload
)
VALUES (
    10,
    'login',
    '2026-01-15 10:00:00+00',
    '{"ip": "203.0.113.10"}'::jsonb
);
~~~

PostgreSQL route row vào partition phù hợp.

---

## 3. Range partition

Range partition phù hợp với giá trị có thứ tự:

- timestamp;
- date;
- số ID;
- numeric range;
- version range.

Ví dụ theo năm:

~~~sql
CREATE TABLE measurements (
    id bigint GENERATED ALWAYS AS IDENTITY,
    measured_at timestamptz NOT NULL,
    value numeric NOT NULL
) PARTITION BY RANGE (measured_at);

CREATE TABLE measurements_2025
PARTITION OF measurements
FOR VALUES FROM ('2025-01-01') TO ('2026-01-01');

CREATE TABLE measurements_2026
PARTITION OF measurements
FOR VALUES FROM ('2026-01-01') TO ('2027-01-01');
~~~

Theo tháng thường phù hợp cho event log có retention ngắn:

~~~sql
CREATE TABLE events_2026_03
PARTITION OF events
FOR VALUES FROM ('2026-03-01') TO ('2026-04-01');
~~~

Không tạo partition theo ngày nếu workload không cần. Quá nhiều partition làm planning và operations phức tạp.

---

## 4. List partition

List partition chia theo các giá trị cụ thể:

~~~sql
CREATE TABLE orders (
    id bigint GENERATED ALWAYS AS IDENTITY,
    region text NOT NULL,
    customer_id bigint NOT NULL,
    created_at timestamptz NOT NULL
) PARTITION BY LIST (region);

CREATE TABLE orders_vietnam
PARTITION OF orders
FOR VALUES IN ('VN');

CREATE TABLE orders_singapore
PARTITION OF orders
FOR VALUES IN ('SG');

CREATE TABLE orders_other
PARTITION OF orders
FOR VALUES IN ('US', 'JP', 'KR');
~~~

List partition phù hợp khi:

- tập category nhỏ;
- tenant hoặc region cần tách;
- mỗi giá trị có policy khác nhau;
- giá trị partition ổn định.

Nếu region có hàng nghìn hoặc thay đổi liên tục, list partition có thể khó quản lý. Hash hoặc range có thể phù hợp hơn.

---

## 5. Hash partition

Hash partition phân phối row theo hash của key:

~~~sql
CREATE TABLE tenant_events (
    id bigint GENERATED ALWAYS AS IDENTITY,
    tenant_id bigint NOT NULL,
    created_at timestamptz NOT NULL,
    payload jsonb NOT NULL
) PARTITION BY HASH (tenant_id);

CREATE TABLE tenant_events_p0
PARTITION OF tenant_events
FOR VALUES WITH (MODULUS 4, REMAINDER 0);

CREATE TABLE tenant_events_p1
PARTITION OF tenant_events
FOR VALUES WITH (MODULUS 4, REMAINDER 1);

CREATE TABLE tenant_events_p2
PARTITION OF tenant_events
FOR VALUES WITH (MODULUS 4, REMAINDER 2);

CREATE TABLE tenant_events_p3
PARTITION OF tenant_events
FOR VALUES WITH (MODULUS 4, REMAINDER 3);
~~~

Hash partition phù hợp khi:

- muốn phân phối đều dữ liệu;
- query thường lọc theo key;
- không có range retention tự nhiên;
- muốn giảm kích thước partition/index mỗi phần.

Hash không thuận tiện cho việc xóa dữ liệu cũ theo thời gian. Nếu retention là mục tiêu chính, range theo thời gian thường hợp lý hơn.

---

## 6. Chọn partition key

Partition key nên:

- xuất hiện trong WHERE của query quan trọng;
- có phân phối phù hợp;
- hỗ trợ pruning;
- phản ánh retention hoặc maintenance;
- không thay đổi thường xuyên;
- phù hợp với constraint và unique design.

Ví dụ event log:

~~~text
created_at → range partition
~~~

Ví dụ workload multi-tenant cần phân phối đều:

~~~text
tenant_id → hash partition
~~~

Tránh partition key:

- query không bao giờ lọc;
- giá trị quá ít làm một partition quá lớn;
- giá trị thay đổi;
- expression khó dùng trong query;
- làm unique constraint không thể thiết kế như mong muốn.

Partition key không phải lúc nào cũng là column có selectivity cao nhất. Nó phải phù hợp cả query pruning và vận hành dữ liệu.

---

## 7. Partition pruning

Partition pruning là loại bỏ partition không thể chứa row phù hợp.

Query có điều kiện trực tiếp:

~~~sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT id, tenant_id, created_at
FROM events
WHERE created_at >= timestamptz '2026-01-10 00:00:00+00'
  AND created_at <  timestamptz '2026-01-12 00:00:00+00';
~~~

Plan tốt chỉ scan events_2026_01.

Query không có partition key:

~~~sql
SELECT id, tenant_id, created_at
FROM events
WHERE tenant_id = 10;
~~~

Có thể phải scan mọi partition.

Partition pruning có thể xảy ra lúc planning hoặc execution. Với parameterized query, PostgreSQL có thể chỉ prune khi value được biết ở execution tùy plan.

Không dùng function khiến planner khó chứng minh range:

~~~sql
WHERE date_trunc('month', created_at) = DATE '2026-01-01'
~~~

Viết range:

~~~sql
WHERE created_at >= timestamptz '2026-01-01 00:00:00+00'
  AND created_at <  timestamptz '2026-02-01 00:00:00+00'
~~~

---

## 8. EXPLAIN để kiểm tra pruning

Dùng EXPLAIN VERBOSE:

~~~sql
EXPLAIN (ANALYZE, BUFFERS, VERBOSE)
SELECT count(*)
FROM events
WHERE created_at >= timestamptz '2026-01-01'
  AND created_at <  timestamptz '2026-02-01';
~~~

Tìm:

- chỉ partition tháng cần thiết xuất hiện;
- Subplans Removed;
- Append hoặc Merge Append;
- actual rows của từng partition;
- buffers đọc từ partition nào.

Nếu mọi partition đều xuất hiện:

1. query có lọc partition key không?
2. predicate có viết thành range dễ suy luận không?
3. kiểu parameter có phù hợp không?
4. partition bounds có đúng không?
5. planner có thể prune ở planning hay execution không?
6. table có default partition chứa dữ liệu không?

Partitioning không thay thế index trong mỗi partition. Query vẫn có thể cần B-tree hoặc GIN local index.

---

## 9. Default partition

Default partition nhận row không khớp partition cụ thể:

~~~sql
CREATE TABLE events_default
PARTITION OF events DEFAULT;
~~~

Lợi ích:

- INSERT không fail khi chưa tạo partition mới;
- bảo vệ ingestion;
- dùng như vùng phát hiện dữ liệu ngoài dự kiến.

Rủi ro:

- dữ liệu có thể âm thầm rơi vào default;
- query có thể phải đọc default;
- attach partition mới có thể phải scan default để chắc chắn không có row trùng range;
- default phình to nếu không theo dõi.

Kiểm tra default:

~~~sql
SELECT COUNT(*)
FROM events_default;
~~~

Tìm row cần chuyển:

~~~sql
SELECT
    min(created_at),
    max(created_at),
    count(*)
FROM events_default;
~~~

Default nên có monitoring và quy trình cleanup. Không tạo default rồi quên nó.

---

## 10. Tạo partition mới và dữ liệu ngoài range

Nếu không có default và insert timestamp chưa có partition:

~~~sql
INSERT INTO events (
    tenant_id,
    event_type,
    created_at,
    payload
)
VALUES (
    10,
    'login',
    '2027-01-01 00:00:00+00',
    '{}'::jsonb
);
~~~

PostgreSQL sẽ báo không tìm được partition phù hợp.

Tạo trước partition:

~~~sql
CREATE TABLE events_2027_01
PARTITION OF events
FOR VALUES FROM ('2027-01-01') TO ('2027-02-01');
~~~

Sau đó insert lại.

Production nên có job tạo partition tương lai trước khi ingestion chạm boundary. Đừng chờ tới lúc INSERT lỗi mới tạo partition.

---

## 11. Local index và partitioned index

Tạo index trên table cha:

~~~sql
CREATE INDEX events_created_at_idx
ON events (created_at);
~~~

Với partitioned table, PostgreSQL quản lý index tương ứng trên partition. Mỗi partition có index vật lý riêng.

Có thể tạo index trên partition cụ thể:

~~~sql
CREATE INDEX events_2026_01_tenant_idx
ON events_2026_01 (tenant_id);
~~~

Nếu index structure giống nhau trên mọi partition, tạo trên parent dễ quản lý hơn:

~~~sql
CREATE INDEX events_tenant_created_idx
ON events (tenant_id, created_at DESC);
~~~

Query có thể dùng index riêng trong từng partition sau khi pruning.

Trade-off:

- nhiều partition → nhiều index object;
- migration index có thể lâu;
- partition mới phải có index;
- index trên partition lớn có thể build riêng;
- unique constraint có giới hạn theo partition key.

---

## 12. Unique và primary key trên partitioned table

Partitioned table có quy tắc quan trọng: unique hoặc primary key trên parent thường phải bao gồm partition key để PostgreSQL có thể bảo đảm uniqueness trên toàn bộ partition.

Ví dụ:

~~~sql
CREATE TABLE events (
    id bigint NOT NULL,
    created_at timestamptz NOT NULL,
    payload jsonb NOT NULL,
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);
~~~

Nếu chỉ muốn PRIMARY KEY(id), PostgreSQL cần cách bảo đảm ID không trùng giữa các partition. Identity sequence thường tạo ID toàn cục, nhưng constraint partitioned table vẫn cần được thiết kế phù hợp phiên bản và schema.

Nếu business key cần unique toàn table nhưng không chứa partition key, cân nhắc:

- sequence hoặc UUID toàn cục;
- bảng registry riêng chứa key unique;
- trigger/application coordination;
- thiết kế partition key khác;
- chấp nhận constraint chỉ local nếu business cho phép.

Đừng giả định unique index local tạo uniqueness toàn partitioned table.

---

## 13. Foreign key với partitioned table

PostgreSQL hỗ trợ nhiều dạng foreign key liên quan partitioned table, nhưng cần kiểm tra version và thiết kế cụ thể.

Các câu hỏi cần kiểm tra:

- bảng referenced có partitioned không?
- primary/unique key có phù hợp không?
- partition mới có constraint/index đầy đủ không?
- thao tác attach/detach có ảnh hưởng constraint không?
- workload delete/update parent có cần index child không?

Đừng chỉ test trên một partition nhỏ. Test insert, update, delete và attach partition trên schema thật.

---

## 14. Attach partition

Có thể tạo table độc lập rồi attach:

~~~sql
CREATE TABLE events_2026_04 (
    LIKE events INCLUDING DEFAULTS INCLUDING CONSTRAINTS
);

ALTER TABLE events_2026_04
ADD CONSTRAINT events_2026_04_bounds
CHECK (
    created_at >= timestamptz '2026-04-01'
    AND created_at < timestamptz '2026-05-01'
);

-- Load hoặc validate data vào table độc lập trước.
ALTER TABLE events
ATTACH PARTITION events_2026_04
FOR VALUES FROM ('2026-04-01') TO ('2026-05-01');
~~~

Constraint chứng minh dữ liệu nằm đúng range giúp attach nhanh hơn vì PostgreSQL không phải scan toàn bộ table để kiểm tra.

Nếu dữ liệu có row ngoài range, attach sẽ fail. Kiểm tra trước:

~~~sql
SELECT *
FROM events_2026_04
WHERE created_at < timestamptz '2026-04-01'
   OR created_at >= timestamptz '2026-05-01'
LIMIT 10;
~~~

Attach partition có lock và có thể ảnh hưởng workload. Chọn thời điểm và migration strategy phù hợp.

---

## 15. Detach partition

Detach tách partition khỏi table cha:

~~~sql
ALTER TABLE events
DETACH PARTITION events_2026_01;
~~~

Sau detach, table vẫn tồn tại độc lập và dữ liệu không bị xóa.

Có thể dùng để:

- archive;
- backup riêng;
- export;
- kiểm tra;
- drop sau retention;
- chuyển sang storage khác.

PostgreSQL có tùy chọn detach concurrently ở các phiên bản hỗ trợ, với điều kiện và trade-off riêng. Kiểm tra documentation của version triển khai trước khi dùng.

Detach không phải delete. Query table cha sẽ không còn thấy partition detached.

---

## 16. Retention bằng detach và drop

Retention tháng:

~~~sql
ALTER TABLE events
DETACH PARTITION events_2025_01;

DROP TABLE events_2025_01;
~~~

Lợi ích:

- nhanh hơn DELETE hàng triệu row;
- giảm bloat của table/index;
- giảm thời gian vacuum cho dữ liệu cũ;
- dễ archive trước khi xóa.

Safety checklist:

1. xác định đúng partition;
2. kiểm tra retention policy;
3. backup hoặc archive nếu cần;
4. kiểm tra foreign key/dependency;
5. detach hoặc drop trong change window;
6. ghi audit log;
7. xác nhận kích thước giảm và query không còn cần dữ liệu.

DROP TABLE là destructive. Trong lab hãy dùng database riêng và xác nhận target chính xác.

---

## 17. Partition theo thời gian và timezone

Nếu partition key là timestamptz, bounds phải thống nhất timezone:

~~~sql
FOR VALUES FROM ('2026-01-01 00:00:00+00')
           TO   ('2026-02-01 00:00:00+00')
~~~

Ứng dụng hiển thị timezone người dùng nhưng partition boundary nên có quy ước rõ, thường UTC.

Nếu business retention theo ngày địa phương, cần xác định:

- boundary theo UTC hay local timezone;
- ngày daylight saving;
- timestamp được chuyển đổi ở đâu;
- query có giữ được pruning không.

Không tạo partition boundary bằng chuỗi mơ hồ phụ thuộc timezone session.

---

## 18. Partition key và query parameter

Query literal range:

~~~sql
WHERE created_at >= timestamptz '2026-01-01'
  AND created_at <  timestamptz '2026-02-01'
~~~

dễ prune.

Parameterized query vẫn có thể prune execution-time nếu planner biết parameter ở thời điểm chạy, nhưng generic plan hoặc expression phức tạp có thể làm behavior khác.

Đo query thực tế qua driver. Đừng chỉ copy literal query vào psql rồi kết luận endpoint production prune đúng.

---

## 19. Partitioning và index strategy

Mỗi partition cần index phù hợp. Ví dụ:

~~~sql
CREATE INDEX events_tenant_created_idx
ON events (tenant_id, created_at DESC);
~~~

Nếu query luôn lọc theo created_at và tenant_id:

- partition pruning loại partition;
- local B-tree tìm tenant trong partition;
- ORDER BY có thể dùng index.

Nếu query chỉ lọc tenant_id trên partition theo thời gian mà không có time condition, nhiều partition vẫn phải được scan. Partitioning không giúp nếu access pattern không có partition key.

Nếu query phổ biến không lọc partition key, cân nhắc:

- index local trên mọi partition;
- partition key khác;
- query rewrite;
- summary/materialized view;
- denormalized read model;
- không partition query đó nếu lợi ích tổng thể không đủ.

---

## 20. Partition maintenance

Cần automation cho:

- tạo partition tương lai;
- kiểm tra default partition;
- analyze partition mới;
- tạo index partition mới;
- detach partition cũ;
- archive/drop;
- kiểm tra row ngoài bounds;
- theo dõi partition size;
- theo dõi số partition;
- kiểm tra lock và failed migration.

Query danh sách partition:

~~~sql
SELECT
    inhrelid::regclass AS partition_name,
    pg_get_expr(c.relpartbound, c.oid) AS partition_bound
FROM pg_inherits AS i
JOIN pg_class AS c
    ON c.oid = i.inhrelid
WHERE i.inhparent = 'events'::regclass
ORDER BY partition_name;
~~~

Kích thước theo partition:

~~~sql
SELECT
    relname,
    pg_size_pretty(pg_total_relation_size(oid)) AS total_size
FROM pg_class
WHERE relname LIKE 'events%';
~~~

---

## 21. Default partition cleanup

Nếu có default partition:

~~~sql
SELECT
    min(created_at),
    max(created_at),
    count(*)
FROM events_default;
~~~

Trước khi attach events_2026_04, phải chuyển row tháng 4 khỏi default:

~~~sql
INSERT INTO events
SELECT *
FROM events_default
WHERE created_at >= timestamptz '2026-04-01'
  AND created_at <  timestamptz '2026-05-01';
~~~

Sau đó xóa row đã chuyển trong transaction phù hợp hoặc tạo table mới/archive. Với dữ liệu lớn, tránh thao tác một transaction khổng lồ; lập kế hoạch batch nhưng phải bảo đảm không duplicate hoặc mất row.

Sau cleanup:

~~~sql
SELECT *
FROM events_default
WHERE created_at >= timestamptz '2026-04-01'
  AND created_at <  timestamptz '2026-05-01'
LIMIT 1;
~~~

---

## 22. EXPLAIN partition pruning

Tạo 12 partition tháng:

~~~sql
CREATE TABLE events_2026_01
PARTITION OF events
FOR VALUES FROM ('2026-01-01') TO ('2026-02-01');

CREATE TABLE events_2026_02
PARTITION OF events
FOR VALUES FROM ('2026-02-01') TO ('2026-03-01');

CREATE TABLE events_2026_03
PARTITION OF events
FOR VALUES FROM ('2026-03-01') TO ('2026-04-01');

CREATE TABLE events_2026_04
PARTITION OF events
FOR VALUES FROM ('2026-04-01') TO ('2026-05-01');
~~~

Query một tháng:

~~~sql
EXPLAIN (ANALYZE, BUFFERS, VERBOSE)
SELECT count(*)
FROM events
WHERE created_at >= timestamptz '2026-02-01'
  AND created_at <  timestamptz '2026-03-01';
~~~

Query hai tháng:

~~~sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT count(*)
FROM events
WHERE created_at >= timestamptz '2026-02-01'
  AND created_at <  timestamptz '2026-04-01';
~~~

Query không có partition key:

~~~sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT count(*)
FROM events
WHERE tenant_id = 10;
~~~

Ghi lại partition nào được scan trong từng plan.

---

## 23. Partitioning không thay thế archive design

Detach partition vẫn để lại dữ liệu trên cùng database nếu chưa chuyển đi. Nếu database storage vẫn là bottleneck, cần archive thật sự:

~~~text
Detach
  → dump/export
  → verify archive
  → drop local partition
~~~

Archive cần:

- checksum hoặc row count;
- metadata partition bounds;
- encryption nếu dữ liệu nhạy cảm;
- retention và restore test;
- runbook rõ ràng.

Không drop partition chỉ vì đã export mà chưa verify khả năng đọc lại.

---

## 24. Lỗi thường gặp

### Partition quá nhỏ

Hàng nghìn hoặc hàng triệu partition làm planning, catalog và maintenance nặng. Chọn granularity theo query và retention.

### Không lọc partition key

Query không có partition key có thể scan mọi partition. Đây không phải partition pruning.

### Dùng function trong predicate

Function trên partition key có thể làm pruning khó. Viết range rõ ràng.

### Quên partition tương lai

Đến boundary, INSERT có thể fail hoặc rơi vào default. Tạo trước partition bằng job.

### Default partition phình to

Dữ liệu sai hoặc chưa có partition mới có thể tập trung ở default. Monitor và cleanup.

### Giả định primary key local là unique toàn table

Unique/primary design phải xem partition key và phạm vi uniqueness.

### Index chỉ tạo ở table cũ

Partition mới có thể thiếu index nếu migration automation không tạo.

### Drop partition không kiểm tra

Drop nhanh nhưng mất dữ liệu. Archive và xác nhận target trước.

### Partition theo cột ít query

Nếu query không filter partition key, pruning không giúp.

### Dùng partition thay index

Partitioning tổ chức table; index vẫn cần trong partition cho filter/join/sort.

---

## 25. Lab hoàn chỉnh

### Bước 1: tạo parent

~~~sql
CREATE TABLE events (
    id bigint GENERATED ALWAYS AS IDENTITY,
    tenant_id bigint NOT NULL,
    event_type text NOT NULL,
    created_at timestamptz NOT NULL,
    payload jsonb NOT NULL
) PARTITION BY RANGE (created_at);
~~~

### Bước 2: tạo partition tháng

Tạo các partition từ tháng 1 tới tháng 12 của một năm, mỗi partition có lower bound và upper bound rõ.

### Bước 3: seed

~~~sql
INSERT INTO events (tenant_id, event_type, created_at, payload)
SELECT
    1 + (g % 100),
    CASE
        WHEN g % 2 = 0 THEN 'login'
        WHEN g % 5 = 0 THEN 'purchase'
        ELSE 'view'
    END,
    timestamptz '2026-01-01'
        + (g % 365) * interval '1 day',
    jsonb_build_object('sequence', g)
FROM generate_series(1, 1000000) AS g;
~~~

### Bước 4: index

~~~sql
CREATE INDEX events_tenant_created_idx
ON events (tenant_id, created_at DESC);
~~~

### Bước 5: analyze

~~~sql
ANALYZE events;
~~~

### Bước 6: đo pruning

Chạy query một tháng, nhiều tháng và không có time filter bằng EXPLAIN ANALYZE.

### Bước 7: default

Tạo default partition, insert row ngoài range và kiểm tra row đi đâu.

### Bước 8: attach

Tạo table độc lập cho tháng mới, thêm CHECK bounds, rồi attach.

### Bước 9: retention

Detach một partition cũ, kiểm tra table cha không còn thấy dữ liệu, sau đó archive/drop trong lab.

### Bước 10: báo cáo

Ghi:

~~~text
Partition key
Partition count
Partition size
Plan có pruning hay không
Index local/parent
Default partition row count
Attach time
Detach/drop behavior
Retention procedure
Operational risks
~~~

---

## 26. Bài tập thực hành

### Bài 1 — Chọn strategy

Chọn range, list hoặc hash cho:

- event theo thời gian;
- order theo region cố định;
- dữ liệu multi-tenant cần phân phối đều;
- log cần drop theo tháng.

Giải thích trade-off.

### Bài 2 — Range tháng

Tạo events partition theo 12 tháng, seed dữ liệu và kiểm tra row vào đúng partition.

### Bài 3 — Partition pruning

Viết ba query: một tháng, hai tháng và không có partition key. Dùng EXPLAIN so sánh.

### Bài 4 — Default partition

Tạo default, insert timestamp ngoài range, tìm row trong default và chuyển row về partition đúng.

### Bài 5 — Attach

Tạo table độc lập, load dữ liệu tháng, thêm CHECK bounds, attach và đo thời gian.

### Bài 6 — Detach retention

Detach partition cũ, archive metadata, kiểm tra query table cha rồi drop trong database lab.

### Bài 7 — Local index

Tạo index tenant_id trên parent. Xác nhận index tồn tại trên partition và query có dùng index.

### Bài 8 — Unique key

Thiết kế primary key cho events partition theo created_at. Giải thích nếu business muốn id unique toàn table.

### Bài 9 — Maintenance

Viết checklist hoặc script giả lập cho job tạo partition tháng kế tiếp và cảnh báo nếu default có row.

### Bài 10 — Partition report

Viết báo cáo gồm query, plan, pruning, size, index và retention trade-off.

---

## 27. Tiêu chí hoàn thành Tuần 7

Bạn hoàn thành bài học khi có thể:

- Giải thích partitioned table và partition vật lý.
- Chọn range, list hoặc hash theo workload.
- Chọn partition key dựa trên query và retention.
- Tạo range partition theo tháng.
- Tạo list và hash partition cơ bản.
- Kiểm tra partition pruning bằng EXPLAIN.
- Hiểu planning-time và execution-time pruning ở mức thực hành.
- Dùng default partition có monitoring.
- Attach table độc lập thành partition.
- Detach partition để archive hoặc retention.
- Drop partition an toàn trong lab.
- Thiết kế index trên partition.
- Hiểu giới hạn unique/primary key với partition key.
- Lập kế hoạch tạo partition tương lai và analyze.
- Nhận biết partitioning không thay thế index.
- Viết runbook retention và restore/archive.

## Tóm tắt cần nhớ

~~~text
Partitioning chia một table logic lớn thành nhiều table vật lý.
Range phù hợp với thời gian và retention.
List phù hợp với tập giá trị nhỏ, ổn định.
Hash phù hợp phân phối đều, không phù hợp drop theo thời gian.
Partition pruning chỉ hiệu quả khi query lọc partition key.
Partition key phải phục vụ cả query và vận hành.
Default partition cần monitoring và cleanup.
Attach cần dữ liệu đúng bounds; CHECK constraint giúp validate trước.
Detach không xóa dữ liệu; DROP mới destructive.
Index trên parent/partition vẫn cần cho filter, join và sort.
Unique toàn table phải thiết kế cùng partition key hoặc registry khác.
Partitioning không tự động làm mọi query nhanh.
~~~

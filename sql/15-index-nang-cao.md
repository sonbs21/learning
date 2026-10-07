# Tuần 3 nâng cao: Index nâng cao trong PostgreSQL

> Mục tiêu: thiết kế index theo workload thực tế, dùng đúng composite index, covering index, partial index, expression index, GIN, GiST và BRIN; biết tạo index ít ảnh hưởng production bằng CREATE INDEX CONCURRENTLY; và nhận biết index bloat cùng chi phí write.

Index nâng cao không bắt đầu bằng câu hỏi “loại index nào hiện đại nhất?”. Nó bắt đầu bằng:

~~~text
Query nào đang chậm?
Điều kiện lọc và thứ tự sort là gì?
Query trả bao nhiêu row?
Dữ liệu có phân bố và tương quan thế nào?
Index này có làm INSERT/UPDATE/DELETE đắt hơn không?
Có thể tạo index mà không chặn ghi production không?
~~~

Nguyên tắc:

1. Query trước, index sau.
2. Đo bằng EXPLAIN (ANALYZE, BUFFERS).
3. Thiết kế theo workload, không theo danh sách type.
4. Một index có thể tốt cho query này nhưng xấu cho query khác.
5. Mỗi index phải có lý do, owner và cách review.

---

## 1. Ôn lại B-tree

B-tree là lựa chọn mặc định cho:

- equality;
- range;
- ORDER BY;
- prefix search phù hợp;
- primary key và unique constraint.

~~~sql
CREATE INDEX events_created_idx
ON events (created_at);
~~~

Query range:

~~~sql
SELECT id, created_at
FROM events
WHERE created_at >= timestamptz '2026-01-01'
  AND created_at <  timestamptz '2026-02-01';
~~~

B-tree không tự động tốt cho substring:

~~~sql
WHERE title ILIKE '%postgres%'
~~~

Với search text, xem xét pg_trgm hoặc full-text search.

---

## 2. Composite index và leftmost prefix

Composite index có thứ tự key:

~~~sql
CREATE INDEX events_tenant_created_idx
ON events (tenant_id, created_at DESC);
~~~

Phù hợp với:

~~~sql
SELECT id, event_type, created_at
FROM events
WHERE tenant_id = 10
ORDER BY created_at DESC
LIMIT 100;
~~~

Index có thể dùng phần đầu tenant_id để tìm nhóm row rồi lấy theo created_at.

Với index (tenant_id, created_at):

- tenant_id phù hợp;
- tenant_id và created_at phù hợp;
- created_at đơn độc thường không tận dụng đầy đủ index;
- event_type không phải key nên không dùng để định vị chính như hai column đầu.

Leftmost prefix không có nghĩa planner tuyệt đối không thể đọc index khi thiếu column đầu, nhưng lợi ích và access path thường kém hơn. Đừng thiết kế dựa trên một trường hợp ngoại lệ.

---

## 3. Equality trước range

Một hướng dẫn thường hữu ích:

~~~text
Equality columns trước.
Range columns sau.
Sort columns đặt theo query.
~~~

Query:

~~~sql
SELECT id, status, created_at
FROM orders
WHERE tenant_id = 10
  AND status = 'paid'
  AND created_at >= now() - interval '30 days'
ORDER BY created_at DESC
LIMIT 50;
~~~

Index ứng viên:

~~~sql
CREATE INDEX orders_tenant_status_created_idx
ON orders (tenant_id, status, created_at DESC);
~~~

tenant_id và status là equality, created_at là range/sort.

Đây là heuristic, không phải quy tắc tuyệt đối. Cần xem:

- selectivity;
- số giá trị distinct;
- MCV;
- query frequency;
- các query khác dùng index;
- khả năng index-only scan;
- chi phí ghi.

Index riêng cho mọi permutation thường tạo quá nhiều write overhead.

---

## 4. Column order và workload

Giả sử có hai query:

~~~sql
-- Query A
WHERE tenant_id = 10
  AND created_at >= now() - interval '7 days'

-- Query B
WHERE created_at >= now() - interval '7 days'
ORDER BY tenant_id
~~~

Một index duy nhất có thể không tối ưu cả hai. Thiết kế index phải dựa trên workload quan trọng nhất, không chỉ một query.

Thu thập:

- query count;
- p95/p99 latency;
- số row trả về;
- tần suất ghi;
- window thời gian dữ liệu;
- parameter phổ biến và hiếm.

Không index hóa mọi query riêng lẻ nếu các query có thể dùng chung một index có cấu trúc hợp lý.

---

## 5. INCLUDE và covering index

INCLUDE thêm column vào phần payload của index, không đưa chúng vào key order:

~~~sql
CREATE INDEX orders_customer_created_covering_idx
ON orders (customer_id, created_at DESC)
INCLUDE (id, status);
~~~

Phục vụ:

~~~sql
SELECT id, status, created_at
FROM orders
WHERE customer_id = 10
ORDER BY created_at DESC
LIMIT 20;
~~~

Nếu visibility map phù hợp, planner có thể chọn Index Only Scan.

INCLUDE không nên chứa mọi column:

- index lớn hơn;
- INSERT/UPDATE tốn hơn;
- column INCLUDE không giúp sort hoặc lookup như key;
- thay đổi payload có thể vẫn phải cập nhật index;
- index-only scan phụ thuộc vacuum và visibility.

Dùng INCLUDE khi query đọc phổ biến, select list ổn định và EXPLAIN chứng minh có lợi.

---

## 6. Partial index

Partial index chỉ lưu row thỏa predicate:

~~~sql
CREATE INDEX orders_active_customer_created_idx
ON orders (customer_id, created_at DESC)
WHERE status IN ('pending', 'paid');
~~~

Query tương thích:

~~~sql
SELECT id, status, created_at
FROM orders
WHERE customer_id = 10
  AND status = 'paid'
ORDER BY created_at DESC;
~~~

Lợi ích:

- index nhỏ;
- ít row phải maintain;
- tập trung vào dữ liệu nóng;
- có thể phù hợp với soft delete hoặc status.

Partial unique index:

~~~sql
CREATE UNIQUE INDEX users_active_email_unique
ON users (lower(email))
WHERE deleted_at IS NULL;
~~~

Email chỉ cần unique trong row chưa bị xóa mềm.

Điều kiện query phải đủ rõ để planner chứng minh row nằm trong predicate. Nếu predicate index quá phức tạp hoặc query viết khác cách, planner có thể không dùng.

---

## 7. Expression index

Expression index index kết quả của biểu thức:

~~~sql
CREATE INDEX users_lower_email_idx
ON users (lower(email));
~~~

Query:

~~~sql
SELECT id, email
FROM users
WHERE lower(email) = 'user@example.com';
~~~

Một số use case:

~~~sql
CREATE INDEX events_day_idx
ON events ((date_trunc('day', created_at)));
~~~

Expression index phù hợp khi:

- expression ổn định;
- query lặp lại pattern;
- không dễ rewrite predicate thành range;
- đã đo query trước/sau.

Trade-off:

- index phải cập nhật khi input thay đổi;
- expression cần nhất quán với query;
- application phải hiểu normalization;
- index không dùng cho expression khác dù tương đương về ý nghĩa nhưng khác cú pháp.

Nếu expression là dữ liệu nghiệp vụ muốn truy cập thường xuyên, generated column có thể dễ đọc hơn.

---

## 8. Partial và expression kết hợp

~~~sql
CREATE UNIQUE INDEX users_active_normalized_email_idx
ON users (lower(email))
WHERE deleted_at IS NULL;
~~~

Phục vụ:

~~~sql
SELECT id, email
FROM users
WHERE deleted_at IS NULL
  AND lower(email) = 'user@example.com';
~~~

Đây là một công cụ mạnh nhưng cần tài liệu rõ:

- predicate là gì;
- query nào dùng;
- dữ liệu nào bị loại;
- cách xử lý khi soft delete thay đổi;
- cách kiểm tra uniqueness.

---

## 9. GIN cho JSONB

GIN phù hợp với jsonb có nhiều key hoặc phần tử:

~~~sql
CREATE INDEX profiles_metadata_gin_idx
ON profiles USING GIN (metadata);
~~~

Query containment:

~~~sql
SELECT id
FROM profiles
WHERE metadata @> '{"plan": "pro"}';
~~~

Có thể dùng jsonb_path_ops cho một số pattern containment:

~~~sql
CREATE INDEX profiles_metadata_path_gin_idx
ON profiles USING GIN (metadata jsonb_path_ops);
~~~

Lựa chọn opclass phụ thuộc operator và workload. Không dùng jsonb_path_ops nếu query cần operator mà opclass đó không phục vụ.

GIN trade-off:

- index có thể lớn;
- INSERT/UPDATE jsonb tốn hơn;
- maintenance và vacuum quan trọng;
- index không biến mọi JSON query thành nhanh.

Field dùng để JOIN, filter hoặc constraint thường xuyên nên là column riêng.

---

## 10. GIN cho full-text search

Full-text search thường dùng tsvector và GIN:

~~~sql
ALTER TABLE books
ADD COLUMN search_vector tsvector
GENERATED ALWAYS AS (
    to_tsvector(
        'simple',
        coalesce(title, '') || ' ' || coalesce(description, '')
    )
) STORED;

CREATE INDEX books_search_vector_gin_idx
ON books USING GIN (search_vector);
~~~

Query:

~~~sql
SELECT
    id,
    title,
    ts_rank(search_vector, query) AS rank
FROM books,
     websearch_to_tsquery('simple', 'postgres database') AS query
WHERE search_vector @@ query
ORDER BY rank DESC;
~~~

Dùng generated column giúp query và index dùng cùng expression. Configuration phải phù hợp ngôn ngữ dữ liệu; simple không cung cấp stemming như language-specific configuration.

ILIKE với wildcard và full-text search giải quyết các nhu cầu khác nhau. Đo cả hai trên workload thật.

---

## 11. GiST ở mức thực hành

GiST là framework index cho nhiều kiểu dữ liệu và operator, thường gặp với:

- range types;
- geometric types;
- PostGIS;
- exclusion constraint;
- một số full-text hoặc similarity use case.

Range query:

~~~sql
CREATE TABLE room_bookings (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    room_id bigint NOT NULL,
    booked_during tstzrange NOT NULL
);

CREATE INDEX room_bookings_during_gist_idx
ON room_bookings USING GIST (booked_during);
~~~

Tìm các booking giao nhau:

~~~sql
SELECT id, room_id, booked_during
FROM room_bookings
WHERE booked_during && tstzrange(
    timestamptz '2026-01-10 10:00:00+00',
    timestamptz '2026-01-10 12:00:00+00'
);
~~~

Ngăn booking cùng phòng bị overlap bằng exclusion constraint:

~~~sql
CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE room_bookings
ADD CONSTRAINT room_bookings_no_overlap
EXCLUDE USING GIST (
    room_id WITH =,
    booked_during WITH &&
);
~~~

GiST không phải B-tree nhanh hơn. Operator class và dữ liệu quyết định khả năng sử dụng.

---

## 12. BRIN cho table lớn theo thời gian

BRIN lưu summary theo block range:

~~~sql
CREATE INDEX events_created_brin_idx
ON events USING BRIN (created_at);
~~~

Phù hợp khi:

- table rất lớn;
- created_at tương quan với thứ tự vật lý;
- query thường lọc range;
- dữ liệu append gần theo thời gian.

Không phù hợp nếu row được insert ngẫu nhiên theo created_at. Kiểm tra correlation trong pg_stats và đo buffers.

BRIN nhỏ hơn B-tree nhưng không cung cấp lookup chính xác từng row theo cùng cách. Nó giúp loại block chắc chắn không chứa giá trị cần tìm.

---

## 13. CREATE INDEX CONCURRENTLY

CREATE INDEX thông thường có thể giữ lock ảnh hưởng tới ghi trong một số giai đoạn. CREATE INDEX CONCURRENTLY giảm ảnh hưởng tới concurrent write:

~~~sql
CREATE INDEX CONCURRENTLY events_tenant_created_idx
ON events (tenant_id, created_at DESC);
~~~

Đặc điểm:

- chạy lâu hơn;
- đọc table nhiều lần;
- tốn tài nguyên;
- không được chạy bên trong transaction block thông thường;
- có thể để lại invalid index nếu thất bại;
- cần kiểm tra và xử lý migration failure.

Trong migration tool, cần hỗ trợ migration ngoài transaction nếu muốn dùng CONCURRENTLY.

Kiểm tra index invalid:

~~~sql
SELECT
    indexrelid::regclass AS index_name,
    indisvalid,
    indisready
FROM pg_index
WHERE NOT indisvalid
   OR NOT indisready;
~~~

Nếu CREATE INDEX CONCURRENTLY thất bại, không tự tạo lại với tên khác mà chưa kiểm tra index invalid và nguyên nhân.

---

## 14. REINDEX CONCURRENTLY

REINDEX có thể xây lại index. REINDEX CONCURRENTLY giảm block ghi hơn nhưng tốn thêm thời gian và disk:

~~~sql
REINDEX INDEX CONCURRENTLY events_tenant_created_idx;
~~~

Dùng khi:

- index bloat;
- thay đổi opclass hoặc collation cần rebuild;
- index cần tái tạo sau sự cố;
- có kế hoạch maintenance.

Không chạy hàng loạt trong giờ cao điểm mà chưa có capacity plan.

---

## 15. Index bloat

Index bloat là không gian dư hoặc cấu trúc index phình lên do UPDATE, DELETE, page split và pattern ghi.

Bloat có thể làm:

- index lớn;
- đọc nhiều page;
- cache kém hiệu quả;
- vacuum và backup nặng;
- write amplification tăng.

Xem kích thước index:

~~~sql
SELECT
    schemaname,
    relname,
    indexrelname,
    pg_size_pretty(pg_relation_size(indexrelid)) AS index_size,
    idx_scan
FROM pg_stat_user_indexes
ORDER BY pg_relation_size(indexrelid) DESC;
~~~

Kích thước lớn không tự chứng minh bloat. Cần extension hoặc phương pháp đo phù hợp trước khi rebuild.

Nguyên nhân liên quan:

- HOT update không thể xảy ra nếu update indexed column;
- page split;
- delete nhiều;
- fillfactor;
- autovacuum không theo kịp;
- index thừa.

---

## 16. Tác động index lên HOT update

PostgreSQL có thể thực hiện HOT update khi không cần cập nhật index cho các column thay đổi và còn chỗ trên page.

Nếu column được update nằm trong index, HOT update thường không áp dụng được. Nhiều index trên table nóng có thể làm write nặng hơn.

Ví dụ table có updated_at thay đổi mỗi request:

~~~sql
UPDATE sessions
SET updated_at = now()
WHERE id = 10;
~~~

Nếu updated_at nằm trong nhiều index, mỗi update phải maintain nhiều index.

Không index column chỉ để “có thể sort” nếu workload thực tế không cần.

---

## 17. Index và write amplification

Mỗi row insert có thể phải ghi:

- heap table;
- primary key index;
- unique index;
- foreign key supporting index;
- business query indexes;
- GIN hoặc GiST index.

Một table write-heavy với 8 index có thể chậm hơn đáng kể so với table có 2 index.

Đánh giá index theo cả hai chiều:

~~~text
Read benefit
Write cost
Storage
Vacuum cost
Backup cost
Planning alternatives
~~~

Index của constraint bắt buộc khác với index tối ưu query. Primary key và unique thường phải giữ vì tính toàn vẹn; index phục vụ query có thể bỏ nếu không còn workload.

---

## 18. Tìm index không dùng hoặc trùng

Xem usage:

~~~sql
SELECT
    schemaname,
    relname,
    indexrelname,
    idx_scan,
    idx_tup_read,
    idx_tup_fetch,
    pg_size_pretty(pg_relation_size(indexrelid)) AS size
FROM pg_stat_user_indexes
ORDER BY idx_scan ASC;
~~~

Cẩn thận:

- statistics có thể reset;
- database có thể mới restart;
- workload theo mùa;
- query hiếm nhưng quan trọng;
- index unique có thể có idx_scan thấp nhưng vẫn bảo vệ constraint.

So sánh định nghĩa index:

~~~sql
SELECT
    indexname,
    indexdef
FROM pg_indexes
WHERE tablename = 'orders';
~~~

Tìm index gần trùng cần xem thứ tự key, partial predicate, INCLUDE và opclass. Không xóa chỉ vì tên giống nhau.

---

## 19. Thiết kế index từ query

Query:

~~~sql
SELECT id, status, created_at
FROM orders
WHERE customer_id = 10
  AND status = 'paid'
ORDER BY created_at DESC
LIMIT 20;
~~~

Ứng viên:

~~~sql
CREATE INDEX orders_customer_status_created_idx
ON orders (customer_id, status, created_at DESC)
INCLUDE (id);
~~~

Nhưng trước khi tạo:

1. query có chạy thường xuyên không?
2. status paid có phổ biến không?
3. partial index có nhỏ hơn không?
4. id và status đã là key hay payload phù hợp chưa?
5. query có cần nhiều column khác khiến index-only không thực tế không?
6. table có write-heavy không?

Không có index “đúng” độc lập với workload.

---

## 20. Lab benchmark

Tạo table:

~~~sql
CREATE TABLE events (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id bigint NOT NULL,
    event_type text NOT NULL,
    created_at timestamptz NOT NULL,
    payload jsonb NOT NULL DEFAULT '{}'::jsonb
);

INSERT INTO events (tenant_id, event_type, created_at, payload)
SELECT
    1 + (g % 10000),
    CASE WHEN g % 2 = 0 THEN 'login' ELSE 'view' END,
    now() - (g % 365) * interval '1 day',
    jsonb_build_object('sequence', g)
FROM generate_series(1, 1000000) AS g;

ANALYZE events;
~~~

Baseline:

~~~sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT id, event_type, created_at
FROM events
WHERE tenant_id = 10
  AND created_at >= now() - interval '30 days'
ORDER BY created_at DESC
LIMIT 100;
~~~

Tạo index:

~~~sql
CREATE INDEX events_tenant_created_idx
ON events (tenant_id, created_at DESC);
~~~

Đo lại:

~~~sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT id, event_type, created_at
FROM events
WHERE tenant_id = 10
  AND created_at >= now() - interval '30 days'
ORDER BY created_at DESC
LIMIT 100;
~~~

Thử covering index ở môi trường khác:

~~~sql
CREATE INDEX events_tenant_created_covering_idx
ON events (tenant_id, created_at DESC)
INCLUDE (event_type);
~~~

So sánh:

- scan node;
- execution time;
- estimated/actual rows;
- shared hit/read;
- index size;
- chi phí insert batch.

Benchmark write:

~~~sql
EXPLAIN (ANALYZE, BUFFERS)
INSERT INTO events (tenant_id, event_type, created_at)
SELECT
    10,
    'test',
    now()
FROM generate_series(1, 10000);
~~~

Không dùng một lần chạy để kết luận. Ghi môi trường, cache condition và số lần lặp.

---

## 21. CREATE INDEX CONCURRENTLY lab

Trên table lớn ở lab:

~~~sql
CREATE INDEX CONCURRENTLY events_type_created_idx
ON events (event_type, created_at DESC);
~~~

Theo dõi progress:

~~~sql
SELECT
    pid,
    datname,
    relid::regclass AS table_name,
    index_relid::regclass AS index_name,
    phase,
    lockers_total,
    lockers_done,
    blocks_total,
    blocks_done,
    tuples_total,
    tuples_done
FROM pg_stat_progress_create_index;
~~~

Kiểm tra sau khi hoàn tất:

~~~sql
SELECT
    indexrelid::regclass AS index_name,
    indisvalid,
    indisready
FROM pg_index
WHERE indexrelid::regclass::text LIKE 'events%';
~~~

Ghi thời gian, lock behavior, disk usage và tác động tới write.

---

## 22. Checklist review một index

Trước khi merge migration:

- Query nào cần index?
- Có EXPLAIN baseline không?
- Predicate, sort và join key đã được xem chưa?
- Column order có lý do không?
- Có thể dùng index hiện có không?
- Có index partial, expression hoặc covering phù hợp hơn không?
- Index có trùng hoặc gần trùng index khác không?
- Table write-heavy đến mức nào?
- Tạo thường hay CONCURRENTLY?
- Cần migration ngoài transaction không?
- Disk và build time đủ không?
- Có kế hoạch rollback/drop không?
- Sau deploy kiểm tra idx_scan và latency ở đâu?

---

## 23. Bài tập thực hành

### Bài 1 — Composite index

Thiết kế index cho query lấy 20 order paid mới nhất của một customer. Giải thích equality, status và created_at.

### Bài 2 — Equality và range

So sánh index (tenant_id, created_at) với (created_at, tenant_id) bằng EXPLAIN trên query có tenant equality và created_at range.

### Bài 3 — Covering index

Thử INCLUDE các column select list. Kiểm tra planner có dùng Index Only Scan không.

### Bài 4 — Partial index

Tạo index chỉ cho order pending hoặc paid. So sánh query compatible và query không compatible.

### Bài 5 — Expression index

Tạo index lower(email), đo query tìm email không phân biệt hoa thường trước/sau.

### Bài 6 — GIN JSONB

Tạo GIN index cho metadata và đo query containment.

### Bài 7 — GiST range

Tạo room_bookings với tstzrange và exclusion constraint chống overlap.

### Bài 8 — BRIN

Tạo events append theo thời gian, so sánh B-tree và BRIN về kích thước và plan.

### Bài 9 — Concurrent index

Tạo index concurrently trên table lớn, theo dõi pg_stat_progress_create_index và ghi thời gian.

### Bài 10 — Write amplification

Đo batch INSERT khi table không có index query, có một index và có nhiều index. Viết kết luận.

---

## 24. Tiêu chí hoàn thành Tuần 3

Bạn hoàn thành bài học khi có thể:

- Thiết kế composite index theo equality, range và sort.
- Giải thích leftmost prefix.
- Dùng INCLUDE cho covering index khi có bằng chứng.
- Dùng partial index cho tập dữ liệu ổn định.
- Dùng expression index cho pattern function ổn định.
- Chọn GIN cho jsonb hoặc full-text search ở mức phù hợp.
- Chọn GiST cho range và exclusion constraint cơ bản.
- Chọn BRIN cho table lớn có correlation theo thời gian.
- Tạo index bằng CREATE INDEX CONCURRENTLY và hiểu giới hạn của nó.
- Nhận biết index bloat và các yếu tố làm write amplification.
- Tìm index không dùng nhưng không xóa máy móc.
- Benchmark trước/sau bằng EXPLAIN ANALYZE, BUFFERS.
- Viết checklist và migration có lý do rõ ràng.

## Tóm tắt cần nhớ

~~~text
Composite index phụ thuộc thứ tự key và workload.
Equality thường đặt trước range hoặc sort.
INCLUDE có thể giúp index-only scan nhưng làm index lớn hơn.
Partial index nhỏ và hữu ích khi predicate ổn định.
Expression index phù hợp với function query lặp lại.
GIN phù hợp với jsonb và full-text.
GiST phù hợp với range, overlap và exclusion constraint.
BRIN phù hợp với table rất lớn có correlation vật lý.
CREATE INDEX CONCURRENTLY giảm ảnh hưởng lock nhưng lâu và phức tạp hơn.
Mỗi index làm write, vacuum, backup và storage tốn hơn.
Đo trước/sau; không ép planner hoặc xóa index chỉ dựa trên cảm giác.
~~~

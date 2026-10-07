# Tuần 1 nâng cao: EXPLAIN, planner và cardinality estimate

Mục tiêu:

- hiểu parse, rewrite, plan và execute;
- đọc Seq Scan, Index Scan, Index Only Scan, Bitmap Scan;
- đọc Nested Loop, Hash Join và Merge Join;
- hiểu estimated cost, startup cost, actual time và planning time;
- so sánh estimated rows với actual rows;
- dùng ANALYZE và statistics target;
- hoàn thành planner lab trên bảng events lớn.

Nguyên tắc: không kết luận query chỉ bằng cost hoặc tên node. Hãy nhìn actual time, buffers, loops, estimated rows và actual rows trên dữ liệu đại diện.

---

## 1. Vòng đời một query

~~~text
SQL text
  → Parse
  → Rewrite
  → Plan
  → Execute
  → Return rows
~~~

Parse kiểm tra cú pháp và tên cơ bản. Rewrite mở rộng view hoặc rule. Planner tạo nhiều kế hoạch khả thi rồi chọn kế hoạch có cost thấp nhất theo model. Executor chạy plan.

Planner phải quyết định:

- Seq Scan hay Index Scan;
- thứ tự join;
- Nested Loop, Hash Join hay Merge Join;
- có sort, aggregate hoặc parallel hay không;
- index nào và điều kiện nào dùng được.

Nếu query chậm, lỗi có thể nằm ở SQL, statistics, index, dữ liệu phân bố hoặc tài nguyên. Không phải mọi lỗi đều sửa bằng cách thêm index.

---

## 2. Đọc execution plan

Plan là một cây node. Node phía trên nhận dữ liệu từ node con.

~~~text
Limit
└── Sort
    └── Index Scan
~~~

Đọc từ dưới lên để biết dữ liệu được tạo ra, sau đó đọc lên để biết cách dữ liệu được sort, join, aggregate hoặc giới hạn.

Ví dụ:

~~~text
Limit
  -> Index Scan using orders_customer_created_idx on orders
       Index Cond: customer_id = 10
~~~

Các thông tin thường gặp:

- startup cost;
- total cost;
- estimated rows;
- estimated width;
- actual time;
- actual rows;
- loops;
- Index Cond;
- Filter;
- Buffers.

---

## 3. Cost và thời gian

EXPLAIN có dạng:

~~~text
(cost=0.42..18.71 rows=20 width=48)
~~~

Startup cost là chi phí trước khi trả row đầu. Total cost là chi phí ước lượng để trả toàn bộ output của node.

Cost không phải milliseconds. Đây là đơn vị tương đối của cost model. Nó hữu ích để so sánh các plan trong cùng môi trường, không nên dùng để so sánh trực tiếp hai database khác cấu hình.

EXPLAIN ANALYZE có thể cho:

~~~text
Planning Time: 0.420 ms
Execution Time: 12.830 ms
~~~

Planning time là thời gian tạo plan. Execution time là thời gian chạy plan. Query nhiều join có thể có planning time đáng kể; query nhỏ chạy rất nhiều lần cũng cần theo dõi planning overhead.

---

## 4. EXPLAIN và EXPLAIN ANALYZE

EXPLAIN chỉ tạo plan:

~~~sql
EXPLAIN
SELECT id, customer_id, created_at
FROM orders
WHERE customer_id = 10
ORDER BY created_at DESC
LIMIT 20;
~~~

EXPLAIN ANALYZE chạy query thật:

~~~sql
EXPLAIN (ANALYZE, BUFFERS, VERBOSE)
SELECT id, customer_id, created_at
FROM orders
WHERE customer_id = 10
ORDER BY created_at DESC
LIMIT 20;
~~~

BUFFFERS cho biết page được đọc từ shared buffers hoặc storage. VERBOSE hiển thị chi tiết hơn.

EXPLAIN ANALYZE với UPDATE hoặc DELETE sẽ thay đổi dữ liệu. Trong lab có thể rollback:

~~~sql
BEGIN;

EXPLAIN (ANALYZE, BUFFERS)
DELETE FROM books
WHERE id = 10;

ROLLBACK;
~~~

Không dùng cách này cho function có side effect bên ngoài database. Không chạy destructive EXPLAIN ANALYZE trên production chỉ để xem plan.

---

## 5. Các scan node

### Seq Scan

Seq Scan đọc toàn bộ table:

~~~text
Seq Scan on events
  Filter: tenant_id = 10
~~~

Seq Scan có thể là lựa chọn đúng khi table nhỏ, điều kiện trả phần lớn row hoặc index không giúp giảm đủ I/O.

### Index Scan

Index Scan tìm key bằng index rồi truy cập heap:

~~~text
Index Scan using events_tenant_created_idx on events
  Index Cond: tenant_id = 10
~~~

Nếu có Filter loại bỏ nhiều row sau khi lấy từ index, có thể cần xem lại index hoặc query.

### Index Only Scan

Index Only Scan có thể trả dữ liệu từ index nếu index chứa đủ column và visibility map phù hợp. Nó không được bảo đảm trong mọi thời điểm.

~~~sql
CREATE INDEX events_tenant_created_covering_idx
ON events (tenant_id, created_at DESC)
INCLUDE (event_type);
~~~

### Bitmap Index Scan và Bitmap Heap Scan

Bitmap Index Scan thu thập vị trí phù hợp; Bitmap Heap Scan đọc các page liên quan. Planner thường chọn bitmap khi cần nhiều row nhưng chưa đến mức Seq Scan rẻ hơn.

Đánh giá scan bằng actual time, actual rows và buffers, không chỉ bằng tên node.

---

## 6. Join strategy

### Nested Loop

Nested Loop lấy từng row của outer relation rồi tìm row tương ứng ở inner relation.

~~~text
Nested Loop
  -> Index Scan on customers
  -> Index Scan on orders
       Index Cond: customer_id = customers.id
~~~

Phù hợp khi outer relation nhỏ và inner có index tốt. Có thể rất chậm nếu outer có nhiều row và inner bị lặp hàng triệu lần.

### Hash Join

Hash Join xây hash table từ một input rồi probe bằng input kia.

~~~text
Hash Join
  Hash Cond: orders.customer_id = customers.id
  -> Seq Scan on orders
  -> Hash
       -> Seq Scan on customers
~~~

Phù hợp với equijoin trên tập dữ liệu đủ lớn. Hash có thể spill nếu thiếu memory.

### Merge Join

Merge Join đi qua hai input đã được sắp xếp theo join key.

~~~text
Merge Join
  Merge Cond: orders.customer_id = customers.id
~~~

Phù hợp khi input đã có thứ tự hoặc có thể sort với chi phí hợp lý.

Không có join strategy luôn tốt nhất. Hãy nhìn cardinality, loops, sort, buffers và actual time.

---

## 7. Cardinality estimate

Cardinality estimate là số row planner dự đoán một node sẽ trả về.

~~~text
(cost=0.00..100.00 rows=100 width=64)
~~~

Với EXPLAIN ANALYZE:

~~~text
(actual time=0.100..40.000 rows=50000 loops=1)
~~~

Trong ví dụ này estimated rows là 100 nhưng actual rows là 50.000. Sai số lớn có thể khiến planner:

- chọn Nested Loop thay vì Hash Join;
- chọn Index Scan thay vì Seq Scan;
- cấp sai memory cho hash hoặc sort;
- chọn thứ tự join không tốt;
- bỏ qua parallel plan phù hợp.

Estimated rows không phải kết quả thật. Nó là dự đoán dựa trên statistics và selectivity model.

---

## 8. Statistics và ANALYZE

Planner dùng:

- số row và page;
- histogram;
- most-common values;
- null fraction;
- distinct estimate;
- correlation;
- constraint;
- operator selectivity.

Xem statistics:

~~~sql
SELECT
    tablename,
    attname,
    null_frac,
    n_distinct,
    most_common_vals,
    most_common_freqs,
    histogram_bounds,
    correlation
FROM pg_stats
WHERE tablename = 'events';
~~~

Sau bulk load hoặc thay đổi dữ liệu lớn:

~~~sql
ANALYZE events;
~~~

Hoặc:

~~~sql
ANALYZE;
~~~

ANALYZE cập nhật statistics, không tạo index và không sửa row.

Xem trạng thái analyze:

~~~sql
SELECT
    relname,
    n_live_tup,
    n_dead_tup,
    last_analyze,
    last_autoanalyze
FROM pg_stat_user_tables
WHERE relname = 'events';
~~~

---

## 9. Statistics target

Đặt target riêng cho column quan trọng:

~~~sql
ALTER TABLE events
ALTER COLUMN tenant_id SET STATISTICS 500;

ANALYZE events;
~~~

Target cao có thể giúp column có phân bố lệch hoặc nhiều giá trị quan trọng, nhưng làm ANALYZE và planning tốn thêm tài nguyên. Không tăng mọi column một cách máy móc.

---

## 10. Dữ liệu lệch và correlation

Giả sử tenant 1 chiếm 80 phần trăm events, còn tenant 999 chỉ có rất ít row. Hai query sau có thể cần plan khác nhau:

~~~sql
SELECT *
FROM events
WHERE tenant_id = 1;

SELECT *
FROM events
WHERE tenant_id = 999;
~~~

Planner cần biết tenant 1 là most-common value. Nếu statistics cũ hoặc sample không phản ánh phân bố, estimate có thể sai.

Hai column cũng có thể tương quan:

~~~sql
WHERE country = 'VN'
  AND city = 'Hanoi'
~~~

Planner có thể giả định độc lập và ước lượng thấp hoặc cao. Extended statistics:

~~~sql
CREATE STATISTICS events_country_city_stats
    (dependencies, ndistinct, mcv)
ON country, city
FROM events;

ANALYZE events;
~~~

Extended statistics bổ sung thông tin cho planner; nó không tạo index.

---

## 11. Function và khả năng dùng index

Điều kiện bọc column bằng function thường khó dùng index thường:

~~~sql
WHERE date_trunc('day', created_at) = DATE '2026-01-10'
~~~

Viết range:

~~~sql
WHERE created_at >= timestamptz '2026-01-10 00:00:00+00'
  AND created_at <  timestamptz '2026-01-11 00:00:00+00'
~~~

Nếu expression là yêu cầu ổn định, có thể dùng expression index:

~~~sql
CREATE INDEX users_lower_email_idx
ON users (lower(email));
~~~

Hoặc generated column. Không rewrite mọi function một cách máy móc; hãy đo plan trước và sau.

---

## 12. Lab events

Tạo table và một triệu row:

~~~sql
DROP TABLE IF EXISTS events;

CREATE TABLE events (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id bigint NOT NULL,
    event_type text NOT NULL,
    created_at timestamptz NOT NULL,
    payload jsonb NOT NULL DEFAULT '{}'::jsonb
);

INSERT INTO events (tenant_id, event_type, created_at, payload)
SELECT
    CASE
        WHEN g <= 700000 THEN 1
        ELSE 2 + (g % 998)
    END,
    CASE
        WHEN g % 2 = 0 THEN 'login'
        WHEN g % 5 = 0 THEN 'purchase'
        ELSE 'view'
    END,
    now() - (g % 365) * interval '1 day',
    jsonb_build_object('sequence', g)
FROM generate_series(1, 1000000) AS g;
~~~

Đo plan trước index:

~~~sql
EXPLAIN (ANALYZE, BUFFERS, VERBOSE)
SELECT id, tenant_id, event_type, created_at
FROM events
WHERE tenant_id = 10
  AND created_at >= now() - interval '30 days';
~~~

Tạo index, analyze và đo lại:

~~~sql
CREATE INDEX events_tenant_created_idx
ON events (tenant_id, created_at DESC);

ANALYZE events;

EXPLAIN (ANALYZE, BUFFERS, VERBOSE)
SELECT id, tenant_id, event_type, created_at
FROM events
WHERE tenant_id = 10
  AND created_at >= now() - interval '30 days';
~~~

Đo thêm tenant phổ biến:

~~~sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT id, tenant_id, event_type, created_at
FROM events
WHERE tenant_id = 1
  AND created_at >= now() - interval '30 days';
~~~

Ghi lại scan node, estimated rows, actual rows, actual time, loops, shared hit và shared read. Index có thể hữu ích cho tenant hiếm nhưng không đáng dùng cho tenant chiếm phần lớn table.

---

## 13. Lab join

~~~sql
CREATE TABLE tenants (
    id bigint PRIMARY KEY,
    name text NOT NULL
);

INSERT INTO tenants (id, name)
SELECT g, 'Tenant ' || g
FROM generate_series(1, 999) AS g;

ANALYZE tenants;
~~~

Đo join:

~~~sql
EXPLAIN (ANALYZE, BUFFERS, VERBOSE)
SELECT
    t.id,
    t.name,
    count(e.id) AS event_count
FROM tenants AS t
JOIN events AS e ON e.tenant_id = t.id
GROUP BY t.id, t.name;
~~~

Quan sát:

- Hash Join, Nested Loop hoặc Merge Join;
- estimated rows của join;
- aggregate chạy trước hay sau join;
- node có loops cao;
- buffers của events;
- chênh lệch estimated và actual rows.

Thử query chỉ một tenant và so sánh plan. Khi cardinality outer relation thay đổi, join strategy có thể thay đổi.

---

## 14. Generic plan và custom plan

Prepared statement có thể dùng custom plan cho parameter cụ thể hoặc generic plan dùng chung.

Nếu tenant nhỏ và tenant lớn có selectivity rất khác, một generic plan có thể không tối ưu cho mọi parameter.

Khi debug query parameterized:

- kiểm tra query chạy qua prepared statement thật;
- so sánh plan với literal và parameter;
- xem workload có parameter skew không;
- không kết luận production từ một giá trị test duy nhất.

Đây là chủ đề sẽ học sâu hơn ở Tuần 2 cùng generic plan, custom plan và parameterized query.

---

## 15. Planner settings ở mức lab

Có thể tạm tắt một node để so sánh:

~~~sql
BEGIN;

SET LOCAL enable_seqscan = off;

EXPLAIN (ANALYZE, BUFFERS)
SELECT *
FROM events
WHERE tenant_id = 10;

ROLLBACK;
~~~

Hoặc tạm tắt Nested Loop:

~~~sql
SET LOCAL enable_nestloop = off;
~~~

Các setting này dùng để chẩn đoán, không phải giải pháp production lâu dài. Nếu plan ép buộc tốt hơn, hãy tìm nguyên nhân thật:

- statistics sai;
- index thiếu;
- query không sargable;
- dữ liệu correlation;
- cost setting không phù hợp;
- estimate sai do nhiều điều kiện.

---

## 16. Cách viết planner lab report

Báo cáo cần có:

~~~text
Query
Môi trường và dữ liệu
Plan trước thay đổi
Node tốn thời gian nhất
Estimated rows và actual rows
Buffers
Nguyên nhân
Thay đổi đã thử
Plan sau thay đổi
Tác dụng phụ
Kết luận
~~~

Mẫu:

~~~text
# Planner lab report

## Query
...

## Dataset
...

## Plan before
...

## Diagnosis
...

## Change
...

## Plan after
...

## Side effects
...

## Conclusion
...
~~~

Bảng so sánh:

| Metric | Trước | Sau |
|---|---:|---:|
| Planning time | ... | ... |
| Execution time | ... | ... |
| Estimated rows | ... | ... |
| Actual rows | ... | ... |
| Shared read | ... | ... |
| Node chính | ... | ... |

Đừng chỉ ghi sau nhanh hơn. Hãy giải thích vì sao plan thay đổi và kết quả có ổn định với parameter khác hay không.

---

## 17. Bài tập thực hành

### Bài 1 — Đọc plan

Chạy EXPLAIN cho query tìm events theo tenant. Xác định scan node, estimated rows và cost.

### Bài 2 — Đo actual

Chạy EXPLAIN ANALYZE, BUFFERS. Ghi actual time, actual rows, loops, shared hit và shared read.

### Bài 3 — So sánh index

Tạo index trên tenant_id và created_at. Đo trước/sau và giải thích plan.

### Bài 4 — Tenant lệch phân phối

So sánh tenant hiếm và tenant chiếm phần lớn dữ liệu. Planner có chọn cùng plan không?

### Bài 5 — ANALYZE

Chạy query trước và sau ANALYZE sau bulk insert. Estimate thay đổi thế nào?

### Bài 6 — Join strategy

Join events với tenants. Giải thích Hash Join, Nested Loop hoặc Merge Join.

### Bài 7 — Statistics target

Tăng statistics target cho tenant_id, chạy ANALYZE và so sánh estimated rows.

### Bài 8 — Extended statistics

Tạo hai column tương quan, tạo extended statistics rồi so sánh estimate trước/sau.

### Bài 9 — Function trong WHERE

So sánh date_trunc trên created_at với điều kiện range. Dùng EXPLAIN kiểm tra khả năng dùng index.

### Bài 10 — Báo cáo cuối

Viết planner lab report theo mẫu và đưa ra khuyến nghị có bằng chứng.

---

## 18. Tiêu chí hoàn thành Tuần 1

Bạn hoàn thành bài học khi có thể:

- Mô tả parse, rewrite, plan và execute.
- Đọc execution plan như một cây node.
- Phân biệt startup cost, total cost, planning time và execution time.
- Dùng EXPLAIN và EXPLAIN ANALYZE đúng ngữ cảnh.
- Hiểu Seq Scan, Index Scan, Index Only Scan, Bitmap Index Scan và Bitmap Heap Scan.
- Phân biệt Nested Loop, Hash Join và Merge Join.
- Đọc estimated rows và actual rows.
- Giải thích cardinality estimate ảnh hưởng tới plan.
- Chạy ANALYZE sau bulk load.
- Đọc pg_stats ở mức cơ bản.
- Nhận biết dữ liệu lệch và column correlation.
- Biết statistics target không chữa được mọi query.
- Không kết luận chỉ bằng cost hoặc tên node.
- Hoàn thành planner lab có plan trước/sau và bằng chứng từ buffers.

## Tóm tắt cần nhớ

~~~text
Planner chọn plan dựa trên cost model và statistics.
Cost không phải milliseconds.
EXPLAIN là estimate; EXPLAIN ANALYZE chạy query thật.
Estimated rows sai có thể dẫn tới join strategy và access path sai.
Seq Scan không luôn xấu.
Nested Loop phù hợp outer nhỏ và inner có access path tốt.
Hash Join phù hợp equijoin trên tập lớn.
Merge Join cần input có thứ tự phù hợp.
ANALYZE cập nhật statistics sau thay đổi dữ liệu lớn.
Dữ liệu lệch và column correlation làm cardinality khó ước lượng.
Đọc plan từ dưới lên và so estimated rows với actual rows.
Kết luận tối ưu phải dựa trên đo lường và workload đại diện.
~~~

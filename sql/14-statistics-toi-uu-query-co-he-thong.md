# Tuần 2 nâng cao: Statistics và tối ưu query có hệ thống

> Mục tiêu: hiểu PostgreSQL dùng statistics như thế nào để ước lượng cardinality, biết đọc pg_stats, xử lý dữ liệu lệch và correlated columns, dùng extended statistics, phân biệt generic/custom plan, viết query sargable và xây quy trình tối ưu có bằng chứng.

Tuần 1 tập trung đọc execution plan. Tuần này tập trung vào câu hỏi:

~~~text
Vì sao planner ước lượng sai?
Statistics đang nói gì về dữ liệu?
Query có để planner dùng index không?
Parameter thật có làm plan thay đổi không?
Cách sửa có cải thiện query nhưng làm hại write hoặc parameter khác không?
~~~

Nguyên tắc làm việc:

1. Đo query trước thay đổi.
2. Xác định estimate sai hoặc node tốn thời gian.
3. Kiểm tra statistics và dữ liệu thật.
4. Thay đổi một yếu tố mỗi lần.
5. Đo lại cùng workload.
6. Ghi tác dụng phụ và điều kiện áp dụng.

---

## 1. Statistics dùng để làm gì?

Planner không thể đọc toàn bộ dữ liệu mỗi lần cần chọn plan. Nó dùng statistics được thu thập bởi ANALYZE để ước lượng:

- số row của table;
- số giá trị khác nhau;
- tỷ lệ NULL;
- giá trị phổ biến;
- phân bố theo histogram;
- tương quan giữa giá trị column và vị trí vật lý;
- quan hệ giữa nhiều column nếu có extended statistics.

Ví dụ query:

~~~sql
SELECT id, created_at
FROM events
WHERE tenant_id = 10
  AND event_type = 'login';
~~~

Planner cần ước lượng số row thỏa điều kiện để quyết định:

- Seq Scan hay Index Scan;
- Hash Join hay Nested Loop nếu query join;
- sort hoặc hash cần bao nhiêu memory;
- có nên parallel hay không.

Nếu estimate gần với actual, plan thường có cơ hội tốt hơn. Estimate không cần chính xác tuyệt đối cho mọi query, nhưng sai hàng trăm hoặc hàng nghìn lần là tín hiệu cần điều tra.

---

## 2. ANALYZE

ANALYZE lấy sample và cập nhật statistics:

~~~sql
ANALYZE events;
~~~

Analyze một column:

~~~sql
ANALYZE events (tenant_id, event_type);
~~~

Analyze toàn database:

~~~sql
ANALYZE;
~~~

Các trường hợp cần chú ý:

- vừa bulk load dữ liệu;
- vừa seed hàng triệu row;
- phân phối dữ liệu thay đổi mạnh;
- bulk UPDATE hoặc DELETE;
- table mới tạo;
- query có estimate bất thường.

Autovacuum thường tự chạy autoanalyze dựa trên thay đổi dữ liệu, nhưng không nên chờ mù quáng sau một migration hoặc bulk load quan trọng. Kiểm tra trạng thái:

~~~sql
SELECT
    relname,
    n_live_tup,
    n_dead_tup,
    last_analyze,
    last_autoanalyze
FROM pg_stat_user_tables
WHERE relname IN ('events', 'orders', 'order_items');
~~~

ANALYZE không:

- tạo index;
- sửa dữ liệu;
- làm query luôn nhanh;
- thay thế VACUUM;
- thay đổi constraint.

Nó chỉ cập nhật thông tin để planner ra quyết định tốt hơn.

---

## 3. Đọc pg_stats

pg_stats là view thân thiện để đọc statistics của column:

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

Các field quan trọng:

| Field | Ý nghĩa |
|---|---|
| null_frac | tỷ lệ giá trị NULL |
| n_distinct | ước lượng số giá trị khác nhau |
| most_common_vals | các giá trị xuất hiện nhiều |
| most_common_freqs | tần suất của các giá trị phổ biến |
| histogram_bounds | biên histogram cho giá trị còn lại |
| correlation | tương quan giữa giá trị và thứ tự vật lý |

pg_stats có thể chỉ hiển thị sample, không phải toàn bộ dữ liệu. Số liệu là estimate và phụ thuộc statistics target.

---

## 4. Histogram

Với column có nhiều giá trị khác nhau, planner dùng histogram để ước lượng selectivity của range:

~~~sql
SELECT *
FROM events
WHERE created_at >= timestamptz '2026-01-01'
  AND created_at <  timestamptz '2026-02-01';
~~~

Histogram chia miền giá trị thành các khoảng. Planner ước lượng tỷ lệ row nằm trong khoảng query.

Histogram không mô tả tốt mọi phân phối nếu:

- dữ liệu có nhiều cụm;
- có outlier;
- dữ liệu lệch mạnh;
- statistics target quá thấp;
- column mới bulk load nhưng chưa ANALYZE.

Range query có thể estimate khác nhiều nếu biên query rơi vào vùng dữ liệu ít sample.

---

## 5. Most-common values

Với column có một số giá trị xuất hiện rất nhiều, PostgreSQL lưu most-common values.

Ví dụ status:

~~~sql
SELECT status, COUNT(*)
FROM orders
GROUP BY status
ORDER BY COUNT(*) DESC;
~~~

Nếu 95 phần trăm order là paid và 1 phần trăm là cancelled, planner cần biết sự khác biệt này.

Query hai giá trị có thể có plan khác:

~~~sql
SELECT *
FROM orders
WHERE status = 'paid';

SELECT *
FROM orders
WHERE status = 'cancelled';
~~~

Index status có thể không hữu ích cho status phổ biến nhưng hữu ích cho status hiếm. Partial index cũng có thể phù hợp với tập query ổn định.

---

## 6. Correlation

correlation mô tả mức tương quan giữa giá trị column và thứ tự vật lý của row trong table.

Xem correlation:

~~~sql
SELECT
    tablename,
    attname,
    correlation
FROM pg_stats
WHERE tablename = 'events';
~~~

Nếu created_at được insert gần theo thứ tự thời gian, correlation có thể cao. Khi đó range scan trên index có thể truy cập heap page hiệu quả hơn.

Nếu row được cập nhật hoặc insert ngẫu nhiên, correlation giảm. Index vẫn đúng nhưng chi phí heap access có thể cao hơn.

Correlation không phải correlation giữa hai column. Nó là thông tin về một column với thứ tự vật lý của table.

---

## 7. Statistics target

Statistics target điều khiển độ chi tiết statistics cho column:

~~~sql
ALTER TABLE events
ALTER COLUMN tenant_id SET STATISTICS 500;

ANALYZE events;
~~~

Target cao có thể giúp:

- column có nhiều most-common values;
- dữ liệu lệch;
- histogram cần nhiều bucket;
- query quan trọng có estimate sai.

Trade-off:

- ANALYZE tốn thêm tài nguyên;
- statistics lưu nhiều hơn;
- planning có thể tốn thêm thời gian;
- không sửa được query không sargable;
- không thay thế extended statistics cho correlated columns.

Không tăng target cho mọi column. Chọn column có bằng chứng từ pg_stats và EXPLAIN.

---

## 8. Dữ liệu lệch

Tạo dữ liệu lệch để quan sát:

~~~sql
CREATE TABLE skewed_events (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id integer NOT NULL,
    event_type text NOT NULL,
    created_at timestamptz NOT NULL
);

INSERT INTO skewed_events (tenant_id, event_type, created_at)
SELECT
    CASE
        WHEN g <= 900000 THEN 1
        ELSE 2 + (g % 999)
    END,
    CASE
        WHEN g % 2 = 0 THEN 'login'
        ELSE 'view'
    END,
    now() - (g % 365) * interval '1 day'
FROM generate_series(1, 1000000) AS g;

ANALYZE skewed_events;
~~~

So sánh:

~~~sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT *
FROM skewed_events
WHERE tenant_id = 1;
~~~

và:

~~~sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT *
FROM skewed_events
WHERE tenant_id = 999;
~~~

tenant 1 trả phần lớn table; tenant 999 trả rất ít. Không nên dùng kết quả của một parameter để kết luận plan cho mọi parameter.

---

## 9. Correlated columns

Planner có thể giả định hai điều kiện độc lập nếu không có thông tin bổ sung:

~~~sql
SELECT *
FROM user_profiles
WHERE country = 'VN'
  AND city = 'Hanoi';
~~~

Nếu mọi city Hanoi đều có country VN, hai điều kiện tương quan. Nhân selectivity độc lập có thể làm estimated rows sai.

Tạo extended statistics:

~~~sql
CREATE STATISTICS user_profiles_country_city_stats
    (dependencies, ndistinct, mcv)
ON country, city
FROM user_profiles;

ANALYZE user_profiles;
~~~

Các loại thường dùng:

- dependencies: dependency giữa các column;
- ndistinct: số tổ hợp distinct;
- mcv: most-common combinations.

Extended statistics không tạo index và không làm query nhanh trực tiếp. Nó giúp planner ước lượng cardinality tốt hơn.

---

## 10. So sánh trước và sau extended statistics

Trước khi tạo statistics:

~~~sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT *
FROM user_profiles
WHERE country = 'VN'
  AND city = 'Hanoi';
~~~

Ghi lại:

~~~text
estimated rows
actual rows
join hoặc scan node
execution time
~~~

Sau đó tạo statistics và ANALYZE:

~~~sql
CREATE STATISTICS user_profiles_country_city_stats
    (dependencies, mcv)
ON country, city
FROM user_profiles;

ANALYZE user_profiles;

EXPLAIN (ANALYZE, BUFFERS)
SELECT *
FROM user_profiles
WHERE country = 'VN'
  AND city = 'Hanoi';
~~~

So sánh estimated rows trước và sau. Actual rows có thể không đổi; mục tiêu là estimate gần actual hơn và plan downstream phù hợp hơn.

---

## 11. SARGability

Một predicate sargable là predicate cho phép planner tận dụng access path, thường là index, mà không phải biến đổi mọi row trước khi so sánh.

Không thuận lợi:

~~~sql
WHERE date_trunc('day', created_at) = DATE '2026-01-10'
~~~

Thuận lợi hơn:

~~~sql
WHERE created_at >= timestamptz '2026-01-10 00:00:00+00'
  AND created_at <  timestamptz '2026-01-11 00:00:00+00'
~~~

Không thuận lợi:

~~~sql
WHERE lower(email) = 'user@example.com'
~~~

Có thể tạo expression index nếu đây là query bắt buộc:

~~~sql
CREATE INDEX users_lower_email_idx
ON users (lower(email));
~~~

Hoặc lưu normalized_email bằng generated column và index column đó.

Không thuận lợi:

~~~sql
WHERE price + 100 > 500
~~~

Viết lại:

~~~sql
WHERE price > 400
~~~

Không phải phép biến đổi nào cũng tương đương khi NULL hoặc kiểu dữ liệu khác nhau. Kiểm tra logic trước khi rewrite.

---

## 12. Function, cast và parameter

Các vấn đề thường gặp:

- bọc column bằng function;
- cast column thay vì cast parameter;
- so sánh hai kiểu không nhất quán;
- dùng biểu thức không thể suy luận thành range;
- truyền parameter dưới dạng text rồi cast tại column.

Nên để column và parameter có kiểu phù hợp:

~~~sql
WHERE created_at >= $1::timestamptz
  AND tenant_id = $2::bigint
~~~

Trong ứng dụng, dùng parameterized query. Không nối chuỗi giá trị người dùng vào SQL để “giúp planner”.

Nếu expression là business requirement ổn định, cân nhắc expression index hoặc generated column. Nếu chỉ là query viết tiện tay, rewrite predicate thường đơn giản hơn.

---

## 13. Generic plan và custom plan

Prepared statement có thể dùng:

- custom plan: planner biết giá trị parameter cụ thể;
- generic plan: một plan dùng cho nhiều giá trị.

Generic plan giảm chi phí planning lặp nhưng có thể kém khi parameter có selectivity rất khác.

Ví dụ:

~~~text
tenant 1  → 90 phần trăm table
tenant 999 → vài row
~~~

Một plan dùng cho cả hai có thể không tối ưu.

Khi điều tra:

1. chạy query bằng literal hiếm;
2. chạy query bằng literal phổ biến;
3. chạy qua prepared statement thật;
4. so sánh plan;
5. kiểm tra application driver và prepare behavior.

Không đánh giá prepared query chỉ bằng query copy vào psql với literal.

---

## 14. Parameterized query

Parameterized query bảo vệ SQL injection và giúp ứng dụng truyền kiểu rõ ràng:

~~~sql
PREPARE recent_events(bigint, timestamptz) AS
SELECT id, event_type, created_at
FROM events
WHERE tenant_id = $1
  AND created_at >= $2
ORDER BY created_at DESC
LIMIT 100;
~~~

Thực thi:

~~~sql
EXECUTE recent_events(
    10,
    now() - interval '30 days'
);
~~~

Dùng EXPLAIN cho prepared statement:

~~~sql
EXPLAIN (ANALYZE, BUFFERS)
EXECUTE recent_events(
    10,
    now() - interval '30 days'
);
~~~

Trong production, driver có thể dùng protocol hoặc prepared behavior khác psql. Cần đo trong cách chạy thật.

Parameterized query không có nghĩa planner luôn dùng cùng một plan. Generic/custom plan còn phụ thuộc setting và driver behavior.

---

## 15. Generic plan lab

Có thể điều khiển plan cache mode trong lab:

~~~sql
SET LOCAL plan_cache_mode = force_custom_plan;

EXPLAIN (ANALYZE, BUFFERS)
EXECUTE recent_events(
    999,
    now() - interval '30 days'
);
~~~

So sánh với:

~~~sql
SET LOCAL plan_cache_mode = force_generic_plan;

EXPLAIN (ANALYZE, BUFFERS)
EXECUTE recent_events(
    999,
    now() - interval '30 days'
);
~~~

Các setting này phục vụ chẩn đoán và benchmark. Không thay đổi global setting production chỉ để chữa một query mà chưa hiểu workload.

---

## 16. Statistics và partition dữ liệu

Statistics có thể khác giữa table partition hoặc cần analyze đúng relation. Query partitioned table còn phụ thuộc partition pruning, nhưng trong tuần này tập trung vào estimate.

Khi bảng lớn:

- theo dõi analyze từng bảng;
- kiểm tra dữ liệu mới có được autoanalyze không;
- xem estimate theo partition;
- không benchmark khi một partition chưa có statistics.

Nếu query lọc theo partition key nhưng estimate vẫn sai, kiểm tra cả pruning và statistics của partition.

---

## 17. Quy trình tối ưu có hệ thống

### Bước 1: xác định query thật

Lấy query từ log, metric hoặc pg_stat_statements. Không tối ưu query giả định.

### Bước 2: cố định workload

Ghi:

- database version;
- schema và index;
- kích thước table;
- parameter;
- cache condition;
- concurrency;
- thời điểm đo.

### Bước 3: chụp baseline

~~~sql
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
SELECT ...;
~~~

Lưu plan đầy đủ, không chỉ chụp một dòng execution time.

### Bước 4: kiểm tra cardinality

So estimated rows và actual rows ở từng node. Tìm sai số lớn nhất.

### Bước 5: kiểm tra statistics

Xem last_analyze, pg_stats, most-common values, histogram, correlation và null fraction.

### Bước 6: thay đổi một yếu tố

Một lần chỉ thay một trong các yếu tố:

- ANALYZE;
- statistics target;
- extended statistics;
- rewrite predicate;
- expression index;
- composite index;
- query join order hoặc projection.

### Bước 7: đo lại

Dùng cùng parameter và cùng điều kiện. Sau đó test thêm parameter phổ biến và hiếm.

### Bước 8: kiểm tra tác dụng phụ

- write cost;
- planning time;
- index size;
- plan stability;
- lock và maintenance;
- behavior khi dữ liệu tăng.

### Bước 9: ghi quyết định

Tài liệu phải trả lời vì sao thay đổi được chấp nhận và khi nào cần review lại.

---

## 18. Tình huống: estimate sai làm Nested Loop chậm

Giả sử planner nghĩ outer table có 10 row:

~~~text
Nested Loop
  estimated outer rows: 10
  actual outer rows:    500000
~~~

Planner có thể chọn Nested Loop vì nghĩ inner lookup chỉ chạy 10 lần. Thực tế inner scan chạy 500.000 lần.

Điều tra:

1. node outer có actual rows cao hơn estimate bao nhiêu;
2. statistics của predicate outer;
3. có điều kiện nhiều column tương quan không;
4. có parameter skew không;
5. query có thể dùng Hash Join không;
6. ANALYZE có cập nhật chưa.

Không tắt Nested Loop vĩnh viễn trước khi sửa nguyên nhân cardinality.

---

## 19. Tình huống: index không được dùng sau ANALYZE

Index không được dùng vẫn có thể là đúng:

- query trả phần lớn table;
- index selectivity thấp;
- heap access ngẫu nhiên đắt;
- Seq Scan rẻ hơn;
- query có predicate không sargable;
- table nhỏ;
- planner tính cost khác kỳ vọng.

Kiểm tra:

~~~sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT *
FROM orders
WHERE status = 'paid';
~~~

Nếu status paid chiếm 95 phần trăm, Seq Scan có thể hợp lý. Nếu cancelled chỉ chiếm 1 phần trăm, partial index hoặc index phù hợp có thể giúp query cancelled.

---

## 20. Lab report mẫu

~~~text
# Statistics and query optimization report

## Query
Nêu query và parameter thực tế.

## Baseline
Plan, execution time, buffers, estimated rows, actual rows.

## Cardinality diagnosis
Node nào estimate sai? Sai bao nhiêu lần?

## Statistics inspection
pg_stats, last_analyze, distribution, null fraction, correlation.

## Change
ANALYZE, target, extended statistics hoặc rewrite nào được thử?

## Result
Plan sau, execution time, buffers và estimate mới.

## Side effects
Write cost, planning time, index size, parameter khác.

## Recommendation
Điều kiện áp dụng và thời điểm cần review lại.
~~~

---

## 21. Bài tập thực hành

### Bài 1 — ANALYZE sau bulk load

Tạo table một triệu row, chạy query trước và sau ANALYZE, so sánh estimate.

### Bài 2 — Most-common values

Tạo column status lệch 95/5, xem pg_stats và so sánh plan cho giá trị phổ biến và hiếm.

### Bài 3 — Histogram

Tạo date range có phân bố không đều, xem histogram và kiểm tra estimate cho hai khoảng khác nhau.

### Bài 4 — Correlated columns

Tạo country và city có quan hệ thực tế. So sánh estimate trước và sau extended statistics.

### Bài 5 — SARGability

So sánh date_trunc với predicate range bằng EXPLAIN ANALYZE.

### Bài 6 — Expression index

Tạo expression index trên lower(email), đo query trước và sau index.

### Bài 7 — Generic/custom plan

Dùng PREPARE và plan_cache_mode để so sánh plan cho parameter hiếm và phổ biến.

### Bài 8 — Cardinality và join

Tạo một query mà outer estimate sai lớn, xác định vì sao Nested Loop trở nên đắt.

### Bài 9 — Statistics target

Tăng target cho column lệch, chạy ANALYZE và ghi thay đổi trong pg_stats.

### Bài 10 — Báo cáo

Viết report theo mẫu gồm baseline, diagnosis, change, result, side effects và recommendation.

---

## 22. Tiêu chí hoàn thành Tuần 2

Bạn hoàn thành bài học khi có thể:

- Giải thích statistics dùng để ước lượng cardinality.
- Chạy ANALYZE đúng thời điểm.
- Đọc null fraction, n_distinct, MCV, histogram và correlation trong pg_stats.
- Nhận biết dữ liệu lệch ảnh hưởng tới selectivity.
- Giải thích column correlation và extended statistics.
- Chọn statistics target dựa trên bằng chứng.
- Nhận biết predicate không sargable.
- Rewrite function hoặc cast để planner có thể tận dụng access path.
- Phân biệt generic plan và custom plan.
- Đo prepared statement trong cách chạy thật.
- So sánh estimated rows với actual rows ở nhiều node.
- Tối ưu theo một quy trình có baseline và kiểm tra tác dụng phụ.
- Viết báo cáo query optimization có plan trước/sau.

## Tóm tắt cần nhớ

~~~text
Statistics giúp planner ước lượng cardinality mà không đọc toàn bộ table.
ANALYZE cập nhật statistics sau bulk load hoặc thay đổi phân phối lớn.
pg_stats chứa MCV, histogram, null fraction, distinct và correlation.
Dữ liệu lệch có thể khiến một plan không tốt cho mọi parameter.
Extended statistics giúp planner hiểu quan hệ giữa nhiều column.
Statistics target cao hơn không phải thuốc chữa mọi vấn đề.
Predicate sargable giúp planner dùng index hoặc access path tốt hơn.
Prepared statement có thể dùng generic hoặc custom plan.
Tối ưu có hệ thống cần baseline, thay đổi từng yếu tố và đo lại.
Kết luận phải bao gồm tác dụng phụ và parameter khác.
~~~

# Tuần 12: Index, EXPLAIN và project cuối khóa

Mục tiêu:

- hiểu index giúp database tìm row như thế nào;
- chọn B-tree, composite index, partial index, GIN và BRIN ở mức cơ bản;
- đọc EXPLAIN và EXPLAIN ANALYZE;
- nhận biết Seq Scan, Index Scan, Bitmap Heap Scan;
- dùng ANALYZE để cập nhật statistics;
- hoàn thành project Bookstore Database có migration, seed, query, index, transaction test và backup/restore.

Nguyên tắc:

~~~text
Index phải phục vụ query thật.
Đo query trước và sau khi tạo index.
Index tăng tốc đọc nhưng làm tăng chi phí ghi và dung lượng.
EXPLAIN cho biết planner dự định làm gì.
EXPLAIN ANALYZE chạy query thật và đo số liệu thật.
~~~

---

## 1. Index là gì?

Index là cấu trúc dữ liệu phụ giúp database tìm row mà không phải đọc toàn bộ table.

Không có index:

~~~sql
SELECT id, customer_id, ordered_at
FROM orders
WHERE customer_id = 10;
~~~

Database có thể phải kiểm tra từng row. Tạo index:

~~~sql
CREATE INDEX orders_customer_id_idx
ON orders (customer_id);
~~~

Planner có thể dùng index để tìm row phù hợp, nhưng không bắt buộc dùng. Planner dựa trên statistics, số row cần trả về, chi phí đọc table và nhiều yếu tố khác.

Index có ích khi:

- query có pattern phù hợp;
- điều kiện đủ selective;
- statistics đúng;
- query không biến đổi column theo cách làm index khó sử dụng;
- chi phí dùng index thấp hơn đọc tuần tự.

---

## 2. B-tree index

B-tree là index mặc định, phù hợp với:

- so sánh bằng;
- range query;
- ORDER BY;
- một số tìm kiếm prefix.

~~~sql
CREATE INDEX books_price_idx
ON books (price);
~~~

Query range:

~~~sql
SELECT id, title, price
FROM books
WHERE price >= 100000
  AND price < 300000;
~~~

Query sort:

~~~sql
SELECT id, title, price
FROM books
ORDER BY price DESC
LIMIT 20;
~~~

B-tree thường không phù hợp với substring:

~~~sql
WHERE title ILIKE '%postgres%'
~~~

Tìm kiếm dạng này có thể cần pg_trgm hoặc full-text search.

Primary key và UNIQUE thường đã tạo index tương ứng. Không tạo thêm index trùng:

~~~sql
CREATE TABLE customers (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email text NOT NULL UNIQUE
);
~~~

---

## 3. Index trên foreign key

Foreign key bảo vệ tính toàn vẹn nhưng query theo foreign key thường cần index riêng:

~~~sql
CREATE INDEX books_author_id_idx
ON books (author_id);

CREATE INDEX orders_customer_id_idx
ON orders (customer_id);

CREATE INDEX order_items_book_id_idx
ON order_items (book_id);
~~~

Trong order_items, primary key (order_id, book_id) đã tạo index bắt đầu bằng order_id. Nó phù hợp với:

~~~sql
SELECT *
FROM order_items
WHERE order_id = 100;
~~~

Nhưng query chỉ theo book_id thường cần index riêng.

---

## 4. Composite index

Composite index chứa nhiều column:

~~~sql
CREATE INDEX orders_customer_created_idx
ON orders (customer_id, created_at DESC);
~~~

Phù hợp với:

~~~sql
SELECT id, status, created_at
FROM orders
WHERE customer_id = 10
ORDER BY created_at DESC
LIMIT 20;
~~~

Thứ tự column quan trọng. Với index (customer_id, created_at):

- query theo customer_id phù hợp;
- query theo customer_id và range created_at phù hợp;
- query chỉ theo created_at không tận dụng đầy đủ index này.

Một hướng dẫn thường hữu ích là equality trước range hoặc sort:

~~~sql
CREATE INDEX orders_customer_status_created_idx
ON orders (customer_id, status, created_at DESC);
~~~

Phục vụ:

~~~sql
WHERE customer_id = 10
  AND status = 'paid'
ORDER BY created_at DESC
~~~

Đây là hướng dẫn, không phải luật tuyệt đối. Luôn đo trên workload thật.

---

## 5. Partial index

Partial index chỉ index các row thỏa predicate:

~~~sql
CREATE INDEX orders_paid_customer_created_idx
ON orders (customer_id, created_at DESC)
WHERE status = 'paid';
~~~

Phù hợp với query có cùng điều kiện:

~~~sql
SELECT id, created_at
FROM orders
WHERE customer_id = 10
  AND status = 'paid'
ORDER BY created_at DESC
LIMIT 20;
~~~

Lợi ích:

- index nhỏ hơn;
- ít chi phí ghi cho row ngoài predicate;
- phù hợp với trạng thái hoặc tập dữ liệu cố định.

Partial unique index:

~~~sql
CREATE UNIQUE INDEX one_active_email
ON customer_emails (lower(email))
WHERE deleted_at IS NULL;
~~~

Chỉ các row chưa soft delete phải có email duy nhất.

---

## 6. GIN và BRIN ở mức nhận biết

GIN phù hợp với jsonb, array và một số dạng text search:

~~~sql
CREATE INDEX books_metadata_gin_idx
ON books USING GIN (metadata);
~~~

Query jsonb:

~~~sql
SELECT id, title
FROM books
WHERE metadata @> '{"edition": 2}';
~~~

GIN có thể lớn và tốn chi phí update. Không tạo GIN chỉ vì column có type jsonb.

BRIN phù hợp với table rất lớn có correlation tự nhiên, ví dụ event được insert theo thời gian:

~~~sql
CREATE INDEX audit_events_created_brin_idx
ON audit_events USING BRIN (created_at);
~~~

BRIN nhỏ hơn B-tree nhưng mang tính tóm tắt. Dùng khi:

- table rất lớn;
- column có thứ tự vật lý gần với thứ tự thời gian;
- query thường lọc range;
- đã đo trên dữ liệu thật.

---

## 7. Index và chi phí ghi

Mỗi index phải được cập nhật khi INSERT, DELETE hoặc UPDATE column liên quan.

~~~text
Nhiều index → đọc có thể nhanh hơn.
Nhiều index → ghi chậm hơn và tốn disk hơn.
~~~

Không tạo index cho mọi column. Review index bằng statistics:

~~~sql
SELECT
    schemaname,
    relname,
    indexrelname,
    idx_scan,
    pg_size_pretty(pg_relation_size(indexrelid)) AS index_size
FROM pg_stat_user_indexes
ORDER BY idx_scan ASC;
~~~

Index có idx_scan bằng 0 chưa chắc vô dụng vì khoảng quan sát có thể ngắn. Đây là tín hiệu để review, không phải lệnh xóa ngay.

---

## 8. EXPLAIN

EXPLAIN hiển thị kế hoạch planner dự định dùng:

~~~sql
EXPLAIN
SELECT id, customer_id, created_at
FROM orders
WHERE customer_id = 10
ORDER BY created_at DESC
LIMIT 20;
~~~

Kế hoạch thường có:

- node scan hoặc join;
- estimated cost;
- estimated rows;
- estimated width;
- Index Cond hoặc Filter.

Đọc từ dưới lên để hiểu dữ liệu được tạo ra, rồi đọc lên để hiểu các bước xử lý tiếp theo. Cost và rows là estimate, không phải thời gian thực tế.

EXPLAIN không chạy query thông thường, nên an toàn hơn EXPLAIN ANALYZE với statement có side effect.

---

## 9. EXPLAIN ANALYZE

EXPLAIN ANALYZE chạy query thật:

~~~sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT id, customer_id, created_at
FROM orders
WHERE customer_id = 10
ORDER BY created_at DESC
LIMIT 20;
~~~

Thông tin cần nhìn:

| Thông tin | Ý nghĩa |
|---|---|
| estimated rows | Số row planner dự đoán |
| actual rows | Số row thực tế |
| actual time | Thời gian node thực tế |
| loops | Số lần node chạy |
| Buffers | Block đọc từ cache hoặc disk |

EXPLAIN ANALYZE chạy INSERT, UPDATE và DELETE thật. Không chạy destructive statement trên dữ liệu production chỉ để xem kế hoạch.

Trong môi trường test có thể dùng transaction rollback:

~~~sql
BEGIN;

EXPLAIN (ANALYZE, BUFFERS)
DELETE FROM books
WHERE id = 10;

ROLLBACK;
~~~

---

## 10. Các node scan chính

### Seq Scan

Đọc tuần tự table:

~~~text
Seq Scan on books
  Filter: price > 100000
~~~

Seq Scan không luôn xấu. Planner có thể chọn nó khi table nhỏ hoặc query trả về phần lớn table.

### Index Scan

Dùng index để tìm row:

~~~text
Index Scan using orders_customer_id_idx on orders
  Index Cond: customer_id = 10
~~~

Phù hợp khi số row cần lấy nhỏ so với table.

### Index Only Scan

Có thể trả dữ liệu chủ yếu từ index nếu index chứa đủ column và visibility map phù hợp. Không phải lúc nào cũng hoàn toàn tránh đọc heap.

### Bitmap Heap Scan

Thường gồm:

1. Bitmap Index Scan tìm vị trí row.
2. Bitmap Heap Scan đọc các block table.

Nó phù hợp khi query trả nhiều row hơn Index Scan thông thường nhưng chưa đến mức đọc toàn bộ table.

Không đánh giá node chỉ dựa trên tên. So sánh actual time, rows và buffers.

---

## 11. Statistics và ANALYZE

Nếu estimated rows khác xa actual rows, planner có thể chọn kế hoạch kém.

~~~text
estimated rows: 10
actual rows:    500000
~~~

Nguyên nhân có thể là statistics cũ, dữ liệu phân bố lệch, query phức tạp hoặc vừa seed dữ liệu lớn.

Chạy:

~~~sql
ANALYZE orders;
ANALYZE books;
ANALYZE order_items;
~~~

Hoặc:

~~~sql
ANALYZE;
~~~

ANALYZE cập nhật statistics, không tạo index và không sửa dữ liệu.

Xem statistics:

~~~sql
SELECT
    tablename,
    attname,
    n_distinct,
    most_common_vals,
    most_common_freqs
FROM pg_stats
WHERE tablename = 'orders';
~~~

Tăng statistics target cho column quan trọng nếu cần:

~~~sql
ALTER TABLE orders
ALTER COLUMN customer_id SET STATISTICS 500;

ANALYZE orders;
~~~

Không tăng mọi column vì statistics cao hơn cũng làm ANALYZE và planning tốn thêm tài nguyên.

---

## 12. Khi query không dùng index

### Biến đổi column

Khó tận dụng index trên created_at:

~~~sql
WHERE date_trunc('day', created_at) = DATE '2026-01-10'
~~~

Viết range:

~~~sql
WHERE created_at >= timestamptz '2026-01-10 00:00:00+00'
  AND created_at <  timestamptz '2026-01-11 00:00:00+00'
~~~

Hoặc tạo expression index nếu pattern là yêu cầu thực tế.

### Wildcard đầu chuỗi

B-tree thường không hiệu quả với:

~~~sql
WHERE title LIKE '%postgres%'
~~~

Cân nhắc pg_trgm hoặc full-text search.

### Query trả phần lớn table

Nếu hầu hết row phù hợp, Seq Scan có thể rẻ hơn Index Scan.

### Kiểu dữ liệu không thống nhất

Parameter và column nên dùng kiểu nhất quán. Dùng parameterized query thay vì nối chuỗi SQL.

---

## 13. Quy trình tối ưu query

1. Tìm query chậm bằng log hoặc metric.
2. Chạy EXPLAIN ANALYZE, BUFFERS trên dữ liệu đại diện.
3. Ghi actual time, rows, loops và buffers.
4. Kiểm tra filter, join, sort và aggregate.
5. Đề xuất index theo pattern query.
6. Tạo index ở môi trường test.
7. Chạy ANALYZE nếu cần.
8. Chạy lại cùng query và so sánh.
9. Kiểm tra tác động lên INSERT, UPDATE và DELETE.
10. Ghi lý do index trong migration hoặc README.

Không tối ưu theo một kế hoạch duy nhất nếu dữ liệu test không giống production.

---

## 14. Project cuối khóa: Bookstore Database

Project tổng hợp toàn bộ roadmap là database cho cửa hàng sách.

Phạm vi:

~~~text
authors
books
customers
addresses
orders
order_items
payments
inventory
~~~

Mục tiêu:

- dựng database từ đầu bằng script;
- có schema chuẩn hóa;
- có seed data đủ lớn;
- có query nghiệp vụ;
- có transaction đặt hàng;
- có index giải thích được;
- có README;
- có backup và restore thử nghiệm.

---

## 15. Cấu trúc project

~~~text
bookstore/
├── README.md
├── migrations/
│   ├── 001_schema.sql
│   ├── 002_constraints.sql
│   ├── 003_indexes.sql
│   └── 004_views.sql
├── seed/
│   ├── 001_reference_data.sql
│   └── 002_generate_data.sql
├── queries/
│   ├── 001_catalog.sql
│   ├── 002_orders.sql
│   ├── 003_reports.sql
│   └── 004_explain.sql
├── transactions/
│   ├── create_order.sql
│   └── concurrency_notes.md
├── backup/
│   └── restore-test.md
└── tests/
    └── acceptance-checklist.md
~~~

Các file cốt lõi theo roadmap:

~~~text
001_schema.sql
002_constraints.sql
003_seed.sql
004_queries.sql
005_indexes.sql
README.md
~~~

---

## 16. Schema project

~~~sql
CREATE TABLE authors (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name text NOT NULL
);

CREATE TABLE books (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    author_id bigint REFERENCES authors(id),
    title text NOT NULL,
    isbn text UNIQUE,
    category text,
    price numeric(12, 2) NOT NULL CHECK (price >= 0),
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE customers (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email text NOT NULL UNIQUE,
    full_name text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE addresses (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id bigint NOT NULL REFERENCES customers(id),
    line1 text NOT NULL,
    city text NOT NULL,
    postal_code text,
    is_default boolean NOT NULL DEFAULT false
);

CREATE TABLE orders (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id bigint NOT NULL REFERENCES customers(id),
    status text NOT NULL CHECK (
        status IN ('pending', 'paid', 'cancelled', 'fulfilled')
    ),
    ordered_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE order_items (
    order_id bigint NOT NULL REFERENCES orders(id),
    book_id bigint NOT NULL REFERENCES books(id),
    quantity integer NOT NULL CHECK (quantity > 0),
    unit_price numeric(12, 2) NOT NULL CHECK (unit_price >= 0),
    PRIMARY KEY (order_id, book_id)
);

CREATE TABLE payments (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id bigint NOT NULL REFERENCES orders(id),
    amount numeric(12, 2) NOT NULL CHECK (amount >= 0),
    status text NOT NULL CHECK (
        status IN ('created', 'succeeded', 'failed', 'refunded')
    ),
    provider_transaction_id text,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE inventory (
    book_id bigint PRIMARY KEY REFERENCES books(id),
    quantity integer NOT NULL CHECK (quantity >= 0),
    updated_at timestamptz NOT NULL DEFAULT now()
);
~~~

---

## 17. Seed data

Mục tiêu tối thiểu:

~~~text
1.000 books
10.000 orders
50.000 order_items
~~~

Seed reference data trước:

~~~sql
INSERT INTO authors (name)
VALUES
    ('George Orwell'),
    ('Nguyen Nhat Anh'),
    ('J. R. R. Tolkien');

INSERT INTO customers (email, full_name)
VALUES
    ('an@example.com', 'Nguyen Van An'),
    ('binh@example.com', 'Tran Thi Binh');
~~~

Có thể dùng generate_series:

~~~sql
INSERT INTO books (author_id, title, isbn, category, price)
SELECT
    1 + (g % 100),
    'Book ' || g,
    'ISBN-' || lpad(g::text, 10, '0'),
    CASE
        WHEN g % 3 = 0 THEN 'fiction'
        WHEN g % 3 = 1 THEN 'technology'
        ELSE 'history'
    END,
    round((50000 + (g % 500) * 1000)::numeric, 2)
FROM generate_series(1, 1000) AS g;
~~~

Seed cần tái lập hoặc có tài liệu rõ. Sau seed lớn:

~~~sql
ANALYZE;
~~~

Không đánh giá index trên table chưa có dữ liệu đại diện.

---

## 18. Query bắt buộc

### Top 10 sách bán chạy

~~~sql
SELECT
    b.id,
    b.title,
    SUM(oi.quantity) AS copies_sold
FROM books AS b
JOIN order_items AS oi ON oi.book_id = b.id
JOIN orders AS o ON o.id = oi.order_id
WHERE o.status = 'paid'
GROUP BY b.id, b.title
ORDER BY copies_sold DESC
LIMIT 10;
~~~

### Doanh thu theo tháng

~~~sql
SELECT
    date_trunc('month', o.ordered_at)::date AS month,
    COUNT(DISTINCT o.id) AS order_count,
    SUM(oi.quantity * oi.unit_price) AS revenue
FROM orders AS o
JOIN order_items AS oi ON oi.order_id = o.id
WHERE o.status = 'paid'
GROUP BY date_trunc('month', o.ordered_at)::date
ORDER BY month;
~~~

### Khách hàng chưa từng mua

~~~sql
SELECT c.id, c.full_name, c.email
FROM customers AS c
WHERE NOT EXISTS (
    SELECT 1
    FROM orders AS o
    WHERE o.customer_id = c.id
);
~~~

### Order tổng tiền cao nhất

~~~sql
SELECT
    o.id AS order_id,
    SUM(oi.quantity * oi.unit_price) AS order_total
FROM orders AS o
JOIN order_items AS oi ON oi.order_id = o.id
GROUP BY o.id
ORDER BY order_total DESC
LIMIT 1;
~~~

### Sách chưa từng bán

~~~sql
SELECT b.id, b.title
FROM books AS b
WHERE NOT EXISTS (
    SELECT 1
    FROM order_items AS oi
    WHERE oi.book_id = b.id
);
~~~

### Tác giả có doanh thu cao nhất

~~~sql
SELECT
    a.id,
    a.name,
    SUM(oi.quantity * oi.unit_price) AS revenue
FROM authors AS a
JOIN books AS b ON b.author_id = a.id
JOIN order_items AS oi ON oi.book_id = b.id
JOIN orders AS o ON o.id = oi.order_id
WHERE o.status = 'paid'
GROUP BY a.id, a.name
ORDER BY revenue DESC
LIMIT 1;
~~~

### Tồn kho dưới ngưỡng

~~~sql
SELECT i.book_id, b.title, i.quantity
FROM inventory AS i
JOIN books AS b ON b.id = i.book_id
WHERE i.quantity < 10
ORDER BY i.quantity, i.book_id;
~~~

### Doanh thu lũy kế

~~~sql
WITH monthly_revenue AS (
    SELECT
        date_trunc('month', o.ordered_at)::date AS month,
        SUM(oi.quantity * oi.unit_price) AS revenue
    FROM orders AS o
    JOIN order_items AS oi ON oi.order_id = o.id
    WHERE o.status = 'paid'
    GROUP BY date_trunc('month', o.ordered_at)::date
)
SELECT
    month,
    revenue,
    SUM(revenue) OVER (
        ORDER BY month
        ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
    ) AS cumulative_revenue
FROM monthly_revenue
ORDER BY month;
~~~

### Top 3 sách theo thể loại

~~~sql
WITH ranked_books AS (
    SELECT
        b.id,
        b.title,
        b.category,
        SUM(oi.quantity) AS copies_sold,
        ROW_NUMBER() OVER (
            PARTITION BY b.category
            ORDER BY SUM(oi.quantity) DESC, b.id
        ) AS category_position
    FROM books AS b
    JOIN order_items AS oi ON oi.book_id = b.id
    JOIN orders AS o ON o.id = oi.order_id
    WHERE o.status = 'paid'
    GROUP BY b.id, b.title, b.category
)
SELECT *
FROM ranked_books
WHERE category_position <= 3
ORDER BY category, category_position;
~~~

### Tìm kiếm sách không phân biệt hoa thường

~~~sql
SELECT id, title, price
FROM books
WHERE title ILIKE '%postgres%'
ORDER BY title;
~~~

Nếu search này quan trọng ở production, đo và cân nhắc pg_trgm hoặc full-text search.

---

## 19. Index plan cho project

Mỗi index phải có query phục vụ:

~~~sql
CREATE INDEX orders_customer_created_idx
ON orders (customer_id, ordered_at DESC);

CREATE INDEX orders_paid_customer_created_idx
ON orders (customer_id, ordered_at DESC)
WHERE status = 'paid';

CREATE INDEX order_items_book_idx
ON order_items (book_id);

CREATE INDEX books_author_idx
ON books (author_id);

CREATE INDEX inventory_quantity_idx
ON inventory (quantity);
~~~

Sau đó:

~~~sql
ANALYZE;
~~~

Chạy EXPLAIN (ANALYZE, BUFFERS) cho ít nhất 5 query và ghi kết quả quan trọng vào README hoặc file explain.

Không giữ index chỉ vì nó có vẻ hợp lý. Mỗi index cần được giải thích bằng query, constraint hoặc workload thực tế.

---

## 20. Transaction và concurrency test

### Hai người cùng mua cuốn sách cuối cùng

Kịch bản:

1. Inventory quantity bằng 1.
2. Hai session cùng BEGIN.
3. Cả hai cố trừ cùng book.
4. Chỉ một transaction cập nhật thành công.
5. Transaction còn lại nhận hết hàng hoặc retry.

~~~sql
BEGIN;

UPDATE inventory
SET quantity = quantity - 1,
    updated_at = now()
WHERE book_id = 10
  AND quantity >= 1
RETURNING book_id, quantity;

-- Không có row: ROLLBACK vì hết hàng.
-- Có row: tạo order item và tiếp tục.

COMMIT;
~~~

### Payment thất bại

Kiểm tra order pending, payment failed, và quy tắc hoàn inventory hoặc reservation.

### Retry request

Cùng idempotency key không được tạo hai order. Cùng key nhưng payload khác phải bị từ chối.

### Order nhiều item và một item hết hàng

Quyết định rõ rollback toàn bộ, reservation hoặc partial fulfillment. Ghi quyết định trong README.

---

## 21. Backup và restore

Backup custom format:

~~~bash
pg_dump \
  --format=custom \
  --file=bookstore.dump \
  bookstore
~~~

Restore vào database test:

~~~bash
createdb bookstore_restore_test

pg_restore \
  --clean \
  --if-exists \
  --dbname=bookstore_restore_test \
  bookstore.dump
~~~

Kiểm tra:

~~~sql
SELECT COUNT(*) FROM books;
SELECT COUNT(*) FROM orders;
SELECT COUNT(*) FROM order_items;
SELECT COUNT(*) FROM payments;
~~~

Backup chưa phải restore test. Project phải chứng minh database phục hồi được và query cơ bản vẫn chạy.

Không commit password, token hoặc dữ liệu nhạy cảm vào Git.

---

## 22. README project

README nên có:

- mục tiêu project;
- phiên bản PostgreSQL;
- yêu cầu cài đặt;
- cách tạo database;
- thứ tự chạy migration;
- cách seed data;
- cách chạy query;
- cách chạy transaction test;
- cách backup và restore;
- các index và query chúng phục vụ;
- quyết định normalization hoặc denormalization;
- giới hạn project;
- cách kiểm tra dữ liệu test.

Thứ tự chạy tối thiểu:

~~~text
1. Tạo database rỗng.
2. Chạy 001_schema.sql.
3. Chạy 002_constraints.sql.
4. Chạy 003_seed.sql.
5. Chạy 005_indexes.sql.
6. Chạy ANALYZE.
7. Chạy 004_queries.sql.
8. Chạy EXPLAIN cho query bắt buộc.
~~~

---

## 23. Definition of Done

### Schema và migration

- Có ERD.
- Có 001_schema.sql.
- Có 002_constraints.sql.
- Script chạy được trên database trống.
- Mọi bảng có primary key.
- Quan hệ quan trọng có foreign key.
- Dữ liệu sai bị chặn bằng NOT NULL, UNIQUE và CHECK.

### Seed

- Có ít nhất 1.000 books.
- Có ít nhất 10.000 orders.
- Có ít nhất 50.000 order_items.
- Seed có thể tái lập hoặc có tài liệu rõ.
- Chạy ANALYZE sau seed lớn.

### Query

- Có top 10 sách bán chạy.
- Có doanh thu theo tháng.
- Có customer chưa từng mua.
- Có order tổng tiền cao nhất.
- Có sách chưa từng bán.
- Có tác giả doanh thu cao nhất.
- Có tồn kho dưới ngưỡng.
- Có doanh thu lũy kế.
- Có top 3 sách mỗi category.
- Có tìm kiếm sách không phân biệt hoa thường.

### Index và hiệu năng

- Có 005_indexes.sql.
- Mỗi index có query phục vụ.
- Có EXPLAIN ANALYZE cho ít nhất 5 query.
- Có giải thích Seq Scan, Index Scan hoặc Bitmap Heap Scan.
- Không có index trùng không cần thiết.
- Đã kiểm tra tác động cơ bản tới write.

### Transaction

- Đã test hai người mua cuốn sách cuối.
- Đã test payment thất bại sau khi tạo order.
- Đã test retry cùng request.
- Đã test order nhiều item và một item hết hàng.
- Có chiến lược xử lý deadlock hoặc serialization retry.

### Vận hành

- Có backup.
- Có restore thử trên database khác.
- README có hướng dẫn từ database trống.
- Không commit secret hoặc dữ liệu nhạy cảm.
- Có ghi lại trade-off thiết kế.

---

## 24. Tiêu chí hoàn thành Tuần 12

Bạn hoàn thành bài học khi có thể:

- Giải thích mục đích và chi phí của index.
- Chọn B-tree cho equality, range và sort phù hợp.
- Thiết kế composite index theo query thật.
- Biết dùng partial index.
- Nhận biết GIN cho jsonb và BRIN cho table lớn có correlation.
- Nhớ rằng primary key và UNIQUE đã có index tương ứng.
- Cân nhắc index trên foreign key.
- Đọc EXPLAIN và EXPLAIN ANALYZE.
- Phân biệt Seq Scan, Index Scan và Bitmap Heap Scan.
- Nhận biết estimate sai và chạy ANALYZE.
- Không chạy EXPLAIN ANALYZE destructive trên dữ liệu thật.
- Xây project bookstore từ database trống.
- Viết seed data đủ lớn để đo query.
- Chạy các query bắt buộc.
- Test transaction và concurrency.
- Backup và restore thành công.
- Viết README và hoàn thành Definition of Done.

## Tóm tắt cần nhớ

~~~text
Index phục vụ query thật, không phải trang trí schema.
B-tree là lựa chọn mặc định cho equality, range và sort.
Composite index phụ thuộc thứ tự column và leftmost prefix.
Partial index phù hợp với tập row có predicate ổn định.
GIN phù hợp với jsonb hoặc dữ liệu nhiều phần tử.
BRIN phù hợp với table rất lớn có thứ tự vật lý tự nhiên.
Index tăng tốc đọc nhưng làm INSERT, UPDATE, DELETE tốn hơn.
EXPLAIN là estimate; EXPLAIN ANALYZE chạy query thật.
Seq Scan không luôn xấu; planner chọn theo chi phí và selectivity.
ANALYZE cập nhật statistics để planner quyết định tốt hơn.
Project cuối phải dựng được từ database trống và restore được từ backup.
Definition of Done cần bằng chứng bằng query, test và tài liệu.
~~~

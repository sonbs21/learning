# Roadmap học SQL và PostgreSQL cơ bản trong 12 tuần

Lộ trình này dành cho người bắt đầu từ SQL và muốn dùng PostgreSQL trong backend hoặc phân tích dữ liệu. Mục tiêu không phải học mọi tính năng của PostgreSQL, mà là hiểu chắc cách thiết kế dữ liệu, viết query đúng, kiểm tra hiệu năng cơ bản và vận hành một database nhỏ.

Thời lượng đề xuất: **8–12 giờ/tuần** trong 12 tuần.

Tài liệu chính thức hiện hiển thị PostgreSQL 18 là phiên bản current; nên dùng bản stable 18.x trong quá trình học. Không cài PostgreSQL 19 beta cho project học tập hoặc production. [PostgreSQL documentation](https://www.postgresql.org/docs/18/)

## Mục tiêu cuối lộ trình

Sau 12 tuần, bạn có thể:

- Hiểu database quan hệ và mô hình table–row–column.
- Viết SELECT, INSERT, UPDATE, DELETE đúng và an toàn.
- Dùng WHERE, JOIN, GROUP BY, HAVING, ORDER BY và LIMIT.
- Xử lý NULL, ngày giờ, chuỗi, số và kiểu dữ liệu PostgreSQL.
- Thiết kế schema có primary key, foreign key, unique và check constraint.
- Chuẩn hóa dữ liệu ở mức cơ bản.
- Dùng transaction và hiểu mức cô lập ở mức thực hành.
- Viết subquery, CTE và window function cơ bản.
- Tạo index phù hợp và đọc EXPLAIN cơ bản.
- Dùng psql, pgAdmin hoặc DBeaver.
- Backup/restore database nhỏ.
- Hoàn thành một project database có migration, seed data và README.

## Công cụ cài đặt

| Công cụ | Mục đích |
|---|---|
| PostgreSQL 18.x | Database server |
| psql | PostgreSQL command-line client |
| pgAdmin hoặc DBeaver | Công cụ xem schema và chạy query |
| Docker Desktop | Cách cài PostgreSQL tái lập, tùy chọn |
| Git | Lưu SQL script và lịch sử thay đổi |

Nếu dùng Docker:

~~~bash
docker run --name pg-learning \
  -e POSTGRES_PASSWORD=postgres \
  -e POSTGRES_DB=shop \
  -p 5432:5432 \
  -d postgres:18
~~~

Trong môi trường thật, không dùng password đơn giản như ví dụ trên và không commit secret vào Git.

---

# Giai đoạn 1 — Nền tảng SQL và PostgreSQL

## Tuần 1: Database quan hệ và môi trường PostgreSQL

### Học

- Database là gì, DBMS là gì.
- Database quan hệ và bảng.
- Row, column, schema, database.
- Primary key và identity.
- Foreign key.
- NULL khác với giá trị rỗng hoặc số 0.
- SQL là ngôn ngữ khai báo.
- PostgreSQL server, database, role và schema.
- Kết nối bằng psql.

### Thực hành

Tạo database và schema học tập:

~~~sql
CREATE DATABASE shop;

CREATE SCHEMA learning;

CREATE TABLE learning.notes (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
~~~

Thực hành các lệnh psql:

~~~text
\l              liệt kê database
\c shop         kết nối database
\dn             liệt kê schema
\dt             liệt kê table
\d learning.notes
\q              thoát
~~~

### Tiêu chí hoàn thành

- Tự tạo database và kết nối bằng psql.
- Phân biệt database, schema và table.
- Giải thích được vì sao NULL không bằng chuỗi rỗng.

## Tuần 2: DDL — tạo và thay đổi cấu trúc

### Học

- CREATE DATABASE, SCHEMA, TABLE.
- ALTER TABLE.
- DROP TABLE và TRUNCATE.
- Data type cơ bản.
- Identity column.
- Primary key.
- Foreign key.
- UNIQUE.
- NOT NULL.
- CHECK.
- DEFAULT.
- Tên bảng, tên cột và quy ước đặt tên.

### Bài tập

Thiết kế schema cửa hàng sách:

~~~text
authors
books
customers
orders
order_items
~~~

Yêu cầu:

- Mỗi table có khóa chính.
- books tham chiếu authors.
- order_items tham chiếu orders và books.
- Giá sách không được âm.
- Email khách hàng là duy nhất.
- Số lượng trong order_items phải lớn hơn 0.

### Cần tránh

- Xóa table khi chưa kiểm tra foreign key.
- Dùng text cho mọi kiểu dữ liệu.
- Lưu nhiều giá trị phân cách bằng dấu phẩy trong một column.
- Dùng tên cột mơ hồ như data, value, type nếu không có ngữ cảnh.

## Tuần 3: DML — thêm, sửa, xóa dữ liệu

### Học

- INSERT một row và nhiều row.
- UPDATE có điều kiện.
- DELETE có điều kiện.
- RETURNING.
- UPSERT với ON CONFLICT.
- COPY và \copy ở mức cơ bản.
- Vì sao phải thử SELECT trước khi UPDATE hoặc DELETE.

### Thực hành

~~~sql
INSERT INTO books (title, price)
VALUES ('SQL Basics', 250000)
RETURNING id, title, price;

UPDATE books
SET price = price * 1.1
WHERE id = 1
RETURNING id, price;

INSERT INTO customers (email, full_name)
VALUES ('user@example.com', 'Nguyen Van A')
ON CONFLICT (email)
DO UPDATE SET full_name = EXCLUDED.full_name
RETURNING *;
~~~

### Quy tắc an toàn

- Không chạy UPDATE hoặc DELETE không có WHERE nếu chưa thực sự muốn tác động toàn bộ bảng.
- Luôn kiểm tra số row bị ảnh hưởng.
- Dùng parameterized query khi gọi SQL từ ứng dụng.
- Thử thao tác phá dữ liệu trong database test, không dùng database thật.

---

# Giai đoạn 2 — Query SQL cốt lõi

## Tuần 4: SELECT, lọc và sắp xếp

### Học

- SELECT column.
- SELECT DISTINCT.
- Alias.
- WHERE.
- AND, OR, NOT.
- IN, BETWEEN.
- LIKE và ILIKE.
- IS NULL và IS NOT NULL.
- ORDER BY.
- LIMIT và OFFSET.
- CASE.

### Bài tập

Viết query:

- Tìm sách theo khoảng giá.
- Tìm khách hàng chưa có số điện thoại.
- Tìm tiêu đề chứa từ khóa không phân biệt hoa thường.
- Lấy 10 sách đắt nhất.
- Phân loại sách theo mức giá bằng CASE.

### Lỗi thường gặp

- Dùng = NULL thay vì IS NULL.
- Dùng OR mà không có ngoặc khiến điều kiện sai.
- OFFSET lớn trên bảng lớn mà không hiểu chi phí.
- SELECT * trong API hoặc query production.

## Tuần 5: Hàm, biểu thức và xử lý dữ liệu

### Học

- Hàm chuỗi: lower, upper, trim, substring, concat.
- Hàm số: round, ceil, floor, abs.
- Hàm ngày giờ: now, current_date, date_trunc, age.
- COALESCE.
- NULLIF.
- CAST và toán tử ::.
- Biểu thức CASE.
- Kiểu boolean.

### Bài tập

- Chuẩn hóa email về chữ thường.
- Tính số ngày từ lúc tạo đơn.
- Thay NULL bằng giá trị mặc định khi hiển thị.
- Tính tổng tiền từng order item.
- Nhóm doanh thu theo tháng bằng date_trunc.

### Tiêu chí hoàn thành

- Biết kiểu dữ liệu đầu vào và đầu ra của hàm.
- Biết khi nào dùng COALESCE và khi nào cần sửa dữ liệu gốc.
- Không biến đổi column trong WHERE một cách tùy tiện nếu muốn index được sử dụng.

## Tuần 6: JOIN — kết hợp nhiều bảng

### Học

- INNER JOIN.
- LEFT JOIN.
- RIGHT JOIN ở mức nhận biết.
- CROSS JOIN ở mức nhận biết.
- Self join.
- Điều kiện ON và điều kiện WHERE.
- Join theo khóa chính/khóa ngoại.
- Tránh nhân bản row ngoài ý muốn.

### Bài tập

Viết query:

- Danh sách order kèm tên khách hàng.
- Chi tiết order kèm tên sách và số lượng.
- Danh sách tác giả chưa có sách.
- Khách hàng chưa từng đặt hàng.
- Doanh thu theo từng tác giả.

### Điều phải hiểu

Với LEFT JOIN, điều kiện lọc bảng bên phải đặt trong WHERE có thể biến kết quả thành INNER JOIN. Biết cách đặt điều kiện đúng trong ON hoặc WHERE.

---

# Giai đoạn 3 — Tổng hợp và truy vấn nhiều bước

## Tuần 7: GROUP BY, aggregate và HAVING

### Học

- COUNT.
- SUM.
- AVG.
- MIN và MAX.
- GROUP BY một column và nhiều column.
- HAVING.
- DISTINCT trong aggregate.
- Khác nhau giữa WHERE và HAVING.

### Bài tập

- Đếm số order theo khách hàng.
- Tính tổng doanh thu theo tháng.
- Tìm tác giả có ít nhất 5 sách.
- Tìm khách hàng có tổng chi tiêu lớn hơn một ngưỡng.
- Tính giá trị order trung bình.

### Bài tập tổng hợp

Tạo báo cáo doanh thu gồm:

~~~text
month
order_count
customer_count
gross_revenue
average_order_value
~~~

## Tuần 8: Subquery, CTE và window function cơ bản

### Học

- Scalar subquery.
- IN và EXISTS.
- Correlated subquery ở mức cơ bản.
- WITH và CTE.
- CTE nhiều bước.
- ROW_NUMBER.
- RANK.
- SUM OVER.
- PARTITION BY.
- ORDER BY trong window function.

### Bài tập

- Tìm sách có giá cao hơn giá trung bình.
- Tìm khách hàng đã từng mua một sách cụ thể bằng EXISTS.
- Dùng CTE để tính doanh thu rồi lọc top khách hàng.
- Xếp hạng sách bán chạy theo từng tác giả.
- Tính doanh thu lũy kế theo tháng.

### Quy tắc

- Dùng JOIN khi quan hệ rõ ràng.
- Dùng EXISTS khi chỉ cần biết có bản ghi tồn tại.
- Tách query nhiều bước bằng CTE để dễ đọc.
- Không dùng CTE chỉ để làm query dài hơn mà không tăng tính rõ ràng.

---

# Giai đoạn 4 — Thiết kế schema và tính toàn vẹn dữ liệu

## Tuần 9: Mô hình hóa và chuẩn hóa

### Học

- Entity và relationship.
- Cardinality.
- One-to-one, one-to-many, many-to-many.
- Bảng trung gian.
- Functional dependency ở mức cơ bản.
- First, second và third normal form.
- Khi nào denormalization có thể hữu ích.
- Natural key và surrogate key.

### Bài tập

Vẽ ERD cho hệ thống bán hàng:

~~~text
Customer 1 ─── N Order
Order    1 ─── N OrderItem
Book     1 ─── N OrderItem
Author   1 ─── N Book
~~~

Sau đó viết DDL với khóa và constraint đầy đủ.

### Tiêu chí hoàn thành

- Không lặp lại thông tin khách hàng trong mỗi order.
- Không lưu danh sách book_id trong một column.
- Biết bảng nào là bảng nghiệp vụ và bảng nào là bảng liên kết.
- Biết giải thích trade-off giữa chuẩn hóa và hiệu năng đọc.

## Tuần 10: Data type và constraint của PostgreSQL

### Học

- bigint và identity.
- numeric cho tiền.
- text.
- boolean.
- date, timestamp và timestamptz.
- uuid.
- jsonb ở mức cơ bản.
- array ở mức nhận biết.
- ENUM: ưu, nhược điểm và lựa chọn thay thế.
- CHECK constraint.
- Composite UNIQUE.
- ON DELETE RESTRICT, CASCADE, SET NULL.

### Khuyến nghị kiểu dữ liệu

| Nhu cầu | Nên dùng | Cần thận trọng |
|---|---|---|
| ID tăng dần | bigint identity | int nếu dữ liệu có thể lớn |
| Tiền | numeric | float |
| Thời điểm có timezone | timestamptz | timestamp không timezone |
| Chuỗi | text | varchar với giới hạn không cần thiết |
| Dữ liệu linh hoạt | jsonb | biến toàn bộ schema thành JSON |
| Boolean | boolean | text hoặc int để biểu diễn cờ |

### Bài tập

- Thêm constraint giá không âm.
- Tạo composite unique cho order_id và book_id.
- Chọn hành vi ON DELETE cho từng quan hệ.
- Lưu metadata ít ổn định trong jsonb nhưng giữ field nghiệp vụ chính ở column riêng.

---

# Giai đoạn 5 — Transaction, index và hiệu năng cơ bản

## Tuần 11: Transaction và concurrency

### Học

- BEGIN, COMMIT, ROLLBACK.
- Atomicity.
- Savepoint.
- Read committed.
- Repeatable read ở mức khái niệm.
- Row-level lock.
- SELECT FOR UPDATE.
- Deadlock.
- Lost update.
- Idempotency.

### Bài tập

Mô phỏng đặt hàng:

1. Bắt đầu transaction.
2. Khóa row inventory.
3. Kiểm tra tồn kho.
4. Trừ số lượng.
5. Tạo order và order item.
6. Commit hoặc rollback khi có lỗi.

~~~sql
BEGIN;

SELECT quantity
FROM inventory
WHERE book_id = 10
FOR UPDATE;

UPDATE inventory
SET quantity = quantity - 1
WHERE book_id = 10
  AND quantity > 0;

COMMIT;
~~~

### Cần hiểu

- Transaction càng dài càng dễ giữ lock lâu.
- Không gọi dịch vụ bên ngoài trong transaction database dài.
- Thứ tự lock không nhất quán có thể tạo deadlock.
- Cần kiểm tra row count sau UPDATE để biết thao tác có thành công.

## Tuần 12: Index, EXPLAIN và project cuối khóa

### Học

- B-tree index mặc định.
- Composite index.
- Partial index.
- GIN index cho jsonb hoặc full-text search ở mức nhận biết.
- BRIN cho dữ liệu lớn có thứ tự tự nhiên ở mức nhận biết.
- Index trên foreign key.
- Khi index làm chậm INSERT/UPDATE.
- EXPLAIN.
- EXPLAIN ANALYZE.
- Seq Scan, Index Scan và Bitmap Heap Scan.
- ANALYZE và statistics ở mức cơ bản.

### Thực hành index

~~~sql
CREATE INDEX idx_orders_customer_created
ON orders (customer_id, created_at DESC);

CREATE INDEX idx_active_users_email
ON users (email)
WHERE deleted_at IS NULL;

EXPLAIN (ANALYZE, BUFFERS)
SELECT *
FROM orders
WHERE customer_id = 10
ORDER BY created_at DESC
LIMIT 20;
~~~

### Quy tắc index

- Equality columns thường đứng trước range columns trong composite index.
- Không tạo index cho mọi column.
- Đo query trước và sau khi tạo index.
- Index phải phục vụ query thật.
- Nhớ chạy ANALYZE sau khi seed lượng dữ liệu lớn.

---

# Project cuối khóa: Bookstore Database

## Phạm vi

Xây database cho cửa hàng sách gồm:

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

## Yêu cầu cấu trúc

- Có ERD.
- Có file 001_schema.sql.
- Có file 002_constraints.sql.
- Có file 003_seed.sql.
- Có file 004_queries.sql.
- Có file 005_indexes.sql.
- Có README hướng dẫn tạo database.
- Có dữ liệu seed tối thiểu 1.000 books, 10.000 orders và 50.000 order_items.

## Query bắt buộc

- Top 10 sách bán chạy.
- Doanh thu theo tháng.
- Khách hàng chưa từng mua hàng.
- Đơn hàng có tổng tiền cao nhất.
- Sách chưa từng được bán.
- Tác giả có doanh thu cao nhất.
- Tồn kho dưới ngưỡng.
- Doanh thu lũy kế.
- Top 3 sách theo từng thể loại.
- Tìm kiếm sách theo tên không phân biệt hoa thường.

## Kiểm tra transaction

- Hai người cùng mua cuốn sách cuối cùng.
- Một payment thất bại sau khi order được tạo.
- Retry cùng một request tạo order.
- Một order chứa nhiều item và một item hết hàng.

## Definition of Done

- Có thể dựng database từ đầu bằng script.
- Script chạy được trên database trống.
- Mọi bảng có primary key phù hợp.
- Quan hệ quan trọng có foreign key.
- Dữ liệu sai bị chặn bằng constraint.
- Query không dùng SELECT * khi không cần.
- Có index giải thích được bằng query thực tế.
- Có EXPLAIN ANALYZE cho ít nhất 5 query.
- Có transaction cho nghiệp vụ nhiều bước.
- Có backup và restore thử nghiệm.

---

# Lịch học mỗi tuần

Với 8–12 giờ/tuần:

| Buổi | Nội dung |
|---|---|
| Buổi 1 — 1,5 giờ | Đọc lý thuyết và xem schema mẫu |
| Buổi 2 — 2 giờ | Tự viết query nhỏ |
| Buổi 3 — 2 giờ | Làm bài tập trên dataset |
| Buổi 4 — 2 giờ | Thiết kế hoặc sửa schema |
| Cuối tuần — 2–4 giờ | Project, review query và ghi chú |

Tỉ lệ thời gian:

~~~text
20% đọc tài liệu
60% tự viết SQL
20% debug, EXPLAIN và review
~~~

Mỗi query nên được luyện qua các bước:

1. Viết yêu cầu bằng ngôn ngữ tự nhiên.
2. Xác định bảng và quan hệ cần dùng.
3. Viết query đơn giản nhất.
4. Kiểm tra kết quả với dữ liệu nhỏ.
5. Thử NULL, dữ liệu trùng và dữ liệu rỗng.
6. Đọc EXPLAIN khi query đủ phức tạp.
7. Lưu query có tên và ghi chú mục đích.

---

# Những chủ đề chưa cần học ngay

Sau khi hoàn thành 12 tuần cơ bản, mới học tiếp:

- PL/pgSQL function phức tạp.
- Trigger nâng cao.
- Full-text search chuyên sâu.
- Partitioning.
- Replication và high availability.
- Logical replication.
- Row-level security.
- Query planner internals.
- Vacuum tuning chuyên sâu.
- Connection pooling và PgBouncer.
- PostGIS.
- Backup point-in-time recovery.

---

# Checklist tự đánh giá

## SQL

- Viết được query có nhiều JOIN.
- Biết lúc nào dùng WHERE và HAVING.
- Biết xử lý NULL.
- Biết dùng GROUP BY và window function.
- Biết khi nào dùng EXISTS thay vì IN.
- Biết đọc query kết quả sai do JOIN nhân bản row.

## Thiết kế database

- Vẽ được ERD.
- Chọn được primary key.
- Thiết lập foreign key.
- Chọn được data type phù hợp.
- Dùng constraint để bảo vệ dữ liệu.
- Nhận biết khi nào schema bị lặp dữ liệu.

## PostgreSQL

- Dùng được psql.
- Tạo role và phân quyền cơ bản.
- Dùng transaction.
- Dùng EXPLAIN ANALYZE.
- Tạo index có lý do.
- Backup bằng pg_dump.
- Restore vào database mới.

## Thực hành

- Có project database hoàn chỉnh.
- Có schema script và seed script.
- Có ít nhất 20 query đã tự viết.
- Có README.
- Có thể giải thích mọi constraint và index trong project.

---

# Nguồn học chính thức

- [PostgreSQL 18.6 Documentation](https://www.postgresql.org/docs/18/)
- [Tutorial: Getting Started](https://www.postgresql.org/docs/18/tutorial-start.html)
- [The SQL Language](https://www.postgresql.org/docs/18/tutorial-sql.html)
- [Data Definition](https://www.postgresql.org/docs/18/ddl.html)
- [Data Manipulation](https://www.postgresql.org/docs/18/dml.html)
- [Queries](https://www.postgresql.org/docs/18/queries.html)
- [Data Types](https://www.postgresql.org/docs/18/datatype.html)
- [Indexes](https://www.postgresql.org/docs/18/indexes.html)
- [Concurrency Control](https://www.postgresql.org/docs/18/mvcc.html)
- [Performance Tips](https://www.postgresql.org/docs/18/performance-tips.html)
- [Backup and Restore](https://www.postgresql.org/docs/18/backup.html)
- [psql reference](https://www.postgresql.org/docs/18/app-psql.html)

Nguyên tắc quan trọng nhất: **đừng học SQL bằng cách chỉ đọc syntax**. Mỗi chủ đề phải được áp dụng vào schema thật, dataset thật, query thật và được kiểm tra bằng kết quả hoặc EXPLAIN.

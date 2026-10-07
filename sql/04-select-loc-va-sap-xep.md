# Tuần 4 cơ bản: SELECT, lọc và sắp xếp trong PostgreSQL

> Mục tiêu: viết được SELECT rõ ràng, lọc bằng WHERE, AND/OR/NOT, IN, BETWEEN, LIKE/ILIKE, xử lý NULL, loại trùng bằng DISTINCT, đặt alias, sắp xếp bằng ORDER BY, phân trang bằng LIMIT/OFFSET và phân loại bằng CASE.

Một query đọc dữ liệu thường có dạng:

~~~sql
SELECT column_1, column_2
FROM table_name
WHERE condition
ORDER BY column_1 DESC
LIMIT 20;
~~~

SELECT chọn dữ liệu, WHERE lọc row, ORDER BY tạo thứ tự, LIMIT giới hạn row và OFFSET bỏ qua row đầu. Không có ORDER BY thì PostgreSQL không cam kết thứ tự kết quả.

---

## 1. Schema lab

~~~sql
DROP SCHEMA IF EXISTS basic_week4 CASCADE;
CREATE SCHEMA basic_week4;

CREATE TABLE basic_week4.books (
    book_id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title text NOT NULL,
    category text NOT NULL,
    price numeric(10, 2) NOT NULL,
    published_year integer,
    stock_quantity integer NOT NULL DEFAULT 0,
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE basic_week4.customers (
    customer_id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    full_name text NOT NULL,
    email text NOT NULL,
    phone text,
    city text,
    active boolean NOT NULL DEFAULT true
);

INSERT INTO basic_week4.books
    (title, category, price, published_year, stock_quantity)
VALUES
    ('Cho toi xin mot ve di tuoi tho', 'novel', 85000, 2008, 12),
    ('Mat biec', 'novel', 95000, 1990, 4),
    ('Norwegian Wood', 'novel', 145000, 1987, 8),
    ('Kafka on the Shore', 'novel', 175000, 2002, 0),
    ('Animal Farm', 'classic', 70000, 1945, 20),
    ('1984', 'classic', 110000, 1949, 6),
    ('The Dispossessed', 'science fiction', 210000, 1974, 3),
    ('A Wizard of Earthsea', 'science fiction', 130000, 1968, 9),
    ('PostgreSQL Basics', 'technology', 250000, NULL, 15);

INSERT INTO basic_week4.customers
    (full_name, email, phone, city, active)
VALUES
    ('An Nguyen', 'an@example.com', '0900000001', 'Ha Noi', true),
    ('Binh Tran', 'binh@example.com', NULL, 'Da Nang', true),
    ('Chi Le', 'chi@example.com', '', NULL, true),
    ('Dung Pham', 'dung@example.com', NULL, 'Ho Chi Minh', false);
~~~

---

## 2. SELECT column và alias

Chọn các column cần thiết:

~~~sql
SELECT
    book_id,
    title,
    category,
    price
FROM basic_week4.books;
~~~

SELECT cụ thể giúp giảm dữ liệu truyền, tránh expose column ngoài ý muốn và làm API ổn định hơn. SELECT * phù hợp khám phá dữ liệu, nhưng nên tránh trong API/report production.

Expression cũng có thể xuất hiện trong SELECT:

~~~sql
SELECT
    title,
    price,
    price * 0.9 AS sale_price,
    'book' AS item_type
FROM basic_week4.books;
~~~

Alias:

~~~sql
SELECT
    title AS book_title,
    price AS listed_price,
    stock_quantity AS stock
FROM basic_week4.books;
~~~

Alias chỉ đổi tên trong result set, không đổi tên column thật. Alias table giúp query rõ hơn:

~~~sql
SELECT b.book_id, b.title, b.price
FROM basic_week4.books AS b;
~~~

Không nên alias gây nhầm như title AS price và price AS title.

---

## 3. DISTINCT

Loại row trùng:

~~~sql
SELECT DISTINCT category
FROM basic_week4.books
ORDER BY category;
~~~

DISTINCT xét toàn bộ tổ hợp column được chọn:

~~~sql
SELECT DISTINCT category, published_year
FROM basic_week4.books
ORDER BY category, published_year;
~~~

Đếm category duy nhất:

~~~sql
SELECT count(DISTINCT category) AS category_count
FROM basic_week4.books;
~~~

DISTINCT không phải cách sửa JOIN bị nhân bản row. Nếu duplicate là ngoài ý muốn, tìm nguyên nhân cardinality trước.

---

## 4. WHERE và toán tử so sánh

~~~sql
SELECT book_id, title, stock_quantity
FROM basic_week4.books
WHERE stock_quantity > 0;
~~~

Các toán tử:

~~~sql
SELECT * FROM basic_week4.books WHERE price = 110000;
SELECT * FROM basic_week4.books WHERE price <> 110000;
SELECT * FROM basic_week4.books WHERE price >= 100000;
SELECT * FROM basic_week4.books WHERE price < 200000;
~~~

Các dạng chính:

~~~text
=   bằng
<>  khác
>   lớn hơn
>=  lớn hơn hoặc bằng
<   nhỏ hơn
<=  nhỏ hơn hoặc bằng
~~~

WHERE chỉ giữ row khi expression là TRUE. FALSE và UNKNOWN bị loại.

---

## 5. AND, OR và NOT

### AND

~~~sql
SELECT title, price, stock_quantity
FROM basic_week4.books
WHERE active = true
  AND stock_quantity > 0
  AND price < 150000;
~~~

Tất cả điều kiện phải đúng.

### OR

~~~sql
SELECT title, category
FROM basic_week4.books
WHERE category = 'novel'
   OR category = 'classic';
~~~

### NOT

~~~sql
SELECT title, category
FROM basic_week4.books
WHERE NOT category = 'novel';
~~~

### Ngoặc khi trộn AND và OR

Ý định: sách active và thuộc novel hoặc classic:

~~~sql
SELECT *
FROM basic_week4.books
WHERE active = true
  AND (category = 'novel' OR category = 'classic');
~~~

Không có ngoặc, AND có precedence cao hơn OR:

~~~sql
WHERE (active = true AND category = 'novel')
   OR category = 'classic'
~~~

Kết quả có thể trả cả sách classic inactive. Khi trộn AND/OR, thêm ngoặc để thể hiện ý định.

---

## 6. IN và NOT IN

IN là cách viết ngắn cho nhiều điều kiện OR:

~~~sql
SELECT title, category
FROM basic_week4.books
WHERE category IN ('novel', 'classic');
~~~

NOT IN:

~~~sql
SELECT title, category
FROM basic_week4.books
WHERE category NOT IN ('novel', 'classic');
~~~

Cẩn thận khi danh sách NOT IN có NULL:

~~~sql
SELECT 1
WHERE 5 NOT IN (1, 2, NULL);
~~~

So sánh với NULL tạo UNKNOWN, nên kết quả không phải TRUE. Khi danh sách đến từ dữ liệu có thể NULL, phải xử lý NULL rõ ràng.

---

## 7. BETWEEN

BETWEEN bao gồm cả hai đầu:

~~~sql
SELECT title, price
FROM basic_week4.books
WHERE price BETWEEN 100000 AND 200000;
~~~

Tương đương:

~~~sql
WHERE price >= 100000
  AND price <= 200000
~~~

Với timestamp, thường dùng khoảng nửa mở:

~~~sql
WHERE created_at >= timestamptz '2026-02-01 00:00:00+07'
  AND created_at <  timestamptz '2026-03-01 00:00:00+07'
~~~

Mẫu [start, end) tránh phải đoán microsecond cuối ngày.

---

## 8. LIKE và ILIKE

LIKE dùng wildcard:

- %: zero hoặc nhiều ký tự;
- _: đúng một ký tự.

~~~sql
SELECT book_id, title
FROM basic_week4.books
WHERE title LIKE '%Postgre%';

SELECT book_id, title
FROM basic_week4.books
WHERE title ILIKE '%postgres%';
~~~

ILIKE không phân biệt hoa thường:

~~~sql
SELECT title
FROM basic_week4.books
WHERE title ILIKE 'the%';
~~~

Pattern bắt đầu bằng % thường khó dùng B-tree index thông thường. Với bảng lớn, full-text search hoặc trigram là hướng chuyên sâu hơn.

Nếu cần tìm literal wildcard:

~~~sql
SELECT *
FROM some_table
WHERE code LIKE '%10\%%' ESCAPE '\';
~~~

---

## 9. IS NULL và IS NOT NULL

Sai:

~~~sql
SELECT *
FROM basic_week4.customers
WHERE phone = NULL;
~~~

Đúng:

~~~sql
SELECT customer_id, full_name, email
FROM basic_week4.customers
WHERE phone IS NULL;
~~~

Có phone:

~~~sql
SELECT customer_id, full_name, phone
FROM basic_week4.customers
WHERE phone IS NOT NULL;
~~~

Nếu chuỗi rỗng cũng được coi là thiếu:

~~~sql
SELECT customer_id, full_name, phone
FROM basic_week4.customers
WHERE phone IS NULL
   OR phone = '';
~~~

NULL và empty text là hai giá trị khác nhau. Cách lưu nào đúng phải do domain quyết định.

---

## 10. ORDER BY

Tăng dần:

~~~sql
SELECT title, price
FROM basic_week4.books
ORDER BY price ASC;
~~~

ASC là mặc định:

~~~sql
SELECT title, price
FROM basic_week4.books
ORDER BY price;
~~~

Giảm dần:

~~~sql
SELECT title, price
FROM basic_week4.books
ORDER BY price DESC;
~~~

Nhiều key:

~~~sql
SELECT title, category, price, stock_quantity
FROM basic_week4.books
ORDER BY category ASC, price DESC, title ASC;
~~~

Có thể order theo alias:

~~~sql
SELECT title, price AS listed_price
FROM basic_week4.books
ORDER BY listed_price DESC;
~~~

Ưu tiên tên alias/column thay vì vị trí SELECT:

~~~sql
SELECT title, price
FROM basic_week4.books
ORDER BY 2 DESC;
~~~

ORDER BY vị trí dễ vỡ khi thay đổi SELECT list.

### NULLS FIRST và NULLS LAST

~~~sql
SELECT title, published_year
FROM basic_week4.books
ORDER BY published_year ASC NULLS LAST;

SELECT title, published_year
FROM basic_week4.books
ORDER BY published_year DESC NULLS FIRST;
~~~

### Tie-breaker

Nếu nhiều row cùng price, thứ tự giữa chúng không ổn định nếu chỉ order một column:

~~~sql
SELECT book_id, title, price
FROM basic_week4.books
ORDER BY price DESC, book_id ASC;
~~~

Tie-breaker rất quan trọng với LIMIT/OFFSET và pagination.

---

## 11. LIMIT và OFFSET

10 sách đắt nhất:

~~~sql
SELECT book_id, title, price
FROM basic_week4.books
ORDER BY price DESC, book_id ASC
LIMIT 10;
~~~

Trang thứ hai, mỗi trang 3 row:

~~~sql
SELECT book_id, title, price
FROM basic_week4.books
ORDER BY price DESC, book_id ASC
LIMIT 3 OFFSET 3;
~~~

LIMIT không có ORDER BY không tạo ra “top” ổn định:

~~~sql
SELECT title, price
FROM basic_week4.books
LIMIT 10;
~~~

OFFSET lớn có thể tốn chi phí vì database phải tìm và bỏ qua nhiều row. Dữ liệu thêm/xóa giữa các page cũng làm page bị trượt.

Ở bảng lớn, keyset pagination thường tốt hơn:

~~~sql
SELECT book_id, title, price
FROM basic_week4.books
WHERE (price, book_id) < (150000, 8)
ORDER BY price DESC, book_id DESC
LIMIT 10;
~~~

Keyset cần cursor từ page trước và thứ tự nhất quán. Đây là bước chuẩn bị cho phần index/query nâng cao.

---

## 12. CASE

Phân loại sách theo giá:

~~~sql
SELECT
    title,
    price,
    CASE
        WHEN price < 100000 THEN 'budget'
        WHEN price < 200000 THEN 'standard'
        ELSE 'premium'
    END AS price_level
FROM basic_week4.books
ORDER BY price;
~~~

CASE xét điều kiện từ trên xuống. Branch rộng đặt trước có thể che branch hẹp.

Phân loại tồn kho:

~~~sql
SELECT
    title,
    stock_quantity,
    CASE
        WHEN stock_quantity = 0 THEN 'out_of_stock'
        WHEN stock_quantity < 5 THEN 'low_stock'
        ELSE 'available'
    END AS stock_status
FROM basic_week4.books;
~~~

CASE trong ORDER BY:

~~~sql
SELECT title, category, price
FROM basic_week4.books
ORDER BY
    CASE category
        WHEN 'technology' THEN 1
        WHEN 'science fiction' THEN 2
        WHEN 'classic' THEN 3
        ELSE 4
    END,
    title;
~~~

CASE xử lý NULL:

~~~sql
SELECT
    title,
    CASE
        WHEN published_year IS NULL THEN 'unknown_year'
        WHEN published_year < 2000 THEN 'older'
        ELSE 'recent'
    END AS publication_group
FROM basic_week4.books;
~~~

---

## 13. Kết hợp query

Sách active, còn hàng, thuộc novel/classic, giá từ 70.000 đến 180.000:

~~~sql
SELECT
    book_id,
    title,
    category,
    price AS original_price,
    price * 0.9 AS sale_price,
    stock_quantity
FROM basic_week4.books
WHERE active IS TRUE
  AND category IN ('novel', 'classic')
  AND price BETWEEN 70000 AND 180000
  AND stock_quantity > 0
ORDER BY sale_price ASC, book_id ASC
LIMIT 10;
~~~

Khách hàng active nhưng thiếu số điện thoại:

~~~sql
SELECT customer_id, full_name, email, phone
FROM basic_week4.customers
WHERE active IS TRUE
  AND (phone IS NULL OR phone = '')
ORDER BY customer_id;
~~~

Tiêu đề chứa từ khóa không phân biệt hoa thường:

~~~sql
SELECT book_id, title, category
FROM basic_week4.books
WHERE title ILIKE '%book%'
   OR title ILIKE '%postgre%'
ORDER BY title;
~~~

---

## 14. Thứ tự logic của SELECT

Ở mức khái niệm:

~~~text
FROM
WHERE
SELECT
ORDER BY
LIMIT/OFFSET
~~~

Hệ quả:

- WHERE lọc row trước result;
- alias SELECT thường chưa dùng được trong WHERE cùng cấp;
- ORDER BY có thể dùng alias;
- LIMIT áp dụng sau ORDER BY.

Alias lọc qua subquery:

~~~sql
SELECT *
FROM (
    SELECT
        title,
        price * 0.9 AS sale_price
    FROM basic_week4.books
) AS priced_books
WHERE sale_price < 100000;
~~~

Subquery sẽ được học sâu hơn ở Tuần 8.

---

## 15. Bài tập theo roadmap

### Bài 1 — Sách theo khoảng giá

Trả book_id, title, category và price cho sách từ 100.000 đến 200.000, bao gồm boundary. Sắp xếp giá tăng dần rồi title.

~~~sql
SELECT book_id, title, category, price
FROM basic_week4.books
WHERE price BETWEEN 100000 AND 200000
ORDER BY price, title;
~~~

### Bài 2 — Khách hàng chưa có số điện thoại

~~~sql
SELECT customer_id, full_name, email
FROM basic_week4.customers
WHERE phone IS NULL;
~~~

Mở rộng để coi chuỗi rỗng cũng là thiếu.

### Bài 3 — Tìm tiêu đề

~~~sql
SELECT book_id, title
FROM basic_week4.books
WHERE title ILIKE '%earth%'
   OR title ILIKE '%postgre%';
~~~

### Bài 4 — 10 sách đắt nhất

~~~sql
SELECT book_id, title, price
FROM basic_week4.books
ORDER BY price DESC, book_id ASC
LIMIT 10;
~~~

### Bài 5 — Phân loại giá

~~~sql
SELECT
    title,
    price,
    CASE
        WHEN price < 100000 THEN 'budget'
        WHEN price < 200000 THEN 'standard'
        ELSE 'premium'
    END AS price_level
FROM basic_week4.books;
~~~

---

## 16. Lỗi thường gặp

### SELECT * trong API

Chọn column cụ thể để response ổn định và tránh expose dữ liệu.

### Dùng = NULL

Dùng IS NULL hoặc IS NOT NULL.

### OR không có ngoặc

AND có precedence cao hơn OR. Thêm ngoặc khi trộn.

### LIMIT không có ORDER BY

Không có thứ tự thì không có top-N ổn định.

### Thiếu tie-breaker

Các row cùng giá/thời gian có thể đổi thứ tự. Thêm primary key.

### OFFSET lớn

Page sâu có thể chậm và trượt. Cân nhắc keyset pagination.

### BETWEEN sai boundary

BETWEEN bao gồm cả hai đầu. Với timestamp, thường dùng khoảng nửa mở.

### NOT IN chứa NULL

NULL có thể làm kết quả thành UNKNOWN.

### LIKE wildcard đầu

Pattern bắt đầu bằng % thường khó dùng B-tree index.

---

## 17. Lab hoàn chỉnh

### Bước 1: Sách đang bán

~~~sql
SELECT book_id, title, category, price, stock_quantity
FROM basic_week4.books
WHERE active IS TRUE
  AND stock_quantity > 0
ORDER BY title, book_id;
~~~

### Bước 2: Top 10 sách

~~~sql
SELECT
    book_id,
    title,
    price,
    CASE
        WHEN price < 100000 THEN 'budget'
        WHEN price < 200000 THEN 'standard'
        ELSE 'premium'
    END AS price_level
FROM basic_week4.books
WHERE active IS TRUE
ORDER BY price DESC, book_id ASC
LIMIT 10;
~~~

### Bước 3: Pagination

~~~sql
SELECT book_id, title, price
FROM basic_week4.books
ORDER BY price DESC, book_id ASC
LIMIT 3 OFFSET 0;

SELECT book_id, title, price
FROM basic_week4.books
ORDER BY price DESC, book_id ASC
LIMIT 3 OFFSET 3;
~~~

### Bước 4: Đọc plan cơ bản

~~~sql
EXPLAIN
SELECT book_id, title, price
FROM basic_week4.books
WHERE price BETWEEN 100000 AND 200000
ORDER BY price DESC
LIMIT 10;
~~~

Bảng nhỏ có thể dùng sequential scan. Mục tiêu là làm quen với việc kiểm tra query, không ép index bằng mọi giá.

---

## 18. Checklist viết SELECT

- Có thật sự cần SELECT * không?
- Column trả về đã đủ và không thừa chưa?
- Alias đã rõ chưa?
- NULL đã dùng IS NULL chưa?
- AND/OR đã có ngoặc chưa?
- IN/BETWEEN có đúng boundary chưa?
- LIKE/ILIKE có đúng wildcard chưa?
- ORDER BY có deterministic không?
- Có tie-breaker chưa?
- LIMIT có ORDER BY không?
- OFFSET có phù hợp kích thước bảng không?
- CASE có ELSE và branch NULL phù hợp chưa?
- Input sort/filter từ API có parameterized và whitelist chưa?

---

## 19. Tiêu chí hoàn thành Tuần 4

Bạn hoàn thành bài khi có thể:

- chọn column bằng SELECT;
- dùng DISTINCT đúng mục đích;
- đặt alias column/table;
- lọc bằng WHERE và toán tử so sánh;
- kết hợp AND, OR, NOT với ngoặc đúng;
- dùng IN, NOT IN và BETWEEN;
- tìm text bằng LIKE và ILIKE;
- lọc NULL bằng IS NULL/IS NOT NULL;
- sắp xếp nhiều column bằng ORDER BY;
- kiểm soát NULLS FIRST/LAST;
- dùng LIMIT/OFFSET;
- giải thích vấn đề OFFSET lớn;
- thêm tie-breaker cho kết quả ổn định;
- dùng CASE để phân loại;
- viết được năm query bài tập của roadmap;
- tránh SELECT * trong API và tránh = NULL.

---

## Tóm tắt cần nhớ

1. SELECT column cụ thể tốt hơn SELECT * trong production.
2. DISTINCT loại trùng theo toàn bộ tổ hợp column được chọn.
3. WHERE chỉ giữ row khi điều kiện là TRUE.
4. AND có precedence cao hơn OR; trộn hai loại thì thêm ngoặc.
5. IN viết ngắn cho nhiều điều kiện OR.
6. BETWEEN bao gồm cả hai đầu.
7. NULL dùng IS NULL/IS NOT NULL, không dùng = NULL.
8. ILIKE tìm text không phân biệt hoa thường.
9. ORDER BY cần tie-breaker nếu kết quả phải ổn định.
10. LIMIT không có ORDER BY không tạo top-N có nghĩa.
11. OFFSET lớn có chi phí và có thể làm pagination trượt.
12. CASE biến dữ liệu thành nhãn theo business rule.

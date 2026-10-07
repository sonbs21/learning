# Tuần 6: JOIN — kết hợp nhiều bảng trong PostgreSQL

> Mục tiêu của bài học: viết được query kết hợp nhiều bảng một cách chính xác, hiểu vì sao số dòng thay đổi sau mỗi `JOIN`, và biết đặt điều kiện ở `ON` hay `WHERE` tùy theo ý nghĩa của query.

## 1. JOIN giải quyết vấn đề gì?

Trong database quan hệ, một thông tin thường được tách thành nhiều bảng để tránh lặp dữ liệu.

Ví dụ trong database cửa hàng sách:

- `customers` lưu thông tin khách hàng.
- `orders` lưu đơn hàng và chỉ giữ `customer_id`.
- `order_items` lưu các dòng sản phẩm trong đơn và giữ `order_id`, `book_id`.
- `books` lưu tên và giá sách.
- `authors` lưu thông tin tác giả.

Muốn hiển thị một đơn hàng có tên khách hàng, tên sách và số lượng, ta phải kết hợp nhiều bảng bằng `JOIN`.

```sql
SELECT
    o.id AS order_id,
    c.full_name AS customer_name,
    b.title AS book_title,
    oi.quantity
FROM orders AS o
JOIN customers AS c ON c.id = o.customer_id
JOIN order_items AS oi ON oi.order_id = o.id
JOIN books AS b ON b.id = oi.book_id;
```

Query trên đọc theo chuỗi quan hệ:

```text
customers 1 ─── N orders 1 ─── N order_items N ─── 1 books
```

`JOIN` không “gộp” hai bảng thành một bảng vật lý mới. Nó tạo ra một tập kết quả tạm thời bằng cách ghép các row thỏa điều kiện liên kết.

---

## 2. Schema và dữ liệu mẫu

Nếu đã có schema của project bookstore, có thể dùng trực tiếp. Nếu chưa có, chạy script nhỏ dưới đây trong database học tập.

```sql
DROP TABLE IF EXISTS order_items, orders, books, authors, customers;

CREATE TABLE authors (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name text NOT NULL
);

CREATE TABLE books (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    author_id bigint REFERENCES authors(id),
    title text NOT NULL,
    price numeric(12, 2) NOT NULL CHECK (price >= 0)
);

CREATE TABLE customers (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    full_name text NOT NULL,
    email text NOT NULL UNIQUE
);

CREATE TABLE orders (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id bigint NOT NULL REFERENCES customers(id),
    ordered_at timestamptz NOT NULL,
    status text NOT NULL
);

CREATE TABLE order_items (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id bigint NOT NULL REFERENCES orders(id),
    book_id bigint NOT NULL REFERENCES books(id),
    quantity integer NOT NULL CHECK (quantity > 0),
    unit_price numeric(12, 2) NOT NULL CHECK (unit_price >= 0)
);

INSERT INTO authors (name) VALUES
    ('George Orwell'),
    ('Nguyen Nhat Anh'),
    ('Tolkien'),
    ('Tác giả chưa có sách');

INSERT INTO books (author_id, title, price) VALUES
    (1, '1984', 120000),
    (1, 'Animal Farm', 90000),
    (2, 'Cho tôi xin một vé đi tuổi thơ', 85000),
    (3, 'The Hobbit', 180000);

INSERT INTO customers (full_name, email) VALUES
    ('Nguyen Van An', 'an@example.com'),
    ('Tran Thi Binh', 'binh@example.com'),
    ('Le Van Cuong', 'cuong@example.com');

INSERT INTO orders (customer_id, ordered_at, status) VALUES
    (1, '2026-01-10 09:00:00+07', 'paid'),
    (1, '2026-01-12 14:30:00+07', 'pending'),
    (2, '2026-01-15 11:20:00+07', 'paid');

INSERT INTO order_items (order_id, book_id, quantity, unit_price) VALUES
    (1, 1, 2, 120000),
    (1, 3, 1, 85000),
    (2, 4, 1, 180000),
    (3, 2, 3, 90000);
```

Lưu ý: thứ tự ID trong ví dụ phụ thuộc vào dữ liệu mới tạo. Trong project thật, không nên giả định ID luôn bắt đầu từ một giá trị cố định; hãy lấy ID bằng query hoặc `RETURNING`.

---

## 3. INNER JOIN

### 3.1. Cú pháp

```sql
SELECT column_list
FROM left_table AS l
INNER JOIN right_table AS r
    ON r.foreign_key = l.primary_key;
```

Từ khóa `INNER` thường được bỏ qua:

```sql
FROM left_table AS l
JOIN right_table AS r
    ON r.foreign_key = l.primary_key
```

`INNER JOIN` chỉ trả về row có cặp tương ứng ở cả hai bảng.

### 3.2. Danh sách order kèm tên khách hàng

```sql
SELECT
    o.id AS order_id,
    o.ordered_at,
    o.status,
    c.id AS customer_id,
    c.full_name,
    c.email
FROM orders AS o
INNER JOIN customers AS c
    ON c.id = o.customer_id
ORDER BY o.id;
```

`orders.customer_id` là khóa ngoại trỏ tới `customers.id`. Vì vậy, điều kiện join đúng là:

```sql
c.id = o.customer_id
```

Không nên nối tùy ý các column cùng kiểu dữ liệu. Một điều kiện `JOIN` tốt thể hiện đúng quan hệ dữ liệu, thường là khóa chính với khóa ngoại.

### 3.3. Chi tiết order kèm sách và số lượng

```sql
SELECT
    o.id AS order_id,
    c.full_name AS customer_name,
    b.title AS book_title,
    oi.quantity,
    oi.unit_price,
    oi.quantity * oi.unit_price AS line_total
FROM orders AS o
JOIN customers AS c ON c.id = o.customer_id
JOIN order_items AS oi ON oi.order_id = o.id
JOIN books AS b ON b.id = oi.book_id
ORDER BY o.id, oi.id;
```

Trong query này, một order có thể xuất hiện nhiều lần vì một order có nhiều `order_items`. Đây không phải lỗi: mỗi row kết quả đại diện cho một dòng sản phẩm trong order.

---

## 4. LEFT JOIN: giữ lại toàn bộ row ở bảng bên trái

### 4.1. Cú pháp và ý nghĩa

```sql
SELECT ...
FROM left_table AS l
LEFT JOIN right_table AS r
    ON r.left_id = l.id;
```

`LEFT JOIN` trả về:

1. Các row có cặp tương ứng.
2. Các row của bảng trái không có cặp, với column từ bảng phải bằng `NULL`.

### 4.2. Danh sách tác giả, kể cả tác giả chưa có sách

```sql
SELECT
    a.id,
    a.name,
    b.id AS book_id,
    b.title
FROM authors AS a
LEFT JOIN books AS b
    ON b.author_id = a.id
ORDER BY a.id, b.id;
```

Tác giả `Tác giả chưa có sách` vẫn xuất hiện, nhưng `book_id` và `title` là `NULL`.

Muốn chỉ lấy tác giả chưa có sách:

```sql
SELECT
    a.id,
    a.name
FROM authors AS a
LEFT JOIN books AS b
    ON b.author_id = a.id
WHERE b.id IS NULL;
```

Đây là mẫu thường dùng để tìm row “không có bản ghi liên quan”.

### 4.3. Khách hàng chưa từng đặt hàng

```sql
SELECT
    c.id,
    c.full_name,
    c.email
FROM customers AS c
LEFT JOIN orders AS o
    ON o.customer_id = c.id
WHERE o.id IS NULL;
```

Có thể viết bằng `NOT EXISTS`, thường dễ diễn đạt hơn khi chỉ cần kiểm tra sự tồn tại:

```sql
SELECT c.id, c.full_name, c.email
FROM customers AS c
WHERE NOT EXISTS (
    SELECT 1
    FROM orders AS o
    WHERE o.customer_id = c.id
);
```

Ở tuần này, cần hiểu cả hai cách. Việc chọn `LEFT JOIN ... IS NULL` hay `NOT EXISTS` có thể phụ thuộc vào mục đích query và style của team.

---

## 5. `ON` và `WHERE`: điểm dễ sai nhất của LEFT JOIN

Hai query dưới đây không tương đương.

### 5.1. Đặt điều kiện trạng thái trong `WHERE`

```sql
SELECT
    c.full_name,
    o.id AS order_id,
    o.status
FROM customers AS c
LEFT JOIN orders AS o
    ON o.customer_id = c.id
WHERE o.status = 'paid';
```

Với khách hàng chưa có order, `o.status` là `NULL`. Điều kiện `o.status = 'paid'` không đúng, nên row bị loại. Kết quả gần giống `INNER JOIN`.

### 5.2. Đặt điều kiện trạng thái trong `ON`

```sql
SELECT
    c.full_name,
    o.id AS paid_order_id,
    o.status
FROM customers AS c
LEFT JOIN orders AS o
    ON o.customer_id = c.id
   AND o.status = 'paid';
```

Query này vẫn giữ toàn bộ khách hàng. Khách hàng không có order `paid` có `paid_order_id = NULL`.

### 5.3. Quy tắc thực hành

- Điều kiện xác định row nào được ghép vào bảng bên phải: đặt trong `ON`.
- Điều kiện lọc kết quả cuối cùng, áp dụng cho cả tập kết quả: đặt trong `WHERE`.
- Với `INNER JOIN`, nhiều điều kiện trong `ON` và `WHERE` thường cho cùng kết quả logic. Với `LEFT JOIN`, vị trí có thể làm thay đổi kết quả.

Ví dụ: muốn hiển thị từng khách hàng và số tiền order `paid`, kể cả khách hàng chưa mua:

```sql
SELECT
    c.id,
    c.full_name,
    o.id AS order_id,
    o.ordered_at
FROM customers AS c
LEFT JOIN orders AS o
    ON o.customer_id = c.id
   AND o.status = 'paid'
ORDER BY c.id, o.id;
```

---

## 6. Nhiều JOIN và sự nhân bản row

### 6.1. Vì sao một order xuất hiện nhiều lần?

Quan hệ `orders 1 ─── N order_items` có nghĩa là một order có nhiều dòng item. Khi join, mỗi item tạo ra một row kết quả.

```sql
SELECT
    o.id AS order_id,
    oi.id AS item_id,
    b.title,
    oi.quantity
FROM orders AS o
JOIN order_items AS oi ON oi.order_id = o.id
JOIN books AS b ON b.id = oi.book_id
WHERE o.id = 1;
```

Nếu order `1` có hai item, order `1` xuất hiện hai lần. Không dùng `DISTINCT` một cách máy móc để che hiện tượng này. Trước tiên phải xác định row kết quả đại diện cho cái gì:

- Một row cho mỗi order?
- Một row cho mỗi item?
- Một row cho mỗi khách hàng?

### 6.2. Muốn một row cho mỗi order

Khi cần tổng hợp, dùng `GROUP BY` và aggregate. Đây là nội dung chính của Tuần 7, nhưng cần biết mẫu query từ bây giờ:

```sql
SELECT
    o.id AS order_id,
    c.full_name AS customer_name,
    SUM(oi.quantity * oi.unit_price) AS order_total
FROM orders AS o
JOIN customers AS c ON c.id = o.customer_id
JOIN order_items AS oi ON oi.order_id = o.id
GROUP BY o.id, c.full_name
ORDER BY o.id;
```

Không được chọn `c.full_name` mà bỏ khỏi `GROUP BY` nếu column đó không nằm trong aggregate.

### 6.3. Tránh join thừa

Nếu chỉ cần tên khách hàng và ngày order, không cần join `order_items` hay `books`:

```sql
SELECT o.id, c.full_name, o.ordered_at
FROM orders AS o
JOIN customers AS c ON c.id = o.customer_id;
```

Mỗi `JOIN` làm query phải xử lý thêm quan hệ. Join đúng bảng cần thiết giúp kết quả dễ hiểu và thường giảm chi phí xử lý.

---

## 7. RIGHT JOIN và khi nào nên tránh

`RIGHT JOIN` giữ toàn bộ row của bảng bên phải:

```sql
SELECT
    b.title,
    oi.order_id,
    oi.quantity
FROM order_items AS oi
RIGHT JOIN books AS b
    ON b.id = oi.book_id;
```

Query này hiển thị cả sách chưa từng được bán; với sách đó, `order_id` và `quantity` là `NULL`.

Phần lớn `RIGHT JOIN` có thể đổi thành `LEFT JOIN` bằng cách đảo thứ tự bảng:

```sql
SELECT
    b.title,
    oi.order_id,
    oi.quantity
FROM books AS b
LEFT JOIN order_items AS oi
    ON oi.book_id = b.id;
```

Nên ưu tiên `LEFT JOIN` vì hướng đọc “bảng chính ở bên trái, bảng liên quan ở bên phải” thường dễ theo dõi hơn. Biết đọc `RIGHT JOIN` là đủ ở mức cơ bản.

---

## 8. CROSS JOIN

`CROSS JOIN` tạo tích Descartes: mỗi row của bảng trái ghép với mọi row của bảng phải.

```sql
SELECT
    c.full_name,
    a.name AS author_name
FROM customers AS c
CROSS JOIN authors AS a;
```

Nếu có 3 customers và 4 authors, kết quả có 12 row.

Một ứng dụng có chủ đích là tạo mọi tổ hợp ngày và trạng thái báo cáo:

```sql
SELECT
    d.report_date,
    s.status
FROM generate_series(
    DATE '2026-01-01',
    DATE '2026-01-03',
    INTERVAL '1 day'
) AS d(report_date)
CROSS JOIN (VALUES ('pending'), ('paid'), ('cancelled')) AS s(status);
```

Không dùng `CROSS JOIN` thay cho `JOIN` thông thường. Nếu quên điều kiện `ON` trong một query join, số row có thể tăng rất lớn.

---

## 9. Self join: một bảng join với chính nó

Self join dùng khi các row trong cùng một bảng có quan hệ với nhau. Ví dụ thêm cấu trúc tác giả có người hướng dẫn:

```sql
ALTER TABLE authors
ADD COLUMN mentor_id bigint REFERENCES authors(id);
```

Lấy tên tác giả và tên người hướng dẫn:

```sql
SELECT
    author.name AS author_name,
    mentor.name AS mentor_name
FROM authors AS author
LEFT JOIN authors AS mentor
    ON mentor.id = author.mentor_id;
```

Điểm bắt buộc của self join là alias. Nếu không đặt alias, PostgreSQL không thể phân biệt hai vai trò của cùng một bảng.

Một ví dụ phổ biến khác là bảng nhân viên:

```sql
SELECT
    employee.name AS employee_name,
    manager.name AS manager_name
FROM employees AS employee
LEFT JOIN employees AS manager
    ON manager.id = employee.manager_id;
```

---

## 10. Join nhiều bảng theo quan hệ khóa

### 10.1. Sơ đồ quan hệ

```text
authors
  │ 1
  └──────── N books

customers
  │ 1
  └──────── N orders
                  │ 1
                  └────── N order_items N ────── 1 books
```

### 10.2. Query doanh thu theo từng tác giả

```sql
SELECT
    a.id AS author_id,
    a.name AS author_name,
    COALESCE(SUM(oi.quantity * oi.unit_price), 0) AS revenue
FROM authors AS a
LEFT JOIN books AS b
    ON b.author_id = a.id
LEFT JOIN order_items AS oi
    ON oi.book_id = b.id
GROUP BY a.id, a.name
ORDER BY revenue DESC, a.id;
```

Tại sao dùng `LEFT JOIN`?

- Muốn giữ cả tác giả chưa có sách.
- Muốn giữ cả tác giả có sách nhưng chưa được bán.
- `COALESCE` biến tổng `NULL` thành `0` để báo cáo dễ đọc.

Nếu chỉ cần tác giả có doanh thu, có thể dùng `INNER JOIN`:

```sql
SELECT
    a.id,
    a.name,
    SUM(oi.quantity * oi.unit_price) AS revenue
FROM authors AS a
JOIN books AS b ON b.author_id = a.id
JOIN order_items AS oi ON oi.book_id = b.id
GROUP BY a.id, a.name
ORDER BY revenue DESC;
```

### 10.3. Query order hoàn chỉnh

```sql
SELECT
    o.id AS order_id,
    o.ordered_at,
    o.status,
    c.full_name AS customer_name,
    json_agg(
        json_build_object(
            'book_id', b.id,
            'title', b.title,
            'quantity', oi.quantity,
            'unit_price', oi.unit_price
        )
        ORDER BY oi.id
    ) AS items
FROM orders AS o
JOIN customers AS c ON c.id = o.customer_id
JOIN order_items AS oi ON oi.order_id = o.id
JOIN books AS b ON b.id = oi.book_id
GROUP BY o.id, o.ordered_at, o.status, c.full_name
ORDER BY o.id;
```

`json_agg` và `json_build_object` chỉ là một cách tạo kết quả phù hợp API. Điều quan trọng của Tuần 6 là hiểu chuỗi join; phần aggregate và JSON sẽ được luyện thêm ở các bài sau.

---

## 11. Những lỗi JOIN thường gặp

### Lỗi 1: Quên điều kiện `ON`

Sai hoặc nguy hiểm:

```sql
SELECT *
FROM orders AS o
JOIN customers AS c;
```

Với `JOIN` thông thường, PostgreSQL yêu cầu điều kiện phù hợp. Nếu cố ý muốn mọi tổ hợp, phải viết rõ `CROSS JOIN` để người đọc nhận ra ý định.

### Lỗi 2: Join nhầm column

```sql
-- Sai ý nghĩa nếu email không phải khóa liên kết
ON c.id = o.id
```

Hãy đọc DDL và xác định foreign key trước khi viết `JOIN`.

### Lỗi 3: Dùng `SELECT *` khi nhiều bảng có cùng tên column

```sql
SELECT *
FROM orders AS o
JOIN customers AS c ON c.id = o.customer_id;
```

Kết quả có thể có nhiều column khó phân biệt, gây lỗi khi ánh xạ sang object trong ứng dụng. Nên chọn column rõ ràng và alias khi cần:

```sql
SELECT
    o.id AS order_id,
    c.id AS customer_id,
    c.full_name AS customer_name
FROM orders AS o
JOIN customers AS c ON c.id = o.customer_id;
```

### Lỗi 4: `LEFT JOIN` bị biến thành `INNER JOIN`

Kiểm tra các điều kiện trong `WHERE` có tham chiếu tới bảng phải hay không. Nếu có, cân nhắc đưa điều kiện đó vào `ON`.

### Lỗi 5: Thấy kết quả trùng và thêm `DISTINCT` ngay

Row “trùng” có thể là kết quả đúng của quan hệ one-to-many. `DISTINCT` chỉ loại row giống hệt nhau trên toàn bộ các column được chọn; nó không biến nhiều item thành một order đúng nghĩa.

### Lỗi 6: Join một bảng one-to-many hai lần làm số row tăng ngoài dự kiến

Ví dụ một customer có 2 orders và 3 addresses. Nếu join cả hai quan hệ cùng lúc, có thể tạo 2 × 3 = 6 row cho customer đó. Khi cần báo cáo một row mỗi customer, hãy tổng hợp từng quan hệ trước hoặc dùng subquery/CTE phù hợp.

---

## 12. Quy trình viết và kiểm tra một JOIN query

Trước khi chạy query, đi qua các bước sau:

1. Viết rõ grain của kết quả: một row là order, item, customer hay author?
2. Liệt kê các bảng cần dùng.
3. Vẽ đường đi của khóa chính và khóa ngoại.
4. Chọn bảng chính đặt ở bên trái.
5. Chọn `INNER JOIN` nếu row không có quan hệ thì phải loại.
6. Chọn `LEFT JOIN` nếu phải giữ toàn bộ row của bảng chính.
7. Viết điều kiện `ON` bằng khóa chính/khóa ngoại.
8. Chỉ chọn các column cần thiết, đặt alias rõ ràng.
9. Kiểm tra số row trước và sau mỗi `JOIN`.
10. Thử các trường hợp rỗng: tác giả chưa có sách, khách hàng chưa có order, sách chưa bán.

Có thể kiểm tra từng bước bằng các query đếm:

```sql
SELECT COUNT(*) FROM orders;

SELECT COUNT(*)
FROM orders AS o
JOIN customers AS c ON c.id = o.customer_id;

SELECT COUNT(*)
FROM orders AS o
JOIN order_items AS oi ON oi.order_id = o.id;
```

Nếu số row tăng, hãy giải thích được vì sao tăng. Nếu không giải thích được, chưa nên đưa query vào application.

---

## 13. Bài tập thực hành

Sử dụng schema và dữ liệu mẫu ở đầu bài. Tự viết query trước khi xem gợi ý.

### Bài 1 — Order và khách hàng

Viết query lấy:

```text
order_id, ordered_at, status, customer_name, customer_email
```

Chỉ hiển thị order có khách hàng hợp lệ.

### Bài 2 — Chi tiết order

Viết query lấy:

```text
order_id, customer_name, book_title, quantity, unit_price
```

Mỗi row là một `order_item`.

### Bài 3 — Tác giả chưa có sách

Hiển thị `author_id` và `author_name` của tác giả chưa có book nào.

### Bài 4 — Khách hàng chưa từng mua

Hiển thị khách hàng chưa có bất kỳ order nào. Viết bằng `LEFT JOIN`, sau đó viết lại bằng `NOT EXISTS`.

### Bài 5 — Sách chưa từng được bán

Hiển thị tất cả sách chưa xuất hiện trong `order_items`.

### Bài 6 — Chỉ giữ khách hàng nhưng lọc order paid

Hiển thị toàn bộ khách hàng và order `paid` của họ. Khách hàng không có order `paid` vẫn phải xuất hiện.

### Bài 7 — Doanh thu tác giả

Hiển thị mỗi tác giả một row, gồm:

```text
author_name, revenue
```

Tác giả chưa có doanh thu phải có `revenue = 0`.

### Bài 8 — Phân tích lỗi

Giải thích vì sao hai query sau cho số khách hàng khác nhau:

```sql
-- Query A
SELECT c.id, c.full_name, o.id AS order_id
FROM customers AS c
LEFT JOIN orders AS o ON o.customer_id = c.id
WHERE o.status = 'paid';

-- Query B
SELECT c.id, c.full_name, o.id AS order_id
FROM customers AS c
LEFT JOIN orders AS o
    ON o.customer_id = c.id
   AND o.status = 'paid';
```

### Đáp án tham khảo ngắn

```sql
-- Bài 3
SELECT a.id AS author_id, a.name AS author_name
FROM authors AS a
LEFT JOIN books AS b ON b.author_id = a.id
WHERE b.id IS NULL;

-- Bài 5
SELECT b.id, b.title
FROM books AS b
LEFT JOIN order_items AS oi ON oi.book_id = b.id
WHERE oi.id IS NULL;

-- Bài 6
SELECT c.full_name, o.id AS paid_order_id
FROM customers AS c
LEFT JOIN orders AS o
    ON o.customer_id = c.id
   AND o.status = 'paid'
ORDER BY c.id, o.id;

-- Bài 7
SELECT
    a.name AS author_name,
    COALESCE(SUM(oi.quantity * oi.unit_price), 0) AS revenue
FROM authors AS a
LEFT JOIN books AS b ON b.author_id = a.id
LEFT JOIN order_items AS oi ON oi.book_id = b.id
GROUP BY a.id, a.name
ORDER BY revenue DESC, a.id;
```

---

## 14. Tiêu chí hoàn thành Tuần 6

Bạn hoàn thành bài học khi có thể:

- Giải thích khác nhau giữa `INNER JOIN` và `LEFT JOIN` bằng ví dụ có row thiếu.
- Viết query `orders` kèm tên `customers`.
- Viết query nối `orders`, `order_items` và `books`.
- Tìm tác giả chưa có sách và khách hàng chưa từng đặt hàng.
- Giải thích tại sao one-to-many làm một entity xuất hiện nhiều row.
- Nhận biết khi nào `DISTINCT` đang che một lỗi về grain của query.
- Nói rõ khác nhau giữa điều kiện trong `ON` và `WHERE` của `LEFT JOIN`.
- Đọc được `RIGHT JOIN`, hiểu mục đích của `CROSS JOIN` và viết được self join đơn giản.
- Tự kiểm tra query bằng cách đếm row và thử dữ liệu không có quan hệ.

## Tóm tắt cần nhớ

```text
INNER JOIN: chỉ giữ row có cặp ở hai bên.
LEFT JOIN: giữ toàn bộ bảng trái, bảng phải thiếu thì NULL.
RIGHT JOIN: giữ toàn bộ bảng phải; thường có thể đổi thành LEFT JOIN.
CROSS JOIN: mọi tổ hợp; số row tăng rất nhanh.
Self join: một bảng đóng hai vai trò, bắt buộc dùng alias.
ON: điều kiện ghép row.
WHERE: điều kiện lọc kết quả; với LEFT JOIN có thể loại row NULL.
One-to-many: row lặp lại có thể là đúng, cần xác định grain trước.
```

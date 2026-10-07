# Tuần 7: GROUP BY, aggregate và HAVING trong PostgreSQL

> Mục tiêu: gom nhiều row thành các nhóm có ý nghĩa, tính toán thống kê bằng aggregate function và lọc nhóm bằng `HAVING`.

Sau Tuần 6, một order có thể xuất hiện nhiều row vì một order có nhiều `order_items`. Tuần này học cách biến các row chi tiết thành báo cáo:

- Mỗi khách hàng có bao nhiêu order?
- Doanh thu theo tháng là bao nhiêu?
- Tác giả nào có ít nhất 2 cuốn sách?
- Customer nào đã chi hơn một ngưỡng?
- Giá trị order trung bình là bao nhiêu?

Ví dụ tổng hợp hoàn chỉnh:

```sql
SELECT
    c.id AS customer_id,
    c.full_name,
    COUNT(DISTINCT o.id) AS order_count,
    COALESCE(SUM(oi.quantity * oi.unit_price), 0) AS total_spent
FROM customers AS c
LEFT JOIN orders AS o ON o.customer_id = c.id
LEFT JOIN order_items AS oi ON oi.order_id = o.id
GROUP BY c.id, c.full_name
HAVING COALESCE(SUM(oi.quantity * oi.unit_price), 0) > 200000
ORDER BY total_spent DESC;
```

Query này dùng `LEFT JOIN` để giữ cả customer chưa mua, `COUNT` để đếm order, `SUM` để tính tiền, `GROUP BY` để tạo nhóm customer và `HAVING` để lọc nhóm sau khi tính tổng.

---

## 1. Schema và grain của kết quả

Bài này dùng cùng mô hình bookstore với [bài Tuần 6 về JOIN](06-join-ket-hop-nhieu-bang.md):

```text
authors      (id, name)
books       (id, author_id, title, price)
customers    (id, full_name, email)
orders       (id, customer_id, ordered_at, status)
order_items  (id, order_id, book_id, quantity, unit_price)
```

Quan hệ chính:

```text
Customer 1 ─── N Order 1 ─── N OrderItem N ─── 1 Book N ─── 1 Author
```

Trước khi viết aggregate, phải xác định **grain**: một row kết quả đại diện cho cái gì?

```text
Một row cho mỗi customer? order? order_item? author? month?
```

Nếu grain là một row cho mỗi order thì không thể dùng trực tiếp kết quả chi tiết một row cho mỗi item mà không gom lại.

---

## 2. Aggregate function

Aggregate function nhận nhiều row và trả về một giá trị tổng hợp.

| Hàm | Ý nghĩa |
|---|---|
| `COUNT` | Đếm row hoặc giá trị khác `NULL` |
| `SUM` | Tính tổng |
| `AVG` | Tính trung bình |
| `MIN` | Giá trị nhỏ nhất |
| `MAX` | Giá trị lớn nhất |

Không có `GROUP BY`, toàn bộ tập kết quả được xem là một nhóm:

```sql
SELECT
    COUNT(*) AS order_count,
    MIN(ordered_at) AS first_order_at,
    MAX(ordered_at) AS last_order_at
FROM orders;
```

Tính thống kê item:

```sql
SELECT
    SUM(quantity * unit_price) AS gross_revenue,
    AVG(quantity * unit_price) AS average_line_value,
    MIN(quantity * unit_price) AS smallest_line_value,
    MAX(quantity * unit_price) AS largest_line_value
FROM order_items;
```

`average_line_value` là trung bình một dòng item, không phải trung bình một order. Muốn tính average order value phải tính tổng từng order trước, rồi mới tính `AVG` trên các tổng đó.

---

## 3. COUNT: ba cách đếm cần phân biệt

### 3.1. `COUNT(*)`

Đếm tất cả row, kể cả row có column `NULL`:

```sql
SELECT COUNT(*) AS customer_count
FROM customers;
```

### 3.2. `COUNT(column)`

Chỉ đếm row có column khác `NULL`:

```sql
SELECT COUNT(mentor_id) AS authors_with_mentor
FROM authors;
```

### 3.3. `COUNT(DISTINCT column)`

Đếm số giá trị khác nhau, bỏ qua `NULL`:

```sql
SELECT COUNT(DISTINCT customer_id) AS buying_customer_count
FROM orders;
```

Sau khi join `orders` với `order_items`, một order có thể tạo nhiều row. Vì vậy:

```sql
SELECT
    COUNT(*) AS item_rows,
    COUNT(DISTINCT o.id) AS distinct_orders
FROM orders AS o
JOIN order_items AS oi ON oi.order_id = o.id;
```

`COUNT(*)` đếm item row; `COUNT(DISTINCT o.id)` đếm order.

### 3.4. COUNT với LEFT JOIN

```sql
SELECT
    c.id,
    c.full_name,
    COUNT(*) AS count_star,
    COUNT(o.id) AS count_order_id
FROM customers AS c
LEFT JOIN orders AS o ON o.customer_id = c.id
GROUP BY c.id, c.full_name;
```

Với customer chưa có order, `COUNT(*)` thường là `1` vì `LEFT JOIN` vẫn tạo một row có các column bên phải là `NULL`; `COUNT(o.id)` là `0`. Khi đếm bản ghi liên quan trong `LEFT JOIN`, thường dùng `COUNT(right_table.id)`.

---

## 4. GROUP BY

### 4.1. Nhóm theo một column

Đếm order theo customer:

```sql
SELECT
    customer_id,
    COUNT(*) AS order_count
FROM orders
GROUP BY customer_id
ORDER BY order_count DESC;
```

Mỗi giá trị `customer_id` tạo một group; aggregate được tính riêng trong group đó.

### 4.2. Nhóm theo nhiều column

Đếm order theo customer và status:

```sql
SELECT
    customer_id,
    status,
    COUNT(*) AS order_count
FROM orders
GROUP BY customer_id, status
ORDER BY customer_id, status;
```

Mỗi tổ hợp `(customer_id, status)` là một group riêng. Một customer có 3 order `paid` và 1 order `pending` sẽ có 2 row kết quả.

### 4.3. Column trong SELECT phải hợp lệ

Query sau sai:

```sql
SELECT customer_id, status, COUNT(*)
FROM orders
GROUP BY customer_id;
```

`status` không nằm trong `GROUP BY` và cũng không nằm trong aggregate. PostgreSQL không biết chọn status nào nếu customer có nhiều status.

Sửa:

```sql
SELECT customer_id, status, COUNT(*)
FROM orders
GROUP BY customer_id, status;
```

Quy tắc: column trong `SELECT` phải nằm trong `GROUP BY` hoặc được đưa vào aggregate function.

### 4.4. Group theo expression

Doanh thu theo tháng:

```sql
SELECT
    date_trunc('month', o.ordered_at)::date AS month,
    SUM(oi.quantity * oi.unit_price) AS revenue
FROM orders AS o
JOIN order_items AS oi ON oi.order_id = o.id
GROUP BY date_trunc('month', o.ordered_at)::date
ORDER BY month;
```

`date_trunc('month', ...)` đưa mọi thời điểm trong cùng tháng về cùng một giá trị nhóm.

### 4.5. Group entity theo ID và tên

Nên group theo ID cùng column hiển thị:

```sql
SELECT
    a.id,
    a.name,
    COUNT(b.id) AS book_count
FROM authors AS a
LEFT JOIN books AS b ON b.author_id = a.id
GROUP BY a.id, a.name;
```

Không nên group chỉ theo tên nếu hai entity khác nhau có thể trùng tên.

---

## 5. SUM, AVG, MIN và MAX

### 5.1. Tổng doanh thu theo customer

```sql
SELECT
    o.customer_id,
    SUM(oi.quantity * oi.unit_price) AS total_spent
FROM orders AS o
JOIN order_items AS oi ON oi.order_id = o.id
GROUP BY o.customer_id
ORDER BY total_spent DESC;
```

Để tính doanh thu lịch sử, dùng `order_items.unit_price`, không dùng `books.price` hiện tại vì giá sách có thể đã thay đổi.

### 5.2. Giá trị order trung bình

Không dùng trực tiếp `AVG(oi.quantity * oi.unit_price)` nếu mục tiêu là average order value. Cần hai bước:

```sql
WITH order_totals AS (
    SELECT
        o.id AS order_id,
        SUM(oi.quantity * oi.unit_price) AS order_total
    FROM orders AS o
    JOIN order_items AS oi ON oi.order_id = o.id
    GROUP BY o.id
)
SELECT AVG(order_total) AS average_order_value
FROM order_totals;
```

Phân biệt:

- Average line value: trung bình giá trị một `order_item`.
- Average order value: trung bình tổng tiền của mỗi `order`.

### 5.3. Thống kê giá sách theo tác giả

```sql
SELECT
    a.id,
    a.name,
    COUNT(b.id) AS book_count,
    MIN(b.price) AS cheapest_book,
    MAX(b.price) AS most_expensive_book,
    AVG(b.price) AS average_book_price
FROM authors AS a
LEFT JOIN books AS b ON b.author_id = a.id
GROUP BY a.id, a.name
ORDER BY average_book_price DESC NULLS LAST;
```

Tác giả chưa có sách có `COUNT(b.id) = 0`, còn `MIN`, `MAX`, `AVG` là `NULL` vì không có giá trị để tính. `NULL` không đồng nghĩa với giá bằng 0.

---

## 6. NULL và COALESCE trong aggregate

Aggregate thường bỏ qua `NULL`:

```sql
SELECT
    COUNT(*) AS all_rows,
    COUNT(price) AS rows_with_price,
    AVG(price) AS average_price
FROM books;
```

`COUNT(*)` đếm row; `COUNT(price)` và `AVG(price)` bỏ qua `price IS NULL`.

`SUM` trên tập rỗng có thể trả về `NULL`:

```sql
SELECT COALESCE(
    SUM(quantity * unit_price),
    0
) AS revenue
FROM order_items
WHERE order_id = -1;
```

Với báo cáo customer, dùng:

```sql
COALESCE(SUM(oi.quantity * oi.unit_price), 0)
```

để customer chưa mua có tổng tiền hiển thị là `0` thay vì `NULL`.

`COALESCE(SUM(expression), 0)` thường diễn đạt rõ hơn việc biến kết quả tổng cuối cùng thành 0. Khi cần xử lý từng input `NULL` trước khi cộng, mới dùng `SUM(COALESCE(expression, 0))`.

---

## 7. WHERE và HAVING

### 7.1. Thứ tự xử lý khái niệm

```text
FROM / JOIN
→ WHERE
→ GROUP BY
→ aggregate
→ HAVING
→ SELECT
→ ORDER BY
→ LIMIT
```

- `WHERE` lọc row trước khi gom nhóm.
- `HAVING` lọc group sau khi aggregate.

### 7.2. WHERE lọc row

Tính doanh thu chỉ của order đã thanh toán:

```sql
SELECT SUM(oi.quantity * oi.unit_price) AS paid_revenue
FROM orders AS o
JOIN order_items AS oi ON oi.order_id = o.id
WHERE o.status = 'paid';
```

`WHERE` loại row của order khác `paid` trước khi `SUM` chạy.

### 7.3. HAVING lọc group

Tìm customer có ít nhất hai order:

```sql
SELECT
    customer_id,
    COUNT(*) AS order_count
FROM orders
GROUP BY customer_id
HAVING COUNT(*) >= 2
ORDER BY order_count DESC;
```

Không viết aggregate trong `WHERE`:

```sql
-- Sai
SELECT customer_id, COUNT(*)
FROM orders
WHERE COUNT(*) >= 2
GROUP BY customer_id;
```

### 7.4. Kết hợp WHERE và HAVING

Customer có ít nhất 2 order `paid` và doanh thu paid lớn hơn 500.000:

```sql
SELECT
    o.customer_id,
    COUNT(DISTINCT o.id) AS paid_order_count,
    SUM(oi.quantity * oi.unit_price) AS paid_revenue
FROM orders AS o
JOIN order_items AS oi ON oi.order_id = o.id
WHERE o.status = 'paid'
GROUP BY o.customer_id
HAVING COUNT(DISTINCT o.id) >= 2
   AND SUM(oi.quantity * oi.unit_price) > 500000
ORDER BY paid_revenue DESC;
```

`WHERE` chọn các row hợp lệ; `HAVING` quyết định group nào được giữ lại.

### 7.5. HAVING không phải WHERE viết dài hơn

Nếu điều kiện không phụ thuộc aggregate, đặt ở `WHERE`:

```sql
SELECT customer_id, COUNT(*) AS paid_order_count
FROM orders
WHERE status = 'paid'
GROUP BY customer_id
HAVING COUNT(*) >= 2;
```

Không chuyển mọi điều kiện vào `HAVING`; làm vậy thường kém rõ ràng và có thể khiến database phải xử lý nhiều row hơn.

---

## 8. Aggregate có điều kiện bằng FILTER

PostgreSQL hỗ trợ `FILTER` để tính nhiều nhóm điều kiện trong cùng query:

```sql
SELECT
    customer_id,
    COUNT(*) AS total_orders,
    COUNT(*) FILTER (WHERE status = 'paid') AS paid_orders,
    COUNT(*) FILTER (WHERE status = 'pending') AS pending_orders,
    COUNT(*) FILTER (WHERE status = 'cancelled') AS cancelled_orders
FROM orders
GROUP BY customer_id
ORDER BY customer_id;
```

Tính nhiều loại doanh thu:

```sql
SELECT
    COALESCE(SUM(oi.quantity * oi.unit_price)
        FILTER (WHERE o.status = 'paid'), 0) AS paid_revenue,
    COALESCE(SUM(oi.quantity * oi.unit_price)
        FILTER (WHERE o.status = 'pending'), 0) AS pending_revenue
FROM orders AS o
JOIN order_items AS oi ON oi.order_id = o.id;
```

`FILTER` rất hữu ích cho báo cáo cột theo trạng thái mà không cần viết nhiều query riêng.

---

## 9. DISTINCT trong aggregate

Đếm số sách khác nhau đã bán:

```sql
SELECT COUNT(DISTINCT oi.book_id) AS sold_book_count
FROM order_items AS oi;
```

Đếm customer đã mua ít nhất một order:

```sql
SELECT COUNT(DISTINCT customer_id) AS buying_customer_count
FROM orders;
```

`DISTINCT` không phải cách sửa một `JOIN` sai. Khi có kết quả “trùng”, kiểm tra điều kiện `ON` và grain trước. Chỉ dùng `COUNT(DISTINCT o.id)` khi mục tiêu thật sự là đếm entity khác nhau sau quan hệ one-to-many.

---

## 10. Các báo cáo mẫu

### 10.1. Tác giả có ít nhất hai sách

```sql
SELECT
    a.id AS author_id,
    a.name AS author_name,
    COUNT(b.id) AS book_count
FROM authors AS a
JOIN books AS b ON b.author_id = a.id
GROUP BY a.id, a.name
HAVING COUNT(b.id) >= 2
ORDER BY book_count DESC, a.id;
```

### 10.2. Customer có tổng chi tiêu vượt ngưỡng

```sql
SELECT
    c.id AS customer_id,
    c.full_name,
    COUNT(DISTINCT o.id) AS paid_order_count,
    SUM(oi.quantity * oi.unit_price) AS total_spent
FROM customers AS c
JOIN orders AS o ON o.customer_id = c.id
JOIN order_items AS oi ON oi.order_id = o.id
WHERE o.status = 'paid'
GROUP BY c.id, c.full_name
HAVING SUM(oi.quantity * oi.unit_price) > 500000
ORDER BY total_spent DESC;
```

### 10.3. Số sách và số bản bán theo tác giả

```sql
SELECT
    a.id,
    a.name,
    COUNT(DISTINCT b.id) AS book_count,
    COUNT(DISTINCT oi.book_id) AS sold_book_count,
    COALESCE(SUM(oi.quantity), 0) AS sold_copy_count
FROM authors AS a
LEFT JOIN books AS b ON b.author_id = a.id
LEFT JOIN order_items AS oi ON oi.book_id = b.id
GROUP BY a.id, a.name
ORDER BY sold_copy_count DESC, a.id;
```

### 10.4. Top sách bán chạy

```sql
SELECT
    b.id AS book_id,
    b.title,
    SUM(oi.quantity) AS copies_sold,
    SUM(oi.quantity * oi.unit_price) AS revenue
FROM books AS b
JOIN order_items AS oi ON oi.book_id = b.id
JOIN orders AS o ON o.id = oi.order_id
WHERE o.status = 'paid'
GROUP BY b.id, b.title
ORDER BY copies_sold DESC, revenue DESC
LIMIT 10;
```

### 10.5. Báo cáo doanh thu theo tháng

```sql
SELECT
    date_trunc('month', o.ordered_at)::date AS month,
    COUNT(DISTINCT o.id) AS order_count,
    COUNT(DISTINCT o.customer_id) AS customer_count,
    SUM(oi.quantity * oi.unit_price) AS gross_revenue,
    SUM(oi.quantity * oi.unit_price)
        / COUNT(DISTINCT o.id) AS average_order_value
FROM orders AS o
JOIN order_items AS oi ON oi.order_id = o.id
WHERE o.status = 'paid'
GROUP BY date_trunc('month', o.ordered_at)::date
ORDER BY month;
```

`GROUP BY` chỉ tạo group cho tháng có dữ liệu. Muốn hiển thị cả tháng không có order, tạo dãy tháng rồi `LEFT JOIN` vào báo cáo:

```sql
WITH months AS (
    SELECT generate_series(
        DATE '2026-01-01', DATE '2026-06-01', INTERVAL '1 month'
    )::date AS month
), monthly_sales AS (
    SELECT
        date_trunc('month', o.ordered_at)::date AS month,
        COUNT(DISTINCT o.id) AS order_count,
        SUM(oi.quantity * oi.unit_price) AS revenue
    FROM orders AS o
    JOIN order_items AS oi ON oi.order_id = o.id
    WHERE o.status = 'paid'
    GROUP BY date_trunc('month', o.ordered_at)::date
)
SELECT
    m.month,
    COALESCE(ms.order_count, 0) AS order_count,
    COALESCE(ms.revenue, 0) AS revenue
FROM months AS m
LEFT JOIN monthly_sales AS ms ON ms.month = m.month
ORDER BY m.month;
```

---

## 11. Group theo NULL

`GROUP BY` gom các giá trị `NULL` vào cùng một group:

```sql
SELECT
    author_id,
    COUNT(*) AS book_count
FROM books
GROUP BY author_id
ORDER BY author_id NULLS LAST;
```

Nếu nhiều sách có `author_id IS NULL`, chúng nằm chung trong nhóm `NULL`. Khi hiển thị nhãn:

```sql
SELECT
    COALESCE(a.name, 'Chưa có tác giả') AS author_name,
    COUNT(b.id) AS book_count
FROM books AS b
LEFT JOIN authors AS a ON a.id = b.author_id
GROUP BY a.id, a.name
ORDER BY book_count DESC;
```

Đừng biến `NULL` thành 0 nếu hai giá trị có ý nghĩa khác nhau. Với `AVG(price)`, `NULL` thường có nghĩa “không có giá để tính”, không phải giá trung bình bằng 0.

---

## 12. Kiểm tra query aggregate

Khi kết quả sai, bỏ aggregate tạm thời để xem các row nguồn:

```sql
SELECT
    o.id AS order_id,
    o.customer_id,
    o.status,
    oi.id AS item_id,
    oi.quantity,
    oi.unit_price,
    oi.quantity * oi.unit_price AS line_total
FROM orders AS o
JOIN order_items AS oi ON oi.order_id = o.id
ORDER BY o.id, oi.id;
```

Sau đó kiểm tra từng cấp:

```sql
SELECT COUNT(*) FROM orders;
SELECT COUNT(*) FROM order_items;

SELECT order_id, COUNT(*) AS item_line_count
FROM order_items
GROUP BY order_id
ORDER BY order_id;

SELECT customer_id, COUNT(*) AS order_count
FROM orders
GROUP BY customer_id
ORDER BY customer_id;
```

Khi debug, luôn trả lời được:

1. Một row hiện tại đại diện cho entity nào?
2. `JOIN` có làm nhân row không?
3. `COUNT(*)` đang đếm row hay entity?
4. Có đang tính cả order chưa `paid` không?
5. `SUM` trả `NULL` hay cần hiển thị `0`?

---

## 13. Lỗi thường gặp

### Lỗi 1: Dùng WHERE để lọc aggregate

```sql
-- Sai
WHERE SUM(oi.quantity * oi.unit_price) > 500000
```

Sửa thành `HAVING SUM(...) > 500000`.

### Lỗi 2: Đếm item thay vì order

Sau khi join order với item, dùng `COUNT(*)` sẽ đếm item row. Dùng `COUNT(DISTINCT o.id)` nếu cần đếm order.

### Lỗi 3: `COUNT(*)` với LEFT JOIN

Muốn customer chưa mua có count bằng 0, dùng `COUNT(o.id)`, không dùng `COUNT(*)`.

### Lỗi 4: Bỏ column khỏi GROUP BY

Mọi column không aggregate trong `SELECT` phải nằm trong `GROUP BY`.

### Lỗi 5: Tính average sai grain

`AVG(item_total)` khác `AVG(order_total)`. Cần aggregate hai bước khi tính trung bình ở cấp cao hơn.

### Lỗi 6: Quên lọc status

Nếu định nghĩa doanh thu chỉ gồm order `paid`, phải lọc `WHERE o.status = 'paid'`.

### Lỗi 7: Group theo tên thay vì ID

Hai tác giả có thể trùng tên. Group theo ID và tên để không gộp nhầm entity.

### Lỗi 8: Dùng giá hiện tại cho giao dịch lịch sử

Tính order bằng `order_items.unit_price`, không lấy `books.price` hiện tại.

---

## 14. Bài tập thực hành

Trước mỗi bài, ghi rõ grain của một row kết quả.

### Bài 1 — Đếm order theo customer

Hiển thị `customer_id, order_count`, chỉ dùng bảng `orders`.

### Bài 2 — Customer và số order

Hiển thị `customer_id, full_name, order_count`, kể cả customer chưa mua; customer chưa mua phải có count bằng 0.

### Bài 3 — Đếm sách theo tác giả

Hiển thị tất cả tác giả, gồm cả người chưa có sách: `author_name, book_count`.

### Bài 4 — Tác giả có ít nhất hai sách

Chỉ hiển thị tác giả có `book_count >= 2`.

### Bài 5 — Tổng tiền từng order

Hiển thị mỗi order một row: `order_id, order_total`.

### Bài 6 — Customer chi trên 200.000

Chỉ tính order `paid`; hiển thị tên customer, số order paid và tổng chi tiêu.

### Bài 7 — Báo cáo theo tháng

Tạo các column `month, order_count, customer_count, gross_revenue, average_order_value`; chỉ tính order `paid`.

### Bài 8 — Top 3 sách bán chạy

Hiển thị tên sách, số bản bán và doanh thu.

### Bài 9 — Phân tích WHERE/HAVING

Viết query tìm customer có ít nhất 2 order `paid` và doanh thu paid lớn hơn 500.000; phải dùng cả `WHERE` và `HAVING` đúng mục đích.

### Đáp án tham khảo

```sql
-- Bài 2
SELECT c.id AS customer_id, c.full_name, COUNT(o.id) AS order_count
FROM customers AS c
LEFT JOIN orders AS o ON o.customer_id = c.id
GROUP BY c.id, c.full_name
ORDER BY c.id;

-- Bài 3
SELECT a.name AS author_name, COUNT(b.id) AS book_count
FROM authors AS a
LEFT JOIN books AS b ON b.author_id = a.id
GROUP BY a.id, a.name
ORDER BY book_count DESC, a.id;

-- Bài 4
SELECT a.name AS author_name, COUNT(b.id) AS book_count
FROM authors AS a
JOIN books AS b ON b.author_id = a.id
GROUP BY a.id, a.name
HAVING COUNT(b.id) >= 2
ORDER BY book_count DESC;

-- Bài 5
SELECT
    o.id AS order_id,
    SUM(oi.quantity * oi.unit_price) AS order_total
FROM orders AS o
JOIN order_items AS oi ON oi.order_id = o.id
GROUP BY o.id
ORDER BY o.id;

-- Bài 6
SELECT
    c.full_name,
    COUNT(DISTINCT o.id) AS paid_order_count,
    SUM(oi.quantity * oi.unit_price) AS total_spent
FROM customers AS c
JOIN orders AS o ON o.customer_id = c.id
JOIN order_items AS oi ON oi.order_id = o.id
WHERE o.status = 'paid'
GROUP BY c.id, c.full_name
HAVING SUM(oi.quantity * oi.unit_price) > 200000
ORDER BY total_spent DESC;

-- Bài 8
SELECT
    b.title,
    SUM(oi.quantity) AS copies_sold,
    SUM(oi.quantity * oi.unit_price) AS revenue
FROM books AS b
JOIN order_items AS oi ON oi.book_id = b.id
JOIN orders AS o ON o.id = oi.order_id
WHERE o.status = 'paid'
GROUP BY b.id, b.title
ORDER BY copies_sold DESC, revenue DESC
LIMIT 3;

-- Bài 9
SELECT
    o.customer_id,
    COUNT(DISTINCT o.id) AS paid_order_count,
    SUM(oi.quantity * oi.unit_price) AS paid_revenue
FROM orders AS o
JOIN order_items AS oi ON oi.order_id = o.id
WHERE o.status = 'paid'
GROUP BY o.customer_id
HAVING COUNT(DISTINCT o.id) >= 2
   AND SUM(oi.quantity * oi.unit_price) > 500000
ORDER BY paid_revenue DESC;
```

---

## 15. Tiêu chí hoàn thành Tuần 7

Bạn hoàn thành bài học khi có thể:

- Giải thích aggregate function và grain của kết quả.
- Phân biệt `COUNT(*)`, `COUNT(column)` và `COUNT(DISTINCT column)`.
- Viết `GROUP BY` theo một và nhiều column.
- Dùng đúng `COUNT`, `SUM`, `AVG`, `MIN`, `MAX`.
- Giải thích `COUNT(*)` khác `COUNT(right_table.id)` trong `LEFT JOIN`.
- Dùng `WHERE` để lọc row trước aggregate.
- Dùng `HAVING` để lọc group sau aggregate.
- Tính doanh thu theo order, customer, author và month.
- Phân biệt average item value và average order value.
- Xử lý `NULL` bằng `COALESCE` khi báo cáo cần số 0.
- Biết khi nào cần `COUNT(DISTINCT ...)` để tránh đếm trùng entity.

## Tóm tắt cần nhớ

```text
GROUP BY tạo group; aggregate tính riêng trong từng group.
COUNT(*) đếm row; COUNT(column) bỏ qua NULL.
COUNT(DISTINCT id) đếm entity khác nhau sau JOIN.
WHERE lọc row trước GROUP BY.
HAVING lọc group sau aggregate.
Một item row khác một order row; xác định grain trước khi COUNT/AVG.
SUM trên tập rỗng có thể là NULL; dùng COALESCE nếu cần 0.
Giá lịch sử nên lấy từ order_items.unit_price.
```

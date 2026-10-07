# Tuần 8: Subquery, CTE và window function cơ bản

> Mục tiêu: viết query nhiều bước bằng subquery và CTE, đồng thời dùng window function để xếp hạng, tính lũy kế và so sánh các row mà không làm mất dữ liệu chi tiết.

Bài này dùng schema bookstore của các tuần trước:

```text
authors      (id, name)
books        (id, author_id, title, price)
customers    (id, full_name, email)
orders       (id, customer_id, ordered_at, status)
order_items  (id, order_id, book_id, quantity, unit_price)
```

Trước mỗi query, hãy xác định **grain**: một row kết quả đại diện cho book, order, customer, item hay month? Đây là điều quyết định nên dùng aggregate, subquery hay window function.

---

## 1. Bài toán nhiều bước

Sau khi học `JOIN` và `GROUP BY`, bạn có thể tính tổng tiền từng order. Nhưng các yêu cầu sau cần thêm một bước:

- sách có giá cao hơn giá trung bình toàn bộ catalog;
- customer đã từng mua một book cụ thể;
- top 3 sách của mỗi tác giả;
- doanh thu tháng hiện tại so với tháng trước;
- doanh thu lũy kế theo thời gian.

Một aggregate thông thường làm giảm số row:

```sql
SELECT author_id, AVG(price) AS average_price
FROM books
GROUP BY author_id;
```

Một window function vẫn giữ từng book và bổ sung giá trung bình của tác giả:

```sql
SELECT
    id,
    title,
    author_id,
    price,
    AVG(price) OVER (PARTITION BY author_id) AS author_average_price
FROM books;
```

---

## 2. Scalar subquery

Subquery là query bên trong query khác. Scalar subquery trả về đúng một giá trị, thường dùng trong `WHERE`, `SELECT` hoặc `HAVING`.

### 2.1. So sánh với một giá trị aggregate

Tìm sách đắt hơn giá trung bình:

```sql
SELECT id, title, price
FROM books
WHERE price > (
    SELECT AVG(price)
    FROM books
)
ORDER BY price DESC;
```

Subquery phải trả về một giá trị. Nếu nó trả về nhiều row, phép so sánh `>` sẽ lỗi. Khi có nhiều giá trị, dùng `IN`, `EXISTS`, `ANY` hoặc `ALL`.

### 2.2. Subquery trong SELECT

```sql
SELECT
    b.title,
    b.price,
    (SELECT AVG(price) FROM books) AS overall_average_price
FROM books AS b
ORDER BY b.price DESC;
```

Mỗi row đều có cùng giá trị trung bình. Trường hợp này có thể viết bằng window function:

```sql
SELECT
    title,
    price,
    AVG(price) OVER () AS overall_average_price
FROM books;
```

### 2.3. Aggregate hai tầng

Tính tổng từng order rồi lấy các order lớn hơn 500.000:

```sql
SELECT order_id, order_total
FROM (
    SELECT
        o.id AS order_id,
        SUM(oi.quantity * oi.unit_price) AS order_total
    FROM orders AS o
    JOIN order_items AS oi ON oi.order_id = o.id
    GROUP BY o.id
) AS order_totals
WHERE order_total > 500000
ORDER BY order_total DESC;
```

Subquery trong `FROM` phải có alias. Nó tạo một tập dữ liệu trung gian có grain một row mỗi order.

---

## 3. IN, EXISTS và NOT EXISTS

### 3.1. IN

`IN` kiểm tra một giá trị có nằm trong tập giá trị của subquery không:

```sql
SELECT c.id, c.full_name
FROM customers AS c
WHERE c.id IN (
    SELECT o.customer_id
    FROM orders AS o
    WHERE o.status = 'paid'
)
ORDER BY c.id;
```

Query này lấy customer đã từng có order `paid`.

### 3.2. EXISTS

`EXISTS` chỉ kiểm tra subquery có ít nhất một row phù hợp:

```sql
SELECT c.id, c.full_name
FROM customers AS c
WHERE EXISTS (
    SELECT 1
    FROM orders AS o
    JOIN order_items AS oi ON oi.order_id = o.id
    WHERE o.customer_id = c.id
      AND oi.book_id = 1
)
ORDER BY c.id;
```

Đây là **correlated subquery** vì `o.customer_id = c.id` tham chiếu column từ query bên ngoài. Subquery được đánh giá theo customer hiện tại.

Dùng `EXISTS` khi câu hỏi là “có bản ghi liên quan không?”. Nếu cần lấy column từ bảng liên quan, dùng `JOIN`.

### 3.3. NOT EXISTS

Tìm sách chưa từng bán:

```sql
SELECT b.id, b.title
FROM books AS b
WHERE NOT EXISTS (
    SELECT 1
    FROM order_items AS oi
    WHERE oi.book_id = b.id
)
ORDER BY b.id;
```

Tìm tác giả chưa có sách:

```sql
SELECT a.id, a.name
FROM authors AS a
WHERE NOT EXISTS (
    SELECT 1
    FROM books AS b
    WHERE b.author_id = a.id
);
```

### 3.4. Cẩn thận với NOT IN và NULL

`NOT IN` có thể cho kết quả khó đoán nếu subquery trả về `NULL`, do SQL có logic ba trạng thái (`TRUE`, `FALSE`, `UNKNOWN`). Khi kiểm tra “không tồn tại bản ghi”, ưu tiên `NOT EXISTS`:

```sql
-- Cách rõ ràng để tìm book chưa có item
WHERE NOT EXISTS (
    SELECT 1
    FROM order_items AS oi
    WHERE oi.book_id = b.id
)
```

---

## 4. Correlated subquery theo nhóm

Tìm book có giá cao hơn giá trung bình của chính tác giả:

```sql
SELECT
    b.id,
    b.title,
    b.author_id,
    b.price
FROM books AS b
WHERE b.price > (
    SELECT AVG(other_book.price)
    FROM books AS other_book
    WHERE other_book.author_id = b.author_id
)
ORDER BY b.author_id, b.price DESC;
```

Điều kiện `other_book.author_id = b.author_id` là phần liên kết với row bên ngoài. Nếu bỏ điều kiện này, query sẽ so sánh với trung bình toàn bộ sách.

Cùng bài toán bằng CTE:

```sql
WITH author_average AS (
    SELECT author_id, AVG(price) AS average_price
    FROM books
    GROUP BY author_id
)
SELECT
    b.id,
    b.title,
    b.price,
    aa.average_price
FROM books AS b
JOIN author_average AS aa ON aa.author_id = b.author_id
WHERE b.price > aa.average_price;
```

Correlated subquery tiện cho logic nhỏ. CTE hoặc window function thường dễ mở rộng hơn khi cần thêm nhiều column thống kê.

---

## 5. CTE với WITH

CTE (Common Table Expression) đặt tên cho query trung gian:

```sql
WITH order_totals AS (
    SELECT
        o.id AS order_id,
        SUM(oi.quantity * oi.unit_price) AS order_total
    FROM orders AS o
    JOIN order_items AS oi ON oi.order_id = o.id
    GROUP BY o.id
)
SELECT order_id, order_total
FROM order_totals
WHERE order_total > 500000;
```

CTE giúp:

- chia query dài thành các bước có tên;
- kiểm tra riêng từng tập dữ liệu trung gian;
- làm rõ grain của mỗi bước;
- tái sử dụng kết quả trong phần query sau.

CTE chủ yếu cải thiện tính rõ ràng, không mặc định làm query nhanh hơn. Khi cần kết luận về hiệu năng, dùng `EXPLAIN`.

### 5.1. Nhiều CTE

Top customer theo doanh thu paid:

```sql
WITH paid_order_totals AS (
    SELECT
        o.id AS order_id,
        o.customer_id,
        SUM(oi.quantity * oi.unit_price) AS order_total
    FROM orders AS o
    JOIN order_items AS oi ON oi.order_id = o.id
    WHERE o.status = 'paid'
    GROUP BY o.id, o.customer_id
), customer_totals AS (
    SELECT
        customer_id,
        COUNT(*) AS paid_order_count,
        SUM(order_total) AS total_spent
    FROM paid_order_totals
    GROUP BY customer_id
)
SELECT
    c.full_name,
    ct.paid_order_count,
    ct.total_spent
FROM customer_totals AS ct
JOIN customers AS c ON c.id = ct.customer_id
ORDER BY ct.total_spent DESC
LIMIT 10;
```

Grain của từng bước:

```text
paid_order_totals: một row / order
customer_totals:   một row / customer
query cuối:        thêm tên và lấy top 10
```

### 5.2. CTE và giá trị trung bình của các nhóm

Doanh thu tháng cao hơn doanh thu tháng trung bình:

```sql
WITH monthly_revenue AS (
    SELECT
        date_trunc('month', o.ordered_at)::date AS month,
        SUM(oi.quantity * oi.unit_price) AS revenue
    FROM orders AS o
    JOIN order_items AS oi ON oi.order_id = o.id
    WHERE o.status = 'paid'
    GROUP BY date_trunc('month', o.ordered_at)::date
), summary AS (
    SELECT AVG(revenue) AS average_revenue
    FROM monthly_revenue
)
SELECT mr.month, mr.revenue, s.average_revenue
FROM monthly_revenue AS mr
CROSS JOIN summary AS s
WHERE mr.revenue > s.average_revenue
ORDER BY mr.revenue DESC;
```

`summary` chỉ có một row, nên `CROSS JOIN` tạo một ngưỡng chung cho từng tháng.

---

## 6. Window function

Window function tính trên một “cửa sổ” row liên quan đến row hiện tại nhưng không làm mất row chi tiết.

Cú pháp tổng quát:

```sql
function_name(...) OVER (
    [PARTITION BY ...]
    [ORDER BY ...]
    [frame clause]
)
```

- `OVER ()`: dùng toàn bộ tập kết quả làm một cửa sổ.
- `PARTITION BY`: chia thành các nhóm độc lập.
- `ORDER BY`: xác định thứ tự trong từng cửa sổ.

So sánh:

```sql
-- GROUP BY: một row / author
SELECT author_id, AVG(price)
FROM books
GROUP BY author_id;

-- Window: vẫn một row / book
SELECT
    id,
    title,
    author_id,
    price,
    AVG(price) OVER (PARTITION BY author_id) AS author_average
FROM books;
```

---

## 7. Window aggregate và PARTITION BY

### 7.1. Thống kê toàn bộ catalog

```sql
SELECT
    b.id,
    b.title,
    b.price,
    AVG(b.price) OVER () AS average_price,
    MIN(b.price) OVER () AS minimum_price,
    MAX(b.price) OVER () AS maximum_price
FROM books AS b
ORDER BY b.price DESC;
```

Mỗi book vẫn là một row.

### 7.2. Thống kê theo tác giả

```sql
SELECT
    b.id,
    b.title,
    b.author_id,
    b.price,
    AVG(b.price) OVER (
        PARTITION BY b.author_id
    ) AS author_average_price,
    b.price - AVG(b.price) OVER (
        PARTITION BY b.author_id
    ) AS difference_from_average
FROM books AS b
ORDER BY b.author_id, b.price DESC;
```

### 7.3. Tổng customer nhưng vẫn giữ item detail

```sql
SELECT
    o.customer_id,
    o.id AS order_id,
    oi.id AS item_id,
    oi.quantity * oi.unit_price AS line_total,
    SUM(oi.quantity * oi.unit_price) OVER (
        PARTITION BY o.customer_id
    ) AS customer_total_spent
FROM orders AS o
JOIN order_items AS oi ON oi.order_id = o.id
WHERE o.status = 'paid';
```

---

## 8. ROW_NUMBER, RANK và DENSE_RANK

### 8.1. ROW_NUMBER

Đánh số tuần tự, không giữ đồng hạng:

```sql
SELECT
    b.id,
    b.title,
    b.price,
    ROW_NUMBER() OVER (
        ORDER BY b.price DESC, b.id
    ) AS price_position
FROM books AS b;
```

Thêm `b.id` làm tie-breaker để thứ tự ổn định khi giá bằng nhau.

### 8.2. RANK và DENSE_RANK

```sql
SELECT
    b.title,
    b.price,
    RANK() OVER (ORDER BY b.price DESC) AS price_rank,
    DENSE_RANK() OVER (ORDER BY b.price DESC) AS dense_price_rank
FROM books AS b;
```

Với các giá `200, 200, 100`:

| Function | Kết quả |
|---|---|
| `ROW_NUMBER` | `1, 2, 3` |
| `RANK` | `1, 1, 3` |
| `DENSE_RANK` | `1, 1, 2` |

`RANK` tạo khoảng trống sau nhóm đồng hạng; `DENSE_RANK` thì không.

---

## 9. Top N trong từng nhóm

`LIMIT 3` chỉ lấy 3 row của toàn bộ kết quả, không lấy top 3 của mỗi tác giả. Dùng window function trong CTE:

```sql
WITH ranked_books AS (
    SELECT
        b.id,
        b.author_id,
        b.title,
        b.price,
        ROW_NUMBER() OVER (
            PARTITION BY b.author_id
            ORDER BY b.price DESC, b.id
        ) AS position_in_author
    FROM books AS b
)
SELECT
    id,
    author_id,
    title,
    price,
    position_in_author
FROM ranked_books
WHERE position_in_author <= 3
ORDER BY author_id, position_in_author;
```

Nếu muốn giữ tất cả sách đồng hạng ở vị trí thứ 3, dùng `RANK()` thay cho `ROW_NUMBER()`.

Không thể lọc trực tiếp alias window trong cùng cấp `WHERE`:

```sql
-- Sai
SELECT b.*, ROW_NUMBER() OVER (ORDER BY price DESC) AS position
FROM books AS b
WHERE position <= 3;
```

CTE hoặc subquery bên ngoài cần thiết vì `WHERE` được xử lý trước `SELECT`.

---

## 10. Lũy kế với SUM OVER

Aggregate doanh thu thành một row mỗi tháng trước, rồi tính lũy kế:

```sql
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
```

Với lũy kế theo customer:

```sql
WITH order_totals AS (
    SELECT
        o.id AS order_id,
        o.customer_id,
        o.ordered_at,
        SUM(oi.quantity * oi.unit_price) AS order_total
    FROM orders AS o
    JOIN order_items AS oi ON oi.order_id = o.id
    WHERE o.status = 'paid'
    GROUP BY o.id, o.customer_id, o.ordered_at
)
SELECT
    customer_id,
    order_id,
    ordered_at,
    order_total,
    SUM(order_total) OVER (
        PARTITION BY customer_id
        ORDER BY ordered_at, order_id
        ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
    ) AS cumulative_spent
FROM order_totals
ORDER BY customer_id, ordered_at, order_id;
```

Luôn thêm tie-breaker như `order_id` nếu nhiều row có cùng thời gian.

---

## 11. LAG và LEAD

`LAG` lấy giá trị ở row trước; `LEAD` lấy giá trị ở row sau theo thứ tự window.

So sánh doanh thu hiện tại với tháng trước:

```sql
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
    LAG(revenue) OVER (ORDER BY month) AS previous_revenue,
    revenue - LAG(revenue) OVER (ORDER BY month) AS change_from_previous
FROM monthly_revenue
ORDER BY month;
```

Row đầu tiên không có row trước nên `previous_revenue` là `NULL`, đây là kết quả đúng.

---

## 12. Chọn công cụ nào?

| Nhu cầu | Công cụ |
|---|---|
| Lấy column từ bảng liên quan | `JOIN` |
| Chỉ kiểm tra có row liên quan | `EXISTS` / `NOT EXISTS` |
| So sánh với một giá trị aggregate | Scalar subquery |
| Chia query thành các bước có tên | CTE |
| Top N trong từng nhóm | Window function + CTE |
| Xếp hạng | `ROW_NUMBER`, `RANK`, `DENSE_RANK` |
| Lũy kế hoặc so sánh row trước/sau | `SUM OVER`, `LAG`, `LEAD` |
| Giữ detail và thêm tổng nhóm | Window function |

Đây là hướng dẫn theo ý nghĩa query. Hiệu năng thực tế cần kiểm tra bằng `EXPLAIN` trên dữ liệu thật.

---

## 13. Lỗi thường gặp

### Lỗi 1: Scalar subquery trả nhiều row

Nếu subquery có thể trả nhiều giá trị, không dùng `=` hoặc `>` đơn độc; dùng `IN` hoặc `EXISTS`.

### Lỗi 2: `NOT IN` gặp NULL

Khi tìm “không có bản ghi liên quan”, ưu tiên `NOT EXISTS`.

### Lỗi 3: Correlated subquery thiếu điều kiện liên kết

Nếu quên điều kiện nối với row ngoài, bạn sẽ tính trên toàn bảng thay vì nhóm liên quan.

### Lỗi 4: Nghĩ CTE luôn nhanh hơn

CTE giúp tổ chức logic; không mặc định cải thiện hiệu năng.

### Lỗi 5: Dùng GROUP BY khi cần giữ detail

Muốn mỗi book vẫn xuất hiện cùng trung bình theo author, dùng window function.

### Lỗi 6: Dùng LIMIT cho top N từng nhóm

Dùng `PARTITION BY` cùng `ROW_NUMBER` hoặc `RANK`.

### Lỗi 7: Thứ hạng không ổn định

Thêm khóa duy nhất vào `ORDER BY`, ví dụ `ORDER BY price DESC, id`.

### Lỗi 8: Tính window trên sai grain

Muốn lũy kế theo tháng, aggregate item thành tháng trước rồi mới dùng `SUM OVER`.

---

## 14. Quy trình giải query nhiều bước

1. Viết yêu cầu bằng ngôn ngữ tự nhiên.
2. Xác định grain của output.
3. Lấy dữ liệu thô và kiểm tra các row nguồn.
4. Aggregate về grain trung gian nếu cần.
5. Đặt từng bước vào CTE có tên rõ ràng.
6. Dùng window function cho rank, lũy kế hoặc so sánh row.
7. Lọc window result ở query bên ngoài.
8. Thử dữ liệu rỗng, `NULL`, đồng hạng và cùng timestamp.
9. Dùng `EXPLAIN` khi query đủ lớn.

Ví dụ “top 3 sách mỗi tác giả”:

```text
Lấy books → chia theo author → xếp hạng → lọc rank <= 3
```

---

## 15. Bài tập thực hành

### Bài 1 — Sách đắt hơn trung bình

Dùng scalar subquery tìm sách có giá cao hơn giá trung bình toàn bộ `books`.

### Bài 2 — Customer đã mua book

Dùng `EXISTS` tìm customer đã từng mua book có `id = 1`.

### Bài 3 — Sách chưa từng bán

Dùng `NOT EXISTS` tìm sách chưa xuất hiện trong `order_items`.

### Bài 4 — Tổng từng order

Dùng CTE tính `order_id, order_total`, sau đó lấy order trên 500.000.

### Bài 5 — Top customer

Dùng CTE tính tổng chi tiêu order `paid` theo customer, rồi lấy top 3.

### Bài 6 — Giá cao hơn trung bình tác giả

Viết bằng correlated subquery, sau đó viết lại bằng CTE hoặc JOIN.

### Bài 7 — Top 2 sách mỗi tác giả

Dùng `ROW_NUMBER` và `PARTITION BY`.

### Bài 8 — Giữ sách đồng hạng

Dùng `RANK` để lấy tất cả sách có rank không lớn hơn 2 trong mỗi tác giả.

### Bài 9 — Doanh thu lũy kế

Tính doanh thu theo tháng, sau đó thêm `cumulative_revenue` bằng `SUM OVER`.

### Bài 10 — So sánh tháng trước

Dùng `LAG` hiển thị doanh thu tháng hiện tại, tháng trước và chênh lệch.

### Đáp án tham khảo

```sql
-- Bài 1
SELECT id, title, price
FROM books
WHERE price > (SELECT AVG(price) FROM books)
ORDER BY price DESC;

-- Bài 2
SELECT c.id, c.full_name
FROM customers AS c
WHERE EXISTS (
    SELECT 1
    FROM orders AS o
    JOIN order_items AS oi ON oi.order_id = o.id
    WHERE o.customer_id = c.id
      AND oi.book_id = 1
);

-- Bài 3
SELECT b.id, b.title
FROM books AS b
WHERE NOT EXISTS (
    SELECT 1
    FROM order_items AS oi
    WHERE oi.book_id = b.id
);

-- Bài 4
WITH order_totals AS (
    SELECT
        o.id AS order_id,
        SUM(oi.quantity * oi.unit_price) AS order_total
    FROM orders AS o
    JOIN order_items AS oi ON oi.order_id = o.id
    GROUP BY o.id
)
SELECT order_id, order_total
FROM order_totals
WHERE order_total > 500000
ORDER BY order_total DESC;

-- Bài 5
WITH customer_totals AS (
    SELECT
        o.customer_id,
        SUM(oi.quantity * oi.unit_price) AS total_spent
    FROM orders AS o
    JOIN order_items AS oi ON oi.order_id = o.id
    WHERE o.status = 'paid'
    GROUP BY o.customer_id
)
SELECT c.full_name, ct.total_spent
FROM customer_totals AS ct
JOIN customers AS c ON c.id = ct.customer_id
ORDER BY ct.total_spent DESC
LIMIT 3;

-- Bài 7
WITH ranked_books AS (
    SELECT
        b.*,
        ROW_NUMBER() OVER (
            PARTITION BY author_id
            ORDER BY price DESC, id
        ) AS position_in_author
    FROM books AS b
)
SELECT id, author_id, title, price, position_in_author
FROM ranked_books
WHERE position_in_author <= 2
ORDER BY author_id, position_in_author;

-- Bài 9
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

-- Bài 10
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
    LAG(revenue) OVER (ORDER BY month) AS previous_revenue,
    revenue - LAG(revenue) OVER (ORDER BY month) AS change_from_previous
FROM monthly_revenue
ORDER BY month;
```

---

## 16. Tiêu chí hoàn thành Tuần 8

Bạn hoàn thành bài học khi có thể:

- Viết scalar subquery để so sánh với aggregate.
- Phân biệt `IN`, `EXISTS` và `NOT EXISTS`.
- Giải thích rủi ro của `NOT IN` khi subquery có `NULL`.
- Viết correlated subquery và chỉ ra điều kiện liên kết.
- Dùng subquery trong `FROM` để aggregate nhiều tầng.
- Dùng CTE một bước và nhiều bước, nêu được grain của từng CTE.
- Giải thích window function khác `GROUP BY` thế nào.
- Dùng `PARTITION BY` để tính thống kê theo nhóm nhưng vẫn giữ detail.
- Dùng đúng `ROW_NUMBER`, `RANK`, `DENSE_RANK`.
- Lấy top N trong từng nhóm.
- Tính lũy kế bằng `SUM OVER`.
- Dùng `LAG` để so sánh row hiện tại với row trước.
- Biết vì sao phải lọc window result ở query bên ngoài.

## Tóm tắt cần nhớ

```text
Scalar subquery trả về một giá trị.
IN dùng với một tập giá trị; EXISTS kiểm tra có row liên quan.
NOT EXISTS thường rõ và an toàn hơn NOT IN khi có thể có NULL.
CTE đặt tên cho các bước trung gian.
GROUP BY làm giảm số row; window function giữ row chi tiết.
PARTITION BY chia cửa sổ thành các nhóm độc lập.
ROW_NUMBER không giữ đồng hạng; RANK có khoảng trống; DENSE_RANK không có.
Top N từng nhóm cần PARTITION BY, không dùng LIMIT đơn độc.
Aggregate về đúng grain trước rồi mới tính window ở cấp cao hơn.
```

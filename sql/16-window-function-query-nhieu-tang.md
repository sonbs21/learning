# Tuần 4 nâng cao: Window function và query nhiều tầng

> Mục tiêu: dùng window frame, ranking, offset function, running total, gaps-and-islands, top-N-per-group, recursive CTE và LATERAL JOIN để giải các query phân tích nhiều bước mà vẫn giữ đúng grain của dữ liệu.

Bạn đã biết ROW_NUMBER, RANK, PARTITION BY và SUM OVER ở mức cơ bản. Tuần này đi sâu vào những bài toán khó hơn:

~~~text
Window frame nào được dùng tại mỗi row?
Tại sao LAST_VALUE đôi khi trả về chính row hiện tại?
Làm sao tìm các ngày hoạt động liên tiếp?
Làm sao lấy top 3 record của từng nhóm?
Làm sao duyệt cây category?
Làm sao lấy bản ghi gần nhất cho mỗi customer?
~~~

Nguyên tắc xuyên suốt:

1. Xác định grain trước khi viết query.
2. Aggregate về grain phù hợp trước khi dùng window.
3. ORDER BY trong window phải có tie-breaker ổn định.
4. Lọc kết quả window ở query bên ngoài.
5. Recursive CTE phải có anchor, recursive member và điều kiện dừng.
6. LATERAL phù hợp khi subquery cần tham chiếu từng row bên ngoài.

---

## 1. Ôn lại window function

Window function tính trên một cửa sổ row liên quan tới row hiện tại nhưng không làm mất row chi tiết.

Cú pháp:

~~~sql
function_name(...) OVER (
    PARTITION BY ...
    ORDER BY ...
    frame_clause
)
~~~

Ví dụ:

~~~sql
SELECT
    b.id,
    b.title,
    b.author_id,
    b.price,
    AVG(b.price) OVER (
        PARTITION BY b.author_id
    ) AS author_average_price
FROM books AS b;
~~~

GROUP BY trả một row cho mỗi author. Window function vẫn trả một row cho mỗi book và thêm thống kê của author.

Các thành phần:

- OVER: biến aggregate hoặc function thành window function;
- PARTITION BY: chia window thành các nhóm độc lập;
- ORDER BY: xác định thứ tự trong window;
- frame: xác định phạm vi row được dùng tại row hiện tại.

---

## 2. ORDER BY và tie-breaker

Khi có giá trị bằng nhau, ORDER BY chưa chắc tạo thứ tự duy nhất.

Không ổn định:

~~~sql
ROW_NUMBER() OVER (
    ORDER BY price DESC
)
~~~

Ổn định hơn:

~~~sql
ROW_NUMBER() OVER (
    ORDER BY price DESC, id
)
~~~

id là tie-breaker duy nhất. Điều này quan trọng cho:

- pagination;
- top N;
- deduplication;
- chọn row đầu tiên;
- running total;
- LAG và LEAD.

Nếu không có thứ tự ổn định, hai lần chạy có thể đánh số các row bằng nhau theo thứ tự khác nhau.

---

## 3. ROW_NUMBER, RANK và DENSE_RANK

~~~sql
SELECT
    b.id,
    b.title,
    b.price,
    ROW_NUMBER() OVER (
        ORDER BY b.price DESC, b.id
    ) AS row_number_value,
    RANK() OVER (
        ORDER BY b.price DESC
    ) AS rank_value,
    DENSE_RANK() OVER (
        ORDER BY b.price DESC
    ) AS dense_rank_value
FROM books AS b;
~~~

Với giá:

~~~text
200, 200, 100
~~~

kết quả:

| Function | Kết quả |
|---|---|
| ROW_NUMBER | 1, 2, 3 |
| RANK | 1, 1, 3 |
| DENSE_RANK | 1, 1, 2 |

Chọn:

- ROW_NUMBER khi cần đúng N row;
- RANK khi muốn giữ đồng hạng và chấp nhận khoảng trống;
- DENSE_RANK khi muốn giữ đồng hạng nhưng thứ hạng liên tục.

---

## 4. Window frame: ROWS, RANGE và GROUPS

Window frame là phần row được xem xét tại row hiện tại.

Ví dụ running total rõ ràng:

~~~sql
SUM(revenue) OVER (
    ORDER BY month
    ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
)
~~~

Các thành phần:

- UNBOUNDED PRECEDING: row đầu tiên của partition;
- CURRENT ROW: row hiện tại;
- n PRECEDING: n row hoặc nhóm trước;
- n FOLLOWING: n row hoặc nhóm sau;
- UNBOUNDED FOLLOWING: row cuối.

### ROWS

ROWS tính theo số row vật lý trong thứ tự đã cho.

~~~sql
SUM(amount) OVER (
    ORDER BY occurred_at, id
    ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
)
~~~

Dùng ROWS khi muốn mỗi row tiến thêm một bước, kể cả khi order value trùng.

### RANGE

RANGE dựa trên giá trị ORDER BY và peer rows. Các row có cùng giá trị order có thể nằm cùng frame.

~~~sql
SUM(amount) OVER (
    ORDER BY occurred_at
    RANGE BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
)
~~~

Khi occurred_at trùng, kết quả có thể cộng cả nhóm peer cùng lúc.

### GROUPS

GROUPS đếm peer group thay vì row riêng lẻ. Dùng khi logic phân tích quan tâm các nhóm có cùng giá trị ORDER BY.

Ở mức thực hành, ưu tiên ROWS với tie-breaker khi viết running total theo từng event.

---

## 5. Running total

### 5.1. Lũy kế theo tháng

Aggregate trước về một row mỗi tháng:

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

### 5.2. Lũy kế trong từng customer

~~~sql
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
    ) AS customer_cumulative_spent
FROM order_totals
ORDER BY customer_id, ordered_at, order_id;
~~~

Aggregate trước giúp window chạy trên grain một row mỗi order thay vì một row mỗi item.

---

## 6. Moving average và frame hữu hạn

Moving average 7 row:

~~~sql
SELECT
    month,
    revenue,
    AVG(revenue) OVER (
        ORDER BY month
        ROWS BETWEEN 6 PRECEDING AND CURRENT ROW
    ) AS moving_average_7_rows
FROM monthly_revenue
ORDER BY month;
~~~

Nếu dữ liệu có tháng bị thiếu, 7 row không nhất thiết là 7 ngày hoặc 7 tháng lịch. Muốn window theo thời gian thực, cần tạo calendar đầy đủ bằng generate_series rồi LEFT JOIN dữ liệu.

Ví dụ tạo tháng:

~~~sql
WITH months AS (
    SELECT generate_series(
        DATE '2026-01-01',
        DATE '2026-12-01',
        INTERVAL '1 month'
    )::date AS month
)
SELECT month
FROM months
ORDER BY month;
~~~

Sau đó join monthly_revenue và COALESCE revenue về 0 trước khi tính window.

---

## 7. LAG, LEAD và so sánh row

LAG lấy giá trị row trước; LEAD lấy row sau:

~~~sql
SELECT
    month,
    revenue,
    LAG(revenue) OVER (ORDER BY month) AS previous_revenue,
    LEAD(revenue) OVER (ORDER BY month) AS next_revenue
FROM monthly_revenue
ORDER BY month;
~~~

Tính chênh lệch:

~~~sql
SELECT
    month,
    revenue,
    revenue - LAG(revenue) OVER (ORDER BY month)
        AS change_from_previous
FROM monthly_revenue
ORDER BY month;
~~~

Row đầu tiên không có previous nên LAG trả NULL. Đó là kết quả đúng.

Có thể truyền offset:

~~~sql
LAG(revenue, 12) OVER (ORDER BY month)
~~~

dùng để so sánh cùng kỳ năm trước nếu dữ liệu có đủ tháng.

Default value:

~~~sql
LAG(revenue, 1, 0) OVER (ORDER BY month)
~~~

Nhưng thay NULL bằng 0 chỉ đúng nếu nghiệp vụ coi “không có row trước” là 0. Đừng biến thiếu dữ liệu thành 0 một cách tự động.

---

## 8. FIRST_VALUE và LAST_VALUE

FIRST_VALUE lấy giá trị đầu tiên trong frame:

~~~sql
SELECT
    customer_id,
    order_id,
    ordered_at,
    FIRST_VALUE(order_id) OVER (
        PARTITION BY customer_id
        ORDER BY ordered_at, order_id
    ) AS first_order_id
FROM orders;
~~~

LAST_VALUE dễ gây bất ngờ vì default frame thường kết thúc ở CURRENT ROW:

~~~sql
SELECT
    customer_id,
    order_id,
    ordered_at,
    LAST_VALUE(order_id) OVER (
        PARTITION BY customer_id
        ORDER BY ordered_at, order_id
    ) AS last_value_default
FROM orders;
~~~

Ở nhiều trường hợp, last_value_default là chính order_id hiện tại.

Muốn lấy row cuối toàn partition, mở rộng frame:

~~~sql
LAST_VALUE(order_id) OVER (
    PARTITION BY customer_id
    ORDER BY ordered_at, order_id
    ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING
) AS last_order_id
~~~

Đây là lý do phải hiểu frame thay vì chỉ nhớ tên function.

---

## 9. Top N per group

LIMIT chỉ áp dụng toàn bộ result:

~~~sql
SELECT *
FROM books
ORDER BY price DESC
LIMIT 3;
~~~

Top 3 toàn catalog khác top 3 của từng author.

Dùng ROW_NUMBER:

~~~sql
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
~~~

Giữ đồng hạng ở vị trí thứ 3:

~~~sql
WITH ranked_books AS (
    SELECT
        b.*,
        RANK() OVER (
            PARTITION BY b.author_id
            ORDER BY b.price DESC
        ) AS price_rank
    FROM books AS b
)
SELECT *
FROM ranked_books
WHERE price_rank <= 3;
~~~

Nếu dùng RANK, kết quả có thể có hơn 3 row trong một group do đồng hạng.

---

## 10. Gaps and islands là gì?

Gaps and islands là pattern tìm các đoạn liên tiếp hoặc các nhóm liên tục trong dữ liệu.

Ví dụ:

~~~text
user_id | activity_date
1       | 2026-01-01
1       | 2026-01-02
1       | 2026-01-03
1       | 2026-01-07
1       | 2026-01-08
~~~

Có hai island:

~~~text
2026-01-01 → 2026-01-03
2026-01-07 → 2026-01-08
~~~

Pattern thường dùng:

1. deduplicate về một row mỗi user mỗi ngày;
2. đánh số row theo ngày;
3. trừ row_number khỏi ngày;
4. các ngày liên tiếp có cùng group key;
5. group lại để lấy start, end, count.

Query:

~~~sql
WITH distinct_days AS (
    SELECT DISTINCT
        user_id,
        occurred_at::date AS activity_date
    FROM user_events
), numbered AS (
    SELECT
        user_id,
        activity_date,
        activity_date
            - ROW_NUMBER() OVER (
                PARTITION BY user_id
                ORDER BY activity_date
              )::integer AS island_key
    FROM distinct_days
)
SELECT
    user_id,
    MIN(activity_date) AS started_on,
    MAX(activity_date) AS ended_on,
    COUNT(*) AS active_days
FROM numbered
GROUP BY user_id, island_key
ORDER BY user_id, started_on;
~~~

Vì ngày liên tiếp tăng một đơn vị cùng với row_number, phép trừ tạo cùng island_key.

---

## 11. Gaps and islands với trạng thái

Không chỉ ngày liên tiếp; có thể gom các đoạn cùng status.

Ví dụ events có status:

~~~text
id | status  | occurred_at
1  | online  | ...
2  | online  | ...
3  | offline | ...
4  | offline | ...
5  | online  | ...
~~~

Dùng LAG để đánh dấu khi status thay đổi:

~~~sql
WITH marked AS (
    SELECT
        id,
        status,
        occurred_at,
        CASE
            WHEN status = LAG(status) OVER (
                ORDER BY occurred_at, id
            )
            THEN 0
            ELSE 1
        END AS new_group
    FROM status_events
), grouped AS (
    SELECT
        id,
        status,
        occurred_at,
        SUM(new_group) OVER (
            ORDER BY occurred_at, id
            ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
        ) AS group_id
    FROM marked
)
SELECT
    group_id,
    status,
    MIN(occurred_at) AS started_at,
    MAX(occurred_at) AS ended_at,
    COUNT(*) AS event_count
FROM grouped
GROUP BY group_id, status
ORDER BY group_id;
~~~

Đây là query nhiều tầng điển hình: LAG → running sum → GROUP BY.

---

## 12. Query nhiều tầng với CTE

CTE giúp mỗi bước có một grain và tên rõ ràng.

Ví dụ top customer theo doanh thu, sau đó xếp hạng:

~~~sql
WITH order_totals AS (
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
        COUNT(*) AS order_count,
        SUM(order_total) AS total_spent
    FROM order_totals
    GROUP BY customer_id
), ranked_customers AS (
    SELECT
        customer_id,
        order_count,
        total_spent,
        RANK() OVER (
            ORDER BY total_spent DESC
        ) AS spending_rank
    FROM customer_totals
)
SELECT
    rc.spending_rank,
    c.full_name,
    rc.order_count,
    rc.total_spent
FROM ranked_customers AS rc
JOIN customers AS c ON c.id = rc.customer_id
WHERE rc.spending_rank <= 10
ORDER BY rc.spending_rank, c.id;
~~~

Grain:

~~~text
order_totals: một row / order
customer_totals: một row / customer
ranked_customers: một row / customer có rank
result: thêm tên và lọc top
~~~

CTE không tự động nhanh hơn subquery. Dùng EXPLAIN khi query lớn.

---

## 13. Recursive CTE

Recursive CTE dùng để duyệt dữ liệu có quan hệ tự tham chiếu như cây category hoặc employee hierarchy.

Cú pháp gồm:

1. WITH RECURSIVE;
2. anchor member;
3. UNION ALL;
4. recursive member;
5. điều kiện join tới row con;
6. điều kiện dừng tự nhiên khi không còn row.

Schema category:

~~~sql
CREATE TABLE categories (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    parent_id bigint REFERENCES categories(id),
    name text NOT NULL
);
~~~

Duyệt từ root:

~~~sql
WITH RECURSIVE category_tree AS (
    SELECT
        id,
        parent_id,
        name,
        0 AS depth,
        name::text AS path
    FROM categories
    WHERE parent_id IS NULL

    UNION ALL

    SELECT
        c.id,
        c.parent_id,
        c.name,
        ct.depth + 1,
        ct.path || ' > ' || c.name
    FROM categories AS c
    JOIN category_tree AS ct
        ON c.parent_id = ct.id
)
SELECT
    id,
    parent_id,
    name,
    depth,
    path
FROM category_tree
ORDER BY path;
~~~

Anchor lấy root. Recursive member lấy child của row đã tìm thấy.

---

## 14. Recursive CTE với cycle protection

Dữ liệu cây lỗi có thể chứa cycle:

~~~text
A → B → C → A
~~~

Nếu không bảo vệ, recursive query có thể chạy mãi hoặc chạm giới hạn tài nguyên.

Lưu path ID:

~~~sql
WITH RECURSIVE category_tree AS (
    SELECT
        id,
        parent_id,
        name,
        0 AS depth,
        ARRAY[id] AS visited_ids
    FROM categories
    WHERE parent_id IS NULL

    UNION ALL

    SELECT
        c.id,
        c.parent_id,
        c.name,
        ct.depth + 1,
        ct.visited_ids || c.id
    FROM categories AS c
    JOIN category_tree AS ct
        ON c.parent_id = ct.id
    WHERE NOT c.id = ANY(ct.visited_ids)
)
SELECT *
FROM category_tree;
~~~

Ngoài query, schema nên ngăn cycle bằng validation ở application, trigger, hoặc thiết kế dữ liệu phù hợp. Recursive query không thay thế integrity rule.

---

## 15. Tìm descendants và ancestors

Descendants của một category:

~~~sql
WITH RECURSIVE descendants AS (
    SELECT id, parent_id, name, 0 AS depth
    FROM categories
    WHERE id = 10

    UNION ALL

    SELECT c.id, c.parent_id, c.name, d.depth + 1
    FROM categories AS c
    JOIN descendants AS d ON c.parent_id = d.id
)
SELECT *
FROM descendants
WHERE depth > 0
ORDER BY depth, id;
~~~

Ancestors của một category:

~~~sql
WITH RECURSIVE ancestors AS (
    SELECT id, parent_id, name, 0 AS distance
    FROM categories
    WHERE id = 42

    UNION ALL

    SELECT c.id, c.parent_id, c.name, a.distance + 1
    FROM categories AS c
    JOIN ancestors AS a ON c.id = a.parent_id
)
SELECT *
FROM ancestors
ORDER BY distance;
~~~

Cùng một table nhưng hướng join khác nhau tạo hai bài toán khác nhau.

---

## 16. LATERAL JOIN

LATERAL cho phép subquery trong FROM tham chiếu column của row phía trước.

Không có LATERAL, subquery trong FROM không thể tự động tham chiếu từng customer theo cách này.

Lấy order mới nhất mỗi customer:

~~~sql
SELECT
    c.id,
    c.full_name,
    latest_order.id AS order_id,
    latest_order.ordered_at,
    latest_order.status
FROM customers AS c
LEFT JOIN LATERAL (
    SELECT
        o.id,
        o.ordered_at,
        o.status
    FROM orders AS o
    WHERE o.customer_id = c.id
    ORDER BY o.ordered_at DESC, o.id DESC
    LIMIT 1
) AS latest_order ON true;
~~~

Ý nghĩa:

1. lấy một customer;
2. chạy subquery orders với customer_id hiện tại;
3. sort và lấy một order;
4. chuyển sang customer tiếp theo.

Index phù hợp:

~~~sql
CREATE INDEX orders_customer_created_idx
ON orders (customer_id, ordered_at DESC, id DESC);
~~~

---

## 17. LATERAL và top N mỗi group

Top 3 order mới nhất mỗi customer:

~~~sql
SELECT
    c.id AS customer_id,
    c.full_name,
    recent_orders.order_id,
    recent_orders.ordered_at,
    recent_orders.status
FROM customers AS c
LEFT JOIN LATERAL (
    SELECT
        o.id AS order_id,
        o.ordered_at,
        o.status
    FROM orders AS o
    WHERE o.customer_id = c.id
    ORDER BY o.ordered_at DESC, o.id DESC
    LIMIT 3
) AS recent_orders ON true
ORDER BY c.id, recent_orders.ordered_at DESC;
~~~

So với ROW_NUMBER:

- LATERAL phù hợp khi outer group nhỏ và có index lookup tốt;
- ROW_NUMBER phù hợp khi đã cần xử lý toàn bộ dataset hoặc muốn một pipeline aggregate/window;
- EXPLAIN quyết định cách nào tốt hơn trên dữ liệu thật.

---

## 18. LATERAL lấy bản ghi gần nhất theo thời điểm

Giả sử customer có events và cần trạng thái gần nhất trước mỗi order:

~~~sql
SELECT
    o.id AS order_id,
    o.customer_id,
    o.ordered_at,
    previous_event.event_type,
    previous_event.occurred_at
FROM orders AS o
LEFT JOIN LATERAL (
    SELECT
        e.event_type,
        e.occurred_at
    FROM customer_events AS e
    WHERE e.customer_id = o.customer_id
      AND e.occurred_at <= o.ordered_at
    ORDER BY e.occurred_at DESC, e.id DESC
    LIMIT 1
) AS previous_event ON true;
~~~

Index:

~~~sql
CREATE INDEX customer_events_customer_time_idx
ON customer_events (customer_id, occurred_at DESC, id DESC);
~~~

LATERAL tránh phải tạo toàn bộ cặp order-event rồi mới lọc bằng window trong một số workload.

---

## 19. LATERAL và jsonb/array

LATERAL cũng hữu ích để mở rộng array hoặc jsonb theo từng row:

~~~sql
SELECT
    b.id,
    tag
FROM books AS b
CROSS JOIN LATERAL jsonb_array_elements_text(
    b.metadata -> 'tags'
) AS tags(tag);
~~~

Mỗi book có thể tạo nhiều row tag. Hãy nhớ grain thay đổi từ một row mỗi book thành một row mỗi book-tag.

---

## 20. Chọn window, LATERAL hay subquery

| Bài toán | Công cụ thường phù hợp |
|---|---|
| Xếp hạng toàn dataset | Window function |
| Top N mỗi group trên dataset lớn | Window function hoặc LATERAL |
| Lấy một row gần nhất mỗi outer row | LATERAL |
| Kiểm tra có row liên quan | EXISTS |
| Tính nhiều bước có tên | CTE |
| Duyệt cây | Recursive CTE |
| Gom đoạn liên tiếp | Window + CTE |
| Aggregate nhiều cấp | CTE/subquery rồi window |

Không dùng một pattern vì nó phổ biến. So sánh grain, selectivity, index và EXPLAIN.

---

## 21. Lỗi thường gặp

### Lỗi 1: Quên frame của LAST_VALUE

LAST_VALUE mặc định có thể trả current row thay vì row cuối partition. Mở rộng frame tới UNBOUNDED FOLLOWING.

### Lỗi 2: Không có tie-breaker

ROW_NUMBER, pagination và LAG có thể không ổn định nếu ORDER BY không duy nhất.

### Lỗi 3: Lọc window trong cùng WHERE

Window được tính sau WHERE cùng cấp. Dùng CTE hoặc subquery bên ngoài.

### Lỗi 4: Tính window trước khi aggregate về đúng grain

Lũy kế theo tháng cần một row mỗi tháng trước khi SUM OVER. Nếu chạy trên item row, kết quả là lũy kế theo item.

### Lỗi 5: Recursive CTE không có điều kiện dừng

Thiếu join đúng hoặc cycle protection có thể làm query chạy rất lâu.

### Lỗi 6: Dùng LATERAL không có index

LATERAL có thể chạy subquery một lần cho mỗi outer row. Nếu inner lookup không có index, chi phí tăng rất nhanh.

### Lỗi 7: Dùng LIMIT cho top N từng group

LIMIT toàn cục không tạo top N cho mỗi group.

### Lỗi 8: Dùng DISTINCT để che nhân row

Kiểm tra lại grain và relationship trước khi thêm DISTINCT.

---

## 22. Lab tổng hợp

Tạo dữ liệu order và events đủ lớn, sau đó thực hành:

1. Top 3 books theo category bằng ROW_NUMBER.
2. Top 3 orders theo customer bằng LATERAL.
3. So sánh EXPLAIN hai cách.
4. Running revenue theo month với explicit ROWS frame.
5. LAG revenue so với tháng trước.
6. Gaps and islands cho ngày hoạt động của customer.
7. Recursive category tree có depth và path.
8. Lấy event gần nhất trước order bằng LATERAL.
9. Kiểm tra index hỗ trợ LATERAL.
10. Ghi execution time, loops và buffers.

Báo cáo phải ghi:

~~~text
Grain của input
Grain của mỗi CTE
Grain của output
Index được dùng
Estimated rows và actual rows
Plan node chính
Tác dụng phụ
~~~

---

## 23. Bài tập thực hành

### Bài 1 — Window frame

Tạo dữ liệu có cùng timestamp và so sánh ROWS với RANGE trong running total.

### Bài 2 — Last value

Viết query lấy order đầu tiên và order cuối cùng của mỗi customer. Giải thích frame của LAST_VALUE.

### Bài 3 — Top N

Lấy top 3 sách đắt nhất mỗi category, giữ hoặc không giữ đồng hạng bằng cách đổi ROW_NUMBER và RANK.

### Bài 4 — Moving average

Tính moving average 7 ngày. Tạo calendar đầy đủ để phân biệt 7 row với 7 ngày lịch.

### Bài 5 — Gaps and islands

Tìm các chuỗi ngày customer hoạt động liên tiếp dài ít nhất 3 ngày.

### Bài 6 — Status islands

Gom các đoạn liên tục có cùng status và lấy thời điểm bắt đầu/kết thúc mỗi đoạn.

### Bài 7 — Recursive category

Duyệt toàn bộ descendants của category, có depth, path và cycle protection.

### Bài 8 — LATERAL latest row

Lấy payment mới nhất của mỗi order bằng LATERAL và index phù hợp.

### Bài 9 — LATERAL top N

Lấy 3 order mới nhất mỗi customer và so sánh với ROW_NUMBER.

### Bài 10 — Query nhiều tầng

Tính order total, customer total, rank customer và chỉ lấy top 10.

---

## 24. Tiêu chí hoàn thành Tuần 4

Bạn hoàn thành bài học khi có thể:

- Giải thích window frame và khác nhau giữa ROWS, RANGE và GROUPS.
- Dùng ROW_NUMBER, RANK và DENSE_RANK đúng mục tiêu.
- Dùng LAG, LEAD, FIRST_VALUE và LAST_VALUE với frame phù hợp.
- Tính running total và moving average.
- Giải bài toán gaps and islands.
- Lấy top N trong từng group.
- Viết CTE nhiều tầng và xác định grain mỗi tầng.
- Dùng recursive CTE để duyệt tree.
- Bảo vệ recursive query khỏi cycle.
- Dùng LATERAL để lấy latest row hoặc top N cho mỗi outer row.
- Chọn giữa window function, CTE, subquery và LATERAL bằng dữ liệu và EXPLAIN.
- Nhận biết các lỗi do thiếu tie-breaker, sai frame, sai grain và thiếu index.

## Tóm tắt cần nhớ

~~~text
Window function giữ detail; GROUP BY làm giảm số row.
Frame quyết định row nào được dùng tại current row.
ROWS đi theo row; RANGE đi theo peer value; GROUPS đi theo peer group.
LAST_VALUE cần chú ý frame tới UNBOUNDED FOLLOWING.
Top N mỗi group cần PARTITION BY hoặc LATERAL, không dùng LIMIT đơn độc.
Gaps and islands thường dùng row_number hoặc LAG rồi group lại.
Recursive CTE gồm anchor, UNION ALL, recursive member và điều kiện dừng.
LATERAL cho phép subquery tham chiếu từng row bên ngoài.
LATERAL cần index tốt nếu chạy nhiều lần.
Query nhiều tầng phải xác định grain của từng CTE.
~~~

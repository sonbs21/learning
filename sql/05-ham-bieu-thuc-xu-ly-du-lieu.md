# Tuần 5 cơ bản: Hàm, biểu thức và xử lý dữ liệu trong PostgreSQL

> Mục tiêu: dùng hàm chuỗi, hàm số, hàm ngày giờ, COALESCE, NULLIF, CAST, CASE và boolean; hiểu NULL; và viết predicate thân thiện với index.

Tuần này dùng schema bán hàng để học cách biến đổi dữ liệu trong SELECT, xử lý giá trị thiếu, tính tiền, tính tuổi order và gom doanh thu theo tháng.

---

## 1. Schema lab

~~~sql
DROP SCHEMA IF EXISTS basic_week5 CASCADE;
CREATE SCHEMA basic_week5;

CREATE TABLE basic_week5.customers (
    customer_id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email text NOT NULL,
    full_name text NOT NULL,
    phone text,
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE basic_week5.orders (
    order_id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id integer NOT NULL
        REFERENCES basic_week5.customers(customer_id),
    status text NOT NULL,
    amount numeric(12, 2),
    discount numeric(12, 2),
    created_at timestamptz NOT NULL DEFAULT now(),
    shipped_at timestamptz,
    note text
);

CREATE TABLE basic_week5.order_items (
    order_item_id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id integer NOT NULL
        REFERENCES basic_week5.orders(order_id),
    product_name text NOT NULL,
    quantity integer NOT NULL,
    unit_price numeric(12, 2) NOT NULL
);

INSERT INTO basic_week5.customers
    (email, full_name, phone, active, created_at)
VALUES
    ('  AN@EXAMPLE.COM ', 'An Nguyen', NULL, true,
        '2026-01-10 09:00+07'),
    ('binh@example.com', 'Binh Tran', '0900000002', true,
        '2026-02-15 10:00+07'),
    ('CHI@example.com', 'Chi Le', '', false,
        '2026-03-01 11:00+07');

INSERT INTO basic_week5.orders
    (customer_id, status, amount, discount, created_at, shipped_at, note)
VALUES
    (1, 'paid', 1200000, 100000,
        '2026-01-12 10:30+07', '2026-01-13 14:00+07', NULL),
    (1, 'pending', NULL, NULL,
        '2026-02-02 12:00+07', NULL, 'Gọi lại cho khách'),
    (2, 'paid', 350000, 0,
        '2026-02-20 08:30+07', '2026-02-21 09:00+07', NULL),
    (3, 'cancelled', 800000, 50000,
        '2026-03-05 16:00+07', NULL, '');

INSERT INTO basic_week5.order_items
    (order_id, product_name, quantity, unit_price)
VALUES
    (1, 'Keyboard', 1, 800000),
    (1, 'Mouse', 2, 200000),
    (3, 'Monitor', 1, 350000);
~~~

---

## 2. Expression và kiểu dữ liệu

Expression tạo ra một giá trị:

~~~sql
SELECT
    42 AS integer_value,
    amount AS original_amount,
    amount * 1.08 AS amount_with_tax,
    lower(email) AS lowercase_email,
    status = 'paid' AS is_paid
FROM basic_week5.orders AS o
JOIN basic_week5.customers AS c
  ON c.customer_id = o.customer_id;
~~~

Kiểm tra output type:

~~~sql
SELECT
    pg_typeof(10 / 4),
    pg_typeof(10::numeric / 4),
    pg_typeof(now()),
    pg_typeof(current_date);
~~~

Phép chia phụ thuộc type:

~~~sql
SELECT 10 / 4 AS integer_result;
SELECT 10::numeric / 4 AS numeric_result;
~~~

Alias SELECT thường chưa dùng được trong WHERE cùng cấp. Lặp expression hoặc dùng subquery/CTE:

~~~sql
SELECT amount * 1.08 AS gross_amount
FROM basic_week5.orders
WHERE amount * 1.08 > 1000000;
~~~

---

## 3. Hàm chuỗi

~~~sql
SELECT
    email,
    lower(email) AS lowercase_email,
    upper(full_name) AS uppercase_name,
    trim(email) AS trimmed_email,
    lower(trim(email)) AS normalized_email
FROM basic_week5.customers;
~~~

Các hàm chính:

- lower: chữ thường;
- upper: chữ hoa;
- trim, ltrim, rtrim: bỏ khoảng trắng;
- substring: lấy một phần chuỗi;
- concat, concat_ws: nối chuỗi;
- length: độ dài;
- position: vị trí;
- replace: thay chuỗi.

Ví dụ:

~~~sql
SELECT
    substring('PostgreSQL' FROM 1 FOR 8) AS first_part,
    substring('order-2026-001' FROM '[0-9]{4}') AS year_part,
    concat_ws(' - ', full_name, phone, email) AS summary,
    length(email) AS email_length,
    position('@' IN email) AS at_position
FROM basic_week5.customers;
~~~

Toán tử nối:

~~~sql
SELECT 'Hello ' || 'PostgreSQL';
SELECT 'Phone: ' || coalesce(phone, 'chưa có')
FROM basic_week5.customers;
~~~

Chuỗi rỗng khác NULL:

~~~sql
SELECT
    phone,
    phone IS NULL AS is_null,
    phone = '' AS is_empty,
    nullif(trim(phone), '') AS phone_or_null
FROM basic_week5.customers;
~~~

---

## 4. Hàm số

~~~sql
SELECT
    round(123.4567::numeric, 2) AS rounded,
    ceil(10.01::numeric) AS rounded_up,
    floor(10.99::numeric) AS rounded_down,
    abs(-123.45::numeric) AS absolute_value;
~~~

Với tiền, dùng numeric thay vì floating point khi cần quy tắc chính xác:

~~~sql
SELECT round(199999.995::numeric, 2);
~~~

Tính tổng tiền từng item:

~~~sql
SELECT
    order_id,
    product_name,
    quantity,
    unit_price,
    quantity * unit_price AS line_total
FROM basic_week5.order_items;
~~~

Tính số kiện, mỗi kiện tối đa 10 sản phẩm:

~~~sql
SELECT
    order_id,
    quantity,
    ceil(quantity::numeric / 10) AS package_count
FROM basic_week5.order_items;
~~~

---

## 5. Hàm ngày giờ

Các kiểu quan trọng:

- date: chỉ ngày;
- timestamp: ngày giờ không timezone;
- timestamptz: thời điểm có timezone semantics.

~~~sql
SELECT
    now() AS transaction_time,
    current_timestamp,
    clock_timestamp() AS wall_clock_time,
    current_date;
~~~

now và current_timestamp thường ổn định trong transaction. clock_timestamp có thể thay đổi giữa các lần gọi.

Tính tuổi order:

~~~sql
SELECT
    order_id,
    age(now(), created_at) AS age_interval,
    current_date - created_at::date AS age_days,
    extract(epoch FROM now() - created_at) AS age_seconds
FROM basic_week5.orders;
~~~

age phù hợp khoảng theo calendar. Phép trừ/epoch phù hợp duration.

Gom theo tháng:

~~~sql
SELECT
    date_trunc('month', created_at) AS month_start,
    count(*) AS order_count
FROM basic_week5.orders
GROUP BY date_trunc('month', created_at)
ORDER BY month_start;
~~~

Report theo timezone:

~~~sql
SELECT date_trunc(
    'month',
    created_at AT TIME ZONE 'Asia/Ho_Chi_Minh'
) AS local_month
FROM basic_week5.orders;
~~~

Khoảng thời gian:

~~~sql
SELECT
    now() - interval '7 days' AS seven_days_ago,
    now() + interval '2 hours' AS two_hours_later;
~~~

Khoảng nửa mở cho một ngày:

~~~sql
SELECT *
FROM basic_week5.orders
WHERE created_at >= timestamptz '2026-02-20 00:00:00+07'
  AND created_at <  timestamptz '2026-02-21 00:00:00+07';
~~~

---

## 6. NULL và logic ba giá trị

NULL có thể biểu thị thiếu, chưa biết hoặc chưa tính. Nó không phải 0, chuỗi rỗng hay false.

SQL có TRUE, FALSE và UNKNOWN. WHERE chỉ giữ row khi điều kiện là TRUE.

Đúng:

~~~sql
SELECT *
FROM basic_week5.orders
WHERE shipped_at IS NULL;
~~~

Sai:

~~~sql
SELECT *
FROM basic_week5.orders
WHERE shipped_at = NULL;
~~~

NULL trong phép tính:

~~~sql
SELECT
    order_id,
    amount,
    discount,
    amount - discount AS net_amount
FROM basic_week5.orders;
~~~

Nếu amount hoặc discount NULL, net_amount có thể NULL. Chỉ thay bằng 0 khi business rule cho phép.

NULL trong ORDER BY:

~~~sql
SELECT order_id, shipped_at
FROM basic_week5.orders
ORDER BY shipped_at ASC NULLS LAST;
~~~

---

## 7. COALESCE

COALESCE trả argument đầu tiên khác NULL:

~~~sql
SELECT
    order_id,
    coalesce(note, 'Không có ghi chú') AS display_note
FROM basic_week5.orders;
~~~

Kết hợp NULL và chuỗi rỗng:

~~~sql
SELECT
    customer_id,
    coalesce(
        nullif(trim(phone), ''),
        'Chưa có số điện thoại'
    ) AS display_phone
FROM basic_week5.customers;
~~~

Tính payable:

~~~sql
SELECT
    order_id,
    coalesce(amount, 0) AS gross_amount,
    coalesce(discount, 0) AS discount_amount,
    coalesce(amount, 0) - coalesce(discount, 0)
        AS payable_amount
FROM basic_week5.orders;
~~~

Format kết quả khác sửa dữ liệu gốc:

~~~sql
SELECT coalesce(phone, 'N/A')
FROM basic_week5.customers;
~~~

Không lưu N/A vào column phone nếu cần phân biệt thiếu dữ liệu với text thật.

---

## 8. NULLIF

NULLIF(a, b) trả NULL nếu a bằng b; nếu không trả a:

~~~sql
SELECT
    nullif('', '') AS empty_to_null,
    nullif('abc', '') AS text_value,
    nullif(0, 0) AS zero_to_null,
    nullif(10, 0) AS ten_value;
~~~

Tránh chia cho 0:

~~~sql
SELECT
    revenue / nullif(order_count, 0) AS average_order_value
FROM some_report;
~~~

Hiển thị 0 nếu business muốn:

~~~sql
SELECT
    coalesce(revenue / nullif(order_count, 0), 0)
        AS average_order_value
FROM some_report;
~~~

Mẫu thường gặp:

~~~text
COALESCE(NULLIF(value, invalid_value), fallback)
~~~

---

## 9. CAST và toán tử ::

~~~sql
SELECT
    CAST('2026-03-10' AS date),
    CAST('123.45' AS numeric(10, 2)),
    CAST(10 AS numeric) / 4;
~~~

Toán tử PostgreSQL:

~~~sql
SELECT
    '2026-03-10'::date,
    '123.45'::numeric(10, 2),
    10::numeric / 4;
~~~

Input không hợp lệ gây lỗi:

~~~sql
SELECT 'one hundred'::integer;
~~~

Dữ liệu import nên qua staging và validation trước khi cast vào bảng production.

Cast timestamp:

~~~sql
SELECT
    created_at::date AS created_date,
    created_at::time AS created_time
FROM basic_week5.orders;
~~~

Cast boolean:

~~~sql
SELECT
    'true'::boolean,
    'false'::boolean,
    true::text;
~~~

Khi type khó đoán:

~~~sql
SELECT
    pg_typeof(10 / 4),
    pg_typeof(10::numeric / 4),
    pg_typeof(now()),
    pg_typeof(current_date);
~~~

---

## 10. CASE

Searched CASE:

~~~sql
SELECT
    order_id,
    amount,
    CASE
        WHEN amount IS NULL THEN 'not_calculated'
        WHEN amount >= 1000000 THEN 'large'
        WHEN amount >= 500000 THEN 'medium'
        ELSE 'small'
    END AS order_size
FROM basic_week5.orders;
~~~

Điều kiện được xét từ trên xuống.

Simple CASE:

~~~sql
SELECT
    order_id,
    CASE status
        WHEN 'paid' THEN 'Đã thanh toán'
        WHEN 'pending' THEN 'Đang chờ'
        WHEN 'cancelled' THEN 'Đã hủy'
        ELSE 'Không rõ'
    END AS status_label
FROM basic_week5.orders;
~~~

CASE tính phí:

~~~sql
SELECT
    order_id,
    CASE
        WHEN status = 'cancelled' THEN 0::numeric
        WHEN coalesce(amount, 0) >= 1000000 THEN 0::numeric
        ELSE 30000::numeric
    END AS shipping_fee
FROM basic_week5.orders;
~~~

CASE trong ORDER BY:

~~~sql
SELECT order_id, status, created_at
FROM basic_week5.orders
ORDER BY
    CASE status
        WHEN 'paid' THEN 1
        WHEN 'pending' THEN 2
        WHEN 'cancelled' THEN 3
        ELSE 4
    END,
    created_at DESC;
~~~

CASE trong aggregate:

~~~sql
SELECT
    count(CASE WHEN status = 'paid' THEN 1 END) AS paid_count,
    sum(CASE WHEN status = 'paid' THEN amount ELSE 0 END)
        AS paid_amount
FROM basic_week5.orders;
~~~

---

## 11. Boolean

Boolean có true, false và NULL:

~~~sql
SELECT *
FROM basic_week5.customers
WHERE active;

SELECT *
FROM basic_week5.customers
WHERE active IS TRUE;
~~~

NOT active không chọn NULL. Nếu NULL được coi là false:

~~~sql
SELECT *
FROM basic_week5.customers
WHERE active IS NOT TRUE;
~~~

Nếu NULL được coi là true:

~~~sql
SELECT *
FROM basic_week5.customers
WHERE active IS NOT FALSE;
~~~

AND có precedence cao hơn OR. Dùng ngoặc:

~~~sql
SELECT *
FROM basic_week5.orders
WHERE status = 'paid'
  AND (amount >= 1000000 OR discount > 0);
~~~

---

## 12. Tính order và doanh thu

Line total:

~~~sql
SELECT
    order_id,
    product_name,
    quantity,
    unit_price,
    quantity * unit_price AS line_total
FROM basic_week5.order_items;
~~~

Tổng item:

~~~sql
SELECT
    order_id,
    sum(quantity * unit_price) AS item_total
FROM basic_week5.order_items
GROUP BY order_id
ORDER BY order_id;
~~~

Doanh thu paid theo tháng:

~~~sql
SELECT
    date_trunc('month', created_at) AS month_start,
    coalesce(
        sum(amount) FILTER (WHERE status = 'paid'),
        0
    ) AS paid_revenue
FROM basic_week5.orders
GROUP BY date_trunc('month', created_at)
ORDER BY month_start;
~~~

Nếu tất cả amount là NULL, sum có thể trả NULL. Dùng coalesce về 0 chỉ khi báo cáo cần semantics đó.

---

## 13. Chuẩn hóa email

Preview:

~~~sql
SELECT
    customer_id,
    email AS original_email,
    lower(trim(email)) AS normalized_email,
    email IS DISTINCT FROM lower(trim(email))
        AS needs_normalization
FROM basic_week5.customers;
~~~

Tìm duplicate sau chuẩn hóa:

~~~sql
SELECT
    lower(trim(email)) AS normalized_email,
    count(*)
FROM basic_week5.customers
GROUP BY lower(trim(email))
HAVING count(*) > 1;
~~~

Update có kiểm soát:

~~~sql
BEGIN;

UPDATE basic_week5.customers
SET email = lower(trim(email))
WHERE email IS DISTINCT FROM lower(trim(email));

SELECT * FROM basic_week5.customers
ORDER BY customer_id;

ROLLBACK;
~~~

IS DISTINCT FROM hữu ích khi cần so sánh NULL một cách deterministic.

---

## 14. Hàm trong WHERE và index

Expression bọc column:

~~~sql
SELECT *
FROM basic_week5.customers
WHERE lower(email) = 'an@example.com';
~~~

Nếu chỉ có index trên email, index đó có thể không được dùng. Có thể tạo expression index:

~~~sql
CREATE INDEX customers_lower_email_idx
ON basic_week5.customers (lower(email));
~~~

Predicate timestamp thân thiện hơn:

~~~sql
SELECT *
FROM basic_week5.orders
WHERE created_at >= timestamptz '2026-02-20 00:00:00+07'
  AND created_at <  timestamptz '2026-02-21 00:00:00+07';
~~~

Thay vì:

~~~sql
SELECT *
FROM basic_week5.orders
WHERE created_at::date = date '2026-02-20';
~~~

Cast column trong WHERE có thể làm index trên created_at khó sử dụng. Đây là ý tưởng SARGability: đưa phép tính sang phía constant nếu có thể.

Kiểm tra:

~~~sql
EXPLAIN
SELECT *
FROM basic_week5.orders
WHERE created_at >= timestamptz '2026-02-20 00:00:00+07'
  AND created_at <  timestamptz '2026-02-21 00:00:00+07';
~~~

Bảng nhỏ vẫn có thể chọn sequential scan; không ép index chỉ vì nhìn thấy index tồn tại.

---

## 15. Thứ tự đánh giá query

Ở mức khái niệm:

~~~text
FROM
WHERE
GROUP BY
HAVING
SELECT
ORDER BY
LIMIT
~~~

Điều này giải thích tại sao alias SELECT thường chưa dùng được trong WHERE cùng cấp, trong khi ORDER BY có thể dùng alias.

~~~sql
SELECT
    order_id,
    amount,
    CASE
        WHEN amount >= 1000000 THEN 'large'
        ELSE 'small'
    END AS size_label
FROM basic_week5.orders
WHERE status = 'paid'
ORDER BY size_label, order_id;
~~~

---

## 16. Lỗi thường gặp

### Dùng = NULL

Dùng IS NULL hoặc IS NOT NULL.

### Nhầm empty và NULL

Dùng nullif(trim(value), '') khi domain coi chuỗi rỗng là thiếu.

### COALESCE che lỗi

NULL amount có thể nghĩa là chưa tính, không phải miễn phí. Không thay bằng 0 nếu chưa có business rule.

### Chia integer

Cast một operand sang numeric khi cần phần thập phân.

### CASE sai thứ tự

Đặt điều kiện cụ thể trước điều kiện rộng.

### AND/OR thiếu ngoặc

Viết ngoặc để thể hiện ý định.

### Bọc column trong WHERE

date(created_at), lower(email) và amount::text có thể làm index hiện có khó sử dụng. Dùng expression index hoặc viết lại predicate khi phù hợp.

### Sửa dữ liệu gốc khi chỉ cần hiển thị

COALESCE trong SELECT không có nghĩa là phải UPDATE table.

---

## 17. Lab hoàn chỉnh

### Bước 1: Chuẩn hóa email

~~~sql
SELECT
    customer_id,
    lower(trim(email)) AS normalized_email,
    nullif(trim(phone), '') AS phone_or_null
FROM basic_week5.customers;
~~~

### Bước 2: Tính tuổi order

~~~sql
SELECT
    order_id,
    current_date - created_at::date AS age_days,
    age(now(), created_at) AS age_interval,
    CASE
        WHEN shipped_at IS NULL THEN 'Chưa giao'
        ELSE 'Đã giao'
    END AS shipping_state
FROM basic_week5.orders;
~~~

### Bước 3: Tính payable

~~~sql
SELECT
    order_id,
    coalesce(amount, 0) AS amount,
    coalesce(discount, 0) AS discount,
    coalesce(amount, 0) - coalesce(discount, 0)
        AS payable_amount
FROM basic_week5.orders;
~~~

### Bước 4: Doanh thu theo tháng

~~~sql
SELECT
    date_trunc('month', created_at) AS month_start,
    coalesce(
        sum(amount) FILTER (WHERE status = 'paid'),
        0
    ) AS paid_revenue
FROM basic_week5.orders
GROUP BY date_trunc('month', created_at)
ORDER BY month_start;
~~~

### Bước 5: So sánh predicate

~~~sql
EXPLAIN
SELECT *
FROM basic_week5.orders
WHERE created_at::date = date '2026-02-20';

EXPLAIN
SELECT *
FROM basic_week5.orders
WHERE created_at >= timestamptz '2026-02-20 00:00:00+07'
  AND created_at <  timestamptz '2026-02-21 00:00:00+07';
~~~

Ghi lại hình dạng predicate và lý do query thứ hai bảo toàn column gốc.

---

## 18. Bài tập thực hành

### Bài 1 — Chuẩn hóa email

Trả email gốc, email trim, email lowercase và cờ cần chuẩn hóa. Tìm duplicate trước khi UPDATE.

### Bài 2 — Số ngày từ lúc tạo đơn

Trả order_id, số ngày, số giờ và nhãn new/aging/old. Xử lý order chưa giao bằng CASE.

### Bài 3 — Giá trị NULL

Trả note hiển thị là “Không có ghi chú” nhưng giữ nguyên NULL trong table. Phân biệt phone NULL và chuỗi rỗng.

### Bài 4 — Tổng item

Tính line_total và item_total theo order. So sánh với amount lưu trong orders.

### Bài 5 — Doanh thu tháng

Gom doanh thu paid bằng date_trunc. Thử tháng không có row và quyết định hiển thị NULL hay 0.

### Bài 6 — NULLIF

Tạo báo cáo revenue, order_count và average_order_value. Dùng NULLIF tránh chia cho 0, sau đó quyết định COALESCE.

### Bài 7 — CASE

Phân loại order theo amount, status và shipped_at. Đặt điều kiện cụ thể trước điều kiện tổng quát.

### Bài 8 — Boolean

Viết query chọn customer active, inactive và active chưa rõ bằng IS TRUE/IS FALSE.

### Bài 9 — CAST

Parse text thành date, numeric và boolean trong staging table. Test input không hợp lệ.

### Bài 10 — SARGability

So sánh EXPLAIN của predicate created_at::date với khoảng timestamp nửa mở và giải thích tác động tới index.

---

## 19. Checklist viết query

Trước khi chạy:

- input column có type gì;
- function trả type gì;
- NULL có ý nghĩa gì;
- chuỗi rỗng có cần thành NULL không;
- phép chia có thể chia 0 không;
- CASE đã bao hết branch chưa;
- timezone report là gì;
- cần duration hay calendar age;
- cast đặt ở column hay constant;
- WHERE có bọc column bằng function không;
- AND/OR đã có ngoặc chưa;
- cần sửa dữ liệu gốc hay chỉ format kết quả.

Sau khi chạy:

- kiểm tra row mẫu;
- kiểm tra NULL, empty và zero;
- kiểm tra boundary date;
- dùng pg_typeof khi type khó đoán;
- dùng EXPLAIN trên bảng lớn;
- SELECT preview trước UPDATE thật.

---

## 20. Tiêu chí hoàn thành Tuần 5

Bạn hoàn thành bài khi có thể:

- biết kiểu input và output của function;
- dùng lower, upper, trim, substring và concat;
- dùng round, ceil, floor và abs;
- dùng now, current_date, date_trunc, age và interval;
- dùng COALESCE có chủ đích;
- dùng NULLIF để xử lý empty value và chia 0;
- dùng CAST và toán tử ::;
- viết searched/simple CASE;
- xử lý boolean true/false/NULL;
- phân biệt NULL, chuỗi rỗng, 0 và false;
- tính order age, line total và doanh thu theo tháng;
- biết khi nào sửa dữ liệu gốc và khi nào chỉ format kết quả;
- tránh = NULL và AND/OR thiếu ngoặc;
- nhận biết function/cast trên column có thể ảnh hưởng index;
- viết predicate timestamp theo khoảng nửa mở;
- dùng EXPLAIN để kiểm tra giả thuyết.

---

## Tóm tắt cần nhớ

1. Expression có input, output type, NULL semantics và chi phí.
2. lower/trim dùng để chuẩn hóa text nhưng không thay validation.
3. round/ceil/floor/abs cần type số phù hợp, đặc biệt với tiền.
4. now ổn định trong transaction; clock_timestamp là wall clock.
5. date_trunc gom thời gian theo bucket; timezone phải rõ.
6. NULL không phải 0, chuỗi rỗng hay false.
7. Dùng IS NULL, không dùng = NULL.
8. COALESCE là quyết định semantics, không phải thuốc chữa mọi NULL.
9. NULLIF hữu ích để xử lý empty value và chia cho 0.
10. CAST rõ ràng tránh implicit cast ngoài ý muốn.
11. CASE xét từ trên xuống; branch rộng đặt trước có thể che branch hẹp.
12. Không bọc column trong WHERE tùy tiện nếu muốn planner có cơ hội dùng index.

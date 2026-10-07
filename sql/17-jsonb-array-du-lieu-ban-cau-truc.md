# Tuần 5 nâng cao: JSONB, array và dữ liệu bán cấu trúc

> Mục tiêu: biết khi nào dùng JSONB, khi nào dùng column quan hệ; đọc, lọc, cập nhật và mở rộng JSONB; tạo GIN index; dùng generated column; xử lý array và unnest; và thiết kế dữ liệu bán cấu trúc mà vẫn giữ được tính đúng và khả năng query.

Dữ liệu bán cấu trúc nằm giữa hai thái cực:

~~~text
Schema quan hệ hoàn toàn cố định
Dữ liệu JSON linh hoạt và thay đổi
~~~

PostgreSQL hỗ trợ cả hai. Mục tiêu không phải đưa mọi thứ vào JSONB, mà là đặt mỗi field ở nơi phù hợp.

Nguyên tắc:

- field nghiệp vụ ổn định, thường JOIN hoặc cần constraint nên là column riêng;
- metadata thay đổi hoặc không đồng nhất có thể dùng JSONB;
- quan hệ N:N cần bảng trung gian, không dùng một JSON array để thay thế;
- index JSONB phải dựa trên operator và query thật;
- cập nhật JSONB phải tạo giá trị mới, không mutate một phần theo cách application không kiểm soát;
- schema linh hoạt vẫn cần validation ở boundary.

---

## 1. JSON và JSONB

JSON là dạng text biểu diễn dữ liệu JSON. JSONB là dạng binary đã được PostgreSQL parse để query và index hiệu quả hơn.

| Đặc điểm | JSON | JSONB |
|---|---|---|
| Lưu trữ | giữ dạng text | lưu dạng binary |
| Parse | parse khi xử lý | parse khi ghi |
| Query | phù hợp lưu/đọc payload | phù hợp query và index |
| Thứ tự key | giữ theo input | không giữ ý nghĩa thứ tự |
| Duplicate key | có thể giữ theo text | key trùng được xử lý theo JSONB |
| Index | hạn chế | GIN và một số index phù hợp |

Với dữ liệu cần query trong PostgreSQL, JSONB thường là lựa chọn mặc định:

~~~sql
CREATE TABLE orders (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id bigint NOT NULL,
    metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now()
);
~~~

Dùng JSON nếu chỉ lưu payload và luôn chuyển tiếp gần như nguyên dạng, không cần query field bên trong. Với phần lớn metadata trong application database, JSONB thực tế hơn.

---

## 2. Giá trị JSONB hợp lệ

JSONB có thể chứa:

- object;
- array;
- string;
- number;
- boolean;
- null.

Ví dụ:

~~~sql
INSERT INTO orders (customer_id, metadata)
VALUES (
    10,
    '{
        "source": "mobile",
        "priority": "high",
        "coupon": {
            "code": "WELCOME",
            "discount_percent": 10
        },
        "tags": ["new", "campaign"]
    }'::jsonb
);
~~~

JSONB không tự đảm bảo business schema. Payload sau vẫn có thể hợp lệ về JSON nhưng sai nghiệp vụ:

~~~json
{
  "priority": 999,
  "discount_percent": -50
}
~~~

Nếu field cần constraint, tách thành column hoặc validate bằng application/schema layer trước khi ghi.

---

## 3. Toán tử truy cập JSONB

Giả sử metadata:

~~~json
{
  "source": "mobile",
  "priority": "high",
  "coupon": {
    "code": "WELCOME",
    "discount_percent": 10
  },
  "tags": ["new", "campaign"]
}
~~~

### 3.1. Mũi tên một

Lấy child value dưới dạng JSONB:

~~~sql
SELECT metadata -> 'coupon' AS coupon
FROM orders;
~~~

Kết quả là object JSONB:

~~~json
{
  "code": "WELCOME",
  "discount_percent": 10
}
~~~

### 3.2. Mũi tên hai

Lấy child value dưới dạng text:

~~~sql
SELECT metadata ->> 'source' AS source
FROM orders;
~~~

Kết quả là text mobile.

Đây là khác biệt quan trọng:

~~~text
→  trả JSONB
→> trả text
~~~

Khi so sánh với text, dùng ->>:

~~~sql
WHERE metadata ->> 'priority' = 'high'
~~~

### 3.3. Truy cập theo path

~~~sql
SELECT metadata #> '{coupon,code}' AS coupon_code_json
FROM orders;
~~~

Trả JSONB.

~~~sql
SELECT metadata #>> '{coupon,code}' AS coupon_code_text
FROM orders;
~~~

Trả text.

Có thể dùng path array:

~~~sql
SELECT jsonb_extract_path_text(
    metadata,
    'coupon',
    'code'
) AS coupon_code
FROM orders;
~~~

---

## 4. JSONB containment

Toán tử @> kiểm tra left JSONB có chứa right JSONB không.

~~~sql
SELECT id, metadata
FROM orders
WHERE metadata @> '{"priority": "high"}'::jsonb;
~~~

Object bên trái có thể chứa thêm key khác nhưng vẫn match.

Nested containment:

~~~sql
SELECT id
FROM orders
WHERE metadata @> '{
    "coupon": {
        "code": "WELCOME"
    }
}'::jsonb;
~~~

Array containment:

~~~sql
SELECT id
FROM orders
WHERE metadata @> '{"tags": ["new"]}'::jsonb;
~~~

Toán tử ngược <@ kiểm tra left có nằm trong right:

~~~sql
SELECT id
FROM orders
WHERE '{"priority": "high"}'::jsonb <@ metadata;
~~~

Containment hữu ích khi query indexable bằng GIN. Nhưng cần xác định chính xác semantics của array và nested object, không giả định nó giống một phép so sánh text.

---

## 5. Key existence

Toán tử ? kiểm tra một key tồn tại:

~~~sql
SELECT id
FROM orders
WHERE metadata ? 'coupon';
~~~

Kiểm tra một trong nhiều key với ?|:

~~~sql
SELECT id
FROM orders
WHERE metadata ?| ARRAY['coupon', 'voucher'];
~~~

Kiểm tra tất cả key với ?&:

~~~sql
SELECT id
FROM orders
WHERE metadata ?& ARRAY['source', 'priority'];
~~~

Các toán tử này hữu ích để phân loại payload:

~~~sql
SELECT
    COUNT(*) FILTER (WHERE metadata ? 'coupon') AS with_coupon,
    COUNT(*) FILTER (WHERE metadata ? 'shipping') AS with_shipping
FROM orders;
~~~

---

## 6. JSON path

PostgreSQL hỗ trợ JSON path với @? và @@.

Kiểm tra có item phù hợp:

~~~sql
SELECT id
FROM orders
WHERE metadata @? '$.items[*] ? (@.quantity > 3)';
~~~

Đánh giá một predicate JSON path:

~~~sql
SELECT id
FROM orders
WHERE metadata @@ '$.priority == "high"';
~~~

JSON path hữu ích với cấu trúc nested hoặc array phức tạp. Nhưng query khó đọc hơn column quan hệ; chỉ dùng khi dữ liệu thực sự bán cấu trúc.

Khi logic ổn định và query thường xuyên, cân nhắc:

- tách field thành column;
- generated column;
- expression index;
- GIN index;
- materialized view cho báo cáo.

---

## 7. Lấy JSONB thành dữ liệu quan hệ

Lấy object field:

~~~sql
SELECT
    id,
    metadata ->> 'source' AS source,
    metadata ->> 'priority' AS priority
FROM orders;
~~~

Ép kiểu số:

~~~sql
SELECT
    id,
    (metadata ->> 'discount_percent')::numeric AS discount_percent
FROM orders
WHERE metadata ? 'discount_percent';
~~~

Phải cẩn thận cast nếu payload có value không phải số:

~~~sql
SELECT
    CASE
        WHEN metadata ->> 'discount_percent' ~ '^[0-9]+(\.[0-9]+)?$'
        THEN (metadata ->> 'discount_percent')::numeric
    END AS discount_percent
FROM orders;
~~~

Tốt hơn nữa là validate payload ở boundary hoặc tách field quan trọng thành numeric column.

---

## 8. Cập nhật JSONB với jsonb_set

jsonb_set tạo JSONB mới với một path được thay đổi:

~~~sql
UPDATE orders
SET metadata = jsonb_set(
    metadata,
    '{priority}',
    '"normal"'::jsonb,
    true
)
WHERE id = 100;
~~~

Tham số true cho phép tạo key nếu chưa tồn tại.

Cập nhật nested value:

~~~sql
UPDATE orders
SET metadata = jsonb_set(
    metadata,
    '{coupon,discount_percent}',
    '15'::jsonb,
    false
)
WHERE id = 100;
~~~

Nếu path không tồn tại và create_missing là false, không tạo path mới.

Xóa key bằng -:

~~~sql
UPDATE orders
SET metadata = metadata - 'coupon'
WHERE id = 100;
~~~

Xóa nested path bằng #-:

~~~sql
UPDATE orders
SET metadata = metadata #- '{coupon,discount_percent}'
WHERE id = 100;
~~~

Concatenate JSONB bằng ||:

~~~sql
UPDATE orders
SET metadata = metadata || '{"source": "web"}'::jsonb
WHERE id = 100;
~~~

|| merge ở mức top-level; nó không phải deep merge tổng quát cho mọi object nested. Kiểm tra kết quả trước khi dùng cho payload phức tạp.

---

## 9. jsonb_array_elements

Mở rộng JSON array thành nhiều row:

~~~sql
CREATE TABLE order_payloads (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    payload jsonb NOT NULL
);

SELECT
    op.id,
    item
FROM order_payloads AS op
CROSS JOIN LATERAL jsonb_array_elements(
    op.payload -> 'items'
) AS items(item);
~~~

Nếu array chứa object:

~~~sql
SELECT
    op.id,
    item ->> 'sku' AS sku,
    (item ->> 'quantity')::integer AS quantity
FROM order_payloads AS op
CROSS JOIN LATERAL jsonb_array_elements(
    op.payload -> 'items'
) AS items(item);
~~~

Grain thay đổi:

~~~text
Trước: một row / payload
Sau: một row / payload item
~~~

Kiểm tra tồn tại một item mà không cần trả tất cả row:

~~~sql
SELECT op.id
FROM order_payloads AS op
WHERE EXISTS (
    SELECT 1
    FROM jsonb_array_elements(op.payload -> 'items') AS items(item)
    WHERE item ->> 'sku' = 'BOOK-10'
);
~~~

---

## 10. jsonb_to_record và jsonb_to_recordset

Khi object có cấu trúc đã biết, chuyển thành record:

~~~sql
SELECT *
FROM jsonb_to_record(
    '{
        "sku": "BOOK-10",
        "quantity": 2,
        "price": 120000
    }'::jsonb
) AS x(
    sku text,
    quantity integer,
    price numeric
);
~~~

Với array object:

~~~sql
SELECT *
FROM jsonb_to_recordset(
    '[
        {"sku": "BOOK-10", "quantity": 2},
        {"sku": "BOOK-20", "quantity": 1}
    ]'::jsonb
) AS x(
    sku text,
    quantity integer
);
~~~

Đây là cách hữu ích cho import hoặc xử lý payload tạm thời, nhưng dữ liệu giao dịch quan trọng nên được đưa vào bảng quan hệ có constraint.

---

## 11. GIN index cho JSONB

Index cơ bản:

~~~sql
CREATE INDEX orders_metadata_gin_idx
ON orders USING GIN (metadata);
~~~

Phục vụ containment:

~~~sql
SELECT id
FROM orders
WHERE metadata @> '{"priority": "high"}'::jsonb;
~~~

GIN có thể hỗ trợ key existence và một số operator JSONB khác tùy opclass.

jsonb_path_ops:

~~~sql
CREATE INDEX orders_metadata_path_gin_idx
ON orders USING GIN (
    metadata jsonb_path_ops
);
~~~

jsonb_path_ops thường tối ưu cho containment pattern và có thể nhỏ hơn, nhưng không hỗ trợ mọi operator như opclass mặc định. Chọn theo query thật.

GIN trade-off:

- index lớn;
- INSERT/UPDATE JSONB tốn hơn;
- write amplification;
- vacuum và bloat;
- query expression phải tương thích operator class.

Không tạo cả hai GIN index mặc định và path_ops nếu không có workload chứng minh.

---

## 12. Expression index trên JSONB

Nếu thường lọc một key scalar, expression index có thể nhỏ hơn GIN:

~~~sql
CREATE INDEX orders_source_idx
ON orders ((metadata ->> 'source'));
~~~

Query phải viết tương thích:

~~~sql
SELECT id, created_at
FROM orders
WHERE metadata ->> 'source' = 'mobile';
~~~

Index trên cast:

~~~sql
CREATE INDEX orders_discount_idx
ON orders (((metadata ->> 'discount_percent')::numeric));
~~~

Chỉ tạo khi payload bảo đảm cast hợp lệ. Một value malformed có thể làm query lỗi.

Nếu field trở thành field nghiệp vụ chính, nên migrate nó thành column riêng:

~~~sql
ALTER TABLE orders
ADD COLUMN source text;
~~~

Sau đó backfill, validate và chuyển application sang column.

---

## 13. Generated column từ JSONB

Generated column biến field JSONB ổn định thành column có type:

~~~sql
CREATE TABLE profiles (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
    source text GENERATED ALWAYS AS (
        metadata ->> 'source'
    ) STORED
);
~~~

Có thể index source:

~~~sql
CREATE INDEX profiles_source_idx
ON profiles (source);
~~~

Generated column phù hợp khi:

- expression ổn định;
- field cần query thường xuyên;
- muốn query dễ đọc;
- muốn planner có column rõ ràng.

Generated column không giải quyết validation nếu JSONB chứa sai type. Nếu source bắt buộc thuộc danh sách, thêm constraint nếu database cho phép biểu thức cần thiết:

~~~sql
CHECK (source IS NULL OR source IN ('web', 'mobile', 'api'))
~~~

---

## 14. jsonb_set và concurrency

jsonb_set trong UPDATE là một statement atomic theo row lock:

~~~sql
UPDATE profiles
SET metadata = jsonb_set(
    metadata,
    '{last_seen_source}',
    '"mobile"'::jsonb,
    true
)
WHERE id = 10;
~~~

Nhưng hai request cùng cập nhật các path khác nhau vẫn có thể ghi đè phiên bản JSONB nếu cả hai đọc rồi ghi toàn object ở application.

Không nên:

1. SELECT metadata;
2. application sửa JSON;
3. UPDATE metadata bằng toàn object cũ.

Nếu cần cập nhật một path, dùng jsonb_set trong UPDATE hoặc khóa row trong transaction. Với payload phức tạp, thiết kế concurrency rule rõ ràng.

---

## 15. Array trong PostgreSQL

Array phù hợp với danh sách đơn giản thuộc một row:

~~~sql
CREATE TABLE users (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    roles text[] NOT NULL DEFAULT ARRAY[]::text[]
);
~~~

Insert:

~~~sql
INSERT INTO users (roles)
VALUES (ARRAY['reader', 'reviewer']);
~~~

Kiểm tra phần tử:

~~~sql
SELECT id
FROM users
WHERE 'reviewer' = ANY(roles);
~~~

Kiểm tra containment:

~~~sql
SELECT id
FROM users
WHERE roles @> ARRAY['reader', 'reviewer'];
~~~

Array có thể có NULL element; hãy quyết định có cho phép không và validate ở application hoặc constraint phù hợp.

---

## 16. unnest

unnest biến array thành nhiều row:

~~~sql
SELECT unnest(ARRAY['reader', 'reviewer', 'admin']) AS role;
~~~

Với table:

~~~sql
SELECT
    u.id,
    role
FROM users AS u
CROSS JOIN LATERAL unnest(u.roles) AS roles(role);
~~~

Kèm ordinal position:

~~~sql
SELECT
    u.id,
    roles.position,
    roles.role
FROM users AS u
CROSS JOIN LATERAL unnest(u.roles)
    WITH ORDINALITY AS roles(role, position);
~~~

Grain đổi từ một row mỗi user thành một row mỗi user-role.

---

## 17. Array nhiều chiều và các hàm hỗ trợ

Các hàm thường dùng:

~~~sql
SELECT array_length(ARRAY['a', 'b', 'c'], 1);
SELECT cardinality(ARRAY['a', 'b', 'c']);
SELECT array_position(ARRAY['a', 'b', 'c'], 'b');
SELECT array_to_string(ARRAY['a', 'b'], ',');
~~~

Concat array:

~~~sql
SELECT ARRAY['reader'] || ARRAY['reviewer'];
~~~

Array không nên dùng để mô phỏng dữ liệu có thứ tự nghiệp vụ phức tạp, foreign key tới từng phần tử hoặc nhiều metadata cho từng phần tử.

---

## 18. Khi nào dùng column quan hệ thay cho JSONB?

Dùng column quan hệ khi field:

- được filter thường xuyên;
- được JOIN;
- cần foreign key;
- cần UNIQUE;
- cần CHECK;
- cần aggregate;
- có schema ổn định;
- là một phần của transaction;
- cần index B-tree rõ ràng.

Dùng JSONB khi field:

- là metadata phụ;
- thay đổi giữa loại record;
- đến từ external payload;
- không cần relation độc lập;
- không có constraint phức tạp;
- truy vấn không quá thường xuyên hoặc có GIN phù hợp.

Dùng bảng quan hệ khi:

- có quan hệ 1:N hoặc N:N;
- phần tử cần ID riêng;
- phần tử có thuộc tính riêng;
- cần update/delete từng phần tử;
- cần constraint và foreign key.

Ví dụ không nên lưu order items trong JSONB:

~~~json
{
  "items": [
    {"book_id": 10, "quantity": 2},
    {"book_id": 20, "quantity": 1}
  ]
}
~~~

Nếu đây là dữ liệu giao dịch, nên dùng order_items:

~~~text
order_items(order_id, book_id, quantity, unit_price)
~~~

JSONB có thể giữ raw payment payload bên cạnh bảng payment chuẩn hóa.

---

## 19. Schema hybrid

Thiết kế hybrid tách field chính và giữ metadata:

~~~sql
CREATE TABLE orders (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id bigint NOT NULL REFERENCES customers(id),
    status text NOT NULL CHECK (
        status IN ('pending', 'paid', 'cancelled')
    ),
    total_amount numeric(12, 2) NOT NULL CHECK (total_amount >= 0),
    metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now()
);
~~~

Field quan hệ:

- customer_id;
- status;
- total_amount;
- created_at.

Metadata có thể chứa:

~~~json
{
  "utm_source": "newsletter",
  "device": "ios",
  "provider_payload_version": 3
}
~~~

Đây thường là cân bằng tốt hơn việc đưa toàn bộ order vào JSONB.

---

## 20. Validate JSONB ở boundary

JSONB không tự áp schema nghiệp vụ. Các lớp validation:

### Application schema

Validate payload trước khi gọi INSERT bằng schema library hoặc DTO.

### Database constraint đơn giản

~~~sql
CREATE TABLE profiles (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
    CHECK (
        metadata ? 'source'
        OR metadata = '{}'::jsonb
    )
);
~~~

### Generated column và CHECK

~~~sql
CREATE TABLE events (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    payload jsonb NOT NULL,
    event_type text GENERATED ALWAYS AS (
        payload ->> 'type'
    ) STORED,
    CHECK (event_type IS NOT NULL)
);
~~~

### Bảng quan hệ

Nếu field trở thành quan trọng, migrate khỏi JSONB sang column hoặc table có type và constraint rõ.

Đừng viết một CHECK JSONB khổng lồ nếu nó khó bảo trì hơn schema quan hệ.

---

## 21. Query plan cho JSONB

Đo trước khi tạo GIN:

~~~sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT id
FROM orders
WHERE metadata @> '{"priority": "high"}'::jsonb;
~~~

Tạo index:

~~~sql
CREATE INDEX orders_metadata_gin_idx
ON orders USING GIN (metadata);

ANALYZE orders;
~~~

Đo lại:

~~~sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT id
FROM orders
WHERE metadata @> '{"priority": "high"}'::jsonb;
~~~

Quan sát:

- GIN Bitmap Index Scan;
- Bitmap Heap Scan;
- estimated rows và actual rows;
- index size;
- shared hit/read;
- write cost khi insert/update metadata.

Nếu priority là field query rất thường xuyên và scalar, B-tree trên expression hoặc column riêng có thể tốt hơn GIN toàn object.

---

## 22. Migration JSONB sang column

Khi metadata source trở thành field chính:

### Bước 1: thêm column nullable

~~~sql
ALTER TABLE orders
ADD COLUMN source text;
~~~

### Bước 2: backfill

~~~sql
UPDATE orders
SET source = metadata ->> 'source'
WHERE source IS NULL
  AND metadata ? 'source';
~~~

### Bước 3: kiểm tra dữ liệu

~~~sql
SELECT source, COUNT(*)
FROM orders
GROUP BY source
ORDER BY COUNT(*) DESC;
~~~

### Bước 4: thêm constraint hoặc index

~~~sql
ALTER TABLE orders
ADD CONSTRAINT orders_source_check
CHECK (source IS NULL OR source IN ('web', 'mobile', 'api'));

CREATE INDEX orders_source_idx
ON orders (source);
~~~

### Bước 5: chuyển application

Đọc/ghi column source. Giữ JSONB chỉ khi còn cần backward compatibility.

### Bước 6: quyết định cleanup

Sau thời gian ổn định, xóa key trong metadata hoặc giữ làm raw history nếu có lý do.

Không backfill và drop key trong một migration không thể rollback dễ dàng trên dữ liệu lớn.

---

## 23. Lỗi thường gặp

### Nhầm -> và ->>

-> trả JSONB; ->> trả text. Dùng sai có thể làm phép so sánh hoặc cast không đúng.

### Dùng JSONB cho field cần foreign key

JSONB không tự bảo đảm customer_id hoặc book_id tồn tại.

### GIN cho mọi JSONB

GIN tốn storage và write. Đo operator và query thật.

### Update toàn object từ application

Hai request có thể ghi đè thay đổi của nhau. Dùng jsonb_set hoặc row lock phù hợp.

### Cast JSONB mù quáng

Value sai type làm query lỗi. Validate trước hoặc dùng column có type.

### Dùng array thay bảng trung gian

Array không phù hợp khi phần tử có foreign key, thuộc tính riêng hoặc cần query độc lập.

### Không xác định grain sau jsonb_array_elements

Một payload có nhiều item sẽ tạo nhiều row; aggregate sau đó phải tính đúng grain.

### Deep merge bằng ||

|| không phải deep merge tổng quát. Với nested object, cập nhật path cụ thể bằng jsonb_set.

### JSONB trở thành schema ẩn

Nếu mọi query phải biết key, version và kiểu dữ liệu bằng convention, schema thật đã tồn tại nhưng database không bảo vệ nó. Hãy tách field ổn định.

---

## 24. Lab tổng hợp

Tạo bảng:

~~~sql
CREATE TABLE order_payloads (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id bigint NOT NULL,
    payload jsonb NOT NULL,
    tags text[] NOT NULL DEFAULT ARRAY[]::text[],
    created_at timestamptz NOT NULL DEFAULT now()
);
~~~

Insert dữ liệu:

~~~sql
INSERT INTO order_payloads (order_id, payload, tags)
VALUES
(
    100,
    '{
        "source": "mobile",
        "priority": "high",
        "items": [
            {"sku": "BOOK-10", "quantity": 2},
            {"sku": "BOOK-20", "quantity": 1}
        ]
    }'::jsonb,
    ARRAY['new', 'campaign']
);
~~~

Thực hành:

1. Lấy source và priority bằng ->>.
2. Tìm payload priority high bằng @>.
3. Tìm payload có key items bằng ?.
4. Mở rộng items thành row bằng jsonb_array_elements.
5. Tính tổng quantity theo order.
6. Unnest tags.
7. Tạo GIN index cho payload.
8. Tạo GIN index cho tags.
9. So sánh GIN với expression index trên source.
10. Dùng EXPLAIN ANALYZE trước và sau index.
11. Cập nhật priority bằng jsonb_set.
12. Migrate source sang generated column hoặc column riêng.

---

## 25. Bài tập thực hành

### Bài 1 — JSONB extraction

Tạo bảng profiles có metadata source, plan và nested preferences. Viết query lấy các field dạng text.

### Bài 2 — Containment

Tìm orders có priority high và coupon code WELCOME bằng @>.

### Bài 3 — Key existence

Tìm record có một trong các key source, campaign hoặc referral.

### Bài 4 — JSON array

Mở rộng payload items thành item row, lấy sku và quantity.

### Bài 5 — jsonb_to_recordset

Dùng jsonb_to_recordset để chuyển danh sách item thành các column có type.

### Bài 6 — jsonb_set

Cập nhật nested discount_percent mà không thay các key khác.

### Bài 7 — GIN

Tạo GIN index và so sánh EXPLAIN trước/sau query containment.

### Bài 8 — Expression index

Tạo expression index cho metadata source và so sánh với GIN.

### Bài 9 — Array

Tạo users.roles, dùng ANY, containment và unnest.

### Bài 10 — Thiết kế hybrid

Thiết kế orders có các field chính dạng column và provider payload dạng JSONB. Giải thích field nào không nên để trong JSONB.

### Bài 11 — Migration

Migrate source từ JSONB sang column riêng với backfill, CHECK và index.

---

## 26. Tiêu chí hoàn thành Tuần 5

Bạn hoàn thành bài học khi có thể:

- Phân biệt JSON và JSONB.
- Dùng ->, ->>, #>, #>> đúng mục đích.
- Dùng containment @>, key existence ?/?|/?&.
- Dùng JSON path ở mức thực hành.
- Dùng jsonb_set, xóa key và hiểu giới hạn của ||.
- Mở rộng JSON array bằng jsonb_array_elements.
- Dùng jsonb_to_record và jsonb_to_recordset.
- Tạo và kiểm tra GIN index cho JSONB.
- Biết khi nào expression index hoặc generated column phù hợp hơn GIN.
- Dùng array, ANY, containment và unnest.
- Biết array không thay thế bảng quan hệ N:N.
- Chọn field quan hệ thay cho JSONB khi cần JOIN, constraint hoặc aggregate.
- Thiết kế schema hybrid có source of truth rõ.
- Migrate field từ JSONB sang column an toàn.
- Đo query bằng EXPLAIN ANALYZE trước và sau index.

## Tóm tắt cần nhớ

~~~text
JSONB phù hợp với metadata linh hoạt, không phải toàn bộ schema.
-> trả JSONB; ->> trả text.
@> kiểm tra containment; ? kiểm tra key.
jsonb_array_elements đổi một array thành nhiều row.
jsonb_set cập nhật path và tạo giá trị JSONB mới.
GIN phù hợp với containment nhưng làm write và storage tốn hơn.
Expression index phù hợp với scalar key được query thường xuyên.
Generated column làm field ổn định dễ query hơn.
Array dùng cho danh sách đơn giản thuộc một row.
Quan hệ N:N cần bảng trung gian có foreign key.
Field nghiệp vụ ổn định nên là column có type và constraint.
Luôn xác định grain sau khi mở rộng JSON hoặc array.
~~~

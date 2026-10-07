# Tuần 11 nâng cao: PL/pgSQL, function, procedure và trigger trong PostgreSQL

> Mục tiêu: viết được PL/pgSQL function có hợp đồng rõ ràng, procedure cho batch job, xử lý lỗi có chủ đích, dùng dynamic SQL an toàn, thiết kế trigger đúng chỗ và kiểm soát quyền thực thi, transaction, recursion và hiệu năng.

PL/pgSQL là ngôn ngữ thủ tục chạy trong PostgreSQL. Nó bổ sung biến, rẽ nhánh, vòng lặp, exception và dynamic SQL cho SQL thuần:

~~~text
SQL thuần       → biểu diễn tập dữ liệu và phép biến đổi
Function        → tính toán hoặc trả về tập kết quả
Procedure       → workflow nhiều bước, thường có side effect
Trigger         → phản ứng tự động với thay đổi dữ liệu
~~~

Đặt logic gần dữ liệu có lợi khi logic cần tính nhất quán, được nhiều ứng dụng dùng chung hoặc phải chạy trong cùng transaction. Tuy nhiên, không nên đưa toàn bộ business logic vào trigger. Mỗi routine cần có API, quyền, cách quan sát lỗi và kế hoạch rollback rõ ràng.

---

## 1. Môi trường và schema dùng chung

Các ví dụ dùng PostgreSQL 18 và có thể chạy bằng psql:

~~~bash
psql -h localhost -U postgres -d lab
~~~

Tạo schema bán hàng:

~~~sql
DROP SCHEMA IF EXISTS shop CASCADE;
CREATE SCHEMA shop;

CREATE TABLE shop.customers (
    customer_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email text NOT NULL UNIQUE,
    full_name text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE shop.products (
    product_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sku text NOT NULL UNIQUE,
    product_name text NOT NULL,
    unit_price numeric(12, 2) NOT NULL CHECK (unit_price >= 0),
    stock_quantity integer NOT NULL DEFAULT 0 CHECK (stock_quantity >= 0),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE shop.orders (
    order_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id bigint NOT NULL REFERENCES shop.customers(customer_id),
    status text NOT NULL DEFAULT 'pending'
        CHECK (status IN ('pending', 'paid', 'cancelled', 'completed')),
    total_amount numeric(12, 2) NOT NULL DEFAULT 0
        CHECK (total_amount >= 0),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE shop.order_items (
    order_id bigint NOT NULL REFERENCES shop.orders(order_id)
        ON DELETE CASCADE,
    product_id bigint NOT NULL REFERENCES shop.products(product_id),
    quantity integer NOT NULL CHECK (quantity > 0),
    unit_price numeric(12, 2) NOT NULL CHECK (unit_price >= 0),
    PRIMARY KEY (order_id, product_id)
);

CREATE TABLE shop.price_audit (
    audit_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id bigint NOT NULL,
    old_price numeric(12, 2),
    new_price numeric(12, 2),
    changed_by name NOT NULL DEFAULT current_user,
    changed_at timestamptz NOT NULL DEFAULT clock_timestamp()
);

INSERT INTO shop.customers (email, full_name)
VALUES ('an@example.com', 'An Nguyen'),
       ('binh@example.com', 'Binh Tran');

INSERT INTO shop.products (sku, product_name, unit_price, stock_quantity)
VALUES ('KB-001', 'Keyboard', 750000, 20),
       ('MS-001', 'Mouse', 250000, 50),
       ('MN-001', 'Monitor', 4500000, 5);
~~~

Dòng DROP SCHEMA chỉ dành cho lab chạy lại từ đầu. Migration production phải có backup/restore plan, dependency check và rollback plan.

---

## 2. Function và procedure

### 2.1 Function

Function trả về một giá trị hoặc một tập kết quả. Nó có thể được gọi trong SELECT, WHERE, JOIN hoặc biểu thức.

Function đơn giản nên dùng LANGUAGE sql thay vì PL/pgSQL:

~~~sql
CREATE OR REPLACE FUNCTION shop.add_tax(
    p_amount numeric,
    p_rate numeric DEFAULT 0.10
)
RETURNS numeric
LANGUAGE sql
IMMUTABLE
RETURNS NULL ON NULL INPUT
AS $$
    SELECT round(p_amount * (1 + p_rate), 2);
$$;

SELECT shop.add_tax(100000, 0.08);
~~~

### 2.2 Procedure

Procedure được gọi bằng CALL và phù hợp với command hoặc batch job:

~~~sql
CREATE OR REPLACE PROCEDURE shop.mark_old_pending_orders(
    p_before timestamptz
)
LANGUAGE plpgsql
AS $$
BEGIN
    UPDATE shop.orders
    SET status = 'cancelled',
        updated_at = now()
    WHERE status = 'pending'
      AND created_at < p_before;
END;
$$;

CALL shop.mark_old_pending_orders(now() - interval '30 days');
~~~

So sánh:

| Đặc điểm | Function | Procedure |
|---|---|---|
| Cách gọi | SELECT hoặc biểu thức | CALL |
| Kết quả | Có RETURNS | Không dùng RETURNS |
| Dùng trực tiếp trong query | Có | Không |
| Trigger gọi được | Có, nếu là trigger function | Không |
| Side effect | Có thể có nhưng phải ghi rõ | Thường là mục tiêu chính |
| Transaction control | Không tự COMMIT/ROLLBACK | Có thể commit trong ngữ cảnh phù hợp |

Function có side effect không nên đặt tên như phép tính thuần. Tên recalculate_and_store_total trung thực hơn calculate_total nếu function có UPDATE.

---

## 3. Cấu trúc PL/pgSQL và biến

Block cơ bản:

~~~sql
DECLARE
    -- biến cục bộ
BEGIN
    -- câu lệnh
EXCEPTION
    WHEN ... THEN
        -- xử lý lỗi
END;
~~~

BEGIN và END trong PL/pgSQL là block, không phải transaction boundary.

Ví dụ dùng phần trăm kiểu của column:

~~~sql
CREATE OR REPLACE FUNCTION shop.order_label(p_order_id bigint)
RETURNS text
LANGUAGE plpgsql
STABLE
AS $$
DECLARE
    v_status shop.orders.status%TYPE;
    v_total shop.orders.total_amount%TYPE;
BEGIN
    SELECT status, total_amount
    INTO STRICT v_status, v_total
    FROM shop.orders
    WHERE order_id = p_order_id;

    RETURN format(
        'order=%s status=%s total=%s',
        p_order_id, v_status, v_total
    );
EXCEPTION
    WHEN no_data_found THEN
        RAISE EXCEPTION 'Không tìm thấy order_id=%', p_order_id
            USING ERRCODE = 'P0002';
END;
$$;
~~~

INTO STRICT yêu cầu đúng một row:

- không có row: no_data_found;
- nhiều hơn một row: too_many_rows;
- không có STRICT: biến nhận kết quả theo semantics của câu lệnh.

Khai báo biến:

~~~sql
DECLARE
    v_total numeric(12, 2) := 0;
    v_product shop.products%ROWTYPE;
    v_row record;
BEGIN
    ...
END;
~~~

%TYPE lấy kiểu của một column. %ROWTYPE lấy cấu trúc của cả row. record linh hoạt hơn nhưng chỉ có field sau khi được gán từ query.

Gán giá trị và kiểm tra số row:

~~~sql
v_total := v_total + 100;

UPDATE shop.products
SET updated_at = now()
WHERE stock_quantity = 0;

GET DIAGNOSTICS v_affected = ROW_COUNT;
RAISE NOTICE 'Đã cập nhật % sản phẩm', v_affected;
~~~

FOUND cho biết câu lệnh trước có tìm/thay đổi row hay không:

~~~sql
SELECT product_name INTO v_name
FROM shop.products
WHERE product_id = p_product_id;

IF NOT FOUND THEN
    RAISE EXCEPTION 'Không có sản phẩm';
END IF;
~~~

---

## 4. Parameter mode và RETURNS TABLE

### 4.1 IN, OUT, INOUT

IN là mặc định:

~~~sql
CREATE OR REPLACE FUNCTION shop.discounted_price(
    p_price numeric,
    p_discount_percent numeric
)
RETURNS numeric
LANGUAGE plpgsql
IMMUTABLE
RETURNS NULL ON NULL INPUT
AS $$
BEGIN
    IF p_discount_percent < 0 OR p_discount_percent > 100 THEN
        RAISE EXCEPTION
            'Discount phải nằm trong [0, 100], nhận được %',
            p_discount_percent
            USING ERRCODE = '22023';
    END IF;

    RETURN round(p_price * (1 - p_discount_percent / 100), 2);
END;
$$;
~~~

OUT trở thành cột kết quả:

~~~sql
CREATE OR REPLACE FUNCTION shop.customer_order_summary(
    p_customer_id bigint,
    OUT order_count bigint,
    OUT paid_amount numeric
)
LANGUAGE plpgsql
STABLE
AS $$
BEGIN
    SELECT
        count(*) FILTER (WHERE status = 'paid'),
        coalesce(sum(total_amount) FILTER (WHERE status = 'paid'), 0)
    INTO order_count, paid_amount
    FROM shop.orders
    WHERE customer_id = p_customer_id;
END;
$$;

SELECT * FROM shop.customer_order_summary(1);
~~~

INOUT nhận giá trị ban đầu và trả giá trị sau khi function kết thúc:

~~~sql
CREATE OR REPLACE FUNCTION shop.add_to_counter(
    INOUT p_counter integer,
    p_amount integer
)
LANGUAGE plpgsql
IMMUTABLE
AS $$
BEGIN
    p_counter := p_counter + p_amount;
    RETURN;
END;
$$;
~~~

VARIADIC gom nhiều argument cùng kiểu thành array:

~~~sql
CREATE OR REPLACE FUNCTION shop.sum_amounts(
    VARIADIC p_values numeric[]
)
RETURNS numeric
LANGUAGE sql
IMMUTABLE
AS $$
    SELECT coalesce(sum(value), 0)
    FROM unnest(p_values) AS value;
$$;

SELECT shop.sum_amounts(10, 20, 30);
~~~

### 4.2 Function trả nhiều row

RETURNS TABLE phù hợp với result set có schema ổn định:

~~~sql
CREATE OR REPLACE FUNCTION shop.find_low_stock_products(
    p_threshold integer DEFAULT 10
)
RETURNS TABLE (
    product_id bigint,
    sku text,
    product_name text,
    stock_quantity integer
)
LANGUAGE plpgsql
STABLE
AS $$
BEGIN
    IF p_threshold < 0 THEN
        RAISE EXCEPTION 'Threshold không được âm'
            USING ERRCODE = '22023';
    END IF;

    RETURN QUERY
    SELECT p.product_id, p.sku, p.product_name, p.stock_quantity
    FROM shop.products AS p
    WHERE p.stock_quantity <= p_threshold
    ORDER BY p.stock_quantity, p.product_id;
END;
$$;

SELECT * FROM shop.find_low_stock_products(10);
~~~

Các lệnh trả kết quả:

- RETURN expression: trả một giá trị;
- RETURN NEXT row: thêm một row;
- RETURN QUERY query: thêm toàn bộ kết quả query;
- RETURN QUERY EXECUTE: thêm kết quả dynamic SQL;
- RETURN: kết thúc function.

Với tập lớn, ưu tiên RETURN QUERY hoặc SQL set-based thay vì loop rồi RETURN NEXT từng row.

---

## 5. Control flow

### IF, CASE và NULL

~~~sql
IF p_amount <= 0 THEN
    RAISE EXCEPTION 'Amount phải lớn hơn 0';
ELSIF p_amount >= 10000000 THEN
    RAISE NOTICE 'Giao dịch lớn';
ELSE
    RAISE NOTICE 'Giao dịch thường';
END IF;

v_label := CASE
    WHEN v_total = 0 THEN 'empty'
    WHEN v_total < 1000000 THEN 'small'
    ELSE 'large'
END;
~~~

So sánh với NULL cho ra NULL, không phải TRUE/FALSE. Dùng IS NULL hoặc IS DISTINCT FROM khi cần:

~~~sql
IF p_value IS NULL THEN
    ...
END IF;

IF OLD.unit_price IS DISTINCT FROM NEW.unit_price THEN
    ...
END IF;
~~~

### Loop

~~~sql
FOR v_product IN
    SELECT product_id, stock_quantity
    FROM shop.products
    WHERE stock_quantity < 10
LOOP
    RAISE NOTICE 'product=% stock=%',
        v_product.product_id, v_product.stock_quantity;
END LOOP;

FOR i IN 1..10 LOOP
    RAISE NOTICE 'i=%', i;
END LOOP;

LOOP
    EXIT WHEN v_done;
    CONTINUE WHEN v_row IS NULL;
    ...
END LOOP;
~~~

Luôn xem xét query set-based trước khi viết loop. Loop phù hợp khi mỗi row có workflow khác nhau; không phù hợp chỉ để thay một UPDATE hoặc aggregate đơn giản.

---

## 6. Exception handling

~~~sql
BEGIN
    INSERT INTO shop.customers (email, full_name)
    VALUES (p_email, p_name);
EXCEPTION
    WHEN unique_violation THEN
        RAISE EXCEPTION 'Email đã tồn tại: %', p_email
            USING ERRCODE = 'P0001';
END;
~~~

Block có EXCEPTION hoạt động như subtransaction. Khi lỗi xảy ra, thay đổi trong block rollback nhưng thay đổi trước block vẫn thuộc transaction chính.

Bắt lỗi cụ thể trước lỗi chung:

~~~sql
EXCEPTION
    WHEN unique_violation THEN
        ...
    WHEN foreign_key_violation THEN
        ...
    WHEN check_violation THEN
        ...
    WHEN OTHERS THEN
        RAISE;
END;
~~~

Không dùng WHEN OTHERS THEN NULL. Nếu cần log rồi ném lại:

~~~sql
EXCEPTION
    WHEN OTHERS THEN
        RAISE WARNING 'Function lỗi: %, SQLSTATE=%',
            SQLERRM, SQLSTATE;
        RAISE;
END;
~~~

Lấy chi tiết lỗi:

~~~sql
DECLARE
    v_state text;
    v_message text;
    v_detail text;
BEGIN
    ...
EXCEPTION
    WHEN OTHERS THEN
        GET STACKED DIAGNOSTICS
            v_state = RETURNED_SQLSTATE,
            v_message = MESSAGE_TEXT,
            v_detail = PG_EXCEPTION_DETAIL;
        RAISE EXCEPTION
            'state=%, message=%, detail=%',
            v_state, v_message, v_detail;
END;
~~~

Chỉ expose thông tin phù hợp cho caller. Chi tiết nội bộ không nên trả nguyên stack trace cho người dùng cuối.

---

## 7. Function tính order total

Giá bán lịch sử phải nằm trong order_items.unit_price, không đọc giá hiện tại trong products:

~~~sql
CREATE OR REPLACE FUNCTION shop.calculate_order_total(
    p_order_id bigint
)
RETURNS numeric(12, 2)
LANGUAGE sql
STABLE
RETURNS NULL ON NULL INPUT
AS $$
    SELECT coalesce(
        sum(oi.quantity * oi.unit_price),
        0
    )::numeric(12, 2)
    FROM shop.order_items AS oi
    WHERE oi.order_id = p_order_id;
$$;
~~~

Function cập nhật tổng đã lưu:

~~~sql
CREATE OR REPLACE FUNCTION shop.recalculate_order_total(
    p_order_id bigint
)
RETURNS numeric(12, 2)
LANGUAGE plpgsql
VOLATILE
AS $$
DECLARE
    v_total numeric(12, 2);
BEGIN
    PERFORM 1
    FROM shop.orders
    WHERE order_id = p_order_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Không tìm thấy order_id=%', p_order_id
            USING ERRCODE = 'P0002';
    END IF;

    v_total := shop.calculate_order_total(p_order_id);

    UPDATE shop.orders
    SET total_amount = v_total,
        updated_at = now()
    WHERE order_id = p_order_id;

    RETURN v_total;
END;
$$;
~~~

FOR UPDATE bảo vệ row order trước khi cập nhật. Nếu các workflow lock table theo thứ tự khác nhau, vẫn có thể deadlock. Chọn lock order nhất quán và retry ở application khi phù hợp.

---

## 8. Dynamic SQL an toàn

Dynamic SQL cần thiết khi schema/table/column thay đổi ở runtime. Phân biệt:

| Thành phần | Ví dụ | Cách an toàn |
|---|---|---|
| Identifier | schema, table, column | format với %I |
| Value/literal | email, status, số | placeholder và USING |

Ví dụ:

~~~sql
CREATE OR REPLACE FUNCTION shop.count_rows(
    p_schema text,
    p_table text,
    p_status text DEFAULT NULL
)
RETURNS bigint
LANGUAGE plpgsql
STABLE
AS $$
DECLARE
    v_count bigint;
    v_sql text;
BEGIN
    v_sql := format(
        'SELECT count(*) FROM %I.%I
         WHERE ($1 IS NULL OR status = $1)',
        p_schema, p_table
    );

    EXECUTE v_sql INTO v_count USING p_status;
    RETURN v_count;
END;
$$;
~~~

Không nối input vào SQL:

~~~sql
-- Sai:
v_sql := '... WHERE status = ''' || p_status || '''';
~~~

Whitelist vẫn cần thiết. %I ngăn identifier injection nhưng không chứng minh table đó là table được phép truy cập:

~~~sql
IF p_sort_column NOT IN ('created_at', 'total_amount') THEN
    RAISE EXCEPTION 'Sort column không được phép: %', p_sort_column
        USING ERRCODE = '22023';
END IF;

v_sql := format(
    'SELECT order_id, total_amount
     FROM shop.orders
     ORDER BY %I %s
     LIMIT $1',
    p_sort_column,
    CASE WHEN p_desc THEN 'DESC' ELSE 'ASC' END
);
~~~

ASC/DESC không bind như value được; tạo chúng từ boolean đã kiểm soát hoặc whitelist. Không nhận nguyên fragment ORDER BY từ client.

---

## 9. SECURITY INVOKER và SECURITY DEFINER

Function mặc định là SECURITY INVOKER, chạy với quyền của caller. Đây thường là lựa chọn an toàn nhất.

SECURITY DEFINER chạy với quyền owner function, phù hợp khi expose API hẹp mà không cấp quyền trực tiếp trên table:

~~~sql
CREATE OR REPLACE FUNCTION shop.public_order_total(p_order_id bigint)
RETURNS numeric
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = shop, pg_temp
AS $$
    SELECT total_amount
    FROM shop.orders
    WHERE order_id = p_order_id;
$$;

REVOKE ALL ON FUNCTION shop.public_order_total(bigint) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION shop.public_order_total(bigint) TO app_runtime;
~~~

Nguyên tắc với SECURITY DEFINER:

1. Khóa search_path, thường là trusted_schema, pg_temp.
2. Gọi object quan trọng bằng tên schema-qualified.
3. Không nối input vào dynamic SQL.
4. Cấp EXECUTE tối thiểu và thu hồi khỏi PUBLIC nếu không public.
5. Owner không nên là superuser nếu không cần.
6. Kiểm tra caller được xem/sửa dữ liệu nào.
7. Error không được làm lộ dữ liệu nhạy cảm.

Quên signature trong GRANT/REVOKE là lỗi phổ biến. PostgreSQL định danh function theo tên và kiểu tham số.

---

## 10. Trigger function

Trigger gọi một function đặc biệt trả về trigger:

~~~sql
CREATE OR REPLACE FUNCTION shop.set_product_updated_at()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$$;

CREATE TRIGGER products_set_updated_at
BEFORE UPDATE ON shop.products
FOR EACH ROW
WHEN (OLD.* IS DISTINCT FROM NEW.*)
EXECUTE FUNCTION shop.set_product_updated_at();
~~~

Trong row-level trigger:

- NEW là row sau thay đổi;
- OLD là row trước thay đổi;
- TG_OP là INSERT, UPDATE hoặc DELETE;
- TG_NAME là tên trigger;
- TG_TABLE_NAME và TG_TABLE_SCHEMA mô tả table.

Giá trị return:

| Trigger | Ý nghĩa |
|---|---|
| BEFORE INSERT/UPDATE trả NEW | cho phép hoặc sửa row |
| BEFORE INSERT/UPDATE trả NULL | bỏ qua row |
| BEFORE DELETE trả OLD | cho phép xóa |
| AFTER | return bị bỏ qua |
| INSTEAD OF trên view | function thực hiện thao tác thay thế |

BEFORE phù hợp để chuẩn hóa hoặc từ chối row. AFTER phù hợp cho audit và event sau khi row đã ghi thành công. INSTEAD OF phù hợp cho view có mapping DML rõ ràng.

---

## 11. Trigger audit thay đổi giá

~~~sql
CREATE OR REPLACE FUNCTION shop.audit_product_price_change()
RETURNS trigger
LANGUAGE plpgsql
SECURITY INVOKER
AS $$
BEGIN
    IF NEW.unit_price IS DISTINCT FROM OLD.unit_price THEN
        INSERT INTO shop.price_audit (
            product_id, old_price, new_price, changed_by
        )
        VALUES (
            OLD.product_id,
            OLD.unit_price,
            NEW.unit_price,
            current_user
        );
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER products_audit_price
AFTER UPDATE OF unit_price ON shop.products
FOR EACH ROW
WHEN (OLD.unit_price IS DISTINCT FROM NEW.unit_price)
EXECUTE FUNCTION shop.audit_product_price_change();
~~~

Test:

~~~sql
UPDATE shop.products
SET unit_price = unit_price + 10000
WHERE sku = 'MS-001';

SELECT *
FROM shop.price_audit
ORDER BY audit_id DESC;
~~~

Audit nằm trong cùng transaction. Nếu update rollback, audit cũng rollback. Đây thường là điều đúng với audit nghiệp vụ. Nếu cần log không thể rollback cùng transaction, cần hệ thống log bên ngoài hoặc thiết kế riêng.

current_user là user thực thi theo quyền hiện tại; session_user là user mở session. Trong SECURITY DEFINER, hai giá trị có thể khác nhau.

---

## 12. Row-level, statement-level và transition table

Row-level trigger chạy một lần cho mỗi row:

~~~sql
CREATE TRIGGER products_audit_price
AFTER UPDATE OF unit_price ON shop.products
FOR EACH ROW
EXECUTE FUNCTION shop.audit_product_price_change();
~~~

Một UPDATE 10.000 row có thể gọi function 10.000 lần. Chi phí phải tính theo số row, không phải số câu lệnh trong code.

Statement-level trigger chạy một lần cho statement:

~~~sql
CREATE TABLE shop.change_batch_audit (
    audit_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    table_name text NOT NULL,
    operation text NOT NULL,
    changed_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    changed_by name NOT NULL DEFAULT current_user
);

CREATE OR REPLACE FUNCTION shop.audit_product_statement()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    INSERT INTO shop.change_batch_audit (table_name, operation)
    VALUES (TG_TABLE_SCHEMA || '.' || TG_TABLE_NAME, TG_OP);
    RETURN NULL;
END;
$$;

CREATE TRIGGER products_statement_audit
AFTER INSERT OR UPDATE OR DELETE ON shop.products
FOR EACH STATEMENT
EXECUTE FUNCTION shop.audit_product_statement();
~~~

Transition table cho phép xử lý tập row sau một statement:

~~~sql
CREATE TABLE shop.product_change_summary (
    summary_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    changed_count bigint NOT NULL,
    changed_at timestamptz NOT NULL DEFAULT clock_timestamp()
);

CREATE OR REPLACE FUNCTION shop.summarize_product_updates()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    INSERT INTO shop.product_change_summary (changed_count)
    SELECT count(*) FROM new_rows;
    RETURN NULL;
END;
$$;

CREATE TRIGGER products_update_summary
AFTER UPDATE ON shop.products
REFERENCING NEW TABLE AS new_rows
FOR EACH STATEMENT
EXECUTE FUNCTION shop.summarize_product_updates();
~~~

Statement-level phù hợp batch-level event. Row-level phù hợp audit từng row. Transition table phù hợp aggregate tập row mà không loop từng row.

---

## 13. Trigger cập nhật order total và trade-off

Có thể cập nhật tổng order sau mỗi thay đổi item:

~~~sql
CREATE OR REPLACE FUNCTION shop.refresh_order_total_from_item()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_order_id bigint;
BEGIN
    v_order_id := CASE
        WHEN TG_OP = 'DELETE' THEN OLD.order_id
        ELSE NEW.order_id
    END;

    UPDATE shop.orders AS o
    SET total_amount = shop.calculate_order_total(o.order_id),
        updated_at = now()
    WHERE o.order_id = v_order_id;

    RETURN CASE
        WHEN TG_OP = 'DELETE' THEN OLD
        ELSE NEW
    END;
END;
$$;

CREATE TRIGGER order_items_refresh_total
AFTER INSERT OR UPDATE OR DELETE ON shop.order_items
FOR EACH ROW
EXECUTE FUNCTION shop.refresh_order_total_from_item();
~~~

Rủi ro:

- nhiều item trong một statement làm update order nhiều lần;
- UPDATE đổi order_id cần refresh cả order cũ và mới;
- trigger chain có thể recursion;
- transaction khác lock order theo thứ tự khác gây deadlock;
- bulk load chậm mạnh.

Các phương án khác: tính total khi đọc, cập nhật set-based sau batch, queue event, transition table hoặc summary table có refresh rõ ràng. Trigger không xấu; trigger không được kiểm soát mới nguy hiểm.

---

## 14. Procedure xử lý batch job

Tạo bảng log:

~~~sql
CREATE TABLE shop.batch_run (
    batch_run_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    job_name text NOT NULL,
    started_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    finished_at timestamptz,
    processed_count bigint,
    status text NOT NULL CHECK (status IN ('running', 'succeeded', 'failed')),
    error_message text
);
~~~

Procedure hủy order pending cũ:

~~~sql
CREATE OR REPLACE PROCEDURE shop.cancel_stale_orders(
    p_before timestamptz,
    p_batch_size integer DEFAULT 500
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_run_id bigint;
    v_count bigint;
BEGIN
    IF p_batch_size <= 0 THEN
        RAISE EXCEPTION 'p_batch_size phải lớn hơn 0'
            USING ERRCODE = '22023';
    END IF;

    INSERT INTO shop.batch_run (job_name)
    VALUES ('cancel_stale_orders')
    RETURNING batch_run_id INTO v_run_id;

    WITH candidates AS (
        SELECT order_id
        FROM shop.orders
        WHERE status = 'pending'
          AND created_at < p_before
        ORDER BY order_id
        FOR UPDATE SKIP LOCKED
        LIMIT p_batch_size
    )
    UPDATE shop.orders AS o
    SET status = 'cancelled',
        updated_at = now()
    FROM candidates AS c
    WHERE o.order_id = c.order_id;

    GET DIAGNOSTICS v_count = ROW_COUNT;

    UPDATE shop.batch_run
    SET finished_at = clock_timestamp(),
        processed_count = v_count,
        status = 'succeeded'
    WHERE batch_run_id = v_run_id;
EXCEPTION
    WHEN OTHERS THEN
        UPDATE shop.batch_run
        SET finished_at = clock_timestamp(),
            status = 'failed',
            error_message = SQLERRM
        WHERE batch_run_id = v_run_id;
        RAISE;
END;
$$;
~~~

FOR UPDATE SKIP LOCKED hữu ích khi nhiều worker cùng xử lý queue. Nó cho phép bỏ qua row đang lock, nên không phù hợp nếu nghiệp vụ yêu cầu đọc đủ và tuyệt đối theo thứ tự.

Procedure có thể COMMIT/ROLLBACK trong một số ngữ cảnh, nhưng không được tùy tiện commit trong function hoặc trigger. Commit theo chunk tạo transaction độc lập:

- chunk trước không rollback được khi chunk sau lỗi;
- lock giải phóng sau mỗi commit;
- dữ liệu có thể thay đổi giữa các chunk;
- job cần idempotent và có khả năng resume.

---

## 15. Transaction, retry và idempotency

Mỗi batch job cần trả lời:

1. Chạy hai lần có nhân đôi side effect không?
2. Lỗi ở chunk thứ ba thì chạy lại từ đâu?
3. Row nào đã lock và lock bao lâu?
4. Có retry deadlock/serialization failure không?
5. Có bảng run log để quan sát không?

Advisory lock có thể ngăn hai instance cùng chạy:

~~~sql
SELECT pg_try_advisory_lock(hashtext('shop.daily_reconcile'));
-- xử lý job
SELECT pg_advisory_unlock(hashtext('shop.daily_reconcile'));
~~~

Advisory lock chỉ là primitive, không lưu trạng thái nghiệp vụ. Nên có thêm bảng job/run, trạng thái, checkpoint và timestamp.

Một batch tốt cần:

- khóa duy nhất cho event đã xử lý;
- trạng thái running/succeeded/failed;
- checkpoint hoặc cursor;
- retry có giới hạn;
- metric số row và thời gian;
- log đủ để xác định command gây lỗi.

---

## 16. Volatility và null behavior

Khai báo volatility giúp PostgreSQL biết function có thể tối ưu thế nào:

| Volatility | Ý nghĩa |
|---|---|
| IMMUTABLE | cùng input luôn cùng output, không đọc dữ liệu thay đổi |
| STABLE | kết quả ổn định trong một statement, có thể đọc database |
| VOLATILE | có thể thay đổi mỗi lần gọi, có side effect hoặc đọc trạng thái thay đổi |

Ví dụ:

~~~sql
-- Chỉ tính từ input
CREATE FUNCTION shop.add_tax(numeric, numeric)
RETURNS numeric
LANGUAGE sql
IMMUTABLE
...

-- Đọc table, không ghi
CREATE FUNCTION shop.calculate_order_total(bigint)
RETURNS numeric
LANGUAGE sql
STABLE
...

-- UPDATE, audit, random hoặc current time
CREATE FUNCTION shop.recalculate_order_total(bigint)
RETURNS numeric
LANGUAGE plpgsql
VOLATILE
...
~~~

Khai báo IMMUTABLE sai có thể tạo kết quả sai vì planner được phép tối ưu dựa trên cam kết đó. Không khai báo IMMUTABLE chỉ vì muốn query nhanh.

STRICT hoặc RETURNS NULL ON NULL INPUT làm function trả NULL ngay khi input cần thiết là NULL. Chọn rõ:

- null nghĩa là thiếu dữ liệu: trả null;
- null quy về 0: dùng coalesce có chủ đích;
- null là input sai: raise exception.

---

## 17. Search path, overload và quyền

Trong code, ưu tiên gọi object bằng tên schema-qualified:

~~~sql
SELECT shop.calculate_order_total(p_order_id);
~~~

Kiểm tra routine:

~~~sql
SELECT
    n.nspname AS schema_name,
    p.proname,
    pg_get_function_identity_arguments(p.oid) AS arguments,
    l.lanname AS language_name,
    p.prosecdef AS security_definer
FROM pg_proc AS p
JOIN pg_namespace AS n ON n.oid = p.pronamespace
JOIN pg_language AS l ON l.oid = p.prolang
WHERE n.nspname = 'shop'
ORDER BY p.proname;
~~~

Hai function cùng tên khác parameter signature là overload. Khi có ambiguity, truyền kiểu rõ ràng:

~~~sql
SELECT shop.some_function(1::bigint);
~~~

Function API cần quyền tối thiểu:

~~~sql
REVOKE ALL ON FUNCTION shop.calculate_order_total(bigint) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION shop.calculate_order_total(bigint) TO app_runtime;
~~~

Migration phải kiểm tra dependency. CREATE OR REPLACE không phải lúc nào cũng thay được function khi đổi kiểu return hoặc signature. Không DROP CASCADE nếu chưa biết object nào phụ thuộc.

---

## 18. Quản lý trigger và dependency

Liệt kê trigger:

~~~sql
SELECT
    n.nspname AS schema_name,
    c.relname AS table_name,
    t.tgname AS trigger_name,
    pg_get_triggerdef(t.oid) AS definition,
    t.tgenabled
FROM pg_trigger AS t
JOIN pg_class AS c ON c.oid = t.tgrelid
JOIN pg_namespace AS n ON n.oid = c.relnamespace
WHERE NOT t.tgisinternal
  AND n.nspname = 'shop'
ORDER BY c.relname, t.tgname;
~~~

Tắt trigger tạm thời:

~~~sql
ALTER TABLE shop.products DISABLE TRIGGER products_audit_price;
~~~

Đây là thao tác nguy hiểm vì dữ liệu có thể thay đổi mà không audit. Cần runbook bật lại, kiểm tra khoảng thời gian bị bỏ sót và backfill nếu cần. Tránh DISABLE TRIGGER ALL trên production nếu chưa hiểu foreign key và toàn bộ dependency.

Xóa trigger:

~~~sql
DROP TRIGGER IF EXISTS products_audit_price ON shop.products;
~~~

---

## 19. Các lỗi thường gặp

### Đưa toàn bộ business logic vào trigger

Một INSERT gây nhiều side effect ẩn, khó test, khó debug và làm bulk load chậm. Chỉ dùng trigger cho invariant, audit, timestamp hoặc phản ứng thật sự cần ở database.

### Nối input vào dynamic SQL

Identifier phải đi qua format với %I; value phải đi qua USING. Nhưng vẫn cần whitelist để giới hạn schema/table/column được phép.

### SECURITY DEFINER không khóa search_path

Object trùng tên trong schema của attacker có thể bị resolve thay cho object tin cậy. Khóa search_path và schema-qualify object quan trọng.

### Nuốt exception

WHEN OTHERS THEN NULL làm mất tín hiệu lỗi. Xử lý lỗi cụ thể, log context cần thiết và RAISE lại nếu caller phải biết.

### Loop thay cho set-based SQL

Loop row-by-row tăng overhead và có thể tạo lock pattern xấu. Viết UPDATE, INSERT SELECT, aggregate hoặc RETURN QUERY trước.

### Trigger recursion

Trigger cập nhật chính table có thể gọi lại chính nó. Dùng WHEN để chỉ chạy khi field liên quan thay đổi và thiết kế chain rõ ràng. pg_trigger_depth chỉ nên là lưới an toàn, không phải cách chữa recursion.

### Procedure không idempotent

Retry sau lỗi có thể tạo duplicate audit, trừ tiền hai lần hoặc chuyển trạng thái sai. Dùng unique key, trạng thái job, checkpoint và event id.

### Function side effect mang tên query thuần

Tách API đọc và command ghi. Tên hàm phải cho caller biết có thể xảy ra UPDATE, audit hoặc lock.

---

## 20. Lab hoàn chỉnh

### Bước 1: Tạo order và tính tổng

~~~sql
INSERT INTO shop.orders (customer_id)
VALUES (1)
RETURNING order_id;

-- Giả sử order_id = 1
INSERT INTO shop.order_items (order_id, product_id, quantity, unit_price)
SELECT 1, product_id, 2, unit_price
FROM shop.products
WHERE sku = 'MS-001';

SELECT shop.recalculate_order_total(1);
SELECT * FROM shop.orders WHERE order_id = 1;
~~~

### Bước 2: Tìm sản phẩm tồn kho thấp

~~~sql
SELECT * FROM shop.find_low_stock_products(10);
~~~

### Bước 3: Test audit

~~~sql
UPDATE shop.products
SET unit_price = unit_price + 10000
WHERE sku = 'MS-001';

SELECT product_id, old_price, new_price, changed_by, changed_at
FROM shop.price_audit
ORDER BY audit_id DESC;
~~~

### Bước 4: Chạy batch

~~~sql
CALL shop.cancel_stale_orders(now() - interval '1 hour', 100);

SELECT *
FROM shop.batch_run
ORDER BY batch_run_id DESC;
~~~

### Bước 5: Test lỗi

~~~sql
SELECT shop.recalculate_order_total(999999);
~~~

Kỳ vọng: lỗi có mã P0002, không im lặng trả NULL.

### Bước 6: Test rollback của audit

~~~sql
BEGIN;

UPDATE shop.products
SET unit_price = unit_price + 1
WHERE product_id = 1;

SELECT count(*) FROM shop.price_audit;

ROLLBACK;

-- Giá và audit của transaction vừa rồi không còn.
~~~

### Bước 7: Kiểm tra trigger

~~~sql
SELECT tgname, pg_get_triggerdef(oid)
FROM pg_trigger
WHERE tgrelid = 'shop.products'::regclass
  AND NOT tgisinternal;
~~~

Ghi lại function nào đọc/ghi, trigger nào chạy trước/sau, quyền nào cần, lỗi nào rollback transaction và batch có thể retry hay không.

---

## 21. Kiểm thử function, procedure và trigger

Routine chưa hoàn thành chỉ vì happy path chạy được.

| Nhóm test | Trường hợp |
|---|---|
| Function | input hợp lệ, NULL, 0, âm, rất lớn |
| Function | không có row, nhiều row, quyền thiếu |
| Trigger | insert, update field liên quan, update field không liên quan |
| Trigger | multi-row statement, delete, rollback, recursion |
| Procedure | batch rỗng, đúng một chunk, vượt chunk |
| Procedure | lỗi giữa chừng, retry, hai worker đồng thời |
| Security | search_path, PUBLIC EXECUTE, dynamic input |

Test concurrency bằng hai session. Cố tình tạo lock wait để kiểm tra timeout và deadlock handling. Đo bulk statement bằng EXPLAIN ANALYZE khi phù hợp, nhưng không chạy ANALYZE trên production nếu chưa đánh giá side effect.

---

## 22. Migration và rollback

Migration routine nên:

1. tạo function trong schema đúng;
2. cấp quyền đúng signature;
3. tạo trigger;
4. kiểm tra definition;
5. chạy smoke test;
6. ghi migration down hoặc rollback procedure;
7. theo dõi lỗi sau deploy.

Ví dụ:

~~~sql
BEGIN;

CREATE OR REPLACE FUNCTION shop.set_product_updated_at()
RETURNS trigger
LANGUAGE plpgsql
AS $function$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$function$;

DROP TRIGGER IF EXISTS products_set_updated_at ON shop.products;

CREATE TRIGGER products_set_updated_at
BEFORE UPDATE ON shop.products
FOR EACH ROW
EXECUTE FUNCTION shop.set_product_updated_at();

COMMIT;
~~~

Với function/trigger, deploy trong transaction thường thuận tiện. Với routine đang được caller dùng, trigger nhiều side effect hoặc DDL lớn, phải kiểm tra lock, dependency, compatibility và thời gian chuyển đổi.

---

## 23. Checklist review

### API và semantics

- Tên function có phản ánh side effect không?
- Function hay procedure là abstraction phù hợp?
- Input, default và null contract có rõ không?
- RETURNS TABLE có schema ổn định không?

### Correctness

- Constraint đã được dùng làm tuyến phòng thủ chính chưa?
- Có race condition giữa SELECT và UPDATE không?
- Lock order có nhất quán không?
- Retry có tạo duplicate side effect không?
- UPDATE đổi quan hệ có xử lý cả row cũ và mới không?

### Security

- Identifier có %I và whitelist không?
- Value có USING không?
- Có thật sự cần SECURITY DEFINER không?
- search_path đã khóa chưa?
- PUBLIC có quyền execute ngoài ý muốn không?

### Performance

- Có loop row-by-row thay cho set-based SQL không?
- Trigger chạy bao nhiêu lần trong bulk statement?
- Function có làm mất khả năng dùng index trong WHERE không?
- Batch giữ lock/transaction quá lâu không?

### Vận hành

- Có migration down và rollback plan không?
- Có batch log và metric lỗi không?
- Có runbook disable/enable trigger an toàn không?
- Có tài liệu dependency và quyền không?

---

## 24. Bài tập thực hành

### Bài 1 — Function tổng đơn hàng

Viết calculate_order_total:

- trả 0 nếu order chưa có item;
- không cập nhật table;
- khai báo volatility đúng;
- test order không tồn tại và order rỗng.

### Bài 2 — Low-stock report

Trả product_id, SKU, tên, tồn kho và giá trị tồn kho. Thêm threshold, validate input và sort ổn định.

### Bài 3 — Audit giá

Chỉ ghi audit khi giá thật sự thay đổi. Test UPDATE gán lại cùng giá và chứng minh không có audit thừa.

### Bài 4 — Dynamic SQL

Viết function đếm row theo whitelist table. Test input có quote và input:

~~~text
orders; DROP TABLE shop.orders; --
~~~

Input phải bị từ chối hoặc được coi như identifier, không được chạy thành SQL thứ hai.

### Bài 5 — Batch cancel

Dùng p_batch_size, FOR UPDATE SKIP LOCKED, bảng run log và test hai session chạy đồng thời.

### Bài 6 — Transition table

Thay row-level summary bằng statement-level trigger với transition table. So sánh chi phí khi statement đổi 1.000 item.

### Bài 7 — Security definer

Tạo role app_runtime không có SELECT trực tiếp trên orders, nhưng được EXECUTE function chỉ trả order_id và total_amount. Kiểm tra search_path và quyền PUBLIC.

### Bài 8 — Failure injection

Cố ý gây lỗi giữa batch, kiểm tra row rollback, batch_run.status, audit duplicate và khả năng retry.

---

## 25. Tiêu chí hoàn thành Tuần 11

Bạn hoàn thành bài khi có thể:

- phân biệt function và procedure bằng cách gọi và transaction semantics;
- dùng IN, OUT, INOUT, VARIADIC;
- viết function RETURNS TABLE;
- dùng DECLARE, %TYPE, %ROWTYPE và record;
- dùng IF, CASE, loop và RETURN QUERY;
- bắt lỗi cụ thể, đọc SQLSTATE và không nuốt WHEN OTHERS;
- viết dynamic SQL với %I cho identifier và USING cho value;
- giải thích SECURITY INVOKER và SECURITY DEFINER;
- khóa search_path trong security-definer function;
- viết BEFORE, AFTER, INSTEAD OF, row-level và statement-level trigger;
- nhận biết recursion, trigger chain và chi phí bulk statement;
- thiết kế procedure batch có lock, retry, idempotency và run log;
- kiểm thử rollback, concurrent execution và quyền tối thiểu;
- tạo migration và rollback plan cho routine.

---

## Tóm tắt cần nhớ

1. Function trả giá trị/tập kết quả và dùng được trong query; procedure là command gọi bằng CALL.
2. BEGIN ... END của PL/pgSQL là block, không tự tạo transaction.
3. RETURNS TABLE phù hợp với result set có schema rõ.
4. Set-based SQL thường tốt hơn loop row-by-row.
5. Bắt lỗi cụ thể; không dùng WHEN OTHERS THEN NULL.
6. Dynamic identifier dùng %I; dynamic value dùng USING.
7. SECURITY DEFINER phải khóa search_path và cấp quyền tối thiểu.
8. Trigger nên nhỏ, dễ quan sát và phục vụ invariant/audit cần thiết.
9. Row-level chạy theo số row; statement-level chạy theo số statement.
10. Procedure batch cần semantics rõ khi lỗi, retry, lock và partial progress.
11. Routine cần volatility, null behavior, quyền và migration được ghi rõ.
12. Logic gần dữ liệu mạnh nhưng cần kỷ luật về dependency, hiệu năng và vận hành.

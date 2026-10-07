# Tuần 9: Mô hình hóa và chuẩn hóa dữ liệu

Mục tiêu: chuyển yêu cầu nghiệp vụ thành mô hình dữ liệu quan hệ, xác định entity và relationship, thiết kế bảng trung gian cho quan hệ nhiều-nhiều, và áp dụng chuẩn hóa đến mức 3NF ở mức thực hành.

Một database tốt bắt đầu bằng các câu hỏi:

~~~text
Hệ thống cần lưu sự kiện gì?
Mỗi sự kiện có thuộc tính nào?
Các đối tượng liên quan với nhau ra sao?
Thông tin nào chỉ nên lưu một lần?
~~~

---

## 1. Từ yêu cầu nghiệp vụ đến entity

Yêu cầu bookstore:

~~~text
Một tác giả có nhiều sách.
Một khách hàng có nhiều order.
Một order có nhiều sách.
Một sách có thể xuất hiện trong nhiều order.
Hệ thống lưu số lượng và giá tại thời điểm mua.
Khách hàng có thể có nhiều địa chỉ.
Một order có thể có nhiều lần thử thanh toán.
~~~

Các danh từ thường gợi ý entity:

~~~text
Author, Book, Customer, Address, Order, OrderItem, Payment
~~~

Các động từ gợi ý relationship:

~~~text
Author writes Book
Customer places Order
Order contains Book
Customer has Address
Order has Payment
~~~

Không tạo table cho mọi từ trong yêu cầu. Hãy hỏi:

- Khái niệm này có vòng đời riêng không?
- Có nhiều instance không?
- Có thuộc tính riêng không?
- Có cần truy vấn hoặc cập nhật riêng không?

OrderItem là entity riêng vì nó có quantity, unit_price, order_id và book_id. Nó mô tả một dòng giao dịch.

---

## 2. Entity, attribute và relationship

~~~text
Entity       → table
Attribute    → column
Instance     → row
Relationship → foreign key hoặc bảng liên kết
Constraint   → quy tắc toàn vẹn dữ liệu
~~~

Ví dụ:

~~~text
Customer → customers(id, email, full_name)
Book     → books(id, title, price)
Order    → orders(id, customer_id, ordered_at, status)
~~~

Relationship cần trả lời:

- Cardinality là 1:1, 1:N hay N:N?
- Quan hệ có bắt buộc không?
- Foreign key đặt ở đâu?
- Khi row cha bị xóa thì row con xử lý thế nào?

---

## 3. Cardinality

### 3.1. One-to-one (1:1)

Một row ở A tương ứng tối đa một row ở B và ngược lại.

~~~text
User 1 ─── 1 UserProfile
~~~

Dùng foreign key có UNIQUE:

~~~sql
CREATE TABLE user_profiles (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id bigint NOT NULL UNIQUE REFERENCES users(id),
    display_name text NOT NULL
);
~~~

UNIQUE(user_id) bảo đảm một user không có hai profile. Không nên tạo 1:1 chỉ vì một bảng có nhiều column; cần có lý do về quyền truy cập, vòng đời hoặc hiệu năng.

### 3.2. One-to-many (1:N)

Một row bên cha có nhiều row con; mỗi row con thuộc về một row cha.

~~~text
Customer 1 ─── N Order
Author   1 ─── N Book
Order    1 ─── N OrderItem
~~~

Foreign key đặt ở phía many:

~~~sql
CREATE TABLE orders (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id bigint NOT NULL REFERENCES customers(id),
    ordered_at timestamptz NOT NULL,
    status text NOT NULL
);
~~~

### 3.3. Many-to-many (N:N)

Một row bên A liên quan nhiều row bên B và ngược lại.

~~~text
Order N ─── N Book
~~~

Không lưu danh sách ID trong một column:

~~~text
book_ids = '10,12,18'  -- Không nên làm
~~~

Tạo bảng trung gian:

~~~sql
CREATE TABLE order_items (
    order_id bigint NOT NULL REFERENCES orders(id),
    book_id bigint NOT NULL REFERENCES books(id),
    quantity integer NOT NULL CHECK (quantity > 0),
    unit_price numeric(12, 2) NOT NULL CHECK (unit_price >= 0),
    PRIMARY KEY (order_id, book_id)
);
~~~

Bảng trung gian còn chứa thuộc tính của relationship: số lượng và giá tại thời điểm mua.

---

## 4. Optionality: quan hệ bắt buộc hay tùy chọn?

Ví dụ:

~~~text
Order bắt buộc phải có Customer.
Customer có thể chưa có Order.
Author có thể chưa có Book.
Book có thể chưa từng bán.
~~~

Trong DDL:

~~~sql
customer_id bigint NOT NULL REFERENCES customers(id)
~~~

nghĩa là order bắt buộc có customer.

Trong khi:

~~~sql
author_id bigint REFERENCES authors(id)
~~~

cho phép author_id là NULL. Chỉ dùng NOT NULL khi record không có giá trị đó sẽ vô nghĩa hoặc vi phạm nghiệp vụ.

ERD bookstore:

~~~text
authors
  │ 1
  └──────── N books

customers
  │ 1
  ├──────── N addresses
  └──────── N orders
                  │ 1
                  └──────── N order_items N ─── 1 books

orders
  │ 1
  └──────── N payments
~~~

orders và books là N:N nhưng quan hệ được triển khai qua order_items. payments có thể là 1:1 nếu chỉ lưu payment cuối cùng hoặc 1:N nếu lưu các lần retry.

---

## 5. Khóa và định danh

### Primary key

Định danh duy nhất một row và không được NULL:

~~~sql
id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY
~~~

### Foreign key

Bảo đảm giá trị tham chiếu tới row tồn tại:

~~~sql
customer_id bigint NOT NULL REFERENCES customers(id)
~~~

### Composite key

Gồm nhiều column:

~~~sql
PRIMARY KEY (order_id, book_id)
~~~

Trong order_items, nó ngăn cùng một book xuất hiện hai lần trong một order. Nếu nghiệp vụ cho phép nhiều dòng cùng book, dùng id riêng và constraint khác phù hợp.

### Surrogate key và natural key

Surrogate key là ID kỹ thuật như bigint identity hoặc UUID. Natural key là giá trị có ý nghĩa nghiệp vụ như email, ISBN hoặc mã quốc gia.

| Loại | Ưu điểm | Rủi ro |
|---|---|---|
| Surrogate key | ngắn, ổn định, dễ tham chiếu | phải thêm constraint cho natural key |
| Natural key | có ý nghĩa nghiệp vụ | có thể dài, đổi hoặc không thật sự duy nhất |

Lựa chọn phổ biến là surrogate primary key kèm UNIQUE cho natural key:

~~~sql
CREATE TABLE customers (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email text NOT NULL UNIQUE,
    full_name text NOT NULL
);
~~~

---

## 6. Chuẩn hóa và các anomaly

Chuẩn hóa tổ chức dữ liệu để giảm lặp, tránh anomaly và bảo đảm mỗi fact được lưu ở nơi phù hợp.

### Update anomaly

Nếu tên customer lặp trong 1.000 order, đổi tên phải cập nhật 1.000 row. Bỏ sót một row làm dữ liệu không nhất quán.

### Insert anomaly

Nếu thông tin author chỉ nằm trong bảng book, không thể thêm author chưa có book mà không tạo book giả.

### Delete anomaly

Nếu xóa book cuối cùng của author và author chỉ nằm trong row book, có thể mất luôn thông tin author.

Thiết kế tốt tách các fact độc lập:

~~~text
customers(id, email, full_name)
authors(id, name)
books(id, author_id, title, price)
~~~

---

## 7. First Normal Form (1NF)

Ở mức thực hành, 1NF yêu cầu:

- mỗi cell chứa một giá trị nguyên tử;
- không lưu danh sách phân cách bằng dấu phẩy;
- không dùng nhóm column lặp như book_1_id, book_2_id;
- mỗi row có định danh.

Thiết kế không đạt 1NF:

~~~text
orders
id | customer_id | book_ids | quantities
1  | 7           | 10,12,18 | 2,1,3
~~~

Vấn đề:

- không tạo được foreign key cho từng book;
- khó tìm order chứa book cụ thể;
- khó cập nhật một quantity;
- database không kiểm tra được số lượng và ID trong chuỗi.

Thiết kế đạt 1NF:

~~~text
orders
id | customer_id
1  | 7

order_items
order_id | book_id | quantity
1        | 10      | 2
1        | 12      | 1
1        | 18      | 3
~~~

Số item không bị giới hạn bởi số column và mỗi cell có một giá trị.

---

## 8. Second Normal Form (2NF)

Ở mức thực hành, 2NF yêu cầu bảng đã đạt 1NF và mọi non-key attribute phụ thuộc vào toàn bộ primary key. Khái niệm này quan trọng nhất với composite key.

Thiết kế không đạt 2NF:

~~~text
order_items
order_id | book_id | book_title | quantity | unit_price
~~~

Với primary key (order_id, book_id):

~~~text
(order_id, book_id) → quantity, unit_price
book_id             → book_title
~~~

book_title chỉ phụ thuộc vào một phần book_id của composite key. Đây là partial dependency.

Thiết kế đúng:

~~~text
books
id | title

order_items
order_id | book_id | quantity | unit_price
~~~

book_title thuộc books; quantity phụ thuộc vào cặp order-book; unit_price là giá snapshot của giao dịch nên giữ trong order_items.

---

## 9. Third Normal Form (3NF)

Ở mức thực hành, 3NF yêu cầu bảng đã đạt 2NF và non-key attribute không phụ thuộc bắc cầu vào primary key thông qua một non-key attribute khác.

Thiết kế không đạt 3NF:

~~~text
customers
id | email | city_id | city_name
~~~

Dependencies:

~~~text
id      → city_id
city_id → city_name
~~~

city_name phụ thuộc vào city_id, không trực tiếp vào customer. Có thể tách:

~~~text
customers(id, email, city_id)
cities(id, name)
~~~

Tương tự, không nên lặp customer_email và customer_name trong orders:

~~~text
customers(id, email, full_name)
orders(id, customer_id, ordered_at)
~~~

Không phải mọi giá trị lặp đều cần table riêng. Tạo cities khi city có thuộc tính, vòng đời hoặc quan hệ riêng; nếu chỉ là text đơn giản, city_name có thể đủ.

---

## 10. Functional dependency

Functional dependency X → Y nghĩa là biết X thì xác định được Y.

~~~text
customer_id             → customer_email, customer_name
book_id                 → book_title, current_price
order_id                → customer_id, ordered_at, status
(order_id, book_id)     → quantity, unit_price
~~~

Dùng các câu hỏi sau để kiểm tra column có nằm đúng bảng không:

1. Column này được xác định bởi key nào?
2. Key đó có phải key của bảng hiện tại không?
3. Column thuộc entity hay thuộc relationship?
4. Column có thay đổi độc lập với key hiện tại không?

books.price là giá hiện tại; order_items.unit_price là giá lịch sử của một giao dịch. Hai column cùng liên quan tới giá nhưng có dependency và vòng đời khác nhau, nên có thể cùng tồn tại.

---

## 11. DDL bookstore đã chuẩn hóa

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
    address_type text NOT NULL CHECK (
        address_type IN ('billing', 'shipping')
    ),
    line1 text NOT NULL,
    city text NOT NULL,
    postal_code text,
    is_default boolean NOT NULL DEFAULT false
);

CREATE TABLE orders (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id bigint NOT NULL REFERENCES customers(id),
    ordered_at timestamptz NOT NULL DEFAULT now(),
    status text NOT NULL CHECK (
        status IN ('pending', 'paid', 'cancelled', 'fulfilled')
    )
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
    provider text NOT NULL,
    provider_transaction_id text,
    amount numeric(12, 2) NOT NULL CHECK (amount >= 0),
    status text NOT NULL CHECK (
        status IN ('created', 'succeeded', 'failed', 'refunded')
    ),
    paid_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);
~~~

Thiết kế này cho phép nhiều payment attempt cho một order. Nếu nghiệp vụ chỉ cho một payment record, thêm UNIQUE(order_id) vào payments.

Nếu mỗi customer chỉ có một address mặc định cho mỗi loại:

~~~sql
CREATE UNIQUE INDEX one_default_address_per_type
ON addresses (customer_id, address_type)
WHERE is_default;
~~~

---

## 12. Snapshot và dữ liệu lịch sử

Chuẩn hóa không có nghĩa xóa mọi dữ liệu lặp. Một số giá trị cần snapshot vì lịch sử phải giữ nguyên:

~~~text
books.price             → giá hiện tại của sách
order_items.unit_price  → giá tại thời điểm mua
~~~

Nếu chỉ join books.price khi xem order cũ, hóa đơn có thể thay đổi sau mỗi lần đổi giá. Vì vậy unit_price là dữ liệu của giao dịch và cần lưu riêng.

Tương tự, hệ thống có thể snapshot địa chỉ giao hàng trong order nếu khách hàng được phép sửa địa chỉ sau khi đặt hàng. Đây là dữ liệu lịch sử có chủ đích, không phải duplication vô tình.

---

## 13. Denormalization

Denormalization là cố ý lưu dữ liệu dư hoặc dữ liệu đã tổng hợp để tối ưu đọc. Ví dụ thêm orders.total_amount dù tổng tiền đã tính được từ order_items.

### Lợi ích

- Query đọc đơn giản hơn.
- Không phải tính lại tổng cho màn hình đọc thường xuyên.
- Phù hợp với read model hoặc báo cáo.

### Rủi ro

- Dữ liệu gốc và tổng tiền có thể lệch.
- Mỗi thay đổi item phải cập nhật thêm order.
- Cần transaction hoặc cơ chế đồng bộ.
- Debug khó vì cùng một fact xuất hiện nhiều nơi.

Chỉ denormalize sau khi:

1. Có query thật sự chậm hoặc tốn tài nguyên.
2. Đã đo bằng EXPLAIN ANALYZE.
3. Xác định rõ source of truth.
4. Có cơ chế cập nhật và kiểm tra consistency.

Hướng an toàn là giữ schema giao dịch chuẩn hóa làm source of truth và tạo read model hoặc materialized view riêng cho báo cáo.

---

## 14. ON DELETE và vòng đời dữ liệu

| Hành vi | Ý nghĩa |
|---|---|
| RESTRICT/mặc định | Chặn xóa nếu còn row con |
| CASCADE | Xóa row con theo row cha |
| SET NULL | Giữ row con nhưng bỏ liên kết |

Với order item phụ thuộc hoàn toàn vào order:

~~~sql
order_id bigint NOT NULL
    REFERENCES orders(id)
    ON DELETE CASCADE
~~~

Nhưng order lịch sử thường không nên bị xóa vật lý. Nhiều hệ thống dùng status hoặc soft delete. Với book đã có giao dịch, giữ hành vi mặc định để chặn xóa hoặc dùng is_active/deleted_at.

ON DELETE phải phản ánh vòng đời nghiệp vụ, không chọn máy móc.

---

## 15. Checklist thiết kế schema

### Entity

- Có đại diện cho khái niệm nghiệp vụ thật không?
- Có primary key chưa?
- Tên table và column có rõ nghĩa không?

### Relationship

- Cardinality là 1:1, 1:N hay N:N?
- Foreign key nằm ở phía many chưa?
- Quan hệ có bắt buộc không?
- Có cần bảng trung gian không?

### Chuẩn hóa

- Mỗi cell có một giá trị nguyên tử không?
- Có danh sách phân cách bằng dấu phẩy không?
- Non-key column có phụ thuộc toàn bộ composite key không?
- Có transitive dependency cần tách không?

### Toàn vẹn

- Có NOT NULL ở nơi bắt buộc không?
- Có UNIQUE cho natural key không?
- Có CHECK cho giá trị hợp lệ không?
- ON DELETE có phù hợp vòng đời không?

### Thực tế

- Có cần lưu snapshot lịch sử không?
- Kiểu thời gian có timezone phù hợp không?
- Foreign key và query quan trọng có index không?
- Denormalization có source of truth và cơ chế đồng bộ không?

---

## 16. Bài tập thực hành

### Bài 1 — Xác định entity

Một khóa học có nhiều lesson. Một học viên đăng ký nhiều khóa học. Một khóa học có nhiều học viên. Học viên có thể đánh dấu lesson đã hoàn thành.

Liệt kê entity, relationship, bảng trung gian và thuộc tính của relationship.

### Bài 2 — Cardinality

Xác định cardinality và vị trí foreign key cho:

~~~text
Customer — Order
Order — Book
Order — OrderItem
User — UserProfile
~~~

### Bài 3 — Sửa thiết kế không đạt 1NF

~~~text
orders(id, customer_id, book_ids, quantities)
~~~

Thiết kế lại thành các bảng chuẩn hóa.

### Bài 4 — Phát hiện partial dependency

~~~text
order_items(order_id, book_id, book_title, quantity, unit_price)
~~~

Với primary key (order_id, book_id), column nào phụ thuộc một phần vào key? Tách lại schema.

### Bài 5 — Phát hiện transitive dependency

~~~text
customers(id, email, city_id, city_name)
~~~

Giải thích dependency và đề xuất schema 3NF.

### Bài 6 — Thiết kế bookstore

Viết DDL cho authors, books, customers, addresses, orders, order_items, payments với primary key, foreign key, unique, check và payment retry.

### Bài 7 — Review denormalization

Một team muốn thêm orders.total_amount để tránh tính tổng từ item. Viết review nêu source of truth, nơi cập nhật, transaction cần thiết, cách kiểm tra lệch và khi nào giải pháp đáng dùng.

### Đáp án tham khảo cho bài 3–5

~~~sql
-- Bài 3: danh sách book_ids trở thành nhiều row
CREATE TABLE order_items (
    order_id bigint NOT NULL REFERENCES orders(id),
    book_id bigint NOT NULL REFERENCES books(id),
    quantity integer NOT NULL CHECK (quantity > 0),
    PRIMARY KEY (order_id, book_id)
);

-- Bài 4: book_title thuộc books
CREATE TABLE books (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title text NOT NULL
);

CREATE TABLE order_items (
    order_id bigint NOT NULL REFERENCES orders(id),
    book_id bigint NOT NULL REFERENCES books(id),
    quantity integer NOT NULL CHECK (quantity > 0),
    unit_price numeric(12, 2) NOT NULL CHECK (unit_price >= 0),
    PRIMARY KEY (order_id, book_id)
);

-- Bài 5: tách city nếu city là entity có dữ liệu riêng
CREATE TABLE cities (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name text NOT NULL UNIQUE
);

CREATE TABLE customers (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email text NOT NULL UNIQUE,
    city_id bigint REFERENCES cities(id)
);
~~~

---

## 17. Tiêu chí hoàn thành Tuần 9

Bạn hoàn thành bài học khi có thể:

- Phân biệt entity, attribute và relationship.
- Xác định cardinality 1:1, 1:N và N:N.
- Đặt foreign key đúng phía của quan hệ 1:N.
- Thiết kế bảng trung gian cho N:N.
- Giải thích vì sao không lưu danh sách ID trong một column.
- Phân biệt surrogate key, natural key và composite key.
- Nhận biết update, insert và delete anomaly.
- Giải thích 1NF, 2NF và 3NF bằng ví dụ.
- Nhận biết partial dependency và transitive dependency.
- Thiết kế schema bookstore có constraint phù hợp.
- Chọn ON DELETE dựa trên vòng đời dữ liệu.
- Giải thích trade-off giữa normalization và denormalization.

## Tóm tắt cần nhớ

~~~text
Entity thành table; attribute thành column; relationship thành foreign key hoặc bảng liên kết.
1:N đặt foreign key ở phía N.
N:N cần bảng trung gian.
1NF không lưu danh sách trong một cell.
2NF loại bỏ dependency chỉ phụ thuộc một phần composite key.
3NF loại bỏ dependency bắc cầu giữa các non-key column.
Surrogate key không thay thế UNIQUE cho natural key.
Chuẩn hóa giúp mỗi fact được cập nhật ở một nơi.
Denormalization chỉ nên làm khi có lý do đo được và cơ chế giữ consistency.
~~~

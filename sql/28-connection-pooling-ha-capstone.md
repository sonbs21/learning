# Tuần 16 nâng cao: Connection pooling, HA và capstone PostgreSQL

> Mục tiêu: hiểu vì sao max_connections cao không đồng nghĩa throughput cao, cấu hình application pool và PgBouncer, chọn session/transaction pooling, thiết kế pool sizing/backpressure/timeout, health check, read/write split, HA topology và SLO; sau đó hoàn thành capstone Multi-tenant Event và Order Platform.

Tuần 16 là tuần ghép các mảnh đã học thành một hệ thống có thể vận hành:

~~~text
application
  → connection pool
  → PgBouncer
  → primary hoặc read replica
  → schema/RLS/index/partition
  → transaction và queue
  → backup/replication
  → monitoring/runbook/SLO
~~~

Một hệ thống PostgreSQL production-like không được đánh giá chỉ bằng câu query chạy đúng. Cần trả lời:

- khi traffic tăng, request có được backpressure không;
- khi primary lỗi, ai promote và ai fence primary cũ;
- tenant A có đọc được tenant B không;
- event cũ được partition/retention thế nào;
- query dashboard có plan và index chứng minh được không;
- backup có restore thử không;
- pool cạn, lock, disk đầy, replication lag được chẩn đoán ra sao;
- thay đổi schema có migration và rollback plan không.

---

## 1. Vì sao max_connections cao không đồng nghĩa throughput cao

Mỗi PostgreSQL connection thường tương ứng một backend process hoặc worker context. Connection nhiều có thể làm tăng:

- memory cho backend;
- context switch;
- lock contention;
- CPU scheduler pressure;
- cache competition;
- số transaction đồng thời;
- số query cùng tranh disk/I/O;
- thời gian recovery khi nhiều session cần xử lý.

Ví dụ:

~~~text
CPU có 8 core
query trung bình dùng nhiều CPU
max_connections = 500
~~~

500 session không làm hệ thống chạy 500 query hiệu quả. Nếu 500 query cùng tranh 8 core và storage, latency tail có thể tăng dù throughput không tăng.

Phân biệt:

| Khái niệm | Ý nghĩa |
|---|---|
| max_connections | giới hạn connection PostgreSQL chấp nhận |
| application pool size | số connection app giữ tới một backend |
| PgBouncer client connection | số client kết nối tới pooler |
| server connection | số connection pooler mở tới PostgreSQL |
| concurrency | số công việc thật sự chạy đồng thời |

Connection pooling làm tách client concurrency khỏi database concurrency. Pooler không biến query chậm thành query nhanh; nó giúp giới hạn và xếp hàng workload.

---

## 2. Application connection pool

Application pool giữ một nhóm database connection đã mở và tái sử dụng:

~~~text
request
  → mượn connection
  → BEGIN/SET LOCAL/query
  → COMMIT/ROLLBACK
  → reset state
  → trả connection
~~~

Các tham số cần có:

- maximum pool size;
- minimum idle;
- acquisition timeout;
- connection timeout;
- idle timeout;
- max lifetime;
- validation query;
- transaction timeout;
- leak detection;
- retry policy.

Ví dụ pseudo-configuration:

~~~text
pool.max_size = 20
pool.min_idle = 5
pool.acquire_timeout = 2s
pool.connect_timeout = 3s
pool.idle_timeout = 60s
pool.max_lifetime = 30m
transaction_timeout = 5s
~~~

Đây là mẫu để suy nghĩ, không phải số đúng cho mọi workload.

### 2.1 Pool size theo tổng instance

Nếu có 10 application instance và mỗi instance mở pool 20:

~~~text
10 × 20 = 200 database connections
~~~

Khi autoscaling từ 10 lên 30 instance, tổng connection thành 600 nếu không có giới hạn tập trung. Pool sizing phải tính trên toàn hệ thống:

~~~text
total server connections
  = số app instance × pool size
  + worker/job pool
  + admin/monitoring
  + replication
  + reserved headroom
~~~

Nếu dùng PgBouncer, app client connections có thể lớn hơn nhiều, nhưng server connections vẫn phải giới hạn theo database capacity.

### 2.2 Reset session state

Connection pool có thể tái sử dụng session với state cũ:

- search_path;
- role;
- SET LOCAL/SET;
- timezone;
- temp table;
- prepared statement;
- advisory lock;
- GUC tenant context;
- transaction còn mở.

Sau mỗi request:

1. COMMIT hoặc ROLLBACK;
2. reset tenant/session setting;
3. giải phóng advisory lock;
4. rollback khi có exception;
5. không trả connection đang ở transaction lỗi.

Có thể dùng:

~~~sql
RESET ALL;
DISCARD ALL;
~~~

DISCARD ALL nặng hơn và có thể không phù hợp trên mọi path. Application/pooler cần biết state nào được phép giữ và state nào phải reset.

---

## 3. Timeout và backpressure

Pool cạn không nhất thiết là lỗi database. Nó có thể là tín hiệu hệ thống đang quá tải. Nếu request chờ connection vô hạn, queue sẽ phình và tạo cascading failure.

Mô hình:

~~~text
request rate > database service rate
  → pool queue tăng
  → latency tăng
  → timeout/retry tăng
  → request rate hiệu dụng tăng
  → database quá tải hơn
~~~

Backpressure cần:

- acquisition timeout;
- database statement_timeout;
- lock_timeout;
- transaction timeout;
- queue length limit;
- bounded retry;
- circuit breaker;
- request cancellation;
- trả lỗi rõ khi hệ thống quá tải.

Ví dụ ở database:

~~~sql
SET LOCAL statement_timeout = '5s';
SET LOCAL lock_timeout = '500ms';
SET LOCAL idle_in_transaction_session_timeout = '30s';
~~~

Không đặt statement_timeout quá ngắn cho mọi query nếu có report/maintenance hợp lệ. Có thể đặt theo role/database/workload:

~~~sql
ALTER ROLE app_runtime SET statement_timeout = '5s';
ALTER ROLE app_reporting SET statement_timeout = '60s';
~~~

Timeout phải được quan sát cùng error rate. Timeout mà không có retry/backpressure chỉ chuyển lỗi từ database sang application.

---

## 4. PgBouncer

PgBouncer là connection pooler nhẹ nằm giữa application và PostgreSQL:

~~~text
many client connections
  → PgBouncer
  → bounded PostgreSQL server connections
~~~

Cấu hình khái quát:

~~~ini
[databases]
app = host=pg-primary port=5432 dbname=app
app_ro = host=pg-replica port=5432 dbname=app

[pgbouncer]
listen_addr = 0.0.0.0
listen_port = 6432
auth_type = scram-sha-256
auth_file = /etc/pgbouncer/userlist.txt
pool_mode = transaction
max_client_conn = 1000
default_pool_size = 20
reserve_pool_size = 5
reserve_pool_timeout = 2
server_connect_timeout = 3
query_wait_timeout = 5
idle_transaction_timeout = 30
server_idle_timeout = 300
max_db_connections = 50
max_user_connections = 50
admin_users = pgbouncer_admin
stats_users = pgbouncer_stats
~~~

Không copy nguyên mẫu vào production. Cần kiểm tra version, TLS, auth, credential, topology và giới hạn host.

### 4.1 Session pooling

Một client giữ cùng server connection trong toàn session:

~~~text
client A ───────── server connection 1
client B ───────── server connection 2
~~~

Ưu điểm:

- tương thích tốt với session state;
- prepared statement/session variable/temp table thường dễ dùng hơn;
- hành vi gần direct connection.

Nhược điểm:

- tiết kiệm connection kém hơn;
- client idle vẫn chiếm server connection.

### 4.2 Transaction pooling

Server connection được gán theo từng transaction:

~~~text
transaction A → server connection 1
transaction B → server connection 1
transaction C → server connection 2
~~~

Ưu điểm:

- nhiều client dùng ít server connection;
- phù hợp API request ngắn;
- giảm connection overhead.

Nhược điểm:

- session state không được đảm bảo giữa các transaction;
- prepared statement/session temp table/advisory lock cần kiểm tra;
- SET không dùng đúng có thể rò hoặc mất state;
- session-level LISTEN/NOTIFY và cursor có hạn chế;
- transaction phải hoàn chỉnh trước khi trả connection.

Transaction pooling cần application viết theo transaction boundary rõ ràng. Mọi tenant context nên là SET LOCAL trong transaction, không phải SET lâu dài.

### 4.3 Statement pooling

Statement pooling gán connection cho từng statement. Nó có tương thích thấp hơn và hiếm phù hợp với ứng dụng cần transaction nhiều bước. Không chọn chỉ vì số pooling cao.

---

## 5. PgBouncer health và vận hành

Kết nối admin:

~~~bash
psql -h pgbouncer-host -p 6432 -U pgbouncer_admin pgbouncer
~~~

Lệnh hữu ích:

~~~sql
SHOW POOLS;
SHOW CLIENTS;
SHOW SERVERS;
SHOW STATS;
SHOW DATABASES;
SHOW CONFIG;
~~~

Theo dõi:

- cl_active/cl_waiting;
- sv_active/sv_idle/sv_used;
- maxwait;
- avg_wait_time;
- transaction/query count;
- server connection saturation;
- auth failure;
- pooler process health.

PgBouncer sống nhưng không có server connection khỏe vẫn là outage. Health check cần thử query thật qua pooler:

~~~sql
SELECT 1;
SELECT current_database(), current_user;
~~~

Không dùng health check chỉ kiểm tra TCP port. Một endpoint có thể accept connection nhưng database đang read-only, lag, lock hoặc không đúng role.

---

## 6. Pool sizing có hệ thống

Một heuristic đơn giản:

~~~text
database concurrency ≈ số core × hệ số workload
~~~

Đây không phải công thức chính xác. Với query CPU-bound, concurrency gần số core thường hiệu quả hơn hàng trăm query cùng chạy. Với I/O-bound, concurrency có thể lớn hơn nhưng bị giới hạn bởi storage.

Quy trình sizing:

1. đặt pool nhỏ an toàn;
2. benchmark cùng workload;
3. tăng từng bước;
4. đo throughput, p95/p99, CPU, I/O, lock và memory;
5. tìm điểm throughput bão hòa;
6. chọn dưới điểm bão hòa để có headroom;
7. tính tổng qua mọi app instance;
8. đặt pool queue và acquisition timeout.

Ví dụ thí nghiệm:

| Pool tổng | TPS | p95 | CPU | Lock wait | Kết luận |
|---:|---:|---:|---:|---:|---|
| 20 | ghi số đo | ghi số đo | ghi số đo | ghi số đo | |
| 40 | ghi số đo | ghi số đo | ghi số đo | ghi số đo | |
| 80 | ghi số đo | ghi số đo | ghi số đo | ghi số đo | |
| 160 | ghi số đo | ghi số đo | ghi số đo | ghi số đo | |

Nếu pool tăng nhưng TPS không tăng và p99 tăng, đã vượt concurrency hữu ích.

---

## 7. Read/write split

Mô hình:

~~~text
write transaction ──→ primary
read-only query ────→ replica
~~~

Không phải query SELECT nào cũng gửi replica được. Các trường hợp cần primary:

- read-after-write cần thấy ngay;
- transaction có write;
- SELECT sau UPDATE trong cùng workflow;
- query phụ thuộc dữ liệu chưa replay;
- advisory lock/transaction state trên primary;
- function có side effect;
- report cần consistency mạnh.

Read replica có eventual consistency. Ví dụ:

~~~text
POST order → primary commit
GET order immediately → replica chưa replay
→ response không thấy order
~~~

Giải pháp:

- sticky read về primary trong một khoảng thời gian;
- gửi read sau write tới primary;
- chờ replica replay tới LSN;
- trả consistency token;
- chấp nhận eventual consistency ở UI/report.

Router phải biết:

- replica health;
- replay lag;
- read-only state;
- topology sau failover;
- connection pool riêng.

Không route read vào standby đang lag hàng giờ chỉ vì endpoint còn mở.

---

## 8. Health check và readiness

Phân biệt:

| Check | Câu hỏi |
|---|---|
| liveness | process còn sống không? |
| readiness | có nhận workload loại này được không? |
| dependency health | primary/replica/pooler/network hoạt động không? |
| data health | lag, disk, lock và WAL có trong giới hạn không? |

Primary write readiness:

~~~sql
SELECT NOT pg_is_in_recovery();
SELECT current_setting('transaction_read_only');
SELECT 1;
~~~

Replica read readiness:

~~~sql
SELECT pg_is_in_recovery();
SELECT
    clock_timestamp() - pg_last_xact_replay_timestamp()
        AS replay_age;
~~~

Readiness nên fail nếu:

- replica lag vượt SLA;
- disk gần đầy;
- recovery state không đúng;
- connection pool không cấp được connection;
- replication slot/WAL risk;
- database đang maintenance có chủ đích.

Health endpoint không nên chạy query nặng hoặc SELECT count(*) trên bảng lớn mỗi giây.

---

## 9. HA topology

### 9.1 Primary + async standby

~~~text
application → primary
             └→ async standby
~~~

Ưu điểm: đơn giản, giảm downtime, có read replica/DR. Nhược điểm: có thể mất transaction cuối khi failover.

### 9.2 Primary + synchronous standby

~~~text
application → primary
             ⇄ synchronous standby
~~~

Giảm RPO nhưng tăng write latency và phụ thuộc mạng/standby.

### 9.3 Pooler/proxy trước database

~~~text
application
  → PgBouncer/proxy
  → primary hoặc read replica
~~~

Cần endpoint/service discovery thay đổi khi promotion. Pooler không tự là HA nếu chỉ chạy một instance.

### 9.4 Nhiều vùng/failure domain

Standby cùng host không bảo vệ lỗi host. Standby cùng zone không bảo vệ lỗi zone. Thiết kế theo:

- failure domain;
- latency;
- bandwidth;
- synchronous requirement;
- cost;
- RPO/RTO.

HA topology phải có fencing và runbook. Có hai PostgreSQL instance không tự tạo HA.

---

## 10. SLO cho database

SLO là mục tiêu đo được trong một cửa sổ thời gian:

~~~text
availability
latency
error rate
freshness/replication lag
durability/RPO
recovery/RTO
~~~

Ví dụ:

| SLO | Ví dụ mục tiêu |
|---|---|
| write availability | 99.9% theo tháng |
| API read p95 | < 200 ms |
| API write p95 | < 300 ms |
| read replica freshness | lag < 5 s trong 99% thời gian |
| RPO | < 1 phút cho giao dịch đã commit |
| RTO | < 15 phút |
| connection acquisition | p95 < 100 ms |

Đây chỉ là ví dụ. Mục tiêu phải dựa trên sản phẩm và khả năng vận hành.

### 10.1 Error budget

Nếu SLO availability 99.9% trong 30 ngày, error budget khoảng:

~~~text
30 ngày × 24 giờ × 60 phút × 0.1%
≈ 43.2 phút
~~~

Migration rủi ro, thay đổi pool và failover drill tiêu thụ error budget. SLO giúp quyết định nên tối ưu latency hay giữ ổn định, không chỉ tạo alert.

### 10.2 SLO không chỉ là uptime

Database process sống nhưng:

- query p99 quá cao;
- connection pool cạn;
- replica lag;
- RLS reject sai;
- restore không đạt RTO;

thì trải nghiệm vẫn thất bại. Dashboard phải map metric với user journey.

---

## 11. Capstone: Multi-tenant Event và Order Platform

Capstone xây một nền tảng đơn giản có:

- nhiều tenant;
- user và role;
- product/inventory;
- order/order_items;
- payment;
- events partition theo thời gian;
- audit_logs;
- JSONB metadata;
- full-text search;
- RLS;
- queue worker;
- dashboard query;
- backup/restore;
- replication;
- monitoring/runbook.

Mục tiêu không phải tạo một ứng dụng hoàn chỉnh. Mục tiêu là chứng minh các quyết định database bằng DDL, query, benchmark, failure test và runbook.

---

## 12. Capstone architecture

~~~text
API service
  → application pool
  → PgBouncer transaction pool
  → primary
       ├─ async/sync standby
       ├─ WAL archive
       └─ logical subscriber hoặc CDC

Read API
  → read pool
  → replica khi lag trong ngưỡng

Worker
  → bounded pool
  → SELECT ... FOR UPDATE SKIP LOCKED

Monitoring
  → pg_stat_activity/locks/statements
  → app metrics/logs
  → dashboard/alerts
~~~

Source of truth cho order/payment là primary. Replica phục vụ read được phép eventual consistency. Events có retention và partition riêng.

---

## 13. Capstone schema DDL

Tạo schema:

~~~sql
CREATE SCHEMA platform;
~~~

### 13.1 Tenants và users

~~~sql
CREATE TABLE platform.tenants (
    tenant_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_key text NOT NULL UNIQUE,
    tenant_name text NOT NULL,
    status text NOT NULL DEFAULT 'active'
        CHECK (status IN ('active', 'suspended')),
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE platform.users (
    user_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id bigint NOT NULL REFERENCES platform.tenants(tenant_id),
    email text NOT NULL,
    display_name text NOT NULL,
    role_name text NOT NULL DEFAULT 'member'
        CHECK (role_name IN ('owner', 'admin', 'member')),
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, email)
);
~~~

Email unique theo tenant, không global. Đây là quyết định business cần ghi trong ERD.

### 13.2 Products và inventory

~~~sql
CREATE TABLE platform.products (
    product_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id bigint NOT NULL REFERENCES platform.tenants(tenant_id),
    sku text NOT NULL,
    product_name text NOT NULL,
    description text NOT NULL DEFAULT '',
    metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
    search_vector tsvector GENERATED ALWAYS AS (
        to_tsvector(
            'simple',
            coalesce(product_name, '') || ' ' ||
            coalesce(description, '')
        )
    ) STORED,
    unit_price numeric(12, 2) NOT NULL CHECK (unit_price >= 0),
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, sku)
);

CREATE TABLE platform.inventory (
    product_id bigint PRIMARY KEY REFERENCES platform.products(product_id),
    tenant_id bigint NOT NULL REFERENCES platform.tenants(tenant_id),
    available_quantity integer NOT NULL DEFAULT 0
        CHECK (available_quantity >= 0),
    reserved_quantity integer NOT NULL DEFAULT 0
        CHECK (reserved_quantity >= 0),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CHECK (reserved_quantity <= available_quantity)
);
~~~

Nếu inventory được update nhiều, tenant_id lặp lại để RLS/filter/index nhanh hơn nhưng cần invariant đảm bảo product và inventory cùng tenant. Có thể dùng composite foreign key nếu cần database-enforced relationship.

### 13.3 Orders và items

~~~sql
CREATE TABLE platform.orders (
    order_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id bigint NOT NULL REFERENCES platform.tenants(tenant_id),
    user_id bigint NOT NULL REFERENCES platform.users(user_id),
    status text NOT NULL DEFAULT 'pending'
        CHECK (status IN (
            'pending', 'processing', 'paid',
            'cancelled', 'failed', 'completed'
        )),
    total_amount numeric(14, 2) NOT NULL DEFAULT 0
        CHECK (total_amount >= 0),
    metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE platform.order_items (
    order_id bigint NOT NULL REFERENCES platform.orders(order_id)
        ON DELETE CASCADE,
    tenant_id bigint NOT NULL REFERENCES platform.tenants(tenant_id),
    product_id bigint NOT NULL REFERENCES platform.products(product_id),
    quantity integer NOT NULL CHECK (quantity > 0),
    unit_price numeric(14, 2) NOT NULL CHECK (unit_price >= 0),
    PRIMARY KEY (order_id, product_id)
);
~~~

Giá trong order_items là snapshot tại thời điểm đặt hàng. Không join giá hiện tại từ products để tính lịch sử.

### 13.4 Payments và audit

~~~sql
CREATE TABLE platform.payments (
    payment_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id bigint NOT NULL REFERENCES platform.tenants(tenant_id),
    order_id bigint NOT NULL REFERENCES platform.orders(order_id),
    provider text NOT NULL,
    provider_reference text,
    status text NOT NULL CHECK (
        status IN ('created', 'authorized', 'captured', 'failed', 'refunded')
    ),
    amount numeric(14, 2) NOT NULL CHECK (amount >= 0),
    metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, provider, provider_reference)
);

CREATE TABLE platform.audit_logs (
    audit_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id bigint,
    actor_user_id bigint,
    action text NOT NULL,
    entity_type text NOT NULL,
    entity_id bigint,
    metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now()
);
~~~

Không lưu raw card number hoặc secret payment provider trong metadata. Token nhạy cảm phải qua vault/tokenization.

---

## 14. Events partition theo thời gian

Bảng event thường tăng nhanh và có retention rõ:

~~~sql
CREATE TABLE platform.events (
    event_id bigint GENERATED ALWAYS AS IDENTITY,
    tenant_id bigint NOT NULL REFERENCES platform.tenants(tenant_id),
    event_type text NOT NULL,
    aggregate_type text NOT NULL,
    aggregate_id bigint NOT NULL,
    payload jsonb NOT NULL DEFAULT '{}'::jsonb,
    occurred_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (event_id, occurred_at)
) PARTITION BY RANGE (occurred_at);
~~~

Partition theo tháng:

~~~sql
CREATE TABLE platform.events_2026_09
PARTITION OF platform.events
FOR VALUES FROM ('2026-09-01 00:00:00+00')
           TO   ('2026-10-01 00:00:00+00');

CREATE TABLE platform.events_2026_10
PARTITION OF platform.events
FOR VALUES FROM ('2026-10-01 00:00:00+00')
           TO   ('2026-11-01 00:00:00+00');
~~~

Tạo partition tương lai trước khi cần. Nếu không có partition phù hợp, INSERT sẽ fail:

~~~text
ERROR: no partition of relation events found for row
~~~

Default partition có thể là safety net nhưng làm maintenance khó hơn:

~~~sql
CREATE TABLE platform.events_default
PARTITION OF platform.events DEFAULT;
~~~

Nếu dùng default partition, phải định kỳ tìm row lệch range và chuyển chúng trước khi tạo partition mới.

Index trên parent/partition:

~~~sql
CREATE INDEX events_tenant_time_idx
ON platform.events (tenant_id, occurred_at DESC);

CREATE INDEX events_payload_gin_idx
ON platform.events
USING gin (payload jsonb_path_ops);
~~~

Kiểm tra index thực tế trên partition sau khi tạo và kiểm tra plan pruning.

---

## 15. JSONB metadata và full-text search

JSONB phù hợp với metadata không ổn định, không thay toàn bộ schema:

~~~sql
CREATE INDEX products_metadata_gin_idx
ON platform.products
USING gin (metadata jsonb_path_ops);

CREATE INDEX orders_metadata_gin_idx
ON platform.orders
USING gin (metadata jsonb_path_ops);
~~~

Query containment:

~~~sql
SELECT product_id, product_name
FROM platform.products
WHERE metadata @> '{"color": "black"}'::jsonb;
~~~

Full-text search dùng generated tsvector:

~~~sql
CREATE INDEX products_search_vector_idx
ON platform.products
USING gin (search_vector);

SELECT product_id, product_name
FROM platform.products
WHERE search_vector @@ plainto_tsquery('simple', 'wireless keyboard')
ORDER BY product_id;
~~~

Đo bằng EXPLAIN, không giả định GIN luôn được dùng. Tenant filter nên xuất hiện đúng trong query/policy và workload cần có statistics đại diện.

---

## 16. Tenant isolation bằng RLS

Thiết lập context trong transaction:

~~~sql
BEGIN;
SET LOCAL app.tenant_id = '42';
SELECT ...
COMMIT;
~~~

Trong production, app.tenant_id không được coi là identity nếu client có thể tự chọn. Application phải xác thực actor và chỉ set tenant mà actor được phép truy cập. Connection pool phải reset context.

Policy mẫu:

~~~sql
ALTER TABLE platform.users ENABLE ROW LEVEL SECURITY;
ALTER TABLE platform.products ENABLE ROW LEVEL SECURITY;
ALTER TABLE platform.inventory ENABLE ROW LEVEL SECURITY;
ALTER TABLE platform.orders ENABLE ROW LEVEL SECURITY;
ALTER TABLE platform.order_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE platform.payments ENABLE ROW LEVEL SECURITY;
ALTER TABLE platform.events ENABLE ROW LEVEL SECURITY;
ALTER TABLE platform.audit_logs ENABLE ROW LEVEL SECURITY;
~~~

Function đọc tenant context:

~~~sql
CREATE FUNCTION platform.current_tenant_id()
RETURNS bigint
LANGUAGE sql
STABLE
SECURITY INVOKER
AS $$
    SELECT current_setting('app.tenant_id', true)::bigint;
$$;
~~~

Policy order:

~~~sql
CREATE POLICY orders_tenant_policy
ON platform.orders
FOR ALL
USING (tenant_id = platform.current_tenant_id())
WITH CHECK (tenant_id = platform.current_tenant_id());

CREATE POLICY order_items_tenant_policy
ON platform.order_items
FOR ALL
USING (tenant_id = platform.current_tenant_id())
WITH CHECK (tenant_id = platform.current_tenant_id());

CREATE POLICY payments_tenant_policy
ON platform.payments
FOR ALL
USING (tenant_id = platform.current_tenant_id())
WITH CHECK (tenant_id = platform.current_tenant_id());
~~~

Tách policy SELECT/INSERT/UPDATE/DELETE nếu business rule khác nhau. Test tenant A đọc/sửa tenant B bằng role runtime thật. Owner/BYPASSRLS cần test riêng.

---

## 17. Index inventory capstone

Index khởi đầu:

~~~sql
CREATE INDEX users_tenant_idx
ON platform.users (tenant_id, user_id);

CREATE INDEX products_tenant_active_idx
ON platform.products (tenant_id, active, product_id);

CREATE INDEX inventory_tenant_idx
ON platform.inventory (tenant_id, product_id);

CREATE INDEX orders_tenant_created_idx
ON platform.orders (tenant_id, created_at DESC);

CREATE INDEX orders_tenant_status_created_idx
ON platform.orders (tenant_id, status, created_at DESC);

CREATE INDEX order_items_tenant_product_idx
ON platform.order_items (tenant_id, product_id);

CREATE INDEX payments_tenant_status_created_idx
ON platform.payments (tenant_id, status, created_at DESC);

CREATE INDEX audit_logs_tenant_time_idx
ON platform.audit_logs (tenant_id, created_at DESC);
~~~

Không tạo mọi tổ hợp cột theo cảm tính. Mỗi index phải có:

- query/use case;
- selectivity;
- write cost;
- size estimate;
- plan trước/sau;
- owner và review date.

---

## 18. Transaction tạo order

Workflow cần tạo order, kiểm tra inventory, reserve stock và tạo event trong cùng transaction:

~~~sql
BEGIN;

SET LOCAL app.tenant_id = '42';
SET LOCAL statement_timeout = '5s';
SET LOCAL lock_timeout = '500ms';

SELECT product_id, available_quantity, reserved_quantity
FROM platform.inventory
WHERE tenant_id = 42
  AND product_id IN (1001, 1002)
ORDER BY product_id
FOR UPDATE;

-- Validate tồn kho.
-- INSERT orders.
-- INSERT order_items.
-- UPDATE inventory.
-- INSERT events.

COMMIT;
~~~

Lock các inventory row theo thứ tự product_id để giảm deadlock giữa hai order chứa cùng nhiều product.

Không gọi external payment provider trong transaction database dài. Pattern tốt hơn:

1. tạo order pending;
2. commit outbox/event;
3. worker gọi provider;
4. cập nhật payment/order idempotently;
5. phát event tiếp theo.

---

## 19. Queue worker với SKIP LOCKED

Dùng events hoặc bảng job riêng. Ví dụ thêm trạng thái job:

~~~sql
ALTER TABLE platform.events
ADD COLUMN processed_at timestamptz;
~~~

Với partitioned table, thay đổi schema parent phải kiểm tra partition. Query worker:

~~~sql
BEGIN;

WITH next_events AS (
    SELECT event_id, occurred_at
    FROM platform.events
    WHERE processed_at IS NULL
      AND occurred_at < now()
    ORDER BY occurred_at, event_id
    FOR UPDATE SKIP LOCKED
    LIMIT 100
)
UPDATE platform.events AS e
SET processed_at = now()
FROM next_events AS n
WHERE e.event_id = n.event_id
  AND e.occurred_at = n.occurred_at
RETURNING e.*;

COMMIT;
~~~

SKIP LOCKED cho phép nhiều worker lấy job khác nhau, nhưng:

- row bị lock có thể bị bỏ qua;
- thứ tự xử lý không tuyệt đối;
- worker phải idempotent;
- failure sau update cần semantics rõ;
- batch size phải giới hạn.

Với outbox, thường nên có cột trạng thái/attempt/locked_at/error để retry và quan sát tốt hơn.

---

## 20. Deadlock scenario capstone

Transaction A:

~~~sql
BEGIN;
SELECT * FROM platform.inventory
WHERE product_id = 1001
FOR UPDATE;

SELECT * FROM platform.inventory
WHERE product_id = 1002
FOR UPDATE;
~~~

Transaction B lock ngược:

~~~sql
BEGIN;
SELECT * FROM platform.inventory
WHERE product_id = 1002
FOR UPDATE;

SELECT * FROM platform.inventory
WHERE product_id = 1001
FOR UPDATE;
~~~

Hai transaction có thể deadlock. Cách sửa:

- sort product_id trước khi lock;
- giữ transaction ngắn;
- retry transaction khi PostgreSQL abort một bên;
- metric deadlock và log_lock_waits;
- không retry vô hạn.

Test scenario trong lab bằng hai session, ghi PID, query và SQLSTATE. Deliverable phải gồm nguyên nhân, sửa code và bằng chứng deadlock không còn trong workload chuẩn.

---

## 21. Serialization failure và retry

Với isolation phù hợp hoặc concurrent update, application có thể nhận serialization failure/deadlock. Retry phải bao toàn transaction:

~~~text
for attempt in 1..N:
    BEGIN
    execute all statements
    COMMIT
    if serialization/deadlock:
        ROLLBACK
        backoff with jitter
        retry
    else:
        return
~~~

Không retry một statement đơn lẻ giữa transaction đã lỗi. Transaction phải rollback hoàn toàn rồi bắt đầu lại.

Retry cần:

- giới hạn attempt;
- exponential backoff;
- jitter;
- idempotency key;
- metric retry count;
- phân biệt lỗi retryable/non-retryable;
- không tạo duplicate payment/order/event.

---

## 22. Query dashboard tối ưu bằng EXPLAIN

Use case: dashboard tenant hiển thị order gần đây theo status:

~~~sql
SELECT
    status,
    count(*) AS order_count,
    sum(total_amount) AS total_amount
FROM platform.orders
WHERE tenant_id = 42
  AND created_at >= now() - interval '30 days'
GROUP BY status
ORDER BY status;
~~~

Đo baseline:

~~~sql
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
SELECT
    status,
    count(*) AS order_count,
    sum(total_amount) AS total_amount
FROM platform.orders
WHERE tenant_id = 42
  AND created_at >= now() - interval '30 days'
GROUP BY status
ORDER BY status;
~~~

Kiểm tra:

- tenant filter có selective không;
- rows estimate lệch không;
- index tenant/status/time có được dùng không;
- query có scan quá nhiều không;
- tenant lớn và tenant nhỏ có plan khác không;
- RLS thêm predicate nào không.

Capstone phải ghi:

~~~text
query gốc
baseline plan
giả thuyết
index hoặc rewrite
plan sau
latency/buffers
tác dụng phụ write/storage
rollback
~~~

Không dùng EXPLAIN ANALYZE trong benchmark mà không ghi workload, cache state và parameter.

---

## 23. Tenant skew

Capstone yêu cầu có tenant lớn bất thường. Đây là cách tìm lỗi mà dữ liệu trung bình che mất:

~~~text
99 tenant nhỏ
1 tenant chiếm phần lớn users/events/orders
~~~

Test:

- query tenant nhỏ;
- query tenant lớn;
- insert burst vào tenant lớn;
- RLS selectivity;
- index size;
- partition scan;
- autovacuum;
- pool fairness.

Một tenant lớn có thể:

- chiếm worker;
- làm p99 tăng cho tenant nhỏ;
- tạo skew statistics;
- làm cache evict dữ liệu khác;
- giữ transaction/lock lâu;
- làm partition hoặc index phình.

Giải pháp có thể gồm rate limit theo tenant, queue fairness, partition/index design, workload isolation và resource policy.

---

## 24. Autovacuum riêng cho bảng nóng

Events/orders có churn khác nhau. Xem storage parameters:

~~~sql
SELECT
    n.nspname,
    c.relname,
    c.reloptions
FROM pg_class AS c
JOIN pg_namespace AS n ON n.oid = c.relnamespace
WHERE n.nspname = 'platform';
~~~

Ví dụ điều chỉnh events partition:

~~~sql
ALTER TABLE platform.events_2026_09
SET (
    autovacuum_vacuum_scale_factor = 0.02,
    autovacuum_analyze_scale_factor = 0.01,
    autovacuum_vacuum_threshold = 1000,
    autovacuum_analyze_threshold = 500
);
~~~

Đây là ví dụ cần benchmark. Scale factor thấp hơn làm autovacuum chạy thường xuyên hơn, dùng I/O nhiều hơn nhưng giữ statistics/bloat tốt hơn.

Theo dõi:

~~~sql
SELECT
    schemaname,
    relname,
    n_live_tup,
    n_dead_tup,
    n_mod_since_analyze,
    last_autovacuum,
    last_autoanalyze
FROM pg_stat_user_tables
WHERE schemaname = 'platform'
ORDER BY n_dead_tup DESC;
~~~

Không tắt autovacuum trên bảng nóng. Nếu maintenance tranh chấp production, điều chỉnh cost/delay/concurrency và workload, không xóa cơ chế bảo vệ.

---

## 25. Partition maintenance script

Partition kế tiếp cần được tạo trước thời điểm boundary:

~~~sql
CREATE TABLE platform.events_2026_11
PARTITION OF platform.events
FOR VALUES FROM ('2026-11-01 00:00:00+00')
           TO   ('2026-12-01 00:00:00+00');
~~~

Runbook maintenance:

1. tính partition cần có trong cửa sổ;
2. kiểm tra đã tồn tại chưa;
3. tạo partition trong migration;
4. tạo index cần thiết;
5. đặt autovacuum storage parameters;
6. kiểm tra constraint/partition bound;
7. alert nếu partition kế tiếp chưa có;
8. detach/drop partition cũ theo retention và approval.

Detach để archive:

~~~sql
ALTER TABLE platform.events
DETACH PARTITION platform.events_2026_09;
~~~

Không drop partition ngay nếu retention, backup hoặc legal hold chưa cho phép. DDL partition có lock; đánh giá thời điểm và concurrency.

---

## 26. Backup, restore và replication capstone

Capstone cần tối thiểu:

### Logical dump

~~~bash
pg_dump \
  -h primary \
  -U backup_user \
  -d platform \
  -Fc \
  -f platform.dump
~~~

### Restore test

~~~bash
createdb -h restore-host -U postgres platform_restore
pg_restore \
  -h restore-host \
  -U postgres \
  -d platform_restore \
  --exit-on-error \
  platform.dump
~~~

### Physical/standby

- có base backup;
- có WAL archive;
- có standby hoặc lab replication;
- đo replay lag;
- ghi runbook promotion.

### Validation

- tenant count;
- user count;
- event count theo partition;
- order/payment totals;
- RLS test;
- index/extension/role;
- sequence;
- application smoke test.

Replication không thay backup. Read replica không phải backup. Dump không tự cung cấp PITR nếu không có WAL.

---

## 27. Monitoring dashboard capstone

Dashboard phải map tới component:

### Connection/pool

- client connections;
- server connections;
- pool waiting;
- acquisition latency;
- timeout count;
- active/idle/idle in transaction.

### Query

- request rate;
- p50/p95/p99;
- top query total time;
- mean/max query time;
- lock wait;
- deadlock;
- temp bytes.

### Storage/WAL

- disk percent;
- pg_wal size;
- WAL bytes per second;
- checkpoint duration;
- temp file growth;
- table/index size.

### Maintenance

- dead tuple;
- last autovacuum/analyze;
- long transaction;
- partition coverage;
- failed jobs.

### HA

- primary role;
- replica state;
- replay lag;
- slot retained WAL;
- archive failure;
- last backup/restore drill.

Mỗi panel cần:

- unit;
- instance/tenant dimension;
- time window;
- alert threshold;
- runbook link;
- owner.

---

## 28. SLO capstone

Đề xuất SLO để nhóm điều chỉnh:

~~~text
write API availability       ≥ 99.9%
order create p95             ≤ 300 ms
order create p99             ≤ 1 s
dashboard read p95           ≤ 500 ms
connection acquisition p95   ≤ 100 ms
read replica lag             ≤ 5 s trong 99% thời gian
RPO cho committed order      ≤ 1 phút
RTO failover                 ≤ 15 phút
RTO logical restore          ≤ 30 phút
tenant isolation             0 cross-tenant rows
~~~

SLO tenant isolation là correctness/security SLO, không chỉ performance. Một row lộ sai tenant là incident nghiêm trọng dù latency rất tốt.

Đo SLO bằng:

- application metrics;
- database metrics;
- synthetic transaction;
- audit/RLS test;
- restore drill;
- failover drill.

---

## 29. Connection pool exhaustion scenario

### Triệu chứng

- pool waiting tăng;
- acquisition timeout;
- database active connection không tăng tương ứng;
- request retry tăng;
- p95/p99 tăng;
- có idle in transaction hoặc query chậm.

### Điều tra

~~~sql
SELECT
    application_name,
    state,
    wait_event_type,
    count(*)
FROM pg_stat_activity
GROUP BY application_name, state, wait_event_type
ORDER BY count(*) DESC;
~~~

### Hành động

1. giảm retry storm;
2. xác định service giữ connection;
3. kiểm tra query/transaction age;
4. pause batch không cần;
5. tăng pool chỉ sau khi biết nguyên nhân;
6. áp backpressure;
7. rollback deployment nếu có leak;
8. xác minh acquisition latency hồi phục.

Capstone phải mô phỏng pool cạn và ghi rõ việc tăng max_connections không phải phản ứng đầu tiên.

---

## 30. Lock incident scenario

Tình huống: migration giữ lock orders trong giờ cao điểm.

Query:

~~~sql
SELECT
    blocked.pid AS blocked_pid,
    blocker.pid AS blocker_pid,
    blocked.wait_event_type,
    blocked.query AS blocked_query,
    blocker.state AS blocker_state,
    blocker.query AS blocker_query
FROM pg_stat_activity AS blocked
JOIN LATERAL unnest(pg_blocking_pids(blocked.pid)) AS b(pid)
    ON true
JOIN pg_stat_activity AS blocker
    ON blocker.pid = b.pid;
~~~

Runbook:

1. xác định migration owner;
2. đo số request bị ảnh hưởng;
3. dừng/rollback migration nếu an toàn;
4. cancel query hoặc terminate session theo phê duyệt;
5. kiểm tra transaction rollback;
6. chạy lại migration trong window;
7. thêm lock_timeout;
8. ghi incident.

Không terminate PID chỉ từ screenshot cũ; PID có thể đã được tái sử dụng.

---

## 31. Disk full và replication slot scenario

### Triệu chứng

- disk usage tăng;
- pg_wal lớn;
- replica lag hoặc slot inactive;
- archive failed;
- write error.

### Điều tra

~~~sql
SELECT
    slot_name,
    slot_type,
    active,
    restart_lsn,
    pg_size_pretty(
        pg_wal_lsn_diff(pg_current_wal_lsn(), restart_lsn)
    ) AS retained_wal
FROM pg_replication_slots
WHERE restart_lsn IS NOT NULL;
~~~

### Hành động

1. xác định slot owner;
2. kiểm tra consumer có cần không;
3. sửa consumer hoặc re-seed;
4. mở rộng storage nếu cần;
5. drop slot chỉ sau approval;
6. kiểm tra archive và replica;
7. không xóa file trong pg_wal thủ công;
8. ghi RPO impact.

---

## 32. Read replica lag scenario

Read API có thể trả dữ liệu cũ khi replica lag. Router cần:

- loại request;
- replay lag;
- consistency requirement;
- fallback primary;
- timeout và circuit breaker.

Pseudo-routing:

~~~text
if request.requires_read_after_write:
    use primary
else if replica.replay_lag <= 5s and replica.ready:
    use replica
else:
    use primary or fail fast
~~~

Fallback primary có thể làm primary quá tải. Cần giới hạn và alert khi fallback rate tăng.

---

## 33. Migration versioned

Capstone cần migration theo thứ tự:

~~~text
V001 create schemas/roles
V002 create tenants/users/products
V003 create orders/payments
V004 create events partitions
V005 create indexes/GIN/FTS
V006 enable RLS/policies
V007 create functions/triggers
V008 create monitoring views
V009 create next partitions
~~~

Migration phải:

- idempotent ở mức phù hợp;
- có dependency order;
- có lock/time expectation;
- backward-compatible khi deploy rolling;
- có down/rollback hoặc kế hoạch phục hồi;
- có smoke test;
- không chứa secret;
- ghi owner và risk.

---

## 34. Deliverables capstone

### Thiết kế

- ERD;
- topology primary/replica/pooler;
- role/privilege matrix;
- quyết định tenant isolation;
- RPO/RTO/SLO.

### Database code

- DDL;
- versioned migration;
- seed script;
- RLS policy;
- trigger/function cần thiết;
- partition maintenance script;
- index inventory;
- autovacuum setting riêng cho bảng nóng.

### Query và benchmark

- workload generator;
- query tenant nhỏ/lớn;
- order workflow;
- queue worker;
- dashboard query;
- EXPLAIN trước/sau;
- pool sizing experiment;
- latency/throughput/temp/WAL metrics.

### Reliability

- backup/restore runbook;
- replication/failover runbook;
- pool exhaustion checklist;
- lock/deadlock checklist;
- disk/slot/WAL checklist;
- read replica lag checklist.

### Security

- tenant isolation test;
- role/GRANT test;
- SECURITY DEFINER review nếu có;
- secret/TLS plan;
- không dùng superuser cho app.

### Observability

- dashboard;
- alert threshold;
- SLO;
- log/metric field;
- incident response checklist.

---

## 35. Seed data và skew

Mục tiêu tối thiểu:

- 100 tenants;
- 100.000 users;
- 1.000.000 events;
- 200.000 orders;
- 1.000.000 order_items;
- ít nhất một tenant lớn bất thường.

Seed theo batch, tránh một transaction khổng lồ:

~~~sql
INSERT INTO platform.tenants (tenant_key, tenant_name)
SELECT
    'tenant-' || g,
    'Tenant ' || g
FROM generate_series(1, 100) AS g;
~~~

Seed events theo tháng:

~~~sql
INSERT INTO platform.events (
    tenant_id,
    event_type,
    aggregate_type,
    aggregate_id,
    payload,
    occurred_at
)
SELECT
    (1 + floor(random() * 100))::bigint,
    'order.created',
    'order',
    g,
    jsonb_build_object(
        'source', 'seed',
        'priority', (1 + floor(random() * 5))::integer
    ),
    now() - (random() * interval '60 days')
FROM generate_series(1, 100000) AS g;
~~~

Một triệu row cần chia batch và theo dõi WAL/disk. Không chạy seed lớn trên production.

---

## 36. Benchmark protocol

Mọi benchmark phải ghi:

- PostgreSQL version;
- hardware/container limit;
- dataset size;
- cache warm/cold;
- connection pool size;
- PgBouncer mode;
- query parameters;
- concurrency;
- duration;
- warm-up;
- CPU/RAM/I/O;
- result và variance.

Quy trình:

1. baseline;
2. giả thuyết;
3. đổi một biến;
4. chạy workload giống nhau;
5. đo throughput/latency/errors;
6. kiểm tra lock/WAL/temp/disk;
7. rollback nếu không tốt;
8. ghi kết luận.

Không so sánh một lần chạy pool 20 với một lần chạy pool 200 rồi kết luận. Dùng nhiều iteration và cùng trạng thái workload.

---

## 37. Bài tập thực hành

### Bài 1 — Pool sizing

Chạy workload với pool 10, 20, 40, 80. Ghi TPS, p50/p95/p99, CPU, I/O, lock wait và tìm điểm bão hòa.

### Bài 2 — PgBouncer modes

So sánh session pooling và transaction pooling. Test temp table, prepared statement, SET LOCAL, advisory lock và transaction nhiều bước. Ghi feature nào không tương thích.

### Bài 3 — Backpressure

Làm pool cạn có kiểm soát. Đo acquisition timeout, request queue, retry và database load. Thêm bounded retry/circuit breaker.

### Bài 4 — Read/write split

Ghi order ở primary, đọc ngay ở replica và quan sát eventual consistency. Thiết kế sticky-read hoặc LSN-aware read.

### Bài 5 — Tenant isolation

Tạo hai tenant, test SELECT/INSERT/UPDATE/DELETE, view và function. Kỳ vọng không có cross-tenant row.

### Bài 6 — Tenant skew

Tạo tenant chiếm phần lớn events/orders. So sánh plan và latency với tenant nhỏ; điều chỉnh index/partition/pool fairness.

### Bài 7 — Partition boundary

Để thiếu partition kế tiếp, quan sát INSERT fail. Tạo partition, thêm alert và viết maintenance script.

### Bài 8 — Deadlock/retry

Tạo hai transaction lock inventory ngược thứ tự. Sửa lock order, thêm retry bounded và chứng minh deadlock giảm.

### Bài 9 — Restore/failover

Restore logical backup, kiểm tra dữ liệu/RLS, promote standby trong lab và ghi RPO/RTO.

### Bài 10 — Incident game day

Mô phỏng lần lượt:

- pool cạn;
- lock wait;
- deadlock;
- replica lag;
- slot giữ WAL;
- disk gần đầy;
- partition chưa tạo;
- tenant A thử đọc tenant B.

Mỗi scenario cần timeline, evidence, mitigation, verification và follow-up.

---

## 38. Rubric review capstone

| Nhóm | Đạt khi |
|---|---|
| Schema | quan hệ, constraint, tenant key rõ |
| RLS | không lộ cross-tenant row qua table/view/function |
| Query | có baseline/plan sau và lý do index |
| Partition | có partition tương lai và retention plan |
| Pool | sizing dựa trên benchmark, có timeout/backpressure |
| HA | topology, lag monitoring, promotion/fencing |
| Backup | restore test có bằng chứng |
| Security | least privilege, secret ngoài Git |
| Monitoring | dashboard và alert có runbook |
| Operations | xử lý được lock/disk/lag/pool/restore |
| Documentation | ERD, migration, benchmark, runbook đầy đủ |

Không chấm cao cho hệ thống có nhiều tính năng nhưng không có test failure và bằng chứng đo.

---

## 39. Tiêu chí hoàn thành Tuần 16

Bạn hoàn thành bài khi có thể:

- giải thích vì sao max_connections cao có thể làm throughput và latency xấu hơn;
- thiết kế application connection pool theo tổng instance;
- phân biệt session pooling và transaction pooling;
- nhận biết session state không tương thích với transaction pooling;
- cấu hình pool timeout, transaction timeout và backpressure;
- đọc PgBouncer pool/client/server stats;
- thiết kế health check liveness/readiness/data health;
- chọn read/write split theo consistency requirement;
- thiết kế primary/standby/pooler topology;
- đặt SLO cho latency, availability, lag, RPO và RTO;
- triển khai schema capstone multi-tenant;
- dùng partition theo thời gian cho events;
- dùng JSONB GIN và full-text search;
- viết RLS policy và test tenant isolation;
- xử lý order/inventory bằng transaction và lock order nhất quán;
- xây worker với SKIP LOCKED và idempotency;
- benchmark query/dashboard bằng EXPLAIN;
- cấu hình autovacuum riêng cho bảng nóng;
- chạy backup/restore và replication drill;
- chẩn đoán pool cạn, lock, disk đầy và replication lag;
- hoàn thiện deliverables và runbook capstone.

---

## Tóm tắt cần nhớ

1. max_connections cao không thay thế concurrency control.
2. Pool phải được tính trên tổng mọi application instance và worker.
3. Transaction pooling tiết kiệm connection nhưng hạn chế session state.
4. Pool exhaustion cần backpressure, timeout và bounded retry.
5. Read replica có eventual consistency; read-after-write cần policy rõ.
6. Health check phải kiểm tra readiness và data freshness, không chỉ TCP.
7. HA cần fencing; promote khi primary cũ còn writable gây split brain.
8. SLO gồm latency, availability, lag, RPO, RTO và tenant isolation.
9. Capstone phải chứng minh RLS, partition, JSONB, FTS, transaction và queue.
10. Mọi tối ưu cần baseline, giả thuyết, đo lại và rollback.
11. Backup/restore và replication phải được diễn tập, không chỉ cấu hình.
12. Một hệ thống production-like được đánh giá bằng bằng chứng vận hành, không chỉ bằng DDL.

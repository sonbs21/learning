# Tuần 14 nâng cao: Physical và logical replication trong PostgreSQL

> Mục tiêu: hiểu primary/standby, streaming replication, WAL sender/receiver, replication slot, synchronous/asynchronous replication, read replica, replication lag, logical publication/subscription, failover/promotion và cách ngăn split brain.

Replication sao chép thay đổi từ một PostgreSQL instance sang một instance khác. Nó có thể giúp:

- giảm thời gian downtime;
- phục vụ read-only workload;
- tạo standby cho failover;
- đưa dữ liệu sang hệ thống khác bằng logical replication;
- hỗ trợ migration hoặc CDC.

Nhưng replication không thay thế backup. Nếu người dùng xóa nhầm dữ liệu ở primary, thay đổi đó có thể được replicate sang standby. Backup và WAL archive vẫn cần cho PITR.

Mô hình khái quát:

~~~text
Primary
  → WAL sender
  → network
  → WAL receiver
  → standby WAL
  → replay

Publisher
  → logical decoding
  → publication
  → subscription worker
  → subscriber table
~~~

---

## 1. Physical và logical replication khác nhau

| Đặc điểm | Physical replication | Logical replication |
|---|---|---|
| Đơn vị sao chép | WAL/block-level của cả cluster | row change theo table |
| Mục tiêu | standby gần giống primary | database/table khác |
| Version | thường cần major version tương thích | linh hoạt hơn |
| DDL | đi theo physical WAL | không tự động đầy đủ |
| Read-only query | hot standby hỗ trợ | subscriber có thể đọc/ghi riêng |
| Failover | phù hợp promotion | không tự động là standby thay thế |
| Chọn table | không | có publication |
| CDC/integration | không tiện | phù hợp hơn |
| Xung đột | ít ở standby read-only | có thể xảy ra khi subscriber ghi |

Chọn theo mục tiêu:

- HA và disaster recovery → physical streaming replication;
- read replica → physical standby với hot_standby;
- migrate chọn lọc table hoặc CDC → logical replication;
- backup → pg_basebackup, WAL archive, logical dump; replication chỉ là một phần.

---

## 2. Thuật ngữ cốt lõi

### Primary

Instance nhận write và phát sinh WAL.

### Standby

Instance nhận WAL và replay. Standby có thể:

- chỉ phục hồi ở background;
- mở read-only query bằng hot_standby;
- được promote thành primary.

### WAL sender

Process trên primary gửi WAL cho standby hoặc logical subscriber.

### WAL receiver

Process trên physical standby nhận WAL từ primary.

### Replication slot

State trên publisher/primary yêu cầu server giữ WAL hoặc logical decoding state cho consumer.

Slot giúp consumer không bỏ lỡ WAL khi tạm mất kết nối, nhưng slot bị bỏ quên có thể làm pg_wal phình đầy disk.

### LSN

Log Sequence Number là vị trí trong WAL. So sánh LSN giúp đo khoảng cách gửi, ghi, flush và replay.

### Timeline

Nhánh lịch sử WAL. Promotion hoặc PITR tạo timeline mới để phân biệt các lịch sử write.

---

## 3. Môi trường Docker lab

Lab dưới đây minh họa hai container trong cùng network. Đây là môi trường học tập, không phải production topology.

~~~bash
docker network create pg-repl-lab

docker run --name pg-primary \
  --network pg-repl-lab \
  -e POSTGRES_PASSWORD=postgres \
  -e POSTGRES_DB=replication_lab \
  -p 5432:5432 \
  -d postgres:18
~~~

Standby cần data directory được tạo từ base backup, không dùng database init độc lập rồi trộn file. Các bước cấu hình primary và tạo user cần thực hiện trước khi chạy pg_basebackup.

Trong production:

- dùng volume riêng cho primary/standby;
- giới hạn network;
- dùng secret manager;
- giám sát disk/WAL/lag;
- không đặt hai instance cùng ghi một data directory;
- không dùng port mapping rộng tùy tiện.

---

## 4. Cấu hình primary cho physical replication

Trên primary, các setting quan trọng:

~~~sql
ALTER SYSTEM SET wal_level = 'replica';
ALTER SYSTEM SET max_wal_senders = 10;
ALTER SYSTEM SET max_replication_slots = 10;
ALTER SYSTEM SET wal_keep_size = '256MB';
~~~

Ý nghĩa:

- wal_level replica: chứa đủ thông tin cho physical standby;
- max_wal_senders: số process gửi WAL;
- max_replication_slots: số slot được tạo;
- wal_keep_size: lượng WAL tối thiểu giữ cục bộ, không thay slot hoặc archive.

Reload setting có context reload:

~~~sql
SELECT pg_reload_conf();
~~~

Một số setting như wal_level, max_wal_senders hoặc max_replication_slots có thể yêu cầu restart tùy context/version:

~~~sql
SELECT
    name,
    setting,
    context,
    pending_restart
FROM pg_settings
WHERE name IN (
    'wal_level',
    'max_wal_senders',
    'max_replication_slots',
    'wal_keep_size',
    'hot_standby'
);
~~~

Không đổi setting production mà chưa biết context, cần restart hay không và disk/WAL impact.

---

## 5. Replication role và pg_hba.conf

Tạo role phục vụ base backup/streaming:

~~~sql
CREATE ROLE repl_user
WITH LOGIN REPLICATION PASSWORD 'secret-tu-secret-manager';
~~~

Trong pg_hba.conf, cho phép standby kết nối replication từ network hẹp:

~~~text
host    replication    repl_user    172.20.0.0/16    scram-sha-256
~~~

Nếu standby cần kết nối database để health check, có thể thêm rule database cụ thể; không mở quyền rộng hơn nhu cầu.

Reload:

~~~sql
SELECT pg_reload_conf();
~~~

Kiểm tra connection:

~~~bash
psql "host=pg-primary dbname=replication_lab user=repl_user sslmode=require"
~~~

Production nên:

- dùng TLS và verify certificate;
- giới hạn source address;
- không dùng password trong command history;
- rotation secret có kế hoạch;
- không cho role replication quyền login từ toàn Internet.

---

## 6. Tạo physical standby bằng pg_basebackup

Standby bắt đầu từ một base backup nhất quán:

~~~bash
mkdir -p /var/lib/postgresql/standby

pg_basebackup \
  -h pg-primary \
  -U repl_user \
  -D /var/lib/postgresql/standby \
  -Fp \
  -Xs \
  -P \
  -R
~~~

Tùy chọn:

- -D: data directory đích;
- -Fp: plain format;
- -Xs: stream WAL trong lúc backup;
- -P: progress;
- -R: tạo cấu hình kết nối standby, gồm standby.signal và primary_conninfo theo version.

Không chạy pg_basebackup vào data directory đang chứa cluster khác. Kiểm tra quyền:

~~~bash
chown -R postgres:postgres /var/lib/postgresql/standby
chmod 700 /var/lib/postgresql/standby
~~~

Nếu không dùng -R, cần tạo thủ công:

~~~bash
touch /var/lib/postgresql/standby/standby.signal
~~~

và đặt primary_conninfo trong cấu hình:

~~~text
primary_conninfo = 'host=pg-primary port=5432 user=repl_user password=... application_name=standby1'
~~~

Không commit password trong postgresql.auto.conf. Dùng .pgpass với permission 0600 hoặc cơ chế secret của môi trường.

---

## 7. Replication slot physical

Tạo physical slot trên primary:

~~~sql
SELECT *
FROM pg_create_physical_replication_slot('standby1_slot');
~~~

Để standby dùng slot, đặt:

~~~text
primary_slot_name = 'standby1_slot'
~~~

Slot giữ WAL cho standby chưa replay. Điều này bảo vệ standby khỏi thiếu WAL sau gián đoạn ngắn, nhưng slot không tự giải phóng khi consumer chết.

Tạo slot trước khi base backup hoặc dùng option slot trong quy trình pg_basebackup:

~~~bash
pg_basebackup \
  -h pg-primary \
  -U repl_user \
  -D /var/lib/postgresql/standby \
  -Fp \
  -Xs \
  -P \
  -R \
  -S standby1_slot \
  -C
~~~

- -S: tên slot;
- -C: tạo slot nếu chưa tồn tại.

Kiểm tra slot:

~~~sql
SELECT
    slot_name,
    slot_type,
    active,
    restart_lsn,
    confirmed_flush_lsn,
    wal_status,
    safe_wal_size
FROM pg_replication_slots;
~~~

Một số cột phụ thuộc version. Dùng SELECT phù hợp với PostgreSQL đang chạy.

---

## 8. Khởi động standby và theo dõi trạng thái

Khởi động instance bằng cách triển khai PostgreSQL theo data directory standby. Với service hệ điều hành:

~~~bash
pg_ctl -D /var/lib/postgresql/standby start
~~~

Trên primary:

~~~sql
SELECT
    pid,
    usename,
    application_name,
    client_addr,
    state,
    sync_state,
    sent_lsn,
    write_lsn,
    flush_lsn,
    replay_lsn,
    write_lag,
    flush_lag,
    replay_lag
FROM pg_stat_replication;
~~~

Ý nghĩa:

- sent_lsn: primary đã gửi đến đâu;
- write_lsn: standby đã ghi WAL đến đâu;
- flush_lsn: standby đã flush WAL;
- replay_lsn: standby đã apply đến đâu;
- write/flush/replay lag: khoảng trễ tương ứng;
- state: streaming, catchup hoặc startup;
- sync_state: async, sync, quorum hoặc potential.

Trên standby:

~~~sql
SELECT
    status,
    receive_start_lsn,
    written_lsn,
    flushed_lsn,
    received_tli,
    latest_end_lsn,
    latest_end_time,
    slot_name,
    conninfo
FROM pg_stat_wal_receiver;

SELECT
    pg_is_in_recovery(),
    pg_last_wal_receive_lsn(),
    pg_last_wal_replay_lsn(),
    pg_last_xact_replay_timestamp();
~~~

pg_is_in_recovery trả true khi instance còn là standby. Sau promotion, giá trị chuyển false.

---

## 9. Đo replication lag

Lag không chỉ có một loại. Có thể đo:

1. primary đã gửi nhưng standby chưa nhận;
2. standby đã nhận nhưng chưa flush;
3. standby đã flush nhưng chưa replay;
4. thời gian từ transaction commit trên primary đến replay trên standby.

Khoảng cách LSN:

~~~sql
SELECT
    application_name,
    pg_size_pretty(
        pg_wal_lsn_diff(sent_lsn, replay_lsn)
    ) AS byte_lag
FROM pg_stat_replication
WHERE application_name = 'standby1';
~~~

Đo theo timestamp trên standby:

~~~sql
SELECT
    clock_timestamp() - pg_last_xact_replay_timestamp()
        AS replay_time_lag;
~~~

Nếu standby không có transaction mới, timestamp có thể cũ và làm time lag trông lớn dù không có thay đổi đang chờ. Kết hợp LSN lag, WAL receiver state, workload và metric commit.

Alert nên dựa trên:

- standby không streaming;
- LSN lag vượt ngưỡng;
- replay lag vượt SLA;
- WAL archive/slot giữ quá lớn;
- disk còn thấp;
- replication conflicts tăng;
- synchronous commit bắt đầu timeout.

---

## 10. Asynchronous replication

Mặc định physical streaming thường là asynchronous. Primary commit mà không chờ standby replay xong:

~~~text
client commit
  → primary flush WAL
  → trả response
  → standby nhận/replay sau
~~~

Ưu điểm:

- latency write thấp;
- primary không bị block ngay khi standby chậm;
- phù hợp read replica và DR có thể chấp nhận RPO khác 0.

Rủi ro:

- primary hỏng trước khi WAL đến standby;
- mất các transaction cuối cùng;
- standby lag khiến read-after-write không nhất quán;
- promote standby có thể thiếu dữ liệu.

Async không có nghĩa là replication không đáng tin; nó chỉ có semantics khác về durability và RPO.

---

## 11. Synchronous replication

Synchronous replication yêu cầu primary chờ một hoặc nhiều standby xác nhận WAL theo cấu hình:

~~~sql
ALTER SYSTEM SET synchronous_standby_names = 'FIRST 1 (standby1)';
~~~

Có thể chọn quorum:

~~~sql
ALTER SYSTEM SET synchronous_standby_names = 'ANY 1 (standby1, standby2)';
~~~

Reload:

~~~sql
SELECT pg_reload_conf();
~~~

Tên trong cấu hình phải khớp application_name của standby trong primary_conninfo.

Điều khiển mức chờ bằng synchronous_commit:

~~~sql
SET LOCAL synchronous_commit = 'remote_flush';
~~~

Các mức thường gặp:

- local: chờ flush tại primary;
- remote_write: chờ standby ghi WAL;
- remote_flush: chờ standby flush WAL;
- remote_apply: chờ standby replay WAL;
- on: semantics mặc định, với synchronous standby thường chờ remote flush.

Trade-off:

~~~text
durability/RPO tốt hơn
  ↔ latency và phụ thuộc vào standby
~~~

Nếu synchronous standby mất kết nối, commit có thể chờ hoặc fail tùy cấu hình timeout/failover. Không bật synchronous chỉ vì muốn “an toàn hơn” mà chưa đo latency và behavior khi standby chết.

---

## 12. Hot standby và read replica

Cho phép query read-only trên standby:

~~~text
hot_standby = on
~~~

Kiểm tra:

~~~sql
SHOW hot_standby;
SELECT pg_is_in_recovery();
~~~

Ứng dụng read replica phải chấp nhận:

- eventual consistency;
- read-after-write có thể chưa thấy dữ liệu;
- query dài có thể conflict với WAL replay;
- replica có thể tạm ngừng query để replay hoặc ngược lại;
- không được gửi INSERT/UPDATE/DELETE vào standby.

Router cần biết role hiện tại và health, không chỉ hardcode endpoint. Sau promotion, endpoint read/write phải được cập nhật qua cơ chế quản lý service.

---

## 13. Recovery conflict và hot_standby_feedback

Standby đọc dữ liệu trong snapshot cũ có thể giữ tuple mà primary muốn dọn. WAL replay trên standby có thể conflict với query:

~~~sql
SELECT
    datname,
    confl_tablespace,
    confl_lock,
    confl_snapshot,
    confl_bufferpin,
    confl_deadlock
FROM pg_stat_database_conflicts;
~~~

hot_standby_feedback cho phép standby báo xmin về primary:

~~~text
hot_standby_feedback = on
~~~

Ưu điểm: giảm một số snapshot conflict. Nhược điểm: transaction/query dài trên standby có thể làm primary giữ dead tuple lâu hơn, tăng bloat.

Không bật mù quáng. Chọn dựa trên:

- workload read replica;
- độ dài query;
- tolerance bị cancel;
- autovacuum/bloat;
- dung lượng storage.

---

## 14. Replication slot và WAL retention

Xem physical/logical slot:

~~~sql
SELECT
    slot_name,
    slot_type,
    plugin,
    database,
    active,
    restart_lsn,
    confirmed_flush_lsn
FROM pg_replication_slots;
~~~

Ước lượng WAL bị giữ:

~~~sql
SELECT
    slot_name,
    pg_size_pretty(
        pg_wal_lsn_diff(pg_current_wal_lsn(), restart_lsn)
    ) AS retained_wal
FROM pg_replication_slots
WHERE restart_lsn IS NOT NULL;
~~~

Nếu slot inactive lâu:

1. xác định consumer còn cần không;
2. kiểm tra endpoint và credential;
3. đo retained WAL và disk;
4. sửa consumer hoặc xóa slot theo phê duyệt;
5. không xóa slot đang phục vụ standby khỏe.

Xóa slot:

~~~sql
SELECT pg_drop_replication_slot('standby1_slot');
~~~

Không chạy lệnh này chỉ để “dọn cho sạch”. Xóa slot có thể làm standby mất WAL cần thiết và phải reinitialize từ base backup.

Giới hạn giữ WAL:

~~~sql
ALTER SYSTEM SET max_slot_wal_keep_size = '10GB';
~~~

Giới hạn giúp bảo vệ disk nhưng có thể làm consumer không còn WAL liên tục. Khi đó cần re-seed/reinitialize. Đây là trade-off an toàn disk và khả năng catch up.

---

## 15. Logical replication: publisher và subscriber

Logical replication đọc thay đổi từ WAL qua logical decoding rồi áp dụng vào table subscriber.

~~~text
publisher
  → publication
  → logical replication slot
  → subscription connection
  → apply worker
  → subscriber
~~~

Đặc điểm:

- chọn table;
- có thể phục vụ migration;
- phù hợp CDC;
- subscriber có thể có schema riêng;
- DDL không tự đồng bộ đầy đủ;
- xung đột ở subscriber có thể làm worker dừng;
- cần quản lý slot và initial copy.

Logical replication không phải bản sao physical của cluster. Nó không tự copy role, function, trigger, extension, sequence semantics hoặc mọi DDL.

---

## 16. Cấu hình publisher cho logical replication

Publisher cần wal_level logical:

~~~sql
ALTER SYSTEM SET wal_level = 'logical';
ALTER SYSTEM SET max_replication_slots = 10;
ALTER SYSTEM SET max_wal_senders = 10;
~~~

wal_level logical cần restart. Kiểm tra:

~~~sql
SELECT name, setting, context, pending_restart
FROM pg_settings
WHERE name IN (
    'wal_level',
    'max_replication_slots',
    'max_wal_senders'
);
~~~

Tạo user:

~~~sql
CREATE ROLE logical_repl
WITH LOGIN REPLICATION PASSWORD 'secret-tu-secret-manager';
~~~

pg_hba.conf cho subscriber:

~~~text
host    replication    logical_repl    172.20.0.0/16    scram-sha-256
host    replication_lab logical_repl   172.20.0.0/16    scram-sha-256
~~~

Role cần quyền SELECT trên table publication để initial copy và quyền phù hợp để đọc thay đổi theo version/configuration:

~~~sql
GRANT CONNECT ON DATABASE replication_lab TO logical_repl;
GRANT USAGE ON SCHEMA sales TO logical_repl;
GRANT SELECT ON sales.customers, sales.orders TO logical_repl;
~~~

---

## 17. Tạo publication

Publication cho hai table:

~~~sql
CREATE PUBLICATION sales_pub
FOR TABLE sales.customers, sales.orders;
~~~

Publication mọi table hiện tại và table tạo sau tùy phiên bản/option:

~~~sql
CREATE PUBLICATION sales_all_pub
FOR ALL TABLES;
~~~

Không dùng FOR ALL nếu chỉ cần một nhóm table. Phạm vi càng rộng, dữ liệu càng dễ bị gửi sang subscriber ngoài ý muốn.

Chọn operation:

~~~sql
CREATE PUBLICATION orders_insert_update_pub
FOR TABLE sales.orders
WITH (publish = 'insert, update');
~~~

Xem publication:

~~~sql
SELECT
    pubname,
    puballtables,
    pubinsert,
    pubupdate,
    pubdelete,
    pubtruncate
FROM pg_publication;

SELECT *
FROM pg_publication_tables
WHERE pubname = 'sales_pub';
~~~

Publication không tự tạo table ở subscriber. Schema và table đích phải được migration trước.

---

## 18. Replica identity và UPDATE/DELETE

Logical replication cần xác định row đích khi UPDATE/DELETE. Table có primary key thường dùng replica identity mặc định.

Kiểm tra:

~~~sql
SELECT
    n.nspname,
    c.relname,
    c.relreplident
FROM pg_class AS c
JOIN pg_namespace AS n ON n.oid = c.relnamespace
WHERE n.nspname = 'sales'
  AND c.relkind = 'r';
~~~

Nếu table không có primary key:

~~~sql
ALTER TABLE sales.events REPLICA IDENTITY FULL;
~~~

FULL có thể tốn hơn vì cần so sánh toàn bộ row. Tốt hơn thường là thiết kế key phù hợp:

~~~sql
ALTER TABLE sales.events
REPLICA IDENTITY USING INDEX events_unique_key;
~~~

Không để UPDATE/DELETE table không có identity rồi mới phát hiện apply worker lỗi. Test từng operation trong staging.

---

## 19. Tạo subscriber

Trên subscriber, tạo schema/table tương thích:

~~~sql
CREATE SCHEMA sales;

CREATE TABLE sales.customers (
    customer_id bigint PRIMARY KEY,
    email text NOT NULL UNIQUE,
    full_name text NOT NULL,
    created_at timestamptz NOT NULL
);

CREATE TABLE sales.orders (
    order_id bigint PRIMARY KEY,
    customer_id bigint NOT NULL,
    status text NOT NULL,
    amount numeric(12, 2) NOT NULL,
    created_at timestamptz NOT NULL
);
~~~

Tạo subscription:

~~~sql
CREATE SUBSCRIPTION sales_sub
CONNECTION 'host=pg-primary port=5432 dbname=replication_lab user=logical_repl password=...'
PUBLICATION sales_pub
WITH (
    copy_data = true,
    create_slot = true,
    slot_name = 'sales_sub_slot'
);
~~~

Production không hardcode password trong migration log. Dùng credential management và cẩn thận vì connection string có thể xuất hiện trong system catalog hoặc process metadata theo quyền.

copy_data=true chạy initial table copy trước khi apply thay đổi. Với table lớn, initial copy cần tính thời gian, lock/scan impact và dung lượng.

---

## 20. Theo dõi logical replication

Trên subscriber:

~~~sql
SELECT
    subname,
    pid,
    relid,
    received_lsn,
    last_msg_send_time,
    last_msg_receipt_time,
    latest_end_lsn,
    latest_end_time,
    status
FROM pg_stat_subscription;
~~~

Trên publisher:

~~~sql
SELECT
    slot_name,
    slot_type,
    database,
    active,
    restart_lsn,
    confirmed_flush_lsn
FROM pg_replication_slots
WHERE slot_type = 'logical';
~~~

Kiểm tra publication/subscription:

~~~sql
SELECT * FROM pg_publication;
SELECT * FROM pg_subscription;
~~~

Subscriber có thể không cập nhật vì:

- table schema không tương thích;
- duplicate key;
- thiếu replica identity;
- constraint reject row;
- subscription worker bị stop;
- network/credential lỗi;
- slot giữ WAL nhưng consumer không tiến.

Khi apply worker lỗi, đọc log và sửa nguyên nhân trước khi resume. Không bỏ qua lỗi bằng cách xóa slot/subscription nếu chưa biết dữ liệu nào đã apply.

---

## 21. Schema migration với logical replication

Logical replication không tự đồng bộ mọi DDL. Quy trình an toàn thường là:

1. thay đổi schema tương thích trên subscriber trước;
2. thay đổi publisher;
3. giữ backward compatibility trong thời gian chuyển tiếp;
4. kiểm tra apply;
5. xóa column/constraint cũ sau khi mọi consumer đã nâng cấp.

Ví dụ thêm nullable column:

~~~sql
ALTER TABLE sales.orders ADD COLUMN source text;
~~~

Thay đổi breaking như drop column, đổi type hoặc rename cần migration coordination. Không giả định publication sẽ tự copy DDL.

Sequence cũng cần kế hoạch riêng. Logical replication copy row value nhưng không nhất thiết đồng bộ sequence state như một physical standby. Sau initial copy, kiểm tra sequence khi subscriber nhận write riêng.

---

## 22. Logical replication và xung đột

Mô hình publisher một chiều thường giảm xung đột. Nếu subscriber cũng cho application ghi vào cùng row:

- duplicate key;
- update không tìm thấy row;
- constraint conflict;
- khác giá trị do write local;
- apply worker dừng.

Không dùng hai chiều nếu chưa có conflict resolution rõ ràng. PostgreSQL logical replication không tự biến hai database thành hệ thống multi-master an toàn.

Nếu cần subscriber writable cho local metadata, tách schema/table rõ ràng hoặc xác định owner của từng cột/row. Đừng để hai hệ thống cùng là source of truth.

---

## 23. Failover và promotion physical standby

Promotion là biến standby thành primary:

~~~sql
SELECT pg_promote(true, 60);
~~~

Hoặc:

~~~bash
pg_ctl -D /var/lib/postgresql/standby promote
~~~

Kiểm tra:

~~~sql
SELECT pg_is_in_recovery();
SELECT pg_current_wal_lsn();
SELECT timeline_id
FROM pg_control_checkpoint();
~~~

Sau promotion, pg_is_in_recovery phải false. Nhưng promotion thành công không đồng nghĩa hệ thống đã an toàn để nhận traffic.

Quy trình failover:

1. phát hiện primary lỗi;
2. xác định standby được chọn;
3. xác nhận standby khỏe và replay position;
4. fence/isolate primary cũ;
5. promote đúng một standby;
6. cập nhật endpoint/DNS/service discovery;
7. kiểm tra write/read;
8. cấu hình các replica theo timeline mới;
9. backup lại cluster mới;
10. ghi incident và RPO thực tế.

---

## 24. Split brain

Split brain xảy ra khi hai instance cùng nghĩ mình là primary và cùng nhận write. Hậu quả:

- dữ liệu phân nhánh;
- conflict khi nhập lại;
- mất write;
- sequence/identity không nhất quán;
- không biết nguồn nào là đúng.

Không promote standby khi primary cũ còn có khả năng nhận write mà chưa fencing. Fencing có thể gồm:

- tắt instance cũ;
- revoke network route/security group;
- remove endpoint;
- stop service;
- power fencing/STONITH trong hạ tầng hỗ trợ;
- khóa write ở application;
- lease/consensus từ hệ thống điều phối.

DNS đổi endpoint một mình chưa chắc đủ vì client có thể cache địa chỉ hoặc primary cũ vẫn accessible. Sau promotion, chỉ một endpoint được coi là writable.

---

## 25. Rejoin primary cũ sau failover

Primary cũ không nên tự động trở lại làm primary sau khi standby đã promote. Nó có thể nằm ở timeline cũ.

Các hướng:

- reinitialize bằng base backup từ primary mới;
- dùng pg_rewind nếu điều kiện phù hợp;
- tạo standby mới từ primary hiện tại;
- đảm bảo mọi write endpoint cũ đã bị fence.

pg_rewind có thể tiết kiệm thời gian nhưng cần wal_log_hints hoặc data checksums và đủ WAL/common history theo điều kiện của phiên bản. Không dùng pg_rewind như nút thần kỳ; kiểm tra precondition, backup và dry run.

Quy trình rejoin:

1. stop primary cũ;
2. xác định timeline/current primary;
3. kiểm tra pg_rewind eligibility;
4. rewind hoặc re-seed;
5. tạo standby.signal/primary_conninfo;
6. start read-only;
7. xác minh streaming;
8. không đưa vào pool write trước khi health check đạt.

---

## 26. Physical failover và RPO

Async standby có thể thiếu WAL cuối cùng:

~~~text
primary commit T1
primary trả response
primary hỏng trước khi gửi T1
standby promote
T1 mất
~~~

RPO thực tế phụ thuộc:

- lag tại thời điểm lỗi;
- WAL đã archive hay chưa;
- sync/async;
- thời gian phát hiện;
- dữ liệu nào được client xác nhận.

Ghi nhận LSN/replay trước promotion để ước lượng phần dữ liệu không có trên standby. Không báo “zero data loss” nếu topology không có cơ chế chứng minh điều đó.

---

## 27. Logical replication cho migration

Logical replication có thể hỗ trợ blue/green migration:

~~~text
database cũ publisher
  → initial copy
  → catch up
  → validation
  → pause write ngắn
  → apply hết lag
  → chuyển application sang database mới
~~~

Checklist:

- schema tương thích;
- extension/function/role tạo riêng;
- publication chỉ chứa table cần;
- primary key/replica identity đúng;
- sequence được xử lý;
- DDL backward-compatible;
- lag thấp và có metric;
- cutover có write freeze;
- rollback plan rõ;
- không để hai database cùng nhận write sau cutover.

Logical replication có thể giảm downtime nhưng không tự giải quyết application compatibility hoặc dữ liệu phát sinh ngoài publication.

---

## 28. Logical replication cho CDC

CDC đọc row change để gửi sang hệ thống khác. Cần quyết định:

- event có chứa before/after image không;
- thứ tự event cần đến mức nào;
- consumer có idempotent không;
- retention khi consumer down;
- schema evolution;
- delete/tombstone;
- transaction boundary;
- replay từ slot đến đâu.

Không coi stream CDC là audit hoàn chỉnh nếu chưa định nghĩa durability, ordering và xử lý retry. Replication slot giữ WAL/decoded changes, nên consumer chết lâu có thể làm disk đầy.

---

## 29. Backup và replication phải đi cùng nhau

Topology tối thiểu production thường cần:

~~~text
primary
  → physical standby cho HA/DR
  → WAL archive cho PITR
  → logical dump định kỳ cho portability/object restore
~~~

Mỗi lớp giải quyết vấn đề khác:

- standby: sẵn sàng và failover;
- WAL archive: phục hồi thời điểm;
- logical dump: khôi phục object/table hoặc di chuyển;
- logical subscriber: tích hợp/migration/CDC.

Kiểm tra restore từ backup độc lập. Không dùng standby làm bằng chứng duy nhất rằng có thể phục hồi.

---

## 30. Monitoring dashboard tối thiểu

### Primary physical replication

~~~sql
SELECT
    application_name,
    state,
    sync_state,
    client_addr,
    pg_size_pretty(pg_wal_lsn_diff(sent_lsn, replay_lsn))
        AS replay_byte_lag,
    replay_lag
FROM pg_stat_replication;
~~~

### Standby

~~~sql
SELECT
    pg_is_in_recovery(),
    pg_last_wal_receive_lsn(),
    pg_last_wal_replay_lsn(),
    pg_last_xact_replay_timestamp();

SELECT *
FROM pg_stat_wal_receiver;

SELECT *
FROM pg_stat_database_conflicts;
~~~

### Slot

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

### Logical

~~~sql
SELECT
    subname,
    pid,
    received_lsn,
    latest_end_lsn,
    latest_end_time,
    status
FROM pg_stat_subscription;
~~~

Alert quan trọng:

- no active sender/receiver;
- state không streaming;
- lag vượt SLA;
- slot inactive giữ WAL lớn;
- disk còn thấp;
- apply worker lỗi;
- conflict tăng;
- standby không replay;
- synchronous commit chờ quá lâu.

---

## 31. Lab physical replication hoàn chỉnh

### Bước 1: Chuẩn bị primary

~~~sql
ALTER SYSTEM SET wal_level = 'replica';
ALTER SYSTEM SET max_wal_senders = 10;
ALTER SYSTEM SET max_replication_slots = 10;
CREATE ROLE repl_user
WITH LOGIN REPLICATION PASSWORD 'lab-secret';
~~~

Thêm pg_hba rule, reload hoặc restart theo pending_restart.

### Bước 2: Tạo slot

~~~sql
SELECT pg_create_physical_replication_slot('standby1_slot');
~~~

### Bước 3: Base backup

~~~bash
pg_basebackup \
  -h pg-primary \
  -U repl_user \
  -D /var/lib/postgresql/standby \
  -Fp -Xs -P -R \
  -S standby1_slot
~~~

### Bước 4: Bật standby

Đặt application_name=standby1 và primary_slot_name=standby1_slot, sau đó start instance.

### Bước 5: Sinh write

~~~sql
INSERT INTO sales.customers (email, full_name)
VALUES ('repl@example.com', 'Replication Test');

SELECT pg_current_wal_lsn();
~~~

### Bước 6: Kiểm tra

Trên primary xem pg_stat_replication. Trên standby chạy SELECT và đo receive/replay LSN.

### Bước 7: Tạo lag có chủ ý

Tạm dừng standby hoặc tạo workload ghi lớn trong lab. Theo dõi:

- replay_lsn;
- retained WAL;
- disk;
- slot active;
- thời gian catch-up.

Không tạo workload lớn trên production chỉ để minh họa nếu chưa có giới hạn.

---

## 32. Lab promotion và chống split brain

### Bước 1: Ghi nhận trạng thái

~~~sql
SELECT
    pg_is_in_recovery(),
    pg_last_wal_replay_lsn(),
    pg_last_xact_replay_timestamp();
~~~

### Bước 2: Fence primary giả lập

Trong lab, dừng primary hoặc chặn network trước khi promote standby. Production cần fencing thật được phê duyệt.

### Bước 3: Promote

~~~sql
SELECT pg_promote(true, 60);
~~~

### Bước 4: Xác minh

~~~sql
SELECT pg_is_in_recovery();
SELECT pg_current_wal_lsn();
~~~

### Bước 5: Test write

~~~sql
INSERT INTO sales.customers (email, full_name)
VALUES ('after-promote@example.com', 'After Promote');
~~~

### Bước 6: Không cho primary cũ nhận write

Kiểm tra endpoint, DNS, pool và job. Sau đó mới tạo lại standby từ primary mới hoặc dùng pg_rewind theo điều kiện phù hợp.

---

## 33. Lab logical replication hoàn chỉnh

### Bước 1: Chuẩn bị publisher

Đặt wal_level=logical, max_replication_slots và max_wal_senders, restart nếu cần. Tạo logical_repl role và pg_hba rule.

### Bước 2: Publication

~~~sql
CREATE PUBLICATION sales_pub
FOR TABLE sales.customers, sales.orders;
~~~

### Bước 3: Chuẩn bị subscriber

Tạo database/schema/table có cấu trúc tương thích. Không trông chờ subscription tự tạo DDL.

### Bước 4: Subscription

~~~sql
CREATE SUBSCRIPTION sales_sub
CONNECTION 'host=pg-primary dbname=replication_lab user=logical_repl password=...'
PUBLICATION sales_pub
WITH (copy_data = true, create_slot = true);
~~~

### Bước 5: Test thay đổi

Trên publisher:

~~~sql
INSERT INTO sales.customers (email, full_name)
VALUES ('logical@example.com', 'Logical Test');

UPDATE sales.orders
SET status = 'paid'
WHERE order_id = 1;
~~~

Trên subscriber, kiểm tra row và pg_stat_subscription.

### Bước 6: Test lỗi có chủ ý

Tạo duplicate key hoặc schema không tương thích trong lab. Quan sát apply worker dừng, đọc log, sửa dữ liệu/schema rồi resume. Không xóa subscription để che lỗi.

---

## 34. Các lỗi thường gặp

### Nghĩ replication là backup

Xóa nhầm trên primary có thể replicate sang standby. Vẫn cần logical dump, base backup và WAL archive.

### Promote khi primary cũ chưa fence

Đây là con đường trực tiếp tới split brain. Fencing trước, promote sau.

### Quên replication slot

Slot inactive giữ WAL vô hạn hoặc đến giới hạn. Disk đầy có thể làm primary gặp sự cố.

### Dùng wal_keep_size như backup

wal_keep_size chỉ giữ tối thiểu WAL cục bộ; không thay slot, WAL archive hoặc base backup.

### Không monitor lag

Standby có thể “đang streaming” nhưng replay lag hàng giờ. Theo dõi cả LSN và thời gian.

### Bật synchronous mà không tính latency

Commit có thể chờ standby và ảnh hưởng SLA khi network/standby lỗi.

### Gửi write vào read replica

Hot standby chỉ read-only. Router phải phân biệt endpoint.

### Bật hot_standby_feedback vô điều kiện

Query dài trên standby có thể làm primary bloat. Theo dõi dead tuple và autovacuum.

### Nghĩ logical replication đồng bộ DDL

Schema migration phải chạy riêng, backward-compatible và có cutover plan.

### Không có primary key/replica identity

UPDATE/DELETE có thể không apply được hoặc phải dùng REPLICA IDENTITY FULL tốn chi phí.

### Cho subscriber ghi cùng row

Dễ tạo conflict và apply worker dừng. Xác định một source of truth.

### Không reinitialize standby sau missing WAL

Nếu standby mất WAL cần thiết, tạo lại từ base backup hoặc dùng quy trình rewind phù hợp; không chỉ restart nhiều lần.

---

## 35. Runbook failover

### Trước sự cố

- có danh sách primary/standby endpoint;
- có health check và lag threshold;
- biết standby nào đủ mới;
- có fencing mechanism;
- có owner phê duyệt promotion;
- có DNS/service discovery plan;
- đã diễn tập;
- backup và WAL archive vẫn hoạt động.

### Trong sự cố

1. xác nhận primary lỗi, tránh false positive;
2. kiểm tra standby state và lag;
3. fence primary cũ;
4. chọn đúng một standby;
5. promote;
6. xác nhận pg_is_in_recovery=false;
7. cập nhật write endpoint;
8. kiểm tra application transaction;
9. đo RPO thực tế;
10. thông báo trạng thái.

### Sau sự cố

1. backup primary mới;
2. kiểm tra timeline;
3. re-seed hoặc rewind primary cũ;
4. dựng lại standby;
5. kiểm tra slot/WAL archive;
6. review incident;
7. cập nhật runbook và alert.

---

## 36. Bài tập thực hành

### Bài 1 — Physical standby

Dựng primary/standby, tạo base backup, bật streaming và chứng minh row mới xuất hiện trên standby.

### Bài 2 — Lag

Tạm dừng replay trong lab, tạo workload, đo sent/write/flush/replay LSN và retained WAL.

### Bài 3 — Replication slot

Tạo slot, ngắt consumer, quan sát WAL tăng, sau đó khôi phục consumer và ghi lại điều kiện catch-up.

### Bài 4 — Synchronous replication

Cấu hình một synchronous standby, so sánh latency commit khi standby khỏe và khi standby mất kết nối. Khôi phục cấu hình sau lab.

### Bài 5 — Promotion

Fence primary trong lab, promote standby, ghi dữ liệu mới và tạo lại standby từ primary mới.

### Bài 6 — Logical publication

Tạo publication chỉ gồm customers/orders, tạo subscriber, kiểm tra INSERT/UPDATE/DELETE và initial copy.

### Bài 7 — Logical schema change

Thêm nullable column ở subscriber trước, sau đó publisher. Test DDL breaking và viết migration tương thích.

### Bài 8 — Failover report

Viết báo cáo có topology, LSN trước promotion, RPO, thời gian promote, bước fence, validation và kế hoạch rejoin.

---

## 37. Tiêu chí hoàn thành Tuần 14

Bạn hoàn thành bài khi có thể:

- phân biệt physical và logical replication;
- giải thích primary, standby, WAL sender và WAL receiver;
- tạo physical standby từ pg_basebackup;
- cấu hình replication role và pg_hba.conf;
- tạo và theo dõi physical replication slot;
- đo sent/write/flush/replay lag;
- phân biệt async và synchronous replication;
- giải thích remote_write, remote_flush và remote_apply;
- vận hành hot standby/read replica;
- nhận biết recovery conflict và hot_standby_feedback;
- tạo publication/subscription;
- kiểm tra replica identity và primary key;
- theo dõi logical apply worker và slot;
- phối hợp schema migration với logical replication;
- hiểu logical conflict và source of truth;
- promote standby trong lab;
- fence primary cũ và tránh split brain;
- rejoin primary cũ bằng re-seed hoặc pg_rewind theo điều kiện;
- giải thích vì sao replication không thay backup;
- viết runbook failover và restore validation.

---

## Tóm tắt cần nhớ

1. Physical replication sao chép WAL của cluster; logical replication sao chép row change theo publication.
2. Standby chỉ an toàn khi được theo dõi lag, WAL và disk.
3. Replication slot bảo vệ consumer nhưng có thể giữ WAL làm đầy disk.
4. Async có thể mất transaction cuối; sync đổi latency lấy durability tốt hơn.
5. Read replica có eventual consistency và recovery conflict.
6. hot_standby_feedback có thể giảm conflict nhưng tăng bloat trên primary.
7. Logical replication không tự đồng bộ đầy đủ DDL, role, extension và sequence.
8. UPDATE/DELETE logical replication cần primary key hoặc replica identity.
9. Failover phải fence primary cũ trước khi promote.
10. Promotion tạo timeline mới; primary cũ cần re-seed hoặc rewind trước khi rejoin.
11. Replication không thay thế logical dump, physical backup và WAL archive.
12. Mọi topology replication cần monitoring, restore drill và runbook vận hành.

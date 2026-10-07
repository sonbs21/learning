# Tuần 13 nâng cao: Backup, restore và Point-in-Time Recovery trong PostgreSQL

> Mục tiêu: phân biệt logical backup và physical backup, dùng pg_dump/pg_dumpall/pg_restore, tạo base backup, lưu WAL archive, phục hồi database mới, thực hiện PITR, hiểu RPO/RTO, mã hóa và retention, đồng thời viết runbook restore có bước xác minh.

Backup chưa được chứng minh là backup cho tới khi restore thử thành công. Một file dump tồn tại trên disk chỉ chứng minh rằng lệnh dump đã tạo file; nó chưa chứng minh:

- file có thể đọc được;
- backup không bị cắt giữa chừng;
- có đủ WAL để phục hồi tới thời điểm cần;
- credential và quyền restore còn hoạt động;
- disk đích đủ lớn;
- ứng dụng có thể chạy với dữ liệu đã phục hồi;
- đội vận hành biết các bước phục hồi khi có sự cố.

Mô hình khái quát:

~~~text
Logical dump
  → khôi phục object/data qua SQL

Physical base backup + WAL archive
  → dựng lại cluster
  → replay WAL
  → phục hồi tới thời điểm mục tiêu
~~~

---

## 1. RPO, RTO và mục tiêu phục hồi

### RPO

Recovery Point Objective là lượng dữ liệu tối đa có thể mất khi sự cố xảy ra.

Ví dụ:

- RPO 24 giờ: chấp nhận mất dữ liệu tối đa một ngày;
- RPO 1 giờ: backup hoặc WAL phải cho phép phục hồi trong phạm vi một giờ;
- RPO gần 0: cần chiến lược WAL/replication và vận hành nghiêm ngặt hơn.

Một logical dump mỗi đêm không thể tự chứng minh RPO 1 giờ. Nếu database hỏng lúc 23:59 sau khi dump lúc 00:00, dữ liệu của gần một ngày có thể mất.

### RTO

Recovery Time Objective là thời gian tối đa để dịch vụ hoạt động lại:

~~~text
RTO = thời gian phát hiện
    + chuẩn bị máy/volume
    + restore base backup hoặc dump
    + replay WAL
    + kiểm tra dữ liệu
    + mở traffic
~~~

Dump nhỏ có thể restore nhanh. Cluster lớn với nhiều WAL có thể mất lâu hơn dù backup đã có sẵn. RPO và RTO phải được đo bằng restore drill, không chỉ ghi trong tài liệu.

---

## 2. Chọn loại backup

| Loại | Công cụ | Phục hồi | Điểm mạnh | Giới hạn |
|---|---|---|---|---|
| Logical database | pg_dump | database/object | chọn table, portable, đọc được | chậm với database lớn, không phải cluster snapshot |
| Logical cluster | pg_dumpall | role, tablespace, database definition | giữ global object dạng SQL | file lớn, restore tuần tự |
| Physical base backup | pg_basebackup | cả PostgreSQL cluster | phù hợp PITR, nhanh hơn logical ở cluster lớn | phụ thuộc version/layout, cần WAL |
| WAL archive | archive_command hoặc archive library | thay đổi sau base backup | phục hồi tới thời điểm | phải theo dõi missing/corrupt WAL |
| Replication | standby/streaming | chuyển sang replica | giảm downtime trong một số tình huống | không thay backup, lỗi logic có thể replicate |

Không có một backup duy nhất phù hợp mọi mục tiêu:

- pg_dump tốt cho migration, subset và restore object;
- physical backup + WAL cần cho PITR;
- pg_dumpall hữu ích cho role/tablespace/global object;
- replication hỗ trợ availability nhưng không thay backup.

---

## 3. Chuẩn bị database lab

Tạo database và dữ liệu mẫu:

~~~bash
createdb -h localhost -U postgres backup_lab
psql -h localhost -U postgres -d backup_lab
~~~

~~~sql
CREATE SCHEMA sales;

CREATE TABLE sales.customers (
    customer_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email text NOT NULL UNIQUE,
    full_name text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE sales.orders (
    order_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id bigint NOT NULL REFERENCES sales.customers(customer_id),
    status text NOT NULL CHECK (
        status IN ('pending', 'paid', 'cancelled')
    ),
    amount numeric(12, 2) NOT NULL CHECK (amount >= 0),
    created_at timestamptz NOT NULL DEFAULT now()
);

INSERT INTO sales.customers (email, full_name)
VALUES ('an@example.com', 'An Nguyen'),
       ('binh@example.com', 'Binh Tran');

INSERT INTO sales.orders (customer_id, status, amount)
VALUES (1, 'paid', 120000),
       (2, 'pending', 250000);
~~~

Kiểm tra:

~~~sql
SELECT * FROM sales.customers;
SELECT * FROM sales.orders;
SELECT pg_current_wal_lsn();
~~~

Trong lab, dùng user postgres hoặc role có quyền backup. Production nên dùng role phù hợp, secret manager và thư mục backup riêng không cho application ghi đè.

---

## 4. Logical backup với pg_dump

pg_dump tạo bản sao logic của một database. Nó không dump toàn bộ cluster:

- không tự chứa role và tablespace toàn cluster;
- không tự chứa các database khác;
- không phải physical snapshot;
- restore tạo lại object qua lệnh SQL hoặc cơ chế archive.

Dump dạng plain SQL:

~~~bash
pg_dump \
  -h localhost \
  -U postgres \
  -d backup_lab \
  -f backup_lab.sql
~~~

Xem file:

~~~bash
head -40 backup_lab.sql
tail -40 backup_lab.sql
~~~

Plain SQL dễ đọc, dễ chỉnh và restore bằng psql:

~~~bash
createdb -h localhost -U postgres backup_lab_plain_restore

psql \
  -h localhost \
  -U postgres \
  -d backup_lab_plain_restore \
  -f backup_lab.sql
~~~

Kiểm tra:

~~~bash
psql -h localhost -U postgres -d backup_lab_plain_restore \
  -c "SELECT count(*) FROM sales.orders;"
~~~

Không chỉnh sửa thủ công dump production rồi coi đó là bản backup nguyên gốc. Nếu cần transform dữ liệu, tạo artifact mới và ghi lại nguồn/phiên bản.

---

## 5. Custom format

Custom format phù hợp với pg_restore:

~~~bash
pg_dump \
  -h localhost \
  -U postgres \
  -d backup_lab \
  -Fc \
  -f backup_lab.dump
~~~

Xem nội dung archive:

~~~bash
pg_restore -l backup_lab.dump
~~~

Ưu điểm:

- nén tốt;
- restore chọn object;
- có thể chạy song song ở một số giai đoạn;
- không cần đọc toàn bộ file để biết danh sách object;
- phù hợp automation.

Restore vào database mới:

~~~bash
createdb -h localhost -U postgres backup_lab_custom_restore

pg_restore \
  -h localhost \
  -U postgres \
  -d backup_lab_custom_restore \
  --exit-on-error \
  backup_lab.dump
~~~

Nếu database đích đã có object:

~~~bash
pg_restore \
  -h localhost \
  -U postgres \
  -d backup_lab_custom_restore \
  --clean \
  --if-exists \
  --exit-on-error \
  backup_lab.dump
~~~

Không dùng --clean tùy tiện trên database đang có dữ liệu cần giữ. Restore vào database mới thường an toàn hơn.

---

## 6. Directory format và parallel restore

Directory format lưu archive thành nhiều file:

~~~bash
mkdir -p backup_lab.dir

pg_dump \
  -h localhost \
  -U postgres \
  -d backup_lab \
  -Fd \
  -j 4 \
  -f backup_lab.dir
~~~

Restore song song:

~~~bash
createdb -h localhost -U postgres backup_lab_dir_restore

pg_restore \
  -h localhost \
  -U postgres \
  -d backup_lab_dir_restore \
  -j 4 \
  --exit-on-error \
  backup_lab.dir
~~~

Trade-off:

- nhiều worker dùng CPU, disk và connection;
- không đặt số job cao hơn khả năng storage;
- directory format gồm nhiều file nên cần copy/retention cả thư mục;
- không xem một file riêng là backup hoàn chỉnh.

Custom format và directory format là archive logical, không phải physical base backup.

---

## 7. Các tùy chọn pg_dump cần biết

Dump một schema:

~~~bash
pg_dump \
  -h localhost \
  -U postgres \
  -d backup_lab \
  -n sales \
  -Fc \
  -f sales.dump
~~~

Dump một table:

~~~bash
pg_dump \
  -h localhost \
  -U postgres \
  -d backup_lab \
  -t sales.orders \
  -Fc \
  -f orders.dump
~~~

Chỉ schema:

~~~bash
pg_dump -h localhost -U postgres -d backup_lab \
  --schema-only -Fc -f backup_lab_schema.dump
~~~

Chỉ data:

~~~bash
pg_dump -h localhost -U postgres -d backup_lab \
  --data-only -Fc -f backup_lab_data.dump
~~~

Không dump owner/ACL để restore sang môi trường khác:

~~~bash
pg_dump \
  -h localhost \
  -U postgres \
  -d backup_lab \
  --no-owner \
  --no-acl \
  -Fc \
  -f backup_lab_portable.dump
~~~

Dùng --no-owner/--no-acl phải có chủ đích. Nếu cần giữ quyền production, bỏ qua chúng có thể làm restore mất security model.

Kiểm tra option theo phiên bản đang chạy:

~~~bash
pg_dump --version
pg_restore --version
pg_basebackup --version
~~~

Client nên tương thích server. Dùng client mới hơn thường hỗ trợ server cũ trong giới hạn, nhưng luôn kiểm tra release note và thử restore.

---

## 8. pg_dumpall

pg_dumpall dump toàn cluster dưới dạng SQL, bao gồm định nghĩa database và global object như role:

~~~bash
pg_dumpall \
  -h localhost \
  -U postgres \
  --globals-only \
  -f globals.sql

pg_dumpall \
  -h localhost \
  -U postgres \
  -f cluster.sql
~~~

Thứ tự thường dùng khi dựng cluster mới:

~~~bash
psql -h new-host -U postgres -f globals.sql
psql -h new-host -U postgres -f cluster.sql
~~~

Không restore cluster.sql vào nơi đã có database trùng tên mà chưa kiểm tra nội dung. pg_dumpall có thể chứa CREATE DATABASE và lệnh kết nối database.

So sánh:

| Lệnh | Phạm vi |
|---|---|
| pg_dump | một database, logic |
| pg_dumpall --globals-only | role, tablespace, global object |
| pg_dumpall | nhiều database và global object dạng SQL |

Một chiến lược thực tế thường lưu:

- pg_dump custom/directory cho từng database;
- pg_dumpall --globals-only cho role/tablespace;
- physical backup + WAL cho PITR.

---

## 9. Kiểm tra tính toàn vẹn logical backup

Không chỉ kiểm tra file có tồn tại:

~~~bash
test -s backup_lab.dump
pg_restore -l backup_lab.dump > backup_lab.contents.txt
sha256sum backup_lab.dump > backup_lab.dump.sha256
sha256sum -c backup_lab.dump.sha256
~~~

Sau restore, kiểm tra:

~~~sql
SELECT count(*) FROM sales.customers;
SELECT count(*) FROM sales.orders;
SELECT min(created_at), max(created_at) FROM sales.orders;

SELECT
    conname,
    convalidated
FROM pg_constraint
WHERE connamespace = 'sales'::regnamespace;
~~~

Kiểm tra nghiệp vụ quan trọng:

~~~sql
SELECT
    o.order_id,
    c.email,
    o.status,
    o.amount
FROM sales.orders AS o
JOIN sales.customers AS c
  ON c.customer_id = o.customer_id
ORDER BY o.order_id;
~~~

Checksum xác nhận file không đổi từ lúc checksum, không chứng minh dữ liệu nghiệp vụ đúng. Restore validation phải có cả kiểm tra cấu trúc, row count và invariant.

---

## 10. Physical base backup với pg_basebackup

Physical backup là bản sao data directory/cluster, thường dùng làm điểm bắt đầu cho PITR. PostgreSQL cần WAL đủ để đưa base backup về trạng thái nhất quán.

Điều kiện thường gặp:

- wal_level phù hợp;
- quyền replication hoặc quyền backup cần thiết;
- pg_hba.conf cho phép connection replication;
- thư mục đích đủ dung lượng;
- backup user có password/credential an toàn;
- WAL được lưu hoặc stream trong quá trình backup.

Ví dụ dạng plain:

~~~bash
pg_basebackup \
  -h localhost \
  -U backup_user \
  -D /backup/base/2026-09-27 \
  -Fp \
  -Xs \
  -P
~~~

Ý nghĩa:

- -D: thư mục đích;
- -Fp: plain format;
- -Xs: stream WAL trong lúc base backup;
- -P: hiển thị progress.

Dạng tar:

~~~bash
pg_basebackup \
  -h localhost \
  -U backup_user \
  -D /backup/base/2026-09-27 \
  -Ft \
  -z \
  -Xs \
  -P
~~~

- -Ft: tar format;
- -z: nén tar bằng gzip ở các phiên bản hỗ trợ.

Kiểm tra kết quả:

~~~bash
find /backup/base/2026-09-27 -maxdepth 2 -type f -ls
cat /backup/base/2026-09-27/backup_manifest
~~~

backup_manifest hữu ích cho kiểm tra file và checksum theo manifest. Nó không thay thế restore test.

---

## 11. Backup user và pg_hba.conf

Tạo role có quyền replication/backup theo yêu cầu phiên bản:

~~~sql
CREATE ROLE backup_user
    WITH LOGIN REPLICATION PASSWORD 'secret-tu-secret-manager';
~~~

Trong pg_hba.conf, cấu hình rule hẹp theo network và database replication. Ví dụ minh họa:

~~~text
host    replication    backup_user    10.0.0.0/24    scram-sha-256
~~~

Sau thay đổi, reload:

~~~sql
SELECT pg_reload_conf();
~~~

Không mở 0.0.0.0/0 cho replication user. Không dùng password inline trong shell history nếu môi trường nhạy cảm; dùng .pgpass với permission phù hợp hoặc secret manager.

Kiểm tra connection replication:

~~~sql
SELECT
    usename,
    application_name,
    client_addr,
    state
FROM pg_stat_replication;
~~~

Một số môi trường dùng quyền backup riêng thay vì REPLICATION role. Kiểm tra tài liệu phiên bản triển khai và test bằng user thực tế.

---

## 12. WAL và archive

WAL ghi lại thay đổi để crash recovery, replication và PITR. Base backup không đủ để phục hồi đến thời điểm sau lúc base backup nếu không có WAL liên tục.

Kiểm tra setting:

~~~sql
SHOW wal_level;
SHOW archive_mode;
SHOW archive_command;
SHOW archive_timeout;

SELECT
    pg_current_wal_lsn(),
    pg_walfile_name(pg_current_wal_lsn());
~~~

Archive command mẫu trong lab:

~~~text
archive_command = 'test ! -f /var/lib/postgresql/wal_archive/%f && cp %p /var/lib/postgresql/wal_archive/%f'
~~~

Placeholder:

- %p: path WAL source;
- %f: tên file WAL.

Điều kiện quan trọng:

1. archive command trả exit code 0 chỉ khi copy thành công;
2. không overwrite WAL archive đã tồn tại;
3. thư mục đích có disk và permission;
4. archive storage không nằm cùng failure domain nếu muốn chống hỏng disk;
5. theo dõi archive failure và lag;
6. có retention phù hợp với base backup.

Nếu archive_command thất bại, PostgreSQL giữ WAL trong pg_wal và có thể làm disk phình. Đây là failure cần alert, không phải lỗi được bỏ qua.

---

## 13. Bật WAL archive trong lab

archive_mode là cấu hình cần restart. Cách triển khai tùy Docker/image. Với container lab, có thể tạo file archive và dùng ALTER SYSTEM cho các setting cần reload:

~~~bash
docker exec -u postgres pg-advanced \
  mkdir -p /var/lib/postgresql/wal_archive
~~~

Trong psql:

~~~sql
ALTER SYSTEM SET wal_level = 'replica';
ALTER SYSTEM SET archive_mode = 'on';
ALTER SYSTEM SET archive_command =
    'test ! -f /var/lib/postgresql/wal_archive/%f && cp %p /var/lib/postgresql/wal_archive/%f';
~~~

Kiểm tra:

~~~sql
SELECT name, setting, context, pending_restart
FROM pg_settings
WHERE name IN ('wal_level', 'archive_mode', 'archive_command');
~~~

Restart container theo runbook lab:

~~~bash
docker restart pg-advanced
~~~

Sau restart:

~~~sql
SHOW wal_level;
SHOW archive_mode;
SHOW archive_command;
~~~

Không áp dụng ALTER SYSTEM hoặc restart production chỉ để thử bài nếu chưa có change window. Với production, dùng configuration management và quy trình rollback.

Ép tạo WAL segment để test archive:

~~~sql
SELECT pg_switch_wal();
~~~

Kiểm tra file:

~~~bash
docker exec pg-advanced \
  ls -lh /var/lib/postgresql/wal_archive
~~~

Nếu file không xuất hiện, kiểm tra archive_command, permission, disk và log PostgreSQL:

~~~sql
SELECT archived_count, failed_count, last_archived_wal,
       last_archived_time, last_failed_wal, last_failed_time
FROM pg_stat_archiver;
~~~

---

## 14. Archive retention và WAL continuity

PITR cần:

~~~text
base backup gần nhất
  + WAL liên tục từ thời điểm base backup
  + WAL chứa target time
~~~

Nếu thiếu một WAL segment ở giữa, replay không thể tiếp tục qua khoảng đó. Có nhiều file WAL không đồng nghĩa có một chuỗi WAL hoàn chỉnh.

Theo dõi archiver:

~~~sql
SELECT
    archived_count,
    failed_count,
    last_archived_wal,
    last_archived_time,
    last_failed_wal,
    last_failed_time,
    stats_reset
FROM pg_stat_archiver;
~~~

Đặt retention theo:

- RPO;
- thời gian muốn PITR quay lại;
- tần suất base backup;
- tốc độ sinh WAL;
- dung lượng object storage;
- legal/compliance retention;
- thời gian phát hiện lỗi dữ liệu.

Không xóa WAL đang cần cho base backup mới nhất. Quản lý retention theo metadata của backup tool hoặc catalog, không xóa theo cảm tính.

---

## 15. Restore logical backup vào database mới

Một nguyên tắc an toàn: restore vào database mới trước, rồi mới quyết định cutover.

~~~bash
createdb -h localhost -U postgres backup_lab_restore

pg_restore \
  -h localhost \
  -U postgres \
  -d backup_lab_restore \
  --exit-on-error \
  backup_lab.dump
~~~

Nếu restore dump plain:

~~~bash
createdb -h localhost -U postgres backup_lab_plain_restore

psql \
  -h localhost \
  -U postgres \
  -d backup_lab_plain_restore \
  -v ON_ERROR_STOP=1 \
  -f backup_lab.sql
~~~

ON_ERROR_STOP giúp psql dừng khi gặp lỗi thay vì tiếp tục tạo restore không đầy đủ.

Restore một nhóm object từ custom archive:

~~~bash
pg_restore -l backup_lab.dump > contents.list
pg_restore \
  -h localhost \
  -U postgres \
  -d backup_lab_restore \
  -L contents.list \
  backup_lab.dump
~~~

Chỉnh contents.list chỉ sau khi hiểu dependency giữa schema, table, sequence, data, index, constraint và ACL.

---

## 16. Physical restore và dựng cluster mới

Physical restore không restore vào một database đang chạy. Quy trình khái quát:

1. dừng PostgreSQL đích;
2. di chuyển hoặc xóa data directory cũ theo quy trình an toàn;
3. giải nén/copy base backup vào data directory;
4. đặt owner và permission đúng;
5. cấu hình restore_command;
6. chỉ định recovery target nếu cần PITR;
7. start PostgreSQL;
8. theo dõi log replay;
9. xác minh dữ liệu;
10. quyết định promote/cutover.

Ví dụ tạo cluster đích bằng initdb chỉ dành cho trường hợp không dùng base backup:

~~~bash
initdb -D /restore/cluster
~~~

Khi dùng base backup, không chạy initdb rồi chép lẫn file vào data directory. Data directory phải là một cluster nhất quán từ base backup.

Kiểm tra version:

~~~bash
pg_controldata /restore/cluster | head -40
~~~

Major version của physical backup phải phù hợp với server restore. Physical files không portable như logical dump.

---

## 17. PITR là gì?

Point-in-Time Recovery phục hồi một physical base backup rồi replay WAL đến một điểm mục tiêu:

~~~text
base backup
  → restore file
  → đọc WAL archive
  → replay thay đổi
  → dừng tại target time/LSN/name
  → database mở ở một timeline mới
~~~

PITR phù hợp với:

- xóa nhầm table;
- UPDATE nhầm hàng loạt;
- dữ liệu hỏng logic ở một thời điểm;
- mất server nhưng còn backup/WAL;
- cần dựng môi trường tại một mốc thời gian.

PITR không phục hồi được nếu:

- không có base backup;
- WAL archive thiếu segment;
- archive bị corrupt;
- backup và WAL không thuộc cùng cluster;
- target nằm trước base backup;
- sự cố đã được replicate sang standby và không có WAL/backup phù hợp.

---

## 18. Target của PITR

Các dạng target thường gặp:

### Target time

~~~text
recovery_target_time = '2026-09-27 10:15:00+07'
~~~

Chọn mốc ngay trước thao tác nhầm. Cẩn thận timezone và clock drift.

### Target LSN

~~~sql
SELECT pg_current_wal_lsn();
~~~

LSN hữu ích khi có marker kỹ thuật chính xác. LSN phải thuộc đúng cluster/timeline.

### Target name

Có thể tạo restore point trước migration nguy hiểm:

~~~sql
SELECT pg_create_restore_point('before_dangerous_migration');
~~~

Sau đó cấu hình:

~~~text
recovery_target_name = 'before_dangerous_migration'
~~~

Restore point chỉ hữu ích nếu WAL chứa marker được archive đầy đủ.

### Target XID

Có thể dùng transaction ID trong các tình huống đặc biệt, nhưng target time/name thường dễ vận hành hơn. Chọn target có bằng chứng từ audit/log/incident timeline.

---

## 19. Cấu hình restore_command

Trên cluster restore, PostgreSQL cần biết cách lấy WAL archive:

~~~text
restore_command = 'cp /var/lib/postgresql/wal_archive/%f %p'
~~~

Production thường lấy từ object storage hoặc backup tool:

~~~text
restore_command = 'wal-g wal-fetch %f %p'
~~~

Không để restore_command luôn trả exit code 0 khi không tìm thấy file. Nếu command giả vờ thành công, recovery có thể hành xử sai hoặc chờ lâu.

Trong PostgreSQL hiện đại, recovery mode được kích hoạt bằng file standby.signal hoặc cơ chế tương ứng của tool. Ví dụ:

~~~bash
touch /restore/cluster/recovery.signal
~~~

Trong postgresql.conf của cluster restore:

~~~text
restore_command = 'cp /var/lib/postgresql/wal_archive/%f %p'
recovery_target_time = '2026-09-27 10:15:00+07'
recovery_target_action = 'pause'
~~~

Các target action thường gặp:

- pause: dừng để kiểm tra trước;
- promote: tự kết thúc recovery và mở writable;
- shutdown: dừng server sau khi đạt target.

Dùng pause trong drill giúp xác minh dữ liệu trước khi promote.

---

## 20. Quy trình PITR xóa nhầm table

Giả sử:

1. base backup được tạo lúc 09:00;
2. WAL archive hoạt động;
3. người vận hành xóa bảng lúc 10:15;
4. phát hiện lúc 10:20;
5. cần phục hồi dữ liệu ngay trước lệnh xóa.

### Bước 1: Khoanh incident

- dừng job ghi dữ liệu nếu tiếp tục làm khó điều tra;
- ghi lại thời điểm phát hiện và thời điểm thao tác nhầm;
- giữ log/audit;
- không overwrite cluster nguồn;
- quyết định target time có timezone rõ.

### Bước 2: Chọn base backup

Chọn base backup gần nhất trước 10:15 có manifest hợp lệ. Ghi backup ID, thời gian, checksum và vị trí WAL.

### Bước 3: Dựng cluster restore riêng

Copy base backup vào data directory mới, giữ owner/permission đúng. Không cho application kết nối nhầm cluster restore.

### Bước 4: Cấu hình archive và target

Đặt restore_command và recovery_target_time ngay trước thao tác DELETE/DROP. Dùng pause nếu cần kiểm tra.

### Bước 5: Start và theo dõi

~~~bash
pg_ctl -D /restore/cluster start
tail -f /restore/cluster/log/postgresql*.log
~~~

Kiểm tra recovery:

~~~sql
SELECT pg_is_in_recovery();
SELECT pg_last_wal_replay_lsn();
SELECT pg_last_xact_replay_timestamp();
~~~

### Bước 6: Kiểm tra dữ liệu

~~~sql
\dt sales.*
SELECT count(*) FROM sales.orders;
SELECT max(created_at) FROM sales.orders;
~~~

Nếu target action pause, xác minh dữ liệu trước khi promote:

~~~sql
SELECT pg_wal_replay_resume();
~~~

### Bước 7: Trích xuất hoặc cutover

Thường an toàn hơn là dump table cần cứu từ cluster restore rồi import vào cluster production đã được đánh giá:

~~~bash
pg_dump \
  -h restore-host \
  -U postgres \
  -d backup_lab \
  -t sales.orders \
  -Fc \
  -f recovered_orders.dump
~~~

Không promote cluster restore vào production chỉ vì query kiểm tra có vẻ đúng. Cutover cần runbook, downtime/traffic plan, kiểm tra sequence, role, extension, application compatibility và dữ liệu phát sinh sau target.

---

## 21. Timeline sau PITR

Khi phục hồi tới thời điểm trong quá khứ rồi tiếp tục ghi dữ liệu, PostgreSQL tạo timeline mới. Timeline giúp phân biệt các nhánh lịch sử WAL.

Kiểm tra:

~~~bash
pg_controldata /restore/cluster | rg "Latest checkpoint|timeline"
~~~

Một timeline mới không nên bị coi là lỗi. Nó là dấu hiệu cluster đã rẽ nhánh sau recovery. Quản lý backup catalog và WAL archive phải nhận biết timeline đúng.

Không cho primary cũ và cluster PITR mới cùng ghi vào cùng một storage hoặc cùng endpoint. Điều đó có thể gây split brain và mất dữ liệu.

---

## 22. Backup encryption và bảo vệ dữ liệu

Backup thường chứa toàn bộ dữ liệu, có thể nhạy cảm hơn database đang chạy vì dễ bị copy. Cần bảo vệ:

- encryption in transit khi upload;
- encryption at rest trên disk/object storage;
- key management và rotation;
- access policy tách khỏi database runtime;
- audit download/restore;
- không để key trong cùng thư mục backup;
- không ghi password vào command history;
- xóa bản tạm sau restore.

Có thể mã hóa file logical bằng công cụ hệ điều hành hoặc backup platform:

~~~bash
pg_dump -h localhost -U postgres -d backup_lab -Fc \
  | openssl enc -aes-256-cbc -salt -pbkdf2 \
    -out backup_lab.dump.enc
~~~

Giải mã:

~~~bash
openssl enc -d -aes-256-cbc -pbkdf2 \
  -in backup_lab.dump.enc \
  -out backup_lab.dump
~~~

Đây là ví dụ lab. Production nên dùng KMS/backup service đã quản lý key, tránh passphrase tương tác không audit được. Nếu key mất, backup mã hóa trở thành dữ liệu không thể phục hồi.

Không coi mã hóa là substitute cho authorization. Người có key vẫn cần được kiểm soát quyền restore và truy cập.

---

## 23. Retention policy

Retention phải trả lời:

- giữ bao nhiêu bản daily/weekly/monthly;
- giữ WAL bao lâu;
- có bản copy ngoài site/failure domain không;
- legal hold xử lý thế nào;
- backup nào được phép xóa;
- bản backup cũ có được restore thử không.

Mẫu policy:

~~~text
hourly  : 48 bản
daily   : 30 bản
weekly  : 12 bản
monthly : 12 bản
WAL     : ít nhất đủ cho PITR theo RPO/incident window
~~~

Đây chỉ là mẫu cần điều chỉnh theo dữ liệu và yêu cầu. Đừng xóa theo thời gian tạo file mà không biết base backup nào còn phụ thuộc WAL nào.

Backup catalog nên lưu:

- backup ID;
- loại backup;
- start/end time;
- server/database;
- PostgreSQL major version;
- size;
- checksum/manifest;
- WAL start/end;
- location;
- encryption key reference;
- last restore test;
- expiration;
- status.

---

## 24. Restore drill

Restore drill là bài kiểm tra bắt buộc:

1. chọn một backup theo catalog;
2. verify checksum/manifest;
3. restore vào môi trường cô lập;
4. replay WAL nếu là physical/PITR;
5. chạy smoke test và invariant;
6. đo thời gian từng bước;
7. ghi dữ liệu mất tối đa;
8. ghi lỗi và sửa runbook;
9. cleanup môi trường đúng cách;
10. lưu bằng chứng test.

Đo:

~~~text
backup age
restore start/end
base restore duration
WAL replay duration
validation duration
total RTO
target RPO
actual data loss
~~~

Một restore drill không cần chờ production hỏng. Chạy theo lịch, thay đổi tần suất khi schema, volume, version hoặc backup tool thay đổi.

---

## 25. Validation sau restore

### Cluster

~~~sql
SELECT version();
SELECT current_database();
SELECT pg_is_in_recovery();
SELECT pg_postmaster_start_time();
~~~

### Schema và object

~~~sql
\dn
\dt sales.*
\dx

SELECT extname, extversion
FROM pg_extension
ORDER BY extname;
~~~

### Dữ liệu

~~~sql
SELECT count(*) FROM sales.customers;
SELECT count(*) FROM sales.orders;

SELECT
    min(created_at),
    max(created_at),
    sum(amount)
FROM sales.orders;
~~~

### Constraint và sequence

~~~sql
SELECT conname, contype, convalidated
FROM pg_constraint
WHERE connamespace = 'sales'::regnamespace;

SELECT last_value, is_called
FROM sales.orders_order_id_seq;
~~~

### Nghiệp vụ

Kiểm tra các invariant quan trọng:

- order.customer_id luôn tồn tại;
- amount không âm;
- tổng theo ngày khớp báo cáo;
- role/ACL vẫn đúng;
- function, trigger, extension có mặt;
- ứng dụng đọc/ghi được theo expected permission.

Row count giống nhau chưa đủ; dữ liệu có thể đúng số lượng nhưng sai foreign key, sequence hoặc nghiệp vụ.

---

## 26. Backup consistency và concurrent workload

pg_dump tạo logical snapshot nhất quán theo transaction semantics, nhưng các option và object đặc biệt vẫn cần kiểm tra. Physical base backup có thể chạy khi server đang hoạt động; PostgreSQL đảm bảo cơ chế nhất quán bằng WAL, nhưng workload tạo nhiều WAL sẽ ảnh hưởng storage và thời gian.

Theo dõi trong lúc backup:

~~~sql
SELECT
    pid,
    usename,
    application_name,
    state,
    query_start,
    wait_event_type,
    wait_event,
    query
FROM pg_stat_activity
WHERE query ILIKE '%backup%'
   OR application_name ILIKE '%basebackup%';
~~~

Đừng chạy nhiều backup lớn đồng thời nếu storage không chịu được. Backup có thể tranh chấp I/O với workload production.

---

## 27. Logical backup không thay PITR

Một dump có thể phục hồi database tại thời điểm dump. Nó không tự phục hồi các thay đổi từng phút sau đó.

Ví dụ:

~~~text
00:00  pg_dump hoàn tất
08:00  dữ liệu bị xóa nhầm
09:00  phát hiện sự cố
~~~

pg_restore chỉ đưa database về khoảng 00:00. Muốn đến 07:59 cần base backup/WAL hoặc một dump gần mốc đó. Vì vậy kế hoạch backup cần khớp RPO, không chỉ khớp dung lượng.

---

## 28. Những lỗi thường gặp

### Chỉ backup database chính, quên globals

Role, tablespace và global object không tự nằm trong pg_dump một database. Lưu pg_dumpall --globals-only hoặc quy trình tạo lại tương đương.

### Có file dump nhưng chưa restore thử

File có thể corrupt, thiếu permission, thiếu extension hoặc restore fail do version/owner. Restore drill là bằng chứng.

### Archive command luôn trả thành công

WAL có thể bị mất âm thầm. Command phải fail khi copy không thành công và phải có metric failed_count.

### Xóa WAL quá sớm

Base backup cũ có thể không còn phục hồi được. Retention phải dựa trên dependency, RPO và catalog.

### Đặt archive cùng disk với data

Mất disk có thể mất cả database và WAL archive. Dùng storage/failure domain khác khi yêu cầu phục hồi cao.

### Dùng pg_dump cho PITR

Logical dump không replay WAL. PITR cần physical base backup và WAL liên tục.

### Restore đè production ngay lập tức

Dễ mất dữ liệu mới hơn và khó rollback. Restore riêng, validate, rồi mới quyết định merge/cutover.

### Không ghi timezone target

Target time không rõ timezone dẫn đến phục hồi sai mốc. Dùng timestamp có offset và đồng bộ clock.

### Không kiểm tra sequence/ACL/extension

Dữ liệu có thể restore nhưng INSERT mới, application hoặc job bị lỗi.

### Xem replication là backup

Xóa nhầm hoặc dữ liệu hỏng logic có thể replicate sang standby. Replication hỗ trợ availability, không thay backup độc lập.

---

## 29. Runbook logical restore

### Trước khi bắt đầu

- xác định database và backup ID;
- kiểm tra checksum/manifest;
- kiểm tra dung lượng đích;
- xác nhận PostgreSQL/client version;
- xác định owner/role/extension;
- ghi incident ID và người phê duyệt;
- không overwrite source.

### Thực hiện

~~~bash
createdb -h restore-host -U postgres restored_db

pg_restore \
  -h restore-host \
  -U postgres \
  -d restored_db \
  --exit-on-error \
  /backup/catalog/backup_lab.dump
~~~

### Xác minh

~~~sql
SELECT count(*) FROM sales.customers;
SELECT count(*) FROM sales.orders;
SELECT version();
SELECT extname FROM pg_extension;
~~~

Chạy query nghiệp vụ, kiểm tra ACL/role và ghi thời gian hoàn tất. Chỉ mở traffic sau khi owner dữ liệu xác nhận.

---

## 30. Runbook PITR

### Trước khi bắt đầu

- xác định thời điểm lỗi và target time;
- chọn base backup trước target;
- kiểm tra WAL archive liên tục;
- xác định timeline;
- chuẩn bị host/volume cô lập;
- tính dung lượng replay;
- khóa access của application vào cluster restore.

### Khôi phục

1. dừng cluster đích;
2. restore base backup;
3. đặt permission đúng;
4. cấu hình restore_command;
5. tạo recovery.signal;
6. đặt recovery_target_time/name/LSN;
7. chọn recovery_target_action;
8. start PostgreSQL;
9. theo dõi log replay;
10. kiểm tra pg_is_in_recovery và replay timestamp;
11. validate schema, row count, invariant;
12. dump dữ liệu cần cứu hoặc promote theo phê duyệt;
13. ghi timeline mới và metadata.

### Sau khi phục hồi

- không để primary cũ và PITR cluster cùng nhận write;
- rotate credential nếu có khả năng lộ;
- cập nhật endpoint/DNS chỉ sau cutover plan;
- kiểm tra job/cron không chạy nhầm hai cluster;
- chụp backup mới của cluster đã promote;
- cập nhật runbook bằng lỗi thực tế.

---

## 31. Bài tập thực hành

### Bài 1 — Custom dump

Dump database bằng custom format, xem danh sách object, restore vào database mới và ghi thời gian restore.

### Bài 2 — Directory format

Dump bằng directory format với hai hoặc bốn worker. So sánh kích thước, thời gian dump và restore với custom format.

### Bài 3 — Globals

Tạo role/group role, dump globals-only và restore vào cluster lab mới. Kiểm tra membership sau restore.

### Bài 4 — Base backup

Tạo physical base backup bằng pg_basebackup. Kiểm tra backup_manifest, checksum, kích thước và WAL range.

### Bài 5 — WAL archive

Bật archive trong lab, tạo dữ liệu, gọi pg_switch_wal, kiểm tra pg_stat_archiver và file archive.

### Bài 6 — PITR xóa nhầm table

Tạo base backup, insert dữ liệu marker, ghi restore point, xóa table, tạo thêm WAL, rồi restore cluster tới restore point trong môi trường riêng.

### Bài 7 — RPO/RTO report

Viết báo cáo:

- base backup bắt đầu/kết thúc;
- WAL archive lag;
- target RPO;
- actual RPO;
- từng bước và duration;
- lỗi gặp phải;
- thay đổi runbook.

### Bài 8 — Encryption và retention

Mã hóa một logical dump trong lab, giải mã, restore, tạo checksum và thiết kế retention 7/30/90 ngày. Ghi rõ nơi lưu key.

---

## 32. Tiêu chí hoàn thành Tuần 13

Bạn hoàn thành bài khi có thể:

- giải thích RPO và RTO bằng số đo thực tế;
- chọn logical, physical hoặc WAL archive theo mục tiêu;
- dùng pg_dump plain, custom và directory format;
- restore bằng psql và pg_restore;
- dùng pg_dumpall cho global object;
- tạo base backup bằng pg_basebackup;
- giải thích WAL continuity;
- bật và theo dõi archive_command trong lab;
- kiểm tra pg_stat_archiver và archive failure;
- dựng cluster restore riêng;
- chọn recovery target time, LSN hoặc restore point;
- cấu hình restore_command và recovery signal;
- phục hồi tới thời điểm trước thao tác phá hoại;
- hiểu timeline sau PITR;
- mã hóa backup và thiết kế retention;
- chạy restore drill và đo RTO/RPO;
- viết runbook restore có validation và rollback/cutover plan.

---

## Tóm tắt cần nhớ

1. Backup chỉ có giá trị sau khi restore thử thành công.
2. pg_dump là logical backup; pg_basebackup là physical base backup.
3. pg_dumpall hữu ích cho role, tablespace và global object.
4. Custom/directory format phù hợp với pg_restore và restore chọn lọc.
5. PITR cần base backup và WAL archive liên tục.
6. archive failure phải được alert; WAL không được xóa theo cảm tính.
7. RPO là dữ liệu có thể mất; RTO là thời gian phục hồi dịch vụ.
8. Restore vào môi trường riêng trước khi cutover.
9. Target time phải có timezone và bằng chứng từ incident timeline.
10. Sau PITR, cluster có thể tạo timeline mới.
11. Encryption, key management và retention đều là một phần của backup design.
12. Runbook phải có validation, người phê duyệt, duration và bằng chứng restore.

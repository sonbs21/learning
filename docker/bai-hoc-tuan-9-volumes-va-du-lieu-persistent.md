# Tuần 9: Volumes và dữ liệu persistent

## 1. Mục tiêu

Sau bài này, bạn có thể:

- Giải thích vì sao container writable layer không phải nơi lưu dữ liệu bền vững.
- Phân biệt named volume, bind mount, anonymous volume và tmpfs.
- Chọn loại storage phù hợp cho database, source code, config và dữ liệu tạm.
- Tạo, inspect, mount, backup, restore và quản lý named volume.
- Chạy PostgreSQL với data directory được persist qua vòng đời container.
- Hiểu permission, UID/GID và lỗi ownership khi dùng volume/bind mount.
- Hiểu storage driver ở mức khái niệm.
- Nhận biết backup volume không đồng nghĩa backup nhất quán database.
- Tránh xóa nhầm volume bằng `docker compose down -v` hoặc prune.

Thời lượng đề xuất: **8–12 giờ** gồm lý thuyết, lab và bài tập.

---

## 2. Vì sao cần persistent storage?

Container thường được tạo để có thể thay thế. Nếu dữ liệu chỉ nằm trong writable layer:

~~~text
Container A
└── Writable layer
    └── database files

rm container A
→ writable layer bị xóa
→ database files mất
~~~

Ứng dụng stateless có thể tạo container mới từ image. Database, upload, file cần giữ lại và dữ liệu người dùng thì không thể chỉ dựa vào container instance.

Storage tách dữ liệu khỏi vòng đời container:

~~~text
Container instance ─────┐
                         ├── Persistent storage
Container mới ──────────┘
~~~

Nhưng persistent storage không tự giải quyết mọi vấn đề. Vẫn cần:

- Backup và restore đã kiểm thử.
- Quyền truy cập/ownership.
- Capacity planning.
- Encryption và access control.
- Consistency khi backup.
- Retention và cleanup.
- Monitoring disk usage.

---

## 3. Container writable layer

Nếu không mount volume/bind mount, file ghi vào path trong container đi vào writable layer.

~~~bash
docker run --name scratch alpine:3.20 sh -c 'echo data > /data.txt'
docker start scratch
docker exec scratch cat /data.txt
~~~

File còn khi container chỉ stop/start. Nhưng khi remove:

~~~bash
docker rm -f scratch
~~~

file mất vì thuộc instance cũ.

### 3.1. Khi nào writable layer phù hợp?

Phù hợp cho:

- File tạm.
- Cache không quan trọng.
- Artifact ngắn hạn trong một job.
- Container test có lifecycle ngắn.

Không phù hợp cho:

- Database data directory.
- User uploads.
- File cần giữ sau recreate.
- Backup duy nhất.
- Dữ liệu cần chia sẻ giữa nhiều container lâu dài.

### 3.2. Container image không phải data backup

Image chứa application/runtime, không nên chứa database runtime data. Build image mới không phải cách backup database; copy writable layer cũng không bảo đảm database nhất quán.

---

## 4. Các loại storage

| Loại | Dùng cho | Ownership | Vòng đời |
|---|---|---|---|
| Writable layer | Tạm, cache không quan trọng | Container process | Theo container |
| Named volume | Database, dữ liệu cần persist | Docker quản lý volume | Độc lập container |
| Bind mount | Source/config dev, file host cần truy cập | Host filesystem | Theo path host |
| Anonymous volume | Persist path nhưng không đặt tên rõ | Docker quản lý ID | Dễ bị bỏ quên |
| tmpfs | Dữ liệu tạm trong memory | Runtime/kernel | Theo container/host memory |

Lựa chọn mặc định cho database trong Docker local thường là **named volume**. Bind mount phù hợp development khi cần xem/sửa file trên host. tmpfs phù hợp dữ liệu ngắn hạn không cần persist xuống disk.

---

## 5. Named volume

Named volume có tên do người dùng đặt và được Docker quản lý:

~~~bash
docker volume create pgdata
docker volume ls
docker volume inspect pgdata
~~~

Output thường có:

- Volume name.
- Driver.
- Mountpoint trên host/VM.
- Labels/options nếu có.

Mount vào container:

~~~bash
docker run -d --name volume-demo \
  -v pgdata:/var/lib/example \
  alpine:3.20 sh -c 'echo persistent > /var/lib/example/message.txt; sleep 3600'
~~~

Kiểm tra:

~~~bash
docker exec volume-demo cat /var/lib/example/message.txt
docker rm -f volume-demo
~~~

Tạo container mới dùng cùng volume:

~~~bash
docker run --rm \
  -v pgdata:/var/lib/example \
  alpine:3.20 cat /var/lib/example/message.txt
~~~

Dữ liệu còn vì volume tồn tại độc lập với container.

### 5.1. Cú pháp `-v` và `--mount`

Cú pháp ngắn:

~~~bash
docker run -v pgdata:/var/lib/example image
~~~

Cú pháp rõ hơn:

~~~bash
docker run \
  --mount type=volume,src=pgdata,dst=/var/lib/example \
  image
~~~

`--mount` dài hơn nhưng dễ đọc và ít nhầm thứ tự. Với tài liệu/production script, nên cân nhắc `--mount` khi có nhiều option.

### 5.2. Read-only volume mount

Nếu container chỉ cần đọc dữ liệu:

~~~bash
docker run --rm \
  --mount type=volume,src=pgdata,dst=/data,readonly \
  alpine:3.20 ls -la /data
~~~

Read-only mount là một lớp giảm rủi ro, nhưng process vẫn có thể ghi vào path khác nếu container còn quyền.

---

## 6. Bind mount

Bind mount map path cụ thể trên host vào container:

~~~bash
mkdir -p ./config
echo 'dev=true' > ./config/application.properties

docker run --rm \
  --mount type=bind,src="$(pwd)/config",dst=/app/config,readonly \
  alpine:3.20 cat /app/config/application.properties
~~~

Dùng bind mount cho:

- Source code trong development.
- Config local.
- File input/output cần nhìn trực tiếp trên host.
- Tooling yêu cầu làm việc trên thư mục host.

### 6.1. Rủi ro bind mount

Bind mount phụ thuộc filesystem và permission của host:

- Đường dẫn khác nhau giữa Linux/macOS/Windows.
- Container có thể đọc/sửa file host nếu mount writable.
- UID/GID trong container có thể khác user host.
- Dễ mount nhầm thư mục rộng.
- Có thể expose source hoặc secret không chủ ý.

Không bind mount cả project/host root vào production container chỉ để “cho chạy được”. Mount tối thiểu, read-only nếu có thể.

### 6.2. Bind mount source trong development

Ví dụ concept:

~~~bash
docker run --rm -it \
  --mount type=bind,src="$(pwd)/src",dst=/workspace/src \
  dev-image:latest sh
~~~

Đây là workflow development, không phải production image immutable. File thay đổi trên host có thể lập tức xuất hiện trong container tùy filesystem/sync behavior.

---

## 7. Anonymous volume

Anonymous volume không đặt tên rõ:

~~~bash
docker run -d --name anonymous-demo \
  -v /var/lib/example \
  alpine:3.20 sh -c 'echo data > /var/lib/example/file; sleep 3600'
~~~

Docker tạo volume ID ngẫu nhiên. Inspect:

~~~bash
docker inspect anonymous-demo --format '{{json .Mounts}}'
docker volume ls
~~~

Anonymous volume có thể hữu ích khi image khai báo `VOLUME` để bảo vệ một thư mục, nhưng dễ tạo volume mồ côi và khó quản lý vòng đời.

Nếu dữ liệu cần backup/retention/owner rõ ràng, ưu tiên named volume.

---

## 8. tmpfs

tmpfs mount lưu dữ liệu trong memory của host/runtime, không persist xuống storage thông thường:

~~~bash
docker run --rm -it \
  --tmpfs /run:rw,noexec,nosuid,size=64m \
  alpine:3.20 sh
~~~

Phù hợp cho:

- Temporary files.
- Cache ngắn hạn.
- Một số secret tạm thời cần tránh ghi disk.
- Runtime state không cần giữ sau container.

Trade-off:

- Dùng memory host/container.
- Mất khi container stop/remove tùy lifecycle.
- Không phù hợp database data.
- Không tự động giải quyết secret exposure trong process/log.

`tmpfs` không phải cách duy nhất hoặc mặc định để xử lý secret. Production nên dùng secret mechanism của platform.

---

## 9. PostgreSQL với named volume

Đây là lab theo roadmap:

~~~bash
docker volume create pgdata

docker run -d --name postgres \
  -e POSTGRES_PASSWORD=postgres \
  -v pgdata:/var/lib/postgresql/data \
  -p 5432:5432 \
  postgres:16
~~~

Kiểm tra:

~~~bash
docker ps
docker logs --tail 50 postgres
docker inspect --format '{{json .Mounts}}' postgres
docker volume inspect pgdata
~~~

Kết nối và tạo dữ liệu:

~~~bash
docker exec -it postgres psql -U postgres
~~~

Trong `psql`:

~~~sql
CREATE DATABASE learning;
\\c learning
CREATE TABLE notes (id SERIAL PRIMARY KEY, body TEXT NOT NULL);
INSERT INTO notes(body) VALUES ('persistent data');
SELECT * FROM notes;
\\q
~~~

Stop/remove container nhưng giữ volume:

~~~bash
docker stop postgres
docker rm postgres

docker run -d --name postgres-new \
  -e POSTGRES_PASSWORD=postgres \
  -v pgdata:/var/lib/postgresql/data \
  -p 5432:5432 \
  postgres:16
~~~

Kiểm tra dữ liệu:

~~~bash
docker exec -it postgres-new psql -U postgres -d learning -c 'SELECT * FROM notes;'
~~~

Dữ liệu còn vì volume `pgdata` được mount lại.

### 9.1. Database data directory là contract

Mỗi image database có data directory riêng. Với PostgreSQL official image, path thường là `/var/lib/postgresql/data`, nhưng cần kiểm tra theo image/version và biến môi trường.

Mount sai path có thể làm:

- Database vẫn chạy nhưng dữ liệu nằm ở path khác.
- Volume không chứa data thật.
- Init script chạy ngoài dự kiến.
- Recreate không thấy dữ liệu.

Không đoán path. Đọc documentation/image inspect và kiểm tra bằng `docker inspect`, logs và filesystem.

### 9.2. Không đổi major version tùy tiện

Volume PostgreSQL chứa data format phụ thuộc version. Không mount volume PostgreSQL 16 vào PostgreSQL 17 rồi giả định tự migrate. Major upgrade cần quy trình backup/migration/upgrade phù hợp.

### 9.3. Không publish database nếu không cần

Trong local learning, `-p 5432:5432` giúp host client kết nối. Trong mô hình API–database nội bộ, chỉ cần cùng Docker network:

~~~text
API → postgres:5432
~~~

Không publish port database giúp giảm exposure, nhưng không thay authentication.

---

## 10. Permission và UID/GID

Volume/bind mount có filesystem ownership. Process container chạy bằng UID/GID nào thì quyền được kiểm tra theo UID/GID đó, không theo tên user trên host.

Kiểm tra:

~~~bash
docker run --rm alpine:3.20 id
ls -ln ./data
~~~

`ls -n` hiển thị numeric UID/GID, hữu ích khi tên user khác nhau giữa host/container.

### 10.1. Lỗi permission với bind mount

~~~bash
mkdir -p ./data
docker run --rm \
  --user 10001:10001 \
  --mount type=bind,src="$(pwd)/data",dst=/data \
  alpine:3.20 sh -c 'touch /data/file'
~~~

Có thể fail nếu host directory không cho UID `10001` ghi. Cách xử lý phải rõ theo môi trường:

- Tạo/chown directory cho UID/GID cần thiết.
- Chạy container với user phù hợp trong development.
- Dùng named volume để Docker initialize ownership phù hợp khi image hỗ trợ.
- Không dùng `chmod 777` như giải pháp mặc định.

### 10.2. Named volume initialization

Khi mount named volume lần đầu vào một path có dữ liệu trong image, Docker có thể copy dữ liệu đó vào volume trong một số trường hợp. Behavior cần được kiểm tra theo image/workflow; đừng dựa vào nó cho migration quan trọng.

Với database chính thức, hãy đọc entrypoint initialization logic của image và dùng volume mới/backup test để xác minh.

### 10.3. UID/GID trong database image

Database image thường có user riêng và entrypoint chỉnh permission. Nếu override `USER`, mount path hoặc command không phù hợp, init có thể fail. Không override user database image khi chưa hiểu entrypoint và ownership.

---

## 11. Volume backup và restore

### 11.1. Backup file-level bằng helper container

Có thể mount volume vào container tạm và tạo tar archive ở thư mục host:

~~~bash
docker run --rm \
  --mount type=volume,src=pgdata,dst=/volume,readonly \
  --mount type=bind,src="$(pwd)/backup",dst=/backup \
  alpine:3.20 \
  tar czf /backup/pgdata-$(date +%Y%m%d-%H%M%S).tar.gz -C /volume .
~~~

Tạo thư mục backup trước:

~~~bash
mkdir -p ./backup
~~~

Lệnh trên minh họa file-level copy. Nó không tự đảm bảo database đang ở trạng thái nhất quán.

### 11.2. Backup database nhất quán

Với PostgreSQL, logical backup thường dùng `pg_dump`/`pg_dumpall`:

~~~bash
docker exec postgres-new pg_dump -U postgres -d learning > backup/learning.sql
~~~

Restore vào database khác:

~~~bash
cat backup/learning.sql | docker exec -i postgres-new psql -U postgres -d learning
~~~

Trong production cần cân nhắc:

- Snapshot storage nhất quán.
- PostgreSQL base backup/WAL archiving.
- Encryption.
- Retention.
- Restore test.
- RPO/RTO.
- Credential và quyền file backup.

`docker cp`, tar volume và snapshot khác nhau về consistency và operational guarantee.

### 11.3. Restore volume từ tar

Tạo volume mới:

~~~bash
docker volume create pgdata-restore
~~~

Extract archive vào volume:

~~~bash
docker run --rm \
  --mount type=volume,src=pgdata-restore,dst=/volume \
  --mount type=bind,src="$(pwd)/backup",dst=/backup,readonly \
  alpine:3.20 \
  sh -c 'tar xzf /backup/backup.tar.gz -C /volume'
~~~

Sau đó mount volume vào container tương thích và kiểm tra. Không restore đè volume production khi chưa xác minh archive, version, owner và rollback.

### 11.4. Backup có thể đọc được chưa đủ

Một archive tồn tại không chứng minh backup tốt. Cần test:

1. Tạo backup.
2. Tạo volume/database test mới.
3. Restore.
4. Chạy query/health/integrity check.
5. Đo thời gian restore.
6. Ghi nhận lỗi và quyền truy cập.

---

## 12. Storage driver ở mức khái niệm

Docker storage gồm nhiều lớp:

~~~text
Application write
    ↓
Container writable layer / volume mount
    ↓
Storage driver
    ↓
Host filesystem / VM disk / external backend
~~~

Storage driver quản lý cách image layer và writable layer được lưu, thường dùng copy-on-write. Chi tiết driver phụ thuộc OS/runtime; không nên giả định path trên host giống nhau giữa Linux và Docker Desktop.

Named volume có thể dùng local driver hoặc driver/plugin khác cho NFS/cloud storage. Với production, storage backend cần đánh giá:

- Durability.
- Performance/IOPS.
- Availability.
- Snapshot/backup.
- Encryption.
- Mount semantics.
- Failure behavior.
- Cost.

### 12.1. `docker system df`

~~~bash
docker system df
docker system df -v
docker volume ls
docker volume inspect pgdata
~~~

Theo dõi volume size theo cách phù hợp; `docker system df` không phải lúc nào cũng thể hiện mọi usage bên trong volume như một filesystem analyzer đầy đủ.

### 12.2. Volume lifecycle

Xóa container không xóa named volume tự động:

~~~bash
docker rm postgres-new
docker volume ls
~~~

Xóa volume là thao tác destructive:

~~~bash
docker volume rm pgdata
~~~

Chỉ làm sau khi xác nhận backup và owner. `docker volume prune` có thể xóa volumes không được dùng; trên host dùng chung, cần kiểm tra kỹ.

---

## 13. `docker compose down -v` và dữ liệu

`docker compose down` thường xóa container/network của project. Thêm `-v` yêu cầu xóa volumes được khai báo/anonymous liên quan:

~~~bash
docker compose down
~~~

khác với:

~~~bash
docker compose down -v
~~~

`down -v` có thể xóa database data. Không chạy nếu chưa xác nhận volume có thể xóa.

Trong development, có thể dùng `down -v` khi muốn reset database hoàn toàn. Trong môi trường có dữ liệu cần giữ, phải kiểm tra compose project, volume name, backup và approval trước.

---

## 14. Lab 1: So sánh writable layer và named volume

### Không có volume

~~~bash
docker run --name no-volume alpine:3.20 sh -c 'echo lost > /data.txt'
docker ps -a --filter name=no-volume
docker rm no-volume
docker run --rm alpine:3.20 sh -c 'test ! -e /data.txt'
~~~

### Có named volume

~~~bash
docker volume create lesson-data
docker run --name volume-writer \
  -v lesson-data:/data \
  alpine:3.20 sh -c 'echo kept > /data/message.txt'
docker rm volume-writer
docker run --rm -v lesson-data:/data alpine:3.20 cat /data/message.txt
docker volume rm lesson-data
~~~

Viết kết luận về object nào bị xóa ở mỗi flow.

---

## 15. Lab 2: PostgreSQL persistence và backup

~~~bash
docker volume create week9-pgdata

docker run -d --name week9-postgres \
  -e POSTGRES_USER=learning \
  -e POSTGRES_PASSWORD=local-only \
  -e POSTGRES_DB=learning \
  -v week9-pgdata:/var/lib/postgresql/data \
  postgres:16
~~~

Tạo dữ liệu:

~~~bash
docker exec week9-postgres psql -U learning -d learning \
  -c "CREATE TABLE notes (id serial PRIMARY KEY, body text); INSERT INTO notes(body) VALUES ('week 9');"
~~~

Tạo logical backup:

~~~bash
mkdir -p backup
docker exec week9-postgres pg_dump -U learning -d learning > backup/week9.sql
~~~

Recreate container với cùng volume:

~~~bash
docker rm -f week9-postgres
docker run -d --name week9-postgres-new \
  -e POSTGRES_USER=learning \
  -e POSTGRES_PASSWORD=local-only \
  -e POSTGRES_DB=learning \
  -v week9-pgdata:/var/lib/postgresql/data \
  postgres:16

docker exec week9-postgres-new psql -U learning -d learning -c 'SELECT * FROM notes;'
~~~

Kiểm tra mount và volume:

~~~bash
docker inspect --format '{{json .Mounts}}' week9-postgres-new
docker volume inspect week9-pgdata
~~~

Dọn lab sau khi đã giữ backup cần thiết:

~~~bash
docker rm -f week9-postgres-new
docker volume rm week9-pgdata
~~~

---

## 16. Lab 3: Permission và read-only mount

Tạo volume và file:

~~~bash
docker volume create permission-demo
docker run --rm -v permission-demo:/data alpine:3.20 sh -c 'echo ok > /data/file'
~~~

Mount read-only:

~~~bash
docker run --rm \
  --mount type=volume,src=permission-demo,dst=/data,readonly \
  alpine:3.20 sh -c 'cat /data/file; ! touch /data/should-fail'
~~~

Kiểm tra volume sau đó và xóa:

~~~bash
docker volume inspect permission-demo
docker volume rm permission-demo
~~~

Lab này chỉ kiểm tra mount mode; permission database thực tế còn phụ thuộc user/entrypoint của image.

---

## 17. Lỗi thường gặp

### Xóa container làm mất dữ liệu

Dữ liệu nằm trong writable layer hoặc anonymous volume không được nhận diện/giữ đúng. Dùng named volume và kiểm tra `Mounts` trước khi remove.

### Mount sai database path

Container vẫn start nhưng database ghi vào path khác hoặc volume rỗng. Kiểm tra documentation/image version, env, logs và filesystem.

### Permission denied

UID/GID process không có quyền trên bind mount/volume. Kiểm tra numeric ownership và tránh `chmod 777` tùy tiện.

### Volume bị mount nhầm vào thư mục không rỗng

Mount có thể che các file image ở destination. Nếu app cần file mặc định, hãy hiểu initialization behavior trước khi mount.

### Backup tar nhưng database đang ghi

File backup có thể không nhất quán. Với database, dùng dump/native backup hoặc stop/quiesce theo quy trình phù hợp.

### Restore sai major version

Database data directory không luôn tương thích giữa major version. Dùng logical dump/upgrade procedure và test restore.

### `docker compose down -v` làm mất data

`-v` là lựa chọn xóa volume. Kiểm tra volume/project trước khi chạy.

### `volume prune` xóa volume chưa dùng nhưng còn cần

“Không đang attach” không nhất thiết nghĩa “không còn giá trị”. Volume có thể là backup/archive hoặc được attach lại sau. Dùng owner/label/retention policy.

### Dùng bind mount production tùy tiện

Path host, permission, backup và portability kém hơn named/external storage phù hợp. Chọn storage theo deployment platform.

---

## 18. Bài tập tự làm

### Bài 1: Persistent volume

Tạo named volume, ghi file từ container A, xóa A, đọc file từ container B. Lặp lại flow không volume và giải thích khác nhau.

### Bài 2: PostgreSQL data directory

Chạy PostgreSQL với named volume, tạo table, remove/recreate container và xác nhận data còn. Sau đó inspect mount path và volume metadata.

### Bài 3: Backup/restore

Tạo logical dump PostgreSQL, tạo database/container test mới, restore dump và chạy query kiểm chứng. Ghi lại thời gian backup/restore.

### Bài 4: Permission

Chạy bind mount bằng UID khác nhau, tạo file, kiểm tra ownership trên host và sửa permission theo nguyên tắc least privilege.

### Bài 5: Storage choice

Với từng loại dữ liệu sau, chọn writable layer, named volume, bind mount, anonymous volume hoặc tmpfs và giải thích: source code dev, database data, cache tạm, upload user, secret tạm, config local.

### Bài 6: Compose safety

Tạo Compose project có named volume, chạy `down` và kiểm tra data; chỉ sau khi backup hãy thử `down -v` trong project lab riêng và ghi lại tác động.

---

## 19. Câu hỏi kiểm tra và đáp án gợi ý

1. Vì sao writable layer không phù hợp cho database?
2. Named volume khác bind mount thế nào?
3. Anonymous volume có nhược điểm gì?
4. Khi nào dùng tmpfs?
5. Vì sao `docker rm` không nên làm mất named volume?
6. `docker compose down -v` nguy hiểm ở điểm nào?
7. Backup tar volume có luôn nhất quán database không?
8. Vì sao UID/GID quan trọng với bind mount?
9. Tại sao mount sai database data directory gây rủi ro?
10. Storage driver làm nhiệm vụ gì ở mức khái niệm?
11. Vì sao database major upgrade cần quy trình riêng?
12. Volume persist có thay thế backup không?

Đáp án ngắn: writable layer theo container; named volume do Docker quản lý còn bind mount trỏ path host; anonymous khó quản lý/backup; tmpfs cho dữ liệu tạm trong memory; named volume có lifecycle độc lập; `down -v` có thể xóa database volume; tar file-level không đảm bảo consistency; kernel kiểm tra numeric UID/GID; mount sai path khiến data nằm nơi khác; driver lưu/quản lý layer/storage; major version có data format khác; persist chỉ giúp survive recreate, không thay backup.

---

## 20. Tiêu chí hoàn thành tuần 9

- [ ] Phân biệt writable layer và persistent storage.
- [ ] Chọn đúng named volume, bind mount, anonymous volume và tmpfs.
- [ ] Tạo/inspect/mount/remove named volume.
- [ ] Chạy PostgreSQL với volume tại data directory đúng.
- [ ] Recreate container và chứng minh data vẫn còn.
- [ ] Hiểu UID/GID và xử lý permission đúng cách.
- [ ] Thực hiện được backup/restore ở mức file và logical database.
- [ ] Biết backup volume không tự đảm bảo database consistency.
- [ ] Hiểu storage driver ở mức khái niệm.
- [ ] Biết `docker compose down -v` và `volume prune` có thể destructive.
- [ ] Không dùng volume persistence thay cho backup/restore test.
- [ ] Biết chọn storage theo durability, performance, portability và security.

Kiểm tra cuối buổi:

~~~bash
docker volume ls
docker volume inspect <volume>
docker system df -v
docker inspect --format '{{json .Mounts}}' <container>
~~~

Trước mọi lệnh xóa volume, xác nhận owner, dữ liệu, backup và khả năng restore. Persistent storage chỉ có giá trị vận hành khi bạn biết nó ở đâu, ai sở hữu, backup thế nào và restore ra sao.

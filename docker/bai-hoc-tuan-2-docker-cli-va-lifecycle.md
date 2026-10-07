# Tuần 2: Docker CLI và lifecycle

## 1. Mục tiêu

Sau bài này, bạn có thể:

- Dùng các lệnh `run`, `create`, `start`, `stop`, `restart`, `kill` và `pause` đúng mục đích.
- Phân biệt foreground mode, detached mode, `attach` và `exec`.
- Đọc log, exit code và trạng thái để điều tra container lỗi.
- Dùng `inspect`, `cp`, `diff`, `top`, `stats`, `rename` và labels.
- Chạy PostgreSQL và Redis bằng CLI với cấu hình tối thiểu.
- Đặt giới hạn CPU/memory và quan sát bằng `docker stats`.
- Hiểu vì sao `exec` không phải cơ chế cấu hình lâu dài và container đang chạy không phải backup.

Thời lượng đề xuất: **8–12 giờ** gồm lý thuyết, lab và bài tập.

---

## 2. Chuẩn bị và quy tắc an toàn

Kiểm tra Docker:

~~~bash
docker version
docker info
docker compose version
~~~

Các lệnh trong bài dùng container thử nghiệm có tên `cli-web`, `cli-postgres`, `cli-redis` và `cli-limited`. Nếu máy đã có container cùng tên, hãy chọn tên khác hoặc kiểm tra trước:

~~~bash
docker ps -a --filter name=cli-
~~~

Khi chạy PostgreSQL, password trong ví dụ chỉ dành cho local learning. Không dùng password này ở staging/production và không commit secret vào Git.

---

## 3. Mental model của Docker CLI

Docker CLI là client gửi yêu cầu đến Docker daemon. Daemon tạo và quản lý container, network, image, volume và metadata. Runtime thực thi process.

~~~text
docker command
    ↓
Docker CLI → Docker daemon → container runtime → process
    ↑              ↓
 terminal       state, logs, config, resources
~~~

Một lệnh có thể:

- Tạo instance mới (`run`, `create`).
- Thay đổi trạng thái instance (`start`, `stop`, `restart`, `pause`).
- Thực thi hoặc kết nối vào process (`exec`, `attach`).
- Đọc metadata/quan sát (`inspect`, `logs`, `top`, `stats`).
- Sửa metadata của container (`rename`, labels ở thời điểm create/run).
- Sao chép hoặc so sánh filesystem (`cp`, `diff`).

Nhóm lệnh quan sát thường không thay đổi workload. Nhóm lifecycle thay đổi trạng thái và cần được dùng có chủ đích.

---

## 4. Container state và lifecycle

Các trạng thái thường gặp:

~~~text
Created → Running → Exited
   │         │         │
   └─────────┴──────→ Removed

Running ⇄ Paused
~~~

- **Created**: metadata đã được tạo nhưng process chưa chạy.
- **Running**: process chính đang chạy.
- **Paused**: process bị đóng băng tạm thời.
- **Exited**: process chính đã kết thúc; container vẫn còn metadata và có thể inspect.
- **Removed**: container instance không còn; image không tự bị xóa.

Xem mọi container, kể cả container đã dừng:

~~~bash
docker ps
docker ps -a
docker ps -a --filter status=exited
docker ps -a --filter name=cli-
~~~

Xem một số thông tin ngắn gọn:

~~~bash
docker ps --format 'table {{.Names}}\\t{{.Status}}\\t{{.Ports}}\\t{{.Image}}'
~~~

---

## 5. `run`, `create` và `start`

### 5.1. `docker run`

`docker run` thường thực hiện nhiều việc: tìm/pull image nếu cần, tạo container, cấu hình runtime và start process.

~~~bash
docker run --name cli-web -d -p 8080:80 nginx:1.27
~~~

Các option thường gặp:

| Option | Ý nghĩa |
|---|---|
| `--name` | Đặt tên container |
| `-d`, `--detach` | Chạy background và trả terminal về |
| `-p`, `--publish` | Publish port host:container |
| `-e`, `--env` | Đặt environment variable |
| `--env-file` | Đọc environment từ file |
| `--restart` | Đặt restart policy |
| `--label` | Gắn metadata dạng key/value |
| `--rm` | Xóa container sau khi process kết thúc |
| `--cpus` | Giới hạn CPU |
| `--memory` | Giới hạn memory |
| `--name` | Chọn tên ổn định thay vì random name |

### 5.2. `docker create` và `docker start`

Dùng hai bước khi muốn kiểm tra cấu hình trước khi chạy:

~~~bash
docker create --name cli-web-manual -p 8081:80 nginx:1.27
docker inspect cli-web-manual
docker start cli-web-manual
~~~

- `create` tạo container nhưng chưa chạy process.
- `start` chạy một container đã tồn tại.
- `run` phù hợp cho flow thông thường; `create` + `start` hữu ích khi cần tách việc tạo và khởi động.

Một container đã `Exited` có thể start lại với cấu hình cũ. `start` không build image mới và không thay đổi command đã định nghĩa khi container được tạo.

---

## 6. Foreground, detached và attach

### 6.1. Foreground mode

Nếu không dùng `-d`, terminal sẽ gắn với process/container:

~~~bash
docker run --name cli-foreground alpine:3.20 sh -c 'echo start; sleep 30; echo done'
~~~

Trong lúc command chạy, bạn nhìn thấy output trực tiếp. `Ctrl+C` có thể gửi signal đến process/container tùy cách terminal được attach.

### 6.2. Detached mode

~~~bash
docker run --name cli-detached -d alpine:3.20 sh -c 'while true; do date; sleep 5; done'
docker logs cli-detached
~~~

Detached mode phù hợp với service chạy lâu. Nó không có nghĩa container tự daemonize process bên trong; Docker vẫn quản lý process chính của container.

### 6.3. `attach`

~~~bash
docker attach cli-detached
~~~

`attach` kết nối terminal hiện tại vào stdin/stdout/stderr của **process chính** đang chạy. Nó không tạo process mới. Để detach mà không gửi signal kết thúc, dùng sequence mặc định `Ctrl-p`, rồi `Ctrl-q` trong terminal tương tác.

Không nên dùng `attach` như shell debug tổng quát. Nếu cần shell mới, dùng `exec`.

### 6.4. `exec`

~~~bash
docker exec -it cli-detached sh
~~~

`exec` tạo một process phụ trong container đang running. Có thể chạy command một lần:

~~~bash
docker exec cli-detached date
docker exec cli-detached ps
~~~

So sánh:

| Lệnh | Kết nối vào | Tạo process mới? | Dùng khi |
|---|---|---:|---|
| `attach` | Process chính | Không | Theo dõi/tương tác với process chính |
| `exec` | Container đang chạy | Có | Debug, kiểm tra, chạy command phụ |

`exec` không biến thay đổi trong container thành image mới.

---

## 7. Stop, restart, kill và pause

~~~bash
docker stop cli-web
docker start cli-web
docker restart cli-web
docker kill cli-web
docker pause cli-web
docker unpause cli-web
~~~

### `stop`

`docker stop` gửi signal yêu cầu process dừng, chờ timeout rồi force kill nếu process không thoát. Có thể chỉ định timeout:

~~~bash
docker stop --time 20 cli-web
~~~

Ứng dụng nên xử lý graceful shutdown: ngừng nhận request mới, hoàn tất request phù hợp, đóng connection và thoát.

### `restart`

`restart` thực hiện stop rồi start lại cùng instance. Nó không tạo container mới và không sửa image.

### `kill`

`kill` dừng process nhanh, thường dùng khi container không phản hồi hoặc cần force stop:

~~~bash
docker kill cli-web
~~~

Không dùng `kill` như shutdown bình thường cho database hoặc ứng dụng cần flush dữ liệu.

### `pause`

`pause` đóng băng process bằng cơ chế OS. Nó khác `stop`: process không chạy tiếp trong thời gian pause nhưng container chưa shutdown:

~~~bash
docker pause cli-web
docker inspect --format '{{.State.Status}}' cli-web
docker unpause cli-web
~~~

---

## 8. Logs và exit code

### 8.1. Đọc log

~~~bash
docker logs cli-web
docker logs --tail 50 cli-web
docker logs --since 10m cli-web
docker logs --timestamps cli-web
docker logs -f cli-web
~~~

`docker logs` chủ yếu đọc stdout/stderr của process chính. Nó không tự động đọc mọi file `.log` bên trong filesystem container.

Nếu log có thể chứa secret, token hoặc thông tin cá nhân, phải xử lý masking và quyền truy cập log phù hợp.

### 8.2. Exit code

Exit code mô tả kết quả cuối của process chính:

~~~bash
docker run --name cli-exit-0 alpine:3.20 sh -c 'exit 0'
docker inspect --format '{{.State.ExitCode}}' cli-exit-0

docker run --name cli-exit-7 alpine:3.20 sh -c 'echo failed; exit 7'
docker inspect --format '{{.State.ExitCode}}' cli-exit-7
docker logs cli-exit-7
~~~

Quy ước phổ biến:

- `0`: kết thúc thành công.
- Khác `0`: có lỗi hoặc kết thúc theo điều kiện đặc biệt.
- `137`: thường liên quan process bị kill bằng SIGKILL; có thể do OOM hoặc force kill.
- `143`: thường liên quan process nhận SIGTERM rồi kết thúc.

Đây là heuristic, không phải bằng chứng duy nhất. Cần xem thêm `OOMKilled`, error, log và event.

~~~bash
docker inspect --format 'status={{.State.Status}} exit={{.State.ExitCode}} oom={{.State.OOMKilled}} error={{.State.Error}}' <container-name>
~~~

---

## 9. Inspect, cp và diff

### 9.1. `inspect`

~~~bash
docker inspect cli-web
docker inspect --format '{{.State.Status}}' cli-web
docker inspect --format '{{.Config.Cmd}}' cli-web
docker inspect --format '{{json .Config.Labels}}' cli-web
docker inspect --format '{{json .NetworkSettings.Ports}}' cli-web
~~~

Khi debug, kiểm tra image thực tế, command, environment, labels, restart policy, resource limits, mounts, network và exit state.

### 9.2. `cp`

Copy file từ container ra host:

~~~bash
docker exec cli-web sh -c 'echo sample-log > /tmp/sample.log'
docker cp cli-web:/tmp/sample.log ./sample.log
cat ./sample.log
~~~

Copy file từ host vào container:

~~~bash
echo 'temporary config' > /tmp/cli-config.txt
docker cp /tmp/cli-config.txt cli-web:/tmp/cli-config.txt
docker exec cli-web cat /tmp/cli-config.txt
~~~

`cp` phù hợp để lấy artifact debug hoặc thử nghiệm. File copy vào writable layer không làm thay đổi image và có thể mất khi container bị remove.

### 9.3. `diff`

~~~bash
docker diff cli-web
~~~

Các trạng thái thường gặp:

- `A`: file được thêm.
- `C`: file bị thay đổi.
- `D`: file bị xóa.

`diff` giúp nhận biết container đã drift khỏi image, nhưng không thay thế version control hoặc image build reproducible.

---

## 10. Top, stats và resource limits

### 10.1. `top`

~~~bash
docker top cli-web
~~~

Lệnh này xem process của container từ phía Docker. Nó hữu ích khi process treo, có nhiều process bất ngờ hoặc command không đúng như dự kiến.

### 10.2. `stats`

~~~bash
docker stats cli-web
~~~

Các chỉ số chính:

- CPU usage.
- Memory usage và memory limit.
- Network input/output.
- Block I/O.
- Process count.

### 10.3. Giới hạn CPU và memory

~~~bash
docker run --name cli-limited -d \
  --cpus=0.50 \
  --memory=128m \
  --memory-swap=128m \
  alpine:3.20 sh -c 'while true; do :; done'

docker stats cli-limited
~~~

- `--cpus=0.50`: giới hạn tương đương khoảng nửa CPU theo cách Docker phân bổ.
- `--memory=128m`: giới hạn memory container.
- `--memory-swap=128m`: tránh cho container dùng thêm swap ngoài limit memory trong cấu hình này.

Resource limit không tự làm ứng dụng khỏe hơn. Nếu limit quá thấp, app có thể bị throttling hoặc OOM kill. Cần thử với workload thực tế.

Dọn container sau lab:

~~~bash
docker rm -f cli-limited
~~~

---

## 11. Rename và labels

### 11.1. Rename

~~~bash
docker rename cli-web cli-web-renamed
docker ps -a --filter name=cli-web-renamed
~~~

Rename chỉ thay đổi tên container, không thay đổi image, process hay filesystem. Script production không nên phụ thuộc vào tên thủ công nếu có cơ chế discovery khác.

### 11.2. Labels

Labels là metadata key/value để lọc và phân loại container:

~~~bash
docker run --name cli-labeled -d \
  --label app=learning \
  --label team=backend \
  --label purpose=week2 \
  nginx:1.27

docker ps --filter label=app=learning
docker inspect --format '{{json .Config.Labels}}' cli-labeled
~~~

Labels hữu ích cho tooling, cleanup có chọn lọc và automation. Chúng không phải cơ chế bảo mật hay secret store; không đặt password/token vào label.

---

## 12. Lab PostgreSQL

Chạy PostgreSQL local để luyện `run`, env, logs, exec, inspect và stop/start:

~~~bash
docker run --name cli-postgres -d \
  -e POSTGRES_USER=learning \
  -e POSTGRES_PASSWORD=local-only-password \
  -e POSTGRES_DB=learning \
  -p 5433:5432 \
  postgres:16
~~~

Quan sát startup:

~~~bash
docker ps
docker logs --tail 50 cli-postgres
docker inspect --format 'status={{.State.Status}} image={{.Config.Image}}' cli-postgres
~~~

Vào database bằng `exec`:

~~~bash
docker exec -it cli-postgres psql -U learning -d learning
~~~

Trong `psql`:

~~~sql
SELECT current_database();
CREATE TABLE notes (id SERIAL PRIMARY KEY, body TEXT NOT NULL);
INSERT INTO notes(body) VALUES ('created inside container');
SELECT * FROM notes;
\q
~~~

Dừng và start lại:

~~~bash
docker stop cli-postgres
docker ps -a --filter name=cli-postgres
docker start cli-postgres
docker exec -it cli-postgres psql -U learning -d learning -c 'SELECT * FROM notes;'
~~~

Ở lab này dữ liệu chỉ tồn tại trong container filesystem và không nên coi là dữ liệu bền vững. Volume sẽ được học ở phần storage; mục tiêu tuần này là quan sát lifecycle và lệnh CLI.

---

## 13. Lab Redis

~~~bash
docker run --name cli-redis -d -p 6380:6379 redis:7-alpine
docker logs --tail 30 cli-redis
docker exec -it cli-redis redis-cli ping
docker exec cli-redis redis-cli SET lesson week2
docker exec cli-redis redis-cli GET lesson
~~~

Kiểm tra command, process và port:

~~~bash
docker top cli-redis
docker port cli-redis
docker inspect --format '{{json .NetworkSettings.Ports}}' cli-redis
docker stats --no-stream cli-redis
~~~

Dừng và đọc lại trạng thái:

~~~bash
docker stop cli-redis
docker inspect --format 'status={{.State.Status}} exit={{.State.ExitCode}}' cli-redis
~~~

---

## 14. Bài tập điều tra lỗi

### Bài 1: PostgreSQL khởi động lỗi

Chạy cố ý thiếu password bắt buộc:

~~~bash
docker run --name cli-postgres-broken -d \
  -e POSTGRES_USER=learning \
  postgres:16
~~~

Dùng các lệnh sau để điều tra:

~~~bash
docker ps -a --filter name=cli-postgres-broken
docker logs cli-postgres-broken
docker inspect --format 'status={{.State.Status}} exit={{.State.ExitCode}} error={{.State.Error}}' cli-postgres-broken
~~~

Giải thích vì sao container exited và sửa bằng cách tạo container mới với `POSTGRES_PASSWORD`.

### Bài 2: Resource limit

Chạy `cli-limited` với `--memory=64m`, quan sát `docker stats`, sau đó giải thích sự khác biệt giữa CPU throttling và OOM kill. Kiểm tra `State.OOMKilled` sau khi container kết thúc.

### Bài 3: Attach hay exec?

Với một container chạy vòng lặp vô hạn:

- Dùng `attach` để quan sát process chính.
- Detach mà không stop container.
- Dùng `exec` để chạy `date` và `ps`.
- Giải thích vì sao `exec` không thay thế `attach` trong trường hợp này.

### Bài 4: Filesystem drift

Tạo file bằng `exec`, dùng `docker diff` để tìm file mới, dùng `docker cp` lấy file ra host, sau đó remove container và giải thích điều gì mất.

---

## 15. Cần tránh

### Dùng `exec` để cấu hình lâu dài

`exec` dành cho debug và thao tác tạm thời. Cấu hình lâu dài phải nằm trong Dockerfile, environment/config management, volume hoặc source of truth phù hợp.

### Sửa file rồi tưởng image đã thay đổi

Filesystem thay đổi thuộc writable layer của container. Muốn lưu thay đổi thành artifact reproducible, hãy sửa source/Dockerfile và build image mới. `docker commit` không nên là workflow build production vì khó review và khó tái tạo.

### Dùng container đang chạy làm backup

Container đang chạy có thể đang ghi dữ liệu. Copy file bằng `docker cp` không đảm bảo snapshot nhất quán cho database. Backup cần cơ chế native của database, snapshot storage hoặc quy trình nhất quán; nội dung này sẽ học ở phần volume/storage.

### Force remove không kiểm tra

`docker rm -f` có thể stop và xóa container ngay. Chỉ dùng cho container test hoặc khi đã hiểu rõ tác động.

### Đặt secret trên command line trong môi trường chia sẻ

Environment và command có thể xuất hiện trong inspect, process metadata, shell history hoặc log. Ví dụ local ở trên chỉ dùng để học; production cần secret manager hoặc cơ chế an toàn hơn.

---

## 16. Cheat sheet

| Lệnh | Mục đích |
|---|---|
| `docker run` | Tạo và chạy container |
| `docker create` | Tạo container chưa chạy |
| `docker start` | Start container đã tồn tại |
| `docker stop` | Graceful stop |
| `docker restart` | Stop rồi start |
| `docker kill` | Force stop |
| `docker pause` | Đóng băng process |
| `docker attach` | Kết nối process chính |
| `docker exec` | Chạy process phụ |
| `docker logs` | Đọc stdout/stderr |
| `docker inspect` | Xem metadata/state |
| `docker cp` | Copy file hai chiều |
| `docker diff` | Xem thay đổi filesystem |
| `docker top` | Xem process |
| `docker stats` | Xem resource usage |
| `docker rename` | Đổi tên container |
| `docker ps --filter` | Lọc container |

---

## 17. Câu hỏi kiểm tra và đáp án gợi ý

1. `docker run` khác `docker create` + `docker start` thế nào?
2. Khi nào dùng `attach`, khi nào dùng `exec`?
3. `docker stop` khác `docker kill` ở điểm nào?
4. Vì sao `docker logs` có thể không thấy file log trong `/var/log`?
5. Exit code `0`, `137` và `143` thường gợi ý điều gì?
6. `docker cp` có làm thay đổi image không?
7. `docker diff` giúp nhận biết điều gì?
8. Vì sao resource limit cần đi cùng quan sát `docker stats`?
9. Vì sao không dùng container đang chạy làm backup database?
10. Label có phải secret store không?

Đáp án ngắn: `run` thường tạo và start; `attach` nối process chính còn `exec` tạo process phụ; `stop` graceful còn `kill` force; logs đọc stdout/stderr chứ không tự gom file; `0` thành công, `137` thường SIGKILL/OOM, `143` thường SIGTERM; `cp` không đổi image; `diff` cho thấy filesystem drift; limit quá thấp gây throttling/OOM; copy filesystem không đảm bảo snapshot nhất quán; label không dành cho secret.

---

## 18. Tiêu chí hoàn thành tuần 2

- [ ] Dùng được `run`, `create`, `start`, `stop`, `restart`, `kill` và `pause`.
- [ ] Phân biệt được foreground, detached, `attach` và `exec`.
- [ ] Đọc được logs, status và exit code.
- [ ] Dùng được `inspect`, `cp`, `diff`, `top`, `stats`, `rename` và labels.
- [ ] Chạy được PostgreSQL và Redis bằng CLI.
- [ ] Điều tra được service khởi động lỗi từ `ps -a`, `logs` và `inspect`.
- [ ] Đặt memory/CPU limit và giải thích kết quả quan sát.
- [ ] Biết `exec` không phải cách cấu hình lâu dài.
- [ ] Biết container đang chạy không phải backup nhất quán.
- [ ] Dọn các container thử nghiệm:

~~~bash
docker rm -f cli-web cli-web-manual cli-foreground cli-detached cli-exit-0 cli-exit-7 cli-postgres cli-redis cli-postgres-broken cli-labeled cli-web-renamed 2>/dev/null || true
rm -f ./sample.log
~~~

Chỉ chạy lệnh dọn dẹp nếu các tên trên là container thử nghiệm của bạn. Không xóa container production hoặc container của người khác.

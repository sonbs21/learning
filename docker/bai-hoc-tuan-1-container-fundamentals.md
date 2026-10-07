# Tuần 1: Container fundamentals

## 1. Mục tiêu

Sau bài này, bạn có thể:

- Giải thích container và phân biệt container với virtual machine.
- Mô tả image, container, registry, Docker CLI, daemon và runtime.
- Hiểu process, namespace, cgroup, writable layer và lifecycle.
- Chạy, inspect, debug, stop và remove container bằng Docker CLI.
- Hiểu ephemeral container, immutability và nguyên tắc một container có một concern chính.

Thời lượng đề xuất: **8–12 giờ** gồm lý thuyết, lab và bài tập.

---

## 2. Chuẩn bị

Cài Docker Desktop trên macOS/Windows hoặc Docker Engine + Compose plugin trên Linux.

~~~bash
docker version
docker info
docker compose version
docker run --rm hello-world
~~~

`docker version` thường có Client và Server. Client là CLI; Server là Docker daemon. Nếu gặp `Cannot connect to the Docker daemon`, hãy khởi động Docker Desktop hoặc Docker Engine.

---

## 3. Container là gì?

Container là một process hoặc nhóm process được chạy với cơ chế cô lập của operating system. Nó có thể có filesystem, process tree, network interface và hostname riêng theo cấu hình.

Container thường **chia sẻ kernel của host**, khởi động nhanh và được tạo để chạy một workload cụ thể. Container không phải sandbox tuyệt đối; mức cô lập phụ thuộc vào kernel, runtime, quyền và cấu hình.

### Container và virtual machine

~~~text
VM:        Host → Hypervisor → Guest OS kernel → Application
Container: Host OS kernel → Runtime → Isolated process + filesystem view
~~~

| Đặc điểm | Virtual machine | Container |
|---|---|---|
| Đơn vị chính | Máy tính ảo | Process hoặc nhóm process |
| Kernel | Guest kernel riêng | Thường dùng chung kernel host |
| Khởi động | Thường lâu hơn | Thường nhanh hơn |
| Kích thước | Thường lớn hơn | Thường nhỏ hơn |
| Phù hợp | Chạy OS khác, cô lập hạ tầng | Đóng gói và triển khai workload |

VM không bị container thay thế trong mọi tình huống. VM phù hợp khi cần kernel khác hoặc cô lập hạ tầng mạnh; container phù hợp khi cần tái tạo môi trường và scale process nhanh.

---

## 4. Process, namespace và cgroup

### Process

Process là chương trình đang chạy. Container không tự thân là một máy tính; nó là cách runtime khởi chạy process với góc nhìn và giới hạn riêng. Nếu process chính kết thúc, container thường chuyển sang `Exited`.

### Namespace

Namespace tạo góc nhìn tài nguyên riêng:

- PID: process tree riêng; process chính thường là PID 1 trong container.
- Network: interface, IP và routing riêng.
- Mount: filesystem view riêng.
- UTS: hostname riêng.
- User: có thể ánh xạ user trong container sang user khác trên host.

### Cgroup

Cgroup theo dõi và giới hạn tài nguyên process như CPU, memory, số process và một số loại I/O. Khi đặt resource limit cho container, Docker dùng cơ chế OS để áp dụng giới hạn; bạn vẫn phải quan sát metrics và kiểm tra hành vi khi quá tải.

---

## 5. Image, container, layer và registry

Image là template chứa filesystem và metadata để tạo container. Image có thể chứa runtime, application files, command, entrypoint và metadata architecture.

Container là instance được tạo từ image, có thêm cấu hình runtime và process:

~~~text
Image: nginx:1.27
  └── docker run
      └── Container: web
          └── Process: nginx
~~~

Một image có thể tạo nhiều container. Image thường có các layer read-only; container thêm một writable layer. File sửa trong container thuộc instance đó, không thay đổi image và không xuất hiện trong container mới từ cùng image.

Registry là nơi lưu trữ/phân phối image. Docker Hub là registry công khai phổ biến; doanh nghiệp có thể dùng private registry.

~~~text
CLI → daemon → local image store → container runtime → process
                 ↑
              registry
~~~

Trong `nginx:1.27`, `nginx` là repository và `1.27` là tag. Tag dễ đọc nhưng không phải bằng chứng tuyệt đối về tính bất biến; digest sẽ học ở tuần 3.

---

## 6. Lab 1: Chạy Nginx

~~~bash
docker run --name web -d -p 8080:80 nginx:1.27
docker ps
curl http://localhost:8080
~~~

Ý nghĩa:

- `--name web`: tên container.
- `-d`: detached mode.
- `-p 8080:80`: host port 8080 chuyển vào container port 80.
- `nginx:1.27`: image và tag.

Flow của port mapping:

~~~text
localhost:8080 → Docker mapping → container:80 → Nginx
~~~

Nếu không dùng `-p`, container có thể chạy nhưng chưa publish port ra host. `EXPOSE` trong image cũng không tự publish port.

~~~bash
docker ps
docker ps -a
docker image ls
~~~

`ps` chỉ hiện running; `ps -a` hiện cả exited; `image ls` hiện image local.

---

## 7. Lab 2: Log, process và exec

~~~bash
docker logs web
docker logs --tail 20 web
docker logs -f web
docker top web
docker stats web
~~~

`docker logs` đọc stdout/stderr của process chính. `-f` follow log; `Ctrl+C` chỉ dừng việc xem log. `top` xem process; `stats` xem CPU, memory, network I/O và block I/O.

Vào container:

~~~bash
docker exec -it web sh
~~~

Bên trong thử `hostname`, `cat /etc/os-release`, `ps`, `ls -la /usr/share/nginx/html`, sau đó `exit`. `exec` chỉ chạy thêm process trong container đang running; nó không tạo image mới. Đây là công cụ debug, không phải cách cấu hình lâu dài.

---

## 8. Lab 3: Inspect và lifecycle

~~~bash
docker inspect web
docker inspect --format '{{.State.Status}}' web
docker inspect --format '{{.Config.Image}}' web
docker inspect --format '{{json .NetworkSettings.Ports}}' web
~~~

Chú ý trạng thái, PID, exit code, image ID, port bindings, network, mounts, environment, command và entrypoint.

~~~text
Created → Running → Exited → Removed
~~~

`docker run` thường là create + start. Tách hai bước:

~~~bash
docker create --name web-manual -p 8081:80 nginx:1.27
docker ps -a --filter name=web-manual
docker start web-manual
~~~

Các lệnh lifecycle:

~~~bash
docker stop web
docker start web
docker restart web
docker kill web
docker pause web
docker unpause web
~~~

- `stop` yêu cầu process dừng bình thường, chờ timeout rồi mới force kill nếu cần.
- `kill` dừng process ngay; chỉ dùng khi cần force stop.
- `pause` đóng băng process, không phải graceful shutdown.
- `restart` stop rồi start lại cùng container.

Xóa container:

~~~bash
docker stop web
docker rm web
docker run --rm hello-world
~~~

`--rm` phù hợp cho command một lần và test nhanh. Không dùng nếu cần xem lại trạng thái hoặc filesystem sau khi process kết thúc.

---

## 9. Ephemeral, immutability và dữ liệu

Ephemeral nghĩa là có vòng đời ngắn và có thể thay thế. Hãy giả định container có thể bị stop, xóa, crash hoặc thay thế cùng host.

Writable layer chưa mất ngay khi process stop nhưng không phải storage bền vững:

~~~text
Container bị xóa → dữ liệu chỉ ở writable layer → dữ liệu có thể mất
~~~

Nếu dữ liệu quan trọng, dùng volume, bind mount hoặc storage bên ngoài. Không lưu dữ liệu quan trọng chỉ trong filesystem writable của container.

Immutability là phát hành bằng image mới thay vì sửa instance đang chạy:

~~~text
Source/config → build image mới → chạy container mới → thay container cũ
~~~

Một container nên có một concern chính, ví dụ API, PostgreSQL hoặc Redis. Không nên nhồi API, database, worker và cron vào một container vì khó scale riêng, khó theo dõi health/log và lifecycle khác nhau. Compose sẽ giúp chạy nhiều service với boundary rõ ràng.

---

## 10. Lab 4: Chứng minh writable layer thuộc instance

~~~bash
docker run --name scratch alpine:3.20 sh -c 'echo hello > /tmp/message.txt; cat /tmp/message.txt'
docker ps -a --filter name=scratch
docker logs scratch
docker start scratch
docker exec scratch cat /tmp/message.txt
docker stop scratch
docker rm scratch
docker run --name scratch-new alpine:3.20 sh -c 'test ! -e /tmp/message.txt'
docker rm scratch-new
~~~

Container mới không có file của `scratch`. File thuộc writable layer của instance cũ, không thuộc image.

---

## 11. Lab tổng hợp

~~~bash
docker pull nginx:1.27
docker create --name week1-web -p 8080:80 nginx:1.27
docker ps -a --filter name=week1-web
docker start week1-web
curl http://localhost:8080
docker logs --tail 20 week1-web
docker inspect --format '{{.State.Status}}' week1-web
docker exec -it week1-web sh
~~~

Sau `exit`:

~~~bash
docker stop week1-web
docker ps -a --filter name=week1-web
docker start week1-web
docker inspect --format '{{.State.Status}}' week1-web
docker stop week1-web
docker rm week1-web
docker image ls nginx
~~~

Cần quan sát: `create` chưa chạy process; `start` chuyển sang running; `exec` mở process phụ; `stop` đưa về exited; `rm` xóa container nhưng không xóa image.

---

## 12. Debug lỗi thường gặp

### Port đã được sử dụng

Đổi host port, không cần đổi port trong container:

~~~bash
docker run --name another-web -d -p 8082:80 nginx:1.27
~~~

### Container name đã tồn tại

~~~bash
docker ps -a --filter name=web
docker start web
# hoặc nếu là container test không cần giữ:
docker stop web
docker rm web
~~~

### Container chạy rồi thoát ngay

~~~bash
docker ps -a --latest
docker logs <container-name>
docker inspect --format '{{.State.ExitCode}}' <container-name>
~~~

Process chính có thể đã kết thúc, command sai hoặc ứng dụng khởi động lỗi.

### Container chạy nhưng không truy cập được

~~~bash
docker ps
docker port <container-name>
docker inspect --format '{{json .NetworkSettings.Ports}}' <container-name>
docker logs <container-name>
~~~

Kiểm tra đã publish port chưa, map đúng port chưa, process có listen đúng port không và container có exited không.

---

## 13. Bài tập và câu hỏi kiểm tra

1. Chạy hai Nginx từ cùng `nginx:1.27`, map lần lượt `8080:80` và `8081:80`; dùng `docker inspect` chứng minh chúng dùng cùng image.
2. Chạy `docker run --name will-exit alpine:3.20 sh -c 'echo starting; exit 7'`; dùng `ps -a`, `logs`, `inspect` để xác định trạng thái, log và exit code.
3. Vẽ flow cho `docker run --name web -d -p 8080:80 nginx:1.27`, có CLI, daemon, image, runtime, container, process và port mapping.
4. Container khác VM ở điểm nào liên quan đến kernel?
5. `docker run` khác `docker create` và `docker start` như thế nào?
6. Nếu process chính kết thúc, container thường ở trạng thái nào?
7. Vì sao file trong writable layer không xuất hiện trong container mới từ cùng image?
8. Vì sao không nên lưu dữ liệu quan trọng chỉ trong container filesystem?

Đáp án ngắn: VM thường có guest kernel riêng còn container thường chia sẻ kernel host; `run` thường kết hợp create và start; process kết thúc thì `Exited`; writable layer thuộc instance; container có thể bị xóa nên không phải storage bền vững.

---

## 14. Tiêu chí hoàn thành

- [ ] Giải thích được container không phải VM đầy đủ.
- [ ] Vẽ được flow `CLI → daemon → runtime → process`.
- [ ] Phân biệt image và container.
- [ ] Chạy được Nginx với port mapping.
- [ ] Dùng được `ps`, `logs`, `exec`, `inspect`, `top` và `stats`.
- [ ] Mô tả được `create → start → running → stop/exited → rm`.
- [ ] Điều tra được container thoát ngay bằng log và exit code.
- [ ] Giải thích được vì sao thay đổi trong container không thay đổi image.
- [ ] Biết dữ liệu quan trọng không nên chỉ nằm trong container filesystem.
- [ ] Dọn sạch container thử nghiệm.

Kiểm tra cuối buổi:

~~~bash
docker ps -a
docker image ls
~~~

Chỉ xóa tài nguyên bạn biết chắc là của mình; không dùng lệnh dọn dẹp diện rộng trên máy đang có workload khác.

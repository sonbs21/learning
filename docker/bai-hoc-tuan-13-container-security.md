# Tuần 13: Container security

## 1. Mục tiêu

Sau bài này, bạn có thể:

- Xây threat model cơ bản cho container và Compose stack.
- Giải thích rủi ro khi chạy process bằng root trong container.
- Chạy application bằng non-root UID/GID.
- Hiểu Linux capabilities và loại bỏ quyền không cần thiết.
- Dùng `no-new-privileges` để ngăn process tăng quyền.
- Chạy container với root filesystem read-only và tmpfs cho path tạm.
- Nhận biết seccomp và AppArmor ở mức cấu hình/kiểm tra cơ bản.
- Giới hạn số process, CPU và memory.
- Kiểm soát network egress ở mức Docker và hiểu giới hạn của nó.
- Không để secret trong image layer, `.env` hoặc Docker socket tùy tiện.
- Scan image và phân loại vulnerability theo risk thay vì chỉ nhìn số CVE.
- Tạo Compose hardening profile có thể kiểm chứng và rollback.

Thời lượng đề xuất: **8–12 giờ** gồm đọc lý thuyết, hardening một service và thực hiện security lab.

---

## 2. Container security là gì?

Container security là tập các biện pháp giảm khả năng và tác động của:

- Process trong container bị khai thác.
- Image/base dependency có lỗ hổng.
- Container truy cập filesystem hoặc network vượt nhu cầu.
- Secret bị lộ trong image/config/log.
- Process tiêu thụ cạn resource host.
- Container escape hoặc lạm dụng runtime socket.
- Supply chain bị thay đổi ngoài ý muốn.

Mục tiêu không phải làm container “không thể bị tấn công”. Mục tiêu là:

~~~text
Giảm khả năng xảy ra
    +
Giảm quyền nếu bị compromise
    +
Giảm phạm vi truy cập
    +
Phát hiện và phản ứng nhanh
~~~

### 2.1. Defense in depth

Một service hardened có thể kết hợp:

1. Image tối thiểu, nguồn tin cậy, được scan.
2. User non-root.
3. Capability tối thiểu.
4. `no-new-privileges`.
5. Root filesystem read-only.
6. Resource limits và PID limit.
7. Network exposure/egress hạn chế.
8. Secret injection không qua image layer.
9. Log/audit/monitoring.
10. Patch/rebuild định kỳ.

Một control có thể fail hoặc bị bypass; nhiều lớp làm giảm blast radius.

---

## 3. Root trong container

### 3.1. Root container không giống root host, nhưng vẫn nguy hiểm

UID 0 trong container thường là root trong namespace container. Container có isolation, nhưng:

- Có thể được cấp capabilities nguy hiểm.
- Có thể access bind mount nhạy cảm.
- Có thể tương tác với Docker socket nếu được mount.
- Có thể khai thác kernel/runtime vulnerability.
- Có thể sửa mọi file writable trong container.
- Có thể gây tác động lớn nếu container chạy `privileged` hoặc host namespace.

Không nên coi “container root khác host root” là lý do chạy root mặc định.

### 3.2. Kiểm tra user

~~~bash
docker run --rm alpine:3.20 id
docker inspect --format '{{.Config.User}}' <container>
docker compose exec api id
~~~

Nếu output là UID 0/root và app không cần quyền đặc biệt, đó là ứng viên cần harden.

### 3.3. User trong Dockerfile

~~~dockerfile
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 appuser
COPY --chown=10001:10001 app.jar app.jar
USER 10001:10001
ENTRYPOINT ["java", "-jar", "app.jar"]
~~~

Hoặc Compose override:

~~~yaml
services:
  api:
    user: "10001:10001"
~~~

Ưu tiên cấu hình user trong image để image có default an toàn. Compose `user` hữu ích cho override/test nhưng cần bảo đảm file ownership.

### 3.4. Numeric UID/GID

Numeric UID/GID giúp tránh phụ thuộc tên user giữa image/host, nhưng cần:

- Chown artifact đúng UID.
- Cho quyền ghi ở path cần thiết.
- Kiểm tra volume ownership.
- Không chọn UID đang có ý nghĩa đặc biệt trong image/host policy.

Không sửa lỗi permission bằng `chmod 777` mặc định.

---

## 4. Linux capabilities

Linux capabilities chia quyền root thành các nhóm nhỏ. Container không cần toàn bộ quyền root cho mọi workload.

Một số capability thường gặp:

- `NET_BIND_SERVICE`: bind port thấp hơn 1024.
- `NET_ADMIN`: thay đổi network configuration.
- `SYS_ADMIN`: quyền rất rộng, rủi ro cao.
- `SYS_PTRACE`: debug/trace process khác.
- `CHOWN`: thay ownership.
- `SETUID`/`SETGID`: đổi user/group.

Tên và behavior phụ thuộc kernel/runtime. Không cấp capability chỉ vì command “chạy được” sau khi thêm nó.

### 4.1. Xem capabilities

~~~bash
docker run --rm alpine:3.20 sh -c 'command -v capsh && capsh --print || true'
docker inspect <container>
~~~

Image tối giản có thể không có `capsh`. Có thể dùng tool debug hoặc inspect runtime config.

### 4.2. Drop all, add tối thiểu

Compose hardening:

~~~yaml
services:
  api:
    cap_drop:
      - ALL
~~~

Nếu workload thật sự cần một capability cụ thể:

~~~yaml
services:
  api:
    cap_drop:
      - ALL
    cap_add:
      - NET_BIND_SERVICE
~~~

Đừng add `SYS_ADMIN` như giải pháp chung. Capability cần được review theo use case và test regression.

### 4.3. `cap_drop: ALL` có thể làm app fail

Một số tool/process cần capability cho:

- Bind privileged port.
- Debug/profiling.
- Network setup.
- Chown/init filesystem.
- Device access.

Nếu drop all làm fail, xác định syscall/capability thật sự cần, cấp tối thiểu hoặc thay đổi thiết kế để không cần quyền đó.

---

## 5. `no-new-privileges`

`no-new-privileges` ngăn process đạt thêm privilege thông qua setuid/setgid binary hoặc cơ chế tương tự.

Compose:

~~~yaml
services:
  api:
    security_opt:
      - no-new-privileges:true
~~~

Docker CLI:

~~~bash
docker run --rm \
  --security-opt no-new-privileges:true \
  task-api:0.4.0
~~~

Đây là defense-in-depth, không làm process non-root thành “không thể bị khai thác”. Kết hợp với non-root và capability drop.

Kiểm tra config:

~~~bash
docker inspect <container> --format '{{json .HostConfig.SecurityOpt}}'
~~~

Một số image/entrypoint cũ có thể phụ thuộc setuid behavior; test trước khi rollout.

---

## 6. Read-only filesystem và tmpfs

### 6.1. Root filesystem read-only

~~~yaml
services:
  api:
    read_only: true
    tmpfs:
      - /tmp:rw,noexec,nosuid,size=64m
~~~

Process không thể ghi root filesystem. Điều này giảm khả năng attacker persist file/binary trong container và giảm accidental mutation.

### 6.2. Tìm path cần writable

Application có thể cần ghi:

- `/tmp`.
- Cache.
- Upload tạm.
- Certificate/token tạm.
- Log file.
- Framework generated metadata.

Ưu tiên:

- Log ra stdout/stderr.
- Cache ở external cache/Redis.
- Upload vào object storage/volume riêng.
- Temporary file ở tmpfs giới hạn size.
- Mount writable path nhỏ và rõ.

Không bỏ `read_only` toàn bộ chỉ vì một file cache cần ghi.

### 6.3. Debug read-only failure

~~~bash
docker compose logs api
docker compose exec api sh -c 'touch /tmp/test && touch /app/test'
~~~

Nếu image không có shell, dùng debug image hoặc kiểm tra application logs. Sau khi tìm path, cấp quyền tối thiểu:

~~~yaml
services:
  api:
    read_only: true
    tmpfs:
      - /tmp:rw,noexec,nosuid,size=64m
    volumes:
      - app-cache:/app/cache:rw

volumes:
  app-cache:
~~~

Volume writable làm tăng khả năng persistence; chỉ dùng nếu app thật sự cần và có policy/cleanup.

### 6.4. tmpfs không phải secret manager

tmpfs tránh ghi dữ liệu xuống disk thông thường, nhưng secret vẫn có thể xuất hiện trong process memory, environment, logs hoặc crash dump. Dùng secret mechanism phù hợp.

---

## 7. Seccomp và AppArmor ở mức nhận biết

### 7.1. Seccomp

Seccomp lọc system call mà process được phép gọi. Docker thường có default seccomp profile nhằm chặn một số syscall nguy hiểm.

Không nên chạy:

~~~bash
docker run --security-opt seccomp=unconfined image
~~~

trong production chỉ để “sửa lỗi chạy không được”. `unconfined` giảm mạnh một lớp bảo vệ.

Nếu app cần syscall bị chặn:

1. Xác định syscall/capability thật sự cần.
2. Kiểm tra có cách thiết kế khác không.
3. Dùng custom profile hẹp hơn nếu cần.
4. Test regression và review policy.

### 7.2. AppArmor

AppArmor áp dụng profile kiểm soát hành vi truy cập của process trên host Linux hỗ trợ. Docker có thể dùng profile mặc định hoặc profile được chỉ định.

~~~bash
docker run --security-opt apparmor=docker-default image
~~~

Tên/profile phụ thuộc host. Docker Desktop và non-Linux host có behavior khác vì chạy qua VM/backend.

### 7.3. Không đồng nhất security flag giữa môi trường

Một container chạy local không chứng minh behavior trên production host giống hệt. Ghi rõ:

- Kernel/runtime.
- Docker Engine/Desktop version.
- Seccomp/AppArmor policy.
- Rootless/rootful mode.
- Orchestrator security context.

Security control phải được verify trên target platform.

---

## 8. PID limit và process containment

### 8.1. Vì sao giới hạn PID?

Fork bomb hoặc process leak có thể tạo quá nhiều process, tiêu tốn PID/memory/CPU host. Giới hạn process giảm blast radius.

Docker CLI:

~~~bash
docker run --rm --pids-limit=128 alpine:3.20 sh -c 'ps'
~~~

Compose:

~~~yaml
services:
  api:
    pids_limit: 128
~~~

Giá trị cần dựa trên process/thread behavior. Java thread không phải lúc nào được tính như process theo cách người học dễ nhầm; runtime/kernel vẫn cần được quan sát.

### 8.2. Test limit

Không chạy fork bomb trên host/shared environment. Dùng workload test có kiểm soát, quan sát:

~~~bash
docker stats <container>
docker top <container>
docker inspect <container>
~~~

Nếu app cần nhiều process/worker, đặt limit đủ và document lý do. Limit quá thấp làm app lỗi không rõ ràng.

---

## 9. CPU và memory limit

Security và resource isolation liên quan nhau. Một service bị bug hoặc bị abuse có thể làm cạn tài nguyên host.

Compose:

~~~yaml
services:
  api:
    cpus: 1.0
    mem_limit: 512m
    mem_reservation: 256m
    pids_limit: 128
~~~

Với Java, memory limit phải chừa headroom cho heap, metaspace, thread stack, direct buffer và native allocation:

~~~yaml
environment:
  JAVA_TOOL_OPTIONS: >-
    -XX:MaxRAMPercentage=65
    -Xlog:gc*:stdout:time,uptime,level,tags
~~~

Quan sát:

~~~bash
docker compose stats
docker inspect --format 'memory={{.HostConfig.Memory}} nano_cpus={{.HostConfig.NanoCpus}} pids={{.HostConfig.PidsLimit}}' <container>
~~~

### 9.1. Limit không thay thế capacity planning

Limit giúp giới hạn blast radius, nhưng không tự chọn được capacity đúng. Cần:

- Load test.
- Working set và p95/p99 latency.
- GC/thread metrics.
- OOM/throttle alert.
- Sizing theo replica/host.

### 9.2. DoS và public endpoint

Resource limit giảm tác động nhưng không thay rate limiting/request size/connection limit. API public vẫn cần application gateway/WAF/rate limit phù hợp.

---

## 10. Network egress

### 10.1. Egress là gì?

Egress là traffic từ container ra ngoài, ví dụ:

- Gọi API bên thứ ba.
- Kết nối database/Redis khác network.
- DNS.
- Download dependency/runtime data.
- Gửi telemetry.

Container có thể cần egress cho ứng dụng, nhưng unrestricted egress làm attacker sau khi compromise dễ gọi C2, exfiltrate data hoặc scan mạng.

### 10.2. Network không có egress

Với job không cần network:

~~~yaml
services:
  migration-check:
    image: task-api:0.4.0
    network_mode: none
~~~

Với service cần network nội bộ, dùng network không publish port và chỉ nối các service cần thiết. Tuy nhiên Docker bridge/internal network không phải policy egress hoàn chỉnh trên mọi platform.

### 10.3. Kiểm soát egress thực tế

Có thể cần:

- Host firewall.
- Network policy của orchestrator/CNI.
- Proxy allowlist.
- Cloud security group/NACL.
- DNS egress policy.
- VPC/private endpoint.
- TLS certificate/hostname policy.

Không tuyên bố “internal network chặn toàn bộ Internet” nếu chưa test trên target environment.

### 10.4. Không dùng host network tùy tiện

`network_mode: host` giảm network isolation và có thể cho container thấy network stack host. Chỉ dùng khi có yêu cầu đã review; bridge/custom network thường an toàn hơn cho app thông thường.

---

## 11. Secret không nằm trong image layer

### 11.1. Các cách làm nguy hiểm

Không làm:

~~~dockerfile
COPY .env /app/.env
ENV DB_PASSWORD=real-password
ARG API_TOKEN
RUN curl -H "Authorization: Bearer ${API_TOKEN}" ...
~~~

Cũng không làm:

~~~bash
docker build --build-arg PASSWORD=real-password .
docker commit running-container image-with-secret
~~~

Secret có thể lộ qua:

- Image layer.
- `docker history`.
- Image config.
- Build logs/cache.
- Container environment/inspect.
- Shell history.
- Application logs.
- Crash dump/heap dump.

Xóa file ở layer cuối không xóa secret khỏi layer trước.

### 11.2. Cách an toàn hơn

- Dùng Compose secrets/secret manager.
- Dùng runtime injection.
- Dùng BuildKit secret mount khi build thật sự cần secret.
- Không log command chứa secret.
- Rotate secret đã lộ.
- Scan history/layer trước push.

Ví dụ Compose secret:

~~~yaml
services:
  api:
    secrets:
      - api_token

secrets:
  api_token:
    file: ./secrets/api_token.txt
~~~

Container đọc tại `/run/secrets/api_token`; app/entrypoint phải hỗ trợ đọc file và không log nội dung.

### 11.3. Docker socket

Không mount Docker socket tùy tiện:

~~~yaml
services:
  api:
    volumes:
      - /var/run/docker.sock:/var/run/docker.sock
~~~

Docker socket thường cho quyền rất mạnh để điều khiển Docker daemon, gần như mở rộng blast radius tới host. Chỉ dùng cho tool được review, với boundary/alternative phù hợp; không mount vào app business thông thường.

---

## 12. `privileged: true` và host access

`privileged: true` cấp quyền rộng, có thể thay đổi nhiều security boundary:

~~~yaml
services:
  api:
    privileged: true
~~~

Không dùng để sửa lỗi permission/network/device một cách nhanh chóng. Nếu workload cần quyền đặc biệt:

1. Xác định tài nguyên cụ thể.
2. Dùng capability/device/security option tối thiểu.
3. Review host impact.
4. Test trên host tương đương production.
5. Document lý do và owner.

Các dấu hiệu cần security review đặc biệt:

- `privileged: true`.
- `network_mode: host`.
- `pid: host`.
- `ipc: host`.
- Bind mount `/`, `/proc`, `/sys`, `/dev`.
- Mount Docker socket.
- `seccomp=unconfined`.
- Capability `SYS_ADMIN`/`NET_ADMIN` không có lý do.

---

## 13. Vulnerability scanning

### 13.1. Scan cái gì?

Scan nên bao gồm:

- Base OS packages.
- Java runtime/JDK packages.
- Application dependencies.
- Native libraries.
- Image config/user/secret indicators.
- SBOM nếu pipeline hỗ trợ.

Runtime image cần scan riêng. Build image có JDK/Maven có thể có CVE không tồn tại trong runtime image, nhưng vẫn cần bảo vệ build environment.

### 13.2. Dùng scanner

Tool phụ thuộc tổ chức. Ví dụ nếu Docker Scout có sẵn:

~~~bash
docker scout quickview task-api:0.4.0
docker scout cves task-api:0.4.0
~~~

Nếu không có, dùng scanner được phê duyệt như Trivy/Grype/registry scanner. Không coi command mẫu là bắt buộc trên mọi máy.

### 13.3. Triage CVE

Không phải mọi CVE có cùng risk. Xem:

- Severity.
- Package/runtime có thật sự hiện diện trong final image không.
- Code path có reachable không.
- Network exposure.
- Exploit availability.
- Fix version/base image update.
- Có workaround/control bổ sung không.
- Policy của tổ chức.

Không bỏ qua High/Critical chỉ vì “container không expose port”; attacker có thể vào từ network nội bộ hoặc dependency chain.

### 13.4. Fix theo thứ tự

- Update base image patch version.
- Update app dependencies.
- Loại package/tool không cần.
- Dùng multi-stage/runtime image tối thiểu.
- Rebuild, rescan và verify digest.
- Record accepted risk nếu chưa thể fix.

Không sửa bằng cách xóa scanner database hoặc suppress tất cả findings.

---

## 14. Compose hardening baseline

Mẫu theo roadmap:

~~~yaml
services:
  api:
    image: task-api:0.4.0
    user: "10001:10001"
    read_only: true
    tmpfs:
      - /tmp:rw,noexec,nosuid,size=64m
    security_opt:
      - no-new-privileges:true
    cap_drop:
      - ALL
    pids_limit: 128
    cpus: 1.0
    mem_limit: 512m
    networks:
      - backend
    secrets:
      - api_token

networks:
  backend:
    internal: true

secrets:
  api_token:
    file: ./secrets/api_token.txt
~~~

Đây là baseline cần test, không phải copy mù quáng. Một số app cần:

- Writable `/tmp`.
- Capability bind low port.
- JDK debug tools.
- Network egress.
- Certificate/agent path.
- Process count cao hơn.

Mọi exception phải có lý do, phạm vi và test.

### 14.1. Render và inspect

~~~bash
docker compose config
docker compose up -d api
docker compose ps
docker inspect <container>
~~~

Kiểm tra effective:

- User.
- SecurityOpt.
- CapDrop/CapAdd.
- ReadonlyRootfs.
- PidsLimit.
- Memory/CPU.
- Mounts/secrets.
- Networks/ports.

---

## 15. Lab 1: Non-root và no-new-privileges

Tạo service test:

~~~yaml
services:
  security-test:
    image: alpine:3.20
    user: "10001:10001"
    security_opt:
      - no-new-privileges:true
    cap_drop:
      - ALL
    command: ["sh", "-c", "id; touch /tmp/allowed; sleep 3600"]
~~~

Chạy và kiểm tra:

~~~bash
docker compose -f compose.security.yaml up -d
docker compose -f compose.security.yaml exec security-test id
docker inspect <container> --format '{{json .HostConfig.SecurityOpt}}'
docker inspect <container> --format '{{json .HostConfig.CapDrop}}'
~~~

Dùng `docker compose exec` thử ghi root path và `/tmp`, ghi nhận behavior. Dọn service sau lab.

---

## 16. Lab 2: Read-only root và tmpfs

~~~yaml
services:
  security-test:
    image: alpine:3.20
    user: "10001:10001"
    read_only: true
    tmpfs:
      - /tmp:rw,noexec,nosuid,size=16m
    command: ["sh", "-c", "touch /tmp/ok; touch /should-fail"]
~~~

Quan sát logs/exit code:

~~~bash
docker compose -f compose.readonly.yaml up
~~~

Sửa command để process không fail, sau đó kiểm tra `ReadonlyRootfs` trong inspect. Kết luận path nào writable và vì sao.

---

## 17. Lab 3: PID/resource limit

~~~yaml
services:
  limited:
    image: alpine:3.20
    pids_limit: 64
    cpus: 0.25
    mem_limit: 64m
    command: ["sh", "-c", "while true; do sleep 5; done"]
~~~

Chạy:

~~~bash
docker compose -f compose.limits.yaml up -d
docker compose -f compose.limits.yaml stats
docker top <container>
docker inspect <container>
~~~

Không chạy fork bomb. Mục tiêu là xác minh limit/observability, không phá host.

---

## 18. Lab 4: Network egress và secret

Tạo service không cần network:

~~~yaml
services:
  offline-job:
    image: alpine:3.20
    network_mode: none
    command: ["sh", "-c", "ip addr || true; sleep 10"]
~~~

Test secret mount:

~~~yaml
services:
  secret-test:
    image: alpine:3.20
    secrets:
      - demo_secret
    command: ["sh", "-c", "test -r /run/secrets/demo_secret && echo secret-mounted"]

secrets:
  demo_secret:
    file: ./secrets/demo_secret.txt
~~~

Kiểm tra không đưa secret vào Dockerfile/image history. Không in nội dung secret trong lab output.

---

## 19. Lab 5: Scan và image history

~~~bash
docker image inspect task-api:0.4.0
docker history --no-trunc task-api:0.4.0
~~~

Chạy scanner được project cho phép. Tìm:

- Base image/version.
- Package không cần.
- User root.
- Secret string/path.
- Runtime image có source/build tool không.
- Findings High/Critical và remediation.

Sau khi sửa, rebuild tag mới, scan lại và giữ digest/report của artifact đã kiểm tra.

---

## 20. Lỗi hardening thường gặp

### Non-root nhưng app không start

File JAR/config hoặc thư mục ghi không thuộc UID. Sửa ownership/path, không quay lại root ngay.

### `cap_drop: ALL` làm bind port fail

App bind port thấp hoặc cần capability. Dùng port >1024 trong container hoặc cấp capability tối thiểu sau review.

### Read-only root làm framework crash

Tìm path ghi qua log/strace/debug; cấp tmpfs/volume tối thiểu hoặc đổi cấu hình log/cache.

### Healthcheck fail vì thiếu tool

Runtime image tối giản không có curl/wget. Chọn check/tool phù hợp.

### `no-new-privileges` làm tool cũ fail

Tool phụ thuộc setuid. Thay tool/thiết kế hoặc document exception; không tắt toàn bộ security control cho app business.

### PID limit quá thấp

Java/framework/agent tạo nhiều thread/process. Đo baseline rồi tăng vừa đủ, không bỏ limit mặc định không suy nghĩ.

### Network egress bị chặn làm app lỗi

Ứng dụng cần DNS/API/telemetry nhưng policy không allow. Liệt kê dependency egress và allowlist tối thiểu.

### Scan báo CVE trong package không reachable

Triage theo runtime exposure và policy, nhưng không suppress mù quáng. Update base/image nếu có bản fix.

### Secret đã nằm trong image history

Rebuild từ base sạch và rotate secret. Xóa layer cuối không đủ.

---

## 21. Bài tập tự làm

### Bài 1: Non-root baseline

Chuyển API từ root sang UID/GID non-root, kiểm tra artifact đọc được và path ghi hoạt động. Ghi lại thay đổi ownership.

### Bài 2: Capability minimization

Chạy API với `cap_drop: ALL`. Nếu fail, xác định quyền cần thiết hoặc sửa app để không cần capability. Không add `SYS_ADMIN`.

### Bài 3: Read-only filesystem

Bật `read_only`, cấp tmpfs `/tmp`, chạy integration test và tìm mọi path cần ghi. Mount riêng với quyền tối thiểu.

### Bài 4: Resource/PID containment

Đặt memory, CPU và PID limit. Tạo workload hợp pháp trong test, quan sát stats/OOM/restart và chọn limit có headroom.

### Bài 5: Egress map

Liệt kê API cần gọi domain nào, database/network nào, DNS/telemetry nào. Tạo network/policy allowlist phù hợp và test negative path.

### Bài 6: Secret hygiene

Tìm `.env`, token, key trong context/image history; loại khỏi build, dùng secret mount/runtime injection và rotate secret giả lập nếu từng lộ.

### Bài 7: Scan remediation

Scan runtime image, chọn một finding có fix, update base/dependency, rebuild và so sánh report/digest.

---

## 22. Câu hỏi kiểm tra và đáp án gợi ý

1. Vì sao root trong container vẫn là rủi ro?
2. Non-root khác `cap_drop: ALL` thế nào?
3. `no-new-privileges` bảo vệ lớp nào?
4. Vì sao read-only root cần tmpfs/volume?
5. Seccomp và AppArmor khác nhau ở mức khái niệm thế nào?
6. PIDs limit giúp giảm rủi ro gì?
7. Vì sao CPU/memory limit là một phần security?
8. Network egress unrestricted có rủi ro gì?
9. Vì sao không mount Docker socket vào app tùy tiện?
10. Vì sao xóa secret ở Dockerfile layer cuối không đủ?
11. Scan CVE cần triage theo yếu tố nào?
12. `privileged: true` nên được xử lý ra sao?

Đáp án ngắn: root có blast radius lớn khi app bị khai thác; non-root là identity còn capabilities là quyền kernel chia nhỏ; no-new-privileges ngăn tăng quyền; read-only cần path tạm/writable riêng; seccomp lọc syscall còn AppArmor áp profile hành vi; pids limit giảm fork/process exhaustion; resource limit giảm DoS/blast radius; egress hỗ trợ exfiltration/C2; Docker socket có quyền điều khiển daemon; secret còn trong layer/history; CVE cần xem severity/reachability/exposure/fix; privileged chỉ dùng khi có lý do cụ thể và review.

---

## 23. Tiêu chí hoàn thành tuần 13

- [ ] Xây được threat model container cơ bản.
- [ ] Chạy app bằng non-root UID/GID.
- [ ] Hiểu và kiểm tra capabilities.
- [ ] Dùng `no-new-privileges:true`.
- [ ] Chạy root filesystem read-only với tmpfs phù hợp.
- [ ] Biết seccomp/AppArmor ở mức nhận biết và không dùng unconfined tùy tiện.
- [ ] Đặt pids, CPU và memory limit.
- [ ] Hiểu network egress và giới hạn của Docker internal network.
- [ ] Không mount Docker socket/host path/host network tùy tiện.
- [ ] Không đưa secret vào image layer, history hoặc committed config.
- [ ] Scan runtime image và biết triage vulnerability.
- [ ] Kiểm tra hardening bằng `inspect`, logs và negative tests.
- [ ] Ghi lại exception/capability cần thiết thay vì bỏ toàn bộ security control.

Kiểm tra cuối buổi:

~~~bash
docker compose config
docker inspect <container>
docker stats --no-stream <container>
docker top <container>
docker history --no-trunc <image>
~~~

Mục tiêu của tuần này là giảm quyền và blast radius của container bằng các control có thể kiểm chứng, không phải chỉ thêm nhiều flag cho YAML.

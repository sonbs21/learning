# Amazon ECS Service Connect: toàn tập

> **Nguồn:** tổng hợp từ các file `15-04-07-*` trong folder này. Một số chi tiết lấy từ `13 - Task definitions`, `14 - Cluster` và `05 - Architect...`.
> Chỗ nào **không có trong folder** (kiến thức chung về Envoy/gRPC, hoặc ví dụ tự đặt) được đánh dấu **🔎 Ngoài folder** hoặc **✍️ Ví dụ tự đặt**, để bạn biết cần tự kiểm chứng.
> Thuật ngữ (port name, client alias, discovery name, namespace…) giữ nguyên tiếng Anh.

---

## Mục lục

0. [Tóm tắt trong 1 phút](#0-tóm-tắt-trong-1-phút)
1. [Bài toán Service Connect giải quyết](#1-bài-toán-service-connect-giải-quyết)
2. [Kiến trúc: các mảnh ghép](#2-kiến-trúc-các-mảnh-ghép)
3. [Thuật ngữ và cách đặt tên endpoint](#3-thuật-ngữ-và-cách-đặt-tên-endpoint)
4. [Cách hoạt động: một request đi như thế nào](#4-cách-hoạt-động-một-request-đi-như-thế-nào)
5. [Hành vi của proxy: LB, outlier, retry, timeout, health check](#5-hành-vi-của-proxy)
6. [Zone-aware routing](#6-zone-aware-routing)
7. [Vòng đời cấu hình: ai thấy endpoint mới, khi nào](#7-vòng-đời-cấu-hình)
8. [Networking: port, security group, VPC](#8-networking)
9. [Triển khai từng bước](#9-triển-khai-từng-bước)
10. [Ví dụ: order-service gọi user-service (có gRPC)](#10-ví-dụ-order-service-gọi-user-service)
11. [Quan sát: metric, log, access log](#11-quan-sát-metric-log-access-log)
12. [Mã hoá TLS](#12-mã-hoá-tls)
13. [Namespace chia sẻ cross-account](#13-namespace-chia-sẻ-cross-account)
14. [Giá](#14-giá)
15. [Yêu cầu, giới hạn, không hỗ trợ](#15-yêu-cầu-giới-hạn-không-hỗ-trợ)
16. [Troubleshooting checklist](#16-troubleshooting-checklist)
17. [So sánh với Service Discovery, VPC Lattice, ALB nội bộ](#17-so-sánh)
18. [Câu hỏi tự kiểm tra / phỏng vấn](#18-câu-hỏi-tự-kiểm-tra)
    - [Đáp án chi tiết](#đáp-án-chi-tiết)
19. [Bản đồ file nguồn](#19-bản-đồ-file-nguồn)

---

## 0. Tóm tắt trong 1 phút

- **Là gì:** tính năng của ECS giúp các **ECS service gọi nhau bằng tên ngắn** (vd `http://user:8080`). Nó vừa là **service discovery** vừa là **service mesh** gọn nhẹ, và được cấu hình hoàn toàn bằng **cấu hình ECS** (task definition + service).
- **Cơ chế:** ECS tự chèn một **container proxy (sidecar, nền Envoy)** vào **mỗi task**. App gọi tên ngắn, proxy cạnh nó lo tìm task đích, cân bằng tải round-robin, retry, outlier detection, timeout, ghi metric/log và mã hoá TLS (nếu bật).
- **Danh bạ:** dùng **AWS Cloud Map namespace**, mặc định loại HTTP (không dùng Route 53 hosted zone). Mỗi service thuộc **một** namespace.
- **Thay đổi đi qua deployment:** task client chỉ "thấy" endpoint mới sau khi **client được deploy lại**. Không bị kẹt chờ TTL của DNS.
- **Không tính thêm phí.** Proxy dùng chung CPU/RAM của task (nên cấp thêm khoảng 256 CPU unit và 64 MiB). TLS thì tốn phí AWS Private CA.
- **Phạm vi:** chỉ dành cho **service ↔ service trong cùng namespace** (có thể khác cluster, khác VPC, khác account nếu namespace được chia sẻ, nhưng **cùng Region**). Traffic từ internet vẫn đi qua ELB.
- **Không chạy với** network mode `host`, Windows container, standalone task, blue/green CodeDeploy, ECS Anywhere.

---

## 1. Bài toán Service Connect giải quyết

*(Nguồn: `15-04-07-00`, `15-04-07-01-00`)*

Microservice A muốn gọi B. Các cách truyền thống đều có nhược điểm:

| Cách | Vấn đề |
|---|---|
| Hardcode IP | Task ECS đổi IP mỗi lần chết hoặc deploy |
| DNS (Cloud Map service discovery) | Client cache DNS theo **TTL**, có thể gọi vào task đã chết; app **phải tự viết retry** và tự bỏ qua backend hỏng; mode `bridge` cần bản ghi `SRV` |
| ALB nội bộ | Thêm một hop, thêm chi phí, thêm thứ phải quản lý |

Service Connect giải quyết như sau:

| ECS lo | App lo |
|---|---|
| Tạo tên tìm thấy được; cập nhật danh sách khi task bật/tắt; chạy proxy trong mỗi task; LB, retry, outlier detection; metric/log chuẩn hoá | Gọi tên DNS như bình thường. **Không cần sửa code** nếu app vốn đã kết nối bằng hostname |

Điểm khác biệt quan trọng nhất so với DNS thuần:

```
DNS thuần:        đổi đích  →  chờ TTL / cache client hết hạn (có thể nhiều giờ)
Service Connect:  đổi đích  →  deployment thay task client  →  có circuit breaker, rollback như mọi deployment
```

---

## 2. Kiến trúc: các mảnh ghép

```
                    ┌──────────────── AWS Cloud Map namespace (vd "prod") ─────────────────┐
                    │   Cloud Map service "user"  → danh sách instance (IP:port task user) │
                    │   Cloud Map service "order" → ...                                    │
                    └──────────────────────────────▲──────────────────────────────────────┘
                                                   │ ECS tự đăng ký / huỷ đăng ký task
                                                   │ (ĐỪNG tự sửa tay)
 Task order-service (client)                       │            Task user-service (client-server)
┌───────────────────────────────┐                  │           ┌──────────────────────────────────┐
│ app order                     │                  │           │ Service Connect proxy (Envoy)    │
│   gọi http://user:8080 ──┐    │                  │           │   lắng nghe containerPort 8080   │
│                          ▼    │                  │           │   (hoặc ingressPortOverride)     │
│ Service Connect proxy (Envoy) │ ═══ mạng VPC ═══════════════►│        │                         │
│  round-robin, retry, outlier  │   (TLS nếu bật)  │           │        ▼  127.0.0.1 / ::1        │
│  timeout, metric, access log  │                  │           │ app user (lắng nghe 8080)        │
└───────────────────────────────┘                              └──────────────────────────────────┘
          ▲ ECS chèn container này vào task, KHÔNG có trong task definition
```

| Mảnh | Vai trò | Ghi chú |
|---|---|---|
| **Namespace** (Cloud Map) | Nhóm logic các service nói chuyện được với nhau | Cùng Region; loại namespace (HTTP / Private DNS / Public DNS) **không ảnh hưởng** Service Connect; ECS tự tạo HTTP namespace nếu bạn đặt tên chưa tồn tại |
| **Cloud Map service** | Mỗi endpoint/discovery name là một Cloud Map service | ECS tạo và quản lý. **Tự sửa tay (register/deregister, xoá) có thể làm hỏng traffic và các deployment sau** |
| **Service Connect proxy** | Sidecar trong mỗi task | Không có trong task definition, không cấu hình trực tiếp; ECS quản lý qua cấu hình service. Nhờ vậy **một task definition dùng lại được** cho nhiều namespace hoặc cho service không dùng Service Connect |
| **Service Connect agent** (trên EC2) | Có sẵn trong ECS-optimized AMI đủ mới, chạy cạnh ECS container agent | Mã nguồn: github.com/aws/amazon-ecs-service-connect-agent |
| **Cluster default namespace** | Service mới trong cluster bật Service Connect sẽ **kế thừa** namespace này | Tuỳ chọn, giúp bớt cấu hình |

> 🔎 **Ngoài folder:** proxy là **Envoy**. Folder có nhắc gián tiếp: header `server: envoy`, `x-envoy-upstream-service-time`, access log dùng command operator của Envoy, và zone-aware routing "dùng tính năng của Envoy". App tìm ra proxy nhờ ECS **thêm dòng vào `/etc/hosts`** của container, trỏ tên endpoint (vd `user`) về một IP loopback của proxy (dải `127.255.0.x`). Kiểm chứng: ECS Exec vào container client rồi `cat /etc/hosts`. Chính vì vậy task cũ không thấy endpoint mới: file hosts được sinh lúc task khởi động.

---

## 3. Thuật ngữ và cách đặt tên endpoint

*(Nguồn: `15-04-07-01-00` mục 2, `15-04-07-01-02` mục 3)*

| Thuật ngữ | Khai ở đâu | Ý nghĩa |
|---|---|---|
| **port name** | Task definition: `portMappings[].name` | Tên cho một port mapping. **Bắt buộc** với server |
| **appProtocol** | Task definition: `portMappings[].appProtocol` | `http` / `http2` / `grpc`. Tuỳ chọn, nhưng có thì proxy xử lý ở tầng 7 và cho metric theo giao thức (vd đếm HTTP 5xx). Không đặt thì proxy coi là TCP |
| **protocol** | Task definition | Service Connect **chỉ hỗ trợ `tcp`** |
| **discovery name** | Service: `services[].discoveryName` | Tên của Cloud Map service. Tuỳ chọn, mặc định = port name. **Duy nhất trong namespace** |
| **client alias** | Service: `services[].clientAliases[]` (`port`, `dnsName`) | Port (và tên DNS) mà **client** dùng để gọi. Có thể khai nhiều alias |
| **endpoint** | Kết quả | `giao thức://tên:port`, vd `http://blog:80`, `grpc://checkout:8080`, `http://_db.production.internal:99` |
| **Service Connect service** | Một object trong `services[]` | Cấu hình của **một** endpoint |
| **ingressPortOverride** | Service: `services[].ingressPortOverride` | Đổi port mà **proxy phía server** lắng nghe |
| **client service** | Service có `serviceConnectConfiguration` nhưng **không có** `services[]` | Chỉ đi gọi. Vd frontend nhận traffic từ ALB |
| **client-server service** | Có namespace + **ít nhất một** endpoint trong `services[]` | Vừa gọi được vừa được gọi. Vd backend, đa số microservice |

**Tên DNS của endpoint lấy theo thứ tự ưu tiên:**

```
clientAliases[].dnsName   →   discoveryName   →   portMappings[].name
   (cao nhất)                                        (mặc định)
```

**Port của endpoint:** `clientAliases[].port`. Đây là port **client gọi tới** (trên proxy phía client), **không phải** port server lắng nghe.

**Bẫy hay gặp:**
- Tên Cloud Map service phải duy nhất trong namespace, nên một task definition chỉ có **một** cấu hình Service Connect **không có discoveryName** trong mỗi namespace. Muốn chạy hai service từ cùng task definition trong một namespace thì phải đặt `discoveryName` khác nhau.
- Mọi endpoint và mọi discovery name phải **duy nhất** trong namespace (kể cả namespace chia sẻ, bất kể account nào tạo).

---

## 4. Cách hoạt động: một request đi như thế nào

*(Nguồn: `15-04-07-01-00` mục 1, `15-04-07-01-01` mục 2.2, `15-04-07-01-07`)*

### 4.1 Control plane: khi bạn deploy

```
1. Bạn create/update service với serviceConnectConfiguration
2. ECS tạo deployment mới
3. ECS tạo/cập nhật Cloud Map service cho từng endpoint (serviceConnectResources: discoveryName + discoveryArn)
4. Mỗi task MỚI khởi động: ECS chèn container proxy, cấu hình proxy biết mọi endpoint trong namespace tại thời điểm đó
5. Task server healthy → ECS đăng ký instance vào Cloud Map → proxy của các client nhận danh sách task mới
6. Task dừng → ECS huỷ đăng ký khỏi Cloud Map
```

> Có hai thứ khác nhau cần phân biệt:
> - **Danh sách endpoint** (có những service nào, tên gì): mỗi task client chốt lúc khởi động. Muốn thấy endpoint mới phải deploy lại client.
> - **Danh sách task phía sau một endpoint** (scale out/in, task thay thế): cập nhật động. Scale out thì kết nối mới được cân bằng sang mọi task. Server deploy phiên bản mới thì kết nối mới đi tới task phiên bản mới.

### 4.2 Data plane: ví dụ WordPress → MySQL trong docs

```
container WordPress (172.31.16.1)
   mysql --host=mysql            ← tên ngắn + port mặc định MySQL
        │
        ▼
proxy trong CÙNG task WordPress  ← chọn task MySQL theo round-robin + outlier detection
        │  (qua mạng VPC, tới containerPort)
        ▼
proxy trong task MySQL (172.31.16.2)
        │  (localhost)
        ▼
MySQL server
   → cả 2 proxy báo metric về console ECS và CloudWatch
```

### 4.3 Traffic nào đi qua proxy

| Traffic từ app | Qua proxy? |
|---|---|
| Tới **tên endpoint** trong cùng namespace | ✅ |
| Tới IP khác trong VPC | ❌ |
| Tới endpoint dịch vụ AWS (S3, DynamoDB…) | ❌ |
| Ra internet | ❌ |
| **Vào** từ ALB (mode `awsvpc`) | ✅ mặc định đi qua proxy. Muốn bỏ qua thì dùng `ingressPortOverride` |
| Vào từ app ngoài namespace / ngoài ECS | Gọi **IP task + containerPort** vẫn đi qua proxy, nhưng **không phân giải được tên** endpoint |

---

## 5. Hành vi của proxy

*(Nguồn: `15-04-07-01-01` mục 2.2, `15-04-07-01-02` mục 4)*

| Tính năng | Giá trị mặc định | Ghi chú |
|---|---|---|
| **Load balancing** | Round-robin giữa các task của endpoint | Proxy phía client chọn |
| **Outlier detection** (health check thụ động) | ≥ **5** kết nối lỗi trong **30 giây** thì tránh task đó **30–300 giây** | Không cần cấu hình |
| **Retry** | **2** lần; lần thử lại **tránh host** vừa lỗi | Giúp chịu được lỗi nhất thời |
| **Timeout** | **15 giây** | Cập nhật được |
| `idleTimeout` | HTTP/HTTP2/gRPC: **5 phút**; TCP: **1 giờ**; `0` = tắt | Kết nối nhàn rỗi bị đóng sau thời gian này |
| `perRequestTimeout` | **15 giây**; chỉ cho HTTP/HTTP2/gRPC; `0` = tắt | Thời gian chờ upstream trả lời **đầy đủ** mỗi request |

Nếu `idleTimeout` **nhỏ hơn** `perRequestTimeout` thì kết nối đóng theo `idleTimeout`. Đặt timeout trong JSON:

```json
"services": [{
  "portName": "api",
  "clientAliases": [{ "port": 8080 }],
  "timeout": { "idleTimeout": 300, "perRequestTimeout": 60 }
}]
```

> ✍️ Cú pháp khối `timeout` lấy theo API `ServiceConnectService` (🔎 ngoài folder). Folder chỉ nêu tên hai tham số và giá trị mặc định.

**Hệ quả thực tế:**
- Request chạy lâu hơn 15 giây (export báo cáo, upload lớn…) sẽ bị proxy cắt **dù app server vẫn đang xử lý**. Phải tăng `perRequestTimeout`.
- Retry nghĩa là một request có thể tới server **hơn một lần**. API không idempotent (vd tạo đơn hàng) nên dùng idempotency key. 🔎 *Đây là suy luận từ hành vi retry, docs không nói.*
- Kết nối nhàn rỗi lâu (DB pool, WebSocket, gRPC stream) sẽ bị cắt theo `idleTimeout`, nên app phải có reconnect/keepalive.

### Health check ban đầu (khi task mới lên)

Điều kiện: **essential container phải có `healthCheck`** trong task definition.

| Trạng thái task mới | Service Connect làm gì |
|---|---|
| `HEALTHY` | Nhận traffic ngay |
| `UNKNOWN` | Chờ một timeout tính từ cấu hình health check (tối đa **8 phút**), rồi vẫn cho nhận traffic |
| `UNHEALTHY` | ECS có thể thay task; nếu không còn task healthy thì deployment có thể **rollback** (tuỳ cấu hình) |

```
Task mới lên:     health check chủ động của container (HEALTHY / UNKNOWN / UNHEALTHY)
Đang chạy:        health check thụ động = outlier detection
```

Nên luôn khai `healthCheck` cho container. Không có thì task mới sẽ ở `UNKNOWN` và có thể nhận traffic khi app chưa sẵn sàng.

---

## 6. Zone-aware routing

*(Nguồn: `15-04-07-01-05`)*

- **Bật mặc định**, không cần sửa code. Traffic được ưu tiên giữ trong **cùng AZ** với client: giảm phí truyền dữ liệu giữa AZ và giảm độ trễ. Khi endpoint trải đều các AZ, **hơn 80%** traffic ở lại cùng AZ.
- Thuật toán (Envoy zone-aware routing):
  1. Proxy biết mọi endpoint đích và AZ của chúng.
  2. Tính % traffic giữ trong cùng AZ theo tỉ lệ endpoint giữa AZ client và AZ server.
  3. Phần còn lại chia sang AZ khác theo **capacity còn dư** (> 0), tính lại khi endpoint scale.
  4. Nếu số endpoint đích **< 2 × số AZ** (3 AZ thì cần ≥ 6) thì **tắt** zone-aware và dùng round-robin. Đủ lại thì tự bật. Endpoint cùng AZ hỏng thì chuyển sang AZ khác.
- **Service đã có từ trước:** phải **deploy lại một lần** cả client lẫn server thì mới có hiệu lực.
- **EC2:** cần ECS-optimized AMI ≥ `20260323`. Dùng được với mọi launch type và với namespace chia sẻ.

**Kiểm tra trên EC2 + Docker:**

```bash
aws ssm start-session --target <instance-id>
sudo docker exec -it $(sudo docker ps --filter "name=ecs-service-connect" -q | head -1) /bin/sh
curl --unix-socket /tmp/envoy_admin.sock http://unix/stats | grep "lb_zone"
# cluster.my-service.lb_zone_routing_cross_zone: 0   ← 0 kéo dài = mọi traffic cùng AZ
# cluster.my-service.lb_zone_cluster_too_small: 0    ← >0 lúc mới deploy là bình thường
```

Fargate hoặc EC2 dùng `containerd` thì xem bằng **VPC Flow Logs** (trường `az-id`). Theo dõi chi phí trước/sau bằng **Cost Explorer**.

> Hệ quả thiết kế: muốn hưởng zone-aware thì service đích cần **≥ 2 task mỗi AZ**. Service nhỏ (vd 2 task / 3 AZ) sẽ luôn chạy round-robin.

---

## 7. Vòng đời cấu hình

*(Nguồn: `15-04-07-01-01` mục 0)*

**Quy tắc vàng:** task **hiện có** không phân giải được endpoint mới. **Chỉ task mới**, có Service Connect, cùng namespace, khởi động **sau** deployment đó mới thấy.

```
Server thêm/đổi endpoint (bất cứ lúc nào)
       │
       ▼
Client CHƯA deploy lại  →  vẫn chạy với cấu hình cũ (không thấy endpoint mới)
Client deploy lại       →  task mới thấy endpoint mới
⇒ Người vận hành CLIENT quyết định thời điểm thay đổi có hiệu lực
```

### Thứ tự triển khai khuyến nghị

**A. Hệ thống mới (một CloudFormation stack):**
- Tạo **backend (client-server) trước**, frontend/public **sau cùng**.
- Thêm `DependsOn` ở frontend cho **từng** backend nó gọi, để CloudFormation không tạo song song. Tránh giai đoạn frontend đã public mà backend chưa có.

**B. Chuyển hệ thống đang chạy sang Service Connect:**
1. Thêm cấu hình **client-server** vào **backend trước**, dùng **cùng tên DNS / `clientAlias`** mà frontend đang gọi. Deployment mới nên rollback được nếu lỗi.
2. Backend ổn rồi mới thêm cấu hình client (hoặc client-server) vào **frontend**. Chỉ task mới của frontend dùng proxy, và cũng rollback được.

> Với service discovery dựa trên DNS, client chỉ đổi theo khi **cache DNS hết hạn**, thường mất **nhiều giờ**. Với Service Connect, mọi thay đổi đi qua deployment nên có circuit breaker và rollback.

---

## 8. Networking

*(Nguồn: `15-04-07-01-01` mục 1 và 3, `15-04-07-00` mục 4)*

### 8.1 Ba con số port, đừng nhầm

```
App client ──► proxy client : clientAliases[].port         (port client GỌI, vd http://user:80)
proxy client ──► proxy server : containerPort               (hoặc ingressPortOverride nếu có)
proxy server ──► app server   : containerPort qua localhost
```

| Bạn đặt | Đổi cái gì |
|---|---|
| `clientAliases[].port` | Port endpoint mà **client** gọi. **Không** đổi port proxy server lắng nghe |
| `ingressPortOverride` | Port **proxy phía server** lắng nghe. Nhớ mở security group cho port này |
| `containerPort` (task def) | Port app lắng nghe; cũng là port proxy server lắng nghe mặc định |

### 8.2 Mở mạng

| Thành phần | Cần cho phép |
|---|---|
| Security group phía **server** | Inbound `containerPort` (hoặc `ingressPortOverride`) **từ subnet/SG của client** |
| Route table, network ACL | Các port `containerPort` / `ingressPortOverride` |
| Khác VPC | Như trên cho **cả hai** VPC + VPC peering (hoặc kết nối tương đương) + route |
| Mode `bridge` | Security group mở **dải port động phía trên** (ephemeral), gán cho **mọi EC2 instance** trong cluster |

Khác VPC mà cùng namespace thì **tên phân giải được ngay** (không cần DNS của VPC), nhưng **kết nối** chỉ thông khi peering, route và NACL cho phép.

### 8.3 Network mode

| | `awsvpc` | `bridge` | `host` |
|---|---|---|---|
| Service Connect | ✅ | ✅ | ❌ |
| TLS | ✅ | ❌ | ❌ |
| Tham số container `hostname` | — | ❌ không hỗ trợ | — |
| Health check HTTPS từ ALB khi bật TLS | ✅ | ❌ (HTTP vẫn được) | — |

### 8.4 IPv4 / IPv6

Ở mode `awsvpc`, proxy chuyển traffic vào app qua `127.0.0.1` (IPv4-only, dualstack) hoặc `::1` (IPv6-only). **Khuyến nghị:** cho app lắng nghe **cả hai** (vd bind `0.0.0.0` và `::`, hoặc `[::]` dual-stack) để đổi sang IPv6-only không bị gián đoạn.

> Bẫy hay gặp: app chỉ bind `127.0.0.1` thì chạy được với proxy, nhưng ALB / health check từ ngoài sẽ không vào được. App chỉ bind IP của ENI thì proxy (gọi localhost) không vào được. Bind `0.0.0.0` là an toàn nhất. 🔎 *Suy luận từ cơ chế, không có trong docs.*

---

## 9. Triển khai từng bước

*(Nguồn: `15-04-07-01-02`, `15-04-07-01-07`)*

### 9.1 Điều kiện tiên quyết

| Hạ tầng | Yêu cầu |
|---|---|
| Fargate | Linux platform version ≥ `1.4.0` |
| EC2 | ECS agent ≥ `1.67.2`; AMI AL2023 ≥ `20230428` hoặc AL2 ≥ `2.0.20221115`; instance role có `ecs:Poll` trên `task-set` (có sẵn trong `AmazonEC2ContainerServiceforEC2Role`) |
| Task definition | **Phải** có **task-level memory**. Nếu đặt container memory limit thì task memory phải **lớn hơn tổng** (phần dư dành cho proxy). **Không dùng `links`** |
| IAM | Task execution role (để ghi log proxy); infrastructure role nếu bật TLS |
| Log group | **Tự tạo trước** (`/ecs/...-proxy`), vì task execution role mặc định không có quyền tạo log group |

### 9.2 Tài nguyên cho proxy

| Trường hợp | Cộng thêm vào task |
|---|---|
| Mặc định | **+256 CPU unit**, **+≥ 64 MiB** |
| Tải đỉnh > 500 req/s mỗi task | **+512 CPU unit** |
| > 100 Service Connect service **hoặc** > 2000 task trong namespace | **+128 MiB** ở **mọi** task definition trong namespace |

> Fargate tối thiểu 512 MiB. App đang dùng sát giới hạn task mà bật Service Connect thì có thể bị OOM hoặc chậm, vì proxy ăn chung tài nguyên.

### 9.3 Bảng "đặt cái gì ở đâu"

| | Task definition | Service | Cluster |
|---|---|---|---|
| **Client** | Không cần gì | `serviceConnectConfiguration` (enabled + namespace): **bắt buộc** | Default namespace: tuỳ chọn |
| **Client-server** | `portMappings[].name`: **bắt buộc**; `appProtocol`: tuỳ chọn | `serviceConnectConfiguration` + `services[]`: **bắt buộc** | — |

### 9.4 CLI từ đầu tới cuối (theo tutorial trong docs)

**Bước 1: Cluster + namespace mặc định**

```bash
aws ecs create-cluster --cluster-name tutorial \
  --service-connect-defaults namespace=service-connect
# status: PROVISIONING, attachments type "sc" ATTACHING → ECS đang tạo HTTP namespace

aws ecs describe-clusters --clusters tutorial --include ATTACHMENTS   # xem trạng thái namespace
aws servicediscovery get-namespace --id ns-EXAMPLE                    # Type: HTTP
```

**Bước 2: Task definition (server)**

```json
{
  "family": "service-connect-nginx",
  "executionRoleArn": "arn:aws:iam::123456789012:role/ecsTaskExecutionRole",
  "taskRoleArn": "arn:aws:iam::123456789012:role/ecsTaskRole",
  "networkMode": "awsvpc",
  "containerDefinitions": [{
    "name": "webserver",
    "image": "public.ecr.aws/docker/library/nginx:latest",
    "cpu": 100,
    "portMappings": [{
      "name": "nginx",
      "containerPort": 80,
      "protocol": "tcp",
      "appProtocol": "http"
    }],
    "essential": true
  }],
  "cpu": "256",
  "memory": "512"
}
```

```bash
aws ecs register-task-definition --cli-input-json file://service-connect-nginx.json
```

**Bước 3: Service (client-server)**

```json
{
  "cluster": "tutorial",
  "serviceName": "service-connect-nginx-service",
  "taskDefinition": "service-connect-nginx",
  "desiredCount": 1,
  "launchType": "FARGATE",
  "platformVersion": "LATEST",
  "enableExecuteCommand": true,
  "networkConfiguration": {
    "awsvpcConfiguration": {
      "subnets": ["subnet-EXAMPLE"],
      "securityGroups": ["sg-EXAMPLE"],
      "assignPublicIp": "ENABLED"
    }
  },
  "serviceConnectConfiguration": {
    "enabled": true,
    "services": [{
      "portName": "nginx",
      "clientAliases": [{ "port": 80 }]
    }],
    "logConfiguration": {
      "logDriver": "awslogs",
      "options": {
        "awslogs-group": "/ecs/service-connect-proxy",
        "awslogs-region": "us-west-2",
        "awslogs-stream-prefix": "service-connect-proxy"
      }
    }
  }
}
```

```bash
aws ecs create-service --cluster tutorial --cli-input-json file://service-connect-nginx-service.json
```

- Không có `namespace` vì cluster đã có namespace mặc định.
- Client trong namespace gọi tới bằng `http://nginx:80/`.
- Output có `serviceConnectResources: [{discoveryName: "nginx", discoveryArn: ...}]`, nghĩa là ECS đã tạo Cloud Map service.

**Bước 4: Kiểm tra**

```bash
curl -v 10.0.19.50:80/      # từ EC2 cùng VPC, gọi IP private của task
# < server: envoy                       ← đi qua proxy
# < x-envoy-upstream-service-time: 0
```

### 9.5 Console

`Create service` → mục **Service Connect** → **Use Service Connect** → chọn **Client side only** hoặc **Client and server** → chọn namespace → thêm port (Discovery / DNS / Port để ghi đè) → (tuỳ chọn) Log collection, Access log configuration, Turn on traffic encryption.

### 9.6 IaC

- **CloudFormation:** dùng `DependsOn` để tạo backend trước frontend (mục 7).
- **Terraform** (theo `14-01-01`): `aws_service_discovery_http_namespace` + `service_connect_defaults` trong `aws_ecs_cluster`; trong `aws_ecs_service` dùng block `service_connect_configuration`.
- **Khi bật TLS:** xoá ECS service **trước**, rồi mới xoá KMS key, IAM role, Private CA. Ngược lại service có thể **kẹt ở DRAINING**.
- **Xoá cluster không xoá namespace.** Phải tự xoá namespace trong Cloud Map.

---

## 10. Ví dụ: order-service gọi user-service

> ✍️ **Ví dụ tự đặt**, dựa trên quy tắc ở các mục trên, để trả lời câu hỏi "từ prod service gọi tới userservice thế nào" và "gRPC Golang dùng gì".

**Mục tiêu:** trong namespace `prod`, `order-service` gọi `user-service` qua gRPC port 50051 và REST port 8080.

**user-service: task definition**

```json
"portMappings": [
  { "name": "user-grpc", "containerPort": 50051, "protocol": "tcp", "appProtocol": "grpc" },
  { "name": "user-http", "containerPort": 8080,  "protocol": "tcp", "appProtocol": "http" }
],
"healthCheck": {
  "command": ["CMD-SHELL", "wget -qO- http://localhost:8080/health || exit 1"],
  "interval": 10, "timeout": 5, "retries": 3, "startPeriod": 20
}
```

**user-service: service (client-server)**

```json
"serviceConnectConfiguration": {
  "enabled": true,
  "namespace": "prod",
  "services": [
    { "portName": "user-grpc", "discoveryName": "user-grpc",
      "clientAliases": [{ "port": 50051, "dnsName": "user" }] },
    { "portName": "user-http", "discoveryName": "user-http",
      "clientAliases": [{ "port": 8080, "dnsName": "user-api" }] }
  ],
  "logConfiguration": { "logDriver": "awslogs", "options": { "...": "..." } }
}
```

**order-service: service**, chỉ cần bật Service Connect. Thêm `services[]` nếu bản thân nó cũng cần được gọi:

```json
"serviceConnectConfiguration": { "enabled": true, "namespace": "prod" }
```

**Code Go trong order-service:**

```go
// Proxy Service Connect lo TLS (nếu bật) giữa các task, nên app gọi plaintext tới proxy cục bộ.
conn, err := grpc.NewClient("user:50051",
    grpc.WithTransportCredentials(insecure.NewCredentials()))
if err != nil { return err }
client := userpb.NewUserServiceClient(conn)
```

REST: `http.Get("http://user-api:8080/users/42")`.

**Security group:** SG của user-service mở inbound **50051 và 8080** từ SG của order-service.

**Thứ tự deploy:** user-service trước, order-service sau. Nếu order-service đã chạy từ trước thì phải **force new deployment** để task mới thấy `user` / `user-api`.

### Ghi chú riêng cho gRPC

| Điểm | Chi tiết |
|---|---|
| Khai `appProtocol: "grpc"` | Proxy hiểu HTTP/2 + gRPC, có metric và access log theo gRPC (`%GRPC_STATUS()%`), áp dụng được `perRequestTimeout` |
| 🔎 Không khai `appProtocol` | Proxy coi là **TCP**: cân bằng tải **theo kết nối**. gRPC giữ một kết nối HTTP/2 lâu dài nên mọi request dồn vào **một** task đích. Có `grpc` thì Envoy cân bằng **theo request** |
| TLS ở app | Không cần. Bật TLS của Service Connect thì proxy mã hoá giữa các task; app vẫn nói plaintext với proxy cục bộ |
| Stream dài | Lưu ý `idleTimeout` (5 phút cho gRPC) và `perRequestTimeout` (15 giây). Với server-streaming / bidi-streaming lâu thì nên tăng hoặc đặt `0` cho `perRequestTimeout` |
| Gọi từ ngoài vào | Service Connect không dành cho traffic ngoài. Dùng **ALB (HTTP/2, hỗ trợ gRPC)**, xem `05-06-02` |

---

## 11. Quan sát: metric, log, access log

*(Nguồn: `15-04-07-01-04-00`, `15-04-07-01-04-01`, `15-04-07-01-07` mục 4, `15-04-07-01-03-00`)*

### 11.1 Metric CloudWatch

Proxy đẩy metric (HTTP, HTTP2, gRPC, TCP) vào namespace **`AWS/ECS`** với các dimension:

| Dimension | Nhìn từ phía |
|---|---|
| `DiscoveryName`; `DiscoveryName, ServiceName, ClusterName` | Phía **server** (traffic vào endpoint) |
| `TargetDiscoveryName`; `TargetDiscoveryName, ServiceName, ClusterName` | Phía **client** (traffic đi tới endpoint đích) |

Khai `appProtocol` thì có thêm metric theo giao thức, vd đếm HTTP 5xx. Biểu đồ cũng hiện trên console ECS.

Với namespace chia sẻ, metric **không xem chéo account** được mặc định. Target metric chỉ đẩy về account sở hữu **client service**. Muốn xem chéo phải bật **CloudWatch cross-account observability**.

### 11.2 Log của proxy

`serviceConnectConfiguration.logConfiguration` gửi stdout/stderr của proxy tới CloudWatch Logs. Docs **khuyến nghị bật**. Nhớ tạo log group trước.

### 11.3 Access log (mặc định tắt)

Ghi metadata **từng request**: method, path, mã phản hồi, thời gian, upstream host… Để bật cần **cả hai** object:

```json
"serviceConnectConfiguration": {
  "enabled": true,
  "namespace": "myapp.namespace",
  "services": [ ... ],
  "logConfiguration": {
    "logDriver": "awslogs",
    "options": { "awslogs-group": "my-envoy-log-group", "awslogs-region": "us-west-2", "awslogs-stream-prefix": "myapp-envoy-logs" }
  },
  "accessLogConfiguration": { "format": "TEXT", "includeQueryParameters": "ENABLED" }
}
```

| Điểm | Chi tiết |
|---|---|
| `format` | `TEXT` hoặc `JSON` |
| `includeQueryParameters` | Mặc định **loại bỏ** query string (vì có thể chứa token). Phải đặt `"ENABLED"` mới ghi |
| Tách khỏi app log | Access log và app log cùng ra `/dev/stdout`. Muốn tách thì dùng `awsfirelens` + Fluent Bit/Fluentd |
| Gộp chung | Dùng `awslogs` để gửi cả hai về cùng chỗ trong CloudWatch |
| Fargate | Platform ≥ `1.4.0` |

Các trường mặc định theo giao thức:

```
HTTP   = start_time, method, path, protocol, response_code, bytes_received/sent, duration,
         upstream_service_time, forwarded_for, user_agent, request_id, authority, upstream_host
HTTP2  = HTTP + stream_id
gRPC   = HTTP + stream_id + grpc_status
TCP    = start_time, downstream_remote_address/port, bytes_received/sent, duration,
         connection_termination_details, connection_id
```

> Mẹo debug: `upstream_host` cho biết request rơi vào task nào. `duration` so với `upstream_service_time` cho biết thời gian mất ở proxy hay ở app.

---

## 12. Mã hoá TLS

*(Nguồn: `15-04-07-01-06-00`, `-01`, `-02`)*

### 12.1 Bản chất

```
App client ─plain─► proxy client ══ TLS 1.3 ══► proxy server ─plain─► App server
                    (khởi tạo TLS)               (kết thúc TLS)
```

- App **không bao giờ thấy** TLS, nên không cần sửa code.
- ECS **tự tạo, xoay vòng, phân phối** certificate từ **AWS Private CA** mà bạn trỏ tới.
- Chỉ **TLS 1.3** (không hỗ trợ 1.0–1.2). Cipher: `TLS_AES_128_GCM_SHA256`, `TLS_AES_256_GCM_SHA384`, `TLS_CHACHA20_POLY1305_SHA256`. Khoá `256-bit ECDSA` hoặc `2048-bit RSA`.
- Chỉ traffic **đi qua agent/proxy** mới được mã hoá.
- **Chỉ `awsvpc`.** `bridge` không hỗ trợ TLS.
- Certificate cấp được cho cả **TLS Web Server** và **TLS Web Client Authentication**.

### 12.2 Cần chuẩn bị

| Thứ | Ghi chú |
|---|---|
| **Infrastructure IAM role** | ECS dùng role này để tạo secret và xin certificate |
| **AWS Private CA** | Root hoặc subordinate. **Bắt buộc tag `AmazonECSManaged : true`**. CA on-prem thì tạo subordinate CA trong AWS Private CA để chung chuỗi tin cậy |
| **KMS key** | AWS owned key, key có sẵn, hoặc key mới. **Chỉ symmetric**. Key policy cần `kms:Encrypt`, `Decrypt`, `GenerateDataKey`, `GenerateDataKeyPair` cho role |

### 12.3 Bật TLS

Console: **Namespaces** → namespace → service → **Update Service** → **Turn on traffic encryption** → chọn TLS role, Signer CA, KMS key.

CLI: thêm `tls` vào từng phần tử `services[]`:

```json
"tls": {
  "issuerCertificateAuthority": { "awsPcaAuthorityArn": "arn:aws:acm-pca:...:certificate-authority/..." },
  "kmsKey": "arn:aws:kms:...:key/...",
  "roleArn": "arn:aws:iam::...:role/ecsInfrastructureRole"
}
```

### 12.4 Certificate và xoay vòng

| Điểm | Chi tiết |
|---|---|
| Chế độ CA | **Short-lived** (≤ 7 ngày): **khuyến nghị**, rẻ hơn nhiều. General purpose cũng được |
| Xoay vòng | Mặc định **mỗi 5 ngày** |
| Thu hồi certificate | **Không hỗ trợ**. Dựa vào thời hạn ngắn |
| Private key | Lưu trong **Secrets Manager**, tên bắt đầu `ecs-sc!` |
| Tự đổi lịch xoay vòng | Ngắn hơn thì tốn tiền hơn. **Dài hơn 7 ngày thì app giao tiếp thất bại.** Xoá secret thì xoay vòng thất bại |
| Giám sát | Sự kiện `RotationFailed` trong CloudTrail, đặt CloudWatch Alarm |
| ⚠️ **Không** thêm replica Region cho secret | ECS sẽ không xoá được secret. Gỡ bằng `aws secretsmanager remove-regions-from-replication` |

**Rủi ro bảo mật:** role nào có quyền đọc Secrets Manager cũng đọc được private key TLS. Nên chặn bằng policy:

```json
{ "Effect": "Deny", "Action": "secretsmanager:GetSecretValue",
  "Resource": "arn:aws:secretsmanager:*:*:secret:ecs-sc!*" }
```

### 12.5 TLS + ALB health check

- ALB: TLS listener với security policy TLS 1.3 (vd `ELBSecurityPolicy-TLS13-1-2-2021-06`); target group **HTTPS**; **health check port = container port** của Service Connect service (**không phải 443**).
- Service: `awsvpc`, load balancer trỏ đúng container port, **không đặt `ingressPortOverride`**.

### 12.6 Kiểm tra TLS đã bật

Gọi tên DNS sẽ **không** thấy certificate. Phải gọi thẳng **IP:port** của task:

```bash
aws ecs execute-command --cluster <c> --task <t> --container <ct> --interactive --command "/bin/sh"
openssl s_client -connect 10.0.147.43:6379 < /dev/null 2>/dev/null | openssl x509 -noout -text
# Signature Algorithm: ecdsa-with-SHA256, Validity ~7 ngày, SAN: DNS:redis.yelb-cftc
```

Lấy IP task trong console **Cloud Map** → namespace → service details.

---

## 13. Namespace chia sẻ cross-account

*(Nguồn: `15-04-07-01-03-00`, `-01`, `-02`)*

### 13.1 Thiết lập

```
Chủ namespace (account A)                     Bên dùng (account B)
1. Cloud Map → Create namespace
2. AWS RAM → Create resource share
   - Resource type: Cloud Map Namespaces
   - Managed permission: AWSRAMPermissionCloudMapECSFullPermission   ← BẮT BUỘC
   - Principals: account ID / OU ID   ───────►  3. RAM → Shared with me → Accept, ghi lại ARN
                                                4. ECS service: namespace = ARN đầy đủ (KHÔNG dùng tên/ID)
```

### 13.2 Quy tắc

| Quy tắc | |
|---|---|
| Cùng Region; AWS RAM phải có ở Region đó | |
| Dùng **ARN** namespace, không dùng ID hay tên | |
| Mọi loại namespace đều được (HTTP / Private DNS / Public DNS) | |
| Khác VPC thì cần VPC peering (hoặc tương đương) | |
| Chỉ chủ namespace quản lý resource share và sửa namespace | Bên dùng chỉ tạo/quản lý service trong đó |
| Discovery name duy nhất trong namespace **bất kể account** | |
| TLS: Private CA gắn theo namespace | Mất quyền namespace thì mất quyền CA |
| Metric không xem chéo account mặc định | Cần CloudWatch cross-account observability |
| Private DNS namespace + truy vấn DNS | Chủ: `route53 create-vpc-association-authorization`; bên dùng: `route53 associate-vpc-with-hosted-zone` |

### 13.3 Khi quyền bị thu hồi / thiếu quyền

| Triệu chứng | Action Cloud Map thiếu | Khắc phục |
|---|---|---|
| `CreateCluster`/`UpdateCluster` lỗi "no resource-based policy allows" | `GetNamespace` | Đã accept share chưa? ARN đúng chưa? |
| `CreateService`/`UpdateService` lỗi | `CreateService`, `GetNamespace`, `GetOperation`/`GetService` | Như trên |
| `ListServicesByNamespace` lỗi | `GetNamespace` | Gọi bằng **ARN** |
| Xoá service **kẹt `DRAINING`** | `DeleteService` | Chủ namespace **chia sẻ lại** |
| Task không start (stopped task error) | `GetOperation`, `RegisterInstance` | Cấp quyền, kiểm tra namespace truy cập được |
| Task kẹt `DEACTIVATING`/`DEPROVISIONING` | `DeregisterInstance`, `GetOperation` | Chủ namespace khôi phục quyền. Xem `statusReason` trong task attachment (`DescribeTasks`) |

---

## 14. Giá

| Khoản | Phí |
|---|---|
| Service Connect | **Miễn phí** |
| Cloud Map dùng bởi Service Connect | **Miễn phí** |
| Proxy | Không tính thêm, nhưng **ăn chung vCPU/RAM** của task mà bạn đã trả tiền |
| TLS | Phí AWS Private CA + phí mỗi certificate |

```
phí cert/tháng ≈ số service bật TLS × giá 1 cert × 6   (xoay vòng mỗi 5 ngày ≈ 6 cert/tháng)
               + phí của chính Private CA
```

Zone-aware routing giúp **giảm** phí truyền dữ liệu giữa AZ.

---

## 15. Yêu cầu, giới hạn, không hỗ trợ

**Bắt buộc / giới hạn:**
- Mỗi service thuộc **đúng một** namespace; namespace phải **cùng Region**.
- Chỉ dùng được với task **do service tạo** (không cho standalone task).
- Endpoint và discovery name **duy nhất** trong namespace.
- Muốn thấy endpoint mới thì **phải deploy lại** client.
- Task definition **phải** có task memory; không dùng `links`; `protocol` chỉ `tcp`.
- Mode `bridge`: không dùng được `hostname`, không có TLS.
- Xoá cluster **không** xoá namespace.
- **Đừng** tự sửa Cloud Map service do Service Connect tạo.

**Không hỗ trợ:**

| |
|---|
| Windows container |
| HTTP 1.0 |
| Standalone task |
| Blue/green bằng CodeDeploy và deployment controller `EXTERNAL` |
| Container instance `External` (ECS Anywhere) |
| PPv2 (Proxy Protocol v2) |
| FIPS mode |
| Network mode `host` |

**Ai không gọi được bằng tên endpoint** (phải dùng cách khác, vd ALB, service discovery, VPC Lattice, hoặc gọi IP:port):
- Task ở namespace khác
- Task không bật Service Connect
- App ngoài ECS (Lambda, EC2 thường, on-prem…)

---

## 16. Troubleshooting checklist

| Triệu chứng | Nguyên nhân thường gặp | Kiểm tra / sửa |
|---|---|---|
| `could not resolve host: user` | Client chưa deploy lại sau khi server thêm endpoint; client không bật Service Connect; khác namespace | `force-new-deployment` cho client; kiểm tra `serviceConnectConfiguration.namespace`; 🔎 `cat /etc/hosts` trong container client |
| Connection refused / timeout tới endpoint | SG server chưa mở `containerPort` / `ingressPortOverride` từ client; NACL/route; khác VPC chưa peering | Mở SG theo mục 8.2 |
| Request dài bị cắt ở ~15 giây (504/upstream timeout) | `perRequestTimeout` mặc định 15 giây | Tăng `timeout.perRequestTimeout` |
| Kết nối nhàn rỗi bị đứt | `idleTimeout` (5 phút HTTP, 1 giờ TCP) | Tăng `idleTimeout` hoặc dùng keepalive/reconnect ở app |
| Task mới nhận traffic khi app chưa sẵn sàng | Không có `healthCheck` cho essential container | Thêm `healthCheck` + `startPeriod` |
| App bị OOM / CPU throttling sau khi bật Service Connect | Proxy ăn chung tài nguyên | Cộng thêm 256 CPU / 64–128 MiB (mục 9.2) |
| Deploy fail "task memory required" | Chưa đặt task-level memory | Đặt `memory` ở mức task, lớn hơn tổng container limit |
| gRPC dồn hết vào một task | 🔎 Thiếu `appProtocol: grpc` nên proxy cân bằng theo kết nối TCP | Khai `appProtocol: "grpc"` |
| `curl` IP task thấy `server: envoy` | **Bình thường**, nghĩa là đang đi qua proxy | — |
| Trùng tên / tạo service lỗi | Hai service cùng task definition cùng namespace mà không có `discoveryName` | Đặt `discoveryName` khác nhau |
| Xoá stack kẹt `DRAINING` (TLS) | Đã xoá CA/KMS/role trước service | Xoá service trước (`DependsOn` đúng chiều) |
| Cross-account kẹt `DRAINING` / `DEACTIVATING` | Quyền RAM bị thu hồi | Chủ namespace chia sẻ lại |
| Zone-aware không hoạt động | Số endpoint < 2 × số AZ; chưa deploy lại; AMI cũ | Xem `lb_zone_cluster_too_small`; deploy lại client + server |
| Không thấy access log | Thiếu `logConfiguration` hoặc `accessLogConfiguration` | Bật **cả hai** |

**Công cụ debug:**
- Service events / stopped reason: `aws ecs describe-services`, `describe-tasks`
- Log proxy: log group trong `serviceConnectConfiguration.logConfiguration`
- Access log: `upstream_host`, `response_code`, `duration`
- Envoy admin stats (EC2 + Docker): `curl --unix-socket /tmp/envoy_admin.sock http://unix/stats`
- Cloud Map console: xem instance (IP) đang đăng ký của từng endpoint
- ECS Exec vào task để `curl` / `openssl`

---

## 17. So sánh

| | **Service Connect** | **Service discovery (Cloud Map DNS)** | **VPC Lattice** | **ALB nội bộ** |
|---|---|---|---|---|
| Cơ chế | Proxy sidecar trong mỗi task | DNS → IP task, gọi thẳng | Dịch vụ mạng được quản lý, task là IP target | Load balancer ở giữa |
| AWS đánh giá | **Khuyến nghị** | Lựa chọn khác | Lựa chọn khác | Lựa chọn khác |
| Retry / outlier / LB | Có sẵn | **App tự viết** | Có | Health check của LB |
| Đổi đích | Qua deployment | Chờ TTL DNS | Tự đăng ký target | LB cập nhật pool |
| Độ trễ | Thêm 2 hop proxy (cục bộ) | **Thấp nhất** | Thêm hop | Thêm hop |
| Phạm vi | Cùng namespace (đa cluster, đa VPC, đa account qua RAM; cùng Region) | Trong VPC | Đa VPC, đa account, đa loại compute (EC2, Lambda, IP, ALB) | Trong VPC |
| Network mode | bridge, awsvpc (không host) | awsvpc (bridge/host cần SRV) | Cả ba | Mọi mode |
| Phí | Không thêm | Theo Cloud Map | Có phí | Có phí |
| Metric/log chuẩn | Có | Không | Có | Có |
| TLS tự động | Có (Private CA) | Không | (xem docs Lattice) | Có (ACM) |
| Client ngoài ECS gọi vào bằng tên | ❌ | ✅ | ✅ | ✅ |

**Chọn nhanh:**
```
Traffic từ internet                         → ALB/NLB
ECS ↔ ECS cùng Region, muốn ít việc nhất    → Service Connect
Cần độ trễ thấp nhất, chấp nhận tự retry    → Service discovery
Đa account/VPC, có Lambda/EC2 thường tham gia → VPC Lattice
Client ngoài ECS cần gọi service ECS        → ALB nội bộ / service discovery / Lattice
```

---

## 18. Câu hỏi tự kiểm tra

**Cơ bản**
1. Service Connect gồm những thành phần nào? Proxy được khai ở đâu? *(Không khai ở đâu cả, ECS tự chèn vào mỗi task.)*
2. Client service và client-server service khác nhau thế nào? Frontend nhận traffic từ ALB nên là loại nào?
3. Tên DNS của endpoint được lấy theo thứ tự ưu tiên nào?
4. `clientAliases.port`, `containerPort`, `ingressPortOverride`: mỗi cái ảnh hưởng tới chặng nào?
5. Vì sao Service Connect không cần Route 53 hosted zone? Namespace ECS tự tạo thuộc loại gì?

**Vận hành**
6. Server vừa thêm endpoint mới, client đang chạy có gọi được không? Phải làm gì?
7. Đang có frontend gọi backend bằng DNS cũ, chuyển sang Service Connect thì làm theo thứ tự nào để không downtime?
8. Request upload mất 40 giây bị lỗi sau khi bật Service Connect. Vì sao, sửa thế nào?
9. Bật Service Connect xong app bị OOM. Vì sao?
10. Retry 2 lần ảnh hưởng gì tới API tạo đơn hàng?
11. Task mới chưa có health check thì Service Connect xử lý ra sao? Tối đa chờ bao lâu?
12. Outlier detection hoạt động với ngưỡng nào?

**Networking / bảo mật**
13. Security group của server cần mở port nào, từ đâu?
14. Hai service ở hai VPC cùng namespace: tên có phân giải được không? Kết nối có thông không?
15. Vì sao mode `host` không dùng được Service Connect? Mode `bridge` thiếu gì?
16. TLS của Service Connect mã hoá đoạn nào? App có phải xử lý certificate không?
17. Vì sao nên chặn `GetSecretValue` trên `ecs-sc!*`?
18. Đổi lịch xoay vòng certificate lên 10 ngày thì chuyện gì xảy ra?
19. Kiểm tra TLS đã bật bằng cách nào? Vì sao gọi tên DNS không thấy certificate?

**Nâng cao**
20. Zone-aware routing cần tối thiểu bao nhiêu task đích với 3 AZ? Dưới ngưỡng thì sao?
21. Chia sẻ namespace cross-account cần managed permission nào? Khi quyền bị thu hồi, service và task đang chạy bị gì?
22. Metric Service Connect nằm ở account nào khi dùng namespace chia sẻ?
23. Khi nào **không** nên dùng Service Connect mà nên chọn service discovery hoặc VPC Lattice?
24. gRPC qua Service Connect: vì sao nên khai `appProtocol: grpc`?
25. Xoá stack CloudFormation có Service Connect TLS bị kẹt `DRAINING`. Nguyên nhân?

---

### Đáp án chi tiết

> Mỗi đáp án ghi file nguồn. Phần 🔎 là kiến thức ngoài folder hoặc suy luận, cần tự kiểm chứng.

---

#### Phần A: Cơ bản

**Câu 1. Service Connect gồm những thành phần nào? Proxy được khai ở đâu?**

Có 4 thành phần chính:

| Thành phần | Vai trò | Ai tạo |
|---|---|---|
| **Cloud Map namespace** | "Danh bạ" chung. Các service trong cùng namespace tìm thấy nhau | Bạn tạo, hoặc ECS tự tạo khi đặt namespace mặc định cho cluster (loại HTTP) |
| **Cloud Map service** | Mỗi endpoint (discovery name) là một Cloud Map service, chứa danh sách IP:port các task | **ECS tự tạo và tự quản lý**. Đừng sửa tay |
| **Service Connect proxy** | Container sidecar trong **mỗi task**. Lo phân giải tên, cân bằng tải, retry, outlier detection, timeout, metric, log, TLS | **ECS tự chèn** khi task khởi động |
| **Cấu hình ECS** | `portMappings[].name` / `appProtocol` trong task definition, `serviceConnectConfiguration` trong service, namespace mặc định trong cluster | Bạn viết |

Trên EC2 còn có **Service Connect agent**, có sẵn trong ECS-optimized AMI đủ mới (AL2023 ≥ `20230428`, AL2 ≥ `2.0.20221115`).

**Proxy không được khai ở đâu cả.** Nó không nằm trong task definition và bạn không cấu hình trực tiếp được. ECS chèn nó vào mỗi task mới, dựa trên `serviceConnectConfiguration` của service. Lợi ích của thiết kế này: **cùng một task definition** dùng lại được cho nhiều service, nhiều namespace, kể cả service không bật Service Connect. Bạn chỉ điều khiển proxy gián tiếp qua cấu hình service (`logConfiguration`, `timeout`, `tls`, `accessLogConfiguration`…).

*(Nguồn: `15-04-07-01-01` mục 2, `15-04-07-01-02` mục 1)*

---

**Câu 2. Client service và client-server service khác nhau thế nào? Frontend nhận traffic từ ALB nên là loại nào?**

| | Client service | Client-server service |
|---|---|---|
| Cần namespace | Có | Có |
| Có `services[]` (endpoint) | **Không** | **Ít nhất một** |
| Gọi được endpoint khác trong namespace | Có | Có |
| Được service khác gọi tới bằng tên | **Không** | **Có**, proxy lắng nghe trên port của endpoint |
| Task definition cần thay đổi | Không | Phải đặt `portMappings[].name` |
| Ví dụ | Frontend, reverse proxy, load balancer nhận traffic từ ngoài (vd từ ELB) | Backend, middleware, tầng nghiệp vụ, đa số microservice |

**Frontend nhận traffic từ ALB:**
- Nếu frontend **chỉ** nhận traffic từ ALB và đi gọi backend thì dùng **client service** là đủ. Traffic ALB vào thẳng task theo target group, không cần endpoint Service Connect.
- Nếu service khác trong namespace cũng cần gọi tới frontend bằng tên thì phải là **client-server**.

Lời khuyên của docs: khi mới bắt đầu, hãy làm **client-server service trước** (backend), vì nó tạo endpoint cho các client dùng.

*(Nguồn: `15-04-07-01-00` mục 2, `15-04-07-01-01` mục 0)*

---

**Câu 3. Tên DNS của endpoint được lấy theo thứ tự ưu tiên nào?**

```
1. clientAliases[].dnsName   (nếu có)        ← ưu tiên cao nhất
2. discoveryName             (nếu có)
3. portMappings[].name       (task definition) ← mặc định
```

Ví dụ:

| Cấu hình | Tên client gọi |
|---|---|
| `portName: "nginx"`, không có gì khác | `nginx` |
| `portName: "nginx"`, `discoveryName: "web"` | `web` |
| `portName: "nginx"`, `discoveryName: "web"`, `clientAliases: [{port: 80, dnsName: "frontend"}]` | `frontend` |

Phân biệt hai tên:
- **discoveryName** là tên của **Cloud Map service** (danh bạ). Phải **duy nhất** trong namespace.
- **clientAliases.dnsName** là tên mà **client gõ trong code**. Một service có thể khai **nhiều** client alias, vd vừa `user` vừa `user.prod.local` để chuyển dần từ DNS cũ sang.

Port của endpoint là `clientAliases[].port`. Không khai thì lấy `containerPort` từ task definition.

*(Nguồn: `15-04-07-01-00` mục 2, `15-04-07-01-02` mục 3)*

---

**Câu 4. `clientAliases.port`, `containerPort`, `ingressPortOverride`: mỗi cái ảnh hưởng tới chặng nào?**

Một request đi qua 3 chặng:

```
[App client] ──(1)──► [Proxy client] ──(2)──► [Proxy server] ──(3)──► [App server]
               clientAliases.port      containerPort           containerPort
                                       hoặc ingressPortOverride  (qua localhost)
```

| Tham số | Chặng | Ý nghĩa |
|---|---|---|
| `clientAliases[].port` | (1) | Port **app client gõ** trong URL, vd `http://user:80`. Proxy phía client lắng nghe port này. **Không** đổi port server lắng nghe |
| `containerPort` | (2) và (3) | Port **app server** lắng nghe. Mặc định proxy server cũng lắng nghe port này, nên proxy client gọi sang bằng port này |
| `ingressPortOverride` | (2) | Đổi port mà **proxy server** lắng nghe. Proxy client sẽ gọi sang port mới này |

Ví dụ: app server chạy port 8080, client muốn gọi `http://user:80`.
- Đặt `containerPort: 8080`, `clientAliases: [{port: 80}]`.
- Security group của server phải mở **8080** (không phải 80), vì traffic giữa hai task đi port 8080.

Khi nào dùng `ingressPortOverride`: khi muốn traffic Service Connect vào một port riêng, để traffic khác (vd từ ALB) đi thẳng vào app **không qua proxy**. Nhớ mở security group cho port này. Khi bật TLS kèm ALB health check thì docs dặn **không đặt** `ingressPortOverride`.

*(Nguồn: `15-04-07-01-01` mục 1 và 3, `15-04-07-01-06-00` mục 1)*

---

**Câu 5. Vì sao Service Connect không cần Route 53 hosted zone? Namespace ECS tự tạo thuộc loại gì?**

- Service Connect **không phân giải tên qua DNS server của VPC**. Proxy lấy danh sách endpoint và task từ **Cloud Map qua API**. App trong task tìm ra proxy cục bộ bằng cơ chế ECS cài sẵn trong task. 🔎 *Cơ chế đó là các dòng ECS ghi vào `/etc/hosts`, trỏ tên endpoint về IP loopback của proxy.* Vì vậy không cần hosted zone, và không phụ thuộc cấu hình DNS của VPC.
- Docs nói rõ: Service Connect **không dùng cũng không tạo** hosted zone trong Route 53.
- Namespace ECS tự tạo (khi bạn đặt tên namespace mặc định cho cluster mà tên đó chưa có) là loại **HTTP** (`"Type": "HTTP"` trong output `get-namespace`). HTTP namespace chỉ cho discovery qua API, không có DNS.
- **Loại namespace không ảnh hưởng Service Connect.** Dùng Private DNS hay Public DNS namespace có sẵn cũng được.
- Hệ quả: hai service ở hai VPC khác nhau, cùng namespace, **phân giải được tên của nhau mà không cần cấu hình DNS gì** (nhưng vẫn cần mạng thông, xem câu 14).

*(Nguồn: `15-04-07-01-02` mục 1, `15-04-07-01-07` bước 1, `15-04-07-01-00` mục 2)*

---

#### Phần B: Vận hành

**Câu 6. Server vừa thêm endpoint mới, client đang chạy có gọi được không? Phải làm gì?**

**Không gọi được.** Quy tắc của docs: task **hiện có không thể** phân giải và kết nối tới endpoint mới. Chỉ những task **mới**, có Service Connect, **cùng namespace**, khởi động **sau** deployment đó mới thấy endpoint.

Lý do: cấu hình proxy (danh sách endpoint có trong namespace) được chốt **lúc task khởi động**. 🔎 *Các dòng trong `/etc/hosts` cũng được ghi lúc đó.*

Cách làm: **deploy lại client service**:

```bash
aws ecs update-service --cluster <cluster> --service <client-service> --force-new-deployment
```

Cần phân biệt hai loại thay đổi:

| Thay đổi | Client cần deploy lại? |
|---|---|
| Server **thêm endpoint mới**, đổi tên hoặc port endpoint | **Có** |
| Server **scale out/in**, task thay thế, deploy phiên bản mới (endpoint giữ nguyên) | **Không**. Proxy client tự cập nhật danh sách task; kết nối mới được cân bằng sang các task mới |

Đây là thiết kế có chủ ý: **người vận hành client quyết định khi nào** thay đổi có hiệu lực, và thay đổi đó đi qua deployment nên có circuit breaker và rollback.

*(Nguồn: `15-04-07-01-01` mục 0 và 3)*

---

**Câu 7. Frontend đang gọi backend bằng DNS cũ, chuyển sang Service Connect thì làm theo thứ tự nào để không downtime?**

Làm theo đúng thứ tự docs đưa ra:

**Bước 1: Backend trước.**
- Thêm cấu hình Service Connect dạng **client-server** vào backend.
- Đặt `clientAliases[].dnsName` **trùng tên DNS mà frontend đang gọi**, vd frontend đang gọi `api.internal` thì alias là `api.internal`.
- Việc này tạo deployment mới cho backend. Lỗi thì rollback bằng circuit breaker, console hoặc CLI.
- Lúc này frontend (chưa có Service Connect) **vẫn gọi qua DNS cũ** như bình thường, vì task backend vẫn nhận kết nối qua IP:port, chỉ là đi qua proxy.

**Bước 2: Theo dõi backend.** Hiệu năng và hành vi ổn thì làm tiếp.

**Bước 3: Frontend sau.**
- Thêm cấu hình **client** (hoặc client-server) vào frontend.
- Task mới của frontend có proxy. Cùng tên `api.internal` nhưng giờ được phân giải **tới proxy**, không qua DNS nữa. **Không cần sửa code.**
- Lỗi thì rollback frontend.

Vì sao không downtime:
- Mỗi bước là một deployment rolling riêng, rollback được.
- Giữ nguyên tên nên code không đổi.
- Task cũ của frontend vẫn chạy với DNS cũ cho tới khi bị thay.

So sánh docs đưa ra: nếu chỉ đổi bản ghi DNS, client phải chờ **cache DNS hết hạn**, thường mất **nhiều giờ**, và không rollback gọn được.

Hệ thống **mới hoàn toàn** trong CloudFormation thì tạo backend trước, frontend public sau cùng, và thêm `DependsOn` ở frontend cho **từng** backend nó gọi.

*(Nguồn: `15-04-07-01-01` mục 0)*

---

**Câu 8. Request upload mất 40 giây bị lỗi sau khi bật Service Connect. Vì sao, sửa thế nào?**

**Nguyên nhân:** proxy có `perRequestTimeout` mặc định **15 giây** cho HTTP/HTTP2/gRPC, nghĩa là thời gian chờ upstream trả lời **đầy đủ** cho một request. Request 40 giây bị proxy **cắt ở giây thứ 15** và trả lỗi cho client. 🔎 *Envoy thường trả `504 Gateway Timeout` hoặc "upstream request timeout"*, trong khi app server **vẫn đang xử lý**. Trước khi bật Service Connect thì không có lớp timeout này nên không lỗi.

**Sửa:** tăng timeout trong `services[]` của **service đích** (server):

```json
"services": [{
  "portName": "upload",
  "clientAliases": [{ "port": 8080 }],
  "timeout": { "perRequestTimeout": 120, "idleTimeout": 300 }
}]
```

Lưu ý:
- `perRequestTimeout: 0` là **tắt** hẳn. Chỉ nên tắt khi thật cần, vd stream dài.
- Nếu `idleTimeout` **nhỏ hơn** `perRequestTimeout` thì kết nối đóng theo `idleTimeout`. Phải đặt `idleTimeout` ≥ `perRequestTimeout`.
- `perRequestTimeout` chỉ áp dụng khi `appProtocol` là `http`/`http2`/`grpc`. Không khai `appProtocol` thì proxy coi là TCP và chỉ có `idleTimeout` (mặc định 1 giờ).
- Cập nhật cấu hình service sẽ tạo deployment mới.
- 🔎 *Kiểm tra lại timeout của các lớp khác (ALB idle timeout, timeout HTTP client trong app), vì lớp nào nhỏ nhất sẽ cắt trước.*

Thiết kế dài hạn: việc chạy lâu (upload hoặc xử lý file lớn) nên chuyển sang bất đồng bộ (upload thẳng S3 bằng presigned URL, xử lý qua queue) thay vì giữ request 40 giây.

*(Nguồn: `15-04-07-01-01` mục 2.2)*

---

**Câu 9. Bật Service Connect xong app bị OOM. Vì sao?**

**Vì proxy chạy bên trong task và dùng chung CPU/RAM của task.** Docs nói rõ: "tài nguyên của task được **chia sẻ** giữa workload của khách hàng và Service Connect agent". Task trước đây vừa đủ cho app, giờ phải nuôi thêm proxy nên app hết RAM.

Khuyến nghị cộng thêm vào task:

| Trường hợp | Cộng thêm |
|---|---|
| Mặc định | **+256 CPU unit**, **+≥ 64 MiB** |
| Tải đỉnh > 500 request/giây mỗi task | **+512 CPU unit** |
| Namespace có > 100 Service Connect service **hoặc** > 2000 task | **+128 MiB**, ở **mọi** task definition trong namespace (vì proxy phải giữ danh sách endpoint/task lớn hơn) |

Một nguyên nhân khác liên quan đến **container memory limit**:
- Service Connect **bắt buộc** đặt **task-level memory**.
- Nếu đặt memory limit cho từng container thì task memory phải **lớn hơn tổng** các container limit. Phần dư là phần proxy (và các container không đặt limit) dùng.
- Đặt task memory = tổng container limit thì proxy không còn chỗ.

Cách sửa: tăng `memory`/`cpu` ở mức task. Trên Fargate thì chọn tổ hợp CPU/RAM lớn hơn. Theo dõi `MemoryUtilization` của task, 🔎 *hoặc Container Insights để xem RAM theo từng container.*

*(Nguồn: `15-04-07-01-01` mục 2.1 và 3, `15-04-07-01-00` mục 5)*

---

**Câu 10. Retry 2 lần ảnh hưởng gì tới API tạo đơn hàng?**

Docs: proxy **thử lại 2 lần** các kết nối thất bại, và lần thử lại **tránh host** vừa lỗi. Mục đích là để lỗi nhất thời không làm request thất bại.

🔎 **Hệ quả (suy luận, docs không nói):**
- Request có thể tới server **hơn một lần**. Trường hợp nguy hiểm: task A **đã nhận và xử lý** request "tạo đơn hàng" (đã ghi DB), nhưng response bị mất hoặc lỗi trên đường về. Proxy coi là thất bại và gửi lại sang task B, nên B **tạo thêm một đơn trùng**.
- Với API **idempotent** (GET, PUT ghi đè, DELETE) thì không sao. Với **POST tạo mới**, trừ tiền, gửi email thì có rủi ro trùng.

**Cách phòng:**
1. **Idempotency key:** client sinh `Idempotency-Key` (UUID) cho mỗi thao tác. Server lưu key đã xử lý (unique constraint trong DB hoặc cache) và trả lại kết quả cũ nếu gặp key trùng.
2. **Unique constraint nghiệp vụ** trong DB, vd `(user_id, cart_id)` cho đơn hàng.
3. Thiết kế thao tác dạng "upsert" khi có thể.

Lưu ý thêm: retry ở proxy **cộng dồn** với retry trong code app. App retry 3 lần × proxy 2 lần retry có thể thành rất nhiều request khi hệ thống đang quá tải. Nên giảm hoặc tắt retry trùng ở app, hoặc dùng backoff.

*(Nguồn: `15-04-07-01-01` mục 2.2; phần hệ quả là suy luận)*

---

**Câu 11. Task mới chưa có health check thì Service Connect xử lý ra sao? Tối đa chờ bao lâu?**

Service Connect cố đảm bảo task **healthy trước khi** định tuyến traffic tới, nhưng điều kiện là essential container **phải có `healthCheck`** trong task definition.

Ba trường hợp:

| Trạng thái task mới | Hành vi |
|---|---|
| `HEALTHY` | Nhận traffic **ngay** |
| `UNKNOWN` | Service Connect tính một timeout dựa trên cấu hình health check của các essential container (interval, retries, startPeriod…), **tối đa 8 phút**. Hết thời gian đó thì task **vẫn được nhận traffic dù vẫn `UNKNOWN`** |
| `UNHEALTHY` | ECS có thể thay task bằng task mới. Không còn task healthy thì deployment có thể **rollback** (tuỳ cấu hình circuit breaker) |

**Không khai `healthCheck`:** task không bao giờ có trạng thái `HEALTHY`/`UNHEALTHY` rõ ràng. Nó ở `UNKNOWN`, nên Service Connect **không có căn cứ** để chờ app sẵn sàng. Hệ quả: task có thể nhận traffic khi app còn đang khởi động (đang warm-up JVM, chưa kết nối DB…) và client nhận lỗi.

Sau khi task đã chạy, Service Connect dùng **health check thụ động** (outlier detection, câu 12) cho traffic đang diễn ra.

**Khuyến nghị:** luôn khai `healthCheck` có `startPeriod` hợp lý, vd app Spring Boot khởi động mất 40 giây thì đặt `startPeriod: 60`.

*(Nguồn: `15-04-07-01-02` mục 4)*

---

**Câu 12. Outlier detection hoạt động với ngưỡng nào?**

- Đây là **health check thụ động**: proxy không chủ động ping, mà nhìn **kết quả các kết nối thật** đã gửi đi.
- **Ngưỡng:** một task đích có **≥ 5 kết nối thất bại trong 30 giây gần nhất**, thì proxy **tránh** task đó trong **30 tới 300 giây**.
- Kết hợp với round-robin: proxy chọn task theo vòng, **bỏ qua** các task đang bị đánh dấu outlier.
- Kết hợp với retry: request lỗi được thử lại sang host **khác**.
- Mỗi proxy client **tự** đánh giá theo dữ liệu nó thấy. 🔎 *Proxy của client A loại một task không có nghĩa client B cũng loại.*
- 🔎 *Khoảng 30–300 giây: Envoy tăng thời gian loại theo số lần một host bị đánh dấu outlier, nên host lỗi lặp lại bị loại lâu hơn.*

So với health check chủ động của ALB: outlier detection **phản ứng theo traffic thật**, không cần endpoint `/health`. Nhưng một task không nhận traffic thì cũng không bị phát hiện.

*(Nguồn: `15-04-07-01-01` mục 2.2)*

---

#### Phần C: Networking / bảo mật

**Câu 13. Security group của server cần mở port nào, từ đâu?**

- Mở **inbound `containerPort`** (port trong `portMappings` của task definition) **từ subnet (hoặc security group) nơi client chạy**. Vì proxy server lắng nghe trên `containerPort`, và proxy client gọi sang port đó.
- Nếu đặt `ingressPortOverride` thì mở **port đó** thay vì (hoặc thêm vào) `containerPort`.
- **Không** cần mở `clientAliases.port`. Port đó chỉ tồn tại **bên trong task client** (giữa app và proxy cục bộ).
- **Route table và network ACL** cũng phải cho phép `containerPort` / `ingressPortOverride` (chú ý NACL là stateless, phải mở cả chiều về cho ephemeral port).
- Mode **`bridge`**: tạo security group mở **dải port động phía trên** (ephemeral range) và gán cho **mọi EC2 instance** trong cluster.

Ví dụ: user-service có `containerPort: 50051`, order-service có SG `sg-order`. Inbound rule trên `sg-user` là `TCP 50051 from sg-order`.

Với `awsvpc`, mỗi service có security group riêng, nên giới hạn được **đúng** service nào được gọi service nào.

*(Nguồn: `15-04-07-01-01` mục 1, `15-04-07-00` mục 2)*

---

**Câu 14. Hai service ở hai VPC cùng namespace: tên có phân giải được không? Kết nối có thông không?**

- **Tên: phân giải được.** Vì cùng namespace, proxy lấy danh sách endpoint từ Cloud Map, **không cần** cấu hình DNS của VPC.
- **Kết nối: chưa chắc.** Proxy client phải tới được IP của task server trong VPC kia. Chỉ thông khi:
  - Có **VPC peering** (🔎 *hoặc Transit Gateway*) giữa hai VPC.
  - **Route table** của các subnet có route sang CIDR của VPC kia.
  - **Network ACL** cho phép `containerPort` / `ingressPortOverride`.
  - **Security group** phía server cho phép inbound từ CIDR hoặc subnet của client. 🔎 *Tham chiếu SG chéo VPC chỉ được khi có peering cùng Region.*
- Yêu cầu route, NACL, SG áp dụng cho **cả hai** VPC.
- Vẫn bắt buộc **cùng Region**. Khác account thì namespace phải được chia sẻ qua AWS RAM.

Đây là bẫy hay gặp: thấy tên phân giải được tưởng mọi thứ đã ổn, nhưng request bị timeout vì thiếu route hoặc peering.

*(Nguồn: `15-04-07-01-01` mục 1, `15-04-07-01-03-00`)*

---

**Câu 15. Vì sao mode `host` không dùng được Service Connect? Mode `bridge` thiếu gì?**

**`host`: không hỗ trợ** (theo bảng tương thích của docs). 🔎 *Lý do (suy luận): ở mode `host`, container dùng thẳng network stack của EC2 host, không có network namespace riêng cho task. Proxy cần một không gian mạng riêng của task để "chen" vào giữa app và mạng, nhận traffic trên `containerPort` rồi chuyển tới app qua localhost. Ở `host`, mọi task trên cùng instance chia chung port và localhost nên không làm được.*

**`bridge`: hỗ trợ**, nhưng thiếu và vướng mấy điểm:

| Điểm | `bridge` |
|---|---|
| **TLS** | **Không hỗ trợ**, chỉ `awsvpc` |
| Health check HTTPS từ ALB khi bật TLS | Không. Health check HTTP vẫn được |
| Tham số container `hostname` | Không hỗ trợ khi dùng Service Connect |
| Security group | Không có SG riêng mỗi task; phải mở **dải port động** trên SG của **mọi EC2 instance** nên kiểm soát thô hơn |

**`awsvpc`** là mode đầy đủ nhất: TLS, SG riêng mỗi service, ALB traffic đi qua proxy (hoặc bỏ qua bằng `ingressPortOverride`), hỗ trợ IPv6. Fargate luôn dùng `awsvpc`.

*(Nguồn: `15-04-07-00` mục 4, `15-04-07-01-01` mục 1 và 3, `15-04-07-01-06-00` mục 1)*

---

**Câu 16. TLS của Service Connect mã hoá đoạn nào? App có phải xử lý certificate không?**

```
App client ─plaintext─► Proxy client ══ TLS 1.3 ══► Proxy server ─plaintext─► App server
           (trong task, localhost)    (qua mạng VPC)         (trong task, localhost)
```

- **Mã hoá đoạn giữa hai proxy**, tức traffic **đi qua mạng giữa các task**. Proxy client **khởi tạo** TLS, proxy server **kết thúc** TLS.
- Đoạn app ↔ proxy cục bộ là **plaintext**, nhưng nó nằm **trong task** (localhost) nên không ra mạng.
- Docs: "**chỉ** traffic vào và ra đi qua agent mới được mã hoá". App gọi thẳng IP khác hay dịch vụ AWS thì không qua proxy nên **không** được mã hoá bởi Service Connect.
- **App không phải làm gì với certificate.** Docs: "code ứng dụng **không bao giờ thấy** các tương tác TLS". ECS tự **tạo, xoay vòng, phân phối** certificate từ AWS Private CA.
- Thông số: chỉ **TLS 1.3**; cipher `TLS_AES_128_GCM_SHA256`, `TLS_AES_256_GCM_SHA384`, `TLS_CHACHA20_POLY1305_SHA256`; khoá ECDSA 256-bit hoặc RSA 2048-bit. Certificate có cả *TLS Web Server* và *TLS Web Client Authentication*. 🔎 *Điều đó gợi ý hai proxy xác thực lẫn nhau (mTLS), nhưng docs không nói rõ.*
- Cần chuẩn bị: infrastructure IAM role, AWS Private CA (tag `AmazonECSManaged: true`), KMS key symmetric, mode `awsvpc`.

*(Nguồn: `15-04-07-01-06-00` mục 0, `15-04-07-01-06-02` mục 0)*

---

**Câu 17. Vì sao nên chặn `GetSecretValue` trên `ecs-sc!*`?**

- Khi bật TLS, Service Connect dùng infrastructure role để **tạo secret trong Secrets Manager**, chứa **private key** của certificate TLS. Tên secret có tiền tố `ecs-sc!`.
- **Rủi ro (docs cảnh báo):** **bất kỳ IAM role nào** có quyền đọc Secrets Manager rộng (vd `secretsmanager:GetSecretValue` trên `*`, thường gặp ở role dev, CI/CD, role app đọc secret DB) đều **đọc được private key** này.
- Có private key thì kẻ xấu có thể giả làm service (giải mã hoặc mạo danh trong chuỗi tin cậy), làm mất ý nghĩa của mã hoá.
- **Cách giảm rủi ro:**
  1. Giới hạn quyền Secrets Manager theo nguyên tắc quyền tối thiểu.
  2. Rà soát IAM role định kỳ.
  3. Thêm **Deny tường minh**. Deny thắng mọi Allow:

```json
{
  "Effect": "Deny",
  "Action": "secretsmanager:GetSecretValue",
  "Resource": "arn:aws:secretsmanager:*:*:secret:ecs-sc!*"
}
```

🔎 *Policy này gắn vào các role **không phải** của ECS (role người dùng, role app). Đừng gắn vào infrastructure role mà Service Connect dùng, kẻo làm hỏng việc xoay vòng certificate. Có thể đặt ở SCP mức Organization kèm điều kiện loại trừ role ECS.*

*(Nguồn: `15-04-07-01-06-00` mục 3)*

---

**Câu 18. Đổi lịch xoay vòng certificate lên 10 ngày thì chuyện gì xảy ra?**

**Ứng dụng giao tiếp thất bại.** Docs: "giao tiếp của ứng dụng **thất bại** nếu tần suất xoay vòng **vượt bảy ngày**".

Lý do:
- AWS Private CA chế độ **short-lived** (chế độ khuyến nghị) cấp certificate có hiệu lực **tối đa 7 ngày**. Ví dụ trong docs: `Not Before: Jan 23` đến `Not After: Jan 30`.
- Mặc định ECS xoay vòng **mỗi 5 ngày**, để certificate mới thay trước khi certificate cũ hết hạn.
- Đặt 10 ngày thì từ ngày 7 tới ngày 10 certificate **đã hết hạn** nhưng chưa được thay. Bắt tay TLS giữa các proxy thất bại và **mọi request Service Connect lỗi**.

Các thay đổi khác cũng có hệ quả:

| Thay đổi | Hệ quả |
|---|---|
| Xoay vòng **ngắn hơn** 5 ngày | Chạy được nhưng **tốn hơn**: thêm phí Private CA (mỗi certificate), KMS, Secrets Manager, và tải xoay vòng |
| Xoay vòng **dài hơn 7 ngày** | **Hỏng giao tiếp** |
| **Xoá secret** | Xoay vòng thất bại, hỏng giao tiếp |
| Thêm **replica Region** cho secret | ECS không xoá được secret (không có quyền gỡ Region). Gỡ bằng `aws secretsmanager remove-regions-from-replication` |

Service Connect **không hỗ trợ thu hồi certificate**. Nó dựa vào certificate ngắn hạn cộng xoay vòng thường xuyên thay cho thu hồi.

**Giám sát:** xoay vòng lỗi sẽ đẩy sự kiện `RotationFailed` lên CloudTrail. Nên đặt CloudWatch Alarm cho sự kiện này.

*(Nguồn: `15-04-07-01-06-00` mục 2.1)*

---

**Câu 19. Kiểm tra TLS đã bật bằng cách nào? Vì sao gọi tên DNS không thấy certificate?**

**Vì sao gọi tên không thấy:** gọi `user:6379` thì app nói chuyện với **proxy cục bộ** bằng **plaintext**. TLS chỉ diễn ra giữa hai proxy. App và `openssl` chạy trong task client đều "đứng trước" proxy client nên không thấy certificate. Docs ghi rõ: "gọi tên DNS trực tiếp **không** hiện ra certificate".

**Cách kiểm tra:** kết nối **thẳng tới IP:port của task server**. Làm vậy là bỏ qua proxy client và nói chuyện trực tiếp với proxy server, mà proxy server đang chờ TLS nên trình ra certificate.

Các bước:
1. Đưa `openssl` vào image của app.
2. Có đường vào mạng: bật **ECS Exec** cho service, **hoặc** chạy một EC2 instance trong **cùng VPC**.
3. Lấy **IP:port** của task server trong console **Cloud Map** → namespace → service details.
4. Vào shell:
   ```bash
   aws ecs execute-command --cluster <cluster> --task <task-id> \
     --container <container> --interactive --command "/bin/sh"
   ```
5. Xem certificate:
   ```bash
   openssl s_client -connect 10.0.147.43:6379 < /dev/null 2>/dev/null | openssl x509 -noout -text
   ```

Kết quả đúng sẽ có:
- `Signature Algorithm: ecdsa-with-SHA256`
- `Validity` khoảng 7 ngày (short-lived)
- `Subject Alternative Name: DNS:<discovery-name>.<namespace>` (vd `redis.yelb-cftc`)
- `Extended Key Usage: TLS Web Server Authentication, TLS Web Client Authentication`
- `CA:FALSE` (certificate cuối, không phải CA)

🔎 *Ngược lại, nếu `openssl s_client` báo lỗi bắt tay, hoặc `curl http://IP:port` trả plaintext bình thường, thì TLS chưa bật.*

*(Nguồn: `15-04-07-01-06-02`)*

---

#### Phần D: Nâng cao

**Câu 20. Zone-aware routing cần tối thiểu bao nhiêu task đích với 3 AZ? Dưới ngưỡng thì sao?**

- Ngưỡng: số endpoint (task) khả dụng của service đích **≥ 2 × số AZ**. Với **3 AZ** thì cần **≥ 6 task**. Với 2 AZ thì cần ≥ 4.
- **Dưới ngưỡng:** zone-aware routing **tự tắt**, traffic chia đều bằng **round-robin** giữa mọi task ở mọi AZ. Mục đích là tránh dồn hết traffic của một AZ vào 1 task duy nhất ở AZ đó, gây quá tải.
- **Đủ lại ngưỡng** (vd scale out): **tự bật lại**, không cần deploy.
- Khi bật: proxy giữ phần lớn traffic trong cùng AZ (**> 80%** nếu task trải đều). Phần còn lại chia sang AZ khác theo **capacity còn dư**. Task cùng AZ hỏng thì chuyển sang AZ khác.
- Theo dõi bằng metric Envoy `lb_zone_cluster_too_small`. Giá trị > 0 nghĩa là đang dưới ngưỡng. Lúc mới deploy (task chưa healthy hết) thì > 0 là bình thường; deployment ổn định phải về 0.
- Điều kiện khác: service đã có từ trước phải **deploy lại một lần** (cả client và server); EC2 cần AMI ≥ `20260323`.

Hệ quả thiết kế: service nhỏ (2–5 task trên 3 AZ) **không bao giờ** được hưởng zone-aware, luôn chạy round-robin và luôn tốn phí truyền dữ liệu giữa AZ.

*(Nguồn: `15-04-07-01-05`)*

---

**Câu 21. Chia sẻ namespace cross-account cần managed permission nào? Khi quyền bị thu hồi, service và task đang chạy bị gì?**

**Permission bắt buộc:** **`AWSRAMPermissionCloudMapECSFullPermission`** khi tạo resource share trong AWS RAM. Dùng permission khác thì Service Connect **không hoạt động đúng**.

Quy trình: chủ namespace tạo namespace → tạo RAM share (resource type *Cloud Map Namespaces*, permission trên, principal là account hoặc OU) → bên dùng **Accept** → bên dùng cấu hình service với **ARN** namespace (không dùng tên hay ID).

**Khi quyền bị thu hồi:**

| Tác động | Chi tiết |
|---|---|
| Thao tác mới thất bại | `CreateService`, `UpdateService`, `ListServicesByNamespace` (và `CreateCluster`/`UpdateCluster` nếu dùng namespace đó) |
| **Task mới không start được** | Thiếu `RegisterInstance`, `GetOperation`. Lỗi hiện ở stopped task error |
| **Task đang dừng bị kẹt** | Kẹt ở `DEACTIVATING` / `DEPROVISIONING` vì không `DeregisterInstance` được. Xem `statusReason` trong task attachment (`DescribeTasks`) |
| **Xoá service kẹt** | Ban đầu trông như xoá thành công, nhưng **kẹt ở `DRAINING`** vì thiếu `servicediscovery:DeleteService` |
| **TLS** | Private CA gắn theo namespace, nên mất quyền namespace là **mất quyền CA** |

**Cách khắc phục:** chủ namespace **chia sẻ lại** (khôi phục quyền) thì các thao tác kẹt mới hoàn tất được.

🔎 *Task đang chạy ổn định có thể vẫn phục vụ traffic một thời gian, nhưng mọi thay đổi (scale, deploy, thay task) đều hỏng. Thực tế gần như service bị "đóng băng".*

*(Nguồn: `15-04-07-01-03-00`, `-01`, `-02`)*

---

**Câu 22. Metric Service Connect nằm ở account nào khi dùng namespace chia sẻ?**

- Mặc định chủ namespace và bên dùng **không xem được metric CloudWatch của nhau**.
- **Target metric** (metric về traffic gửi tới endpoint đích, dimension `TargetDiscoveryName…`) chỉ được đẩy về account **sở hữu client service** (bên gọi).
- Account sở hữu **client-server service** (bên được gọi) **không thấy** metric mà account client nhận, và ngược lại.
- Ví dụ: order-service ở account B gọi user-service ở account A. Metric "order gọi user: số request, lỗi, độ trễ" nằm ở **account B**. Đội user-service ở account A không thấy góc nhìn đó.
- **Muốn xem chéo:** thiết lập **CloudWatch cross-account observability**, vd gom về một account monitoring trung tâm.

*(Nguồn: `15-04-07-01-03-00` mục 1)*

---

**Câu 23. Khi nào không nên dùng Service Connect mà nên chọn service discovery hoặc VPC Lattice?**

**Chọn Service discovery (Cloud Map DNS) khi:**
- Cần **độ trễ thấp nhất**: traffic đi thẳng container sang container, không qua proxy (Service Connect thêm 2 hop proxy).
- **Client nằm ngoài ECS** (EC2 thường, Lambda trong VPC, on-prem qua VPN) cần tìm service ECS **bằng tên**. Service Connect không cho client ngoài namespace phân giải tên endpoint.
- Không muốn tốn CPU/RAM cho sidecar (task rất nhỏ, số lượng rất lớn).
- Workload dùng **network mode `host`**: Service discovery dùng được (với điều kiện client hiểu bản ghi SRV khi không có `hostPort`), còn Service Connect thì không.
- Workload thuộc loại Service Connect không hỗ trợ: **Windows container**, **standalone task** (🔎 *service discovery cũng gắn với service, standalone task thì phải tự đăng ký*), blue/green **CodeDeploy**, ECS Anywhere.
- Chấp nhận **tự viết** retry và xử lý backend hỏng, cũng như độ trễ do TTL DNS.

**Chọn VPC Lattice khi:**
- Kết nối giữa **nhiều loại compute**: ECS cùng EC2 instance, Lambda, IP, ALB. Service Connect chỉ nối ECS service với nhau.
- Kiến trúc **nhiều VPC, nhiều account** cần mạng ứng dụng được quản lý tập trung, có quan sát và bảo mật thống nhất, mà không muốn tự dựng VPC peering.
- Cần hỗ trợ **mọi network mode**, kể cả `host`.
- Chấp nhận **có phí** (Service Connect thì miễn phí).

**Chọn ALB nội bộ khi:** cần tính năng tầng 7 mạnh (định tuyến theo path hoặc header, WAF), hoặc client ngoài ECS cần một endpoint ổn định.

**Còn lại** (ECS ↔ ECS, cùng Region, Linux, service, muốn ít việc nhất) thì chọn **Service Connect**, lựa chọn AWS khuyến nghị.

*(Nguồn: `15-04-07-00`, `15-04-07-01-00` mục 3, `15-04-07-01-01` mục 3, `15-99` mục 7)*

---

**Câu 24. gRPC qua Service Connect: vì sao nên khai `appProtocol: grpc`?**

Theo docs, khai `appProtocol` thì:
1. **Metric theo giao thức:** proxy hiểu request ở tầng 7 nên đếm được request, lỗi, độ trễ theo gRPC (tương tự HTTP 5xx cho HTTP).
2. **Access log dạng gRPC:** có thêm `%GRPC_STATUS()%` và `%STREAM_ID%`, nhìn được mã trạng thái gRPC (`OK`, `UNAVAILABLE`, `DEADLINE_EXCEEDED`…) thay vì chỉ có byte và kết nối như TCP.
3. **`perRequestTimeout`** chỉ áp dụng được khi `appProtocol` là `http`/`http2`/`grpc`. Không khai thì chỉ có `idleTimeout` kiểu TCP (1 giờ).

🔎 **Lý do quan trọng nhất (ngoài folder): cân bằng tải.**
- gRPC chạy trên **HTTP/2**: client mở **một kết nối dài** rồi gửi rất nhiều request (multiplex) trên kết nối đó.
- Không khai `appProtocol` thì proxy coi là **TCP** và cân bằng **theo kết nối**. Một client giữ một kết nối nên **mọi request dồn vào một task** đích. Scale server lên 10 task cũng vô ích, vì client cũ vẫn bám task cũ.
- Khai `grpc` thì Envoy hiểu HTTP/2 và cân bằng **theo từng request** (round-robin per request), nên tải chia đều. Retry và outlier detection cũng hoạt động ở mức request.

Lưu ý khi dùng gRPC:
- Stream dài (server-streaming, bidi) thì tăng hoặc tắt (`0`) `perRequestTimeout` (mặc định 15 giây). `idleTimeout` mặc định 5 phút.
- App gọi plaintext (`insecure.NewCredentials()`) tới `tên:port`. Cần mã hoá thì bật TLS của Service Connect, không cần TLS trong app.
- Traffic gRPC **từ internet** thì dùng **ALB** (HTTP/2, hỗ trợ gRPC), không phải Service Connect.

*(Nguồn: `15-04-07-01-01` mục 2.2, `15-04-07-01-04-00` mục 2.3, `13-10` port mappings; phần cân bằng tải là 🔎)*

---

**Câu 25. Xoá stack CloudFormation có Service Connect TLS bị kẹt `DRAINING`. Nguyên nhân?**

**Nguyên nhân: thứ tự xoá sai.** CloudFormation xoá các tài nguyên **song song** hoặc theo thứ tự phụ thuộc mà nó biết. Nếu template không khai phụ thuộc, nó có thể xoá **KMS key, IAM infrastructure role, hoặc AWS Private CA trước** ECS service.

Khi xoá ECS service có TLS, ECS còn phải dọn dẹp: huỷ certificate và secret (`ecs-sc!…`) trong Secrets Manager, dùng KMS key để thao tác với secret, dùng infrastructure role để có quyền làm những việc đó. Nếu role, key hoặc CA đã mất thì ECS **không dọn được**, và service **kẹt ở `DRAINING`**.

Docs: "AWS KMS key (nếu có), IAM role, và AWS Private CA nên được **xoá sau** Amazon ECS service."

**Cách sửa:**
- Trong template, cho ECS service **`DependsOn`** KMS key, infrastructure role và Private CA. CloudFormation tạo các tài nguyên đó trước, và khi xoá thì **xoá service trước**, rồi mới xoá chúng.

```yaml
UserService:
  Type: AWS::ECS::Service
  DependsOn:
    - ServiceConnectKmsKey
    - EcsInfrastructureRole
    - PrivateCA
```

- Terraform: 🔎 *tham chiếu ARN của key, role, CA trực tiếp trong resource `aws_ecs_service` (Terraform tự suy ra phụ thuộc), hoặc dùng `depends_on`.*
- **Đã kẹt rồi:** tạo lại (hoặc khôi phục) role, key hoặc CA đã bị xoá để ECS hoàn tất việc dọn dẹp.

Hai trường hợp khác cũng gây kẹt `DRAINING`:
- Dùng **namespace chia sẻ** mà quyền RAM bị thu hồi (thiếu `servicediscovery:DeleteService`). Sửa bằng cách nhờ chủ namespace chia sẻ lại.
- Thêm **replica Region** cho secret của Service Connect, nên ECS không xoá được secret. Sửa bằng `remove-regions-from-replication`.

*(Nguồn: `15-04-07-01-06-00` mục 2.1 và 2.4, `15-04-07-01-03-02` mục 2.1)*

---

## 19. Bản đồ file nguồn

| Chủ đề | File |
|---|---|
| So sánh 3 cách kết nối service | `15-04-07-00-ecs-interconnect-services.md` |
| Tổng quan, thuật ngữ, giá | `15-04-07-01-00-ecs-service-connect.md` |
| Thứ tự deploy, networking, proxy, giới hạn | `15-04-07-01-01-ecs-service-connect-components.md` |
| Bảng tham số, các bước, health check ban đầu | `15-04-07-01-02-ecs-service-connect-configuration-overview.md` |
| Namespace chia sẻ (khái niệm / setup / lỗi) | `15-04-07-01-03-00`, `-01`, `-02` |
| Access log | `15-04-07-01-04-00`, `-01` |
| Zone-aware routing | `15-04-07-01-05-ecs-service-connect-zone-aware-routing.md` |
| TLS (khái niệm / bật / kiểm tra) | `15-04-07-01-06-00`, `-01`, `-02` |
| Tutorial CLI | `15-04-07-01-07-ecs-service-connect-cli-configuration.md` |
| Tham số `portMappings` (`name`, `appProtocol`, `protocol`) | `13 - Task definitions/13-09`, `13-10`, `13-11` |
| Namespace mặc định của cluster | `14 - Cluster/14-00`, `14-02-01`, `14-03-02` |
| Infrastructure role cho TLS | `05 - .../05-11-ecs-iam-roles-overview.md` |
| ALB hỗ trợ gRPC (traffic từ ngoài) | `05 - .../05-06-02-ecs-receiving-inbound-connections.md` |

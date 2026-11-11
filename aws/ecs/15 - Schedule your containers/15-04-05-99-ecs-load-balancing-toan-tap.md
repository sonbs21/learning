# Amazon ECS + Elastic Load Balancing: toàn tập

> **Nguồn:** tổng hợp từ các file `15-04-05-*` trong folder này. Lấy thêm từ `05-06-02` (nhận traffic từ internet), `05-08-01` (quota, throttling), `05-09-02` / `05-09-03` (scaling, capacity), `15-01` (vòng đời task), `15-04-00` (service), `15-04-06-01` (auto scaling), `13-02-03` (IPv6), `15-04-07-01-06-00` (TLS + ALB).
> Chỗ nào **không có trong folder** (kiến thức chung về ELB, suy luận, ví dụ tự đặt) được đánh dấu **🔎 Ngoài folder** hoặc **✍️ Ví dụ tự đặt**, để bạn tự kiểm chứng.

---

## Mục lục

0. [Tóm tắt trong 1 phút](#0-tóm-tắt-trong-1-phút)
1. [Vì sao cần load balancer cho ECS](#1-vì-sao-cần-load-balancer-cho-ecs)
2. [Các khái niệm nền: listener, rule, target group, target](#2-các-khái-niệm-nền)
3. [Ba loại load balancer: ALB, NLB, GWLB](#3-ba-loại-load-balancer)
4. [Target type và network mode](#4-target-type-và-network-mode)
5. [Cách ECS và load balancer phối hợp (vòng đời target)](#5-cách-ecs-và-load-balancer-phối-hợp)
6. [Health check](#6-health-check)
7. [Gỡ task: deregistration delay + SIGTERM](#7-gỡ-task-deregistration-delay--sigterm)
8. [Deployment và load balancer](#8-deployment-và-load-balancer)
9. [Nhiều target group cho một service](#9-nhiều-target-group-cho-một-service)
10. [Triển khai từng bước](#10-triển-khai-từng-bước)
11. [Kiến trúc mẫu](#11-kiến-trúc-mẫu)
12. [Bảo mật](#12-bảo-mật)
13. [Auto scaling dựa trên load balancer](#13-auto-scaling-dựa-trên-load-balancer)
14. [Quota và throttling](#14-quota-và-throttling)
15. [IPv6](#15-ipv6)
16. [Load balancer hay Service Connect hay API Gateway](#16-so-sánh-lựa-chọn-đầu-vào)
17. [Giá](#17-giá)
18. [Troubleshooting checklist](#18-troubleshooting-checklist)
19. [Câu hỏi tự kiểm tra (có đáp án)](#19-câu-hỏi-tự-kiểm-tra)
20. [Bản đồ file nguồn](#20-bản-đồ-file-nguồn)

---

## 0. Tóm tắt trong 1 phút

- Service ECS có thể **tuỳ chọn** gắn **Elastic Load Balancing** để chia traffic **đều** cho các task. ECS **tự đăng ký** task mới vào target group và **tự huỷ đăng ký** task sắp dừng. Bạn không bao giờ phải tự thêm IP.
- Có 3 loại:
  - **ALB:** tầng 7, HTTP/HTTPS/gRPC/WebSocket. **AWS khuyến nghị mặc định dùng ALB.**
  - **NLB:** tầng 4, TCP/UDP/TLS, hàng triệu request/giây.
  - **GWLB:** tầng 3, dùng cho virtual appliance như firewall, IDS/IPS.
- **`awsvpc` (bắt buộc với Fargate) thì target type phải là `ip`.** Mode `bridge` trên EC2 dùng `instance` + **dynamic host port** (host port 0).
- **Mỗi service một target group riêng.** Một service có tối đa **5 target group** (ALB/NLB).
- Load balancer quyết định **tốc độ deploy** qua hai nút:
  - **Health check**: mặc định interval 30 giây × healthy threshold 5. Có thể giảm còn 5 giây × 2.
  - **Deregistration delay**: mặc định **300 giây**. Có thể giảm còn **5 giây** nếu request ngắn.
- Task **fail health check của target group** thì bị ECS **dừng và thay**.
- Cấu hình load balancer của service **không sửa được trên console** sau khi tạo. Phải dùng CLI/SDK/CloudFormation (chỉ với deployment controller `ECS`), và mỗi lần sửa là một **deployment mới**.
- Load balancer dành cho **traffic từ ngoài vào** (internet hoặc client ngoài ECS). **ECS gọi ECS** thì AWS khuyến nghị **Service Connect** (không cần load balancer).

---

## 1. Vì sao cần load balancer cho ECS

*(Nguồn: `05-06-02` mục 0, `15-04-05-00`)*

Task ECS **có vòng đời ngắn**: IP thay đổi mỗi lần scale, deploy, hoặc khi task chết. Client từ internet cần **một địa chỉ cố định**.

```
Không khuyến nghị (quy mô lớn):  Internet ──► container ở public subnet có IP public
Khuyến nghị:                     Internet ──► lớp đầu vào scale được (ALB | NLB | API Gateway) ──► task ở private subnet
```

Load balancer mang lại:

| Lợi ích | Giải thích |
|---|---|
| **Một điểm vào cố định** | DNS name của load balancer không đổi, task phía sau thay thoải mái |
| **Chia tải đều** | Phân phối request cho mọi task healthy |
| **Chỉ gửi tới task healthy** | Health check của target group |
| **Task ở private subnet** | Chỉ load balancer ở public subnet, task không cần IP public |
| **Tự động hoá với ECS** | ECS tự đăng ký/huỷ đăng ký target theo vòng đời task |
| **Tính năng thêm** (ALB) | TLS termination, định tuyến theo host/path, WAF, gRPC/WebSocket |
| **Thay task unhealthy** | Task fail health check của target group thì service scheduler tự thay |

---

## 2. Các khái niệm nền

🔎 *Phần định nghĩa listener/rule là kiến thức ELB chung. Folder chỉ nhắc tên.*

```
                     ┌──────────────────── Load balancer ────────────────────┐
Client ──► DNS ──►   │ Listener :443 (HTTPS)                                  │
                     │   ├─ Rule: host = api.example.com   ─► Target group A  │──► task A1, A2, A3 (ECS service "api")
                     │   ├─ Rule: path = /admin/*          ─► Target group B  │──► task B1, B2     (ECS service "admin")
                     │   └─ Default                        ─► Target group C  │──► task C1, C2     (ECS service "web")
                     │ Listener :80 (HTTP) ─► redirect sang 443               │
                     └────────────────────────────────────────────────────────┘
                               Health check do TARGET GROUP định nghĩa
```

| Khái niệm | Là gì | Liên quan ECS |
|---|---|---|
| **Load balancer** | Thực thể có DNS name, đặt trong các subnet (mỗi AZ một subnet) | Subnet của LB phải bao gồm **mọi AZ** có task/container instance |
| **Listener** | Port + protocol mà LB lắng nghe (vd HTTPS:443) | Muốn 80 và 443 thì tạo **hai listener**: 443 chuyển tới service, 80 redirect sang HTTPS |
| **Rule** (ALB) | Điều kiện (host, path…) và hành động (forward, redirect) | Nhiều service dùng chung **một listener port** nhờ path-based routing + priority |
| **Target group** | Nhóm target + **cấu hình health check** + **deregistration delay** | **Mỗi ECS service một target group riêng** |
| **Target** | Một đích cụ thể: `instance ID + port` hoặc `IP + port` | ECS tự đăng ký/huỷ |
| **Target type** | `instance`, `ip` (🔎 *còn có `lambda`, `alb`*) | `awsvpc` thì bắt buộc `ip` |

Liên kết service với target group nằm trong **service definition**:

```json
"loadBalancers": [
  {
    "targetGroupArn": "arn:aws:elasticloadbalancing:region:123456789012:targetgroup/web-tg/1234567890123456",
    "containerName": "web",
    "containerPort": 8080
  }
]
```

| Trường | Ý nghĩa |
|---|---|
| `targetGroupArn` | Target group mà task của service sẽ được đăng ký vào |
| `containerName` | Container **nào** trong task nhận traffic (task có thể có nhiều container) |
| `containerPort` | Port **nào** của container đó. Phải khớp với `portMappings` trong task definition |

> Bạn không chỉ định load balancer ARN trong service. Bạn chỉ định **target group**. Target group gắn với load balancer qua listener/rule. Docs yêu cầu target group phải **gắn với một load balancer** (đúng loại).

---

## 3. Ba loại load balancer

*(Nguồn: `15-04-05-00`, `-03`, `-04`, `-05`, `05-06-02`)*

### 3.1 Bảng so sánh tổng

| | **Application LB (ALB)** | **Network LB (NLB)** | **Gateway LB (GWLB)** |
|---|---|---|---|
| Tầng OSI | **7**: HTTP/HTTPS | **4**: TCP/UDP/TLS | **3**: mọi gói IP, mọi port |
| Chọn target | Theo rule (host/path, priority) | Thuật toán **flow hash** | Stickiness theo **5-tuple** (TCP/UDP) hoặc **3-tuple** (khác) |
| Sửa header | Có (là proxy HTTP) | **Không sửa header** | Không |
| Năng lực | Cao | **Hàng triệu request/giây** | — |
| Dùng cho | Website, REST API, gRPC, WebSocket | Giao thức không phải HTTP, UDP, cần mã hoá end-to-end, cần IP client | Virtual appliance: firewall, IDS/IPS, deep packet inspection |
| TLS termination | ✅ | ✅ (TLS listener) | — |
| Định tuyến host/path | ✅ | ❌ | ❌ |
| gRPC / WebSocket | ✅ (HTTP/2) | 🔎 *chỉ như TCP thuần* | — |
| UDP | ❌ | ✅ | ✅ (mọi IP) |
| WAF, HTTP desync mitigation | ✅ | ❌ | ❌ |
| Dynamic host port (bridge) | ✅ | ✅ | (docs có ví dụ, nhưng GWLB chỉ hỗ trợ `awsvpc`) |
| Network mode ECS | Mọi mode (`ip` cho awsvpc) | Mọi mode (`ip` cho awsvpc) | **Chỉ `awsvpc`** |
| Nhiều target group / service | ✅ tối đa 5 | ✅ tối đa 5 | Docs không nêu (mục nhiều target group chỉ hỗ trợ ALB/NLB) |
| Fargate | ✅ | ✅ (platform `1.4.0` Linux / `1.0.0` Windows) | ✅ (platform `1.4.0` / `1.0.0`) |
| **AWS khuyến nghị** | **Mặc định** | Khi cần tính năng chỉ NLB có | Khi cần virtual appliance |

### 3.2 ALB: điểm mạnh với ECS

1. **Nhiều target group cho một service**: một service nhận traffic từ nhiều LB, mở nhiều port.
2. **Hỗ trợ cả Fargate lẫn EC2.**
3. **Dynamic host port mapping**: nhiều task cùng service chạy trên **một** EC2 instance (mode `bridge`).
4. **Path-based routing + priority rule**: **nhiều service** dùng chung **một listener port** trên **một** ALB, nên tiết kiệm tiền (không phải mỗi service một ALB).
5. TLS termination (app không cần lo certificate), WAF (chặn SQL injection, XSS), HTTP desync mitigation, gRPC và WebSocket qua HTTP/2.

Luồng: ALB ở **public subnet** nhận HTTP request, rồi **mở kết nối mới** tới **IP private** của task và chuyển tiếp request qua kết nối nội bộ đó.

### 3.3 NLB: điểm mạnh và điểm cần nhớ

- Tầng 4, **không đọc nội dung** gói tin, nên hợp với **mã hoá end-to-end** (TLS đi xuyên tới app).
- Có thể **kết thúc TLS** để backend không phải làm.
- **UDP**: game server, DNS, VoIP, syslog… 🔎 *ví dụ tự đặt*.
- **Client IP preservation** tương thích với target Fargate: app thấy **IP thật của client**.
- Giới hạn số target **chặt hơn** ALB; bật cross-zone thì còn thêm quota `Targets per Availability Zone Per Network Load Balancer`.
- **Đóng kết nối khi task dừng:** NLB không hiểu giao thức tầng cao nên **không gửi được thông điệp đóng kết nối** ở tầng ứng dụng như ALB. Có 3 cách:

| Cách | Ghi chú |
|---|---|
| App tự đóng kết nối | Tuỳ app |
| Bật **connection termination** của target group NLB (đóng kết nối tầng 4 khi target bị deregister) | Client có thể thấy lỗi nếu không xử lý retry |
| Đặt **deregistration delay > timeout kết nối của client** | Client timeout trước và tự nối lại qua NLB sang task mới, task cũ drain dần. **Cách êm nhất** |

### 3.4 GWLB

- Tầng 3, nghe **mọi gói IP trên mọi port**, giữ luồng dính vào một appliance theo 5-tuple/3-tuple.
- Với ECS: **chỉ `awsvpc`**, target type `ip`.
- ⚠️ **Rủi ro bảo mật:** target là IP (🔎 *và tắt Client IP Preservation*) thì request được nhìn thấy như đến từ **IP private của GWLB**. Mở security group cho "IP của GWLB" (để nhận request + health check) nghĩa là **mở cho cả thế giới**, vì mọi traffic đều mang IP đó.
- Docs có điểm mâu thuẫn: ví dụ dynamic port (instance ID + port) và quy tắc target type `instance` khó áp dụng, vì GWLB chỉ hỗ trợ `awsvpc` mà `awsvpc` bắt buộc `ip`. **Thực tế cấu hình: `awsvpc` + `ip`.**

---

## 4. Target type và network mode

*(Nguồn: `15-04-05-03`, `-04`, `-05`, `13-02-03`)*

### 4.1 Quy tắc

| Network mode của task | Target type | Target được đăng ký là | Ghi chú |
|---|---|---|---|
| **`awsvpc`** (Fargate bắt buộc; EC2 tuỳ chọn) | **`ip`** (bắt buộc) | **IP của ENI của task** + `containerPort` | Task gắn với **ENI**, không gắn với instance. **Hỗ trợ chéo VPC** |
| **`bridge`** (EC2) | `instance` | **Instance ID** + **host port** (động nếu `hostPort: 0`) | LB và task phải **cùng VPC** |
| **`host`** (EC2) | `instance` | Instance ID + port (= containerPort) | 🔎 *Mỗi instance chỉ chạy được 1 task mỗi port* |

Không đăng ký được **theo instance ID** với các instance type đời cũ **C1, CC1, CC2, CG1, CG2, CR1, G1, G2, HI1, HS1, M1, M2, M3, T1**. Với các type này phải đăng ký **theo IP**.

### 4.2 Dynamic host port mapping (mode `bridge`)

```
Task definition: containerPort = 80, hostPort = 0
                          │
Task 1 khởi động  ──► Docker chọn host port 32768  ──► ECS đăng ký target (i-abc, 32768)
Task 2 khởi động  ──► Docker chọn host port 32769  ──► ECS đăng ký target (i-abc, 32769)
                          │
ALB/NLB chia traffic: i-abc:32768, i-abc:32769, i-def:32770 ...
```

- Host port được chọn từ **dải ephemeral** của container instance (vd **32768–61000** trên ECS-optimized AMI mới nhất).
- Lợi ích: **nhiều task của cùng service trên cùng một instance**, không đụng port.
- Hệ quả bảo mật: security group của **EC2 instance** phải mở **cả dải ephemeral** từ security group của LB. 🔎 *suy luận; docs Service Connect cũng nói mở dải port động phía trên cho bridge.*

### 4.3 `awsvpc` + `ip`

```
Task (ENI 10.0.12.34) ── containerPort 8080 ──► target (10.0.12.34, 8080)
```

- Mỗi task một ENI, **một security group riêng mỗi service**: chỉ cần mở `containerPort` **từ SG của LB**. Gọn và chặt.
- Mỗi ENI được đăng ký thành một target, nên tính vào quota ENI và quota target (mục 14).
- 🔎 *Đăng ký chéo VPC được với target type `ip` (task ở VPC khác có peering), vì target là IP.*

---

## 5. Cách ECS và load balancer phối hợp

*(Nguồn: `15-01`, `15-03-03`, `15-04-05-01`, `-02`, `15-04-00`, `05-08-01`)*

### 5.1 Vòng đời task gắn với target group

```
PROVISIONING   (vd tạo ENI cho awsvpc)
     │
PENDING        (chờ tài nguyên)
     │
ACTIVATING     pull image, tạo container, cấu hình mạng,
     │         ★ ĐĂNG KÝ vào target group (RegisterTargets)
     │         LB health check target → healthy (target mới: chỉ cần 1 lần pass)
     ▼
RUNNING        nhận traffic
     │
     │  (scale in / deploy / stop task / task unhealthy)
     ▼
DEACTIVATING   ★ HUỶ ĐĂNG KÝ khỏi target group (DeregisterTargets)
     │         LB ngừng gửi request MỚI; target ở "draining"
     │         chờ tối đa deregistration delay (mặc định 300 giây) cho tới khi target UNUSED
     ▼
STOPPING       ECS gửi SIGTERM (STOPSIGNAL) → chờ stop timeout (mặc định 30 giây) → SIGKILL
     │
DEPROVISIONING (vd gỡ ENI)
     │
STOPPED
```

**Thời gian từ lúc cần task tới lúc task nhận traffic** = `PROVISIONING` + `PENDING` + `ACTIVATING` (gồm pull image) + **health check của target group**.

**Thời gian gỡ một task cũ** ≈ **deregistration delay** + **thời gian app tắt** (≤ stop timeout).

### 5.2 Ai làm gì

| Việc | Ai làm |
|---|---|
| Tạo LB, listener, rule, target group, cấu hình health check | **Bạn** (console EC2, CLI, IaC) |
| Gắn target group vào service (`loadBalancers`) | **Bạn** (lúc create service, hoặc `UpdateService`) |
| Đăng ký / huỷ đăng ký từng task | **ECS** (qua **service-linked role** `AWSServiceRoleForECS`) |
| Gửi health check | **Load balancer** |
| Theo dõi kết quả health check, coi container là healthy | **ECS** (agent chờ LB báo cáo) |
| Dừng và thay task fail health check | **ECS service scheduler** |

> **Service-linked role là bắt buộc**, vì nó cấp quyền để ECS đăng ký/huỷ target. Khi tạo service có nhiều target group, role được tạo **bằng cách bỏ trống** tham số `role` (API) / `Role` (CloudFormation).

### 5.3 Thay task unhealthy

Khi task bị đánh dấu unhealthy (fail **container health check** hoặc **health check của target group**):

```
1. Scheduler khởi động task THAY THẾ trước (nếu maximumPercent cho phép)
2. Task thay thế HEALTHY   → dừng task unhealthy
   Task thay thế UNHEALTHY → dừng task thay thế hoặc task cũ, để tổng số = desiredCount
3. maximumPercent không cho chạy thêm → dừng NGẪU NHIÊN từng task unhealthy trước, rồi mới khởi động task thay
4. Còn dư task healthy so với desiredCount → dừng ngẫu nhiên cho bằng desiredCount
```

Task fail health check của LB thì bị **dừng và khởi động lại**, lặp lại cho tới khi đạt desired count. 🔎 *Nếu lỗi do cấu hình (sai path, sai port, SG chặn) thì sẽ thành vòng lặp start → fail → stop liên tục. Xem mục 18.*

---

## 6. Health check

*(Nguồn: `15-04-05-01`, `05-09-03`)*

### 6.1 Cơ chế

- LB chỉ gửi request tới target **healthy**, trong các AZ của LB.
- Health check được cấu hình ở **target group**.
- Target **mới đăng ký** chỉ cần **1 lần** health check pass là được coi healthy, **bất kể** healthy threshold.
- **Healthy threshold count** chỉ áp dụng khi target **đi từ unhealthy về healthy**.
- ECS agent **chờ LB báo** kết quả trước khi coi container healthy.

### 6.2 Tham số quan trọng

| Tham số | Tên API | Tên console EC2 | Mặc định | Tăng tốc (docs) |
|---|---|---|---|---|
| Interval | `HealthCheckIntervalSeconds` | **Interval** | **30 giây** | **5** |
| Healthy threshold | `HealthyThresholdCount` | **Healthy threshold** | **5** | **2** |
| Thời gian hồi phục (interval × threshold) | | | **150 giây** (2 phút 30) | **10 giây** |

Điều kiện để tăng tốc: service **khởi động và ổn định trong dưới 10 giây**.

Docs (`05-09-03`) cũng nói health check mặc định có thể mất **90 giây hoặc lâu hơn**. Giảm interval và threshold giúp task mới nhận traffic sớm, đỡ tải cho task khác lúc scale gấp.

🔎 **Các tham số khác (ngoài folder):**

| Tham số | Ý nghĩa | Lưu ý |
|---|---|---|
| `HealthCheckPath` | Path ALB gọi (vd `/health`) | Endpoint phải trả nhanh, **không** phụ thuộc DB nặng, nếu không lỗi DB sẽ kéo mọi task unhealthy cùng lúc |
| `HealthCheckPort` | `traffic-port` (mặc định) hoặc port cụ thể | Với TLS Service Connect: phải bằng **container port**, không phải 443 |
| `HealthCheckTimeoutSeconds` | Thời gian chờ mỗi lần check | Phải < interval |
| `UnhealthyThresholdCount` | Số lần fail liên tiếp thì đánh dấu unhealthy | Thấp quá thì task bị giết oan khi có spike |
| `Matcher` (HttpCode) | Mã thành công, vd `200` hoặc `200-399` | App redirect `/` sang `/login` (302) mà matcher chỉ `200` thì fail |
| `healthCheckGracePeriodSeconds` (**trong ECS service**) | Bỏ qua kết quả health check của LB trong N giây đầu sau khi task khởi động | Dùng cho app khởi động chậm (Java/Spring). Không đặt thì task bị giết trước khi kịp lên |

### 6.3 Health check của LB khác container health check thế nào

| | Health check của target group (LB) | Container `healthCheck` (task definition) |
|---|---|---|
| Ai chạy | Load balancer, từ **bên ngoài** qua mạng | ECS agent / Docker, chạy lệnh **bên trong** container |
| Kiểm tra được | Đường mạng + SG + app trả lời đúng | Tiến trình app còn sống, logic tự kiểm |
| Fail thì | LB ngừng gửi traffic; ECS thay task | ECS đánh dấu container unhealthy; container essential thì thay task |
| Cần LB không | Có | Không |

Nên dùng **cả hai**: container health check bắt lỗi app treo; LB health check bắt lỗi mạng/SG và quyết định có gửi traffic hay không.

---

## 7. Gỡ task: deregistration delay + SIGTERM

*(Nguồn: `15-04-05-02`, `05-06-02` mục 2)*

### 7.1 Deregistration delay (connection draining)

- Client thường giữ **kết nối keep-alive** để dùng lại.
- Khi muốn ngừng gửi traffic tới một task, ECS báo LB (huỷ đăng ký). LB **ngừng gửi request mới**, rồi chờ client đóng kết nối keep-alive. Khi xong (hoặc hết thời gian chờ), target chuyển sang **`UNUSED`** và ECS mới đi tiếp.
- Khoảng chờ tối đa đó là **deregistration delay**:

| `deregistration_delay.timeout_seconds` | Khi nào |
|---|---|
| **300** (mặc định) | An toàn, nhưng **mỗi đợt thay task chờ tới 5 phút**, nên deploy rất chậm |
| **5** | Service có thời gian phản hồi **dưới 1 giây** |
| ❌ Đừng đặt 5 | Request sống lâu: **upload file chậm**, **streaming**, 🔎 *WebSocket, long polling* |

> Đây là **thủ phạm số 1** làm deploy ECS chậm. Rolling update thay task theo đợt, mỗi đợt có thể chờ 300 giây.

### 7.2 SIGTERM và stop timeout

```
ECS dừng container
  ├─ gửi STOPSIGNAL (khai trong Dockerfile; mặc định SIGTERM)
  ├─ chờ tối đa stop timeout
  │     EC2: tham số agent ECS_CONTAINER_STOP_TIMEOUT (mặc định 30 giây)
  │     🔎 task definition: containerDefinitions[].stopTimeout (Fargate tối đa 120 giây)
  └─ app chưa thoát → SIGKILL (bị giết cứng, request dở dang mất)
```

| `ECS_CONTAINER_STOP_TIMEOUT` | Khi nào |
|---|---|
| **30** (mặc định) | — |
| **2** | Rút ngắn khoảng chờ |
| Thời gian tắt × **2** | App cần hơn 1 giây để tắt, vd tắt mất 3 giây thì đặt 6 |

**Cách tốt nhất:** app **bắt SIGTERM**, ngừng nhận request mới, xử lý xong request đang dở rồi thoát. Ví dụ Node.js trong docs:

```javascript
process.on('SIGTERM', function() {
  server.close();   // ngừng nhận request mới, xong request cũ thì event loop rỗng → thoát
})
```

✍️ Tương tự với **Spring Boot**: `server.shutdown=graceful` + `spring.lifecycle.timeout-per-shutdown-phase=20s`. Với **Go**: `signal.Notify(ch, syscall.SIGTERM)` rồi gọi `srv.Shutdown(ctx)`.

### 7.3 Ghép lại: thứ tự đúng khi gỡ task

```
t=0     ECS DeregisterTargets → LB ngừng gửi request mới tới task
t=0..D  LB chờ kết nối cũ xong (D = deregistration delay)
t=D     target UNUSED → ECS gửi SIGTERM
t=D..S  app shutdown êm (S ≤ stop timeout)
t=D+S   SIGKILL nếu vẫn chưa thoát
```

| Thời gian chờ | Tham số | Mặc định | Tăng tốc | Điều kiện |
|---|---|---|---|---|
| LB chờ client đóng keep-alive | `deregistration_delay.timeout_seconds` | 300 giây | 5 giây | Phản hồi < 1 giây; không dùng cho upload/stream |
| ECS chờ trước SIGKILL | `ECS_CONTAINER_STOP_TIMEOUT` | 30 giây | 2 giây | Thời gian tắt × 2 |

**NLB:** đặt **deregistration delay dài hơn timeout của client**. Client sẽ timeout trước và tự nối lại sang task mới, trong khi task cũ drain hết.

---

## 8. Deployment và load balancer

*(Nguồn: `15-04-05-03` mục 2, `15-04-05-06`, `15-04-06-01`, `15-04-00`)*

### 8.1 Rolling update qua load balancer

✍️ Ví dụ: `desiredCount=4`, `minimumHealthyPercent=100`, `maximumPercent=200`.

```
Bước 1: chạy 4 task mới (v2) song song 4 task cũ (v1)  → 8 task
Bước 2: task v2 ACTIVATING → đăng ký target group → health check pass → nhận traffic
Bước 3: ECS deregister 4 task v1 → draining (deregistration delay) → SIGTERM → STOPPED
Trong suốt quá trình, LB gửi traffic cho mọi target healthy (cả v1 lẫn v2) → không downtime
```

🔎 *Hệ quả: trong lúc deploy có giai đoạn **v1 và v2 cùng phục vụ**. API/DB schema phải tương thích ngược.*

### 8.2 Sửa cấu hình load balancer sau khi tạo service

| Cách | Được không |
|---|---|
| AWS Management Console | ❌ **Không sửa được** |
| AWS Copilot, CloudFormation, AWS CLI, SDK (`UpdateService`) | ✅ **chỉ** với deployment controller **`ECS`** |
| CodeDeploy blue/green, controller `EXTERNAL` | ❌ |

Mỗi lần **thêm, sửa, gỡ** cấu hình LB, ECS tạo **deployment mới**, task bị đăng ký/huỷ đăng ký lại. Docs khuyến nghị **thử trên môi trường test trước**.

```bash
aws ecs update-service --cluster prod --service web \
  --load-balancers targetGroupArn=arn:...:targetgroup/web-tg-v2/abc,containerName=web,containerPort=8080
```

### 8.3 Blue/green

- **Nhiều target group** được hỗ trợ với controller `ECS`, gồm cả **ECS native blue/green** và rolling update.
- 🔎 *Blue/green dùng 2 target group (blue và green) + listener (production, test). Traffic được chuyển giữa hai target group sau khi green healthy.*
- Metric **`ALBRequestCountPerTarget`** cho target tracking **không được hỗ trợ** với blue/green.

### 8.4 Task set

Khi dùng **task set**: **mọi** task trong set cùng dùng ELB, hoặc **không task nào** dùng.

---

## 9. Nhiều target group cho một service

*(Nguồn: `15-04-05-06`)*

**Điều kiện:**

| | |
|---|---|
| Loại LB | **ALB hoặc NLB** |
| Tối đa | **5 target group / service** |
| Deployment controller | **`ECS`** (rolling hoặc native blue/green) |
| Launch type | Fargate và EC2 |
| Tạo bằng | **API, SDK, CLI, CloudFormation** (console chỉ **xem** được) |
| Sửa bằng | `UpdateService` |
| IAM | Service-linked role (bỏ trống `role` / `Role`) |

**3 use case trong docs:**

```
(a) Một container, hai đường vào (nội bộ + internet)
    internal ALB      ─► TG1 ─┐
    internet-facing ALB ─► TG2 ─┴─► nginx:8080

(b) Một container, nhiều port
    ALB ─► TG1 ─► jenkins:8080   (web UI)
        └► TG2 ─► jenkins:50000  (API)

(c) Nhiều container trong cùng task
    ALB ─► TG1 ─► webserver:80
        └► TG2 ─► database:3306
```

```json
"loadBalancers": [
  { "targetGroupArn": "arn:...:targetgroup/internal-tg/111", "containerName": "nginx", "containerPort": 8080 },
  { "targetGroupArn": "arn:...:targetgroup/public-tg/222",   "containerName": "nginx", "containerPort": 8080 }
]
```

> Ví dụ trên docs có comment `//Internal ELB`. JSON thật không cho phép `//`, phải bỏ.

✍️ Lưu ý: health check của **mọi** target group phải pass thì task mới được coi là healthy. 🔎 *Suy luận: task fail health check ở bất kỳ target group nào đều có thể bị thay.*

---

## 10. Triển khai từng bước

✍️ **Ví dụ tự đặt** cho Fargate + ALB, ghép từ các quy tắc trong folder. Lệnh `elbv2` là 🔎 kiến thức CLI chung.

### 10.1 Sơ đồ mạng

```
VPC 10.0.0.0/16
├─ Public subnet AZ-a, AZ-b    ← ALB (internet-facing), NAT GW
└─ Private subnet AZ-a, AZ-b   ← task Fargate (awsvpc, không IP public)

sg-alb  : inbound 443, 80 từ 0.0.0.0/0
sg-task : inbound 8080 CHỈ từ sg-alb
```

Subnet của ALB phải phủ **mọi AZ** mà task chạy.

### 10.2 Tạo ALB, target group, listener

```bash
# 1. ALB
aws elbv2 create-load-balancer --name web-alb --type application --scheme internet-facing \
  --subnets subnet-pub-a subnet-pub-b --security-groups sg-alb

# 2. Target group: target-type ip (vì awsvpc), port = containerPort
aws elbv2 create-target-group --name web-tg --protocol HTTP --port 8080 \
  --vpc-id vpc-xxx --target-type ip \
  --health-check-path /health --health-check-interval-seconds 10 \
  --healthy-threshold-count 2 --unhealthy-threshold-count 3 --matcher HttpCode=200

# 3. Deregistration delay ngắn (API trả nhanh)
aws elbv2 modify-target-group-attributes --target-group-arn <tg-arn> \
  --attributes Key=deregistration_delay.timeout_seconds,Value=15

# 4. Listener HTTPS → forward; HTTP → redirect
aws elbv2 create-listener --load-balancer-arn <alb-arn> --protocol HTTPS --port 443 \
  --certificates CertificateArn=<acm-cert-arn> \
  --default-actions Type=forward,TargetGroupArn=<tg-arn>
aws elbv2 create-listener --load-balancer-arn <alb-arn> --protocol HTTP --port 80 \
  --default-actions 'Type=redirect,RedirectConfig={Protocol=HTTPS,Port=443,StatusCode=HTTP_301}'
```

### 10.3 Task definition

```json
{
  "family": "web",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "512", "memory": "1024",
  "executionRoleArn": "arn:aws:iam::123456789012:role/ecsTaskExecutionRole",
  "containerDefinitions": [{
    "name": "web",
    "image": "123456789012.dkr.ecr.ap-southeast-1.amazonaws.com/web:1.0.0",
    "essential": true,
    "portMappings": [{ "containerPort": 8080, "protocol": "tcp" }],
    "stopTimeout": 30,
    "healthCheck": {
      "command": ["CMD-SHELL", "wget -qO- http://localhost:8080/health || exit 1"],
      "interval": 10, "timeout": 5, "retries": 3, "startPeriod": 30
    }
  }]
}
```

### 10.4 Service gắn target group

```json
{
  "cluster": "prod",
  "serviceName": "web",
  "taskDefinition": "web",
  "desiredCount": 2,
  "launchType": "FARGATE",
  "networkConfiguration": {
    "awsvpcConfiguration": {
      "subnets": ["subnet-priv-a", "subnet-priv-b"],
      "securityGroups": ["sg-task"],
      "assignPublicIp": "DISABLED"
    }
  },
  "loadBalancers": [{
    "targetGroupArn": "arn:aws:elasticloadbalancing:...:targetgroup/web-tg/abc",
    "containerName": "web",
    "containerPort": 8080
  }],
  "healthCheckGracePeriodSeconds": 60,
  "deploymentConfiguration": {
    "minimumHealthyPercent": 100,
    "maximumPercent": 200,
    "deploymentCircuitBreaker": { "enable": true, "rollback": true }
  }
}
```

```bash
aws ecs create-service --cli-input-json file://web-service.json
```

### 10.5 Kiểm tra

```bash
aws elbv2 describe-target-health --target-group-arn <tg-arn>
# State: initial → healthy   |  unhealthy (Reason: Target.Timeout / Target.ResponseCodeMismatch ...)
aws ecs describe-services --cluster prod --services web --query 'services[0].events[:10]'
curl -I https://<alb-dns-name>/
```

### 10.6 Console

`Create service` → mục **Load balancing** → chọn loại LB → **Create new** hoặc **Use existing** load balancer → listener → target group (tạo mới hoặc chọn sẵn) → container:port. ⚠️ Sau khi tạo service, console **không cho sửa** phần này.

### 10.7 Nhiều service dùng chung một ALB

✍️ Một ALB, một listener 443, mỗi service một target group, phân luồng bằng rule:

| Priority | Điều kiện | Target group | ECS service |
|---|---|---|---|
| 10 | `host = api.example.com` | `api-tg` | api |
| 20 | `path = /admin/*` | `admin-tg` | admin |
| default | — | `web-tg` | web |

```bash
aws elbv2 create-rule --listener-arn <listener-443> --priority 10 \
  --conditions Field=host-header,Values=api.example.com \
  --actions Type=forward,TargetGroupArn=<api-tg-arn>
```

---

## 11. Kiến trúc mẫu

```
              Internet
                 │
         ┌───────▼────────┐  (tuỳ chọn) CloudFront + WAF
         │  ALB public    │  TLS termination, host/path routing
         └───┬───────┬────┘
             │       │
       web-tg│       │api-tg
             ▼       ▼
        [web svc] [api svc]  ←── private subnet, awsvpc, SG chỉ nhận từ sg-alb
                     │
                     │ Service Connect (ECS gọi ECS, không cần LB)
                     ▼
                [user svc] [order svc]
                     │
                     ▼
                  RDS / ElastiCache
```

| Nhu cầu | Lựa chọn |
|---|---|
| Website, REST, gRPC từ internet | ALB public |
| API nội bộ cho client ngoài ECS (EC2, Lambda trong VPC, on-prem qua VPN) | ALB **internal** |
| TCP/UDP thuần, cần IP client, cần end-to-end TLS, cần IP tĩnh 🔎 | NLB |
| Firewall, IDS/IPS dạng container | GWLB |
| Cùng service phục vụ cả nội bộ lẫn internet | **2 target group** (internal ALB + public ALB) |
| ECS service gọi nhau | **Service Connect** (không qua LB) |
| HTTP API traffic thấp/đột biến | API Gateway + VPC link + Cloud Map |

---

## 12. Bảo mật

| Điểm | Chi tiết |
|---|---|
| **Task ở private subnet** | Chỉ LB public; task `assignPublicIp: DISABLED` |
| **SG chaining** | SG của task chỉ nhận `containerPort` **từ SG của LB** (với `awsvpc`). Mode `bridge` phải mở dải ephemeral trên SG instance |
| **TLS termination ở ALB/NLB** | Certificate (🔎 ACM) đặt ở listener; app không phải quản lý certificate |
| **End-to-end TLS** | NLB pass-through (không đọc gói tin) |
| **WAF** | Gắn vào ALB: chặn SQL injection, XSS |
| **HTTP desync mitigation** | Có sẵn trên ALB |
| **GWLB + IP target** | Request mang IP private của GWLB; mở SG cho IP đó nghĩa là mở cho cả thế giới |
| **IP client thật** | 🔎 ALB: header `X-Forwarded-For`. NLB: client IP preservation (tương thích Fargate) |
| **TLS 1.3 + Service Connect** | ALB TLS listener policy TLS 1.3 (vd `ELBSecurityPolicy-TLS13-1-2-2021-06`); target group HTTPS; **health check port = container port** (không phải 443); `awsvpc`; **không** đặt `ingressPortOverride` |
| **Service Connect + ALB** | Ở `awsvpc`, traffic ALB **mặc định đi qua** proxy Service Connect. Muốn bỏ qua thì dùng `ingressPortOverride` |

---

## 13. Auto scaling dựa trên load balancer

*(Nguồn: `15-04-06-01`, `05-09-02`, `05-09-03`)*

| Metric | Cách dùng | Lưu ý |
|---|---|---|
| **`ALBRequestCountPerTarget`** (predefined) | Target tracking: giữ số request mỗi task quanh một giá trị | **Không hỗ trợ với blue/green** |
| **`ActiveConnectionCount`** của ALB | Đưa vào metric scale | Phải **chia cho số replica** để ra trung bình. Dùng **trung bình**, không dùng số đếm thô |
| CPU / Memory | Target tracking phổ biến | Không phản ánh trực tiếp tải request |

Lời khuyên liên quan từ `05-09-03`:
- **Giảm ngưỡng health check** để task mới nhận traffic sớm khi scale out gấp.
- Dùng **step scaling** khi cần phản ứng nhanh hơn target tracking.

🔎 *Scale in thì task bị deregister. Deregistration delay dài làm scale in chậm, nhưng giúp không cắt request đang dở.*

---

## 14. Quota và throttling

*(Nguồn: `05-08-01`)*

**Quota cần để ý** (phần lớn **tăng được** qua Service Quotas):

| LB | Quota |
|---|---|
| ALB | `Targets per Application Load Balancer`; `Targets per Target Group per Region` |
| NLB | Giới hạn target **chặt hơn**; bật cross-zone thì thêm `Targets per Availability Zone Per Network Load Balancer` |
| Service | Tối đa **5 target group** |
| `awsvpc` | Mỗi task một **ENI**, mỗi ENI là một target, nên tính cả quota ENI |

**API throttling:** ECS gọi `RegisterTargets`, `DeregisterTargets`, `DescribeTargetHealth` thay cho bạn. Account có **nhiều service dùng LB** deploy cùng lúc thì có thể bị throttle, deploy chậm, lỗi hiện trong **service event**. Cách xử lý: giãn lịch deploy, xem `05-08-02` (handle throttling).

---

## 15. IPv6

*(Nguồn: `15-04-05-03/04/05`, `13-02-03`)*

| LB | Service IPv6-only cần |
|---|---|
| ALB | IP address type của target group: **`dualstack`** hoặc **`dualstack-without-public-ipv4`** |
| NLB | **`dualstack`** |
| GWLB | **`dualstack`** |

- Service đang chạy sau ALB/NLB muốn chuyển sang IPv6-only: tạo LB dualstack mới rồi chuyển traffic, **hoặc** cập nhật IP address type của LB hiện có.
- EC2 mode `host`/`bridge`: instance **không có primary IPv6** thì task **không đăng ký được** với LB.

---

## 16. So sánh lựa chọn đầu vào

*(Nguồn: `05-06-02` mục 4, `15-04-07-00`)*

| | **ALB** | **NLB** | **API Gateway HTTP API** | **Service Connect** |
|---|---|---|---|---|
| Dùng cho | HTTP public / internal | TCP/UDP, end-to-end TLS | HTTP API traffic thấp/đột biến | **ECS gọi ECS** |
| Tầng | 7 | 4 | (HTTP) | Proxy sidecar (L4/L7) |
| Tới task bằng | IP private (target group) | IP private (target group) | **VPC link + Cloud Map** (ECS Service Discovery) | Proxy ↔ proxy |
| Tính phí | Theo **giờ** (+ dung lượng 🔎 LCU) | Theo giờ (+ NLCU 🔎) | Theo **request** (không có request thì không mất tiền) | **Miễn phí** |
| Tính năng thêm | WAF, path/host routing, gRPC/WebSocket | UDP, IP client, TLS pass-through | Auth (Lambda authorizer), usage tier, cache, sửa request/response | Retry, outlier, metric chuẩn, TLS tự động |

**Quy tắc chọn:**

```
Traffic từ internet, HTTP           → ALB (mặc định)
Traffic từ internet, TCP/UDP        → NLB
HTTP API ít request / cần auth, quota → API Gateway
Virtual appliance                   → GWLB
ECS ↔ ECS                           → Service Connect (không cần LB)
```

---

## 17. Giá

- Load balancer: **trả theo những gì dùng**. ALB/NLB có **phí theo giờ** để luôn sẵn sàng, 🔎 *cộng phí theo dung lượng xử lý (LCU/NLCU)*.
- Tải cao thì ALB/NLB **rẻ hơn mỗi request** so với API Gateway. Tải thấp hoặc có giai đoạn vắng thì API Gateway (tính phí theo request) có thể rẻ hơn.
- **Tiết kiệm:** dùng **một ALB cho nhiều service** (path/host routing + priority rule), không phải mỗi service một ALB.
- 🔎 *Cross-zone với NLB có thể phát sinh phí truyền dữ liệu giữa AZ.*

---

## 18. Troubleshooting checklist

| Triệu chứng | Nguyên nhân thường gặp | Kiểm tra / sửa |
|---|---|---|
| Task start rồi bị dừng liên tục, event "failed ELB health checks" | Sai `HealthCheckPath`/port/matcher; SG task không cho LB vào; app khởi động chậm hơn health check | `describe-target-health` xem `Reason`; sửa path/matcher; SG task mở `containerPort` từ SG LB; đặt `healthCheckGracePeriodSeconds` 🔎 |
| Target `unhealthy`, reason `Target.Timeout` | SG/NACL chặn, app bind `127.0.0.1` | App bind `0.0.0.0`; mở SG |
| `Target.ResponseCodeMismatch` | App trả 301/302/404 ở path health check | Đổi path hoặc matcher (`200-399`) |
| Tạo service lỗi "target type instance… awsvpc" | Target group type `instance` cho task `awsvpc` | Tạo lại target group với **`ip`** |
| Không có target nào ở một AZ | Subnet của LB không phủ AZ có task | Thêm subnet AZ đó vào LB |
| **Deploy rất chậm** | Deregistration delay 300 giây; health check 30 × 5; app bỏ qua SIGTERM (chờ 30 giây) | Giảm còn 5–30 giây; health check 5 × 2 (nếu app lên < 10 giây); bắt SIGTERM |
| Client lỗi 502/connection reset khi deploy | Delay quá ngắn với request dài; app tắt cứng; NLB đóng kết nối tầng 4 | Tăng deregistration delay; graceful shutdown; NLB: delay > client timeout |
| 503 ngay sau khi tạo | Chưa có target healthy nào | Đợi health check; xem service events |
| Không sửa được LB trên console | Bị giới hạn theo thiết kế | `aws ecs update-service --load-balancers ...` (controller `ECS`) |
| Hai service dùng chung target group, deploy loạn | Vi phạm "mỗi service một target group" | Tách target group |
| Deploy chậm, event báo throttling | `RegisterTargets`/`DescribeTargetHealth` bị throttle | Giãn deploy; xem `05-08-02` |
| Lỗi thiếu quyền đăng ký target | Không có service-linked role | Tạo `AWSServiceRoleForECS`; không truyền `role` |
| Service IPv6-only không đăng ký được | Target group không `dualstack` | Đổi IP address type |
| Không gắn được target group thứ 6 | Giới hạn 5 | Gộp hoặc tách service |

**Công cụ:** `aws elbv2 describe-target-health`, service events (`describe-services`), stopped reason (`describe-tasks`), 🔎 *ALB access log (S3), metric `HTTPCode_Target_5XX_Count`, `TargetResponseTime`, `UnHealthyHostCount`*, VPC Flow Logs.

---

## 19. Câu hỏi tự kiểm tra

<details>
<summary><b>1. Ba loại LB khác nhau thế nào? Khi nào AWS khuyến nghị ALB?</b></summary>

ALB tầng 7 (HTTP/HTTPS, định tuyến host/path, gRPC/WebSocket, WAF). NLB tầng 4 (TCP/UDP/TLS, flow hash, không sửa header, hàng triệu req/s, giữ IP client). GWLB tầng 3 (mọi gói IP, stickiness 5-tuple/3-tuple, cho firewall/IDS/IPS). AWS khuyến nghị **ALB mặc định**, trừ khi cần tính năng **chỉ** NLB hoặc GWLB có (UDP, TLS pass-through, appliance).
</details>

<details>
<summary><b>2. Vì sao task <code>awsvpc</code> phải dùng target type <code>ip</code>?</b></summary>

Task `awsvpc` có **ENI riêng**, không gắn với một EC2 instance (Fargate thậm chí không có instance để bạn thấy). Target phải là **IP của ENI**. Target type `ip` còn cho phép LB và task ở **khác VPC**, còn `instance` bắt buộc cùng VPC.
</details>

<details>
<summary><b>3. Dynamic host port mapping là gì, giải quyết vấn đề gì?</b></summary>

Ở mode `bridge`, đặt `hostPort: 0` thì Docker chọn host port ngẫu nhiên trong dải ephemeral (32768–61000). ECS đăng ký target là `instance ID + port động`. Nhờ vậy **nhiều task của cùng service** (cùng containerPort 80) chạy được trên **một instance** mà không đụng port. Cái giá: SG của instance phải mở cả dải ephemeral cho LB.
</details>

<details>
<summary><b>4. Target mới đăng ký cần bao nhiêu lần health check pass? Healthy threshold áp dụng khi nào?</b></summary>

Target mới chỉ cần **1 lần** pass, bất kể healthy threshold. Healthy threshold (mặc định 5) chỉ áp dụng khi target **từ unhealthy quay lại healthy**: mặc định 30 giây × 5 = 150 giây. Giảm còn 5 giây × 2 = 10 giây nếu app lên và ổn định trong dưới 10 giây.
</details>

<details>
<summary><b>5. Deregistration delay là gì? Mặc định bao nhiêu, nên đặt bao nhiêu?</b></summary>

Thời gian tối đa LB chờ kết nối (keep-alive) tới target đang bị gỡ kết thúc, trước khi target chuyển `UNUSED`. Mặc định **300 giây**. Service phản hồi < 1 giây thì đặt **5 giây** để deploy nhanh. **Không** đặt 5 giây cho upload chậm hoặc streaming. NLB: đặt **dài hơn timeout của client**.
</details>

<details>
<summary><b>6. Vì sao deploy ECS sau ALB mất 10–15 phút dù app khởi động 5 giây? Sửa thế nào?</b></summary>

Mỗi đợt thay task cộng dồn: health check (task mới cần ít nhất một interval, mặc định 30 giây) + **deregistration delay 300 giây** cho task cũ + stop timeout 30 giây nếu app bỏ qua SIGTERM. Nhiều đợt thì thành nhiều phút. Sửa:
- Giảm deregistration delay còn 5–30 giây.
- Health check interval 5, threshold 2.
- App bắt SIGTERM để thoát sớm, giảm `ECS_CONTAINER_STOP_TIMEOUT` / `stopTimeout`.
- Tăng `maximumPercent` để thay nhiều task một đợt 🔎.
</details>

<details>
<summary><b>7. Thứ tự sự kiện khi ECS gỡ một task sau ALB?</b></summary>

`DEACTIVATING`: ECS DeregisterTargets, LB ngừng gửi request mới, chờ ≤ deregistration delay tới `UNUSED`. Rồi `STOPPING`: gửi STOPSIGNAL (SIGTERM), chờ ≤ stop timeout, SIGKILL nếu chưa thoát. Rồi `DEPROVISIONING` (gỡ ENI), rồi `STOPPED`. Nhờ deregister **trước** SIGTERM, task không nhận request mới trong lúc đang tắt.
</details>

<details>
<summary><b>8. App không xử lý SIGTERM thì sao?</b></summary>

ECS phải chờ hết stop timeout (mặc định 30 giây) rồi SIGKILL. Request đang dở bị cắt cứng, deploy chậm thêm 30 giây mỗi đợt. Nên bắt SIGTERM: ngừng nhận request mới, xử lý xong request dở rồi thoát (Node `server.close()`, Spring `server.shutdown=graceful`, Go `srv.Shutdown`).
</details>

<details>
<summary><b>9. Task fail health check của target group thì ECS làm gì?</b></summary>

Service scheduler coi task **unhealthy**: khởi động task thay thế trước (nếu `maximumPercent` cho phép). Task thay healthy thì dừng task cũ. Không đủ chỗ thì dừng ngẫu nhiên từng task unhealthy trước. Lặp lại tới khi đủ `desiredCount` task healthy. Lỗi do cấu hình (sai path, SG) thì thành vòng lặp start/stop. Có circuit breaker thì deployment bị đánh dấu fail và rollback 🔎.
</details>

<details>
<summary><b>10. Một service nhận traffic cả từ nội bộ lẫn internet làm sao?</b></summary>

Hai target group cho **cùng container/port**: một gắn **internal ALB**, một gắn **internet-facing ALB**. Khai 2 phần tử trong `loadBalancers`. Điều kiện: ALB/NLB, controller `ECS`, tạo bằng API/CLI/CloudFormation, tối đa 5 target group.
</details>

<details>
<summary><b>11. Vì sao "mỗi service một target group riêng"?</b></summary>

Khi deploy, ECS đăng ký/huỷ đăng ký target theo task của **service đó**. Hai service chung target group thì health check, draining và đăng ký đan xen nhau: deploy service A có thể ảnh hưởng traffic của B, và LB chia traffic lẫn lộn giữa hai ứng dụng khác nhau. Docs cảnh báo có thể gây sự cố khi deploy.
</details>

<details>
<summary><b>12. Muốn đổi target group của service đang chạy thì làm sao?</b></summary>

Không làm được trên console. Dùng `aws ecs update-service --load-balancers ...` (hoặc SDK/CloudFormation/Copilot), **chỉ** với deployment controller `ECS`. ECS tạo deployment mới; task mới đăng ký vào target group mới, task cũ bị huỷ đăng ký. Nên thử trên môi trường test trước.
</details>

<details>
<summary><b>13. NLB khác ALB thế nào khi task bị dừng?</b></summary>

ALB hiểu HTTP nên đóng kết nối êm ở tầng ứng dụng. NLB không hiểu giao thức tầng trên nên không gửi được thông điệp đóng. Hoặc app tự đóng, hoặc bật connection termination của target group (client có thể thấy lỗi), hoặc tốt nhất là **deregistration delay > timeout client** để client tự nối lại sang task mới.
</details>

<details>
<summary><b>14. Rủi ro bảo mật của GWLB với IP target?</b></summary>

Mọi request tới target mang IP nguồn là IP private của GWLB. Để nhận request + health check, bạn mở SG cho IP đó, và vì **mọi** traffic (kể cả từ internet) đều mang IP đó, service **thực tế mở cho cả thế giới**. Phải kiểm soát ở appliance hoặc tầng khác, không dựa vào SG lọc IP nguồn.
</details>

<details>
<summary><b>15. Vì sao subnet của LB phải phủ mọi AZ có task?</b></summary>

LB chỉ định tuyến tới target healthy **trong các AZ mà LB được bật**. Task ở AZ mà LB không có subnet sẽ không nhận traffic, hoặc không đăng ký/health check được đúng. Kết quả là một phần capacity bị bỏ phí hoặc service mất HA.
</details>

<details>
<summary><b>16. Nên scale theo metric nào khi có ALB? Hạn chế gì?</b></summary>

`ALBRequestCountPerTarget` (target tracking số request mỗi task), **không hỗ trợ blue/green**. Hoặc `ActiveConnectionCount` **chia cho số replica** (dùng trung bình). Request concurrency do app tự đẩy custom metric thường là metric tốt nhất (`05-09-02`).
</details>

<details>
<summary><b>17. Deploy chậm và service event báo throttling. Vì sao?</b></summary>

ECS gọi ELB API `RegisterTargets`, `DeregisterTargets`, `DescribeTargetHealth` cho từng task. Account có nhiều service dùng LB deploy cùng lúc thì vượt rate limit. Giãn lịch deploy, giảm số service deploy đồng thời, xem hướng dẫn xử lý throttling.
</details>

<details>
<summary><b>18. Khi nào chọn API Gateway thay vì ALB trước ECS?</b></summary>

HTTP API có traffic **thấp hoặc đột biến**: API Gateway tính phí theo request (không có request thì không mất tiền), ALB tính theo giờ. Hoặc cần **auth (Lambda authorizer), usage tier, cache, sửa request/response**. API Gateway tới task qua **VPC link** và tìm IP qua **Cloud Map** (ECS Service Discovery). Tải cao ổn định thì ALB rẻ hơn mỗi request.
</details>

<details>
<summary><b>19. ECS service gọi ECS service khác có nên đi qua ALB nội bộ không?</b></summary>

Được, nhưng AWS **khuyến nghị Service Connect**: không cần LB, miễn phí, có retry, outlier detection, metric và TLS tự động. ALB nội bộ hợp lý khi **client nằm ngoài ECS** hoặc cần tính năng L7 của ALB (WAF, rule phức tạp).
</details>

<details>
<summary><b>20. Dùng ALB health check với Service Connect TLS cần chú ý gì?</b></summary>

ALB TLS listener với policy TLS 1.3; target group **HTTPS**; **health check port = container port** của Service Connect service (không phải 443); service ở `awsvpc` (bridge không hỗ trợ HTTPS health check); **không** đặt `ingressPortOverride`.
</details>

---

## 20. Bản đồ file nguồn

| Chủ đề | File |
|---|---|
| Tổng quan, chọn loại LB | `15-04-05-00-ecs-service-load-balancing.md` |
| Health check (interval, threshold) | `15-04-05-01-ecs-load-balancer-health-check-parameters.md` |
| Deregistration delay, SIGTERM | `15-04-05-02-ecs-load-balancer-connection-draining.md` |
| ALB | `15-04-05-03-ecs-application-load-balancer.md` |
| NLB | `15-04-05-04-ecs-network-load-balancer.md` |
| GWLB | `15-04-05-05-ecs-gateway-load-balancer.md` |
| Nhiều target group | `15-04-05-06-ecs-register-multiple-target-groups.md` |
| ALB / NLB / API Gateway nhận traffic từ internet | `05 - .../05-06-02-ecs-receiving-inbound-connections.md` |
| Quota, throttling ELB | `05 - .../05-08-01-ecs-service-quotas-and-api-throttling.md` |
| Scaling theo `ActiveConnectionCount` | `05 - .../05-09-02-ecs-optimizing-service-auto-scaling.md` |
| Giảm ngưỡng health check khi scale | `05 - .../05-09-03-ecs-capacity-and-availability.md` |
| Vòng đời task (ACTIVATING/DEACTIVATING) | `15-01-ecs-task-lifecycle.md`, `15-03-03-ecs-stop-task.md` |
| Thay task unhealthy | `15-04-00-ecs-services.md` |
| `ALBRequestCountPerTarget` + blue/green | `15-04-06-01-ecs-service-autoscaling-target-tracking.md` |
| IPv6 + LB | `13 - Task definitions/13-02-03-ecs-task-networking-ec2.md` |
| ALB + Service Connect TLS | `15-04-07-01-06-00-ecs-service-connect-tls.md` |

# Amazon ECS Gateway — Toàn tập

> Tổng hợp từ **toàn bộ folder `12 - Amazon ECS Gateway`**:
>
> | File | Nội dung | Vai trò chính |
> |---|---|---|
> | `12-00-ecs-gateway.md` | Tổng quan, khái niệm, hạ tầng, giá, giới hạn, lưu ý | Mọi người |
> | `12-01-ecs-gateway-managing.md` | IAM, tạo / tuỳ chỉnh / associate / update / disassociate / xoá gateway | Platform team |
> | `12-02-ecs-gateway-connect-service.md` | Gateway connector, chọn gateway, URL, migrate, ngắt kết nối | Application team |
> | `12-03-ecs-gateway-monitoring.md` | Trạng thái, hạ tầng, route, metric, log | Cả hai |
> | `12-04-ecs-gateway-troubleshooting.md` | Lỗi và cách xử lý, bảng mã lỗi | Cả hai |
> | `12-99-thuyet-trinh-cto.md` | Góc nhìn quyết định | Lãnh đạo |
>
> **Ký hiệu**
> - Không ký hiệu = lấy từ docs AWS đã dịch trong folder (mỗi mục ghi file nguồn).
> - 🔎 = kiến thức ngoài folder hoặc suy luận của mình — **nên tự kiểm tra lại** với docs AWS hiện tại.
> - ✍️ = ví dụ tự đặt (tên, giá trị, ID là placeholder).
> - ⚠️ = bẫy hay gặp.

---

## Mục lục

0. [Tóm tắt một trang](#0-tóm-tắt-một-trang)
1. [Vấn đề ECS Gateway giải quyết](#1-vấn-đề-ecs-gateway-giải-quyết)
2. [Hai vai trò: platform team và application team](#2-hai-vai-trò-platform-team-và-application-team)
3. [Bốn khái niệm cốt lõi](#3-bốn-khái-niệm-cốt-lõi)
4. [Hạ tầng gateway tạo ra và cách định tuyến](#4-hạ-tầng-gateway-tạo-ra-và-cách-định-tuyến)
5. [Vòng đời: từ gateway rỗng đến ALB thật](#5-vòng-đời-từ-gateway-rỗng-đến-alb-thật)
6. [Giá, phạm vi hỗ trợ, giới hạn](#6-giá-phạm-vi-hỗ-trợ-giới-hạn)
7. [IAM: ba danh tính, ba loại quyền](#7-iam-ba-danh-tính-ba-loại-quyền)
8. [Tạo gateway](#8-tạo-gateway)
9. [Thiết lập bất biến và thiết lập đổi được](#9-thiết-lập-bất-biến-và-thiết-lập-đổi-được)
10. [Liên kết gateway với cluster](#10-liên-kết-gateway-với-cluster)
11. [Cập nhật gateway: bẫy ghi đè toàn bộ](#11-cập-nhật-gateway-bẫy-ghi-đè-toàn-bộ)
12. [Gateway connector: phía application team](#12-gateway-connector-phía-application-team)
13. [ECS chọn gateway tương thích như thế nào](#13-ecs-chọn-gateway-tương-thích-như-thế-nào)
14. [Deployment, test traffic và progressive deployment](#14-deployment-test-traffic-và-progressive-deployment)
15. [Lấy URL và kiểm tra kết nối](#15-lấy-url-và-kiểm-tra-kết-nối)
16. [Migrate service](#16-migrate-service)
17. [Ngắt kết nối, gỡ liên kết, xoá gateway](#17-ngắt-kết-nối-gỡ-liên-kết-xoá-gateway)
18. [Trạng thái của gateway, association, route](#18-trạng-thái-của-gateway-association-route)
19. [Giám sát ba tầng](#19-giám-sát-ba-tầng)
20. [Bảo mật](#20-bảo-mật)
21. [Sức chứa và chi phí](#21-sức-chứa-và-chi-phí)
22. [Triển khai từ đầu đến cuối (CLI)](#22-triển-khai-từ-đầu-đến-cuối-cli)
23. [Troubleshooting theo triệu chứng](#23-troubleshooting-theo-triệu-chứng)
24. [Bảng tra mã lỗi](#24-bảng-tra-mã-lỗi)
25. [So sánh với cách ingress khác](#25-so-sánh-với-cách-ingress-khác)
26. [Chỗ docs không khớp nhau](#26-chỗ-docs-không-khớp-nhau)
27. [Checklist](#27-checklist)
28. [Câu hỏi tự kiểm tra (40 câu)](#28-câu-hỏi-tự-kiểm-tra-40-câu)
29. [Bản đồ nguồn](#29-bản-đồ-nguồn)

---

## 0. Tóm tắt một trang

```
 Platform team                                   Application team
 ─────────────                                   ────────────────
 create-gateway (hostSharedAlb)                  task definition: awsvpc + portMappings[].name
   scheme, subnets, IP type   ← BẤT BIẾN         update-service --gateway-connectors
   SG, WAF, desync, log, test header, maxAlb       [{portName, filters.accessTypes, healthCheck}]
 associate-gateway-with-cluster                          │
        │                                                ▼ (deploy)
        └──────────────► ECS chọn gateway hợp lệ (access type · VPC · AZ)
                              │
                              ▼
     ALB dùng chung (≤25 service) ─ listener HTTPS :443 ─ host-based routing
        └─ mỗi service: cert ACM + hostname + listener rule + target group + SG
              → https://<unique-id>.ecs.<region>.on.aws
```

| Chủ đề | Ý phải nhớ |
|---|---|
| Là gì | Ingress **HTTPS** cấu hình **một lần**, dùng chung cho nhiều service; ECS tự tạo ALB, listener, TG, SG, cert, hostname **trong account của bạn** |
| Ai làm gì | Platform: gateway + association. App: một tham số `gatewayConnectors` |
| Khi nào có tài nguyên | **Chỉ khi service đầu tiên kết nối**. Tạo gateway và associate chưa tốn tiền ALB |
| Định tuyến | Host-based; mỗi service một hostname `https://<id>.ecs.<region>.on.aws` (ngẫu nhiên, không theo tên service) |
| Sức chứa | **25 service / ALB** × `maxAlbCount` (mặc định **10**, tối đa **200**) = 250 service mặc định |
| Giới hạn | 5 gateway / cluster · 10.000 cluster / gateway · 1 connector / service |
| Chỉ hỗ trợ | HTTPS **:443**, network mode **`awsvpc`** (EC2, Fargate, Managed Instances), cùng account, không Express Mode |
| Bất biến | Name, **subnets**, IP address type, **scheme** (→ access type `PUBLIC`/`PRIVATE`), listener port/protocol |
| Chọn gateway | **Không theo tên/ARN**. Nối tới **mọi** gateway khớp access type + cùng VPC + phủ **mọi AZ** của service |
| ⚠️ Bảo mật | Service nội bộ **phải** đặt `filters.accessTypes: ["PRIVATE"]` |
| ⚠️ Update | `UpdateGateway` **ghi đè toàn bộ** — `describe-gateway` trước, truyền đủ, luôn kèm infrastructure role |
| ⚠️ Hiệu lực | Mọi thay đổi liên quan gateway chỉ tới service ở **lần deploy kế tiếp** của service đó |
| ⚠️ Health check | Với `ROLLING`, không đổi được sau khi đã kết nối — chốt ngay từ đầu |
| ⚠️ Sửa tay | Không sửa tài nguyên có tag `AmazonECSManaged=true`; ECS không tự sửa lại |
| Giám sát | 3 tầng độc lập: gateway → route → target. Metric lấy từ ALB, dimension `TargetGroup` cho từng service |
| Giá | Gateway miễn phí; trả cho ALB, CloudWatch, data transfer |

---

## 1. Vấn đề ECS Gateway giải quyết

> Nguồn: `12-00` (mục 1, 3), `12-99` (mục 0).

Muốn expose một service ra ngoài qua HTTPS theo cách truyền thống, mỗi service phải tự dựng cả chuỗi:

```
ALB → listener HTTPS → certificate ACM → listener rule → target group → SG cho ALB → SG cho service
```

Thêm service mới thì lặp lại phần lớn công việc đó, và người deploy phải hiểu networking lẫn có quyền tạo load balancer.

ECS Gateway **gom phần dùng chung** (ALB, listener, SG của ALB) về một nơi do platform team cấu hình một lần; **phần riêng của từng service** (target group, rule, certificate, hostname, SG service) thì ECS tự tạo khi service "opt in".

```
Trước:   Service A ─ ALB A + listener + TG + SG + cert
         Service B ─ ALB B + listener + TG + SG + cert

Gateway:                ┌─ rule + TG + cert + hostname ─► Service A
  Gateway ─ ALB chung ──┼─ rule + TG + cert + hostname ─► Service B
  (cấu hình 1 lần)      └─ rule + TG + cert + hostname ─► Service C
```

| Lợi ích (theo docs) | Ý nghĩa |
|---|---|
| **Cấu hình tập trung** | Mạng, bảo mật, quản lý traffic khai báo một lần; mọi service — kể cả ở nhiều cluster — thừa hưởng. App team không cần chuyên môn networking |
| **Tự quản lý hạ tầng** | ECS tạo và quản lý ALB, listener, TG, SG, certificate, DNS hostname |
| **Tối ưu chi phí** | Nhiều service dùng chung ALB (tối đa 25) → ít ALB tính phí theo giờ |
| **Minh bạch** | Mọi tài nguyên nằm trong account của bạn, xem được bằng console/API (nhưng **không nên sửa**) |

---

## 2. Hai vai trò: platform team và application team

> Nguồn: `12-00` (mục 2.1), `12-01` (mục 0, 1.1), `12-02` (mục 0).

Gateway **tách quản lý hạ tầng khỏi triển khai ứng dụng**.

| | Platform team — sở hữu gateway | Application team — đưa service vào |
|---|---|---|
| Làm việc với | Gateway, association với cluster | `gatewayConnectors` trên service |
| **Networking** | Public hay internal; subnet nào | — |
| **Security** | Inbound SG; AWS WAF web ACL; HTTP desync | Bộ lọc access type |
| **Operations** | Access log; số ALB tối đa; header test traffic | Health check |
| Thứ phải chỉ ra | Toàn bộ cấu hình gateway | **Tên container port** nhận traffic |
| Quyền IAM | Thao tác gateway + `iam:PassRole` cho infrastructure role | **Không cần** PassRole, quyền gateway hay quyền tạo LB |
| Cần hiểu networking? | Có | Không |

Cấu hình gateway **áp cho mọi service** kết nối vào nó. Một số thiết lập **không đổi được** sau khi tạo (mục 9).

---

## 3. Bốn khái niệm cốt lõi

> Nguồn: `12-00` (mục 4).

| Khái niệm | Bản chất | Điểm phải nhớ |
|---|---|---|
| **Gateway** | Tài nguyên cấp **account**, có ARN | Định nghĩa cấu hình ingress, quản lý hạ tầng bên dưới. Tên **duy nhất trong account + Region**. Kiểu hiện có: `hostSharedAlb` |
| **Gateway-cluster association** | Liên kết gateway ↔ cluster | **Nhiều–nhiều**. Cluster ≤ 5 gateway; gateway ≤ 10.000 cluster. Trạng thái `ACTIVE` / `DISASSOCIATING` |
| **Gateway connector** | Tham số opt-in trên **service** | Tối đa **1** mỗi service. Kết nối tới **mọi gateway tương thích** của cluster. Chỉ định `portName`, tuỳ chọn access type + health check |
| **Gateway route** | Tài nguyên có ARN — **một kết nối** service ↔ gateway | ECS tạo khi service kết nối, xoá khi ngắt. Chứa trạng thái, URL, tài nguyên riêng của service |

```
Gateway ◄──(association, N–N)──► Cluster
                                   │
                                   └── Service (≤ 1 gateway connector)
                                          │
              Gateway route = 1 cặp (service, gateway)
              → service khớp 2 gateway ⇒ 2 route ⇒ 2 URL
```

⚠️ "Một connector" **không** có nghĩa "một gateway". Một connector có thể sinh nhiều route.

---

## 4. Hạ tầng gateway tạo ra và cách định tuyến

> Nguồn: `12-00` (mục 5), `12-03` (mục 3.2–3.3).

### 4.1. Hai nhóm tài nguyên

| Nhóm | Tài nguyên | Ai dùng |
|---|---|---|
| **Dùng chung** | Application Load Balancer, listener (HTTPS :443), SG của load balancer | Nhiều service (≤ 25 / ALB) |
| **Riêng từng service** (mỗi gateway route) | Certificate ACM, DNS hostname, listener rule (production + test), target group (production + alternate), SG của service | Một route |

### 4.2. Định tuyến

- **Host-based routing**: ALB dùng chung chuyển request tới đúng service theo **Host header**.
- Hostname tự sinh: `https://<unique-id>.ecs.<region>.on.aws` — domain khác nhau theo AWS partition.
- Vì định tuyến theo hostname nên mỗi service cần **certificate riêng** → certificate thuộc nhóm "riêng".

```
Client ── HTTPS :443 ──► ALB dùng chung (listener HTTPS + certificate)
                           ├─ Host = abc123.ecs.<region>.on.aws
                           │     ├─ rule production ─► TG production ─► task revision hiện tại
                           │     └─ rule test header ─► TG alternate  ─► task revision mới (lúc deploy)
                           ├─ Host = def456.ecs.<region>.on.aws ─► service B
                           └─ … (≤ 25 service / ALB)
```

### 4.3. Tag

| Tag | Gắn lên | Dùng để |
|---|---|---|
| `AmazonECSManaged=true` | Mọi tài nguyên ECS quản lý | ECS nhận diện tài nguyên của nó. **Sửa/xoá tag → ECS không cập nhật, không dọn dẹp được** |
| `aws:ecs:gateway-arn` (system tag) | Mọi tài nguyên quản lý | Tìm tài nguyên của một gateway; chọn tài nguyên cho alarm/dashboard |
| `aws:ecs:gateway-route-arn` | TG, listener rule, SG service (tài nguyên per-route) | Tìm tài nguyên của một service cụ thể |
| Tag của gateway | Hạ tầng gateway quản lý (tag propagation mặc định `GATEWAY`) | Đặt `NONE` để tag chỉ nằm trên gateway |

🔎 Tìm ALB của gateway bằng tag:

```bash
aws resourcegroupstaggingapi get-resources \
  --resource-type-filters elasticloadbalancing:loadbalancer \
  --tag-filters Key=aws:ecs:gateway-arn,Values=<ARN_GATEWAY>
```

---

## 5. Vòng đời: từ gateway rỗng đến ALB thật

> Nguồn: `12-00` (mục 2.2, 5), `12-01` (mục 0, 2, 6.6), `12-02` (mục 5).

```
[Platform] create-gateway        → ACTIVE, KHÔNG có tài nguyên nào
[Platform] associate với cluster → vẫn không có gì, không đổi service đang chạy
[App]      service + connector   → deployment:
              ├─ chọn gateway hợp lệ
              ├─ service ĐẦU TIÊN: tạo ALB + listener + SG dùng chung   (lâu hơn)
              ├─ service sau: dùng lại ALB còn chỗ, hoặc tạo ALB mới (≤ maxAlbCount)
              ├─ tạo cert, hostname, rule, TG, SG riêng
              └─ tạo gateway route → URL HTTPS
[App]      ngắt kết nối          → xoá route + tài nguyên riêng
                                   ALB trống hẳn → ECS xoá ALB
```

Quy tắc lấp ALB: ECS lấp một ALB đến 25 service rồi mới tạo ALB kế tiếp, tới `maxAlbCount`.

⚠️ **ECS không gom (consolidate) ALB**: không di chuyển service giữa các ALB. Sau khi nhiều service rời đi, có thể còn nhiều ALB chỉ đầy một phần — vẫn tính phí.

---

## 6. Giá, phạm vi hỗ trợ, giới hạn

> Nguồn: `12-00` (mục 6–9).

### 6.1. Giá

Gateway **không mất phí**. Trả cho tài nguyên tạo ra khi service kết nối: **ALB**, **log và metric CloudWatch**, **data transfer**.

### 6.2. Phạm vi hỗ trợ

- **Mọi commercial Region**.
- Service network mode **`awsvpc`** trên **EC2, Fargate, Managed Instances**.
- Quản lý qua **ECS console, AWS CLI, AWS SDK, CloudFormation**. (🔎 Docs không nhắc Terraform — kiểm tra AWS provider.)

### 6.3. Giới hạn

| Giới hạn | Giá trị |
|---|---|
| Gateway-cluster association mỗi cluster | **5** |
| Cluster mỗi gateway | **10.000** |
| Gateway connector mỗi service | **1** |
| Service mỗi ALB | **25** (cố định khi dùng host-based routing, không đổi được) |
| ALB mỗi gateway | **200** (giới hạn trên của `maxAlbCount`, mặc định 10) |

Tài nguyên bên dưới còn chịu quota riêng của ELB, ACM, EC2 (mục 21, 24).

### 6.4. Những điều cần cân nhắc

| # | Lưu ý | Hệ quả |
|---|---|---|
| 1 | Chỉ **HTTPS :443** | Không dùng cho TCP/UDP thuần, HTTP không mã hoá, port khác |
| 2 | Chỉ **`awsvpc`** | Service `bridge`/`host`/`none` (thường gặp trên EC2) phải chuyển sang `awsvpc` trước |
| 3 | **Một account** | Gateway và cluster cùng account; multi-account cần gateway ở từng account |
| 4 | Không cho **Express Mode** service | Chỉ service ECS thông thường |
| 5 | **1 connector / service** | Nhưng 1 connector có thể nối nhiều gateway |
| 6 | **Không chọn gateway theo tên** | Do association + VPC + AZ + access type quyết định |
| 7 | Nhiều thiết lập **bất biến** | Đổi = tạo gateway mới và migrate |
| 8 | **Không gom ALB** | Theo dõi số ALB để kiểm soát chi phí |
| 9 | **Không sửa lại thay đổi ngoài ECS** | Sửa tay tài nguyên quản lý → traffic có thể dừng, deploy/update/cleanup về sau lỗi |

---

## 7. IAM: ba danh tính, ba loại quyền

> Nguồn: `12-01` (mục 1), `12-04` (mục 4).

```
               (1) iam:PassRole trên ĐÚNG ARN role
Người gọi API ───────────────────────────────► ECS
(platform team)                                 │ (2) sts:AssumeRole — trust policy cho ecs.amazonaws.com
                                                ▼
                                     Infrastructure role
                                                │ (3) quyền ELB / EC2 / ACM
                                                │     managed policy AmazonECSInfrastructureRoleForGatewayHostSharedALB
                                                ▼
                              ALB, listener, TG, SG, certificate
```

### 7.1. Infrastructure role

```bash
aws iam create-role --role-name ecsGatewayInfraRole \
  --assume-role-policy-document file://trust-ecs.json

aws iam attach-role-policy --role-name ecsGatewayInfraRole \
  --policy-arn arn:aws:iam::aws:policy/AmazonECSInfrastructureRoleForGatewayHostSharedALB
```

`trust-ecs.json`:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    { "Effect": "Allow", "Principal": { "Service": "ecs.amazonaws.com" }, "Action": "sts:AssumeRole" }
  ]
}
```

### 7.2. Quyền của platform team

```json
{
  "Version": "2012-10-17",
  "Statement": [
    { "Effect": "Allow", "Action": "iam:PassRole", "Resource": "arn:aws:iam::<ACCOUNT_ID>:role/ecsGatewayInfraRole" }
  ]
}
```

Docs khuyên giới hạn `iam:PassRole` vào **đúng ARN** của infrastructure role. 🔎 Cộng thêm quyền các action `ecs:CreateGateway`, `ecs:UpdateGateway`, `ecs:AssociateGatewayWithCluster`… (tên action theo tên API; kiểm tra trang *Actions, resources, and condition keys for Amazon ECS*).

### 7.3. Bảng sửa lỗi theo danh tính

| Triệu chứng | Danh tính lỗi | Sửa ở đâu |
|---|---|---|
| `CreateGateway`/`UpdateGateway` lỗi `iam:PassRole` (lỗi **phổ biến nhất lần đầu**) | Người gọi | Policy người gọi; kiểm tra permissions boundary, SCP, explicit deny |
| "infrastructureRoleArn không assume được" | Trust policy / IAM chưa lan truyền | Sửa trust policy; **chờ ~1 phút** sau khi tạo role; role cùng account + partition |
| `AWS STS is not activated for this account in a Region` | STS | Bật STS ở Region đó |
| `AccessDenied` cho ELB / EC2 / ACM khi tạo route hoặc update | Infrastructure role | Gắn managed policy; nếu dùng policy tự viết, đối chiếu đúng action trong lỗi |

⚠️ Lỗi ở danh tính này **không sửa được** bằng cách đổi danh tính kia (vd thêm quyền cho infrastructure role không giải quyết việc người gọi thiếu PassRole).

⚠️ Access log **không** dùng infrastructure role — quyền ghi S3 nằm ở **bucket policy** cho Elastic Load Balancing (mục 9.2).

---

## 8. Tạo gateway

> Nguồn: `12-01` (mục 2).

**Điều kiện tiên quyết**: infrastructure role; **default VPC** trong Region, hoặc tự chỉ định subnet.

**Bắt buộc**: `name` duy nhất, ARN infrastructure role, object `hostSharedAlb`. Mọi thiết lập bên trong `hostSharedAlb` đều tuỳ chọn.

```bash
aws ecs create-gateway \
  --name public-gw \
  --infrastructure-role-arn arn:aws:iam::<ACCOUNT_ID>:role/ecsGatewayInfraRole \
  --configuration '{
    "hostSharedAlb": {
      "scheme": "internet-facing",
      "networkConfiguration": { "subnets": ["<SUBNET_AZ_A>", "<SUBNET_AZ_B>", "<SUBNET_AZ_C>"] }
    }
  }'
```

- `CreateGateway` **đồng bộ**, trả `gatewayArn`, `name`, `status: ACTIVE`.
- **Chưa** tạo hạ tầng nào.
- Kiểm tra: `aws ecs describe-gateway --gateway public-gw`.

⚠️ Dạng tối giản `{"hostSharedAlb": {}}` dùng **subnet của default VPC** và scheme **`internet-facing`** — cả hai **bất biến**. Chỉ dùng để thử.

⚠️ Subnet phải ở **ít nhất 2 AZ** (mức tối thiểu của ALB), nếu không route sẽ lỗi `InsufficientAvailabilityZones` (`12-04`). Vì subnet bất biến, sửa nghĩa là tạo gateway mới.

---

## 9. Thiết lập bất biến và thiết lập đổi được

> Nguồn: `12-01` (mục 3, 6), `12-00` (mục 9.7).

Tên trường và giá trị trùng với **ELB API** khi có thiết lập tương đương.

### 9.1. Bất biến — chốt ngay từ đầu

| Thiết lập | Mặc định | Vì sao quan trọng |
|---|---|---|
| **Name** | — | Duy nhất trong account, ≤ 255 ký tự |
| **Subnets** | Subnet của default VPC | Quyết định gateway phủ **VPC nào, AZ nào** → service nào dùng được. Service không ở default VPC → phải đặt subnet |
| **IP address type** | `dualstack` | `ipv4` / `dualstack` / `dualstack-without-public-ipv4` |
| **Scheme** | `internet-facing` | `internet-facing` → access type **`PUBLIC`**; `internal` → **`PRIVATE`** |
| Listener port / protocol | HTTPS / 443 | Không hỗ trợ giá trị khác |

Muốn đổi: tạo gateway mới → associate → chuyển service → disassociate và xoá gateway cũ.

🔎 Thiết kế gợi ý: chọn subnet **phủ mọi AZ mà service có thể chạy** (docs `12-04` khuyên), và nếu cần cả public lẫn internal thì tạo **hai gateway** khác scheme.

### 9.2. Đổi được bất kỳ lúc nào

| Thiết lập | Trường (trong `hostSharedAlb` trừ khi ghi khác) | Mặc định | Ghi chú |
|---|---|---|---|
| **Inbound SG** | `networkConfiguration.inboundSecurityGroup` | ECS tự tạo SG, mở **443 từ mọi IP** | Cung cấp SG của bạn → ECS gắn vào ALB, **xoá rule nó từng quản lý, ngừng quản lý**. Là thiết lập mạng **duy nhất** đổi được. Đổi rule ảnh hưởng **mọi service** trên ALB |
| **Max ALB** | `maxAlbCount` | **10** | Nâng **trước khi cần** |
| **WAF** | `webACLArn` | Không có | **Regional** web ACL, cùng Region; áp mọi ALB |
| **HTTP desync** | `httpDesync` | `defensive` | `monitor` (cho qua, ghi lại) / `defensive` / `strictest` (từ chối mọi request không tuân thủ). Muốn `strictest` thì chạy `monitor` + access log trước |
| **Access log** | `accessLogs.destination.s3.{bucket,prefix}` | Tắt | Cung cấp bucket = bật. ECS **không tạo bucket**. Bucket cùng Region, **bucket policy** cho ELB ghi. Một bucket nhận log **mọi service** → lọc theo hostname |
| **Test traffic rules** | `testTrafficRules.header.{name,values}` | Không có | Chỉ khớp **header**, không chia %. Áp ở **lần deploy kế tiếp** của từng service |
| **Deletion protection** | `deletionProtection` (top level) | **Bật** | Lan xuống các ALB. Phải tắt mới xoá được gateway |
| **Tag propagation** | `propagateTags` (top level) | `GATEWAY` | `NONE` để tag chỉ ở gateway |

✍️ Một cấu hình đầy đủ:

```json
{
  "hostSharedAlb": {
    "networkConfiguration": { "inboundSecurityGroup": "sg-0abc1234" },
    "webACLArn": "arn:aws:wafv2:<REGION>:<ACCOUNT_ID>:regional/webacl/edge-acl/<ID>",
    "httpDesync": "defensive",
    "accessLogs": { "destination": { "s3": { "bucket": "my-alb-logs", "prefix": "gateway/public" } } },
    "testTrafficRules": { "header": { "name": "x-test-traffic", "values": ["canary"] } },
    "maxAlbCount": 25
  }
}
```

---

## 10. Liên kết gateway với cluster

> Nguồn: `12-01` (mục 4).

```bash
aws ecs associate-gateway-with-cluster --gateway public-gw --cluster prod-cluster
aws ecs list-gateway-cluster-associations --gateway public-gw
aws ecs list-gateway-cluster-associations --cluster prod-cluster   # cluster đang có gateway nào
```

- Cùng **Region và account**.
- Association **không định tuyến traffic** và **không đổi** service hiện có. Service dùng gateway ở **lần deploy kế tiếp**, nếu có connector.
- Thêm gateway thứ hai vào cluster cũng **không tự kết nối** service đang chạy.
- Chỉ quản lý qua `AssociateGatewayWithCluster` / `DisassociateGatewayFromCluster`.
- `list-gateway-cluster-associations`: lọc theo gateway, cluster hoặc trạng thái; phân trang, mặc định 100 mục/trang.

| Trạng thái association | Ý nghĩa |
|---|---|
| `ACTIVE` | Dùng được cho kết nối mới |
| `DISASSOCIATING` | Đang gỡ: không nhận kết nối mới; route cũ vẫn phục vụ tới khi bị xoá |

⚠️ Thêm một gateway **internet-facing** vào cluster là hành động **có hệ quả bảo mật**: mọi service có connector **không lọc access type** sẽ bị expose ra internet ở lần deploy kế tiếp (mục 12.2).

---

## 11. Cập nhật gateway: bẫy ghi đè toàn bộ

> Nguồn: `12-01` (mục 5).

⚠️ **`UpdateGateway` thay thế toàn bộ cấu hình** (kiểu PUT, không phải PATCH). Thiết lập bị bỏ sót = bị **xoá** hoặc **về mặc định**.

### 11.1. Bỏ sót thì sao

| Thiết lập | Vị trí | Nếu bỏ sót |
|---|---|---|
| `infrastructureRoleArn` | Top level | **Request lỗi** (bắt buộc) |
| `configuration.hostSharedAlb` | Top level | **Request lỗi** (bắt buộc) |
| `deletionProtection` | Top level | Về `true` |
| `propagateTags` | Top level | Về `GATEWAY` |
| `networkConfiguration.inboundSecurityGroup` | `hostSharedAlb` | Bị gỡ → ECS lại tự quản lý (mở 443 mọi IP) |
| `webACLArn` | `hostSharedAlb` | Bị gỡ → **mất WAF** |
| `httpDesync` | `hostSharedAlb` | Về `defensive` |
| `accessLogs` | `hostSharedAlb` | Bị gỡ → tắt log |
| `testTrafficRules` | `hostSharedAlb` | Bị gỡ |
| `maxAlbCount` | `hostSharedAlb` | Về `10` |

**Không đưa** vào request: `name`, `scheme`, `protocol`, `port`, `subnets`, `ipAddressType` (bất biến) và `currentAlbCount` (giá trị báo cáo).

### 11.2. Quy trình an toàn

```bash
# 1) Lấy cấu hình hiện tại
aws ecs describe-gateway --gateway public-gw > current.json

# 2) Dựng configuration mới = cấu hình hiện tại (bỏ trường bất biến) + thay đổi
# 3) Cập nhật, luôn kèm role
aws ecs update-gateway --gateway public-gw \
  --infrastructure-role-arn arn:aws:iam::<ACCOUNT_ID>:role/ecsGatewayInfraRole \
  --configuration file://new-config.json

# 4) Theo dõi
aws ecs describe-gateway --gateway public-gw --query 'gateway.{name:name,status:status,reason:statusReason}'
```

✍️🔎 Dựng `new-config.json` bằng `jq` từ output (giả sử output có dạng `gateway.configuration.hostSharedAlb`, như ví dụ trong `12-01`):

```bash
jq '.gateway.configuration
    | .hostSharedAlb |= (del(.scheme, .protocol, .port, .currentAlbCount)
                         | .networkConfiguration |= del(.subnets, .ipAddressType)
                         | .maxAlbCount = 25)' current.json > new-config.json
```

`deletionProtection` và `propagateTags` là tham số top level — truyền lại bằng flag CLI tương ứng (🔎 kiểm tra tên flag bằng `aws ecs update-gateway help`).

### 11.3. Tiến trình

```
update-gateway ─► UPDATE_IN_PROGRESS ─(rolling từng ALB, bất đồng bộ)─► UPDATE_COMPLETE
                                                         └──────────► UPDATE_FAILED
```

- Trong lúc chạy, các ALB mang **lẫn cấu hình cũ và mới**.
- ECS **không dừng ở lỗi đầu tiên** → có thể thành công một phần. **Không rollback**.
- `statusReason` dạng "12/12 load balancers updated" hoặc "10/12 updated, 2 failed update".
- `failures`: từng tài nguyên lỗi, thao tác, mã lỗi, thông điệp — tối đa **200 tài nguyên × 5 lỗi**.
- Sửa nguyên nhân (thiếu quyền, ARN WAF sai, SG không tồn tại, tài nguyên bị sửa bên ngoài) → chạy lại `update-gateway`.

⚠️ Chưa thấy `UPDATE_COMPLETE` thì **đừng giả định** mọi ALB đã có cấu hình mới. Ví dụ đổi WAF mà chỉ 10/12 ALB nhận → service trên 2 ALB còn lại được bảo vệ khác.

🔎 Vì bẫy ghi đè, nên quản lý gateway bằng **IaC** (CloudFormation) — luôn gửi đủ trạng thái mong muốn.

---

## 12. Gateway connector: phía application team

> Nguồn: `12-02` (mục 1–3).

### 12.1. Điều kiện

```
[ ] Cluster có gateway association ACTIVE (không có → ECS TỪ CHỐI request ngay)
[ ] Task definition network mode awsvpc
[ ] Container có portMappings với "name" (named port)
[ ] Ít nhất một gateway tương thích: cùng VPC, khớp access type, phủ mọi AZ của service
```

✍️ Named port:

```json
"portMappings": [
  { "name": "web", "containerPort": 8080, "protocol": "tcp", "appProtocol": "http" }
]
```

### 12.2. Cấu trúc connector

```json
[{
  "portName": "web",
  "filters": { "accessTypes": ["PRIVATE"] },
  "healthCheck": { "healthCheckPath": "/health" }
}]
```

| Trường | Bắt buộc | Ý nghĩa |
|---|---|---|
| `portName` | **Có** | Tên named port. ECS lấy **số port, protocol, protocol version** để cấu hình target group. Không khớp → `InvalidConfiguration: "portName not found in task definition portMappings"` |
| `filters.accessTypes` | Không | `PUBLIC`, `PRIVATE`, hoặc cả hai; **bỏ trống = cả hai**. Chỉ dùng để **chọn gateway**, không tạo traffic/security rule |
| `healthCheck` | Không | Health check trên target group của **mỗi route** |

| `filters.accessTypes` | Service nối tới |
|---|---|
| (trống) | Mọi gateway hợp lệ, public lẫn private |
| `["PUBLIC"]` | Chỉ gateway internet-facing |
| `["PRIVATE"]` | Chỉ gateway internal (chỉ tới được từ mạng vươn tới internal ALB) |
| `["PUBLIC","PRIVATE"]` | Như trống |

⚠️ **Service không bao giờ được lộ ra internet thì phải đặt `PRIVATE`.** Bỏ trống → service phụ thuộc vào việc platform team gắn gateway gì vào cluster trong tương lai.

Thu hẹp bộ lọc có hiệu lực ở deployment nó khởi động: service bỏ kết nối gateway không còn khớp, **URL của các kết nối đó ngừng hoạt động**.

### 12.3. Health check

| | Mặc định |
|---|---|
| Request | HTTP tới `/` trên port của `portName` |
| Chu kỳ | **30 giây** |
| Timeout | **5 giây**, chờ `200` |

- Service trả `200` ở `/` thì không cần cấu hình.
- Nên đổi nếu `/` chậm hoặc nặng (query DB, render trang): health check lặp lại mỗi 30 s cho từng target, và chậm quá timeout làm target khoẻ bị đánh dấu unhealthy → dùng `/health` nhẹ.
- Các thiết lập khác: theo *Health checks for ALB target groups*.

⚠️ **Đổi health check = thay target group** → **không làm được với deployment strategy `ROLLING`** (lỗi "thuộc tính target group không đổi được với ROLLING"). Lựa chọn:

| Cách | Hệ quả |
|---|---|
| **Đặt health check ngay lần đầu** kết nối | Tốt nhất |
| Dùng strategy `BLUE_GREEN` hoặc `CANARY` | Đổi được |
| Giữ nguyên | Không đổi gì |
| Ngắt rồi nối lại connector | Đổi được nhưng **route + hostname bị xoá và tạo lại → URL mới** |

---

## 13. ECS chọn gateway tương thích như thế nào

> Nguồn: `12-02` (mục 4), `12-04` (mục 1.2), `12-00` (mục 9.6).

Mỗi lần deploy, ECS xét các gateway đã associate (≤ 5) theo **ba tiêu chí**:

| Tiêu chí | Hợp lệ khi |
|---|---|
| **Access type** | Khớp `filters.accessTypes` (trống = cả hai) |
| **VPC** | Gateway cùng VPC với subnet của service |
| **Availability Zone** | Gateway có subnet ở **mọi AZ** mà subnet service dùng (subnet ID không cần trùng). Phủ **một phần** → không hợp lệ |

```
ứng_viên = gateway associate ACTIVE với cluster (≤ 5)
hợp_lệ   = [g for g in ứng_viên
            if g.accessType ∈ filter (hoặc filter trống)
            and g.vpc == service.vpc
            and service.AZs ⊆ g.AZs]
hợp_lệ rỗng → request FAIL
ngược lại   → 1 route cho MỖI g trong hợp_lệ
```

- Gateway không hợp lệ bị **bỏ qua**, không làm deploy fail — miễn còn ít nhất một cái hợp lệ.
- **Vì sao AZ phải phủ đủ**: task ở AZ gateway không phủ sẽ **không bao giờ qua health check** → ECS thay task liên tục.

Ví dụ của docs:

| | AZ | Hợp lệ với S1? |
|---|---|---|
| GW1 | 2a, 2b, 2c | **Có** |
| GW2 | 2a, 2b | **Không** — thiếu 2c |
| S1 | 2b, 2c | |

⚠️ Connector **không chọn theo tên/ARN**. Muốn service nối vào một gateway cụ thể: cấu hình gateway đó phủ mọi AZ của service (gateway phủ **rộng hơn** service là cấu hình **an toàn hơn**), và điều khiển bằng association + access type. Đổi association cần phối hợp platform team vì ảnh hưởng nhiều service.

⚠️ Không thể dùng bộ lọc để bỏ **một** gateway trong khi giữ gateway khác **cùng access type**.

---

## 14. Deployment, test traffic và progressive deployment

> Nguồn: `12-02` (mục 5), `12-01` (mục 6.5), `12-03` (mục 3.2–3.3).

### 14.1. Quy tắc "chỉ có hiệu lực qua deploy"

| Sự kiện | Khi nào service bị ảnh hưởng |
|---|---|
| Thêm / sửa / xoá connector | Deployment do chính lệnh đó khởi động |
| Platform associate / disassociate gateway | Lần deploy kế tiếp của service |
| Platform đổi header test traffic | Lần deploy kế tiếp của service |
| Platform nâng `maxAlbCount` sau khi deploy đã fail | **Không tự chạy lại** — chủ service phải redeploy |
| Inbound SG, WAF, desync, access log | Áp lên ALB khi update gateway chạy (không cần deploy service) |

ECS **không bao giờ tự redeploy** service vì thay đổi phía gateway.

Kết nối **đầu tiên** vào một gateway có thể lâu hơn vì phải tạo ALB dùng chung. Mỗi lần deploy, ECS **đánh giá lại** gateway hợp lệ: tạo route tới gateway mới hợp lệ, xoá route không còn khớp.

### 14.2. Test traffic

- Header + giá trị đặt **trên gateway** → mọi service sau gateway dùng **chung một header**. App team phải **hỏi platform team** header là gì.
- Trong progressive deployment (blue/green, canary): request mang header → **revision mới** (đăng ký vào **alternate target group**); request khác → revision hiện tại (**production target group**).
- Ngoài lúc deploy, request mang header vẫn tới production.
- Chỉ khớp header; **không chia theo %** ở rule này.

### 14.3. Đổi header test

- ECS **không sửa** điều kiện khớp của rule có sẵn; đổi header tạo **rule mới** cho revision mới.
- Deployment thất bại chỉ xoá rule **nó tạo**, giữ rule đang phục vụ revision hiện tại.
- Vì vậy một gateway có thể có service khớp header cũ và service khớp header mới **cùng lúc**, tới khi từng service redeploy. Route có nhiều hơn một test listener rule chỉ trong giai đoạn chuyển tiếp này.
- Quá nhiều test rule trên một service → `TestRuleLimitExceeded`.

⚠️ Đổi header là việc **phải thông báo cho mọi app team**.

---

## 15. Lấy URL và kiểm tra kết nối

> Nguồn: `12-02` (mục 6), `12-03` (mục 3.3).

```bash
# 1) ARN revision mà deployment đang chuyển tới
aws ecs list-service-deployments --cluster prod-cluster --service orders \
  --query 'serviceDeployments[0].targetServiceRevisionArn' --output text
# (hoặc describe-service-deployments → targetServiceRevision.arn)

# 2) Gateway connections của revision đó
aws ecs describe-service-revisions --service-revision-arns <ARN_SERVICE_REVISION> \
  --query 'serviceRevisions[0].ecsManagedResources.gatewayConnections'
```

Mỗi connection có:

| Trường | Ý nghĩa |
|---|---|
| `serviceUrl` | Hostname HTTPS |
| `accessType` | Public hay private |
| `status` | = trạng thái của gateway route |
| Gateway, route, tên port | Định danh kết nối |
| Tài nguyên | Certificate, ALB + listener, listener rule production + test, SG của ALB, **TG production + alternate** |

| `status` | Làm gì |
|---|---|
| `CREATE_IN_PROGRESS` | Chờ, chạy lại lệnh |
| `ACTIVE` | Dùng được URL |
| `CREATE_FAILED` | **Đã thất bại** — chờ thêm vô ích, đi xử lý lỗi (mục 23) |

- Service nối 2 gateway → **2 connection** cần kiểm tra.
- Trong lúc deploy, nhiều revision cùng tồn tại, mỗi revision có connection riêng.
- Thông tin gateway nằm trong **service revision** vì connector có thể đổi giữa các lần deploy; so hai revision để biết ingress đã đổi gì.

⚠️ Hostname **ngẫu nhiên**, không suy ra từ tên service. **URL đổi** khi:
- xoá service rồi tạo lại cùng tên;
- ngắt rồi nối lại connector;
- tài nguyên bị xoá bên ngoài ECS và phải tái tạo (mục 23);
- service redeploy trong lúc association `DISASSOCIATING`, hoặc association bị tạo lại (mục 17).

🔎 Vì vậy không nên hard-code `*.on.aws` khắp nơi. Docs trong folder không nói về custom domain; nếu cần tên miền riêng, phải tự tìm hiểu thêm (một CNAME trỏ thẳng tới hostname gateway có thể gặp vấn đề certificate, vì cert do ECS cấp cho hostname `*.on.aws` — cần kiểm tra docs mới nhất).

---

## 16. Migrate service

> Nguồn: `12-02` (mục 7).

### 16.1. Từ ALB tự quản lý sang gateway (không downtime)

```
GĐ 1: [ALB riêng] ──► service
GĐ 2: [ALB riêng] ──► service ◄── [Gateway URL]   ← thêm connector; ECS KHÔNG đụng LB cũ
GĐ 3:                 service ◄── [Gateway URL]   ← kiểm tra xong, gỡ ALB riêng ở lần update sau
```

### 16.2. Từ gateway này sang gateway khác

Connector không chỉ định ARN → migrate qua **association** và **access type**:

1. **Platform**: tạo gateway đích + associate. Xác nhận access type, VPC khớp, subnet phủ **mọi AZ** của task.
2. **App**: redeploy → có thể có 2 route (cũ + mới) song song.
3. **App**: chờ connection mới `ACTIVE` → đổi client sang hostname mới.
4. **Platform**: disassociate gateway cũ → **App**: redeploy → route cũ bị xoá, **hostname cũ chết**.

⚠️ Disassociate ảnh hưởng **mọi service** dùng association đó — lên kế hoạch cùng các app team.

Đây cũng là cách duy nhất để "đổi" thiết lập bất biến (scheme, subnet…).

---

## 17. Ngắt kết nối, gỡ liên kết, xoá gateway

> Nguồn: `12-02` (mục 8), `12-01` (mục 7–8), `12-04` (mục 7).

### 17.1. App: ngắt kết nối service

```bash
aws ecs update-service --cluster prod-cluster --service orders --gateway-connectors '[]'
```

| Muốn | Cách |
|---|---|
| Ngắt khỏi mọi gateway | `--gateway-connectors '[]'` |
| Bỏ gateway public, giữ private | `filters.accessTypes: ["PRIVATE"]` |
| Bỏ đúng một gateway, giữ gateway khác cùng access type | **Không làm được** bằng connector → nhờ platform team |

- Có hiệu lực ở deployment nó khởi động; service vẫn phục vụ qua gateway tới khi deployment xong.
- ECS xoá route + tài nguyên riêng; ALB trống hẳn → xoá ALB. Gateway và association vẫn còn.
- Xoá service cũng xoá các kết nối gateway của nó.
- ⚠️ Đảm bảo service có **đường ingress khác** nếu vẫn cần truy cập.

### 17.2. Platform: gỡ liên kết

```bash
aws ecs disassociate-gateway-from-cluster --gateway public-gw --cluster prod-cluster
aws ecs list-gateway-routes --gateway public-gw --cluster prod-cluster   # route còn giữ association
```

Trong `DISASSOCIATING`:
- service **mới** không kết nối được;
- service **hiện có** vẫn nhận traffic;
- service ngắt khi **update/redeploy** (ECS không tự redeploy);
- cluster không còn gateway nào khác → app team nên **gỡ connector** trước khi redeploy (nếu không, `update-service` sẽ bị từ chối — mục 23);
- route cuối cùng bị xoá → association bị xoá.

`DISASSOCIATING` kéo dài **không phải lỗi** — chỉ là còn service chưa redeploy.

**Huỷ việc gỡ**: associate lại cùng cluster khi đang `DISASSOCIATING` → về `ACTIVE`.

| Service trong lúc `DISASSOCIATING` | Sau khi associate lại |
|---|---|
| **Chưa** redeploy | Giữ route + hostname, không gián đoạn |
| **Đã** redeploy | Mất route; deploy thêm lần nữa để có route mới, **hostname mới** |

Gỡ đã hoàn tất → association **mất vĩnh viễn**; tạo association mới → route mới, **hostname mới**.

```
(không có) ─associate─► ACTIVE ─disassociate─► DISASSOCIATING ─associate lại─► ACTIVE
                                                     │ route cuối bị xoá
                                                     ▼
                                               (xoá vĩnh viễn) ─associate mới─► ACTIVE (hostname mới)
```

⚠️ Khi lọc `list-gateway-routes` theo service: dùng **ARN đầy đủ** hoặc tên service **kèm cluster** — chỉ tên service bị coi là mơ hồ và lỗi.

### 17.3. Platform: xoá gateway

```bash
# 1) Tắt deletion protection (mặc định bật)
aws ecs update-gateway --gateway public-gw --no-deletion-protection
# 2) Gỡ từng cluster
aws ecs disassociate-gateway-from-cluster --gateway public-gw --cluster prod-cluster
# 3) Chờ app team redeploy/xoá service/gỡ connector → không còn association
aws ecs list-gateway-cluster-associations --gateway public-gw
# 4) Xoá
aws ecs delete-gateway --gateway public-gw
```

- `DeleteGateway` **đồng bộ** → `DELETED`; request sau trả `GatewayNotFoundException`.
- Không còn gì để dọn (route, tài nguyên riêng, ALB đã được xoá khi service ngắt).
- Nút thắt thực tế: **chờ con người** (app team redeploy).

⚠️ Lệnh ở bước 1 chỉ truyền `--no-deletion-protection`. Theo quy tắc ghi đè (mục 11), nó cũng đưa thiết lập khác về mặc định — chấp nhận được vì sắp xoá, **đừng dùng cho gateway còn giữ**. Xem thêm mục 26.

---

## 18. Trạng thái của gateway, association, route

> Nguồn: `12-03` (mục 1.1).

| Tài nguyên | API | Trạng thái |
|---|---|---|
| **Gateway** | `DescribeGateway`, `ListGateways` | `ACTIVE`, `UPDATE_IN_PROGRESS`, `UPDATE_COMPLETE`, `UPDATE_FAILED`, `DELETED` |
| **Association** | `ListGatewayClusterAssociations` | `ACTIVE`, `DISASSOCIATING` |
| **Route** | `DescribeGatewayRoute`, `ListGatewayRoutes` | `ACTIVE`, `CREATE_IN_PROGRESS`, `CREATE_FAILED`, `UPDATE_IN_PROGRESS`, `UPDATE_FAILED`, `DELETE_IN_PROGRESS`, `DELETE_FAILED` |

```
Gateway:  create ─► ACTIVE ─update─► UPDATE_IN_PROGRESS ─► UPDATE_COMPLETE
                                              └──────────► UPDATE_FAILED   ← cần xử lý
          delete ─► DELETED (cuối)

Route:    CREATE_IN_PROGRESS ─► ACTIVE ─► UPDATE_IN_PROGRESS ─► ACTIVE
                  └─► CREATE_FAILED            └─► UPDATE_FAILED
          ACTIVE ─► DELETE_IN_PROGRESS ─► (xoá)   └─► DELETE_FAILED
```

- Gateway chưa từng update thì không có `statusReason`; `failures` chỉ có khi lỗi.
- Trong các trạng thái của gateway, chỉ **`UPDATE_FAILED`** cần bạn xử lý.
- Connection ở góc nhìn app **không có trạng thái riêng** — chính là trạng thái route.

⚠️ Gateway `ACTIVE` **không** có nghĩa service phía sau khoẻ. Lỗi ingress của service hiện ở **route**; app unhealthy hiện ở **target health**. Cả hai đều không đổi trạng thái gateway.

---

## 19. Giám sát ba tầng

> Nguồn: `12-03`.

```
Tầng 1 Gateway   → describe-gateway                     UPDATE_FAILED? statusReason x/y, failures
Tầng 2 Route     → list-gateway-routes --status …       route của service *_FAILED?
                   describe-gateway-route               lần theo tài nguyên
Tầng 3 Target    → elbv2 describe-target-health         target khoẻ? lý do?
                   CloudWatch (dimension TargetGroup)   xu hướng traffic, 5XX, latency
                   access log S3 (lọc theo hostname)    từng request
```

"Xanh" ở tầng trên **không đảm bảo** "xanh" ở tầng dưới.

### 19.1. Lệnh theo góc nhìn

| Lệnh | Cho biết | Không cho biết |
|---|---|---|
| `list-gateways` | Gateway trong account + Region | — |
| `describe-gateway` | Trạng thái, role, cấu hình, `currentAlbCount`, `statusReason`, `failures` | Association, route |
| `list-gateway-cluster-associations` | Cluster nào gắn gateway nào, trạng thái | — |
| `describe-gateway-resources` | **Bản đồ**: tài nguyên nhóm theo từng ALB (ALB, listener, SG, route) — chỉ **ARN** | **Trạng thái thật** — xem bằng `elbv2`/`ec2` |
| `list-gateway-routes` | Route theo gateway / cluster / service / port / status | Lý do lỗi |
| `describe-gateway-route` | Trạng thái, URL, cert, ALB, listener, rule production + test, SG ALB, SG service | **Target group** (xem revision) |
| `describe-service-revisions` | Connection của một revision, kèm **TG production + alternate** | SG service |
| Service deployment / service events | **Lý do** lỗi tạo hạ tầng | — |

⚠️ `describe-gateway-resources` là bản ghi của ECS, **không đọc trạng thái thật** → **không phát hiện** thay đổi làm bên ngoài ECS.

### 19.2. Metric

Gateway **không tự phát metric**. Dùng metric ALB trên CloudWatch (🔎 namespace `AWS/ApplicationELB`).

| Phạm vi | Dimension | Metric |
|---|---|---|
| **Một service** | `TargetGroup` + `LoadBalancer` | `RequestCount`, `TargetResponseTime`, `HTTPCode_Target_5XX_Count`, `HealthyHostCount`, `UnHealthyHostCount` |
| **ALB dùng chung** | `LoadBalancer` | `HTTPCode_ELB_5XX_Count`, `ActiveConnectionCount`, `RejectedConnectionCount`, `ConsumedLCUs` |
| **AZ** | `AvailabilityZone` + LB/TG | Target health, request — vấn đề có khoanh trong một AZ không |

⚠️ Metric cấp `LoadBalancer` là **gộp ≤ 25 service** — 5XX tăng có thể do service khác. App team dùng dimension **TargetGroup**.

ELB chỉ phát metric **khi có traffic** và **không tính** health check → service mới chưa traffic không có metric là bình thường.

### 19.3. Alarm

| Ai | Alarm |
|---|---|
| App team | `UnHealthyHostCount`, `TargetResponseTime`, `HTTPCode_Target_5XX_Count` (dimension TG) |
| Platform team | `HTTPCode_ELB_5XX_Count`, `RejectedConnectionCount` (dimension LB) |

⚠️ Gateway thêm/xoá ALB theo service → alarm gắn **ARN cứng** có thể **âm thầm ngừng đo**. Chọn tài nguyên **theo tag** `aws:ecs:gateway-arn` (CloudWatch *resource tags for telemetry*), và cấu hình cách xử lý **missing data**.

### 19.4. Log

- Access log ALB → S3 (mặc định tắt). Một ALB chứa request của nhiều service → lọc theo **hostname**.
- 🔎 Truy vấn bằng Athena trên bucket log là cách thường dùng.

---

## 20. Bảo mật

> Nguồn: gom từ `12-00`, `12-01`, `12-02`, `12-04`.

| Lớp | Cơ chế | Ghi chú |
|---|---|---|
| Phơi bày ra internet | **Scheme** của gateway + `filters.accessTypes` của service | Service nội bộ **luôn** `PRIVATE` |
| Mạng vào ALB | Inbound SG (mặc định 443 từ mọi IP) | Tự cung cấp SG để giới hạn nguồn; ⚠️ quên trong `update-gateway` → về mặc định mở |
| Mạng ALB → task | SG của service do ECS quản lý | Task SG phải cho LB vào container port |
| Tầng ứng dụng | **AWS WAF** regional web ACL | Một lần cho mọi service; ⚠️ quên `webACLArn` khi update → mất WAF |
| Request smuggling | `httpDesync` (`defensive` mặc định) | `strictest` sau khi chạy `monitor` |
| Mã hoá | HTTPS-only :443, cert ACM tự cấp mỗi service | Không có HTTP |
| Quyền | Infrastructure role; PassRole giới hạn đúng ARN | App team không cần quyền LB |
| Toàn vẹn hạ tầng | Không sửa tài nguyên `AmazonECSManaged=true` | Không có drift detection |
| Kiểm toán | Access log S3; 🔎 CloudTrail cho API gateway | — |

✍️ Mô hình hai gateway điển hình:

```
public-gw   (internet-facing, WAF, inbound SG giới hạn) ── service web/API công khai: accessTypes [PUBLIC]
private-gw  (internal)                                   ── service nội bộ:          accessTypes [PRIVATE]
cả hai associate với prod-cluster, subnet phủ cả 3 AZ
```

---

## 21. Sức chứa và chi phí

> Nguồn: `12-00` (mục 5, 6, 8), `12-01` (mục 6.6), `12-04` (mục 3).

```
Số service tối đa / gateway = 25 × maxAlbCount
   mặc định: 25 × 10  = 250
   tối đa:   25 × 200 = 5.000
```

- Hết chỗ (mọi ALB đầy + chạm `maxAlbCount`) → **deploy service mới thất bại**. Nâng `maxAlbCount` **trước**; nâng sau không tự chạy lại deploy lỗi.
- Theo dõi `currentAlbCount` / `maxAlbCount` trong `describe-gateway`.
- ⚠️ ALB không được gom → chi phí có thể cao hơn mức tối thiểu sau khi service rời đi.
- ⚠️ **Certificate**: mỗi route một cert. `CertificateAnnualLimitExceeded` đếm cert yêu cầu trong **365 ngày**; **xoá không giảm** con số → ngắt/nối lại liên tục (vd môi trường preview tạo/xoá service thường xuyên) tiêu hạn mức. Tăng qua **AWS Support**.
- Quota bên dưới: ALB, target group (account/Region), listener rule & cert mỗi ALB (`ResourceQuotaExceeded`), SG mỗi VPC, rule mỗi SG, tag (tag gateway + tag service + `AmazonECSManaged` chiếm 1 slot).
- Subnet phải còn **IP trống** cho ALB (`InvalidSubnet`).

🔎 Ước lượng nhanh: 30 service dùng gateway → 2 ALB (25 + 5) thay vì 30 ALB riêng. Phần tiết kiệm chính là phí giờ của ALB; LCU vẫn tính theo traffic.

---

## 22. Triển khai từ đầu đến cuối (CLI)

> ✍️ Ghép từ các lệnh trong docs; tên, ARN, subnet là placeholder. Mình **chưa chạy thử** — kiểm tra tham số bằng `aws ecs <lệnh> help` với phiên bản CLI của bạn.

### 22.1. Platform team

```bash
ACCOUNT=<ACCOUNT_ID>
ROLE_ARN=arn:aws:iam::$ACCOUNT:role/ecsGatewayInfraRole

# 1) Infrastructure role
aws iam create-role --role-name ecsGatewayInfraRole --assume-role-policy-document file://trust-ecs.json
aws iam attach-role-policy --role-name ecsGatewayInfraRole \
  --policy-arn arn:aws:iam::aws:policy/AmazonECSInfrastructureRoleForGatewayHostSharedALB
sleep 60   # chờ IAM lan truyền

# 2) Gateway public (subnet public, 3 AZ) và private (subnet private, 3 AZ)
aws ecs create-gateway --name public-gw --infrastructure-role-arn $ROLE_ARN \
  --configuration '{
    "hostSharedAlb": {
      "scheme": "internet-facing",
      "networkConfiguration": { "subnets": ["<PUB_A>","<PUB_B>","<PUB_C>"] },
      "webACLArn": "<ARN_WEB_ACL>",
      "accessLogs": { "destination": { "s3": { "bucket": "my-alb-logs", "prefix": "public-gw" } } },
      "testTrafficRules": { "header": { "name": "x-test-traffic", "values": ["canary"] } },
      "maxAlbCount": 20
    }
  }'

aws ecs create-gateway --name private-gw --infrastructure-role-arn $ROLE_ARN \
  --configuration '{
    "hostSharedAlb": {
      "scheme": "internal",
      "networkConfiguration": { "subnets": ["<PRIV_A>","<PRIV_B>","<PRIV_C>"] },
      "testTrafficRules": { "header": { "name": "x-test-traffic", "values": ["canary"] } }
    }
  }'

# 3) Associate
aws ecs associate-gateway-with-cluster --gateway public-gw  --cluster prod-cluster
aws ecs associate-gateway-with-cluster --gateway private-gw --cluster prod-cluster
aws ecs list-gateway-cluster-associations --cluster prod-cluster
```

Bucket `my-alb-logs`: cùng Region, bucket policy cho ELB ghi (🔎 xem trang *Access logs for your ALB* để lấy policy theo Region).

### 22.2. Application team

Task definition (`awsvpc` + named port):

```json
{
  "family": "orders",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "512",
  "memory": "1024",
  "executionRoleArn": "arn:aws:iam::<ACCOUNT_ID>:role/ecsTaskExecutionRole",
  "containerDefinitions": [{
    "name": "app",
    "image": "<ACCOUNT_ID>.dkr.ecr.<REGION>.amazonaws.com/orders:3f9c2ab",
    "essential": true,
    "portMappings": [{ "name": "web", "containerPort": 8080, "protocol": "tcp", "appProtocol": "http" }]
  }]
}
```

Service public (web), chốt health check ngay từ đầu:

```bash
aws ecs register-task-definition --cli-input-json file://orders.json

aws ecs create-service --cluster prod-cluster --service-name orders \
  --task-definition orders --desired-count 3 \
  --capacity-provider-strategy capacityProvider=FARGATE,weight=1 \
  --network-configuration 'awsvpcConfiguration={subnets=[<PRIV_A>,<PRIV_B>,<PRIV_C>],securityGroups=[<SG_TASK>]}' \
  --gateway-connectors '[{"portName":"web","filters":{"accessTypes":["PUBLIC"]},"healthCheck":{"healthCheckPath":"/health"}}]'
```

Service nội bộ:

```bash
aws ecs update-service --cluster prod-cluster --service billing-internal \
  --gateway-connectors '[{"portName":"web","filters":{"accessTypes":["PRIVATE"]},"healthCheck":{"healthCheckPath":"/health"}}]'
```

⚠️ Subnet của service phải nằm trong các AZ mà gateway phủ (ở đây cả 3 AZ). Task có thể chạy ở subnet private trong khi gateway public dùng subnet public — điều kiện là **cùng VPC + AZ**, không phải cùng subnet.

### 22.3. Lấy URL và kiểm tra

```bash
REV=$(aws ecs list-service-deployments --cluster prod-cluster --service orders \
  --query 'serviceDeployments[0].targetServiceRevisionArn' --output text)

aws ecs describe-service-revisions --service-revision-arns "$REV" \
  --query 'serviceRevisions[0].ecsManagedResources.gatewayConnections[].{url:serviceUrl,type:accessType,status:status}'

curl -sS https://<unique-id>.ecs.<region>.on.aws/health
curl -sS -H 'x-test-traffic: canary' https://<unique-id>.ecs.<region>.on.aws/health   # trong lúc deploy → revision mới
```

### 22.4. Platform kiểm tra sức chứa

```bash
aws ecs describe-gateway --gateway public-gw \
  --query 'gateway.{status:status,current:configuration.hostSharedAlb.currentAlbCount,max:configuration.hostSharedAlb.maxAlbCount}'
aws ecs describe-gateway-resources --gateway public-gw
aws ecs list-gateway-routes --gateway public-gw --status CREATE_FAILED UPDATE_FAILED
```

---

## 23. Troubleshooting theo triệu chứng

> Nguồn: `12-04`.

**Kiểm tra 0 phút**: đúng account / Region / tên-ARN; Region hỗ trợ; đủ quyền; task definition `awsvpc`.

| Triệu chứng | Nguyên nhân | Xử lý |
|---|---|---|
| `create/update-service` bị từ chối: *no gateways are associated with cluster* | Cluster chưa có gateway; gateway duy nhất đang `DISASSOCIATING`; **gateway cuối cùng đã bị xoá** (service còn connector cũ → kể cả đổi image cũng lỗi) | Nhờ platform associate, hoặc gỡ connector |
| *none are in the service's VPC* | Khác VPC | Gateway đúng VPC |
| *none of the gateways associated with cluster match* | Bộ lọc access type loại hết | Sửa `filters.accessTypes` |
| Không có gateway hợp lệ vì AZ | Gateway không phủ đủ AZ của service | So bằng `describe-subnets` (VpcId, AZ) cho subnet service + gateway; thu hẹp subnet service hoặc gateway mới |
| `portName not found in task definition portMappings` | Dùng số port; revision mới quên `name` | `describe-task-definition --query '…portMappings[].name'` |
| Đổi health check lỗi với `ROLLING` | Thay TG không hỗ trợ ROLLING | Giữ nguyên / blue-green / canary / ngắt-nối lại (URL mới) |
| Route `CREATE_FAILED` / `UPDATE_FAILED` | Thiếu quyền role, quota, subnet hết IP, < 2 AZ, sửa ngoài ECS | **Route chỉ báo "hỏng"; lý do ở service deployment + service events**. Sửa gốc → **redeploy** |
| Deploy lỗi khi gateway đầy | ALB đầy + chạm `maxAlbCount` | Nâng `maxAlbCount` (update đầy đủ), chờ `UPDATE_COMPLETE`, **redeploy** |
| Cluster không associate thêm được | Đủ 5 gateway | Gỡ gateway không dùng (ngắt service trước) |
| Gateway `UPDATE_FAILED` | WAF ARN sai, SG không tồn tại, thiếu quyền, tài nguyên bị sửa | `statusReason` + `failures`; sửa, chạy lại; coi là **áp dụng một phần** |
| Hostname không phản hồi | Xem quy trình 5 bước dưới | |
| Association kẹt `DISASSOCIATING` | Còn route | `list-gateway-routes --cluster`; app team redeploy |
| Không xoá được gateway | Deletion protection bật; còn association | Tắt protection; gỡ hết association |
| Tài nguyên bị sửa/xoá bên ngoài | `ManagedResourceDeleted`, `ConflictingListener`, `SecurityGroupNameInUse`, `InvalidSecurityGroup` | Bị **sửa**: khôi phục cấu hình gốc → redeploy (giữ hostname). Bị **xoá**: gỡ connector → deploy → thêm lại → deploy (**hostname mới**) |

**Hostname không phản hồi — đi từ ngoài vào trong:**

```
1. Route ACTIVE?            list-gateway-routes --cluster --service   (*_FAILED → xử lý route trước)
2. Mạng tới được ALB?       gateway internal → phải thử từ mạng nội bộ
3. Target healthy?          elbv2 describe-target-health (TG lấy từ describe-service-revisions)
                            vd Target.ResponseCodeMismatch; soát health path, port, phản hồi app
                            → SOÁT APP TRƯỚC, đừng vội sửa hạ tầng gateway
4. Security group?          inbound SG ALB cho client; SG task cho ALB vào container port
                            (SG tự cung cấp → ECS không quản lý rule)
5. App + WAF?               port đang nghe, health endpoint; rule WAF chặn?
                            access log + CloudWatch phân biệt lỗi ELB vs lỗi app
```

---

## 24. Bảng tra mã lỗi

> Nguồn: `12-04` (mục 9). Cột "Hướng xử lý" có dấu 🔎 là suy luận của mình.

| Mã / thông điệp | Nguyên nhân | Hướng xử lý |
|---|---|---|
| `gatewayConnectors were specified but no gateways are associated with cluster` | Cluster không có gateway dùng được | Associate / gỡ connector |
| `has associated gateways but none are in the service's VPC` | Khác VPC | Gateway đúng VPC |
| `none of the gateways associated with cluster match` | Access type | Sửa filter |
| `InvalidConfiguration: "portName not found in task definition portMappings"` | Sai tên port | Sửa `portName`/task def |
| Lỗi thay target group với `ROLLING` | Đổi health check | Blue/green, canary |
| `InsufficientAvailabilityZones` | Subnet gateway < 2 AZ | Gateway mới |
| `InvalidSubnet` | Subnet không tồn tại / khác VPC / hết IP | Sửa subnet |
| `Cluster has reached the maximum number of gateway associations (5)` | Đủ 5 | Gỡ bớt |
| (deploy fail khi đầy) | `maxAlbCount` | Nâng + redeploy |
| `LoadBalancerQuotaExceeded`, `TargetGroupQuotaExceeded` | Quota account/Region | Xin tăng |
| `ResourceQuotaExceeded` | Quota mỗi ALB (rule, cert) | Xin tăng |
| `CertificateQuotaExceeded` | Quota cert | Xin tăng |
| `CertificateAnnualLimitExceeded` | Cert 365 ngày, xoá không giảm | AWS Support |
| `SecurityGroupCapacityExceeded`, `SecurityGroupRuleCapacityExceeded` | SG / rule | Dọn, xin tăng |
| `TestRuleLimitExceeded` | Quá nhiều test rule | Redeploy để dọn rule cũ 🔎 |
| `TagLimitExceeded` | Tag gateway + service + `AmazonECSManaged` vượt; tag policy | Bớt tag / `propagateTags: NONE` 🔎 |
| Lỗi `iam:PassRole` | Người gọi | Policy người gọi |
| Không assume được role / `AWS STS is not activated…` | Trust / STS | Trust policy, bật STS, chờ lan truyền |
| `AccessDenied` ELB/EC2/ACM | Infrastructure role | Managed policy |
| `Target.ResponseCodeMismatch` | App trả mã ngoài dải | Sửa app / health path |
| Gateway `UPDATE_FAILED` | Update một phần | `failures` |
| `ManagedResourceDeleted` | Bị xoá ngoài ECS | Gỡ → thêm connector |
| `ConflictingListener` | Listener lạ trên port gateway | Xoá listener lạ 🔎 |
| `SecurityGroupNameInUse` | SG trùng tên không do ECS quản lý | Đổi tên/xoá SG đó 🔎 |
| `InvalidSecurityGroup` | Inbound SG không còn | Cập nhật SG hợp lệ |

---

## 25. So sánh với cách ingress khác

> 🔎 Bảng ghép từ folder 12 và các toàn tập khác (`15-04-05-99` load balancing, `15-04-07-01-99` Service Connect, `15-98` mục 17–20). Đánh giá ở đoạn cuối là của mình.

| | **ECS Gateway** | **ALB/NLB tự quản lý** | **Service Connect** | **VPC Lattice** |
|---|---|---|---|---|
| Hướng traffic | Ingress từ client (public/internal) | Ingress | **Service ↔ service** trong namespace | Service ↔ service, cross-VPC/account |
| Protocol | **Chỉ HTTPS :443** | HTTP/HTTPS/gRPC (ALB), TCP/UDP/TLS (NLB) | HTTP, HTTP2, gRPC, TCP | HTTP/HTTPS/gRPC, TCP |
| Ai cấu hình | Platform 1 lần, app 1 tham số | Mỗi service | Namespace + service | Lattice service network |
| Hostname | `*.ecs.<region>.on.aws` ngẫu nhiên | Tự chọn (custom domain) | Tên trong namespace | Tên Lattice / custom |
| Network mode | `awsvpc` | `awsvpc` (target `ip`) hoặc `bridge` (target `instance`) | `awsvpc`, `bridge` | `awsvpc` |
| Cross-account | Không | Tuỳ thiết kế | Shared namespace (RAM) | Có |
| Chi phí ALB | Dùng chung (≤25) | 1 ALB/service (hoặc tự share) | Không ALB | Phí Lattice |

Khi nên dùng Gateway: nhiều service HTTP(S) cần URL riêng, muốn self-service cho app team và quản trị bảo mật tập trung. Khi **không** hợp: cần TCP/UDP, custom domain chặt chẽ, cross-account, hoặc service `bridge` (`12-99` mục 8).

---

## 26. Chỗ docs không khớp nhau

| Chủ đề | Chỗ A | Chỗ B | Cách xử lý |
|---|---|---|---|
| Infrastructure role trong `UpdateGateway` | `12-01`: **bắt buộc** trong mọi request | `12-01` mục xoá & `12-04` mục 7.2: `update-gateway --no-deletion-protection` không kèm role | 🔎 Thử trên môi trường test; an toàn nhất là luôn truyền role + cấu hình đầy đủ |
| Ví dụ tăng `maxAlbCount` | `12-04` mục 3.2: chỉ `--configuration` | `12-01`: phải kèm role và giữ thiết lập khác | Làm theo quy trình `12-01` |
| Danh sách bất biến | `12-00`: name, type, scheme, listener port, listener protocol, subnet, IP type | `12-01`: name, subnets, IP type, scheme (+ listener luôn 443) | Gộp cả hai; thêm **type** (`hostSharedAlb`) |
| Lấy ARN revision | `12-02`: `describe-service-deployments` → `targetServiceRevision.arn` | `12-04`: `list-service-deployments` → `targetServiceRevisionArn` | Hai cách đều được |
| Gateway không hợp lệ | `12-00`: request thất bại nếu không có gateway nào thoả | `12-02`: gateway không khớp bị **bỏ qua**, không fail | Không mâu thuẫn: chỉ fail khi **không còn** gateway hợp lệ |
| Thông điệp lỗi AZ | `12-04` nêu thông điệp cho VPC và access type | Không có thông điệp riêng cho AZ | Kiểm tra AZ bằng `describe-subnets` |
| Target group trong route | `describe-gateway-route` không có TG | `describe-service-revisions` có TG production + alternate | Dùng revision để lấy TG |
| Công cụ | `12-00`: console, CLI, SDK, CloudFormation | Không nhắc Terraform | 🔎 Kiểm tra AWS provider |

---

## 27. Checklist

```
Platform — thiết kế (bất biến)
[ ] Số gateway: thường 1 public (internet-facing) + 1 internal
[ ] Subnet phủ MỌI AZ mà service có thể chạy, ≥ 2 AZ, đúng VPC, còn đủ IP
[ ] IP address type (ipv4 / dualstack / dualstack-without-public-ipv4)
[ ] Tên gateway theo quy ước

Platform — IAM
[ ] Infrastructure role: trust ecs.amazonaws.com + AmazonECSInfrastructureRoleForGatewayHostSharedALB
[ ] iam:PassRole giới hạn đúng ARN role cho platform team
[ ] Chờ ~1 phút sau khi tạo role

Platform — cấu hình đổi được
[ ] Inbound SG (giới hạn nguồn cho gateway internal / theo yêu cầu)
[ ] WAF regional web ACL cho gateway public
[ ] httpDesync (defensive; monitor → strictest nếu cần)
[ ] Access log S3 + bucket policy cho ELB
[ ] Header test traffic đã thông báo cho app team
[ ] maxAlbCount dư so với nhu cầu; alarm khi currentAlbCount gần max
[ ] Deletion protection bật
[ ] Quản lý bằng IaC; update luôn từ describe-gateway, kèm role, đủ trường

Platform — vận hành
[ ] Alarm ELB 5XX, RejectedConnectionCount chọn theo tag aws:ecs:gateway-arn, xử lý missing data
[ ] Không ai/không tool nào sửa tài nguyên AmazonECSManaged=true
[ ] Thêm/gỡ association có thông báo app team (ảnh hưởng ở lần deploy kế)
[ ] Theo dõi ALB đầy một phần (không gom), hạn mức certificate năm

App team
[ ] Task definition awsvpc + portMappings[].name
[ ] portName là TÊN port
[ ] filters.accessTypes tường minh; PRIVATE cho service nội bộ
[ ] healthCheckPath nhẹ (/health) đặt NGAY lần đầu (nhất là với ROLLING)
[ ] Subnet service nằm trong các AZ gateway phủ
[ ] Chờ connection ACTIVE trước khi chuyển client; xử lý ngay nếu CREATE_FAILED
[ ] Không hard-code hostname *.on.aws ở nhiều nơi
[ ] Alarm theo dimension TargetGroup
[ ] Biết header test traffic để kiểm tra revision mới
```

---

## 28. Câu hỏi tự kiểm tra (40 câu)

**1. ECS Gateway giải quyết vấn đề gì?**
<details><summary>Đáp án</summary>

Không phải dựng ALB + listener + TG + SG + cert cho từng service. Platform team cấu hình ingress HTTPS một lần; ECS tự tạo hạ tầng dùng chung và hạ tầng riêng cho mỗi service khi service opt in, cấp mỗi service một hostname HTTPS.
</details>

**2. Ba bước thiết lập là gì, ai làm?**
<details><summary>Đáp án</summary>

(1) Platform tạo gateway; (2) platform associate gateway với cluster; (3) app team thêm gateway connector vào service và deploy.
</details>

**3. Khi nào ECS bắt đầu tạo ALB?**
<details><summary>Đáp án</summary>

Khi service **đầu tiên** kết nối vào gateway đã được associate. Tạo gateway và associate chưa tạo tài nguyên nào (chưa tốn tiền ALB).
</details>

**4. Bốn khái niệm cốt lõi?**
<details><summary>Đáp án</summary>

Gateway (cấp account, có ARN), gateway-cluster association (N–N), gateway connector (opt-in trên service, ≤ 1), gateway route (một kết nối service–gateway, có ARN, URL, trạng thái).
</details>

**5. Tài nguyên nào dùng chung, tài nguyên nào riêng từng service?**
<details><summary>Đáp án</summary>

Dùng chung: ALB, listener, SG của ALB. Riêng: certificate ACM, DNS hostname, listener rule, target group, SG của service.
</details>

**6. Vì sao mỗi service có certificate riêng?**
<details><summary>Đáp án</summary>

Vì định tuyến theo host — mỗi service có hostname riêng `https://<id>.ecs.<region>.on.aws`, nên cần cert cho hostname đó.
</details>

**7. Hostname của service có suy ra từ tên service không?**
<details><summary>Đáp án</summary>

Không, phần định danh ngẫu nhiên. Xoá và tạo lại service cùng tên sẽ có URL khác.
</details>

**8. Một gateway chứa được tối đa bao nhiêu service với mặc định?**
<details><summary>Đáp án</summary>

25 service/ALB × `maxAlbCount` mặc định 10 = 250. Tối đa 25 × 200 = 5.000.
</details>

**9. Kể các giới hạn chính.**
<details><summary>Đáp án</summary>

5 gateway/cluster, 10.000 cluster/gateway, 1 connector/service, 25 service/ALB, 200 ALB/gateway.
</details>

**10. Gateway hỗ trợ protocol và network mode nào?**
<details><summary>Đáp án</summary>

Chỉ HTTPS port 443; chỉ service `awsvpc` (trên EC2, Fargate, Managed Instances).
</details>

**11. Gateway có dùng được với cluster ở account khác, hay với Express Mode service?**
<details><summary>Đáp án</summary>

Không và không.
</details>

**12. Thiết lập nào bất biến?**
<details><summary>Đáp án</summary>

Name, (type), scheme, subnets, IP address type, listener port/protocol. Muốn đổi phải tạo gateway mới và chuyển service.
</details>

**13. Vì sao subnet của gateway là quyết định quan trọng nhất?**
<details><summary>Đáp án</summary>

Subnet quyết định gateway phủ VPC và AZ nào, nên quyết định service nào dùng được gateway; và nó không đổi được. Ngoài ra subnet phải ở ≥ 2 AZ và còn IP.
</details>

**14. Scheme liên quan access type thế nào?**
<details><summary>Đáp án</summary>

`internet-facing` → `PUBLIC`; `internal` → `PRIVATE`.
</details>

**15. `{"hostSharedAlb": {}}` tạo gateway thế nào và rủi ro gì?**
<details><summary>Đáp án</summary>

Toàn mặc định: subnet default VPC, scheme `internet-facing`, IP `dualstack`. Các giá trị đó bất biến nên không sửa được về sau.
</details>

**16. Infrastructure role cần gì?**
<details><summary>Đáp án</summary>

Trust policy cho `ecs.amazonaws.com` `sts:AssumeRole`, và managed policy `AmazonECSInfrastructureRoleForGatewayHostSharedALB`.
</details>

**17. Lỗi `iam:PassRole` khi create-gateway sửa ở đâu? Thêm quyền vào infrastructure role có giúp không?**
<details><summary>Đáp án</summary>

Sửa policy của **người gọi** (PassRole trên đúng ARN role; kiểm tra boundary, SCP, deny). Thêm quyền vào infrastructure role không giúp.
</details>

**18. App team có cần `iam:PassRole` hay quyền tạo load balancer không?**
<details><summary>Đáp án</summary>

Không. Quyền tạo hạ tầng nằm ở infrastructure role của ECS.
</details>

**19. Lần đầu create-gateway báo không assume được role vừa tạo. Vì sao?**
<details><summary>Đáp án</summary>

IAM eventually consistent; chờ khoảng một phút rồi chạy lại. (Hoặc trust policy sai / STS chưa bật ở Region.)
</details>

**20. Associate gateway với cluster thì service đang chạy có thay đổi gì không?**
<details><summary>Đáp án</summary>

Không. Service có connector chỉ dùng gateway ở lần deploy kế tiếp.
</details>

**21. Vì sao `UpdateGateway` nguy hiểm?**
<details><summary>Đáp án</summary>

Nó thay thế toàn bộ cấu hình. Bỏ sót thiết lập → bị xoá hoặc về mặc định (mất WAF, mất inbound SG tuỳ chỉnh, tắt log, `maxAlbCount` về 10, deletion protection bật lại…). Phải `describe-gateway` trước, truyền đủ, luôn kèm infrastructure role.
</details>

**22. Những trường nào không được đưa vào request update?**
<details><summary>Đáp án</summary>

`name`, `scheme`, `protocol`, `port`, `subnets`, `ipAddressType` (bất biến) và `currentAlbCount` (giá trị báo cáo).
</details>

**23. `UPDATE_FAILED` có nghĩa là update đã rollback?**
<details><summary>Đáp án</summary>

Không. ECS cập nhật rolling từng ALB và không dừng ở lỗi đầu, nên có thể áp dụng một phần. Xem `statusReason` (x/y ALB) và `failures`, sửa rồi chạy lại.
</details>

**24. Bỏ inbound SG tuỳ chỉnh thì ALB mở thế nào?**
<details><summary>Đáp án</summary>

ECS quay lại tự tạo/quản lý SG, cho phép HTTPS 443 từ mọi IP.
</details>

**25. Bật access log cần gì? Cấp quyền S3 cho infrastructure role có tác dụng không?**
<details><summary>Đáp án</summary>

Cung cấp S3 bucket bạn sở hữu, cùng Region, bucket policy cho Elastic Load Balancing ghi. Infrastructure role không tham gia ghi log nên cấp quyền cho nó vô ích.
</details>

**26. `portName` là gì?**
<details><summary>Đáp án</summary>

Tên (trường `name`) của một port mapping trong task definition — không phải số port. ECS lấy port, protocol, protocol version từ đó cho target group.
</details>

**27. Bỏ trống `filters.accessTypes` có rủi ro gì?**
<details><summary>Đáp án</summary>

Service nối mọi gateway hợp lệ, cả public. Nếu sau này platform team gắn gateway internet-facing vào cluster, service bị expose ra internet ở lần deploy kế tiếp. Service nội bộ phải đặt `PRIVATE`.
</details>

**28. Health check mặc định?**
<details><summary>Đáp án</summary>

HTTP tới `/` trên port của `portName`, mỗi 30 giây, chờ `200` trong 5 giây.
</details>

**29. Vì sao không đổi được health check với `ROLLING`, và làm gì?**
<details><summary>Đáp án</summary>

Đổi health check cần thay target group, ROLLING không hỗ trợ. Đặt ngay từ đầu, dùng BLUE_GREEN/CANARY, hoặc ngắt–nối lại (URL mới).
</details>

**30. Ba tiêu chí chọn gateway tương thích?**
<details><summary>Đáp án</summary>

Access type khớp filter; cùng VPC; gateway phủ **mọi** AZ của service (tập con).
</details>

**31. GW1 phủ 2a/2b/2c, GW2 phủ 2a/2b, service ở 2b/2c. Service nối gateway nào? Vì sao không nối GW2?**
<details><summary>Đáp án</summary>

Chỉ GW1. GW2 thiếu 2c; task ở 2c sẽ không qua health check và bị thay liên tục.
</details>

**32. Có cách chỉ định gateway theo tên trong connector không?**
<details><summary>Đáp án</summary>

Không. Điều khiển qua association, VPC/AZ của gateway và access type.
</details>

**33. Test traffic header đặt ở đâu, hoạt động thế nào?**
<details><summary>Đáp án</summary>

Trên gateway, dùng chung cho mọi service. Trong progressive deployment, request mang header tới revision mới (alternate TG), còn lại tới production TG. Chỉ khớp header, không chia %. Đổi header chỉ áp cho service ở lần deploy kế tiếp.
</details>

**34. Lấy URL service thế nào?**
<details><summary>Đáp án</summary>

Lấy ARN service revision (từ `list/describe-service-deployments`), rồi `describe-service-revisions --query 'serviceRevisions[0].ecsManagedResources.gatewayConnections'` → `serviceUrl`, `accessType`, `status`. Chờ `ACTIVE`.
</details>

**35. Connection báo `CREATE_FAILED`, có nên chờ thêm?**
<details><summary>Đáp án</summary>

Không — đã thất bại. Tìm lý do ở service deployment / service events, sửa và redeploy.
</details>

**36. Route chỉ báo trạng thái lỗi; lý do lỗi xem ở đâu?**
<details><summary>Đáp án</summary>

Service deployment đã kích hoạt việc tạo hạ tầng, và service events.
</details>

**37. Gateway đầy (ALB đủ 25 × maxAlbCount), deploy fail. Nâng maxAlbCount xong service có tự lên không?**
<details><summary>Đáp án</summary>

Không. Chờ `UPDATE_COMPLETE` rồi chủ service phải redeploy.
</details>

**38. Association kẹt `DISASSOCIATING` lâu có phải lỗi?**
<details><summary>Đáp án</summary>

Không. Còn service có route chưa redeploy; dùng `list-gateway-routes --cluster` để tìm và phối hợp app team.
</details>

**39. Ai đó xoá tay target group của service. Phục hồi thế nào, hệ quả?**
<details><summary>Đáp án</summary>

Gỡ connector → deploy → thêm lại connector → deploy; tạo tài nguyên và route mới với **hostname mới** → cập nhật client/DNS. (Nếu chỉ bị sửa: khôi phục cấu hình gốc rồi redeploy, giữ hostname.)
</details>

**40. App team nên đặt alarm ở dimension nào và vì sao? Vì sao không gắn ARN cứng?**
<details><summary>Đáp án</summary>

Dimension `TargetGroup` (+ LoadBalancer), vì metric cấp ALB gộp tới 25 service. Không gắn ARN cứng vì gateway thêm/xoá ALB, alarm có thể âm thầm ngừng đo; chọn theo tag `aws:ecs:gateway-arn` và cấu hình missing data.
</details>

---

## 29. Bản đồ nguồn

| Mục | File nguồn |
|---|---|
| 1–6 | `12-00-ecs-gateway.md` |
| 7–11 | `12-01-ecs-gateway-managing.md` (mục 1–6) |
| 12–16 | `12-02-ecs-gateway-connect-service.md` |
| 17 | `12-02` (mục 8), `12-01` (mục 7–8), `12-04` (mục 7) |
| 18–19 | `12-03-ecs-gateway-monitoring.md` |
| 20–21 | Gom từ `12-00`, `12-01`, `12-02`, `12-04` (mục 3) |
| 22 | ✍️ ghép lệnh từ `12-01`, `12-02`, `12-03` |
| 23–24 | `12-04-ecs-gateway-troubleshooting.md` |
| 25 | 🔎 ghép với `15-04-05-99`, `15-04-07-01-99`, `15-98`, `12-99` |
| 26–28 | Tổng hợp |

Liên quan trong repo:
- Load balancing tự quản lý → [`15-04-05-99-ecs-load-balancing-toan-tap.md`](../15%20-%20Schedule%20your%20containers/15-04-05-99-ecs-load-balancing-toan-tap.md)
- Service Connect → [`15-04-07-01-99-ecs-service-connect-toan-tap.md`](../15%20-%20Schedule%20your%20containers/15-04-07-01-99-ecs-service-connect-toan-tap.md)
- Named port, `awsvpc` → folder `13 - Task definitions`

Docs gốc:
- Amazon ECS Gateway — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/gateway-overview.html>
- Create and manage a gateway — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/gateway-managing.html>
- Connect services to a gateway — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/gateway-connect-service.html>
- Monitor a gateway — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/gateway-monitoring.html>
- Troubleshoot a gateway — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/gateway-troubleshooting.html>

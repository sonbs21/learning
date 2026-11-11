# Connect Amazon ECS services to a gateway

> Nguồn: **Amazon ECS Developer Guide — Connect Amazon ECS services to a gateway**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/gateway-connect-service.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: diễn đạt lại bằng tiếng Việt, bám sát từng ý và đúng thứ tự của trang gốc, không thêm thông tin ngoài trang. Đây không phải bản dịch từng chữ; khi cần câu chữ hoặc ví dụ code chính xác, đọc link gốc ở trên.
> - Khối **💡 Giải thích thêm**: phần giải thích để dễ hiểu hơn, chỉ làm rõ những gì docs đã nói.
> - Lệnh CLI dùng placeholder (`<...>`); tên lệnh, tên tham số và tên trường JSON giữ đúng như docs.

---

## Mục lục

0. [Giới thiệu](#0-giới-thiệu)
1. [Điều kiện tiên quyết](#1-điều-kiện-tiên-quyết)
2. [Kết nối service vào gateway](#2-kết-nối-service-vào-gateway)
3. [Tuỳ chỉnh gateway connector](#3-tuỳ-chỉnh-gateway-connector)
4. [ECS chọn gateway tương thích như thế nào](#4-ecs-chọn-gateway-tương-thích-như-thế-nào)
5. [Hiểu về service deployment](#5-hiểu-về-service-deployment)
6. [Kiểm tra kết nối và tìm URL của service](#6-kiểm-tra-kết-nối-và-tìm-url-của-service)
7. [Di chuyển (migrate) service](#7-di-chuyển-migrate-service)
8. [Ngắt kết nối service khỏi gateway](#8-ngắt-kết-nối-service-khỏi-gateway)
9. [Tóm tắt](#9-tóm-tắt)

---

## 0. Giới thiệu

**📘 Theo docs AWS**

- Bạn kết nối một ECS service vào gateway bằng cách cấu hình **gateway connector** khi **tạo hoặc cập nhật** service.
- Gateway connector xác định **container port có tên (named port)** sẽ nhận traffic. Bạn cũng có thể cấu hình **health check** và chỉ định service được dùng những **access type** nào của gateway.
- ECS kết nối service tới các gateway **tương thích** đã liên kết với cluster của nó. Với **mỗi kết nối**, ECS tạo một **gateway route** và cấp một **hostname HTTPS riêng**.

**💡 Giải thích thêm**

Trang trước (*Create and manage*) là việc của platform team; trang này là việc của **application team**. Toàn bộ "giao diện" của app team với gateway chỉ là một tham số trên service: `gatewayConnectors`.

---

## 1. Điều kiện tiên quyết

**📘 Theo docs AWS**

Trước khi thêm gateway connector vào service, cần có:

- **Một gateway đã liên kết với cluster** của service, và association ở trạng thái **ACTIVE**. Nếu chưa có gateway nào liên kết, ECS **từ chối request ngay**, nên bạn biết lỗi ngay thay vì chờ một service deploy xong mà không có URL.
- **Task definition dùng network mode `awsvpc`** và khai báo một **named port** trên container cần nhận traffic. (Xem *Amazon ECS task networking options* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/networking-networkmode.html>.)
- **Ít nhất một gateway trên cluster tương thích** với service. Gateway tương thích khi: cùng VPC với subnet của service, khớp bộ lọc access type, và bao phủ **mọi AZ** mà service chạy.

**💡 Giải thích thêm**

"Named port" là trường `name` trong `portMappings` của container definition. Ví dụ, một container có `portMappings` với `name` là `web` và `containerPort` 8080 thì connector sẽ tham chiếu `web` chứ không tham chiếu số 8080.

Checklist nhanh trước khi thêm connector:

| Kiểm tra | Hỏi ai / ở đâu |
|---|---|
| Cluster đã có gateway `ACTIVE`? | Platform team, hoặc `list-gateway-cluster-associations --cluster` |
| Task definition là `awsvpc` + có named port? | Task definition của bạn |
| Gateway cùng VPC, phủ đủ AZ của service, đúng access type? | Platform team (cấu hình subnet/scheme của gateway) |

---

## 2. Kết nối service vào gateway

**📘 Theo docs AWS**

Thêm gateway connector vào service và đặt `portName` là tên container port (trong task definition) cần nhận traffic.

```bash
aws ecs update-service \
  --cluster <TÊN_CLUSTER> \
  --service <TÊN_SERVICE> \
  --gateway-connectors '[{"portName": "<TÊN_PORT>"}]'
```

Trong quá trình deploy, ECS sẽ:
- chọn các gateway tương thích;
- tạo mới hoặc dùng lại hạ tầng ingress cần thiết;
- tạo certificate, hostname, listener rule và target group;
- tạo một gateway route cho **mỗi kết nối**. Mỗi route có hostname HTTPS riêng.

Nếu có **nhiều hơn một** gateway tương thích, service kết nối tới **tất cả**. Một service có **tối đa một** gateway connector.

**💡 Giải thích thêm**

"Một connector" không có nghĩa là "một gateway". Một connector có thể sinh ra nhiều route (mỗi gateway tương thích một route), tức là service có thể có nhiều URL cùng lúc.

---

## 3. Tuỳ chỉnh gateway connector

**📘 Theo docs AWS**

- **`portName` là bắt buộc**, tham chiếu tới một named port trong task definition. ECS dùng **số port, protocol và protocol version** của port đó để cấu hình target group. Nếu tên không khớp port nào trong task definition mà service đang dùng, request thất bại.
- **Health check** và **bộ lọc access type** là tuỳ chọn.

### 3.1. Bộ lọc access type (`filters.accessTypes`)

**📘 Theo docs AWS**

- `filters.accessTypes` thu hẹp các gateway mà service kết nối. Đặt `PUBLIC`, `PRIVATE`, hoặc cả hai; **bỏ trống = chấp nhận cả hai**.
- Gateway `PUBLIC` truy cập được từ internet.
- Gateway `PRIVATE` chỉ truy cập được từ mạng có thể tới internal load balancer của nó; request từ internet không tới được.
- Bộ lọc **chỉ dùng để chọn gateway**; nó không cấu hình traffic rule hay security rule.

Ví dụ chỉ kết nối tới gateway private:

```bash
aws ecs update-service \
  --cluster <TÊN_CLUSTER> \
  --service <TÊN_SERVICE> \
  --gateway-connectors '[{ "portName": "<TÊN_PORT>", "filters": { "accessTypes": ["PRIVATE"] } }]'
```

- Thu hẹp bộ lọc có hiệu lực ở **deployment mà nó khởi động**. Service bỏ kết nối tới các gateway không còn khớp, và URL của các kết nối đó **ngừng hoạt động**.

> **Important (docs):** Service **không bao giờ được lộ ra internet** thì **phải** lọc `PRIVATE`. Nếu không lọc, khi sau này có ai liên kết một gateway internet-facing với cluster, service sẽ bị expose ra internet ở **lần deploy kế tiếp**.

**💡 Giải thích thêm**

Nhắc lại từ trang *Create and manage*: access type được suy ra từ **scheme** của gateway (`internet-facing` → `PUBLIC`, `internal` → `PRIVATE`).

| `filters.accessTypes` | Service kết nối tới |
|---|---|
| (bỏ trống) | Mọi gateway tương thích, cả public lẫn private |
| `["PUBLIC"]` | Chỉ gateway internet-facing |
| `["PRIVATE"]` | Chỉ gateway internal |
| `["PUBLIC","PRIVATE"]` | Như bỏ trống |

Điểm Important ở trên là rủi ro bảo mật thật: bỏ trống bộ lọc nghĩa là service **phụ thuộc vào việc platform team gắn gateway gì vào cluster trong tương lai**. Service nội bộ (admin API, service chỉ cho service khác gọi) nên đặt `PRIVATE` ngay từ đầu.

### 3.2. Health check

**📘 Theo docs AWS**

- ECS cấu hình health check trên target group nó tạo cho **mỗi gateway route**, và chỉ chuyển traffic tới target vượt qua health check.
- **Mặc định**: gửi HTTP request tới `/` trên port mà `portName` chỉ định, **mỗi 30 giây**, chờ phản hồi `200` trong vòng **5 giây**. Service nào trả `200` ở `/` thì không cần cấu hình health check.
- Nên đổi path nếu `/` **chậm** hoặc **làm việc nặng** (truy vấn database, render cả trang). Health check sẽ lặp lại việc đó mỗi 30 giây cho từng target, và phản hồi chậm có thể vượt timeout, khiến target khoẻ bị đánh dấu unhealthy. Một endpoint nhẹ như `/health` tránh được cả hai vấn đề.

```bash
aws ecs update-service \
  --cluster <TÊN_CLUSTER> \
  --service <TÊN_SERVICE> \
  --gateway-connectors '[{ "portName": "<TÊN_PORT>", "healthCheck": { "healthCheckPath": "/health" } }]'
```

Ý nghĩa từng thiết lập health check: xem *Health checks for Application Load Balancer target groups* — <https://docs.aws.amazon.com/elasticloadbalancing/latest/application/target-group-health-checks.html>.

> **Important (docs):** Thay đổi health check sẽ **thay target group**, nên **không làm được** trên service dùng deployment strategy `ROLLING` — request báo lỗi rằng thuộc tính target group không đổi được với ROLLING. Hãy dùng strategy `BLUE_GREEN` hoặc `CANARY`, hoặc **đặt health check ngay lần đầu** kết nối service.

**💡 Giải thích thêm**

- Docs chỉ ví dụ trường `healthCheckPath`; các thiết lập khác tham chiếu sang tài liệu health check của ALB target group.
- Bài học thực tế từ điểm Important: nếu service dùng `ROLLING`, hãy **chốt health check path ngay khi thêm connector lần đầu**, vì đổi về sau sẽ bị từ chối.

---

## 4. ECS chọn gateway tương thích như thế nào

**📘 Theo docs AWS**

Trong mỗi lần deploy service, ECS xét các gateway đã liên kết với cluster (tối đa **5** mỗi cluster) và thu hẹp danh sách ứng viên. Gateway không khớp sẽ bị **bỏ qua**, chứ không làm deploy thất bại.

ECS so khớp theo ba tiêu chí:

| Tiêu chí | Điều kiện để hợp lệ |
|---|---|
| **Access type** | Gateway khớp `filters.accessTypes` của connector. Bỏ trống bộ lọc thì cả `PUBLIC` và `PRIVATE` đều hợp lệ. |
| **VPC** | Gateway cùng VPC với subnet của service. Gateway ở VPC khác không hợp lệ. |
| **Availability Zone** | Gateway phải có subnet ở **mọi AZ** mà subnet của service sử dụng. Gateway chỉ trùng **một phần** AZ thì không hợp lệ. |

- ECS kết nối service tới **mọi** gateway hợp lệ.
- Một gateway không tương thích **tự nó không làm deploy thất bại**, miễn là còn gateway khác hợp lệ. Nếu **không còn** gateway nào hợp lệ, request thất bại vì service không tạo được kết nối.

**Vì sao AZ phải phủ đủ:** gateway chỉ tới được các AZ nơi nó có subnet. Task khởi chạy ở AZ mà gateway không phủ sẽ **không bao giờ qua health check**, và ECS sẽ thay task đó. Phủ đủ AZ bảo đảm task chạy ở bất kỳ AZ nào của service đều đăng ký được thành target khoẻ phía sau gateway.

**Ví dụ trong docs** (hai gateway GW1, GW2 và service S1 cùng VPC):

| Tài nguyên | AZ đã cấu hình | Hợp lệ với S1? |
|---|---|---|
| GW1 | `us-west-2a`, `us-west-2b`, `us-west-2c` | **Có** — phủ cả hai AZ của service |
| GW2 | `us-west-2a`, `us-west-2b` | **Không** — thiếu `us-west-2c` |
| S1 | `us-west-2b`, `us-west-2c` | |

Giả sử access type cũng khớp, ECS chỉ kết nối S1 với **GW1**. Nếu nối với GW2, task chạy ở `us-west-2c` có thể trượt health check của load balancer và bị thay liên tục.

> **Important (docs):** Gateway connector **không chọn gateway theo tên hay ARN**. Service kết nối tới **mọi** gateway trên cluster vượt qua ba kiểm tra. Gateway trải **nhiều AZ hơn** mức service cần là cấu hình **an toàn hơn**. Muốn service nối vào một gateway cụ thể, hãy cấu hình gateway đó phủ mọi AZ mà task của service chạy. Thay đổi association của cluster cần phối hợp với platform team vì có thể ảnh hưởng nhiều service.

**💡 Giải thích thêm**

Logic chọn gateway, viết dạng giả mã:

```
ứng_viên = gateway đã associate với cluster (≤ 5)
hợp_lệ = [g for g in ứng_viên
          if g.accessType ∈ filter (hoặc filter trống)
          and g.vpc == service.vpc
          and service.AZs ⊆ g.AZs]
nếu hợp_lệ rỗng → request thất bại
ngược lại       → tạo 1 route cho mỗi g trong hợp_lệ
```

Điều kiện AZ là **tập con**: AZ của service phải nằm trọn trong AZ của gateway. Điều này cũng giải thích lời khuyên "gateway nên phủ rộng hơn service".

---

## 5. Hiểu về service deployment

**📘 Theo docs AWS**

- Tạo hoặc cập nhật service có gateway connector sẽ **khởi động một deployment**. ECS tạo gateway route và tài nguyên riêng của service **trong** deployment đó.
- Kết nối **đầu tiên** vào một gateway có thể lâu hơn, vì ECS có thể phải tạo ALB dùng chung trước. Các service sau **dùng lại** ALB, listener và security group đó.
- Khi bạn cập nhật connector, hoặc deploy service sau khi association của cluster thay đổi, ECS **đánh giá lại** gateway nào hợp lệ: tạo route tới gateway mới hợp lệ và xoá kết nối không còn khớp.

### 5.1. Test traffic đi tới revision mới như thế nào

**📘 Theo docs AWS**

- Trong progressive deployment, request mang **header và giá trị test traffic của gateway** sẽ đi tới revision mới; mọi request khác vẫn tới revision hiện tại.
- Header này do **platform team đặt trên gateway**, không phải trên service, nên mọi service phía sau gateway dùng chung một header. **Hãy hỏi platform team** header là gì.
- Đổi header chỉ tới service của bạn ở **lần deploy kế tiếp**, nên service có thể vẫn đang khớp header cũ. (Xem *Configure test traffic rules* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/gateway-managing.html#gateway-test-traffic-rules>.)

**💡 Giải thích thêm**

Quy tắc chung xuyên suốt trang: **mọi thay đổi liên quan gateway chỉ có hiệu lực qua một lần deploy của service**:

| Sự kiện | Khi nào service bị ảnh hưởng |
|---|---|
| Thêm / sửa / xoá connector | Deployment do chính lệnh đó khởi động |
| Platform team associate / disassociate gateway | Lần deploy kế tiếp của service |
| Platform team đổi header test traffic | Lần deploy kế tiếp của service |

---

## 6. Kiểm tra kết nối và tìm URL của service

**📘 Theo docs AWS**

- Mỗi gateway hợp lệ tạo ra **một kết nối riêng** với **hostname HTTPS riêng**.
- Các kết nối này thuộc về một **service revision**, nên một lệnh trả về mọi kết nối của revision bạn chỉ định, kèm **URL, access type và trạng thái** của từng kết nối. Trong lúc deploy có nhiều revision cùng tồn tại, mỗi revision có kết nối riêng.
- ARN của revision lấy từ deployment: `describe-service-deployments` trả về ARN revision mà deployment đang chuyển tới, ở trường `targetServiceRevision.arn`. (Xem *Viewing Amazon ECS service revision details* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/view-service-revision.html>.)

```bash
aws ecs describe-service-revisions \
  --service-revision-arns <ARN_SERVICE_REVISION> \
  --query 'serviceRevisions[0].ecsManagedResources.gatewayConnections'
```

Mỗi kết nối có:
- `serviceUrl` — hostname HTTPS để gửi traffic tới;
- `accessType` — cho biết hostname đó vươn tới đâu (public hay private);
- `status` — trạng thái kết nối.

Về hostname:
- Dạng `https://<unique-id>.ecs.<region>.on.aws`; domain khác nhau theo AWS partition.
- Phần định danh là **ngẫu nhiên**, **không** suy ra từ tên service. Xoá service rồi tạo lại **cùng tên** sẽ nhận **URL khác**.

Về `status`:
- Kiểm tra `status` của **từng** kết nối, đợi tới khi báo `ACTIVE` mới chuyển traffic client vào URL đó.
- `CREATE_IN_PROGRESS` khi đang tạo; `ACTIVE` khi sẵn sàng. Chạy lại lệnh cho tới khi các kết nối cần thiết đều `ACTIVE`. Service nối hai gateway thì có **hai** kết nối cần kiểm tra.
- `CREATE_FAILED` nghĩa là việc tạo **đã thất bại**, không phải đang chạy; chờ thêm cũng không thay đổi. (Xem *The service hostname does not respond* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/gateway-troubleshooting.html#gateway-ts-url-no-response>.)

Thiết lập gateway nằm trong `DescribeServiceRevisions` vì connector có thể đổi giữa các lần deploy, và revision ghi lại đúng cấu hình lúc nó được deploy. Trạng thái route, kiểm tra tài nguyên, và lần theo một route qua mọi tài nguyên trên đường đi: xem *Monitor gateway routes* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/gateway-monitoring.html#gateway-inspect-routes>.

**💡 Giải thích thêm**

Quy trình lấy URL:

```
describe-service-deployments ──► targetServiceRevision.arn
            │
            ▼
describe-service-revisions --query '...gatewayConnections'
            │
            ▼
mỗi phần tử: serviceUrl | accessType | status
   CREATE_IN_PROGRESS → chờ, chạy lại
   ACTIVE             → dùng được URL
   CREATE_FAILED      → không chờ nữa, đi xử lý lỗi
```

Hệ quả thực tế của "hostname ngẫu nhiên": không nên hard-code hostname `*.on.aws` vào nhiều nơi, vì xoá–tạo lại service, hoặc các tình huống ở mục 7–8 và trang *Create and manage*, đều có thể đổi URL.

---

## 7. Di chuyển (migrate) service

### 7.1. Service đã có load balancer riêng

**📘 Theo docs AWS**

- Chỉ cần **thêm gateway connector**. ECS tạo gateway route và hostname mới, **không xoá cũng không sửa** cấu hình load balancer hiện có của bạn.
- **Hai đường ingress phục vụ song song**, nên bạn kiểm tra URL gateway trước, rồi gỡ load balancer riêng ở một lần cập nhật sau.

**💡 Giải thích thêm**

```
Giai đoạn 1: [ALB riêng] ──► service      (đang dùng)
Giai đoạn 2: [ALB riêng] ──► service ◄── [Gateway URL]   (song song, test gateway)
Giai đoạn 3:                 service ◄── [Gateway URL]   (gỡ ALB riêng)
```

Đây là cách chuyển không downtime mà docs gợi ý.

### 7.2. Chuyển service từ gateway này sang gateway khác

**📘 Theo docs AWS**

- Cần **phối hợp với platform team**. Vì connector không chỉ định ARN gateway, việc migrate được điều khiển qua **cluster association** và **bộ lọc access type**.
- Đó cũng là lý do các thiết lập cố định của gateway (scheme, subnet, listener port, protocol) không đổi tại chỗ được — tạo gateway thay thế và chuyển service sang là cách duy nhất để có giá trị khác.

Các bước:
1. **Tạo và associate gateway đích.** Xác nhận access type và VPC khớp với service, subnet phủ **mọi AZ** của task. Gateway chỉ phủ một phần AZ thì không hợp lệ và service sẽ không kết nối.
2. **Redeploy service** để kết nối tới gateway đích. Nếu gateway cũ vẫn hợp lệ, hai route có thể cùng tồn tại trong giai đoạn chuyển tiếp.
3. **Xác nhận** kết nối tới gateway đích báo `ACTIVE`, rồi cập nhật các client đang dùng hostname cũ.
4. **Platform team gỡ liên kết gateway cũ** khi không cần nữa. Redeploy các service bị ảnh hưởng để xoá route cũ.

> **Important (docs):** Gỡ liên kết gateway ảnh hưởng **mọi service** dùng association đó. Lên kế hoạch cùng các app team khác. Hostname cũ ngừng hoạt động sau khi gateway route của nó bị xoá.

**💡 Giải thích thêm**

```
B1  Platform: tạo GW_mới + associate cluster
B2  App:      redeploy → service có route tới GW_cũ và GW_mới (2 URL)
B3  App:      chờ GW_mới ACTIVE → đổi client sang URL mới
B4  Platform: disassociate GW_cũ → App: redeploy → route cũ bị xoá, URL cũ chết
```

---

## 8. Ngắt kết nối service khỏi gateway

**📘 Theo docs AWS**

Ngắt khỏi **mọi** gateway: truyền danh sách connector rỗng.

```bash
aws ecs update-service --cluster <TÊN_CLUSTER> --service <TÊN_SERVICE> --gateway-connectors '[]'
```

- Muốn **chỉ giữ một số access type**, hãy cập nhật `filters.accessTypes`. Ở lần deploy kế tiếp, ECS xoá route của gateway không còn khớp.
- **Không thể** dùng bộ lọc access type để gỡ **một gateway cụ thể** trong khi giữ một gateway hợp lệ khác **cùng access type**.

Khi service ngắt kết nối:
- ECS xoá gateway route và tài nguyên ingress riêng của service. Gateway và các association của nó **vẫn còn** cho service khác dùng.
- **Xoá một service** đang kết nối cũng xoá các kết nối gateway của nó.
- Nếu một ALB được quản lý không còn service nào, ECS xoá ALB đó.
- Association của cluster giữ `DISASSOCIATING` cho tới khi mọi gateway route trên cluster bị xoá, rồi ECS xoá association. (Xem *Disassociate a gateway from a cluster* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/gateway-managing.html#gateway-disassociate-cluster>.)

Ngắt kết nối có hiệu lực ở **deployment nó khởi động**, nên service vẫn phục vụ traffic qua gateway cho tới khi deployment đó xong.

> **Important (docs):** Gỡ gateway connector khiến service **không truy cập được qua hostname gateway** sau khi route tương ứng bị xoá. Đảm bảo service có đường ingress khác nếu vẫn cần truy cập.

**💡 Giải thích thêm**

| Muốn | Cách làm theo docs |
|---|---|
| Ngắt khỏi tất cả gateway | `--gateway-connectors '[]'` |
| Bỏ hết gateway public, giữ private | `filters.accessTypes: ["PRIVATE"]` |
| Bỏ đúng **một** gateway, giữ gateway khác **cùng** access type | Không làm được bằng connector; cần platform team điều chỉnh association hoặc cấu hình gateway (theo logic mục 4 và 7.2) |

---

## 9. Tóm tắt

- **Connector** là tham số duy nhất app team cần: `gatewayConnectors` trên service, tối đa 1 connector, nhưng có thể sinh nhiều route.
- **Điều kiện**: cluster có gateway `ACTIVE`; task definition `awsvpc` + named port; ít nhất một gateway tương thích.
- **`portName` bắt buộc** — tham chiếu named port; ECS lấy port, protocol, protocol version cho target group.
- **`filters.accessTypes`**: `PUBLIC` / `PRIVATE` / cả hai (bỏ trống = cả hai). Service nội bộ **phải** đặt `PRIVATE`.
- **Health check** mặc định: HTTP `/`, mỗi 30 giây, chờ `200` trong 5 giây. Đổi health check thay target group → không làm được với `ROLLING`; chốt ngay từ đầu hoặc dùng `BLUE_GREEN`/`CANARY`.
- **Chọn gateway**: theo access type, VPC, AZ (phủ đủ, không phủ một phần); không chọn theo tên/ARN; nối tới **mọi** gateway hợp lệ; không còn gateway hợp lệ → request thất bại.
- **Deployment**: mọi thay đổi chỉ có hiệu lực qua deploy; kết nối đầu tiên có thể lâu hơn vì phải tạo ALB.
- **URL**: lấy qua `describe-service-revisions` → `gatewayConnections` (`serviceUrl`, `accessType`, `status`); hostname ngẫu nhiên; chờ `ACTIVE`; `CREATE_FAILED` thì không chờ nữa.
- **Migrate**: từ ALB riêng → thêm connector, chạy song song; giữa các gateway → qua association và bộ lọc, phối hợp platform team.
- **Ngắt kết nối**: `--gateway-connectors '[]'` hoặc thu hẹp bộ lọc; có hiệu lực sau deploy; đảm bảo có ingress thay thế.

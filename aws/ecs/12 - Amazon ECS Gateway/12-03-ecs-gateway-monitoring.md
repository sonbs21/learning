# Monitor a gateway

> Nguồn: **Amazon ECS Developer Guide — Monitor a gateway**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/gateway-monitoring.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: diễn đạt lại bằng tiếng Việt, bám sát từng ý và đúng thứ tự của trang gốc, không thêm thông tin ngoài trang. Đây không phải bản dịch từng chữ; khi cần câu chữ hoặc ví dụ code chính xác, đọc link gốc ở trên.
> - Khối **💡 Giải thích thêm**: phần giải thích để dễ hiểu hơn, chỉ làm rõ những gì docs đã nói.
> - Lệnh CLI dùng placeholder (`<...>`); tên lệnh, tên tham số, tên trường, giá trị trạng thái và tên metric giữ đúng như docs.

---

## Mục lục

0. [Giới thiệu](#0-giới-thiệu)
1. [Xem trạng thái gateway](#1-xem-trạng-thái-gateway)
2. [Kiểm tra hạ tầng được quản lý (góc nhìn platform)](#2-kiểm-tra-hạ-tầng-được-quản-lý-góc-nhìn-platform)
3. [Giám sát gateway route](#3-giám-sát-gateway-route)
4. [Giám sát traffic và sức khoẻ target](#4-giám-sát-traffic-và-sức-khoẻ-target)
5. [Xem log traffic và thao tác](#5-xem-log-traffic-và-thao-tác)
6. [Tóm tắt](#6-tóm-tắt)

---

## 0. Giới thiệu

**📘 Theo docs AWS**

Giám sát ECS Gateway giúp bạn xem trạng thái gateway, xác định hạ tầng nó quản lý và kiểm tra kết nối giữa gateway với các ECS service. Ngoài ra có thể dùng **CloudWatch** và **Elastic Load Balancing** để theo dõi traffic request và sức khoẻ của các target nhận traffic đó.

Có ba loại thông tin:

- **Trạng thái gateway và association** — gateway có sẵn sàng không, lần cập nhật cấu hình có thành công không, gateway đang gắn với những cluster nào.
- **Trạng thái gateway route** — ECS đã tạo hoặc cập nhật xong hạ tầng ingress cho một kết nối service cụ thể chưa.
- **Traffic và sức khoẻ target** — load balancer có nhận request không, các target phục vụ service có khoẻ không.

Ba loại trạng thái này **độc lập nhau**:
- Gateway có thể `ACTIVE` dù một route riêng lẻ tạo thất bại.
- Route có thể `ACTIVE` dù target của ứng dụng đang unhealthy.

**💡 Giải thích thêm**

```
Tầng 1: Gateway          → cấu hình gateway có ổn không?        (describe-gateway)
Tầng 2: Gateway route    → ingress cho từng service đã dựng xong? (list/describe-gateway-route)
Tầng 3: Traffic/target   → ứng dụng có nhận và trả lời được?   (CloudWatch, describe-target-health)
```

"Xanh" ở tầng trên **không đảm bảo** "xanh" ở tầng dưới. Khi debug, kiểm tra cả ba tầng.

---

## 1. Xem trạng thái gateway

**📘 Theo docs AWS**

Dùng `describe-gateway` để xem cấu hình và trạng thái hiện tại:

```bash
aws ecs describe-gateway --gateway <TÊN_GATEWAY>
```

Kết quả gồm trạng thái, infrastructure IAM role và cấu hình của gateway. Dùng nó để xác nhận một thay đổi trên hạ tầng được quản lý đã hoàn tất chưa.

- Khi một lần cập nhật bắt đầu, `statusReason` cho biết **bao nhiêu load balancer đã cập nhật trên tổng số**. Ví dụ docs: thành công hết thì dạng "12/12 load balancers updated"; có lỗi thì dạng "10/12 updated, 2 failed update".
- Gateway **chưa từng được cập nhật** thì không có `statusReason`.
- `failures` **chỉ xuất hiện** khi gateway ở trạng thái lỗi; mỗi mục ghi ARN tài nguyên, thao tác thất bại, mã lỗi và thông điệp. Cách xử lý: xem *Gateway update failures* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/gateway-troubleshooting.html#gateway-ts-update-failed>.
- Kết quả này **không chứa** association với cluster và route của gateway.

Liệt kê gateway trong account + Region:

```bash
aws ecs list-gateways
```

Xem các cluster đã gắn với gateway:

```bash
aws ecs list-gateway-cluster-associations --gateway <TÊN_GATEWAY>
```

Association `ACTIVE` giúp service tương thích trong cluster dùng được gateway. Association `DISASSOCIATING` tồn tại cho tới khi các route của cluster đó bị xoá.

### 1.1. Trạng thái của gateway, association và route

**📘 Theo docs AWS**

| Tài nguyên | API | Giá trị trạng thái | Ý nghĩa |
|---|---|---|---|
| **Gateway** | DescribeGateway, ListGateways | `ACTIVE`, `UPDATE_IN_PROGRESS`, `UPDATE_COMPLETE`, `UPDATE_FAILED`, `DELETED` | Trạng thái cấu hình của gateway và kết quả cập nhật hạ tầng nó quản lý |
| **Gateway cluster association** | ListGatewayClusterAssociations | `ACTIVE`, `DISASSOCIATING` | Dùng được cho kết nối service mới, hay đang chờ route hiện có bị xoá |
| **Gateway route** | DescribeGatewayRoute, ListGatewayRoutes | `ACTIVE`, `CREATE_IN_PROGRESS`, `CREATE_FAILED`, `UPDATE_IN_PROGRESS`, `UPDATE_FAILED`, `DELETE_IN_PROGRESS`, `DELETE_FAILED` | Trạng thái tạo hoặc gỡ của một kết nối service–gateway và tài nguyên ingress của nó |

**Gateway:**
- `ACTIVE` từ lúc tạo cho tới lần cập nhật đầu tiên.
- Cập nhật: `UPDATE_IN_PROGRESS` → `UPDATE_COMPLETE` hoặc `UPDATE_FAILED`.
- Chỉ **`UPDATE_FAILED`** là trạng thái cần bạn xử lý: đọc `statusReason` và `failures` để biết load balancer nào chưa cập nhật và vì sao.
- `DELETED` là trạng thái cuối; request sau đó trả `GatewayNotFoundException`.

**Association:**
- `ACTIVE` khi tạo, `DISASSOCIATING` sau khi gỡ.
- Giữ `DISASSOCIATING` cho tới khi mọi route của cluster biến mất, nên `DISASSOCIATING` **kéo dài** chỉ là dấu hiệu **còn service đang kết nối**, không phải có lỗi.

**Gateway route:**
- Có vòng đời create, update, delete riêng; `ACTIVE` khi đang mang traffic.
- Ba trạng thái `*_FAILED` là nơi lộ ra vấn đề tạo hạ tầng **của từng service**. Tìm chúng bằng `list-gateway-routes --status CREATE_FAILED UPDATE_FAILED`.
- Ở góc nhìn ứng dụng, một connection **không có trạng thái riêng**: `status` của nó chính là trạng thái của gateway route, nên dùng cùng các giá trị trên.

> **Note (docs):** Gateway `ACTIVE` **không** có nghĩa mọi service phía sau đều khoẻ. Trạng thái gateway chỉ mô tả cấu hình của chính gateway. Deploy service tạo ingress thất bại sẽ hiện ở **gateway route**; ứng dụng unhealthy hiện ở **target group health**. Cả hai đều không làm thay đổi trạng thái gateway.

**💡 Giải thích thêm**

Vòng đời gateway theo docs:

```
create ──► ACTIVE ──update──► UPDATE_IN_PROGRESS ──► UPDATE_COMPLETE
                                         └─────────► UPDATE_FAILED   ← cần xử lý
delete ──► DELETED (cuối, sau đó GatewayNotFoundException)
```

Vòng đời route:

```
CREATE_IN_PROGRESS ──► ACTIVE ──► UPDATE_IN_PROGRESS ──► ACTIVE
        └─► CREATE_FAILED              └─► UPDATE_FAILED
ACTIVE ──► DELETE_IN_PROGRESS ──► (bị xoá)
                   └─► DELETE_FAILED
```

Bảng "đọc trạng thái nhanh":

| Thấy | Ý nghĩa | Làm gì |
|---|---|---|
| Gateway `UPDATE_FAILED` | Một số ALB chưa nhận cấu hình mới | Xem `statusReason`, `failures` |
| Association `DISASSOCIATING` lâu | Còn service chưa redeploy | Không phải lỗi; tìm route còn lại |
| Route `*_FAILED` | Ingress của một service có vấn đề | `describe-gateway-route`, xem trang troubleshoot |
| Route `ACTIVE` nhưng service không trả lời | Ingress ổn, ứng dụng/target có vấn đề | Xem target health (mục 4) |

---

## 2. Kiểm tra hạ tầng được quản lý (góc nhìn platform)

**📘 Theo docs AWS**

Dùng `describe-gateway-resources` để xác định các tài nguyên ingress mà gateway quản lý:

```bash
aws ecs describe-gateway-resources --gateway <TÊN_GATEWAY>
```

- Với gateway `hostSharedAlb`, kết quả **nhóm tài nguyên theo từng ALB**: mỗi nhóm cho biết load balancer, listener, security group và các gateway route gắn với nó.
- Dùng view này để biết ALB nào thuộc gateway và kết nối service nào dùng chung từng ALB.

Kết quả trả về **ARN** của tài nguyên ECS quản lý, **không phải trạng thái thực tế**. Theo docs, nếu trả cả trạng thái thì thông tin đó có thể đã cũ hoặc mâu thuẫn với dịch vụ sở hữu tài nguyên. Muốn biết trạng thái thật, dùng ARN với chính dịch vụ AWS sở hữu tài nguyên. Ví dụ, xem một ALB được quản lý:

```bash
aws elbv2 describe-load-balancers --load-balancer-arns <ARN_ALB>
```

- Listener, listener rule, target group: kiểm tra tương tự bằng các thao tác Elastic Load Balancing; security group: bằng Amazon EC2.
- Vì gateway chỉ báo cáo bản ghi của chính nó chứ không đọc trực tiếp, nó **không phát hiện** khi tài nguyên bị thay đổi bên ngoài ECS. (Xem *Changes made outside Amazon ECS* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/gateway-troubleshooting.html#gateway-changes-outside-ecs>.)

**💡 Giải thích thêm**

`describe-gateway-resources` giống như **bản đồ** (tài nguyên nào thuộc gateway, ALB nào chứa route nào), chứ không phải **đồng hồ đo** (tài nguyên đang chạy ra sao):

```
describe-gateway-resources ──► ARN của ALB / listener / SG / route
           │
           ├── aws elbv2 describe-load-balancers / describe-listeners / describe-rules / describe-target-groups
           └── aws ec2 describe-security-groups
                     ↑ trạng thái thật nằm ở đây
```

Kết hợp với cảnh báo ở trang *Amazon ECS Gateway*: ai đó sửa tay ALB thì gateway **vẫn báo bình thường**, vì nó không đọc lại trạng thái thật.

---

## 3. Giám sát gateway route

**📘 Theo docs AWS**

Dùng gateway route để theo dõi **từng kết nối service**. Mỗi route có trạng thái tạo hạ tầng và URL service riêng, kể cả khi nhiều route dùng chung một ALB.

### 3.1. Liệt kê gateway route

Mọi kết nối service qua một gateway:

```bash
aws ecs list-gateway-routes --gateway <TÊN_GATEWAY>
```

Route của một service cụ thể (chỉ định cluster và service):

```bash
aws ecs list-gateway-routes --cluster <TÊN_CLUSTER> --service <TÊN_SERVICE>
```

- Kết quả gồm ARN route, service và gateway liên quan, trạng thái route.
- Lọc được theo gateway, cluster, service, tên container port hoặc trạng thái.
- Lọc theo service: truyền **ARN đầy đủ** của service, hoặc tên service **kèm** cluster. Chỉ tên service thì mơ hồ (tên chỉ duy nhất trong một cluster), nên ECS trả lỗi.

Tìm route tạo hoặc cập nhật thất bại:

```bash
aws ecs list-gateway-routes --gateway <TÊN_GATEWAY> --status CREATE_FAILED UPDATE_FAILED
```

- `CREATE_IN_PROGRESS`: vẫn đang tạo tài nguyên ingress. `ACTIVE`: đã xong.
- Route lỗi: kiểm tra bằng `describe-gateway-route` và xem *Troubleshoot a gateway* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/gateway-troubleshooting.html>.

### 3.2. Kiểm tra một gateway route

```bash
aws ecs describe-gateway-route --gateway-route-arn <ARN_GATEWAY_ROUTE>
```

Kết quả gồm trạng thái route, URL service và định danh các tài nguyên ingress của kết nối đó:
- certificate;
- load balancer và listener;
- listener rule cho **production traffic**;
- listener rule cho **test traffic**;
- security group của load balancer;
- security group của service (nơi gateway quản lý ingress rule).

Dùng các định danh này để lần theo một request từ ALB dùng chung tới service.
- Một route thường chỉ có **một** test listener rule; có nhiều hơn chỉ trong lúc đang chuyển từ cấu hình header test cũ sang mới.
- Muốn biết **target group** nhận production traffic, hoặc **alternate target group** mà progressive deployment dùng, hãy xem **service revision** (mục 3.3).

### 3.3. Xem gateway connection của một service revision (góc nhìn ứng dụng)

App team có thể xem các gateway connection được ghi nhận cho một service revision đã deploy:

```bash
aws ecs describe-service-revisions \
  --service-revision-arns <ARN_SERVICE_REVISION> \
  --query 'serviceRevisions[0].ecsManagedResources.gatewayConnections'
```

Mỗi connection cho biết:
- gateway và route, tên container port, access type, trạng thái kết nối, URL HTTPS được sinh;
- `status` chính là trạng thái của gateway route;
- các tài nguyên ingress của service: certificate, load balancer và listener, listener rule cho production và test, security group của load balancer, và **target group production + alternate**.

Trong progressive deployment, revision mới được đăng ký vào **alternate target group**, còn revision hiện tại tiếp tục phục vụ từ target group **production**.

Xem service revision khi muốn kiểm tra một deployment cụ thể được cấu hình những kết nối nào. **So sánh hai revision** cho thấy ingress của service đã thay đổi gì giữa các lần deploy. Cách tạo, sửa, gỡ kết nối: xem *Connect Amazon ECS services to a gateway* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/gateway-connect-service.html>.

**💡 Giải thích thêm**

So sánh hai góc nhìn theo docs:

| | `describe-gateway-route` (platform) | `describe-service-revisions` (application) |
|---|---|---|
| Đầu vào | ARN route | ARN service revision |
| Trạng thái + URL | Có | Có |
| Certificate, ALB, listener, SG | Có | Có |
| Listener rule production / test | Có | Có |
| SG của service | Có | — (docs không liệt kê) |
| Target group production / alternate | **Không** — docs bảo xem revision | **Có** |
| Gắn với một lần deploy cụ thể | Không | Có (so sánh được giữa revision) |

Đường đi của một request, ghép từ các định danh trên:

```
Client ─► ALB dùng chung ─► listener HTTPS :443 (+ certificate)
             ─► listener rule (production)  ─► target group production ─► task revision hiện tại
             ─► listener rule (test header) ─► alternate target group   ─► task revision mới (khi deploy)
```

---

## 4. Giám sát traffic và sức khoẻ target

**📘 Theo docs AWS**

Sau khi kết nối service, cần theo dõi hai thứ: **traffic tới service** và **sức khoẻ target** phục vụ nó. ECS Gateway **không tự phát metric** cho việc này; thay vào đó bạn dùng metric của ALB mà Elastic Load Balancing đẩy lên CloudWatch, và đọc target health trực tiếp từ Elastic Load Balancing.

### 4.1. Chọn đúng dimension cho metric

Một gateway đặt tới 25 service trên một ALB, nên metric tính cho **cả load balancer** là gộp của mọi service trên đó. Muốn xem **riêng một service**, truy vấn metric theo dimension **target group** thay vì load balancer.

| Phạm vi | Dimension khuyến nghị | Metric hữu ích |
|---|---|---|
| **Từng kết nối service** | `TargetGroup` kèm `LoadBalancer` | `RequestCount`, `TargetResponseTime`, `HTTPCode_Target_5XX_Count`, `HealthyHostCount`, `UnHealthyHostCount` |
| **Load balancer dùng chung** | `LoadBalancer` | `HTTPCode_ELB_5XX_Count`, `ActiveConnectionCount`, `RejectedConnectionCount`, `ConsumedLCUs` |
| **Availability Zone** | `AvailabilityZone` kèm dimension load balancer hoặc target group tương ứng | Metric target health và request, để biết vấn đề có chỉ giới hạn trong một AZ không |

- App team nên dùng dimension **target group**. Tăng đột biến request hay 5XX ở dimension load balancer có thể đến từ **service khác** dùng chung ALB, không quy được cho service của bạn.
- Target group của service nằm trong gateway connection mà `describe-service-revisions` trả về.
- Danh sách đầy đủ metric và statistic: *CloudWatch metrics for your Application Load Balancer* — <https://docs.aws.amazon.com/elasticloadbalancing/latest/application/load-balancer-cloudwatch-metrics.html>.

> **Note (docs):** Elastic Load Balancing chỉ phát metric **khi có traffic**, và **không tính** request health check. Service mới kết nối chưa có traffic sẽ không có metric request — đây là bình thường, không phải lỗi.

### 4.2. Kiểm tra target health

Metric cho thấy xu hướng theo thời gian. Muốn biết sức khoẻ target **ngay lúc này**, đọc trực tiếp target group:

```bash
aws elbv2 describe-target-health --target-group-arn <ARN_TARGET_GROUP>
```

- Kết quả cho trạng thái hiện tại của từng target đã đăng ký, kèm **lý do** khi target unhealthy (ví dụ health check thất bại và mã phản hồi nhận được).
- Route `ACTIVE` chỉ có nghĩa ECS đã tạo xong ingress, **không** có nghĩa target phía sau khoẻ → phải kiểm tra target health riêng.
- Target unhealthy kéo dài: xem *Service connectivity and target health* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/gateway-troubleshooting.html#gateway-connectivity-issues>.

### 4.3. Cấu hình CloudWatch alarm

- Tạo alarm cho metric quan trọng với ứng dụng: target unhealthy, response time của target tăng, lỗi 5XX từ target.
- Platform team có thể alarm thêm 5XX của load balancer và rejected connection cho hạ tầng dùng chung.
- Gateway **thêm và xoá ALB** theo service kết nối/ngắt kết nối, nên alarm gắn cứng với ARN của một load balancer hoặc target group có thể **âm thầm ngừng đo** khi tài nguyên đó biến mất.
- ECS gắn ARN gateway làm tag lên mọi ALB nó tạo. Hãy **chọn tài nguyên theo tag** đó để alarm và dashboard tiếp tục bám theo gateway khi hạ tầng thay đổi. (Xem *Using resource tags for telemetry* — <https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/UsingResourceTagsForTelemetry.html>.)
- Vì có metric không phát ở mọi chu kỳ, hãy chọn cách alarm xử lý **missing data**.

**💡 Giải thích thêm**

Phân chia trách nhiệm giám sát theo docs:

| Ai | Nhìn ở đâu | Alarm gợi ý (theo docs) |
|---|---|---|
| App team | Dimension `TargetGroup` (+ `LoadBalancer`) | `UnHealthyHostCount`, `TargetResponseTime`, `HTTPCode_Target_5XX_Count` |
| Platform team | Dimension `LoadBalancer` | `HTTPCode_ELB_5XX_Count`, `RejectedConnectionCount` |

Hai cái bẫy docs chỉ ra:
1. **Nhìn nhầm dimension** — xem metric cấp ALB rồi tưởng là của service mình.
2. **Alarm gắn ARN cứng** — ALB bị gateway xoá/thay thì alarm vẫn "OK" nhưng thực ra không đo gì. Kết hợp thêm việc metric chỉ có khi có traffic, nên cần chọn đúng cách xử lý missing data, nếu không alarm có thể im lặng khi lẽ ra phải báo.

---

## 5. Xem log traffic và thao tác

### 5.1. Access log của Application Load Balancer

**📘 Theo docs AWS**

- Khi bật access log cho các ALB do gateway quản lý, Elastic Load Balancing gửi bản ghi request tới S3 bucket đã cấu hình. Access log chứa thông tin request và response, giúp xem mẫu traffic và điều tra từng request.
- Vì ALB dùng chung, log của một ALB có thể chứa request của **nhiều service**. Dùng **hostname** trong từng bản ghi để phân biệt request của các service.
- Access log mặc định **tắt**. Cách bật: *Enable access logs* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/gateway-managing.html#gateway-access-logs>. Định dạng log và cách gửi: *Access logs for your Application Load Balancer* — <https://docs.aws.amazon.com/elasticloadbalancing/latest/application/load-balancer-access-logs.html>.

### 5.2. Tìm chi tiết lỗi

**📘 Theo docs AWS**

| Lỗi quan sát được | Xem ở đâu | Thông tin có được |
|---|---|---|
| Cập nhật cấu hình gateway báo `UPDATE_FAILED` | `describe-gateway` | `statusReason` và `failures` cho biết tài nguyên bị ảnh hưởng và thao tác thất bại |
| Kết nối service báo `CREATE_FAILED` hoặc `UPDATE_FAILED` | `describe-gateway-route` | Trạng thái route và tài nguyên ingress của kết nối |
| Deploy service không đạt trạng thái mong muốn | `describe-service-revisions` | Gateway connection của revision và ngữ cảnh về các thao tác deploy |
| Request lỗi hoặc target unhealthy | Target health của ELB, metric CloudWatch, access log (nếu bật) | Trạng thái target, metric request, thông tin từng request |

Dùng các nguồn này để xác định tài nguyên hoặc kết nối nào bị ảnh hưởng. Triệu chứng, nguyên nhân và cách khắc phục: *Troubleshoot a gateway* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/gateway-troubleshooting.html>.

**💡 Giải thích thêm**

Bảng 5.2 có thể dùng như một quy trình debug từ trên xuống, khớp với ba tầng ở mục 0:

```
1. describe-gateway            → gateway có UPDATE_FAILED không?
2. list-gateway-routes --status CREATE_FAILED UPDATE_FAILED
   describe-gateway-route      → route của service có lỗi không?
3. describe-service-revisions  → deployment này được cấu hình kết nối nào? status?
4. elbv2 describe-target-health + CloudWatch (dimension TargetGroup) + access log (lọc theo hostname)
                               → ứng dụng có khoẻ và trả lời đúng không?
```

---

## 6. Tóm tắt

- **Ba tầng độc lập**: trạng thái gateway/association, trạng thái route, traffic/target health. "Xanh" ở tầng trên không đảm bảo tầng dưới.
- **Gateway**: `describe-gateway` (`statusReason` dạng x/y ALB đã cập nhật, `failures` chỉ có khi lỗi); `list-gateways`; `list-gateway-cluster-associations`. Chỉ `UPDATE_FAILED` cần xử lý; `DISASSOCIATING` kéo dài không phải lỗi.
- **Hạ tầng**: `describe-gateway-resources` nhóm theo ALB, chỉ trả ARN; trạng thái thật xem bằng `elbv2` / `ec2`. Gateway không phát hiện thay đổi bên ngoài.
- **Route**: `list-gateway-routes` (lọc theo gateway, cluster, service, port, status; service phải kèm cluster hoặc dùng ARN); `describe-gateway-route` để lần theo request; target group production/alternate xem ở service revision.
- **Metric**: Gateway không tự phát metric; dùng metric ALB trên CloudWatch. App team dùng dimension `TargetGroup`; platform team dùng `LoadBalancer`. Không có traffic thì không có metric.
- **Target health**: `elbv2 describe-target-health`; route `ACTIVE` không có nghĩa target khoẻ.
- **Alarm**: chọn tài nguyên theo tag ARN gateway thay vì ARN cứng; cấu hình xử lý missing data.
- **Log**: access log ALB (mặc định tắt) lên S3, phân biệt service bằng hostname.

# Troubleshoot a gateway

> Nguồn: **Amazon ECS Developer Guide — Troubleshoot a gateway**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/gateway-troubleshooting.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: diễn đạt lại bằng tiếng Việt, bám sát từng ý và đúng thứ tự của trang gốc, không thêm thông tin ngoài trang. Đây không phải bản dịch từng chữ; khi cần câu chữ hoặc ví dụ code chính xác, đọc link gốc ở trên.
> - Khối **💡 Giải thích thêm**: phần giải thích để dễ hiểu hơn, chỉ làm rõ những gì docs đã nói.
> - Lệnh CLI dùng placeholder (`<...>`). Tên lệnh, tham số, trường, trạng thái và **mã lỗi / thông điệp lỗi** giữ nguyên tiếng Anh như docs để bạn tìm kiếm được.

---

## Mục lục

0. [Giới thiệu và kiểm tra ban đầu](#0-giới-thiệu-và-kiểm-tra-ban-đầu)
1. [Lỗi kết nối service và deployment](#1-lỗi-kết-nối-service-và-deployment)
2. [Lỗi tạo gateway route](#2-lỗi-tạo-gateway-route)
3. [Vấn đề capacity và quota](#3-vấn-đề-capacity-và-quota)
4. [Vấn đề IAM và quyền](#4-vấn-đề-iam-và-quyền)
5. [Kết nối tới service và sức khoẻ target](#5-kết-nối-tới-service-và-sức-khoẻ-target)
6. [Lỗi cập nhật gateway](#6-lỗi-cập-nhật-gateway)
7. [Vấn đề khi gỡ liên kết và xoá](#7-vấn-đề-khi-gỡ-liên-kết-và-xoá)
8. [Thay đổi làm bên ngoài ECS](#8-thay-đổi-làm-bên-ngoài-ecs)
9. [Bảng tra cứu mã lỗi](#9-bảng-tra-cứu-mã-lỗi)

---

## 0. Giới thiệu và kiểm tra ban đầu

**📘 Theo docs AWS**

Trang này hướng dẫn xử lý các vấn đề của ECS Gateway: lỗi kết nối service, lỗi tạo gateway route, lỗi cập nhật cấu hình gateway và lỗi kết nối mạng.

Gateway, gateway route và target ứng dụng **báo trạng thái độc lập nhau**:
- gateway có thể vẫn `ACTIVE` dù một route tạo thất bại;
- route có thể `ACTIVE` dù target ứng dụng unhealthy.

Để tìm nguyên nhân, xem lỗi do API trả về, hoặc kiểm tra trạng thái và chi tiết lỗi của gateway hay route bị ảnh hưởng.

Trước khi điều tra sâu hơn, kiểm tra:
- đúng **tên hoặc ARN** tài nguyên, đúng **account**, đúng **Region**;
- Region có hỗ trợ ECS Gateway, và bạn có đủ quyền;
- với lỗi kết nối service: task definition của service dùng network mode **`awsvpc`**.

Vẫn lỗi thì làm theo các quy trình bên dưới.

**💡 Giải thích thêm**

Checklist "0 phút" trước khi debug:

```
[ ] Đúng account / Region?
[ ] Đúng tên hoặc ARN gateway, cluster, service?
[ ] Region có ECS Gateway?
[ ] Đủ quyền?
[ ] Task definition là awsvpc?
```

---

## 1. Lỗi kết nối service và deployment

### 1.1. Cluster chưa có gateway nào được liên kết

**📘 Theo docs AWS**

- Request `CreateService`/`UpdateService` có gateway connector sẽ **thất bại** nếu cluster của service chưa liên kết gateway nào.
- ECS báo lỗi `gatewayConnectors were specified but no gateways are associated with cluster` và từ chối request, thay vì deploy một service mà nó không cấp được URL.
- Kiểm tra association của cluster:

```bash
aws ecs list-gateway-cluster-associations --cluster <TÊN_CLUSTER>
```

- Nếu không có association, liên hệ platform team để liên kết gateway trước khi deploy service có connector.
- Gateway có association đã chuyển sang `DISASSOCIATING` **không dùng được** cho kết nối mới.
- Lỗi này cũng xuất hiện sau khi platform team **xoá gateway cuối cùng** trên cluster. Khi đó, gỡ connector khỏi service hoặc đợi gateway thay thế trước lần cập nhật kế tiếp.

**💡 Giải thích thêm**

Ba tình huống cùng ra lỗi này: (1) cluster chưa từng có gateway; (2) gateway duy nhất đang `DISASSOCIATING`; (3) gateway cuối cùng đã bị xoá. Trường hợp (3) dễ bất ngờ: service vẫn còn connector cũ, nên lần `update-service` kế tiếp (kể cả chỉ đổi image) sẽ bị từ chối.

### 1.2. Không có gateway nào tương thích với service

**📘 Theo docs AWS**

ECS kết nối service tới mọi gateway đã liên kết thoả **cả ba** điều kiện:
- **Access type**: khớp `filters.accessTypes` của connector; bỏ trống thì cả `PUBLIC` lẫn `PRIVATE` đều được.
- **VPC**: gateway và service cùng VPC.
- **Availability Zone**: gateway phủ **mọi AZ** mà subnet của service dùng. Subnet ID **không cần trùng**, nhưng gateway phải có subnet trong từng AZ mà service dùng.

Gateway không thoả bị loại. Còn ít nhất một gateway tương thích thì service kết nối; không còn thì request thất bại. Hai thông điệp lỗi:
- `has associated gateways but none are in the service's VPC` — không gateway nào cùng VPC với service;
- `none of the gateways associated with cluster match` — bộ lọc access type loại hết các gateway còn lại.

**Quy trình xác định và xử lý:**

1. **Kiểm tra bộ lọc access type** — `filters.accessTypes` có khớp access type của gateway bạn muốn nối không.
2. **Lấy subnet của service** — dùng `list-service-deployments` để lấy ARN service revision, rồi `describe-service-revisions` để lấy subnet:

   ```bash
   aws ecs list-service-deployments --cluster <TÊN_CLUSTER> --service <TÊN_SERVICE> \
     --query 'serviceDeployments[].targetServiceRevisionArn'

   aws ecs describe-service-revisions --service-revision-arns <ARN_SERVICE_REVISION> \
     --query 'serviceRevisions[0].networkConfiguration.awsvpcConfiguration.subnets'
   ```

3. **Lấy subnet của gateway** — `aws ecs describe-gateway --gateway <TÊN_GATEWAY>` để xem cấu hình mạng.
4. **So sánh VPC và AZ** — dùng `describe-subnets` cho cả subnet của service và của gateway:

   ```bash
   aws ec2 describe-subnets --subnet-ids <SUBNET_IDS> \
     --query 'Subnets[].{SubnetId:SubnetId,VpcId:VpcId,AvailabilityZone:AvailabilityZone}'
   ```

   Xác nhận cùng VPC và gateway phủ mọi AZ của service.
5. **Xử lý** — sửa bộ lọc access type hoặc cấu hình subnet của service cho phù hợp. Nếu không có gateway tương thích, nhờ platform team cấu hình hoặc liên kết gateway khớp access type, VPC và AZ của service.

Sửa xong, chạy lại việc tạo hoặc cập nhật service.

> **Note (docs):** Để linh hoạt hơn, nên cấu hình gateway với subnet phủ **mọi AZ mà service có thể chạy**. Phủ rộng giúp một gateway phục vụ nhiều service hơn và giảm nhu cầu tạo thêm gateway. Subnet của gateway **không đổi được** sau khi tạo.

**💡 Giải thích thêm**

Đọc thông điệp lỗi để biết hỏng ở tiêu chí nào:

| Thông điệp | Tiêu chí hỏng | Hướng xử lý |
|---|---|---|
| `...none are in the service's VPC` | VPC | Service và gateway khác VPC |
| `...none of the gateways associated with cluster match` | Access type | Kiểm tra `filters.accessTypes` |
| (lỗi do thiếu AZ) | AZ | So AZ bằng `describe-subnets` ở bước 4 |

Docs nêu thông điệp cho trường hợp VPC và access type; với AZ, docs không nêu thông điệp riêng, nên bước 4 là cách kiểm tra.

### 1.3. Gateway connector tham chiếu container port không hợp lệ

**📘 Theo docs AWS**

- `portName` phải trùng **tên** của một port mapping trong task definition — là **tên port**, không phải số port.
- Không container nào có port mapping với tên đó thì ECS báo `InvalidConfiguration: "portName not found in task definition portMappings"`.
- Xem các tên port:

```bash
aws ecs describe-task-definition --task-definition <FAMILY>:<REVISION> \
  --query 'taskDefinition.containerDefinitions[].portMappings[].name'
```

- Sửa connector hoặc task definition cho khớp tên, rồi chạy lại. Kiểm tra lại mỗi khi cập nhật task definition, vì tên port từng hợp lệ có thể **không còn tồn tại**.

**💡 Giải thích thêm**

Lỗi hay gặp: đặt `portName` là `"8080"` (số) thay vì tên như `"web"`; hoặc tạo task definition revision mới mà quên khai báo `name` trong `portMappings`.

### 1.4. Cập nhật health check thất bại với strategy ROLLING

**📘 Theo docs AWS**

- Cập nhật health check của connector trả lỗi rằng thuộc tính target group không đổi được với deployment strategy `ROLLING`.
- Lý do: đổi các thiết lập đó cần **thay target group**, mà service dùng `ROLLING` không hỗ trợ việc thay này khi cập nhật.
- Cách xử lý: giữ nguyên health check hiện tại, hoặc dùng strategy **blue/green** hay **canary** cho thay đổi này.
- Nếu buộc phải ngắt rồi kết nối lại service để thay target group, hãy lường trước việc gateway route và **hostname bị xoá rồi tạo lại**. (Xem *Connect Amazon ECS services to a gateway* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/gateway-connect-service.html>.)

**💡 Giải thích thêm**

| Lựa chọn | Hệ quả |
|---|---|
| Giữ health check cũ | Không đổi gì |
| Chuyển sang blue/green hoặc canary | Đổi được health check |
| Ngắt rồi nối lại connector | Đổi được, nhưng **URL mới** → phải cập nhật client/DNS |

---

## 2. Lỗi tạo gateway route

**📘 Theo docs AWS**

Một kết nối service có thể có route ở `CREATE_FAILED` hoặc `UPDATE_FAILED`, hoặc deploy service không tạo xong ingress. Lỗi ở mức route **không nhất thiết** làm đổi trạng thái tổng của gateway hay ảnh hưởng route có sẵn của service khác.

1. **Tìm route lỗi:**

   ```bash
   aws ecs list-gateway-routes --gateway <TÊN_GATEWAY> --status CREATE_FAILED UPDATE_FAILED
   ```

2. **Xác định service sở hữu** route và tài nguyên gateway đã tạo cho nó:

   ```bash
   aws ecs describe-gateway-route --gateway-route-arn <ARN_GATEWAY_ROUTE>
   ```

3. **Xem trạng thái deployment và service events** để tìm lý do lỗi. Từ lý do đó xác định tài nguyên nào hỏng: cấp certificate, listener rule, target group, security group hay tài nguyên khác.
4. **Sửa nguyên nhân gốc và chạy lại** deploy service bị ảnh hưởng. Thay đổi cấu hình gateway **không nhất thiết** khởi động lại một deployment đã thất bại.

> **Note (docs):** Route chỉ báo **trạng thái lỗi**, không báo **lý do**. ECS ghi lý do ở **service deployment** đã kích hoạt việc tạo hạ tầng, và trong **service events**.

Nguyên nhân thường gặp:
- infrastructure role thiếu quyền;
- capacity load balancer hoặc service quota;
- subnet hết IP;
- tài nguyên bị xoá hoặc sửa bên ngoài ECS.

Hai lý do có thể gặp:
- `InsufficientAvailabilityZones` — subnet của gateway nằm ở **ít hơn hai AZ**, mức tối thiểu của ALB.
- `InvalidSubnet` — subnet đã cấu hình không tồn tại, thuộc VPC khác, hoặc **không còn IP trống**.

Các mục sau hướng dẫn điều tra quyền, capacity và thay đổi ngoài ECS. Quy trình giám sát: *Monitor a gateway* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/gateway-monitoring.html>. Quyền mà gateway `hostSharedAlb` cần: *AmazonECSInfrastructureRoleForGatewayHostSharedALB* — <https://docs.aws.amazon.com/aws-managed-policy/latest/reference/AmazonECSInfrastructureRoleForGatewayHostSharedALB.html>.

**💡 Giải thích thêm**

Điểm mấu chốt của mục này: **route cho biết "hỏng", deployment cho biết "vì sao"**.

```
list-gateway-routes --status *_FAILED   → route nào hỏng
describe-gateway-route                  → service nào, tài nguyên nào
service deployment / service events     → LÝ DO (ví dụ InvalidSubnet, AccessDenied...)
sửa nguyên nhân → redeploy service      (gateway không tự chạy lại)
```

`InsufficientAvailabilityZones` liên quan trực tiếp tới việc chọn subnet khi tạo gateway; vì subnet bất biến, cách xử lý sẽ là tạo gateway mới với subnet ở ít nhất hai AZ (suy ra từ trang *Create and manage*).

---

## 3. Vấn đề capacity và quota

**📘 Theo docs AWS**

Giá trị quota: xem *Gateway Service Limits* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/gateway-overview.html#gateway-service-quotas>.

### 3.1. Cluster đạt giới hạn số gateway liên kết

- Một cluster liên kết tối đa **5** gateway. Chạm giới hạn thì ECS báo `Cluster has reached the maximum number of gateway associations (5)`.
- Gỡ liên kết một gateway cluster không còn cần — việc này đòi hỏi **ngắt các service** đang dùng gateway đó trước.

### 3.2. Gateway đạt số ALB tối đa

- Khi các ALB hiện có không nhận thêm kết nối, ECS tạo thêm ALB cho tới `maxAlbCount` (mặc định **10**). Mỗi ALB phục vụ tối đa **25** service.
- Khi mọi ALB đầy và đã chạm `maxAlbCount`, deploy service mới **thất bại**.
- So `currentAlbCount` với `maxAlbCount` và xem tài nguyên được quản lý:

```bash
aws ecs describe-gateway --gateway <TÊN_GATEWAY>
aws ecs describe-gateway-resources --gateway <TÊN_GATEWAY>
```

- Nếu `maxAlbCount` là nút thắt, tăng nó trong giới hạn hỗ trợ. Docs ví dụ đặt `maxAlbCount` là 25 bằng `update-gateway`.
- Đợi cập nhật gateway hoàn tất rồi **redeploy** service bị ảnh hưởng. Tăng `maxAlbCount` **không tự chạy lại** deploy đã thất bại.

### 3.3. Chạm quota của tài nguyên AWS bên dưới

Tài nguyên do gateway tạo chịu quota của dịch vụ sở hữu (load balancer, target group, listener rule, certificate, rule của security group). Việc tạo cũng có thể lỗi nếu subnet không đủ IP trống. Các lý do có thể gặp:

| Mã lỗi | Ý nghĩa theo docs |
|---|---|
| `LoadBalancerQuotaExceeded`, `TargetGroupQuotaExceeded` | Giới hạn của account trong Region |
| `ResourceQuotaExceeded` | Giới hạn theo từng load balancer, như listener rule hoặc certificate |
| `CertificateQuotaExceeded` | Giới hạn certificate của account |
| `CertificateAnnualLimitExceeded` | Số certificate yêu cầu trong **365 ngày** gần nhất. Xoá certificate **không làm giảm** con số này → cần xin tăng quota qua **AWS Support** |
| `SecurityGroupCapacityExceeded`, `SecurityGroupRuleCapacityExceeded` | Số security group mỗi VPC, số rule mỗi security group |
| `TestRuleLimitExceeded` | Số test traffic rule trên một service |
| `TagLimitExceeded` | Tổng tag của gateway + tag của service vượt mức tài nguyên cho phép, hoặc bị tag policy của account từ chối. ECS cũng gắn tag `AmazonECSManaged` của riêng nó, chiếm **một slot** |

Dùng lý do lỗi để xác định tài nguyên bị giới hạn, kiểm tra service quota tương ứng; xin tăng quota, giải phóng capacity không dùng, hoặc dùng cấu hình thay thế được hỗ trợ, rồi chạy lại deploy. Giới hạn của ELB: *Quotas for your Application Load Balancers* — <https://docs.aws.amazon.com/elasticloadbalancing/latest/application/load-balancer-limits.html>.

**💡 Giải thích thêm**

- Về ví dụ tăng `maxAlbCount` ở 3.2: lệnh minh hoạ trong mục này của docs chỉ truyền `--configuration`. Trang *Create and manage* lại nói infrastructure role **bắt buộc** trong mọi `UpdateGateway`, và mọi thiết lập bị bỏ sót sẽ bị đặt lại. Vì vậy khi làm thật, hãy làm theo quy trình của trang *Create and manage*: `describe-gateway` trước, truyền `--infrastructure-role-arn` và giữ lại các thiết lập hiện có.
- `CertificateAnnualLimitExceeded` là lỗi đặc biệt: mỗi route có certificate riêng, nên việc ngắt/nối lại service nhiều lần cũng tiêu tốn hạn mức này, và xoá cũng không lấy lại được.
- `TagLimitExceeded`: nếu service và gateway đều nhiều tag, cộng thêm tag hệ thống của ECS có thể vượt giới hạn tag của tài nguyên.

---

## 4. Vấn đề IAM và quyền

**📘 Theo docs AWS**

Thao tác gateway liên quan **hai danh tính khác nhau**:
- **IAM principal gọi API** (người hoặc role thực hiện request);
- **infrastructure IAM role** mà ECS assume để quản lý tài nguyên.

Lỗi quyền ở danh tính này **không sửa được** bằng cách thay đổi danh tính kia.

### 4.1. Người gọi không pass được infrastructure role

- `CreateGateway` và `UpdateGateway` lỗi quyền liên quan `iam:PassRole` khi người gọi không pass được role ghi trong `infrastructureRoleArn`.
- Đây là lỗi **phổ biến nhất ở lần chạy đầu**, vì request vẫn lỗi dù người gọi có quyền tạo gateway.
- Xác nhận người gọi có `iam:PassRole` trên **đúng ARN** trong `infrastructureRoleArn`; kiểm tra permissions boundary, service control policy và các explicit deny.
- Thêm quyền vào infrastructure role **không** giải quyết việc người gọi thiếu quyền. Thiết lập IAM role: *Infrastructure IAM role for Amazon ECS Gateway* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/gateway-managing.html#gateway-infrastructure-role>.

### 4.2. ECS không assume được infrastructure role

- ECS tìm thấy role nhưng không assume được. Kiểm tra role tồn tại và trust policy cho phép `ecs.amazonaws.com` gọi `sts:AssumeRole`:

```bash
aws iam get-role --role-name <TÊN_ROLE>
```

- Trust policy phải cho phép principal `ecs.amazonaws.com` thực hiện `sts:AssumeRole` (docs đưa mẫu policy với một statement `Allow` như vậy).
- Role vừa tạo hoặc vừa sửa: chờ thay đổi IAM lan truyền rồi thử lại.
- Trust relationship sai hoặc thiếu **không** sửa được chỉ bằng cách cấp `iam:PassRole` cho người gọi.
- Xác nhận role **cùng account và partition** với gateway.
- Lỗi liên quan `AWS STS is not activated for this account in a Region` nghĩa là STS chưa được bật ở Region đó — bật lên rồi thử lại.

### 4.3. Infrastructure role không quản lý được một tài nguyên

- Route hoặc lần cập nhật gateway báo access denied cho thao tác Elastic Load Balancing, EC2 hoặc ACM khi infrastructure role **thiếu quyền**.
- Kiểm tra policy đang gắn:

```bash
aws iam list-attached-role-policies --role-name <TÊN_ROLE>
```

- Gắn managed policy `AmazonECSInfrastructureRoleForGatewayHostSharedALB`; nếu bạn thay bằng policy tự viết, đối chiếu policy đó với **IAM action** được nêu trong lỗi. (Quyền của managed policy: <https://docs.aws.amazon.com/aws-managed-policy/latest/reference/AmazonECSInfrastructureRoleForGatewayHostSharedALB.html>.)
- Kiểm tra thêm permissions boundary hoặc hạn chế khác của account.
- **Thao tác thất bại cụ thể và ARN tài nguyên** hữu ích hơn nhiều so với một thông điệp `AccessDenied` chung chung.

**💡 Giải thích thêm**

```
             (4.1) iam:PassRole
Người gọi ────────────────────────► ECS
                                     │ (4.2) sts:AssumeRole — trust policy cho ecs.amazonaws.com
                                     ▼
                           Infrastructure role
                                     │ (4.3) quyền ELB / EC2 / ACM — managed policy
                                     ▼
                       ALB, listener, TG, SG, certificate
```

| Triệu chứng | Sửa ở đâu |
|---|---|
| Lỗi `iam:PassRole` khi create/update gateway | Policy của **người gọi** |
| Không assume được role / STS chưa bật | **Trust policy** của role, STS của Region |
| Access denied cho ELB/EC2/ACM khi tạo route hoặc update | **Permission policy** của infrastructure role |

---

## 5. Kết nối tới service và sức khoẻ target

### 5.1. Hostname của service không phản hồi

**📘 Theo docs AWS**

Khi request tới hostname HTTPS do gateway sinh ra bị timeout hoặc lỗi, kiểm tra đường đi từ route tới ứng dụng:

1. **Kiểm tra trạng thái route** — tìm route của service và xác nhận `ACTIVE`. `CREATE_IN_PROGRESS` là chưa tạo xong; `*_FAILED` là lỗi tạo hạ tầng — xử lý lỗi route trước khi debug ứng dụng.

   ```bash
   aws ecs list-gateway-routes --cluster <TÊN_CLUSTER> --service <TÊN_SERVICE>
   ```

2. **Kiểm tra quyền truy cập gateway** — gateway internal cần kết nối mạng tới internal load balancer. Xác nhận bạn đang thử từ mạng **tới được** nó.
3. **Kiểm tra target health** — lấy ARN target group từ gateway connection (do `describe-service-revisions` trả về), rồi xem target:

   ```bash
   aws elbv2 describe-target-health --target-group-arn <ARN_TARGET_GROUP>
   ```

   - Target unhealthy khi ứng dụng trả mã trạng thái không mong đợi, timeout, lắng nghe ở port khác, hoặc load balancer không tới được.
   - Kết quả có trạng thái từng target và, khi cần, lý do và mô tả. Ví dụ `Target.ResponseCodeMismatch` nghĩa là phản hồi không nằm trong dải mã thành công đã cấu hình.
   - Kiểm tra health check path, phản hồi của ứng dụng và port của target **trước khi** sửa bất kỳ hạ tầng gateway nào. Mã lý do health check: *Health checks for Application Load Balancer target groups* — <https://docs.aws.amazon.com/elasticloadbalancing/latest/application/target-group-health-checks.html>.
   - Route `ACTIVE` chỉ có nghĩa tạo ingress thành công, **không** có nghĩa target khoẻ.
4. **Kiểm tra security group** — inbound SG của load balancer cho phép traffic từ client mong muốn; SG của task cho phép traffic từ load balancer trên container port. Nếu platform team tự cung cấp inbound SG cho gateway, ECS **không quản lý** inbound rule của nó.
5. **Kiểm tra ứng dụng và cơ chế kiểm soát traffic** — xác nhận port container đang lắng nghe và health endpoint. Nếu có AWS WAF web ACL, kiểm tra rule nào đang chặn request. Dùng access log của load balancer và metric CloudWatch để phân biệt lỗi của load balancer với phản hồi của ứng dụng.

**💡 Giải thích thêm**

Năm bước này là đi dọc đường request từ ngoài vào trong:

```
Client ─(2) mạng tới được LB? (internal cần đúng mạng)
   └─► ALB ─(4) inbound SG cho client?  ─(5) WAF có chặn?
         └─► route/listener rule ─(1) route ACTIVE?
               └─► target group ─(3) target healthy? lý do?
                     └─► task ─(4) SG task cho LB vào port? ─(5) app nghe đúng port, health endpoint đúng?
```

Lời khuyên của docs ở bước 3 đáng nhớ: **soát ứng dụng trước, đừng vội sửa hạ tầng gateway** — vì sửa tay tài nguyên được quản lý dẫn tới vấn đề ở mục 8.

---

## 6. Lỗi cập nhật gateway

**📘 Theo docs AWS**

- `DescribeGateway` báo `UPDATE_FAILED` khi ECS không áp được cập nhật cấu hình lên **mọi** load balancer.
- ECS cập nhật lần lượt từng ALB và cố cập nhật hết chứ không dừng ở lỗi đầu tiên → có ALB nhận thay đổi, có ALB không.
- **Đừng giả định** cập nhật đã được rollback trên toàn gateway. Lấy trạng thái và thông tin lỗi:

```bash
aws ecs describe-gateway --gateway <TÊN_GATEWAY>
```

- Xem `statusReason` và `failures` để biết tài nguyên và thao tác nào không cập nhật được. Ví dụ docs:
  - lỗi security group ID không hợp lệ → chỉ tới **inbound SG** đã cấu hình cho gateway;
  - lỗi access denied → chỉ tới **quyền của infrastructure role**.
- Cách đọc danh sách `failures`: *Locate failure details* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/gateway-monitoring.html#gateway-where-failures-recorded>.
- Sửa nguyên nhân, chạy lại cập nhật. Chỉ coi mọi ALB đã có cấu hình mới khi gateway báo cập nhật thành công; đợi cập nhật xong rồi mới thử lại.

> **Important (docs):** Cập nhật dở dang có thể để các ALB mang **cấu hình khác nhau**, làm thay đổi hành vi traffic hoặc policy của service phía sau. Coi như **mới áp dụng một phần** cho tới khi gateway báo `UPDATE_COMPLETE`, và kiểm tra lại tài nguyên bị ảnh hưởng sau khi khôi phục.

**💡 Giải thích thêm**

Ví dụ hậu quả thực tế suy từ docs: đổi WAF ACL mà chỉ 10/12 ALB nhận → service trên 2 ALB còn lại vẫn dùng WAF cũ (hoặc không có WAF). Vì 25 service dùng chung một ALB, một lần update lỗi dở có thể khiến các service **khác nhau** được bảo vệ khác nhau.

---

## 7. Vấn đề khi gỡ liên kết và xoá

### 7.1. Association bị kẹt ở `DISASSOCIATING`

**📘 Theo docs AWS**

- Association giữ `DISASSOCIATING` cho tới khi **mọi** gateway route của cluster đó bị xoá.
- Khi gỡ liên kết, ECS **không tự redeploy** service đang kết nối và không xoá route của chúng.
- Tìm service còn kết nối:

```bash
aws ecs list-gateway-routes --gateway <TÊN_GATEWAY> --cluster <TÊN_CLUSTER>
```

- Phối hợp với app team để cập nhật hoặc redeploy service, ngắt chúng khỏi gateway. Route cuối cùng bị xoá thì việc gỡ liên kết hoàn tất.

### 7.2. Không xoá được gateway

**📘 Theo docs AWS**

- Không xoá được gateway khi **deletion protection đang bật**, hoặc còn association ở `ACTIVE` hay `DISASSOCIATING`.
- Kiểm tra association, đảm bảo mọi route còn lại bị xoá để gỡ liên kết hoàn tất. Khi hết association, tắt deletion protection rồi xoá lại. Quy trình đầy đủ: *Delete a gateway* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/gateway-managing.html#gateway-delete>.

```bash
aws ecs update-gateway --gateway <TÊN_GATEWAY> --no-deletion-protection
aws ecs delete-gateway --gateway <TÊN_GATEWAY>
```

**💡 Giải thích thêm**

Chuỗi phụ thuộc khi xoá:

```
delete-gateway cần ─► deletion protection = off
                  └─► không còn association ─► association hết DISASSOCIATING
                                                  └─► không còn route ─► mọi service đã redeploy/xoá/gỡ connector
```

Nút thắt thực tế thường là **con người** (chờ app team redeploy), không phải kỹ thuật.

---

## 8. Thay đổi làm bên ngoài ECS

**📘 Theo docs AWS**

ECS **không tự sửa hay tạo lại** tài nguyên do gateway quản lý nếu chúng bị sửa hoặc xoá bên ngoài ECS. Sửa hoặc xoá load balancer, listener, listener rule, target group, certificate hay security group được quản lý có thể làm **gián đoạn traffic**, hoặc khiến các lần deploy service, cập nhật gateway hay dọn dẹp về sau thất bại.

Nếu tài nguyên được quản lý đã bị sửa hoặc xoá bên ngoài ECS:

1. **Xác định tài nguyên bị ảnh hưởng** — dùng `describe-gateway-resources` hoặc `describe-gateway-route` để liệt kê tài nguyên, rồi xem cấu hình hiện tại ở dịch vụ AWS tương ứng để tìm cái bị thiếu hoặc bị sửa. Thay đổi bên ngoài có thể hiện ra dưới dạng:
   - `ManagedResourceDeleted` — tài nguyên ECS tạo cho service đã bị xoá bên ngoài.
   - `ConflictingListener` — trên load balancer có một listener ở port của gateway mà ECS không tạo.
   - `SecurityGroupNameInUse` — đã tồn tại một security group trùng tên mà ECS muốn dùng, và nó không do ECS quản lý. ECS không cấu hình lại hay xoá nó.
   - `InvalidSecurityGroup` — inbound security group của gateway không còn tồn tại.
2. **Khôi phục service bị ảnh hưởng:**
   - Tài nguyên **bị sửa**: khôi phục cấu hình gốc rồi redeploy service.
   - Tài nguyên **bị xoá**: gỡ gateway connector khỏi service và deploy, sau đó thêm connector lại và deploy lần nữa. Lần deploy thứ hai tạo tài nguyên mới và route mới. Vì service nhận **hostname mới**, cần cập nhật client hoặc bản ghi DNS đang trỏ tới hostname cũ.
3. **Kiểm tra kết nối** — xác nhận route của service `ACTIVE` và target khoẻ trước khi chuyển traffic sang.

> **Important (docs):** **Không sửa hoặc xoá tag `AmazonECSManaged`** trên tài nguyên do gateway quản lý. ECS dùng tag này để nhận diện tài nguyên của nó; thiếu tag, ECS không cập nhật hay dọn dẹp được tài nguyên đó. Hãy thay đổi cấu hình qua các thao tác của ECS Gateway thay vì sửa trực tiếp tài nguyên bên dưới.

**💡 Giải thích thêm**

| Đã xảy ra | Cách phục hồi theo docs | Hệ quả |
|---|---|---|
| Ai đó **sửa** tài nguyên | Khôi phục cấu hình gốc → redeploy | Giữ hostname |
| Ai đó **xoá** tài nguyên | Gỡ connector → deploy → thêm lại → deploy | **Hostname mới** |

Liên hệ với các trang trước: gateway chỉ lưu "bản ghi" (ARN) chứ không đọc trạng thái thật (*Monitor a gateway*), nên thay đổi bên ngoài **không bị phát hiện** cho tới khi một thao tác về sau thất bại. Với người dùng Terraform hoặc script tự động, cần đảm bảo chúng **không quản lý** các tài nguyên có tag `AmazonECSManaged=true` (suy ra từ cảnh báo này, docs không nhắc tới Terraform).

---

## 9. Bảng tra cứu mã lỗi

Tổng hợp từ các mục trên (chỉ gồm thông điệp/mã docs nêu):

| Mã / thông điệp | Mục | Nguyên nhân tóm tắt |
|---|---|---|
| `gatewayConnectors were specified but no gateways are associated with cluster` | 1.1 | Cluster không có gateway dùng được |
| `has associated gateways but none are in the service's VPC` | 1.2 | Khác VPC |
| `none of the gateways associated with cluster match` | 1.2 | Bộ lọc access type loại hết |
| `InvalidConfiguration: "portName not found in task definition portMappings"` | 1.3 | `portName` không khớp tên port |
| Lỗi thay target group với `ROLLING` | 1.4 | Đổi health check khi dùng ROLLING |
| `InsufficientAvailabilityZones` | 2 | Subnet gateway ở < 2 AZ |
| `InvalidSubnet` | 2 | Subnet không tồn tại / khác VPC / hết IP |
| `Cluster has reached the maximum number of gateway associations (5)` | 3.1 | Cluster đủ 5 gateway |
| (deploy thất bại khi ALB đầy và chạm `maxAlbCount`) | 3.2 | Hết sức chứa gateway |
| `LoadBalancerQuotaExceeded`, `TargetGroupQuotaExceeded` | 3.3 | Quota account trong Region |
| `ResourceQuotaExceeded` | 3.3 | Quota theo từng ALB |
| `CertificateQuotaExceeded` | 3.3 | Quota certificate |
| `CertificateAnnualLimitExceeded` | 3.3 | Hạn mức certificate 365 ngày — cần AWS Support |
| `SecurityGroupCapacityExceeded`, `SecurityGroupRuleCapacityExceeded` | 3.3 | Quota SG / rule |
| `TestRuleLimitExceeded` | 3.3 | Quá số test traffic rule |
| `TagLimitExceeded` | 3.3 | Quá số tag hoặc tag policy chặn |
| Lỗi `iam:PassRole` | 4.1 | Người gọi thiếu PassRole |
| Không assume được role / `AWS STS is not activated for this account in a Region` | 4.2 | Trust policy / STS |
| `AccessDenied` cho ELB / EC2 / ACM | 4.3 | Infrastructure role thiếu quyền |
| `Target.ResponseCodeMismatch` | 5.1 | Ứng dụng trả mã ngoài dải thành công |
| Gateway `UPDATE_FAILED` | 6 | Cập nhật áp dụng một phần |
| `ManagedResourceDeleted` | 8 | Tài nguyên bị xoá bên ngoài |
| `ConflictingListener` | 8 | Listener lạ trên port của gateway |
| `SecurityGroupNameInUse` | 8 | SG trùng tên không do ECS quản lý |
| `InvalidSecurityGroup` | 8 | Inbound SG của gateway không còn |

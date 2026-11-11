# Create and manage an Amazon ECS Gateway

> Nguồn: **Amazon ECS Developer Guide — Create and manage an Amazon ECS Gateway**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/gateway-managing.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: diễn đạt lại bằng tiếng Việt, bám sát từng ý và đúng thứ tự của trang gốc, không thêm thông tin ngoài trang. Đây không phải bản dịch từng chữ; khi cần câu chữ hoặc ví dụ code chính xác, đọc link gốc ở trên.
> - Khối **💡 Giải thích thêm**: phần giải thích để dễ hiểu hơn, chỉ làm rõ những gì docs đã nói.
> - Các lệnh CLI trong file dùng placeholder (`<...>`) để bạn tự thay giá trị; tên lệnh và tên tham số giữ đúng như docs.

---

## Mục lục

0. [Giới thiệu](#0-giới-thiệu)
1. [Điều kiện tiên quyết (Prerequisites)](#1-điều-kiện-tiên-quyết-prerequisites)
2. [Tạo gateway](#2-tạo-gateway)
3. [Tuỳ chỉnh gateway (Customizing)](#3-tuỳ-chỉnh-gateway-customizing)
4. [Liên kết gateway với cluster](#4-liên-kết-gateway-với-cluster)
5. [Cập nhật gateway](#5-cập-nhật-gateway)
6. [Các thiết lập có thể thay đổi](#6-các-thiết-lập-có-thể-thay-đổi)
7. [Huỷ liên kết gateway khỏi cluster](#7-huỷ-liên-kết-gateway-khỏi-cluster)
8. [Xoá gateway](#8-xoá-gateway)
9. [Tóm tắt](#9-tóm-tắt)

---

## 0. Giới thiệu

**📘 Theo docs AWS**

- ECS Gateway là tài nguyên **cấp account**, chứa cấu hình ingress và các chuẩn bảo mật mà các service kết nối vào sẽ **thừa hưởng**.
- **Platform team** tạo gateway, cấu hình chính sách mạng và liên kết gateway với các ECS cluster đích.
- **Application team** sau đó cho service opt in; service sẽ kết nối tới các gateway **tương thích** trên cluster. (Xem *Connect Amazon ECS services to a gateway* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/gateway-connect-service.html>.)
- Tạo gateway **chưa tạo hạ tầng ngay**. ECS chỉ tạo ALB đầu tiên khi service đầu tiên kết nối vào một gateway đã được liên kết.

**💡 Giải thích thêm**

Trang này là phần việc của **platform team**: chuẩn bị IAM, tạo gateway, gắn cluster, cập nhật, gỡ và xoá. Phần kết nối service nằm ở trang khác.

---

## 1. Điều kiện tiên quyết (Prerequisites)

**📘 Theo docs AWS**

Trước khi tạo gateway, bạn cần:
- Một **infrastructure IAM role** mà ECS có thể assume để tạo và quản lý tài nguyên ingress.
- Một **default VPC** trong Region tạo gateway. Nếu không có default VPC, bạn có thể tự chỉ định subnet khi tạo gateway.

### 1.1. Infrastructure IAM role cho Gateway

**📘 Theo docs AWS**

ECS cần một infrastructure role để tạo, cấu hình và quản lý load balancer, listener, target group, security group và certificate ACM trong account của bạn.

Cách tạo:
- Tạo IAM role có **trust policy** cho phép service principal `ecs.amazonaws.com` assume role.
- Gắn AWS managed policy **`AmazonECSInfrastructureRoleForGatewayHostSharedALB`**. Quyền cụ thể của policy này xem trong *AWS Managed Policy Reference* — <https://docs.aws.amazon.com/aws-managed-policy/latest/reference/AmazonECSInfrastructureRoleForGatewayHostSharedALB.html>.

Lệnh tương ứng (docs dùng `aws iam create-role` rồi `aws iam attach-role-policy`):

```bash
# 1) Tạo role, trust policy cho phép ecs.amazonaws.com thực hiện sts:AssumeRole
aws iam create-role \
  --role-name <TÊN_ROLE> \
  --assume-role-policy-document file://trust-ecs.json

# 2) Gắn managed policy cho gateway
aws iam attach-role-policy \
  --role-name <TÊN_ROLE> \
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

**Quyền `iam:PassRole`:**
- IAM user/role nào **tạo hoặc cập nhật** gateway phải có quyền pass infrastructure role cho ECS.
- Docs khuyến nghị giới hạn `iam:PassRole` vào **đúng ARN của infrastructure role đó**, thay vì cấp rộng.

```json
{
  "Version": "2012-10-17",
  "Statement": [
    { "Effect": "Allow", "Action": "iam:PassRole", "Resource": "arn:aws:iam::<ACCOUNT_ID>:role/<TÊN_ROLE>" }
  ]
}
```

- **Application team không cần** `iam:PassRole`, không cần quyền thao tác gateway, cũng không cần quyền tạo load balancer để service có HTTPS endpoint — vì những quyền đó nằm trong infrastructure role.

> **Note (docs):** IAM role có tính *eventually consistent*, nên role vừa tạo có thể chưa dùng được ngay. Lần gọi `create-gateway` đầu tiên có thể lỗi kiểu "infrastructureRoleArn không assume được". Đợi khoảng **một phút** cho role lan truyền rồi chạy lại.

> **Important (docs):** ECS gắn tag `AmazonECSManaged=true` lên mọi tài nguyên nó tạo. Nếu bạn **sửa hoặc xoá tag này** trên ALB, security group hoặc listener rule được quản lý, ECS sẽ **không còn cập nhật hay dọn dẹp** được tài nguyên đó.

**💡 Giải thích thêm**

Phân quyền theo docs:

| Ai | Cần gì |
|---|---|
| ECS (service principal) | Assume infrastructure role → tạo ALB, listener, TG, SG, cert |
| Platform team | Quyền thao tác gateway + `iam:PassRole` cho đúng role đó |
| Application team | Không cần PassRole, không cần quyền gateway, không cần quyền tạo LB |

Đây là lý do app team "không cần chuyên môn networking": quyền tạo hạ tầng nằm ở role của ECS chứ không ở người deploy service.

---

## 2. Tạo gateway

**📘 Theo docs AWS**

Khi tạo gateway, **bắt buộc** chỉ định:
- **tên** duy nhất;
- **ARN của infrastructure role**;
- object cấu hình **`hostSharedAlb`**.

Gateway kiểu `hostSharedAlb` **dùng chung ALB** giữa các service và dùng **host-based routing** để chuyển traffic tới service. Mọi thiết lập bên trong đều **tuỳ chọn**, nên truyền `{"hostSharedAlb": {}}` sẽ tạo gateway với toàn giá trị mặc định.

`CreateGateway` là thao tác **đồng bộ**: trả về trạng thái `ACTIVE`, và ECS chưa tạo hạ tầng nào cho tới khi có service đầu tiên kết nối.

Ví dụ trong docs tạo gateway `internet-facing` với hai subnet chỉ định:

```bash
aws ecs create-gateway \
  --name <TÊN_GATEWAY> \
  --infrastructure-role-arn <ARN_INFRA_ROLE> \
  --configuration '{
    "hostSharedAlb": {
      "scheme": "internet-facing",
      "networkConfiguration": { "subnets": ["<SUBNET_1>", "<SUBNET_2>"] }
    }
  }'
```

Kết quả trả về gồm `gatewayArn`, `name` và `status` = `ACTIVE`.

Để kiểm tra và đọc lại cấu hình: `aws ecs describe-gateway --gateway <TÊN_GATEWAY>`. (Xem *View gateway status* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/gateway-monitoring.html#gateway-view-status>.)

**💡 Giải thích thêm**

- Tạo xong là `ACTIVE` ngay vì chưa có tài nguyên thật nào phải dựng. ALB chỉ xuất hiện khi service đầu tiên kết nối.
- Dạng tối giản `{"hostSharedAlb": {}}` tiện để thử, nhưng hãy xem mục 3 trước: một số giá trị mặc định (subnet của default VPC, scheme `internet-facing`) **không đổi được** về sau.

---

## 3. Tuỳ chỉnh gateway (Customizing)

**📘 Theo docs AWS**

- Thiết lập nào bạn không truyền, ECS sẽ chọn giá trị mặc định. Muốn tuỳ chỉnh thì đặt các trường tuỳ chọn trong configuration.
- Với thiết lập có tương đương bên Elastic Load Balancing, **tên trường và giá trị trùng với ELB API**.
- Listener của gateway dùng **HTTPS port 443**; không hỗ trợ giao thức hay port khác.

### 3.1. Thiết lập KHÔNG đổi được sau khi tạo

Muốn đổi các thiết lập này phải: tạo gateway mới → cập nhật service để kết nối sang → xoá gateway cũ.

| Thiết lập | Theo docs |
|---|---|
| **Name** | Duy nhất trong account, tối đa 255 ký tự. |
| **Subnets** | Subnet mà load balancer dùng. Mặc định là subnet của default VPC. Subnet quyết định gateway bao phủ **VPC nào** và **AZ nào**. Service chỉ kết nối tới gateway bao phủ VPC và các AZ nó đang chạy, nên lựa chọn này quyết định service nào dùng được gateway. Nếu service không chạy trong default VPC, phải đặt subnet khi tạo. |
| **IP address type** | `ipv4`, `dualstack` hoặc `dualstack-without-public-ipv4`. Mặc định `dualstack`. |
| **Scheme** | `internet-facing` hoặc `internal`. Mặc định `internet-facing`. ECS suy ra **access type** từ scheme: `internet-facing` → `PUBLIC`, `internal` → `PRIVATE`. Service chỉ kết nối tới gateway có access type mà nó chấp nhận. |

### 3.2. Thiết lập đổi được bất kỳ lúc nào

| Thiết lập | Theo docs |
|---|---|
| **Inbound security group** | Tự cung cấp SG nếu muốn tự kiểm soát traffic vào load balancer; nếu không, ECS tạo SG và quản lý inbound rule. |
| **Maximum load balancer count** | Số ALB tối đa gateway được quản lý. Mặc định **10**. |
| **AWS WAF web ACL** | ARN của web ACL gắn vào mọi load balancer của gateway. Không có mặc định. |
| **HTTP desync mitigation mode** | `monitor`, `defensive` hoặc `strictest`. Mặc định `defensive`. |
| **Access logs** | Mặc định tắt. Bật bằng cách cung cấp S3 bucket bạn sở hữu. |
| **Test traffic rules** | Header dùng để đưa test traffic tới revision mới của service. Không có mặc định. Thay đổi chỉ áp dụng cho từng service ở **lần deploy kế tiếp** của service đó. |
| **Deletion protection** | Mặc định **bật**, và gateway lan truyền nó xuống các load balancer nó quản lý. Không xoá được gateway cho tới khi tắt. |
| **Tag propagation** | Gateway áp tag của nó lên hạ tầng nó quản lý. Muốn tag chỉ nằm trên gateway thì đặt `NONE`. |

**💡 Giải thích thêm**

- Quyết định quan trọng nhất lúc tạo là **subnet** và **scheme**: subnet quyết định gateway phục vụ được service nào (VPC + AZ), còn scheme quyết định public hay private. Cả hai đều cố định.
- Nếu cần cả public lẫn internal, theo logic của docs, bạn sẽ có hai gateway với scheme khác nhau, và service dùng bộ lọc access type để chọn loại.

---

## 4. Liên kết gateway với cluster

**📘 Theo docs AWS**

- Service trong cluster chỉ dùng được gateway **sau khi** gateway được liên kết với cluster đó.
- Gateway và cluster phải **cùng Region và cùng account**.
- Bản thân việc liên kết **không định tuyến traffic nào** và **không thay đổi** cách service hiện có nhận traffic. Service trong cluster opt in riêng và bắt đầu dùng gateway ở **lần deploy kế tiếp**.

```bash
aws ecs associate-gateway-with-cluster --gateway <TÊN_GATEWAY> --cluster <TÊN_CLUSTER>
```

- Quan hệ **nhiều–nhiều**: một gateway gắn tối đa **10.000 cluster**, một cluster gắn tối đa **5 gateway**.
- Association chỉ được quản lý qua `AssociateGatewayWithCluster` và `DisassociateGatewayFromCluster`.

**Cách service được ghép với gateway:**
- Khi service có gateway connector được deploy, ECS xét các gateway đã liên kết với cluster của nó.
- Service kết nối tới gateway tương thích dựa trên **access type, VPC và AZ**.
- Hãy lên kế hoạch **độ bao phủ subnet** cẩn thận: gateway chỉ phục vụ service trong VPC và các AZ mà subnet của nó bao phủ. Bao phủ hẹp thì các service còn lại trong cluster không dùng được. (Xem *How Amazon ECS selects compatible gateways* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/gateway-connect-service.html#gateway-select-compatible>.)

> **Note (docs):** Liên kết gateway với cluster không ảnh hưởng service hiện có cho tới lần deploy kế tiếp. Thêm một gateway nữa vào cluster cũng **không tự kết nối** các service đang chạy.

**Liệt kê association:**

```bash
aws ecs list-gateway-cluster-associations --gateway <TÊN_GATEWAY>
```

Kết quả có ARN gateway, ARN cluster và trạng thái association:
- `ACTIVE` — gateway đang liên kết với cluster.
- `DISASSOCIATING` — đang gỡ liên kết; các gateway route hiện có vẫn phục vụ traffic cho tới khi bị xoá.

Đây là trạng thái của **association**, khác với trạng thái của **gateway**. Có thể lọc theo gateway, cluster hoặc trạng thái (ví dụ dùng `--cluster` để xem cluster đang có những gateway nào, hoặc lọc trạng thái để tìm association còn `DISASSOCIATING` trước khi xoá gateway). Kết quả phân trang, mặc định 100 mục/trang.

**💡 Giải thích thêm**

Điểm hay bị hiểu nhầm: **associate xong không có gì xảy ra ngay**. Service phải có connector và phải được **deploy lại** thì mới được ghép vào gateway. Điều này giúp thêm gateway vào cluster mà không gây gián đoạn service đang chạy.

---

## 5. Cập nhật gateway

**📘 Theo docs AWS**

Cập nhật gateway thay đổi các thiết lập mà **mọi service đã kết nối** thừa hưởng, kể cả service và hạ tầng đang nằm sau gateway. Một số thiết lập không đổi được (xem mục 3).

> **Important (docs):** `UpdateGateway` **thay thế toàn bộ** cấu hình bằng nội dung trong request. Bỏ sót một thiết lập nghĩa là **xoá nó** chứ không phải giữ nguyên. Trước khi cập nhật, chạy `describe-gateway` để lấy cấu hình hiện tại và đưa **mọi thiết lập muốn giữ** vào request, không chỉ thiết lập đang thay đổi.

### Bước 1 — Lấy cấu hình hiện tại

```bash
aws ecs describe-gateway --gateway <TÊN_GATEWAY>
```

Kết quả (ví dụ trong docs) gồm các trường như `status`, `accessType`, `deletionProtection`, `infrastructureRoleArn`, `propagateTags`, và trong `configuration.hostSharedAlb`: `scheme`, `protocol`, `port`, `maxAlbCount`, `currentAlbCount`, `httpDesync`, `networkConfiguration` (`subnets`, `ipAddressType`).

### Bước 2 — Chọn thiết lập đưa vào request

Truyền giá trị mới cho thiết lập cần đổi, và **truyền lại giá trị hiện tại** cho mọi thiết lập khác.

| Thiết lập | Đặt ở đâu | Nếu bỏ sót |
|---|---|---|
| `infrastructureRoleArn` | Top level | Request lỗi (bắt buộc) |
| `configuration.hostSharedAlb` | Top level | Request lỗi (bắt buộc) |
| `deletionProtection` | Top level | Bị đặt lại `true` |
| `propagateTags` | Top level | Bị đặt lại `GATEWAY` |
| `networkConfiguration.inboundSecurityGroup` | `hostSharedAlb` | Bị gỡ; ECS quay lại tự quản lý inbound rule |
| `webACLArn` | `hostSharedAlb` | Bị gỡ; web ACL bị bỏ liên kết |
| `httpDesync` | `hostSharedAlb` | Bị đặt lại `defensive` |
| `accessLogs` | `hostSharedAlb` | Bị gỡ; tắt access log |
| `testTrafficRules` | `hostSharedAlb` | Bị gỡ |
| `maxAlbCount` | `hostSharedAlb` | Bị đặt lại `10` |

Các trường `name`, `scheme`, `protocol`, `port`, `subnets`, `ipAddressType` đã cố định từ lúc tạo; `currentAlbCount` là giá trị ECS báo cáo, không phải thiết lập. **Không đưa** các trường này vào request.

> **Note (docs):** Infrastructure role **bắt buộc** trong mọi request `UpdateGateway`; thiếu sẽ báo lỗi validation. Bỏ sót deletion protection hay tag propagation thì chúng bị đưa về mặc định.

### Bước 3 — Thực hiện cập nhật

Ví dụ trong docs: đổi `maxAlbCount` từ 10 lên 25, đồng thời **giữ lại** `httpDesync` (đang là `monitor`) — nếu bỏ sót thì nó sẽ về `defensive`.

```bash
aws ecs update-gateway \
  --gateway <TÊN_GATEWAY> \
  --infrastructure-role-arn <ARN_INFRA_ROLE> \
  --configuration '{ "hostSharedAlb": { "maxAlbCount": 25, "httpDesync": "monitor" } }'
```

**Về thiết lập bất biến:** chúng mô tả hạ tầng mà ECS không dựng lại được nếu không làm gián đoạn traffic. Muốn đổi: tạo gateway mới với cấu hình mong muốn → liên kết với cluster → chuyển service sang → gỡ liên kết và xoá gateway cũ.

**Tiến trình cập nhật:**
- Với thiết lập thay đổi được, ECS áp dụng **bất đồng bộ** lên mọi ALB do gateway quản lý.
- Gateway chuyển sang `UPDATE_IN_PROGRESS`; ECS cập nhật **cuốn chiếu (rolling)** từng ALB đang hoạt động; xong thì thành `UPDATE_COMPLETE`.
- Nếu không áp dụng được cho một hoặc nhiều ALB (ví dụ ARN WAF sai, thiếu quyền IAM), trạng thái thành `UPDATE_FAILED`. Dùng `describe-gateway` để xem lỗi.

Theo dõi tiến trình:

```bash
aws ecs describe-gateway --gateway <TÊN_GATEWAY> --query 'gateway.{name:name,status:status}'
```

> **Note (docs):** Trong lúc cập nhật, các ALB mang **lẫn cấu hình cũ và mới**, vì ECS mới áp dụng cho một số ALB. Lặp lại lệnh cho tới khi trạng thái đổi.

**Cập nhật thành công một phần:**
- Có thể thành công ở ALB này và thất bại ở ALB khác; ECS cố cập nhật mọi tài nguyên chứ không dừng ở lỗi đầu tiên.
- Khi `UPDATE_FAILED`, lý do nằm ở `statusReason`; trường `failures` liệt kê từng tài nguyên không cập nhật được, kèm thao tác, mã lỗi và thông điệp.
- Gateway ghi nhận lỗi tối đa cho **200 tài nguyên**, mỗi tài nguyên tối đa **5 lỗi**.
- Xử lý nguyên nhân (thiếu quyền IAM, tài nguyên bị sửa bên ngoài…) rồi chạy lại `update-gateway`.

Mô tả đầy đủ của gateway, gồm cấu hình và số ALB hiện có: xem *View gateway status*.

> **Important (docs):** Chưa thấy `UPDATE_COMPLETE` thì **đừng giả định** mọi tài nguyên đã mang cấu hình mới.

**💡 Giải thích thêm**

`UpdateGateway` hoạt động kiểu **PUT (ghi đè toàn bộ)** chứ không phải PATCH. Quy trình an toàn:

```
describe-gateway ──► lấy toàn bộ thiết lập hiện tại
       │
       ▼
sửa thiết lập cần đổi, giữ nguyên các thiết lập khác
bỏ các trường bất biến / trường báo cáo (name, scheme, port, subnets, currentAlbCount...)
luôn kèm infrastructure-role-arn
       │
       ▼
update-gateway ──► UPDATE_IN_PROGRESS ──► UPDATE_COMPLETE  (hoặc UPDATE_FAILED → xem failures)
```

Lỗi dễ mắc nhất theo bảng ở bước 2: quên `deletionProtection` → bị bật lại; quên `webACLArn` → **mất WAF**; quên `inboundSecurityGroup` → quay về SG do ECS quản lý (mặc định mở 443 cho mọi IP, xem mục 6.1).

---

## 6. Các thiết lập có thể thay đổi

**📘 Theo docs AWS**

Mỗi mục dưới đây chỉ cho thấy phần cấu hình của riêng nó. Khi dùng, hãy **ghép vào cấu hình lấy ở bước 1** và kèm infrastructure role trong request.

### 6.1. Kiểm soát traffic vào (inbound)

- Mặc định ECS tạo SG cho các ALB của gateway, **cho phép HTTPS port 443 từ mọi IP**.
- Muốn tự kiểm soát: cung cấp SG của bạn. ECS sẽ gắn SG đó vào ALB, **xoá các inbound rule nó từng quản lý** và **ngừng quản lý** inbound rule.

> **Important (docs):** Trước khi chỉ định SG, đảm bảo nó cho phép traffic mà service cần; nếu không, service đang kết nối có thể **không truy cập được**.

```json
"networkConfiguration": { "inboundSecurityGroup": "<SG_ID>" }
```

- ALB là tài nguyên dùng chung, nên đổi rule của inbound SG có thể ảnh hưởng **mọi service** trên các ALB đó.
- Inbound SG là thiết lập mạng **duy nhất** đổi được sau khi tạo; subnet và IP address type thì không.

### 6.2. Gắn AWS WAF web ACL

- ECS áp web ACL lên **mọi ALB** của gateway → bảo vệ mọi service phía sau mà từng team không phải tự cấu hình.
- Web ACL phải là **regional web ACL** ở **cùng Region** với gateway.

```json
"webACLArn": "<ARN_REGIONAL_WEB_ACL>"
```

### 6.3. Chế độ chống HTTP desync

- Mặc định `defensive`: chống phần lớn tấn công request smuggling nhưng vẫn cho qua request không tuân thủ HTTP nghiêm ngặt.
- `strictest`: từ chối mọi request không tuân thủ.
- `monitor`: cho qua tất cả, nhưng ghi lại những request lẽ ra sẽ bị chặn.
- Nếu định dùng `strictest`, hãy bắt đầu bằng `monitor` để xem trước sẽ bị chặn những gì; bật access log để xem kết quả.

```json
"httpDesync": "strictest"
```

### 6.4. Bật access log

- Access log tắt cho tới khi bạn cung cấp S3 bucket. ECS **không tạo bucket** hộ bạn, và **không có công tắc riêng**: cung cấp bucket chính là bật log.

```json
"accessLogs": { "destination": { "s3": { "bucket": "<TÊN_BUCKET>", "prefix": "<PREFIX>" } } }
```

- Bucket phải cùng Region với gateway, và **bucket policy** phải cho phép **Elastic Load Balancing** ghi vào.
- Infrastructure role **không tham gia** việc ghi log, nên cấp quyền S3 cho role đó **không có tác dụng**.
- Vì ALB dùng chung, một bucket nhận log của **mọi service** trên gateway. Dùng **hostname** trong từng bản ghi log để biết request đi tới service nào.

### 6.5. Cấu hình test traffic rules

- Trong progressive deployment, revision mới cần nhận traffic **trước** khi traffic production chuyển sang. Test traffic rule xác định request nào đi tới revision mới.
- Rule cấu hình trên **gateway**, không phải trên từng service → mọi service phía sau dùng **chung một rule**.
- ECS khớp request **theo header**; bạn phải cung cấp cả tên header và ít nhất một giá trị.

```json
"testTrafficRules": { "header": { "name": "<TÊN_HEADER>", "values": ["<GIÁ_TRỊ>"] } }
```

- Ví dụ docs: request mang header `x-test-traffic: canary` sẽ tới revision mới trong lúc deploy; request khác tới revision production.
- **Chỉ có cơ chế khớp header**; không chia được theo **phần trăm** traffic.
- Ngoài lúc deploy không có revision mới, nên request mang header test vẫn tới production như bình thường.

> **Important (docs):** Thay đổi test traffic rule **không áp dụng** cho service đang chạy tại thời điểm thay đổi; nó áp dụng cho từng service ở **lần deploy kế tiếp** của service đó.

- ECS **không sửa lại** điều kiện khớp của rule có sẵn. Đổi header sẽ tạo **rule mới** cho revision mới. Một deployment thất bại chỉ xoá rule do chính nó tạo, giữ nguyên rule đang phục vụ revision hiện tại.
- Vì vậy một gateway có thể có service khớp header cũ và service khớp header mới cùng lúc, cho tới khi từng service redeploy. App team phải redeploy để nhận header mới; ECS không tự redeploy, nên cần **phối hợp với chủ service** khi đổi header.

### 6.6. Đặt số ALB tối đa

- `maxAlbCount` giới hạn số ALB gateway được tạo. Mặc định **10**.

```json
"maxAlbCount": 50
```

Hai giới hạn quyết định số service một gateway phục vụ được:
- **Service mỗi ALB**: tối đa **25**, là thuộc tính cố định của ALB khi dùng host-based routing, không đổi được.
- **ALB mỗi gateway**: chính là `maxAlbCount` bạn đặt (mặc định 10).

ECS lấp một ALB đến 25 service rồi mới tạo ALB tiếp theo, tối đa tới `maxAlbCount`. Khi service cuối cùng rời ALB, ECS xoá ALB đó. Gộp lại, một gateway chứa tối đa **25 × `maxAlbCount`** service — tức **250** với mặc định.

- **Nâng giới hạn trước khi cần.** Nếu service opt in lúc mọi ALB đã đầy và gateway đã chạm tối đa, ECS không tạo được ingress cho service đó → **deploy của service thất bại**. Nâng giới hạn sau đó **không tự chạy lại** deploy đã lỗi; chủ service phải deploy lại.
- Xem số ALB hiện có so với tối đa: `describe-gateway` rồi so `currentAlbCount` với `maxAlbCount`. Xem danh sách ALB và route trên từng ALB: *Inspect managed infrastructure (platform view)* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/gateway-monitoring.html#gateway-inspect-infrastructure>.

**💡 Giải thích thêm**

| Thiết lập | Ảnh hưởng tới | Khi nào có hiệu lực |
|---|---|---|
| Inbound SG | Mọi service trên các ALB | Khi update chạy |
| WAF | Mọi service | Khi update chạy |
| HTTP desync | Mọi service | Khi update chạy |
| Access logs | Mọi service (chung một bucket) | Khi update chạy |
| Test traffic rules | Mọi service | **Lần deploy kế tiếp của từng service** |
| `maxAlbCount` | Sức chứa của gateway | Khi update chạy; deploy đã lỗi phải chạy lại |

Hai điểm vận hành rút ra: theo dõi `currentAlbCount`/`maxAlbCount` để không bị lỗi deploy vì hết chỗ; và đổi header test là việc cần thông báo cho mọi app team.

---

## 7. Huỷ liên kết gateway khỏi cluster

**📘 Theo docs AWS**

- Gỡ liên kết khi không muốn service trong cluster đó kết nối tới gateway nữa.
- Gỡ liên kết **chặn kết nối mới** qua association đó, nhưng **không xoá ngay** các kết nối hiện có.

> **Important (docs):** Trước khi gỡ, kiểm tra service bị ảnh hưởng có **đường ingress thay thế** nếu vẫn cần truy cập được. Xoá gateway route có thể làm service không truy cập được qua gateway đó.

```bash
aws ecs disassociate-gateway-from-cluster --gateway <TÊN_GATEWAY> --cluster <TÊN_CLUSTER>
```

Association chuyển sang `DISASSOCIATING`. Trong trạng thái này:
- Service **mới** không kết nối được tới gateway qua cluster này.
- Service **hiện có** vẫn nhận traffic qua gateway route của chúng.
- Service ngắt kết nối khỏi gateway khi được **update hoặc redeploy**.
- Association giữ `DISASSOCIATING` cho tới khi **mọi gateway route** bị xoá.

ECS **không tự redeploy** service khi gateway bị gỡ khỏi cluster. App team phải update/redeploy để ngắt kết nối. Nếu cluster không còn gateway nào khác, hãy **gỡ gateway connector** khỏi service trước khi redeploy. Khi route cuối cùng bị xoá, ECS xoá association.

### 7.1. Xem các gateway route đang chặn việc gỡ liên kết

```bash
aws ecs list-gateway-routes --gateway <TÊN_GATEWAY> --cluster <TÊN_CLUSTER>
```

- Kết quả cho biết route nào vẫn còn kết nối; nếu còn, update/redeploy các service tương ứng.
- Ngoài gateway và cluster, có thể lọc theo **service**, **tên container port** và **trạng thái**. Lọc theo trạng thái giúp tìm route **bị kẹt** thay vì đang được dùng.
- Từ ARN của route, gọi `DescribeGatewayRoute` để xem hostname và hạ tầng riêng của kết nối đó. (Xem *Monitor a gateway* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/gateway-monitoring.html>.)

> **Note (docs):** Không được lọc chỉ bằng **tên service**, vì tên service chỉ duy nhất trong một cluster. Hãy truyền **ARN đầy đủ của service**, hoặc tên service **kèm** cluster.

### 7.2. Huỷ việc gỡ liên kết

- Khi association đang `DISASSOCIATING`, bạn có thể kích hoạt lại bằng cách **associate lại** gateway với chính cluster đó; association trở về `ACTIVE`.

```bash
aws ecs associate-gateway-with-cluster --gateway <TÊN_GATEWAY> --cluster <TÊN_CLUSTER>
```

Service giữ được gì phụ thuộc vào việc nó có redeploy trong lúc `DISASSOCIATING` hay không:
- **Chưa redeploy**: giữ nguyên gateway route và DNS hostname, tiếp tục phục vụ traffic không gián đoạn.
- **Đã redeploy**: đã mất gateway route; cần deploy thêm lần nữa để có route mới, và route đó mang **DNS hostname mới**.

Khi gỡ liên kết đã hoàn tất, association **mất vĩnh viễn**, không kích hoạt lại được. Muốn dùng lại cùng gateway với cluster đó thì tạo association mới; service từng kết nối sẽ nhận route mới, với **hostname mới**, ở lần deploy kế tiếp.

**💡 Giải thích thêm**

Vòng đời association:

```
            associate
   (không có) ───────► ACTIVE
                          │ disassociate
                          ▼
                   DISASSOCIATING ── associate lại ──► ACTIVE
                          │                    (service chưa redeploy giữ hostname cũ)
                          │ route cuối cùng bị xoá (do service redeploy/xoá)
                          ▼
                     (bị xoá vĩnh viễn) ── associate mới ──► ACTIVE (hostname mới)
```

Điều cần nhớ: **hostname có thể đổi**. Nếu client hoặc DNS của bạn trỏ thẳng vào hostname `*.ecs.<region>.on.aws`, việc service redeploy trong lúc gỡ liên kết (hoặc tạo lại association) sẽ đổi hostname đó.

---

## 8. Xoá gateway

**📘 Theo docs AWS**

Điều kiện để xoá:
- **Deletion protection đã tắt.**
- Gateway **không còn association** nào ở trạng thái `ACTIVE` hoặc `DISASSOCIATING`.

**Bước 1 — Tắt deletion protection** (mặc định đang bật):

```bash
aws ecs update-gateway --gateway <TÊN_GATEWAY> --no-deletion-protection
```

**Bước 2 — Gỡ liên kết gateway khỏi từng cluster:**

```bash
aws ecs disassociate-gateway-from-cluster --gateway <TÊN_GATEWAY> --cluster <TÊN_CLUSTER>
```

- Mỗi association giữ `DISASSOCIATING` cho tới khi mọi gateway route của gateway đó trong cluster bị xoá.
- ECS chỉ xoá route khi service sở hữu nó được **redeploy hoặc bị xoá**, và ECS không tự redeploy → cần phối hợp với chủ service.
- Tìm route đang giữ association: `aws ecs list-gateway-routes --gateway <TÊN_GATEWAY> --cluster <TÊN_CLUSTER>`.

**Bước 3 — Xác nhận không còn association:**

```bash
aws ecs list-gateway-cluster-associations --gateway <TÊN_GATEWAY>
```

ECS xoá association sau khi route cuối cùng biến mất. Danh sách rỗng thì xoá được gateway.

**Bước 4 — Xoá gateway:**

```bash
aws ecs delete-gateway --gateway <TÊN_GATEWAY>
```

- `DeleteGateway` là thao tác **đồng bộ**. Thành công thì trả về thông tin gateway với trạng thái `DELETED`; các request sau tới gateway đó trả về `GatewayNotFoundException`. Không đủ điều kiện thì thao tác thất bại.
- **Không còn gì phải dọn**: ECS xoá route và tài nguyên của service khi service ngắt kết nối, và xoá ALB khi service cuối cùng rời đi.

**💡 Giải thích thêm**

Lưu ý ở bước 1: docs dùng `update-gateway` chỉ với `--no-deletion-protection`. Theo nguyên tắc ở mục 5 (`UpdateGateway` thay thế toàn bộ cấu hình), lệnh này cũng sẽ đưa các thiết lập không truyền về mặc định. Điều này không thành vấn đề vì gateway sắp bị xoá, nhưng **đừng dùng cách viết tắt này cho gateway bạn còn giữ lại**.

Thứ tự xoá tóm gọn:

```
Tắt deletion protection
   → disassociate từng cluster
   → app team redeploy/xoá service (hoặc gỡ connector) để xoá route
   → list-gateway-cluster-associations rỗng
   → delete-gateway  (DELETED)
```

---

## 9. Tóm tắt

- **Chuẩn bị**: infrastructure role (trust `ecs.amazonaws.com` + `AmazonECSInfrastructureRoleForGatewayHostSharedALB`), `iam:PassRole` giới hạn đúng role cho platform team; app team không cần các quyền này. Đợi khoảng 1 phút sau khi tạo role.
- **Tạo**: cần tên, ARN infrastructure role, `hostSharedAlb`. Thao tác đồng bộ, trả `ACTIVE`, chưa tạo hạ tầng.
- **Bất biến**: name, subnets, IP address type, scheme (→ access type `PUBLIC`/`PRIVATE`); listener luôn HTTPS 443.
- **Đổi được**: inbound SG, `maxAlbCount` (mặc định 10), WAF, HTTP desync (mặc định `defensive`), access logs (S3), test traffic rules (header), deletion protection (mặc định bật), tag propagation.
- **Associate**: cùng Region + account; không tác động gì cho tới lần deploy kế tiếp của service; 5 gateway/cluster, 10.000 cluster/gateway.
- **Update**: ghi đè toàn bộ cấu hình → luôn `describe-gateway` trước; bất đồng bộ, rolling qua các ALB; theo dõi `UPDATE_IN_PROGRESS` → `UPDATE_COMPLETE` / `UPDATE_FAILED`.
- **Sức chứa**: 25 service/ALB × `maxAlbCount` (mặc định 250 service); nâng giới hạn trước khi cần.
- **Disassociate**: `DISASSOCIATING` cho tới khi service redeploy hết; có thể huỷ bằng associate lại; hostname có thể đổi.
- **Delete**: tắt deletion protection → gỡ mọi association → `delete-gateway` (đồng bộ, `DELETED`).
- **Không sửa/xoá tag `AmazonECSManaged=true`** trên tài nguyên do gateway quản lý.

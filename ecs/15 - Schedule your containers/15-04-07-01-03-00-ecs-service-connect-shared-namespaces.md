# Amazon ECS Service Connect with shared AWS Cloud Map namespaces

> Nguồn: **Amazon ECS Developer Guide — Amazon ECS Service Connect with shared AWS Cloud Map namespaces**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-connect-shared-namespaces.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: **bản dịch** sang tiếng Việt, đi đúng thứ tự và đúng nội dung của trang gốc, không thêm, không bớt ý. Lệnh ví dụ giữ nguyên như trên docs.
> - Khối **💡 Giải thích thêm**: chỉ giải thích lại những gì **chính trang này** đã nói, cho dễ hiểu hơn. Không đưa thông tin từ trang khác hay ví dụ tự đặt.
> - Tên dịch vụ, managed permission, API, lệnh giữ nguyên tiếng Anh.
> - Tài liệu AWS được phát hành theo giấy phép **Creative Commons Attribution-ShareAlike 4.0**; bản dịch này ghi rõ nguồn ở trên.

---

## Mục lục

0. [Phần mở đầu](#0-phần-mở-đầu)
1. [Những điều cần cân nhắc](#1-những-điều-cần-cân-nhắc)
2. [Tổng hợp](#2-tổng-hợp)

---

## 0. Phần mở đầu

**📘 Theo docs AWS**

Amazon ECS Service Connect hỗ trợ dùng **AWS Cloud Map namespace chia sẻ** giữa **nhiều AWS account** trong **cùng AWS Region**. Khả năng này cho phép bạn tạo ứng dụng phân tán, trong đó các service chạy ở các AWS account khác nhau có thể tìm thấy và giao tiếp với nhau thông qua Service Connect. Namespace chia sẻ được quản lý bằng **AWS Resource Access Manager (AWS RAM)**, cho phép chia sẻ tài nguyên giữa các account một cách an toàn. Thông tin thêm về namespace chia sẻ, xem *Cross-account AWS Cloud Map namespace sharing* (<https://docs.aws.amazon.com/cloud-map/latest/dg/sharing-namespaces.html>) trong *AWS Cloud Map Developer Guide*.

> **Important:** Bạn **phải** dùng managed permission **`AWSRAMPermissionCloudMapECSFullPermission`** để chia sẻ namespace thì Service Connect mới hoạt động đúng với namespace đó.

Khi dùng AWS Cloud Map namespace chia sẻ với Service Connect, service từ nhiều AWS account có thể tham gia **cùng một namespace**. Điều này đặc biệt hữu ích cho tổ chức có nhiều AWS account cần duy trì giao tiếp service-to-service **vượt ranh giới account** mà vẫn giữ **bảo mật** và **cô lập**.

> **Note:** Để giao tiếp với service ở **VPC khác**, bạn cần cấu hình **kết nối giữa các VPC**. Việc này có thể làm bằng một **VPC Peering connection**. Thông tin thêm, xem *Create or delete a VPC Peering connection* (<https://docs.aws.amazon.com/vpc/latest/peering/create-vpc-peering-connection.html>) trong *Amazon Virtual Private Cloud VPC Peering guide*.

**💡 Giải thích thêm**

```
Account A (chủ namespace)              Account B (bên dùng namespace)
  Cloud Map namespace ── chia sẻ qua AWS RAM ──►
       (managed permission: AWSRAMPermissionCloudMapECSFullPermission)
  service X ◄────────── Service Connect ──────────► service Y
                (khác VPC → cần VPC Peering)
```

| Yêu cầu | Theo trang |
|---|---|
| Phạm vi | Nhiều account, **cùng Region** |
| Cơ chế chia sẻ | AWS RAM |
| Managed permission bắt buộc | `AWSRAMPermissionCloudMapECSFullPermission` |
| Khác VPC | Cấu hình kết nối giữa VPC, vd VPC Peering |

---

## 1. Những điều cần cân nhắc

**📘 Theo docs AWS**

Cân nhắc các điểm sau khi dùng AWS Cloud Map namespace chia sẻ với Service Connect:

- **AWS RAM** phải có sẵn ở AWS Region nơi bạn muốn dùng namespace chia sẻ.

- Namespace chia sẻ phải ở **cùng AWS Region** với Amazon ECS service và cluster.

- Bạn phải dùng **ARN** của namespace, **không phải ID**, khi cấu hình Service Connect với namespace chia sẻ.

- **Mọi loại namespace** đều được hỗ trợ: HTTP, Private DNS và Public DNS namespace.

- Nếu quyền truy cập vào namespace chia sẻ bị **thu hồi**, các thao tác Amazon ECS cần tương tác với namespace (như `CreateService`, `UpdateService` và `ListServicesByNamespace`) sẽ **thất bại**. Thông tin thêm về xử lý sự cố quyền với namespace chia sẻ, xem *Troubleshooting Amazon ECS Service Connect with shared AWS Cloud Map namespaces* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-connect-shared-namespaces-troubleshooting.html>).

- Với service discovery bằng **truy vấn DNS** trong **private DNS namespace chia sẻ**:

  - **Chủ namespace** cần gọi **`create-vpc-association-authorization`** với ID của private hosted zone gắn với namespace, và VPC của bên dùng.

    ```
    aws route53 create-vpc-association-authorization --hosted-zone-id Z1234567890ABC --vpc VPCRegion=us-east-1,VPCId=vpc-12345678
    ```

  - **Bên dùng namespace** cần gọi **`associate-vpc-with-hosted-zone`** với ID của private hosted zone.

    ```
    aws route53 associate-vpc-with-hosted-zone --hosted-zone-id Z1234567890ABC --vpc VPCRegion=us-east-1,VPCId=vpc-12345678
    ```

- **Chỉ chủ namespace** mới quản lý được resource share.

- Bên dùng namespace có thể **tạo và quản lý service** trong namespace chia sẻ nhưng **không thể sửa** chính namespace.

- Discovery name phải **duy nhất** trong namespace chia sẻ, **bất kể** account nào tạo service.

- Service trong namespace chia sẻ có thể tìm thấy và kết nối tới service từ các AWS account khác có quyền truy cập namespace.

- Khi bật **TLS** cho Service Connect và dùng namespace chia sẻ, **AWS Private CA** Certificate Authority (CA) được **gắn phạm vi theo namespace**. Khi quyền truy cập namespace chia sẻ bị thu hồi, quyền truy cập vào CA cũng **bị dừng**.

- Khi làm việc với namespace chia sẻ, chủ namespace và bên dùng **mặc định không có quyền truy cập metric Amazon CloudWatch chéo account**. Target metric chỉ được đẩy lên account **sở hữu client service**. Account sở hữu client service không có quyền xem metric mà account sở hữu client-server service nhận được, và ngược lại. Để cho phép truy cập metric chéo account, hãy thiết lập **CloudWatch cross-account observability**. Thông tin thêm về cấu hình cross-account observability, xem *CloudWatch cross-account observabilty* (<https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/CloudWatch-Unified-Cross-Account.html>) trong *Amazon CloudWatch User Guide*. Thông tin thêm về CloudWatch metric cho Service Connect, xem *Amazon ECS CloudWatch metrics* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/available-metrics.html>).

**💡 Giải thích thêm**

Quyền của từng bên:

| Việc | Chủ namespace | Bên dùng namespace |
|---|---|---|
| Quản lý resource share (AWS RAM) | ✅ | ❌ |
| Sửa namespace | ✅ | ❌ |
| Tạo và quản lý service trong namespace | ✅ | ✅ |
| Private DNS: cho phép VPC liên kết hosted zone | Gọi `create-vpc-association-authorization` | — |
| Private DNS: liên kết VPC vào hosted zone | — | Gọi `associate-vpc-with-hosted-zone` |

Thứ tự hai lệnh Route 53 cho private DNS namespace:

```
1. Chủ namespace:   create-vpc-association-authorization  (hosted zone + VPC của bên dùng)
2. Bên dùng:        associate-vpc-with-hosted-zone         (hosted zone + VPC của mình)
```

Khi quyền truy cập bị **thu hồi**:

| Bị ảnh hưởng | Theo trang |
|---|---|
| `CreateService`, `UpdateService`, `ListServicesByNamespace` | Thất bại |
| TLS (AWS Private CA gắn theo namespace) | Mất quyền truy cập CA |

Metric chéo account:

| Account | Thấy metric của |
|---|---|
| Sở hữu **client service** | Target metric (đẩy lên account này) |
| Sở hữu **client-server service** | Không thấy metric mà account client nhận, và ngược lại |
| Muốn xem chéo | Bật CloudWatch cross-account observability |

---

## 2. Tổng hợp

**💡 Giải thích thêm**

Tổng hợp từ các mục trên của chính trang:

| Chủ đề | Quy tắc |
|---|---|
| Phạm vi | Nhiều account, cùng Region; AWS RAM phải có ở Region đó |
| Chia sẻ | Qua AWS RAM với `AWSRAMPermissionCloudMapECSFullPermission` |
| Cấu hình Service Connect | Dùng **ARN** namespace, không dùng ID |
| Loại namespace | HTTP, Private DNS, Public DNS đều được |
| Khác VPC | Cần kết nối giữa VPC (vd VPC Peering) |
| Private DNS + truy vấn DNS | Chủ: `create-vpc-association-authorization`; bên dùng: `associate-vpc-with-hosted-zone` |
| Discovery name | Duy nhất trong namespace, bất kể account nào |
| TLS | Private CA gắn theo namespace; mất quyền namespace → mất quyền CA |
| Metric | Không chéo account mặc định; cần CloudWatch cross-account observability |

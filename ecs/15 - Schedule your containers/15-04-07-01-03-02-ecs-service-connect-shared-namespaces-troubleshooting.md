# Troubleshooting Amazon ECS Service Connect with shared AWS Cloud Map namespaces

> Nguồn: **Amazon ECS Developer Guide — Troubleshooting Amazon ECS Service Connect with shared AWS Cloud Map namespaces**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-connect-shared-namespaces-troubleshooting.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: **bản dịch** sang tiếng Việt, đi đúng thứ tự và đúng nội dung của trang gốc, không thêm, không bớt ý. Mẫu thông báo lỗi giữ nguyên tiếng Anh như trên docs.
> - Khối **💡 Giải thích thêm**: chỉ giải thích lại những gì **chính trang này** đã nói, cho dễ hiểu hơn. Không đưa thông tin từ trang khác hay ví dụ tự đặt.
> - Tên API, action IAM, trạng thái giữ nguyên tiếng Anh.
> - Tài liệu AWS được phát hành theo giấy phép **Creative Commons Attribution-ShareAlike 4.0**; bản dịch này ghi rõ nguồn ở trên.

---

## Mục lục

0. [Phần mở đầu](#0-phần-mở-đầu)
1. [Lỗi dạng "no resource-based policy allows"](#1-lỗi-dạng-no-resource-based-policy-allows)
2. [Lỗi dạng "explicit deny in an identity-based policy"](#2-lỗi-dạng-explicit-deny-in-an-identity-based-policy)
3. [Tổng hợp](#3-tổng-hợp)

---

## 0. Phần mở đầu

**📘 Theo docs AWS**

Dùng thông tin sau để xử lý sự cố với AWS Cloud Map namespace chia sẻ và Service Connect. Thông tin thêm về cách tìm thông báo lỗi, xem *Amazon ECS troubleshooting* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/troubleshooting.html>).

Thông báo lỗi liên quan tới quyền xuất hiện do **thiếu quyền**, hoặc khi quyền truy cập namespace **bị thu hồi**.

> **Important:** Bạn **phải** dùng managed permission **`AWSRAMPermissionCloudMapECSFullPermission`** để chia sẻ namespace thì Service Connect mới hoạt động đúng với namespace đó.

Thông báo lỗi xuất hiện theo một trong các định dạng sau:

**💡 Giải thích thêm**

| Nguyên nhân gốc theo trang | |
|---|---|
| Thiếu quyền | Vd chưa dùng đúng managed permission, chưa chấp nhận share |
| Quyền bị thu hồi | Chủ namespace ngừng chia sẻ |

Trang chia lỗi theo **hai định dạng thông báo** (mục 1 và 2).

---

## 1. Lỗi dạng "no resource-based policy allows"

**📘 Theo docs AWS**

Định dạng thông báo:

```
An error occurred (ClientException) when calling the <OperationName> operation: User: arn:aws:iam::<account-id>:user/<user-name> is not authorized to perform: <ActionName> on resource: <ResourceArn> because no resource-based policy allows the <ActionName> action
```

Các tình huống sau có thể gây thông báo lỗi theo định dạng này:

### 1.1 Tạo hoặc cập nhật cluster thất bại

**📘 Theo docs AWS**

Các vấn đề này xảy ra khi thao tác Amazon ECS như **`CreateCluster`** hoặc **`UpdateCluster`** thất bại do thiếu quyền AWS Cloud Map. Các thao tác này cần quyền cho action AWS Cloud Map sau:

- `servicediscovery:GetNamespace`

Đảm bảo **lời mời resource share đã được chấp nhận** trong account bên dùng, và **đúng ARN namespace** đang được dùng trong cấu hình Service Connect.

### 1.2 Tạo hoặc cập nhật service thất bại

**📘 Theo docs AWS**

Các vấn đề này xảy ra khi thao tác Amazon ECS như **`CreateService`** hoặc **`UpdateService`** thất bại do thiếu quyền AWS Cloud Map. Các thao tác này cần quyền cho các action AWS Cloud Map sau:

- `servicediscovery:CreateService`
- `servicediscovery:GetNamespace`
- `servicediscovery:GetOperation` (khi tạo AWS Cloud Map service mới)
- `servicediscovery:GetService` (khi AWS Cloud Map service đã tồn tại)

Đảm bảo lời mời resource share đã được chấp nhận trong account bên dùng, và đúng ARN namespace đang được dùng trong cấu hình Service Connect.

### 1.3 Thao tác `ListServicesByNamespace` thất bại

**📘 Theo docs AWS**

Vấn đề này xảy ra khi thao tác Amazon ECS **`ListServicesByNamespace`** thất bại. Thao tác này cần quyền cho action AWS Cloud Map sau:

- `servicediscovery:GetNamespace`

Để khắc phục:

- Kiểm tra account bên dùng có quyền `servicediscovery:GetNamespace`.
- Dùng **ARN** namespace khi gọi API, **không** dùng tên.
- Đảm bảo resource share **đang hoạt động** và lời mời đã được chấp nhận.

**💡 Giải thích thêm**

| Thao tác ECS lỗi | Action Cloud Map cần | Cách khắc phục theo trang |
|---|---|---|
| `CreateCluster`, `UpdateCluster` | `GetNamespace` | Đã chấp nhận share; dùng đúng ARN |
| `CreateService`, `UpdateService` | `CreateService`, `GetNamespace`, `GetOperation` (Cloud Map service mới), `GetService` (Cloud Map service đã có) | Đã chấp nhận share; dùng đúng ARN |
| `ListServicesByNamespace` | `GetNamespace` | Có quyền `GetNamespace`; gọi bằng **ARN**, không bằng tên; share đang hoạt động và đã chấp nhận |

Ba điểm lặp lại ở cả ba tình huống: **share đã được chấp nhận**, **dùng ARN đúng**, **share còn hoạt động**.

---

## 2. Lỗi dạng "explicit deny in an identity-based policy"

**📘 Theo docs AWS**

Định dạng thông báo:

```
User: <iam-user> is not authorized to perform: <ActionName> on resource: <ResourceArn> with an explicit deny in an identity-based policy.
```

Các tình huống sau có thể gây thông báo lỗi theo định dạng này:

### 2.1 Xoá service thất bại và kẹt ở trạng thái `DRAINING`

**📘 Theo docs AWS**

Vấn đề này xảy ra khi thao tác Amazon ECS **`DeleteService`** thất bại do thiếu quyền **`servicediscovery:DeleteService`** khi quyền truy cập namespace **bị thu hồi**. Service có thể **ban đầu trông như đã xoá thành công** nhưng sẽ **kẹt ở trạng thái `DRAINING`**. Thông báo lỗi xuất hiện dưới dạng một **Amazon ECS service event**.

Để khắc phục, **chủ namespace** phải **chia sẻ lại** namespace với account bên dùng để cho phép hoàn tất việc xoá service.

### 2.2 Task trong service không chạy được

**📘 Theo docs AWS**

Vấn đề này xảy ra khi task không khởi động được do thiếu quyền. Thông báo lỗi hiện dưới dạng **stopped task error**. Thông tin thêm, xem *Resolve Amazon ECS stopped task errors* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/resolve-stopped-errors.html>).

Các action AWS Cloud Map sau là cần thiết để chạy một task:

- `servicediscovery:GetOperation`
- `servicediscovery:RegisterInstance`

Đảm bảo account bên dùng có các quyền cần thiết và namespace chia sẻ **truy cập được**.

### 2.3 Task không dừng gọn hoặc kẹt ở trạng thái `DEACTIVATING` hoặc `DEPROVISIONING`

**📘 Theo docs AWS**

Vấn đề này xảy ra khi task không huỷ đăng ký khỏi AWS Cloud Map service trong lúc tắt do thiếu quyền. Lỗi hiện dưới dạng **`statusReason`** trong **task attachment**, có thể lấy bằng API **`DescribeTasks`**. Thông tin thêm, xem *DescribeTasks* (<https://docs.aws.amazon.com/AmazonECS/latest/APIReference/API_DescribeTasks.html>) trong *Amazon Elastic Container Service API Reference*.

Các action AWS Cloud Map sau là cần thiết để dừng một task:

- `servicediscovery:DeregisterInstance`
- `servicediscovery:GetOperation`

Nếu quyền truy cập namespace chia sẻ bị thu hồi, task có thể **ở lại** trạng thái `DEACTIVATING` hoặc `DEPROVISIONING` cho tới khi quyền truy cập namespace được **khôi phục**. Hãy yêu cầu chủ namespace khôi phục quyền truy cập namespace.

**💡 Giải thích thêm**

| Triệu chứng | Action Cloud Map cần | Lỗi hiện ở đâu | Cách khắc phục theo trang |
|---|---|---|---|
| Xoá service kẹt ở `DRAINING` | `DeleteService` | Service event | Chủ namespace **chia sẻ lại** để việc xoá hoàn tất |
| Task không khởi động | `GetOperation`, `RegisterInstance` | Stopped task error | Bên dùng có đủ quyền, namespace truy cập được |
| Task kẹt ở `DEACTIVATING` / `DEPROVISIONING` | `DeregisterInstance`, `GetOperation` | `statusReason` trong task attachment (`DescribeTasks`) | Chủ namespace **khôi phục** quyền truy cập |

Các action Cloud Map theo vòng đời, ghép từ cả trang:

```
Tạo/cập nhật cluster    → GetNamespace
Tạo/cập nhật service    → CreateService, GetNamespace, GetOperation | GetService
Liệt kê theo namespace  → GetNamespace
Chạy task               → GetOperation, RegisterInstance
Dừng task               → DeregisterInstance, GetOperation
Xoá service             → DeleteService
```

Hệ quả khi bị **thu hồi** quyền: không chỉ không tạo được gì mới, mà service và task **đang có** còn có thể **kẹt** (`DRAINING`, `DEACTIVATING`, `DEPROVISIONING`) cho tới khi quyền được trả lại.

---

## 3. Tổng hợp

**💡 Giải thích thêm**

| Định dạng lỗi | Tình huống | Hướng khắc phục chung |
|---|---|---|
| "…because no resource-based policy allows…" | Tạo/cập nhật cluster, tạo/cập nhật service, `ListServicesByNamespace` | Chấp nhận resource share; dùng **ARN** namespace; share còn hoạt động; có quyền `servicediscovery:GetNamespace` |
| "…with an explicit deny in an identity-based policy." | Xoá service kẹt `DRAINING`; task không chạy; task kẹt khi dừng | Đảm bảo có đủ quyền Cloud Map; nếu bị thu hồi → nhờ chủ namespace chia sẻ lại / khôi phục |

Luôn kiểm tra trước: namespace được chia sẻ bằng managed permission **`AWSRAMPermissionCloudMapECSFullPermission`** (khung Important).

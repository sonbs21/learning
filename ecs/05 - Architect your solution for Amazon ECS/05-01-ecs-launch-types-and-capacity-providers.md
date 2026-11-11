# Amazon ECS launch types and capacity providers

> Nguồn: **Amazon ECS Developer Guide — Amazon Amazon ECS launch types and capacity providers**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/capacity-launch-type-comparison.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: **bản dịch** sang tiếng Việt, đi đúng thứ tự và đúng nội dung của trang gốc, không thêm, không bớt ý.
> - Khối **💡 Giải thích thêm**: chỉ giải thích lại những gì **chính trang này** đã nói, cho dễ hiểu hơn. Không đưa thông tin từ trang khác hay ví dụ tự đặt.
> - Tên launch type, capacity provider, tham số và API giữ nguyên tiếng Anh.
> - Tài liệu AWS được phát hành theo giấy phép **Creative Commons Attribution-ShareAlike 4.0**; bản dịch này ghi rõ nguồn ở trên.

---

## Mục lục

0. [Phần mở đầu](#0-phần-mở-đầu)
1. [Best practices](#1-best-practices)
2. [Service mutability](#2-service-mutability)
   - [2.1 Capacity provider sang capacity provider](#21-capacity-provider-sang-capacity-provider)
   - [2.2 Launch type sang capacity provider](#22-launch-type-sang-capacity-provider)
   - [2.3 Launch type sang launch type](#23-launch-type-sang-launch-type)
   - [2.4 Capacity provider sang launch type](#24-capacity-provider-sang-launch-type)

---

## 0. Phần mở đầu

**📘 Theo docs AWS**

Amazon ECS cung cấp hai cách để cấu hình capacity cho workload. Bạn có thể dùng launch type hoặc capacity provider. Launch type gồm EC2, Fargate và External. Capacity provider mang lại sự linh hoạt cao hơn và các tính năng nâng cao cho việc quản lý capacity. Bạn có thể chạy workload trên compute serverless với capacity provider Fargate và Fargate Spot, trên EC2 instance tự quản lý thông qua capacity provider Auto Scaling group, hoặc trên compute được quản lý hoàn toàn bằng capacity provider Amazon ECS Managed Instances — kết hợp sự đơn giản của Fargate với sự linh hoạt của EC2 compute. Capacity provider cho phép kiểm soát việc phân bổ tài nguyên tốt hơn và có thể giúp tối ưu cả hiệu năng lẫn chi phí. Capacity provider là cách **được khuyến nghị** để cấu hình capacity cho workload, so với launch type truyền thống. Dùng nội dung dưới đây để hiểu sự khác nhau giữa capacity provider và launch type.

**💡 Giải thích thêm**

| Cách cấu hình | Các lựa chọn theo đoạn trên |
|---|---|
| **Launch type** | EC2, Fargate, External |
| **Capacity provider** (khuyến nghị) | Fargate và Fargate Spot (serverless); Auto Scaling group (EC2 tự quản lý); Amazon ECS Managed Instances (được quản lý hoàn toàn) |

Tên trang gốc bị lặp chữ "Amazon Amazon".

---

## 1. Best practices

**📘 Theo docs AWS**

Sau đây là các best practice:

**Dùng launch type để định nghĩa khả năng tương thích hạ tầng**

Launch type định nghĩa hạ tầng mà task và service chạy trên đó. Khi định nghĩa task, hãy chỉ định `RequiresCompatibilities` gồm một hoặc nhiều launch type tương thích với task. Bạn có thể dùng các launch type sau: EC2, Fargate, External và Amazon ECS Managed Instances. Dù bạn cũng có thể dùng launch type để chạy task hoặc service, chúng tôi khuyến nghị **chỉ dùng launch type để định nghĩa khả năng tương thích trong task definition**, và **dùng capacity provider để launch task hoặc service**. Lưu ý rằng bạn có thể chọn một hoặc nhiều launch type để định nghĩa khả năng tương thích cho task.

**Dùng capacity provider để cấu hình compute capacity**

Khi launch task hoặc service, hãy cấu hình một capacity provider strategy. Amazon ECS hỗ trợ các capacity provider sau: Fargate và FARGATE_SPOT, Auto Scaling group cho EC2 instance tự quản lý, và Amazon ECS Managed Instances. Lưu ý rằng Spot Fleet chỉ có dưới dạng capacity provider, không có dưới dạng launch type. Bạn có thể tạo một hoặc nhiều capacity provider Amazon ECS Managed Instances hoặc Auto Scaling group trong một cluster. Capacity provider Fargate và Fargate Spot được Amazon ECS tạo và quản lý trên mọi cluster, bạn không cần tạo chúng. Một cluster có thể có lẫn mọi loại capacity provider, tuy nhiên một capacity provider strategy **không** được có lẫn các loại capacity provider khác nhau.

**Cập nhật capacity cho service**

Bạn chỉ cần cập nhật capacity provider strategy của service để chuyển nó từ loại compute này sang loại compute khác.

**💡 Giải thích thêm**

Cách chia vai trò mà trang khuyến nghị:

| Dùng | Ở đâu | Để làm gì |
|---|---|---|
| **Launch type** | `RequiresCompatibilities` trong task definition | Khai báo task **tương thích** với hạ tầng nào (EC2, Fargate, External, Amazon ECS Managed Instances) — chọn được nhiều cái |
| **Capacity provider strategy** | Khi launch task hoặc service | Quyết định task **thực sự chạy** trên capacity nào |

Về capacity provider:

| Loại | Ai tạo |
|---|---|
| Fargate, FARGATE_SPOT | Amazon ECS tạo sẵn trên mọi cluster |
| Auto Scaling group | Bạn tạo (một hoặc nhiều mỗi cluster) |
| Amazon ECS Managed Instances | Bạn tạo (một hoặc nhiều mỗi cluster) |

Quy tắc trộn: **cluster** có thể chứa mọi loại; **một strategy** chỉ chứa **một loại**.

Đoạn mở đầu liệt kê launch type gồm EC2, Fargate, External; còn mục này liệt kê thêm **Amazon ECS Managed Instances** trong danh sách dùng cho `RequiresCompatibilities`.

---

## 2. Service mutability

**📘 Theo docs AWS**

Amazon ECS hỗ trợ cập nhật service giữa các capacity provider khác nhau. Điều này cho phép:

- Cập nhật liền mạch từ launch type sang capacity provider
- Chuyển đổi giữa các loại capacity provider khác nhau
- Thử các lựa chọn compute khác nhau mà không phải tạo lại service

Sau đây là tổng quan quy trình:

1. **Cập nhật task definition** – Đảm bảo `requiresCompatibilities` có capacity provider đích, ví dụ `MANAGED_INSTANCES`.

   > **Note:** Task definition phải vượt qua bước kiểm tra tương thích với capacity provider đích. Nếu kiểm tra `requiresCompatibilities` thất bại với phiên bản task definition đó, lời gọi `UpdateService` sẽ thất bại.

2. **Tạo capacity provider** – Nếu bạn dùng Amazon EC2 Auto Scaling group tuỳ chỉnh, hãy tạo capacity provider.
3. **Cập nhật service** – Sửa service để dùng capacity provider strategy thay cho launch type.
4. **Kiểm tra deployment** – Xác nhận task được deploy thành công.
5. **Giám sát và tối ưu** – Điều chỉnh cài đặt capacity provider khi cần.

**💡 Giải thích thêm**

```
1. Task definition: requiresCompatibilities có đích (vd MANAGED_INSTANCES)
        │   (không đạt → UpdateService thất bại)
2. Tạo capacity provider (nếu dùng Auto Scaling group tuỳ chỉnh)
3. UpdateService: dùng capacity provider strategy thay launch type
4. Kiểm tra task deploy thành công
5. Giám sát, chỉnh cài đặt capacity provider
```

Bước 1 là chỗ dễ thất bại nhất: service không chuyển được nếu task definition chưa khai báo tương thích với capacity provider đích.

### 2.1 Capacity provider sang capacity provider

**📘 Theo docs AWS**

**Mọi** cập nhật từ capacity provider sang capacity provider đều được hỗ trợ:

- Capacity provider Amazon EC2 Auto Scaling group → Amazon ECS Managed Instances
- Capacity provider Fargate → Amazon ECS Managed Instances
- Capacity provider Amazon EC2 Auto Scaling group → capacity provider Fargate
- Amazon ECS Managed Instances → capacity provider Fargate
- Capacity provider Fargate → capacity provider Amazon EC2 Auto Scaling group
- Amazon ECS Managed Instances → capacity provider Amazon EC2 Auto Scaling group

### 2.2 Launch type sang capacity provider

**📘 Theo docs AWS**

**Mọi** cập nhật từ launch type sang capacity provider đều được hỗ trợ:

- EC2 launch type → Amazon ECS Managed Instances
- Fargate launch type → Amazon ECS Managed Instances
- EC2 launch type → capacity provider Fargate
- EC2 launch type → capacity provider EC2 Auto Scaling group
- Fargate launch type → capacity provider Amazon EC2 Auto Scaling group
- Fargate launch type → capacity provider Fargate
- External launch type → Amazon ECS Managed Instances
- External launch type → capacity provider Fargate
- External launch type → capacity provider Amazon EC2 Auto Scaling group

### 2.3 Launch type sang launch type

**📘 Theo docs AWS**

Cập nhật từ launch type sang launch type **không** được hỗ trợ:

- EC2 launch type → Fargate launch type (hãy dùng capacity provider Fargate thay thế)
- Fargate launch type → EC2 launch type (hãy dùng capacity provider Amazon EC2 Auto Scaling group thay thế)

Thay vì chuyển giữa các launch type, hãy chuyển sang capacity provider tương đương để có thêm chức năng và tương thích về sau.

### 2.4 Capacity provider sang launch type

**📘 Theo docs AWS**

Cập nhật từ capacity provider sang launch type **không** được hỗ trợ. Nếu ban đầu bạn tạo service với một launch type rồi sau đó cập nhật service để dùng capacity provider, bạn có thể đưa service về lại launch type ban đầu. Để đưa về, truyền một **danh sách rỗng** làm `capacityProviderStrategy` khi gọi `UpdateService`.

> **Note:** Cách này chỉ đưa service về **đúng launch type đã chỉ định khi service được tạo lần đầu**. Bạn không thể dùng cách này để chuyển service sang một launch type khác.

**💡 Giải thích thêm**

Bảng tổng hợp bốn trường hợp ở mục 2:

| Từ \ Sang | Capacity provider | Launch type |
|---|---|---|
| **Capacity provider** | ✅ Mọi trường hợp (6 hướng giữa Auto Scaling group, Fargate, Managed Instances) | ❌ Không hỗ trợ — **trừ** việc quay về đúng launch type ban đầu bằng `capacityProviderStrategy` rỗng |
| **Launch type** | ✅ Mọi trường hợp (EC2, Fargate, External → Managed Instances, Fargate, Auto Scaling group) | ❌ Không hỗ trợ (EC2 ↔ Fargate) — dùng capacity provider tương đương |

Ví dụ cho trường hợp "quay về" ở 2.4, theo đúng mô tả của trang:

```
Tạo service với Fargate launch type
   → UpdateService sang capacity provider Managed Instances
   → UpdateService với capacityProviderStrategy = []   ✅ quay về Fargate launch type
   → muốn sang EC2 launch type                          ❌ không được (không phải launch type ban đầu)
```

Hướng đi trang khuyến nghị: thay vì đổi launch type, chuyển sang **capacity provider tương đương** (EC2 launch type → Auto Scaling group capacity provider; Fargate launch type → Fargate capacity provider).

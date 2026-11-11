# Clusters for Amazon ECS Managed Instances

> Nguồn: **Amazon ECS Developer Guide — Clusters for Amazon ECS Managed Instances**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/managed-instance-clusters.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: diễn đạt lại bằng tiếng Việt, bám sát từng ý và đúng thứ tự của trang gốc, không thêm thông tin ngoài trang. Đây không phải bản dịch từng chữ; khi cần câu chữ chính xác, đọc link gốc ở trên.
> - Khối **💡 Giải thích thêm**: phần giải thích để dễ hiểu hơn, chỉ làm rõ những gì docs đã nói.

---

## Mục lục

1. [Capacity provider trong cluster](#1-capacity-provider-trong-cluster)
2. [Hai loại capacity provider của Managed Instances](#2-hai-loại-capacity-provider-của-managed-instances)
3. [Ai tạo instance và capacity provider](#3-ai-tạo-instance-và-capacity-provider)
4. [Ghi chú và liên hệ với các file trước](#4-ghi-chú-và-liên-hệ-với-các-file-trước)
5. [Tóm tắt](#5-tóm-tắt)

---

## 1. Capacity provider trong cluster

**📘 Theo docs AWS**

- Capacity provider **quản lý việc scale hạ tầng** cho task trong cluster.
- Sau khi tạo cluster, bạn tạo **một hoặc nhiều** capacity provider và một **capacity provider strategy** (tuỳ chọn) cho cluster.
- Strategy quyết định task được **phân bổ** qua các capacity provider của cluster thế nào.
- Khi chạy standalone task hoặc tạo service: dùng **strategy mặc định** của cluster, hoặc một strategy **ghi đè** mặc định.

**💡 Giải thích thêm**

Phần này nhắc lại ý ở file *Amazon ECS clusters* (mục capacity provider), áp dụng cho cluster dùng Managed Instances.

---

## 2. Hai loại capacity provider của Managed Instances

**📘 Theo docs AWS**

| Loại | Tiêu chí chọn instance |
|---|---|
| **Default** | Instance **tiết kiệm chi phí nhất** đáp ứng các yêu cầu sau. **Task definition:** `operatingSystemFamily`, `cpuArchitecture`, `cpu`, `memory`. **Service definition:** `placementConstraints`, `placementStrategy` |
| **Custom** | Instance đáp ứng **yêu cầu về thuộc tính và instance type** bạn khai báo khi tạo cluster. Thuộc tính: *Amazon ECS container instance attributes* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task-placement-constraints.html#attributes>. Instance type: *Amazon EC2 instance type specifications* — <https://docs.aws.amazon.com/ec2/latest/instancetypes/ec2-instance-type-specifications.html> |

**💡 Giải thích thêm**

| | Default | Custom |
|---|---|---|
| Ai quyết định instance type | ECS, dựa trên task definition + service | Bạn khai báo thuộc tính / type |
| Phù hợp | Không có yêu cầu phần cứng đặc biệt | GPU, ARM cụ thể, local storage, VPC encryption, Spot, Capacity Reservations… |
| Cấu hình liên quan | — | `instanceRequirements`, `allowedInstanceTypes`, `excludedInstanceTypes`, `capacityOptionType`… (file *Managed Instances instance types*) |

Bảng tiêu chí của loại **Default** giống hệt best practice số 1 ở file *Instance selection best practices*.

---

## 3. Ai tạo instance và capacity provider

**📘 Theo docs AWS**

- ECS **launch instance** và **gắn** chúng vào capacity provider Managed Instances.
- Với **custom capacity provider**, ECS **cũng tạo** luôn capacity provider.

**💡 Giải thích thêm**

Câu cuối hiểu theo luồng tạo cluster trên console: khi bạn chọn cấu hình custom lúc tạo cluster (vd **Use custom – advanced** ở file *VPC Encryption control*), ECS tạo capacity provider custom tương ứng cho bạn. Trang không mô tả chi tiết luồng này; xem trang tiếp theo *Creating a cluster for Amazon ECS Managed Instances*. Đây là cách hiểu của mình từ câu chữ trên trang.

Lưu ý từ file *Architect for Amazon ECS Managed Instances*: capacity provider mặc định do console tạo **không** có `localStorageConfiguration` — muốn instance store thì phải tạo capacity provider custom.

---

## 4. Ghi chú và liên hệ với các file trước

**💡 Giải thích thêm**

**Chỗ không khớp:** tiêu chí của loại Default có `placementStrategy` của service, trong khi file *How Amazon ECS places tasks* nói Managed Instances **không hỗ trợ placement strategy**. Ghi chú này lặp lại điều đã nêu ở file *Instance selection best practices*.

**Liên hệ:**

| Nội dung | File liên quan |
|---|---|
| Default vs custom capacity provider | *Architect for Amazon ECS Managed Instances*, *Managed Instances instance types* |
| Tiêu chí chọn instance mặc định | *Instance selection best practices* |
| Thuộc tính container instance | *Define which container instances…* (Managed Instances chỉ hỗ trợ 4 thuộc tính trong constraint) |
| Strategy mặc định của cluster | *Amazon ECS clusters* |
| Một strategy chỉ một loại provider | *Amazon ECS clusters*, *Architect for Amazon ECS Managed Instances* |

---

## 5. Tóm tắt

- Capacity provider quản lý scale hạ tầng; tạo một hoặc nhiều capacity provider và strategy (tuỳ chọn) cho cluster; task/service dùng strategy mặc định hoặc strategy ghi đè.
- **Default capacity provider**: ECS chọn instance **rẻ nhất** khớp `operatingSystemFamily`, `cpuArchitecture`, `cpu`, `memory` (task definition) và `placementConstraints`, `placementStrategy` (service).
- **Custom capacity provider**: instance khớp **thuộc tính và instance type** bạn khai báo khi tạo cluster.
- ECS **launch instance** và gắn vào capacity provider; với custom, ECS **tạo luôn** capacity provider.

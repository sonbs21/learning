# Amazon ECS task definition differences for Amazon ECS Managed Instances

> Nguồn: **Amazon ECS Developer Guide — Amazon ECS task definition differences for Amazon ECS Managed Instances**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/managed-instances-tasks-services.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: diễn đạt lại bằng tiếng Việt, bám sát từng ý và đúng thứ tự của trang gốc, không thêm thông tin ngoài trang. Đây không phải bản dịch từng chữ; khi cần câu chữ chính xác, đọc link gốc ở trên.
> - Khối **💡 Giải thích thêm**: phần giải thích để dễ hiểu hơn, chỉ làm rõ những gì docs đã nói.
> - Tên tham số giữ nguyên như docs.

---

## Mục lục

1. [Tổng quan](#1-tổng-quan)
2. [Tham số KHÔNG hợp lệ trên Managed Instances](#2-tham-số-không-hợp-lệ-trên-managed-instances)
3. [Tham số hợp lệ nhưng có giới hạn](#3-tham-số-hợp-lệ-nhưng-có-giới-hạn)
4. [Đảm bảo task definition được kiểm tra hợp lệ khi register](#4-đảm-bảo-task-definition-được-kiểm-tra-hợp-lệ-khi-register)
5. [Checklist khi chuyển task definition sang Managed Instances](#5-checklist-khi-chuyển-task-definition-sang-managed-instances)
6. [Tóm tắt](#6-tóm-tắt)

---

## 1. Tổng quan

**📘 Theo docs AWS**

- Muốn dùng **Amazon ECS Managed Instances**, bạn phải cấu hình task definition để dùng **launch type Managed Instances**.
- Khi dùng Managed Instances có thêm một số điều cần cân nhắc.
- Task trên Managed Instances hỗ trợ **phần lớn** tham số task definition, nhưng **một số tham số** có hành vi riêng hoặc bị giới hạn.

**💡 Giải thích thêm**

Trang này là "danh sách khác biệt": nó không mô tả lại toàn bộ task definition, mà chỉ ra những tham số **không dùng được** (mục 2) và **dùng được nhưng có điều kiện** (mục 3). Rất hữu ích khi bạn chuyển task definition đang chạy trên EC2 sang Managed Instances.

---

## 2. Tham số KHÔNG hợp lệ trên Managed Instances

**📘 Theo docs AWS**

Các tham số sau **không hợp lệ** với task chạy trên Managed Instances:

| Tham số | | Tham số |
|---|---|---|
| `disableNetworking` | | `inferenceAccelerator` |
| `dnsSearchDomains` | | `ipcMode` |
| `dnsServers` | | `links` |
| `dockerSecurityOptions` | | `maxSwap` |
| `dockerVolumeConfiguration` | | `proxyConfiguration` |
| `ephemeralStorage` | | `sharedMemorySize` |
| `extraHosts` | | volume dùng `sourcepath` |
| `fsxWindowsFileServerVolumeConfiguration` | | `swappiness` |
| `hostname` | | `tmpfs` |

**💡 Giải thích thêm**

Nhóm lại theo chủ đề để dễ nhớ (cách nhóm là của mình, docs chỉ liệt kê):

| Nhóm | Tham số | Ghi chú |
|---|---|---|
| Mạng / DNS | `disableNetworking`, `dnsSearchDomains`, `dnsServers`, `extraHosts`, `hostname`, `links` | `links` gắn với mode `bridge`, mà Managed Instances không có `bridge` (xem file networking Managed Instances) |
| Memory / swap | `maxSwap`, `swappiness`, `sharedMemorySize`, `tmpfs` | Liên hệ file *container swap*: hai tham số swap **không dùng được** trên Managed Instances |
| Lưu trữ | `dockerVolumeConfiguration`, `ephemeralStorage`, `fsxWindowsFileServerVolumeConfiguration`, volume `sourcepath` | Xem lưu ý về `sourcepath` ở mục 3 |
| Bảo mật / tiến trình | `dockerSecurityOptions`, `ipcMode` | — |
| Khác | `inferenceAccelerator`, `proxyConfiguration` | — |

Về "volume dùng `sourcepath`": danh sách mục 2 ghi volume `sourcepath` không hợp lệ, nhưng mục 3 lại mô tả cách dùng bind mount với `sourcePath` (trỏ tới thư mục ghi được như `/var`, `/tmp`). Hai chỗ này trên docs có vẻ mâu thuẫn (và viết hoa khác nhau: `sourcepath` / `sourcePath`). Mình giữ nguyên cả hai như docs; khi dùng thực tế nên thử register task definition để xem ECS chấp nhận hay không.

---

## 3. Tham số hợp lệ nhưng có giới hạn

**📘 Theo docs AWS**

| Tham số | Giới hạn trên Managed Instances |
|---|---|
| `networkConfiguration` | Task dùng network mode **`awsvpc`** hoặc **`host`**. |
| `placementConstraints` | Chỉ hỗ trợ các thuộc tính ràng buộc: `ecs.subnet-id`, `ecs.availability-zone`, `ecs.instance-type`, `ecs.cpu-architecture`. |
| `requiresCompatibilities` | **Phải có** `MANAGED_INSTANCES` để task definition tương thích với Managed Instances. |
| `resourceRequirement` | **Không hỗ trợ** `InferenceAccelerator`. |
| `operatingSystemFamily` | Managed Instances dùng **`LINUX`**. |
| `volumes` | Bind mount có `sourcePath` thì đường dẫn phải trỏ tới **thư mục ghi được** trên host. Một phần filesystem của Managed Instance là **chỉ đọc**; thư mục ghi được gồm **`/var`** và **`/tmp`**. (Xem *Use bind mounts with Amazon ECS* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/bind-mounts.html>.) |

**💡 Giải thích thêm**

- **Network mode**: khớp với file *task networking for Managed Instances* — chỉ `awsvpc` và `host`, và phải khai báo rõ.
- **Placement constraints**: chỉ ràng buộc theo subnet, AZ, instance type và kiến trúc CPU. Ví dụ ý nghĩa: `ecs.cpu-architecture` để buộc task chạy trên ARM hoặc x86. Những thuộc tính tuỳ chỉnh (custom attribute) bạn hay gắn lên instance EC2 tự quản lý sẽ không có ở đây — đây là suy luận từ việc docs chỉ liệt kê bốn thuộc tính.
- **OS**: chỉ Linux → không có Windows container trên Managed Instances.
- **Bind mount**: nếu task definition cũ trỏ `sourcePath` vào thư mục khác (ví dụ `/data`, `/opt/...`), cần đổi sang dưới `/var` hoặc `/tmp`.

---

## 4. Đảm bảo task definition được kiểm tra hợp lệ khi register

**📘 Theo docs AWS**

Để task definition được kiểm tra hợp lệ cho Managed Instances, khi register hãy chỉ định:

- **AWS Management Console**: trường **Requires Compatibilities** → chọn `MANAGED_INSTANCES`.
- **AWS CLI**: dùng tuỳ chọn **`--requires-compatibilities`**.
- **Amazon ECS API**: dùng cờ **`requiresCompatibilities`**.

**💡 Giải thích thêm**

Ví dụ minh hoạ CLI (cách ghép giá trị là của mình; docs chỉ nêu tên tuỳ chọn):

```bash
aws ecs register-task-definition \
  --cli-input-json file://taskdef.json \
  --requires-compatibilities MANAGED_INSTANCES
```

Hoặc trong file JSON:

```json
{
  "family": "<FAMILY>",
  "requiresCompatibilities": ["MANAGED_INSTANCES"],
  "networkMode": "awsvpc",
  "containerDefinitions": [ ... ]
}
```

Lợi ích: khai báo `MANAGED_INSTANCES` khiến ECS **kiểm tra ngay lúc register**, nên các tham số không hợp lệ ở mục 2 bị phát hiện sớm, thay vì lỗi lúc chạy task.

---

## 5. Checklist khi chuyển task definition sang Managed Instances

**💡 Giải thích thêm**

Ghép từ nội dung trang này và các file trước:

```
[ ] requiresCompatibilities có MANAGED_INSTANCES
[ ] networkMode khai báo rõ: awsvpc (khuyến nghị) hoặc host — không bridge, không bỏ trống
[ ] Gỡ các tham số không hợp lệ (mục 2), đặc biệt hay gặp:
      - maxSwap / swappiness (nếu từng cấu hình swap trên EC2)
      - links, hostname, extraHosts, dnsServers (thường đi kèm mode bridge)
      - dockerVolumeConfiguration, tmpfs, sharedMemorySize
[ ] placementConstraints chỉ dùng: ecs.subnet-id, ecs.availability-zone, ecs.instance-type, ecs.cpu-architecture
[ ] Không dùng InferenceAccelerator
[ ] Container Linux (operatingSystemFamily = LINUX)
[ ] Bind mount sourcePath trỏ vào thư mục ghi được (/var, /tmp)
```

---

## 6. Tóm tắt

- Dùng Managed Instances cần cấu hình task definition cho launch type Managed Instances; phần lớn tham số được hỗ trợ.
- **Không hợp lệ**: `disableNetworking`, `dnsSearchDomains`, `dnsServers`, `dockerSecurityOptions`, `dockerVolumeConfiguration`, `ephemeralStorage`, `extraHosts`, `fsxWindowsFileServerVolumeConfiguration`, `hostname`, `inferenceAccelerator`, `ipcMode`, `links`, `maxSwap`, `proxyConfiguration`, `sharedMemorySize`, volume `sourcepath`, `swappiness`, `tmpfs`.
- **Có giới hạn**: network mode chỉ `awsvpc`/`host`; placement constraint chỉ subnet-id, availability-zone, instance-type, cpu-architecture; `requiresCompatibilities` phải có `MANAGED_INSTANCES`; không `InferenceAccelerator`; OS là `LINUX`; bind mount `sourcePath` phải ở thư mục ghi được (`/var`, `/tmp`).
- Khai báo `MANAGED_INSTANCES` khi register (console, `--requires-compatibilities`, hoặc `requiresCompatibilities`) để được kiểm tra hợp lệ.

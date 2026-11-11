# Amazon ECS task definition differences for EC2 instances running Windows

> Nguồn: **Amazon ECS Developer Guide — Amazon ECS task definition differences for EC2 instances running Windows**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/windows_task_definitions.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: diễn đạt lại bằng tiếng Việt, bám sát từng ý và đúng thứ tự của trang gốc, không thêm thông tin ngoài trang. Đây không phải bản dịch từng chữ; khi cần câu chữ chính xác, đọc link gốc ở trên.
> - Khối **💡 Giải thích thêm**: phần giải thích để dễ hiểu hơn, chỉ làm rõ những gì docs đã nói.
> - Tên tham số giữ nguyên như docs.

---

## Mục lục

1. [Tổng quan](#1-tổng-quan)
2. [Tham số không hỗ trợ trong `containerDefinitions`](#2-tham-số-không-hỗ-trợ-trong-containerdefinitions)
3. [Tham số không hỗ trợ ở mức task](#3-tham-số-không-hỗ-trợ-ở-mức-task)
4. [Ghi chú về `taskRoleArn`](#4-ghi-chú-về-taskrolearn)
5. [So sánh với Fargate và Managed Instances](#5-so-sánh-với-fargate-và-managed-instances)
6. [Checklist khi viết task definition cho EC2 Windows](#6-checklist-khi-viết-task-definition-cho-ec2-windows)
7. [Tóm tắt](#7-tóm-tắt)

---

## 1. Tổng quan

**📘 Theo docs AWS**

Task chạy trên **EC2 instance Windows** không hỗ trợ tất cả tham số task definition: một số **hoàn toàn không hỗ trợ**, một số **hoạt động khác đi**.

**💡 Giải thích thêm**

Trang này ngắn, chỉ là danh sách tham số không hỗ trợ. Docs chia thành hai nhóm: tham số trong **container definition** (mục 2) và tham số ở **mức task** (mục 3), kèm vài khuyến nghị.

---

## 2. Tham số không hỗ trợ trong `containerDefinitions`

**📘 Theo docs AWS**

Trong `containerDefinitions`, các tham số sau **không được hỗ trợ** cho task definition EC2 Windows:

| Tham số | | Tham số |
|---|---|---|
| `disableNetworking` | | `linuxParameters` |
| `dnsServers` | | `privileged` |
| `dnsSearchDomains` | | `readonlyRootFilesystem` |
| `extraHosts` | | `user` |
| `links` | | `ulimits` |

Trong `volumes`: **`dockerVolumeConfiguration`** không được hỗ trợ.

**💡 Giải thích thêm**

Nhóm theo ý nghĩa (cách nhóm là của mình):

| Nhóm | Tham số | Ghi chú |
|---|---|---|
| Mạng / DNS | `disableNetworking`, `dnsServers`, `dnsSearchDomains`, `extraHosts`, `links` | `links` gắn với mode `bridge` — mà Windows trên EC2 không có `bridge` (file *task networking options for EC2*: Windows chỉ có `awsvpc` và `default`) |
| Tính năng riêng Linux | `linuxParameters`, `ulimits` | Liên hệ file *container swap*: `maxSwap`/`swappiness` nằm trong `linuxParameters`, nên Windows cũng không có swap theo container |
| Bảo mật / quyền | `privileged`, `readonlyRootFilesystem`, `user` | Không chạy được container privileged hay đặt root filesystem chỉ đọc |
| Lưu trữ | `dockerVolumeConfiguration` | Không dùng Docker volume |

---

## 3. Tham số không hỗ trợ ở mức task

**📘 Theo docs AWS**

| Tham số | Theo docs |
|---|---|
| `cpu` (mức task) | Không hỗ trợ. AWS **khuyến nghị khai báo CPU ở mức container** cho Windows container. |
| `memory` (mức task) | Không hỗ trợ. AWS **khuyến nghị khai báo memory ở mức container** cho Windows container. |
| `proxyConfiguration` | Không hỗ trợ. |
| `ipcMode` | Không hỗ trợ. |
| `pidMode` | Không hỗ trợ. |
| `taskRoleArn` | Có ghi chú riêng — xem mục 4. |

**💡 Giải thích thêm**

Đây là khác biệt lớn với Fargate: trên **Fargate**, CPU và memory **bắt buộc** ở mức task (file *Fargate task definition differences*); trên **EC2 Windows**, ngược lại, đặt ở **mức container**.

Ví dụ minh hoạ (giá trị do mình đặt, docs không có ví dụ):

```json
{
  "family": "<FAMILY>",
  "containerDefinitions": [
    {
      "name": "web",
      "image": "<IMAGE_WINDOWS>",
      "cpu": 1024,
      "memory": 2048
    }
  ]
}
```

Không có trường `cpu`/`memory` ở cấp ngoài cùng (mức task).

---

## 4. Ghi chú về `taskRoleArn`

**📘 Theo docs AWS**

- `taskRoleArn` nằm trong danh sách của trang.
- Docs giải thích: tính năng **IAM role cho task** trên instance EC2 Windows **cần cấu hình bổ sung**, nhưng phần lớn cấu hình đó **tương tự** việc cấu hình IAM role cho task trên container instance Linux. (Xem *Amazon EC2 Windows instance additional configuration* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task-iam-roles.html#windows_task_IAM_roles>.)

**💡 Giải thích thêm**

Có một điểm dễ gây nhầm trên docs: `taskRoleArn` được đặt trong danh sách "không hỗ trợ", nhưng phần ghi chú lại nói task role **dùng được** nếu làm thêm cấu hình. Cách hiểu hợp lý: không dùng được **như mặc định trên Linux**, mà cần **cấu hình bổ sung** trên instance Windows. Chi tiết cấu hình nằm ở link trên; trang này không mô tả.

Liên hệ file *Architect your application* (mỗi family một IAM role): trên Windows vẫn áp dụng được, nhưng phải làm phần cấu hình bổ sung trước.

---

## 5. So sánh với Fargate và Managed Instances

**💡 Giải thích thêm**

Ghép từ ba trang "task definition differences", chỉ dựa trên nội dung docs. "Không" = không hỗ trợ / không hợp lệ; "—" = trang đó không liệt kê.

| Tham số | EC2 Windows | Fargate | Managed Instances |
|---|---|---|---|
| `disableNetworking`, `dnsServers`, `dnsSearchDomains`, `extraHosts`, `links` | Không | Không | Không |
| `ipcMode` | Không | Không | Không |
| `privileged` | Không | Không | — |
| `dockerVolumeConfiguration` | Không | Không | Không |
| `proxyConfiguration` | Không | — | Không |
| `pidMode` | Không | — | — |
| `linuxParameters` | Không | Có giới hạn | — |
| `ulimits` | Không | Linux có, Windows không | — |
| `readonlyRootFilesystem`, `user` | Không | — | — |
| `cpu` / `memory` mức task | Không (đặt ở mức container) | **Bắt buộc** | — |
| `taskRoleArn` | Cần cấu hình bổ sung | — | — |

Nhận xét: nhóm tham số mạng/DNS và `links` bị loại ở cả ba môi trường, vì đều liên quan tới cách Docker tự quản lý mạng mà các môi trường này không dùng.

---

## 6. Checklist khi viết task definition cho EC2 Windows

**💡 Giải thích thêm**

```
[ ] Không có cpu / memory ở mức task → đặt ở từng container
[ ] Không dùng linuxParameters (→ không capabilities, không swap...)
[ ] Không dùng ulimits
[ ] Không privileged, readonlyRootFilesystem, user
[ ] Không DNS/hosts tuỳ chỉnh: dnsServers, dnsSearchDomains, extraHosts; không links, disableNetworking
[ ] Không dockerVolumeConfiguration
[ ] Không proxyConfiguration, ipcMode, pidMode
[ ] Network mode: awsvpc hoặc default (theo file task networking options for EC2)
[ ] Cần task role? → làm cấu hình bổ sung cho Windows trước
```

---

## 7. Tóm tắt

- Task trên EC2 Windows không hỗ trợ mọi tham số task definition.
- **Trong container definition, không hỗ trợ**: `disableNetworking`, `dnsServers`, `dnsSearchDomains`, `extraHosts`, `links`, `linuxParameters`, `privileged`, `readonlyRootFilesystem`, `user`, `ulimits`.
- **Trong volumes, không hỗ trợ**: `dockerVolumeConfiguration`.
- **Mức task, không hỗ trợ**: `cpu`, `memory` (khuyến nghị đặt ở mức container), `proxyConfiguration`, `ipcMode`, `pidMode`.
- **`taskRoleArn`**: IAM role cho task trên Windows cần cấu hình bổ sung, phần lớn tương tự Linux.

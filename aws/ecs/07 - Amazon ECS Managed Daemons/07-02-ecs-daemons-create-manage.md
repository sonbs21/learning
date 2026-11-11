# Creating and managing daemons

> Nguồn: **Amazon ECS Developer Guide — Creating and managing daemons**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/managed-daemons-create-manage.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: diễn đạt lại bằng tiếng Việt, bám sát từng bước và đúng thứ tự của trang gốc, không thêm thông tin ngoài trang. Đây không phải bản dịch từng chữ; khi cần câu chữ hoặc ví dụ code chính xác, đọc link gốc ở trên.
> - Khối **💡 Giải thích thêm**: phần giải thích để dễ hiểu hơn, chỉ làm rõ những gì docs đã nói.
> - Lệnh CLI dùng placeholder (`<...>`); tên lệnh, tham số và trường giữ đúng như docs. Tên nút trên console giữ tiếng Anh (in đậm).

---

## Mục lục

0. [Tổng quan](#0-tổng-quan)
1. [Điều kiện tiên quyết](#1-điều-kiện-tiên-quyết)
2. [Tạo daemon](#2-tạo-daemon)
3. [Kiểm tra daemon đã triển khai](#3-kiểm-tra-daemon-đã-triển-khai)
4. [Cập nhật daemon](#4-cập-nhật-daemon)
5. [Xoá daemon](#5-xoá-daemon)
6. [Vòng đời daemon tóm gọn](#6-vòng-đời-daemon-tóm-gọn)
7. [Tóm tắt](#7-tóm-tắt)

---

## 0. Tổng quan

**📘 Theo docs AWS**

Sau khi đăng ký **daemon task definition**, bạn tạo **daemon** để triển khai nó lên các capacity provider **Managed Instances**. ECS tự đặt **một daemon task** trên **mỗi EC2 instance** của các capacity provider được gắn, và quản lý vòng đời của daemon.

**💡 Giải thích thêm**

```
Daemon task definition  (file "Daemon task definitions")  = bản thiết kế
        │
        ▼
Daemon                  (trang này)                       = bản triển khai: gắn vào cluster + capacity provider
        │
        ▼
Daemon task             = 1 bản chạy trên mỗi instance
```

Quan hệ giống "task definition → service → task" của ứng dụng thường.

---

## 1. Điều kiện tiên quyết

**📘 Theo docs AWS**

- Một **ECS cluster**.
- **Một hoặc nhiều** capacity provider **Managed Instances** đã gắn với cluster.
- Một **daemon task definition** đã đăng ký.

---

## 2. Tạo daemon

### 2.1 Bằng AWS Management Console

**📘 Theo docs AWS**

1. Mở console ECS → **Clusters** → chọn cluster.
2. Tab **Daemons** → **Create**.
3. **Daemon task definition family**: chọn từ danh sách.
4. **Daemon task definition revision**: chọn revision; **để trống** thì dùng revision **mới nhất**.
5. **Daemon name**: tên duy nhất, tối đa **255** ký tự (chữ số, gạch nối, gạch dưới).
6. **Capacity providers**: chọn một hoặc nhiều capacity provider Managed Instances — quyết định **instance nào** chạy daemon.
7. (Tuỳ chọn) Cấu hình deployment:

| Thiết lập | Ý nghĩa | Mặc định |
|---|---|---|
| **Drain percentage** | Tỉ lệ % instance được drain **cùng lúc** khi cập nhật | **25** |
| **Use CloudWatch alarm(s)** | Bật để theo dõi sức khoẻ deployment và **tự rollback** khi alarm kích hoạt | — |
| **Bake time** | Số **phút** ECS chờ **sau khi** đã cập nhật mọi instance sang revision mới, trước khi hoàn tất deployment. Trong thời gian này ECS theo dõi CloudWatch alarm và tự rollback nếu alarm kích hoạt | **0** |

8. (Tuỳ chọn) **Critical**: daemon có quan trọng với sức khoẻ instance hay không.
9. (Tuỳ chọn) Thêm tag.
10. (Tuỳ chọn) Bật **Enable ECS Exec** để chạy lệnh tương tác trong container daemon khi xử lý sự cố.
11. Xem lại → **Create**.

### 2.2 Bằng AWS CLI

**📘 Theo docs AWS**

Tạo file JSON cấu hình daemon rồi chạy **`create-daemon`**.

```json
{
  "clusterArn": "arn:aws:ecs:<REGION>:<ACCOUNT_ID>:cluster/<CLUSTER>",
  "daemonName": "<DAEMON_NAME>",
  "daemonTaskDefinitionArn": "arn:aws:ecs:<REGION>:<ACCOUNT_ID>:daemon-task-definition/<FAMILY>:<REVISION>",
  "capacityProviderArns": [
    "arn:aws:ecs:<REGION>:<ACCOUNT_ID>:capacity-provider/<CAPACITY_PROVIDER>"
  ],
  "critical": true
}
```

```bash
aws ecs create-daemon --cli-input-json file://create-daemon.json
```

| Trường | Bắt buộc | Ý nghĩa |
|---|---|---|
| `daemonName` | **Có** | Tên duy nhất của daemon |
| `clusterArn` | **Có** | ARN của cluster |
| `daemonTaskDefinitionArn` | **Có** | ARN của daemon task definition |
| `capacityProviderArns` | **Có** | Mảng ARN các capacity provider Managed Instances |
| `deploymentConfiguration` | Không | Object **`DaemonDeploymentConfiguration`** tuỳ chỉnh hành vi deploy |
| `critical` | Không | Daemon có quan trọng với sức khoẻ instance không |
| `tags` | Không | Cặp key-value |
| `propagateTags` | Không | Cấu hình lan truyền tag |
| `clientToken` | Không | Token **idempotency** |

**💡 Giải thích thêm**

- Docs không liệt kê tên trường bên trong `DaemonDeploymentConfiguration`; nhiều khả năng chúng tương ứng với ba thiết lập trên console (drain percentage, alarm, bake time) — suy luận của mình, nên tra API reference trước khi dùng.
- `critical` bỏ trống thì mặc định là `true` (theo file *Managed Daemons* và ghi chú ở mục 4).
- `clientToken` giúp gọi lại `create-daemon` an toàn (vd retry trong pipeline) mà không tạo trùng daemon — giải thích chung về idempotency, docs không đi sâu.

---

## 3. Kiểm tra daemon đã triển khai

**📘 Theo docs AWS**

**Console:**
1. Mở console ECS → **Clusters** → chọn cluster.
2. Tab **Daemons**.
3. Kiểm tra daemon có trạng thái **Active**.
4. Tab **Tasks**: xác nhận **mỗi container instance** có **một** daemon task đang chạy.

**CLI:**

```bash
aws ecs list-daemons --cluster-arn <CLUSTER_ARN>

aws ecs describe-daemons --daemon-arn <DAEMON_ARN>
```

**💡 Giải thích thêm**

Daemon ARN có dạng `arn:aws:ecs:<REGION>:<ACCOUNT_ID>:daemon/<CLUSTER>/<DAEMON_NAME>` (theo ví dụ trên trang). Điều cần kiểm tra: **số daemon task = số container instance** của các capacity provider được gắn.

---

## 4. Cập nhật daemon

**📘 Theo docs AWS**

- Cập nhật daemon sang **task definition revision mới** hoặc **thêm capacity provider** → kích hoạt **rolling deployment** trên **mọi instance**.

**Console:**
1. Mở console ECS → **Clusters** → chọn cluster.
2. Tab **Daemons** → chọn daemon.
3. **Update**.
4. **Daemon task definition revision**: chọn revision mới.
5. (Tuỳ chọn) Sửa capacity provider hoặc thiết lập deployment.
6. **Update** để bắt đầu rolling deployment.

**CLI** — lệnh **`update-daemon`**. Ví dụ docs cập nhật sang revision `2`, chỉ định capacity provider và đặt daemon thành non-critical:

```bash
aws ecs update-daemon \
  --daemon-arn <DAEMON_ARN> \
  --daemon-task-definition-arn <DAEMON_TASK_DEFINITION_ARN_REVISION_MỚI> \
  --capacity-provider-arns <CAPACITY_PROVIDER_ARN> \
  --no-critical
```

> **Important (docs):**
> - Khi `UpdateDaemon` có các thiết lập cấu hình, ECS dùng **đúng** những gì bạn truyền thay cho mặc định.
> - Thiết lập cấu hình daemon — **kể cả tag** và cờ **bật execute command** — **không được giữ lại** giữa các lần cập nhật.
> - Mỗi lần gọi `UpdateDaemon` phải gửi **đầy đủ** bộ thiết lập muốn áp dụng; thiết lập nào **bỏ sót** sẽ **về mặc định**.
> - `critical` **mặc định là `true`** → bỏ sót trong `UpdateDaemon` thì daemon **trở thành critical**.

**💡 Giải thích thêm**

`UpdateDaemon` hoạt động kiểu **ghi đè toàn bộ** — giống `UpdateGateway` (file *Create and manage an Amazon ECS Gateway*). Lỗi dễ mắc:

| Bỏ sót khi update | Hậu quả |
|---|---|
| `--no-critical` (với daemon đang non-critical) | Daemon thành **critical** → daemon lỗi sẽ làm drain/thay instance |
| Tag | Mất tag |
| Bật ECS Exec | ECS Exec bị tắt |
| Deployment configuration | Về mặc định (vd drain 25%, bake time 0) |

Quy trình an toàn (gợi ý, theo tinh thần ghi chú Important):

```
describe-daemons  → lấy cấu hình hiện tại
     ↓
update-daemon     → truyền revision mới + giữ nguyên MỌI thiết lập còn lại
                    (capacity provider, critical, tags, exec, deployment config)
     ↓
describe-daemons  → theo dõi rolling deployment
```

Rolling deployment drain instance → task của service cũng bị dời sang instance mới (file *Managed Daemons*). Drain percentage càng lớn thì cập nhật càng nhanh nhưng càng nhiều task bị dời cùng lúc.

---

## 5. Xoá daemon

**📘 Theo docs AWS**

- Xoá daemon bằng console hoặc CLI. **Chờ mọi daemon task dừng** rồi mới xoá **capacity provider** hoặc **cluster**.

**Console:**
1. Mở console ECS → **Clusters** → chọn cluster.
2. Tab **Daemons** → chọn daemon.
3. **Delete**.
4. Hộp xác nhận → **Delete**.

**CLI** — lệnh **`delete-daemon`**:

```bash
aws ecs delete-daemon --daemon-arn <DAEMON_ARN>
```

**💡 Giải thích thêm**

Thứ tự dọn dẹp:

```
delete-daemon → chờ mọi daemon task dừng (kiểm tra tab Tasks / describe-daemons)
             → xoá capacity provider → xoá cluster
```

Với daemon **critical**, cân nhắc ảnh hưởng tới instance trước khi xoá — trang này không mô tả chi tiết điều gì xảy ra với instance khi xoá daemon critical, nên kiểm tra trên môi trường thử trước.

---

## 6. Vòng đời daemon tóm gọn

**💡 Giải thích thêm**

| Thao tác | Console | CLI |
|---|---|---|
| Tạo | Cluster → **Daemons** → **Create** | `create-daemon` |
| Kiểm tra | Cluster → **Daemons** (Active), **Tasks** (1 task/instance) | `list-daemons`, `describe-daemons` |
| Cập nhật | Cluster → **Daemons** → chọn → **Update** | `update-daemon` (gửi đủ mọi thiết lập) |
| Xoá | Cluster → **Daemons** → chọn → **Delete** | `delete-daemon` |

Cùng với file *Daemon task definitions*: `register-daemon-task-definition` → `create-daemon` → `update-daemon` → `delete-daemon`.

---

## 7. Tóm tắt

- **Daemon** = triển khai daemon task definition lên capacity provider Managed Instances; ECS đặt **một daemon task mỗi instance**.
- **Tiên quyết**: cluster, capacity provider Managed Instances đã gắn, daemon task definition đã đăng ký.
- **Tạo** (console hoặc `create-daemon`): family + revision (trống = mới nhất), tên ≤ 255 ký tự, capacity provider; tuỳ chọn drain percentage (mặc định 25), CloudWatch alarm, bake time (mặc định 0 phút), critical, tag, ECS Exec. CLI bắt buộc `daemonName`, `clusterArn`, `daemonTaskDefinitionArn`, `capacityProviderArns`.
- **Kiểm tra**: daemon **Active**, mỗi instance một daemon task; `list-daemons`, `describe-daemons`.
- **Cập nhật** (đổi revision hoặc thêm capacity provider) → rolling deployment. `UpdateDaemon` **không giữ** thiết lập cũ: phải gửi đủ; bỏ sót thì về mặc định; `critical` mặc định `true`.
- **Xoá** (console hoặc `delete-daemon`): chờ mọi daemon task dừng rồi mới xoá capacity provider hay cluster.

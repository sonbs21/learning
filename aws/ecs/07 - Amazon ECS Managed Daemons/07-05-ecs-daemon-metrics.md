# Daemon metrics

> Nguồn: **Amazon ECS Developer Guide — Daemon metrics**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/managed-daemons-metrics.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: diễn đạt lại bằng tiếng Việt, bám sát từng ý và đúng thứ tự của trang gốc, không thêm thông tin ngoài trang. Đây không phải bản dịch từng chữ; khi cần câu chữ chính xác, đọc link gốc ở trên.
> - Khối **💡 Giải thích thêm**: phần giải thích để dễ hiểu hơn, chỉ làm rõ những gì docs đã nói.
> - Lệnh CLI dùng placeholder (`<...>`); namespace, tên metric, dimension giữ đúng như docs.

---

## Mục lục

1. [Tổng quan](#1-tổng-quan)
2. [Metric sử dụng CPU và memory của daemon](#2-metric-sử-dụng-cpu-và-memory-của-daemon)
   - [2.1 Các statistic](#21-các-statistic)
   - [2.2 Công thức tính](#22-công-thức-tính)
   - [2.3 Cách thu thập số liệu](#23-cách-thu-thập-số-liệu)
   - [2.4 Ví dụ tính](#24-ví-dụ-tính)
3. [Xem daemon metric](#3-xem-daemon-metric)
4. [Dùng metric trong thực tế](#4-dùng-metric-trong-thực-tế)
5. [Tóm tắt](#5-tóm-tắt)

---

## 1. Tổng quan

**📘 Theo docs AWS**

- ECS đẩy **CloudWatch metric** cho Managed Daemons, dùng để theo dõi mức **sử dụng CPU và memory** của daemon task trên toàn cluster.
- Daemon metric dùng **cùng namespace `AWS/ECS`** với metric của service.
- ECS phân biệt daemon metric với service metric qua giá trị dimension **`ServiceName`**: có tiền tố **`daemon:`** theo sau là **tên daemon** (ví dụ `daemon:my-daemon`).

**💡 Giải thích thêm**

| | Service metric | Daemon metric |
|---|---|---|
| Namespace | `AWS/ECS` | `AWS/ECS` |
| Dimension `ClusterName` | tên cluster | tên cluster |
| Dimension `ServiceName` | `<tên service>` | **`daemon:<tên daemon>`** |

Nhờ vậy dashboard/alarm có sẵn cho service có thể dùng lại cho daemon, chỉ đổi giá trị `ServiceName`.

---

## 2. Metric sử dụng CPU và memory của daemon

**📘 Theo docs AWS**

ECS đo mức sử dụng CPU và memory của daemon dưới dạng **phần trăm** tài nguyên daemon task **thực dùng** so với tài nguyên **khai báo trong daemon task definition**.

### 2.1 Các statistic

**📘 Theo docs AWS**

| Statistic | Ý nghĩa |
|---|---|
| **Average** | Mức sử dụng **trung bình** trên mọi daemon task trong cluster |
| **Minimum** | Mức sử dụng của daemon task **dùng ít nhất** |
| **Maximum** | Mức sử dụng của daemon task **dùng nhiều nhất** |

### 2.2 Công thức tính

**📘 Theo docs AWS**

Công thức áp dụng cho statistic **Average**:

```
Daemon CPU utilization (%)    = (Tổng CPU unit các daemon task dùng × 100)
                                ÷ (CPU unit khai báo trong daemon task definition × Số daemon task)

Daemon memory utilization (%) = (Tổng MiB memory các daemon task dùng × 100)
                                ÷ (MiB memory khai báo trong daemon task definition × Số daemon task)
```

- Công thức **chỉ áp dụng cho Average**.
- **Minimum** và **Maximum** là mức sử dụng của **từng daemon task riêng lẻ** thấp nhất và cao nhất.

### 2.3 Cách thu thập số liệu

**📘 Theo docs AWS**

- ECS container agent **lấy mẫu** CPU và memory của mỗi daemon task đang chạy **mỗi 20 giây**.
- **Mỗi phút**, agent báo **3 mẫu** cho ECS.
- ECS **tổng hợp** mẫu của mọi daemon task trong cluster, rồi đẩy **Average**, **Minimum**, **Maximum** lên CloudWatch dưới dạng **% của tổng tài nguyên khai báo** trong daemon task definition.

### 2.4 Ví dụ tính

**📘 Theo docs AWS**

Daemon task definition khai báo **256 CPU unit** và **512 MiB** memory. Daemon có **2 task** trên **2 instance**:

| Daemon task | CPU dùng | Memory dùng |
|---|---|---|
| Task 1 | 200 | 400 MiB |
| Task 2 | 100 | 256 MiB |

ECS báo:
- **Average CPU** = ((200 + 100) × 100) ÷ (256 × 2) = **58,6%**
- **Average memory** = ((400 + 256) × 100) ÷ (512 × 2) = **64,1%**

**💡 Giải thích thêm**

Tính thêm Minimum/Maximum cho cùng ví dụ (do mình tính, theo định nghĩa ở 2.1):

| | Task 1 | Task 2 | Minimum | Maximum |
|---|---|---|---|---|
| CPU | 200 / 256 = 78,1% | 100 / 256 = 39,1% | **39,1%** | **78,1%** |
| Memory | 400 / 512 = 78,1% | 256 / 512 = 50,0% | **50,0%** | **78,1%** |

Average (58,6%) trông "an toàn", nhưng **Maximum** cho thấy một instance đang dùng 78% — vì daemon chạy **mỗi instance một bản**, instance bận (nhiều task app, nhiều log) sẽ đẩy daemon ở đó lên cao. Nên xem **Maximum** khi đánh giá rủi ro thiếu tài nguyên.

Vì là phần trăm so với **khai báo**, giá trị có thể **vượt 100%** nếu daemon dùng nhiều hơn mức khai báo (vd CPU được burst khi host còn trống — file *Best practices for task sizes*). Đây là suy luận; trang không nói giới hạn trên.

---

## 3. Xem daemon metric

**📘 Theo docs AWS**

**Console CloudWatch:**
1. Mở console CloudWatch.
2. Thanh điều hướng → **Metrics** → **All metrics**.
3. Chọn namespace **AWS/ECS**.
4. Chọn dimension **ClusterName, ServiceName**.
5. Lọc các giá trị `ServiceName` **bắt đầu bằng `daemon:`**.

**AWS CLI** — lệnh **`get-metric-statistics`**, chỉ định dimension `ServiceName` có tiền tố `daemon:`. Docs đưa hai ví dụ lấy **Average** trong **12 giờ**, chu kỳ **5 phút** (`--period 300`), cho metric **`CPUUtilization`** và **`MemoryUtilization`**:

```bash
aws cloudwatch get-metric-statistics \
  --namespace AWS/ECS \
  --metric-name CPUUtilization \
  --dimensions Name=ClusterName,Value=<CLUSTER> Name=ServiceName,Value=daemon:<DAEMON_NAME> \
  --start-time <START_ISO8601> \
  --end-time <END_ISO8601> \
  --period 300 \
  --statistics Average
```

Đổi `--metric-name` thành `MemoryUtilization` để lấy memory.

**💡 Giải thích thêm**

Lấy cả ba statistic một lần: `--statistics Average Minimum Maximum`.

---

## 4. Dùng metric trong thực tế

**💡 Giải thích thêm**

Phần này là gợi ý của mình, ghép với các file trước:

| Mục đích | Metric / statistic | Gợi ý |
|---|---|---|
| Daemon sắp thiếu memory | `MemoryUtilization` **Maximum** | Alarm khi gần 100% → tăng `memory` trong daemon task definition (file *Daemon task definitions*) |
| Daemon khai báo dư | `CPUUtilization` / `MemoryUtilization` **Maximum** thấp kéo dài | Giảm `cpu`/`memory` → để lại nhiều tài nguyên hơn cho task app |
| Kiểm tra revision mới | So metric trước/sau `update-daemon` | Dùng làm **CloudWatch alarm** cho `alarms` + `bakeTimeInMinutes` trong deployment (file *Daemon deployments*) |
| Phân biệt daemon lỗi vs tài nguyên | Kết hợp với `TASK_CREATION_FAILED` (`RESOURCE:CPU`) | File *Daemon events* |

Ví dụ alarm Terraform (tên resource `aws_cloudwatch_metric_alarm` là bổ sung của mình):

```hcl
resource "aws_cloudwatch_metric_alarm" "daemon_memory_high" {
  alarm_name          = "daemon-<DAEMON_NAME>-memory-high"
  namespace           = "AWS/ECS"
  metric_name         = "MemoryUtilization"
  statistic           = "Maximum"
  period              = 300
  evaluation_periods  = 2
  threshold           = 90
  comparison_operator = "GreaterThanThreshold"
  dimensions = {
    ClusterName = "<CLUSTER>"
    ServiceName = "daemon:<DAEMON_NAME>"
  }
}
```

---

## 5. Tóm tắt

- Daemon metric nằm trong namespace **`AWS/ECS`**, phân biệt bằng `ServiceName` = **`daemon:<tên daemon>`** (cùng dimension `ClusterName`).
- Metric: **`CPUUtilization`**, **`MemoryUtilization`** — % tài nguyên dùng so với khai báo trong daemon task definition.
- Statistic: **Average** (theo công thức tổng dùng ÷ (khai báo × số task)), **Minimum** và **Maximum** (task riêng lẻ thấp nhất / cao nhất).
- Agent lấy mẫu mỗi **20 giây**, báo **3 mẫu mỗi phút**; ECS tổng hợp trên cả cluster.
- Ví dụ docs: 256 CPU / 512 MiB, 2 task → Average CPU 58,6%, memory 64,1%.
- Xem bằng console CloudWatch (AWS/ECS → ClusterName, ServiceName → lọc `daemon:`) hoặc `aws cloudwatch get-metric-statistics`.

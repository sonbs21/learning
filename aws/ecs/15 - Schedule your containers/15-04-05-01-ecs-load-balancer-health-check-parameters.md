# Optimize load balancer health check parameters for Amazon ECS

> Nguồn: **Amazon ECS Developer Guide — Optimize load balancer health check parameters for Amazon ECS**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/load-balancer-healthcheck.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: **bản dịch** sang tiếng Việt, đi đúng thứ tự và đúng nội dung của trang gốc, không thêm, không bớt ý.
> - Khối **💡 Giải thích thêm**: chỉ giải thích lại những gì **chính trang này** đã nói, cho dễ hiểu hơn. Không đưa thông tin từ trang khác hay ví dụ tự đặt.
> - Tên tham số API và tên trên console giữ nguyên tiếng Anh.
> - Tài liệu AWS được phát hành theo giấy phép **Creative Commons Attribution-ShareAlike 4.0**; bản dịch này ghi rõ nguồn ở trên.

---

## Mục lục

0. [Phần mở đầu: load balancer kiểm tra sức khoẻ thế nào](#0-phần-mở-đầu-load-balancer-kiểm-tra-sức-khoẻ-thế-nào)
1. [Hai tham số ảnh hưởng tốc độ deploy](#1-hai-tham-số-ảnh-hưởng-tốc-độ-deploy)
2. [Tăng tốc health check](#2-tăng-tốc-health-check)

---

## 0. Phần mở đầu: load balancer kiểm tra sức khoẻ thế nào

**📘 Theo docs AWS**

Load balancer chỉ định tuyến request tới các target **healthy** trong các Availability Zone của load balancer. Mỗi target được đăng ký vào một **target group**. Load balancer kiểm tra sức khoẻ từng target bằng **cài đặt health check của target group**. Sau khi bạn đăng ký target, nó phải vượt qua **một** health check để được coi là healthy. Amazon ECS giám sát load balancer. Load balancer định kỳ gửi health check tới container Amazon ECS. **Amazon ECS agent** giám sát, và **chờ** load balancer báo cáo về sức khoẻ container. Agent làm vậy **trước khi** coi container ở trạng thái healthy.

**💡 Giải thích thêm**

```
Target (container) đăng ký vào target group
   └─ load balancer gửi health check định kỳ (theo cài đặt của target group)
         └─ ECS agent chờ load balancer báo kết quả → mới coi container là healthy
Load balancer chỉ gửi request tới target healthy
```

---

## 1. Hai tham số ảnh hưởng tốc độ deploy

**📘 Theo docs AWS**

Hai tham số health check của Elastic Load Balancing ảnh hưởng tới **tốc độ deploy**:

- **Health check interval**: Xác định khoảng thời gian gần đúng, tính bằng giây, **giữa các lần health check** của một container. Mặc định, load balancer kiểm tra **mỗi 30 giây**.

  Tham số này có tên:
  - `HealthCheckIntervalSeconds` trong Elastic Load Balancing API
  - **Interval** trên console Amazon EC2

- **Healthy threshold count**: Xác định **số lần health check thành công liên tiếp** cần có trước khi coi một container **unhealthy** là **healthy**. Mặc định, load balancer cần **năm** lần health check đạt trước khi báo target container là healthy.

  Tham số này có tên:
  - `HealthyThresholdCount` trong Elastic Load Balancing API
  - **Healthy threshold** trên console Amazon EC2

**Important:** Với target **mới đăng ký**, chỉ cần **một** lần health check thành công là target được coi là healthy, **bất kể** cài đặt healthy threshold count. Healthy threshold count **chỉ áp dụng** khi target chuyển từ trạng thái **unhealthy trở lại healthy**.

Với cài đặt mặc định, nếu một target trở nên unhealthy rồi hồi phục, tổng thời gian để xác định sức khoẻ của container là **hai phút 30 giây** (`30 seconds * 5 = 150 seconds`).

**💡 Giải thích thêm**

| Tham số | Tên trong ELB API | Tên trên console EC2 | Mặc định |
|---|---|---|---|
| Health check interval | `HealthCheckIntervalSeconds` | **Interval** | 30 giây |
| Healthy threshold count | `HealthyThresholdCount` | **Healthy threshold** | 5 lần |

Healthy threshold count áp dụng khi nào (theo khung Important):

| Trường hợp | Số lần health check thành công cần |
|---|---|
| Target **mới đăng ký** | **1** (bỏ qua healthy threshold count) |
| Target **unhealthy → healthy** | **Healthy threshold count** (mặc định 5) |

Phép tính thời gian hồi phục với mặc định:

```
interval × healthy threshold = 30 giây × 5 = 150 giây = 2 phút 30 giây
```

---

## 2. Tăng tốc health check

**📘 Theo docs AWS**

Bạn có thể tăng tốc quá trình health check nếu service **khởi động và ổn định trong dưới 10 giây**. Để tăng tốc, **giảm** health check interval và healthy threshold count.

- `HealthCheckIntervalSeconds` (tên trong Elastic Load Balancing API) hoặc **Interval** (tên trên console Amazon EC2): **5**
- `HealthyThresholdCount` (tên trong Elastic Load Balancing API) hoặc **Healthy threshold** (tên trên console Amazon EC2): **2**

Với cài đặt này, quá trình health check mất **10 giây** so với mặc định **hai phút 30 giây**.

Thông tin thêm về các tham số health check của Elastic Load Balancing, xem *Health checks for your target groups* (<https://docs.aws.amazon.com/elasticloadbalancing/latest/application/target-group-health-checks.html>) trong *Elastic Load Balancing User Guide*.

**💡 Giải thích thêm**

| | Mặc định | Cài đặt tăng tốc theo trang |
|---|---|---|
| `HealthCheckIntervalSeconds` | 30 | **5** |
| `HealthyThresholdCount` | 5 | **2** |
| Thời gian (interval × threshold) | 150 giây | **10 giây** |
| Điều kiện | — | Service khởi động và ổn định **< 10 giây** |

Theo khung Important ở mục 1, công thức `interval × threshold` (150 giây hay 10 giây) tương ứng trường hợp target **hồi phục từ unhealthy**. Với target **mới đăng ký**, chỉ cần một lần health check thành công, nên thời gian chủ yếu phụ thuộc **interval**.

# Amazon ECS Service Connect zone-aware routing

> Nguồn: **Amazon ECS Developer Guide — Amazon ECS Service Connect zone-aware routing**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-connect-zone-aware-routing.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: **bản dịch** sang tiếng Việt, đi đúng thứ tự và đúng nội dung của trang gốc, không thêm, không bớt ý. Lệnh ví dụ giữ nguyên như trên docs.
> - Khối **💡 Giải thích thêm**: chỉ giải thích lại những gì **chính trang này** đã nói, cho dễ hiểu hơn. Không đưa thông tin từ trang khác hay ví dụ tự đặt.
> - Tên metric, lệnh, tham số giữ nguyên tiếng Anh.
> - Tài liệu AWS được phát hành theo giấy phép **Creative Commons Attribution-ShareAlike 4.0**; bản dịch này ghi rõ nguồn ở trên.

---

## Mục lục

0. [Phần mở đầu](#0-phần-mở-đầu)
1. [Cách zone-aware routing hoạt động](#1-cách-zone-aware-routing-hoạt-động)
2. [Kiểm tra zone-aware routing trên Amazon EC2](#2-kiểm-tra-zone-aware-routing-trên-amazon-ec2)
3. [Giám sát zone-aware routing](#3-giám-sát-zone-aware-routing)
4. [Những điều cần cân nhắc](#4-những-điều-cần-cân-nhắc)

---

## 0. Phần mở đầu

**📘 Theo docs AWS**

Với **zone-aware routing** của Amazon ECS Service Connect, traffic ở lại trong **cùng Availability Zone (AZ)** với client. Điều này giảm **chi phí truyền dữ liệu giữa các AZ** và **độ trễ**, mà **không cần thêm hạ tầng** hay **sửa code ứng dụng**. Khi endpoint được cân bằng giữa các AZ, **hơn 80%** traffic ở lại cục bộ, trong khi trọng số traffic tự điều chỉnh khi endpoint scale để giữ tải cân bằng giữa các service đích. Zone-aware routing **bật mặc định** cho mọi service mới và hiện có dùng Service Connect.

**💡 Giải thích thêm**

| Lợi ích theo trang | Điều kiện |
|---|---|
| Giảm chi phí truyền dữ liệu giữa AZ | — |
| Giảm độ trễ | — |
| > 80% traffic ở lại cùng AZ | Endpoint được cân bằng giữa các AZ |
| Không cần thêm hạ tầng / sửa code | — |

---

## 1. Cách zone-aware routing hoạt động

**📘 Theo docs AWS**

Thuật toán dùng tính năng **zone-aware routing của Envoy** trong Service Connect proxy để định tuyến traffic dựa trên **vị trí** của endpoint:

1. **Phát hiện endpoint** ‐ Proxy phát hiện mọi endpoint khả dụng của service đích và vị trí AZ của chúng.
2. **Ưu tiên cùng AZ** ‐ Proxy tính phần trăm traffic ở lại cùng AZ dựa trên **tỉ lệ phân bố endpoint** giữa AZ nguồn (client) và AZ đích (server). Khi endpoint trải đều giữa các zone, traffic cùng AZ thường **vượt 80%**.
3. **Định tuyến theo capacity còn dư** ‐ Traffic không ở lại được cùng AZ được phân bổ sang các AZ khác dựa trên **capacity còn dư** của chúng. Proxy tính capacity còn dư của từng zone và chia traffic khác AZ **theo tỉ lệ** giữa các zone có capacity còn dư **dương**. Khi endpoint scale lên hoặc xuống, proxy **tính lại** trọng số định tuyến theo thời gian thực.
4. **Bảo vệ quá tải và dự phòng** ‐ Để tránh làm quá tải một AZ, proxy yêu cầu số endpoint khả dụng của service đích ít nhất bằng **2 × số AZ**. Với Region có 3 AZ, nghĩa là ít nhất **6 endpoint**. Dưới ngưỡng này, zone-aware routing **tắt** và traffic được phân bổ đều bằng cân bằng tải **round-robin**. Định tuyến **tự bật lại** khi số endpoint vượt ngưỡng. Nếu endpoint ở cùng AZ không khả dụng, traffic được phân bổ lại sang các AZ khác để giữ tính sẵn sàng.

**💡 Giải thích thêm**

```
1. Proxy biết mọi endpoint đích và AZ của chúng
2. Tính % traffic giữ trong cùng AZ (theo tỉ lệ endpoint client/server mỗi AZ)
3. Phần còn lại → chia cho AZ khác theo capacity còn dư (> 0); tính lại khi endpoint scale
4. Số endpoint đích < 2 × số AZ → tắt zone-aware, dùng round-robin; đủ lại → tự bật
   Endpoint cùng AZ hỏng → chuyển sang AZ khác
```

Ngưỡng tối thiểu theo công thức của trang:

| Số AZ | Endpoint đích tối thiểu (2 × số AZ) |
|---|---|
| 2 | 4 |
| 3 | 6 (ví dụ của trang) |

---

## 2. Kiểm tra zone-aware routing trên Amazon EC2

**📘 Theo docs AWS**

Trên container instance Amazon EC2 dùng **Docker runtime**, bạn có thể xem thống kê Envoy proxy bên trong **container Service Connect agent** để xác nhận zone-aware routing đang hoạt động. Container instance phải dùng **ECS-optimized AMI phiên bản `20260323` trở lên**. Quy trình này cần quyền truy cập host qua **AWS Systems Manager Session Manager** và `docker exec`. Với **AWS Fargate** hoặc container instance dùng **`containerd`**, hãy dùng **Amazon Virtual Private Cloud Flow Logs** để kiểm tra hành vi định tuyến như mô tả ở *Giám sát zone-aware routing* (mục 3).

**Kiểm tra zone-aware routing trên Amazon EC2 với Docker**

1. Kết nối tới container instance bằng AWS Systems Manager Session Manager. Thay `instance-id` bằng ID container instance của bạn.

   ```
   aws ssm start-session --target instance-id
   ```

2. Mở shell trong container Service Connect agent.

   ```
   sudo docker exec -it $(sudo docker ps --filter "name=ecs-service-connect" -q | head -1) /bin/sh
   ```

3. Truy vấn thống kê định tuyến theo zone từ giao diện admin của Envoy.

   ```
   curl --unix-socket /tmp/envoy_admin.sock http://unix/stats | grep "lb_zone"
   ```

4. Kiểm tra output. Một deployment khoẻ mạnh với zone-aware routing đang hoạt động cho thấy **không có** request khác zone:

   ```
   cluster.my-service.lb_zone_routing_cross_zone: 0
   cluster.my-service.lb_zone_cluster_too_small: 0
   ```

Bảng sau mô tả các metric định tuyến theo zone chính.

| Metric | Mô tả |
|---|---|
| `lb_zone_routing_cross_zone` | Số request được định tuyến tới endpoint ở **AZ khác**. Giá trị **0 kéo dài** xác nhận mọi traffic ở lại cùng AZ. |
| `lb_zone_cluster_too_small` | Số lần zone-aware routing bị **bỏ qua** vì số endpoint dưới ngưỡng tối thiểu. Giá trị khác 0 trong lúc deploy ban đầu là **bình thường** và sẽ hết khi endpoint healthy. Deployment ổn định cho thấy **0**. |

**💡 Giải thích thêm**

| Hạ tầng | Cách kiểm tra theo trang |
|---|---|
| EC2 + Docker (AMI ≥ `20260323`) | Session Manager → `docker exec` vào container Service Connect agent → `curl` Envoy admin stats |
| Fargate, hoặc EC2 dùng `containerd` | VPC Flow Logs (mục 3) |

Đọc kết quả:

| Metric | Giá trị | Nghĩa |
|---|---|---|
| `lb_zone_routing_cross_zone` | 0 kéo dài | Mọi traffic ở cùng AZ |
| `lb_zone_cluster_too_small` | > 0 lúc mới deploy | Bình thường — chưa đủ endpoint healthy (dưới ngưỡng 2 × số AZ) |
| `lb_zone_cluster_too_small` | 0 | Deployment ổn định |

---

## 3. Giám sát zone-aware routing

**📘 Theo docs AWS**

Dùng **Amazon Virtual Private Cloud Flow Logs** với trường **`az-id`** để quan sát mẫu traffic ở **mức mạng**. Trường này cho biết mỗi flow record bắt đầu và kết thúc ở AZ nào, giúp bạn đo tỉ lệ traffic **cùng AZ** so với **khác AZ**. Bạn cũng có thể dùng **AWS Cost Explorer** để theo dõi chi phí truyền dữ liệu giữa các AZ **trước và sau** khi deploy lại service để bật zone-aware routing.

Thông tin thêm về cấu hình flow log, xem *VPC Flow Logs* (<https://docs.aws.amazon.com/vpc/latest/userguide/flow-logs.html>) trong *Amazon Virtual Private Cloud User Guide*.

**💡 Giải thích thêm**

| Công cụ | Đo gì |
|---|---|
| VPC Flow Logs (trường `az-id`) | Tỉ lệ traffic cùng AZ / khác AZ ở mức mạng |
| AWS Cost Explorer | Chi phí truyền dữ liệu giữa AZ trước/sau khi deploy lại |

---

## 4. Những điều cần cân nhắc

**📘 Theo docs AWS**

Lưu ý các điểm sau khi dùng zone-aware routing:

- Zone-aware routing hoạt động với **mọi launch type**.
- Zone-aware routing tương thích với Service Connect namespace **chéo account** được chia sẻ qua AWS Resource Access Manager.
- Service **hiện có** (cả client lẫn server) cần **deploy lại một lần** để kích hoạt zone-aware routing. Sau lần deploy lại đầu tiên, định tuyến tự điều chỉnh khi endpoint thay đổi mà không cần deploy lại nữa.
- Với launch type Amazon EC2, container instance phải dùng Amazon ECS-optimized AMI phiên bản **`20260323` trở lên**.

**💡 Giải thích thêm**

| Điểm | Theo trang |
|---|---|
| Launch type | Mọi launch type |
| Namespace chia sẻ chéo account | Tương thích |
| Service hiện có | Deploy lại **một lần** (cả client và server) |
| EC2 | ECS-optimized AMI ≥ `20260323` |

Đoạn mở đầu nói zone-aware routing "bật mặc định cho mọi service mới và hiện có", còn mục này nói service hiện có cần **deploy lại một lần** để kích hoạt. Đọc cùng nhau: service hiện có được bật mặc định nhưng chỉ **có hiệu lực** sau một lần deploy lại cả phía client lẫn server.

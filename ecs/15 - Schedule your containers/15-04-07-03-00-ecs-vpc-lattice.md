# Use Amazon VPC Lattice to connect, observe, and secure your Amazon ECS services

> Nguồn: **Amazon ECS Developer Guide — Use Amazon VPC Lattice to connect, observe, and secure your Amazon ECS services**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/ecs-vpc-lattice.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: **bản dịch** sang tiếng Việt, đi đúng thứ tự và đúng nội dung của trang gốc, không thêm, không bớt ý.
> - Khối **💡 Giải thích thêm**: chỉ giải thích lại những gì **chính trang này** đã nói, cho dễ hiểu hơn. Không đưa thông tin từ trang khác hay ví dụ tự đặt.
> - Tên dịch vụ, tính năng, deployment controller giữ nguyên tiếng Anh.
> - Tài liệu AWS được phát hành theo giấy phép **Creative Commons Attribution-ShareAlike 4.0**; bản dịch này ghi rõ nguồn ở trên.

---

## Mục lục

0. [Phần mở đầu](#0-phần-mở-đầu)
1. [Lợi ích cho khách hàng Amazon ECS](#1-lợi-ích-cho-khách-hàng-amazon-ecs)
2. [VPC Lattice hoạt động thế nào với các dịch vụ Amazon ECS khác](#2-vpc-lattice-hoạt-động-thế-nào-với-các-dịch-vụ-amazon-ecs-khác)

---

## 0. Phần mở đầu

**📘 Theo docs AWS**

**Amazon VPC Lattice** là dịch vụ mạng ứng dụng được quản lý hoàn toàn, cho phép khách hàng Amazon ECS **quan sát, bảo mật và giám sát** ứng dụng xây dựng trên các dịch vụ compute của AWS, VPC và account — **không cần sửa code**.

VPC Lattice dùng **target group**, là tập hợp các tài nguyên compute. Các target này chạy ứng dụng hoặc service của bạn và có thể là Amazon EC2 instance, địa chỉ IP, Lambda function, và Application Load Balancer. Bằng cách gắn Amazon ECS service với một target group VPC Lattice, khách hàng có thể bật task Amazon ECS làm **IP target** trong VPC Lattice. Amazon ECS **tự động đăng ký** task vào target group VPC Lattice khi task của service đã đăng ký được khởi chạy.

> **Note:** Khi dùng **năm** cấu hình VPC Lattice, thời gian deploy có thể **lâu hơn một chút** so với khi dùng ít cấu hình hơn.

**Listener rule** được dùng để chuyển traffic tới một target group chỉ định khi điều kiện được thoả. **Listener** kiểm tra các yêu cầu kết nối bằng giao thức trên port bạn đã cấu hình. Một service định tuyến request tới các target đã đăng ký dựa trên các quy tắc bạn định nghĩa khi cấu hình listener.

Amazon ECS cũng **tự động thay task** nếu task trở nên **unhealthy** theo **health check của VPC Lattice**. Khi đã gắn với VPC Lattice, khách hàng Amazon ECS cũng tận dụng được nhiều tính năng kết nối, bảo mật và quan sát xuyên compute khác của VPC Lattice, như kết nối tới service **giữa các cluster, VPC và account** bằng AWS Resource Access Manager, **tích hợp IAM** cho phân quyền và xác thực, và các tính năng **quản lý traffic nâng cao**.

**💡 Giải thích thêm**

```
Client ─► VPC Lattice listener (giao thức + port)
              └─ listener rule (điều kiện) ─► target group ─► task ECS (IP target, ECS tự đăng ký)
                                                   ▲
                    health check của VPC Lattice: task unhealthy → ECS tự thay task
```

| Điểm | Theo đoạn trên |
|---|---|
| Loại target của target group | EC2 instance, địa chỉ IP, Lambda function, Application Load Balancer |
| Task ECS là | **IP target** |
| Đăng ký task | ECS tự đăng ký khi task khởi chạy |
| Task unhealthy (theo VPC Lattice) | ECS tự thay |
| Dùng 5 cấu hình VPC Lattice | Deploy có thể lâu hơn một chút |
| Tính năng thêm | Kết nối chéo cluster/VPC/account (AWS RAM), IAM cho xác thực/phân quyền, quản lý traffic nâng cao |

---

## 1. Lợi ích cho khách hàng Amazon ECS

**📘 Theo docs AWS**

Khách hàng Amazon ECS có thể hưởng lợi từ VPC Lattice theo các cách sau.

- **Tăng năng suất developer** ‐ VPC Lattice tăng năng suất developer bằng cách để bạn tập trung xây dựng tính năng, trong khi VPC Lattice xử lý các thách thức về mạng, bảo mật và quan sát một cách **thống nhất** trên mọi nền tảng compute.

- **Tư thế bảo mật tốt hơn** ‐ VPC Lattice cho phép developer dễ dàng **xác thực** và **bảo mật** giao tiếp giữa các ứng dụng và nền tảng compute, **bắt buộc mã hoá khi truyền**, và áp dụng **kiểm soát truy cập chi tiết** bằng **VPC Lattice Auth policy**. Điều này giúp bạn có tư thế bảo mật mạnh hơn, đáp ứng các yêu cầu quy định và tuân thủ hàng đầu ngành.

- **Cải thiện khả năng scale và chịu lỗi của ứng dụng** ‐ VPC Lattice cho phép bạn tạo một mạng các ứng dụng đã deploy với các tính năng như định tuyến theo **path**, **header** và **method**, xác thực, phân quyền và giám sát. Các lợi ích này được cung cấp **không tốn thêm tài nguyên** trên workload và có thể hỗ trợ deploy **nhiều cluster** tạo ra **hàng triệu request mỗi giây** mà không thêm độ trễ đáng kể.

- **Linh hoạt triển khai với hạ tầng không đồng nhất** ‐ VPC Lattice cung cấp tính năng **nhất quán** trên mọi dịch vụ compute như Amazon ECS, Fargate, Amazon EC2, Amazon EKS và Lambda, và cho phép tổ chức của bạn linh hoạt chọn hạ tầng phù hợp cho từng ứng dụng.

**💡 Giải thích thêm**

| Lợi ích | Điểm chính theo trang |
|---|---|
| Năng suất developer | VPC Lattice lo mạng, bảo mật, quan sát thống nhất; developer tập trung tính năng |
| Bảo mật | Xác thực, mã hoá khi truyền, VPC Lattice Auth policy (kiểm soát truy cập chi tiết) |
| Scale và chịu lỗi | Định tuyến theo path/header/method; không tốn thêm tài nguyên workload; nhiều cluster, hàng triệu req/s |
| Hạ tầng không đồng nhất | Nhất quán trên ECS, Fargate, EC2, EKS, Lambda |

---

## 2. VPC Lattice hoạt động thế nào với các dịch vụ Amazon ECS khác

**📘 Theo docs AWS**

Dùng VPC Lattice với Amazon ECS có thể thay đổi cách bạn dùng một số dịch vụ Amazon ECS khác, còn số khác giữ nguyên.

### 2.1 Application Load Balancer

**📘 Theo docs AWS**

Bạn **không còn cần** tạo một Application Load Balancer riêng để dùng với loại target group Application Load Balancer trong VPC Lattice rồi mới nối tới Amazon ECS service. Bạn chỉ cần cấu hình Amazon ECS service với một **target group VPC Lattice**. Bạn vẫn có thể chọn dùng Application Load Balancer với Amazon ECS **cùng lúc**.

### 2.2 Amazon ECS deployments

**📘 Theo docs AWS**

**Rolling deployment** của Amazon ECS và chiến lược **blue/green deployment** của Amazon ECS (gồm cả cấu hình chuyển traffic **linear** và **canary**) hoạt động với VPC Lattice, và Amazon ECS đưa task vào và gỡ task khỏi service một cách an toàn trong lúc deploy. Tài nguyên VPC Lattice cần cho blue/green, linear và canary deployment, xem *VPC Lattice resources for blue/green, linear, and canary deployments* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/vpc-lattice-resources-for-blue-green.html>). **Blue/green deployment chạy bằng CodeDeploy** (deployment controller **`CODE_DEPLOY`**) **không được hỗ trợ**.

Để tìm hiểu thêm về VPC Lattice, xem *Amazon VPC Lattice User Guide* (<https://docs.aws.amazon.com/vpc-lattice/latest/ug/what-is-vpc-lattice.html>).

> **Note:** VPC Lattice **không được hỗ trợ** trên **Amazon ECS Anywhere**.

**💡 Giải thích thêm**

| Thành phần | Với VPC Lattice (theo trang) |
|---|---|
| Application Load Balancer | Không cần ALB riêng chỉ để làm target group trong VPC Lattice; vẫn dùng ALB song song được |
| Rolling deployment | ✅ Hỗ trợ |
| Blue/green của Amazon ECS (cả linear, canary) | ✅ Hỗ trợ — cần tài nguyên VPC Lattice riêng (xem link) |
| Blue/green bằng CodeDeploy (`CODE_DEPLOY`) | ❌ Không hỗ trợ |
| Amazon ECS Anywhere | ❌ Không hỗ trợ |

```
Trước:  ECS service ─► ALB ─► (target group loại ALB trong VPC Lattice)
Với Lattice: ECS service ─► target group VPC Lattice (task là IP target)
```

Ghi chú nhỏ về trang gốc: câu về listener có lỗi chính tả "it's registered targets" (đúng là "its"); không ảnh hưởng nghĩa.

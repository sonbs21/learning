# AWS services for Amazon ECS networking troubleshooting

> Nguồn: **Amazon ECS Developer Guide — AWS services for Amazon ECS networking troubleshooting**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/networking-troubleshooting.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: **bản dịch** sang tiếng Việt, đi đúng thứ tự và đúng nội dung của trang gốc, không thêm, không bớt ý.
> - Khối **💡 Giải thích thêm**: chỉ giải thích lại những gì **chính trang này** đã nói, cho dễ hiểu hơn. Không đưa thông tin từ trang khác hay ví dụ tự đặt.
> - Tên dịch vụ, tính năng, tên cài đặt giữ nguyên tiếng Anh.
> - Tài liệu AWS được phát hành theo giấy phép **Creative Commons Attribution-ShareAlike 4.0**; bản dịch này ghi rõ nguồn ở trên.

---

## Mục lục

0. [Phần mở đầu](#0-phần-mở-đầu)
1. [CloudWatch Container Insights](#1-cloudwatch-container-insights)
2. [AWS X-Ray](#2-aws-x-ray)
3. [VPC Flow Logs](#3-vpc-flow-logs)
4. [Mẹo tinh chỉnh mạng](#4-mẹo-tinh-chỉnh-mạng)
   - [4.1 nofile ulimit](#41-nofile-ulimit)
   - [4.2 sysctl net](#42-sysctl-net)
5. [Tổng hợp](#5-tổng-hợp)

---

## 0. Phần mở đầu

**📘 Theo docs AWS**

Các dịch vụ và tính năng sau có thể giúp bạn hiểu rõ hơn về cấu hình mạng và service. Bạn có thể dùng thông tin này để xử lý sự cố mạng và tối ưu service tốt hơn.

---

## 1. CloudWatch Container Insights

**📘 Theo docs AWS**

CloudWatch Container Insights thu thập, tổng hợp và tóm tắt metric và log từ các ứng dụng container và microservice của bạn. Metric gồm mức sử dụng tài nguyên như CPU, memory, đĩa và **mạng**. Chúng có sẵn trong các **dashboard tự động** của CloudWatch. Thông tin thêm, xem *Setting up Container Insights on Amazon ECS* (<https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/deploy-container-insights-ECS.html>) trong *Amazon CloudWatch User Guide*.

**💡 Giải thích thêm**

Với xử lý sự cố mạng, phần liên quan trực tiếp là metric **network** — xem được ngay trên dashboard tự động của CloudWatch, cạnh CPU, memory, đĩa.

---

## 2. AWS X-Ray

**📘 Theo docs AWS**

AWS X-Ray là dịch vụ **tracing** mà bạn có thể dùng để thu thập thông tin về các network request mà ứng dụng tạo ra. Bạn có thể dùng SDK để instrument ứng dụng và ghi lại **thời gian** và **mã phản hồi** của traffic giữa các service, và giữa service với endpoint của dịch vụ AWS. Thông tin thêm, xem *What is AWS X-Ray* (<https://docs.aws.amazon.com/xray/latest/devguide/aws-xray.html>) trong *AWS X-Ray Developer Guide*.

Bạn cũng có thể xem các **biểu đồ** AWS X-Ray về cách các service kết nối mạng với nhau. Hoặc dùng chúng để xem **thống kê tổng hợp** về hiệu năng của từng liên kết service-to-service. Cuối cùng, bạn có thể đi sâu vào bất kỳ **giao dịch (transaction)** cụ thể nào để xem các segment đại diện cho lời gọi mạng gắn với giao dịch đó như thế nào.

Bạn có thể dùng các tính năng này để xác định có **nút thắt cổ chai (bottleneck)** về mạng hay không, hoặc có service cụ thể nào trong mạng **không hoạt động như mong đợi** hay không.

**💡 Giải thích thêm**

| Tính năng X-Ray | Dùng để |
|---|---|
| Instrument bằng SDK | Ghi thời gian và mã phản hồi của request giữa các service, và tới endpoint dịch vụ AWS |
| Biểu đồ (graph) | Xem các service kết nối với nhau thế nào |
| Thống kê tổng hợp | Hiệu năng từng liên kết service-to-service |
| Đi sâu vào transaction | Xem các segment (lời gọi mạng) trong một giao dịch |

Mục tiêu: tìm **bottleneck mạng** hoặc **service hoạt động bất thường**.

---

## 3. VPC Flow Logs

**📘 Theo docs AWS**

Bạn có thể dùng Amazon VPC flow logs để phân tích hiệu năng mạng và debug vấn đề kết nối. Khi bật VPC flow logs, bạn có thể ghi lại log của **mọi kết nối** trong VPC. Bao gồm kết nối tới các network interface gắn với Elastic Load Balancing, Amazon RDS, NAT gateway, và các dịch vụ AWS quan trọng khác mà bạn có thể đang dùng. Thông tin thêm, xem *VPC Flow Logs* (<https://docs.aws.amazon.com/vpc/latest/userguide/flow-logs.html>) trong *Amazon VPC User Guide*.

**💡 Giải thích thêm**

VPC Flow Logs ghi lại kết nối ở mức **network interface** trong VPC — gồm cả interface của ELB, RDS, NAT gateway… Dùng khi cần biết **kết nối có đi được không** và **hiệu năng mạng** ra sao.

---

## 4. Mẹo tinh chỉnh mạng

**📘 Theo docs AWS**

Có một số cài đặt bạn có thể tinh chỉnh để cải thiện mạng.

### 4.1 nofile ulimit

**📘 Theo docs AWS**

Nếu bạn dự kiến ứng dụng có traffic cao và xử lý nhiều kết nối đồng thời, bạn nên tính đến **quota hệ thống về số file** được phép. Khi có nhiều network socket mở, mỗi socket phải được đại diện bởi một **file descriptor**. Nếu quota file descriptor quá thấp, nó sẽ giới hạn số network socket. Điều này dẫn tới kết nối thất bại hoặc lỗi. Bạn có thể cập nhật quota số file riêng cho container trong **task definition** của Amazon ECS. Nếu chạy trên Amazon EC2 (thay vì AWS Fargate), bạn có thể cũng cần điều chỉnh các quota này trên **Amazon EC2 instance bên dưới**.

**💡 Giải thích thêm**

```
mỗi network socket mở  =  một file descriptor
quota file descriptor thấp  →  hết socket  →  kết nối thất bại / lỗi
```

| Hạ tầng | Chỉnh ở đâu (theo trang) |
|---|---|
| Fargate | Task definition (quota số file của container) |
| EC2 | Task definition **và** có thể cả trên EC2 instance bên dưới |

### 4.2 sysctl net

**📘 Theo docs AWS**

Một nhóm cài đặt tinh chỉnh được khác là các cài đặt **`sysctl` net**. Bạn nên tham khảo các cài đặt cụ thể cho bản phân phối Linux mà bạn chọn. Nhiều cài đặt trong số này điều chỉnh kích thước **buffer đọc và ghi**. Điều này có thể giúp trong một số tình huống khi chạy EC2 instance cỡ lớn có nhiều container trên đó.

**💡 Giải thích thêm**

| Điểm | Theo đoạn trên |
|---|---|
| Là gì | Nhóm cài đặt mạng `sysctl` của Linux |
| Thường chỉnh gì | Kích thước buffer đọc / ghi |
| Khi nào có ích | EC2 instance cỡ lớn chạy nhiều container |
| Tham khảo | Tài liệu của bản phân phối Linux đang dùng |

---

## 5. Tổng hợp

**💡 Giải thích thêm**

Tổng hợp từ các mục trên của chính trang:

| Công cụ / cài đặt | Giúp được gì |
|---|---|
| CloudWatch Container Insights | Metric và log (gồm mạng) của container, dashboard tự động |
| AWS X-Ray | Tracing request giữa service và tới dịch vụ AWS; tìm bottleneck, service bất thường |
| VPC Flow Logs | Log mọi kết nối trong VPC; phân tích hiệu năng, debug kết nối |
| `nofile` ulimit | Tránh hết file descriptor khi có nhiều kết nối đồng thời |
| `sysctl` net | Chỉnh buffer đọc/ghi trên instance lớn nhiều container |

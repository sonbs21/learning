# Best practices for connecting Amazon ECS to AWS services from inside your VPC

> Nguồn: **Amazon ECS Developer Guide — Best practices for connecting Amazon ECS to AWS services from inside your VPC**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/networking-connecting-vpc.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: **bản dịch** sang tiếng Việt, đi đúng thứ tự và đúng nội dung của trang gốc, không thêm, không bớt ý.
> - Khối **💡 Giải thích thêm**: chỉ giải thích lại những gì **chính trang này** đã nói, cho dễ hiểu hơn. Không đưa thông tin từ trang khác hay ví dụ tự đặt.
> - Tên dịch vụ, tính năng giữ nguyên tiếng Anh.
> - Tài liệu AWS được phát hành theo giấy phép **Creative Commons Attribution-ShareAlike 4.0**; bản dịch này ghi rõ nguồn ở trên.

---

## Mục lục

0. [Phần mở đầu](#0-phần-mở-đầu)
1. [NAT gateway](#1-nat-gateway)
2. [AWS PrivateLink](#2-aws-privatelink)
3. [So sánh hai cách](#3-so-sánh-hai-cách)

---

## 0. Phần mở đầu

**📘 Theo docs AWS**

Để Amazon ECS hoạt động đúng, Amazon ECS container agent chạy trên mỗi host phải giao tiếp được với **control plane** của Amazon ECS. Nếu bạn lưu container image trong Amazon ECR, các Amazon EC2 host phải giao tiếp được với **service endpoint của Amazon ECR**, và với **Amazon S3**, nơi lưu các layer của image. Nếu bạn dùng các dịch vụ AWS khác cho ứng dụng container, chẳng hạn lưu dữ liệu bền vững trong DynamoDB, hãy kiểm tra kỹ rằng các dịch vụ này cũng có hỗ trợ mạng cần thiết.

**💡 Giải thích thêm**

Những đích mà host/task cần tới được, theo đoạn trên:

| Ai gọi | Gọi tới | Để làm gì |
|---|---|---|
| ECS container agent | Control plane của Amazon ECS | Để ECS hoạt động |
| EC2 host | Service endpoint của Amazon ECR | Lấy container image |
| EC2 host | Amazon S3 | Tải các layer của image |
| Ứng dụng | Dịch vụ AWS khác (vd DynamoDB) | Tuỳ ứng dụng |

Hai mục tiếp theo là hai cách để tới được các đích này từ bên trong VPC.

---

## 1. NAT gateway

**📘 Theo docs AWS**

Dùng NAT gateway là cách **dễ nhất** để đảm bảo task Amazon ECS truy cập được các dịch vụ AWS khác. Thông tin thêm về cách này, xem *Private subnet and NAT gateway* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/networking-outbound.html#networking-private-subnet>).

*(Hình trên trang gốc: sơ đồ kiến trúc mạng dùng NAT gateway.)*

Sau đây là các **nhược điểm** của cách này:

- Bạn không thể giới hạn những đích mà NAT gateway được giao tiếp tới. Bạn cũng không thể giới hạn những đích mà tầng backend được giao tiếp tới mà không làm gián đoạn mọi giao tiếp ra ngoài từ VPC.

- NAT gateway tính phí cho **mỗi GB** dữ liệu đi qua. Nếu dùng NAT gateway cho bất kỳ thao tác nào sau đây, bạn bị tính phí cho mỗi GB băng thông:

  - Tải file lớn từ Amazon S3
  - Thực hiện lượng lớn truy vấn database tới DynamoDB
  - Pull image từ Amazon ECR

  Ngoài ra, NAT gateway hỗ trợ băng thông **5 Gbps** và tự động scale lên tới **45 Gbps**. Nếu đi qua một NAT gateway duy nhất, ứng dụng cần kết nối băng thông rất cao có thể gặp giới hạn về mạng. Để khắc phục, bạn có thể chia workload ra nhiều subnet và cho mỗi subnet một NAT gateway riêng.

**💡 Giải thích thêm**

| Nhược điểm | Theo trang |
|---|---|
| Không giới hạn được đích | Không chặn được đích mà NAT hay backend được gọi tới, trừ khi cắt mọi kết nối ra ngoài của VPC |
| Phí theo GB | Mọi dữ liệu qua NAT đều tính phí — kể cả tải từ S3, truy vấn DynamoDB, pull image từ ECR |
| Giới hạn băng thông | 5 Gbps, tự scale tới 45 Gbps mỗi NAT gateway |

Cách khắc phục giới hạn băng thông mà trang nêu: **chia workload ra nhiều subnet**, mỗi subnet một NAT gateway.

---

## 2. AWS PrivateLink

**📘 Theo docs AWS**

AWS PrivateLink cung cấp kết nối riêng tư giữa các VPC, dịch vụ AWS và mạng on-premises của bạn mà không để traffic đi ra internet công cộng.

**VPC endpoint** cho phép kết nối riêng tư giữa VPC của bạn với các dịch vụ AWS được hỗ trợ và các VPC endpoint service. Traffic giữa VPC và dịch vụ kia **không rời khỏi mạng Amazon**. VPC endpoint **không cần** internet gateway, virtual private gateway, thiết bị NAT, kết nối VPN hay kết nối Direct Connect. Các Amazon EC2 instance trong VPC **không cần IP public** để giao tiếp với tài nguyên trong dịch vụ.

Sơ đồ sau cho thấy cách giao tiếp tới các dịch vụ AWS khi bạn dùng VPC endpoint thay cho internet gateway. AWS PrivateLink cấp **elastic network interface (ENI)** bên trong subnet, và **quy tắc định tuyến của VPC** được dùng để gửi mọi giao tiếp tới **hostname của dịch vụ** qua ENI đó, trực tiếp tới dịch vụ AWS đích. Traffic này **không còn cần** đi qua NAT gateway hay internet gateway.

*(Hình trên trang gốc: sơ đồ kiến trúc mạng dùng AWS PrivateLink.)*

Sau đây là một số VPC endpoint thường dùng với dịch vụ Amazon ECS.

- *Gateway endpoints for Amazon S3* (<https://docs.aws.amazon.com/vpc/latest/privatelink/vpc-endpoints-s3.html>)
- *DynamoDB VPC endpoint* (<https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/vpc-endpoints-dynamodb.html>)
- *Amazon ECS VPC endpoint* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/vpc-endpoints.html>)
- *Amazon ECR VPC endpoint* (<https://docs.aws.amazon.com/AmazonECR/latest/userguide/vpc-endpoints.html>)

Nhiều dịch vụ AWS khác cũng hỗ trợ VPC endpoint. Nếu bạn dùng nhiều một dịch vụ AWS nào đó, hãy tra tài liệu riêng của dịch vụ đó và cách tạo VPC endpoint cho traffic đó.

**💡 Giải thích thêm**

```
Task / host trong subnet
   │  gọi hostname của dịch vụ (vd ECR, ECS)
   ▼
ENI do PrivateLink cấp trong subnet  ── định tuyến VPC ──►  dịch vụ AWS đích
   (không qua NAT gateway, không qua internet gateway; traffic không rời mạng Amazon)
```

Những thứ VPC endpoint **không cần**, theo trang: internet gateway, virtual private gateway, thiết bị NAT, VPN, Direct Connect, IP public cho EC2 instance.

Bốn VPC endpoint trang liệt kê khớp với các đích ở phần mở đầu:

| Đích ở phần mở đầu | VPC endpoint tương ứng |
|---|---|
| Control plane Amazon ECS | Amazon ECS VPC endpoint |
| Service endpoint Amazon ECR | Amazon ECR VPC endpoint |
| Amazon S3 (layer của image) | Gateway endpoint cho Amazon S3 |
| DynamoDB (ví dụ dịch vụ khác) | DynamoDB VPC endpoint |

---

## 3. So sánh hai cách

**💡 Giải thích thêm**

Tổng hợp từ hai mục trên của chính trang:

| | NAT gateway | AWS PrivateLink (VPC endpoint) |
|---|---|---|
| Độ dễ | Dễ nhất | Cần tạo endpoint cho từng dịch vụ |
| Giới hạn đích | Không giới hạn được | Chỉ tới dịch vụ có endpoint |
| Traffic đi đâu | Ra ngoài qua NAT | Không rời mạng Amazon |
| Chi phí theo trang | Tính phí **mỗi GB** qua NAT (S3, DynamoDB, ECR…) | Không qua NAT nên không chịu phí NAT cho traffic này |
| Băng thông theo trang | 5 Gbps, tự scale tới 45 Gbps mỗi NAT | (trang không nêu) |
| Cần internet gateway, NAT, IP public cho instance | (trang không nêu) | Không cần |

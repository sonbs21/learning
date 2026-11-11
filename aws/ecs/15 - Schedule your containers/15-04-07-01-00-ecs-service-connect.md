# Use Service Connect to connect Amazon ECS services with short names

> Nguồn: **Amazon ECS Developer Guide — Use Service Connect to connect Amazon ECS services with short names**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-connect.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: **bản dịch** sang tiếng Việt, đi đúng thứ tự và đúng nội dung của trang gốc, không thêm, không bớt ý.
> - Khối **💡 Giải thích thêm**: chỉ giải thích lại những gì **chính trang này** đã nói, cho dễ hiểu hơn. Không đưa thông tin từ trang khác hay ví dụ tự đặt.
> - Thuật ngữ Service Connect (port name, client alias, discovery name, endpoint, namespace…) giữ nguyên tiếng Anh.
> - Tài liệu AWS được phát hành theo giấy phép **Creative Commons Attribution-ShareAlike 4.0**; bản dịch này ghi rõ nguồn ở trên.

---

## Mục lục

0. [Phần mở đầu](#0-phần-mở-đầu)
1. [Ví dụ mạng Service Connect](#1-ví-dụ-mạng-service-connect)
2. [Thuật ngữ](#2-thuật-ngữ)
3. [Mạng ảo của các service liên quan](#3-mạng-ảo-của-các-service-liên-quan)
4. [Các chủ đề con](#4-các-chủ-đề-con)
5. [Giá](#5-giá)

---

## 0. Phần mở đầu

**📘 Theo docs AWS**

Amazon ECS Service Connect cung cấp việc quản lý giao tiếp service-to-service dưới dạng **cấu hình Amazon ECS**. Nó xây dựng cả **service discovery** lẫn **service mesh** trong Amazon ECS. Điều này cung cấp:
- cấu hình đầy đủ bên trong mỗi service mà bạn quản lý bằng **deployment của service**;
- một cách thống nhất để tham chiếu tới service trong các **namespace**, **không phụ thuộc** cấu hình DNS của VPC;
- **metric và log chuẩn hoá** để giám sát mọi ứng dụng.

Service Connect **chỉ** kết nối các service với nhau.

---

## 1. Ví dụ mạng Service Connect

**📘 Theo docs AWS**

Sơ đồ sau cho thấy một ví dụ mạng Service Connect với **2 subnet** trong VPC và **2 service**. Một **client service** chạy **WordPress** với 1 task ở mỗi subnet. Một **server service** chạy **MySQL** với 1 task ở mỗi subnet. Cả hai service đều **sẵn sàng cao** và **chịu được** sự cố task và Availability Zone, vì mỗi service chạy nhiều task trải trên 2 subnet. Các mũi tên liền thể hiện kết nối từ WordPress tới MySQL. Ví dụ, lệnh CLI `mysql --host=mysql` được chạy từ bên trong container WordPress trong task có địa chỉ IP `172.31.16.1`. Lệnh dùng tên ngắn **`mysql`** trên port mặc định của MySQL. Tên và port này kết nối tới **Service Connect proxy trong cùng task**. Proxy trong task WordPress dùng cân bằng tải **round-robin** và mọi thông tin lỗi trước đó trong **outlier detection** để chọn task MySQL nào kết nối tới. Như mũi tên liền trong sơ đồ, proxy kết nối tới **proxy thứ hai** trong task MySQL có địa chỉ IP `172.31.16.2`. Proxy thứ hai kết nối tới MySQL server **cục bộ** trong cùng task. Cả hai proxy báo cáo hiệu năng kết nối, hiển thị trong biểu đồ ở console Amazon ECS và Amazon CloudWatch, để bạn có metric hiệu năng từ mọi loại ứng dụng theo cùng một cách.

*(Hình trên trang gốc: ví dụ mạng Service Connect với các service HA tối thiểu.)*

**💡 Giải thích thêm**

```
Task WordPress (172.31.16.1)                       Task MySQL (172.31.16.2)
┌─────────────────────────────┐                    ┌─────────────────────────────┐
│ container WordPress         │                    │ Service Connect proxy       │
│   mysql --host=mysql ──────►│ Service Connect ──►│   ──► MySQL server cục bộ   │
│                             │ proxy (round-robin │                             │
│                             │ + outlier detection│                             │
└─────────────────────────────┘ chọn task MySQL)   └─────────────────────────────┘
          cả hai proxy báo cáo hiệu năng → console ECS và CloudWatch
```

| | Client service | Server service |
|---|---|---|
| Ứng dụng | WordPress | MySQL |
| Task | 1 task mỗi subnet (2 subnet) | 1 task mỗi subnet (2 subnet) |

Ứng dụng chỉ cần gọi tên ngắn `mysql` + port mặc định; việc chọn task MySQL nào là do proxy trong task WordPress.

---

## 2. Thuật ngữ

**📘 Theo docs AWS**

Các thuật ngữ sau được dùng với Service Connect.

**port name**
Cấu hình trong **task definition** gán một tên cho một port mapping cụ thể. Cấu hình này chỉ được Amazon ECS Service Connect dùng.

**client alias**
Cấu hình trong **service** gán số port dùng trong endpoint. Ngoài ra, client alias có thể gán **tên DNS** của endpoint, **ghi đè discovery name**. Nếu service không có discovery name, tên client alias ghi đè port name để làm tên endpoint. Ví dụ endpoint, xem định nghĩa *endpoint*. Có thể gán **nhiều client alias** cho một service. Cấu hình này chỉ được Amazon ECS Service Connect dùng.

**discovery name**
Tên trung gian, **tuỳ chọn**, bạn có thể tạo cho một port chỉ định từ task definition. Tên này được dùng để tạo một **AWS Cloud Map service**. Nếu không có tên này, port name từ task definition được dùng. Có thể gán **nhiều discovery name** cho một port cụ thể của một service. Cấu hình này chỉ được Amazon ECS Service Connect dùng.

Tên AWS Cloud Map service phải **duy nhất trong một namespace**. Vì giới hạn này, bạn chỉ có thể có **một** cấu hình Service Connect **không có discovery name** cho một task definition cụ thể trong mỗi namespace.

**endpoint**
URL để kết nối tới một API hoặc website. URL gồm **giao thức**, **tên DNS** và **port**. Thông tin thêm về endpoint nói chung, xem *endpoint* (<https://docs.aws.amazon.com/glossary/latest/reference/glos-chap.html#endpoint>) trong *AWS glossary* của Amazon Web Services General Reference.

Service Connect tạo các endpoint kết nối tới Amazon ECS service và cấu hình các task trong service để kết nối tới endpoint. URL gồm giao thức, tên DNS và port. Bạn chọn **giao thức** và **port name** trong **task definition**, vì port phải khớp với ứng dụng trong container image. Trong **service**, bạn chọn từng port theo tên và có thể gán tên DNS. Nếu không chỉ định tên DNS trong cấu hình service, port name từ task definition được dùng mặc định. Ví dụ, một endpoint Service Connect có thể là `http://blog:80`, `grpc://checkout:8080`, hoặc `http://_db.production.internal:99`.

**Service Connect service**
Cấu hình của **một endpoint** trong một Amazon ECS service. Đây là một phần của cấu hình Service Connect, gồm **một dòng** trong mục **Service Connect and discovery name configuration** trên console, hoặc **một object** trong danh sách `services` trong cấu hình JSON của Amazon ECS service. Cấu hình này chỉ được Amazon ECS Service Connect dùng.

Thông tin thêm, xem *ServiceConnectService* (<https://docs.aws.amazon.com/AmazonECS/latest/APIReference/API_ServiceConnectService.html>) trong Amazon Elastic Container Service API Reference.

**namespace**
Tên ngắn hoặc ARN đầy đủ của **AWS Cloud Map namespace** dùng với Service Connect. Namespace phải ở **cùng AWS Region** với Amazon ECS service và cluster. **Loại** namespace trong AWS Cloud Map **không ảnh hưởng** Service Connect. Namespace có thể là namespace được chia sẻ với AWS account của bạn qua **AWS Resource Access Manager (AWS RAM)** ở các AWS Region có AWS RAM. Thông tin thêm về namespace chia sẻ, xem *Cross-account AWS Cloud Map namespace sharing* (<https://docs.aws.amazon.com/cloud-map/latest/dg/sharing-namespaces.html>) trong *AWS Cloud Map Developer Guide*.

Service Connect dùng AWS Cloud Map namespace như một **nhóm logic** các task Amazon ECS nói chuyện với nhau. Mỗi Amazon ECS service chỉ thuộc **một** namespace. Các service trong một namespace có thể trải trên **nhiều Amazon ECS cluster** trong cùng AWS Region. Nếu namespace là namespace chia sẻ, service có thể trải trên AWS account của **chủ namespace** và **bên dùng namespace**. Bạn có thể tự do sắp xếp service theo bất kỳ tiêu chí nào.

**client service**
Service chạy ứng dụng **network client**. Service này phải có **namespace** được cấu hình. Mỗi task trong service có thể tìm thấy và kết nối tới **mọi endpoint** trong namespace thông qua một **container Service Connect proxy**.

Nếu bất kỳ container nào trong task cần kết nối tới endpoint của một service trong namespace, hãy chọn client service. Nếu một ứng dụng frontend, reverse proxy hoặc load balancer nhận traffic bên ngoài qua cách khác như từ Elastic Load Balancing, nó có thể dùng loại cấu hình Service Connect này.

**client-server service**
Amazon ECS service chạy ứng dụng **network** hoặc **web service**. Service này phải có **namespace** và **ít nhất một endpoint** được cấu hình. Mỗi task trong service **truy cập được** qua các endpoint. Container Service Connect proxy lắng nghe trên tên và port của endpoint để chuyển traffic tới các container ứng dụng trong task.

Nếu bất kỳ container nào mở và lắng nghe một port cho traffic mạng, hãy chọn client-server service. Các ứng dụng này không cần kết nối tới client-server service khác trong cùng namespace, nhưng vẫn cần cấu hình client. Backend, middleware, tầng nghiệp vụ, hoặc đa số microservice có thể dùng loại cấu hình Service Connect này. Nếu bạn muốn một ứng dụng frontend, reverse proxy hoặc load balancer nhận traffic từ các service khác được cấu hình Service Connect trong cùng namespace, các service này nên dùng loại cấu hình Service Connect này.

**💡 Giải thích thêm**

Cấu hình nằm ở đâu:

| Thuật ngữ | Nằm ở | Vai trò |
|---|---|---|
| port name | **Task definition** | Đặt tên cho port mapping |
| discovery name | Service (tuỳ chọn) | Tên tạo Cloud Map service; không có thì dùng port name |
| client alias | **Service** | Port của endpoint; có thể đặt tên DNS, ghi đè discovery name |
| Service Connect service | **Service** | Cấu hình **một** endpoint (một dòng trên console / một object trong `services`) |
| namespace | Service | Cloud Map namespace, cùng Region, mỗi service một namespace |

Tên DNS của endpoint được lấy theo thứ tự ưu tiên (theo các định nghĩa trên):

```
client alias (nếu đặt tên DNS)  →  discovery name  →  port name (từ task definition)
     ưu tiên cao nhất                                       mặc định
```

Ví dụ endpoint trên trang: `http://blog:80`, `grpc://checkout:8080`, `http://_db.production.internal:99` — đều là **giao thức** + **tên DNS** + **port**.

Giới hạn cần nhớ: tên Cloud Map service duy nhất trong namespace → mỗi task definition chỉ có **một** cấu hình Service Connect **không có discovery name** trong mỗi namespace.

| | Client service | Client-server service |
|---|---|---|
| Cần namespace | Có | Có |
| Cần endpoint | Không | **Ít nhất một** |
| Gọi được endpoint khác | Có | Có (vẫn có cấu hình client) |
| Được gọi tới | Không | Có — proxy lắng nghe tên/port endpoint |
| Ví dụ theo trang | Frontend, reverse proxy, load balancer nhận traffic ngoài (vd từ ELB) | Backend, middleware, tầng nghiệp vụ, đa số microservice; frontend muốn nhận traffic từ service khác trong namespace |

---

## 3. Mạng ảo của các service liên quan

**📘 Theo docs AWS**

Tính năng Service Connect tạo ra một **mạng ảo** của các service liên quan. Cùng một cấu hình service có thể được dùng trên **nhiều namespace khác nhau** để chạy các bộ ứng dụng độc lập nhưng giống hệt nhau. Service Connect định nghĩa **container proxy trong Amazon ECS service**. Nhờ vậy, cùng một task definition có thể dùng để chạy các ứng dụng giống hệt nhau trong các namespace khác nhau với cấu hình Service Connect khác nhau. **Mỗi task** mà service tạo ra đều chạy một container proxy trong task.

Service Connect phù hợp cho kết nối giữa các Amazon ECS service **trong cùng namespace**. Với các ứng dụng sau, bạn cần dùng **thêm một cách kết nối khác** để kết nối tới Amazon ECS service được cấu hình Service Connect:

- Task được cấu hình ở **namespace khác**
- Task **không** được cấu hình Service Connect
- Ứng dụng khác **bên ngoài** Amazon ECS

Các ứng dụng này có thể kết nối **qua** Service Connect proxy nhưng **không phân giải được** tên endpoint của Service Connect.

Để các ứng dụng này phân giải được địa chỉ IP của task Amazon ECS, bạn cần dùng một cách kết nối khác.

**💡 Giải thích thêm**

```
Namespace A                         Namespace B
 service X ──► service Y            service X ──► service Y
 (cùng task definition, cấu hình Service Connect khác nhau → hai bộ ứng dụng độc lập, giống hệt nhau)
```

| Ai gọi tới service dùng Service Connect | Phân giải tên endpoint? |
|---|---|
| Task Service Connect cùng namespace | ✅ |
| Task ở namespace khác | ❌ — cần cách kết nối khác |
| Task không cấu hình Service Connect | ❌ — cần cách kết nối khác |
| Ứng dụng ngoài Amazon ECS | ❌ — cần cách kết nối khác |

---

## 4. Các chủ đề con

**📘 Theo docs AWS**

- Pricing (mục 5 bên dưới)
- *Amazon ECS Service Connect components* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-connect-concepts-deploy.html>
- *Amazon ECS Service Connect configuration overview* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-connect-concepts.html>
- *Amazon ECS Service Connect with shared AWS Cloud Map namespaces* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-connect-shared-namespaces.html>
- *Amazon ECS Service Connect access logs* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-connect-envoy-access-logs.html>
- *Amazon ECS Service Connect zone-aware routing* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-connect-zone-aware-routing.html>
- *Encrypt Amazon ECS Service Connect traffic* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-connect-tls.html>
- *Configuring Amazon ECS Service Connect with the AWS CLI* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/create-service-connect.html>

---

## 5. Giá

**📘 Theo docs AWS**

- Giá Amazon ECS Service Connect phụ thuộc vào việc bạn dùng hạ tầng **AWS Fargate** hay **Amazon EC2** để chạy workload container. Khi dùng Amazon ECS trên **AWS Outposts**, giá theo cùng mô hình như khi dùng Amazon EC2 trực tiếp. Thông tin thêm, xem *Amazon ECS Pricing* (<https://aws.amazon.com/ecs/pricing>).
- **Không tính thêm phí** khi dùng Amazon ECS Service Connect.
- **Không tính thêm phí** cho việc dùng AWS Cloud Map khi được Service Connect dùng.
- Khách hàng trả tiền cho tài nguyên compute mà Amazon ECS Service Connect dùng, gồm **vCPU** và **memory**. Vì Amazon ECS Service Connect agent chạy **bên trong task** của khách hàng, không tính thêm phí cho việc chạy nó. Tài nguyên của task được **chia sẻ** giữa workload của khách hàng và Amazon ECS Service Connect agent.
- Khi dùng chức năng **mã hoá traffic** của Amazon ECS Service Connect với **AWS Private CA**, khách hàng trả tiền cho private certificate authority họ tạo và cho **mỗi TLS certificate** được cấp. Thông tin thêm, xem *AWS Private Certificate Authority pricing* (<https://aws.amazon.com/private-ca/pricing/>). Để ước tính chi phí TLS certificate hằng tháng, khách hàng cần biết số Amazon ECS service bật TLS, nhân với giá certificate, rồi nhân với **sáu**. Vì Amazon ECS Service Connect **tự động xoay vòng** TLS certificate **mỗi năm ngày**, trung bình có **sáu** certificate được cấp cho mỗi Amazon ECS service mỗi tháng.

**💡 Giải thích thêm**

| Khoản | Có tính phí? (theo trang) |
|---|---|
| Bản thân Service Connect | Không |
| AWS Cloud Map dùng bởi Service Connect | Không |
| vCPU/memory mà agent dùng | Không tính thêm — nhưng agent **dùng chung** tài nguyên task bạn đã trả |
| Mã hoá TLS với AWS Private CA | Có — phí private CA + phí mỗi certificate |

Công thức ước tính phí certificate TLS hằng tháng theo trang:

```
phí certificate/tháng ≈ (số service bật TLS) × (giá một certificate) × 6
                                                                    ▲
                                       xoay vòng mỗi 5 ngày → ~6 certificate/service/tháng
```

(Chưa gồm phí của chính private certificate authority.)

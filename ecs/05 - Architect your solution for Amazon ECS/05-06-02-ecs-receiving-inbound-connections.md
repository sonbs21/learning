# Best practices for receiving inbound connections to Amazon ECS from the internet

> Nguồn: **Amazon ECS Developer Guide — Best practices for receiving inbound connections to Amazon ECS from the internet**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/networking-inbound.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: **bản dịch** sang tiếng Việt, đi đúng thứ tự và đúng nội dung của trang gốc, không thêm, không bớt ý.
> - Khối **💡 Giải thích thêm**: chỉ giải thích lại những gì **chính trang này** đã nói, cho dễ hiểu hơn. Không đưa thông tin từ trang khác hay ví dụ tự đặt.
> - Tên dịch vụ, tính năng giữ nguyên tiếng Anh.
> - Tài liệu AWS được phát hành theo giấy phép **Creative Commons Attribution-ShareAlike 4.0**; bản dịch này ghi rõ nguồn ở trên.

---

## Mục lục

0. [Phần mở đầu](#0-phần-mở-đầu)
1. [Application Load Balancer](#1-application-load-balancer)
2. [Network Load Balancer](#2-network-load-balancer)
3. [Amazon API Gateway HTTP API](#3-amazon-api-gateway-http-api)
4. [So sánh ba lựa chọn](#4-so-sánh-ba-lựa-chọn)

---

## 0. Phần mở đầu

**📘 Theo docs AWS**

Nếu bạn chạy một service public, bạn phải chấp nhận traffic **vào (inbound)** từ internet. Ví dụ, website public của bạn phải chấp nhận request HTTP đến từ trình duyệt. Trong trường hợp đó, các host khác trên internet cũng phải khởi tạo kết nối vào tới host của ứng dụng.

Một cách giải quyết là launch container trên host nằm trong public subnet có địa chỉ IP public. Tuy nhiên, chúng tôi **không khuyến nghị** cách này cho ứng dụng quy mô lớn. Với các ứng dụng đó, cách tốt hơn là có một **lớp đầu vào (input layer) có khả năng scale**, nằm giữa internet và ứng dụng. Với cách này, bạn có thể dùng bất kỳ dịch vụ AWS nào được liệt kê trong phần này làm đầu vào.

**💡 Giải thích thêm**

```
Không khuyến nghị (quy mô lớn):  Internet → host/container ở public subnet (IP public)
Khuyến nghị:                     Internet → lớp đầu vào scale được → ứng dụng
                                            (ALB | NLB | API Gateway)
```

---

## 1. Application Load Balancer

**📘 Theo docs AWS**

Application Load Balancer hoạt động ở **tầng ứng dụng** — tầng thứ **bảy** của mô hình Open Systems Interconnection (OSI). Điều này khiến Application Load Balancer phù hợp với các service HTTP public. Nếu bạn có website hoặc HTTP REST API, Application Load Balancer là load balancer phù hợp cho workload này. Thông tin thêm, xem *What is an Application Load Balancer?* (<https://docs.aws.amazon.com/elasticloadbalancing/latest/application/introduction.html>) trong *User Guide for Application Load Balancers*.

*(Hình trên trang gốc: sơ đồ kiến trúc mạng dùng Application Load Balancer.)*

Với kiến trúc này, bạn tạo Application Load Balancer trong public subnet để nó có địa chỉ IP public và nhận được kết nối vào từ internet. Khi Application Load Balancer nhận một kết nối vào, cụ thể hơn là một HTTP request, nó mở kết nối tới ứng dụng bằng **địa chỉ IP private** của ứng dụng. Sau đó, nó chuyển tiếp request qua kết nối nội bộ đó.

Application Load Balancer có các ưu điểm sau.

- **SSL/TLS termination** — Application Load Balancer có thể duy trì giao tiếp HTTPS an toàn và certificate cho giao tiếp với client. Nó có thể tuỳ chọn kết thúc (terminate) kết nối SSL ở mức load balancer để bạn không phải xử lý certificate trong chính ứng dụng.

- **Định tuyến nâng cao** — Application Load Balancer có thể có nhiều DNS hostname. Nó cũng có khả năng định tuyến nâng cao để gửi HTTP request đến tới các đích khác nhau dựa trên các thông số như hostname hoặc path của request. Nghĩa là bạn có thể dùng một Application Load Balancer làm đầu vào cho nhiều service nội bộ khác nhau, hoặc thậm chí cho các microservice ở các path khác nhau của một REST API.

- **Hỗ trợ gRPC và websocket** — Application Load Balancer xử lý được nhiều hơn HTTP. Nó cũng có thể cân bằng tải cho các service dựa trên gRPC và websocket, với hỗ trợ HTTP/2.

- **Bảo mật** — Application Load Balancer giúp bảo vệ ứng dụng khỏi traffic độc hại. Nó có các tính năng như giảm thiểu HTTP desync, và tích hợp với AWS Web Application Firewall (AWS WAF). AWS WAF có thể lọc thêm traffic độc hại có thể chứa các mẫu tấn công, như SQL injection hoặc cross-site scripting.

**💡 Giải thích thêm**

```
Internet → ALB (public subnet, IP public) → mở kết nối tới IP private của ứng dụng → chuyển tiếp HTTP request
```

| Ưu điểm | Ý chính |
|---|---|
| SSL/TLS termination | Xử lý certificate ở ALB, ứng dụng không cần |
| Định tuyến nâng cao | Theo hostname, path → một ALB cho nhiều service / microservice |
| gRPC, websocket | Có, với HTTP/2 |
| Bảo mật | Giảm thiểu HTTP desync; tích hợp AWS WAF (chặn SQL injection, XSS) |

---

## 2. Network Load Balancer

**📘 Theo docs AWS**

Network Load Balancer hoạt động ở tầng thứ **tư** của mô hình OSI. Nó phù hợp cho giao thức không phải HTTP, hoặc các tình huống cần mã hoá đầu-cuối (end-to-end encryption), nhưng **không có** các tính năng đặc thù HTTP như Application Load Balancer. Vì vậy, Network Load Balancer phù hợp nhất cho ứng dụng **không dùng HTTP**. Thông tin thêm, xem *What is a Network Load Balancer?* (<https://docs.aws.amazon.com/elasticloadbalancing/latest/network/introduction.html>) trong *User Guide for Network Load Balancers*.

*(Hình trên trang gốc: sơ đồ kiến trúc mạng dùng Network Load Balancer.)*

Khi dùng Network Load Balancer làm đầu vào, nó hoạt động tương tự Application Load Balancer. Đó là vì nó được tạo trong public subnet và có địa chỉ IP public truy cập được từ internet. Sau đó Network Load Balancer mở kết nối tới địa chỉ IP private của host chạy container, và gửi các gói tin từ phía public sang phía private.

### Tính năng của Network Load Balancer

Vì Network Load Balancer hoạt động ở tầng thấp hơn của network stack, nó không có cùng bộ tính năng như Application Load Balancer. Tuy nhiên, nó có các tính năng quan trọng sau.

- **Mã hoá đầu-cuối** — Vì Network Load Balancer hoạt động ở tầng thứ tư của mô hình OSI, nó **không đọc nội dung** gói tin. Điều này khiến nó phù hợp để cân bằng tải cho giao tiếp cần mã hoá đầu-cuối.

- **Mã hoá TLS** — Ngoài mã hoá đầu-cuối, Network Load Balancer cũng có thể kết thúc kết nối TLS. Bằng cách này, ứng dụng backend không phải tự triển khai TLS.

- **Hỗ trợ UDP** — Vì Network Load Balancer hoạt động ở tầng thứ tư của mô hình OSI, nó phù hợp với workload không phải HTTP và các giao thức khác TCP.

### Đóng kết nối

Vì Network Load Balancer không theo dõi giao thức ứng dụng ở các tầng cao hơn của mô hình OSI, nó không thể gửi thông điệp đóng kết nối tới client trong các giao thức đó. Không giống Application Load Balancer, các kết nối đó cần được **ứng dụng đóng**, hoặc bạn có thể cấu hình Network Load Balancer **đóng kết nối tầng bốn** khi task bị dừng hoặc bị thay thế. Xem cài đặt connection termination cho target group của Network Load Balancer trong *Network Load Balancer documentation* (<https://docs.aws.amazon.com/elasticloadbalancing/latest/network/load-balancer-target-groups.html#deregistration-delay>).

Để Network Load Balancer đóng kết nối ở tầng bốn có thể khiến client hiển thị thông báo lỗi không mong muốn, nếu client không xử lý chúng. Xem Builders Library để biết thêm về cấu hình client được khuyến nghị tại đây (<https://aws.amazon.com/builders-library/timeouts-retries-and-backoff-with-jitter>).

Cách đóng kết nối sẽ khác nhau theo ứng dụng, tuy nhiên một cách là đảm bảo **target deregistration delay** của Network Load Balancer **dài hơn** **timeout kết nối của client**. Client sẽ timeout trước và kết nối lại một cách êm qua Network Load Balancer tới task tiếp theo, trong khi task cũ từ từ drain hết client của nó. Thông tin thêm về target deregistration delay của Network Load Balancer, xem *Network Load Balancer documentation* (<https://docs.aws.amazon.com/elasticloadbalancing/latest/network/load-balancer-target-groups.html#deregistration-delay>).

**💡 Giải thích thêm**

| Tính năng NLB | Lý do (theo trang) |
|---|---|
| Mã hoá đầu-cuối | Tầng 4, không đọc nội dung gói tin |
| Kết thúc TLS | Backend không cần tự làm TLS |
| UDP | Tầng 4, hợp với giao thức không phải HTTP / khác TCP |

Vấn đề **đóng kết nối** khi task dừng hoặc bị thay:

| Cách | Nhược điểm / điều kiện (theo trang) |
|---|---|
| Ứng dụng tự đóng | Tuỳ ứng dụng |
| NLB đóng kết nối tầng 4 (connection termination) | Client có thể thấy lỗi nếu không xử lý |
| Deregistration delay **>** timeout của client | Client timeout trước, tự kết nối lại sang task mới; task cũ drain dần |

```
deregistration delay của NLB  ──────────────────────────┐
timeout của client            ──────────┐                │
                                        ▼                ▼
                                 client tự nối lại   task cũ gỡ khỏi NLB
                                 sang task mới       (lúc này đã hết client)
```

---

## 3. Amazon API Gateway HTTP API

**📘 Theo docs AWS**

Amazon API Gateway phù hợp với ứng dụng HTTP có lượng request **tăng đột biến** hoặc lượng request **thấp**. Thông tin thêm, xem *What is Amazon API Gateway?* (<https://docs.aws.amazon.com/apigateway/latest/developerguide/welcome.html>) trong *API Gateway Developer Guide*.

*(Hình trên trang gốc: sơ đồ kiến trúc mạng dùng API Gateway.)*

Mô hình giá của cả Application Load Balancer và Network Load Balancer đều có **phí theo giờ** để giữ load balancer luôn sẵn sàng nhận kết nối đến. Ngược lại, API Gateway tính phí **cho từng request**. Hệ quả là nếu không có request nào, sẽ không bị tính phí. Khi tải cao, Application Load Balancer hoặc Network Load Balancer có thể xử lý lượng request lớn hơn với giá mỗi request rẻ hơn API Gateway. Tuy nhiên, nếu tổng số request thấp hoặc có những giai đoạn traffic thấp, thì tổng chi phí dùng API Gateway sẽ hiệu quả hơn so với trả phí theo giờ để duy trì một load balancer đang dùng không hết công suất. API Gateway cũng có thể **cache** response của API, có thể giúp giảm số request tới backend.

API Gateway hoạt động bằng **VPC link**, cho phép dịch vụ được AWS quản lý này kết nối tới host bên trong private subnet của VPC bằng địa chỉ IP private. Nó có thể phát hiện các địa chỉ IP private này bằng cách xem các bản ghi service discovery của **AWS Cloud Map** do **Amazon ECS Service Discovery** quản lý.

API Gateway hỗ trợ các tính năng sau.

- Hoạt động của API Gateway tương tự load balancer, nhưng có thêm các khả năng riêng cho quản lý API.
- API Gateway cung cấp thêm các khả năng về phân quyền client, các bậc sử dụng (usage tiers), và sửa đổi request/response. Thông tin thêm, xem *Amazon API Gateway features* (<https://aws.amazon.com/api-gateway/features/>).
- API Gateway hỗ trợ endpoint API gateway dạng edge, regional và private. Endpoint edge có sẵn qua một CloudFront distribution được quản lý. Endpoint regional và private đều nằm cục bộ trong một Region.
- SSL/TLS termination
- Định tuyến các HTTP path khác nhau tới các backend microservice khác nhau

Ngoài các tính năng trên, API Gateway còn hỗ trợ dùng **custom Lambda authorizer** để bảo vệ API khỏi việc dùng trái phép. Thông tin thêm, xem *Field Notes: Serverless Container-based APIs with Amazon ECS and Amazon API Gateway* (<https://aws.amazon.com/blogs/architecture/field-notes-serverless-container-based-apis-with-amazon-ecs-and-amazon-api-gateway/>).

**💡 Giải thích thêm**

So sánh chi phí theo trang:

| | ALB / NLB | API Gateway |
|---|---|---|
| Cách tính phí | Theo **giờ** (luôn sẵn sàng) | Theo **từng request** |
| Không có request | Vẫn trả phí theo giờ | Không mất phí |
| Tải cao | Rẻ hơn mỗi request | Đắt hơn mỗi request |
| Tải thấp / có giai đoạn vắng | Trả tiền cho load balancer dùng không hết | Hiệu quả chi phí hơn |

Cách API Gateway tới được task trong private subnet:

```
Client → API Gateway → VPC link → IP private của task (private subnet)
                         ▲
                tìm IP qua bản ghi AWS Cloud Map
                (do Amazon ECS Service Discovery quản lý)
```

| Loại endpoint | Nằm ở đâu (theo trang) |
|---|---|
| Edge | Qua CloudFront distribution được quản lý |
| Regional | Cục bộ trong Region |
| Private | Cục bộ trong Region |

---

## 4. So sánh ba lựa chọn

**💡 Giải thích thêm**

Tổng hợp từ ba mục trên của chính trang:

| | Application Load Balancer | Network Load Balancer | API Gateway HTTP API |
|---|---|---|---|
| Tầng OSI | 7 | 4 | (trang không nêu) |
| Phù hợp | Website, HTTP REST API public | Giao thức không phải HTTP, cần mã hoá đầu-cuối | HTTP có lưu lượng đột biến hoặc thấp |
| Kết nối tới ứng dụng | IP private | IP private | VPC link + Cloud Map |
| SSL/TLS termination | Có | Có (TLS) | Có |
| Định tuyến theo path | Có (và theo hostname) | — | Có |
| gRPC / websocket | Có | — | — |
| UDP | — | Có | — |
| Bảo mật / phân quyền | HTTP desync mitigation, AWS WAF | — | Phân quyền client, Lambda authorizer |
| Tính phí | Theo giờ | Theo giờ | Theo request |
| Khác | — | Cần xử lý đóng kết nối khi task dừng | Cache response, usage tiers, sửa request/response |

Ô "—" nghĩa là trang không nhắc tới tính năng đó cho lựa chọn này.

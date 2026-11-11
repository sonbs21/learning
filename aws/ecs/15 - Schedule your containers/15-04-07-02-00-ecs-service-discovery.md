# Use service discovery to connect Amazon ECS services with DNS names

> Nguồn: **Amazon ECS Developer Guide — Use service discovery to connect Amazon ECS services with DNS names**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-discovery.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: **bản dịch** sang tiếng Việt, đi đúng thứ tự và đúng nội dung của trang gốc, không thêm, không bớt ý.
> - Khối **💡 Giải thích thêm**: chỉ giải thích lại những gì **chính trang này** đã nói, cho dễ hiểu hơn. Không đưa thông tin từ trang khác hay ví dụ tự đặt.
> - Tên thuộc tính, loại bản ghi DNS, lệnh, Region giữ nguyên tiếng Anh.
> - Tài liệu AWS được phát hành theo giấy phép **Creative Commons Attribution-ShareAlike 4.0**; bản dịch này ghi rõ nguồn ở trên.

---

## Mục lục

0. [Phần mở đầu và các Region hỗ trợ](#0-phần-mở-đầu-và-các-region-hỗ-trợ)
1. [Các khái niệm service discovery](#1-các-khái-niệm-service-discovery)
2. [Những điều cần cân nhắc](#2-những-điều-cần-cân-nhắc)
3. [Giá](#3-giá)

---

## 0. Phần mở đầu và các Region hỗ trợ

**📘 Theo docs AWS**

Amazon ECS service có thể **tuỳ chọn** được cấu hình dùng **Amazon ECS service discovery**. Service discovery dùng các API action của **AWS Cloud Map** để quản lý **HTTP namespace** và **DNS namespace** cho Amazon ECS service. Thông tin thêm, xem *What Is AWS Cloud Map* (<https://docs.aws.amazon.com/cloud-map/latest/dg/Welcome.html>) trong *AWS Cloud Map Developer Guide*.

Service discovery có ở các AWS Region sau:

| Tên Region | Region |
|---|---|
| US East (N. Virginia) | us-east-1 |
| US East (Ohio) | us-east-2 |
| US West (N. California) | us-west-1 |
| US West (Oregon) | us-west-2 |
| Africa (Cape Town) | af-south-1 |
| Asia Pacific (Hong Kong) | ap-east-1 |
| Asia Pacific (Taipei) | ap-east-2 |
| Asia Pacific (Mumbai) | ap-south-1 |
| Asia Pacific (Hyderabad) | ap-south-2 |
| Asia Pacific (Tokyo) | ap-northeast-1 |
| Asia Pacific (Seoul) | ap-northeast-2 |
| Asia Pacific (Osaka) | ap-northeast-3 |
| Asia Pacific (Singapore) | ap-southeast-1 |
| Asia Pacific (Sydney) | ap-southeast-2 |
| Asia Pacific (Jakarta) | ap-southeast-3 |
| Asia Pacific (Melbourne) | ap-southeast-4 |
| Asia Pacific (Malaysia) | ap-southeast-5 |
| Asia Pacific (New Zealand) | ap-southeast-6 |
| Asia Pacific (Thailand) | ap-southeast-7 |
| Canada (Central) | ca-central-1 |
| Canada West (Calgary) | ca-west-1 |
| China (Beijing) | cn-north-1 |
| China (Ningxia) | cn-northwest-1 |
| Europe (Frankfurt) | eu-central-1 |
| Europe (Zurich) | eu-central-2 |
| Europe (Ireland) | eu-west-1 |
| Europe (London) | eu-west-2 |
| Europe (Paris) | eu-west-3 |
| Europe (Milan) | eu-south-1 |
| Europe (Stockholm) | eu-north-1 |
| Israel (Tel Aviv) | il-central-1 |
| Europe (Spain) | eu-south-2 |
| Middle East (UAE) | me-central-1 |
| Mexico (Central) | mx-central-1 |
| Middle East (Bahrain) | me-south-1 |
| South America (São Paulo) | sa-east-1 |
| AWS GovCloud (US-East) | us-gov-east-1 |
| AWS GovCloud (US-West) | us-gov-west-1 |

---

## 1. Các khái niệm service discovery

**📘 Theo docs AWS**

Service discovery gồm các thành phần sau:

- **Service discovery namespace**: Một nhóm logic các service discovery service dùng **chung tên miền**, như `example.com`, là nơi bạn muốn định tuyến traffic. Bạn có thể tạo namespace bằng lệnh `aws servicediscovery create-private-dns-namespace` hoặc trong console Amazon ECS. Bạn có thể dùng lệnh `aws servicediscovery list-namespaces` để xem thông tin tóm tắt về các namespace mà account hiện tại đã tạo. Thông tin thêm về các lệnh service discovery, xem *create-private-dns-namespace* (<https://docs.aws.amazon.com/cli/latest/reference/servicediscovery/create-private-dns-namespace.html>) và *list-namespaces* (<https://docs.aws.amazon.com/cli/latest/reference/servicediscovery/list-namespaces.html>) trong *AWS Cloud Map (service discovery) AWS CLI Reference Guide*.

- **Service discovery service**: Nằm trong service discovery namespace, gồm **tên service** và **cấu hình DNS** cho namespace. Nó cung cấp thành phần cốt lõi sau:

  - **Service registry**: Cho phép bạn tra một service qua **DNS** hoặc **API action của AWS Cloud Map** và nhận lại một hoặc nhiều endpoint khả dụng để kết nối tới service.

- **Service discovery instance**: Nằm trong service discovery service, gồm các thuộc tính gắn với mỗi Amazon ECS service trong danh mục service.

  - **Thuộc tính instance**: Metadata sau được thêm làm **thuộc tính tuỳ chỉnh** cho mỗi Amazon ECS service được cấu hình dùng service discovery:

    - **`AWS_INSTANCE_IPV4`** – Với bản ghi `A`, địa chỉ IPv4 mà Route 53 trả về khi có truy vấn DNS và AWS Cloud Map trả về khi phát hiện chi tiết instance, ví dụ `192.0.2.44`.
    - **`AWS_INSTANCE_IPV6`** – Với bản ghi `AAAA`, địa chỉ IPv6 mà Route 53 trả về khi có truy vấn DNS và AWS Cloud Map trả về khi phát hiện chi tiết instance, ví dụ `2001:0db8:85a3:0000:0000:abcd:0001:2345`. Cả `AWS_INSTANCE_IPv4` và `AWS_INSTANCE_IPv6` được thêm cho Amazon ECS service **dualstack**. Chỉ `AWS_INSTANCE_IPv6` được thêm cho Amazon ECS service **IPv6-only**.
    - **`AWS_INSTANCE_PORT`** – Giá trị port gắn với service discovery service.
    - **`AVAILABILITY_ZONE`** – Availability Zone nơi task được launch. Với task dùng **EC2**, đây là AZ của container instance. Với task dùng **Fargate**, đây là AZ của elastic network interface.
    - **`REGION`** – Region nơi task tồn tại.
    - **`ECS_SERVICE_NAME`** – Tên Amazon ECS service mà task thuộc về.
    - **`ECS_CLUSTER_NAME`** – Tên Amazon ECS cluster mà task thuộc về.
    - **`EC2_INSTANCE_ID`** – ID container instance mà task được đặt lên. Thuộc tính tuỳ chỉnh này **không** được thêm nếu task dùng Fargate.
    - **`ECS_TASK_DEFINITION_FAMILY`** – Task definition family mà task đang dùng.
    - **`ECS_TASK_SET_EXTERNAL_ID`** – Nếu một task set được tạo cho **external deployment** và gắn với một service discovery registry, thì thuộc tính `ECS_TASK_SET_EXTERNAL_ID` chứa external ID của task set.

- **Amazon ECS health checks**: Amazon ECS thực hiện health check **mức container** định kỳ. Nếu một endpoint không vượt qua health check, nó bị **gỡ khỏi định tuyến DNS** và được đánh dấu **unhealthy**.

**💡 Giải thích thêm**

Cấu trúc lồng nhau theo trang:

```
Service discovery namespace   (vd example.com — tên miền chung)
 └─ Service discovery service (tên service + cấu hình DNS; cung cấp service registry)
     └─ Service discovery instance (thuộc tính của từng task: IP, port, AZ, Region, tên service/cluster…)
Amazon ECS health check: endpoint không đạt → gỡ khỏi DNS, đánh dấu unhealthy
```

Thuộc tính instance:

| Thuộc tính | Nội dung | Ghi chú |
|---|---|---|
| `AWS_INSTANCE_IPV4` | IPv4 (bản ghi `A`) | Dualstack: có cả IPv4 và IPv6 |
| `AWS_INSTANCE_IPV6` | IPv6 (bản ghi `AAAA`) | IPv6-only: chỉ có IPv6 |
| `AWS_INSTANCE_PORT` | Port | — |
| `AVAILABILITY_ZONE` | AZ của task | EC2: AZ của container instance; Fargate: AZ của ENI |
| `REGION` | Region | — |
| `ECS_SERVICE_NAME` | Tên service | — |
| `ECS_CLUSTER_NAME` | Tên cluster | — |
| `EC2_INSTANCE_ID` | ID container instance | Không có với Fargate |
| `ECS_TASK_DEFINITION_FAMILY` | Family của task definition | — |
| `ECS_TASK_SET_EXTERNAL_ID` | External ID của task set | Chỉ với external deployment |

Trên trang gốc, tên thuộc tính viết lẫn hoa thường (`AWS_INSTANCE_IPV4` ở tiêu đề và `AWS_INSTANCE_IPv4` trong câu giải thích); bản dịch giữ nguyên như docs.

---

## 2. Những điều cần cân nhắc

**📘 Theo docs AWS**

Cần cân nhắc các điểm sau khi dùng service discovery:

- Service discovery được hỗ trợ cho task trên **Fargate** dùng **platform version 1.1.0 trở lên**. Thông tin thêm, xem *Fargate platform versions for Amazon ECS* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/platform-fargate.html>).
- Service được cấu hình dùng service discovery có giới hạn **1.000 task mỗi service**. Lý do là service quota của **Route 53**.
- Quy trình **Create Service** trên console Amazon ECS **chỉ hỗ trợ** đăng ký service vào **private DNS namespace**. Khi tạo một AWS Cloud Map private DNS namespace, một **Route 53 private hosted zone** sẽ được tạo tự động.
- Phải cấu hình **thuộc tính DNS của VPC** để phân giải DNS thành công. Cách cấu hình, xem *DNS support in your VPC* (<https://docs.aws.amazon.com/vpc/latest/userguide/vpc-dns.html#vpc-dns-support>) trong *Amazon VPC User Guide*.
- Amazon ECS **không hỗ trợ** đăng ký service vào **AWS Cloud Map namespace chia sẻ**.
- Bản ghi DNS tạo cho một service discovery service **luôn** đăng ký với **IP private** của task, **không phải** IP public, **kể cả khi** dùng public namespace.
- Service discovery yêu cầu task chỉ định network mode **`awsvpc`**, **`bridge`** hoặc **`host`** (**không hỗ trợ `none`**).
- Nếu task definition của service dùng network mode **`awsvpc`**, bạn có thể tạo bất kỳ tổ hợp bản ghi **`A`** hoặc **`SRV`** nào cho mỗi task của service. Nếu dùng bản ghi `SRV`, **cần có port**. Bạn có thể tạo thêm bản ghi **`AAAA`** nếu service dùng subnet **dualstack**. Nếu service dùng subnet **IPv6-only**, bạn **không thể** tạo bản ghi `A`.
- Nếu task definition của service dùng network mode **`bridge`** hoặc **`host`**, bản ghi **SRV** là loại bản ghi DNS **duy nhất** được hỗ trợ. Tạo một bản ghi SRV cho mỗi task của service. Bản ghi SRV phải chỉ định tổ hợp **tên container** và **container port** từ task definition.
- Bản ghi DNS của service discovery service có thể được truy vấn **trong VPC**. Chúng dùng định dạng: **`<service-discovery-service-name>.<service-discovery-namespace>`**.
- Khi truy vấn DNS theo tên service, bản ghi `A` và `AAAA` trả về **tập địa chỉ IP** tương ứng các task. Bản ghi `SRV` trả về **tập địa chỉ IP và port** cho mỗi task.
- Nếu bạn có **tám bản ghi healthy trở xuống**, Route 53 trả lời mọi truy vấn DNS với **mọi** bản ghi healthy.
- Khi **mọi** bản ghi đều unhealthy, Route 53 trả lời truy vấn DNS với **tối đa tám** bản ghi unhealthy.
- Bạn có thể cấu hình service discovery cho service nằm **sau load balancer**, nhưng traffic service discovery **luôn** được định tuyến tới **task**, **không phải** load balancer.
- Service discovery **không hỗ trợ** Classic Load Balancer.
- Chúng tôi khuyến nghị dùng **health check mức container do Amazon ECS quản lý** cho service discovery service.
  - **HealthCheckCustomConfig** — Amazon ECS quản lý health check thay cho bạn. Amazon ECS dùng thông tin từ container, health check và trạng thái task để cập nhật sức khoẻ lên AWS Cloud Map. Việc này được chỉ định bằng tham số **`--health-check-custom-config`** khi tạo service discovery service. Thông tin thêm, xem *HealthCheckCustomConfig* (<https://docs.aws.amazon.com/cloud-map/latest/api/API_HealthCheckCustomConfig.html>) trong *AWS Cloud Map API Reference*.
- Tài nguyên AWS Cloud Map được tạo khi dùng service discovery phải được **dọn dẹp thủ công**.
- Task và instance được đăng ký là **`UNHEALTHY`** cho tới khi container health check trả về giá trị. Nếu health check đạt, trạng thái được cập nhật thành **`HEALTHY`**. Nếu container health check thất bại, service discovery instance bị **huỷ đăng ký**.

**💡 Giải thích thêm**

Bản ghi DNS theo network mode:

| Network mode | `A` | `AAAA` | `SRV` |
|---|---|---|---|
| `awsvpc` (IPv4) | ✅ | — | ✅ (cần port) |
| `awsvpc` dualstack | ✅ | ✅ | ✅ |
| `awsvpc` IPv6-only | ❌ | ✅ | ✅ |
| `bridge`, `host` | ❌ | ❌ | ✅ **duy nhất** (tên container + container port) |
| `none` | Không hỗ trợ service discovery | | |

Truy vấn DNS:

| Mục | Theo trang |
|---|---|
| Định dạng tên | `<service-discovery-service-name>.<service-discovery-namespace>` |
| Phạm vi truy vấn | Trong VPC |
| IP trả về | Luôn **IP private** của task (kể cả public namespace) |
| `A` / `AAAA` trả về | Tập IP |
| `SRV` trả về | Tập IP + port |
| ≤ 8 bản ghi healthy | Trả về **mọi** bản ghi healthy |
| Mọi bản ghi unhealthy | Trả về **tối đa 8** bản ghi unhealthy |

Trạng thái sức khoẻ của instance:

```
Đăng ký ──► UNHEALTHY ── container health check đạt ──► HEALTHY
                └────── container health check thất bại ──► huỷ đăng ký
```

Giới hạn và không hỗ trợ:

| Điểm | Theo trang |
|---|---|
| Fargate | Platform version ≥ 1.1.0 |
| Task mỗi service | Tối đa 1.000 (quota Route 53) |
| Console Create Service | Chỉ private DNS namespace (tự tạo Route 53 private hosted zone) |
| Namespace chia sẻ | **Không** hỗ trợ |
| Classic Load Balancer | **Không** hỗ trợ |
| Sau load balancer | Được, nhưng traffic service discovery đi thẳng tới task |
| Dọn tài nguyên Cloud Map | Thủ công |

---

## 3. Giá

**📘 Theo docs AWS**

Khách hàng dùng Amazon ECS service discovery bị tính phí cho **tài nguyên Route 53** và **API discovery của AWS Cloud Map**. Bao gồm chi phí tạo **Route 53 hosted zone** và **truy vấn tới service registry**. Thông tin thêm, xem *AWS Cloud Map Pricing* (<https://docs.aws.amazon.com/cloud-map/latest/dg/cloud-map-pricing.html>) trong *AWS Cloud Map Developer Guide*.

Amazon ECS thực hiện health check mức container và đưa chúng ra qua API custom health check của AWS Cloud Map. Việc này hiện **không tính thêm phí**. Nếu bạn cấu hình thêm **health check mạng** cho các task mở ra public, bạn **bị tính phí** cho các health check đó.

**💡 Giải thích thêm**

| Khoản | Có tính phí? (theo trang) |
|---|---|
| Route 53 hosted zone | Có |
| Truy vấn service registry / API discovery của Cloud Map | Có |
| Health check mức container do ECS quản lý | Không (hiện tại) |
| Health check mạng bổ sung cho task public | Có |

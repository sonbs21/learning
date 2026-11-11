# Amazon ECS Service Connect configuration overview

> Nguồn: **Amazon ECS Developer Guide — Amazon ECS Service Connect configuration overview**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-connect-concepts.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: **bản dịch** sang tiếng Việt, đi đúng thứ tự và đúng nội dung của trang gốc, không thêm, không bớt ý.
> - Khối **💡 Giải thích thêm**: chỉ giải thích lại những gì **chính trang này** đã nói, cho dễ hiểu hơn. Không đưa thông tin từ trang khác hay ví dụ tự đặt.
> - Tên tham số, trạng thái, tuỳ chọn console giữ nguyên tiếng Anh.
> - Tài liệu AWS được phát hành theo giấy phép **Creative Commons Attribution-ShareAlike 4.0**; bản dịch này ghi rõ nguồn ở trên.

---

## Mục lục

0. [Bảng tham số cấu hình](#0-bảng-tham-số-cấu-hình)
1. [Tổng quan các bước cấu hình](#1-tổng-quan-các-bước-cấu-hình)
2. [Cấu hình cluster](#2-cấu-hình-cluster)
3. [Cấu hình service](#3-cấu-hình-service)
4. [Cấu hình health check ban đầu](#4-cấu-hình-health-check-ban-đầu)

---

## 0. Bảng tham số cấu hình

**📘 Theo docs AWS**

Khi dùng Service Connect, có những tham số bạn cần cấu hình trong tài nguyên.

Bảng sau mô tả các tham số cấu hình cho tài nguyên Amazon ECS.

| Vị trí tham số | Loại ứng dụng | Mô tả | Bắt buộc |
|---|---|---|---|
| Task definition | Client | Không có thay đổi nào cho Service Connect trong task definition của client. | Không áp dụng |
| Task definition | Client-server | Server phải thêm trường **`name`** cho các port trong **`portMappings`** của container. Thông tin thêm, xem *portMappings* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task_definition_parameters.html#ContainerDefinition-portMappings>). | **Có** |
| Task definition | Client-server | Server có thể tuỳ chọn khai báo **giao thức ứng dụng** (ví dụ HTTP) để nhận metric theo giao thức cho ứng dụng server (ví dụ `HTTP 5xx`). | Không |
| Service definition | Client | Client service phải thêm **`serviceConnectConfiguration`** để cấu hình namespace sẽ tham gia. Namespace này phải chứa **mọi** server service mà service này cần tìm thấy. Thông tin thêm, xem *serviceConnectConfiguration* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service_definition_parameters.html#Service-serviceConnectConfiguration>). | **Có** |
| Service definition | Client-server | Server service phải thêm **`serviceConnectConfiguration`** để cấu hình tên DNS, số port, và namespace mà service có thể được truy cập. Thông tin thêm, xem *serviceConnectConfiguration* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service_definition_parameters.html#Service-serviceConnectConfiguration>). | **Có** |
| Cluster | Client | Cluster có thể thêm một **namespace Service Connect mặc định**. Service mới trong cluster **kế thừa** namespace khi Service Connect được cấu hình trong service. | Không |
| Cluster | Client-server | Không có thay đổi nào cho Service Connect trong cluster áp dụng cho server service. Task definition và service của server phải đặt cấu hình tương ứng. | Không áp dụng |

**💡 Giải thích thêm**

| | Task definition | Service definition | Cluster |
|---|---|---|---|
| **Client** | — | `serviceConnectConfiguration` (namespace) — **bắt buộc** | Namespace mặc định — tuỳ chọn |
| **Client-server** | `portMappings[].name` — **bắt buộc**; giao thức ứng dụng — tuỳ chọn | `serviceConnectConfiguration` (tên DNS, port, namespace) — **bắt buộc** | — |

Điểm bắt buộc tối thiểu: **đặt `name` cho port** (server) và **thêm `serviceConnectConfiguration`** vào service (cả client lẫn server).

---

## 1. Tổng quan các bước cấu hình

**📘 Theo docs AWS**

Các bước sau đưa ra tổng quan cách cấu hình Service Connect.

> **Important:**
> - Service Connect tạo **AWS Cloud Map service** trong account của bạn. Việc sửa các tài nguyên AWS Cloud Map này bằng cách tự đăng ký/huỷ đăng ký instance, thay đổi thuộc tính instance, hoặc xoá service có thể dẫn tới **hành vi không mong muốn** cho traffic ứng dụng hoặc các lần deploy sau.
> - Service Connect **không hỗ trợ links** trong task definition.

1. Thêm **port name** vào port mapping trong task definition. Ngoài ra, bạn có thể chỉ định **giao thức tầng 7** của ứng dụng để có thêm metric.

2. Tạo cluster với một AWS Cloud Map namespace, dùng namespace chia sẻ, hoặc tạo namespace riêng. Để tổ chức đơn giản, hãy tạo cluster với tên bạn muốn dùng cho namespace và chỉ định **cùng tên** cho namespace. Trong trường hợp này, Amazon ECS tạo một **HTTP namespace** mới với cấu hình cần thiết. Service Connect **không dùng hay tạo** DNS hosted zone trong Amazon Route 53.

3. Cấu hình service để tạo endpoint Service Connect trong namespace.

4. **Deploy** service để tạo endpoint. Amazon ECS thêm một **container Service Connect proxy** vào mỗi task, và tạo các endpoint Service Connect trong AWS Cloud Map. Container này **không** được cấu hình trong task definition, và task definition có thể được **dùng lại không cần sửa** để tạo nhiều service trong cùng namespace hoặc nhiều namespace.

5. Deploy ứng dụng client dưới dạng service để kết nối tới endpoint. Amazon ECS kết nối chúng tới endpoint Service Connect thông qua Service Connect proxy trong mỗi task.

   Ứng dụng **chỉ** dùng proxy để kết nối tới endpoint Service Connect. **Không cần cấu hình thêm** để dùng proxy. Proxy thực hiện cân bằng tải round-robin, outlier detection và retry. Thông tin thêm về proxy, xem *Service Connect proxy* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-connect-concepts-deploy.html#service-connect-concepts-proxy>).

6. Giám sát traffic đi qua Service Connect proxy trong **Amazon CloudWatch**.

**💡 Giải thích thêm**

```
1. Task definition: đặt port name (+ giao thức tầng 7 nếu muốn thêm metric)
2. Namespace: tạo cùng cluster (cùng tên → ECS tạo HTTP namespace) | dùng namespace chia sẻ | tạo riêng
3. Service: cấu hình endpoint trong namespace
4. Deploy service → ECS thêm proxy vào mỗi task + tạo endpoint trong Cloud Map
5. Deploy client service → kết nối tới endpoint qua proxy
6. Giám sát trong CloudWatch
```

Hai điều **không nên / không được** theo khung Important:

| Không | Vì sao |
|---|---|
| Tự sửa Cloud Map service do Service Connect tạo (đăng ký/huỷ instance, sửa thuộc tính, xoá service) | Có thể làm hỏng traffic hoặc các lần deploy sau |
| Dùng `links` trong task definition | Service Connect không hỗ trợ |

Bước 2 nói rõ: Service Connect **không** dùng Route 53 hosted zone.

---

## 2. Cấu hình cluster

**📘 Theo docs AWS**

Bạn có thể đặt **namespace mặc định** cho Service Connect khi tạo hoặc cập nhật cluster. Namespace bạn chỉ định làm mặc định có thể ở **cùng AWS Region và account**, hoặc ở **cùng AWS Region** và được **chia sẻ** bởi một AWS account khác qua AWS Resource Access Manager.

Nếu bạn tạo cluster và chỉ định namespace Service Connect mặc định, cluster **chờ ở trạng thái `PROVISIONING`** trong khi Amazon ECS tạo namespace. Bạn có thể thấy một **`attachment`** trong trạng thái của cluster cho biết trạng thái của namespace. Attachment **không hiển thị mặc định** trong AWS CLI, bạn phải thêm **`--include ATTACHMENTS`** để xem.

Nếu bạn muốn dùng namespace được chia sẻ với AWS account của bạn qua AWS RAM, hãy chỉ định **Amazon Resource Name (ARN)** của namespace trong cấu hình cluster. Thông tin thêm về AWS Cloud Map namespace chia sẻ, xem *Amazon ECS Service Connect with shared AWS Cloud Map namespaces* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-connect-shared-namespaces.html>).

**💡 Giải thích thêm**

| Điểm | Theo đoạn trên |
|---|---|
| Đặt khi nào | Tạo hoặc cập nhật cluster |
| Namespace hợp lệ | Cùng Region + cùng account, **hoặc** cùng Region + chia sẻ qua AWS RAM (dùng **ARN**) |
| Trong lúc tạo namespace | Cluster ở `PROVISIONING`; xem trạng thái namespace qua `attachment` |
| Xem attachment bằng CLI | Thêm `--include ATTACHMENTS` |

Ví dụ lệnh xem attachment (theo đúng tuỳ chọn trang nêu):

```bash
aws ecs describe-clusters --clusters <CLUSTER> --include ATTACHMENTS
```

---

## 3. Cấu hình service

**📘 Theo docs AWS**

Service Connect được thiết kế để cần **cấu hình tối thiểu**. Bạn cần đặt **tên cho mỗi port mapping** muốn dùng với Service Connect trong task definition. Trong service, bạn cần **bật Service Connect** và chọn một namespace trong AWS account hoặc một namespace chia sẻ để tạo **client service**. Để tạo **client-server service**, bạn cần thêm **một** cấu hình Service Connect service khớp với tên của một trong các port mapping. Amazon ECS **dùng lại số port và port name** từ task definition để định nghĩa Service Connect service và endpoint. Để ghi đè các giá trị này, bạn có thể dùng các tham số khác **Discovery**, **DNS** và **Port** trên console, hoặc lần lượt **`discoveryName`** và **`clientAliases`** trong Amazon ECS API.

**💡 Giải thích thêm**

| Muốn tạo | Cần làm (theo trang) |
|---|---|
| **Client service** | Bật Service Connect + chọn namespace (của account hoặc chia sẻ) |
| **Client-server service** | Như trên + thêm **một** Service Connect service khớp **tên một port mapping** |

Giá trị mặc định và cách ghi đè:

| Giá trị | Mặc định lấy từ | Ghi đè trên console | Ghi đè trong API |
|---|---|---|---|
| Tên dùng để discovery | Port name (task definition) | **Discovery** | `discoveryName` |
| Tên DNS, port của endpoint | Port name, số port (task definition) | **DNS**, **Port** | `clientAliases` |

---

## 4. Cấu hình health check ban đầu

**📘 Theo docs AWS**

Service Connect đảm bảo task **healthy** trước khi định tuyến traffic tới chúng. Khi một task khởi chạy (trong lúc deploy, scale, hoặc thay thế), Service Connect theo dõi sức khoẻ của task để đảm bảo nó sẵn sàng nhận traffic. Bạn **phải** định nghĩa **health check cho essential container** trong task definition để bật hành vi này.

Hành vi health check ban đầu có tính tới độ trễ có thể có trước khi task sẵn sàng nhận traffic:

- Nếu task **`HEALTHY`**, nó **ngay lập tức** sẵn sàng nhận traffic.
- Nếu sức khoẻ task là **`UNKNOWN`**, Service Connect theo cấu hình container health check (xem *Health check* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task_definition_parameters.html#container_definition_healthcheck>) của các essential container trong task để tính một **timeout**, tối đa **`8 phút`**, rồi mới cho task nhận traffic, **kể cả khi** nó vẫn còn `UNKNOWN`.
- Nếu task **`UNHEALTHY`**, Amazon ECS có thể khởi chạy task thay thế. Nếu không có task healthy nào, deployment có thể bị **rollback** tuỳ cấu hình service.

Với mọi traffic đang diễn ra, Service Connect dùng **health check thụ động** dựa trên **outlier detection** để định tuyến traffic hiệu quả.

**💡 Giải thích thêm**

Điều kiện: phải có **health check cho essential container** trong task definition.

| Trạng thái task lúc khởi chạy | Service Connect làm gì |
|---|---|
| `HEALTHY` | Nhận traffic ngay |
| `UNKNOWN` | Chờ một timeout tính từ cấu hình health check của essential container (tối đa 8 phút), rồi cho nhận traffic dù vẫn `UNKNOWN` |
| `UNHEALTHY` | ECS có thể chạy task thay thế; không còn task healthy → deployment có thể rollback |

```
Lúc task mới khởi chạy:  health check ban đầu (HEALTHY / UNKNOWN / UNHEALTHY)
Trong lúc đang chạy:     health check thụ động (outlier detection)
```

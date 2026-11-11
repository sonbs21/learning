# Amazon ECS Service Connect components

> Nguồn: **Amazon ECS Developer Guide — Amazon ECS Service Connect components**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-connect-concepts-deploy.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: **bản dịch** sang tiếng Việt, đi đúng thứ tự và đúng nội dung của trang gốc, không thêm, không bớt ý.
> - Khối **💡 Giải thích thêm**: chỉ giải thích lại những gì **chính trang này** đã nói, cho dễ hiểu hơn. Không đưa thông tin từ trang khác hay ví dụ tự đặt.
> - Tên tham số, thuật ngữ Service Connect, phiên bản giữ nguyên tiếng Anh.
> - Tài liệu AWS được phát hành theo giấy phép **Creative Commons Attribution-ShareAlike 4.0**; bản dịch này ghi rõ nguồn ở trên.

---

## Mục lục

0. [Phần mở đầu: cách Service Connect được áp dụng](#0-phần-mở-đầu-cách-service-connect-được-áp-dụng)
1. [Networking](#1-networking)
2. [Service Connect proxy](#2-service-connect-proxy)
   - [2.1 Tài nguyên cho proxy](#21-tài-nguyên-cho-proxy)
   - [2.2 Cấu hình proxy](#22-cấu-hình-proxy)
3. [Những điều cần cân nhắc](#3-những-điều-cần-cân-nhắc)

---

## 0. Phần mở đầu: cách Service Connect được áp dụng

**📘 Theo docs AWS**

Khi dùng Amazon ECS Service Connect, bạn cấu hình mỗi Amazon ECS service để chạy một **ứng dụng server** nhận network request (**client-server service**), hoặc chạy một **ứng dụng client** gửi request (**client service**).

Khi chuẩn bị bắt đầu dùng Service Connect, hãy **bắt đầu với một client-server service**. Bạn có thể thêm cấu hình Service Connect vào service mới hoặc service có sẵn. Amazon ECS tạo một **endpoint Service Connect** trong namespace. Ngoài ra, Amazon ECS tạo một **deployment mới** trong service để thay các task đang chạy.

Task hiện có và các ứng dụng khác có thể tiếp tục kết nối tới các endpoint hiện có và ứng dụng bên ngoài. Nếu một client-server service thêm task bằng cách scale out, các kết nối **mới** từ client sẽ được cân bằng giữa **mọi** task. Nếu client-server service được cập nhật, các kết nối mới từ client sẽ được cân bằng giữa các task của **phiên bản mới**.

Task hiện có **không thể** phân giải và kết nối tới endpoint mới. **Chỉ** task **mới** có cấu hình Service Connect trong **cùng namespace** và **bắt đầu chạy sau** deployment này mới phân giải và kết nối được tới endpoint này.

Điều này nghĩa là người vận hành ứng dụng **client** quyết định khi nào cấu hình ứng dụng của họ thay đổi, dù người vận hành ứng dụng **server** có thể đổi cấu hình bất cứ lúc nào. Danh sách endpoint trong namespace có thể thay đổi mỗi khi bất kỳ service nào trong namespace được deploy. Task hiện có và task thay thế tiếp tục hoạt động giống như sau lần deploy gần nhất.

Xét các ví dụ sau.

**Thứ nhất**, giả sử bạn tạo một ứng dụng mở ra internet công cộng trong **một** AWS CloudFormation template và **một** CloudFormation stack. Phần public discovery và khả năng truy cập công khai nên được CloudFormation tạo **cuối cùng**, bao gồm frontend client service. Các service cần được tạo theo thứ tự này để tránh một khoảng thời gian frontend client service đang chạy và public, nhưng backend thì chưa. Việc này loại bỏ thông báo lỗi gửi tới công chúng trong khoảng thời gian đó. Trong AWS CloudFormation, bạn phải dùng **`dependsOn`** để báo cho CloudFormation rằng nhiều Amazon ECS service **không được** tạo song song hay đồng thời. Bạn nên thêm `dependsOn` vào frontend client service cho **mỗi** backend client-server service mà task client kết nối tới.

**Thứ hai**, giả sử đã có một frontend service **không có** cấu hình Service Connect. Các task đang kết nối tới một backend service có sẵn. Hãy thêm cấu hình Service Connect dạng client-server vào **backend service trước**, dùng **cùng tên** trong **DNS** hoặc `clientAlias` mà frontend đang dùng. Việc này tạo một deployment mới, nên có thể dùng mọi cơ chế phát hiện rollback của deployment hoặc AWS Management Console, AWS CLI, AWS SDK và các cách khác để rollback và đưa backend service về deployment và cấu hình trước đó. Nếu bạn hài lòng với hiệu năng và hành vi của backend service, hãy thêm cấu hình Service Connect dạng client hoặc client-server vào **frontend service**. Chỉ các task trong deployment mới dùng Service Connect proxy được thêm vào các task mới đó. Nếu có vấn đề với cấu hình này, bạn có thể rollback và trở về cấu hình trước bằng cơ chế phát hiện rollback của deployment hoặc AWS Management Console, AWS CLI, AWS SDK và các cách khác để rollback và đưa backend service về deployment và cấu hình trước đó. Nếu bạn dùng một hệ thống service discovery khác dựa trên DNS thay vì Service Connect, mọi ứng dụng frontend hoặc client bắt đầu dùng endpoint mới và cấu hình endpoint đã thay đổi **sau khi cache DNS cục bộ hết hạn**, thường mất **nhiều giờ**.

**💡 Giải thích thêm**

Ai thấy endpoint mới:

| Task | Phân giải được endpoint mới? |
|---|---|
| Task **hiện có** (chạy trước deployment) | ❌ |
| Task **mới**, có Service Connect, **cùng namespace**, chạy **sau** deployment | ✅ |

```
Server đổi cấu hình (bất cứ lúc nào)  →  endpoint trong namespace thay đổi
Client chỉ thấy thay đổi khi CLIENT được deploy lại  →  người vận hành client quyết định thời điểm
```

Hai ví dụ trên trang:

| Ví dụ | Thứ tự làm |
|---|---|
| **1. Ứng dụng mới trong một CloudFormation stack** | Tạo backend trước, frontend (public) **cuối cùng**; thêm `dependsOn` vào frontend cho **từng** backend nó gọi tới |
| **2. Chuyển hệ thống có sẵn sang Service Connect** | (1) Thêm cấu hình client-server vào **backend** — giữ cùng tên DNS / `clientAlias` frontend đang dùng; kiểm tra, rollback nếu cần. (2) Khi ổn → thêm cấu hình client hoặc client-server vào **frontend**; rollback nếu cần |

So sánh trang đưa ra: Service Connect áp dụng thay đổi **qua deployment**; service discovery dựa trên DNS chờ **cache DNS hết hạn** (thường nhiều giờ).

Câu "Việc này tạo một deployment mới, nên…" trong ví dụ thứ hai trên trang gốc bị cụt (thiếu vế "bạn có thể dùng"); bản dịch bổ sung chữ "có thể dùng" cho câu trọn nghĩa, theo ý của câu tương tự ở cuối đoạn.

---

## 1. Networking

**📘 Theo docs AWS**

Mặc định, Service Connect proxy lắng nghe trên **`containerPort`** từ port mapping của task definition. Quy tắc security group phải cho phép traffic **vào (ingress)** port này từ các subnet nơi client chạy.

Kể cả khi bạn đặt số port trong cấu hình Service Connect service, việc đó **không** đổi port mà Service Connect proxy của client-server service lắng nghe. Khi bạn đặt số port này, Amazon ECS đổi **port của endpoint** mà client service kết nối tới, trên Service Connect proxy **bên trong các task client** đó. Proxy trong client service kết nối tới proxy trong client-server service bằng **`containerPort`**.

Nếu bạn muốn đổi port mà Service Connect proxy lắng nghe, hãy đổi **`ingressPortOverride`** trong cấu hình Service Connect của client-server service. Nếu đổi port này, bạn phải cho phép traffic vào trên port này cho traffic tới service đó.

Traffic mà ứng dụng gửi tới Amazon ECS service được cấu hình Service Connect yêu cầu Amazon VPC và subnet có quy tắc **route table** và **network ACL** cho phép các port **`containerPort`** và **`ingressPortOverride`** bạn đang dùng.

Bạn có thể dùng Service Connect để gửi traffic **giữa các VPC**. Các yêu cầu về route table, network ACL và security group áp dụng cho **cả hai** VPC.

Ví dụ, hai cluster tạo task ở hai VPC khác nhau. Một service ở mỗi cluster được cấu hình dùng **cùng namespace**. Ứng dụng trong hai service này có thể phân giải mọi endpoint trong namespace **mà không cần cấu hình DNS của VPC**. Tuy nhiên, các proxy **không kết nối được** trừ khi VPC peering, route table của VPC hoặc subnet, và network ACL của VPC cho phép traffic trên các port `containerPort` và `ingressPortOverride`.

Với task dùng network mode **`bridge`**, bạn phải tạo một security group có inbound rule cho phép traffic trên **dải port động phía trên**. Sau đó, gán security group đó cho **mọi EC2 instance** trong cluster Service Connect.

**💡 Giải thích thêm**

```
Task client                                     Task client-server
┌──────────────────────────┐                    ┌──────────────────────────────┐
│ app ─► proxy             │                    │ proxy lắng nghe trên:         │
│   (port endpoint client  │ ── containerPort ─►│   containerPort (mặc định)    │
│    gọi tới = port đặt    │   (hoặc            │   hoặc ingressPortOverride    │
│    trong Service Connect │ ingressPortOverride)│ ─► container ứng dụng         │
│    service config)       │                    │                              │
└──────────────────────────┘                    └──────────────────────────────┘
```

| Bạn đặt | Đổi cái gì |
|---|---|
| Số port trong cấu hình Service Connect service | Port của **endpoint** mà client gọi tới (trên proxy phía client) — **không** đổi port proxy server lắng nghe |
| `ingressPortOverride` (client-server service) | Port mà **proxy phía server** lắng nghe |

Cần mở trong mạng (theo trang):

| Thành phần | Port cần cho phép |
|---|---|
| Security group (phía server) | `containerPort` (hoặc `ingressPortOverride` nếu đổi), từ subnet của client |
| Route table, network ACL | `containerPort`, `ingressPortOverride` |
| Giữa hai VPC | Như trên cho **cả hai** VPC + VPC peering / route |
| Mode `bridge` | Security group cho dải port động phía trên, gán cho mọi EC2 instance |

Giữa các VPC: **tên** phân giải được ngay (cùng namespace), nhưng **kết nối** chỉ thông khi mạng cho phép.

---

## 2. Service Connect proxy

**📘 Theo docs AWS**

Nếu bạn tạo hoặc cập nhật service có cấu hình Service Connect, Amazon ECS thêm một **container mới** vào **mỗi task mới** khi task khởi động. Mẫu dùng một container riêng như vậy gọi là **`sidecar`**. Container này **không có trong task definition** và bạn **không cấu hình được** nó. Amazon ECS quản lý cấu hình container trong service. Điều này cho phép bạn dùng lại cùng task definition giữa nhiều service, namespace, và cả task không dùng Service Connect.

### 2.1 Tài nguyên cho proxy

**📘 Theo docs AWS**

- Với task definition, bạn **phải** đặt tham số CPU và memory.

  Chúng tôi khuyến nghị thêm **256 CPU unit** và **ít nhất 64 MiB** memory vào CPU và memory của task cho container Service Connect proxy. Trên **AWS Fargate**, memory thấp nhất bạn có thể đặt là **512 MiB**. Trên **Amazon EC2**, memory của task definition là **bắt buộc**.

- Với service, bạn đặt cấu hình log trong cấu hình Service Connect.

- Nếu bạn dự kiến task trong service này nhận **hơn 500 request mỗi giây** ở tải đỉnh, chúng tôi khuyến nghị thêm **512 CPU unit** vào CPU của task trong task definition này cho container Service Connect proxy.

- Nếu bạn dự kiến tạo **hơn 100 Service Connect service** trong namespace, hoặc **tổng cộng 2000 task** trên mọi Amazon ECS service trong namespace, chúng tôi khuyến nghị thêm **128 MiB** memory vào memory của task cho container Service Connect proxy. Bạn nên làm việc này trong **mọi** task definition được dùng bởi mọi Amazon ECS service trong namespace.

**💡 Giải thích thêm**

| Trường hợp | Thêm cho proxy (theo trang) |
|---|---|
| Mặc định | +256 CPU unit, +≥ 64 MiB memory |
| Tải đỉnh > 500 request/giây mỗi task | +512 CPU unit |
| > 100 Service Connect service **hoặc** > 2000 task trong namespace | +128 MiB memory, ở **mọi** task definition trong namespace |

| Hạ tầng | Lưu ý memory |
|---|---|
| Fargate | Tối thiểu 512 MiB |
| EC2 | Memory mức task definition là bắt buộc |

### 2.2 Cấu hình proxy

**📘 Theo docs AWS**

Ứng dụng kết nối tới proxy trong container sidecar **cùng task** với ứng dụng. Amazon ECS cấu hình task và container sao cho ứng dụng **chỉ** kết nối tới proxy khi ứng dụng kết nối tới **tên endpoint trong cùng namespace**. Mọi traffic khác **không** dùng proxy. Traffic khác gồm địa chỉ IP trong cùng VPC, endpoint dịch vụ AWS, và traffic bên ngoài.

**Cân bằng tải (Load balancing)**

Service Connect cấu hình proxy dùng chiến lược **round-robin** để cân bằng tải giữa các task trong một endpoint Service Connect. Proxy cục bộ trong task nơi kết nối xuất phát sẽ chọn một trong các task của client-server service cung cấp endpoint.

Ví dụ, xét một task chạy WordPress trong một service được cấu hình là *client service* trong namespace tên *local*. Có một service khác với 2 task chạy database MySQL. Service này được cấu hình cung cấp endpoint tên `mysql` qua Service Connect trong cùng namespace. Trong task WordPress, ứng dụng WordPress kết nối tới database bằng tên endpoint. Kết nối tới tên này đi tới proxy chạy trong container sidecar cùng task. Sau đó, proxy có thể kết nối tới một trong hai task MySQL theo chiến lược round-robin.

Chiến lược cân bằng tải: **round-robin**

**Phát hiện bất thường (Outlier detection)**

Tính năng này dùng dữ liệu proxy có về các kết nối lỗi trước đó để tránh gửi kết nối mới tới các host đã có kết nối lỗi. Service Connect cấu hình tính năng outlier detection của proxy để cung cấp **health check thụ động (passive)**.

Dùng ví dụ trước, proxy có thể kết nối tới một trong hai task MySQL. Nếu proxy đã tạo nhiều kết nối tới một task MySQL cụ thể, và **5 kết nối trở lên thất bại trong 30 giây gần nhất**, thì proxy **tránh** task MySQL đó trong **30 tới 300 giây**.

**Thử lại (Retries)**

Service Connect cấu hình proxy **thử lại** kết nối đi qua proxy mà thất bại, và lần thử thứ hai **tránh dùng host** của lần kết nối trước. Điều này đảm bảo mỗi kết nối qua Service Connect không thất bại vì lý do nhất thời.

Số lần thử lại: **2**

**Thời gian chờ (Timeout)**

Service Connect cấu hình proxy chờ một khoảng thời gian tối đa để ứng dụng client-server phản hồi. Giá trị timeout mặc định là **15 giây**, nhưng có thể cập nhật.

Tham số tuỳ chọn:

**idleTimeout** ‐ Khoảng thời gian (giây) một kết nối được giữ hoạt động khi **nhàn rỗi**. Giá trị `0` **tắt** `idleTimeout`.

`idleTimeout` mặc định cho `HTTP`/`HTTP2`/`GRPC` là **5 phút**.

`idleTimeout` mặc định cho `TCP` là **1 giờ**.

**perRequestTimeout** ‐ Khoảng thời gian chờ upstream phản hồi **đầy đủ** cho **mỗi request**. Giá trị `0` **tắt** `perRequestTimeout`. Chỉ đặt được khi `appProtocol` của container ứng dụng là `HTTP`/`HTTP2`/`GRPC`. Mặc định là **15 giây**.

> **Note:** Nếu `idleTimeout` được đặt **nhỏ hơn** `perRequestTimeout`, kết nối sẽ đóng khi đạt `idleTimeout` chứ không phải `perRequestTimeout`.

**💡 Giải thích thêm**

Traffic nào đi qua proxy:

| Traffic | Qua proxy? |
|---|---|
| Tới **tên endpoint** cùng namespace | ✅ |
| Tới IP trong cùng VPC | ❌ |
| Tới endpoint dịch vụ AWS | ❌ |
| Traffic ra bên ngoài | ❌ |

Hành vi proxy theo trang:

| Tính năng | Giá trị / hành vi |
|---|---|
| Load balancing | Round-robin giữa các task của endpoint |
| Outlier detection (health check thụ động) | ≥ 5 kết nối lỗi trong 30 giây → tránh task đó 30–300 giây |
| Retries | 2 lần; lần thử lại tránh host vừa lỗi |
| Timeout mặc định | 15 giây (cập nhật được) |
| `idleTimeout` | HTTP/HTTP2/GRPC: 5 phút; TCP: 1 giờ; `0` = tắt |
| `perRequestTimeout` | 15 giây; chỉ cho HTTP/HTTP2/GRPC; `0` = tắt |

```
idleTimeout < perRequestTimeout  →  kết nối đóng theo idleTimeout (cái nhỏ hơn thắng)
```

---

## 3. Những điều cần cân nhắc

**📘 Theo docs AWS**

Cân nhắc các điểm sau khi dùng Service Connect:

- Task chạy trên Fargate phải dùng **Fargate Linux platform version `1.4.0` trở lên** để dùng Service Connect.
- Phiên bản **Amazon ECS agent** trên container instance phải là **`1.67.2` trở lên**.
- Container instance phải chạy **Amazon ECS-optimized Amazon Linux 2023 AMI** phiên bản **`20230428`** trở lên, hoặc **Amazon ECS-optimized Amazon Linux 2 AMI** phiên bản **`2.0.20221115`** để dùng Service Connect. Các phiên bản này có **Service Connect agent** bên cạnh Amazon ECS container agent. Thông tin thêm về Service Connect agent, xem *Amazon ECS Service Connect Agent* (<https://github.com/aws/amazon-ecs-service-connect-agent>) trên GitHub.
- Container instance phải có quyền **`ecs:Poll`** cho tài nguyên `arn:aws:ecs:region:0123456789012:task-set/cluster/*`. Nếu bạn dùng `ecsInstanceRole`, bạn không cần thêm quyền. Managed policy **`AmazonEC2ContainerServiceforEC2Role`** có các quyền cần thiết. Thông tin thêm, xem *Amazon ECS container instance IAM role* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/instance_IAM_role.html>).
- Task dùng network mode `bridge` và dùng Service Connect **không hỗ trợ** tham số container definition **`hostname`**.
- Task definition phải đặt **task memory limit** để dùng Service Connect. Thông tin thêm, xem *Service Connect proxy* (mục 2).
- Task definition đặt **container memory limit** không được hỗ trợ.

  Bạn có thể đặt container memory limit cho container, nhưng phải đặt task memory limit **lớn hơn tổng** các container memory limit. Phần CPU và memory dư trong task limit không được cấp cho container limit sẽ được container Service Connect proxy và các container khác không đặt container limit dùng. Thông tin thêm, xem *Service Connect proxy* (mục 2).
- Bạn có thể cấu hình Service Connect dùng bất kỳ AWS Cloud Map namespace nào trong **cùng Region**, thuộc **cùng AWS account** hoặc được **chia sẻ** với account của bạn qua AWS Resource Access Manager. Thông tin thêm về namespace chia sẻ, xem *Amazon ECS Service Connect with shared AWS Cloud Map namespaces* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-connect-shared-namespaces.html>).
- Mỗi service chỉ thuộc **một** namespace.
- Chỉ hỗ trợ task **do service tạo ra**.
- Mọi endpoint phải **duy nhất** trong một namespace.
- Mọi discovery name phải **duy nhất** trong một namespace.
- Bạn phải **deploy lại** service hiện có trước khi ứng dụng phân giải được endpoint mới. Endpoint mới được thêm vào namespace sau lần deploy gần nhất sẽ không được thêm vào cấu hình task. Thông tin thêm, xem *Amazon ECS Service Connect components* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-connect-concepts-deploy.html>).
- Service Connect **không xoá** namespace khi cluster bị xoá. Bạn phải xoá namespace trong AWS Cloud Map.
- Traffic Application Load Balancer **mặc định đi qua** Service Connect agent ở network mode `awsvpc`. Nếu muốn traffic không phải của service **bỏ qua** Service Connect agent, dùng tham số **`ingressPortOverride`** (<https://docs.aws.amazon.com/AmazonECS/latest/APIReference/API_ServiceConnectService.html>) trong cấu hình Service Connect service.
- Service Connect với **TLS không hỗ trợ** network mode `bridge`. Chỉ hỗ trợ network mode `awsvpc`.
- Ở mode `awsvpc`, Service Connect proxy chuyển traffic tới localhost IPv4 **`127.0.0.1`** cho service ở cấu hình IPv4-only và dualstack. Với service ở cấu hình **IPv6-only**, proxy chuyển traffic tới localhost IPv6 **`::1`**. Chúng tôi khuyến nghị cấu hình ứng dụng **lắng nghe cả hai localhost** để chuyển đổi liền mạch khi service được cập nhật từ IPv4-only hoặc dualstack sang IPv6-only. Thông tin thêm, xem *Amazon ECS task networking options for EC2* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task-networking.html>) và *Amazon ECS task networking options for Fargate* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/fargate-task-networking.html>).

**Service Connect không hỗ trợ:**

- Windows container
- HTTP 1.0
- Standalone task
- Service dùng blue/green chạy bằng CodeDeploy và kiểu deploy external
- Container instance `External` cho Amazon ECS Anywhere
- PPv2
- FIPS mode

**💡 Giải thích thêm**

Yêu cầu phiên bản:

| Hạ tầng | Yêu cầu |
|---|---|
| Fargate | Linux platform version ≥ `1.4.0` |
| EC2 | ECS agent ≥ `1.67.2`; AMI AL2023 ≥ `20230428` hoặc AL2 `2.0.20221115` |
| EC2 IAM | `ecs:Poll` trên `task-set` — có sẵn trong `AmazonEC2ContainerServiceforEC2Role` / `ecsInstanceRole` |

Memory khi dùng Service Connect:

```
task memory limit  >  tổng container memory limit
                     └─ phần dư dành cho proxy và container không đặt limit
```

Trên trang gốc, câu "Task definition đặt container memory limit không được hỗ trợ" đi ngay trước câu "bạn **có thể** đặt container memory limit, nhưng task memory limit phải lớn hơn tổng". Đọc cả hai câu cùng nhau: container memory limit **dùng được**, với điều kiện task memory limit **lớn hơn tổng** các container limit.

Những điểm "duy nhất / một":

| Phạm vi | Quy tắc |
|---|---|
| Service ↔ namespace | Mỗi service **một** namespace |
| Endpoint | Duy nhất trong namespace |
| Discovery name | Duy nhất trong namespace |

Hành vi theo network mode:

| | `awsvpc` | `bridge` |
|---|---|---|
| TLS | ✅ | ❌ |
| `hostname` của container | (trang không nêu) | ❌ |
| Traffic ALB | Mặc định qua Service Connect agent; dùng `ingressPortOverride` để bỏ qua | — |
| Localhost proxy chuyển tới | `127.0.0.1` (IPv4-only, dualstack); `::1` (IPv6-only) | — |

Lời khuyên localhost theo trang: cho ứng dụng **lắng nghe cả `127.0.0.1` và `::1`** để chuyển sang IPv6-only không bị gián đoạn.

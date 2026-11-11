# Connect Amazon ECS applications to the internet

> Nguồn: **Amazon ECS Developer Guide — Connect Amazon ECS applications to the internet**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/networking-outbound.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: **bản dịch** sang tiếng Việt, đi đúng thứ tự và đúng nội dung của trang gốc, không thêm, không bớt ý.
> - Khối **💡 Giải thích thêm**: chỉ giải thích lại những gì **chính trang này** đã nói, cho dễ hiểu hơn. Không đưa thông tin từ trang khác hay ví dụ tự đặt.
> - Tên dịch vụ, network mode, tuỳ chọn console giữ nguyên tiếng Anh.
> - Tài liệu AWS được phát hành theo giấy phép **Creative Commons Attribution-ShareAlike 4.0**; bản dịch này ghi rõ nguồn ở trên.

---

## Mục lục

0. [Phần mở đầu](#0-phần-mở-đầu)
1. [Public subnet và internet gateway](#1-public-subnet-và-internet-gateway)
2. [Private subnet và NAT gateway](#2-private-subnet-và-nat-gateway)
3. [So sánh hai cách](#3-so-sánh-hai-cách)

---

## 0. Phần mở đầu

**📘 Theo docs AWS**

Hầu hết ứng dụng container đều có ít nhất một số thành phần cần truy cập **ra (outbound)** internet. Ví dụ, backend của một ứng dụng di động cần truy cập ra ngoài để gửi push notification.

Amazon Virtual Private Cloud có **hai** cách chính để hỗ trợ giao tiếp giữa VPC của bạn và internet.

---

## 1. Public subnet và internet gateway

**📘 Theo docs AWS**

*(Hình trên trang gốc: sơ đồ kiến trúc một public subnet kết nối với internet gateway.)*

Khi bạn dùng public subnet có route tới internet gateway, ứng dụng container có thể chạy trên một host bên trong VPC, ở public subnet. Host chạy container được gán một **địa chỉ IP public**. Địa chỉ IP public này **có thể định tuyến được từ internet**. Thông tin thêm, xem *Internet gateways* (<https://docs.aws.amazon.com/vpc/latest/userguide/VPC_Internet_Gateway.html>) trong *Amazon VPC User Guide*.

Kiến trúc mạng này cho phép host chạy ứng dụng giao tiếp **trực tiếp** với các host khác trên internet. Giao tiếp là **hai chiều**. Nghĩa là bạn không chỉ có thể mở kết nối ra tới bất kỳ host nào trên internet, mà các host khác trên internet cũng có thể thử kết nối tới host của bạn. Vì vậy, bạn cần chú ý kỹ tới security group và quy tắc firewall. Điều này đảm bảo các host khác trên internet không thể mở những kết nối mà bạn không muốn được mở.

Ví dụ, nếu ứng dụng chạy trên Amazon EC2, hãy đảm bảo **port 22** dành cho truy cập SSH **không mở**. Nếu không, instance của bạn có thể liên tục nhận các lần thử kết nối SSH từ bot độc hại trên internet. Các bot này dò quét các địa chỉ IP public. Khi tìm thấy một port SSH đang mở, chúng thử brute-force mật khẩu để truy cập instance. Vì lý do này, nhiều tổ chức hạn chế dùng public subnet và muốn đặt phần lớn, nếu không phải toàn bộ, tài nguyên trong private subnet.

Dùng public subnet cho mạng phù hợp với ứng dụng public cần **băng thông lớn** hoặc **độ trễ tối thiểu**. Các trường hợp áp dụng gồm dịch vụ streaming video và game.

Cách kết nối mạng này được hỗ trợ cả khi dùng Amazon ECS trên Amazon EC2 và khi dùng trên AWS Fargate.

- **Amazon EC2** — Bạn có thể launch EC2 instance trên public subnet. Amazon ECS dùng các EC2 instance này làm capacity của cluster, và mọi container chạy trên instance có thể dùng địa chỉ IP public của host bên dưới để kết nối ra ngoài. Điều này áp dụng cho cả network mode `host` và `bridge`. Tuy nhiên, network mode `awsvpc` **không** cấp địa chỉ IP public cho task ENI. Do đó, chúng không thể dùng trực tiếp internet gateway.

- **Fargate** — Khi tạo Amazon ECS service, chỉ định public subnet cho cấu hình mạng của service và dùng tuỳ chọn **Assign public IP address**. Mỗi task Fargate được đặt trong public subnet và có địa chỉ IP public riêng để giao tiếp trực tiếp với internet.

**💡 Giải thích thêm**

```
Internet ⇄ Internet gateway ⇄ Public subnet ⇄ Host/task có IP public
          (hai chiều: ra được, và người khác cũng thử vào được)
```

Ai dùng được IP public theo trang:

| Hạ tầng / network mode | Ra internet qua internet gateway |
|---|---|
| EC2 — `host`, `bridge` | Có — container dùng IP public của **host** |
| EC2 — `awsvpc` | **Không** — task ENI không được cấp IP public |
| Fargate (bật **Assign public IP address**) | Có — **mỗi task** có IP public riêng |

Rủi ro trang nhấn mạnh: vì giao tiếp **hai chiều**, phải siết security group và firewall; ví dụ **không mở port 22** — bot quét IP public và brute-force SSH.

---

## 2. Private subnet và NAT gateway

**📘 Theo docs AWS**

*(Hình trên trang gốc: sơ đồ kiến trúc một private subnet kết nối với NAT gateway.)*

Khi dùng private subnet và NAT gateway, bạn có thể chạy ứng dụng container trên host nằm trong private subnet. Như vậy, host này có **địa chỉ IP private** định tuyến được **bên trong VPC**, nhưng **không** định tuyến được **từ internet**. Nghĩa là các host khác trong VPC có thể kết nối tới host bằng IP private của nó, nhưng các host trên internet **không thể** tạo kết nối **vào (inbound)** tới host.

Với private subnet, bạn có thể dùng **Network Address Translation (NAT) gateway** để cho host trong private subnet kết nối ra internet. Các host trên internet nhận một kết nối đến trông như xuất phát từ **địa chỉ IP public của NAT gateway**, vốn nằm trong một public subnet. NAT gateway chịu trách nhiệm làm **cầu nối** giữa internet và private subnet. Cấu hình này thường được ưu tiên vì lý do bảo mật, vì nó có nghĩa VPC của bạn được bảo vệ khỏi truy cập trực tiếp của kẻ tấn công trên internet. Thông tin thêm, xem *NAT gateways* (<https://docs.aws.amazon.com/vpc/latest/userguide/vpc-nat-gateway.html>) trong *Amazon VPC User Guide*.

Cách kết nối mạng private này phù hợp với các tình huống bạn muốn **bảo vệ container khỏi truy cập trực tiếp từ bên ngoài**. Các tình huống áp dụng gồm hệ thống xử lý thanh toán hoặc container lưu dữ liệu và mật khẩu người dùng. Bạn bị **tính phí** khi tạo và dùng NAT gateway trong account. Phí theo giờ sử dụng và phí xử lý dữ liệu của NAT gateway cũng được áp dụng. Vì mục đích dự phòng, bạn nên có **một NAT gateway ở mỗi Availability Zone**. Như vậy, việc mất khả dụng của một Availability Zone không làm ảnh hưởng tới kết nối ra ngoài. Vì vậy, nếu bạn có workload nhỏ, việc dùng private subnet và NAT gateway có thể hiệu quả chi phí hơn.

Cách kết nối mạng này được hỗ trợ cả khi dùng Amazon ECS trên Amazon EC2 và khi dùng trên AWS Fargate.

- **Amazon EC2** — Bạn có thể launch EC2 instance trên private subnet. Container chạy trên các EC2 host này dùng mạng của host bên dưới, và request ra ngoài đi qua NAT gateway.

- **Fargate** — Khi tạo Amazon ECS service, chỉ định private subnet cho cấu hình mạng của service và **không** dùng tuỳ chọn **Assign public IP address**. Mỗi task Fargate được đặt trong private subnet. Traffic ra ngoài của task được định tuyến qua bất kỳ NAT gateway nào bạn đã gắn với private subnet đó.

**💡 Giải thích thêm**

```
Host/task (IP private) → Private subnet → NAT gateway (ở public subnet, có IP public) → Internet
Internet  ✗→  không tạo được kết nối vào host/task
```

| | Theo đoạn trên |
|---|---|
| Trong VPC | Host khác kết nối được qua IP private |
| Từ internet | Không kết nối vào được |
| Ra internet | Qua NAT gateway; bên ngoài thấy IP public của NAT gateway |
| Chi phí | Phí tạo/dùng NAT gateway, phí theo giờ, phí xử lý dữ liệu |
| Dự phòng | Một NAT gateway mỗi Availability Zone |
| EC2 | Container dùng mạng của host, ra ngoài qua NAT |
| Fargate | Private subnet, **không** bật Assign public IP address |

Câu cuối của đoạn chi phí trên trang gốc nói workload nhỏ thì dùng private subnet + NAT gateway "có thể hiệu quả chi phí hơn". Câu này đứng ngay sau phần nói NAT gateway tính phí và nên có một cái mỗi AZ, nên đọc theo mạch văn thì có vẻ ngược ý (chi phí NAT là gánh nặng hơn với workload nhỏ). Bản dịch ở trên giữ đúng như câu gốc; khi dùng để ra quyết định, nên đối chiếu thêm.

---

## 3. So sánh hai cách

**💡 Giải thích thêm**

Tổng hợp từ hai mục trên của chính trang:

| | Public subnet + internet gateway | Private subnet + NAT gateway |
|---|---|---|
| IP của host/task | Public, định tuyến được từ internet | Private, chỉ định tuyến được trong VPC |
| Chiều giao tiếp với internet | Hai chiều | Chỉ ra ngoài |
| Rủi ro | Bị quét, bị thử kết nối (vd SSH port 22) | VPC được bảo vệ khỏi truy cập trực tiếp |
| Phù hợp | Ứng dụng public cần băng thông lớn, độ trễ thấp (streaming video, game) | Bảo vệ container (xử lý thanh toán, lưu dữ liệu/mật khẩu người dùng) |
| Chi phí NAT | — | Có (theo giờ + xử lý dữ liệu), nên một NAT mỗi AZ |
| EC2 | `host`, `bridge` dùng IP public của host; `awsvpc` không dùng được | Container dùng mạng host, ra qua NAT |
| Fargate | Public subnet + bật **Assign public IP address** | Private subnet + **không** bật Assign public IP address |

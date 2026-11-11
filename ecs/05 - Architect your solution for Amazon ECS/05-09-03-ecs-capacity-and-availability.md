# Amazon ECS capacity and availability

> Nguồn: **Amazon ECS Developer Guide — Amazon ECS capacity and availability**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/capacity-availability-best-practice.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: **bản dịch** sang tiếng Việt, đi đúng thứ tự và đúng nội dung của trang gốc, không thêm, không bớt ý.
> - Khối **💡 Giải thích thêm**: chỉ giải thích lại những gì **chính trang này** đã nói, cho dễ hiểu hơn. Không đưa thông tin từ trang khác hay ví dụ tự đặt.
> - Tên dịch vụ, kiểu scaling, network mode, loại volume giữ nguyên tiếng Anh.
> - Tài liệu AWS được phát hành theo giấy phép **Creative Commons Attribution-ShareAlike 4.0**; bản dịch này ghi rõ nguồn ở trên.

---

## Mục lục

0. [Phần mở đầu](#0-phần-mở-đầu)
1. [Tối đa hoá tốc độ scale](#1-tối-đa-hoá-tốc-độ-scale)
2. [Xử lý cú sốc nhu cầu (demand shock)](#2-xử-lý-cú-sốc-nhu-cầu-demand-shock)

---

## 0. Phần mở đầu

**📘 Theo docs AWS**

**Tính sẵn sàng** của ứng dụng rất quan trọng để mang lại trải nghiệm không lỗi và giảm thiểu độ trễ. Tính sẵn sàng phụ thuộc vào việc có tài nguyên **truy cập được** và có **đủ capacity** để đáp ứng nhu cầu. AWS cung cấp nhiều cơ chế để quản lý tính sẵn sàng. Với ứng dụng chạy trên Amazon ECS, chúng gồm **autoscaling** và **Availability Zone (AZ)**. Autoscaling quản lý số task hoặc instance dựa trên metric bạn định nghĩa, còn Availability Zone cho phép bạn chạy ứng dụng ở các địa điểm biệt lập nhưng gần nhau về địa lý.

Cũng như kích thước task, capacity và tính sẵn sàng có những **đánh đổi** bạn phải cân nhắc. Lý tưởng nhất, capacity khớp hoàn hảo với nhu cầu. Luôn có vừa đủ capacity để phục vụ request và xử lý job, đáp ứng **Service Level Objective (SLO)** gồm độ trễ thấp và tỉ lệ lỗi thấp. Capacity không bao giờ quá cao, gây tốn chi phí; cũng không bao giờ quá thấp, gây độ trễ và tỉ lệ lỗi cao.

Autoscaling là một quá trình **có độ trễ**. Trước tiên, CloudWatch phải nhận metric thời gian thực. Sau đó, CloudWatch cần tổng hợp chúng để phân tích, việc này có thể mất tới vài phút tuỳ độ chi tiết của metric. CloudWatch so sánh metric với ngưỡng alarm để xác định thiếu hay thừa tài nguyên. Để tránh mất ổn định, bạn nên cấu hình alarm yêu cầu ngưỡng bị vượt **trong vài phút** trước khi alarm kích hoạt. Việc cấp task mới và kết thúc task không còn cần cũng mất thời gian.

Vì các độ trễ tiềm ẩn này trong hệ thống, bạn nên duy trì một khoảng **dư (headroom)** bằng cách **cấp dư (over-provisioning)**. Cấp dư giúp đáp ứng các đợt tăng nhu cầu ngắn hạn. Nó cũng giúp ứng dụng phục vụ thêm request mà không chạm mức bão hoà. Theo thực hành tốt, bạn có thể đặt mục tiêu scale ở khoảng **60–80% utilization**. Điều này giúp ứng dụng xử lý tốt hơn các đợt nhu cầu tăng thêm trong khi capacity bổ sung vẫn đang được cấp.

Một lý do khác chúng tôi khuyến nghị cấp dư là để bạn phản ứng nhanh với **sự cố Availability Zone**. AWS khuyến nghị workload production được phục vụ từ **nhiều Availability Zone**. Đó là vì nếu một Availability Zone gặp sự cố, các task đang chạy ở các Availability Zone còn lại vẫn phục vụ được nhu cầu. Nếu ứng dụng chạy ở **hai** Availability Zone, bạn cần **gấp đôi** số task bình thường. Điều này để bạn có capacity ngay lập tức khi có sự cố. Nếu ứng dụng chạy ở **ba** Availability Zone, chúng tôi khuyến nghị chạy **1,5 lần** số task bình thường. Tức là chạy **ba task cho mỗi hai task** cần cho việc phục vụ thông thường.

**💡 Giải thích thêm**

Vì sao autoscaling có độ trễ, theo trang:

```
metric thời gian thực ─► CloudWatch tổng hợp (có thể vài phút)
                      ─► so với ngưỡng alarm (ngưỡng phải bị vượt vài phút)
                      ─► cấp task mới / dừng task thừa (cũng mất thời gian)
```

Hai lý do để **cấp dư** theo trang:

| Lý do | Cách làm theo trang |
|---|---|
| Đỡ đợt tăng nhu cầu ngắn trong lúc autoscaling còn trễ | Đặt mục tiêu scale ở **60–80%** utilization |
| Chịu được sự cố một AZ | 2 AZ → **2×** số task; 3 AZ → **1,5×** số task |

Phép tính cho số AZ (theo đúng con số của trang):

| Số AZ | Số task chạy | Khi mất 1 AZ còn lại | So với nhu cầu bình thường |
|---|---|---|---|
| 2 | 2× | 1 AZ = 1× | Vừa đủ |
| 3 | 1,5× (3 task cho mỗi 2 task cần) | 2 AZ = 1× | Vừa đủ |

---

## 1. Tối đa hoá tốc độ scale

**📘 Theo docs AWS**

Autoscaling là quá trình **phản ứng**, cần thời gian để có hiệu lực. Tuy nhiên, có một số cách giúp giảm thời gian cần để scale out.

**Giảm kích thước image.** Image lớn hơn mất nhiều thời gian hơn để tải từ image repository và giải nén. Vì vậy, giữ image nhỏ giảm thời gian container khởi động. Để giảm kích thước image, bạn có thể làm theo các khuyến nghị cụ thể sau:

- Nếu bạn build được static binary hoặc dùng Golang, hãy build image `FROM` scratch và chỉ đưa binary ứng dụng vào image kết quả.
- Dùng base image tối giản từ các nhà cung cấp distro upstream, như Amazon Linux hoặc Ubuntu.
- Không đưa bất kỳ artifact build nào vào image cuối. Dùng **multi-stage build** có thể giúp việc này.
- Gộp các stage `RUN` khi có thể. Mỗi stage `RUN` tạo một image layer mới, dẫn tới thêm một lượt tải layer. Một stage `RUN` duy nhất gồm nhiều lệnh nối bằng `&&` có ít layer hơn so với nhiều stage `RUN`.
- Nếu bạn muốn đưa dữ liệu, như dữ liệu ML inference, vào image cuối, chỉ đưa dữ liệu cần để khởi động và bắt đầu phục vụ traffic. Nếu bạn lấy dữ liệu theo yêu cầu từ Amazon S3 hoặc bộ lưu trữ khác mà không ảnh hưởng service, thì lưu dữ liệu ở những nơi đó thay vì trong image.

**Giữ image ở gần.** Độ trễ mạng càng cao, tải image càng lâu. Lưu image trong repository ở **cùng AWS Region** với workload. Amazon ECR là image repository hiệu năng cao, có ở mọi Region mà Amazon ECS có. Tránh đi qua Internet hoặc đường VPN để tải container image. Lưu image ở cùng Region cải thiện độ tin cậy tổng thể. Nó giảm rủi ro sự cố kết nối mạng và sự cố sẵn sàng ở một Region khác. Ngoài ra, bạn cũng có thể dùng **Amazon ECR cross-region replication** để hỗ trợ việc này.

**Giảm ngưỡng health check của load balancer.** Load balancer thực hiện health check trước khi gửi traffic tới ứng dụng. Cấu hình health check mặc định của target group có thể mất **90 giây hoặc lâu hơn**. Trong thời gian này, load balancer kiểm tra trạng thái sức khoẻ và nhận request. Giảm **interval** và **threshold count** của health check có thể giúp ứng dụng nhận traffic nhanh hơn và giảm tải cho các task khác.

**Cân nhắc hiệu năng cold-start.** Một số ứng dụng dùng runtime như Java thực hiện biên dịch **Just-In-Time (JIT)**. Quá trình biên dịch, ít nhất là lúc bắt đầu, có thể làm chậm hiệu năng ứng dụng. Một cách khắc phục là viết lại các phần nhạy cảm với độ trễ của workload bằng ngôn ngữ không chịu phạt hiệu năng cold-start.

**Dùng step scaling, không dùng target-tracking scaling policy.** Bạn có nhiều lựa chọn Application Auto Scaling cho task Amazon ECS. **Target tracking** là chế độ dễ dùng nhất. Với nó, bạn chỉ cần đặt giá trị mục tiêu cho một metric, như CPU utilization trung bình. Sau đó, auto scaler tự quản lý số task cần để đạt giá trị đó. Với **step scaling**, bạn có thể phản ứng **nhanh hơn** với thay đổi nhu cầu, vì bạn định nghĩa các ngưỡng cụ thể cho metric scale, và số task thêm hoặc bớt khi vượt ngưỡng. Và quan trọng hơn, bạn có thể phản ứng rất nhanh với thay đổi nhu cầu bằng cách **giảm thiểu thời gian một alarm ngưỡng ở trạng thái vi phạm**. Thông tin thêm, xem *Service Auto Scaling* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-auto-scaling.html>) trong *Amazon Elastic Container Service Developer Guide*.

Nếu bạn dùng Amazon EC2 instance để cung cấp capacity cho cluster, hãy cân nhắc các khuyến nghị sau:

**Dùng Amazon EC2 instance lớn hơn và Amazon EBS volume nhanh hơn.** Bạn có thể cải thiện tốc độ tải và chuẩn bị image bằng cách dùng Amazon EC2 instance lớn hơn và Amazon EBS volume nhanh hơn. Trong cùng một Amazon EC2 instance family, throughput tối đa của mạng và Amazon EBS **tăng theo kích thước instance** (ví dụ từ `m5.xlarge` lên `m5.2xlarge`). Ngoài ra, bạn cũng có thể tuỳ chỉnh Amazon EBS volume để tăng throughput và IOPS. Ví dụ, nếu dùng volume `gp2`, hãy dùng volume lớn hơn để có throughput cơ bản cao hơn. Nếu dùng volume `gp3`, hãy chỉ định throughput và IOPS khi tạo volume.

**Dùng network mode bridge cho task chạy trên Amazon EC2 instance.** Task dùng network mode `bridge` trên Amazon EC2 khởi động nhanh hơn task dùng network mode `awsvpc`. Khi dùng network mode `awsvpc`, Amazon ECS gắn một elastic network interface (ENI) vào instance trước khi khởi chạy task. Việc này thêm độ trễ. Tuy nhiên, dùng mạng bridge có một số đánh đổi. Các task này không có security group riêng, và có một số hệ quả đối với cân bằng tải. Thông tin thêm, xem *Load balancer target groups* (<https://docs.aws.amazon.com/elasticloadbalancing/latest/network/load-balancer-target-groups.html>) trong *Elastic Load Balancing User Guide*.

**💡 Giải thích thêm**

Tổng hợp các cách tăng tốc scale out theo trang:

| Khuyến nghị | Áp dụng | Ý chính |
|---|---|---|
| Giảm kích thước image | Mọi nơi | `FROM scratch` cho static binary/Go; base image tối giản; multi-stage build; gộp `RUN` bằng `&&`; chỉ đưa dữ liệu cần để khởi động |
| Giữ image ở gần | Mọi nơi | ECR cùng Region; tránh Internet/VPN; hoặc ECR cross-region replication |
| Giảm ngưỡng health check | Có load balancer | Mặc định có thể ≥ 90 giây; giảm interval và threshold count |
| Cân nhắc cold-start | Runtime JIT (vd Java) | Viết lại phần nhạy độ trễ bằng ngôn ngữ không bị phạt cold-start |
| Step scaling thay target tracking | Application Auto Scaling | Tự đặt ngưỡng và số task thêm/bớt; giảm thời gian alarm ở trạng thái vi phạm |
| Instance lớn hơn, EBS nhanh hơn | EC2 | Throughput mạng/EBS tăng theo kích thước (vd `m5.xlarge` → `m5.2xlarge`); `gp2` dùng volume lớn hơn; `gp3` chỉ định throughput và IOPS |
| Network mode `bridge` | EC2 | Khởi động nhanh hơn `awsvpc` (không phải gắn ENI); đổi lại không có security group riêng mỗi task, có hệ quả với load balancing |

Gộp `RUN` theo trang:

```
Nhiều stage RUN   → nhiều layer → nhiều lượt tải
Một RUN nối bằng && → ít layer hơn
```

| | Target tracking | Step scaling |
|---|---|---|
| Cách đặt | Một giá trị mục tiêu cho metric | Các ngưỡng + số task thêm/bớt mỗi ngưỡng |
| Độ dễ | Dễ nhất | Cần định nghĩa nhiều hơn |
| Tốc độ phản ứng theo trang | — | Nhanh hơn |

Ở đoạn cold-start, câu gốc tiếng Anh viết "can **show** application performance" — theo ngữ cảnh, bản dịch hiểu là "làm **chậm** hiệu năng" (có vẻ "show" là lỗi đánh máy của "slow").

---

## 2. Xử lý cú sốc nhu cầu (demand shock)

**📘 Theo docs AWS**

Một số ứng dụng gặp **cú sốc nhu cầu** lớn và đột ngột. Điều này xảy ra vì nhiều lý do: một sự kiện tin tức, đợt giảm giá lớn, sự kiện truyền thông, hoặc sự kiện khác lan truyền mạnh khiến traffic tăng nhanh và đáng kể trong thời gian rất ngắn. Nếu không có kế hoạch, điều này có thể khiến nhu cầu nhanh chóng vượt tài nguyên có sẵn.

Cách tốt nhất để xử lý cú sốc nhu cầu là **dự đoán** và **lên kế hoạch** tương ứng. Vì autoscaling có thể mất thời gian, chúng tôi khuyến nghị bạn **scale out trước** khi cú sốc bắt đầu. Để có kết quả tốt nhất, chúng tôi khuyến nghị có một kế hoạch nghiệp vụ với sự phối hợp chặt chẽ giữa các team dùng **chung một lịch**. Team lên kế hoạch sự kiện nên làm việc chặt chẽ với team phụ trách ứng dụng từ trước. Điều này cho team đó đủ thời gian để có kế hoạch lịch rõ ràng. Họ có thể lên lịch để capacity **scale out trước** sự kiện và **scale in sau** sự kiện. Thông tin thêm, xem *Scheduled scaling* (<https://docs.aws.amazon.com/autoscaling/application/userguide/application-auto-scaling-scheduled-scaling.html>) trong *Application Auto Scaling User Guide*.

Nếu bạn có gói **Enterprise Support**, hãy làm việc thêm với **Technical Account Manager (TAM)**. TAM có thể kiểm tra service quota của bạn và đảm bảo các quota cần thiết được nâng trước khi sự kiện bắt đầu. Bằng cách này, bạn không vô tình chạm service quota. Họ cũng có thể giúp bạn **làm nóng trước (prewarming)** các dịch vụ như load balancer để sự kiện diễn ra suôn sẻ.

Xử lý cú sốc nhu cầu **không lên lịch trước** là vấn đề khó hơn. Cú sốc không lên lịch, nếu biên độ đủ lớn, có thể nhanh chóng khiến nhu cầu vượt capacity. Nó cũng có thể vượt khả năng phản ứng của autoscaling. Cách tốt nhất để chuẩn bị cho cú sốc không lên lịch là **cấp dư tài nguyên**. Bạn phải có đủ tài nguyên để xử lý nhu cầu traffic tối đa dự kiến **bất cứ lúc nào**.

Duy trì capacity tối đa để đề phòng cú sốc không lên lịch có thể tốn kém. Để giảm tác động chi phí, hãy tìm một **metric hoặc sự kiện báo trước (leading indicator)** dự đoán một cú sốc nhu cầu lớn sắp xảy ra. Nếu metric hoặc sự kiện đó đáng tin cậy và báo trước đủ sớm, hãy bắt đầu quá trình scale out **ngay** khi sự kiện xảy ra hoặc khi metric vượt ngưỡng cụ thể bạn đã đặt.

Nếu ứng dụng dễ gặp cú sốc nhu cầu không lên lịch, hãy cân nhắc thêm một **chế độ hiệu năng cao** cho ứng dụng, hy sinh chức năng không quan trọng nhưng giữ chức năng thiết yếu cho khách hàng. Ví dụ, giả sử ứng dụng có thể chuyển từ việc tạo response tuỳ biến tốn kém sang phục vụ một **trang response tĩnh**. Trong kịch bản này, bạn có thể tăng throughput đáng kể mà **không cần scale** ứng dụng.

Cuối cùng, bạn có thể cân nhắc **tách các service nguyên khối** để xử lý cú sốc nhu cầu tốt hơn. Nếu ứng dụng là một service nguyên khối tốn kém để chạy và chậm scale, bạn có thể tách hoặc viết lại các phần quan trọng về hiệu năng và chạy chúng thành service riêng. Các service mới này sau đó có thể scale **độc lập** với các thành phần ít quan trọng hơn. Có sự linh hoạt để scale out chức năng quan trọng về hiệu năng riêng với các phần khác của ứng dụng có thể vừa giảm thời gian thêm capacity, vừa giúp tiết kiệm chi phí.

**💡 Giải thích thêm**

| Loại cú sốc | Khuyến nghị theo trang |
|---|---|
| **Có lên lịch** (sale lớn, sự kiện truyền thông…) | Dùng chung lịch giữa các team; **scheduled scaling**: scale out trước, scale in sau; nếu có Enterprise Support → làm việc với TAM để nâng quota và prewarm load balancer |
| **Không lên lịch** | Cấp dư tới mức nhu cầu tối đa dự kiến; giảm chi phí bằng **leading indicator** để scale out sớm; thêm **chế độ hiệu năng cao** (vd chuyển sang trang tĩnh); **tách service nguyên khối** để scale riêng phần quan trọng |

```
Có lịch:      lịch chung ─► scheduled scaling (out trước sự kiện, in sau sự kiện)
Không lịch:   cấp dư ─┬─ leading indicator → scale out ngay khi có tín hiệu
                      ├─ chế độ hiệu năng cao (cắt chức năng phụ, vd trang tĩnh)
                      └─ tách monolith → scale riêng phần quan trọng
```

Đoạn về lên kế hoạch trên trang viết "shared calender" (sai chính tả của "calendar") — không ảnh hưởng nghĩa.

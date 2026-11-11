# Optimizing Amazon ECS service auto scaling

> Nguồn: **Amazon ECS Developer Guide — Optimizing Amazon ECS service auto scaling**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/capacity-autoscaling-best-practice.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: **bản dịch** sang tiếng Việt, đi đúng thứ tự và đúng nội dung của trang gốc, không thêm, không bớt ý.
> - Khối **💡 Giải thích thêm**: chỉ giải thích lại những gì **chính trang này** đã nói, cho dễ hiểu hơn. Không đưa thông tin từ trang khác hay ví dụ tự đặt.
> - Tên dịch vụ, metric, instance family, kiểu scaling giữ nguyên tiếng Anh.
> - Tài liệu AWS được phát hành theo giấy phép **Creative Commons Attribution-ShareAlike 4.0**; bản dịch này ghi rõ nguồn ở trên.

---

## Mục lục

0. [Phần mở đầu](#0-phần-mở-đầu)
1. [Mô tả đặc tính ứng dụng](#1-mô-tả-đặc-tính-ứng-dụng)
   - [1.1 Xác định utilization metric](#11-xác-định-utilization-metric)
   - [1.2 Các mô hình ứng dụng thường gặp và đặc tính scale](#12-các-mô-hình-ứng-dụng-thường-gặp-và-đặc-tính-scale)
2. [Tổng hợp: metric scale theo mô hình](#2-tổng-hợp-metric-scale-theo-mô-hình)

---

## 0. Phần mở đầu

**📘 Theo docs AWS**

Một Amazon ECS service là một tập task được quản lý. Mỗi service có một task definition đi kèm, một số lượng task mong muốn (desired task count), và một placement strategy tuỳ chọn.

Auto scaling của Amazon ECS service hoạt động thông qua dịch vụ **Application Auto Scaling**. Application Auto Scaling dùng **CloudWatch metric** làm nguồn cho metric scale. Nó cũng dùng **CloudWatch alarm** để đặt ngưỡng khi nào scale in hoặc scale out service.

Bạn cung cấp ngưỡng để scale. Bạn có thể đặt một mục tiêu cho metric, gọi là **target tracking scaling**. Bạn cũng có thể chỉ định các ngưỡng, gọi là **step scaling**.

Sau khi cấu hình Application Auto Scaling, nó liên tục tính toán desired task count phù hợp cho service. Nó cũng báo cho Amazon ECS khi desired task count cần thay đổi, bằng cách scale out hoặc scale in.

Để dùng service auto scaling hiệu quả, bạn phải chọn **metric scale phù hợp**. Chúng tôi bàn cách chọn metric trong các phần sau.

**💡 Giải thích thêm**

```
CloudWatch metric ─► CloudWatch alarm (ngưỡng) ─► Application Auto Scaling
                                                     │ tính desired task count
                                                     ▼
                                               Amazon ECS service (scale in / out)
```

| Kiểu scaling | Bạn cung cấp |
|---|---|
| Target tracking scaling | Một **mục tiêu** cho metric |
| Step scaling | Các **ngưỡng** |

Điểm then chốt cả trang xoay quanh: chọn **đúng metric**.

---

## 1. Mô tả đặc tính ứng dụng

**📘 Theo docs AWS**

Scale ứng dụng đúng cách đòi hỏi bạn biết điều kiện nào nên scale in và khi nào nên scale out.

Về bản chất, bạn nên scale out ứng dụng nếu **nhu cầu được dự báo vượt capacity**. Ngược lại, bạn có thể scale in để **tiết kiệm chi phí** khi tài nguyên vượt nhu cầu.

### 1.1 Xác định utilization metric

**📘 Theo docs AWS**

Để scale hiệu quả, bạn phải xác định một metric thể hiện **mức sử dụng (utilization)** hoặc **mức bão hoà (saturation)**. Metric này phải có các tính chất sau mới hữu ích cho việc scale.

- **Metric phải tương quan với nhu cầu.** Khi giữ nguyên tài nguyên nhưng nhu cầu thay đổi, giá trị metric cũng phải thay đổi. Metric nên tăng hoặc giảm khi nhu cầu tăng hoặc giảm.

- **Giá trị metric phải thay đổi tỉ lệ với capacity.** Khi nhu cầu giữ nguyên, thêm tài nguyên phải làm giá trị metric thay đổi tương ứng. Vì vậy, **gấp đôi số task** phải làm metric **giảm 50%**.

Cách tốt nhất để xác định utilization metric là **load test** trong môi trường trước production như môi trường staging. Có nhiều giải pháp load test thương mại và mã nguồn mở. Các giải pháp này thường có thể tạo tải tổng hợp hoặc mô phỏng traffic người dùng thật.

Để bắt đầu quy trình load test, trước tiên bạn nên dựng dashboard cho các utilization metric của ứng dụng. Các metric này gồm CPU utilization, memory utilization, thao tác I/O, độ sâu hàng đợi I/O, và network throughput. Bạn có thể thu các metric này bằng dịch vụ như CloudWatch Container Insights. Bạn cũng có thể thu bằng Amazon Managed Service for Prometheus cùng với Amazon Managed Grafana. Trong quá trình này, hãy đảm bảo bạn thu và vẽ biểu đồ metric về **thời gian phản hồi** hoặc **tốc độ hoàn thành công việc** của ứng dụng.

Khi load test, bắt đầu với tốc độ request hoặc tốc độ đưa job vào **nhỏ**. Giữ tốc độ này ổn định vài phút để ứng dụng "khởi động" (warm up). Sau đó, **tăng chậm** tốc độ và giữ ổn định vài phút. Lặp lại chu kỳ này, mỗi lần tăng tốc độ, cho tới khi thời gian phản hồi hoặc hoàn thành của ứng dụng quá chậm để đáp ứng **service-level objective (SLO)**.

Trong lúc load test, xem xét từng utilization metric. Các metric **tăng cùng với tải** là ứng viên hàng đầu cho utilization metric tốt nhất.

Tiếp theo, xác định tài nguyên đạt **bão hoà**. Đồng thời, xem các utilization metric để biết metric nào **đi ngang ở mức cao trước tiên**. Hoặc xem metric nào đạt đỉnh rồi làm ứng dụng **crash trước tiên**. Ví dụ, nếu CPU utilization tăng từ 0% lên 70–80% khi bạn thêm tải, rồi giữ ở mức đó dù bạn thêm tải nữa, thì có thể nói CPU đã bão hoà. Tuỳ kiến trúc CPU, nó có thể không bao giờ đạt 100%. Một ví dụ khác, giả sử memory utilization tăng khi bạn thêm tải, rồi ứng dụng đột ngột crash khi chạm limit memory của task hoặc Amazon EC2 instance. Trong tình huống này, nhiều khả năng memory đã bị dùng hết. Ứng dụng có thể tiêu thụ nhiều loại tài nguyên. Vì vậy, hãy chọn metric đại diện cho tài nguyên **cạn kiệt trước tiên**.

Cuối cùng, thử load test lại sau khi **gấp đôi** số task hoặc Amazon EC2 instance. Giả sử metric chính tăng, hoặc giảm, với tốc độ **bằng một nửa** trước đó. Nếu đúng vậy, metric **tỉ lệ với capacity**. Đây là utilization metric tốt cho auto scaling.

Giờ xét tình huống giả định này. Giả sử bạn load test một ứng dụng và thấy CPU utilization cuối cùng đạt **80%** ở **100 request mỗi giây**. Khi thêm tải, CPU utilization không tăng nữa. Tuy nhiên, ứng dụng phản hồi chậm hơn. Sau đó, bạn chạy lại load test, **gấp đôi số task** nhưng giữ tốc độ ở giá trị đỉnh trước đó. Nếu thấy CPU utilization trung bình giảm còn khoảng **40%**, thì CPU utilization trung bình là ứng viên tốt làm metric scale. Ngược lại, nếu CPU utilization vẫn ở **80%** sau khi tăng số task, thì CPU utilization trung bình **không** phải metric scale tốt. Khi đó, bạn cần nghiên cứu thêm để tìm metric phù hợp.

**💡 Giải thích thêm**

Hai tính chất của một utilization metric tốt:

| Tính chất | Kiểm tra bằng cách |
|---|---|
| Tương quan với nhu cầu | Giữ nguyên tài nguyên, đổi tải → metric phải đổi theo |
| Tỉ lệ với capacity | Giữ nguyên tải, **gấp đôi** task/instance → metric phải **giảm một nửa** |

Quy trình load test theo trang:

```
1. Dựng dashboard: CPU, memory, I/O, I/O queue depth, network throughput
                   + thời gian phản hồi / tốc độ hoàn thành
2. Tải nhỏ, giữ vài phút (warm up)
3. Tăng chậm, giữ vài phút → lặp lại tới khi không đạt SLO
4. Metric nào tăng theo tải → ứng viên
5. Tài nguyên nào bão hoà / cạn trước (đi ngang ở mức cao, hoặc làm crash) → chọn metric đó
6. Gấp đôi task/instance, chạy lại → metric giảm một nửa? → metric tốt
```

Tình huống giả định của trang:

| Bước | Kết quả | Kết luận |
|---|---|---|
| Load test | CPU đạt 80% ở 100 request/giây; thêm tải thì CPU đứng yên nhưng phản hồi chậm đi | CPU bão hoà |
| Gấp đôi số task, giữ 100 request/giây | CPU trung bình còn ~**40%** | CPU trung bình là metric **tốt** |
| | CPU vẫn ~**80%** | CPU trung bình **không** tốt — tìm metric khác |

### 1.2 Các mô hình ứng dụng thường gặp và đặc tính scale

**📘 Theo docs AWS**

Bạn có thể chạy mọi loại phần mềm trên AWS. Nhiều workload là tự phát triển, trong khi số khác dựa trên phần mềm mã nguồn mở phổ biến. Dù xuất phát từ đâu, chúng tôi đã quan sát thấy một số mẫu thiết kế chung cho service. Cách scale hiệu quả phụ thuộc phần lớn vào mẫu đó.

#### Server hiệu quả bị giới hạn bởi CPU (efficient CPU-bound server)

**📘 Theo docs AWS**

Server hiệu quả bị giới hạn bởi CPU gần như không dùng tài nguyên nào ngoài CPU và network throughput. Mỗi request có thể được ứng dụng tự xử lý. Request không phụ thuộc vào service khác như database. Ứng dụng có thể xử lý hàng trăm nghìn request đồng thời, và có thể dùng hiệu quả nhiều CPU để làm vậy. Mỗi request được phục vụ bởi một thread riêng có overhead memory thấp, hoặc có một vòng lặp sự kiện bất đồng bộ chạy trên mỗi CPU để phục vụ request. Mỗi bản sao (replica) của ứng dụng đều có khả năng xử lý request như nhau. Tài nguyên duy nhất có thể cạn trước CPU là băng thông mạng. Với service bị giới hạn bởi CPU, memory utilization, kể cả ở throughput đỉnh, chỉ là một phần nhỏ tài nguyên có sẵn.

Bạn có thể dùng **auto scaling dựa trên CPU** cho loại ứng dụng này. Ứng dụng có độ linh hoạt tối đa về scale. Bạn có thể scale theo chiều dọc bằng cách cấp Amazon EC2 instance lớn hơn hoặc nhiều vCPU Fargate hơn. Và bạn cũng có thể scale theo chiều ngang bằng cách thêm replica. Thêm replica, hoặc gấp đôi kích thước instance, làm CPU utilization trung bình so với capacity giảm một nửa.

Nếu dùng capacity Amazon EC2 cho ứng dụng này, hãy cân nhắc đặt nó trên instance **tối ưu compute** như family `c5` hoặc `c6g`.

#### Server hiệu quả bị giới hạn bởi memory (efficient memory-bound server)

**📘 Theo docs AWS**

Server hiệu quả bị giới hạn bởi memory cấp phát một lượng memory đáng kể cho mỗi request. Ở mức đồng thời tối đa — nhưng không nhất thiết ở throughput tối đa — memory cạn trước tài nguyên CPU. Memory gắn với một request được giải phóng khi request kết thúc. Có thể nhận thêm request miễn là còn memory.

Bạn có thể dùng **auto scaling dựa trên memory** cho loại ứng dụng này. Ứng dụng có độ linh hoạt tối đa về scale. Bạn có thể scale theo chiều dọc bằng cách cấp tài nguyên memory Amazon EC2 hoặc Fargate lớn hơn. Và bạn cũng có thể scale theo chiều ngang bằng cách thêm replica. Thêm replica, hoặc gấp đôi kích thước instance, có thể làm memory utilization trung bình so với capacity giảm một nửa.

Nếu dùng capacity Amazon EC2 cho ứng dụng này, hãy cân nhắc đặt nó trên instance **tối ưu memory** như family `r5` hoặc `r6g`.

Một số ứng dụng bị giới hạn bởi memory **không giải phóng** memory gắn với request khi request kết thúc, nên giảm mức đồng thời không làm giảm memory đang dùng. Với trường hợp này, chúng tôi **không khuyến nghị** dùng scale dựa trên memory.

#### Server dựa trên worker (worker-based server)

**📘 Theo docs AWS**

Server dựa trên worker xử lý **một request cho mỗi worker thread**, lần lượt từng cái. Worker thread có thể là thread nhẹ, như POSIX thread. Chúng cũng có thể là thread nặng hơn, như UNIX process. Dù là loại thread nào, luôn có một **mức đồng thời tối đa** mà ứng dụng hỗ trợ. Thường giới hạn đồng thời được đặt tỉ lệ với tài nguyên memory có sẵn. Nếu chạm giới hạn đồng thời, ứng dụng đưa các request thêm vào một **hàng đợi tồn đọng (backlog queue)**. Nếu backlog queue tràn, ứng dụng **từ chối ngay** các request đến thêm. Các ứng dụng phổ biến thuộc mẫu này gồm **Apache web server** và **Gunicorn**.

**Mức đồng thời của request (request concurrency)** thường là metric tốt nhất để scale ứng dụng này. Vì mỗi replica có giới hạn đồng thời, việc **scale out trước khi chạm giới hạn trung bình** là quan trọng.

Cách tốt nhất để có metric request concurrency là cho ứng dụng **báo cáo lên CloudWatch**. Mỗi replica có thể đẩy số request đồng thời dưới dạng **custom metric** với tần suất cao. Chúng tôi khuyến nghị đặt tần suất **ít nhất mỗi phút một lần**. Sau khi thu được vài lần báo cáo, bạn có thể dùng **mức đồng thời trung bình** làm metric scale. Bạn tính metric này bằng cách lấy tổng mức đồng thời chia cho số replica. Ví dụ, nếu tổng mức đồng thời là 1000 và số replica là 10, thì mức đồng thời trung bình là 100.

Nếu ứng dụng ở sau Application Load Balancer, bạn cũng có thể dùng metric **`ActiveConnectionCount`** của load balancer làm một yếu tố trong metric scale. Bạn phải chia metric `ActiveConnectionCount` cho số replica để có giá trị trung bình. Bạn phải dùng **giá trị trung bình** để scale, không phải giá trị đếm thô.

Để thiết kế này hoạt động tốt nhất, **độ lệch chuẩn** của độ trễ phản hồi nên **nhỏ** ở tốc độ request thấp. Chúng tôi khuyến nghị rằng, trong giai đoạn nhu cầu thấp, phần lớn request được trả lời trong thời gian ngắn, và không có nhiều request mất thời gian phản hồi lâu hơn đáng kể so với trung bình. Thời gian phản hồi trung bình nên **gần** với thời gian phản hồi **percentile thứ 95**. Nếu không, hàng đợi có thể bị tràn. Điều này dẫn tới lỗi. Chúng tôi khuyến nghị cấp thêm replica khi cần để giảm rủi ro tràn.

#### Server chờ (waiting server)

**📘 Theo docs AWS**

Server chờ có xử lý một phần cho mỗi request, nhưng **phụ thuộc nhiều** vào một hoặc nhiều service downstream để hoạt động. Ứng dụng container thường dùng nhiều service downstream như database và các API service khác. Các service này có thể mất thời gian để phản hồi, nhất là ở kịch bản capacity cao hoặc mức đồng thời cao. Đó là vì các ứng dụng này có xu hướng dùng ít CPU và dùng hết mức đồng thời tối đa theo memory có sẵn.

Waiting service phù hợp với **mẫu memory-bound** hoặc **mẫu worker-based**, tuỳ cách ứng dụng được thiết kế. Nếu mức đồng thời của ứng dụng chỉ bị giới hạn bởi memory, thì nên dùng **memory utilization trung bình** làm metric scale. Nếu mức đồng thời dựa trên giới hạn worker, thì nên dùng **mức đồng thời trung bình** làm metric scale.

#### Server dựa trên Java (Java-based server)

**📘 Theo docs AWS**

Nếu server Java của bạn bị giới hạn bởi CPU và scale tỉ lệ với tài nguyên CPU, thì nó có thể phù hợp với mẫu efficient CPU-bound server. Nếu vậy, CPU utilization trung bình có thể phù hợp làm metric scale. Tuy nhiên, nhiều ứng dụng Java **không** bị giới hạn bởi CPU, khiến chúng khó scale.

Để có hiệu năng tốt nhất, chúng tôi khuyến nghị cấp càng nhiều memory càng tốt cho **heap của Java Virtual Machine (JVM)**. Các phiên bản JVM gần đây, gồm **Java 8 update 191 trở lên**, tự động đặt kích thước heap lớn nhất có thể để vừa trong container. Điều này nghĩa là trong Java, memory utilization **hiếm khi tỉ lệ** với mức dùng của ứng dụng. Khi tốc độ request và mức đồng thời tăng, memory utilization vẫn không đổi. Vì vậy, chúng tôi **không khuyến nghị** scale server Java dựa trên memory utilization. Thay vào đó, chúng tôi thường khuyến nghị scale theo **CPU utilization**.

Trong một số trường hợp, server Java gặp tình trạng **cạn heap** trước khi cạn CPU. Nếu ứng dụng dễ cạn heap ở **mức đồng thời cao**, thì **số kết nối trung bình** là metric scale tốt nhất. Nếu ứng dụng dễ cạn heap ở **throughput cao**, thì **tốc độ request trung bình** là metric scale tốt nhất.

#### Server dùng runtime có garbage collection khác

**📘 Theo docs AWS**

Nhiều ứng dụng server dựa trên runtime có thực hiện **garbage collection** như **.NET** và **Ruby**. Các ứng dụng server này có thể thuộc một trong các mẫu đã mô tả. Tuy nhiên, như với Java, chúng tôi **không khuyến nghị** scale các ứng dụng này dựa trên memory, vì memory utilization trung bình quan sát được thường **không tương quan** với throughput hay mức đồng thời.

Với các ứng dụng này, chúng tôi khuyến nghị scale theo **CPU utilization** nếu ứng dụng bị giới hạn bởi CPU. Nếu không, chúng tôi khuyến nghị scale theo **throughput trung bình** hoặc **mức đồng thời trung bình**, dựa trên kết quả load test.

#### Bộ xử lý job (job processors)

**📘 Theo docs AWS**

Nhiều workload liên quan tới xử lý job bất đồng bộ. Chúng gồm các ứng dụng không nhận request theo thời gian thực, mà **đăng ký vào một hàng đợi công việc** để nhận job. Với các loại ứng dụng này, metric scale phù hợp gần như luôn là **độ sâu hàng đợi (queue depth)**. Hàng đợi tăng là dấu hiệu công việc chờ vượt khả năng xử lý, còn hàng đợi rỗng cho thấy capacity nhiều hơn công việc cần làm.

Các dịch vụ messaging của AWS, như **Amazon SQS** và **Amazon Kinesis Data Streams**, cung cấp CloudWatch metric có thể dùng để scale. Với Amazon SQS, **`ApproximateNumberOfMessagesVisible`** là metric tốt nhất. Với Kinesis Data Streams, hãy cân nhắc dùng metric **`MillisBehindLatest`**, do Kinesis Client Library (KCL) đẩy lên. Metric này nên được **lấy trung bình trên mọi consumer** trước khi dùng để scale.

**💡 Giải thích thêm**

Ví dụ tính mức đồng thời trung bình của trang (worker-based server):

```
tổng mức đồng thời = 1000
số replica         = 10
──────────────────────────
trung bình         = 1000 / 10 = 100   ← dùng giá trị này để scale
```

Tương tự với `ActiveConnectionCount` của ALB: **chia cho số replica**, dùng trung bình, không dùng số đếm thô.

Điều kiện để mô hình worker-based chạy tốt, theo trang:

| Điều kiện | Nếu không đạt |
|---|---|
| Độ lệch chuẩn độ trễ nhỏ ở tải thấp | Hàng đợi có thể tràn → lỗi |
| Thời gian phản hồi trung bình gần p95 | Cấp thêm replica để giảm rủi ro tràn |

---

## 2. Tổng hợp: metric scale theo mô hình

**💡 Giải thích thêm**

Tổng hợp từ mục 1.2 của chính trang:

| Mô hình | Đặc điểm | Metric scale khuyến nghị | Không nên | EC2 instance gợi ý |
|---|---|---|---|---|
| **Efficient CPU-bound** | Chỉ dùng CPU + mạng; memory chỉ một phần nhỏ | CPU utilization | — | Compute-optimized (`c5`, `c6g`) |
| **Efficient memory-bound** | Nhiều memory mỗi request, giải phóng khi xong | Memory utilization | Memory nếu ứng dụng **không giải phóng** memory | Memory-optimized (`r5`, `r6g`) |
| **Worker-based** (Apache, Gunicorn) | Giới hạn đồng thời mỗi replica, có backlog queue | Mức đồng thời trung bình (custom metric ≥ mỗi phút); `ActiveConnectionCount` / số replica | Số đếm thô | — |
| **Waiting server** | Phụ thuộc downstream, ít CPU | Memory trung bình (nếu giới hạn bởi memory) hoặc mức đồng thời trung bình (nếu giới hạn worker) | — | — |
| **Java-based** | JVM tự đặt heap lớn (Java 8u191+) → memory gần như không đổi | CPU utilization; cạn heap ở đồng thời cao → số kết nối trung bình; cạn heap ở throughput cao → tốc độ request trung bình | Memory | — |
| **Runtime GC khác** (.NET, Ruby) | Memory không tương quan với tải | CPU (nếu CPU-bound); nếu không → throughput hoặc mức đồng thời trung bình | Memory | — |
| **Job processors** | Lấy job từ hàng đợi | Queue depth: SQS `ApproximateNumberOfMessagesVisible`; Kinesis `MillisBehindLatest` (trung bình mọi consumer) | — | — |

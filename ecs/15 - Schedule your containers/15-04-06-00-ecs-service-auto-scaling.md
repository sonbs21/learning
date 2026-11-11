# Automatically scale your Amazon ECS service

> Nguồn: **Amazon ECS Developer Guide — Automatically scale your Amazon ECS service**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-auto-scaling.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: **bản dịch** sang tiếng Việt, đi đúng thứ tự và đúng nội dung của trang gốc, không thêm, không bớt ý.
> - Khối **💡 Giải thích thêm**: chỉ giải thích lại những gì **chính trang này** đã nói, cho dễ hiểu hơn. Không đưa thông tin từ trang khác hay ví dụ tự đặt.
> - Tên kiểu scaling, tham số, lệnh giữ nguyên tiếng Anh.
> - Tài liệu AWS được phát hành theo giấy phép **Creative Commons Attribution-ShareAlike 4.0**; bản dịch này ghi rõ nguồn ở trên.

---

## Mục lục

0. [Phần mở đầu](#0-phần-mở-đầu)
1. [Các kiểu automatic scaling](#1-các-kiểu-automatic-scaling)
2. [Những điều cần cân nhắc](#2-những-điều-cần-cân-nhắc)
3. [Service auto scaling và deployment](#3-service-auto-scaling-và-deployment)

---

## 0. Phần mở đầu

**📘 Theo docs AWS**

**Automatic scaling** là khả năng **tự động tăng hoặc giảm** số task mong muốn (desired) trong Amazon ECS service. Amazon ECS dùng dịch vụ **Application Auto Scaling** để cung cấp chức năng này. Thông tin thêm, xem *Application Auto Scaling User Guide* (<https://docs.aws.amazon.com/autoscaling/application/userguide/what-is-application-auto-scaling.html>).

Amazon ECS đẩy CloudWatch metric về mức dùng **CPU và memory trung bình** của service. Thông tin thêm, xem *Amazon ECS service utilization metrics* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service_utilization.html>). Bạn có thể dùng các metric này và các CloudWatch metric khác để **scale out** service (thêm task) khi nhu cầu cao vào giờ cao điểm, và **scale in** service (chạy ít task hơn) để giảm chi phí khi mức sử dụng thấp.

---

## 1. Các kiểu automatic scaling

**📘 Theo docs AWS**

Amazon ECS Service Auto Scaling hỗ trợ các kiểu automatic scaling sau:

- *Use a target metric to scale Amazon ECS services* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-autoscaling-targettracking.html>) — Tăng hoặc giảm số task service chạy dựa trên **giá trị mục tiêu** của một metric cụ thể. Giống cách **máy điều nhiệt** giữ nhiệt độ trong nhà. Bạn chọn nhiệt độ và máy điều nhiệt lo phần còn lại.
- *Use predefined increments based on CloudWatch alarms to scale Amazon ECS services* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-autoscaling-stepscaling.html>) — Tăng hoặc giảm số task dựa trên một tập điều chỉnh scale, gọi là **step adjustment**, thay đổi theo **mức độ vượt ngưỡng** của alarm.
- *Use scheduled actions to scale Amazon ECS services* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-autoscaling-schedulescaling.html>) — Tăng hoặc giảm số task dựa trên **ngày và giờ**.
- *Use historical patterns to scale Amazon ECS services with predictive scaling* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/predictive-auto-scaling.html>) — Tăng hoặc giảm số task dựa trên phân tích **dữ liệu tải lịch sử** để phát hiện mẫu traffic **theo ngày hoặc theo tuần**.
- *Scale Amazon ECS services based on an Amazon SQS queue* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-autoscaling-queue.html>) — Tăng hoặc giảm số task dựa trên **lượng message tồn đọng** trong một hàng đợi Amazon SQS, dùng custom metric **backlog per task**. Hữu ích cho ứng dụng bất đồng bộ, nơi task Amazon ECS làm worker tiêu thụ message từ hàng đợi.

**💡 Giải thích thêm**

| Kiểu | Dựa trên | Phù hợp theo trang |
|---|---|---|
| **Target tracking** | Giá trị mục tiêu của một metric | Dễ dùng nhất — "như máy điều nhiệt" |
| **Step scaling** | Step adjustment theo mức vượt ngưỡng alarm | Phản ứng nhanh với thay đổi nhu cầu |
| **Scheduled** | Ngày và giờ | Thay đổi theo lịch |
| **Predictive** | Dữ liệu tải lịch sử (mẫu ngày/tuần) | Traffic có chu kỳ |
| **SQS queue** | Backlog per task (custom metric) | Worker xử lý hàng đợi bất đồng bộ |

---

## 2. Những điều cần cân nhắc

**📘 Theo docs AWS**

Khi dùng scaling policy, hãy cân nhắc các điểm sau:

- Amazon ECS gửi metric tới CloudWatch theo **chu kỳ 1 phút**. Metric chưa có cho tới khi cluster và service gửi metric lên CloudWatch, và bạn không thể tạo CloudWatch alarm cho metric chưa tồn tại.

- Scaling policy hỗ trợ **cooldown period**. Đây là số giây chờ để hoạt động scale trước đó có hiệu lực.
  - Với sự kiện **scale-out**, mục đích là scale out **liên tục** (nhưng **không quá mức**). Sau khi Service Auto Scaling scale out thành công bằng một scaling policy, nó bắt đầu tính thời gian cooldown. Scaling policy sẽ **không tăng** desired capacity nữa trừ khi có một lần scale out **lớn hơn** được khởi tạo, hoặc cooldown kết thúc. Trong lúc scale-out cooldown có hiệu lực, capacity được thêm bởi hoạt động scale-out khởi tạo được **tính vào** desired capacity cho lần scale-out tiếp theo.
  - Với sự kiện **scale-in**, mục đích là scale in **thận trọng** để bảo vệ tính sẵn sàng của ứng dụng, nên các hoạt động scale-in **bị chặn** cho tới khi cooldown hết hạn. Tuy nhiên, nếu một alarm khác khởi tạo scale-out trong lúc scale-in cooldown, Service Auto Scaling **scale out ngay** target. Trong trường hợp này, scale-in cooldown **dừng** và không hoàn tất.

- Service scheduler **luôn tôn trọng** desired count, nhưng chừng nào service còn có scaling policy và alarm đang hoạt động, Service Auto Scaling **có thể thay đổi** desired count mà bạn đặt **thủ công**.

- Nếu desired count của service được đặt **dưới** giá trị **minimum capacity**, và một alarm khởi tạo scale-out, Service Auto Scaling scale desired count **lên tới minimum capacity** rồi tiếp tục scale out theo nhu cầu, dựa trên scaling policy gắn với alarm. Tuy nhiên, một hoạt động scale-in **không điều chỉnh** desired count, vì nó đã ở dưới minimum capacity.

- Nếu desired count của service được đặt **trên** giá trị **maximum capacity**, và một alarm khởi tạo scale-in, Service Auto Scaling scale desired count **ra tới maximum capacity** rồi tiếp tục scale in theo nhu cầu, dựa trên scaling policy gắn với alarm. Tuy nhiên, một hoạt động scale-out **không điều chỉnh** desired count, vì nó đã ở trên maximum capacity.

- Trong các hoạt động scale, **số task đang chạy thực tế** trong service là giá trị Service Auto Scaling dùng làm **điểm xuất phát**, **không phải** desired count. Đây là capacity xử lý thực sự. Điều này ngăn việc scale **quá mức (runaway)** mà có thể không đáp ứng được, ví dụ khi không đủ tài nguyên container instance để đặt thêm task. Nếu capacity container instance có sẵn sau đó, hoạt động scale đang chờ có thể thành công, và các hoạt động scale tiếp theo có thể tiếp tục sau cooldown.

- Nếu bạn muốn số task **scale về 0** khi không có việc, hãy đặt **minimum capacity là 0**. Với target tracking scaling policy, khi capacity thực tế là 0 và metric cho thấy có nhu cầu workload, Service Auto Scaling **chờ một data point** được gửi trước khi scale out. Trong trường hợp này, nó scale out **mức nhỏ nhất có thể** làm điểm khởi đầu rồi tiếp tục scale dựa trên số task đang chạy thực tế.

- Application Auto Scaling **tắt scale-in** trong khi Amazon ECS deployment đang diễn ra. Tuy nhiên, **scale-out vẫn tiếp tục** trong lúc deployment, trừ khi bị tạm dừng. Hành vi này **không áp dụng** cho Amazon ECS service dùng **external deployment controller**. Thông tin thêm, xem *Service auto scaling and deployments* (mục 3).

- Bạn có nhiều lựa chọn Application Auto Scaling cho task Amazon ECS. **Target tracking** là chế độ **dễ dùng nhất**. Với nó, bạn chỉ cần đặt giá trị mục tiêu cho một metric, như CPU utilization trung bình. Sau đó, auto scaler tự quản lý số task cần để đạt giá trị đó. Với **step scaling**, bạn có thể phản ứng **nhanh hơn** với thay đổi nhu cầu, vì bạn định nghĩa ngưỡng cụ thể cho metric scale, và số task thêm hoặc bớt khi vượt ngưỡng. Và quan trọng hơn, bạn có thể phản ứng rất nhanh với thay đổi nhu cầu bằng cách giảm thiểu thời gian một alarm ngưỡng ở trạng thái vi phạm.

Thông tin thêm về best practice cho service auto scaling, xem *Optimizing Amazon ECS service auto scaling* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/capacity-autoscaling-best-practice.html>).

**💡 Giải thích thêm**

Cooldown theo trang:

| | Scale-out | Scale-in |
|---|---|---|
| Mục đích | Liên tục nhưng không quá mức | Thận trọng, bảo vệ tính sẵn sàng |
| Trong cooldown | Không tăng thêm, **trừ khi** có scale-out lớn hơn; capacity vừa thêm được tính vào lần sau | **Bị chặn** |
| Ngoại lệ | — | Alarm khác gây scale-out → scale out **ngay**, scale-in cooldown dừng |

Khi desired count nằm ngoài khoảng [min, max]:

| Desired count | Alarm gây | Kết quả theo trang |
|---|---|---|
| **< min** | Scale-out | Nâng lên **min**, rồi scale out tiếp theo policy |
| **< min** | Scale-in | Không đổi (đã dưới min) |
| **> max** | Scale-in | Đưa về **max**, rồi scale in tiếp theo policy |
| **> max** | Scale-out | Không đổi (đã trên max) |

Các điểm khác:

| Điểm | Theo trang |
|---|---|
| Chu kỳ metric | 1 phút; metric chưa có thì chưa tạo alarm được |
| Desired count đặt tay | Có thể bị Service Auto Scaling ghi đè khi có policy/alarm |
| Điểm xuất phát khi scale | **Số task đang chạy thực tế**, không phải desired count — tránh scale quá mức khi thiếu tài nguyên |
| Scale về 0 | Đặt min = 0; target tracking chờ 1 data point rồi scale out mức nhỏ nhất |
| Trong lúc deploy | Scale-in **tắt**; scale-out **vẫn chạy** (trừ khi tạm dừng); không áp dụng cho external deployment controller |
| Target tracking vs step scaling | Target tracking dễ nhất; step scaling phản ứng nhanh hơn |

Câu về trường hợp desired count **trên** max trên trang gốc viết "scales the desired count **out** to the maximum capacity value". Theo ngữ cảnh (đang ở trên max và có scale-in), ý là **đưa về** max; bản dịch giữ chữ "ra tới" theo câu gốc.

---

## 3. Service auto scaling và deployment

**📘 Theo docs AWS**

Application Auto Scaling **tắt scale-in** trong khi Amazon ECS deployment đang diễn ra. Tuy nhiên, **scale-out vẫn tiếp tục** trong lúc deployment, trừ khi bị tạm dừng. Hành vi này không áp dụng cho Amazon ECS service dùng external deployment controller. Nếu bạn muốn **tạm dừng scale-out** trong khi deployment đang diễn ra, làm theo các bước sau.

1. Gọi lệnh **`describe-scalable-targets`** (<https://docs.aws.amazon.com/cli/latest/reference/application-autoscaling/describe-scalable-targets.html>), chỉ định resource ID của service gắn với scalable target trong Application Auto Scaling (Ví dụ: `service/default/sample-webapp`). **Ghi lại output.** Bạn sẽ cần nó khi gọi lệnh tiếp theo.
2. Gọi lệnh **`register-scalable-target`** (<https://docs.aws.amazon.com/cli/latest/reference/application-autoscaling/register-scalable-target.html>), chỉ định resource ID, namespace và scalable dimension. Chỉ định **`true`** cho cả **`DynamicScalingInSuspended`** và **`DynamicScalingOutSuspended`**.
3. Sau khi deployment hoàn tất, bạn có thể gọi lệnh `register-scalable-target` (<https://docs.aws.amazon.com/cli/latest/reference/application-autoscaling/register-scalable-target.html>) để **tiếp tục** scale.

Thông tin thêm, xem *Suspending and resuming scaling for Application Auto Scaling* (<https://docs.aws.amazon.com/autoscaling/application/userguide/application-auto-scaling-suspend-resume-scaling.html>).

**💡 Giải thích thêm**

Hành vi mặc định trong lúc deploy:

| | Scale-in | Scale-out |
|---|---|---|
| Mặc định | Tắt | Vẫn chạy |
| External deployment controller | Không áp dụng hành vi trên | |

Tạm dừng scale-out trong lúc deploy, theo các bước của trang:

```
1. describe-scalable-targets   (resource ID, vd service/default/sample-webapp) → ghi lại output
2. register-scalable-target    DynamicScalingInSuspended=true, DynamicScalingOutSuspended=true
   ... deploy ...
3. register-scalable-target    (sau khi deploy xong) → tiếp tục scale
```

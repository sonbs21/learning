# Use historical patterns to scale Amazon ECS services with predictive scaling

> Nguồn: **Amazon ECS Developer Guide — Use historical patterns to scale Amazon ECS services with predictive scaling**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/predictive-auto-scaling.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: **bản dịch** sang tiếng Việt, đi đúng thứ tự và đúng nội dung của trang gốc, không thêm, không bớt ý.
> - Khối **💡 Giải thích thêm**: chỉ giải thích lại những gì **chính trang này** đã nói, cho dễ hiểu hơn. Không đưa thông tin từ trang khác hay ví dụ tự đặt.
> - Tên chế độ, API, thuộc tính, Region giữ nguyên tiếng Anh.
> - Tài liệu AWS được phát hành theo giấy phép **Creative Commons Attribution-ShareAlike 4.0**; bản dịch này ghi rõ nguồn ở trên.

---

## Mục lục

0. [Phần mở đầu](#0-phần-mở-đầu)
1. [Predictive scaling hoạt động thế nào trong Amazon ECS](#1-predictive-scaling-hoạt-động-thế-nào-trong-amazon-ecs)
   - [1.1 Những điều cần cân nhắc](#11-những-điều-cần-cân-nhắc)
   - [1.2 Cách predictive scaling hoạt động](#12-cách-predictive-scaling-hoạt-động)
   - [1.3 Giới hạn số task tối đa](#13-giới-hạn-số-task-tối-đa)
   - [1.4 Các Region hỗ trợ](#14-các-region-hỗ-trợ)

---

## 0. Phần mở đầu

**📘 Theo docs AWS**

**Predictive scaling** xem **dữ liệu tải trong quá khứ** từ luồng traffic để phân tích **mẫu theo ngày hoặc theo tuần**. Sau đó nó dùng phân tích này để **dự đoán nhu cầu tương lai** và **chủ động tăng** task trong service khi cần.

Predictive auto scaling hữu ích nhất trong các tình huống sau.

- **Traffic có chu kỳ** ‐ Dùng nhiều tài nguyên trong giờ làm việc thông thường, và dùng ít tài nguyên vào buổi tối và cuối tuần.
- **Mẫu workload bật/tắt lặp lại** ‐ Ví dụ gồm batch processing, kiểm thử, hoặc phân tích dữ liệu định kỳ.
- **Ứng dụng có thời gian khởi tạo dài** ‐ Điều này có thể ảnh hưởng hiệu năng ứng dụng trong các lần scale-out, gây độ trễ đáng kể.

Nếu ứng dụng mất nhiều thời gian để khởi tạo và traffic tăng theo một mẫu đều đặn, bạn nên cân nhắc dùng predictive scaling. Nó giúp bạn scale nhanh hơn bằng cách **chủ động tăng** số task cho tải đã dự báo, thay vì chỉ dùng các dynamic scaling policy như Target Tracking hay Step Scaling. Bằng cách giúp tránh khả năng **cấp dư** số task, predictive scaling cũng có thể giúp **tiết kiệm chi phí**.

Ví dụ, xét một ứng dụng dùng nhiều trong giờ làm việc và dùng ít vào ban đêm. Vào đầu mỗi ngày làm việc, predictive scaling có thể scale out task **trước** đợt traffic đầu tiên. Điều này giúp ứng dụng duy trì tính sẵn sàng cao và hiệu năng khi chuyển từ giai đoạn sử dụng thấp sang giai đoạn sử dụng cao. Bạn không phải chờ dynamic scaling phản ứng với traffic thay đổi. Bạn cũng không phải mất thời gian xem lại mẫu tải của ứng dụng và cố gắng lên lịch đúng số task bằng scheduled scaling.

Predictive scaling là khả năng **ở mức service**, scale task của service **độc lập** với việc scale capacity compute bên dưới (ví dụ EC2 hoặc Fargate). Với **Fargate**, AWS quản lý và tự động scale capacity bên dưới dựa trên yêu cầu của task. Với capacity **EC2**, bạn có thể dùng **capacity provider Auto Scaling group** để tự động scale EC2 instance bên dưới dựa trên yêu cầu scale của task.

**Nội dung**

- Predictive scaling overview (mục 1 bên dưới)
- *Create a predictive scaling policy* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/predictive-scaling-create-policy.html>
- *Evaluate your predictive scaling policies* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/predictive-scaling-graphs.html>
- *Override the forecast* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/predictive-scaling-overriding-forecast-capacity.html>
- *Use custom metrics* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/predictive-scaling-custom-metrics.html>

**💡 Giải thích thêm**

| Tình huống hợp với predictive scaling | Ví dụ theo trang |
|---|---|
| Traffic có chu kỳ | Cao giờ làm việc, thấp buổi tối/cuối tuần |
| Workload bật/tắt lặp lại | Batch, kiểm thử, phân tích dữ liệu định kỳ |
| Ứng dụng khởi tạo lâu | Scale-out bị trễ đáng kể |

So với các cách khác theo trang:

| | Dynamic scaling (target tracking, step) | Scheduled scaling | Predictive scaling |
|---|---|---|---|
| Thời điểm scale | Sau khi traffic đổi (phản ứng) | Theo lịch bạn đặt | **Trước** đợt traffic, theo dự báo |
| Công sức | — | Phải tự xem mẫu tải và đặt lịch | Tự phân tích dữ liệu lịch sử |

Phạm vi: predictive scaling chỉ scale **task**; capacity bên dưới do Fargate (AWS lo) hoặc ASG capacity provider (EC2) lo.

---

## 1. Predictive scaling hoạt động thế nào trong Amazon ECS

**📘 Theo docs AWS**

Ở đây bạn tìm hiểu những điều cần cân nhắc khi dùng predictive scaling, cách nó hoạt động, và các giới hạn.

### 1.1 Những điều cần cân nhắc

**📘 Theo docs AWS**

- Bạn muốn đảm bảo predictive scaling phù hợp với workload. Bạn có thể kiểm tra bằng cách cấu hình scaling policy ở chế độ **forecast only** và xem console khuyến nghị gì. Bạn nên **đánh giá dự báo và khuyến nghị** trước khi bắt đầu dùng predictive scaling.

- Trước khi predictive scaling có thể bắt đầu dự báo, nó cần **ít nhất 24 giờ** dữ liệu lịch sử. Càng nhiều dữ liệu lịch sử, dự báo càng hiệu quả, **lý tưởng là hai tuần**. Bạn cũng cần **chờ 24 giờ** trước khi predictive scaling có thể tạo dự báo mới khi bạn **xoá một Amazon ECS service và tạo service mới**. Một cách để tăng tốc là dùng **custom metric** để tổng hợp metric giữa service Amazon ECS cũ và mới.

- Chọn một **load metric** phản ánh chính xác **toàn bộ tải** của ứng dụng và là khía cạnh **quan trọng nhất** để scale theo.

- Dynamic scaling kết hợp predictive scaling giúp bạn bám sát nhu cầu của ứng dụng, để có thể scale in khi vắng và scale out khi traffic tăng bất ngờ. Khi **nhiều scaling policy** cùng hoạt động, mỗi policy **tự xác định** số task mong muốn, và số task mong muốn được đặt bằng **giá trị lớn nhất** trong số đó.

- Bạn có thể dùng predictive scaling **cùng với** các dynamic scaling policy, như target tracking hoặc step scaling, để ứng dụng scale dựa trên cả mẫu **thời gian thực** lẫn **lịch sử**. **Tự bản thân predictive scaling không scale in** task.

- Nếu bạn dùng **custom role** khi gọi API `register-scalable-target`, bạn có thể gặp lỗi nói predictive scaling policy chỉ hoạt động khi **SLR** được bật. Trong trường hợp này, hãy gọi lại `register-scalable-target` nhưng **không có role-arn**. Dùng SLR khi đăng ký scalable target và gọi API `put-scaling-policy`.

**💡 Giải thích thêm**

| Điểm | Theo trang |
|---|---|
| Thử trước | Chế độ **forecast only** — đánh giá dự báo trước khi bật scale thật |
| Dữ liệu lịch sử | Tối thiểu **24 giờ**; lý tưởng **2 tuần** |
| Xoá service rồi tạo mới | Chờ 24 giờ mới có dự báo mới; tăng tốc bằng custom metric gộp service cũ + mới |
| Load metric | Phản ánh toàn bộ tải, là yếu tố quan trọng nhất để scale |
| Nhiều policy cùng lúc | Desired count = **giá trị lớn nhất** trong các policy |
| Scale in | Predictive scaling **tự nó không** scale in → dùng kèm dynamic scaling |
| Lỗi "SLR" với custom role | Gọi lại `register-scalable-target` **không** có role-arn (dùng SLR), rồi `put-scaling-policy` |

```
Predictive scaling (theo lịch sử)  ─┐
Target tracking / step (thời gian thực) ─┼─► desired count = MAX(các policy)
                                     ─┘
Scale in: chỉ đến từ dynamic scaling (predictive không scale in)
```

### 1.2 Cách predictive scaling hoạt động

**📘 Theo docs AWS**

Bạn dùng predictive scaling bằng cách tạo một **predictive scaling policy** chỉ định CloudWatch metric cần theo dõi và phân tích. Predictive scaling phải có **ít nhất 24 giờ** dữ liệu để bắt đầu dự báo giá trị tương lai.

Sau khi tạo policy, predictive scaling bắt đầu phân tích dữ liệu metric từ **tối đa 14 ngày** trước đó để nhận diện mẫu. Phân tích này được dùng để tạo dự báo **theo giờ** cho **48 giờ tiếp theo**. Dữ liệu CloudWatch mới nhất được dùng để **cập nhật dự báo mỗi sáu giờ**. Khi có dữ liệu mới, predictive scaling liên tục cải thiện độ chính xác của các dự báo tương lai.

Khi bạn **bật predictive scaling lần đầu**, nó chạy ở chế độ **forecast only**. Nó tạo dự báo ở chế độ này, nhưng **không scale** Amazon ECS service dựa trên các dự báo đó. Nghĩa là bạn có thể đánh giá độ chính xác và mức phù hợp của dự báo. Bạn xem dữ liệu dự báo bằng thao tác API **`GetPredictiveScalingForecast`** hoặc AWS Management Console.

Khi bạn quyết định bắt đầu dùng predictive scaling, chuyển scaling policy sang chế độ **forecast and scale**. Sau đây là điều xảy ra ở chế độ này.

Amazon ECS service được scale **vào đầu mỗi giờ** dựa trên dự báo cho giờ đó, theo mặc định. Bạn có thể chọn bắt đầu **sớm hơn** bằng thuộc tính **`SchedulingBufferTime`** trong thao tác API `PutScalingPolicy`. Điều này làm task mới khởi chạy **trước** nhu cầu dự báo và cho chúng thời gian khởi động và sẵn sàng xử lý traffic.

**💡 Giải thích thêm**

| Thông số | Giá trị theo trang |
|---|---|
| Dữ liệu tối thiểu để dự báo | 24 giờ |
| Dữ liệu được phân tích | Tối đa 14 ngày trước đó |
| Dự báo | Theo giờ, cho 48 giờ tới |
| Cập nhật dự báo | Mỗi 6 giờ |
| Thời điểm scale (forecast and scale) | Đầu mỗi giờ (mặc định); sớm hơn bằng `SchedulingBufferTime` |

Hai chế độ:

| Chế độ | Tạo dự báo | Scale service | Dùng khi |
|---|---|---|---|
| **forecast only** (mặc định khi bật lần đầu) | ✅ | ❌ | Đánh giá dự báo — xem bằng `GetPredictiveScalingForecast` hoặc console |
| **forecast and scale** | ✅ | ✅ | Đã tin dự báo |

```
Bật policy ─► forecast only (xem dự báo, không scale)
             ─ đánh giá ổn ─► forecast and scale
                               └─ scale đầu mỗi giờ (hoặc sớm hơn với SchedulingBufferTime
                                  để task kịp khởi động trước nhu cầu)
```

### 1.3 Giới hạn số task tối đa

**📘 Theo docs AWS**

Khi đăng ký Amazon ECS service để scale, bạn định nghĩa **số task tối đa** có thể khởi chạy cho mỗi service. Theo mặc định, khi đặt scaling policy, chúng **không thể** tăng số task vượt giới hạn tối đa.

Ngoài ra, bạn có thể cho phép số task tối đa của service được **tự động tăng** nếu dự báo **gần chạm hoặc vượt** số task tối đa của Amazon ECS service.

> **Warning:** Hãy **thận trọng** khi cho phép số task tối đa tự động tăng. Điều này có thể dẫn tới **khởi chạy nhiều task hơn dự định**, nếu số task tối đa đã tăng không được theo dõi và quản lý. Số task tối đa đã tăng sau đó trở thành **số task tối đa mới** của Amazon ECS service cho tới khi bạn **cập nhật thủ công**. Số task tối đa **không tự giảm** về giá trị ban đầu.

**💡 Giải thích thêm**

| Cài đặt | Hành vi theo trang |
|---|---|
| Mặc định | Không vượt số task tối đa đã đăng ký |
| Cho phép tự tăng max | Khi dự báo gần/vượt max → max tự tăng |
| Rủi ro (Warning) | Max mới **giữ nguyên** tới khi bạn sửa tay; **không tự giảm** → có thể chạy nhiều task hơn dự định |

### 1.4 Các Region hỗ trợ

**📘 Theo docs AWS**

- US East (N. Virginia)
- US East (Ohio)
- US West (N. California)
- US West (Oregon)
- Africa (Cape Town)
- Asia Pacific (Hong Kong)
- Asia Pacific (Hyderabad)
- Asia Pacific (Jakarta)
- Asia Pacific (Malaysia)
- Asia Pacific (Melbourne)
- Asia Pacific (Mumbai)
- Asia Pacific (New Zealand)
- Asia Pacific (Osaka)
- Asia Pacific (Seoul)
- Asia Pacific (Singapore)
- Asia Pacific (Sydney)
- Asia Pacific (Taipei)
- Asia Pacific (Thailand)
- Asia Pacific (Tokyo)
- Canada (Central)
- Canada West (Calgary)
- China (Beijing)
- China (Ningxia)
- Europe (Frankfurt)
- Europe (Ireland)
- Europe (London)
- Europe (Milan)
- Europe (Paris)
- Europe (Spain)
- Europe (Stockholm)
- Europe (Zurich)
- Israel (Tel Aviv)
- Mexico (Central)
- Middle East (Bahrain)
- South America (São Paulo)
- AWS GovCloud (US-East)
- AWS GovCloud (US-West)

**💡 Giải thích thêm**

Ghi chú nhỏ về trang gốc: có hai lỗi đánh máy — "predicitive" (đúng là "predictive") và "wait 24 hours **for before**" (thừa chữ "for"). Không ảnh hưởng nghĩa.

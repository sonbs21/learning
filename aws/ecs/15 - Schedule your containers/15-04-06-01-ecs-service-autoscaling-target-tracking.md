# Use a target metric to scale Amazon ECS services

> Nguồn: **Amazon ECS Developer Guide — Use a target metric to scale Amazon ECS services**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-autoscaling-targettracking.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: **bản dịch** sang tiếng Việt, đi đúng thứ tự và đúng nội dung của trang gốc, không thêm, không bớt ý.
> - Khối **💡 Giải thích thêm**: chỉ giải thích lại những gì **chính trang này** đã nói, cho dễ hiểu hơn. Không đưa thông tin từ trang khác hay ví dụ tự đặt.
> - Tên metric, kiểu policy giữ nguyên tiếng Anh.
> - Tài liệu AWS được phát hành theo giấy phép **Creative Commons Attribution-ShareAlike 4.0**; bản dịch này ghi rõ nguồn ở trên.

---

## Mục lục

0. [Phần mở đầu](#0-phần-mở-đầu)
1. [Những điều cần cân nhắc](#1-những-điều-cần-cân-nhắc)

---

## 0. Phần mở đầu

**📘 Theo docs AWS**

Với **target tracking scaling policy**, bạn chọn **một metric** và đặt **một giá trị mục tiêu**. Amazon ECS Service Auto Scaling **tạo và quản lý** các CloudWatch alarm điều khiển scaling policy, và **tính toán mức điều chỉnh scale** dựa trên metric và giá trị mục tiêu. Scaling policy thêm hoặc bớt task của service khi cần để giữ metric **ở bằng hoặc gần** giá trị mục tiêu. Ngoài việc giữ metric gần giá trị mục tiêu, target tracking scaling policy cũng **thích nghi** với dao động của metric do tải dao động, và **giảm thiểu dao động nhanh** về số task đang chạy trong service.

Target tracking policy **loại bỏ nhu cầu tự định nghĩa** CloudWatch alarm và mức điều chỉnh scale. Amazon ECS xử lý việc này **tự động** dựa trên mục tiêu bạn đặt.

**💡 Giải thích thêm**

```
Bạn:              chọn metric + đặt giá trị mục tiêu
Service Auto Scaling: tạo/quản lý CloudWatch alarm, tính mức điều chỉnh
Kết quả:          thêm/bớt task để metric ≈ mục tiêu, hạn chế số task dao động nhanh
```

| Bạn làm | Service Auto Scaling làm |
|---|---|
| Chọn metric | Tạo và quản lý CloudWatch alarm |
| Đặt giá trị mục tiêu | Tính mức điều chỉnh, thêm/bớt task |

---

## 1. Những điều cần cân nhắc

**📘 Theo docs AWS**

Cân nhắc các điểm sau khi dùng target tracking policy:

- Target tracking scaling policy giả định rằng nó nên **scale out khi metric chỉ định cao hơn** giá trị mục tiêu. Bạn **không thể** dùng target tracking scaling policy để scale out khi metric chỉ định **thấp hơn** giá trị mục tiêu.
- Target tracking scaling policy **không thực hiện scale** khi metric chỉ định **không đủ dữ liệu**. Nó không scale in vì nó **không coi** việc thiếu dữ liệu là mức sử dụng thấp.
- Bạn có thể thấy **khoảng chênh** giữa giá trị mục tiêu và các data point thực tế của metric. Đó là vì Service Auto Scaling luôn hành động **thận trọng** bằng cách **làm tròn lên hoặc xuống** khi xác định lượng capacity cần thêm hoặc bớt. Điều này ngăn nó thêm **không đủ** capacity hoặc bớt **quá nhiều** capacity.
- Để đảm bảo tính sẵn sàng của ứng dụng, service **scale out tỉ lệ với metric nhanh nhất có thể**, nhưng **scale in từ từ hơn**.
- Application Auto Scaling **tắt scale-in** trong khi Amazon ECS deployment đang diễn ra. Tuy nhiên, scale-out vẫn tiếp tục trong lúc deployment, trừ khi bị tạm dừng. Hành vi này không áp dụng cho Amazon ECS service dùng external deployment controller. Thông tin thêm, xem *Service auto scaling and deployments* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-auto-scaling.html#service-auto-scaling-deployments>).
- Bạn có thể có **nhiều** target tracking scaling policy cho một Amazon ECS service, **miễn là mỗi policy dùng một metric khác nhau**. Mục đích của Service Auto Scaling là luôn **ưu tiên tính sẵn sàng**, nên hành vi của nó khác nhau tuỳ các target tracking policy sẵn sàng scale out hay scale in. Nó sẽ **scale out** service nếu **bất kỳ** target tracking policy nào sẵn sàng scale out, nhưng chỉ **scale in** nếu **tất cả** target tracking policy (có bật phần scale-in) đều sẵn sàng scale in.
- **Không** sửa hay xoá các CloudWatch alarm mà Service Auto Scaling quản lý cho target tracking scaling policy. Service Auto Scaling **tự xoá** các alarm khi bạn xoá scaling policy.
- Metric **`ALBRequestCountPerTarget`** cho target tracking scaling policy **không được hỗ trợ** với kiểu deployment **blue/green**.

Thông tin thêm về target tracking scaling policy, xem *Target tracking scaling policies* (<https://docs.aws.amazon.com/autoscaling/application/userguide/application-auto-scaling-target-tracking.html>) trong *Application Auto Scaling User Guide*.

**💡 Giải thích thêm**

| Tình huống | Hành vi theo trang |
|---|---|
| Metric **> mục tiêu** | Scale out |
| Muốn scale out khi metric **< mục tiêu** | **Không** làm được bằng target tracking |
| Metric **thiếu dữ liệu** | Không scale (không coi là tải thấp) |
| Lệch giữa mục tiêu và thực tế | Bình thường — do làm tròn thận trọng |
| Tốc độ | Scale out **nhanh**, scale in **từ từ** |
| Trong lúc deploy | Scale-in tắt, scale-out vẫn chạy (trừ external deployment controller) |
| Alarm do Service Auto Scaling tạo | Không sửa/xoá; tự xoá khi xoá policy |
| `ALBRequestCountPerTarget` + blue/green | Không hỗ trợ |

Nhiều target tracking policy trên một service (mỗi cái một metric khác nhau):

```
Scale OUT  nếu  BẤT KỲ policy nào muốn scale out
Scale IN   chỉ khi  TẤT CẢ policy (có bật scale-in) đều muốn scale in
                 → ưu tiên tính sẵn sàng
```

Ví dụ minh hoạ theo đúng quy tắc trên (hai policy CPU và memory): CPU vượt mục tiêu còn memory thấp → **scale out**; CPU thấp nhưng memory chưa thấp → **không scale in**.

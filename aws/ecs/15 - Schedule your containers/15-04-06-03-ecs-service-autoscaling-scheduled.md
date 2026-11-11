# Use scheduled actions to scale Amazon ECS services

> Nguồn: **Amazon ECS Developer Guide — Use scheduled actions to scale Amazon ECS services**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-autoscaling-schedulescaling.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: **bản dịch** sang tiếng Việt, đi đúng thứ tự và đúng nội dung của trang gốc, không thêm, không bớt ý.
> - Khối **💡 Giải thích thêm**: chỉ giải thích lại những gì **chính trang này** đã nói, cho dễ hiểu hơn. Không đưa thông tin từ trang khác hay ví dụ tự đặt.
> - Tên kiểu scaling giữ nguyên tiếng Anh.
> - Tài liệu AWS được phát hành theo giấy phép **Creative Commons Attribution-ShareAlike 4.0**; bản dịch này ghi rõ nguồn ở trên.

---

## Nội dung trang

**📘 Theo docs AWS**

Với **scheduled scaling**, bạn có thể thiết lập tự động scale cho ứng dụng dựa trên các **thay đổi tải dự đoán được**, bằng cách tạo **scheduled action** tăng hoặc giảm số task vào **những thời điểm cụ thể**. Điều này cho phép bạn scale ứng dụng **chủ động** để khớp với thay đổi tải dự đoán được.

Các scheduled scaling action này giúp bạn **tối ưu chi phí và hiệu năng**. Ứng dụng có đủ số task để xử lý **đỉnh traffic giữa tuần**, nhưng **không cấp dư** số task vào những lúc khác.

Bạn có thể dùng **scheduled scaling và scaling policy cùng nhau** để có lợi ích của cả hai cách scale **chủ động (proactive)** và **phản ứng (reactive)**. Sau khi một scheduled scaling action chạy, scaling policy có thể tiếp tục quyết định có scale thêm số task hay không. Điều này giúp đảm bảo bạn có đủ task để xử lý tải của ứng dụng. Trong khi ứng dụng scale để khớp nhu cầu, capacity hiện tại **phải nằm trong** số task **tối thiểu và tối đa** được đặt bởi scheduled action.

Bạn có thể cấu hình scheduled scaling bằng **AWS CLI**. Thông tin thêm về scheduled scaling, xem *Scheduled Scaling* (<https://docs.aws.amazon.com/autoscaling/application/userguide/application-auto-scaling-scheduled-scaling.html>) trong *Application Auto Scaling User Guide*.

**💡 Giải thích thêm**

| Cách scale | Kích hoạt bởi | Kiểu |
|---|---|---|
| Scheduled action | Thời điểm cụ thể (tải dự đoán được) | **Chủ động** |
| Scaling policy | Metric/alarm | **Phản ứng** |

Dùng kết hợp theo trang:

```
Scheduled action chạy  → đặt khoảng [min, max] số task cho thời điểm đó
Scaling policy         → tiếp tục scale theo nhu cầu, nhưng luôn nằm trong [min, max] của scheduled action
```

Ví dụ trên trang: đủ task cho **đỉnh traffic giữa tuần**, không cấp dư vào lúc khác. Cấu hình bằng **AWS CLI** (theo trang).

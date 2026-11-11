# Use predefined increments based on CloudWatch alarms to scale Amazon ECS services

> Nguồn: **Amazon ECS Developer Guide — Use predefined increments based on CloudWatch alarms to scale Amazon ECS services**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-autoscaling-stepscaling.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: **bản dịch** sang tiếng Việt, đi đúng thứ tự và đúng nội dung của trang gốc, không thêm, không bớt ý.
> - Khối **💡 Giải thích thêm**: chỉ giải thích lại những gì **chính trang này** đã nói, cho dễ hiểu hơn. Không đưa thông tin từ trang khác hay ví dụ tự đặt.
> - Tên kiểu policy giữ nguyên tiếng Anh.
> - Tài liệu AWS được phát hành theo giấy phép **Creative Commons Attribution-ShareAlike 4.0**; bản dịch này ghi rõ nguồn ở trên.

---

## Nội dung trang

**📘 Theo docs AWS**

Với **step scaling policy**, **bạn tạo và quản lý** các CloudWatch alarm kích hoạt quá trình scale. Khi một alarm bị vượt ngưỡng, Amazon ECS khởi chạy scaling policy gắn với alarm đó. Step scaling policy scale task bằng một tập điều chỉnh, gọi là **step adjustment**. **Mức điều chỉnh thay đổi theo độ lớn** của việc vượt ngưỡng alarm.

- Nếu mức vượt **qua ngưỡng thứ nhất**, Amazon ECS áp dụng **step adjustment thứ nhất**.
- Nếu mức vượt **qua ngưỡng thứ hai**, Amazon ECS áp dụng **step adjustment thứ hai**, và cứ thế tiếp tục.

Chúng tôi **khuyến nghị mạnh** dùng **target tracking scaling policy** để scale theo các metric như **CPU utilization trung bình** hoặc **số request trung bình mỗi target**. Các metric **giảm khi capacity tăng** và **tăng khi capacity giảm** có thể dùng để scale out hoặc scale in số task **theo tỉ lệ** bằng target tracking. Điều này giúp Amazon ECS bám sát đường cong nhu cầu của ứng dụng.

**💡 Giải thích thêm**

```
Bạn tạo CloudWatch alarm ─► alarm vượt ngưỡng ─► ECS chạy step scaling policy gắn với alarm
                                                     │
                     vượt ngưỡng 1 → step adjustment 1
                     vượt ngưỡng 2 → step adjustment 2
                     ...           → ...
```

| | Step scaling (trang này) | Target tracking (trang khuyến nghị) |
|---|---|---|
| CloudWatch alarm | **Bạn** tạo và quản lý | (trang không nêu chi tiết) |
| Cách scale | Step adjustment theo độ lớn vượt ngưỡng | Theo tỉ lệ với metric |
| Trang khuyến nghị cho | — | CPU trung bình, số request trung bình mỗi target |

Điều kiện của metric hợp với target tracking theo trang: **giảm khi capacity tăng**, **tăng khi capacity giảm**.

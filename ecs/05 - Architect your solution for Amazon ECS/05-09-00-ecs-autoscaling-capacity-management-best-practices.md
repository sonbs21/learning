# Amazon ECS Auto scaling and capacity management best practices

> Nguồn: **Amazon ECS Developer Guide — Amazon ECS Auto scaling and capacity management best practices**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/capacity-availability.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: **bản dịch** sang tiếng Việt, đi đúng thứ tự và đúng nội dung của trang gốc, không thêm, không bớt ý.
> - Khối **💡 Giải thích thêm**: chỉ giải thích lại những gì **chính trang này** đã nói, cho dễ hiểu hơn. Không đưa thông tin từ trang khác hay ví dụ tự đặt.
> - Tên các trang con giữ nguyên tiếng Anh.
> - Tài liệu AWS được phát hành theo giấy phép **Creative Commons Attribution-ShareAlike 4.0**; bản dịch này ghi rõ nguồn ở trên.

---

## Nội dung trang

**📘 Theo docs AWS**

Bạn có thể chạy workload ứng dụng container ở **mọi quy mô** trên Amazon ECS. Bao gồm từ môi trường kiểm thử tối thiểu tới môi trường production lớn hoạt động ở quy mô toàn cầu.

Với Amazon ECS, như mọi dịch vụ AWS, bạn **chỉ trả tiền cho những gì bạn dùng**. Khi thiết kế ứng dụng phù hợp, bạn có thể tiết kiệm chi phí bằng cách chỉ dùng tài nguyên bạn cần, vào lúc bạn cần.

Các khuyến nghị sau cho bạn thấy cách chạy workload Amazon ECS để đáp ứng **mục tiêu mức dịch vụ (service-level objectives)** trong khi vẫn vận hành **tiết kiệm chi phí**.

### Các chủ đề

- *Determining the task size for Amazon ECS* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/capacity-tasksize-best-practice.html>
- *Optimizing Amazon ECS service auto scaling* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/capacity-autoscaling-best-practice.html>
- *Amazon ECS capacity and availability* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/capacity-availability-best-practice.html>
- *Amazon ECS cluster capacity* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/capacity-cluster-best-practice.html>
- *Choosing Fargate task sizes for Amazon ECS* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/fargate-task-size-best-practice.html>
- *Speeding up Amazon ECS cluster capacity provisioning with capacity providers on Amazon EC2* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/capacity-cluster-speed-up-ec2-best-practice.html>

**💡 Giải thích thêm**

Trang này là **trang mục lục** cho nhóm best practice về auto scaling và quản lý capacity. Hai mục tiêu được nêu cùng lúc:

| Mục tiêu | Theo trang |
|---|---|
| Đáp ứng service-level objectives | Workload chạy đúng yêu cầu dịch vụ |
| Tiết kiệm chi phí | Chỉ dùng tài nguyên cần, vào lúc cần (trả theo mức dùng) |

Sáu trang con, theo tên gọi:

| # | Trang con | Chủ đề |
|---|---|---|
| 1 | Determining the task size | Xác định kích thước task |
| 2 | Optimizing service auto scaling | Tối ưu auto scaling của service |
| 3 | Capacity and availability | Capacity và tính sẵn sàng |
| 4 | Cluster capacity | Capacity của cluster |
| 5 | Choosing Fargate task sizes | Chọn kích thước task Fargate |
| 6 | Speeding up cluster capacity provisioning (EC2) | Tăng tốc cấp capacity cho cluster với capacity provider trên EC2 |

Nội dung chi tiết nằm ở từng trang con; trang này chỉ có phần mở đầu và danh sách liên kết.

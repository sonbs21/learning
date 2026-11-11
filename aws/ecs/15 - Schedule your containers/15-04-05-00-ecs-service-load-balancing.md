# Use load balancing to distribute Amazon ECS service traffic

> Nguồn: **Amazon ECS Developer Guide — Use load balancing to distribute Amazon ECS service traffic**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-load-balancing.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: **bản dịch** sang tiếng Việt, đi đúng thứ tự và đúng nội dung của trang gốc, không thêm, không bớt ý.
> - Khối **💡 Giải thích thêm**: chỉ giải thích lại những gì **chính trang này** đã nói, cho dễ hiểu hơn. Không đưa thông tin từ trang khác hay ví dụ tự đặt.
> - Tên loại load balancer giữ nguyên tiếng Anh.
> - Tài liệu AWS được phát hành theo giấy phép **Creative Commons Attribution-ShareAlike 4.0**; bản dịch này ghi rõ nguồn ở trên.

---

## Nội dung trang

**📘 Theo docs AWS**

Service có thể **tuỳ chọn** được cấu hình dùng **Elastic Load Balancing** để phân phối traffic **đều** cho các task trong service.

> **Note:** Khi dùng **task set**, mọi task trong set phải **cùng** được cấu hình dùng Elastic Load Balancing, hoặc **cùng không** dùng Elastic Load Balancing.

Amazon ECS service chạy trên AWS Fargate hỗ trợ **Application Load Balancer**, **Network Load Balancer** và **Gateway Load Balancer**. Dùng bảng sau để biết nên dùng loại load balancer nào.

| Loại load balancer | Dùng trong các trường hợp |
|---|---|
| **Application Load Balancer** | Định tuyến traffic HTTP/HTTPS (tầng 7). Application Load Balancer có một số tính năng khiến nó hấp dẫn khi dùng với Amazon ECS service: • Mỗi service có thể nhận traffic từ **nhiều load balancer** và mở **nhiều port** được cân bằng tải bằng cách chỉ định nhiều target group. • Được hỗ trợ bởi task chạy trên **cả Fargate và EC2 instance**. • Application Load Balancer cho phép container dùng **dynamic host port mapping** (để **nhiều task của cùng service** chạy được trên mỗi container instance). • Application Load Balancer hỗ trợ **định tuyến theo path** và **quy tắc ưu tiên** (để **nhiều service** dùng chung một listener port trên **một** Application Load Balancer). |
| **Network Load Balancer** | Định tuyến traffic TCP hoặc UDP (tầng 4). |
| **Gateway Load Balancer** | Định tuyến traffic TCP hoặc UDP (tầng 4). Dùng **thiết bị ảo (virtual appliance)**, như firewall, hệ thống phát hiện và ngăn chặn xâm nhập, và hệ thống kiểm tra gói tin sâu. |

Chúng tôi **khuyến nghị dùng Application Load Balancer** cho Amazon ECS service để tận dụng các tính năng mới nhất này, **trừ khi** service cần một tính năng **chỉ có** ở Network Load Balancer hoặc Gateway Load Balancer. Thông tin thêm về Elastic Load Balancing và sự khác nhau giữa các loại load balancer, xem *Elastic Load Balancing User Guide* (<https://docs.aws.amazon.com/elasticloadbalancing/latest/userguide/>).

Với load balancer, bạn **chỉ trả cho những gì bạn dùng**. Thông tin thêm, xem *Elastic Load Balancing pricing* (<https://aws.amazon.com/elasticloadbalancing/pricing/>).

**💡 Giải thích thêm**

| Loại | Tầng | Khi nào dùng theo trang |
|---|---|---|
| **Application Load Balancer** | 7 (HTTP/HTTPS) | **Mặc định nên dùng** — trừ khi cần tính năng chỉ NLB/GWLB có |
| Network Load Balancer | 4 (TCP/UDP) | Cần tính năng chỉ NLB có |
| Gateway Load Balancer | 4 (TCP/UDP) | Virtual appliance: firewall, IDS/IPS, deep packet inspection |

Bốn tính năng của ALB với ECS service theo bảng:

| Tính năng | Lợi ích |
|---|---|
| Nhiều target group | Một service nhận traffic từ nhiều load balancer, mở nhiều port |
| Hỗ trợ Fargate và EC2 | Dùng được cho cả hai |
| Dynamic host port mapping | Nhiều task cùng service trên một container instance |
| Định tuyến theo path + quy tắc ưu tiên | Nhiều service dùng chung một listener port trên một ALB |

Ràng buộc với **task set**: hoặc **mọi** task trong set dùng ELB, hoặc **không task nào** dùng.

Câu dẫn của bảng nói về service "chạy trên AWS Fargate", trong khi chính dòng ALB của bảng ghi ALB hỗ trợ cả Fargate lẫn EC2 instance.

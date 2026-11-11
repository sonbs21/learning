# Amazon ECS security considerations for when to use Fargate

> Nguồn: **Amazon ECS Developer Guide — Amazon ECS security considerations for when to use Fargate**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/security-fargate-ec2.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: **bản dịch** sang tiếng Việt, đi đúng thứ tự và đúng nội dung của trang gốc, không thêm, không bớt ý.
> - Khối **💡 Giải thích thêm**: chỉ giải thích lại những gì **chính trang này** đã nói, cho dễ hiểu hơn. Không đưa thông tin từ trang khác hay ví dụ tự đặt.
> - Tài liệu AWS được phát hành theo giấy phép **Creative Commons Attribution-ShareAlike 4.0**; bản dịch này ghi rõ nguồn ở trên.

---

## Nội dung trang

**📘 Theo docs AWS**

Chúng tôi khuyến nghị khách hàng cần **cô lập mạnh** cho task nên dùng **Fargate**. Fargate chạy **mỗi task** trong một **môi trường ảo hoá phần cứng**. Điều này đảm bảo các workload container này **không dùng chung** network interface, Fargate ephemeral storage, CPU, hay memory với các task khác.

**💡 Giải thích thêm**

```
Fargate
 ├─ Task A ── môi trường ảo hoá phần cứng riêng: ENI, ephemeral storage, CPU, memory riêng
 ├─ Task B ── môi trường ảo hoá phần cứng riêng: ENI, ephemeral storage, CPU, memory riêng
 └─ ...
```

| Tài nguyên | Dùng chung giữa các task Fargate? (theo trang) |
|---|---|
| Network interface | Không |
| Fargate ephemeral storage | Không |
| CPU | Không |
| Memory | Không |

Khuyến nghị của trang: cần **cô lập mạnh** giữa các task → dùng **Fargate**.

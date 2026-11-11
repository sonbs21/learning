# Enabling TLS for Amazon ECS Service Connect

> Nguồn: **Amazon ECS Developer Guide — Enabling TLS for Amazon ECS Service Connect**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/enable-service-connect-tls.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: **bản dịch** sang tiếng Việt, đi đúng thứ tự và đúng nội dung của trang gốc, không thêm, không bớt ý.
> - Khối **💡 Giải thích thêm**: chỉ giải thích lại những gì **chính trang này** đã nói, cho dễ hiểu hơn. Không đưa thông tin từ trang khác hay ví dụ tự đặt.
> - Tên nút trên console giữ nguyên tiếng Anh (in đậm).
> - Tài liệu AWS được phát hành theo giấy phép **Creative Commons Attribution-ShareAlike 4.0**; bản dịch này ghi rõ nguồn ở trên.

---

## Nội dung trang

**📘 Theo docs AWS**

Bạn bật mã hoá traffic khi **tạo** hoặc **cập nhật** một Service Connect service.

**Bật mã hoá traffic cho một service trong namespace có sẵn bằng AWS Management Console**

1. Bạn cần có **infrastructure IAM role**. Thông tin thêm về role này, xem *Amazon ECS infrastructure IAM role* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/infrastructure_IAM_role.html>).
2. Mở console tại <https://console.aws.amazon.com/ecs/v2>.
3. Ở thanh điều hướng, chọn **Namespaces**.
4. Chọn **Namespace** có **Service** bạn muốn bật mã hoá traffic.
5. Chọn **Service** bạn muốn bật mã hoá traffic.
6. Chọn **Update Service** và cuộn xuống mục Service Connect.
7. Chọn **Turn on traffic encryption** bên dưới thông tin service để bật TLS.
8. Ở **Service Connect TLS role**, chọn một infrastructure IAM role có sẵn hoặc tạo mới.
9. Ở **Signer certificate authority**, chọn một certificate authority có sẵn hoặc tạo mới.

   Thông tin thêm, xem *AWS Private Certificate Authority certificates and Service Connect* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-connect-tls.html#service-connect-tls-certificates>).

10. Ở **Choose an AWS KMS key**, chọn một AWS owned and managed key, hoặc chọn một key khác. Bạn cũng có thể chọn tạo key mới.

Ví dụ dùng AWS CLI để cấu hình TLS cho service, xem *Configuring Amazon ECS Service Connect with the AWS CLI* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/create-service-connect.html>).

**💡 Giải thích thêm**

```
Điều kiện: có infrastructure IAM role
Console → Namespaces → chọn namespace → chọn service → Update Service
   └─ mục Service Connect
        ├─ Turn on traffic encryption
        ├─ Service Connect TLS role       : infrastructure role (có sẵn / tạo mới)
        ├─ Signer certificate authority  : CA (có sẵn / tạo mới)
        └─ Choose an AWS KMS key         : AWS owned key | key khác | tạo mới
```

Ba thứ cần chọn khi bật TLS theo trang:

| Mục trên console | Chọn |
|---|---|
| **Service Connect TLS role** | Infrastructure IAM role (có sẵn hoặc tạo mới) |
| **Signer certificate authority** | Certificate authority (có sẵn hoặc tạo mới) |
| **Choose an AWS KMS key** | AWS owned and managed key, key khác, hoặc tạo mới |

Ghi chú về trang gốc:
- Quy trình dừng ở bước 10, không có bước cuối xác nhận cập nhật service.
- Bước 9 bị lặp chữ ("see see").
- Câu cuối về AWS CLI thiếu động từ "see" ở đầu câu; bản dịch thêm "xem" cho trọn nghĩa.

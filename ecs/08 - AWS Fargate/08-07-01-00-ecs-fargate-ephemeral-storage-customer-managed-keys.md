# Customer managed keys for AWS Fargate ephemeral storage for Amazon ECS

> Nguồn: **Amazon ECS Developer Guide — Customer managed keys for AWS Fargate ephemeral storage for Amazon ECS**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/fargate-storage-encryption.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: **bản dịch** sang tiếng Việt, đi đúng thứ tự và đúng nội dung của trang gốc, không thêm, không bớt ý.
> - Khối **💡 Giải thích thêm**: chỉ giải thích lại những gì **chính trang này** đã nói, cho dễ hiểu hơn. Không đưa thông tin từ trang khác hay ví dụ tự đặt.
> - Tên dịch vụ, thuật ngữ KMS giữ nguyên tiếng Anh.
> - Tài liệu AWS được phát hành theo giấy phép **Creative Commons Attribution-ShareAlike 4.0**; bản dịch này ghi rõ nguồn ở trên.

---

## Nội dung trang

**📘 Theo docs AWS**

AWS Fargate hỗ trợ **customer managed key** để mã hoá dữ liệu của task Amazon ECS lưu trong **ephemeral storage**, giúp khách hàng chịu **ràng buộc quy định** đáp ứng các chính sách bảo mật nội bộ. Khách hàng vẫn có lợi ích **serverless** của Fargate, đồng thời cho **kiểm toán viên tuân thủ** thấy rõ hơn việc mã hoá storage do chính họ quản lý. Dù Fargate **mặc định** đã có mã hoá ephemeral storage **do Fargate quản lý**, khách hàng cũng có thể dùng **khoá tự quản lý** khi mã hoá dữ liệu nhạy cảm như thông tin **tài chính** hay **sức khoẻ**.

Bạn có thể **import khoá của riêng mình** vào AWS KMS hoặc **tạo khoá** trong AWS KMS. Các khoá tự quản lý này được lưu trong AWS KMS và thực hiện các thao tác vòng đời AWS KMS tiêu chuẩn như **xoay vòng (rotate)**, **vô hiệu hoá (disable)** và **xoá (delete)**. Bạn có thể **kiểm toán** việc truy cập và sử dụng khoá trong log **CloudTrail**.

Mặc định, một KMS key hỗ trợ **50.000 grant** mỗi key. Fargate dùng **một AWS KMS grant** cho mỗi task dùng customer managed key, nên hỗ trợ tối đa **50.000 task đồng thời** cho một key. Nếu muốn tăng con số này, bạn có thể yêu cầu **tăng giới hạn**, được duyệt **theo từng trường hợp**.

Fargate **không tính thêm phí** khi dùng customer managed key. Bạn chỉ bị tính **giá tiêu chuẩn** khi dùng AWS KMS key cho storage và API request.

**Các chủ đề**

- *Create an encryption key for Fargate ephemeral storage for Amazon ECS* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/fargate-create-storage-key.html>)
- *Managing AWS KMS keys for Fargate ephemeral storage for Amazon ECS* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/fargate-managing-kms-key.html>)

**💡 Giải thích thêm**

| | Mã hoá mặc định | Customer managed key |
|---|---|---|
| Ai quản lý khoá | **Fargate** | **Bạn** (import hoặc tạo trong AWS KMS) |
| Vòng đời khoá | — | Rotate, disable, delete (thao tác chuẩn của KMS) |
| Kiểm toán | — | Truy cập và sử dụng khoá trong **CloudTrail** |
| Phù hợp | Mặc định | Dữ liệu nhạy cảm (tài chính, sức khoẻ), yêu cầu tuân thủ |
| Phí Fargate thêm | — | **Không** — chỉ phí KMS tiêu chuẩn |

Giới hạn task đồng thời mỗi key theo trang:

```
1 KMS key  → 50.000 grant (mặc định)
1 task     → 1 grant
⇒ tối đa 50.000 task đồng thời dùng chung 1 key  (xin tăng theo từng trường hợp)
```

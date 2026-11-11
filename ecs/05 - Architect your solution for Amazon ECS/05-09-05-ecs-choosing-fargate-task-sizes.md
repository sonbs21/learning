# Choosing Fargate task sizes for Amazon ECS

> Nguồn: **Amazon ECS Developer Guide — Choosing Fargate task sizes for Amazon ECS**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/fargate-task-size-best-practice.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: **bản dịch** sang tiếng Việt, đi đúng thứ tự và đúng nội dung của trang gốc, không thêm, không bớt ý.
> - Khối **💡 Giải thích thêm**: chỉ giải thích lại những gì **chính trang này** đã nói, cho dễ hiểu hơn. Không đưa thông tin từ trang khác hay ví dụ tự đặt.
> - Thuật ngữ reservation, limit giữ nguyên tiếng Anh.
> - Tài liệu AWS được phát hành theo giấy phép **Creative Commons Attribution-ShareAlike 4.0**; bản dịch này ghi rõ nguồn ở trên.

---

## Nội dung trang

**📘 Theo docs AWS**

Với task definition AWS Fargate, bạn **bắt buộc** phải chỉ định CPU và memory **ở mức task**, và **không cần** tính tới bất kỳ phần overhead nào. Bạn cũng có thể chỉ định CPU và memory **ở mức container** cho task Fargate. Tuy nhiên, việc này **không bắt buộc**. Limit tài nguyên phải **lớn hơn hoặc bằng** mọi reservation bạn đã khai báo. Trong đa số trường hợp, bạn có thể đặt chúng bằng **tổng reservation của từng container** khai báo trong task definition. Sau đó, **làm tròn lên** tới kích thước Fargate gần nhất. Thông tin thêm về các kích thước có sẵn, xem *Task CPU and memory* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/fargate-tasks-services.html#fargate-tasks-size>).

**💡 Giải thích thêm**

| Mức khai báo | Bắt buộc? |
|---|---|
| CPU và memory **mức task** | **Có** (không cần cộng thêm overhead) |
| CPU và memory **mức container** | Không |

Quy tắc và cách chọn theo trang:

```
1. Limit mức task ≥ mọi reservation đã khai báo
2. Đặt limit = tổng reservation của các container trong task definition
3. Làm tròn lên kích thước Fargate gần nhất (xem bảng kích thước ở link "Task CPU and memory")
```

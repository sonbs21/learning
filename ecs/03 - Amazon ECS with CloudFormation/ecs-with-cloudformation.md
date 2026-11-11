# Using Amazon ECS with AWS CloudFormation

> Nguồn: **Amazon ECS Developer Guide — Using Amazon ECS with AWS CloudFormation**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/ecs-with-cloudformation.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: **bản dịch** sang tiếng Việt, đi đúng thứ tự và đúng nội dung của trang gốc, không thêm, không bớt ý.
> - Khối **💡 Giải thích thêm**: chỉ giải thích lại những gì **chính trang này** đã nói, cho dễ hiểu hơn. Không đưa thông tin từ trang khác hay ví dụ tự đặt.
> - Thuật ngữ CloudFormation (template, stack, change set) giữ nguyên tiếng Anh.
> - Tài liệu AWS được phát hành theo giấy phép **Creative Commons Attribution-ShareAlike 4.0**; bản dịch này ghi rõ nguồn ở trên.

---

## Nội dung trang

**📘 Theo docs AWS**

Amazon ECS được tích hợp với **AWS CloudFormation**, một dịch vụ bạn có thể dùng để mô hình hoá và thiết lập tài nguyên AWS bằng các **template** do bạn định nghĩa. CloudFormation dùng **template** là file văn bản định dạng **`YAML`** hoặc **`JSON`**. Template giống như **bản thiết kế** cho tài nguyên AWS bạn muốn tạo. Khi bạn tạo và gửi một template, CloudFormation tạo một **stack**. Bạn quản lý các tài nguyên định nghĩa trong template **thông qua stack**. Khi muốn tạo, cập nhật hoặc xoá một tài nguyên, bạn tạo, cập nhật hoặc xoá stack đã được tạo từ tài nguyên đó. Khi cập nhật stack, bạn cần tạo một **change set** trước. Change set cho bạn thấy những gì **bị ảnh hưởng** bởi thay đổi **trước khi** bạn thực hiện nó. Điều này giúp bạn tránh, ví dụ, **vô tình xoá database** do đổi tên database. Thông tin thêm về template, stack và change set, xem *How CloudFormation works* (<https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/cloudformation-overview.html#cfn-concepts-stacks>) trong *AWS CloudFormation User Guide*.

Khi dùng CloudFormation, bạn có thể **tốn ít thời gian hơn** để tạo và quản lý tài nguyên và hạ tầng. Bạn có thể tạo một template mô tả mọi tài nguyên AWS bạn muốn, như Amazon ECS **cluster**, **task definition**, **service**. Sau đó, CloudFormation lo việc **cấp phát và cấu hình** các tài nguyên đó cho bạn.

CloudFormation cũng cho phép bạn **dùng lại** template để thiết lập tài nguyên Amazon ECS một cách **nhất quán và lặp lại được**. Bạn mô tả tài nguyên **một lần** rồi cấp phát lại cùng các tài nguyên đó trên **nhiều AWS account** và **nhiều AWS Region**.

Template CloudFormation có thể được dùng với cả **AWS Management Console** hoặc **AWS Command Line Interface** để tạo tài nguyên.

Để tìm hiểu thêm về CloudFormation, xem các tài liệu sau:

- *AWS CloudFormation* (<https://aws.amazon.com/cloudformation/>)
- *AWS CloudFormation User Guide* (<https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/Welcome.html>)
- *AWS CloudFormation Command Line Interface User Guide* (<https://docs.aws.amazon.com/cloudformation-cli/latest/userguide/what-is-cloudformation-cli.html>)

**Các chủ đề**

- *Creating Amazon ECS resources using the CloudFormation console* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/ecs-cloudformation-console.html>)
- *Creating Amazon ECS resources using AWS CLI commands for CloudFormation* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/ecs-cloudformation-cli.html>)
- *CloudFormation example templates for Amazon ECS* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/working-with-templates.html>)

**💡 Giải thích thêm**

Ba khái niệm chính theo trang:

| Khái niệm | Ý nghĩa |
|---|---|
| **Template** | File YAML/JSON — bản thiết kế tài nguyên |
| **Stack** | Được tạo từ template; bạn quản lý tài nguyên **qua stack** |
| **Change set** | Tạo **trước** khi cập nhật stack — xem trước thay đổi ảnh hưởng gì |

```
Template (YAML/JSON) ──tạo──► Stack ──► tài nguyên (cluster, task definition, service…)
                                 │
            muốn cập nhật ──► Change set (xem trước ảnh hưởng) ──► thực hiện cập nhật
```

Lợi ích theo trang:

| Lợi ích | Nội dung |
|---|---|
| Tiết kiệm thời gian | CloudFormation tự cấp phát và cấu hình tài nguyên từ template |
| Nhất quán, lặp lại được | Mô tả một lần, cấp phát lại trên nhiều account và Region |
| An toàn khi cập nhật | Change set cho thấy trước thay đổi — tránh vô tình xoá tài nguyên (vd database) |

Dùng template qua: **AWS Management Console** hoặc **AWS CLI**.

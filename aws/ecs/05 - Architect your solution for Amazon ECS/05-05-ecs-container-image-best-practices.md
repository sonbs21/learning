# Best practices for Amazon ECS container images

> Nguồn: **Amazon ECS Developer Guide — Best practices for Amazon ECS container images**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/container-considerations.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: **bản dịch** sang tiếng Việt, đi đúng thứ tự và đúng nội dung của trang gốc, không thêm, không bớt ý.
> - Khối **💡 Giải thích thêm**: chỉ giải thích lại những gì **chính trang này** đã nói, cho dễ hiểu hơn. Không đưa thông tin từ trang khác hay ví dụ tự đặt.
> - Tên tín hiệu, tag, tính năng giữ nguyên tiếng Anh.
> - Tài liệu AWS được phát hành theo giấy phép **Creative Commons Attribution-ShareAlike 4.0**; bản dịch này ghi rõ nguồn ở trên.

---

## Mục lục

0. [Container image là gì](#0-container-image-là-gì)
1. [Hướng dẫn thiết kế và build container image](#1-hướng-dẫn-thiết-kế-và-build-container-image)
   - [1.1 Image đầy đủ](#11-image-đầy-đủ)
   - [1.2 Một tiến trình ứng dụng mỗi container](#12-một-tiến-trình-ứng-dụng-mỗi-container)
   - [1.3 Xử lý `SIGTERM`](#13-xử-lý-sigterm)
   - [1.4 Ghi log ra `stdout` và `stderr`](#14-ghi-log-ra-stdout-và-stderr)
   - [1.5 Dùng tag để đánh phiên bản image](#15-dùng-tag-để-đánh-phiên-bản-image)
2. [Một task definition hay nhiều task definition (Fargate)](#2-một-task-definition-hay-nhiều-task-definition-fargate)

---

## 0. Container image là gì

**📘 Theo docs AWS**

Container image là một tập chỉ dẫn về cách build container. Container image chứa code ứng dụng của bạn và mọi dependency mà code ứng dụng cần để chạy. Dependency của ứng dụng gồm các gói source code mà code ứng dụng dựa vào, language runtime cho ngôn ngữ thông dịch, và các gói binary mà code liên kết động (dynamically linked) dựa vào.

Dùng các hướng dẫn sau khi thiết kế và build container image:

**💡 Giải thích thêm**

| Thành phần trong image | Theo đoạn trên |
|---|---|
| Code ứng dụng | Code của bạn |
| Gói source code | Các thư viện/gói mà code dựa vào |
| Language runtime | Cho ngôn ngữ thông dịch |
| Gói binary | Cho code liên kết động |

---

## 1. Hướng dẫn thiết kế và build container image

### 1.1 Image đầy đủ

**📘 Theo docs AWS**

- Làm cho container image **đầy đủ** bằng cách lưu mọi dependency của ứng dụng dưới dạng file tĩnh **bên trong** container image.

  Nếu bạn thay đổi gì đó trong container image, hãy build một container image **mới** với các thay đổi đó.

**💡 Giải thích thêm**

Image không phụ thuộc thứ cài thêm bên ngoài; muốn thay đổi thì **build image mới**, không sửa image đang có.

### 1.2 Một tiến trình ứng dụng mỗi container

**📘 Theo docs AWS**

- Chạy **một tiến trình ứng dụng duy nhất** trong một container.

  Vòng đời của container kéo dài bằng thời gian tiến trình ứng dụng chạy. Amazon ECS thay thế các tiến trình bị crash và quyết định nơi launch tiến trình thay thế. Một image đầy đủ giúp toàn bộ việc deploy bền vững (resilient) hơn.

**💡 Giải thích thêm**

```
Tiến trình ứng dụng chạy  →  container sống
Tiến trình crash          →  container kết thúc  →  ECS launch tiến trình thay thế ở nơi nó chọn
```

Kết hợp với 1.1: vì image **đầy đủ**, tiến trình thay thế khởi động được ở bất kỳ đâu ECS chọn.

### 1.3 Xử lý `SIGTERM`

**📘 Theo docs AWS**

- Làm cho ứng dụng **xử lý `SIGTERM`**.

  Khi Amazon ECS dừng một task, trước tiên nó gửi tín hiệu SIGTERM tới task để báo cho ứng dụng rằng cần kết thúc và tắt. Sau đó Amazon ECS gửi thông điệp `SIGKILL`. Khi ứng dụng bỏ qua `SIGTERM`, Amazon ECS service phải chờ để gửi tín hiệu `SIGKILL` nhằm kết thúc tiến trình.

  Bạn cần xác định ứng dụng mất bao lâu để hoàn tất công việc, và đảm bảo ứng dụng xử lý tín hiệu `SIGTERM`. Phần xử lý tín hiệu của ứng dụng cần **ngừng nhận việc mới** và **hoàn tất việc đang làm dở**, hoặc **lưu phần việc chưa xong** ra bộ lưu trữ bên ngoài task khi công việc mất quá nhiều thời gian để hoàn tất.

**💡 Giải thích thêm**

```
ECS dừng task
   → gửi SIGTERM  (ứng dụng: ngừng nhận việc mới, làm nốt / lưu việc dở ra ngoài task)
   → gửi SIGKILL  (kết thúc tiến trình)
```

Ba việc ứng dụng cần làm khi nhận `SIGTERM`, theo đoạn trên:

| # | Việc |
|---|---|
| 1 | Ngừng nhận việc mới |
| 2 | Hoàn tất việc đang làm |
| 3 | Nếu việc quá lâu: lưu phần chưa xong ra bộ lưu trữ **bên ngoài task** |

Bỏ qua `SIGTERM` thì ECS chỉ còn cách chờ rồi `SIGKILL` — ứng dụng không có cơ hội dọn dẹp.

### 1.4 Ghi log ra `stdout` và `stderr`

**📘 Theo docs AWS**

- Cấu hình ứng dụng container ghi log ra **`stdout`** và **`stderr`**.

  Tách việc xử lý log khỏi code ứng dụng giúp bạn linh hoạt điều chỉnh xử lý log ở **tầng hạ tầng**. Một ví dụ là thay đổi hệ thống logging. Thay vì sửa service, build và deploy container image mới, bạn có thể chỉ cần điều chỉnh cài đặt.

**💡 Giải thích thêm**

| Ứng dụng ghi log ra… | Muốn đổi hệ thống logging thì |
|---|---|
| `stdout` / `stderr` | Chỉ đổi **cài đặt** ở tầng hạ tầng |
| Nơi khác do code tự quyết | Phải sửa service, build và deploy image mới |

### 1.5 Dùng tag để đánh phiên bản image

**📘 Theo docs AWS**

- Dùng **tag** để đánh phiên bản container image.

  Container image được lưu trong container registry. Mỗi image trong registry được định danh bằng một tag. Có một tag tên là `latest`. Tag này đóng vai trò con trỏ tới phiên bản mới nhất của container image ứng dụng, tương tự `HEAD` trong git repository. Chúng tôi khuyến nghị chỉ dùng tag `latest` cho **mục đích kiểm thử**. Theo best practice, hãy gắn cho container image một **tag duy nhất cho mỗi lần build**. Chúng tôi khuyến nghị gắn tag image bằng **git SHA** của git commit đã dùng để build image.

  Bạn không cần build container image cho mọi commit. Tuy nhiên, chúng tôi khuyến nghị build container image mới mỗi khi bạn phát hành một commit code cụ thể lên môi trường production. Chúng tôi cũng khuyến nghị gắn cho image một tag tương ứng với git commit của code bên trong image. Nếu đã gắn tag image bằng git commit, bạn có thể nhanh chóng biết image đang chạy phiên bản code nào.

  Chúng tôi cũng khuyến nghị bật **immutable image tags** trong Amazon Elastic Container Registry. Với cài đặt này, bạn không thể thay đổi container image mà một tag trỏ tới. Thay vào đó, Amazon ECR buộc image mới phải được upload lên một tag mới. Thông tin thêm, xem *Image tag mutability* (<https://docs.aws.amazon.com/AmazonECR/latest/userguide/image-tag-mutability.html>) trong *Amazon ECR User Guide*.

**💡 Giải thích thêm**

| Tag | Dùng khi (theo trang) |
|---|---|
| `latest` | Chỉ để **kiểm thử** — giống `HEAD` của git, luôn trỏ tới bản mới nhất |
| Tag duy nhất mỗi build (git SHA) | Mọi image phát hành, nhất là **production** |

Ba khuyến nghị đi cùng nhau:

```
1. Mỗi lần phát hành commit lên production → build image mới
2. Tag image = git SHA của commit đó        → biết ngay image chạy code nào
3. Bật immutable image tags trong ECR       → tag không bị ghi đè sang image khác
```

Trang cũng nói rõ: **không cần** build image cho **mọi** commit — chỉ những commit được phát hành lên production.

---

## 2. Một task definition hay nhiều task definition (Fargate)

**📘 Theo docs AWS**

Khi thiết kế ứng dụng để chạy trên AWS Fargate, bạn phải quyết định giữa việc deploy nhiều container vào **cùng một task definition** và deploy container **riêng rẽ** trong nhiều task definition. Nếu cần các điều kiện sau, chúng tôi khuyến nghị deploy nhiều container vào cùng một task definition:

- Các container có chung vòng đời (tức là được launch và kết thúc cùng nhau).
- Các container phải chạy trên cùng host bên dưới (tức là container này tham chiếu container kia qua một port trên localhost).
- Các container chia sẻ tài nguyên.
- Các container chia sẻ data volume.

Nếu không cần các điều kiện này, chúng tôi khuyến nghị deploy container riêng rẽ trong nhiều task definition. Cách này cho phép bạn scale, cấp phát và thu hồi các container một cách riêng biệt.

**💡 Giải thích thêm**

```
Container A và B có cần MỘT trong các điều sau không?
  • launch và kết thúc cùng nhau
  • gọi nhau qua localhost (phải cùng host)
  • chia sẻ tài nguyên
  • chia sẻ data volume
       │
   có ─┴─ không
   │         │
   ▼         ▼
 cùng một    tách thành nhiều task definition
 task def    → scale, cấp phát, thu hồi riêng từng cái
```

Phần này trên trang được nói trong bối cảnh **AWS Fargate**.

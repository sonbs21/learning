# Linux containers on Fargate container image pull behavior for Amazon ECS

> Nguồn: **Amazon ECS Developer Guide — Linux containers on Fargate container image pull behavior for Amazon ECS**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/fargate-pull-behavior.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: **bản dịch** sang tiếng Việt, đi đúng thứ tự và đúng nội dung của trang gốc, không thêm, không bớt ý.
> - Khối **💡 Giải thích thêm**: chỉ giải thích lại những gì **chính trang này** đã nói, cho dễ hiểu hơn. Không đưa thông tin từ trang khác hay ví dụ tự đặt.
> - Tên thuật toán nén, công nghệ, dịch vụ giữ nguyên tiếng Anh.
> - Tài liệu AWS được phát hành theo giấy phép **Creative Commons Attribution-ShareAlike 4.0**; bản dịch này ghi rõ nguồn ở trên.

---

## Mục lục

0. [Phần mở đầu: vì sao thời gian pull image quan trọng trên Fargate](#0-phần-mở-đầu-vì-sao-thời-gian-pull-image-quan-trọng-trên-fargate)
1. [Đặt image gần nơi chạy](#1-đặt-image-gần-nơi-chạy)
2. [Giảm kích thước image](#2-giảm-kích-thước-image)
3. [Thuật toán nén khác](#3-thuật-toán-nén-khác)
4. [Lazy loading container image](#4-lazy-loading-container-image)
5. [Tổng hợp](#5-tổng-hợp)

---

## 0. Phần mở đầu: vì sao thời gian pull image quan trọng trên Fargate

**📘 Theo docs AWS**

Mỗi task Fargate chạy trên instance **riêng, dùng một lần, một khách hàng (single use, single tenant)**. Khi chạy Linux container trên Fargate, container image hoặc các layer của image **không được cache** trên instance. Vì vậy, với mỗi container image định nghĩa trong task, **toàn bộ** container image cần được pull từ container image registry **cho mỗi task Fargate**. Thời gian pull image **tương quan trực tiếp** với thời gian khởi động một task Fargate.

Hãy tính tới các điểm sau để tối ưu thời gian pull image.

**💡 Giải thích thêm**

```
Mỗi task Fargate = instance riêng, dùng một lần, không cache image
  → mỗi task mới phải pull TOÀN BỘ image
  → thời gian pull ≈ thời gian khởi động task
```

Bốn cách tối ưu ở các mục sau đều nhằm **giảm thời gian pull** (tải về + giải nén).

---

## 1. Đặt image gần nơi chạy

**📘 Theo docs AWS**

**Vị trí container image**

Để giảm thời gian tải container image, hãy đặt dữ liệu **càng gần compute càng tốt**. Pull container image **qua internet** hoặc **giữa các AWS Region** có thể ảnh hưởng thời gian tải. Chúng tôi khuyến nghị lưu container image ở **cùng Region** nơi task sẽ chạy. Nếu lưu container image trong **Amazon ECR**, hãy dùng **VPC interface endpoint** để giảm thêm thời gian pull image. Thông tin thêm, xem *Amazon ECR interface VPC endpoints (AWS PrivateLink)* (<https://docs.aws.amazon.com/AmazonECR/latest/userguide/vpc-endpoints.html>) trong *Amazon ECR User Guide*.

**💡 Giải thích thêm**

| Nguồn pull | Theo trang |
|---|---|
| Qua internet / khác Region | Có thể chậm hơn |
| **Cùng Region** với task | Khuyến nghị |
| Amazon ECR + **VPC interface endpoint** | Giảm thêm thời gian pull |

---

## 2. Giảm kích thước image

**📘 Theo docs AWS**

**Giảm kích thước container image**

Kích thước container image **ảnh hưởng trực tiếp** tới thời gian tải. Giảm kích thước container image hoặc **số layer** của container image có thể giảm thời gian tải image. Các **base image nhẹ** (như **Amazon Linux 2023 minimal container image**) có thể nhỏ hơn đáng kể so với base image dựa trên hệ điều hành truyền thống. Thông tin thêm về minimal image, xem *AL2023 Minimal container image* (<https://docs.aws.amazon.com/linux/al2023/ug/minimal-container.html>) trong *Amazon Linux 2023 User Guide*.

**💡 Giải thích thêm**

| Cách giảm | Theo trang |
|---|---|
| Giảm kích thước image | Tải nhanh hơn |
| Giảm số layer | Tải nhanh hơn |
| Dùng base image nhẹ | Ví dụ AL2023 minimal — nhỏ hơn đáng kể so với base image OS truyền thống |

---

## 3. Thuật toán nén khác

**📘 Theo docs AWS**

**Thuật toán nén thay thế**

Các layer của container image thường được **nén** khi push lên container image registry. Nén layer giảm lượng dữ liệu phải truyền qua mạng và lưu trong registry. Sau khi một layer được container runtime tải về instance, layer đó được **giải nén**. **Thuật toán nén** được dùng và **số vCPU** có sẵn cho runtime ảnh hưởng thời gian giải nén container image. Trên Fargate, bạn có thể **tăng kích thước task** hoặc tận dụng thuật toán nén **zstd** hiệu năng cao hơn để giảm thời gian giải nén. Thông tin thêm, xem *zstd* (<https://github.com/facebook/zstd>) trên GitHub. Thông tin về cách triển khai image cho Fargate, xem *Reducing AWS Fargate Startup Times with zstd Compressed Container Images* (<https://aws.amazon.com/blogs/containers/reducing-aws-fargate-startup-times-with-zstd-compressed-container-images/>).

**💡 Giải thích thêm**

```
push: layer được nén   → ít dữ liệu truyền và lưu
pull: tải layer về      → giải nén (tốn thời gian)
         thời gian giải nén phụ thuộc: thuật toán nén + số vCPU
```

| Cách giảm thời gian giải nén (theo trang) | |
|---|---|
| Tăng kích thước task | Nhiều vCPU hơn cho runtime |
| Dùng nén **zstd** | Thuật toán hiệu năng cao hơn |

---

## 4. Lazy loading container image

**📘 Theo docs AWS**

**Lazy loading container image**

Với container image **lớn (> 250mb)**, có thể tối ưu hơn khi **lazy load** container image thay vì tải toàn bộ container image. Trên Fargate, bạn có thể dùng **Seekable OCI (SOCI)** để lazy load một container image từ container image registry. Thông tin thêm, xem *soci-snapshotter* (<https://github.com/awslabs/soci-snapshotter>) trên GitHub và *Lazy loading container images using Seekable OCI (SOCI)* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/fargate-tasks-services.html#fargate-tasks-soci-images>).

**💡 Giải thích thêm**

| | Tải toàn bộ | Lazy load (SOCI) |
|---|---|---|
| Cách làm | Pull hết image rồi mới chạy | Tải dần theo nhu cầu |
| Trang gợi ý khi | — | Image **> 250 MB** |

---

## 5. Tổng hợp

**💡 Giải thích thêm**

Tổng hợp từ các mục trên của chính trang:

| Cách tối ưu | Giảm phần nào | Cách làm theo trang |
|---|---|---|
| Đặt image gần | Thời gian tải | Cùng Region; ECR + VPC interface endpoint |
| Giảm kích thước | Thời gian tải | Image nhỏ, ít layer; base image nhẹ (AL2023 minimal) |
| Nén khác | Thời gian giải nén | Tăng kích thước task; dùng zstd |
| Lazy loading | Không phải tải hết | SOCI cho image > 250 MB |

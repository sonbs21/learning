# Verifying TLS is enabled for Amazon ECS Service Connect

> Nguồn: **Amazon ECS Developer Guide — Verifying TLS is enabled for Amazon ECS Service Connect**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/verify-tls-enabled.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: **bản dịch** sang tiếng Việt, đi đúng thứ tự và đúng nội dung của trang gốc, không thêm, không bớt ý. Lệnh và output ví dụ giữ nguyên như trên docs.
> - Khối **💡 Giải thích thêm**: chỉ giải thích lại những gì **chính trang này** đã nói, cho dễ hiểu hơn. Không đưa thông tin từ trang khác hay ví dụ tự đặt.
> - Tên lệnh, trường certificate giữ nguyên tiếng Anh.
> - Tài liệu AWS được phát hành theo giấy phép **Creative Commons Attribution-ShareAlike 4.0**; bản dịch này ghi rõ nguồn ở trên.

---

## Mục lục

0. [Phần mở đầu](#0-phần-mở-đầu)
1. [Các bước kiểm tra](#1-các-bước-kiểm-tra)
2. [Đọc certificate trong output ví dụ](#2-đọc-certificate-trong-output-ví-dụ)

---

## 0. Phần mở đầu

**📘 Theo docs AWS**

Service Connect **khởi tạo TLS** tại **Service Connect agent** và **kết thúc** nó tại **agent đích**. Vì vậy, code ứng dụng **không bao giờ thấy** các tương tác TLS. Dùng các bước sau để kiểm tra TLS đã được bật.

**💡 Giải thích thêm**

```
App (client) ─ plain ─► Service Connect agent ══ TLS ══► Service Connect agent ─ plain ─► App (server)
                         (khởi tạo TLS)                    (kết thúc TLS)
```

Vì ứng dụng không thấy TLS, muốn biết TLS đã bật phải **kết nối thẳng tới IP và port của task** để xem certificate (các bước ở mục 1).

---

## 1. Các bước kiểm tra

**📘 Theo docs AWS**

1. Đưa CLI **`openssl`** vào image ứng dụng.

2. Bật **ECS Exec** (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/ecs-exec.html>) cho service để kết nối vào task qua SSM. Hoặc, bạn có thể launch một **Amazon EC2 instance** trong **cùng Amazon VPC** với service.

3. Lấy **IP và port** của một task thuộc service bạn muốn kiểm tra. Bạn có thể lấy địa chỉ IP của task trong console **AWS Cloud Map**. Thông tin nằm ở trang chi tiết service của namespace.

4. Đăng nhập vào bất kỳ task nào bằng `execute-command` như ví dụ sau. Hoặc, đăng nhập vào Amazon EC2 instance đã tạo ở **Bước 2**.

   ```
   $ aws ecs execute-command --cluster cluster-name \
       --task task-id  \
       --container container-name \
       --interactive \
       --command "/bin/sh"
   ```

   > **Note:** Gọi **tên DNS** trực tiếp **không** hiện ra certificate.

5. Trong shell đã kết nối, dùng CLI `openssl` để kiểm tra và xem certificate gắn với task.

   Ví dụ:

   ```
   openssl s_client -connect 10.0.147.43:6379 < /dev/null 2> /dev/null \
   | openssl x509 -noout -text
   ```

   Response ví dụ:

   ```
   Certificate:
       Data:
           Version: 3 (0x2)
           Serial Number:
               <serial-number>
           Signature Algorithm: ecdsa-with-SHA256
           Issuer: <issuer>
           Validity
               Not Before: Jan 23 21:38:12 2024 GMT
               Not After : Jan 30 22:38:12 2024 GMT
           Subject: <subject>
           Subject Public Key Info:
               Public Key Algorithm: id-ecPublicKey
                   Public-Key: (256 bit)
                   pub:
                       <pub>
                   ASN1 OID: prime256v1
                   NIST CURVE: P-256
           X509v3 extensions:
               X509v3 Subject Alternative Name:
                   DNS:redis.yelb-cftc
               X509v3 Basic Constraints:
                   CA:FALSE
               X509v3 Authority Key Identifier:
                   keyid:<key-id>

       X509v3 Subject Key Identifier:
                   1D:<id>
               X509v3 Key Usage: critical
                   Digital Signature, Key Encipherment
               X509v3 Extended Key Usage:
                   TLS Web Server Authentication, TLS Web Client Authentication
       Signature Algorithm: ecdsa-with-SHA256
           <hash>
   ```

**💡 Giải thích thêm**

```
1. Image có openssl
2. Có đường vào: ECS Exec (SSM)  |  EC2 instance cùng VPC
3. Lấy IP:port của task (console Cloud Map → namespace → service details)
4. Vào shell: aws ecs execute-command ... --command "/bin/sh"   |  SSH/đăng nhập EC2
5. openssl s_client -connect <IP>:<port> | openssl x509 -noout -text   → xem certificate
```

| Cách vào | Yêu cầu theo trang |
|---|---|
| ECS Exec | Bật ECS Exec cho service; vào task qua SSM |
| EC2 instance | Cùng Amazon VPC với service |

Lưu ý từ Note: phải kết nối bằng **IP:port**, gọi **tên DNS** sẽ **không** thấy certificate.

---

## 2. Đọc certificate trong output ví dụ

**💡 Giải thích thêm**

Các trường trong response ví dụ của trang:

| Trường | Giá trị trong ví dụ | Ý nghĩa |
|---|---|---|
| `Signature Algorithm` | `ecdsa-with-SHA256` | Ký bằng ECDSA với SHA-256 |
| `Validity` | `Not Before: Jan 23 21:38:12 2024 GMT` → `Not After: Jan 30 22:38:12 2024 GMT` | Khoảng thời gian certificate có hiệu lực |
| `Public Key Algorithm` | `id-ecPublicKey`, 256 bit, `prime256v1` / `P-256` | Khoá công khai đường cong elliptic 256-bit |
| `Subject Alternative Name` | `DNS:redis.yelb-cftc` | Tên mà certificate cấp cho |
| `Basic Constraints` | `CA:FALSE` | Certificate cuối (không phải CA) |
| `Key Usage` | `Digital Signature, Key Encipherment` | Mục đích dùng khoá |
| `Extended Key Usage` | `TLS Web Server Authentication, TLS Web Client Authentication` | Dùng cho cả phía server lẫn client TLS |

Thấy được một certificate như trên khi kết nối tới IP:port của task là dấu hiệu TLS đang hoạt động (theo mục đích của quy trình trên trang).

# Encrypt Amazon ECS Service Connect traffic

> Nguồn: **Amazon ECS Developer Guide — Encrypt Amazon ECS Service Connect traffic**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-connect-tls.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: **bản dịch** sang tiếng Việt, đi đúng thứ tự và đúng nội dung của trang gốc, không thêm, không bớt ý. Lệnh và policy ví dụ giữ nguyên như trên docs.
> - Khối **💡 Giải thích thêm**: chỉ giải thích lại những gì **chính trang này** đã nói, cho dễ hiểu hơn. Không đưa thông tin từ trang khác hay ví dụ tự đặt.
> - Tên dịch vụ, cipher, security policy, tham số giữ nguyên tiếng Anh.
> - Tài liệu AWS được phát hành theo giấy phép **Creative Commons Attribution-ShareAlike 4.0**; bản dịch này ghi rõ nguồn ở trên.

---

## Mục lục

0. [Phần mở đầu](#0-phần-mở-đầu)
1. [Service Connect và health check của Application Load Balancer](#1-service-connect-và-health-check-của-application-load-balancer)
2. [Certificate AWS Private Certificate Authority và Service Connect](#2-certificate-aws-private-certificate-authority-và-service-connect)
3. [Service Connect và Secrets Manager](#3-service-connect-và-secrets-manager)
4. [Service Connect và AWS Key Management Service](#4-service-connect-và-aws-key-management-service)

---

## 0. Phần mở đầu

**📘 Theo docs AWS**

Amazon ECS Service Connect hỗ trợ **tự động mã hoá traffic** bằng certificate **Transport Layer Security (TLS)** cho Amazon ECS service. Khi bạn trỏ Amazon ECS service tới một **AWS Private Certificate Authority (AWS Private CA)** (<https://docs.aws.amazon.com/privateca/latest/userguide/PcaWelcome.html>), Amazon ECS tự động cấp certificate TLS để mã hoá traffic giữa các service Amazon ECS Service Connect. Amazon ECS **tạo, xoay vòng và phân phối** certificate TLS dùng để mã hoá traffic.

Mã hoá traffic tự động với Service Connect dùng khả năng mã hoá hàng đầu để bảo vệ giao tiếp giữa các service, giúp bạn đáp ứng yêu cầu bảo mật. Nó hỗ trợ certificate TLS của AWS Private Certificate Authority với mã hoá **`256-bit ECDSA`** và **`2048-bit RSA`**. Bạn cũng có **toàn quyền kiểm soát** private certificate và khoá ký để đáp ứng yêu cầu tuân thủ. Mặc định, **TLS 1.3** được hỗ trợ, còn **TLS 1.0 – 1.2 không** được hỗ trợ. Service Connect hỗ trợ TLS 1.3 với các cipher sau:

- `TLS_AES_128_GCM_SHA256`
- `TLS_AES_256_GCM_SHA384`
- `TLS_CHACHA20_POLY1305_SHA256`

> **Note:** Để dùng TLS 1.3, bạn phải bật nó trên **listener của target**.

**Chỉ** traffic vào và ra **đi qua Amazon ECS agent** mới được mã hoá.

**💡 Giải thích thêm**

| Điểm | Theo đoạn trên |
|---|---|
| Nguồn certificate | AWS Private CA bạn trỏ tới |
| ECS lo gì | Tạo, xoay vòng, phân phối certificate |
| Thuật toán khoá | `256-bit ECDSA`, `2048-bit RSA` |
| Phiên bản TLS | Chỉ **TLS 1.3** (TLS 1.0–1.2 không hỗ trợ) |
| Cipher | `TLS_AES_128_GCM_SHA256`, `TLS_AES_256_GCM_SHA384`, `TLS_CHACHA20_POLY1305_SHA256` |
| Phạm vi mã hoá | Chỉ traffic đi qua Amazon ECS agent |

---

## 1. Service Connect và health check của Application Load Balancer

**📘 Theo docs AWS**

Bạn có thể dùng Service Connect với health check của Application Load Balancer và mã hoá TLS 1.3.

### 1.1 Cấu hình Application Load Balancer

**📘 Theo docs AWS**

Cấu hình Application Load Balancer với các thiết lập sau:

- Cấu hình một **TLS listener** với **security policy TLS 1.3** (như `ELBSecurityPolicy-TLS13-1-2-2021-06`). Thông tin thêm, xem *Security policies for your Application Load Balancer* (<https://docs.aws.amazon.com/elasticloadbalancing/latest/application/describe-ssl-policies.html>).
- Tạo một **target group** với các thiết lập sau:
  - Đặt protocol là **HTTPS**
  - Gắn target group vào TLS listener
  - Cấu hình **health check port** khớp với **container port** của Service Connect service

### 1.2 Cấu hình Service Connect

**📘 Theo docs AWS**

Cấu hình service với các thiết lập sau:

- Cấu hình service dùng network mode **`awsvpc`**, vì network mode `bridge` **không được hỗ trợ**.
- Bật Service Connect cho service.
- Thiết lập cấu hình load balancer với các thiết lập sau:
  - Chỉ định target group bạn đã cấu hình cho Application Load Balancer
  - Đặt container port khớp với container port của Service Connect TLS service
- **Tránh** đặt `ingressPortOverride` cho service. Thông tin thêm, xem *ServiceConnectService* (<https://docs.aws.amazon.com/AmazonECS/latest/APIReference/API_ServiceConnectService.html>) trong *Amazon Elastic Container Service API Reference*.

### 1.3 Những điều cần cân nhắc

**📘 Theo docs AWS**

Cân nhắc các điểm sau khi dùng Application Load Balancer, TLS và Service Connect:

- Dùng network mode **`awsvpc`** thay vì `bridge` cho **health check HTTPS** khi dùng Service Connect với mã hoá TLS. Health check **HTTP** vẫn hoạt động với mode `bridge`.
- Cấu hình health check port của target group khớp với **container port của Service Connect service**, **không phải** port HTTPS mặc định (**443**).

**💡 Giải thích thêm**

```
Client ─► ALB: TLS listener (security policy TLS 1.3, vd ELBSecurityPolicy-TLS13-1-2-2021-06)
              └─ target group: HTTPS, health check port = container port của Service Connect service
                     └─► ECS service: awsvpc, Service Connect bật, container port khớp, KHÔNG đặt ingressPortOverride
```

| Thiết lập | Đúng | Sai |
|---|---|---|
| Network mode | `awsvpc` | `bridge` (không hỗ trợ cho HTTPS health check; HTTP health check vẫn được) |
| Health check port | Container port của Service Connect service | 443 mặc định |
| `ingressPortOverride` | Không đặt | Đặt |

---

## 2. Certificate AWS Private Certificate Authority và Service Connect

**📘 Theo docs AWS**

Bạn cần có **infrastructure IAM role**. Thông tin thêm về role này, xem *Amazon ECS infrastructure IAM role* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/infrastructure_IAM_role.html>).

### 2.1 Các chế độ AWS Private Certificate Authority cho Service Connect

**📘 Theo docs AWS**

AWS Private Certificate Authority có thể chạy ở hai chế độ: **general purpose** và **short-lived**.

- **General purpose** ‐ Certificate có thể cấu hình với **bất kỳ** ngày hết hạn nào.
- **Short-lived** ‐ Certificate có hiệu lực tối đa **bảy ngày**.

Dù Amazon ECS hỗ trợ cả hai chế độ, chúng tôi **khuyến nghị dùng certificate short-lived**. Mặc định, certificate **xoay vòng mỗi năm ngày**, và chạy ở chế độ short-lived **tiết kiệm chi phí đáng kể** so với general purpose.

Service Connect **không hỗ trợ thu hồi certificate**, mà thay vào đó tận dụng certificate short-lived với việc xoay vòng thường xuyên. Bạn có quyền sửa tần suất xoay vòng, tắt, hoặc xoá secret bằng **managed rotation** (<https://docs.aws.amazon.com/secretsmanager/latest/userguide/rotate-secrets_managed.html>) trong **Secrets Manager** (<https://docs.aws.amazon.com/secretsmanager/latest/userguide/intro.html>), nhưng làm vậy có thể kéo theo các hệ quả sau.

- **Tần suất xoay vòng ngắn hơn** ‐ Tần suất xoay vòng ngắn hơn làm **tăng chi phí** do AWS Private CA, AWS KMS và Secrets Manager, và Auto Scaling phải chịu khối lượng công việc xoay vòng tăng lên.
- **Tần suất xoay vòng dài hơn** ‐ Giao tiếp của ứng dụng **thất bại** nếu tần suất xoay vòng **vượt bảy ngày**.
- **Xoá secret** ‐ Xoá secret dẫn tới **xoay vòng thất bại** và ảnh hưởng giao tiếp của ứng dụng khách hàng.

Nếu việc xoay vòng secret thất bại, một sự kiện **`RotationFailed`** được đẩy lên **AWS CloudTrail** (<https://docs.aws.amazon.com/awscloudtrail/latest/userguide/cloudtrail-user-guide.html>). Bạn cũng có thể thiết lập **CloudWatch Alarm** (<https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/AlarmThatSendsEmail.html>) cho `RotationFailed`.

> **Important:** **Không** thêm **replica Region** cho secret. Làm vậy ngăn Amazon ECS xoá secret, vì Amazon ECS không có quyền gỡ Region khỏi replication. Nếu bạn đã thêm replication, hãy chạy lệnh sau.
>
> ```
> aws secretsmanager remove-regions-from-replication \
>  --secret-id SecretId \
>  --remove-replica-regions region-name
> ```

**💡 Giải thích thêm**

| Chế độ | Thời hạn certificate | Khuyến nghị theo trang |
|---|---|---|
| General purpose | Tuỳ ý | — |
| Short-lived | ≤ 7 ngày | **Khuyến nghị** — rẻ hơn đáng kể |

Mặc định: xoay vòng **mỗi 5 ngày**, nằm trong giới hạn 7 ngày của short-lived. Service Connect **không thu hồi** certificate — dựa vào thời hạn ngắn + xoay vòng thường xuyên.

Hệ quả khi tự chỉnh xoay vòng trong Secrets Manager:

| Thay đổi | Hệ quả |
|---|---|
| Ngắn hơn | Tốn chi phí hơn (Private CA, KMS, Secrets Manager, Auto Scaling) |
| Dài hơn **7 ngày** | Ứng dụng **giao tiếp thất bại** |
| Xoá secret | Xoay vòng thất bại, ảnh hưởng ứng dụng |
| Thêm replica Region | ECS không xoá được secret → gỡ bằng `remove-regions-from-replication` |

Giám sát: sự kiện `RotationFailed` trong CloudTrail, đặt CloudWatch Alarm.

### 2.2 Subordinate Certificate Authorities

**📘 Theo docs AWS**

Bạn có thể dùng bất kỳ AWS Private CA nào, **root** hoặc **subordinate**, cho Service Connect TLS để cấp end-entity certificate cho các service. Issuer được cung cấp được coi là **bên ký** và **gốc tin cậy (root of trust)** ở mọi nơi. Bạn có thể cấp end-entity certificate cho các phần khác nhau của ứng dụng từ các subordinate CA khác nhau. Khi dùng AWS CLI, cung cấp **ARN** của CA để thiết lập chuỗi tin cậy.

### 2.3 Certificate Authority on-premises

**📘 Theo docs AWS**

Để dùng CA on-premises, bạn tạo và cấu hình một **subordinate CA** trong AWS Private Certificate Authority. Điều này đảm bảo mọi certificate TLS cấp cho workload Amazon ECS **chung chuỗi tin cậy** với workload bạn chạy on-premises và có thể kết nối an toàn.

> **Important:** Thêm tag **bắt buộc** **`AmazonECSManaged : true`** vào AWS Private CA của bạn.

### 2.4 Infrastructure as code

**📘 Theo docs AWS**

Khi dùng Service Connect TLS với công cụ Infrastructure as Code (IaC), quan trọng là cấu hình **phụ thuộc** đúng để tránh sự cố, như service **kẹt ở draining**. AWS KMS key (nếu có), IAM role, và AWS Private CA nên được **xoá sau** Amazon ECS service.

Nếu namespace dùng cho Service Connect là namespace chia sẻ, bạn có thể chọn dùng tài nguyên AWS Private CA **chia sẻ**. Thông tin thêm, xem *Attach a policy for cross-account access* (<https://docs.aws.amazon.com/privateca/latest/userguide/pca-ram.html>) trong *AWS Private Certificate Authority User Guide*.

**💡 Giải thích thêm**

| Loại CA | Cách dùng theo trang |
|---|---|
| Root hoặc subordinate AWS Private CA | Issuer = bên ký + root of trust; CLI dùng ARN CA |
| Nhiều subordinate CA | Cấp certificate cho các phần khác nhau của ứng dụng |
| CA on-premises | Tạo **subordinate CA** trong AWS Private CA → chung chuỗi tin cậy với on-premises |
| Bắt buộc | Tag `AmazonECSManaged : true` trên AWS Private CA |

Thứ tự xoá khi dùng IaC (để tránh service kẹt draining):

```
Xoá ECS service  ──►  rồi mới xoá: KMS key (nếu có), IAM role, AWS Private CA
```

---

## 3. Service Connect và Secrets Manager

**📘 Theo docs AWS**

**Khi dùng Amazon ECS Service Connect với mã hoá TLS, service tương tác với Secrets Manager theo các cách sau:**

Service Connect dùng **infrastructure role** được cung cấp để **tạo secret** trong Secrets Manager. Các secret này được dùng để lưu **private key** đi kèm certificate TLS dùng mã hoá traffic giữa các Service Connect service.

> **Warning:** Việc Service Connect tự động tạo và quản lý các secret này giúp đơn giản hoá việc triển khai mã hoá TLS cho service. Tuy nhiên, cần lưu ý các hệ quả bảo mật tiềm ẩn. **Các IAM role khác có quyền đọc Secrets Manager** có thể truy cập được các secret tự tạo này. Điều này có thể làm lộ tài liệu mật mã nhạy cảm cho bên không được phép, nếu kiểm soát truy cập không được cấu hình đúng.

Để giảm rủi ro này, làm theo các best practice sau:

- Quản lý cẩn thận và giới hạn quyền truy cập Secrets Manager, đặc biệt với secret do Service Connect tạo.
- Thường xuyên kiểm tra IAM role và quyền của chúng để đảm bảo nguyên tắc **quyền tối thiểu**.

Khi cấp quyền đọc Secrets Manager, hãy cân nhắc **loại trừ** các private key TLS do Service Connect tạo. Bạn có thể làm vậy bằng một điều kiện trong IAM policy để loại trừ các secret có ARN khớp mẫu:

```
"arn:aws:secretsmanager:::secret:ecs-sc!"
```

Một IAM policy ví dụ **từ chối** action `GetSecretValue` cho mọi secret có tiền tố `ecs-sc!`:

JSON
```json
{
    "Version":"2012-10-17",
    "Statement": [
        {
            "Effect": "Deny",
            "Action": "secretsmanager:GetSecretValue",
            "Resource": "arn:aws:secretsmanager:*:*:secret:ecs-sc!*"
        }
    ]
}
```

> **Note:** Đây là ví dụ chung và có thể cần điều chỉnh theo trường hợp sử dụng và cấu hình AWS account cụ thể. Luôn kiểm thử kỹ IAM policy để đảm bảo nó cấp đúng quyền mong muốn mà vẫn giữ bảo mật.

Khi hiểu cách Service Connect tương tác với Secrets Manager, bạn có thể quản lý tốt hơn bảo mật của Amazon ECS service trong khi vẫn tận dụng lợi ích của mã hoá TLS tự động.

**💡 Giải thích thêm**

```
Infrastructure role ─► Service Connect tạo secret trong Secrets Manager
                          (tên bắt đầu bằng ecs-sc!, chứa private key TLS)
Rủi ro: role khác có quyền đọc Secrets Manager → đọc được private key
Giảm rủi ro: giới hạn quyền + Deny GetSecretValue trên secret:ecs-sc!*
```

| Biện pháp theo trang | |
|---|---|
| Giới hạn quyền truy cập Secrets Manager | Đặc biệt với secret do Service Connect tạo |
| Kiểm tra định kỳ IAM role | Giữ quyền tối thiểu |
| Policy `Deny` `secretsmanager:GetSecretValue` trên `arn:aws:secretsmanager:*:*:secret:ecs-sc!*` | Loại trừ private key TLS khỏi quyền đọc chung |

Mẫu ARN dạng chuỗi trên trang gốc (`arn:aws:secretsmanager:::secret:ecs-sc!`) để trống phần Region và account, trong khi policy ví dụ dùng `*:*` và có `*` ở cuối. Khi viết policy, theo dạng trong ví dụ JSON.

---

## 4. Service Connect và AWS Key Management Service

**📘 Theo docs AWS**

Bạn có thể dùng **AWS Key Management Service** (<https://docs.aws.amazon.com/kms/latest/developerguide/overview.html>) để mã hoá và giải mã tài nguyên Service Connect. AWS KMS là dịch vụ do AWS quản lý, nơi bạn có thể tạo và quản lý khoá mật mã bảo vệ dữ liệu.

Khi dùng AWS KMS với Service Connect, bạn có thể chọn dùng **AWS owned key** do AWS quản lý cho bạn, hoặc chọn một **AWS KMS key có sẵn**. Bạn cũng có thể **tạo AWS KMS key mới** (<https://docs.aws.amazon.com/kms/latest/developerguide/create-keys.html#create-symmetric-cmk>) để dùng.

### 4.1 Cung cấp khoá mã hoá của riêng bạn

**📘 Theo docs AWS**

Bạn có thể cung cấp **key material** của riêng mình, hoặc dùng **external key store** thông qua AWS Key Management Service. Import khoá của bạn vào AWS KMS, rồi chỉ định **ARN** của khoá đó trong Amazon ECS Service Connect.

Sau đây là một AWS KMS policy ví dụ. Thay các giá trị `user input` bằng giá trị của bạn.

JSON
```json
{
  "Version":"2012-10-17",
  "Statement": [
    {
      "Sid": "id",
      "Effect": "Allow",
      "Principal": {
        "AWS": "arn:aws:iam::111122223333:role/role-name"
      },
      "Action": [
        "kms:Encrypt",
        "kms:Decrypt",
        "kms:GenerateDataKey",
        "kms:GenerateDataKeyPair"
      ],
      "Resource": "*"
    }
  ]
}
```

Thông tin thêm về key policy, xem *Creating a key policy* (<https://docs.aws.amazon.com/kms/latest/developerguide/key-policy-overview.html>) trong *AWS Key Management Service Developer Guide*.

> **Note:** Service Connect **chỉ hỗ trợ** AWS KMS key **mã hoá đối xứng (symmetric)**. Bạn không thể dùng loại AWS KMS key nào khác để mã hoá tài nguyên Service Connect. Để xác định một AWS KMS key có phải khoá mã hoá đối xứng hay không, xem *Identify asymmetric KMS keys* (<https://docs.aws.amazon.com/kms/latest/developerguide/identify-key-types.html#identify-asymm-keys>).

Thông tin thêm về khoá mã hoá đối xứng AWS Key Management Service, xem *Symmetric encryption AWS KMS keys* (<https://docs.aws.amazon.com/kms/latest/developerguide/concepts.html#symmetric-cmks>) trong *AWS Key Management Service Developer Guide*.

**💡 Giải thích thêm**

| Lựa chọn khoá theo trang | |
|---|---|
| AWS owned key | AWS quản lý cho bạn |
| AWS KMS key có sẵn | Dùng key bạn đã có |
| Tạo AWS KMS key mới | Tạo key đối xứng mới |
| Key của riêng bạn | Import key material hoặc dùng external key store → chỉ định ARN trong Service Connect |

Ràng buộc: chỉ **symmetric** KMS key.

Các action KMS trong policy ví dụ, cấp cho role chỉ định:

| Action |
|---|
| `kms:Encrypt` |
| `kms:Decrypt` |
| `kms:GenerateDataKey` |
| `kms:GenerateDataKeyPair` |

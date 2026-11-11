# Using shared AWS Cloud Map namespaces with Amazon ECS Service Connect

> Nguồn: **Amazon ECS Developer Guide — Using shared AWS Cloud Map namespaces with Amazon ECS Service Connect**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-connect-shared-namespaces-setup.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: **bản dịch** sang tiếng Việt, đi đúng thứ tự và đúng nội dung của trang gốc, không thêm, không bớt ý. Lệnh ví dụ giữ nguyên như trên docs.
> - Khối **💡 Giải thích thêm**: chỉ giải thích lại những gì **chính trang này** đã nói, cho dễ hiểu hơn. Không đưa thông tin từ trang khác hay ví dụ tự đặt.
> - Tên nút trên console, tên tham số giữ nguyên tiếng Anh (in đậm).
> - Tài liệu AWS được phát hành theo giấy phép **Creative Commons Attribution-ShareAlike 4.0**; bản dịch này ghi rõ nguồn ở trên.

---

## Mục lục

0. [Phần mở đầu](#0-phần-mở-đầu)
1. [Bước 1: Tạo AWS Cloud Map namespace](#1-bước-1-tạo-aws-cloud-map-namespace)
2. [Bước 2: Chia sẻ namespace bằng AWS RAM](#2-bước-2-chia-sẻ-namespace-bằng-aws-ram)
3. [Bước 3: Chấp nhận resource share](#3-bước-3-chấp-nhận-resource-share)
4. [Bước 4: Cấu hình Amazon ECS service với namespace chia sẻ](#4-bước-4-cấu-hình-amazon-ecs-service-với-namespace-chia-sẻ)
5. [Tổng hợp](#5-tổng-hợp)

---

## 0. Phần mở đầu

**📘 Theo docs AWS**

Thiết lập AWS Cloud Map namespace chia sẻ cho Service Connect gồm các bước: **chủ namespace** tạo namespace, chủ namespace **chia sẻ** nó qua AWS Resource Access Manager (AWS RAM), **bên dùng** **chấp nhận** resource share, và bên dùng **cấu hình Service Connect** để dùng namespace chia sẻ.

**💡 Giải thích thêm**

```
Chủ namespace                          Bên dùng namespace
─────────────                          ──────────────────
Bước 1: tạo namespace (Cloud Map)
Bước 2: chia sẻ qua AWS RAM ──────────► Bước 3: chấp nhận resource share, ghi lại ARN
                                       Bước 4: cấu hình ECS service với ARN namespace
```

---

## 1. Bước 1: Tạo AWS Cloud Map namespace

**📘 Theo docs AWS**

Chủ namespace tạo một AWS Cloud Map namespace sẽ được chia sẻ với các account khác.

**Tạo namespace để chia sẻ bằng AWS Management Console**

1. Mở console AWS Cloud Map tại <https://console.aws.amazon.com/cloudmap/>.
2. Chọn **Create namespace**.
3. Nhập **Namespace name**. Tên này sẽ được service ở **mọi account tham gia** dùng.
4. Ở **Namespace type**, chọn loại phù hợp với trường hợp sử dụng:
   - **API calls** ‐ HTTP namespace cho service discovery **không có** chức năng DNS.
   - **API calls and DNS queries in VPCs** ‐ Private DNS namespace cho service discovery với truy vấn DNS **private trong VPC**.
   - **API calls and public DNS queries** ‐ Public DNS namespace cho service discovery với truy vấn DNS **public**.
5. Chọn **Create namespace**.

**💡 Giải thích thêm**

| Lựa chọn trên console | Loại namespace | Discovery qua |
|---|---|---|
| **API calls** | HTTP | API, không DNS |
| **API calls and DNS queries in VPCs** | Private DNS | API + DNS private trong VPC |
| **API calls and public DNS queries** | Public DNS | API + DNS public |

---

## 2. Bước 2: Chia sẻ namespace bằng AWS RAM

**📘 Theo docs AWS**

Chủ namespace dùng AWS RAM để chia sẻ namespace với các AWS account khác.

**Chia sẻ namespace bằng console AWS RAM**

1. Mở console AWS RAM tại <https://console.aws.amazon.com/ram/>.
2. Chọn **Create resource share**.
3. Ở **Name**, nhập tên mô tả cho resource share.
4. Trong mục **Resources**:
   1. Ở **Resource type**, chọn **Cloud Map Namespaces**.
   2. Chọn namespace bạn đã tạo ở bước trước.
5. Trong mục **Managed permissions**, chỉ định **AWSRAMPermissionCloudMapECSFullPermission**.

   > **Important:** Bạn **phải** dùng managed permission `AWSRAMPermissionCloudMapECSFullPermission` để chia sẻ namespace thì Service Connect mới hoạt động đúng với namespace đó.

6. Trong mục **Principals**, chỉ định các AWS account bạn muốn chia sẻ namespace. Bạn có thể nhập **account ID** hoặc **organizational unit ID**.
7. Chọn **Create resource share**.

**💡 Giải thích thêm**

| Mục trên console | Giá trị |
|---|---|
| Resource type | **Cloud Map Namespaces** |
| Managed permissions | **AWSRAMPermissionCloudMapECSFullPermission** (bắt buộc) |
| Principals | Account ID hoặc organizational unit ID |

---

## 3. Bước 3: Chấp nhận resource share

**📘 Theo docs AWS**

Account bên dùng namespace phải **chấp nhận** lời mời resource share để dùng namespace chia sẻ.

**Chấp nhận lời mời resource share bằng console AWS RAM**

1. Trong **account bên dùng**, mở console AWS RAM tại <https://console.aws.amazon.com/ram/>.
2. Ở thanh điều hướng, chọn **Shared with me**, rồi chọn **Resource shares**.
3. Chọn lời mời resource share và chọn **Accept resource share**.
4. Sau khi chấp nhận, ghi lại **ARN của namespace chia sẻ** từ chi tiết tài nguyên. Bạn sẽ dùng ARN này khi cấu hình Service Connect service.

---

## 4. Bước 4: Cấu hình Amazon ECS service với namespace chia sẻ

**📘 Theo docs AWS**

Sau khi chấp nhận namespace chia sẻ, bên dùng namespace có thể cấu hình Amazon ECS service dùng namespace chia sẻ. Cấu hình tương tự dùng namespace thông thường, nhưng bạn phải chỉ định **ARN** của namespace **thay vì tên**. Quy trình tạo service chi tiết, xem *Creating an Amazon ECS rolling update deployment* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/create-service-console-v2.html>).

**Tạo service với namespace chia sẻ bằng AWS Management Console**

1. Mở console tại <https://console.aws.amazon.com/ecs/v2>.
2. Trên trang **Clusters**, chọn cluster muốn tạo service.
3. Ở **Services**, chọn **Create**.
4. Sau khi điền các chi tiết khác tuỳ workload, trong mục **Service Connect**, chọn **Use Service Connect**.
5. Ở **Namespace**, nhập **ARN đầy đủ** của namespace chia sẻ.

   Định dạng ARN là: `arn:aws:servicediscovery:region:account-id:namespace/namespace-id`

6. Cấu hình các thiết lập Service Connect còn lại theo loại service (client hoặc client-server).
7. Hoàn tất quy trình tạo service.

Bạn cũng có thể cấu hình service bằng AWS CLI hoặc AWS SDK bằng cách chỉ định ARN của namespace chia sẻ trong tham số **`namespace`** của **`serviceConnectConfiguration`**.

```
aws ecs create-service \
    --cluster my-cluster \
    --service-name my-service \
    --task-definition my-task-def \
    --service-connect-configuration '{
        "enabled": true,
        "namespace": "arn:aws:servicediscovery:us-west-2:123456789012:namespace/ns-abcdef1234567890",
        "services": [{
            "portName": "web",
            "discoveryName": "my-service",
            "clientAliases": [{
                "port": 80,
                "dnsName": "my-service"
            }]
        }]
    }'
```

**💡 Giải thích thêm**

Đọc cấu hình `serviceConnectConfiguration` trong ví dụ:

| Trường | Giá trị ví dụ | Ý nghĩa |
|---|---|---|
| `enabled` | `true` | Bật Service Connect |
| `namespace` | `arn:aws:servicediscovery:…:namespace/ns-…` | **ARN** của namespace chia sẻ (không phải tên) |
| `services[].portName` | `web` | Tên port mapping trong task definition |
| `services[].discoveryName` | `my-service` | Discovery name |
| `services[].clientAliases[].port` | `80` | Port của endpoint |
| `services[].clientAliases[].dnsName` | `my-service` | Tên DNS của endpoint |

Khác biệt duy nhất trang nhấn mạnh so với namespace thông thường: ô **Namespace** (console) hoặc trường `namespace` (CLI/SDK) nhận **ARN đầy đủ** thay vì tên.

---

## 5. Tổng hợp

**💡 Giải thích thêm**

| Bước | Ai làm | Ở đâu | Kết quả |
|---|---|---|---|
| 1 | Chủ namespace | Console AWS Cloud Map → **Create namespace** | Namespace (HTTP / Private DNS / Public DNS) |
| 2 | Chủ namespace | Console AWS RAM → **Create resource share** | Resource share với **AWSRAMPermissionCloudMapECSFullPermission**, gửi tới account / OU |
| 3 | Bên dùng | Console AWS RAM → **Shared with me** → **Resource shares** → **Accept resource share** | Ghi lại **ARN** namespace |
| 4 | Bên dùng | Console ECS (**Use Service Connect**) hoặc CLI/SDK (`serviceConnectConfiguration.namespace`) | Service dùng namespace chia sẻ qua **ARN** |

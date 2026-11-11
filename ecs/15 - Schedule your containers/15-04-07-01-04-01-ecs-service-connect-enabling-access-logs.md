# Enabling access logs for Amazon ECS Service Connect

> Nguồn: **Amazon ECS Developer Guide — Enabling access logs for Amazon ECS Service Connect**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-connect-access-logs-configuration.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: **bản dịch** sang tiếng Việt, đi đúng thứ tự và đúng nội dung của trang gốc, không thêm, không bớt ý. Lệnh ví dụ giữ nguyên như trên docs.
> - Khối **💡 Giải thích thêm**: chỉ giải thích lại những gì **chính trang này** đã nói, cho dễ hiểu hơn. Không đưa thông tin từ trang khác hay ví dụ tự đặt.
> - Tên nút trên console, tên tham số giữ nguyên tiếng Anh (in đậm).
> - Tài liệu AWS được phát hành theo giấy phép **Creative Commons Attribution-ShareAlike 4.0**; bản dịch này ghi rõ nguồn ở trên.

---

## Mục lục

0. [Phần mở đầu](#0-phần-mở-đầu)
1. [Bật access log bằng AWS CLI](#1-bật-access-log-bằng-aws-cli)
2. [Bật access log bằng console](#2-bật-access-log-bằng-console)
3. [So sánh CLI và console](#3-so-sánh-cli-và-console)

---

## 0. Phần mở đầu

**📘 Theo docs AWS**

Access log **không được bật mặc định** cho Amazon ECS service dùng Service Connect. Bạn có thể bật access log theo các cách sau.

---

## 1. Bật access log bằng AWS CLI

**📘 Theo docs AWS**

Lệnh sau cho thấy cách bật access log cho một Amazon ECS service bằng AWS CLI, bằng cách chỉ định **`accessLogConfiguration`** khi tạo service:

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
        }],
        "logConfiguration": {
            "logDriver": "awslogs",
            "options": {
                "awslogs-group": "my-envoy-log-group",
                "awslogs-region": "us-west-2",
                "awslogs-stream-prefix": "myapp-envoy-logs"
            }
        },
         "accessLogConfiguration": {
            "format": "TEXT",
            "includeQueryParameters": "ENABLED"
        }
    }'
```

**💡 Giải thích thêm**

Các phần của `--service-connect-configuration` trong ví dụ:

| Phần | Giá trị ví dụ | Vai trò |
|---|---|---|
| `enabled` | `true` | Bật Service Connect |
| `namespace` | ARN namespace | Namespace của service |
| `services[]` | `portName: web`, `discoveryName: my-service`, `clientAliases: port 80 / dnsName my-service` | Endpoint của service |
| `logConfiguration` | `awslogs` → `my-envoy-log-group`, `us-west-2`, prefix `myapp-envoy-logs` | **Nơi gửi log** |
| `accessLogConfiguration` | `format: TEXT`, `includeQueryParameters: ENABLED` | **Bật access log**, định dạng, có query parameter |

Phần thêm vào để bật access log là `accessLogConfiguration` (cùng với `logConfiguration` chỉ nơi gửi log).

---

## 2. Bật access log bằng console

**📘 Theo docs AWS**

Quy trình tạo service chi tiết, xem *Creating an Amazon ECS rolling update deployment* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/create-service-console-v2.html>).

**Tạo service với namespace chia sẻ bằng AWS Management Console**

1. Mở console tại <https://console.aws.amazon.com/ecs/v2>.
2. Trên trang **Clusters**, chọn cluster muốn tạo service.
3. Ở **Services**, chọn **Create**.
4. Sau khi điền các chi tiết khác tuỳ workload, trong mục **Service Connect**, chọn **Use Service Connect**.
5. Cấu hình các thiết lập Service Connect theo loại service (client hoặc client-server).
6. Mở rộng **Access log configuration**. Ở **Format**, chọn **JSON** hoặc `TEXT`.
7. Để đưa query parameter vào access log, chọn **Include query parameters**.
8. Hoàn tất quy trình tạo service.

**💡 Giải thích thêm**

```
Create service
 └─ Service Connect: Use Service Connect
      ├─ cấu hình client / client-server
      └─ Access log configuration
           ├─ Format: JSON | TEXT
           └─ [ ] Include query parameters
```

Tiêu đề của quy trình console trên trang gốc ghi "Tạo service với **namespace chia sẻ**", trong khi nội dung các bước là bật access log và không có bước nào về namespace chia sẻ. Có vẻ tiêu đề được chép từ trang thiết lập namespace chia sẻ.

---

## 3. So sánh CLI và console

**💡 Giải thích thêm**

Tổng hợp từ hai mục trên của chính trang:

| | AWS CLI (`create-service`) | Console |
|---|---|---|
| Nơi bật | `accessLogConfiguration` trong `--service-connect-configuration` | Mục **Access log configuration** trong phần **Service Connect** |
| Định dạng | `"format": "TEXT"` (ví dụ) | **JSON** hoặc `TEXT` |
| Query parameter | `"includeQueryParameters": "ENABLED"` | Chọn **Include query parameters** |
| Nơi gửi log | `logConfiguration` (ví dụ dùng `awslogs`) | (các bước console trên trang không nêu) |

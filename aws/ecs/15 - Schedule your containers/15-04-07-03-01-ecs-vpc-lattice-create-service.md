# Create a service that uses VPC Lattice

> Nguồn: **Amazon ECS Developer Guide — Create a service that uses VPC Lattice**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/ecs-vpc-lattice-create-service.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: **bản dịch** sang tiếng Việt, đi đúng thứ tự và đúng nội dung của trang gốc, không thêm, không bớt ý. Lệnh, file JSON và output ví dụ giữ nguyên như trên docs.
> - Khối **💡 Giải thích thêm**: chỉ giải thích lại những gì **chính trang này** đã nói, cho dễ hiểu hơn. Không đưa thông tin từ trang khác hay ví dụ tự đặt.
> - Tên nút trên console, tham số, lệnh giữ nguyên tiếng Anh (in đậm).
> - Tài liệu AWS được phát hành theo giấy phép **Creative Commons Attribution-ShareAlike 4.0**; bản dịch này ghi rõ nguồn ở trên.

---

## Mục lục

0. [Phần mở đầu](#0-phần-mở-đầu)
1. [Điều kiện tiên quyết](#1-điều-kiện-tiên-quyết)
2. [Tạo service dùng VPC Lattice bằng AWS Management Console](#2-tạo-service-dùng-vpc-lattice-bằng-aws-management-console)
3. [Tạo service dùng VPC Lattice bằng AWS CLI](#3-tạo-service-dùng-vpc-lattice-bằng-aws-cli)
4. [Ghi chú về trang gốc](#4-ghi-chú-về-trang-gốc)

---

## 0. Phần mở đầu

**📘 Theo docs AWS**

Bạn có thể dùng **AWS Management Console** hoặc **AWS CLI** để tạo service với VPC Lattice.

---

## 1. Điều kiện tiên quyết

**📘 Theo docs AWS**

Trước khi bắt đầu hướng dẫn này, hãy đảm bảo các điều kiện tiên quyết sau được đáp ứng:

- Đã cài và cấu hình **phiên bản AWS CLI mới nhất**. Thông tin thêm, xem *Installing the AWS Command Line Interface* (<https://docs.aws.amazon.com/cli/latest/userguide/install-cliv2.html>).

  > **Note:** Bạn có thể dùng **dual-stack service endpoint** để làm việc với Amazon ECS từ AWS CLI, SDK và Amazon ECS API qua cả IPv4 và IPv6. Thông tin thêm, xem *Using Amazon ECS dual-stack endpoints* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/dual-stack-endpoint.html>).

- Đã hoàn tất các bước trong *Set up to use Amazon ECS* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/get-set-up-for-amazon-ecs.html>).
- IAM user của bạn có các quyền cần thiết như trong ví dụ IAM policy **AmazonECS_FullAccess** (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/security-iam-awsmanpol.html#security-iam-awsmanpol-AmazonECS_FullAccess>).

---

## 2. Tạo service dùng VPC Lattice bằng AWS Management Console

**📘 Theo docs AWS**

Làm theo các bước sau để tạo service với VPC Lattice bằng AWS Management Console.

1. Mở console tại <https://console.aws.amazon.com/ecs/v2>.
2. Ở trang điều hướng, chọn **Clusters**.
3. Trên trang **Clusters**, chọn cluster để tạo service.
4. Từ tab **Services**, chọn **Create**.

   Nếu bạn chưa từng tạo service, hãy làm theo các bước trong *Creating an Amazon ECS service using the console* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/create-service-console-v2.html>), rồi tiếp tục với các bước này khi tới mục VPC Lattice.

5. Chọn **Turn on VPC Lattice** bằng cách đánh dấu nút.
6. Để dùng role có sẵn, ở **ECS infrastructure role for Amazon ECS**, chọn role bạn đã tạo để dùng khi tạo target group VPC Lattice. Để tạo role mới, chọn **Create ECS infrastructure role**.
7. Chọn **VPC**.

   **VPC** phụ thuộc vào network mode bạn chọn khi đăng ký task definition. Nếu bạn dùng mode `host` hoặc `network` với EC2, hãy chọn VPC của bạn.

   Với mode `awsvpc`, VPC được **chọn tự động** dựa trên VPC bạn đã chọn ở mục **Networking** và **không thể thay đổi**.

8. Ở **Target Groups**, chọn một hoặc nhiều target group. Bạn cần chọn **ít nhất một** target group và có thể có **tối đa năm**. Chọn **Add target group** để thêm target group. Chọn **Port name**, **Protocol** và **Port** cho mỗi target group đã chọn. Để xoá một target group, chọn **Remove**.

   > **Note:**
   > - Nếu bạn muốn thêm **target group có sẵn**, bạn cần dùng AWS CLI. Hướng dẫn cách thêm target group bằng AWS CLI, xem *register-targets* (<https://docs.aws.amazon.com/cli/latest/reference/vpc-lattice/register-targets.html>) trong *AWS Command Line Interface Reference*.
   > - Dù một VPC Lattice service có thể có nhiều target group, **mỗi target group chỉ được thêm vào một service**.
   > - Để tạo service ở cấu hình **IPv6-only**, chọn target group có loại địa chỉ IP là **`IPv6`**.

9. Đến đây, bạn chuyển sang **console VPC Lattice** để tiếp tục thiết lập. Đây là nơi bạn đưa target group mới vào **default action của listener** hoặc vào **rule** của một VPC Lattice service có sẵn.

   Thông tin thêm, xem *Listener rules for your VPC Lattice service* (<https://docs.aws.amazon.com/vpc-lattice/latest/ug/listener-rules.html>).

> **Important:** Bạn cần cho phép **inbound rule** với prefix **`vpc-lattice`** trong security group, nếu không task và health check có thể **thất bại**.

**💡 Giải thích thêm**

```
ECS console: Create service
 └─ VPC Lattice
     ├─ [x] Turn on VPC Lattice
     ├─ ECS infrastructure role (có sẵn / tạo mới)
     ├─ VPC (awsvpc: tự chọn theo Networking, không đổi được)
     └─ Target Groups: 1–5, mỗi cái chọn Port name + Protocol + Port
VPC Lattice console:
 └─ đưa target group vào default action của listener hoặc rule của service
Security group: inbound cho prefix vpc-lattice
```

| Điểm | Theo trang |
|---|---|
| Số target group | Tối thiểu 1, tối đa 5 |
| Target group có sẵn | Phải thêm bằng AWS CLI (`register-targets`) |
| Một target group | Chỉ thuộc **một** service |
| IPv6-only | Target group loại địa chỉ IP `IPv6` |
| Security group | Inbound rule cho prefix `vpc-lattice` — thiếu thì task và health check có thể thất bại |

---

## 3. Tạo service dùng VPC Lattice bằng AWS CLI

**📘 Theo docs AWS**

Dùng AWS CLI để tạo service với VPC Lattice. Thay mỗi `user input placeholder` bằng thông tin của bạn.

1. Tạo một file cấu hình target group. Ví dụ sau tên là `tg-config.json`

   ```
   {
       "ipAddressType": "IPV4",
       "port": 443,
       "protocol": "HTTPS",
       "protocolVersion": "HTTP1",
       "vpcIdentifier": "vpc-f1663d9868EXAMPLE"
   }
   ```

2. Dùng lệnh sau để tạo target group VPC Lattice.

   ```
   aws vpc-lattice create-target-group \
       --name my-lattice-target-group-ip \
       --type IP \
       --config file://tg-config.json
   ```

   > **Note:** Để tạo service ở cấu hình IPv6-only, tạo target group có loại địa chỉ IP là `IPv6`. Thông tin thêm, xem *create-target-group* (<https://docs.aws.amazon.com/cli/latest/reference/vpc-lattice/create-target-group.html>) trong *AWS CLI Command Reference*.

   Output ví dụ:

   ```
   {
       "arn": "arn:aws:vpc-lattice:us-east-2:123456789012:targetgroup/tg-0eaa4b9ab4EXAMPLE",
       "config": {
           "healthCheck": {
               "enabled": true,
               "healthCheckIntervalSeconds": 30,
               "healthCheckTimeoutSeconds": 5,
               "healthyThresholdCount": 5,
               "matcher": {
                   "httpCode": "200"
               },
               "path": "/",
               "protocol": "HTTPS",
               "protocolVersion": "HTTP1",
               "unhealthyThresholdCount": 2
           },
           "ipAddressType": "IPV4",
           "port": 443,
           "protocol": "HTTPS",
           "protocolVersion": "HTTP1",
           "vpcIdentifier": "vpc-f1663d9868EXAMPLE"
       },
       "id": "tg-0eaa4b9ab4EXAMPLE",
       "name": "my-lattice-target-group-ip",
       "status": "CREATE_IN_PROGRESS",
       "type": "IP"
   }
   ```

3. File JSON sau tên `ecs-service-vpc-lattice.json` là ví dụ dùng để gắn một Amazon ECS service vào target group VPC Lattice. **`portName`** trong ví dụ sau **giống** tên bạn đã định nghĩa ở trường **`name`** của thuộc tính **`portMappings`** trong task definition.

   ```
   {
       "serviceName": "ecs-service-vpc-lattice",
       "taskDefinition": "ecs-task-def",
           "vpcLatticeConfigurations": [
           {
               "targetGroupArn": "arn:aws:vpc-lattice:us-west-2:123456789012:targetgroup/tg-0eaa4b9ab4EXAMPLE",
               "portName": "testvpclattice",
               "roleArn": "arn:aws:iam::123456789012:role/ecsInfrastructureRoleVpcLattice"
           }
       ],
       "desiredCount": 5,
       "role": "ecsServiceRole"
   }
   ```

   Dùng lệnh sau để tạo Amazon ECS service và gắn nó vào target group VPC Lattice bằng ví dụ JSON trên.

   ```
   aws ecs create-service \
       --cluster clusterName \
       --serviceName ecs-service-vpc-lattice \
       --cli-input-json file://ecs-service-vpc-lattice.json
   ```

   Để dùng **blue/green, linear hoặc canary deployment** với VPC Lattice, thêm một khối **`advancedConfiguration`** (alternate target group cùng các listener rule cho production và test) vào mục `vpcLatticeConfigurations`, và đặt deployment strategy trong **`deploymentConfiguration`**. Thông tin thêm, xem *VPC Lattice resources for blue/green, linear, and canary deployments* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/vpc-lattice-resources-for-blue-green.html>).

**💡 Giải thích thêm**

Luồng CLI:

```
1. tg-config.json (IPV4, port 443, HTTPS, HTTP1, VPC)
2. aws vpc-lattice create-target-group --type IP → lấy ARN target group
3. ecs-service-vpc-lattice.json (vpcLatticeConfigurations) → aws ecs create-service
```

Health check mặc định của target group, theo output ví dụ:

| Trường | Giá trị |
|---|---|
| `enabled` | `true` |
| `healthCheckIntervalSeconds` | 30 |
| `healthCheckTimeoutSeconds` | 5 |
| `healthyThresholdCount` | 5 |
| `unhealthyThresholdCount` | 2 |
| `matcher.httpCode` | `200` |
| `path` | `/` |

Các trường trong `vpcLatticeConfigurations`:

| Trường | Giá trị ví dụ | Vai trò |
|---|---|---|
| `targetGroupArn` | ARN target group VPC Lattice | Target group gắn với service |
| `portName` | `testvpclattice` | Phải khớp `portMappings[].name` trong task definition |
| `roleArn` | `ecsInfrastructureRoleVpcLattice` | ECS infrastructure role |

Blue/green, linear, canary: thêm `advancedConfiguration` (alternate target group + listener rule production và test) và đặt strategy trong `deploymentConfiguration`.

---

## 4. Ghi chú về trang gốc

**💡 Giải thích thêm**

Các điểm trên chính trang mà bản dịch giữ nguyên như docs:

| Chỗ | Ghi chú |
|---|---|
| Bước 7 (console) | Nhắc mode `host` hoặc **`network`** với EC2 — trang không giải thích mode `network` |
| Lệnh `create-service` | Dùng `--serviceName` (kiểu camelCase), trong khi `serviceName` cũng đã có trong file JSON |
| Region trong ARN | Output `create-target-group` dùng `us-east-2`, còn file service JSON dùng `us-west-2` cho cùng ID target group |
| Note bước 8 | "you need use" thiếu chữ "to" — không ảnh hưởng nghĩa |

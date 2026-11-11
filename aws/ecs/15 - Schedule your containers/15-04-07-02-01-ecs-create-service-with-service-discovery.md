# Creating an Amazon ECS service that uses Service Discovery

> Nguồn: **Amazon ECS Developer Guide — Creating an Amazon ECS service that uses Service Discovery**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/create-service-discovery.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: **bản dịch** sang tiếng Việt, đi đúng thứ tự và đúng nội dung của trang gốc, không thêm, không bớt ý. Lệnh, file JSON và output ví dụ giữ nguyên như trên docs.
> - Khối **💡 Giải thích thêm**: chỉ giải thích lại những gì **chính trang này** đã nói, cho dễ hiểu hơn. Không đưa thông tin từ trang khác hay ví dụ tự đặt.
> - Tên lệnh, tham số, ID ví dụ giữ nguyên tiếng Anh.
> - Tài liệu AWS được phát hành theo giấy phép **Creative Commons Attribution-ShareAlike 4.0**; bản dịch này ghi rõ nguồn ở trên.

---

## Mục lục

0. [Phần mở đầu](#0-phần-mở-đầu)
1. [Điều kiện tiên quyết](#1-điều-kiện-tiên-quyết)
2. [Bước 1: Tạo tài nguyên service discovery trong AWS Cloud Map](#2-bước-1-tạo-tài-nguyên-service-discovery-trong-aws-cloud-map)
3. [Bước 2: Tạo tài nguyên Amazon ECS](#3-bước-2-tạo-tài-nguyên-amazon-ecs)
4. [Bước 3: Kiểm tra service discovery trong AWS Cloud Map](#4-bước-3-kiểm-tra-service-discovery-trong-aws-cloud-map)
5. [Bước 4: Dọn dẹp](#5-bước-4-dọn-dẹp)
6. [Ghi chú về trang gốc](#6-ghi-chú-về-trang-gốc)

---

## 0. Phần mở đầu

**📘 Theo docs AWS**

Tìm hiểu cách tạo một service chứa task **Fargate** dùng service discovery bằng AWS CLI.

Danh sách AWS Region hỗ trợ service discovery, xem *Use service discovery to connect Amazon ECS services with DNS names* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-discovery.html>).

Thông tin về các Region hỗ trợ Fargate, xem *Supported Regions for Amazon ECS on AWS Fargate* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/AWS_Fargate-Regions.html>).

> **Note:** Bạn có thể dùng **dual-stack service endpoint** để làm việc với Amazon ECS từ AWS CLI, SDK và Amazon ECS API qua cả IPv4 và IPv6. Thông tin thêm, xem *Using Amazon ECS dual-stack endpoints* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/dual-stack-endpoint.html>).

**💡 Giải thích thêm**

```
Bước 1 (Cloud Map): private DNS namespace "tutorial" → service discovery service "myapplication" (bản ghi A, TTL 300)
Bước 2 (ECS):       cluster "tutorial" → task definition httpd (Fargate, awsvpc) → service có serviceRegistries
Bước 3 (kiểm tra):  list-instances, discover-instances, get-namespace + Route 53, dig
Bước 4 (dọn dẹp):   huỷ đăng ký instance → xoá Cloud Map service → xoá namespace → desired 0 → xoá ECS service → xoá cluster
```

---

## 1. Điều kiện tiên quyết

**📘 Theo docs AWS**

Trước khi bắt đầu hướng dẫn này, hãy đảm bảo các điều kiện tiên quyết sau được đáp ứng:

- Đã cài và cấu hình **phiên bản AWS CLI mới nhất**. Thông tin thêm, xem *Installing or updating to the latest version of the AWS CLI* (<https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html>).
- Đã hoàn tất các bước trong *Set up to use Amazon ECS* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/get-set-up-for-amazon-ecs.html>).
- IAM user của bạn có các quyền cần thiết như trong ví dụ IAM policy **AmazonECS_FullAccess** (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/security-iam-awsmanpol.html#security-iam-awsmanpol-AmazonECS_FullAccess>).
- Bạn đã tạo ít nhất **một VPC** và **một security group**. Thông tin thêm, xem *Create a virtual private cloud* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/get-set-up-for-amazon-ecs.html#create-a-vpc>).

---

## 2. Bước 1: Tạo tài nguyên service discovery trong AWS Cloud Map

**📘 Theo docs AWS**

Làm theo các bước sau để tạo service discovery namespace và service discovery service:

1. Tạo một **private Cloud Map service discovery namespace**. Ví dụ này tạo namespace tên `tutorial`. Thay `vpc-abcd1234` bằng ID của một VPC có sẵn của bạn.

   ```
   aws servicediscovery create-private-dns-namespace \
         --name tutorial \
         --vpc vpc-abcd1234
   ```

   Output của lệnh này như sau.

   ```
   {
       "OperationId": "h2qe3s6dxftvvt7riu6lfy2f6c3jlhf4-je6chs2e"
   }
   ```

2. Dùng `OperationId` từ output của bước trước, kiểm tra private namespace đã được tạo thành công. **Ghi lại namespace ID** vì bạn sẽ dùng nó ở các lệnh sau.

   ```
   aws servicediscovery get-operation \
         --operation-id h2qe3s6dxftvvt7riu6lfy2f6c3jlhf4-je6chs2e
   ```

   Output như sau.

   ```
   {
       "Operation": {
           "Id": "h2qe3s6dxftvvt7riu6lfy2f6c3jlhf4-je6chs2e",
           "Type": "CREATE_NAMESPACE",
           "Status": "SUCCESS",
           "CreateDate": 1519777852.502,
           "UpdateDate": 1519777856.086,
           "Targets": {
              "NAMESPACE": "ns-uejictsjen2i4eeg"
           }
       }
   }
   ```

3. Dùng `NAMESPACE` ID từ output của bước trước, tạo một **service discovery service**. Ví dụ này tạo service tên `myapplication`. **Ghi lại service ID và ARN** vì bạn sẽ dùng chúng ở các lệnh sau.

   ```
   aws servicediscovery create-service \
         --name myapplication \
         --dns-config "NamespaceId="ns-uejictsjen2i4eeg",DnsRecords=[{Type="A",TTL="300"}]" \
         --health-check-custom-config FailureThreshold=1
   ```

   Output như sau.

   ```
   {
       "Service": {
          "Id": "srv-utcrh6wavdkggqtk",
           "Arn": "arn:aws:servicediscovery:region:aws_account_id:service/srv-utcrh6wavdkggqtk",
           "Name": "myapplication",
           "DnsConfig": {
               "NamespaceId": "ns-uejictsjen2i4eeg",
               "DnsRecords": [
                   {
                       "Type": "A",
                       "TTL": 300
                   }
               ]
           },
           "HealthCheckCustomConfig": {
               "FailureThreshold": 1
           },
           "CreatorRequestId": "e49a8797-b735-481b-a657-b74d1d6734eb"
       }
   }
   ```

**💡 Giải thích thêm**

Các giá trị cần ghi lại và dùng tiếp:

| Từ lệnh | Giá trị | Dùng ở đâu |
|---|---|---|
| `create-private-dns-namespace` | `OperationId` | `get-operation` để lấy namespace ID |
| `get-operation` | `NAMESPACE`: `ns-uejictsjen2i4eeg` | `create-service --dns-config`, `get-namespace`, `delete-namespace` |
| `create-service` | `Id`: `srv-utcrh6wavdkggqtk`, `Arn` | `registryArn` của ECS service; `list-instances`; dọn dẹp |

Cấu hình của service discovery service `myapplication`:

| Tham số | Giá trị | Ý nghĩa |
|---|---|---|
| `DnsRecords` | `Type=A`, `TTL=300` | Bản ghi `A`, cache 300 giây |
| `--health-check-custom-config` | `FailureThreshold=1` | Dùng health check tuỳ chỉnh (`HealthCheckCustomConfig`) |

---

## 3. Bước 2: Tạo tài nguyên Amazon ECS

**📘 Theo docs AWS**

Làm theo các bước sau để tạo Amazon ECS cluster, task definition và service:

1. Tạo một Amazon ECS cluster. Ví dụ này tạo cluster tên `tutorial`.

   ```
   aws ecs create-cluster \
         --cluster-name tutorial
   ```

2. Đăng ký một task definition tương thích Fargate và dùng network mode `awsvpc`. Làm theo các bước sau:

   1. Tạo file tên **`fargate-task.json`** với nội dung task definition sau.

      ```
      {
          "family": "tutorial-task-def",
              "networkMode": "awsvpc",
              "containerDefinitions": [
                  {
                      "name": "sample-app",
                      "image": "public.ecr.aws/docker/library/httpd:2.4",
                      "portMappings": [
                          {
                              "containerPort": 80,
                              "hostPort": 80,
                              "protocol": "tcp"
                          }
                      ],
                      "essential": true,
                      "entryPoint": [
                          "sh",
                          "-c"
                      ],
                      "command": [
                          "/bin/sh -c \"echo '<html> <head> <title>Amazon ECS Sample App</title> <style>body {margin-top: 40px; background-color: #333;} </style> </head><body> <div style=color:white;text-align:center> <h1>Amazon ECS Sample App</h1> <h2>Congratulations!</h2> <p>Your application is now running on a container in Amazon ECS.</p> </div></body></html>' >  /usr/local/apache2/htdocs/index.html && httpd-foreground\""
                      ]
                  }
              ],
              "requiresCompatibilities": [
                  "FARGATE"
              ],
              "cpu": "256",
              "memory": "512"
      }
      ```

   2. Đăng ký task definition bằng `fargate-task.json`.

      ```
      aws ecs register-task-definition \
            --cli-input-json file://fargate-task.json
      ```

3. Tạo ECS service theo các bước sau:

   1. Tạo file tên **`ecs-service-discovery.json`** với nội dung ECS service bạn đang tạo. Ví dụ này dùng task definition tạo ở bước trước. Cần có **`awsvpcConfiguration`** vì task definition ví dụ dùng network mode `awsvpc`.

      Khi tạo ECS service, chỉ định **Fargate** và platform version **`LATEST`** hỗ trợ service discovery. Khi service discovery service được tạo trong AWS Cloud Map, **`registryArn`** là ARN được trả về. `securityGroups` và `subnets` phải thuộc **VPC đã dùng để tạo Cloud Map namespace**. Bạn có thể lấy ID security group và subnet từ Amazon VPC Console.

      ```
      {
          "cluster": "tutorial",
          "serviceName": "ecs-service-discovery",
          "taskDefinition": "tutorial-task-def",
          "serviceRegistries": [
             {
                "registryArn": "arn:aws:servicediscovery:region:aws_account_id:service/srv-utcrh6wavdkggqtk"
             }
          ],
          "launchType": "FARGATE",
          "platformVersion": "LATEST",
          "networkConfiguration": {
             "awsvpcConfiguration": {
                "assignPublicIp": "ENABLED",
                "securityGroups": [ "sg-abcd1234" ],
                "subnets": [ "subnet-abcd1234" ]
             }
          },
          "desiredCount": 1
      }
      ```

   2. Tạo ECS service bằng `ecs-service-discovery.json`.

      ```
      aws ecs create-service \
            --cli-input-json file://ecs-service-discovery.json
      ```

**💡 Giải thích thêm**

Chỗ nối ECS service với service discovery:

```
Cloud Map service "myapplication" (ARN) ──► ECS service: serviceRegistries[].registryArn
```

| Điều kiện theo trang | |
|---|---|
| Launch type | Fargate, platform version `LATEST` |
| Network mode | `awsvpc` → cần `awsvpcConfiguration` |
| Subnet, security group | Phải thuộc **cùng VPC** với Cloud Map namespace |

---

## 4. Bước 3: Kiểm tra service discovery trong AWS Cloud Map

**📘 Theo docs AWS**

Bạn có thể kiểm tra mọi thứ đã được tạo đúng bằng cách truy vấn thông tin service discovery. Sau khi service discovery được cấu hình, bạn có thể dùng **API của AWS Cloud Map**, hoặc gọi **`dig`** từ một instance trong VPC. Làm theo các bước sau:

1. Dùng service discovery service ID, liệt kê các service discovery instance. **Ghi lại instance ID** (được in đậm) để dọn dẹp tài nguyên.

   ```
   aws servicediscovery list-instances \
         --service-id srv-utcrh6wavdkggqtk
   ```

   Output như sau.

   ```
   {
       "Instances": [
           {
               "Id": "16becc26-8558-4af1-9fbd-f81be062a266",
               "Attributes": {
                   "AWS_INSTANCE_IPV4": "172.31.87.2"
                   "AWS_INSTANCE_PORT": "80",
                   "AVAILABILITY_ZONE": "us-east-1a",
                   "REGION": "us-east-1",
                   "ECS_SERVICE_NAME": "ecs-service-discovery",
                   "ECS_CLUSTER_NAME": "tutorial",
                   "ECS_TASK_DEFINITION_FAMILY": "tutorial-task-def"
               }
           }
       ]
   }
   ```

2. Dùng service discovery namespace, service, và các tham số bổ sung như tên ECS cluster để truy vấn chi tiết về các service discovery instance.

   ```
   aws servicediscovery discover-instances \
         --namespace-name tutorial \
         --service-name myapplication \
         --query-parameters ECS_CLUSTER_NAME=tutorial
   ```

3. Bản ghi DNS được tạo trong Route 53 hosted zone cho service discovery service có thể được truy vấn bằng các lệnh AWS CLI sau:

   1. Dùng namespace ID, lấy thông tin về namespace, gồm **Route 53 hosted zone ID**.

      ```
      aws servicediscovery \
            get-namespace --id ns-uejictsjen2i4eeg
      ```

      Output như sau.

      ```
      {
          "Namespace": {
              "Id": "ns-uejictsjen2i4eeg",
              "Arn": "arn:aws:servicediscovery:region:aws_account_id:namespace/ns-uejictsjen2i4eeg",
              "Name": "tutorial",
              "Type": "DNS_PRIVATE",
              "Properties": {
                   "DnsProperties": {
                      "HostedZoneId": "Z35JQ4ZFDRYPLV"
                  }
              },
              "CreateDate": 1519777852.502,
              "CreatorRequestId": "9049a1d5-25e4-4115-8625-96dbda9a6093"
          }
      }
      ```

   2. Dùng Route 53 hosted zone ID từ bước trước (xem phần in đậm), lấy resource record set của hosted zone.

      ```
      aws route53 list-resource-record-sets \
            --hosted-zone-id Z35JQ4ZFDRYPLV
      ```

4. Bạn cũng có thể truy vấn DNS từ một instance trong VPC bằng `dig`.

   ```
   dig +short myapplication.tutorial
   ```

**💡 Giải thích thêm**

Bốn cách kiểm tra theo trang:

| Cách | Lệnh | Thấy gì |
|---|---|---|
| Liệt kê instance | `servicediscovery list-instances --service-id …` | Instance ID + thuộc tính (IP, port, AZ, Region, tên service/cluster, family) |
| Discover có lọc | `servicediscovery discover-instances … --query-parameters ECS_CLUSTER_NAME=tutorial` | Instance khớp điều kiện |
| Qua Route 53 | `get-namespace` (lấy `HostedZoneId`) → `route53 list-resource-record-sets` | Bản ghi DNS trong hosted zone |
| DNS trong VPC | `dig +short myapplication.tutorial` | IP của task |

Tên DNS `myapplication.tutorial` theo dạng **`<tên service discovery service>.<namespace>`**. Namespace có `Type` là **`DNS_PRIVATE`**.

---

## 5. Bước 4: Dọn dẹp

**📘 Theo docs AWS**

Khi xong hướng dẫn này, hãy dọn dẹp các tài nguyên liên quan để tránh bị tính phí cho tài nguyên không dùng. Làm theo các bước sau:

1. Huỷ đăng ký các service discovery instance bằng service ID và instance ID đã ghi lại trước đó.

   ```
   aws servicediscovery deregister-instance \
         --service-id srv-utcrh6wavdkggqtk \
         --instance-id 16becc26-8558-4af1-9fbd-f81be062a266
   ```

   Output như sau.

   ```
   {
       "OperationId": "xhu73bsertlyffhm3faqi7kumsmx274n-jh0zimzv"
   }
   ```

2. Dùng `OperationId` từ output của bước trước, kiểm tra service discovery instance đã được huỷ đăng ký thành công.

   ```
   aws servicediscovery get-operation \
         --operation-id xhu73bsertlyffhm3faqi7kumsmx274n-jh0zimzv
   ```

   ```
   {
     "Operation": {
           "Id": "xhu73bsertlyffhm3faqi7kumsmx274n-jh0zimzv",
           "Type": "DEREGISTER_INSTANCE",
           "Status": "SUCCESS",
           "CreateDate": 1525984073.707,
           "UpdateDate": 1525984076.426,
           "Targets": {
               "INSTANCE": "16becc26-8558-4af1-9fbd-f81be062a266",
               "ROUTE_53_CHANGE_ID": "C5NSRG1J4I1FH",
               "SERVICE": "srv-utcrh6wavdkggqtk"
           }
       }
   }
   ```

3. Xoá service discovery service bằng service ID.

   ```
   aws servicediscovery delete-service \
         --id srv-utcrh6wavdkggqtk
   ```

4. Xoá service discovery namespace bằng namespace ID.

   ```
   aws servicediscovery delete-namespace \
         --id ns-uejictsjen2i4eeg
   ```

   Output như sau.

   ```
   {
       "OperationId": "c3ncqglftesw4ibgj5baz6ktaoh6cg4t-jh0ztysj"
   }
   ```

5. Dùng `OperationId` từ output của bước trước, kiểm tra service discovery namespace đã được xoá thành công.

   ```
   aws servicediscovery get-operation \
         --operation-id c3ncqglftesw4ibgj5baz6ktaoh6cg4t-jh0ztysj
   ```

   Output như sau.

   ```
   {
       "Operation": {
           "Id": "c3ncqglftesw4ibgj5baz6ktaoh6cg4t-jh0ztysj",
           "Type": "DELETE_NAMESPACE",
           "Status": "SUCCESS",
           "CreateDate": 1525984602.211,
           "UpdateDate": 1525984602.558,
           "Targets": {
               "NAMESPACE": "ns-rymlehshst7hhukh",
               "ROUTE_53_CHANGE_ID": "CJP2A2M86XW3O"
           }
       }
   }
   ```

6. Cập nhật desired count của Amazon ECS service thành **`0`**. Bạn **phải** làm việc này để xoá service ở bước tiếp theo.

   ```
   aws ecs update-service \
         --cluster tutorial \
         --service ecs-service-discovery \
         --desired-count 0
   ```

7. Xoá Amazon ECS service.

   ```
   aws ecs delete-service \
         --cluster tutorial \
         --service ecs-service-discovery
   ```

8. Xoá Amazon ECS cluster.

   ```
   aws ecs delete-cluster \
         --cluster tutorial
   ```

**💡 Giải thích thêm**

Thứ tự dọn dẹp theo trang:

| # | Tài nguyên | Lệnh | Kiểm tra |
|---|---|---|---|
| 1–2 | Service discovery instance | `deregister-instance` | `get-operation` → `DEREGISTER_INSTANCE` `SUCCESS` |
| 3 | Service discovery service | `delete-service` | — |
| 4–5 | Namespace | `delete-namespace` | `get-operation` → `DELETE_NAMESPACE` `SUCCESS` |
| 6 | ECS service | `update-service --desired-count 0` | **Bắt buộc** trước khi xoá |
| 7 | ECS service | `delete-service` | — |
| 8 | ECS cluster | `delete-cluster` | — |

Mỗi thao tác bất đồng bộ của Cloud Map (tạo/xoá namespace, huỷ đăng ký instance) trả về một `OperationId`; dùng `get-operation` để xác nhận `Status: SUCCESS`.

---

## 6. Ghi chú về trang gốc

**💡 Giải thích thêm**

Các điểm trên chính trang mà bản dịch giữ nguyên như docs:

| Chỗ | Ghi chú |
|---|---|
| Output `list-instances` | Thiếu dấu phẩy sau dòng `"AWS_INSTANCE_IPV4": "172.31.87.2"`, nên không phải JSON hợp lệ |
| Bước 3.1 và 3.3.2 | Nói "in đậm" (instance ID, hosted zone ID) nhưng trong khối code không có định dạng đậm |
| Output kiểm tra xoá namespace | `NAMESPACE` là `ns-rymlehshst7hhukh`, khác ID namespace dùng xuyên suốt hướng dẫn (`ns-uejictsjen2i4eeg`) |
| Thứ tự dọn dẹp | Huỷ đăng ký instance và xoá tài nguyên Cloud Map ở bước 1–5, **trước** khi đưa ECS service về desired count 0 ở bước 6 |

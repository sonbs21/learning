# Configuring Amazon ECS Service Connect with the AWS CLI

> Nguồn: **Amazon ECS Developer Guide — Configuring Amazon ECS Service Connect with the AWS CLI**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/create-service-connect.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: **bản dịch** sang tiếng Việt, đi đúng thứ tự và đúng nội dung của trang gốc, không thêm, không bớt ý. Lệnh, file JSON và output ví dụ giữ nguyên như trên docs.
> - Khối **💡 Giải thích thêm**: chỉ giải thích lại những gì **chính trang này** đã nói, cho dễ hiểu hơn. Không đưa thông tin từ trang khác hay ví dụ tự đặt.
> - Tên tham số, lệnh, metric dimension giữ nguyên tiếng Anh.
> - Tài liệu AWS được phát hành theo giấy phép **Creative Commons Attribution-ShareAlike 4.0**; bản dịch này ghi rõ nguồn ở trên.

---

## Mục lục

0. [Phần mở đầu](#0-phần-mở-đầu)
1. [Điều kiện tiên quyết](#1-điều-kiện-tiên-quyết)
2. [Bước 1: Tạo cluster](#2-bước-1-tạo-cluster)
3. [Bước 2: Tạo service cho server](#3-bước-2-tạo-service-cho-server)
4. [Bước 3: Kiểm tra kết nối](#4-bước-3-kiểm-tra-kết-nối)
5. [Ghi chú về trang gốc](#5-ghi-chú-về-trang-gốc)

---

## 0. Phần mở đầu

**📘 Theo docs AWS**

Bạn có thể tạo một Amazon ECS service cho task **Fargate** dùng Service Connect bằng AWS CLI.

> **Note:** Bạn có thể dùng **dual-stack service endpoint** để làm việc với Amazon ECS từ AWS CLI, SDK và Amazon ECS API qua cả IPv4 và IPv6. Thông tin thêm, xem *Using Amazon ECS dual-stack endpoints* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/dual-stack-endpoint.html>).

**💡 Giải thích thêm**

Toàn bộ hướng dẫn gồm 3 bước:

```
Bước 1: tạo cluster "tutorial" + namespace mặc định "service-connect"
Bước 2: đăng ký task definition nginx (port có name + appProtocol) → tạo service có serviceConnectConfiguration
Bước 3: kết nối tới task bằng IP:port, xem header "server: envoy"; xem metric trong CloudWatch
```

---

## 1. Điều kiện tiên quyết

**📘 Theo docs AWS**

Sau đây là điều kiện tiên quyết cho Service Connect:

- Đã cài và cấu hình **phiên bản AWS CLI mới nhất**. Thông tin thêm, xem *Installing or updating to the latest version of the AWS CLI* (<https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html>).
- IAM user của bạn có các quyền cần thiết như trong ví dụ IAM policy **AmazonECS_FullAccess** (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/security-iam-awsmanpol.html#security-iam-awsmanpol-AmazonECS_FullAccess>).
- Bạn đã tạo sẵn **VPC, subnet, route table và security group** để dùng. Thông tin thêm, xem *Create a virtual private cloud* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/get-set-up-for-amazon-ecs.html#create-a-vpc>).
- Bạn có một **task execution role** tên **`ecsTaskExecutionRole`** và managed policy **`AmazonECSTaskExecutionRolePolicy`** được gắn vào role. Role này cho phép Fargate ghi log của ứng dụng NGINX và log của Service Connect proxy vào Amazon CloudWatch Logs. Thông tin thêm, xem *Creating the task execution role* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task_execution_IAM_role.html#create-task-execution-role>).

**💡 Giải thích thêm**

| Cần có | Ghi chú theo trang |
|---|---|
| AWS CLI mới nhất | — |
| Quyền IAM | Như ví dụ `AmazonECS_FullAccess` |
| VPC, subnet, route table, security group | Tạo sẵn |
| `ecsTaskExecutionRole` + `AmazonECSTaskExecutionRolePolicy` | Để ghi log NGINX và log proxy vào CloudWatch Logs |

---

## 2. Bước 1: Tạo cluster

**📘 Theo docs AWS**

Dùng các bước sau để tạo Amazon ECS cluster và namespace.

**Tạo Amazon ECS cluster và AWS Cloud Map namespace**

1. Tạo một Amazon ECS cluster tên **`tutorial`** để dùng. Tham số **`--service-connect-defaults`** đặt **namespace mặc định** của cluster. Trong output ví dụ, một AWS Cloud Map namespace tên `service-connect` **chưa tồn tại** trong account và AWS Region này, nên namespace được **Amazon ECS tạo**. Namespace được tạo trong AWS Cloud Map trong account, và hiển thị cùng mọi namespace khác, nên hãy dùng tên thể hiện **mục đích**.

   ```
   aws ecs create-cluster --cluster-name tutorial --service-connect-defaults namespace=service-connect
   ```

   Output:

   ```
   {
       "cluster": {
           "clusterArn": "arn:aws:ecs:us-west-2:123456789012:cluster/tutorial",
           "clusterName": "tutorial",
           "serviceConnectDefaults": {
               "namespace": "arn:aws:servicediscovery:us-west-2:123456789012:namespace/ns-EXAMPLE"
           },
           "status": "PROVISIONING",
           "registeredContainerInstancesCount": 0,
           "runningTasksCount": 0,
           "pendingTasksCount": 0,
           "activeServicesCount": 0,
           "statistics": [],
           "tags": [],
           "settings": [
               {
                   "name": "containerInsights",
                   "value": "disabled"
               }
           ],
           "capacityProviders": [],
           "defaultCapacityProviderStrategy": [],
           "attachments": [
               {
                   "id": "a1b2c3d4-5678-90ab-cdef-EXAMPLE11111",
                   "type": "sc",
                   "status": "ATTACHING",
                   "details": []
               }
           ],
           "attachmentsStatus": "UPDATE_IN_PROGRESS"
       }
   }
   }
   ```

2. Kiểm tra cluster đã được tạo:

   ```
   aws ecs describe-clusters --clusters tutorial
   ```

   Output:

   ```
   {
       "clusters": [
           {
               "clusterArn": "arn:aws:ecs:us-west-2:123456789012:cluster/tutorial",
               "clusterName": "tutorial",
               "serviceConnectDefaults": {
                   "namespace": "arn:aws:servicediscovery:us-west-2:123456789012:namespace/ns-EXAMPLE"
               },
               "status": "ACTIVE",
               "registeredContainerInstancesCount": 0,
               "runningTasksCount": 0,
               "pendingTasksCount": 0,
               "activeServicesCount": 0,
               "statistics": [],
               "tags": [],
               "settings": [],
               "capacityProviders": [],
               "defaultCapacityProviderStrategy": []
           }
       ],
       "failures": []
   }
   ```

3. (Tuỳ chọn) Kiểm tra namespace đã được tạo trong AWS Cloud Map. Bạn có thể dùng AWS Management Console hoặc cấu hình AWS CLI thông thường vì namespace này được tạo trong AWS Cloud Map.

   Ví dụ, dùng AWS CLI:

   ```
   aws servicediscovery get-namespace --id ns-EXAMPLE
   ```

   Output:

   ```
   {
       "Namespace": {
           "Id": "ns-EXAMPLE",
           "Arn": "arn:aws:servicediscovery:us-west-2:123456789012:namespace/ns-EXAMPLE",
           "Name": "service-connect",
           "Type": "HTTP",
           "Properties": {
               "DnsProperties": {
                   "SOA": {}
               },
               "HttpProperties": {
                   "HttpName": "service-connect"
               }
           },
           "CreateDate": 1661749852.422,
           "CreatorRequestId": "service-connect"
       }
   }
   ```

**💡 Giải thích thêm**

Diễn biến trạng thái cluster qua hai output:

| Lệnh | `status` | Ghi chú |
|---|---|---|
| `create-cluster` | `PROVISIONING` | Có `attachments` loại `sc` ở `ATTACHING`; `attachmentsStatus`: `UPDATE_IN_PROGRESS` — ECS đang tạo namespace |
| `describe-clusters` (sau đó) | `ACTIVE` | `serviceConnectDefaults.namespace` trỏ tới ARN namespace |

Namespace do ECS tạo (theo output `get-namespace`): tên `service-connect`, loại **`HTTP`**.

---

## 3. Bước 2: Tạo service cho server

**📘 Theo docs AWS**

Tính năng Service Connect nhằm kết nối nhiều ứng dụng trên Amazon ECS với nhau. Ít nhất một trong các ứng dụng đó cần cung cấp một web service để kết nối tới. Ở bước này, bạn tạo:

- **Task definition** dùng container image NGINX chính thức **không chỉnh sửa** và có cấu hình Service Connect.
- **Service definition** Amazon ECS cấu hình Service Connect để cung cấp service discovery và service mesh proxy cho traffic tới service này. Cấu hình này **dùng lại namespace mặc định** từ cấu hình cluster để giảm lượng cấu hình phải viết cho mỗi service.
- **Amazon ECS service**. Nó chạy một task dùng task definition, và chèn thêm một container cho Service Connect proxy. Proxy lắng nghe trên port từ container port mapping của task definition. Trong một ứng dụng client chạy trên Amazon ECS, proxy trong task client lắng nghe kết nối ra tới port name của task definition, service discovery name hoặc service client alias name, và số port từ client alias.

**Tạo web service với Service Connect**

1. Đăng ký một task definition tương thích Fargate và dùng network mode `awsvpc`. Làm theo các bước sau:

   1. Tạo file tên **`service-connect-nginx.json`** với nội dung task definition sau.

      Task definition này cấu hình Service Connect bằng cách thêm tham số **`name`** và **`appProtocol`** vào port mapping. Port name giúp port này dễ nhận diện hơn trong cấu hình service khi dùng nhiều port. Port name cũng được dùng mặc định làm **tên có thể được tìm thấy** cho các ứng dụng khác trong namespace.

      Task definition có **task IAM role** vì service bật **ECS Exec**.

      > **Important:** Task definition này dùng `logConfiguration` để gửi output nginx từ `stdout` và `stderr` tới Amazon CloudWatch Logs. Task execution role này **không có** quyền bổ sung cần để **tạo** log group CloudWatch Logs. Hãy tạo log group trong CloudWatch Logs bằng AWS Management Console hoặc AWS CLI. Nếu không muốn gửi log nginx tới CloudWatch Logs, bạn có thể bỏ `logConfiguration`.

      Thay AWS account id trong task execution role bằng AWS account id của bạn.

      ```
      {
          "family": "service-connect-nginx",
          "executionRoleArn": "arn:aws:iam::123456789012:role/ecsTaskExecutionRole",
          "taskRoleArn": "arn:aws:iam::123456789012:role/ecsTaskRole",
          "networkMode": "awsvpc",
          "containerDefinitions": [
              {
              "name": "webserver",
              "image": "public.ecr.aws/docker/library/nginx:latest",
              "cpu": 100,
              "portMappings": [
                  {
                      "name": "nginx",
                      "containerPort": 80,
                      "protocol": "tcp",
                      "appProtocol": "http"
                  }
              ],
              "essential": true,
              "logConfiguration": {
                  "logDriver": "awslogs",
                  "options": {
                      "awslogs-group": "/ecs/service-connect-nginx",
                      "awslogs-region": "region",
                      "awslogs-stream-prefix": "nginx"
                  }
              }
              }
          ],
          "cpu": "256",
          "memory": "512"
      }
      ```

   2. Đăng ký task definition bằng file `service-connect-nginx.json`:

      ```
      aws ecs register-task-definition --cli-input-json file://service-connect-nginx.json
      ```

2. Tạo service:

   1. Tạo file tên **`service-connect-nginx-service.json`** với nội dung Amazon ECS service bạn đang tạo. Ví dụ này dùng task definition tạo ở bước trước. Cần có **`awsvpcConfiguration`** vì task definition ví dụ dùng network mode `awsvpc`.

      Khi tạo ECS service, chỉ định **Fargate** và platform version **`LATEST`** hỗ trợ Service Connect. `securityGroups` và `subnets` phải thuộc một VPC đáp ứng yêu cầu dùng Amazon ECS. Bạn có thể lấy ID security group và subnet từ Amazon VPC Console.

      Service này cấu hình Service Connect bằng cách thêm tham số **`serviceConnectConfiguration`**. **Không cần** namespace vì cluster đã cấu hình namespace mặc định. Ứng dụng client chạy trên ECS trong namespace kết nối tới service này bằng **`portName`** và port trong **`clientAliases`**. Ví dụ, service này truy cập được qua **`http://nginx:80/`**, vì nginx cung cấp trang chào mừng ở vị trí gốc `/`. Ứng dụng bên ngoài không chạy trên Amazon ECS hoặc không cùng namespace có thể truy cập ứng dụng này **qua Service Connect proxy** bằng **địa chỉ IP của task** và **số port từ task definition**. Với cấu hình **`tls`**, thêm `arn` certificate cho **`awsPcaAuthorityArn`**, **`kmsKey`** của bạn, và **`roleArn`** của IAM role.

      Service này dùng `logConfiguration` để gửi output của Service Connect proxy từ `stdout` và `stderr` tới Amazon CloudWatch Logs. Task execution role này không có quyền bổ sung cần để tạo log group CloudWatch Logs. Hãy tạo log group trong CloudWatch Logs bằng AWS Management Console hoặc AWS CLI. Chúng tôi **khuyến nghị** tạo log group này và lưu log proxy trong CloudWatch Logs. Nếu không muốn gửi log proxy tới CloudWatch Logs, bạn có thể bỏ `logConfiguration`.

      ```
      {
          "cluster": "tutorial",
          "deploymentConfiguration": {
              "maximumPercent": 200,
              "minimumHealthyPercent": 0
          },
          "deploymentController": {
              "type": "ECS"
          },
          "desiredCount": 1,
          "enableECSManagedTags": true,
          "enableExecuteCommand": true,
          "launchType": "FARGATE",
          "networkConfiguration": {
              "awsvpcConfiguration": {
                  "assignPublicIp": "ENABLED",
                  "securityGroups": [
                      "sg-EXAMPLE"
                  ],
                  "subnets": [
                      "subnet-EXAMPLE",
                      "subnet-EXAMPLE",
                      "subnet-EXAMPLE"
                  ]
                 }
          },
          "platformVersion": "LATEST",
          "propagateTags": "SERVICE",
          "serviceName": "service-connect-nginx-service",
          "serviceConnectConfiguration": {
              "enabled": true,
              "services": [
                  {
                      "portName": "nginx",
                      "clientAliases": [
                          {
                              "port": 80
                          }
                      ],
                      "tls": {
                         "issuerCertificateAuthority": {
                            "awsPcaAuthorityArn": "certificateArn"
                         },
                         "kmsKey": "kmsKey",
                         "roleArn": "iamRoleArn"
                      }
                  }
              ],
              "logConfiguration": {
                  "logDriver": "awslogs",
                  "options": {
                      "awslogs-group": "/ecs/service-connect-proxy",
                      "awslogs-region": "region",
                      "awslogs-stream-prefix": "service-connect-proxy"
                  }
              }
          },
          "taskDefinition": "service-connect-nginx"
      }
      ```

   2. Tạo service bằng file `service-connect-nginx-service.json`:

      ```
      aws ecs create-service --cluster tutorial --cli-input-json file://service-connect-nginx-service.json
      ```

      Output:

      ```
      {
          "service": {
              "serviceArn": "arn:aws:ecs:us-west-2:123456789012:service/tutorial/service-connect-nginx-service",
              "serviceName": "service-connect-nginx-service",
              "clusterArn": "arn:aws:ecs:us-west-2:123456789012:cluster/tutorial",
              "loadBalancers": [],
              "serviceRegistries": [],
              "status": "ACTIVE",
              "desiredCount": 1,
              "runningCount": 0,
              "pendingCount": 0,
              "launchType": "FARGATE",
              "platformVersion": "LATEST",
              "platformFamily": "Linux",
              "taskDefinition": "arn:aws:ecs:us-west-2:123456789012:task-definition/service-connect-nginx:1",
              "deploymentConfiguration": {
                  "deploymentCircuitBreaker": {
                      "enable": false,
                      "rollback": false
                  },
                  "maximumPercent": 200,
                  "minimumHealthyPercent": 0
              },
              "deployments": [
                  {
                      "id": "ecs-svc/3763308422771520962",
                      "status": "PRIMARY",
                      "taskDefinition": "arn:aws:ecs:us-west-2:123456789012:task-definition/service-connect-nginx:1",
                      "desiredCount": 1,
                      "pendingCount": 0,
                      "runningCount": 0,
                      "failedTasks": 0,
                      "createdAt": 1661210032.602,
                      "updatedAt": 1661210032.602,
                      "launchType": "FARGATE",
                      "platformVersion": "1.4.0",
                      "platformFamily": "Linux",
                      "networkConfiguration": {
                          "awsvpcConfiguration": {
                              "assignPublicIp": "ENABLED",
                              "securityGroups": [
                                  "sg-EXAMPLE"
                              ],
                              "subnets": [
                                  "subnet-EXAMPLEf",
                                  "subnet-EXAMPLE",
                                  "subnet-EXAMPLE"
                              ]
                          }
                      },
                      "rolloutState": "IN_PROGRESS",
                      "rolloutStateReason": "ECS deployment ecs-svc/3763308422771520962 in progress.",
                      "failedLaunchTaskCount": 0,
                      "replacedTaskCount": 0,
                      "serviceConnectConfiguration": {
                          "enabled": true,
                          "namespace": "service-connect",
                          "services": [
                              {
                                  "portName": "nginx",
                                  "clientAliases": [
                                      {
                                          "port": 80
                                      }
                                  ]
                              }
                          ],
                          "logConfiguration": {
                              "logDriver": "awslogs",
                              "options": {
                                  "awslogs-group": "/ecs/service-connect-proxy",
                                  "awslogs-region": "us-west-2",
                                  "awslogs-stream-prefix": "service-connect-proxy"
                              },
                              "secretOptions": []
                          }
                      },
                      "serviceConnectResources": [
                          {
                              "discoveryName": "nginx",
                              "discoveryArn": "arn:aws:servicediscovery:us-west-2:123456789012:service/srv-EXAMPLE"
                          }
                      ]
                  }
              ],
              "roleArn": "arn:aws:iam::123456789012:role/aws-service-role/ecs.amazonaws.com/AWSServiceRoleForECS",
              "version": 0,
              "events": [],
              "createdAt": 1661210032.602,
              "placementConstraints": [],
              "placementStrategy": [],
              "networkConfiguration": {
                  "awsvpcConfiguration": {
                      "assignPublicIp": "ENABLED",
                      "securityGroups": [
                          "sg-EXAMPLE"
                      ],
                      "subnets": [
                          "subnet-EXAMPLE",
                          "subnet-EXAMPLE",
                          "subnet-EXAMPLE"
                      ]
                  }
              },
              "schedulingStrategy": "REPLICA",
              "enableECSManagedTags": true,
              "propagateTags": "SERVICE",
              "enableExecuteCommand": true
          }
      }
      ```

      `serviceConnectConfiguration` bạn cung cấp xuất hiện bên trong **deployment đầu tiên** của output. Khi bạn thay đổi ECS service theo cách cần thay đổi task, Amazon ECS tạo một **deployment mới**.

**💡 Giải thích thêm**

Phần Service Connect trong **task definition**:

| Trường trong `portMappings` | Giá trị | Vai trò theo trang |
|---|---|---|
| `name` | `nginx` | Port name — dễ nhận diện, và mặc định là tên tìm thấy được trong namespace |
| `containerPort` | `80` | Port proxy lắng nghe |
| `appProtocol` | `http` | Giao thức ứng dụng |

Phần Service Connect trong **service**:

| Trường | Giá trị | Vai trò theo trang |
|---|---|---|
| `enabled` | `true` | Bật Service Connect |
| `namespace` | (không đặt) | Dùng namespace mặc định của cluster |
| `services[].portName` | `nginx` | Khớp `name` của port mapping |
| `services[].clientAliases[].port` | `80` | Port client dùng → `http://nginx:80/` |
| `services[].tls` | `awsPcaAuthorityArn`, `kmsKey`, `roleArn` | Cấu hình TLS (thay bằng giá trị của bạn) |
| `logConfiguration` | `awslogs` → `/ecs/service-connect-proxy` | Log của proxy |

Hai cách truy cập service nginx theo trang:

| Ai | Truy cập bằng |
|---|---|
| Ứng dụng ECS **cùng namespace** | `http://nginx:80/` (portName + port của clientAliases) |
| Ứng dụng ngoài ECS hoặc khác namespace | **IP của task** + **port từ task definition** (qua proxy) |

Hai log group cần **tự tạo trước** (vì task execution role không có quyền tạo):

| Log group | Cho |
|---|---|
| `/ecs/service-connect-nginx` | Log nginx (task definition) |
| `/ecs/service-connect-proxy` | Log Service Connect proxy (service) — trang **khuyến nghị** giữ |

Trong output của `create-service`, `serviceConnectResources` cho thấy ECS đã tạo Cloud Map service với `discoveryName` = `nginx` (lấy từ port name).

---

## 4. Bước 3: Kiểm tra kết nối

**📘 Theo docs AWS**

Để kiểm tra Service Connect đã được cấu hình và hoạt động, làm theo các bước sau để kết nối tới web service từ một **ứng dụng bên ngoài**. Sau đó, xem các metric bổ sung trong CloudWatch mà Service Connect proxy tạo ra.

**Kết nối tới web service từ ứng dụng bên ngoài**

- Kết nối tới địa chỉ IP của task và container port bằng địa chỉ IP của task

  Dùng AWS CLI để lấy task ID, bằng `aws ecs list-tasks --cluster tutorial`.

  Nếu subnet và security group cho phép traffic từ internet công cộng trên port từ task definition, bạn có thể kết nối tới IP public từ máy của mình. Tuy nhiên, IP public **không có** trong `describe-tasks`, nên các bước gồm việc vào AWS Management Console Amazon EC2 hoặc AWS CLI để lấy chi tiết của elastic network interface.

  Trong ví dụ này, một Amazon EC2 instance trong cùng VPC dùng **IP private** của task. Ứng dụng là nginx, nhưng header **`server: envoy`** cho thấy **Service Connect proxy** đang được dùng. Service Connect proxy lắng nghe trên container port từ task definition.

  ```
  $ curl -v 10.0.19.50:80/
  *   Trying 10.0.19.50:80...
  * Connected to 10.0.19.50 (10.0.19.50) port 80 (#0)
  > GET / HTTP/1.1
  > Host: 10.0.19.50
  > User-Agent: curl/7.79.1
  > Accept: */*
  >
  * Mark bundle as not supporting multiuse
  < HTTP/1.1 200 OK
  < server: envoy
  < date: Tue, 23 Aug 2022 03:53:06 GMT
  < content-type: text/html
  < content-length: 612
  < last-modified: Tue, 16 Apr 2019 13:08:19 GMT
  < etag: "5cb5d3c3-264"
  < accept-ranges: bytes
  < x-envoy-upstream-service-time: 0
  <
  <!DOCTYPE html>
  <html>
  <head>
  <title>Welcome to nginx!</title>
  <style>
      body {width: 35em;
          margin: 0 auto;
          font-family: Tahoma, Verdana, Arial, sans-serif;
      }
  </style>
  </head>
  <body>
  <h1>Welcome to nginx!</h1>
  <p>If you see this page, the nginx web server is successfully installed and
  working. Further configuration is required.</p>

      <p>For online documentation and support please refer to
  <a href="http://nginx.org/">nginx.org</a>.<br/>
  Commercial support is available at
  <a href="http://nginx.com/">nginx.com</a>.</p>

      <p><em>Thank you for using nginx.</em></p>
  </body>
  </html>
  ```

**Xem metric của Service Connect**

Service Connect proxy tạo metric ứng dụng (kết nối HTTP, HTTP2, gRPC hoặc TCP) trong CloudWatch metrics. Khi dùng CloudWatch console, xem các **metric dimension** bổ sung **DiscoveryName**, (**DiscoveryName, ServiceName, ClusterName**), **TargetDiscoveryName**, và (**TargetDiscoveryName, ServiceName, ClusterName**) trong namespace **Amazon ECS**. Thông tin thêm về các metric và dimension này, xem *View Available Metrics* (<https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/viewing_metrics_with_cloudwatch.html>) trong Amazon CloudWatch Logs User Guide.

**💡 Giải thích thêm**

Dấu hiệu Service Connect đang hoạt động trong output `curl`:

| Header | Ý nghĩa theo trang |
|---|---|
| `server: envoy` | Request đi qua Service Connect proxy (dù ứng dụng là nginx) |
| `HTTP/1.1 200 OK` + trang "Welcome to nginx!" | Proxy chuyển tới nginx thành công |

Lấy IP để test:

| IP | Cách lấy theo trang |
|---|---|
| Private | Dùng từ EC2 instance cùng VPC (như ví dụ `10.0.19.50`) |
| Public | Không có trong `describe-tasks` → xem elastic network interface qua console/CLI EC2; cần subnet và security group cho phép |

Metric dimension trong namespace Amazon ECS của CloudWatch:

| Dimension |
|---|
| `DiscoveryName` |
| `DiscoveryName, ServiceName, ClusterName` |
| `TargetDiscoveryName` |
| `TargetDiscoveryName, ServiceName, ClusterName` |

---

## 5. Ghi chú về trang gốc

**💡 Giải thích thêm**

Các điểm trên chính trang mà bản dịch giữ nguyên như docs:

| Chỗ | Ghi chú |
|---|---|
| Output `create-cluster` | Có thêm một dấu `}` thừa ở cuối, nên không phải JSON hợp lệ |
| Task definition | Có `taskRoleArn` trỏ tới `ecsTaskRole` (vì bật ECS Exec), nhưng phần điều kiện tiên quyết chỉ nhắc `ecsTaskExecutionRole` |
| File service JSON | Có khối `tls`, nhưng `serviceConnectConfiguration` trong output của `create-service` không hiện khối `tls` |
| Output `create-service` | Một subnet ghi `subnet-EXAMPLEf` (có chữ `f` thừa) |
| `"awslogs-region": "region"` | Giá trị giữ chỗ — thay bằng Region thật |

# Amazon Elastic Container Service on AWS Outposts

> Nguồn: **Amazon ECS Developer Guide — Amazon Elastic Container Service on AWS Outposts**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/using-outposts.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: **bản dịch** sang tiếng Việt, đi đúng thứ tự và đúng nội dung của trang gốc, không thêm, không bớt ý. Code ví dụ giữ nguyên như trên docs.
> - Khối **💡 Giải thích thêm**: chỉ giải thích lại những gì **chính trang này** đã nói, cho dễ hiểu hơn. Không đưa thông tin từ trang khác hay ví dụ tự đặt.
> - Tên dịch vụ, tham số, lệnh giữ nguyên tiếng Anh.
> - Tài liệu AWS được phát hành theo giấy phép **Creative Commons Attribution-ShareAlike 4.0**; bản dịch này ghi rõ nguồn ở trên.

---

## Mục lục

0. [Phần mở đầu](#0-phần-mở-đầu)
1. [Considerations](#1-considerations)
2. [Prerequisites](#2-prerequisites)
3. [Tổng quan tạo cluster trên AWS Outposts](#3-tổng-quan-tạo-cluster-trên-aws-outposts)
4. [Ví dụ tạo cluster trên AWS Outposts](#4-ví-dụ-tạo-cluster-trên-aws-outposts)

---

## 0. Phần mở đầu

**📘 Theo docs AWS**

AWS Outposts mang các dịch vụ, hạ tầng và mô hình vận hành gốc của AWS vào các cơ sở on-premises. Trong môi trường AWS Outposts, bạn có thể dùng cùng các AWS API, công cụ và hạ tầng mà bạn dùng trên AWS Cloud.

Amazon ECS trên AWS Outposts lý tưởng cho workload độ trễ thấp cần chạy gần dữ liệu và ứng dụng on-premises.

Thông tin thêm về AWS Outposts, xem *AWS Outposts User Guide* (<https://docs.aws.amazon.com/outposts/latest/userguide/what-is-outposts.html>).

**💡 Giải thích thêm**

Outposts = hạ tầng AWS đặt **tại cơ sở của bạn**, dùng cùng API và công cụ như trên AWS Cloud. Dùng ECS trên Outposts khi workload cần **độ trễ thấp** và phải chạy **gần dữ liệu/ứng dụng on-premises**.

---

## 1. Considerations

**📘 Theo docs AWS**

Sau đây là những điều cần cân nhắc khi dùng Amazon ECS trên AWS Outposts:

- Amazon Elastic Container Registry, AWS Identity and Access Management và Network Load Balancer chạy trong AWS Region, không chạy trên AWS Outposts. Điều này sẽ làm tăng độ trễ giữa các dịch vụ này và container.
- AWS Fargate không có trên AWS Outposts.

Sau đây là những điều cần cân nhắc về kết nối mạng cho AWS Outposts:

- Nếu mất kết nối mạng giữa AWS Outposts và AWS Region của nó, cluster của bạn vẫn tiếp tục chạy. Tuy nhiên, bạn không thể tạo cluster mới hoặc thực hiện hành động mới trên cluster hiện có cho tới khi kết nối được khôi phục. Nếu instance bị lỗi, instance sẽ không được thay thế tự động. CloudWatch Logs agent sẽ không thể cập nhật log và dữ liệu sự kiện.
- Chúng tôi khuyến nghị bạn cung cấp kết nối tin cậy, sẵn sàng cao và độ trễ thấp giữa AWS Outposts và AWS Region của nó.

**💡 Giải thích thêm**

Cái gì chạy ở đâu, theo đoạn trên:

| Ở AWS Region | Ở AWS Outposts | Không có |
|---|---|---|
| Amazon ECR, IAM, Network Load Balancer | Container (chạy trên EC2 container instance của Outpost) | AWS Fargate |

Khi **mất kết nối** Outpost ↔ Region:

| Vẫn hoạt động | Không làm được |
|---|---|
| Cluster đang chạy tiếp tục chạy | Tạo cluster mới |
| | Hành động mới trên cluster hiện có |
| | Tự thay instance khi instance lỗi |
| | CloudWatch Logs agent cập nhật log và sự kiện |

Vì vậy trang khuyến nghị kết nối tới Region phải **tin cậy, sẵn sàng cao, độ trễ thấp**.

---

## 2. Prerequisites

**📘 Theo docs AWS**

Sau đây là điều kiện tiên quyết để dùng Amazon ECS trên AWS Outposts:

- Bạn phải đã cài đặt và cấu hình một Outpost trong trung tâm dữ liệu on-premises của mình.
- Bạn phải có kết nối mạng tin cậy giữa Outpost và AWS Region của nó.

---

## 3. Tổng quan tạo cluster trên AWS Outposts

**📘 Theo docs AWS**

Sau đây là tổng quan cấu hình:

1. Tạo role và policy có quyền trên AWS Outposts.
2. Tạo IAM instance profile có quyền trên AWS Outposts.
3. Tạo VPC, hoặc dùng VPC có sẵn nằm trong cùng Region với AWS Outposts.
4. Tạo subnet hoặc dùng subnet có sẵn được gắn với AWS Outposts.

   Đây là subnet nơi container instance chạy.

5. Tạo security group cho container instance trong cluster.
6. Tạo Amazon ECS cluster.
7. Định nghĩa biến môi trường của Amazon ECS container agent để launch instance vào cluster.
8. Chạy container.

Thông tin chi tiết về cách tích hợp Amazon ECS với AWS Outposts, xem *Extend Amazon ECS across two AWS Outposts racks* (<https://community.aws/content/2k5wK9P1oSC9I4ZzuSLWynsiJaa/extend-amazon-ecs-across-two-outposts-racks>).

**💡 Giải thích thêm**

```
IAM: role + policy → instance profile
Mạng: VPC (cùng Region với Outpost) → subnet gắn với Outpost → security group
ECS: tạo cluster → cấu hình agent (biến môi trường) → launch instance → chạy container
```

Subnet ở bước 4 là điểm then chốt: container instance chạy trong subnet **gắn với Outpost**, nên container chạy trên Outpost.

---

## 4. Ví dụ tạo cluster trên AWS Outposts

**📘 Theo docs AWS**

Ví dụ sau tạo một Amazon ECS cluster trên AWS Outposts.

**1. Tạo role và policy có quyền trên AWS Outposts.**

File `role-policy.json` là tài liệu policy chứa effect và action cho tài nguyên. Thông tin về định dạng file, xem *PutRolePolicy* (<https://docs.aws.amazon.com/IAM/latest/APIReference/API_PutRolePolicy.html>) trong *IAM API Reference*.

```
aws iam create-role –-role-name ecsRole \
    --assume-role-policy-document file://ecs-policy.json
aws iam put-role-policy --role-name ecsRole --policy-name ecsRolePolicy \
    --policy-document file://role-policy.json
```

**2. Tạo IAM instance profile có quyền trên AWS Outposts.**

```
aws iam create-instance-profile --instance-profile-name outpost
aws iam add-role-to-instance-profile --instance-profile-name outpost \
    --role-name ecsRole
```

**3. Tạo VPC.**

```
aws ec2 create-vpc --cidr-block 10.0.0.0/16
```

**4. Tạo subnet gắn với AWS Outposts.**

```
aws ec2 create-subnet \
    --cidr-block 10.0.3.0/24 \
    --vpc-id vpc-xxxxxxxx \
    --outpost-arn arn:aws:outposts:us-west-2:123456789012:outpost/op-xxxxxxxxxxxxxxxx \
    --availability-zone-id usw2-az1
```

**5. Tạo security group cho container instance, chỉ định dải CIDR phù hợp cho AWS Outposts.** (Bước này khác biệt với AWS Outposts.)

```
aws ec2 create-security-group --group-name MyOutpostSG
aws ec2 authorize-security-group-ingress --group-name MyOutpostSG --protocol tcp \
    --port 22 --cidr 10.0.3.0/24
aws ec2 authorize-security-group-ingress --group-name MyOutpostSG --protocol tcp \
    --port 80 --cidr 10.0.3.0/24
```

**6. Tạo cluster.**

**7. Định nghĩa biến môi trường của Amazon ECS container agent** để launch instance vào cluster đã tạo ở bước trước, và định nghĩa các tag bạn muốn thêm để dễ nhận diện cluster (ví dụ `Outpost` để cho biết cluster dành cho một Outpost).

```
#! /bin/bash
cat << ‘EOF’ >> /etc/ecs/ecs.config
ECS_CLUSTER=MyCluster
ECS_IMAGE_PULL_BEHAVIOR=prefer-cached
ECS_CONTAINER_INSTANCE_TAGS={“environment”: ”Outpost”}
EOF
```

> **Note:** Để tránh độ trễ do pull container image từ Amazon ECR ở Region, hãy dùng image cache. Để làm vậy, mỗi lần chạy task, cấu hình Amazon ECS agent mặc định dùng image đã cache trên chính instance, bằng cách đặt `ECS_IMAGE_PULL_BEHAVIOR` thành `prefer-cached`.

**8. Tạo container instance**, chỉ định VPC và subnet cho AWS Outposts nơi instance sẽ chạy, và một instance type có sẵn trên AWS Outposts. (Bước này khác biệt với AWS Outposts.)

File `userdata.txt` chứa user data mà instance có thể dùng để thực hiện các tác vụ cấu hình tự động thông thường và thậm chí chạy script sau khi instance khởi động. Thông tin về file cho lời gọi API, xem *Run commands on your Linux instance at launch* (<https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/user-data.html>) trong *Amazon EC2 User Guide*.

```
aws ec2 run-instances --count 1 --image-id ami-xxxxxxxx --instance-type c5.large \
    --key-name aws-outpost-key –-subnet-id subnet-xxxxxxxxxxxxxxxxx \
    --iam-instance-profile Name outpost --security-group-id sg-xxxxxx \
    --associate-public-ip-address --user-data file://userdata.txt
```

> **Note:** Lệnh này cũng dùng khi thêm instance vào cluster. Mọi container deploy trong cluster sẽ được đặt trên chính AWS Outposts đó.

**💡 Giải thích thêm**

**Các bước "khác biệt với Outposts"** theo trang:

| Bước | Điểm riêng của Outposts |
|---|---|
| 4. Tạo subnet | Có `--outpost-arn` — subnet được gắn với Outpost |
| 5. Security group | Chỉ định dải CIDR phù hợp cho Outpost (ví dụ cho phép port 22 và 80 từ `10.0.3.0/24` — chính là CIDR của subnet ở bước 4) |
| 8. Tạo instance | Dùng subnet của Outpost và instance type **có sẵn trên Outpost** |

**Biến cấu hình agent ở bước 7:**

| Biến | Ý nghĩa trong ví dụ |
|---|---|
| `ECS_CLUSTER=MyCluster` | Instance đăng ký vào cluster `MyCluster` |
| `ECS_IMAGE_PULL_BEHAVIOR=prefer-cached` | Ưu tiên image đã cache trên instance, tránh độ trễ pull từ ECR ở Region (theo Note) |
| `ECS_CONTAINER_INSTANCE_TAGS` | Gắn tag `environment = Outpost` cho container instance để nhận diện |

Đoạn user data này chính là nội dung dùng cho file `userdata.txt` ở bước 8.

**Lưu ý khi chép code từ trang gốc** — một số ký tự trên trang không phải ký tự ASCII chuẩn, chạy nguyên văn có thể lỗi:

| Bước | Trên trang | Nên là |
|---|---|---|
| 1 | `–-role-name` (gạch dài) | `--role-name` |
| 8 | `–-subnet-id` (gạch dài) | `--subnet-id` |
| 7 | `‘EOF’`, `“environment”: ”Outpost”` (nháy cong) | `'EOF'`, `"environment": "Outpost"` |
| 8 | `--iam-instance-profile Name outpost` | `--iam-instance-profile Name=outpost` |

Ngoài ra, ở bước 1 phần mô tả nói về file `role-policy.json`, còn lệnh `create-role` dùng file `ecs-policy.json` cho trust policy — hai file khác nhau. Bước 6 (tạo cluster) trên trang không có lệnh kèm theo.

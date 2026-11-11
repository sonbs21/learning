# Tạo IAM user và Amazon SQS queue (Creating IAM users and Amazon SQS queues)

> Nguồn: [Creating IAM users and Amazon SQS queues](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-abac-creating-queues.html)

Các ví dụ sau giải thích cách tạo một ABAC policy để kiểm soát quyền truy cập vào Amazon SQS bằng AWS Management Console và CloudFormation.

## Sử dụng AWS Management Console (Using the AWS Management Console)

**Tạo một IAM user (Create an IAM user)**

1. Đăng nhập vào AWS Management Console và mở IAM console tại [https://console.aws.amazon.com/iam/](https://console.aws.amazon.com/iam/).
2. Chọn **User** từ khung điều hướng bên trái (navigation pane).
3. Chọn **Add Users** và nhập một tên vào ô văn bản **User name**.
4. Chọn ô **Access key - Programmatic access** và chọn **Next:Permissions**.
5. Chọn **Next:Tags**.
6. Thêm tag key là `environment` và tag value là `beta`.
7. Chọn **Next:Review** rồi chọn **Create user**.
8. Sao chép và lưu trữ access key ID và secret access key ở một nơi an toàn.

**Thêm permission cho IAM user (Add IAM user permissions)**

1. Chọn IAM user mà bạn đã tạo.
2. Chọn **Add inline policy**.
3. Trên tab JSON, dán policy sau:

   > Ghi chú của người dịch: khối policy JSON của bước này không xuất hiện trong văn bản trang tải về. Nhiều khả năng đó chính là nội dung `PolicyDocument` trong template CloudFormation ở phần dưới — hãy đối chiếu với trang gốc.

4. Chọn **Review policy**.
5. Chọn **Create policy**.

## Sử dụng AWS CloudFormation (Using AWS CloudFormation)

Sử dụng template CloudFormation mẫu sau để tạo một IAM user có gắn kèm một inline policy và một Amazon SQS queue:

```yaml
AWSTemplateFormatVersion: "2010-09-09"
Description: "CloudFormation template to create IAM user with custom inline policy"
Resources:
    IAMPolicy:
        Type: "AWS::IAM::Policy"
        Properties:
            PolicyDocument: |
                {
                    "Version": "2012-10-17",
                    "Statement": [
                        {
                            "Sid": "AllowAccessForSameResTag",
                            "Effect": "Allow",
                            "Action": [
                                "sqs:SendMessage",
                                "sqs:ReceiveMessage",
                                "sqs:DeleteMessage"
                            ],
                            "Resource": "*",
                            "Condition": {
                                "StringEquals": {
                                    "aws:ResourceTag/environment": "${aws:PrincipalTag/environment}"
                                }
                            }
                        },
                        {
                            "Sid": "AllowAccessForSameReqTag",
                            "Effect": "Allow",
                            "Action": [
                                "sqs:CreateQueue",
                                "sqs:DeleteQueue",
                                "sqs:SetQueueAttributes",
                                "sqs:tagqueue"
                            ],
                            "Resource": "*",
                            "Condition": {
                                "StringEquals": {
                                    "aws:RequestTag/environment": "${aws:PrincipalTag/environment}"
                                }
                            }
                        },
                        {
                            "Sid": "DenyAccessForProd",
                            "Effect": "Deny",
                            "Action": "sqs:*",
                            "Resource": "*",
                            "Condition": {
                                "StringEquals": {
                                    "aws:ResourceTag/stage": "prod"
                                }
                            }
                        }
                    ]
                }

            Users:
              - "testUser"
            PolicyName: tagQueuePolicy

    IAMUser:
        Type: "AWS::IAM::User"
        Properties:
            Path: "/"
            UserName: "testUser"
            Tags:
              -
                Key: "environment"
                Value: "beta"
```

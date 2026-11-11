# Gắn tag để kiểm soát truy cập trong Amazon SQS (Tagging for access control in Amazon SQS)

> Nguồn: [Tagging for access control in Amazon SQS](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-abac-tagging-resource-control.html)

Sau đây là một ví dụ về việc sử dụng tag để kiểm soát truy cập trong Amazon SQS. IAM policy giới hạn một IAM user được thực hiện tất cả các action của Amazon SQS trên tất cả các queue có chứa một resource tag với key là environment và value là production. Để biết thêm thông tin, xem [Attribute-based access control with tags and AWS Organizations](https://docs.aws.amazon.com/organizations/latest/userguide/orgs_tagging_abac.html).

```json
{
  "Version":"2012-10-17",
  "Statement": [
    {
      "Sid": "AllowAccessForProd",
      "Effect": "Allow",
      "Action": "sqs:*",
      "Resource": "*",
      "Condition": {
        "StringEquals": {
          "aws:ResourceTag/environment": "prod"
        }
      }
    }
  ]
}
```

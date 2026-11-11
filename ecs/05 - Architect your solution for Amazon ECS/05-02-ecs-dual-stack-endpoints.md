# Using Amazon ECS dual-stack endpoints

> Nguồn: **Amazon ECS Developer Guide — Using Amazon ECS dual-stack endpoints**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/dual-stack-endpoint.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: **bản dịch** sang tiếng Việt, đi đúng thứ tự và đúng nội dung của trang gốc, không thêm, không bớt ý. Code ví dụ giữ nguyên như trên docs.
> - Khối **💡 Giải thích thêm**: chỉ giải thích lại những gì **chính trang này** đã nói, cho dễ hiểu hơn. Không đưa thông tin từ trang khác hay ví dụ tự đặt.
> - Tên tham số, cấu hình, lệnh giữ nguyên tiếng Anh.
> - Tài liệu AWS được phát hành theo giấy phép **Creative Commons Attribution-ShareAlike 4.0**; bản dịch này ghi rõ nguồn ở trên.

---

## Mục lục

0. [Phần mở đầu](#0-phần-mở-đầu)
1. [Dùng dual-stack endpoint từ AWS CLI](#1-dùng-dual-stack-endpoint-từ-aws-cli)
2. [Dùng dual-stack endpoint từ AWS SDK](#2-dùng-dual-stack-endpoint-từ-aws-sdk)
3. [Dùng dual-stack endpoint từ REST API](#3-dùng-dual-stack-endpoint-từ-rest-api)

---

## 0. Phần mở đầu

**📘 Theo docs AWS**

Dual-stack endpoint của Amazon ECS hỗ trợ request tới Amazon ECS qua Internet Protocol version 4 (IPv4) và Internet Protocol version 6 (IPv6). Danh sách mọi endpoint của Amazon ECS, xem *Amazon ECS endpoints and quotas* (<https://docs.aws.amazon.com/general/latest/gr/ecs-service.html>) trong AWS General Reference.

Khi dùng REST API, bạn truy cập trực tiếp một endpoint Amazon ECS bằng tên endpoint (URI). Amazon ECS chỉ hỗ trợ tên dual-stack endpoint **theo Region**, nghĩa là bạn phải chỉ định Region như một phần của tên.

Dùng quy ước đặt tên sau cho dual-stack endpoint: `ecs.region.api.aws`.

Khi dùng AWS Command Line Interface (AWS CLI) và AWS SDK, bạn có thể dùng một tham số hoặc một cờ để chuyển sang dual-stack endpoint. Bạn cũng có thể chỉ định trực tiếp dual-stack endpoint để ghi đè endpoint Amazon ECS trong file cấu hình.

Các phần sau mô tả cách dùng dual-stack endpoint từ AWS CLI, AWS SDK và REST API.

**💡 Giải thích thêm**

| | Nội dung theo đoạn trên |
|---|---|
| Hỗ trợ | Request qua IPv4 **và** IPv6 |
| Dạng tên | `ecs.<region>.api.aws` — luôn có Region trong tên |
| Cách dùng | REST API: gọi thẳng URI; CLI/SDK: tham số hoặc cờ, hoặc ghi đè endpoint trong file cấu hình |

Ví dụ theo quy ước tên, Region `us-east-1` thành `ecs.us-east-1.api.aws` (đúng như ví dụ ở mục 2 và 3).

---

## 1. Dùng dual-stack endpoint từ AWS CLI

**📘 Theo docs AWS**

Phần này đưa ra ví dụ lệnh AWS CLI dùng để gửi request tới dual-stack endpoint. Thông tin về cài đặt AWS CLI hoặc cập nhật lên phiên bản mới nhất, xem *Installing or updating to the latest version of the AWS CLI* (<https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html>) trong *AWS Command Line Interface User Guide for Version 2*.

Để dùng dual-stack endpoint, bạn có thể đặt giá trị cấu hình `use_dualstack_endpoint` thành `true` trong file `config` của AWS CLI, để mọi request Amazon ECS do lệnh AWS CLI `ecs` tạo ra đều đi tới dual-stack endpoint của Region đã chỉ định. Bạn có thể chỉ định Region trong file `config` hoặc trong lệnh bằng tuỳ chọn `--region`. Thông tin về file cấu hình của AWS CLI, xem *Configuration and credential file settings in the AWS CLI* (<https://docs.aws.amazon.com/cli/latest/userguide/cli-configure-files.html>) trong *AWS Command Line Interface User Guide for Version 2*.

Nếu muốn dùng dual-stack endpoint cho một số lệnh AWS CLI cụ thể, bạn có thể dùng một trong các cách sau:

- Dùng dual-stack endpoint cho từng lệnh bằng cách đặt tham số `--endpoint-url` thành `https://ecs.aws-region.api.aws` hoặc `http://ecs.aws-region.api.aws` cho bất kỳ lệnh `ecs` nào.

  Lệnh ví dụ sau liệt kê mọi cluster có sẵn và dùng dual-stack endpoint cho request.

  ```
  $ aws ecs list-clusters --endpoint-url https://ecs.aws-region.api.aws
  ```

- Thiết lập các profile riêng trong file AWS Config. Ví dụ, tạo một profile đặt `use_dualstack_endpoint` thành `true` và một profile không đặt `use_dualstack_endpoint`. Khi chạy lệnh, chỉ định profile muốn dùng, tuỳ theo bạn có muốn dùng dual-stack endpoint hay không.

**💡 Giải thích thêm**

Ba cách trên CLI theo trang:

| Cách | Phạm vi | Thiết lập |
|---|---|---|
| `use_dualstack_endpoint = true` trong file `config` | **Mọi** lệnh `ecs` (theo Region trong `config` hoặc `--region`) | File cấu hình |
| `--endpoint-url https://ecs.aws-region.api.aws` | **Từng lệnh** | Tham số trên lệnh |
| Hai profile (một có `use_dualstack_endpoint`, một không) | Theo profile chọn khi chạy | File cấu hình + chọn profile |

Trong `https://ecs.aws-region.api.aws`, phần `aws-region` là chỗ thay bằng mã Region thật.

---

## 2. Dùng dual-stack endpoint từ AWS SDK

**📘 Theo docs AWS**

Phần này đưa ra ví dụ cách truy cập dual-stack endpoint bằng AWS SDK.

**AWS SDK for Java 2.x**

Ví dụ sau cho thấy cách chỉ định dual-stack endpoint cho Region `us-east-1` bằng AWS SDK for Java 2.x.

```java
Region region = Region.US_EAST_1
EcsClient client = EcsClient.builder().region(region).dualstackEnabled(true).build();
```

**AWS SDK for Go**

Ví dụ sau cho thấy cách chỉ định dual-stack endpoint cho Region `us-east-1` bằng AWS SDK for Go.

```go
sess := session.Must(session.NewSession())
svc := ecs.New(sess, &aws.Config{
    Region: aws.String(endpoints.UsEast1RegionID),
    Endpoint: aws.String("https://ecs.us-east-1.api.aws")
})
```

Thông tin thêm, xem *Dual-stack and FIPS endpoints* (<https://docs.aws.amazon.com/sdkref/latest/guide/feature-endpoints.html>) trong *AWS SDKs and Tools Reference Guide*.

**💡 Giải thích thêm**

Hai ví dụ dùng hai kiểu khác nhau — đúng với hai cách nêu ở phần mở đầu ("dùng tham số/cờ" và "chỉ định trực tiếp endpoint"):

| SDK | Cách trong ví dụ |
|---|---|
| Java 2.x | **Bật cờ** `dualstackEnabled(true)` trên client builder; SDK tự chọn dual-stack endpoint theo Region |
| Go | **Ghi đè endpoint** trực tiếp bằng `Endpoint: "https://ecs.us-east-1.api.aws"` |

Dòng đầu của ví dụ Java trên trang gốc thiếu dấu chấm phẩy (`;`) ở cuối — giữ nguyên như docs ở trên; khi dùng thật cần thêm vào.

---

## 3. Dùng dual-stack endpoint từ REST API

**📘 Theo docs AWS**

Khi dùng REST API, bạn có thể truy cập trực tiếp dual-stack endpoint bằng cách chỉ định nó trong request. Ví dụ sau dùng dual-stack endpoint để liệt kê mọi Amazon ECS cluster trong Region `us-east-1`.

```http
POST / HTTP/1.1
Host: ecs.us-east-1.api.aws
Accept-Encoding: identity
Content-Length: 2
X-Amz-Target: AmazonEC2ContainerServiceV20141113.ListClusters
X-Amz-Date: 20150429T170621Z
Content-Type: application/x-amz-json-1.1
Authorization: AUTHPARAMS

{}
```

**💡 Giải thích thêm**

Các phần chính của request ví dụ:

| Phần | Ý nghĩa trong ví dụ |
|---|---|
| `Host: ecs.us-east-1.api.aws` | Chính là dual-stack endpoint, theo quy ước `ecs.region.api.aws` |
| `X-Amz-Target: …ListClusters` | Hành động được gọi — liệt kê cluster |
| `Authorization: AUTHPARAMS` | Chỗ đặt chữ ký xác thực (giá trị giữ chỗ trong ví dụ) |
| Body `{}` | Không truyền tham số nào |

Theo câu mở đầu của mục này, việc dùng dual-stack endpoint với REST API là **chỉ định endpoint đó trong request** — trong ví dụ, thể hiện ở header `Host`.

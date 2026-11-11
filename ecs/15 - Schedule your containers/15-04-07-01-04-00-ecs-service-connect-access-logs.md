# Amazon ECS Service Connect access logs

> Nguồn: **Amazon ECS Developer Guide — Amazon ECS Service Connect access logs**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-connect-envoy-access-logs.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: **bản dịch** sang tiếng Việt, đi đúng thứ tự và đúng nội dung của trang gốc, không thêm, không bớt ý. Cấu hình và định dạng log giữ nguyên như trên docs.
> - Khối **💡 Giải thích thêm**: chỉ giải thích lại những gì **chính trang này** đã nói, cho dễ hiểu hơn. Không đưa thông tin từ trang khác hay ví dụ tự đặt.
> - Tên tham số, command operator, log driver giữ nguyên tiếng Anh.
> - Tài liệu AWS được phát hành theo giấy phép **Creative Commons Attribution-ShareAlike 4.0**; bản dịch này ghi rõ nguồn ở trên.

---

## Mục lục

0. [Phần mở đầu và cách bật](#0-phần-mở-đầu-và-cách-bật)
1. [Những điều cần cân nhắc](#1-những-điều-cần-cân-nhắc)
2. [Định dạng access log](#2-định-dạng-access-log)
   - [2.1 HTTP](#21-http-access-logs)
   - [2.2 HTTP2](#22-http2-access-logs)
   - [2.3 gRPC](#23-grpc-access-logs)
   - [2.4 TCP](#24-tcp-access-logs)
3. [So sánh các command operator theo giao thức](#3-so-sánh-các-command-operator-theo-giao-thức)

---

## 0. Phần mở đầu và cách bật

**📘 Theo docs AWS**

Amazon ECS Service Connect hỗ trợ **access log** để cung cấp telemetry chi tiết về **từng request** được Service Connect proxy xử lý. Access log bổ sung cho application log hiện có bằng cách ghi lại metadata traffic **theo từng request** như HTTP method, path, mã phản hồi, flag và thông tin thời gian. Điều này cho phép quan sát sâu hơn các mẫu traffic ở mức request và tương tác giữa các service, giúp xử lý sự cố và giám sát hiệu quả.

Để bật access log, chỉ định **cả hai** object **`logConfiguration`** và **`accessLogConfiguration`** trong object **`serviceConnectConfiguration`**. Bạn có thể cấu hình **định dạng** log và việc log có **gồm query parameter** hay không trong `accessLogConfiguration`. Log được gửi tới log group đích bởi log driver chỉ định trong `logConfiguration`.

```json
{
    "serviceConnectConfiguration": {
        "enabled": true,
        "namespace": "myapp.namespace",
        "services": [
            ...
        ],
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
    }
}
```

**💡 Giải thích thêm**

Hai object cần có trong `serviceConnectConfiguration`:

| Object | Vai trò | Trong ví dụ |
|---|---|---|
| `logConfiguration` | **Gửi log đi đâu** (log driver + đích) | `awslogs` → log group `my-envoy-log-group`, Region `us-west-2`, prefix `myapp-envoy-logs` |
| `accessLogConfiguration` | **Access log trông thế nào** | `format`: `TEXT`; `includeQueryParameters`: `ENABLED` |

Thiếu một trong hai thì không bật được access log (theo câu "chỉ định cả hai").

---

## 1. Những điều cần cân nhắc

**📘 Theo docs AWS**

Cân nhắc các điểm sau khi bật access log:

- Access log và application log đều được ghi ra **`/dev/stdout`**. Để **tách** access log khỏi application log, chúng tôi khuyến nghị dùng log driver **`awsfirelens`** với cấu hình **Fluent Bit** hoặc **Fluentd** tuỳ chỉnh.
- Chúng tôi khuyến nghị dùng log driver **`awslogs`** để gửi application log và access log tới **cùng một đích** CloudWatch.
- Access log được hỗ trợ trên service Fargate dùng **platform version `1.4.0` trở lên**.
- Query parameter như request id và token **mặc định bị loại** khỏi access log. Để đưa query parameter vào access log, đặt **`includeQueryParameters`** thành **`"ENABLED"`**.

**💡 Giải thích thêm**

| Muốn | Log driver khuyến nghị |
|---|---|
| Access log và application log ở **cùng** đích CloudWatch | `awslogs` |
| **Tách** access log khỏi application log | `awsfirelens` + Fluent Bit / Fluentd tuỳ chỉnh |

Lý do cần tách: cả hai cùng ghi ra `/dev/stdout`.

| `includeQueryParameters` | Kết quả |
|---|---|
| Không đặt (mặc định) | Query parameter (vd request id, token) **bị loại** |
| `"ENABLED"` | Query parameter **có** trong access log |

Yêu cầu Fargate: platform version ≥ `1.4.0`.

---

## 2. Định dạng access log

**📘 Theo docs AWS**

Access log có thể định dạng dưới dạng **dictionary JSON** hoặc **chuỗi Text**, với khác biệt về command operator được hỗ trợ cho từng loại access log.

### 2.1 HTTP access logs

**📘 Theo docs AWS**

Các command operator sau mặc định có trong log HTTP:

Text
```
[%START_TIME%] "%REQ(:METHOD)% %REQ(X-ENVOY-ORIGINAL-PATH?:PATH)% %PROTOCOL%"
%RESPONSE_CODE% %BYTES_RECEIVED% %BYTES_SENT% %DURATION%
%RESP(X-ENVOY-UPSTREAM-SERVICE-TIME)% "%REQ(X-FORWARDED-FOR)%" "%REQ(USER-AGENT)%"
"%REQ(X-REQUEST-ID)%" "%REQ(:AUTHORITY)%" "%UPSTREAM_HOST%"\n
```

JSON
```json
{
  "start_time": "%START_TIME%",
  "method": "%REQ(:METHOD)%",
  "path": "%REQ(X-ENVOY-ORIGINAL-PATH?:PATH)%",
  "protocol": "%PROTOCOL%",
  "response_code": "%RESPONSE_CODE%",
  "bytes_received": "%BYTES_RECEIVED%",
  "bytes_sent": "%BYTES_SENT%",
  "duration_ms": "%DURATION%",
  "upstream_service_time": "%RESP(X-ENVOY-UPSTREAM-SERVICE-TIME)%",
  "forwarded_for": "%REQ(X-FORWARDED-FOR)%",
  "user_agent": "%REQ(USER-AGENT)%",
  "request_id": "%REQ(X-REQUEST-ID)%",
  "authority": "%REQ(:AUTHORITY)%",
  "upstream_host": "%UPSTREAM_HOST%"
}
```

### 2.2 HTTP2 access logs

**📘 Theo docs AWS**

Ngoài các command operator có trong log HTTP, log HTTP2 mặc định có thêm operator **`%STREAM_ID%`**.

Text
```
[%START_TIME%] "%REQ(:METHOD)% %REQ(X-ENVOY-ORIGINAL-PATH?:PATH)% %PROTOCOL%"
%RESPONSE_CODE% %BYTES_RECEIVED% %BYTES_SENT% %DURATION%
%RESP(X-ENVOY-UPSTREAM-SERVICE-TIME)% "%REQ(X-FORWARDED-FOR)%" "%REQ(USER-AGENT)%"
"%REQ(X-REQUEST-ID)%" "%REQ(:AUTHORITY)%" "%UPSTREAM_HOST%" "%STREAM_ID%"\n
```

JSON
```json
{
  "start_time": "%START_TIME%",
  "method": "%REQ(:METHOD)%",
  "path": "%REQ(X-ENVOY-ORIGINAL-PATH?:PATH)%",
  "protocol": "%PROTOCOL%",
  "response_code": "%RESPONSE_CODE%",
  "bytes_received": "%BYTES_RECEIVED%",
  "bytes_sent": "%BYTES_SENT%",
  "duration": "%DURATION%",
  "upstream_service_time": "%RESP(X-ENVOY-UPSTREAM-SERVICE-TIME)%",
  "forwarded_for": "%REQ(X-FORWARDED-FOR)%",
  "user_agent": "%REQ(USER-AGENT)%",
  "request_id": "%REQ(X-REQUEST-ID)%",
  "authority": "%REQ(:AUTHORITY)%",
  "upstream_host": "%UPSTREAM_HOST%",
  "stream_id": "%STREAM_ID%"
}
```

### 2.3 gRPC access logs

**📘 Theo docs AWS**

Ngoài các command operator có trong log HTTP, access log gRPC mặc định có thêm operator **`%STREAM_ID%`** và **`%GRPC_STATUS()%`**.

Text
```
[%START_TIME%] "%REQ(:METHOD)% %REQ(X-ENVOY-ORIGINAL-PATH?:PATH)% %PROTOCOL%"
%RESPONSE_CODE% %GRPC_STATUS()% %BYTES_RECEIVED% %BYTES_SENT% %DURATION%
%RESP(X-ENVOY-UPSTREAM-SERVICE-TIME)% "%REQ(X-FORWARDED-FOR)%" "%REQ(USER-AGENT)%"
"%REQ(X-REQUEST-ID)%" "%REQ(:AUTHORITY)%" "%UPSTREAM_HOST%" "%STREAM_ID%"\n
```

JSON
```json
{
  "start_time": "%START_TIME%",
  "method": "%REQ(:METHOD)%",
  "path": "%REQ(X-ENVOY-ORIGINAL-PATH?:PATH)%",
  "protocol": "%PROTOCOL%",
  "response_code": "%RESPONSE_CODE%",
  "grpc_status": "%GRPC_STATUS()%",
  "bytes_received": "%BYTES_RECEIVED%",
  "bytes_sent": "%BYTES_SENT%",
  "duration": "%DURATION%",
  "upstream_service_time": "%RESP(X-ENVOY-UPSTREAM-SERVICE-TIME)%",
  "forwarded_for": "%REQ(X-FORWARDED-FOR)%",
  "user_agent": "%REQ(USER-AGENT)%",
  "request_id": "%REQ(X-REQUEST-ID)%",
  "authority": "%REQ(:AUTHORITY)%",
  "upstream_host": "%UPSTREAM_HOST%",
  "stream_id": "%STREAM_ID%"
}
```

### 2.4 TCP access logs

**📘 Theo docs AWS**

Các command operator sau mặc định có trong access log TCP:

Text
```
[%START_TIME%] %DOWNSTREAM_REMOTE_ADDRESS% %DOWNSTREAM_REMOTE_PORT%
%BYTES_RECEIVED% %BYTES_SENT% %DURATION%  
%CONNECTION_TERMINATION_DETAILS% %CONNECTION_ID%\n
```

JSON
```
{
  "start_time": "%START_TIME%",
  "downstream_remote_address": "%DOWNSTREAM_REMOTE_ADDRESS%",
  "downstream_remote_port": "%DOWNSTREAM_REMOTE_PORT%",s
  "bytes_received": "%BYTES_RECEIVED%",
  "bytes_sent": "%BYTES_SENT%",
  "duration": "%DURATION%",
  "connection_termination_details": "%CONNECTION_TERMINATION_DETAILS%",
  "connection_id": %CONNECTION_ID%
}
```

Thông tin thêm về các command operator này, xem *Command Operators* (<https://www.envoyproxy.io/docs/envoy/latest/configuration/observability/access_log/usage#command-operators>) trong tài liệu Envoy.

**💡 Giải thích thêm**

Hai lỗi trong mẫu JSON của TCP trên trang gốc (giữ nguyên ở trên như docs):
- Sau dòng `"downstream_remote_port"` có ký tự thừa **`s`** sau dấu phẩy.
- Giá trị `"connection_id": %CONNECTION_ID%` **không có dấu ngoặc kép**, khác các trường còn lại.

Ngoài ra, tên trường thời gian trong JSON khác nhau giữa các mẫu: HTTP dùng **`duration_ms`**, còn HTTP2, gRPC và TCP dùng **`duration`**.

---

## 3. So sánh các command operator theo giao thức

**💡 Giải thích thêm**

Tổng hợp từ các mẫu mặc định ở mục 2:

| Command operator | Ý nghĩa (theo tên trường JSON trên trang) | HTTP | HTTP2 | gRPC | TCP |
|---|---|---|---|---|---|
| `%START_TIME%` | `start_time` | ✅ | ✅ | ✅ | ✅ |
| `%REQ(:METHOD)%` | `method` | ✅ | ✅ | ✅ | — |
| `%REQ(X-ENVOY-ORIGINAL-PATH?:PATH)%` | `path` | ✅ | ✅ | ✅ | — |
| `%PROTOCOL%` | `protocol` | ✅ | ✅ | ✅ | — |
| `%RESPONSE_CODE%` | `response_code` | ✅ | ✅ | ✅ | — |
| `%GRPC_STATUS()%` | `grpc_status` | — | — | ✅ | — |
| `%BYTES_RECEIVED%` | `bytes_received` | ✅ | ✅ | ✅ | ✅ |
| `%BYTES_SENT%` | `bytes_sent` | ✅ | ✅ | ✅ | ✅ |
| `%DURATION%` | `duration_ms` (HTTP) / `duration` | ✅ | ✅ | ✅ | ✅ |
| `%RESP(X-ENVOY-UPSTREAM-SERVICE-TIME)%` | `upstream_service_time` | ✅ | ✅ | ✅ | — |
| `%REQ(X-FORWARDED-FOR)%` | `forwarded_for` | ✅ | ✅ | ✅ | — |
| `%REQ(USER-AGENT)%` | `user_agent` | ✅ | ✅ | ✅ | — |
| `%REQ(X-REQUEST-ID)%` | `request_id` | ✅ | ✅ | ✅ | — |
| `%REQ(:AUTHORITY)%` | `authority` | ✅ | ✅ | ✅ | — |
| `%UPSTREAM_HOST%` | `upstream_host` | ✅ | ✅ | ✅ | — |
| `%STREAM_ID%` | `stream_id` | — | ✅ | ✅ | — |
| `%DOWNSTREAM_REMOTE_ADDRESS%` | `downstream_remote_address` | — | — | — | ✅ |
| `%DOWNSTREAM_REMOTE_PORT%` | `downstream_remote_port` | — | — | — | ✅ |
| `%CONNECTION_TERMINATION_DETAILS%` | `connection_termination_details` | — | — | — | ✅ |
| `%CONNECTION_ID%` | `connection_id` | — | — | — | ✅ |

Quan hệ giữa các mẫu, theo câu dẫn của từng mục:

```
HTTP   = bộ cơ bản
HTTP2  = HTTP + %STREAM_ID%
gRPC   = HTTP + %STREAM_ID% + %GRPC_STATUS()%
TCP    = bộ riêng (địa chỉ/port downstream, chi tiết kết thúc kết nối, connection id)
```

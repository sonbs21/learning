# Tuần 9 — Message contract và schema evolution

> Message là contract giữa producer và consumer, có thể sống lâu hơn version hiện tại của service. Một thay đổi nhỏ như đổi enum, bỏ field hoặc đổi timezone có thể làm consumer cũ hỏng âm thầm.

## 1. Kết quả cần đạt

Sau bài học, bạn có thể:

- Phân biệt envelope và payload.
- Thiết kế event có eventId, eventType, eventVersion, occurredAt, correlationId và causationId.
- Lập catalog message với owner, consumer và routing key.
- Phân biệt backward-compatible change và breaking change.
- Thêm field optional mà consumer cũ vẫn chạy.
- Tạo version 2 và viết adapter.
- Xử lý malformed JSON, missing field, unknown enum và nullability.
- Quyết định format cho timezone, tiền, số thập phân và identifier.
- Tránh PII, secret và token trong message.
- Viết contract test bắt breaking change trước deploy.

## 2. Message contract là gì?

Contract là tập quy tắc producer cam kết và consumer dựa vào:

~~~text
event type
routing key
headers/properties
envelope
payload fields
field types
required/optional/nullability
format
version
error behavior
~~~

Ví dụ:

~~~json
{
  "eventId": "evt-100",
  "eventType": "order.created",
  "eventVersion": 1,
  "occurredAt": "2026-10-04T10:00:00Z",
  "correlationId": "corr-100",
  "causationId": "cmd-100",
  "producer": "order-service",
  "payload": {
    "orderId": "ORD-1001",
    "customerId": "CUS-10",
    "totalMinor": 125000
  }
}
~~~

Consumer cần biết không chỉ JSON có parse được, mà còn:

- event này là loại gì;
- version nào;
- field nào bắt buộc;
- total tính theo đơn vị nào;
- occurredAt là UTC hay local;
- field mới có thể xuất hiện không;
- unknown enum xử lý ra sao;
- eventId dùng để deduplication thế nào.

## 3. Envelope và payload

### Envelope

Envelope chứa metadata chung:

~~~json
{
  "eventId": "uuid",
  "eventType": "order.created",
  "eventVersion": 1,
  "occurredAt": "2026-10-04T10:00:00Z",
  "correlationId": "uuid",
  "causationId": "uuid",
  "producer": "order-service",
  "payload": {}
}
~~~

### Payload

Payload chứa dữ liệu nghiệp vụ:

~~~json
{
  "orderId": "ORD-1001",
  "customerId": "CUS-10",
  "totalMinor": 125000,
  "currency": "VND"
}
~~~

Tách hai phần giúp middleware đọc metadata mà không cần hiểu business, tracing thống nhất, replay giữ được context và adapter version dễ viết.

## 4. Các field quan trọng

| Field | Ý nghĩa |
|---|---|
| eventId | Định danh event, dùng audit/dedup/replay |
| eventType | Tên semantic như order.created |
| eventVersion | Version contract |
| occurredAt | Thời điểm business event xảy ra |
| correlationId | Định danh workflow/trace |
| causationId | Message trực tiếp gây ra event |
| producer | Service phát message |
| payload | Dữ liệu nghiệp vụ |

eventType không nên là full Java class name. occurredAt phải là ISO-8601 có timezone. correlationId và eventId có lifecycle khác nhau.

~~~text
eventId       = định danh event
messageId     = định danh delivery/publish nếu dùng
correlationId = định danh workflow
causationId   = nguyên nhân trực tiếp
~~~

## 5. Contract ownership và catalog

Catalog tối thiểu:

| Message | Type | Owner | Exchange | Routing key | Consumer |
|---|---|---|---|---|---|
| OrderCreated | event | order-service | order.events | order.created | email, analytics, audit |
| CapturePayment | command | payment-service | order.commands | payment.capture | payment worker |
| ShippingFeeRequest | request | checkout-service | shipping.requests | shipping.request | shipping worker |

Ghi thêm:

- version hiện tại;
- schema location;
- required fields;
- deprecated fields;
- PII classification;
- retry/DLQ policy;
- retention/replay policy;
- compatibility expectation.

Event producer sở hữu sự thật nghiệp vụ. Command owner chịu trách nhiệm validation và xử lý. Không để service không sở hữu contract tự ý đổi field.

## 6. Backward-compatible changes

### Thêm field optional

V1:

~~~json
{
  "orderId": "ORD-1",
  "totalMinor": 125000
}
~~~

Mở rộng:

~~~json
{
  "orderId": "ORD-1",
  "totalMinor": 125000,
  "salesChannel": "WEB"
}
~~~

Consumer cũ có thể bỏ qua salesChannel. Field mới phải optional, có default hợp lý và không đổi meaning field cũ.

### Thêm object optional

~~~json
{
  "orderId": "ORD-1",
  "shipping": {
    "country": "VN",
    "method": "EXPRESS"
  }
}
~~~

Consumer cũ không dùng shipping vẫn chạy. Consumer mới phải xử lý message cũ không có object này.

### Thêm routing key

Thêm key mới thường không phá binding key cụ thể, nhưng binding rộng như order.# có thể nhận event mới. Compatibility phải kiểm tra cả routing topology, không chỉ JSON.

## 7. Breaking changes

Các thay đổi thường breaking:

- xóa required field;
- đổi field name;
- đổi type;
- đổi đơn vị tiền;
- đổi timezone semantics;
- đổi meaning field;
- thêm enum value khi consumer strict;
- đổi eventType;
- đổi routing key khiến queue không còn nhận;
- thay đổi ordering hoặc duplicate contract.

Ví dụ V1:

~~~json
{
  "total": 125000
}
~~~

V2 breaking:

~~~json
{
  "total": {
    "amount": 125000,
    "currency": "VND"
  }
}
~~~

Consumer V1 mong number sẽ fail. Cách an toàn hơn là giữ total trong compatibility window và thêm object money optional, hoặc tăng eventVersion rồi dùng adapter.

## 8. Versioning strategy

### Version trong envelope

~~~text
eventType = order.created
eventVersion = 1 hoặc 2
~~~

Giữ semantic event ổn định, consumer dispatch theo version.

### Version trong eventType/routing key

~~~text
order.created.v1
order.created.v2
~~~

Route rõ và rollout riêng, nhưng catalog có nhiều event name hơn.

### Version trong header

~~~text
eventType = order.created
header x-event-version = 2
~~~

Header hữu ích cho transport, nhưng schema vẫn phải được document trong catalog.

| Strategy | Khi phù hợp |
|---|---|
| eventVersion trong envelope | Nhiều version cùng semantic |
| version trong routing key | Muốn route riêng |
| version trong eventType | Contract tách hẳn |
| Không version | Chỉ cho contract rất ổn định |

Chọn một convention nhất quán. Không tăng version tùy tiện cho mọi additive field.

## 9. Consumer tolerant nhưng vẫn validate

Tolerant reader nghĩa là consumer không fail chỉ vì producer thêm field không liên quan. Không có nghĩa bỏ qua mọi validation.

Flow:

~~~text
parse envelope
  -> validate eventType/version
  -> validate required payload fields
  -> ignore unknown optional fields
  -> map internal model
  -> process
~~~

Có thể bỏ qua:

- optional field mới;
- tracing header mới;
- metadata không thuộc responsibility.

Phải fail rõ:

- thiếu orderId;
- eventType không biết;
- eventVersion không hỗ trợ;
- amount không hợp lệ;
- currency không hợp lệ;
- occurredAt không parse được;
- payload vi phạm security policy.

Message permanent invalid nên đi error/DLQ, không retry vô hạn.

## 10. Missing, null và default

~~~json
{}
~~~

~~~json
{
  "discount": null
}
~~~

~~~json
{
  "discount": 0
}
~~~

Ba trạng thái có thể có nghĩa khác nhau:

| Trạng thái | Nghĩa có thể |
|---|---|
| Missing | Producer chưa biết hoặc message version cũ |
| null | Biết là không có giá trị |
| 0 | Giá trị bằng zero |

Contract phải document. Không tự động coi missing = null = zero.

## 11. Tiền, timezone và identifier

### Tiền

Không dùng floating point cho tiền:

~~~text
sai: double 12.34
tốt: amountMinor=1234, currency=USD
~~~

Hoặc:

~~~json
{
  "amount": "125000.00",
  "currency": "USD"
}
~~~

Document scale và rounding. Consumer không được đoán đơn vị.

### Timezone

Dùng UTC hoặc offset rõ:

~~~text
2026-10-04T10:00:00Z
2026-10-04T17:00:00+07:00
~~~

Ghi rõ occurredAt là thời điểm business event, commit, publish hay consume.

### Identifier

Dùng string cho ID trong JSON:

~~~json
{
  "orderId": "1001"
}
~~~

Tránh đổi number/string giữa version. Đừng để consumer phụ thuộc format nội bộ nếu format có thể thay đổi.

## 12. Enum và unknown value

Enum mới có thể làm consumer cũ fail:

~~~text
V1: CREATED | PAID | CANCELLED
V2: CREATED | PAID | CANCELLED | REFUNDED
~~~

Policy có thể là:

~~~text
unknown enum
  -> log/metric
  -> bỏ qua nếu không thuộc responsibility
  -> DLQ nếu không xử lý an toàn
~~~

Không map REFUNDED thành FAILED chỉ để tránh exception. Đó có thể là data corruption.

## 13. Malformed JSON và validation

Có hai lớp:

~~~text
syntactic validation = JSON parse được không
semantic validation  = dữ liệu đúng contract/business không
~~~

JSON hợp lệ nhưng sai nghiệp vụ:

~~~json
{
  "orderId": "",
  "totalMinor": -100
}
~~~

Boundary nên là:

~~~text
bytes
  -> JSON parse
  -> envelope validation
  -> schema/version validation
  -> business validation
  -> handler
~~~

Malformed hoặc permanent invalid message không nên đi retry vô hạn.

## 14. DTO và adapter

V1:

~~~java
public record OrderCreatedV1(
        String orderId,
        String customerId,
        long totalMinor
) {
}
~~~

V2:

~~~java
public record OrderCreatedV2(
        String orderId,
        String customerId,
        long totalMinor,
        String currency,
        String salesChannel
) {
}
~~~

Internal model:

~~~java
public record OrderCreated(
        String orderId,
        String customerId,
        long totalMinor,
        String currency,
        String salesChannel
) {
}
~~~

Adapter V1:

~~~java
public OrderCreated adapt(OrderCreatedV1 source) {
    return new OrderCreated(
            source.orderId(),
            source.customerId(),
            source.totalMinor(),
            "VND",
            "UNKNOWN"
    );
}
~~~

Default V1 chỉ được dùng khi contract đã thống nhất. Không tự gán VND chỉ vì service hiện tại ở Việt Nam nếu payload không cung cấp currency.

Adapter V2:

~~~java
public OrderCreated adapt(OrderCreatedV2 source) {
    return new OrderCreated(
            source.orderId(),
            source.customerId(),
            source.totalMinor(),
            source.currency(),
            source.salesChannel()
    );
}
~~~

Business handler chỉ nhận internal model, không phụ thuộc chi tiết từng version.

## 15. Envelope Java

~~~java
public record MessageEnvelope<T>(
        String eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        String correlationId,
        String causationId,
        String producer,
        T payload
) {
}
~~~

Dispatch:

~~~java
switch (envelope.eventType()) {
    case "order.created" -> handleOrderCreated(envelope);
    case "order.paid" -> handleOrderPaid(envelope);
    default -> handleUnknownEvent(envelope);
}
~~~

Unknown event không được rơi vào business handler mặc định nguy hiểm.

## 16. Producer rules

Producer nên:

- phát event sau khi business event thực sự xảy ra;
- tạo eventId ổn định;
- ghi eventVersion;
- ghi occurredAt có timezone;
- dùng eventType semantic;
- không phát entity JPA trực tiếp;
- không xóa/đổi field consumer còn dùng;
- document additive fields;
- test JSON output;
- ghi owner và changelog.

Không nên đổi meaning field mà không tăng version, dùng class name làm eventType, phát secret/token hoặc gửi local timestamp không timezone.

## 17. Consumer rules

Consumer nên:

- validate envelope;
- kiểm tra eventType/version;
- bỏ qua unknown optional field;
- xử lý message cũ;
- map DTO về internal model;
- log eventId/correlationId;
- metric unknown version/field;
- đưa malformed/permanent invalid vào DLQ;
- giữ idempotency.

Không nên fail vì field mới không liên quan, map unknown enum vào trạng thái sai, coi missing = zero hoặc silently ignore event không hiểu.

## 18. Contract testing

### Producer contract test

Kiểm tra:

- eventType và eventVersion;
- required fields;
- field types;
- occurredAt có timezone;
- payload không chứa secret;
- routing key đúng.

### Consumer compatibility fixtures

Tạo:

~~~text
valid-v1.json
valid-v2.json
malformed.json
missing-required.json
unknown-enum.json
unsupported-version.json
~~~

Kỳ vọng:

| Fixture | Expected |
|---|---|
| V1 hợp lệ | Process |
| V2 thêm optional field | Process |
| Missing required | Error/DLQ |
| Unknown optional enum | Policy rõ |
| Malformed JSON | Error/DLQ |
| Unsupported version | Quarantine/DLQ |

### Consumer-driven contract

Consumer công bố expectation:

~~~text
email consumer:
  eventType = order.created
  orderId = required
  customerId = required
  totalMinor = required
  salesChannel = optional
~~~

Producer chạy contract test trước release để bắt thay đổi phá consumer.

## 19. Schema registry và catalog

RabbitMQ không tự quản lý schema compatibility. Hệ thống lớn cần catalog hoặc registry lưu:

- schema;
- version;
- owner;
- compatibility mode;
- examples;
- changelog;
- consumer list;
- deprecation date.

Catalog tối thiểu:

~~~text
event type
current version
owner
exchange/routing key
schema file
required fields
deprecated fields
PII classification
compatibility rule
consumer list
replay policy
~~~

Schema không nên chỉ nằm trong code của một service.

## 20. PII và security

Không đưa vào message nếu không cần:

- password;
- access token;
- refresh token;
- secret key;
- full payment card number;
- PII ngoài responsibility;
- stack trace chứa connection string.

Message có thể nằm trong queue, retry queue, DLQ, log, backup và trace. Error payload cũng phải sanitize.

Nếu consumer chỉ cần customerId, gửi customerId thay vì toàn bộ customer profile.

## 21. Lab 1 — Thêm field optional

V1:

~~~json
{
  "eventId": "evt-1",
  "eventType": "order.created",
  "eventVersion": 1,
  "payload": {
    "orderId": "ORD-1",
    "customerId": "CUS-1",
    "totalMinor": 125000
  }
}
~~~

V2 additive:

~~~json
{
  "eventId": "evt-2",
  "eventType": "order.created",
  "eventVersion": 1,
  "payload": {
    "orderId": "ORD-2",
    "customerId": "CUS-2",
    "totalMinor": 125000,
    "salesChannel": "WEB"
  }
}
~~~

Thực hiện:

1. Chạy consumer chỉ đọc field V1.
2. Publish V2 có salesChannel.
3. Xác nhận consumer cũ vẫn process.
4. Kiểm tra unknown field policy.
5. Ghi lý do additive field không breaking.

## 22. Lab 2 — Version 2 và adapter

V2:

~~~json
{
  "eventType": "order.created",
  "eventVersion": 2,
  "payload": {
    "orderId": "ORD-2",
    "customer": {
      "id": "CUS-2"
    },
    "money": {
      "amountMinor": 125000,
      "currency": "VND"
    }
  }
}
~~~

Thực hiện:

1. Viết DTO V1 và V2.
2. Viết adapter về internal model.
3. Dispatch theo eventVersion.
4. Test fixture V1 và V2.
5. Xác nhận business handler chỉ nhận internal model.
6. Ghi default/migration rule.

## 23. Lab 3 — Malformed, missing và enum mới

Fixtures:

~~~json
{"eventId":"evt-1"
~~~

~~~json
{
  "eventId": "evt-2",
  "eventType": "order.created",
  "eventVersion": 1,
  "payload": {
    "customerId": "CUS-2"
  }
}
~~~

~~~json
{
  "eventId": "evt-3",
  "eventType": "order.created",
  "eventVersion": 1,
  "payload": {
    "orderId": "ORD-3",
    "status": "REFUNDED"
  }
}
~~~

Thực hiện:

1. Publish từng fixture.
2. Ghi lỗi parse/validation.
3. Kiểm tra retry có xảy ra không.
4. Đưa permanent invalid message vào DLQ.
5. Log eventId, version và error type.
6. Xác nhận message hợp lệ phía sau vẫn xử lý.

## 24. Lab 4 — Correlation ID

Producer tạo:

~~~text
correlationId = trace-week9-001
causationId   = command-create-order-001
eventId       = evt-order-001
~~~

Consumer log:

~~~text
eventType=order.created
eventVersion=1
eventId=evt-order-001
correlationId=trace-week9-001
causationId=command-create-order-001
producer=order-service
consumer=email-service
~~~

Kiểm tra correlationId, causationId và eventId có đúng lifecycle hay không. Replay nên giữ eventId gốc nhưng thêm replayId.

## 25. Lab 5 — Breaking change contract test

Tạo thay đổi:

~~~text
V1: totalMinor là number
V2: totalMinor là object
~~~

Chạy consumer V1 và xác nhận test fail rõ. Chọn một cách sửa:

1. Giữ field cũ, thêm money object optional.
2. Tăng eventVersion và viết adapter.
3. Tách eventType versioned.
4. Rollout consumer mới trước producer mới.

Ghi quyết định, lý do và rollout order.

## 26. Compatibility matrix

| Thay đổi | Consumer cũ | Hành động |
|---|---|---|
| Thêm optional field | Thường chạy nếu tolerant | Giữ version |
| Xóa optional field | Có thể thiếu dữ liệu | Document/deprecate |
| Xóa required field | Có thể hỏng | Breaking/version |
| Đổi type | Có thể hỏng | Version/adapter |
| Thêm enum | Có thể hỏng | Unknown policy |
| Đổi timezone meaning | Sai logic | Breaking/document |
| Thêm routing key | Broad binding có thể nhận thêm | Test topology |
| Đổi routing key | Queue cũ không nhận | Migration |
| Đổi meaning field | Hỏng âm thầm | Không làm hoặc version |

## 27. Lỗi thiết kế thường gặp

- Không có eventVersion hoặc version strategy.
- Dùng class name làm eventType.
- Đổi meaning field nhưng giữ nguyên tên.
- Thêm required field đột ngột.
- Deserialize enum quá strict.
- Coi missing, null và zero là một.
- Dùng floating point cho tiền.
- Gửi local timestamp không timezone.
- Serialize entity JPA.
- Đưa PII/secret vào DLQ.
- Không có catalog owner.
- Không test message cũ với consumer mới.
- Không test producer mới với consumer cũ.
- Replay message cũ bằng parser chỉ hỗ trợ version mới.
- Đổi routing key không kiểm tra binding.
- Silently ignore unknown event không metric.

## 28. Checklist

- [ ] Có catalog event/command và owner.
- [ ] Có eventId/messageId.
- [ ] Có eventType semantic.
- [ ] Có eventVersion hoặc strategy rõ.
- [ ] occurredAt có timezone.
- [ ] correlationId/causationId được định nghĩa.
- [ ] Envelope và payload tách biệt.
- [ ] Required/optional/nullability được document.
- [ ] Tiền có currency và scale.
- [ ] Enum có unknown policy.
- [ ] Consumer tolerant với unknown optional field.
- [ ] Invalid payload đi error/DLQ.
- [ ] Không serialize entity JPA.
- [ ] Không gửi secret/PII không cần thiết.
- [ ] Có fixture V1/V2.
- [ ] Có producer contract test.
- [ ] Có consumer compatibility test.
- [ ] Routing key nằm trong contract.
- [ ] Replay tránh duplicate.

## 29. Deliverable cuối tuần

Tạo:

~~~text
rabbitmq/week-09/
├── README.md
├── contracts/
│   ├── order.created.v1.json
│   ├── order.created.v2.json
│   └── catalog.md
├── fixtures/
│   ├── valid-v1.json
│   ├── valid-v2.json
│   ├── malformed.json
│   ├── missing-required.json
│   └── unknown-enum.json
└── src/test/
    ├── ProducerContractTest.java
    ├── ConsumerCompatibilityTest.java
    └── EventVersionAdapterTest.java
~~~

README cần có:

1. Envelope chuẩn.
2. Payload V1/V2.
3. Event/command catalog và owner.
4. Compatibility matrix.
5. Versioning strategy.
6. Nullability, enum, money và timezone policy.
7. PII/security policy.
8. Kết quả malformed/missing/unknown enum lab.
9. Kết quả contract tests.
10. Rollout plan khi producer và consumer deploy lệch version.

## 30. Tiêu chí tự kiểm tra

1. Message contract gồm những phần nào?
2. Envelope khác payload thế nào?
3. eventId khác correlationId và causationId thế nào?
4. Vì sao occurredAt cần timezone rõ?
5. Thêm optional field có luôn backward-compatible không?
6. Vì sao đổi field type thường breaking?
7. Consumer tolerant reader cần tolerant đến mức nào?
8. Missing, null và zero khác nhau thế nào?
9. Vì sao tiền không nên dùng floating point?
10. Unknown enum xử lý ra sao?
11. Khi nào tăng eventVersion?
12. Version trong envelope khác version trong routing key thế nào?
13. Vì sao consumer cần adapter?
14. Contract test bắt lỗi gì trước deploy?
15. Vì sao RabbitMQ không tự quản lý schema compatibility?
16. PII nào không nên đưa vào message/DLQ?
17. Replay event cũ có nguy cơ gì?
18. Vì sao routing key cũng là contract?

### Đáp án cần nhớ

> Message là contract giữa các service, không phải JSON tạm thời. Hãy tách envelope và payload, định danh event rõ, ghi version/timezone/correlation, thêm field theo hướng backward-compatible, dùng adapter cho version khác nhau và test bằng fixture cũ/mới. Consumer nên tolerant với metadata mới nhưng strict với dữ liệu bắt buộc. Schema evolution bao gồm cả payload, headers, routing key và replay policy.

## 31. Chuẩn bị cho Tuần 10

Tuần sau sẽ giải quyết duplicate và consistency:

- at-least-once + idempotency;
- inbox/deduplication;
- unique constraint trên eventId;
- ack sau database commit;
- dual-write problem;
- transactional outbox;
- publisher worker;
- processed_messages và retention.

Giữ lại catalog, fixtures và adapter của tuần này. Chúng là contract nền cho outbox, inbox và integration test ở tuần sau.

# Tuần 13: Event schema và compatibility

## 1. Mục tiêu của tuần

Sau bài học này, bạn có thể:

- Phân biệt command, event và notification.
- Thiết kế event envelope có metadata đủ cho tracing, deduplication và debugging.
- Tách event name, event version và schema version.
- Giải thích backward compatibility, forward compatibility và full compatibility.
- Thiết kế thay đổi schema an toàn khi producer và consumer deploy lệch thời điểm.
- Biết khi nào field mới nên optional, khi nào cần default value.
- Tránh các lỗi phổ biến với enum, null, timestamp, tiền tệ và field rename.
- Hiểu khác biệt thực tế giữa JSON Schema, Avro và Protobuf.
- Biết Schema Registry giải quyết vấn đề gì và không giải quyết vấn đề gì.
- Thiết kế policy owner, retention, privacy classification và migration cho event.
- Hoàn thành bài tập OrderCreated v1/v2 với consumer cũ vẫn đọc được event mới.

Nguyên tắc trung tâm:

> Event là một public contract giữa các service. Một field đã phát hành không còn là implementation detail riêng của producer.

---

## 2. Vì sao event schema là vấn đề kiến trúc

Khi một service phát event, service đó không chỉ gửi một object. Nó đang công bố:

- Tên của một sự việc đã xảy ra.
- Ý nghĩa nghiệp vụ của từng field.
- Kiểu dữ liệu và đơn vị đo.
- Quy tắc field nào bắt buộc.
- Cách consumer xử lý field không biết.
- Cách contract thay đổi theo thời gian.
- Cách debug một event xuyên qua nhiều service.
- Cách bảo vệ dữ liệu cá nhân và dữ liệu bí mật.

Ví dụ, field totalAmount có thể mang nhiều nghĩa:

- Tổng tiền trước discount.
- Tổng tiền sau discount.
- Đơn vị là VND hay USD.
- Là integer theo đơn vị nhỏ nhất hay decimal.
- Có bao gồm shipping fee và tax hay không.

Nếu không ghi rõ semantics, schema vẫn có thể validate thành công nhưng consumer vẫn hiểu sai nghiệp vụ.

Một schema tốt phải trả lời được:

1. Event này đại diện cho fact hay yêu cầu?
2. Ai sở hữu contract?
3. Consumer cần field nào để xử lý?
4. Field nào có thể vắng mặt?
5. Khi thêm hoặc đổi field, consumer cũ có còn hoạt động không?
6. Event có chứa PII hay secret không?
7. Có thể replay event này sau 30 ngày không?
8. Event đã được publish rồi có được đổi ý nghĩa không?

---

## 3. Command, event và notification

### Command

Command là yêu cầu một service hoặc component thực hiện hành động.

Đặc điểm:

- Thường có một consumer chịu trách nhiệm chính.
- Có ý định trong tương lai.
- Có thể bị từ chối vì validation hoặc business rule.
- Tên thường dùng động từ: CreateOrder, ReserveInventory, CapturePayment.
- Cần correlation và reply/error strategy rõ ràng.

Ví dụ:

~~~text
ReserveInventory
CapturePayment
SendOrderConfirmation
~~~

Không nên coi command là fact đã xảy ra. ReserveInventory chỉ nói “hãy reserve”, không nói việc reserve đã thành công.

### Event

Event là fact đã xảy ra.

Đặc điểm:

- Dùng thì quá khứ hoặc tên sự kiện: OrderCreated, PaymentCaptured.
- Có thể có nhiều consumer độc lập.
- Consumer không nên sửa event gốc.
- Có thể replay để dựng lại projection hoặc chạy pipeline mới.
- Semantics của event phải ổn định.

Ví dụ:

~~~text
OrderCreated
InventoryReserved
PaymentCaptured
OrderCancelled
~~~

OrderCreated không nên được phát trước khi hệ thống thực sự commit order theo consistency model đã chọn.

### Notification

Notification thường là thông báo cho một hoặc nhiều bên, có thể ít formal hơn event domain.

Ví dụ:

~~~text
CustomerEmailRequested
ShipmentTrackingUpdated
DashboardRefreshRequested
~~~

Notification có thể mang dữ liệu đã được chuẩn bị cho người nhận. Nó không nhất thiết là canonical business event.

### Bảng so sánh

| Loại | Ý nghĩa | Ngữ pháp | Consumer chính | Có replay không |
|---|---|---|---|---|
| Command | Yêu cầu hành động | Mệnh lệnh | Một service hoặc handler | Có thể |
| Event | Fact đã xảy ra | Quá khứ | Nhiều consumer | Thường có |
| Notification | Thông báo hoặc signal | Linh hoạt | Một hoặc nhiều consumer | Tùy |

Sai lầm phổ biến là đặt tên mọi message là event trong khi message thực tế là command. Sai phân loại làm consumer khó hiểu ownership và retry semantics.

---

## 4. Event envelope

Payload nghiệp vụ không nên phải tự mang toàn bộ metadata vận hành. Hãy dùng envelope để tách metadata chung khỏi data riêng của event.

Một envelope có thể gồm:

~~~json
{
  "specversion": "1.0",
  "type": "com.example.order.created",
  "id": "01JORDER9W5S8E7K3X2",
  "time": "2026-09-26T10:15:30.123Z",
  "source": "order-service",
  "subject": "order-123",
  "datacontenttype": "application/json",
  "schema": "order-created",
  "schemaVersion": 2,
  "correlationId": "corr-7f4d",
  "causationId": "cmd-82aa",
  "traceId": "trace-abc",
  "data": {
    "orderId": "order-123",
    "customerId": "customer-9",
    "currency": "VND",
    "totalMinor": 1250000,
    "items": []
  }
}
~~~

### Các field nên có

specversion:

- Version của envelope convention.
- Không nhất thiết là version của business payload.

type:

- Event type ổn định và có namespace.
- Không tái sử dụng type cho semantics khác.
- Ví dụ com.example.order.created.

id:

- ID duy nhất của event instance.
- Dùng cho tracing, deduplication và audit.
- Không dùng lại id khi retry cùng một event.

time:

- Thời điểm event được tạo hoặc fact xảy ra.
- Cần thống nhất timezone và format.
- Thường dùng UTC với ISO-8601.

source:

- Service hoặc bounded context phát event.
- Nên ổn định và có owner.

subject:

- Entity mà event liên quan, ví dụ order-123.
- Hữu ích khi trace hoặc partition theo entity.

schema:

- Tên logical của schema.
- Có thể là subject trong Schema Registry.

schemaVersion:

- Version của payload contract.
- Không nên dùng một field version chung cho mọi mục đích.

correlationId:

- Liên kết nhiều message trong cùng một business flow hoặc request flow.
- Ví dụ một HTTP request tạo ra OrderCreated, PaymentRequested và EmailRequested.

causationId:

- ID của message trực tiếp gây ra event hiện tại.
- Giúp dựng chuỗi nguyên nhân: command -> event -> event.

traceId:

- Liên kết với distributed tracing.
- Không nên dùng traceId thay cho event id.

data:

- Business payload.
- Không nhét password, access token hoặc secret vào đây.

### Correlation ID và causation ID

Ví dụ:

~~~text
HTTP request
  correlationId = C1

CreateOrder command
  id = M1
  correlationId = C1
  causationId = HTTP-request-1

OrderCreated event
  id = E1
  correlationId = C1
  causationId = M1

PaymentRequested command
  id = M2
  correlationId = C1
  causationId = E1
~~~

Correlation ID trả lời “chuỗi này thuộc flow nào?”. Causation ID trả lời “message nào trực tiếp gây ra message này?”.

Một event retry phải giữ nguyên event id nếu đó là cùng một event delivery. Nếu application tạo business event mới thì phải tạo id mới và ghi rõ lý do.

---

## 5. Event schema không chỉ là kiểu dữ liệu

Schema nên mô tả cả:

- Tên field.
- Kiểu dữ liệu.
- Required hay optional.
- Đơn vị và range.
- Semantics.
- Default khi thiếu.
- Null có nghĩa gì.
- Enum có thể mở rộng ra sao.
- PII classification.
- Owner.
- Retention.
- Compatibility policy.
- Ví dụ hợp lệ và không hợp lệ.

Ví dụ documentation cho amount:

~~~text
totalMinor:
  type: integer
  required: true
  unit: minor currency unit
  currency: lấy từ field currency
  minimum: 0
  semantic: tổng tiền phải thanh toán sau discount và trước payment capture
  pii: no
~~~

Tránh field tên quá chung:

- status.
- type.
- value.
- amount.
- data.
- metadata.

Nếu dùng tên chung, hãy đặt trong context rõ hoặc mô tả semantics thật cụ thể.

Tốt hơn:

~~~text
paymentStatus
totalMinor
shippingFeeMinor
customerEmailHash
~~~

---

## 6. Compatibility là gì

Compatibility trả lời câu hỏi: hai version schema có thể cùng tồn tại trong quá trình rolling deployment, replay và migration hay không?

Có ba hướng cần phân biệt.

### Backward compatibility

Schema mới có thể đọc data được tạo bởi schema cũ.

~~~text
new reader <- old writer
~~~

Ví dụ:

- Consumer code mới đọc event cũ không có field mới.
- Field mới phải có default hoặc optional.
- Consumer mới không được giả định mọi record cũ có field mới.

Backward compatibility rất quan trọng khi:

- Consumer deploy trước producer.
- Replay dữ liệu cũ bằng code mới.
- Một service nâng version độc lập.

### Forward compatibility

Schema cũ có thể đọc data được tạo bởi schema mới.

~~~text
old reader <- new writer
~~~

Ví dụ:

- Producer mới thêm field.
- Consumer cũ bỏ qua field mà nó chưa biết.
- Old consumer không được fail chỉ vì có field mới.

Forward compatibility rất quan trọng khi:

- Producer deploy trước consumer.
- Rollout theo canary.
- Có consumer bên ngoài chưa nâng cấp.

### Full compatibility

Cả hai hướng đều hoạt động:

~~~text
new reader <- old writer
old reader <- new writer
~~~

Không phải mọi hệ thống đều cần full compatibility. Nhưng khi nhiều service deploy độc lập và event được replay lâu dài, full compatibility thường là mục tiêu an toàn.

### Compatibility theo thời gian

Ngoài hai version liền kề, cần nghĩ đến:

- Consumer mới đọc event rất cũ.
- Consumer cũ còn chạy trong rolling deployment.
- Event trong retention 30 ngày.
- Replay sau incident.
- Một consumer ít được deploy hơn các consumer khác.

Một schema change “đọc được ngay” chưa chắc tương thích với toàn bộ lịch sử retention.

---

## 7. Ma trận thay đổi field

| Thay đổi | Backward | Forward | Nhận xét |
|---|---|---|---|
| Thêm optional field | Thường an toàn | Thường an toàn nếu reader cũ bỏ qua | Khuyến nghị |
| Thêm required field không default | Có thể phá reader mới với event cũ | Có thể phá reader cũ | Tránh |
| Xóa field | Có thể phá reader mới nếu field cần | Có thể an toàn về wire nhưng mất semantics | Cần migration |
| Đổi tên field | Thường là xóa + thêm | Có thể mất dữ liệu | Tránh hoặc alias |
| Đổi type hẹp sang rộng | Tùy format | Tùy format | Phải test |
| Đổi type rộng sang hẹp | Dễ mất dữ liệu | Dễ fail | Tránh |
| Đổi ý nghĩa field | Không tương thích semantics | Không tương thích semantics | Không làm |
| Thêm enum value | Có thể phá consumer strict | Có thể an toàn nếu unknown value được xử lý | Consumer phải tolerant |
| Xóa enum value | Phá reader xử lý lịch sử | Có thể phá | Deprecate trước |
| Đổi required thành optional | Thường an toàn cho reader mới | Có thể phá consumer cũ nếu nó cần field | Cần xem semantics |
| Đổi optional thành required | Dễ phá | Dễ phá | Tránh |
| Đổi đơn vị đo | Có thể validate nhưng sai nghiệp vụ | Có thể validate nhưng sai nghiệp vụ | Tạo field mới |

Điểm quan trọng: compatibility ở syntax không đồng nghĩa compatibility ở business semantics.

---

## 8. Optional field và default value

Thêm field mới an toàn thường cần:

- Field không bắt buộc trong giai đoạn đầu.
- Consumer cũ bỏ qua field mới.
- Consumer mới có hành vi rõ khi field vắng mặt.
- Default mang semantics đúng, không phải giá trị tùy tiện.

Ví dụ thêm couponCode:

~~~text
v1:
  orderId
  customerId
  totalMinor

v2:
  orderId
  customerId
  totalMinor
  couponCode: optional
~~~

Consumer mới có thể:

~~~text
if couponCode absent:
    xử lý như order không có coupon
else:
    validate coupon metadata
~~~

Không nên dùng default giả:

- totalMinor = 0 khi thiếu.
- customerId = unknown.
- status = ACTIVE.
- currency = VND nếu event có thể đến từ nhiều currency.

Default chỉ an toàn khi nó đúng về semantics. Nếu không có default hợp lệ, hãy:

- Giữ field optional và yêu cầu consumer branch rõ ràng.
- Tạo event version mới với migration.
- Tạo event type mới nếu đây là semantics khác.
- Reject event và đưa vào DLT nếu thiếu field thật sự không thể xử lý.

### Optional khác nullable

Hai trạng thái sau không giống nhau:

~~~text
field không xuất hiện
field xuất hiện với value = null
~~~

Cần quyết định:

- Missing nghĩa là producer chưa biết?
- Null nghĩa là biết rõ giá trị rỗng?
- Null có phải tombstone không?
- Consumer có được coi hai trạng thái là giống nhau không?

Trong JSON Schema, properties không tự động trở thành required; required phải được khai báo riêng. Null cũng không tự động tương đương với missing. Hãy định nghĩa rõ trong schema và code validation. [JSON Schema object reference](https://json-schema.org/understanding-json-schema/reference/object)

---

## 9. Enum evolution

Enum dễ tạo breaking change vì consumer thường viết logic exhaustive.

Ví dụ v1:

~~~text
PENDING
PAID
CANCELLED
~~~

Producer v2 thêm REFUNDED. Consumer cũ có thể:

- Bỏ qua unknown value.
- Map về UNKNOWN.
- Reject record.
- Throw exception làm partition bị kẹt.
- Chạy default branch nguy hiểm.

Policy an toàn:

- Consumer phải có UNKNOWN hoặc default branch.
- Không dùng unknown enum để tự động trigger side effect nguy hiểm.
- Thêm enum value là change cần contract test.
- Xóa enum value không nên làm ngay; deprecate trước.
- Không đổi nghĩa của enum value cũ.
- Nếu trạng thái mới làm thay đổi state machine, cần review consumer chứ không chỉ review schema.

Với business state, đôi khi tốt hơn dùng:

~~~text
statusCode: string
statusVersion: integer
~~~

hoặc event riêng:

~~~text
OrderRefunded
~~~

Thay vì biến một enum thành nơi chứa toàn bộ tiến hóa domain.

Protobuf có quy tắc đặc biệt với field number và enum value: số đã dùng không nên tái sử dụng; khi xóa field hoặc enum value cần reserve để tránh diễn giải nhầm dữ liệu cũ. [Proto3 updating message types](https://protobuf.dev/programming-guides/proto3/)

---

## 10. JSON Schema

JSON Schema phù hợp khi:

- Muốn payload dễ đọc.
- Cần validate ở boundary.
- Hệ thống dùng JSON qua Kafka, HTTP hoặc SQS.
- Team muốn schema gần với data representation.
- Cần mô tả required, type, enum, range và format.

Ví dụ schema cho OrderCreated v1:

~~~json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://example.com/schemas/order-created/v1",
  "title": "OrderCreated",
  "type": "object",
  "required": [
    "orderId",
    "customerId",
    "currency",
    "totalMinor",
    "items"
  ],
  "properties": {
    "orderId": {
      "type": "string",
      "minLength": 1
    },
    "customerId": {
      "type": "string",
      "minLength": 1
    },
    "currency": {
      "type": "string",
      "pattern": "^[A-Z]{3}$"
    },
    "totalMinor": {
      "type": "integer",
      "minimum": 0
    },
    "items": {
      "type": "array",
      "minItems": 1
    }
  },
  "additionalProperties": true
}
~~~

Trong giai đoạn event evolution, additionalProperties=true thường giúp consumer cũ tolerate field mới. Tuy nhiên, nó không có nghĩa mọi field lạ đều đúng về nghiệp vụ. Producer vẫn phải validate schema của chính nó.

additionalProperties=false giúp bắt typo và schema drift sớm, nhưng có thể làm consumer cũ reject event mới khi producer thêm field. Vì vậy phải chọn theo compatibility policy và cách validator được dùng. JSON Schema cho phép kiểm soát property bổ sung bằng keyword này. [JSON Schema additional properties](https://json-schema.org/understanding-json-schema/reference/object)

### JSON Schema v2 với field optional

~~~json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://example.com/schemas/order-created/v2",
  "title": "OrderCreated",
  "type": "object",
  "required": [
    "orderId",
    "customerId",
    "currency",
    "totalMinor",
    "items"
  ],
  "properties": {
    "orderId": {
      "type": "string",
      "minLength": 1
    },
    "customerId": {
      "type": "string",
      "minLength": 1
    },
    "currency": {
      "type": "string",
      "pattern": "^[A-Z]{3}$"
    },
    "totalMinor": {
      "type": "integer",
      "minimum": 0
    },
    "items": {
      "type": "array",
      "minItems": 1
    },
    "discountMinor": {
      "type": "integer",
      "minimum": 0,
      "default": 0
    }
  },
  "additionalProperties": true
}
~~~

Cần phân biệt:

- default trong schema có thể là metadata tùy validator.
- Nhiều validator không tự chèn default vào JSON.
- Application vẫn phải quyết định giá trị khi field vắng mặt.
- Nếu default là business rule quan trọng, nên thể hiện trong consumer code và test.

### Format và semantic validation

format có thể mô tả date-time, email hoặc uri, nhưng mức enforcement tùy validator. Các rule nghiệp vụ nên có validation riêng:

- totalMinor phải bằng tổng line item sau discount.
- currency phải khớp với pricing policy.
- item quantity phải lớn hơn 0.
- customerId phải tồn tại nếu event contract yêu cầu reference hợp lệ.

---

## 11. Avro ở mức khái niệm

Avro thường dùng schema riêng và binary encoding. Record được đọc dựa trên writer schema và reader schema.

Các khái niệm cần nắm:

- Writer schema: schema dùng để serialize record.
- Reader schema: schema consumer muốn dùng để đọc.
- Schema resolution: cách hai schema được đối chiếu.
- Default field: giúp reader mới đọc record cũ khi field mới không có trong writer schema.
- Union: thường dùng để biểu diễn nullable hoặc nhiều loại.
- Schema Registry: lưu schema và schema id để producer/consumer dùng đúng schema.

Ví dụ Avro record:

~~~json
{
  "type": "record",
  "name": "OrderCreated",
  "namespace": "com.example.order",
  "fields": [
    {
      "name": "orderId",
      "type": "string"
    },
    {
      "name": "customerId",
      "type": "string"
    },
    {
      "name": "totalMinor",
      "type": "long"
    },
    {
      "name": "currency",
      "type": "string",
      "default": "VND"
    }
  ]
}
~~~

Avro schema resolution match field theo tên trong record, không dựa vào thứ tự field. Reader field không có trong writer phải có default nếu muốn reader đọc được record cũ. Enum cũng cần policy default khi writer gửi symbol mà reader chưa biết. Đây là các quy tắc quan trọng trong Avro specification. [Apache Avro schema resolution](https://avro.apache.org/docs/1.12.0/specification/)

Avro phù hợp khi:

- Muốn payload compact.
- Cần schema evolution có quy tắc rõ.
- Dùng Schema Registry.
- Muốn giảm chi phí serialization cho pipeline lớn.
- Các service dùng code generation hoặc generic record.

Rủi ro:

- Team không hiểu writer/reader schema.
- Debug binary payload khó hơn JSON.
- Default hoặc union bị dùng sai.
- Schema Registry bị coi là nơi thay thế documentation.

---

## 12. Protobuf ở mức khái niệm

Protobuf dùng message definition và field number trong wire format.

Ví dụ:

~~~protobuf
message OrderCreated {
  string order_id = 1;
  string customer_id = 2;
  int64 total_minor = 3;
  string currency = 4;
}
~~~

Quy tắc quan trọng:

- Field number định danh field trong wire format.
- Không đổi field number của field đã phát hành.
- Không tái sử dụng field number đã xóa.
- Khi xóa field, reserve field number và thường cả field name.
- Thêm field mới với field number mới thường an toàn với binary format.
- Consumer nên xử lý unknown field theo cách tương thích.
- Enum value đã xóa cũng cần reserve nếu không muốn bị tái sử dụng.

Protobuf có ưu điểm:

- Binary nhỏ.
- Serialization nhanh.
- Code generation mạnh.
- Type contract rõ.
- Unknown field handling hỗ trợ evolution.

Rủi ro:

- Field number bị reuse gây diễn giải sai dữ liệu.
- ProtoJSON có quy tắc compatibility khác binary wire format.
- Người dùng nhầm đổi tên field là vô hại trong mọi representation.
- Enum unknown behavior khác nhau giữa ngôn ngữ và runtime.

Hãy đọc quy tắc update message type của protobuf trước khi sửa một message đã phát hành. [Protocol Buffers updating message types](https://protobuf.dev/programming-guides/proto3/)

---

## 13. Chọn JSON Schema, Avro hay Protobuf

| Tiêu chí | JSON Schema | Avro | Protobuf |
|---|---|---|---|
| Dễ đọc bằng mắt | Rất tốt | Tốt ở schema, payload binary | Thấp hơn khi xem payload |
| Kích thước payload | Lớn hơn | Nhỏ | Nhỏ |
| Code generation | Tùy tooling | Tốt | Rất tốt |
| Evolution | Cần policy rõ | Schema resolution mạnh | Field number rules mạnh |
| Debug production | Dễ | Cần decoder/schema | Cần generated tools |
| HTTP/browser | Thuận tiện | Ít thuận tiện hơn | Cần gateway/tooling |
| Kafka pipeline | Tốt | Rất phổ biến | Phổ biến |
| Rủi ro chính | Schema quá lỏng hoặc quá strict | Registry và union khó hiểu | Reuse field number |

Không có format tốt tuyệt đối.

Chọn JSON Schema khi:

- Payload cần dễ inspect.
- Hệ thống nhỏ hoặc trung bình.
- Consumer đa ngôn ngữ nhưng tooling JSON là ưu tiên.
- Muốn bắt đầu đơn giản với validation rõ.

Chọn Avro khi:

- Kafka là trung tâm.
- Cần schema registry và evolution policy.
- Muốn compact payload nhưng vẫn có schema resolution.
- Pipeline data lớn.

Chọn Protobuf khi:

- Cần binary contract mạnh.
- Có code generation.
- Có gRPC hoặc nhiều service typed.
- Cần hiệu năng serialization và payload nhỏ.

Điều quan trọng hơn format là:

- Contract có owner.
- Có compatibility check.
- Có consumer-driven contract test.
- Có migration plan.
- Có observability và privacy review.

---

## 14. Schema Registry

Schema Registry là nơi quản lý schema và version của schema. Tùy implementation, registry có thể:

- Lưu schema theo subject.
- Gán schema id.
- Kiểm tra compatibility.
- Cho producer serialize với schema id.
- Cho consumer lấy schema phù hợp để deserialize.
- Ngăn publish schema phá policy.

Registry không tự giải quyết:

- Event có đúng business semantics không.
- Producer có phát event quá sớm trước database commit không.
- Consumer có xử lý idempotent không.
- PII có được phép phát không.
- Field amount có đúng đơn vị không.
- Consumer đã deploy code xử lý schema mới chưa.
- Migration giữa hai event type có hoàn chỉnh không.

### Subject và ownership

Cần thống nhất schema subject theo một convention:

~~~text
orders.OrderCreated-value
payments.PaymentCaptured-value
com.example.order.created
~~~

Mỗi subject nên có:

- Owner team.
- Documentation link.
- Compatibility mode.
- Current version.
- Deprecation date nếu có.
- Data classification.
- Retention policy.
- Consumer catalog.

Không nên để mỗi team tự đặt naming tùy ý. Naming hỗn loạn làm registry khó tìm và khó audit.

### Compatibility policy

Một số policy thường gặp:

- BACKWARD: schema mới đọc được data cũ.
- FORWARD: schema cũ đọc được data mới.
- FULL: cả hai hướng.
- NONE: không kiểm tra tự động.

Chọn policy theo deployment và retention:

- Consumer deploy trước producer: cần backward.
- Producer deploy trước consumer: cần forward.
- Deploy độc lập và replay dài: cân nhắc full.
- NONE chỉ phù hợp khi topic private, migration atomic hoặc schema luôn đi cùng code.

---

## 15. Versioning strategy

Có ba lớp version cần tách.

### Event type version

Dùng khi semantics của event thay đổi lớn.

Ví dụ:

~~~text
OrderCreated
OrderCreatedV2
~~~

Nhược điểm là tạo nhiều event type và consumer route.

### Schema version

Dùng khi cùng event semantics nhưng shape thay đổi tương thích.

Ví dụ:

~~~text
type = OrderCreated
schemaVersion = 2
~~~

Phù hợp khi consumer có thể xử lý cả version cũ và mới.

### Envelope version

Dùng khi convention metadata thay đổi.

Ví dụ:

~~~text
specversion = 1.0
~~~

Không dùng envelope version để che giấu breaking change trong business payload.

### Khi nào tạo event type mới

Tạo event type mới nếu:

- Ý nghĩa event thay đổi.
- Điều kiện phát event thay đổi.
- Consumer phải xử lý theo state machine khác.
- Field cũ bị diễn giải lại.
- Event cũ và mới có lifecycle khác.
- Cần rollout song song trong thời gian dài.
- Không thể giữ compatibility mà không làm contract mơ hồ.

Ví dụ không nên đổi OrderCreated thành “order được tạo hoặc import từ legacy”. Nếu semantics khác, dùng:

~~~text
OrderCreated
OrderImported
~~~

Tên event phải phản ánh fact, không phải tên của database table.

---

## 16. Các thay đổi thường an toàn và không an toàn

### Thường an toàn

- Thêm optional field.
- Thêm metadata mà consumer cũ bỏ qua.
- Thêm enum value nếu consumer có UNKNOWN branch.
- Thêm event type mới song song với event cũ.
- Tăng numeric range nếu reader xử lý được.
- Thêm alias theo cơ chế của serialization format.

### Dễ phá compatibility

- Thêm required field không có default.
- Xóa field mà consumer mới vẫn cần.
- Đổi tên field.
- Đổi string thành integer hoặc ngược lại.
- Đổi đơn vị amount.
- Đổi timezone semantics.
- Đổi null thành missing hoặc ngược lại.
- Đổi enum value cũ sang nghĩa khác.
- Đổi event name nhưng giữ topic cũ mà không migration.
- Đặt additionalProperties=false trong khi producer còn rollout field mới.
- Reuse protobuf field number.
- Xóa Avro enum symbol mà không có default strategy.

### Không chỉ test schema

Cần test cả:

- Deserialize.
- Validation.
- Business mapping.
- Default behavior.
- Unknown enum.
- Missing field.
- Null field.
- Duplicate event.
- Replay event cũ.
- Event mới qua consumer cũ.
- Consumer mới qua event cũ.
- Privacy redaction.
- DLQ/DLT behavior khi schema invalid.

---

## 17. Privacy và security trong event contract

Event thường tồn tại lâu hơn request và có nhiều consumer hơn. Vì vậy event có thể làm dữ liệu nhạy cảm lan rộng.

Không đưa vào event nếu không cần:

- Password.
- Access token.
- Refresh token.
- Secret key.
- Full payment card number.
- CVV.
- Session cookie.
- Raw authentication header.

PII cần đánh classification:

~~~text
customerId: internal identifier
email: personal data
emailHash: pseudonymous, vẫn cần policy
address: sensitive personal data
paymentMethodToken: restricted
~~~

Một event catalog nên có:

- privacy classification.
- retention.
- encryption requirement.
- access control.
- masking rule trong log.
- consumer được phép đọc.
- data subject deletion strategy.

Hash không tự động làm dữ liệu vô hại. Email hash có thể bị dò nếu domain nhỏ hoặc attacker biết candidate set.

Khi có yêu cầu xóa dữ liệu:

- Kafka retention không phải cơ chế xóa tức thời.
- Compaction không phải privacy erasure guarantee.
- Cần policy riêng cho PII trong event.
- Có thể phát event redaction hoặc dùng tokenization.
- Cần biết bản sao trong DLQ, archive và downstream storage.

Schema review phải có privacy review, không chỉ compile pass.

---

## 18. Event catalog

Mỗi event nên có một trang catalog hoặc entry tương đương:

~~~text
Event name: com.example.order.created
Owner: Order Platform Team
Purpose: thông báo order đã commit thành công
Topic: domain.order.events
Key: orderId
Schema subject: orders.OrderCreated-value
Current schema: v2
Compatibility: FULL
Consumers: payment, inventory, notification, analytics
Retention: 14 days
Replay: supported
PII: customerId, shippingAddress
Security: internal restricted
Producer SLA: publish within 5 seconds after commit
Deprecation: v1 đọc được đến 2027-03-01
~~~

Catalog giúp trả lời khi incident:

- Ai chịu trách nhiệm schema?
- Có consumer nào còn dùng v1?
- Nếu thêm field, ai cần review?
- Có được replay event không?
- Event có PII không?
- Topic retention bao lâu?
- Có thể đổi field này không?

Không để schema nằm trong một repository mà không có owner và documentation.

---

## 19. Quy trình review schema

### Bước 1: Mô tả fact

Viết câu:

~~~text
OrderCreated means an order has been persisted and is ready for downstream processing.
~~~

Nếu không viết được một câu semantics rõ, chưa nên chốt schema.

### Bước 2: Xác định owner và consumer

- Producer owner.
- Consumer hiện tại.
- Consumer dự kiến.
- Team ngoài tổ chức.
- Replay hoặc data platform consumer.

### Bước 3: Chọn key và identity

- Partition key hoặc message key.
- Event id.
- Aggregate id.
- Correlation id.
- Causation id.

### Bước 4: Định nghĩa field semantics

Mỗi field cần:

- Type.
- Required/optional.
- Unit.
- Range.
- Null behavior.
- Example.
- Privacy classification.
- Deprecated policy.

### Bước 5: Chọn compatibility

- Backward, forward hay full.
- Thời gian giữ version cũ.
- Rollout order.
- Tín hiệu hoàn thành migration.

### Bước 6: Viết negative cases

- Thiếu required field.
- Sai type.
- Sai enum.
- Sai currency.
- Amount âm.
- Unknown field.
- Null ở field không nullable.
- Event timestamp tương lai quá xa.
- Schema id không tồn tại.

### Bước 7: Test hai chiều

~~~text
old producer -> new consumer
new producer -> old consumer
old event in retention -> new consumer
new event -> all active consumers
~~~

### Bước 8: Review privacy

- Có dữ liệu không cần thiết không?
- Log có làm lộ data không?
- DLQ có policy không?
- Retention có phù hợp không?

---

## 20. Bài tập chính: OrderCreated v1 và v2

### 20.1 OrderCreated v1

Thiết kế v1:

~~~json
{
  "eventId": "evt-100",
  "eventType": "OrderCreated",
  "eventVersion": 1,
  "occurredAt": "2026-09-26T10:15:30Z",
  "orderId": "order-123",
  "customerId": "customer-9",
  "currency": "VND",
  "totalMinor": 1250000,
  "items": [
    {
      "sku": "SKU-1",
      "quantity": 2,
      "unitPriceMinor": 500000
    }
  ]
}
~~~

Contract:

- eventId là duy nhất.
- occurredAt là UTC.
- totalMinor là tổng tiền sau discount và trước payment capture.
- currency dùng ISO 4217 uppercase.
- items không được rỗng.
- quantity là số nguyên dương.
- unitPriceMinor là đơn vị tiền nhỏ nhất.
- Không có payment token trong event.

### 20.2 OrderCreated v2

Thêm:

- discountMinor.
- customerSegment.
- shippingFeeMinor.

Schema v2 có thể là:

~~~json
{
  "eventId": "evt-101",
  "eventType": "OrderCreated",
  "eventVersion": 2,
  "occurredAt": "2026-09-26T10:16:30Z",
  "orderId": "order-124",
  "customerId": "customer-10",
  "currency": "VND",
  "totalMinor": 1450000,
  "discountMinor": 100000,
  "shippingFeeMinor": 50000,
  "customerSegment": "STANDARD",
  "items": [
    {
      "sku": "SKU-2",
      "quantity": 1,
      "unitPriceMinor": 1500000
    }
  ]
}
~~~

Policy:

- Các field v2 mới là optional khi consumer đọc v1.
- Consumer cũ phải bỏ qua field mới.
- Consumer mới coi discountMinor vắng mặt là 0 chỉ khi semantics v1 xác nhận totalMinor đã chứa discount.
- shippingFeeMinor vắng mặt không được tự động coi là 0 nếu v1 không định nghĩa shipping.
- customerSegment vắng mặt là UNKNOWN, không phải STANDARD.
- Không đổi ý nghĩa totalMinor giữa v1 và v2.
- Không đổi eventType thành một event có semantics khác.

### 20.3 Consumer cũ đọc event mới

Pseudo-code:

~~~text
parse envelope
validate fields required bởi v1
ignore fields không biết
map totalMinor, currency, items
không dùng customerSegment nếu code chưa hỗ trợ
xử lý business operation idempotent theo eventId
commit offset sau side effect
~~~

Consumer cũ chỉ an toàn nếu:

- Validator không reject unknown fields.
- Deserializer không fail vì field mới.
- Business mapper không yêu cầu exact field set.
- Enum cũ có fallback.
- Contract test đã chứng minh behavior.

### 20.4 Consumer mới đọc event cũ

Pseudo-code:

~~~text
parse envelope
nếu discountMinor missing:
    discountMinor = 0

nếu customerSegment missing:
    customerSegment = UNKNOWN

nếu shippingFeeMinor missing:
    không suy đoán trừ khi contract định nghĩa default hợp lệ

xử lý theo capability của record
~~~

### 20.5 Migration plan

Giai đoạn 1:

- Publish schema v2 nhưng chỉ thêm optional fields.
- Giữ consumer cũ chạy.
- Kiểm tra producer không gửi field sai semantics.
- Đo số consumer đã đọc v2.

Giai đoạn 2:

- Deploy consumer mới có thể đọc v1 và v2.
- Chạy replay một tập event v1.
- So sánh business result.
- Theo dõi validation failure và unknown enum.

Giai đoạn 3:

- Producer bắt đầu gửi field v2.
- Consumer cũ vẫn phải bỏ qua field mới.
- Theo dõi consumer lag, DLQ và side effect.

Giai đoạn 4:

- Khi mọi consumer đã migrate, đánh dấu v1 deprecated.
- Không xóa khả năng đọc v1 trước khi retention cũ hết hoặc archive được xử lý.
- Cập nhật catalog và runbook.

Giai đoạn 5:

- Nếu cần breaking semantic change, tạo event type mới hoặc major version mới.
- Không ép migration bằng cách đổi nghĩa field v1.

---

## 21. Lab 1: Compatibility matrix

### Mục tiêu

Chứng minh consumer cũ và mới đọc được event của nhau theo policy.

### Chuẩn bị

Tạo bốn artifact:

~~~text
producer-v1
producer-v2
consumer-v1
consumer-v2
~~~

### Test matrix

| Writer | Reader | Kỳ vọng |
|---|---|---|
| v1 | v1 | Pass |
| v1 | v2 | Pass nếu v2 có default/optional đúng |
| v2 | v1 | Pass nếu v1 tolerate field mới |
| v2 | v2 | Pass |

### Test cases

- Event v1 bình thường.
- Event v1 thiếu optional field.
- Event v2 có field mới.
- Event có unknown field.
- Event null tại field optional.
- Event enum value mới.
- Event amount âm.
- Event thiếu required field.
- Event sai schema version.

### Deliverable

Viết bảng:

~~~text
case
writer schema
reader schema
valid/invalid
error type
business behavior
compatibility result
~~~

Không chỉ kiểm tra deserialize. Một event deserialize được nhưng business mapping sai vẫn là failure.

---

## 22. Lab 2: JSON Schema validation

### Mục tiêu

Đo sự khác nhau giữa schema mở và schema đóng.

### Thử nghiệm

Schema A:

~~~json
{
  "type": "object",
  "required": ["orderId"],
  "properties": {
    "orderId": { "type": "string" }
  },
  "additionalProperties": true
}
~~~

Schema B:

~~~json
{
  "type": "object",
  "required": ["orderId"],
  "properties": {
    "orderId": { "type": "string" }
  },
  "additionalProperties": false
}
~~~

Payload:

~~~json
{
  "orderId": "order-1",
  "newField": "introduced-by-v2"
}
~~~

Quan sát:

- Schema A có cho qua không?
- Schema B có reject không?
- Consumer cũ nên dùng schema nào trong rolling deployment?
- Producer có cần schema strict riêng không?
- Có thể dùng schema strict ở producer và tolerant ở consumer không?

Kết luận nên phân biệt:

- Producer validation: thường strict để không phát dữ liệu sai.
- Consumer parsing: thường tolerant với field mới nếu policy yêu cầu forward compatibility.
- Business validation: có thể strict ở field consumer thực sự cần.

---

## 23. Lab 3: Enum evolution

### Mục tiêu

Kiểm tra consumer khi producer thêm enum value.

### v1

~~~text
STANDARD
PREMIUM
~~~

### v2

~~~text
STANDARD
PREMIUM
ENTERPRISE
~~~

### Consumer variants

- Consumer reject enum lạ.
- Consumer map enum lạ về UNKNOWN.
- Consumer có default branch.
- Consumer dùng enum lạ để trigger side effect.

### Câu hỏi

1. Consumer nào an toàn nhất?
2. Khi nào map về UNKNOWN làm mất business signal?
3. Có nên publish event mới thay vì thêm enum không?
4. Alert nào cần phát khi UNKNOWN tăng?
5. Nếu ENTERPRISE yêu cầu policy hoàn toàn khác, có nên tách event không?

---

## 24. Lab 4: Schema Registry workflow

### Mục tiêu

Mô phỏng quy trình publish schema có compatibility check.

### Các bước

1. Publish schema v1.
2. Đặt policy backward hoặc full.
3. Thử thêm optional field.
4. Thử thêm required field không default.
5. Thử đổi type.
6. Thử xóa field.
7. Thử đổi event subject.
8. Ghi lại registry accept hoặc reject.
9. Với change bị reject, viết migration alternative.

### Deliverable

Bảng:

| Change | Registry result | Runtime risk | Alternative |
|---|---|---|---|
| Add optional field | Accept | Thấp | Giữ |
| Add required field | Reject hoặc risk | Cao | Optional trước |
| Rename field | Có thể accept tùy policy | Cao | Add new, deprecate old |
| Change meaning | Có thể không phát hiện | Rất cao | Event type mới |
| Add enum | Tùy policy | Vừa | UNKNOWN branch |

Lab này giúp thấy registry chỉ kiểm tra rule cấu trúc, không hiểu đầy đủ domain semantics.

---

## 25. Lab 5: Replay và migration

### Mục tiêu

Đảm bảo consumer mới đọc được dữ liệu cũ trong retention.

### Thiết kế

- Tạo một tập event v1.
- Lưu vào topic hoặc fixture.
- Deploy consumer v2.
- Replay event v1.
- So sánh output với consumer v1.
- Kiểm tra default field và side effect idempotency.

### Kiểm soát side effect

Không replay trực tiếp vào production side effect nếu chưa có:

- dry-run mode.
- idempotency key.
- output topic riêng.
- database transaction hoặc sandbox.
- giới hạn tốc độ.
- audit log.
- rollback hoặc compensation plan.

Replay là một operation có thể tạo payment, email hoặc inventory side effect lần nữa nếu thiết kế không cẩn thận.

---

## 26. Consumer-driven contract test

Producer không thể tự biết mọi consumer cần gì. Consumer-driven contract test giúp ghi lại expectation của consumer.

Consumer contract nên kiểm tra:

- Event type.
- Required field.
- Field type.
- Enum behavior.
- Default behavior.
- Unknown field behavior.
- Business invariant.
- Error behavior.

Producer pipeline nên chạy:

~~~text
schema lint
-> schema compatibility check
-> producer serialization test
-> consumer contract test
-> fixture replay test
-> privacy scan
-> publish artifact
~~~

Một contract test tốt không assert toàn bộ JSON byte-for-byte nếu field mới được phép thêm. Nên assert các field và semantics consumer thực sự cần.

Ví dụ:

~~~text
assert event.type == OrderCreated
assert data.orderId exists
assert data.totalMinor >= 0
assert missing discountMinor behaves as zero
assert unknown fields do not break parsing
~~~

Nếu consumer thật sự cần exact shape, đó là decision phải document vì nó làm compatibility khó hơn.

---

## 27. Schema error handling

Có nhiều loại lỗi khác nhau:

### Lỗi deserialize

Payload không thể parse theo format.

Xử lý:

- Không retry vô hạn.
- Ghi metadata an toàn.
- Đưa vào DLT/DLQ nếu có thể.
- Alert producer owner.
- Giữ raw payload theo privacy policy, có thể phải redact.

### Lỗi validation

Payload parse được nhưng không thỏa schema.

Xử lý:

- Phân biệt lỗi producer và lỗi dữ liệu cũ.
- Không commit như xử lý thành công nếu cần điều tra.
- Có quarantine hoặc DLT.
- Ghi schema id/version.
- Không log secret hoặc PII.

### Lỗi business

Schema hợp lệ nhưng rule nghiệp vụ sai.

Ví dụ:

- order total không khớp items.
- customer không tồn tại.
- event đến sai lifecycle.
- currency không được hỗ trợ.

Xử lý:

- Có thể retry nếu downstream tạm thời lỗi.
- Không retry vô hạn lỗi bất biến.
- DLT cần giữ lý do và metadata.
- Có runbook xử lý thủ công hoặc replay sau fix.

Schema validation không thay thế business validation.

---

## 28. Event semantics và immutable fact

Event nên mô tả fact đã xảy ra, nhưng fact có thể cần correction.

Không sửa event cũ bằng cách publish lại cùng event id với payload khác. Cách đó gây khó cho:

- Deduplication.
- Audit.
- Replay.
- Cache.
- Consumer state.

Thay vào đó:

~~~text
OrderCreated
OrderTotalCorrected
OrderCancelled
CustomerDataRedacted
~~~

Nếu event cũ phát sai:

- Ghi incident.
- Xác định consumer đã nhận gì.
- Phát correction event.
- Dùng causationId trỏ về event lỗi.
- Tạo migration hoặc replay có kiểm soát.
- Không âm thầm đổi record lịch sử.

Event version dùng để thay đổi contract có kiểm soát, không phải để sửa lịch sử.

---

## 29. Naming policy

Tên event nên:

- Có namespace.
- Nói rõ domain và fact.
- Không phụ thuộc tên class Java.
- Không phụ thuộc tên table.
- Không dùng tên quá chung.
- Không tái sử dụng cho semantics khác.

Ví dụ tốt:

~~~text
com.example.order.created
com.example.payment.captured
com.example.inventory.reserved
~~~

Ví dụ cần xem lại:

~~~text
OrderMessage
OrderData
OrderUpdate
StatusChanged
Event1
~~~

OrderUpdated mơ hồ vì không biết field nào đổi và consumer cần làm gì. Có thể tốt hơn:

~~~text
OrderAddressChanged
OrderPaymentMethodChanged
OrderCancelled
~~~

Nếu domain thực sự cần generic update, schema phải có changedFields, revision và semantics rõ. Nhưng generic update thường làm consumer khó maintain.

---

## 30. Checklist review một event schema

### Identity

- [ ] Event type có namespace.
- [ ] Event id duy nhất.
- [ ] Aggregate key rõ.
- [ ] Correlation id và causation id có mục đích.
- [ ] Timestamp là UTC và có semantics.

### Payload

- [ ] Tên field mô tả đúng semantics.
- [ ] Type không mơ hồ.
- [ ] Unit được document.
- [ ] Currency được document.
- [ ] Required và optional rõ.
- [ ] Null và missing khác nhau thế nào được document.
- [ ] Enum có UNKNOWN/default strategy.
- [ ] Không có secret hoặc dữ liệu thừa.

### Compatibility

- [ ] Đã chọn backward, forward hay full.
- [ ] Có test old writer -> new reader.
- [ ] Có test new writer -> old reader nếu cần.
- [ ] Field mới có default hoặc optional hợp lệ.
- [ ] Không đổi nghĩa field cũ.
- [ ] Không reuse field number trong Protobuf.
- [ ] Có migration plan cho rename hoặc delete.

### Operations

- [ ] Có owner.
- [ ] Có Schema Registry subject hoặc schema artifact.
- [ ] Có retention.
- [ ] Có privacy classification.
- [ ] Có DLT/DLQ policy.
- [ ] Có replay plan.
- [ ] Có dashboard lỗi schema.
- [ ] Có runbook khi consumer không đọc được event.

---

## 31. Bài tập mở rộng

### Bài 1: Thiết kế envelope

Thiết kế envelope cho PaymentCaptured, gồm:

- event id.
- payment id.
- order id.
- amount.
- currency.
- payment provider.
- captured at.
- correlation id.
- causation id.
- schema version.
- privacy classification.

Giải thích vì sao không đưa payment token vào payload.

### Bài 2: Phân loại message

Phân loại các message sau thành command, event hoặc notification:

~~~text
CreateOrder
OrderCreated
ReserveInventory
InventoryReserved
CustomerEmailRequested
SendPaymentReminder
PaymentFailed
~~~

Với mỗi message, giải thích ai là owner và có nên replay không.

### Bài 3: Compatibility review

Một team muốn:

- Đổi totalMinor từ integer sang decimal string.
- Đổi customerId thành customer.
- Thêm field required shippingAddress.
- Xóa enum CANCELLED.
- Dùng field status cho semantics mới.

Review từng thay đổi và đề xuất migration.

### Bài 4: Privacy review

Event có:

~~~text
customerEmail
fullAddress
paymentMethodToken
ipAddress
orderId
~~~

Phân loại field nào cần thiết, field nào nên bỏ, field nào cần masking hoặc tokenization.

### Bài 5: Consumer cũ

Viết pseudo-code consumer v1 đọc OrderCreated v2. Consumer không được fail khi có:

- discountMinor.
- shippingFeeMinor.
- customerSegment.
- enum mới.
- unknown metadata.

### Bài 6: Consumer mới

Viết pseudo-code consumer v2 đọc cả v1 và v2. Nêu rõ default nào hợp lệ và default nào không được tự suy đoán.

### Bài 7: Schema Registry policy

Chọn policy cho:

- Internal command topic.
- Public domain event topic.
- Audit topic retention 1 năm.
- Temporary pipeline topic.
- Topic chỉ producer và consumer deploy atomically.

### Bài 8: Event correction

OrderCreated đã phát sai totalMinor. Thiết kế correction event và quy trình replay mà không tạo duplicate payment.

---

## 32. Câu hỏi tự kiểm tra

1. Command khác event ở intent và thời điểm như thế nào?
2. Notification có luôn là domain event không?
3. Event envelope cần correlationId và causationId để làm gì?
4. Event id khác aggregate id thế nào?
5. Backward compatibility là hướng reader/writer nào?
6. Forward compatibility là hướng nào?
7. Thêm required field không default phá những flow nào?
8. Optional khác nullable thế nào?
9. Vì sao thêm enum value vẫn có thể phá consumer?
10. Khi nào nên tạo event type mới?
11. JSON Schema additionalProperties=true và false có trade-off gì?
12. Avro writer schema và reader schema khác nhau ra sao?
13. Vì sao Protobuf field number không được reuse?
14. Schema Registry kiểm tra được gì và không kiểm tra được gì?
15. Vì sao schema valid chưa có nghĩa event có business semantics đúng?
16. Vì sao replay có thể tạo duplicate side effect?
17. Event có PII cần policy gì ngoài validation?
18. Khi nào dùng full compatibility?
19. Vì sao không nên đổi ý nghĩa field cũ?
20. Consumer-driven contract test nên assert điều gì?

Nếu chưa trả lời rõ câu 5, 6, 8, 9, 13, 14 và 16, hãy làm lại compatibility matrix và replay lab.

---

## 33. Checklist hoàn thành tuần

- [ ] Phân biệt command, event và notification.
- [ ] Thiết kế được event envelope.
- [ ] Dùng correlationId và causationId đúng mục đích.
- [ ] Viết được backward và forward compatibility example.
- [ ] Thiết kế OrderCreated v1 và v2.
- [ ] Viết consumer cũ đọc event mới.
- [ ] Viết consumer mới đọc event cũ.
- [ ] Test optional field và default.
- [ ] Test enum evolution.
- [ ] Validate JSON Schema.
- [ ] Hiểu writer/reader schema của Avro.
- [ ] Hiểu field number và reserved của Protobuf.
- [ ] Mô tả vai trò Schema Registry.
- [ ] Có event catalog entry.
- [ ] Có privacy classification.
- [ ] Có migration plan và deprecation date.
- [ ] Chạy replay fixture mà không tạo duplicate side effect.
- [ ] Viết contract test và negative cases.

---

## 34. Kết luận

Event schema là ranh giới hợp tác giữa các service. Một schema tốt không chỉ giúp deserialize thành công mà còn bảo vệ semantics, rollout, replay, tracing, privacy và khả năng vận hành lâu dài.

Policy thực dụng cho hệ thống bắt đầu từ:

- Event type có namespace và semantics rõ.
- Envelope có event id, source, time, subject, correlation id và causation id.
- Field mới ưu tiên optional trước khi trở thành required.
- Không đổi nghĩa field cũ.
- Enum có UNKNOWN/default branch.
- Consumer chịu trách nhiệm tolerate thay đổi được phép.
- Producer validate strict trước khi publish.
- Schema Registry kiểm tra compatibility nhưng không thay thế domain review.
- Mọi event có owner, retention và privacy classification.
- Breaking change dùng event type mới hoặc migration có kiểm soát.
- Replay phải bảo vệ side effect bằng idempotency.
- Contract test phải bao phủ cả schema và business behavior.

Mục tiêu cuối cùng không phải giữ schema bất biến mãi mãi. Mục tiêu là cho phép schema tiến hóa mà không làm consumer cũ, replay pipeline và business side effect trở nên không kiểm soát.

---

## 35. Tài liệu tham khảo chính thức

- JSON Schema object validation: https://json-schema.org/understanding-json-schema/reference/object
- JSON Schema schema and dialect: https://json-schema.org/understanding-json-schema/reference/schema
- Apache Avro specification và schema resolution: https://avro.apache.org/docs/1.12.0/specification/
- Protocol Buffers updating message types: https://protobuf.dev/programming-guides/proto3/
- Protocol Buffers language guide: https://protobuf.dev/programming-guides/proto3/


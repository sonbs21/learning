# Tuần 10 — Message contract và Schema Registry

> Mục tiêu: thiết kế message contract có version, quản lý schema tập trung và thay đổi payload mà không làm hỏng consumer đang chạy.

## 1. Kết quả cần đạt

Sau bài học, bạn có thể:

- Giải thích message contract và vì sao producer/consumer phải cùng hiểu contract.
- So sánh JSON, Avro và Protobuf.
- Phân biệt schema, subject, version và schema ID.
- Hiểu Schema Registry là service riêng, không nằm trong broker.
- Chọn compatibility mode phù hợp.
- Phân biệt TopicNameStrategy, RecordNameStrategy và TopicRecordNameStrategy.
- Thêm optional field/default mà consumer cũ vẫn đọc được.
- Nhận diện breaking change với required field, enum và type.
- Quản lý schema owner, catalog, PII và retention.
- Viết contract test chạy trong CI.
- Thiết kế migration nhiều bước cho producer và consumer.
- Kiểm tra schema ID/subject bằng API và trong message metadata.

## 2. Message contract là gì?

Message contract là thỏa thuận giữa producer và consumer về:

- event type;
- key;
- value structure;
- field name và data type;
- required/optional;
- default value;
- enum;
- timestamp/format;
- headers;
- version;
- compatibility;
- ownership;
- error behavior.

Ví dụ envelope:

    {
      "eventId": "uuid",
      "eventType": "order.created",
      "eventVersion": 1,
      "occurredAt": "2026-10-02T10:00:00Z",
      "correlationId": "uuid",
      "causationId": "uuid",
      "producer": "order-service",
      "payload": {
        "orderId": "ORD-1001",
        "customerId": "CUS-10",
        "total": 125000
      }
    }

Contract không chỉ là danh sách field. Nó còn nói:

- event đã xảy ra hay command;
- field nào là định danh;
- đơn vị tiền là gì;
- timestamp là UTC hay local;
- total có thể âm không;
- consumer được phép bỏ qua field lạ không;
- schema thay đổi theo quy trình nào.

## 3. Vì sao contract quan trọng?

### 3.1 Producer và consumer deploy độc lập

Producer có thể deploy trước consumer hoặc ngược lại. Nếu đổi field không tương thích:

    producer version mới -> payload mới
    consumer version cũ -> deserialize/failure

Contract và compatibility giúp rollout theo nhiều giai đoạn.

### 3.2 Nhiều ngôn ngữ

Order Service có thể là Java, Analytics là Python, Data Platform là Go. Không nên để Java class name hoặc JPA entity trở thành contract liên ngôn ngữ.

### 3.3 Replay lịch sử

Schema hôm nay phải có cách đọc record cũ. Nếu schema thay đổi không kiểm soát, replay một topic retention 30 ngày có thể fail.

### 3.4 Governance và ownership

Khi một field bị đổi, cần biết:

- ai sở hữu schema;
- ai là consumer;
- consumer nào bị ảnh hưởng;
- compatibility mode nào áp dụng;
- migration deadline;
- cách rollback.

## 4. JSON, Avro và Protobuf

### 4.1 JSON

JSON dễ đọc:

    {
      "orderId": "ORD-1001",
      "total": 125000
    }

Ưu điểm:

- dễ debug;
- phổ biến;
- hỗ trợ đa ngôn ngữ;
- phù hợp prototype và integration đơn giản.

Nhược điểm:

- payload lớn hơn binary;
- type enforcement yếu nếu không có schema;
- field rename/type change dễ gây lỗi runtime;
- schema drift nếu không có validation;
- không tự có schema ID/compatibility.

JSON Schema có thể thêm contract, nhưng vẫn cần quy trình registry/compatibility riêng.

### 4.2 Avro

Avro dùng schema để encode/decode binary:

    {
      "type": "record",
      "name": "OrderCreated",
      "namespace": "com.example.orders",
      "fields": [
        {"name": "eventId", "type": "string"},
        {"name": "orderId", "type": "string"},
        {"name": "total", "type": "long"}
      ]
    }

Ưu điểm:

- payload compact;
- schema evolution tốt;
- schema tách khỏi generated class;
- phù hợp Kafka ecosystem;
- có schema compatibility rõ.

Nhược điểm:

- cần registry/serde toolchain;
- debug binary khó hơn JSON;
- phải hiểu default, union và field rules;
- contract mismatch có thể gây lỗi khi deploy.

### 4.3 Protobuf

Protobuf dùng message definition và field number:

    message OrderCreated {
      string event_id = 1;
      string order_id = 2;
      int64 total = 3;
    }

Ưu điểm:

- binary nhỏ;
- generated code mạnh;
- field number hỗ trợ evolution;
- đa ngôn ngữ tốt;
- type rõ.

Nhược điểm:

- debug cần tooling;
- không được tái sử dụng field number tùy tiện;
- enum/oneof/optional cần quy tắc;
- schema governance quan trọng.

### 4.4 So sánh

| Tiêu chí | JSON | Avro | Protobuf |
|---|---|---|---|
| Dễ đọc bằng mắt | Rất tốt | Schema dễ đọc, payload binary | Cần tooling |
| Payload | Lớn hơn | Nhỏ | Nhỏ |
| Type safety | Thấp nếu không schema | Cao | Cao |
| Evolution | Phải tự quản lý | Mạnh với compatibility | Mạnh với field number |
| Đa ngôn ngữ | Tốt | Tốt | Rất tốt |
| Prototype | Rất nhanh | Vừa | Vừa |
| Production contract | Cần JSON Schema | Phù hợp | Phù hợp |

Không có format đúng tuyệt đối. Chọn dựa trên team, ecosystem, latency, contract governance và nhu cầu evolution.

## 5. Schema Registry là gì?

Schema Registry là service quản lý schema và compatibility ngoài Kafka broker.

Mô hình:

    Producer
       |
       +--> Schema Registry: đăng ký/lookup schema
       |
       +--> Kafka broker: gửi payload

    Consumer
       |
       +--> Schema Registry: lấy schema
       |
       +--> Kafka broker: đọc payload

Registry thường cung cấp:

- lưu schema version;
- subject;
- schema ID;
- compatibility check;
- REST API;
- serializer/deserializer integration;
- quản lý metadata.

### 5.1 Broker không tự kiểm tra compatibility

Kafka broker chủ yếu lưu bytes và metadata record. Broker không tự biết field total có đổi từ long sang string có phá consumer hay không.

Schema Registry hoặc contract tooling đảm nhiệm:

- validate producer schema;
- từ chối incompatible schema;
- map schema ID;
- giúp consumer decode.

### 5.2 Registry không thay thế business documentation

Schema chỉ mô tả cấu trúc. Nó không tự giải thích:

- total là VND hay USD;
- event xảy ra lúc nào;
- orderId có thể reuse không;
- consumer phải retry thế nào;
- event có chứa PII không;
- side effect nào được phép.

Cần schema catalog và tài liệu domain.

## 6. Subject, schema ID và version

### 6.1 Subject

Subject là tên logic mà registry dùng để quản lý tập schema evolution. Cách subject được tạo phụ thuộc naming strategy.

Ví dụ:

    orders-value

hoặc:

    com.example.orders.OrderCreated

Một subject có thể có nhiều schema version:

    subject orders-value
      version 1 -> fields cũ
      version 2 -> thêm customerEmail optional
      version 3 -> thay đổi khác

### 6.2 Schema version

Schema version là thứ tự schema trong một subject. Version không nhất thiết giống eventVersion trong envelope.

- schema version: version do registry quản lý;
- eventVersion: version business do domain định nghĩa.

Có thể liên kết hai khái niệm, nhưng không nên nhầm chúng là một.

### 6.3 Schema ID

Schema ID là định danh schema trong registry. Serializer thường đặt schema ID trong payload/header/format encoding để consumer lookup schema.

Schema ID không phải:

- Kafka offset;
- eventId;
- topic version;
- business event version.

Một event có thể được gửi nhiều lần với cùng schema ID nhưng eventId khác nhau.

## 7. Naming strategies

### 7.1 TopicNameStrategy

Mỗi topic thường có subject cho key/value:

    orders-value
    orders-key

Ưu điểm:

- đơn giản;
- dễ quản lý khi mỗi topic chỉ chứa một loại record;
- phù hợp topic contract chặt.

Nhược điểm:

- nhiều event type trong cùng topic dễ conflict;
- schema compatibility áp dụng chung cho topic.

### 7.2 RecordNameStrategy

Subject dựa trên full record name:

    com.example.orders.OrderCreated
    com.example.orders.PaymentAuthorized

Ưu điểm:

- một record type có thể xuất hiện ở nhiều topic;
- schema evolution theo record.

Nhược điểm:

- consumer/operator phải hiểu record name;
- topic có thể chứa nhiều type;
- governance phức tạp hơn.

### 7.3 TopicRecordNameStrategy

Subject kết hợp topic và record name:

    orders-com.example.orders.OrderCreated
    audit-com.example.orders.OrderCreated

Ưu điểm:

- schema của cùng record ở các topic khác nhau độc lập;
- tránh conflict giữa topic.

Nhược điểm:

- số subject tăng;
- cần convention và catalog tốt.

### 7.4 Chọn strategy

| Tình huống | Strategy có thể phù hợp |
|---|---|
| Một event type/topic | TopicNameStrategy |
| Record type dùng nhiều topic | RecordNameStrategy |
| Cần độc lập theo topic và record | TopicRecordNameStrategy |
| Topic chứa nhiều event type | Cẩn thận, cần governance |

Không đổi naming strategy giữa các producer mà không có migration plan.

## 8. Compatibility modes

Compatibility quyết định schema mới có thể đọc/ghi với schema cũ thế nào.

### 8.1 Backward

Schema mới có thể đọc dữ liệu được ghi bằng schema cũ.

Hữu ích khi deploy consumer mới trước producer mới.

    consumer mới + data cũ

### 8.2 Forward

Schema cũ có thể đọc dữ liệu được ghi bằng schema mới.

Hữu ích khi deploy producer mới trước consumer mới.

    consumer cũ + data mới

### 8.3 Full

Schema mới tương thích cả chiều backward và forward trong phạm vi registry rule.

Hữu ích khi producer/consumer rollout độc lập và cần compatibility hai chiều.

### 8.4 None

Không kiểm tra compatibility. Chỉ dùng khi có governance riêng hoặc migration đặc biệt. Không nên chọn mặc định production.

### 8.5 Transitive

Một số registry hỗ trợ kiểm tra schema mới với toàn bộ lịch sử thay vì chỉ version ngay trước đó. Transitive hữu ích khi topic có nhiều version và cần bảo vệ replay cũ.

Compatibility không thay thế integration test. Một schema có thể hợp lệ về cấu trúc nhưng phá business rule.

## 9. Schema evolution an toàn

### 9.1 Thêm optional field có default

Ví dụ schema cũ:

    OrderCreated {
      eventId: string
      orderId: string
      total: long
    }

Schema mới:

    OrderCreated {
      eventId: string
      orderId: string
      total: long
      customerEmail: optional string
    }

Consumer cũ bỏ qua field mới. Consumer mới dùng default/null khi đọc record cũ.

Đây thường là thay đổi an toàn nếu serializer/compatibility mode hỗ trợ đúng.

### 9.2 Thêm required field không default

Schema mới bắt buộc customerTier nhưng record cũ không có field:

    customerTier: string required

Consumer mới đọc data cũ có thể không biết giá trị. Đây thường là breaking change hoặc cần migration nhiều bước.

### 9.3 Đổi type

    total: long -> string

Có thể phá consumer, query, serialization và business calculation. Không đổi type tùy tiện. Tạo event version/topic mới nếu không có compatibility path rõ.

### 9.4 Rename field

Rename có thể được hiểu là xóa field cũ và thêm field mới. Consumer cũ/mới có thể không tương thích. Cân nhắc:

1. thêm field mới optional;
2. producer ghi cả cũ và mới trong giai đoạn migration;
3. consumer đọc ưu tiên field mới, fallback field cũ;
4. deprecate field cũ;
5. xóa ở version sau.

### 9.5 Xóa field

Field cũ có thể vẫn cần cho consumer chưa migrate. Thực hiện deprecation trước khi xóa.

### 9.6 Enum evolution

Enum thêm giá trị mới có thể làm consumer cũ fail nếu consumer không có unknown/default handling.

Quy tắc:

- không giả định enum closed world nếu consumer cũ còn chạy;
- có UNKNOWN/UNSPECIFIED khi phù hợp;
- test consumer cũ với enum value mới;
- không tái sử dụng enum meaning cũ.

### 9.7 Field number trong Protobuf

Không tái sử dụng field number đã xóa. Reserve field number/name nếu tool hỗ trợ. Thay đổi type field number cần compatibility analysis.

### 9.8 Avro union và default

Avro optional field thường cần union và default đúng quy tắc. Thứ tự union/default có thể ảnh hưởng compatibility. Không chỉ nhìn JSON schema bề ngoài; chạy registry compatibility test.

## 10. Envelope chuẩn

Một envelope gợi ý:

    {
      "eventId": "uuid",
      "eventType": "order.created",
      "eventVersion": 1,
      "occurredAt": "2026-10-02T10:00:00Z",
      "correlationId": "uuid",
      "causationId": "uuid",
      "producer": "order-service",
      "payload": {
        "orderId": "ORD-1001",
        "customerId": "CUS-10",
        "total": 125000
      }
    }

### 10.1 eventId

Dùng dedup và truy vết. Nên ổn định qua retry cùng logical event.

### 10.2 eventType

Dùng để route/deserialize và phân biệt business fact. Convention nên thống nhất, ví dụ order.created.

### 10.3 eventVersion

Dùng cho business migration nếu cần. Không dùng version để né schema compatibility.

### 10.4 occurredAt

Lưu thời điểm event xảy ra, thường UTC. Có thể thêm producedAt/processedAt nếu cần latency.

### 10.5 correlationId và causationId

- correlationId: nối toàn workflow/request;
- causationId: event nào gây ra event hiện tại.

### 10.6 producer

Giúp trace owner, version và source service.

### 10.7 PII

Không đưa email, phone, address vào mọi event chỉ vì tiện. Xác định:

- field nào là PII;
- encryption/masking;
- retention;
- access control;
- log redaction;
- deletion policy;
- replay implications.

## 11. Schema Registry workflow

### Bước 1: define contract

Viết:

- event purpose;
- key;
- value;
- required/optional;
- semantics;
- examples;
- owner;
- compatibility mode.

### Bước 2: review

Owner và consumer review:

- field names/types;
- PII;
- ordering;
- null/default;
- enum;
- migration;
- retention.

### Bước 3: register schema

Registry kiểm tra schema mới với compatibility mode. Nếu fail, CI không cho merge/deploy.

### Bước 4: generate/use serializer

Producer dùng schema-aware serializer. Consumer dùng deserializer/reader schema phù hợp.

### Bước 5: deploy theo migration order

Ví dụ thêm optional field:

1. deploy consumer hiểu field mới nhưng vẫn chạy được data cũ;
2. register schema mới;
3. deploy producer ghi field mới;
4. quan sát consumer cũ;
5. deprecate field cũ sau thời gian.

### Bước 6: catalog và monitor

Ghi:

- subject;
- current version;
- schema ID;
- owner;
- consumers;
- compatibility;
- last used time;
- deprecation date.

## 12. Spring Kafka/Avro conceptual config

Nếu dùng Avro serializer:

    spring:
      kafka:
        properties:
          schema.registry.url: http://localhost:8081
        producer:
          value-serializer: io.confluent.kafka.serializers.KafkaAvroSerializer
        consumer:
          value-deserializer: io.confluent.kafka.serializers.KafkaAvroDeserializer
          properties:
            specific.avro.reader: true

Tên class và property phụ thuộc distribution/serializer vendor. Luôn pin dependency và kiểm tra license/configuration của stack.

Nếu dùng Protobuf, serializer/deserializer và generated message tương ứng. Không trộn JSON serializer với Avro bytes.

### 12.1 Schema Registry unavailable

Cần quyết định:

- producer fail closed hay dùng cache schema;
- consumer có đọc được schema đã cache không;
- startup có chờ registry health không;
- retry Registry request bao lâu;
- alert nào được phát.

Không để registry outage biến thành silent data corruption.

## 13. Contract tests

### 13.1 Producer contract test

Kiểm tra:

- payload serialize được;
- schema đăng ký được;
- compatibility pass;
- required field có dữ liệu;
- eventType/version đúng;
- key đúng;
- PII không lọt vào field/log cấm.

### 13.2 Consumer compatibility test

Dùng:

- schema cũ + consumer mới;
- schema mới + consumer cũ;
- record lịch sử representative;
- enum mới;
- missing optional/default;
- unknown field;
- malformed payload.

### 13.3 CI gate

CI có thể chạy:

1. validate schema format;
2. kiểm tra naming;
3. register/check compatibility trên registry test;
4. generate code;
5. serialize fixtures;
6. deserialize bằng consumer versions;
7. chạy integration test;
8. tạo schema diff report.

Không chỉ test class Java hiện tại; cần test contract giữa version.

## 14. Schema catalog

Mỗi event nên có entry:

| Field | Ví dụ |
|---|---|
| Event type | order.created |
| Topic | prod.orders.order-created.v1 |
| Key schema | string/orderId |
| Value subject | orders-value |
| Current schema version | 3 |
| Current schema ID | 42 |
| Compatibility | BACKWARD |
| Owner | order-team |
| Consumers | payment, inventory, analytics |
| PII | customerEmail |
| Retention | 14 days |
| Deprecation | none |

Catalog giúp trả lời nhanh khi muốn đổi field hoặc deprecate event.

## 15. Bài lab 1 — Đăng ký Avro hoặc Protobuf schema

1. Khởi động Schema Registry local.
2. Tạo schema OrderCreated v1.
3. Đăng ký subject.
4. Lấy schema ID/version.
5. Serialize một record.
6. Consume và deserialize.
7. Ghi schema catalog entry.
8. Kiểm tra API subject/version.

Schema v1 tối thiểu:

    eventId: string
    orderId: string
    customerId: string
    total: long

## 16. Bài lab 2 — Thêm optional field

1. Tạo schema v2 thêm customerEmail optional/default.
2. Kiểm tra backward compatibility.
3. Dùng consumer v2 đọc record v1.
4. Dùng consumer v1 đọc record v2.
5. Ghi kết quả.
6. Xác định field default/null.
7. Test producer rollout trước và sau consumer rollout.

Kết luận phải nói rõ compatibility mode đang dùng, không chỉ nói “chạy được”.

## 17. Bài lab 3 — Breaking change

Thử từng thay đổi:

- đổi total từ long sang string;
- xóa orderId;
- thêm required field không default;
- đổi enum;
- rename field;
- tái sử dụng Protobuf field number.

Quan sát:

- Registry accept/reject;
- serializer/consumer error;
- version nào bị ảnh hưởng;
- migration nào cần thiết.

Không sửa registry để “bỏ qua” breaking change nếu chưa có approval.

## 18. Bài lab 4 — Contract test trong CI

Tạo fixtures:

- event v1;
- event v2;
- event thiếu optional;
- event có enum value mới;
- malformed event.

Test:

1. producer serialize;
2. consumer v1 đọc fixture cũ;
3. consumer v2 đọc fixture cũ;
4. consumer v1 đọc fixture mới theo compatibility mong muốn;
5. schema registry check;
6. fail build khi breaking.

## 19. Bài lab 5 — Kiểm tra subject và schema ID bằng API

Các API thường có dạng:

    GET /subjects
    GET /subjects/{subject}/versions
    GET /schemas/ids/{id}

URL và endpoint cụ thể phụ thuộc Schema Registry implementation. Kiểm tra:

- subject list;
- version;
- schema text;
- schema ID;
- compatibility config;
- references nếu có.

Không đưa credential registry vào command history/log.

## 20. Bài lab 6 — Migration producer/consumer

Tình huống: thêm customerEmail.

### Giai đoạn 1

Consumer v1 vẫn chạy. Producer chưa gửi field mới.

### Giai đoạn 2

Deploy consumer v2 có thể đọc cả v1/v2.

### Giai đoạn 3

Register schema v2, deploy producer v2 ghi customerEmail khi có.

### Giai đoạn 4

Theo dõi consumer cũ và remove/deprecate sau cửa sổ migration.

Ghi lại rollback plan nếu producer v2 bị lỗi.

## 21. Compatibility matrix

Tạo bảng:

| Producer | Consumer | Data | Kỳ vọng |
|---|---|---|---|
| v1 | v1 | v1 | pass |
| v2 | v1 | v2 | tùy forward compatibility |
| v1 | v2 | v1 | tùy backward compatibility |
| v2 | v2 | v2 | pass |
| v3 breaking | v1 | v3 | fail có chủ đích |
| v3 breaking | v3 | v3 | pass sau migration |

Chạy matrix trong CI cho các schema change quan trọng.

## 22. Sai lầm phổ biến

### Nghĩ JSON tự có contract

JSON chỉ là format. Không có schema governance thì contract drift nhanh.

### Dùng Java class name làm contract

Refactor package có thể phá consumer. Contract nên độc lập ngôn ngữ.

### Đổi required field tùy tiện

Consumer đọc record cũ có thể không có field.

### Xóa field ngay sau deploy producer mới

Consumer cũ vẫn có thể đang chạy.

### Tái sử dụng Protobuf field number

Có thể làm dữ liệu mới bị decode theo nghĩa cũ.

### Chọn compatibility NONE vì registry từ chối

Registry từ chối có thể đang bảo vệ consumer. Phải thiết kế migration, không tắt guard mù.

### Dùng một subject cho nhiều event không cùng evolution

Một schema change của event A có thể chặn event B.

### Trust schema ID tuyệt đối

Schema ID chỉ có ý nghĩa trong registry/context phù hợp; migration registry cần kế hoạch.

### Không version topic khi breaking change

Một số breaking change nên tạo topic v2 thay vì ép consumer cũ.

### Bỏ PII vào schema không kiểm soát

Replay, DLT, logs và analytics có thể làm PII lan rộng.

## 23. Câu hỏi tự kiểm tra

1. Message contract gồm những gì?
2. Vì sao broker không tự kiểm tra schema compatibility?
3. JSON, Avro và Protobuf trade-off gì?
4. Schema Registry quản lý subject/version/schema ID thế nào?
5. Schema ID khác offset và eventId ra sao?
6. TopicNameStrategy, RecordNameStrategy và TopicRecordNameStrategy khác nhau thế nào?
7. Backward/forward/full compatibility dùng trong rollout nào?
8. Vì sao thêm optional field có default thường an toàn hơn required field?
9. Rename field có thể phá compatibility thế nào?
10. Vì sao không tái sử dụng Protobuf field number?
11. eventVersion khác schema version ra sao?
12. Vì sao không deserialize event vào JPA entity?
13. Khi nào cần topic v2?
14. Contract test nên kiểm tra producer/consumer nào?
15. Vì sao consumer cũ phải test với schema mới?
16. Schema Registry outage cần quyết định gì?
17. PII ảnh hưởng replay và retention ra sao?
18. Ai chịu trách nhiệm schema?

### Đáp án ngắn

1. Structure, semantics, key, version, headers, compatibility và ownership.
2. Broker chủ yếu lưu bytes; registry/tooling hiểu schema.
3. Dễ đọc, binary/schema evolution, type safety và tooling.
4. Subject chứa nhiều version; schema ID định danh schema.
5. Offset là vị trí Kafka; eventId là business identity; schema ID là contract identity.
6. Khác phạm vi subject: topic, record hoặc cả topic+record.
7. Quyết định consumer/producer rollout và chiều đọc dữ liệu.
8. Consumer mới đọc data cũ bằng default/null.
9. Có thể thành delete+add và consumer cũ không hiểu.
10. Old data có thể bị decode sai nghĩa.
11. Một là business envelope, một là registry evolution.
12. Tách transport contract khỏi persistence model.
13. Khi change không tương thích hoặc cần tách lifecycle.
14. Serialize, schema compatibility và deserialize nhiều version.
15. Đảm bảo forward/backward behavior thực tế.
16. Cache, fail-open/closed, retry, alert và startup behavior.
17. PII có thể tồn tại trong log/replay lâu hơn dự kiến.
18. Domain owner cùng platform/consumer stakeholders.

## 24. Checklist hoàn thành Tuần 10

- [ ] Tôi giải thích được message contract.
- [ ] Tôi so sánh được JSON, Avro và Protobuf.
- [ ] Tôi phân biệt subject, schema version và schema ID.
- [ ] Tôi hiểu Schema Registry là service riêng.
- [ ] Tôi hiểu TopicNameStrategy.
- [ ] Tôi hiểu RecordNameStrategy.
- [ ] Tôi hiểu TopicRecordNameStrategy.
- [ ] Tôi phân biệt backward, forward và full compatibility.
- [ ] Tôi thêm được optional field/default an toàn.
- [ ] Tôi nhận diện được breaking change.
- [ ] Tôi biết quy tắc enum và Protobuf field number.
- [ ] Tôi có schema catalog và owner.
- [ ] Tôi biết contract test trong CI.
- [ ] Tôi kiểm tra được schema ID/subject bằng API.
- [ ] Tôi có migration plan producer/consumer.
- [ ] Tôi đã đánh giá PII, retention và replay.
- [ ] Tôi không dùng JPA entity làm event contract.
- [ ] Tôi biết khi nào cần topic v2.
- [ ] Tôi có compatibility matrix cho capstone.

## 25. Câu chốt cần nhớ

Schema Registry không chỉ là nơi lưu schema. Nó là một guardrail để producer và consumer tiến hóa có kiểm soát trong một hệ thống deploy độc lập.

Năm ý cần nhớ:

1. Message contract bao gồm cả structure lẫn business semantics.
2. Schema ID, schema version và eventVersion là ba khái niệm khác nhau.
3. Compatibility phải được kiểm tra trước deploy, không đợi production consumer lỗi.
4. Thay đổi an toàn thường cần optional field/default và migration nhiều bước.
5. Schema owner, contract test, PII policy và replay plan quan trọng không kém schema file.


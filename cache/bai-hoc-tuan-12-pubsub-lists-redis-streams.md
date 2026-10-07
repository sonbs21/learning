# Tuần 12: Pub/Sub, Lists và Redis Streams

## 1. Mục tiêu

Sau bài này, bạn có thể:

- Phân biệt Redis Pub/Sub, Lists và Streams.
- Biết khi nào dùng fire-and-forget broadcast.
- Dùng List cho queue/feed đơn giản.
- Thêm và đọc event bằng XADD/XREAD.
- Tạo consumer group và consumer.
- Acknowledge bằng XACK.
- Theo dõi pending entries và xử lý consumer chết.
- Claim/retry message chưa được xử lý.
- Replay event theo ID.
- Thiết kế retention và trimming.
- Chọn primitive phù hợp cho WebSocket update, order event và background worker.

---

## 2. Bài toán message trong Redis

Có ba nhu cầu thường bị trộn lẫn:

### Broadcast realtime

~~~text
Publisher → mọi subscriber đang online
~~~

Ví dụ:

- Cập nhật WebSocket.
- Notification tạm thời.
- Cache invalidation không cần replay.

### Queue đơn giản

~~~text
Producer → một consumer lấy item → xử lý
~~~

Ví dụ:

- Feed gần đây.
- Job đơn giản.
- Work item không cần acknowledgement phức tạp.

### Event stream bền hơn

~~~text
Producer → event log → nhiều consumer/group → replay/ack/retry
~~~

Ví dụ:

- Order event.
- Payment event.
- Audit event.
- Integration giữa service.

Pub/Sub, List và Stream có behavior rất khác nhau. Chọn sai có thể làm mất message hoặc khiến hệ thống không thể recover.

---

## 3. Bảng so sánh nhanh

| Đặc điểm | Pub/Sub | List | Stream |
|---|---|---|---|
| Lưu message | Không bền | Có trong List | Có entry ID |
| Subscriber offline | Mất message | Item vẫn trong queue nếu chưa pop | Có thể đọc lại |
| Một message cho nhiều consumer | Broadcast | Thường một consumer pop | Group phân phối cho consumer |
| Acknowledgement | Không | Không tự có | Có XACK |
| Replay | Không phù hợp | Không theo event ID | Có |
| Pending tracking | Không | Không | Có |
| Use case | Realtime broadcast | Queue/feed đơn giản | Event processing |
| Retention | Không có message history | LTRIM/cleanup | MAXLEN/retention |

---

## 4. Redis Pub/Sub

Pub/Sub là cơ chế publish/subscribe realtime. Publisher gửi message vào channel; các subscriber đang subscribe channel nhận message.

~~~text
Publisher → channel:product-updated
                   ├─ Subscriber A
                   ├─ Subscriber B
                   └─ Subscriber C
~~~

### 4.1. Subscribe

~~~text
SUBSCRIBE product-updated
~~~

Subscriber giữ connection mở và chờ message.

### 4.2. Publish

~~~text
PUBLISH product-updated '{"productId":123,"version":42}'
~~~

Redis trả về số subscriber nhận message.

### 4.3. Pattern subscribe

~~~text
PSUBSCRIBE product:*
~~~

Subscriber có thể nhận các channel khớp pattern. Pattern rộng có thể làm subscriber nhận quá nhiều message, nên dùng namespace rõ.

### 4.4. Đặc tính fire-and-forget

Pub/Sub không lưu message để subscriber đọc sau. Nếu subscriber offline tại thời điểm publish:

~~~text
Publisher gửi message
Subscriber đang offline
Message bị bỏ qua với subscriber đó
~~~

Đây là behavior thiết kế, không phải lỗi.

---

## 5. Khi nào dùng Pub/Sub?

Phù hợp:

- Broadcast cập nhật UI qua WebSocket.
- Cache invalidation best-effort.
- Refresh signal.
- Notification realtime không cần replay.
- Signaling tạm thời giữa instance.

Ví dụ:

~~~text
Product updated
    ↓
PUBLISH product-events
    ↓
Các instance evict local cache
~~~

Không phù hợp:

- Payment event.
- Order event quan trọng.
- Job cần retry.
- Event cần audit.
- Message phải xử lý sau khi consumer restart.

Nếu message quan trọng, dùng Stream hoặc message broker có persistence/retry phù hợp.

---

## 6. Pub/Sub trong Spring

Spring Data Redis có thể dùng MessageListenerContainer.

Khái niệm:

~~~java
@Bean
RedisMessageListenerContainer container(
    RedisConnectionFactory factory,
    MessageListener listener
) {
    RedisMessageListenerContainer container =
        new RedisMessageListenerContainer();

    container.setConnectionFactory(factory);
    container.addMessageListener(
        listener,
        new ChannelTopic("product-updated")
    );

    return container;
}
~~~

Publisher:

~~~java
stringRedisTemplate.convertAndSend(
    "product-updated",
    jsonMessage
);
~~~

Listener:

~~~java
public class ProductUpdateListener
    implements MessageListener {

    @Override
    public void onMessage(
        Message message,
        byte[] pattern
    ) {
        String body = new String(
            message.getBody(),
            StandardCharsets.UTF_8
        );

        handle(body);
    }
}
~~~

Listener phải:

- Xử lý nhanh hoặc dispatch sang worker.
- Không block connection lâu.
- Có metric message received/error.
- Không giả định message sẽ được replay.
- Có behavior khi Redis disconnect/reconnect.

---

## 7. Pub/Sub và local cache invalidation

Use case:

~~~text
Instance A update product
    ↓
Database commit
    ↓
PUBLISH product-updated
    ↓
Instance B/C xóa local L1 cache
~~~

Pub/Sub có thể phù hợp nếu:

- Local cache stale ngắn chấp nhận được.
- Miss event không gây lỗi nghiêm trọng.
- TTL local cache là safety net.
- Có thể rebuild từ Redis/database.

Nếu bỏ lỡ invalidation event gây sai nghiệp vụ, cần durable event hoặc version check thay vì chỉ Pub/Sub.

---

## 8. Redis Lists

List là danh sách có thứ tự, phù hợp cho queue hoặc feed đơn giản.

### 8.1. Producer và consumer

Producer:

~~~text
RPUSH queue:emails job-1 job-2
~~~

Consumer:

~~~text
LPOP queue:emails
~~~

Blocking consumer:

~~~text
BLPOP queue:emails 30
~~~

### 8.2. Feed

~~~text
LPUSH feed:user:42 activity-100
LPUSH feed:user:42 activity-101
LRANGE feed:user:42 0 19
LTRIM feed:user:42 0 99
~~~

List giữ item theo thứ tự push. LTRIM giới hạn feed gần đây.

### 8.3. Rủi ro queue List

Flow:

~~~text
Consumer LPOP message
Process chết trước khi xử lý xong
Message mất khỏi List
~~~

List không có acknowledgement hoặc pending tracking tự nhiên. Có thể tự xây processing list:

~~~text
RPOPLPUSH ready processing
xử lý
LREM processing message
~~~

Nhưng retry, timeout, ownership và cleanup sẽ trở nên phức tạp. Nếu cần những tính năng này, Stream thường phù hợp hơn.

---

## 9. Redis Streams

Stream là append-only log gồm entry ID và field/value.

~~~text
Stream:
1727770000000-0 → type=order-created, order_id=1001
1727770000100-0 → type=order-paid, order_id=1001
~~~

Khác với Pub/Sub, Stream lưu entry để consumer đọc lại trong retention period.

---

## 10. XADD: thêm event

~~~text
XADD shop:events:orders * type order-created order_id 1001
~~~

Dấu * để Redis tạo ID theo timestamp và sequence.

Response:

~~~text
1727770000000-0
~~~

Có thể cung cấp ID rõ ràng, nhưng application thường để Redis tạo ID trừ khi có use case đặc biệt.

### 10.1. Event fields

Stream entry là các cặp field/value:

~~~text
XADD shop:events:orders *
     type order-created
     order_id 1001
     user_id 42
~~~

Giữ event payload đủ để consumer xử lý hoặc chứa reference đến database. Không nhét payload khổng lồ nếu consumer có thể đọc resource từ source.

### 10.2. ID và ordering

ID thường có phần timestamp và sequence:

~~~text
milliseconds-sequence
~~~

ID giúp đọc theo thứ tự trong cùng stream. Không coi timestamp là business ordering tuyệt đối nếu event đến từ nhiều nguồn hoặc clock khác nhau. Khi business cần ordering, dùng sequence/version domain rõ ràng.

---

## 11. XRANGE và replay

Đọc một khoảng event:

~~~text
XRANGE shop:events:orders - +
~~~

Giới hạn:

~~~text
XRANGE shop:events:orders - + COUNT 100
~~~

Đọc từ một ID:

~~~text
XRANGE shop:events:orders 1727770000000-0 +
~~~

Replay hữu ích khi:

- Consumer deploy lại.
- Rebuild projection.
- Debug event.
- Backfill service mới.
- Khôi phục sau lỗi.

Replay handler phải idempotent. Đọc lại event không được tạo side effect trùng.

---

## 12. XREAD

Đọc stream từ một vị trí:

~~~text
XREAD COUNT 10 STREAMS shop:events:orders 0
~~~

Đọc event sau ID:

~~~text
XREAD COUNT 10 STREAMS shop:events:orders 1727770000000-0
~~~

Block chờ event mới:

~~~text
XREAD BLOCK 5000 COUNT 10 STREAMS shop:events:orders $
~~~

Dấu $ nghĩa là chỉ nhận event mới sau thời điểm bắt đầu đọc, không đọc lịch sử trước đó.

XREAD không tự tạo consumer group. Application tự quản lý last ID và retry.

---

## 13. Consumer group

Consumer group phân phối message giữa nhiều consumer trong cùng group.

~~~text
Stream: order-events
Group: billing
    ├─ worker-1
    ├─ worker-2
    └─ worker-3
~~~

Một entry trong group thường được giao cho một consumer, không phải tất cả consumer.

Tạo group:

~~~text
XGROUP CREATE shop:events:orders billing 0 MKSTREAM
~~~

- billing là group name.
- 0 nghĩa là đọc từ đầu.
- $ nghĩa là chỉ đọc event mới.
- MKSTREAM tạo stream nếu chưa tồn tại tùy behavior Redis.

Đọc message mới chưa được giao:

~~~text
XREADGROUP GROUP billing worker-1
    COUNT 10
    STREAMS shop:events:orders >
~~~

Dấu > yêu cầu message mới đối với group.

---

## 14. XACK và at-least-once processing

Sau khi side effect thành công:

~~~text
XACK shop:events:orders billing 1727770000000-0
~~~

Nếu consumer chết trước XACK:

~~~text
Message vẫn nằm trong Pending Entries List
~~~

Semantics thường là at-least-once:

~~~text
Message có thể được xử lý lại
~~~

Vì vậy handler phải idempotent:

- Dùng event ID làm dedup key.
- Database unique constraint.
- Kiểm tra trạng thái hiện tại trước side effect.
- Lưu processed event record.

Không XACK trước khi side effect quan trọng thành công, nếu không message có thể mất khi worker chết.

---

## 15. Pending Entries List

Kiểm tra pending:

~~~text
XPENDING shop:events:orders billing
~~~

Pending entry thường có:

- Entry ID.
- Consumer hiện tại.
- Thời điểm giao.
- Số lần delivery.

Pending có thể cho biết:

- Consumer bị chết.
- Handler bị treo.
- Message poison.
- Database downstream chậm.
- Consumer đọc nhưng không acknowledge.

Cần có monitoring cho pending count và age của message cũ nhất.

---

## 16. Claim message

Khi consumer chết, consumer khác cần claim message pending.

Tùy Redis version, dùng XCLAIM hoặc XAUTOCLAIM.

Khái niệm:

~~~text
1. Tìm pending quá lâu
2. Claim về worker mới
3. Xử lý
4. XACK nếu thành công
~~~

Ví dụ XAUTOCLAIM:

~~~text
XAUTOCLAIM shop:events:orders billing worker-2
    60000 0-0 COUNT 10
~~~

Ý nghĩa:

- Chỉ claim message idle ít nhất 60 giây.
- Worker-2 nhận message.
- Bắt đầu từ ID 0-0.
- Giới hạn 10 message.

Cần tránh hai worker cùng xử lý side effect bằng idempotency. Claim không xóa vấn đề duplicate processing.

---

## 17. Poison message

Poison message là message luôn làm handler lỗi.

Nếu retry vô hạn:

~~~text
Message lỗi → pending → claim → lỗi → pending → claim ...
~~~

Hậu quả:

- Worker tốn thời gian.
- Message khác bị chậm.
- Pending tăng.
- Log/error tăng.

Cần:

- Delivery count.
- Retry limit.
- Dead-letter stream.
- Error reason.
- Manual replay sau khi sửa lỗi.
- Không XACK message lỗi nếu chưa có policy xử lý.

Ví dụ:

~~~text
shop:events:orders:dead-letter
  original_id
  error
  attempts
  failed_at
~~~

---

## 18. Redis Streams trong Spring

Thêm record:

~~~java
StreamOperations<String, String, String> streams =
    stringRedisTemplate.opsForStream();

Map<String, String> body = Map.of(
    "type", "order-created",
    "order_id", "1001"
);

RecordId id = streams.add(
    StreamRecords.newRecord()
        .in("shop:events:orders")
        .ofMap(body)
);
~~~

Tạo group và đọc group có nhiều overload trong Spring Data Redis. Cần xác định:

- Stream key.
- Group name.
- Consumer name.
- Read offset.
- Batch size.
- Poll timeout.
- Acknowledge policy.
- Error/retry policy.

Với consumer production, thường dùng listener container hoặc worker loop có backoff thay vì tự spin busy loop.

---

## 19. Consumer handler an toàn

Flow:

~~~text
1. Đọc message
2. Validate schema
3. Kiểm tra event type/version
4. Thực hiện side effect idempotent
5. Commit side effect
6. XACK
~~~

Không XACK ở bước 1. Nếu side effect là database transaction:

~~~text
Database transaction commit thành công
    ↓
XACK
~~~

Nếu XACK thất bại sau database commit, message sẽ được xử lý lại. Đây là lý do side effect phải idempotent.

---

## 20. Event schema và version

Event nên có:

~~~json
{
  "event_id": "evt-123",
  "event_type": "order-created",
  "event_version": "1",
  "occurred_at": "2026-10-02T10:00:00Z",
  "aggregate_id": "order-1001",
  "payload": {
    "user_id": "42"
  }
}
~~~

Lợi ích:

- Dedup bằng event_id.
- Chọn handler theo event_type.
- Migration bằng event_version.
- Debug theo occurred_at và aggregate_id.
- Replay có kiểm soát.

Không coi raw Stream entry ID là business event ID duy nhất nếu producer retry có thể tạo nhiều entry cho cùng event.

---

## 21. Retention và trimming

Stream không nên tăng vô hạn.

Thêm event với approximate trim:

~~~text
XADD shop:events:orders MAXLEN ~ 100000 * type order-created order_id 1001
~~~

Approximate trim thường ít tốn chi phí hơn exact trim.

Có thể trim theo minimum ID hoặc thời gian tùy policy. Retention phải dựa trên:

- Thời gian consumer có thể offline.
- Thời gian cần replay.
- Audit requirement.
- Memory budget.
- Số lượng event mỗi giây.
- Khả năng rebuild từ database.

Nếu retention quá ngắn, consumer offline lâu có thể không còn event cần đọc.

---

## 22. Pub/Sub vs Stream cho cache invalidation

### Pub/Sub

~~~text
DB commit → PUBLISH invalidation
~~~

Phù hợp khi:

- Local cache stale ngắn chấp nhận được.
- Bỏ lỡ event vẫn có TTL safety net.
- Consumer online liên tục.

### Stream

~~~text
DB/outbox → XADD invalidation
    ↓
Mỗi instance/group đọc và XACK
~~~

Phù hợp khi:

- Không được bỏ lỡ invalidation.
- Instance có thể restart.
- Cần retry/replay.
- Cần theo dõi consumer lag.

Nếu mỗi application instance cần nhận mọi event, mỗi instance có thể dùng group riêng hoặc đọc cùng stream theo policy phù hợp. Một group duy nhất sẽ chia event giữa consumer, không broadcast cho tất cả consumer.

---

## 23. Lists vs Streams cho queue

List:

~~~text
RPUSH queue job
BLPOP queue job
~~~

Phù hợp với queue đơn giản, nhưng pop trước process có thể làm mất job.

Stream:

~~~text
XADD stream job
XREADGROUP group consumer
XACK sau xử lý
~~~

Phù hợp với job cần pending, retry và consumer group.

Chọn List khi:

- Work item đơn giản.
- Mất message có thể chấp nhận.
- Có recovery ngoài Redis.
- Cần thao tác hai đầu danh sách.

Chọn Stream khi:

- Event quan trọng.
- Cần retry/replay.
- Có nhiều consumer.
- Cần quan sát pending/lag.

---

## 24. Exactly-once và at-least-once

Redis Stream consumer group thường dẫn tới at-least-once:

~~~text
Một event có thể được giao lại
~~~

Exactly-once end-to-end rất khó vì side effect và XACK là hai bước khác nhau:

~~~text
Database commit thành công
XACK thất bại
→ event được giao lại
~~~

Thiết kế thực tế:

- At-least-once delivery.
- Idempotent consumer.
- Deduplication record.
- Unique constraint.
- Retry/dead-letter.
- Observability.

Đừng tuyên bố exactly-once chỉ vì dùng XACK.

---

## 25. Ordering

Trong một Stream, entry ID có thứ tự. Nhưng xử lý qua nhiều consumer có thể hoàn thành không theo thứ tự:

~~~text
event A giao worker-1
event B giao worker-2
worker-2 hoàn thành trước worker-1
~~~

Nếu cần ordering theo aggregate:

- Partition stream theo aggregate hash.
- Dùng một consumer/partition cho aggregate.
- Lưu sequence number.
- Reject hoặc buffer event đến sai thứ tự.
- Thiết kế handler idempotent và commutative nếu có thể.

Không giả định consumer group tự bảo đảm business ordering toàn hệ thống.

---

## 26. Backpressure và consumer lag

Nếu producer nhanh hơn consumer:

~~~text
Produced: 10.000 event/s
Consumed:  5.000 event/s
Lag tăng dần
~~~

Cần theo dõi:

- Stream length.
- Pending count.
- Oldest pending age.
- Consumer throughput.
- Processing latency.
- Error/retry rate.

Cách xử lý:

- Tăng số consumer.
- Batch read hợp lý.
- Tối ưu handler/downstream.
- Giảm payload.
- Rate limit producer.
- Tách stream theo workload.
- Tạm dừng producer không quan trọng.
- Tăng retention có kiểm soát.

Tăng consumer không luôn tốt nếu bottleneck là database downstream.

---

## 27. WebSocket với Pub/Sub

Flow:

~~~text
Application update
    ↓
PUBLISH websocket:room:42
    ↓
WebSocket gateway nhận
    ↓
Broadcast đến client room 42
~~~

Pub/Sub phù hợp vì client online cần thông báo realtime và message cũ không cần replay.

Cần:

- Authenticate subscription.
- Kiểm tra user được vào room.
- Giới hạn message size.
- Không gửi dữ liệu nhạy cảm cho channel rộng.
- Reconnect behavior ở client.
- Nếu client cần state hiện tại, đọc snapshot từ API sau reconnect; đừng kỳ vọng Pub/Sub replay.

---

## 28. Order events với Stream

Flow:

~~~text
Order service
    ↓
XADD orders:events
    ↓
billing group
inventory group
notification group
analytics group
~~~

Mỗi group có tiến độ riêng:

~~~text
billing xử lý order-created
inventory xử lý order-created
notification xử lý order-created
~~~

Một group chia message giữa các consumer trong group. Nhiều group cho các downstream độc lập cùng đọc event.

Cần outbox nếu event phải phản ánh database transaction:

~~~text
DB update + outbox insert trong một transaction
Worker outbox → XADD Stream
~~~

---

## 29. Bài thực hành Pub/Sub

Tạo channel:

~~~text
websocket:room:42
~~~

Yêu cầu:

- Publisher gửi notification.
- Subscriber broadcast cho WebSocket.
- Subscriber disconnect/reconnect.
- Kiểm tra message gửi khi offline bị mất.
- Sau reconnect, client gọi API lấy snapshot hiện tại.
- Thêm authorization cho room.
- Theo dõi publish count và listener error.

---

## 30. Bài thực hành List

Tạo queue email bằng List:

~~~text
RPUSH email:queue job-1
BLPOP email:queue 30
~~~

Mô phỏng worker chết sau khi pop. Sau đó đánh giá:

- Message có mất không?
- Có thể dùng processing list để khôi phục không?
- Khi nào nên chuyển sang Stream?
- Cần giới hạn queue và monitor length thế nào?

---

## 31. Bài thực hành Stream

Tạo order event:

~~~text
XADD orders:events * type order-created order_id 1001
XADD orders:events * type order-paid order_id 1001
~~~

Tạo group:

~~~text
XGROUP CREATE orders:events billing 0 MKSTREAM
~~~

Đọc:

~~~text
XREADGROUP GROUP billing worker-1 COUNT 10 STREAMS orders:events >
~~~

Xác nhận:

~~~text
XACK orders:events billing <entry-id>
~~~

Mô phỏng:

- Worker chết trước XACK.
- Kiểm tra XPENDING.
- Worker khác XAUTOCLAIM.
- Xử lý duplicate.
- Đưa poison message vào dead-letter.
- Replay từ một event ID.
- Trim stream theo retention.

---

## 32. Metrics

Pub/Sub:

- Published message count.
- Active subscriber count.
- Listener disconnect/reconnect.
- Message processing latency.
- Publish/listener error.

List:

- Queue length.
- Oldest item age nếu có timestamp.
- Pop rate.
- Processing failure.
- Lost/recovered item count.

Stream:

- Stream length.
- Produced rate.
- Consumed rate.
- Consumer lag.
- Pending count.
- Oldest pending age.
- Delivery/retry count.
- XACK failure.
- Dead-letter count.
- Trimmed entry count.

---

## 33. Câu hỏi tự kiểm tra

1. Vì sao Pub/Sub được gọi là fire-and-forget?
2. Subscriber offline có đọc lại Pub/Sub message không?
3. Khi nào List phù hợp hơn Stream?
4. Vì sao List queue có thể mất message?
5. XADD tạo event ID thế nào?
6. XRANGE và XREAD khác nhau ra sao?
7. Consumer group phân phối message thế nào?
8. XACK nên được gọi trước hay sau side effect?
9. Pending Entries List dùng để làm gì?
10. Khi nào dùng XCLAIM hoặc XAUTOCLAIM?
11. Vì sao Stream consumer cần idempotent?
12. Vì sao phải trim Stream?
13. Một group duy nhất có broadcast event cho mọi service không?
14. Redis Stream có đảm bảo exactly-once end-to-end không?
15. Làm sao xử lý event ordering theo aggregate?

---

## 34. Tiêu chí hoàn thành

Bạn hoàn thành Tuần 12 khi có thể:

- Giải thích khác nhau giữa Pub/Sub, List và Stream.
- Dùng Pub/Sub cho WebSocket/broadcast không cần replay.
- Dùng List cho feed hoặc queue đơn giản.
- Dùng XADD/XREAD để thêm và đọc event.
- Tạo consumer group và đọc bằng XREADGROUP.
- XACK sau khi side effect thành công.
- Theo dõi pending entries và claim message.
- Xử lý retry, duplicate và poison message.
- Replay event theo ID.
- Trim Stream theo retention policy.
- Theo dõi consumer lag và backpressure.
- Chọn đúng primitive cho order events và cache invalidation.

Kết luận:

> Pub/Sub dành cho broadcast realtime không cần replay; List dành cho queue/feed đơn giản; Redis Streams dành cho event có ID, retention, consumer group, acknowledgement và replay. Nếu message quan trọng, hãy thiết kế at-least-once + idempotent handler thay vì giả định exactly-once.


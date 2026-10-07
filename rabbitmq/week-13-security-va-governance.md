# Tuần 13 — Security và governance

> Một RabbitMQ an toàn không bắt đầu bằng việc bật thêm một plugin. Nó bắt đầu bằng câu hỏi: service nào được phép làm gì, trên resource nào, trong environment nào, qua đường mạng nào, và khi credential bị lộ thì thu hồi ra sao?

## 1. Kết quả cần đạt

Sau bài học, bạn có thể:

- Phân biệt user, password, tags, vhost và permission trong RabbitMQ.
- Giải thích ba quyền `configure`, `write`, `read`.
- Tạo user riêng cho từng service với quyền tối thiểu.
- Dùng vhost làm boundary cho environment hoặc team.
- Cấu hình TLS cho AMQP và HTTPS cho Management UI.
- Rotate password, certificate và secret mà không commit credential vào Git.
- Giới hạn network access và không expose port quản trị công khai.
- Thiết kế naming convention, ownership và audit trail.
- Tránh đưa password, access token hoặc PII nhạy cảm vào message.
- Viết permission matrix và automated permission test cho CI.
- Viết runbook revoke user và rotate secret.

## 2. Mô hình threat trước khi cấu hình

Hãy coi RabbitMQ là một hệ thống chứa dữ liệu và năng lực điều khiển luồng xử lý. Có ít nhất năm nhóm rủi ro:

| Rủi ro | Ví dụ | Hậu quả |
|---|---|---|
| Lộ credential | Password nằm trong Git hoặc log | Người ngoài publish/consume dữ liệu |
| Quyền quá rộng | Service dùng user administrator | Một service có thể đọc queue của service khác |
| Lộ network | Port `15672` mở Internet | Attacker thử password, xem topology, quản trị broker |
| Message chứa secret | Access token nằm trong payload | Secret bị lưu trong queue, DLQ, log, backup |
| Thiếu audit | Không biết ai tạo user hoặc đổi permission | Khó điều tra và thu hồi đúng phạm vi |

Mục tiêu của governance là giảm blast radius. Nếu `notification-service` bị compromise, nó không nên đọc được message thanh toán, xóa queue của order hoặc đăng nhập vào Management UI bằng quyền admin.

## 3. Các lớp bảo vệ

```text
Identity       service nào đang kết nối?
Authentication password/certificate có hợp lệ không?
Authorization  service được làm gì trên resource nào?
Network        kết nối đến từ subnet/security group nào?
Transport      dữ liệu trên đường truyền có được mã hóa không?
Data           message có chứa secret hoặc PII không?
Governance     ai sở hữu, ai phê duyệt, ai audit và khi nào rotate?
```

Không có lớp nào thay thế hoàn toàn lớp khác. TLS không sửa được permission quá rộng; permission không bảo vệ password bị commit lên Git; private subnet không giúp nếu user có quyền administrator.

## 4. User, password và tags

### User

User là identity được RabbitMQ xác thực. Mỗi application service nên có user riêng:

```text
order-service-dev
order-service-staging
order-service-prod
payment-service-prod
notification-service-prod
```

Không dùng một user chung kiểu `application` cho toàn bộ hệ thống. User chung làm mất khả năng:

- phân quyền theo service;
- truy vết service nào tạo traffic;
- rotate một credential mà không restart mọi service;
- cô lập sự cố khi một credential bị lộ.

### Password

Password phải được cấp từ secret manager hoặc cơ chế secret của deployment platform. Application đọc password qua environment injection hoặc mounted secret, không hardcode trong source code.

Không làm như sau:

```yaml
# Sai: credential nằm trong repository
rabbitmq:
  username: order-service
  password: super-secret-password
```

Nên làm như sau:

```yaml
rabbitmq:
  username: ${RABBITMQ_USERNAME}
  password: ${RABBITMQ_PASSWORD}
  virtual-host: ${RABBITMQ_VHOST:/orders}
```

Giá trị mặc định chỉ nên dùng cho local development, không dùng cho staging hoặc production.

### Tags

Tags là metadata/quyền quản trị gắn với user, không phải permission của queue. Một số tag thường gặp:

| Tag | Mục đích |
|---|---|
| Không có tag | Application user thông thường |
| `management` | Đăng nhập Management UI và dùng management API theo quyền được hỗ trợ |
| `monitoring` | Đọc thông tin monitoring |
| `policymaker` | Quản lý policy |
| `administrator` | Quyền quản trị rộng |

Application user thường không cần tag `management`. Nếu một service chỉ publish/consume, hãy để user không có tag quản trị.

## 5. Vhost là boundary

Virtual host, viết tắt là vhost, là namespace logic chứa exchange, queue, binding và permission. Tên resource trong vhost này không tự động tồn tại trong vhost khác.

Ví dụ:

```text
/orders-dev
/orders-staging
/orders-prod
/payments-prod
```

Vhost có thể dùng để tách:

- environment: dev, staging, production;
- team hoặc domain: orders, payments, notifications;
- tenant, nếu mô hình vận hành phù hợp.

### Khi nào không nên tạo quá nhiều vhost?

Mỗi vhost tạo thêm cấu hình, permission và đối tượng cần quản lý. Đừng tạo vhost cho từng queue hoặc từng request. Một vhost nên đại diện cho một boundary quản trị rõ ràng.

Nguyên tắc thực tế:

- Production không dùng chung vhost với development.
- Domain nhạy cảm như payment có thể tách vhost để giảm blast radius.
- Chỉ tạo vhost khi có khác biệt về ownership, access policy hoặc lifecycle.
- Đưa tên vhost vào configuration, không rải literal trong code.

## 6. Ba quyền: configure, write, read

Permission được áp dụng theo vhost và pattern resource. Ba quyền có ý nghĩa khác nhau:

| Quyền | Cho phép |
|---|---|
| `configure` | Khai báo, thay đổi hoặc xóa resource theo pattern được cấp |
| `write` | Publish vào exchange theo pattern được cấp |
| `read` | Consume hoặc đọc message từ queue theo pattern được cấp |

Permission là regex. Vì vậy cần đặc biệt cẩn thận với `.*`.

### Ví dụ permission tối thiểu

Giả sử vhost `/orders-prod` có:

```text
exchange: orders.events
queue:    order-created.notification
queue:    order-created.payment
```

`order-service` cần tạo topology và publish event:

```text
configure: ^orders\..*$
write:     ^orders\.events$
read:      ^$
```

`notification-service` chỉ consume queue của nó:

```text
configure: ^$
write:     ^$
read:      ^order-created\.notification$
```

`payment-service` chỉ consume queue payment và có thể publish payment event nếu contract yêu cầu:

```text
configure: ^$
write:     ^orders\.events$
read:      ^order-created\.payment$
```

Pattern thực tế cần được kiểm tra trên đúng RabbitMQ version và naming convention của hệ thống. Mục tiêu là permission phải hẹp theo resource, không phải cho đẹp trên giấy.

### Permission không giống role trong application

RabbitMQ không hiểu business role như `ORDER_ADMIN` hoặc `CAN_REFUND`. RabbitMQ chỉ quyết định user có thể thao tác với resource AMQP nào. Business authorization vẫn phải nằm trong application/service.

## 7. Tạo vhost và service user bằng CLI

Ví dụ dành cho local hoặc môi trường mà bạn có quyền administrator:

```bash
rabbitmqctl add_vhost /orders-dev
rabbitmqctl add_user order-service-dev "$ORDER_SERVICE_DEV_PASSWORD"
rabbitmqctl set_permissions -p /orders-dev order-service-dev \
  '^orders\\..*$' \
  '^orders\\.events$' \
  '^$'
```

Ba đối số sau username lần lượt là `configure`, `write`, `read`.

Tạo consumer user:

```bash
rabbitmqctl add_user notification-service-dev "$NOTIFICATION_SERVICE_DEV_PASSWORD"
rabbitmqctl set_permissions -p /orders-dev notification-service-dev \
  '^$' \
  '^$' \
  '^order-created\\.notification$'
```

Cho phép user dùng vhost:

```bash
rabbitmqctl set_permissions -p /orders-dev order-service-dev \
  '^orders\\..*$' '^orders\\.events$' '^$'
```

Kiểm tra:

```bash
rabbitmqctl list_users
rabbitmqctl list_vhosts
rabbitmqctl list_permissions -p /orders-dev
rabbitmqctl list_user_permissions order-service-dev
```

Xóa quyền khỏi vhost:

```bash
rabbitmqctl clear_permissions -p /orders-dev order-service-dev
```

Xóa user chỉ sau khi đã xác nhận không còn connection hợp lệ cần dùng user đó:

```bash
rabbitmqctl delete_user old-order-service
```

## 8. Khai báo topology: startup hay IaC?

Có hai cách phổ biến để khai báo exchange, queue và binding:

### Application tự declare

Application khai báo topology khi khởi động. Cách này tiện cho local và giúp service tự mô tả dependency của nó.

Điểm cần nhớ: nếu application cần `configure` nhưng user production không có quyền này, startup có thể fail. Nếu muốn app chỉ consume topology đã được provision, cấp `configure` rỗng và quản lý topology bằng deployment/IaC.

### Provision bằng operator/IaC

Platform team tạo vhost, exchange, queue, binding và policy trước. Application chỉ có `write`/`read` tối thiểu.

Đây thường là lựa chọn tốt hơn cho production vì:

- topology được review như code;
- permission không phụ thuộc thứ tự startup;
- dễ audit thay đổi;
- tránh service tự ý tạo resource ngoài boundary.

### Quyết định cần ghi rõ

Trong mỗi service, tài liệu phải trả lời:

```text
Ai sở hữu exchange/queue?
Ai được declare?
Ai được publish?
Ai được consume?
Resource tồn tại qua deployment hay do app tạo?
Khi xóa service, resource có được xóa không?
```

## 9. TLS cho AMQP

Không dùng AMQP plaintext trên network không đáng tin cậy. AMQP thường dùng port `5672`; AMQP over TLS dùng `5671` theo convention.

TLS bảo vệ:

- password khi client authenticate;
- message trên đường truyền;
- metadata AMQP giữa client và broker.

TLS không bảo vệ message khi đã nằm trong queue, log, DLQ hoặc database. Vì vậy vẫn phải bảo vệ broker, storage, log và payload.

### Certificate chain

Một kết nối TLS thường có:

```text
RabbitMQ server certificate
  được ký bởi CA mà client tin cậy

Client truststore
  chứa CA certificate phù hợp
```

Client cần verify hostname và certificate chain. Không tắt hostname verification hoặc trust mọi certificate chỉ để “cho chạy được”.

### Checklist TLS client

- Dùng `amqps://` hoặc bật SSL/TLS trong client configuration.
- Trust đúng CA, không trust toàn bộ hệ thống một cách mù quáng.
- Verify hostname của broker.
- Chọn TLS version/cipher theo policy của tổ chức.
- Không log private key, password hoặc full certificate chain không cần thiết.
- Test cả handshake thành công và handshake bị từ chối.

Ví dụ Spring Boot:

```yaml
spring:
  rabbitmq:
    host: ${RABBITMQ_HOST}
    port: 5671
    username: ${RABBITMQ_USERNAME}
    password: ${RABBITMQ_PASSWORD}
    virtual-host: ${RABBITMQ_VHOST:/orders-prod}
    ssl:
      enabled: true
      algorithm: TLSv1.3
      validate-server-certificate: true
```

Tên property cụ thể có thể khác theo Spring Boot version và cách cung cấp truststore. Hãy dùng property metadata của version dự án, nhưng không bỏ qua server certificate validation.

## 10. HTTPS cho Management UI và HTTP API

Management UI thường dùng port `15672` cho HTTP và `15671` cho HTTPS theo convention. Không expose Management UI ra Internet công khai.

Nếu cần truy cập:

```text
Operator workstation
  -> VPN/bastion/private network
  -> HTTPS Management UI
```

Các biện pháp nên có:

- bind interface private;
- firewall/security group chỉ cho phép subnet quản trị;
- TLS certificate hợp lệ;
- SSO hoặc identity integration nếu tổ chức hỗ trợ;
- MFA ở lớp identity/VPN/SSO;
- audit log cho hành động quản trị;
- không dùng user administrator trong application.

Management API cũng là control plane. Token hoặc credential gọi API phải được bảo vệ như credential quản trị, không đặt trong frontend hoặc script chia sẻ công khai.

## 11. Network policy và firewall

Định nghĩa rõ ai được phép kết nối:

| Source | Destination | Port | Mục đích |
|---|---|---:|---|
| Order service subnet | RabbitMQ private endpoint | 5671 | AMQPS |
| Operator subnet/VPN | RabbitMQ management | 15671 | HTTPS UI/API |
| Monitoring subnet | Metrics endpoint | 15692 hoặc port theo setup | Prometheus |
| Internet | RabbitMQ | Không cho phép | Giảm attack surface |
| Internet | Management UI | Không cho phép | Không expose control plane |

Đừng chỉ dựa vào port. Cần kết hợp:

- security group/firewall;
- Kubernetes NetworkPolicy nếu chạy trên Kubernetes;
- private subnet/private DNS;
- routing không đi qua public Internet;
- egress policy nếu service không cần kết nối tùy ý.

Nếu broker phải nhận kết nối từ network khác, ưu tiên private connectivity hoặc VPN, sau đó mới đến TLS và credential.

## 12. Certificate rotation

Certificate rotation cần được thiết kế trước ngày certificate hết hạn. Một quy trình an toàn:

```text
1. Cấp certificate mới và kiểm tra chain.
2. Cấu hình broker tin CA cần thiết.
3. Phân phối CA/certificate mới cho client.
4. Test một client canary.
5. Rollout các client còn lại.
6. Theo dõi TLS handshake và connection errors.
7. Thu hồi/xóa certificate cũ sau thời gian chuyển tiếp.
```

Không thay certificate trên toàn bộ cluster rồi mới phát hiện client đang pin certificate cũ. Cần có overlap window nếu policy cho phép.

Theo dõi:

- ngày hết hạn certificate;
- thời gian còn lại đến expiry;
- handshake failure rate;
- connection churn;
- client nào còn dùng certificate cũ.

## 13. Password và secret rotation

Password rotation có thể làm rớt connection đang dùng credential cũ tùy cách triển khai. Chọn một trong hai chiến lược:

### Chiến lược A: tạo user mới rồi chuyển dần

```text
old user: order-service-prod-v1
new user: order-service-prod-v2

1. Tạo v2 với permission giống v1.
2. Inject secret v2 vào deployment.
3. Rollout/restart service.
4. Xác nhận connection dùng v2.
5. Revoke/delete v1.
```

Ưu điểm là dễ rollback. Nhược điểm là trong thời gian chuyển tiếp có hai user active.

### Chiến lược B: đổi password trên cùng user

Phù hợp khi secret platform và client có thể reload an toàn. Cần kiểm tra connection pool, reconnect policy và thời điểm restart.

### Quy tắc rotation

- Không in secret vào CI logs.
- Không gửi secret qua chat hoặc issue tracker.
- Không commit file `.env`, private key hoặc password.
- Có owner và expiry cho từng secret.
- Có quy trình revoke khẩn cấp.
- Sau rotation, kiểm tra không còn connection dùng credential cũ.

## 14. Không đưa secret hoặc PII vào message

Message có thể xuất hiện ở nhiều nơi:

```text
queue -> retry queue -> DLQ -> log -> trace -> backup -> replay tool
```

Vì vậy không đưa vào payload:

- password;
- access token, refresh token, API key;
- private key;
- số thẻ đầy đủ hoặc dữ liệu thanh toán nhạy cảm;
- PII không cần thiết cho consumer;
- session cookie;
- thông tin định danh không có retention policy.

Thay vì gửi secret:

```json
{
  "orderId": "ord-123",
  "customerId": "cus-456",
  "paymentReference": "pay-ref-789"
}
```

Consumer dùng reference để lấy dữ liệu từ service sở hữu, với authorization phù hợp. Đừng biến RabbitMQ thành kho dữ liệu bí mật.

### Log payload có chọn lọc

Không log nguyên message để debug production. Log metadata cần thiết:

```json
{
  "event": "message_consumed",
  "messageId": "msg-123",
  "eventType": "OrderCreated.v1",
  "correlationId": "corr-789",
  "queue": "order-created.notification",
  "result": "success"
}
```

Nếu cần debug payload, dùng redaction, sampling, thời hạn lưu ngắn và approval rõ ràng.

## 15. Naming convention và ownership

Naming tốt giúp permission regex hẹp và audit dễ hơn.

Một convention mẫu:

```text
exchange: <domain>.<purpose>.<version?>
queue:    <consumer>.<event>.<environment>
routing:  <domain>.<event>.<version>
vhost:    /<domain>-<environment>
user:     <service>-<environment>
```

Ví dụ:

```text
exchange: orders.events
queue: order-created.notification.prod
routing key: order.created.v1
vhost: /orders-prod
user: notification-service-prod
```

Mỗi resource nên có owner:

| Resource | Owner | Purpose | Data class | Retention | On-call |
|---|---|---|---|---|---|
| `orders.events` | Order team | Domain events | Internal | Theo policy | Team Orders |
| `order-created.notification.prod` | Notification team | Email/SMS work | Internal | 7 ngày | Team Notification |

Nếu không ai biết owner, resource đó sẽ không được rotate, không được dọn dẹp và không ai chịu trách nhiệm khi incident xảy ra.

## 16. Audit và change management

Các thay đổi cần được ghi nhận:

- tạo/xóa vhost;
- tạo/sửa/xóa user;
- thay đổi tags;
- thay đổi permission;
- thay đổi policy/parameter;
- thay đổi certificate hoặc TLS config;
- thay đổi firewall/network policy;
- thay đổi retention hoặc DLQ policy.

Một audit record tối thiểu:

```text
timestamp
actor
environment
resource
old value / new value
reason or ticket
approval
rollback plan
```

Không nhất thiết phải bắt mọi thay đổi qua UI. Tốt hơn là quản lý bằng IaC hoặc script review được, sau đó lưu log deployment và ticket.

## 17. Permission matrix cho order, payment và notification

Đây là artifact quan trọng của bài học. Ví dụ:

| Service/user | Vhost | Configure | Write | Read |
|---|---|---|---|---|
| `order-service-prod` | `/orders-prod` | topology orders | `orders.events` | không có |
| `payment-service-prod` | `/orders-prod` | không có | `orders.events` | `order-created.payment` |
| `notification-service-prod` | `/orders-prod` | không có | không có | `order-created.notification` |
| `reporting-service-prod` | `/orders-prod` | không có | không có | queue reporting riêng |

Permission matrix phải trả lời được:

1. Service publish vào exchange nào?
2. Service consume queue nào?
3. Service có cần declare topology không?
4. Service có thể đọc nhầm dữ liệu domain khác không?
5. Nếu service bị compromise, blast radius là gì?

## 18. Permission test tự động

Permission test nên chạy trong CI hoặc integration environment. Mục tiêu không chỉ là “login được”, mà là quyền đúng và quyền sai bị từ chối.

### Test case tối thiểu

```text
order-service có thể publish orders.events                 PASS
order-service không thể consume payment queue              PASS/EXPECTED DENY
notification-service có thể consume notification queue    PASS
notification-service không thể publish orders.events       EXPECTED DENY
application user không đăng nhập được Management UI       EXPECTED DENY
user của dev không truy cập được vhost production           EXPECTED DENY
```

Có thể kiểm tra bằng CLI/API hoặc một test client thật. Đừng chỉ assert permission file tồn tại; hãy thực hiện thao tác publish/consume thực tế.

Pseudo-code:

```java
assertThat(publishAs("notification-service-prod", "orders.events"))
    .isRejected();

assertThat(consumeAs("notification-service-prod",
        "order-created.notification.prod"))
    .isSuccessful();
```

Test phải dùng test vhost riêng, message test không chứa dữ liệu thật và cleanup sau test.

## 19. Lab 1 — Tạo vhost dev và service user

### Mục tiêu

Tạo vhost `/orders-dev`, user `order-service-dev` và chỉ cho phép user này thao tác với resource orders.

### Thực hiện

```bash
export ORDER_PASSWORD="thay-bang-secret-local"

rabbitmqctl add_vhost /orders-dev
rabbitmqctl add_user order-service-dev "$ORDER_PASSWORD"
rabbitmqctl set_permissions -p /orders-dev order-service-dev \
  '^orders\\..*$' \
  '^orders\\.events$' \
  '^$'
```

Declare một exchange test:

```bash
rabbitmqadmin -V /orders-dev \
  -u order-service-dev -p "$ORDER_PASSWORD" \
  declare exchange name=orders.events type=topic durable=true
```

Thử declare exchange ngoài pattern:

```bash
rabbitmqadmin -V /orders-dev \
  -u order-service-dev -p "$ORDER_PASSWORD" \
  declare exchange name=payments.events type=topic durable=true
```

Lệnh thứ hai phải bị từ chối. Nếu không bị từ chối, permission đang rộng hơn mục tiêu.

### Kết quả cần lưu

- output `list_permissions`;
- command thành công;
- command bị từ chối;
- giải thích regex đã dùng.

## 20. Lab 2 — Thử publish vào exchange không được cấp quyền

Tạo consumer user chỉ được đọc:

```bash
export NOTIFICATION_PASSWORD="thay-bang-secret-local"

rabbitmqctl add_user notification-service-dev "$NOTIFICATION_PASSWORD"
rabbitmqctl set_permissions -p /orders-dev notification-service-dev \
  '^$' '^$' '^order-created\\.notification$'
```

Tạo queue/binding bằng admin hoặc IaC, sau đó:

```text
1. Đăng nhập bằng notification-service-dev.
2. Consume từ order-created.notification.
3. Publish vào orders.events.
4. Declare queue mới.
5. Ghi lại kết quả từng thao tác.
```

Expected:

| Thao tác | Kết quả |
|---|---|
| Consume queue được cấp | Thành công |
| Publish exchange | Bị từ chối |
| Declare resource | Bị từ chối |
| Consume queue ngoài pattern | Bị từ chối |

## 21. Lab 3 — Cấu hình TLS local hoặc staging

Mục tiêu không phải tự phát minh PKI production, mà là chứng minh client không kết nối plaintext và có verify certificate.

Checklist:

```text
[ ] Broker có listener TLS.
[ ] Client dùng port TLS.
[ ] Client trust đúng CA.
[ ] Hostname verification bật.
[ ] Kết nối với CA sai bị fail.
[ ] Kết nối plaintext vào listener TLS bị fail.
[ ] Không có private key trong Git.
```

Kiểm tra handshake ở môi trường được phép:

```bash
openssl s_client -connect rabbitmq.internal.example:5671 \
  -servername rabbitmq.internal.example \
  -CAfile ca.crt
```

Không copy private key thật vào thư mục project chỉ để chạy lab.

## 22. Lab 4 — Rotate password không commit secret

Quy trình:

```text
1. Tạo user v2 hoặc password v2 trong secret store.
2. Cập nhật secret reference, không sửa source chứa giá trị thật.
3. Deploy một instance canary.
4. Xác nhận AMQP connection dùng v2.
5. Rollout toàn bộ service.
6. Revoke user/password v1.
7. Kiểm tra connection cũ đã biến mất.
```

Kiểm tra repository:

```bash
git status --short
git grep -n -I -E 'password|access_token|private_key|amqp(s)?://[^ ]+:[^ ]+@'
```

Lệnh này có thể cho false positive; mọi hit phải được review. Không paste output chứa secret vào issue hoặc chat.

## 23. Lab 5 — Viết permission matrix

Viết file `permission-matrix.md` cho ba service:

```text
order-service
payment-service
notification-service
```

Mỗi service phải có:

- vhost;
- user;
- configure pattern;
- write pattern;
- read pattern;
- resource owner;
- data classification;
- cách revoke;
- người/team on-call.

Sau đó thêm ít nhất một negative test cho mỗi service.

## 24. Runbook: revoke user khẩn cấp

### Khi nào dùng?

- credential xuất hiện trong Git hoặc log;
- nghi ngờ token/password bị lộ;
- service bị compromise;
- phát hiện traffic bất thường từ user.

### Các bước

```text
1. Xác định user, vhost và environment bị ảnh hưởng.
2. Xác định service nào đang dùng user.
3. Tạo user v2 nếu cần duy trì traffic hợp lệ.
4. Update secret/deployment hoặc clear permission ngay lập tức.
5. Đóng/revoke connection liên quan.
6. Kiểm tra publish/consume bất thường.
7. Rotate các secret có thể bị ảnh hưởng dây chuyền.
8. Xóa user cũ sau khi xác nhận không cần rollback.
9. Lưu timeline và bằng chứng audit.
```

Không chỉ đổi password rồi coi incident đã xong. Cần kiểm tra nơi credential từng xuất hiện và rotate mọi secret có khả năng bị truy cập.

## 25. Runbook: Management UI bị expose

### Phản ứng đầu tiên

- chặn public ingress ở firewall/security group;
- kiểm tra access log và audit log;
- disable hoặc rotate credential quản trị;
- kiểm tra user/tag/permission mới được tạo;
- xác định có message bị đọc, publish hoặc xóa không.

### Sau containment

- chuyển truy cập qua VPN/bastion;
- bật HTTPS;
- review tất cả admin users;
- loại bỏ tag quản trị không cần thiết;
- bổ sung network policy và alert cho public exposure;
- ghi post-incident action.

## 26. Common mistakes

### Dùng administrator cho application

Nhanh lúc local nhưng tạo blast radius rất lớn. Application user không cần quản trị broker.

### Permission `.*` cho mọi thứ

Regex rộng khiến least privilege chỉ còn trên tài liệu. Hãy bắt đầu từ deny và thêm đúng resource.

### Dùng chung user giữa dev và production

Credential của dev có thể lọt qua laptop, CI log hoặc local config rồi truy cập production.

### Chỉ bật TLS nhưng bỏ verify certificate

Mã hóa có thể bị vô hiệu hóa bởi man-in-the-middle nếu client tin certificate bất kỳ hoặc bỏ hostname verification.

### Expose port `15672` để tiện debug

Management UI là control plane, không phải endpoint public cho developer. Dùng VPN, bastion hoặc port-forward có kiểm soát.

### Đặt secret trong message để consumer dùng lại

Queue, retry và DLQ kéo dài vòng đời secret. Gửi reference và dùng secret manager/service authorization.

### Không có owner

Không owner nghĩa là không ai rotate, revoke, cleanup hay trả lời khi alert xảy ra.

## 27. Checklist production

### Identity và authorization

```text
[ ] Mỗi service có user riêng.
[ ] Không application user nào có administrator tag nếu không cần.
[ ] Production tách khỏi dev/staging bằng vhost hoặc cluster boundary phù hợp.
[ ] Permission configure/write/read đã được review.
[ ] Không có permission rộng hơn nhu cầu thực tế.
[ ] Permission negative test chạy trong CI.
```

### Network và transport

```text
[ ] AMQP production dùng TLS.
[ ] Client verify CA và hostname.
[ ] Management UI dùng HTTPS.
[ ] Port quản trị không public.
[ ] Firewall/security group chỉ cho phép source cần thiết.
[ ] Metrics endpoint không mở cho Internet.
```

### Secret và data

```text
[ ] Password/certificate nằm trong secret manager hoặc secret injection.
[ ] Không có secret trong Git, image hoặc log.
[ ] Có lịch rotate và expiry.
[ ] Có quy trình revoke khẩn cấp.
[ ] Message không chứa token, password hoặc PII không cần thiết.
[ ] Log và DLQ có retention/redaction policy.
```

### Governance

```text
[ ] Naming convention được tài liệu hóa.
[ ] Exchange/queue/vhost có owner.
[ ] Thay đổi permission có audit trail.
[ ] Có permission matrix.
[ ] Có runbook rotate và revoke.
[ ] On-call biết cách containment khi credential bị lộ.
```

## 28. Deliverable cuối tuần

Tạo thư mục:

```text
rabbitmq/week-13/
├── README.md
├── permission-matrix.md
├── topology-and-ownership.md
├── permission-tests/
├── tls/
├── secret-rotation-runbook.md
├── user-revocation-runbook.md
└── network-policy.md
```

`README.md` phải nêu:

- vhost của từng environment;
- service user nào tồn tại;
- ai sở hữu từng resource;
- cách chạy permission test;
- cách rotate password/certificate;
- cách revoke user trong incident.

Không commit password, private key, access token hoặc file secret thật vào deliverable.

## 29. Tiêu chí hoàn thành

Bạn hoàn thành Tuần 13 khi:

- Tạo được vhost dev và service user với quyền tối thiểu.
- Chứng minh thao tác publish/consume ngoài quyền bị từ chối.
- Có test permission chạy được trong CI hoặc integration environment.
- Hiểu đường đi của AMQP TLS và Management HTTPS.
- Có permission matrix cho order, payment và notification.
- Có tài liệu rotate secret và revoke user.
- Có network policy không expose management port công khai.
- Không đưa password, access token hoặc PII nhạy cảm vào message.

## 30. Câu hỏi tự kiểm tra

### Câu 1

`read` permission có cho phép publish vào exchange không?

**Đáp án:** Không. Publish cần `write`; consume/đọc message cần `read`.

### Câu 2

Vì sao application không nên dùng user có `administrator` tag?

**Đáp án:** Khi credential hoặc service bị compromise, attacker có thể thay đổi user, permission, topology và policy trên phạm vi rộng.

### Câu 3

TLS có ngăn message nhạy cảm xuất hiện trong DLQ không?

**Đáp án:** Không. TLS bảo vệ trên đường truyền; message trong queue/DLQ vẫn cần data minimization, encryption-at-rest và retention policy phù hợp.

### Câu 4

Nếu notification service chỉ consume queue, permission tối thiểu nên có gì?

**Đáp án:** `read` trên đúng queue; `configure` và `write` có thể để rỗng nếu topology được provision bên ngoài.

### Câu 5

Khi password bị commit vào Git, chỉ xóa commit có đủ không?

**Đáp án:** Không. Phải coi password đã bị lộ, revoke/rotate ngay, kiểm tra log/cache/fork và điều tra truy cập.

## 31. Chuẩn bị cho Tuần 14

Sau khi identity, permission, network boundary và secret lifecycle rõ ràng, bước tiếp theo là thiết kế topology production có high availability. Tuần 14 sẽ nối các nguyên tắc đó với cluster, quorum queues, failure domain, node failure và chiến lược vận hành khi một broker gặp sự cố.

# Tuần 2 — Kafka cluster, KRaft và CLI

> Mục tiêu: khởi động được Kafka local, hiểu các thành phần cơ bản của cluster và dùng CLI để quan sát, cô lập lỗi trước khi viết Java client.

## 1. Kết quả cần đạt

Sau bài học, bạn có thể:

- Phân biệt node, broker, controller và cluster.
- Giải thích KRaft, broker role, controller role, quorum và metadata log.
- Hiểu bootstrap server không phải là “broker duy nhất”.
- Phân biệt listener với advertised listener.
- Kết nối đúng từ host và từ container Docker.
- Dùng CLI để tạo topic, publish, consume và kiểm tra consumer group.
- Đọc partition, leader, offset, lag và topic configuration.
- Restart broker local và xác minh metadata sau restart.
- Đặt tên topic theo environment, domain và ownership.

## 2. Chuẩn bị môi trường

### Công cụ

- Docker Engine và Docker Compose.
- Apache Kafka version đã pin theo roadmap.
- JDK 17 hoặc 21 nếu muốn chạy Java client.
- Terminal có curl hoặc công cụ tương đương.
- Kafka CLI đi kèm distribution hoặc container Kafka.

Không dùng tag latest trong lab. Hãy ghi version cụ thể trong README để lệnh và hành vi có thể lặp lại.

### Kiểm tra Docker

Chạy:

    docker --version
    docker compose version

Nếu dùng image Kafka, kiểm tra image đã được pull đúng version:

    docker images | grep kafka

Tên image và đường dẫn CLI có thể thay đổi theo distribution. Khi lệnh trong bài có kafka-topics.sh, kafka-console-producer.sh hoặc kafka-console-consumer.sh, hãy chạy chúng bên trong container nếu máy host không cài Kafka CLI.

## 3. Kafka cluster là gì?

### 3.1 Node, broker và cluster

- Node là một tiến trình hoặc máy có định danh trong cluster.
- Broker là node có vai trò phục vụ lưu trữ partition và nhận request produce/fetch.
- Cluster là nhóm các node Kafka cùng quản lý một không gian metadata.

Trong lab một node, một tiến trình có thể vừa làm broker vừa làm controller. Trong production, các vai trò thường được phân bố trên nhiều node để tăng khả năng chịu lỗi.

### 3.2 Topic và partition ở góc nhìn cluster

Topic là tên logic của stream. Topic được chia thành partition. Broker lưu các partition và phục vụ request cho client.

Ví dụ:

    Cluster
      +-- broker-1: topic orders partition 0
      +-- broker-2: topic orders partition 1
      +-- broker-3: topic orders partition 2

Ở tuần 2, bạn chỉ cần hiểu cluster biết partition nào tồn tại và leader của partition nào đang ở broker nào. Cách chọn key, replication và ordering sẽ học sâu ở tuần 3.

### 3.3 Controller

Controller chịu trách nhiệm quản lý metadata và các thay đổi topology quan trọng, ví dụ:

- tạo hoặc xóa topic;
- thay đổi assignment;
- quản lý trạng thái broker;
- điều phối một số hoạt động bầu leader;
- ghi nhận metadata của cluster.

Trong kiến trúc KRaft, controller không cần ZooKeeper để lưu metadata. Controller sử dụng quorum để thống nhất trạng thái metadata.

### 3.4 Bootstrap server

Client thường nhận một hoặc vài địa chỉ bootstrap server để kết nối ban đầu:

    bootstrap.servers=localhost:9092

Bootstrap server chỉ là điểm vào ban đầu để client lấy metadata cluster. Sau đó client biết broker nào là leader của partition cần truy cập và kết nối tới broker phù hợp.

Vì vậy:

> bootstrap server không có nghĩa là toàn bộ traffic luôn đi qua một broker duy nhất.

Nếu cấu hình chỉ có một bootstrap address, client vẫn có thể nhận metadata của các broker khác, miễn là advertised listener trả về địa chỉ mà client truy cập được.

## 4. KRaft

### 4.1 KRaft là gì?

KRaft là kiến trúc Kafka dùng metadata quorum thay cho ZooKeeper. Metadata của cluster được quản lý bởi các controller trong một quorum.

KRaft giúp kiến trúc đơn giản hơn:

    Client --> Broker
                 ^
                 |
    Controller quorum quản lý metadata

Không nên hiểu KRaft là cơ chế thay thế toàn bộ broker. KRaft chủ yếu thay đổi cách Kafka quản lý metadata và controller.

### 4.2 Broker role và controller role

Một node có thể có:

- broker role: nhận produce/fetch, lưu partition;
- controller role: tham gia quorum và quản lý metadata;
- cả hai role: phù hợp cho lab nhỏ hoặc môi trường đơn giản.

Lab một node thường dùng:

    KAFKA_PROCESS_ROLES=broker,controller

Production cần cân nhắc tách controller và broker role để controller không bị ảnh hưởng bởi tải dữ liệu. Một quorum nên có số lượng controller phù hợp để duy trì majority khi mất node.

### 4.3 Quorum

Quorum là nhóm controller cùng thống nhất metadata. Majority là số thành viên tối thiểu cần để quorum tiếp tục hoạt động.

Ví dụ quorum ba controller:

    controller-1
    controller-2
    controller-3

Quorum vẫn có thể tiến hành khi mất một controller. Nếu mất hai, không còn majority để ghi metadata mới.

Đây là ví dụ khái niệm. Capacity, failure domain, disk và cấu hình production cần được thiết kế riêng.

### 4.4 Metadata log

Kafka lưu thay đổi metadata trong một log dành cho quorum. Log này khác với event topic nghiệp vụ mà application consume.

Metadata bao gồm thông tin như:

- topic tồn tại;
- partition nào thuộc topic;
- broker/node nào có mặt;
- cấu hình và assignment;
- trạng thái controller.

Phân biệt hai loại log:

| Loại | Mục đích |
|---|---|
| Metadata log | Duy trì trạng thái và topology Kafka |
| Data log | Lưu record trong các topic ứng dụng |

Không dùng metadata log như nơi lưu event nghiệp vụ.

## 5. Listener và advertised listener

Đây là phần dễ gây lỗi nhất khi chạy Kafka bằng Docker.

### 5.1 Listener

Listener là địa chỉ Kafka bind để nhận kết nối. Ví dụ:

    KAFKA_LISTENERS=PLAINTEXT://:9092

Dấu hai chấm trước port nghĩa là bind trên các interface phù hợp trong container.

### 5.2 Advertised listener

Advertised listener là địa chỉ Kafka trả cho client trong metadata:

    KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092

Client không chỉ kết nối bootstrap. Client còn nhận địa chỉ broker từ metadata. Nếu Kafka quảng bá hostname chỉ tồn tại bên trong Docker nhưng client chạy trên host, client có thể bootstrap được rồi thất bại ở bước kết nối broker.

### 5.3 Hai network phổ biến

Client chạy trên host:

    Application on host --> localhost:9092 --> Kafka container

Client chạy trong Docker network:

    Application container --> kafka:29092 --> Kafka container

Vì vậy có thể cần hai listener:

    INTERNAL://:29092,EXTERNAL://:9092

Và quảng bá hai địa chỉ tương ứng:

    INTERNAL://kafka:29092,EXTERNAL://localhost:9092

Tên listener chỉ là quy ước; điều quan trọng là mapping listener security protocol đúng.

### 5.4 Bảng chẩn đoán

| Client chạy ở đâu | Địa chỉ thường dùng |
|---|---|
| Host machine | localhost:9092 |
| Container cùng compose network | kafka:29092 hoặc hostname nội bộ |
| Máy khác trong mạng | DNS/IP có thể truy cập từ máy đó |
| Kubernetes | Service DNS/advertised address phù hợp |

Đừng dùng localhost một cách máy móc. localhost có ý nghĩa là máy của chính client. Trong container, localhost là chính container đó, không phải host machine.

## 6. Compose lab một node

Ví dụ cấu hình tối giản theo roadmap:

    services:
      kafka:
        image: apache/kafka:PINNED_VERSION
        ports:
          - "9092:9092"
        environment:
          KAFKA_NODE_ID: 1
          KAFKA_PROCESS_ROLES: broker,controller
          KAFKA_LISTENERS: PLAINTEXT://:9092,CONTROLLER://:9093
          KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
          KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
          KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka:9093
          KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
          KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
          KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1

Đây là cấu hình học local, không phải production.

Một số điểm cần hiểu:

- node id định danh node;
- process roles cho biết node làm broker, controller hay cả hai;
- listeners là nơi process bind;
- advertised listeners là địa chỉ trả cho client;
- controller listener dùng cho giao tiếp controller;
- quorum voters mô tả thành viên controller quorum;
- replication factor bằng 1 chỉ phù hợp local;
- internal Kafka topics phải có replication factor phù hợp với số broker.

Nếu image hoặc Kafka distribution dùng tên biến môi trường khác, tra README chính thức của image đang pin. Không trộn cấu hình của hai image khác nhau.

## 7. Quy trình khởi động và kiểm tra health

### Bước 1: khởi động

    docker compose up -d

### Bước 2: xem container

    docker compose ps

### Bước 3: xem log

    docker compose logs kafka

Tìm dấu hiệu broker đã start hoàn tất, listener đang bind và controller quorum đã khởi tạo. Không chỉ nhìn thấy container ở trạng thái running rồi kết luận Kafka đã sẵn sàng.

### Bước 4: kiểm tra API bằng CLI

Nếu CLI nằm trong container:

    docker compose exec kafka kafka-topics.sh \
      --bootstrap-server localhost:9092 \
      --list

Nếu CLI nằm trên host:

    kafka-topics.sh \
      --bootstrap-server localhost:9092 \
      --list

Một danh sách topic rỗng vẫn có thể là kết quả hợp lệ nếu cluster mới chưa tạo topic nghiệp vụ.

## 8. CLI: topic

### 8.1 Tạo topic

Tạo topic local:

    kafka-topics.sh \
      --bootstrap-server localhost:9092 \
      --create \
      --topic orders.dev.v1 \
      --partitions 3 \
      --replication-factor 1

Ý nghĩa:

- bootstrap-server: điểm kết nối lấy metadata;
- topic: tên topic;
- partitions: số partition;
- replication-factor: số replica mỗi partition.

### 8.2 Liệt kê topic

    kafka-topics.sh \
      --bootstrap-server localhost:9092 \
      --list

### 8.3 Mô tả topic

    kafka-topics.sh \
      --bootstrap-server localhost:9092 \
      --describe \
      --topic orders.dev.v1

Hãy đọc các trường:

- Topic;
- PartitionCount;
- ReplicationFactor;
- Partition;
- Leader;
- Replicas;
- Isr.

Ở tuần 2, bạn cần biết dùng lệnh này để kiểm tra topic có tồn tại, có bao nhiêu partition và leader đang ở đâu. Ý nghĩa sâu hơn của replica và ISR sẽ học ở tuần 3.

### 8.4 Topic config

Xem cấu hình topic:

    kafka-configs.sh \
      --bootstrap-server localhost:9092 \
      --entity-type topics \
      --entity-name orders.dev.v1 \
      --describe

Có thể xem một số config mặc định ở cấp broker bằng:

    kafka-configs.sh \
      --bootstrap-server localhost:9092 \
      --entity-type brokers \
      --entity-default \
      --describe

Đừng nhầm config không hiển thị với config không tồn tại. Nhiều config được kế thừa từ default của broker.

### 8.5 Xóa topic trong lab

Chỉ làm với topic test:

    kafka-topics.sh \
      --bootstrap-server localhost:9092 \
      --delete \
      --topic orders.dev.v1

Không xóa topic production hoặc topic có dữ liệu quan trọng chỉ vì muốn chạy lại lab. Với môi trường thật, phải xác nhận ownership, retention, consumer impact và quy trình change.

## 9. CLI: console producer

### 9.1 Gửi value đơn giản

    kafka-console-producer.sh \
      --bootstrap-server localhost:9092 \
      --topic orders.dev.v1

Sau đó nhập từng dòng:

    order-1001
    order-1002
    order-1003

Mỗi dòng là một record. Kết thúc bằng Ctrl+C.

### 9.2 Gửi key và value

Dùng delimiter để phân biệt key/value:

    kafka-console-producer.sh \
      --bootstrap-server localhost:9092 \
      --topic orders.dev.v1 \
      --property parse.key=true \
      --property key.separator=:

Nhập:

    order-1001:created
    order-1002:created
    order-1001:paid

Ở tuần 2, mục tiêu là thấy CLI có thể phát record có key. Việc key ảnh hưởng partition nào sẽ phân tích sâu ở tuần 3.

### 9.3 Gửi file

    kafka-console-producer.sh \
      --bootstrap-server localhost:9092 \
      --topic orders.dev.v1 < events.txt

Trong tài liệu chia sẻ cho team, nên ghi rõ shell syntax này có thể khác trên Windows PowerShell.

## 10. CLI: console consumer

### 10.1 Consume record mới

    kafka-console-consumer.sh \
      --bootstrap-server localhost:9092 \
      --topic orders.dev.v1

Consumer mặc định thường chờ record mới theo cấu hình. Mở terminal khác rồi chạy producer để thấy record xuất hiện.

### 10.2 Đọc từ earliest

    kafka-console-consumer.sh \
      --bootstrap-server localhost:9092 \
      --topic orders.dev.v1 \
      --from-beginning

Lệnh này hữu ích để kiểm tra record đã tồn tại. Nó không có nghĩa là “xóa offset cũ” hay “replay cho tất cả consumer”. Đây là một consumer invocation với hành vi offset riêng.

### 10.3 Hiển thị key, partition và offset

    kafka-console-consumer.sh \
      --bootstrap-server localhost:9092 \
      --topic orders.dev.v1 \
      --from-beginning \
      --property print.key=true \
      --property print.partition=true \
      --property print.offset=true

Dùng lệnh này khi cần chứng minh record đang nằm ở partition nào và offset nào.

### 10.4 Giới hạn số record

    kafka-console-consumer.sh \
      --bootstrap-server localhost:9092 \
      --topic orders.dev.v1 \
      --from-beginning \
      --max-messages 3

Đây là cách tốt để lab không bị treo chờ vô hạn.

## 11. CLI: consumer group, offset và lag

### 11.1 Tạo consumer group bằng lệnh consume

    kafka-console-consumer.sh \
      --bootstrap-server localhost:9092 \
      --topic orders.dev.v1 \
      --group orders-debug \
      --from-beginning

Group sẽ lưu offset của nó. Chạy lại cùng group thường tiếp tục từ offset đã commit, tùy cấu hình và trạng thái trước đó.

### 11.2 Liệt kê group

    kafka-consumer-groups.sh \
      --bootstrap-server localhost:9092 \
      --list

### 11.3 Mô tả group và lag

    kafka-consumer-groups.sh \
      --bootstrap-server localhost:9092 \
      --describe \
      --group orders-debug

Các trường quan trọng thường gồm:

- GROUP;
- TOPIC;
- PARTITION;
- CURRENT-OFFSET;
- LOG-END-OFFSET;
- LAG;
- CONSUMER-ID;
- HOST;
- CLIENT-ID.

Cách đọc:

    lag = log-end-offset - current-offset

Lag tăng có thể nghĩa là consumer chậm, consumer chết, record không được commit hoặc producer đang ghi nhanh hơn.

### 11.4 Đọc offset của group

Dùng describe group để xem group đang ở partition nào và offset nào. Không tự ý reset offset khi chưa hiểu tác động: reset có thể làm consumer replay hoặc bỏ qua dữ liệu.

### 11.5 Reset offset trong lab

Chỉ thử trên group test, khi không có consumer đang chạy:

    kafka-consumer-groups.sh \
      --bootstrap-server localhost:9092 \
      --group orders-debug \
      --topic orders.dev.v1 \
      --reset-offsets \
      --to-earliest \
      --execute

Các option reset thường gặp:

- to-earliest;
- to-latest;
- by-duration;
- to-offset;
- shift-by.

Luôn chạy với --dry-run trước nếu CLI hỗ trợ, đọc kết quả rồi mới execute. Reset offset không xóa record; nó chỉ thay đổi vị trí đọc của group.

## 12. CLI: broker và metadata

Tùy distribution, các lệnh metadata có thể nằm trong kafka-metadata-quorum.sh hoặc công cụ tương đương.

Ví dụ kiểm tra quorum:

    kafka-metadata-quorum.sh \
      --bootstrap-server localhost:9092 \
      describe --status

Mục tiêu của lab là nhận diện:

- leader ID của metadata quorum;
- voters;
- observers nếu có;
- high watermark;
- trạng thái quorum.

Nếu image không cung cấp lệnh này hoặc syntax khác, hãy xem help:

    kafka-metadata-quorum.sh --help

Không suy đoán tên lệnh giữa các version. Hãy ưu tiên CLI cùng version với broker.

## 13. Naming, environment prefix và ownership

Tên topic nên giúp người vận hành trả lời nhanh:

- topic thuộc domain nào?
- dùng cho môi trường nào?
- version/schema nào?
- ai chịu trách nhiệm?

Một convention dễ đọc:

    <environment>.<domain>.<event-name>.v<version>

Ví dụ:

    dev.orders.order-created.v1
    staging.payments.payment-authorized.v1
    prod.inventory.stock-reserved.v1

Một số team dùng domain trước environment. Không có một format bắt buộc, nhưng phải thống nhất và ghi vào README.

### Nguyên tắc đặt tên

- tránh tên mơ hồ như data, events, messages;
- không nhúng thông tin bí mật vào tên topic;
- tránh đổi nghĩa topic sau khi đã có consumer;
- version topic khi thay đổi không tương thích;
- ghi owner và retention;
- xác định topic chứa event hay command;
- thống nhất chữ thường, dấu chấm hoặc dấu gạch ngang.

### Topic ownership table

| Topic | Loại | Owner | Consumer group | Retention |
|---|---|---|---|---|
| dev.orders.order-created.v1 | Event | order-team | payment, inventory, analytics | lab |
| dev.payments.payment-authorized.v1 | Event | payment-team | order-status, analytics | lab |
| dev.inventory.stock-reserved.v1 | Event | inventory-team | order-status | lab |

Ownership không phải thủ tục hành chính thừa. Khi topic lỗi, lag hoặc cần đổi schema, phải biết ai chịu trách nhiệm.

## 14. Bài lab hoàn chỉnh

### Lab 1: khởi động cluster

1. Pin Kafka image version.
2. Tạo compose file local.
3. Chạy docker compose up -d.
4. Kiểm tra docker compose ps.
5. Đọc log broker.
6. Dùng kafka-topics.sh --list.
7. Ghi lại advertised listener đang dùng.

Kết quả cần có:

- container running;
- broker accept được bootstrap connection;
- controller quorum ở trạng thái hợp lệ;
- CLI list topic không báo lỗi connection.

### Lab 2: tạo và mô tả topic

Tạo topic:

    kafka-topics.sh \
      --bootstrap-server localhost:9092 \
      --create \
      --topic dev.orders.order-created.v1 \
      --partitions 3 \
      --replication-factor 1

Sau đó:

1. list topic;
2. describe topic;
3. ghi lại PartitionCount;
4. ghi lại Leader của từng partition;
5. xem topic config;
6. giải thích vì sao replication factor 1 phù hợp local nhưng không phù hợp production.

### Lab 3: publish và consume

Terminal A:

    kafka-console-consumer.sh \
      --bootstrap-server localhost:9092 \
      --topic dev.orders.order-created.v1 \
      --group orders-lab \
      --from-beginning \
      --property print.partition=true \
      --property print.offset=true

Terminal B:

    kafka-console-producer.sh \
      --bootstrap-server localhost:9092 \
      --topic dev.orders.order-created.v1

Gửi ít nhất năm record. Sau đó:

1. xem record ở terminal A;
2. mô tả group orders-lab;
3. ghi lại current offset, log end offset và lag;
4. dừng consumer;
5. gửi thêm record;
6. khởi động lại cùng group;
7. quan sát consumer tiếp tục ở đâu.

### Lab 4: earliest và latest

1. Gửi năm record trước khi chạy consumer mới.
2. Chạy group mới với from-beginning.
3. Chạy group khác không có from-beginning.
4. So sánh dữ liệu nhận được.
5. Gửi record mới sau khi cả hai group đang chạy.
6. Mô tả sự khác nhau giữa offset ban đầu và record mới.

Lưu ý: hành vi chính xác còn phụ thuộc auto.offset.reset và group đã có committed offset hay chưa.

### Lab 5: restart broker

1. Chạy consumer group.
2. Gửi record.
3. Ghi metadata topic.
4. Restart container:

    docker compose restart kafka

5. Chờ broker ready.
6. Chạy lại topic describe.
7. Chạy group describe.
8. Kiểm tra record mới có thể publish/consume hay không.

Cần phân biệt:

- container restart thành công;
- broker đã sẵn sàng nhận request;
- metadata đã được load;
- consumer đã reconnect;
- record có bị mất hay chỉ đang chờ reconnect.

### Lab 6: mô phỏng lỗi listener

Cố tình dùng địa chỉ không tồn tại trong advertised listener, sau đó quan sát:

1. bootstrap có thể thành công hay không;
2. metadata trả hostname nào;
3. client fail ở bước nào;
4. log client báo lỗi DNS, connection refused hay timeout;
5. sửa advertised listener;
6. kiểm tra lại từ host.

Mục tiêu là hiểu lỗi có thể xảy ra sau bootstrap, không vội kết luận Kafka “không chạy”.

## 15. Cây chẩn đoán lỗi CLI

### Lỗi không kết nối được bootstrap

Kiểm tra theo thứ tự:

1. container có running không;
2. port có publish đúng không;
3. listener có bind đúng port không;
4. bootstrap address có đúng vị trí client không;
5. Kafka đã ready hay mới chỉ running;
6. firewall hoặc port conflict;
7. broker log có bind/config error không.

### Bootstrap được nhưng không consume/produce

Khả năng cao:

- advertised listener trả hostname không thể resolve;
- client ở host nhưng dùng hostname nội bộ Docker;
- client ở container nhưng dùng localhost;
- port advertised khác port exposed;
- security protocol mapping sai;
- broker trả metadata của địa chỉ cũ.

### Topic không tồn tại

Kiểm tra:

    kafka-topics.sh --bootstrap-server localhost:9092 --list

Sau đó:

- kiểm tra typo tên topic;
- kiểm tra đang dùng đúng cluster;
- kiểm tra create command có báo lỗi;
- không nhầm environment prefix;
- xem log broker.

### Consumer không thấy record

Kiểm tra:

- đang consume đúng topic không;
- group đã có offset cũ chưa;
- có dùng from-beginning hoặc auto.offset.reset phù hợp không;
- producer có gửi vào cluster khác không;
- key/value có bị lỗi serialization ở client thật không;
- record có được gửi trước khi consumer bắt đầu không.

### Lag không giảm

Kiểm tra:

- consumer process có còn sống không;
- group có assignment không;
- downstream có chậm không;
- có exception làm poll loop dừng không;
- số partition có đủ để scale không;
- offset có thực sự commit không.

## 16. Sai lầm phổ biến

### Nhầm container running với Kafka ready

Container process còn sống không đảm bảo controller và broker đã sẵn sàng. Luôn kiểm tra bằng CLI.

### Nhầm bootstrap server với broker duy nhất

Bootstrap chỉ giúp lấy metadata ban đầu. Đừng thiết kế client chỉ biết một broker trong tư duy vận hành.

### Dùng localhost từ container

localhost bên trong app container là app container, không phải Kafka container và cũng không phải host.

### Chỉ sửa ports nhưng quên advertised listeners

Port mapping giúp đi vào container, nhưng metadata có thể vẫn trả địa chỉ không truy cập được.

### Reset offset tùy tiện

Reset offset có thể replay side effect hoặc bỏ qua dữ liệu. Chỉ dùng group test khi lab.

### Xóa topic để “làm sạch”

Xóa topic là thay đổi dữ liệu khó phục hồi. Trong lab có thể làm có chủ đích; production cần quy trình và xác nhận.

### Dùng config của ZooKeeper cho KRaft

Tutorial cũ có thể chứa ZooKeeper, broker id hoặc biến cấu hình không phù hợp. Hãy xác nhận kiến trúc và version trước khi copy.

## 17. Bài kiểm tra lý thuyết

1. Node khác broker ở điểm nào?
2. Cluster gồm những thành phần nào?
3. Controller làm nhiệm vụ gì?
4. KRaft thay thế vai trò nào của ZooKeeper?
5. Broker role khác controller role thế nào?
6. Vì sao controller quorum cần majority?
7. Metadata log khác data log thế nào?
8. Bootstrap server có phải broker duy nhất không?
9. Listener và advertised listener khác nhau thế nào?
10. Tại sao client chạy trong Docker không nên dùng localhost của host?
11. Tạo topic cần quan tâm những tham số nào?
12. Leader, replicas và ISR trong topic describe dùng để làm gì?
13. from-beginning có reset offset của mọi group không?
14. Lag được tính khái niệm như thế nào?
15. Vì sao topic ownership quan trọng?
16. Khi nào cần dùng --dry-run trước reset offset?
17. Restart container có đồng nghĩa broker đã ready không?
18. Vì sao replication factor 1 chỉ phù hợp local?

### Đáp án ngắn

1. Node là định danh/tiến trình; broker là node phục vụ dữ liệu Kafka.
2. Broker, controller và metadata của cluster.
3. Quản lý metadata và thay đổi topology.
4. Cơ chế quản lý metadata/controller coordination.
5. Broker phục vụ data request; controller quản lý metadata quorum.
6. Để quorum vẫn có thể quyết định khi mất một phần node.
7. Metadata log giữ trạng thái Kafka; data log giữ record ứng dụng.
8. Không, chỉ là điểm vào để lấy metadata.
9. Listener bind địa chỉ; advertised listener là địa chỉ trả cho client.
10. localhost trong container trỏ về chính container client.
11. Topic name, partitions, replication factor và config.
12. Xác định partition leader và replica hiện tại.
13. Không, nó chỉ ảnh hưởng invocation/group đang chạy.
14. Log end offset trừ current committed offset.
15. Biết ai sửa schema, xử lý lag và chịu trách nhiệm vận hành.
16. Để xem tác động trước khi thay đổi thật.
17. Không; cần kiểm tra readiness bằng CLI.
18. Mất broker là mất replica, không có khả năng chịu lỗi.

## 18. Checklist hoàn thành Tuần 2

- [ ] Tôi khởi động được Kafka local bằng version đã pin.
- [ ] Tôi phân biệt node, broker, controller và cluster.
- [ ] Tôi giải thích được KRaft và metadata quorum.
- [ ] Tôi biết broker role và controller role.
- [ ] Tôi không nhầm bootstrap server với broker duy nhất.
- [ ] Tôi hiểu listener và advertised listener.
- [ ] Tôi biết khác biệt host networking và Docker networking.
- [ ] Tôi tạo, list và describe được topic.
- [ ] Tôi publish được bằng console producer.
- [ ] Tôi consume được từ beginning và theo group.
- [ ] Tôi xem được current offset, log end offset và lag.
- [ ] Tôi xem được topic config và partition leader.
- [ ] Tôi restart broker rồi kiểm tra lại metadata.
- [ ] Tôi biết dùng CLI để cô lập lỗi trước khi sửa application code.
- [ ] Tôi có convention topic name và bảng ownership.

## 19. Câu chốt cần nhớ

Kafka CLI không chỉ để gửi vài message thử nghiệm. CLI là bộ kính hiển vi giúp bạn kiểm tra cluster, metadata, topic, offset, consumer group và network trước khi đổ lỗi cho code ứng dụng.

Bốn ý cần nhớ:

1. Bootstrap server chỉ là điểm vào lấy metadata, không phải broker duy nhất.
2. KRaft dùng controller quorum để quản lý metadata thay cho ZooKeeper.
3. Advertised listener phải là địa chỉ mà chính client có thể truy cập.
4. Khi debug Kafka, kiểm tra từ cluster đến topic rồi consumer group trước khi sửa Java code.


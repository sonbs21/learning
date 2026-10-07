# Tuần 8: Docker networking

## 1. Mục tiêu

Sau bài này, bạn có thể:

- Giải thích network namespace và cách container nhận network interface riêng.
- Phân biệt bridge network, custom bridge network và host network ở mức sử dụng.
- Tạo, inspect, connect và disconnect Docker network.
- Cho container gọi nhau bằng service/container name qua Docker DNS.
- Phân biệt container port, host port, `EXPOSE` và port publishing.
- Hiểu vì sao `localhost` bên trong container không phải host hoặc container khác.
- Thiết kế internal-only network và mô hình frontend/backend.
- Chạy API, Redis và database với boundary network rõ ràng.
- Điều tra lỗi DNS, port, route và connectivity bằng Docker CLI.
- Biết giới hạn của network isolation và các nguyên tắc bảo mật cơ bản.

Thời lượng đề xuất: **8–12 giờ** gồm lý thuyết, lab và bài tập.

---

## 2. Network trong Docker gồm những lớp nào?

Khi một container giao tiếp với container khác hoặc host, có nhiều lớp tham gia:

~~~text
Application
    ↓ hostname + port
Container network namespace
    ↓ virtual Ethernet/interface
Docker network driver
    ↓ bridge/NAT/routing
Host network stack
    ↓
Host/LAN/Internet
~~~

Một lỗi “không kết nối được” có thể nằm ở:

- Application listen sai port/interface.
- Dùng sai hostname, đặc biệt là dùng `localhost`.
- Container chưa cùng network.
- DNS name không tồn tại.
- Port chỉ listen nội bộ nhưng chưa publish.
- Host firewall hoặc network policy.
- Service chưa ready dù container đang running.
- Protocol/TLS/credential sai dù TCP đã kết nối.

Docker networking giúp tạo đường đi và tên; nó không tự đảm bảo application protocol đúng.

---

## 3. Network namespace

Mỗi container thường có network namespace riêng. Namespace này có thể chứa:

- Network interface.
- IP address.
- Routing table.
- Loopback interface.
- Port binding riêng trong namespace.

Mental model:

~~~text
Host namespace
├── docker bridge / host interfaces
└── Container namespace A
    ├── eth0
    ├── 172.x.x.x
    └── lo (127.0.0.1)

Container namespace B
├── eth0
├── 172.x.x.x
└── lo (127.0.0.1)
~~~

Container A và B có thể cùng dùng port `8080` trong namespace riêng mà không xung đột. Xung đột xảy ra khi publish cùng một host port:

~~~bash
docker run -d --name api-a -p 8080:8080 image-a
docker run -d --name api-b -p 8080:8080 image-b
~~~

Container thứ hai sẽ lỗi vì host port `8080` đã được dùng, dù cả hai app đều listen `8080` bên trong container.

### 3.1. Loopback và `localhost`

`127.0.0.1` luôn có nghĩa là loopback của network namespace hiện tại.

Trong container API:

~~~text
http://localhost:8080
→ API container chính nó, port 8080
~~~

Nó không có nghĩa:

- Host machine.
- Container database.
- Container Redis.
- Container frontend.

Đây là nguyên nhân phổ biến khi app trong container cố gọi `localhost:5432` để tìm PostgreSQL đang ở container khác.

---

## 4. Docker bridge network

Bridge network tạo một mạng ảo trên host để container kết nối với nhau. Container thường có interface trong namespace và được nối vào bridge qua virtual Ethernet pair.

~~~text
Container A ─┐
Container B ─┼── Docker bridge ── host network/NAT
Container C ─┘
~~~

Docker network mặc định có thể cho container giao tiếp theo cách hạn chế. Trong ứng dụng thực tế, nên tạo custom bridge network để có DNS theo tên và boundary rõ ràng.

Liệt kê network:

~~~bash
docker network ls
~~~

Xem network mặc định:

~~~bash
docker network inspect bridge
~~~

Không nên phụ thuộc IP cố định của container. Container có thể được tạo lại và nhận IP khác; dùng network name/service name.

---

## 5. Custom network và Docker DNS

Tạo custom bridge network:

~~~bash
docker network create app-net
docker network ls
docker network inspect app-net
~~~

Chạy Redis trên network:

~~~bash
docker run -d --name redis --network app-net redis:7.4
~~~

Container tạm thời cùng network gọi Redis bằng tên `redis`:

~~~bash
docker run --rm --network app-net redis:7.4 redis-cli -h redis ping
~~~

Kết quả mong đợi:

~~~text
PONG
~~~

Trong custom network, Docker cung cấp DNS nội bộ để name `redis` phân giải tới IP của container Redis. Client không cần biết IP cụ thể.

### 5.1. Name resolution không phải service readiness

DNS phân giải được `redis` không có nghĩa Redis đã sẵn sàng. Có thể xảy ra:

1. Tên đã tồn tại trong DNS.
2. Container đang start.
3. Process chưa listen.
4. Client kết nối thất bại.

Ứng dụng cần retry/backoff hoặc orchestration health/dependency strategy. Không dùng một lần `ping` DNS để kết luận service đã ready.

### 5.2. Kiểm tra DNS từ container

Dùng image có tool DNS/network phù hợp:

~~~bash
docker run --rm --network app-net busybox nslookup redis
docker run --rm --network app-net busybox ping -c 1 redis
~~~

`ping` chỉ kiểm tra ICMP/reachability và có thể bị disable; TCP/application check đáng tin hơn:

~~~bash
docker run --rm --network app-net redis:7.4 redis-cli -h redis ping
~~~

### 5.3. Container name và network alias

Có thể thêm alias khi connect:

~~~bash
docker network connect --alias cache app-net redis
docker run --rm --network app-net redis:7.4 redis-cli -h cache ping
~~~

Alias nên dùng có chủ đích. Service name ổn định và dễ đọc hơn việc phụ thuộc container ID hoặc IP.

---

## 6. `docker network` commands

### 6.1. Inspect

~~~bash
docker network inspect app-net
~~~

Chú ý:

- Driver.
- Subnet/gateway.
- Containers đang kết nối.
- Container IP.
- Options/labels.

### 6.2. Connect và disconnect

Tạo container trước rồi nối network:

~~~bash
docker run -d --name toolbox alpine:3.20 sleep 3600
docker network connect app-net toolbox
docker network inspect app-net
docker network disconnect app-net toolbox
~~~

Một container có thể kết nối nhiều network:

~~~bash
docker network connect app-net toolbox
docker network connect bridge toolbox
docker network inspect toolbox 2>/dev/null || docker inspect toolbox
~~~

Trong thiết kế production, nhiều network có thể dùng để tách frontend/backend, nhưng cần hiểu routing và interface thực tế. Không nối container vào network rộng hơn nhu cầu.

### 6.3. Remove network

Network không còn container dùng có thể xóa:

~~~bash
docker network disconnect app-net redis
docker network rm app-net
~~~

Nếu network còn endpoint, Docker sẽ không cho remove bình thường. Kiểm tra trước khi disconnect/remove.

Dọn lab:

~~~bash
docker rm -f redis toolbox 2>/dev/null || true
docker network rm app-net 2>/dev/null || true
~~~

Chỉ chạy cleanup với resource lab của mình.

---

## 7. Container port, host port, `EXPOSE` và publishing

### 7.1. Container port

Container port là port process listen trong network namespace của container. Ví dụ Spring Boot listen `8080`:

~~~text
Container namespace: 0.0.0.0:8080
~~~

Container khác cùng Docker network có thể gọi `http://api:8080` mà không cần host port publishing.

### 7.2. Host port

Host port là port trên host được Docker bind và chuyển traffic vào container:

~~~bash
docker run -d --name api -p 8080:8080 task-api:0.3.0
~~~

Format:

~~~text
-p HOST_PORT:CONTAINER_PORT
~~~

Có thể map port khác nhau:

~~~bash
docker run -d --name api -p 9000:8080 task-api:0.3.0
~~~

Client trên host dùng `localhost:9000`; client trong cùng Docker network dùng `api:8080`.

### 7.3. Bind vào host interface cụ thể

Publish mặc định có thể bind trên các interface host tùy Docker configuration. Có thể giới hạn loopback cho local-only:

~~~bash
docker run -d --name api -p 127.0.0.1:8080:8080 task-api:0.3.0
~~~

Điều này khác `-p 0.0.0.0:8080:8080`, vốn có thể expose ra các interface host. Chọn binding theo threat model và môi trường.

### 7.4. `EXPOSE`

Trong Dockerfile:

~~~dockerfile
EXPOSE 8080
~~~

`EXPOSE` chỉ là metadata/documentation. Nó không publish port và không tạo firewall rule. Muốn truy cập từ host, dùng `-p`; muốn container khác truy cập, đặt cùng network và dùng service name/port.

### 7.5. Xem port mapping

~~~bash
docker port api
docker inspect --format '{{json .NetworkSettings.Ports}}' api
~~~

---

## 8. Internal-only network

Mục tiêu của internal-only network là giữ service không cần nhận traffic từ host/external network.

Tạo network internal:

~~~bash
docker network create --internal backend-net
~~~

Chạy database trên network này, không publish port:

~~~bash
docker run -d --name postgres \
  --network backend-net \
  -e POSTGRES_USER=app \
  -e POSTGRES_PASSWORD=local-only \
  -e POSTGRES_DB=appdb \
  postgres:16
~~~

API cùng network gọi:

~~~text
jdbc:postgresql://postgres:5432/appdb
~~~

Không dùng:

~~~text
jdbc:postgresql://localhost:5432/appdb
~~~

và không cần:

~~~bash
-p 5432:5432
~~~

nếu chỉ API trong Docker cần truy cập database.

### 8.1. Internal network không thay thế authentication

Không publish port không có nghĩa database “an toàn tuyệt đối”. Vẫn cần:

- Credential mạnh và secret management.
- Authentication/authorization.
- Network policy/firewall phù hợp.
- Patch base image/database.
- Không log password.
- Backup và audit.

Network isolation là một lớp defense-in-depth.

### 8.2. Cảnh báo về host access

`--internal` giới hạn egress theo Docker network behavior, nhưng behavior có thể phụ thuộc platform/Docker Desktop. Không dùng assumption thay cho firewall/security policy trong production.

---

## 9. Mô hình frontend/backend

Mô hình phổ biến:

~~~text
Internet/Browser
       │
       ▼
Frontend/Reverse proxy ─── frontend-net ─── API
                                             │
                                             ▼
                                      backend-net
                                      ├── PostgreSQL
                                      └── Redis
~~~

Thiết kế:

- Frontend/proxy có network hướng ngoài và frontend network.
- API nối frontend network và backend network.
- Database/Redis chỉ nối backend network.
- Chỉ publish port frontend hoặc API gateway.
- Database/Redis không publish ra host nếu không cần debug local.

Trong Docker CLI, một container có thể nối nhiều network. Trong Compose, khai báo network rõ ràng giúp đọc architecture dễ hơn.

### 9.1. Ví dụ Docker CLI

~~~bash
docker network create frontend-net
docker network create --internal backend-net

docker run -d --name api \
  --network frontend-net \
  task-api:0.3.0

docker network connect backend-net api

docker run -d --name postgres \
  --network backend-net \
  -e POSTGRES_PASSWORD=local-only \
  postgres:16
~~~

API có thể gọi `postgres:5432`; PostgreSQL không nhìn thấy frontend network nếu không được connect vào đó.

### 9.2. Không hiểu nhầm “network không nhìn thấy”

Một service có thể có route tới network khác tùy cách container được nối và routing. Network segmentation giúp giảm exposure và tạo boundary, nhưng cần kiểm tra thực tế bằng `inspect`, policy và test connectivity. Không tuyên bố isolation mạnh hơn điều Docker driver/platform cung cấp.

---

## 10. Host network ở mức nhận biết

`--network host` cho container dùng network namespace của host trên các platform hỗ trợ tương ứng:

~~~bash
docker run --network host nginx:1.27
~~~

Đặc điểm:

- Ít lớp NAT/port publishing hơn.
- Container gần như dùng trực tiếp host network stack.
- Giảm network isolation.
- Port conflict với host dễ xảy ra.
- Behavior khác giữa Linux và Docker Desktop/VM.

Không dùng host network chỉ vì “nhanh hơn” mà chưa đo và chưa đánh giá security/isolation. Hầu hết app học Docker nên bắt đầu với bridge/custom network.

---

## 11. Docker Compose networking

Compose thường tạo default network cho project. Service gọi nhau bằng **service name**:

~~~yaml
services:
  api:
    image: task-api:0.3.0
    environment:
      SPRING_DATA_REDIS_HOST: redis
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/appdb
    depends_on:
      - postgres
      - redis

  postgres:
    image: postgres:16

  redis:
    image: redis:7.4
~~~

Trong Compose:

- `api` gọi database bằng `postgres:5432`.
- `api` gọi Redis bằng `redis:6379`.
- `localhost` bên trong `api` vẫn chỉ là `api`.
- `ports` dùng để publish ra host, không cần cho service-to-service traffic nội bộ.

Ví dụ custom networks:

~~~yaml
services:
  frontend:
    image: frontend:1.0.0
    ports:
      - "8080:80"
    networks:
      - frontend-net

  api:
    image: task-api:0.3.0
    networks:
      - frontend-net
      - backend-net

  postgres:
    image: postgres:16
    networks:
      - backend-net

  redis:
    image: redis:7.4
    networks:
      - backend-net

networks:
  frontend-net:
  backend-net:
    internal: true
~~~

Không publish PostgreSQL/Redis nếu chỉ API cần dùng. `depends_on` không tự đảm bảo service ready; cần healthcheck/retry ở các bài sau.

---

## 12. DNS và service name

### 12.1. Vì sao không dùng IP?

IP container có thể thay đổi khi container được recreate. Service name bền hơn:

~~~text
postgres:5432
redis:6379
api:8080
~~~

Ứng dụng nên đọc hostname/port từ environment/config:

~~~properties
spring.datasource.url=jdbc:postgresql://${DB_HOST:postgres}:${DB_PORT:5432}/appdb
spring.data.redis.host=${REDIS_HOST:redis}
spring.data.redis.port=${REDIS_PORT:6379}
~~~

Local chạy ngoài Docker có thể override `DB_HOST=localhost`; chạy trong Docker dùng `DB_HOST=postgres`.

### 12.2. DNS alias và scale

Khi scale service, DNS/service discovery có thể trả nhiều endpoint tùy Compose/orchestrator. Ứng dụng cần connection/retry behavior phù hợp và không cache IP vô thời hạn.

### 12.3. DNS không giải quyết protocol

Nếu `redis` resolve được nhưng dùng HTTP vào port Redis, vẫn lỗi. Debug theo tầng:

~~~text
Name resolve → TCP connect → protocol handshake → authentication → application operation
~~~

---

## 13. Lab tổng hợp: Redis trên custom network

Đúng theo roadmap:

~~~bash
docker network create app-net
docker run -d --name redis --network app-net redis:7.4
docker run --rm --network app-net redis:7.4 redis-cli -h redis ping
~~~

Kiểm tra network và DNS:

~~~bash
docker network inspect app-net
docker run --rm --network app-net busybox nslookup redis
docker run --rm --network app-net redis:7.4 redis-cli -h redis SET lesson networking
docker run --rm --network app-net redis:7.4 redis-cli -h redis GET lesson
~~~

Thử sai để hiểu `localhost`:

~~~bash
docker run --rm --network app-net redis:7.4 redis-cli -h localhost ping
~~~

Lệnh cuối sẽ tìm Redis trong container tạm thời, không phải container `redis`, nên thường thất bại.

Dọn:

~~~bash
docker rm -f redis
docker network rm app-net
~~~

---

## 14. Lab API–PostgreSQL–Redis với network boundary

Tạo hai network:

~~~bash
docker network create frontend-net
docker network create --internal backend-net
~~~

Chạy database và Redis chỉ ở backend:

~~~bash
docker run -d --name week8-postgres \
  --network backend-net \
  -e POSTGRES_USER=app \
  -e POSTGRES_PASSWORD=local-only \
  -e POSTGRES_DB=appdb \
  postgres:16

docker run -d --name week8-redis \
  --network backend-net \
  redis:7.4
~~~

Chạy API với frontend network, sau đó nối thêm backend network:

~~~bash
docker run -d --name week8-api \
  --network frontend-net \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://week8-postgres:5432/appdb \
  -e SPRING_DATA_REDIS_HOST=week8-redis \
  task-api:0.3.0

docker network connect backend-net week8-api
~~~

Lưu ý: hostname container `week8-postgres` và `week8-redis` chỉ resolve trong network mà container tương ứng cùng tham gia. Nếu API chưa ở backend network, nó không gọi được database/Redis.

Kiểm tra:

~~~bash
docker network inspect frontend-net
docker network inspect backend-net
docker inspect week8-api
docker logs week8-api
~~~

Chỉ publish API nếu cần truy cập từ host:

~~~bash
docker rm -f week8-api
docker run -d --name week8-api \
  --network frontend-net \
  -p 8080:8080 \
  task-api:0.3.0
docker network connect backend-net week8-api
~~~

Trong lab thực tế, truyền đầy đủ environment/secret phù hợp với image app của bạn.

Dọn:

~~~bash
docker rm -f week8-api week8-postgres week8-redis
docker network rm frontend-net backend-net
~~~

---

## 15. Debug networking theo tầng

### 15.1. Kiểm tra container có running không

~~~bash
docker ps -a
docker logs <container>
docker inspect --format 'status={{.State.Status}} exit={{.State.ExitCode}}' <container>
~~~

### 15.2. Kiểm tra network membership

~~~bash
docker inspect --format '{{json .NetworkSettings.Networks}}' <container>
docker network inspect <network>
~~~

Hai container muốn gọi bằng name phải cùng network phù hợp.

### 15.3. Kiểm tra process listen port

~~~bash
docker exec <container> sh -c 'ss -lntp || netstat -lntp || true'
~~~

Image tối giản có thể không có `ss` hoặc `netstat`. Dùng tool debug phù hợp hoặc kiểm tra qua application logs/health endpoint.

### 15.4. Kiểm tra DNS

~~~bash
docker run --rm --network <network> busybox nslookup <service-name>
~~~

Nếu DNS fail, kiểm tra name, network, alias và container endpoint.

### 15.5. Kiểm tra TCP/application

~~~bash
docker run --rm --network <network> busybox nc -vz <service-name> <port>
~~~

Với Redis:

~~~bash
docker run --rm --network <network> redis:7.4 redis-cli -h <service-name> ping
~~~

Với HTTP:

~~~bash
docker run --rm --network <network> curlimages/curl:latest http://<service-name>:8080/actuator/health
~~~

Pin version tool image trong script/CI khi cần reproducibility; `latest` ở đây chỉ là ví dụ learning.

### 15.6. Kiểm tra từ host

~~~bash
docker port <container>
curl http://localhost:<published-host-port>
~~~

Nếu container-to-container chạy nhưng host không truy cập được, xem port publishing/binding. Nếu host truy cập được nhưng container-to-container fail, xem network membership/DNS/listen address.

---

## 16. Listen address và binding

Application có thể bind vào `127.0.0.1` hoặc `0.0.0.0` trong container.

- `127.0.0.1:8080`: chỉ loopback trong container; traffic từ network interface khác có thể không vào được.
- `0.0.0.0:8080`: listen trên các interface phù hợp trong container.

Spring Boot thường cần listen đúng interface khi chạy container:

~~~properties
server.address=0.0.0.0
server.port=8080
~~~

Không nhầm `server.address` với host port. Host port mapping chỉ chuyển traffic; application vẫn phải listen đúng interface/port bên trong container.

---

## 17. Network security cơ bản

Một số nguyên tắc:

- Chỉ publish port cần truy cập từ host/external.
- Database, Redis và broker thường không publish trong production nếu API là client duy nhất.
- Tách frontend/backend network.
- Dùng authentication dù service nằm trong internal network.
- Không coi container name/DNS là security boundary duy nhất.
- Giới hạn network capability/host mode khi không cần.
- Không dùng host network để né cấu hình mà chưa đánh giá exposure.
- Log lỗi kết nối nhưng không log credential.
- Test cả positive path và negative path: frontend không được truy cập DB trực tiếp.

Network isolation là một phần defense-in-depth; nó không thay thế TLS, auth, authorization, firewall và policy của platform.

---

## 18. Bài tập tự làm

### Bài 1: DNS bằng service name

Tạo `app-net`, chạy Redis, dùng container tạm thời gọi `redis` bằng `redis-cli`. Sau đó chạy cùng command với `-h localhost` và giải thích kết quả.

### Bài 2: Internal database

Tạo `backend-net --internal`, chạy PostgreSQL không publish port, chạy một client cùng network gọi PostgreSQL bằng service name. Chứng minh host không thể dùng `localhost:5432` để truy cập database đó nếu không publish.

### Bài 3: Frontend/backend boundary

Tạo frontend network và backend network. API nối cả hai; database chỉ nối backend. Test:

- API gọi được database.
- Container chỉ ở frontend không resolve/connect được database.
- Chỉ API hoặc frontend được publish ra host.

### Bài 4: Port mapping

Chạy app listen port `8080` trong container nhưng publish thành `9000:8080`. Kiểm tra client host dùng `9000`, client cùng network dùng `service-name:8080`.

### Bài 5: Debug connectivity

Cố ý sai từng lớp: sai service name, khác network, sai port, app bind `127.0.0.1`, app chưa ready. Dùng `ps`, `logs`, `inspect`, `nslookup`, `nc`/client protocol để tìm nguyên nhân.

---

## 19. Câu hỏi kiểm tra và đáp án gợi ý

1. Network namespace cung cấp những gì cho container?
2. Vì sao hai container có thể cùng listen port 8080 nhưng không thể cùng publish host port 8080?
3. `localhost` trong container trỏ tới đâu?
4. Vì sao nên gọi service bằng name thay vì IP container?
5. `EXPOSE` có publish port không?
6. Container-to-container traffic có cần `-p` không?
7. Internal-only network có thay thế authentication không?
8. Vì sao DNS resolve được nhưng app vẫn connect fail?
9. `--network host` có trade-off gì?
10. Vì sao frontend không nên nối trực tiếp database trong mô hình frontend/backend?
11. App bind `127.0.0.1` khác `0.0.0.0` thế nào trong container?
12. `depends_on` có bảo đảm service ready không?

Đáp án ngắn: namespace có interface/IP/route/loopback riêng; host port là tài nguyên chung; localhost là container hiện tại; IP có thể đổi khi recreate; `EXPOSE` chỉ metadata; cùng network gọi trực tiếp bằng service name/port; internal network không thay auth; DNS không bảo đảm process ready/protocol đúng; host network giảm isolation và dễ conflict; frontend không cần biết/truy cập DB trực tiếp; `127.0.0.1` chỉ loopback container còn `0.0.0.0` listen interface; `depends_on` không mặc định đảm bảo readiness.

---

## 20. Tiêu chí hoàn thành tuần 8

- [ ] Giải thích được network namespace và bridge network.
- [ ] Tạo/inspect/connect/disconnect/remove custom network.
- [ ] Cho container gọi nhau bằng service/container name.
- [ ] Test được Docker DNS và TCP/application connectivity.
- [ ] Phân biệt container port, host port, `EXPOSE` và `-p`.
- [ ] Giải thích đúng `localhost` bên trong container.
- [ ] Chạy được Redis trên custom network như roadmap.
- [ ] Tạo được internal-only backend network.
- [ ] Thiết kế được frontend/backend network boundary.
- [ ] Chỉ publish port cần thiết.
- [ ] Điều tra được lỗi DNS, network membership, bind address và port.
- [ ] Hiểu host network ở mức trade-off.
- [ ] Không coi network isolation là thay thế cho authentication/security policy.

Kiểm tra cuối buổi:

~~~bash
docker network ls
docker network inspect <network>
docker inspect --format '{{json .NetworkSettings.Networks}}' <container>
docker port <container>
docker logs <container>
~~~

Mục tiêu của tuần này là hiểu rõ traffic đi từ đâu đến đâu, dùng tên service ổn định và thiết kế network boundary nhỏ nhất phù hợp với ứng dụng.

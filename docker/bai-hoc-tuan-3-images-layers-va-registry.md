# Tuần 3: Images, layers và registry

## 1. Mục tiêu

Sau bài này, bạn có thể:

- Giải thích image manifest, config, layer và image reference.
- Phân biệt tag với digest và hiểu vì sao production cần reproducibility.
- Quan sát image bằng `images`, `inspect`, `history` và `du`.
- Pull, tag, retag và push image một cách có chủ đích.
- Hiểu local image store và vì sao image có thể dùng chung layer.
- Nhận biết Docker Hub, private registry và local registry.
- Hiểu multi-architecture image ở mức sử dụng và kiểm tra.
- Dọn image an toàn, phân biệt dangling image với image đang được dùng.
- Tránh đưa secret vào image hoặc push nhầm image chưa kiểm tra.

Thời lượng đề xuất: **8–12 giờ** gồm lý thuyết, lab và bài tập.

---

## 2. Chuẩn bị

Kiểm tra Docker:

~~~bash
docker version
docker info
docker image ls
~~~

Tải image mẫu:

~~~bash
docker pull nginx:1.27
docker image ls nginx
docker image inspect nginx:1.27
~~~

Trong bài, các image như `nginx:1.27`, `alpine:3.20` và `registry:2` dùng cho learning. Khi push lên registry thật, thay tên repository bằng namespace của bạn và không push image có secret, credential, private key hoặc file `.env`.

---

## 3. Image reference: Docker đang nói đến image nào?

Một image reference đầy đủ có thể có dạng:

~~~text
registry.example.com/team/payment-api:1.4.2
~~~

Các thành phần:

- `registry.example.com`: registry host.
- `team`: namespace hoặc organization.
- `payment-api`: repository.
- `1.4.2`: tag.

Một số ví dụ:

~~~text
nginx:1.27
alpine:3.20
ubuntu@sha256:<digest>
registry.example.com/team/api:2026-10-02
~~~

Nếu không ghi registry, Docker thường mặc định tìm trên Docker Hub. Nếu không ghi tag, reference thường được hiểu là `:latest`. Không nên dùng `latest` làm cách phát hành production vì tên này không mô tả rõ phiên bản nội dung.

### 3.1. Tag và digest

**Tag** là tên dễ đọc, ví dụ `nginx:1.27` hoặc `api:1.4.2`. Một tag có thể được di chuyển sang digest khác khi image được push lại.

**Digest** là định danh nội dung, ví dụ:

~~~text
nginx@sha256:abc123...
~~~

Digest thay đổi khi manifest hoặc nội dung được định danh thay đổi. Khi pin bằng digest, pull sau đó sẽ nhắm tới đúng nội dung đã biết thay vì phụ thuộc vào tag đang trỏ tới đâu.

Mental model:

~~~text
Tag:    api:1.4.2  ───────► Manifest hiện tại
Digest: api@sha256:... ───► Một nội dung cụ thể
~~~

Tag thuận tiện cho con người; digest phù hợp cho reproducibility, audit và rollback chính xác.

---

## 4. Image manifest và image config

Khi pull image, Docker không chỉ tải một file duy nhất. Registry trả về metadata mô tả image và các blob cần tải.

Ở mức khái niệm, một image có:

~~~text
Image reference
    ↓
Manifest hoặc manifest list
    ├── Image config
    │   ├── Entrypoint/Cmd
    │   ├── Environment mặc định
    │   ├── Working directory
    │   └── History/metadata
    └── Layer digests
        ├── Layer 1
        ├── Layer 2
        └── Layer 3
~~~

### Manifest

Manifest liên kết image config với các layer. Nó cũng chứa digest, size và media type của những thành phần cần thiết.

### Image config

Config chứa metadata runtime và history của image, chẳng hạn command mặc định, environment, working directory, user và root filesystem diff IDs.

### Manifest list hoặc image index

Một reference có thể trỏ tới image index chứa nhiều manifest cho các platform khác nhau:

~~~text
alpine:3.20
└── Image index
    ├── linux/amd64
    ├── linux/arm64
    └── linux/arm/v7
~~~

Docker chọn manifest phù hợp với OS và architecture của máy, trừ khi bạn chỉ định `--platform`.

Inspect metadata:

~~~bash
docker image inspect nginx:1.27
docker manifest inspect nginx:1.27
~~~

`docker manifest inspect` có thể yêu cầu network hoặc quyền tùy registry. Output chính xác thay đổi theo image và Docker version; mục tiêu là nhận biết image có nhiều platform và đọc digest/architecture.

---

## 5. Layers: image được ghép từ nhiều phần

Image thường được xây từ nhiều layer read-only. Mỗi layer biểu diễn một thay đổi filesystem so với layer trước.

~~~text
Container writable layer
────────────────────────
Layer 3: application files
Layer 2: installed packages
Layer 1: base filesystem
~~~

Layer có hai lợi ích chính:

1. **Tái sử dụng**: nhiều image hoặc tag có thể dùng chung layer giống nhau.
2. **Cache và truyền tải hiệu quả**: chỉ layer còn thiếu hoặc thay đổi cần được tải/build lại.

Layer không phải lúc nào cũng tương ứng một file duy nhất. Layer là một snapshot/delta filesystem được content-addressed bằng digest.

### 5.1. Layer và writable container layer

Khi tạo container từ image, image layers vẫn read-only và container nhận thêm writable layer riêng. Thay đổi runtime trong container không tự tạo image layer mới.

~~~text
Image layers: read-only, chia sẻ được
Container layer: writable, thuộc về một instance
~~~

Xóa container thường xóa writable layer của container. Xóa image có thể xóa các layer không còn được image/container nào tham chiếu, tùy garbage collection.

### 5.2. Layer sharing

Nếu hai image cùng dùng base layer, Docker chỉ cần lưu một bản layer ở local store:

~~~text
Image A ─┐
         ├── base layer dùng chung
Image B ─┘
~~~

Dung lượng hiển thị trên mỗi image không đơn giản bằng tổng dung lượng vật lý. Hãy dùng `docker system df` để xem reclaimable space và shared size.

---

## 6. Lab 1: Quan sát image local

### 6.1. Liệt kê image

~~~bash
docker image ls
docker images nginx
docker image ls --no-trunc
~~~

Các cột thường thấy:

- Repository.
- Tag.
- Image ID.
- Created.
- Size.

Image ID dạng rút gọn chỉ để đọc nhanh; không nên dùng nó thay cho digest khi cần định danh lâu dài.

### 6.2. Inspect image

~~~bash
docker image inspect nginx:1.27
docker image inspect --format '{{.Id}}' nginx:1.27
docker image inspect --format '{{json .RepoDigests}}' nginx:1.27
docker image inspect --format '{{json .Config.Cmd}}' nginx:1.27
docker image inspect --format '{{json .Config.Entrypoint}}' nginx:1.27
~~~

Chú ý:

- `Id`: ID content-addressed của image local.
- `RepoTags`: các tag local trỏ tới image.
- `RepoDigests`: digest registry đã biết sau khi pull/push.
- `Architecture` và `Os`.
- `Config`: command, entrypoint, env, user, working directory.
- `RootFS`: type và layer/diff IDs.

### 6.3. Xem lịch sử layer

~~~bash
docker history nginx:1.27
docker history --no-trunc nginx:1.27
~~~

`history` giúp nhận biết layer đến từ command nào và layer nào lớn. Không phải mọi metadata đều hiện hoàn hảo, đặc biệt với image được tạo bằng cách khác nhau.

### 6.4. Xem dung lượng

~~~bash
docker system df
docker system df -v
docker image du
~~~

`docker image du` phụ thuộc Docker version/buildx. Nếu lệnh không có, dùng `docker system df -v` để xem image và layer usage.

---

## 7. Pull, tag và retag

### 7.1. Pull

~~~bash
docker pull nginx:1.27
docker pull --platform linux/amd64 alpine:3.20
~~~

Pull có thể tải manifest, config và các layer còn thiếu. Docker sẽ reuse layer đã có local.

Chỉ định `--platform` hữu ích khi build/test cho platform khác, nhưng cần hiểu binary trong image phải tương thích với platform đó hoặc runtime phải có cơ chế emulation.

### 7.2. Tag

Tag tạo một reference khác trỏ tới image đã có local; nó không copy toàn bộ image:

~~~bash
docker tag nginx:1.27 local/nginx-learning:1.0.0
docker image ls local/nginx-learning
~~~

Xác minh hai reference dùng cùng image ID:

~~~bash
docker image inspect nginx:1.27 local/nginx-learning:1.0.0 \
  --format '{{.RepoTags}} {{.Id}}'
~~~

Tag local không upload gì lên registry. Nó chỉ tạo tên mới trong local image store.

### 7.3. Tag không phải copy bất biến

Nếu bạn retag một image khác vào cùng tên tag, reference sẽ trỏ sang image mới:

~~~bash
docker pull alpine:3.20
docker tag alpine:3.20 local/test:one
docker image inspect local/test:one --format '{{.Id}}'
~~~

Vì tag có thể được di chuyển, release nên dùng version rõ ràng và lưu digest. Với artifact quan trọng, deploy bằng reference dạng digest.

---

## 8. Push lên registry

### 8.1. Docker Hub flow

Giả sử username Docker Hub là `YOUR_USER`:

~~~bash
docker login
docker tag local/nginx-learning:1.0.0 YOUR_USER/nginx-learning:1.0.0
docker push YOUR_USER/nginx-learning:1.0.0
~~~

Sau khi push:

~~~bash
docker image inspect YOUR_USER/nginx-learning:1.0.0 \
  --format '{{json .RepoDigests}}'
~~~

Không paste password hoặc access token vào command history. Dùng credential helper hoặc cơ chế login tương ứng với môi trường.

### 8.2. Repository name phải đúng

Registry thường yêu cầu tên image có namespace/organization mà user có quyền push. `local/nginx-learning` chỉ là tên local; chưa chắc là repository hợp lệ trên Docker Hub.

### 8.3. Push nhiều tag

Nếu cùng một digest cần có tag release và tag môi trường:

~~~bash
docker tag local/nginx-learning:1.0.0 YOUR_USER/nginx-learning:1.0.0
docker tag local/nginx-learning:1.0.0 YOUR_USER/nginx-learning:stable
docker push YOUR_USER/nginx-learning:1.0.0
docker push YOUR_USER/nginx-learning:stable
~~~

Hai tag có thể trỏ cùng digest tại thời điểm push. Nhưng `stable` có thể được di chuyển sau đó; đừng dùng nó thay digest trong audit hoặc rollback chính xác.

---

## 9. Local registry lab

Local registry giúp học push/pull mà không cần publish image lên Docker Hub.

### 9.1. Chạy registry

~~~bash
docker run -d --name local-registry -p 5000:5000 registry:2
~~~

Kiểm tra:

~~~bash
curl http://localhost:5000/v2/
docker logs local-registry
~~~

Kết quả `200` với body rỗng hoặc `{}` thường cho thấy registry endpoint đang phản hồi.

### 9.2. Tag và push

~~~bash
docker pull alpine:3.20
docker tag alpine:3.20 localhost:5000/learning/alpine:3.20
docker push localhost:5000/learning/alpine:3.20
~~~

Liệt kê repository/tag qua Registry API:

~~~bash
curl http://localhost:5000/v2/_catalog
curl http://localhost:5000/v2/learning/alpine/tags/list
~~~

Xóa reference local rồi pull lại:

~~~bash
docker image rm localhost:5000/learning/alpine:3.20
docker pull localhost:5000/learning/alpine:3.20
docker image inspect localhost:5000/learning/alpine:3.20
~~~

Lab này minh họa registry lưu image distribution; local image store của Docker là nơi client giữ image sau khi pull.

### 9.3. Cảnh báo về insecure registry

`localhost:5000` thường được Docker Desktop/Engine xử lý riêng trong learning setup. Registry thật cần TLS, authentication, authorization, retention policy và audit. Không tắt TLS verification hoặc mở insecure registry trên production chỉ để làm lệnh chạy được.

Dọn local registry sau lab:

~~~bash
docker rm -f local-registry
~~~

---

## 10. Multi-architecture image

Một image reference có thể phục vụ nhiều architecture:

~~~text
image:tag
└── index
    ├── linux/amd64
    ├── linux/arm64
    └── linux/arm/v7
~~~

Điều này quan trọng khi developer dùng Apple Silicon/ARM nhưng production chạy x86_64, hoặc ngược lại.

Xem platform hiện tại:

~~~bash
docker version --format '{{.Server.Os}}/{{.Server.Arch}}'
uname -m
~~~

Xem manifest/index:

~~~bash
docker manifest inspect nginx:1.27
~~~

Pull một platform cụ thể để kiểm tra:

~~~bash
docker pull --platform linux/amd64 alpine:3.20
docker image inspect alpine:3.20 --format '{{.Os}}/{{.Architecture}}'
~~~

Nếu binary không đúng architecture, container có thể không start hoặc chạy qua emulation với hiệu năng khác. Khi build image đa nền tảng, cần kiểm tra dependency native, libc, JDK/JRE, CPU instruction và test trên platform mục tiêu.

---

## 11. Image immutability và supply chain cơ bản

### Không dùng `latest` làm release identifier

`latest` là một tag mặc định, không phải phiên bản ổn định. Nó có thể trỏ sang nội dung khác theo thời gian.

Nên ưu tiên:

~~~text
api:1.4.2
api:git-abc1234
api@sha256:<digest>
~~~

Quy tắc thực tế:

- Dùng version tag bất biến theo policy của team.
- Lưu digest sau khi build/push.
- Deploy bằng digest khi cần reproducibility cao.
- Không overwrite tag release đã publish nếu policy yêu cầu immutable tag.
- Ghi lại source commit, builder và base image trong metadata/attestation khi có tooling.

### Không push secret

Trước khi push, kiểm tra:

- `.env`, private key, token và credential.
- SSH keys hoặc cloud credentials.
- File build chứa secret.
- History của image và các layer cũ.
- Environment mặc định có password thật hay không.

Xóa file ở layer cuối không đảm bảo secret biến mất khỏi layer trước. Nếu secret đã từng được đưa vào image layer, cần rebuild từ base sạch và rotate secret đã lộ.

### Image scan

Scan vulnerability là bước cần thiết trước khi dùng image production. Công cụ cụ thể có thể là scanner tích hợp registry hoặc tool bên ngoài. Scan không chứng minh image an toàn tuyệt đối; cần xem severity, exploitability, runtime exposure và kế hoạch cập nhật.

---

## 12. Image cleanup và garbage collection

### 12.1. Dangling image

Dangling image thường là image không còn tag, ví dụ sau nhiều lần build lại. Xem trước:

~~~bash
docker image ls --filter dangling=true
docker system df
~~~

Xóa dangling image:

~~~bash
docker image prune
~~~

Docker sẽ yêu cầu xác nhận. Không dùng `-a` nếu chưa hiểu rõ phạm vi.

### 12.2. Unused image

~~~bash
docker image prune -a
~~~

`-a` mở rộng phạm vi tới image không được container nào tham chiếu, kể cả image có tag. Trên máy dùng chung hoặc CI cache, lệnh này có thể xóa image cần cho job khác và làm build sau chậm hơn.

### 12.3. System cleanup

~~~bash
docker system df -v
docker system prune
~~~

`docker system prune` có thể xóa nhiều loại tài nguyên không dùng. Không chạy cleanup diện rộng trên host production hoặc host dùng chung nếu chưa xác định owner, retention và tác động.

### 12.4. Registry garbage collection

Xóa tag trong registry không nhất thiết xóa blob ngay. Registry thường cần garbage collection theo cơ chế riêng. Chính sách retention cần cân bằng dung lượng, rollback và compliance.

---

## 13. Lab tổng hợp: từ pull đến digest

Thực hiện:

~~~bash
docker pull nginx:1.27
docker image inspect nginx:1.27 --format 'id={{.Id}} tags={{json .RepoTags}} digests={{json .RepoDigests}}'
docker history nginx:1.27
docker system df -v
docker tag nginx:1.27 local/nginx-learning:1.0.0
docker image inspect local/nginx-learning:1.0.0 --format '{{.Id}}'
~~~

Sau đó, nếu có local registry:

~~~bash
docker run -d --name local-registry -p 5000:5000 registry:2
docker tag local/nginx-learning:1.0.0 localhost:5000/learning/nginx:1.0.0
docker push localhost:5000/learning/nginx:1.0.0
docker image inspect localhost:5000/learning/nginx:1.0.0 --format '{{json .RepoDigests}}'
docker image rm localhost:5000/learning/nginx:1.0.0
docker pull localhost:5000/learning/nginx:1.0.0
docker rm -f local-registry
~~~

Cần trả lời được:

1. Tag local có upload image không?
2. Khi push, layer nào được upload nếu registry đã có layer đó?
3. Digest lấy ở đâu?
4. Xóa reference local có xóa registry image không?
5. Vì sao pull lại có thể nhanh hơn pull lần đầu?

---

## 14. Bài tập tự làm

### Bài 1: So sánh tag và digest

Pull `alpine:3.20`, lấy `RepoTags`, `RepoDigests`, `Id` bằng `docker image inspect`. Viết giải thích ngắn: mỗi giá trị dùng để làm gì và giá trị nào phù hợp để pin deployment.

### Bài 2: Layer sharing

Pull hai image có base gần nhau, chạy `docker system df -v` trước và sau. Dùng `docker history` để tìm layer lớn và giải thích vì sao tổng dung lượng hiển thị không nhất thiết bằng dung lượng vật lý.

### Bài 3: Push vào local registry

Chạy registry local, tag `alpine:3.20` thành `localhost:5000/learning/alpine:3.20`, push, xóa reference local, pull lại và xác minh digest.

### Bài 4: Multi-architecture

Dùng `docker manifest inspect` kiểm tra một image có `linux/amd64` và `linux/arm64` không. Giải thích điều gì xảy ra nếu image chỉ có architecture khác máy hiện tại.

### Bài 5: Cleanup có kiểm soát

Liệt kê dangling images, xem `docker system df -v`, xác định image nào có thể xóa, rồi chỉ chạy cleanup sau khi phân biệt resource của lab với resource cần giữ.

---

## 15. Câu hỏi kiểm tra và đáp án gợi ý

1. Image manifest liên kết những thành phần nào?
2. Layer có lợi ích gì cho cache và storage?
3. Container writable layer khác image layer như thế nào?
4. Tag khác digest ở điểm nào?
5. Vì sao không nên dùng `latest` làm release identifier?
6. `docker tag` có copy hoặc push image không?
7. Local image store khác registry như thế nào?
8. Vì sao cùng image reference có thể chạy trên amd64 và arm64?
9. `docker image prune` khác `docker image prune -a` thế nào?
10. Vì sao xóa secret ở layer cuối không đủ?

Đáp án ngắn: manifest liên kết config với layer; layer giúp reuse/cache; writable layer thuộc container; tag là tên có thể di chuyển còn digest định danh nội dung; `latest` không bảo đảm reproducibility; tag chỉ tạo reference local; registry phân phối còn image store giữ bản local; manifest/index chọn platform; `-a` xóa rộng hơn; secret có thể còn trong layer cũ nên phải rebuild và rotate.

---

## 16. Tiêu chí hoàn thành tuần 3

- [ ] Giải thích được image reference, manifest, config và layer.
- [ ] Phân biệt được tag, image ID và digest.
- [ ] Dùng được `docker image ls`, `inspect`, `history`, `system df`.
- [ ] Pull và tag image đúng repository/tag.
- [ ] Push được image vào registry được cấp quyền hoặc local registry.
- [ ] Biết xóa reference local không đồng nghĩa xóa image trên registry.
- [ ] Nhận biết image multi-architecture và kiểm tra platform.
- [ ] Phân biệt dangling image với image đang được sử dụng.
- [ ] Biết cleanup image có thể ảnh hưởng cache và workload khác.
- [ ] Không push image chứa secret.
- [ ] Biết khi nào cần pin digest để reproducibility.

Kiểm tra cuối buổi:

~~~bash
docker image ls
docker system df -v
docker image inspect nginx:1.27
docker history nginx:1.27
~~~

Chỉ chạy `prune` sau khi kiểm tra kỹ image nào đang được dùng và ai sở hữu chúng.

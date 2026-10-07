# Tuần 5: Build context, `.dockerignore` và layer cache

## 1. Mục tiêu

Sau bài này, bạn có thể:

- Giải thích build context và phạm vi file Docker có thể dùng khi build.
- Nhận biết vì sao context lớn làm build chậm và có thể làm lộ dữ liệu không cần thiết.
- Viết `.dockerignore` phù hợp cho project Java/Spring Boot.
- Hiểu image layer, cache hit, cache miss và cache invalidation.
- Sắp xếp `COPY` để source code thay đổi không làm cài lại dependency.
- Dùng BuildKit, `--progress`, `--pull` và `--no-cache` đúng mục đích.
- Đo build time, context size và image size trước/sau tối ưu.
- Debug khi cache không được dùng hoặc build cho kết quả cũ.

Thời lượng đề xuất: **8–12 giờ** gồm lý thuyết, lab và bài tập.

---

## 2. Vì sao build context và cache quan trọng?

Một lệnh build đơn giản:

~~~bash
docker build -t task-api:0.1.0 .
~~~

có hai phần cần phân biệt:

- `-t task-api:0.1.0`: tên và tag image đầu ra.
- `.`: build context, ở đây là thư mục hiện tại.

Docker không gửi toàn bộ filesystem của máy cho daemon. CLI chuẩn bị context rồi gửi context đó cho builder. Builder chỉ có thể dùng file nằm trong context để xử lý `COPY` và `ADD`.

Nếu context chứa `.git`, `target`, dependency cache, log, file IDE hoặc secret, Docker có thể phải đọc/truyền những file không cần thiết ở mỗi build. Điều này làm:

- Build khởi đầu chậm hơn.
- Cache dễ bị invalidated.
- Log build khó đọc hơn.
- Tăng nguy cơ đưa dữ liệu nhạy cảm vào quy trình build.
- Tạo image hoặc layer lớn hơn nếu Dockerfile copy quá rộng.

Layer cache giúp bỏ qua bước đã có kết quả. Nhưng cache chỉ hữu ích khi input của instruction không thay đổi và builder có thể xác định đúng cache key.

---

## 3. Build context

### 3.1. Context là gì?

Với lệnh:

~~~bash
docker build -f docker/Dockerfile -t task-api:dev .
~~~

- Dockerfile nằm ở `docker/Dockerfile`.
- Build context vẫn là `.`.
- Các path trong `COPY` được tính từ root của context, không tính từ thư mục chứa Dockerfile.

Ví dụ:

~~~text
project/
├── docker/
│   └── Dockerfile
├── pom.xml
├── mvnw
└── src/
~~~

Lệnh đúng:

~~~bash
docker build -f docker/Dockerfile -t task-api:dev .
~~~

Trong Dockerfile, có thể viết:

~~~dockerfile
COPY pom.xml /workspace/pom.xml
COPY src /workspace/src
~~~

Nếu chạy từ `docker/` với context `docker/`, Dockerfile sẽ không nhìn thấy `pom.xml` ở thư mục cha.

### 3.2. Context không phải output image

Context là input của build, không có nghĩa mọi file trong context sẽ nằm trong image. File chỉ được đưa vào image khi Dockerfile dùng `COPY`/`ADD`, hoặc được tạo bởi `RUN`.

Ngược lại, context chứa file không copy vào image vẫn có cost truyền/đọc và có thể tác động tới một số cơ chế cache/build.

### 3.3. Context và daemon từ xa

Khi builder/daemon ở máy khác, context phải được gửi qua connection đến builder. Context lớn càng gây rõ độ trễ và tiêu tốn bandwidth. Với Docker Desktop, việc này có thể diễn ra qua backend VM dù người dùng đang build local.

### 3.4. Kiểm tra context

Khi build, để ý dòng context trong output:

~~~bash
docker build --progress=plain -t task-api:dev .
~~~

BuildKit thường hiển thị các bước load build definition và load context. Nếu thấy context rất lớn, hãy kiểm tra `.dockerignore` và thư mục đang dùng làm context.

Có thể đo kích thước thư mục trước khi build:

~~~bash
du -sh .
du -sh .git target node_modules 2>/dev/null || true
~~~

Các lệnh trên là phép đo thô; context thực tế còn phụ thuộc `.dockerignore`.

---

## 4. `.dockerignore`

`.dockerignore` là file ở root của build context, dùng để loại file khỏi context gửi cho builder.

Ví dụ cho Java/Maven/Spring Boot:

~~~gitignore
.git
.gitignore
.idea
.vscode
*.iml

# Maven output và local cache
target
.mvn/wrapper/maven-wrapper.jar

# Logs và runtime files
*.log
logs

# Local environment/secrets
.env
.env.*
!.env.example
*.pem
*.key

# OS files
.DS_Store
Thumbs.db

# Temporary files
*.tmp
*.swp
~~~

Nếu dùng Gradle, thêm:

~~~gitignore
.gradle
build
!gradle/wrapper/gradle-wrapper.jar
~~~

### 4.1. Vì sao loại `.git`?

`.git` có thể rất lớn và thường không cần để build ứng dụng. Nếu cần revision metadata, truyền một giá trị đã kiểm soát bằng `--build-arg` hoặc label trong CI, thay vì copy toàn bộ lịch sử Git vào image.

### 4.2. Vì sao loại `target`?

Có hai workflow khác nhau:

1. Dockerfile runtime copy JAR đã build từ `target/app.jar`.
2. Dockerfile builder tự build source trong container.

Nếu Dockerfile runtime cần copy `target/app.jar`, **không được ignore `target`** trong workflow đó. Nếu Dockerfile builder tự chạy Maven và chỉ cần source/pom, ignore `target` để tránh gửi artifact cũ.

`.dockerignore` phải phù hợp với Dockerfile thực tế; không copy máy móc một template.

### 4.3. Syntax và pattern

Pattern thường dùng:

~~~gitignore
*.log
**/*.tmp
node_modules/
secret.txt
config/local/
~~~

Có thể dùng exception:

~~~gitignore
.env.*
!.env.example
~~~

Thứ tự pattern và exception cần được kiểm tra. `!.env.example` giữ lại file mẫu nhưng không giữ các file `.env.production` hoặc `.env.local`.

### 4.4. Secret không nên dựa chỉ vào `.dockerignore`

`.dockerignore` giảm khả năng file đi vào context, nhưng không phải secret manager. Vẫn cần:

- Không commit secret vào Git.
- Rotate secret nếu đã lộ.
- Dùng secret store/CI secret mechanism.
- Không copy toàn bộ context bằng `COPY . .` khi không cần.
- Scan source, context và image.

Dockerfile và `.dockerignore` thường vẫn được builder đọc để build; không dùng `.dockerignore` như cách ngăn Dockerfile có quyền truy cập chính nó. Chỉ file được copy hoặc dùng bởi instruction mới ảnh hưởng image theo cách tương ứng.

---

## 5. Image layer và cache key

Mỗi instruction có thể tạo layer hoặc metadata. Ví dụ:

~~~dockerfile
FROM eclipse-temurin:21-jdk
WORKDIR /workspace
COPY pom.xml .
RUN ./mvnw dependency:go-offline
COPY src src
RUN ./mvnw package -DskipTests
~~~

Có thể hình dung:

~~~text
Layer 1: base image
Layer 2: WORKDIR metadata
Layer 3: pom.xml + wrapper files
Layer 4: dependency download
Layer 5: source code
Layer 6: application build output
~~~

Cache key của instruction phụ thuộc vào instruction và input liên quan. Nếu key khớp cache có sẵn, builder dùng lại kết quả; nếu không, instruction chạy lại.

### 5.1. Cache invalidation theo thứ tự

Khi một instruction bị cache miss, các instruction sau nó thường phải chạy lại vì output phía trước đã khác.

~~~text
COPY pom.xml     cache hit
RUN dependencies cache hit
COPY src         cache miss
RUN package      cache miss
~~~

Nếu `COPY src src` đặt trước `RUN dependency:go-offline`, mỗi lần sửa source sẽ làm dependency step chạy lại. Đó là layout cache kém.

### 5.2. `COPY` và checksum

Với `COPY`, builder xem xét path và nội dung file để xác định input. Chỉ cần file thuộc phạm vi `COPY` thay đổi, instruction có thể bị invalidated.

Nếu dùng:

~~~dockerfile
COPY . .
RUN ./mvnw package -DskipTests
~~~

thì mọi thay đổi trong context được copy, kể cả file không liên quan nếu không bị ignore, có thể làm bước sau mất cache.

### 5.3. `RUN` không tự biết source bên ngoài

Một `RUN` cache hit không có nghĩa command luôn được chạy lại để kiểm tra network hoặc package repository. Builder nhìn cache key của instruction. Nếu muốn refresh base image hoặc dependency theo chủ ý, dùng flag/cache strategy phù hợp thay vì kỳ vọng Docker tự phát hiện mọi thay đổi bên ngoài.

---

## 6. Cache invalidation và thứ tự `COPY`

### 6.1. Dockerfile kém hiệu quả

~~~dockerfile
FROM eclipse-temurin:21-jdk
WORKDIR /workspace
COPY . .
RUN ./mvnw dependency:go-offline
RUN ./mvnw package -DskipTests
~~~

Khi đổi một source file, `COPY . .` thay đổi. Dependency step sau đó có thể chạy lại dù `pom.xml` không đổi.

### 6.2. Dockerfile tốt hơn cho dependency cache

~~~dockerfile
FROM eclipse-temurin:21-jdk
WORKDIR /workspace

COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw dependency:go-offline

COPY src src
RUN ./mvnw package -DskipTests
~~~

Bây giờ:

- Đổi `src/main/...`: dependency layer có thể được reuse.
- Đổi `pom.xml`: dependency layer phải chạy lại.
- Đổi `mvnw` hoặc `.mvn`: dependency layer có thể phải chạy lại.
- Đổi file được copy trước dependency step: cache của step đó bị invalidated.

### 6.3. Tách source theo mức độ thay đổi

Nguyên tắc tổng quát:

~~~text
File ít thay đổi, ảnh hưởng dependency
    ↓
Cài dependency
    ↓
File source thay đổi thường xuyên
    ↓
Compile/package
~~~

Không phải mọi project đều dùng đúng Maven wrapper hoặc layout `src`. Với Gradle, pattern tương tự:

~~~dockerfile
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN ./gradlew dependencies
COPY src src
RUN ./gradlew build -x test
~~~

Cần điều chỉnh theo project thật và task build thật.

---

## 7. BuildKit

BuildKit là builder hiện đại của Docker, hỗ trợ cache tốt hơn, output rõ hơn, parallelism và các tính năng build nâng cao.

Kiểm tra builder:

~~~bash
docker buildx version
docker buildx ls
docker buildx inspect --bootstrap
~~~

Build với progress dễ đọc cho CI/debug:

~~~bash
docker build --progress=plain -t task-api:dev .
~~~

Một số môi trường có thể dùng BuildKit mặc định. Không nên giả định mọi máy CI có cùng builder/config; hãy kiểm tra pipeline.

### 7.1. Cache mount cho package manager

BuildKit có thể mount cache cho Maven:

~~~dockerfile
# syntax=docker/dockerfile:1
FROM eclipse-temurin:21-jdk
WORKDIR /workspace

COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw dependency:go-offline

COPY src src
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw package -DskipTests
~~~

Cache mount giữ package cache bên ngoài layer output. Nó có thể giảm thời gian tải dependency nhưng không thay thế việc sắp xếp layer đúng.

Nếu chạy non-root ở build stage, target cache phải thuộc về đúng UID hoặc được cấu hình phù hợp. Đừng copy `.m2` vào image runtime.

### 7.2. Cache export/import trong CI

CI thường build trên worker mới nên local cache không tồn tại. BuildKit có thể export/import cache qua registry hoặc backend cache. Cú pháp phụ thuộc builder/CI:

~~~bash
docker buildx build \
  --cache-from type=registry,ref=registry.example.com/team/task-api:buildcache \
  --cache-to type=registry,ref=registry.example.com/team/task-api:buildcache,mode=max \
  -t registry.example.com/team/task-api:1.0.0 \
  --push .
~~~

Cache image không phải runtime image và cần policy retention. Không đưa secret vào cache export.

---

## 8. `--pull` và `--no-cache`

### 8.1. `--pull`

~~~bash
docker build --pull -t task-api:dev .
~~~

`--pull` yêu cầu builder kiểm tra/pull base image mới hơn trước build. Nó hữu ích khi muốn nhận bản cập nhật base image, nhưng có thể làm build không còn giống lần trước nếu base tag đã trỏ nội dung mới.

Nếu cần reproducibility, pin digest và cập nhật digest qua quy trình review.

### 8.2. `--no-cache`

~~~bash
docker build --no-cache -t task-api:clean .
~~~

`--no-cache` bỏ qua cache cho các bước build trong lần chạy đó. Nó hữu ích để:

- Xác minh Dockerfile có thể build từ đầu.
- Điều tra cache cũ gây kết quả không mong đợi.
- Kiểm tra dependency install có thật sự hoạt động.
- Tạo baseline đo cold build.

`--no-cache` không tự pull base image mới nhất. Nếu muốn cả hai:

~~~bash
docker build --pull --no-cache -t task-api:clean .
~~~

Không dùng `--no-cache` mặc định trong mọi CI nếu muốn build nhanh; hãy dùng có chủ đích hoặc theo lịch.

---

## 9. Đo build time, context và image size

Tối ưu cần có số đo trước/sau.

### 9.1. Đo cold build và warm build

~~~bash
time docker build --progress=plain -t task-api:baseline .
time docker build --progress=plain -t task-api:baseline .
~~~

- Lần đầu gần với cold build nếu chưa có cache.
- Lần thứ hai cho thấy warm build.
- Sửa một file source rồi build lại để đo incremental build.

Ghi lại:

| Scenario | Build time | Context | Image size |
|---|---:|---:|---:|
| Dockerfile ban đầu | ... | ... | ... |
| Có `.dockerignore` | ... | ... | ... |
| Tách dependency layer | ... | ... | ... |
| `--no-cache` | ... | ... | ... |

### 9.2. Đo image size và layer

~~~bash
docker image ls task-api
docker system df -v
docker history task-api:baseline
~~~

Image nhỏ hơn thường giúp pull/deploy nhanh hơn, nhưng không được đánh đổi bằng cách bỏ runtime dependency cần thiết hoặc làm build khó bảo trì.

### 9.3. Đo context sau `.dockerignore`

~~~bash
docker build --progress=plain -t task-api:context-test .
~~~

So sánh dòng context trước và sau khi thêm `.dockerignore`. Nếu builder không hiện số liệu giống nhau giữa các version, dùng `du`, `find` và build log để đánh giá tương đối.

---

## 10. Lab 1: `.dockerignore` cho Spring Boot

Tạo `.dockerignore` ở root project:

~~~gitignore
.git
.idea
.vscode
*.iml
.DS_Store

# Chọn dòng này nếu Dockerfile tự build source
# target

*.log
logs
.env
.env.*
!.env.example
*.pem
*.key
~~~

Nếu Dockerfile copy JAR đã build từ `target`, giữ `target` ngoài danh sách ignore. Nếu Dockerfile tự chạy Maven, thêm `target` vào ignore.

Kiểm tra những file lớn:

~~~bash
du -sh .
find . -maxdepth 3 -type f -size +10M -print
~~~

Build trước và sau khi điều chỉnh `.dockerignore`, dùng `--progress=plain` để so sánh context.

---

## 11. Lab 2: So sánh Dockerfile cache kém và tốt

### 11.1. Dockerfile cache kém

~~~dockerfile
FROM eclipse-temurin:21-jdk
WORKDIR /workspace
COPY . .
RUN ./mvnw dependency:go-offline
RUN ./mvnw package -DskipTests
~~~

Đo:

~~~bash
time docker build --progress=plain -f Dockerfile.bad -t task-api:bad .
# sửa một file trong src rồi chạy lại
time docker build --progress=plain -f Dockerfile.bad -t task-api:bad .
~~~

Quan sát dependency step có bị chạy lại sau source change không.

### 11.2. Dockerfile cache tốt hơn

~~~dockerfile
FROM eclipse-temurin:21-jdk
WORKDIR /workspace
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw dependency:go-offline
COPY src src
RUN ./mvnw package -DskipTests
~~~

Đo lại:

~~~bash
time docker build --progress=plain -f Dockerfile.good -t task-api:good .
# sửa một file trong src rồi chạy lại
time docker build --progress=plain -f Dockerfile.good -t task-api:good .
~~~

Kết luận cần ghi lại:

- Instruction nào cache hit?
- Instruction nào cache miss?
- Dependency có phải tải lại không?
- Incremental build giảm bao nhiêu thời gian?
- Image size có thay đổi không?

---

## 12. Lab 3: `--pull`, `--no-cache` và BuildKit

Chạy ba biến thể:

~~~bash
docker build --progress=plain -t task-api:warm .
docker build --no-cache --progress=plain -t task-api:cold .
docker build --pull --progress=plain -t task-api:pull .
~~~

So sánh:

- Warm build dùng cache local.
- No-cache chạy lại các bước build.
- Pull kiểm tra base image mới hơn.

Kiểm tra builder:

~~~bash
docker buildx ls
docker buildx inspect --bootstrap
~~~

Không xóa cache global trong lab này nếu máy còn project khác sử dụng. Nếu cần nghiên cứu cache builder, kiểm tra `docker buildx du` trước và đọc kỹ phạm vi lệnh prune.

---

## 13. Lab 4: Cache mount Maven

Dùng Dockerfile thử nghiệm:

~~~dockerfile
# syntax=docker/dockerfile:1
FROM eclipse-temurin:21-jdk
WORKDIR /workspace
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw dependency:go-offline
COPY src src
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw package -DskipTests
~~~

Build hai lần và quan sát log tải dependency. Cache mount giúp Maven reuse local repository giữa các lần build theo builder, nhưng:

- Không làm dependency trở thành một phần runtime image.
- Không thay thế layer cache.
- Có thể phụ thuộc builder/CI.
- Cần lock/pin dependency để reproducibility.

---

## 14. Lỗi thường gặp

### Build context quá lớn

Nguyên nhân: chạy context ở thư mục quá rộng, thiếu `.dockerignore`, có `.git`, `target`, log, dependency cache hoặc file binary lớn.

Cách xử lý:

~~~bash
du -sh .
find . -maxdepth 3 -type f -size +10M -print
docker build --progress=plain -t context-check .
~~~

Chọn context hẹp nhất nhưng vẫn chứa các file Dockerfile cần.

### `COPY` không tìm thấy file

Nguyên nhân: path tính từ context, file bị `.dockerignore` loại, hoặc JAR chưa build.

~~~bash
ls -la target/
cat .dockerignore
docker build -f docker/Dockerfile .
~~~

Kiểm tra path trong Dockerfile và context cuối lệnh build.

### Dependency vẫn bị tải lại sau mỗi source change

Kiểm tra:

- `COPY pom.xml` và wrapper có đứng trước `COPY src` không.
- `pom.xml` có bị generate hoặc thay đổi mỗi build không.
- `.mvn`/wrapper có bị copy sau dependency step không.
- Có dùng `COPY . .` trước dependency step không.
- Build có đang dùng đúng Dockerfile không.

### Cache bị dùng khi muốn build sạch

Dùng:

~~~bash
docker build --pull --no-cache --progress=plain -t task-api:clean .
~~~

Nhưng đừng xóa cache hoặc dùng no-cache thường xuyên nếu mục tiêu là incremental build nhanh.

### Build dùng base image cũ

Dùng `--pull` hoặc pin digest và có quy trình cập nhật base image. `docker image ls` chỉ cho biết image local, không chứng minh registry không có bản mới.

### `.dockerignore` loại nhầm file cần copy

Nếu `COPY target/app.jar` thất bại sau khi thêm `target`, đây là nguyên nhân có thể xảy ra. Điều chỉnh `.dockerignore` theo workflow runtime-vs-builder.

### Dùng cache làm source of truth

Cache là tối ưu hóa, không phải artifact duy nhất. Build sạch vẫn phải thành công trong CI/release theo tần suất phù hợp.

---

## 15. Bài tập tự làm

### Bài 1: Đo context

Đo kích thước project trước và sau `.dockerignore`. Ghi rõ các thư mục bị loại và build context giảm bao nhiêu.

### Bài 2: Cache dependency

Tạo `Dockerfile.bad` với `COPY . .` trước dependency step và `Dockerfile.good` copy `pom.xml`/wrapper trước `src`. Sửa một source file, build cả hai và so sánh log/time.

### Bài 3: Cache invalidation

Thực hiện riêng từng thay đổi:

1. Sửa `src`.
2. Sửa `pom.xml`.
3. Sửa Dockerfile.
4. Thêm một file log không được copy.
5. Thêm một file vào vùng `COPY . .`.

Dự đoán layer bị invalidated rồi kiểm tra bằng `--progress=plain`.

### Bài 4: `--pull` và `--no-cache`

Chạy warm build, no-cache build và pull build. Viết kết luận: mỗi flag giải quyết vấn đề gì, không giải quyết vấn đề gì.

### Bài 5: Cache mount

Thêm Maven cache mount, build lần đầu và lần sau, rồi giải thích sự khác nhau giữa cache mount và image layer cache.

---

## 16. Câu hỏi kiểm tra và đáp án gợi ý

1. Build context là gì và được xác định bởi phần nào của `docker build`?
2. Vì sao context lớn làm build chậm dù Dockerfile không copy mọi file?
3. `.dockerignore` cần đặt ở đâu?
4. Vì sao `COPY pom.xml` trước `COPY src` giúp Maven build nhanh hơn?
5. Khi một layer cache miss, điều gì thường xảy ra với các layer sau?
6. `--pull` và `--no-cache` khác nhau thế nào?
7. `COPY` khác `ADD` ở điểm nào khiến `COPY` thường được ưu tiên?
8. Cache mount Maven khác image layer cache ra sao?
9. Vì sao `.dockerignore` không thay thế secret manager?
10. Vì sao `target` có lúc phải ignore, có lúc không?

Đáp án ngắn: context là phần cuối lệnh build; context lớn tốn truyền/đọc và có thể ảnh hưởng input; `.dockerignore` ở root context; dependency files ít thay đổi nên tách khỏi source; cache miss thường kéo theo các bước sau chạy lại; `--pull` cập nhật base còn `--no-cache` bỏ qua cache build; `COPY` có behavior rõ hơn; cache mount giữ package cache ngoài layer; secret vẫn cần quản lý riêng; runtime Dockerfile cần JAR trong target còn builder Dockerfile thường không cần target.

---

## 17. Tiêu chí hoàn thành tuần 5

- [ ] Giải thích được build context và path của `COPY`.
- [ ] Viết được `.dockerignore` phù hợp với project Spring Boot.
- [ ] Biết khi nào `target` nên được ignore và khi nào không.
- [ ] Hiểu cache hit, cache miss và cache invalidation.
- [ ] Sắp xếp dependency files trước source code.
- [ ] Dùng được `--progress=plain`, `--pull` và `--no-cache`.
- [ ] Biết kiểm tra BuildKit/buildx.
- [ ] Đo được context size, build time và image size.
- [ ] Biết dùng cache mount Maven ở mức cơ bản.
- [ ] Phân biệt image layer cache với package-manager cache.
- [ ] Không đưa secret, `.env`, key hoặc file không cần thiết vào context/image.

Kiểm tra cuối buổi:

~~~bash
docker build --progress=plain -t task-api:week5 .
docker image ls task-api
docker history task-api:week5
docker system df -v
~~~

Mục tiêu không phải làm mọi build đều “cache hit”, mà là hiểu input nào thay đổi, layer nào cần chạy lại và trade-off giữa tốc độ, reproducibility, bảo mật và khả năng bảo trì.

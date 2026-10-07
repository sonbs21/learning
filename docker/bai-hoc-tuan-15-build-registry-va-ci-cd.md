# Tuần 15: Build, registry và CI/CD

## 1. Mục tiêu

Sau bài này, bạn có thể:

- Giải thích Buildx builder và khi nào cần multi-platform build.
- Thiết kế cache `cache-to`/`cache-from` cho CI.
- Tag image theo commit SHA và digest thay vì phụ thuộc `latest`.
- Login registry bằng credential/token tối thiểu.
- Push/pull image và lưu digest làm release artifact.
- Viết GitHub Actions workflow cho build/test/scan/push.
- Chạy integration test với PostgreSQL và Redis.
- Deploy image vào staging bằng immutable reference.
- Smoke test sau deploy.
- Rollback về image digest/tag trước đó.
- Phân biệt build trên pull request với publish sau merge.
- Bảo vệ CI khỏi secret leak, tag overwrite và pipeline không đáng tin.

Thời lượng đề xuất: **8–12 giờ** gồm viết workflow, chạy CI test và mô phỏng deploy/rollback.

---

## 2. CI/CD container pipeline

Pipeline trong roadmap:

~~~text
Checkout
  ↓
Unit test
  ↓
Build image
  ↓
Integration test
  ↓
Security scan
  ↓
Push immutable tag
  ↓
Deploy staging
  ↓
Smoke test
  ↓
Promote production
~~~

Mỗi bước có một trách nhiệm:

- **Checkout**: lấy đúng commit/PR revision.
- **Unit test**: kiểm tra logic source.
- **Build image**: tạo runtime image từ Dockerfile.
- **Integration test**: chạy image với dependency thật như PostgreSQL/Redis.
- **Security scan**: scan final image, không chỉ source/build image.
- **Push**: publish artifact sau khi gate pass.
- **Deploy staging**: chạy đúng digest/tag đã build.
- **Smoke test**: kiểm tra flow quan trọng sau deploy.
- **Promote**: dùng cùng artifact đã verify, không rebuild ngẫu nhiên.

Nguyên tắc quan trọng:

> Build một artifact, kiểm tra artifact đó, rồi promote chính artifact đó qua các môi trường.

Không build lại khác nhau cho staging và production nếu mục tiêu là reproducibility.

---

## 3. Image tag và release identity

### 3.1. Tag theo commit SHA

Tag theo Git commit giúp truy ra source:

~~~text
registry.example.com/team/task-api:sha-a1b2c3d4
~~~

Trong CI, dùng SHA đầy đủ hoặc short SHA theo policy. SHA đầy đủ giảm collision hơn:

~~~bash
git rev-parse HEAD
git rev-parse --short=12 HEAD
~~~

Có thể thêm tag môi trường/semantic version, nhưng deploy nên lưu digest:

~~~text
Tag:    task-api:sha-a1b2c3d4
Digest: task-api@sha256:...
~~~

### 3.2. PR tag và release tag

PR build không nên push vào production repository tùy tiện. Có thể:

- Build local trong runner.
- Push vào temporary/PR namespace với retention ngắn.
- Chỉ scan/test rồi discard.

Khi merge `main`:

- Push `sha-<commit>` immutable tag.
- Có thể push version tag nếu release policy cho phép.
- Ghi digest vào job output/release metadata.

### 3.3. Không dùng `latest` làm deployment identity

`latest` có thể là convenience tag cho local/dev. Production deployment nên dùng:

~~~text
registry.example.com/team/task-api@sha256:<digest>
~~~

hoặc tag immutable được registry policy bảo vệ. Tag chỉ là lookup; digest là nội dung cụ thể.

---

## 4. Docker Buildx

Buildx là CLI/plugin hỗ trợ BuildKit và các workflow build nâng cao:

- Builder instance.
- Multi-platform output.
- Registry push.
- Cache import/export.
- SBOM/provenance tùy builder/version.

Kiểm tra:

~~~bash
docker buildx version
docker buildx ls
docker buildx inspect --bootstrap
~~~

Tạo builder riêng cho local/CI khi cần:

~~~bash
docker buildx create --name learning-builder --use
docker buildx inspect --bootstrap
~~~

Builder có lifecycle riêng. CI runner ephemeral thường cần setup builder trong mỗi job hoặc dùng action tương ứng.

### 4.1. `docker build` và `buildx build`

Build local image:

~~~bash
docker build -t task-api:dev .
~~~

Buildx push trực tiếp:

~~~bash
docker buildx build \
  --tag registry.example.com/team/task-api:sha-a1b2c3d4 \
  --push .
~~~

Buildx thường không load image vào local Docker image store nếu chỉ dùng `--push`. Nếu cần test local:

~~~bash
docker buildx build --load -t task-api:test .
~~~

`--load` phù hợp single-platform; multi-platform image thường push registry thay vì load vào local daemon.

---

## 5. Multi-platform build

### 5.1. Vì sao cần multi-platform?

Developer có thể dùng ARM64/Apple Silicon, CI/production có thể dùng AMD64. Multi-platform image index cho phép cùng reference chọn manifest phù hợp:

~~~text
registry/team/task-api:1.0.0
├── linux/amd64
└── linux/arm64
~~~

### 5.2. Kiểm tra platform

~~~bash
docker version --format '{{.Server.Os}}/{{.Server.Arch}}'
docker buildx inspect --bootstrap
~~~

### 5.3. Build và push multi-platform

~~~bash
docker buildx build \
  --platform linux/amd64,linux/arm64 \
  --tag registry.example.com/team/task-api:sha-a1b2c3d4 \
  --push .
~~~

Buildx có thể dùng native builder hoặc emulation. Emulation tiện nhưng chậm hơn và có thể khác behavior/performance native.

### 5.4. Rủi ro multi-platform

- Native dependency/JNI không tương thích.
- Base image thiếu platform.
- Build script download binary theo architecture sai.
- QEMU/emulation che giấu lỗi native.
- Test chỉ chạy trên một platform.
- Image digest/index digest khác với manifest digest từng platform.

Test tối thiểu trên platform production. Nếu chỉ deploy AMD64, không cần build ARM64 chỉ vì “có thể”.

---

## 6. Cache-to và cache-from

CI runner mới thường không có local build cache. `cache-to` export cache; `cache-from` import cache.

### 6.1. Registry cache

~~~bash
docker buildx build \
  --cache-from type=registry,ref=registry.example.com/team/task-api:buildcache \
  --cache-to type=registry,ref=registry.example.com/team/task-api:buildcache,mode=max \
  --tag registry.example.com/team/task-api:sha-a1b2c3d4 \
  --push .
~~~

`mode=max` thường lưu nhiều thông tin cache hơn nhưng tốn registry storage. Cache image không phải runtime image và cần retention/permission policy.

### 6.2. GitHub Actions cache

Trong GitHub Actions, có thể dùng cache backend `gha`:

~~~yaml
- name: Build and push
  uses: docker/build-push-action@v6
  with:
    context: .
    push: true
    tags: ${{ steps.meta.outputs.tags }}
    cache-from: type=gha
    cache-to: type=gha,mode=max
~~~

Syntax/action version có thể được cập nhật; pin action major/version theo policy và kiểm tra release.

### 6.3. Cache không được làm source of truth

Build phải có khả năng chạy khi cache miss. Không chứa secret trong exported cache. Cache stale có thể làm build nhanh nhưng không được dùng để bypass test/scan.

### 6.4. Cache invalidation

Cache hiệu quả phụ thuộc Dockerfile:

~~~dockerfile
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw dependency:go-offline
COPY src src
RUN ./mvnw package -DskipTests
~~~

Source change không nên làm download dependency lại. Cache không thay thế Dockerfile layer design.

---

## 7. Registry login, push và pull

### 7.1. Registry credential

Không commit username/password/token. Dùng:

- GitHub Actions secrets/variables.
- OIDC/workload identity khi registry hỗ trợ.
- Short-lived token.
- Credential helper local.
- Robot/service account với quyền tối thiểu.

Login local:

~~~bash
docker login registry.example.com
~~~

Không truyền password trực tiếp trong command line/history. CI action nên nhận credential từ secret mechanism.

### 7.2. Push image

~~~bash
docker push registry.example.com/team/task-api:sha-a1b2c3d4
~~~

Sau push, lấy digest:

~~~bash
docker inspect --format '{{json .RepoDigests}}' registry.example.com/team/task-api:sha-a1b2c3d4
~~~

Một số registry/CLI cần query manifest để lấy digest chính xác. Lưu digest làm output, không đoán từ tag.

### 7.3. Pull và verify

~~~bash
docker pull registry.example.com/team/task-api@sha256:<digest>
docker image inspect registry.example.com/team/task-api@sha256:<digest>
~~~

Production host nên pull artifact đã được scan/sign/approve, không tự build từ source nếu pipeline đã có controlled build.

### 7.4. Quyền registry

Tách:

- PR job: build/scan, không push production.
- Main/release job: push immutable tag.
- Deploy job: pull/verify, không cần delete.
- Admin: quản lý repository/policy.

Không dùng registry admin token cho mọi job.

---

## 8. GitHub Actions workflow cơ bản

### 8.1. Trigger và permissions

~~~yaml
name: container-ci

on:
  pull_request:
  push:
    branches: [main]

permissions:
  contents: read

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Set up Java
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: maven
      - name: Unit tests
        run: ./mvnw test
~~~

Action version cần được review/pin theo policy. `permissions` nên tối thiểu; chỉ cấp `packages: write` hoặc `id-token: write` cho job thật sự cần.

### 8.2. Setup Buildx

~~~yaml
  image:
    needs: test
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write
    steps:
      - uses: actions/checkout@v4
      - name: Set up Buildx
        uses: docker/setup-buildx-action@v3
~~~

### 8.3. Login registry

GitHub Container Registry example:

~~~yaml
      - name: Log in to registry
        uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}
~~~

Với registry khác, dùng secret service account scoped đúng repository. Không echo token.

### 8.4. Metadata và tag

~~~yaml
      - name: Docker metadata
        id: meta
        uses: docker/metadata-action@v5
        with:
          images: ghcr.io/${{ github.repository }}
          tags: |
            type=sha,format=long,prefix=sha-
            type=ref,event=tag
~~~

Metadata action có thể tạo nhiều tag theo event. Review tag policy để không tạo mutable production tag ngoài ý muốn.

### 8.5. Build/push

~~~yaml
      - name: Build and push
        uses: docker/build-push-action@v6
        with:
          context: .
          file: ./Dockerfile
          push: ${{ github.event_name == 'push' && github.ref == 'refs/heads/main' }}
          tags: ${{ steps.meta.outputs.tags }}
          labels: ${{ steps.meta.outputs.labels }}
          cache-from: type=gha
          cache-to: type=gha,mode=max
          provenance: true
          sbom: true
~~~

PR sẽ build nhưng `push: false`; merge main mới push. Nếu action/runtime không hỗ trợ field provenance/SBOM, kiểm tra version hoặc dùng tool riêng.

---

## 9. Workflow PR và main

### 9.1. Pull request

Mục tiêu:

- Unit test.
- Build image.
- Integration test.
- Scan.
- Không push production artifact.
- Không dùng untrusted PR code với write token/secret nhạy cảm.

Fork PR đặc biệt nhạy cảm: không expose registry write credentials cho code chưa được trust.

### 9.2. Merge main

Mục tiêu:

- Re-run test/quality gate.
- Build cùng Dockerfile/context.
- Push `sha-<full commit>`.
- Lấy digest.
- Generate SBOM/provenance/sign.
- Deploy staging.
- Smoke test.
- Promote hoặc chờ approval production.

### 9.3. Tag release

Release tag có thể tạo semantic tag, nhưng nên lưu mapping:

~~~text
v1.4.2 → sha-abc... → digest sha256:def...
~~~

Rollback dùng digest mapping, không tìm “tag gần nhất” bằng cảm tính.

---

## 10. Test container

### 10.1. Vì sao test image thật?

Unit test trên host không phát hiện:

- Dockerfile thiếu artifact.
- Runtime user không có quyền.
- Port/path sai.
- Environment mapping sai.
- JRE thiếu library/tool.
- Entrypoint/signal sai.

Test container sau build để kiểm tra artifact thật sẽ deploy.

### 10.2. Smoke test một container

~~~bash
docker run -d --name task-api-test -p 18080:8080 task-api:test
curl --fail http://localhost:18080/actuator/health
docker logs task-api-test
docker rm -f task-api-test
~~~

CI cần cleanup kể cả khi test fail, thường dùng `always()` step.

### 10.3. Integration test với Compose

`compose.test.yaml`:

~~~yaml
services:
  api:
    image: task-api:test
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/testdb
      SPRING_DATASOURCE_USERNAME: test
      SPRING_DATASOURCE_PASSWORD: test-only
      SPRING_DATA_REDIS_HOST: redis
    depends_on:
      db:
        condition: service_healthy
      redis:
        condition: service_healthy

  db:
    image: postgres:16
    environment:
      POSTGRES_DB: testdb
      POSTGRES_USER: test
      POSTGRES_PASSWORD: test-only
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U test -d testdb"]
      interval: 3s
      timeout: 3s
      retries: 20

  redis:
    image: redis:7.4
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 3s
      timeout: 3s
      retries: 20
~~~

Chạy:

~~~bash
docker compose -f compose.test.yaml up -d
docker compose -f compose.test.yaml ps
docker compose -f compose.test.yaml logs api
curl --fail http://localhost:8080/actuator/health
docker compose -f compose.test.yaml down -v
~~~

Trong CI, `down -v` chỉ an toàn khi project dùng database test ephemeral. Không chạy trên production project.

### 10.4. Test không phụ thuộc host port nếu có thể

Integration test container có thể chạy trong Compose network và gọi `api:8080`, giảm port conflict trên runner. Smoke test từ runner cần publish port hoặc dùng helper container cùng network.

---

## 11. Scan trong CI

### 11.1. Scan trước push

Có thể build image local (`load: true`) rồi scan:

~~~yaml
- name: Build image for test
  uses: docker/build-push-action@v6
  with:
    context: .
    load: true
    tags: task-api:ci
    cache-from: type=gha
    cache-to: type=gha,mode=max

- name: Scan image
  run: docker scout cves task-api:ci
~~~

Hoặc build/push vào temporary registry rồi scanner scan theo digest. Chọn flow phù hợp với scanner/policy.

### 11.2. Fail threshold

Policy concept:

~~~text
Fail nếu có:
- Critical chưa được exception.
- High vượt SLA.
- Secret detection.
- Root user nếu policy cấm.
- Base image ngoài allowlist.
~~~

Cấu hình scanner cụ thể khác nhau; đọc output/exit code và lưu report artifact.

### 11.3. Scan runtime image

Với multi-stage Dockerfile, scan final target/tag:

~~~bash
docker build --target runtime -t task-api:scan .
docker scout cves task-api:scan
~~~

Không chỉ scan build target có JDK/Maven nếu production deploy runtime target.

---

## 12. Push immutable tag và lấy digest

Một flow shell concept:

~~~bash
IMAGE=registry.example.com/team/task-api
TAG=sha-$(git rev-parse HEAD)

docker buildx build \
  --tag "$IMAGE:$TAG" \
  --provenance=true \
  --sbom=true \
  --push .

# Query digest bằng registry/manifest tool phù hợp
printf '%s\n' "$IMAGE:$TAG"
~~~

Không dùng untrusted shell input làm tag/command mà chưa validate. Trong CI, metadata action giúp chuẩn hóa tag/labels.

Sau push, ghi:

~~~text
source commit
image tag
image digest
builder/run ID
scan result
SBOM reference
signature/provenance reference
~~~

Release artifact metadata là căn cứ deploy/rollback.

---

## 13. Deploy staging

Compose staging có thể dùng biến image:

~~~yaml
services:
  api:
    image: ${API_IMAGE}
~~~

Deploy bằng digest:

~~~bash
export API_IMAGE=registry.example.com/team/task-api@sha256:<digest>
docker compose -f compose.staging.yaml pull api
docker compose -f compose.staging.yaml up -d api
docker compose -f compose.staging.yaml ps
docker compose -f compose.staging.yaml logs --tail 100 api
~~~

Trong CI, biến không nên được nối từ input không tin cậy. Verify registry access, signature/provenance và environment trước deploy.

### 13.1. Staging deploy checklist

- Image digest đúng artifact scan.
- Database migration plan rõ.
- Config/secret staging đúng.
- Network/volume đúng environment.
- Healthcheck pass.
- Logs/metrics hoạt động.
- Smoke endpoint reachable.
- Rollback digest trước đó đã biết.

### 13.2. Promote không rebuild

Nếu staging pass, promote cùng digest:

~~~text
staging: digest D
production: digest D
~~~

Không build lại từ cùng tag rồi giả định content giống. Nếu cần rebuild, coi là artifact mới và chạy lại gate.

---

## 14. Smoke test

Smoke test là test nhỏ sau deploy để xác nhận hệ thống cơ bản hoạt động:

~~~bash
curl --fail --retry 10 --retry-delay 2 \
  https://staging.example.com/actuator/health

curl --fail -X POST \
  -H 'Content-Type: application/json' \
  -d '{"name":"smoke"}' \
  https://staging.example.com/api/tasks
~~~

Không đưa credential production vào command/log. Dùng test account/data riêng.

Smoke test nên kiểm tra:

- DNS/TLS/route.
- Health/readiness.
- Authentication flow test.
- Một read/write path an toàn.
- DB/Redis dependency.
- Error response không leak secret.

Smoke pass không thay thế integration/load/security test.

---

## 15. Rollback image

### 15.1. Rollback bằng digest

Lưu release mapping:

~~~text
Current:  sha256:new...
Previous: sha256:old...
~~~

Đổi staging/Compose config về digest cũ:

~~~bash
export API_IMAGE=registry.example.com/team/task-api@sha256:<previous-digest>
docker compose -f compose.staging.yaml up -d api
docker compose -f compose.staging.yaml ps
~~~

Smoke test sau rollback.

### 15.2. Khi rollback image chưa đủ

Database migration có thể không backward-compatible. Rollback app image mà schema đã forward-migrate có thể làm app cũ fail.

Release cần:

- Backward-compatible migration (expand/contract).
- Rollback plan cho schema.
- Feature flag.
- Data compatibility check.
- Backup/restore plan.

### 15.3. Rollback trigger

Định nghĩa trước:

- Error rate.
- p95/p99 latency.
- Healthcheck fail.
- Crash loop.
- Critical security finding.
- Data correctness issue.

Không chờ mọi metric hoàn hảo mới rollback nếu blast radius đang tăng.

---

## 16. GitHub Actions workflow mẫu

Workflow khung, cần chỉnh registry/project/policy:

~~~yaml
name: container-pipeline

on:
  pull_request:
  push:
    branches: [main]
    tags: ['v*.*.*']

permissions:
  contents: read

env:
  REGISTRY: ghcr.io
  IMAGE_NAME: ${{ github.repository }}

jobs:
  unit-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: maven
      - run: ./mvnw test

  build-scan:
    needs: unit-test
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write
    steps:
      - uses: actions/checkout@v4
      - uses: docker/setup-buildx-action@v3
      - uses: docker/login-action@v3
        if: github.event_name == 'push'
        with:
          registry: ${{ env.REGISTRY }}
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}
      - uses: docker/metadata-action@v5
        id: meta
        with:
          images: ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}
          tags: |
            type=sha,format=long,prefix=sha-
            type=semver,pattern={{version}}
      - uses: docker/build-push-action@v6
        with:
          context: .
          push: ${{ github.event_name == 'push' }}
          tags: ${{ steps.meta.outputs.tags }}
          labels: ${{ steps.meta.outputs.labels }}
          load: ${{ github.event_name == 'pull_request' }}
          cache-from: type=gha
          cache-to: type=gha,mode=max
          provenance: true
          sbom: true
      - name: Scan PR image
        if: github.event_name == 'pull_request'
        run: docker scout cves task-api:ci

  deploy-staging:
    if: github.event_name == 'push' && github.ref == 'refs/heads/main'
    needs: build-scan
    runs-on: ubuntu-latest
    environment: staging
    steps:
      - name: Deploy immutable digest
        run: echo "Deploy approved image digest via environment-specific mechanism"
      - name: Smoke test
        run: echo "Run staging smoke tests"
~~~

Workflow trên là skeleton. Một workflow thật cần:

- Cấu hình tag chính xác cho PR/main/tag.
- Lấy và truyền digest giữa job.
- Deploy mechanism thật (SSH/Compose host/ECS/Kubernetes).
- Environment protection/approval.
- Scan command/report/policy thực tế.
- Cleanup và artifact retention.
- Permissions least privilege.

Không copy skeleton vào production mà chưa review trigger, permissions và secret exposure.

---

## 17. GitHub Actions security

### Pull request từ fork

Code trong fork có thể không đáng tin. Tránh cấp:

- Registry push token.
- Production secret.
- `id-token: write` không cần thiết.
- Deploy permission.
- Docker socket/privileged runner không kiểm soát.

PR job nên build/scan không push production. Nếu cần push PR image, dùng isolated namespace/token giới hạn và retention ngắn.

### Action pinning

Dùng action major version thuận tiện nhưng cần policy update. High-assurance pipeline có thể pin commit SHA của action và update qua Dependabot.

### Permissions

Default permissions nên tối thiểu:

~~~yaml
permissions:
  contents: read
~~~

Job push mới cấp `packages: write`; job OIDC mới cấp `id-token: write`. Không cấp write toàn workflow nếu chỉ một job cần.

---

## 18. Bài tập tự làm

### Bài 1: Build trên pull request

Tạo workflow PR chạy unit test, Buildx build, integration test và scan nhưng không push production registry.

### Bài 2: Tag commit SHA

Tag image bằng full commit SHA, lưu tag/digest như job artifact và chứng minh tag không phụ thuộc `latest`.

### Bài 3: Push sau merge main

Thêm điều kiện chỉ push khi event là push vào `main`. Kiểm tra fork PR không nhận write token.

### Bài 4: Integration test PostgreSQL/Redis

Dùng Compose test stack với healthcheck, chạy API image vừa build, gọi endpoint và cleanup volume/container trong `always` step.

### Bài 5: Cache

Thêm `cache-from/cache-to`, so sánh cold/warm build time và xác minh cache miss vẫn build được.

### Bài 6: Multi-platform

Build `linux/amd64` và `linux/arm64` trong registry test, inspect manifest/index và chạy test trên platform phù hợp.

### Bài 7: Staging và smoke

Deploy digest cụ thể lên staging, chạy health/smoke test, ghi release metadata và rollback về digest trước.

### Bài 8: Rollback với migration

Thiết kế scenario app mới đã migrate schema. Phân tích vì sao rollback image đơn thuần có thể fail và đề xuất expand/contract migration.

---

## 19. Câu hỏi kiểm tra và đáp án gợi ý

1. Buildx giải quyết vấn đề gì?
2. Vì sao multi-platform build có thể khác native build?
3. `cache-to/cache-from` dùng để làm gì?
4. Vì sao tag theo commit SHA tốt hơn `latest`?
5. PR job nên push image production không?
6. Vì sao phải scan final runtime image?
7. Deploy staging bằng tag hay digest tốt hơn cho reproducibility?
8. Smoke test khác integration test thế nào?
9. Vì sao rollback app image có thể không đủ sau database migration?
10. Registry deploy identity cần quyền gì?
11. Vì sao fork PR không nên có production secret?
12. Build một lần promote nhiều môi trường nghĩa là gì?

Đáp án ngắn: Buildx hỗ trợ BuildKit/multi-platform/cache; native dependency/emulation/platform khác nhau; cache export/import CI; SHA truy source và ổn định hơn mutable tag; PR không nên push production; runtime là artifact thật deploy; digest rõ identity; smoke test kiểm tra flow tối thiểu sau deploy; schema mới có thể không tương thích app cũ; deploy thường cần pull/verify; fork code không đáng tin; cùng digest được promote thay vì rebuild.

---

## 20. Tiêu chí hoàn thành tuần 15

- [ ] Dùng được Buildx và hiểu builder lifecycle.
- [ ] Build/push được image multi-platform khi có yêu cầu.
- [ ] Cấu hình cache-to/cache-from cho CI.
- [ ] Tag image theo commit SHA.
- [ ] Login/push/pull registry bằng token/quyền tối thiểu.
- [ ] Viết được GitHub Actions workflow cơ bản.
- [ ] Chạy unit và integration test với container dependencies.
- [ ] Scan image trước push/deploy.
- [ ] Push immutable tag và lưu digest.
- [ ] Deploy staging bằng digest.
- [ ] Chạy smoke test và ghi release metadata.
- [ ] Rollback về digest trước đó.
- [ ] Bảo vệ fork PR, permissions và CI secrets.
- [ ] Không rebuild khác artifact khi promote production.

Kiểm tra cuối buổi:

~~~bash
docker buildx ls
docker buildx inspect --bootstrap
docker image inspect <image>
docker buildx imagetools inspect <registry>/<image>:<tag>
docker compose -f compose.test.yaml ps
docker compose -f compose.test.yaml logs --tail 100
docker compose -f compose.test.yaml down -v
~~~

Mục tiêu của tuần này là biến Docker image thành artifact có thể build, test, scan, push, deploy và rollback bằng identity rõ ràng từ commit đến digest.

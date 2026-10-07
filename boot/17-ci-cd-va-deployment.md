# Tuần 17 — CI/CD và deployment

> Stack thực hành: Java 21, Spring Boot, Maven Wrapper, Docker, GitHub Actions, container registry, PostgreSQL và Prometheus. Bài này nối tiếp Docker ở Tuần 16 và observability/resilience ở Tuần 15.

## 1. Mục tiêu bài học

Sau tuần này, bạn có thể:

- Phân biệt CI, continuous delivery và continuous deployment.
- Thiết kế pipeline từ checkout tới deploy có quality gate.
- Chạy compile, unit test, integration test, coverage, lint và security scan.
- Build image reproducible và push vào container registry.
- Tag image bằng commit SHA, release version và digest.
- Tách cấu hình dev, staging và production.
- Chạy database migration an toàn khi deploy.
- Hiểu rolling, blue-green và canary deployment.
- Dùng health/readiness để tránh đưa instance chưa sẵn sàng vào traffic.
- Smoke test sau deploy và dừng rollout khi metric xấu.
- Rollback application mà không làm hỏng schema database.
- Backup, restore và kiểm tra RPO/RTO.
- Quản lý secret bằng OIDC/secret manager thay vì hardcode.

Mục tiêu của CI/CD không phải là deploy nhanh bằng mọi giá. Mục tiêu là biến release thành quy trình lặp lại, có bằng chứng kiểm tra, có approval phù hợp và có đường lui rõ ràng.

---

## 2. CI, delivery và deployment

### 2.1. Continuous Integration

Mỗi pull request phải được kiểm tra trên môi trường sạch:

~~~text
commit → checkout → compile → unit test → integration test → scan → report
~~~

CI phát hiện lỗi càng gần commit càng tốt. Không merge khi check bắt buộc thất bại.

### 2.2. Continuous Delivery

Mỗi commit hợp lệ tạo được artifact/image và có thể deploy. Production vẫn cần approval hoặc thao tác phát hành:

~~~text
merge main → build immutable image → deploy staging → smoke test → approval → production
~~~

### 2.3. Continuous Deployment

Mỗi thay đổi đã qua gate được tự động đưa lên production. Chỉ chọn cách này khi monitoring, rollback, ownership và blast-radius đã đủ tốt.

Không gọi một pipeline là CD chỉ vì nó chạy `docker build`. CD cần artifact, environment, health check, rollout status và rollback strategy.

---

## 3. Branch, release và immutable artifact

Một lựa chọn đơn giản cho project cá nhân:

- Pull request vào `main` chạy toàn bộ CI.
- Merge vào `main` build image và deploy staging tự động.
- Tag `v1.4.0` tạo release candidate hoặc production release sau approval.
- Hotfix tạo branch từ tag đang chạy, merge lại cả `main` và branch release nếu có.

Không build lại source ở mỗi environment. Build một image rồi promote cùng image đó:

~~~text
source commit abc123
  → image ghcr.io/acme/task-api:abc123
  → staging
  → production (cùng digest)
~~~

Nếu staging và production chạy image khác nhau, kết quả staging không còn là bằng chứng cho production.

### 3.1. Tag image

Dùng ít nhất:

- Commit SHA bất biến: `sha-abc123`.
- Release version: `v1.4.0`.
- Có thể có tag môi trường như `staging`, nhưng không dùng tag mutable làm nguồn rollback duy nhất.

Lưu digest sau push. Rollback nên trỏ tới digest hoặc SHA đã biết, không trỏ tới `latest`.

### 3.2. Conventional commit và changelog

~~~text
feat(task): add task expiration
fix(auth): reject expired refresh token
perf(query): add index for project status
chore(ci): pin action versions
~~~

Commit convention không thay thế review. Một migration destructive vẫn cần đánh giá riêng dù commit bắt đầu bằng `feat` hay `fix`.

---

## 4. Pipeline tổng thể và quality gate

~~~text
Checkout → Compile → Unit tests → Integration tests
→ Coverage → Security scan → Build image
→ Deploy staging → Smoke test → Approval → Production
~~~

Một merge không đạt khi:

- Compile hoặc test thất bại.
- Coverage giảm dưới ngưỡng đã thống nhất.
- Dependency có CVE critical chưa được accept có thời hạn.
- Docker image chứa secret hoặc chạy bằng root.
- Integration test không kết nối được dependency cần thiết.
- Artifact không truy xuất được commit nguồn.

Đừng đặt coverage threshold cao giả tạo rồi exclude phần lớn code. Coverage là tín hiệu, không phải chứng minh mọi behavior đúng.

### 4.1. Tách job và artifact

| Job | Kết quả |
|---|---|
| `compile` | Class/JAR compile được |
| `unit-test` | JUnit report, coverage |
| `integration-test` | Test DB/Redis/provider |
| `security` | Dependency, secret, image scan |
| `image` | Image immutable + digest |
| `deploy-staging` | Rollout staging |
| `smoke` | HTTP/business smoke pass |
| `deploy-production` | Production rollout |

Artifact test report phải được upload kể cả khi test fail để debug CI.

---

## 5. GitHub Actions CI cho Spring Boot

### 5.1. Workflow pull request

Tạo `.github/workflows/ci.yml`:

~~~yaml
name: CI

on:
  pull_request:
  push:
    branches: [main]

permissions:
  contents: read

jobs:
  verify:
    runs-on: ubuntu-latest
    timeout-minutes: 20
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up Java
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 21
          cache: maven

      - name: Make Maven Wrapper executable
        run: chmod +x mvnw

      - name: Compile and unit test
        run: ./mvnw -B verify

      - name: Upload test reports
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: test-reports-${{ github.sha }}
          path: |
            target/surefire-reports/**
            target/failsafe-reports/**
            target/site/jacoco/**
          if-no-files-found: ignore
~~~

`setup-java` cache Maven nhưng không thay thế Maven Wrapper. Action major version nên được cập nhật có review; production có thể pin commit SHA của action để giảm supply-chain risk.

### 5.2. Unit và integration test

Nếu Maven project tách integration test bằng Failsafe:

~~~bash
./mvnw -B test
./mvnw -B verify -Pintegration-test
~~~

Một cách khác là dùng profile mặc định chạy Testcontainers trong `verify`. Dù chọn cách nào, CI phải thực sự chạy test có database/Redis thay vì mock mọi dependency.

Test cần kiểm tra:

- API status, validation và error envelope.
- Transaction và migration trên PostgreSQL thật.
- Redis serialization/TTL nếu app dùng cache.
- Authentication, authorization và ownership.
- Timeout/retry/circuit breaker với fake provider.
- Idempotency khi worker chạy lại.

### 5.3. Coverage

JaCoCo cần được cấu hình trong `pom.xml`; pipeline không tự tạo coverage chỉ bằng cách upload thư mục. Ví dụ command sau chỉ có ý nghĩa khi plugin đã được khai báo:

~~~bash
./mvnw -B verify jacoco:report
~~~

Đặt threshold ở module phù hợp. Theo dõi branch coverage cho authorization, error handling và retry; đừng chỉ nhìn line coverage.

---

## 6. Lint, dependency và security scan

### 6.1. Dependency scan

~~~bash
./mvnw -B dependency:tree
./mvnw -B org.owasp:dependency-check-maven:check
~~~

Dependency-check có thể cần NVD data/cache và làm CI lâu hơn. Cache dữ liệu scanner có kiểm soát, không bỏ qua CVE critical chỉ vì job chậm.

Trong pipeline thật, khai báo version của Maven plugin trong `pom.xml` thay vì để Maven tự chọn latest ở thời điểm chạy CI. Cùng nguyên tắc đó áp dụng cho GitHub Action: pin major đã review hoặc pin commit SHA theo policy của team.

Nếu vulnerability là false positive hoặc chưa thể cập nhật, tạo exception có CVE, dependency, lý do, biện pháp giảm rủi ro, owner và ngày hết hạn.

### 6.2. Secret scan

Scan commit và artifact bằng gitleaks hoặc scanner tương đương. Xóa secret khỏi file hiện tại không đủ; secret có thể vẫn nằm trong git history hoặc image layer.

Nếu secret đã lộ:

1. Revoke/rotate ngay.
2. Xác định phạm vi truy cập.
3. Xóa khỏi pipeline/log/artifact.
4. Điều tra history và audit log.
5. Tạo rule ngăn tái diễn.

### 6.3. SAST, image scan và SBOM

Tối thiểu nên có SAST cho Java/Spring Security, dependency scan cho Maven, secret scan, container scan và SBOM CycloneDX hoặc SPDX.

~~~bash
docker scout cves task-api:${GIT_SHA}
trivy image --exit-code 1 --severity CRITICAL,HIGH task-api:${GIT_SHA}
~~~

Có policy cho critical/high CVE, exception có thời hạn và owner rõ ràng.

---

## 7. Build và push container image

### 7.1. GitHub Container Registry

Job image chỉ chạy sau verify:

~~~yaml
  image:
    needs: verify
    if: github.event_name == 'push' && github.ref == 'refs/heads/main'
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write
      id-token: write
    steps:
      - uses: actions/checkout@v4
      - uses: docker/setup-buildx-action@v3
      - uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}
      - name: Build and push
        uses: docker/build-push-action@v6
        with:
          context: .
          push: true
          tags: |
            ghcr.io/${{ github.repository }}:sha-${{ github.sha }}
            ghcr.io/${{ github.repository }}:main
          labels: |
            org.opencontainers.image.revision=${{ github.sha }}
            org.opencontainers.image.source=${{ github.server_url }}/${{ github.repository }}
          cache-from: type=gha
          cache-to: type=gha,mode=max
~~~

`main` là tag tiện dụng nhưng mutable; deploy và rollback phải dùng `sha-${{ github.sha }}` hoặc digest. Đặt package visibility/private registry phù hợp, không public image có code hoặc config nội bộ.

### 7.2. Provenance và SBOM

Nếu nền tảng hỗ trợ, bật attestation/SBOM và verify trước deploy:

~~~yaml
      - name: Build with provenance
        uses: docker/build-push-action@v6
        with:
          context: .
          push: true
          provenance: true
          sbom: true
          tags: ghcr.io/${{ github.repository }}:sha-${{ github.sha }}
~~~

Không đưa secret build arg vào image. Build arg có thể xuất hiện trong metadata/log; dùng secret mount nếu build thật sự cần credential.

---

## 8. Dev, staging và production

### 8.1. Dev

- Docker Compose chạy app, PostgreSQL, Redis.
- Dữ liệu disposable hoặc volume local có tên riêng.
- Log DEBUG khi điều tra.
- Không dùng secret production.
- Có thể deploy thủ công để học.

### 8.2. Staging

- Dùng image cùng digest với production dự kiến.
- Schema và config gần production.
- Có smoke test, integration test và synthetic traffic.
- Secret staging tách hoàn toàn production.
- Dữ liệu anonymized, không copy PII production tùy tiện.

### 8.3. Production

- Deploy qua approval/environment protection.
- Chỉ dùng registry private và image đã scan.
- Secret từ secret manager/OIDC.
- Có readiness, graceful shutdown, resource limit và alert.
- Ghi release owner, commit, image digest, migration version.
- Có rollback command đã test.

Config khác môi trường nên nằm ngoài image. Artifact bất biến, config mutable có kiểm soát:

~~~text
same image + dev config     → dev
same image + staging config → staging
same image + prod config    → production
~~~

---

## 9. Database migration khi deploy

### 9.1. Quy tắc expand/contract

Rolling deployment làm v1 và v2 cùng chạy. Migration phải backward-compatible:

~~~text
Expand: thêm cột/index nullable hoặc bảng mới
Deploy code: v1 và v2 đều đọc được schema
Backfill: chạy batch có giới hạn
Switch: code mới ghi/đọc field mới
Contract: xóa field cũ ở release sau
~~~

Không rename/drop cột ngay trong cùng release khi instance cũ vẫn còn query cột đó.

Ví dụ an toàn hơn:

1. Thêm `display_name` nullable.
2. Code v2 ghi cả `name` và `display_name`.
3. Backfill `display_name` theo batch.
4. Code v3 chỉ đọc `display_name`.
5. Xóa `name` ở release sau khi kiểm tra không còn consumer.

### 9.2. Migration tool

Flyway/Liquibase migration nên:

- Có version và checksum.
- Chạy một lần có lock.
- Không chứa dữ liệu môi trường production trong repository.
- Có timeout và log migration version.
- Test trên snapshot gần production.

Không chạy migration đồng thời từ tất cả replica nếu tool/lock không hỗ trợ. Tách migration job khỏi app startup khi migration lâu hoặc cần approval:

~~~text
backup → migrate job → verify schema → deploy app → smoke test
~~~

### 9.3. Migration thất bại

Migration failure không đồng nghĩa rollback SQL luôn an toàn. Với thay đổi destructive:

- Stop rollout.
- Giữ schema ở trạng thái tương thích.
- Khôi phục từ backup nếu dữ liệu đã bị thay đổi sai.
- Sửa migration bằng migration mới, không sửa file đã chạy trên production.

---

## 10. Deployment strategy

### 10.1. Rolling deployment

~~~text
v1 v1 v1
↓
v2 v1 v1
v2 v2 v1
v2 v2 v2
~~~

Điều kiện:

- Schema backward-compatible.
- Readiness chỉ pass khi app thật sự nhận traffic.
- Có tối thiểu một instance healthy trong lúc rollout.
- Graceful shutdown để drain request.
- Metric/error threshold dừng rollout.

Ưu điểm là không cần gấp đôi toàn bộ hạ tầng. Nhược điểm là hai version cùng tồn tại và rollback code không giúp nếu migration đã phá schema.

### 10.2. Blue-green

~~~text
blue v1  ← traffic
green v2   idle

smoke green → switch traffic → green nhận traffic
~~~

Rollback nhanh bằng cách chuyển traffic lại blue, nhưng cần gấp đôi compute và phải xử lý schema/shared database cẩn thận.

### 10.3. Canary

Gửi phần nhỏ traffic vào v2:

~~~text
v1: 95% → kiểm tra error rate, p95, business metric
v2:  5% → nếu ổn thì tăng dần
~~~

Canary cần traffic splitting, metric theo version và rollback tự động/manual. Không gọi là canary nếu mọi request vẫn đi vào v1.

---

## 11. Health, rollout và smoke test

### 11.1. Readiness trước traffic

Instance mới chỉ nhận traffic khi:

- JVM startup hoàn tất.
- Migration/schema compatible.
- Connection pool có thể lấy connection.
- Dependency bắt buộc được kiểm tra với timeout.
- Metrics/log/tracing đã hoạt động.

Liveness không nên phụ thuộc toàn bộ database; nếu database tạm lỗi mà restart tất cả pod, outage có thể nặng hơn.

### 11.2. Smoke test sau deploy

Smoke test nhanh, deterministic, không mutate dữ liệu thật nếu không có cleanup:

~~~bash
set -euo pipefail

curl --fail --silent https://staging.example.com/actuator/health/readiness
curl --fail --silent "https://staging.example.com/api/projects?page=0&size=1"
curl --fail --silent "https://staging.example.com/api/tasks?page=0&size=1"
~~~

Kiểm tra thêm:

- Response envelope và status code.
- Authentication/authorization cơ bản bằng test account.
- Trace ID xuất hiện trong response/log.
- Metric request tăng đúng.
- Không có exception spike sau deploy.

Không hardcode production token trong workflow. Dùng test identity riêng với quyền tối thiểu và secret manager.

### 11.3. Dừng rollout

Đặt điều kiện dừng:

- Readiness fail liên tục.
- HTTP 5xx vượt ngưỡng.
- p95/p99 tăng bất thường.
- DB pool/executor queue bão hòa.
- Business metric như task create success giảm.

Rollback không nên chờ người dùng báo lỗi đầu tiên.

---

## 12. Rollback

### 12.1. Application rollback

Lưu release metadata:

~~~text
release_id=2026-09-26.1
commit=abc123
image=ghcr.io/acme/task-api@sha256:...
migration=V18
deployed_at=...
~~~

Rollback dùng image digest trước đó:

~~~bash
kubectl -n task set image deployment/task-api \
  task-api=ghcr.io/acme/task-api@sha256:<previous-digest>
kubectl -n task rollout status deployment/task-api --timeout=120s
~~~

Lệnh trên là ví dụ Kubernetes. Với ECS, Nomad, Cloud Run hoặc platform khác, dùng cơ chế promote revision tương ứng; không chạy máy móc nếu nền tảng không hỗ trợ.

### 12.2. Database rollback

Ưu tiên migration backward-compatible để chỉ cần rollback code. Không rollback database bằng cách xóa dữ liệu vội vàng.

Phân biệt:

- **Rollback code**: đưa image về digest trước.
- **Roll-forward schema**: thêm migration sửa lỗi.
- **Restore backup**: khôi phục dữ liệu khi corruption/loss nghiêm trọng.

Database restore có thể gây downtime và mất dữ liệu sau backup. Phải được approval và ghi rõ RPO/RTO.

### 12.3. Rollback checklist

- [ ] Digest/image trước vẫn còn trong registry.
- [ ] Schema tương thích với image trước.
- [ ] Feature flag tắt được behavior lỗi.
- [ ] Người on-call biết command rollback.
- [ ] Smoke test sau rollback.
- [ ] Ghi incident và nguyên nhân sau khi ổn định.

---

## 13. Backup và restore

### 13.1. RPO và RTO

- **RPO**: mất tối đa bao nhiêu dữ liệu chấp nhận được?
- **RTO**: cần phục hồi dịch vụ trong bao lâu?

RPO/RTO quyết định tần suất backup, replication và quy trình restore. Một file backup chưa từng restore không phải chiến lược backup hoàn chỉnh.

### 13.2. Backup PostgreSQL local

~~~bash
mkdir -p backup
docker compose exec -T db pg_dump \
  -U task -d taskdb --format=custom > backup/taskdb-$(date +%Y%m%d-%H%M%S).dump
~~~

Restore vào database disposable để kiểm tra:

~~~bash
cat backup/taskdb-20260926-120000.dump | \
  docker compose exec -T db pg_restore -U task -d taskdb --clean --if-exists
~~~

Các lệnh restore có thể xóa object hiện tại trong database đích. Chỉ chạy trên database đã xác định, có backup mới và approval phù hợp.

### 13.3. Production backup

Production nên dùng backup/point-in-time recovery do managed PostgreSQL hoặc backup system quản lý. Checklist:

- Backup mã hóa.
- Quyền ghi/đọc tách biệt.
- Retention policy rõ.
- Copy khác region nếu cần.
- Restore drill định kỳ.
- Alert khi backup thất bại.
- Không lưu credential backup trong repository.

Redis cache thường có thể rebuild, nhưng nếu Redis chứa queue hoặc state bắt buộc thì phải xem lại persistence/backup policy; không mặc định coi mọi Redis data là disposable.

---

## 14. Lỗi thường gặp

- Build lại source riêng cho staging và production.
- Deploy bằng tag `latest` nên không biết chính xác version đang chạy.
- Chỉ chạy unit test, không chạy integration test với database thật.
- Có test nhưng không upload report khi job fail.
- Bỏ qua CVE critical bằng một exception không có expiry.
- Đưa secret vào GitHub workflow, Dockerfile, image layer hoặc log.
- Dùng `GITHUB_TOKEN` với quyền rộng hơn cần thiết.
- Migration drop/rename column trong lúc rolling deployment.
- Chạy migration đồng thời từ mọi replica.
- Deploy production không có staging smoke test.
- Readiness pass trước khi connection pool/dependency sẵn sàng.
- Rollback code sau migration destructive rồi làm v1 không đọc được schema.
- Không lưu image digest/release metadata.
- Rollback chưa từng test.
- Backup có tạo nhưng chưa từng restore.
- Dùng production PII trong staging.
- Alert không có threshold, owner hoặc runbook.

---

## 15. Bài thực hành bắt buộc

1. Tạo workflow CI chạy compile, unit test và integration test.
2. Upload JUnit/coverage report kể cả khi test fail.
3. Thêm dependency, secret và container image scan.
4. Build image từ Dockerfile Tuần 16 và push vào registry private.
5. Tag image bằng commit SHA và lưu digest.
6. Deploy cùng image digest lên staging.
7. Chạy smoke test health, list project và list task.
8. Thêm approval trước production environment.
9. Thực hiện migration expand/contract cho một field Project.
10. Mô phỏng rolling deploy với hai version ứng dụng.
11. Rollback về image digest cũ và chạy smoke test.
12. Backup PostgreSQL, xóa database disposable rồi restore.
13. Viết runbook khi rollout fail, migration fail và database mất.
14. Đo RPO/RTO của local stack và nêu khác biệt production.

---

## 16. Câu hỏi tự kiểm tra

1. CI khác continuous delivery và continuous deployment thế nào?
2. Vì sao phải promote cùng image digest qua các environment?
3. Quality gate nào nên chặn merge?
4. Unit test và integration test bổ sung cho nhau ra sao?
5. Vì sao `latest` không phù hợp rollback?
6. SBOM giúp điều tra supply-chain risk thế nào?
7. OIDC có lợi gì so với lưu cloud access key lâu dài trong CI?
8. Vì sao migration phải expand/contract khi rolling deploy?
9. Readiness khác liveness trong rollout thế nào?
10. Khi nào dùng rolling, blue-green hoặc canary?
11. Vì sao rollback code không luôn sửa được database migration?
12. RPO và RTO ảnh hưởng backup policy ra sao?
13. Smoke test tốt cần tránh side effect nào?
14. Khi nào phải stop rollout thay vì retry?
15. Một rollback runbook tối thiểu phải có những gì?

---

## 17. Tiêu chí hoàn thành tuần 17

- [ ] PR chạy compile, unit test và integration test.
- [ ] Có coverage report và quality threshold hợp lý.
- [ ] Dependency, secret và image được scan.
- [ ] Image được build một lần và promote bằng digest.
- [ ] Registry private và quyền CI tối thiểu.
- [ ] Có dev, staging, production config tách biệt.
- [ ] Staging chạy smoke test trước production.
- [ ] Production có approval/environment protection.
- [ ] Migration backward-compatible với rolling deploy.
- [ ] Có readiness, graceful shutdown và rollout timeout.
- [ ] Có rollback image và rollback checklist.
- [ ] Có backup encryption, retention và restore drill.
- [ ] Có RPO/RTO được ghi lại.
- [ ] Có alert, dashboard và runbook cho release failure.

---

## 18. Gợi ý lịch học 7 ngày

| Ngày | Nội dung | Kết quả |
|---|---|---|
| 1 | CI concepts, branch và artifact | Hiểu pipeline |
| 2 | GitHub Actions Maven | CI chạy test |
| 3 | Coverage, dependency, secret và image scan | Quality gate |
| 4 | Registry, tag, digest, SBOM | Image truy xuất được |
| 5 | Staging, migration expand/contract, smoke test | Deploy an toàn |
| 6 | Rolling/blue-green/canary, rollback | Có đường lui |
| 7 | Backup/restore, RPO/RTO, runbook | Hoàn thiện checklist |

Tuần 18 theo roadmap chuyển sang modular monolith: package-by-feature, layered/hexagonal architecture, domain event, transactional outbox và module boundary.

## 19. Tài liệu tham khảo

- GitHub Actions documentation: https://docs.github.com/en/actions
- GitHub Actions security hardening: https://docs.github.com/en/actions/security-for-github-actions/security-guides/security-hardening-for-github-actions
- Docker build and push action: https://github.com/docker/build-push-action
- Spring Boot container images: https://docs.spring.io/spring-boot/reference/packaging/container-images/
- Flyway migrations: https://documentation.red-gate.com/flyway
- Liquibase documentation: https://docs.liquibase.com/
- Kubernetes deployment strategies: https://kubernetes.io/docs/concepts/workloads/controllers/deployment/
- PostgreSQL backup and restore: https://www.postgresql.org/docs/current/backup.html
- SLSA supply-chain framework: https://slsa.dev/spec/v1.0/

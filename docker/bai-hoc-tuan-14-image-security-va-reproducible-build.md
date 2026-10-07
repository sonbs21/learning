# Tuần 14: Image security và reproducible build

## 1. Mục tiêu

Sau bài này, bạn có thể:

- Chọn base image từ nguồn đáng tin cậy và có lifecycle rõ.
- Phân biệt tag mutable, version tag, image ID và digest.
- Hiểu reproducible build và các nguồn làm build không tái tạo được.
- Scan image bằng Docker Scout hoặc scanner tương đương.
- Triage CVE theo severity, reachability, exposure và fix availability.
- Tạo/đọc SBOM ở mức thực hành.
- Hiểu image signing và provenance ở mức kiến trúc.
- Cấu hình Dependabot hoặc cơ chế tương đương cho base/dependency update.
- Thiết kế rebuild định kỳ để nhận bản vá base image.
- Kiểm soát quyền push/pull/delete trong registry.
- Tạo CI policy fail khi image vi phạm ngưỡng vulnerability đã định.
- Kiểm tra image history để phát hiện secret và artifact không cần thiết.

Thời lượng đề xuất: **8–12 giờ** gồm scan, pin digest, tạo artifact metadata và viết policy CI.

---

## 2. Image security và reproducibility là hai bài toán liên quan

### Image security

Câu hỏi chính:

- Image chứa những package nào?
- Có vulnerability nào không?
- Ai build và push image?
- Image có chạy non-root không?
- Có secret hoặc source không cần thiết không?
- Runtime image có nhỏ và rõ footprint không?

### Reproducible build

Câu hỏi chính:

- Cùng source/dependency/input có tạo cùng image content không?
- Có biết chính xác base image digest không?
- Có truy ra commit, builder, Dockerfile và dependency không?
- Có thể rebuild/rollback artifact cũ không?

~~~text
Trusted source
    ↓
Pinned inputs
    ↓
Controlled build
    ↓
SBOM + scan
    ↓
Signature + provenance
    ↓
Registry policy
    ↓
Verified deployment
~~~

Một image sạch CVE nhưng build từ nguồn không xác minh vẫn có rủi ro. Một image có signature nhưng chứa dependency dễ khai thác cũng không đủ an toàn. Cần nhìn toàn chuỗi.

---

## 3. Base image từ trusted source

### 3.1. Base image quyết định supply chain đầu tiên

Base image có thể chứa:

- OS userspace.
- libc/OpenSSL/CA certificates.
- Java runtime/JDK.
- Package manager và native libraries.
- User/entrypoint mặc định.
- Vulnerability và lifecycle của vendor.

Chọn base image theo:

- Nhà cung cấp/repository rõ.
- Chính sách release/patch.
- Architecture/platform cần dùng.
- Support lifecycle.
- Scan/SBOM có sẵn.
- Compatibility với ứng dụng.
- Kích thước và attack surface.

Không chọn image chỉ vì có số download cao hoặc nhỏ nhất.

### 3.2. Kiểm tra image origin

~~~bash
docker image inspect eclipse-temurin:21-jre
docker image inspect task-api:0.4.0
docker history --no-trunc task-api:0.4.0
~~~

Kiểm tra:

- Repository/tag/digest.
- OS/architecture.
- User/entrypoint.
- Labels/source metadata.
- Layer history.
- Base image update policy.

Với registry private, kiểm tra namespace/organization và quyền push. Không pull image cùng tên từ registry không rõ khi CI có thể bị cấu hình nhầm.

### 3.3. Official/vendor image không có nghĩa không có CVE

Image chính thức giúp giảm rủi ro provenance/maintenance, nhưng vẫn có thể chứa vulnerability. Cần scan, pin/update và đánh giá runtime exposure.

---

## 4. Tag mutable và digest

### 4.1. Tag là reference dễ đọc

~~~text
FROM eclipse-temurin:21-jre
FROM postgres:16
FROM registry.example.com/team/task-api:1.4.2
~~~

Tag có thể được di chuyển sang digest khác. Ngay cả version tag có policy bất biến hay không còn tùy registry/owner.

`latest` đặc biệt nguy hiểm cho reproducibility:

~~~dockerfile
FROM eclipse-temurin:latest
~~~

Hai lần build ở thời điểm khác có thể dùng base khác mà Dockerfile/source không đổi.

### 4.2. Digest định danh nội dung

~~~text
FROM eclipse-temurin:21-jre@sha256:<digest>
~~~

Hoặc deploy image:

~~~text
registry.example.com/team/task-api@sha256:<digest>
~~~

Lấy digest sau pull/push:

~~~bash
docker pull eclipse-temurin:21-jre
docker image inspect eclipse-temurin:21-jre --format '{{json .RepoDigests}}'
~~~

Digest cần được lưu trong source/config lock hoặc release metadata. Không copy placeholder vào production.

### 4.3. Trade-off của pin digest

Lợi ích:

- Build/release có input rõ.
- Rollback chính xác.
- Audit biết artifact nào đã chạy.
- Tránh tag bị di chuyển ngoài ý muốn.

Trade-off:

- Không tự nhận base patch mới.
- Cần automation tạo PR update digest.
- Cần scan/rebuild định kỳ.
- Digest có thể khác theo architecture/platform.

Pin digest không thay thế patch management; nó làm update trở thành thay đổi được review.

---

## 5. Reproducible build

### 5.1. Định nghĩa thực tế

Một build có tính reproducible khi cùng bộ input được kiểm soát tạo artifact giống hoặc tương đương theo tiêu chí đã định.

Bộ input gồm:

- Git commit/source.
- Dockerfile và build context.
- Base image digest.
- OS/platform/architecture.
- Dependency lock/version.
- Build tool/JDK/Maven version.
- Build args/environment.
- Generated metadata/timestamp.
- Build secrets/tool behavior.

Không nên nói “reproducible” chỉ vì Dockerfile nằm trong Git.

### 5.2. Nguồn không deterministic

- Base tag mutable.
- Dependency version range hoặc repository thay đổi.
- `apt-get update` lấy package index mới.
- Download URL không pin checksum.
- Timestamp được ghi vào JAR/layer.
- Host architecture khác.
- Build arg/environment khác.
- Generated UUID/build ID.
- Network artifact thay đổi.
- Compiler/JDK/build plugin khác.
- Compression/order metadata khác.

### 5.3. Cách tăng reproducibility

- Pin base image bằng digest khi cần.
- Lock application dependencies.
- Pin Maven/Gradle/plugin/wrapper.
- Dùng artifact repository có retention/checksum.
- Ghi source revision/build metadata.
- Dùng builder/platform được xác định.
- Tránh download không checksum.
- Tách build cache khỏi output artifact.
- Rebuild định kỳ từ input đã lock.
- Lưu digest và SBOM/provenance.

### 5.4. “Cùng nội dung” và “cùng behavior”

Hai image có thể có digest khác vì metadata/timestamp khác nhưng behavior giống. Ngược lại, cùng tag không bảo đảm content giống.

Policy tổ chức cần quy định mức nào cần:

- Byte-for-byte identical.
- Same dependency/base digest.
- Same tests/SBOM/provenance.
- Same runtime behavior trên platform mục tiêu.

---

## 6. Image metadata và traceability

Thêm OCI labels vào Dockerfile:

~~~dockerfile
ARG VCS_REF=unknown
ARG VERSION=dev

LABEL org.opencontainers.image.title="task-api" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.revision="${VCS_REF}" \
      org.opencontainers.image.source="https://example.invalid/team/task-api"
~~~

Build:

~~~bash
docker build \
  --build-arg VCS_REF="$(git rev-parse HEAD)" \
  --build-arg VERSION=1.4.2 \
  -t task-api:1.4.2 .
~~~

Không đưa secret vào label hoặc build arg. Metadata nên giúp trả lời:

- Image build từ commit nào?
- Version release nào?
- Source repository nào?
- Builder/pipeline nào?
- Base digest nào?

Build args có thể xuất hiện trong metadata/history; chỉ dùng cho giá trị không nhạy cảm.

---

## 7. Scan image

### 7.1. Scan lúc nào?

Nên scan:

- Pull request/build candidate.
- Trước push registry.
- Sau push nếu registry có scanner.
- Định kỳ để phát hiện CVE mới trong image cũ.
- Trước deploy/promote production.

Scan runtime image cuối, không chỉ scan build image. Build stage có JDK/Maven/tool nhiều hơn runtime.

### 7.2. Docker Scout hoặc scanner tương đương

Nếu Docker Scout có sẵn:

~~~bash
docker scout quickview task-api:1.4.2
docker scout cves task-api:1.4.2
~~~

Scanner khác có thể dùng:

~~~bash
trivy image task-api:1.4.2
grype task-api:1.4.2
~~~

Command/tool/version phụ thuộc môi trường và policy. Hãy dùng scanner được tổ chức phê duyệt; không coi một tool cụ thể là nguồn sự thật duy nhất.

### 7.3. Scan output cần lưu

Lưu cùng release metadata:

- Image digest.
- Scanner name/version.
- Vulnerability database timestamp/version nếu có.
- SBOM/reference.
- Findings và severity.
- Suppression/accepted risk.
- Scan policy result.

Không chỉ lưu một ảnh chụp màn hình “0 critical” không có digest.

---

## 8. CVE triage

Một finding cần được đánh giá theo context:

| Câu hỏi | Ý nghĩa |
|---|---|
| Severity là gì? | Mức độ kỹ thuật ban đầu |
| Package có thật trong final image không? | Loại false positive/build-only |
| Code path có reachable không? | Khả năng exploit thực tế |
| Service có expose network không? | Attack surface |
| Có exploit công khai không? | Mức cấp bách |
| Có bản fix không? | Hành động remediation |
| Có compensating control không? | Risk giảm qua hardening |
| Policy deadline là gì? | Quyết định vận hành |

### 8.1. Không bỏ qua chỉ vì không có port public

Vulnerability vẫn có thể bị khai thác từ:

- Internal network.
- Compromised sibling container.
- Malicious input qua API.
- Background job/file processing.
- Egress callback.
- Host/operator access.

Network isolation là một factor, không phải lý do tự động dismiss.

### 8.2. Không fix bằng suppress tất cả

Suppression cần:

- Finding/package cụ thể.
- Lý do.
- Owner.
- Expiry/review date.
- Compensating control.
- Link ticket.

Không dùng ignore pattern rộng như `CVE-*` hoặc suppress toàn repository.

### 8.3. Quyết định remediation

Thứ tự thường gặp:

1. Update base image patch.
2. Update application dependency.
3. Remove package không cần.
4. Dùng runtime image nhỏ hơn.
5. Thay dependency có risk cao.
6. Thêm hardening/segmentation tạm thời.
7. Document accepted risk có expiry.

Rebuild và rescan sau fix; không kết luận từ package manager output mà chưa scan final image.

---

## 9. SBOM

### 9.1. SBOM là gì?

SBOM, Software Bill of Materials, là inventory các component trong software artifact/image:

- Package name/version.
- Ecosystem/type.
- Relationship/dependency.
- License/metadata tùy format.
- Layer/source tùy tool.

SBOM trả lời “image này gồm gì?”; scanner trả lời “component nào có finding?”; signature/provenance trả lời “ai build và artifact từ đâu?”.

### 9.2. Tạo SBOM

Tool tùy môi trường. Ví dụ Docker Scout nếu hỗ trợ:

~~~bash
docker scout sbom task-api:1.4.2
~~~

Hoặc scanner/SBOM tool khác được phê duyệt:

~~~bash
syft task-api:1.4.2 -o spdx-json > sbom.spdx.json
~~~

Lưu SBOM gắn với image digest, không chỉ tag.

### 9.3. Build image và runtime SBOM

Multi-stage image có build dependency khác runtime. Tùy mục tiêu, tạo:

- SBOM final runtime image để vận hành/vulnerability.
- SBOM source/dependency để license/application analysis.
- SBOM build environment nếu supply-chain policy yêu cầu.

Không nhầm SBOM của build stage với SBOM final image.

### 9.4. SBOM không chứng minh an toàn

SBOM có thể đầy đủ nhưng component vẫn vulnerable. Nó là inventory hỗ trợ scan, response và compliance.

---

## 10. Image signing

### 10.1. Signing giải quyết câu hỏi nào?

Signature giúp xác minh artifact được ký bởi identity/key/pipeline được tin cậy và digest không bị thay đổi sau khi ký.

~~~text
Build image → digest D
       ↓ sign
Signature(identity, D)
       ↓ verify trước deploy
Accept/reject
~~~

Signing không đảm bảo:

- Code không có bug.
- Image không có CVE.
- Base image là tốt.
- Pipeline không bị compromise trước khi ký.

Nó là một lớp provenance/integrity.

### 10.2. Cosign ở mức khái niệm

Ví dụ flow thường gặp:

~~~bash
cosign sign registry.example.com/team/task-api@sha256:<digest>
cosign verify registry.example.com/team/task-api@sha256:<digest>
~~~

Không đưa private key vào repo hoặc image. Production thường dùng keyless signing/OIDC hoặc key management phù hợp, tùy registry/policy.

### 10.3. Ký digest, không ký tag mutable

Tag có thể di chuyển; signature nên gắn với digest cụ thể. Deployment policy có thể yêu cầu:

- Digest pinned.
- Signature hợp lệ.
- Issuer/identity đúng.
- Repository/source đúng.
- SBOM/scan attestation có mặt.

---

## 11. Provenance

Provenance mô tả artifact được tạo như thế nào:

- Source repository/commit.
- Builder identity.
- Build invocation.
- Input/base image.
- Platform.
- Build timestamp.
- Output digest.

~~~text
Source commit + Dockerfile + base digest + builder
                         ↓
                    Image digest
                         +
                    Provenance
~~~

BuildKit/buildx có thể tạo provenance/attestation tùy version/config:

~~~bash
docker buildx build \
  --provenance=true \
  --sbom=true \
  -t registry.example.com/team/task-api:1.4.2 \
  --push .
~~~

Syntax/behavior phụ thuộc BuildKit/builder/registry. Kiểm tra artifact sau push và policy của registry trước khi dựa vào attestation.

Provenance hữu ích khi incident response: biết image production đến từ commit/pipeline nào và input nào.

---

## 12. Dependabot và dependency updates

Dependabot hoặc tool tương đương có thể tạo PR khi:

- Docker base image tag/digest update.
- Maven/Gradle dependency update.
- GitHub Action/toolchain update.
- Security advisory mới.

Workflow tốt:

1. Bot mở PR update.
2. CI build/test/scan image.
3. Review diff/base release note.
4. Merge nếu policy pass.
5. Push immutable artifact.
6. Deploy/promote theo pipeline.

Không auto-merge mọi base update nếu chưa có test; cũng không bỏ bot vì sợ noise. Điều chỉnh grouping/schedule/owner/review policy.

### 12.1. Pin và automation không mâu thuẫn

Pin digest khiến build ổn định. Dependabot/automation tạo PR thay digest khi có bản mới. Đây là mô hình “explicit update” thay vì tag tự đổi âm thầm.

### 12.2. Dependency lock

Java project nên pin/lock:

- Direct dependency version.
- Plugin version.
- Maven/Gradle wrapper.
- Repository/mirror.
- Base runtime digest.

Range version hoặc snapshot có thể làm build khác nhau theo thời điểm.

---

## 13. Rebuild định kỳ

Một image không có CVE hôm nay có thể có CVE mới ngày mai. Rebuild định kỳ giúp:

- Nhận base OS/runtime patch.
- Refresh vulnerability database findings.
- Phát hiện registry/dependency changes.
- Xác nhận pipeline còn hoạt động.
- Tạo artifact mới với digest mới.

### 13.1. Rebuild schedule

Ví dụ policy:

- On every source release.
- Khi base image security update.
- Hàng tuần hoặc theo risk tier.
- Trước deploy production.
- Khi scanner phát hiện critical/high trong runtime.

### 13.2. Rebuild không đổi source

Rebuild security patch vẫn cần:

- Ghi source commit không đổi.
- Ghi base digest mới.
- Chạy test/integration/scan.
- Push tag immutable mới hoặc cập nhật promotion reference theo policy.
- Không overwrite release tag nếu policy cấm.

### 13.3. Rollback

Giữ image digest cũ đã biết tốt, SBOM/signature/provenance và deployment metadata. Rollback bằng digest rõ hơn rollback bằng tag mutable.

---

## 14. Registry access control

Registry cần kiểm soát:

- Ai được pull.
- Ai được push.
- Ai được overwrite tag.
- Ai được delete manifest/tag.
- Ai được tạo repository.
- Ai xem scan/SBOM/signature.
- Retention/immutability policy.
- Audit log.

### 14.1. Principle of least privilege

- Developer pull, push dev namespace.
- CI push release namespace.
- Deploy identity pull/verify, không cần push/delete.
- Human admin có quyền quản trị tách biệt.
- Token scoped theo repository/action.

Không dùng một registry admin token trong mọi pipeline.

### 14.2. Immutable release tags

Registry nên bảo vệ tag release không bị overwrite:

~~~text
v1.4.2 → digest D1
~~~

Nếu tag bị overwrite sang D2, audit/rollback trở nên mơ hồ. Dùng commit SHA/digest và tag policy rõ.

### 14.3. Pull-only production

Runtime host chỉ cần pull image đã được promote/verify. Không cho host production build image từ source tùy tiện; build nên xảy ra trong controlled CI.

---

## 15. Image history và secret audit

Kiểm tra history:

~~~bash
docker history --no-trunc task-api:1.4.2
docker image inspect task-api:1.4.2
~~~

Tìm dấu hiệu:

- `ENV PASSWORD=...`.
- `RUN curl ... token ...`.
- `COPY .env`.
- Private key/certificate không cần.
- Source/test/debug artifact.
- Build arg chứa credential.

Secret có thể nằm trong layer cũ dù file hiện tại đã bị xóa. Nếu nghi ngờ:

1. Ngừng push/deploy image đó.
2. Rotate secret.
3. Rebuild từ base/context sạch.
4. Kiểm tra layer/history/cache.
5. Xóa/revoke artifact theo registry policy.
6. Kiểm tra repository/CI logs.

Không coi `docker image rm` local là remediation cho secret đã push.

---

## 16. CI security policy

### 16.1. Pipeline cơ bản

~~~text
Checkout pinned commit
      ↓
Build runtime image
      ↓
Unit/integration test
      ↓
Generate SBOM
      ↓
Scan image
      ↓
Check policy
      ↓
Sign/provenance
      ↓
Push immutable digest/tag
~~~

### 16.2. Policy fail

Ví dụ policy concept:

~~~text
Fail nếu:
- Có Critical chưa được chấp thuận.
- Có High vượt SLA.
- Image chạy root khi policy cấm.
- Image chứa secret pattern.
- Base image không nằm trong allowlist.
- Không có SBOM/provenance/signature.
- Tag release đã tồn tại và registry cấm overwrite.
~~~

Policy phải versioned, có exception workflow và không dựa vào exit code scanner mà không hiểu output.

### 16.3. Không block mù quáng

Nếu fail vì CVE false positive hoặc package build-only:

- Xác minh bằng SBOM/runtime image.
- Triage reachability/exposure.
- Tạo exception cụ thể có expiry.
- Theo dõi remediation.

“CI xanh” không có nghĩa security tuyệt đối; “CI đỏ” cần được điều tra chứ không suppress hàng loạt.

---

## 17. Lab 1: Pin base image và compare digest

Pull image và xem digest:

~~~bash
docker pull eclipse-temurin:21-jre
docker image inspect eclipse-temurin:21-jre --format '{{json .RepoDigests}}'
~~~

Tạo Dockerfile:

~~~dockerfile
FROM eclipse-temurin:21-jre@sha256:<real-digest>
WORKDIR /app
COPY app.jar app.jar
USER 10001
ENTRYPOINT ["java", "-jar", "app.jar"]
~~~

Thay `<real-digest>` bằng digest thật đã review. Build:

~~~bash
docker build -t task-api:pinned .
docker image inspect task-api:pinned
~~~

Ghi lại:

- Base digest.
- Final image ID/digest.
- Source commit.
- Build command.
- Platform.

---

## 18. Lab 2: Scan runtime image

~~~bash
docker scout quickview task-api:pinned
docker scout cves task-api:pinned
~~~

Nếu không có Scout, dùng scanner được phê duyệt. Triage ít nhất một finding:

1. Package/version.
2. Severity.
3. Có trong final runtime image không?
4. Có bản fix không?
5. Có exposed/reachable không?
6. Remediation hoặc accepted risk.

Không push image vào registry thật chỉ để thử nếu chưa có authorization.

---

## 19. Lab 3: SBOM và image history

~~~bash
docker history --no-trunc task-api:pinned
docker image inspect task-api:pinned
syft task-api:pinned -o spdx-json > task-api.sbom.spdx.json
~~~

Kiểm tra SBOM không chứa build-only tool nếu runtime image không cần. Tìm secret pattern trong history/output bằng scanner phù hợp; không in secret thật.

Lưu SBOM với digest, không đặt tên file chỉ là `latest.sbom.json`.

---

## 20. Lab 4: Reproducible rebuild

Build hai lần từ cùng commit/input:

~~~bash
docker build --pull=false -t task-api:build-a .
docker build --pull=false -t task-api:build-b .
docker image inspect task-api:build-a --format '{{.Id}}'
docker image inspect task-api:build-b --format '{{.Id}}'
~~~

Nếu khác, điều tra:

- Timestamp/JAR metadata.
- Base/digest.
- Dependency download.
- Build args/environment.
- Architecture/platform.
- Generated file/random ID.
- Dockerfile/build tool version.

Không kết luận chỉ từ local image ID nếu tag/config khác. So sánh layer/config/digest bằng tool phù hợp.

Thử thay đổi có chủ đích:

~~~bash
docker build --build-arg VERSION=1.4.2 -t task-api:versioned .
docker build --build-arg VERSION=1.4.3 -t task-api:versioned-new .
~~~

Quan sát metadata thay đổi và ghi lại input làm image khác.

---

## 21. Lab 5: Signature/provenance concept

Trong registry/dev environment được cấp quyền, flow concept:

~~~bash
# push image trước để có digest
cosign sign registry.example.com/team/task-api@sha256:<digest>
cosign verify registry.example.com/team/task-api@sha256:<digest>
~~~

Kiểm tra identity/issuer theo policy, không chỉ xem command exit 0. Không lưu private key trong repo/Compose/.env.

Nếu dùng BuildKit attestation:

~~~bash
docker buildx build \
  --sbom=true \
  --provenance=true \
  -t registry.example.com/team/task-api:1.4.2 \
  --push .
~~~

Tính năng/registry support có thể khác; verify artifact sau push.

---

## 22. Lab 6: CI policy giả lập

Tạo checklist script/policy ở mức concept:

~~~text
1. Image có User non-root?
2. Base image có allowlist không?
3. Có secret pattern trong history không?
4. Runtime image có SBOM không?
5. Critical vulnerability chưa được exception?
6. Tag release có immutable không?
7. Digest có được lưu trong release metadata không?
8. Signature/provenance có hợp lệ không?
~~~

CI cần fail khi policy không đạt, nhưng output phải chỉ rõ lý do và remediation. Không log credential hoặc toàn bộ secret environment.

---

## 23. Lỗi thường gặp

### Pin tag nhưng tưởng đã immutable

`FROM image:1.2` vẫn có thể đổi nếu registry cho phép. Pin digest hoặc dùng registry policy immutable tag.

### Pin digest quá lâu

Image reproducible nhưng có thể giữ CVE cũ. Kết hợp Dependabot/rebuild schedule/scan.

### Chỉ scan build image

Build image có thể chứa tool không nằm runtime, còn runtime có vulnerability riêng. Scan final image và artifact thật sẽ deploy.

### Chỉ nhìn số lượng CVE

Một image nhiều low CVE không nhất thiết rủi ro hơn image có một critical reachable. Triage theo package/exposure/fix/policy.

### SBOM không khớp digest

SBOM tạo cho tag mutable rồi tag đổi. Gắn SBOM với immutable digest và verify artifact association.

### Ký tag thay vì digest

Tag bị di chuyển làm verification/audit mơ hồ. Ký và deploy digest.

### Provenance không đáng tin vì builder bị compromise

Provenance mô tả pipeline nhưng không tự làm builder an toàn. Bảo vệ runner, credentials, source/registry access và review workflow.

### Rebuild định kỳ phá reproducibility

Rebuild với floating dependency/base tạo output khác. Pin input, ghi thay đổi và review digest mới.

### Dependabot tạo quá nhiều PR

Group update theo ecosystem/base, đặt schedule/labels/review owner và giữ CI tự động. Đừng tắt security update toàn bộ.

### Registry token có quyền quá rộng

Tách push/pull/delete/admin identity. Rotate token và audit usage.

### Secret chỉ bị xóa ở layer cuối

Layer cũ vẫn tồn tại trong image history/cache/registry. Rotate và rebuild sạch.

---

## 24. Bài tập tự làm

### Bài 1: Base image policy

Chọn một base image Java từ trusted source, ghi vendor/tag/digest/platform/lifecycle và lý do lựa chọn.

### Bài 2: Digest lock/update

Pin base image digest, tạo PR giả lập để update digest mới, build/scan/test và ghi lại diff.

### Bài 3: CVE triage

Scan runtime image, chọn một finding, phân tích severity/reachability/exposure/fix và tạo remediation hoặc exception có expiry.

### Bài 4: SBOM

Tạo SBOM cho final image, gắn với digest và đối chiếu package runtime với Dockerfile layer/history.

### Bài 5: Reproducibility

Build hai lần từ cùng commit; nếu digest khác, tìm input không deterministic và đề xuất kiểm soát.

### Bài 6: Secret audit

Cố ý tạo Dockerfile không an toàn trong branch lab, chạy `history`/scanner để phát hiện, rồi rebuild từ context sạch và rotate secret giả lập.

### Bài 7: Registry access

Vẽ ma trận quyền developer/CI/deploy/admin: pull, push, tag overwrite, delete, sign, verify. Giảm quyền theo least privilege.

### Bài 8: CI gate

Viết policy checklist fail khi có critical chưa exception, root user, missing SBOM hoặc missing digest metadata.

---

## 25. Câu hỏi kiểm tra và đáp án gợi ý

1. Vì sao trusted base image vẫn cần scan?
2. Tag khác digest như thế nào?
3. Pin digest có nhược điểm gì?
4. Những input nào ảnh hưởng reproducible build?
5. Vì sao runtime image phải scan riêng build image?
6. SBOM trả lời câu hỏi gì?
7. Image signing chứng minh điều gì và không chứng minh điều gì?
8. Provenance khác SBOM thế nào?
9. Vì sao cần rebuild định kỳ dù image digest không đổi?
10. CVE triage cần xem ngoài severity những gì?
11. Vì sao secret xóa ở layer cuối vẫn có thể lộ?
12. Registry deploy identity nên có quyền gì?

Đáp án ngắn: trusted source không loại CVE; tag có thể di chuyển còn digest định danh nội dung; digest cần automation update; base/dependency/tool/build metadata/platform; final runtime có footprint khác; SBOM là inventory; signing xác minh identity/integrity chứ không đảm bảo code an toàn; provenance mô tả nguồn/builder/invocation; CVE mới xuất hiện sau build; xem reachability/exposure/fix/policy; layer cũ giữ secret; deploy thường cần pull/verify, không cần push/delete.

---

## 26. Tiêu chí hoàn thành tuần 14

- [ ] Chọn được base image trusted và ghi rõ policy/lifecycle.
- [ ] Phân biệt tag mutable, version tag, image ID và digest.
- [ ] Pin digest khi cần reproducibility.
- [ ] Liệt kê được nguồn làm build không deterministic.
- [ ] Scan image trước push bằng tool phù hợp.
- [ ] Triage được CVE theo severity/reachability/exposure/fix.
- [ ] Tạo và lưu SBOM gắn với image digest.
- [ ] Hiểu image signing và verify theo identity/policy.
- [ ] Hiểu provenance và trace source → builder → image.
- [ ] Thiết lập Dependabot/cơ chế update tương đương.
- [ ] Có lịch rebuild định kỳ và rollback bằng digest.
- [ ] Thiết kế registry access control least privilege.
- [ ] Kiểm tra image history không có secret.
- [ ] Tạo CI policy fail cho vulnerability/security requirement theo policy.

Kiểm tra cuối buổi:

~~~bash
docker image inspect <image>
docker history --no-trunc <image>
docker scout quickview <image>
docker system df -v
~~~

Mục tiêu của tuần này là biết image đến từ đâu, gồm những gì, có thể tái tạo/kiểm chứng thế nào và policy nào ngăn artifact không đạt đi tiếp vào registry/deployment.

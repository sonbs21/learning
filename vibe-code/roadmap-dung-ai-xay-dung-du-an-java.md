# Roadmap dùng AI để xây dựng và duy trì một dự án Java

> Đây là roadmap về **AI-assisted software engineering**, không phải roadmap học Machine Learning. Mục tiêu là dùng AI như một engineering partner để phân tích codebase, lập kế hoạch, triển khai feature, viết test, review chất lượng, tối ưu performance và giữ kiến thức dự án nhất quán qua nhiều conversation.

## 1. Điều quan trọng nhất: Conversation không phải nơi lưu kiến thức dự án

AI có thể mất context khi:

- Conversation quá dài.
- Bạn mở conversation mới.
- Một feature thay đổi nhưng tài liệu không được cập nhật.
- AI chỉ nhìn một vài file rồi suy đoán phần còn lại.
- Prompt chứa nhiều yêu cầu nhưng không có acceptance criteria.

Vì vậy, kiến thức quan trọng phải nằm trong **repository**, không nằm trong trí nhớ tạm thời của conversation.

### Nguồn sự thật theo thứ tự ưu tiên

1. Code và test đang chạy.
2. API/schema/config contract đã version.
3. ADR và tài liệu kiến trúc đã được review.
4. Feature spec và acceptance criteria.
5. Handoff note của conversation trước.
6. Tin nhắn trong conversation.

Nếu conversation mâu thuẫn với code hoặc test, phải kiểm tra repository trước khi tiếp tục.

## 2. Mục tiêu đầu ra

Sau roadmap, bạn có thể:

- Thiết lập một project Java/Spring Boot để AI đọc và làm việc đúng context.
- Chia feature lớn thành task nhỏ có acceptance criteria rõ.
- Cho AI khám phá codebase trước khi sửa.
- Sử dụng AI để viết plan, code, test, review và tài liệu theo một quy trình lặp lại.
- Bảo đảm feature mới không phá behavior cũ.
- Dùng test, static analysis, performance benchmark và security scan làm quality gate.
- Theo dõi performance bằng profiling và metrics thay vì phỏng đoán.
- Lưu architecture, domain glossary, coding rules và decision log trong repository.
- Bắt đầu conversation mới mà AI vẫn hiểu project nhờ context pack.
- Tạo handoff note để tiếp tục công việc không bị mất trạng thái.
- Giới hạn quyền của AI, review diff và ngăn thay đổi ngoài scope.

## 3. Kiến trúc lưu context cho dự án

Tạo các file sau ngay từ đầu:

~~~text
project-root/
├── AGENTS.md
├── README.md
├── CONTRIBUTING.md
├── docs/
│   ├── ai/
│   │   ├── project-brief.md
│   │   ├── architecture.md
│   │   ├── domain-glossary.md
│   │   ├── coding-rules.md
│   │   ├── test-strategy.md
│   │   ├── performance-baseline.md
│   │   ├── security-baseline.md
│   │   ├── context-index.md
│   │   ├── handoffs/
│   │   └── features/
│   ├── architecture/
│   │   └── adr/
│   ├── api/
│   ├── runbooks/
│   └── operations/
├── src/
├── tests/
└── scripts/
    ├── context-pack.sh
    └── verify-project.sh
~~~

## 4. Nội dung các file context quan trọng

### AGENTS.md

Đây là file AI phải đọc trước khi làm việc. Nội dung nên ngắn, rõ và có thể kiểm chứng:

~~~markdown
# Project instructions

## Project
- Java 21, Spring Boot 3.x.
- Build bằng Maven.
- Package theo feature.

## Commands
- Build: ./mvnw clean verify
- Unit test: ./mvnw test
- Static analysis: ./mvnw verify

## Rules
- Đọc architecture.md và coding-rules.md trước khi sửa.
- Không thay đổi public API nếu task không yêu cầu.
- Viết test trước hoặc đồng thời với behavior mới.
- Không tắt test để làm build xanh.
- Không thêm dependency nếu chưa nêu lý do.
- Không log secret, token hoặc dữ liệu nhạy cảm.
- Sau khi sửa phải báo file đã thay đổi, test đã chạy và rủi ro còn lại.

## Definition of done
- Compile thành công.
- Test liên quan pass.
- Regression test pass.
- API/docs/ADR được cập nhật nếu contract thay đổi.
- Diff không có thay đổi ngoài scope.
~~~

### project-brief.md

Ghi thông tin ổn định:

- Product giải quyết vấn đề gì.
- Người dùng và actor chính.
- Domain boundary.
- Các module chính.
- Dependency bên ngoài.
- Non-functional requirements.
- RTO/RPO, latency, throughput và security requirement.
- Những điều tuyệt đối không được thay đổi.

### architecture.md

Ghi:

- Component diagram.
- Request flow.
- Module ownership.
- Integration boundary.
- Authentication flow.
- Deployment topology.
- Failure boundary.
- Những quyết định chưa giải quyết.

### domain-glossary.md

Mỗi thuật ngữ cần có:

| Term | Meaning | Không được nhầm với | Owner |
|---|---|---|---|
| Order | Đơn hàng đã được tạo | Cart | Order module |
| Payment | Trạng thái thanh toán | Invoice | Payment module |

### coding-rules.md

Ghi các rule mà AI không nên tự suy đoán:

- Naming.
- Package direction.
- Exception mapping.
- Logging fields.
- API response shape.
- Null/optional policy.
- Date/time policy.
- Validation policy.
- Test naming.
- Dependency policy.

### test-strategy.md

Ghi:

- Unit test nằm ở đâu.
- Integration test nào bắt buộc.
- Critical flow cần E2E.
- Test data factory.
- Coverage threshold.
- Cách chạy local và CI.
- Các regression test quan trọng.

### ADR

Mỗi quyết định quan trọng tạo một file:

~~~text
docs/architecture/adr/ADR-0001-use-rest-for-public-api.md
~~~

Template:

~~~markdown
# ADR-0001: Chọn REST cho public API

## Context
...

## Decision
...

## Alternatives
...

## Consequences
...

## Status
Accepted
~~~

## 5. Roadmap 16 tuần

## Tuần 1 — Tư duy AI-assisted engineering

### Học

- AI là trợ lý tạo/biến đổi thông tin, không phải source of truth.
- Context window khác memory dài hạn.
- AI có thể hallucinate API, package, behavior và file dependency.
- Planning quality quan trọng hơn tốc độ sinh code.
- Review behavior quan trọng hơn review syntax.
- Explicit boundary và stable contract giúp AI làm việc tốt hơn.
- Deterministic tests là cách kiểm soát code do AI tạo.
- Không giao cho AI mục tiêu mơ hồ kiểu “làm cho tốt hơn”.

### Thực hành

1. Chọn một project Java/Spring Boot nhỏ.
2. Viết project brief và architecture overview.
3. Tạo AGENTS.md với command, rule và definition of done.
4. Yêu cầu AI chỉ đọc và tóm tắt, chưa được sửa file.
5. So sánh tóm tắt của AI với code thực tế.

### Hoàn thành khi

- AI biết cách build/test project.
- AI biết file nào là source of truth.
- Bạn phân biệt được fact, assumption và proposal trong câu trả lời AI.

## Tuần 2 — Chuẩn hóa codebase để AI đọc được

### Học

- Feature-based package.
- Naming nhất quán.
- Module boundary và dependency direction.
- DTO/domain/config tách rõ.
- Một class/method có trách nhiệm dễ mô tả.
- Public contract và internal implementation.
- README cho từng module.
- Code comment cho lý do, không comment lại syntax.

### Thực hành

1. Liệt kê module và ownership.
2. Tạo context-index.md trỏ đến file quan trọng.
3. Viết README cho từng module.
4. Xóa dead code và đổi tên mơ hồ.
5. Tạo sơ đồ request flow.

### Hoàn thành khi

- Người mới có thể tìm entry point của một feature.
- AI có thể tìm module liên quan mà không đọc toàn repository.
- Không có package cycle khó giải thích.

## Tuần 3 — Khám phá codebase trước khi sửa

### Học

- Search-first workflow.
- Call graph, dependency graph và data flow.
- Entry point, boundary và side effect.
- Đọc test để hiểu behavior thật.
- Tìm configuration, migration, API contract và runbook.
- Phân biệt code path production với code path test.

### Prompt mẫu

~~~text
Hãy chỉ khám phá, chưa sửa code.

Mục tiêu: hiểu flow tạo order.

Hãy trả về:
1. Entry point.
2. Các class/method đi qua.
3. External dependency.
4. Validation và authorization.
5. Test hiện có.
6. Rủi ro nếu sửa flow này.
7. File cần đọc thêm.

Mỗi nhận định phải kèm path và symbol liên quan.
Nếu chưa chắc, ghi rõ là assumption.
~~~

### Hoàn thành khi

- AI trích dẫn được file/symbol thay vì trả lời chung chung.
- Bạn có dependency map trước khi bắt đầu feature.
- Không sửa file chỉ vì AI đoán cấu trúc.

## Tuần 4 — Lập kế hoạch feature bằng AI

### Học

- Feature brief.
- Acceptance criteria.
- Non-goals.
- Impact analysis.
- Compatibility và rollout plan.
- Task decomposition.
- Risk/assumption log.
- Definition of done.

### Feature brief template

~~~markdown
# Feature: ...

## Problem
...

## User story
...

## In scope
- ...

## Out of scope
- ...

## Acceptance criteria
- Given ...
- When ...
- Then ...

## API/domain impact
...

## Security impact
...

## Performance impact
...

## Observability
...

## Rollout/rollback
...
~~~

### Hoàn thành khi

- Mỗi feature có acceptance criteria kiểm thử được.
- AI đưa ra plan trước khi code.
- Bạn duyệt plan và scope trước khi cho AI chỉnh sửa.

## Tuần 5 — Workflow triển khai feature từng bước

### Quy trình chuẩn

1. Cập nhật feature brief.
2. Tạo branch/worktree riêng.
3. Nạp context pack tối thiểu.
4. Yêu cầu AI phân tích impact.
5. Duyệt implementation plan.
6. Viết hoặc cập nhật test.
7. Cho AI triển khai một slice nhỏ.
8. Chạy test ngay sau slice đó.
9. Review diff.
10. Chạy quality gate.
11. Cập nhật docs/ADR/runbook.
12. Tạo handoff note.

### Rule quan trọng

- Không giao “build toàn bộ feature” trong một prompt.
- Mỗi lượt chỉ thay đổi một bounded slice.
- AI phải nêu file dự kiến sửa trước.
- Không chấp nhận thay đổi ngoài scope mà không giải thích.
- Không bỏ qua test vì implementation “đơn giản”.

### Hoàn thành khi

- Có thể dừng giữa chừng và tiếp tục mà không mất trạng thái.
- Mỗi commit có scope nhỏ và message rõ.
- Có test chứng minh behavior mới và behavior cũ.

## Tuần 6 — Dùng AI viết code Java/Spring Boot có kiểm soát

### Học

- Cung cấp interface, input/output và invariant rõ.
- Yêu cầu AI viết một class/method nhỏ thay vì cả module.
- Yêu cầu giữ nguyên public contract.
- Yêu cầu liệt kê assumption và edge case.
- Dùng existing pattern trước khi tạo abstraction mới.
- Tránh generated code có reflection/magic không cần thiết.
- Kiểm tra lifecycle, transaction boundary và thread safety.
- Không thêm dependency nếu chưa có lý do.

### Prompt mẫu

~~~text
Implement task theo spec dưới đây.

Scope:
- Chỉ sửa các file: ...
- Không đổi public API: ...
- Không thêm dependency.

Behavior:
- ...

Constraints:
- Java ...
- Spring Boot ...
- Phải giữ backward compatibility.

Trước khi sửa:
1. Nêu plan ngắn.
2. Nêu file sẽ sửa.
3. Nêu edge cases.

Sau khi sửa:
1. Tóm tắt behavior.
2. Liệt kê test đã thêm/chạy.
3. Nêu rủi ro còn lại.
~~~

### Hoàn thành khi

- Code AI tạo ra theo đúng style project.
- Không có hidden behavior ngoài spec.
- Mỗi thay đổi đều có test hoặc lý do không cần test.

## Tuần 7 — Test-first và regression safety

### Học

- Unit test, integration test, contract test và E2E.
- Test behavior thay vì test implementation detail.
- Boundary test và negative test.
- Regression test cho bug đã sửa.
- Property-based test ở mức phù hợp.
- Test fixture và test data builder.
- Mutation testing ở mức nhận biết.
- Coverage không thay thế chất lượng test.

### Prompt mẫu

~~~text
Hãy viết test trước cho feature này.

Bao gồm:
- Happy path.
- Validation failure.
- Authorization failure.
- Dependency timeout/error.
- Duplicate request.
- Boundary/empty input.

Mỗi test ghi rõ behavior được bảo vệ.
Không sửa production code cho đến khi tôi duyệt test cases.
~~~

### Quality gate

- Build/compile pass.
- Unit test pass.
- Integration test liên quan pass.
- Regression test pass.
- Không giảm coverage của vùng bị chạm.
- Test failure được điều tra, không tắt hoặc skip tùy tiện.

### Hoàn thành khi

- AI không chỉ sinh test happy path.
- Bạn biết test nào bảo vệ contract nào.
- Feature mới không làm hỏng behavior cũ.

## Tuần 8 — API contract và backward compatibility

### Học

- OpenAPI và API-first design.
- Request/response compatibility.
- Additive change và breaking change.
- Error code stability.
- Versioning và deprecation.
- Contract test giữa client và server.
- Webhook contract và idempotency.
- Feature flag và gradual rollout.

### Thực hành

1. Cho AI đọc API contract hiện tại.
2. Yêu cầu phân loại thay đổi compatible/breaking.
3. Thêm field optional với test backward compatibility.
4. Viết deprecation note.
5. Tạo contract test cho endpoint quan trọng.

### Hoàn thành khi

- AI không tự đổi JSON field/name.
- Breaking change có migration plan.
- API docs cập nhật cùng code.

## Tuần 9 — Performance engineering với AI

### Học

- Performance requirement và latency budget.
- Throughput, concurrency, p50/p95/p99.
- CPU-bound và I/O-bound.
- Allocation, garbage collection và memory pressure.
- Thread pool, connection pool và bounded concurrency.
- Payload size, serialization và compression.
- N+1 ở tầng application nói chung.
- Caching HTTP/CDN ở mức kiến trúc.
- Load test, benchmark và profiling.
- Java Flight Recorder, async-profiler, Micrometer và Actuator.

### Quy trình tối ưu

1. Đo baseline trước khi sửa.
2. Xác định bottleneck bằng profile/metric.
3. Đặt hypothesis.
4. Cho AI đề xuất tối đa ba phương án.
5. Chọn phương án ít rủi ro nhất.
6. Benchmark trước/sau trên workload giống nhau.
7. Kiểm tra correctness và memory.
8. Cập nhật performance-baseline.md.

### Prompt mẫu

~~~text
Phân tích vấn đề performance dựa trên:
- p50/p95/p99 hiện tại: ...
- Throughput: ...
- CPU/memory/GC: ...
- Profile hoặc trace: ...

Không sửa code ngay.
Hãy:
1. Nêu bottleneck có bằng chứng.
2. Phân biệt fact và hypothesis.
3. Đề xuất ba phương án.
4. Ước lượng rủi ro/regression.
5. Đề xuất benchmark để xác nhận.
~~~

### Hoàn thành khi

- Không chấp nhận “tối ưu” không có số đo.
- Có performance regression test cho critical path.
- AI không đổi algorithm hoặc concurrency chỉ dựa vào cảm giác.

## Tuần 10 — Security review và safe AI usage

### Học

- Threat modeling.
- Authentication và authorization.
- Input validation.
- Secret/token/PII handling.
- Injection, SSRF, XSS, CSRF và path traversal.
- Dependency vulnerability.
- Secure logging.
- Data access boundary.
- AI-generated code có thể thêm lỗ hổng mà không báo.
- Prompt và source code có thể chứa thông tin bí mật.

### Prompt review mẫu

~~~text
Review diff này như security reviewer.

Kiểm tra:
1. Authentication/authorization bypass.
2. Input validation và injection.
3. Secret/PII leakage.
4. SSRF/file access.
5. Unsafe deserialization/reflection.
6. Dependency/config risk.
7. Error/log information leak.

Chỉ báo cáo issue có path, severity, exploit scenario và fix đề xuất.
Không tự sửa cho đến khi tôi xác nhận.
~~~

### Hoàn thành khi

- Security review là bước riêng, không gộp vào “code nhìn ổn”.
- Không gửi secret hoặc dữ liệu khách hàng vào AI không được phép.
- Có security checklist trong Definition of Done.

## Tuần 11 — Refactoring và code review với AI

### Học

- Refactor behavior-preserving.
- Code smell: duplication, deep nesting, god class, hidden side effect.
- Cohesion/coupling.
- API boundary và dependency direction.
- Chia refactor thành bước nhỏ.
- Review diff thay vì review lời giải thích.
- Không refactor và thêm feature trong cùng một diff lớn.

### Workflow

1. Chụp baseline test/metric.
2. Yêu cầu AI chỉ xác định smell.
3. Chọn một smell có impact rõ.
4. Refactor nhỏ.
5. Chạy test và compare diff.
6. Đo lại performance nếu code path quan trọng.
7. Cập nhật ADR nếu boundary thay đổi.

### Hoàn thành khi

- Refactor không đổi behavior ngoài ý muốn.
- Diff có thể review trong một lần.
- AI nêu được lý do và trade-off của refactor.

## Tuần 12 — CI/CD và quality gates

### Học

- Build, test, static analysis và artifact.
- Branch protection và pull request check.
- Dependency update và vulnerability scan.
- API compatibility check.
- Coverage trend và flaky test.
- Environment promotion: development, staging, production.
- Feature flag, canary, rollback.
- Changelog và release note.

### Pipeline tối thiểu

~~~text
Pull request
  → Format/lint
  → Compile
  → Unit test
  → Integration/contract test
  → Static/security scan
  → Performance smoke test
  → Review
  → Artifact

Staging
  → Smoke test
  → Canary
  → Monitor
  → Promote or rollback
~~~

### Hoàn thành khi

- AI không thể tự bypass quality gate.
- Build xanh không đồng nghĩa feature đã đúng; vẫn cần review behavior.
- Có rollback được kiểm thử.

## Tuần 13 — Quản lý context giữa nhiều conversation

### Vấn đề

Conversation cũ không phải memory đáng tin cậy. Khi bắt đầu conversation mới, AI cần một context pack có cấu trúc để hiểu:

- Project đang làm gì.
- Kiến trúc hiện tại.
- Feature nào đã hoàn thành.
- Quyết định nào đã được chốt.
- Test/quality baseline hiện tại.
- Task hiện tại và file liên quan.
- Việc gì đang dang dở.

### Context pack tối thiểu

~~~text
1. AGENTS.md
2. docs/ai/project-brief.md
3. docs/ai/architecture.md
4. docs/ai/domain-glossary.md
5. docs/ai/coding-rules.md
6. docs/ai/test-strategy.md
7. Feature brief hiện tại
8. ADR liên quan
9. Handoff note mới nhất
10. File/code path liên quan
~~~

Không gửi toàn bộ repository trong mỗi conversation. Gửi core context nhỏ, sau đó bổ sung task context theo nhu cầu.

### Cách tạo context pack

~~~text
Project identity
  → Architecture summary
  → Relevant module
  → Relevant tests
  → Relevant ADR
  → Current task
  → Constraints
  → Definition of done
~~~

### Prompt mở conversation mới

~~~text
Đây là project context pack. Hãy đọc theo thứ tự:
1. AGENTS.md
2. docs/ai/project-brief.md
3. docs/ai/architecture.md
4. docs/ai/domain-glossary.md
5. docs/ai/coding-rules.md
6. docs/ai/test-strategy.md
7. docs/ai/features/<current-feature>.md
8. docs/ai/handoffs/<latest-handoff>.md

Chưa được sửa code.
Hãy trả về:
- Những facts đã xác nhận.
- Những điểm chưa rõ.
- Các file cần đọc thêm.
- Tóm tắt task hiện tại.
- Rủi ro nếu triển khai.
~~~

### Hoàn thành khi

- Mở conversation mới vẫn khôi phục được project state.
- Context pack đủ nhỏ để AI xử lý chính xác.
- Mỗi task có nguồn context rõ ràng.

## Tuần 14 — Handoff và project memory

### Handoff note template

~~~markdown
# Handoff: feature/order-cancellation

## Date
2026-10-10

## Goal
...

## Current status
- Done:
- In progress:
- Blocked:

## Files changed
- src/...
- src/test/...

## Decisions
- ...

## Tests
- Passed:
- Failed:
- Not run:

## Known risks
- ...

## Next exact step
...

## Context to read next
- docs/ai/...
- docs/architecture/adr/...
~~~

### Handoff rules

- Tạo handoff sau mỗi session có thay đổi.
- Ghi trạng thái bằng fact, không ghi “gần xong”.
- Ghi test đã chạy và test chưa chạy.
- Ghi blocker và next exact step.
- Không xóa handoff cũ; archive theo feature.
- Nếu decision đổi, tạo ADR hoặc cập nhật decision log.

### Hoàn thành khi

- Người khác có thể tiếp tục task chỉ bằng handoff + context pack.
- Không cần hỏi lại toàn bộ lịch sử conversation.
- Handoff không chứa secret hoặc dữ liệu khách hàng.

## Tuần 15 — AI agent, automation và quyền hạn

### Học

- Read-only exploration agent.
- Planning agent.
- Implementation agent.
- Test/review agent.
- Agent handoff qua artifact, không qua trí nhớ ngầm.
- Tool permission và least privilege.
- File scope, command allowlist và approval.
- Human-in-the-loop cho migration, deploy, delete và permission change.
- Deterministic automation và audit trail.

### Phân chia role

| Role | Được làm | Không được tự làm |
|---|---|---|
| Explorer | Đọc/search/summarize | Sửa code |
| Planner | Tạo plan/task/impact analysis | Chạy mutation nguy hiểm |
| Implementer | Sửa file trong scope | Đổi kiến trúc ngoài task |
| Tester | Chạy test, thêm test | Bỏ qua failure |
| Reviewer | Phân tích diff/risk | Tự merge/deploy |

### Hoàn thành khi

- Agent boundary rõ.
- Mọi mutation có diff và approval.
- Tool output được kiểm tra trước khi đưa vào code.

## Tuần 16 — Capstone và operating model

### Capstone

Thêm feature “Order cancellation with refund” vào project Java/Spring Boot đang có.

### Acceptance criteria

- User chỉ hủy order của chính mình.
- Chỉ order ở trạng thái hợp lệ mới được hủy.
- Refund call có timeout và idempotency key.
- Error response giữ nguyên API contract.
- Audit log ghi actor, order, reason và requestId.
- Feature flag cho rollout.
- Có test happy path, unauthorized, invalid state, timeout, duplicate và provider error.
- Có metrics latency, success/error và refund failure.
- Có runbook rollback.

### Quy trình làm capstone

1. Cập nhật feature brief.
2. Đọc context pack và flow order hiện tại.
3. Yêu cầu AI tạo impact analysis.
4. Yêu cầu AI đề xuất test cases.
5. Duyệt plan.
6. Viết test trước.
7. Implement domain behavior.
8. Implement external adapter.
9. Thêm observability.
10. Chạy quality gates.
11. Security review.
12. Performance smoke test.
13. Cập nhật API docs/ADR.
14. Tạo handoff/release note.

### Definition of Done

- [ ] Plan được review trước khi code.
- [ ] Diff không có thay đổi ngoài scope.
- [ ] Unit/integration/contract test pass.
- [ ] Regression suite pass.
- [ ] Security review không còn issue nghiêm trọng.
- [ ] Performance baseline không xấu đi ngoài ngưỡng.
- [ ] Logs/metrics/traces được cập nhật.
- [ ] API/docs/ADR được cập nhật.
- [ ] Rollback đã được xác định.
- [ ] Handoff note đã được tạo.

## 7. Quy trình chuẩn cho mỗi feature

~~~text
Spec
  → Explore
  → Impact analysis
  → Plan
  → Test design
  → Implement small slice
  → Run tests
  → Review diff
  → Security/performance check
  → Update docs/ADR
  → Commit
  → Handoff
~~~

## 8. Prompt library

### Explore

~~~text
Chỉ đọc và phân tích, chưa sửa file.
Hãy xác định entry point, call path, side effect, test hiện có,
contract liên quan và rủi ro. Mỗi nhận định phải trỏ tới path/symbol.
~~~

### Plan

~~~text
Dựa trên feature brief và context pack, hãy tạo implementation plan.
Nêu file sẽ sửa, file sẽ thêm, behavior, test cases, security impact,
performance impact, rollout và rollback. Không code.
~~~

### Implement

~~~text
Implement đúng plan đã duyệt.
Chỉ sửa các file trong scope.
Không đổi public contract hoặc dependency nếu chưa nêu.
Sau khi sửa hãy chạy test liên quan và báo kết quả thật.
~~~

### Review

~~~text
Review diff này như senior engineer.
Tập trung vào behavior regression, security, data integrity,
failure handling, concurrency, performance và rollout safety.
Mỗi issue cần severity, file/symbol, scenario và fix đề xuất.
~~~

### Handoff

~~~text
Tạo handoff note cho session này.
Ghi goal, done, in-progress, blocked, files changed,
decisions, tests passed/failed/not run, risks và next exact step.
~~~

## 9. Quality gate bắt buộc

### Code

- [ ] Compile/build pass.
- [ ] Formatter/linter/static analysis pass.
- [ ] Không có dependency mới không giải thích.
- [ ] Không có TODO quan trọng bị giấu.

### Behavior

- [ ] Acceptance criteria pass.
- [ ] Happy path pass.
- [ ] Negative path pass.
- [ ] Regression suite pass.
- [ ] API compatibility pass.

### Security

- [ ] Auth/authz pass.
- [ ] Input validation pass.
- [ ] Không leak secret/PII.
- [ ] Dependency scan pass.
- [ ] Logging review pass.

### Performance

- [ ] Baseline trước/sau đã đo.
- [ ] p95/p99 trong ngưỡng.
- [ ] Không tăng allocation/thread/network vô lý.
- [ ] Timeout và resource limit tồn tại.

### Operations

- [ ] Log/metric/trace đủ để debug.
- [ ] Health/readiness pass.
- [ ] Rollback plan tồn tại.
- [ ] Runbook và handoff được cập nhật.

## 10. Cách ngăn AI quên thông tin cũ

### Không làm

- Không dựa vào “conversation trước AI đã biết rồi”.
- Không copy toàn bộ lịch sử chat khổng lồ vào prompt.
- Không để quyết định kiến trúc chỉ tồn tại trong chat.
- Không sửa code mà không cập nhật test/docs liên quan.
- Không để nhiều tài liệu mâu thuẫn mà không ghi rõ tài liệu nào mới nhất.

### Nên làm

1. Ghi rule ổn định vào AGENTS.md.
2. Ghi kiến trúc vào architecture.md.
3. Ghi thuật ngữ vào domain-glossary.md.
4. Ghi quyết định vào ADR.
5. Ghi feature state vào feature brief.
6. Ghi session state vào handoff note.
7. Ghi quality/performance baseline bằng số đo.
8. Mở conversation mới bằng context pack.
9. Yêu cầu AI xác nhận facts trước khi code.
10. Review repository state sau mỗi feature.

## 11. Anti-pattern khi dùng AI làm phần mềm

- Dùng một prompt rất dài để yêu cầu AI xây cả hệ thống.
- Để AI sửa nhiều module mà không có plan.
- Tin code compile được là code đúng.
- Không yêu cầu AI đọc test hiện có.
- Chấp nhận test do AI tạo nhưng không kiểm tra assertion có ý nghĩa.
- Cho AI tự thêm dependency hoặc đổi framework.
- Cho AI sửa config/security/deploy mà không approval.
- Không ghi source của quyết định.
- Không đo performance trước và sau.
- Dùng conversation làm knowledge base duy nhất.
- Merge diff lớn không review từng file.
- Xóa test khó vì “test đang sai”.

## 12. Checklist setup lần đầu

- [ ] Tạo AGENTS.md.
- [ ] Tạo project-brief.md.
- [ ] Tạo architecture.md.
- [ ] Tạo domain-glossary.md.
- [ ] Tạo coding-rules.md.
- [ ] Tạo test-strategy.md.
- [ ] Tạo performance-baseline.md.
- [ ] Tạo security-baseline.md.
- [ ] Tạo context-index.md.
- [ ] Tạo template feature brief.
- [ ] Tạo template handoff.
- [ ] Tạo template ADR.
- [ ] Viết command build/test/check.
- [ ] Thiết lập quality gate trong CI.

## 13. Lộ trình rút gọn 4 tuần

### Tuần 1

- Tạo AGENTS.md, architecture docs, glossary và feature brief.
- Học workflow explore → plan → implement → test.

### Tuần 2

- Dùng AI cho một feature nhỏ.
- Viết test, review diff, security check và cập nhật handoff.

### Tuần 3

- Thêm performance baseline, observability, CI quality gate và rollback.

### Tuần 4

- Làm capstone feature qua nhiều conversation.
- Mở conversation mới bằng context pack.
- Đánh giá chất lượng code và khả năng khôi phục context.

## 14. Tiêu chí thành thạo

- [ ] Có thể bắt đầu conversation mới mà AI hiểu project trong vài phút.
- [ ] Feature được triển khai theo plan, không sửa ngẫu hứng.
- [ ] Test và quality gate ngăn regression.
- [ ] Performance được đo bằng số liệu.
- [ ] Security review có bằng chứng.
- [ ] Mọi quyết định quan trọng có tài liệu.
- [ ] Handoff cho phép người khác tiếp tục ngay.
- [ ] AI bị giới hạn trong scope và quyền hạn rõ ràng.
- [ ] Bạn biết lúc nào không nên nghe đề xuất của AI.

AI hiệu quả nhất khi repository trở thành bộ nhớ dài hạn, test trở thành cơ chế kiểm chứng, tài liệu trở thành contract và mỗi conversation chỉ xử lý một phạm vi nhỏ có thể review được.

## 15. Dùng thêm ECC, Superpowers và plugin mà không mất context

### Nguyên tắc cốt lõi

Skill/plugin là **khả năng bổ sung**, không phải bộ nhớ dự án. Không được đặt yêu cầu cốt lõi của project chỉ trong một skill hoặc một conversation.

Hãy chia context thành ba lớp:

| Lớp | Nội dung | Cách xử lý |
|---|---|---|
| Core project context | Product, architecture, coding rules, security, test gate | Luôn nạp |
| Task context | Feature brief, file liên quan, ADR, tests, handoff | Nạp theo task |
| Optional capability | ECC Java, Spring, security, performance, Superpowers | Chỉ nạp khi task cần |

### Những gì phải luôn được giữ

Đặt các yêu cầu bất biến trong AGENTS.md và docs/ai/core/project-contract.md:

~~~markdown
# Project contract

## Mission
Dự án giải quyết ...

## Non-negotiable constraints
- Java ...
- Spring Boot ...
- Không thay đổi public API nếu chưa có migration plan.
- Không log secret/PII.
- Mọi feature phải có regression test.
- Mọi external call phải có timeout.
- Không thêm dependency nếu chưa nêu lý do.

## Architecture invariants
- Package boundary ...
- Authentication boundary ...
- Error response format ...
- Observability fields ...

## Quality gates
- Build/compile pass.
- Unit/integration/contract test pass.
- Security scan pass.
- Performance baseline không giảm quá ngưỡng ...

## Definition of done
- Code, test, docs, ADR, metrics và rollback được cập nhật nếu có ảnh hưởng.
~~~

Skill/plugin có thể giúp thực hiện công việc, nhưng không được phép ghi đè các invariant này.

## 16. Phân loại skill/plugin theo mức cần nạp

### Nhóm A — Always-on

Chỉ giữ các rule thật sự áp dụng cho mọi task:

- Project contract.
- Coding rules.
- Test strategy.
- Security baseline.
- Quy tắc không sửa ngoài scope.
- Quy tắc phải báo test và rủi ro.

Nhóm này nên ngắn, ổn định và không chứa tutorial dài.

### Nhóm B — Task-specific

Chỉ nạp khi task liên quan:

- Java/Spring Boot.
- REST/API design.
- Security review.
- Performance profiling.
- Refactoring.
- Testing.
- CI/CD.

Ví dụ: sửa data-access thì nạp capability data-access; làm API thì nạp capability API; tối ưu latency thì nạp performance capability.

### Nhóm C — Temporary

Chỉ bật trong một đoạn ngắn:

- Debug một lỗi cụ thể.
- Migration.
- Incident response.
- Benchmark.
- Review release.

Xong task thì không tiếp tục đưa nhóm này vào mọi conversation.

### Nhóm D — Không cần nạp

- Skill trùng nội dung.
- Skill không liên quan tới file/task.
- Plugin chỉ cung cấp thông tin mà repository đã có.
- Tutorial dài nhưng không có action cần thực hiện.

## 17. Plugin registry để kiểm soát

Tạo file docs/ai/plugin-registry.md:

~~~markdown
# Plugin/skill registry

| Capability | Dùng khi | Không dùng khi | Owner | Ghi chú |
|---|---|---|---|---|
| ECC Java/Spring | Sửa Java/Spring | Docs thuần | Backend | Task-specific |
| Security review | Auth/API/secret | Feature UI thuần | Security | Bắt buộc trước release |
| Performance | Có baseline/profile | CRUD nhỏ chưa đo | Platform | Temporary |
| Superpowers planning | Feature lớn/không rõ | Hotfix một dòng | Team | Planning only |

## Activation rule
Mỗi task chỉ nạp capability thực sự cần. Nếu hai skill có nội dung trùng,
chọn một skill làm nguồn chính và ghi lý do.
~~~

Mỗi plugin nên có:

- Mục đích.
- Trigger/điều kiện sử dụng.
- File nào được phép đọc/sửa.
- Token/context cost ước lượng.
- Skill khác bị trùng.
- Người chịu trách nhiệm review.
- Khi nào phải tắt hoặc bỏ.

## 18. Quy trình thêm một skill/plugin mới

### Bước 1 — Inventory

Trước khi cài hoặc bật:

- Skill này giải quyết vấn đề nào?
- Có trùng AGENTS.md, coding rules hoặc skill khác không?
- Có tự động nạp vào mọi task không?
- Có bao nhiêu file/tool/schema được thêm vào context?
- Có quyền chạy command hoặc ghi file không?

### Bước 2 — Classify

Đặt vào một trong bốn nhóm: always-on, task-specific, temporary hoặc không dùng.

### Bước 3 — Define activation

Viết rule cụ thể:

~~~text
Nếu task thay đổi Java/Spring Boot:
  nạp Java/Spring capability.
Nếu task là security-sensitive:
  nạp security review capability.
Nếu task yêu cầu đo latency:
  nạp performance capability.
Nếu chỉ đọc docs:
  không nạp capability implementation.
~~~

### Bước 4 — Kiểm tra scope

Plugin không được:

- Tự thay đổi architecture ngoài task.
- Tự thêm dependency.
- Tự sửa test để che lỗi.
- Tự commit/merge/deploy nếu chưa được phép.
- Bỏ qua project contract.

### Bước 5 — Re-audit

Sau khi thêm plugin:

- Đo lại token/context overhead nếu môi trường hỗ trợ.
- Kiểm tra skill trùng nhau.
- Kiểm tra prompt/rule mâu thuẫn.
- Chạy một task nhỏ và review output.
- Ghi thay đổi vào plugin-registry.md.

## 19. Quản lý token thực tế

### Ngân sách vận hành đề xuất

Đây là heuristic để giữ chất lượng, không phải giới hạn cứng của mọi model:

| Thành phần | Mục tiêu |
|---|---:|
| Core project context | Khoảng 5–10% context khả dụng |
| Task context | Khoảng 10–20% |
| Skill/plugin cần thiết | Chỉ 1–3 capability |
| Lịch sử conversation | Không để chiếm phần lớn context |
| Dự phòng output/tool | Giữ ít nhất 30% cho code, test và kết quả |

### Cách giảm token

- Giữ AGENTS.md ngắn, chỉ ghi invariant và command.
- Tách tutorial dài khỏi file always-on.
- Dùng context index thay vì copy toàn bộ docs.
- Chỉ đưa file/symbol liên quan tới task.
- Dùng summary/handoff thay vì copy lịch sử chat.
- Gộp rule trùng lặp.
- Không bật toàn bộ plugin chỉ vì đã cài.
- Không nạp hai skill làm cùng một việc.
- Đưa ví dụ dài vào tài liệu on-demand.
- Lưu kết quả audit dưới dạng artifact ngắn.

### Audit định kỳ

Nếu môi trường hỗ trợ context audit, kiểm tra:

- Skill nào đang được nạp luôn.
- Agent/plugin nào có mô tả hoặc schema quá lớn.
- Rule nào bị trùng.
- Tool nào không dùng.
- Tổng overhead trước và sau khi thêm plugin.

Nếu không có command tự động, lập bảng thủ công:

| Component | Always-on? | Mục đích | Trùng với | Giữ/bỏ |
|---|---:|---|---|---|
| Project contract | Có | Invariant | Không | Giữ |
| Java skill | Không | Spring task | Một skill khác | Chọn một |
| Performance skill | Không | Profiling | Không | Nạp khi cần |

## 20. Prompt kiểm soát context

### Preflight trước khi code

~~~text
Trước khi thực hiện:
1. Đọc project contract và AGENTS.md.
2. Liệt kê facts đã xác nhận.
3. Liệt kê constraints không được vi phạm.
4. Xác định skill/plugin đang dùng và lý do.
5. Liệt kê file sẽ đọc/sửa.
6. Nêu phần context còn thiếu.
Chưa được sửa code.
~~~

### Contract cho mỗi câu trả lời của AI

Yêu cầu AI luôn trả về:

~~~text
## Facts
Những điều được xác nhận từ repository.

## Assumptions
Những điều chưa chắc.

## Plan
Các bước sắp làm.

## Changes
File/symbol đã thay đổi.

## Verification
Command/test/metric đã chạy và kết quả thật.

## Risks
Rủi ro hoặc việc chưa kiểm chứng.

## Context update
Docs/ADR/handoff nào cần cập nhật.
~~~

### Nếu AI quên yêu cầu cốt lõi

Không nhắc lại toàn bộ lịch sử. Gửi context contract ngắn:

~~~text
Bạn đang lệch khỏi project contract.
Các invariant cần giữ:
- ...
- ...
- ...

Hãy dừng, xác nhận lại facts và cập nhật plan.
Không sửa code cho đến khi plan phù hợp.
~~~

## 21. Ví dụ chọn skill theo task

### Task: thêm endpoint trong Spring Boot

Nạp:

- Core project context.
- Java/Spring capability.
- API contract/testing capability.

Không cần nạp:

- Performance profiling nếu chưa có yêu cầu đo.
- Migration capability nếu không đổi schema.
- Agent/autonomous workflow nếu chỉ sửa một endpoint nhỏ.

### Task: tối ưu latency

Nạp:

- Core project context.
- Performance capability.
- Observability/test capability.

Bắt buộc cung cấp:

- Baseline p50/p95/p99.
- Trace/profile.
- Workload và acceptance threshold.

### Task: security review

Nạp:

- Core project context.
- Security capability.
- Relevant API/auth files.

Không cho AI tự sửa toàn bộ repository; yêu cầu báo cáo issue trước, sau đó sửa từng issue có approval.

## 22. Handoff khi mở conversation mới

Trình tự:

1. Đọc AGENTS.md.
2. Đọc project-contract.md.
3. Đọc architecture/glossary/test strategy.
4. Đọc feature brief.
5. Đọc handoff mới nhất.
6. Chỉ nạp skill/plugin phù hợp task.
7. Yêu cầu AI tóm tắt facts và constraints.
8. Kiểm tra AI không nhầm assumption với fact.
9. Mới cho phép lập plan hoặc sửa code.

### Handoff tối thiểu

~~~markdown
# Handoff

## Current task
...

## Project contract to preserve
- ...

## Skills/plugins active
- ...

## Files relevant
- ...

## Done
- ...

## Tests
- Passed:
- Failed:
- Not run:

## Next exact step
...
~~~

## 23. Nguyên tắc cuối cùng

- Repository là memory dài hạn.
- AGENTS.md là entry rule, không phải nơi chứa mọi kiến thức.
- ADR lưu quyết định, test lưu behavior, metrics lưu baseline, handoff lưu trạng thái.
- Skill/plugin chỉ cung cấp capability theo task.
- Context pack phải nhỏ, có thứ tự và có source.
- Token budget được kiểm soát bằng classify, lazy-load, deduplicate và audit.
- AI phải báo facts, assumptions, changes, verification và risks.
- Không có plugin nào thay thế được review, test và trách nhiệm của người sở hữu project.

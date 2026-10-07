# Tuần 18 — Modular monolith

> Stack: Java 21, Spring Boot, Spring Data JPA và PostgreSQL. Bài này nối tiếp CI/CD ở Tuần 17 và chuẩn bị cho messaging/distributed systems ở Tuần 19.

## 1. Mục tiêu bài học

Sau tuần này, bạn có thể:

- Hiểu modular monolith khác layered monolith và microservices thế nào.
- Tổ chức source theo feature thay vì gom toàn bộ controller/service/repository.
- Thiết kế domain, application, inbound adapter và outbound adapter.
- Dùng dependency inversion để module không phụ thuộc trực tiếp vào infrastructure của module khác.
- Đặt module boundary và public API rõ ràng.
- Dùng domain event và application event mà không tạo coupling ngầm.
- Dùng transactional outbox cho event cần giao hàng bền vững.
- Kiểm tra boundary bằng ArchUnit hoặc Spring Modulith.
- Test domain, use case, adapter và module integration theo đúng phạm vi.
- Refactor từng vertical slice mà không rewrite toàn bộ ứng dụng.
- Nhận biết khi nào modular monolith là đủ và khi nào cần tách service.

Modular monolith là một ứng dụng deploy cùng process nhưng được chia thành các module có boundary rõ. “Monolith” nói về cách deploy; “modular” nói về cách tổ chức và kiểm soát dependency.

---

## 2. Vì sao cần module?

Một layered monolith thường có cấu trúc:

~~~text
controller/
service/
repository/
entity/
config/
~~~

Khi project lớn, một feature mới phải chạm nhiều package ngang. Bất kỳ service nào cũng có thể gọi repository hoặc service của feature khác. Boundary chỉ tồn tại trong quy ước, không được máy kiểm tra.

Modular monolith nhóm code theo capability:

~~~text
auth/
user/
project/
task/
notification/
shared/
~~~

Mỗi feature sở hữu use case, domain rule, persistence adapter và inbound adapter của nó. Module khác chỉ gọi public API hoặc phát/nhận event đã được quy định.

### 2.1. Lợi ích

- Dễ tìm code theo business capability.
- Giảm coupling và phạm vi thay đổi.
- Test module nhanh hơn test cả ứng dụng.
- Có đường tiến hóa sang service nếu thật sự cần.
- Vẫn dùng transaction và database join đơn giản khi phù hợp.
- Deploy một artifact, không có network hop giữa mọi module.

### 2.2. Chi phí

- Phải thiết kế contract và mapping rõ.
- Không thể tùy tiện truy cập entity/repository module khác.
- Có thể cần eventual consistency cho workflow liên module.
- Boundary không tự tốt lên chỉ vì tạo thêm folder.

Không tách microservice chỉ vì package đã chia module. Microservice thêm network, retry, deployment, observability, data ownership và failure mode mới.

---

## 3. Cấu trúc package theo feature

Roadmap gợi ý:

~~~text
com.example.taskmanager
├── TaskManagerApplication.java
├── auth
├── user
├── project
├── task
├── notification
└── shared
~~~

Một module có thể đi từ đơn giản đến đầy đủ:

~~~text
project/
├── api/
│   ├── ProjectController.java
│   └── ProjectResponse.java
├── application/
│   ├── CreateProjectUseCase.java
│   ├── GetProjectQuery.java
│   └── port/
│       ├── in/
│       └── out/
├── domain/
│   ├── Project.java
│   ├── ProjectId.java
│   └── ProjectPolicy.java
└── infrastructure/
    ├── persistence/
    │   ├── ProjectEntity.java
    │   ├── SpringDataProjectRepository.java
    │   └── ProjectRepositoryAdapter.java
    └── configuration/
~~~

Không bắt buộc mọi module có đủ thư mục ngay từ ngày đầu. CRUD nhỏ có thể bắt đầu bằng `api`, `application`, `domain`, `infrastructure`, sau đó tách khi code có lý do thực tế.

### 3.1. `shared` không phải thùng rác

Chỉ đưa vào `shared` thứ thực sự dùng chung và không thuộc business capability cụ thể:

- `ClockProvider` hoặc ID generator.
- Error contract chung.
- Security principal abstraction.
- Technical configuration.

Không đưa `ProjectService`, `UserRepository`, DTO của một feature hoặc mọi tiện ích tiện tay vào `shared`. Shared càng lớn, dependency ngược càng khó phát hiện.

---

## 4. Layered architecture trong một module

Một module có thể dùng các lớp:

~~~text
Inbound adapter (HTTP, event listener, CLI)
              ↓
Application/use case (orchestration, transaction boundary)
              ↓
Domain (invariants, value object, domain event)
              ↓
Outbound ports (repository, gateway, clock)
              ↑
Outbound adapters (JPA, Redis, HTTP client, queue)
~~~

Dependency đi vào trong:

- Adapter biết application contract.
- Application biết port, không biết JPA/HTTP SDK cụ thể.
- Domain không import Spring, JPA, Jackson hoặc HTTP.
- Infrastructure implement outbound port.

### 4.1. Domain layer

Domain chứa rule không phụ thuộc transport:

~~~java
public record ProjectId(UUID value) {

    public ProjectId {
        Objects.requireNonNull(value, "value");
    }
}

public final class Project {

    private final ProjectId id;
    private final String ownerId;
    private final String name;
    private final ProjectStatus status;

    private Project(ProjectId id, String ownerId, String name, ProjectStatus status) {
        this.id = id;
        this.ownerId = ownerId;
        this.name = name;
        this.status = status;
    }

    public static Project create(ProjectId id, String ownerId, String name) {
        if (name == null || name.isBlank()) {
            throw new InvalidProjectException("Project name is required");
        }
        return new Project(id, ownerId, name.trim(), ProjectStatus.ACTIVE);
    }

    public Project rename(String newName) {
        if (status != ProjectStatus.ACTIVE) {
            throw new InvalidProjectException("Only active project can be renamed");
        }
        return new Project(id, ownerId, newName.trim(), status);
    }

    public ProjectId id() { return id; }
    public String ownerId() { return ownerId; }
    public String name() { return name; }
    public ProjectStatus status() { return status; }
}
~~~

Ví dụ dùng immutable state: `rename()` trả về object mới thay vì thay đổi object đang được chia sẻ. Domain có thể dùng mutation nội bộ nếu aggregate được kiểm soát chặt, nhưng boundary phải rõ và test phải chứng minh invariant.

### 4.2. Application layer

Use case điều phối transaction, port và domain:

~~~java
public interface CreateProjectUseCase {
    ProjectView execute(CreateProjectCommand command);
}

public record CreateProjectCommand(String ownerId, String name) {}

public record ProjectView(UUID id, String ownerId, String name, String status) {}
~~~

Port inbound là API mà module cung cấp. Controller không nên gọi repository trực tiếp.

---

## 5. Ports và adapters

### 5.1. Outbound port

Port mô tả capability, không mô tả công nghệ:

~~~java
public interface ProjectRepositoryPort {
    Optional<Project> findById(ProjectId id);
    Project save(Project project);
    boolean existsByOwnerAndName(String ownerId, String name);
}

public interface ProjectEventPort {
    void publish(ProjectCreatedEvent event);
}
~~~

Tên port là `ProjectRepositoryPort`, không phải `JpaProjectRepositoryPort`; application không cần biết adapter đang dùng JPA, JDBC hay fake.

### 5.2. Use case implementation

~~~java
@Service
@Transactional
public class CreateProjectService implements CreateProjectUseCase {

    private final ProjectRepositoryPort projects;
    private final ProjectEventPort events;
    private final ProjectIdGenerator ids;

    public CreateProjectService(
            ProjectRepositoryPort projects,
            ProjectEventPort events,
            ProjectIdGenerator ids
    ) {
        this.projects = projects;
        this.events = events;
        this.ids = ids;
    }

    @Override
    public ProjectView execute(CreateProjectCommand command) {
        if (projects.existsByOwnerAndName(command.ownerId(), command.name())) {
            throw new ProjectNameAlreadyUsedException();
        }

        Project project = Project.create(
                ids.next(),
                command.ownerId(),
                command.name()
        );
        Project saved = projects.save(project);
        events.publish(new ProjectCreatedEvent(saved.id(), saved.ownerId()));
        return ProjectViewMapper.toView(saved);
    }
}
~~~

`@Transactional` có thể nằm ở application service vì đây là workflow boundary. Domain không cần biết transaction tồn tại.

### 5.3. Inbound adapter

~~~java
@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final CreateProjectUseCase createProject;

    public ProjectController(CreateProjectUseCase createProject) {
        this.createProject = createProject;
    }

    @PostMapping
    ResponseEntity<ProjectResponse> create(
            @Valid @RequestBody CreateProjectRequest request,
            Authentication authentication
    ) {
        ProjectView view = createProject.execute(
                new CreateProjectCommand(
                        authentication.getName(),
                        request.name()
                )
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ProjectResponse.from(view));
    }
}
~~~

Controller chỉ chuyển HTTP request thành command và chuyển output thành response. Không truyền `HttpServletRequest`, `EntityManager` hoặc Spring `Page` vào domain/use case.

### 5.4. Outbound adapter

~~~java
@Component
public class JpaProjectRepositoryAdapter implements ProjectRepositoryPort {

    private final SpringDataProjectRepository repository;

    public JpaProjectRepositoryAdapter(SpringDataProjectRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Project> findById(ProjectId id) {
        return repository.findById(id.value()).map(ProjectMapper::toDomain);
    }

    @Override
    public Project save(Project project) {
        return ProjectMapper.toDomain(
                repository.save(ProjectMapper.toEntity(project))
        );
    }

    @Override
    public boolean existsByOwnerAndName(String ownerId, String name) {
        return repository.existsByOwnerIdAndName(ownerId, name);
    }
}
~~~

Mapping entity ↔ domain ở adapter. Không trả `ProjectEntity` ra ngoài module persistence.

---

## 6. Module boundary

Boundary trả lời ba câu hỏi:

1. Module sở hữu dữ liệu và rule nào?
2. Module khác được gọi public API nào?
3. Thay đổi nội bộ nào không được làm ảnh hưởng module khác?

Ví dụ ownership:

| Module | Sở hữu | Public capability |
|---|---|---|
| `auth` | credential, token, policy đăng nhập | `authenticate`, `currentUser` |
| `user` | profile, membership | `findUser`, `changeProfile` |
| `project` | project, member, status | `createProject`, `getProject` |
| `task` | task, due date, status | `createTask`, `closeExpiredTasks` |
| `notification` | delivery attempt, template | `enqueueNotification` |

### 6.1. Public API module

Tạo package `project.api` hoặc `project.application.port.in` làm public contract:

~~~java
package com.example.taskmanager.project.api;

public interface ProjectFacade {
    Optional<ProjectSummary> findSummary(ProjectId projectId);
    boolean canEdit(ProjectId projectId, String userId);
}
~~~

Module `task` phụ thuộc vào `ProjectFacade`, không phụ thuộc `ProjectEntity`, `ProjectRepository` hoặc bảng project trực tiếp.

### 6.2. Dữ liệu không đi xuyên boundary tùy tiện

Không nên:

~~~java
// Sai: task module biết repository và entity nội bộ của project.
projectRepository.findById(id).get().getMembers().contains(userId);
~~~

Nên:

~~~java
// Đúng: task gọi capability được project công bố.
if (!projectFacade.canEdit(projectId, userId)) {
    throw new ProjectAccessDeniedException();
}
~~~

Nếu nhiều use case cần cùng một rule, đưa rule về module sở hữu hoặc public policy nhỏ. Đừng copy rule sang mọi caller.

### 6.3. Cross-module transaction

Trong monolith, hai module vẫn có thể dùng cùng database transaction, nhưng không nên coi đó là lý do để mọi module gọi trực tiếp repository của nhau. Transaction boundary là quyết định nghiệp vụ, còn module boundary là quyết định dependency.

Nếu workflow chỉ cần kết quả “đã xảy ra”, dùng event. Nếu cần kết quả đồng bộ để quyết định tiếp, dùng facade/port rõ ràng.

---

## 7. Domain event và application event

### 7.1. Domain event

Domain event mô tả điều đã xảy ra trong domain:

~~~java
public record ProjectCreatedEvent(
        ProjectId projectId,
        String ownerId
) {}
~~~

Event nên chứa ID và dữ liệu tối thiểu, immutable, không chứa entity JPA hoặc object có lazy relationship.

### 7.2. Event listener trong monolith

~~~java
@Component
public class ProjectCreatedListener {

    private final NotificationFacade notifications;

    public ProjectCreatedListener(NotificationFacade notifications) {
        this.notifications = notifications;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProjectCreated(ProjectCreatedEvent event) {
        notifications.enqueueProjectCreated(event.projectId());
    }
}
~~~

`AFTER_COMMIT` tránh gửi side effect trước khi transaction thành công. Nhưng Spring application event trong memory có thể mất khi process chết; nó không tự cung cấp durable delivery.

### 7.3. Synchronous hay asynchronous?

| Cách | Dùng khi | Rủi ro |
|---|---|---|
| Gọi facade đồng bộ | Caller cần kết quả ngay | Tăng coupling/latency |
| Event đồng bộ | Rule cần chạy cùng flow | Listener làm request chậm |
| `@Async` event | Side effect không cần trả ngay | Mất event khi process crash |
| Outbox + worker | Cần delivery/retry | Thêm bảng và worker |

Không dùng `@Async` để che một workflow bắt buộc phải durable. Chọn outbox nếu notification, billing hoặc integration không được mất.

---

## 8. Transactional outbox

Outbox ghi business change và event trong cùng transaction:

~~~text
transaction:
  update project
  insert outbox_event(status=NEW)
commit

worker:
  claim NEW event
  publish/handle side effect
  mark SENT hoặc RETRY/FAILED
~~~

### 8.1. Schema tối thiểu

~~~sql
CREATE TABLE outbox_event (
    id              UUID PRIMARY KEY,
    aggregate_type  VARCHAR(100) NOT NULL,
    aggregate_id    VARCHAR(100) NOT NULL,
    event_type      VARCHAR(200) NOT NULL,
    payload         JSONB NOT NULL,
    status          VARCHAR(30) NOT NULL,
    attempts        INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL,
    processed_at    TIMESTAMPTZ
);

CREATE INDEX idx_outbox_ready
    ON outbox_event (status, next_attempt_at, created_at);
~~~

`payload` phải có schema/version và không chứa secret. Bảng cần retention/cleanup sau khi event đã xử lý, nếu không sẽ phình vô hạn.

### 8.2. Ghi outbox

~~~java
@Transactional
public ProjectView execute(CreateProjectCommand command) {
    Project project = createProject(command);
    Project saved = projects.save(project);
    outbox.save(OutboxEvent.newEvent(
            "Project",
            saved.id().value().toString(),
            "ProjectCreated.v1",
            Map.of("projectId", saved.id().value())
    ));
    return ProjectViewMapper.toView(saved);
}
~~~

Nếu transaction rollback, cả project và outbox event rollback. Nếu commit thành công nhưng worker chết, event vẫn còn để retry.

### 8.3. Claim event và idempotency

Worker có thể chạy nhiều instance. Claim bằng row lock/lease:

~~~sql
SELECT id, payload
FROM outbox_event
WHERE status = $$NEW$$
  AND (next_attempt_at IS NULL OR next_attempt_at <= now())
ORDER BY created_at
FOR UPDATE SKIP LOCKED
LIMIT 50;
~~~

Trong cùng transaction claim, worker phải chuyển row sang `PROCESSING` và đặt lease/`locked_until` trước khi commit; nếu chỉ `SELECT` rồi commit, instance khác có thể lấy cùng event. Lease cần hết hạn để event bị worker khác tiếp quản sau crash.

Side effect phải idempotent. Tạo unique key theo `(event_id, consumer)` hoặc lưu delivery record trước khi gọi provider nếu provider hỗ trợ idempotency key.

At-least-once delivery nghĩa là handler có thể nhận cùng event nhiều lần. Đừng giả định event chỉ tới một lần.

---

## 9. Module dependency hợp lệ

Một dependency graph đơn giản:

~~~text
auth ───────┐
user ───────┼──→ shared contracts
project ────┤
task ────────┤
notification┘

task → project.api
project → user.api
project → outbox port
notification → outbox consumer contract
~~~

Tránh vòng:

~~~text
project → task → project
~~~

Nếu hai module cần nhau:

- Tách shared concept nhỏ, nhưng không đẩy business logic vào shared.
- Đổi một lời gọi thành domain/application event.
- Đặt facade ở module sở hữu capability.
- Gộp hai module nếu boundary thực tế không tồn tại.

Vòng dependency thường là dấu hiệu ownership chưa rõ, không phải vấn đề cần che bằng `@Lazy`.

---

## 10. Kiểm tra boundary bằng ArchUnit

ArchUnit biến quy ước package thành test:

~~~xml
<dependency>
    <groupId>com.tngtech.archunit</groupId>
    <artifactId>archunit-junit5</artifactId>
    <scope>test</scope>
</dependency>
~~~

Ví dụ cấm task truy cập infrastructure của project:

~~~java
@AnalyzeClasses(packages = "com.example.taskmanager")
class ModuleBoundaryTest {

    @ArchTest
    static final ArchRule task_must_not_access_project_infrastructure =
            noClasses()
                    .that().resideInAnyPackage("..task..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..project.infrastructure..");
}
~~~

Thêm rule:

- Domain không phụ thuộc Spring/JPA/Jackson.
- API không phụ thuộc repository implementation.
- Module không truy cập entity module khác.
- Adapter không được gọi adapter module khác trực tiếp.
- Chỉ package public được import từ module khác.

ArchUnit kiểm tra dependency tĩnh, không chứng minh runtime behavior. Vẫn cần module integration test và E2E test.

---

## 11. Spring Modulith

Spring Modulith cung cấp convention và công cụ để xác định, verify, test và document application module trong Spring Boot. Nó không thay thế domain design; nó giúp phát hiện boundary bị phá.

### 11.1. Dependency

Version phải được quản lý bằng Spring Boot/Spring Modulith BOM tương thích:

~~~xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.modulith</groupId>
            <artifactId>spring-modulith-bom</artifactId>
            <version>${spring-modulith.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependency>
    <groupId>org.springframework.modulith</groupId>
    <artifactId>spring-modulith-starter-core</artifactId>
</dependency>
~~~

Không tự ghép version Modulith với Spring Boot. Chọn release line tương thích với Java/Spring Boot của project.

### 11.2. Verify module arrangement

~~~java
@SpringBootTest
class ApplicationModulesTest {

    @Test
    void verifiesModuleBoundaries() {
        ApplicationModules.of(TaskManagerApplication.class).verify();
    }
}
~~~

Test fail khi có dependency không được phép theo module model. Chạy nó trong CI để boundary không bị phá âm thầm.

### 11.3. Document module graph

~~~java
class ModuleDocumentationTest {

    @Test
    void writesModuleDocumentation() {
        var modules = ApplicationModules.of(TaskManagerApplication.class).verify();
        new Documenter(modules)
                .writeModulesAsPlantUml()
                .writeIndividualModulesAsPlantUml();
    }
}
~~~

Đưa sơ đồ generated vào artifact hoặc docs review, không chỉnh tay sơ đồ thay vì sửa code/boundary thật.

### 11.4. Module integration test

~~~java
@ApplicationModuleTests
class ProjectModuleIntegrationTests {

    @Test
    void createsProjectThroughModuleBoundary() {
        // Test module với dependency cần thiết, không boot cả external system nếu không cần.
    }
}
~~~

Spring Modulith là lựa chọn hữu ích khi project đã dùng Spring Boot và muốn enforce module; ArchUnit vẫn phù hợp nếu team muốn dependency tối thiểu hoặc rule tùy biến sâu.

---

## 12. Test theo boundary

### 12.1. Domain test

Không cần Spring context:

~~~java
class ProjectTest {

    @Test
    void cannotRenameClosedProject() {
        Project closed = TestProjects.closed();

        assertThatThrownBy(() -> closed.rename("new-name"))
                .isInstanceOf(InvalidProjectException.class);
    }
}
~~~

Domain test nên nhanh, deterministic và kiểm tra invariant.

### 12.2. Use case unit test

Dùng fake port thay vì mock mọi chi tiết framework:

~~~java
class CreateProjectServiceTest {

    private final InMemoryProjectRepository projects =
            new InMemoryProjectRepository();
    private final RecordingProjectEvents events =
            new RecordingProjectEvents();
    private final CreateProjectService service =
            new CreateProjectService(projects, events, new FixedIds());

    @Test
    void savesProjectAndPublishesEvent() {
        ProjectView result = service.execute(
                new CreateProjectCommand("user-1", "Roadmap")
        );

        assertThat(result.name()).isEqualTo("Roadmap");
        assertThat(events.events()).hasSize(1);
    }
}
~~~

Test cả outcome và interaction với port: repository được gọi đúng, event không phát khi validation fail, duplicate name bị chặn.

### 12.3. Adapter integration test

JPA adapter chạy với PostgreSQL Testcontainers để kiểm tra mapping, constraint, query và transaction. Không dùng unit test để kết luận Hibernate mapping đúng.

### 12.4. Module integration và E2E

- Module integration: boot scope vừa đủ, test contract nội bộ/public API.
- E2E: HTTP → controller → use case → adapter → database, chỉ dành cho flow quan trọng.

Không biến mọi test thành `@SpringBootTest` full context; test càng gần boundary càng nhanh và dễ chẩn đoán.

---

## 13. Refactor layered monolith sang modular monolith

Không rewrite big-bang. Dùng strangler/vertical slice:

### Bước 1 — Characterization test

Ghi lại behavior hiện tại của một endpoint/job: response, error, side effect và transaction. Test này bảo vệ khi di chuyển code.

### Bước 2 — Chọn một slice

Chọn feature có churn cao nhưng blast radius thấp, ví dụ `create project`. Không bắt đầu bằng auth hoặc billing nếu chưa có boundary tốt.

### Bước 3 — Tạo public use case

Định nghĩa command, output và error contract. Controller cũ có thể delegate vào use case mới.

### Bước 4 — Bao port quanh dependency cũ

Đặt interface quanh repository/gateway hiện tại, rồi viết adapter gọi implementation cũ. Chưa cần thay toàn bộ persistence ngay.

### Bước 5 — Di chuyển domain rule

Tách invariant khỏi controller/service chung. Giữ behavior tương đương và thêm domain test.

### Bước 6 — Enforce rule

Thêm ArchUnit/Spring Modulith verify vào CI. Boundary phải fail ngay khi có import sai.

### Bước 7 — Lặp lại từng slice

Không di chuyển mọi feature trong một PR khổng lồ. Mỗi PR nên build, test, review và rollback độc lập.

---

## 14. Transaction và consistency giữa module

### 14.1. Synchronous consistency

Nếu Project và Task cần cùng quyết định trong một request, gọi public facade trong cùng transaction khi coupling đó hợp lý:

~~~text
CreateTaskUseCase
  → ProjectFacade.canCreateTask(projectId, actorId)
  → TaskRepository.save(task)
~~~

Không expose `ProjectEntity` để Task tự kiểm tra field nội bộ.

### 14.2. Eventual consistency

Nếu notification chỉ cần biết Project đã tạo, phát event/outbox:

~~~text
Project transaction → outbox commit
                     ↓
                 worker/handler
                     ↓
              notification delivery
~~~

UI có thể thấy project trước khi notification được gửi. API cần document trạng thái `queued`, `sent`, `failed` nếu người dùng quan tâm.

### 14.3. Không dùng event cho mọi thứ

Event làm giảm coupling trực tiếp nhưng tăng coupling qua schema, ordering, retry và observability. Nếu caller cần câu trả lời ngay, facade synchronous dễ hiểu hơn. Event phù hợp khi “đã xảy ra” là contract đủ.

---

## 15. Khi nào tách thành microservice?

Chỉ cân nhắc tách khi có nhu cầu đo được:

- Module cần scale độc lập rõ ràng.
- Team ownership và release cadence khác nhau.
- Fault isolation bắt buộc.
- Data/compliance boundary cần tách.
- Có traffic hoặc workload khiến process chung không phù hợp.
- Có khả năng vận hành distributed system: tracing, retry, contract test, on-call.

Không tách chỉ vì:

- Package đang lớn.
- Muốn dùng Kafka sớm.
- Có vài team nhưng chưa có ownership rõ.
- Nghĩ microservice luôn scale tốt hơn.

Modular monolith là một điểm dừng hợp lệ. Module boundary tốt cho phép tách sau này, nhưng không hứa rằng việc tách sẽ miễn phí.

---

## 16. Lỗi thường gặp

- Chỉ đổi tên folder nhưng mọi module vẫn truy cập mọi repository.
- Đặt toàn bộ class dùng chung vào `shared`.
- Domain import JPA annotation, Spring service hoặc HTTP client.
- Trả entity/lazy proxy qua module boundary.
- Controller module này gọi adapter module khác trực tiếp.
- Dùng `@Lazy` để che circular dependency.
- Copy lại authorization/business rule ở nhiều module.
- Phát application event trước commit cho side effect bắt buộc.
- Dùng `@Async` thay cho outbox khi event không được mất.
- Event payload chứa entity hoặc secret.
- Không version event schema.
- Worker không idempotent và xử lý outbox duplicate.
- Module test chỉ boot full app, không kiểm tra boundary.
- Rewrite toàn bộ trước khi có characterization test.
- Tách microservice trước khi có monitoring và runbook.

---

## 17. Bài thực hành bắt buộc

1. Tổ chức package `auth`, `user`, `project`, `task`, `notification`, `shared`.
2. Chọn feature `project` làm vertical slice đầu tiên.
3. Tách `Project` domain khỏi `ProjectEntity` JPA.
4. Tạo `CreateProjectUseCase`, command, view và inbound port.
5. Tạo `ProjectRepositoryPort` và JPA adapter.
6. Expose `ProjectFacade` tối thiểu cho Task module.
7. Thêm domain event `ProjectCreated` với payload immutable.
8. Tạo bảng transactional outbox và worker retry idempotent.
9. Viết ArchUnit rule cấm Task truy cập Project infrastructure.
10. Chạy `ApplicationModules.verify()` nếu dùng Spring Modulith.
11. Viết domain, use case, adapter và module integration test.
12. Vẽ dependency graph và giải thích mọi cross-module dependency.
13. Refactor một endpoint cũ theo strangler approach.
14. Viết decision record: vì sao chưa tách microservice.

---

## 18. Câu hỏi tự kiểm tra

1. Modular monolith khác layered monolith ở đâu?
2. Module boundary bảo vệ điều gì?
3. Vì sao domain không nên import JPA/Spring?
4. Inbound port và outbound port khác nhau thế nào?
5. Adapter có nên gọi adapter module khác không?
6. Khi nào dùng facade synchronous, khi nào dùng event?
7. Vì sao `AFTER_COMMIT` vẫn có thể mất event?
8. Transactional outbox giải quyết khoảng trống nào?
9. At-least-once delivery yêu cầu handler làm gì?
10. `FOR UPDATE SKIP LOCKED` hỗ trợ worker ra sao?
11. ArchUnit và Spring Modulith kiểm tra khác nhau thế nào?
12. Vì sao nên refactor từng vertical slice?
13. Migration nào cần expand/contract khi module cùng database?
14. Tín hiệu nào cho thấy cần microservice?
15. Khi nào shared package trở thành coupling hub?

---

## 19. Tiêu chí hoàn thành tuần 18

- [ ] Source được tổ chức package-by-feature.
- [ ] Mỗi module có ownership và public API rõ.
- [ ] Domain không phụ thuộc framework/infrastructure.
- [ ] Use case phụ thuộc port, không phụ thuộc adapter cụ thể.
- [ ] Entity/DTO nội bộ không đi xuyên module tùy tiện.
- [ ] Cross-module call đi qua facade/port hoặc event.
- [ ] Domain event immutable và có schema rõ.
- [ ] Event bắt buộc được lưu qua transactional outbox.
- [ ] Worker outbox có retry, lease và idempotency.
- [ ] ArchUnit hoặc Spring Modulith verify chạy trong CI.
- [ ] Có domain, use case, adapter và module integration test.
- [ ] Có characterization test trước refactor.
- [ ] Dependency graph không có vòng không giải thích được.
- [ ] Có decision record về việc giữ modular monolith.

---

## 20. Gợi ý lịch học 7 ngày

| Ngày | Nội dung | Kết quả |
|---|---|---|
| 1 | Layered monolith và package-by-feature | Chọn module boundary |
| 2 | Domain model, value object, invariant | Domain độc lập framework |
| 3 | Ports, adapters và use case | Một vertical slice hoàn chỉnh |
| 4 | Facade, dependency graph, ArchUnit | Boundary được enforce |
| 5 | Domain event và transactional outbox | Side effect durable |
| 6 | Test theo module và refactor strangler | Refactor an toàn |
| 7 | Spring Modulith, review architecture | Sẵn sàng cho messaging |

Tuần 19 theo roadmap chuyển sang messaging và distributed systems: Kafka hoặc RabbitMQ, at-least-once delivery, idempotent consumer, dead-letter queue, ordering, retry, outbox và eventual consistency.

## 21. Tài liệu tham khảo

- Spring Modulith: https://spring.io/projects/spring-modulith
- Spring Modulith reference: https://docs.spring.io/spring-modulith/reference/
- Spring Modulith testing: https://docs.spring.io/spring-modulith/reference/testing.html
- Spring Modulith events: https://docs.spring.io/spring-modulith/reference/events.html
- Spring Framework application events: https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html#context-functionality-events
- ArchUnit: https://www.archunit.org/userguide/html/000_Index.html
- Martin Fowler — Monolith First: https://martinfowler.com/bliki/MonolithFirst.html
- Martin Fowler — Event Sourcing and transactional messaging concepts: https://martinfowler.com/articles/patterns-of-distributed-systems/transactional-outbox.html

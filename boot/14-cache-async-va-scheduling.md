# Tuần 14 — Cache, async và scheduling

> Stack: Java 21, Spring Boot 4.x, Spring Cache, Redis, Spring Data JPA và PostgreSQL. Bài này nối tiếp Task API, authentication và background work từ các tuần trước.

## 1. Mục tiêu bài học

Sau tuần này, bạn có thể:

- Hiểu cache-aside và abstraction của Spring Cache.
- Dùng Cacheable, CachePut, CacheEvict và Caching đúng semantics.
- Cấu hình Redis CacheManager với TTL, key prefix và serialization.
- Thiết kế cache key ổn định, tránh cache entity/lazy proxy.
- Xử lý cache invalidation, stale data, cache stampede và cache penetration.
- Chọn dữ liệu nên cache và dữ liệu không nên cache.
- Chạy công việc bất đồng bộ với Async và thread pool có giới hạn.
- Xử lý exception, timeout, retry và backpressure của async task.
- Dùng Scheduled với fixedDelay, fixedRate và cron.
- Làm scheduled job idempotent khi chạy nhiều instance.
- Dùng distributed lock hoặc job queue để chống chạy trùng.
- Thiết kế retry exponential backoff có jitter.
- Viết test cho cache, async notification và scheduled job.

---

## 2. Cache giải quyết vấn đề gì?

Cache lưu bản sao dữ liệu gần application hơn database hoặc dependency bên ngoài:

~~~text
request → cache hit → response
request → cache miss → database → cache put → response
~~~

Cache có thể giảm latency và tải database, nhưng tạo thêm bản sao dữ liệu. Vì vậy cache luôn có hai câu hỏi:

1. Dữ liệu có thể stale tối đa bao lâu?
2. Khi dữ liệu gốc thay đổi, cache bị invalidated bằng cách nào?

Không cache chỉ vì một method chậm. Đầu tiên đo query, index và connection pool ở tuần 9. Cache phù hợp khi dữ liệu đọc nhiều, thay đổi ít, và có policy stale rõ ràng.

Ví dụ phù hợp:

- Project summary được đọc nhiều.
- Cấu hình public có TTL.
- Permission metadata có thể invalidate khi admin thay đổi.

Ví dụ cần cẩn thận:

- Số dư tài khoản hoặc trạng thái thanh toán.
- Authorization decision phụ thuộc dữ liệu mới.
- Entity graph lớn, mutable hoặc chứa dữ liệu riêng tư.
- Kết quả query có filter cá nhân nhưng key không bao gồm đủ context.

---

## 3. Spring Cache abstraction

Bật cache annotation:

~~~java
@Configuration
@EnableCaching
public class CacheConfiguration {
}
~~~

Spring Cache là abstraction. Implementation có thể là:

- ConcurrentMap: local/dev, không phù hợp nhiều instance.
- Caffeine: local in-memory nhanh, có eviction.
- Redis: shared cache giữa nhiều instance.
- Cache provider khác theo hạ tầng.

Annotation chính:

| Annotation | Hành vi |
|---|---|
| Cacheable | Nếu có value thì bỏ qua method; miss thì gọi method rồi put |
| CachePut | Luôn gọi method và put kết quả |
| CacheEvict | Xóa một key hoặc toàn bộ cache |
| Caching | Gom nhiều thao tác cache |
| CacheConfig | Cấu hình chung ở class |

Cache annotation hoạt động qua Spring proxy. Self-invocation trong cùng object không đi qua proxy:

~~~java
public TaskResponse outer(UUID id) {
    return inner(id); // có thể không kích hoạt @Cacheable
}

@Cacheable(cacheNames = "tasks", key = "#id")
public TaskResponse inner(UUID id) {
    // ...
}
~~~

Tách bean hoặc gọi qua proxy nếu cần cache. Không để cache behavior phụ thuộc vào việc một method nội bộ gọi method khác mà không có test.

---

## 4. Cache project bằng Redis

### 4.1. Dependency

~~~xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
~~~

Cấu hình connection bằng environment:

~~~yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      timeout: 2s
~~~

Production không dùng password rỗng. Nếu Redis có TLS hoặc ACL, cấu hình theo managed Redis provider và không hardcode credential.

### 4.2. Redis CacheManager với TTL

~~~java
@Configuration
@EnableCaching
public class ProjectCacheConfiguration {

    @Bean
    RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaults =
                RedisCacheConfiguration.defaultCacheConfig()
                    .entryTtl(Duration.ofMinutes(5))
                    .disableCachingNullValues()
                    .computePrefixWith(cacheName ->
                            "task-api:" + cacheName + "::")
                    .serializeValuesWith(
                        RedisSerializationContext.SerializationPair
                            .fromSerializer(
                                new GenericJackson2JsonRedisSerializer()
                            )
                    );

        Map<String, RedisCacheConfiguration> cacheConfigs = Map.of(
            "projects", defaults.entryTtl(Duration.ofMinutes(10)),
            "task-summaries", defaults.entryTtl(Duration.ofMinutes(2))
        );

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaults)
                .withInitialCacheConfigurations(cacheConfigs)
                .transactionAware()
                .build();
    }
}
~~~

TTL là safety net, không phải invalidation strategy duy nhất. Prefix tránh collision với key của ứng dụng khác. Serializer generic ở trên chỉ nên dùng cho cache nội bộ có schema kiểm soát; production nên dùng typed serializer/cache manager theo từng DTO và không bật polymorphic typing tùy tiện với dữ liệu không tin cậy.

Redis cache value nên là immutable response DTO hoặc projection đã ổn định schema, không phải JPA entity có lazy relationship. Khi đổi DTO schema, cân nhắc đổi cache name/prefix hoặc xử lý backward compatibility.

---

## 5. Cacheable project

~~~java
@Service
@CacheConfig(cacheNames = "projects")
public class ProjectQueryService {

    private final ProjectRepository projectRepository;

    public ProjectQueryService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @Cacheable(
        cacheNames = "projects",
        key = "#projectId",
        unless = "#result == null"
    )
    @Transactional(readOnly = true)
    public ProjectSummary getProject(UUID projectId) {
        return projectRepository.findSummaryById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));
    }
}
~~~

Cache key phải chứa mọi input ảnh hưởng kết quả:

~~~java
@Cacheable(
    cacheNames = "task-search",
    key = "T(String).format('%s:%s:%s', #status, #page, #size)"
)
public Page<TaskListItem> search(
        TaskStatus status,
        int page,
        int size
) {
    // ...
}
~~~

Với filter nhiều field, dùng value object có stable toString hoặc custom KeyGenerator. Không dùng object mutable làm key. Đừng bỏ tenantId, userId, locale hoặc permission context khỏi key nếu kết quả phụ thuộc chúng.

Cache null có thể che lỗi not-found hoặc làm cache penetration; chọn rõ giữa caching null ngắn hạn và không cache null.

---

## 6. Cập nhật và invalidate cache

### 6.1. CachePut

Dùng khi method luôn phải chạy và kết quả mới chính là cache value:

~~~java
@CachePut(cacheNames = "projects", key = "#result.id")
@Transactional
public ProjectSummary renameProject(
        UUID projectId,
        String newName
) {
    ProjectEntity project = projectRepository.findById(projectId)
            .orElseThrow(() -> new ProjectNotFoundException(projectId));

    project.rename(newName);
    return ProjectSummary.from(project);
}
~~~

Key của CachePut phải khớp key của Cacheable. Đừng dùng CachePut trước khi transaction commit nếu cache value có thể phản ánh transaction rollback.

### 6.2. CacheEvict

~~~java
@CacheEvict(cacheNames = "projects", key = "#projectId")
@Transactional
public void deleteProject(UUID projectId) {
    projectRepository.deleteById(projectId);
}
~~~

Xóa toàn bộ cache:

~~~java
@CacheEvict(cacheNames = "task-search", allEntries = true)
public void invalidateTaskSearch() {
}
~~~

allEntries=true đơn giản nhưng có thể gây cache miss lớn. Nếu có thể, evict key cụ thể hoặc versioned key.

### 6.3. Caching nhiều key

~~~java
@Caching(evict = {
    @CacheEvict(cacheNames = "projects", key = "#projectId"),
    @CacheEvict(cacheNames = "project-lists", allEntries = true)
})
@Transactional
public void updateProject(UUID projectId, UpdateProjectCommand command) {
    // ...
}
~~~

Cache list khó invalidate hơn cache detail. Khi project đổi, mọi list có thể bị ảnh hưởng. Cân nhắc TTL ngắn, versioned key, hoặc không cache list ở giai đoạn đầu.

---

## 7. Transaction và cache consistency

Annotation cache chạy quanh method, còn database transaction có thể commit sau khi method return. Nếu cache put/evict xảy ra trước commit rồi transaction rollback, cache chứa dữ liệu sai.

### Strategy A — Cache sau commit

Phát domain event trong transaction và xử lý bằng TransactionalEventListener:

~~~java
@Transactional
public void renameProject(UUID id, String name) {
    ProjectEntity project = load(id);
    project.rename(name);
    applicationEventPublisher.publishEvent(
            new ProjectChangedEvent(id)
    );
}

@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void evictProject(ProjectChangedEvent event) {
    cacheManager.getCache("projects").evict(event.projectId());
}
~~~

Async listener không tự đảm bảo retry/durability. Nếu mất process sau commit trước khi event xử lý, cần outbox hoặc queue.

### Strategy B — Cache after commit

Sau commit, đọc lại projection từ database rồi put cache. Điều này tránh cache object chưa commit.

### Strategy C — TTL ngắn + eventual consistency

Chấp nhận stale window document được. Phù hợp public summary, không phù hợp authorization hoặc tiền.

Không có cache policy nào vừa luôn fresh, luôn nhanh và không có coordination. Ghi rõ freshness SLA.

---

## 8. Cache failure và Redis outage

Cache là optimization. Khi Redis unavailable, cần policy:

- Fail open: bỏ qua cache và đọc database nếu endpoint vẫn chịu được tải.
- Fail closed: trả lỗi cho dữ liệu không được phép stale.
- Degrade: trả response đơn giản hơn hoặc chỉ cho read path quan trọng.

Không nuốt mọi Redis exception mà không metric/log. Nhưng cũng không để cache outage làm lộ stack trace hoặc làm login/security fail sai cách.

Không cache password, access token, refresh token raw, private key hoặc authorization result nhạy cảm trong một cache chung.

Redis timeout phải ngắn hơn request timeout. Connection pool Redis cần giới hạn để một outage không làm cạn thread request.

---

## 9. Cache stampede, penetration và avalanche

### Cache stampede

Một key nóng hết TTL, nhiều request cùng miss và cùng query database.

Giải pháp:

- TTL jitter.
- Single-flight/locking cho key nóng.
- Pre-warm có giới hạn.
- Stale-while-revalidate.
- Rate limit endpoint.
- Query database chịu được burst.

Không dùng global synchronized lock làm mọi key chặn nhau.

### Cache penetration

Attacker truy vấn nhiều ID không tồn tại.

Giải pháp:

- Validate ID/input.
- Caching null với TTL rất ngắn nếu hợp lý.
- Rate limiting.
- Bloom filter khi dataset lớn.
- Không để error path tạo database load vô hạn.

### Cache avalanche

Nhiều key hết hạn cùng lúc sau deploy hoặc cùng TTL.

Giải pháp:

- TTL ngẫu nhiên nhỏ.
- Warm cache theo batch.
- Giới hạn concurrency khi rebuild.
- Không dùng một thời điểm expiry cứng cho mọi key.

---

## 10. Async trong Spring

Bật async:

~~~java
@Configuration
@EnableAsync
public class AsyncConfiguration {
}
~~~

Thread pool phải có giới hạn:

~~~java
@Configuration
@EnableAsync
public class AsyncExecutorConfiguration {

    @Bean(name = "notificationExecutor")
    ThreadPoolTaskExecutor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(200);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("notification-");
        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
~~~

Không dùng unbounded queue cho notification/job có thể tăng không giới hạn. CallerRunsPolicy tạo backpressure nhưng làm request thread chạy task; với task nặng có thể dùng rejection + 429/queue bền vững.

Pool size dựa trên loại workload:

- CPU-bound: gần số core, đo thực tế.
- I/O-bound: có thể lớn hơn nhưng phải giới hạn external dependency.
- DB-bound: không vượt quá connection pool và database capacity.

### 10.1. Async notification

~~~java
@Service
public class NotificationService {

    @Async("notificationExecutor")
    public CompletableFuture<Void> sendTaskAssigned(
            UUID taskId,
            String recipient
    ) {
        try {
            emailClient.send(recipient, "Task assigned: " + taskId);
            return CompletableFuture.completedFuture(null);
        } catch (Exception exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }
}
~~~

Async chạy qua proxy. Self-invocation không tạo async task. Không truyền JPA entity detached vào async; truyền ID/immutable command rồi load dữ liệu trong worker nếu cần.

Request thread không nên chờ CompletableFuture nếu mục tiêu là giảm latency. Nếu client cần biết task hoàn thành, dùng queue/job status thay vì fire-and-forget.

### 10.2. Exception handling

Với CompletableFuture, exception nằm trong future và caller phải xử lý. Với void async, dùng AsyncUncaughtExceptionHandler:

~~~java
@Bean
AsyncConfigurer asyncConfigurer() {
    return new AsyncConfigurer() {
        @Override
        public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
            return (exception, method, params) ->
                    log.error(
                        "async_failed method={}",
                        method.getName(),
                        exception
                    );
        }
    };
}
~~~

Không log token, password hoặc payload nhạy cảm khi log async failure. Gắn job ID/correlation ID để truy vết.

### 10.3. SecurityContext và MDC

SecurityContext, MDC và request scope không tự động an toàn khi sang thread khác. Nếu worker cần actor:

- Truyền actor ID rõ trong immutable command.
- Dùng TaskDecorator có kiểm soát để copy MDC.
- Không copy SecurityContext sang background job rồi dùng nó như quyền lâu dài.
- Re-authorize khi worker thực hiện hành động nhạy cảm.

---

## 11. Async event sau transaction

Không gửi notification trước khi database commit:

~~~java
@Transactional
public TaskEntity assignTask(UUID taskId, UUID assigneeId) {
    TaskEntity task = load(taskId);
    task.assignTo(assigneeId);
    applicationEventPublisher.publishEvent(
            new TaskAssignedEvent(taskId, assigneeId)
    );
    return task;
}

@Component
public class TaskNotificationListener {

    private final NotificationService notifications;
    private static final Logger log =
            LoggerFactory.getLogger(TaskNotificationListener.class);

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTaskAssigned(TaskAssignedEvent event) {
        notifications.sendTaskAssigned(
                event.taskId(),
                event.assigneeId().toString()
        ).whenComplete((ignored, error) -> {
            if (error != null) {
                log.error("notification_failed taskId={}", event.taskId(), error);
            }
        });
    }
}
~~~

Nếu email provider lỗi, transaction database đã commit. Callback chỉ giúp quan sát lỗi; cần retry durable/outbox nếu notification bắt buộc. Application event in-memory có thể mất khi process crash; outbox/queue mới phù hợp delivery guarantee.

---

## 12. Scheduling

Bật scheduling:

~~~java
@Configuration
@EnableScheduling
public class SchedulingConfiguration {
}
~~~

Các kiểu lịch:

| Annotation | Ý nghĩa |
|---|---|
| fixedDelay | Đợi method kết thúc rồi đợi delay |
| fixedRate | Bắt đầu theo khoảng cố định |
| cron | Lịch biểu đạt bằng cron |
| initialDelay | Trì hoãn lần chạy đầu |

Ví dụ đóng task quá hạn:

~~~java
@Component
public class ExpiredTaskJob {

    private final TaskService taskService;

    public ExpiredTaskJob(TaskService taskService) {
        this.taskService = taskService;
    }

    @Scheduled(
        cron = "${jobs.expire-tasks.cron}",
        zone = "Asia/Ho_Chi_Minh"
    )
    public void expireTasks() {
        taskService.closeExpiredTasks();
    }
}
~~~

Cấu hình thực tế đưa cron vào environment/profile. Ví dụ local:

~~~yaml
jobs:
  expire-tasks:
    cron: "0 */5 * * * *"
~~~

Cron timezone phải document. Mặc định scheduler có thể dùng một thread; job chạy lâu có thể làm job khác trễ. Cấu hình TaskScheduler riêng:

~~~java
@Bean
ThreadPoolTaskScheduler taskScheduler() {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(4);
    scheduler.setThreadNamePrefix("scheduled-");
    scheduler.setWaitForTasksToCompleteOnShutdown(true);
    scheduler.setAwaitTerminationSeconds(30);
    scheduler.initialize();
    return scheduler;
}
~~~

Nếu nhiều job quan trọng hoặc retry phức tạp, dùng queue/job framework thay vì nhồi mọi việc vào Scheduled.

---

## 13. Idempotency cho scheduled job

Job có thể chạy lại do retry, restart, overlap hoặc nhiều instance. Mỗi job cần idempotent:

~~~java
@Transactional
public int closeExpiredTasks() {
    return taskRepository.closeExpiredTasks(
            Instant.now(clock),
            TaskStatus.DONE,
            TaskStatus.EXPIRED
    );
}
~~~

Bulk update theo điều kiện trạng thái khiến chạy lại không đổi row đã xử lý. Không gửi notification dựa trên “method được gọi” nếu cùng row có thể xử lý hai lần; lưu job event/idempotency key.

Với job tạo record:

~~~sql
CREATE TABLE job_runs (
    job_name     VARCHAR(100) NOT NULL,
    run_key      VARCHAR(200) NOT NULL,
    started_at   TIMESTAMPTZ NOT NULL,
    finished_at  TIMESTAMPTZ,
    status       VARCHAR(30) NOT NULL,
    PRIMARY KEY (job_name, run_key)
);
~~~

Unique key làm lần chạy lại nhận conflict thay vì tạo duplicate. Business operation cũng cần unique constraint phù hợp.

---

## 14. Chống chạy trùng nhiều instance

Spring Scheduled chạy trên từng instance. Với 3 pod, cùng cron chạy 3 lần.

### Distributed lock

Dùng ShedLock hoặc lock table PostgreSQL/Redis:

~~~text
acquire lock(job-name, lease)
    nếu thành công → chạy
    nếu thất bại → skip
release/expire lock
~~~

Lock phải có lease để instance chết không khóa vĩnh viễn. Thời gian lock lớn hơn runtime dự kiến nhưng nhỏ hơn khoảng retry vận hành.

### Database advisory/row lock

PostgreSQL advisory lock phù hợp job ngắn và một database trung tâm. Cần kiểm tra lock release khi connection/transaction kết thúc.

### Queue/worker

Scheduler chỉ enqueue job; worker consumer xử lý với visibility timeout, retry và dead-letter. Phù hợp công việc lớn hoặc cần delivery guarantee.

Distributed lock không thay idempotency. Luôn thiết kế job an toàn khi chạy lặp.

---

## 15. Retry và exponential backoff

Retry phù hợp lỗi transient: timeout, connection reset, HTTP 503. Không retry validation, 401, 403, duplicate business key hoặc lỗi không thể thành công.

Backoff có jitter:

~~~java
int boundedAttempt = Math.min(Math.max(attempt, 0), 10);
Duration delay = Duration.ofMillis(
        Math.min(30_000, baseMillis * (1L << boundedAttempt))
);

Duration jitter = Duration.ofMillis(
        ThreadLocalRandom.current().nextLong(delay.toMillis() + 1)
);
~~~

Trong thực tế dùng thư viện retry có cấu hình max attempts, max delay và jitter. Mỗi retry có thể nhân tải dependency; luôn có giới hạn và circuit/bulkhead ở tuần 15.

Retry phải idempotent. Gửi email/payment hai lần là side effect nguy hiểm. Dùng idempotency key ở provider hoặc outbox event có trạng thái.

Không dùng Thread.sleep trong request thread để retry nhiều lần. Với async/job, delay trong queue hoặc scheduler giúp giải phóng worker.

---

## 16. Cache và async cùng nhau

Pattern cache-aside + async refresh:

~~~text
cache hit → trả ngay
cache miss → một worker load/refresh
nhiều request → không cùng đánh database
~~~

Cẩn thận:

- Async refresh có thể ghi kết quả cũ sau khi request update đã evict.
- Gắn version/updatedAt vào value để bỏ qua kết quả cũ.
- Invalidate sau commit.
- Không để worker không giới hạn tạo cache stampede.
- Redis key phải bao gồm tenant/locale/filter phù hợp.

Nếu consistency quan trọng, ưu tiên write path cập nhật database rồi evict/put sau commit; background refresh chỉ là tối ưu.

---

## 17. Test cache, async và scheduling

### 17.1. Cache test

- Lần đầu cache miss gọi repository một lần.
- Lần thứ hai cùng key không gọi repository.
- Update/evict làm lần đọc tiếp theo thấy dữ liệu mới.
- TTL hết hạn thì value được load lại.
- Key khác tenant/user không trả nhầm dữ liệu.
- Redis outage theo đúng degradation policy.

Dùng Testcontainers Redis cho integration test serialization/TTL. Không chỉ dùng ConcurrentMap rồi kết luận Redis behavior đúng.

### 17.2. Async test

- Gửi notification không block request lâu hơn contract.
- Exception trong CompletableFuture được quan sát.
- Thread pool có queue/rejection policy.
- MDC/job ID không lộ secret.
- Shutdown chờ task trong thời gian giới hạn.
- Retry không tạo duplicate side effect.

Không dùng Thread.sleep cố định để test async. Dùng Awaitility hoặc polling timeout có giới hạn.

### 17.3. Scheduled job test

- Gọi job method trực tiếp để test business logic.
- Test cron wiring riêng ở integration smoke test.
- Job chạy lại không tạo duplicate.
- Hai instance chỉ một instance giữ lock.
- Job đang chạy khi shutdown được xử lý graceful.
- Clock được inject để test thời điểm hết hạn.

---

## 18. Lỗi thường gặp

- Cache entity JPA rồi serialize lazy relationship.
- Cache key thiếu tenantId/userId/filter.
- Cache trước database commit.
- Cache list nhưng chỉ evict cache detail.
- TTL mọi key giống nhau gây avalanche.
- Async method self-invocation nên vẫn chạy đồng bộ.
- Dùng unbounded executor queue.
- Truyền EntityManager/JPA entity sang worker.
- Nuốt exception trong CompletableFuture.
- Scheduled chạy duplicate ở nhiều instance.
- Tin distributed lock nhưng không có idempotency.
- Retry non-idempotent payment/email.
- Retry 401/403/validation.
- Dùng Thread.sleep trong HTTP request.
- Log token, password, cache value nhạy cảm.
- Dùng local in-memory cache khi có nhiều pod mà không nói consistency policy.

---

## 19. Bài thực hành bắt buộc

1. Cache Project summary trong Redis với TTL.
2. Evict detail/list cache sau create/update/delete Project.
3. Viết integration test với PostgreSQL và Redis Testcontainers.
4. Tạo notification service với executor giới hạn.
5. Gửi notification sau transaction commit.
6. Test exception và retry của notification.
7. Tạo job đóng Task quá hạn bằng cron.
8. Đảm bảo job idempotent khi chạy lại.
9. Chạy hai instance local và chứng minh job bị duplicate.
10. Thêm distributed lock hoặc chuyển scheduler thành enqueue job.
11. Đặt rate/backpressure cho async executor.
12. Đo cache hit ratio, job duration và queue rejection để chuẩn bị cho observability tuần 15.

---

## 20. Câu hỏi tự kiểm tra

1. Cache-aside hoạt động theo những bước nào?
2. Cacheable khác CachePut thế nào?
3. Vì sao cache entity JPA là rủi ro?
4. Khi nào evict sau commit?
5. Cache stampede và cache penetration khác nhau thế nào?
6. Vì sao TTL không thay thế invalidation?
7. Async chạy qua proxy ra sao?
8. Vì sao thread pool phải bounded?
9. SecurityContext có tự truyền sang async thread không?
10. fixedDelay khác fixedRate thế nào?
11. Vì sao Scheduled chạy trùng ở nhiều instance?
12. Distributed lock có thay idempotency không?
13. Khi nào retry an toàn?
14. Vì sao exponential backoff cần jitter?
15. Khi nào dùng queue/outbox thay Async?
16. Test async/scheduled mà không dùng sleep cố định thế nào?

---

## 21. Tiêu chí hoàn thành tuần 14

- [ ] Cache Project summary bằng Redis.
- [ ] Có TTL, key prefix và serialization policy.
- [ ] Có cache invalidation sau update/delete.
- [ ] Cache không chứa entity/lazy proxy hoặc secret.
- [ ] Có policy xử lý Redis outage.
- [ ] Có async executor bounded và rejection policy.
- [ ] Notification chạy sau transaction commit.
- [ ] Async exception được quan sát và retry có giới hạn.
- [ ] Scheduled job đóng Task quá hạn.
- [ ] Job idempotent khi chạy lại.
- [ ] Có distributed lock/queue strategy cho nhiều instance.
- [ ] Retry chỉ áp dụng transient/idempotent operation.
- [ ] Có integration test Redis và test query/job.
- [ ] Có kế hoạch metrics cho tuần 15.

---

## 22. Gợi ý lịch học 7 ngày

| Ngày | Nội dung | Kết quả |
|---|---|---|
| 1 | Cache abstraction và cache-aside | Cache được Project |
| 2 | Redis, TTL, serialization, invalidation | Cache consistency rõ |
| 3 | Stampede, penetration, outage | Có degradation policy |
| 4 | Async executor và CompletableFuture | Notification không block |
| 5 | Transactional event và retry | Side effect sau commit |
| 6 | Scheduled, idempotency, distributed lock | Job chạy an toàn nhiều instance |
| 7 | Test, benchmark và review | Hoàn thiện checklist |

Tuần 15 theo roadmap chuyển sang observability và resilience: Actuator, Micrometer, Prometheus, structured logging, trace ID, timeout, retry, circuit breaker và graceful shutdown.

## 23. Tài liệu tham khảo

- Spring Cache Annotations: https://docs.spring.io/spring-framework/reference/integration/cache/annotations.html
- Spring Scheduling and Async: https://docs.spring.io/spring-framework/reference/integration/scheduling.html
- Spring Boot Redis: https://docs.spring.io/spring-boot/reference/data/nosql.html
- Redis documentation: https://redis.io/docs/latest/
- ShedLock: https://github.com/lukas-krecan/ShedLock

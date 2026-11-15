# 7.8. Thực thi và lập lịch tác vụ (Task Execution and Scheduling)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.task-execution-and-scheduling>

Khi không có bean `Executor` nào trong context, Spring Boot tự động cấu hình một `AsyncTaskExecutor`. Khi virtual thread được bật (dùng Java 21+ và `spring.threads.virtual.enabled` được đặt thành `true`), đây sẽ là một `SimpleAsyncTaskExecutor` dùng virtual thread. Nếu không, nó sẽ là một `ThreadPoolTaskExecutor` với các giá trị mặc định hợp lý. Trong cả hai trường hợp, executor được tự động cấu hình sẽ được tự động dùng cho:

- thực thi tác vụ bất đồng bộ (`@EnableAsync`)
- việc xử lý bất đồng bộ các giá trị trả về `Callable` từ các phương thức controller của Spring for GraphQL
- xử lý request bất đồng bộ của Spring MVC
- hỗ trợ thực thi blocking của Spring WebFlux

> **MẸO (TIP):** Nếu bạn đã định nghĩa một `Executor` tùy chỉnh trong context, cả việc thực thi tác vụ thông thường (tức là `@EnableAsync`) và Spring for GraphQL đều sẽ dùng nó. Tuy nhiên, hỗ trợ của Spring MVC và Spring WebFlux sẽ chỉ dùng nó nếu nó là một implementation `AsyncTaskExecutor` (có tên `applicationTaskExecutor`). Tùy thuộc vào cách bố trí mục tiêu của bạn, bạn có thể đổi `Executor` của mình thành một `AsyncTaskExecutor` hoặc định nghĩa cả một `AsyncTaskExecutor` và một `AsyncConfigurer` bọc `Executor` tùy chỉnh của bạn.
>
> `ThreadPoolTaskExecutorBuilder` được tự động cấu hình cho phép bạn dễ dàng tạo các instance tái tạo lại những gì auto-configuration làm theo mặc định.

Khi một `ThreadPoolTaskExecutor` được tự động cấu hình, thread pool dùng 8 core thread, có thể tăng và giảm theo tải. Các thiết lập mặc định đó có thể được tinh chỉnh bằng namespace `spring.task.execution`, như trong ví dụ sau:

**Properties**

```properties
spring.task.execution.pool.max-size=16
spring.task.execution.pool.queue-capacity=100
spring.task.execution.pool.keep-alive=10s
```

**Yaml**

```yaml
spring:
  task:
    execution:
      pool:
        max-size: 16
        queue-capacity: 100
        keep-alive: "10s"
```

Thiết lập này thay đổi thread pool để dùng một hàng đợi có giới hạn (bounded queue), sao cho khi hàng đợi đầy (100 tác vụ), thread pool sẽ tăng lên tối đa 16 thread. Việc thu nhỏ pool diễn ra mạnh hơn vì các thread được thu hồi khi chúng nhàn rỗi trong 10 giây (thay vì 60 giây theo mặc định).

Một scheduler cũng có thể được tự động cấu hình nếu cần gắn với việc thực thi tác vụ theo lịch (ví dụ dùng `@EnableScheduling`).

Nếu virtual thread được bật (dùng Java 21+ và `spring.threads.virtual.enabled` được đặt thành `true`), đây sẽ là một `SimpleAsyncTaskScheduler` dùng virtual thread. `SimpleAsyncTaskScheduler` này sẽ bỏ qua mọi property liên quan đến pooling.

Nếu virtual thread không được bật, nó sẽ là một `ThreadPoolTaskScheduler` với các giá trị mặc định hợp lý. `ThreadPoolTaskScheduler` mặc định dùng một thread và các thiết lập của nó có thể được tinh chỉnh bằng namespace `spring.task.scheduling`, như trong ví dụ sau:

**Properties**

```properties
spring.task.scheduling.thread-name-prefix=scheduling-
spring.task.scheduling.pool.size=2
```

**Yaml**

```yaml
spring:
  task:
    scheduling:
      thread-name-prefix: "scheduling-"
      pool:
        size: 2
```

Một bean `ThreadPoolTaskExecutorBuilder`, một bean `SimpleAsyncTaskExecutorBuilder`, một bean `ThreadPoolTaskSchedulerBuilder` và một `SimpleAsyncTaskSchedulerBuilder` được cung cấp sẵn trong context nếu cần tạo một executor hoặc scheduler tùy chỉnh. Các bean `SimpleAsyncTaskExecutorBuilder` và `SimpleAsyncTaskSchedulerBuilder` được tự động cấu hình để dùng virtual thread nếu chúng được bật (dùng Java 21+ và `spring.threads.virtual.enabled` được đặt thành `true`).

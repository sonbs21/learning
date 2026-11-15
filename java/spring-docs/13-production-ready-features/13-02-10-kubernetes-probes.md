# 13.2.10. Kubernetes Probe (Kubernetes Probes)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.endpoints.kubernetes-probes>

Các ứng dụng được triển khai trên Kubernetes có thể cung cấp thông tin về trạng thái nội bộ của chúng bằng [Container Probe](https://kubernetes.io/docs/concepts/workloads/pods/pod-lifecycle/#container-probes). Tùy thuộc vào [cấu hình Kubernetes của bạn](https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/), kubelet gọi các probe đó và phản ứng với kết quả.

Theo mặc định, Spring Boot quản lý [Trạng thái sẵn sàng của ứng dụng (Application Availability State)](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.spring-application.application-availability). Nếu được triển khai trong môi trường Kubernetes, actuator thu thập thông tin "Liveness" và "Readiness" từ interface `ApplicationAvailability` và dùng thông tin đó trong các [health indicator](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.endpoints.health.auto-configured-health-indicators) chuyên biệt: `LivenessStateHealthIndicator` và `ReadinessStateHealthIndicator`. Các indicator này được hiển thị trên endpoint health toàn cục (`"/actuator/health"`). Chúng cũng được expose như các HTTP Probe riêng biệt bằng cách dùng [nhóm health](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.endpoints.health.groups): `"/actuator/health/liveness"` và `"/actuator/health/readiness"`.

Sau đó, bạn có thể cấu hình hạ tầng Kubernetes với thông tin endpoint sau:

```yaml
livenessProbe:
  httpGet:
    path: "/actuator/health/liveness"
    port: <actuator-port>
  failureThreshold: ...
  periodSeconds: ...

readinessProbe:
  httpGet:
    path: "/actuator/health/readiness"
    port: <actuator-port>
  failureThreshold: ...
  periodSeconds: ...
```

> **LƯU Ý (NOTE):** `<actuator-port>` nên được đặt thành cổng mà các actuator endpoint có sẵn trên đó. Nó có thể là cổng web server chính hoặc một cổng management riêng nếu property `"management.server.port"` đã được đặt.

Các nhóm health này chỉ được tự động bật nếu ứng dụng [chạy trong môi trường Kubernetes](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#deployment.cloud.kubernetes). Bạn có thể bật chúng trong bất kỳ môi trường nào bằng configuration property `management.endpoint.health.probes.enabled`.

> **LƯU Ý (NOTE):** Nếu một ứng dụng mất nhiều thời gian để khởi động hơn khoảng thời gian liveness đã cấu hình, Kubernetes đề cập đến `"startupProbe"` như một giải pháp khả thi. Nói chung, `"startupProbe"` không nhất thiết cần ở đây, vì `"readinessProbe"` thất bại cho đến khi tất cả các tác vụ khởi động hoàn tất. Điều này có nghĩa là ứng dụng của bạn sẽ không nhận traffic cho đến khi nó sẵn sàng. Tuy nhiên, nếu ứng dụng của bạn mất nhiều thời gian để khởi động, hãy cân nhắc dùng `"startupProbe"` để đảm bảo Kubernetes sẽ không giết ứng dụng khi nó đang trong quá trình khởi động. Xem phần mô tả [cách các probe hoạt động trong vòng đời ứng dụng](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.endpoints.kubernetes-probes.lifecycle).

Nếu các Actuator endpoint của bạn được triển khai trên một management context riêng, các endpoint không dùng cùng hạ tầng web (cổng, connection pool, các component framework) như ứng dụng chính. Trong trường hợp này, một lần kiểm tra probe có thể thành công ngay cả khi ứng dụng chính không hoạt động đúng (ví dụ, nó không thể chấp nhận kết nối mới). Vì lý do này, nên làm cho các nhóm health `liveness` và `readiness` khả dụng trên cổng server chính. Điều này có thể được thực hiện bằng cách đặt property sau:

```properties
management.endpoint.health.probes.add-additional-paths=true
```

Điều này sẽ làm cho nhóm `liveness` khả dụng tại `/livez` và nhóm `readiness` khả dụng tại `/readyz` trên cổng server chính. Các đường dẫn có thể được tùy chỉnh bằng property `additional-path` trên mỗi nhóm, xem [nhóm health](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.endpoints.health.groups) để biết chi tiết.

## Kiểm tra trạng thái bên ngoài với Kubernetes Probe (Checking External State With Kubernetes Probes)

Actuator cấu hình các probe "liveness" và "readiness" như các Nhóm Health. Điều này có nghĩa là tất cả [các tính năng của nhóm health](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.endpoints.health.groups) đều có sẵn cho chúng. Ví dụ, bạn có thể cấu hình thêm Health Indicator:

**Properties**

```properties
management.endpoint.health.group.readiness.include=readinessState,customCheck
```

**Yaml**

```yaml
management:
  endpoint:
    health:
      group:
        readiness:
          include: "readinessState,customCheck"
```

Theo mặc định, Spring Boot không thêm các health indicator khác vào các nhóm này.

Probe "liveness" không nên phụ thuộc vào các health check cho hệ thống bên ngoài. Nếu [trạng thái liveness của ứng dụng](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.spring-application.application-availability.liveness) bị hỏng, Kubernetes cố gắng giải quyết vấn đề đó bằng cách khởi động lại instance ứng dụng. Điều này có nghĩa là nếu một hệ thống bên ngoài (như database, Web API, hoặc cache bên ngoài) gặp lỗi, Kubernetes có thể khởi động lại tất cả các instance ứng dụng và tạo ra lỗi dây chuyền.

Đối với probe "readiness", việc lựa chọn kiểm tra hệ thống bên ngoài phải được lập trình viên ứng dụng đưa ra một cách cẩn thận. Vì lý do này, Spring Boot không bao gồm bất kỳ health check bổ sung nào trong probe readiness. Nếu [trạng thái readiness của một instance ứng dụng](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.spring-application.application-availability.readiness) là chưa sẵn sàng, Kubernetes không định tuyến traffic tới instance đó. Một số hệ thống bên ngoài có thể không được chia sẻ giữa các instance ứng dụng, khi đó chúng có thể được đưa vào probe readiness. Các hệ thống bên ngoài khác có thể không thiết yếu đối với ứng dụng (ứng dụng có thể có circuit breaker và fallback), khi đó chắc chắn không nên đưa chúng vào. Đáng tiếc là một hệ thống bên ngoài được chia sẻ bởi tất cả các instance ứng dụng là phổ biến, và bạn phải đưa ra quyết định: Đưa nó vào probe readiness và chấp nhận rằng ứng dụng sẽ bị đưa ra khỏi dịch vụ khi service bên ngoài bị sập, hoặc để nó ngoài và xử lý lỗi ở tầng cao hơn, có thể bằng cách dùng circuit breaker ở bên gọi.

> **LƯU Ý (NOTE):** Nếu tất cả các instance của một ứng dụng đều chưa sẵn sàng, một Kubernetes Service với `type=ClusterIP` hoặc `NodePort` sẽ không chấp nhận bất kỳ kết nối đến nào. Không có response lỗi HTTP nào (503, v.v.), vì không có kết nối. Một service với `type=LoadBalancer` có thể chấp nhận hoặc không chấp nhận kết nối, tùy thuộc vào nhà cung cấp. Một service có [ingress](https://kubernetes.io/docs/concepts/services-networking/ingress/) tường minh cũng phản hồi theo cách phụ thuộc vào implementation — chính service ingress phải quyết định cách xử lý "connection refused" từ phía dưới. HTTP 503 khá có khả năng xảy ra trong trường hợp của cả load balancer và ingress.

Ngoài ra, nếu một ứng dụng dùng [autoscaling](https://kubernetes.io/docs/tasks/run-application/horizontal-pod-autoscale/) của Kubernetes, nó có thể phản ứng khác nhau với việc ứng dụng bị đưa ra khỏi load-balancer, tùy thuộc vào cấu hình autoscaler của nó.

## Vòng đời ứng dụng và trạng thái probe (Application Lifecycle and Probe States)

Một khía cạnh quan trọng của hỗ trợ Kubernetes Probe là tính nhất quán của nó với vòng đời ứng dụng. Có sự khác biệt đáng kể giữa `AvailabilityState` (là trạng thái nội bộ, in-memory của ứng dụng) và probe thực tế (expose trạng thái đó). Tùy thuộc vào giai đoạn của vòng đời ứng dụng, probe có thể không khả dụng.

Spring Boot publish [các application event trong quá trình khởi động và tắt](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.spring-application.application-events-and-listeners), và các probe có thể lắng nghe các event đó và expose thông tin `AvailabilityState`.

Các bảng sau cho thấy `AvailabilityState` và trạng thái của các HTTP connector ở các giai đoạn khác nhau.

Khi một ứng dụng Spring Boot khởi động:

| Giai đoạn khởi động | LivenessState | ReadinessState | HTTP server | Ghi chú |
|---|---|---|---|---|
| Starting | `BROKEN` | `REFUSING_TRAFFIC` | Chưa khởi động | Kubernetes kiểm tra Probe "liveness" và khởi động lại ứng dụng nếu nó mất quá nhiều thời gian. |
| Started | `CORRECT` | `REFUSING_TRAFFIC` | Từ chối request | Application context đã được refresh. Ứng dụng thực hiện các tác vụ khởi động và chưa nhận traffic. |
| Ready | `CORRECT` | `ACCEPTING_TRAFFIC` | Chấp nhận request | Các tác vụ khởi động đã hoàn tất. Ứng dụng đang nhận traffic. |

Khi một ứng dụng Spring Boot tắt:

| Giai đoạn tắt | Liveness State | Readiness State | HTTP server | Ghi chú |
|---|---|---|---|---|
| Running | `CORRECT` | `ACCEPTING_TRAFFIC` | Chấp nhận request | Việc tắt đã được yêu cầu. |
| Graceful shutdown | `CORRECT` | `REFUSING_TRAFFIC` | Các request mới bị từ chối | Nếu được bật, [graceful shutdown xử lý các request đang thực hiện dở](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#web.graceful-shutdown). |
| Shutdown complete | N/A | N/A | Server đã tắt | Application context đã đóng và ứng dụng đã tắt. |

> **MẸO (TIP):** Xem [phần vòng đời container Kubernetes](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#deployment.cloud.kubernetes.container-lifecycle) để biết thêm thông tin về triển khai Kubernetes.

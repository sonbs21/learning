# Amazon ECS — Knowledge Map & Learning Checklist

> Tổng hợp theo 24 mục trong mục lục AWS Amazon ECS Developer Guide (ảnh cung cấp). Đây là **bản đồ kiến thức/checklist học tập**, không phải bản sao toàn văn AWS Docs. Cập nhật tham khảo: 2026-10-23.
>
> Tài liệu gốc: https://docs.aws.amazon.com/AmazonECS/latest/developerguide/Welcome.html

## 1. What is Amazon ECS?
- [ ] ECS (Elastic Container Service): dịch vụ điều phối container (container orchestration) được AWS quản lý.
- [ ] Phân biệt **Cluster**, **Task Definition**, **Task**, **Service**, **Container**, **Capacity Provider**.
- [ ] Phân biệt ECS với EKS, Docker thuần và AWS Lambda.
- [ ] Vòng đời ứng dụng: source → Docker image → ECR → task definition → ECS service → endpoint.
- [ ] Mô hình trách nhiệm chung (shared responsibility) và thành phần tính phí.

## 2. Getting started
- [ ] Chuẩn bị AWS account, IAM, VPC, subnets, security groups, AWS CLI.
- [ ] Dockerize ứng dụng (ví dụ Java Spring Boot), tạo Dockerfile và health endpoint.
- [ ] Tạo ECR repository, build/tag/push container image.
- [ ] Tạo ECS cluster, task definition và service.
- [ ] Cấu hình Fargate, networking, CloudWatch Logs và Application Load Balancer (ALB).
- [ ] Kiểm tra deployment, logs, health checks, truy cập endpoint; xóa tài nguyên để tránh phí.

## 3. Amazon ECS with CloudFormation
- [ ] Infrastructure as Code (IaC): quản lý cluster, service, task definition, IAM, networking bằng CloudFormation.
- [ ] Tham số (Parameters), đầu ra (Outputs), tham chiếu (Ref/GetAtt), dependencies.
- [ ] Change Sets, drift detection, rollback và quản lý môi trường dev/staging/prod.
- [ ] Tách stack networking, IAM, compute; không hardcode secrets trong template.
- [ ] Triển khai bản cập nhật task definition và service có kiểm soát.

## 4. Best practices
- [ ] Thiết kế container bất biến (immutable), stateless, chạy non-root nếu có thể.
- [ ] Đặt CPU/memory hợp lý, kiểm thử tải, tránh OOM và CPU throttling.
- [ ] Dùng multi-AZ, health checks, auto scaling, deployment rollback.
- [ ] Cấp quyền tối thiểu (least privilege), quản lý secrets bằng Secrets Manager/SSM.
- [ ] Ghi logs ra stdout/stderr, tập trung quan sát metrics, traces, alerts.
- [ ] Pin image bằng digest khi cần tái lập; scan image và cập nhật dependencies.
- [ ] Dùng tagging, budgets, rightsizing, Spot phù hợp để tối ưu chi phí.

## 5. Architect your solution for Amazon ECS
- [ ] Chọn workload: web API, background worker, scheduled job, batch, daemon.
- [ ] Chọn Fargate, EC2, Managed Instances, ECS Anywhere theo kiểm soát và vận hành.
- [ ] Thiết kế VPC, public/private subnet, NAT/VPC endpoints, ALB/NLB.
- [ ] Chọn giao tiếp service-to-service: service discovery, Service Connect, load balancer.
- [ ] Thiết kế tính sẵn sàng (availability), mở rộng (scalability), DR, giới hạn quota.
- [ ] Phân biệt synchronous HTTP với asynchronous processing qua SQS/EventBridge.
- [ ] Tính toán chi phí compute, networking, storage, load balancer, logs.

## 6. Amazon ECS Managed Instances
- [ ] Hiểu chế độ ECS quản lý hạ tầng EC2 để chạy tasks, khác với EC2 launch type tự quản lý.
- [ ] Managed Instances capacity provider và cách chọn instance/capacity.
- [ ] Quyền IAM, mạng, cấu hình compute, quản lý vòng đời instance.
- [ ] Khả năng tích hợp daemon và các giới hạn/tính tương thích hiện hành.
- [ ] So sánh với Fargate về chi phí, kiểm soát máy chủ, hiệu năng và mức độ vận hành.

## 7. Amazon ECS Managed Daemons
- [ ] Agent chạy một bản trên mỗi EC2 instance thuộc Managed Instances capacity provider.
- [ ] Daemon task definition, daemon resource, rollout và cập nhật độc lập với application.
- [ ] Critical vs non-critical daemon: tác động tới instance khi daemon không khỏe.
- [ ] Use cases: security agent, monitoring, logging, networking agent.
- [ ] Rolling deployment, drain, circuit breaker, CloudWatch alarms và rollback.
- [ ] Phân biệt Managed Daemons với ECS service scheduling strategy `DAEMON` trên EC2.
- Docs: https://docs.aws.amazon.com/AmazonECS/latest/developerguide/managed-daemons.html

## 8. AWS Fargate
- [ ] Serverless compute cho ECS: không trực tiếp quản lý EC2 hosts.
- [ ] Fargate CPU/memory combinations, platform versions, ephemeral storage.
- [ ] Network mode `awsvpc`, ENI, subnet, security groups, public IP.
- [ ] Task execution role vs task role; image pull và logging.
- [ ] Fargate Spot: tiết kiệm chi phí nhưng có thể bị gián đoạn.
- [ ] Tối ưu startup time, image size, scaling và cost.

## 9. EC2
- [ ] ECS container instances, ECS agent, ECS-optimized AMI.
- [ ] EC2 Auto Scaling Group, capacity provider và managed scaling.
- [ ] Placement strategies: spread, binpack, random; placement constraints.
- [ ] Networking modes: `awsvpc`, `bridge`, `host` (theo khả năng hỗ trợ).
- [ ] CPU/memory/GPU, EBS/EFS, host resources và patching OS.
- [ ] Instance draining, rolling replacement và quản lý capacity.

## 10. Amazon ECS Anywhere
- [ ] Chạy ECS tasks trên external instances ngoài AWS (on-premises/hybrid).
- [ ] Đăng ký external instances, Systems Manager activation, ECS agent.
- [ ] Network connectivity, IAM, bảo mật, giám sát và giới hạn hỗ trợ.
- [ ] Phân biệt workload hybrid với Fargate và ECS EC2 trong AWS.

## 11. Amazon ECS Express Mode
- [ ] Triển khai web/API container với cấu hình đơn giản hóa.
- [ ] Input cơ bản: container image, task execution role, infrastructure role.
- [ ] ECS tự cấu hình Fargate service, HTTPS endpoint, load balancer, autoscaling, monitoring, networking.
- [ ] Tùy chỉnh CPU/memory, environment variables, secrets, task role, VPC, health check.
- [ ] Hiểu tài nguyên tạo trong AWS account, quyền chỉnh sửa và chi phí hạ tầng.
- [ ] Phân biệt Express Mode với ECS service cấu hình thủ công.
- Docs: https://docs.aws.amazon.com/AmazonECS/latest/developerguide/express-service-overview.html

## 12. Amazon ECS Gateway
- [ ] Gateway tập trung quản lý ingress cho nhiều ECS services.
- [ ] Gateway → cluster association → service gateway connector → gateway route.
- [ ] ECS quản lý ALB, listener, target group, security groups, certificates, DNS.
- [ ] Host-based routing, HTTPS, `awsvpc`, public/private access.
- [ ] AWS WAF, health checks, logs, quotas và chi phí underlying resources.
- [ ] Giới hạn: không dùng Gateway cho Express Mode services; xem tài liệu trước khi triển khai.
- Docs: https://docs.aws.amazon.com/AmazonECS/latest/developerguide/gateway-overview.html

## 13. Task definitions
- [ ] Task definition family/revision; JSON schema và versioning.
- [ ] Container definitions: image, command, entryPoint, ports, environment, secrets.
- [ ] `cpu`, `memory`, `memoryReservation`, runtime platform, OS/architecture.
- [ ] Task role vs execution role và IAM permissions.
- [ ] Log configuration (`awslogs`/FireLens), healthCheck, container dependencies.
- [ ] Volumes: ephemeral, EFS/EBS khi được hỗ trợ; mount points, read-only filesystem.
- [ ] Multi-container tasks, sidecars, essential container, resource isolation.
- [ ] Ví dụ Spring Boot: port 8080, `/actuator/health`, JVM container memory settings.

## 14. Clusters
- [ ] Cluster là ranh giới logic để nhóm ECS tasks/services/capacity.
- [ ] Tạo, cập nhật, xóa cluster; cluster settings.
- [ ] Capacity providers, default capacity provider strategy.
- [ ] Container Insights và observability cấp cluster.
- [ ] Tổ chức cluster theo environment, ownership, workload, isolation.

## 15. Schedule your containers
- [ ] ECS Service: giữ desired count của long-running tasks.
- [ ] Standalone Task (`RunTask`): chạy tác vụ một lần.
- [ ] Scheduled Task với EventBridge Scheduler cho cron/rate.
- [ ] Task placement, capacity provider strategy, AZ balancing.
- [ ] Service deployment: rolling, circuit breaker, rollback và các chiến lược được hỗ trợ.
- [ ] Autoscaling theo CPU, memory, request metrics hoặc custom metrics.
- [ ] Retry, idempotency, timeout, DLQ cho scheduled/background workloads.

## 16. Tagging resources
- [ ] AWS tags dạng key-value cho cluster, service, task definition, tasks và tài nguyên liên quan.
- [ ] Cost allocation tags, ownership, environment, application, compliance.
- [ ] Tag propagation và IAM tag-based access control (ABAC).
- [ ] Quy ước tên tags, chính sách bắt buộc và kiểm tra tagging.

## 17. Monitoring
- [ ] CloudWatch Logs: log groups, streams, retention, structured JSON logs.
- [ ] CloudWatch metrics: CPU, memory, running/pending task count, service health.
- [ ] Container Insights, application metrics và dashboards.
- [ ] ECS events, EventBridge, deployment events, CloudTrail audit.
- [ ] Distributed tracing (OpenTelemetry/X-Ray), correlation IDs.
- [ ] Alerts: task stopped, OOM, unhealthy target, deployment failure, latency/error rate.

## 18. Amazon ECS MCP server
- [ ] MCP (Model Context Protocol) cho công cụ AI tương tác với ECS theo khả năng server cung cấp.
- [ ] Phân biệt đọc trạng thái, sinh cấu hình và hành động thay đổi tài nguyên.
- [ ] IAM least privilege, phê duyệt thao tác, audit logs, secrets hygiene.
- [ ] Kiểm tra phiên bản, nguồn phân phối và hướng dẫn chính thức trước khi sử dụng.

## 19. Troubleshooting
- [ ] Task stuck `PENDING`/`PROVISIONING`: thiếu capacity, subnet IP, permissions.
- [ ] Task `STOPPED`: kiểm tra stopped reason, container exit code, logs.
- [ ] `CannotPullContainerError`: image/tag, ECR permissions, network/VPC endpoints.
- [ ] `ResourceInitializationError`: secrets, CloudWatch Logs, ECR/network.
- [ ] Container health check vs ALB target health check, grace period.
- [ ] OOMKilled, CPU throttling, app crash, incorrect port binding.
- [ ] ECS Exec, CloudWatch, service events, VPC Flow Logs để debug.

## 20. Security
- [ ] IAM task role, task execution role, service-linked role; least privilege.
- [ ] Private subnets, security groups, network segmentation, VPC endpoints.
- [ ] Secrets Manager/SSM Parameter Store, encryption in transit/at rest.
- [ ] ECR image scanning, software supply chain, non-root, read-only filesystem.
- [ ] ECS Exec audit và quyền truy cập; CloudTrail và AWS Config khi phù hợp.
- [ ] Phân tách môi trường và tài khoản AWS; bảo vệ credentials.
- [ ] Fargate/EC2 isolation differences và trách nhiệm cập nhật host.

## 21. Tutorials
- [ ] Lab 1: Dockerize Spring Boot và push image lên ECR.
- [ ] Lab 2: Deploy ECS Fargate service sau ALB, health checks.
- [ ] Lab 3: Rolling deployment và rollback khi image lỗi.
- [ ] Lab 4: Auto Scaling và load test.
- [ ] Lab 5: Scheduled task xử lý batch job.
- [ ] Lab 6: SQS consumer trên ECS, scale theo backlog.
- [ ] Lab 7: Secrets Manager + IAM task role + CloudWatch Logs.
- [ ] Lab 8: CloudFormation hoặc Terraform để tái tạo môi trường.

## 22. Service quotas
- [ ] Quotas về clusters, services, tasks, task definitions, capacity providers.
- [ ] Quotas riêng cho Fargate vCPU, ENIs/IPs, ALB, ECS Gateway.
- [ ] Kiểm tra Service Quotas console và khả năng yêu cầu tăng quota.
- [ ] Thiết kế scale có tính tới AWS account/Region quotas.
- [ ] Tránh hardcode quota: giới hạn có thể thay đổi theo thời gian và Region.

## 23. Using the API for Amazon ECS
- [ ] AWS CLI/SDK và ECS API: `CreateCluster`, `RegisterTaskDefinition`.
- [ ] `CreateService`, `UpdateService`, `DescribeServices`, `DeleteService`.
- [ ] `RunTask`, `StopTask`, `DescribeTasks`, `ListTasks`.
- [ ] Pagination, throttling, retries, idempotency và IAM permissions.
- [ ] Automate deployment trong GitLab CI/CD, GitHub Actions hoặc CodePipeline.
- [ ] Theo dõi eventual consistency và chờ trạng thái ổn định khi triển khai.
- API Reference: https://docs.aws.amazon.com/AmazonECS/latest/APIReference/Welcome.html

## 24. Document history
- [ ] Theo dõi thay đổi AWS ECS Developer Guide, tính năng mới và deprecations.
- [ ] Kiểm tra ngày cập nhật, availability theo Region, pricing, limits.
- [ ] Rà soát lại architecture và runbooks khi AWS thay đổi tính năng.
- [ ] Cập nhật checklist này định kỳ dựa trên docs chính thức.

---

## Lộ trình học đề xuất

1. **Nền tảng:** 1 → 2 → 13 → 14 → 8 → 15.
2. **Production:** 5 → 4 → 20 → 17 → 19 → 22.
3. **Triển khai tự động:** 3 → 23 → 16 → 21.
4. **Nâng cao/chọn compute:** 9 → 6 → 7 → 10 → 11 → 12 → 18 → 24.

## Sơ đồ kiến trúc tham khảo: Spring Boot API trên ECS Fargate

```text
Developer → GitLab CI/CD → Build Docker image → Amazon ECR
                                              ↓
Internet → HTTPS / ALB → ECS Service → ECS Task (Spring Boot)
                             │                │
                             │                ├─ IAM Task Role → S3/DynamoDB/SQS
                             │                ├─ Secrets Manager / SSM
                             │                └─ CloudWatch Logs
                             └─ Service Auto Scaling
```

**Câu hỏi tự kiểm tra:** Khi nào chọn Fargate thay EC2? Vì sao ECS Task Role khác Execution Role? Khi task chạy nhưng ALB trả 503 phải kiểm tra gì? Làm thế nào rollback deployment? Làm sao scale worker theo SQS queue depth? Làm sao chạy cron job mà không cần server riêng?

## Nguồn chính
- [Amazon ECS Developer Guide](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/Welcome.html)
- [Amazon ECS API Reference](https://docs.aws.amazon.com/AmazonECS/latest/APIReference/Welcome.html)
- [ECS Managed Daemons](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/managed-daemons.html)
- [ECS Express Mode](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/express-service-overview.html)
- [ECS Gateway](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/gateway-overview.html)

# 13.12.1. Tắt hỗ trợ actuator mở rộng cho Cloud Foundry (Disabling Extended Cloud Foundry Actuator Support)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.cloud-foundry.disable>

Nếu bạn muốn tắt hoàn toàn các endpoint `/cloudfoundryapplication`, bạn có thể thêm thiết lập sau vào file `application.properties`:

**Properties**

```properties
management.cloudfoundry.enabled=false
```

**Yaml**

```yaml
management:
  cloudfoundry:
    enabled: false
```

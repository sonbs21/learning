# 13.12.2. Certificate tự ký trên Cloud Foundry (Cloud Foundry Self-signed Certificates)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.cloud-foundry.ssl>

Theo mặc định, việc xác minh bảo mật cho các endpoint `/cloudfoundryapplication` thực hiện các lời gọi SSL tới nhiều service Cloud Foundry khác nhau. Nếu các service UAA hoặc Cloud Controller của Cloud Foundry dùng certificate tự ký (self-signed), bạn cần đặt property sau:

**Properties**

```properties
management.cloudfoundry.skip-ssl-validation=true
```

**Yaml**

```yaml
management:
  cloudfoundry:
    skip-ssl-validation: true
```

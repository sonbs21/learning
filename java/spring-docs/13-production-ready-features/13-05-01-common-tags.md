# 13.5.1. Tag chung (Common tags)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.observability.common-tags>

Tag chung thường được dùng để đi sâu theo chiều (dimensional drill-down) vào môi trường vận hành, như host, instance, region, stack, và các thông tin khác. Tag chung được áp dụng cho tất cả các observation dưới dạng tag có cardinality thấp và có thể được cấu hình, như ví dụ sau cho thấy:

**Properties**

```properties
management.observations.key-values.region=us-east-1
management.observations.key-values.stack=prod
```

**Yaml**

```yaml
management:
  observations:
    key-values:
      region: "us-east-1"
      stack: "prod"
```

Ví dụ trên thêm các tag `region` và `stack` vào tất cả các observation với giá trị lần lượt là `us-east-1` và `prod`.

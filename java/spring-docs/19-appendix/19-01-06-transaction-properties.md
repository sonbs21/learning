# A.6. Property transaction (Transaction Properties)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#appendix.application-properties.transaction>

| Tên | Mô tả | Giá trị mặc định |
|---|---|---|
| [`spring.jta.enabled`](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#application-properties.transaction.spring.jta.enabled) | Có bật hỗ trợ JTA hay không. | `true` |
| [`spring.transaction.default-timeout`](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#application-properties.transaction.spring.transaction.default-timeout) | Timeout mặc định của transaction. Nếu không chỉ định hậu tố thời lượng, đơn vị giây sẽ được dùng. |  |
| [`spring.transaction.rollback-on-commit-failure`](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#application-properties.transaction.spring.transaction.rollback-on-commit-failure) | Có rollback khi commit thất bại hay không. |  |

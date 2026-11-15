# 8.6.3. Hỗ trợ repository Querydsl và QueryByExample (Querydsl and QueryByExample Repositories Support)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#web.graphql.data-query>

Spring Data cung cấp hỗ trợ cho cả repository Querydsl và QueryByExample. Spring GraphQL có thể [cấu hình các repository Querydsl và QueryByExample làm `DataFetcher`](https://docs.spring.io/spring-graphql/reference/1.2/#data).

Các repository Spring Data được đánh annotation `@GraphQlRepository` và kế thừa một trong các interface sau:

- `QuerydslPredicateExecutor`
- `ReactiveQuerydslPredicateExecutor`
- `QueryByExampleExecutor`
- `ReactiveQueryByExampleExecutor`

sẽ được Spring Boot phát hiện và được coi là ứng viên làm `DataFetcher` cho các query cấp cao nhất phù hợp.

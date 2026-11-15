# 8.6. Spring for GraphQL

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#web.graphql>

Nếu bạn muốn xây dựng các ứng dụng GraphQL, bạn có thể tận dụng auto-configuration của Spring Boot cho [Spring for GraphQL](https://spring.io/projects/spring-graphql). Project Spring for GraphQL dựa trên [GraphQL Java](https://github.com/graphql-java/graphql-java). Tối thiểu bạn sẽ cần starter `spring-boot-starter-graphql`. Vì GraphQL không phụ thuộc vào transport, bạn cũng sẽ cần có một hoặc nhiều starter bổ sung trong ứng dụng để expose GraphQL API qua web:

| Starter | Transport | Implementation |
|---|---|---|
| `spring-boot-starter-web` | HTTP | Spring MVC |
| `spring-boot-starter-websocket` | WebSocket | WebSocket cho ứng dụng Servlet |
| `spring-boot-starter-webflux` | HTTP, WebSocket | Spring WebFlux |
| `spring-boot-starter-rsocket` | TCP, WebSocket | Spring WebFlux trên Reactor Netty |

# 8.6.5. Xử lý exception (Exception Handling)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#web.graphql.exception-handling>

Spring GraphQL cho phép ứng dụng đăng ký một hoặc nhiều component `DataFetcherExceptionResolver` của Spring, được gọi tuần tự. Exception phải được phân giải thành một danh sách các object `graphql.GraphQLError`, xem [tài liệu xử lý exception của Spring GraphQL](https://docs.spring.io/spring-graphql/reference/1.2/#execution-exceptions). Spring Boot sẽ tự động phát hiện các bean `DataFetcherExceptionResolver` và đăng ký chúng với `GraphQlSource.Builder`.

# 8.6.6. GraphiQL và Schema printer (GraphiQL and Schema printer)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#web.graphql.graphiql>

Spring GraphQL cung cấp hạ tầng để hỗ trợ lập trình viên khi sử dụng hoặc phát triển một GraphQL API.

Spring GraphQL đi kèm một trang [GraphiQL](https://github.com/graphql/graphiql) mặc định, được expose tại `"/graphiql"` theo mặc định. Trang này mặc định bị tắt và có thể được bật bằng property `spring.graphql.graphiql.enabled`. Nhiều ứng dụng expose trang như vậy sẽ thích một bản build tùy chỉnh hơn. Một implementation mặc định rất hữu ích trong quá trình phát triển, đó là lý do nó được tự động expose cùng [`spring-boot-devtools`](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.devtools) trong quá trình phát triển.

Bạn cũng có thể chọn expose GraphQL schema ở định dạng văn bản tại `/graphql/schema` khi property `spring.graphql.schema.printer.enabled` được bật.

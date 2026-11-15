# 8.6.1. GraphQL Schema

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#web.graphql.schema>

Một ứng dụng Spring GraphQL yêu cầu một schema đã được định nghĩa khi khởi động. Theo mặc định, bạn có thể viết các file schema ".graphqls" hoặc ".gqls" bên dưới `src/main/resources/graphql/**` và Spring Boot sẽ tự động nhận diện chúng. Bạn có thể tùy chỉnh các vị trí bằng `spring.graphql.schema.locations` và phần mở rộng file bằng `spring.graphql.schema.file-extensions`.

> **LƯU Ý (NOTE):** Nếu bạn muốn Spring Boot phát hiện các file schema trong tất cả các module và dependency của ứng dụng cho vị trí đó, bạn có thể đặt `spring.graphql.schema.locations` thành `"classpath*:graphql/**/"` (lưu ý tiền tố `classpath*:`).

Trong các phần tiếp theo, chúng ta sẽ xét schema GraphQL mẫu này, định nghĩa hai type và hai query:

```json
type Query {
    greeting(name: String! = "Spring"): String!
    project(slug: ID!): Project
}

""" A Project in the Spring portfolio """
type Project {
    """ Unique string id used in URLs """
    slug: ID!
    """ Project name """
    name: String!
    """ URL of the git repository """
    repositoryUrl: String!
    """ Current support status """
    status: ProjectStatus!
}

enum ProjectStatus {
    """ Actively supported by the Spring team """
    ACTIVE
    """ Supported by the community """
    COMMUNITY
    """ Prototype, not officially supported yet  """
    INCUBATING
    """ Project being retired, in maintenance mode """
    ATTIC
    """ End-Of-Lifed """
    EOL
}
```

> **LƯU Ý (NOTE):** Theo mặc định, [field introspection](https://spec.graphql.org/draft/#sec-Introspection) sẽ được cho phép trên schema vì nó cần thiết cho các công cụ như GraphiQL. Nếu bạn không muốn expose thông tin về schema, bạn có thể tắt introspection bằng cách đặt `spring.graphql.schema.introspection.enabled` thành `false`.

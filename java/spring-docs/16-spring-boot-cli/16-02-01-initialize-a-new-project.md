# 16.2.1. Khởi tạo một project mới (Initialize a New Project)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#cli.using-the-cli.initialize-new-project>

Lệnh `init` cho phép bạn tạo một project mới bằng [start.spring.io](https://start.spring.io) mà không cần rời khỏi shell, như trong ví dụ sau:

```shell
$ spring init --dependencies=web,data-jpa my-project
Using service at https://start.spring.io
Project extracted to '/Users/developer/example/my-project'
```

Ví dụ trên tạo một thư mục `my-project` với một project dựa trên Maven dùng `spring-boot-starter-web` và `spring-boot-starter-data-jpa`. Bạn có thể liệt kê các khả năng của service bằng cờ `--list`, như trong ví dụ sau:

```shell
$ spring init --list
=======================================
Capabilities of https://start.spring.io
=======================================

Available dependencies:
-----------------------
actuator - Actuator: Production ready features to help you monitor and manage your application
...
web - Web: Support for full-stack web development, including Tomcat and spring-webmvc
websocket - Websocket: Support for WebSocket development
ws - WS: Support for Spring Web Services

Available project types:
------------------------
gradle-build -  Gradle Config [format:build, build:gradle]
gradle-project -  Gradle Project [format:project, build:gradle]
maven-build -  Maven POM [format:build, build:maven]
maven-project -  Maven Project [format:project, build:maven] (default)

...
```

Lệnh `init` hỗ trợ nhiều tùy chọn. Xem output của `help` để biết thêm chi tiết. Ví dụ, lệnh sau tạo một project Gradle dùng Java 17 và đóng gói `war`:

```shell
$ spring init --build=gradle --java-version=17 --dependencies=websocket --packaging=war sample-app.zip
Using service at https://start.spring.io
Content saved to 'sample-app.zip'
```

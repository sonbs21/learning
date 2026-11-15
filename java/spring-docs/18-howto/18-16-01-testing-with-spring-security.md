# 18.16.1. Kiểm thử với Spring Security (Testing With Spring Security)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.testing.with-spring-security>

Spring Security cung cấp hỗ trợ chạy test dưới danh nghĩa một người dùng cụ thể. Ví dụ, test trong đoạn code dưới đây sẽ chạy với một người dùng đã được xác thực có role `ADMIN`.

**Java**

```java
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@WebMvcTest(UserController.class)
class MySecurityTests {

    @Autowired
    private MockMvc mvc;

    @Test
    @WithMockUser(roles = "ADMIN")
    void requestProtectedUrlWithUser() throws Exception {
        this.mvc.perform(get("/"));
    }

}
```

**Kotlin**

```kotlin
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders

@WebMvcTest(UserController::class)
class MySecurityTests(@Autowired val mvc: MockMvc) {

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun requestProtectedUrlWithUser() {
        mvc.perform(MockMvcRequestBuilders.get("/"))
    }

}
```

Spring Security cung cấp tích hợp toàn diện với Spring MVC Test, và điều này cũng có thể được dùng khi kiểm thử controller bằng slice `@WebMvcTest` và `MockMvc`.

Để biết thêm chi tiết về hỗ trợ kiểm thử của Spring Security, xem [tài liệu tham khảo](https://docs.spring.io/spring-security/reference/6.2/servlet/test/index.html) của Spring Security.

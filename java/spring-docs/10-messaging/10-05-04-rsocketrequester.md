# 10.5.4. Gọi RSocket service bằng RSocketRequester (Calling RSocket Services with RSocketRequester)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#messaging.rsocket.requester>

Sau khi kênh `RSocket` được thiết lập giữa server và client, bất kỳ bên nào cũng có thể gửi hoặc nhận request tới bên kia.

Ở vai trò server, bạn có thể được inject một instance `RSocketRequester` trên bất kỳ phương thức handler nào của một `@Controller` RSocket. Ở vai trò client, trước tiên bạn cần cấu hình và thiết lập một kết nối RSocket. Spring Boot tự động cấu hình một `RSocketRequester.Builder` cho những trường hợp như vậy với các codec mong đợi và áp dụng mọi bean `RSocketConnectorConfigurer`.

Instance `RSocketRequester.Builder` là một prototype bean, nghĩa là mỗi điểm inject sẽ cung cấp cho bạn một instance mới. Điều này được làm có chủ đích vì builder này có trạng thái (stateful) và bạn không nên tạo các requester với các thiết lập khác nhau bằng cùng một instance.

Đoạn code sau cho thấy một ví dụ điển hình:

**Java**

```java
import reactor.core.publisher.Mono;

import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.stereotype.Service;

@Service
public class MyService {

    private final RSocketRequester rsocketRequester;

    public MyService(RSocketRequester.Builder rsocketRequesterBuilder) {
        this.rsocketRequester = rsocketRequesterBuilder.tcp("example.org", 9898);
    }

    public Mono<User> someRSocketCall(String name) {
        return this.rsocketRequester.route("user").data(name).retrieveMono(User.class);
    }

}
```

**Kotlin**

```kotlin
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

@Service
class MyService(rsocketRequesterBuilder: RSocketRequester.Builder) {

    private val rsocketRequester: RSocketRequester

    init {
        rsocketRequester = rsocketRequesterBuilder.tcp("example.org", 9898)
    }

    fun someRSocketCall(name: String): Mono<User> {
        return rsocketRequester.route("user").data(name).retrieveMono(
            User::class.java
        )
    }

}
```

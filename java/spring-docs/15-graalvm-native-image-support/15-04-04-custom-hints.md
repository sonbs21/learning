# 15.4.4. Gợi ý tùy chỉnh (Custom Hints)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#native-image.advanced.custom-hints>

Nếu bạn cần cung cấp các gợi ý của riêng mình cho reflection, resource, serialization, việc sử dụng proxy, v.v., bạn có thể dùng API `RuntimeHintsRegistrar`. Hãy tạo một class hiện thực interface `RuntimeHintsRegistrar`, rồi thực hiện các lời gọi phù hợp tới instance `RuntimeHints` được cung cấp:

```java
import java.lang.reflect.Method;

import org.springframework.aot.hint.ExecutableMode;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.util.ReflectionUtils;

public class MyRuntimeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        // Register method for reflection
        Method method = ReflectionUtils.findMethod(MyClass.class, "sayHello", String.class);
        hints.reflection().registerMethod(method, ExecutableMode.INVOKE);

        // Register resources
        hints.resources().registerPattern("my-resource.txt");

        // Register serialization
        hints.serialization().registerType(MySerializableClass.class);

        // Register proxy
        hints.proxies().registerJdkProxy(MyInterface.class);
    }

}
```

Sau đó, bạn có thể dùng `@ImportRuntimeHints` trên bất kỳ class `@Configuration` nào (ví dụ class ứng dụng được đánh annotation `@SpringBootApplication` của bạn) để kích hoạt các gợi ý đó.

Nếu bạn có các class cần bind (chủ yếu cần khi serialize hoặc deserialize JSON), bạn có thể dùng [`@RegisterReflectionForBinding`](https://docs.spring.io/spring-framework/reference/6.1/core/aot.html#aot.hints.register-reflection-for-binding) trên bất kỳ bean nào. Hầu hết các gợi ý đều được suy ra tự động, ví dụ khi nhận hoặc trả về dữ liệu từ một phương thức `@RestController`. Nhưng khi bạn làm việc trực tiếp với `WebClient`, `RestClient` hoặc `RestTemplate`, bạn có thể cần dùng `@RegisterReflectionForBinding`.

## Kiểm thử gợi ý tùy chỉnh (Testing custom hints)

API `RuntimeHintsPredicates` có thể được dùng để kiểm thử các gợi ý của bạn. API này cung cấp các phương thức xây dựng một `Predicate` có thể dùng để kiểm thử một instance `RuntimeHints`.

Nếu bạn đang dùng AssertJ, test của bạn sẽ trông như thế này:

```java
import org.junit.jupiter.api.Test;

import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;
import org.springframework.boot.docs.nativeimage.advanced.customhints.MyRuntimeHints;

import static org.assertj.core.api.Assertions.assertThat;

class MyRuntimeHintsTests {

    @Test
    void shouldRegisterHints() {
        RuntimeHints hints = new RuntimeHints();
        new MyRuntimeHints().registerHints(hints, getClass().getClassLoader());
        assertThat(RuntimeHintsPredicates.resource().forResource("my-resource.txt")).accepts(hints);
    }

}
```

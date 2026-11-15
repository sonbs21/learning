# 18.1.3. Tùy chỉnh Environment hoặc ApplicationContext trước khi nó khởi động (Customize the Environment or ApplicationContext Before It Starts)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.application.customize-the-environment-or-application-context>

Một `SpringApplication` có các `ApplicationListeners` và `ApplicationContextInitializers` được dùng để áp dụng các tùy chỉnh cho context hoặc environment. Spring Boot nạp một số tùy chỉnh như vậy để dùng nội bộ từ `META-INF/spring.factories`. Có nhiều hơn một cách để đăng ký các tùy chỉnh bổ sung:

- Bằng lập trình, cho từng ứng dụng, bằng cách gọi các phương thức `addListeners` và `addInitializers` trên `SpringApplication` trước khi chạy nó.
- Bằng khai báo, cho tất cả ứng dụng, bằng cách thêm một `META-INF/spring.factories` và đóng gói một file jar mà tất cả các ứng dụng đều dùng làm thư viện.

`SpringApplication` gửi một số `ApplicationEvents` đặc biệt tới các listener (một số thậm chí trước khi context được tạo) và sau đó cũng đăng ký các listener cho các event được `ApplicationContext` publish. Xem "[Application Events và Listeners](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.spring-application.application-events-and-listeners)" trong phần 'Các tính năng của Spring Boot' để có danh sách đầy đủ.

Cũng có thể tùy chỉnh `Environment` trước khi application context được refresh bằng cách dùng `EnvironmentPostProcessor`. Mỗi implementation nên được đăng ký trong `META-INF/spring.factories`, như trong ví dụ sau:

```
org.springframework.boot.env.EnvironmentPostProcessor=com.example.YourEnvironmentPostProcessor
```

Implementation có thể nạp các file tùy ý và thêm chúng vào `Environment`. Chẳng hạn, ví dụ sau nạp một file cấu hình YAML từ classpath:

**Java**

```java
import java.io.IOException;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;

public class MyEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private final YamlPropertySourceLoader loader = new YamlPropertySourceLoader();

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Resource path = new ClassPathResource("com/example/myapp/config.yml");
        PropertySource<?> propertySource = loadYaml(path);
        environment.getPropertySources().addLast(propertySource);
    }

    private PropertySource<?> loadYaml(Resource path) {
        Assert.isTrue(path.exists(), () -> "Resource " + path + " does not exist");
        try {
            return this.loader.load("custom-resource", path).get(0);
        }
        catch (IOException ex) {
            throw new IllegalStateException("Failed to load yaml configuration from " + path, ex);
        }
    }

}
```

**Kotlin**

```kotlin
import org.springframework.boot.SpringApplication
import org.springframework.boot.env.EnvironmentPostProcessor
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.PropertySource
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.Resource
import org.springframework.util.Assert
import java.io.IOException

class MyEnvironmentPostProcessor : EnvironmentPostProcessor {

    private val loader = YamlPropertySourceLoader()

    override fun postProcessEnvironment(environment: ConfigurableEnvironment, application: SpringApplication) {
        val path: Resource = ClassPathResource("com/example/myapp/config.yml")
        val propertySource = loadYaml(path)
        environment.propertySources.addLast(propertySource)
    }

    private fun loadYaml(path: Resource): PropertySource<*> {
        Assert.isTrue(path.exists()) { "Resource $path does not exist" }
        return try {
            loader.load("custom-resource", path)[0]
        } catch (ex: IOException) {
            throw IllegalStateException("Failed to load yaml configuration from $path", ex)
        }
    }

}
```

> **MẸO (TIP):** `Environment` đã được chuẩn bị sẵn với tất cả các property source thông thường mà Spring Boot nạp theo mặc định. Do đó có thể lấy vị trí của file từ environment. Ví dụ trên thêm property source `custom-resource` vào cuối danh sách để một key được định nghĩa ở bất kỳ vị trí thông thường nào khác sẽ được ưu tiên hơn. Một implementation tùy chỉnh có thể định nghĩa một thứ tự khác.

> **THẬN TRỌNG (CAUTION):** Mặc dù dùng `@PropertySource` trên `@SpringBootApplication` có vẻ là một cách tiện lợi để nạp một resource tùy chỉnh vào `Environment`, chúng tôi không khuyến nghị điều đó. Các property source như vậy chỉ được thêm vào `Environment` khi application context đang được refresh. Lúc đó đã quá muộn để cấu hình một số property nhất định như `logging.*` và `spring.main.*`, vốn được đọc trước khi quá trình refresh bắt đầu.

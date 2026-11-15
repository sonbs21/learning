# 6.5. Spring Bean và Dependency Injection (Spring Beans and Dependency Injection)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.spring-beans-and-dependency-injection>

Bạn có thể tự do dùng bất kỳ kỹ thuật tiêu chuẩn nào của Spring Framework để định nghĩa các bean và các dependency được inject của chúng. Chúng tôi thường khuyên dùng constructor injection để kết nối (wire up) các dependency và `@ComponentScan` để tìm bean.

Nếu bạn tổ chức code như gợi ý ở trên (đặt class ứng dụng trong package trên cùng), bạn có thể thêm `@ComponentScan` mà không cần tham số nào, hoặc dùng annotation `@SpringBootApplication` vốn đã ngầm bao gồm nó. Tất cả các component của ứng dụng (`@Component`, `@Service`, `@Repository`, `@Controller`, và các loại khác) đều được tự động đăng ký thành Spring Bean.

Ví dụ sau cho thấy một Bean `@Service` dùng constructor injection để lấy bean `RiskAssessor` cần thiết:

**Java**

```java
import org.springframework.stereotype.Service;

@Service
public class MyAccountService implements AccountService {

    private final RiskAssessor riskAssessor;

    public MyAccountService(RiskAssessor riskAssessor) {
        this.riskAssessor = riskAssessor;
    }

    // ...

}
```

**Kotlin**

```kotlin
import org.springframework.stereotype.Service

@Service
class MyAccountService(private val riskAssessor: RiskAssessor) : AccountService
```

Nếu một bean có nhiều hơn một constructor, bạn cần đánh dấu constructor mà bạn muốn Spring sử dụng bằng `@Autowired`:

**Java**

```java
import java.io.PrintStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MyAccountService implements AccountService {

    private final RiskAssessor riskAssessor;

    private final PrintStream out;

    @Autowired
    public MyAccountService(RiskAssessor riskAssessor) {
        this.riskAssessor = riskAssessor;
        this.out = System.out;
    }

    public MyAccountService(RiskAssessor riskAssessor, PrintStream out) {
        this.riskAssessor = riskAssessor;
        this.out = out;
    }

    // ...

}
```

**Kotlin**

```kotlin
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.io.PrintStream

@Service
class MyAccountService : AccountService {

    private val riskAssessor: RiskAssessor

    private val out: PrintStream

    @Autowired
    constructor(riskAssessor: RiskAssessor) {
        this.riskAssessor = riskAssessor
        out = System.out
    }

    constructor(riskAssessor: RiskAssessor, out: PrintStream) {
        this.riskAssessor = riskAssessor
        this.out = out
    }

    // ...

}
```

> **MẸO (TIP):** Hãy để ý cách dùng constructor injection cho phép field `riskAssessor` được đánh dấu là `final`, cho biết rằng nó không thể bị thay đổi về sau.

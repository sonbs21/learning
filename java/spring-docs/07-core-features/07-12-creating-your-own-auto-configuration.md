# 7.12. Tự tạo auto-configuration (Creating Your Own Auto-configuration)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.developing-auto-configuration>

Nếu bạn làm việc ở một công ty phát triển các thư viện dùng chung, hoặc nếu bạn làm việc trên một thư viện mã nguồn mở hoặc thương mại, bạn có thể muốn phát triển auto-configuration của riêng mình. Các class auto-configuration có thể được đóng gói trong các jar bên ngoài và vẫn được Spring Boot nhận diện.

Auto-configuration có thể được gắn với một "starter" cung cấp code auto-configuration cũng như các thư viện điển hình mà bạn sẽ dùng cùng với nó. Trước tiên, chúng tôi đề cập đến những gì bạn cần biết để xây dựng auto-configuration của riêng mình, rồi chuyển sang [các bước điển hình cần thiết để tạo một starter tùy chỉnh](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.developing-auto-configuration.custom-starter).

## 7.12.1. Hiểu về các bean được tự động cấu hình (Understanding Auto-configured Beans)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.developing-auto-configuration.understanding-auto-configured-beans>

Các class hiện thực auto-configuration được đánh annotation `@AutoConfiguration`. Bản thân annotation này được meta-annotate bằng `@Configuration`, khiến các auto-configuration trở thành các class `@Configuration` tiêu chuẩn. Các annotation `@Conditional` bổ sung được dùng để ràng buộc khi nào auto-configuration nên được áp dụng. Thông thường, các class auto-configuration dùng các annotation `@ConditionalOnClass` và `@ConditionalOnMissingBean`. Điều này đảm bảo auto-configuration chỉ được áp dụng khi tìm thấy các class liên quan và khi bạn chưa khai báo `@Configuration` của riêng mình.

Bạn có thể duyệt mã nguồn của [`spring-boot-autoconfigure`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-autoconfigure/src/main/java/org/springframework/boot/autoconfigure) để xem các class `@AutoConfiguration` mà Spring cung cấp (xem file [`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-autoconfigure/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports)).

## 7.12.2. Định vị các ứng viên auto-configuration (Locating Auto-configuration Candidates)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.developing-auto-configuration.locating-auto-configuration-candidates>

Spring Boot kiểm tra sự hiện diện của file `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` bên trong jar đã publish của bạn. File này nên liệt kê các class cấu hình của bạn, mỗi dòng một tên class, như trong ví dụ sau:

```
com.mycorp.libx.autoconfigure.LibXAutoConfiguration
com.mycorp.libx.autoconfigure.LibXWebAutoConfiguration
```

> **MẸO (TIP):** Bạn có thể thêm comment vào file imports bằng ký tự `#`.

> **LƯU Ý (NOTE):** Các auto-configuration phải được nạp *chỉ* bằng cách được nêu tên trong file imports. Hãy đảm bảo chúng được định nghĩa trong một không gian package cụ thể và chúng không bao giờ là mục tiêu của component scanning. Hơn nữa, các class auto-configuration không nên bật component scanning để tìm thêm component. Thay vào đó nên dùng các annotation `@Import` cụ thể.

Nếu cấu hình của bạn cần được áp dụng theo một thứ tự cụ thể, bạn có thể dùng các thuộc tính `before`, `beforeName`, `after` và `afterName` trên annotation [`@AutoConfiguration`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-autoconfigure/src/main/java/org/springframework/boot/autoconfigure/AutoConfiguration.java) hoặc các annotation chuyên biệt [`@AutoConfigureBefore`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-autoconfigure/src/main/java/org/springframework/boot/autoconfigure/AutoConfigureBefore.java) và [`@AutoConfigureAfter`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-autoconfigure/src/main/java/org/springframework/boot/autoconfigure/AutoConfigureAfter.java). Ví dụ, nếu bạn cung cấp cấu hình đặc thù cho web, class của bạn có thể cần được áp dụng sau `WebMvcAutoConfiguration`.

Nếu bạn muốn sắp thứ tự một số auto-configuration không nên biết trực tiếp về nhau, bạn cũng có thể dùng `@AutoConfigureOrder`. Annotation đó có cùng ngữ nghĩa với annotation `@Order` thông thường nhưng cung cấp một thứ tự chuyên biệt cho các class auto-configuration.

Cũng như với các class `@Configuration` tiêu chuẩn, thứ tự áp dụng các class auto-configuration chỉ ảnh hưởng đến thứ tự định nghĩa các bean của chúng. Thứ tự các bean đó được tạo ra sau đó không bị ảnh hưởng và được xác định bởi các dependency của mỗi bean và mọi quan hệ `@DependsOn`.

## 7.12.3. Các annotation điều kiện (Condition Annotations)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.developing-auto-configuration.condition-annotations>

Hầu như lúc nào bạn cũng muốn thêm một hoặc nhiều annotation `@Conditional` vào class auto-configuration. Annotation `@ConditionalOnMissingBean` là một ví dụ phổ biến, được dùng để cho phép lập trình viên ghi đè auto-configuration nếu họ không hài lòng với các giá trị mặc định của bạn.

Spring Boot bao gồm một số annotation `@Conditional` mà bạn có thể tái sử dụng trong code của mình bằng cách đánh annotation lên các class `@Configuration` hoặc các phương thức `@Bean` riêng lẻ. Các annotation này bao gồm:

- [Điều kiện về class (Class Conditions)](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.developing-auto-configuration.condition-annotations.class-conditions)
- [Điều kiện về bean (Bean Conditions)](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.developing-auto-configuration.condition-annotations.bean-conditions)
- [Điều kiện về property (Property Conditions)](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.developing-auto-configuration.condition-annotations.property-conditions)
- [Điều kiện về resource (Resource Conditions)](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.developing-auto-configuration.condition-annotations.resource-conditions)
- [Điều kiện về ứng dụng web (Web Application Conditions)](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.developing-auto-configuration.condition-annotations.web-application-conditions)
- [Điều kiện dùng biểu thức SpEL (SpEL Expression Conditions)](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.developing-auto-configuration.condition-annotations.spel-conditions)

### Điều kiện về class (Class Conditions)

Các annotation `@ConditionalOnClass` và `@ConditionalOnMissingClass` cho phép các class `@Configuration` được đưa vào dựa trên sự hiện diện hoặc vắng mặt của các class cụ thể. Do metadata của annotation được phân tích bằng [ASM](https://asm.ow2.io/), bạn có thể dùng thuộc tính `value` để tham chiếu tới class thực, ngay cả khi class đó có thể không thực sự xuất hiện trên classpath của ứng dụng đang chạy. Bạn cũng có thể dùng thuộc tính `name` nếu muốn chỉ định tên class bằng một giá trị `String`.

Cơ chế này không áp dụng theo cùng cách cho các phương thức `@Bean`, nơi kiểu trả về thường là mục tiêu của điều kiện: trước khi điều kiện trên phương thức được áp dụng, JVM sẽ đã nạp class và có thể đã xử lý các tham chiếu phương thức, điều này sẽ thất bại nếu class không tồn tại.

Để xử lý tình huống này, có thể dùng một class `@Configuration` riêng để cô lập điều kiện, như trong ví dụ sau:

**Java**

```java
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@AutoConfiguration
// Some conditions ...
public class MyAutoConfiguration {

    // Auto-configured beans ...

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(SomeService.class)
    public static class SomeServiceConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public SomeService someService() {
            return new SomeService();
        }

    }

}
```

**Kotlin**

```kotlin
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
// Some conditions ...
class MyAutoConfiguration {

    // Auto-configured beans ...
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(SomeService::class)
    class SomeServiceConfiguration {

        @Bean
        @ConditionalOnMissingBean
        fun someService(): SomeService {
            return SomeService()
        }

    }

}
```

> **MẸO (TIP):** Nếu bạn dùng `@ConditionalOnClass` hoặc `@ConditionalOnMissingClass` như một phần của meta-annotation để tạo các annotation tổng hợp của riêng mình, bạn phải dùng `name`, vì việc tham chiếu tới class trong trường hợp như vậy không được xử lý.

### Điều kiện về bean (Bean Conditions)

Các annotation `@ConditionalOnBean` và `@ConditionalOnMissingBean` cho phép một bean được đưa vào dựa trên sự hiện diện hoặc vắng mặt của các bean cụ thể. Bạn có thể dùng thuộc tính `value` để chỉ định bean theo kiểu hoặc `name` để chỉ định bean theo tên. Thuộc tính `search` cho phép bạn giới hạn cây phân cấp `ApplicationContext` nên được xem xét khi tìm kiếm bean.

Khi được đặt trên một phương thức `@Bean`, kiểu đích mặc định là kiểu trả về của phương thức, như trong ví dụ sau:

**Java**

```java
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class MyAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SomeService someService() {
        return new SomeService();
    }

}
```

**Kotlin**

```kotlin
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class MyAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    fun someService(): SomeService {
        return SomeService()
    }

}
```

Trong ví dụ trên, bean `someService` sẽ được tạo nếu chưa có bean nào kiểu `SomeService` nằm trong `ApplicationContext`.

> **MẸO (TIP):** Bạn cần rất cẩn thận về thứ tự thêm các bean definition, vì các điều kiện này được đánh giá dựa trên những gì đã được xử lý cho đến thời điểm đó. Vì lý do này, chúng tôi khuyên chỉ dùng các annotation `@ConditionalOnBean` và `@ConditionalOnMissingBean` trên các class auto-configuration (vì chúng được đảm bảo nạp sau khi mọi bean definition do người dùng định nghĩa đã được thêm vào).

> **LƯU Ý (NOTE):** `@ConditionalOnBean` và `@ConditionalOnMissingBean` không ngăn các class `@Configuration` được tạo ra. Sự khác biệt duy nhất giữa việc dùng các điều kiện này ở cấp class và việc đánh dấu từng phương thức `@Bean` bên trong bằng annotation là cách đầu tiên ngăn việc đăng ký class `@Configuration` thành một bean nếu điều kiện không khớp.

> **MẸO (TIP):** Khi khai báo một phương thức `@Bean`, hãy cung cấp càng nhiều thông tin kiểu càng tốt trong kiểu trả về của phương thức. Ví dụ, nếu class cụ thể của bean hiện thực một interface, kiểu trả về của phương thức bean nên là class cụ thể chứ không phải interface. Cung cấp càng nhiều thông tin kiểu càng tốt trong các phương thức `@Bean` đặc biệt quan trọng khi dùng điều kiện về bean, vì việc đánh giá chúng chỉ có thể dựa vào thông tin kiểu có sẵn trong chữ ký phương thức.

### Điều kiện về property (Property Conditions)

Annotation `@ConditionalOnProperty` cho phép cấu hình được đưa vào dựa trên một property của Spring Environment. Dùng các thuộc tính `prefix` và `name` để chỉ định property cần được kiểm tra. Theo mặc định, bất kỳ property nào tồn tại và không bằng `false` đều được coi là khớp. Bạn cũng có thể tạo các kiểm tra nâng cao hơn bằng các thuộc tính `havingValue` và `matchIfMissing`.

### Điều kiện về resource (Resource Conditions)

Annotation `@ConditionalOnResource` cho phép cấu hình chỉ được đưa vào khi một resource cụ thể tồn tại. Resource có thể được chỉ định bằng các quy ước thông thường của Spring, như trong ví dụ sau: `file:/home/user/test.dat`.

### Điều kiện về ứng dụng web (Web Application Conditions)

Các annotation `@ConditionalOnWebApplication` và `@ConditionalOnNotWebApplication` cho phép cấu hình được đưa vào tùy thuộc vào việc ứng dụng có phải là ứng dụng web hay không. Một ứng dụng web dựa trên servlet là bất kỳ ứng dụng nào dùng `WebApplicationContext` của Spring, định nghĩa scope `session`, hoặc có một `ConfigurableWebEnvironment`. Một ứng dụng web reactive là bất kỳ ứng dụng nào dùng `ReactiveWebApplicationContext`, hoặc có một `ConfigurableReactiveWebEnvironment`.

Các annotation `@ConditionalOnWarDeployment` và `@ConditionalOnNotWarDeployment` cho phép cấu hình được đưa vào tùy thuộc vào việc ứng dụng có phải là một ứng dụng WAR truyền thống được triển khai lên servlet container hay không. Điều kiện này sẽ không khớp với các ứng dụng chạy bằng web server nhúng.

### Điều kiện dùng biểu thức SpEL (SpEL Expression Conditions)

Annotation `@ConditionalOnExpression` cho phép cấu hình được đưa vào dựa trên kết quả của một [biểu thức SpEL](https://docs.spring.io/spring-framework/reference/6.1/core/expressions.html).

> **LƯU Ý (NOTE):** Tham chiếu tới một bean trong biểu thức sẽ khiến bean đó được khởi tạo từ rất sớm trong quá trình refresh context. Kết quả là, bean sẽ không đủ điều kiện cho việc hậu xử lý (post-processing) (chẳng hạn như bind configuration properties) và trạng thái của nó có thể không đầy đủ.

## 7.12.4. Kiểm thử auto-configuration (Testing your Auto-configuration)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.developing-auto-configuration.testing>

Một auto-configuration có thể bị ảnh hưởng bởi nhiều yếu tố: cấu hình của người dùng (định nghĩa `@Bean` và tùy chỉnh `Environment`), đánh giá điều kiện (sự hiện diện của một thư viện cụ thể), và các yếu tố khác. Cụ thể, mỗi test nên tạo một `ApplicationContext` được định nghĩa rõ ràng, đại diện cho một tổ hợp các tùy chỉnh đó. `ApplicationContextRunner` cung cấp một cách tuyệt vời để đạt được điều đó.

> **CẢNH BÁO (WARNING):** `ApplicationContextRunner` không hoạt động khi chạy test trong native image.

`ApplicationContextRunner` thường được định nghĩa như một field của class test để tập hợp cấu hình cơ sở, dùng chung. Ví dụ sau đảm bảo `MyServiceAutoConfiguration` luôn được gọi:

**Java**

```java
private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
    .withConfiguration(AutoConfigurations.of(MyServiceAutoConfiguration.class));
```

**Kotlin**

```kotlin
val contextRunner = ApplicationContextRunner()
    .withConfiguration(AutoConfigurations.of(MyServiceAutoConfiguration::class.java))
```

> **MẸO (TIP):** Nếu phải định nghĩa nhiều auto-configuration, không cần sắp thứ tự khai báo của chúng vì chúng được gọi theo đúng thứ tự như khi chạy ứng dụng.

Mỗi test có thể dùng runner để đại diện cho một trường hợp sử dụng cụ thể. Ví dụ, mẫu dưới đây gọi một cấu hình của người dùng (`UserConfiguration`) và kiểm tra rằng auto-configuration lùi lại đúng cách. Gọi `run` cung cấp một callback context có thể dùng với `AssertJ`.

**Java**

```java
@Test
void defaultServiceBacksOff() {
    this.contextRunner.withUserConfiguration(UserConfiguration.class).run((context) -> {
        assertThat(context).hasSingleBean(MyService.class);
        assertThat(context).getBean("myCustomService").isSameAs(context.getBean(MyService.class));
    });
}

@Configuration(proxyBeanMethods = false)
static class UserConfiguration {

    @Bean
    MyService myCustomService() {
        return new MyService("mine");
    }

}
```

**Kotlin**

```kotlin
@Test
fun defaultServiceBacksOff() {
    contextRunner.withUserConfiguration(UserConfiguration::class.java)
        .run { context: AssertableApplicationContext ->
            assertThat(context).hasSingleBean(MyService::class.java)
            assertThat(context).getBean("myCustomService")
                .isSameAs(context.getBean(MyService::class.java))
        }
}

@Configuration(proxyBeanMethods = false)
internal class UserConfiguration {

    @Bean
    fun myCustomService(): MyService {
        return MyService("mine")
    }

}
```

Cũng có thể dễ dàng tùy chỉnh `Environment`, như trong ví dụ sau:

**Java**

```java
@Test
void serviceNameCanBeConfigured() {
    this.contextRunner.withPropertyValues("user.name=test123").run((context) -> {
        assertThat(context).hasSingleBean(MyService.class);
        assertThat(context.getBean(MyService.class).getName()).isEqualTo("test123");
    });
}
```

**Kotlin**

```kotlin
@Test
fun serviceNameCanBeConfigured() {
    contextRunner.withPropertyValues("user.name=test123").run { context: AssertableApplicationContext ->
        assertThat(context).hasSingleBean(MyService::class.java)
        assertThat(context.getBean(MyService::class.java).name).isEqualTo("test123")
    }
}
```

Runner cũng có thể được dùng để hiển thị `ConditionEvaluationReport`. Báo cáo có thể được in ở mức `INFO` hoặc `DEBUG`. Ví dụ sau cho thấy cách dùng `ConditionEvaluationReportLoggingListener` để in báo cáo trong các test auto-configuration.

**Java**

```java
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.logging.ConditionEvaluationReportLoggingListener;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class MyConditionEvaluationReportingTests {

    @Test
    void autoConfigTest() {
        new ApplicationContextRunner()
            .withInitializer(ConditionEvaluationReportLoggingListener.forLogLevel(LogLevel.INFO))
            .run((context) -> {
                // Test something...
            });
    }

}
```

**Kotlin**

```kotlin
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.logging.ConditionEvaluationReportLoggingListener
import org.springframework.boot.logging.LogLevel
import org.springframework.boot.test.context.assertj.AssertableApplicationContext
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class MyConditionEvaluationReportingTests {

    @Test
    fun autoConfigTest() {
        ApplicationContextRunner()
            .withInitializer(ConditionEvaluationReportLoggingListener.forLogLevel(LogLevel.INFO))
            .run { context: AssertableApplicationContext? -> }
    }

}
```

### Mô phỏng web context (Simulating a Web Context)

Nếu bạn cần kiểm thử một auto-configuration chỉ hoạt động trong một application context web servlet hoặc reactive, hãy dùng lần lượt `WebApplicationContextRunner` hoặc `ReactiveWebApplicationContextRunner`.

### Ghi đè classpath (Overriding the Classpath)

Cũng có thể kiểm thử điều gì xảy ra khi một class và/hoặc package cụ thể không tồn tại tại runtime. Spring Boot đi kèm một `FilteredClassLoader` có thể dễ dàng được runner sử dụng. Trong ví dụ sau, chúng ta assert rằng nếu `MyService` không tồn tại, auto-configuration sẽ bị vô hiệu hóa đúng cách:

**Java**

```java
@Test
void serviceIsIgnoredIfLibraryIsNotPresent() {
    this.contextRunner.withClassLoader(new FilteredClassLoader(MyService.class))
        .run((context) -> assertThat(context).doesNotHaveBean("myService"));
}
```

**Kotlin**

```kotlin
@Test
fun serviceIsIgnoredIfLibraryIsNotPresent() {
    contextRunner.withClassLoader(FilteredClassLoader(MyService::class.java))
        .run { context: AssertableApplicationContext? ->
            assertThat(context).doesNotHaveBean("myService")
        }
}
```

## 7.12.5. Tự tạo Starter (Creating Your Own Starter)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.developing-auto-configuration.custom-starter>

Một starter Spring Boot điển hình chứa code để tự động cấu hình và tùy chỉnh hạ tầng của một công nghệ nhất định, hãy gọi nó là "acme". Để giúp nó dễ mở rộng, một số key cấu hình trong một namespace chuyên biệt có thể được expose ra environment. Cuối cùng, một dependency "starter" duy nhất được cung cấp để giúp người dùng bắt đầu dễ dàng nhất có thể.

Cụ thể, một starter tùy chỉnh có thể chứa những thứ sau:

- Module `autoconfigure` chứa code auto-configuration cho "acme".
- Module `starter` cung cấp dependency tới module `autoconfigure` cũng như "acme" và mọi dependency bổ sung thường hữu ích. Tóm lại, việc thêm starter nên cung cấp mọi thứ cần thiết để bắt đầu dùng thư viện đó.

Việc tách thành hai module này hoàn toàn không bắt buộc. Nếu "acme" có nhiều biến thể, tùy chọn hoặc tính năng tùy chọn, thì tốt hơn nên tách auto-configuration ra, vì bạn có thể thể hiện rõ ràng việc một số tính năng là tùy chọn. Bên cạnh đó, bạn có khả năng tạo ra một starter đưa ra quan điểm về các dependency tùy chọn đó. Đồng thời, những người khác có thể chỉ dựa vào module `autoconfigure` và tạo starter của riêng họ với các quan điểm khác.

Nếu auto-configuration tương đối đơn giản và không có tính năng tùy chọn, việc gộp hai module vào starter chắc chắn là một lựa chọn.

### Đặt tên (Naming)

Bạn nên đảm bảo cung cấp một namespace phù hợp cho starter của mình. Đừng bắt đầu tên module bằng `spring-boot`, ngay cả khi bạn dùng một `groupId` Maven khác. Chúng tôi có thể cung cấp hỗ trợ chính thức cho thứ mà bạn tự động cấu hình trong tương lai.

Theo kinh nghiệm chung, bạn nên đặt tên một module gộp theo tên starter. Ví dụ, giả sử bạn đang tạo một starter cho "acme" và bạn đặt tên module auto-configure là `acme-spring-boot` và starter là `acme-spring-boot-starter`. Nếu bạn chỉ có một module gộp cả hai, hãy đặt tên nó là `acme-spring-boot-starter`.

### Các key cấu hình (Configuration keys)

Nếu starter của bạn cung cấp các key cấu hình, hãy dùng một namespace duy nhất cho chúng. Cụ thể, đừng đưa các key của bạn vào các namespace mà Spring Boot sử dụng (chẳng hạn như `server`, `management`, `spring`, v.v.). Nếu bạn dùng cùng namespace, chúng tôi có thể sửa đổi các namespace này trong tương lai theo những cách làm hỏng module của bạn. Theo kinh nghiệm chung, hãy đặt tiền tố cho tất cả các key bằng một namespace mà bạn sở hữu (ví dụ `acme`).

Hãy đảm bảo các key cấu hình được tài liệu hóa bằng cách thêm javadoc cho field của mỗi property, như trong ví dụ sau:

**Java**

```java
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("acme")
public class AcmeProperties {

    /**
     * Whether to check the location of acme resources.
     */
    private boolean checkLocation = true;

    /**
     * Timeout for establishing a connection to the acme server.
     */
    private Duration loginTimeout = Duration.ofSeconds(3);

    // getters/setters ...

    public boolean isCheckLocation() {
        return this.checkLocation;
    }

    public void setCheckLocation(boolean checkLocation) {
        this.checkLocation = checkLocation;
    }

    public Duration getLoginTimeout() {
        return this.loginTimeout;
    }

    public void setLoginTimeout(Duration loginTimeout) {
        this.loginTimeout = loginTimeout;
    }

}
```

**Kotlin**

```kotlin
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("acme")
class AcmeProperties(

    /**
     * Whether to check the location of acme resources.
     */
    var isCheckLocation: Boolean = true,

    /**
     * Timeout for establishing a connection to the acme server.
     */
    var loginTimeout:Duration = Duration.ofSeconds(3))
```

> **LƯU Ý (NOTE):** Bạn chỉ nên dùng văn bản thuần (plain text) trong Javadoc của field `@ConfigurationProperties`, vì chúng không được xử lý trước khi được thêm vào JSON.

Dưới đây là một số quy tắc mà chúng tôi tuân theo nội bộ để đảm bảo các mô tả nhất quán:

- Đừng bắt đầu mô tả bằng "The" hoặc "A".
- Với các kiểu `boolean`, hãy bắt đầu mô tả bằng "Whether" hoặc "Enable".
- Với các kiểu dựa trên collection, hãy bắt đầu mô tả bằng "Comma-separated list".
- Dùng `java.time.Duration` thay vì `long` và mô tả đơn vị mặc định nếu nó khác mili giây, chẳng hạn như "If a duration suffix is not specified, seconds will be used".
- Đừng cung cấp giá trị mặc định trong mô tả trừ khi nó phải được xác định tại runtime.

Hãy đảm bảo [kích hoạt việc sinh metadata](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#appendix.configuration-metadata.annotation-processor) để hỗ trợ của IDE cũng khả dụng cho các key của bạn. Bạn có thể muốn xem lại metadata được sinh ra (`META-INF/spring-configuration-metadata.json`) để đảm bảo các key của bạn được tài liệu hóa đúng cách. Dùng starter của riêng bạn trong một IDE tương thích cũng là một ý hay để kiểm tra chất lượng của metadata.

### Module "autoconfigure" (The "autoconfigure" Module)

Module `autoconfigure` chứa mọi thứ cần thiết để bắt đầu với thư viện. Nó cũng có thể chứa các định nghĩa key cấu hình (chẳng hạn như `@ConfigurationProperties`) và mọi callback interface có thể dùng để tùy chỉnh thêm cách các component được khởi tạo.

> **MẸO (TIP):** Bạn nên đánh dấu các dependency tới thư viện là optional để có thể đưa module `autoconfigure` vào các project dễ dàng hơn. Nếu bạn làm theo cách đó, thư viện sẽ không được cung cấp và, theo mặc định, Spring Boot sẽ lùi lại.

Spring Boot dùng một annotation processor để thu thập các điều kiện của auto-configuration vào một file metadata (`META-INF/spring-autoconfigure-metadata.properties`). Nếu file đó tồn tại, nó được dùng để lọc sớm các auto-configuration không khớp, điều này sẽ cải thiện thời gian khởi động.

Khi build bằng Maven, khuyến nghị thêm dependency sau vào module chứa các auto-configuration:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-autoconfigure-processor</artifactId>
    <optional>true</optional>
</dependency>
```

Nếu bạn đã định nghĩa các auto-configuration trực tiếp trong ứng dụng, hãy đảm bảo cấu hình `spring-boot-maven-plugin` để ngăn goal `repackage` thêm dependency này vào uber jar:

```xml
<project>
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-autoconfigure-processor</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

Với Gradle, dependency nên được khai báo trong cấu hình `annotationProcessor`, như trong ví dụ sau:

```gradle
dependencies {
    annotationProcessor "org.springframework.boot:spring-boot-autoconfigure-processor"
}
```

### Module starter (Starter Module)

Starter thực chất là một jar rỗng. Mục đích duy nhất của nó là cung cấp các dependency cần thiết để làm việc với thư viện. Bạn có thể coi nó như một quan điểm định sẵn về những gì cần có để bắt đầu.

Đừng đưa ra giả định về project mà starter của bạn được thêm vào. Nếu thư viện mà bạn đang tự động cấu hình thường cần các starter khác, hãy đề cập đến chúng luôn. Việc cung cấp một tập hợp dependency *mặc định* phù hợp có thể khó khăn nếu số lượng dependency tùy chọn lớn, vì bạn nên tránh đưa vào các dependency không cần thiết cho cách dùng điển hình của thư viện. Nói cách khác, bạn không nên đưa vào các dependency tùy chọn.

> **LƯU Ý (NOTE):** Dù theo cách nào, starter của bạn phải tham chiếu tới starter cốt lõi của Spring Boot (`spring-boot-starter`) một cách trực tiếp hoặc gián tiếp (không cần thêm nó nếu starter của bạn dựa vào một starter khác). Nếu một project được tạo chỉ với starter tùy chỉnh của bạn, các tính năng cốt lõi của Spring Boot sẽ được đảm bảo nhờ sự hiện diện của starter cốt lõi.

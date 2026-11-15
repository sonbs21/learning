# 15.1.2. Hiểu về xử lý Ahead-of-Time của Spring (Understanding Spring Ahead-of-Time Processing)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#native-image.introducing-graalvm-native-images.understanding-aot-processing>

Các ứng dụng Spring Boot điển hình khá động và việc cấu hình được thực hiện tại runtime. Trên thực tế, khái niệm auto-configuration của Spring Boot phụ thuộc nhiều vào việc phản ứng với trạng thái của runtime để cấu hình mọi thứ đúng cách.

Mặc dù có thể thông báo cho GraalVM về các khía cạnh động này của ứng dụng, làm như vậy sẽ xóa bỏ phần lớn lợi ích của phân tích tĩnh. Vì vậy thay vào đó, khi dùng Spring Boot để tạo native image, một thế giới đóng (closed-world) được giả định và các khía cạnh động của ứng dụng bị hạn chế.

Giả định thế giới đóng ngụ ý, ngoài [các hạn chế do chính GraalVM tạo ra](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#native-image.introducing-graalvm-native-images.key-differences-with-jvm-deployments), các hạn chế sau:

- Các bean được định nghĩa trong ứng dụng không thể thay đổi tại runtime, nghĩa là:
  - Annotation `@Profile` của Spring và cấu hình theo profile [có những hạn chế](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.aot.conditions).
  - Các property thay đổi việc một bean có được tạo hay không đều không được hỗ trợ (ví dụ, `@ConditionalOnProperty` và các property `.enable`).

Khi các hạn chế này được áp dụng, Spring có thể thực hiện xử lý ahead-of-time trong thời gian build và sinh ra các tài nguyên bổ sung mà GraalVM có thể dùng. Một ứng dụng được xử lý bởi Spring AOT thường sẽ sinh ra:

- Mã nguồn Java
- Bytecode (cho dynamic proxy, v.v.)
- Các file gợi ý (hint) JSON cho GraalVM:
  - Gợi ý resource (`resource-config.json`)
  - Gợi ý reflection (`reflect-config.json`)
  - Gợi ý serialization (`serialization-config.json`)
  - Gợi ý Java Proxy (`proxy-config.json`)
  - Gợi ý JNI (`jni-config.json`)

## Sinh mã nguồn (Source Code Generation)

Ứng dụng Spring được tạo thành từ các Spring Bean. Bên trong, Spring Framework dùng hai khái niệm riêng biệt để quản lý bean. Có các bean instance, là các instance thực tế đã được tạo ra và có thể được inject vào các bean khác. Cũng có các bean definition, được dùng để định nghĩa các thuộc tính của một bean và cách instance của nó nên được tạo ra.

Nếu chúng ta lấy một class `@Configuration` điển hình:

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class MyConfiguration {

    @Bean
    public MyBean myBean() {
        return new MyBean();
    }

}
```

Bean definition được tạo ra bằng cách phân tích class `@Configuration` và tìm các phương thức `@Bean`. Trong ví dụ trên, chúng ta đang định nghĩa một `BeanDefinition` cho một singleton bean tên là `myBean`. Chúng ta cũng đang tạo một `BeanDefinition` cho chính class `MyConfiguration`.

Khi cần đến instance `myBean`, Spring biết rằng nó phải gọi phương thức `myBean()` và dùng kết quả. Khi chạy trên JVM, việc phân tích class `@Configuration` diễn ra khi ứng dụng khởi động và các phương thức `@Bean` được gọi bằng reflection.

Khi tạo một native image, Spring hoạt động theo một cách khác. Thay vì phân tích các class `@Configuration` và sinh bean definition tại runtime, nó làm điều đó tại thời điểm build. Sau khi các bean definition được phát hiện, chúng được xử lý và chuyển đổi thành mã nguồn có thể được trình biên dịch GraalVM phân tích.

Quá trình Spring AOT sẽ chuyển đổi class cấu hình ở trên thành code như sau:

```java
import org.springframework.beans.factory.aot.BeanInstanceSupplier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;

/**
 * Bean definitions for {@link MyConfiguration}.
 */
public class MyConfiguration__BeanDefinitions {

    /**
     * Get the bean definition for 'myConfiguration'.
     */
    public static BeanDefinition getMyConfigurationBeanDefinition() {
        Class<?> beanType = MyConfiguration.class;
        RootBeanDefinition beanDefinition = new RootBeanDefinition(beanType);
        beanDefinition.setInstanceSupplier(MyConfiguration::new);
        return beanDefinition;
    }

    /**
     * Get the bean instance supplier for 'myBean'.
     */
    private static BeanInstanceSupplier<MyBean> getMyBeanInstanceSupplier() {
        return BeanInstanceSupplier.<MyBean>forFactoryMethod(MyConfiguration.class, "myBean")
            .withGenerator((registeredBean) -> registeredBean.getBeanFactory().getBean(MyConfiguration.class).myBean());
    }

    /**
     * Get the bean definition for 'myBean'.
     */
    public static BeanDefinition getMyBeanBeanDefinition() {
        Class<?> beanType = MyBean.class;
        RootBeanDefinition beanDefinition = new RootBeanDefinition(beanType);
        beanDefinition.setInstanceSupplier(getMyBeanInstanceSupplier());
        return beanDefinition;
    }

}
```

> **LƯU Ý (NOTE):** Code chính xác được sinh ra có thể khác nhau tùy thuộc vào bản chất của các bean definition của bạn.

Bạn có thể thấy ở trên rằng code được sinh ra tạo ra các bean definition tương đương với class `@Configuration`, nhưng theo một cách trực tiếp mà GraalVM có thể hiểu được.

Có một bean definition cho bean `myConfiguration`, và một cho `myBean`. Khi cần đến một instance `myBean`, một `BeanInstanceSupplier` được gọi. Supplier này sẽ gọi phương thức `myBean()` trên bean `myConfiguration`.

> **LƯU Ý (NOTE):** Trong quá trình xử lý Spring AOT, ứng dụng của bạn được khởi động cho đến thời điểm các bean definition khả dụng. Các bean instance không được tạo ra trong giai đoạn xử lý AOT.

Spring AOT sẽ sinh code như thế này cho tất cả các bean definition của bạn. Nó cũng sẽ sinh code khi cần hậu xử lý bean (ví dụ, để gọi các phương thức `@Autowired`). Một `ApplicationContextInitializer` cũng sẽ được sinh ra, được Spring Boot dùng để khởi tạo `ApplicationContext` khi một ứng dụng đã được xử lý AOT thực sự được chạy.

> **MẸO (TIP):** Mặc dù mã nguồn được AOT sinh ra có thể dài dòng, nó khá dễ đọc và có thể hữu ích khi debug một ứng dụng. Các file mã nguồn được sinh ra có thể được tìm thấy trong `target/spring-aot/main/sources` khi dùng Maven và `build/generated/aotSources` với Gradle.

## Sinh file gợi ý (Hint File Generation)

Ngoài việc sinh các file mã nguồn, engine Spring AOT cũng sẽ sinh các file gợi ý được GraalVM sử dụng. File gợi ý chứa dữ liệu JSON mô tả cách GraalVM nên xử lý những thứ mà nó không thể hiểu bằng cách kiểm tra trực tiếp code.

Ví dụ, bạn có thể đang dùng một annotation Spring trên một phương thức private. Spring sẽ cần dùng reflection để gọi các phương thức private, ngay cả trên GraalVM. Khi những tình huống như vậy phát sinh, Spring có thể ghi một gợi ý reflection để GraalVM biết rằng mặc dù phương thức private không được gọi trực tiếp, nó vẫn cần có sẵn trong native image.

Các file gợi ý được sinh ra bên dưới `META-INF/native-image`, nơi chúng được GraalVM tự động nhận diện.

> **MẸO (TIP):** Các file gợi ý được sinh ra có thể được tìm thấy trong `target/spring-aot/main/resources` khi dùng Maven và `build/generated/aotResources` với Gradle.

## Sinh class proxy (Proxy Class Generation)

Spring đôi khi cần sinh các class proxy để tăng cường code bạn đã viết với các tính năng bổ sung. Để làm điều này, nó dùng thư viện cglib, thư viện trực tiếp sinh ra bytecode.

Khi một ứng dụng đang chạy trên JVM, các class proxy được sinh ra động khi ứng dụng chạy. Khi tạo một native image, các proxy này cần được tạo ra tại thời điểm build để chúng có thể được GraalVM đưa vào.

> **LƯU Ý (NOTE):** Không giống như việc sinh mã nguồn, bytecode được sinh ra không đặc biệt hữu ích khi debug một ứng dụng. Tuy nhiên, nếu bạn cần kiểm tra nội dung của các file `.class` bằng một công cụ như `javap`, bạn có thể tìm thấy chúng trong `target/spring-aot/main/classes` cho Maven và `build/generated/aotClasses` cho Gradle.

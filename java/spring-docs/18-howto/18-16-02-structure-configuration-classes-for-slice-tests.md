# 18.16.2. Cấu trúc các class `@Configuration` để đưa vào slice test (Structure `@Configuration` classes for inclusion in slice tests)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.testing.slice-tests>

Slice test hoạt động bằng cách giới hạn việc quét component của Spring Framework vào một tập hợp component hạn chế dựa trên kiểu của chúng. Đối với bất kỳ bean nào không được tạo thông qua component scanning, ví dụ, các bean được tạo bằng annotation `@Bean`, slice test sẽ không thể đưa vào/loại trừ chúng khỏi application context. Hãy xem ví dụ này:

```java
import org.apache.commons.dbcp2.BasicDataSource;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
public class MyConfiguration {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests((requests) -> requests.anyRequest().authenticated());
        return http.build();
    }

    @Bean
    @ConfigurationProperties("app.datasource.second")
    public BasicDataSource secondDataSource() {
        return DataSourceBuilder.create().type(BasicDataSource.class).build();
    }

}
```

Với một `@WebMvcTest` cho một ứng dụng có class `@Configuration` ở trên, bạn có thể mong đợi có bean `SecurityFilterChain` trong application context để bạn có thể kiểm thử xem các endpoint của controller có được bảo mật đúng cách hay không. Tuy nhiên, `MyConfiguration` không được bộ lọc component scanning của @WebMvcTest nhận vào vì nó không khớp với bất kỳ kiểu nào được bộ lọc chỉ định. Bạn có thể đưa cấu hình vào một cách tường minh bằng cách annotate class test với `@Import(MyConfiguration.class)`. Điều này sẽ nạp tất cả các bean trong `MyConfiguration` bao gồm cả bean `BasicDataSource`, vốn không cần thiết khi kiểm thử tầng web. Việc tách class cấu hình làm hai sẽ cho phép chỉ import cấu hình bảo mật.

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
public class MySecurityConfiguration {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests((requests) -> requests.anyRequest().authenticated());
        return http.build();
    }

}
```

```java
import org.apache.commons.dbcp2.BasicDataSource;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class MyDatasourceConfiguration {

    @Bean
    @ConfigurationProperties("app.datasource.second")
    public BasicDataSource secondDataSource() {
        return DataSourceBuilder.create().type(BasicDataSource.class).build();
    }

}
```

Việc có một class cấu hình duy nhất có thể không hiệu quả khi cần đưa các bean thuộc một domain nhất định vào slice test. Thay vào đó, việc cấu trúc cấu hình của ứng dụng thành nhiều class chi tiết với các bean cho một domain cụ thể có thể cho phép chỉ import chúng cho các slice test cụ thể.

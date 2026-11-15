# 18.8.10. Dùng nhiều EntityManagerFactory (Using Multiple EntityManagerFactories)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.data-access.use-multiple-entity-managers>

Nếu bạn cần dùng JPA với nhiều data source, có lẽ bạn cần một `EntityManagerFactory` cho mỗi data source. `LocalContainerEntityManagerFactoryBean` từ Spring ORM cho phép bạn cấu hình một `EntityManagerFactory` theo nhu cầu. Bạn cũng có thể tái sử dụng `JpaProperties` để bind các thiết lập cho mỗi `EntityManagerFactory`, như trong ví dụ sau:

**Java**

```java
import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.JpaVendorAdapter;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

@Configuration(proxyBeanMethods = false)
public class MyEntityManagerFactoryConfiguration {

    @Bean
    @ConfigurationProperties("app.jpa.first")
    public JpaProperties firstJpaProperties() {
        return new JpaProperties();
    }

    @Bean
    public LocalContainerEntityManagerFactoryBean firstEntityManagerFactory(DataSource firstDataSource,
            JpaProperties firstJpaProperties) {
        EntityManagerFactoryBuilder builder = createEntityManagerFactoryBuilder(firstJpaProperties);
        return builder.dataSource(firstDataSource).packages(Order.class).persistenceUnit("firstDs").build();
    }

    private EntityManagerFactoryBuilder createEntityManagerFactoryBuilder(JpaProperties jpaProperties) {
        JpaVendorAdapter jpaVendorAdapter = createJpaVendorAdapter(jpaProperties);
        return new EntityManagerFactoryBuilder(jpaVendorAdapter, jpaProperties.getProperties(), null);
    }

    private JpaVendorAdapter createJpaVendorAdapter(JpaProperties jpaProperties) {
        // ... map JPA properties as needed
        return new HibernateJpaVendorAdapter();
    }

}
```

**Kotlin**

```kotlin
import javax.sql.DataSource

import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.orm.jpa.JpaVendorAdapter
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter

@Configuration(proxyBeanMethods = false)
class MyEntityManagerFactoryConfiguration {

    @Bean
    @ConfigurationProperties("app.jpa.first")
    fun firstJpaProperties(): JpaProperties {
        return JpaProperties()
    }

    @Bean
    fun firstEntityManagerFactory(
        firstDataSource: DataSource?,
        firstJpaProperties: JpaProperties
    ): LocalContainerEntityManagerFactoryBean {
        val builder = createEntityManagerFactoryBuilder(firstJpaProperties)
        return builder.dataSource(firstDataSource).packages(Order::class.java).persistenceUnit("firstDs").build()
    }

    private fun createEntityManagerFactoryBuilder(jpaProperties: JpaProperties): EntityManagerFactoryBuilder {
        val jpaVendorAdapter = createJpaVendorAdapter(jpaProperties)
        return EntityManagerFactoryBuilder(jpaVendorAdapter, jpaProperties.properties, null)
    }

    private fun createJpaVendorAdapter(jpaProperties: JpaProperties): JpaVendorAdapter {
        // ... map JPA properties as needed
        return HibernateJpaVendorAdapter()
    }

}
```

Ví dụ trên tạo một `EntityManagerFactory` dùng một bean `DataSource` tên là `firstDataSource`. Nó quét các entity nằm trong cùng package với `Order`. Có thể ánh xạ thêm các property JPA bằng namespace `app.first.jpa`.

> **LƯU Ý (NOTE):** Khi bạn tự tạo một bean cho `LocalContainerEntityManagerFactoryBean`, mọi tùy chỉnh đã được áp dụng trong quá trình tạo `LocalContainerEntityManagerFactoryBean` được auto-configure sẽ bị mất. Ví dụ, trong trường hợp Hibernate, mọi property dưới tiền tố `spring.jpa.hibernate` sẽ không được tự động áp dụng cho `LocalContainerEntityManagerFactoryBean` của bạn. Nếu bạn đang dựa vào các property này để cấu hình những thứ như naming strategy hoặc chế độ DDL, bạn sẽ cần cấu hình tường minh điều đó khi tạo bean `LocalContainerEntityManagerFactoryBean`.

Bạn nên cung cấp một cấu hình tương tự cho mọi data source bổ sung mà bạn cần truy cập JPA. Để hoàn thiện bức tranh, bạn cũng cần cấu hình một `JpaTransactionManager` cho mỗi `EntityManagerFactory`. Ngoài ra, bạn có thể dùng một JTA transaction manager trải rộng trên cả hai.

Nếu bạn dùng Spring Data, bạn cần cấu hình `@EnableJpaRepositories` tương ứng, như trong các ví dụ sau:

**Java**

```java
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration(proxyBeanMethods = false)
@EnableJpaRepositories(basePackageClasses = Order.class, entityManagerFactoryRef = "firstEntityManagerFactory")
public class OrderConfiguration {

}
```

**Kotlin**

```kotlin
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@Configuration(proxyBeanMethods = false)
@EnableJpaRepositories(basePackageClasses = [Order::class], entityManagerFactoryRef = "firstEntityManagerFactory")
class OrderConfiguration
```

**Java**

```java
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration(proxyBeanMethods = false)
@EnableJpaRepositories(basePackageClasses = Customer.class, entityManagerFactoryRef = "secondEntityManagerFactory")
public class CustomerConfiguration {

}
```

**Kotlin**

```kotlin
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@Configuration(proxyBeanMethods = false)
@EnableJpaRepositories(basePackageClasses = [Customer::class], entityManagerFactoryRef = "secondEntityManagerFactory")
class CustomerConfiguration
```

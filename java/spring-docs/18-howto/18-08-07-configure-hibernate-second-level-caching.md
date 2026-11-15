# 18.8.7. Cấu hình second-level cache của Hibernate (Configure Hibernate Second-Level Caching)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.data-access.configure-hibernate-second-level-caching>

[Second-level cache](https://docs.jboss.org/hibernate/orm/6.4/userguide/html_single/Hibernate_User_Guide.html#caching) của Hibernate có thể được cấu hình cho nhiều cache provider khác nhau. Thay vì cấu hình Hibernate tìm kiếm lại cache provider, tốt hơn là cung cấp cache provider đã có sẵn trong context bất cứ khi nào có thể.

Để làm điều này với JCache, trước tiên hãy đảm bảo rằng `org.hibernate.orm:hibernate-jcache` có sẵn trên classpath. Sau đó, thêm một bean `HibernatePropertiesCustomizer` như trong ví dụ sau:

**Java**

```java
import org.hibernate.cache.jcache.ConfigSettings;

import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.cache.jcache.JCacheCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class MyHibernateSecondLevelCacheConfiguration {

    @Bean
    public HibernatePropertiesCustomizer hibernateSecondLevelCacheCustomizer(JCacheCacheManager cacheManager) {
        return (properties) -> properties.put(ConfigSettings.CACHE_MANAGER, cacheManager.getCacheManager());
    }

}
```

**Kotlin**

```kotlin
import org.hibernate.cache.jcache.ConfigSettings
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer
import org.springframework.cache.jcache.JCacheCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class MyHibernateSecondLevelCacheConfiguration {

    @Bean
    fun hibernateSecondLevelCacheCustomizer(cacheManager: JCacheCacheManager): HibernatePropertiesCustomizer {
        return HibernatePropertiesCustomizer { properties ->
            properties[ConfigSettings.CACHE_MANAGER] = cacheManager.cacheManager
        }
    }

}
```

Customizer này sẽ cấu hình Hibernate dùng cùng `CacheManager` với cái mà ứng dụng dùng. Cũng có thể dùng các instance `CacheManager` riêng biệt. Để biết chi tiết, xem [hướng dẫn người dùng Hibernate](https://docs.jboss.org/hibernate/orm/6.4/userguide/html_single/Hibernate_User_Guide.html#caching-provider-jcache).

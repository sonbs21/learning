# 11.1.1. Các cache provider được hỗ trợ (Supported Cache Providers)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#io.caching.provider>

Abstraction cache không cung cấp kho lưu trữ thực sự mà dựa vào abstraction được hiện thực hóa bởi các interface `org.springframework.cache.Cache` và `org.springframework.cache.CacheManager`.

Nếu bạn chưa định nghĩa một bean kiểu `CacheManager` hoặc một `CacheResolver` tên là `cacheResolver` (xem [`CachingConfigurer`](https://docs.spring.io/spring-framework/docs/6.1.6/javadoc-api/org/springframework/cache/annotation/CachingConfigurer.html)), Spring Boot cố gắng phát hiện các provider sau (theo thứ tự đã chỉ ra):

1. [Generic](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#io.caching.provider.generic)
2. [JCache (JSR-107)](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#io.caching.provider.jcache) (EhCache 3, Hazelcast, Infinispan, và các loại khác)
3. [Hazelcast](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#io.caching.provider.hazelcast)
4. [Infinispan](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#io.caching.provider.infinispan)
5. [Couchbase](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#io.caching.provider.couchbase)
6. [Redis](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#io.caching.provider.redis)
7. [Caffeine](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#io.caching.provider.caffeine)
8. [Cache2k](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#io.caching.provider.cache2k)
9. [Simple](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#io.caching.provider.simple)

Ngoài ra, [Spring Boot for Apache Geode](https://github.com/spring-projects/spring-boot-data-geode) cung cấp [auto-configuration để dùng Apache Geode làm cache provider](https://docs.spring.io/spring-boot-data-geode-build/2.0.x/reference/html5/#geode-caching-provider).

> **MẸO (TIP):** Nếu `CacheManager` được Spring Boot tự động cấu hình, có thể *bắt buộc* dùng một cache provider cụ thể bằng cách đặt property `spring.cache.type`. Hãy dùng property này nếu bạn cần [dùng cache no-op](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#io.caching.provider.none) trong một số môi trường nhất định (chẳng hạn như test).

> **MẸO (TIP):** Dùng "Starter" `spring-boot-starter-cache` để nhanh chóng thêm các dependency caching cơ bản. Starter này kéo theo `spring-context-support`. Nếu bạn thêm dependency thủ công, bạn phải đưa `spring-context-support` vào để dùng hỗ trợ JCache hoặc Caffeine.

Nếu `CacheManager` được Spring Boot tự động cấu hình, bạn có thể tinh chỉnh thêm cấu hình của nó trước khi nó được khởi tạo hoàn toàn bằng cách expose một bean hiện thực interface `CacheManagerCustomizer`. Ví dụ sau đặt một cờ để nói rằng các giá trị `null` không nên được truyền xuống map bên dưới:

**Java**

```java
import org.springframework.boot.autoconfigure.cache.CacheManagerCustomizer;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class MyCacheManagerConfiguration {

    @Bean
    public CacheManagerCustomizer<ConcurrentMapCacheManager> cacheManagerCustomizer() {
        return (cacheManager) -> cacheManager.setAllowNullValues(false);
    }

}
```

**Kotlin**

```kotlin
import org.springframework.boot.autoconfigure.cache.CacheManagerCustomizer
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class MyCacheManagerConfiguration {

    @Bean
    fun cacheManagerCustomizer(): CacheManagerCustomizer<ConcurrentMapCacheManager> {
        return CacheManagerCustomizer { cacheManager ->
            cacheManager.isAllowNullValues = false
        }
    }

}
```

> **LƯU Ý (NOTE):** Trong ví dụ trên, một `ConcurrentMapCacheManager` được tự động cấu hình được mong đợi. Nếu không phải vậy (bạn đã cung cấp cấu hình riêng hoặc một cache provider khác được tự động cấu hình), customizer hoàn toàn không được gọi. Bạn có thể có bao nhiêu customizer tùy thích, và bạn cũng có thể sắp thứ tự chúng bằng `@Order` hoặc `Ordered`.

## Generic

Caching generic được dùng nếu context định nghĩa *ít nhất* một bean `org.springframework.cache.Cache`. Một `CacheManager` bọc tất cả các bean thuộc kiểu đó sẽ được tạo ra.

## JCache (JSR-107)

[JCache](https://jcp.org/en/jsr/detail?id=107) được khởi động thông qua sự hiện diện của một `javax.cache.spi.CachingProvider` trên classpath (tức là có một thư viện caching tương thích JSR-107 trên classpath), và `JCacheCacheManager` được cung cấp bởi "Starter" `spring-boot-starter-cache`. Có nhiều thư viện tương thích khác nhau, và Spring Boot cung cấp quản lý dependency cho Ehcache 3, Hazelcast, và Infinispan. Bất kỳ thư viện tương thích nào khác cũng có thể được thêm vào.

Có thể xảy ra trường hợp có nhiều hơn một provider, khi đó provider phải được chỉ định tường minh. Mặc dù tiêu chuẩn JSR-107 không áp đặt một cách chuẩn hóa để định nghĩa vị trí file cấu hình, Spring Boot cố gắng hết sức để hỗ trợ việc thiết lập một cache với các chi tiết implementation, như trong ví dụ sau:

**Properties**

```properties
# Only necessary if more than one provider is present
spring.cache.jcache.provider=com.example.MyCachingProvider
spring.cache.jcache.config=classpath:example.xml
```

**Yaml**

```yaml
# Only necessary if more than one provider is present
spring:
  cache:
    jcache:
      provider: "com.example.MyCachingProvider"
      config: "classpath:example.xml"
```

> **LƯU Ý (NOTE):** Khi một thư viện cache cung cấp cả implementation gốc và hỗ trợ JSR-107, Spring Boot ưu tiên hỗ trợ JSR-107, để có cùng các tính năng nếu bạn chuyển sang một implementation JSR-107 khác.

> **MẸO (TIP):** Spring Boot có [hỗ trợ chung cho Hazelcast](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#io.hazelcast). Nếu có một `HazelcastInstance` duy nhất, nó cũng được tự động tái sử dụng cho `CacheManager`, trừ khi property `spring.cache.jcache.config` được chỉ định.

Có hai cách để tùy chỉnh `javax.cache.cacheManager` bên dưới:

- Các cache có thể được tạo khi khởi động bằng cách đặt property `spring.cache.cache-names`. Nếu có một bean `javax.cache.configuration.Configuration` tùy chỉnh được định nghĩa, nó được dùng để tùy chỉnh chúng.
- Các bean `org.springframework.boot.autoconfigure.cache.JCacheManagerCustomizer` được gọi với tham chiếu tới `CacheManager` để tùy chỉnh hoàn toàn.

> **MẸO (TIP):** Nếu có một bean `javax.cache.CacheManager` tiêu chuẩn được định nghĩa, nó sẽ được tự động bọc trong một implementation `org.springframework.cache.CacheManager` mà abstraction mong đợi. Không có tùy chỉnh nào khác được áp dụng lên nó.

## Hazelcast

Spring Boot có [hỗ trợ chung cho Hazelcast](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#io.hazelcast). Nếu một `HazelcastInstance` đã được tự động cấu hình và `com.hazelcast:hazelcast-spring` có trên classpath, nó sẽ được tự động bọc trong một `CacheManager`.

> **LƯU Ý (NOTE):** Hazelcast có thể được dùng như một cache tương thích JCache hoặc như một cache tương thích `CacheManager` của Spring. Khi đặt `spring.cache.type` thành `hazelcast`, Spring Boot sẽ dùng implementation dựa trên `CacheManager`. Nếu bạn muốn dùng Hazelcast như một cache tương thích JCache, hãy đặt `spring.cache.type` thành `jcache`. Nếu bạn có nhiều cache provider tương thích JCache và muốn bắt buộc dùng Hazelcast, bạn phải [đặt tường minh JCache provider](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#io.caching.provider.jcache).

## Infinispan

[Infinispan](https://infinispan.org/) không có vị trí file cấu hình mặc định, vì vậy nó phải được chỉ định tường minh. Nếu không, cách khởi động mặc định sẽ được dùng.

**Properties**

```properties
spring.cache.infinispan.config=infinispan.xml
```

**Yaml**

```yaml
spring:
  cache:
    infinispan:
      config: "infinispan.xml"
```

Các cache có thể được tạo khi khởi động bằng cách đặt property `spring.cache.cache-names`. Nếu có một bean `ConfigurationBuilder` tùy chỉnh được định nghĩa, nó được dùng để tùy chỉnh các cache.

Để tương thích với nền tảng Jakarta EE 9 của Spring Boot, phải dùng các module `-jakarta` của Infinispan. Với mọi module có biến thể `-jakarta`, biến thể đó phải được dùng thay cho module tiêu chuẩn. Ví dụ, `infinispan-core-jakarta` và `infinispan-commons-jakarta` phải được dùng thay cho lần lượt `infinispan-core` và `infinispan-commons`.

## Couchbase

Nếu có Spring Data Couchbase và Couchbase đã được [cấu hình](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#data.nosql.couchbase), một `CouchbaseCacheManager` sẽ được tự động cấu hình. Có thể tạo thêm cache khi khởi động bằng cách đặt property `spring.cache.cache-names` và các giá trị mặc định của cache có thể được cấu hình bằng các property `spring.cache.couchbase.*`. Ví dụ, cấu hình sau tạo các cache `cache1` và `cache2` với thời gian *hết hạn* của entry là 10 phút:

**Properties**

```properties
spring.cache.cache-names=cache1,cache2
spring.cache.couchbase.expiration=10m
```

**Yaml**

```yaml
spring:
  cache:
    cache-names: "cache1,cache2"
    couchbase:
      expiration: "10m"
```

Nếu bạn cần kiểm soát nhiều hơn đối với cấu hình, hãy cân nhắc đăng ký một bean `CouchbaseCacheManagerBuilderCustomizer`. Ví dụ sau cho thấy một customizer cấu hình thời gian hết hạn entry cụ thể cho `cache1` và `cache2`:

**Java**

```java
import java.time.Duration;

import org.springframework.boot.autoconfigure.cache.CouchbaseCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.couchbase.cache.CouchbaseCacheConfiguration;

@Configuration(proxyBeanMethods = false)
public class MyCouchbaseCacheManagerConfiguration {

    @Bean
    public CouchbaseCacheManagerBuilderCustomizer myCouchbaseCacheManagerBuilderCustomizer() {
        return (builder) -> builder
                .withCacheConfiguration("cache1", CouchbaseCacheConfiguration
                        .defaultCacheConfig().entryExpiry(Duration.ofSeconds(10)))
                .withCacheConfiguration("cache2", CouchbaseCacheConfiguration
                        .defaultCacheConfig().entryExpiry(Duration.ofMinutes(1)));

    }

}
```

**Kotlin**

```kotlin
import org.springframework.boot.autoconfigure.cache.CouchbaseCacheManagerBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.couchbase.cache.CouchbaseCacheConfiguration
import java.time.Duration

@Configuration(proxyBeanMethods = false)
class MyCouchbaseCacheManagerConfiguration {

    @Bean
    fun myCouchbaseCacheManagerBuilderCustomizer(): CouchbaseCacheManagerBuilderCustomizer {
        return CouchbaseCacheManagerBuilderCustomizer { builder ->
            builder
                .withCacheConfiguration(
                    "cache1", CouchbaseCacheConfiguration
                        .defaultCacheConfig().entryExpiry(Duration.ofSeconds(10))
                )
                .withCacheConfiguration(
                    "cache2", CouchbaseCacheConfiguration
                        .defaultCacheConfig().entryExpiry(Duration.ofMinutes(1))
                )
        }
    }

}
```

## Redis

Nếu [Redis](https://redis.io/) có sẵn và đã được cấu hình, một `RedisCacheManager` sẽ được tự động cấu hình. Có thể tạo thêm cache khi khởi động bằng cách đặt property `spring.cache.cache-names` và các giá trị mặc định của cache có thể được cấu hình bằng các property `spring.cache.redis.*`. Ví dụ, cấu hình sau tạo các cache `cache1` và `cache2` với *thời gian sống (time to live)* là 10 phút:

**Properties**

```properties
spring.cache.cache-names=cache1,cache2
spring.cache.redis.time-to-live=10m
```

**Yaml**

```yaml
spring:
  cache:
    cache-names: "cache1,cache2"
    redis:
      time-to-live: "10m"
```

> **LƯU Ý (NOTE):** Theo mặc định, một tiền tố key được thêm vào để, nếu hai cache riêng biệt dùng cùng một key, Redis không có các key chồng chéo và không thể trả về các giá trị không hợp lệ. Chúng tôi đặc biệt khuyên giữ thiết lập này được bật nếu bạn tạo `RedisCacheManager` của riêng mình.

> **MẸO (TIP):** Bạn có thể kiểm soát hoàn toàn cấu hình mặc định bằng cách thêm một `@Bean` `RedisCacheConfiguration` của riêng bạn. Điều này có thể hữu ích nếu bạn cần tùy chỉnh chiến lược serialization mặc định.

Nếu bạn cần kiểm soát nhiều hơn đối với cấu hình, hãy cân nhắc đăng ký một bean `RedisCacheManagerBuilderCustomizer`. Ví dụ sau cho thấy một customizer cấu hình thời gian sống cụ thể cho `cache1` và `cache2`:

**Java**

```java
import java.time.Duration;

import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;

@Configuration(proxyBeanMethods = false)
public class MyRedisCacheManagerConfiguration {

    @Bean
    public RedisCacheManagerBuilderCustomizer myRedisCacheManagerBuilderCustomizer() {
        return (builder) -> builder
                .withCacheConfiguration("cache1", RedisCacheConfiguration
                        .defaultCacheConfig().entryTtl(Duration.ofSeconds(10)))
                .withCacheConfiguration("cache2", RedisCacheConfiguration
                        .defaultCacheConfig().entryTtl(Duration.ofMinutes(1)));

    }

}
```

**Kotlin**

```kotlin
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.cache.RedisCacheConfiguration
import java.time.Duration

@Configuration(proxyBeanMethods = false)
class MyRedisCacheManagerConfiguration {

    @Bean
    fun myRedisCacheManagerBuilderCustomizer(): RedisCacheManagerBuilderCustomizer {
        return RedisCacheManagerBuilderCustomizer { builder ->
            builder
                .withCacheConfiguration(
                    "cache1", RedisCacheConfiguration
                        .defaultCacheConfig().entryTtl(Duration.ofSeconds(10))
                )
                .withCacheConfiguration(
                    "cache2", RedisCacheConfiguration
                        .defaultCacheConfig().entryTtl(Duration.ofMinutes(1))
                )
        }
    }

}
```

## Caffeine

[Caffeine](https://github.com/ben-manes/caffeine) là một bản viết lại bằng Java 8 của cache Guava, thay thế cho hỗ trợ Guava. Nếu có Caffeine, một `CaffeineCacheManager` (được cung cấp bởi "Starter" `spring-boot-starter-cache`) sẽ được tự động cấu hình. Các cache có thể được tạo khi khởi động bằng cách đặt property `spring.cache.cache-names` và có thể được tùy chỉnh bằng một trong những cách sau (theo thứ tự đã chỉ ra):

1. Một cache spec được định nghĩa bởi `spring.cache.caffeine.spec`
2. Một bean `com.github.benmanes.caffeine.cache.CaffeineSpec` được định nghĩa
3. Một bean `com.github.benmanes.caffeine.cache.Caffeine` được định nghĩa

Ví dụ, cấu hình sau tạo các cache `cache1` và `cache2` với kích thước tối đa là 500 và *thời gian sống* là 10 phút

**Properties**

```properties
spring.cache.cache-names=cache1,cache2
spring.cache.caffeine.spec=maximumSize=500,expireAfterAccess=600s
```

**Yaml**

```yaml
spring:
  cache:
    cache-names: "cache1,cache2"
    caffeine:
      spec: "maximumSize=500,expireAfterAccess=600s"
```

Nếu có một bean `com.github.benmanes.caffeine.cache.CacheLoader` được định nghĩa, nó sẽ được tự động gắn với `CaffeineCacheManager`. Vì `CacheLoader` sẽ được gắn với *tất cả* các cache do cache manager quản lý, nó phải được định nghĩa là `CacheLoader<Object, Object>`. Auto-configuration bỏ qua mọi kiểu generic khác.

## Cache2k

[Cache2k](https://cache2k.org/) là một cache in-memory. Nếu có tích hợp spring của Cache2k, một `SpringCache2kCacheManager` sẽ được tự động cấu hình.

Các cache có thể được tạo khi khởi động bằng cách đặt property `spring.cache.cache-names`. Các giá trị mặc định của cache có thể được tùy chỉnh bằng một bean `Cache2kBuilderCustomizer`. Ví dụ sau cho thấy một customizer cấu hình sức chứa của cache là 200 entry, với thời gian hết hạn là 5 phút:

**Java**

```java
import java.util.concurrent.TimeUnit;

import org.springframework.boot.autoconfigure.cache.Cache2kBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class MyCache2kDefaultsConfiguration {

    @Bean
    public Cache2kBuilderCustomizer myCache2kDefaultsCustomizer() {
        return (builder) -> builder.entryCapacity(200)
                .expireAfterWrite(5, TimeUnit.MINUTES);
    }

}
```

**Kotlin**

```kotlin
import org.springframework.boot.autoconfigure.cache.Cache2kBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.TimeUnit

@Configuration(proxyBeanMethods = false)
class MyCache2kDefaultsConfiguration {

    @Bean
    fun myCache2kDefaultsCustomizer(): Cache2kBuilderCustomizer {
        return Cache2kBuilderCustomizer { builder ->
            builder.entryCapacity(200)
                .expireAfterWrite(5, TimeUnit.MINUTES)
        }
    }
}
```

## Simple

Nếu không tìm thấy provider nào khác, một implementation đơn giản dùng `ConcurrentHashMap` làm kho lưu cache sẽ được cấu hình. Đây là mặc định nếu không có thư viện caching nào trong ứng dụng. Theo mặc định, các cache được tạo khi cần, nhưng bạn có thể giới hạn danh sách các cache có sẵn bằng cách đặt property `cache-names`. Ví dụ, nếu bạn chỉ muốn các cache `cache1` và `cache2`, hãy đặt property `cache-names` như sau:

**Properties**

```properties
spring.cache.cache-names=cache1,cache2
```

**Yaml**

```yaml
spring:
  cache:
    cache-names: "cache1,cache2"
```

Nếu bạn làm vậy và ứng dụng dùng một cache không có trong danh sách, nó sẽ thất bại tại runtime khi cần đến cache đó, chứ không phải khi khởi động. Điều này tương tự với cách các cache provider "thực" hoạt động nếu bạn dùng một cache chưa được khai báo.

## None

Khi `@EnableCaching` có mặt trong cấu hình, một cấu hình cache phù hợp cũng được mong đợi. Nếu bạn có một `CacheManager` tùy chỉnh, hãy cân nhắc định nghĩa nó trong một class `@Configuration` riêng để bạn có thể ghi đè nó nếu cần. None dùng một implementation no-op, hữu ích trong test, và các slice test dùng nó theo mặc định thông qua `@AutoConfigureCache`.

Nếu bạn cần dùng một cache no-op thay vì cache manager được tự động cấu hình trong một môi trường nhất định, hãy đặt loại cache thành `none`, như trong ví dụ sau:

**Properties**

```properties
spring.cache.type=none
```

**Yaml**

```yaml
spring:
  cache:
    type: "none"
```

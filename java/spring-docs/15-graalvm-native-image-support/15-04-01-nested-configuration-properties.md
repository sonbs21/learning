# 15.4.1. Configuration property lồng nhau (Nested Configuration Properties)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#native-image.advanced.nested-configuration-properties>

Các gợi ý reflection được engine ahead-of-time của Spring tự động tạo ra cho các configuration property. Tuy nhiên, các configuration property lồng nhau không phải là inner class **phải** được đánh annotation `@NestedConfigurationProperty`, nếu không chúng sẽ không được phát hiện và sẽ không thể bind được.

```java
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties(prefix = "my.properties")
public class MyProperties {

    private String name;

    @NestedConfigurationProperty
    private final Nested nested = new Nested();

    // getters / setters...

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Nested getNested() {
        return this.nested;
    }

}
```

trong đó `Nested` là:

```java
public class Nested {

    private int number;

    // getters / setters...

    public int getNumber() {
        return this.number;
    }

    public void setNumber(int number) {
        this.number = number;
    }

}
```

Ví dụ trên tạo ra các configuration property cho `my.properties.name` và `my.properties.nested.number`. Nếu không có annotation `@NestedConfigurationProperty` trên field `nested`, property `my.properties.nested.number` sẽ không thể bind được trong một native image.

Khi dùng constructor binding, bạn phải đánh annotation `@NestedConfigurationProperty` lên field:

```java
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties(prefix = "my.properties")
public class MyPropertiesCtor {

    private final String name;

    @NestedConfigurationProperty
    private final Nested nested;

    public MyPropertiesCtor(String name, Nested nested) {
        this.name = name;
        this.nested = nested;
    }

    // getters / setters...

    public String getName() {
        return this.name;
    }

    public Nested getNested() {
        return this.nested;
    }

}
```

Khi dùng record, bạn phải đánh annotation `@NestedConfigurationProperty` lên tham số:

```java
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties(prefix = "my.properties")
public record MyPropertiesRecord(String name, @NestedConfigurationProperty Nested nested) {

}
```

Khi dùng Kotlin, bạn cần đánh annotation `@NestedConfigurationProperty` lên tham số của một data class:

```kotlin
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty

@ConfigurationProperties(prefix = "my.properties")
data class MyPropertiesKotlin(
    val name: String,
    @NestedConfigurationProperty val nested: Nested
)
```

> **LƯU Ý (NOTE):** Vui lòng dùng getter và setter public trong mọi trường hợp, nếu không các property sẽ không thể bind được.

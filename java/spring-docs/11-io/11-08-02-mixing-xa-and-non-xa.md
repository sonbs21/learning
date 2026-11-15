# 11.8.2. Kết hợp kết nối JMS XA và không XA (Mixing XA and Non-XA JMS Connections)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#io.jta.mixing-xa-and-non-xa-connections>

Khi dùng JTA, bean `ConnectionFactory` JMS chính có hỗ trợ XA (XA-aware) và tham gia vào các giao dịch phân tán. Bạn có thể inject nó vào bean mà không cần dùng `@Qualifier` nào:

**Java**

```java
public MyBean(ConnectionFactory connectionFactory) {
    // ...
}
```

**Kotlin**

```kotlin
```

Trong một số tình huống, bạn có thể muốn xử lý một số message JMS nhất định bằng một `ConnectionFactory` không XA. Ví dụ, logic xử lý JMS của bạn có thể mất nhiều thời gian hơn timeout XA.

Nếu bạn muốn dùng một `ConnectionFactory` không XA, bạn có thể dùng bean `nonXaJmsConnectionFactory`:

**Java**

```java
public MyBean(@Qualifier("nonXaJmsConnectionFactory") ConnectionFactory connectionFactory) {
    // ...
}
```

**Kotlin**

```kotlin
```

Để nhất quán, bean `jmsConnectionFactory` cũng được cung cấp thông qua alias bean `xaJmsConnectionFactory`:

**Java**

```java
public MyBean(@Qualifier("xaJmsConnectionFactory") ConnectionFactory connectionFactory) {
    // ...
}
```

**Kotlin**

```kotlin
```

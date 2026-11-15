# 10.3.3. Kafka Streams

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#messaging.kafka.streams>

Spring for Apache Kafka cung cấp một factory bean để tạo object `StreamsBuilder` và quản lý vòng đời các stream của nó. Spring Boot tự động cấu hình bean `KafkaStreamsConfiguration` cần thiết miễn là `kafka-streams` có trên classpath và Kafka Streams được bật bằng annotation `@EnableKafkaStreams`.

Bật Kafka Streams có nghĩa là application id và bootstrap server phải được đặt. Cái đầu có thể được cấu hình bằng `spring.kafka.streams.application-id`, mặc định là `spring.application.name` nếu không được đặt. Cái sau có thể được đặt toàn cục hoặc được ghi đè riêng chỉ cho stream.

Một số property bổ sung có sẵn thông qua các property chuyên biệt; các property Kafka tùy ý khác có thể được đặt bằng namespace `spring.kafka.streams.properties`. Xem thêm [Các property Kafka bổ sung (Additional Kafka Properties)](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#messaging.kafka.additional-properties) để biết thêm thông tin.

Để dùng factory bean, hãy kết nối `StreamsBuilder` vào `@Bean` của bạn như trong ví dụ sau:

**Java**

```java
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Produced;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaStreams;
import org.springframework.kafka.support.serializer.JsonSerde;

@Configuration(proxyBeanMethods = false)
@EnableKafkaStreams
public class MyKafkaStreamsConfiguration {

    @Bean
    public KStream<Integer, String> kStream(StreamsBuilder streamsBuilder) {
        KStream<Integer, String> stream = streamsBuilder.stream("ks1In");
        stream.map(this::uppercaseValue).to("ks1Out", Produced.with(Serdes.Integer(), new JsonSerde<>()));
        return stream;
    }

    private KeyValue<Integer, String> uppercaseValue(Integer key, String value) {
        return new KeyValue<>(key, value.toUpperCase());
    }

}
```

**Kotlin**

```kotlin
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.KeyValue
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.Produced
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafkaStreams
import org.springframework.kafka.support.serializer.JsonSerde

@Configuration(proxyBeanMethods = false)
@EnableKafkaStreams
class MyKafkaStreamsConfiguration {

    @Bean
    fun kStream(streamsBuilder: StreamsBuilder): KStream<Int, String> {
        val stream = streamsBuilder.stream<Int, String>("ks1In")
        stream.map(this::uppercaseValue).to("ks1Out", Produced.with(Serdes.Integer(), JsonSerde()))
        return stream
    }

    private fun uppercaseValue(key: Int, value: String): KeyValue<Int?, String?> {
        return KeyValue(key, value.uppercase())
    }

}
```

Theo mặc định, các stream được object `StreamBuilder` quản lý được khởi động tự động. Bạn có thể tùy chỉnh hành vi này bằng property `spring.kafka.streams.auto-startup`.

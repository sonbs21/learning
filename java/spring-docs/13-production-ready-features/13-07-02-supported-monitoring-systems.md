# 13.7.2. Các hệ thống giám sát được hỗ trợ (Supported Monitoring Systems)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.metrics.export>

Phần này mô tả ngắn gọn từng hệ thống giám sát được hỗ trợ.

## AppOptics

Theo mặc định, registry AppOptics định kỳ đẩy metrics tới [api.appoptics.com/v1/measurements](https://api.appoptics.com/v1/measurements). Để xuất metrics tới SaaS [AppOptics](https://docs.micrometer.io/micrometer/reference/implementations/appOptics), bạn phải cung cấp API token:

**Properties**

```properties
management.appoptics.metrics.export.api-token=YOUR_TOKEN
```

**Yaml**

```yaml
management:
  appoptics:
    metrics:
      export:
        api-token: "YOUR_TOKEN"
```

## Atlas

Theo mặc định, metrics được xuất tới [Atlas](https://docs.micrometer.io/micrometer/reference/implementations/atlas) chạy trên máy cục bộ của bạn. Bạn có thể cung cấp vị trí của [Atlas server](https://github.com/Netflix/atlas):

**Properties**

```properties
management.atlas.metrics.export.uri=https://atlas.example.com:7101/api/v1/publish
```

**Yaml**

```yaml
management:
  atlas:
    metrics:
      export:
        uri: "https://atlas.example.com:7101/api/v1/publish"
```

## Datadog

Một registry Datadog định kỳ đẩy metrics tới [datadoghq](https://www.datadoghq.com). Để xuất metrics tới [Datadog](https://docs.micrometer.io/micrometer/reference/implementations/datadog), bạn phải cung cấp API key:

**Properties**

```properties
management.datadog.metrics.export.api-key=YOUR_KEY
```

**Yaml**

```yaml
management:
  datadog:
    metrics:
      export:
        api-key: "YOUR_KEY"
```

Nếu bạn cung cấp thêm một application key (tùy chọn), thì các metadata như mô tả meter, kiểu, và đơn vị cơ sở cũng sẽ được xuất:

**Properties**

```properties
management.datadog.metrics.export.api-key=YOUR_API_KEY
management.datadog.metrics.export.application-key=YOUR_APPLICATION_KEY
```

**Yaml**

```yaml
management:
  datadog:
    metrics:
      export:
        api-key: "YOUR_API_KEY"
        application-key: "YOUR_APPLICATION_KEY"
```

Theo mặc định, metrics được gửi tới [site](https://docs.datadoghq.com/getting_started/site) Datadog US ([api.datadoghq.com](https://api.datadoghq.com)). Nếu project Datadog của bạn được host trên một trong các site khác, hoặc bạn cần gửi metrics thông qua proxy, hãy cấu hình URI tương ứng:

**Properties**

```properties
management.datadog.metrics.export.uri=https://api.datadoghq.eu
```

**Yaml**

```yaml
management:
  datadog:
    metrics:
      export:
        uri: "https://api.datadoghq.eu"
```

Bạn cũng có thể thay đổi khoảng thời gian gửi metrics tới Datadog:

**Properties**

```properties
management.datadog.metrics.export.step=30s
```

**Yaml**

```yaml
management:
  datadog:
    metrics:
      export:
        step: "30s"
```

## Dynatrace

Dynatrace cung cấp hai API thu nạp (ingest) metrics, cả hai đều được hiện thực cho [Micrometer](https://docs.micrometer.io/micrometer/reference/implementations/dynatrace). Bạn có thể tìm tài liệu Dynatrace về việc thu nạp metrics Micrometer [tại đây](https://docs.dynatrace.com/docs/shortlink/micrometer-metrics-ingest). Các configuration property trong namespace `v1` chỉ áp dụng khi xuất tới [Timeseries v1 API](https://docs.dynatrace.com/docs/shortlink/api-metrics). Các configuration property trong namespace `v2` chỉ áp dụng khi xuất tới [Metrics v2 API](https://docs.dynatrace.com/docs/shortlink/api-metrics-v2-post-datapoints). Lưu ý rằng tích hợp này chỉ có thể xuất tới phiên bản `v1` hoặc `v2` của API tại một thời điểm, với `v2` được ưu tiên. Nếu `device-id` (bắt buộc cho v1 nhưng không dùng trong v2) được đặt trong namespace `v1`, metrics sẽ được xuất tới endpoint `v1`. Nếu không, `v2` được giả định.

### v2 API

Bạn có thể dùng v2 API theo hai cách.

#### Auto-configuration

Auto-configuration của Dynatrace có sẵn cho các host được giám sát bởi OneAgent hoặc bởi Dynatrace Operator cho Kubernetes.

**OneAgent cục bộ:** Nếu một OneAgent đang chạy trên host, metrics sẽ được tự động xuất tới [endpoint thu nạp của OneAgent cục bộ](https://docs.dynatrace.com/docs/shortlink/local-api). Endpoint thu nạp chuyển tiếp metrics tới backend Dynatrace.

**Dynatrace Kubernetes Operator:** Khi chạy trong Kubernetes có cài Dynatrace Operator, registry sẽ tự động lấy endpoint URI và API token từ operator thay thế.

Đây là hành vi mặc định và không yêu cầu thiết lập đặc biệt nào ngoài một dependency tới `io.micrometer:micrometer-registry-dynatrace`.

#### Cấu hình thủ công (Manual configuration)

Nếu không có auto-configuration, endpoint của [Metrics v2 API](https://docs.dynatrace.com/docs/shortlink/api-metrics-v2-post-datapoints) và một API token là bắt buộc. [API token](https://docs.dynatrace.com/docs/shortlink/api-authentication) phải có quyền "Ingest metrics" (`metrics.ingest`). Chúng tôi khuyên giới hạn phạm vi của token ở quyền duy nhất này. Bạn phải đảm bảo endpoint URI chứa đường dẫn (ví dụ, `/api/v2/metrics/ingest`):

URL của endpoint thu nạp Metrics API v2 khác nhau tùy theo tùy chọn triển khai của bạn:

- SaaS: `https://{your-environment-id}.live.dynatrace.com/api/v2/metrics/ingest`
- Triển khai Managed: `https://{your-domain}/e/{your-environment-id}/api/v2/metrics/ingest`

Ví dụ dưới đây cấu hình việc xuất metrics dùng environment id `example`:

**Properties**

```properties
management.dynatrace.metrics.export.uri=https://example.live.dynatrace.com/api/v2/metrics/ingest
management.dynatrace.metrics.export.api-token=YOUR_TOKEN
```

**Yaml**

```yaml
management:
  dynatrace:
    metrics:
      export:
        uri: "https://example.live.dynatrace.com/api/v2/metrics/ingest"
        api-token: "YOUR_TOKEN"
```

Khi dùng Dynatrace v2 API, các tính năng tùy chọn sau có sẵn (thêm chi tiết có thể được tìm thấy trong [tài liệu Dynatrace](https://docs.dynatrace.com/docs/shortlink/micrometer-metrics-ingest#dt-configuration-properties)):

- Tiền tố key metric (Metric key prefix): Đặt một tiền tố được thêm vào trước tất cả các key metric được xuất.
- Bổ sung metadata Dynatrace (Enrich with Dynatrace metadata): Nếu một OneAgent hoặc Dynatrace operator đang chạy, bổ sung metadata cho metrics (ví dụ, về host, process, hoặc pod).
- Các dimension mặc định (Default dimensions): Chỉ định các cặp key-value được thêm vào tất cả các metric được xuất. Nếu các tag có cùng key được chỉ định với Micrometer, chúng sẽ ghi đè các dimension mặc định.
- Dùng các instrument Summary của Dynatrace (Use Dynatrace Summary instruments): Trong một số trường hợp, registry Micrometer Dynatrace đã tạo ra các metric bị từ chối. Trong Micrometer 1.9.x, điều này đã được khắc phục bằng cách giới thiệu các instrument summary đặc thù cho Dynatrace. Đặt tùy chọn này thành `false` buộc Micrometer quay về hành vi mặc định trước 1.9.x. Nó chỉ nên được dùng khi gặp vấn đề trong quá trình chuyển từ Micrometer 1.8.x sang 1.9.x.
- Xuất metadata của meter (Export meter metadata): Bắt đầu từ Micrometer 1.12.0, exporter Dynatrace cũng sẽ xuất metadata của meter, như đơn vị và mô tả, theo mặc định. Dùng tùy chọn `export-meter-metadata` để tắt tính năng này.

Có thể không chỉ định URI và API token, như trong ví dụ sau. Trong tình huống này, endpoint được cấu hình tự động sẽ được dùng:

**Properties**

```properties
management.dynatrace.metrics.export.v2.metric-key-prefix=your.key.prefix
management.dynatrace.metrics.export.v2.enrich-with-dynatrace-metadata=true
management.dynatrace.metrics.export.v2.default-dimensions.key1=value1
management.dynatrace.metrics.export.v2.default-dimensions.key2=value2
management.dynatrace.metrics.export.v2.use-dynatrace-summary-instruments=true
management.dynatrace.metrics.export.v2.export-meter-metadata=true
```

**Yaml**

```yaml
management:
  dynatrace:
    metrics:
      export:
        # Specify uri and api-token here if not using the local OneAgent endpoint.
        v2:
          metric-key-prefix: "your.key.prefix"
          enrich-with-dynatrace-metadata: true
          default-dimensions:
            key1: "value1"
            key2: "value2"
          use-dynatrace-summary-instruments: true # (default: true)
          export-meter-metadata: true             # (default: true)
```

### v1 API (Legacy)

Registry metrics Dynatrace v1 API định kỳ đẩy metrics tới URI đã cấu hình bằng [Timeseries v1 API](https://docs.dynatrace.com/docs/shortlink/api-metrics). Để tương thích ngược với các thiết lập hiện có, khi `device-id` được đặt (bắt buộc cho v1, nhưng không dùng trong v2), metrics được xuất tới endpoint Timeseries v1. Để xuất metrics tới [Dynatrace](https://docs.micrometer.io/micrometer/reference/implementations/dynatrace), bạn phải cung cấp API token, device ID, và URI:

**Properties**

```properties
management.dynatrace.metrics.export.uri=https://{your-environment-id}.live.dynatrace.com
management.dynatrace.metrics.export.api-token=YOUR_TOKEN
management.dynatrace.metrics.export.v1.device-id=YOUR_DEVICE_ID
```

**Yaml**

```yaml
management:
  dynatrace:
    metrics:
      export:
        uri: "https://{your-environment-id}.live.dynatrace.com"
        api-token: "YOUR_TOKEN"
        v1:
          device-id: "YOUR_DEVICE_ID"
```

Với v1 API, bạn phải chỉ định URI môi trường cơ sở không có đường dẫn, vì đường dẫn endpoint v1 được thêm tự động.

### Các thiết lập không phụ thuộc phiên bản (Version-independent Settings)

Ngoài endpoint API và token, bạn cũng có thể thay đổi khoảng thời gian gửi metrics tới Dynatrace. Khoảng thời gian xuất mặc định là `60s`. Ví dụ sau đặt khoảng thời gian xuất là 30 giây:

**Properties**

```properties
management.dynatrace.metrics.export.step=30s
```

**Yaml**

```yaml
management:
  dynatrace:
    metrics:
      export:
        step: "30s"
```

Bạn có thể tìm thêm thông tin về cách thiết lập exporter Dynatrace cho Micrometer trong [tài liệu Micrometer](https://docs.micrometer.io/micrometer/reference/implementations/dynatrace) và [tài liệu Dynatrace](https://docs.dynatrace.com/docs/shortlink/micrometer-metrics-ingest).

## Elastic

Theo mặc định, metrics được xuất tới [Elastic](https://docs.micrometer.io/micrometer/reference/implementations/elastic) chạy trên máy cục bộ của bạn. Bạn có thể cung cấp vị trí của Elastic server cần dùng bằng property sau:

**Properties**

```properties
management.elastic.metrics.export.host=https://elastic.example.com:8086
```

**Yaml**

```yaml
management:
  elastic:
    metrics:
      export:
        host: "https://elastic.example.com:8086"
```

## Ganglia

Theo mặc định, metrics được xuất tới [Ganglia](https://docs.micrometer.io/micrometer/reference/implementations/ganglia) chạy trên máy cục bộ của bạn. Bạn có thể cung cấp host và cổng của [Ganglia server](http://ganglia.sourceforge.net), như ví dụ sau cho thấy:

**Properties**

```properties
management.ganglia.metrics.export.host=ganglia.example.com
management.ganglia.metrics.export.port=9649
```

**Yaml**

```yaml
management:
  ganglia:
    metrics:
      export:
        host: "ganglia.example.com"
        port: 9649
```

## Graphite

Theo mặc định, metrics được xuất tới [Graphite](https://docs.micrometer.io/micrometer/reference/implementations/graphite) chạy trên máy cục bộ của bạn. Bạn có thể cung cấp host và cổng của [Graphite server](https://graphiteapp.org), như ví dụ sau cho thấy:

**Properties**

```properties
management.graphite.metrics.export.host=graphite.example.com
management.graphite.metrics.export.port=9004
```

**Yaml**

```yaml
management:
  graphite:
    metrics:
      export:
         host: "graphite.example.com"
         port: 9004
```

Micrometer cung cấp một `HierarchicalNameMapper` mặc định, chi phối cách một meter ID có chiều (dimensional) được [ánh xạ sang các tên phân cấp phẳng](https://docs.micrometer.io/micrometer/reference/implementations/graphite#_hierarchical_name_mapping).

> **MẸO (TIP):** Để kiểm soát hành vi này, hãy định nghĩa `GraphiteMeterRegistry` của bạn và cung cấp `HierarchicalNameMapper` của riêng bạn. Các bean `GraphiteConfig` và `Clock` được tự động cấu hình sẽ được cung cấp trừ khi bạn định nghĩa của riêng mình:

**Java**

```java
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;
import io.micrometer.graphite.GraphiteConfig;
import io.micrometer.graphite.GraphiteMeterRegistry;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class MyGraphiteConfiguration {

    @Bean
    public GraphiteMeterRegistry graphiteMeterRegistry(GraphiteConfig config, Clock clock) {
        return new GraphiteMeterRegistry(config, clock, this::toHierarchicalName);
    }

    private String toHierarchicalName(Meter.Id id, NamingConvention convention) {
        return ...
    }

}
```

**Kotlin**

```kotlin
import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.Meter
import io.micrometer.core.instrument.config.NamingConvention
import io.micrometer.core.instrument.util.HierarchicalNameMapper
import io.micrometer.graphite.GraphiteConfig
import io.micrometer.graphite.GraphiteMeterRegistry
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class MyGraphiteConfiguration {

    @Bean
    fun graphiteMeterRegistry(config: GraphiteConfig, clock: Clock): GraphiteMeterRegistry {
        return GraphiteMeterRegistry(config, clock, this::toHierarchicalName)
    }
    private fun toHierarchicalName(id: Meter.Id, convention: NamingConvention): String {
        return  ...
    }

}
```

## Humio

Theo mặc định, registry Humio định kỳ đẩy metrics tới [cloud.humio.com](https://cloud.humio.com). Để xuất metrics tới SaaS [Humio](https://docs.micrometer.io/micrometer/reference/implementations/humio), bạn phải cung cấp API token:

**Properties**

```properties
management.humio.metrics.export.api-token=YOUR_TOKEN
```

**Yaml**

```yaml
management:
  humio:
    metrics:
      export:
        api-token: "YOUR_TOKEN"
```

Bạn cũng nên cấu hình một hoặc nhiều tag để xác định nguồn dữ liệu mà metrics được đẩy tới:

**Properties**

```properties
management.humio.metrics.export.tags.alpha=a
management.humio.metrics.export.tags.bravo=b
```

**Yaml**

```yaml
management:
  humio:
    metrics:
      export:
        tags:
          alpha: "a"
          bravo: "b"
```

## Influx

Theo mặc định, metrics được xuất tới một instance [Influx](https://docs.micrometer.io/micrometer/reference/implementations/influx) v1 chạy trên máy cục bộ của bạn với cấu hình mặc định. Để xuất metrics tới InfluxDB v2, hãy cấu hình `org`, `bucket`, và `token` xác thực để ghi metrics. Bạn có thể cung cấp vị trí của [Influx server](https://www.influxdata.com) cần dùng bằng:

**Properties**

```properties
management.influx.metrics.export.uri=https://influx.example.com:8086
```

**Yaml**

```yaml
management:
  influx:
    metrics:
      export:
        uri: "https://influx.example.com:8086"
```

## JMX

Micrometer cung cấp một ánh xạ phân cấp tới [JMX](https://docs.micrometer.io/micrometer/reference/implementations/jmx), chủ yếu như một cách rẻ và di động để xem metrics cục bộ. Theo mặc định, metrics được xuất tới domain JMX `metrics`. Bạn có thể cung cấp domain cần dùng bằng:

**Properties**

```properties
management.jmx.metrics.export.domain=com.example.app.metrics
```

**Yaml**

```yaml
management:
  jmx:
    metrics:
      export:
        domain: "com.example.app.metrics"
```

Micrometer cung cấp một `HierarchicalNameMapper` mặc định, chi phối cách một meter ID có chiều được [ánh xạ sang các tên phân cấp phẳng](https://docs.micrometer.io/micrometer/reference/implementations/jmx#_hierarchical_name_mapping).

> **MẸO (TIP):** Để kiểm soát hành vi này, hãy định nghĩa `JmxMeterRegistry` của bạn và cung cấp `HierarchicalNameMapper` của riêng bạn. Các bean `JmxConfig` và `Clock` được tự động cấu hình sẽ được cung cấp trừ khi bạn định nghĩa của riêng mình:

**Java**

```java
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;
import io.micrometer.jmx.JmxConfig;
import io.micrometer.jmx.JmxMeterRegistry;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class MyJmxConfiguration {

    @Bean
    public JmxMeterRegistry jmxMeterRegistry(JmxConfig config, Clock clock) {
        return new JmxMeterRegistry(config, clock, this::toHierarchicalName);
    }

    private String toHierarchicalName(Meter.Id id, NamingConvention convention) {
        return ...
    }

}
```

**Kotlin**

```kotlin
import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.Meter
import io.micrometer.core.instrument.config.NamingConvention
import io.micrometer.core.instrument.util.HierarchicalNameMapper
import io.micrometer.jmx.JmxConfig
import io.micrometer.jmx.JmxMeterRegistry
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class MyJmxConfiguration {

    @Bean
    fun jmxMeterRegistry(config: JmxConfig, clock: Clock): JmxMeterRegistry {
        return JmxMeterRegistry(config, clock, this::toHierarchicalName)
    }

    private fun toHierarchicalName(id: Meter.Id, convention: NamingConvention): String {
        return  ...
    }

}
```

## KairosDB

Theo mặc định, metrics được xuất tới [KairosDB](https://docs.micrometer.io/micrometer/reference/implementations/kairos) chạy trên máy cục bộ của bạn. Bạn có thể cung cấp vị trí của [KairosDB server](https://kairosdb.github.io/) cần dùng bằng:

**Properties**

```properties
management.kairos.metrics.export.uri=https://kairosdb.example.com:8080/api/v1/datapoints
```

**Yaml**

```yaml
management:
  kairos:
    metrics:
      export:
        uri: "https://kairosdb.example.com:8080/api/v1/datapoints"
```

## New Relic

Một registry New Relic định kỳ đẩy metrics tới [New Relic](https://docs.micrometer.io/micrometer/reference/implementations/new-relic). Để xuất metrics tới [New Relic](https://newrelic.com), bạn phải cung cấp API key và account ID:

**Properties**

```properties
management.newrelic.metrics.export.api-key=YOUR_KEY
management.newrelic.metrics.export.account-id=YOUR_ACCOUNT_ID
```

**Yaml**

```yaml
management:
  newrelic:
    metrics:
      export:
        api-key: "YOUR_KEY"
        account-id: "YOUR_ACCOUNT_ID"
```

Bạn cũng có thể thay đổi khoảng thời gian gửi metrics tới New Relic:

**Properties**

```properties
management.newrelic.metrics.export.step=30s
```

**Yaml**

```yaml
management:
  newrelic:
    metrics:
      export:
        step: "30s"
```

Theo mặc định, metrics được publish thông qua các lời gọi REST, nhưng bạn cũng có thể dùng Java Agent API nếu bạn có nó trên classpath:

**Properties**

```properties
management.newrelic.metrics.export.client-provider-type=insights-agent
```

**Yaml**

```yaml
management:
  newrelic:
    metrics:
      export:
        client-provider-type: "insights-agent"
```

Cuối cùng, bạn có thể kiểm soát hoàn toàn bằng cách định nghĩa bean `NewRelicClientProvider` của riêng mình.

## OpenTelemetry

Theo mặc định, metrics được xuất tới [OpenTelemetry](https://docs.micrometer.io/micrometer/reference/implementations/otlp) chạy trên máy cục bộ của bạn. Bạn có thể cung cấp vị trí của [endpoint metric OpenTelemetry](https://opentelemetry.io/) cần dùng bằng:

**Properties**

```properties
management.otlp.metrics.export.url=https://otlp.example.com:4318/v1/metrics
```

**Yaml**

```yaml
management:
  otlp:
    metrics:
      export:
        url: "https://otlp.example.com:4318/v1/metrics"
```

## Prometheus

[Prometheus](https://docs.micrometer.io/micrometer/reference/implementations/prometheus) mong đợi thu thập (scrape) hoặc thăm dò (poll) từng instance ứng dụng riêng lẻ để lấy metrics. Spring Boot cung cấp một actuator endpoint tại `/actuator/prometheus` để trình bày một [Prometheus scrape](https://prometheus.io) với định dạng phù hợp.

> **MẸO (TIP):** Theo mặc định, endpoint này không khả dụng và phải được expose. Xem [expose endpoint](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.endpoints.exposing) để biết thêm chi tiết.

Ví dụ `scrape_config` sau thêm vào `prometheus.yml`:

```yaml
scrape_configs:
  - job_name: "spring"
    metrics_path: "/actuator/prometheus"
    static_configs:
      - targets: ["HOST:PORT"]
```

[Prometheus Exemplars](https://prometheus.io/docs/prometheus/latest/feature_flags/#exemplars-storage) cũng được hỗ trợ. Để bật tính năng này, cần có một bean `SpanContextSupplier`. Nếu bạn dùng [Micrometer Tracing](https://micrometer.io/docs/tracing), bean này sẽ được tự động cấu hình cho bạn, nhưng bạn luôn có thể tạo bean của riêng mình nếu muốn. Vui lòng xem [Tài liệu Prometheus](https://prometheus.io/docs/prometheus/latest/feature_flags/#exemplars-storage), vì tính năng này cần được bật tường minh ở phía Prometheus, và nó chỉ được hỗ trợ khi dùng định dạng [OpenMetrics](https://github.com/OpenObservability/OpenMetrics/blob/v1.0.0/specification/OpenMetrics.md#exemplars).

Với các job tạm thời (ephemeral) hoặc batch có thể không tồn tại đủ lâu để được thu thập, bạn có thể dùng hỗ trợ [Prometheus Pushgateway](https://github.com/prometheus/pushgateway) để expose metrics tới Prometheus. Để bật hỗ trợ Prometheus Pushgateway, hãy thêm dependency sau vào project:

```xml
<dependency>
    <groupId>io.prometheus</groupId>
    <artifactId>simpleclient_pushgateway</artifactId>
</dependency>
```

Khi dependency Prometheus Pushgateway có trên classpath và property `management.prometheus.metrics.export.pushgateway.enabled` được đặt thành `true`, một bean `PrometheusPushGatewayManager` sẽ được tự động cấu hình. Bean này quản lý việc đẩy metrics tới một Prometheus Pushgateway.

Bạn có thể tinh chỉnh `PrometheusPushGatewayManager` bằng các property bên dưới `management.prometheus.metrics.export.pushgateway`. Để cấu hình nâng cao, bạn cũng có thể cung cấp bean `PrometheusPushGatewayManager` của riêng mình.

## SignalFx

Registry SignalFx định kỳ đẩy metrics tới [SignalFx](https://docs.micrometer.io/micrometer/reference/implementations/signalFx). Để xuất metrics tới [SignalFx](https://www.signalfx.com), bạn phải cung cấp access token:

**Properties**

```properties
management.signalfx.metrics.export.access-token=YOUR_ACCESS_TOKEN
```

**Yaml**

```yaml
management:
  signalfx:
    metrics:
      export:
        access-token: "YOUR_ACCESS_TOKEN"
```

Bạn cũng có thể thay đổi khoảng thời gian gửi metrics tới SignalFx:

**Properties**

```properties
management.signalfx.metrics.export.step=30s
```

**Yaml**

```yaml
management:
  signalfx:
    metrics:
      export:
        step: "30s"
```

## Simple

Micrometer đi kèm một backend in-memory đơn giản, được tự động dùng làm phương án dự phòng nếu không có registry nào khác được cấu hình. Điều này cho phép bạn xem metrics nào được thu thập trong [endpoint metrics](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.metrics.endpoint).

Backend in-memory tự tắt ngay khi bạn dùng bất kỳ backend khả dụng nào khác. Bạn cũng có thể tắt nó một cách tường minh:

**Properties**

```properties
management.simple.metrics.export.enabled=false
```

**Yaml**

```yaml
management:
  simple:
    metrics:
      export:
        enabled: false
```

## Stackdriver

Registry Stackdriver định kỳ đẩy metrics tới [Stackdriver](https://cloud.google.com/stackdriver/). Để xuất metrics tới SaaS [Stackdriver](https://docs.micrometer.io/micrometer/reference/implementations/stackdriver), bạn phải cung cấp Google Cloud project ID:

**Properties**

```properties
management.stackdriver.metrics.export.project-id=my-project
```

**Yaml**

```yaml
management:
  stackdriver:
    metrics:
      export:
        project-id: "my-project"
```

Bạn cũng có thể thay đổi khoảng thời gian gửi metrics tới Stackdriver:

**Properties**

```properties
management.stackdriver.metrics.export.step=30s
```

**Yaml**

```yaml
management:
  stackdriver:
    metrics:
      export:
        step: "30s"
```

## StatsD

Registry StatsD chủ động đẩy metrics qua UDP tới một StatsD agent. Theo mặc định, metrics được xuất tới một agent [StatsD](https://docs.micrometer.io/micrometer/reference/implementations/statsD) chạy trên máy cục bộ của bạn. Bạn có thể cung cấp host, cổng, và giao thức của StatsD agent cần dùng bằng:

**Properties**

```properties
management.statsd.metrics.export.host=statsd.example.com
management.statsd.metrics.export.port=9125
management.statsd.metrics.export.protocol=udp
```

**Yaml**

```yaml
management:
  statsd:
    metrics:
      export:
        host: "statsd.example.com"
        port: 9125
        protocol: "udp"
```

Bạn cũng có thể thay đổi line protocol của StatsD cần dùng (mặc định là Datadog):

**Properties**

```properties
management.statsd.metrics.export.flavor=etsy
```

**Yaml**

```yaml
management:
  statsd:
    metrics:
      export:
        flavor: "etsy"
```

## Wavefront

Registry Wavefront định kỳ đẩy metrics tới [Wavefront](https://docs.micrometer.io/micrometer/reference/implementations/wavefront). Nếu bạn xuất metrics trực tiếp tới [Wavefront](https://www.wavefront.com/), bạn phải cung cấp API token:

**Properties**

```properties
management.wavefront.api-token=YOUR_API_TOKEN
```

**Yaml**

```yaml
management:
  wavefront:
    api-token: "YOUR_API_TOKEN"
```

Ngoài ra, bạn có thể dùng một Wavefront sidecar hoặc một proxy nội bộ trong môi trường của bạn để chuyển tiếp dữ liệu metrics tới host API của Wavefront:

**Properties**

```properties
management.wavefront.uri=proxy://localhost:2878
```

**Yaml**

```yaml
management:
  wavefront:
    uri: "proxy://localhost:2878"
```

> **LƯU Ý (NOTE):** Nếu bạn publish metrics tới một proxy Wavefront (như được mô tả trong [tài liệu Wavefront](https://docs.wavefront.com/proxies_installing.html)), host phải ở định dạng `proxy://HOST:PORT`.

Bạn cũng có thể thay đổi khoảng thời gian gửi metrics tới Wavefront:

**Properties**

```properties
management.wavefront.metrics.export.step=30s
```

**Yaml**

```yaml
management:
  wavefront:
    metrics:
      export:
        step: "30s"
```

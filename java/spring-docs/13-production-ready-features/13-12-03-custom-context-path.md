# 13.12.3. Context path tùy chỉnh (Custom Context Path)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.cloud-foundry.custom-context-path>

Nếu context-path của server đã được cấu hình thành bất kỳ giá trị nào khác `/`, các endpoint Cloud Foundry sẽ không khả dụng ở thư mục gốc của ứng dụng. Ví dụ, nếu `server.servlet.context-path=/app`, các endpoint Cloud Foundry khả dụng tại `/app/cloudfoundryapplication/*`.

Nếu bạn mong muốn các endpoint Cloud Foundry luôn khả dụng tại `/cloudfoundryapplication/*`, bất kể context-path của server, bạn cần cấu hình điều đó một cách tường minh trong ứng dụng. Cấu hình khác nhau tùy thuộc vào web server đang được dùng. Với Tomcat, bạn có thể thêm cấu hình sau:

**Java**

```java
import java.io.IOException;
import java.util.Collections;

import jakarta.servlet.GenericServlet;
import jakarta.servlet.Servlet;
import jakarta.servlet.ServletContainerInitializer;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.apache.catalina.Host;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.Tomcat;

import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class MyCloudFoundryConfiguration {

    @Bean
    public TomcatServletWebServerFactory servletWebServerFactory() {
        return new TomcatServletWebServerFactory() {

            @Override
            protected void prepareContext(Host host, ServletContextInitializer[] initializers) {
                super.prepareContext(host, initializers);
                StandardContext child = new StandardContext();
                child.addLifecycleListener(new Tomcat.FixContextListener());
                child.setPath("/cloudfoundryapplication");
                ServletContainerInitializer initializer = getServletContextInitializer(getContextPath());
                child.addServletContainerInitializer(initializer, Collections.emptySet());
                child.setCrossContext(true);
                host.addChild(child);
            }

        };
    }

    private ServletContainerInitializer getServletContextInitializer(String contextPath) {
        return (classes, context) -> {
            Servlet servlet = new GenericServlet() {

                @Override
                public void service(ServletRequest req, ServletResponse res) throws ServletException, IOException {
                    ServletContext context = req.getServletContext().getContext(contextPath);
                    context.getRequestDispatcher("/cloudfoundryapplication").forward(req, res);
                }

            };
            context.addServlet("cloudfoundry", servlet).addMapping("/*");
        };
    }

}
```

**Kotlin**

```kotlin
import jakarta.servlet.GenericServlet
import jakarta.servlet.Servlet
import jakarta.servlet.ServletContainerInitializer
import jakarta.servlet.ServletContext
import jakarta.servlet.ServletException
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import org.apache.catalina.Host
import org.apache.catalina.core.StandardContext
import org.apache.catalina.startup.Tomcat.FixContextListener
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory
import org.springframework.boot.web.servlet.ServletContextInitializer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.io.IOException
import java.util.Collections.emptySet

@Configuration(proxyBeanMethods = false)
class MyCloudFoundryConfiguration {

    @Bean
    fun servletWebServerFactory(): TomcatServletWebServerFactory {
        return object : TomcatServletWebServerFactory() {

            override fun prepareContext(host: Host, initializers: Array<ServletContextInitializer>) {
                super.prepareContext(host, initializers)
                val child = StandardContext()
                child.addLifecycleListener(FixContextListener())
                child.path = "/cloudfoundryapplication"
                val initializer = getServletContextInitializer(contextPath)
                child.addServletContainerInitializer(initializer, emptySet())
                child.crossContext = true
                host.addChild(child)
            }

        }
    }

    private fun getServletContextInitializer(contextPath: String): ServletContainerInitializer {
        return ServletContainerInitializer { classes: Set<Class<*>?>?, context: ServletContext ->
            val servlet: Servlet = object : GenericServlet() {

                @Throws(ServletException::class, IOException::class)
                override fun service(req: ServletRequest, res: ServletResponse) {
                    val servletContext = req.servletContext.getContext(contextPath)
                    servletContext.getRequestDispatcher("/cloudfoundryapplication").forward(req, res)
                }

            }
            context.addServlet("cloudfoundry", servlet).addMapping("/*")
        }
    }
}
```

Nếu bạn đang dùng một ứng dụng dựa trên Webflux, bạn có thể dùng cấu hình sau:

**Java**

```java
import java.util.Map;

import reactor.core.publisher.Mono;

import org.springframework.boot.autoconfigure.web.reactive.WebFluxProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.reactive.ContextPathCompositeHandler;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(WebFluxProperties.class)
public class MyReactiveCloudFoundryConfiguration {

    @Bean
    public HttpHandler httpHandler(ApplicationContext applicationContext, WebFluxProperties properties) {
        HttpHandler httpHandler = WebHttpHandlerBuilder.applicationContext(applicationContext).build();
        return new CloudFoundryHttpHandler(properties.getBasePath(), httpHandler);
    }

    private static final class CloudFoundryHttpHandler implements HttpHandler {

        private final HttpHandler delegate;

        private final ContextPathCompositeHandler contextPathDelegate;

        private CloudFoundryHttpHandler(String basePath, HttpHandler delegate) {
            this.delegate = delegate;
            this.contextPathDelegate = new ContextPathCompositeHandler(Map.of(basePath, delegate));
        }

        @Override
        public Mono<Void> handle(ServerHttpRequest request, ServerHttpResponse response) {
            // Remove underlying context path first (e.g. Servlet container)
            String path = request.getPath().pathWithinApplication().value();
            if (path.startsWith("/cloudfoundryapplication")) {
                return this.delegate.handle(request, response);
            }
            else {
                return this.contextPathDelegate.handle(request, response);
            }
        }

    }

}
```

**Kotlin**

```kotlin
import org.springframework.boot.autoconfigure.web.reactive.WebFluxProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.server.reactive.ContextPathCompositeHandler
import org.springframework.http.server.reactive.HttpHandler
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.http.server.reactive.ServerHttpResponse
import org.springframework.web.server.adapter.WebHttpHandlerBuilder
import reactor.core.publisher.Mono

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(WebFluxProperties::class)
class MyReactiveCloudFoundryConfiguration {

    @Bean
    fun httpHandler(applicationContext: ApplicationContext, properties: WebFluxProperties): HttpHandler {
        val httpHandler = WebHttpHandlerBuilder.applicationContext(applicationContext).build()
        return CloudFoundryHttpHandler(properties.basePath, httpHandler)
    }

    private class CloudFoundryHttpHandler(basePath: String, private val delegate: HttpHandler) : HttpHandler {
        private val contextPathDelegate = ContextPathCompositeHandler(mapOf(basePath to delegate))

        override fun handle(request: ServerHttpRequest, response: ServerHttpResponse): Mono<Void> {
            // Remove underlying context path first (e.g. Servlet container)
            val path = request.path.pathWithinApplication().value()
            return if (path.startsWith("/cloudfoundryapplication")) {
                delegate.handle(request, response)
            } else {
                contextPathDelegate.handle(request, response)
            }
        }
    }
}
```

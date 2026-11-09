package com.restorant.config;
import java.time.Clock;
import org.springframework.context.annotation.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@Configuration @EnableConfigurationProperties(RestaurantSettings.class)
public class AppConfig {
    @Bean Clock restaurantClock(RestaurantSettings settings) { return Clock.system(settings.zone()); }
    @Bean org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory webServer() {
        var factory = new org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory();
        // NIO2 avoids the Windows selector pipe failure seen with this machine's JDK.
        factory.setProtocol("org.apache.coyote.http11.Http11Nio2Protocol");
        return factory;
    }
}

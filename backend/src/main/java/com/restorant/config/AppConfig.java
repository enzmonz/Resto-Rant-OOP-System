package com.restorant.config;
import java.time.Clock;
import org.springframework.context.annotation.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@Configuration @EnableConfigurationProperties({RestaurantSettings.class, PaymentSettings.class})
public class AppConfig {
    // The restaurant clock is the only source of "now" and "today". It reads the server's UTC instant and applies
    // the configured restaurant zone, so neither the server's nor the browser's timezone setting changes a booking.
    @Bean Clock restaurantClock(RestaurantSettings settings) { return Clock.system(settings.zone()); }
    @Bean org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory webServer() {
        var factory = new org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory();
        // NIO2 avoids the Windows selector pipe failure seen with this machine's JDK.
        factory.setProtocol("org.apache.coyote.http11.Http11Nio2Protocol");
        return factory;
    }
}

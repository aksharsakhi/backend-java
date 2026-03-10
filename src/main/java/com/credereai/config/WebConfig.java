package com.credereai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final String DEFAULT_ALLOWED_ORIGINS = "*";

    @Bean
    public CorsFilter corsFilter() {
        var config = new CorsConfiguration();
        String configuredOrigins = System.getenv().getOrDefault("CORS_ALLOWED_ORIGINS", DEFAULT_ALLOWED_ORIGINS);
        List<String> origins = Arrays.stream(configuredOrigins.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toList());

        if (origins.isEmpty() || (origins.size() == 1 && "*".equals(origins.get(0)))) {
            config.addAllowedOriginPattern("*");
            config.setAllowCredentials(false);
        } else {
            origins.forEach(config::addAllowedOriginPattern);
            config.setAllowCredentials(true);
        }

        config.addAllowedMethod("*");
        config.addAllowedHeader("*");
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }

    @Bean
    public WebClient geminiWebClient() {
        return WebClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com")
                .codecs(c -> c.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
    }

    @Bean
    public WebClient groqWebClient() {
        return WebClient.builder()
                .baseUrl("https://api.groq.com")
                .codecs(c -> c.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/static/**")
                .addResourceLocations("file:../frontend-react/dist/");
    }
}

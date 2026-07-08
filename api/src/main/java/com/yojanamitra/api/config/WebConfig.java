package com.yojanamitra.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * A {@link RestClient} pointed at the Python GenAI service.
 * (CORS is configured in SecurityConfig now that Spring Security is in play.)
 */
@Configuration
public class WebConfig {

    @Bean
    public RestClient aiRestClient(@Value("${yojanamitra.ai.base-url}") String baseUrl) {
        // Pin to HTTP/1.1: the JDK client defaults to HTTP/2 and attempts an h2c
        // upgrade that the Python (uvicorn/HTTP-1.1) service rejects.
        HttpClient http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        // Generous read timeout: on free hosting the AI service may be cold and take
        // ~30-60s to wake. Wait for it (the UI shows a spinner) instead of 503-ing.
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(90));
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }
}

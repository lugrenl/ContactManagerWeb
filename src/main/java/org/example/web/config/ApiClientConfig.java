package org.example.web.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class ApiClientConfig {

    @Bean
    RestClient contactManagerRestClient(
            RestClient.Builder builder,
            @Value("${contactmanager.api.base-url}") String baseUrl,
            @Value("${contactmanager.api.connect-timeout:5s}") Duration connectTimeout,
            @Value("${contactmanager.api.read-timeout:20s}") Duration readTimeout) {

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(toMillis(connectTimeout));
        requestFactory.setReadTimeout(toMillis(readTimeout));

        return builder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    private static int toMillis(Duration timeout) {
        long millis = timeout.toMillis();
        if (millis < 1 || millis > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("API timeout must be between 1 ms and " + Integer.MAX_VALUE + " ms");
        }
        return (int) millis;
    }
}

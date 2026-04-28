package com.deallock.backend.paymentintegration.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class PaystackConfig {

    @Value("${paystack.base-url:https://api.paystack.co}")
    private String baseUrl;

    @Bean
    public RestTemplate paystackRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(30_000);
        return new RestTemplate(factory);
    }

    public String getBaseUrl() {
        return baseUrl;
    }
}
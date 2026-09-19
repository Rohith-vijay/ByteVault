package com.bytevault.payment.config;

import feign.Request;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.TimeUnit;

public class FeignTimeoutConfig {

    @Bean
    public Request.Options requestOptions() {
        return new Request.Options(3000, TimeUnit.MILLISECONDS, 5000, TimeUnit.MILLISECONDS, true);
    }
}

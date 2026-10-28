package com.bytevault.payment.config;

import com.bytevault.common.config.CommonFeignConfig;
import feign.Request;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.util.concurrent.TimeUnit;

@Import(CommonFeignConfig.class)
public class FeignTimeoutConfig {

    @Bean
    public Request.Options requestOptions() {
        return new Request.Options(3000, TimeUnit.MILLISECONDS, 5000, TimeUnit.MILLISECONDS, true);
    }
}

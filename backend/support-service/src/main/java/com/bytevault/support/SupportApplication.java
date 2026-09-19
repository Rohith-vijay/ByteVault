package com.bytevault.support;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication(scanBasePackages = {"com.bytevault.support", "com.bytevault.common"})
@EnableDiscoveryClient
@EnableJpaAuditing
public class SupportApplication {

    public static void main(String[] args) {
        SpringApplication.run(SupportApplication.class, args);
    }
}

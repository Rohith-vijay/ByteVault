package com.bytevault.shipping;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(scanBasePackages = "com.bytevault")
@EnableDiscoveryClient
public class ShippingApplication {
    public static void main(String[] args) {
        SpringApplication.run(ShippingApplication.class, args);
    }
}

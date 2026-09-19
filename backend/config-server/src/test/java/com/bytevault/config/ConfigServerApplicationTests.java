package com.bytevault.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.server.environment.NativeEnvironmentRepository;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.profiles.active=native",
        "spring.cloud.config.server.native.search-locations=classpath:/config",
        "eureka.client.register-with-eureka=false",
        "eureka.client.fetch-registry=false"
})
class ConfigServerApplicationTests {

    @Autowired(required = false)
    private NativeEnvironmentRepository environmentRepository;

    @Test
    @DisplayName("Config Server boots and NativeEnvironmentRepository resolves centralized configuration profiles")
    void testConfigServerResolvesProfiles() {
        assertNotNull(environmentRepository, "NativeEnvironmentRepository should be active in native profile");

        Environment authEnv = environmentRepository.findOne("auth-service", "default", "main");
        assertNotNull(authEnv, "Should locate configuration for auth-service");
        assertTrue(authEnv.getPropertySources().size() > 0, "Should load properties from central repository");

        Environment productEnv = environmentRepository.findOne("product-service", "default", "main");
        assertNotNull(productEnv, "Should locate configuration for product-service");
        assertTrue(productEnv.getPropertySources().size() > 0, "Should load properties for product-service");
    }
}

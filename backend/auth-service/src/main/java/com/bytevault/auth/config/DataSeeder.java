package com.bytevault.auth.config;

import com.bytevault.auth.entity.AuthCredentials;
import com.bytevault.auth.entity.Role;
import com.bytevault.auth.repository.AuthCredentialsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final AuthCredentialsRepository credentialsRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (credentialsRepository.findByUsername("admin@bytevault.com").isEmpty()) {
            AuthCredentials admin = AuthCredentials.builder()
                    .username("admin@bytevault.com")
                    .password(passwordEncoder.encode("AdminPass123!"))
                    .role(Role.ADMIN)
                    .isActive(true)
                    .build();
            credentialsRepository.save(admin);
            log.info("[DataSeeder] Initialized default admin account: admin@bytevault.com");
        }

        if (credentialsRepository.findByUsername("customer@bytevault.com").isEmpty()) {
            AuthCredentials customer = AuthCredentials.builder()
                    .username("customer@bytevault.com")
                    .password(passwordEncoder.encode("CustomerPass123!"))
                    .role(Role.CUSTOMER)
                    .isActive(true)
                    .build();
            credentialsRepository.save(customer);
            log.info("[DataSeeder] Initialized default customer account: customer@bytevault.com");
        }
    }
}

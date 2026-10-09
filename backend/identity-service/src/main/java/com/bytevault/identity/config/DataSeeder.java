package com.bytevault.identity.config;

import com.bytevault.identity.entity.AuthCredentials;
import com.bytevault.identity.entity.Role;
import com.bytevault.identity.entity.VendorStatus;
import com.bytevault.identity.repository.AuthCredentialsRepository;
import com.bytevault.identity.service.UserProfileService;
import com.bytevault.identity.service.VendorProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final AuthCredentialsRepository credentialsRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserProfileService userProfileService;
    private final VendorProfileService vendorProfileService;

    @Override
    public void run(String... args) {
        if (credentialsRepository.findByUsername("admin@bytevault.com").isEmpty()) {
            AuthCredentials admin = AuthCredentials.builder()
                    .username("admin@bytevault.com")
                    .password(passwordEncoder.encode("AdminPass123!"))
                    .role(Role.ADMIN)
                    .isActive(true)
                    .isEmailVerified(true)
                    .build();
            admin = credentialsRepository.save(admin);
            userProfileService.createProfile(admin.getId(), admin.getUsername(), "Marketplace Administrator", "Marketplace", "Administrator");
            log.info("[DataSeeder] Initialized default admin account: admin@bytevault.com");
        }

        if (credentialsRepository.findByUsername("customer@bytevault.com").isEmpty()) {
            AuthCredentials customer = AuthCredentials.builder()
                    .username("customer@bytevault.com")
                    .password(passwordEncoder.encode("CustomerPass123!"))
                    .role(Role.CUSTOMER)
                    .isActive(true)
                    .isEmailVerified(true)
                    .build();
            customer = credentialsRepository.save(customer);
            userProfileService.createProfile(customer.getId(), customer.getUsername(), "Demo Customer", "Demo", "Customer");
            log.info("[DataSeeder] Initialized default customer account: customer@bytevault.com");
        }

        if (credentialsRepository.findByUsername("vendor@bytevault.com").isEmpty()) {
            AuthCredentials vendor = AuthCredentials.builder()
                    .username("vendor@bytevault.com")
                    .password(passwordEncoder.encode("VendorPass123!"))
                    .role(Role.VENDOR)
                    .isActive(true)
                    .isEmailVerified(true)
                    .build();
            vendor = credentialsRepository.save(vendor);
            userProfileService.createProfile(vendor.getId(), vendor.getUsername(), "ByteVault Media Studio", "ByteVault", "Studio");
            var vendorProfile = vendorProfileService.createPendingVendorProfile(
                    vendor.getId(),
                    vendor.getUsername(),
                    "ByteVault Studio",
                    "Official ByteVault Media digital creation and distribution studio.",
                    "TAX-BV-10029",
                    "Bank Transfer - Standard Payout",
                    "support@bytevault.com"
            );
            if (vendorProfile != null) {
                vendorProfile.setStatus(VendorStatus.APPROVED);
                vendorProfile.setReviewedAt(LocalDateTime.now());
            }
            log.info("[DataSeeder] Initialized default approved vendor account: vendor@bytevault.com");
        }
    }
}

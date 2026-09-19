package com.bytevault.product.config;

import com.bytevault.product.entity.Category;
import com.bytevault.product.entity.Product;
import com.bytevault.product.entity.ProductStatus;
import com.bytevault.product.entity.ProductType;
import com.bytevault.product.repository.CategoryRepository;
import com.bytevault.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    @Override
    public void run(String... args) {
        if (categoryRepository.count() == 0) {
            Category ebooks = categoryRepository.save(Category.builder()
                    .name("E-Books")
                    .description("Digital engineering manuals, audiobooks, and publications")
                    .build());

            Category software = categoryRepository.save(Category.builder()
                    .name("Developer Software")
                    .description("Developer tools, SDK licenses, and productivity packages")
                    .build());

            Category hardware = categoryRepository.save(Category.builder()
                    .name("Hardware & Security")
                    .description("Physical FIDO2 hardware keys, microcontrollers, and hardware")
                    .build());

            Category vinyl = categoryRepository.save(Category.builder()
                    .name("Audio & Media")
                    .description("Audiophile vinyl pressings and physical master recordings")
                    .build());

            log.info("[DataSeeder] Initialized 4 product categories");

            // Seed sample digital & physical products
            productRepository.save(Product.builder()
                    .name("Cloud Architecture Handbook (PDF)")
                    .description("Complete 600-page engineering guide covering high-scale microservices, event-driven architectures, and distributed security.")
                    .price(new BigDecimal("29.99"))
                    .productType(ProductType.DIGITAL)
                    .status(ProductStatus.PUBLISHED)
                    .category(ebooks)
                    .fileName("cloud-arch-handbook-v2.pdf")
                    .fileType("application/pdf")
                    .fileSize(14200000L)
                    .fileVersion("2.1.0")
                    .build());

            productRepository.save(Product.builder()
                    .name("Quantum Engine Pro Enterprise License")
                    .description("High-performance simulation runtime engine for data pipelines and real-time processing.")
                    .price(new BigDecimal("149.99"))
                    .productType(ProductType.DIGITAL)
                    .status(ProductStatus.PUBLISHED)
                    .category(software)
                    .fileName("quantum-engine-pro.zip")
                    .fileType("application/zip")
                    .fileSize(85000000L)
                    .fileVersion("4.0.2")
                    .build());

            productRepository.save(Product.builder()
                    .name("ByteVault Titanium Hardware Key")
                    .description("Military-grade FIDO2 / WebAuthn dual-interface security hardware key with tamper-proof enclave.")
                    .price(new BigDecimal("79.99"))
                    .productType(ProductType.PHYSICAL)
                    .status(ProductStatus.PUBLISHED)
                    .category(hardware)
                    .physicalSku("BV-HWKEY-TITANIUM-01")
                    .physicalWeight(0.045)
                    .physicalDimensions("45 x 18 x 5 mm")
                    .build());

            productRepository.save(Product.builder()
                    .name("Synthwave Chronicles Master Vinyl (Limited)")
                    .description("Heavyweight 180g double-LP collector's edition master analog recording.")
                    .price(new BigDecimal("44.50"))
                    .productType(ProductType.PHYSICAL)
                    .status(ProductStatus.PUBLISHED)
                    .category(vinyl)
                    .physicalSku("BV-VINYL-SYNTH-02")
                    .physicalWeight(0.480)
                    .physicalDimensions("315 x 315 x 8 mm")
                    .build());

            log.info("[DataSeeder] Initialized 4 marketplace sample products (2 Digital, 2 Physical)");
        }
    }
}

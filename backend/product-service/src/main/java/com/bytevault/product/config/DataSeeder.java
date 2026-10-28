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
                    .name("Synthwave Chronicles Studio Master FLAC")
                    .description("Lossless 24-bit/192kHz multi-track digital audio master recording with full licensing.")
                    .price(new BigDecimal("44.50"))
                    .productType(ProductType.DIGITAL)
                    .status(ProductStatus.PUBLISHED)
                    .category(software)
                    .fileName("synthwave-chronicles-master.flac")
                    .fileType("audio/flac")
                    .fileSize(480000000L)
                    .fileVersion("1.0.0")
                    .build());

            productRepository.save(Product.builder()
                    .name("Zero-Trust Security Architect Reference Guide")
                    .description("Comprehensive enterprise blueprint for distributed authorization and token architectures.")
                    .price(new BigDecimal("79.99"))
                    .productType(ProductType.DIGITAL)
                    .status(ProductStatus.PUBLISHED)
                    .category(ebooks)
                    .fileName("zero-trust-architecture-guide.pdf")
                    .fileType("application/pdf")
                    .fileSize(25000000L)
                    .fileVersion("3.2.0")
                    .build());

            log.info("[DataSeeder] Initialized 4 marketplace sample digital products");
        }
    }
}
